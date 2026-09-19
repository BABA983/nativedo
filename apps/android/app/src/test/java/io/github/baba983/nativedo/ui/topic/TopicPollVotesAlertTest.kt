package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.feature.topic.ui.PollOptionRef
import io.github.baba983.nativedo.feature.topic.ui.PollVotersSheetArgs
import org.junit.Test
import org.telegram.tgnet.TLRPC

class TopicPollVotesAlertTest {

    @Test
    fun buildMessageMapsDiscoursePollOntoTelegramPoll() {
        val message = TopicPollVotesAlert.buildMessage(
            postId = 43L,
            args = PollVotersSheetArgs(
                pollName = "favorite",
                pollTitle = "Which option do you prefer?",
                options = listOf(
                    PollOptionRef(id = "first", label = "First option", votes = 3),
                    PollOptionRef(id = "second", label = "Second option", votes = 2),
                ),
                initialSelectedOptionId = "second",
                totalVoters = 5,
                multiple = true,
            ),
        )

        val media = message.messageOwner.media as TLRPC.TL_messageMediaPoll
        assertThat(media.poll.question.text).isEqualTo("Which option do you prefer?")
        assertThat(media.poll.multiple_choice).isTrue()
        assertThat(media.poll.answers.map { String(it.option, Charsets.UTF_8) })
            .containsExactly("first", "second").inOrder()
        assertThat(media.poll.answers.map { it.text.text })
            .containsExactly("First option", "Second option").inOrder()
        assertThat(media.results.total_voters).isEqualTo(5)
        assertThat(media.results.results.map { it.voters }).containsExactly(3, 2).inOrder()
        assertThat(media.results.results.map { it.chosen }).containsExactly(false, true).inOrder()
        assertThat(message.getId()).isEqualTo(43)
    }

    @Test
    fun buildMessageFallsBackToPollNameForTitle() {
        val message = TopicPollVotesAlert.buildMessage(
            postId = 7L,
            args = PollVotersSheetArgs(pollName = "poll-name"),
        )

        val media = message.messageOwner.media as TLRPC.TL_messageMediaPoll
        assertThat(media.poll.question.text).isEqualTo("poll-name")
    }

    @Test
    fun buildVotesListPagesUntilOptionTotalIsReached() {
        val voters = (1..TopicPollVotesAlert.PAGE_SIZE).map { index ->
            PostReactionUser(
                id = index.toLong(),
                username = "user-$index",
                displayName = "User $index",
                avatarTemplate = "/user_avatar/linux.do/user-$index/{size}.png",
            )
        }

        val firstPage = TopicPollVotesAlert.buildVotesList(
            baseUrl = BASE_URL,
            voters = voters,
            optionId = "first",
            optionTotal = TopicPollVotesAlert.PAGE_SIZE + 10,
            page = 1,
        )
        assertThat(firstPage.count).isEqualTo(TopicPollVotesAlert.PAGE_SIZE + 10)
        assertThat(firstPage.next_offset).isEqualTo("2")
        assertThat(firstPage.votes).hasSize(TopicPollVotesAlert.PAGE_SIZE)
        assertThat(firstPage.users).hasSize(TopicPollVotesAlert.PAGE_SIZE)

        val lastPage = TopicPollVotesAlert.buildVotesList(
            baseUrl = BASE_URL,
            voters = voters.take(10),
            optionId = "first",
            optionTotal = TopicPollVotesAlert.PAGE_SIZE + 10,
            page = 2,
        )
        assertThat(lastPage.next_offset).isNull()
    }

    @Test
    fun buildVotesListSynthesizesResolvableVoters() {
        val list = TopicPollVotesAlert.buildVotesList(
            baseUrl = BASE_URL,
            voters = listOf(
                PostReactionUser(
                    id = 0L,
                    username = "alice",
                    displayName = "Alice Chen",
                    avatarTemplate = "/user_avatar/linux.do/alice/{size}.png",
                ),
            ),
            optionId = "first",
            optionTotal = 1,
            page = 1,
        )

        val user = list.users.single()
        assertThat(user.id).isGreaterThan(0L)
        assertThat(user.first_name).isEqualTo("Alice Chen")
        assertThat(user.username).isEqualTo("alice")
        assertThat(user.nativeDoAvatarUrl).isEqualTo("$BASE_URL/user_avatar/linux.do/alice/96.png")

        val vote = list.votes.single() as TLRPC.TL_messagePeerVote
        assertThat(vote.option).isEqualTo("first".toByteArray(Charsets.UTF_8))
        assertThat((vote.peer as TLRPC.TL_peerUser).user_id).isEqualTo(user.id)
    }

    @Test
    fun buildVotesListLeavesAvatarEmptyWhenTemplateIsMissing() {
        val list = TopicPollVotesAlert.buildVotesList(
            baseUrl = BASE_URL,
            voters = listOf(PostReactionUser(id = 5L, username = "bob")),
            optionId = "first",
            optionTotal = 1,
            page = 1,
        )

        assertThat(list.users.single().nativeDoAvatarUrl).isNull()
    }

    private companion object {
        const val BASE_URL = "https://linux.do"
    }
}
