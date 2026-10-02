package io.github.baba983.nativedo.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class ImageRequestHeadersInterceptor(
    private val baseUrl: String = "https://linux.do/",
    /** The same client identity the API and the WebView present; see [resolveNativeDoUserAgent]. */
    private val userAgent: String = FALLBACK_USER_AGENT,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply {
                addImageRequestHeaders(baseUrl = baseUrl, userAgent = userAgent)
            }
            .build()
        return chain.proceed(request)
    }
}

internal fun Request.Builder.addImageRequestHeaders(
    baseUrl: String,
    userAgent: String = FALLBACK_USER_AGENT,
): Request.Builder {
    val referer = buildImageRequestReferer(baseUrl)
    val originalRequest = build()
    if (originalRequest.header("Accept") == null) {
        header("Accept", IMAGE_ACCEPT_HEADER)
    }
    if (originalRequest.header("Accept-Language") == null) {
        header("Accept-Language", ACCEPT_LANGUAGE_HEADER)
    }
    if (originalRequest.header("Referer") == null) {
        header("Referer", referer)
    }
    if (originalRequest.header("User-Agent") == null) {
        header("User-Agent", userAgent)
    }
    return this
}

private fun buildImageRequestReferer(baseUrl: String): String {
    val siteUrl = baseUrl.toHttpUrl()
    return buildString {
        append(siteUrl.scheme)
        append("://")
        append(siteUrl.host)
        val shouldAppendPort =
            (siteUrl.scheme == "https" && siteUrl.port != 443) ||
                (siteUrl.scheme == "http" && siteUrl.port != 80)
        if (shouldAppendPort) {
            append(":")
            append(siteUrl.port)
        }
        append("/")
    }
}

private const val IMAGE_ACCEPT_HEADER =
    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
private const val ACCEPT_LANGUAGE_HEADER = "zh-CN,zh;q=0.9,en;q=0.8"
