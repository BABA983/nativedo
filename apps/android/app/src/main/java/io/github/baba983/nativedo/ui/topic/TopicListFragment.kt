package io.github.baba983.nativedo.ui.topic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.Path
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.util.TypedValue
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.CategorySummary
import org.telegram.messenger.AndroidUtilities
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab
import io.github.baba983.nativedo.feature.home.ui.HomeFeedState
import io.github.baba983.nativedo.feature.home.ui.HomeViewModel
import io.github.baba983.nativedo.feature.topic.data.TopicSearchRepository
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.RecyclerItemsEnterAnimator
import org.telegram.ui.Components.SearchStateDrawable
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.PullForegroundDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.FragmentSearchField
import org.telegram.ui.Components.FragmentFloatingButton
import org.telegram.ui.Components.ViewPagerFixed
import me.vkryl.android.animator.BoolAnimator
import me.vkryl.android.animator.FactorAnimator
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val MENU_SWITCH_THEME = 1
private const val MENU_SEARCH = 2

private const val TAB_ID_LATEST = 0
private const val TAB_ID_NEW = 1
private const val TAB_ID_TOP = 2
private const val TAB_ID_SEARCH_TOPICS = 10
private const val TAB_ID_SEARCH_POSTS = 11
private const val TAB_ID_SEARCH_USERS = 12

/** Matches Telegram's DialogsActivity.SEARCH_FIELD_HEIGHT = 48. */
private const val SEARCH_FIELD_HEIGHT_DP = 48f

/** Tab strip container height. Matches Telegram's DialogsActivity.SEARCH_TABS_HEIGHT = 36 + 7 + 7. */
private const val TAB_STRIP_HEIGHT_DP = 50f
private const val SEARCH_UI_DURATION_MS = 350L
private const val SEARCH_ENTER_DURATION_MS = 200L
private const val SEARCH_EXIT_DURATION_MS = 180L
private const val SEARCH_EXIT_START_DELAY_MS = 20L
private const val SEARCH_PAGER_MIN_SCALE = 0.95f
private const val SEARCH_CONTENT_MAX_SCALE = 1.05f
private const val TAB_MODE_SWITCH_DURATION_MS = 220L
private const val TAB_MODE_SWITCH_OUT_DURATION_MS = 90L
private const val TAB_MODE_SWITCH_MIN_SCALE = 0.98f

private enum class SearchFilter {
    Topics,
    Posts,
    Users,
}

/**
 * Topic list screen with a Telegram-style search pill.
 *
 * Layout architecture (mirrors Telegram DialogsActivity):
 * - Root is a [SizeNotifierFrameLayout] (FrameLayout).
 * - ActionBar, pill, and tab strip are all **direct children** of the root
 *   FrameLayout — not nested in a LinearLayout. This lets them float freely
 *   via `translationY` and overlap each other during animations.
 *
 * Scroll / sticky behavior:
 * - Pill sits below ActionBar. As the list scrolls, pill fades out.
 * - Tab strip sits below pill. As the list scrolls and pill fades, tab strip
 *   slides up to occupy pill's space, then "sticks" below the ActionBar
 *   (like CSS `position: sticky`). This is driven by the list's scroll offset.
 *
 * Search expand / collapse:
 * - When search expands, pill slides up into ActionBar (replacing title).
 * - Tab strip content switches from [Latest/New/Top] to [Topics/Posts/Users].
 * - Tab strip stays in place below ActionBar — no separate search tabs needed.
 * - Search results fade in over the topic list.
 */
class TopicListFragment : BaseFragment(), FactorAnimator.Target, Theme.ThemeDelegate {

    // --- ViewModel & data -----------------------------------------------------------

    private lateinit var viewModel: HomeViewModel
    private lateinit var searchRepository: TopicSearchRepository
    private val collectorScope = MainScope()

    // --- Views ----------------------------------------------------------------------

    private var listView: RecyclerListView? = null
    private var tabStrip: BubbleTabStrip? = null
    private var tabStripContainer: PillTabContainer? = null
    private var adapter: TopicListAdapter? = null
    private var viewPager: ViewPagerFixed? = null
    private var searchListView: RecyclerListView? = null
    private var searchAdapter: TopicListAdapter? = null
    private var searchMenuItem: ActionBarMenuItem? = null
    private var fragmentSearchField: FragmentSearchField? = null
    private var searchContentView: View? = null
    private var actionBarDivider: View? = null

    /**
     * Body container (pager + search results only).
     * Tab strip is NOT in here — it floats in the root FrameLayout.
     */
    private var bodyContainer: FrameLayout? = null

    // --- Search state ---------------------------------------------------------------

    private var searchJob: Job? = null
    private var selectedSearchFilter = SearchFilter.Topics
    private val pagesByPosition = mutableMapOf<Int, TopicListPage>()
    private val scrollPositions = mutableMapOf<Int, ScrollPosition>()
    private var searchStateDrawable: SearchStateDrawable? = null
    private var searchExpanded = false
    private var searchContentAnimator: ValueAnimator? = null
    private var searchContentFactor = 0f

    /** Whether the tab strip currently shows search filters (vs. feed tabs). */
    private var tabStripInSearchMode = false

    // --- BoolAnimators (Telegram pattern) -------------------------------------------

    private val ANIMATOR_ID_SEARCH_VISIBLE = 1

    private val animatorSearchVisible = BoolAnimator(
        ANIMATOR_ID_SEARCH_VISIBLE,
        this,
        CubicBezierInterpolator.EASE_OUT_QUINT,
        SEARCH_UI_DURATION_MS,
        false,
    )

