package io.github.baba983.nativedo.ui

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val BrowserHostOverlayZIndex = 2f

internal data class BrowserReaderSceneRequest(
    val content: AppBrowserReaderContent,
    val sourceBrowserRequest: AppBrowserOpenRequest? = null,
)

internal data class BrowserHostState(
    val fullScreenRequest: AppBrowserOpenRequest? = null,
    val sheetRequest: AppBrowserOpenRequest? = null,
    val readerRequest: BrowserReaderSceneRequest? = null,
) {
    val activeTopBarOwner: NativeDoTopBarOwner?
        get() = when {
            readerRequest != null -> NativeDoTopBarOwner.Pushed
            fullScreenRequest != null -> NativeDoTopBarOwner.Browser
            sheetRequest != null -> NativeDoTopBarOwner.Browser
            else -> null
        }

    fun present(
        request: AppBrowserOpenRequest,
        target: AppBrowserPresentationTarget,
    ): BrowserHostState = when (target) {
        AppBrowserPresentationTarget.FullRoute -> copy(
            fullScreenRequest = request,
            sheetRequest = null,
            readerRequest = null,
        )
        AppBrowserPresentationTarget.Sheet -> copy(
            fullScreenRequest = null,
            sheetRequest = request,
            readerRequest = null,
        )
        AppBrowserPresentationTarget.InstantView -> copy(
            readerRequest = BrowserReaderSceneRequest(
                content = AppBrowserReaderContent(
                    url = request.url,
                    title = request.url.toHttpUrlOrNull()?.host ?: request.url,
                    bodyText = "",
                ),
                sourceBrowserRequest = sheetRequest ?: fullScreenRequest,
            ),
        )
    }

    fun presentReader(
        content: AppBrowserReaderContent,
        sourceBrowserRequest: AppBrowserOpenRequest? = sheetRequest ?: fullScreenRequest,
    ): BrowserHostState = copy(
        readerRequest = BrowserReaderSceneRequest(
            content = content,
            sourceBrowserRequest = sourceBrowserRequest,
        ),
    )

    fun dismissFullScreen(): BrowserHostState = copy(
        fullScreenRequest = null,
        readerRequest = null,
    )

    fun dismissReader(): BrowserHostState = copy(readerRequest = null)

    fun dismissAll(): BrowserHostState = copy(
        fullScreenRequest = null,
        sheetRequest = null,
        readerRequest = null,
    )
}

@Composable
internal fun BoxScope.BrowserHost(
    state: BrowserHostState,
    fullScreenContent: @Composable BoxScope.(AppBrowserOpenRequest) -> Unit = {},
    sheetContent: @Composable BoxScope.(AppBrowserOpenRequest) -> Unit = {},
    readerContent: @Composable BoxScope.(BrowserReaderSceneRequest) -> Unit = {},
) {
    state.fullScreenRequest?.let { request ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(BrowserHostOverlayZIndex),
        ) {
            fullScreenContent(request)
        }
    }

    state.sheetRequest?.let { request ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(BrowserHostOverlayZIndex),
        ) {
            sheetContent(request)
        }
    }

    state.readerRequest?.let { request ->
        Dialog(
            onDismissRequest = {},
            properties = browserReaderDialogProperties(),
        ) {
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                dialogWindow?.let { window ->
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                readerContent(request)
            }
        }
    }
}

internal fun browserReaderDialogProperties(): DialogProperties = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)
