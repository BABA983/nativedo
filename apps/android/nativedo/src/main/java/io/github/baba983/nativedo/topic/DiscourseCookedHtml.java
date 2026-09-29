package io.github.baba983.nativedo.topic;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.RichHtml;

import java.util.ArrayList;

/**
 * Turns a Discourse post's cooked HTML into the rich-message model that upstream's
 * {@link RichHtml} understands.
 *
 * <p>Discourse wraps a few block elements in presentational {@code <div>}s. Upstream's importer
 * dispatches on the tag of each direct child and treats an unknown {@code <div>} as a paragraph, so
 * a wrapped element is flattened into one run of text: a {@code <table>} becomes its cells in a
 * column instead of a grid. Unwrapping the wrapper is what lets the importer see the real block, so
 * the fix belongs here rather than in the upstream file.</p>
 */
public final class DiscourseCookedHtml {

    /** Discourse's presentational wrapper around a table. */
    private static final String TABLE_WRAPPER = "div.md-table";

    public static TL_iv.RichMessage richMessage(String cookedHtml) {
        TL_iv.RichMessage richMessage = new TL_iv.RichMessage();
        richMessage.blocks = new ArrayList<>();
        for (BlockRow row : RichHtml.parse(unwrapDiscourseWrappers(cookedHtml))) {
            richMessage.blocks.add(row.block);
        }
        return richMessage;
    }

    static String unwrapDiscourseWrappers(String cookedHtml) {
        if (cookedHtml == null || cookedHtml.isEmpty()) {
            return "";
        }
        Document document = Jsoup.parseBodyFragment(cookedHtml);
        for (Element wrapper : document.select(TABLE_WRAPPER)) {
            wrapper.unwrap();
        }
        return document.body().html();
    }

    private DiscourseCookedHtml() {
    }
}