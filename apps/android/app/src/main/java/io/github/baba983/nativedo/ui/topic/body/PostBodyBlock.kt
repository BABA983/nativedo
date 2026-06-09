package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.RectF
import android.text.StaticLayout
import android.view.MotionEvent
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList

internal interface PostBodyBlock {
    fun measure(maxWidthPx: Int): Int
    fun draw(canvas: Canvas, parentWidth: Int)
    fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean = false
    fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) = Unit
    fun imageBoundsInBlock(imageUrl: String): RectF? = null
    fun imageReceiverForUrl(imageUrl: String): ImageReceiver? = null
    fun imageBitmapForUrl(imageUrl: String): ImageReceiver.BitmapHolder? = imageReceiverForUrl(imageUrl)?.getBitmapSafe()
    fun imageRadiusForUrl(imageUrl: String): IntArray? = imageReceiverForUrl(imageUrl)?.getRoundRadius(true)?.copyOf()
    fun onAttachedToWindow() = Unit
    fun onDetachedFromWindow() = Unit
    fun applyTheme() = Unit
}

internal class PostSelectionLayoutBlock(
    private val layout: StaticLayout,
    private val x: Int,
    private val y: Int,
    private val row: Int = 0,
    private val prefix: CharSequence? = null,
) : TextSelectionHelper.TextLayoutBlock {
    override fun getLayout(): StaticLayout = layout
    override fun getX(): Int = x
    override fun getY(): Int = y
    override fun getRow(): Int = row
    override fun getPrefix(): CharSequence? = prefix
}
