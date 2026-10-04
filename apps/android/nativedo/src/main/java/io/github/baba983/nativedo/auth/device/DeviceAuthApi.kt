package io.github.baba983.nativedo.auth.device

import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.SessionCookieInterceptor
import io.github.baba983.nativedo.core.session.SharedCookieJar
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Discourse's User API Key device authorization flow (`/login/device` on linux.do).
 *
 * The device POSTs an RSA public key and a nonce, shows the returned `user_code` to the reader,
 * the reader approves in a signed-in browser, and the device polls until the server hands it a
 * payload encrypted to that public key. The decrypted payload carries a User API Key - a header
 * credential, not a cookie session.
 *
 * Shapes: https://github.com/discourse/discourse/pull/40189 (core, 2026.6+), contract copy in
 * `contracts/discourse/device-authorization.md`.
 */
@Serializable
data class DeviceCreateRequest(
    val nonce: String,
    val scopes: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("application_name") val applicationName: String,
    @SerialName("public_key") val publicKey: String,
    val padding: String = "oaep",
)

@Serializable
data class DeviceCreateResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_with_request") val verificationUriWithRequest: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Int = 600,
    val interval: Int = 5,
)

@Serializable
data class DevicePollResponse(val status: String, val payload: String? = null)

/** The decrypted authorized payload. `nonce` is the client-chosen value from the create request. */
@Serializable
data class DeviceKeyPayload(
    val key: String,
    val nonce: String,
    val push: Boolean = false,
    val api: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** A create/poll/probe answer the server refused. Carries no secrets: status + error naming only. */
class DeviceAuthHttpException(
    val status: Int,
    val errorType: String?,
    val serverMessage: String?,
    val retryAfterSeconds: Int? = null,
) : IOException("device auth HTTP $status${errorType?.let { " ($it)" } ?: ""}")

sealed interface DevicePollOutcome {
    data object Pending : DevicePollOutcome
    data class Authorized(val encryptedPayload: String) : DevicePollOutcome
    data object Denied : DevicePollOutcome
    data object Expired : DevicePollOutcome
}

/** What one authenticated keyed request answered: status and username only, never the key. */
data class KeyProbeResult(val status: Int, val username: String?, val errorType: String?)

interface DeviceAuthApi {
    suspend fun create(request: DeviceCreateRequest): DeviceCreateResponse

    suspend fun poll(deviceCode: String): DevicePollOutcome

    suspend fun probeSession(key: String, clientId: String): KeyProbeResult

    suspend fun revoke(key: String): Int
}

/**
 * The real client. It shares the app's single cookie jar and interceptors, because the create and
 * poll calls sit behind the same Cloudflare clearance as every other native request.
 */
class HttpDeviceAuthApi(
    baseUrl: String,
    userAgent: String,
    cookieJar: SharedCookieJar,
) : DeviceAuthApi {

    private val root = baseUrl.trimEnd('/') + "/"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(cookieJar))
        .addInterceptor(
            RequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = userAgent,
                cookieJar = cookieJar,
            ),
        )
        .build()

    override suspend fun create(request: DeviceCreateRequest): DeviceCreateResponse {
        val body = NetworkJson.instance.encodeToString(DeviceCreateRequest.serializer(), request)
        val answer = postJson("user-api-key/device.json", body)
        if (answer.status != 200) throw answer.toException()
        return NetworkJson.instance.decodeFromString(DeviceCreateResponse.serializer(), answer.body)
    }

    override suspend fun poll(deviceCode: String): DevicePollOutcome {
        val body = buildJsonObject { put("device_code", deviceCode) }.toString()
        val answer = postJson("user-api-key/device/poll.json", body)
        if (answer.status != 200) throw answer.toException()
        val parsed = NetworkJson.instance.decodeFromString(DevicePollResponse.serializer(), answer.body)
        return when (parsed.status) {
            "authorization_pending" -> DevicePollOutcome.Pending
            "authorized" -> parsed.payload?.takeIf(String::isNotEmpty)
                ?.let(DevicePollOutcome::Authorized)
                ?: throw DeviceAuthHttpException(200, parsed.status, "authorized without payload")
            "access_denied" -> DevicePollOutcome.Denied
            "expired_token" -> DevicePollOutcome.Expired
            else -> throw DeviceAuthHttpException(200, parsed.status, "unknown poll status")
        }
    }

    override suspend fun probeSession(key: String, clientId: String): KeyProbeResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(root + "session/current.json")
                .header("User-Api-Key", key)
                .header("User-Api-Client-Id", clientId)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val objectBody = runCatching {
                    NetworkJson.instance.parseToJsonElement(text).jsonObject
                }.getOrNull()
                KeyProbeResult(
                    status = response.code,
                    username = runCatching {
                        objectBody?.get("current_user")?.jsonObject
                            ?.get("username")?.jsonPrimitive?.contentOrNull
                    }.getOrNull(),
                    errorType = objectBody?.get("error_type")?.jsonPrimitive?.contentOrNull,
                )
            }
        }

    override suspend fun revoke(key: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(root + "user-api-key/revoke")
            .header("User-Api-Key", key)
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response -> response.code }
    }

    private suspend fun postJson(path: String, body: String): HttpAnswer = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(root + path)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            HttpAnswer(
                status = response.code,
                body = response.body?.string().orEmpty(),
                retryAfterSeconds = response.header("Retry-After")?.toIntOrNull(),
            )
        }
    }

    private class HttpAnswer(
        val status: Int,
        val body: String,
        val retryAfterSeconds: Int?,
    ) {
        fun toException(): DeviceAuthHttpException {
            var errorType: String? = null
            var message: String? = null
            runCatching {
                val objectBody = NetworkJson.instance.parseToJsonElement(body).jsonObject
                errorType = objectBody["error_type"]?.jsonPrimitive?.contentOrNull
                message = (objectBody["errors"] as? JsonArray)
                    ?.joinToString("；") { it.jsonPrimitive.contentOrNull.orEmpty() }
                    ?.takeIf(String::isNotBlank)
            }
            return DeviceAuthHttpException(status, errorType, message, retryAfterSeconds)
        }
    }
}
