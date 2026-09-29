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

    fun user(username: String, displayName: String, avatarTemplate: String, baseUrl: String): TLRPC.TL_user {
        val user = TLRPC.TL_user()
        user.id = DiscourseRows.userId(username)
        user.username = username
        user.first_name = displayName.ifEmpty { username }
        val avatarUrl = url(avatarTemplate, baseUrl)
        if (avatarUrl != null) {
            // Upstream builds an avatar location only for a peer with an access hash, and it never
            // fetches anything from a datacenter when the file is already where that location names.
            user.access_hash = 1
            user.photo = photo(idOf(avatarUrl))
        }
        return user
    }

    /**
     * Where upstream looks for a small avatar: an `ImageReceiver` asks for cache type 0, which is
     * the images directory, while `FileLoader.getPathToAttach(photo, "s")` — what the open-a-file
     * paths use — names the cache directory. On a device those are two directories, so the bytes go
     * to both; under the test host they are the same one.
     */
    fun localFiles(user: TLRPC.User): List<File> {
        val photoSmall = user.photo?.photo_small ?: return emptyList()
        val attach = FileLoader.getInstance(0).getPathToAttach(photoSmall, false)
        val cache = FileLoader.getInstance(0).getPathToAttach(photoSmall, true)
        return listOf(attach, cache).distinct()
    }

    /** The avatar image upstream asks for; null when the user has no photo. */
    fun imageLocation(user: TLRPC.User): org.telegram.messenger.ImageLocation? =
        org.telegram.messenger.ImageLocation.getForUser(0, user, org.telegram.messenger.ImageLocation.TYPE_SMALL)

    /** Whether an avatar is already on disk, checked the way the two readers would. */
    fun isCached(user: TLRPC.User): Boolean = localFiles(user).any { it.exists() && it.length() > 0 }

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
            runCatching {
                client.newCall(Request.Builder().url(avatarUrl).build()).execute().use { response ->
                    check(response.isSuccessful) { "GET $avatarUrl answered ${response.code}" }
                    // An avatar is small, and both readers need their own copy of the bytes.
                    val bytes = response.body?.bytes() ?: error("GET $avatarUrl answered no body")
                    targets.forEach { target ->
                        target.parentFile?.mkdirs()
                        val partial = File(target.parentFile, target.name + ".part")
                        partial.writeBytes(bytes)
                        check(partial.renameTo(target)) { "cannot move ${partial.name} into place" }
                    }
                }
                file
            }.getOrElse { error ->
                error.printStackTrace()
                null
            }
        }
}
