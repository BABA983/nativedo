package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaItem
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerAction
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerClipInsets
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerEnterTransform
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerGestureState
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerLaunchSource
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerPageMotionState
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerPanResult
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerRequest
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerState
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerTapAction
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerSwitchDirection
import io.github.baba983.nativedo.core.ui.mediaviewer.applyMediaViewerPanDelta
import io.github.baba983.nativedo.core.ui.mediaviewer.buildMediaViewerRequestHeaders
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerContentMask
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerThumbnailWindow
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerThumbnailAnchorIndex
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerEdgeTapZoneWidth
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerEnterTransform
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerExitTransform
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerPopupOffset
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerSourceClipInsets
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerSourceCornerRadius
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerTargetRect
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerTargetRectInContainer
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerWindowDismissTransform
import io.github.baba983.nativedo.core.ui.mediaviewer.calculatePinchTransform
import io.github.baba983.nativedo.core.ui.mediaviewer.clampMediaViewerOffset
import io.github.baba983.nativedo.core.ui.mediaviewer.isTapInsideContent
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerActions
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerBaseScrimAlpha
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerCloseContentAlpha
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerDialogProperties
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerDismissedScale
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerDoubleTapTargetOffset
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerOpenScrimAlpha
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerPopupCascadeProgress
import io.github.baba983.nativedo.core.ui.mediaviewer.mediaViewerPopupOpenDuration
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldAnimateMediaViewerPageJump
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerAspectRatio
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerDismissSource
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerChromeText
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPageSwitchSpec
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPageMotionAlpha
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerSidePageScale
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPageTravelDistance
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPageTranslationX
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPageVisibility
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerSwitchProgress
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerSwitchTargetPage
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerRenderedScale
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerRenderedTranslation
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerPagerMotion
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerThumbnailSelectionProgress
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerThumbnailTargetWidth
import io.github.baba983.nativedo.core.ui.mediaviewer.resolveMediaViewerTapAction
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldCommitMediaViewerPageSwitch
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldEnableMediaViewerPagerScroll
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldStartMediaViewerDismissDrag
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldStartMediaViewerPageSwitchDrag
import io.github.baba983.nativedo.core.ui.mediaviewer.settleMediaViewerScale
import io.github.baba983.nativedo.core.ui.mediaviewer.updateMediaViewerPagerGestureLock
import io.github.baba983.nativedo.core.ui.mediaviewer.isMediaViewerAtRest
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldAnimateMediaViewerDismissToSource
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldDelayMediaViewerThumbnailHide
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldDismissViewer
import io.github.baba983.nativedo.core.ui.mediaviewer.shouldRenderMediaViewerCloseOverlay
import io.github.baba983.nativedo.core.ui.mediaviewer.suggestMediaDownloadFileName
import io.github.baba983.nativedo.core.ui.mediaviewer.toggleMediaViewerScale
import io.github.baba983.nativedo.core.ui.mediaviewer.toMediaItem
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerThumbnailStripOffset
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerThumbnailStripScrollPosition
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerThumbnailStripScrollTarget
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerFilmstripCenterOffset
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerFilmstripItemLeft
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerFilmstripItemLayouts
import io.github.baba983.nativedo.core.ui.mediaviewer.calculateMediaViewerFilmstripWindow
import io.github.baba983.nativedo.core.ui.mediaviewer.updateMediaViewerPageTranslation
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import java.util.IdentityHashMap

class MediaViewerTest {

    @Test
    fun updateMediaViewerPagerGestureLock_matchesMultiTouchLifecycle() {
        assertThat(
            updateMediaViewerPagerGestureLock(
                wasLocked = false,
                pressedPointerCount = 2,
            ),
        ).isTrue()

        assertThat(
            updateMediaViewerPagerGestureLock(
                wasLocked = true,
                pressedPointerCount = 1,
            ),
        ).isTrue()

        assertThat(
            updateMediaViewerPagerGestureLock(
                wasLocked = true,
                pressedPointerCount = 0,
            ),
        ).isFalse()
    }

    @Test
    fun shouldEnableMediaViewerPagerScroll_requiresRestingTransform() {
        assertThat(
            shouldEnableMediaViewerPagerScroll(
                isTransitionAnimating = false,
                scale = 1f,
                dismissOffsetY = 0f,
                isPagerGestureLocked = false,
            ),
        ).isTrue()

        assertThat(
            shouldEnableMediaViewerPagerScroll(
                isTransitionAnimating = false,
                scale = 1.2f,
                dismissOffsetY = 0f,
                isPagerGestureLocked = false,
            ),
        ).isFalse()

        assertThat(
            shouldEnableMediaViewerPagerScroll(
                isTransitionAnimating = false,
                scale = 1f,
                dismissOffsetY = 24f,
                isPagerGestureLocked = false,
            ),
        ).isFalse()
    }

    @Test
    fun resolveMediaViewerPagerMotion_usesSettledPageAsOutgoingDuringAdjacentSwipe() {
        val motion = resolveMediaViewerPagerMotion(
            settledPage = 1,
            currentPage = 1,
            targetPage = 2,
            currentPageOffsetFraction = -0.3f,
            pageCount = 4,
        )

        assertThat(motion.currentPage).isEqualTo(1)
        assertThat(motion.targetPage).isEqualTo(2)
        assertThat(motion.switchProgress).isEqualTo(0.3f)
    }

    @Test
    fun resolveMediaViewerPagerMotion_keepsOutgoingPageAfterCurrentPageFlips() {
        val motion = resolveMediaViewerPagerMotion(
            settledPage = 1,
            currentPage = 2,
            targetPage = 2,
            currentPageOffsetFraction = 0.4f,
            pageCount = 4,
        )

        assertThat(motion.currentPage).isEqualTo(1)
        assertThat(motion.targetPage).isEqualTo(2)
        assertThat(motion.switchProgress).isEqualTo(0.6f)
    }

    @Test
    fun thumbnailSelectionProgress_staysContinuousWhenPagerCurrentPageFlipsMidSwipe() {
        val motion = resolveMediaViewerPagerMotion(
            settledPage = 1,
            currentPage = 2,
            targetPage = 2,
            currentPageOffsetFraction = 0.4f,
            pageCount = 4,
        )

        assertThat(
            resolveMediaViewerThumbnailSelectionProgress(
                itemIndex = 1,
                currentPage = motion.currentPage,
                targetPage = motion.targetPage,
                switchProgress = motion.switchProgress,
            ),
        ).isWithin(0.0001f).of(0.4f)

        assertThat(
            resolveMediaViewerThumbnailSelectionProgress(
                itemIndex = 2,
                currentPage = motion.currentPage,
                targetPage = motion.targetPage,
                switchProgress = motion.switchProgress,
            ),
        ).isWithin(0.0001f).of(0.6f)
    }

