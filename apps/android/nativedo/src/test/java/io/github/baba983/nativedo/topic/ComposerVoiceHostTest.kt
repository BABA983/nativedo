package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.media.VoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecordedAudioPlayerView

/**
 * The forum screens' voice host, driven through the real reply / new-topic / private-message screens
 * and the site's real wire - not a manually mounted panel.
 *
 * <p>The failure modes pinned here, none of which needs a device microphone (a finished take is
 * injected the way a completed capture would produce it; the upload is the real client):</p>
 * <ul>
 *   <li>an empty reply, new topic and private message offer their own microphone (the button is
 *       visible while the field is empty) and a recording host is bound to the composer's own
 *       delegate, so upstream's Telegram recorder is unreachable;</li>
 *   <li>the finished take is uploaded as the site's `composer` kind and the answer becomes the
 *       site's own `![name|audio](...)` markdown in the field - never a message, never a download
 *       link, and never a posted topic;</li>
 *   <li>an upload the site refuses keeps the take and offers the retry, and inserts nothing;</li>
 *   <li>cancelling deletes the private file and clears the band;</li>
 *   <li>a session change drops the take instead of uploading a clip for the wrong reader.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram", "androidx.media3.decoder.ffmpeg"],
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
        ComposerVoiceHostTest.HostFfmpegLibrary::class,
    ],
)
internal class ComposerVoiceHostTest {

    /** The orb cannot load Android's FFmpeg JNI; platform playback is not a host-test claim. */
    @org.robolectric.annotation.Implements(className = "androidx.media3.decoder.ffmpeg.FfmpegLibrary", isInAndroidSdk = false)
    class HostFfmpegLibrary {
        companion object {
            @JvmStatic
            @org.robolectric.annotation.Implementation
            fun supportsFormat(mimeType: String): Boolean = false
        }
    }

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A native composer over a Discourse post.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("t/$TOPIC_ID/posts.json", """{"post_stream":{"posts":[]}}""")
        site.serveFixture("categories.json", "categories.json")
        site.serveJson("uploads.json", audioUploadResponse())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        harness.visibleFragment()?.onFragmentDestroy()
        site.stop()
    }

    @Test
    fun theEmptyReplyComposerOffersItsOwnMicrophoneAndBindsTheHost() {
        val fragment = openReply()
        val composer = composer(fragment)
        assertEquals(
            "an empty reply offers its microphone, not a hidden button",
            View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        val voice = fragment.voiceControllerForTest()
        assertNotNull("the reply screen bound a voice host", voice)
        assertNotNull(
            "the host is bound to the composer's own delegate",
            voice!!.boundRecordHostForTest(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theEmptyNewTopicOffersTheMicrophoneAndBindsTheHost() {
        val fragment = openNewTopic(NewTopicFragment.create())
        assertEquals(
            "an empty new topic offers its microphone",
            View.VISIBLE,
            composer(fragment).getSendButton()?.visibility,
        )
        assertNotNull("the new-topic screen bound a voice host", fragment.voiceControllerForTest())
        fragment.voiceControllerForTest()!!.attachPendingRecordingForTest(take())
        assertResumeAboveComposerWithIme(fragment, "new-topic-voice-review-ime")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theEmptyPrivateMessageOffersTheMicrophoneAndBindsTheHost() {
        val fragment = openNewTopic(NewTopicFragment.privateMessage("alice"))
        assertTrue("the form is in private-message mode", fragment.isPrivateMessageMode())
        assertEquals(
            "an empty private message offers its microphone",
            View.VISIBLE,
            composer(fragment).getSendButton()?.visibility,
        )
        assertNotNull("the private-message screen bound a voice host",
            fragment.voiceControllerForTest())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theMicrophoneGestureIsUpstreamsAndTheHostOwnsOnlyTheBytes() {
        grantRecordPermission(true)
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!
        val host = voice.boundRecordHostForTest()
        assertNotNull("a recording host is bound", host)
        // Upstream owns the gesture (RecordCircle, slide-to-cancel, lock, timer) and this host only
        // serves the recorder behind it, so it does not consume the touch.
        assertFalse(
            "the host leaves the microphone gesture to upstream",
            host!!.onRecordTouch(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 200f, 0f, 0)),
        )

        val recording = take()
        val fake = FakeRecorder(recording)
        voice.recorderFactoryForTest = { fake }

        // The hold: upstream opens its own recording interface, and this client's recorder is what
        // captures the bytes - Telegram's `MediaController` is never reached.
        micGesture(composer, MotionEvent.ACTION_DOWN)
        assertTrue("upstream entered its own recording interface", composer.isRecordingAudioVideo())
        assertEquals("this client's recorder started", 1, fake.starts)
        harness.idleClock(800)
        harness.renderApp("forum-voice-recording")

        // The release: upstream leaves its recording interface through its own transition and the
        // finished take becomes this screen's review band, played back locally before it is sent.
        micGesture(composer, MotionEvent.ACTION_UP)
        harness.idleClock(400)
        assertFalse("upstream left its recording interface", composer.isRecordingAudioVideo())
        assertTrue("release keeps upstream's voice editor", composer.hasAudioToSend())
        assertEquals(View.VISIBLE, editor(composer).visibility)
        assertFalse("no upload before explicit send", site.requestPaths.contains("uploads.json"))
        harness.renderApp("forum-voice-review")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aFinishedTakeUploadsAsTheComposerKindAndInsertsTheSitesAudioMarkdown() {
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!

        val take = take()
        voice.attachPendingRecordingForTest(take)
        assertTrue("a take becomes upstream's editor, never a post", composer.hasAudioToSend())
        assertEquals(View.VISIBLE, editor(composer).visibility)
        harness.renderApp("forum-voice-review")

        voice.sendForTest()
        harness.await("the accepted audio markup in the field") {
            composer.editField!!.text.toString() == "![voice-1.m4a|audio](upload://9d5a.m4a)\n"
        }

        val uploadBody = site.requestBodyFor("uploads.json")
        assertNotNull("the clip was uploaded", uploadBody)
        assertTrue("as the site's composer kind", uploadBody!!.contains("composer"))
        assertFalse("a forum upload is not the chat kind", uploadBody.contains("chat-composer"))

        assertEquals(
            "the field holds the site's own audio embed, not a download link",
            "![voice-1.m4a|audio](upload://9d5a.m4a)\n",
            composer.editField!!.text.toString(),
        )
        assertFalse("nothing was posted on its own", site.requestPaths.contains("posts.json"))
        assertFalse("the accepted take is spent", take.file.exists())
        assertNull("no take is kept after an accepted upload", voice.pendingForTest())
        assertFalse("the editor is away", composer.hasAudioToSend())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedUploadKeepsTheTakeAndInsertsNothing() {
        site.serveResponse("uploads.json") {
            MockResponse().setResponseCode(422).setBody("""{"errors":["不允许上传音频"]}""")
        }
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!

        val take = take()
        voice.attachPendingRecordingForTest(take)
        voice.sendForTest()
        // Do not jump to the banner's auto-hide timer while waiting for the network callback.
        for (attempt in 0 until 4000) {
            harness.idle(1)
            if (!voice.isUploadingForTest()) break
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1))
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
        assertFalse("the refused upload returns control to the editor", voice.isUploadingForTest())

        assertTrue("the take is kept for a retry", take.file.exists())
        assertNotNull("the take is still pending", voice.pendingForTest())
        assertTrue("the original editor stays available to retry or delete", composer.hasAudioToSend())
        // Hold the notice while the host drains animation frames; this checks layout, not expiry.
        val notice = org.telegram.ui.Components.Bulletin.getVisibleBulletin()!!
        notice.setDuration(-1)
        harness.renderApp("forum-voice-error")
        // A retry banner must not cover the waveform/send row or the separate resume target.
        val bulletin = notice.layout
        val bannerBounds = android.graphics.Rect()
        assertTrue("the retry banner is visible", bulletin.getGlobalVisibleRect(bannerBounds))
        val resumeBounds = android.graphics.Rect()
        composer.controlsView.accessibilityNodeProvider.createAccessibilityNodeInfo(2)!!
            .getBoundsInScreen(resumeBounds)
        assertTrue("retry does not cover the voice controls", bannerBounds.bottom <= resumeBounds.top)
        assertEquals("the field is untouched", "", composer.editField!!.text.toString())
        assertFalse("no post follows a refused upload", site.requestPaths.contains("posts.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun cancellingTheTakeDeletesItAndClearsTheBand() {
        grantRecordPermission(true)
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!

        val take = take()
        voice.recorderFactoryForTest = { FakeRecorder(take) }
        micGesture(composer, MotionEvent.ACTION_DOWN)
        harness.idleClock(800)
        micGesture(composer, MotionEvent.ACTION_UP)
        harness.idleClock(800)
        val delete = descendants(composer.recordedAudioPanel!!).single {
            it.contentDescription == org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Delete)
        }
        assertTrue("the upstream delete action is available", delete.performClick())

        assertNull("no take is kept", voice.pendingForTest())
        assertFalse("the private file goes with it", take.file.exists())
        assertFalse("the editor is away", composer.hasAudioToSend())
        assertEquals("the field is untouched", "", composer.editField!!.text.toString())
        assertFalse("nothing was uploaded", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSessionChangeDropsAStaleTakeInsteadOfUploadingIt() {
        val fragment = openReply()
        val voice = fragment.voiceControllerForTest()!!

        val take = take()
        voice.attachPendingRecordingForTest(take)

        // The account the take belongs to changes under it: the draft this recording was for is
        // gone, so the take must not be uploaded for the new account.
        val container = NativeDo.container(harness.context)
        container.sessionStore.save(
            SessionSnapshot("otheruser", "Other", "", System.currentTimeMillis()),
        )

        voice.sendForTest()
        harness.idleClock(300)

        assertFalse("a stale take is never uploaded", site.requestPaths.contains("uploads.json"))
        assertNull("the stale take is dropped", voice.pendingForTest())
        assertFalse("and its private file goes with it", take.file.exists())
        assertFalse("the editor is away", composer(fragment).hasAudioToSend())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun switchingAccountDuringCaptureCannotGiveTheFinishedTakeANewOwner() {
        grantRecordPermission(true)
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!
        voice.recorderFactoryForTest = { FakeRecorder(take()) }

        // The capture opens under this reader's session - the moment the take's owner is latched.
        micGesture(composer, MotionEvent.ACTION_DOWN)

        // The account changes under it before the finished take arrives.
        NativeDo.container(harness.context).sessionStore.save(
            SessionSnapshot("otheruser", "Other", "", System.currentTimeMillis()),
        )

        val take = take()
        voice.attachPendingRecordingForTest(take)

        assertNull("the old account's capture is not a new account's pending take", voice.pendingForTest())
        assertFalse("the rejected capture file is deleted", take.file.exists())
        assertFalse("the capture never reaches uploads", site.requestPaths.contains("uploads.json"))
        assertEquals("the new account's field stays empty", "", composer(fragment).editField!!.text.toString())
        harness.assertNoTelegramSideEffects()
    }

    /** Failure: resume replaces the first segment, or upload ignores the edited waveform region. */
    @Test
    fun pauseResumeJoinsTheTakeAndSendingUsesTheSelectedAudioOnly() {
        grantRecordPermission(true)
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!
        val first = realTake("voice-alpha.m4a", 1523)
        val second = realTake("voice-beta.m4a", 1023)
        val queue = java.util.ArrayDeque(listOf(first, second))
        voice.recorderFactoryForTest = { FakeRecorder(queue.removeFirst()) }
        micGesture(composer, MotionEvent.ACTION_DOWN)
        harness.idleClock(800)
        val host = voice.boundRecordHostForTest()!!
        host.onComposerRecordTogglePause()
        harness.idleClock(650)
        assertTrue("pause exposes the real editor", composer.hasAudioToSend())
        harness.renderApp("forum-voice-paused")
        val pageDown = descendants(fragment.fragmentView)
            .filterIsInstance<org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton>().single()
        assertFalse("page navigation must not cover the resume control", pageDown.isShown && pageDown.alpha > 0)
        val controls = composer.controlsView
        val pauseNode = controls.accessibilityNodeProvider.createAccessibilityNodeInfo(2)!!
        val pauseBounds = android.graphics.Rect()
        pauseNode.getBoundsInParent(pauseBounds)
        assertTrue("the upstream resume control has a visible, touchable target", pauseBounds.width() > 0 && pauseBounds.height() > 0)
        val now = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, pauseBounds.exactCenterX(), pauseBounds.exactCenterY(), 0)
            assertTrue("the upstream resume control handles the touch", controls.dispatchTouchEvent(event))
            event.recycle()
        }
        harness.await("resume opens a new segment") { composer.isRecordingAudioVideo }
        harness.idleClock(800)
        host.onComposerRecordTogglePause()
        harness.await("the segments are joined") {
            voice.pendingForTest()?.durationMillis?.let { it > 2500 } == true
        }
        harness.idleClock(650)
        val joined = voice.pendingForTest()!!
        assertTrue("both segments contribute to the take", joined.durationMillis > first.durationMillis + 900)
        val player = editor(composer)
        // Public region state is what the upstream handles edit; the media editor's retained
        // fixture tests independently verify exact frame selection and ordering.
        player.left = 0.25f
        player.right = 0.8f
        harness.renderApp("forum-voice-trimmed")
        voice.sendForTest()
        harness.await("the selected clip is uploaded") { composer.editField!!.text.contains("|audio") }
        assertFalse("the original joined file is spent", joined.file.exists())
        val wire = site.requestBytesFor("uploads.json")!!.toString(Charsets.ISO_8859_1)
        val fileStart = wire.indexOf("Content-Type: audio/mp4\r\n")
        val payloadStart = wire.indexOf("\r\n\r\n", fileStart) + 4
        val payloadEnd = wire.indexOf("\r\n--", payloadStart)
        val received = File(harness.activity.cacheDir, "received-clip.m4a")
        received.writeBytes(wire.substring(payloadStart, payloadEnd).toByteArray(Charsets.ISO_8859_1))
        val track = com.googlecode.mp4parser.authoring.container.mp4.MovieCreator.build(received.absolutePath).tracks.single()
        val uploadedMillis = track.sampleDurations.sum() * 1000 / track.trackMetaData.timescale
        assertTrue("the upload contains only the selected 55%, not the full 2.54 s take",
            uploadedMillis in 1350L..1420L)
        assertFalse("voice send only inserts markup", site.requestPaths.contains("posts.json"))
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun openReply(): TopicFragment {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        val fragment = TopicFragment.forTopic(TOPIC_ID, "Voice host")
        harness.present(fragment)
        harness.await("the reply screen and its voice host") {
            harness.visibleFragment() === fragment && fragment.voiceControllerForTest() != null
        }
        harness.await("the posts") { rowCount() == 1 }
        harness.layoutApp()
        return fragment
    }

    private fun openNewTopic(fragment: NewTopicFragment): NewTopicFragment {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(fragment)
        harness.await("the new-topic screen and its voice host") {
            harness.visibleFragment() === fragment && fragment.voiceControllerForTest() != null
        }
        harness.layoutApp()
        return fragment
    }

    private fun composer(fragment: BaseFragment): ChatActivityEnterView =
        descendants(fragment.fragmentView).filterIsInstance<ChatActivityEnterView>().single()

    private fun editor(composer: ChatActivityEnterView): RecordedAudioPlayerView =
        descendants(composer.recordedAudioPanel!!).filterIsInstance<RecordedAudioPlayerView>().single()

    /** Failure: root-hosted record controls stay below the IME while the input island moves up. */
    private fun assertResumeAboveComposerWithIme(fragment: BaseFragment, name: String) {
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),
                androidx.core.graphics.Insets.of(0, 0, 0, org.telegram.messenger.AndroidUtilities.dp(260f)))
            .build()
        (fragment as NewTopicFragment).onInsetsInternal(fragment.fragmentView, insets)
        harness.idleClock(800)
        harness.renderApp(name)
        val composer = composer(fragment)
        val controls = composer.controlsView
        val node = controls.accessibilityNodeProvider.createAccessibilityNodeInfo(2)!!
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val inputAt = IntArray(2)
        composer.getLocationOnScreen(inputAt)
        assertTrue("resume remains above the composer when the IME is open", bounds.bottom <= inputAt[1])
        assertTrue("resume retains its actual touch target", bounds.width() > 0 && bounds.height() > 0)
    }

    private fun realTake(name: String, duration: Long): VoiceRecording {
        val file = File(harness.activity.cacheDir, name)
        file.writeBytes(javaClass.classLoader!!.getResourceAsStream("voice-editor/$name")!!.use { it.readBytes() })
        val waveform = javaClass.classLoader!!.getResourceAsStream("voice-editor/${file.nameWithoutExtension}.waveform")!!.use { it.readBytes() }
        return VoiceRecording(file, name, "audio/mp4", duration, waveform)
    }

    private fun rowCount(): Int = contentRowCount(harness)

    /** Grants or denies the microphone before a test drives the composer's own gesture. */
    private fun grantRecordPermission(grant: Boolean) {
        val shadow = org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
        if (grant) {
            shadow.grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        } else {
            shadow.denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /** The same touch a finger would send to the composer's own microphone affordance. */
    private fun micGesture(composer: ChatActivityEnterView, action: Int) {
        val mic = composer.audioVideoButtonContainer
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, mic.width / 2f, mic.height / 2f, 0)
        try {
            mic.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun take(): VoiceRecording {
        val dir = File(harness.activity.cacheDir, "voice-recordings").apply { mkdirs() }
        val source = realTake("voice-alpha.m4a", 1523)
        val file = source.file.copyTo(File(dir, "voice-1.m4a"), overwrite = true)
        return source.copy(file = file, name = file.name)
    }

    /** A recorder a test hands in instead of the device microphone. */
    private class FakeRecorder(private val recording: VoiceRecording) : VoiceRecorder {
        var starts = 0
        override val isRecording: Boolean get() = starts > 0
        override fun start(): Boolean {
            starts++
            return true
        }

        override fun stop(): VoiceRecording = recording
        override fun abort() { recording.file.delete() }
        override fun release() = Unit
    }

    private fun audioUploadResponse(): String = """
        {"id":4211,
         "url":"https://linux.do/uploads/default/original/2X/9/voice-1.m4a",
         "original_filename":"voice-1.m4a",
         "extension":"m4a",
         "short_url":"upload://9d5a.m4a",
         "human_filesize":"12.3 KB"}
    """.trimIndent()

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private companion object {
        const val TOPIC_ID = 778899L
    }
}
