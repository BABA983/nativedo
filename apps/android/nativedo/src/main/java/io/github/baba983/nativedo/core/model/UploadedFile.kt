package io.github.baba983.nativedo.core.model

/** An upload the site accepted: where its markdown points, and what the file is. */
data class UploadedFile(
    val id: Long,
    val url: String,
    val filename: String?,
    val width: Int,
    val height: Int,
    /**
     * The site's `upload://` handle for the file. Its own composer embeds this rather than the
     * full URL, and the cook resolves it back to the upload on the server.
     */
    val shortUrl: String? = null,
    /**
     * The site's own human size ("12.3 KB"), which its composer appends to an attachment's markdown
     * (`[name|attachment](upload://x.zip) (12.3 KB)`). Null when the response did not carry one.
     */
    val humanSize: String? = null,
)
