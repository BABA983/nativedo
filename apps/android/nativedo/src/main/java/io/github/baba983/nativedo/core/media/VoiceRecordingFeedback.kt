package io.github.baba983.nativedo.core.media

/**
 * What the reader sees while holding the microphone. The host calls these; the screen decides how
 * to draw them, so the recording flow stays Telegram-shaped (a live panel, a timer, a hint) without
 * the host reaching into a specific screen's view tree.
 *
 * <p>The screen binds its own implementation: the forum composer's controller uses Telegram
 * bulletins, the chat its voice band, and the voice editor its own review state.</p>
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
