package io.github.baba983.nativedo.core.util

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

object LayoutHelper {
    const val MATCH_PARENT: Int = -1
    const val WRAP_CONTENT: Int = -2

    private fun getSize(size: Float): Int {
        return if (size < 0f) {
            size.toInt()
        } else {
            AndroidUtilities.dp(size)
        }
    }

    fun createFrame(
        width: Int,
        height: Int,
        gravity: Int = Gravity.TOP or Gravity.START,
        leftMargin: Float = 0f,
        topMargin: Float = 0f,
        rightMargin: Float = 0f,
        bottomMargin: Float = 0f,
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat())).apply {
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
            this.leftMargin = AndroidUtilities.dp(leftMargin)
            this.topMargin = AndroidUtilities.dp(topMargin)
            this.rightMargin = AndroidUtilities.dp(rightMargin)
            this.bottomMargin = AndroidUtilities.dp(bottomMargin)
        }
    }
}
