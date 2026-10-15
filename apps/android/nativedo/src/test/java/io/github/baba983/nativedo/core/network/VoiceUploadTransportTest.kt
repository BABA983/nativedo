package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The audio upload/send transport, driven through the real client against a local site.
 *
 * <p>The failure modes pinned here, none of which a render could show:</p>
 * <ul>
 *   <li>a chat upload must be sent as the site's `chat-composer` kind (a `composer` upload is not
 *       accepted for a chat message), with no `channel_id` on the upload;</li>
 *   <li>the message write must carry the upload ids as `upload_ids[]` and may carry a blank
 *       `message` when there are uploads - the site validates `message` only when there are none;</li>
 *   <li>an existing text-only send must be unchanged: no `upload_ids` field appears;</li>
 *   <li>a received message's `uploads` must survive the DTO/model mapping (an audio message is
 *       dropped today if the client throws the attachment away).</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
internal class VoiceUploadTransportTest {

    private val site = FakeDiscourseSite()
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("uploads.json", "upload-response.json")
        site.serveJson("chat/71.json", """{"success":"OK","message_id":4711}""")
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun aChatUploadIsSentAsTheSitesChatComposerKind() {
        val container = authenticated()
        val recording = tempRecording("voice-1.m4a")

        val uploaded = runBlocking { container.chatRepository.upload(recording, "audio/mp4") }
        assertTrue("the upload is accepted", uploaded.isSuccess)
        assertEquals("the id the site answered", 4211L, uploaded.getOrNull()?.id)

        val body = site.requestBodyFor("uploads.json")
        assertNotNull("the upload carried a body", body)
        assertTrue(
            "sent as the site's chat-composer kind, not a post composer",
            body!!.contains("name=\"upload_type\"") && body.contains("chat-composer"),
        )
        assertFalse("an upload carries no channel_id; the message carries the channel", body.contains("channel_id"))
        assertTrue("with the file as its own part", body.contains("name=\"file\""))
        assertTrue(
            "and asked for a synchronous answer, the one that carries the id",
            body.contains("name=\"synchronous\"") && body.contains("true"),
        )
        assertEquals("with the token the site asks writes for", "csrf-token-demo", site.csrfHeaderFor("uploads.json"))
    }

    @Test
    fun sendingAnUploadCarriesTheIdsAndAllowsABlankMessage() {
        val container = authenticated()

        val accepted = runBlocking {
            container.chatRepository.send(channelId = 71L, message = "", uploadIds = listOf(42L))
        }
        assertEquals("the site accepted the write", 4711L, accepted.getOrNull())

        val fields = formFields(site.requestBodyFor("chat/71.json"))
        assertEquals("the message may be blank when uploads are attached", "", fields["message"]?.single())
        assertEquals("the upload is referenced by id", "42", fields["upload_ids[]"]?.single())
        assertEquals("with the token the site asks writes for", "csrf-token-demo", site.csrfHeaderFor("chat/71.json"))
    }

    @Test
    fun aTextOnlySendIsUnchanged() {
        val container = authenticated()

        runBlocking { container.chatRepository.send(channelId = 71L, message = "hello") }

        val fields = formFields(site.requestBodyFor("chat/71.json"))
        assertEquals("the text is still the message field", "hello", fields["message"]?.single())
        assertFalse("a text-only send invents no upload_ids", fields.containsKey("upload_ids[]"))
    }

    @Test
    fun aReceivedMessagesAudioUploadSurvivesTheMapping() {
        site.serveJson(
            "chat/api/channels/71/messages.json",
            """
            {"messages":[{
              "id":9001,
              "message":"",
              "cooked":"<p><audio controls src=\"/uploads/default/original/2X/9/9d5a.m4a\"></audio></p>",
              "created_at":"2024-01-01T00:00:00.000Z",
              "chat_channel_id":71,
              "user":{"id":1,"username":"baba","name":"Baba","avatar_template":"/u/{size}.png"},
              "uploads":[{
                "id":4211,
                "url":"https://linux.do/uploads/default/original/2X/9/9d5a.m4a",
                "original_filename":"voice-1.m4a",
                "extension":"m4a",
                "human_filesize":"12.3 KB",
                "short_url":"upload://9d5a.m4a"
              }]
            }],"meta":{"can_load_more_past":false,"can_load_more_future":false}}
            """.trimIndent(),
        )
        val container = authenticated()

        val page = runBlocking { container.discourseApiClient.fetchChatMessages(71L) }
        val upload = page.messages.single().uploads.single()
        assertEquals("the audio upload is carried, not dropped", "voice-1.m4a", upload.filename)
        assertEquals("by its extension, which is how the site classifies audio", "m4a", upload.extension)
        assertEquals("pointing at the site's own URL", "https://linux.do/uploads/default/original/2X/9/9d5a.m4a", upload.url)
    }

    private fun authenticated(): AppContainer {
        val container = site.authenticatedContainer(context)
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("baba") }
        return container
    }

    private fun tempRecording(name: String): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(ByteArray(2048) { 0x11 })
        return file
    }

    /** A form body as decoded `key -> values`; a repeated key (an array field) keeps every value. */
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
}
