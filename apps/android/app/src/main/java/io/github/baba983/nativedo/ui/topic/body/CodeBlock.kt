/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockPreformattedCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.telegram.messenger.AndroidUtilities
import kotlin.math.abs
import kotlin.math.roundToInt

internal class CodeBlock(
    private val code: String,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0F000000
    }
    private val clipRect = RectF()
    private var layout: StaticLayout? = null
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
        val horizontalPadding = AndroidUtilities.dp(12f)
        val source = code.ifBlank { " " }
        val longestLineWidth = source.lineSequence()
            .map { line -> context.monoPaint.measureText(line).roundToInt() }
            .maxOrNull()
            ?: viewportWidth
        contentWidth = (longestLineWidth + horizontalPadding * 2).coerceAtLeast(viewportWidth)
        layout = StaticLayout.Builder.obtain(source, 0, source.length, context.monoPaint, contentWidth - horizontalPadding * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
        measuredHeight = (layout?.height ?: 0) + AndroidUtilities.dp(20f)
        clipRect.set(0f, 0f, viewportWidth.toFloat(), measuredHeight.toFloat())
        scrollX = scrollX.coerceIn(0f, maxScroll().toFloat())
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val currentLayout = layout ?: return
        val radius = AndroidUtilities.dp(10f).toFloat()
        canvas.drawRoundRect(clipRect, radius, radius, backgroundPaint)
        val saveCount = canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(AndroidUtilities.dp(12f) - scrollX, AndroidUtilities.dp(10f).toFloat())
        currentLayout.draw(canvas)
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
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    setParentInterceptBlocked(true)
                }
                if (dragging) {
                    val delta = lastX - localX
                    scrollX = (scrollX + delta).coerceIn(0f, maxScroll().toFloat())
                    lastX = localX
                    context.host.invalidate()
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

    private fun maxScroll(): Int = (contentWidth - viewportWidth).coerceAtLeast(0)

    private fun setParentInterceptBlocked(blocked: Boolean) {
        context.host.parent?.requestDisallowInterceptTouchEvent(blocked)
    }
}
