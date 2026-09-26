package io.github.baba983.nativedo.ui.topic.body

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.text.Layout
import android.text.style.CharacterStyle
import android.text.style.URLSpan
import android.text.StaticLayout
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.ui.PostBodyInternalDestination
import io.github.baba983.nativedo.core.ui.PostBodySegment
import io.github.baba983.nativedo.core.ui.parseInternalLinkDestination
import io.github.baba983.nativedo.core.ui.PostRichMessageBuilder
import io.github.baba983.nativedo.core.ui.splitPostBodySegments
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
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
 * Roll-out switch for the rich-message body: when [enabled], a post's body is upstream's renderer
 * (`RichMessageLayout`, one [RichMessageBodyBlock] per run of mappable blocks) with the
 * Discourse-only blocks (callout, onebox, poll) and any container holding one kept in our own block
 * tree and stacked in place between those runs.
 *
 * Off by default. The renderer's pixels are covered by the `richMessageBody` / `richMessageHybridBody`
 * goldens in `TopicPostCellScreenshotTest`, but the cell path has not run on a device yet
 * (`docs/device-verification.md` §7), so the switch stays opt-in until that pass.
 */
internal object RichMessageBody {

    /** Keys the debug toggle in Settings writes; restored by [loadPreference]. */
    private const val PREF_ENABLED = "nativedo_rich_message_body_enabled"

    @JvmField
    var enabled: Boolean = false

    /**
     * Restores the toggle's persisted choice. Called from the app's entry point, so a device pass
     * (docs/device-verification.md §7) survives a restart without a rebuild.
     */
    @JvmStatic
    fun loadPreference() {
        enabled = MessagesController.getGlobalMainSettings().getBoolean(PREF_ENABLED, false)
    }

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = value
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_ENABLED, value).apply()
    }

    @JvmStatic
    fun blocks(
        cooked: List<CookedPostBlock>,
        context: PostBodyBuildContext,
        state: PostBodyBlockState? = null,
    ): List<PostBodyBlock> {
        val result = ArrayList<PostBodyBlock>()
        val segments = splitPostBodySegments(cooked)
        var index = 0
        for ((segmentIndex, segment) in segments.withIndex()) {
            when (segment) {
                is PostBodySegment.RichRun -> {
                    // The renderer trims the run's first top and last bottom edge (`layout()`), which
                    // is right at the post's own ends but wrong where the run meets a block of ours: a
                    // poll or callout would sit flush against the paragraph above it. The renderer's
                    // own rule for a block that follows something is `dp(6)` of top padding
                    // (`mediaPadding()`), so a run that is not the last segment keeps that much of its
                    // trimmed edge.
                    val trailingGap = if (segmentIndex < segments.size - 1) AndroidUtilities.dp(6f) else 0
                    result.add(RichMessageBodyBlock(segment.blocks, context, trailingGapPx = trailingGap))
                    index += segment.blocks.size
                }
                is PostBodySegment.OwnBlock -> {
                    // The same path the flag-off body uses, keyed by the block's index in the post so
                    // its state (a poll's selection, a callout's collapse) survives a rebind.
                    result.addAll(
                        PostBodyBlockFactory.build(
                            blocks = listOf(segment.block),
                            context = context,
                            state = state,
                            stateKeyPrefix = "rich.$index",
                        ),
                    )
                    index++
                }
            }
        }
        return result
    }
}

/**
 * A run of the post body drawn by upstream's renderer: one `TL_iv.RichMessage` built from the run's
 * cooked blocks, laid out by `RichMessageLayout` and drawn into the cell's canvas.
 *
 * The cell keeps doing what it does for every other block - it paints its own background, insets the
 * column and forwards touch - because `RichMessageLayout` draws no message bubble (it draws only
 * quote/pullquote backgrounds). The message is built as an incoming one (no `out` flag), which is
 * what gives a post body the body colours rather than the outgoing variants.
 */
internal class RichMessageBodyBlock(
    private val cooked: List<CookedPostBlock>,
    private val context: PostBodyBuildContext,
    /** Kept under the run when another block follows it; see `RichMessageBody.blocks`. */
    private val trailingGapPx: Int = 0,
) : PostBodyBlock {

    private var messageObject: MessageObject? = null
    private var layout: RichMessageLayout? = null
    private var layoutWidth = -1
    private var attached = false

    /** Where the cell draws this block, learned from the canvas matrix: the viewer needs it. */
    private var originX = 0
    private var originY = 0

    private val delegate = RichMessageBodyDelegate()

    override fun measure(maxWidthPx: Int): Int = (layoutFor(maxWidthPx)?.height ?: 0) + trailingGapPx

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

    /**
     * The cell hands a block its own local coordinates; `RichMessageLayout` draws from the block's
     * origin, so the event has to be moved into that space before it sees it - forwarding the raw
     * cell event made every press land a block-offset lower, which is how a press on a text line
     * opened the image viewer.
     */
    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val rich = layout ?: return false
        val localEvent = MotionEvent.obtain(event)
        localEvent.offsetLocation(localX - event.x, localY - event.y)
        val handled = rich.onTouchEvent(localEvent)
        localEvent.recycle()
        return handled
    }

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
            messageOwner.rich_message = PostRichMessageBuilder.build(cooked, context.baseUrl)
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

    /**
     * A link press inside the run. Upstream answers this on the cell (`didPressUrl`), and this host is
     * a plain view, so the renderer's own cell-less branch hands it here instead - the app then opens
     * it through the same entry the block path's links use. The chip travels as a link run, so this is
     * its tap too.
     */
    override fun didPressUrl(cell: ChatMessageCell?, url: CharacterStyle?, longPress: Boolean) {
        val href = (url as? URLSpan)?.url ?: return
        if (href.isBlank()) return
        if (longPress) {
            // The block path's long press copies the URL (`didLongPressLink`); the run does the same, so
            // a held link does not silently do nothing here.
            context.delegate.onLinkLongPress(href)
            return
        }
        // The same routing the block path's spans take (`applyLinkSpan`): a topic opens in this app, a
        // mention on the profile, everything else (a hashtag, an external link) through the link entry.
        when (val destination = parseInternalLinkDestination(baseUrl = context.baseUrl, rawUrl = href)) {
            is PostBodyInternalDestination.Topic -> context.delegate.onTopicClick(destination.topicId)
            is PostBodyInternalDestination.User -> context.delegate.onUserClick(destination.username)
            else -> context.delegate.onLinkClick(href)
        }
    }

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
        override fun getLayout(): Layout = inner.layout
        override fun getX(): Int = inner.x + dx
        override fun getY(): Int = inner.y + dy
        override fun getRow(): Int = inner.row
        override fun getPrefix(): CharSequence? = inner.prefix
        override fun getSelectionBounds(): Rect? = inner.selectionBounds
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
