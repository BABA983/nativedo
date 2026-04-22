package io.github.baba983.nativedo.core.util

import android.graphics.PointF
import android.view.animation.Interpolator

class CubicBezierInterpolator(
    private val start: PointF,
    private val end: PointF,
) : Interpolator {
    private val a = PointF()
    private val b = PointF()
    private val c = PointF()

    init {
        require(start.x in 0f..1f) { "startX value must be in the range [0, 1]" }
        require(end.x in 0f..1f) { "endX value must be in the range [0, 1]" }
    }

    constructor(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ) : this(PointF(startX, startY), PointF(endX, endY))

    constructor(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ) : this(
        startX = startX.toFloat(),
        startY = startY.toFloat(),
        endX = endX.toFloat(),
        endY = endY.toFloat(),
    )

    override fun getInterpolation(input: Float): Float {
        return getBezierCoordinateY(getXForTime(input))
    }

    private fun getBezierCoordinateY(time: Float): Float {
        c.y = 3f * start.y
        b.y = (3f * (end.y - start.y)) - c.y
        a.y = 1f - c.y - b.y
        return time * (c.y + time * (b.y + (time * a.y)))
    }

    private fun getXForTime(time: Float): Float {
        var x = time
        repeat(13) {
            val z = getBezierCoordinateX(x) - time
            if (kotlin.math.abs(z) < 1e-3f) {
                return x
            }
            x -= z / getXDerivative(x)
        }
        return x
    }

    private fun getXDerivative(time: Float): Float {
        return c.x + time * ((2f * b.x) + (3f * a.x * time))
    }

    private fun getBezierCoordinateX(time: Float): Float {
        c.x = 3f * start.x
        b.x = (3f * (end.x - start.x)) - c.x
        a.x = 1f - c.x - b.x
        return time * (c.x + time * (b.x + (time * a.x)))
    }

    companion object {
        val DEFAULT: CubicBezierInterpolator = CubicBezierInterpolator(0.25, 0.1, 0.25, 1.0)
        val EASE_OUT: CubicBezierInterpolator = CubicBezierInterpolator(0.0, 0.0, 0.58, 1.0)
        val EASE_OUT_QUINT: CubicBezierInterpolator = CubicBezierInterpolator(0.23, 1.0, 0.32, 1.0)
        val EASE_IN: CubicBezierInterpolator = CubicBezierInterpolator(0.42, 0.0, 1.0, 1.0)
        val EASE_BOTH: CubicBezierInterpolator = CubicBezierInterpolator(0.42, 0.0, 0.58, 1.0)
        val EASE_OUT_BACK: CubicBezierInterpolator = CubicBezierInterpolator(0.34, 1.56, 0.64, 1.0)
    }
}
