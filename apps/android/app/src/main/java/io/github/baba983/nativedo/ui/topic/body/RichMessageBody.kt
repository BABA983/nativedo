package io.github.baba983.nativedo.ui.topic.body

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.text.StaticLayout
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.ui.PostRichMessageBuilder
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.PhotoViewer
import java.io.File
import java.util.ArrayList

/**
 * Roll-out switch for the rich-message body: when [enabled], a post's body is one
 * [RichMessageBodyBlock] (upstream's `RichMessageLayout`) instead of our own block tree.
 *
 * Off by default. The renderer's pixels are covered by `PostRichMessageScreenshotTest`, but the cell
 * path has not run on a device yet (`docs/device-verification.md` §7), and the mapping deliberately
 * drops the Discourse-only blocks (callout, onebox, poll, image/collage), so the switch stays opt-in
 * until that pass and the media bridge land.
 */
internal object RichMessageBody {

    @JvmField
    var enabled: Boolean = false

    @JvmStatic
    fun blocks(cooked: List<CookedPostBlock>, context: PostBodyBuildContext): List<PostBodyBlock> =
        listOf(RichMessageBodyBlock(cooked, context))
}

/**
 * The whole post body as upstream's renderer: one `TL_iv.RichMessage` built from the cooked blocks,
 * laid out by `RichMessageLayout` and drawn into the cell's canvas.
 *
 * The cell keeps doing what it does for every other block - it paints its own background, insets the
 * column and forwards touch - because `RichMessageLayout` draws no message bubble (it draws only
 * quote/pullquote backgrounds). The message is built as an incoming one (no `out` flag), which is
 * what gives a post body the body colours rather than the outgoing variants.
 */
internal class RichMessageBodyBlock(
    private val cooked: List<CookedPostBlock>,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {

    private var messageObject: MessageObject? = null
    private var layout: RichMessageLayout? = null
    private var layoutWidth = -1
    private var attached = false

    /** Where the cell draws this block, learned from the canvas matrix: the viewer needs it. */
    private var originX = 0
    private var originY = 0

    private val delegate = RichMessageBodyDelegate()

    override fun measure(maxWidthPx: Int): Int = layoutFor(maxWidthPx)?.height ?: 0

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val rich = layout ?: return
        // The cell translates the canvas to the block's origin before drawing, so the matrix is where
        // this block sits inside the cell; the PhotoViewer place object needs that origin.
        val matrix = FloatArray(9)
        canvas.matrix.getValues(matrix)
        originX = matrix[Matrix.MTRANS_X].toInt()
        originY = matrix[Matrix.MTRANS_Y].toInt()
        rich.draw(canvas, 0, (parentWidth - rich.minWidth).coerceAtLeast(0), null, 0f, rich.height.toFloat())
        if (rich.hasOverlay()) {
            rich.drawOverlay(canvas, null)
        }
    }

    /** Same column as the text blocks; the renderer insets its own content inside it. */
    override fun horizontalInsetPx(): Int = PostBodyBlock.DEFAULT_HORIZONTAL_INSET

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        layout?.onTouchEvent(event) ?: false

    override fun fillTextLayoutBlocks(
        blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>,
        x: Int,
        y: Int,
    ) {
        val rich = layout ?: return
        for (block in rich.textBlocks) {
            blocks.add(OffsetTextLayoutBlock(block, x, y))
        }
    }

    override fun onAttachedToWindow() {
        attached = true
        layout?.attach(context.host)
    }

    override fun onDetachedFromWindow() {
        attached = false
        layout?.detach(context.host)
    }

    /** The renderer reads theme colours while laying out, so a theme change drops the layout. */
    override fun applyTheme() {
        layout?.detach(context.host)
        layout = null
        layoutWidth = -1
    }

    private fun layoutFor(maxWidthPx: Int): RichMessageLayout? {
        if (maxWidthPx <= 0) return null
        val current = layout
        if (current != null && layoutWidth == maxWidthPx) {
            return current
        }
        val message = messageObject ?: MessageObject(
            UserConfig.selectedAccount,
            TLRPC.TL_message(),
            false,
            false,
        ).apply {
            messageOwner.rich_message = PostRichMessageBuilder.build(cooked)
        }.also { messageObject = it }

        val rich = RichMessageLayout(message, maxWidthPx, null)
        rich.invalidateAnimatedEmojiInParent = true
        rich.setChatMessageCellDelegate(null, delegate)
        if (attached) {
            rich.attach(context.host)
        }
        layout = rich
        layoutWidth = maxWidthPx
        return rich
    }

/**
 * Upstream's image tap, with our viewer: `RichMessageLayout` calls
 * `delegate.openArticlePhoto(cell, block)` when a media block is tapped (the same callback
 * `ChatActivity` implements upstream), and this answers by opening `PhotoViewer` over every media
 * block of the message - `PhotoViewer.openPhoto(index, PageBlocksAdapter, provider)` is the port of
 * upstream's entry point, and it reads each block's URL through `photoItemForPageBlock`.
 */
private inner class RichMessageBodyDelegate : ChatMessageCell.ChatMessageCellDelegate {

    override fun openArticlePhoto(cell: ChatMessageCell?, block: TL_iv.PageBlock?): Boolean {
        val rich = layout ?: return false
        val message = messageObject ?: return false
        val activity = findActivity(context.host.context) ?: return false
        if (block == null) return false

        val media = ArrayList<TL_iv.PageBlock>()
        rich.collectMediaBlocks(media)
        val index = media.indexOfFirst { it === block }
        if (index < 0) return false

        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(activity)
        val placeProvider = RichMessagePlaceProvider(media).apply {
            origin = { intArrayOf(originX, originY) }
            receiverFor = { target, offset -> layout?.findMediaImageReceiver(target, offset) }
            hostProvider = { context.host }
        }
        return viewer.openPhoto(index, RichMessageMediaAdapter(media, message), placeProvider)
    }
}

    /** The renderer's own text blocks, shifted to where the cell draws them. */
    private class OffsetTextLayoutBlock(
        private val inner: TextSelectionHelper.TextLayoutBlock,
        private val dx: Int,
        private val dy: Int,
    ) : TextSelectionHelper.TextLayoutBlock {
        override fun getLayout(): StaticLayout = inner.layout
        override fun getX(): Int = inner.x + dx
        override fun getY(): Int = inner.y + dy
        override fun getRow(): Int = inner.row
        override fun getPrefix(): CharSequence? = inner.prefix
        override fun getPrefixLayout(): StaticLayout? = inner.prefixLayout
        override fun getPrefixX(): Int = inner.prefixX + dx
        override fun getPrefixY(): Int = inner.prefixY + dy
        override fun getClipBounds(): Rect? = inner.clipBounds
    }
}

