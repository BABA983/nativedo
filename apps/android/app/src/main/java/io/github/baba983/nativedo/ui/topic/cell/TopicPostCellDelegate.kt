package io.github.baba983.nativedo.ui.topic.cell

import android.graphics.RectF
import android.view.View
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction

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
    fun didPressImage(
        url: String,
        gallery: List<GalleryImage>,
        sourceBoundsInWindow: RectF?,
        hostView: View,
    ) = Unit
}
