package io.github.baba983.nativedo.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserPresentationPolicyTest {
    private val policy = AppBrowserPresentationPolicy(baseUrl = "https://linux.do")

    @Test
    fun resolveTarget_returnsFullRouteForAuthSensitiveLinuxDoPage() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://linux.do/login"),
            availability = AppBrowserPresentationAvailability(supportsSheetBrowser = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.FullRoute)
    }

    @Test
    fun resolveTarget_returnsSheetForLightweightLinuxDoPageWhenSheetIsAvailable() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://linux.do/c/android/5"),
            availability = AppBrowserPresentationAvailability(supportsSheetBrowser = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.Sheet)
    }

    @Test
    fun resolveTarget_returnsSheetForExternalPageWhenSheetIsAvailableAndFlowIsOrdinary() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://news.ycombinator.com/item?id=47660925"),
            availability = AppBrowserPresentationAvailability(supportsSheetBrowser = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.Sheet)
    }

    @Test
    fun resolveTarget_returnsFullRouteForExternalComplexPageEvenWhenSheetIsAvailable() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://example.com/login"),
            availability = AppBrowserPresentationAvailability(supportsSheetBrowser = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.FullRoute)
    }

    @Test
    fun resolveTarget_fallsBackToFullRouteWhenSheetIsUnavailable() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://linux.do/c/android/5"),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.FullRoute)
    }

    @Test
    fun resolveTarget_returnsInstantViewOnlyForSupportedPages() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(
                url = "https://example.com/article",
                preferredTarget = AppBrowserPresentationTarget.InstantView,
                readerSupport = AppBrowserReaderSupport.Supported,
            ),
            availability = AppBrowserPresentationAvailability(supportsInstantView = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.InstantView)
    }

    @Test
    fun resolveTarget_doesNotExposeInstantViewForUnsupportedPage() {
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(
                url = "https://example.com/article",
                preferredTarget = AppBrowserPresentationTarget.InstantView,
                readerSupport = AppBrowserReaderSupport.Unsupported,
            ),
            availability = AppBrowserPresentationAvailability(supportsInstantView = true),
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.FullRoute)
    }
}
