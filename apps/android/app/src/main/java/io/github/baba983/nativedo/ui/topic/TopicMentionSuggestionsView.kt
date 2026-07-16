/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's
 * TMessagesProj/src/main/java/org/telegram/ui/Components/MentionsContainerView.java.
 * The container keeps Telegram's list measurement, recycling, and spring reveal
 * while binding Discourse topic participants and search users.
 */
package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.data.TopicMentionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable
import org.telegram.ui.Adapters.PaddedListAdapter

internal data class TopicMentionToken(
    val start: Int,
    val cursor: Int,
    val end: Int,
    val query: String,
)

internal data class TopicMentionReplacement(
    val text: String,
    val cursor: Int,
)

internal fun topicMentionTokenAt(text: CharSequence, cursor: Int): TopicMentionToken? {
    if (cursor <= 0 || cursor > text.length) return null
    var start = cursor - 1
    while (start >= 0) {
        val ch = text[start]
        if (ch == '@') break
        if (!topicMentionUsernameCharacter(ch)) return null
        start--
    }
    if (start < 0 || text[start] != '@') return null
    if (start > 0 && !topicMentionBoundaryCharacter(text[start - 1])) return null
    var end = cursor
    while (end < text.length && topicMentionUsernameCharacter(text[end])) {
        end++
    }
    return TopicMentionToken(
        start = start,
        cursor = cursor,
        end = end,
        query = text.subSequence(start + 1, cursor).toString(),
    )
}

internal fun topicMentionReplacement(
    text: CharSequence,
    token: TopicMentionToken,
    username: String,
): TopicMentionReplacement? {
    if (token.start !in 0..text.length || token.end !in token.start..text.length) return null
    if (token.cursor !in (token.start + 1)..token.end) return null
    if (text.subSequence(token.start, token.cursor).toString() != "@${token.query}") return null
    if (text.subSequence(token.cursor, token.end).any { !topicMentionUsernameCharacter(it) }) return null
    val needsSpace = token.end >= text.length || !text[token.end].isWhitespace()
    val insertion = "@${username.trim().removePrefix("@")}"
        .let { value -> if (needsSpace) "$value " else value }
    val replaced = buildString(text.length - (token.end - token.start) + insertion.length) {
        append(text, 0, token.start)
        append(insertion)
        append(text, token.end, text.length)
    }
    return TopicMentionReplacement(replaced, token.start + insertion.length)
}

internal fun topicMentionParticipants(posts: List<Post>): List<MentionCandidate> {
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

internal fun topicMentionParticipants(
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
        topicMentionParticipants(posts).forEach { participant ->
            if (seen.add(participant.username.lowercase())) {
                add(participant)
            }
        }
    }
}

internal fun topicMentionMergedUsers(
    localUsers: List<MentionCandidate>,
    remoteUsers: List<MentionCandidate>,
    query: String,
): List<MentionCandidate> {
    val normalizedQuery = query.trim().lowercase()
    val localMatches = localUsers.filter { user -> topicMentionUserMatches(user, normalizedQuery) }
    val seen = localMatches.mapTo(HashSet()) { it.username.lowercase() }
    val remoteMatches = remoteUsers.filter { user ->
        topicMentionUserMatches(user, normalizedQuery) && seen.add(user.username.lowercase())
    }
    return (localMatches + remoteMatches).take(TOPIC_MENTION_MAX_RESULTS)
}

private fun topicMentionUsernameCharacter(ch: Char): Boolean {
    return ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-' || ch == '.'
}

private fun topicMentionBoundaryCharacter(ch: Char): Boolean {
    return ch.isWhitespace() || ch == '(' || ch == '[' || ch == '{' || ch == '"' || ch == '\''
}

private fun topicMentionUserMatches(user: MentionCandidate, query: String): Boolean {
    if (query.isEmpty()) return true
    return user.username.lowercase().startsWith(query) ||
        user.displayName.lowercase().splitToSequence(' ', '-', '_').any { it.startsWith(query) }
}

