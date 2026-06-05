/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockDividerCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import org.telegram.messenger.AndroidUtilities

internal class RuleBlock(
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private val rect = android.graphics.RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.dividerColor
    }
    private var measuredHeight = AndroidUtilities.dp(18f)

    override fun measure(maxWidthPx: Int): Int = measuredHeight

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val width = parentWidth / 3
        rect.set(width.toFloat(), AndroidUtilities.dp(8f).toFloat(), (width * 2).toFloat(), AndroidUtilities.dp(10f).toFloat())
        canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), paint)
    }
}
