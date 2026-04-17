package io.github.baba983.nativedo.feature.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderBlock
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal interface AppBrowserInstantViewResolveHost {
    val context: Context
    val url: String?
    val title: String?

    fun captureArchiveSnapshot(
        onResult: (AppBrowserInstantViewArchiveSnapshot?) -> Unit,
    )

    fun evaluateJavascript(
        script: String,
        onResult: (String?) -> Unit,
    )
}

internal class AppBrowserInstantViewResolver(
    private val hiddenExtraction: (
        AppBrowserInstantViewResolveHost,
        String,
        AppBrowserInstantViewArchiveSnapshot?,
        String,
        (String?) -> Unit,
    ) -> Unit = ::runHiddenAppBrowserInstantViewExtraction,
) {
    fun resolve(
        host: AppBrowserInstantViewResolveHost?,
        expectedUrl: String,
        onResolved: (AppBrowserReaderDocument?) -> Unit,
    ) {
        if (host == null || expectedUrl.isBlank()) {
            onResolved(null)
            return
        }
        host.captureArchiveSnapshot { archiveSnapshot ->
            if (host.url != expectedUrl) {
                return@captureArchiveSnapshot
            }
            if (archiveSnapshot != null) {
                resolveFromSanitizedHtml(
                    host = host,
                    expectedUrl = expectedUrl,
                    archiveSnapshot = archiveSnapshot,
                    sanitizedHtml = archiveSnapshot.mainHtml,
                    onResolved = onResolved,
                )
                return@captureArchiveSnapshot
            }
            resolveFromOuterHtmlSnapshot(
                host = host,
                expectedUrl = expectedUrl,
                onResolved = onResolved,
            )
        }
    }

    private fun resolveFromOuterHtmlSnapshot(
        host: AppBrowserInstantViewResolveHost,
        expectedUrl: String,
        onResolved: (AppBrowserReaderDocument?) -> Unit,
    ) {
        host.evaluateJavascript(appBrowserInstantViewHtmlJavascript) { htmlValue ->
            if (host.url != expectedUrl) {
                return@evaluateJavascript
            }
            resolveFromSnapshot(
                host = host,
                expectedUrl = expectedUrl,
                htmlSnapshot = htmlValue.parseAppBrowserJavascriptString(),
                onResolved = onResolved,
            )
        }
    }

    private fun resolveFromSnapshot(
        host: AppBrowserInstantViewResolveHost,
        expectedUrl: String,
        htmlSnapshot: String?,
        onResolved: (AppBrowserReaderDocument?) -> Unit,
    ) {
        val sanitizedHtml = htmlSnapshot
            ?.takeIf(String::isNotBlank)
            ?.let(::sanitizeAppBrowserInstantViewHtmlSnapshot)
        if (sanitizedHtml.isNullOrBlank()) {
            resolveFromFallbackText(
                host = host,
                expectedUrl = expectedUrl,
                onResolved = onResolved,
            )
            return
        }
        resolveFromSanitizedHtml(
            host = host,
            expectedUrl = expectedUrl,
            archiveSnapshot = null,
            sanitizedHtml = sanitizedHtml,
            onResolved = onResolved,
        )
    }

    private fun resolveFromSanitizedHtml(
        host: AppBrowserInstantViewResolveHost,
        expectedUrl: String,
        archiveSnapshot: AppBrowserInstantViewArchiveSnapshot?,
        sanitizedHtml: String,
        onResolved: (AppBrowserReaderDocument?) -> Unit,
    ) {
        hiddenExtraction(
            host,
            expectedUrl,
            archiveSnapshot,
            sanitizedHtml,
        ) { scriptJson ->
            val extractedFromScript = parseAppBrowserInstantViewJsonDocument(
                pageUrl = expectedUrl,
                json = scriptJson,
            )?.resolveArchiveBackedReaderMedia(archiveSnapshot)
            if (extractedFromScript != null) {
                onResolved(extractedFromScript)
                return@hiddenExtraction
            }
            val extractedFromHtml = extractAppBrowserReaderDocumentFromHtml(
                html = sanitizedHtml,
                pageUrl = expectedUrl,
            )?.resolveArchiveBackedReaderMedia(archiveSnapshot)
            if (extractedFromHtml != null) {
                onResolved(extractedFromHtml)
                return@hiddenExtraction
            }
            resolveFromFallbackText(
                host = host,
                expectedUrl = expectedUrl,
                onResolved = onResolved,
            )
        }
    }

    private fun resolveFromFallbackText(
        host: AppBrowserInstantViewResolveHost,
        expectedUrl: String,
        onResolved: (AppBrowserReaderDocument?) -> Unit,
    ) {
        host.evaluateJavascript(appBrowserInstantViewTextJavascript) { rawValue ->
            if (host.url != expectedUrl) {
                return@evaluateJavascript
            }
            val normalizedBodyText = normalizeAppBrowserReaderBodyText(
                rawValue.parseAppBrowserJavascriptString(),
            ).takeIf(String::isNotBlank)
            onResolved(
                normalizedBodyText?.let { bodyText ->
                    buildAppBrowserReaderContent(
                        url = expectedUrl,
                        title = host.title,
                        bodyText = bodyText,
                    )?.document
                },
            )
        }
    }
}

