package io.github.baba983.nativedo.auth.device

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.baba983.nativedo.core.session.AndroidKeystoreCipher
import io.github.baba983.nativedo.core.session.CredentialCipher
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * A payload the device key managed to open. [transform] names the cipher that worked
 * (`oaep-sha1`, `oaep-sha256`, `oaep-sha512`, `pkcs1`), for diagnosis only.
 */
class OpenedPayload(val bytes: ByteArray, val transform: String)

/**
 * The RSA keypair the device authorization flow hands to the server so only this device can open
 * the authorized payload.
 *
 * The preferred home is AndroidKeyStore: the private key never exists in the process's heap and
 * cannot be exported. Keystore's RSA-OAEP support is not uniform across API levels and its digest
 * authorization is easy to get wrong, so the key is only accepted after a real encrypt/decrypt
 * self-test with the exact OAEP transformation the server uses. When Keystore cannot do it, a
 * software keypair is generated and its PKCS#8 private half is sealed with the same
 * hardware-backed AES/GCM key that protects the saved credentials ([AndroidKeystoreCipher]); the
 * at-rest protection is equivalent, only the cipher operation moves into the process.
 *
 * Discourse's Ruby asks for `rsa_padding_mode=oaep` (OpenSSL default SHA-1) and its official Node
 * client decrypts with Node's OAEP default, but linux.do's live payload (2026-10-04) turned out to
 * be **PKCS#1 v1.5**: every OAEP variant failed and `RSA/ECB/PKCS1Padding` opened it. The ciphertext
 * is therefore opened through a short chain of transformations and the one that worked is recorded -
 * the site's actual padding must not make the flow silently unusable.
 */
interface DeviceLoginKey {
    /** `keystore` or `software`; diagnostics only. */
    val storage: String

    val publicKey: PublicKey

    /** Opens the server's sealed payload with the private half, trying the known paddings. */
    fun open(sealed: ByteArray): OpenedPayload
}

object DeviceLoginKeys {

    fun create(
        prefs: SharedPreferences?,
        cipher: CredentialCipher = AndroidKeystoreCipher,
    ): DeviceLoginKey =
        runCatching { AndroidKeystoreDeviceLoginKey.create() }.getOrNull()
            ?: SoftwareDeviceLoginKey.create(prefs, cipher)

    /** A plain software key over an existing pair; tests and previews only. */
    internal fun softwareForTest(pair: KeyPair): DeviceLoginKey = SoftwareDeviceLoginKey.overTestPair(pair)
}

private val DECRYPT_TRANSFORMATIONS: List<Pair<String, String>> = listOf(
    "oaep-sha1" to "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
    "oaep-sha256" to "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
    "oaep-sha512" to "RSA/ECB/OAEPWithSHA-512AndMGF1Padding",
    "pkcs1" to "RSA/ECB/PKCS1Padding",
)

