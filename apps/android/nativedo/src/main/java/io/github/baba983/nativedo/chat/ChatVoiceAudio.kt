package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.media.isAudioUpload
import io.github.baba983.nativedo.core.model.ChatMessage
import io.github.baba983.nativedo.core.model.ChatMessageUpload
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * A chat message's audio upload, resolved to what a screen needs to act on it: whether the message
 * carries a voice/audio clip, the absolute URL of that clip on the configured site, and whether a
 * pressed link is that clip.
 *
 * <p>The site classifies audio by the file's extension (its own `Upload` kind test), which is the
 * same rule this client records under, so a received `.m4a` is recognized from the message's own
 * upload metadata rather than by re-parsing the cooked body. The screen fetches the URL itself with
 * the session-aware client and plays the local file; nothing here reaches Telegram.</p>
 */
object ChatVoiceAudio {

    /** The message's first audio upload, or null when it carries none. */
    @JvmStatic
    fun audioUpload(message: ChatMessage): ChatMessageUpload? =
        message.uploads.firstOrNull(::isAudio)

    /** The absolute URL of the message's audio upload, or null when it carries none. */
    @JvmStatic
    fun audioUrl(message: ChatMessage, baseUrl: String): String? =
        audioUpload(message)?.let { absoluteUrl(baseUrl, it.url) }

    /**
     * The message's cooked body with a recorded clip's own `<audio>` embed replaced by the labeled
     * link the screen can act on.
     *
     * <p>Upstream's rich importer has no block for a bare `<audio>`, so the site's own voice markup
     * (`![name|audio](upload://...)` cooked to `<audio controls src="...">`) would draw as an
     * unsupported block or vanish. The message's own upload metadata supplies the label (the file
     * the site named) and the target (the upload's URL), so the reader sees a voice message and can
     * play it - the screen fetches the clip with the session and plays the local file. A body with
     * no audio, or a message with no audio upload, is returned untouched.</p>
     */
    @JvmStatic
    fun audioLinkHtml(cookedHtml: String?, message: ChatMessage, baseUrl: String): String {
        val html = cookedHtml ?: return ""
        val upload = audioUpload(message) ?: return html
        if (!html.contains("<audio")) return html
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val label = upload.filename.trim().ifBlank { "语音消息" }
        val href = absoluteUrl(baseUrl, upload.url)
        for (audio in body.select("audio").toList()) {
            val source = audio.attr("src").ifBlank {
                audio.selectFirst("source[src]")?.attr("src").orEmpty()
            }.trim()
            val target = href ?: absoluteUrl(baseUrl, source)
            if (target == null) {
                audio.remove()
                continue
            }
            audio.replaceWith(Element(Tag.valueOf("a"), "").attr("href", target).text(label))
        }
        return body.html()
    }

    /**
     * Whether a pressed link is [message]'s own audio clip: the same URL the body's audio link
     * points at, compared after resolving both against the site, with the file name as a fallback
     * for a site that answered the upload and the cook with different hosts (a CDN).
     */
    @JvmStatic
    fun matches(message: ChatMessage, baseUrl: String, pressedUrl: String?): Boolean {
        if (pressedUrl.isNullOrBlank()) return false
        val upload = audioUpload(message) ?: return false
        val target = absoluteUrl(baseUrl, upload.url)
        if (target != null && pressedUrl == target) return true
        val name = upload.filename.ifBlank { "audio." + upload.extension }
        return pressedUrl.substringBefore('?').substringAfterLast('/') == name
    }

    /** The site's URL for an upload, resolved exactly as the composer's own markup resolves it. */
    @JvmStatic
    fun absoluteUrl(baseUrl: String, url: String): String? {
        if (url.isBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("//")) return "https:$url"
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }

    private fun isAudio(upload: ChatMessageUpload): Boolean =
        isAudioUpload(null, upload.filename.ifBlank { "audio." + upload.extension })
}
