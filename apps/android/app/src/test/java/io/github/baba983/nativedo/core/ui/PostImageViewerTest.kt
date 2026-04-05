package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogProperties
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import java.util.IdentityHashMap

class PostImageViewerTest {

    @Test
    fun updatePostImageViewerPagerGestureLock_locksOnMultitouchUntilGestureEnds() {
        assertThat(
            updatePostImageViewerPagerGestureLock(
                wasLocked = false,
                pressedPointerCount = 2,
            ),
        ).isTrue()

        assertThat(
            updatePostImageViewerPagerGestureLock(
                wasLocked = true,
                pressedPointerCount = 1,
            ),
        ).isTrue()

        assertThat(
            updatePostImageViewerPagerGestureLock(
                wasLocked = true,
                pressedPointerCount = 0,
            ),
        ).isFalse()
    }

    @Test
    fun shouldEnablePostImageViewerPagerScroll_disablesPagingDuringLockedPinchGesture() {
        assertThat(
            shouldEnablePostImageViewerPagerScroll(
                isTransitionAnimating = false,
                scale = 1f,
                dismissOffsetY = 0f,
                isPagerGestureLocked = false,
            ),
        ).isTrue()

        assertThat(
            shouldEnablePostImageViewerPagerScroll(
                isTransitionAnimating = false,
                scale = 1f,
                dismissOffsetY = 0f,
                isPagerGestureLocked = true,
            ),
        ).isFalse()
    }

    @Test
    fun shouldAnimatePostImageViewerDismissToSource_requiresViewportIntersection() {
        assertThat(
            shouldAnimatePostImageViewerDismissToSource(
                dismissSource = PostImageViewerLaunchSource(
                    boundsOnScreen = IntRect(left = -320, top = 120, right = -40, bottom = 340),
                ),
                viewportBoundsOnScreen = IntRect(left = 0, top = 0, right = 1_080, bottom = 2_340),
            ),
        ).isFalse()

        assertThat(
            shouldAnimatePostImageViewerDismissToSource(
                dismissSource = PostImageViewerLaunchSource(
                    boundsOnScreen = IntRect(left = 40, top = 120, right = 320, bottom = 340),
                ),
                viewportBoundsOnScreen = IntRect(left = 0, top = 0, right = 1_080, bottom = 2_340),
            ),
        ).isTrue()
    }

