/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockPhotoCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList
import kotlin.math.roundToInt

internal class ImageBlock(
    private val image: CookedPostBlock.Image,
    private val gallery: List<GalleryImage>,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private val imageUrl = resolvePostImageUrl(context.baseUrl, image.src)
    private val targetUrl = image.href?.let { resolvePostImageUrl(context.baseUrl, it) } ?: imageUrl
    private val receiver: ImageReceiver = context.imagePool.obtain("image:$targetUrl")
    private val imageRect = RectF()
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.mediaPlaceholderColor }
    private val captionPaint = TextPaint(context.quotePaint).apply {
        color = context.secondaryTextColor
        textSize = AndroidUtilities.dp(13f).toFloat()
    }
    private var captionLayout: StaticLayout? = null
    private var measuredHeight = 0
    private var pressed = false

    init {
        receiver.setRoundRadius(AndroidUtilities.dp(14f))
        receiver.setAspectFit(true)
        context.imagePool.setImage(receiver, imageUrl)
    }

    override fun measure(maxWidthPx: Int): Int {
        val width = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        val aspect = image.aspectRatio ?: 1.5f
        var imageWidth = width
        var imageHeight = (imageWidth / aspect).roundToInt()
        val maxHeight = ((maxOf(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(56f)) * 0.9f).roundToInt()
        if (imageHeight > maxHeight) {
            imageHeight = maxHeight
            imageWidth = (imageHeight * aspect).roundToInt().coerceAtMost(width)
        }
        val imageLeft = (width - imageWidth) / 2f
        imageRect.set(imageLeft, 0f, imageLeft + imageWidth, imageHeight.toFloat())
        captionLayout = image.alt.takeIf { it.isNotBlank() }?.let { alt ->
            StaticLayout.Builder.obtain(alt, 0, alt.length, captionPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
        }
        measuredHeight = imageHeight + captionLayout?.let { AndroidUtilities.dp(6f) + it.height } .orZero()
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val hidden = context.hiddenImageUrlProvider() == targetUrl
        canvas.drawRoundRect(imageRect, AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(14f).toFloat(), placeholderPaint)
        if (!hidden) {
            receiver.setBounds(imageRect)
            receiver.draw(canvas)
        }
        captionLayout?.let { layout ->
            canvas.save()
            canvas.translate(0f, imageRect.bottom + AndroidUtilities.dp(6f))
            layout.draw(canvas)
            canvas.restore()
        }
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (!imageRect.contains(localX, localY)) {
            if (pressed && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP)) {
                pressed = false
                context.host.invalidate()
            }
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressed = true
            context.host.invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            pressed = false
            context.host.invalidate()
            context.delegate.onImageClick(
                url = targetUrl,
                gallery = gallery,
                sourceBoundsInBlock = imageBoundsInCell(),
                hostView = context.host,
                sourceBoundsProvider = { imageUrl -> if (imageUrl == targetUrl) imageBoundsInCell() else null },
                sourceImageReceiverProvider = { imageUrl -> if (imageUrl == targetUrl) receiver else null },
                sourceBitmapProvider = { imageUrl -> if (imageUrl == targetUrl) receiver.getBitmapSafe() else null },
                sourceRadiusProvider = { imageUrl -> if (imageUrl == targetUrl) receiver.getRoundRadius(true).copyOf() else null },
            )
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pressed = false
            context.host.invalidate()
            return true
        }
        return pressed
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        val layout = captionLayout ?: return
        blocks.add(PostSelectionLayoutBlock(layout, x, y + imageRect.bottom.toInt() + AndroidUtilities.dp(6f)))
    }

    override fun imageBoundsInBlock(imageUrl: String): RectF? {
        return if (imageUrl == targetUrl) imageBoundsInCell() else null
    }

    override fun imageReceiverForUrl(imageUrl: String): ImageReceiver? {
        return if (imageUrl == targetUrl) receiver else null
    }

    override fun onAttachedToWindow() {
        receiver.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        receiver.onDetachedFromWindow()
    }

    fun imageBoundsInCell(): RectF = RectF(imageRect)

    private fun Int?.orZero(): Int = this ?: 0
}
