/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockTableCell and
 * TableLayout geometry patterns.
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
import android.view.MotionEvent
import android.view.ViewConfiguration
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

internal class TableBlock(
    private val block: CookedPostBlock.Table,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private data class CellLayout(
        val text: String,
        val layout: StaticLayout,
        val row: Int,
        val column: Int,
        val isHeader: Boolean,
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0F000000 }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.dividerColor
        strokeWidth = AndroidUtilities.dp(1f).toFloat()
    }
    private val headerPaint = TextPaint(context.quotePaint).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val bodyPaint = TextPaint(context.quotePaint)
    private val clipRect = RectF()
    private var cellLayouts: List<CellLayout> = emptyList()
    private var rowHeights = IntArray(0)
    private var columnWidths = IntArray(0)
    private var rowTops = IntArray(0)
    private var columnLefts = IntArray(0)
    private var contentWidth = 0
    private var viewportWidth = 0
    private var measuredHeight = 0
    private var scrollX = 0f
    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context.host.context).scaledTouchSlop

    override fun measure(maxWidthPx: Int): Int {
        viewportWidth = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        val rows = tableRows()
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        if (columnCount == 0) {
            measuredHeight = 0
            clipRect.setEmpty()
            return measuredHeight
        }
        val horizontalPadding = AndroidUtilities.dp(12f)
        val verticalPadding = AndroidUtilities.dp(8f)
        val minColumnWidth = AndroidUtilities.dp(72f)
        val maxColumnWidth = AndroidUtilities.dp(240f)
        val desiredColumnWidths = IntArray(columnCount) { minColumnWidth }
        val minimumColumnWidths = IntArray(columnCount) { minColumnWidth }
        rows.forEach { row ->
            row.forEachIndexed { column, text ->
                val desired = bodyPaint.measureText(text).roundToInt() + horizontalPadding * 2
                val longestWord = text.split(Regex("\\s+")).maxOfOrNull { bodyPaint.measureText(it).roundToInt() } ?: 0
                desiredColumnWidths[column] = maxOf(desiredColumnWidths[column], desired.coerceIn(minColumnWidth, maxColumnWidth))
                minimumColumnWidths[column] = maxOf(minimumColumnWidths[column], (longestWord + horizontalPadding * 2).coerceIn(minColumnWidth, maxColumnWidth))
            }
        }
        columnWidths = distributeColumnWidths(minimumColumnWidths, desiredColumnWidths, viewportWidth)
        columnLefts = IntArray(columnCount)
        var x = 0
        columnWidths.forEachIndexed { index, width ->
            columnLefts[index] = x
            x += width
        }
        contentWidth = x.coerceAtLeast(viewportWidth)
        rowHeights = IntArray(rows.size)
        val layouts = mutableListOf<CellLayout>()
        rows.forEachIndexed { rowIndex, row ->
            var maxRowHeight = AndroidUtilities.dp(36f)
            row.forEachIndexed { column, text ->
                val isHeader = block.headers.isNotEmpty() && rowIndex == 0
                val paint = if (isHeader) headerPaint else bodyPaint
                val textWidth = (columnWidths[column] - horizontalPadding * 2).coerceAtLeast(AndroidUtilities.dp(40f))
                val source = text.ifBlank { " " }
                val layout = StaticLayout.Builder.obtain(source, 0, source.length, paint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(false)
                    .build()
                maxRowHeight = maxOf(maxRowHeight, layout.height + verticalPadding * 2)
                layouts += CellLayout(text = text, layout = layout, row = rowIndex, column = column, isHeader = isHeader)
            }
            rowHeights[rowIndex] = maxRowHeight
        }
        cellLayouts = layouts
        rowTops = IntArray(rows.size)
        var y = 0
        rowHeights.forEachIndexed { index, height ->
            rowTops[index] = y
            y += height
        }
        measuredHeight = y
        clipRect.set(0f, 0f, viewportWidth.toFloat(), measuredHeight.toFloat())
        scrollX = scrollX.coerceIn(0f, maxScroll().toFloat())
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        if (measuredHeight <= 0) return
        val radius = AndroidUtilities.dp(10f).toFloat()
        canvas.drawRoundRect(clipRect, radius, radius, backgroundPaint)
        val saveCount = canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(-scrollX, 0f)
        drawGrid(canvas)
        val horizontalPadding = AndroidUtilities.dp(12f)
        val verticalPadding = AndroidUtilities.dp(8f)
        cellLayouts.forEach { cell ->
            canvas.save()
            canvas.translate(
                (columnLefts[cell.column] + horizontalPadding).toFloat(),
                (rowTops[cell.row] + verticalPadding).toFloat(),
            )
            cell.layout.draw(canvas)
            canvas.restore()
        }
        canvas.restoreToCount(saveCount)
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (!clipRect.contains(localX, localY) || maxScroll() <= 0) {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
                dragging = false
                setParentInterceptBlocked(false)
            }
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = localX
                downY = localY
                lastX = localX
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = localX - downX
                val dy = localY - downY
                val movedX = abs(dx)
                val movedY = abs(dy)
                if (!dragging && (movedX > touchSlop || movedY > touchSlop)) {
                    if (movedX > movedY) {
                        dragging = true
                        setParentInterceptBlocked(true)
                    } else {
                        setParentInterceptBlocked(false)
                        return false
                    }
                }
                if (dragging) {
                    val delta = lastX - localX
                    val nextScrollX = (scrollX + delta).coerceIn(0f, maxScroll().toFloat())
                    lastX = localX
                    if (nextScrollX != scrollX) {
                        scrollX = nextScrollX
                        context.selectionGeometryInvalidator()
                    } else {
                        context.host.invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                dragging = false
                setParentInterceptBlocked(false)
                return wasDragging
            }
        }
        return true
    }

    private fun drawGrid(canvas: Canvas) {
        var x = 0f
        columnWidths.forEach { width ->
            x += width
            canvas.drawLine(x, 0f, x, measuredHeight.toFloat(), dividerPaint)
        }
        var y = 0f
        rowHeights.forEach { height ->
            y += height
            canvas.drawLine(0f, y, contentWidth.toFloat(), y, dividerPaint)
        }
    }

    private fun tableRows(): List<List<String>> {
        return buildList {
            if (block.headers.isNotEmpty()) add(block.headers)
            addAll(block.rows)
        }
    }

    private fun maxScroll(): Int = (contentWidth - viewportWidth).coerceAtLeast(0)

    private fun setParentInterceptBlocked(blocked: Boolean) {
        context.host.parent?.requestDisallowInterceptTouchEvent(blocked)
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        val horizontalPadding = AndroidUtilities.dp(12f)
        val verticalPadding = AndroidUtilities.dp(8f)
        cellLayouts.forEach { cell ->
            blocks.add(TableSelectionBlock(
                layout = cell.layout,
                x = x + columnLefts[cell.column] + horizontalPadding - scrollX.roundToInt(),
                y = y + rowTops[cell.row] + verticalPadding,
                row = cell.row + 1,
            ))
        }
    }

    private fun distributeColumnWidths(minimumWidths: IntArray, desiredWidths: IntArray, targetWidth: Int): IntArray {
        val widths = desiredWidths.copyOf()
        val desiredTotal = widths.sum()
        if (desiredTotal >= targetWidth) {
            return widths
        }
        val minimumTotal = minimumWidths.sum()
        if (minimumTotal >= targetWidth) {
            return minimumWidths
        }
        var remaining = targetWidth - desiredTotal
        var index = 0
        while (remaining > 0 && widths.isNotEmpty()) {
            widths[index % widths.size] += 1
            index++
            remaining--
        }
        return widths
    }

    private class TableSelectionBlock(
        private val layout: StaticLayout,
        private val x: Int,
        private val y: Int,
        private val row: Int,
    ) : TextSelectionHelper.TextLayoutBlock {
        override fun getLayout(): StaticLayout = layout
        override fun getX(): Int = x
        override fun getY(): Int = y
        override fun getRow(): Int = row
        override fun getPrefix(): CharSequence? = null
    }
}
