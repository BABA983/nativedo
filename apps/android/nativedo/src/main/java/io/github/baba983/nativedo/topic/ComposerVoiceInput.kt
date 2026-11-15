// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import io.github.baba983.nativedo.core.media.AndroidVoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecordedAudioPlayerView
import java.lang.ref.WeakReference

/** Instance-bound recording service; Telegram owns the gesture, controls and editor. */
class ComposerVoiceInput internal constructor(
    private val activity: Activity,
    composer: ChatActivityEnterView,
    private val delegate: DiscourseComposerDelegate,
    private val feedback: VoiceRecordingFeedback,
    private val recorderFactory: (Context) -> VoiceRecorder,
    private val onRecorded: (VoiceRecording) -> Unit,
    private val captureAllowed: () -> Boolean,
    private val onCaptureOpened: () -> Unit = {},
    private val useVoiceEditor: Boolean = true,
    private val onEditorCreated: (RecordedAudioPlayerView, Runnable) -> Unit = { _, _ -> },
    private val onSendRequested: () -> Unit = {},
    private val onResumeRequested: (() -> Unit)? = null,
    private val onTrimRequested: (Long, Long, Runnable) -> Unit = { _, _, done -> done.run() },
) : NativeDoHooks.ComposerRecordHost {
    private val composerRef = WeakReference(composer)
    private var recorder: VoiceRecorder? = null
    private var recordedFor: ChatActivityEnterView? = null
    private var awaitingPermission = false
    private var disposed = false
    private var paused = false
    private var continuing = false
    private val amplitudes = ArrayList<Byte>()

    val isRecording: Boolean get() = recorder != null

    override fun onRecordTouch(event: MotionEvent): Boolean =
        recorder == null && (disposed || !captureAllowed())

    override fun onComposerRecordStart(account: Int, guid: Int): Boolean {
        if (!openSegment()) return false
        continuing = false
        onCaptureOpened() // Only a new take can acquire an account; resume never re-owns it.
        return true
    }

    private fun openSegment(): Boolean {
        if (disposed || !captureAllowed() || recorder != null) return false
        val target = recorderFactory(activity)
        if (!target.start()) {
            target.release()
            feedback.onRecordingFailed(null)
            return false
        }
        recorder = target
        recordedFor = composerRef.get()
        paused = false
        amplitudes.clear()
        AndroidUtilities.runOnUIThread(amplitudeTick, 50)
        return true
    }

    private val amplitudeTick = object : Runnable {
        override fun run() {
            val target = recorder ?: return
            val amplitude = target.amplitude.coerceIn(0, 32767)
            amplitudes.add((amplitude * 127 / 32767).toByte())
            composerRef.get()?.recordCircle?.setAmplitude(amplitude.toDouble())
            AndroidUtilities.runOnUIThread(this, 50)
        }
    }

    override fun onComposerRecordStop(send: Int) {
        if (send == 0) discardActive() else if (recorder != null) completeActive()
        else if (paused && useVoiceEditor) onSendRequested()
    }

    override fun onComposerRecordTogglePause() {
        if (recorder != null) {
            completeActive()
            if (!paused) exitComposerRecording()
        } else if (paused) {
            onResumeRequested?.invoke() ?: resumeCapture()
        }
    }

    fun resumeCapture() {
        if (!paused || !openSegment()) return
        continuing = true
        composerRef.get()?.let {
            it.didReceivedNotification(NotificationCenter.recordResumed, 0)
        }
    }

    override fun isComposerRecordingPaused(): Boolean = paused

    override fun onComposerVoiceEditor(editor: RecordedAudioPlayerView, clear: Runnable) =
        onEditorCreated(editor, clear)

    override fun onComposerVoiceSend(): Boolean {
        if (!useVoiceEditor || (!paused && recorder == null && composerRef.get()?.hasAudioToSend() != true)) return false
        if (recorder != null) completeActive() else onSendRequested()
        return true
    }

    override fun onComposerVoiceTrim(start: Long, end: Long, done: Runnable) =
        onTrimRequested(start, end, done)

    override fun onComposerRecordPermission(activity: Activity): Boolean {
        if (!disposed && !awaitingPermission) {
            awaitingPermission = true
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_PERMISSION_REQUEST_CODE)
            feedback.onPermissionRequired()
        }
        return true
    }

    override fun mapRecordInterfaceState(state: Int): Int =
        if (state == ChatActivityEnterView.RECORD_STATE_SENDING) {
            if (useVoiceEditor && paused) ChatActivityEnterView.RECORD_STATE_PREPARING
            else ChatActivityEnterView.RECORD_STATE_CANCEL
        } else state

    fun onPermissionResult() { awaitingPermission = false }

    fun finishReview() { paused = false }

    fun toggleFromAccessibility() {
        if (disposed) return
        val composer = composerRef.get() ?: return
        val mic = composer.audioVideoButtonContainer ?: return
        if (!composer.isRecordingAudioVideo && !captureAllowed()) return
        val action = if (composer.isRecordingAudioVideo) MotionEvent.ACTION_UP else MotionEvent.ACTION_DOWN
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, mic.width / 2f, mic.height / 2f, 0).let {
            try { mic.dispatchTouchEvent(it) } finally { it.recycle() }
        }
    }

    fun cancelCapture() {
        discardActive()
        exitComposerRecording()
    }

    fun onPause() { if (recorder != null) cancelCapture() }

    fun onDestroy() {
        disposed = true
        cancelCapture()
        if (delegate.recordHost === this) delegate.setRecordHost(null)
    }

    private fun completeActive() {
        val target = recorder ?: return
        recorder = null
        AndroidUtilities.cancelRunOnUIThread(amplitudeTick)
        val finished = target.stop()
        val bound = composerRef.get()
        if (disposed || bound == null || bound !== recordedFor || (!continuing && (finished == null || finished.durationMillis < 700))) {
            finished?.file?.delete()
            feedback.onRecordingDiscarded()
            return
        }
        paused = useVoiceEditor
        if (finished == null) {
            feedback.onRecordingFailed("续录未保存，之前的录音已保留")
            return
        }
        feedback.onRecordingFinished()
        onRecorded(if (finished.waveform.isNotEmpty()) finished else finished.copy(waveform = amplitudes.toByteArray()))
    }

    private fun discardActive() {
        val hadTake = recorder != null || paused
        recorder?.abort()
        recorder = null
        recordedFor = null
        paused = false
        AndroidUtilities.cancelRunOnUIThread(amplitudeTick)
        if (hadTake) feedback.onRecordingDiscarded()
    }

    private fun exitComposerRecording() {
        composerRef.get()?.let { if (it.isRecordingAudioVideo) it.cancelRecordingAudioVideo() }
    }

    companion object {
        const val RECORD_PERMISSION_REQUEST_CODE = 0x4E44

        /** Chat retains its existing review/upload contract rather than adopting the forum editor. */
        @JvmStatic
        fun bind(activity: Activity, composer: ChatActivityEnterView, delegate: DiscourseComposerDelegate,
                 feedback: VoiceRecordingFeedback, onRecorded: (VoiceRecording) -> Unit,
                 captureAllowed: () -> Boolean = { true }): ComposerVoiceInput =
            ComposerVoiceInput(activity, composer, delegate, feedback, { AndroidVoiceRecorder(it) },
                onRecorded, captureAllowed, useVoiceEditor = false)
    }

    init { delegate.setRecordHost(this) }
}
