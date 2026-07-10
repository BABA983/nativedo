package io.github.baba983.nativedo.ui.topic.cell

import android.graphics.RectF
import android.view.View
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.feature.topic.ui.PollVotersSheetArgs
import org.telegram.messenger.ImageReceiver

internal interface TopicPostCellDelegate {
    fun didPressLink(url: String) = Unit
    fun didPressUser(username: String) = Unit
    fun didPressTopic(topicId: Long) = Unit
    fun didLongPressLink(url: String) = Unit
    fun didPressReaction(post: Post, reaction: PostReaction, anchorView: View, x: Float, y: Float) = Unit
    fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?, anchorView: View, x: Float, y: Float) = Unit
    fun didPressReply(post: Post) = Unit
    fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) = Unit
    fun didExpandQuote(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) = Unit
    fun didPressPollOptions(post: Post, pollName: String, optionIds: List<String>) = Unit
    fun didPressPollVoteRemoved(post: Post, pollName: String) = Unit
    fun didPressPollVoters(post: Post, args: PollVotersSheetArgs) = Unit
    fun didPressImage(
        post: Post,
        url: String,
        gallery: List<GalleryImage>,
        sourceBoundsInWindow: RectF?,
        hostView: View,
        sourceBoundsProvider: (String) -> RectF?,
        sourceImageReceiverProvider: (String) -> ImageReceiver?,
        sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
        sourceRadiusProvider: (String) -> IntArray?,
    ) = Unit
}