    @Test
    fun resolveMediaViewerSwitchProgress_clampsToContainerWidth() {
        assertThat(
            resolveMediaViewerSwitchProgress(
                translationX = -540f,
                travelDistance = 1_080f,
            ),
        ).isEqualTo(0.5f)
    }

    @Test
    fun pageMotionState_updateTranslation_keepsResolvedTargetPageDuringDrag() {
        val state = MediaViewerPageMotionState(initialPage = 3)

        state.updateTranslation(
            translationX = -240f,
            targetPage = 4,
        )

        assertThat(state.currentPage).isEqualTo(3)
        assertThat(state.targetPage).isEqualTo(4)
        assertThat(state.translationX).isEqualTo(-240f)
        assertThat(state.isSwitching).isFalse()
    }

    @Test
    fun pageMotionState_updateTranslation_canPreserveSwitchingFlag() {
        val state = MediaViewerPageMotionState(initialPage = 3)

        state.beginSwitch(
            targetPage = 4,
            translationX = -240f,
        )
        state.updateTranslation(
            translationX = -600f,
            targetPage = 4,
            isSwitching = true,
        )

        assertThat(state.currentPage).isEqualTo(3)
        assertThat(state.targetPage).isEqualTo(4)
        assertThat(state.translationX).isEqualTo(-600f)
        assertThat(state.isSwitching).isTrue()
    }

    @Test
    fun pageMotionState_updateTranslation_keepsTargetStickyNearRest() {
        val state = MediaViewerPageMotionState(initialPage = 3)

        state.updateTranslation(
            translationX = -64f,
            targetPage = 4,
        )
        state.updateTranslation(
            translationX = 6f,
            targetPage = 2,
        )

        assertThat(state.targetPage).isEqualTo(4)
        assertThat(state.translationX).isEqualTo(6f)
        assertThat(state.hasActiveMotion).isTrue()
    }

    @Test
    fun pageMotionState_updateTranslation_allowsTargetFlipAfterCommittedOppositeDrag() {
        val state = MediaViewerPageMotionState(initialPage = 3)

        state.updateTranslation(
            translationX = -64f,
            targetPage = 4,
        )
        state.updateTranslation(
            translationX = 48f,
            targetPage = 2,
        )

        assertThat(state.targetPage).isEqualTo(2)
    }

    @Test
    fun shouldAnimateMediaViewerPageJump_onlyAnimatesAdjacentThumbnailJumps() {
        assertThat(
            shouldAnimateMediaViewerPageJump(
                currentPage = 0,
                targetPage = 1,
            ),
        ).isTrue()

        assertThat(
            shouldAnimateMediaViewerPageJump(
                currentPage = 0,
                targetPage = 2,
            ),
        ).isFalse()
    }

    @Test
    fun resolveMediaViewerChromeText_prefersLightboxMetadataAndAppendsPageLabel() {
        val text = resolveMediaViewerChromeText(
            item = MediaItem.Image(
                rawUrl = "/uploads/default/optimized/1X/demo-thumb.png",
                originalUrl = "/uploads/default/original/1X/demo.png",
                title = "demo.png",
                subtitle = "1464×762 65.9 KB",
            ),
            currentPage = 1,
            pageCount = 3,
        )

        assertThat(text.title).isEqualTo("demo.png")
        assertThat(text.subtitle).isEqualTo("1464×762 65.9 KB | 2/3")
    }

    @Test
    fun resolveMediaViewerChromeText_fallsBackToFileNameWhenMetadataMissing() {
        val text = resolveMediaViewerChromeText(
            item = MediaItem.Image(
                rawUrl = "/uploads/default/original/1X/demo-fallback.jpeg",
            ),
            currentPage = 0,
            pageCount = 1,
        )

        assertThat(text.title).isEqualTo("demo-fallback.jpeg")
        assertThat(text.subtitle).isNull()
    }

