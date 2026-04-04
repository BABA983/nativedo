package io.github.baba983.nativedo.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

class RequestHeadersInterceptor(
    baseUrl: String = "https://linux.do/",
    private val userAgent: String = FALLBACK_USER_AGENT,
) : Interceptor {
    private val siteUrl = baseUrl.toHttpUrl()
    private val siteOrigin = buildString {
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
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val acceptHeader = originalRequest.header("Accept")
        val isHtmlRequest = acceptHeader?.contains("text/html", ignoreCase = true) == true
        val isXhrRequest = !isHtmlRequest || originalRequest.header("X-Requested-With") == XHR_HEADER_VALUE

        val request = originalRequest.newBuilder().apply {
            if (acceptHeader == null) {
                header("Accept", JSON_ACCEPT_HEADER)
            }
            if (originalRequest.header("Accept-Language") == null) {
                header("Accept-Language", ACCEPT_LANGUAGE_HEADER)
            }
            if (originalRequest.header("User-Agent") == null) {
                header("User-Agent", userAgent)
            }
            if (!isHtmlRequest && originalRequest.header("X-Requested-With") == null) {
                header("X-Requested-With", XHR_HEADER_VALUE)
            }
            if (isXhrRequest) {
                if (originalRequest.header("Origin") == null) {
                    header("Origin", siteOrigin)
                }
                if (originalRequest.header("Referer") == null) {
                    header("Referer", "$siteOrigin/")
                }
            }
        }.build()
        return chain.proceed(request)
    }

    companion object {
        private const val JSON_ACCEPT_HEADER = "application/json;q=0.9, text/plain;q=0.8, */*;q=0.5"
        private const val ACCEPT_LANGUAGE_HEADER = "zh-CN,zh;q=0.9,en;q=0.8"
        private const val XHR_HEADER_VALUE = "XMLHttpRequest"
    }
}
