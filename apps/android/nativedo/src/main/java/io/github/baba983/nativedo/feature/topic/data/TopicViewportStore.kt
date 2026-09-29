package io.github.baba983.nativedo.feature.topic.data

import android.content.SharedPreferences

/** Position relative to RecyclerView's content padding, independent of highest read post. */
data class TopicViewport(val postNumber: Int, val offsetPx: Int)

class TopicViewportStore(private val preferences: SharedPreferences? = null) {
    private val memory = mutableMapOf<TopicReadPositionKey, TopicViewport>()

    fun load(key: TopicReadPositionKey): TopicViewport? {
        if (preferences == null) return memory[key]
        val value = preferences.getString(key.preferenceKey() + ".viewport", null) ?: return null
        val parts = value.split(':')
        val post = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val offset = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return TopicViewport(post, offset)
    }

    fun save(key: TopicReadPositionKey, viewport: TopicViewport) {
        if (viewport.postNumber <= 0) return
        memory[key] = viewport
        preferences?.edit()?.putString(key.preferenceKey() + ".viewport",
            "${viewport.postNumber}:${viewport.offsetPx}")?.apply()
    }
}
