package io.github.baba983.nativedo.ui.topic.body

import android.graphics.RectF
import android.view.View
import io.github.baba983.nativedo.core.html.GalleryImage
import org.telegram.messenger.ImageReceiver

internal interface PostBodyDelegate {
    fun onLinkClick(url: String)
    fun onTopicClick(topicId: Long)
    fun onUserClick(username: String)
    fun onLinkLongPress(url: String) = Unit
    fun onImageClick(
        url: String,
        gallery: List<GalleryImage>,
        sourceBoundsInBlock: RectF?,
        hostView: View,
        sourceBoundsProvider: (String) -> RectF? = { imageUrl -> if (imageUrl == url) sourceBoundsInBlock else null },
        sourceImageReceiverProvider: (String) -> ImageReceiver? = { null },
        sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder? = { null },
        sourceRadiusProvider: (String) -> IntArray? = { null },
    ) = Unit
    fun onQuoteExpand(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) = Unit
    fun onPollOptionSelected(pollName: String, optionId: String) = Unit
}
