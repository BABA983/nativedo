package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Rect
import android.text.StaticLayout
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.ui.PostRichMessageBuilder
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.TextSelectionHelper
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

    override fun measure(maxWidthPx: Int): Int = layoutFor(maxWidthPx)?.height ?: 0

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val rich = layout ?: return
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
        if (attached) {
            rich.attach(context.host)
        }
        layout = rich
        layoutWidth = maxWidthPx
        return rich
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
