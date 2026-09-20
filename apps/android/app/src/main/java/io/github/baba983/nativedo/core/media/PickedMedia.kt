package io.github.baba983.nativedo.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** A file the user picked: our own copy in the cache, with the name the picker reported. */
data class PickedMedia(
    val file: File,
    val name: String,
    val mimeType: String?,
)

/**
 * Copies a picker's content URI into the app cache, because the upload needs a path and a name we
 * own - the same thing the editor's own `RichEditorListView.attachExternalMedia` does for its
 * media rows. Returns null when the URI cannot be read.
 */
object PickedMediaResolver {

    fun resolve(context: Context, uri: Uri): PickedMedia? {
        val reported = displayName(context, uri)
        val name = (reported ?: uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_NAME)
            .substringAfterLast('/')
            .ifBlank { DEFAULT_NAME }
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (error: Exception) {
            null
        }
        val target = File(File(context.cacheDir, CACHE_DIR), "${System.currentTimeMillis()}-$name")
        return try {
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            PickedMedia(file = target, name = name, mimeType = mimeType ?: mediaMimeTypeFor(name))
        } catch (error: Exception) {
            target.delete()
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (error: Exception) {
        null
    }

    private const val CACHE_DIR = "reply-media"
    private const val DEFAULT_NAME = "upload"
}

/** What a file's name says it is (the picker's own mime type wins when it has one). */
internal fun mediaMimeTypeFor(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "jpg", "jpeg" -> "image/jpeg"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> null
    }
