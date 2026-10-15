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
 * The hold-to-record microphone bound to a composer's own delegate.
 *
 * <p>What is pinned here, none of which needs a device microphone (the recorder is a fake, the
 * clock is Robolectric's):</p>
 * <ul>
 *   <li>a <em>tap</em> records nothing - the hold has to survive its threshold, and a release
 *       before it is only a hint;</li>
 *   <li>a hold released under the minimum is discarded, not delivered;</li>
 *   <li>a slide to cancel and a system {@code ACTION_CANCEL} both discard, and the release that
 *       follows delivers nothing;</li>
 *   <li>a busy screen ([captureAllowed] false) starts nothing;</li>
 *   <li>a missing microphone permission is asked for once and never starts a capture;</li>
 *   <li>the binding is instance-bound: the delegate carries the gesture in, and a teardown releases
 *       only its own binding.</li>
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
    private lateinit var delegate: DiscourseComposerDelegate
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
    fun theDelegateCarriesTheGestureInAndTheHostOwnsIt() {
        val input = bind()
        assertSame("the host is bound to the composer's own delegate", input, delegate.getRecordHost())
        assertTrue("a touch is owned while bound", touch(MotionEvent.ACTION_DOWN, 200f))
        // The host owns the whole gesture, so upstream's Telegram recorder (and its video note) never
        // runs: every action is consumed while a host is bound.
        assertTrue("a move is owned", touch(MotionEvent.ACTION_MOVE, 200f))
        assertTrue("a cancel is owned", touch(MotionEvent.ACTION_CANCEL, 200f))
        assertTrue("an up is owned", touch(MotionEvent.ACTION_UP, 200f))
    }

    @Test
    fun aTapRecordsNothingAndOnlyHints() {
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        // The finger is gone before the hold threshold: no recorder is ever built.
        touch(MotionEvent.ACTION_UP, 200f)
        harness.idleClock(300)
        assertNull("a tap never opens the recorder", recorder.lastStart)
        assertEquals("a tap is only a hold hint", 1, feedback.holdHints)
        assertTrue("nothing was delivered", delivered.isEmpty())
    }

    @Test
    fun aHoldRecordsAndADeliveryCarriesTheTake() {
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(200) // the hold threshold passes: the recorder opens
        assertTrue("the recorder started", recorder.starts == 1)
        assertTrue("the screen was told recording began", feedback.started == 1)
        harness.idleClock(800)
        touch(MotionEvent.ACTION_UP, 200f)
        assertEquals("a real hold is delivered", 1, delivered.size)
        assertEquals("the take is the recorder's own file", recorder.file, delivered.single().file)
    }

    @Test
    fun aHoldReleasedTooSoonIsDiscarded() {
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(200)
        touch(MotionEvent.ACTION_UP, 200f) // well under the minimum
        assertEquals("the short take is aborted, not stopped", 1, recorder.aborts)
        assertEquals("and never delivered", 0, delivered.size)
        assertTrue("the discard was reported", feedback.discards >= 1)
    }

    @Test
    fun slidingToCancelDiscardsAndTheReleaseDeliversNothing() {
        bind()
        touch(MotionEvent.ACTION_DOWN, 900f)
        harness.idleClock(200)
        touch(MotionEvent.ACTION_MOVE, 0f) // far left of the press
        assertEquals("the slide aborted the capture", 1, recorder.aborts)
        touch(MotionEvent.ACTION_UP, 0f)
        assertEquals("the release that follows delivers nothing", 0, delivered.size)
    }

    @Test
    fun aSystemCancelDiscards() {
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(200)
        touch(MotionEvent.ACTION_CANCEL, 200f)
        assertEquals("a stolen gesture aborts the capture", 1, recorder.aborts)
        assertEquals(0, delivered.size)
    }

    @Test
    fun aBusyScreenStartsNoCapture() {
        allowed = false
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(300)
        assertNull("no recorder while a send/upload/recovery is in flight", recorder.lastStart)
        assertEquals(0, delivered.size)
    }

    @Test
    fun aMissingPermissionIsAskedForOnceAndRecordsNothing() {
        grantRecordPermission(false)
        bind()
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(300)
        assertEquals("the permission was asked for", 1, feedback.permissions)
        assertNull("no capture without permission", recorder.lastStart)
        // A second hold while the request is still being decided does not raise a second request.
        touch(MotionEvent.ACTION_UP, 200f)
        touch(MotionEvent.ACTION_DOWN, 200f)
        harness.idleClock(300)
        assertEquals("still one request", 1, feedback.permissions)
        // The answer arrives; a later hold may record.
        val input = delegate.getRecordHost() as ComposerVoiceInput
        input.onPermissionResult()
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
    fun anUnboundDelegateLetsUpstreamRunUnchanged() {
        // A delegate with no host bound answers "not mine", so upstream's own gesture runs.
        assertFalse("a delegate with no host does not own the touch", delegate.onRecordTouch(down(200f)))
    }

    private fun touch(action: Int, x: Float): Boolean {
        val event = when (action) {
            MotionEvent.ACTION_UP -> MotionEvent.obtain(0, 0, action, x, 0f, 0)
            MotionEvent.ACTION_CANCEL -> MotionEvent.obtain(0, 0, action, x, 0f, 0)
            else -> MotionEvent.obtain(0, 0, action, x, 0f, 0)
        }
        return delegate.onRecordTouch(event)
    }

    private fun down(x: Float): MotionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, 0f, 0)

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

    private class FakeRecorder(val file: File) : VoiceRecorder {
        var starts = 0
        var aborts = 0
        var lastStart: Boolean? = null
        override val isRecording: Boolean get() = starts > aborts
        override fun start(): Boolean {
            starts++
            lastStart = true
            return true
        }

        override fun stop(): VoiceRecording? = VoiceRecording(file, file.name, "audio/mp4", 1_000L)
        override fun abort() {
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
