package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.session.SessionDiagnostics
import io.github.baba983.nativedo.core.session.SharedCookieJar
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import okio.buffer

/**
 * Network interceptor: the native side of the shared [SharedCookieJar].
 *
 * <p>Each exchange reads the request's cookies from CookieManager and returns each response
 * `Set-Cookie` value verbatim to it, so a `_t` rotation performed here, in the login WebView or
 * in the in-app browser is the one every other participant sees next. There is no native cookie
 * snapshot to replay and no fence between two jars to maintain.</p>
 *
 * <p>Two rules survive from the two-jar design, both about *not writing*:</p>
 *
 * <ul>
 *   <li>a Cloudflare challenge response must not touch `_t`/`_forum_session` (a proxy
 *   interception is not the site's answer);</li>
 *   <li>a response whose request went out with an older `_t` than the jar holds now must not
 *   write or delete auth cookies: another participant (or a parallel response) rotated the token
 *   while this exchange was in flight, and that newer rotation is the live one. A plain read
 *   still returns its payload - Discourse rotates `_t` roughly every ten minutes, so a cold
 *   start's parallel requests lose that race routinely - but a negative answer (a logout, a
 *   challenge, a rejection) loses its meaning in that race and fails the request instead, so a
 *   stale response can never report a logout.</li>
 * </ul>
 */
class SessionCookieInterceptor(private val cookieJar: SharedCookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val sentHeader = cookieJar.requestCookieHeader(url)
        val sentToken = SharedCookieJar.cookieValue(sentHeader, SharedCookieJar.TOKEN_COOKIE)
            ?.takeIf(String::isNotBlank)
        val request = chain.request().newBuilder().apply {
            if (sentHeader.isNotBlank()) header("Cookie", sentHeader)
            if (sentToken == null) {
                removeHeader("Discourse-Logged-In")
                removeHeader("Discourse-Present")
            }
        }.build()
        val response = chain.proceed(request)
        val contentType = response.header("Content-Type").orEmpty()
        val inspectBody = !response.isSuccessful || contentType.contains("text/html", ignoreCase = true)
        val evidence = SessionResponseEvidence.classify(
            response.code,
            response.headers,
            if (inspectBody) response.evidenceBody() else "",
        )
        val setCookieHeaders = response.headers.values("Set-Cookie")
        val currentToken = cookieJar.authToken(url)
        // A rotation only counts when both values are present and differ: a token the browser
        // deleted is the session genuinely ending, and this response's logout evidence is real.
        val rotatedWhileInFlight =
            sentToken != null && currentToken != null && currentToken != sentToken
        val blockAuthWrites = rotatedWhileInFlight || evidence == SessionResponseEvidence.Challenge
        var wroteCookies = false
        setCookieHeaders.forEach { header ->
            val name = SharedCookieJar.setCookieName(header)
            if (!(blockAuthWrites && name in SharedCookieJar.AUTH_COOKIE_NAMES)) {
                wroteCookies = cookieJar.saveSetCookie(url, header) || wroteCookies
            }
        }
        if (wroteCookies) {
            // The rotation handed to this response must reach disk before the process can die;
            // a lost newer `_t` would leave the jar on the older one.
            cookieJar.flush()
        }
        val route = when (url.encodedPath) {
            "/session/current.json" -> "session"
            "/session/csrf" -> "csrf"
            "/", "/latest", "/latest.json" -> "feed"
            else -> "other"
        }
        val operation = when (url.encodedPath) {
            "/posts.json" -> "posts"
            "/topics/timings" -> "timings"
            else -> "other"
        }
        SessionDiagnostics.response(
            cookieJar.revision(), response.code, accepted = !rotatedWhileInFlight, evidence.name,
            route, operation,
            sentToken != null,
            SharedCookieJar.cookieValue(sentHeader, "_forum_session") != null,
            SharedCookieJar.cookieValue(sentHeader, SharedCookieJar.CF_CLEARANCE_COOKIE) != null,
            SharedCookieJar.cookieValue(sentHeader, "_rt") != null,
            setCookieHeaders.any { SharedCookieJar.setCookieName(it) == SharedCookieJar.TOKEN_COOKIE },
            setCookieHeaders.any {
                SharedCookieJar.setCookieName(it) == SharedCookieJar.TOKEN_COOKIE &&
                    isDeletion(url, it)
            },
        )
        if (bodyReadMustFail(response.isSuccessful, evidence, rotatedWhileInFlight)) {
            response.close()
            throw SessionChangedException()
        }
        if (rotatedWhileInFlight) {
            SessionDiagnostics.record("fenced_read_returned", cookieJar.revision(), response.code)
        }
        return response
    }

    private fun bodyReadMustFail(
        successful: Boolean,
        evidence: SessionResponseEvidence,
        rotatedWhileInFlight: Boolean,
    ): Boolean = rotatedWhileInFlight && !(successful && evidence == SessionResponseEvidence.Other)

    private fun isDeletion(url: okhttp3.HttpUrl, setCookieHeader: String): Boolean {
        val parsed = okhttp3.Cookie.parse(url, setCookieHeader) ?: return false
        return parsed.expiresAt <= System.currentTimeMillis()
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
