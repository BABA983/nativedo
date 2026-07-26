/*
 * This file is part of NativeDo.
 *
 * NativeDo port of Telegram's ProfileActivity shared-media content shape.
 * The pager and nested lists bind Discourse profile-feed data.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 or later.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.profile

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.topic.TopicListAdapter
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LiteMode
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.ViewPagerFixed
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor

internal class ProfileContentLayout(
    context: Context,
    private val baseUrl: String,
    private val onTopicSelected: (TopicSummary, ProfileFeedPage) -> Unit,
    private val onPageSelected: (ProfileFeedPage) -> Unit,
    private val onLoadMore: (ProfileFeedPage) -> Unit,
    private val onRetry: (ProfileFeedPage) -> Unit,
    private val onRetryLoadMore: (ProfileFeedPage) -> Unit,
) : FrameLayout(context), Theme.Colorable {

    private val tabBlurSource = BlurredBackgroundSourceColor().apply {
        setColor(Theme.getColor(Theme.key_windowBackgroundWhite))
    }

    private val tabStrip: ScrollSlidingTextTabStrip = ScrollSlidingTextTabStrip(context).apply {
        animationDuration = TAB_ANIMATION_DURATION_MS.toLong()
        setUseMinimalWidth(true)
        setColors(
            Theme.key_profile_tabSelectedLine,
            Theme.key_profile_tabSelectedText,
            Theme.key_profile_tabText,
            Theme.key_profile_tabSelector,
        )
        setDelegate(object : ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {
            override fun onPageSelected(page: Int, forward: Boolean) {
                val selected = ProfileFeedPage.fromTabId(page) ?: return
                if (pager.currentPosition != selected.ordinal) {
                    pager.scrollToPosition(selected.ordinal)
                }
            }

            override fun onPageScrolled(progress: Float) = Unit

            override fun onSamePageSelected() {
                scrollCurrentToTop()
            }
        })
        addTextTab(ProfileFeedPage.Topics.tabId, context.getString(R.string.profile_tab_topics))
        addTextTab(ProfileFeedPage.Replies.tabId, context.getString(R.string.profile_tab_replies))
        finishAddingTabs()
    }

    private val pager: ViewPagerFixed = object : ViewPagerFixed(context) {
        override fun onTabPageSelected(position: Int, forward: Boolean) {
            ProfileFeedPage.fromOrdinal(position)?.let(onPageSelected)
        }

        override fun onTabAnimationUpdate(manual: Boolean) {
            val page = ProfileFeedPage.fromOrdinal(nextPosition) ?: return
            val progress = if (manual) currentProgress else 1f - currentProgress
            tabStrip.selectTabWithId(page.tabId, progress)
        }

        override fun onScrollEnd() {
            ProfileFeedPage.fromOrdinal(currentPosition)?.let {
                tabStrip.selectTabWithId(it.tabId, 1f)
                onPageSelected(it)
            }
        }

        override fun getManualScrollDuration(): Long = TAB_ANIMATION_DURATION_MS.toLong()
    }

    private val pages = arrayOfNulls<FeedPage>(ProfileFeedPage.entries.size)
    private var selectedPage = ProfileFeedPage.Topics

    init {
        setWillNotDraw(false)
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        val tabBackgroundFactory = BlurredBackgroundDrawableViewFactory(tabBlurSource).apply {
            setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS))
        }
        val tabBackground = tabBackgroundFactory.create(
            tabStrip,
            BlurredBackgroundProviderImpl.topPanel(null),
        ).apply {
            setRadius(AndroidUtilities.dpf2(18f))
            setPadding(AndroidUtilities.dp(6.666f))
        }
        tabStrip.setPadding(0, AndroidUtilities.dp(7f), 0, AndroidUtilities.dp(7f))
        tabStrip.clipToPadding = false
        tabStrip.background = null
        tabStrip.setBlurredBackground(tabBackground)
        tabStrip.setOpen(false)
        addView(
            tabStrip,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT.toFloat(),
                50f,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                -2f,
                0f,
                -2f,
                0f,
            ),
        )
        addView(
            pager,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT.toFloat(),
                LayoutHelper.MATCH_PARENT.toFloat(),
                Gravity.TOP,
                0f,
                50f,
                0f,
                0f,
            ),
        )
        pager.setAdapter(object : ViewPagerFixed.Adapter() {
            override fun getItemCount(): Int = ProfileFeedPage.entries.size

            override fun getItemViewType(position: Int): Int = position

            override fun createView(viewType: Int): View = FeedPage(
                context,
                ProfileFeedPage.fromOrdinal(viewType) ?: ProfileFeedPage.Topics,
            )

            override fun bindView(view: View, position: Int, viewType: Int) {
                val page = view as FeedPage
                pages[page.page.ordinal] = page
                page.submit()
            }
        })
    }

    fun selectPage(page: ProfileFeedPage, animated: Boolean): Boolean {
        selectedPage = page
        if (animated) {
            return pager.scrollToPosition(page.ordinal)
        }
        pager.setPosition(page.ordinal)
        tabStrip.selectTabWithId(page.tabId, 1f)
        onPageSelected(page)
        return true
    }

    fun currentListView(): RecyclerListView? = pages.getOrNull(pager.currentPosition)?.listView

    fun selectedPage(): ProfileFeedPage = ProfileFeedPage.fromOrdinal(pager.currentPosition) ?: selectedPage

    fun isTrackingOrAnimating(): Boolean = pager.isManualScrolling || pager.currentProgress < 0.999f

    fun setPinnedToTop(pinned: Boolean) {
        tabStrip.setOpen(pinned)
    }

    fun scrollCurrentToTop() {
        currentListView()?.smoothScrollToPosition(0)
    }

    fun submit(page: ProfileFeedPage, state: ProfileFeedUiState) {
        pages.getOrNull(page.ordinal)?.submit(state)
    }

    override fun updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        tabBlurSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        tabStrip.updateColors()
        tabStrip.invalidate()
        pages.forEach { it?.updateColors() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var height = MeasureSpec.getSize(heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || height <= 0) {
            height = AndroidUtilities.displaySize.y.coerceAtLeast(AndroidUtilities.dp(420f))
        }
        val exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, exactHeight)
    }

    private inner class FeedPage(
        context: Context,
        val page: ProfileFeedPage,
    ) : FrameLayout(context) {
        val listView = RecyclerListView(context)
        private val adapter = TopicListAdapter(context, baseUrl)
        private val progressView = FlickerLoadingView(context).apply {
            setViewType(FlickerLoadingView.DIALOG_TYPE)
            setIsSingleCell(false)
            showDate(false)
        }
        private val emptyView = StickerEmptyView(
            context,
            null,
            StickerEmptyView.STICKER_TYPE_SEARCH,
        ).apply {
            visibility = View.GONE
            setAnimateLayoutChange(true)
            showProgress(false, false)
        }
        private var state = ProfileFeedUiState()

        init {
            listView.layoutManager = LinearLayoutManager(context)
            listView.itemAnimator = DialogsItemAnimator(listView)
            listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            listView.setPadding(
                0,
                0,
                0,
                AndroidUtilities.dp((MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat()),
            )
            listView.clipToPadding = false
            listView.isNestedScrollingEnabled = true
            listView.adapter = adapter
            listView.setOnItemClickListener { _, position ->
                adapter.getTopicAt(position)?.let { onTopicSelected(it, page) }
            }
            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (manager.findLastVisibleItemPosition() >= manager.itemCount - 4) {
                        onLoadMore(page)
                    }
                }
            })
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        }

        fun submit(newState: ProfileFeedUiState = state) {
            state = newState
            val emptyTitle = context.getString(
                if (page == ProfileFeedPage.Topics) R.string.profile_feed_topics_empty_title
                else R.string.profile_feed_replies_empty_title,
            )
            val emptyMessage = context.getString(
                if (page == ProfileFeedPage.Topics) R.string.profile_feed_topics_empty_message
                else R.string.profile_feed_replies_empty_message,
            )
            val showLoading = state.isLoading && state.items.isEmpty()
            val showEmpty = !showLoading && state.items.isEmpty() &&
                (state.isEmpty || state.errorMessage != null)
            if (showEmpty) {
                if (state.errorMessage != null) {
                    emptyView.title(context.getString(R.string.topic_list_error_title))
                    emptyView.subtitle(
                        state.errorMessage.orEmpty() + "\n" + context.getString(R.string.action_retry),
                    )
                    emptyView.setOnClickListener { onRetry(page) }
                    emptyView.isClickable = true
                } else {
                    emptyView.title(emptyTitle)
                    emptyView.subtitle(emptyMessage)
                    emptyView.setOnClickListener(null)
                    emptyView.isClickable = false
                }
            }
            setOverlayVisible(progressView, showLoading)
            setOverlayVisible(emptyView, showEmpty)
            adapter.submit(
                state.items,
                state.categories,
                state.isLoadingMore && state.loadMoreErrorMessage == null,
                false,
                null,
                null,
                state.errorMessage?.takeIf { state.items.isNotEmpty() }
                    ?.let { context.getString(R.string.topic_list_error_title) },
                state.errorMessage?.takeIf { state.items.isNotEmpty() },
                state.loadMoreErrorMessage?.let { context.getString(R.string.topic_list_load_more_error) },
                state.errorMessage?.takeIf { state.items.isNotEmpty() }
                    ?.let { Runnable { onRetry(page) } },
                state.loadMoreErrorMessage?.let { Runnable { onRetryLoadMore(page) } },
            )
        }

        private fun setOverlayVisible(view: View, visible: Boolean) {
            val targetVisibility = if (visible) View.VISIBLE else View.GONE
            if (view.visibility == targetVisibility) return
            view.animate().cancel()
            if (visible) {
                view.visibility = View.VISIBLE
                if (!view.isLaidOut) {
                    view.alpha = 1f
                } else {
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(STATE_CROSSFADE_MS).start()
                }
            } else if (view.visibility == View.VISIBLE) {
                view.animate()
                    .alpha(0f)
                    .setDuration(STATE_CROSSFADE_MS)
                    .withEndAction {
                        view.visibility = View.GONE
                        view.alpha = 1f
                    }
                    .start()
            }
        }

        fun updateColors() {
            listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            progressView.updateColors()
            for (index in 0 until listView.childCount) {
                (listView.getChildAt(index) as? Theme.Colorable)?.updateColors()
            }
            listView.invalidate()
        }
    }

    private companion object {
        const val TAB_ANIMATION_DURATION_MS = 320
        const val STATE_CROSSFADE_MS = 150L
    }
}

internal enum class ProfileFeedPage(val tabId: Int) {
    Topics(1),
    Replies(2),
    ;

    companion object {
        fun fromTabId(tabId: Int): ProfileFeedPage? = entries.firstOrNull { it.tabId == tabId }
        fun fromOrdinal(ordinal: Int): ProfileFeedPage? = entries.getOrNull(ordinal)
    }
}
