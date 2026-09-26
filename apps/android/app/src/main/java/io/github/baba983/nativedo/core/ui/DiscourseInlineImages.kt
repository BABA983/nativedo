package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.View
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiSpan

/**
 * The app's end of the rich renderer's inline images.
 *
 * Upstream draws a `TL_iv.textCustomEmoji` run through [AnimatedEmojiSpan], which resolves a
 * Telegram emoji document; a Discourse post's inline images are a site URL (a custom emoji, a
 * twemoji image) or a sprite symbol (`<kbd>`, an inline site icon), so the span asks this registry
 * for the image instead. [PostRichMessageBuilder] registers each token-form inline image and emits
 * the run with the registry's id, which is what makes the rich body draw what the block path draws
 * (`InlineTextImages`) instead of flattening the token into text.
 *
 * A URL image is a [DeferredInlineImageDrawable] that [attach] gives an `ImageReceiver`, so the
 * image pool loads it through the app's own HTTP path and invalidates the renderer's view when the
 * bitmap arrives - the same mechanism `BuiltText` uses for the block path. A sprite icon renders
 * synchronously from the site's captured sprite markup; while that markup is missing the span falls
 * back to the alt text, which is what the block path does too.
 *
 * Ids are interned per image (URL, or symbol + tint), so a rebind reuses the same id and the same
 * drawable; the process holds one entry per distinct image on the site, like the media bridge's
 * id ↔ URL map.
 */
internal object DiscourseInlineImages : AnimatedEmojiSpan.InlineImageResolver {

    private sealed interface Spec {
        data class Image(val resolvedUrl: String) : Spec
        data class Icon(val symbolId: String, val rawTintColor: String) : Spec
        data class Checkbox(val checked: Boolean) : Spec
        data class Chip(
            val label: String,
            val symbolId: String,
            val rawIconColor: String,
            val href: String,
        ) : Spec
    }

    private val idByKey = ConcurrentHashMap<String, Long>()
    private val specById = ConcurrentHashMap<Long, Spec>()
    private val drawablesById = ConcurrentHashMap<Long, HashMap<Int, Drawable>>()
    /** Keyed by `id@size`: one emoji can be laid out at two sizes (a heading and the body). */
    private val receiversByKey = ConcurrentHashMap<String, ImageReceiver>()
    private var nextId = 0L
    private var spriteMarkup: () -> String = { "" }

    /** Called once from the app's entry point; a test host may install its own markup. */
    fun install(spriteMarkupProvider: () -> String) {
        spriteMarkup = spriteMarkupProvider
        AnimatedEmojiSpan.setInlineImageResolver(this)
    }

    /** An id for this image, stable for as long as the URL is registered. */
    fun registerImage(spec: InlineImageSpec, baseUrl: String): Long {
        // A caller without a base keeps the raw src, like the photo mapping does.
        val resolvedUrl = if (baseUrl.isEmpty()) spec.rawUrl else resolvePostImageUrl(baseUrl, spec.rawUrl)
        return register("image:$resolvedUrl") { Spec.Image(resolvedUrl) }
    }

    /** An id for this sprite symbol at this tint, stable while both stay the same. */
    fun registerIcon(spec: InlineIconSpec): Long =
        register("icon:${spec.symbolId}:${spec.rawTintColor}") {
            Spec.Icon(symbolId = spec.symbolId, rawTintColor = spec.rawTintColor)
        }

    /** An id for a cooked inline checkbox: the site's `[x]`/`[ ]`, drawn as Telegram's task box. */
    fun registerCheckbox(checked: Boolean): Long = register("checkbox:$checked") {
        Spec.Checkbox(checked)
    }

    /**
     * An id for this hashtag chip: the label it draws, the sprite symbol its icon resolves to, and
     * the tag URL a tap opens. The chip's colours are the theme's, read when it draws.
     */
    fun registerChip(spec: InlineHashtagChipSpec, baseUrl: String): Long {
        val href = if (baseUrl.isEmpty()) spec.href else resolvePostImageUrl(baseUrl, spec.href)
        return register("chip:$href:${spec.label}") {
            Spec.Chip(
                label = spec.label,
                symbolId = spec.symbolId,
                rawIconColor = spec.rawIconColor,
                href = href,
            )
        }
    }

    /** The URL a registered id opens when tapped, for the ids that have one (a chip's tag page). */
    fun hrefFor(documentId: Long): String? = (specById[documentId] as? Spec.Chip)?.href

    private fun register(key: String, spec: () -> Spec): Long = idByKey.computeIfAbsent(key) {
        val id = nextId + 1
        nextId = id
        specById[id] = spec()
        id
    }

