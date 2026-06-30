package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.view.MotionEvent
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList

internal interface PostBodyBlock {
    fun measure(maxWidthPx: Int): Int
    fun draw(canvas: Canvas, parentWidth: Int)

    /**
     * Horizontal inset (in px) the host container applies to this block at the
     * post's top level. Mirrors ArticleViewer's per-cell self-managed inset:
     * text-content cells inset by [DEFAULT_HORIZONTAL_INSET]; media cells
     * (image/code/table/collage) and cells that already inset internally
     * (quote/poll) return 0 so they span the full content width. Onebox uses
     * the same 18dp host inset as text blocks so its ChatMessageCell-style rail
     * starts near the body text column, then applies ChatMessageCell's internal
     * link-preview `linkX -= dp(1.33f)` shift with preview text another 10dp inward.
     *
     * Only consulted for top-level blocks; nested children are positioned by
     * their parent block, so this is never stacked.
     */
    fun horizontalInsetPx(): Int = DEFAULT_HORIZONTAL_INSET
    fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean = false
    fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) = Unit
    fun imageBoundsInBlock(imageUrl: String): RectF? = null
    fun imageReceiverForUrl(imageUrl: String): ImageReceiver? = null
    fun imageBitmapForUrl(imageUrl: String): ImageReceiver.BitmapHolder? = imageReceiverForUrl(imageUrl)?.getBitmapSafe()
    fun imageRadiusForUrl(imageUrl: String): IntArray? = imageReceiverForUrl(imageUrl)?.getRoundRadius(true)?.copyOf()
    fun onAttachedToWindow() = Unit
    fun onDetachedFromWindow() = Unit
    fun applyTheme() = Unit

    companion object {
        @JvmField
        val DEFAULT_HORIZONTAL_INSET: Int = AndroidUtilities.dp(18f)
    }
}

internal class PostSelectionLayoutBlock @JvmOverloads constructor(
    private val layout: StaticLayout,
    private val x: Int,
    private val y: Int,
    private val row: Int = 0,
    private val prefix: CharSequence? = null,
    private val prefixLayout: StaticLayout? = null,
    private val prefixX: Int = x,
    private val prefixY: Int = y,
    private val clipBounds: Rect? = null,
) : TextSelectionHelper.TextLayoutBlock {
    override fun getLayout(): StaticLayout = layout
    override fun getX(): Int = x
    override fun getY(): Int = y
    override fun getRow(): Int = row
    override fun getPrefix(): CharSequence? = prefix
    override fun getPrefixLayout(): StaticLayout? = prefixLayout
    override fun getPrefixX(): Int = prefixX
    override fun getPrefixY(): Int = prefixY
    override fun getClipBounds(): Rect? = clipBounds
}
