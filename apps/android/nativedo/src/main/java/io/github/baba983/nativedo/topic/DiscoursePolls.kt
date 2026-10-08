package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostPoll
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import java.nio.charset.StandardCharsets

/** What a tap on a poll answer means for the site: send [digests], or retract the vote. */
class PollVoteSelection(
    @JvmField val digests: List<String>,
    @JvmField val retract: Boolean,
)

/**
 * A Discourse poll as the message upstream's poll cell draws.
 *
 * Upstream has a complete poll UI (buttons, per-answer counts, checkboxes, vote animation) and it
 * reads everything off `TL_messageMediaPoll`: the answers, the counts, and the reader's own choice
 * as `PollAnswerVoters.chosen`. Discourse's poll payload carries exactly that, so the mapping is a
 * translation - except for the option's identity: Discourse votes by the option's digest, which is
 * an opaque string, and upstream compares options as `byte[]`, so the digest's own bytes are the
 * option. They round-trip, and the answer's `text` stays what the reader sees.
 */
object DiscoursePolls {

    /**
     * The poll of [media] within [post], or null when the media is not one of the post's polls.
     *
     * A poll bubble is one segment of a post, so the poll it draws is found by the id the site
     * assigned it - with the option digests as a fallback for a payload that omits the id.
     */
    @JvmStatic
    fun pollOf(post: Post, media: TLRPC.MessageMedia): PostPoll? {
        val poll = (media as? TLRPC.TL_messageMediaPoll)?.poll ?: return null
        post.polls.firstOrNull { candidate -> candidate.id != 0L && candidate.id == poll.id }?.let { return it }
        val digests = poll.answers.map { answer -> String(answer.option, StandardCharsets.UTF_8) }.toSet()
        return post.polls.firstOrNull { candidate ->
            candidate.options.isNotEmpty() && candidate.options.all { option -> option.id in digests }
        }
    }

    /**
     * What a tap on one answer does: a single-choice poll replaces the vote, and re-tapping the
     * chosen answer retracts it; a multiple-choice poll sends the whole selection, and an empty one
     * retracts. Null means the poll takes no tap at all (closed, or ranked, which needs an order a
     * tap cannot express).
     */
    @JvmStatic
    fun selectionFor(poll: PostPoll, digest: String): PollVoteSelection? {
        if (poll.isClosed || poll.type == "ranked_choice" || digest.isEmpty()) {
            return null
        }
        val chosen = poll.chosenDigests
        if (poll.isMultiple) {
            val next = if (digest in chosen) chosen - digest else chosen + digest
            return if (next.isEmpty()) {
                PollVoteSelection(emptyList(), retract = true)
            } else {
                PollVoteSelection(next.toList(), retract = false)
            }
        }
        return if (digest in chosen) {
            PollVoteSelection(emptyList(), retract = true)
        } else {
            PollVoteSelection(listOf(digest), retract = false)
        }
    }

    /**
     * A post's poll as the media of its own message. The question is the site's own poll title: the
     * body already carries the post's prose, and a poll the site gave a title to is the one place
     * that title belongs - upstream draws it above the options, where an empty question would only
     * leave a blank line. A poll the site left untitled keeps an empty question.
     */
    @JvmStatic
    fun media(poll: PostPoll): TLRPC.TL_messageMediaPoll {
        val chosen = poll.chosenDigests
        val answers = ArrayList<TLRPC.PollAnswer>()
        val results = ArrayList<TLRPC.PollAnswerVoters>()
        var counted = 0
        for (option in poll.options) {
            val optionBytes = option.id.toByteArray(StandardCharsets.UTF_8)
            val answer = TLRPC.TL_pollAnswer()
            answer.text = TLRPC.TL_textWithEntities()
            answer.text.text = option.label
            answer.option = optionBytes
            answers.add(answer)

            val voters = TLRPC.TL_pollAnswerVoters()
            voters.option = optionBytes
            voters.chosen = option.chosen || option.id in chosen
            // A missing count is the site saying "not visible yet", not zero, and upstream draws
            // the answer without a number until there is one.
            voters.voters = option.votes ?: 0
            results.add(voters)
            counted += option.votes ?: 0
        }

        val tlPoll = TLRPC.TL_poll()
        tlPoll.id = poll.id
        tlPoll.question = TLRPC.TL_textWithEntities()
        tlPoll.question.text = poll.title.trim()
        tlPoll.answers = answers
        tlPoll.closed = poll.isClosed
        tlPoll.public_voters = poll.isPublic
        tlPoll.multiple_choice = poll.isMultiple
        tlPoll.hide_results_until_close = poll.results == "on_close"

        val tlResults = TLRPC.TL_pollResults()
        tlResults.results = results
        tlResults.total_voters = if (poll.voters > 0) poll.voters else counted

        val media = TLRPC.TL_messageMediaPoll()
        media.poll = tlPoll
        media.results = tlResults
        return media
    }

    /**
     * A poll bubble: one floor's poll as the message upstream's poll cell draws, from the same
     * author and the same floor as the body's own bubbles.
     */
    @JvmStatic
    @JvmOverloads
    fun message(
        post: Post,
        poll: PostPoll,
        topicId: Long,
        users: HashMap<Long, TLRPC.User>,
        baseUrl: String,
        replyTo: TLRPC.Message? = null,
    ): MessageObject {
        val user = DiscourseRows.author(post, baseUrl)
        users[user.id] = user
        val message = DiscourseRows.localMessage(
            post.id.toInt(),
            DiscourseRows.epochSeconds(post.createdAt),
            topicId,
            user.id,
            media(poll),
        )
        // A poll the floor's body opens with is that floor's first bubble, so it carries the reply
        // header like any other first bubble (set before the MessageObject reads it).
        DiscourseRows.applyReply(message, replyTo)
        return DiscourseRows.messageObject(message, users)
    }
}
