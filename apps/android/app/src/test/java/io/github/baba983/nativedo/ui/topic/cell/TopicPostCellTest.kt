package io.github.baba983.nativedo.ui.topic.cell

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.PostReaction
import org.junit.Test

class TopicPostCellTest {
    @Test
    fun buildReactionButtons_addsPlusButtonWhenValidReactionsExist() {
        val buttons = TopicPostCell.buildReactionButtonSpecs(
            emptyList<PostReaction>(),
            listOf("heart", "+1"),
            emptyMap<String, String>(),
            3,
        )

        assertThat(buttons.map { it.countText }).containsExactly("+")
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
    fun buildReactionButtons_usesOverflowButtonWhenExistingReactionsExceedVisibleLimit() {
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

        assertThat(buttons.map { it.countText }.last()).isEqualTo("+1")
    }
}
