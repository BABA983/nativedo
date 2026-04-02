package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.baba983.nativedo.core.storage.CookieStore

internal data class PostImageViewerState(
    val images: List<PostBodyImage>,
    val initialIndex: Int,
)

internal fun collectGalleryImages(blocks: List<PostBodyBlock>): List<PostBodyImage> {
    return buildList {
        blocks.forEach { block ->
            when (block) {
                is PostBodyBlock.HtmlBlock -> Unit
                is PostBodyBlock.ImageBlock -> add(block.image)
                is PostBodyBlock.ImageGridBlock -> addAll(block.images)
            }
        }
    }
}

internal fun findGalleryStartIndex(
    galleryImages: List<PostBodyImage>,
    clickedImage: PostBodyImage,
): Int {
    val clickedKey = clickedImage.galleryKey()
    val index = galleryImages.indexOfFirst { image ->
        image.galleryKey() == clickedKey
    }
    return index.takeIf { it >= 0 } ?: 0
}

@Composable
internal fun PostImageViewerDialog(
    state: PostImageViewerState,
    cookieStore: CookieStore,
    baseUrl: String,
    onDismiss: () -> Unit,
) {
    val initialPage = state.initialIndex.coerceIn(0, state.images.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) {
        state.images.size
    }
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val consumeInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = dismissInteractionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .clickable(
                            interactionSource = consumeInteractionSource,
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    PostImageView(
                        image = state.images[page].asViewerImage(),
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        imageAlignment = Alignment.Center,
                        maxHeight = maxHeight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close image viewer",
                    tint = Color.White,
                )
            }

            if (state.images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${state.images.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                )
            }
        }
    }
}

private fun PostBodyImage.galleryKey(): String = originalUrl ?: rawUrl

private fun PostBodyImage.asViewerImage(): PostBodyImage = copy(
    rawUrl = originalUrl ?: rawUrl,
)
