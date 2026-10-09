// SPDX-License-Identifier: GPL-2.0-or-later
// Container reconstruction adapted from org/telegram/ui/iv/RichEditorListView.java.
package io.github.baba983.nativedo.topic;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.RichHtml;

import java.util.ArrayList;

/**
 * Turns a prepared Discourse post body into the rich-message model upstream's cells draw.
 *
 * <p>The preparation itself (normalizing Discourse's wrappers, replacing post
 * images with media references) happens in {@link DiscourseImageBridge}, because it also has to
 * fetch those images; this class is only the last step into {@link RichHtml}, upstream's own
 * importer.</p>
 */
public final class DiscourseCookedHtml {

    public static TL_iv.RichMessage richMessage(PreparedPostBody body) {
        TL_iv.RichMessage richMessage = new TL_iv.RichMessage();
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(body.getHtml());
        document.outputSettings().prettyPrint(false);
        richMessage.blocks = parseChildren(document.body());
        richMessage.photos = new ArrayList<>(body.getPhotos());
        richMessage.documents = new ArrayList<>();
        return richMessage;
    }

    // RichHtml owns leaf text/styles/media/table import. Reconstruct semantic containers from
    // the DOM, because its editor list importer only preserves text plus nested lists in an li.
    private static ArrayList<TL_iv.PageBlock> parseChildren(Element parent) {
        ArrayList<TL_iv.PageBlock> out = new ArrayList<>();
        StringBuilder inline = new StringBuilder();
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode || node instanceof Element && isInline((Element) node)) {
                inline.append(node.outerHtml());
                continue;
            }
            if (!inline.toString().isBlank()) appendImported(out, "<p>" + inline + "</p>");
            inline.setLength(0);
            if (!(node instanceof Element)) continue;
            Element element = (Element) node;
            String tag = element.normalName();
            if (tag.equals("ul") || tag.equals("ol")) {
                out.add(parseList(element));
            } else if (tag.equals("blockquote") && !element.className().toLowerCase().contains("pull")) {
                Element copy = element.clone();
                Element cite = copy.children().stream().filter(c -> c.normalName().equals("cite")).findFirst().orElse(null);
                TL_iv.RichText author = cite == null ? new TL_iv.textEmpty() : inlineText(cite.html());
                if (cite != null) cite.remove();
                ArrayList<TL_iv.PageBlock> inner = parseChildren(copy);
                if (inner.size() == 1 && inner.get(0) instanceof TL_iv.pageBlockParagraph) {
                    TL_iv.pageBlockBlockquote quote = new TL_iv.pageBlockBlockquote();
                    quote.text = inner.get(0).text;
                    quote.caption = author;
                    quote.collapsed = element.hasAttr("data-collapsed") || element.hasAttr("collapsed");
                    out.add(quote);
                } else {
                    TL_iv.pageBlockBlockquoteBlocks quote = new TL_iv.pageBlockBlockquoteBlocks();
                    quote.blocks = inner;
                    quote.caption = author;
                    out.add(quote);
                }
            } else if (tag.equals("details")) {
                Element copy = element.clone();
                Element summary = copy.children().stream().filter(c -> c.normalName().equals("summary")).findFirst().orElse(null);
                TL_iv.pageBlockDetails details = new TL_iv.pageBlockDetails();
                details.title = summary == null ? new TL_iv.textEmpty() : inlineText(summary.html());
                details.open = element.hasAttr("open");
                if (summary != null) summary.remove();
                details.blocks = parseChildren(copy);
                out.add(details);
            } else if (tag.equals("section") || tag.equals("article") || tag.equals("main")) {
                out.addAll(parseChildren(element));
            } else {
                appendImported(out, element.outerHtml());
            }
        }
        if (!inline.toString().isBlank()) appendImported(out, "<p>" + inline + "</p>");
        return out;
    }

    private static boolean isInline(Element element) {
        String tag = element.normalName();
        return !element.tag().isBlock() && !tag.equals("img") && !tag.equals("video")
                && !tag.equals("audio") && !tag.equals("figure");
    }

    private static void appendImported(ArrayList<TL_iv.PageBlock> out, String html) {
        if (html.isBlank()) return;
        for (BlockRow row : RichHtml.parse(html)) out.add(row.block);
    }

    private static TL_iv.RichText inlineText(String html) {
        return RichHtml.parse("<p>" + html + "</p>").get(0).block.text;
    }

    private static TL_iv.PageBlock parseList(Element list) {
        boolean ordered = list.normalName().equals("ol");
        TL_iv.pageBlockOrderedList orderedList = ordered ? new TL_iv.pageBlockOrderedList() : null;
        TL_iv.pageBlockList unorderedList = ordered ? null : new TL_iv.pageBlockList();
        int counter = 1;
        for (Element li : list.children()) {
            if (!li.normalName().equals("li")) continue;
            ArrayList<TL_iv.PageBlock> children = parseChildren(li);
            boolean textOnly = children.size() == 1 && children.get(0) instanceof TL_iv.pageBlockParagraph;
            TL_iv.RichText text = textOnly ? children.get(0).text : new TL_iv.textEmpty();
            boolean checkbox = li.hasAttr("data-checkbox") || li.className().toLowerCase().contains("checkbox");
            if (ordered) {
                TL_iv.PageListOrderedItem item;
                if (textOnly) {
                    TL_iv.TL_pageListOrderedItemText leaf = new TL_iv.TL_pageListOrderedItemText();
                    leaf.text = text;
                    item = leaf;
                } else {
                    TL_iv.TL_pageListOrderedItemBlocks nested = new TL_iv.TL_pageListOrderedItemBlocks();
                    nested.blocks = children;
                    item = nested;
                }
                item.num = counter + ".";
                item.checkbox = checkbox;
                item.checked = li.hasAttr("data-checked");
                orderedList.items.add(item);
            } else {
                TL_iv.PageListItem item;
                if (textOnly) {
                    TL_iv.TL_pageListItemText leaf = new TL_iv.TL_pageListItemText();
                    leaf.text = text;
                    item = leaf;
                } else {
                    TL_iv.TL_pageListItemBlocks nested = new TL_iv.TL_pageListItemBlocks();
                    nested.blocks = children;
                    item = nested;
                }
                item.checkbox = checkbox;
                item.checked = li.hasAttr("data-checked");
                unorderedList.items.add(item);
            }
            counter++;
        }
        return ordered ? orderedList : unorderedList;
    }
}
