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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import java.util.IdentityHashMap
import kotlin.math.roundToInt

internal fun calculatePostImageViewerLaunchSource(
    imageBoundsOnScreen: IntRect,
    visibleBoundsOnScreen: IntRect,
    cornerRadiusPx: Float = 0f,
): PostImageViewerLaunchSource? {
    if (visibleBoundsOnScreen.width <= 0 || visibleBoundsOnScreen.height <= 0) {
        return null
    }

    return PostImageViewerLaunchSource(
        boundsOnScreen = visibleBoundsOnScreen,
        fullBoundsOnScreen = imageBoundsOnScreen,
        cornerRadiusPx = cornerRadiusPx,
    )
}

internal val postBodyEmbeddedBlockCornerRadius = 10.dp
internal val postBodyEmbeddedMediaCornerRadius = 14.dp
private val postBodyImageGridSpacing = 4.dp
private val postImageThumbnailCornerRadius = postBodyEmbeddedMediaCornerRadius

@Composable
fun HtmlPostBodyView(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { parsePostBodyBlocks(html) }
    val galleryImages = remember(blocks) { collectGalleryImages(blocks) }
    val galleryImageIndexLookup = remember(galleryImages) {
        buildPostImageViewerImageIndexLookup(galleryImages)
    }
    val galleryImageSourceLookup = remember(galleryImages) {
        IdentityHashMap<PostBodyImage, PostImageViewerLaunchSource>(galleryImages.size)
    }
    var hiddenViewerImage by remember(galleryImages) { mutableStateOf<PostBodyImage?>(null) }
    var viewerState by remember(galleryImages) { mutableStateOf<PostImageViewerState?>(null) }

    Column(
        modifier = modifier,
    ) {
        blocks.forEachIndexed { index, block ->
            RenderPostBodyBlock(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenViewerImage,
                onImageSourceChanged = { image, launchSource ->
                    if (launchSource == null) {
                        galleryImageSourceLookup.remove(image)
                    } else {
                        galleryImageSourceLookup[image] = launchSource
                    }
                },
                onImageClick = { clickedImage, launchSource ->
                    viewerState = PostImageViewerState(
                        images = galleryImages,
                        initialIndex = galleryImageIndexLookup[clickedImage]
                            ?: findGalleryStartIndex(
                                galleryImages = galleryImages,
                                clickedImage = clickedImage,
                            ),
                        launchSource = launchSource ?: galleryImageSourceLookup[clickedImage],
                        imageLaunchSources = galleryImageSourceLookup,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val spacing = postBodyBlockSpacingAfter(
                current = block,
                next = blocks.getOrNull(index + 1),
            )
            if (spacing > 0.dp) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }

    viewerState?.let { state ->
        if (state.images.isNotEmpty()) {
            PostImageViewerDialog(
                state = state,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onVisibleImageChanged = { hiddenViewerImage = it },
                onOpenLink = onOpenLink,
                onDismiss = {
                    hiddenViewerImage = null
                    viewerState = null
                },
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
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val bodyStyle = postBodyParagraphTextStyle(MaterialTheme.typography.bodyLarge)
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(TRANSPARENT)
                setTextIsSelectable(false)
                includeFontPadding = false
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
                onOpenLink = onOpenLink,
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
internal fun PostImageView(
    image: PostBodyImage,
    cookieStore: CookieStore,
    baseUrl: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 320.dp,
    imageAlignment: Alignment = Alignment.CenterStart,
    clipRoundedCorners: Boolean = true,
    isHidden: Boolean = false,
    previewBitmap: android.graphics.Bitmap? = null,
    onLaunchSourceChanged: ((PostImageViewerLaunchSource?) -> Unit)? = null,
    onClick: ((PostImageViewerLaunchSource?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = NativeDoThemeExtras.colors
    val requestRawUrl = remember(image) {
        resolvePostImageRenderRawUrl(image)
    }
    val resolvedUrl = remember(baseUrl, requestRawUrl) {
        resolvePostImageUrl(
            baseUrl = baseUrl,
            rawUrl = requestRawUrl,
        )
    }
    val shouldAnimate = remember(resolvedUrl) {
        isAnimatedImageUrl(resolvedUrl)
    }
    val bitmap = if (shouldAnimate) {
        previewBitmap
    } else {
        val loadedBitmap by rememberDiscourseBitmap(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            previewBitmap = previewBitmap,
        )
        loadedBitmap
    }

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
    val density = LocalDensity.current
    val thumbnailCornerRadiusPx = remember(density, clipRoundedCorners) {
        if (clipRoundedCorners) {
            with(density) { postImageThumbnailCornerRadius.toPx() }
        } else {
            0f
        }
    }
    var launchSource by remember(image) { mutableStateOf<PostImageViewerLaunchSource?>(null) }
    LaunchedEffect(image, bitmap) {
        bitmap?.let { currentBitmap ->
            cachePostImageViewerPreviewBitmap(
                image = image,
                bitmap = currentBitmap,
            )
        }
    }
    DisposableEffect(image) {
        onDispose {
            launchSource = null
            onLaunchSourceChanged?.invoke(null)
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
            .graphicsLayer {
                alpha = if (isHidden) 0f else 1f
            }
            .onGloballyPositioned { coordinates ->
                val imagePosition = coordinates.positionOnScreen()
                val imageBoundsOnScreen = IntRect(
                    left = imagePosition.x.roundToInt(),
                    top = imagePosition.y.roundToInt(),
                    right = (imagePosition.x + coordinates.size.width).roundToInt(),
                    bottom = (imagePosition.y + coordinates.size.height).roundToInt(),
                )
                var rootCoordinates = coordinates
                while (rootCoordinates.parentLayoutCoordinates != null) {
                    rootCoordinates = rootCoordinates.parentLayoutCoordinates!!
                }
                val rootPosition = rootCoordinates.positionOnScreen()
                val clippedBoundsInRoot = rootCoordinates.localBoundingBoxOf(
                    sourceCoordinates = coordinates,
                    clipBounds = true,
                )
                val visibleBoundsOnScreen = IntRect(
                    left = (rootPosition.x + clippedBoundsInRoot.left).roundToInt(),
                    top = (rootPosition.y + clippedBoundsInRoot.top).roundToInt(),
                    right = (rootPosition.x + clippedBoundsInRoot.right).roundToInt(),
                    bottom = (rootPosition.y + clippedBoundsInRoot.bottom).roundToInt(),
                )
                val nextLaunchSource = calculatePostImageViewerLaunchSource(
                    imageBoundsOnScreen = imageBoundsOnScreen,
                    visibleBoundsOnScreen = visibleBoundsOnScreen,
                    cornerRadiusPx = thumbnailCornerRadiusPx,
                )
                if (launchSource != nextLaunchSource) {
                    launchSource = nextLaunchSource
                    onLaunchSourceChanged?.invoke(nextLaunchSource)
                }
            }
            .then(
                if (clipRoundedCorners) {
                    Modifier
                        .clip(RoundedCornerShape(postImageThumbnailCornerRadius))
                        .background(colors.glassBackground)
                } else {
                    Modifier
                },
            )
            .let { baseModifier ->
                if (onClick != null) {
                    baseModifier.clickable {
                        onClick(launchSource)
                    }
                } else {
                    baseModifier
                }
            }

        Box(
            modifier = imageModifier,
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
            } else if (bitmap == null) {
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
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)? = null,
    onImageClick: (PostBodyImage, PostImageViewerLaunchSource?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedColumns = columns.coerceAtLeast(1)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(postBodyImageGridSpacing),
    ) {
        images.chunked(clampedColumns).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(postBodyImageGridSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                rowImages.forEach { image ->
                    PostImageView(
                        image = image,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        maxHeight = 220.dp,
                        isHidden = shouldHidePostImageThumbnail(image, hiddenImage),
                        onLaunchSourceChanged = onImageSourceChanged?.let { callback ->
                            { launchSource -> callback(image, launchSource) }
                        },
                        onClick = { launchSource ->
                            onImageClick(image, launchSource)
                        },
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

internal fun shouldHidePostImageThumbnail(
    image: PostBodyImage,
    hiddenImage: PostBodyImage?,
): Boolean = image === hiddenImage
