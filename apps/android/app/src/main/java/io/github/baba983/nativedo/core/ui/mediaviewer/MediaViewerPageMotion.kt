package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class MediaViewerSwitchDirection(val step: Int) {
    Previous(step = -1),
    Next(step = 1),
}

private const val MEDIA_VIEWER_PAGE_SWITCH_POSITION_THRESHOLD = 0.35f
private const val MEDIA_VIEWER_PAGE_SWITCH_TARGET_STICKY_THRESHOLD_PX = 24f
private const val MEDIA_VIEWER_PAGE_SWITCH_REST_TRANSLATION_EPSILON_PX = 0.5f

/**
 * Telegram uses `AndroidUtilities.dp(650)` as the fling-to-switch threshold.
 */
private const val MEDIA_VIEWER_PAGE_SWITCH_VELOCITY_THRESHOLD_DP = 650f

private data class MediaViewerPageMotionSnapshot(
    val currentPage: Int,
    val targetPage: Int? = null,
    val translationX: Float = 0f,
    val isSwitching: Boolean = false,
)

internal class MediaViewerPageMotionState(
    initialPage: Int,
) {
    private var snapshot by mutableStateOf(
        MediaViewerPageMotionSnapshot(currentPage = initialPage),
    )

    val currentPage: Int
        get() = snapshot.currentPage

    val targetPage: Int?
        get() = snapshot.targetPage

    val translationX: Float
        get() = snapshot.translationX

    val isSwitching: Boolean
        get() = snapshot.isSwitching

    val hasActiveMotion: Boolean
        get() = targetPage != null ||
            kotlin.math.abs(translationX) > MEDIA_VIEWER_PAGE_SWITCH_REST_TRANSLATION_EPSILON_PX ||
            isSwitching

    val switchDirection: MediaViewerSwitchDirection?
        get() = resolveMediaViewerSwitchDirection(
            currentPage = currentPage,
            targetPage = targetPage,
        )

    fun updateTranslation(
        translationX: Float,
        targetPage: Int? = this.targetPage,
        isSwitching: Boolean = false,
    ) {
        val stableTargetPage = resolveMediaViewerStableTargetPage(
            currentTargetPage = snapshot.targetPage,
            proposedTargetPage = targetPage,
            translationX = translationX,
            isSwitching = isSwitching,
        )
        snapshot = snapshot.copy(
            translationX = translationX,
            targetPage = stableTargetPage,
            isSwitching = isSwitching,
        )
    }

    fun beginSwitch(
        targetPage: Int,
        translationX: Float,
    ) {
        snapshot = snapshot.copy(
            targetPage = targetPage,
            translationX = translationX,
            isSwitching = true,
        )
    }

    fun finishSwitch() {
        snapshot = MediaViewerPageMotionSnapshot(
            currentPage = targetPage ?: currentPage,
        )
    }

    fun cancelSwitch() {
        snapshot = snapshot.copy(
            targetPage = null,
            isSwitching = false,
        )
    }

    fun settleAt(page: Int) {
        snapshot = MediaViewerPageMotionSnapshot(currentPage = page)
    }

    fun reset() {
        snapshot = snapshot.copy(
            translationX = 0f,
            targetPage = null,
            isSwitching = false,
        )
    }
}

private fun resolveMediaViewerStableTargetPage(
    currentTargetPage: Int?,
    proposedTargetPage: Int?,
    translationX: Float,
    isSwitching: Boolean,
): Int? {
    if (isSwitching) {
        return proposedTargetPage ?: currentTargetPage
    }
    if (proposedTargetPage == currentTargetPage) {
        return proposedTargetPage
    }
    if (proposedTargetPage == null) {
        return if (
            kotlin.math.abs(translationX) <= MEDIA_VIEWER_PAGE_SWITCH_REST_TRANSLATION_EPSILON_PX
        ) {
            null
        } else {
            currentTargetPage
        }
    }
    if (currentTargetPage == null) {
        return proposedTargetPage
    }
    return if (kotlin.math.abs(translationX) >= MEDIA_VIEWER_PAGE_SWITCH_TARGET_STICKY_THRESHOLD_PX) {
        proposedTargetPage
    } else {
        currentTargetPage
    }
}

