package io.github.baba983.nativedo.topic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.telegram.messenger.FileLoader
import org.telegram.tgnet.TLRPC
import java.io.File
import java.net.URI
import java.security.MessageDigest

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
     * Below this width an image is decoration rather than content — an emoji, an avatar, a onebox
     * favicon — and it stays an inline picture in the text.
     */
    private const val MIN_MEDIA_WIDTH = 120

    /** Discourse's presentational wrappers; upstream's importer dispatches on the child tag. */
    private const val WRAPPERS = "div.md-table, div.lightbox-wrapper, a.lightbox"

    /**
     * Discourse's own download hint next to a lightboxed image ("name.jpg 31.1 KB"), which is not
     * part of the post.
     */
    private const val LIGHTBOX_META = "div.meta"

    fun prepare(cookedHtml: String, baseUrl: String): PreparedPostBody {
        val document = Jsoup.parseBodyFragment(cookedHtml ?: "")
        val body = document.body()
        for (wrapper in body.select(WRAPPERS)) {
            wrapper.unwrap()
        }
        body.select(LIGHTBOX_META).remove()

        val images = ArrayList<PostImage>()
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
        return PreparedPostBody(body.html(), photos, images)
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

    fun photoOf(image: PostImage): TLRPC.TL_photo = TLRPC.TL_photo().apply {
        id = image.id
        dc_id = 0
        access_hash = 0
        file_reference = ByteArray(0)
        sizes = arrayListOf(sizeOf(image))
    }

    /** The file upstream draws from; `fileExists()` and the loader agree on this path. */
    fun localFile(image: PostImage): File =
        FileLoader.getInstance(0).getPathToAttach(sizeOf(image), false)

    /**
     * Downloads the image when it is not cached yet. The writes go through a temporary name so a
     * transfer cut short cannot leave a file that the cell would happily "find".
     */
    suspend fun cache(client: OkHttpClient, image: PostImage): File? = withContext(Dispatchers.IO) {
        val file = localFile(image)
        if (file.exists() && file.length() > 0) {
            return@withContext file
        }
        runCatching {
            client.newCall(Request.Builder().url(image.url).build()).execute().use { response ->
                check(response.isSuccessful) { "GET ${image.url} answered ${response.code}" }
                val body = response.body ?: error("GET ${image.url} answered no body")
                file.parentFile?.mkdirs()
                val partial = File(file.parentFile, file.name + ".part")
                body.byteStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                check(partial.renameTo(file)) { "cannot move ${partial.name} into place" }
            }
            file
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

/** A post body ready for upstream: HTML whose media tags carry ids, plus the photos they name. */
class PreparedPostBody(
    val html: String,
    val photos: List<TLRPC.Photo>,
    val images: List<PostImage>,
)
