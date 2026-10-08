// SPDX-License-Identifier: GPL-2.0-or-later

package io.github.baba983.nativedo.topic

import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.SpanWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.feature.topic.data.TopicMentionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.EditTextCaption

/**
 * The `@mention` token under the cursor: where it starts, where the cursor is inside it, where the
 * run of username characters ends, and the prefix typed so far.
 */
internal data class MentionToken(
    val start: Int,
    val cursor: Int,
    val end: Int,
    val query: String,
)

/** A username is `[A-Za-z0-9_.-]`, which is what the site itself accepts in a mention. */
private fun isMentionChar(ch: Char): Boolean =
    ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-' || ch == '.'

/**
 * Whether a character may sit immediately before an `@` for it to open a mention. Requiring a
 * boundary is what keeps `email@host` and `https://site/@name` from opening the panel: the character
 * before their `@` is a word character or a path separator, never one of these.
 */
private fun isMentionBoundary(ch: Char): Boolean =
    ch.isWhitespace() || ch == '(' || ch == '[' || ch == '{' || ch == '"' || ch == '\'' || ch == '>'

/**
 * The mention token [cursor] sits in, or null when the cursor is not inside one. Only the cursor's
 * own run is considered, so a caret placed in the middle of `@alice` reports `ali`.
 */
internal fun mentionTokenAt(text: CharSequence, cursor: Int): MentionToken? {
    if (cursor <= 0 || cursor > text.length) return null
    var start = cursor - 1
    while (start >= 0) {
        val ch = text[start]
        if (ch == '@') break
        if (!isMentionChar(ch)) return null
        start--
    }
    if (start < 0 || text[start] != '@') return null
    if (start > 0 && !isMentionBoundary(text[start - 1])) return null
    var end = cursor
    while (end < text.length && isMentionChar(text[end])) {
        end++
    }
    return MentionToken(
        start = start,
        cursor = cursor,
        end = end,
        query = text.subSequence(start + 1, cursor).toString(),
    )
}

/**
 * What to put in the token's place, or null when the token no longer matches the field (a stale
 * panel selection). A trailing space is added unless the field already has one, which is how
 * upstream's own mention insertion finishes a name.
 */
internal fun mentionInsertion(text: CharSequence, token: MentionToken, username: String): String? {
    if (token.start !in 0..text.length || token.end !in token.start..text.length) return null
    if (token.cursor !in (token.start + 1)..token.end) return null
    if (text.subSequence(token.start, token.cursor).toString() != "@${token.query}") return null
    if (text.subSequence(token.cursor, token.end).any { !isMentionChar(it) }) return null
    val cleaned = username.trim().removePrefix("@")
    if (cleaned.isEmpty()) return null
    val needsSpace = token.end >= text.length || !text[token.end].isWhitespace()
    return "@$cleaned" + if (needsSpace) " " else ""
}

/** The topic's loaded post authors, first appearance first, deduped case-insensitively. */
internal fun mentionLocalCandidates(posts: List<Post>): List<MentionCandidate> {
    val seen = HashSet<String>()
    return posts.mapNotNull { post ->
        val username = post.authorUsername.trim()
        if (username.isEmpty() || !seen.add(username.lowercase())) return@mapNotNull null
        MentionCandidate(
            username = username,
            displayName = post.authorDisplayName,
            avatarTemplate = post.authorAvatarTemplate,
            animatedAvatarUrl = post.authorAnimatedAvatarUrl,
        )
    }
}

/**
 * The server-ranked participants first, then the loaded authors they did not already name, which is
 * the contract's ordering: the site's own ranking leads, the page supplies what it has not seen.
 */
internal fun mentionLocalCandidates(
    participants: List<MentionCandidate>,
    posts: List<Post>,
): List<MentionCandidate> {
    val seen = HashSet<String>()
    return buildList {
        participants.forEach { participant ->
            if (participant.username.isNotBlank() && seen.add(participant.username.lowercase())) {
                add(participant)
            }
        }
        mentionLocalCandidates(posts).forEach { participant ->
            if (seen.add(participant.username.lowercase())) {
                add(participant)
            }
        }
    }
}

private fun mentionMatches(candidate: MentionCandidate, query: String): Boolean {
    if (query.isEmpty()) return true
    if (candidate.username.lowercase().startsWith(query)) return true
    return candidate.displayName.lowercase().splitToSequence(' ', '-', '_').any { it.startsWith(query) }
}

/**
 * The server's ranked answer first, then the local matches it did not name, deduped and capped.
 *
 * <p>Once the answer has arrived the server's order leads - it is ranked in topic context, users
 * and mentionable groups together - so a topic with many authors cannot push the site's mentionable
 * groups past the cap. Before any answer arrives [remote] is empty and this returns the local
 * matches alone, so the topic's own candidates are drawn immediately. The server's list is not
 * re-filtered by the prefix: its answer already answers this query, and a group's own name is not a
 * username prefix. Only the local list is narrowed by the prefix, because it holds every
 * participant regardless of what was typed.</p>
 */
