package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import io.github.baba983.nativedo.core.network.addImageRequestHeaders
import io.github.baba983.nativedo.core.storage.CookieStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val twemojiInlineImageScale = 0.90f
private const val customEmojiInlineImageScale = 1.0f
private const val siteIconInlineImageScale = 0.92f
private const val emojiInlineImageUpwardBiasRatio = 0.04f
private const val siteIconInlineImageUpwardBiasRatio = 0.04f

private val inlineImageClient = OkHttpClient()
private val inlineImageExecutor: ExecutorService = Executors.newFixedThreadPool(2)
private val inlineImageCache = InlineMemoryCache<Bitmap>(maxEntries = 128)

internal data class AppliedInlineImages(
    val text: CharSequence,
    val pendingImages: List<PendingInlineImage>,
)

internal data class PendingInlineImage(
    val resolvedUrl: String,
    val drawable: DeferredInlineImageDrawable,
)

internal class InlineImageRenderHandle {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun load(
        pendingImage: PendingInlineImage,
        cookieStore: CookieStore,
        baseUrl: String,
        onLoaded: () -> Unit,
    ) {
        inlineImageCache.get(pendingImage.resolvedUrl)?.let { bitmap ->
            pendingImage.drawable.setBitmap(bitmap)
            onLoaded()
            return
        }

        inlineImageExecutor.execute {
            val bitmap = fetchInlineBitmap(
                resolvedUrl = pendingImage.resolvedUrl,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
            ) ?: return@execute
            Handler(Looper.getMainLooper()).post {
                if (cancelled) {
                    return@post
                }
                pendingImage.drawable.setBitmap(bitmap)
                onLoaded()
            }
        }
    }
}

internal fun applyInlineImagePlaceholders(
    text: CharSequence,
    inlineImages: List<InlineImageSpec>,
    baseUrl: String,
    textSizePx: Float,
): AppliedInlineImages {
    val builder = SpannableStringBuilder(text)
    val pendingImages = mutableListOf<PendingInlineImage>()
    val searchableText = text.toString()
    var replacedCharacterCount = 0

    inlineImages.forEach { image ->
        val originalStart = searchableText.indexOf(image.token)
        if (originalStart == -1) {
            return@forEach
        }

        val start = originalStart - replacedCharacterCount
        val end = start + image.token.length
        val drawable = DeferredInlineImageDrawable(
            widthPx = image.displayWidthPx(textSizePx),
            heightPx = image.displayHeightPx(textSizePx),
        )
        builder.replace(start, end, "\uFFFC")
        builder.setSpan(
            CenteredInlineImageSpan(
                drawable = drawable,
                kind = image.kind,
                emojiScale = emojiInlineImageScaleForUrl(image.rawUrl),
            ),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        pendingImages += PendingInlineImage(
            resolvedUrl = resolvePostImageUrl(
                baseUrl = baseUrl,
                rawUrl = image.rawUrl,
            ),
            drawable = drawable,
        )
        replacedCharacterCount += image.token.length - 1
    }

    return AppliedInlineImages(
        text = builder,
        pendingImages = pendingImages,
    )
}

private fun emojiInlineImageScaleForUrl(rawUrl: String): Float {
    return if (rawUrl.contains("/images/emoji/twemoji/")) {
        twemojiInlineImageScale
    } else {
        customEmojiInlineImageScale
    }
}

private fun calculateInlineImageTranslationY(
    top: Int,
    bottom: Int,
    baseline: Int,
    fontDescent: Int,
    drawableHeight: Int,
    kind: InlineImageKind,
): Float {
    val upwardBiasRatio = when (kind) {
        InlineImageKind.Emoji -> emojiInlineImageUpwardBiasRatio
        InlineImageKind.SiteIcon -> siteIconInlineImageUpwardBiasRatio
    }
    val translationY = when (kind) {
        InlineImageKind.Emoji -> baseline + fontDescent - drawableHeight.toFloat()
        InlineImageKind.SiteIcon -> top + ((bottom - top - drawableHeight) / 2f)
    }
    return translationY - (drawableHeight * upwardBiasRatio)
}

private fun calculateInlineImageSizePx(
    fontMetrics: Paint.FontMetricsInt,
    kind: InlineImageKind,
    emojiScale: Float = customEmojiInlineImageScale,
): Int {
    val fontHeight = max(1, abs(fontMetrics.ascent) + abs(fontMetrics.descent))
    val scale = when (kind) {
        InlineImageKind.Emoji -> emojiScale
        InlineImageKind.SiteIcon -> siteIconInlineImageScale
    }
    return max(1, (fontHeight * scale).roundToInt())
}

private class CenteredInlineImageSpan(
    drawable: Drawable,
    private val kind: InlineImageKind,
    private val emojiScale: Float = customEmojiInlineImageScale,
) : ImageSpan(drawable, ALIGN_BASELINE) {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val paintMetrics = paint.fontMetricsInt
        val targetSizePx = calculateInlineImageSizePx(
            fontMetrics = paintMetrics,
            kind = kind,
            emojiScale = emojiScale,
        )
        drawable.setBounds(0, 0, targetSizePx, targetSizePx)
        if (fm != null) {
            fm.ascent = paintMetrics.ascent
            fm.descent = paintMetrics.descent
            fm.top = paintMetrics.top
            fm.bottom = paintMetrics.bottom
        }
        return targetSizePx
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val drawable = drawable
        canvas.save()
        canvas.translate(
            x,
            calculateInlineImageTranslationY(
                top = top,
                bottom = bottom,
                baseline = y,
                fontDescent = paint.fontMetricsInt.descent,
                drawableHeight = drawable.bounds.height(),
                kind = kind,
            ),
        )
        drawable.draw(canvas)
        canvas.restore()
    }
}

@Suppress("OVERRIDE_DEPRECATION")
internal class DeferredInlineImageDrawable(
    widthPx: Int,
    heightPx: Int,
) : Drawable() {
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x335D6A7A
    }
    private var bitmap: Bitmap? = null

    init {
        setBounds(0, 0, max(1, widthPx), max(1, heightPx))
    }

    fun setBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        val targetBounds = bounds
        val maxWidth = max(1, targetBounds.width())
        val maxHeight = max(1, targetBounds.height())
        val scale = minOf(
            maxWidth.toFloat() / bitmap.width.toFloat(),
            maxHeight.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        setBounds(0, 0, scaledWidth, scaledHeight)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bitmap = bitmap
        if (bitmap == null) {
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                bounds.width() * 0.18f,
                bounds.height() * 0.18f,
                placeholderPaint,
            )
            return
        }

        canvas.drawBitmap(bitmap, null, bounds, null)
    }

    override fun setAlpha(alpha: Int) {
        placeholderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        placeholderPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun fetchInlineBitmap(
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Bitmap? {
    inlineImageCache.get(resolvedUrl)?.let { return it }
    val imageUrl = runCatching { resolvedUrl.toHttpUrl() }.getOrNull() ?: return null
    val cookieHeader = cookieStore.load(imageUrl)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    val request = Request.Builder()
        .url(imageUrl)
        .apply { addImageRequestHeaders(baseUrl = baseUrl) }
        .apply {
            if (!cookieHeader.isNullOrBlank()) {
                header("Cookie", cookieHeader)
            }
        }
        .build()
    return runCatching {
        inlineImageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@runCatching null
            }
            response.body?.bytes()
                ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                ?.also { bitmap -> inlineImageCache.put(resolvedUrl, bitmap) }
        }
    }.getOrNull()
}

private class InlineMemoryCache<T>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<String, T>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun put(key: String, value: T) {
        entries[key] = value
    }
}