/**
 * The pin's `ChatArticlePageBlocksAdapter`, reduced to what a Discourse body has: URL images (no
 * `TLRPC` media, no files, no video), one caption per block - the group caption a folded collage
 * carries, or the photo's own.
 */
internal class RichMessageMediaAdapter(
    private val blocks: List<TL_iv.PageBlock>,
    private val messageObject: MessageObject,
) : PhotoViewer.PageBlocksAdapter {

    override fun getItemsCount(): Int = blocks.size

    override fun get(index: Int): TL_iv.PageBlock = blocks[index]

    override fun getAll(): List<TL_iv.PageBlock> = blocks

    override fun isVideo(index: Int): Boolean = false

    override fun isHardwarePlayer(index: Int): Boolean = false

    override fun getMedia(index: Int): TLObject? = null

    override fun getFile(index: Int): File? = null

    override fun getFileName(index: Int): String? = urlOf(index)

    /**
     * Upstream answers null here on purpose (`ChatArticlePageBlocksAdapter.getCaption`): a rich
     * message already draws the caption under its image, so the viewer does not repeat it.
     */
    override fun getCaption(index: Int): CharSequence? = null

    override fun getFileLocation(media: TLObject?, size: IntArray?): TLRPC.PhotoSize? = null

    override fun updateSlideshowCell(currentPageBlock: TL_iv.PageBlock?) = Unit

    override fun getParentObject(): Any = messageObject

    private fun urlOf(index: Int): String? = blocks.getOrNull(index)?.url

}

private class RichMessagePlaceProvider(
    private val media: List<TL_iv.PageBlock>,
) : PhotoViewer.EmptyPhotoViewerProvider() {

    /** Set by the block before opening, so the place object can be computed in window coordinates. */
    var origin: (() -> IntArray)? = null
    var receiverFor: ((TL_iv.PageBlock, IntArray) -> ImageReceiver?)? = null
    var hostProvider: (() -> android.view.View?)? = null

    override fun getPlaceForPhoto(
        messageObject: MessageObject?,
        fileLocation: TLRPC.FileLocation?,
        index: Int,
        needPreview: Boolean,
        closing: Boolean,
    ): PhotoViewer.PlaceProviderObject? {
        val target = media.getOrNull(index) ?: return null
        val host = hostProvider?.invoke() ?: return null
        val offset = IntArray(2)
        val receiver = receiverFor?.invoke(target, offset) ?: return null
        val origin = origin?.invoke() ?: return null

        val window = IntArray(2)
        host.getLocationInWindow(window)
        val object_ = PhotoViewer.PlaceProviderObject()
        object_.viewX = (window[0] + origin[0] + offset[0] - receiver.imageX).toInt()
        object_.viewY = (window[1] + origin[1] + offset[1] - receiver.imageY).toInt()
        object_.viewWidth = receiver.imageWidth.toInt()
        object_.viewHeight = receiver.imageHeight.toInt()
        object_.parentView = host
        object_.imageReceiver = receiver
        object_.thumb = if (needPreview) receiver.bitmapSafe else null
        object_.radius = if (needPreview) receiver.getRoundRadius(true) else null
        object_.scale = 1.0f
        return object_
    }

    override fun getThumbForPhoto(
        messageObject: MessageObject?,
        fileLocation: TLRPC.FileLocation?,
        index: Int,
    ): ImageReceiver.BitmapHolder? {
        val target = media.getOrNull(index) ?: return null
        val offset = IntArray(2)
        return receiverFor?.invoke(target, offset)?.bitmapSafe
    }
}

private fun findActivity(context: Context?): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