    override fun imageFor(documentId: Long, sizePx: Int): Drawable? {
        val spec = specById[documentId] ?: return null
        val side = max(1, sizePx)
        return when (spec) {
            is Spec.Image -> drawablesById.drawablesFor(documentId).getOrPut(side) {
                DeferredInlineImageDrawable(side, side)
            }
            is Spec.Icon -> drawablesById.drawablesFor(documentId)[side]
                ?: renderInlineSvgIconDrawable(
                    spriteMarkup = spriteMarkup(),
                    symbolId = spec.symbolId,
                    rawTintColor = spec.rawTintColor,
                )?.mutate()?.apply { setBounds(0, 0, side, side) }
                    ?.also { drawablesById.drawablesFor(documentId)[side] = it }
            is Spec.Checkbox -> drawablesById.drawablesFor(documentId).getOrPut(side) {
                DiscourseCheckboxDrawable(checked = spec.checked).apply { setBounds(0, 0, side, side) }
            }
            is Spec.Chip -> drawablesById.drawablesFor(documentId).getOrPut(side) {
                // The renderer sizes an inline image to `fontSize + 8dp`; the chip's label is the
                // body size inside that box, and the chip's own height comes from its paint.
                DiscourseTagChipDrawable(
                    label = spec.label,
                    icon = renderInlineSvgIconDrawable(
                        spriteMarkup = spriteMarkup(),
                        symbolId = spec.symbolId,
                        rawTintColor = spec.rawIconColor.ifBlank { accentColor().toCssHexColor() },
                    ),
                    textSizePx = max(1f, (side - AndroidUtilities.dp(8f)).toFloat()),
                )
            }
        }
    }

    override fun attach(documentId: Long, sizePx: Int, host: View) {
        val spec = specById[documentId] as? Spec.Image ?: return
        val side = max(1, sizePx)
        val key = receiverKey(documentId, side)
        if (receiversByKey.containsKey(key)) {
            return
        }
        val drawable = imageFor(documentId, side) as? DeferredInlineImageDrawable ?: return
        val receiver = ImageReceiver(host).apply {
            setInvalidateAll(true)
            setAllowLoadingOnAttachedOnly(true)
            setAllowDecodeSingleFrame(!isAnimatedDiscourseImageUrl(spec.resolvedUrl))
            setImage(
                ImageLocation.getForPath(spec.resolvedUrl),
                imageFilter(side),
                null,
                ImageLoader.getHttpUrlExtension(spec.resolvedUrl, "jpg"),
                spec.resolvedUrl,
                0,
            )
            if (host.isAttachedToWindow) {
                onAttachedToWindow()
            }
        }
        receiversByKey[key] = receiver
        drawable.setImageReceiver(receiver)
        host.invalidate()
    }

    override fun detach(documentId: Long) {
        val prefix = "$documentId@"
        receiversByKey.keys
            .filter { it.startsWith(prefix) }
            .forEach { key -> receiversByKey.remove(key)?.onDetachedFromWindow() }
        drawablesById[documentId]?.values?.forEach { drawable ->
            (drawable as? DeferredInlineImageDrawable)?.setImageReceiver(null)
        }
    }

    private fun receiverKey(documentId: Long, sizePx: Int): String = "$documentId@$sizePx"

    /** The same dp-shaped filter the block path asks the image pool for. */
    private fun imageFilter(sizePx: Int): String {
        val density = max(1f, AndroidUtilities.density)
        val sideDp = max(1, ceil(sizePx / density).toInt())
        return "${sideDp}_${sideDp}_i"
    }

    private fun ConcurrentHashMap<Long, HashMap<Int, Drawable>>.drawablesFor(id: Long): HashMap<Int, Drawable> =
        getOrPut(id) { HashMap() }
}

/** The accent the block path's chip uses (`PostBodyBuildContext.accentColor`). */
private fun accentColor(): Int = Theme.getColor(Theme.key_chat_inReplyLine)

/**
 * A hashtag chip as a drawable, for the rich run: `AnimatedEmojiSpan` draws whatever this registry
 * hands it, so the chip reuses the block path's own [DiscourseTagChipSpan] instead of a second
 * implementation. The span paints its background, icon and label; this wrapper only gives it a
 * canvas of the chip's size, with the baseline placed so the chip fills the bounds.
 *
 * The chip's colours are the theme's, so a theme switch rebuilds the span on the next draw instead
 * of leaving a stale chip in the registry's cache.
 */
private class DiscourseTagChipDrawable(
    private val label: String,
    private val icon: Drawable?,
    private val textSizePx: Float,
) : Drawable() {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textSizePx }
    private val metrics = Paint.FontMetricsInt()
    private var chipSpan: DiscourseTagChipSpan? = null
    private var chipAccent = 0
    private val chipHeight: Int
    private val chipWidth: Int

    init {
        val span = spanFor(accentColor())
        chipAccent = accentColor()
        chipWidth = max(1, span.getSize(paint, label, 0, label.length, metrics))
        chipHeight = max(1, metrics.descent - metrics.ascent)
        setBounds(0, 0, chipWidth, chipHeight)
    }

    private fun spanFor(accent: Int): DiscourseTagChipSpan = DiscourseTagChipSpan(
        label = label,
        icon = icon,
        backgroundColor = Theme.multAlpha(accent, 0.15f),
        textColor = accent,
        textSizePx = textSizePx,
    )

    override fun draw(canvas: Canvas) {
        val accent = accentColor()
        val span = if (chipSpan == null || chipAccent != accent) {
            spanFor(accent).also { chipSpan = it; chipAccent = accent }
        } else {
            chipSpan!!
        }
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        span.draw(canvas, label, 0, label.length, 0f, 0, -metrics.ascent, chipHeight, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
