package io.github.baba983.nativedo.feature.profile.data

import android.content.SharedPreferences
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

open class ProfileRepository protected constructor() {
    private lateinit var client: DiscourseApiClient
    private var json: Json? = null
    private var preferences: SharedPreferences? = null
    private val cacheMutex = Mutex()
    private val profileCache = ConcurrentHashMap<String, UserProfile>()

    constructor(
        client: DiscourseApiClient,
        preferences: SharedPreferences? = null,
        json: Json? = null,
    ) : this() {
        this.client = client
        this.preferences = preferences
        this.json = json
    }

    open fun cachedProfile(username: String): UserProfile? =
        profileCache[username] ?: readDiskCache(username)

    open suspend fun load(username: String): Result<UserProfile> = loadInternal(username, forceRefresh = false)

    open suspend fun refresh(username: String): Result<UserProfile> = loadInternal(username, forceRefresh = true)

    private suspend fun loadInternal(username: String, forceRefresh: Boolean): Result<UserProfile> =
        cacheMutex.withLock {
            if (!forceRefresh) {
                profileCache[username]?.let { return@withLock Result.success(it) }
                readDiskCache(username)?.let { return@withLock Result.success(it) }
            }
            runCatching { client.fetchProfile(username) }
                .onSuccess {
                    profileCache[username] = it
                    writeDiskCache(username, it)
                }
        }

    private fun readDiskCache(username: String): UserProfile? {
        val json = json ?: return null
        val prefs = preferences ?: return null
        val raw = prefs.getString(PROFILE_CACHE_PREFIX + username, null) ?: return null
        return runCatching { json.decodeFromString<UserProfile>(raw) }.getOrNull()
    }

    private fun writeDiskCache(username: String, profile: UserProfile) {
        val json = json ?: return
        val prefs = preferences ?: return
        runCatching {
            prefs.edit().putString(PROFILE_CACHE_PREFIX + username, json.encodeToString(profile)).apply()
        }
    }

    companion object {
        private const val PROFILE_CACHE_PREFIX = "profile_cache_"
    }
}
