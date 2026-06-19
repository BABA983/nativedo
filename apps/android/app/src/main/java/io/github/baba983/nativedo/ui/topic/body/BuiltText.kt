package io.github.baba983.nativedo.ui.topic.body

import android.text.Spannable
import android.text.SpannableString
import android.view.View
import io.github.baba983.nativedo.core.ui.PendingInlineImage
import io.github.baba983.nativedo.core.ui.isAnimatedDiscourseImageUrl
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import kotlin.math.ceil
import kotlin.math.max

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
                setInvalidateAll(true)
                setAllowLoadingOnAttachedOnly(true)
                setAllowDecodeSingleFrame(!isAnimatedDiscourseImageUrl(pendingImage.resolvedUrl))
                setImage(
                    ImageLocation.getForPath(pendingImage.resolvedUrl),
                    pendingImage.imageFilter(),
                    null,
                    ImageLoader.getHttpUrlExtension(pendingImage.resolvedUrl, "jpg"),
                    pendingImage.resolvedUrl,
                    0,
                )
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

private fun PendingInlineImage.imageFilter(): String {
    val density = max(1f, AndroidUtilities.density)
    val widthDp = max(1, ceil(drawable.bounds.width() / density).toInt())
    val heightDp = max(1, ceil(drawable.bounds.height() / density).toInt())
    return "${widthDp}_${heightDp}_i"
}
