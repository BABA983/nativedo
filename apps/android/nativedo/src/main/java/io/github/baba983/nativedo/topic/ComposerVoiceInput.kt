package io.github.baba983.nativedo.topic

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.baba983.nativedo.core.media.AndroidVoiceRecorder
import io.github.baba983.nativedo.core.media.HoldToRecordGesture
import io.github.baba983.nativedo.core.media.ToastVoiceRecordingFeedback
import io.github.baba983.nativedo.core.media.VoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.ChatActivityEnterView
import java.lang.ref.WeakReference

/**
 * The recording host bound to one composer: this client's own hold-to-record microphone, over the
 * upstream composer's visible mic affordance.
 *
 * <p>Upstream's gesture would start Telegram's own recorder (and its camera for a video note).
 * Neither belongs here, so this host is bound to the composer's own [DiscourseComposerDelegate] -
 * which implements [NativeDoHooks.ComposerRecordHost] and forwards to this one host - and upstream's
 * single touch call site hands the live delegate in. While bound it owns the whole gesture - a hold
 * records, a release completes, a slide or a system cancel discards, and a short tap only hints -
 * and Telegram's recorder is never reached. The binding is instance-bound, not a process-wide table,
 * and [onDestroy] releases only this host's own binding, so a replacement host is never evicted by a
 * stale one's teardown.</p>
 *
 * <p>Ownership rules that the design turns on:</p>
 * <ul>
 *   <li><b>No stale append.</b> A capture carries the composer it was started for; if the host was
 *       disposed, or the composer it was bound to is gone or replaced, the finished file is deleted
 *       and the callback never runs - a late recording cannot land in a different draft.</li>
 *   <li><b>No leftover audio.</b> A cancelled, too-short or interrupted capture deletes its private
 *       cache file; nothing is left in the cache after [onDestroy].</li>
 *   <li><b>No Telegram service.</b> Recording uses `android.media.MediaRecorder` only; no tgnet, no
 *       Telegram database, no `MediaController`.</li>
 *   <li><b>No busy capture.</b> [captureAllowed] is the screen's own gate - false while a send, an
 *       upload, a permission request or a recovery is in flight - so a hold never starts a second
 *       capture or races an in-flight write.</li>
 * </ul>
 */