    // --- FactorAnimator.Target ------------------------------------------------------

    override fun onFactorChanged(id: Int, factor: Float, fraction: Float, callee: FactorAnimator?) {
        if (id == ANIMATOR_ID_SEARCH_VISIBLE) {
            checkUi_searchFieldVisibility()
        }
    }

    override fun onFactorChangeFinished(id: Int, finalFactor: Float, callee: FactorAnimator?) {
        if (id == ANIMATOR_ID_SEARCH_VISIBLE) {
            listView?.isVerticalScrollBarEnabled = true
            searchListView?.isVerticalScrollBarEnabled = true
            if (finalFactor == 0f && tabStripInSearchMode) {
                switchTabStripToFeedMode(animated = false)
            }
        }
    }

    // ================================================================================
    // createView
    // ================================================================================

    override fun createView(context: Context): View {
        Theme.addThemeDelegate(this)
        val ctxString = { id: Int -> context.getString(id) }
        actionBar.setTitle(ctxString(R.string.screen_topic_list))
        actionBar.setBackButtonDrawable(null)
        actionBar.setAddToContainer(false)
        actionBar.setCastShadows(false)
        applyCurrentTheme(context)

        val appContainer = (context.applicationContext as NativeDoApp).appContainer
        searchRepository = TopicSearchRepository(appContainer.discourseApiClient)
        viewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(
                repository = HomeRepository(appContainer.discourseApiClient),
                categoryStore = appContainer.categoryStore,
            ),
        )[HomeViewModel::class.java]

        // ---- ActionBar menu (search + overflow) ----
        fun themeModeText(): String {
            val id = if (Theme.isCurrentThemeDark()) R.string.theme_mode_day else R.string.theme_mode_night
            return ctxString(id)
        }

        val menu = actionBar.createMenu()

        searchStateDrawable = SearchStateDrawable().apply {
            setColor(Theme.getColor(Theme.key_actionBarDefaultIcon))
            setIconState(SearchStateDrawable.State.STATE_SEARCH, false)
        }
        searchMenuItem = menu.addItem(MENU_SEARCH, searchStateDrawable as Drawable).apply {
            alpha = 0f
            visibility = View.INVISIBLE
        }

