package io.github.baba983.nativedo.feature.topic.data

import android.content.SharedPreferences

data class TopicReplyDraft(
    val raw: String = "",
    val replyToPostNumber: Int? = null,
)

data class TopicReplyDraftKey(
    val baseUrl: String,
    val username: String,
    val topicId: Long,
) {
    internal fun preferencePrefix(): String = buildString {
        append(baseUrl.trim().trimEnd('/').lowercase())
        append('|')
        append(username.trim().lowercase())
        append('|')
        append(topicId)
    }
}

class TopicReplyDraftStore(
    private val persistence: TopicReplyDraftPersistence = NoOpTopicReplyDraftPersistence,
) {
    fun load(key: TopicReplyDraftKey): TopicReplyDraft = persistence.load(key)

    fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) {
        persistence.save(key, draft)
    }
}

interface TopicReplyDraftPersistence {
    fun load(key: TopicReplyDraftKey): TopicReplyDraft

    fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft)
}

class SharedPreferencesTopicReplyDraftPersistence(
    private val preferences: SharedPreferences,
) : TopicReplyDraftPersistence {
    override fun load(key: TopicReplyDraftKey): TopicReplyDraft {
        val prefix = key.preferencePrefix()
        val raw = preferences.getString("$prefix.raw", "").orEmpty()
        val replyKey = "$prefix.reply_to"
        val replyToPostNumber = preferences.takeIf { it.contains(replyKey) }?.getInt(replyKey, 0)?.takeIf { it > 0 }
        return TopicReplyDraft(raw = raw, replyToPostNumber = replyToPostNumber)
    }

    override fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) {
        val prefix = key.preferencePrefix()
        val editor = preferences.edit()
        if (draft.raw.isBlank() && draft.replyToPostNumber == null) {
            editor.remove("$prefix.raw").remove("$prefix.reply_to").apply()
            return
        }
        editor.putString("$prefix.raw", draft.raw)
        draft.replyToPostNumber?.let { editor.putInt("$prefix.reply_to", it) }
            ?: editor.remove("$prefix.reply_to")
        editor.apply()
    }
}

private object NoOpTopicReplyDraftPersistence : TopicReplyDraftPersistence {
    override fun load(key: TopicReplyDraftKey): TopicReplyDraft = TopicReplyDraft()

    override fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) = Unit
}
