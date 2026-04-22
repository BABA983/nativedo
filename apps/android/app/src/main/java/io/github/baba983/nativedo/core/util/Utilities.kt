package io.github.baba983.nativedo.core.util

import android.os.Handler
import android.os.Looper

object Utilities {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun clamp(value: Int, max: Int, min: Int): Int {
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    fun clamp(value: Float, max: Float, min: Float): Float {
        if (value.isNaN()) {
            return min
        }
        if (value.isInfinite()) {
            return max
        }
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    fun clamp01(value: Float): Float = clamp(value = value, max = 1f, min = 0f)

    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    fun runOnMainThread(
        delayMillis: Long = 0L,
        runnable: () -> Unit,
    ): Runnable {
        val task = Runnable(runnable)
        if (delayMillis <= 0L) {
            mainHandler.post(task)
        } else {
            mainHandler.postDelayed(task, delayMillis)
        }
        return task
    }

    fun cancelRunOnMainThread(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }
}
