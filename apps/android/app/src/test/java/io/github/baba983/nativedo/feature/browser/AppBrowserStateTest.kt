package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import com.google.common.truth.Truth.assertThat
import android.Manifest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderBlock
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
import io.github.baba983.nativedo.core.ui.TextDocument
import io.github.baba983.nativedo.core.ui.TextEntity
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBrowserStateTest {
    @Test
    fun appBrowserChromeScene_returnsBrowseWhenNoTransientStateIsActive() {
        val scene = resolveAppBrowserChromeScene(
            isSearching = false,
            isAddressEditing = false,
            isHistoryPopupVisible = false,
            isOverflowMenuVisible = false,
            isInSubPanel = false,
        )

        assertThat(scene).isEqualTo(AppBrowserChromeScene.Browse)
    }

    @Test
    fun appBrowserChromeScene_prioritizesAddressEditingOverOtherScenes() {
        val scene = resolveAppBrowserChromeScene(
            isSearching = true,
            isAddressEditing = true,
            isHistoryPopupVisible = true,
            isOverflowMenuVisible = true,
            isInSubPanel = true,
        )

        assertThat(scene).isEqualTo(AppBrowserChromeScene.AddressEditing)
    }

    @Test
    fun appBrowserChromeScene_reportsOverflowMenuWhenOnlyMenuIsExpanded() {
        val scene = resolveAppBrowserChromeScene(
            isSearching = false,
            isAddressEditing = false,
            isHistoryPopupVisible = false,
            isOverflowMenuVisible = true,
            isInSubPanel = false,
        )

        assertThat(scene).isEqualTo(AppBrowserChromeScene.OverflowMenu)
    }

    @Test
    fun appBrowserRoutePresentationState_clampsOffsetAndDerivesProgressAlphaAndBackdrop() {
        val state = buildAppBrowserRoutePresentationState(
            offsetPx = 120f,
            screenWidthPx = 400f,
        )

        assertThat(state.progress).isWithin(0.0001f).of(0.7f)
        assertThat(state.translationX).isWithin(0.0001f).of(120f)
        assertThat(state.alpha).isWithin(0.0001f).of(0.952f)
        assertThat(state.backdropAlpha).isWithin(0.0001f).of(0.096f)
    }

    @Test
    fun appBrowserRoutePresentationState_clampsOverflowOffsetToClosedState() {
        val state = buildAppBrowserRoutePresentationState(
            offsetPx = 999f,
            screenWidthPx = 400f,
        )

        assertThat(state.progress).isEqualTo(0f)
        assertThat(state.translationX).isWithin(0.0001f).of(400f)
        assertThat(state.alpha).isWithin(0.0001f).of(0.84f)
        assertThat(state.backdropAlpha).isWithin(0.0001f).of(0.32f)
    }

    @Test
    fun appBrowserTopBarPresentationState_keepsTopBarVisibleWhileAddingTelegramShift() {
        val state = buildAppBrowserTopBarPresentationState(
            offsetPx = 120f,
            screenWidthPx = 400f,
            maxTranslationPx = 56f,
        )

        assertThat(state.alpha).isWithin(0.0001f).of(0.97f)
        assertThat(state.translationX).isWithin(0.0001f).of(16.8f)
    }

    @Test
    fun appBrowserTopBarPresentationState_clampsClosedOffsetToMaxShift() {
        val state = buildAppBrowserTopBarPresentationState(
            offsetPx = 999f,
            screenWidthPx = 400f,
            maxTranslationPx = 56f,
        )

        assertThat(state.alpha).isWithin(0.0001f).of(0.90f)
        assertThat(state.translationX).isWithin(0.0001f).of(56f)
    }

    @Test
    fun appBrowserSheetPresentationState_derivesOpenedFullscreenAndScrimProgress() {
        val state = buildAppBrowserSheetPresentationState(
            topOffsetPx = 80f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(state.openedProgress).isEqualTo(1f)
        assertThat(state.fullscreenProgress).isWithin(0.0001f).of(0.5f)
        assertThat(state.scrimAlpha).isWithin(0.0001f).of(0.30f)
    }

    @Test
    fun appBrowserSheetPresentationState_isFullyOpenWhenTopOffsetIsZero() {
        val state = buildAppBrowserSheetPresentationState(
            topOffsetPx = 0f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(state.openedProgress).isEqualTo(1f)
        assertThat(state.fullscreenProgress).isEqualTo(1f)
    }

    @Test
    fun appBrowserSheetPresentationState_clampsClosedState() {
        val state = buildAppBrowserSheetPresentationState(
            topOffsetPx = 999f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(state.openedProgress).isEqualTo(0f)
        assertThat(state.fullscreenProgress).isEqualTo(0f)
        assertThat(state.topOffsetPx).isEqualTo(800f)
        assertThat(state.scrimAlpha).isEqualTo(0f)
    }

    @Test
    fun appBrowserSheetSettle_dismissesWhenDraggedFarEnoughDown() {
        val settle = buildAppBrowserSheetSettle(
            currentTopOffsetPx = 440f,
            velocityPxPerSecond = 120f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Closed)
        assertThat(settle.durationMillis).isAtLeast(220)
    }

    @Test
    fun appBrowserSheetSettle_prefersDismissForStrongDownwardVelocity() {
        val settle = buildAppBrowserSheetSettle(
            currentTopOffsetPx = 220f,
            velocityPxPerSecond = 1200f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Closed)
    }

    @Test
    fun appBrowserSheetSettle_promotesToFullscreenOnFastUpwardDrag() {
        val settle = buildAppBrowserSheetSettle(
            currentTopOffsetPx = 120f,
            velocityPxPerSecond = -1200f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Fullscreen)
    }

    @Test
    fun appBrowserSheetSettle_keepsFullscreenWhenReleaseIsNearTop() {
        val settle = buildAppBrowserSheetSettle(
            currentTopOffsetPx = 40f,
            velocityPxPerSecond = 0f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Fullscreen)
    }

    @Test
    fun appBrowserSheetSettle_restoresFullscreenByDefault() {
        val settle = buildAppBrowserSheetSettle(
            currentTopOffsetPx = 220f,
            velocityPxPerSecond = 100f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Fullscreen)
    }

    @Test
    fun appBrowserSwipeSettle_decidesDismissWhenThresholdExceeded() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 180f,
            velocityPxPerSecond = 100f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isTrue()
        assertThat(settle.targetOffsetPx).isEqualTo(400f)
        assertThat(settle.durationMillis).isEqualTo(250)
    }

    @Test
    fun appBrowserSwipeSettle_decidesDismissWhenVelocityExceeded() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 80f,
            velocityPxPerSecond = 900f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isTrue()
        assertThat(settle.targetOffsetPx).isEqualTo(400f)
        assertThat(settle.durationMillis).isEqualTo(336)
    }

    @Test
    fun appBrowserSwipeSettle_snapsBackWhenBelowThresholdAndVelocity() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 80f,
            velocityPxPerSecond = 100f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isFalse()
        assertThat(settle.targetOffsetPx).isEqualTo(0f)
        assertThat(settle.durationMillis).isEqualTo(250)
    }

    @Test
    fun appBrowserLoadProgressState_start_makesIndicatorVisibleWithHeadStart() {
        val state = AppBrowserLoadProgressState().start()

        assertThat(state.displayedProgress).isEqualTo(0.08f)
        assertThat(state.actualProgress).isEqualTo(0f)
        assertThat(state.isLoading).isTrue()
        assertThat(state.isVisible).isTrue()
    }

    @Test
    fun appBrowserLoadProgressState_onActualProgressChanged_neverRegressesAndAllowsJumpAhead() {
        val state = AppBrowserLoadProgressState(
            displayedProgress = 0.24f,
            actualProgress = 0.24f,
            isLoading = true,
            isVisible = true,
        )

        val nonRegressed = state.onActualProgressChanged(0.12f)
        val jumpedAhead = nonRegressed.onActualProgressChanged(0.84f)

        assertThat(nonRegressed.displayedProgress).isEqualTo(state.displayedProgress)
        assertThat(jumpedAhead.displayedProgress).isEqualTo(0.84f)
        assertThat(jumpedAhead.actualProgress).isEqualTo(0.84f)
    }

    @Test
    fun appBrowserLoadProgressState_finish_keepsFullProgressUntilDismissed() {
        val state = AppBrowserLoadProgressState()
            .start()
            .onActualProgressChanged(0.86f)
            .finish()
        val dismissed = state.dismiss()

        assertThat(state.displayedProgress).isEqualTo(1f)
        assertThat(state.actualProgress).isEqualTo(1f)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isVisible).isTrue()
        assertThat(dismissed.displayedProgress).isEqualTo(0f)
        assertThat(dismissed.actualProgress).isEqualTo(0f)
        assertThat(dismissed.isVisible).isFalse()
    }

    @Test
    fun appBrowserWebPermissionRequestData_returnsNullWhenNoSupportedResourcesExist() {
        val request = buildAppBrowserWebPermissionRequestData(
            requestedResources = arrayOf("android.webkit.resource.PROTECTED_MEDIA_ID"),
        )

        assertThat(request).isNull()
    }

    @Test
    fun appBrowserWebPermissionRequestData_mapsSupportedResourcesToAndroidPermissions() {
        val request = buildAppBrowserWebPermissionRequestData(
            requestedResources = arrayOf(
                "android.webkit.resource.VIDEO_CAPTURE",
                "android.webkit.resource.AUDIO_CAPTURE",
                "android.webkit.resource.PROTECTED_MEDIA_ID",
            ),
        )

        requireNotNull(request)
        assertThat(request.grantableResources).containsExactly(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.AUDIO_CAPTURE",
        )
        assertThat(request.requiredAndroidPermissions).containsExactly(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }

    @Test
    fun appBrowserDownloadHeaders_includeCookieAndUserAgentWhenPresent() {
        val headers = buildAppBrowserDownloadHeaders(
            cookie = "_t=session",
            userAgent = "NativeDo/1.0",
        )

        assertThat(headers).containsExactly(
            "Cookie", "_t=session",
            "User-Agent", "NativeDo/1.0",
        )
    }

    @Test
    fun appBrowserDownloadHeaders_omitBlankValues() {
        val headers = buildAppBrowserDownloadHeaders(
            cookie = "  ",
            userAgent = null,
        )

        assertThat(headers).isEmpty()
    }

    @Test
    fun appBrowserPageLoadError_returnsNullForNonMainFrameRequests() {
        val error = buildAppBrowserPageLoadError(
            failingUrl = "https://linux.do/t/123",
            isForMainFrame = false,
            description = "host lookup failed",
        )

        assertThat(error).isNull()
    }

    @Test
    fun appBrowserPageLoadError_buildsNetworkErrorForMainFrameFailure() {
        val error = buildAppBrowserPageLoadError(
            failingUrl = "https://linux.do/t/123",
            isForMainFrame = true,
            description = "Unable to resolve host linux.do",
        )

        requireNotNull(error)
        assertThat(error.kind).isEqualTo(AppBrowserPageLoadErrorKind.Network)
        assertThat(error.url).isEqualTo("https://linux.do/t/123")
        assertThat(error.rawMessage).contains("Unable to resolve host")
        assertThat(error.statusCode).isNull()
    }

    @Test
    fun appBrowserPageLoadError_buildsHttpErrorWhenStatusCodeExists() {
        val error = buildAppBrowserPageLoadError(
            failingUrl = "https://linux.do/t/123",
            isForMainFrame = true,
            description = null,
            statusCode = 404,
            reasonPhrase = "Not Found",
        )

        requireNotNull(error)
        assertThat(error.kind).isEqualTo(AppBrowserPageLoadErrorKind.Http)
        assertThat(error.statusCode).isEqualTo(404)
        assertThat(error.reasonPhrase).isEqualTo("Not Found")
    }

    @Test
    fun appBrowserPopupUrl_prefersExplicitRequestedUrl() {
        val resolved = resolveAppBrowserPopupUrl(
            requestedUrl = "https://linux.do/latest",
            fallbackUrl = "https://linux.do/ignored",
        )

        assertThat(resolved).isEqualTo("https://linux.do/latest")
    }

    @Test
    fun appBrowserPopupUrl_fallsBackWhenRequestedUrlMissing() {
        val resolved = resolveAppBrowserPopupUrl(
            requestedUrl = null,
            fallbackUrl = "https://linux.do/latest",
        )

        assertThat(resolved).isEqualTo("https://linux.do/latest")
    }

    @Test
    fun appBrowserPopupRequest_resolvesUsingRequestedUrlBeforeFallback() {
        val request = AppBrowserPopupRequest(
            requestedUrl = "https://linux.do/latest",
            fallbackUrl = "https://linux.do/ignored",
            isDialog = false,
            isUserGesture = true,
        )

        assertThat(request.resolvedUrl).isEqualTo("https://linux.do/latest")
    }

    @Test
    fun appBrowserScrollSnapshot_reportsProgressAndDelta() {
        val snapshot = buildAppBrowserScrollSnapshot(
            scrollY = 240,
            previousScrollY = 180,
            contentHeightPx = 1_200,
            viewportHeightPx = 600,
        )

        assertThat(snapshot.scrollY).isEqualTo(240)
        assertThat(snapshot.deltaY).isEqualTo(60)
        assertThat(snapshot.progress).isEqualTo(0.4f)
    }

    @Test
    fun appBrowserHeaderCollapseState_accumulatesDeltaUntilCap() {
        val state = buildAppBrowserHeaderCollapseState(
            currentOffsetPx = 10f,
            scrollY = 220,
            deltaY = 18,
            maxCollapseOffsetPx = 24f,
            isEnabled = true,
        )

        assertThat(state.offsetPx).isEqualTo(24f)
        assertThat(state.progress).isEqualTo(1f)
    }

    @Test
    fun appBrowserHeaderCollapseState_resetsWhenDisabledOrNearTop() {
        val disabled = buildAppBrowserHeaderCollapseState(
            currentOffsetPx = 10f,
            scrollY = 220,
            deltaY = 18,
            maxCollapseOffsetPx = 24f,
            isEnabled = false,
        )
        val nearTop = buildAppBrowserHeaderCollapseState(
            currentOffsetPx = 18f,
            scrollY = 8,
            deltaY = -12,
            maxCollapseOffsetPx = 24f,
            isEnabled = true,
        )

        assertThat(disabled.offsetPx).isEqualTo(0f)
        assertThat(disabled.progress).isEqualTo(0f)
        assertThat(nearTop.offsetPx).isEqualTo(0f)
        assertThat(nearTop.progress).isEqualTo(0f)
    }

    @Test
    fun shouldApplyAppBrowserFindResult_acceptsOnlyCurrentVisibleQuery() {
        assertThat(
            shouldApplyAppBrowserFindResult(
                isSearching = true,
                visibleQuery = "compose",
                dispatchedQuery = "compose",
            ),
        ).isTrue()
        assertThat(
            shouldApplyAppBrowserFindResult(
                isSearching = true,
                visibleQuery = "compose",
                dispatchedQuery = "telegram",
            ),
        ).isFalse()
        assertThat(
            shouldApplyAppBrowserFindResult(
                isSearching = false,
                visibleQuery = "compose",
                dispatchedQuery = "compose",
            ),
        ).isFalse()
    }

    @Test
    fun appBrowserScrollProgress_zeroWhenContentFitsViewport() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 0,
            contentHeightPx = 640,
            viewportHeightPx = 640,
        )

        assertThat(progress).isEqualTo(0f)
    }

    @Test
    fun appBrowserScrollProgress_computesFractionFromScrollableRange() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 240,
            contentHeightPx = 1_200,
            viewportHeightPx = 600,
        )

        assertThat(progress).isEqualTo(0.4f)
    }

    @Test
    fun appBrowserScrollProgress_clampsToFullRange() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 999,
            contentHeightPx = 1_200,
            viewportHeightPx = 600,
        )

        assertThat(progress).isEqualTo(1f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_hidesFillWhileLoading() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 0.75f,
            isLoadingVisible = true,
        )

        assertThat(state.fillFraction).isEqualTo(0f)
        assertThat(state.fillAlpha).isEqualTo(0f)
        assertThat(state.dividerAlpha).isEqualTo(0f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_scalesFillAndShadowWithScroll() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 0.5f,
            isLoadingVisible = false,
        )

        assertThat(state.fillFraction).isEqualTo(0.5f)
        assertThat(state.fillAlpha).isWithin(0.0001f).of(0.09f)
        assertThat(state.dividerAlpha).isWithin(0.0001f).of(0.04f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_clampsOverflowProgress() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 1.4f,
            isLoadingVisible = false,
        )

        assertThat(state.fillFraction).isEqualTo(1f)
        assertThat(state.fillAlpha).isWithin(0.0001f).of(0.18f)
        assertThat(state.dividerAlpha).isWithin(0.0001f).of(0.08f)
    }

    @Test
    fun appBrowserSearchPanelState_hiddenWhenSearchModeIsClosed() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = false,
            query = "linux",
            activeMatchOrdinal = 0,
            numberOfMatches = 4,
            isCounting = false,
        )

        assertThat(state.visible).isEqualTo(false)
    }

    @Test
    fun appBrowserSearchPanelState_hiddenWhenQueryIsBlank() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = " ",
            activeMatchOrdinal = 0,
            numberOfMatches = 4,
            isCounting = false,
        )

        assertThat(state.visible).isEqualTo(false)
    }

    @Test
    fun appBrowserSearchPanelState_showsNoResultsWhenNothingMatches() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "linux",
            activeMatchOrdinal = 0,
            numberOfMatches = 0,
            isCounting = false,
        )

        assertThat(state.visible).isEqualTo(true)
        assertThat(state.matchText).isEmpty()
        assertThat(state.canNavigate).isEqualTo(false)
        assertThat(state.showsNoResults).isTrue()
    }

    @Test
    fun appBrowserSearchPanelState_showsOrdinalAndEnablesNavigationWhenMatchesExist() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "linux",
            activeMatchOrdinal = 1,
            numberOfMatches = 4,
            isCounting = false,
        )

        assertThat(state.visible).isEqualTo(true)
        assertThat(state.matchText).isEqualTo("2/4")
        assertThat(state.canNavigate).isEqualTo(true)
        assertThat(state.showsNoResults).isFalse()
    }

    @Test
    fun appBrowserSearchPanelState_hidesWhenComposingTextResolvesToBlank() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "",
            activeMatchOrdinal = 0,
            numberOfMatches = 3,
            isCounting = false,
        )

        assertThat(state.visible).isEqualTo(false)
        assertThat(state.canNavigate).isEqualTo(false)
        assertThat(state.showsNoResults).isFalse()
    }

    @Test
    fun appBrowserSearchPanelState_hidesNoResultsWhileSearchIsCounting() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "linux",
            activeMatchOrdinal = 0,
            numberOfMatches = 0,
            isCounting = true,
        )

        assertThat(state.visible).isTrue()
        assertThat(state.matchText).isEmpty()
        assertThat(state.canNavigate).isFalse()
        assertThat(state.showsNoResults).isFalse()
    }

    @Test
    fun buildAppBrowserMenuEntries_telegramLayout_includesSearchAndKeepsThreeGroups() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
            isBookmarked = false,
        )

        val actions = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .map { entry -> entry.item.action }

        assertThat(actions).containsExactly(
            AppBrowserMenuAction.Forward,
            AppBrowserMenuAction.Refresh,
            AppBrowserMenuAction.AddBookmark,
            AppBrowserMenuAction.OpenInBrowser,
            AppBrowserMenuAction.Share,
            AppBrowserMenuAction.CopyLink,
            AppBrowserMenuAction.Search,
            AppBrowserMenuAction.History,
            AppBrowserMenuAction.Settings,
            AppBrowserMenuAction.Minimize,
            AppBrowserMenuAction.Close,
        ).inOrder()
        assertThat(entries.count { entry -> entry is AppBrowserMenuEntry.Divider }).isEqualTo(2)
    }

    @Test
    fun buildAppBrowserMenuEntries_includesSearchBeforeHistory() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = true,
            hasBrowserHistory = true,
            isBookmarked = false,
        )

        val actions = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .map { entry -> entry.item.action }

        assertThat(actions).containsAtLeast(
            AppBrowserMenuAction.Search,
            AppBrowserMenuAction.History,
        ).inOrder()
    }

    @Test
    fun buildAppBrowserMenuEntries_withoutBrowserHistory_disablesHistoryButKeepsEntry() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = false,
            isBookmarked = false,
        )

        val historyEntry = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .first { entry -> entry.item.action == AppBrowserMenuAction.History }

        assertThat(historyEntry.item.enabled).isFalse()
    }

    @Test
    fun buildAppBrowserMenuEntries_switchesToRemoveBookmarkWhenCurrentPageIsSaved() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
            isBookmarked = true,
        )

        val bookmarkEntry = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .first { entry ->
                entry.item.action == AppBrowserMenuAction.RemoveBookmark
            }

        assertThat(bookmarkEntry.item.enabled).isTrue()
    }

    @Test
    fun buildAppBrowserMenuEntries_includesInstantViewOnlyWhenShown() {
        val supportedEntries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
            isBookmarked = false,
            instantViewActionAvailability = AppBrowserInstantViewActionAvailability.Enabled,
        )
        val hiddenEntries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
            isBookmarked = false,
            instantViewActionAvailability = AppBrowserInstantViewActionAvailability.Hidden,
        )

        assertThat(
            supportedEntries.filterIsInstance<AppBrowserMenuEntry.Item>().map { it.item.action },
        ).contains(AppBrowserMenuAction.InstantView)
        assertThat(
            hiddenEntries.filterIsInstance<AppBrowserMenuEntry.Item>().map { it.item.action },
        ).doesNotContain(AppBrowserMenuAction.InstantView)
    }

    @Test
    fun buildAppBrowserMenuEntries_disablesInstantViewUntilContentIsReady() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
            isBookmarked = false,
            instantViewActionAvailability = AppBrowserInstantViewActionAvailability.Disabled,
        )

        val instantEntry = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .first { it.item.action == AppBrowserMenuAction.InstantView }

        assertThat(instantEntry.item.enabled).isFalse()
    }

    @Test
    fun appBrowserInstantViewLoaderState_actionAvailability_isEnabledWhenDocumentExists() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            phase = AppBrowserInstantViewPhase.Ready,
            document = AppBrowserReaderDocument(
                url = "https://example.com/article",
                title = "Example Article",
                blocks = emptyList<AppBrowserReaderBlock>(),
            ),
        )

        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Enabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_actionAvailability_isDisabledForVisiblePlaceholder() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            phase = AppBrowserInstantViewPhase.Probing,
            document = null,
        )

        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Disabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_onPageStarted_showsPlaceholder() {
        val state = AppBrowserInstantViewLoaderState().onPageStarted(
            pageUrl = "https://example.com/article",
        )

        assertThat(state.phase).isEqualTo(AppBrowserInstantViewPhase.Probing)
        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Disabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_onResolveStarted_keepsPlaceholderVisible() {
        val state = AppBrowserInstantViewLoaderState()
            .onPageStarted(pageUrl = "https://example.com/article")
            .onResolveStarted(
                pageUrl = "https://example.com/article",
                openWhenResolved = false,
            )

        assertThat(state.phase).isEqualTo(AppBrowserInstantViewPhase.Probing)
        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Disabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_onResolvedWithDocument_keepsActionVisibleAndSupported() {
        val state = AppBrowserInstantViewLoaderState()
            .onResolveStarted(
                pageUrl = "https://example.com/article",
                openWhenResolved = false,
            )
            .onResolved(
                pageUrl = "https://example.com/article",
                document = AppBrowserReaderDocument(
                    url = "https://example.com/article",
                    title = "Example Article",
                    blocks = emptyList<AppBrowserReaderBlock>(),
                ),
            )

        assertThat(state.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Enabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_onResolvedWithoutDocument_hidesAction() {
        val state = AppBrowserInstantViewLoaderState()
            .onResolveStarted(
                pageUrl = "https://example.com/article",
                openWhenResolved = false,
            )
            .onResolved(
                pageUrl = "https://example.com/article",
                document = null,
            )

        assertThat(state.phase).isEqualTo(AppBrowserInstantViewPhase.Unsupported)
        assertThat(state.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Hidden)
    }

    @Test
    fun appBrowserInstantViewLoaderState_preservesPendingOpenWhileResolving() {
        val state = AppBrowserInstantViewLoaderState()
            .onResolveStarted(
                pageUrl = "https://example.com/article",
                openWhenResolved = true,
            )
            .onResolved(
                pageUrl = "https://example.com/article",
                document = null,
            )

        assertThat(state.pendingOpenRequested).isTrue()
    }

    @Test
    fun appBrowserInstantViewLoaderState_clearPendingOpen_resetsOpenRequest() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            phase = AppBrowserInstantViewPhase.Probing,
            pendingOpenRequested = true,
        )

        assertThat(state.clearPendingOpen().pendingOpenRequested).isFalse()
    }

    @Test
    fun appBrowserInstantViewLoaderState_onPageSnapshotChanged_invalidatesResolvedDocument() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            pageProgress = 1f,
            pageLoaded = true,
            phase = AppBrowserInstantViewPhase.Ready,
            document = AppBrowserReaderDocument(
                url = "https://example.com/article",
                title = "Example Article",
                blocks = emptyList<AppBrowserReaderBlock>(),
            ),
        )

        val invalidated = state.onPageSnapshotChanged(
            pageUrl = "https://example.com/article",
            pageProgress = 0.42f,
        )

        assertThat(invalidated.pageProgress).isEqualTo(1f)
        assertThat(invalidated.pageLoaded).isTrue()
        assertThat(invalidated.document).isNotNull()
        assertThat(invalidated.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
        assertThat(invalidated.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Enabled)
    }

    @Test
    fun appBrowserInstantViewLoaderState_onLateProgressAfterPageFinished_keepsResolvedDocument() {
        val document = AppBrowserReaderDocument(
            url = "https://example.com/article",
            title = "Example Article",
            blocks = emptyList<AppBrowserReaderBlock>(),
        )
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            pageProgress = 1f,
            pageLoaded = true,
            phase = AppBrowserInstantViewPhase.Ready,
            document = document,
        )

        val updated = state.onPageSnapshotChanged(
            pageUrl = "https://example.com/article",
            pageProgress = 0.85f,
        )

        assertThat(updated.pageProgress).isEqualTo(1f)
        assertThat(updated.pageLoaded).isTrue()
        assertThat(updated.document).isEqualTo(document)
        assertThat(updated.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
    }

    @Test
    fun appBrowserInstantViewLoaderState_isStaleForSnapshot_returnsTrueWhenProgressChanges() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            pageProgress = 1f,
            pageLoaded = true,
        )

        assertThat(
            state.isStaleForSnapshot(
                pageUrl = "https://example.com/article",
                pageProgress = 0.8f,
                pageLoaded = true,
            ),
        ).isTrue()
    }

    @Test
    fun appBrowserInstantViewLoaderState_isStaleForSnapshot_returnsFalseWhenSnapshotMatches() {
        val state = AppBrowserInstantViewLoaderState(
            pageUrl = "https://example.com/article",
            pageProgress = 1f,
            pageLoaded = true,
        )

        assertThat(
            state.isStaleForSnapshot(
                pageUrl = "https://example.com/article",
                pageProgress = 1f,
                pageLoaded = true,
            ),
        ).isFalse()
    }

    @Test
    fun supportsAppBrowserInstantView_acceptsLinuxDoTopicWithSubstantialBody() {
        val supported = supportsAppBrowserInstantView(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            bodyText = """
                第一段内容已经足够长，而且会模拟帖子正文里比较连续的技术说明和上下文。

                第二段内容继续描述 Compose、WebView、动画和返回路径恢复这些实现细节。

                第三段内容继续补充容器切换、顶部栏 ownership、history 和 search 状态。

                第四段内容继续解释 Instant View 为什么需要独立 reader scene 和恢复链路。

                第五段内容继续补充 Telegram 对齐目标、菜单 gating 和正文提取判定。

                第六段内容继续保证文本长度、段落数量和行密度都满足支持规则。
            """.trimIndent(),
        )

        assertThat(supported).isTrue()
    }

    @Test
    fun supportsAppBrowserInstantView_rejectsLinuxDoNonTopicPage() {
        val supported = supportsAppBrowserInstantView(
            url = "https://linux.do/c/android/5",
            title = "Android",
            bodyText = """
                这一页即便内容很多，也不应该被误判成 Instant View。

                因为 linux.do 站内首版只允许 /t/ 主题页进入阅读器场景。

                这里继续补充多段文字，确保失败原因来自路径规则而不是正文长度不足。

                继续补充分类页、列表页、搜索页不该出现 Instant View 的约束。

                再补一段，确保足够长。

                最后一段，完成长度门槛。
            """.trimIndent(),
        )

        assertThat(supported).isFalse()
    }

    @Test
    fun supportsAppBrowserInstantView_acceptsHackerNewsDiscussionLikePage() {
        val supported = supportsAppBrowserInstantView(
            url = "https://news.ycombinator.com/item?id=47660925",
            title = "Ask HN: Example discussion",
            bodyText = """
                pg 3 hours ago | next [–]
                I used this technique on a real project and it worked surprisingly well once we simplified the data flow and removed a few unnecessary abstractions.
                another_user 2 hours ago | parent | next [–]
                The most interesting part for me is how the browser container and the reader container can share source restoration while still feeling like different scenes.
                third_user 1 hour ago | parent | next [–]
                Telegram gets this right because the article transition feels distinct from the web page itself, and the resulting reading mode is much calmer.
            """.trimIndent(),
        )

        assertThat(supported).isTrue()
    }

    @Test
    fun extractAppBrowserReaderBodyFromHtml_extractsHackerNewsComments() {
        val bodyText = extractAppBrowserReaderBodyFromHtml(
            html = """
                <html>
                  <body>
                    <table>
                      <tr class="athing">
                        <td class="title">
                          <span class="titleline"><a href="https://example.com/story">Example story</a></span>
                        </td>
                      </tr>
                    </table>
                    <tr class="comtr">
                      <td class="default">
                        <div class="comment">
                          <span class="commtext">I used this technique on a real project and it worked surprisingly well once we simplified the data flow.</span>
                        </div>
                      </td>
                    </tr>
                    <tr class="comtr">
                      <td class="default">
                        <div class="comment">
                          <span class="commtext">The most interesting part for me is how the browser container and reader container share restoration semantics.</span>
                        </div>
                      </td>
                    </tr>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://news.ycombinator.com/item?id=47660925",
        )

        assertThat(bodyText).contains("I used this technique on a real project")
        assertThat(bodyText).contains("browser container and reader container share restoration semantics")
    }

    @Test
    fun extractAppBrowserReaderBodyFromHtml_fallsBackToMainContentForGenericPage() {
        val bodyText = extractAppBrowserReaderBodyFromHtml(
            html = """
                <html>
                  <body>
                    <main>
                      <p>First paragraph about browser routing and reader scenes.</p>
                      <p>Second paragraph about preserving source restoration and long-form reading.</p>
                    </main>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/article",
        )

        assertThat(bodyText).contains("First paragraph about browser routing")
        assertThat(bodyText).contains("Second paragraph about preserving source restoration")
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_extractsStructuredBlocksForLinuxDoTopic() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <div class="topic-post">
                      <div class="cooked">
                        <h2>Compose Browser Reader</h2>
                        <p>First paragraph explains why a browser reader should render structured content instead of flattening everything into one plain string.</p>
                        <p>Second paragraph continues with source restoration, top bar ownership, and reader scene transitions.</p>
                        <blockquote>
                          <p>Quoted context should remain visually distinct inside the reader document.</p>
                        </blockquote>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://linux.do/t/compose-browser-reader/123456",
        )

        assertThat(readProperty(document, "title")).isEqualTo("Compose Browser Reader")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
            "Paragraph",
            "PullQuote",
        ).inOrder()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_extractsStructuredBlocksForGenericArticle() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <article>
                      <h1>External Reader Article</h1>
                      <p>First paragraph about readability extraction and how a generic article should still become a structured reader document.</p>
                      <ul>
                        <li>First list item keeps useful reading structure.</li>
                        <li>Second list item avoids collapsing list semantics.</li>
                      </ul>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/article",
        )

        assertThat(readProperty(document, "title")).isEqualTo("External Reader Article")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
            "ListBlock",
        ).inOrder()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_dropsLeadingHeadingWhenItDuplicatesDocumentTitle() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <head>
                    <title>Same Title</title>
                  </head>
                  <body>
                    <article>
                      <h1>Same Title</h1>
                      <p>First paragraph stays visible in the final reader document.</p>
                      <h2>Different Section</h2>
                      <p>Second paragraph belongs under the real section heading.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/same-title",
        )

        assertThat(readProperty(document, "title")).isEqualTo("Same Title")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
            "Heading",
            "Paragraph",
        ).inOrder()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_dropsLeadingHeadingWhenItDuplicatesNormalizedDocumentTitle() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <head>
                    <title>Same Title | Example</title>
                    <meta property="og:title" content="Same Title | Example" />
                  </head>
                  <body>
                    <article>
                      <h1>Same Title</h1>
                      <p>First paragraph stays visible in the final reader document even when the html title carries a site suffix.</p>
                      <h2>Different Section</h2>
                      <p>Second paragraph belongs under the real section heading.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/same-title",
        )

        assertThat(readProperty(document, "title")).isEqualTo("Same Title")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
            "Heading",
            "Paragraph",
        ).inOrder()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_preservesMixedListItemContentAndNestedList() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <article>
                      <h1>Nested List Article</h1>
                      <p>Intro paragraph keeps the article readable and long enough for extraction.</p>
                      <ul>
                        <li>
                          Parent item intro text
                          <ul>
                            <li>Nested child item</li>
                          </ul>
                        </li>
                        <li>
                          <strong>Second item label</strong>
                          continues with trailing text.
                        </li>
                      </ul>
                      <p>Closing paragraph keeps the structured article substantial for the reader pipeline.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/nested-list-article",
        )

        val blocks = readProperty(document, "blocks") as List<*>
        val listBlock = blocks.first { it?.javaClass?.simpleName == "ListBlock" }
        val items = readProperty(listBlock!!, "items") as List<*>
        assertThat(items).hasSize(2)

        val firstItemBlocks = readProperty(items[0]!!, "blocks") as List<*>
        assertThat(firstItemBlocks.map { it?.javaClass?.simpleName }).containsExactly(
            "Paragraph",
            "ListBlock",
        ).inOrder()
        assertThat(readProperty(firstItemBlocks[0]!!, "content")?.let { readProperty(it, "text") })
            .isEqualTo("Parent item intro text")

        val nestedList = firstItemBlocks[1]
        val nestedItems = readProperty(nestedList!!, "items") as List<*>
        val nestedItemBlocks = readProperty(nestedItems[0]!!, "blocks") as List<*>
        assertThat(readProperty(nestedItemBlocks[0]!!, "content")?.let { readProperty(it, "text") })
            .isEqualTo("Nested child item")

        val secondItemBlocks = readProperty(items[1]!!, "blocks") as List<*>
        assertThat(secondItemBlocks).hasSize(1)
        assertThat(readProperty(secondItemBlocks[0]!!, "content")?.let { readProperty(it, "text") })
            .isEqualTo("Second item label continues with trailing text.")

        val flattened = invokeFlattenBodyText(document)
        assertThat(flattened).contains("Parent item intro text")
        assertThat(flattened).contains("Nested child item")
        assertThat(flattened).contains("Second item label continues with trailing text.")
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_prefersPrimaryArticleContentOverSidebarNoise() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <main>
                      <aside class="sidebar">
                        <p>Sidebar promo text that should not become reader content even if it is long enough to look substantial.</p>
                      </aside>
                      <div class="layout">
                        <article class="post">
                          <h1>Primary Article</h1>
                          <p>First article paragraph explains how a stronger readability-style root selection should prefer the main story content.</p>
                          <p>Second article paragraph continues with enough detail to make the article clearly more substantial than surrounding chrome.</p>
                        </article>
                        <section class="related">
                          <p>Related links and newsletter prompts should stay outside the extracted reader document.</p>
                        </section>
                      </div>
                    </main>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/primary-article",
        )

        val bodyText = invokeFlattenBodyText(document)

        assertThat(readProperty(document, "title")).isEqualTo("Primary Article")
        assertThat(bodyText).contains("First article paragraph explains how a stronger readability-style root selection")
        assertThat(bodyText).contains("Second article paragraph continues with enough detail")
        assertThat(bodyText.contains("Sidebar promo text")).isFalse()
        assertThat(bodyText.contains("Related links and newsletter prompts")).isFalse()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_detectsReadableContentWithoutSemanticArticleTag() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <div class="page">
                      <div class="hero">
                        <p>Marketing header copy that should not dominate the extracted reader document.</p>
                      </div>
                      <div class="content article-body">
                        <div class="title-block">
                          <h1>Readable Content Root</h1>
                        </div>
                        <div class="entry">
                          <p>First body paragraph explains that some sites wrap the whole article in generic div elements instead of semantic article tags.</p>
                          <p>Second body paragraph continues with enough structure and length that a readability-style scorer should still pick this container.</p>
                          <p>Third body paragraph adds more detail so the article body is clearly denser and more substantial than surrounding chrome.</p>
                        </div>
                      </div>
                      <div class="comments">
                        <p>First comment that should stay out of the reader extraction.</p>
                        <p>Second comment that should also stay out of the reader extraction.</p>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/readable-div-layout",
        )

        val bodyText = invokeFlattenBodyText(document)

        assertThat(readProperty(document, "title")).isEqualTo("Readable Content Root")
        assertThat(bodyText).contains("First body paragraph explains that some sites wrap the whole article")
        assertThat(bodyText).contains("Second body paragraph continues with enough structure and length")
        assertThat(bodyText).contains("Third body paragraph adds more detail")
        assertThat(bodyText.contains("First comment that should stay out")).isFalse()
        assertThat(bodyText.contains("Marketing header copy")).isFalse()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_removesNoiseInsideSemanticMainRoot() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <body>
                    <main>
                      <h1>Semantic Root Article</h1>
                      <p>First article paragraph should remain in the reader output.</p>
                      <p>Second article paragraph should also remain in the reader output.</p>
                      <section class="newsletter signup">
                        <p>Newsletter signup prompt should not appear in the reader output.</p>
                      </section>
                      <div class="share-tools">
                        <p>Share on X Share on Facebook Share on Threads</p>
                      </div>
                      <div class="discussion">
                        <p><a href="https://example.com/u/alice">alice</a> Comment text that belongs to the discussion area, not the article body.</p>
                      </div>
                    </main>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/semantic-root-article",
        )

        val bodyText = invokeFlattenBodyText(document)

        assertThat(readProperty(document, "title")).isEqualTo("Semantic Root Article")
        assertThat(bodyText).contains("First article paragraph should remain")
        assertThat(bodyText).contains("Second article paragraph should also remain")
        assertThat(bodyText.contains("Newsletter signup prompt")).isFalse()
        assertThat(bodyText.contains("Share on X")).isFalse()
        assertThat(bodyText.contains("alice")).isFalse()
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_capturesReaderMetadata() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <head>
                    <title>Ignored Title</title>
                    <meta property="og:site_name" content="Example Journal" />
                    <meta property="og:description" content="This article explains why reader metadata matters." />
                    <meta name="author" content="Example Author" />
                    <meta property="article:published_time" content="2026-04-15T10:30:00Z" />
                    <meta property="og:image" content="/images/hero.jpg" />
                  </head>
                  <body>
                    <article>
                      <h1>Structured Reader Title</h1>
                      <p>First paragraph is long enough to be reader content and carries the article intro clearly.</p>
                      <p>Second paragraph continues the article so the extraction logic keeps the document readable.</p>
                      <p>Third paragraph adds enough text to satisfy the local reader heuristics without issue.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/story",
        )

        assertThat(readProperty(document, "title")).isEqualTo("Structured Reader Title")
        assertThat(readProperty(document, "sourceName")).isEqualTo("Example Journal")
        assertThat(readProperty(document, "excerpt")).isEqualTo("This article explains why reader metadata matters.")
        assertThat(readProperty(document, "byline")).isEqualTo("Example Author")
        assertThat(readProperty(document, "publishedTime")).isEqualTo("2026-04-15T10:30:00Z")
        assertThat(readProperty(document, "heroImageUrl")).isEqualTo("https://example.com/images/hero.jpg")
    }

    @Test
    fun parseAppBrowserInstantViewJsonDocument_parsesStructuredBlocks() {
        val document = invokeParseInstantViewJsonDocument(
            pageUrl = "https://example.com/instant-view",
            json = """
                {
                  "title": "Instant View Article",
                  "sourceName": "Example News",
                  "excerpt": "A concise summary from the hidden extractor.",
                  "byline": "By Example Author",
                  "publishedTime": "2026-04-15T10:30:00Z",
                  "heroImageUrl": "https://example.com/hero.jpg",
                  "blocks": [
                    { "type": "heading", "level": 1, "text": "Instant View Article" },
                    { "type": "paragraph", "text": "First paragraph extracted from the hidden WebView script." },
                    {
                      "type": "list",
                      "ordered": false,
                      "items": [
                        {
                          "blocks": [
                            { "type": "paragraph", "text": "First list item" }
                          ]
                        },
                        {
                          "blocks": [
                            { "type": "paragraph", "text": "Second list item" }
                          ]
                        }
                      ]
                    },
                    {
                      "type": "details",
                      "summary": {
                        "text": "More context"
                      },
                      "expanded": true,
                      "blocks": [
                        { "type": "paragraph", "text": "Hidden but parsed content." }
                      ]
                    },
                    {
                      "type": "table",
                      "titleContent": {
                        "text": "Example Table"
                      },
                      "rows": [
                        {
                          "cells": [
                            {
                              "content": { "text": "Name" },
                              "header": true
                            },
                            {
                              "content": { "text": "Score" },
                              "header": true,
                              "align": "end"
                            }
                          ]
                        },
                        {
                          "cells": [
                            {
                              "content": { "text": "Alice" }
                            },
                            {
                              "content": { "text": "42" },
                              "align": "end"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(readProperty(document, "title")).isEqualTo("Instant View Article")
        assertThat(readProperty(document, "sourceName")).isEqualTo("Example News")
        assertThat(readProperty(document, "excerpt")).isEqualTo("A concise summary from the hidden extractor.")
        assertThat(readProperty(document, "byline")).isEqualTo("By Example Author")
        assertThat(readProperty(document, "publishedTime")).isEqualTo("2026-04-15T10:30:00Z")
        assertThat(readProperty(document, "heroImageUrl")).isEqualTo("https://example.com/hero.jpg")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
            "ListBlock",
            "Details",
            "Table",
        ).inOrder()
        assertThat(invokeFlattenBodyText(document)).contains("First list item")
        assertThat(invokeFlattenBodyText(document)).contains("Example Table")
    }

    @Test
    fun parseAppBrowserInstantViewJsonDocument_dropsNearDuplicateLeadingHeading() {
        val document = invokeParseInstantViewJsonDocument(
            pageUrl = "https://example.com/instant-view",
            json = """
                {
                  "title": "Instant View Article | Example",
                  "blocks": [
                    { "type": "heading", "level": 1, "text": "Instant View Article" },
                    { "type": "paragraph", "text": "First paragraph extracted from the hidden WebView script." }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(readProperty(document, "title")).isEqualTo("Instant View Article")
        assertThat(readBlockTypeNames(document)).containsExactly(
            "Paragraph",
        ).inOrder()
    }

    @Test
    fun parseAppBrowserInstantViewJsonDocument_returnsNullWhenBlocksAreMissing() {
        val document = invokeParseInstantViewJsonDocumentOrNull(
            pageUrl = "https://example.com/instant-view",
            json = """{"title":"No Content","blocks":[]}""",
        )

        assertThat(document == null).isTrue()
    }

    @Test
    fun parseAppBrowserInstantViewJsonDocument_parsesRichTextEntitiesAndImageBlocks() {
        val document = invokeParseInstantViewJsonDocument(
            pageUrl = "https://example.com/instant-view",
            json = """
                {
                  "title": "Instant View Rich Article",
                  "blocks": [
                    {
                      "type": "paragraph",
                      "content": {
                        "text": "Read more here",
                        "entities": [
                          { "type": "bold", "start": 0, "end": 4 },
                          { "type": "link", "start": 10, "end": 14, "url": "/article" }
                        ]
                      }
                    },
                    {
                      "type": "image",
                      "url": "https://example.com/image.jpg",
                      "width": 1200,
                      "height": 800,
                      "alt": "Hero image"
                    },
                    {
                      "type": "figure",
                      "url": "https://example.com/figure.jpg",
                      "caption": {
                        "text": "Figure caption",
                        "entities": [
                          { "type": "italic", "start": 0, "end": 6 }
                        ]
                      },
                      "credit": {
                        "text": "Photo by Example",
                        "entities": [
                          { "type": "link", "start": 9, "end": 16, "url": "https://example.com/credit" }
                        ]
                      }
                    },
                    {
                      "type": "pullquote",
                      "content": {
                        "text": "A memorable line from the story."
                      },
                      "attribution": {
                        "text": "Example Person"
                      }
                    }
                  ]
                }
            """.trimIndent(),
        )

        val blocks = readProperty(document, "blocks") as List<*>
        val paragraph = blocks[0] as AppBrowserReaderBlock.Paragraph
        assertThat(paragraph.content.text).isEqualTo("Read more here")
        assertThat(paragraph.content.entities).containsExactly(
            TextEntity.Bold(start = 0, end = 4),
            TextEntity.Link(start = 10, end = 14, rawUrl = "/article"),
        ).inOrder()

        val image = blocks[1] as AppBrowserReaderBlock.Image
        assertThat(image.image.rawUrl).isEqualTo("https://example.com/image.jpg")
        assertThat(image.image.width).isEqualTo(1200)
        assertThat(image.image.height).isEqualTo(800)
        assertThat(image.alt).isEqualTo("Hero image")
        assertThat(image.caption).isNull()

        val figure = blocks[2] as AppBrowserReaderBlock.Image
        assertThat(figure.image.rawUrl).isEqualTo("https://example.com/figure.jpg")
        assertThat(figure.caption?.text).isEqualTo("Figure caption")
        assertThat(figure.caption?.entities).containsExactly(
            TextEntity.Italic(start = 0, end = 6),
        )
        assertThat(figure.credit?.text).isEqualTo("Photo by Example")
        assertThat(figure.credit?.entities).containsExactly(
            TextEntity.Link(start = 9, end = 16, rawUrl = "https://example.com/credit"),
        )

        val pullQuote = blocks[3] as AppBrowserReaderBlock.PullQuote
        assertThat(pullQuote.content.text).isEqualTo("A memorable line from the story.")
        assertThat(pullQuote.attribution?.text).isEqualTo("Example Person")
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_splitsFigureCaptionAndCredit_andRecognizesPullQuote() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <head>
                    <title>Reader Rich Media</title>
                  </head>
                  <body>
                    <article>
                      <h1>Reader Rich Media</h1>
                      <p>First paragraph is long enough to keep the reader extraction alive and stable for this test.</p>
                      <blockquote class="pullquote">
                        <p>A memorable line from the story.</p>
                        <cite>Example Person</cite>
                      </blockquote>
                      <figure>
                        <img src="https://example.com/figure.jpg" alt="Scene" />
                        <figcaption>
                          Figure caption
                          <span class="credit">Photo by Example</span>
                        </figcaption>
                      </figure>
                      <p>Second paragraph continues the article so fallback parsing still looks like a full article.</p>
                      <p>Third paragraph adds enough text to satisfy the local Instant View heuristics without issue.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/story",
        )

        val blocks = readProperty(document, "blocks") as List<*>
        val pullQuote = blocks.first { it is AppBrowserReaderBlock.PullQuote } as AppBrowserReaderBlock.PullQuote
        assertThat(pullQuote.content.text).isEqualTo("A memorable line from the story.")
        assertThat(pullQuote.attribution?.text).isEqualTo("Example Person")

        val figure = blocks.first { it is AppBrowserReaderBlock.Image } as AppBrowserReaderBlock.Image
        assertThat(figure.caption?.text).contains("Figure caption")
        assertThat(figure.credit?.text).isEqualTo("Photo by Example")
    }

    @Test
    fun extractAppBrowserReaderDocumentFromHtml_parsesDetailsAndTableBlocks() {
        val document = extractReaderDocument(
            html = """
                <html>
                  <head>
                    <title>Reader Structured Blocks</title>
                  </head>
                  <body>
                    <article>
                      <h1>Reader Structured Blocks</h1>
                      <p>First paragraph is long enough to keep the article extraction heuristics satisfied.</p>
                      <details open>
                        <summary>More context</summary>
                        <p>Hidden but parsed content.</p>
                      </details>
                      <table class="striped">
                        <caption>Example Table</caption>
                        <tr>
                          <th>Name</th>
                          <th align="right">Score</th>
                        </tr>
                        <tr>
                          <td>Alice</td>
                          <td align="right">42</td>
                        </tr>
                      </table>
                      <p>Second paragraph continues the article and keeps the fallback reader body substantial.</p>
                      <p>Third paragraph ensures the local Instant View heuristics still clearly pass.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://example.com/story",
        )

        val blocks = readProperty(document, "blocks") as List<*>

        val details = blocks.first { it?.javaClass?.simpleName == "Details" }
        assertThat(readProperty(details!!, "expandedByDefault")).isEqualTo(true)
        assertThat(readProperty(readProperty(details, "summary")!!, "text")).isEqualTo("More context")

        val table = blocks.first { it?.javaClass?.simpleName == "Table" }
        assertThat(readProperty(readProperty(table!!, "title")!!, "text")).isEqualTo("Example Table")
        val rows = readProperty(table, "rows") as List<*>
        assertThat(rows).hasSize(2)
    }

    @Test
    fun sanitizeAppBrowserInstantViewHtmlSnapshot_removesExecutableAndRemoteTags() {
        val sanitized = invokeSanitizeInstantViewHtmlSnapshot(
            """
                <html>
                  <head>
                    <title>Snapshot Title</title>
                    <script>window.bad = true;</script>
                    <link rel="stylesheet" href="https://example.com/site.css" />
                  </head>
                  <body>
                    <article>
                      <p>Reader content stays.</p>
                      <img src="https://example.com/image.jpg" />
                      <iframe src="https://example.com/embed"></iframe>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
        )

        val document = Jsoup.parse(sanitized)

        assertThat(document.title()).isEqualTo("Snapshot Title")
        assertThat(document.select("article p").text()).contains("Reader content stays.")
        assertThat(document.select("script").isEmpty()).isTrue()
        assertThat(document.select("link").isEmpty()).isTrue()
        assertThat(document.select("img").isEmpty()).isFalse()
        assertThat(document.select("iframe").isEmpty()).isTrue()
    }

    @Test
    fun buildAppBrowserReaderContent_normalizesBodyAndReturnsPayload() {
        val content = buildAppBrowserReaderContent(
            url = "https://example.com/compose-reader",
            title = "Compose Reader",
            sourceName = "Example Source",
            excerpt = "Reader summary",
            byline = "By NativeDo",
            publishedTime = "2026-04-15T10:30:00Z",
            heroImageUrl = "https://example.com/hero.png",
            bodyText = """
                第一段内容有很多空格。


                第二段内容继续解释 reader scene、browser scene、source restoration 和 overlay host。

                第三段内容继续说明 Instant View 需要 conservative gating，并且正文必须足够长。

                第四段内容继续写一些实现细节，保证文本长度超过规则阈值。

                第五段内容继续写状态恢复、菜单入口和 WebView 文本提取逻辑。

                第六段内容继续写 system bar、glass chrome 和 reader layout。
            """.trimIndent(),
        )

        assertThat(content).isEqualTo(
            AppBrowserReaderContent(
                url = "https://example.com/compose-reader",
                title = "Compose Reader",
                sourceName = "Example Source",
                excerpt = "Reader summary",
                byline = "By NativeDo",
                publishedTime = "2026-04-15T10:30:00Z",
                heroImageUrl = "https://example.com/hero.png",
                bodyText = """
                第一段内容有很多空格。

                第二段内容继续解释 reader scene、browser scene、source restoration 和 overlay host。

                第三段内容继续说明 Instant View 需要 conservative gating，并且正文必须足够长。

                第四段内容继续写一些实现细节，保证文本长度超过规则阈值。

                第五段内容继续写状态恢复、菜单入口和 WebView 文本提取逻辑。

                第六段内容继续写 system bar、glass chrome 和 reader layout。
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun buildAppBrowserReaderContent_fallsBackToHostWhenTitleIsBlank() {
        val content = buildAppBrowserReaderContent(
            url = "https://example.com/compose-reader",
            title = " ",
            bodyText = """
                First paragraph has enough length to qualify for reader mode on an external page.

                Second paragraph continues describing a Hacker News style discussion and why a reader mode can still be useful.

                Third paragraph adds enough textual weight so the body clearly passes the minimum threshold.

                Fourth paragraph keeps the text long enough for the local Instant View support rules.

                Fifth paragraph ensures there are enough substantial lines to satisfy the heuristic.
            """.trimIndent(),
        )

        assertThat(content?.title).isEqualTo("example.com")
    }

    @Test
    fun buildAppBrowserReaderContent_documentVariant_prefersExtractedArticleTitleAndDropsDuplicateHeading() {
        val content = buildAppBrowserReaderContent(
            document = AppBrowserReaderDocument(
                url = "https://example.com/compose-reader",
                title = "Compose Reader",
                blocks = listOf(
                    AppBrowserReaderBlock.Heading(
                        level = 1,
                        content = TextDocument(
                            text = "Compose Reader",
                            entities = emptyList(),
                        ),
                    ),
                    AppBrowserReaderBlock.Paragraph(
                        TextDocument(
                            text = """
                            First paragraph has enough length to qualify for reader mode on an external page.

                            Second paragraph continues describing why extractor titles should win over raw page titles.

                            Third paragraph adds enough textual weight so the body clearly passes the minimum threshold.

                            Fourth paragraph keeps the text long enough for the local Instant View support rules.

                            Fifth paragraph ensures there are enough substantial lines to satisfy the heuristic.
                            """.trimIndent(),
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        assertThat(content?.title).isEqualTo("Compose Reader")
        assertThat(content?.document?.blocks?.map { it::class.simpleName }).containsExactly(
            "Paragraph",
        )
    }

    @Test
    fun buildAppBrowserReaderContent_documentVariant_prefersLeadingHeadingWhenStoredTitleFallsBackToHost() {
        val content = buildAppBrowserReaderContent(
            document = AppBrowserReaderDocument(
                url = "https://example.com/compose-reader",
                title = "example.com",
                blocks = listOf(
                    AppBrowserReaderBlock.Heading(
                        level = 1,
                        content = TextDocument(
                            text = "Compose Reader",
                            entities = emptyList(),
                        ),
                    ),
                    AppBrowserReaderBlock.Paragraph(
                        TextDocument(
                            text = """
                            First paragraph has enough length to qualify for reader mode on an external page.

                            Second paragraph continues describing why the extracted heading should replace a host-only title fallback.

                            Third paragraph adds enough textual weight so the body clearly passes the minimum threshold.

                            Fourth paragraph keeps the text long enough for the local Instant View support rules.

                            Fifth paragraph ensures there are enough substantial lines to satisfy the heuristic.
                            """.trimIndent(),
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        assertThat(content?.title).isEqualTo("Compose Reader")
        assertThat(content?.document?.blocks?.map { it::class.simpleName }).containsExactly(
            "Paragraph",
        )
    }

    @Test
    fun appBrowserHistoryStore_recordVisit_keepsNewestEntryAndDedupesUrl() = runTest {
        val store = AppBrowserHistoryStore(
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://cdk.linux.do/dashboard",
            title = null,
            visitedAtMillis = 100L,
        )
        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        store.recordVisit(
            url = "https://cdk.linux.do/dashboard",
            title = "CDK Dashboard",
            visitedAtMillis = 300L,
        )
        advanceUntilIdle()

        assertThat(store.history.value.map { entry -> entry.url }).containsExactly(
            "https://cdk.linux.do/dashboard",
            "https://linux.do/t/render-debug-case/123456",
        ).inOrder()
        assertThat(store.history.value.first().title).isEqualTo("CDK Dashboard")
        assertThat(store.history.value.first().visitedAtMillis).isEqualTo(300L)
    }

    @Test
    fun appBrowserBookmarkStore_toggle_dedupesCanonicalUrlAndRemovesExistingBookmark() = runTest {
        val store = AppBrowserBookmarkStore(
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        val firstToggle = store.toggle(
            url = "https://linux.do/t/render-debug-case/123456#reply-1",
            title = "Render Debug Case",
            savedAtMillis = 100L,
        )
        advanceUntilIdle()
        val secondToggle = store.toggle(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            savedAtMillis = 200L,
        )
        advanceUntilIdle()

        assertThat(firstToggle).isTrue()
        assertThat(secondToggle).isFalse()
        assertThat(store.bookmarks.value).isEmpty()
    }

    @Test
    fun appBrowserBookmarkStore_restoresPersistedBookmarks() {
        val store = AppBrowserBookmarkStore(
            persistence = object : io.github.baba983.nativedo.core.storage.AppBrowserBookmarkStorePersistence {
                override fun load(): List<io.github.baba983.nativedo.core.storage.AppBrowserBookmarkSnapshot> {
                    return listOf(
                        io.github.baba983.nativedo.core.storage.AppBrowserBookmarkSnapshot(
                            title = "Linux.do",
                            url = "https://linux.do",
                            host = "linux.do",
                            savedAtMillis = 42L,
                        ),
                    )
                }

                override fun save(records: List<io.github.baba983.nativedo.core.storage.AppBrowserBookmarkSnapshot>) = Unit
            },
        )

        assertThat(store.bookmarks.value).hasSize(1)
        assertThat(store.bookmarks.value.single().url).isEqualTo("https://linux.do")
    }

    @Test
    fun mergeAppBrowserHistoryEntries_withoutBackStack_returnsBrowserHistoryEntries() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = emptyList(),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "CDK Dashboard",
                    url = "https://cdk.linux.do/dashboard",
                    host = "cdk.linux.do",
                    visitedAtMillis = 300L,
                ),
            ),
        )

        assertThat(merged).hasSize(1)
        assertThat(merged.first().url).isEqualTo("https://cdk.linux.do/dashboard")
        assertThat(merged.first().offset).isNull()
    }

    @Test
    fun mergeHistoryEntries_withoutBrowserHistory_returnsEmptyList() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Back stack only",
                    url = "https://linux.do/t/back-only/1",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 100L,
                ),
            ),
            browserHistoryEntries = emptyList(),
        )

        assertThat(merged).isEmpty()
    }

    @Test
    fun mergeHistoryEntries_prefersBackStackOffsetWhenUrlExistsInCurrentTab() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Topic",
                    url = "https://linux.do/t/render-debug-case/123456",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 200L,
                ),
            ),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "Topic",
                    url = "https://linux.do/t/render-debug-case/123456",
                    host = "linux.do",
                    visitedAtMillis = 200L,
                ),
            ),
        )

        assertThat(merged.single().offset).isEqualTo(-1)
    }

    @Test
    fun mergeHistoryEntries_prefersNearestBackStackMatchWhenUrlRepeats() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Nearest",
                    url = "https://linux.do/t/repeat/1",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 300L,
                ),
                AppBrowserHistoryEntry(
                    title = "Older",
                    url = "https://linux.do/t/repeat/1",
                    offset = -3,
                    host = "linux.do",
                    visitedAtMillis = 100L,
                ),
            ),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "Repeat",
                    url = "https://linux.do/t/repeat/1",
                    host = "linux.do",
                    visitedAtMillis = 400L,
                ),
            ),
        )

        assertThat(merged.single().offset).isEqualTo(-1)
        assertThat(merged.single().title).isEqualTo("Nearest")
    }

    @Test
    fun formatBrowserDataSize_zeroBytes_showsZero() {
        assertThat(formatAppBrowserDataSize(0L)).isEqualTo("0 B")
    }

    @Test
    fun formatBrowserDataSize_kilobytes() {
        assertThat(formatAppBrowserDataSize(1_536L)).isEqualTo("1.5 KB")
    }

    @Test
    fun formatBrowserDataSize_megabytes() {
        assertThat(formatAppBrowserDataSize(2_621_440L)).isEqualTo("2.5 MB")
    }

    @Test
    fun readAppBrowserStorageUsage_countsNestedCookieFilesSeparatelyFromCache() {
        val appDataDir = createTempDir(prefix = "appData")
        val appCacheDir = createTempDir(prefix = "appCache")
        try {
            writeSizedFile(File(appDataDir, "app_webview/Default/Cookies"), 120)
            writeSizedFile(File(appDataDir, "app_webview/Default/Cache_Data"), 80)
            writeSizedFile(File(appCacheDir, "WebView/page-cache"), 40)
            writeSizedFile(File(appDataDir, "databases/webview.db"), 30)
            writeSizedFile(File(appDataDir, "databases/webviewCache.db"), 20)

            val usage = readAppBrowserStorageUsage(
                appDataDir = appDataDir,
                appCacheDir = appCacheDir,
            )

            assertThat(usage.cookiesSizeBytes).isEqualTo(120L)
            assertThat(usage.cacheSizeBytes).isEqualTo(170L)
        } finally {
            appDataDir.deleteRecursively()
            appCacheDir.deleteRecursively()
        }
    }

    @Test
    fun clearAppBrowserCacheFiles_keepsCookieFiles() {
        val appDataDir = createTempDir(prefix = "appData")
        val appCacheDir = createTempDir(prefix = "appCache")
        try {
            val cookieFile = File(appDataDir, "app_webview/Default/Cookies")
            val cacheFile = File(appDataDir, "app_webview/Default/Cache_Data")
            val sharedCacheFile = File(appCacheDir, "WebView/page-cache")
            val webviewDb = File(appDataDir, "databases/webview.db")
            writeSizedFile(cookieFile, 120)
            writeSizedFile(cacheFile, 80)
            writeSizedFile(sharedCacheFile, 40)
            writeSizedFile(webviewDb, 30)

            clearAppBrowserCacheFiles(
                appDataDir = appDataDir,
                appCacheDir = appCacheDir,
            )

            assertThat(cookieFile.exists()).isTrue()
            assertThat(cacheFile.exists()).isFalse()
            assertThat(sharedCacheFile.exists()).isFalse()
            assertThat(webviewDb.exists()).isFalse()
        } finally {
            appDataDir.deleteRecursively()
            appCacheDir.deleteRecursively()
        }
    }

    @Test
    fun historyStore_restore_keepsPersistedEntries() {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "CDK Dashboard",
                    url = "https://cdk.linux.do/dashboard",
                    host = "cdk.linux.do",
                    visitedAtMillis = 300L,
                ),
            ),
        )

        val store = AppBrowserHistoryStore(persistence = persistence)

        assertThat(store.history.value).hasSize(1)
        assertThat(store.history.value.single()).isEqualTo(
            AppBrowserHistoryRecord(
                title = "CDK Dashboard",
                url = "https://cdk.linux.do/dashboard",
                host = "cdk.linux.do",
                visitedAtMillis = 300L,
            ),
        )
    }

    @Test
    fun historyStore_clear_removesPersistedEntries() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence()
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        store.clear()
        advanceUntilIdle()

        assertThat(store.history.value).isEmpty()
        assertThat(persistence.saved).isEmpty()
    }

    @Test
    fun historyStore_clearImmediate_clearsVisibleStateBeforeBackgroundPersistenceRuns() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence()
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        advanceUntilIdle()

        store.clearImmediate()

        assertThat(store.history.value).isEmpty()
        advanceUntilIdle()
        assertThat(persistence.saved).isEmpty()
    }

    @Test
    fun historyStore_restore_keepsPersistedFaviconSnapshot() {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "Linux.do",
                    url = "https://linux.do",
                    host = "linux.do",
                    visitedAtMillis = 300L,
                    faviconBase64 = "ZmFrZS1pY29u",
                ),
            ),
        )

        val store = AppBrowserHistoryStore(persistence = persistence)

        assertThat(store.history.value.single().faviconBase64).isEqualTo("ZmFrZS1pY29u")
    }

    @Test
    fun historyStore_recordVisit_withoutNewFavicon_keepsExistingSnapshot() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "Linux.do",
                    url = "https://linux.do",
                    host = "linux.do",
                    visitedAtMillis = 100L,
                    faviconBase64 = "persisted-favicon",
                ),
            ),
        )
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do",
            title = "Linux.do Updated",
            visitedAtMillis = 200L,
            favicon = null,
        )
        advanceUntilIdle()

        assertThat(store.history.value.single().faviconBase64).isEqualTo("persisted-favicon")
        assertThat(persistence.saved.single().faviconBase64).isEqualTo("persisted-favicon")
    }

    private fun extractReaderDocument(
        html: String,
        pageUrl: String,
    ): Any {
        val method = Class.forName("io.github.baba983.nativedo.feature.browser.AppBrowserStateKt")
            .methods
            .firstOrNull { candidate ->
                candidate.name == "extractAppBrowserReaderDocumentFromHtml" &&
                    candidate.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
            }

        assertThat(method).isNotNull()
        val result = method!!.invoke(null, html, pageUrl)
        assertThat(result).isNotNull()
        return result!!
    }

    private fun readBlockTypeNames(document: Any): List<String> {
        val blocks = readProperty(document, "blocks") as? List<*> ?: return emptyList()
        return blocks.map { block -> block?.javaClass?.simpleName.orEmpty() }
    }

    private fun readProperty(
        instance: Any,
        propertyName: String,
    ): Any? {
        val getterName = "get${propertyName.replaceFirstChar(Char::uppercaseChar)}"
        val getter = instance.javaClass.methods.firstOrNull { method ->
            method.name == getterName && method.parameterCount == 0
        }
        return getter?.invoke(instance)
    }

    private fun invokeFlattenBodyText(
        document: Any,
    ): String {
        val method = Class.forName("io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocumentKt")
            .methods
            .firstOrNull { candidate ->
                candidate.name == "flattenBodyText" &&
                    candidate.parameterTypes.contentEquals(arrayOf(document.javaClass))
            }

        assertThat(method).isNotNull()
        val result = method!!.invoke(null, document)
        assertThat(result).isInstanceOf(String::class.java)
        return result as String
    }

    private fun invokeParseInstantViewJsonDocument(
        pageUrl: String,
        json: String,
    ): Any {
        val result = invokeParseInstantViewJsonDocumentOrNull(pageUrl, json)
        assertThat(result).isNotNull()
        return result!!
    }

    private fun invokeParseInstantViewJsonDocumentOrNull(
        pageUrl: String,
        json: String,
    ): Any? {
        val method = Class.forName("io.github.baba983.nativedo.feature.browser.AppBrowserStateKt")
            .methods
            .firstOrNull { candidate ->
                candidate.name == "parseAppBrowserInstantViewJsonDocument" &&
                    candidate.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
            }

        assertThat(method).isNotNull()
        return method!!.invoke(null, pageUrl, json)
    }

    private fun invokeSanitizeInstantViewHtmlSnapshot(
        html: String,
    ): String {
        val method = Class.forName("io.github.baba983.nativedo.feature.browser.AppBrowserStateKt")
            .methods
            .firstOrNull { candidate ->
                candidate.name == "sanitizeAppBrowserInstantViewHtmlSnapshot" &&
                    candidate.parameterTypes.contentEquals(arrayOf(String::class.java))
            }

        assertThat(method).isNotNull()
        val result = method!!.invoke(null, html)
        assertThat(result).isInstanceOf(String::class.java)
        return result as String
    }
}

private class InMemoryAppBrowserHistoryStorePersistence(
    records: List<AppBrowserHistorySnapshot> = emptyList(),
) : AppBrowserHistoryStorePersistence {
    var saved: List<AppBrowserHistorySnapshot> = records

    override fun load(): List<AppBrowserHistorySnapshot> = saved

    override fun save(records: List<AppBrowserHistorySnapshot>) {
        saved = records
    }
}

private fun writeSizedFile(file: File, size: Int) {
    file.parentFile?.mkdirs()
    file.writeBytes(ByteArray(size) { 1 })
}
