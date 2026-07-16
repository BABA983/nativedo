/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's org/telegram/ui/Components/SearchViewPager.java.
 * It keeps independently scrolling result pages on ViewPagerFixed while binding
 * Discourse topics, posts, and users to NativeDo adapters.
 */
package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.topic.ui.TopicSearchSnapshot
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ViewPagerFixed

internal class TopicSearchViewPager(
    context: Context,
    private val baseUrl: String,
    private val topPaddingPx: Int,
    private val onFilterSelected: (SearchFilter) -> Unit,
    private val onTopicSelected: (TopicSummary, Boolean) -> Unit,
    private val onUserSelected: (String) -> Unit,
    private val onLoadMore: () -> Unit,
    private val onRetry: () -> Unit,
) : ViewPagerFixed(context) {
    private val pages = linkedMapOf<SearchFilter, SearchPage>()
    private var snapshot = TopicSearchSnapshot()
    private var categories: Map<Int, CategorySummary> = emptyMap()
    private var searchField: View? = null

    init {
        setAdapter(object : Adapter() {
            override fun getItemCount(): Int = SearchFilter.entries.size

            override fun getItemViewType(position: Int): Int = position

            override fun createView(viewType: Int): View = SearchPage(context, SearchFilter.entries[viewType])

            override fun bindView(view: View, position: Int, viewType: Int) {
                val filter = SearchFilter.entries[position]
                val page = view as SearchPage
                page.bindFilter(filter)
                pages[filter] = page
                page.submit(snapshot, categories)
            }
        })
    }

    override fun onTabPageSelected(position: Int, forward: Boolean) {
        onFilterSelected(SearchFilter.entries[position])
    }

    override fun onScrollEnd() {
        onFilterSelected(SearchFilter.entries[currentPosition])
    }

    fun setSearchField(view: View?) {
        searchField = view
    }

    fun selectFilter(filter: SearchFilter, animated: Boolean): Boolean {
        val position = filter.ordinal
        if (!animated) {
            if (currentPosition == position) return false
            setPosition(position)
            onFilterSelected(filter)
            return true
        }
        return scrollToPosition(position)
    }

    fun submit(snapshot: TopicSearchSnapshot, categories: Map<Int, CategorySummary>) {
        this.snapshot = snapshot
        this.categories = categories
        pages.values.forEach { page -> page.submit(snapshot, categories) }
    }

    fun currentListView(): RecyclerListView? = pages[SearchFilter.entries[currentPosition]]?.listView

    fun scrollCurrentToTop() {
        currentListView()?.smoothScrollToPosition(0)
    }

    fun setScrollBarsEnabled(enabled: Boolean) {
        pages.values.forEach { page -> page.listView.isVerticalScrollBarEnabled = enabled }
    }

    fun updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        pages.values.forEach(SearchPage::updateColors)
    }

    private inner class SearchPage(
        context: Context,
        initialFilter: SearchFilter,
    ) : FrameLayout(context) {
        val listView = RecyclerListView(context)
        private var filter = initialFilter
        private val topicAdapter = TopicListAdapter(context, baseUrl)
        private val usersAdapter = SearchUsersAdapter(context, baseUrl)

        init {
            listView.layoutManager = LinearLayoutManager(context)
            listView.itemAnimator = DialogsItemAnimator(listView)
            listView.setPadding(0, topPaddingPx, 0, 0)
            listView.clipToPadding = false
            listView.setOnItemClickListener { _, position ->
                if (filter == SearchFilter.Users) {
                    usersAdapter.userAt(position)?.username?.let(onUserSelected)
                } else {
                    topicAdapter.getTopicAt(position)?.let { topic ->
                        onTopicSelected(topic, filter == SearchFilter.Posts)
                    }
                }
            }
            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        searchField?.let(AndroidUtilities::hideKeyboard)
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || filter == SearchFilter.Users) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (manager.findLastVisibleItemPosition() >= (recyclerView.adapter?.itemCount ?: 0) - 3) {
                        onLoadMore()
                    }
                }
            })
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            bindFilter(initialFilter)
        }

        fun bindFilter(filter: SearchFilter) {
            this.filter = filter
            listView.adapter = if (filter == SearchFilter.Users) usersAdapter else topicAdapter
        }

        fun submit(snapshot: TopicSearchSnapshot, categories: Map<Int, CategorySummary>) {
            when (filter) {
                SearchFilter.Topics,
                SearchFilter.Posts,
                -> {
                    val rows = if (filter == SearchFilter.Topics) snapshot.result.topics else snapshot.result.posts
                    topicAdapter.setSearchPresentation(snapshot.query, filter == SearchFilter.Posts)
                    val emptyTitle = if (filter == SearchFilter.Topics) {
                        R.string.search_topics_empty_title
                    } else {
                        R.string.search_posts_empty_title
                    }
                    val showEmpty = snapshot.query.isNotBlank() &&
                        !snapshot.isInitialLoading &&
                        snapshot.initialError == null
                    topicAdapter.submit(
                        rows,
                        categories,
                        snapshot.isLoadingMore,
                        snapshot.isInitialLoading,
                        if (showEmpty) context.getString(emptyTitle) else null,
                        if (showEmpty) context.getString(R.string.search_topics_empty_message) else null,
                        snapshot.initialError?.let { context.getString(R.string.search_topics_error_title) },
                        snapshot.initialError,
                        snapshot.loadMoreError,
                        snapshot.initialError?.let { Runnable(onRetry) },
                        snapshot.loadMoreError?.let { Runnable(onRetry) },
                    )
                }
                SearchFilter.Users -> usersAdapter.submit(
                    snapshot.result.users,
                    loading = snapshot.isInitialLoading,
                    stateMessage = when {
                        snapshot.initialError != null -> listOf(
                            context.getString(R.string.search_topics_error_title),
                            snapshot.initialError,
                        ).joinToString("\n")
                        !snapshot.isInitialLoading && snapshot.result.users.isEmpty() && snapshot.query.isNotBlank() -> listOf(
                            context.getString(R.string.search_users_empty_title),
                            context.getString(R.string.search_topics_empty_message),
                        ).joinToString("\n")
                        else -> null
                    },
                    retry = snapshot.initialError?.let { onRetry },
                )
            }
        }

        fun updateColors() {
            listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            for (index in 0 until listView.childCount) {
                (listView.getChildAt(index) as? Theme.Colorable)?.updateColors()
            }
            listView.invalidate()
        }
    }
}
