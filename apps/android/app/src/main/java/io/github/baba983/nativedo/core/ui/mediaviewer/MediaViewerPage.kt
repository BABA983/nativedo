package io.github.baba983.nativedo.core.ui.mediaviewer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
internal fun MediaViewerPage(
    item: MediaItem,
    gestureState: MediaViewerGestureState,
    containerSize: IntSize,
    contentSize: IntSize,
    bitmap: Bitmap?,
    resolvedUrl: String?,
    shouldAnimate: Boolean,
    clipInsets: MediaViewerClipInsets? = null,
    cornerRadiusPx: Float? = null,
    enabled: Boolean = true,
    currentPage: Int = 0,
    canSwitchToPrevious: Boolean = false,
    canSwitchToNext: Boolean = false,
    pageTranslationX: Float = 0f,
    dismissDistancePx: Float,
    onPageTranslationDelta: (Float) -> Unit,
    onPageTranslationRelease: (Float, Float) -> Unit,
    onSwitchToPrevious: (MediaViewerPageSwitchSpec?) -> Unit,
    onSwitchToNext: (MediaViewerPageSwitchSpec?) -> Unit,
    onDismissRequest: () -> Unit,
    onToggleChrome: () -> Unit,
    onMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestureModifier = Modifier
        .mediaViewerGestures(
            gestureState = gestureState,
            containerSize = containerSize,
            contentSize = contentSize,
            enabled = enabled,
            currentPage = currentPage,
            canSwitchToPrevious = canSwitchToPrevious,
            canSwitchToNext = canSwitchToNext,
            pageTranslationX = pageTranslationX,
            dismissDistancePx = dismissDistancePx,
            onPageTranslationDelta = onPageTranslationDelta,
            onPageTranslationRelease = onPageTranslationRelease,
            onSwitchToPrevious = onSwitchToPrevious,
            onSwitchToNext = onSwitchToNext,
            onDismissRequest = onDismissRequest,
            onToggleChrome = onToggleChrome,
            onMenuDismiss = onMenuDismiss,
        )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is MediaItem.Image -> {
                        ViewerImageContent(
                            contentSize = contentSize,
                            bitmap = bitmap,
                            resolvedUrl = resolvedUrl,
                            shouldAnimate = shouldAnimate,
                            clipInsets = clipInsets,
                            cornerRadiusPx = cornerRadiusPx,
                        )
                    }

                    is MediaItem.Video -> {
                        Box(
                            modifier = Modifier,
                            contentAlignment = Alignment.Center,
                        ) {
                            ViewerImageContent(
                                contentSize = contentSize,
                                bitmap = bitmap,
                                resolvedUrl = resolvedUrl,
                                shouldAnimate = shouldAnimate,
                                clipInsets = clipInsets,
                                cornerRadiusPx = cornerRadiusPx,
                            )
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.48f),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ViewerImageContent(
    contentSize: IntSize,
    bitmap: Bitmap?,
    resolvedUrl: String? = null,
    shouldAnimate: Boolean = false,
    clipInsets: MediaViewerClipInsets? = null,
    cornerRadiusPx: Float? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val contentWidth = remember(contentSize, density) {
        with(density) { contentSize.width.toDp() }
    }
    val contentHeight = remember(contentSize, density) {
        with(density) { contentSize.height.toDp() }
    }

    Box(
        modifier = modifier
            .size(width = contentWidth, height = contentHeight)
            .then(
                if (clipInsets != null || (cornerRadiusPx ?: 0f) > 0f) {
                    Modifier.drawWithContent {
                        val mask = calculateMediaViewerContentMask(
                            contentWidth = size.width,
                            contentHeight = size.height,
                            clipInsets = clipInsets,
                            cornerRadiusPx = cornerRadiusPx ?: 0f,
                        )
                        if (mask != null) {
                            clipRect(
                                left = mask.visibleBounds.left,
                                top = mask.visibleBounds.top,
                                right = mask.visibleBounds.right,
                                bottom = mask.visibleBounds.bottom,
                            ) {
                                if (mask.cornerRadiusPx > 0f) {
                                    clipPath(
                                        Path().apply {
                                            addRoundRect(
                                                RoundRect(
                                                    left = mask.roundedBounds.left,
                                                    top = mask.roundedBounds.top,
                                                    right = mask.roundedBounds.right,
                                                    bottom = mask.roundedBounds.bottom,
                                                    cornerRadius = CornerRadius(
                                                        mask.cornerRadiusPx,
                                                        mask.cornerRadiusPx,
                                                    ),
                                                ),
                                            )
                                        },
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldAnimate) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (resolvedUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
