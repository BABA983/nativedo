/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.Components.CubicBezierInterpolator.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/CubicBezierInterpolator.java
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

import android.view.animation.Interpolator
import kotlin.math.abs

class CubicBezierInterpolator(
    private val startX: Double,
    private val startY: Double,
    private val endX: Double,
    private val endY: Double,
) : Interpolator {
    init {
        require(startX in 0.0..1.0) { "startX value must be in range [0, 1]" }
        require(endX in 0.0..1.0) { "endX value must be in range [0, 1]" }
    }

    constructor(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ) : this(startX.toDouble(), startY.toDouble(), endX.toDouble(), endY.toDouble())

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) {
            return 0f
        }
        if (input >= 1f) {
            return 1f
        }
        return sampleCurveY(solveCurveX(input.toDouble())).toFloat()
    }

    private fun sampleCurveX(time: Double): Double {
        val inverse = 1.0 - time
        return (3.0 * inverse * inverse * time * startX) +
            (3.0 * inverse * time * time * endX) +
            (time * time * time)
    }

    private fun sampleCurveY(time: Double): Double {
        val inverse = 1.0 - time
        return (3.0 * inverse * inverse * time * startY) +
            (3.0 * inverse * time * time * endY) +
            (time * time * time)
    }

    private fun sampleCurveDerivativeX(time: Double): Double {
        val inverse = 1.0 - time
        return (3.0 * inverse * inverse * startX) +
            (6.0 * inverse * time * (endX - startX)) +
            (3.0 * time * time * (1.0 - endX))
    }

    private fun solveCurveX(x: Double): Double {
        var time = x
        repeat(8) {
            val delta = sampleCurveX(time) - x
            if (abs(delta) < NEWTON_EPSILON) {
                return time
            }
            val derivative = sampleCurveDerivativeX(time)
            if (abs(derivative) < NEWTON_EPSILON) {
                return binarySubdivide(x)
            }
            time -= delta / derivative
        }
        return binarySubdivide(x)
    }

    private fun binarySubdivide(x: Double): Double {
        var start = 0.0
        var end = 1.0
        var time = x
        repeat(12) {
            val value = sampleCurveX(time)
            if (abs(value - x) < SUBDIVISION_EPSILON) {
                return time
            }
            if (value < x) {
                start = time
            } else {
                end = time
            }
            time = (start + end) / 2.0
        }
        return time
    }

    companion object {
        private const val NEWTON_EPSILON: Double = 1e-7
        private const val SUBDIVISION_EPSILON: Double = 1e-7

        @JvmField
        val DEFAULT: CubicBezierInterpolator = CubicBezierInterpolator(0.25, 0.1, 0.25, 1.0)

        @JvmField
        val EASE_OUT: CubicBezierInterpolator = CubicBezierInterpolator(0.0, 0.0, 0.58, 1.0)

        @JvmField
        val EASE_OUT_QUINT: CubicBezierInterpolator = CubicBezierInterpolator(0.23, 1.0, 0.32, 1.0)

        @JvmField
        val EASE_IN: CubicBezierInterpolator = CubicBezierInterpolator(0.42, 0.0, 1.0, 1.0)

        @JvmField
        val EASE_BOTH: CubicBezierInterpolator = CubicBezierInterpolator(0.42, 0.0, 0.58, 1.0)

        @JvmField
        val EASE_OUT_BACK: CubicBezierInterpolator = CubicBezierInterpolator(0.34, 1.56, 0.64, 1.0)
    }
}
