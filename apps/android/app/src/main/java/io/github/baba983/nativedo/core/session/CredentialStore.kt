package io.github.baba983.nativedo.core.session

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores WebView login credentials (username + password) for auto-fill.
 *
 * The username is plain; the password is sealed with an AndroidKeyStore
 * AES/GCM key ([AndroidKeystoreCipher]) so it never rests on disk in the
 * clear. Credentials are saved only after the user successfully logs in.
 */
class CredentialStore(
    private val prefs: SharedPreferences?,
    private val cipher: CredentialCipher = AndroidKeystoreCipher,
) {
    constructor(context: Context?) : this(
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    data class Credentials(val username: String?, val password: String?)

    fun load(): Credentials {
        val storedPassword = prefs?.getString(KEY_PASSWORD, null)
        return Credentials(
            username = prefs?.getString(KEY_USERNAME, null),
            password = storedPassword?.let(cipher::decrypt),
        )
    }

    fun save(username: String, password: String) {
        prefs?.edit()?.apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, cipher.encrypt(password))
        }?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    fun hasCredentials(): Boolean {
        val credentials = load()
        return !credentials.username.isNullOrBlank() && !credentials.password.isNullOrBlank()
    }

    private companion object {
        const val PREFS_NAME = "nativedo.credentials"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}

/** Seals credential values before they touch disk. */
interface CredentialCipher {
    fun encrypt(plaintext: String): String?

    fun decrypt(ciphertext: String): String?
}

/**
 * AES/GCM sealing backed by a non-exportable AndroidKeyStore key. Ciphertext
 * is `v1:` + Base64(IV || ciphertext); anything else fails to decrypt, which
 * transparently discards values written by older, plaintext versions.
 */
object AndroidKeystoreCipher : CredentialCipher {
    private const val KEY_ALIAS = "nativedo_credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    override fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }.getOrNull()

    override fun decrypt(ciphertext: String): String? {
        if (!ciphertext.startsWith(PREFIX)) return null
        return runCatching {
            val sealed = Base64.decode(ciphertext.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, sealed, 0, GCM_IV_LENGTH_BYTES),
            )
            String(
                cipher.doFinal(sealed, GCM_IV_LENGTH_BYTES, sealed.size - GCM_IV_LENGTH_BYTES),
                Charsets.UTF_8,
            )
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
}
