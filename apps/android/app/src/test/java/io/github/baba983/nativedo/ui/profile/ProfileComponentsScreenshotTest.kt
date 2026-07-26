package io.github.baba983.nativedo.ui.profile

import android.text.Html
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.R
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AboutLinkCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.StickerEmptyView

internal class ProfileComponentsScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun profileBioAndStateComponentsUseTelegramChrome() {
        paparazziRule.snapshot { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            RecyclerListView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(520f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                layoutManager = LinearLayoutManager(context)
                adapter = ProfileComponentsAdapter(
                    about = AboutLinkCell(context, null).apply {
                        setText(
                            Html.fromHtml(
                                "A longer linux.do bio with <a href=\"https://linux.do\">a profile link</a> " +
                                    "and enough text to exercise Telegram's three-line collapsed layout, show-more affordance, " +
                                    "link hit path, and dynamic row height before the remaining profile settings.",
                                Html.FROM_HTML_MODE_COMPACT,
                            ),
                            false,
                        )
                    },
                    empty = StickerEmptyView(
                        context,
                        null,
                        StickerEmptyView.STICKER_TYPE_SEARCH,
                    ).apply {
                        minimumHeight = AndroidUtilities.dp(156f)
                        title("Couldn't load profile")
                        subtitle("Network request failed\nRetry")
                        showProgress(false, false)
                    },
                    details = listOf(
                        TextDetailCell(context).apply {
                            setTextAndValue("活跃用户 (TL3)", "信任等级", true)
                        },
                        TextDetailCell(context).apply {
                            setTextAndValue("Battery", "徽标", true)
                        },
                        TextDetailCell(context).apply {
                            setTextAndValue("2731", "论坛积分", true)
                        },
                        TextDetailCell(context).apply {
                            setTextAndValue("最近活动 2 分钟前", "最近活动", true)
                        },
                    ),
                )
                itemAnimator = null
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        }
    }

    @Test
    fun loadingAndAvatarProgressKeepTelegramDimensions() {
        paparazziRule.verify { context ->
            val loading = LoadingCell(context)
            loading.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200f), View.MeasureSpec.AT_MOST),
            )
            assertThat(loading.measuredHeight).isEqualTo(AndroidUtilities.dp(54f))

            val progress = RadialProgressView(context).apply {
                setSize(AndroidUtilities.dp(26f))
            }
            progress.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f), View.MeasureSpec.EXACTLY),
            )
            assertThat(progress.measuredWidth).isEqualTo(AndroidUtilities.dp(64f))
            assertThat(progress.measuredHeight).isEqualTo(AndroidUtilities.dp(64f))
        }
    }

    @Test
    fun profileFeedTabsKeepTelegramIndicatorContract() {
        paparazziRule.snapshot { context ->
            Theme.createChatResources(context, false)
            ScrollSlidingTextTabStrip(context).apply {
                animationDuration = 320L
                setUseMinimalWidth(true)
                setColors(
                    Theme.key_profile_tabSelectedLine,
                    Theme.key_profile_tabSelectedText,
                    Theme.key_profile_tabText,
                    Theme.key_profile_tabSelector,
                )
                addTextTab(1, context.getString(R.string.profile_tab_topics))
                addTextTab(2, context.getString(R.string.profile_tab_replies))
                finishAddingTabs()
                setOpen(false)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(48f),
                )
            }
        }
    }

    @Test
    fun profileContentKeepsIndependentFullHeightFeedPages() {
        paparazziRule.verify { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val selected = mutableListOf<ProfileFeedPage>()
            val content = ProfileContentLayout(
                context = context,
                baseUrl = "https://linux.do",
                onTopicSelected = { _, _ -> },
                onPageSelected = selected::add,
                onLoadMore = {},
                onRetry = {},
                onRetryLoadMore = {},
            )
            content.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(640f), View.MeasureSpec.EXACTLY),
            )
            val topicsList = content.currentListView()
            content.selectPage(ProfileFeedPage.Replies, animated = false)

            assertThat(content.measuredHeight).isEqualTo(AndroidUtilities.dp(640f))
            assertThat(content.selectedPage()).isEqualTo(ProfileFeedPage.Replies)
            assertThat(content.currentListView()).isNotSameInstanceAs(topicsList)
            assertThat(selected.last()).isEqualTo(ProfileFeedPage.Replies)
        }
    }

    private class ProfileComponentsAdapter(
        private val about: AboutLinkCell,
        private val empty: StickerEmptyView,
        private val details: List<View>,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val views = listOf(about) + details + listOf(empty)

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(views[viewType]) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = views.size
    }
}
