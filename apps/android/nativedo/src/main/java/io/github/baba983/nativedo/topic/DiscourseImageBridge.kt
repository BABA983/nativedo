package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.hooks.NativeDoHooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.tgnet.TLRPC
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Post images.
 *
 * Upstream's rich media draws a photo that already sits in its own file cache; it has no notion of
 * an image URL, because every photo it knows is one Telegram hosts. A linux.do post carries URLs.
 * So each image gets a stable numeric id, the cooked HTML is rewritten so upstream's importer sees
 * a media reference where the post had an image, and the bytes are put at the exact path upstream
 * will look in. Upstream then draws it with no Telegram account, datacenter or database involved.
 *
 * The download goes through the app's own HTTP client: linux.do serves some uploads to signed-in
 * users only, and that client is the one that carries the session cookies.
 */
object DiscourseImageBridge {

    /**
     * Where downloaded attachments are kept. Upstream reads a document's own `localPath` before it
     * looks anywhere in its cache hierarchy, so the bytes can live under a directory of ours.
     */
    private const val ATTACHMENT_DIR = "nativedo"

    /**
     * Below this width an image is decoration rather than content — an emoji, an avatar, a onebox
     * favicon — and it stays an inline picture in the text.
     */
    private const val MIN_MEDIA_WIDTH = 120

    /** Discourse's presentational wrappers; upstream's importer dispatches on the child tag. */
    private const val WRAPPERS = "div.md-table, div.lightbox-wrapper, a.lightbox, " +
        "div.video-container, div.video-placeholder-container"

    /**
     * Discourse's own download hint next to a lightboxed image ("name.jpg 31.1 KB"), which is not
     * part of the post.
     */
    private const val LIGHTBOX_META = "div.meta"

    /** A file a post links to, which Discourse wraps in an anchor with this class. */
    private const val ATTACHMENT = "a.attachment"

    /**
     * How large a clip this client fetches at all. A reader watches a video on purpose, so the
     * file is fetched when play is pressed - and a clip this long, or one the site refuses to
     * hand over, is streamed by the device's own player instead of copied into the cache.
     */
    private const val MAX_VIDEO_BYTES = 25L * 1024 * 1024

    /** How far a clip's transfer runs before it tells upstream's block about it: a UI hop per step. */
    private const val PROGRESS_STEP = 256L * 1024

    /** The account every message this module builds belongs to; upstream's singletons are per account. */
    private const val ACCOUNT = 0

    /** The videos the posts have carried, so a tap on one can find the file behind its id. */
    private val videos = java.util.concurrent.ConcurrentHashMap<Long, PostVideo>()

    /** Discourse's ratio on the wrapper around a video that carries no size of its own. */
    private val RATIO = Regex("--aspect-ratio:\\s*([0-9.]+)\\s*/\\s*([0-9.]+)")

    /** A video tag with no frame of its own is still a video; sixteen by nine is what a player assumes. */
    private const val DEFAULT_FRAME_WIDTH = 1280

    fun prepare(cookedHtml: String, baseUrl: String): PreparedPostBody {
        val document = Jsoup.parseBodyFragment(cookedHtml ?: "")
        val body = document.body()
        val images = ArrayList<PostImage>()
        // A video is read before the wrappers around it go: Discourse puts the frame's shape on the
        // wrapper it wraps the tag in, and unwrapping would take that with it.
        val videos = ArrayList<PostVideo>()
        for (element in body.select("video")) {
            val video = postVideo(element, baseUrl) ?: continue
            videos += video
            this.videos[video.id] = video
            // Upstream reads a media tag's `src` as the id of a file it already holds - for a
            // video, whose block then draws its cover and plays the file, as it does for a photo.
            element.attr("src", video.id.toString())
            element.removeAttr("poster")
            element.select("source").remove()
        }
        for (wrapper in body.select(WRAPPERS)) {
            wrapper.unwrap()
        }
        body.select(LIGHTBOX_META).remove()

        val photos = ArrayList<TLRPC.Photo>()
        for (image in body.select("img")) {
            val postImage = postImage(image, baseUrl) ?: continue
            images += postImage
            photos += photoOf(postImage)
            // Upstream reads the source of a media tag as the numeric key of a photo it holds, and
            // writes the same shape when its editor serialises a message.
            image.attr("src", postImage.id.toString())
            image.removeAttr("srcset")
            image.removeAttr("data-download-href")
        }
        val attachments = ArrayList<PostAttachment>()
        for (anchor in body.select(ATTACHMENT)) {
            val attachment = attachment(anchor, baseUrl) ?: continue
            attachments += attachment
            // The same shape as a photo: the importer reads `src` as the id of the file it holds,
            // and a document block draws and opens whatever that id names.
            // A figure is the one container upstream's importer turns into a document block, so the
            // element goes inside one.
            val replacement = Element(Tag.valueOf("figure"), "")
                .appendChild(Element(Tag.valueOf("document"), "").attr("src", attachment.id.toString()))
            val paragraph = anchor.parent()
            // Upstream's importer only sees block elements as direct children, and Discourse puts
            // the link in a paragraph with the size hint next to it ("file.zip (12.3 KB)"), which
            // the document block prints itself.
            if (paragraph != null && "p".equals(paragraph.tagName()) && paragraph.children().size == 1) {
                paragraph.replaceWith(replacement)
            } else {
                anchor.replaceWith(replacement)
            }
        }
        return PreparedPostBody(body.html(), photos, images, attachments, videos)
    }

