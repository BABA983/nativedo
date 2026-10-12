// SPDX-License-Identifier: GPL-2.0-or-later
// Search panel wiring adapted from Telegram's ChatActivity; widgets remain upstream.
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package io.github.baba983.nativedo.search

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.GlassPanelHost
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.topic.data.TopicSearchRepository
import io.github.baba983.nativedo.topic.DiscourseRows
import io.github.baba983.nativedo.topic.PostListView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.FragmentSearchField
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Components.chat.ChatActivitySearchContainer
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout
import java.util.WeakHashMap

/** One search session, hosted by the topic rather than a second navigation screen. */
class TopicSearch(
    context: Context, private val container: AppContainer, private val topicId: Long,
    private val root: FrameLayout, private val posts: PostListView,
    private val glass: GlassPanelHost, private val resources: Theme.ResourcesProvider?,
    top: Int, bottom: Int, private val host: Host
) {
    interface Host {
        fun queryChanged()
        fun navigate(floor: Int, forward: Boolean)
        fun close()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hits = ArrayList<TopicSummary>()
    private var query = ""
    private var job: Job? = null
    private var revision = 0
    private var nextPage = 0
    private var loading = false
    private var hasMore = false
    private var failed = false
    private var requestedPage = 0
    private var selected = -1
    private var advanceAfterPage = false
    private var listMode = false
    private val originals = WeakHashMap<RichMessageLayout.Text, LinkPath?>()
    private val highlighted = WeakHashMap<RichMessageLayout.Text, String>()
    private val plainHighlights = WeakHashMap<ChatMessageCell, Pair<MessageObject, String>>()
    private val results = ChatActivitySearchContainer(context)
    private val list = RecyclerListView(context)
    private val state = EmptyTextProgressView(context)
    private val field = FragmentSearchField(context, resources)
    private val panel = FrameLayout(context)
    private val count = AnimatedTextView(context, true, true, true)
    private val toggle = AnimatedTextView(context, true, false, true)
    private val controls = ChatActivitySideControlsButtonsLayout(context, resources,
        BlurredBackgroundProviderImpl.bottomPanelChatActivity(resources), glass.factory)
    private val adapter = object : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder) = !loading
        override fun getItemCount() = hits.size + if (hasMore || (failed && hits.isNotEmpty())) 1 else 0
        override fun getItemViewType(position: Int) = if (position < hits.size) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            val view = if (type == 0) DialogCell(null, parent.context, false, false, 0, resources)
                else TextSettingsCell(parent.context)
            view.layoutParams = RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(if (type == 0) 72f else 50f))
            return RecyclerListView.Holder(view)
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position < hits.size) {
                val hit = hits[position]
                val author = DiscourseRows.topicAuthor(hit.copy(authorAvatarTemplate = ""), container.baseUrl)
                MessagesController.getInstance(0).putUser(author, true)
                val row = DiscourseRows.topicRow(hit, false)
                val message = TLRPC.TL_message().apply {
                    id = hit.targetPostId?.toInt() ?: hit.targetPostNumber!!
                    date = row.date
                    this.message = hit.excerpt
                    from_id = TLRPC.TL_peerUser().apply { user_id = author.id }
                    peer_id = TLRPC.TL_peerUser().apply { user_id = author.id }
                }
                val objectRow = DiscourseRows.messageObject(message, hashMapOf(author.id to author))
                objectRow.setQuery(query)
                val cell = holder.itemView as DialogCell
                cell.setTitleOverride("${hit.authorUsername} · 第 ${hit.targetPostNumber} 楼")
                cell.setDialog(author.id, objectRow, row.date, false, false)
                cell.useSeparator = true
                cell.contentDescription = "第 ${hit.targetPostNumber} 楼，${hit.excerpt}"
            } else {
                (holder.itemView as TextSettingsCell).setText(
                    if (loading) "正在加载…" else if (failed) "加载失败，点击重试" else "加载更多", false)
            }
        }
    }

    init {
        results.setup(glass.wallpaperFactory, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resources))
        results.setFade(top, AndroidUtilities.dp(64f) + bottom)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(0, top, 0, AndroidUtilities.dp(64f) + bottom)
        list.setOnItemClickListener { _, position ->
            if (!loading) {
                if (position < hits.size) {
                    val forward = position >= selected
                    selected = position
                    setListMode(false, forward)
                } else request(if (failed) requestedPage else nextPage)
            }
        }
        results.addView(list, LayoutHelper.createFrameMatchParent())
        state.setText("输入关键词，搜索当前话题")
        state.showTextView()
        state.setOnClickListener { if (failed && !loading) request(requestedPage) }
        results.addView(state, LayoutHelper.createFrameMatchParent())
        root.addView(results, LayoutHelper.createFrameMatchParent())
        field.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))
        field.setupBlurredBackground(glass.createPanel(field, BlurredBackgroundProviderImpl.topPanel(resources)))
        field.setHintText("", false)
        field.editText.setHint("搜索当前话题")
        field.editText.contentDescription = "搜索当前话题"
        field.setCloseButtonVisible(true)
        field.setCloseButtonOnClickListener { host.close() }
        field.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changeQuery(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP,
            7f, (top - AndroidUtilities.dp(52f)) / AndroidUtilities.density, 7f, 0f))
        panel.background = glass.createPanel(panel, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resources))
        count.setTextSize(AndroidUtilities.dp(14f).toFloat())
        count.setTypeface(AndroidUtilities.bold())
        count.setGravity(Gravity.LEFT)
        count.setAnimationProperties(.25f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT)
        panel.addView(count, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30f, Gravity.CENTER_VERTICAL, 16f, 0f, 112f, 0f))
        toggle.setTextSize(AndroidUtilities.dp(15f).toFloat())
        toggle.setTypeface(AndroidUtilities.bold())
        toggle.setGravity(Gravity.RIGHT)
        toggle.setPadding(AndroidUtilities.dp(15f), 0, AndroidUtilities.dp(15f), 0)
        toggle.setOnClickListener { setListMode(!listMode) }
        panel.addView(toggle, LayoutHelper.createFrame(112, LayoutHelper.MATCH_PARENT, Gravity.RIGHT))
        root.addView(panel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 7f, 0f, 7f,
            bottom / AndroidUtilities.density + 7f))
        controls.setGravity(Gravity.RIGHT or Gravity.BOTTOM)
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN, true, false)
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, true, false)
        controls.getChildAt(0).contentDescription = "下一条搜索结果"
        controls.getChildAt(1).contentDescription = "上一条搜索结果"
        controls.setOnClickListener { id, _ ->
            if (id == ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN) next() else if (selected > 0) {
                selected--; navigate(false)
            }
        }
        root.addView(controls, LayoutHelper.createFrame(52, 116f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 7f,
            bottom / AndroidUtilities.density + 62f))
        posts.setSearchHighlighter { highlight(it) }
        updateColors()
        updateUi()
        field.editText.requestFocus()
        AndroidUtilities.showKeyboard(field.editText)
    }

    private fun setListMode(value: Boolean, forward: Boolean = true) {
        listMode = value
        AndroidUtilities.hideKeyboard(field.editText)
        if (!value && selected >= 0) navigate(forward) else updateUi()
        val entering = if (listMode) results else posts
        entering.animate().cancel()
        entering.alpha = 0f
        entering.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
    }

    private fun changeQuery(text: String) {
        job?.cancel(); revision++
        host.queryChanged()
        query = text.trim()
        hits.clear(); selected = -1; nextPage = 0
        hasMore = false; loading = false; failed = false; advanceAfterPage = false
        adapter.notifyDataSetChanged()
        posts.invalidateViews()
        state.visibility = View.VISIBLE
        if (query.isEmpty() || Regex("(?i)\\btopic\\s*:").containsMatchIn(query)) {
            state.setText(if (query.isEmpty()) "输入关键词，搜索当前话题" else "搜索范围已固定为当前话题")
            state.showTextView(); updateUi()
        } else request(0, true)
    }

    private fun next() {
        if (loading) return
        if (selected + 1 < hits.size) { selected++; navigate(true) }
        else if (hasMore || failed) {
            advanceAfterPage = true
            request(if (failed) requestedPage else nextPage)
        }
    }

    private fun navigate(forward: Boolean) {
        AndroidUtilities.hideKeyboard(field.editText)
        host.navigate(hits[selected].targetPostNumber!!, forward)
        updateUi()
    }

    private fun request(page: Int, debounce: Boolean = false) {
        if (loading || query.isBlank()) return
        requestedPage = page; failed = false; loading = true
        if (hits.isEmpty()) { state.visibility = View.VISIBLE; state.showProgress() }
        adapter.notifyDataSetChanged(); updateUi()
        val expectedRevision = revision
        val words = query
        job = scope.launch {
            if (debounce) delay(300)
            val result = TopicSearchRepository(container.discourseApiClient).search("$words topic:$topicId", page, "posts")
            if (expectedRevision != revision) return@launch
            loading = false
            result.onSuccess { response ->
                val ids = hits.map { it.targetPostNumber }.toHashSet()
                hits.addAll(response.posts.filter { it.id == topicId && (it.targetPostNumber ?: 0) > 0 }
                    .filter { ids.add(it.targetPostNumber) })
                hasMore = response.hasMore; nextPage = page + 1
                if (hits.isEmpty()) { state.setText("当前话题中没有找到结果"); state.showTextView() }
                else {
                    state.visibility = View.GONE
                    if (selected < 0) {
                        selected = 0
                        if (!listMode) navigate(true)
                    } else if (advanceAfterPage && selected + 1 < hits.size) { selected++; navigate(true) }
                }
                advanceAfterPage = false
            }.onFailure {
                failed = true
                if (hits.isEmpty()) { state.setText("加载失败，点击重试"); state.showTextView() }
            }
            adapter.notifyDataSetChanged(); updateUi()
        }
    }

    private fun updateUi() {
        results.visibility = if (listMode) View.VISIBLE else View.GONE
        posts.visibility = if (listMode) View.INVISIBLE else View.VISIBLE
        glass.setRecordedContent(if (listMode) list else posts)
        controls.visibility = if (listMode) View.GONE else View.VISIBLE
        val text = when {
            loading -> "正在搜索…"
            failed -> "加载失败，点击重试"
            hits.isEmpty() -> "没有结果"
            hasMore -> "第 ${selected + 1} 条，已加载 ${hits.size} 条"
            else -> "第 ${selected + 1} 条，共 ${hits.size} 条"
        }
        count.setText(text, true)
        count.contentDescription = text
        count.setOnClickListener { if (failed && !loading) request(requestedPage) }
        val modeText = if (listMode) "显示为消息" else "显示为列表"
        toggle.setText(modeText, true); toggle.contentDescription = modeText
        controls.setButtonEnabled(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, !loading && selected > 0, true)
        controls.setButtonEnabled(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN,
            !loading && (selected + 1 < hits.size || hasMore || failed), true)
        controls.setButtonLoading(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN, loading, true)
    }

    fun updateColors() {
        field.updateColors()
        list.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resources))
        count.setTextColor(Theme.getColor(Theme.key_chat_searchPanelText, resources))
        toggle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resources))
        controls.updateColors()
        adapter.notifyDataSetChanged()
    }

    fun updateInsets(top: Int, bottom: Int) {
        results.setFade(top, AndroidUtilities.dp(64f) + bottom)
        list.setPadding(0, top, 0, AndroidUtilities.dp(64f) + bottom)
        val fieldParams = field.layoutParams as FrameLayout.LayoutParams
        val fieldTop = top - AndroidUtilities.dp(52f)
        if (fieldParams.topMargin != fieldTop) {
            fieldParams.topMargin = fieldTop
            field.layoutParams = fieldParams
        }
        for ((view, gap) in arrayOf(panel to 7f, controls to 62f)) {
            val params = view.layoutParams as FrameLayout.LayoutParams
            val margin = bottom + AndroidUtilities.dp(gap)
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin
                view.layoutParams = params
            }
        }
    }

    /** Upstream's mark paths paint behind glyphs, including Chinese substrings inside rich blocks. */
    private fun highlight(cell: ChatMessageCell) {
        val message = cell.messageObject ?: return
        val rich = message.richLayout
        if (rich == null) {
            val previous = plainHighlights[cell]
            if (previous?.first !== message || previous.second != query) {
                cell.setHighlightedText(query)
                plainHighlights[cell] = message to query
            }
            return
        }
        for (block in rich.textBlocks) {
            if (block !is RichMessageLayout.Text || highlighted[block] == query) continue
            if (!originals.containsKey(block)) originals[block] = block.markPath
            val original = originals[block]
            var path: LinkPath? = null
            val text = block.layout.text.toString()
            if (query.isNotEmpty()) {
                var start = text.indexOf(query, ignoreCase = true)
                while (start >= 0) {
                    if (path == null) { path = LinkPath(true); path.setAllowReset(false); original?.let { path.addPath(it) } }
                    path.setCurrentLayout(block.layout, start, 0f)
                    block.layout.getSelectionPath(start, start + query.length, path)
                    start = text.indexOf(query, start + query.length, ignoreCase = true)
                }
            }
            path?.setAllowReset(true)
            block.markPath = path ?: original
            highlighted[block] = query
        }
    }

    fun destroy() {
        revision++; scope.cancel()
        originals.forEach { (block, path) -> block.markPath = path }
        originals.clear(); highlighted.clear(); plainHighlights.clear()
        posts.setSearchHighlighter(null)
        for (index in 0 until posts.childCount) (posts.getChildAt(index) as? ChatMessageCell)?.setHighlightedText(null)
        posts.animate().cancel(); posts.alpha = 1f; posts.visibility = View.VISIBLE
        glass.setRecordedContent(posts)
        AndroidUtilities.hideKeyboard(field.editText)
        for (view in arrayOf(results, field, panel, controls)) { view.animate().cancel(); root.removeView(view) }
    }
}
