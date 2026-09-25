package io.github.baba983.nativedo.core.ui

import android.graphics.drawable.Drawable
import android.view.View
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
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
