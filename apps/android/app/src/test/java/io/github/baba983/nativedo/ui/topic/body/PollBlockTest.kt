package io.github.baba983.nativedo.ui.topic.body

import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.telegram.messenger.AndroidUtilities
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PollBlockTest {
    @Test
    fun pollGeometryConstants_matchTelegramChatMessageCellContract() {
        assertThat(PollBlock.pollAnimationDurationMs()).isEqualTo(300)
        assertThat(PollBlock.checkStrokeWidthPx()).isEqualTo(AndroidUtilities.dp(1.833f))
        assertThat(PollBlock.optionTextLeftPx()).isEqualTo(AndroidUtilities.dp(35f))
        assertThat(PollBlock.pollVerticalPaddingPx()).isEqualTo(AndroidUtilities.dp(12f))
        assertThat(PollBlock.selectorVerticalInsetPx()).isEqualTo(AndroidUtilities.dp(13f))
        assertThat(PollBlock.resultLineTopGapPx()).isEqualTo(AndroidUtilities.dp(7f))
        assertThat(PollBlock.resultLineHeightPx()).isEqualTo(AndroidUtilities.dp(5f))
        assertThat(PollBlock.percentXOffsetPx()).isEqualTo(AndroidUtilities.dp(6.5f))
        assertThat(PollBlock.voteControlCenterXOffsetPx()).isEqualTo(AndroidUtilities.dp(22f))
        assertThat(PollBlock.voteControlCenterYPx()).isEqualTo(AndroidUtilities.dp(9f))
        assertThat(PollBlock.resultCheckCenterXOffsetPx()).isEqualTo(AndroidUtilities.dp(15.5f))
        assertThat(PollBlock.resultCheckCenterYOffsetPx()).isEqualTo(AndroidUtilities.dp(2.5f))
        assertThat(PollBlock.checkboxCustomRadiusPx()).isEqualTo(AndroidUtilities.dp(5f))
        assertThat(PollBlock.MULTI_CHOICE_CHECKBOX_SIZE).isEqualTo(19)
        assertThat(PollBlock.SINGLE_CHOICE_CHECKBOX_SIZE).isEqualTo(20)
    }

    @Test
    fun multipleChoice_togglesOptionsBeforeSubmittingVote() {
        val state = PostBodyBlockState()
        val votes = mutableListOf<List<String>>()
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            type = "multiple",
            status = "open",
            voters = null,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A"),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, optionIds -> votes += optionIds },
            state,
            4,
        )
        block.populateButtons(poll.options)

        block.updateLocalSelection(0)
        block.updateLocalSelection(1)

        assertThat(votes).isEmpty()
        assertThat(state.selectedPollOptionIndexesByIndex[4]).containsExactly(0, 1).inOrder()
        assertThat(state.submittedPollIndexes).doesNotContain(4)

        block.submitMultiSelectVote()

        assertThat(votes).containsExactly(listOf("option-a", "option-b"))
        assertThat(state.submittedPollIndexes).contains(4)
    }

    @Test
    fun singleChoice_submitsImmediately() {
        val state = PostBodyBlockState()
        val host = CountingHostView()
        val votes = mutableListOf<List<String>>()
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            type = "regular",
            status = "open",
            voters = null,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A"),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext(host = host) { _, optionIds -> votes += optionIds },
            state,
            5,
        )
        block.populateButtons(poll.options)

        block.updateLocalSelection(1)
        val optionIds = block.selectedOptionIdsForVote(1)
        val requestLayoutBeforeSubmit = host.requestLayoutCount
        val invalidateBeforeSubmit = host.invalidateCount
        block.markLocalSubmitted()
        block.contextDelegate().onPollOptionsSelected("frontend", optionIds)

        assertThat(votes).containsExactly(listOf("option-b"))
        assertThat(state.submittedPollIndexes).contains(5)
        assertThat(host.requestLayoutCount).isEqualTo(requestLayoutBeforeSubmit + 1)
        assertThat(host.invalidateCount).isGreaterThan(invalidateBeforeSubmit)
    }

    @Test
    fun optionRichTextDragCancel_doesNotSwallowNextVote() {
        val votes = mutableListOf<List<String>>()
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            type = "regular",
            status = "open",
            voters = null,
            options = listOf(
                CookedPostBlock.PollOption(
                    id = "option-a",
                    label = "Visible hidden",
                    labelHtml = """Visible <span class="spoiler">hidden</span>""",
                ),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, optionIds -> votes += optionIds },
            null,
            6,
        )

        block.populateButtons(poll.options)
        block.setActiveRichTextButtonIndex(0)
        block.setPressedSpoilerForOption(0, downX = 0f, downY = 0f)
        assertThat(block.touch(MotionEvent.ACTION_MOVE, 200f, 0f)).isFalse()
        assertThat(block.activeRichTextButtonIndex()).isEqualTo(-1)

        val optionBounds = RectF(10f, 10f, 220f, 70f)
        block.setOptionSelectorBounds(1, optionBounds)
        block.setOptionSelectorDrawable(1)
        val optionX = optionBounds.centerX()
        val optionY = optionBounds.centerY()
        assertThat(block.touch(MotionEvent.ACTION_DOWN, optionX, optionY)).isTrue()
        assertThat(block.touch(MotionEvent.ACTION_UP, optionX, optionY)).isTrue()

        assertThat(votes).containsExactly(listOf("option-b"))
    }

    @Test
    fun optionSpoilerReveal_usesInlineStateWhenStructuralStateDisabled() {
        val inlineState = PostBodyBlockState()
        val option = CookedPostBlock.PollOption(
            id = "option-a",
            label = "Visible hidden",
            labelHtml = """Visible <span class="spoiler">hidden</span>""",
        )
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            type = "regular",
            status = "open",
            voters = null,
            options = listOf(option),
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            null,
            0,
            inlineState,
            "details:0.children.2",
        )
        block.populateButtons(poll.options)
        val button = block.pollButton(0)

        block.markButtonSpoilersRevealed(button)

        assertThat(inlineState.revealedInlineSpoilersByKey)
            .contains(block.pollOptionSpoilerStateKey(option, option.labelHtml!!))
    }

    private fun buildContext(
        host: View = View(RuntimeEnvironment.getApplication()),
        onPollOptionsSelected: (String, List<String>) -> Unit,
    ): PostBodyBuildContext {
        return PostBodyBuildContext(
            host = host,
            baseUrl = "https://linux.do/",
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) = Unit
                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit
                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) {
                    onPollOptionsSelected(pollName, optionIds)
                }
            },
            textColor = 0xFF111111.toInt(),
            secondaryTextColor = 0xFF666666.toInt(),
            accentColor = 0xFF1677FF.toInt(),
            chipBackgroundColor = 0x221677FF,
            dividerColor = 0xFFE0E0E0.toInt(),
        )
    }

    private fun PollBlock.populateButtons(options: List<CookedPostBlock.PollOption>) =
        PollBlock::class.java.getDeclaredMethod("ensureButtons", List::class.java)
            .apply { isAccessible = true }
            .invoke(this, options)

    private fun PollBlock.updateLocalSelection(index: Int) =
        PollBlock::class.java.getDeclaredMethod("updateLocalSelection", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, index)

    @Suppress("UNCHECKED_CAST")
    private fun PollBlock.selectedOptionIdsForVote(index: Int): List<String> =
        PollBlock::class.java.getDeclaredMethod("selectedOptionIdsForVote", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, index) as List<String>

    private fun PollBlock.submitMultiSelectVote() =
        PollBlock::class.java.getDeclaredMethod("submitMultiSelectVote")
            .apply { isAccessible = true }
            .invoke(this)

    private fun PollBlock.markLocalSubmitted() =
        PollBlock::class.java.getDeclaredMethod("markLocalSubmitted")
            .apply { isAccessible = true }
            .invoke(this)

    private fun PollBlock.markButtonSpoilersRevealed(button: Any) =
        PollBlock::class.java.getDeclaredMethod("markButtonSpoilersRevealed", button.javaClass)
            .apply { isAccessible = true }
            .invoke(this, button)

    private fun PollBlock.pollOptionSpoilerStateKey(option: CookedPostBlock.PollOption, html: String): String =
        PollBlock::class.java.getDeclaredMethod(
            "pollOptionSpoilerStateKey",
            CookedPostBlock.PollOption::class.java,
            String::class.java,
        )
            .apply { isAccessible = true }
            .invoke(this, option, html) as String

    private fun PollBlock.contextDelegate(): PostBodyDelegate {
        val context = PollBlock::class.java.getDeclaredField("context")
            .apply { isAccessible = true }
            .get(this) as PostBodyBuildContext
        return context.delegate
    }

    private fun PollBlock.setActiveRichTextButtonIndex(index: Int) {
        PollBlock::class.java.getDeclaredField("activeRichTextButtonIndex")
            .apply { isAccessible = true }
            .setInt(this, index)
    }

    private fun PollBlock.activeRichTextButtonIndex(): Int =
        PollBlock::class.java.getDeclaredField("activeRichTextButtonIndex")
            .apply { isAccessible = true }
            .getInt(this)

    private fun PollBlock.setPressedSpoilerForOption(index: Int, downX: Float, downY: Float) {
        val button = pollButton(index)
        button.setBooleanField("pressedSpoiler", true)
        button.setFloatField("downX", downX)
        button.setFloatField("downY", downY)
    }

    private fun PollBlock.setOptionSelectorBounds(index: Int, bounds: RectF) {
        val button = pollButton(index)
        (button.javaClass.getDeclaredField("selectorBounds")
            .apply { isAccessible = true }
            .get(button) as RectF).set(bounds)
    }

    private fun PollBlock.setOptionSelectorDrawable(index: Int) {
        val button = pollButton(index)
        button.javaClass.getDeclaredField("selectorDrawable")
            .apply { isAccessible = true }
            .set(button, ColorDrawable(0))
    }

    private fun PollBlock.pollButton(index: Int): Any {
        val buttons = PollBlock::class.java.getDeclaredField("pollButtons")
            .apply { isAccessible = true }
            .get(this) as ArrayList<*>
        return buttons[index] ?: error("Missing poll button at $index")
    }

    private fun Any.setBooleanField(name: String, value: Boolean) {
        javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .setBoolean(this, value)
    }

    private fun Any.setFloatField(name: String, value: Float) {
        javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .setFloat(this, value)
    }

    private fun PollBlock.touch(action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        return try {
            onTouch(event, x, y)
        } finally {
            event.recycle()
        }
    }

    private class CountingHostView : View(RuntimeEnvironment.getApplication()) {
        var requestLayoutCount = 0
            private set
        var invalidateCount = 0
            private set

        override fun requestLayout() {
            requestLayoutCount += 1
            super.requestLayout()
        }

        override fun invalidate() {
            invalidateCount += 1
            super.invalidate()
        }
    }
}