        menu.addItem(0, MoreDrawable())
            .apply {
                contentDescription = ctxString(R.string.action_more_options)
                addSubItem(MENU_SWITCH_THEME, themeModeText())
            }

        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == MENU_SEARCH) {
                        enterSearchMode()
                    } else if (id == MENU_SWITCH_THEME) {
                        Theme.toggleTheme()
                        actionBar.menu?.getItem(0)?.setSubItemText(MENU_SWITCH_THEME, themeModeText())
                    }
                }
            },
        )

        // ---- FragmentSearchField (the pill) ----
        fragmentSearchField = FragmentSearchField(context, null).apply {
            editText.hint = ctxString(R.string.search_topics_hint)
            setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))
            pivotX = 0f
            pivotY = 0f
            editText.isFocusable = false
            editText.isFocusableInTouchMode = false
            editText.isCursorVisible = false
            setCloseButtonVisible(false)
            setOnClickListener { enterSearchMode() }
            editText.setOnClickListener { enterSearchMode() }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    submitSearchQuery(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setCloseButtonOnClickListener {
                if (editText.text.isNotEmpty()) {
                    editText.text.clear()
                } else {
                    exitSearchMode()
                }
            }
            updateColors()
        }

        // ---- Root layout (FrameLayout, like Telegram's contentView) ----
        val root = SizeNotifierFrameLayout(context)
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))

        // ActionBar — direct child of root.
        root.addView(
            actionBar,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
        )

        // Search pill — direct child of root, positioned via translationY.
        root.addView(
            fragmentSearchField,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT.toFloat(),
                SEARCH_FIELD_HEIGHT_DP,
                Gravity.TOP,
                7f,
                0f,
                7f,
                0f,
            ),
        )

        val pillContainer = PillTabContainer(context)
        val strip = BubbleTabStrip(context)
        strip.setTabs(feedTabs(context), TAB_ID_LATEST, false)
        strip.setDelegate { id, forward ->
            if (searchExpanded) {
                selectedSearchFilter = when (id) {
                    TAB_ID_SEARCH_POSTS -> SearchFilter.Posts
                    TAB_ID_SEARCH_USERS -> SearchFilter.Users
                    else -> SearchFilter.Topics
                }
                fragmentSearchField?.editText?.text?.let { submitSearchQuery(it.toString()) }
                return@setDelegate
            }
            val position = idToPosition(id)
            val changed = viewPager?.scrollToPosition(position) == true
            if (!changed) {
                viewModel.onFeedSelected(idToTab(id))
            }
        }
        pillContainer.addView(
            strip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        tabStrip = strip
        tabStripContainer = pillContainer
        root.addView(
            pillContainer,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT.toFloat(),
                TAB_STRIP_HEIGHT_DP,
                Gravity.TOP,
                0f,
                0f,
                0f,
                0f,
            ),
        )

        // Body container (pager + search results) — also direct child of root.
        val body = FrameLayout(context)
        root.addView(
            body,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        bodyContainer = body

        actionBarDivider = View(context).apply {
            alpha = 0f
            setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.18f))
        }
        root.addView(
            actionBarDivider,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), 1f, Gravity.TOP),
        )

        val pager = object : ViewPagerFixed(context) {
            override fun onTabPageSelected(position: Int, forward: Boolean) {
                viewModel.onFeedSelected(positionToTab(position))
            }

            override fun onScrollEnd() {
                val id = positionToId(currentPosition)
                strip.selectTab(id, false)
                viewModel.onFeedSelected(positionToTab(currentPosition))
                pagesByPosition[currentPosition]?.let { currentPage ->
                    listView = currentPage.listView
                    this@TopicListFragment.adapter = currentPage.adapter
                    updateSearchPillOffset(currentPage)
                }
            }
        }
        pager.setAdapter(object : ViewPagerFixed.Adapter() {
            override fun getItemCount(): Int = HomeFeedTab.entries.size
            override fun createView(viewType: Int): View = TopicListPage(context, appContainer.baseUrl)
            override fun bindView(view: View, position: Int, viewType: Int) {
                val page = view as TopicListPage
                page.bindPosition(position)
                pagesByPosition[position] = page
                if (position == pager.currentPosition) {
                    listView = page.listView
                    this@TopicListFragment.adapter = page.adapter
                    updateSearchPillOffset(page)
                }
                viewModel.uiState.value.let { state ->
                    page.submit(state.feedState(positionToTab(position)), state.categories)
                }
            }
        })
        viewPager = pager
        body.addView(
            pager,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT),
        )

        // Search results overlay.
        val searchContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        val searchList = RecyclerListView(context)
        val searchAdapter = TopicListAdapter(context, appContainer.baseUrl)
        searchList.layoutManager = LinearLayoutManager(context)
        searchList.itemAnimator = DialogsItemAnimator(searchList)
        searchList.setPadding(0, AndroidUtilities.dp(TAB_STRIP_HEIGHT_DP), 0, 0)
        searchList.clipToPadding = false
        searchList.adapter = searchAdapter
        searchList.setOnItemClickListener { view, _ ->
            val cell = view as? TopicCell ?: return@setOnItemClickListener
            val topic = cell.topic ?: return@setOnItemClickListener
            presentFragment(TopicDetailFragment(topic.id, topic.title))
        }
        searchContent.addView(
            searchList,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        body.addView(
            searchContent,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT),
        )
        searchContentView = searchContent
        searchListView = searchList
        this.searchAdapter = searchAdapter

        // ---- Observe state ----
        collectorScope.launch {
            viewModel.uiState.collectLatest { state ->
                val currentTabId = tabToId(state.selectedTab)
                val currentPosition = tabToPosition(state.selectedTab)
                if (pager.currentPosition != currentPosition && !pager.isManualScrolling) {
                    pager.setPosition(currentPosition)
                }
                strip.selectTab(currentTabId, false)
                pagesByPosition.forEach { (position, page) ->
                    page.submit(state.feedState(positionToTab(position)), state.categories)
                }
            }
        }

        actionBar.bringToFront()
        fragmentSearchField?.bringToFront()
        pillContainer.bringToFront()

        return root
    }

    // ================================================================================
    // Position helpers
    // ================================================================================

    /** Pill's Y when collapsed: right below ActionBar. */
    private fun getPillCollapsedTop(): Float = actionBar.measuredHeight.toFloat()

    /** Pill's Y when expanded: bottom-aligned inside ActionBar content, below the status bar area. */
    private fun getPillExpandedTop(): Float {
        val abHeight = actionBar.measuredHeight
        return (abHeight - AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP)).coerceAtLeast(0).toFloat()
    }

    /** Tab strip's Y when pill is fully visible: below ActionBar + pill. */
    private fun getTabStripFullTop(): Float =
        actionBar.measuredHeight.toFloat() + AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP)

    /** Tab strip's Y when pill is gone (scrolled away): just below ActionBar. */
    private fun getTabStripStickyTop(): Float = actionBar.measuredHeight.toFloat()

    /** Body is anchored below ActionBar; each list owns the scrollable header padding. */
    private fun getBodyTopMargin(): Int = actionBar.measuredHeight

    // ================================================================================
    // Unified UI state check
    // ================================================================================

    private fun checkUi_searchFieldVisibility() {
        val pill = fragmentSearchField ?: return
        if (tabStrip == null) return
        val container = tabStripContainer ?: return
        val body = bodyContainer ?: return
        val searchContent = searchContentView
        val searchItem = searchMenuItem
        val pager = viewPager

        val searchFactor = animatorSearchVisible.getFloatValue() // 0→1

        val scrollProgress = pill.tag as? Float ?: 0f

        // --- Pill translationY & alpha ---
        val collapsedY = getPillCollapsedTop()
        val scrolledY = collapsedY - AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP)
        val expandedY = getPillExpandedTop()
        val feedY = collapsedY + (scrolledY - collapsedY) * scrollProgress
        val pillY = feedY + (expandedY - feedY) * searchFactor
        pill.translationY = pillY

        val scrollAlpha = 1f - scrollProgress
        val pillAlpha = maxOf(searchFactor, scrollAlpha).coerceIn(0f, 1f)
        pill.alpha = pillAlpha
        pill.visibility = if (pillAlpha > 0.01f) View.VISIBLE else View.GONE

        // --- Tab strip translationY: sticky behavior ---
        container.translationY = pillY + AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP)
        container.visibility = View.VISIBLE

        // --- Body topMargin ---
        val lp = body.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            val topMargin = getBodyTopMargin()
            if (lp.topMargin != topMargin) {
                lp.topMargin = topMargin
                body.layoutParams = lp
            }
        }

        // --- Search results & pager visibility ---
        val contentFactor = searchContentFactor
        val pagerAlpha = 1f - contentFactor
        pager?.let {
            it.alpha = pagerAlpha
            it.scaleX = 1f - (1f - SEARCH_PAGER_MIN_SCALE) * contentFactor
            it.scaleY = 1f - (1f - SEARCH_PAGER_MIN_SCALE) * contentFactor
            it.visibility = if (pagerAlpha > 0.01f) View.VISIBLE else View.GONE
        }
        searchContent?.let {
            it.alpha = contentFactor
            it.scaleX = SEARCH_CONTENT_MAX_SCALE - (SEARCH_CONTENT_MAX_SCALE - 1f) * contentFactor
            it.scaleY = SEARCH_CONTENT_MAX_SCALE - (SEARCH_CONTENT_MAX_SCALE - 1f) * contentFactor
            it.visibility = if (contentFactor > 0.01f) View.VISIBLE else View.GONE
        }

        // --- ActionBar title/menu ---
        if (searchFactor > 0.5f) {
            actionBar.setTitle("")
        } else {
            actionBar.setTitle(getContext()?.getString(R.string.screen_topic_list).orEmpty())
        }
        actionBar.menu?.let { menu ->
            val menuAlpha = 1f - searchFactor
            menu.alpha = menuAlpha
            menu.visibility = if (menuAlpha > 0.01f) View.VISIBLE else View.INVISIBLE
        }

        actionBarDivider?.let {
            it.translationY = actionBar.measuredHeight - 1f
            it.alpha = (scrollProgress * (1f - searchFactor)).coerceIn(0f, 1f)
        }

        // --- ActionBar search button (complementary to pill) ---
        searchItem?.let { FragmentFloatingButton.setAnimatedVisibility(it, (1f - scrollAlpha) * (1f - searchFactor)) }
    }

    // ================================================================================
    // Tab strip mode switching
    // ================================================================================

    private fun switchTabStripToSearchMode(animated: Boolean) {
        val strip = tabStrip ?: return
        val ctx = getContext() ?: return
        tabStripInSearchMode = true
        strip.setTabs(searchTabs(ctx), TAB_ID_SEARCH_TOPICS, animated)
    }

    private fun switchTabStripToFeedMode(animated: Boolean) {
        val strip = tabStrip ?: return
        val ctx = getContext() ?: return
        tabStripInSearchMode = false
        strip.setTabs(feedTabs(ctx), tabToId(viewModel.uiState.value.selectedTab), animated)
    }

    // ================================================================================
    // Scroll-driven pill offset
    // ================================================================================

    private fun updateSearchPillOffset(page: TopicListPage? = pagesByPosition[viewPager?.currentPosition ?: 0]) {
        val pill = fragmentSearchField ?: return
        if (searchExpanded) return

        val scrollOffset = page?.listView?.computeVerticalScrollOffset() ?: 0
        val maxOffset = AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP)

        val progress = (scrollOffset / maxOffset.toFloat()).coerceIn(0f, 1f)

        // Store scroll progress for checkUi to read.
        pill.tag = progress

        checkUi_searchFieldVisibility()
    }

    // ================================================================================
    // Theme
    // ================================================================================

    override fun didSetNewTheme() {
        val context = getContext() ?: return
        applyCurrentTheme(context)
        (fragmentView as? View)?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        actionBarDivider?.setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.18f))
        searchStateDrawable?.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon))
        fragmentSearchField?.updateColors()
        searchContentView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        searchListView?.let { list ->
            list.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            for (index in 0 until list.childCount) {
                (list.getChildAt(index) as? Theme.Colorable)?.updateColors()
            }
            list.invalidate()
        }
        tabStrip?.updateColors()
        tabStripContainer?.updateColors()
        pagesByPosition.values.forEach { page -> page.updateColors() }
    }

    // ================================================================================
    // Back press
    // ================================================================================

    override fun onBackPressed(): Boolean {
        if (searchExpanded) {
            exitSearchMode()
            return false
        }
        return super.onBackPressed()
    }

    // ================================================================================
    // Search query
    // ================================================================================

    private fun submitSearchQuery(query: String) {
        val normalizedQuery = query.trim()
        searchJob?.cancel()
        if (normalizedQuery.isBlank()) {
            searchAdapter?.submit(emptyList(), viewModel.uiState.value.categories, false, false)
            searchStateDrawable?.setIconState(SearchStateDrawable.State.STATE_SEARCH, true)
            return
        }
        searchAdapter?.submit(emptyList(), viewModel.uiState.value.categories, false, true)
        searchStateDrawable?.setIconState(SearchStateDrawable.State.STATE_PROGRESS, true)
        searchJob = collectorScope.launch {
            delay(300L)
            val result = searchRepository.search(normalizedQuery, page = 0)
            if (!isActiveSearchQuery(normalizedQuery)) return@launch
            result.fold(
                onSuccess = { searchResult ->
                    searchAdapter?.submit(
                        searchResult.topics,
                        viewModel.uiState.value.categories,
                        false,
                        false,
                        getContext()?.getString(R.string.search_topics_empty_title),
                        getContext()?.getString(R.string.search_topics_empty_message),
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                },
                onFailure = { error ->
                    searchAdapter?.submit(
                        emptyList(),
                        viewModel.uiState.value.categories,
                        false,
                        false,
                        null,
                        null,
                        getContext()?.getString(R.string.search_topics_error_title),
                        error.message ?: error.javaClass.simpleName,
                        null,
                        { submitSearchQuery(normalizedQuery) },
                        null,
                    )
                },
            )
            searchStateDrawable?.setIconState(SearchStateDrawable.State.STATE_SEARCH, true)
        }
    }

    private fun isActiveSearchQuery(query: String): Boolean {
        return searchExpanded && searchJob?.isCancelled == false && query.isNotBlank()
    }

    private fun animateSearchContent(show: Boolean) {
        searchContentAnimator?.let { animator ->
            searchContentAnimator = null
            animator.cancel()
        }
        searchContentView?.let {
            if (show) {
                it.visibility = View.VISIBLE
                if (searchContentFactor == 0f) {
                    it.alpha = 0f
                    it.scaleX = SEARCH_CONTENT_MAX_SCALE
                    it.scaleY = SEARCH_CONTENT_MAX_SCALE
                }
            }
        }
        listView?.isVerticalScrollBarEnabled = false
        searchListView?.isVerticalScrollBarEnabled = false
        searchContentAnimator = ValueAnimator.ofFloat(searchContentFactor, if (show) 1f else 0f).apply {
            duration = if (show) SEARCH_ENTER_DURATION_MS else SEARCH_EXIT_DURATION_MS
            startDelay = if (show) 0L else SEARCH_EXIT_START_DELAY_MS
            interpolator = CubicBezierInterpolator.EASE_OUT
            addUpdateListener { animator ->
                searchContentFactor = animator.animatedValue as Float
                checkUi_searchFieldVisibility()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (searchContentAnimator != animation) return
                    searchContentFactor = if (show) 1f else 0f
                    checkUi_searchFieldVisibility()
                    listView?.isVerticalScrollBarEnabled = true
                    searchListView?.isVerticalScrollBarEnabled = true
                    searchContentAnimator = null
                }
            })
            start()
        }
    }

    // ================================================================================
    // Search mode enter / exit
    // ================================================================================

    private fun enterSearchMode() {
        if (searchExpanded) return
        searchExpanded = true
        switchTabStripToSearchMode(animated = true)
        fragmentSearchField?.editText?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
            setText("")
        }
        fragmentSearchField?.setCloseButtonVisible(true)
        animatorSearchVisible.setDuration(SEARCH_UI_DURATION_MS)
        animatorSearchVisible.setStartDelay(0L)
        animatorSearchVisible.setValue(true, true)
        animateSearchContent(true)
        fragmentSearchField?.editText?.post {
            val field = fragmentSearchField?.editText ?: return@post
            field.requestFocus()
            val imm = field.context?.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun exitSearchMode() {
        if (!searchExpanded) return
        searchJob?.cancel()
        searchExpanded = false
        switchTabStripToFeedMode(animated = true)
        fragmentSearchField?.editText?.let { field ->
            val imm = field.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(field.windowToken, 0)
            field.clearFocus()
            field.setText("")
            field.isFocusable = false
            field.isFocusableInTouchMode = false
            field.isCursorVisible = false
        }
        fragmentSearchField?.setCloseButtonVisible(false)
        searchAdapter?.submit(emptyList(), viewModel.uiState.value.categories, false, false)
        animatorSearchVisible.setDuration(SEARCH_UI_DURATION_MS)
        animatorSearchVisible.setStartDelay(0L)
        animatorSearchVisible.setValue(false, true)
        animateSearchContent(false)
    }

    // ================================================================================
    // Fragment lifecycle
    // ================================================================================

    override fun onFragmentDestroy() {
        pagesByPosition.values.forEach { page -> page.saveScrollPosition() }
        Theme.removeThemeDelegate(this)
        super.onFragmentDestroy()
        collectorScope.cancel()
    }

    private fun applyCurrentTheme(context: Context) {
        actionBar.applyTheme()
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        actionBar.setCastShadows(false)
        (parentLayout as? View)?.setBackgroundColor(Theme.getColor(Theme.key_iv_background))
        (context as? Activity)?.let {
            AndroidUtilities.setLightStatusBar(it.window, !Theme.isCurrentThemeDark())
        }
    }

    override fun getNavigationBarColor(): Int = Theme.getColor(Theme.key_windowBackgroundGray)

    override fun isSupportEdgeToEdge(): Boolean = true

    // ================================================================================
    // Tab / position mapping helpers
    // ================================================================================

    private fun idToTab(id: Int): HomeFeedTab = when (id) {
        TAB_ID_NEW -> HomeFeedTab.New
        TAB_ID_TOP -> HomeFeedTab.Top
        else -> HomeFeedTab.Latest
    }

    private fun idToPosition(id: Int): Int = when (id) {
        TAB_ID_NEW -> 1
        TAB_ID_TOP -> 2
        else -> 0
    }

    private fun positionToTab(position: Int): HomeFeedTab = when (position) {
        1 -> HomeFeedTab.New
        2 -> HomeFeedTab.Top
        else -> HomeFeedTab.Latest
    }

    private fun positionToId(position: Int): Int = when (position) {
        1 -> TAB_ID_NEW
        2 -> TAB_ID_TOP
        else -> TAB_ID_LATEST
    }

    private fun tabToPosition(tab: HomeFeedTab): Int = when (tab) {
        HomeFeedTab.Latest -> 0
        HomeFeedTab.New -> 1
        HomeFeedTab.Top -> 2
    }

    private fun tabToId(tab: HomeFeedTab): Int = when (tab) {
        HomeFeedTab.Latest -> TAB_ID_LATEST
        HomeFeedTab.New -> TAB_ID_NEW
        HomeFeedTab.Top -> TAB_ID_TOP
    }

    // ================================================================================
    // PullRefreshLayout
    // ================================================================================

    private data class ScrollPosition(val position: Int, val offset: Int)

    private inner class PullRefreshLayout(
        context: Context,
        private val listView: RecyclerListView,
        private val onRefresh: () -> Unit,
    ) : FrameLayout(context) {
        private val maxPull = PullForegroundDrawable.getMaxOverscroll().toFloat()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val pullDrawable = PullForegroundDrawable(
            context.getString(R.string.topic_list_pull_refresh),
            context.getString(R.string.topic_list_release_refresh),
        )
        private val headerView = object : View(context) {
            override fun onDraw(canvas: Canvas) { pullDrawable.draw(canvas) }
        }
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var pullDistance = 0f
        private var thresholdReached = false
        private var settleAnimator: ValueAnimator? = null
        var currentFeed: HomeFeedState = HomeFeedState()

        init {
            pullDrawable.setCell(headerView)
            pullDrawable.setListView(listView)
            pullDrawable.doNotShow()
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, PullForegroundDrawable.getMaxOverscroll(), Gravity.TOP))
        }

        fun updateColors() { pullDrawable.updateColors(); invalidate(); headerView.invalidate() }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; dragging = false
                    settleAnimator?.cancel(); pullDrawable.resetText()
                    thresholdReached = false; pullDrawable.colorize(false)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(ev.x - downX)
                    val dy = ev.y - downY
                    if (dy > touchSlop && dy > dx && !listView.canScrollVertically(-1)) {
                        dragging = true; parent?.requestDisallowInterceptTouchEvent(true); return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; settleAnimator?.cancel()
                    pullDrawable.resetText(); thresholdReached = false; pullDrawable.colorize(false); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - downY
                    if (dy > 0 && !listView.canScrollVertically(-1)) {
                        dragging = true; parent?.requestDisallowInterceptTouchEvent(true)
                        setPullDistance(dampenPull(dy)); return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging || pullDistance > 0f) {
                        if (pullDrawable.pullProgress >= PullForegroundDrawable.SNAP_HEIGHT && !currentFeed.isLoading && !currentFeed.isRefreshing) onRefresh()
                        pullDrawable.doNotShow(); pullDrawable.colorize(false); thresholdReached = false
                        animatePullBack(); dragging = false; parent?.requestDisallowInterceptTouchEvent(false); return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dampenPull(distance: Float): Float = distance.coerceAtMost(maxPull * 2.0f) * 0.5f

        private fun setPullDistance(distance: Float) {
            pullDistance = distance.coerceIn(0f, maxPull)
            listView.translationY = pullDistance; headerView.translationY = 0f
            val progress = (pullDistance / maxPull).coerceIn(0f, 1f)
            val reached = progress >= PullForegroundDrawable.SNAP_HEIGHT
            if (thresholdReached != reached) { thresholdReached = reached; pullDrawable.colorize(reached) }
            pullDrawable.showHidden(); pullDrawable.setWillDraw(pullDistance > 0f); pullDrawable.setPullProgress(progress)
            headerView.invalidate()
        }

        private fun animatePullBack() {
            settleAnimator?.cancel()
            settleAnimator = ValueAnimator.ofFloat(pullDistance, 0f).apply {
                interpolator = DecelerateInterpolator(); duration = 180L
                addUpdateListener { setPullDistance(it.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        pullDrawable.doNotShow(); pullDrawable.resetText(); pullDrawable.colorize(false)
                        thresholdReached = false; headerView.invalidate()
                    }
                })
                start()
            }
        }
    }

    // ================================================================================
    // TopicListPage
    // ================================================================================

    private inner class TopicListPage(context: Context, baseUrl: String) : FrameLayout(context) {
        val listView: RecyclerListView = RecyclerListView(context)
        val adapter: TopicListAdapter = TopicListAdapter(context, baseUrl)
        private val itemsEnterAnimator: RecyclerItemsEnterAnimator
        private val pullRefreshLayout = PullRefreshLayout(context, listView, viewModel::onRefresh)
        var position: Int = -1
            private set
        private var pendingScrollRestore = false
        private var lastTopicCount = 0

        init {
            listView.layoutManager = LinearLayoutManager(context)
            listView.itemAnimator = DialogsItemAnimator(listView)
            // Padding for pill + tab strip space above the list.
            listView.setPadding(0, AndroidUtilities.dp(SEARCH_FIELD_HEIGHT_DP + TAB_STRIP_HEIGHT_DP), 0, 0)
            listView.clipToPadding = false
            itemsEnterAnimator = RecyclerItemsEnterAnimator(listView, true)
            listView.adapter = adapter
            listView.setOnItemClickListener { view, _ ->
                val cell = view as? TopicCell ?: return@setOnItemClickListener
                val topic = cell.topic ?: return@setOnItemClickListener
                viewModel.markTopicSeen(topic.id)
                presentFragment(TopicDetailFragment(topic.id, topic.title))
            }
            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    saveScrollPosition()
                    if (position == viewPager?.currentPosition) updateSearchPillOffset(this@TopicListPage)
                    if (dy <= 0 || position != viewPager?.currentPosition) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val total = lm.itemCount
                    val lastVisible = lm.findLastVisibleItemPosition()
                    if (lastVisible >= total - 4) viewModel.loadNextPage()
                }
            })
            addView(pullRefreshLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        }

        fun bindPosition(position: Int) {
            if (this.position == position) return
            saveScrollPosition()
            if (this.position >= 0 && pagesByPosition[this.position] === this) pagesByPosition.remove(this.position)
            this.position = position; pendingScrollRestore = true
        }

        fun submit(feed: HomeFeedState, categories: Map<Int, CategorySummary>) {
            pullRefreshLayout.currentFeed = feed
            val previousTopicCount = lastTopicCount
            val context = getContext()
            adapter.submit(
                feed.items,
                categories,
                feed.isLoadingMore && feed.loadMoreErrorMessage == null,
                feed.isLoading && feed.items.isEmpty(),
                if (feed.isEmpty) context?.getString(R.string.topic_list_empty_title) else null,
                if (feed.isEmpty) context?.getString(R.string.topic_list_empty_message) else null,
                if (feed.errorMessage != null) context?.getString(R.string.topic_list_error_title) else null,
                feed.errorMessage,
                feed.loadMoreErrorMessage?.let { message -> context?.getString(R.string.topic_list_load_more_error) ?: message },
                viewModel::retrySelectedFeed,
                viewModel::loadNextPage,
            )
            lastTopicCount = feed.items.size
            if (previousTopicCount > 0 && feed.items.size > previousTopicCount && !feed.isLoading) listView.post { itemsEnterAnimator.showItemsAnimated(previousTopicCount) }
            restoreScrollPositionIfNeeded()
        }

        fun updateColors() {
            pullRefreshLayout.updateColors()
            listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            for (index in 0 until listView.childCount) { (listView.getChildAt(index) as? Theme.Colorable)?.updateColors() }
            listView.invalidate(); invalidate()
        }

        fun saveScrollPosition() {
            if (position < 0) return
            val lm = listView.layoutManager as? LinearLayoutManager ?: return
            val first = lm.findFirstVisibleItemPosition()
            if (first == RecyclerView.NO_POSITION) return
            val firstView = lm.findViewByPosition(first)
            scrollPositions[position] = ScrollPosition(first, (firstView?.top ?: 0) - listView.paddingTop)
        }

        private fun restoreScrollPositionIfNeeded() {
            if (!pendingScrollRestore) return
            val saved = scrollPositions[position] ?: run { pendingScrollRestore = false; return }
            listView.post {
                val lm = listView.layoutManager as? LinearLayoutManager ?: return@post
                if (adapter.itemCount > saved.position) { lm.scrollToPositionWithOffset(saved.position, saved.offset); pendingScrollRestore = false }
            }
        }
    }
}

