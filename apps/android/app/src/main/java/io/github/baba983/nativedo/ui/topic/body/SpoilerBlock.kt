/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's SpoilersTextView / SpoilerEffect
 * reveal model.
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
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.jsoup.Jsoup
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CubicBezierInterpolator
import java.util.ArrayList

internal class SpoilerBlock(
    private val block: CookedPostBlock.Spoiler,
    private val context: PostBodyBuildContext,
    private val state: PostBodyBlockState? = null,
    private val stateIndex: Int = -1,
) : PostBodyBlock {
    private class SpoilerTextBlock(
        html: String,
        context: PostBodyBuildContext,
    ) : TextLayoutBlock(
        html = html,
        context = context,
        paint = context.paragraphPaint,
    )

    private val spoilerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.chipBackgroundColor }
    private val textPaint = TextPaint(context.paragraphPaint)
    private val rect = RectF()
    private val bodyBlock = SpoilerTextBlock(block.html, context)
    private var concealedLayout: StaticLayout? = null
    private var bodyWidth = 0
    private var measuredHeight = 0
    var revealed: Boolean = false
        private set
    private val revealProgress = AnimatedFloat(if (state?.revealedSpoilers?.contains(stateIndex) == true) 1f else 0f, context.host, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT)

    init {
        revealed = state?.revealedSpoilers?.contains(stateIndex) == true
    }

    override fun measure(maxWidthPx: Int): Int {
        val width = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        bodyWidth = width
        concealedLayout = if (revealed) {
            null
        } else {
            val text = Jsoup.parseBodyFragment(block.html).body().text().ifBlank { "••••••" }
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, bodyWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        }
        val bodyHeight = if (revealed) bodyBlock.measure(bodyWidth) else concealedLayout?.height ?: 0
        measuredHeight = if (revealed) {
            bodyHeight
        } else {
            bodyHeight
        }.coerceAtLeast(AndroidUtilities.dp(24f))
        rect.set(0f, 0f, width.toFloat(), measuredHeight.toFloat())
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val progress = revealProgress.set(if (revealed) 1f else 0f)
        if (revealed) {
            if (progress < 1f) {
                canvas.saveLayerAlpha(0f, 0f, bodyWidth.toFloat(), measuredHeight.toFloat(), (255 * progress).toInt())
            }
            bodyBlock.draw(canvas, bodyWidth)
            if (progress < 1f) {
                canvas.restore()
            }
        } else {
            concealedLayout?.let { layout ->
                val radius = AndroidUtilities.dp(4f).toFloat()
                val oldAlpha = spoilerPaint.alpha
                spoilerPaint.alpha = (oldAlpha * (1f - progress)).toInt()
                for (line in 0 until layout.lineCount) {
                    val top = layout.getLineTop(line).toFloat()
                    val bottom = layout.getLineBottom(line).toFloat()
                    val left = layout.getLineLeft(line).coerceAtLeast(0f)
                    val right = layout.getLineRight(line).coerceAtMost(bodyWidth.toFloat())
                    if (right > left) {
                        rect.set(left, top + AndroidUtilities.dp(1f), right, bottom - AndroidUtilities.dp(1f))
                        canvas.drawRoundRect(rect, radius, radius, spoilerPaint)
                    }
                }
                spoilerPaint.alpha = oldAlpha
            }
        }
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (!rect.contains(localX, localY)) return false
        if (!revealed) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                revealed = true
                state?.revealedSpoilers?.add(stateIndex)
                measuredHeight = 0
                context.host.requestLayout()
                context.host.invalidate()
            }
            return event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP
        }
        return bodyBlock.onTouch(event, localX, localY)
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) {
        if (revealed) {
            bodyBlock.fillTextLayoutBlocks(blocks, x, y)
        }
    }

    override fun onAttachedToWindow() {
        if (revealed) {
            bodyBlock.onAttachedToWindow()
        }
    }

    override fun onDetachedFromWindow() {
        bodyBlock.onDetachedFromWindow()
    }

    override fun applyTheme() {
        bodyBlock.applyTheme()
    }
}
