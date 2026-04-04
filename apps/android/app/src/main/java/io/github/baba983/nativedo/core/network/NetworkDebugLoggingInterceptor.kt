package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.log.debugLog
import io.github.baba983.nativedo.core.log.errorLog
import io.github.baba983.nativedo.core.log.NativeDoLogTag
import okhttp3.Interceptor
import okhttp3.Response

class NetworkDebugLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        debugLog(
            TAG,
            buildString {
                append("--> ${request.method} ${request.url}")
                append(" accept=${request.header("Accept").orEmpty()}")
                append(" xhr=${request.header("X-Requested-With").orEmpty()}")
                append(" referer=${request.header("Referer").orEmpty()}")
                append(" ua=${request.header("User-Agent").orEmpty()}")
                append(" cookies=${request.headers(COOKIE_HEADER).cookieNamesSummary()}")
            },
        )

        return try {
            val response = chain.proceed(request)
            debugLog(
                TAG,
                buildString {
                    append("<-- ${response.code} ${request.method} ${request.url}")
                    append(" setCookies=${response.headers(SET_COOKIE_HEADER).cookieNamesSummary()}")
                },
            )
            response
        } catch (error: Throwable) {
            errorLog(TAG, "<-- HTTP FAILED ${request.method} ${request.url}", error)
            throw error
        }
    }

    private fun List<String>.cookieNamesSummary(): String {
        val cookieNames = flatMap { header ->
            header.split(';').mapNotNull { segment ->
                segment.substringBefore('=')
                    .trim()
                    .takeIf { name -> name.isNotBlank() && !name.equals("path", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("domain", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("expires", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("max-age", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("secure", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("httponly", ignoreCase = true) }
                    ?.takeUnless { name -> name.equals("samesite", ignoreCase = true) }
            }
        }.distinct()

        return cookieNames.takeIf { names -> names.isNotEmpty() }
            ?.joinToString(prefix = "[", postfix = "]")
            ?: "[]"
    }

    private companion object {
        val TAG: NativeDoLogTag = NativeDoLogTag.Http
        const val COOKIE_HEADER: String = "Cookie"
        const val SET_COOKIE_HEADER: String = "Set-Cookie"
    }
}
