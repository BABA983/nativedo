// PARITY-BLOCKER:
// PARITY-BLOCKER:
//   - Exact Telegram media/file loader fields and player/embed behavior are not represented here.
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

    private static final char INLINE_IMAGE_PLACEHOLDER = '\uFFFC';

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
        if (shouldIgnoreBlock(tag)) {
            return;
        }
        String anchorName = parseAnchorName(object);
        if (!isEmpty(anchorName)) {
            blocks.add(new PageBlockAnchor(anchorName));
        }
        if ("p".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(object.optJSONArray("content"), baseUrl);
            text = mediaBlocks.isEmpty() ? text : stripInlineImages(text);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockParagraph(text));
            }
            blocks.addAll(mediaBlocks);
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
        if ("footer".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(object.optJSONArray("content"), baseUrl);
            text = mediaBlocks.isEmpty() ? text : stripInlineImages(text);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockFooter(text));
            }
            blocks.addAll(mediaBlocks);
            return;
        }
        if ("blockquote".equals(tag)) {
            RichText text = parseRichTextObject(object, baseUrl);
            ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(object.optJSONArray("content"), baseUrl);
            text = mediaBlocks.isEmpty() ? text : stripInlineImages(text);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockBlockquote(addEntity(text, "italic"), emptyRichText()));
            }
            blocks.addAll(mediaBlocks);
            return;
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
            PageBlock mediaBlock = parseFigureBlock(object, baseUrl);
            if (mediaBlock != null) {
                blocks.add(mediaBlock);
                return;
            }
        }
        if ("iframe".equals(tag) || "embed".equals(tag) || "object".equals(tag)) {
            blocks.add(parseEmbedBlock(object, baseUrl, emptyRichText(), emptyRichText()));
            return;
        }
        if ("video".equals(tag)) {
            blocks.add(parseVideoBlock(object, baseUrl, emptyRichText(), emptyRichText()));
            return;
        }
        if ("audio".equals(tag)) {
            blocks.add(parseAudioBlock(object, baseUrl, emptyRichText(), emptyRichText()));
            return;
        }
        if ("canvas".equals(tag)) {
            blocks.add(parseUnsupportedBlock(object, baseUrl));
            return;
        }
        if ("cover".equals(tag)) {
            PageBlock coverBlock = parseCoverBlock(object, baseUrl);
            if (coverBlock != null) {
                blocks.add(coverBlock);
            }
            return;
        }
        if ("slideshow".equals(tag) || "gallery".equals(tag)) {
            PageBlockSlideshow slideshow = parseSlideshowBlock(object, baseUrl);
            if (slideshow != null) {
                blocks.add(slideshow);
            }
            return;
        }
        if ("collage".equals(tag)) {
            PageBlockCollage collage = parseCollageBlock(object, baseUrl);
            if (collage != null) {
                blocks.add(collage);
            }
            return;
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
            ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(object.optJSONArray("content"), baseUrl);
            text = mediaBlocks.isEmpty() ? text : stripInlineImages(text);
            if (!isEmpty(text.text)) {
                blocks.add(new PageBlockParagraph(text));
            }
            blocks.addAll(mediaBlocks);
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
                    ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(itemContent, baseUrl);
                    if (mediaBlocks.isEmpty() && !isEmpty(text.text)) {
                        items.add(new PageListItemText(text));
                    } else if (!mediaBlocks.isEmpty()) {
                        ArrayList<PageBlock> blocks = new ArrayList<>();
                        RichText stripped = stripInlineImages(text);
                        if (!isEmpty(stripped.text)) {
                            blocks.add(new PageBlockParagraph(stripped));
                        }
                        blocks.addAll(mediaBlocks);
                        items.add(new PageListItemBlocks(blocks));
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
                    ArrayList<PageBlock> mediaBlocks = collectInlineMediaBlocks(itemContent, baseUrl);
                    if (mediaBlocks.isEmpty() && !isEmpty(text.text)) {
                        items.add(new PageListOrderedItemText(num, text));
                    } else if (!mediaBlocks.isEmpty()) {
                        ArrayList<PageBlock> blocks = new ArrayList<>();
                        RichText stripped = stripInlineImages(text);
                        if (!isEmpty(stripped.text)) {
                            blocks.add(new PageBlockParagraph(stripped));
                        }
                        blocks.addAll(mediaBlocks);
                        items.add(new PageListOrderedItemBlocks(num, blocks));
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
                    title = parseRichTextObject((JSONObject) child, baseUrl);
                } else {
                    remainder.put(child);
                }
            }
        }
        ArrayList<PageBlock> blocks = parsePageBlocks(remainder, baseUrl);
        if (isEmpty(title.text) && blocks.isEmpty()) {
            return null;
        }
        return new PageBlockDetails(object.has("open"), title, blocks);
    }

    @Nullable
    private static PageBlock parseFigureBlock(JSONObject figure, @Nullable String baseUrl) {
        JSONArray content = figure.optJSONArray("content");
        if (content == null) {
            return null;
        }
        RichText caption = null;
        RichText credit = null;
        ArrayList<PageBlock> mediaBlocks = new ArrayList<>();
        ArrayList<String> sourceUrls = collectSourceUrls(content, baseUrl);
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String tag = child.optString("tag").toLowerCase();
            if ("figcaption".equals(tag) || "figurecaption".equals(tag) || "caption".equals(tag)) {
                caption = parseRichTextArray(child.optJSONArray("content"), baseUrl);
            } else if (!"source".equals(tag)) {
                collectMediaBlocks(mediaBlocks, child, baseUrl);
            }
        }
        if (mediaBlocks.isEmpty()) {
            PageBlockPhoto imageBlock = parseImageLikeBlock(figure, baseUrl, caption, credit);
            if (imageBlock != null) {
                return imageBlock;
            }
        }
        if (mediaBlocks.isEmpty()) {
            return null;
        }
        if (mediaBlocks.size() == 1 && mediaBlocks.get(0) instanceof PageBlockPhoto && !sourceUrls.isEmpty()) {
            mediaBlocks.set(0, withAdditionalPhotoUrls((PageBlockPhoto) mediaBlocks.get(0), sourceUrls));
        }
        if (mediaBlocks.size() == 1) {
            return withCaption(mediaBlocks.get(0), caption, credit);
        }
        return new PageBlockCollage(mediaBlocks, caption != null ? caption : emptyRichText(), credit != null ? credit : emptyRichText());
    }

    private static void collectMediaBlocks(ArrayList<PageBlock> blocks, JSONObject object, @Nullable String baseUrl) {
        PageBlock media = parseMediaBlock(object, baseUrl, emptyRichText(), emptyRichText());
        if (media != null) {
            blocks.add(media);
            return;
        }
        JSONArray content = object.optJSONArray("content");
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child != null) {
                collectMediaBlocks(blocks, child, baseUrl);
            }
        }
    }

    private static ArrayList<PageBlock> collectInlineMediaBlocks(@Nullable JSONArray content, @Nullable String baseUrl) {
        ArrayList<PageBlock> mediaBlocks = new ArrayList<>();
        if (content == null) {
            return mediaBlocks;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child != null) {
                collectMediaBlocks(mediaBlocks, child, baseUrl);
            }
        }
        return mediaBlocks;
    }

    @Nullable
    private static PageBlock parseMediaBlock(JSONObject object, @Nullable String baseUrl, RichText caption, RichText credit) {
        String tag = object.optString("tag").toLowerCase();
        if ("img".equals(tag) || "picture".equals(tag)) {
            return parseImageLikeBlock(object, baseUrl, caption, credit);
        }
        if ("iframe".equals(tag) || "embed".equals(tag) || "object".equals(tag)) {
            return parseEmbedBlock(object, baseUrl, caption, credit);
        }
        if ("video".equals(tag)) {
            return parseVideoBlock(object, baseUrl, caption, credit);
        }
        if ("audio".equals(tag)) {
            return parseAudioBlock(object, baseUrl, caption, credit);
        }
        if ("canvas".equals(tag)) {
            return parseUnsupportedBlock(object, baseUrl);
        }
        return null;
    }

    private static PageBlock withCaption(PageBlock block, @Nullable RichText caption, @Nullable RichText credit) {
        RichText nextCaption = caption == null ? emptyRichText() : caption;
        RichText nextCredit = credit == null ? emptyRichText() : credit;
        if (block instanceof PageBlockPhoto) {
            PageBlockPhoto photo = (PageBlockPhoto) block;
            if (caption == null || isEmpty(caption.text)) {
                nextCaption = photo.caption;
            }
            if (credit == null || isEmpty(credit.text)) {
                nextCredit = photo.credit;
            }
            return new PageBlockPhoto(photo.url, photo.width, photo.height, photo.alt, nextCaption, nextCredit, photo.urls);
        }
        if (block instanceof PageBlockEmbed) {
            PageBlockEmbed embed = (PageBlockEmbed) block;
            return new PageBlockEmbed(embed.url, embed.html, embed.width, embed.height, embed.fullWidth, embed.allowScrolling, embed.title, nextCaption, nextCredit);
        }
        if (block instanceof PageBlockVideo) {
            PageBlockVideo video = (PageBlockVideo) block;
            return new PageBlockVideo(video.url, video.posterUrl, video.width, video.height, video.autoplay, video.loop, nextCaption, nextCredit);
        }
        if (block instanceof PageBlockAudio) {
            PageBlockAudio audio = (PageBlockAudio) block;
            return new PageBlockAudio(audio.url, audio.title, nextCaption, nextCredit);
        }
        return block;
    }

    private static PageBlockEmbed parseEmbedBlock(JSONObject object, @Nullable String baseUrl, RichText caption, RichText credit) {
        String url = resolveUrl(baseUrl, firstNonEmpty(object.optString("src"), object.optString("data"), object.optString("href")));
        String html = emptyToNull(object.optString("html"));
        String title = emptyToNull(firstNonEmpty(object.optString("title"), object.optString("aria-label"), object.optString("name")));
        int width = parsePositiveInt(object.optString("width"), 0);
        int height = parsePositiveInt(object.optString("height"), 0);
        boolean fullWidth = object.has("full-width") || object.has("data-full-width");
        boolean allowScrolling = !"no".equalsIgnoreCase(object.optString("scrolling"));
        return new PageBlockEmbed(url, html, width, height, fullWidth, allowScrolling, title, caption, credit);
    }

    private static PageBlockVideo parseVideoBlock(JSONObject object, @Nullable String baseUrl, RichText caption, RichText credit) {
        String url = resolveUrl(baseUrl, firstNonEmpty(object.optString("src"), firstSourceUrl(object.optJSONArray("content"))));
        String posterUrl = resolveUrl(baseUrl, object.optString("poster"));
        int width = parsePositiveInt(object.optString("width"), 0);
        int height = parsePositiveInt(object.optString("height"), 0);
        RichText text = parseRichTextObject(object, baseUrl);
        RichText nextCaption = !isEmpty(caption.text) ? caption : text;
        return new PageBlockVideo(url, posterUrl, width, height, object.has("autoplay"), object.has("loop"), nextCaption, credit);
    }

    private static PageBlockAudio parseAudioBlock(JSONObject object, @Nullable String baseUrl, RichText caption, RichText credit) {
        String url = resolveUrl(baseUrl, firstNonEmpty(object.optString("src"), firstSourceUrl(object.optJSONArray("content"))));
        RichText text = parseRichTextObject(object, baseUrl);
        String title = emptyToNull(firstNonEmpty(object.optString("title"), object.optString("aria-label"), text.text));
        return new PageBlockAudio(url, title, caption, credit);
    }

    private static PageBlockUnsupported parseUnsupportedBlock(JSONObject object, @Nullable String baseUrl) {
        return new PageBlockUnsupported(
                emptyToNull(object.optString("tag")),
                emptyToNull(firstNonEmpty(object.optString("title"), object.optString("aria-label"))),
                resolveUrl(baseUrl, firstNonEmpty(object.optString("src"), object.optString("data"), object.optString("href")))
        );
    }

    @Nullable
    private static PageBlock parseCoverBlock(JSONObject object, @Nullable String baseUrl) {
        ArrayList<PageBlock> blocks = parsePageBlocks(object.optJSONArray("content"), baseUrl);
        if (blocks.isEmpty()) {
            PageBlock media = parseMediaBlock(object, baseUrl, emptyRichText(), emptyRichText());
            return media == null ? null : new PageBlockCover(media);
        }
        return new PageBlockCover(blocks.get(0));
    }

    @Nullable
    private static PageBlockSlideshow parseSlideshowBlock(JSONObject object, @Nullable String baseUrl) {
        ArrayList<PageBlock> items = new ArrayList<>();
        collectCollectionItems(items, object.optJSONArray("content"), baseUrl);
        return items.isEmpty() ? null : new PageBlockSlideshow(items, emptyRichText(), emptyRichText());
    }

    @Nullable
    private static PageBlockCollage parseCollageBlock(JSONObject object, @Nullable String baseUrl) {
        ArrayList<PageBlock> items = new ArrayList<>();
        collectCollectionItems(items, object.optJSONArray("content"), baseUrl);
        return items.isEmpty() ? null : new PageBlockCollage(items, emptyRichText(), emptyRichText());
    }

    private static void collectCollectionItems(ArrayList<PageBlock> items, @Nullable JSONArray content, @Nullable String baseUrl) {
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            PageBlock media = parseMediaBlock(child, baseUrl, emptyRichText(), emptyRichText());
            if (media != null) {
                items.add(media);
            } else {
                collectCollectionItems(items, child.optJSONArray("content"), baseUrl);
            }
        }
    }

    @Nullable
    private static PageBlockPhoto parseImageLikeBlock(JSONObject object, @Nullable String baseUrl, @Nullable RichText caption, @Nullable RichText credit) {
        ArrayList<String> sourceUrls = collectSourceUrls(object.optJSONArray("content"), baseUrl);
        ArrayList<String> urls = imageUrls(object, baseUrl, sourceUrls);
        if (urls.isEmpty()) {
            JSONArray content = object.optJSONArray("content");
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject child = content.optJSONObject(i);
                    if (child == null) {
                        continue;
                    }
                    PageBlockPhoto nested = parseImageLikeBlock(child, baseUrl, caption, credit);
                    if (nested != null) {
                        return sourceUrls.isEmpty() ? nested : withAdditionalPhotoUrls(nested, sourceUrls);
                    }
                }
            }
            return null;
        }
        String url = urls.get(0);
        int width = Math.max(0, parsePositiveInt(object.optString("width"), 0));
        int height = Math.max(0, parsePositiveInt(object.optString("height"), 0));
        if (width == 0) {
            width = height;
        }
        if (height == 0) {
            height = width;
        }
        String alt = emptyToNull(object.optString("alt"));
        RichText resolvedCaption = caption != null && !isEmpty(caption.text) ? caption : (alt == null ? emptyRichText() : new RichText(alt, new ArrayList<>()));
        return new PageBlockPhoto(
                url,
                width,
                height,
                alt,
                resolvedCaption,
                credit != null ? credit : emptyRichText(),
                urls
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
        String attributeTitle = object.optString("title");
        if (!isEmpty(attributeTitle)) {
            title = new RichText(attributeTitle, new ArrayList<>());
        }
        JSONArray content = object.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject child = content.optJSONObject(i);
                if (isEmpty(title.text) && child != null && "caption".equalsIgnoreCase(child.optString("tag"))) {
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
                                "end".equals(normalizeAlign(cellObject.optString("align"))),
                                cellObject.has("xcenter") || "center".equals(normalizeAlign(cellObject.optString("align"))),
                                cellObject.has("ycenter") || "middle".equalsIgnoreCase(cellObject.optString("valign")),
                                "bottom".equalsIgnoreCase(cellObject.optString("valign")),
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

    private static void appendRichTextArray(StringBuilder builder, ArrayList<Entity> entities, @Nullable JSONArray array, @Nullable String baseUrl, boolean[] centered) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String) {
                builder.append((String) item);
            } else if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                if ("p".equalsIgnoreCase(object.optString("tag")) && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                appendRichTextObject(builder, entities, object, baseUrl, centered);
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
            String url = firstImageUrl(object, baseUrl);
            String alt = emptyToNull(object.optString("alt"));
            if (isEmpty(url)) {
                if (alt != null) {
                    builder.append(alt);
                }
                return;
            }
            if (builder.length() > 0 && !Character.isWhitespace(builder.charAt(builder.length() - 1))) {
                builder.append(' ');
            }
            int start = builder.length();
            int width = Math.max(0, parsePositiveInt(object.optString("width"), 0));
            int height = Math.max(0, parsePositiveInt(object.optString("height"), 0));
            if (width == 0) {
                width = height;
            }
            if (height == 0) {
                height = width;
            }
            builder.append(INLINE_IMAGE_PLACEHOLDER);
            entities.add(new Entity(
                    "image",
                    start,
                    builder.length(),
                    url,
                    width,
                    height,
                    alt
            ));
            return;
        }
        if ("source".equals(tag) || "track".equals(tag)) {
            String alt = emptyToNull(object.optString("alt"));
            if (alt != null) {
                builder.append(alt);
            }
            return;
        }

        int start = builder.length();
        appendRichTextArray(builder, entities, object.optJSONArray("content"), baseUrl, centered);
        int end = builder.length();

        String anchorName = parseAnchorName(object);
        if (start >= end) {
            if (!isEmpty(anchorName)) {
                entities.add(new Entity("anchor", start, end, anchorName));
            }
            return;
        }

        if (!isEmpty(anchorName)) {
            entities.add(new Entity("anchor", start, end, anchorName));
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
            String href = object.optString("href");
            if (!isEmpty(href)) {
                if (href.startsWith("tel:")) {
                    entities.add(new Entity("phone", start, end, href));
                } else if (href.startsWith("mailto:")) {
                    entities.add(new Entity("email", start, end, href));
                } else {
                    entities.add(new Entity("link", start, end, resolveUrl(baseUrl, href)));
                }
            }
        }
    }

    private static RichText trimRichText(RichText richText) {
        if (richText == null || isEmpty(richText.text)) {
            if (richText != null && hasAnchorEntity(richText.entities)) {
                return richText;
            }
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
            if (entityStart < entityEnd || "anchor".equals(entity.type) && entityStart == entityEnd) {
                entities.add(new Entity(entity.type, entityStart, entityEnd, entity.url, entity.width, entity.height, entity.alt));
            }
        }
        return new RichText(text.substring(start, end), entities, richText.centered);
    }

    private static boolean hasAnchorEntity(ArrayList<Entity> entities) {
        for (int i = 0; i < entities.size(); i++) {
            if ("anchor".equals(entities.get(i).type)) {
                return true;
            }
        }
        return false;
    }

    private static RichText stripInlineImages(RichText richText) {
        if (richText == null || isEmpty(richText.text)) {
            return emptyRichText();
        }
        String text = richText.text;
        int[] offsets = new int[text.length() + 1];
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            offsets[i] = builder.length();
            if (text.charAt(i) != INLINE_IMAGE_PLACEHOLDER) {
                builder.append(text.charAt(i));
            }
        }
        offsets[text.length()] = builder.length();
        ArrayList<Entity> entities = new ArrayList<>();
        for (int i = 0; i < richText.entities.size(); i++) {
            Entity entity = richText.entities.get(i);
            if ("image".equals(entity.type)) {
                continue;
            }
            int start = offsets[Math.max(0, Math.min(offsets.length - 1, entity.start))];
            int end = offsets[Math.max(0, Math.min(offsets.length - 1, entity.end))];
            if (start < end || "anchor".equals(entity.type) && start == end) {
                entities.add(new Entity(entity.type, start, end, entity.url, entity.width, entity.height, entity.alt));
            }
        }
        return trimRichText(new RichText(builder.toString(), entities, richText.centered));
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
                    || "div".equals(tag)
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

    @Nullable
    private static String firstSourceUrl(@Nullable JSONArray content) {
        if (content == null) {
            return null;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String tag = child.optString("tag").toLowerCase();
            if ("source".equals(tag)) {
                String src = firstNonEmpty(child.optString("src"), firstUrlFromSrcSet(child.optString("srcset")));
                if (!isEmpty(src)) {
                    return src;
                }
            }
            String nested = firstSourceUrl(child.optJSONArray("content"));
            if (!isEmpty(nested)) {
                return nested;
            }
        }
        return null;
    }

    private static ArrayList<String> collectSourceUrls(@Nullable JSONArray content, @Nullable String baseUrl) {
        ArrayList<String> urls = new ArrayList<>();
        if (content == null) {
            return urls;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String tag = child.optString("tag").toLowerCase();
            if ("source".equals(tag)) {
                addImageUrl(urls, baseUrl, child.optString("src"));
                addImageUrl(urls, baseUrl, child.optString("data-src"));
                addImageUrl(urls, baseUrl, child.optString("data-original"));
                addImageUrl(urls, baseUrl, child.optString("data-lazy-src"));
                addUrls(urls, urlsFromSrcSet(child.optString("srcset"), baseUrl));
                addUrls(urls, urlsFromSrcSet(child.optString("data-srcset"), baseUrl));
            }
            addUrls(urls, collectSourceUrls(child.optJSONArray("content"), baseUrl));
        }
        return urls;
    }

    @Nullable
    private static String parseAnchorName(JSONObject object) {
        return emptyToNull(firstNonEmpty(object.optString("id"), object.optString("name")));
    }

    private static boolean shouldIgnoreBlock(String tag) {
        return "script".equals(tag)
                || "style".equals(tag)
                || "noscript".equals(tag)
                || "template".equals(tag)
                || "nav".equals(tag)
                || "form".equals(tag)
                || "input".equals(tag)
                || "button".equals(tag)
                || "select".equals(tag)
                || "option".equals(tag)
                || "source".equals(tag)
                || "track".equals(tag)
                || "svg".equals(tag);
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
    private static String firstImageUrl(JSONObject object, @Nullable String baseUrl) {
        ArrayList<String> urls = imageUrls(object, baseUrl, null);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private static ArrayList<String> imageUrls(JSONObject object, @Nullable String baseUrl, @Nullable ArrayList<String> sourceUrls) {
        ArrayList<String> urls = new ArrayList<>();
        addImageUrl(urls, baseUrl, object.optString("src"));
        addUrls(urls, urlsFromSrcSet(object.optString("srcset"), baseUrl));
        addImageUrl(urls, baseUrl, object.optString("data-src"));
        addImageUrl(urls, baseUrl, object.optString("data-original"));
        addImageUrl(urls, baseUrl, object.optString("data-lazy-src"));
        addUrls(urls, urlsFromSrcSet(object.optString("data-srcset"), baseUrl));
        if (sourceUrls != null) {
            addUrls(urls, sourceUrls);
        }
        addImageUrl(urls, baseUrl, object.optString("poster"));
        return urls;
    }

    private static void addImageUrl(ArrayList<String> target, @Nullable String baseUrl, @Nullable String url) {
        String resolved = resolveUrl(baseUrl, url);
        if (isEmpty(resolved)) {
            return;
        }
        try {
            String scheme = URI.create(resolved).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }
        addUrl(target, resolved);
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

    private static ArrayList<String> urlsFromSrcSet(@Nullable String srcset, @Nullable String baseUrl) {
        ArrayList<String> urls = new ArrayList<>();
        if (isEmpty(srcset)) {
            return urls;
        }
        String[] sets = srcset.split(",");
        for (String set : sets) {
            String trimmed = set.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length > 0) {
                addImageUrl(urls, baseUrl, parts[0]);
            }
        }
        return urls;
    }

    private static PageBlockPhoto withAdditionalPhotoUrls(PageBlockPhoto photo, ArrayList<String> urls) {
        ArrayList<String> merged = new ArrayList<>(photo.urls);
        addUrls(merged, urls);
        return new PageBlockPhoto(photo.url, photo.width, photo.height, photo.alt, photo.caption, photo.credit, merged);
    }

    private static void addUrls(ArrayList<String> target, ArrayList<String> urls) {
        for (int i = 0; i < urls.size(); i++) {
            addUrl(target, urls.get(i));
        }
    }

    private static void addUrl(ArrayList<String> target, @Nullable String url) {
        if (isEmpty(url) || target.contains(url)) {
            return;
        }
        target.add(url);
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

    static final class PageBlockAnchor extends PageBlock {
        final String name;

        PageBlockAnchor(String name) {
            this.name = name;
        }
    }

    static final class PageBlockFooter extends PageBlock {
        final RichText text;

        PageBlockFooter(RichText text) {
            this.text = text;
        }
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
        final ArrayList<String> urls;
        int width;
        int height;
        final String alt;
        final RichText caption;
        final RichText credit;

        PageBlockPhoto(String url, int width, int height, String alt, RichText caption, RichText credit) {
            this(url, width, height, alt, caption, credit, null);
        }

        PageBlockPhoto(String url, int width, int height, String alt, RichText caption, RichText credit, @Nullable ArrayList<String> urls) {
            this.url = url;
            this.urls = urls == null ? new ArrayList<>() : new ArrayList<>(urls);
            addUrl(this.urls, url);
            this.width = width;
            this.height = height;
            this.alt = alt;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockCover extends PageBlock {
        final PageBlock block;

        PageBlockCover(PageBlock block) {
            this.block = block;
        }
    }

    static final class PageBlockEmbed extends PageBlock {
        final String url;
        final String html;
        final int width;
        final int height;
        final boolean fullWidth;
        final boolean allowScrolling;
        final String title;
        final RichText caption;
        final RichText credit;

        PageBlockEmbed(String url, String html, int width, int height, boolean fullWidth, boolean allowScrolling, String title, RichText caption, RichText credit) {
            this.url = url;
            this.html = html;
            this.width = width;
            this.height = height;
            this.fullWidth = fullWidth;
            this.allowScrolling = allowScrolling;
            this.title = title;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockVideo extends PageBlock {
        final String url;
        final String posterUrl;
        final int width;
        final int height;
        final boolean autoplay;
        final boolean loop;
        final RichText caption;
        final RichText credit;

        PageBlockVideo(String url, String posterUrl, int width, int height, boolean autoplay, boolean loop, RichText caption, RichText credit) {
            this.url = url;
            this.posterUrl = posterUrl;
            this.width = width;
            this.height = height;
            this.autoplay = autoplay;
            this.loop = loop;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockAudio extends PageBlock {
        final String url;
        final String title;
        final RichText caption;
        final RichText credit;

        PageBlockAudio(String url, String title, RichText caption, RichText credit) {
            this.url = url;
            this.title = title;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockSlideshow extends PageBlock {
        final ArrayList<PageBlock> items;
        final RichText caption;
        final RichText credit;

        PageBlockSlideshow(ArrayList<PageBlock> items, RichText caption, RichText credit) {
            this.items = items;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockCollage extends PageBlock {
        final ArrayList<PageBlock> items;
        final RichText caption;
        final RichText credit;

        PageBlockCollage(ArrayList<PageBlock> items, RichText caption, RichText credit) {
            this.items = items;
            this.caption = caption;
            this.credit = credit;
        }
    }

    static final class PageBlockUnsupported extends PageBlock {
        final String tag;
        final String title;
        final String url;

        PageBlockUnsupported(String tag, String title, String url) {
            this.tag = tag;
            this.title = title;
            this.url = url;
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
        final boolean alignRight;
        final boolean alignCenter;
        final boolean valignMiddle;
        final boolean valignBottom;
        final RichText text;

        PageTableCell(boolean header, int colspan, int rowspan, boolean alignRight, boolean alignCenter, boolean valignMiddle, boolean valignBottom, RichText text) {
            this.header = header;
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.alignRight = alignRight;
            this.alignCenter = alignCenter;
            this.valignMiddle = valignMiddle;
            this.valignBottom = valignBottom;
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
        final int width;
        final int height;
        final String alt;

        Entity(String type, int start, int end, String url) {
            this(type, start, end, url, 0, 0, null);
        }

        Entity(String type, int start, int end, String url, int width, int height, String alt) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.url = url;
            this.width = width;
            this.height = height;
            this.alt = alt;
        }
    }
}
