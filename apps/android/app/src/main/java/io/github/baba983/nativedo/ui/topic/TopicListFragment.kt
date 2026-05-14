// PORT-MODE: Feature Workflow Port
// UPSTREAM-SCOPE: org/telegram/ui/DialogsActivity.java (createView, ActionBar search wiring)
// UPSTREAM-COMMIT: master@2026-05-13
// HOST-WIRING:
//   - Uses NativeDo's ScrollSlidingTextTabStrip / RecyclerListView / TopicCell ports.
//   - HomeViewModel state drives the adapter on a MainScope() that ends with onFragmentDestroy.
// ADAPTED-BACKEND:
//   - HomeRepository (Discourse JSON) replaces MessagesController/dialogsByTab.
//   - Tabs: Latest / New / Top (Discourse) instead of Telegram Folders.
// ADAPTED-PRODUCT:
//   - Overflow theme-toggle preserved for the milestone build.
// OMITTED:
//   - Archive folder header, hidden chats reveal animation, drag-reorder,
//     pull-to-refresh hookup (PullForegroundDrawable is ported but not yet bound to a
//     virtual archive cell; refresh happens through tab re-selection and load-more for now),
//     speed dial, fab, search results screen (search field expansion only).
package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab
import io.github.baba983.nativedo.feature.home.ui.HomeViewModel
import io.github.baba983.nativedo.ui.actionbar.ActionBar
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenuItem
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.cells.TopicCell
import io.github.baba983.nativedo.ui.components.LayoutHelper
import io.github.baba983.nativedo.ui.components.RecyclerListView
import io.github.baba983.nativedo.ui.components.ScrollSlidingTextTabStrip
import io.github.baba983.nativedo.ui.components.SearchStateDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val MENU_SWITCH_THEME = 1
private const val MENU_SEARCH = 2

private const val TAB_ID_LATEST = 0
private const val TAB_ID_NEW = 1
private const val TAB_ID_TOP = 2

class TopicListFragment : BaseFragment() {
    private lateinit var viewModel: HomeViewModel
    private val collectorScope = MainScope()

    private var listView: RecyclerListView? = null
    private var tabStrip: ScrollSlidingTextTabStrip? = null
    private var adapter: TopicListAdapter? = null
    private var searchStateDrawable: SearchStateDrawable? = null

