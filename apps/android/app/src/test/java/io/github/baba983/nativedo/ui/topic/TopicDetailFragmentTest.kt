package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.feature.topic.ui.TOPIC_REACTION_ALL_FILTER_ID
import io.github.baba983.nativedo.feature.topic.ui.TopicUiState
import io.github.baba983.nativedo.feature.topic.ui.isReadPositionOnlyUpdateFrom
import io.github.baba983.nativedo.feature.topic.ui.topicUnreadPostsCount
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

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
    fun replyComposerEmojiButton_ignoresDuringKeyboardAnimationOrKeyboardOpenWait() {
        assertThat(
            topicReplyComposerShouldIgnoreEmojiButton(
                keyboardAnimationInProgress = true,
                waitingForKeyboardOpenAfterAnimation = false,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldIgnoreEmojiButton(
                keyboardAnimationInProgress = false,
                emojiPanelAnimationInProgress = true,
                waitingForKeyboardOpenAfterAnimation = false,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldIgnoreEmojiButton(
                keyboardAnimationInProgress = false,
                waitingForKeyboardOpenAfterAnimation = true,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldIgnoreEmojiButton(
                keyboardAnimationInProgress = false,
                waitingForKeyboardOpenAfterAnimation = false,
            ),
        ).isFalse()
        assertThat(
            topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(
                usingHardwareInput = false,
                isInMultiwindow = false,
            ),
        ).isTrue()
        assertThat(
            topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(
                usingHardwareInput = true,
                isInMultiwindow = false,
            ),
        ).isFalse()
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
    fun replyComposerActiveKeyboardInset_onlyAppliesWhilePanelAttached() {
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = true, keyboardInset = 260))
            .isEqualTo(260)
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = false, keyboardInset = 260))
            .isEqualTo(0)
        assertThat(topicReplyComposerActiveKeyboardInset(panelAttached = true, keyboardInset = -1))
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
}
