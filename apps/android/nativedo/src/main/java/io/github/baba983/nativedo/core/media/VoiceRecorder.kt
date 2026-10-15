package io.github.baba983.nativedo.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * A recorder this client owns: it captures the device microphone into a private cache file and hands
 * back a finished [VoiceRecording]. It never starts Telegram's own recording path - no `tgnet`, no
 * Telegram database, no `MediaController` - which is the whole point of the seam it sits behind.
 */
interface VoiceRecorder {

    /** Whether a capture is currently running. */
    val isRecording: Boolean

    /**
     * Starts capturing into a fresh private file. Returns false when the device refused to start
     * (the microphone is busy, permission was revoked between the check and the start); the caller
     * then shows the failure rather than pretending a recording exists.
     */
    fun start(): Boolean

    /** Stops and returns the finished file, or null when nothing usable was captured (deleted). */
    fun stop(): VoiceRecording?

    /** Stops and deletes the file: the recording was cancelled or too short to keep. */
    fun abort()

    /** Releases the device recorder; safe to call when nothing is running. */
    fun release()
}

/**
 * [VoiceRecorder] over `android.media.MediaRecorder`. One instance owns one recorder at a time and
 * is used from a single (UI) thread, which is how `MediaRecorder` requires it.
 */
class AndroidVoiceRecorder(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override val isRecording: Boolean get() = recorder != null

    override fun start(): Boolean {
        if (recorder != null) return false
        val target = VoiceRecordingFiles.newFile(context, nowMillis())
        val created = runCatching { newRecorder() }.getOrNull() ?: run {
            target.delete()
            return false
        }
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            created.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
        } catch (error: Exception) {
            // A recorder that failed to prepare/start still owns the microphone: release it before
            // reporting the refusal, or the device stays busy for the next capture.
            runCatching { created.release() }
            target.delete()
            return false
        }
        recorder = created
        file = target
        startedAt = nowMillis()
        return true
    }

    override fun stop(): VoiceRecording? {
        val active = recorder ?: return null
        val target = file
        recorder = null
        file = null
        val stopped = try {
            active.stop()
            true
        } catch (error: Exception) {
            // stop() throws when too little was captured to write a container; that is not a crash.
            false
        } finally {
            runCatching { active.release() }
        }
        if (target == null) return null
        if (!stopped || target.length() <= 0L) {
            target.delete()
            return null
        }
        return VoiceRecording(
            file = target,
            name = target.name,
            mimeType = VOICE_RECORDING_MIME_TYPE,
            durationMillis = (nowMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    override fun abort() {
        val active = recorder
        val target = file
        recorder = null
        file = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        target?.delete()
    }

    override fun release() {
        recorder?.let { runCatching { it.release() } }
        recorder = null
        file = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val AUDIO_BIT_RATE = 96_000
        const val AUDIO_SAMPLE_RATE = 44_100
    }
}
