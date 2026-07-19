package io.github.baba983.nativedo.ui.profile

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedType
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedViewModel
import io.github.baba983.nativedo.ui.topic.TopicCell
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import io.github.baba983.nativedo.ui.topic.TopicListAdapter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.RecyclerListView

class ProfileFeedFragment(
    private val username: String,
    private val feedType: ProfileFeedType,
) : BaseFragment(), Theme.ThemeDelegate {
    private val collectorScope = MainScope()
    private lateinit var viewModel: ProfileFeedViewModel
    private lateinit var listView: RecyclerListView
    private lateinit var adapter: TopicListAdapter

    override fun createView(context: Context): View {
        Theme.addThemeDelegate(this)
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(context.getString(feedType.screenTitleRes))
        actionBar.setCastShadows(false)
        applyCurrentTheme(context)

        val appContainer = (context.applicationContext as NativeDoApp).appContainer
        viewModel = ViewModelProvider(
            this,
            ProfileFeedViewModelFactory(
                username = username,
                feedType = feedType,
                repository = ProfileFeedRepository(appContainer.discourseApiClient),
                categoryStore = appContainer.categoryStore,
            ),
        )[ProfileFeedViewModel::class.java]

        adapter = TopicListAdapter(context, appContainer.baseUrl)
        listView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = DialogsItemAnimator(this)
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            adapter = this@ProfileFeedFragment.adapter
            setOnItemClickListener { _, position ->
                val topic = this@ProfileFeedFragment.adapter.getTopicAt(position)
                    ?: return@setOnItemClickListener
                presentFragment(
                    TopicDetailFragment(
                        topic.id,
                        topic.title,
                        if (feedType == ProfileFeedType.Replies) topic.targetPostNumber else null,
                        if (feedType == ProfileFeedType.Replies) topic.targetPostId else null,
                    ),
                )
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (layoutManager.findLastVisibleItemPosition() >= layoutManager.itemCount - 4) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }

        collectorScope.launch {
            viewModel.uiState.collectLatest(::render)
        }
        return listView
    }

    override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
        val descriptions = java.util.ArrayList<ThemeDescription>()
        val chromeDelegate = ThemeDescription.ThemeDescriptionDelegate {
            val context = context ?: return@ThemeDescriptionDelegate
            applyCurrentTheme(context)
            if (::listView.isInitialized) {
                listView.invalidate()
            }
        }
        if (actionBar != null) {
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_BACKGROUND,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_windowBackgroundWhite,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_ITEMSCOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultIcon,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_TITLECOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultTitle,
                ),
            )
        }
        if (::listView.isInitialized) {
            descriptions.add(
                ThemeDescription(
                    listView,
                    ThemeDescription.FLAG_BACKGROUND,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_windowBackgroundWhite,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    listView,
                    0,
                    arrayOf(TopicCell::class.java),
                    null as android.graphics.Paint?,
                    null,
                    chromeDelegate,
                    Theme.key_windowBackgroundWhite,
                ),
            )
        } else {
            descriptions.add(
                ThemeDescription(
                    null,
                    0,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    chromeDelegate,
                    Theme.key_windowBackgroundWhite,
                ),
            )
        }
        return descriptions
    }

    override fun didSetNewTheme() {
        Theme.applyThemeDescriptions(themeDescriptions)
    }

    override fun onFragmentDestroy() {
        Theme.removeThemeDelegate(this)
        collectorScope.cancel()
        super.onFragmentDestroy()
    }

    override fun getNavigationBarColor(): Int = Theme.getColor(Theme.key_windowBackgroundGray)

    private fun render(state: ProfileFeedUiState) {
        val context = context ?: return
        val emptyTitle = when (feedType) {
            ProfileFeedType.Topics -> context.getString(R.string.profile_feed_topics_empty_title)
            ProfileFeedType.Replies -> context.getString(R.string.profile_feed_replies_empty_title)
        }
        val emptyMessage = when (feedType) {
            ProfileFeedType.Topics -> context.getString(R.string.profile_feed_topics_empty_message)
            ProfileFeedType.Replies -> context.getString(R.string.profile_feed_replies_empty_message)
        }
        adapter.submit(
            state.items,
            state.categories,
            state.isLoadingMore && state.loadMoreErrorMessage == null,
            state.isLoading && state.items.isEmpty(),
            if (state.isEmpty) emptyTitle else null,
            if (state.isEmpty) emptyMessage else null,
            if (state.errorMessage != null) context.getString(R.string.topic_list_error_title) else null,
            state.errorMessage,
            state.loadMoreErrorMessage?.let {
                context.getString(R.string.topic_list_load_more_error)
            },
            viewModel::retry,
            viewModel::retryLoadMore,
        )
    }

    private fun applyCurrentTheme(context: Context) {
        actionBar.applyTheme()
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        (parentLayout as? View)?.setBackgroundColor(Theme.getColor(Theme.key_iv_background))
        (context as? Activity)?.let { activity ->
            AndroidUtilities.setLightStatusBar(activity.window, !Theme.isCurrentThemeDark())
        }
    }
}

private class ProfileFeedViewModelFactory(
    private val username: String,
    private val feedType: ProfileFeedType,
    private val repository: ProfileFeedRepository,
    private val categoryStore: io.github.baba983.nativedo.core.storage.CategoryStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileFeedViewModel(
            username = username,
            feedType = feedType,
            repository = repository,
            categoryStore = categoryStore,
        ) as T
}
