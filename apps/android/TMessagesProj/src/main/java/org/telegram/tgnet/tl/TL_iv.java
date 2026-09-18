/*
 * Host surface for Telegram TL_iv types: the instant-view page/block/rich-text
 * model, used by the article viewer and the post-body table pipeline.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * The pin's instant-view model, carried as data holders: 12.10.2 keeps these
 * types in TL_iv and this tree moved them out of TLRPC to match, with the field
 * subset our ports read and write (no serializers). The classes keep the pin's
 * names, so a later import of upstream's article-viewer code spells the same
 * types.
 */
public final class TL_iv {
    private TL_iv() {}

    public static class PageBlock extends TLObject {
        public int level;
        public boolean bottom;
        /**
         * A6 (partial): pin {@code quoteLevels} bitmask — bit i set => draw a vertical
         * quote gutter at layer i ({@code padx + i*14} dp). Local producers currently leave
         * this at 0; {@link ArticleViewer#drawQuoteLines} falls back to single-level
         * {@link #level} drawing (pin legacy path).
         */
        public int quoteLevels;
        public int mid;
        public long groupId;
        public int cachedWidth;
        public int cachedHeight;
        public TLRPC.PhotoSize thumb;
        public TLObject thumbObject;
        public RichText title;
        public RichText text;
        public RichText subtitle;
        public PageCaption caption;
        public TLRPC.Photo photo;
        public TLRPC.Document document;
        public String url;
        public String html;
        public long poster_photo_id;
        public String author;
        public int date;
        public int w;
        public int h;
        public long photo_id;
        public long video_id;
        public long audio_id;
        public long channel_id;
        public TLRPC.GeoPoint geo;
        public int zoom;
        public PageBlock block;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public boolean first;
    }

    public static class pageBlockUnsupported extends PageBlock {
    }

    public static class pageBlockTitle extends PageBlock {
    }

    public static class pageBlockSubtitle extends PageBlock {
    }

    public static class pageBlockAuthorDate extends PageBlock {
        public RichText author;
        public int published_date;
    }

    public static class pageBlockHeader extends PageBlock {
    }

    public static class pageBlockSubheader extends PageBlock {
    }

    public static class pageBlockHeading1 extends PageBlock {
    }

    public static class pageBlockHeading2 extends PageBlock {
    }

    public static class pageBlockHeading3 extends PageBlock {
    }

    public static class pageBlockHeading4 extends PageBlock {
    }

    public static class pageBlockHeading5 extends PageBlock {
    }

    public static class pageBlockHeading6 extends PageBlock {
    }

    public static class pageBlockKicker extends PageBlock {
    }

    public static class pageBlockParagraph extends PageBlock {
    }

    public static class pageBlockPreformatted extends PageBlock {
        public String language;
    }

    public static class pageBlockFooter extends PageBlock {
    }

    public static class pageBlockDivider extends PageBlock {
    }

    public static class pageBlockAnchor extends PageBlock {
        public String name;
    }

    public static class pageBlockBlockquote extends PageBlock {
        public RichText caption;
    }

    public static class pageBlockBlockquoteBlocks extends PageBlock {
        public RichText caption;
    }

    public static class pageBlockPullquote extends PageBlock {
        public RichText caption;
    }

    public static class pageBlockCover extends PageBlock {
        public PageBlock cover;
    }

    public static class pageBlockEmbed extends PageBlock {
        public boolean full_width;
        public boolean allow_scrolling;
    }

    public static class pageBlockEmbedPost extends PageBlock {
        public String webpage_id;
        public long author_photo_id;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
    }

    public static class pageBlockCollage extends PageBlock {
        public ArrayList<PageBlock> items = new ArrayList<>();
    }

    public static class pageBlockSlideshow extends PageBlock {
        public ArrayList<PageBlock> items = new ArrayList<>();
    }

    public static class pageBlockChannel extends PageBlock {
        public TLRPC.Chat channel;
    }

