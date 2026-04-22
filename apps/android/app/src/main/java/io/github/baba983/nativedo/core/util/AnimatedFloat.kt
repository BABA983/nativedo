package io.github.baba983.nativedo.core.util

import android.animation.TimeInterpolator
import android.os.SystemClock
import android.view.View
import androidx.core.math.MathUtils

class AnimatedFloat {
    private var parent: View? = null
    private var invalidate: (() -> Unit)? = null
    private var value: Float = 0f
    private var targetValue: Float = 0f
    private var firstSet: Boolean = true
    private var transitionDelay: Long = 0L
    private var transitionDuration: Long = 200L
    private var transitionInterpolator: TimeInterpolator? = CubicBezierInterpolator.DEFAULT
    private var transition: Boolean = false
    private var transitionStart: Long = 0L
    private var startValue: Float = 0f

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
        initialValue: Float,
        parentToInvalidate: View,
    ) : this(parentToInvalidate) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    constructor(
        initialValue: Float,
        invalidate: () -> Unit,
    ) : this(invalidate) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    constructor(
        initialValue: Float,
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
        initialValue: Float,
        invalidate: () -> Unit,
        transitionDelay: Long,
        transitionDuration: Long,
        transitionInterpolator: TimeInterpolator?,
    ) : this(invalidate, transitionDelay, transitionDuration, transitionInterpolator) {
        value = initialValue
        targetValue = initialValue
        firstSet = false
    }

    @Deprecated("Use getValue() to match Telegram's frame-driven animation contract")
    fun get(): Float = value

    fun set(mustBe: Float): Float = set(mustBe, force = false)

    fun set(mustBe: Boolean): Float = set(if (mustBe) 1f else 0f, force = false)

    fun force(value: Float) {
        set(value, force = true)
    }

    fun force(value: Boolean) {
        set(if (value) 1f else 0f, force = true)
    }

    fun set(
        mustBe: Boolean,
        force: Boolean,
    ): Float = set(if (mustBe) 1f else 0f, force = force)

    fun set(
        mustBe: Float,
        force: Boolean,
    ): Float {
        if (force || transitionDuration <= 0L || firstSet) {
            value = mustBe
            targetValue = mustBe
            transition = false
            firstSet = false
        } else if (kotlin.math.abs(targetValue - mustBe) > 0.0001f) {
            transition = true
            targetValue = mustBe
            startValue = value
            transitionStart = SystemClock.elapsedRealtime()
        }
        return getValue()
    }

    fun getValue(): Float {
        if (transition) {
            val now = SystemClock.elapsedRealtime()
            val progress = MathUtils.clamp(
                (now - transitionStart - transitionDelay) / transitionDuration.toFloat(),
                0f,
                1f,
            )
            if (now - transitionStart >= transitionDelay) {
                value = if (transitionInterpolator == null) {
                    AndroidUtilities.lerp(startValue, targetValue, progress)
                } else {
                    AndroidUtilities.lerp(startValue, targetValue, transitionInterpolator!!.getInterpolation(progress))
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

    fun setDuration(duration: Long) {
        transitionDuration = duration
    }

    fun setDelay(delay: Long) {
        transitionDelay = delay
    }

    fun getDuration(): Long = transitionDuration

    fun isInProgress(): Boolean = transition

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

    fun getTargetValue(): Float = targetValue

    fun setParent(parent: View?) {
        this.parent = parent
    }

    fun setInvalidate(invalidate: (() -> Unit)?) {
        this.invalidate = invalidate
    }
}
