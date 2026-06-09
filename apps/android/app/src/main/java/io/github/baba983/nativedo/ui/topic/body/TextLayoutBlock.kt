/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer text block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.LinkSpanDrawable
import java.util.ArrayList
import kotlin.math.abs

internal abstract class TextLayoutBlock(
    private val html: String,
    private val context: PostBodyBuildContext,
    private val paint: TextPaint,
) : PostBodyBlock {
    private val textBuilder = PostBodyTextBuilder(context)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context.host.context).scaledTouchSlop
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, Color.red(context.accentColor), Color.green(context.accentColor), Color.blue(context.accentColor))
        style = Paint.Style.FILL
    }
    private var builtText: BuiltText? = null
    private var layout: StaticLayout? = null
    private var measuredWidth = 0
    private var measuredHeight = 0
    private var handle: BuiltText.Handle? = null
    private var pressedLink: LinkRegion? = null
    private var pressedLinkDrawable: LinkSpanDrawable<ClickableSpan>? = null
    private var releasedLinkDrawable: LinkSpanDrawable<ClickableSpan>? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false

    override fun measure(maxWidthPx: Int): Int {
        val width = maxWidthPx.coerceAtLeast(1)
        if (layout != null && measuredWidth == width) {
            return measuredHeight
        }
        val text = textBuilder.build(html = html, textSizePx = paint.textSize)
        builtText = text
        if (context.host.isAttachedToWindow) {
            handle?.detach()
            handle = text.attach(context.host)
        }
        layout = StaticLayout.Builder.obtain(text.spannable, 0, text.spannable.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
        measuredWidth = width
        measuredHeight = (layout?.height ?: 0) + verticalPaddingPx()
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val currentLayout = layout ?: return
        canvas.save()
        canvas.translate(0f, topPaddingPx().toFloat())
        releasedLinkDrawable?.let { drawable ->
            if (!drawable.draw(canvas)) {
                drawable.reset()
                releasedLinkDrawable = null
            } else {
                context.host.invalidate()
            }
        }
        pressedLinkDrawable?.let { drawable ->
            if (drawable.draw(canvas)) {
                context.host.invalidate()
            }
        }
        currentLayout.draw(canvas)
        canvas.restore()
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val currentLayout = layout ?: return false
        val yInText = localY - topPaddingPx()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val link = findLinkAt(currentLayout, localX, yInText) ?: return false
                pressedLink = link
                downX = localX
                downY = localY
                longPressFired = false
                pressedLinkDrawable?.reset()
                pressedLinkDrawable = LinkSpanDrawable<ClickableSpan>(null, null, localX, yInText, true).also { drawable ->
                    val path = drawable.obtainNewPath()
                    path.setCurrentLayout(currentLayout, link.start, 0f)
                    currentLayout.getSelectionPath(link.start, link.end, path)
                }
                longPressHandler.postDelayed({
                    val current = pressedLink ?: return@postDelayed
                    longPressFired = true
                    context.delegate.onLinkLongPress(current.url)
                    context.host.invalidate()
                }, ViewConfiguration.getLongPressTimeout().toLong())
                context.host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                pressedLink ?: return false
                if (abs(localX - downX) > touchSlop || abs(localY - downY) > touchSlop) {
                    clearPressedLink(release = false)
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedLink ?: return false
                clearPressedLink(release = true)
                if (!longPressFired) {
                    pressed.onClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressedLink != null) {
                    clearPressedLink(release = false)
                    return true
                }
            }
        }
        return false
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        val currentLayout = layout ?: return
        blocks.add(SelectionTextLayoutBlock(currentLayout, x, y + topPaddingPx()))
    }

    override fun onAttachedToWindow() {
        if (handle != null) return
        handle = builtText?.attach(context.host)
    }

    override fun onDetachedFromWindow() {
        longPressHandler.removeCallbacksAndMessages(null)
        handle?.detach()
        handle = null
        pressedLink = null
        pressedLinkDrawable?.reset()
        pressedLinkDrawable = null
        releasedLinkDrawable?.reset()
        releasedLinkDrawable = null
    }

    protected open fun topPaddingPx(): Int = 0
    protected open fun bottomPaddingPx(): Int = 0
    private fun verticalPaddingPx(): Int = topPaddingPx() + bottomPaddingPx()

    protected fun dp(value: Float): Int = AndroidUtilities.dp(value)

    private fun findLinkAt(layout: StaticLayout, x: Float, y: Float): LinkRegion? {
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x)
        return builtText?.links?.firstOrNull { link -> offset in link.start until link.end }
    }

    private fun clearPressedLink(release: Boolean) {
        longPressHandler.removeCallbacksAndMessages(null)
        pressedLink = null
        val drawable = pressedLinkDrawable
        pressedLinkDrawable = null
        if (release && drawable != null) {
            drawable.release()
            releasedLinkDrawable?.reset()
            releasedLinkDrawable = drawable
        } else {
            drawable?.reset()
        }
        context.host.invalidate()
    }

    private class SelectionTextLayoutBlock(
        private val layout: StaticLayout,
        private val x: Int,
        private val y: Int,
    ) : TextSelectionHelper.TextLayoutBlock {
        override fun getLayout(): StaticLayout = layout
        override fun getX(): Int = x
        override fun getY(): Int = y
        override fun getRow(): Int = 0
        override fun getPrefix(): CharSequence? = null
    }
}
