package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire payloads of the reads a reader has already made, kept between launches.
 *
 * <p>What is stored is the site's own DTO JSON, not a domain model: a cached answer is rebuilt by
 * the same `toModel` mapping the live read uses, so an offline read and a live one reach the screen
 * through one code path. Every key includes the site and reader identity, not the in-memory session
 * generation; `DiscourseApiClient` checks that generation before committing an in-flight read.</p>
 *
 * <p>Bounded on purpose: a device cannot keep every page it has scrolled. The eldest entry is
 * dropped when either the entry count or the stored size passes its cap, so the cache converges on
 * the reader's recent reading instead of growing without limit.</p>
 */
class DiscourseReadCache(
    private val persistence: DiscourseReadCachePersistence,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    constructor() : this(NoOpDiscourseReadCachePersistence)

    /** Insertion order, so the eldest entry is the first key and eviction is O(1) to find. */
    private val entries = LinkedHashMap<String, String>()

    init {
        persistence.load()?.forEach { stored ->
            if (stored.json.isNotEmpty()) {
                entries[stored.key] = stored.json
            }
        }
        trim()
    }

    /** The stored payload for [key], or null; a hit is marked most-recently-used. */
    @Synchronized
    fun read(key: String): String? {
        val json = entries.remove(key) ?: return null
        entries[key] = json
        return json
    }

    /** Check ownership under the clear/write lock so a late read cannot undo logout's clear. */
    @Synchronized
    fun write(key: String, json: String, stillOwned: () -> Boolean) {
        if (json.isEmpty() || !stillOwned()) {
            return
        }
        entries.remove(key)
        entries[key] = json
        trim()
        persistence.save(entries.map { (storedKey, payload) -> CachedReadPayload(storedKey, payload) })
    }

    /**
     * Drops every stored read, in memory and on disk. Called from the session's own clear path (a
     * logout or a server-proven logout), so a cache written for a reader who is gone cannot outlive
     * the session that produced it.
     */
    @Synchronized
    fun clear() {
        entries.clear()
        persistence.save(emptyList())
    }

    private fun trim() {
        while (entries.size > maxEntries && entries.isNotEmpty()) {
            entries.remove(entries.keys.first())
        }
        var total = entries.values.sumOf { payload -> payload.toByteArray(Charsets.UTF_8).size }
        while (total > maxBytes && entries.isNotEmpty()) {
            val eldest = entries.keys.first()
            total -= entries.remove(eldest)?.toByteArray(Charsets.UTF_8)?.size ?: 0
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 40
        const val DEFAULT_MAX_BYTES = 1_000_000
    }
}

/** One stored entry: the site/reader-scoped key and the site's own DTO JSON. */
@Serializable
data class CachedReadPayload(
    val key: String,
    val json: String,
)

interface DiscourseReadCachePersistence {
    fun load(): List<CachedReadPayload>?

    fun save(entries: List<CachedReadPayload>)
}

class SharedPreferencesDiscourseReadCachePersistence(
    private val sharedPreferences: SharedPreferences,
) : DiscourseReadCachePersistence {

    override fun load(): List<CachedReadPayload>? {
        val raw = sharedPreferences.getString(KEY_ENTRIES, null) ?: return null
        return runCatching {
            json.decodeFromString(CachedReadPayloadList.serializer(), raw).entries
        }.getOrNull()
    }

    override fun save(entries: List<CachedReadPayload>) {
        sharedPreferences.edit()
            .putString(KEY_ENTRIES, json.encodeToString(CachedReadPayloadList.serializer(), CachedReadPayloadList(entries)))
            .apply()
    }

    private companion object {
        const val KEY_ENTRIES = "cached_reads"
        val json: Json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CachedReadPayloadList(
    val entries: List<CachedReadPayload> = emptyList(),
)

private object NoOpDiscourseReadCachePersistence : DiscourseReadCachePersistence {
    override fun load(): List<CachedReadPayload>? = null

    override fun save(entries: List<CachedReadPayload>) = Unit
}
