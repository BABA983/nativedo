package io.github.baba983.nativedo.topic

import android.graphics.BitmapFactory
import io.github.baba983.nativedo.hooks.NativeDoHooks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.AnimatedEmojiDrawable
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

    /**
     * The largest side a photo's own size may declare. Upstream asks a photo for a size no larger
     * than this (`AndroidUtilities.getPhotoSize()`), and picks the largest size that fits; a photo
     * whose only size is larger still wins, but a photo with a smaller sibling would switch to it.
     * Capping the full picture's own size keeps it the one the block and the viewer pick.
     */
    private const val MAX_PHOTO_SIDE = 1280

    /** The account every message this module builds belongs to; upstream's singletons are per account. */
    private const val ACCOUNT = 0

    /** The videos the posts have carried, so a tap on one can find the file behind its id. */
    private val videos = java.util.concurrent.ConcurrentHashMap<Long, PostVideo>()

    /** Discourse's ratio on the wrapper around a video that carries no size of its own. */
    private val RATIO = Regex("--aspect-ratio:\\s*([0-9.]+)\\s*/\\s*([0-9.]+)")

    /** A video tag with no frame of its own is still a video; sixteen by nine is what a player assumes. */
    private const val DEFAULT_FRAME_WIDTH = 1280

    @JvmOverloads
    fun prepare(
        cookedHtml: String,
        baseUrl: String,
        emojiCatalog: Map<String, String> = emptyMap(),
    ): PreparedPostBody {
        val document = Jsoup.parseBodyFragment(cookedHtml ?: "")
        // Serialize verbatim: jsoup's pretty-printer injects a newline and indent after a <br>,
        // which upstream's importer reads as a second, blank line. The text itself is normalized
        // at the cooked-input boundary in DiscoursePostBody.
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val images = ArrayList<PostImage>()
        val mediaBlocks = ArrayList<Element>()
        // Table cells carry rich text, not PageBlocks. Keep media at its authored position as a
        // source link, rather than silently dropping it or moving it outside the table.
        for (element in body.select("td img, th img, td video, th video")) {
            if (element.hasClass("emoji") || element.hasClass("avatar")) continue
            val source = if (element.normalName() == "img") originalSource(element, baseUrl)
                else resolve(element.attr("src").ifBlank {
                    element.selectFirst("source[src]")?.attr("src").orEmpty()
                }, baseUrl)
            if (source != null && (source.startsWith("https://") || source.startsWith("http://"))) {
                val link = Element(Tag.valueOf("a"), "").attr("href", source).text(source)
                val replacement = if (element.hasAttr("data-spoiler"))
                    Element(Tag.valueOf("spoiler"), "").appendChild(link) else link
                element.replaceWith(replacement)
            }
        }
        // The cell's delegate passes external URLs straight to Browser; it does not resolve
        // relative hrefs. Resolve here, where the configured site's base URL is available.
        for (anchor in body.select("a[href]")) {
            val href = anchor.attr("href")
            if (runCatching { URI(href).scheme == null }.getOrDefault(false)) {
                resolve(href, baseUrl)?.let { anchor.attr("href", it) }
            }
        }
        // A video is read before the wrappers around it go: Discourse puts the frame's shape on the
        // wrapper it wraps the tag in, and unwrapping would take that with it.
        val videos = ArrayList<PostVideo>()
        for (element in body.select("video")) {
            val video = postVideo(element, baseUrl) ?: continue
            videos += video
            mediaBlocks += element
            this.videos[video.id] = video
            // Upstream reads a media tag's `src` as the id of a file it already holds - for a
            // video, whose block then draws its cover and plays the file, as it does for a photo.
            element.attr("src", video.id.toString())
            element.removeAttr("poster")
            element.select("source").remove()
        }
        body.select(LIGHTBOX_META).remove()

        val photos = ArrayList<TLRPC.Photo>()
        // Read the lightbox's download target before unwrapping it; src is usually a resize.
        for (image in body.select("img")) {
            val postImage = postImage(image, baseUrl) ?: continue
            images += postImage
            photos += photoOf(postImage)
            mediaBlocks += image
            // Upstream reads the source of a media tag as the numeric key of a photo it holds, and
            // writes the same shape when its editor serialises a message.
            image.attr("src", postImage.id.toString())
            image.removeAttr("srcset")
            image.removeAttr("data-download-href")
        }
        // The files this client fetches itself, told to the seam as the body is built - before any
        // cell can ask about one. A block then reads its loading state from this client's own
        // transfer, and never hands its file to upstream's downloader, which has no datacenter for
        // it. See NativeDoHooks.mediaBlockLoading and mediaAutoDownload. An image whose source is a
        // GIF is registered as an animation too, so the block's own loader plays it instead of
        // drawing one frame; the site's smaller preview is registered on its own source, so a still
        // preview is never mistaken for one.
        for (image in images) {
            val name = photoFileName(image)
            NativeDoHooks.setClientMediaFile(name, true)
            if (isAnimatedSource(image.url)) {
                NativeDoHooks.setClientMediaAnimated(name, true)
            }
            if (image.previewUrl != null) {
                val previewName = FileLoader.getAttachFileName(previewSizeOf(image))
                NativeDoHooks.setClientMediaFile(previewName, true)
                if (isAnimatedSource(image.previewUrl)) {
                    NativeDoHooks.setClientMediaAnimated(previewName, true)
                }
            }
        }
        // Inline emoji: the site's cook leaves each one as an `img.emoji` (standard twemoji, or a
        // custom emoji it uploaded) carrying the picture's own src and a readable shortcode as
        // alt/title. Upstream's importer drops a bare inline img, so each becomes an
        // `animated-emoji` element - the tag upstream's rich renderer draws from a document id -
        // once its picture is on disk; until then it stays the readable label it came with, so a
        // slow or refused fetch never leaves a blank square. The picture is fetched through the
        // same image cache as a post photo, by the id both the tag and the document share.
        val emojiImages = ArrayList<PostImage>()
        if (emojiCatalog.isNotEmpty()) resolveShortcodes(body, baseUrl, emojiCatalog, emojiImages)
        for (image in body.select("img.emoji")) {
            val asset = emojiAsset(image, baseUrl)
            if (asset == null) {
                image.replaceWith(TextNode(image.attr("alt").ifBlank { image.attr("title") }))
                continue
            }
            if (emojiCached(asset.image)) {
                seedEmojiDocument(asset.image)
                image.replaceWith(animatedEmojiElement(asset))
            } else {
                emojiImages += asset.image
                image.replaceWith(TextNode(asset.label))
            }
        }
        for (wrapper in body.select(WRAPPERS)) {
            wrapper.unwrap()
        }
        // Ordinary divs are structural wrappers; only Telegram's explicit rich gallery/button
        // containers have their own semantics. Keep a section boundary so adjacent text divs
        // stay separate paragraphs and heading/table children retain their own blocks.
        for (wrapper in body.select("div")) {
            val classes = wrapper.className().lowercase()
            if (!classes.contains("collage") && !classes.contains("slideshow") && !classes.contains("button-row")) {
                wrapper.tagName("section")
            }
        }
        val attachments = ArrayList<PostAttachment>()
        for (anchor in body.select(ATTACHMENT)) {
            // Documents have no native spoiler cover. Keep a hidden attachment as a covered
            // link, just as a table cell keeps it inline, rather than exposing its filename.
            if (anchor.parents().any { it.normalName() in setOf("td", "th", "spoiler") }) continue
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
            mediaBlocks += replacement
        }
        // Split text wrappers around prepared media, keeping their styles and authored order.
        // Stop at semantic containers: a list/quote/details owns its media, not the outer body.
        for (media in mediaBlocks) {
            while (true) {
                val parent = media.parent() ?: break
                if (parent.normalName() in setOf("body", "blockquote", "li", "td", "th", "details", "pre", "code", "figure")) break
                if (parent.normalName() == "div") break // gallery/button containers retained above
                val children = parent.childNodes().toList()
                val split = children.indexOf(media)
                val before = parent.clone().empty().appendChildren(children.take(split))
                val after = parent.clone().empty().appendChildren(children.drop(split + 1))
                // An ordinary image link is navigation, not a lightbox download hint. Keep a
                // separate link when its only visible child becomes a gallery media block.
                if (parent.normalName() == "a" && parent.hasAttr("href") && children.size == 1) {
                    if (media.hasAttr("data-spoiler")) after.appendElement("spoiler").text("打开链接")
                    else after.text("打开链接")
                }
                if (before.text().isNotBlank() || before.select("img,video,animated-emoji,document,input").isNotEmpty()) parent.before(before)
                parent.before(media)
                if (after.text().isNotBlank() || after.select("img,video,animated-emoji,document,input").isNotEmpty()) parent.before(after)
                parent.remove()
            }
        }
        return PreparedPostBody(body.html(), photos, images, attachments, videos, emojiImages.distinctBy { it.id })
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
            val outcome = try {
                // Staging the transfer's own temporary file can itself fail - a cache directory that
                // cannot be created or written, a name it will not take - and that is a fetch that
                // failed, not a block left waiting: the failure is mapped below and the block's state
                // is cleared however this ends. The temporary is this fetch's own, so a cancel
                // removes only its own half-written bytes.
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
                if (transferred == VideoDownload.Stored) storeVideo(file, partial, name) else transferred
            } catch (error: Throwable) {
                if (cancelled.get()) {
                    VideoDownload.Cancelled
                } else {
                    FileLog.e("nativedo: a post's clip could not be staged", error)
                    VideoDownload.Failed
                }
            } finally {
                NativeDoHooks.setFetchCanceller(name, null)
                NativeDoHooks.setFileLoading(name, false)
            }
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
        val url = originalSource(image, baseUrl) ?: return null
        // Discourse writes a lightbox as a small, optimized picture (`src`) beside the original
        // (`data-download-href`). The original is what the cell draws and the viewer opens; the
        // smaller one is a preview the reader can look at while the original is still arriving.
        val preview = resolve(image.attr("src"), baseUrl)
            ?.takeIf { candidate -> candidate != url && (candidate.startsWith("http://") || candidate.startsWith("https://")) }
        return PostImage(
            id = idOf(url),
            url = url,
            width = width,
            height = height.coerceAtLeast(1),
            extension = extensionOf(url),
            previewUrl = preview,
        )
    }

    /** The size reserved for an emoji whose own markup named none: the site's own 20px default. */
    private const val EMOJI_PX = 20

    /** One cooked emoji: the id/label upstream's tag carries, and the picture both share. */
    private data class EmojiAsset(val label: String, val image: PostImage)

    /**
     * One cooked `img.emoji` as an emoji asset, or null when it names no picture this client can
     * fetch. The label is what the site already put on it (`alt`, else `title`), so a body never
     * loses the run: an emoji whose picture is not there yet reads as its shortcode.
     */
    private fun emojiAsset(image: Element, baseUrl: String): EmojiAsset? {
        val source = image.attr("src").ifBlank { image.attr("data-src") }
        val url = resolve(source, baseUrl)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        val label = image.attr("alt").ifBlank { image.attr("title") }.ifBlank { ":${nameOf(url)}:" }
        val side = image.attr("width").toIntOrNull() ?: EMOJI_PX
        val height = image.attr("height").toIntOrNull() ?: EMOJI_PX
        val postImage = PostImage(
            id = idOf(url),
            url = url,
            width = side.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            extension = extensionOf(url),
        )
        return EmojiAsset(label, postImage)
    }

    /**
     * Whether an emoji's picture is on disk <em>and</em> decodable. A cache file that a fetch cut
     * short, or a 200 the site answered with something that is not a picture, is not artwork: the
     * emoji keeps its readable label instead of drawing a blank square. Same rule the reaction
     * chips use - the bytes are checked, not the response code.
     */
    fun emojiCached(image: PostImage): Boolean = localFiles(image).any { file ->
        file.exists() && file.length() > 0 && runCatching {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching false
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    /** The tag upstream's importer reads as a custom emoji: its id, and the label it covers. */
    private fun animatedEmojiElement(asset: EmojiAsset): Element =
        Element(Tag.valueOf("animated-emoji"), "")
            .attr("data-document-id", asset.image.id.toString())
            .appendText(asset.label)

    /**
     * Registers the emoji's document with upstream before any span names it.
     *
     * <p>Upstream resolves a custom emoji's id through its own document store, which is a Telegram
     * database this client never opens. Seeding the fetcher's in-memory cache with a document whose
     * picture sits in the file cache this client fills is what lets the renderer draw it with no
     * database and no datacenter: the document's own thumbnail names the file, and its id is the one
     * the tag carries.</p>
     */
    private fun seedEmojiDocument(image: PostImage) {
        val file = localFiles(image).firstOrNull { it.exists() }
        val document = TLRPC.TL_document().apply {
            id = image.id
            dc_id = 0
            access_hash = 0
            file_name = "emoji." + image.extension
            mime_type = mimeOf(image.extension)
            size = file?.length() ?: 0L
            thumbs = arrayListOf(sizeOf(image))
            attributes = arrayListOf()
        }
        // Upstream keys the fetcher by account: the renderer asks the selected one, the rest of this
        // client files everything under account 0. Both are seeded so the id is found either way.
        AnimatedEmojiDrawable.getDocumentFetcher(0).putDocument(document)
        val selected = UserConfig.selectedAccount
        if (selected != 0) {
            AnimatedEmojiDrawable.getDocumentFetcher(selected).putDocument(document)
        }
    }

    /** A picture's file name, without its extension, as a readable shortcode label. */
    private fun nameOf(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifEmpty { "emoji" }

    /** The form a raw `:shortcode:` is written in. */
    private val SHORTCODE = Regex(":([A-Za-z0-9_+\\-]{1,60}):")

    /**
     * Raw shortcodes the site's cook left as literal text: `:name:` becomes the same emoji a cooked
     * one would, but only when the loaded catalog names that shortcode. An unknown literal, and any
     * text inside a code/pre block, is left exactly as written. Only missing pictures are queued
     * for download, so a warm picture cannot cause an unrelated failed label to rebuild the floor.
     */
    private fun resolveShortcodes(
        root: Element,
        baseUrl: String,
        catalog: Map<String, String>,
        out: MutableList<PostImage>,
    ) {
        val texts = ArrayList<TextNode>()
        collectTextNodes(root, texts)
        for (text in texts) {
            val whole = text.wholeText
            if (whole.indexOf(':') < 0) {
                continue
            }
            val pieces = ArrayList<Node>()
            var last = 0
            for (match in SHORTCODE.findAll(whole)) {
                val name = match.groupValues[1]
                val raw = catalog[name]?.takeUnless { it.isBlank() } ?: continue
                val url = resolve(raw, baseUrl)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: continue
                if (match.range.first > last) {
                    pieces += TextNode(whole.substring(last, match.range.first))
                }
                val label = ":$name:"
                val image = PostImage(idOf(url), url, EMOJI_PX, EMOJI_PX, extensionOf(url))
                if (emojiCached(image)) {
                    seedEmojiDocument(image)
                    pieces += animatedEmojiElement(EmojiAsset(label, image))
                } else {
                    out += image
                    pieces += TextNode(label)
                }
                last = match.range.last + 1
            }
            if (pieces.isEmpty()) {
                continue
            }
            if (last < whole.length) {
                pieces += TextNode(whole.substring(last))
            }
            for (piece in pieces) {
                text.before(piece)
            }
            text.remove()
        }
    }

    /** The raw text of [root] outside the tags whose whitespace and literals are authored. */
    private fun collectTextNodes(element: Element, out: MutableList<TextNode>) {
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> out += child
                is Element -> if (child.normalName() !in VERBATIM_TAGS) collectTextNodes(child, out)
                else -> Unit
            }
        }
    }

    /** Tags whose text is authored verbatim, so a `:shortcode:` inside one is not an emoji. */
    private val VERBATIM_TAGS = setOf("pre", "code", "tt", "textarea")

    /** Explicit download targets, then responsive candidates; ordinary image links are navigation. */
    private fun originalSource(image: Element, baseUrl: String): String? {
        val lightbox = image.parents().firstOrNull { it.tagName() == "a" && it.hasClass("lightbox") }
        val hints = listOf(image.attr("data-download-href"),
            lightbox?.attr("data-download-href").orEmpty(), lightbox?.attr("href").orEmpty())
        for (hint in hints) {
            val url = resolve(hint, baseUrl) ?: continue
            if (url.startsWith("https://") || url.startsWith("http://")) return url
        }
        val candidates = image.attr("srcset").split(',').mapNotNull { candidate ->
            val parts = candidate.trim().split(Regex("\\s+"))
            val url = resolve(parts.first(), baseUrl) ?: return@mapNotNull null
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
            val size = parts.getOrNull(1)?.removeSuffix("w")?.removeSuffix("x")?.toDoubleOrNull() ?: 1.0
            url to size
        }
        return candidates.firstOrNull { URI(it.first).path.contains("/original/") }?.first
            ?: candidates.maxByOrNull { it.second }?.first
            ?: resolve(image.attr("src"), baseUrl)
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
     * Whether the asset at [url] is an animation. Discourse serves an animated upload as a GIF, and
     * the extension survives the lightbox's original resolution, so the source this client actually
     * fetches decides it - not the block's own name in the cache.
     */
    private fun isAnimatedSource(url: String): Boolean = extensionOf(url) == "gif"

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
    private fun locationOf(id: Long): TLRPC.TL_fileLocationToBeDeprecated =
        TLRPC.TL_fileLocationToBeDeprecated().apply {
            volume_id = 0
            local_id = (id and 0x7fffffffL).toInt()
            dc_id = 0
        }

    private fun sizeOf(image: PostImage): TLRPC.TL_photoSize = TLRPC.TL_photoSize().apply {
        type = "x"
        w = image.width
        h = image.height
        size = 0
        location = locationOf(image.id)
    }

    /**
     * The full picture as the block sees it.
     *
     * <p>Its own size keeps the photo's display shape, but its declared side is never larger than
     * [MAX_PHOTO_SIDE], the side upstream asks a photo for: a size larger than that is skipped by
     * upstream's own "closest size" pick as soon as a smaller one exists, and the block would end
     * up drawing - and opening - the preview. Capping keeps the shape (only the layout scale uses
     * it) and keeps the full picture the one the block and the viewer choose.</p>
     */
    private fun fullSizeOf(image: PostImage): TLRPC.TL_photoSize = TLRPC.TL_photoSize().apply {
        type = "x"
        val side = maxOf(image.width, image.height).coerceAtLeast(1)
        val scale = if (side > MAX_PHOTO_SIDE) MAX_PHOTO_SIDE.toDouble() / side else 1.0
        w = (image.width * scale).toInt().coerceAtLeast(1)
        h = (image.height * scale).toInt().coerceAtLeast(1)
        size = 0
        location = locationOf(image.id)
    }

    /**
     * The site's own smaller picture as a second, half-shape size: what the block draws while the
     * full one is still on its way. It has its own file, so a preview landing does not stand in for
     * the full picture in upstream's image cache.
     */
    private fun previewSizeOf(image: PostImage): TLRPC.TL_photoSize = TLRPC.TL_photoSize().apply {
        type = "m"
        val full = fullSizeOf(image)
        w = (full.w / 2).coerceAtLeast(1)
        h = (full.h / 2).coerceAtLeast(1)
        size = 0
        location = locationOf(idOf(image.previewUrl ?: image.url))
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
        val list = ArrayList<TLRPC.PhotoSize>()
        list.add(fullSizeOf(image))
        if (image.previewUrl != null) {
            list.add(previewSizeOf(image))
        }
        sizes = list
    }

    /**
     * The name upstream's photo block keys the file by: the same name it hands the module on a
     * press, and the name the module files its progress, failure and success reports under.
     */
    fun photoFileName(image: PostImage): String = FileLoader.getAttachFileName(sizeOf(image))

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

    /** Where upstream looks for the preview the block draws while the full picture is on its way. */
    fun previewLocalFiles(image: PostImage): List<File> {
        if (image.previewUrl == null) {
            return emptyList()
        }
        val size = previewSizeOf(image)
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

    /** What one post image's fetch ended as. */
    enum class PhotoDownload {
        /** The picture the block draws is on disk. */
        Stored,

        /** The site refused the request, or the transfer broke, with nothing left to draw. */
        Failed,

        /** The reader stopped it: no half-written file is left, and the block goes back to its button. */
        Cancelled,
    }

    /** All rows sharing a file observe one transfer, including its preview and failed outcome. */
    private class PhotoTransfer {
        val result = CompletableDeferred<PhotoDownload>()
        val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    }

    private val photoTransfers = java.util.concurrent.ConcurrentHashMap<String, PhotoTransfer>()
    private val photoPermits = Semaphore(4)

    /**
     * How to start (or retry) the fetch for one file, when the block's own download button is
     * pressed. Registered by the screen that draws the row, and removed when that screen leaves.
     */
    private val photoStarters = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<Runnable>>()

    private val downloadHandler = object : NativeDoHooks.MediaBlockDownloadHandler {
        override fun start(fileName: String): Boolean {
            val starter = photoStarters[fileName]?.lastOrNull() ?: return false
            starter.run()
            return true
        }
    }

    init {
        NativeDoHooks.setMediaBlockDownloadHandler(downloadHandler)
    }

    /** Registers how a press on the block's own download button starts (or retries) one fetch. */
    fun registerPhotoStarter(name: String, starter: Runnable) {
        if (name.isEmpty()) {
            return
        }
        photoStarters.compute(name) { _, listeners ->
            (listeners ?: java.util.concurrent.CopyOnWriteArrayList()).apply { addIfAbsent(starter) }
        }
    }

    /** Removing the top screen's starter restores the same photo's retry on the screen beneath it. */
    fun clearPhotoStarter(name: String, starter: Runnable) {
        photoStarters.computeIfPresent(name) { _, listeners ->
            listeners.remove(starter)
            listeners.takeUnless { it.isEmpty() }
        }
    }

    /**
     * Downloads the picture the block draws for [image] when it is not on disk yet, telling the
     * block about the transfer on upstream's own channels.
     *
     * <p>A fetch this client runs is not a `FileLoader` download, so upstream's block never learns
     * of it by itself: it is put into its own loading state before the first byte, given the
     * transfer as it arrives, and told how it ended - success, failure, or the reader's cancel.
     * The writes go through a private temporary file, so a transfer cut short leaves nothing a cell
     * would draw and a retry starts from an empty name.</p>
     *
     * <p>When the site serves a smaller preview beside the original, that preview is fetched first,
     * into its own file, and [onBytes] is called the moment it lands - the block then draws it while
     * the original is still on its way; the original replaces it as the row's main picture once it
     * arrives, under the same identity, and the viewer opens that file.</p>
     */
    suspend fun downloadPhoto(
        client: OkHttpClient,
        image: PostImage,
        onBytes: () -> Unit,
    ): PhotoDownload = withContext(Dispatchers.IO) {
        val name = photoFileName(image)
        val created = PhotoTransfer()
        val existing = photoTransfers.putIfAbsent(name, created)
        val transfer = existing ?: created
        transfer.listeners.add(onBytes)
        try {
            if (existing != null) {
                // A preview may have landed before this row joined the transfer.
                withContext(Dispatchers.Main.immediate) { onBytes() }
                transfer.result.await()
            } else {
                val outcome = photoPermits.withPermit {
                    fetchPhoto(client, image, name) { transfer.listeners.forEach { it() } }
                }
                transfer.result.complete(outcome)
                outcome
            }
        } catch (error: Throwable) {
            if (existing == null) transfer.result.completeExceptionally(error)
            throw error
        } finally {
            transfer.listeners.remove(onBytes)
            if (existing == null) photoTransfers.remove(name, transfer)
        }
    }

    private suspend fun fetchPhoto(
        client: OkHttpClient,
        image: PostImage,
        name: String,
        onBytes: () -> Unit,
    ): PhotoDownload {
        val targets = localFiles(image)
        if (targets.all(::decodablePhoto)) {
            return PhotoDownload.Stored
        }
        targets.filterNot(::decodablePhoto).forEach { it.delete() }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val call = java.util.concurrent.atomic.AtomicReference<Call?>()
        NativeDoHooks.setFileLoading(name, true)
        NativeDoHooks.setFetchCanceller(name) {
            cancelled.set(true)
            call.get()?.cancel()
        }
        // The block is told the fetch exists before the first byte, so it draws a download rather
        // than a button that seems to do nothing.
        reportPhotoProgress(name, 0L, 0L)
        val outcome = try {
            val preview = image.previewUrl
            if (preview != null) {
                val previewOutcome = transferPhoto(client, preview, previewLocalFiles(image), null, call, cancelled)
                if (previewOutcome == PhotoDownload.Stored) {
                    withContext(Dispatchers.Main.immediate) { onBytes() }
                }
                if (previewOutcome == PhotoDownload.Cancelled) {
                    PhotoDownload.Cancelled
                } else {
                    transferPhoto(client, image.url, targets, name, call, cancelled)
                }
            } else {
                transferPhoto(client, image.url, targets, name, call, cancelled)
            }
        } finally {
            NativeDoHooks.setFetchCanceller(name, null)
            NativeDoHooks.setFileLoading(name, false)
        }
        when (outcome) {
            PhotoDownload.Stored -> {
                val length = targets.first().length()
                reportPhotoProgress(name, length, length)
                postPhotoNotification(NotificationCenter.fileLoaded, name, targets.first().absolutePath)
            }

            PhotoDownload.Cancelled -> postPhotoNotification(NotificationCenter.fileLoadFailed, name, 1)

            PhotoDownload.Failed -> postPhotoNotification(NotificationCenter.fileLoadFailed, name, 0)
        }
        return outcome
    }

    /**
     * One transfer, into a private temporary file beside the first target and then into every place
     * upstream looks. A transfer that carried the whole picture stops with those bytes still under
     * the temporary name; a cut one leaves no file behind. [progressName] is the name the block
     * looks its file up by, or null when the transfer is a preview the block does not key on.
     */
    private fun transferPhoto(
        client: OkHttpClient,
        url: String,
        targets: List<File>,
        progressName: String?,
        call: java.util.concurrent.atomic.AtomicReference<Call?>,
        cancelled: java.util.concurrent.atomic.AtomicBoolean,
    ): PhotoDownload {
        if (cancelled.get()) return PhotoDownload.Cancelled
        if (targets.all(::decodablePhoto)) return PhotoDownload.Stored
        var partial: File? = null
        return try {
            val staged = AtomicFileWrite.tempFor(targets.first()).also { partial = it }
            val request = client.newCall(Request.Builder().url(url).build())
            call.set(request)
            if (cancelled.get()) {
                request.cancel()
                return PhotoDownload.Cancelled
            }
            request.execute().use { response ->
                if (!response.isSuccessful) {
                    return@use PhotoDownload.Failed
                }
                val body = response.body ?: return@use PhotoDownload.Failed
                val total = body.contentLength()
                body.byteStream().use { input ->
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var loaded = 0L
                        var reported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }
                            loaded += read
                            output.write(buffer, 0, read)
                            // A report is a UI-thread hop per reader, so a transfer reports in steps.
                            if (progressName != null && (loaded - reported >= PROGRESS_STEP || loaded == total)) {
                                reported = loaded
                                reportPhotoProgress(progressName, loaded, total)
                            }
                        }
                    }
                }
                when {
                    cancelled.get() -> PhotoDownload.Cancelled
                    !decodablePhoto(staged) -> PhotoDownload.Failed
                    installPhoto(staged, targets) -> PhotoDownload.Stored
                    else -> PhotoDownload.Failed
                }
            }
        } catch (error: Throwable) {
            if (cancelled.get()) {
                PhotoDownload.Cancelled
            } else {
                FileLog.e("nativedo: photo transfer failed (${error.javaClass.simpleName})")
                PhotoDownload.Failed
            }
        } finally {
            // A successful install moved this name; every other path removes the private partial.
            partial?.delete()
        }
    }

    /** A 200 HTML challenge or an empty body is not a cached picture. Read only the image header. */
    private fun decodablePhoto(file: File): Boolean {
        if (file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    /**
     * The finished transfer becomes the file the block reads, in every place upstream looks. The
     * first target is the moved temporary file; the rest are written from it, because on a device
     * the images and cache directories are two.
     */
    private fun installPhoto(partial: File, targets: List<File>): Boolean {
        if (!AtomicFileWrite.commit(partial, targets.first())) {
            return false
        }
        val source = targets.first()
        for (target in targets.drop(1)) {
            val written = AtomicFileWrite.write(target) { output -> source.inputStream().use { it.copyTo(output) } }
            if (!written) {
                return false
            }
        }
        return true
    }

    private fun reportPhotoProgress(name: String, loaded: Long, total: Long) {
        // An unknown Content-Length cannot be a divisor for PhotoViewer's progress calculation.
        postPhotoNotification(NotificationCenter.fileLoadProgressChanged, name,
            if (total > 0) loaded else 0L, if (total > 0) total else 1L)
    }

    /**
     * Upstream's own channel is how the block that asked for a file learns how the fetch it did not
     * start ended: a report here is what draws its radial progress, stops its spinner, or returns it
     * to its download button.
     */
    private fun postPhotoNotification(id: Int, name: String, vararg args: Any?) {
        NotificationCenter.getInstance(ACCOUNT).postNotificationNameOnUIThread(id, name, *args)
    }
}

/** One post image: the URL to fetch and the identity upstream refers to it by. */
data class PostImage(
    val id: Long,
    val url: String,
    val width: Int,
    val height: Int,
    val extension: String,
    /** The smaller picture the site serves beside the original, or null when there is none. */
    val previewUrl: String? = null,
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
    /** Missing emoji pictures to fetch, then upgrade their labels to upstream's inline artwork. */
    val emojis: List<PostImage> = emptyList(),
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
