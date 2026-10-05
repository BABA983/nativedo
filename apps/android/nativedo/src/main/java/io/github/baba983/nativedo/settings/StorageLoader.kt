package io.github.baba983.nativedo.settings

import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The three groups the storage screen draws; bytes, not formatted yet. */
data class StorageSizes(
    val imagesAndVideos: Long,
    val documents: Long,
    val otherCache: Long,
) {
    val total: Long
        get() = imagesAndVideos + documents + otherCache
}

/**
 * Measures and clears the caches this client fills: the directories upstream's image and file
 * pipeline reads (avatars, post images, emoji and attachments all land in them), reached through
 * [FileLoader] so the screen and the loaders agree on where files are.
 *
 * <p>What is never touched is everything outside those directories - the session store, the
 * cookie jar and the WebView's own data do not live under them, and clearing a cache is not
 * signing out. The cleanup mirrors upstream's own: files and subdirectories are removed, the
 * directories themselves stay, and a `drafts` directory is left alone (upstream's
 * `CacheControlActivity.cleanDirJava` skips it).</p>
 */
object StorageLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Every cache directory this client writes into, as upstream's FileLoader names them. */
    private fun cacheDirs(): List<File> {
        val types = intArrayOf(
            FileLoader.MEDIA_DIR_IMAGE,
            FileLoader.MEDIA_DIR_IMAGE_PUBLIC,
            FileLoader.MEDIA_DIR_VIDEO,
            FileLoader.MEDIA_DIR_VIDEO_PUBLIC,
            FileLoader.MEDIA_DIR_DOCUMENT,
            FileLoader.MEDIA_DIR_FILES,
            FileLoader.MEDIA_DIR_CACHE,
            FileLoader.MEDIA_DIR_AUDIO,
            FileLoader.MEDIA_DIR_STORIES,
        )
        return types.map { type -> FileLoader.checkDirectory(type) }
            .filterNotNull()
            .distinct()
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        var total = 0L
        file.listFiles()?.forEach { child -> total += sizeOf(child) }
        return total
    }

    /** Measures the three groups off the UI thread and answers on the calling (main) thread. */
    fun sizes(onDone: (StorageSizes) -> Unit) {
        scope.launch {
            val sizes = withContext(Dispatchers.IO) {
                fun group(vararg types: Int): Long = types.sumOf { type ->
                    sizeOf(FileLoader.checkDirectory(type))
                }
                StorageSizes(
                    imagesAndVideos = group(
                        FileLoader.MEDIA_DIR_IMAGE, FileLoader.MEDIA_DIR_IMAGE_PUBLIC,
                        FileLoader.MEDIA_DIR_VIDEO, FileLoader.MEDIA_DIR_VIDEO_PUBLIC,
                    ),
                    documents = group(FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_FILES),
                    otherCache = group(
                        FileLoader.MEDIA_DIR_CACHE, FileLoader.MEDIA_DIR_AUDIO,
                        FileLoader.MEDIA_DIR_STORIES,
                    ),
                )
            }
            onDone(sizes)
        }
    }

    /**
     * Deletes the cached files and answers with the bytes freed. Directories are kept; the
     * `drafts` directory is skipped the way upstream's cleaner skips it; after the files are
     * gone the in-memory image cache is dropped too, so a row cannot keep drawing a file the
     * reader just cleared.
     */
    fun clear(onDone: (Long) -> Unit) {
        scope.launch {
            val freed = withContext(Dispatchers.IO) {
                var freed = 0L
                for (dir in cacheDirs()) {
                    val children = dir.listFiles() ?: continue
                    for (child in children) {
                        if (child.name == "drafts") {
                            continue
                        }
                        val before = sizeOf(child)
                        if (deleteRecursively(child)) {
                            freed += before
                        }
                    }
                }
                freed
            }
            runCatching { ImageLoader.getInstance().clearMemory() }
            onDone(freed)
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        return file.delete()
    }
}
