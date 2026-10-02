package io.github.baba983.nativedo.topic

import java.io.File
import java.io.OutputStream

/**
 * A cache file's bytes, written so that a reader never sees a half-written file and no two writers
 * share the file they are writing.
 *
 * <p>Upstream's cells treat any file that exists at the cache path as the picture, so the bytes
 * cannot be written there directly: a transfer cut short would leave a file the cell would draw as
 * if it were whole. A download therefore writes a private temporary file beside its target and
 * moves that into place in one step. The temporary name carries a token unique to the write,
 * because two downloads of the same picture can overlap - a topic opened twice, a retry next to a
 * transfer still running - and a shared temporary name would have the two overwriting and
 * truncating each other's bytes. With a name of its own each write moves a whole file into place,
 * and the last one to arrive is the file that stays; no interleaving can mix two downloads'
 * bytes, and a writer that fails removes only its own temporary file.</p>
 */
internal object AtomicFileWrite {

    /** Writes [bytes] to [target], replacing it in one step. Returns whether the file is in place. */
    fun bytes(target: File, bytes: ByteArray): Boolean = write(target) { output ->
        output.write(bytes)
    }

    /**
     * Runs [write] into a private temporary file beside [target], then moves it into place. Returns
     * whether [target] now holds the written bytes; on any failure the temporary file is removed
     * and the failure is reported to the caller.
     */
    fun write(target: File, write: (OutputStream) -> Unit): Boolean {
        val temp = tempFor(target)
        return try {
            temp.outputStream().use(write)
            commit(temp, target)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    /**
     * A file no other write shares, in the target's own directory: `File.createTempFile` picks a
     * fresh name and creates the file, so two writers can never open the same one. The caller
     * writes it; [commit] then moves it into place.
     */
    fun tempFor(target: File): File {
        target.parentFile?.mkdirs()
        return File.createTempFile(target.name + ".", TEMP_SUFFIX, target.parentFile)
    }

    /**
     * Moves a finished [temp] onto [target] in one step, so a reader sees either the old file or a
     * whole new one and never a mixture. A move that cannot happen removes the temporary file and
     * reports false; a concurrent writer's own temporary file is never touched.
     */
    fun commit(temp: File, target: File): Boolean =
        if (temp.renameTo(target)) {
            true
        } else {
            temp.delete()
            false
        }

    /** What every temporary file ends with, so a leftover one is recognisable as a temporary. */
    const val TEMP_SUFFIX = ".part"
}
