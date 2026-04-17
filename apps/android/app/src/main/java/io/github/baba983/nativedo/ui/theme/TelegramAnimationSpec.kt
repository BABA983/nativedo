package io.github.baba983.nativedo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Telegram-style animation parameters
 *
 * All values are extracted from Telegram Android source code:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java
 */
object TelegramAnimationSpec {
    /**
     * Standard page transition duration (milliseconds)
     * Source: ActionBarLayout.java line 1771
     */
    const val STANDARD_DURATION = 150

    /**
     * Preview mode transition duration (milliseconds)
     * Source: ActionBarLayout.java line 1771
     */
    const val PREVIEW_DURATION = 190

    /**
     * Predictive back cancel animation duration (milliseconds)
     * Source: ActionBarLayout.java line 1561
     */
    const val PREDICTIVE_BACK_CANCEL_DURATION = 320

    /**
     * Predictive back complete animation duration (milliseconds)
     * Source: ActionBarLayout.java line 1549
     */
    const val PREDICTIVE_BACK_COMPLETE_DURATION = 380

    /**
     * Standard slide offset in dp
     * Source: ActionBarLayout.java line 1833, 1848
     */
    const val STANDARD_SLIDE_OFFSET_DP = 48

    /**
     * Predictive back max drag offset in dp
     * Source: ActionBarLayout.java line 1511, 1552
     */
    const val PREDICTIVE_BACK_MAX_OFFSET_DP = 56

    /**
     * Predictive back gesture-era minimum scale
     * Source: ActionBarLayout.java line 1011
     */
    const val PREDICTIVE_BACK_GESTURE_SCALE = 0.90f

    /**
     * Predictive back completion-era minimum scale
     * Source: ActionBarLayout.java line 1011
     */
    const val PREDICTIVE_BACK_COMPLETE_SCALE = 0.85f

    /**
     * Predictive back scrim alpha ceiling
     * Source: ActionBarLayout.java line 1148
     */
    const val PREDICTIVE_BACK_SCRIM_MAX_ALPHA = 120f / 255f

    /**
     * Predictive back scrim opacity clamp
     * Source: ActionBarLayout.java line 1147
     */
    const val PREDICTIVE_BACK_SCRIM_MAX_OPACITY = 0.8f

    /**
     * Preview mode vertical offset in dp
     * Source: ActionBarLayout.java line 1823
     */
    const val PREVIEW_VERTICAL_OFFSET_DP = 40

    /**
     * Preview mode initial scale
     * Source: ActionBarLayout.java line 1820
     */
    const val PREVIEW_INITIAL_SCALE = 0.7f

    /**
     * Preview mode close scale
     * Source: ActionBarLayout.java line 1839
     */
    const val PREVIEW_CLOSE_SCALE = 0.9f

    /**
     * Background overlay alpha during preview
     * Source: ActionBarLayout.java line 1828
     */
    const val PREVIEW_BACKGROUND_ALPHA = 0x2e / 255f // ~0.18

    /**
     * Exit transition target alpha (keeps previous page slightly visible)
     * Source: Observed behavior in Telegram
     */
    const val EXIT_TARGET_ALPHA = 0.7f

    /**
     * Predictive back completion threshold (0.0 - 1.0)
     * Used for manual edge-drag progress on paths without system predictive back
     */
    const val PREDICTIVE_BACK_THRESHOLD = 0.3f
}

/**
 * Telegram-style easing curves
 */
object TelegramEasing {
    /**
     * DecelerateInterpolator equivalent
     * Used for standard page transitions
     * Source: ActionBarLayout.java line 1814
     */
    val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /**
     * AccelerateDecelerateInterpolator equivalent
     * Used for alpha animations
     * Source: ActionBarLayout.java line 2079
     */
    val AccelerateDecelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)

    /**
     * OvershootInterpolator equivalent
     * Used for preview mode open animation
     * Source: ActionBarLayout.java line 1809
     */
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    /**
     * CubicBezierInterpolator.EASE_OUT_QUINT equivalent
     * Used for predictive back animations and preview close
     * Source: ActionBarLayout.java line 1556, 1568, 1811
     */
    val EaseOutQuint: Easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
}

/**
 * Helper to check if we should use new back transitions
 * Source: ActionBarLayout.java line 1532-1534
 */
fun shouldUseNewBackTransitions(
    isPredictiveBackInProgress: Boolean,
    hasPredictiveBackProgress: Boolean
): Boolean = isPredictiveBackInProgress && hasPredictiveBackProgress

/**
 * Calculate slide progress for inner translation
 * Source: ActionBarLayout.java line 862-867
 */
fun calculateSlideProgress(
    translationX: Float,
    containerWidth: Float,
    useNewBackTransitions: Boolean
): Float {
    return if (useNewBackTransitions) {
        // New: progress based on 6 * 56dp
        (translationX / (6 * 56)).coerceIn(0f, 1f)
    } else {
        // Old: progress based on container width
        translationX / containerWidth
    }
}

/**
 * Blend two colors based on animation progress
 * Source: ActionBarLayout.java line 876
 */
fun blendColors(
    startColor: Int,
    endColor: Int,
    ratio: Float
): Int {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverseRatio = 1f - clampedRatio

    val a = ((startColor shr 24 and 0xff) * inverseRatio + (endColor shr 24 and 0xff) * clampedRatio).toInt()
    val r = ((startColor shr 16 and 0xff) * inverseRatio + (endColor shr 16 and 0xff) * clampedRatio).toInt()
    val g = ((startColor shr 8 and 0xff) * inverseRatio + (endColor shr 8 and 0xff) * clampedRatio).toInt()
    val b = ((startColor and 0xff) * inverseRatio + (endColor and 0xff) * clampedRatio).toInt()

    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
