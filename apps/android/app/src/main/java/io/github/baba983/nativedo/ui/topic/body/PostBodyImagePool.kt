package io.github.baba983.nativedo.ui.topic.body

import android.graphics.RectF
import android.view.View
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver

internal class PostBodyImagePool(
    private val host: View,
) {
    private val receivers = linkedMapOf<String, ImageReceiver>()

    fun obtain(key: String): ImageReceiver {
        return receivers.getOrPut(key) {
            ImageReceiver(host).apply {
                setAllowLoadingOnAttachedOnly(true)
            }
        }
    }

    fun setImage(receiver: ImageReceiver, url: String, filter: String? = null) {
        receiver.setImage(ImageLocation.getForPath(url), filter, null, null, url, 0)
    }

    fun onAttachedToWindow() {
        receivers.values.forEach(ImageReceiver::onAttachedToWindow)
    }

    fun onDetachedFromWindow() {
        receivers.values.forEach(ImageReceiver::onDetachedFromWindow)
    }

    fun clear() {
        receivers.values.forEach { receiver ->
            receiver.onDetachedFromWindow()
        }
        receivers.clear()
    }
}

internal fun ImageReceiver.setBounds(rect: RectF) {
    setImageCoords(rect.left, rect.top, rect.width(), rect.height())
}
