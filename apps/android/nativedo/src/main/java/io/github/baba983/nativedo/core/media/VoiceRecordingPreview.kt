package io.github.baba983.nativedo.core.media

import android.media.MediaPlayer
import java.io.File

/**
 * Plays a local recording back, so the reader can check a take before it is sent. This is preview
 * only: it plays a private cache file this client wrote, never a remote Discourse upload (a received
 * message's audio is a URL the client has no local copy of, and rendering it is the screen's job).
 */
interface VoiceRecordingPreview {
    /** Starts [file] from the beginning, replacing any previous preview. */
    fun play(file: File)

    fun stop()

    /** The file being previewed, or null. */
    val playing: File?
}

/** [VoiceRecordingPreview] over `android.media.MediaPlayer`; one preview at a time. */
class MediaPlayerVoiceRecordingPreview : VoiceRecordingPreview {

    private var player: MediaPlayer? = null
    private var current: File? = null

    override val playing: File? get() = current

    override fun play(file: File) {
        stop()
        val created = runCatching { MediaPlayer() }.getOrNull() ?: return
        try {
            created.setDataSource(file.absolutePath)
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            created.prepare()
            created.start()
        } catch (error: Exception) {
            // A player that failed to prepare/start still holds its decoder: release it before
            // giving up, or the next preview plays over a leaked one.
            runCatching { created.release() }
            return
        }
        player = created
        current = file
    }

    override fun stop() {
        player?.let { active ->
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.release() }
        }
        player = null
        current = null
    }
}
