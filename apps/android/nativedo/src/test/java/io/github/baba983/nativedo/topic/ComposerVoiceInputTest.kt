package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import io.github.baba983.nativedo.core.media.VoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import io.github.baba983.nativedo.hooks.NativeDoHooks
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.SizeNotifierFrameLayout

/**
 * The recording service bound to a composer's own delegate.
 *
 * <p>Upstream owns the microphone gesture now, so what is pinned here is the boundary this client
 * replaces: the touch is left to upstream, and the recorder service upstream would hand to
 * `MediaController` is served by this host instead. None of it needs a device microphone (the
 * recorder is a fake, the clock is Robolectric's).</p>
 *
 * <p>The failure modes:</p>
 * <ul>
 *   <li>a hold starts upstream's own recording while this host owns the bytes - the host must not
 *       consume the touch, but a live capture must always be releasable even if the screen turns
 *       busy mid-hold, or the microphone would be left open;</li>
 *   <li>a busy screen still refuses the gesture, so a second capture cannot race an upload, and a
 *       repeat start while a capture is already live opens no second recorder;</li>
 *   <li>the lock's own pause control ends the take into review rather than sitting inert (an `.m4a`
 *       recorder cannot truly pause);</li>
 *   <li>the `MediaController` fallback must never run while a host is bound (Telegram's recorder
 *       must be unreachable), and must run unchanged when none is;</li>
 *   <li>a release delivers the take, a cancel discards it, and a too-short take is deleted, not
 *       delivered;</li>
 *   <li>the permission request is this client's, raised once, and released by its own answer;</li>
 *   <li>upstream's send-state exit is mapped to a plain exit, so no send animation fights the
 *       screen's review band;</li>
 *   <li>the binding is instance-bound: a teardown releases only its own binding.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class ComposerVoiceInputTest {

    private val harness = UpstreamUiHarness()

    private lateinit var composer: ChatActivityEnterView
    private lateinit var delegate: TestDelegate
    private lateinit var feedback: RecordingFeedback
    private lateinit var recorder: FakeRecorder
    private var allowed = true
    private var delivered = mutableListOf<VoiceRecording>()

    @Before
    fun setUp() {
        harness.bootViews()
        grantRecordPermission(true)
        val parent = SizeNotifierFrameLayout(harness.activity)
        harness.content.addView(parent)
        composer = ChatActivityEnterView(harness.activity, parent, null, true, null)
        delegate = TestDelegate()
        composer.setDelegate(delegate)
        feedback = RecordingFeedback()
        recorder = FakeRecorder(File(harness.activity.cacheDir, "voice-test.m4a").apply { writeBytes(ByteArray(16)) })
        allowed = true
        delivered = mutableListOf()
    }

    private fun grantRecordPermission(grant: Boolean) {
        val shadow = org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
        if (grant) {
            shadow.grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        } else {
            shadow.denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun bind(): ComposerVoiceInput = ComposerVoiceInput(
        activity = harness.activity,
        composer = composer,
        delegate = delegate,
        feedback = feedback,
        recorderFactory = { recorder },
        onRecorded = { delivered += it },
        captureAllowed = { allowed },
    )

    @Test
    fun theGestureIsUpstreamsWhileACaptureMayStart() {
        val input = bind()
        assertSame("the host is bound to the composer's own delegate", input, delegate.getRecordHost())
        // Upstream owns the whole gesture (RecordCircle, slide-to-cancel, lock, timer), so the host
        // answers "not mine" and upstream's own handling runs.
        assertFalse("a down is left to upstream", touch(MotionEvent.ACTION_DOWN, 200f))
        assertFalse("so is a move", touch(MotionEvent.ACTION_MOVE, 200f))
        assertFalse("so is an up", touch(MotionEvent.ACTION_UP, 200f))
    }

    @Test
    fun aBusyScreenRefusesTheGestureSoNoSecondCaptureCanRace() {
        allowed = false
        bind()
        assertTrue("a hold is consumed while an upload is in flight", touch(MotionEvent.ACTION_DOWN, 200f))
        assertTrue("and its whole gesture stays consumed", touch(MotionEvent.ACTION_MOVE, 200f))
        assertTrue(touch(MotionEvent.ACTION_UP, 200f))
    }

    /**
     * Failure mode: a session change mid-hold turns [captureAllowed] false. If the release were
     * swallowed on that basis, upstream would never stop the recorder and the microphone would stay
     * open with no way to close it.
     */
    @Test
    fun aLiveCaptureAlwaysEndsEvenIfCaptureAllowedTurnsFalseMidHold() {
        val input = bind()
        assertFalse("the hold starts a capture", touch(MotionEvent.ACTION_DOWN, 200f))
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        assertTrue("a capture is live", input.isRecording)

        allowed = false // the account went away while the finger is still down
        assertFalse("the release still reaches upstream", touch(MotionEvent.ACTION_UP, 200f))

        NativeDoHooks.stopComposerRecording(delegate, 0) {}
        assertFalse("the recorder is stopped, not left holding the microphone", input.isRecording)
    }

    /**
     * Failure mode: a second start would open a second `MediaRecorder` over the first, leaking the
     * device microphone with no callsite left to release it.
     */
    @Test
    fun aRepeatStartWhileACaptureIsLiveIsRefused() {
        bind()
        assertTrue("the first start is served", NativeDoHooks.startComposerRecording(delegate, 0, 0) {})
        assertEquals("one recorder opened", 1, recorder.starts)
        assertFalse("a second start is refused", NativeDoHooks.startComposerRecording(delegate, 0, 0) {})
        assertEquals("no second recorder is opened", 1, recorder.starts)
    }

    /** Failure mode: pause discarded resumability or a resume re-latched the take's account. */
    @Test
    fun theLockPauseControlKeepsAResumableTakeInsteadOfEndingTheSession() {
        val input = bind()
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        var fallbackRan = false
        NativeDoHooks.toggleComposerRecordingPause(delegate) { fallbackRan = true }
        assertFalse("upstream's own pause path is not run behind the host", fallbackRan)
        assertEquals("the finished take is delivered to review", 1, delivered.size)
        assertEquals("and it is the recorder's own file", recorder.file, delivered.single().file)
        assertTrue("pause remains resumable", input.isComposerRecordingPaused())
        NativeDoHooks.toggleComposerRecordingPause(delegate) { fallbackRan = true }
        assertTrue("resume opens a new playable segment", input.isRecording)
        assertFalse("no longer paused", input.isComposerRecordingPaused())
        assertEquals("the new segment uses our recorder, not Telegram's", 2, recorder.starts)
        input.onDestroy()
    }

    /** A sub-700ms continuation is not a new take: rejecting it would lose earlier speech. */
    @Test
    fun aShortContinuationIsKeptWithThePreviousTake() {
        val input = bind()
        input.onComposerRecordStart(0, 0)
        input.onComposerRecordTogglePause()
        input.onComposerRecordTogglePause()
        recorder.durationMillis = 200L
        input.onComposerRecordTogglePause()
        assertEquals("both segments are handed to the same editor", 2, delivered.size)
        assertEquals("no earlier speech is discarded", 0, feedback.discards)
        input.onDestroy()
    }

    @Test
    fun theStartBoundaryOpensThisClientsRecorderAndSkipsMediaController() {
        val input = bind()
        var fallbackRan = false
        assertTrue(
            "the host owns the capture",
            NativeDoHooks.startComposerRecording(delegate, 0, 0) { fallbackRan = true },
        )
        assertFalse("Telegram's recorder is never started behind the host", fallbackRan)
        assertEquals("this client's recorder started", 1, recorder.starts)
        assertTrue("a capture is live", input.isRecording)
    }

    @Test
    fun aReleaseDeliversTheTakeAndACancelDiscardsIt() {
        bind()
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        NativeDoHooks.stopComposerRecording(delegate, 1) {}
        assertEquals("a release hands the finished take on", 1, delivered.size)
        assertEquals("the take is the recorder's own file", recorder.file, delivered.single().file)

        // A fresh capture cancelled by the reader is discarded, never delivered.
        delivered = mutableListOf()
        bind()
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        NativeDoHooks.stopComposerRecording(delegate, 0) {}
        assertTrue("a cancel delivers nothing", delivered.isEmpty())
        assertEquals("and the recorder is aborted", 1, recorder.aborts)
        assertTrue("the discard was reported", feedback.discards >= 1)
    }

    @Test
    fun aTooShortTakeIsDeletedNotDelivered() {
        recorder = FakeRecorder(File(harness.activity.cacheDir, "voice-short.m4a").apply { writeBytes(ByteArray(16)) }, durationMillis = 200L)
        bind()
        val file = recorder.file
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        NativeDoHooks.stopComposerRecording(delegate, 1) {}
        assertTrue("a take under the minimum is never delivered", delivered.isEmpty())
        assertFalse("and its private file goes with it", file.exists())
    }

    @Test
    fun anUnboundDelegateRunsUpstreamsOwnRecording() {
        // A genuine upstream delegate, not NativeDo's recorder host with its binding removed.
        val plain = java.lang.reflect.Proxy.newProxyInstance(
            ChatActivityEnterView.ChatActivityEnterViewDelegate::class.java.classLoader,
            arrayOf(ChatActivityEnterView.ChatActivityEnterViewDelegate::class.java),
        ) { _, _, _ -> null } as ChatActivityEnterView.ChatActivityEnterViewDelegate
        var fallbackRan = false
        assertTrue(
            "the composer may start its own recording",
            NativeDoHooks.startComposerRecording(plain, 0, 0) { fallbackRan = true },
        )
        assertTrue("upstream's own recorder is what runs", fallbackRan)
    }

    @Test
    fun aMissingPermissionIsRaisedOnceByThisClientAndReleasedByItsAnswer() {
        grantRecordPermission(false)
        val input = bind()
        var upstreamRequest = false
        NativeDoHooks.requestComposerRecordPermission(delegate, harness.activity) { upstreamRequest = true }
        assertFalse("upstream's own request code is not used for this client", upstreamRequest)
        assertEquals("this client raised its own request", 1, feedback.permissions)
        assertNull("a permission request never starts a capture", recorder.lastStart)

        // A second hold while the answer is still pending does not raise a second request.
        NativeDoHooks.requestComposerRecordPermission(delegate, harness.activity) { upstreamRequest = true }
        assertEquals("still one request", 1, feedback.permissions)

        // The screen's answer reaches the host for this client's code, releasing the pending request.
        input.onPermissionResult()
        NativeDoHooks.requestComposerRecordPermission(delegate, harness.activity) { upstreamRequest = true }
        assertEquals("the pending request was released", 2, feedback.permissions)
    }

    @Test
    fun theReleaseStateBecomesAPlainExitForAHostAndIsUntouchedForUpstream() {
        bind()
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        NativeDoHooks.stopComposerRecording(delegate, 1) {}
        assertEquals(
            "a forum release stays in upstream's review interface, not a send animation",
            ChatActivityEnterView.RECORD_STATE_PREPARING,
            NativeDoHooks.mapRecordInterfaceState(delegate, ChatActivityEnterView.RECORD_STATE_SENDING),
        )
        assertEquals(
            "an unrelated state is untouched",
            ChatActivityEnterView.RECORD_STATE_ENTER,
            NativeDoHooks.mapRecordInterfaceState(delegate, ChatActivityEnterView.RECORD_STATE_ENTER),
        )
        assertEquals(
            "a real Telegram delegate keeps upstream's own state",
            ChatActivityEnterView.RECORD_STATE_SENDING,
            NativeDoHooks.mapRecordInterfaceState(TestDelegate(), ChatActivityEnterView.RECORD_STATE_SENDING),
        )
    }

    @Test
    fun teardownReleasesOnlyItsOwnBinding() {
        val first = bind()
        assertSame(first, delegate.getRecordHost())
        val second = bind() // a replacement host takes the binding
        assertSame(second, delegate.getRecordHost())
        first.onDestroy() // the old host's teardown must not evict the new one
        assertSame("a stale teardown does not clear the live binding", second, delegate.getRecordHost())
        second.onDestroy()
        assertNull("the live host releases its own binding", delegate.getRecordHost())
    }

    @Test
    fun atenDestroyedHostDeliversNothingAndLeavesNoFile() {
        val input = bind()
        NativeDoHooks.startComposerRecording(delegate, 0, 0) {}
        val file = recorder.file
        input.onDestroy()
        NativeDoHooks.stopComposerRecording(delegate, 1) {}
        assertTrue("nothing survives the screen", delivered.isEmpty())
        assertFalse("the private file goes with the host", file.exists())
    }

    private fun touch(action: Int, x: Float): Boolean =
        delegate.onRecordTouch(MotionEvent.obtain(0, 0, action, x, 0f, 0))

    private class TestDelegate : DiscourseComposerDelegate() {
        override fun onMessageSend(
            message: CharSequence?,
            notify: Boolean,
            scheduleDate: Int,
            scheduleRepeatPeriod: Int,
            payStars: Long,
        ) = Unit

        override fun onTextChanged(text: CharSequence?, bigChange: Boolean, fromDraft: Boolean) = Unit
    }

    private class FakeRecorder(val file: File, var durationMillis: Long = 1_500L) : VoiceRecorder {
        var starts = 0
        var aborts = 0
        var lastStart: Boolean? = null
        override val isRecording: Boolean get() = starts > aborts
        override fun start(): Boolean {
            starts++
            lastStart = true
            return true
        }

        override fun stop(): VoiceRecording = VoiceRecording(file, file.name, "audio/mp4", durationMillis)
        override fun abort() {
            file.delete()
            aborts++
        }

        override fun release() = Unit
    }

    private class RecordingFeedback : VoiceRecordingFeedback {
        var holdHints = 0
        var started = 0
        var discards = 0
        var permissions = 0
        override fun onHoldHint() {
            holdHints++
        }

        override fun onRecordingStarted() {
            started++
        }

        override fun onRecordingTick(durationMillis: Long) = Unit
        override fun onRecordingFinished() = Unit
        override fun onRecordingDiscarded() {
            discards++
        }

        override fun onRecordingFailed(message: String?) = Unit
        override fun onPermissionRequired() {
            permissions++
        }
    }
}
