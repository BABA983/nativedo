package io.github.baba983.nativedo.core.media

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.telegram.messenger.nativedo.RichMediaUploadBridge

/**
 * The editor's uploads, against Discourse instead of Telegram.
 *
 * `org.telegram.ui.iv.RichMediaUploader` (the pin's public surface, native here) asks
 * [RichMediaUploadBridge] for an upload and gets back a URL; this class is the `:app` end of that
 * seam: it takes the file to [DiscourseApiClient.uploadFile] (`POST /uploads.json`), resolves the
 * answer to an absolute URL, and reports progress and the outcome on the [scope]'s dispatcher - in
 * the app that is the main dispatcher, because the editor's listeners touch views directly.
 *
 * [cancel] is by file path, which is how the editor names an upload; a cancelled upload reports
 * nothing at all (neither success nor error), the same way the pin's own uploader behaves.
 */
class DiscourseMediaUploader(
    private val client: DiscourseApiClient,
    private val baseUrl: String,
    private val scope: CoroutineScope,
) : RichMediaUploadBridge.Uploader {

    private val uploads = ConcurrentHashMap<String, Job>()

    override fun upload(request: RichMediaUploadBridge.Request, callback: RichMediaUploadBridge.Callback) {
        val file = File(request.path)
        if (!file.isFile) {
            callback.onError()
            return
        }
        uploads[request.path] = scope.launch {
            val job = coroutineContext[Job]
            try {
                val uploaded = client.uploadFile(
                    file = file,
                    mimeType = mimeTypeFor(file),
                    // the request body reports from OkHttp's writer thread; the editor's listeners
                    // read and touch views, so progress goes through the scope's dispatcher
                    onProgress = { progress -> scope.launch { callback.onProgress(progress) } },
                )
                val url = absoluteUrl(uploaded.url) ?: run {
                    callback.onError()
                    return@launch
                }
                callback.onUploaded(
                    url,
                    uploaded.filename ?: file.name,
                    uploaded.width.takeIf { it > 0 } ?: request.width,
                    uploaded.height.takeIf { it > 0 } ?: request.height,
                    request.durationSec,
                )
            } catch (cancelled: CancellationException) {
                // the editor dropped the row; the site's upload, if it landed, is simply unused
                throw cancelled
            } catch (error: Exception) {
                callback.onError()
            } finally {
                uploads.remove(request.path, job)
            }
        }
    }

    override fun cancel(path: String) {
        uploads.remove(path)?.cancel()
    }

    /** The site's URL for an upload: absolute answers as they are, a path resolved against the base. */
    private fun absoluteUrl(url: String): String? {
        if (url.isBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("//")) return "https:" + url
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }

    private fun mimeTypeFor(file: File): String? = when (file.name.substringAfterLast('.', "").lowercase()) {
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
}
