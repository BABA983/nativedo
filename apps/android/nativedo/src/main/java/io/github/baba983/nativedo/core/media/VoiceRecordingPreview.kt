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

    /** The live playback position of the clip, in milliseconds, or 0 when nothing is playing. */
    fun positionMillis(): Int = 0

    /** The duration of the clip being previewed, in milliseconds, or 0. */
    fun durationMillis(): Int = 0

    /**
     * Notified when a preview reaches its own end (or errors), so a screen can put its transport
     * back to play. A caller's own [stop] does not notify; the default listener does nothing.
     */
    fun setOnPlaybackFinished(listener: (() -> Unit)?) {}
}

/** [VoiceRecordingPreview] over `android.media.MediaPlayer`; one preview at a time. */
class MediaPlayerVoiceRecordingPreview : VoiceRecordingPreview {

    private var player: MediaPlayer? = null
    private var current: File? = null
    private var onPlaybackFinished: (() -> Unit)? = null

    override val playing: File? get() = current

    override fun positionMillis(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    override fun durationMillis(): Int = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    override fun setOnPlaybackFinished(listener: (() -> Unit)?) {
        onPlaybackFinished = listener
    }

    override fun play(file: File) {
        stop()
        val created = runCatching { MediaPlayer() }.getOrNull() ?: return
        try {
            created.setDataSource(file.absolutePath)
            created.setOnCompletionListener {
                stop()
                onPlaybackFinished?.invoke()
            }
            created.setOnErrorListener { _, _, _ ->
                stop()
                onPlaybackFinished?.invoke()
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