    /**
     * One video a post carries.
     *
     * A site writes a video tag the way a browser reads it: the source on the tag or on a `source`
     * child, the frame shown before it plays as the tag's `poster`, and its size on the tag or as
     * a ratio on the wrapper Discourse puts around it. Upstream's importer reads a video tag the
     * same way it reads a photo tag, so all this has to produce is the id its block will name.
     */
    private fun postVideo(element: Element, baseUrl: String): PostVideo? {
        val source = element.attr("src").ifEmpty {
            element.selectFirst("source[src]")?.attr("src").orEmpty()
        }
        val url = resolve(source, baseUrl) ?: return null
        val frame = frameSize(element)
        val poster = resolve(element.attr("poster"), baseUrl)?.let { cover ->
            PostImage(
                id = idOf(cover),
                url = cover,
                width = frame.first,
                height = frame.second,
                extension = extensionOf(cover),
            )
        }
        val name = element.selectFirst("a[href]")?.text()?.trim().orEmpty()
            .ifEmpty { url.substringBefore('?').substringAfterLast('/') }
        return PostVideo(
            id = idOf(url),
            url = url,
            name = name.ifEmpty { "video.mp4" },
            width = frame.first,
            height = frame.second,
            cover = poster,
        )
    }

    /** A video tag's own frame, or the ratio the site put on the wrapper around it. */
    private fun frameSize(element: Element): Pair<Int, Int> {
        val width = element.attr("width").toIntOrNull() ?: 0
        val height = element.attr("height").toIntOrNull() ?: 0
        if (width > 0 && height > 0) {
            return width to height
        }
        val ratio = RATIO.find(element.parent()?.attr("style").orEmpty())
        val parts = ratio?.let { match ->
            (match.groupValues[1].toDoubleOrNull() ?: 0.0) to (match.groupValues[2].toDoubleOrNull() ?: 0.0)
        }
        if (parts != null && parts.first > 0.0 && parts.second > 0.0) {
            return DEFAULT_FRAME_WIDTH to (DEFAULT_FRAME_WIDTH * parts.second / parts.first).toInt()
        }
        return DEFAULT_FRAME_WIDTH to DEFAULT_FRAME_WIDTH * 9 / 16
    }

    /** The video a block names, so a reader who taps play can be given its file or its address. */
    fun videoOf(id: Long): PostVideo? = videos[id]

    /** Whether the clip a block names is already on disk, which is what decides who plays it. */
    fun videoFileExists(id: Long): Boolean {
        val video = videos[id] ?: return false
        val file = videoFile(video)
        return file.exists() && file.length() > 0
    }

    /**
     * The file upstream's video block plays.
     *
     * <p>The block itself draws the cover: the poster frame travels as the document's own
     * thumbnail, fetched to the location the loader looks in, and the clip's own bytes are only
     * needed when someone presses play on it.</p>
     */
    fun videoDocument(video: PostVideo): TLRPC.TL_document = TLRPC.TL_document().apply {
        id = video.id
        dc_id = 0
        access_hash = 0
        file_name = video.name
        mime_type = mimeOf(extensionOf(video.url))
        size = videoFile(video).takeIf { it.exists() }?.length() ?: 0L
        thumbs = if (video.cover != null) {
            arrayListOf(sizeOf(video.cover))
        } else {
            ArrayList()
        }
        localPath = videoFile(video).absolutePath
        attributes = arrayListOf(TLRPC.TL_documentAttributeVideo().apply {
            w = video.width
            h = video.height
            duration = 0.0
            supports_streaming = true
        })
    }

