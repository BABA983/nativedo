package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.caverock.androidsvg.SVG
import io.github.baba983.nativedo.core.log.NativeDoLogTag
import io.github.baba983.nativedo.core.log.warnLog
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser
import java.util.LinkedHashMap

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
    val colors = NativeDoThemeExtras.colors
    val isImageFlair = remember(flairUrl) { looksLikeFlairImageUrl(flairUrl) }
    val badgeBackgroundColor = remember(flairBgColor, colors.glassBackground) {
        discourseFlairColor(
            rawHex = flairBgColor,
            fallback = colors.glassBackground,
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
    url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://")

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
    val symbolId = normalizeFlairIconName(rawFlairUrl)
    if (symbolId.isBlank()) {
        return null
    }

    val document = Jsoup.parse("<root>$spriteMarkup</root>", "", Parser.xmlParser())
    val symbols = document.select("symbol")
    val symbolsById = symbols.associateBy { symbol -> symbol.attr("id") }
    val targetSymbol = symbols.firstOrNull { symbol ->
        symbol.attr("id") == symbolId
    } ?: return null
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
    return runCatching {
        val svg = SVG.getFromString(svgDocument)
        svg.setDocumentWidth(widthPx.toFloat())
        svg.setDocumentHeight(heightPx.toFloat())

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        svg.renderToCanvas(canvas)
        bitmap.asImageBitmap()
    }.getOrElse { error ->
        warnLog(
            tag = NativeDoLogTag.Ui,
            message = "flair svg render failed width=$widthPx height=$heightPx length=${svgDocument.length}",
            error = error,
        )
        null
    }
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
