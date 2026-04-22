/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.BackDrawable.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BackDrawable.java
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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import io.github.baba983.nativedo.core.util.AndroidUtilities

class BackDrawable(
    private val alwaysClose: Boolean,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = AndroidUtilities.dpf2(2f)
    }
    private val interpolator = DecelerateInterpolator()

    private var reverseAngle: Boolean = false
    private var lastFrameTime: Long = 0L
    private var finalRotation: Float = 0f
    private var currentRotation: Float = 0f
    private var currentAnimationTime: Int = 0
    private var color: Int = Color.WHITE
    private var rotatedColor: Int = 0xFF757575.toInt()
    private var animationTime: Float = 300f
    private var arrowRotation: Int = 0

    fun getRotation(): Float = finalRotation

    fun setColor(value: Int) {
        color = value
        invalidateSelf()
    }

    fun setRotatedColor(value: Int) {
        rotatedColor = value
        invalidateSelf()
    }

    fun setArrowRotation(angle: Int) {
        arrowRotation = angle
        invalidateSelf()
    }

    fun setRotation(
        rotation: Float,
        animated: Boolean,
    ) {
        lastFrameTime = 0L
        reverseAngle = when (currentRotation) {
            1f -> true
            0f -> false
            else -> reverseAngle
        }
        if (animated) {
            currentAnimationTime = if (currentRotation < rotation) {
                (currentRotation * animationTime).toInt()
            } else {
                ((1f - currentRotation) * animationTime).toInt()
            }
            lastFrameTime = System.currentTimeMillis()
            finalRotation = rotation
        } else {
            finalRotation = rotation
            currentRotation = rotation
        }
        invalidateSelf()
    }

    fun setAnimationTime(value: Float) {
        animationTime = value
    }

    override fun draw(canvas: Canvas) {
        if (currentRotation != finalRotation) {
            if (lastFrameTime != 0L) {
                val dt = System.currentTimeMillis() - lastFrameTime
                currentAnimationTime += dt.toInt()
                currentRotation = if (currentAnimationTime >= animationTime) {
                    finalRotation
                } else if (currentRotation < finalRotation) {
                    interpolator.getInterpolation(currentAnimationTime / animationTime) * finalRotation
                } else {
                    1f - interpolator.getInterpolation(currentAnimationTime / animationTime)
                }
            }
            lastFrameTime = System.currentTimeMillis()
            invalidateSelf()
        }

        paint.strokeWidth = AndroidUtilities.dpf2(2f)
        paint.color = ColorUtils.blendARGB(color, rotatedColor, currentRotation)

        canvas.save()
        canvas.translate(intrinsicWidth / 2f, intrinsicHeight / 2f)
        if (arrowRotation != 0) {
            canvas.rotate(arrowRotation.toFloat())
        }
        var rotation = currentRotation
        if (!alwaysClose) {
            canvas.rotate(currentRotation * if (reverseAngle) -225f else 135f)
        } else {
            canvas.rotate(135f + currentRotation * if (reverseAngle) -180f else 180f)
            rotation = 1f
        }
        canvas.drawLine(
            AndroidUtilities.dp(AndroidUtilities.lerp(-6.75f, -8f, rotation)).toFloat(),
            0f,
            AndroidUtilities.dp(8).toFloat() - (paint.strokeWidth / 2f) * (1f - rotation),
            0f,
            paint,
        )
        val startYDiff = AndroidUtilities.dp(-0.25f).toFloat()
        val endYDiff = AndroidUtilities.dp(AndroidUtilities.lerp(7f, 8f, rotation)).toFloat() -
            (paint.strokeWidth / 4f) * (1f - rotation)
        val startXDiff = AndroidUtilities.dp(AndroidUtilities.lerp(-7.25f, 0f, rotation)).toFloat()
        canvas.drawLine(startXDiff, -startYDiff, 0f, -endYDiff, paint)
        canvas.drawLine(startXDiff, startYDiff, 0f, endYDiff, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(24)

    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(24)
}
