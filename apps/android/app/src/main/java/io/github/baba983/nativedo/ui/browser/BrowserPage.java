// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/web/WebInstantView.java; org/telegram/tgnet/TLRPC.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses org.json and plain Java model objects inside NativeDo's browser package.
// ADAPTED-BACKEND:
//   - Parses NativeDo's generated `instant.js` content tree into a Telegram-shaped local Page/PageBlock subset.
//   - Resolves relative URLs against the generated page URL because NativeDo's native renderer
//     fetches images and links directly instead of Telegram's cached-page media loader.
// OMITTED:
//   - Telegram TLObject serialization, remote web-page fields, media ids, and page-level RTL/view counters.
// PARITY-BLOCKER:
//   - Telegram media families not represented here are unsupported until parser/model/host support exists.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/WebInstantView.java and org/telegram/tgnet/TLRPC.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;

final class BrowserPage {

    final String url;
    final String title;
    final String sourceName;
    final String byline;
    final String publishedTime;
    final String excerpt;
    final String heroImageUrl;
    final ArrayList<PageBlock> blocks;

    private BrowserPage(
            String url,
            String title,
            String sourceName,
            String byline,
            String publishedTime,
            String excerpt,
            String heroImageUrl,
            ArrayList<PageBlock> blocks
    ) {
        this.url = url;
        this.title = title;
        this.sourceName = sourceName;
        this.byline = byline;
        this.publishedTime = publishedTime;
        this.excerpt = excerpt;
        this.heroImageUrl = heroImageUrl;
        this.blocks = blocks;
    }

