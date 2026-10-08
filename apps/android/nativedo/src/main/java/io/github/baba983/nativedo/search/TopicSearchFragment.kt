package io.github.baba983.nativedo.search

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.topic.data.TopicSearchRepository
import io.github.baba983.nativedo.topic.DiscourseRows
import io.github.baba983.nativedo.topic.TopicFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

/** A post search locked to one Discourse topic, using upstream search input and result cells. */
class TopicSearchFragment(private val topicId: Long, private val topicTitle: String?) : BaseFragment() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var container: AppContainer
    private lateinit var list: RecyclerListView
    private lateinit var state: EmptyTextProgressView
    private val hits = ArrayList<TopicSummary>()
    private var query = ""
    private var job: Job? = null
    private var revision = 0
    private var nextPage = 0
    private var loading = false
    private var hasMore = false
    private var failed = false
    private var requestedPage = 0
    private val adapter = object : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder) = !loading
        override fun getItemCount() = hits.size + if (hasMore || (failed && hits.isNotEmpty())) 1 else 0
        override fun getItemViewType(position: Int) = if (position < hits.size) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            val view = if (type == 0) DialogCell(null, parent.context, false, false, 0, null)
                else TextSettingsCell(parent.context)
            view.layoutParams = RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(if (type == 0) 72f else 50f))
            return RecyclerListView.Holder(view)
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position < hits.size) {
                (holder.itemView as DialogCell).setDialog(DiscourseRows.topicRow(hits[position], false))
            } else {
                (holder.itemView as TextSettingsCell).setText(
                    if (loading) "正在加载…" else if (failed) "加载失败，点击重试" else "加载更多", false)
            }
        }
    }

    override fun createView(context: Context): View {
        container = NativeDo.container(context)
        actionBar.setTitle(topicTitle ?: "话题内搜索")
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) { if (id == -1) finishFragment() }
        })
        val body = FrameLayout(context)
        body.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        list = RecyclerListView(context)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.setOnItemClickListener { _, position ->
            if (!loading) {
                if (position < hits.size) {
                    val hit = hits[position]
                    presentFragment(TopicFragment.forFloor(topicId, hit.title, hit.targetPostNumber!!))
                } else request(if (failed) requestedPage else nextPage)
            }
        }
        body.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        state = EmptyTextProgressView(context)
        state.setText("输入关键词，搜索当前话题")
        state.showTextView()
        state.setOnClickListener { if (failed && !loading) request(requestedPage) }
        body.addView(state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        val search = actionBar.createMenu().addItem(10, org.telegram.messenger.R.drawable.msg_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onTextChanged(editText: android.widget.EditText) { changeQuery(editText.text.toString()) }
                override fun canCollapseSearch(): Boolean { finishFragment(); return false }
            })
        search.setSearchFieldHint("搜索当前话题")
        search.openSearch(false)
        fragmentView = body
        return body
    }

    private fun changeQuery(text: String) {
        job?.cancel()
        revision++
        query = text.trim()
        hits.clear()
        nextPage = 0
        hasMore = false
        loading = false
        failed = false
        adapter.notifyDataSetChanged()
        state.visibility = View.VISIBLE
        if (query.isEmpty() || Regex("(?i)\\btopic\\s*:").containsMatchIn(query)) {
            state.setText(if (query.isEmpty()) "输入关键词，搜索当前话题" else "搜索范围已固定为当前话题")
            state.showTextView()
        } else request(0, true)
    }

    private fun request(page: Int, debounce: Boolean = false) {
        if (loading || query.isBlank()) return
        requestedPage = page
        failed = false
        loading = true
        if (hits.isEmpty()) { state.visibility = View.VISIBLE; state.showProgress() }
        adapter.notifyDataSetChanged()
        val expectedRevision = revision
        val words = query
        job = scope.launch {
            if (debounce) delay(300)
            val result = TopicSearchRepository(container.discourseApiClient)
                .search("$words topic:$topicId", page, "posts")
            if (expectedRevision != revision) return@launch
            loading = false
            result.onSuccess { response ->
                val ids = hits.map { it.targetPostNumber }.toHashSet()
                hits.addAll(response.posts.filter { it.id == topicId && (it.targetPostNumber ?: 0) > 0 }
                    .filter { ids.add(it.targetPostNumber) })
                hasMore = response.hasMore
                nextPage = page + 1
                if (hits.isEmpty()) {
                    state.setText("当前话题中没有找到结果")
                    state.showTextView()
                } else state.visibility = View.GONE
            }.onFailure {
                failed = true
                if (hits.isEmpty()) {
                    state.setText("加载失败，点击重试")
                    state.showTextView()
                }
            }
            adapter.notifyDataSetChanged()
        }
    }

    override fun onFragmentDestroy() {
        revision++
        scope.cancel()
        super.onFragmentDestroy()
    }
}
