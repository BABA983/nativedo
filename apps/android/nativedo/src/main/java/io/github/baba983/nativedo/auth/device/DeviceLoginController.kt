package io.github.baba983.nativedo.auth.device

import io.github.baba983.nativedo.core.network.NetworkJson
import java.io.IOException
import java.security.PublicKey
import java.security.SecureRandom
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

private const val DEVICE_LOGIN_APPLICATION_NAME = "NativeDo（linux.do 客户端）"

/**
 * Drives one device-code login: create the grant, show the code, poll until the browser's answer
 * arrives, open the payload with the device key, verify the nonce, store the key, then make one
 * authenticated request with it (status + username only).
 *
 * The controller is the only place that sees the device code and the key; it never writes either
 * to the log. The stored key does not replace the cookie session in this round - the app's
 * network layer keeps using cookies; a key-mode client would read [DeviceKeyStore.load] and add
 * the `User-Api-Key` header the way [DeviceAuthApi.probeSession] does.
 */
class DeviceLoginController(
    private val api: DeviceAuthApi,
    private val keyStore: DeviceKeyStore,
    private val loginKey: DeviceLoginKey,
    private val clientId: String,
    private val applicationName: String = DEVICE_LOGIN_APPLICATION_NAME,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope,
) {

    sealed interface State {
        data object Idle : State

        data object Creating : State

        data class Waiting(
            val userCode: String,
            val verificationUri: String,
            val expiresAtMillis: Long,
        ) : State

        data class Authorized(
            val username: String?,
            val expiresAt: String?,
            val keyStorage: String,
        ) : State

        data object Denied : State

        data object Expired : State

        data class Failed(val reason: String) : State
    }

    /** One scope set's verdict in the scope audit: the status and the server's own naming. */
    data class ScopeAuditResult(
        val scopes: String,
        val status: Int,
        val userCode: String?,
        val errorType: String?,
        val message: String?,
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _scopeAudit = MutableStateFlow<List<ScopeAuditResult>>(emptyList())
    val scopeAudit: StateFlow<List<ScopeAuditResult>> = _scopeAudit.asStateFlow()

    private val _auditRunning = MutableStateFlow(false)
    val auditRunning: StateFlow<Boolean> = _auditRunning.asStateFlow()

    private val _hasStoredKey = MutableStateFlow(keyStore.hasKey())
    val hasStoredKey: StateFlow<Boolean> = _hasStoredKey.asStateFlow()

    private var loginJob: Job? = null
    private var auditJob: Job? = null

    fun startLogin(scopes: List<String> = DEFAULT_LOGIN_SCOPES) {
        if (loginJob?.isActive == true) return
        loginJob = scope.launch {
            val scopesParam = scopes.joinToString(",")
            _state.value = State.Creating
            DeviceLoginDiagnostics.record(
                "create_attempt",
                "scopes" to scopesParam,
                "key_storage" to loginKey.storage,
            )
            val nonce = newNonce()
            val created = try {
                api.create(
                    DeviceCreateRequest(
                        nonce = nonce,
                        scopes = scopesParam,
                        clientId = clientId,
                        applicationName = applicationName,
                        publicKey = publicKeyPem(loginKey.publicKey),
                    ),
                )
            } catch (error: DeviceAuthHttpException) {
                DeviceLoginDiagnostics.record(
                    "create_rejected",
                    "scopes" to scopesParam,
                    "status" to error.status,
                    "error_type" to error.errorType,
                )
                _state.value = State.Failed(httpFailureText("申请授权码被拒绝", error))
                return@launch
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DeviceLoginDiagnostics.record(
                    "create_failed",
                    "scopes" to scopesParam,
                    "kind" to error.javaClass.simpleName,
                )
                _state.value = State.Failed("网络错误：${error.javaClass.simpleName}")
                return@launch
            }
            DeviceLoginDiagnostics.record(
                "create_succeeded",
                "scopes" to scopesParam,
                "user_code" to created.userCode,
                "verification_path" to verificationPath(created.verificationUri),
                "expires_in" to created.expiresInSeconds,
                "interval" to created.interval,
            )
            _state.value = State.Waiting(
                userCode = created.userCode,
                verificationUri = created.verificationUriWithRequest ?: created.verificationUri,
                expiresAtMillis = now() + created.expiresInSeconds * 1000L,
            )
            pollLoop(created, nonce, scopesParam)
        }
    }

    /**
     * Runs one create request per candidate scope set and records the server's answer. This is the
     * probe for linux.do's `allow_user_api_key_scopes`: a 200 means the scope passed the allow
     * list, anything else carries the server's own error naming. The grants are left unapproved,
     * so they expire without creating a key.
     */
    fun probeScopes() {
        if (auditJob?.isActive == true) return
        auditJob = scope.launch {
            _auditRunning.value = true
            _scopeAudit.value = emptyList()
            for (scopes in SCOPE_AUDIT_SETS) {
                val scopesParam = scopes.joinToString(",")
                val result = try {
                    val created = api.create(
                        DeviceCreateRequest(
                            nonce = newNonce(),
                            scopes = scopesParam,
                            clientId = clientId,
                            applicationName = applicationName,
                            publicKey = publicKeyPem(loginKey.publicKey),
                        ),
                    )
                    ScopeAuditResult(scopesParam, 200, created.userCode, null, null)
                } catch (error: DeviceAuthHttpException) {
                    ScopeAuditResult(scopesParam, error.status, null, error.errorType, error.serverMessage)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    ScopeAuditResult(scopesParam, 0, null, error.javaClass.simpleName, null)
                }
                _scopeAudit.value = _scopeAudit.value + result
                DeviceLoginDiagnostics.record(
                    "scope_audit",
                    "scopes" to result.scopes,
                    "status" to result.status,
                    "error_type" to result.errorType,
                    "user_code" to result.userCode,
                )
                delay(AUDIT_GAP_MILLIS)
            }
            _auditRunning.value = false
        }
    }

    /** Stops a pending login; a grant already authorized is not touched. */
    fun cancel() {
        loginJob?.cancel()
        loginJob = null
    }

    /**
     * Revokes the stored key on the server (`POST /user-api-key/revoke` with the key header) and
     * clears it locally on success. A failure leaves it in place so the operation can be retried.
     */
    fun revokeStoredKey() {
        val stored = keyStore.load() ?: return
        scope.launch {
            try {
                val status = api.revoke(stored.key)
                DeviceLoginDiagnostics.record("key_revoke", "status" to status)
                if (status in 200..299) {
                    keyStore.clear()
                    _hasStoredKey.value = false
                    DeviceLoginDiagnostics.record("key_cleared")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DeviceLoginDiagnostics.record(
                    "key_revoke_failed",
                    "kind" to error.javaClass.simpleName,
                )
            }
        }
    }

    private suspend fun pollLoop(created: DeviceCreateResponse, nonce: String, scopesParam: String) {
        var delayMillis = created.interval.coerceAtLeast(1) * 1000L
        var pendingLogged = false
        val deadline = now() + created.expiresInSeconds * 1000L
        while (currentCoroutineContext().isActive && now() < deadline) {
            delay(delayMillis)
            val outcome = try {
                api.poll(created.deviceCode)
            } catch (error: DeviceAuthHttpException) {
                if (error.status == 429) {
                    delayMillis = (error.retryAfterSeconds ?: 30).coerceAtLeast(5) * 1000L
                    DeviceLoginDiagnostics.record(
                        "poll_backoff",
                        "status" to 429,
                        "next_seconds" to delayMillis / 1000,
                    )
                    continue
                }
                DeviceLoginDiagnostics.record(
                    "poll_rejected",
                    "status" to error.status,
                    "error_type" to error.errorType,
                )
                _state.value = State.Failed(httpFailureText("轮询被拒绝", error))
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                delayMillis = min(delayMillis * 2, MAX_BACKOFF_MILLIS)
                DeviceLoginDiagnostics.record(
                    "poll_backoff",
                    "kind" to error.javaClass.simpleName,
                    "next_seconds" to delayMillis / 1000,
                )
                continue
            }
            when (outcome) {
                DevicePollOutcome.Pending -> {
                    if (!pendingLogged) {
                        DeviceLoginDiagnostics.record("poll_pending")
                        pendingLogged = true
                    }
                    delayMillis = created.interval.coerceAtLeast(1) * 1000L
                }
                is DevicePollOutcome.Authorized -> {
                    DeviceLoginDiagnostics.record("poll_authorized")
                    authorize(outcome.encryptedPayload, nonce, scopesParam)
                    return
                }
                DevicePollOutcome.Denied -> {
                    DeviceLoginDiagnostics.record("poll_denied")
                    _state.value = State.Denied
                    return
                }
                DevicePollOutcome.Expired -> {
                    DeviceLoginDiagnostics.record("poll_expired")
                    _state.value = State.Expired
                    return
                }
            }
        }
        DeviceLoginDiagnostics.record("grant_expired")
        _state.value = State.Expired
    }

    private suspend fun authorize(encryptedPayload: String, nonce: String, scopesParam: String) {
        val opened = try {
            openPayload(encryptedPayload, nonce)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PayloadRejected) {
            DeviceLoginDiagnostics.record(
                "payload_rejected",
                "reason" to error.reason,
                "detail" to error.detail,
                "chars" to encryptedPayload.length,
            )
            _state.value = State.Failed("授权载荷校验失败：${error.reason}")
            return
        }
        DeviceLoginDiagnostics.record(
            "payload_opened",
            "transform" to opened.transform,
            "chars" to encryptedPayload.length,
            "bytes" to opened.sealedBytes,
        )
        val payload = opened.payload
        val saved = keyStore.save(
            key = payload.key,
            clientId = clientId,
            scopes = scopesParam.split(","),
            expiresAt = payload.expiresAt,
            savedAtMillis = now(),
        )
        if (saved) _hasStoredKey.value = true
        DeviceLoginDiagnostics.record(
            "key_stored",
            "ok" to saved,
            "scopes" to scopesParam,
            "expires_at" to payload.expiresAt,
            "key_storage" to loginKey.storage,
        )
        val probe = try {
            api.probeSession(payload.key, clientId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            null
        }
        DeviceLoginDiagnostics.record(
            "probe_session",
            "status" to probe?.status,
            "username" to probe?.username,
            "error_type" to probe?.errorType,
        )
        _state.value = State.Authorized(
            username = probe?.username,
            expiresAt = payload.expiresAt,
            keyStorage = loginKey.storage,
        )
    }

    private fun openPayload(encryptedPayload: String, nonce: String): Opened {
        val compact = encryptedPayload.replace(WHITESPACE, "")
        val sealed = compact.decodeBase64()?.toByteArray()
            ?: throw PayloadRejected("base64")
        val opened = try {
            loginKey.open(sealed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw PayloadRejected("decrypt", error)
        }
        val payload = try {
            NetworkJson.instance.decodeFromString(
                DeviceKeyPayload.serializer(),
                String(opened.bytes, Charsets.UTF_8),
            )
        } catch (error: Throwable) {
            throw PayloadRejected("json", error)
        }
        if (payload.nonce != nonce) throw PayloadRejected("nonce_mismatch")
        return Opened(payload, opened.transform, sealed.size)
    }

    private class Opened(
        val payload: DeviceKeyPayload,
        val transform: String,
        val sealedBytes: Int,
    )

    private class PayloadRejected(val reason: String, cause: Throwable? = null) : Exception(reason, cause) {
        val detail: String? = cause?.let { error ->
            "${error.javaClass.simpleName}: ${error.message?.take(120)}"
        }
    }

    private fun httpFailureText(prefix: String, error: DeviceAuthHttpException): String = buildString {
        append(prefix)
        append("：HTTP ")
        append(error.status)
        error.errorType?.let { append("（").append(it).append("）") }
        error.serverMessage?.takeIf(String::isNotBlank)?.let { append(" ").append(it.take(120)) }
    }

    companion object {
        /**
         * linux.do's probe (2026-10-04) refuses `read`/`write` with 403 `invalid_access` and
         * accepts `notifications` and `session_info`; `session_info` is the one that covers the
         * identity read this client makes, so it is the login default. (`one_time_password` is
         * forbidden in the device flow by core and answered 400 `invalid_parameters`.)
         */
        val DEFAULT_LOGIN_SCOPES: List<String> = listOf("session_info")

        /** The scope sets the audit asks for, in order; none is approved. */
        val SCOPE_AUDIT_SETS: List<List<String>> = listOf(
            listOf("read"),
            listOf("read", "write"),
            listOf("notifications"),
            listOf("session_info"),
            listOf("one_time_password"),
        )

        private const val MAX_BACKOFF_MILLIS = 30_000L
        private const val AUDIT_GAP_MILLIS = 1_000L
        private val WHITESPACE = Regex("\\s+")

        private fun newNonce(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** The page the reader must open; the path alone, never the request token in the query. */
        private fun verificationPath(verificationUri: String): String =
            runCatching { java.net.URI(verificationUri).path }.getOrNull() ?: "unknown"

        private fun publicKeyPem(publicKey: PublicKey): String {
            val encoded = publicKey.encoded.toByteString().base64()
            return buildString {
                append("-----BEGIN PUBLIC KEY-----\n")
                encoded.chunked(64).forEach { append(it).append('\n') }
                append("-----END PUBLIC KEY-----\n")
            }
        }
    }
}
