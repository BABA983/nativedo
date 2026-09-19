package io.github.baba983.nativedo.core.network

import java.io.File
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Buffer
import okio.source

/**
 * A file body that reports how much of it has left the device, which is the only progress a plain
 * multipart POST can offer (`writeTo` runs on OkHttp's writer thread). The callback is throttled to
 * whole percents, so a large file cannot flood the caller with events.
 */
internal class ProgressRequestBody(
    private val file: File,
    private val mimeType: String?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    private var lastReported = -1f

    override fun contentType(): MediaType? = mimeType?.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        var written = 0L
        file.source().use { source ->
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, CHUNK_BYTES)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                report(written, total)
            }
        }
        report(total, total)
    }

    private fun report(written: Long, total: Long) {
        val progress = (written.toFloat() / total).coerceIn(0f, 1f)
        if (progress - lastReported >= 0.01f || progress >= 1f) {
            lastReported = progress
            onProgress(progress)
        }
    }

    private companion object {
        const val CHUNK_BYTES = 64L * 1024L
    }
}
