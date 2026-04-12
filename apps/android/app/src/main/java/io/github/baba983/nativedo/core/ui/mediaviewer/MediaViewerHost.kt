package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.runtime.Composable
import io.github.baba983.nativedo.core.storage.CookieStore

@Composable
internal fun MediaViewerHost(
    state: MediaViewerState,
    cookieStore: CookieStore,
    baseUrl: String,
    onVisibleItemChanged: (MediaItem?) -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    val request = state.activeRequest ?: return
    if (request.items.isEmpty()) return

    MediaViewerDialog(
        state = state,
        request = request,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onVisibleItemChanged = onVisibleItemChanged,
        onOpenLink = onOpenLink,
        onDismiss = {
            state.dismiss()
            onDismiss()
        },
    )
}
