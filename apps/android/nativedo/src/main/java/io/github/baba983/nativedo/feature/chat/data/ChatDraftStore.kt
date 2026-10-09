package io.github.baba983.nativedo.feature.chat.data

import android.content.SharedPreferences

/**
 * One channel's half-written message: the text and, when the writer was answering a message, that
 * message's id. A draft belongs to the site, the account **and** the channel, so two channels never
 * share a field and a draft never follows the reader to another account.
 */
data class ChatDraft(
    val raw: String = "",
    val inReplyToId: Long? = null,
) {
    val isEmpty: Boolean get() = raw.isBlank() && inReplyToId == null
}

/**
 * The key one chat draft is stored under: the site, the authenticated account and the channel. The
 * channel is the innermost scope exactly as a topic's own draft is per topic.
 */
data class ChatDraftKey(
    val baseUrl: String,
    val username: String,
    val channelId: Long,
) {
    internal fun preferencePrefix(): String = buildString {
        append("chat|")
        append(baseUrl.trim().trimEnd('/').lowercase())
        append('|')
        append(username.trim().lowercase())
        append('|')
        append(channelId)
    }
}

/** A channel's draft, kept while the screen is away and across a process restart. */
class ChatDraftStore(
    private val persistence: ChatDraftPersistence = NoOpChatDraftPersistence,
) {
    fun load(key: ChatDraftKey): ChatDraft = persistence.load(key)

    fun save(key: ChatDraftKey, draft: ChatDraft) = persistence.save(key, draft)
}

interface ChatDraftPersistence {
    fun load(key: ChatDraftKey): ChatDraft

    fun save(key: ChatDraftKey, draft: ChatDraft)
}

class SharedPreferencesChatDraftPersistence(
    private val preferences: SharedPreferences,
) : ChatDraftPersistence {
    override fun load(key: ChatDraftKey): ChatDraft {
        val prefix = key.preferencePrefix()
        val raw = preferences.getString("$prefix.raw", "").orEmpty()
        val replyKey = "$prefix.reply_to"
        val inReplyToId = preferences.takeIf { it.contains(replyKey) }
            ?.getLong(replyKey, 0L)
            ?.takeIf { it > 0L }
        return ChatDraft(raw = raw, inReplyToId = inReplyToId)
    }

    override fun save(key: ChatDraftKey, draft: ChatDraft) {
        val prefix = key.preferencePrefix()
        val editor = preferences.edit()
        if (draft.isEmpty) {
            editor.remove("$prefix.raw").remove("$prefix.reply_to").apply()
            return
        }
        editor.putString("$prefix.raw", draft.raw)
        draft.inReplyToId?.let { editor.putLong("$prefix.reply_to", it) }
            ?: editor.remove("$prefix.reply_to")
        editor.apply()
    }
}

private object NoOpChatDraftPersistence : ChatDraftPersistence {
    override fun load(key: ChatDraftKey): ChatDraft = ChatDraft()

    override fun save(key: ChatDraftKey, draft: ChatDraft) = Unit
}