private class HomeViewModelFactory(
    private val repository: HomeRepository,
    private val categoryStore: io.github.baba983.nativedo.core.storage.CategoryStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(repository = repository, categoryStore = categoryStore) as T
}

private class MoreDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun draw(canvas: Canvas) {
        val bounds = bounds; val centerX = bounds.centerX().toFloat(); val centerY = bounds.centerY().toFloat()
        val spacing = AndroidUtilities.dpf2(5f); val radius = AndroidUtilities.dpf2(1.6f)
        canvas.drawCircle(centerX, centerY - spacing, radius, paint)
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawCircle(centerX, centerY + spacing, radius, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(24f)
    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(24f)
}

private data class BubbleTab(val id: Int, val title: CharSequence)

private fun feedTabs(context: Context): List<BubbleTab> = listOf(
    BubbleTab(TAB_ID_LATEST, context.getString(R.string.home_tab_latest)),
    BubbleTab(TAB_ID_NEW, context.getString(R.string.home_tab_new)),
    BubbleTab(TAB_ID_TOP, context.getString(R.string.home_tab_top)),
)

private fun searchTabs(context: Context): List<BubbleTab> = listOf(
    BubbleTab(TAB_ID_SEARCH_TOPICS, context.getString(R.string.search_filter_topics)),
    BubbleTab(TAB_ID_SEARCH_POSTS, context.getString(R.string.search_filter_posts)),
    BubbleTab(TAB_ID_SEARCH_USERS, context.getString(R.string.search_filter_users)),
)

private class BubbleTabStrip(context: Context) : FrameLayout(context), Theme.Colorable {
    private val tabsContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorRect = RectF()
    private val selectorFrom = RectF()
    private val selectorTo = RectF()
    private val tabs = mutableListOf<BubbleTab>()
    private var selectedId = -1
    private var selectedIndex = 0
    private var selectorAnimator: ValueAnimator? = null
    private var delegate: ((Int, Boolean) -> Unit)? = null
    private var activeTextColor = 0
    private var inactiveTextColor = 0
    private var tabsModeSwitchGeneration = 0

