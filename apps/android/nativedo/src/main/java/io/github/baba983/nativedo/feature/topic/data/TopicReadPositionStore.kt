package io.github.baba983.nativedo.feature.topic.data

import android.content.SharedPreferences

data class TopicReadPositionKey(
    val baseUrl: String,
    val username: String,
    val topicId: Long,
) {
    internal fun preferenceKey(): String = buildString {
        append(baseUrl.trim().trimEnd('/').lowercase())
        append('|')
        append(username.trim().lowercase())
        append('|')
        append(topicId)
        append(".post_number")
    }
}

class TopicReadPositionStore(
    private val persistence: TopicReadPositionPersistence = NoOpTopicReadPositionPersistence,
) {
    @Synchronized
    fun load(key: TopicReadPositionKey): Int = persistence.load(key).coerceAtLeast(0)

    @Synchronized
    fun save(key: TopicReadPositionKey, postNumber: Int) {
        val next = postNumber.coerceAtLeast(0)
        if (next > persistence.load(key)) {
            persistence.save(key, next)
        }
    }
}

interface TopicReadPositionPersistence {
    fun load(key: TopicReadPositionKey): Int

    fun save(key: TopicReadPositionKey, postNumber: Int)
}

class SharedPreferencesTopicReadPositionPersistence(
    private val preferences: SharedPreferences,
) : TopicReadPositionPersistence {
    override fun load(key: TopicReadPositionKey): Int =
        preferences.getInt(key.preferenceKey(), 0).coerceAtLeast(0)

    override fun save(key: TopicReadPositionKey, postNumber: Int) {
        preferences.edit().putInt(key.preferenceKey(), postNumber.coerceAtLeast(0)).apply()
    }
}

private object NoOpTopicReadPositionPersistence : TopicReadPositionPersistence {
    override fun load(key: TopicReadPositionKey): Int = 0

    override fun save(key: TopicReadPositionKey, postNumber: Int) = Unit
}