internal fun runHiddenAppBrowserInstantViewExtraction(
    host: AppBrowserInstantViewResolveHost,
    pageUrl: String,
    archiveSnapshot: AppBrowserInstantViewArchiveSnapshot?,
    sanitizedHtml: String,
    onResolved: (String?) -> Unit,
) {
    val activity = host.context.findActivity()
    if (activity == null) {
        onResolved(null)
        return
    }
    val replaySnapshot = archiveSnapshot ?: buildAppBrowserInstantViewHtmlSnapshot(
        pageUrl = pageUrl,
        sanitizedHtml = sanitizedHtml,
    )
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
    val container = FrameLayout(host.context).apply {
        layoutParams = ViewGroup.LayoutParams(1, 1)
        alpha = 0f
    }
    val hiddenWebView = WebView(host.context)
    var completed = false
    var firstRequestPending = true

    fun finish(result: String?) {
        if (completed) {
            return
        }
        completed = true
        hiddenWebView.removeJavascriptInterface("NativeDoInstantView")
        hiddenWebView.stopLoading()
        hiddenWebView.destroy()
        container.removeAllViews()
        rootView.removeView(container)
        onResolved(result)
    }

    hiddenWebView.settings.javaScriptEnabled = true
    hiddenWebView.settings.domStorageEnabled = false
    hiddenWebView.settings.allowFileAccess = false
    hiddenWebView.settings.allowContentAccess = false
    hiddenWebView.webViewClient = object : android.webkit.WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
        ): WebResourceResponse? {
            val requestUrl = request?.url?.toString()
            val target = requestUrl?.let { url ->
                resolveAppBrowserInstantViewReplayTarget(
                    snapshot = replaySnapshot,
                    requestUrl = url,
                    isFirstRequest = firstRequestPending,
                )
            }
            if (request?.isForMainFrame == true) {
                firstRequestPending = false
            }
            return when (target) {
                AppBrowserInstantViewReplayTarget.BootstrapDocument -> {
                    buildAppBrowserInstantViewBootstrapResponse(
                        pageUrl = replaySnapshot.pageUrl,
                    )
                }

                AppBrowserInstantViewReplayTarget.SnapshotIndex -> {
                    AppBrowserInstantViewArchiveResource(
                        mimeType = "text/html",
                        encoding = "UTF-8",
                        data = replaySnapshot.mainHtml.toByteArray(StandardCharsets.UTF_8),
                    ).toWebResourceResponse()
                }

                is AppBrowserInstantViewReplayTarget.LocalAsset -> {
                    buildAppBrowserInstantViewLocalAssetResponse(
                        context = host.context,
                        target = target,
                    )
                }

                is AppBrowserInstantViewReplayTarget.ArchiveResource -> {
                    resolveAppBrowserArchiveResource(
                        snapshot = replaySnapshot,
                        requestUrl = target.url,
                    )?.toWebResourceResponse()
                }

                AppBrowserInstantViewReplayTarget.Missing -> buildMissingAppBrowserArchiveResponse()
                null -> super.shouldInterceptRequest(view, request)
            }
        }
    }
    hiddenWebView.addJavascriptInterface(
        object {
            @JavascriptInterface
            fun onResult(result: String?) {
                hiddenWebView.post {
                    finish(result?.takeIf(String::isNotBlank))
                }
            }
        },
        "NativeDoInstantView",
    )
    container.addView(
        hiddenWebView,
        FrameLayout.LayoutParams(1, 1),
    )
    rootView.addView(container)
    hiddenWebView.loadUrl(replaySnapshot.pageUrl)
    hiddenWebView.postDelayed(
        { finish(null) },
        appBrowserInstantViewHiddenExtractionTimeoutMillis,
    )
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}

