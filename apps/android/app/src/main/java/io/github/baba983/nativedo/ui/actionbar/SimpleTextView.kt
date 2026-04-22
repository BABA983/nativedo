/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.SimpleTextView.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/SimpleTextView.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 as published by
 * Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */
package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import kotlin.math.ceil

class SimpleTextView(
    context: Context,
) : View(context) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var sourceText: CharSequence = ""
    private var displayText: CharSequence = ""

    var gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }

    var text: CharSequence
        get() = sourceText
        set(value) {
            val newValue = value
            if (TextUtils.equals(sourceText, newValue)) {
                return
            }
            sourceText = newValue
            requestLayout()
            invalidate()
        }

    fun setTextColor(color: Int) {
        if (textPaint.color == color) {
            return
        }
        textPaint.color = color
        invalidate()
    }

    fun setTextSizeSp(sizeSp: Float) {
        val textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            resources.displayMetrics,
        )
        if (textPaint.textSize == textSize) {
            return
        }
        textPaint.textSize = textSize
        requestLayout()
        invalidate()
    }

    fun setTypeface(typeface: Typeface?) {
        if (textPaint.typeface == typeface) {
            return
        }
        textPaint.typeface = typeface
        requestLayout()
        invalidate()
    }

    fun getTextHeight(): Int {
        val metrics = textPaint.fontMetricsInt
        return metrics.descent - metrics.ascent
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val maxWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> Int.MAX_VALUE
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val availableTextWidth = (maxWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        displayText = if (maxWidth == Int.MAX_VALUE) {
            sourceText
        } else {
            TextUtils.ellipsize(
                sourceText,
                textPaint,
                availableTextWidth.toFloat(),
                TextUtils.TruncateAt.END,
            )
        }
        val desiredTextWidth = if (displayText.isEmpty()) {
            0
        } else {
            ceil(textPaint.measureText(displayText, 0, displayText.length).toDouble()).toInt()
        }
        val desiredWidth = desiredTextWidth + paddingLeft + paddingRight
        val desiredHeight = getTextHeight() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (displayText.isEmpty()) {
            return
        }
        val textWidth = textPaint.measureText(displayText, 0, displayText.length)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        val x = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> paddingLeft + (contentWidth - textWidth) / 2f
            Gravity.END,
            Gravity.RIGHT,
            -> width - paddingRight - textWidth
            else -> paddingLeft.toFloat()
        }
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.bottom - metrics.top
        val y = paddingTop + (contentHeight - textHeight) / 2f - metrics.top
        canvas.drawText(displayText, 0, displayText.length, x, y, textPaint)
    }
}