internal fun mentionMerge(
    local: List<MentionCandidate>,
    remote: List<MentionCandidate>,
    query: String,
): List<MentionCandidate> {
    val normalized = query.trim().lowercase()
    val seen = HashSet<String>()
    val remoteMatches = remote.filter { it.username.isNotBlank() && seen.add(it.username.lowercase()) }
    val localMatches = local.filter { mentionMatches(it, normalized) && seen.add(it.username.lowercase()) }
    return (remoteMatches + localMatches).take(MAX_MENTION_RESULTS)
}

private const val MAX_MENTION_RESULTS = 20
private const val MENTION_DEBOUNCE_MS = 200L

/**
 * The composer's `@mention` autocomplete: what upstream's own mention panel shows over a chat, held
 * against the site's candidates.
 *
 * <p>It attaches to the composer's own field (no delegate plumbing through the screen), reads the
 * token under the caret, shows the topic's participants and loaded authors at once, and debounces a
 * topic-aware `/u/search/users` request behind them. A response is applied only if it belongs to the
 * token still under the caret (a generation counter), so a slow answer can never overwrite a newer
 * one. Picking a candidate rewrites just that token and leaves the rest of the field alone. Every
 * exit - a selection, a dismiss, a pause, the screen's destroy - cancels the pending request and
 * hides the panel; a pause additionally silences the field's watchers until the screen resumes, so
 * a write while the reader is away cannot reopen the panel or send a query.</p>
 */