    @Test
    fun shouldDelayPostImageViewerThumbnailHide_waitsUntilEnterCanStart() {
        assertThat(
            shouldDelayPostImageViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isTrue()

        assertThat(
            shouldDelayPostImageViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = true,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()
    }

    @Test
    fun shouldDelayPostImageViewerThumbnailHide_doesNotDelayAfterEnterOrWithoutLaunchSource() {
        assertThat(
            shouldDelayPostImageViewerThumbnailHide(
                hasLaunchSource = false,
                hasPlayedEnterAnimation = false,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()

        assertThat(
            shouldDelayPostImageViewerThumbnailHide(
                hasLaunchSource = true,
                hasPlayedEnterAnimation = true,
                hasContentContainerBoundsOnScreen = false,
                currentPage = 0,
                initialPage = 0,
            ),
        ).isFalse()
    }

    @Test
    fun postImageViewerBaseScrimAlpha_tracksDismissProgress() {
        assertThat(postImageViewerBaseScrimAlpha(dismissProgress = 0f)).isEqualTo(0.94f)
        assertThat(postImageViewerBaseScrimAlpha(dismissProgress = 0.5f)).isWithin(0.0001f).of(0.79f)
        assertThat(postImageViewerBaseScrimAlpha(dismissProgress = 1f)).isWithin(0.0001f).of(0.64f)
    }

    @Test
    fun postImageViewerOpenScrimAlpha_scalesBaseScrimByEnterProgress() {
        assertThat(
            postImageViewerOpenScrimAlpha(
                enterProgress = 0f,
                dismissProgress = 0f,
            ),
        ).isEqualTo(0f)

        assertThat(
            postImageViewerOpenScrimAlpha(
                enterProgress = 0.5f,
                dismissProgress = 0.25f,
            ),
        ).isWithin(0.0001f).of(0.4325f)

        assertThat(
            postImageViewerOpenScrimAlpha(
                enterProgress = 1f,
                dismissProgress = 1f,
            ),
        ).isWithin(0.0001f).of(0.64f)
    }

    @Test
    fun postImageViewerCloseContentAlpha_fadesLiveLayerOutEarlyDuringClose() {
        assertThat(postImageViewerCloseContentAlpha(progress = 0f)).isEqualTo(1f)
        assertThat(postImageViewerCloseContentAlpha(progress = 0.2f)).isWithin(0.0001f).of(0.4285714f)
        assertThat(postImageViewerCloseContentAlpha(progress = 0.35f)).isEqualTo(0f)
        assertThat(postImageViewerCloseContentAlpha(progress = 1f)).isEqualTo(0f)
    }

    @Test
    fun shouldRenderPostImageViewerCloseOverlay_requiresClosingAndSnapshotBitmap() {
        assertThat(
            shouldRenderPostImageViewerCloseOverlay(
                isClosing = true,
                hasSnapshotBitmap = true,
            ),
        ).isTrue()

        assertThat(
            shouldRenderPostImageViewerCloseOverlay(
                isClosing = false,
                hasSnapshotBitmap = true,
            ),
        ).isFalse()

        assertThat(
            shouldRenderPostImageViewerCloseOverlay(
                isClosing = true,
                hasSnapshotBitmap = false,
            ),
        ).isFalse()
    }

    @Test
    fun postImageViewerDialogProperties_drawsBehindSystemBars() {
        val properties = postImageViewerDialogProperties()

        assertThat(properties.usePlatformDefaultWidth).isFalse()
        assertThat(properties.decorFitsSystemWindows).isFalse()
    }

    @Test
    fun postImageViewerActions_returnsShareOpenAndSaveInOrder() {
        assertThat(postImageViewerActions()).containsExactly(
            PostImageViewerAction.Share,
            PostImageViewerAction.OpenExternal,
            PostImageViewerAction.SaveImage,
        ).inOrder()
    }

    @Test
    fun togglePostImageViewerScale_switchesBetweenDefaultAndZoomedScale() {
        assertThat(togglePostImageViewerScale(currentScale = 1f)).isEqualTo(3f)
        assertThat(togglePostImageViewerScale(currentScale = 3f)).isEqualTo(1f)
    }

    @Test
    fun settlePostImageViewerScale_clampsInteractiveBounceRangeBackToViewerBounds() {
        assertThat(settlePostImageViewerScale(currentScale = 0.82f)).isEqualTo(1f)
        assertThat(settlePostImageViewerScale(currentScale = 2.5f)).isEqualTo(2.5f)
        assertThat(settlePostImageViewerScale(currentScale = 4.6f)).isEqualTo(4f)
    }

    @Test
    fun clampPostImageViewerOffset_limitsPanToScaledImageBounds() {
        val clamped = clampPostImageViewerOffset(
            proposed = Offset(x = 2_000f, y = -2_000f),
            scale = 2f,
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(clamped.x).isEqualTo(300f)
        assertThat(clamped.y).isEqualTo(-200f)
    }

    @Test
    fun clampPostImageViewerOffset_allowsTemporaryOverscrollWhenSlackIsProvided() {
        val clamped = clampPostImageViewerOffset(
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
    fun shouldDismissPostImageViewer_returnsTrueForLongPullOrFastDownwardFling() {
        assertThat(
            shouldDismissPostImageViewer(
                verticalOffset = 260f,
                velocityY = 400f,
                dismissDistance = 220f,
            ),
        ).isTrue()

        assertThat(
            shouldDismissPostImageViewer(
                verticalOffset = 80f,
                velocityY = 2_400f,
                dismissDistance = 220f,
            ),
        ).isTrue()

        assertThat(
            shouldDismissPostImageViewer(
                verticalOffset = 80f,
                velocityY = 400f,
                dismissDistance = 220f,
            ),
        ).isFalse()
    }

    @Test
    fun postImageViewerDismissedScale_reducesScaleAsDismissProgressGrows() {
        assertThat(postImageViewerDismissedScale(progress = 0f)).isEqualTo(1f)
        assertThat(postImageViewerDismissedScale(progress = 0.5f)).isEqualTo(0.94f)
        assertThat(postImageViewerDismissedScale(progress = 1f)).isEqualTo(0.88f)
    }

    @Test
    fun buildPostImageViewerImageIndexLookup_keepsDuplicateImageInstancesDistinct() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")
        val second = PostBodyImage(rawUrl = "/uploads/default/original/1X/repeated.png")

        val lookup = buildPostImageViewerImageIndexLookup(
            images = listOf(first, second),
        )

        assertThat(lookup[first]).isEqualTo(0)
        assertThat(lookup[second]).isEqualTo(1)
    }

    @Test
    fun resolvePostImageViewerDismissSource_prefersCurrentPageSourceWhenAvailable() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png")
        val second = PostBodyImage(rawUrl = "/uploads/default/original/1X/second.png")
        val initialSource = PostImageViewerLaunchSource(
            boundsOnScreen = IntRect(left = 10, top = 20, right = 110, bottom = 220),
        )
        val secondSource = PostImageViewerLaunchSource(
            boundsOnScreen = IntRect(left = 40, top = 60, right = 240, bottom = 260),
        )
        val sources = IdentityHashMap<PostBodyImage, PostImageViewerLaunchSource>().apply {
            put(second, secondSource)
        }

        val dismissSource = resolvePostImageViewerDismissSource(
            state = PostImageViewerState(
                images = listOf(first, second),
                initialIndex = 0,
                launchSource = initialSource,
                imageLaunchSources = sources,
            ),
            currentPage = 1,
        )

        assertThat(dismissSource).isEqualTo(secondSource)
    }

    @Test
    fun resolvePostImageViewerDismissSource_fallsBackToInitialLaunchSourceForInitialPage() {
        val first = PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png")
        val initialSource = PostImageViewerLaunchSource(
            boundsOnScreen = IntRect(left = 10, top = 20, right = 110, bottom = 220),
        )

        val dismissSource = resolvePostImageViewerDismissSource(
            state = PostImageViewerState(
                images = listOf(first),
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
    fun resolvePostImageViewerAspectRatio_prefersLoadedBitmapWhenMetadataIsMissing() {
        val aspectRatio = resolvePostImageViewerAspectRatio(
            image = PostBodyImage(rawUrl = "/uploads/default/original/1X/unknown.png"),
            loadedBitmapSize = IntSize(width = 1_200, height = 400),
            previewBitmapSize = IntSize(width = 800, height = 800),
        )

        assertThat(aspectRatio).isEqualTo(3f)
    }

    @Test
    fun calculatePostImageViewerTargetRect_centersImageInsideViewerWindow() {
        val targetRect = calculatePostImageViewerTargetRect(
            windowSize = IntSize(width = 1_080, height = 2_340),
            contentSize = IntSize(width = 900, height = 600),
        )

        assertThat(targetRect).isEqualTo(
            IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )
    }

    @Test
    fun calculatePostImageViewerTargetRectInContainer_usesActualContainerWindowBounds() {
        val targetRect = calculatePostImageViewerTargetRectInContainer(
            containerBounds = IntRect(left = 16, top = 84, right = 1_064, bottom = 2_256),
            contentSize = IntSize(width = 900, height = 600),
        )

        assertThat(targetRect).isEqualTo(
            IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )
    }

    @Test
    fun calculatePostImageViewerEnterTransform_interpolatesFromThumbnailToFullscreenRect() {
        val transform = calculatePostImageViewerEnterTransform(
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
    fun calculatePostImageViewerExitTransform_startsFromCurrentViewerPose() {
        val transform = calculatePostImageViewerExitTransform(
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
    fun calculatePostImageViewerExitTransform_endsAtThumbnailTransform() {
        val transform = calculatePostImageViewerExitTransform(
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
    fun calculatePostImageViewerSourceClipInsets_mapsClippedThumbnailBackIntoViewerSpace() {
        val clipInsets = calculatePostImageViewerSourceClipInsets(
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
    fun calculatePostImageViewerSourceCornerRadius_mapsThumbnailRadiusIntoViewerSpace() {
        val radius = calculatePostImageViewerSourceCornerRadius(
            sourceCornerRadiusPx = 18f,
            sourceFullBounds = IntRect(left = 40, top = 100, right = 400, bottom = 340),
            targetBounds = IntRect(left = 90, top = 870, right = 990, bottom = 1_470),
        )

        assertThat(radius).isEqualTo(45f)
    }

    @Test
    fun calculatePostImageViewerContentMask_keepsRoundedBoundsAtFullImageWhenVisibleAreaIsClipped() {
        val mask = calculatePostImageViewerContentMask(
            contentWidth = 900f,
            contentHeight = 600f,
            clipInsets = PostImageViewerClipInsets(top = 100f),
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
    fun resolvePostImageViewerRenderedScale_includesOpenTransformScaleDuringEnter() {
        val scale = resolvePostImageViewerRenderedScale(
            displayedScale = 1f,
            openTransform = PostImageViewerEnterTransform(
                scaleX = 0.4f,
                scaleY = 0.4f,
                translationX = -320f,
                translationY = -950f,
            ),
        )

        assertThat(scale).isEqualTo(0.4f)
    }

    @Test
    fun resolvePostImageViewerRenderedTranslation_includesOpenTransformTranslationDuringEnter() {
        val translation = resolvePostImageViewerRenderedTranslation(
            activeOffset = Offset(x = 12f, y = 24f),
            activeDismissOffsetY = 36f,
            openTransform = PostImageViewerEnterTransform(
                scaleX = 0.4f,
                scaleY = 0.4f,
                translationX = -320f,
                translationY = -950f,
            ),
        )

        assertThat(translation).isEqualTo(Offset(x = -308f, y = -890f))
    }

    @Test
    fun calculatePostImageViewerWindowDismissTransform_slidesTowardScreenEdgeByCurrentDirection() {
        val downward = calculatePostImageViewerWindowDismissTransform(
            currentScale = 0.72f,
            currentTranslation = Offset(x = 16f, y = 140f),
            viewportHeight = 2_340,
            progress = 1f,
        )
        val upward = calculatePostImageViewerWindowDismissTransform(
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
    fun postImageViewerDoubleTapTargetOffset_keepsCenterTapCentered() {
        val target = postImageViewerDoubleTapTargetOffset(
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
    fun postImageViewerDoubleTapTargetOffset_zoomsTowardTapPointAndClampsToBounds() {
        val target = postImageViewerDoubleTapTargetOffset(
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
    fun calculatePostImageViewerPinchTransform_scalesAroundInitialPinchCenter() {
        val transform = calculatePostImageViewerPinchTransform(
            pinchStartDistance = 100f,
            currentDistance = 200f,
            pinchStartScale = 1f,
            pinchStartOffset = Offset.Zero,
            pinchCenter = Offset(x = 700f, y = 300f),
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(transform.scale).isEqualTo(2f)
        assertThat(transform.offset).isEqualTo(Offset(x = -200f, y = 100f))
    }

    @Test
    fun calculatePostImageViewerPinchTransform_keepsTranslationWhenTwoFingersOnlyRecenter() {
        val transform = calculatePostImageViewerPinchTransform(
            pinchStartDistance = 180f,
            currentDistance = 180f,
            pinchStartScale = 2.2f,
            pinchStartOffset = Offset(x = 120f, y = -80f),
            pinchCenter = Offset(x = 640f, y = 520f),
            containerSize = IntSize(width = 1_000, height = 800),
            contentSize = IntSize(width = 800, height = 600),
        )

        assertThat(transform.scale).isEqualTo(2.2f)
        assertThat(transform.offset).isEqualTo(Offset(x = 120f, y = -80f))
    }

    @Test
    fun isPostImageViewerTapInsideContent_returnsTrueOnlyInsideCenteredImageBounds() {
        assertThat(
            isPostImageViewerTapInsideContent(
                tapOffset = Offset(x = 500f, y = 400f),
                containerSize = IntSize(width = 1_000, height = 800),
                contentSize = IntSize(width = 800, height = 600),
            ),
        ).isTrue()

        assertThat(
            isPostImageViewerTapInsideContent(
                tapOffset = Offset(x = 50f, y = 50f),
                containerSize = IntSize(width = 1_000, height = 800),
                contentSize = IntSize(width = 800, height = 600),
            ),
        ).isFalse()
    }

    @Test
    fun buildPostImageViewerRequestHeaders_includesBrowserHeadersAndCookie() {
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

        val headers = buildPostImageViewerRequestHeaders(
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
    fun suggestPostImageDownloadFileName_usesLastPathSegment() {
        assertThat(
            suggestPostImageDownloadFileName(
                "https://linux.do/uploads/default/original/1X/pretty-image.webp?download=1",
            ),
        ).isEqualTo("pretty-image.webp")
    }

    @Test
    fun calculatePostImageViewerPopupOffset_alignsPopupToAnchorRightEdge() {
        val offset = calculatePostImageViewerPopupOffset(
            anchorBounds = IntRect(left = 930, top = 40, right = 974, bottom = 84),
            windowSize = IntSize(width = 1_080, height = 2_340),
            popupSize = IntSize(width = 204, height = 156),
            margin = 12,
            spacing = 8,
        )

        assertThat(offset).isEqualTo(IntOffset(x = 770, y = 92))
    }

    @Test
    fun calculatePostImageViewerPopupOffset_clampsInsideWindowBounds() {
        val offset = calculatePostImageViewerPopupOffset(
            anchorBounds = IntRect(left = 18, top = 40, right = 62, bottom = 84),
            windowSize = IntSize(width = 240, height = 180),
            popupSize = IntSize(width = 204, height = 156),
            margin = 12,
            spacing = 8,
        )

        assertThat(offset).isEqualTo(IntOffset(x = 12, y = 12))
    }

    @Test
    fun postImageViewerPopupOpenDuration_matchesTelegramBaseAndPerItemStep() {
        assertThat(postImageViewerPopupOpenDuration(itemCount = 0)).isEqualTo(150)
        assertThat(postImageViewerPopupOpenDuration(itemCount = 3)).isEqualTo(198)
    }

    @Test
    fun postImageViewerPopupCascadeProgress_matchesTelegramFormulaWithoutCascadeForShortMenu() {
        val first = postImageViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 0f,
            count = 3f,
            waveLength = 4f,
        )
        val second = postImageViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 1f,
            count = 3f,
            waveLength = 4f,
        )
        val third = postImageViewerPopupCascadeProgress(
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
    fun postImageViewerPopupCascadeProgress_staggersWhenMenuHasMoreThanWaveLengthItems() {
        val first = postImageViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 0f,
            count = 6f,
            waveLength = 4f,
        )
        val last = postImageViewerPopupCascadeProgress(
            fullAnimationT = 0.5f,
            position = 5f,
            count = 6f,
            waveLength = 4f,
        )

        assertThat(first).isGreaterThan(last)
        assertThat(last).isAtLeast(0f)
    }
}