/** Generates (or loads) the pair. Any failure - generation or the self-test - falls back. */
private class AndroidKeystoreDeviceLoginKey private constructor(
    private val keyStore: KeyStore,
) : DeviceLoginKey {

    override val storage: String = "keystore"

    override val publicKey: PublicKey
        get() = privateEntry().certificate.publicKey

    override fun open(sealed: ByteArray): OpenedPayload {
        var firstFailure: Throwable? = null
        for ((name, transformation) in DECRYPT_TRANSFORMATIONS) {
            try {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.DECRYPT_MODE, privateEntry().privateKey)
                return OpenedPayload(cipher.doFinal(sealed), name)
            } catch (error: GeneralSecurityException) {
                if (firstFailure == null) firstFailure = error
            }
        }
        throw IllegalStateException(
            "no padding opened the payload (${firstFailure?.javaClass?.simpleName}: " +
                "${firstFailure?.message?.take(120)})",
            firstFailure,
        )
    }

    private fun privateEntry(): KeyStore.PrivateKeyEntry = keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "nativedo_device_login_2026v2"
        /** The first version authorized OAEP only; unused after the v2 key replaced it. */
        private const val OLD_ALIAS = "nativedo_device_login_2026"

        fun create(): AndroidKeystoreDeviceLoginKey? = runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            runCatching { keyStore.deleteEntry(OLD_ALIAS) }
            if (!keyStore.containsAlias(ALIAS)) {
                val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                generator.initialize(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(2048)
                        // Both paddings: the chain above must be able to try PKCS#1 as well.
                        .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                            KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        )
                        .setDigests(
                            KeyProperties.DIGEST_SHA1,
                            KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512,
                        )
                        .build(),
                )
                generator.generateKeyPair()
            }
            AndroidKeystoreDeviceLoginKey(keyStore).also { key ->
                check(key.selfTest()) { "AndroidKeystore RSA-OAEP self-test failed" }
            }
        }.getOrElse {
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
            }
            null
        }

        private fun AndroidKeystoreDeviceLoginKey.selfTest(): Boolean {
            val probe = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(DECRYPT_TRANSFORMATIONS.first().second)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val sealed = cipher.doFinal(probe)
            return probe.contentEquals(open(sealed).bytes)
        }
    }
}

/**
 * The fallback: an ordinary RSA pair whose private half rests only as AES/GCM ciphertext sealed
 * by the AndroidKeyStore credential key. Regeneration on a lost key is acceptable - the key is
 * only meaningful for the grant that names its public half.
 */
private class SoftwareDeviceLoginKey(
    private val privateKey: PrivateKey,
    override val publicKey: PublicKey,
    private val prefs: SharedPreferences?,
    private val cipher: CredentialCipher,
) : DeviceLoginKey {

    override val storage: String = "software"

    override fun open(sealed: ByteArray): OpenedPayload {
        var firstFailure: Throwable? = null
        for ((name, transformation) in DECRYPT_TRANSFORMATIONS) {
            try {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.DECRYPT_MODE, privateKey)
                return OpenedPayload(cipher.doFinal(sealed), name)
            } catch (error: GeneralSecurityException) {
                if (firstFailure == null) firstFailure = error
            }
        }
        throw IllegalStateException(
            "no padding opened the payload (${firstFailure?.javaClass?.simpleName}: " +
                "${firstFailure?.message?.take(120)})",
            firstFailure,
        )
    }

    companion object {
        private const val KEY_PRIVATE_PKCS8 = "rsa_private_pkcs8"

        fun create(prefs: SharedPreferences?, cipher: CredentialCipher): SoftwareDeviceLoginKey {
            val stored = prefs?.getString(KEY_PRIVATE_PKCS8, null)?.let(cipher::decrypt)
            if (stored != null) {
                val key = runCatching { decodePrivate(stored) }.getOrNull()
                if (key != null) {
                    return SoftwareDeviceLoginKey(key, publicFor(key), prefs, cipher)
                }
            }
            val generated = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val encoded = Base64.encodeToString(generated.private.encoded, Base64.NO_WRAP)
            cipher.encrypt(encoded)?.let { sealed ->
                prefs?.edit()?.putString(KEY_PRIVATE_PKCS8, sealed)?.apply()
            }
            return SoftwareDeviceLoginKey(generated.private, generated.public, prefs, cipher)
        }

        /** Uses only the private half; the stored form does not need to carry the public half. */
        internal fun overTestPair(pair: KeyPair): SoftwareDeviceLoginKey =
            SoftwareDeviceLoginKey(pair.private, pair.public, null, AndroidKeystoreCipher)

        private fun decodePrivate(encodedPrivateKey: String): PrivateKey =
            KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(Base64.decode(encodedPrivateKey, Base64.NO_WRAP)),
            )

        private fun publicFor(privateKey: PrivateKey): PublicKey {
            check(privateKey is java.security.interfaces.RSAPrivateCrtKey) { "not an RSA CRT key" }
            val spec = java.security.spec.RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)
            return KeyFactory.getInstance("RSA").generatePublic(spec)
        }
    }
}
