package io.github.baba983.nativedo.topic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.telegram.messenger.FileLoader
import org.telegram.tgnet.TLRPC
import java.io.File
import java.security.MessageDigest

/**
 * Discourse avatars.
 *
 * Upstream draws an avatar from a `TLRPC.UserProfilePhoto`, and it only looks at such a photo's file
 * location — the datacenter id and access hash are for fetching one from Telegram, which this client
 * never does. So an avatar here is a synthetic photo whose file location names a stable id, with the
 * bytes downloaded by the app's own client next to that id. A user built this way also needs a
 * non-zero access hash: upstream refuses to build an avatar location for a peer it cannot address.
 */
object DiscourseAvatars {

    /** Discourse's templates take a size; 96 px is what a 42 dp avatar needs at 2x. */
    const val DEFAULT_SIZE = 96

    /**
     * The expanded profile header draws the same picture across the screen; the template's size is
     * filled with this where the small one would blur, and the site answers its own nearest size.
     */
    const val EXPANDED_SIZE = 720


    /**
     * `/user_avatar/<host>/<username>/{size}/<id>.png` -> an absolute URL at [size]. Discourse also
     * hands out uploads without the size placeholder, in which case the template is already a URL.
     */
    fun url(template: String, baseUrl: String, size: Int = DEFAULT_SIZE): String? {
        val path = template.trim()
        if (path.isEmpty()) {
            return null
        }
        val filled = path.replace("{size}", size.toString())
        return runCatching { java.net.URI(baseUrl).resolve(filled).toString() }.getOrNull()
    }

