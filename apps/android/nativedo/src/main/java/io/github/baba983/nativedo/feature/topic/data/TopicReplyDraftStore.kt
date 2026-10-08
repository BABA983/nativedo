package io.github.baba983.nativedo.feature.topic.data

import android.content.SharedPreferences

data class TopicReplyDraft(
    val raw: String = "",
    val replyToPostNumber: Int? = null,
)

/**
 * The key one reply draft is stored under.
 *
 * <p>[rootPostNumber] is the optional second scope: the discussion page opened from a floor's reply
 * footer keeps its own draft, so the text written while answering a floor's thread never lands in
 * the topic's own field and two different threads of one topic never share a field either. It is
 * null for a normal topic read, and the prefix is then byte-for-byte the one that key always had,
 * so every draft written before threads existed still resolves.</p>
 */
data class TopicReplyDraftKey(
    val baseUrl: String,
    val username: String,
    val topicId: Long,
    val rootPostNumber: Int? = null,
) {
    internal fun preferencePrefix(): String = buildString {
        append(baseUrl.trim().trimEnd('/').lowercase())
        append('|')
        append(username.trim().lowercase())
        append('|')
        append(topicId)
        if (rootPostNumber != null && rootPostNumber > 0) {
            append("|thread|")
            append(rootPostNumber)
        }
    }
}

/**
 * A topic that is being started but has not been posted: the title, the chosen category and the
 * body. There is no topic id yet - the site assigns it - so the key is the account alone, the way
 * the site's own composer keeps one new-topic draft per reader.
 */
data class NewTopicDraft(
    val title: String = "",
    val categoryId: Int? = null,
    val raw: String = "",
)

data class NewTopicDraftKey(
    val baseUrl: String,
    val username: String,
) {
    internal fun preferencePrefix(): String = buildString {
        append("new|")
        append(baseUrl.trim().trimEnd('/').lowercase())
        append('|')
        append(username.trim().lowercase())
    }
}

class TopicReplyDraftStore(
    private val persistence: TopicReplyDraftPersistence = NoOpTopicReplyDraftPersistence,
) {
    fun load(key: TopicReplyDraftKey): TopicReplyDraft = persistence.load(key)

    fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) {
        persistence.save(key, draft)
    }

    fun loadNewTopic(key: NewTopicDraftKey): NewTopicDraft = persistence.loadNewTopic(key)

    fun saveNewTopic(key: NewTopicDraftKey, draft: NewTopicDraft) {
        persistence.saveNewTopic(key, draft)
    }
}

interface TopicReplyDraftPersistence {
    fun load(key: TopicReplyDraftKey): TopicReplyDraft

    fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft)

    fun loadNewTopic(key: NewTopicDraftKey): NewTopicDraft

    fun saveNewTopic(key: NewTopicDraftKey, draft: NewTopicDraft)
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

    override fun loadNewTopic(key: NewTopicDraftKey): NewTopicDraft {
        val prefix = key.preferencePrefix()
        val title = preferences.getString("$prefix.title", "").orEmpty()
        val raw = preferences.getString("$prefix.raw", "").orEmpty()
        val categoryKey = "$prefix.category"
        val categoryId = preferences.takeIf { it.contains(categoryKey) }?.getInt(categoryKey, 0)?.takeIf { it > 0 }
        return NewTopicDraft(title = title, categoryId = categoryId, raw = raw)
    }

    override fun saveNewTopic(key: NewTopicDraftKey, draft: NewTopicDraft) {
        val prefix = key.preferencePrefix()
        val editor = preferences.edit()
        if (draft.title.isBlank() && draft.raw.isBlank() && draft.categoryId == null) {
            editor.remove("$prefix.title").remove("$prefix.raw").remove("$prefix.category").apply()
            return
        }
        editor.putString("$prefix.title", draft.title)
        editor.putString("$prefix.raw", draft.raw)
        draft.categoryId?.let { editor.putInt("$prefix.category", it) }
            ?: editor.remove("$prefix.category")
        editor.apply()
    }
}

private object NoOpTopicReplyDraftPersistence : TopicReplyDraftPersistence {
    override fun load(key: TopicReplyDraftKey): TopicReplyDraft = TopicReplyDraft()

    override fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) = Unit

    override fun loadNewTopic(key: NewTopicDraftKey): NewTopicDraft = NewTopicDraft()

    override fun saveNewTopic(key: NewTopicDraftKey, draft: NewTopicDraft) = Unit
}
