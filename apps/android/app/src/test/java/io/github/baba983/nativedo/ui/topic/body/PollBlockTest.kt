package io.github.baba983.nativedo.ui.topic.body

import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PollBlockTest {
    @Test
    fun instantButtonText_usesViewVotersLabelForPublicPollWithResults() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            type = "regular",
            status = "closed",
            voters = 14,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 4),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B", votes = 10),
            ),
            publicPoll = true,
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            23,
        )
        block.populateButtons(poll.options)

        assertThat(block.instantButtonText()).isEqualTo("查看投票")
        assertThat(block.canOpenVotersList()).isTrue()
        assertThat(block.canInstantButtonAction()).isTrue()
    }

    @Test
    fun openVotedPoll_showsViewVotersAndRemoveButtons() {
        val poll = CookedPostBlock.Poll(
            name = "poll",
            type = "regular",
            status = "open",
            voters = 5,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 5, selected = true),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            27,
        )
        block.populateButtons(poll.options)

        assertThat(block.canOpenVotersList()).isTrue()
        assertThat(block.shouldDrawInstantButton()).isTrue()
        assertThat(block.instantButtonText()).isEqualTo("查看投票")
        assertThat(block.shouldDrawRemoveVoteButton()).isTrue()
        assertThat(block.shouldDrawInfoFooter()).isFalse()
    }

    @Test
    fun publicVotedPoll_showsViewVotersAndRemoveButtons() {
        val poll = CookedPostBlock.Poll(
            name = "poll",
            type = "regular",
            status = "open",
            voters = 5,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", selected = true),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
            results = "on_close",
            publicPoll = true,
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            28,
        )
        block.populateButtons(poll.options)
        block.measure(1080)

        assertThat(block.canOpenVotersList()).isTrue()
        assertThat(block.shouldDrawInstantButton()).isTrue()
        assertThat(block.instantButtonText()).isEqualTo("查看投票")
        assertThat(block.shouldDrawRemoveVoteButton()).isTrue()
        assertThat(block.shouldDrawInfoFooter()).isFalse()
        assertThat(block.removeVoteButtonBounds().left).isGreaterThan(block.instantButtonBounds().right)
    }

    @Test
    fun pollActionInFlight_disablesVoteAndRemoveActions() {
        val state = PostBodyBlockState()
        state.inFlightPollIndexes.add(29)
        val poll = CookedPostBlock.Poll(
            name = "poll",
            type = "regular",
            status = "open",
            voters = 5,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 5, selected = true),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
            publicPoll = true,
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            state,
            29,
        )
        block.populateButtons(poll.options)

        assertThat(block.canRemoveVote()).isFalse()
        assertThat(block.canVote(1)).isFalse()
        assertThat(block.canInstantButtonAction()).isFalse()
    }

    @Test
    fun closedVotedPoll_hidesRemoveVoteButton() {
        val poll = CookedPostBlock.Poll(
            name = "poll",
            type = "regular",
            status = "closed",
            voters = 5,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 5, selected = true),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            25,
        )

        assertThat(block.shouldDrawRemoveVoteButton()).isFalse()
    }

    @Test
    fun localRemoveVote_clearsServerSelectionOptimistically() {
        val state = PostBodyBlockState()
        val poll = CookedPostBlock.Poll(
            name = "poll",
            type = "regular",
            status = "open",
            voters = 5,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 5, selected = true),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            state,
            26,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldDrawRemoveVoteButton()).isTrue()
        assertThat(block.canVote(0)).isFalse()
        assertThat(block.statusText()).isEqualTo("已投票")

        block.clearLocalVoteState()

        assertThat(state.removedPollIndexes).containsExactly(26)
        assertThat(block.shouldDrawRemoveVoteButton()).isFalse()
        assertThat(block.canVote(0)).isTrue()
        assertThat(block.statusText()).isEmpty()
    }

    @Test
    fun openPollWithOnlyTotalVoters_staysVoteableInsteadOfShowingResults() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "open",
            voters = 12,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A"),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            7,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isFalse()
        assertThat(block.canVote(0)).isTrue()
    }

    @Test
    fun alwaysVisiblePollWithoutOptionResults_doesNotInventResultBars() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "open",
            voters = 12,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A"),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
            results = "always",
            publicPoll = true,
            chartType = "bar",
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            8,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isFalse()
        assertThat(block.canVote(0)).isTrue()
    }

    @Test
    fun alwaysVisiblePollWithZeroOptionVotes_staysVoteableInsteadOfShowingEmptyResults() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "open",
            voters = 0,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 0),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B", votes = 0),
            ),
            results = "always",
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            12,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isFalse()
        assertThat(block.canVote(0)).isTrue()
    }

    @Test
    fun openPollWithServerOptionResults_showsResultsRegardlessOfVisibilityMode() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "open",
            voters = 12,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", votes = 7),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B", percent = 0.4f),
            ),
            results = "on_vote",
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            9,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isTrue()
    }

    @Test
    fun selectedPollHiddenUntilClose_keepsChoiceStateWithoutResultBars() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "open",
            voters = 12,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A", selected = true),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
            results = "on_close",
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            10,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isFalse()
        assertThat(block.canVote(0)).isFalse()
    }

    @Test
    fun closedPollShowsResultsEvenWithoutPerOptionBreakdown() {
        val poll = CookedPostBlock.Poll(
            name = "frontend",
            title = null,
            type = "regular",
            status = "closed",
            voters = 12,
            options = listOf(
                CookedPostBlock.PollOption(id = "option-a", label = "Tool A"),
                CookedPostBlock.PollOption(id = "option-b", label = "Tool B"),
            ),
            results = "on_close",
        )
        val block = PollBlock(
            poll,
            buildContext { _, _ -> },
            PostBodyBlockState(),
            11,
        )
        block.populateButtons(poll.options)

        assertThat(block.shouldShowResults()).isTrue()
        assertThat(block.canVote(0)).isFalse()
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

    private fun PollBlock.shouldShowResults(): Boolean =
        PollBlock::class.java.getDeclaredMethod("shouldShowResults")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.instantButtonText(): String =
        PollBlock::class.java.getDeclaredMethod("instantButtonText")
            .apply { isAccessible = true }
            .invoke(this) as String

    private fun PollBlock.canOpenVotersList(): Boolean =
        PollBlock::class.java.getDeclaredMethod("canOpenVotersList")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.shouldDrawRemoveVoteButton(): Boolean =
        PollBlock::class.java.getDeclaredMethod("shouldDrawRemoveVoteButton")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.shouldDrawInstantButton(): Boolean =
        PollBlock::class.java.getDeclaredMethod("shouldDrawInstantButton")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.shouldDrawInfoFooter(): Boolean =
        PollBlock::class.java.getDeclaredMethod("shouldDrawInfoFooter")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.canRemoveVote(): Boolean =
        PollBlock::class.java.getDeclaredMethod("canRemoveVote")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.instantButtonBounds(): RectF =
        PollBlock::class.java.getDeclaredField("instantButtonBounds")
            .apply { isAccessible = true }
            .get(this) as RectF

    private fun PollBlock.removeVoteButtonBounds(): RectF =
        PollBlock::class.java.getDeclaredField("removeVoteButtonBounds")
            .apply { isAccessible = true }
            .get(this) as RectF

    private fun PollBlock.statusText(): String =
        PollBlock::class.java.getDeclaredMethod("statusText")
            .apply { isAccessible = true }
            .invoke(this) as String

    private fun PollBlock.clearLocalVoteState() =
        PollBlock::class.java.getDeclaredMethod("clearLocalVoteState")
            .apply { isAccessible = true }
            .invoke(this)

    private fun PollBlock.canInstantButtonAction(): Boolean =
        PollBlock::class.java.getDeclaredMethod("canInstantButtonAction")
            .apply { isAccessible = true }
            .invoke(this) as Boolean

    private fun PollBlock.canVote(index: Int): Boolean =
        PollBlock::class.java.getDeclaredMethod("canVote", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, index) as Boolean

    private fun PollBlock.markLocalSubmitted() =
        PollBlock::class.java.getDeclaredMethod("markLocalSubmitted")
            .apply { isAccessible = true }
            .invoke(this)

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
