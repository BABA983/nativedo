package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.harness.FakeDiscourseSite
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
import java.net.URLDecoder
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

/**
 * The chat screen's voice flow: a finished local take uploaded as the site's `chat-composer` kind
 * and referenced on the message write by `upload_ids[]`, its retry when the site refuses, and the
 * playback of a received voice message through the session-aware cache.
 *
 * <p>The failure modes pinned here (a JVM has no microphone, so the take is injected the way a
 * completed capture would produce it; everything downstream is the real wire):</p>
 * <ul>
 *   <li>the clip is uploaded with `upload_type=chat-composer` and no `channel_id`, and the message
 *       write carries `upload_ids[]` with a blank `message` - the site validates `message` only when
 *       there are no uploads;</li>
 *   <li>an upload the site refuses keeps the take and offers a retry, and writes no message;</li>
 *   <li>a message write the site refuses after an accepted upload reuses the upload id on the retry
 *       (the clip is never uploaded twice) and never resends a write the site already took;</li>
 *   <li>a session change cancels the pending take rather than letting a stale one land in a new
 *       draft;</li>
 *   <li>a received voice message is classified from its upload metadata and played by fetching the
 *       upload with the session - never an unsupported-content block or an invisible recording.</li>
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
internal class ChatVoiceTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aFinishedTakeIsUploadedAsChatComposerThenReferencedByUploadIds() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", audioUploadResponse())
        site.serveJson("chat/71.json", """{"success":"OK","message_id":4711}""")
        site.serveJsonFor("chat/api/channels/71/messages.json") { path ->
            if (path.contains("target_message_id=4711")) harness.fixture("chat-messages-audio.json")
            else harness.fixture("chat-messages.json")
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        val take = pendingTake()
        fragment.attachPendingRecordingForTest(take)
        assertEquals("the finished take becomes the review band",
            ComposerVoicePanelState.REVIEW, fragment.voicePanelForTest().state)

        fragment.sendPendingRecordingForTest()
        harness.await("the accepted voice write and its targeted readback") {
            fragment.pendingRecordingForTest() == null &&
                fragment.hasReceivedAudioForTest(4711)
        }

        val uploadBody = site.requestBodyFor("uploads.json")
        assertNotNull("the clip was uploaded", uploadBody)
        assertTrue("as the site's chat-composer kind", uploadBody!!.contains("chat-composer"))
        assertFalse("an upload carries no channel_id", uploadBody.contains("channel_id"))

        val fields = formFields(site.requestBodyFor("chat/71.json"))
        assertEquals("the message may be blank when an upload is attached", "", fields["message"]?.single())
        assertEquals("the upload is referenced by id", "4211", fields["upload_ids[]"]?.single())
        assertTrue("the readback asked for the accepted id",
            site.queriesFor("chat/api/channels/71/messages.json").any { it.contains("target_message_id=4711") })
        assertFalse("the take is spent", take.file.exists())
        assertNull("no take is kept after an accepted write", fragment.pendingRecordingForTest())
        assertEquals("the band is away", ComposerVoicePanelState.HIDDEN, fragment.voicePanelForTest().state)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedUploadKeepsTheTakeAndWritesNothing() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveResponse("uploads.json") {
            MockResponse().setResponseCode(422).setBody("""{"errors":["不允许上传音频"]}""")
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        val take = pendingTake()
        fragment.attachPendingRecordingForTest(take)
        fragment.sendPendingRecordingForTest()
        harness.await("the refused upload's error band") {
            fragment.voicePanelForTest().state == ComposerVoicePanelState.ERROR
        }

        assertTrue("the take is kept for a retry", take.file.exists())
        assertNotNull("the take is still pending", fragment.pendingRecordingForTest())
        assertEquals("the band says the write failed",
            ComposerVoicePanelState.ERROR, fragment.voicePanelForTest().state)
        assertFalse("no message follows a refused upload", site.requestPaths.contains("chat/71.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedMessageWriteReusesTheUploadIdAndNeverResends() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", audioUploadResponse())
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        site.serveFor("chat/71.json") { _ ->
            if (accepted.get()) {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"success":"OK","message_id":4711}""")
            } else {
                MockResponse().setResponseCode(422).setBody("""{"errors":["频道不允许上传"]}""")
            }
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        val take = pendingTake()
        fragment.attachPendingRecordingForTest(take)
        fragment.sendPendingRecordingForTest()
        harness.await("the refused message write's error band") {
            fragment.voicePanelForTest().state == ComposerVoicePanelState.ERROR
        }

        assertEquals("the clip was uploaded once", 1, site.requestPaths.count { it == "uploads.json" })
        assertEquals("the refused write is not replayed by itself", 1,
            site.requestPaths.count { it == "chat/71.json" })
        assertNotNull("the take is kept", fragment.pendingRecordingForTest())

        // The site accepts the retry: the same upload id is referenced, and the clip is not uploaded
        // a second time.
        accepted.set(true)
        fragment.sendPendingRecordingForTest()
        harness.await("the accepted retry") {
            site.requestPaths.count { it == "chat/71.json" } == 2 && fragment.pendingRecordingForTest() == null
        }

        assertEquals("the retry does not upload the clip twice", 1,
            site.requestPaths.count { it == "uploads.json" })
        val fields = formFields(site.requestBodiesFor("chat/71.json").last())
        assertEquals("the retry references the accepted upload", "4211", fields["upload_ids[]"]?.single())
        assertFalse("the accepted take is spent", take.file.exists())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSessionChangeCancelsThePendingTake() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        val take = pendingTake()
        fragment.attachPendingRecordingForTest(take)
        assertTrue("the take exists before the change", take.file.exists())

        val container = NativeDo.container(harness.context)
        kotlinx.coroutines.runBlocking {
            container.sessionCoordinator.markExpired(
                io.github.baba983.nativedo.core.session.SessionExpiredReason.CookieMissing,
            )
        }
        harness.await("the expired-session state") { fragment.stateForTest() != null }
        harness.idleClock(200)

        assertNull("a stale take is dropped on a session change", fragment.pendingRecordingForTest())
        assertFalse("its private file goes with it", take.file.exists())
        assertEquals("the band is away", ComposerVoicePanelState.HIDDEN, fragment.voicePanelForTest().state)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aReceivedVoiceMessageIsPlayedThroughTheSessionAwareCache() {
        serveJoinedChannel()
        val audioUrl = site.url("uploads/default/original/2X/9/voice-1.m4a")
        site.serveImage("uploads/default/original/2X/9/voice-1.m4a", ByteArray(2048) { 0x22 }, "audio/mp4")
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages-audio.json"))

        val fragment = openChannel()
        harness.await("the audio message") { fragment.messageCount() == 1 }
        harness.layoutApp()
        harness.idleClock(200)

        assertNotNull("the audio message drew a bubble", fragment.bubbleForTest(0))
        assertTrue("its upload is classified as audio", fragment.hasReceivedAudioForTest(4711))

        assertTrue("a press on the audio link is this screen's to act on",
            fragment.playReceivedAudioForTest(4711))
        harness.await("the clip to be fetched with the session") {
            site.requestPaths.contains("uploads/default/original/2X/9/voice-1.m4a")
        }
        harness.await("the local file to play") { fragment.playingAudioUrlForTest() == audioUrl }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun pendingTake(): VoiceRecording {
        val file = File(File(harness.activity.cacheDir, "voice-recordings").apply { mkdirs() }, "voice-test.m4a")
        file.writeBytes(ByteArray(2048) { 0x11 })
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

    private fun formFields(body: String?): Map<String, List<String>> {
        if (body.isNullOrEmpty()) return emptyMap()
        val fields = linkedMapOf<String, MutableList<String>>()
        for (pair in body.split('&')) {
            if (pair.isEmpty()) continue
            val index = pair.indexOf('=')
            val key = URLDecoder.decode(if (index < 0) pair else pair.substring(0, index), "UTF-8")
            val value = if (index < 0) "" else URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            fields.getOrPut(key) { mutableListOf() }.add(value)
        }
        return fields
    }

    private fun serveJoinedChannel() {
        site.serveJson("chat/api/channels/71.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/71/read.json", harness.fixture("chat-read.json"))
    }

    private fun openChannel(): ChatChannelFragment {
        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        val fragment = ChatChannelFragment.forChannel(71L, "站点反馈")
        harness.present(fragment)
        return fragment
    }
}

/** The voice band's states, read without depending on the panel's own enum from Java tests. */
private object ComposerVoicePanelState {
    val HIDDEN = io.github.baba983.nativedo.topic.ComposerVoicePanel.State.Hidden
    val REVIEW = io.github.baba983.nativedo.topic.ComposerVoicePanel.State.Review
    val ERROR = io.github.baba983.nativedo.topic.ComposerVoicePanel.State.Error
}
