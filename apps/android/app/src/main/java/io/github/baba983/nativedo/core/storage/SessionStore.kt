package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import io.github.baba983.nativedo.core.session.SessionSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStore(
    private val persistence: SessionStorePersistence,
) {
    constructor() : this(persistence = NoOpSessionStorePersistence())

    private var snapshot: SessionSnapshot? = persistence.load()

    fun save(snapshot: SessionSnapshot) {
        this.snapshot = snapshot
        persistence.save(snapshot)
    }

    fun snapshot(): SessionSnapshot? = snapshot

    fun clear() {
        snapshot = null
        persistence.save(snapshot = null)
    }
}

interface SessionStorePersistence {
    fun load(): SessionSnapshot?

    fun save(snapshot: SessionSnapshot?)
}

class SharedPreferencesSessionStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : SessionStorePersistence {
    override fun load(): SessionSnapshot? {
        val rawSnapshot = sharedPreferences.getString(KEY_SNAPSHOT, null).orEmpty()
        if (rawSnapshot.isBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString<SessionSnapshot>(rawSnapshot)
        }.getOrNull()
    }

    override fun save(snapshot: SessionSnapshot?) {
        if (snapshot == null) {
            sharedPreferences.edit().remove(KEY_SNAPSHOT).apply()
            return
        }
        sharedPreferences.edit()
            .putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
            .apply()
    }

    private companion object {
        const val KEY_SNAPSHOT: String = "session_snapshot"
        val json: Json = Json
    }
}

private class NoOpSessionStorePersistence : SessionStorePersistence {
    override fun load(): SessionSnapshot? = null

    override fun save(snapshot: SessionSnapshot?) = Unit
}
