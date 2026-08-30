package io.github.baba983.nativedo.ui.profile

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Html
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.R
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R as TelegramR
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AboutLinkCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.ProfileActivity
import org.telegram.ui.Components.ProfileGalleryBlurView
import org.telegram.ui.Components.ProfileGalleryView
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
    fun profileLoadingSkeletonKeepsTelegramDimensions() {
        paparazziRule.verify { context ->
            val loading = FlickerLoadingView(context).apply {
                setViewType(FlickerLoadingView.CHECKBOX_TYPE)
                setIsSingleCell(true)
                setItemsCount(2)
            }
            loading.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200f), View.MeasureSpec.AT_MOST),
            )
            assertThat(loading.measuredHeight).isEqualTo((AndroidUtilities.dp(50f) + 1) * 2)
        }
    }

    @Test
    fun anonymousProfileShowsTelegramLoginCta() {
        paparazziRule.snapshot { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val session = ProfileSessionStateCell(context).apply {
                bind(
                    titleText = context.getString(R.string.profile_login_title),
                    subtitleText = context.getString(R.string.profile_login_subtitle),
                    actionText = context.getString(R.string.profile_login_action),
                    danger = false,
                    onAction = {},
                )
            }
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(280f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                addView(
                    session,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        AndroidUtilities.dp(280f),
                    ),
                )
            }
        }
    }

    @Test
    fun anonymousSessionFillsTheProfileViewport() {
        paparazziRule.verify { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val cell = ProfileSessionStateCell(context).apply {
                bottomContentInset = AndroidUtilities.dp(84f)
                bind(
                    titleText = context.getString(R.string.profile_login_title),
                    subtitleText = context.getString(R.string.profile_login_subtitle),
                    actionText = context.getString(R.string.profile_login_action),
                    danger = false,
                    onAction = {},
                )
            }
            val recycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                addView(cell)
            }
            recycler.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(720f), View.MeasureSpec.EXACTLY),
            )
            cell.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            cell.layout(0, 0, cell.measuredWidth, cell.measuredHeight)
            val action = cell.linearLayout.getChildAt(cell.linearLayout.childCount - 1) as TextView
            val actionParams = action.layoutParams as LinearLayout.LayoutParams

            assertThat(kotlin.math.abs(cell.measuredHeight - AndroidUtilities.dp(636f))).isAtMost(1)
            assertThat(actionParams.width).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT)
            assertThat(actionParams.height).isEqualTo(AndroidUtilities.dp(48f))
            assertThat(actionParams.leftMargin).isEqualTo(0)
            assertThat(actionParams.topMargin).isEqualTo(AndroidUtilities.dp(28f))
            assertThat(actionParams.rightMargin).isEqualTo(0)
            assertThat(actionParams.bottomMargin).isEqualTo(AndroidUtilities.dp(4f))
            assertThat(action.paddingLeft).isEqualTo(AndroidUtilities.dp(34f))
            assertThat(action.paddingTop).isEqualTo(0)
            assertThat(action.paddingRight).isEqualTo(AndroidUtilities.dp(34f))
            assertThat(action.paddingBottom).isEqualTo(0)
            assertThat(action.textSize).isEqualTo(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    15f,
                    context.resources.displayMetrics,
                ),
            )
            assertThat(action.measuredWidth).isEqualTo(AndroidUtilities.dp(320f))
            assertThat(action.measuredHeight).isEqualTo(AndroidUtilities.dp(48f))
            assertThat(kotlin.math.abs(action.left + action.right - cell.linearLayout.width)).isAtMost(1)
        }
    }

    @Test
    fun anonymousProfileLoginCtaShrinksInsideNarrowViewport() {
        paparazziRule.verify { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val cell = ProfileSessionStateCell(context).apply {
                bind(
                    titleText = context.getString(R.string.profile_login_title),
                    subtitleText = context.getString(R.string.profile_login_subtitle),
                    actionText = context.getString(R.string.profile_login_action),
                    danger = false,
                    onAction = {},
                )
            }
            val width = AndroidUtilities.dp(320f)
            cell.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(640f), View.MeasureSpec.EXACTLY),
            )
            cell.layout(0, 0, cell.measuredWidth, cell.measuredHeight)
            val action = cell.linearLayout.getChildAt(cell.linearLayout.childCount - 1) as TextView

            assertThat(action.measuredWidth).isEqualTo(AndroidUtilities.dp(288f))
            assertThat(action.left).isAtLeast(0)
            assertThat(action.right).isAtMost(cell.linearLayout.width)
            assertThat(kotlin.math.abs(action.left + action.right - cell.linearLayout.width)).isAtMost(1)
        }
    }

    @Test
    fun expandedProfileGalleryKeepsActionsOutsideSquareImageArea() {
        paparazziRule.verify { context ->
            val width = AndroidUtilities.dp(360f)
            val actionsHeight = AndroidUtilities.dp(74f)
            val blur = ProfileGalleryBlurView(context).apply {
                setSize(actionsHeight)
            }
            val gallery = ProfileGalleryView(context, RecyclerListView(context)).apply {
                setBlurView(blur)
            }
            gallery.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width + actionsHeight, View.MeasureSpec.EXACTLY),
            )
            blur.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            gallery.layout(0, 0, width, width + actionsHeight)

            assertThat(gallery.measuredHeight).isEqualTo(width + actionsHeight)
            assertThat(gallery.currentItemView.measuredWidth).isEqualTo(width)
            assertThat(gallery.currentItemView.measuredHeight).isEqualTo(width)
            assertThat(blur.measuredHeight).isEqualTo(actionsHeight + (AndroidUtilities.dp(64f) * 1.5f).toInt())
            assertThat(blur.translationY).isEqualTo((gallery.height - blur.measuredHeight).toFloat())
        }
    }

    @Test
    fun profileAvatarOwnsGalleryFrameDuringCollapse() {
        paparazziRule.verify { context ->
            val avatar = ProfileActivity.AvatarImageView(context)
            val drawable = ColorDrawable(0xff336699.toInt())

            avatar.setForegroundImageDrawable(ImageReceiver.BitmapHolder(drawable, null, 0))
            avatar.setForegroundAlpha(1f)

            assertThat(avatar.foregroundImageReceiver.drawable).isSameInstanceAs(drawable)
            assertThat(avatar.foregroundAlpha).isEqualTo(1f)

            avatar.clearForeground()
            assertThat(avatar.foregroundAlpha).isEqualTo(0f)
        }
    }

    @Test
    fun profileFeedTabsKeepTelegramIndicatorContract() {
        paparazziRule.snapshot { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val content = ProfileContentLayout(
                context = context,
                baseUrl = "https://linux.do",
                onTopicSelected = { _, _ -> },
                onPageSelected = {},
                onLoadMore = {},
                onRetry = {},
                onRetryLoadMore = {},
                viewportHeightProvider = { AndroidUtilities.dp(50f) },
            )
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(50f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
                addView(
                    content,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        AndroidUtilities.dp(50f),
                    ),
                )
            }
        }
    }

    @Test
    fun profileActionTabsKeepTelegramTextAndIconAlignment() {
        paparazziRule.snapshot { context ->
            Theme.createChatResources(context, false)
            Theme.createProfileResources(context)
            val actions = DiscourseProfileActionsView(
                context,
                AndroidUtilities.dp(74f),
            ).apply {
                setActionsColor(Theme.getColor(Theme.key_profile_actionBackground))
                setActions(
                    listOf(
                        DiscourseProfileActionsView.ActionSpec(
                            DiscourseProfileActionsView.KEY_EDIT_PROFILE,
                            "编辑资料",
                            TelegramR.drawable.filled_profile_edit_24,
                            TelegramR.drawable.outline_profile_edit_24,
                        ),
                        DiscourseProfileActionsView.ActionSpec(
                            DiscourseProfileActionsView.KEY_TOPICS,
                            "我的主题",
                            TelegramR.drawable.tabs_chats_active_24,
                            TelegramR.drawable.tabs_chats_24,
                        ),
                        DiscourseProfileActionsView.ActionSpec(
                            DiscourseProfileActionsView.KEY_REPLIES,
                            "我的回复",
                            TelegramR.drawable.filled_profile_message_24,
                            TelegramR.drawable.outline_profile_message_24,
                        ),
                        DiscourseProfileActionsView.ActionSpec(
                            DiscourseProfileActionsView.KEY_SHARE,
                            "分享资料",
                            TelegramR.drawable.action_share,
                            TelegramR.drawable.msg_share,
                        ),
                    ),
                    false,
                )
                updatePosition(0f, AndroidUtilities.dp(74f).toFloat())
            }
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(74f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
                addView(
                    actions,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        AndroidUtilities.dp(74f),
                    ),
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
            val contentViewportHeight = AndroidUtilities.dp(520f)
            val content = ProfileContentLayout(
                context = context,
                baseUrl = "https://linux.do",
                onTopicSelected = { _, _ -> },
                onPageSelected = selected::add,
                onLoadMore = {},
                onRetry = {},
                onRetryLoadMore = {},
                viewportHeightProvider = { contentViewportHeight },
            )
            content.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(640f), View.MeasureSpec.EXACTLY),
            )
            val profileTabs = content.getChildAt(0) as ScrollSlidingTextTabStrip
            val topicsList = content.currentListView()
            // Upstream SharedMediaLayout draws no own background: the content
            // layout and the feed list stay transparent; white section cards are
            // drawn by the list sections over the gray outer surface.
            assertThat((content.background as ColorDrawable).color)
                .isEqualTo(Color.TRANSPARENT)
            assertThat(topicsList?.background).isNull()
            content.submit(
                ProfileFeedPage.Replies,
                ProfileFeedUiState(
                    items = listOf(
                        TopicSummary(
                            id = 42L,
                            title = "Cached reply",
                            authorUsername = "baba",
                        ),
                    ),
                    isLoading = false,
                ),
            )
            content.selectPage(ProfileFeedPage.Replies, animated = false)

            assertThat(profileTabs.minimumWidth).isEqualTo(0)
            assertThat(profileTabs.measuredHeight).isEqualTo(AndroidUtilities.dp(50f))
            assertThat(profileTabs.getTabsContainer().childCount).isEqualTo(2)
            assertThat(profileTabs.getCurrentTabId()).isEqualTo(ProfileFeedPage.Replies.tabId)
            assertThat(profileTabs.getCurrentPosition()).isEqualTo(ProfileFeedPage.Replies.ordinal)
            assertThat(profileTabs.measuredWidth).isLessThan(AndroidUtilities.dp(200f))
            assertThat(profileTabs.paddingLeft).isEqualTo(AndroidUtilities.dp(6f))
            assertThat(profileTabs.paddingTop).isEqualTo(AndroidUtilities.dp(7f))
            assertThat(profileTabs.paddingRight).isEqualTo(AndroidUtilities.dp(6f))
            assertThat(profileTabs.paddingBottom).isEqualTo(AndroidUtilities.dp(7f))
            assertThat(profileTabs.getTabsContainer().paddingLeft).isEqualTo(AndroidUtilities.dp(7f))
            assertThat(profileTabs.getTabsContainer().paddingRight).isEqualTo(AndroidUtilities.dp(7f))
            assertThat(content.selectedPage()).isEqualTo(ProfileFeedPage.Replies)
            assertThat(content.currentListView()).isNotSameInstanceAs(topicsList)
            assertThat(content.currentListView()?.adapter?.itemCount).isEqualTo(1)
            assertThat(content.currentListView()?.background).isNull()
            assertThat(selected.last()).isEqualTo(ProfileFeedPage.Replies)

            // The initial-load skeleton stays out of the resting peek area:
            // the surface shows the gray background and tab pill only until
            // the section pins, then the in-list skeleton rows fill a card.
            content.selectPage(ProfileFeedPage.Topics, animated = false)
            content.submit(ProfileFeedPage.Topics, ProfileFeedUiState())
            assertThat(content.currentListView()?.adapter?.itemCount).isEqualTo(0)
            content.setPinnedToTop(true)
            assertThat(content.currentListView()?.adapter?.itemCount).isEqualTo(7)
            content.setPinnedToTop(false)
            assertThat(content.currentListView()?.adapter?.itemCount).isEqualTo(0)
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
