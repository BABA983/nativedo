package io.github.baba983.nativedo.ui.topic.body

import android.text.Spannable
import android.text.SpannableString
import android.view.View
import io.github.baba983.nativedo.core.ui.PendingInlineImage
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver

internal data class LinkRegion(
    val start: Int,
    val end: Int,
    val url: String,
    val onClick: () -> Unit,
)

internal class BuiltText(
    val spannable: Spannable,
    val pendingImages: List<PendingInlineImage>,
    val links: List<LinkRegion>,
) {
    fun attach(host: View): Handle {
        val receivers = pendingImages.map { pendingImage ->
            ImageReceiver(host).apply {
                setAllowLoadingOnAttachedOnly(true)
                setImage(ImageLocation.getForPath(pendingImage.resolvedUrl), null, null, null, pendingImage.resolvedUrl, 0)
                if (host.isAttachedToWindow) {
                    onAttachedToWindow()
                }
            }.also { receiver ->
                pendingImage.drawable.setImageReceiver(receiver)
            }
        }
        host.invalidate()
        return Handle(host, pendingImages, receivers)
    }

    class Handle(
        private val host: View,
        private val pendingImages: List<PendingInlineImage>,
        private val receivers: List<ImageReceiver>,
    ) {
        fun detach() {
            pendingImages.forEach { pendingImage ->
                pendingImage.drawable.setImageReceiver(null)
            }
            receivers.forEach(ImageReceiver::onDetachedFromWindow)
            host.invalidate()
        }
    }
}

internal fun CharSequence.asSpannable(): Spannable {
    return this as? Spannable ?: SpannableString(this)
}
