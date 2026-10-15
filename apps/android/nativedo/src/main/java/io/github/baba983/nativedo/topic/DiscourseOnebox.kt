package io.github.baba983.nativedo.topic

import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * A Discourse onebox: the link preview the site renders for a link in a post.
 *
 * Upstream's rich body has no preview block - the blocks its importer knows are text, media, tables,
 * quotes and so on - so a onebox is not lifted out into a message of its own. Its parts are read
 * back into the post's run of text ([preview]), where the link was written, and the bare link line
 * the card stands for goes out of the text the way the site's own renderer takes it: one rich
 * bubble, in the reader's order.
 */
object DiscourseOnebox {

    /** Whether a body element is one of the site's oneboxes. */
    @JvmStatic
    fun isOnebox(element: Element): Boolean =
        (element.tagName() == "aside" || element.tagName() == "div") && element.hasClass("onebox")

    /**
     * The parts of [onebox] as body markup, in the order the site wrote them: the site's own name as
     * a link, the thumbnail, then the linked page's title as a link and the description it found.
     * Empty for a box with nothing to draw, so the caller can leave the box (and its bare link)
     * where they are.
     *
     * <p>The parts are the site's preview read back into the post's own text, not a message of their
     * own: upstream's rich body has no preview block, and a preview a reader can only see as a second
     * message is worse than its own parts read in place. The decoration - the favicon, the metadata
     * row, the clearing div - is dropped, because it says nothing the parts do not.</p>
     */
    @JvmStatic
    fun preview(onebox: Element): List<Element> {
        val header = onebox.selectFirst("header.source, header")
        val siteName = header?.selectFirst("a[href]")?.text()?.trim().orEmpty()
        val body = onebox.selectFirst("article.onebox-body") ?: onebox
        val title = body.selectFirst("h3 a[href], h3")?.text()?.trim().orEmpty()
        val description = body.selectFirst("p")?.text()?.trim().orEmpty()
        val thumbnail = onebox.selectFirst("img.thumbnail") ?: largestImage(onebox)
        val url = onebox.attr("data-onebox-src").trim()
            .ifEmpty { (body.selectFirst("a[href]") ?: header?.selectFirst("a[href]"))?.attr("href").orEmpty() }
        if (siteName.isEmpty() && title.isEmpty() && description.isEmpty() && thumbnail == null) {
            return emptyList()
        }
        val parts = ArrayList<Element>()
        if (siteName.isNotEmpty()) parts += linkedParagraph(url, siteName)
        if (thumbnail != null) parts += thumbnail.clone()
        if (title.isNotEmpty()) parts += linkedParagraph(url, title)
        if (description.isNotEmpty()) parts += Element(Tag.valueOf("p"), "").text(description)
        return parts
    }

    /**
     * The largest picture in [onebox] that is not the site's own decoration, for a box whose
     * thumbnail carries no `thumbnail` class. Null when the box has no such picture.
     */
    private fun largestImage(onebox: Element): Element? {
        var best: Element? = null
        var bestArea = 0
        for (image in onebox.select("img")) {
            if (image.hasClass("site-icon")) continue
            val area = (image.attr("width").toIntOrNull() ?: 0) * (image.attr("height").toIntOrNull() ?: 0)
            if (best == null || area > bestArea) {
                best = image
                bestArea = area
            }
        }
        return best
    }

    /** A paragraph holding one link: the shape upstream's importer reads as a clickable URL. */
    private fun linkedParagraph(url: String, label: String): Element {
        val paragraph = Element(Tag.valueOf("p"), "")
        val anchor = Element(Tag.valueOf("a"), "")
        if (url.isNotEmpty()) anchor.attr("href", url)
        anchor.text(label)
        paragraph.appendChild(anchor)
        return paragraph
    }
}
