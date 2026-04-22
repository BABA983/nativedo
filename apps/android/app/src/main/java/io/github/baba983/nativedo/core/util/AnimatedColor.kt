package io.github.baba983.nativedo.core.util

import android.animation.TimeInterpolator
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils

class AnimatedColor {
    private var parent: View? = null
    private var invalidate: (() -> Unit)? = null
    private var value: Int = 0
    private var targetValue: Int = 0
    private var firstSet: Boolean = true
    private var transitionDelay: Long = 0L
    private var transitionDuration: Long = 200L
    private var transitionInterpolator: TimeInterpolator? = CubicBezierInterpolator.DEFAULT
    private var transition: Boolean = false
    private var transitionStart: Long = 0L
    private var startValue: Int = 0

    constructor()

    constructor(
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this() {
        this.transitionDuration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
    }

    constructor(
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(transitionDuration, transitionInterpolator) {
        this.transitionDelay = transitionDelay
    }

    constructor(parentToInvalidate: View) : this() {
        parent = parentToInvalidate
    }

    constructor(
        parentToInvalidate: View,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(parentToInvalidate) {
        this.transitionDuration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
    }

    constructor(
        parentToInvalidate: View,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(parentToInvalidate, transitionDuration, transitionInterpolator) {
        this.transitionDelay = transitionDelay
    }

    constructor(invalidate: () -> Unit) : this() {
        this.invalidate = invalidate
    }

    constructor(
        invalidate: () -> Unit,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(invalidate) {
        this.transitionDuration = transitionDuration
        this.transitionInterpolator = transitionInterpolator
    }

    constructor(
        invalidate: () -> Unit,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(invalidate, transitionDuration, transitionInterpolator) {
        this.transitionDelay = transitionDelay
    }

    constructor(
        initialValue: Int,
        parentToInvalidate: View,
    ) : this(parentToInvalidate) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    constructor(
        initialValue: Int,
        invalidate: () -> Unit,
    ) : this(invalidate) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    constructor(
        initialValue: Int,
        parentToInvalidate: View,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(parentToInvalidate, transitionDelay, transitionDuration, transitionInterpolator) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    constructor(
        initialValue: Int,
        invalidate: () -> Unit,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(invalidate, transitionDelay, transitionDuration, transitionInterpolator) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    fun get(): Int = value

    fun force(color: Int): Int = set(color, force = true)

    fun set(color: Int): Int = set(color, force = false)

    fun set(
        color: Int,
        force: Boolean,
    ): Int {
        val now = SystemClock.elapsedRealtime()
        if (force || transitionDuration <= 0L || firstSet) {
            value = color
            targetValue = color
            transition = false
            firstSet = false
        } else if (targetValue != color) {
            transition = true
            targetValue = color
            startValue = value
            transitionStart = now
        }
        if (transition) {
            val progress = MathUtils.clamp(
                (now - transitionStart - transitionDelay) / transitionDuration.toFloat(),
                0f,
                1f,
            )
            if (now - transitionStart >= transitionDelay) {
                value = if (transitionInterpolator == null) {
                    ColorUtils.blendARGB(startValue, targetValue, progress)
                } else {
                    ColorUtils.blendARGB(
                        startValue,
                        targetValue,
                        transitionInterpolator!!.getInterpolation(progress),
                    )
                }
            }
            if (progress >= 1f) {
                transition = false
            } else {
                parent?.invalidate()
                invalidate?.invoke()
            }
        }
        return value
    }

    fun getTransitionProgress(): Float {
        if (!transition) {
            return 0f
        }
        val now = SystemClock.elapsedRealtime()
        return MathUtils.clamp(
            (now - transitionStart - transitionDelay) / transitionDuration.toFloat(),
            0f,
            1f,
        )
    }

    fun getTransitionProgressInterpolated(): Float {
        val interpolator = transitionInterpolator ?: return getTransitionProgress()
        return interpolator.getInterpolation(getTransitionProgress())
    }

    fun setParent(parent: View?) {
        this.parent = parent
    }
}
