package io.github.baba983.nativedo.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a small secret before it touches disk.
 *
 * <p>The active app has no stored login password: the login WebView leaves form filling to
 * Android's Autofill framework. The remaining caller is the experimental device-authorization
 * key store, whose User API Key and software private key are sealed with
 * [AndroidKeystoreCipher]. The interface is kept generic because it describes a cipher, not a
 * credential store.</p>
 */
interface CredentialCipher {
    fun encrypt(plaintext: String): String?

    fun decrypt(ciphertext: String): String?
}

/**
 * AES/GCM sealing backed by a non-exportable AndroidKeyStore key. Ciphertext
 * is `v1:` + Base64(IV || ciphertext); anything else fails to decrypt, which
 * transparently discards values written by older, plaintext versions.
 *
 * <p>The key alias predates the device-authorization flow: it was also used by the retired login
 * autofill store. It is deliberately unchanged so existing device keys stay readable.</p>
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
