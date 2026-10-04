package io.github.baba983.nativedo.auth.device

import android.content.SharedPreferences
import io.github.baba983.nativedo.core.session.AndroidKeystoreCipher
import io.github.baba983.nativedo.core.session.CredentialCipher

/**
 * Resting place for a device-flow User API Key: the key itself is sealed with the
 * AndroidKeyStore AES/GCM key ([AndroidKeystoreCipher]), its client id and metadata are plain.
 * The store says nothing about how the key is used - the app's cookie session keeps driving it
 * in this round; a key-mode client would read [StoredKey] the same way.
 */
class DeviceKeyStore(
    private val prefs: SharedPreferences? = null,
    private val cipher: CredentialCipher = AndroidKeystoreCipher,
) {

    data class StoredKey(
        val key: String,
        val clientId: String,
        val scopes: List<String>,
        val expiresAt: String?,
        val savedAtMillis: Long,
    )

    /** Used when there is no preference file (tests, previews); never the device path. */
    private var inMemory: StoredKey? = null

    fun save(
        key: String,
        clientId: String,
        scopes: List<String>,
        expiresAt: String?,
        savedAtMillis: Long,
    ): Boolean {
        if (key.isBlank()) return false
        if (prefs == null) {
            inMemory = StoredKey(key, clientId, scopes, expiresAt, savedAtMillis)
            return true
        }
        val sealed = cipher.encrypt(key) ?: return false
        prefs.edit()
            .putString(KEY_API_KEY, sealed)
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_SCOPES, scopes.joinToString(","))
            .putString(KEY_EXPIRES_AT, expiresAt)
            .putLong(KEY_SAVED_AT, savedAtMillis)
            .apply()
        return true
    }

    fun load(): StoredKey? {
        if (prefs == null) return inMemory
        val sealed = prefs.getString(KEY_API_KEY, null) ?: return null
        val key = cipher.decrypt(sealed) ?: return null
        return StoredKey(
            key = key,
            clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
            scopes = prefs.getString(KEY_SCOPES, "").orEmpty().split(",").filter(String::isNotBlank),
            expiresAt = prefs.getString(KEY_EXPIRES_AT, null),
            savedAtMillis = prefs.getLong(KEY_SAVED_AT, 0L),
        )
    }

    fun hasKey(): Boolean = load()?.key?.isNotBlank() == true

    fun clear() {
        inMemory = null
        prefs?.edit()
            ?.remove(KEY_API_KEY)
            ?.remove(KEY_CLIENT_ID)
            ?.remove(KEY_SCOPES)
            ?.remove(KEY_EXPIRES_AT)
            ?.remove(KEY_SAVED_AT)
            ?.apply()
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_SCOPES = "scopes"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_SAVED_AT = "saved_at"
    }
}
