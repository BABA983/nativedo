package io.github.baba983.nativedo.ui.topic.cell

import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.TextSelectionHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicPostCellTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun selectionModeInterceptsOnlyOneTouchDragContinuation() {
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(false, false)).isFalse()
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(true, false)).isFalse()
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(true, true)).isTrue()
    }

    @Test
    fun searchHighlightRange_matchesTelegramWordBoundaryAndWholeWordExtension() {
        val range = TopicPostCell.searchHighlightRange("prefix searching suffix", "SEARCH")

        assertThat(range).isNotNull()
        assertThat(range!![0]).isEqualTo(7)
        assertThat(range[1]).isEqualTo(16)
        assertThat(TopicPostCell.searchHighlightRange("researching", "search")).isNull()
    }

    @Test
    fun searchHighlightRange_keepsTelegramLongestMatchingPrefix() {
        val range = TopicPostCell.searchHighlightRange("telegram native", "telegramming")

        assertThat(range).isNotNull()
        assertThat(range!![0]).isEqualTo(0)
        assertThat(range[1]).isEqualTo(8)
    }

    @Test
    fun searchHighlightRange_handlesCjkAndLatinWithoutWhitespace() {
        val latinRange = TopicPostCell.searchHighlightRange("来点Linux建议", "linux")
        val cjkRange = TopicPostCell.searchHighlightRange("中文搜索结果", "搜索")

        assertThat(latinRange).isEqualTo(intArrayOf(2, 7))
        assertThat(cjkRange).isEqualTo(intArrayOf(2, 4))
    }

    @Test
    fun replyFooterMoveOutside_keepsPressLikeChatMessageCellCommentButton() {
        val cell = TopicPostCell(RuntimeEnvironment.getApplication())
        val post = Post(id = 1001L, postNumber = 1, authorUsername = "alice", cookedHtml = "")
        var replyPresses = 0
        cell.delegate = object : TopicPostCellDelegate {
            override fun didPressReply(post: Post) {
                replyPresses++
            }
        }
        cell.setPost(
            post,
            null,
            null,
            true,
            true,
            42L,
            "https://linux.do/",
            emptyMap(),
            emptyMap(),
            emptyList(),
            2,
            emptyList(),
            emptyList(),
            PostBodyBlockState(),
            0,
            null,
        )
        val width = AndroidUtilities.dp(360f)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)

        val downX = TopicPostCell.horizontalPaddingPx() + TopicPostCell.replyContentLeftPx() + AndroidUtilities.dp(6f)
        val downY = cell.measuredHeight - TopicPostCell.verticalPaddingPx() - TopicPostCell.replyFooterHeightPx() / 2f
        val movedX = width + AndroidUtilities.dp(48f)
        val movedY = downY + AndroidUtilities.dp(48f)

        assertThat(dispatchTouch(cell, MotionEvent.ACTION_DOWN, downX.toFloat(), downY)).isTrue()
        assertThat(dispatchTouch(cell, MotionEvent.ACTION_MOVE, movedX.toFloat(), movedY)).isTrue()
        assertThat(dispatchTouch(cell, MotionEvent.ACTION_UP, movedX.toFloat(), movedY)).isTrue()
        assertThat(replyPresses).isEqualTo(1)
    }

    @Test
    fun selectableTextLongPress_opensPostMenuBeforeTextSelection() {
        val cell = TestTopicPostCell(RuntimeEnvironment.getApplication())
        val post = Post(id = 1001L, postNumber = 1, authorUsername = "alice", cookedHtml = "")
        val selectionHelper = FakeSelectingHelper()
        var longPressedPost: Post? = null
        cell.delegate = object : TopicPostCellDelegate {
            override fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) {
                longPressedPost = post
            }
        }
        cell.setTextSelectionHelper(selectionHelper)
        bindAndLayoutBasicPost(cell, post)

        assertThat(dispatchTouch(cell, MotionEvent.ACTION_DOWN, TopicPostCell.horizontalPaddingPx() + 80f, TopicPostCell.verticalPaddingPx() + 80f)).isTrue()
        assertThat(cell.triggerLongPressForTest()).isTrue()
        assertThat(selectionHelper.maybeViewSet).isFalse()
        assertThat(selectionHelper.trySelectCalled).isFalse()
        assertThat(selectionHelper.isInSelectionMode).isFalse()
        assertThat(longPressedPost).isEqualTo(post)
    }

    @Test
    fun explicitTextSelection_selectsTouchedWordWithoutOneTouchDrag() {
        val cell = TestTopicPostCell(RuntimeEnvironment.getApplication())
        val post = Post(id = 1001L, postNumber = 1, authorUsername = "alice", cookedHtml = "")
        val selectionHelper = FakeSelectingHelper()
        cell.setTextSelectionHelper(selectionHelper)
        bindAndLayoutBasicPost(cell, post)

        assertThat(cell.startTextSelectionAt(80f, 120f)).isTrue()
        assertThat(selectionHelper.maybeViewSet).isTrue()
        assertThat(selectionHelper.trySelectCalled).isTrue()
    }

    @Test
    fun chromeLongPress_opensPostMenuWhenTextSelectionDoesNotClaimTouch() {
        val cell = TestTopicPostCell(RuntimeEnvironment.getApplication())
        val post = Post(id = 1001L, postNumber = 1, authorUsername = "alice", cookedHtml = "")
        var longPressedPost: Post? = null
        cell.delegate = object : TopicPostCellDelegate {
            override fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) {
                longPressedPost = post
            }
        }
        cell.setTextSelectionHelper(FakeNonSelectingHelper())
        bindAndLayoutBasicPost(cell, post)

        assertThat(dispatchTouch(cell, MotionEvent.ACTION_DOWN, TopicPostCell.horizontalPaddingPx() + 8f, TopicPostCell.verticalPaddingPx() + 8f)).isTrue()
        assertThat(cell.triggerLongPressForTest()).isTrue()
        assertThat(longPressedPost).isEqualTo(post)
    }

    @Test
    fun replyFooterLongPress_opensPostMenuInsteadOfRepliesSheet() {
        val cell = TestTopicPostCell(RuntimeEnvironment.getApplication())
        val post = Post(id = 1001L, postNumber = 1, authorUsername = "alice", cookedHtml = "")
        var replyPresses = 0
        var longPressedPost: Post? = null
        cell.delegate = object : TopicPostCellDelegate {
            override fun didPressReply(post: Post) {
                replyPresses++
            }

            override fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) {
                longPressedPost = post
            }
        }
        cell.setPost(
            post,
            null,
            null,
            true,
            true,
            42L,
            "https://linux.do/",
            emptyMap(),
            emptyMap(),
            emptyList(),
            2,
            emptyList(),
            emptyList(),
            PostBodyBlockState(),
            0,
            null,
        )
        val width = AndroidUtilities.dp(360f)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)

        val downX = TopicPostCell.horizontalPaddingPx() + TopicPostCell.replyContentLeftPx() + AndroidUtilities.dp(6f)
        val downY = cell.measuredHeight - TopicPostCell.verticalPaddingPx() - TopicPostCell.replyFooterHeightPx() / 2f

        assertThat(dispatchTouch(cell, MotionEvent.ACTION_DOWN, downX.toFloat(), downY)).isTrue()
        assertThat(cell.triggerLongPressForTest()).isTrue()
        assertThat(dispatchTouch(cell, MotionEvent.ACTION_UP, downX.toFloat(), downY)).isTrue()
        assertThat(longPressedPost).isEqualTo(post)
        assertThat(replyPresses).isEqualTo(0)
    }

    @Test
    fun reactionLongPress_opensReactionRailInsteadOfReactionUsers() {
        val cell = TestTopicPostCell(RuntimeEnvironment.getApplication())
        val reaction = PostReaction("heart", 1, null, true)
        val post = Post(
            id = 1001L,
            postNumber = 1,
            authorUsername = "alice",
            currentUserReaction = "heart",
            reactions = listOf(reaction),
            cookedHtml = "",
        )
        var reactionPresses = 0
        var moreReactionInitialId: String? = null
        cell.delegate = object : TopicPostCellDelegate {
            override fun didPressReaction(post: Post, reaction: PostReaction, anchorView: View, x: Float, y: Float) {
                reactionPresses++
            }

            override fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?, anchorView: View, x: Float, y: Float) {
                moreReactionInitialId = initialReactionId
            }
        }
        cell.setPost(
            post,
            null,
            null,
            true,
            true,
            42L,
            "https://linux.do/",
            emptyMap(),
            emptyMap(),
            listOf("heart", "+1"),
            0,
            emptyList(),
            emptyList(),
            PostBodyBlockState(),
            0,
            null,
        )
        val width = AndroidUtilities.dp(360f)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)

        val downX = TopicPostCell.horizontalPaddingPx() + AndroidUtilities.dp(12f)
        val downY = cell.measuredHeight - TopicPostCell.verticalPaddingPx() - TopicPostCell.reactionButtonHeightPx() / 2f

        assertThat(dispatchTouch(cell, MotionEvent.ACTION_DOWN, downX.toFloat(), downY)).isTrue()
        assertThat(cell.triggerLongPressForTest()).isTrue()
        assertThat(dispatchTouch(cell, MotionEvent.ACTION_UP, downX.toFloat(), downY)).isTrue()
        assertThat(moreReactionInitialId).isEqualTo("heart")
        assertThat(reactionPresses).isEqualTo(0)
    }

    private fun dispatchTouch(cell: TopicPostCell, action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        return try {
            cell.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun bindAndLayoutBasicPost(cell: TopicPostCell, post: Post) {
        cell.setPost(
            post,
            null,
            null,
            true,
            true,
            42L,
            "https://linux.do/",
            emptyMap(),
            emptyMap(),
            emptyList(),
            0,
            emptyList(),
            emptyList(),
            PostBodyBlockState(),
            0,
            null,
        )
        val width = AndroidUtilities.dp(360f)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)
    }

    private class TestTopicPostCell(context: android.content.Context) : TopicPostCell(context) {
        fun triggerLongPressForTest(): Boolean = super.onLongPress()
    }

    private open class FakeNonSelectingHelper : TextSelectionHelper.ArticleTextSelectionHelper() {
        override fun isSelectable(view: View?): Boolean = false
    }

    private class FakeSelectingHelper : TextSelectionHelper.ArticleTextSelectionHelper() {
        var maybeViewSet = false
        var trySelectCalled = false
        private var inSelectionMode = false

        override fun isSelectable(view: View?): Boolean = true

        override fun setMaybeView(x: Int, y: Int, view: View?) {
            maybeViewSet = true
        }

        override fun trySelect(view: View?) {
            trySelectCalled = true
            inSelectionMode = true
        }

        override fun isInSelectionMode(): Boolean = inSelectionMode

    }
}
