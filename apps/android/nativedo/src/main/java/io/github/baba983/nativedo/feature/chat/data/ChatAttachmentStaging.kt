package io.github.baba983.nativedo.feature.chat.data

import java.io.File

/** Where one staged chat attachment stands on its way to the site. */
enum class ChatAttachmentState {
    /** A private copy exists; the site has not accepted it yet (an upload may be running). */
    Staged,

    /** The bytes are on their way to the site. */
    Uploading,

    /** The site accepted the file and answered the upload id a message references. */
    Uploaded,

    /** The site refused, or the file could not be sent; the copy is kept for the send retry. */
    Failed,
}

/**
 * One file the reader picked for the next chat message, and the upload the site accepted for it.
 *
 * <p>The file is this app's own copy: a picker's grant is handed back as soon as the copy lands, so
 * an upload reads a path this app owns rather than a provider's stream. Nothing here is a message:
 * the upload id only becomes content when [ChatAttachmentStaging] is sent with
 * `upload_ids[]`, and the site checks ownership again on that write.</p>
 */
class StagedChatAttachment internal constructor(
    val id: Long,
    val file: File,
    val name: String,
    val mimeType: String?,
) {
    var state: ChatAttachmentState = ChatAttachmentState.Staged
        private set

    /** The upload the site accepted for [file], or null while it has not accepted one. */
    var uploadId: Long? = null
        private set

    var progress: Float = 0f
        private set

    val sizeBytes: Long get() = file.length()

    /** Whether the site already accepted this file, so a send retry must not upload it again. */
    val isUploaded: Boolean get() = uploadId != null

    internal fun uploading() {
        state = ChatAttachmentState.Uploading
        progress = 0f
    }

    internal fun progressed(value: Float) {
        if (state == ChatAttachmentState.Uploading) {
            progress = value.coerceIn(0f, 1f)
        }
    }

    internal fun accepted(id: Long) {
        uploadId = id
        state = ChatAttachmentState.Uploaded
        progress = 1f
    }

    internal fun refused() {
        uploadId = null
        state = ChatAttachmentState.Failed
    }
}

/**
 * The files the reader staged for one chat message, in pick order.
 *
 * <p>Staging is not sending: a staged file is uploaded (the site's `chat-composer` kind) so the
 * message write can reference it, but no message exists until the reader presses Send. The staging
 * keeps the accepted upload ids across a failed message write, so a retry never uploads the same
 * file twice. It owns no network or screen state; the chat screen drives it and decides when a
 * staged file is spent.</p>
 */
class ChatAttachmentStaging {

    private val items = ArrayList<StagedChatAttachment>()
    private var nextId = 1L

    fun count(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun items(): List<StagedChatAttachment> = ArrayList(items)

    fun find(id: Long): StagedChatAttachment? = items.firstOrNull { it.id == id }

    /** Adds a private copy; the caller has already staged its bytes and owns the file until then. */
    fun stage(file: File, name: String, mimeType: String?): StagedChatAttachment {
        val item = StagedChatAttachment(nextId++, file, name, mimeType)
        items.add(item)
        return item
    }

    /** Drops [id] from the staging; the caller deletes its private copy. */
    fun remove(id: Long): StagedChatAttachment? {
        val item = items.firstOrNull { it.id == id } ?: return null
        items.remove(item)
        return item
    }

    /** Drops everything; the caller deletes the copies this answers. */
    fun clear(): List<StagedChatAttachment> {
        val removed = ArrayList(items)
        items.clear()
        return removed
    }

    /** The staged ids the site has not accepted yet, in stage order: what a send has to upload. */
    fun awaitingUpload(): List<Long> = items.filter { it.uploadId == null }.map { it.id }

    /** The accepted upload ids, in stage order: what a message write references. */
    fun uploadIds(): List<Long> = items.mapNotNull { it.uploadId }

    fun markUploading(id: Long) {
        find(id)?.uploading()
    }

    fun markProgress(id: Long, progress: Float) {
        find(id)?.progressed(progress)
    }

    fun markAccepted(id: Long, uploadId: Long) {
        find(id)?.accepted(uploadId)
    }

    fun markRefused(id: Long) {
        find(id)?.refused()
    }
}
