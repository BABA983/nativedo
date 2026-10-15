package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.model.ChatMessage
import io.github.baba983.nativedo.core.model.ChatMessageUpload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a received message's audio clip is recognized and addressed, with no Android runtime.
 *
 * <p>The failure this pins: the site classifies audio by the file's extension, and a received
 * message carries that in its own upload metadata. A screen that classified by mime alone would
 * miss the clip the site never gave a mime for; one that re-parsed the cooked body would miss a
 * clip the cook answered on a different host.</p>
 */
internal class ChatVoiceAudioTest {

    private fun message(vararg uploads: ChatMessageUpload) = ChatMessage(id = 9001L, uploads = uploads.toList())

    private fun upload(id: Long, url: String, name: String, extension: String) =
        ChatMessageUpload(id = id, url = url, filename = name, extension = extension)

    @Test
    fun theSitesAudioUploadIsTheExtensionBasedOne() {
        val message = message(
            upload(1L, "https://linux.do/uploads/1.png", "shot.png", "png"),
            upload(2L, "https://linux.do/uploads/2.m4a", "voice-1.m4a", "m4a"),
        )
        assertEquals("the .m4a is the voice message", 2L, ChatVoiceAudio.audioUpload(message)?.id)
    }

    @Test
    fun anAudioUploadWithOnlyAnExtensionIsStillAudio() {
        val message = message(upload(3L, "/uploads/3.m4a", "", "m4a"))
        assertEquals("a blank filename falls back to the extension", 3L, ChatVoiceAudio.audioUpload(message)?.id)
    }

    @Test
    fun aNonAudioMessageHasNoClip() {
        val message = message(upload(4L, "/uploads/4.zip", "notes.zip", "zip"))
        assertNull(ChatVoiceAudio.audioUpload(message))
    }

    @Test
    fun theClipUrlResolvesAgainstTheSite() {
        val message = message(upload(5L, "/uploads/5.m4a", "voice-1.m4a", "m4a"))
        assertEquals("a path resolves against the base", "https://linux.do/uploads/5.m4a",
            ChatVoiceAudio.audioUrl(message, "https://linux.do/"))
        assertEquals("an absolute answer is kept", "https://cdn.example/5.m4a",
            ChatVoiceAudio.audioUrl(message(upload(6L, "https://cdn.example/5.m4a", "voice-1.m4a", "m4a")),
                "https://linux.do/"))
    }

    @Test
    fun aPressedLinkMatchesTheClipByUrlOrByFileName() {
        val message = message(upload(7L, "/uploads/7.m4a", "voice-1.m4a", "m4a"))
        assertTrue("the resolved url is the clip", ChatVoiceAudio.matches(message, "https://linux.do/",
            "https://linux.do/uploads/7.m4a"))
        assertTrue("a CDN copy with the same file name is the clip", ChatVoiceAudio.matches(message, "https://linux.do/",
            "https://cdn.example/original/7/voice-1.m4a"))
        assertFalse("another message's link is not", ChatVoiceAudio.matches(message, "https://linux.do/",
            "https://linux.do/uploads/other.m4a"))
        assertFalse("a message with no clip matches nothing", ChatVoiceAudio.matches(message(), "https://linux.do/",
            "https://linux.do/uploads/7.m4a"))
    }

    @Test
    fun theSitesAudioEmbedBecomesALabeledPlayableLink() {
        val message = message(upload(8L, "/uploads/8.m4a", "voice-2.m4a", "m4a"))
        val cooked = """<p><audio controls src="/uploads/8.m4a"></audio></p>"""
        val rewritten = ChatVoiceAudio.audioLinkHtml(cooked, message, "https://linux.do/")
        assertTrue("the audio element is gone, so no unsupported block reaches the importer",
            !rewritten.contains("<audio"))
        assertTrue("the clip is a labeled link the screen can act on",
            rewritten.contains("<a href=\"https://linux.do/uploads/8.m4a\">voice-2.m4a</a>"))
    }

    @Test
    fun aMessageWithNoAudioKeepsItsCookedBody() {
        val cooked = """<p><audio controls src="/uploads/9.m4a"></audio></p>"""
        assertEquals("no upload metadata means nothing to rewrite", cooked,
            ChatVoiceAudio.audioLinkHtml(cooked, message(), "https://linux.do/"))
    }
}
