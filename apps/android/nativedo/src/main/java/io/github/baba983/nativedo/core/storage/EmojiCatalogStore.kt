package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import io.github.baba983.nativedo.core.network.EmojiDto
import io.github.baba983.nativedo.core.network.EmojiGroupsDto
import io.github.baba983.nativedo.core.network.NetworkJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The site's emoji table, kept between topic reads.
 *
 * It is decoration: a floor draws without it, and a reaction whose payload carries no icon falls
 * back to a bare count. That is why the table is cached at all - a topic read with a warm cache
 * makes no catalog request, so the request cannot hold the topic up. The one read that has to ask
 * for it asks once and keeps the answer; see `DiscourseApiClient.emojiGroupsForDecoration`.
 */
class EmojiCatalogStore(
    private val persistence: EmojiCatalogStorePersistence,
) {
    constructor() : this(persistence = NoOpEmojiCatalogStorePersistence())

    private val _groups = MutableStateFlow(persistence.load().orEmpty())
    val groups: StateFlow<EmojiGroupsDto> = _groups.asStateFlow()

    fun save(groups: EmojiGroupsDto) {
        if (groups.isEmpty() || groups == _groups.value) {
            return
        }
        _groups.value = groups
        persistence.save(groups)
    }
}

interface EmojiCatalogStorePersistence {
    fun load(): EmojiGroupsDto?

    fun save(groups: EmojiGroupsDto)
}

class SharedPreferencesEmojiCatalogStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : EmojiCatalogStorePersistence {

    override fun load(): EmojiGroupsDto? {
        val raw = sharedPreferences.getString(KEY_GROUPS, null) ?: return null
        return runCatching {
            NetworkJson.instance.decodeFromJsonElement<EmojiCatalogSnapshot>(
                NetworkJson.instance.parseToJsonElement(raw),
            ).groups
        }.getOrNull()?.takeIf { groups -> groups.isNotEmpty() }
    }

    override fun save(groups: EmojiGroupsDto) {
        val raw = NetworkJson.instance.encodeToString(
            EmojiCatalogSnapshot.serializer(),
            EmojiCatalogSnapshot(groups),
        )
        sharedPreferences.edit()
            .putString(KEY_GROUPS, raw)
            .apply()
    }

    private companion object {
        const val KEY_GROUPS: String = "emoji_groups"
    }
}

/** The stored shape: the site's groups, as `emojis.json` answers them. */
@Serializable
private data class EmojiCatalogSnapshot(
    val groups: Map<String, List<EmojiDto>> = emptyMap(),
)

private class NoOpEmojiCatalogStorePersistence : EmojiCatalogStorePersistence {
    override fun load(): EmojiGroupsDto? = null

    override fun save(groups: EmojiGroupsDto) = Unit
}