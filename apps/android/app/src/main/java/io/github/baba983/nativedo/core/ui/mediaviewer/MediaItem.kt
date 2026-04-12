package io.github.baba983.nativedo.core.ui.mediaviewer

import io.github.baba983.nativedo.core.ui.PostBodyImage

sealed interface MediaItem {
    val cacheKey: String

    data class Image(
        val rawUrl: String,
        val originalUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val title: String? = null,
        val subtitle: String? = null,
    ) : MediaItem {
        override val cacheKey: String get() = originalUrl ?: rawUrl
    }

    data class Video(
        val url: String,
        val thumbnailUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) : MediaItem {
        override val cacheKey: String get() = url
    }
}

fun PostBodyImage.toMediaItem(
    title: String? = null,
    subtitle: String? = null,
): MediaItem.Image = MediaItem.Image(
    rawUrl = rawUrl,
    originalUrl = originalUrl,
    width = width,
    height = height,
    title = title,
    subtitle = subtitle,
)

fun MediaItem.Image.toPostBodyImage(): PostBodyImage = PostBodyImage(
    rawUrl = rawUrl,
    originalUrl = originalUrl,
    width = width,
    height = height,
)
