package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Paint
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.DiscourseEmoji
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.topic.ui.TOPIC_REACTION_ALL_FILTER_ID
import io.github.baba983.nativedo.feature.topic.ui.TopicUiState
import io.github.baba983.nativedo.feature.topic.ui.isReadPositionOnlyUpdateFrom
import io.github.baba983.nativedo.feature.topic.ui.topicUnreadPostsCount
import io.github.baba983.nativedo.ui.topic.cell.TopicDateCell
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicDetailFragmentTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.density = 1f
        AndroidUtilities.statusBarHeight = 0
        AndroidUtilities.navigationBarHeight = 0
        AndroidUtilities.displaySize.set(400, 800)
    }

    @Test
    fun postOptionsPopupPosition_usesLongPressWindowPointAndClampsToDisplay() {
        assertThat(positionFor(anchorX = 20, anchorY = 100, pressX = 40f, pressY = 180f))
            .isEqualTo(TopicPostOptionsPopupPosition(x = 6, y = 172))
        assertThat(positionFor(anchorX = 20, anchorY = 100, pressX = 220f, pressY = 180f))
            .isEqualTo(TopicPostOptionsPopupPosition(x = 12, y = 172))
        assertThat(positionFor(anchorX = 20, anchorY = 100, pressX = 40f, pressY = 180f, popupHeight = 100))
            .isEqualTo(TopicPostOptionsPopupPosition(x = 6, y = 280))
        assertThat(positionFor(anchorX = 0, anchorY = 0, pressX = 0f, pressY = 0f))
            .isEqualTo(TopicPostOptionsPopupPosition(x = 6, y = 6))
        assertThat(positionFor(anchorX = 0, anchorY = 700, pressX = 320f, pressY = 480f))
            .isEqualTo(TopicPostOptionsPopupPosition(x = 92, y = 444))
    }

    @Test
    fun topicDetailBackground_usesConfiguredTelegramWallpaperFallback() {
        assertThat(Theme.getDefaultColor(Theme.key_chat_wallpaper)).isEqualTo(0xfff1f1f3.toInt())
        assertThat(TopicDetailFragment.topicDetailListBackgroundColor()).isEqualTo(0xfff1f1f3.toInt())
        assertThat(Theme.getDefaultColor(Theme.key_chat_serviceBackground)).isEqualTo(0x6685859e)
        assertThat(Theme.getDefaultColor(Theme.key_chat_serviceBackgroundSelected)).isEqualTo(0x889393af.toInt())
    }

    @Test
    fun richEditorMediaResult_isConsumedOnlyForItsOwnRequest() {
        val fragment = TopicDetailFragment(topicId = 1L)
        val result = Intent().setData(Uri.parse("content://media/picked/1"))

        // the dispatch MainActivity does: a foreign request code must fall through
        assertThat(
            fragment.handleRichEditorActivityResult(
                RICH_EDITOR_MEDIA_REQUEST_CODE,
                Activity.RESULT_OK,
                result,
            ),
        ).isTrue()
        assertThat(
            fragment.handleRichEditorActivityResult(
                RICH_EDITOR_MEDIA_REQUEST_CODE + 1,
                Activity.RESULT_OK,
                result,
            ),
        ).isFalse()
        // a cancelled picker (or one without a sheet) is still ours to consume, and must not throw
        assertThat(
            fragment.handleRichEditorActivityResult(
                RICH_EDITOR_MEDIA_REQUEST_CODE,
                Activity.RESULT_CANCELED,
                null,
            ),
        ).isTrue()
    }

    @Test
    fun wallpaperReadyNotificationInvalidatesFloatingDateDisplayList() {
        val fragment = TopicDetailFragment(topicId = 1L)
        val dateCell = InvalidatingTopicDateCell(RuntimeEnvironment.getApplication())
        TopicDetailFragment::class.java.getDeclaredField("floatingDateView").apply {
            isAccessible = true
            set(fragment, dateCell)
        }
        dateCell.invalidationCount = 0

        assertThat(fragment.onFragmentCreate()).isTrue()
        try {
            NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.didSetNewWallpapper,
            )
            assertThat(dateCell.invalidationCount).isGreaterThan(0)
        } finally {
            NotificationCenter.getGlobalInstance().removeObserver(
                fragment,
                NotificationCenter.didSetNewWallpapper,
            )
        }
    }

    @Test
    fun pageDownCounterFontKeepsDecimalDigitsTabular() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = AndroidUtilities.bold()
            textSize = 13f
        }
        val widths = ('0'..'9').map { digit -> paint.measureText(digit.toString()) }

        assertThat(widths.maxOrNull()!! - widths.minOrNull()!!).isLessThan(0.01f)
    }

    private class InvalidatingTopicDateCell(context: Context) : TopicDateCell(context, true) {
        var invalidationCount = 0

        override fun invalidate() {
            invalidationCount += 1
            super.invalidate()
        }
    }

    @Test
    fun topicDetailBlurSources_matchChatActivityWallpaperVsListSplit() {
        // ChatActivity: FadeView -> navbarContentSourceWallpaper; ActionBar glass ->
        // glassBackgroundSourceRenderNode / Frosted (live list + noise suppressor).
        assertThat(topicDetailTopFadeSourceKind())
            .isEqualTo(TopicDetailBlurSourceKind.WALLPAPER_COLOR)
        assertThat(topicDetailActionBarGlassSourceKind(liquidGlassEnabled = false))
            .isEqualTo(TopicDetailBlurSourceKind.LIST_RENDER_NODE_FROSTED)
        assertThat(topicDetailActionBarGlassSourceKind(liquidGlassEnabled = true))
            .isEqualTo(TopicDetailBlurSourceKind.LIST_RENDER_NODE_GLASS)
    }


    @Test
    fun swipeReply_startsOnlyWhenCompetingTopicInteractionsAreIdle() {
        assertThat(topicDetailCanStartSwipeReply(false, false, false, false)).isTrue()
        assertThat(topicDetailCanStartSwipeReply(true, false, false, false)).isFalse()
        assertThat(topicDetailCanStartSwipeReply(false, true, false, false)).isFalse()
        assertThat(topicDetailCanStartSwipeReply(false, false, true, false)).isFalse()
        assertThat(topicDetailCanStartSwipeReply(false, false, false, true)).isFalse()
    }

    @Test
    fun reactionAllFilterMergesRecentUsersAndDeduplicatesByUsername() {
        val groups = listOf(
            PostReactionUsersGroup(
                id = "heart",
                users = listOf(
                    PostReactionUser(username = "baba", createdAt = "2026-05-01T01:00:00Z"),
                    PostReactionUser(username = "alice", createdAt = "2026-05-01T03:00:00Z"),
                ),
            ),
            PostReactionUsersGroup(
                id = "+1",
                users = listOf(
                    PostReactionUser(username = "baba", createdAt = "2026-05-01T04:00:00Z"),
                    PostReactionUser(username = "carol", createdAt = "2026-05-01T02:00:00Z"),
                ),
            ),
        )

        assertThat(topicReactionUsersForFilter(groups, TOPIC_REACTION_ALL_FILTER_ID).map { it.username })
            .containsExactly("baba", "alice", "carol")
            .inOrder()
        assertThat(topicReactionUsersForFilter(groups, "heart").map { it.username })
            .containsExactly("baba", "alice")
            .inOrder()
    }

    @Test
    fun replyFooterUsesServerReplyCountInsteadOfLoadedPreviewSize() {
        val post = io.github.baba983.nativedo.core.model.Post(
            id = 1001L,
            postNumber = 1,
            authorUsername = "baba",
            replyCount = 80,
            cookedHtml = "<p>source</p>",
        )

        assertThat(topicReplyFooterCount(post, loadedPreviewCount = 0)).isEqualTo(80)
        assertThat(topicReplyFooterCount(post.copy(replyCount = 0), loadedPreviewCount = 3)).isEqualTo(3)
        assertThat(topicReplySkeletonRows(totalCount = 1)).isEqualTo(1)
        assertThat(topicReplySkeletonRows(totalCount = 80)).isEqualTo(6)
    }

    @Test
    fun topicDetailLoadMore_requiresUserScrollNearEnd() {
        assertThat(
            topicDetailShouldLoadMoreFromScroll(
                dy = 12,
                userScrollActive = false,
                lastVisibleItemPosition = 18,
                itemCount = 20,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldLoadMoreFromScroll(
                dy = 12,
                userScrollActive = true,
                lastVisibleItemPosition = 18,
                itemCount = 20,
            ),
        ).isTrue()
        assertThat(
            topicDetailShouldLoadMoreFromScroll(
                dy = -4,
                userScrollActive = true,
                lastVisibleItemPosition = 18,
                itemCount = 20,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldLoadMoreFromScroll(
                dy = 12,
                userScrollActive = true,
                lastVisibleItemPosition = 10,
                itemCount = 20,
            ),
        ).isFalse()
    }

    @Test
    fun topicDetailLoadPrevious_requiresUpwardUserScrollNearStart() {
        assertThat(
            topicDetailShouldLoadPreviousFromScroll(
                dy = -12,
                userScrollActive = true,
                firstVisibleItemPosition = 2,
            ),
        ).isTrue()
        assertThat(
            topicDetailShouldLoadPreviousFromScroll(
                dy = -12,
                userScrollActive = false,
                firstVisibleItemPosition = 2,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldLoadPreviousFromScroll(
                dy = 12,
                userScrollActive = true,
                firstVisibleItemPosition = 2,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldLoadPreviousFromScroll(
                dy = -12,
                userScrollActive = true,
                firstVisibleItemPosition = 8,
            ),
        ).isFalse()
    }

    @Test
    fun repliesSheetLoadMore_countsTelegramPaddingRowInOuterAdapter() {
        assertThat(topicRepliesSheetShouldLoadMore(1, true, 5, outerItemCount = 8)).isTrue()
        assertThat(topicRepliesSheetShouldLoadMore(1, true, 4, outerItemCount = 8)).isFalse()
        assertThat(topicRepliesSheetShouldLoadMore(0, true, 5, outerItemCount = 8)).isFalse()
        assertThat(topicRepliesSheetShouldLoadMore(1, false, 5, outerItemCount = 8)).isFalse()
    }

    @Test
    fun topicDetailInitialContentAnchor_onlyBeforeUserScrollAndJump() {
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = false,
                userHasScrolled = false,
                isLoading = false,
                isRefreshing = false,
                postsEmpty = false,
                errorMessage = null,
                hasJumpEvent = false,
            ),
        ).isTrue()
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = false,
                userHasScrolled = true,
                isLoading = false,
                isRefreshing = false,
                postsEmpty = false,
                errorMessage = null,
                hasJumpEvent = false,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = true,
                userHasScrolled = false,
                isLoading = false,
                isRefreshing = false,
                postsEmpty = false,
                errorMessage = null,
                hasJumpEvent = false,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = false,
                userHasScrolled = false,
                isLoading = false,
                isRefreshing = false,
                postsEmpty = false,
                errorMessage = null,
                hasJumpEvent = true,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = false,
                userHasScrolled = false,
                isLoading = false,
                isRefreshing = true,
                postsEmpty = false,
                errorMessage = null,
                hasJumpEvent = false,
            ),
        ).isFalse()
        assertThat(
            topicDetailShouldAnchorInitialContent(
                initialContentAnchored = false,
                userHasScrolled = false,
                isLoading = true,
                isRefreshing = false,
                postsEmpty = true,
                errorMessage = null,
                hasJumpEvent = false,
            ),
        ).isFalse()
    }

    @Test
    fun topicInitialReadAnchorPrefersThePostMatchingMergedReadNumber() {
        val posts = listOf(
            Post(id = 1003L, postNumber = 3, authorUsername = "alice", cookedHtml = ""),
            Post(id = 1004L, postNumber = 4, authorUsername = "alice", cookedHtml = ""),
        )

        assertThat(
            topicInitialReadAnchorPostId(
                posts = posts,
                lastReadPostNumber = 4,
                lastReadPostId = 1003L,
            ),
        ).isEqualTo(1004L)
        assertThat(
            topicInitialReadAnchorPostId(
                posts = posts,
                lastReadPostNumber = 2,
                lastReadPostId = 1003L,
            ),
        ).isEqualTo(1003L)
        assertThat(
            topicInitialReadAnchorPostId(
                posts = posts,
                lastReadPostNumber = 0,
                lastReadPostId = 1003L,
            ),
        ).isNull()
    }

    @Test
    fun topicDetailListShowsTopicHeader_whenLoadedPostsWithoutError() {
        assertThat(
            topicDetailListShowsTopicHeader(
                isLoading = false,
                postsEmpty = false,
                errorMessage = null,
            ),
        ).isTrue()
        assertThat(
            topicDetailListShowsTopicHeader(
                isLoading = true,
                postsEmpty = true,
                errorMessage = null,
            ),
        ).isFalse()
        assertThat(
            topicDetailListShowsTopicHeader(
                isLoading = false,
                postsEmpty = true,
                errorMessage = null,
            ),
        ).isFalse()
        assertThat(
            topicDetailListShowsTopicHeader(
                isLoading = false,
                postsEmpty = false,
                errorMessage = "network",
            ),
        ).isFalse()
    }

    @Test
    fun postBookmarkFeedbackMessage_usesPreviousBookmarkState() {
        assertThat(TopicDetailFragment.postBookmarkFeedbackMessageRes(wasBookmarked = false))
            .isEqualTo(io.github.baba983.nativedo.R.string.app_browser_bookmark_saved_message)
        assertThat(TopicDetailFragment.postBookmarkFeedbackMessageRes(wasBookmarked = true))
            .isEqualTo(io.github.baba983.nativedo.R.string.app_browser_bookmark_removed_message)
    }

    @Test
    fun postBookmarkIcon_usesArticleViewerBookmarkIconForAddAction() {
        assertThat(TopicDetailFragment.postBookmarkIconRes(wasBookmarked = false))
            .isEqualTo(org.telegram.messenger.R.drawable.msg_saved)
        assertThat(TopicDetailFragment.postBookmarkIconRes(wasBookmarked = true))
            .isEqualTo(org.telegram.messenger.R.drawable.menu_browser_bookmarks)
    }

    @Test
    fun postActionIcons_matchChatActivityMessageMenuIcons() {
        assertThat(TopicDetailFragment.postReplyIconRes())
            .isEqualTo(org.telegram.messenger.R.drawable.menu_reply)
        assertThat(TopicDetailFragment.postCopyLinkIconRes())
            .isEqualTo(org.telegram.messenger.R.drawable.msg_link)
        assertThat(TopicDetailFragment.postSelectTextIconRes())
            .isEqualTo(org.telegram.messenger.R.drawable.msg_select)
    }

    @Test
    fun postOptionsActions_matchTelegramLongPressMenuOrder() {
        val addActions = TopicDetailFragment.postOptionsActions(isBookmarked = false)
        assertThat(addActions.map { it.kind })
            .containsExactly(
                TopicPostActionKind.REPLY,
                TopicPostActionKind.SELECT_TEXT,
                TopicPostActionKind.COPY_LINK,
                TopicPostActionKind.BOOKMARK,
            )
            .inOrder()
        assertThat(addActions.last().titleRes)
            .isEqualTo(io.github.baba983.nativedo.R.string.app_browser_add_bookmark)

        val removeActions = TopicDetailFragment.postOptionsActions(isBookmarked = true)
        assertThat(removeActions.last().titleRes)
            .isEqualTo(io.github.baba983.nativedo.R.string.app_browser_remove_bookmark)
        assertThat(removeActions.last().iconRes)
            .isEqualTo(org.telegram.messenger.R.drawable.menu_browser_bookmarks)
    }

    @Test
    fun postOptionsReactionIds_prioritizeCurrentThenValidThenVisibleCounts() {
        assertThat(
            TopicDetailFragment.postOptionsReactionIds(
                currentUserReaction = "clap",
                validReactionIds = listOf("heart", "clap"),
                postReactions = listOf(
                    PostReaction("laughing", 2, null, false),
                    PostReaction("heart", 1, null, true),
                ),
                fallbackReactionIds = listOf("heart", "+1"),
            ),
        ).containsExactly("clap", "heart", "laughing").inOrder()

        assertThat(
            TopicDetailFragment.postOptionsReactionIds(
                currentUserReaction = null,
                validReactionIds = emptyList(),
                postReactions = emptyList(),
                fallbackReactionIds = listOf("heart", "+1"),
            ),
        ).containsExactly("heart", "+1").inOrder()
    }

    @Test
    fun pageDownCountUsesCanonicalStreamInsteadOfAdapterRowDistance() {
        val stream = listOf(1001L, 1002L, 1003L, 1004L)

        assertThat(topicPostsBelowViewportOrNull(stream, 1001L)).isEqualTo(3)
        assertThat(topicPostsBelowViewportOrNull(stream, 1003L)).isEqualTo(1)
        assertThat(topicPostsBelowViewportOrNull(stream, 1004L)).isEqualTo(0)
        assertThat(topicPostsBelowViewportOrNull(stream, 9999L)).isNull()
        assertThat(topicPostsBelowViewportOrNull(stream, null)).isNull()
        assertThat(topicPostsAboveViewportOrNull(stream, 1001L)).isEqualTo(0)
        assertThat(topicPostsAboveViewportOrNull(stream, 1003L)).isEqualTo(2)
        assertThat(topicPostsAboveViewportOrNull(stream, 9999L)).isNull()
    }

    @Test
    fun pageDownVisibilityFollowsTelegramScrollMachine() {
        // Before the first bottom-reach the button invites the jump; after that the
        // scroll machine (canShow from totalDy threshold) owns visibility.

        assertThat(topicShouldShowPageDown(5, selectionActive = false, canShowFromScroll = true)).isTrue()
        assertThat(topicShouldShowPageDown(5, selectionActive = false, canShowFromScroll = false)).isFalse()
        assertThat(topicShouldShowPageDown(5, selectionActive = true, canShowFromScroll = true)).isFalse()
        assertThat(topicShouldShowPageDown(0, selectionActive = false, canShowFromScroll = true)).isFalse()
        assertThat(topicShouldShowReturnToTop(2, selectionActive = false)).isTrue()
        assertThat(topicShouldShowReturnToTop(0, selectionActive = false)).isFalse()
        assertThat(topicShouldShowReturnToTop(2, selectionActive = true)).isFalse()
    }

    @Test
    fun pageDownUnreadCountUsesSessionAndDiscourseReadPosition() {
        assertThat(topicUnreadPostsCount(30, 0, canTrackReadPosition = false)).isEqualTo(0)
        assertThat(topicUnreadPostsCount(30, 0, canTrackReadPosition = true)).isEqualTo(30)
        assertThat(topicUnreadPostsCount(30, 12, canTrackReadPosition = true)).isEqualTo(18)
        assertThat(topicUnreadPostsCount(30, 34, canTrackReadPosition = true)).isEqualTo(0)
    }

    @Test
    fun readPositionOnlyStateUpdateCanSkipFullTopicRender() {
        val previous = TopicUiState(highestPostNumber = 8, lastReadPostNumber = 3, lastReadPostId = 1003L)

        assertThat(TopicUiState(highestPostNumber = 8, lastReadPostNumber = 4, lastReadPostId = 1004L)
            .isReadPositionOnlyUpdateFrom(previous)).isTrue()
        assertThat(TopicUiState(highestPostNumber = 9, lastReadPostNumber = 4, lastReadPostId = 1004L)
            .isReadPositionOnlyUpdateFrom(previous)).isFalse()
    }

    @Test
    fun pageDownTranslationTracksComposerAndKeyboardInset() {
        assertThat(topicPageDownControlsBottomOffset(panelHeight = 44, panelBottomInset = 0))
            .isEqualTo(AndroidUtilities.dp(48f))
        assertThat(topicPageDownControlsBottomOffset(panelHeight = 44, panelBottomInset = 300))
            .isEqualTo(AndroidUtilities.dp(48f) + 300)
    }

    @Test
    fun postReactionPickerIds_useLinuxDoDefaultReactionsWhenServerListIsMissing() {
        assertThat(
            TopicDetailFragment.postReactionPickerIds(
                initialReactionId = null,
                validReactionIds = emptyList(),
                postReactions = emptyList(),
                fallbackReactionIds = TopicDetailFragment.defaultReactionIds(),
            ),
        ).containsExactly(
            "heart",
            "+1",
            "laughing",
            "open_mouth",
            "clap",
            "confetti_ball",
            "hugs",
            "distorted_face",
            "tieba_087",
            "bili_057",
        ).inOrder()
    }

    @Test
    fun selectionCopyFeedback_usesTelegramTextCopiedMessage() {
        assertThat(TopicDetailFragment.selectionCopyFeedbackMessageRes())
            .isEqualTo(org.telegram.messenger.R.string.TextCopied)
    }

    @Test
    fun postCopyLinkBulletin_showsOnTopLikeArticleViewer() {
        assertThat(TopicDetailFragment.topicCopyLinkBulletinOnTop()).isTrue()
    }

    @Test
    fun reactionPickerRail_usesTelegramHorizontalContainerGeometry() {
        assertThat(TopicDetailFragment.reactionPickerRailHeightDp()).isEqualTo(48)
        assertThat(TopicDetailFragment.reactionPickerRailButtonWidthDp()).isEqualTo(36)
        assertThat(TopicDetailFragment.reactionPickerRailHorizontalPaddingDp()).isEqualTo(8)
        assertThat(TopicDetailFragment.reactionPickerRailEdgeShadowWidthDp()).isEqualTo(24)
        assertThat(TopicDetailFragment.reactionPickerRailIconSizeDp()).isEqualTo(30)
        assertThat(TopicDetailFragment.reactionPickerRailSelectorSizeDp()).isEqualTo(30)
        assertThat(TopicDetailFragment.reactionPickerRailWidthDp(0)).isEqualTo(200)
        assertThat(TopicDetailFragment.reactionPickerRailWidthDp(3)).isEqualTo(200)
        assertThat(TopicDetailFragment.reactionPickerRailWidthDp(5)).isEqualTo(204)
        assertThat(TopicDetailFragment.reactionPickerRailWidthDp(12)).isEqualTo(318)
        assertThat(TopicDetailFragment.reactionPickerRailMaxVisibleReactions()).isEqualTo(5)
        assertThat(TopicDetailFragment.reactionPickerRailShownItemCount(reactionCount = 5, showMoreButton = true)).isEqualTo(5)
        assertThat(TopicDetailFragment.reactionPickerRailShownItemCount(reactionCount = 6, showMoreButton = true)).isEqualTo(6)
        assertThat(TopicDetailFragment.reactionPickerRailShownItemCount(reactionCount = 12, showMoreButton = true)).isEqualTo(6)
        assertThat(TopicDetailFragment.reactionPickerRailShownItemCount(reactionCount = 12, showMoreButton = false)).isEqualTo(12)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelColumns(10)).isEqualTo(5)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelWidthDp(10)).isEqualTo(204)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelHeightDp(10)).isEqualTo(82)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelInterItemGapDp()).isEqualTo(2)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelCornerRadiusDp()).isEqualTo(8)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelIconSizeDp()).isEqualTo(30)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelSelectorSizeDp()).isEqualTo(30)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelTargetTopOffsetDp()).isEqualTo(2)
    }

    @Test
    fun reactionPickerRailEnterAnimation_matchesReactionsContainerLayout() {
        assertThat(TopicDetailFragment.reactionPickerRailEnterDurationMs()).isEqualTo(250L)
        assertThat(TopicDetailFragment.reactionPickerRailEnterOvershootTension()).isEqualTo(0.5f)
        assertThat(TopicDetailFragment.reactionPickerRailClipProgress()).isEqualTo(0.25f)
        assertThat(TopicDetailFragment.reactionPickerRailBigBubbleRadiusDp()).isEqualTo(8)
        assertThat(TopicDetailFragment.reactionPickerRailSmallBubbleRadiusDp()).isEqualTo(4)
        assertThat(TopicDetailFragment.reactionPickerRailBigBubbleOffsetDp()).isEqualTo(36)
        assertThat(TopicDetailFragment.reactionPickerRailBigBubbleShadowPadDp()).isEqualTo(3)
        assertThat(TopicDetailFragment.reactionPickerRailSmallBubbleShadowPadDp()).isEqualTo(-1)
        assertThat(TopicDetailFragment.reactionPickerRailPressedStartDelayMs()).isEqualTo(ViewConfiguration.getLongPressTimeout().toLong())
        assertThat(TopicDetailFragment.reactionPickerRailLongPressSelectProgress()).isEqualTo(0.8f)
        assertThat(TopicDetailFragment.reactionPickerRailShouldSelectOnRelease(longPressStarted = false, pressedProgress = 0f)).isTrue()
        assertThat(TopicDetailFragment.reactionPickerRailShouldSelectOnRelease(longPressStarted = true, pressedProgress = 0.8f)).isFalse()
        assertThat(TopicDetailFragment.reactionPickerRailShouldSelectOnRelease(longPressStarted = true, pressedProgress = 0.81f)).isTrue()
        assertThat(TopicDetailFragment.reactionPickerRailShouldCancelPressByMove(dx = 16f, dy = 0f, touchSlop = 8f)).isFalse()
        assertThat(TopicDetailFragment.reactionPickerRailShouldCancelPressByMove(dx = 16.1f, dy = 0f, touchSlop = 8f)).isTrue()
        assertThat(TopicDetailFragment.reactionPickerRailShouldCancelPressByMove(dx = 0f, dy = -16.1f, touchSlop = 8f)).isTrue()
        assertThat(TopicDetailFragment.reactionPickerRailBubbleProgress(0f)).isEqualTo(0f)
        assertThat(TopicDetailFragment.reactionPickerRailBubbleProgress(0.25f)).isEqualTo(0f)
        assertThat(TopicDetailFragment.reactionPickerRailBubbleProgress(0.5f)).isWithin(0.0001f).of(1f / 3f)
        assertThat(TopicDetailFragment.reactionPickerRailBubbleProgress(1f)).isEqualTo(1f)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelEnterDurationMs()).isEqualTo(350L)
        assertThat(TopicDetailFragment.reactionPickerExpandedPanelDismissDurationMs()).isEqualTo(220L)
    }

    @Test
    fun reactionEffectMoveOverlay_matchesTelegramOnlyMoveAnimationSize() {
        assertThat(TopicDetailFragment.reactionEffectOnlyMoveSizeDp()).isEqualTo(34)
        assertThat(TopicDetailFragment.reactionEffectShortSizeDp()).isEqualTo(80)
        assertThat(TopicDetailFragment.reactionEffectMoveDurationMs()).isEqualTo(350L)
    }

    @Test
    fun replyComposerEmojiMarkup_usesDiscourseEmojiSyntax() {
        assertThat(topicReplyComposerEmojiMarkup("heart")).isEqualTo(":heart:")
        assertThat(topicReplyComposerEmojiMarkup("+1")).isEqualTo(":+1:")
        assertThat(topicReplyComposerEmojiMarkup("confetti ball")).isEqualTo(":confetti_ball:")
        assertThat(topicReplyComposerEmojiMarkup(":laughing:")).isEqualTo(":laughing:")
    }

    @Test
    fun replyComposerEmojiInsertion_addsWordBoundarySpaces() {
        assertThat(topicReplyComposerEmojiInsertionText("hello", 5, 5, "heart"))
            .isEqualTo(" :heart:")
        assertThat(topicReplyComposerEmojiInsertionText("hello world", 5, 5, "heart"))
            .isEqualTo(" :heart:")
        assertThat(topicReplyComposerEmojiInsertionText("helloworld", 5, 5, "heart"))
            .isEqualTo(" :heart: ")
        assertThat(topicReplyComposerEmojiInsertionText("hello", 0, 5, "+1"))
            .isEqualTo(":+1:")
    }

    @Test
    fun replyComposerMediaInsertion_putsTheImageOnItsOwnLine() {
        // mid-line: a newline first, and a trailing one so typing continues below the image
        assertThat(topicReplyComposerMediaInsertionText("hello", 5, 5, "![a.png](https://x/a.png)"))
            .isEqualTo("\n![a.png](https://x/a.png)\n")
        // at a line start: no leading newline
        assertThat(topicReplyComposerMediaInsertionText("hello\n", 6, 6, "![a.png](https://x/a.png)"))
            .isEqualTo("![a.png](https://x/a.png)\n")
        // an empty draft starts the line
        assertThat(topicReplyComposerMediaInsertionText("", 0, 0, "![a.png](https://x/a.png)"))
            .isEqualTo("![a.png](https://x/a.png)\n")
        // a selection is replaced, and the markdown still owns its line
        assertThat(topicReplyComposerMediaInsertionText("old text", 0, 8, "![a.png](https://x/a.png)"))
            .isEqualTo("![a.png](https://x/a.png)\n")
    }

    @Test
    fun replyComposerMediaMarkup_usesTheSitesUrlAndAReadableAlt() {
        assertThat(topicReplyComposerMediaMarkup("screenshot.png", "https://linux.do/uploads/a.png"))
            .isEqualTo("![screenshot.png](https://linux.do/uploads/a.png)")
        // an alt that would break the image syntax, and a blank one
        assertThat(topicReplyComposerMediaMarkup("a]b\nc", "https://linux.do/u.png"))
            .isEqualTo("![ab c](https://linux.do/u.png)")
        assertThat(topicReplyComposerMediaMarkup("", "https://linux.do/u.png"))
            .isEqualTo("![image](https://linux.do/u.png)")
        assertThat(topicReplyComposerMediaMarkup("a", " ")).isEmpty()
    }

    @Test
    fun replyComposerEmojiButton_togglesLikeChatActivityEnterView() {
        assertThat(topicReplyComposerEmojiButtonAction(isEmojiPopupShowing = false))
            .isEqualTo(TopicReplyComposerEmojiButtonAction.SHOW_EMOJI_POPUP)
        assertThat(topicReplyComposerEmojiButtonAction(isEmojiPopupShowing = true))
            .isEqualTo(TopicReplyComposerEmojiButtonAction.HIDE_POPUP_AND_OPEN_KEYBOARD)
    }

    @Test
    fun replyComposerEmojiPanelIgnoresStaleImeUntilKeyboardWasExplicitlyRequested() {
        assertThat(
            topicReplyComposerShouldHideEmojiPanelForKeyboard(
                keyboardVisible = true,
                emojiPanelShowing = true,
                waitingForKeyboardOpenAfterEmoji = false,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldHideEmojiPanelForKeyboard(
                keyboardVisible = true,
                emojiPanelShowing = true,
                waitingForKeyboardOpenAfterEmoji = true,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldHideEmojiPanelForKeyboard(
                keyboardVisible = false,
                emojiPanelShowing = true,
                waitingForKeyboardOpenAfterEmoji = true,
            ),
        ).isFalse()
    }

    @Test
    fun collapsedTitleWaitsUntilSearchFieldTransitionIsFinished() {
        assertThat(topicDetailShouldUpdateCollapsedTitle(searchOpen = false, searchFieldVisible = false)).isTrue()
        assertThat(topicDetailShouldUpdateCollapsedTitle(searchOpen = true, searchFieldVisible = true)).isFalse()
        assertThat(topicDetailShouldUpdateCollapsedTitle(searchOpen = false, searchFieldVisible = true)).isFalse()
    }

    @Test
    fun identitySubtitle_formatsRepliesViewsAndOptionalTag() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertThat(
            topicDetailIdentitySubtitle(context, totalPosts = 12, viewCount = 340, tags = listOf("开发")),
        ).contains("开发")
        assertThat(
            topicDetailIdentitySubtitle(context, totalPosts = 12, viewCount = 340, tags = emptyList()),
        ).isNotEmpty()
        assertThat(
            topicDetailIdentityDisplayName(
                firstPost = Post(id = 1, authorUsername = "baba", authorDisplayName = "Baba", cookedHtml = ""),
                fallbackTitle = "Topic",
            ),
        ).isEqualTo("Baba")
        assertThat(
            topicDetailIdentityDisplayName(
                firstPost = Post(id = 1, authorUsername = "baba", cookedHtml = ""),
                fallbackTitle = "Topic",
            ),
        ).isEqualTo("baba")
    }

    @Test
    fun initialTopicIdentityUsesListAvatarWithSharedCacheKey() {
        val initialIdentity = TopicSummary(
            id = 1,
            title = "Topic",
            authorUsername = "initial-author",
            authorAvatarTemplate = "/user_avatar/linux.do/initial/{size}/1.png",
        ).toTopicInitialIdentity()
        assertThat(topicDetailIdentityDisplayName(null, "Topic", initialIdentity)).isEqualTo("initial-author")
        assertThat(topicDetailIdentityAvatarUrl("https://linux.do", null, initialIdentity))
            .isEqualTo("https://linux.do/user_avatar/linux.do/initial/96/1.png")
    }

    @Test
    fun replyComposerEmojiIcon_switchesSmileAndKeyboardLikeChatActivityEnterView() {
        assertThat(topicReplyComposerEmojiIconKind(isEmojiPopupShowing = false))
            .isEqualTo(ComposerIconKind.Smile)
        assertThat(topicReplyComposerEmojiIconKind(isEmojiPopupShowing = true))
            .isEqualTo(ComposerIconKind.Keyboard)
    }

    @Test
    fun replyComposerSendButtonState_matchesChatActivityEnterViewScaleAlphaContract() {
        assertThat(topicReplyComposerSendButtonState(hasText = false, isSubmitting = false))
            .isEqualTo(TopicReplyComposerSendButtonState(visible = false, enabled = false, alpha = 0f, scale = 0.1f))
        assertThat(topicReplyComposerSendButtonState(hasText = true, isSubmitting = false))
            .isEqualTo(TopicReplyComposerSendButtonState(visible = true, enabled = true, alpha = 1f, scale = 1f))
        assertThat(topicReplyComposerSendButtonState(hasText = true, isSubmitting = true))
            .isEqualTo(TopicReplyComposerSendButtonState(visible = true, enabled = false, alpha = 1f, scale = 1f))
    }

    @Test
    fun replyComposerDefaultHeight_matchesChatActivityEnterView() {
        assertThat(topicReplyComposerDefaultHeightDp()).isEqualTo(44)
    }

    @Test
    fun replyComposerSendButtonWidth_matchesChatActivityEnterViewContainer() {
        assertThat(topicReplyComposerSendButtonWidthDp()).isEqualTo(100)
    }

    @Test
    fun replyComposerSendButtonIcon_usesTelegramSendPlaneDrawable() {
        assertThat(topicReplyComposerSendButtonIconRes()).isEqualTo(org.telegram.messenger.R.drawable.send_plane_24)
    }

    @Test
    fun replyComposerSendButtonLoadingRadius_matchesChatActivityEnterView() {
        assertThat(topicReplyComposerSendButtonLoadingRadiusDp()).isEqualTo(8.66f)
    }

    @Test
    fun replyComposerSendButtonTouchDown_matchesChatActivityEnterViewHitRegion() {
        assertThat(topicReplyComposerSendButtonShouldHandleDown(63.9f, 20f, 100, 44, 36, 36)).isFalse()
        assertThat(topicReplyComposerSendButtonShouldHandleDown(64f, 7.9f, 100, 44, 36, 36)).isFalse()
        assertThat(topicReplyComposerSendButtonShouldHandleDown(64f, 8f, 100, 44, 36, 36)).isTrue()
        assertThat(topicReplyComposerSendButtonShouldHandleDown(99f, 43f, 100, 44, 36, 36)).isTrue()
        assertThat(topicReplyComposerSendButtonShouldHandleDown(Float.NaN, 43f, 100, 44, 36, 36)).isFalse()
    }

    @Test
    fun replyComposerInitialSendButtonVisibility_matchesChatActivityEnterView() {
        assertThat(topicReplyComposerInitialSendButtonVisibility()).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun replyComposerIconButtonChrome_matchesChatActivityEnterViewEmojiButton() {
        assertThat(topicReplyComposerIconButtonPaddingDp()).isEqualTo(7.5f)
        assertThat(topicReplyComposerIconButtonSelectorRadiusDp()).isEqualTo(19f)
        assertThat(topicReplyComposerIconButtonSelectorInsetWDp()).isEqualTo(1f)
        assertThat(topicReplyComposerIconButtonSelectorInsetHDp()).isEqualTo(3f)
        // the block editor's entry button sits next to the emoji one (2dp inset + 44dp button)
        assertThat(topicReplyComposerRichEditorButtonLeftMarginDp()).isEqualTo(46f)
    }

    @Test
    fun replyComposerInputFieldMargins_matchChatActivityEnterView() {
        assertThat(topicReplyComposerTextFieldBottomPaddingDp()).isEqualTo(0f)
        // 96, not upstream's 52: our composer carries the block editor's entry button next to the
        // emoji one (upstream opens the editor from the attach sheet instead, so its input field
        // starts right after the emoji button).
        assertThat(topicReplyComposerEditTextLeftMarginDp()).isEqualTo(96f)
        assertThat(topicReplyComposerEditTextRightMarginDp()).isEqualTo(50f)
    }

    @Test
    fun replyComposerBackground_usesChatActivityEnterViewFullWidthPanel() {
        assertThat(topicReplyComposerPanelHorizontalInsetDp()).isEqualTo(0f)
        assertThat(topicReplyComposerPanelBottomInsetDp()).isEqualTo(0f)
        assertThat(topicReplyComposerPanelRadiusDp()).isEqualTo(0f)
        assertThat(topicInputIslandBottomGapDp()).isEqualTo(9f)
    }

    @Test
    fun replyComposerEnterViewClip_usesInputFieldHeightAsTelegramSeparator() {
        assertThat(topicReplyComposerEnterViewSeparatorY(containerHeight = 100, inputFieldHeight = 44f))
            .isEqualTo(56f)
        assertThat(topicReplyComposerEnterViewSeparatorY(containerHeight = 100, inputFieldHeight = 68f))
            .isEqualTo(32f)
        assertThat(topicReplyComposerEnterViewSeparatorY(containerHeight = 40, inputFieldHeight = 68f))
            .isEqualTo(0f)
        assertThat(topicReplyComposerEnterViewSeparatorY(containerHeight = 100, inputFieldHeight = 0f))
            .isEqualTo(56f)
    }

    @Test
    fun replyComposerIconSwitchDuration_matchesChatActivityEnterViewIconStateChange() {
        assertThat(topicReplyComposerIconSwitchDurationMs()).isEqualTo(200L)
    }

    @Test
    fun replyComposerIconTransition_usesChatActivityEnterViewAnimatedIconStates() {
        assertThat(topicReplyComposerIconTransitState(ComposerIconKind.Smile, ComposerIconKind.Keyboard))
            .isEqualTo(ComposerIconTransitState.SMILE_TO_KEYBOARD)
        assertThat(topicReplyComposerIconTransitState(ComposerIconKind.Keyboard, ComposerIconKind.Smile))
            .isEqualTo(ComposerIconTransitState.KEYBOARD_TO_SMILE)
        assertThat(topicReplyComposerIconTransitState(null, ComposerIconKind.Smile))
            .isNull()
    }

    @Test
    fun replyComposerReplyHeaderHeight_matchesChatActivityReplyPanel() {
        assertThat(topicReplyComposerTopViewHeightDp()).isEqualTo(48)
        assertThat(topicReplyComposerReplyHeaderHeightDp()).isEqualTo(46)
    }

    @Test
    fun replyComposerTopViewTiming_matchesChatActivityEnterView() {
        assertThat(topicReplyComposerTopViewShowDelayMs()).isEqualTo(200L)
        assertThat(topicReplyComposerTopViewEnterDurationMs()).isEqualTo(250L)
        assertThat(topicReplyComposerTopViewHideDurationMs()).isEqualTo(250L)
        assertThat(topicReplyComposerTopViewEnterDurationMs()).isEqualTo(topicReplyComposerEditTextBoundsAnimationDurationMs())
        assertThat(topicReplyComposerTopViewHideDurationMs()).isEqualTo(topicReplyComposerEditTextBoundsAnimationDurationMs())
        assertThat(topicReplyComposerShouldFlushDelayedTopView(keyboardVisible = true, showTopViewPending = true)).isTrue()
        assertThat(topicReplyComposerShouldFlushDelayedTopView(keyboardVisible = false, showTopViewPending = true)).isFalse()
        assertThat(topicReplyComposerShouldFlushDelayedTopView(keyboardVisible = true, showTopViewPending = false)).isFalse()
        assertThat(topicReplyComposerTopViewTranslationY(headerHeightPx = 48, visibilityProgress = 0f)).isEqualTo(48f)
        assertThat(topicReplyComposerTopViewTranslationY(headerHeightPx = 48, visibilityProgress = 0.5f)).isEqualTo(24f)
        assertThat(topicReplyComposerTopViewTranslationY(headerHeightPx = 48, visibilityProgress = 1f)).isEqualTo(0f)
    }

    @Test
    fun replyComposerEmojiButton_ignoresDuringKeyboardAnimationOrKeyboardOpenWait() {
        assertThat(topicReplyComposerKeyboardOpenAfterEmojiWaitMs()).isEqualTo(200L)
        assertThat(topicReplyComposerKeyboardHeightPreferenceKey()).isAnyOf("kbd_height", "kbd_height_land3")
        assertThat(topicReplyComposerEmojiPanelAnimationStartDelayMs()).isEqualTo(0L)
        assertThat(topicReplyComposerEmojiPanelTabsHeightDp()).isEqualTo(36)
        assertThat(topicReplyComposerEmojiTabButtonSizeDp()).isEqualTo(30)
        assertThat(topicReplyComposerEmojiTabSelectorSizeDp()).isEqualTo(30)
        assertThat(topicReplyComposerEmojiTabGapDp()).isEqualTo(3)
        assertThat(topicReplyComposerEmojiPanelSearchHeightDp()).isEqualTo(0)
        assertThat(topicReplyComposerEmojiPanelBottomControlsDp()).isEqualTo(44)
        assertThat(topicReplyComposerEmojiGridHorizontalPaddingDp()).isEqualTo(5)
        assertThat(topicReplyComposerEmojiPanelColumns()).isEqualTo(8)
        assertThat(topicReplyComposerEmojiCellSizeDp()).isEqualTo(42)
        assertThat(topicReplyComposerEmojiIconSizeDp()).isEqualTo(28)
        assertThat(topicReplyComposerEmojiPanelMaxHeightDp()).isEqualTo(360)
        assertThat(topicReplyComposerEmojiTabIconRes(0)).isEqualTo(org.telegram.messenger.R.drawable.msg_emoji_smiles)
        assertThat(topicReplyComposerEmojiTabIconRes(1)).isEqualTo(org.telegram.messenger.R.drawable.msg_emoji_cat)
        assertThat(topicReplyComposerEmojiTabIconRes(7)).isEqualTo(org.telegram.messenger.R.drawable.msg_emoji_flags)
        assertThat(topicReplyComposerEmojiTabIconRes(99)).isEqualTo(org.telegram.messenger.R.drawable.msg_emoji_flags)
        assertThat(
            topicReplyComposerEmojiCategoryIconUrl(
                baseUrl = "https://linux.do/",
                category = DiscourseEmojiCategory(
                    id = "people",
                    title = "People",
                    emojis = listOf(
                        DiscourseEmoji(name = "missing", url = ""),
                        DiscourseEmoji(name = "heart", url = "/images/emoji/twemoji/heart.png?v=15"),
                    ),
                ),
            ),
        ).isEqualTo("https://linux.do/images/emoji/twemoji/heart.png?v=15")
        assertThat(
            topicReplyComposerEmojiCategoryIconUrl(
                baseUrl = "https://linux.do/",
                category = DiscourseEmojiCategory(id = "empty", title = "Empty", emojis = emptyList()),
            ),
        ).isNull()
        assertThat(topicReplyComposerShouldIgnoreEmojiButton(keyboardAnimationInProgress = true, waitingForKeyboardOpenAfterAnimation = false))
            .isTrue()
        assertThat(topicReplyComposerShouldIgnoreEmojiButton(keyboardAnimationInProgress = false, emojiPanelAnimationInProgress = true, waitingForKeyboardOpenAfterAnimation = false))
            .isTrue()
        assertThat(topicReplyComposerShouldIgnoreEmojiButton(keyboardAnimationInProgress = false, waitingForKeyboardOpenAfterAnimation = true))
            .isTrue()
        assertThat(topicReplyComposerShouldIgnoreEmojiButton(keyboardAnimationInProgress = false, waitingForKeyboardOpenAfterAnimation = false))
            .isFalse()
        assertThat(topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(usingHardwareInput = false, isInMultiwindow = false))
            .isTrue()
        assertThat(topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(usingHardwareInput = true, isInMultiwindow = false))
            .isFalse()
    }

    @Test
    fun replyComposerEmojiPanelTabs_trackTelegramStyleSectionPositions() {
        val starts = listOf(0, 14, 38)
        assertThat(topicReplyComposerEmojiTabsBottomY(0)).isEqualTo(36)
        assertThat(topicReplyComposerEmojiGridTopPaddingPx(12)).isEqualTo(48)
        assertThat(topicReplyComposerEmojiGridClipTopPx(12)).isEqualTo(49)
        assertThat(topicReplyComposerEmojiSectionScrollOffsetPx(12)).isEqualTo(-9)
        assertThat(
            topicReplyComposerEmojiShouldUseHelperScroll(
                targetAttached = false,
                targetPosition = 90,
                firstVisiblePosition = 0,
                spanCount = 8,
                animationsEnabled = true,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerEmojiShouldUseHelperScroll(
                targetAttached = false,
                targetPosition = 16,
                firstVisiblePosition = 0,
                spanCount = 8,
                animationsEnabled = true,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerEmojiShouldUseHelperScroll(
                targetAttached = true,
                targetPosition = 16,
                firstVisiblePosition = 0,
                spanCount = 8,
                animationsEnabled = false,
            ),
        ).isTrue()
        assertThat(topicReplyComposerEmojiCategoryForPosition(-1, starts)).isEqualTo(0)
        assertThat(topicReplyComposerEmojiCategoryForPosition(0, starts)).isEqualTo(0)
        assertThat(topicReplyComposerEmojiCategoryForPosition(13, starts)).isEqualTo(0)
        assertThat(topicReplyComposerEmojiCategoryForPosition(14, starts)).isEqualTo(1)
        assertThat(topicReplyComposerEmojiCategoryForPosition(37, starts)).isEqualTo(1)
        assertThat(topicReplyComposerEmojiCategoryForPosition(38, starts)).isEqualTo(2)
        assertThat(topicReplyComposerEmojiCategoryForPosition(200, starts)).isEqualTo(2)
        assertThat(topicReplyComposerEmojiCategoryForPosition(10, emptyList())).isEqualTo(0)
    }

    @Test
    fun replyComposerEditTextBackKey_hidesEmojiPopupLikeChatActivityEnterView() {
        assertThat(
            topicReplyComposerShouldHideEmojiPanelFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = false,
                emojiPanelShowing = true,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldHideEmojiPanelFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_DOWN,
                keyboardVisible = false,
                emojiPanelShowing = true,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldHideEmojiPanelFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = true,
                emojiPanelShowing = true,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldHideEmojiPanelFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = false,
                emojiPanelShowing = true,
            ),
        ).isFalse()
    }

    @Test
    fun replyComposerEditTextBackKey_closesKeyboardBeforeLeavingTopic() {
        assertThat(
            topicReplyComposerShouldCloseKeyboardFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = true,
                emojiPanelShowing = false,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldCloseKeyboardFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = false,
                emojiPanelShowing = false,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldCloseKeyboardFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                keyboardVisible = true,
                emojiPanelShowing = true,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldCloseKeyboardFromEditTextBack(
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_DOWN,
                keyboardVisible = true,
                emojiPanelShowing = false,
            ),
        ).isFalse()
    }

    @Test
    fun replyComposerEditTextBoundsAnimation_matchesChatActivityEnterView() {
        assertThat(topicReplyComposerEditTextBoundsAnimationDurationMs()).isEqualTo(250L)
        assertThat(topicReplyComposerEditTextOffsetDelta(oldHeight = 44, newHeight = 68, oldScrollY = 0, newScrollY = 0))
            .isEqualTo(-24f)
        assertThat(topicReplyComposerShouldAnimateEditTextBounds(1, 2, 44, 68, 0, 0)).isTrue()
        assertThat(topicReplyComposerShouldAnimateEditTextBounds(2, 2, 68, 68, 0, 0)).isFalse()
    }

    @Test
    fun replyComposerKeyboardInset_ignoresTelegramSmallKeyboardNoise() {
        assertThat(topicReplyComposerKeyboardInsetPx(AndroidUtilities.dp(20f))).isEqualTo(0)
        assertThat(topicReplyComposerKeyboardInsetPx(AndroidUtilities.dp(21f))).isEqualTo(AndroidUtilities.dp(21f))

        AndroidUtilities.navigationBarHeight = AndroidUtilities.dp(21f)
        assertThat(topicReplyComposerKeyboardInsetPx(AndroidUtilities.dp(21f))).isEqualTo(0)
        assertThat(topicReplyComposerKeyboardInsetPx(AndroidUtilities.dp(221f))).isEqualTo(AndroidUtilities.dp(200f))
    }

    @Test
    fun replyComposerPanelKeyboardInset_doesNotSubtractNavigationBarTwice() {
        AndroidUtilities.navigationBarHeight = AndroidUtilities.dp(21f)

        assertThat(topicReplyComposerPanelKeyboardInsetPx(AndroidUtilities.dp(200f)))
            .isEqualTo(AndroidUtilities.dp(200f))
        assertThat(topicReplyComposerPanelKeyboardInsetPx(-1)).isEqualTo(0)
    }

    @Test
    fun replyComposerEmojiPanelHeight_clampsToKeyboardSizedArea() {
        val rootHeight = AndroidUtilities.dp(1000f)
        val rawHeight = AndroidUtilities.dp(900f)
        val maxHeight = topicReplyComposerEmojiPanelMaxHeightPx(rootHeight)
        assertThat(topicReplyComposerEmojiPanelHeightPx(keyboardInset = rawHeight, fallbackHeight = rawHeight, rootHeight = rootHeight))
            .isEqualTo(maxHeight)
        assertThat(maxHeight).isAtMost(AndroidUtilities.dp(360f))
    }

    @Test
    fun replyComposerActiveKeyboardInset_onlyAppliesWhilePanelAttached() {
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = true, keyboardInset = 260))
            .isEqualTo(260)
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = false, keyboardInset = 260))
            .isEqualTo(0)
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = true, keyboardInset = -1))
            .isEqualTo(0)
    }

    @Test
    fun panStartOffsetUsesEmojiHeightOnlyWhileSystemKeyboardIsClosed() {
        assertThat(topicDetailPanStartOffset(keyboardHeight = 0, popupShowing = true, popupHeight = 220))
            .isEqualTo(220)
        assertThat(topicDetailPanStartOffset(keyboardHeight = 20, popupShowing = true, popupHeight = 220))
            .isEqualTo(220)
        assertThat(topicDetailPanStartOffset(keyboardHeight = 21, popupShowing = true, popupHeight = 220))
            .isEqualTo(0)
        assertThat(topicDetailPanStartOffset(keyboardHeight = 0, popupShowing = false, popupHeight = 220))
            .isEqualTo(0)
    }

    @Test
    fun panHeightAnimationDisabledForBrowserSheetAndBottomTabsMotion() {
        assertThat(
            topicDetailPanHeightAnimationEnabled(
                hasParentActivity = true,
                fragmentPaused = false,
                hasBlockingPopup = false,
                hasShownAttachedSheet = false,
                bottomTabsAnimating = false,
                isLastFragment = true,
            ),
        ).isTrue()
        assertThat(
            topicDetailPanHeightAnimationEnabled(
                hasParentActivity = true,
                fragmentPaused = false,
                hasBlockingPopup = false,
                hasShownAttachedSheet = true,
                bottomTabsAnimating = false,
                isLastFragment = true,
            ),
        ).isFalse()
        assertThat(
            topicDetailPanHeightAnimationEnabled(
                hasParentActivity = true,
                fragmentPaused = false,
                hasBlockingPopup = false,
                hasShownAttachedSheet = false,
                bottomTabsAnimating = true,
                isLastFragment = true,
            ),
        ).isFalse()
        assertThat(
            topicDetailPanHeightAnimationEnabled(
                hasParentActivity = true,
                fragmentPaused = true,
                hasBlockingPopup = false,
                hasShownAttachedSheet = false,
                bottomTabsAnimating = false,
                isLastFragment = true,
            ),
        ).isFalse()
    }

    @Test
    fun listTopPaddingAndContentLiftMatchChatGeometry() {
        assertThat(topicDetailListTopPaddingPx(baseTopPadding = 0, contentPanTranslation = 48f))
            .isEqualTo(0)
        assertThat(topicDetailListContentTopPaddingPx(actionBarHeight = 112))
            .isEqualTo(112)
        assertThat(topicDetailContentLiftPx(animatedImeBottomInset = 300f))
            .isEqualTo(-300f)
        assertThat(topicDetailContentLiftPx(animatedImeBottomInset = -10f))
            .isEqualTo(0f)
        assertThat(topicDetailPanSafeBottomInsetPx(navigationBarInset = 40, inAppKeyboardHeight = 0, systemImeHeight = 300))
            .isEqualTo(40)
        assertThat(topicDetailIslandBottomInsetPx(windowMaxBottomInset = 360, bottomTabsHeight = 68))
            .isEqualTo(292)
        assertThat(topicDetailShouldApplyInputInsets(panInProgress = true, systemImeHeight = 300))
            .isFalse()
        assertThat(topicDetailShouldApplyInputInsets(panInProgress = false, systemImeHeight = 300))
            .isTrue()
        assertThat(topicDetailShouldApplyInputInsets(panInProgress = true, systemImeHeight = 0))
            .isTrue()
    }

    @Test
    fun replyComposerListBottomPadding_includesPanelAndKeyboardInset() {
        assertThat(topicReplyComposerListBottomPadding(basePadding = 24, panelHeight = 72, keyboardInset = 260))
            .isEqualTo(356)
        assertThat(topicReplyComposerListBottomPadding(basePadding = 24, panelHeight = -1, keyboardInset = -1))
            .isEqualTo(24)
    }

    @Test
    fun replyComposerListBottomPadding_tracksAnimatedPanelInsetDuringKeyboardHide() {
        assertThat(
            topicReplyComposerPanelBottomInsetForListPadding(
                targetInset = 0,
                animatedInset = 180,
                animationInProgress = true,
            ),
        ).isEqualTo(180)
        assertThat(
            topicReplyComposerPanelBottomInsetForListPadding(
                targetInset = 0,
                animatedInset = 180,
                animationInProgress = false,
            ),
        ).isEqualTo(0)
        assertThat(
            topicReplyComposerPanelBottomInsetForListPadding(
                targetInset = 260,
                animatedInset = -1,
                animationInProgress = false,
            ),
        ).isEqualTo(260)
    }

    @Test
    fun replyComposerVisiblePanelHeight_animatesInputIslandHeightLikeChatActivityEnterView() {
        assertThat(
            topicReplyComposerVisiblePanelHeightForListPadding(
                panelHeight = 100,
                animatedInputFieldHeight = 44f,
                targetInputFieldHeight = 68,
            ),
        ).isEqualTo(76)
        assertThat(
            topicReplyComposerVisiblePanelHeightForListPadding(
                panelHeight = 100,
                animatedInputFieldHeight = 56f,
                targetInputFieldHeight = 68,
            ),
        ).isEqualTo(88)
        assertThat(
            topicReplyComposerVisiblePanelHeightForListPadding(
                panelHeight = 100,
                animatedInputFieldHeight = 68f,
                targetInputFieldHeight = 44,
            ),
        ).isEqualTo(124)
    }

    @Test
    fun replyComposerKeyboardSubmit_matchesChatActivityEnterViewDefaultSendByEnter() {
        assertThat(
            topicReplyComposerShouldSubmitFromKey(
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
                shiftPressed = false,
                ctrlPressed = true,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldSubmitFromKey(
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
                shiftPressed = false,
                ctrlPressed = false,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldSubmitFromKey(
                keyCode = KeyEvent.KEYCODE_ENTER,
                action = KeyEvent.ACTION_DOWN,
                shiftPressed = true,
                ctrlPressed = true,
            ),
        ).isFalse()
    }

    @Test
    fun textSelectionDispatch_matchesArticleViewerOverlayOrdering() {
        assertThat(topicSelectionTouchDispatch(overlayHandled = true, tapClearedSelection = false))
            .isEqualTo(TopicSelectionTouchDispatch.CONSUME)
        assertThat(topicSelectionTouchDispatch(overlayHandled = true, tapClearedSelection = true))
            .isEqualTo(TopicSelectionTouchDispatch.CONSUME)
        assertThat(topicSelectionTouchDispatch(overlayHandled = false, tapClearedSelection = true))
            .isEqualTo(TopicSelectionTouchDispatch.DISPATCH_CANCEL)
        assertThat(topicSelectionTouchDispatch(overlayHandled = false, tapClearedSelection = false))
            .isEqualTo(TopicSelectionTouchDispatch.DISPATCH_ORIGINAL)
    }

    /**
     * `BaseFragment.getResourceProvider()` returns null, and the block editor's sheet requires one
     * (the on-device symptom was a `NullPointerException` in `showRichEditor` on the entry tap).
     */
    @Test
    fun fragmentSuppliesTheResourcesProviderTheEditorSheetNeeds() {
        assertThat(TopicDetailFragment(1L).getResourceProvider()).isNotNull()
    }

    private fun positionFor(
        anchorX: Int,
        anchorY: Int,
        pressX: Float,
        pressY: Float,
        anchorWidth: Int = 320,
        anchorHeight: Int = 480,
        popupWidth: Int = 200,
        popupHeight: Int = 300,
    ): TopicPostOptionsPopupPosition {
        val anchorView = object : View(RuntimeEnvironment.getApplication()) {
            override fun getLocationInWindow(outLocation: IntArray) {
                outLocation[0] = anchorX
                outLocation[1] = anchorY
            }
        }
        anchorView.layout(0, 0, anchorWidth, anchorHeight)
        return topicPostOptionsPopupPosition(
            anchorView = anchorView,
            popupWidth = popupWidth,
            popupHeight = popupHeight,
            pressX = pressX,
            pressY = pressY,
        )
    }
}
