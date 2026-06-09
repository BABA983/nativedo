package io.github.baba983.nativedo.ui.topic.cell

import android.graphics.RectF
import android.view.View
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import org.telegram.messenger.ImageReceiver

internal interface TopicPostCellDelegate {
    fun didPressLink(url: String) = Unit
    fun didPressUser(username: String) = Unit
    fun didPressTopic(topicId: Long) = Unit
    fun didLongPressLink(url: String) = Unit
    fun didPressReaction(post: Post, reaction: PostReaction) = Unit
    fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?) = Unit
    fun didPressReply(post: Post) = Unit
    fun didLongPressPost(post: Post) = Unit
    fun didExpandQuote(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) = Unit
    fun didPressPollOption(post: Post, pollName: String, optionId: String) = Unit
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