internal fun resolveMediaViewerSwitchDirection(
    currentPage: Int,
    targetPage: Int?,
): MediaViewerSwitchDirection? {
    if (targetPage == null || targetPage == currentPage) return null
    return if (targetPage > currentPage) {
        MediaViewerSwitchDirection.Next
    } else {
        MediaViewerSwitchDirection.Previous
    }
}

internal fun resolveMediaViewerSwitchProgress(
    translationX: Float,
    travelDistance: Float,
): Float {
    if (travelDistance <= 0f) return 0f
    return (kotlin.math.abs(translationX) / travelDistance).coerceIn(0f, 1f)
}

internal fun resolveMediaViewerPageTravelDistance(
    containerWidth: Float,
    pageGap: Float,
): Float = (containerWidth + pageGap).coerceAtLeast(0f)

internal fun resolveMediaViewerPageMotionAlpha(
    page: Int,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
): Float {
    val progress = switchProgress.coerceIn(0f, 1f)
    return when {
        targetPage == null -> if (page == currentPage) 1f else 0f
        page == currentPage -> 1f - progress
        page == targetPage -> progress
        else -> 0f
    }
}

/**
 * Scale for the side (incoming) page during a page switch.
 *
 * Telegram uses `1.0 - (1-alpha)*0.3` on the side image, producing a 0.7→1.0
 * range.  In practice, because the side image is positioned next to the current
 * image (translation-based, like a pager), an overly aggressive scale makes the
 * transition look like a "transition effect" rather than a "scroll".
 *
 * We keep the center page at scale 1.0 (no scale change) and apply a subtle
 * 0.9→1.0 reveal on the incoming page only.
 */
internal fun resolveMediaViewerSidePageScale(
    progress: Float,
): Float = 0.9f + 0.1f * progress.coerceIn(0f, 1f)

internal fun resolveMediaViewerSwitchTargetPage(
    currentPage: Int,
    translationX: Float,
    pageCount: Int,
): Int? {
    if (pageCount <= 0 || translationX == 0f) return null
    val proposed = if (translationX < 0f) {
        currentPage + 1
    } else {
        currentPage - 1
    }
    return proposed.takeIf { it in 0 until pageCount }
}

internal fun resolveMediaViewerPageTranslationX(
    page: Int,
    currentPage: Int,
    targetPage: Int?,
    translationX: Float,
    travelDistance: Float,
): Float {
    if (travelDistance <= 0f) return 0f
    return when {
        page == currentPage -> translationX
        page == targetPage && targetPage > currentPage -> translationX + travelDistance
        page == targetPage && targetPage < currentPage -> translationX - travelDistance
        else -> 0f
    }
}

internal fun resolveMediaViewerPageVisibility(
    page: Int,
    currentPage: Int,
    targetPage: Int?,
): Boolean {
    return page == currentPage || page == targetPage
}

internal fun shouldCommitMediaViewerPageSwitch(
    translationX: Float,
    velocityX: Float,
    containerWidth: Float,
    density: Float,
): Boolean {
    if (containerWidth <= 0f) return false
    val progress = kotlin.math.abs(translationX) / containerWidth
    if (progress >= MEDIA_VIEWER_PAGE_SWITCH_POSITION_THRESHOLD) {
        return true
    }
    val velocityThresholdPx = MEDIA_VIEWER_PAGE_SWITCH_VELOCITY_THRESHOLD_DP *
        density.coerceAtLeast(1f)
    if (kotlin.math.abs(velocityX) < velocityThresholdPx) {
        return false
    }
    if (translationX == 0f) return false
    return kotlin.math.sign(translationX) == kotlin.math.sign(velocityX)
}