internal fun String?.parseAppBrowserJavascriptString(): String? {
    val candidate = this
        ?.trim()
        ?.takeUnless { value -> value == "null" }
        ?: return null
    return runCatching {
        JSONArray("[$candidate]").optString(0)
    }.getOrNull()
}

internal fun AppBrowserWebViewView.asInstantViewResolveHost(): AppBrowserInstantViewResolveHost {
    return object : AppBrowserInstantViewResolveHost {
        override val context: Context
            get() = this@asInstantViewResolveHost.context

        override val url: String?
            get() = this@asInstantViewResolveHost.url

        override val title: String?
            get() = this@asInstantViewResolveHost.title

        override fun captureArchiveSnapshot(
            onResult: (AppBrowserInstantViewArchiveSnapshot?) -> Unit,
        ) {
            this@asInstantViewResolveHost.captureArchiveSnapshot(onResult)
        }

        override fun evaluateJavascript(
            script: String,
            onResult: (String?) -> Unit,
        ) {
            this@asInstantViewResolveHost.evaluateJavascript(script, onResult)
        }
    }
}

internal fun AppBrowserWebViewView.captureArchiveSnapshot(
    onResult: (AppBrowserInstantViewArchiveSnapshot?) -> Unit,
) {
    val cacheDir = context.cacheDir ?: run {
        onResult(null)
        return
    }
    val archiveFile = runCatching {
        File.createTempFile("instant-view-", ".mht", cacheDir)
    }.getOrNull() ?: run {
        onResult(null)
        return
    }
    saveWebArchive(archiveFile.absolutePath, false) { savedPath ->
        val savedFile = savedPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?: archiveFile
        val archiveSnapshot = runCatching {
            savedFile.readBytes().toString(StandardCharsets.ISO_8859_1)
        }.getOrNull()?.let { mhtml ->
            extractAppBrowserArchiveSnapshotFromMhtml(
                mhtml = mhtml,
                fallbackPageUrl = url.orEmpty(),
            )
        }
        if (savedFile != archiveFile) {
            savedFile.delete()
        }
        archiveFile.delete()
        onResult(archiveSnapshot)
    }
}

internal fun extractAppBrowserHtmlFromMhtml(
    mhtml: String,
): String? {
    return extractAppBrowserArchiveSnapshotFromMhtml(
        mhtml = mhtml,
        fallbackPageUrl = "https://example.com/",
    )?.mainHtml
}

internal data class AppBrowserInstantViewArchiveSnapshot(
    val pageUrl: String,
    val mainHtml: String,
    val resourcesByUrl: Map<String, AppBrowserInstantViewArchiveResource>,
)

internal data class AppBrowserInstantViewArchiveResource(
    val mimeType: String,
    val encoding: String?,
    val data: ByteArray,
)

internal sealed interface AppBrowserInstantViewReplayTarget {
    data object BootstrapDocument : AppBrowserInstantViewReplayTarget

    data object SnapshotIndex : AppBrowserInstantViewReplayTarget

    data class LocalAsset(
        val fileName: String,
        val mimeType: String,
        val encoding: String,
    ) : AppBrowserInstantViewReplayTarget

    data class ArchiveResource(
        val url: String,
    ) : AppBrowserInstantViewReplayTarget

    data object Missing : AppBrowserInstantViewReplayTarget
}

