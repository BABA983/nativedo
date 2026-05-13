package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class RequestHeadersInterceptor(
    baseUrl: String = "https://linux.do/",
    private val userAgent: String = FALLBACK_USER_AGENT,
    private val cookieStore: CookieStore? = null,
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
            if (!isHtmlRequest) {
                if (originalRequest.header("Origin") == null) {
                    header("Origin", siteOrigin)
                }
                if (originalRequest.header("Referer") == null) {
                    header("Referer", "$siteOrigin/")
                }
                if (cookieStore?.hasAuthenticatedCookies() == true) {
                    header("Discourse-Logged-In", "true")
                    header("Discourse-Present", "true")
                }
            }
        }.build()
        return chain.proceed(request)
    }

    private companion object {
        const val JSON_ACCEPT_HEADER: String = "application/json;q=0.9, text/plain;q=0.8, */*;q=0.5"
        const val ACCEPT_LANGUAGE_HEADER: String = "zh-CN,zh;q=0.9,en;q=0.8"
        const val XHR_HEADER_VALUE: String = "XMLHttpRequest"
    }
}
