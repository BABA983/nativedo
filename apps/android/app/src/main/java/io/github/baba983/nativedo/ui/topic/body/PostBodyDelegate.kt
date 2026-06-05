package io.github.baba983.nativedo.ui.topic.body

import android.graphics.RectF
import android.view.View
import io.github.baba983.nativedo.core.html.GalleryImage

internal interface PostBodyDelegate {
    fun onLinkClick(url: String)
    fun onTopicClick(topicId: Long)
    fun onUserClick(username: String)
    fun onLinkLongPress(url: String) = Unit
    fun onImageClick(
        url: String,
        gallery: List<GalleryImage>,
        sourceBoundsInWindow: RectF?,
        hostView: View,
    ) = Unit
    fun onQuoteExpand(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) = Unit
}