    /** Where a post's video is kept: the cache the attachments live in, named by its id. */
    fun videoFile(video: PostVideo): File = File(
        File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), ATTACHMENT_DIR),
        "${video.id}_${safeName(video.name)}",
    )

    /**
     * Fetches a clip's cover - the picture its block draws before anyone presses play.
     *
     * <p>The clip's own bytes do not travel with the page: a page is read for its text, and a
     * video is watched on purpose, so the file is fetched when play is pressed
     * ([downloadVideo]) and not before.</p>
     */
    suspend fun cacheVideoCover(client: OkHttpClient, video: PostVideo) {
        withContext(Dispatchers.IO) {
            cacheCover(client, video)
        }
    }

    /**
     * The name upstream's media block keys its file by.
     *
     * <p>It is asked of upstream's own function on the same document the block is handed, so a
     * progress report can never be filed under a name the block does not look up.</p>
     */
    fun videoFileName(video: PostVideo): String = FileLoader.getAttachFileName(videoDocument(video))

    /** What a clip's fetch ended as. Anything but [Stored] leaves the site's player to stream it. */
    enum class VideoDownload {
        /** The bytes are on disk, where upstream's viewer reads them. */
        Stored,

        /** The site says the clip runs longer than a page read may pull; the reader's player streams it. */
        TooLarge,

        /** The site refused the request, or the transfer broke; the reader's player streams it. */
        Failed,

        /** The reader stopped it: nothing plays, and the block goes back to its play button. */
        Cancelled,
    }

    /**
     * Fetches one clip, on a play press, telling upstream's own block about it.
     *
     * <p>Upstream's block draws a download it knows about through {@code FileLoader}; a fetch this
     * client makes is not one, so the block is told directly: it is put into its own loading state
     * before the first byte, given the transfer's progress as it arrives, and told how it ended.
     * The bytes go through the session's own client, because the site serves some uploads only to
     * a signed-in reader and that client is the one that carries the session.</p>
     *
     * <p>The block's cancel button is the same button for this fetch: the module registers how to
     * stop the transfer it runs, and a press stops the request rather than only the drawing.</p>
     */
    suspend fun downloadVideo(client: OkHttpClient, video: PostVideo): VideoDownload =
        withContext(Dispatchers.IO) {
            val file = videoFile(video)
            if (file.exists() && file.length() > 0) {
                return@withContext VideoDownload.Stored
            }
            val name = videoFileName(video)
            val call = client.newCall(Request.Builder().url(video.url).build())
            val cancelled = AtomicBoolean(false)
            NativeDoHooks.setFetchCanceller(name) {
                cancelled.set(true)
                call.cancel()
            }
            NativeDoHooks.setFileLoading(name, true)
            // The block's own state is what turns on this: it is told the fetch exists before the
            // first byte, so the reader sees a download rather than a play button that seems dead.
            reportVideoProgress(name, 0L, 0L)
            // The transfer writes a private temporary file, not the clip's own name: the bytes it
            // has written must not be a file the block could read as the clip, and the name must be
            // this fetch's own so a cancel removes only its own half-written bytes.
            val partial = AtomicFileWrite.tempFor(file)
            // The transfer's own state ends before the bytes it wrote become the file the block
            // plays, and both are this one step apart, on this thread: a reader who finds the clip
            // on disk has found it after the fetch ended, never during. The other order publishes
            // the file while the block is still told a transfer runs - a loading state, and a live
            // cancel button, over a clip that is already whole.
            val transferred = try {
                fetchVideo(call, video, partial, name, cancelled)
            } finally {
                NativeDoHooks.setFetchCanceller(name, null)
                NativeDoHooks.setFileLoading(name, false)
            }
            val outcome = if (transferred == VideoDownload.Stored) storeVideo(file, partial, name) else transferred
            // The block registered itself as an observer of that name, so upstream's own channel is
            // how it learns how the file it was waiting for ended.
            when (outcome) {
                VideoDownload.Stored -> NotificationCenter.getInstance(ACCOUNT).postNotificationNameOnUIThread(
                    NotificationCenter.fileLoaded, name, file.absolutePath,
                )
                VideoDownload.Cancelled -> NotificationCenter.getInstance(ACCOUNT).postNotificationNameOnUIThread(
                    NotificationCenter.fileLoadFailed, name, 1,
                )
                else -> NotificationCenter.getInstance(ACCOUNT).postNotificationNameOnUIThread(
                    NotificationCenter.fileLoadFailed, name, 0,
                )
            }
            outcome
        }

    /**
     * One clip, written beside [file] under a temporary name so a transfer cut short cannot leave
     * bytes the block would read as its file. A transfer that carried the whole clip stops here,
     * with those bytes still under the temporary name; making them the file is [storeVideo]'s step,
     * which runs once the transfer's own state has ended.
     *
     * <p>The site's own content length decides before a byte is written; a source that answers
     * without one is read up to the same limit, so a lying header cannot fill the disk either.</p>
     */
    private fun fetchVideo(
        call: Call,
        video: PostVideo,
        partial: File,
        name: String,
        cancelled: AtomicBoolean,
    ): VideoDownload = runCatching {
        call.execute().use responseUse@{ response ->
            if (!response.isSuccessful) {
                return@responseUse VideoDownload.Failed
            }
            val body = response.body ?: return@responseUse VideoDownload.Failed
            val total = body.contentLength()
            if (total > MAX_VIDEO_BYTES) {
                return@responseUse VideoDownload.TooLarge
            }
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var reported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        copied += read
                        if (copied > MAX_VIDEO_BYTES) {
                            return@responseUse VideoDownload.TooLarge
                        }
                        output.write(buffer, 0, read)
                        // A report is a UI-thread hop per reader, so a long clip reports in steps.
                        if (copied - reported >= PROGRESS_STEP || copied == total) {
                            reported = copied
                            reportVideoProgress(name, copied, total)
                        }
                    }
                }
            }
            VideoDownload.Stored
        }
    }.getOrElse { error ->
        if (cancelled.get()) {
            VideoDownload.Cancelled
        } else {
            FileLog.e("nativedo: fetching a post's clip failed", error)
            VideoDownload.Failed
        }
    }.also { outcome ->
        // Half a transfer is not this file: whatever arrived under the temporary name goes, whether
        // the reader stopped it or the transfer broke. Only a whole transfer is kept, for
        // [storeVideo] to move into place - and it is this fetch's own temporary file, so a
        // neighbouring fetch's bytes are never touched.
        if (outcome != VideoDownload.Stored) {
            partial.delete()
        }
    }

    /**
     * The finished transfer becomes the file the block plays, and the block is told the whole clip
     * is there. The bytes a transfer cannot move into place are not a file anyone may open, so that
     * ends as a broken fetch and the reader's own player streams the clip instead.
     */
    private fun storeVideo(file: File, partial: File, name: String): VideoDownload {
        if (!AtomicFileWrite.commit(partial, file)) {
            FileLog.e("nativedo: the clip could not be moved into place: " + partial.name)
            return VideoDownload.Failed
        }
        reportVideoProgress(name, file.length(), file.length())
        return VideoDownload.Stored
    }

    /**
     * Reports a clip's progress on upstream's own channel, under the name its block looks up: the
     * transfer then draws as the block's own radial progress, cancel icon and all.
     */
    private fun reportVideoProgress(name: String, loaded: Long, total: Long) {
        NotificationCenter.getInstance(ACCOUNT).postNotificationNameOnUIThread(
            NotificationCenter.fileLoadProgressChanged, name, loaded, total,
        )
    }

    /**
     * Lays a clip's cover down where upstream's video block looks for it.
     *
     * <p>The picture of a <em>document</em> is asked for under the document's own extension, not the
     * picture's: upstream builds that name from the file the reader would play. So the cover exists
     * under that name as well as under its own - the same bytes, a name the loader will accept, and
     * the only thing the block draws before anyone presses play.</p>
     */
    private suspend fun cacheCover(client: OkHttpClient, video: PostVideo) {
        val cover = video.cover ?: return
        val file = cache(client, cover) ?: return
        val expected = File(
            FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
            file.nameWithoutExtension + "." + extensionOf(video.url),
        )
        if (expected.exists() && expected.length() == file.length()) {
            return
        }
        runCatching {
            check(AtomicFileWrite.write(expected) { output -> file.inputStream().use { it.copyTo(output) } }) {
                "cannot move ${expected.name} into place"
            }
        }.onFailure { error -> error.printStackTrace() }
    }

    private fun attachment(anchor: Element, baseUrl: String): PostAttachment? {
        val url = resolve(anchor.attr("href"), baseUrl) ?: return null
        val name = anchor.text().trim().ifEmpty { url.substringBefore('?').substringAfterLast('/') }
        if (name.isEmpty()) {
            return null
        }
        return PostAttachment(
            id = idOf(url),
            url = url,
            name = name,
            extension = extensionOf(name),
        )
    }

    private fun postImage(image: Element, baseUrl: String): PostImage? {
        val classes = image.className().split(' ')
        if (classes.contains("emoji") || classes.contains("avatar")) {
            return null
        }
        val width = image.attr("width").toIntOrNull() ?: 0
        val height = image.attr("height").toIntOrNull() ?: 0
        if (width < MIN_MEDIA_WIDTH) {
            return null
        }
        val url = resolve(image.attr("src"), baseUrl) ?: return null
        return PostImage(
            id = idOf(url),
            url = url,
            width = width,
            height = height.coerceAtLeast(1),
            extension = extensionOf(url),
        )
    }

    /** Discourse writes both absolute and site-relative sources; the client only fetches absolute ones. */
    private fun resolve(url: String, baseUrl: String): String? {
        if (url.isEmpty()) {
            return null
        }
        return runCatching { URI(baseUrl).resolve(url).toString() }.getOrNull()
    }

    private fun extensionOf(url: String): String {
        val name = url.substringBefore('?').substringAfterLast('/')
        val extension = name.substringAfterLast('.', "")
        return if (extension.length in 2..4) extension.lowercase() else "jpg"
    }

    /**
     * The id has to survive between runs: the cached file is named after it, and the next launch
     * must find the image it already downloaded.
     */
    private fun idOf(url: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xff)
        }
        return (value and 0x7fffffffffffffffL).coerceAtLeast(1L)
    }

    /**
     * Where upstream will look for the image. `volume_id = 0` keeps `FileLoader` off its
     * file-path database: a real volume id would send every lookup through Telegram's sqlite.
     */
    private fun locationOf(image: PostImage): TLRPC.TL_fileLocationToBeDeprecated =
        TLRPC.TL_fileLocationToBeDeprecated().apply {
            volume_id = 0
            local_id = (image.id and 0x7fffffffL).toInt()
            dc_id = 0
        }

    private fun sizeOf(image: PostImage): TLRPC.TL_photoSize = TLRPC.TL_photoSize().apply {
        type = "x"
        w = image.width
        h = image.height
        size = 0
        location = locationOf(image)
    }

    /**
     * The file upstream draws and opens.
     *
     * <p>`localPath` is set even before the download, and to the path the download will use: it is
     * both what upstream's document block checks first and what keeps `FileLoader` from resolving a
     * synthetic Telegram file id through its file-path database — which is a Telegram database this
     * client never opens.</p>
     */
    fun documentOf(attachment: PostAttachment, file: File?): TLRPC.TL_document = TLRPC.TL_document().apply {
        id = attachment.id
        dc_id = 0
        access_hash = 0
        file_name = attachment.name
        mime_type = mimeOf(attachment.extension)
        size = file?.length() ?: 0L
        thumbs = ArrayList()
        localPath = attachmentFile(attachment).absolutePath
    }

    /**
     * The name comes from the post, so it is reduced to a safe file name and prefixed with the id:
     * two posts may link to files that share a name, and a crafted name must not escape the folder.
     */
    fun attachmentFile(attachment: PostAttachment): File {
        return File(
            File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), ATTACHMENT_DIR),
            "${attachment.id}_${safeName(attachment.name)}",
        )
    }

    /** A name that came from the site, reduced to something a file name may safely carry. */
    private fun safeName(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
        .take(96)
        .ifEmpty { "file" }

    /** The document as it is now: with its file when it is already on disk, without it before. */
    fun documentFor(attachment: PostAttachment): TLRPC.TL_document {
        val file = attachmentFile(attachment).takeIf { it.exists() && it.length() > 0 }
        return documentOf(attachment, file)
    }

    /**
     * Points the message's documents at the files that have arrived since it was built.
     *
     * <p>A message keeps the rich layout it was built with, so re-binding a row does not read the
     * document again; the document object itself has to learn where its file is, which is what the
     * block re-checks when it decides whether it can open it.</p>
     */
    fun applyAttachmentFiles(message: TLRPC.Message, attachments: List<PostAttachment>) {
        val documents = message.rich_message?.documents ?: return
        for (attachment in attachments) {
            val file = attachmentFile(attachment).takeIf { it.exists() && it.length() > 0 } ?: continue
            documents.firstOrNull { it.id == attachment.id }?.let { document ->
                document.size = file.length()
            }
        }
    }

    private fun mimeOf(extension: String): String = when (extension.lowercase()) {
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "gz", "tgz" -> "application/gzip"
        "txt", "log", "md" -> "text/plain"
        "json" -> "application/json"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    /** Fetches an attachment that is not on disk yet, with the same client the images use. */
    suspend fun cacheAttachment(client: OkHttpClient, attachment: PostAttachment): File? =
        withContext(Dispatchers.IO) {
            val file = attachmentFile(attachment)
            if (file.exists() && file.length() > 0) {
                return@withContext file
            }
            runCatching {
                client.newCall(Request.Builder().url(attachment.url).build()).execute().use { response ->
                    check(response.isSuccessful) { "GET ${attachment.url} answered ${response.code}" }
                    val body = response.body ?: error("GET ${attachment.url} answered no body")
                    val written = AtomicFileWrite.write(file) { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                    check(written) { "cannot move ${file.name} into place" }
                }
                file
            }.getOrElse { error ->
                error.printStackTrace()
                null
            }
        }

    fun photoOf(image: PostImage): TLRPC.TL_photo = TLRPC.TL_photo().apply {
        id = image.id
        dc_id = 0
        access_hash = 0
        file_reference = ByteArray(0)
        sizes = arrayListOf(sizeOf(image))
    }

    /**
     * Where upstream looks for the image. An `ImageReceiver` asks for cache type 0, which is the
     * images directory, while the loader that misses it there asks for cache type 1, which is the
     * cache directory - on a device those are two directories, and a copy in only one of them is a
     * copy the cell cannot draw (it reads as a gray block while `FileLoader` reports a download
     * failure against a file it has no datacenter for). Under the test host they are the same one.
     */
    fun localFiles(image: PostImage): List<File> {
        val size = sizeOf(image)
        val loader = FileLoader.getInstance(0)
        return listOf(loader.getPathToAttach(size, false), loader.getPathToAttach(size, true)).distinct()
    }

    /**
     * Downloads the image when it is not cached yet, to both places upstream looks. The writes go
     * through a temporary name so a transfer cut short cannot leave a file that the cell would
     * happily "find".
     */
    suspend fun cache(client: OkHttpClient, image: PostImage): File? = withContext(Dispatchers.IO) {
        val targets = localFiles(image)
        if (targets.all { it.exists() && it.length() > 0 }) {
            return@withContext targets.first()
        }
        runCatching {
            client.newCall(Request.Builder().url(image.url).build()).execute().use { response ->
                check(response.isSuccessful) { "GET ${image.url} answered ${response.code}" }
                val body = response.body ?: error("GET ${image.url} answered no body")
                val bytes = body.bytes()
                targets.forEach { target ->
                    check(AtomicFileWrite.bytes(target, bytes)) {
                        "cannot move ${target.name} into place"
                    }
                }
            }
            targets.first()
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }
}

/** One post image: the URL to fetch and the identity upstream refers to it by. */
data class PostImage(
    val id: Long,
    val url: String,
    val width: Int,
    val height: Int,
    val extension: String,
)

/** One file a post links to: the URL to fetch and the identity upstream refers to it by. */
data class PostAttachment(
    val id: Long,
    val url: String,
    val name: String,
    val extension: String,
)

/** A post body ready for upstream: HTML whose media tags carry ids, plus the media they name. */
class PreparedPostBody(
    val html: String,
    val photos: List<TLRPC.Photo>,
    val images: List<PostImage>,
    val attachments: List<PostAttachment> = emptyList(),
    val videos: List<PostVideo> = emptyList(),
)

/** One video a post carries: the file to fetch, its cover, and the identity upstream names it by. */
data class PostVideo(
    val id: Long,
    val url: String,
    val name: String,
    val width: Int,
    val height: Int,
    val cover: PostImage?,
)
