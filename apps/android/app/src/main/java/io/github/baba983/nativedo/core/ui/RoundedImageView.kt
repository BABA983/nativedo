// NativeDo: thin ImageView wrapper around upstream ImageReceiver.
// Keeps AppCompatImageView so Coil's load() extension works.
package io.github.baba983.nativedo.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.telegram.messenger.ImageReceiver

/**
 * ImageView-compatible source cell that draws through [ImageReceiver].
 *
 * Keeping the inline cell and PhotoViewer transition on the same receiver-style
 * draw region avoids the Outline-vs-Path mismatch that made rounded images look
 * subtly clipped during source handoff.
 */
class RoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    val imageReceiver: ImageReceiver = ImageReceiver(this)

    var cornerRadiusPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                imageReceiver.setRoundRadius(value.toInt())
                invalidate()
            }
        }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(null)
        imageReceiver.setImageBitmap(bm)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(null)
        imageReceiver.setImageBitmap(extractBitmap(drawable))
    }

    private fun extractBitmap(drawable: Drawable?): android.graphics.Bitmap? = when (drawable) {
        null -> null
        is BitmapDrawable -> drawable.bitmap
        is coil3.transition.CrossfadeDrawable -> {
            // After the crossfade completes, pull the final bitmap.
            extractBitmap(drawable.end)
        }
        else -> null
    }

    val bitmap: android.graphics.Bitmap?
        get() = imageReceiver.bitmap

    override fun onDraw(canvas: Canvas) {
        if (imageReceiver.hasBitmapImage()) {
            imageReceiver.setImageCoords(0f, 0f, width.toFloat(), height.toFloat())
            imageReceiver.draw(canvas)
        } else {
            super.onDraw(canvas)
        }
    }
}