class DiscourseMentions internal constructor(
    private val field: EditTextCaption,
    private val panel: DiscourseMentionsPanel,
    private val repository: TopicMentionRepository,
    private val scope: CoroutineScope,
    private val topicId: Long?,
    private val baseUrl: String,
    private val imageClient: OkHttpClient?,
) {

    private var generation = 0
    private var queryJob: Job? = null
    private var token: MentionToken? = null
    private var local: List<MentionCandidate> = emptyList()

    /** Set while this class rewrites the field itself, so the watchers do not re-enter. */
    private var applying = false

    /** Set while the screen is paused: the field's watchers stay attached but have no effect. */
    private var paused = false

    /** Set once the screen is destroyed: nothing may reopen the panel or touch it afterward. */
    private var destroyed = false

    /** Every avatar fetch this controller launched, so destroy can cancel them. */
    private val avatarJobs = mutableListOf<Job>()

    /** The (text, caret) pair last read, so a text edit and its own caret move are read once. */
    private var lastState: Pair<String, Int>? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (applying) return
            ensureSelectionWatcher()
            if (paused || destroyed) return
            schedule()
        }
    }

    /**
     * Upstream's field moves its caret after it writes the text (`setFieldText`), and a reader can
     * move it without typing; a text watcher alone sees neither. Selection is a span pair, so a
     * {@link SpanWatcher} on the same editable sees the caret land wherever it lands.
     */
    private val selectionWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) {}
        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) {}
        override fun onSpanChanged(
            text: Spannable,
            what: Any,
            ostart: Int,
            oend: Int,
            nstart: Int,
            nend: Int,
        ) {
            if (applying || paused || destroyed) return
            if (what === Selection.SELECTION_START || what === Selection.SELECTION_END) {
                schedule()
            }
        }
    }

    init {
        panel.setMentionBaseUrl(baseUrl)
        panel.setMentionDarkTheme(Theme.isCurrentThemeDark())
        panel.setListener { candidate -> apply(candidate) }
        field.addTextChangedListener(watcher)
        ensureSelectionWatcher()
    }

    /** The panel this controller drives; the screen adds no second one. */
    fun panel(): DiscourseMentionsPanel = panel

    /**
     * The topic's ranked participants plus its loaded authors. The server's participants are already
     * ranked; the loaded posts fill in authors the site's short list did not name.
     */
    fun setLocalCandidates(participants: List<MentionCandidate>, posts: List<Post>) {
        local = mentionLocalCandidates(participants, posts)
        if (token != null) {
            lastState = null
            schedule()
        }
    }

    /** Keeps the caret observer attached across the field's own full-text writes. */
    private fun ensureSelectionWatcher() {
        val spannable = field.text as? Spannable ?: return
        if (spannable.getSpanStart(selectionWatcher) < 0) {
            spannable.setSpan(selectionWatcher, 0, spannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    /**
     * The field's text or caret moved (or the local candidates changed): read the token once and
     * answer it. The same (text, caret) pair is ignored, so an edit and its own caret move - or a
     * pending-results refresh - do not each fire a request.
     */
    private fun schedule() {
        if (paused || destroyed) return
        val editable = field.text ?: return
        val cursor = field.selectionStart
        if (cursor < 0 || cursor > editable.length) return
        val state = editable.toString() to cursor
        if (state == lastState) return
        lastState = state
        process(cursor)
    }

    private fun process(cursor: Int) {
        val editable = field.text ?: return
        val next = mentionTokenAt(editable, cursor)
        token = next
        generation++
        queryJob?.cancel()
        queryJob = null

        if (next == null) {
            panel.hide()
            return
        }

        val localMatches = mentionMerge(local, emptyList(), next.query)
        if (localMatches.isEmpty() && next.query.isNotEmpty()) {
            panel.hide()
        } else {
            showPanel(localMatches)
        }

        val generationAt = generation
        val requested = next
        queryJob = scope.launch {
            delay(MENTION_DEBOUNCE_MS)
            val remote = repository.search(requested.query, topicId).getOrNull()?.candidates.orEmpty()
            if (generationAt != generation || token != requested) return@launch
            showPanel(mentionMerge(local, remote, requested.query))
        }
    }

    private fun showPanel(candidates: List<MentionCandidate>) {
        panel.submit(candidates)
        cacheAvatars(candidates)
    }

    /** A candidate was picked: only the token under the caret is rewritten. */
    private fun apply(candidate: MentionCandidate) {
        val editable = field.text ?: return
        val current = mentionTokenAt(editable, field.selectionStart) ?: token ?: return
        val insertion = mentionInsertion(editable, current, candidate.username) ?: return
        generation++
        queryJob?.cancel()
        queryJob = null
        token = null
        applying = true
        editable.replace(current.start, current.end, insertion)
        field.setSelection(current.start + insertion.length)
        applying = false
        panel.hide()
    }

    /** The reader left the token, dismissed the panel, or the screen is going away. */
    fun dismiss() {
        generation++
        queryJob?.cancel()
        queryJob = null
        token = null
        panel.hide()
    }

    /**
     * Forwarded by the screen when it pauses: the panel is hidden and the field's watchers go
     * quiet. A write into the composer while the screen is behind another - the rich editor applies
     * back into the field, a draft is restored - must not reopen the panel or send a query.
     */
    fun onPause() {
        paused = true
        dismiss()
    }

    /**
     * Forwarded by the screen when it comes back: the watchers are active again and the token under
     * the caret is answered afresh, so a mention the reader left mid-typing is offered again.
     */
    fun onResume() {
        paused = false
        lastState = null
        schedule()
    }

    /** Forwarded by the screen when it is destroyed: watchers, jobs and the adapter are released. */
    fun onDestroy() {
        destroyed = true
        dismiss()
        field.removeTextChangedListener(watcher)
        (field.text as? Spannable)?.removeSpan(selectionWatcher)
        avatarJobs.forEach { it.cancel() }
        avatarJobs.clear()
        panel.setListener(null)
        panel.destroy()
    }

    /**
     * Fetches the candidates' avatars through the module's own client and re-binds the rows once a
     * file lands, so a row shows the site's picture rather than the initials it drew meanwhile.
     */
    private fun cacheAvatars(candidates: List<MentionCandidate>) {
        val client = imageClient ?: return
        if (destroyed) return
        // A query can be typed many times; drop the fetches that already finished before adding.
        avatarJobs.removeAll { it.isCompleted }
        for (candidate in candidates) {
            if (candidate.isGroup || candidate.avatarTemplate.isBlank()) continue
            val url = DiscourseAvatars.url(candidate.avatarTemplate, baseUrl) ?: continue
            val user = DiscourseAvatars.user(
                candidate.username,
                candidate.displayName,
                candidate.avatarTemplate,
                baseUrl,
            )
            if (DiscourseAvatars.isCached(user)) continue
            avatarJobs += scope.launch {
                DiscourseAvatars.cache(client, url, user)
                if (!destroyed) panel.refreshRows()
            }
        }
    }

    companion object {
        /** Upstream hosts the panel in a bottom-aligned band of this height (ChatActivity). */
        @JvmField
        val MENTION_HOST_HEIGHT_DP = 110

        /**
         * The tallest this client lets the mention host grow. Upstream lets it reach the whole
         * content height, which lets a long candidate list paint its band over everything; capping
         * it keeps the posts above the candidates visible while still holding a useful band of rows.
         */
        @JvmField
        val MENTION_BAND_HEIGHT_DP = 320

        /**
         * The one entry point a screen needs: it is handed the composer and the host the panel
         * rides in, and answers with the controller it must forward lifecycle to - or null when the
         * composer has no field. [topicId] is the topic being answered; a new topic passes null,
         * which omits `topic_id` from the request rather than sending a made-up id.
         */
        @JvmStatic
        @JvmOverloads
        fun install(
            composer: ChatActivityEnterView,
            host: ViewGroup,
            fragment: BaseFragment,
            resourcesProvider: Theme.ResourcesProvider?,
            baseUrl: String,
            topicId: Long?,
            repository: TopicMentionRepository,
            scope: CoroutineScope,
            imageClient: OkHttpClient? = null,
        ): DiscourseMentions? {
            val field = composer.editField ?: return null
            val panel = DiscourseMentionsPanel(field.context, fragment, resourcesProvider)
            val params = if (host is FrameLayout) {
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(MENTION_HOST_HEIGHT_DP.toFloat()),
                    Gravity.BOTTOM,
                )
            } else {
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(MENTION_HOST_HEIGHT_DP.toFloat()),
                )
            }
            host.addView(panel, params)
            return DiscourseMentions(field, panel, repository, scope, topicId, baseUrl, imageClient)
        }
    }
}
