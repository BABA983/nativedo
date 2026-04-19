package io.github.baba983.nativedo.core.navigation

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class AppBrowserPresentationTarget {
    FullRoute,
    Sheet,
    InstantView,
}

internal enum class AppBrowserReaderSupport {
    Unsupported,
    Supported,
}

internal data class AppBrowserOpenRequest(
    val url: String,
    val preferredTarget: AppBrowserPresentationTarget? = null,
    val readerSupport: AppBrowserReaderSupport = AppBrowserReaderSupport.Unsupported,
)

internal data class AppBrowserPresentationAvailability(
    val supportsSheetBrowser: Boolean = false,
    val supportsInstantView: Boolean = false,
)

internal class AppBrowserPresentationPolicy(
    baseUrl: String,
) {
    private val baseHttpUrl = baseUrl.toHttpUrlOrNull()

    fun resolveTarget(
        request: AppBrowserOpenRequest,
        availability: AppBrowserPresentationAvailability = AppBrowserPresentationAvailability(),
    ): AppBrowserPresentationTarget {
        resolveBrowserUrl(request.url) ?: return AppBrowserPresentationTarget.Sheet

        if (
            request.preferredTarget == AppBrowserPresentationTarget.InstantView &&
            availability.supportsInstantView &&
            request.readerSupport == AppBrowserReaderSupport.Supported
        ) {
            return AppBrowserPresentationTarget.InstantView
        }

        return AppBrowserPresentationTarget.Sheet
    }

    private fun resolveBrowserUrl(url: String): HttpUrl? {
        val absoluteUrl = url.toHttpUrlOrNull()
        if (absoluteUrl != null) {
            return absoluteUrl
        }

        return baseHttpUrl?.resolve(url)
    }

}
