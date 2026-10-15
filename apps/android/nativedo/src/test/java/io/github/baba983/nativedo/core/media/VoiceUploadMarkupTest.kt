package io.github.baba983.nativedo.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The markdown a recorded clip contributes to a post, and how the site's own kind test is mirrored.
 *
 * <p>The failure this pins: an audio upload written as a generic `|attachment` (a download link)
 * instead of the site's `|audio` image-shaped link (an embedded player). Discourse reads audio by
 * the file's extension, so `.m4a` is audio and `.mp4` is video; the markup has to follow the same
 * rule the server does, or a recording is offered as a file to download.</p>
 */
internal class VoiceUploadMarkupTest {

    @Test
    fun aRecordedClipBecomesTheSitesAudioMarkup() {
        assertEquals(
            "a recorded m4a is embedded with the site's |audio marker, not offered as a download",
            "![voice-1.m4a|audio](upload://9d5a.m4a)",
            replyUploadMarkup(
                name = "voice-1.m4a",
                shortUrl = "upload://9d5a.m4a",
                url = "https://linux.do/uploads/9d5a.m4a",
                mimeType = "audio/mp4",
                width = 0,
                height = 0,
                humanSize = "12.3 KB",
            ),
        )
    }

    @Test
    fun audioIsRecognizedByExtensionEvenWithoutAMime() {
        assertEquals(
            "the site's kind test is by filename, so a null mime must not demote audio to attachment",
            "![voice-1.m4a|audio](upload://9d5a.m4a)",
            replyUploadMarkup(
                name = "voice-1.m4a",
                shortUrl = "upload://9d5a.m4a",
                url = "",
                mimeType = null,
                width = 0,
                height = 0,
                humanSize = "12.3 KB",
            ),
        )
    }

    @Test
    fun aVideoIsStillAVideoAndAFileIsStillAnAttachment() {
        assertEquals(
            "mp4 is video, not audio",
            "![clip.mp4](upload://clip.mp4)",
            replyUploadMarkup("clip.mp4", "upload://clip.mp4", "", "video/mp4", 0, 0, "1.2 MB"),
        )
        assertEquals(
            "a zip stays a downloadable attachment with its size",
            "[notes.zip|attachment](upload://notes.zip) (1.2 MB)",
            replyUploadMarkup("notes.zip", "upload://notes.zip", "", null, 0, 0, "1.2 MB"),
        )
    }

    @Test
    fun audioMarkupFallsBackToTheResolvedUrlWithoutAShortHandle() {
        assertEquals(
            "without a short_url the audio link points at the answer's resolved URL",
            "![voice-1.m4a|audio](https://linux.do/uploads/9d5a.m4a)",
            replyUploadMarkup(
                name = "voice-1.m4a",
                shortUrl = null,
                url = "https://linux.do/uploads/9d5a.m4a",
                mimeType = "audio/mp4",
                width = 0,
                height = 0,
                humanSize = null,
            ),
        )
    }

    @Test
    fun theAudioClassificationMatchesTheSitesExtensionRule() {
        assertTrue(isAudioUpload("audio/mp4", "voice-1.m4a"))
        assertTrue("extension alone is enough", isAudioUpload(null, "voice-1.m4a"))
        assertTrue(isAudioMimeType("audio/ogg"))
        assertFalse("mp4 is a video extension", isAudioFileName("clip.mp4"))
        assertFalse("a zip is not audio", isAudioUpload(null, "notes.zip"))
    }
}