    @Test
    fun mediaViewerState_resetsChromeVisibilityOnOpenAndDismiss() {
        val state = MediaViewerState()
        state.hideChrome()

        state.open(
            items = listOf(PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png").toMediaItem()),
        )
        assertThat(state.isChromeVisible).isTrue()

        state.hideChrome()
        state.dismiss()
        assertThat(state.isChromeVisible).isTrue()
    }

    @Test
    fun mediaViewerState_toggleChrome_flipsVisibility() {
        val state = MediaViewerState()

        assertThat(state.isChromeVisible).isTrue()
        state.toggleChrome()
        assertThat(state.isChromeVisible).isFalse()
        state.showChrome()
        assertThat(state.isChromeVisible).isTrue()
    }

    @Test
    fun calculateMediaViewerThumbnailWindow_keepsCurrentPageCenteredWhenPossible() {
        assertThat(
            calculateMediaViewerThumbnailWindow(
                currentPage = 4,
                pageCount = 9,
                maxVisible = 5,
            ),
        ).isEqualTo(2..6)
    }

    @Test
    fun calculateMediaViewerThumbnailWindow_clampsNearStartAndEnd() {
        assertThat(
            calculateMediaViewerThumbnailWindow(
                currentPage = 0,
                pageCount = 9,
                maxVisible = 5,
            ),
        ).isEqualTo(0..4)

        assertThat(
            calculateMediaViewerThumbnailWindow(
                currentPage = 8,
                pageCount = 9,
                maxVisible = 5,
            ),
        ).isEqualTo(4..8)
    }

    @Test
    fun calculateMediaViewerThumbnailAnchorIndex_matchesVisibleWindowStart() {
        assertThat(
            calculateMediaViewerThumbnailAnchorIndex(
                currentPage = 0,
                pageCount = 9,
                maxVisible = 7,
            ),
        ).isEqualTo(0)

        assertThat(
            calculateMediaViewerThumbnailAnchorIndex(
                currentPage = 4,
                pageCount = 9,
                maxVisible = 7,
            ),
        ).isEqualTo(1)

        assertThat(
            calculateMediaViewerThumbnailAnchorIndex(
                currentPage = 8,
                pageCount = 9,
                maxVisible = 7,
            ),
        ).isEqualTo(2)
    }

    @Test
    fun resolveMediaViewerThumbnailTargetWidth_matchesTelegramFilmstripSizing() {
        assertThat(
            resolveMediaViewerThumbnailTargetWidth(
                item = MediaItem.Image(
                    rawUrl = "/uploads/default/original/1X/landscape.png",
                    width = 1600,
                    height = 900,
                ),
                isSelected = false,
            ),
        ).isEqualTo(42.dp)

        assertThat(
            resolveMediaViewerThumbnailTargetWidth(
                item = MediaItem.Image(
                    rawUrl = "/uploads/default/original/1X/landscape.png",
                    width = 1600,
                    height = 900,
                ),
                isSelected = true,
            ),
        ).isEqualTo(64.dp)

        assertThat(
            resolveMediaViewerThumbnailTargetWidth(
                item = MediaItem.Image(
                    rawUrl = "/uploads/default/original/1X/portrait.png",
                    width = 900,
                    height = 1600,
                ),
                isSelected = true,
            ),
        ).isEqualTo(42.dp)
    }

    @Test
    fun resolveMediaViewerThumbnailSelectionProgress_tracksCurrentAndTargetPages() {
        assertThat(
            resolveMediaViewerThumbnailSelectionProgress(
                itemIndex = 3,
                currentPage = 3,
                targetPage = 4,
                switchProgress = 0.25f,
            ),
        ).isEqualTo(0.75f)

        assertThat(
            resolveMediaViewerThumbnailSelectionProgress(
                itemIndex = 4,
                currentPage = 3,
                targetPage = 4,
                switchProgress = 0.25f,
            ),
        ).isEqualTo(0.25f)

        assertThat(
            resolveMediaViewerThumbnailSelectionProgress(
                itemIndex = 2,
                currentPage = 3,
                targetPage = 4,
                switchProgress = 0.25f,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun resolveMediaViewerThumbnailTargetWidth_interpolatesWithSelectionProgress() {
        assertThat(
            resolveMediaViewerThumbnailTargetWidth(
                item = MediaItem.Image(
                    rawUrl = "/uploads/default/original/1X/interpolate.png",
                    width = 1600,
                    height = 900,
                ),
                selectionProgress = 0.5f,
            ),
        ).isEqualTo(53.dp)
    }

    @Test
    fun calculateMediaViewerThumbnailStripOffset_interpolatesBetweenCurrentAndTargetCenters() {
        assertThat(
            calculateMediaViewerThumbnailStripOffset(
                itemWidthsPx = listOf(42f, 42f, 42f),
                currentPage = 1,
                targetPage = null,
                switchProgress = 0f,
                spacingPx = 1f,
            ),
        ).isEqualTo(64f)

        assertThat(
            calculateMediaViewerThumbnailStripOffset(
                itemWidthsPx = listOf(42f, 42f, 42f),
                currentPage = 1,
                targetPage = 2,
                switchProgress = 0.5f,
                spacingPx = 1f,
            ),
        ).isEqualTo(85.5f)
    }

    @Test
    fun calculateMediaViewerThumbnailStripScrollPosition_tracksLazyListAbsoluteOffset() {
        assertThat(
            calculateMediaViewerThumbnailStripScrollPosition(
                itemWidthsPx = listOf(42f, 64f, 42f),
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 10,
                spacingPx = 1f,
            ),
        ).isEqualTo(33f)
    }

    @Test
    fun calculateMediaViewerThumbnailStripScrollTarget_matchesSettledSelectedCenter() {
        assertThat(
            calculateMediaViewerThumbnailStripScrollTarget(
                itemWidthsPx = listOf(42f, 64f, 42f),
                currentPage = 1,
                targetPage = null,
                switchProgress = 0f,
                spacingPx = 1f,
                contentPaddingPx = 129f,
                viewportWidthPx = 300f,
            ),
        ).isEqualTo(54f)
    }

    @Test
    fun calculateMediaViewerFilmstripCenterOffset_keepsCurrentItemAnchoredAtRest() {
        assertThat(
            calculateMediaViewerFilmstripCenterOffset(
                currentPage = 2,
                targetPage = null,
                switchProgress = 0f,
                baseItemWidthPx = 42f,
                spacingPx = 1f,
            ),
        ).isEqualTo(-86f)
    }

    @Test
    fun calculateMediaViewerFilmstripCenterOffset_interpolatesTowardsNextItem() {
        assertThat(
            calculateMediaViewerFilmstripCenterOffset(
                currentPage = 2,
                targetPage = 3,
                switchProgress = 0.5f,
                baseItemWidthPx = 42f,
                spacingPx = 1f,
            ),
        ).isEqualTo(-107.5f)
    }

    @Test
    fun calculateMediaViewerFilmstripItemLeft_centersSelectedWidthWithinBaseSlot() {
        assertThat(
            calculateMediaViewerFilmstripItemLeft(
                itemIndex = 2,
                currentPage = 2,
                targetPage = null,
                switchProgress = 0f,
                baseItemWidthPx = 42f,
                itemWidthPx = 64f,
                spacingPx = 1f,
            ),
        ).isEqualTo(75f)
    }

    @Test
    fun calculateMediaViewerFilmstripWindow_limitsRenderingToVisibleBuffer() {
        assertThat(
            calculateMediaViewerFilmstripWindow(
                currentPage = 5,
                targetPage = 6,
                pageCount = 12,
                extraBuffer = 2,
            ),
        ).isEqualTo(3..8)
    }

    @Test
    fun calculateMediaViewerFilmstripItemLayouts_keepsCurrentAndNextItemsSeparated() {
        val layouts = calculateMediaViewerFilmstripItemLayouts(
            currentPage = 2,
            targetPage = 3,
            switchProgress = 0.5f,
            itemWidthsPx = listOf(42f, 42f, 53f, 53f, 42f),
            baseItemWidthPx = 42f,
            spacingPx = 1f,
            viewportWidthPx = 300f,
        )

        val current = layouts.first { it.itemIndex == 2 }
        val next = layouts.first { it.itemIndex == 3 }

        assertThat(next.leftPx).isAtLeast(current.leftPx + current.widthPx + 1f)
    }

    @Test
    fun calculateMediaViewerFilmstripItemLayouts_pushesTrailingItemsAfterExpandedTarget() {
        val layouts = calculateMediaViewerFilmstripItemLayouts(
            currentPage = 2,
            targetPage = 3,
            switchProgress = 1f,
            itemWidthsPx = listOf(42f, 42f, 42f, 64f, 42f),
            baseItemWidthPx = 42f,
            spacingPx = 1f,
            viewportWidthPx = 300f,
        )

        val target = layouts.first { it.itemIndex == 3 }
        val trailing = layouts.first { it.itemIndex == 4 }

        assertThat(trailing.leftPx).isEqualTo(target.leftPx + target.widthPx + 1f)
    }

    @Test
    fun calculateMediaViewerEdgeTapZoneWidth_matchesTelegramOneEighthRuleWithCap() {
        assertThat(calculateMediaViewerEdgeTapZoneWidth(containerWidth = 720)).isEqualTo(90f)
        assertThat(calculateMediaViewerEdgeTapZoneWidth(containerWidth = 1_440)).isEqualTo(135f)
    }

    @Test
    fun resolveMediaViewerPageVisibility_hidesNonActiveNonTargetPage() {
        assertThat(
            resolveMediaViewerPageVisibility(
                page = 2,
                currentPage = 1,
                targetPage = 0,
            ),
        ).isFalse()
    }

    @Test
    fun resolveMediaViewerPageVisibility_showsCurrentAndTargetPages() {
        assertThat(
            resolveMediaViewerPageVisibility(
                page = 2,
                currentPage = 1,
                targetPage = 2,
            ),
        ).isTrue()

        assertThat(
            resolveMediaViewerPageVisibility(
                page = 1,
                currentPage = 1,
                targetPage = 2,
            ),
        ).isTrue()
    }

    @Test
    fun resolveMediaViewerPageTranslationX_keepsTargetPageOffscreenAtAnimationStart() {
        assertThat(
            resolveMediaViewerPageTranslationX(
                page = 3,
                currentPage = 2,
                targetPage = 3,
                translationX = 0f,
                travelDistance = 1_080f,
            ),
        ).isEqualTo(1_080f)

        assertThat(
            resolveMediaViewerPageTranslationX(
                page = 1,
                currentPage = 2,
                targetPage = 1,
                translationX = 0f,
                travelDistance = 1_080f,
            ),
        ).isEqualTo(-1_080f)
    }

    @Test
    fun resolveMediaViewerPageTravelDistance_addsTelegramStyleGap() {
        assertThat(
            resolveMediaViewerPageTravelDistance(
                containerWidth = 1_000f,
                pageGap = 14f,
            ),
        ).isEqualTo(1_014f)
    }

    @Test
    fun resolveMediaViewerPageMotionAlpha_crossFadesCurrentAndTargetPages() {
        assertThat(
            resolveMediaViewerPageMotionAlpha(
                page = 2,
                currentPage = 2,
                targetPage = 3,
                switchProgress = 0.25f,
            ),
        ).isEqualTo(0.75f)

        assertThat(
            resolveMediaViewerPageMotionAlpha(
                page = 3,
                currentPage = 2,
                targetPage = 3,
                switchProgress = 0.25f,
            ),
        ).isEqualTo(0.25f)
    }

    @Test
    fun resolveMediaViewerSidePageScale_subtleRevealFromNinetyPercent() {
        assertThat(resolveMediaViewerSidePageScale(progress = 0f)).isEqualTo(0.9f)
        assertThat(resolveMediaViewerSidePageScale(progress = 1f)).isEqualTo(1f)
        assertThat(resolveMediaViewerSidePageScale(progress = 0.5f)).isEqualTo(0.95f)
    }

    @Test
    fun resolveMediaViewerTapAction_prefersPreviousHotzoneAtRest() {
        val action = resolveMediaViewerTapAction(
            tapOffset = Offset(x = 42f, y = 840f),
            containerSize = IntSize(width = 1_080, height = 2_340),
            currentScale = 1f,
            hasPrevious = true,
            hasNext = true,
        )

        assertThat(action).isEqualTo(MediaViewerTapAction.NavigatePrevious)
    }

    @Test
    fun resolveMediaViewerTapAction_prefersNextHotzoneAtRest() {
        val action = resolveMediaViewerTapAction(
            tapOffset = Offset(x = 1_040f, y = 840f),
            containerSize = IntSize(width = 1_080, height = 2_340),
            currentScale = 1f,
            hasPrevious = true,
            hasNext = true,
        )

        assertThat(action).isEqualTo(MediaViewerTapAction.NavigateNext)
    }

    @Test
    fun resolveMediaViewerTapAction_togglesChromeForCenterTapOrZoomedState() {
        assertThat(
            resolveMediaViewerTapAction(
                tapOffset = Offset(x = 540f, y = 1_170f),
                containerSize = IntSize(width = 1_080, height = 2_340),
                currentScale = 1f,
                hasPrevious = true,
                hasNext = true,
            ),
        ).isEqualTo(MediaViewerTapAction.ToggleChrome)

        assertThat(
            resolveMediaViewerTapAction(
                tapOffset = Offset(x = 42f, y = 840f),
                containerSize = IntSize(width = 1_080, height = 2_340),
                currentScale = 2f,
                hasPrevious = true,
                hasNext = true,
            ),
        ).isEqualTo(MediaViewerTapAction.ToggleChrome)
    }

    @Test
    fun shouldStartMediaViewerPageSwitchDrag_requiresHorizontalDominanceAndPageAvailability() {
        assertThat(
            shouldStartMediaViewerPageSwitchDrag(
                totalDx = 42f,
                totalDy = 8f,
                touchSlop = 12f,
                canSwitchToPrevious = true,
                canSwitchToNext = false,
            ),
        ).isTrue()
    }

    @Test
    fun updateMediaViewerPageTranslation_clampsToAvailableDirection() {
        assertThat(
            updateMediaViewerPageTranslation(
                currentTranslationX = 0f,
                deltaX = -200f,
                containerWidth = 1_000f,
                canSwitchToPrevious = false,
                canSwitchToNext = true,
            ),
        ).isEqualTo(-200f)

        assertThat(
            updateMediaViewerPageTranslation(
                currentTranslationX = 0f,
                deltaX = 200f,
                containerWidth = 1_000f,
                canSwitchToPrevious = false,
                canSwitchToNext = true,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun shouldAnimateMediaViewerDismissToSource_requiresViewportIntersection() {
        assertThat(
            shouldAnimateMediaViewerDismissToSource(
                dismissSource = MediaViewerLaunchSource(
                    boundsOnScreen = IntRect(left = -320, top = 120, right = -40, bottom = 340),
                ),
                viewportBoundsOnScreen = IntRect(left = 0, top = 0, right = 1_080, bottom = 2_340),
            ),
        ).isFalse()

        assertThat(
            shouldAnimateMediaViewerDismissToSource(
                dismissSource = MediaViewerLaunchSource(
                    boundsOnScreen = IntRect(left = 40, top = 120, right = 320, bottom = 340),
                ),
                viewportBoundsOnScreen = IntRect(left = 0, top = 0, right = 1_080, bottom = 2_340),
            ),
        ).isTrue()
    }

    @Test
    fun shouldDelayMediaViewerThumbnailHide_waitsUntilEnterCanStart() {
        assertThat(
            shouldDelayMediaViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isTrue()

        assertThat(
            shouldDelayMediaViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = true,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()
    }

    @Test
    fun shouldDelayMediaViewerThumbnailHide_doesNotDelayAfterEnterOrWithoutLaunchSource() {
        assertThat(
            shouldDelayMediaViewerThumbnailHide(
                hasLaunchSource = false,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()

        assertThat(
            shouldDelayMediaViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = true,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()
    }

    @Test
    fun mediaViewerBaseScrimAlpha_tracksDismissProgress() {
        assertThat(mediaViewerBaseScrimAlpha(dismissProgress = 0f)).isEqualTo(0.94f)
        assertThat(mediaViewerBaseScrimAlpha(dismissProgress = 0.5f)).isWithin(0.0001f).of(0.79f)
        assertThat(mediaViewerBaseScrimAlpha(dismissProgress = 1f)).isWithin(0.0001f).of(0.64f)
    }

    @Test
    fun mediaViewerOpenScrimAlpha_scalesBaseScrimByEnterProgress() {
        assertThat(
            mediaViewerOpenScrimAlpha(
                enterProgress = 0f,
                dismissProgress = 0f,
            ),
        ).isEqualTo(0f)

        assertThat(
            mediaViewerOpenScrimAlpha(
                enterProgress = 0.5f,
                dismissProgress = 0.25f,
            ),
        ).isWithin(0.0001f).of(0.4325f)

        assertThat(
            mediaViewerOpenScrimAlpha(
                enterProgress = 1f,
                dismissProgress = 1f,
            ),
        ).isWithin(0.0001f).of(0.64f)
    }

    @Test
    fun mediaViewerCloseContentAlpha_fadesLiveLayerOutEarlyDuringClose() {
        assertThat(mediaViewerCloseContentAlpha(progress = 0f)).isEqualTo(1f)
        assertThat(mediaViewerCloseContentAlpha(progress = 0.2f)).isWithin(0.0001f).of(0.4285714f)
        assertThat(mediaViewerCloseContentAlpha(progress = 0.35f)).isEqualTo(0f)
        assertThat(mediaViewerCloseContentAlpha(progress = 1f)).isEqualTo(0f)
    }

    @Test
    fun shouldRenderMediaViewerCloseOverlay_requiresClosingAndSnapshotBitmap() {
        assertThat(
            shouldRenderMediaViewerCloseOverlay(
                isClosing = true,
                hasSnapshotBitmap = true,
            ),
        ).isTrue()

        assertThat(
            shouldRenderMediaViewerCloseOverlay(
                isClosing = false,
                hasSnapshotBitmap = true,
            ),
        ).isFalse()

        assertThat(
            shouldRenderMediaViewerCloseOverlay(
                isClosing = true,
                hasSnapshotBitmap = false,
            ),
        ).isFalse()
    }

    @Test
    fun mediaViewerDialogProperties_drawsBehindSystemBars() {
        val properties = mediaViewerDialogProperties()

        assertThat(properties.usePlatformDefaultWidth).isFalse()
        assertThat(properties.decorFitsSystemWindows).isFalse()
    }

    @Test
    fun mediaViewerActions_returnsShareOpenAndSaveInOrder() {
        assertThat(mediaViewerActions()).containsExactly(
            MediaViewerAction.Share,
            MediaViewerAction.OpenExternal,
            MediaViewerAction.SaveImage,
        ).inOrder()
    }

    @Test
    fun toggleMediaViewerScale_switchesBetweenDefaultAndZoomedScale() {
        assertThat(toggleMediaViewerScale(currentScale = 1f)).isEqualTo(3f)
        assertThat(toggleMediaViewerScale(currentScale = 3f)).isEqualTo(1f)
    }

    @Test
    fun settleMediaViewerScale_clampsInteractiveBounceRangeBackToViewerBounds() {
        assertThat(settleMediaViewerScale(currentScale = 0.82f)).isEqualTo(1f)
        assertThat(settleMediaViewerScale(currentScale = 2.5f)).isEqualTo(2.5f)
        assertThat(settleMediaViewerScale(currentScale = 4.6f)).isEqualTo(4f)
    }

    @Test
    fun clampMediaViewerOffset_limitsPanToScaledImageBounds() {
        val clamped = clampMediaViewerOffset(
            proposed = Offset(x = 2_000f, y = -2_000f),
            scale = 2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(clamped.x).isEqualTo(300f)
        assertThat(clamped.y).isEqualTo(-200f)
    }

    @Test
    fun clampMediaViewerOffset_allowsTemporaryOverscrollWhenSlackIsProvided() {
        val clamped = clampMediaViewerOffset(
            proposed = Offset(x = 2_000f, y = -2_000f),
            scale = 2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
            extraSlack = 96f,
        )

        assertThat(clamped.x).isEqualTo(396f)
        assertThat(clamped.y).isEqualTo(-296f)
    }

    @Test
    fun applyMediaViewerPanDelta_resistsHorizontalDragAfterReachingEdge() {
        val result = applyMediaViewerPanDelta(
            currentOffset = Offset(x = 320f, y = 0f),
            delta = Offset(x = 90f, y = 0f),
            scale = 1.2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 900, height = 700),
        )

        assertThat(result.offset.x).isEqualTo(40f)
        assertThat(result.pageTranslationX).isEqualTo(0f)
    }

    @Test
    fun applyMediaViewerPanDelta_handsOverscrollToAdjacentPageTranslation() {
        val result = applyMediaViewerPanDelta(
            currentOffset = Offset(x = -320f, y = 0f),
            delta = Offset(x = -90f, y = 0f),
            scale = 1.2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 900, height = 700),
            hasAdjacentNext = true,
        )

        assertThat(result.offset.x).isEqualTo(-40f)
        assertThat(result.pageTranslationX).isEqualTo(-370f)
    }

    @Test
    fun resolveMediaViewerPageSwitchSpec_switchesWhenZoomedImageOverscrollsHorizontally() {
        val switchSpec = resolveMediaViewerPageSwitchSpec(
            currentPage = 4,
            offset = Offset(x = -500f, y = 0f),
            pageTranslationX = -120f,
            scale = 1.2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 900, height = 700),
            canSwitchToPrevious = true,
            canSwitchToNext = true,
        )

        assertThat(switchSpec?.direction).isEqualTo(MediaViewerSwitchDirection.Next)
        assertThat(switchSpec?.targetPage).isEqualTo(5)
    }

    @Test
    fun shouldCommitMediaViewerPageSwitch_acceptsFastReleaseInSameDirection() {
        assertThat(
            shouldCommitMediaViewerPageSwitch(
                translationX = -120f,
                velocityX = -1_800f,
                containerWidth = 1_080f,
                density = 2.5f,
            ),
        ).isTrue()
    }

    @Test
    fun shouldCommitMediaViewerPageSwitch_requiresTelegramDensityScaledVelocity() {
        assertThat(
            shouldCommitMediaViewerPageSwitch(
                translationX = -120f,
                velocityX = -1_800f,
                containerWidth = 1_080f,
                density = 3f,
            ),
        ).isFalse()
    }

    @Test
    fun resolveMediaViewerSwitchTargetPage_usesDragDirection() {
        assertThat(
            resolveMediaViewerSwitchTargetPage(
                currentPage = 2,
                translationX = -180f,
                pageCount = 5,
            ),
        ).isEqualTo(3)

        assertThat(
            resolveMediaViewerSwitchTargetPage(
                currentPage = 2,
                translationX = 180f,
                pageCount = 5,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun resolveMediaViewerPageTranslationX_positionsIncomingPageBesideCurrentPage() {
        assertThat(
            resolveMediaViewerPageTranslationX(
                page = 3,
                currentPage = 2,
                targetPage = 3,
                translationX = -200f,
                travelDistance = 1_014f,
            ),
        ).isEqualTo(814f)
    }

    @Test
    fun shouldDismissViewer_returnsTrueForLongPullOrFastDownwardFling() {
        assertThat(
            shouldDismissViewer(
                verticalOffset = 260f,
                velocityY = 400f,
                dismissDistance = 220f,
            ),
        ).isTrue()

        assertThat(
            shouldDismissViewer(
                verticalOffset = 80f,
                velocityY = 2_400f,
                dismissDistance = 220f,
            ),
        ).isTrue()

        assertThat(
            shouldDismissViewer(
                verticalOffset = 80f,
                velocityY = 400f,
                dismissDistance = 220f,
            ),
        ).isFalse()

        assertThat(
            shouldDismissViewer(
                verticalOffset = -260f,
                velocityY = 400f,
                dismissDistance = 220f,
            ),
        ).isTrue()

        assertThat(
            shouldDismissViewer(
                verticalOffset = 80f,
                velocityY = -2_400f,
                dismissDistance = 220f,
            ),
        ).isTrue()
    }

    @Test
    fun shouldStartMediaViewerDismissDrag_requiresVerticalIntent() {
        assertThat(
            shouldStartMediaViewerDismissDrag(
                totalDx = 18f,
                totalDy = 40f,
                dismissTriggerDistancePx = 30f,
            ),
        ).isTrue()

        assertThat(
            shouldStartMediaViewerDismissDrag(
                totalDx = 30f,
                totalDy = 35f,
                dismissTriggerDistancePx = 30f,
            ),
        ).isFalse()

        assertThat(
            shouldStartMediaViewerDismissDrag(
                totalDx = 0f,
                totalDy = -40f,
                dismissTriggerDistancePx = 30f,
            ),
        ).isTrue()
    }

    @Test
    fun isMediaViewerAtRest_toleratesTinyResidualTransform() {
        assertThat(
            isMediaViewerAtRest(
                scale = 1.004f,
                offset = Offset(x = 0.3f, y = -0.3f),
                dismissOffsetY = 0.2f,
            ),
        ).isTrue()

        assertThat(
            isMediaViewerAtRest(
                scale = 1.02f,
                offset = Offset.Zero,
                dismissOffsetY = 0f,
            ),
        ).isFalse()
    }

    @Test
    fun calculatePinchTransform_tracksMovingPinchCenterIncrementally() {
        val transform = calculatePinchTransform(
            previousDistance = 100f,
            currentDistance = 150f,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            previousCenter = Offset(x = 300f, y = 500f),
            currentCenter = Offset(x = 240f, y = 420f),
            containerSize = IntSize(width = 1_000, height = 1_600),
            contentSize = IntSize(width = 700, height = 500),
        )

        assertThat(transform.scale).isEqualTo(1.5f)
        assertThat(transform.offset.x).isWithin(0.0001f).of(40f)
        assertThat(transform.offset.y).isWithin(0.0001f).of(70f)
    }

    @Test
    fun mediaViewerDismissedScale_reducesScaleAsDismissProgressGrows() {
        assertThat(mediaViewerDismissedScale(progress = 0f)).isEqualTo(1f)
        assertThat(mediaViewerDismissedScale(progress = 0.5f)).isEqualTo(0.94f)
        assertThat(mediaViewerDismissedScale(progress = 1f)).isEqualTo(0.88f)
    }

    @Test
    fun buildMediaViewerImageIndexLookup_keepsDuplicateImageInstancesDistinct() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")
        val second = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")

        val lookup = buildMediaViewerImageIndexLookup(
            images = listOf(first, second),
        )

        assertThat(lookup[first]).isEqualTo(0)
        assertThat(lookup[second]).isEqualTo(1)
    }

    @Test
    fun resolveMediaViewerDismissSource_prefersCurrentPageSourceWhenAvailable() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png")
        val second = PostBodyImage(rawUrl = "/uploads/default/original/1X/second.png")
        val firstItem = first.toMediaItem()
        val secondItem = second.toMediaItem()
        val initialSource = MediaViewerLaunchSource(
            boundsOnScreen = IntRect(left = 10, top = 20, right = 110, bottom = 220),
        )
        val secondSource = MediaViewerLaunchSource(
            boundsOnScreen = IntRect(left = 40, top = 60, right = 240, bottom = 260),
        )
        val sources = IdentityHashMap<PostBodyImage, MediaViewerLaunchSource>().apply {
            put(second, secondSource)
        }

        val dismissSource = resolveMediaViewerDismissSource(
            request = MediaViewerRequest(
                items = listOf(firstItem, secondItem),
                initialIndex = 0,
                launchSource = initialSource,
                itemLaunchSources = IdentityHashMap<MediaItem, MediaViewerLaunchSource>().apply {
                    put(secondItem, secondSource)
                },
            ),
            currentPage = 1,
        )

        assertThat(dismissSource).isEqualTo(secondSource)
    }

    @Test
    fun resolveMediaViewerDismissSource_fallsBackToInitialLaunchSourceForInitialPage() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png")
        val firstItem = first.toMediaItem()
        val initialSource = MediaViewerLaunchSource(
            boundsOnScreen = IntRect(left = 10, top = 20, right = 110, bottom = 220),
        )

        val dismissSource = resolveMediaViewerDismissSource(
            request = MediaViewerRequest(
                items = listOf(firstItem),
                initialIndex = 0,
                launchSource = initialSource,
            ),
            currentPage = 0,
        )

        assertThat(dismissSource).isEqualTo(initialSource)
    }

    @Test
    fun shouldHidePostImageThumbnail_matchesOnlySameImageInstance() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")
        val second = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")

        assertThat(
            shouldHidePostImageThumbnail(
                image = first,
                hiddenImage = first,
            ),
        ).isTrue()

        assertThat(
            shouldHidePostImageThumbnail(
                image = second,
                hiddenImage = first,
            ),
        ).isFalse()
    }

    @Test
    fun resolveMediaViewerAspectRatio_prefersLoadedBitmapWhenMetadataIsMissing() {
        val aspectRatio = resolveMediaViewerAspectRatio(
            item = MediaItem.Image(rawUrl = "/uploads/default/original/1X/unknown.png"),
            loadedBitmapSize = IntSize(width = 1_200, height = 400),
            previewBitmapSize = IntSize(width = 800, height = 800),
        )

        assertThat(aspectRatio).isEqualTo(3f)
    }

    @Test
    fun calculateMediaViewerTargetRect_centersImageInsideViewerWindow() {
        val targetRect = calculateMediaViewerTargetRect(
            windowSize = IntSize(width = 1_080, height = 2_340),
            contentSize = IntSize(width = 900, height = 600),
        )

        assertThat(targetRect).isEqualTo(
            IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )
    }

    @Test
    fun calculateMediaViewerTargetRectInContainer_usesActualContainerWindowBounds() {
        val targetRect = calculateMediaViewerTargetRectInContainer(
            containerBounds = IntRect(left = 16, top = 84, right = 1_064, bottom = 2_256),
            contentSize = IntSize(width = 900, height = 600),
        )

        assertThat(targetRect).isEqualTo(
            IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )
    }

    @Test
    fun calculateMediaViewerEnterTransform_interpolatesFromThumbnailToFullscreenRect() {
        val transform = calculateMediaViewerEnterTransform(
            sourceBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
            progress = 0.5f,
        )

        assertThat(transform.scaleX).isWithin(0.0001f).of(0.7f)
        assertThat(transform.scaleY).isWithin(0.0001f).of(0.7f)
        assertThat(transform.translationX).isWithin(0.0001f).of(-160f)
        assertThat(transform.translationY).isWithin(0.0001f).of(-475f)
    }

    @Test
    fun calculateMediaViewerExitTransform_startsFromCurrentViewerPose() {
        val transform = calculateMediaViewerExitTransform(
            sourceBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
            currentScale = 2.1f,
            currentTranslation = Offset(x = 72f, y = -184f),
            progress = 0f,
        )

        assertThat(transform.scaleX).isWithin(0.0001f).of(2.1f)
        assertThat(transform.scaleY).isWithin(0.0001f).of(2.1f)
        assertThat(transform.translationX).isWithin(0.0001f).of(72f)
        assertThat(transform.translationY).isWithin(0.0001f).of(-184f)
    }

    @Test
    fun calculateMediaViewerExitTransform_endsAtThumbnailTransform() {
        val transform = calculateMediaViewerExitTransform(
            sourceBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
            currentScale = 2.1f,
            currentTranslation = Offset(x = 72f, y = -184f),
            progress = 1f,
        )

        assertThat(transform.scaleX).isWithin(0.0001f).of(0.4f)
        assertThat(transform.scaleY).isWithin(0.0001f).of(0.4f)
        assertThat(transform.translationX).isWithin(0.0001f).of(-320f)
        assertThat(transform.translationY).isWithin(0.0001f).of(-950f)
    }

    @Test
    fun calculateMediaViewerSourceClipInsets_mapsClippedThumbnailBackIntoViewerSpace() {
        val clipInsets = calculateMediaViewerSourceClipInsets(
            sourceVisibleBounds = IntRect(left = 40, top = 140, right = 400, bottom = 340),
            sourceFullBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )

        assertThat(clipInsets.left).isEqualTo(0f)
        assertThat(clipInsets.top).isEqualTo(100f)
        assertThat(clipInsets.right).isEqualTo(0f)
        assertThat(clipInsets.bottom).isEqualTo(0f)
    }

    @Test
    fun calculateMediaViewerSourceCornerRadius_mapsThumbnailRadiusIntoViewerSpace() {
        val radius = calculateMediaViewerSourceCornerRadius(
            sourceCornerRadiusPx = 18f,
            sourceFullBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )

        assertThat(radius).isEqualTo(45f)
    }

    @Test
    fun calculateMediaViewerContentMask_keepsRoundedBoundsAtFullImageWhenVisibleAreaIsClipped() {
        val mask = calculateMediaViewerContentMask(
            contentWidth = 900f,
            contentHeight = 600f,
            clipInsets = MediaViewerClipInsets(top = 100f),
            cornerRadiusPx = 45f,
        )

        assertThat(mask).isNotNull()
        assertThat(mask!!.visibleBounds).isEqualTo(
            Rect(left = 0f, top = 100f, right = 900f, bottom = 600f),
        )
        assertThat(mask.roundedBounds).isEqualTo(
            Rect(left = 0f, top = 0f, right = 900f, bottom = 600f),
        )
        assertThat(mask.cornerRadiusPx).isEqualTo(45f)
    }

    @Test
    fun resolveMediaViewerRenderedScale_includesOpenTransformScaleDuringEnter() {
        val scale = resolveMediaViewerRenderedScale(
            displayedScale = 1f,
            openTransform = MediaViewerEnterTransform(
                scaleX = 0.4f,
                scaleY = 0.4f,
                translationX = -320f,
                translationY = -950f,
            ),
        )

        assertThat(scale).isEqualTo(0.4f)
    }

    @Test
    fun resolveMediaViewerRenderedTranslation_includesOpenTransformTranslationDuringEnter() {
        val translation = resolveMediaViewerRenderedTranslation(
            activeOffset = Offset(x = 12f, y = 24f),
            activeDismissOffsetY = 36f,
            openTransform = MediaViewerEnterTransform(
                scaleX = 0.4f,
                scaleY = 0.4f,
                translationX = -320f,
                translationY = -950f,
            ),
        )

        assertThat(translation).isEqualTo(Offset(x = -308f, y = -890f))
    }

    @Test
    fun calculateMediaViewerWindowDismissTransform_slidesTowardScreenEdgeByCurrentDirection() {
        val downward = calculateMediaViewerWindowDismissTransform(
            currentScale = 0.72f,
            currentTranslation = Offset(x = 16f, y = 140f),
            viewportHeight = 2_340,
            progress = 1f,
        )
        val upward = calculateMediaViewerWindowDismissTransform(
            currentScale = 0.72f,
            currentTranslation = Offset(x = 16f, y = -140f),
            viewportHeight = 2_340,
            progress = 1f,
        )

        assertThat(downward.scaleX).isEqualTo(0.72f)
        assertThat(downward.scaleY).isEqualTo(0.72f)
        assertThat(downward.translationX).isEqualTo(16f)
        assertThat(downward.translationY).isEqualTo(2_340f)
        assertThat(upward.translationY).isEqualTo(-2_340f)
    }

    @Test
    fun mediaViewerDoubleTapTargetOffset_keepsCenterTapCentered() {
        val target = mediaViewerDoubleTapTargetOffset(
            tapOffset = Offset(x = 500f, y = 400f),
            currentScale = 1f,
            targetScale = 3f,
            currentOffset = Offset.Zero,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(target).isEqualTo(Offset.Zero)
    }

    @Test
    fun mediaViewerDoubleTapTargetOffset_zoomsTowardTapPointAndClampsToBounds() {
        val target = mediaViewerDoubleTapTargetOffset(
            tapOffset = Offset(x = 800f, y = 100f),
            currentScale = 1f,
            targetScale = 3f,
            currentOffset = Offset.Zero,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(target.x).isEqualTo(-600f)
        assertThat(target.y).isEqualTo(500f)
    }

    @Test
    fun calculatePinchTransform_scalesAroundInitialPinchCenter() {
        val transform = calculatePinchTransform(
            previousDistance = 100f,
            currentDistance = 200f,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            previousCenter = Offset(x = 700f, y = 300f),
            currentCenter = Offset(x = 700f, y = 300f),
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(transform.scale).isEqualTo(2f)
        assertThat(transform.offset).isEqualTo(Offset(x = -200f, y = 100f))
    }

    @Test
    fun calculatePinchTransform_keepsTranslationWhenTwoFingersOnlyRecenter() {
        val transform = calculatePinchTransform(
            previousDistance = 180f,
            currentDistance = 180f,
            currentScale = 2.2f,
            currentOffset = Offset(x = 120f, y = -80f),
            previousCenter = Offset(x = 640f, y = 520f),
            currentCenter = Offset(x = 640f, y = 520f),
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(transform.scale).isEqualTo(2.2f)
        assertThat(transform.offset).isEqualTo(Offset(x = 120f, y = -80f))
    }

    @Test
    fun isTapInsideContent_returnsTrueOnlyInsideCenteredImageBounds() {
        assertThat(
            isTapInsideContent(
                tapOffset = Offset(x = 500f, y = 400f),
                containerSize = IntSize(width = 1_000, height = 800),
                contentSize = IntSize(width = 800, height = 600),
            ),
        ).isTrue()

        assertThat(
            isTapInsideContent(
                tapOffset = Offset(x = 50f, y = 50f),
                containerSize = IntSize(width = 1_000, height = 800),
                contentSize = IntSize(width = 800, height = 600),
            ),
        ).isFalse()
    }

    @Test
    fun buildMediaViewerRequestHeaders_includesBrowserHeadersAndCookie() {
        val baseUrl = "https://linux.do/"
        val resolvedUrl = "https://linux.do/uploads/default/original/1X/demo.webp"
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }

        val headers = buildMediaViewerRequestHeaders(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        )

        assertThat(headers["Accept"])
            .isEqualTo("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        assertThat(headers["Accept-Language"]).isEqualTo("zh-CN,zh;q=0.9,en;q=0.8")
        assertThat(headers["Referer"]).isEqualTo("https://linux.do/")
        assertThat(headers["User-Agent"]).contains("Mozilla/5.0")
        assertThat(headers["Cookie"]).isEqualTo("_t=auth")
    }

    @Test
    fun suggestMediaDownloadFileName_usesLastPathSegment() {
        assertThat(
            suggestMediaDownloadFileName(
                "https://linux.do/uploads/default/original/1X/pretty-image.webp?download=1",
            ),
        ).isEqualTo("pretty-image.webp")
    }

    @Test
    fun calculateMediaViewerPopupOffset_alignsPopupToAnchorRightEdge() {
        val offset = calculateMediaViewerPopupOffset(
            anchorBounds = IntRect(left = 930, top = 40, right = 974, bottom = 84),
            windowSize = IntSize(width = 1_080, height = 2_340),
            popupSize = IntSize(width = 204, height = 156),
            margin = 12,
            spacing = 8,
        )

        assertThat(offset).isEqualTo(IntOffset(x = 770, y = 92))
    }

    @Test
    fun calculateMediaViewerPopupOffset_clampsInsideWindowBounds() {
        val offset = calculateMediaViewerPopupOffset(
            anchorBounds = IntRect(left = 18, top = 40, right = 62, bottom = 84),
            windowSize = IntSize(width = 240, height = 180),
            popupSize = IntSize(width = 204, height = 156),
            margin = 12,
            spacing = 8,
        )

        assertThat(offset).isEqualTo(IntOffset(x = 12, y = 12))
    }

    @Test
    fun mediaViewerPopupOpenDuration_matchesTelegramBaseAndPerItemStep() {
        assertThat(mediaViewerPopupOpenDuration(itemCount = 0)).isEqualTo(150)
        assertThat(mediaViewerPopupOpenDuration(itemCount = 3)).isEqualTo(198)
    }

    @Test
    fun mediaViewerPopupCascadeProgress_matchesTelegramFormulaWithoutCascadeForShortMenu() {
        val first = mediaViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 0f,
            count = 3f,
            waveLength = 4f,
        )
        val second = mediaViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 1f,
            count = 3f,
            waveLength = 4f,
        )
        val third = mediaViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 2f,
            count = 3f,
            waveLength = 4f,
        )

        assertThat(first).isEqualTo(0.5f)
        assertThat(second).isEqualTo(0.5f)
        assertThat(third).isEqualTo(0.5f)
    }

    @Test
    fun mediaViewerPopupCascadeProgress_staggersWhenMenuHasMoreThanWaveLengthItems() {
        val first = mediaViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 0f,
            count = 6f,
            waveLength = 4f,
        )
        val last = mediaViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 5f,
            count = 6f,
            waveLength = 4f,
        )

        assertThat(first).isGreaterThan(last)
        assertThat(last).isAtLeast(0f)
    }

    @Test
    fun gestureState_reset_clearsAllTransformState() {
        val state = MediaViewerGestureState()
        state.scale = 2.5f
        state.offset = Offset(x = 100f, y = -200f)
        state.dismissOffsetY = 120f

        state.reset()
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
        assertThat(state.dismissOffsetY).isEqualTo(0f)
    }
}
