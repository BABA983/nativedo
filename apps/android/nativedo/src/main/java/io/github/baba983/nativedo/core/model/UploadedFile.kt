package io.github.baba983.nativedo.core.model

/** An upload the site accepted: the URL its markdown points at, and what the file is. */
data class UploadedFile(
    val id: Long,
    val url: String,
    val filename: String?,
    val width: Int,
    val height: Int,
    /**
     * The site's own human size ("12.3 KB"), which its composer appends to an attachment's markdown
     * (`[name|attachment](url) (12.3 KB)`). Null when the response did not carry one.
     */
    val humanSize: String? = null,
)
