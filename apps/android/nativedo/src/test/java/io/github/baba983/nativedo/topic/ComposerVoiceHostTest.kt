package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
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
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ChatActivityEnterView

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
internal class ComposerVoiceHostTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A native composer over a Discourse post.</p>")
            post.put("post_number", 1)
        }
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
    fun theComposersOwnDelegateOwnsTheMicrophoneSoTelegramNeverRecords() {
        val fragment = openReply()
        val voice = fragment.voiceControllerForTest()!!
        val host = voice.boundRecordHostForTest()
        assertNotNull("a recording host is bound", host)
        assertTrue(
            "the host owns the gesture, so upstream's Telegram recorder is skipped",
            host!!.onRecordTouch(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 200f, 0f, 0)),
        )
        host.onRecordTouch(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 200f, 0f, 0))
        assertFalse(
            "no Telegram audio/video recording started behind the host",
            composer(fragment).isRecordingAudioVideo(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aFinishedTakeUploadsAsTheComposerKindAndInsertsTheSitesAudioMarkdown() {
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!

        voice.onRecordingStarted()
        harness.renderApp("forum-voice-recording")
        val take = take()
        voice.attachPendingRecordingForTest(take)
        assertEquals(
            "a finished take becomes the review band, never a post",
            ComposerVoicePanel.State.Review,
            voice.panelForTest().state,
        )
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
        assertEquals("the band is away", ComposerVoicePanel.State.Hidden, voice.panelForTest().state)
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
        harness.await("the refused upload's error band") {
            voice.panelForTest().state == ComposerVoicePanel.State.Error
        }

        assertTrue("the take is kept for a retry", take.file.exists())
        assertNotNull("the take is still pending", voice.pendingForTest())
        assertEquals(
            "the band says the upload failed",
            ComposerVoicePanel.State.Error,
            voice.panelForTest().state,
        )
        harness.renderApp("forum-voice-error")
        assertEquals("the field is untouched", "", composer.editField!!.text.toString())
        assertFalse("no post follows a refused upload", site.requestPaths.contains("posts.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun cancellingTheTakeDeletesItAndClearsTheBand() {
        val fragment = openReply()
        val composer = composer(fragment)
        val voice = fragment.voiceControllerForTest()!!

        val take = take()
        voice.attachPendingRecordingForTest(take)
        voice.cancelForTest()

        assertNull("no take is kept", voice.pendingForTest())
        assertFalse("the private file goes with it", take.file.exists())
        assertEquals("the band is away", ComposerVoicePanel.State.Hidden, voice.panelForTest().state)
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
        assertEquals("the band is away", ComposerVoicePanel.State.Hidden, voice.panelForTest().state)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun switchingAccountDuringCaptureCannotGiveTheFinishedTakeANewOwner() {
        val fragment = openReply()
        val voice = fragment.voiceControllerForTest()!!
        voice.onRecordingStarted()
        val take = take()
        NativeDo.container(harness.context).sessionStore.save(
            SessionSnapshot("otheruser", "Other", "", System.currentTimeMillis()),
        )

        voice.attachPendingRecordingForTest(take)
        voice.sendForTest()

        assertNull("the old account's capture is not a new account's pending take", voice.pendingForTest())
        assertFalse("the rejected capture file is deleted", take.file.exists())
        assertFalse("the capture never reaches uploads", site.requestPaths.contains("uploads.json"))
        assertEquals("the new account's field stays empty", "", composer(fragment).editField!!.text.toString())
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

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun take(): VoiceRecording {
        val dir = File(harness.activity.cacheDir, "voice-recordings").apply { mkdirs() }
        val file = File(dir, "voice-1.m4a").apply { writeBytes(ByteArray(2048) { 0x11 }) }
        return VoiceRecording(file, file.name, "audio/mp4", 1_500L)
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
