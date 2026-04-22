/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.Components.LayoutHelper.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/LayoutHelper.java
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
package io.github.baba983.nativedo.core.util

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

object LayoutHelper {
    const val MATCH_PARENT: Int = -1
    const val WRAP_CONTENT: Int = -2

    fun createFrame(
        width: Int,
        height: Int,
        gravity: Int = Gravity.TOP or Gravity.START,
        leftMargin: Float = 0f,
        topMargin: Float = 0f,
        rightMargin: Float = 0f,
        bottomMargin: Float = 0f,
    ): FrameLayout.LayoutParams {
        return createFrame(width, height.toFloat(), gravity, leftMargin, topMargin, rightMargin, bottomMargin)
    }

    fun createFrame(
        width: Int,
        height: Float,
        gravity: Int = Gravity.TOP or Gravity.START,
        leftMargin: Float = 0f,
        topMargin: Float = 0f,
        rightMargin: Float = 0f,
        bottomMargin: Float = 0f,
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height)).apply {
            this.gravity = gravity
            setMargins(
                AndroidUtilities.dp(leftMargin),
                AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin),
                AndroidUtilities.dp(bottomMargin),
            )
        }
    }

    fun createFrame(
        width: Int,
        height: Int,
        gravity: Int,
        leftMargin: Int,
        topMargin: Int,
        rightMargin: Int,
        bottomMargin: Int,
    ): FrameLayout.LayoutParams {
        return createFrame(width, height.toFloat(), gravity, leftMargin, topMargin, rightMargin, bottomMargin)
    }

    fun createFrame(
        width: Int,
        height: Float,
        gravity: Int,
        leftMargin: Int,
        topMargin: Int,
        rightMargin: Int,
        bottomMargin: Int,
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height)).apply {
            this.gravity = gravity
            setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
        }
    }

    fun createLinear(
        width: Int,
        height: Int,
        weight: Float = 0f,
        gravity: Int = Gravity.NO_GRAVITY,
        leftMargin: Float = 0f,
        topMargin: Float = 0f,
        rightMargin: Float = 0f,
        bottomMargin: Float = 0f,
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight).apply {
            this.gravity = gravity
            setMargins(
                AndroidUtilities.dp(leftMargin),
                AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin),
                AndroidUtilities.dp(bottomMargin),
            )
        }
    }

    fun createLinear(
        width: Int,
        height: Int,
        weight: Float,
        gravity: Int,
        leftMargin: Int,
        topMargin: Int,
        rightMargin: Int,
        bottomMargin: Int,
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight).apply {
            this.gravity = gravity
            setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
        }
    }

    fun createScroll(
        width: Int = MATCH_PARENT,
        height: Int = MATCH_PARENT,
        gravity: Int = Gravity.NO_GRAVITY,
        leftMargin: Float = 0f,
        topMargin: Float = 0f,
        rightMargin: Float = 0f,
        bottomMargin: Float = 0f,
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), gravity).apply {
            setMargins(
                AndroidUtilities.dp(leftMargin),
                AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin),
                AndroidUtilities.dp(bottomMargin),
            )
        }
    }

    fun createScroll(
        width: Int,
        height: Int,
        gravity: Int,
        leftMargin: Int,
        topMargin: Int,
        rightMargin: Int,
        bottomMargin: Int,
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), gravity).apply {
            setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
        }
    }

    private fun getSize(size: Float): Int {
        return if (size < 0f) {
            size.toInt()
        } else {
            AndroidUtilities.dp(size)
        }
    }
}
