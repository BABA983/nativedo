package io.github.baba983.nativedo.ui.topic.cell

import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionRendering
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
    fun cellChromeGeometry_matchesTelegramStylePostCellContract() {
        assertThat(TopicPostCell.horizontalPaddingPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(TopicPostCell.verticalPaddingPx()).isEqualTo(AndroidUtilities.dp(12f))
        assertThat(TopicPostCell.avatarSizePx()).isEqualTo(AndroidUtilities.dp(42f))
        assertThat(TopicPostCell.avatarGapPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(TopicPostCell.headerBottomSpacingPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(TopicPostCell.blockSpacingPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(TopicPostCell.footerTopSpacingPx()).isEqualTo(AndroidUtilities.dp(12f))
    }

    @Test
    fun replyFooterGeometry_keepsChatMessageCellCommentButtonAvatarContract() {
        assertThat(TopicPostCell.replyFooterHeightPx()).isEqualTo(AndroidUtilities.dp(40f))
        assertThat(TopicPostCell.replyAvatarSizePx()).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(TopicPostCell.replyAvatarStepPx()).isEqualTo(AndroidUtilities.dp(17f))
        assertThat(TopicPostCell.replyAvatarLeadingOffsetPx()).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(TopicPostCell.replyAvatarOverlapMaskRadiusPx()).isEqualTo(AndroidUtilities.dp(13f))
        assertThat(TopicPostCell.replyAvatarOffsetPx(0)).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(TopicPostCell.replyAvatarOffsetPx(2)).isEqualTo(AndroidUtilities.dp(2f) + AndroidUtilities.dp(34f))
        assertThat(TopicPostCell.replyAvatarsWidthPx(0)).isEqualTo(0)
        assertThat(TopicPostCell.replyAvatarsWidthPx(3))
            .isEqualTo(AndroidUtilities.dp(2f) + AndroidUtilities.dp(24f) + AndroidUtilities.dp(34f))
        assertThat(TopicPostCell.replyContentLeftPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(TopicPostCell.replyTextGapPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(TopicPostCell.replySelectorRightPaddingPx()).isEqualTo(AndroidUtilities.dp(12f))
    }

    @Test
    fun reactionGeometry_matchesChatMessageCellReactionsLayoutSubset() {
        assertThat(TopicPostCell.usesReactionOverflowChip()).isFalse()
        assertThat(TopicPostCell.reactionTopSpacingPx()).isEqualTo(AndroidUtilities.dp(12f))
        assertThat(TopicPostCell.reactionButtonHeightPx()).isEqualTo(AndroidUtilities.dp(26f))
        assertThat(TopicPostCell.reactionButtonSpacingPx()).isEqualTo(AndroidUtilities.dp(4f))
        assertThat(TopicPostCell.reactionButtonTouchSlopPx()).isEqualTo(AndroidUtilities.dp(4f))
        assertThat(TopicPostCell.reactionButtonHorizontalPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(TopicPostCell.reactionButtonMinWidthPx()).isEqualTo(AndroidUtilities.dp(34f))
        assertThat(TopicPostCell.reactionIconSizePx()).isEqualTo(AndroidUtilities.dp(20f))
        assertThat(TopicPostCell.reactionIconGapPx()).isEqualTo(AndroidUtilities.dp(4f))
    }

    @Test
    fun selectionModeInterceptsOnlyOneTouchDragContinuation() {
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(false, false)).isFalse()
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(true, false)).isFalse()
        assertThat(TopicPostCell.shouldInterceptSelectionTouch(true, true)).isTrue()
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
    fun selectableTextLongPress_startsSelectionInsteadOfPostMenu() {
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
        assertThat(selectionHelper.maybeViewSet).isTrue()
        assertThat(selectionHelper.trySelectCalled).isTrue()
        assertThat(selectionHelper.oneTouchSelectionStarted).isTrue()
        assertThat(longPressedPost).isNull()
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

    @Test
    fun buildReactionButtons_keepsRowHiddenWhenOnlyValidReactionOptionsExist() {
        val buttons = TopicPostCell.buildReactionButtonSpecs(
            emptyList<PostReaction>(),
            listOf("heart", "+1"),
            emptyMap<String, String>(),
            3,
        )

        assertThat(buttons).isEmpty()
    }

    @Test
    fun reactionRendering_prefersExplicitIconThenEmojiCatalog() {
        assertThat(PostReactionRendering.iconUrl("heart", "explicit.png", mapOf("heart" to "catalog.png")))
            .isEqualTo("explicit.png")
        assertThat(PostReactionRendering.iconUrl("heart", "", mapOf("heart" to "catalog.png")))
            .isEqualTo("catalog.png")
        assertThat(PostReactionRendering.iconUrl("heart", null, emptyMap()))
            .isNull()
    }

    @Test
    fun buildReactionButtons_usesFallbackTextOnlyWhenIconIsMissing() {
        val withCatalogIcon = TopicPostCell.buildReactionButtonSpecs(
            listOf(PostReaction("heart", 4)),
            listOf("heart"),
            mapOf("heart" to "/images/emoji/heart.png"),
            3,
        )
        val withoutIcon = TopicPostCell.buildReactionButtonSpecs(
            listOf(PostReaction("heart", 4)),
            listOf("heart"),
            emptyMap<String, String>(),
            3,
        )

        assertThat(withCatalogIcon.first().countText).isEqualTo("4")
        assertThat(withoutIcon.first().countText).isEqualTo("♡ 4")
    }

    @Test
    fun buildReactionButtons_keepsReactionRowHiddenWithoutKnownReactionOptions() {
        val buttons = TopicPostCell.buildReactionButtonSpecs(
            emptyList<PostReaction>(),
            emptyList(),
            emptyMap<String, String>(),
            3,
        )

        assertThat(buttons).isEmpty()
    }

    @Test
    fun buildReactionButtons_rendersAllExistingReactionsWithoutOverflowChip() {
        val buttons = TopicPostCell.buildReactionButtonSpecs(
            listOf(
                PostReaction("heart", 4),
                PostReaction("+1", 3),
                PostReaction("laughing", 2),
                PostReaction("clap", 1),
            ),
            listOf("heart", "+1", "laughing", "clap"),
            emptyMap<String, String>(),
            3,
        )

        assertThat(buttons.map { it.postReaction?.id })
            .containsExactly("heart", "+1", "laughing", "clap")
            .inOrder()
    }

    @Test
    fun reactionChangeAnimationDuration_matchesReactionsLayoutInBubbleCounter() {
        assertThat(TopicPostCell.reactionChangeAnimationDurationMs()).isEqualTo(320L)
    }

    @Test
    fun reactionButtonInOutScale_matchesReactionsLayoutInBubbleAnimationTypes() {
        assertThat(TopicPostCell.reactionButtonInAlpha(0.25f)).isEqualTo(0.25f)
        assertThat(TopicPostCell.reactionButtonInAlpha(-1f)).isEqualTo(0f)
        assertThat(TopicPostCell.reactionButtonInScale(0f)).isEqualTo(0.5f)
        assertThat(TopicPostCell.reactionButtonInScale(1f)).isEqualTo(1f)
        assertThat(TopicPostCell.reactionButtonOutScale(0f)).isEqualTo(1f)
        assertThat(TopicPostCell.reactionButtonOutScale(1f)).isEqualTo(0.5f)
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
        var oneTouchSelectionStarted = false
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

        override fun startOneTouchSelection() {
            oneTouchSelectionStarted = true
        }
    }
}