class ComposerVoiceInput internal constructor(
    private val activity: Activity,
    composer: ChatActivityEnterView,
    private val delegate: DiscourseComposerDelegate,
    private val feedback: VoiceRecordingFeedback,
    private val recorderFactory: (Context) -> VoiceRecorder,
    private val onRecorded: (VoiceRecording) -> Unit,
    private val captureAllowed: () -> Boolean,
) : NativeDoHooks.ComposerRecordHost {

    private val composerRef = WeakReference(composer)
    private val gesture = HoldToRecordGesture()
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: VoiceRecorder? = null

    /** The composer a live capture was started for; a delivery only counts while it still holds. */
    private var recordedFor: ChatActivityEnterView? = null
    private var downX = 0f
    private var holdTimer: Runnable? = null
    private var tickTimer: Runnable? = null
    private var awaitingPermission = false
    private var disposed = false

    init {
        delegate.setRecordHost(this)
    }

    /** True while a capture is live. */
    val isRecording: Boolean get() = recorder != null

    override fun onRecordTouch(event: MotionEvent): Boolean {
        if (disposed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp()
            MotionEvent.ACTION_CANCEL -> onCancel()
        }
        // The host owns the whole gesture while it is bound, so upstream's Telegram recording (and
        // its video-note switch) never runs. Video is not offered; it is not pretended either.
        return true
    }

    /**
     * The microphone permission request this host made has been answered. Until this is called, a
     * second hold does not raise a second request: a permission that is still being decided is not
     * a permission to record.
     */
    fun onPermissionResult() {
        awaitingPermission = false
    }

    /**
     * An accessible alternative to the hold gesture: one activation starts a capture, the next
     * finishes and delivers it. The microphone is a touch-only affordance otherwise, which would
     * leave a screen-reader user with no way to record at all; a tap-to-start / tap-to-finish path
     * is the same flow without the hold. A tap that is too short to keep is discarded exactly as a
     * short hold is.
     */
    fun toggleFromAccessibility() {
        if (disposed) return
        if (isRecording) {
            cancelHoldTimer()
            if (gesture.onUp(now()) == HoldToRecordGesture.Event.Complete) {
                completeActive()
            } else {
                discardActive()
            }
            return
        }
        if (!captureAllowed() || awaitingPermission) return
        if (!hasRecordPermission()) {
            awaitingPermission = true
            requestRecordPermission()
            feedback.onPermissionRequired()
            return
        }
        if (gesture.onDown(now()) != HoldToRecordGesture.Event.Armed) return
        beginRecording()
    }

    /**
     * Drops everything a capture could still deliver: the armed hold, a live recorder, and the
     * pending take this host would hand to the screen. A screen calls this on pause, on destroy and
     * on any session change, so a late recording can never land in a different draft.
     */
    fun cancelCapture() {
        cancelHoldTimer()
        cancelTickTimer()
        gesture.onCancel()
        discardActive(silent = true)
    }

    /** The owning screen going to the background: a live capture is discarded, never delivered. */
    fun onPause() = cancelCapture()

    /** The owning screen going away: nothing survives, and the binding is released. */
    fun onDestroy() {
        disposed = true
        cancelCapture()
        recorder?.release()
        recorder = null
        recordedFor = null
        // Only clear the binding if it is still ours: a replacement host must not be evicted by the
        // old one's teardown.
        if (delegate.getRecordHost() === this) {
            delegate.setRecordHost(null)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Gesture
    // ---------------------------------------------------------------------------------------------

    private fun onDown(event: MotionEvent) {
        if (!captureAllowed() || awaitingPermission) return
        if (!hasRecordPermission()) {
            awaitingPermission = true
            requestRecordPermission()
            feedback.onPermissionRequired()
            return
        }
        if (gesture.onDown(now()) != HoldToRecordGesture.Event.Armed) return
        downX = event.x
        val runnable = Runnable {
            holdTimer = null
            beginRecording()
        }
        holdTimer = runnable
        handler.postDelayed(runnable, gesture.holdThreshold)
    }

    private fun onMove(event: MotionEvent) {
        val cancelDistance = AndroidUtilities.dp(SLIDE_CANCEL_DISTANCE_DP).toFloat().coerceAtLeast(1f)
        val remaining = ((event.x - downX + cancelDistance) / cancelDistance).coerceIn(0f, 1f)
        if (gesture.onMove(remaining) == HoldToRecordGesture.Event.Discard) {
            discardActive()
        }
    }

    private fun onUp() {
        cancelHoldTimer()
        when (gesture.onUp(now())) {
            HoldToRecordGesture.Event.ShortTap -> feedback.onHoldHint()
            HoldToRecordGesture.Event.Complete -> completeActive()
            HoldToRecordGesture.Event.Discard -> discardActive()
            else -> Unit
        }
    }

    private fun onCancel() {
        cancelHoldTimer()
        if (gesture.onCancel() == HoldToRecordGesture.Event.Discard) {
            discardActive()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Recorder
    // ---------------------------------------------------------------------------------------------

    private fun beginRecording() {
        if (gesture.onHoldElapsed(now()) != HoldToRecordGesture.Event.BeginRecording) return
        val target = recorderFactory(activity)
        if (!target.start()) {
            target.release()
            gesture.onCancel()
            feedback.onRecordingFailed(null)
            return
        }
        recorder = target
        recordedFor = composerRef.get()
        feedback.onRecordingStarted()
        scheduleTick()
    }

    private fun completeActive() {
        cancelTickTimer()
        val target = recorder ?: return
        recorder = null
        val finished = target.stop()
        if (finished == null) {
            recordedFor = null
            feedback.onRecordingFailed(null)
            return
        }
        val bound = composerRef.get()
        if (disposed || bound == null || bound !== recordedFor) {
            // Stale: the screen is gone or the composer was replaced, so this recording belongs to no
            // live draft. Delete it rather than let it surface somewhere else.
            finished.file.delete()
            recordedFor = null
            return
        }
        recordedFor = null
        feedback.onRecordingFinished()
        onRecorded(finished)
    }

    private fun discardActive(silent: Boolean = false) {
        cancelTickTimer()
        val target = recorder ?: run {
            recordedFor = null
            return
        }
        recorder = null
        target.abort()
        recordedFor = null
        if (!silent) {
            feedback.onRecordingDiscarded()
        }
    }

    private fun scheduleTick() {
        val runnable = object : Runnable {
            override fun run() {
                if (recorder == null) return
                feedback.onRecordingTick(gesture.elapsedMillis(now()))
                handler.postDelayed(this, TICK_INTERVAL_MILLIS)
            }
        }
        tickTimer = runnable
        handler.postDelayed(runnable, TICK_INTERVAL_MILLIS)
    }

    private fun cancelHoldTimer() {
        holdTimer?.let { handler.removeCallbacks(it) }
        holdTimer = null
    }

    private fun cancelTickTimer() {
        tickTimer?.let { handler.removeCallbacks(it) }
        tickTimer = null
    }

    // ---------------------------------------------------------------------------------------------
    // Permission
    // ---------------------------------------------------------------------------------------------

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_PERMISSION_REQUEST_CODE,
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    companion object {
        /** How far left the finger must travel to cancel; the gesture's own fraction is applied to it. */
        private const val SLIDE_CANCEL_DISTANCE_DP = 72f

        /** How often the live duration is reported to [VoiceRecordingFeedback.onRecordingTick]. */
        private const val TICK_INTERVAL_MILLIS = 200L

        /** This client's own request code for the microphone permission. */
        const val RECORD_PERMISSION_REQUEST_CODE = 0x4E44

        /**
         * Binds a hold-to-record microphone to [composer], whose own [delegate] carries the gesture
         * in. Every finished recording is handed to [onRecorded] as a private local `.m4a`. The
         * caller keeps the returned instance and forwards [ComposerVoiceInput.onPause] /
         * [ComposerVoiceInput.onDestroy] from its own lifecycle, plus
         * [ComposerVoiceInput.onPermissionResult] from `onRequestPermissionsResult`.
         */
        @JvmStatic
        fun bind(
            activity: Activity,
            composer: ChatActivityEnterView,
            delegate: DiscourseComposerDelegate,
            onRecorded: (VoiceRecording) -> Unit,
            captureAllowed: () -> Boolean = { true },
        ): ComposerVoiceInput = bind(
            activity, composer, delegate, ToastVoiceRecordingFeedback(activity), onRecorded, captureAllowed,
        )

        /**
         * Binds with a caller-supplied [feedback], so a screen can draw its own recorder panel and
         * timer instead of the default toasts, and its own [captureAllowed] gate.
         */
        @JvmStatic
        fun bind(
            activity: Activity,
            composer: ChatActivityEnterView,
            delegate: DiscourseComposerDelegate,
            feedback: VoiceRecordingFeedback,
            onRecorded: (VoiceRecording) -> Unit,
            captureAllowed: () -> Boolean = { true },
        ): ComposerVoiceInput = ComposerVoiceInput(
            activity = activity,
            composer = composer,
            delegate = delegate,
            feedback = feedback,
            recorderFactory = { AndroidVoiceRecorder(it) },
            onRecorded = onRecorded,
            captureAllowed = captureAllowed,
        )
    }
}