internal class TopicMentionSuggestionsView(
    context: Context,
    private val baseUrl: String,
    private val topicId: Long,
    private val scope: CoroutineScope,
    private val repository: TopicMentionRepository,
    private val onUserSelected: (TopicMentionToken, MentionCandidate) -> Unit,
) : FrameLayout(context) {
    private val linearLayoutManager = object : LinearLayoutManager(context) {
        override fun supportsPredictiveItemAnimations(): Boolean = false
    }
    private val adapter = MentionAdapter()
    private val paddedAdapter = PaddedListAdapter(adapter)
    private val listView = MentionsListView(context)
    private val clipPath = Path()
    private val clipBounds = RectF()
    private var backgroundDrawable: BlurredBackgroundDrawable? = null
    private var localUsers: List<MentionCandidate> = emptyList()
    private var currentToken: TopicMentionToken? = null
    private var queryJob: Job? = null
    private var queryGeneration = 0
    private var translationSpring: SpringAnimation? = null
    private var shown = false
    private var hideT = 1f
    private var listViewHiding = false
    private var scrollToFirst = false
    private var selectedPosition = 0
    private var listViewPadding = topicMentionListPaddingPx()
    private var containerTop = 0f
    private var containerBottom = 0f
    private var scrollRangeUpdateTries = 0
    private val updateVisibilityRunnable = Runnable { updateListViewTranslation(!shown, true) }

    init {
        visibility = View.GONE
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        listView.layoutManager = linearLayoutManager
        listView.itemAnimator = TopicMentionItemAnimator()
        listView.clipToPadding = false
        listView.adapter = paddedAdapter
        listView.translationY = AndroidUtilities.dp(6f).toFloat()
        listView.setOnItemClickListener { _, position ->
            if (position == 0) return@setOnItemClickListener
            val token = currentToken ?: return@setOnItemClickListener
            val user = adapter.userAt(position - 1) ?: return@setOnItemClickListener
            onUserSelected(token, user)
        }
        addView(listView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setParticipants(participants: List<MentionCandidate>, posts: List<Post>) {
        val nextUsers = topicMentionParticipants(participants, posts)
        if (nextUsers == localUsers) return
        localUsers = nextUsers
        currentToken?.let(::updateQuery)
    }

    fun updateQuery(token: TopicMentionToken?) {
        queryGeneration++
        queryJob?.cancel()
        queryJob = null
        currentToken = token
        if (token == null) {
            dismiss()
            return
        }
        val localResults = topicMentionMergedUsers(localUsers, emptyList(), token.query)
        if (localResults.isEmpty() && token.query.isNotBlank()) {
            hideForPendingResults()
        } else {
            submit(localResults)
        }
        val generation = queryGeneration
        queryJob = scope.launch {
            delay(TOPIC_MENTION_REMOTE_DELAY_MS)
            val remote = repository.search(token.query, topicId).getOrNull()?.candidates ?: return@launch
            if (generation != queryGeneration || currentToken != token) return@launch
            submit(topicMentionMergedUsers(remote, emptyList(), token.query))
        }
    }

    fun isOpen(): Boolean = shown

    fun setBackgroundDrawable(drawable: BlurredBackgroundDrawable) {
        backgroundDrawable = drawable.apply {
            setRadius(AndroidUtilities.dp(22f).toFloat())
            setPadding(AndroidUtilities.dp(5f))
        }
        checkBackgroundBounds()
    }

    fun handleKey(keyCode: Int, action: Int): Boolean {
        if (!shown || adapter.itemCount == 0) return false
        val supported = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_TAB
        if (!supported) return false
        if (action != KeyEvent.ACTION_DOWN) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> selectedPosition = (selectedPosition - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedPosition = (selectedPosition + 1).coerceAtMost(adapter.itemCount - 1)
            else -> {
                val token = currentToken ?: return true
                val user = adapter.userAt(selectedPosition) ?: return true
                onUserSelected(token, user)
                return true
            }
        }
        listView.scrollToPosition(selectedPosition + 1)
        listView.highlightRow { selectedPosition + 1 }
        return true
    }

    fun dismiss(animated: Boolean = true) {
        queryGeneration++
        queryJob?.cancel()
        queryJob = null
        currentToken = null
        updateVisibility(false, animated)
    }

    fun release() {
        AndroidUtilities.cancelRunOnUIThread(updateVisibilityRunnable)
        dismiss(animated = false)
        translationSpring?.cancel()
        translationSpring = null
        backgroundDrawable = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        backgroundDrawable?.draw(canvas)
        if (!clipPath.isEmpty) {
            canvas.save()
            canvas.clipPath(clipPath)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        checkBackgroundBounds()
    }

    private fun submit(users: List<MentionCandidate>) {
        adapter.submit(users)
        if (users.isEmpty()) {
            updateVisibility(false, true)
            return
        }
        selectedPosition = selectedPosition.coerceIn(0, users.lastIndex)
        updateVisibility(true, true)
    }

    private fun hideForPendingResults() {
        updateVisibility(false, true)
    }

    private fun updateVisibility(show: Boolean, animated: Boolean) {
        if (show) {
            if (!shown) {
                scrollToFirst = true
                linearLayoutManager.scrollToPositionWithOffset(0, 100000)
                if (visibility == View.GONE) {
                    hideT = 1f
                    listView.translationY = listView.computeVerticalScrollOffset() + listViewPadding.toFloat()
                }
            }
            visibility = View.VISIBLE
        }
        shown = show
        AndroidUtilities.cancelRunOnUIThread(updateVisibilityRunnable)
        translationSpring?.cancel()
        translationSpring = null
        if (animated && isAttachedToWindow) {
            AndroidUtilities.runOnUIThread(updateVisibilityRunnable, 100L)
        } else {
            updateListViewTranslation(forceZeroHeight = !show, animated = false)
        }
    }

    private fun updateListViewTranslation(forceZeroHeight: Boolean, animated: Boolean) {
        if (listViewHiding && translationSpring?.isRunning == true && forceZeroHeight) return
        val itemHeight = if (forceZeroHeight) {
            -AndroidUtilities.dp(6f).toFloat()
        } else {
            val scrollRange = listView.computeVerticalScrollRange()
            if (scrollRange <= 0 && adapter.itemCount > 0 && scrollRangeUpdateTries < 3) {
                scrollRangeUpdateTries++
                post { updateListViewTranslation(false, animated) }
                return
            }
            scrollRangeUpdateTries = 0
            (scrollRange - paddedAdapter.padding).toFloat()
        }
        val target = topicMentionListTranslation(
            itemHeight = itemHeight,
            listViewPadding = listViewPadding,
            forceZeroHeight = forceZeroHeight,
            scrollOffset = listView.computeVerticalScrollOffset(),
        )
        animateListTo(target, forceZeroHeight, animated)
    }

    private fun animateListTo(target: Float, forceZeroHeight: Boolean, animated: Boolean) {
        translationSpring?.cancel()
        translationSpring = null
        val fromTranslation = listView.translationY
        val fromHideT = hideT
        val targetHideT = if (forceZeroHeight) 1f else 0f
        if (!animated || !isAttachedToWindow || fromTranslation == target) {
            hideT = targetHideT
            listView.translationY = target
            if (forceZeroHeight) visibility = View.GONE
            return
        }
        listViewHiding = forceZeroHeight
        val spring = SpringAnimation(FloatValueHolder(fromTranslation)).apply {
            setSpring(
                SpringForce(target)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                    .setStiffness(TOPIC_MENTION_SPRING_STIFFNESS),
            )
            addUpdateListener { _, value, _ ->
                listView.translationY = value
                val denominator = target - fromTranslation
                val progress = if (denominator == 0f) 1f else ((value - fromTranslation) / denominator).coerceIn(0f, 1f)
                hideT = fromHideT + (targetHideT - fromHideT) * progress
                checkBackgroundBounds()
            }
            addEndListener { animation, cancelled, _, _ ->
                if (translationSpring === animation) translationSpring = null
                listViewHiding = false
                if (!cancelled && forceZeroHeight && !shown) visibility = View.GONE
            }
        }
        translationSpring = spring
        spring.start()
    }

    private fun checkBackgroundBounds() {
        val paddingViewBottom = if (paddedAdapter.paddingViewAttached) paddedAdapter.paddingView.bottom else 0
        val top = topicMentionContainerTop(
            paddingViewBottom = paddingViewBottom,
            listTranslationY = listView.translationY,
            hideT = hideT,
            hostHeight = measuredHeight,
        )
        containerTop = top
        containerBottom = measuredHeight.toFloat()
        val drawable = backgroundDrawable ?: return
        drawable.setBounds(
            0,
            containerTop.toInt() - AndroidUtilities.dp(5f),
            measuredWidth,
            containerBottom.toInt() + AndroidUtilities.dp(5f),
        )
        clipPath.rewind()
        clipBounds.set(drawable.paddedBounds)
        clipPath.addRoundRect(
            clipBounds,
            AndroidUtilities.dp(22f).toFloat(),
            AndroidUtilities.dp(22f).toFloat(),
            Path.Direction.CW,
        )
        clipPath.close()
        invalidate()
    }

    private inner class MentionsListView(context: Context) : RecyclerListView(context) {
        private var lastWidth = 0
        private var lastHeight = 0
        private var ignoreLayout = false

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            val height = b - t
            val position = linearLayoutManager.findLastVisibleItemPosition()
            val child = linearLayoutManager.findViewByPosition(position)
            val offset = child?.top?.minus(lastHeight - height) ?: 0
            super.onLayout(changed, l, t, r, b)
            if (scrollToFirst) {
                ignoreLayout = true
                linearLayoutManager.scrollToPositionWithOffset(0, 100000)
                super.onLayout(false, l, t, r, b)
                ignoreLayout = false
                scrollToFirst = false
            } else if (position != -1 && width == lastWidth && height != lastHeight) {
                ignoreLayout = true
                linearLayoutManager.scrollToPositionWithOffset(position, offset)
                super.onLayout(false, l, t, r, b)
                ignoreLayout = false
            }
            lastHeight = height
            lastWidth = width
            post { if (shown) updateListViewTranslation(false, true) }
        }

        override fun setTranslationY(translationY: Float) {
            super.setTranslationY(translationY)
            this@TopicMentionSuggestionsView.invalidate()
            checkBackgroundBounds()
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val height = MeasureSpec.getSize(heightSpec)
            paddedAdapter.setPadding(height)
            listViewPadding = topicMentionListPaddingPx()
            super.onMeasure(
                widthSpec,
                MeasureSpec.makeMeasureSpec(height + listViewPadding, MeasureSpec.EXACTLY),
            )
        }

        override fun onScrolled(dx: Int, dy: Int) {
            super.onScrolled(dx, dy)
            this@TopicMentionSuggestionsView.invalidate()
            checkBackgroundBounds()
        }

        override fun requestLayout() {
            if (!ignoreLayout) super.requestLayout()
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (paddedAdapter.paddingViewAttached && event.y < paddedAdapter.paddingView.bottom) return false
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (paddedAdapter.paddingViewAttached && event.y < paddedAdapter.paddingView.bottom) return false
            return super.onTouchEvent(event)
        }
    }

    private inner class MentionAdapter : RecyclerListView.SelectionAdapter() {
        private var users: List<MentionCandidate> = emptyList()

        fun submit(nextUsers: List<MentionCandidate>) {
            users = nextUsers
            notifyDataSetChanged()
        }

        fun userAt(position: Int): MentionCandidate? = users.getOrNull(position)

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean = true
        override fun getItemCount(): Int = users.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            RecyclerListView.Holder(TopicMentionCell(parent.context))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val user = users[position]
            val avatarSource = user.animatedAvatarUrl.ifBlank { user.avatarTemplate }
            val avatarUrl = resolveAvatarUrl(baseUrl, avatarSource, size = 96)
            (holder.itemView as TopicMentionCell).bind(
                user.displayName,
                user.username,
                avatarUrl,
                avatarImageFilter("", 50),
                user.animatedAvatarUrl.isNotBlank() || avatarSource.endsWith(".gif", ignoreCase = true),
                user,
            )
        }
    }
}

internal const val TOPIC_MENTION_HOST_HEIGHT_DP = 110
internal fun topicMentionListPaddingPx(): Int = minOf(
    AndroidUtilities.dp(36f * 3.5f),
    (AndroidUtilities.displaySize.y * 0.22f).toInt(),
)
internal fun topicMentionListTranslation(
    itemHeight: Float,
    listViewPadding: Int,
    forceZeroHeight: Boolean,
    scrollOffset: Int,
): Float {
    var target = -listViewPadding + maxOf(0f, listViewPadding - itemHeight)
    if (forceZeroHeight) target += scrollOffset
    return target
}

internal fun topicMentionContainerTop(
    paddingViewBottom: Int,
    listTranslationY: Float,
    hideT: Float,
    hostHeight: Int,
): Float {
    return maxOf(0f, paddingViewBottom + listTranslationY)
        .coerceAtLeast(hideT.coerceIn(0f, 1f) * hostHeight)
}
private const val TOPIC_MENTION_MAX_RESULTS = 20
private const val TOPIC_MENTION_REMOTE_DELAY_MS = 200L
private const val TOPIC_MENTION_SPRING_STIFFNESS = 550f