    init {
        setWillNotDraw(false)
        addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        updateColors()
    }

    fun setDelegate(delegate: (Int, Boolean) -> Unit) {
        this.delegate = delegate
    }

    fun setTabs(newTabs: List<BubbleTab>, initialSelectedId: Int, animated: Boolean) {
        selectorAnimator?.cancel()
        animate().cancel()
        val generation = ++tabsModeSwitchGeneration
        if (!animated || tabsContainer.childCount == 0) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            replaceTabs(newTabs, initialSelectedId)
            return
        }
        animate()
            .alpha(0f)
            .scaleX(TAB_MODE_SWITCH_MIN_SCALE)
            .scaleY(TAB_MODE_SWITCH_MIN_SCALE)
            .setDuration(TAB_MODE_SWITCH_OUT_DURATION_MS)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT)
            .withEndAction {
                if (generation != tabsModeSwitchGeneration) return@withEndAction
                replaceTabs(newTabs, initialSelectedId)
                alpha = 0f
                scaleX = TAB_MODE_SWITCH_MIN_SCALE
                scaleY = TAB_MODE_SWITCH_MIN_SCALE
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(TAB_MODE_SWITCH_DURATION_MS)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT)
                    .start()
            }
            .start()
    }

    private fun replaceTabs(newTabs: List<BubbleTab>, initialSelectedId: Int) {
        tabs.clear()
        tabs.addAll(newTabs)
        tabsContainer.removeAllViews()
        selectedId = if (tabs.any { it.id == initialSelectedId }) initialSelectedId else tabs.firstOrNull()?.id ?: -1
        selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        tabs.forEachIndexed { index, tab ->
            tabsContainer.addView(
                createTabView(tab, index),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )
        }
        updateTextColors(1f, selectedIndex, selectedIndex)
        post {
            updateSelectorTarget(selectedIndex, selectorRect)
            invalidate()
        }
    }

    fun selectTab(id: Int, animated: Boolean) {
        val newIndex = tabs.indexOfFirst { it.id == id }
        if (newIndex < 0 || newIndex == selectedIndex && selectedId == id) return
        val oldIndex = selectedIndex
        selectedId = id
        selectedIndex = newIndex
        animateSelector(oldIndex, newIndex, animated)
    }

    private fun createTabView(tab: BubbleTab, index: Int): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            typeface = AndroidUtilities.bold()
            text = tab.title
            includeFontPadding = false
            setPadding(AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f), 0)
            background = null
            setOnClickListener {
                val previous = selectedIndex
                if (tab.id != selectedId) {
                    selectTab(tab.id, true)
                }
                delegate?.invoke(tab.id, index > previous)
            }
        }
    }

    private fun animateSelector(oldIndex: Int, newIndex: Int, animated: Boolean) {
        selectorAnimator?.cancel()
        updateSelectorTarget(oldIndex, selectorFrom)
        updateSelectorTarget(newIndex, selectorTo)
        if (!animated || width == 0) {
            selectorRect.set(selectorTo)
            updateTextColors(1f, oldIndex, newIndex)
            invalidate()
            return
        }
        selectorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                selectorRect.set(
                    lerp(selectorFrom.left, selectorTo.left, progress),
                    lerp(selectorFrom.top, selectorTo.top, progress),
                    lerp(selectorFrom.right, selectorTo.right, progress),
                    lerp(selectorFrom.bottom, selectorTo.bottom, progress),
                )
                updateTextColors(progress, oldIndex, newIndex)
                invalidate()
            }
            start()
        }
    }

    private fun updateSelectorTarget(index: Int, out: RectF) {
        val child = tabsContainer.getChildAt(index)
        if (child == null || child.width == 0) {
            out.setEmpty()
            return
        }
        val text = (child as TextView).text.toString()
        selectorTextPaint.textSize = child.textSize
        selectorTextPaint.typeface = child.typeface
        val halfWidth = selectorTextPaint.measureText(text) / 2f + AndroidUtilities.dp(12.5f)
        val centerX = child.left + child.width / 2f
        val top = height / 2f - AndroidUtilities.dp(14f)
        out.set(
            centerX - halfWidth,
            top,
            centerX + halfWidth,
            top + AndroidUtilities.dp(28f),
        )
    }

    private fun updateTextColors(progress: Float, oldIndex: Int, newIndex: Int) {
        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) as TextView
            child.setTextColor(when (i) {
                oldIndex -> blend(activeTextColor, inactiveTextColor, progress)
                newIndex -> blend(inactiveTextColor, activeTextColor, progress)
                selectedIndex -> activeTextColor
                else -> inactiveTextColor
            })
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSelectorTarget(selectedIndex, selectorRect)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!selectorRect.isEmpty) {
            canvas.drawRoundRect(selectorRect, selectorRect.height() / 2f, selectorRect.height() / 2f, selectorPaint)
        }
        super.dispatchDraw(canvas)
    }

    override fun updateColors() {
        activeTextColor = Theme.getColor(Theme.key_actionBarTabActiveText)
        inactiveTextColor = Theme.getColor(Theme.key_actionBarTabUnactiveText)
        selectorPaint.color = Theme.multAlpha(activeTextColor, 31f / 255f)
        updateTextColors(1f, selectedIndex, selectedIndex)
        invalidate()
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    private fun blend(from: Int, to: Int, progress: Float): Int {
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * progress).toInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * progress).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * progress).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * progress).toInt(),
        )
    }
}

