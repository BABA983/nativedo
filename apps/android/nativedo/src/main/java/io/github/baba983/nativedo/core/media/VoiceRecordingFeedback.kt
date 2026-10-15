package io.github.baba983.nativedo.core.media

import android.content.Context
import android.widget.Toast

/**
 * What the reader sees while holding the microphone. The host calls these; the screen decides how
 * to draw them, so the recording flow stays Telegram-shaped (a live panel, a timer, a hint) without
 * the host reaching into a specific screen's view tree.
 *
 * <p>The default implementation is transient toasts, enough for the flow to be usable on its own;
 * a screen that wants the upstream-shaped recorder panel binds its own.</p>
 */
interface VoiceRecordingFeedback {

    /** A short tap on the microphone: nothing was recorded, tell the reader to hold. */
    fun onHoldHint()

    /** A capture started (the hold passed its threshold and the recorder opened). */
    fun onRecordingStarted()

    /** How long the live capture has run; called periodically while recording, for a timer. */
    fun onRecordingTick(durationMillis: Long)

    /** The capture finished and is being handed to the upload path. */
    fun onRecordingFinished()

    /** The capture was discarded: slid to cancel, cancelled by the system, or too short to keep. */
    fun onRecordingDiscarded()

    /** The recorder could not start or produced nothing usable. */
    fun onRecordingFailed(message: String?)

    /** The microphone permission is not granted; the request has been made. */
    fun onPermissionRequired()
}

/**
 * The default [VoiceRecordingFeedback]: transient toasts. A tick is deliberately silent - a timer
 * belongs in a screen's own panel, not in a stream of toasts.
 */
class ToastVoiceRecordingFeedback(private val context: Context) : VoiceRecordingFeedback {

    override fun onHoldHint() = toast("按住录音")

    override fun onRecordingStarted() = Unit

    override fun onRecordingTick(durationMillis: Long) = Unit

    override fun onRecordingFinished() = Unit

    override fun onRecordingDiscarded() = toast("已取消录音")

    override fun onRecordingFailed(message: String?) = toast(message ?: "无法开始录音")

    override fun onPermissionRequired() = toast("需要麦克风权限")

    private fun toast(text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
