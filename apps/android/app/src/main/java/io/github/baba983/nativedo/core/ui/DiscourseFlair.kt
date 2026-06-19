package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.core.graphics.PathParser
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import org.telegram.ui.ActionBar.Theme
import io.github.baba983.nativedo.ui.actionbar.themedColor
import org.telegram.messenger.SvgHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun UserAvatarWithFlair(
    label: String,
    avatarTemplate: String,
    animatedAvatarUrl: String = "",
    flairName: String = "",
    flairUrl: String = "",
    flairBgColor: String = "",
    flairColor: String = "",
    svgSpriteMarkup: String = "",
    baseUrl: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    flairBadgeSize: Dp = 14.dp,
    flairContentSize: Dp = 12.dp,
) {
    val fallbackBadgeBackground = themedColor(Theme.key_windowBackgroundWhite)
    val isImageFlair = remember(flairUrl) { looksLikeFlairImageUrl(flairUrl) }
    val badgeBackgroundColor = remember(flairBgColor, fallbackBadgeBackground) {
        discourseFlairColor(
            rawHex = flairBgColor,
            fallback = fallbackBadgeBackground,
        )
    }
    val flairSvgDocument = remember(svgSpriteMarkup, flairUrl, flairColor, isImageFlair) {
        if (isImageFlair) {
            null
        } else {
            cachedDiscourseFlairSvgDocument(
                spriteMarkup = svgSpriteMarkup,
                rawFlairUrl = flairUrl,
                rawTintColor = flairColor,
            )
        }
    }

    Box(modifier = modifier.size(avatarSize)) {
        UserAvatar(
            label = label,
            avatarTemplate = avatarTemplate,
            animatedAvatarUrl = animatedAvatarUrl,
            baseUrl = baseUrl,
            size = avatarSize,
        )
        when {
            isImageFlair -> {
                FlairBadge(
                    backgroundColor = badgeBackgroundColor,
                    badgeSize = flairBadgeSize,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FlairImage(
                        rawUrl = flairUrl,
                        label = flairName,
                        baseUrl = baseUrl,
                        size = flairContentSize,
                    )
                }
            }

            flairSvgDocument != null -> {
                FlairBadge(
                    backgroundColor = badgeBackgroundColor,
                    badgeSize = flairBadgeSize,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FlairSvg(
                        svgDocument = flairSvgDocument,
                        label = flairName,
                        size = flairContentSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlairBadge(
    backgroundColor: Color,
    badgeSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun FlairImage(
    rawUrl: String,
    label: String,
    baseUrl: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedUrl = remember(baseUrl, rawUrl) {
        resolvePostImageUrl(baseUrl = baseUrl, rawUrl = rawUrl)
    }
    if (resolvedUrl.isBlank()) {
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedUrl)
            .build(),
        contentDescription = label.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
private fun FlairSvg(
    svgDocument: String,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val imageBitmap = remember(svgDocument, sizePx) {
        cachedFlairImageBitmap(
            svgDocument = svgDocument,
            widthPx = sizePx,
            heightPx = sizePx,
        )
    } ?: return

    Image(
        bitmap = imageBitmap,
        contentDescription = label.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

internal fun looksLikeFlairImageUrl(url: String): Boolean =
    looksLikeDiscourseImageUrl(url)

internal fun normalizeFlairIconName(rawValue: String): String {
    return rawValue
        .trim()
        .substringAfterLast('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast(' ')
        .removePrefix("fa-")
        .removePrefix("far-")
        .removePrefix("fas-")
        .removePrefix("fab-")
        .lowercase()
}

internal fun buildDiscourseFlairSvgDocument(
    spriteMarkup: String,
    rawFlairUrl: String,
    rawTintColor: String,
): String? {
    if (spriteMarkup.isBlank()) {
        return null
    }
    val rawSymbolId = rawFlairUrl
        .trim()
        .substringAfterLast('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast(' ')
    val symbolIds = listOf(
        rawSymbolId,
        normalizeFlairIconName(rawFlairUrl),
    ).filter { symbolId ->
        symbolId.isNotBlank()
    }.distinct()
    if (symbolIds.isEmpty()) {
        return null
    }

    val document = Jsoup.parse("<root>$spriteMarkup</root>", "", Parser.xmlParser())
    val symbols = document.select("symbol")
    val symbolsById = symbols.associateBy { symbol -> symbol.attr("id") }
    val targetSymbol = symbolIds.firstNotNullOfOrNull(symbolsById::get) ?: return null
    val viewBox = targetSymbol.attr("viewBox").ifBlank { return null }
    val tintHex = normalizeFlairTintHex(rawTintColor)
    val expandedMarkup = expandSymbolContent(
        symbol = targetSymbol,
        symbolsById = symbolsById,
        tintHex = tintHex,
    ).ifBlank { return null }
    val viewBoxDimensions = viewBox.trim().split(Regex("\\s+"))
    val width = viewBoxDimensions.getOrNull(2).orEmpty()
    val height = viewBoxDimensions.getOrNull(3).orEmpty()
    val sizeAttributes = buildString {
        if (width.isNotBlank()) {
            append(""" width="$width"""")
        }
        if (height.isNotBlank()) {
            append(""" height="$height"""")
        }
    }
    val rootColorAttributes = tintHex?.let { hex ->
        """ fill="$hex" color="$hex" """
    }.orEmpty()

    return """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox"$sizeAttributes$rootColorAttributes>
          $expandedMarkup
        </svg>
    """.trimIndent()
}

internal fun discourseFlairColor(
    rawHex: String,
    fallback: Color,
): Color {
    val normalizedHex = normalizeFlairColorHex(rawHex) ?: return fallback
    val hexWithoutPrefix = normalizedHex.removePrefix("#")
    val argbHex = when (hexWithoutPrefix.length) {
        6 -> "FF$hexWithoutPrefix"
        8 -> hexWithoutPrefix.takeLast(2) + hexWithoutPrefix.dropLast(2)
        else -> return fallback
    }
    return argbHex
        .toLongOrNull(radix = 16)
        ?.let { Color(it.toInt()) }
        ?: fallback
}

private fun cachedDiscourseFlairSvgDocument(
    spriteMarkup: String,
    rawFlairUrl: String,
    rawTintColor: String,
): String? {
    return DiscourseFlairRenderCache.svgDocument(
        key = FlairSvgDocumentCacheKey(
            spriteMarkup = spriteMarkup,
            rawFlairUrl = rawFlairUrl,
            rawTintColor = rawTintColor,
        ),
    ) {
        buildDiscourseFlairSvgDocument(
            spriteMarkup = spriteMarkup,
            rawFlairUrl = rawFlairUrl,
            rawTintColor = rawTintColor,
        )
    }
}

private fun normalizeFlairTintHex(rawHex: String): String? = normalizeFlairColorHex(rawHex)

private fun normalizeFlairColorHex(rawHex: String): String? {
    val normalizedHex = rawHex.trim().removePrefix("#")
    if (normalizedHex.isBlank()) {
        return null
    }
    return when (normalizedHex.length) {
        3 -> "#${normalizedHex.map { "$it$it" }.joinToString(separator = "")}"
        4 -> "#${normalizedHex.map { "$it$it" }.joinToString(separator = "")}"
        6, 8 -> "#$normalizedHex"
        else -> null
    }
}

private fun renderSvgDocumentToImageBitmap(
    svgDocument: String,
    widthPx: Int,
    heightPx: Int,
): ImageBitmap? {
    return renderDiscourseSvgDocumentToBitmap(
        svgDocument = svgDocument,
        widthPx = widthPx,
        heightPx = heightPx,
    )?.asImageBitmap()
}

internal fun renderDiscourseSvgDocumentToBitmap(
    svgDocument: String,
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)
    return runCatching {
        val drawable = buildDiscourseSvgDrawable(svgDocument) ?: return null
        drawable.setBounds(0, 0, safeWidth, safeHeight)
        Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
}

internal fun buildDiscourseSvgDrawable(svgDocument: String): Drawable? {
    val document = Jsoup.parse(svgDocument, "", Parser.xmlParser())
    val root = document.selectFirst("svg") ?: return null
    val viewBox = parseSvgViewBox(root) ?: return null
    val layers = mutableListOf<DiscourseSvgLayer>()
    collectDiscourseSvgLayers(
        element = root,
        inheritedStyle = SvgStyle.default(),
        inheritedTransform = "",
        layers = layers,
    )
    if (layers.isEmpty()) {
        return null
    }
    val intrinsicWidth = parseSvgFloat(root.attr("width"))?.toInt() ?: viewBox.width().toInt()
    val intrinsicHeight = parseSvgFloat(root.attr("height"))?.toInt() ?: viewBox.height().toInt()
    return DiscourseSvgDrawable(
        viewBox = viewBox,
        layers = layers,
        intrinsicWidth = intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight = intrinsicHeight.coerceAtLeast(1),
    )
}

private fun cachedFlairImageBitmap(
    svgDocument: String,
    widthPx: Int,
    heightPx: Int,
): ImageBitmap? {
    return DiscourseFlairRenderCache.bitmap(
        key = FlairBitmapCacheKey(
            svgDocument = svgDocument,
            widthPx = widthPx,
            heightPx = heightPx,
        ),
    ) {
        renderSvgDocumentToImageBitmap(
            svgDocument = svgDocument,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }
}

private data class FlairSvgDocumentCacheKey(
    val spriteMarkup: String,
    val rawFlairUrl: String,
    val rawTintColor: String,
)

private data class FlairBitmapCacheKey(
    val svgDocument: String,
    val widthPx: Int,
    val heightPx: Int,
)

private object DiscourseFlairRenderCache {
    private const val MAX_SVG_DOCUMENTS = 32
    private const val MAX_BITMAPS = 64

    private val svgDocumentCache = object : LinkedHashMap<FlairSvgDocumentCacheKey, String?>(MAX_SVG_DOCUMENTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FlairSvgDocumentCacheKey, String?>): Boolean {
            return size > MAX_SVG_DOCUMENTS
        }
    }
    private val bitmapCache = object : LinkedHashMap<FlairBitmapCacheKey, ImageBitmap>(MAX_BITMAPS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FlairBitmapCacheKey, ImageBitmap>): Boolean {
            return size > MAX_BITMAPS
        }
    }

    fun svgDocument(
        key: FlairSvgDocumentCacheKey,
        producer: () -> String?,
    ): String? {
        synchronized(svgDocumentCache) {
            if (svgDocumentCache.containsKey(key)) {
                return svgDocumentCache[key]
            }
        }
        val value = producer()
        synchronized(svgDocumentCache) {
            svgDocumentCache[key] = value
        }
        return value
    }

    fun bitmap(
        key: FlairBitmapCacheKey,
        producer: () -> ImageBitmap?,
    ): ImageBitmap? {
        synchronized(bitmapCache) {
            bitmapCache[key]?.let { return it }
        }
        val value = producer() ?: return null
        synchronized(bitmapCache) {
            bitmapCache[key] = value
        }
        return value
    }
}

private fun collectDiscourseSvgLayers(
    element: Element,
    inheritedStyle: SvgStyle,
    inheritedTransform: String,
    layers: MutableList<DiscourseSvgLayer>,
) {
    val tagName = element.normalName()
    if (tagName == "style" || tagName == "script" || tagName == "defs") {
        return
    }

    val style = inheritedStyle.merge(element)
    val transform = listOf(
        inheritedTransform.takeIf { it.isNotBlank() },
        element.attr("transform").trim().takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString(separator = " ")

    if (tagName == "path" && element.hasAttr("d")) {
        parseDiscourseSvgPathLayer(
            element = element,
            style = style,
            transform = transform,
        )?.let(layers::add)
    }

    element.children().forEach { child ->
        collectDiscourseSvgLayers(
            element = child,
            inheritedStyle = style,
            inheritedTransform = transform,
            layers = layers,
        )
    }
}

private fun parseDiscourseSvgPathLayer(
    element: Element,
    style: SvgStyle,
    transform: String,
): DiscourseSvgLayer? {
    val path = runCatching {
        PathParser.createPathFromPathData(element.attr("d"))
    }.getOrNull() ?: return null
    val pathBounds = RectF()
    path.computeBounds(pathBounds, true)
    if (pathBounds.isEmpty) {
        return null
    }

    if (style.fillRule.equals("evenodd", ignoreCase = true)) {
        path.fillType = Path.FillType.EVEN_ODD
    }
    if (transform.isNotBlank()) {
        SvgHelper.parseTransform(transform)?.let(path::transform)
    }

    return DiscourseSvgLayer(
        path = path,
        fillColor = parseSvgColor(style.fill, style.color),
        fillAlpha = style.fillOpacity * style.opacity,
        strokeColor = parseSvgColor(style.stroke, style.color),
        strokeAlpha = style.strokeOpacity * style.opacity,
        strokeWidth = style.strokeWidth,
        strokeCap = parseStrokeCap(style.strokeLineCap),
        strokeJoin = parseStrokeJoin(style.strokeLineJoin),
    )
}

private fun parseSvgViewBox(root: Element): RectF? {
    val values = root.attr("viewBox")
        .trim()
        .split(Regex("[,\\s]+"))
        .mapNotNull { value -> value.toFloatOrNull() }
    if (values.size >= 4 && values[2] > 0f && values[3] > 0f) {
        return RectF(values[0], values[1], values[0] + values[2], values[1] + values[3])
    }

    val width = parseSvgFloat(root.attr("width"))
    val height = parseSvgFloat(root.attr("height"))
    return if (width != null && height != null && width > 0f && height > 0f) {
        RectF(0f, 0f, width, height)
    } else {
        null
    }
}

private data class DiscourseSvgLayer(
    val path: Path,
    @ColorInt val fillColor: Int?,
    val fillAlpha: Float,
    @ColorInt val strokeColor: Int?,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val strokeCap: Paint.Cap,
    val strokeJoin: Paint.Join,
)

private data class SvgStyle(
    val fill: String?,
    val stroke: String?,
    val color: String?,
    val fillOpacity: Float,
    val strokeOpacity: Float,
    val opacity: Float,
    val strokeWidth: Float,
    val strokeLineCap: String?,
    val strokeLineJoin: String?,
    val fillRule: String?,
) {
    fun merge(element: Element): SvgStyle {
        val values = LinkedHashMap<String, String>()
        element.attributes().forEach { attribute ->
            values[attribute.key.lowercase()] = attribute.value.trim()
        }
        parseSvgStyleDeclarations(element.attr("style")).forEach { (key, value) ->
            values[key] = value
        }

        return copy(
            fill = values["fill"] ?: fill,
            stroke = values["stroke"] ?: stroke,
            color = values["color"] ?: color,
            fillOpacity = fillOpacity * parseSvgAlpha(values["fill-opacity"], 1f),
            strokeOpacity = strokeOpacity * parseSvgAlpha(values["stroke-opacity"], 1f),
            opacity = opacity * parseSvgAlpha(values["opacity"], 1f),
            strokeWidth = parseSvgFloat(values["stroke-width"]) ?: strokeWidth,
            strokeLineCap = values["stroke-linecap"] ?: strokeLineCap,
            strokeLineJoin = values["stroke-linejoin"] ?: strokeLineJoin,
            fillRule = values["fill-rule"] ?: fillRule,
        )
    }

    companion object {
        fun default(): SvgStyle = SvgStyle(
            fill = "#000000",
            stroke = "none",
            color = "#000000",
            fillOpacity = 1f,
            strokeOpacity = 1f,
            opacity = 1f,
            strokeWidth = 1f,
            strokeLineCap = null,
            strokeLineJoin = null,
            fillRule = null,
        )
    }
}

private class DiscourseSvgDrawable(
    private val viewBox: RectF,
    private val layers: List<DiscourseSvgLayer>,
    private val intrinsicWidth: Int,
    private val intrinsicHeight: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alpha = 255
    private var colorFilter: ColorFilter? = null
    private var aspectFill = false

    fun setAspectFill(aspectFill: Boolean) {
        this.aspectFill = aspectFill
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty || viewBox.width() <= 0f || viewBox.height() <= 0f) {
            return
        }
        val scaleX = bounds.width().toFloat() / viewBox.width()
        val scaleY = bounds.height().toFloat() / viewBox.height()
        val scale = if (aspectFill) max(scaleX, scaleY) else min(scaleX, scaleY)
        val dx = bounds.left + (bounds.width() - viewBox.width() * scale) / 2f - viewBox.left * scale
        val dy = bounds.top + (bounds.height() - viewBox.height() * scale) / 2f - viewBox.top * scale

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        layers.forEach { layer ->
            layer.fillColor?.let { color ->
                drawLayer(
                    canvas = canvas,
                    layer = layer,
                    color = color,
                    layerAlpha = layer.fillAlpha,
                    style = Paint.Style.FILL,
                )
            }
            layer.strokeColor?.takeIf { layer.strokeWidth > 0f }?.let { color ->
                drawLayer(
                    canvas = canvas,
                    layer = layer,
                    color = color,
                    layerAlpha = layer.strokeAlpha,
                    style = Paint.Style.STROKE,
                )
            }
        }
        canvas.restore()
    }

    private fun drawLayer(
        canvas: Canvas,
        layer: DiscourseSvgLayer,
        @ColorInt color: Int,
        layerAlpha: Float,
        style: Paint.Style,
    ) {
        val alphaFactor = (layerAlpha.coerceIn(0f, 1f) * (alpha / 255f)).coerceIn(0f, 1f)
        if (alphaFactor <= 0f) {
            return
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = style
        paint.color = colorWithAlpha(color, alphaFactor)
        paint.colorFilter = colorFilter
        if (style == Paint.Style.STROKE) {
            paint.strokeWidth = layer.strokeWidth
            paint.strokeCap = layer.strokeCap
            paint.strokeJoin = layer.strokeJoin
        }
        canvas.drawPath(layer.path, paint)
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = intrinsicWidth

    override fun getIntrinsicHeight(): Int = intrinsicHeight
}

private fun parseSvgStyleDeclarations(style: String): Map<String, String> {
    if (style.isBlank()) {
        return emptyMap()
    }
    return style
        .split(';')
        .mapNotNull { declaration ->
            val key = declaration.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
            val value = declaration.substringAfter(':', missingDelimiterValue = "").trim()
            if (key.isBlank() || value.isBlank()) {
                null
            } else {
                key to value
            }
        }
        .toMap()
}

private fun parseSvgAlpha(value: String?, fallback: Float): Float {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
    return when {
        raw.endsWith("%") -> raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        else -> raw.toFloatOrNull()
    }?.coerceIn(0f, 1f) ?: fallback
}

private fun parseSvgFloat(value: String?): Float? {
    return value
        ?.trim()
        ?.removeSuffix("px")
        ?.takeIf { it.isNotBlank() }
        ?.toFloatOrNull()
}

@ColorInt
private fun parseSvgColor(rawColor: String?, currentColor: String?): Int? {
    val raw = rawColor?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (raw.equals("none", ignoreCase = true)) {
        return null
    }
    if (raw.equals("currentColor", ignoreCase = true)) {
        return parseSvgColor(currentColor, null)
    }
    if (raw.equals("transparent", ignoreCase = true)) {
        return android.graphics.Color.TRANSPARENT
    }
    parseSvgHexColor(raw)?.let { return it }
    parseSvgFunctionalColor(raw)?.let { return it }
    return when (raw.lowercase()) {
        "black" -> android.graphics.Color.BLACK
        "white" -> android.graphics.Color.WHITE
        "red" -> android.graphics.Color.RED
        "green" -> android.graphics.Color.GREEN
        "blue" -> android.graphics.Color.BLUE
        else -> runCatching { android.graphics.Color.parseColor(raw) }.getOrNull()
    }
}

@ColorInt
private fun parseSvgHexColor(rawColor: String): Int? {
    if (!rawColor.startsWith("#")) {
        return null
    }
    val hex = rawColor.removePrefix("#")
    val argb = when (hex.length) {
        3 -> "FF" + hex.map { "$it$it" }.joinToString(separator = "")
        4 -> {
            val expanded = hex.map { "$it$it" }
            expanded[3] + expanded[0] + expanded[1] + expanded[2]
        }
        6 -> "FF$hex"
        8 -> hex.takeLast(2) + hex.dropLast(2)
        else -> return null
    }
    return argb.toLongOrNull(16)?.toInt()
}

@ColorInt
private fun parseSvgFunctionalColor(rawColor: String): Int? {
    val lower = rawColor.lowercase()
    val isRgb = lower.startsWith("rgb(")
    val isRgba = lower.startsWith("rgba(")
    if (!isRgb && !isRgba) {
        return null
    }
    val values = rawColor.substringAfter('(').substringBeforeLast(')')
        .split(',', ' ', '/')
        .mapNotNull { part -> part.trim().takeIf { it.isNotBlank() } }
    if (values.size < 3) {
        return null
    }
    fun channel(value: String): Int? {
        return if (value.endsWith("%")) {
            (value.removeSuffix("%").trim().toFloatOrNull()?.times(2.55f))?.roundToInt()
        } else {
            value.toFloatOrNull()?.roundToInt()
        }?.coerceIn(0, 255)
    }
    val red = channel(values[0]) ?: return null
    val green = channel(values[1]) ?: return null
    val blue = channel(values[2]) ?: return null
    val alpha = values.getOrNull(3)
        ?.let { parseSvgAlpha(it, 1f) }
        ?.times(255f)
        ?.roundToInt()
        ?.coerceIn(0, 255)
        ?: 255
    return android.graphics.Color.argb(alpha, red, green, blue)
}

@ColorInt
private fun colorWithAlpha(@ColorInt color: Int, alphaFactor: Float): Int {
    val alpha = (android.graphics.Color.alpha(color) * alphaFactor).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )
}

private fun parseStrokeCap(rawCap: String?): Paint.Cap {
    return when (rawCap?.trim()?.lowercase()) {
        "round" -> Paint.Cap.ROUND
        "square" -> Paint.Cap.SQUARE
        else -> Paint.Cap.BUTT
    }
}

private fun parseStrokeJoin(rawJoin: String?): Paint.Join {
    return when (rawJoin?.trim()?.lowercase()) {
        "round" -> Paint.Join.ROUND
        "bevel" -> Paint.Join.BEVEL
        else -> Paint.Join.MITER
    }
}

private fun expandSymbolContent(
    symbol: Element,
    symbolsById: Map<String, Element>,
    tintHex: String?,
): String {
    return symbol.childNodes()
        .flatMap { node ->
            expandSvgNode(
                node = node,
                symbolsById = symbolsById,
                tintHex = tintHex,
            )
        }
        .joinToString(separator = "") { node -> node.outerHtml() }
}

private fun expandSvgNode(
    node: Node,
    symbolsById: Map<String, Element>,
    tintHex: String?,
): List<Node> {
    val element = node as? Element ?: return listOf(node.clone())
    if (element.normalName() == "use") {
        return expandUseElement(
            useElement = element,
            symbolsById = symbolsById,
            tintHex = tintHex,
        )
    }

    val clone = Element(element.tag(), element.baseUri())
    element.attributes().forEach { attribute ->
        clone.attr(attribute.key, sanitizeSvgAttributeValue(attribute.key, attribute.value, tintHex))
    }
    if (tintHex != null && element.isPaintableSvgElement() && !clone.hasAttr("fill") && !clone.hasAttr("stroke") && !clone.hasAttr("color")) {
        clone.attr("fill", tintHex)
    }
    element.childNodes().forEach { child ->
        expandSvgNode(
            node = child,
            symbolsById = symbolsById,
            tintHex = tintHex,
        ).forEach { expandedChild ->
            clone.appendChild(expandedChild)
        }
    }
    return listOf(clone)
}

private fun Element.isPaintableSvgElement(): Boolean {
    return normalName() in setOf(
        "path",
        "circle",
        "ellipse",
        "line",
        "polygon",
        "polyline",
        "rect",
    )
}

private fun expandUseElement(
    useElement: Element,
    symbolsById: Map<String, Element>,
    tintHex: String?,
): List<Node> {
    val referencedSymbolId = useElement.attr("href")
        .ifBlank { useElement.attr("xlink:href") }
        .substringAfterLast('#')
        .trim()
    val referencedSymbol = symbolsById[referencedSymbolId] ?: return emptyList()
    val wrapper = Element("g")

    useElement.attributes().forEach { attribute ->
        when (attribute.key) {
            "href",
            "xlink:href",
            -> Unit

            "x",
            "y",
            -> Unit

            else -> wrapper.attr(
                attribute.key,
                sanitizeSvgAttributeValue(attribute.key, attribute.value, tintHex),
            )
        }
    }

    val x = useElement.attr("x").trim()
    val y = useElement.attr("y").trim()
    val translate = when {
        x.isNotBlank() && y.isNotBlank() -> "translate($x $y)"
        x.isNotBlank() -> "translate($x 0)"
        y.isNotBlank() -> "translate(0 $y)"
        else -> ""
    }
    val transform = useElement.attr("transform").trim()
    val mergedTransform = listOfNotNull(
        translate.takeIf { it.isNotBlank() },
        transform.takeIf { it.isNotBlank() },
    ).joinToString(separator = " ")
    if (mergedTransform.isNotBlank()) {
        wrapper.attr("transform", mergedTransform)
    }

    referencedSymbol.childNodes().forEach { child ->
        expandSvgNode(
            node = child,
            symbolsById = symbolsById,
            tintHex = tintHex,
        ).forEach { expandedChild ->
            wrapper.appendChild(expandedChild)
        }
    }

    return if (wrapper.attributesSize() == 0) {
        wrapper.childNodes()
            .map { child -> child.clone() }
    } else {
        listOf(wrapper)
    }
}

private fun sanitizeSvgAttributeValue(
    key: String,
    value: String,
    tintHex: String?,
): String {
    if (tintHex != null && value.equals("currentColor", ignoreCase = true)) {
        return tintHex
    }
    if (value.startsWith("var(")) {
        return when (key) {
            "fill",
            "stroke",
            "color",
            "stop-color",
            -> tintHex ?: "currentColor"

            else -> value
        }
    }
    return value
}