private class PillTabContainer(context: Context) : FrameLayout(context), Theme.Colorable {

    private val clipPath = Path()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dpf2(0.66f)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateClipPath(w, h)
    }

    private fun updateClipPath(w: Int, h: Int) {
        val insetH = AndroidUtilities.dp(7f).toFloat()
        val insetV = AndroidUtilities.dp(7f).toFloat()
        val l = AndroidUtilities.dp(4f).toFloat() + insetH
        val t = insetV
        val r = w.toFloat() - AndroidUtilities.dp(4f).toFloat() - insetH
        val b = h.toFloat() - insetV
        val radius = (b - t) / 2f
        clipPath.rewind()
        clipPath.addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW)
    }

    private val clipBounds = RectF()

    override fun dispatchDraw(canvas: Canvas) {
        clipPath.computeBounds(clipBounds, true)
        val radius = clipBounds.height() / 2f
        canvas.drawRoundRect(clipBounds, radius, radius, shadowPaint)
        canvas.drawRoundRect(clipBounds, radius, radius, bgPaint)
        canvas.drawRoundRect(clipBounds, radius, radius, strokePaint)
        canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun updateColors() {
        bgPaint.color = Theme.getColor(Theme.key_windowBackgroundWhite)
        strokePaint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.16f)
        shadowPaint.color = Theme.getColor(Theme.key_windowBackgroundWhite)
        shadowPaint.setShadowLayer(AndroidUtilities.dp(4f).toFloat(), 0f, AndroidUtilities.dpf2(1.33f), Theme.multAlpha(Color.BLACK, if (Theme.isCurrentThemeDark()) 0.28f else 0.08f))
        invalidate()
    }
}
