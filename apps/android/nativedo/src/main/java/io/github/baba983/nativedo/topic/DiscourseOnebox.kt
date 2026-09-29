package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.model.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC

/**
 * A Discourse onebox: the link preview the site renders for a link in a post, as the message shape
 * upstream draws for one.
 *
 * Upstream's rich body has no preview block - the blocks its importer knows are text, media,
 * tables, quotes and so on - but it has a complete link preview for a message that carries a
 * `TL_messageMediaWebPage`: site name, title, description and a thumbnail, in the compact card
 * every chat shows under a link. The card is *not* a rich body either (a message whose media is a
 * webpage is a text message, by upstream's own `isMediaEmpty`), so a onebox becomes a bubble of its
 * own, exactly like a poll: the run of markup around it stays text, and this card is where the site
 * put it. What the site's own renderer does with the bare link line - taking it out of the text,
 * because the card stands for it - is done to the segment that held it.
 */
object DiscourseOnebox {

    /** The parts of a onebox the card draws, plus the prepared body its thumbnail downloads from. */
    class Card internal constructor(
        @JvmField val url: String,
        @JvmField val siteName: String,
        @JvmField val title: String,
        @JvmField val description: String,
        @JvmField val body: PreparedPostBody,
        @JvmField val photo: TLRPC.Photo?,
    )

    /** Whether a body element is one of the site's oneboxes. */
    @JvmStatic
    fun isOnebox(element: Element): Boolean =
        (element.tagName() == "aside" || element.tagName() == "div") && element.hasClass("onebox")

    /**
     * Reads one onebox's own markup. Null for a box with nothing to draw: a card needs a title, a
     * description or a thumbnail, and a box that has none of the three is better left in the text.
     */
    @JvmStatic
    fun card(oneboxHtml: String, baseUrl: String): Card? {
        val element = Jsoup.parseBodyFragment(oneboxHtml).body().children().firstOrNull() ?: return null
        val title = element.selectFirst("article.onebox-body h3, h3")?.text().orEmpty().trim()
        val description = element.selectFirst("article.onebox-body p, p")?.text().orEmpty().trim()
        val header = element.selectFirst("header.source a[href], header a[href]")
        val link = element.selectFirst("article.onebox-body h3 a[href], h3 a[href]")
            ?: header
            ?: element.selectFirst("article.onebox-body a[href], a[href]")
        val url = link?.attr("href").orEmpty().ifEmpty { element.attr("data-onebox-src") }
        val siteName = header?.text().orEmpty().trim()
        if (title.isEmpty() && description.isEmpty() && element.selectFirst("img.thumbnail") == null) {
            return null
        }
        // The thumbnail goes through the same bridge a post's own images do: it is downloaded by the
        // session's client and given the id and location upstream's image loader reads from a path.
        val body = DiscourseImageBridge.prepare(element.html(), baseUrl)
        val thumbnail = body.images.maxByOrNull { image -> image.width * image.height }
        return Card(
            url = url,
            siteName = siteName,
            title = title,
            description = description,
            body = body,
            photo = thumbnail?.let { image -> DiscourseImageBridge.photoOf(image) },
        )
    }

    /**
     * One onebox as a bubble of its own. The message's text is the link the card stands for, which
     * is what upstream shows above such a card, and the card's own fields are the parts the site
     * rendered: the site's name, the linked page's title and the description it found for it.
     */
    @JvmStatic
    fun message(
        post: Post,
        card: Card,
        topicId: Long,
        users: HashMap<Long, TLRPC.User>,
        baseUrl: String,
    ): MessageObject {
        val user = DiscourseRows.author(post, baseUrl)
        users[user.id] = user

        val webpage = TLRPC.TL_webPage()
        webpage.url = card.url
        webpage.display_url = displayUrl(card)
        webpage.site_name = card.siteName.ifEmpty { displayUrl(card) }
        webpage.title = card.title
        webpage.description = card.description
        // "article" is what makes upstream draw the thumbnail as the small card image, the way the
        // site's own onebox does, rather than as a full-width photo.
        webpage.type = "article"
        card.photo?.let { photo ->
            webpage.photo = photo
            webpage.flags = webpage.flags or 1
        }

        val media = TLRPC.TL_messageMediaWebPage()
        media.webpage = webpage

        val message = DiscourseRows.localMessage(
            post.id.toInt(),
            DiscourseRows.epochSeconds(post.createdAt),
            topicId,
            user.id,
            media,
        )
        message.message = card.url
        return DiscourseRows.messageObject(message, users)
    }

    /** What the card shows as the link's address: the host, as the site prints it in its header. */
    private fun displayUrl(card: Card): String {
        val host = runCatching { java.net.URI(card.url).host }.getOrNull().orEmpty()
        return host.ifEmpty { card.siteName }
    }
}
