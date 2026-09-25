package io.github.baba983.nativedo.core.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialStoreTest {
    private class FakeCipher : CredentialCipher {
        override fun encrypt(plaintext: String): String? = "sealed:" + plaintext.reversed()

        override fun decrypt(ciphertext: String): String? =
            ciphertext.removePrefix("sealed:").reversed().takeIf { ciphertext.startsWith("sealed:") }
    }

    @Test
    fun savedPasswordIsSealedBeforeItReachesDisk() {
        val storage = HashMap<String, String>()
        val prefs = FakeSharedPreferences(storage)
        val store = CredentialStore(prefs, FakeCipher())

        store.save(username = "baba", password = "hunter2")

        assertThat(storage.getValue("password")).isEqualTo("sealed:2retnuh")
        assertThat(storage.getValue("username")).isEqualTo("baba")
    }

    @Test
    fun loadDecryptsTheSealedPassword() {
        val storage = HashMap<String, String>()
        val store = CredentialStore(FakeSharedPreferences(storage), FakeCipher())
        store.save(username = "baba", password = "demo-secret")

        val credentials = store.load()

        assertThat(credentials.username).isEqualTo("baba")
        assertThat(credentials.password).isEqualTo("demo-secret")
        assertThat(store.hasCredentials()).isTrue()
    }

    @Test
    fun undecryptablePasswordReadsBackAsAbsent() {
        val storage = HashMap<String, String>()
        storage["username"] = "baba"
        storage["password"] = "plaintext-legacy"
        val store = CredentialStore(FakeSharedPreferences(storage), FakeCipher())

        assertThat(store.load().password).isNull()
        assertThat(store.hasCredentials()).isFalse()
    }
}

/** Minimal SharedPreferences stand-in so tests observe exactly what is written. */
private class FakeSharedPreferences(
    private val storage: MutableMap<String, String>,
) : android.content.SharedPreferences {
    override fun getString(key: String?, defValue: String?): String? =
        key?.let(storage::get) ?: defValue

    override fun edit(): android.content.SharedPreferences.Editor = Editor()

    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getAll(): MutableMap<String, *> = storage
    override fun contains(key: String?): Boolean = storage.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
            if (key == null || value == null) {
                key?.let(storage::remove)
            } else {
                storage[key] = value
            }
            return this
        }

        override fun clear(): android.content.SharedPreferences.Editor {
            storage.clear()
            return this
        }

        override fun apply() = Unit
        override fun commit(): Boolean = true
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putLong(key: String?, value: Long) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
        override fun remove(key: String?) = this
    }
}
