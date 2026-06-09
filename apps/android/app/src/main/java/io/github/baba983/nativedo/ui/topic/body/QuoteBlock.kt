/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockBlockquoteCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList

internal class QuoteBlock(
    private val block: CookedPostBlock.Quote,
    private val context: PostBodyBuildContext,
    private val state: PostBodyBlockState? = null,
    private val stateIndex: Int = -1,
) : PostBodyBlock {
    private class QuoteTextBlock(
        html: String,
        context: PostBodyBuildContext,
    ) : TextLayoutBlock(
        html = html,
        context = context,
        paint = context.quotePaint,
    )

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.accentColor }
    private val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = context.secondaryTextColor
        textSize = AndroidUtilities.dp(13f).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private var bodyBlock = QuoteTextBlock(block.html, context)
    private var titleLayout: StaticLayout? = null
    private var bodyTop = 0
    private var bodyWidth = 0
    private var textLeft = 0
    private var blockWidth = 0
    private var measuredHeight = 0
    private var expanded = false
    private var loading = false
    private var expandedHtml: String? = null

    init {
        state?.expandedQuotesByIndex?.get(stateIndex)?.let { html ->
            expandedHtml = html
            bodyBlock = QuoteTextBlock(html, context)
            expanded = true
        }
    }

    override fun measure(maxWidthPx: Int): Int {
        blockWidth = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        val textY = AndroidUtilities.dp(8f)
        textLeft = AndroidUtilities.dp(18f + 14f)
        bodyWidth = (blockWidth - textLeft - AndroidUtilities.dp(18f)).coerceAtLeast(AndroidUtilities.dp(80f))
        titleLayout = block.title?.takeIf { it.isNotBlank() }?.let { title ->
            StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, bodyWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
        }
        bodyTop = textY + (titleLayout?.let { it.height + AndroidUtilities.dp(6f) } ?: 0)
        val bodyHeight = bodyBlock.measure(bodyWidth)
        measuredHeight = bodyTop + bodyHeight + AndroidUtilities.dp(8f)
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val barLeft = AndroidUtilities.dp(18f).toFloat()
        val barWidth = AndroidUtilities.dp(2f)
        canvas.drawRect(
            barLeft,
            AndroidUtilities.dp(6f).toFloat(),
            barLeft + barWidth,
            (measuredHeight - AndroidUtilities.dp(6f)).toFloat(),
            barPaint,
        )
        titleLayout?.let { layout ->
            canvas.save()
            canvas.translate(textLeft.toFloat(), AndroidUtilities.dp(8f).toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.save()
        canvas.translate(textLeft.toFloat(), bodyTop.toFloat())
        bodyBlock.draw(canvas, bodyWidth)
        canvas.restore()
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (localX in 0f..blockWidth.toFloat() && localY in 0f..measuredHeight.toFloat() && canExpand() && !expanded) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                expand()
            }
            return event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP
        }
        if (localX < textLeft || localY < bodyTop || localY > measuredHeight) return false
        return bodyBlock.onTouch(event, localX - textLeft, localY - bodyTop)
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        titleLayout?.let { layout ->
            blocks.add(PostSelectionLayoutBlock(layout, x + textLeft, y + AndroidUtilities.dp(8f)))
        }
        bodyBlock.fillTextLayoutBlocks(blocks, x + textLeft, y + bodyTop)
    }

    override fun onAttachedToWindow() {
        bodyBlock.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        bodyBlock.onDetachedFromWindow()
    }

    override fun applyTheme() {
        bodyBlock.applyTheme()
    }

    private fun canExpand(): Boolean {
        return block.sourceTopicId != null && block.sourcePostNumber != null
    }

    private fun expand() {
        val topicId = block.sourceTopicId ?: return
        val postNumber = block.sourcePostNumber ?: return
        if (loading) return
        expandedHtml?.let { html ->
            setExpandedHtml(html)
            return
        }
        loading = true
        context.delegate.onQuoteExpand(topicId, postNumber) { html ->
            loading = false
            if (html.isNullOrBlank()) return@onQuoteExpand
            expandedHtml = html
            state?.expandedQuotesByIndex?.put(stateIndex, html)
            setExpandedHtml(html)
        }
    }

    private fun setExpandedHtml(html: String) {
        if (expanded) return
        bodyBlock.onDetachedFromWindow()
        bodyBlock = QuoteTextBlock(html, context)
        expanded = true
        if (context.host.isAttachedToWindow) {
            bodyBlock.onAttachedToWindow()
        }
        context.host.requestLayout()
        context.host.invalidate()
    }
}