    override fun createView(context: Context): View {
        val ctxString = { id: Int -> context.getString(id) }
        actionBar.setTitle(ctxString(R.string.screen_topic_list))
        actionBar.setBackButtonDrawable(null)
        applyCurrentTheme(context)

        val appContainer = (context.applicationContext as NativeDoApp).appContainer
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

        // Search button — uses SearchStateDrawable (magnifier ↔ loading ↔ X morph).
        searchStateDrawable = SearchStateDrawable().apply {
            setColor(Theme.getColor(Theme.key_actionBarDefaultIcon))
            setIconState(SearchStateDrawable.State.STATE_SEARCH, false)
        }
        menu.addItem(MENU_SEARCH, searchStateDrawable as Drawable)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onSearchExpand() {
                    // OMITTED: switch to search results UI (reserved for next milestone).
                }
                override fun onSearchCollapse() {
                    // OMITTED: restore tab/list UI (currently always visible behind search field).
                }
                override fun onTextChanged(editText: EditText?) {
                    // OMITTED: search query handling (reserved for next milestone).
                }
            })
            .setSearchFieldHint(ctxString(R.string.search_topics_hint))

        // Overflow (theme switch).
        menu.addItem(0, MoreDrawable())
            .apply {
                contentDescription = ctxString(R.string.action_more_options)
                addSubItem(MENU_SWITCH_THEME, themeModeText())
            }

        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == MENU_SWITCH_THEME) {
                        Theme.toggleTheme()
                        applyCurrentTheme(context)
                        actionBar.menu?.getItem(0)?.setSubItemText(MENU_SWITCH_THEME, themeModeText())
                        searchStateDrawable?.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon))
                        adapter?.notifyDataSetChangedCompat()
                        tabStrip?.let {
                            it.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault))
                            it.invalidate()
                        }
                    }
                }
            },
        )

        // ---- Root layout ----
        val root = FrameLayout(context)
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))

        // Tab strip across the top.
        val strip = ScrollSlidingTextTabStrip(context)
        strip.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault))
        strip.addTextTab(TAB_ID_LATEST, ctxString(R.string.home_tab_latest))
        strip.addTextTab(TAB_ID_NEW, ctxString(R.string.home_tab_new))
        strip.addTextTab(TAB_ID_TOP, ctxString(R.string.home_tab_top))
        strip.finishAddingTabs()
        strip.setDelegate(object : ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {
            override fun onPageSelected(id: Int, forward: Boolean) {
                viewModel.onFeedSelected(idToTab(id))
            }
            override fun onPageScrolled(progress: Float) {}
            override fun onSamePageSelected() {}
        })
        root.addView(
            strip,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP),
        )
        tabStrip = strip

        // List below the tab strip.
        val list = RecyclerListView(context)
        list.layoutManager = LinearLayoutManager(context)
        val adapter = TopicListAdapter(context, baseUrl = appContainer.baseUrl)
        list.adapter = adapter
        list.setOnItemClickListener { view, position ->
            val cell = view as? TopicCell ?: return@setOnItemClickListener
            val topic = cell.topic ?: return@setOnItemClickListener
            viewModel.markTopicSeen(topic.id)
            presentFragment(TopicDetailFragment(topic.id, topic.title))
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= total - 4) {
                    viewModel.loadNextPage()
                }
            }
        })
        root.addView(
            list,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.TOP,
                0, 44, 0, 0,
            ),
        )
        listView = list
        this.adapter = adapter

        // ---- Observe state ----
        collectorScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Sync tab strip selection.
                val currentTabId = tabToId(state.selectedTab)
                strip.setInitialTabId(currentTabId)
                // Push data into adapter.
                adapter.submit(
                    topics = state.items,
                    categories = state.categories,
                    showLoadingFooter = state.isLoadingMore || state.hasMore && state.items.isNotEmpty(),
                    showInitialLoading = state.isLoading && state.items.isEmpty(),
                )
            }
        }

        return root
    }

    override fun onFragmentDestroy() {
        super.onFragmentDestroy()
        collectorScope.cancel()
    }

    private fun applyCurrentTheme(context: Context) {
        actionBar.applyTheme()
        (parentLayout as? View)?.setBackgroundColor(
            Theme.getColor(Theme.key_iv_background),
        )
        (context as? Activity)?.let {
            AndroidUtilities.setLightStatusBar(it.window, !Theme.isCurrentThemeDark())
        }
    }

    override fun getNavigationBarColor(): Int {
        return Theme.getColor(Theme.key_windowBackgroundGray)
    }

    override fun isSupportEdgeToEdge(): Boolean = true

    private fun idToTab(id: Int): HomeFeedTab = when (id) {
        TAB_ID_NEW -> HomeFeedTab.New
        TAB_ID_TOP -> HomeFeedTab.Top
        else -> HomeFeedTab.Latest
    }

    private fun tabToId(tab: HomeFeedTab): Int = when (tab) {
        HomeFeedTab.Latest -> TAB_ID_LATEST
        HomeFeedTab.New -> TAB_ID_NEW
        HomeFeedTab.Top -> TAB_ID_TOP
    }
}

private class HomeViewModelFactory(
    private val repository: HomeRepository,
    private val categoryStore: io.github.baba983.nativedo.core.storage.CategoryStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(repository = repository, categoryStore = categoryStore) as T
    }
}

private class MoreDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val spacing = AndroidUtilities.dpf2(5f)
        val radius = AndroidUtilities.dpf2(1.6f)
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
