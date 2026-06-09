/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer list item block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList

internal class ListBlock(
    private val block: CookedPostBlock.ListItems,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private class ItemTextBlock(
        html: String,
        context: PostBodyBuildContext,
    ) : TextLayoutBlock(
        html = html,
        context = context,
        paint = context.paragraphPaint,
    )

    private val itemSpacing = AndroidUtilities.dp(6f)
    private val firstItemTopPadding = AndroidUtilities.dp(10f)
    private val markerTextGap = AndroidUtilities.dp(6f)
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.secondaryTextColor
        textAlign = Paint.Align.CENTER
        textSize = AndroidUtilities.dp(19f).toFloat()
    }
    private val numberPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = context.secondaryTextColor
        textAlign = Paint.Align.RIGHT
        textSize = AndroidUtilities.dp(16f).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val itemBlocks = block.items.map { html -> ItemTextBlock(html, context) }
    private var itemTops = IntArray(itemBlocks.size)
    private var itemHeights = IntArray(itemBlocks.size)
    private var measuredHeight = 0
    private var textWidth = 0
    private var markerColumnWidth = AndroidUtilities.dp(32f)

    override fun measure(maxWidthPx: Int): Int {
        markerColumnWidth = if (block.ordered) {
            val maxNumberWidth = block.items.indices.maxOfOrNull { index ->
                numberPaint.measureText("${index + 1}.").toInt()
            } ?: 0
            (maxNumberWidth + markerTextGap + AndroidUtilities.dp(18f)).coerceAtLeast(AndroidUtilities.dp(32f))
        } else {
            AndroidUtilities.dp(30f)
        }
        textWidth = (maxWidthPx - markerColumnWidth).coerceAtLeast(AndroidUtilities.dp(80f))
        var y = if (itemBlocks.isEmpty()) 0 else firstItemTopPadding
        itemBlocks.forEachIndexed { index, item ->
            if (index > 0) y += itemSpacing
            itemTops[index] = y
            val height = item.measure(textWidth)
            itemHeights[index] = height
            y += height
        }
        measuredHeight = y
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        itemBlocks.forEachIndexed { index, item ->
            val top = itemTops[index]
            val baseline = top - context.paragraphPaint.fontMetrics.ascent
            if (block.ordered) {
                canvas.drawText(
                    "${index + 1}.",
                    (markerColumnWidth - markerTextGap).toFloat(),
                    baseline,
                    numberPaint,
                )
            } else {
                canvas.drawText(
                    "•",
                    (markerColumnWidth / 2f),
                    baseline,
                    bulletPaint,
                )
            }
            canvas.save()
            canvas.translate(markerColumnWidth.toFloat(), top.toFloat())
            item.draw(canvas, textWidth)
            canvas.restore()
        }
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val itemX = localX - markerColumnWidth
        if (itemX < 0f) return false
        itemBlocks.forEachIndexed { index, item ->
            val top = itemTops[index]
            val bottom = top + itemHeights[index]
            if (localY >= top && localY <= bottom) {
                return item.onTouch(event, itemX, localY - top)
            }
        }
        return false
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        itemBlocks.forEachIndexed { index, item ->
            val before = blocks.size
            item.fillTextLayoutBlocks(blocks, x + markerColumnWidth, y + itemTops[index])
            val prefix = if (block.ordered) "${index + 1}." else "•"
            for (i in before until blocks.size) {
                val current = blocks[i]
                blocks[i] = PostSelectionLayoutBlock(
                    layout = current.layout,
                    x = current.x,
                    y = current.y,
                    row = current.row,
                    prefix = prefix,
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        itemBlocks.forEach(PostBodyBlock::onAttachedToWindow)
    }

    override fun onDetachedFromWindow() {
        itemBlocks.forEach(PostBodyBlock::onDetachedFromWindow)
    }

    override fun applyTheme() {
        itemBlocks.forEach(PostBodyBlock::applyTheme)
    }
}
