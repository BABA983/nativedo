package io.github.baba983.nativedo.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class ImageRequestHeadersInterceptor(
    private val baseUrl: String = "https://linux.do/",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val request = originalRequest.newBuilder()
            .apply {
                addImageRequestHeaders(baseUrl = baseUrl)
            }
            .build()
        return chain.proceed(request)
    }

    companion object {
        internal const val IMAGE_ACCEPT_HEADER =
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        internal const val ACCEPT_LANGUAGE_HEADER = "zh-CN,zh;q=0.9,en;q=0.8"
        internal const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal fun Request.Builder.addImageRequestHeaders(
    baseUrl: String,
): Request.Builder {
    val referer = buildImageRequestReferer(baseUrl)
    val originalRequest = build()
    if (originalRequest.header("Accept") == null) {
        header("Accept", ImageRequestHeadersInterceptor.IMAGE_ACCEPT_HEADER)
    }
    if (originalRequest.header("Accept-Language") == null) {
        header("Accept-Language", ImageRequestHeadersInterceptor.ACCEPT_LANGUAGE_HEADER)
    }
    if (originalRequest.header("Referer") == null) {
        header("Referer", referer)
    }
    if (originalRequest.header("User-Agent") == null) {
        header("User-Agent", ImageRequestHeadersInterceptor.DEFAULT_USER_AGENT)
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
