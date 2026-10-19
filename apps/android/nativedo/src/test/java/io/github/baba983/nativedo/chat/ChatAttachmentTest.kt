package io.github.baba983.nativedo.chat

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.feature.chat.data.ChatAttachmentState
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView

/**
 * The chat screen's paperclip: a picked file is copied into the app's own cache and uploaded as the
 * site's `chat-composer` kind, previewed above the composer, and only referenced by `upload_ids[]`
 * when the reader presses Send.
 *
 * <p>The failure modes pinned here:</p>
 * <ul>
 *   <li>picking stages and uploads but never sends: no message write exists until the reader's own
 *       Send, and the preview is this app's own copy of the picked bytes;</li>
 *   <li>a read grant handed to the screen's attach seam is released only after the copy it guards
 *       exists;</li>
 *   <li>the write references the accepted upload ids in pick order, and may carry a blank message
 *       because an attachment is the content;</li>
 *   <li>a refused upload keeps the preview and the reader's text, writes nothing, and a Send retries
 *       the upload before the write;</li>
 *   <li>a refused message write reuses the accepted upload on the retry - the file is never uploaded
 *       twice - and a readback that fails after an accepted write never resends it;</li>
 *   <li>a cancelled picker stages nothing, a removed preview discards its copy, and an account
 *       switch discards both the preview and its private file;</li>
 *   <li>a staged attachment and a pending voice take never share one write.</li>
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
internal class ChatAttachmentTest {

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
    fun thePaperclipStagesAndUploadsButNeverSends() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        val uri = contentUri("note.png", "attachment-bytes".toByteArray())

        composer(fragment).getAttachButton().performClick()
        harness.idleClock(200)
        val picker = shadowOf(harness.activity).nextStartedActivityForResult
            ?: throw AssertionError("the paperclip opened no picker")
        assertEquals("the paperclip asks the system picker", Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)
        shadowOf(harness.activity).receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(uri))

        harness.await("the picked copy to be staged and accepted") {
            fragment.stagedAttachmentCountForTest() == 1 &&
                fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        assertTrue("the preview is on screen", fragment.isAttachmentStripShownForTest())
        assertEquals("the preview carries the picker's name", "note.png", fragment.stagedAttachmentNameForTest(0))
        // The pick is a copy: the paperclip's URI grant is the Activity's, so what has to be true is
        // that the preview owns the picked bytes rather than a provider stream that may go away.
        assertEquals("the preview holds the app's own copy of the picked bytes",
            "attachment-bytes", fragment.stagedAttachmentFileForTest(0).readText())
        assertTrue(
            "the copy - not the provider's stream - is what went to the site",
            site.requestBodyFor("uploads.json")!!.contains("attachment-bytes"),
        )
        assertTrue("as the site's chat-composer kind",
            site.requestBodyFor("uploads.json")!!.contains("chat-composer"))
        assertFalse("a pick is not a send", site.requestPaths.contains("chat/71.json"))

        harness.layoutApp()
        harness.idleClock(300)
        harness.renderApp("chat-attachment-staged")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The read grant a caller hands the attach seam (a keyboard's commit, a paste, a recording) is
     * released exactly once, and only after the copy it guards exists: a release that happened first
     * would upload a revoked URI, and a missing release would hold the provider's grant.
     */
    @Test
    fun aHandedGrantIsReleasedOnceTheCopyExists() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))
        val released = AtomicBoolean(false)

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(
            contentUri("note.png", ReleaseAwareStream(released)),
            Runnable { released.set(true) },
        )

        harness.await("the handed-grant copy to be staged and accepted") {
            fragment.stagedAttachmentCountForTest() == 1 &&
                fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        assertTrue("the grant is handed back once the copy exists", released.get())
        assertTrue("and the copy, not the stream, is what the site received",
            site.requestBodyFor("uploads.json")!!.contains("attachment-bytes"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun sendReferencesTheAcceptedUploadsAndClearsThePreview() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJsonFor("chat/api/channels/71/messages.json") { path ->
            if (path.contains("target_message_id=4801")) attachmentMessage() else harness.fixture("chat-messages.json")
        }
        site.serveJson("chat/71.json", """{"success":"OK","message_id":4801}""")
        site.serveImage("uploads/chat/staged.png", imageBytes())
        val nextUploadId = java.util.concurrent.atomic.AtomicLong(5210)
        site.serveResponse("uploads.json") {
            jsonResponse(uploadResponse(nextUploadId.incrementAndGet(), "picked-file"))
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the first preview accepted by the site") {
            fragment.stagedAttachmentCountForTest() == 1 &&
                fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        fragment.attachMediaUri(contentUri("report.txt", "report-bytes".toByteArray()), null)
        harness.await("both previews accepted by the site") {
            fragment.stagedAttachmentCountForTest() == 2 && fragment.stagedAttachmentStateForTest(0) ==
                ChatAttachmentState.Uploaded && fragment.stagedAttachmentStateForTest(1) ==
                ChatAttachmentState.Uploaded
        }
        assertTrue("each pick keeps its own name in the preview",
            site.requestBodiesFor("uploads.json").any { it.contains("filename=\"report.txt\"") })
        assertFalse("still no message", site.requestPaths.contains("chat/71.json"))

        // An attachment-only message: the composer shows its own Send because a file is staged, and
        // the reader's press is the only thing that writes.
        val composer = composer(fragment)
        harness.idleClock(300)
        harness.layoutApp()
        assertTrue("a staged attachment exposes the send button", composer.getSendButtonInternal().isShown)
        composer.getSendButtonInternal().performClick()
        harness.await("the accepted write and its readback") {
            site.requestPaths.count { it == "chat/71.json" } == 1 && fragment.stagedAttachmentCountForTest() == 0
        }
        harness.await("the accepted message read back") { fragment.messageCount() == 1 }

        val fields = formFields(site.requestBodyFor("chat/71.json"))
        assertEquals("an attachment message may be blank", "", fields["message"]?.single())
        assertEquals(
            "both accepted uploads are referenced, in pick order",
            listOf("5211", "5212"),
            fields["upload_ids[]"],
        )
        assertTrue("the readback asked for the accepted id",
            site.queriesFor("chat/api/channels/71/messages.json").any { it.contains("target_message_id=4801") })
        assertEquals("the accepted message id is what the readback found", 4801L,
            fragment.chatMessageForTest(0).id)
        assertFalse("the preview is gone", fragment.isAttachmentStripShownForTest())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedUploadKeepsThePreviewAndTextAndRetriesOnSend() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("chat/71.json", """{"success":"OK","message_id":4801}""")
        val accepted = AtomicBoolean(false)
        site.serveResponse("uploads.json") {
            if (accepted.get()) jsonResponse(uploadResponse(5211, "note.png"))
            else MockResponse().setResponseCode(422).setBody("""{"errors":["不允许上传该文件"]}""")
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.setComposerTextForTest("这个文件给你")

        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the refused upload") {
            fragment.stagedAttachmentCountForTest() == 1 &&
                fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Failed
        }
        harness.layoutApp()
        harness.idleClock(200)
        harness.renderApp("chat-attachment-upload-failed")
        assertFalse("a refused upload writes nothing", site.requestPaths.contains("chat/71.json"))
        assertEquals("the text is untouched", "这个文件给你", fragment.composerTextForTest())
        assertTrue("the preview is kept for the retry", fragment.isAttachmentStripShownForTest())

        accepted.set(true)
        fragment.sendForTest("这个文件给你")
        harness.await("the retried upload and the write") {
            site.requestPaths.count { it == "chat/71.json" } == 1 && fragment.stagedAttachmentCountForTest() == 0
        }
        assertEquals("the retry uploaded the file again, once", 2,
            site.requestPaths.count { it == "uploads.json" })
        assertEquals("the accepted upload is referenced",
            "5211", formFields(site.requestBodyFor("chat/71.json"))["upload_ids[]"]?.single())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedWriteReusesTheUploadAndKeepsThePreview() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))
        val accepted = AtomicBoolean(false)
        site.serveFor("chat/71.json") { _ ->
            if (accepted.get()) {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"success":"OK","message_id":4801}""")
            } else {
                MockResponse().setResponseCode(422).setBody("""{"errors":["频道不允许上传"]}""")
            }
        }

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the accepted upload") {
            fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }

        fragment.setComposerTextForTest("带附件的消息")
        fragment.sendForTest("带附件的消息")
        // The request landing on the site is not the refusal: the composer only comes back once the
        // site's answer has been processed, and that is when a retry can be made at all.
        harness.await("the refused write to settle") {
            site.requestPaths.count { it == "chat/71.json" } == 1 && fragment.isComposerShown()
        }
        assertEquals("the refused text stays in the field", "带附件的消息", fragment.composerTextForTest())
        harness.idleClock(200)
        assertEquals("the upload happened once", 1, site.requestPaths.count { it == "uploads.json" })
        assertEquals("the preview is kept", 1, fragment.stagedAttachmentCountForTest())
        assertEquals("and stays accepted", ChatAttachmentState.Uploaded, fragment.stagedAttachmentStateForTest(0))

        accepted.set(true)
        fragment.sendForTest("带附件的消息")
        harness.await("the accepted retry") {
            site.requestPaths.count { it == "chat/71.json" } == 2 && fragment.stagedAttachmentCountForTest() == 0
        }
        assertEquals("the retry does not upload the file twice", 1,
            site.requestPaths.count { it == "uploads.json" })
        assertEquals("the retry references the accepted upload", "5211",
            formFields(site.requestBodiesFor("chat/71.json").last())["upload_ids[]"]?.single())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aReadbackThatFailsDoesNotResendOrRestageTheAcceptedAttachment() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveResponseByQuery("chat/api/channels/71/messages.json") { path ->
            if (path.contains("target_message_id=4801")) {
                MockResponse().setResponseCode(500).setBody("""{"errors":["boom"]}""")
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("chat-messages.json"))
            }
        }
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))
        site.serveJson("chat/71.json", """{"success":"OK","message_id":4801}""")

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the accepted upload") {
            fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        fragment.setComposerTextForTest("只发一次")
        fragment.sendForTest("只发一次")
        // The accepted write is what matters, not the request landing: the composer comes back and
        // the targeted readback goes out only once the site's acknowledgement was processed (and
        // that readback is the one it then refuses).
        harness.await("the accepted write to settle") {
            fragment.isComposerShown() &&
                site.queriesFor("chat/api/channels/71/messages.json")
                    .any { it.contains("target_message_id=4801") }
        }
        harness.idleClock(400)

        assertEquals("a failed readback never replays the write", 1,
            site.requestPaths.count { it == "chat/71.json" })
        assertEquals("nor uploads again", 1, site.requestPaths.count { it == "uploads.json" })
        assertEquals("the accepted message is spent, not staged for a second send", 0,
            fragment.stagedAttachmentCountForTest())
        assertEquals("the client does not invent the message it could not read", 3, fragment.messageCount())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aCancelledPickerStagesNothing() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }

        composer(fragment).getAttachButton().performClick()
        harness.idleClock(200)
        val picker = shadowOf(harness.activity).nextStartedActivityForResult
            ?: throw AssertionError("the paperclip opened no picker")
        shadowOf(harness.activity).receiveResult(picker.intent, Activity.RESULT_CANCELED, null)
        harness.idleClock(300)

        assertEquals("a cancelled pick stages nothing", 0, fragment.stagedAttachmentCountForTest())
        assertFalse("and shows no preview", fragment.isAttachmentStripShownForTest())
        assertFalse("no upload follows a cancelled pick", site.requestPaths.contains("uploads.json"))
        assertFalse("no message follows a cancelled pick", site.requestPaths.contains("chat/71.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun removingAPreviewDiscardsItsCopyAndSendsNothing() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the accepted upload") {
            fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        val copy = fragment.stagedAttachmentFileForTest(0)

        fragment.removeStagedAttachmentForTest(0)

        assertEquals("the preview is gone", 0, fragment.stagedAttachmentCountForTest())
        assertFalse("and so is its private copy", copy.exists())
        assertFalse("an accepted upload alone sends nothing", site.requestPaths.contains("chat/71.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anAccountSwitchDiscardsThePreviewAndItsCopy() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the accepted upload") {
            fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }
        val copy = fragment.stagedAttachmentFileForTest(0)
        assertTrue("the copy exists before the change", copy.exists())

        val container = NativeDo.container(harness.context)
        kotlinx.coroutines.runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.CookieMissing) }
        harness.await("the expired-session state") { fragment.stateForTest() != null }
        harness.idleClock(300)

        assertEquals("a stale preview does not outlive the account", 0,
            fragment.stagedAttachmentCountForTest())
        assertFalse("its private copy goes with it", copy.exists())
        assertFalse("no write is made for it", site.requestPaths.contains("chat/71.json"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aStagedAttachmentAndAPendingTakeNeverShareOneWrite() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("uploads.json", uploadResponse(5211, "note.png"))

        val fragment = openChannel()
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.attachMediaUri(contentUri("note.png", imageBytes()), null)
        harness.await("the accepted upload") {
            fragment.stagedAttachmentStateForTest(0) == ChatAttachmentState.Uploaded
        }

        fragment.attachPendingRecordingForTest(pendingTake())
        fragment.sendPendingRecordingForTest()
        harness.idleClock(300)

        assertFalse("the conflicting voice write never went out", site.requestPaths.contains("chat/71.json"))
        assertNotNull("the take is kept for its own send", fragment.pendingRecordingForTest())
        assertEquals("the attachment is still staged", 1, fragment.stagedAttachmentCountForTest())
        assertEquals("and the take was not uploaded beside it", 1, site.requestPaths.count { it == "uploads.json" })

        // The other direction: a finished take refuses a new pick rather than mixing the two.
        fragment.cancelPendingRecordingForTest()
        fragment.removeStagedAttachmentForTest(0)
        fragment.attachPendingRecordingForTest(pendingTake())
        val released = AtomicBoolean(false)
        fragment.attachMediaUri(contentUri("second.png", imageBytes()), Runnable { released.set(true) })
        harness.idleClock(300)

        assertTrue("the refused pick still hands its grant back", released.get())
        assertEquals("nothing was staged beside the take", 0, fragment.stagedAttachmentCountForTest())
        assertEquals("and nothing was uploaded for it", 1, site.requestPaths.count { it == "uploads.json" })
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun openChannel(): ChatChannelFragment {
        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        val fragment = ChatChannelFragment.forChannel(71L, "站点反馈")
        harness.present(fragment)
        return fragment
    }

    private fun serveJoinedChannel() {
        site.serveJson("chat/api/channels/71.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/71/read.json", harness.fixture("chat-read.json"))
    }

    private fun composer(fragment: ChatChannelFragment): ChatActivityEnterView =
        descendants(fragment.fragmentView).filterIsInstance<ChatActivityEnterView>().single()

    /**
     * A provider URI for [name] whose bytes are [stream]. Robolectric registers the stream under the
     * URI, the way a real picker's provider would hand one back.
     */
    private fun contentUri(name: String, stream: java.io.InputStream): Uri {
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$name")
        shadowOf(harness.activity.contentResolver).registerInputStream(uri, stream)
        return uri
    }

    private fun contentUri(name: String, bytes: ByteArray): Uri = contentUri(name, ByteArrayInputStream(bytes))

    /** A stream that refuses to be read once the read grant it came with has been handed back. */
    private class ReleaseAwareStream(private val released: AtomicBoolean) :
        ByteArrayInputStream("attachment-bytes".toByteArray()) {
        override fun read(): Int {
            check(!released.get()) { "the grant was released before the copy finished" }
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            check(!released.get()) { "the grant was released before the copy finished" }
            return super.read(b, off, len)
        }
    }

    private fun imageBytes(): ByteArray {
        val image = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(38, 140, 120))
        return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun pendingTake(): io.github.baba983.nativedo.core.media.VoiceRecording {
        val file = File(File(harness.activity.cacheDir, "voice-recordings").apply { mkdirs() }, "take.m4a")
        file.writeBytes(ByteArray(2048) { 0x11 })
        return io.github.baba983.nativedo.core.media.VoiceRecording(file, file.name, "audio/mp4", 1_200L)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun uploadResponse(id: Long, name: String): String = """
        {"id":$id,
         "url":"https://linux.do/uploads/chat/$name",
         "original_filename":"$name",
         "extension":"${name.substringAfterLast('.')}",
         "width":320,
         "height":180,
         "short_url":"upload://staged.$id",
         "human_filesize":"12.3 KB"}
    """.trimIndent()

    /** One message with the accepted attachment, as the targeted readback answers it. */
    private fun attachmentMessage(): String = """
        {"messages":[{"id":4801,
          "message":"",
          "cooked":"<p><img src=\"/uploads/chat/staged.png\" alt=\"note.png\"></p>",
          "created_at":"2026-10-02T10:00:00.000Z",
          "chat_channel_id":71,
          "user":{"id":7,"username":"baba","name":"Baba","avatar_template":"/user_avatar/linux.do/baba/{size}/1.png"},
          "uploads":[{"id":5211,
            "url":"/uploads/chat/staged.png",
            "original_filename":"note.png",
            "extension":"png",
            "width":320,
            "height":180,
            "short_url":"upload://staged.png",
            "human_filesize":"12.3 KB"}]}],
         "meta":{"can_load_more_past":false,"can_load_more_future":false}}
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

    private fun descendants(view: android.view.View?): List<android.view.View> {
        if (view == null) return emptyList()
        val found = mutableListOf(view)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) found += descendants(view.getChildAt(index))
        }
        return found
    }
}
