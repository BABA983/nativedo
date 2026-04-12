package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.IntRect
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaItem
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerLaunchSource
import io.github.baba983.nativedo.core.ui.mediaviewer.toMediaItem
import java.util.IdentityHashMap

internal data class GalleryImageEntry(
    val image: PostBodyImage,
    val mediaItem: MediaItem.Image,
)

internal fun calculateMediaViewerLaunchSource(
    imageBoundsOnScreen: IntRect,
    visibleBoundsOnScreen: IntRect,
    cornerRadiusPx: Float = 0f,
): MediaViewerLaunchSource? {
    if (visibleBoundsOnScreen.width <= 0 || visibleBoundsOnScreen.height <= 0) {
        return null
    }

    return MediaViewerLaunchSource(
        boundsOnScreen = visibleBoundsOnScreen,
        fullBoundsOnScreen = imageBoundsOnScreen,
        cornerRadiusPx = cornerRadiusPx,
    )
}

internal fun collectGalleryImages(blocks: List<PostBodyBlock>): List<PostBodyImage> {
    return buildList {
        blocks.forEach(::addBlockImages)
    }
}

internal fun collectGalleryImageEntries(blocks: List<PostBodyBlock>): List<GalleryImageEntry> {
    return buildList {
        blocks.forEach(::addGalleryImageEntries)
    }
}

internal fun findGalleryStartIndex(
    galleryImages: List<PostBodyImage>,
    clickedImage: PostBodyImage,
): Int {
    val clickedKey = clickedImage.mediaViewerCacheKey()
    val index = galleryImages.indexOfFirst { image ->
        image.mediaViewerCacheKey() == clickedKey
    }
    return index.takeIf { it >= 0 } ?: 0
}

internal fun buildMediaViewerImageIndexLookup(
    images: List<PostBodyImage>,
): IdentityHashMap<PostBodyImage, Int> {
    return IdentityHashMap<PostBodyImage, Int>(images.size).apply {
        images.forEachIndexed { index, image ->
            put(image, index)
        }
    }
}

internal fun PostBodyImage.mediaViewerCacheKey(): String = originalUrl ?: rawUrl

private fun MutableList<PostBodyImage>.addBlockImages(block: PostBodyBlock) {
    when (block) {
        is PostBodyBlock.HtmlBlock -> Unit
        is PostBodyBlock.UnknownBlock -> Unit
        is PostBodyBlock.RichTextBlock -> Unit
        is PostBodyBlock.ParagraphBlock -> Unit
        is PostBodyBlock.HeadingBlock -> Unit
        is PostBodyBlock.HorizontalRuleBlock -> Unit
        is PostBodyBlock.ImageBlock -> add(block.image)
        is PostBodyBlock.LightboxBlock -> add(block.image)
        is PostBodyBlock.ImageGridBlock -> addAll(block.images)
        is PostBodyBlock.OneboxBlock -> Unit
        is PostBodyBlock.DetailsBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.CalloutBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.QuoteBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.DiscourseQuoteBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.CodeBlock -> Unit
        is PostBodyBlock.ListBlock -> {
            block.items.forEach { item ->
                item.children.forEach(::addBlockImages)
            }
        }
    }
}

private fun MutableList<GalleryImageEntry>.addGalleryImageEntries(block: PostBodyBlock) {
    when (block) {
        is PostBodyBlock.HtmlBlock -> Unit
        is PostBodyBlock.UnknownBlock -> Unit
        is PostBodyBlock.RichTextBlock -> Unit
        is PostBodyBlock.ParagraphBlock -> Unit
        is PostBodyBlock.HeadingBlock -> Unit
        is PostBodyBlock.HorizontalRuleBlock -> Unit
        is PostBodyBlock.ImageBlock -> add(
            GalleryImageEntry(
                image = block.image,
                mediaItem = block.image.toMediaItem(),
            ),
        )
        is PostBodyBlock.LightboxBlock -> add(
            GalleryImageEntry(
                image = block.image,
                mediaItem = block.image.toMediaItem(
                    title = block.filename,
                    subtitle = block.information,
                ),
            ),
        )
        is PostBodyBlock.ImageGridBlock -> addAll(
            block.images.map { image ->
                GalleryImageEntry(
                    image = image,
                    mediaItem = image.toMediaItem(),
                )
            },
        )
        is PostBodyBlock.OneboxBlock -> Unit
        is PostBodyBlock.DetailsBlock -> block.children.forEach(::addGalleryImageEntries)
        is PostBodyBlock.CalloutBlock -> block.children.forEach(::addGalleryImageEntries)
        is PostBodyBlock.QuoteBlock -> block.children.forEach(::addGalleryImageEntries)
        is PostBodyBlock.DiscourseQuoteBlock -> block.children.forEach(::addGalleryImageEntries)
        is PostBodyBlock.CodeBlock -> Unit
        is PostBodyBlock.ListBlock -> {
            block.items.forEach { item ->
                item.children.forEach(::addGalleryImageEntries)
            }
        }
    }
}
