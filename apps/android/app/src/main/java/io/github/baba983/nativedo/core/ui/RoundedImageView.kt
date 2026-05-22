// PORT-MODE: Native Feature
// HOST-WIRING:
//   - View wrapper around NativeDo's minimal ImageReceiver so PhotoViewer source
//     providers can use ImageReceiver-shaped geometry, visibility, bitmap, and radius.
package io.github.baba983.nativedo.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil3.transition.CrossfadeDrawable
import io.github.baba983.nativedo.telegram.messenger.ImageReceiver

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
                imageReceiver.setRoundRadius(value)
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
        is CrossfadeDrawable -> (drawable.end as? BitmapDrawable)?.bitmap
            ?: (drawable.start as? BitmapDrawable)?.bitmap
        else -> null
    }

    val bitmap: android.graphics.Bitmap?
        get() = imageReceiver.bitmap

    override fun setScaleType(scaleType: ScaleType?) {
        super.setScaleType(scaleType)
        imageReceiver.setScaleType(
            if (scaleType == ScaleType.CENTER_CROP) {
                ImageReceiver.ScaleType.CENTER_CROP
            } else {
                ImageReceiver.ScaleType.FIT_CENTER
            },
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        imageReceiver.setImageCoords(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        imageReceiver.draw(canvas)
    }
}
