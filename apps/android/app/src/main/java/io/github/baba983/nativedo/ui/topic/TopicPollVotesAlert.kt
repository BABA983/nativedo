package io.github.baba983.nativedo.ui.topic

import android.content.Context
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.ui.PollVotersSheetArgs
import org.telegram.messenger.MessageObject
import org.telegram.messenger.UserConfig
import org.telegram.messenger.nativedo.PollVotesBridge
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.PollVotesAlert

/**
 * Shows Telegram's poll-votes sheet (vendored [PollVotesAlert]) for a Discourse poll: the poll is
 * rebuilt as Telegram's [TLRPC.Poll], and voter pages are served through [PollVotesBridge] from
 * `polls/voters.json` instead of `messages.getPollVotes`.
 */
internal object TopicPollVotesAlert {

    internal const val PAGE_SIZE = 50

    fun show(
        context: Context,
        baseUrl: String,
        postId: Long,
        args: PollVotersSheetArgs,
        loadVoters: (optionId: String, page: Int, onResult: (Result<List<PostReactionUser>>) -> Unit) -> Unit,
        onUserClick: (String) -> Unit,
    ) {
        val optionTotals = args.options.associate { option -> option.id to option.votes }
        PollVotesBridge.setProvider { request, callback ->
            val optionId = String(request.option ?: ByteArray(0), Charsets.UTF_8)
            val page = request.offset?.toIntOrNull() ?: 1
            loadVoters(optionId, page) { result ->
                callback.onVotes(
                    buildVotesList(
                        baseUrl = baseUrl,
                        voters = result.getOrElse { emptyList() },
                        optionId = optionId,
                        optionTotal = optionTotals[optionId] ?: 0,
                        page = page,
                    ),
                )
            }
            true
        }
        PollVotesBridge.setUserOpener { user ->
            val username = user.username
            if (username.isNullOrBlank()) {
                false
            } else {
                onUserClick(username)
                true
            }
        }

        val alert = PollVotesAlert(context, UserConfig.selectedAccount, buildMessage(postId, args), null)
        alert.setOnDismissListener(android.content.DialogInterface.OnDismissListener {
            PollVotesBridge.setProvider(null)
            PollVotesBridge.setUserOpener(null)
        })
        alert.show()
    }

    internal fun buildMessage(postId: Long, args: PollVotersSheetArgs): MessageObject {
        val poll = TLRPC.TL_poll()
        poll.id = postId
        poll.question = textWithEntities(args.pollTitle?.takeIf { it.isNotBlank() } ?: args.pollName)
        poll.multiple_choice = args.multiple
        for (option in args.options) {
            val answer = TLRPC.TL_pollAnswer()
            answer.option = option.id.toByteArray(Charsets.UTF_8)
            answer.text = textWithEntities(option.label)
            poll.answers.add(answer)
        }

        val results = TLRPC.TL_pollResults()
        results.total_voters = args.totalVoters
        for (option in args.options) {
            val answerVoters = TLRPC.TL_pollAnswerVoters()
            answerVoters.option = option.id.toByteArray(Charsets.UTF_8)
            answerVoters.voters = option.votes
            answerVoters.chosen = option.id == args.initialSelectedOptionId
            results.results.add(answerVoters)
        }

        val media = TLRPC.TL_messageMediaPoll()
        media.poll = poll
        media.results = results

        val owner = TLRPC.TL_message()
        owner.id = postId
        owner.media = media
        return MessageObject(UserConfig.selectedAccount, owner, false, false)
    }

    internal fun buildVotesList(
        baseUrl: String,
        voters: List<PostReactionUser>,
        optionId: String,
        optionTotal: Int,
        page: Int,
    ): TLRPC.TL_messages_votesList {
        val list = TLRPC.TL_messages_votesList()
        list.count = optionTotal.coerceAtLeast(voters.size)
        list.next_offset = if (voters.size >= PAGE_SIZE && page * PAGE_SIZE < list.count) {
            (page + 1).toString()
        } else {
            null
        }
        val option = optionId.toByteArray(Charsets.UTF_8)
        for (voter in voters) {
            val user = buildVoter(baseUrl, voter)
            val vote = TLRPC.TL_messagePeerVote()
            vote.peer = TLRPC.TL_peerUser().apply { user_id = user.id }
            vote.option = option
            list.users.add(user)
            list.votes.add(vote)
        }
        return list
    }

    private fun buildVoter(baseUrl: String, voter: PostReactionUser): TLRPC.TL_user {
        val user = TLRPC.TL_user()
        user.id = voter.id.takeIf { it > 0 }
            ?: (voter.username.hashCode().toLong() and 0x7fffffffL).coerceAtLeast(1L)
        user.first_name = voter.displayName.ifBlank { voter.username }
        user.username = voter.username
        val avatarUrl = resolveAvatarUrl(
            baseUrl = baseUrl,
            avatarTemplate = voter.avatarTemplate,
            animatedAvatarUrl = voter.animatedAvatarUrl,
            size = 96,
        )
        if (avatarUrl.isNotBlank()) {
            user.nativeDoAvatarUrl = avatarUrl
        }
        return user
    }

    private fun textWithEntities(text: String): TLRPC.TL_textWithEntities {
        val result = TLRPC.TL_textWithEntities()
        result.text = text
        return result
    }
}