    public static class pageBlockAudio extends PageBlock {
    }

    public static class pageBlockMap extends PageBlock {
    }

    public static class pageBlockDetails extends PageBlock {
        public boolean open;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
    }

    public static class pageBlockTable extends PageBlock {
        public boolean bordered;
        public boolean striped;
        public ArrayList<pageTableRow> rows = new ArrayList<>();
    }

    public static class pageBlockRelatedArticles extends PageBlock {
        public RichText title;
        public ArrayList<pageRelatedArticle> articles = new ArrayList<>();
    }

    public static class pageBlockList extends PageBlock {
        public ArrayList<PageListItem> items = new ArrayList<>();
        public boolean ordered;
    }

    public static class pageBlockOrderedList extends PageBlock {
        public ArrayList<PageListOrderedItem> items = new ArrayList<>();
    }

    public static class pageBlockPhoto extends PageBlock {
    }

    public static class pageBlockVideo extends PageBlock {
    }

    public static class PageCaption extends TLObject {
        public RichText text;
        public RichText credit;
    }

    public static class PageListItem extends TLObject {
        public RichText text;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
    }

    public static class TL_pageListItemText extends PageListItem {
    }

    public static class TL_pageListItemBlocks extends PageListItem {
    }

    public static class PageListOrderedItem extends TLObject {
        public String num;
        public RichText text;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
    }

    public static class TL_pageListOrderedItemText extends PageListOrderedItem {
    }

    public static class TL_pageListOrderedItemBlocks extends PageListOrderedItem {
    }

    public static class pageTableRow extends TLObject {
        public ArrayList<pageTableCell> cells = new ArrayList<>();
    }

    public static class pageTableCell extends TLObject {
        public int flags;
        public boolean header;
        public boolean align_center;
        public boolean align_right;
        public boolean valign_middle;
        public boolean valign_bottom;
        public int colspan = 1;
        public int rowspan = 1;
        public RichText text;
    }

    public static class pageRelatedArticle extends TLObject {
        public String url;
        public long webpage_id;
        public long photo_id;
        public String title;
        public String description;
        public TLRPC.Photo photo;
        public String author;
        public int published_date;
    }

    public static class RichText extends TLObject {
        public String url;
        public long webpage_id;
        public String email;
        public RichText text;
        public ArrayList<RichText> texts = new ArrayList<>();
        public RichText parentRichText;
    }

    public static class textEmpty extends RichText {
    }

    public static class textPlain extends RichText {
        public String text;
    }

    public static class textBold extends RichText {
    }

    public static class textItalic extends RichText {
    }

    public static class textUnderline extends RichText {
    }

    public static class textStrike extends RichText {
    }

    public static class textFixed extends RichText {
    }

    public static class textUrl extends RichText {
    }

    public static class textEmail extends RichText {
    }

    public static class textConcat extends RichText {
    }

    public static class textSubscript extends RichText {
    }

    public static class textSuperscript extends RichText {
    }

    public static class textMarked extends RichText {
    }

    public static class textSpoiler extends RichText {
    }

    public static class textPhone extends RichText {
        public String phone;
    }

    public static class textImage extends RichText {
        public long photo_id;
        public long document_id;
        public int w;
        public int h;
    }

    public static class textAnchor extends RichText {
        public String name;
    }

    public static class Page extends TLObject {
        public int flags;
        public String url;
        public boolean part;
        public boolean rtl;
        public boolean web;
        public ArrayList<PageBlock> blocks = new ArrayList<>();
        public ArrayList<TLRPC.Photo> photos = new ArrayList<>();
        public ArrayList<TLRPC.Document> documents = new ArrayList<>();
        public ArrayList<Page> cached_pages = new ArrayList<>();
        public int views;
    }

    public static class TL_page extends Page {
    }

    public static class TL_pagePart_layer82 extends TL_page {
    }
}
