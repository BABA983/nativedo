package io.github.baba983.nativedo.core.model

/**
 * One file the site accepted (`POST /uploads.json`): the URL is what a post embeds, the
 * dimensions are what the site read from the file (0 when it did not report them).
 */
data class UploadedFile(
    val id: Long,
    val url: String,
    val filename: String?,
    val width: Int,
    val height: Int,
)
