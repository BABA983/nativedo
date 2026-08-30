/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of org/telegram/ui/Components/SharedMediaLayout.java, bound to
 * Discourse profile-feed data.
 */
package io.github.baba983.nativedo.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.ViewPagerFixed
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode
import org.telegram.ui.Components.chat.ViewPositionWatcher

internal class ProfileContentLayout(
    context: Context,
    private val baseUrl: String,
    private val onTopicSelected: (TopicSummary, ProfileFeedPage) -> Unit,
    private val onPageSelected: (ProfileFeedPage) -> Unit,
    private val onLoadMore: (ProfileFeedPage) -> Unit,
    private val onRetry: (ProfileFeedPage) -> Unit,
    private val onRetryLoadMore: (ProfileFeedPage) -> Unit,
    private val viewportHeightProvider: (() -> Int)? = null,
    // Source and tab bounds share fragment-root coordinates.
    private val blurSourceRoot: ViewGroup? = null,
) : FrameLayout(context), Theme.Colorable {

    private val tabBlurColorSource = BlurredBackgroundSourceColor().apply {
        setColor(Theme.getColor(Theme.key_windowBackgroundGray))
    }
    private val tabBlurSource: BlurredBackgroundSource
    private val blurPositionWatcher: ViewPositionWatcher?
    private val blurPagerBounds = RectF()

    init {
        // RenderNode-backed glass is only useful once the hardware RenderNode
        // APIs are available. Keep the color source on pre-S devices so merely
        // constructing this view never asks the class verifier for an unavailable
        // platform type.
        if (blurSourceRoot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurPositionWatcher = ViewPositionWatcher(blurSourceRoot)
            tabBlurSource = BlurredBackgroundSourceRenderNode(tabBlurColorSource)
        } else {
            blurPositionWatcher = null
            tabBlurSource = tabBlurColorSource
        }
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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // ProfileActivity records its source once per frame. Record the gray
        // profile surface plus the pager only; drawing this whole hierarchy would
        // recursively capture the glass tab into its own source.
        recordTabBlurSource()
    }

    // UniversalAdapter.updateColors paints factory rows windowBackgroundWhite
    // on every attach, right after running this view's own updateColors. This
    // row's contract is to stay transparent over the gray outer surface (the
    // white sheet otherwise covers the whole feed on the first profile visit),
    // so every background paint is forced back to transparent.
    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun recordTabBlurSource() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val source = tabBlurSource as? BlurredBackgroundSourceRenderNode ?: return
        if (source.inRecording()) return
        val root = blurSourceRoot ?: return
        val width = root.measuredWidth
        val height = root.measuredHeight
        if (width <= 0 || height <= 0) return
        if (!ViewPositionWatcher.computeRectInParent(pager, root, blurPagerBounds)) return

        val recordingCanvas = source.beginRecording(width, height)
        try {
            // The glass pill samples the surface it floats over: the gray
            // profile background with the feed content on top. A white base
            // here made the pill render as an opaque white block over the
            // empty feed.
            recordingCanvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray))
            val save = recordingCanvas.save()
            recordingCanvas.translate(blurPagerBounds.left, blurPagerBounds.top)
            pager.draw(recordingCanvas)
            recordingCanvas.restoreToCount(save)
        } finally {
            source.endRecording()
        }
    }

    private val pager: ViewPagerFixed = object : ViewPagerFixed(context) {
        override fun onTabPageSelected(position: Int, forward: Boolean) {
            ProfileFeedPage.fromOrdinal(position)?.let { page ->
                onPageSelected(page)
                pages.getOrNull(page.ordinal)?.requestNextPageIfNeeded()
            }
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
                pages.getOrNull(it.ordinal)?.requestNextPageIfNeeded()
            }
        }

        override fun getManualScrollDuration(): Long = TAB_ANIMATION_DURATION_MS.toLong()
    }

    private val pages = arrayOfNulls<FeedPage>(ProfileFeedPage.entries.size)
    private val pageStates = Array(ProfileFeedPage.entries.size) { ProfileFeedUiState() }
    private var selectedPage = ProfileFeedPage.Topics
    private var bottomContentInset = AndroidUtilities.dp(
        (MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat(),
    )
    private var visibleContentHeight = 0

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        val tabBackgroundFactory = BlurredBackgroundDrawableViewFactory(tabBlurSource).apply {
            setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS))
        }
        if (blurPositionWatcher != null && blurSourceRoot != null) {
            tabBackgroundFactory.setSourceRootView(blurPositionWatcher, blurSourceRoot)
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
                0f,
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
                page.submit(pageStates[page.page.ordinal])
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

    private var pinnedToTop = false

    fun setPinnedToTop(pinned: Boolean) {
        if (pinnedToTop == pinned) {
            tabStrip.setOpen(pinned)
            return
        }
        pinnedToTop = pinned
        tabStrip.setOpen(pinned)
        // The initial-load skeleton stays out of the resting peek area: until
        // the section scrolls up to the action bar, the feed surface shows the
        // gray background and the tab pill only, matching when upstream
        // surfaces its shared-media loading sheet. Resubmitting on both edges
        // recomputes the skeleton gate from the new pinned state.
        pages.forEach { it?.reshowInitialSkeletonIfLoading() }
    }

    fun scrollCurrentToTop() {
        currentListView()?.smoothScrollToPosition(0)
    }

    fun submit(page: ProfileFeedPage, state: ProfileFeedUiState) {
        pageStates[page.ordinal] = state
        pages.getOrNull(page.ordinal)?.submit(state)
    }

    fun setBottomContentInset(inset: Int) {
        val resolved = inset.coerceAtLeast(0)
        if (bottomContentInset == resolved) return
        bottomContentInset = resolved
        pages.forEach { it?.applyBottomContentInset() }
    }

    fun setVisibleContentHeight(height: Int) {
        val resolved = height.coerceAtLeast(0)
        if (visibleContentHeight == resolved) return
        visibleContentHeight = resolved
        pages.forEach { it?.applyVisibleContentHeight() }
    }

    override fun updateColors() {
        setBackgroundColor(Color.TRANSPARENT)
        tabBlurColorSource.setColor(Theme.getColor(Theme.key_windowBackgroundGray))
        tabStrip.updateColors()
        tabStrip.invalidate()
        pages.forEach { it?.updateColors() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var height = viewportHeightProvider?.invoke()?.takeIf { it > 0 }
            ?: MeasureSpec.getSize(heightMeasureSpec)
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
        private val emptyView = StickerEmptyView(
            context,
            null,
            StickerEmptyView.STICKER_TYPE_SEARCH,
        ).apply {
            visibility = View.GONE
            setBackgroundColor(Color.TRANSPARENT)
            setAnimateLayoutChange(true)
            showProgress(false, false)
        }
        private var state = ProfileFeedUiState()

        init {
            listView.layoutManager = LinearLayoutManager(context)
            listView.itemAnimator = DialogsItemAnimator(listView)
            // Upstream SharedMediaLayout mediaPages draw no page background: white
            // rounded sections over the outer gray surface come from RecyclerListView
            // sections, so the feed stays transparent until pinned. The page is
            // full-height like upstream; content starts below the tab strip but
            // scrolls under its glass pill.
            listView.setSections(true)
            listView.setPadding(
                0,
                AndroidUtilities.dp(50f),
                0,
                bottomContentInset,
            )
            listView.clipToPadding = false
            listView.isNestedScrollingEnabled = true
            listView.adapter = adapter
            listView.setOnItemClickListener { _, position ->
                adapter.getTopicAt(position)?.let { onTopicSelected(it, page) }
            }
            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    requestNextPageIfNeeded()
                }
            })
            listView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                requestNextPageIfNeeded()
            }
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            applyBottomContentInset()
            applyVisibleContentHeight()
        }

        fun applyBottomContentInset() {
            listView.setPadding(0, listView.paddingTop, 0, bottomContentInset)
            emptyView.setPadding(0, AndroidUtilities.dp(50f), 0, bottomContentInset)
        }

        fun applyVisibleContentHeight() {
            if (visibleContentHeight <= 0 || measuredHeight <= 0) return
            val visibleHeight = visibleContentHeight.coerceAtLeast(AndroidUtilities.dp(120f))
            emptyView.translationY = -(measuredHeight - visibleHeight).coerceAtLeast(0) / 2f
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            applyVisibleContentHeight()
        }

        fun reshowInitialSkeletonIfLoading() {
            if (state.isLoading && state.items.isEmpty()) {
                submit(state)
            }
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
            // The initial-load skeleton renders as in-list rows once the
            // section is pinned, so the section decoration wraps them in the
            // same white card as loaded topics; the resting peek stays gray.
            val showInitialSkeleton = pinnedToTop && showLoading
            setOverlayVisible(emptyView, showEmpty)
            adapter.submit(
                state.items,
                state.categories,
                state.isLoadingMore && state.loadMoreErrorMessage == null,
                showInitialSkeleton,
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
            listView.post { requestNextPageIfNeeded() }
        }

        fun requestNextPageIfNeeded() {
            if (page.ordinal != pager.currentPosition) return
            val manager = listView.layoutManager as? LinearLayoutManager ?: return
            if (
                shouldRequestProfileFeedPage(
                    state = state,
                    lastVisibleItem = manager.findLastVisibleItemPosition(),
                    itemCount = manager.itemCount,
                )
            ) {
                onLoadMore(page)
            }
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

internal fun shouldRequestProfileFeedPage(
    state: ProfileFeedUiState,
    lastVisibleItem: Int,
    itemCount: Int,
): Boolean {
    if (
        state.isLoading ||
        state.isLoadingMore ||
        !state.hasMore ||
        state.loadMoreErrorMessage != null
    ) {
        return false
    }
    if (itemCount == 0) return true
    return lastVisibleItem >= itemCount - PROFILE_FEED_PREFETCH_ITEMS
}

private const val PROFILE_FEED_PREFETCH_ITEMS = 4

internal enum class ProfileFeedPage(val tabId: Int) {
    Topics(1),
    Replies(2),
    ;

    companion object {
        fun fromTabId(tabId: Int): ProfileFeedPage? = entries.firstOrNull { it.tabId == tabId }
        fun fromOrdinal(ordinal: Int): ProfileFeedPage? = entries.getOrNull(ordinal)
    }
}