    static BrowserPage parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String baseUrl = firstNonEmpty(root.optString("url"), root.optString("pageUrl"));
        return new BrowserPage(
                emptyToNull(baseUrl),
                emptyToNull(root.optString("title")),
                emptyToNull(firstNonEmpty(root.optString("sourceName"), root.optString("siteName"))),
                emptyToNull(root.optString("byline")),
                emptyToNull(root.optString("publishedTime")),
                emptyToNull(root.optString("excerpt")),
                emptyToNull(resolveUrl(baseUrl, root.optString("heroImageUrl"))),
                parsePageBlocks(root.optJSONArray("content"), baseUrl)
        );
    }

    private static ArrayList<PageBlock> parsePageBlocks(@Nullable JSONArray array, @Nullable String baseUrl) {
        ArrayList<PageBlock> blocks = new ArrayList<>();
        if (array == null) {
            return blocks;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String) {
                RichText text = trimRichText(new RichText((String) item, new ArrayList<>()));
                if (!isEmpty(text.text)) {
                    blocks.add(new PageBlockParagraph(text));
                }
            } else if (item instanceof JSONObject) {
                appendPageBlock(blocks, (JSONObject) item, baseUrl);
            }
        }
        return blocks;
    }

    private static void appendPageBlock(ArrayList<PageBlock> blocks, JSONObject object, @Nullable String baseUrl) {
        String tag = object.optString("tag").toLowerCase();
        if ("p".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockParagraph(text));
            }
            return;
        }
        if ("h1".equals(tag) || "h2".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockHeader(text));
            }
            return;
        }
        if ("h3".equals(tag) || "h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockSubheader(text));
            }
            return;
        }
        if ("blockquote".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockBlockquote(addEntity(text, "italic"), emptyRichText()));
                return;
            }
        }
        if ("pre".equals(tag)) {
            RichText text = parseRichTextArray(object.optJSONArray("content"), baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockPreformatted(text.text, emptyToNull(detectCodeLanguage(object))));
            }
            return;
        }
        if ("img".equals(tag)) {
            PageBlockPhoto imageBlock = parseImageLikeBlock(object, baseUrl, null, null);
            if (imageBlock != null) {
                blocks.add(imageBlock);
            }
            return;
        }
        if ("figure".equals(tag) || "picture".equals(tag)) {
            PageBlockPhoto imageBlock = parseFigureBlock(object, baseUrl);
            if (imageBlock != null) {
                blocks.add(imageBlock);
                return;
            }
        }
        if ("table".equals(tag)) {
            PageBlockTable tableBlock = parseTableBlock(object, baseUrl);
            if (tableBlock != null) {
                blocks.add(tableBlock);
            }
            return;
        }
        if ("ul".equals(tag)) {
            PageBlockList list = parseListBlock(object, baseUrl);
            if (list != null) {
                blocks.add(list);
            }
            return;
        }
        if ("ol".equals(tag)) {
            PageBlockOrderedList list = parseOrderedListBlock(object, baseUrl);
            if (list != null) {
                blocks.add(list);
            }
            return;
        }
        if ("details".equals(tag)) {
            PageBlockDetails details = parseDetailsBlock(object, baseUrl);
            if (details != null) {
                blocks.add(details);
            }
            return;
        }
        if ("hr".equals(tag)) {
            blocks.add(new PageBlockDivider());
            return;
        }
        if ("b".equals(tag)
                || "strong".equals(tag)
                || "i".equals(tag)
                || "em".equals(tag)
                || "s".equals(tag)
                || "strike".equals(tag)
                || "del".equals(tag)
                || "a".equals(tag)
                || "code".equals(tag)
                || "mark".equals(tag)
                || "sub".equals(tag)
                || "sup".equals(tag)
                || "span".equals(tag)
                || "small".equals(tag)
                || "kbd".equals(tag)
                || "u".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockParagraph(text));
            }
            return;
        }

        JSONArray content = object.optJSONArray("content");
        if (content != null) {
            ArrayList<PageBlock> nested = parsePageBlocks(content, baseUrl);
            if (!nested.isEmpty()) {
                blocks.addAll(nested);
                return;
            }
        }
        RichText text = parseRichTextObject(object, baseUrl);
        if (!isEmpty(text.text)) {
            blocks.add(new PageBlockParagraph(text));
        }
    }

    @Nullable
    private static PageBlockList parseListBlock(JSONObject object, @Nullable String baseUrl) {
        JSONArray content = object.optJSONArray("content");
        ArrayList<PageListItem> items = new ArrayList<>();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.optJSONObject(i);
                if (item == null || !"li".equalsIgnoreCase(item.optString("tag"))) {
                    continue;
                }
                JSONArray itemContent = item.optJSONArray("content");
                if (isInlineContent(itemContent)) {
                    RichText text = parseRichTextArray(itemContent, baseUrl);
                    if (!isEmpty(text.text)) {
                        items.add(new PageListItemText(text));
                    }
                } else {
                    ArrayList<PageBlock> blocks = parsePageBlocks(itemContent, baseUrl);
                    if (blocks.isEmpty()) {
                        RichText text = parseRichTextObject(item, baseUrl);
                        if (!isEmpty(text.text)) {
                            items.add(new PageListItemText(text));
                        }
                    } else {
                        items.add(new PageListItemBlocks(blocks));
                    }
                }
            }
        }
        return items.isEmpty() ? null : new PageBlockList(items);
    }

    @Nullable
    private static PageBlockOrderedList parseOrderedListBlock(JSONObject object, @Nullable String baseUrl) {
        JSONArray content = object.optJSONArray("content");
        ArrayList<PageListOrderedItem> items = new ArrayList<>();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.optJSONObject(i);
                if (item == null || !"li".equalsIgnoreCase(item.optString("tag"))) {
                    continue;
                }
                String num = emptyToNull(firstNonEmpty(item.optString("value"), item.optString("data-value")));
                JSONArray itemContent = item.optJSONArray("content");
                if (isInlineContent(itemContent)) {
                    RichText text = parseRichTextArray(itemContent, baseUrl);
                    if (!isEmpty(text.text)) {
                        items.add(new PageListOrderedItemText(num, text));
                    }
                } else {
                    ArrayList<PageBlock> blocks = parsePageBlocks(itemContent, baseUrl);
                    if (blocks.isEmpty()) {
                        RichText text = parseRichTextObject(item, baseUrl);
                        if (!isEmpty(text.text)) {
                            items.add(new PageListOrderedItemText(num, text));
                        }
                    } else {
                        items.add(new PageListOrderedItemBlocks(num, blocks));
                    }
                }
            }
        }
        return items.isEmpty() ? null : new PageBlockOrderedList(items);
    }

    @Nullable
    private static PageBlockDetails parseDetailsBlock(JSONObject object, @Nullable String baseUrl) {
        JSONArray content = object.optJSONArray("content");
        JSONArray remainder = new JSONArray();
        RichText title = emptyRichText();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                Object child = content.opt(i);
                if (child instanceof JSONObject && "summary".equalsIgnoreCase(((JSONObject) child).optString("tag")) && isEmpty(title.text)) {
                    title = parseRichTextArray(((JSONObject) child).optJSONArray("content"), baseUrl);
                } else {
                    remainder.put(child);
                }
            }
        }
        if (isEmpty(title.text)) {
            return null;
        }
        return new PageBlockDetails(object.has("open"), title, parsePageBlocks(remainder, baseUrl));
    }

    @Nullable
    private static PageBlockPhoto parseFigureBlock(JSONObject figure, @Nullable String baseUrl) {
        JSONArray content = figure.optJSONArray("content");
        if (content == null) {
            return null;
        }
        RichText caption = null;
        RichText credit = null;
        PageBlockPhoto imageBlock = null;
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String tag = child.optString("tag").toLowerCase();
            if ("figcaption".equals(tag) || "caption".equals(tag)) {
                caption = parseRichTextArray(child.optJSONArray("content"), baseUrl);
            } else if ("img".equals(tag) || "picture".equals(tag)) {
                imageBlock = parseImageLikeBlock(child, baseUrl, null, null);
            }
        }
        if (imageBlock == null) {
            imageBlock = parseImageLikeBlock(figure, baseUrl, null, null);
        }
        if (imageBlock == null) {
            return null;
        }
        return new PageBlockPhoto(
                imageBlock.url,
                imageBlock.width,
                imageBlock.height,
                imageBlock.alt,
                caption != null ? caption : imageBlock.caption,
                credit != null ? credit : imageBlock.credit
        );
    }

    @Nullable
    private static PageBlockPhoto parseImageLikeBlock(JSONObject object, @Nullable String baseUrl, @Nullable RichText caption, @Nullable RichText credit) {
        String url = resolveUrl(baseUrl, firstNonEmpty(
                object.optString("src"),
                firstUrlFromSrcSet(object.optString("srcset")),
                object.optString("data-src"),
                object.optString("poster")
        ));
        if (isEmpty(url)) {
            JSONArray content = object.optJSONArray("content");
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject child = content.optJSONObject(i);
                    if (child == null) {
                        continue;
                    }
                    PageBlockPhoto nested = parseImageLikeBlock(child, baseUrl, caption, credit);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
            return null;
        }
        return new PageBlockPhoto(
                url,
                Math.max(0, parsePositiveInt(object.optString("width"), 0)),
                Math.max(0, parsePositiveInt(object.optString("height"), 0)),
                emptyToNull(object.optString("alt")),
                caption != null ? caption : emptyRichText(),
                credit != null ? credit : emptyRichText()
        );
    }

    @Nullable
    private static PageBlockTable parseTableBlock(JSONObject object, @Nullable String baseUrl) {
        ArrayList<PageTableRow> rows = new ArrayList<>();
        appendTableRows(rows, object.optJSONArray("content"), baseUrl);
        if (rows.isEmpty()) {
            return null;
        }
        RichText title = emptyRichText();
        JSONArray content = object.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject child = content.optJSONObject(i);
                if (child != null && "caption".equalsIgnoreCase(child.optString("tag"))) {
                    title = parseRichTextArray(child.optJSONArray("content"), baseUrl);
                    break;
                }
            }
        }
        return new PageBlockTable(title, rows, true, true);
    }

    private static void appendTableRows(ArrayList<PageTableRow> rows, @Nullable JSONArray content, @Nullable String baseUrl) {
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String tag = child.optString("tag").toLowerCase();
            if ("tr".equals(tag)) {
                ArrayList<PageTableCell> cells = new ArrayList<>();
                JSONArray cellContent = child.optJSONArray("content");
                if (cellContent != null) {
                    for (int j = 0; j < cellContent.length(); j++) {
                        JSONObject cellObject = cellContent.optJSONObject(j);
                        if (cellObject == null) {
                            continue;
                        }
                        String cellTag = cellObject.optString("tag").toLowerCase();
                        if (!"td".equals(cellTag) && !"th".equals(cellTag)) {
                            continue;
                        }
                        RichText text = parseRichTextArray(cellObject.optJSONArray("content"), baseUrl);
                        if ("th".equals(cellTag) || cellObject.has("bold")) {
                            text = addEntity(text, "bold");
                        }
                        if (cellObject.has("italic")) {
                            text = addEntity(text, "italic");
                        }
                        cells.add(new PageTableCell(
                                "th".equals(cellTag),
                                Math.max(1, parsePositiveInt(cellObject.optString("colspan"), 1)),
                                Math.max(1, parsePositiveInt(cellObject.optString("rowspan"), 1)),
                                cellObject.has("xcenter") ? "center" : normalizeAlign(cellObject.optString("align")),
                                text
                        ));
                    }
                }
                if (!cells.isEmpty()) {
                    rows.add(new PageTableRow(cells));
                }
            } else if (!"caption".equals(tag)) {
                appendTableRows(rows, child.optJSONArray("content"), baseUrl);
            }
        }
    }

    private static RichText parseRichTextObject(@Nullable JSONObject object, @Nullable String baseUrl) {
        if (object == null) {
            return emptyRichText();
        }
        StringBuilder builder = new StringBuilder();
        ArrayList<Entity> entities = new ArrayList<>();
        boolean[] centered = new boolean[1];
        appendRichTextObject(builder, entities, object, baseUrl, centered);
        return trimRichText(new RichText(builder.toString(), entities, centered[0]));
    }

    private static RichText parseRichTextArray(@Nullable JSONArray array, @Nullable String baseUrl) {
        if (array == null) {
            return emptyRichText();
        }
        StringBuilder builder = new StringBuilder();
        ArrayList<Entity> entities = new ArrayList<>();
        boolean[] centered = new boolean[1];
        appendRichTextArray(builder, entities, array, baseUrl, centered);
        return trimRichText(new RichText(builder.toString(), entities, centered[0]));
    }

    private static void appendRichTextArray(StringBuilder builder, ArrayList<Entity> entities, JSONArray array, @Nullable String baseUrl, boolean[] centered) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String) {
                builder.append((String) item);
            } else if (item instanceof JSONObject) {
                appendRichTextObject(builder, entities, (JSONObject) item, baseUrl, centered);
            }
        }
    }

    private static void appendRichTextObject(StringBuilder builder, ArrayList<Entity> entities, JSONObject object, @Nullable String baseUrl, boolean[] centered) {
        String tag = object.optString("tag").toLowerCase();
        if (object.has("xcenter")) {
            centered[0] = true;
        }
        if ("br".equals(tag)) {
            builder.append('\n');
            return;
        }
        if ("img".equals(tag)) {
            String alt = emptyToNull(object.optString("alt"));
            if (alt != null) {
                builder.append(alt);
            }
            return;
        }

        int start = builder.length();
        appendRichTextArray(builder, entities, object.optJSONArray("content"), baseUrl, centered);
        int end = builder.length();

        if ("p".equals(tag) && end > start && (end == 0 || builder.charAt(end - 1) != '\n')) {
            builder.append('\n');
            end = builder.length();
        }
        if (start >= end) {
            return;
        }

        if ("strong".equals(tag) || "b".equals(tag) || object.has("bold")) {
            entities.add(new Entity("bold", start, end, null));
        }
        if ("em".equals(tag) || "i".equals(tag) || object.has("italic")) {
            entities.add(new Entity("italic", start, end, null));
        }
        if ("s".equals(tag) || "strike".equals(tag) || "del".equals(tag)) {
            entities.add(new Entity("strike", start, end, null));
        }
        if ("code".equals(tag) || "pre".equals(tag)) {
            entities.add(new Entity("inline_code", start, end, null));
        }
        if ("u".equals(tag)) {
            entities.add(new Entity("underline", start, end, null));
        }
        if ("mark".equals(tag)) {
            entities.add(new Entity("highlight", start, end, null));
        }
        if ("sup".equals(tag)) {
            entities.add(new Entity("superscript", start, end, null));
        }
        if ("sub".equals(tag)) {
            entities.add(new Entity("subscript", start, end, null));
        }
        if ("small".equals(tag)) {
            entities.add(new Entity("small", start, end, null));
        }
        if ("kbd".equals(tag)) {
            entities.add(new Entity("kbd", start, end, null));
        }
        if ("a".equals(tag)) {
            String href = resolveUrl(baseUrl, object.optString("href"));
            if (!isEmpty(href)) {
                entities.add(new Entity("link", start, end, href));
            }
        }
    }

    private static RichText trimRichText(RichText richText) {
        if (richText == null || isEmpty(richText.text)) {
            return emptyRichText();
        }
        String text = richText.text;
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start == 0 && end == text.length()) {
            return richText;
        }
        ArrayList<Entity> entities = new ArrayList<>();
        for (int i = 0; i < richText.entities.size(); i++) {
            Entity entity = richText.entities.get(i);
            int entityStart = Math.max(0, entity.start - start);
            int entityEnd = Math.min(end - start, Math.max(0, entity.end - start));
            if (entityStart < entityEnd) {
                entities.add(new Entity(entity.type, entityStart, entityEnd, entity.url));
            }
        }
        return new RichText(text.substring(start, end), entities, richText.centered);
    }

    private static RichText addEntity(RichText richText, String type) {
        if (richText == null || isEmpty(richText.text)) {
            return emptyRichText();
        }
        ArrayList<Entity> entities = new ArrayList<>(richText.entities);
        entities.add(new Entity(type, 0, richText.text.length(), null));
        return new RichText(richText.text, entities, richText.centered);
    }

    private static RichText emptyRichText() {
        return new RichText("", new ArrayList<>(), false);
    }

    @Nullable
    private static String detectCodeLanguage(JSONObject object) {
        String className = firstNonEmpty(object.optString("class"), object.optString("className"));
        if (isEmpty(className)) {
            JSONArray content = object.optJSONArray("content");
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject child = content.optJSONObject(i);
                    if (child == null) {
                        continue;
                    }
                    String nested = detectCodeLanguage(child);
                    if (!isEmpty(nested)) {
                        return nested;
                    }
                }
            }
            return null;
        }
        String[] parts = className.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("language-")) {
                return part.substring("language-".length());
            }
            if (part.startsWith("lang-")) {
                return part.substring("lang-".length());
            }
        }
        return null;
    }

    private static boolean isInlineContent(@Nullable JSONArray content) {
        if (content == null) {
            return true;
        }
        for (int i = 0; i < content.length(); i++) {
            Object item = content.opt(i);
            if (item instanceof String) {
                continue;
            }
            if (!(item instanceof JSONObject)) {
                return false;
            }
            JSONObject object = (JSONObject) item;
            String tag = object.optString("tag").toLowerCase();
            if ("b".equals(tag)
                    || "strong".equals(tag)
                    || "span".equals(tag)
                    || "img".equals(tag)
                    || "i".equals(tag)
                    || "em".equals(tag)
                    || "s".equals(tag)
                    || "strike".equals(tag)
                    || "del".equals(tag)
                    || "a".equals(tag)
                    || "code".equals(tag)
                    || "mark".equals(tag)
                    || "sub".equals(tag)
                    || "sup".equals(tag)
                    || "u".equals(tag)
                    || "small".equals(tag)
                    || "kbd".equals(tag)
                    || "br".equals(tag)) {
                if (!isInlineContent(object.optJSONArray("content"))) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private static int parsePositiveInt(@Nullable String value, int fallback) {
        if (isEmpty(value)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeAlign(@Nullable String align) {
        if (isEmpty(align)) {
            return "start";
        }
        String normalized = align.trim().toLowerCase();
        if ("center".equals(normalized) || "middle".equals(normalized)) {
            return "center";
        }
        if ("right".equals(normalized) || "end".equals(normalized)) {
            return "end";
        }
        return "start";
    }

    @Nullable
    private static String firstUrlFromSrcSet(@Nullable String srcset) {
        if (isEmpty(srcset)) {
            return null;
        }
        String[] sets = srcset.split(",");
        for (String set : sets) {
            String trimmed = set.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length > 0 && !isEmpty(parts[0])) {
                return parts[0];
            }
        }
        return null;
    }

    @Nullable
    private static String resolveUrl(@Nullable String baseUrl, @Nullable String candidate) {
        if (isEmpty(candidate)) {
            return null;
        }
        if (isEmpty(baseUrl)) {
            return candidate;
        }
        try {
            return URI.create(baseUrl).resolve(candidate).toString();
        } catch (Exception ignored) {
            return candidate;
        }
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return isEmpty(value) ? null : value;
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.length() == 0 || "null".equals(value);
    }

    abstract static class PageBlock {
        boolean first;
        boolean bottom;
        int level;
        int cachedWidth;
        int cachedHeight;
    }

    static final class PageBlockKicker extends PageBlock {
        final CharSequence text;

        PageBlockKicker(CharSequence text) {
            this.text = text;
        }
    }

    static final class PageBlockTitle extends PageBlock {
        final RichText text;

        PageBlockTitle(RichText text) {
            this.text = text;
        }
    }

    static final class PageBlockAuthorDate extends PageBlock {
        final CharSequence text;

        PageBlockAuthorDate(CharSequence text) {
            this.text = text;
        }
    }

    static final class PageBlockSubtitle extends PageBlock {
        final RichText text;

        PageBlockSubtitle(RichText text) {
            this.text = text;
        }
    }

    static final class PageBlockSource extends PageBlock {
        final String url;
        final RichText text;

        PageBlockSource(String url) {
            this.url = url;
            ArrayList<Entity> entities = new ArrayList<>();
            entities.add(new Entity("link", 0, url.length(), url));
            text = new RichText(url, entities);
        }
    }

    static final class PageBlockHeader extends PageBlock {
        final RichText text;

        PageBlockHeader(RichText text) {
            this.text = text;
        }
    }

    static final class PageBlockSubheader extends PageBlock {
        final RichText text;

        PageBlockSubheader(RichText text) {
            this.text = text;
        }
    }

    static final class PageBlockParagraph extends PageBlock {
        final RichText text;

        PageBlockParagraph(RichText text) {
            this.text = text;
        }
    }

    static final class PageBlockPullquote extends PageBlock {
        final RichText text;
        final RichText caption;

        PageBlockPullquote(RichText text, RichText caption) {
            this.text = text;
            this.caption = caption;
        }
    }

    static final class PageBlockBlockquote extends PageBlock {
        final RichText text;
        final RichText caption;

        PageBlockBlockquote(RichText text, RichText caption) {
            this.text = text;
            this.caption = caption;
        }
    }

    static final class PageBlockList extends PageBlock {
        final ArrayList<PageListItem> items;

        PageBlockList(ArrayList<PageListItem> items) {
            this.items = items;
        }
    }

    static final class PageBlockOrderedList extends PageBlock {
        final ArrayList<PageListOrderedItem> items;

        PageBlockOrderedList(ArrayList<PageListOrderedItem> items) {
            this.items = items;
        }
    }

    abstract static class PageListItem {
    }

    static final class PageListItemText extends PageListItem {
        final RichText text;

        PageListItemText(RichText text) {
            this.text = text;
        }
    }

    static final class PageListItemBlocks extends PageListItem {
        final ArrayList<PageBlock> blocks;

        PageListItemBlocks(ArrayList<PageBlock> blocks) {
            this.blocks = blocks;
        }
    }

    abstract static class PageListOrderedItem {
        final String num;

        PageListOrderedItem(@Nullable String num) {
            this.num = num;
        }
    }

    static final class PageListOrderedItemText extends PageListOrderedItem {
        final RichText text;

        PageListOrderedItemText(@Nullable String num, RichText text) {
            super(num);
            this.text = text;
        }
    }

    static final class PageListOrderedItemBlocks extends PageListOrderedItem {
        final ArrayList<PageBlock> blocks;

        PageListOrderedItemBlocks(@Nullable String num, ArrayList<PageBlock> blocks) {
            super(num);
            this.blocks = blocks;
        }
    }

    static final class PageBlockListParent extends PageBlock {
        PageBlockList pageBlockList;
        final ArrayList<PageBlockListItem> items = new ArrayList<>();
        int maxNumWidth;
        int level;
    }

    static final class PageBlockListItem extends PageBlock {
        PageBlockListParent parent;
        PageBlock blockItem;
        RichText textItem;
        String num;
        int index = Integer.MAX_VALUE;
    }

    static final class PageBlockOrderedListParent extends PageBlock {
        PageBlockOrderedList pageBlockOrderedList;
        final ArrayList<PageBlockOrderedListItem> items = new ArrayList<>();
        int maxNumWidth;
        int level;
    }

    static final class PageBlockOrderedListItem extends PageBlock {
        PageBlockOrderedListParent parent;
        PageBlock blockItem;
        RichText textItem;
        String num;
        int index = Integer.MAX_VALUE;
    }

    static final class PageBlockPreformatted extends PageBlock {
        final String text;
        final String language;

        PageBlockPreformatted(String text, String language) {
            this.text = text;
            this.language = language;
        }
    }

    static final class PageBlockDivider extends PageBlock {
    }

    static final class PageBlockDetails extends PageBlock {
        boolean open;
        final RichText title;
        final ArrayList<PageBlock> blocks;

        PageBlockDetails(boolean open, RichText title, ArrayList<PageBlock> blocks) {
            this.open = open;
            this.title = title;
            this.blocks = blocks;
        }
    }

    static final class PageBlockDetailsChild extends PageBlock {
        PageBlock parent;
        PageBlock block;
    }

    static final class PageBlockDetailsBottom extends PageBlock {
        PageBlockDetails parent;
    }

    static final class PageBlockPhoto extends PageBlock {
        final String url;
        int width;
        int height;
        final String alt;
        final RichText caption;
        final RichText credit;

        PageBlockPhoto(String url, int width, int height, String alt, RichText caption, RichText credit) {
            this.url = url;
            this.width = width;
            this.height = height;
            this.alt = alt;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockTable extends PageBlock {
        final RichText title;
        final ArrayList<PageTableRow> rows;
        final boolean bordered;
        final boolean striped;

        PageBlockTable(RichText title, ArrayList<PageTableRow> rows, boolean bordered, boolean striped) {
            this.title = title;
            this.rows = rows;
            this.bordered = bordered;
            this.striped = striped;
        }
    }

    static final class PageTableRow {
        final ArrayList<PageTableCell> cells;

        PageTableRow(ArrayList<PageTableCell> cells) {
            this.cells = cells;
        }
    }

    static final class PageTableCell {
        final boolean header;
        final int colspan;
        final int rowspan;
        final String align;
        final RichText text;

        PageTableCell(boolean header, int colspan, int rowspan, String align, RichText text) {
            this.header = header;
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.align = align;
            this.text = text;
        }
    }

    static final class RichText {
        final String text;
        final ArrayList<Entity> entities;
        final boolean centered;

        RichText(String text, ArrayList<Entity> entities) {
            this(text, entities, false);
        }

        RichText(String text, ArrayList<Entity> entities, boolean centered) {
            this.text = text == null ? "" : text;
            this.entities = entities;
            this.centered = centered;
        }
    }

    static final class Entity {
        final String type;
        final int start;
        final int end;
        final String url;

        Entity(String type, int start, int end, String url) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.url = url;
        }
    }
}
