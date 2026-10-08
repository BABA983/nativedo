// SPDX-License-Identifier: GPL-2.0-or-later
// Container reconstruction adapted from org/telegram/ui/iv/RichEditorListView.java.
package io.github.baba983.nativedo.topic;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.RichHtml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a prepared Discourse post body into the rich-message model upstream's cells draw.
 *
 * <p>The preparation itself (unwrapping Discourse's presentational {@code <div>}s, replacing post
 * images with media references) happens in {@link DiscourseImageBridge}, because it also has to
 * fetch those images; this class is only the last step into {@link RichHtml}, upstream's own
 * importer.</p>
 */
public final class DiscourseCookedHtml {

    public static TL_iv.RichMessage richMessage(PreparedPostBody body) {
        TL_iv.RichMessage richMessage = new TL_iv.RichMessage();
        DiscourseCookedHtml parsed = new DiscourseCookedHtml(body.getHtml());
        richMessage.blocks = parsed.flattenRange(0, parsed.rows.size(), 0);
        richMessage.photos = new ArrayList<>(body.getPhotos());
        richMessage.documents = new ArrayList<>();
        return richMessage;
    }

    private final List<BlockRow> rows;
    private final Map<Long, TL_iv.RichText> quoteAuthors = new HashMap<>();

    private DiscourseCookedHtml(String html) {
        rows = RichHtml.parse(html, quoteAuthors);
    }

    private ArrayList<TL_iv.PageBlock> flattenRange(int from, int to, int quoteDepth) {
        ArrayList<TL_iv.PageBlock> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            BlockRow row = rows.get(i);
            if (row.detailsEnd) {
                i++;
            } else if (row.quoteIds.size() > quoteDepth) {
                long id = row.quoteIds.get(quoteDepth);
                int end = i + 1;
                while (end < to && rows.get(end).quoteIds.size() > quoteDepth
                        && rows.get(end).quoteIds.get(quoteDepth) == id) {
                    end++;
                }
                ArrayList<TL_iv.PageBlock> inner = flattenRange(i, end, quoteDepth + 1);
                TL_iv.RichText author = quoteAuthors.get(id);
                if (author == null) author = new TL_iv.textEmpty();
                if (inner.size() == 1 && inner.get(0) instanceof TL_iv.pageBlockParagraph) {
                    TL_iv.pageBlockBlockquote quote = new TL_iv.pageBlockBlockquote();
                    quote.text = inner.get(0).text;
                    quote.caption = author;
                    out.add(quote);
                } else {
                    TL_iv.pageBlockBlockquoteBlocks quote = new TL_iv.pageBlockBlockquoteBlocks();
                    quote.blocks = inner;
                    quote.caption = author;
                    out.add(quote);
                }
                i = end;
            } else if (row.block instanceof TL_iv.pageBlockDetails) {
                TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) row.block;
                int end = i + 1;
                int depth = 1;
                while (end < to) {
                    BlockRow child = rows.get(end);
                    if (child.block instanceof TL_iv.pageBlockDetails) depth++;
                    else if (child.detailsEnd && --depth == 0) break;
                    end++;
                }
                details.blocks = flattenRange(i + 1, end, quoteDepth);
                out.add(details);
                i = end + 1;
            } else if (row.level > 0) {
                int[] end = {i};
                out.add(buildListBlock(i, row.level, row.num > 0, end, to, quoteDepth));
                i = end[0];
            } else {
                // Prepared media already names a photo/document. It is not an editor upload.
                out.add(row.block);
                i++;
            }
        }
        return out;
    }

    private TL_iv.PageBlock buildListBlock(int from, int level, boolean ordered,
            int[] end, int limit, int quoteDepth) {
        TL_iv.pageBlockOrderedList orderedList = ordered ? new TL_iv.pageBlockOrderedList() : null;
        TL_iv.pageBlockList unorderedList = ordered ? null : new TL_iv.pageBlockList();
        int counter = 1;
        int i = from;
        while (i < limit) {
            BlockRow row = rows.get(i);
            if (row.detailsEnd || row.quoteIds.size() > quoteDepth
                    || row.level != level || (row.num > 0) != ordered) break;
            // RichHtml.parseList emits one text row per item followed by its nested list rows.
            TL_iv.RichText text = row.block.text == null ? new TL_iv.textEmpty() : row.block.text;
            ArrayList<TL_iv.PageBlock> children = new ArrayList<>();
            int j = i + 1;
            while (j < limit && rows.get(j).level > level
                    && rows.get(j).quoteIds.size() <= quoteDepth && !rows.get(j).detailsEnd) {
                BlockRow child = rows.get(j);
                int[] childEnd = {j};
                children.add(buildListBlock(j, child.level, child.num > 0, childEnd, limit, quoteDepth));
                j = childEnd[0];
            }
            if (!children.isEmpty()) {
                TL_iv.pageBlockParagraph paragraph = new TL_iv.pageBlockParagraph();
                paragraph.text = text;
                children.add(0, paragraph);
            }
            if (ordered) {
                TL_iv.PageListOrderedItem item;
                if (children.isEmpty()) {
                    TL_iv.TL_pageListOrderedItemText leaf = new TL_iv.TL_pageListOrderedItemText();
                    leaf.text = text;
                    item = leaf;
                } else {
                    TL_iv.TL_pageListOrderedItemBlocks nested = new TL_iv.TL_pageListOrderedItemBlocks();
                    nested.blocks = children;
                    item = nested;
                }
                item.num = counter + ".";
                item.checkbox = row.checkbox;
                item.checked = row.checked;
                orderedList.items.add(item);
            } else {
                TL_iv.PageListItem item;
                if (children.isEmpty()) {
                    TL_iv.TL_pageListItemText leaf = new TL_iv.TL_pageListItemText();
                    leaf.text = text;
                    item = leaf;
                } else {
                    TL_iv.TL_pageListItemBlocks nested = new TL_iv.TL_pageListItemBlocks();
                    nested.blocks = children;
                    item = nested;
                }
                item.checkbox = row.checkbox;
                item.checked = row.checked;
                unorderedList.items.add(item);
            }
            counter++;
            i = j;
        }
        end[0] = i;
        return ordered ? orderedList : unorderedList;
    }
}
