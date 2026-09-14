package io.github.baba983.nativedo.core.network

import java.util.Locale
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers

/** Classification only; never retain or log response bodies. */
internal enum class SessionResponseEvidence {
    Challenge, LoggedOut, Csrf, Forbidden, RateLimited, Other;

    companion object {
        fun classify(status: Int, headers: Headers, body: String): SessionResponseEvidence {
            // A proxy challenge cannot prove an application session was revoked.
            if (headers["cf-mitigated"].equals("challenge", ignoreCase = true) ||
                (status in setOf(200, 403, 429, 503) &&
                    looksLikeCloudflareChallenge(body.lowercase(Locale.US)))) return Challenge
            val error = runCatching {
                val payload = NetworkJson.instance.parseToJsonElement(body).jsonObject
                listOf("error_type", "error").mapNotNull { payload[it]?.jsonPrimitive?.contentOrNull }
            }.getOrDefault(emptyList())
            if (headers["discourse-logged-out"].let { it == "1" || it.equals("true", true) } ||
                status == 401 || error.any { it.equals("not_logged_in", true) || it.equals("logged_out", true) }) {
                return LoggedOut
            }
            if (status == 403 && (body.trim() == "[\"BAD CSRF\"]" ||
                    error.any { it.equals("invalid_access", true) && body.contains("CSRF", true) })) return Csrf
            return when (status) {
                403 -> Forbidden
                429 -> RateLimited
                else -> Other
            }
        }
    }
}