internal fun buildAppBrowserInstantViewBootstrapHtml(
    pageUrl: String,
): String {
    val domPurifyUrl = buildAppBrowserInstantViewLocalAssetUrl(pageUrl, appBrowserInstantViewDomPurifyFileName)
    val readabilityUrl = buildAppBrowserInstantViewLocalAssetUrl(pageUrl, appBrowserInstantViewReadabilityFileName)
    val runtimeUrl = buildAppBrowserInstantViewLocalAssetUrl(pageUrl, appBrowserInstantViewRuntimeFileName)
    val indexUrl = buildAppBrowserInstantViewLocalAssetUrl(pageUrl, appBrowserInstantViewIndexFileName)
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>NativeDo Instant View</title>
          <script>
            window.NativeDoInstantViewConfig = {
              pageUrl: ${JSONObject.quote(pageUrl)},
              indexUrl: ${JSONObject.quote(indexUrl.orEmpty())}
            };
          </script>
          <script src="${domPurifyUrl.orEmpty()}"></script>
          <script src="${readabilityUrl.orEmpty()}"></script>
          <script src="${runtimeUrl.orEmpty()}"></script>
        </head>
        <body></body>
        </html>
    """.trimIndent()
}

internal fun resolveAppBrowserInstantViewReplayTarget(
    snapshot: AppBrowserInstantViewArchiveSnapshot,
    requestUrl: String,
    isFirstRequest: Boolean,
): AppBrowserInstantViewReplayTarget? {
    val normalizedRequestUrl = requestUrl.normalizeAppBrowserArchiveUrl() ?: return null
    if (isFirstRequest && normalizedRequestUrl == snapshot.pageUrl.normalizeAppBrowserArchiveUrl()) {
        return AppBrowserInstantViewReplayTarget.BootstrapDocument
    }
    if (normalizedRequestUrl == buildAppBrowserInstantViewLocalAssetUrl(snapshot.pageUrl, appBrowserInstantViewIndexFileName)) {
        return AppBrowserInstantViewReplayTarget.SnapshotIndex
    }
    appBrowserInstantViewLocalAssets.forEach { asset ->
        if (normalizedRequestUrl == buildAppBrowserInstantViewLocalAssetUrl(snapshot.pageUrl, asset.fileName)) {
            return AppBrowserInstantViewReplayTarget.LocalAsset(
                fileName = asset.fileName,
                mimeType = asset.mimeType,
                encoding = asset.encoding,
            )
        }
    }
    if (resolveAppBrowserArchiveResource(snapshot, normalizedRequestUrl) != null) {
        return AppBrowserInstantViewReplayTarget.ArchiveResource(normalizedRequestUrl)
    }
    return if (normalizedRequestUrl.toHttpUrlOrNull()?.scheme in setOf("http", "https")) {
        AppBrowserInstantViewReplayTarget.Missing
    } else {
        null
    }
}

internal fun extractAppBrowserArchiveSnapshotFromMhtml(
    mhtml: String,
    fallbackPageUrl: String,
): AppBrowserInstantViewArchiveSnapshot? {
    val normalizedMhtml = mhtml.replace("\r\n", "\n")
    val topLevelHeaders = parseAppBrowserMhtmlHeaders(
        normalizedMhtml.substringBefore("\n\n"),
    )
    val boundary = Regex("""boundary="?([^";\n]+)""", RegexOption.IGNORE_CASE)
        .find(topLevelHeaders["content-type"].orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val boundaryToken = "--$boundary"
    val parsedResources = normalizedMhtml
        .split(boundaryToken)
        .asSequence()
        .drop(1)
        .map { part ->
            part
                .removePrefix("\n")
                .removeSuffix("--")
                .trim('\n')
        }
        .filter(String::isNotBlank)
        .mapNotNull { part ->
            parseAppBrowserArchivePart(
                rawPart = part,
                fallbackPageUrl = fallbackPageUrl,
            )
        }
        .toList()
    if (parsedResources.isEmpty()) {
        return null
    }
    val mainResource = parsedResources.firstOrNull { resource ->
        resource.mimeType.equals("text/html", ignoreCase = true) &&
            resource.url == fallbackPageUrl.normalizeAppBrowserArchiveUrl()
    } ?: parsedResources.firstOrNull { resource ->
        resource.mimeType.equals("text/html", ignoreCase = true)
    } ?: return null
    val mainHtml = mainResource
        .data
        .toString(StandardCharsets.UTF_8)
        .trim()
        .takeIf(String::isNotBlank)
        ?: return null
    val pageUrl = mainResource.url ?: fallbackPageUrl.normalizeAppBrowserArchiveUrl() ?: return null
    val resourcesByUrl = linkedMapOf<String, AppBrowserInstantViewArchiveResource>()
    parsedResources.forEach { resource ->
        val resolvedUrl = resource.url ?: return@forEach
        if (!resource.mimeType.isSupportedAppBrowserArchiveMimeType()) {
            return@forEach
        }
        resourcesByUrl.putIfAbsent(
            resolvedUrl,
            AppBrowserInstantViewArchiveResource(
                mimeType = resource.mimeType,
                encoding = resource.encoding,
                data = resource.data,
            ),
        )
    }
    resourcesByUrl[pageUrl] = AppBrowserInstantViewArchiveResource(
        mimeType = "text/html",
        encoding = "UTF-8",
        data = mainHtml.toByteArray(StandardCharsets.UTF_8),
    )
    return AppBrowserInstantViewArchiveSnapshot(
        pageUrl = pageUrl,
        mainHtml = mainHtml,
        resourcesByUrl = resourcesByUrl,
    )
}

private data class AppBrowserParsedArchivePart(
    val url: String?,
    val mimeType: String,
    val encoding: String?,
    val data: ByteArray,
)

private fun parseAppBrowserArchivePart(
    rawPart: String,
    fallbackPageUrl: String,
): AppBrowserParsedArchivePart? {
    val separatorIndex = rawPart.indexOf("\n\n").takeIf { it >= 0 } ?: return null
    val headers = parseAppBrowserMhtmlHeaders(
        rawPart.substring(0, separatorIndex),
    )
    val contentType = headers["content-type"].orEmpty()
    val mimeType = contentType.substringBefore(';').trim().lowercase()
    if (!mimeType.isSupportedAppBrowserArchiveMimeType()) {
        return null
    }
    val encoding = parseAppBrowserArchiveCharset(contentType)
    val decodedBytes = decodeAppBrowserMhtmlBodyToBytes(
        body = rawPart.substring(separatorIndex + 2),
        transferEncoding = headers["content-transfer-encoding"],
    ) ?: return null
    val resolvedUrl = headers["content-location"]
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.resolveAppBrowserArchiveUrlAgainst(fallbackPageUrl)
        ?: fallbackPageUrl.normalizeAppBrowserArchiveUrl()
    val data = when {
        mimeType.equals("text/html", ignoreCase = true) -> {
            val htmlText = decodeAppBrowserArchiveText(
                bytes = decodedBytes,
                encoding = encoding,
            ) ?: return null
            sanitizeAppBrowserInstantViewArchiveHtmlSnapshot(htmlText)
                .toByteArray(StandardCharsets.UTF_8)
        }
        mimeType.equals("text/css", ignoreCase = true) -> {
            val cssText = decodeAppBrowserArchiveText(
                bytes = decodedBytes,
                encoding = encoding,
            ) ?: return null
            cssText.toByteArray(StandardCharsets.UTF_8)
        }
        mimeType.startsWith("image/", ignoreCase = true) -> decodedBytes
        else -> return null
    }
    return AppBrowserParsedArchivePart(
        url = resolvedUrl,
        mimeType = mimeType,
        encoding = if (mimeType.startsWith("image/", ignoreCase = true)) null else "UTF-8",
        data = data,
    )
}

internal fun resolveAppBrowserArchiveResource(
    snapshot: AppBrowserInstantViewArchiveSnapshot,
    requestUrl: String,
): AppBrowserInstantViewArchiveResource? {
    val normalizedUrl = requestUrl.normalizeAppBrowserArchiveUrl() ?: return null
    val resource = snapshot.resourcesByUrl[normalizedUrl] ?: return null
    return resource.takeIf { it.mimeType.isReplayableAppBrowserArchiveMimeType() }
}

private fun AppBrowserInstantViewArchiveResource.toWebResourceResponse(): WebResourceResponse {
    return WebResourceResponse(
        mimeType,
        encoding,
        ByteArrayInputStream(data),
    )
}

private fun buildAppBrowserInstantViewBootstrapResponse(
    pageUrl: String,
): WebResourceResponse {
    return AppBrowserInstantViewArchiveResource(
        mimeType = "text/html",
        encoding = "UTF-8",
        data = buildAppBrowserInstantViewBootstrapHtml(pageUrl)
            .toByteArray(StandardCharsets.UTF_8),
    ).toWebResourceResponse()
}

private fun buildAppBrowserInstantViewLocalAssetResponse(
    context: Context,
    target: AppBrowserInstantViewReplayTarget.LocalAsset,
): WebResourceResponse? {
    val asset = appBrowserInstantViewLocalAssets.firstOrNull { it.fileName == target.fileName } ?: return null
    return AppBrowserInstantViewArchiveResource(
        mimeType = asset.mimeType,
        encoding = asset.encoding,
        data = readRawBytesResource(context, asset.resId),
    ).toWebResourceResponse()
}

private fun buildMissingAppBrowserArchiveResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0)),
    )
}

private fun parseAppBrowserMhtmlHeaders(
    rawHeaders: String,
): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    var currentName: String? = null
    var currentValue = StringBuilder()
    rawHeaders.lines().forEach { rawLine ->
        if (rawLine.isBlank()) {
            return@forEach
        }
        if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && currentName != null) {
            currentValue.append(rawLine.trim())
            return@forEach
        }
        if (currentName != null) {
            headers[currentName!!] = currentValue.toString()
        }
        val separatorIndex = rawLine.indexOf(':')
        if (separatorIndex < 0) {
            currentName = null
            currentValue = StringBuilder()
            return@forEach
        }
        currentName = rawLine.substring(0, separatorIndex).trim().lowercase()
        currentValue = StringBuilder(rawLine.substring(separatorIndex + 1).trim())
    }
    if (currentName != null) {
        headers[currentName!!] = currentValue.toString()
    }
    return headers
}

private fun decodeAppBrowserMhtmlBodyToBytes(
    body: String,
    transferEncoding: String?,
): ByteArray? {
    return when {
        transferEncoding.equals("base64", ignoreCase = true) -> {
            runCatching {
                Base64.decode(
                    body.filterNot { it.isWhitespace() },
                    Base64.DEFAULT,
                )
            }.getOrNull()
        }

        transferEncoding.equals("quoted-printable", ignoreCase = true) -> {
            decodeAppBrowserQuotedPrintableToBytes(body)
        }

        else -> body.toByteArray(StandardCharsets.ISO_8859_1)
    }
}

private fun decodeAppBrowserArchiveText(
    bytes: ByteArray,
    encoding: String?,
): String? {
    val charset = encoding
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
        ?: StandardCharsets.UTF_8
    return runCatching { bytes.toString(charset) }.getOrNull()
}

private fun parseAppBrowserArchiveCharset(
    contentType: String,
): String? {
    return Regex("""charset="?([^";\n]+)""", RegexOption.IGNORE_CASE)
        .find(contentType)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun decodeAppBrowserQuotedPrintableToBytes(
    value: String,
): ByteArray? {
    return runCatching {
        val output = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            when {
                value[index] == '=' && index + 2 < value.length &&
                    value[index + 1] == '\r' && value[index + 2] == '\n' -> {
                    index += 3
                }

                value[index] == '=' && index + 1 < value.length && value[index + 1] == '\n' -> {
                    index += 2
                }

                value[index] == '=' &&
                    index + 2 < value.length &&
                    value[index + 1].isAppBrowserHexDigit() &&
                    value[index + 2].isAppBrowserHexDigit() -> {
                    output.write(
                        (value[index + 1].digitToInt(16) shl 4) + value[index + 2].digitToInt(16),
                    )
                    index += 3
                }

                else -> {
                    val char = value[index]
                    if (char.code <= 0xFF) {
                        output.write(char.code)
                    } else {
                        output.write(char.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                    index += 1
                }
            }
        }
        output.toByteArray()
    }.getOrNull()
}

internal fun sanitizeAppBrowserInstantViewArchiveHtmlSnapshot(
    html: String,
): String {
    val document = org.jsoup.Jsoup.parse(html)
    document.select(
        "script,object,embed,canvas,video,audio,meta[http-equiv=refresh]",
    ).remove()
    document.select("link").forEach { link ->
        val rel = link.attr("rel").lowercase()
        if (!rel.contains("stylesheet")) {
            link.remove()
        }
    }
    document.outputSettings().prettyPrint(false)
    return document.outerHtml()
}

private data class AppBrowserInstantViewLocalAsset(
    val fileName: String,
    val resId: Int,
    val mimeType: String,
    val encoding: String = "UTF-8",
)

internal fun buildAppBrowserInstantViewHtmlSnapshot(
    pageUrl: String,
    sanitizedHtml: String,
): AppBrowserInstantViewArchiveSnapshot {
    val normalizedPageUrl = pageUrl.normalizeAppBrowserArchiveUrl() ?: pageUrl
    val normalizedHtml = sanitizeAppBrowserInstantViewHtmlSnapshot(sanitizedHtml)
    return AppBrowserInstantViewArchiveSnapshot(
        pageUrl = normalizedPageUrl,
        mainHtml = normalizedHtml,
        resourcesByUrl = mapOf(
            normalizedPageUrl to AppBrowserInstantViewArchiveResource(
                mimeType = "text/html",
                encoding = "UTF-8",
                data = normalizedHtml.toByteArray(StandardCharsets.UTF_8),
            ),
        ),
    )
}

private fun buildAppBrowserInstantViewLocalAssetUrl(
    pageUrl: String,
    fileName: String,
): String? {
    val url = pageUrl.toHttpUrlOrNull() ?: return null
    return url.newBuilder()
        .encodedPath("$appBrowserInstantViewLocalAssetPathPrefix$fileName")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .normalizeAppBrowserArchiveUrl()
}

private fun String.resolveAppBrowserArchiveUrlAgainst(
    fallbackPageUrl: String,
): String? {
    return when {
        startsWith("cid:", ignoreCase = true) -> null
        else -> {
            val baseUrl = fallbackPageUrl.toHttpUrlOrNull() ?: return normalizeAppBrowserArchiveUrl()
            baseUrl.resolve(this)?.toString()?.normalizeAppBrowserArchiveUrl()
                ?: normalizeAppBrowserArchiveUrl()
        }
    }
}

private fun String.normalizeAppBrowserArchiveUrl(): String? {
    return toHttpUrlOrNull()
        ?.newBuilder()
        ?.fragment(null)
        ?.build()
        ?.toString()
}

private fun String.isReplayableAppBrowserArchiveMimeType(): Boolean {
    return equals("text/html", ignoreCase = true) || equals("text/css", ignoreCase = true)
}

private fun String.isSupportedAppBrowserArchiveMimeType(): Boolean {
    return isReplayableAppBrowserArchiveMimeType() || startsWith("image/", ignoreCase = true)
}

internal fun resolveAppBrowserArchiveImageDataUrl(
    snapshot: AppBrowserInstantViewArchiveSnapshot,
    requestUrl: String,
): String? {
    val normalizedUrl = requestUrl.normalizeAppBrowserArchiveUrl() ?: return null
    val resource = snapshot.resourcesByUrl[normalizedUrl]
        ?.takeIf { it.mimeType.startsWith("image/", ignoreCase = true) }
        ?: return null
    val encoded = Base64.encodeToString(resource.data, Base64.NO_WRAP)
    return "data:${resource.mimeType};base64,$encoded"
}

private fun AppBrowserReaderDocument.resolveArchiveBackedReaderMedia(
    archiveSnapshot: AppBrowserInstantViewArchiveSnapshot?,
): AppBrowserReaderDocument {
    if (archiveSnapshot == null) {
        return this
    }
    return copy(
        heroImageUrl = heroImageUrl
            ?.let { url -> resolveAppBrowserArchiveImageDataUrl(archiveSnapshot, url) ?: url },
        blocks = blocks.map { block ->
            block.resolveArchiveBackedReaderMedia(archiveSnapshot)
        },
    )
}

private fun AppBrowserReaderBlock.resolveArchiveBackedReaderMedia(
    archiveSnapshot: AppBrowserInstantViewArchiveSnapshot,
): AppBrowserReaderBlock {
    return when (this) {
        is AppBrowserReaderBlock.Paragraph -> this
        is AppBrowserReaderBlock.Heading -> this
        is AppBrowserReaderBlock.CodeBlock -> this
        AppBrowserReaderBlock.Divider -> this
        is AppBrowserReaderBlock.PullQuote -> this
        is AppBrowserReaderBlock.Table -> this
        is AppBrowserReaderBlock.Details -> copy(
            children = children.map { block ->
                block.resolveArchiveBackedReaderMedia(archiveSnapshot)
            },
        )
        is AppBrowserReaderBlock.ListBlock -> copy(
            items = items.map { item ->
                item.copy(
                    blocks = item.blocks.map { block ->
                        block.resolveArchiveBackedReaderMedia(archiveSnapshot)
                    },
                )
            },
        )
        is AppBrowserReaderBlock.Quote -> copy(
            blocks = blocks.map { block ->
                block.resolveArchiveBackedReaderMedia(archiveSnapshot)
            },
        )
        is AppBrowserReaderBlock.Image -> {
            val archiveUrl = resolveAppBrowserArchiveImageDataUrl(
                snapshot = archiveSnapshot,
                requestUrl = image.rawUrl,
            )
            if (archiveUrl == null) {
                this
            } else {
                copy(image = image.copy(rawUrl = archiveUrl))
            }
        }
    }
}

internal fun readRawBytesResource(
    context: Context,
    resId: Int,
): ByteArray {
    return context.resources.openRawResource(resId).use { it.readBytes() }
}

internal const val appBrowserInstantViewLocalAssetPathPrefix = "/__nativedo/instant-view/"
internal const val appBrowserInstantViewIndexFileName = "index.html"
internal const val appBrowserInstantViewDomPurifyFileName = "dompurify.js"
internal const val appBrowserInstantViewReadabilityFileName = "readability.js"
internal const val appBrowserInstantViewRuntimeFileName = "runtime.js"

private val appBrowserInstantViewLocalAssets = listOf(
    AppBrowserInstantViewLocalAsset(
        fileName = appBrowserInstantViewDomPurifyFileName,
        resId = R.raw.app_browser_instant_view_dompurify,
        mimeType = "text/javascript",
    ),
    AppBrowserInstantViewLocalAsset(
        fileName = appBrowserInstantViewReadabilityFileName,
        resId = R.raw.app_browser_instant_view_readability,
        mimeType = "text/javascript",
    ),
    AppBrowserInstantViewLocalAsset(
        fileName = appBrowserInstantViewRuntimeFileName,
        resId = R.raw.app_browser_instant_view_runtime,
        mimeType = "text/javascript",
    ),
)

private fun Char.isAppBrowserHexDigit(): Boolean {
    return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

internal const val appBrowserInstantViewHiddenExtractionTimeoutMillis = 1_500L
internal const val appBrowserInstantViewHtmlJavascript =
    "(function(){return document.documentElement?document.documentElement.outerHTML:null;})()"
internal const val appBrowserInstantViewTextJavascript =
    "(function(){var selectors=['article','.topic-post .cooked','main','[role=\"main\"]','.cooked','.post-stream','body'];for(var i=0;i<selectors.length;i+=1){var node=document.querySelector(selectors[i]);if(node&&node.innerText){var text=node.innerText.replace(/\\u00a0/g,' ').replace(/[ \\t]{2,}/g,' ').trim();if(text.length>0){return text;}}}return null;})()"