    fun idOf(url: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xff)
        }
        return (value and 0x7fffffffffffffffL).coerceAtLeast(1L)
    }

    /**
     * The animation's own URL, by the photo id the synthetic avatar is built with.
     *
     * <p>An animated avatar is not a different picture upstream can ask for by itself: the site
     * hands the animation's own URL in its `animated_avatar` metadata, this client fetches it, and
     * the loader only plays it when the location it is handed is marked as an animation. That URL
     * is remembered next to the asset the whole binding path already shares — the id is derived
     * from the URL itself, so a still template and an animation for the same person are two
     * different entries — so both the download and the hand-over pick the animation without the
     * caller having to carry it too.</p>
     */
    private val animatedAvatarUrls = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /** The id of the avatar asset a peer was built with, or null for a peer with no picture. */
    private fun avatarId(user: TLRPC.User?): Long? =
        user?.photo?.photo_id?.takeIf { it != 0L }

    /** Whether the peer's avatar is the animation the site advertised. */
    fun isAnimated(user: TLRPC.User?): Boolean = avatarId(user)?.let { animatedAvatarUrls.containsKey(it) } ?: false

    /** Whether a URL names a GIF, the extension a Discourse animation is served under. */
    private fun isGif(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".gif")

    /**
     * The photo upstream asks for. `photo_small` is the size a chat list and a message cell use;
     * `dc_id = 0` and `volume_id = 0` keep `FileLoader` off Telegram's file-path database.
     */
    fun photo(id: Long): TLRPC.TL_userProfilePhoto = TLRPC.TL_userProfilePhoto().apply {
        photo_id = id
        dc_id = 0
        has_video = false
        photo_small = locationOf(id)
        photo_big = locationOf(id)
    }

    private fun locationOf(id: Long): TLRPC.TL_fileLocationToBeDeprecated =
        TLRPC.TL_fileLocationToBeDeprecated().apply {
            volume_id = 0
            local_id = (id and 0x7fffffffL).toInt()
            dc_id = 0
        }

    @JvmOverloads
    fun user(
        username: String,
        displayName: String,
        avatarTemplate: String,
        baseUrl: String,
        animatedAvatar: String = "",
        size: Int = DEFAULT_SIZE,
    ): TLRPC.TL_user {
        val user = TLRPC.TL_user()
        user.id = DiscourseRows.userId(username)
        user.username = username
        user.first_name = displayName.ifEmpty { username }
        // The animation's own URL when the site names one; the sized still template otherwise.
        val animatedUrl = url(animatedAvatar, baseUrl, size)
        val avatarUrl = animatedUrl ?: url(avatarTemplate, baseUrl, size)
        if (avatarUrl != null) {
            // Upstream builds an avatar location only for a peer with an access hash, and it never
            // fetches anything from a datacenter when the file is already where that location names.
            user.access_hash = 1
            user.photo = photo(idOf(avatarUrl))
            // The site's metadata is the authority; a site that names none but serves a GIF template
            // is still an animation, decided by the asset this client actually fetches.
            if (animatedUrl != null || isGif(avatarUrl)) {
                animatedAvatarUrls[idOf(avatarUrl)] = animatedUrl ?: avatarUrl
            }
        }
        return user
    }

    /**
     * Where upstream looks for a small avatar: an `ImageReceiver` asks for cache type 0, which is
     * the images directory, while `FileLoader.getPathToAttach(photo, "s")` — what the open-a-file
     * paths use — names the cache directory. On a device those are two directories, so the bytes go
     * to both; under the test host they are the same one.
     */
    fun localFiles(user: TLRPC.User?): List<File> {
        val photoSmall = user?.photo?.photo_small ?: return emptyList()
        val attach = FileLoader.getInstance(0).getPathToAttach(photoSmall, false)
        val cache = FileLoader.getInstance(0).getPathToAttach(photoSmall, true)
        return listOf(attach, cache).distinct()
    }

    /**
     * The location the loader reads, at the path our HTTP download put the bytes.
     *
     * <p>A peer whose avatar the site says animates is handed an <em>animation</em> location -
     * upstream's own "decode this path frame by frame" one - so its own image loader builds the
     * animated drawable rather than decoding a single still. Every other peer keeps the plain path
     * location it has always had, so nothing that is not an animation changes.</p>
     */
    fun imageLocation(user: TLRPC.User): org.telegram.messenger.ImageLocation? {
        val file = localFile(user) ?: return null
        return if (isAnimated(user)) {
            org.telegram.messenger.ImageLocation.getForVideoPath(file.absolutePath)
        } else {
            org.telegram.messenger.ImageLocation.getForPath(file.absolutePath)
        }
    }

    /** Whether an avatar is already on disk, checked the way the two readers would. */
    fun isCached(user: TLRPC.User?): Boolean =
        localFiles(user).any { it.exists() && it.length() > 0 }

    /** The file an avatar was downloaded to, or null while it is not on disk yet. */
    fun localFile(user: TLRPC.User?): File? =
        localFiles(user).firstOrNull { it.exists() && it.length() > 0 }

    /**
     * Fetches a peer's avatar with the app's own client.
     *
     * <p>The bytes fetched are the peer's own avatar: when the site advertised an animation, that
     * animation's URL is the one downloaded, whatever URL the caller re-derived from the static
     * template. The still the site serves beside the animation is not what plays, and the file both
     * readers look in must hold the animation.</p>
     */
    suspend fun cache(client: OkHttpClient, avatarUrl: String, user: TLRPC.User): File? =
        withContext(Dispatchers.IO) {
            val targets = localFiles(user)
            if (targets.isEmpty()) {
                return@withContext null
            }
            val file = targets.first()
            if (targets.all { it.exists() && it.length() > 0 }) {
                return@withContext file
            }
            val url = avatarId(user)?.let { animatedAvatarUrls[it] } ?: avatarUrl
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "GET $url answered ${response.code}" }
                    // An avatar is small, and both readers need their own copy of the bytes.
                    val bytes = response.body?.bytes() ?: error("GET $url answered no body")
                    targets.forEach { target ->
                        check(AtomicFileWrite.bytes(target, bytes)) {
                            "cannot move ${target.name} into place"
                        }
                    }
                }
                file
            }.getOrElse { error ->
                error.printStackTrace()
                null
            }
        }
}
