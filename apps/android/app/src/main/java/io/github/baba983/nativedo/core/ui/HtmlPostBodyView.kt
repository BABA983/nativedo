package io.github.baba983.nativedo.core.ui

import android.graphics.Color.TRANSPARENT
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Composable
fun HtmlPostBodyView(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { parsePostBodyBlocks(html) }
    val galleryImages = remember(blocks) { collectGalleryImages(blocks) }
    var viewerState by remember(galleryImages) { mutableStateOf<PostImageViewerState?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            RenderPostBodyBlock(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onImageClick = { clickedImage ->
                    viewerState = PostImageViewerState(
                        images = galleryImages,
                        initialIndex = findGalleryStartIndex(
                            galleryImages = galleryImages,
                            clickedImage = clickedImage,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    viewerState?.let { state ->
        if (state.images.isNotEmpty()) {
            PostImageViewerDialog(
                state = state,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onDismiss = { viewerState = null },
            )
        }
    }
}

@Composable
internal fun HtmlTextBlock(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(TRANSPARENT)
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()

            textView.setTextColor(colors.textPrimary.toArgb())
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodyStyle.fontSize.value)
            if (bodyStyle.lineHeight.value > 0f && bodyStyle.fontSize.value > 0f) {
                textView.setLineSpacing(0f, bodyStyle.lineHeight.value / bodyStyle.fontSize.value)
            }

            val inlineContent = prepareInlineContent(html)
            val appliedInlineImages = applyInlineImagePlaceholders(
                text = buildPostBodyText(
                    html = inlineContent.html,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                ),
                inlineImages = inlineContent.inlineImages,
                baseUrl = baseUrl,
                textSizePx = textView.textSize,
            )
            textView.text = appliedInlineImages.text

            val handle = InlineImageRenderHandle()
            textView.tag = handle
            appliedInlineImages.pendingImages.forEach { pendingImage ->
                handle.jobs += coroutineScope.launch {
                    val bitmap = fetchDiscourseBitmap(
                        resolvedUrl = pendingImage.resolvedUrl,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                    ) ?: return@launch

                    pendingImage.drawable.setBitmap(bitmap)
                    textView.invalidate()
                }
            }
        },
    )
}

internal class InlineImageRenderHandle {
    val jobs: MutableList<Job> = mutableListOf()

    fun cancel() {
        jobs.forEach(Job::cancel)
        jobs.clear()
    }
}

@Composable
fun PostImageView(
    image: PostBodyImage,
    cookieStore: CookieStore,
    baseUrl: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 320.dp,
    imageAlignment: Alignment = Alignment.CenterStart,
    onClick: (() -> Unit)? = null,
) {
    val colors = NativeDoThemeExtras.colors
    val resolvedUrl = remember(baseUrl, image.rawUrl) {
        resolvePostImageUrl(
            baseUrl = baseUrl,
            rawUrl = image.rawUrl,
        )
    }
    val bitmap by rememberDiscourseBitmap(
        resolvedUrl = resolvedUrl,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
    )

    val aspectRatio = remember(image.width, image.height, bitmap) {
        when {
            image.width != null && image.height != null && image.width > 0 && image.height > 0 -> {
                image.width.toFloat() / image.height.toFloat()
            }

            bitmap != null && bitmap!!.width > 0 && bitmap!!.height > 0 -> {
                bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
            }

            else -> 4f / 3f
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = imageAlignment,
    ) {
        val imageSize = remember(maxWidth, aspectRatio, maxHeight) {
            calculatePostImageSize(
                availableWidth = maxWidth,
                aspectRatio = aspectRatio,
                maxHeight = maxHeight,
            )
        }

        val imageModifier = Modifier
            .width(imageSize.width)
            .height(imageSize.height)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.glassBackground)
            .let { baseModifier ->
                if (onClick != null) {
                    baseModifier.clickable(onClick = onClick)
                } else {
                    baseModifier
                }
            }

        Box(
            modifier = imageModifier,
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
internal fun PostImageGrid(
    images: List<PostBodyImage>,
    columns: Int,
    cookieStore: CookieStore,
    baseUrl: String,
    onImageClick: (PostBodyImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedColumns = columns.coerceAtLeast(1)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.chunked(clampedColumns).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowImages.forEach { image ->
                    PostImageView(
                        image = image,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        maxHeight = 220.dp,
                        onClick = { onImageClick(image) },
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                    )
                }
                repeat(clampedColumns - rowImages.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                    )
                }
            }
        }
    }
}

internal fun calculatePostImageSize(
    availableWidth: Dp,
    aspectRatio: Float,
    maxHeight: Dp,
): DpSize {
    val resolvedWidth = when {
        availableWidth == Dp.Unspecified || availableWidth <= 0.dp -> 240.dp
        else -> availableWidth
    }
    var targetWidth = resolvedWidth
    var targetHeight = resolvedWidth / aspectRatio.coerceAtLeast(0.1f)

    if (targetHeight > maxHeight) {
        targetHeight = maxHeight
        targetWidth = maxHeight * aspectRatio.coerceAtLeast(0.1f)
    }

    return DpSize(
        width = targetWidth,
        height = targetHeight,
    )
}

internal fun resolvePostImageUrl(
    baseUrl: String,
    rawUrl: String,
): String = baseUrl.toHttpUrl().resolve(rawUrl)?.toString() ?: rawUrl
