package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.session.SessionDiagnostics
import java.io.IOException
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import okio.buffer

/** Network interceptor: each redirect/retry captures the cookies actually sent.
 * OkHttp's CookieJar callback has no request context, so it cannot fence stale writes.
 */
class SessionCookieInterceptor(private val cookieStore: CookieStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val (revision, cookies) = cookieStore.requestSnapshot(url)
        val request = chain.request().newBuilder().removeHeader("Cookie").apply {
            if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
            if (cookies.none { it.name == "_t" && it.value.isNotBlank() }) {
                removeHeader("Discourse-Logged-In")
                removeHeader("Discourse-Present")
            }
        }.build()
        val response = chain.proceed(request)
        val contentType = response.header("Content-Type").orEmpty()
        val inspectBody = !response.isSuccessful || contentType.contains("text/html", ignoreCase = true)
        val evidence = SessionResponseEvidence.classify(response.code, response.headers,
            if (inspectBody) response.evidenceBody() else "")
        val responseCookies = Cookie.parseAll(url, response.headers).filterNot {
            evidence == SessionResponseEvidence.Challenge && (it.name == "_t" || it.name == "_forum_session")
        }
        val accepted = cookieStore.saveIfCurrent(url, responseCookies, revision)
        val route = when (url.encodedPath) {
            "/session/current.json" -> "session"
            "/session/csrf" -> "csrf"
            "/", "/latest", "/latest.json" -> "feed"
            else -> "other"
        }
        SessionDiagnostics.response(revision, response.code, accepted, evidence.name, route,
            cookies.any { it.name == "_t" }, cookies.any { it.name == "_forum_session" },
            cookies.any { it.name == "cf_clearance" }, cookies.any { it.name == "_rt" },
            responseCookies.any { it.name == "_t" },
            responseCookies.any { it.name == "_t" && it.expiresAt <= System.currentTimeMillis() })
        if (!accepted) {
            response.close()
            throw IOException("Session changed while request was in flight")
        }
        return response
    }
}

// Network interceptors run before OkHttp's transparent gzip decoding.
private fun Response.evidenceBody(): String = runCatching {
    val preview = peekBody(64 * 1024L)
    if (!header("Content-Encoding").equals("gzip", ignoreCase = true)) return@runCatching preview.string()
    GzipSource(preview.source()).buffer().use { source ->
        val decoded = Buffer()
        while (decoded.size < 32 * 1024L) {
            if (source.read(decoded, 32 * 1024L - decoded.size) == -1L) break
        }
        decoded.readUtf8()
    }
}.getOrDefault("")
