// PARITY-BLOCKER:
// PARITY-BLOCKER:
//   - Full TextSelectionHelper drawing/selection overlays remain omitted from local ArticleViewer cells.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/WebInstantView.java and org/telegram/ui/ArticleViewer.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.LruCache;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.BitmapDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
// TODO(photoviewer): re-enable when telegram/messenger/ImageLoader compiles.
// import org.telegram.messenger.ImageLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
// TODO(photoviewer): re-enable when telegram/ui/PhotoViewer can compile.
// import org.telegram.ui.PhotoViewer;

class BrowserInstantViewFragment extends BaseFragment {

    private static final int ROW_PARAGRAPH = 0;
    private static final int ROW_HEADER = 1;
    private static final int ROW_DIVIDER = 2;
    private static final int ROW_EMBED = 3;
    private static final int ROW_SUBTITLE = 4;
    private static final int ROW_VIDEO = 5;
    private static final int ROW_PULLQUOTE = 6;
    private static final int ROW_BLOCKQUOTE = 7;
    private static final int ROW_SLIDESHOW = 8;
    private static final int ROW_PHOTO = 9;
    private static final int ROW_AUTHOR_DATE = 10;
    private static final int ROW_TITLE = 11;
    private static final int ROW_LIST_ITEM = 12;
    private static final int ROW_FOOTER = 13;
    private static final int ROW_CODE = 14;
    private static final int ROW_SUBHEADER = 15;
    private static final int ROW_COLLAGE = 17;
    private static final int ROW_AUDIO = 19;
    private static final int ROW_KICKER = 20;
    private static final int ROW_ORDERED_LIST_ITEM = 21;
    private static final int ROW_DETAILS = 24;
    private static final int ROW_TABLE = 25;
    private static final int ROW_DETAILS_BOTTOM = 27;
    private static final int ROW_SOURCE = 29;
    private static final int ROW_UNSUPPORTED = 30;
    private static final int ROW_SHEET_PADDING = Integer.MAX_VALUE - 1;

    private static final ExecutorService IMAGE_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * URL → JSON cache for in-document instant-view links. When a link inside an instant view
     * matches a previously generated URL, opening the link pushes a new instant-view fragment
     * onto the host ActionBarLayout instead of bouncing to the external browser.
     */
    private static final LruCache<String, String> INSTANT_VIEW_JSON_CACHE = new LruCache<>(16);

    static void cacheInstantViewJson(@Nullable String url, @Nullable String json) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(json)) {
            return;
        }
        INSTANT_VIEW_JSON_CACHE.put(url, json);
    }

    static void cacheInstantImage(@Nullable String url, @Nullable Bitmap bitmap) {
        if (TextUtils.isEmpty(url) || bitmap == null) {
            return;
        }
        // TODO(photoviewer): ImageLoader.put(url, bitmap);
    }

    @Nullable
    static String getCachedInstantViewJson(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        return INSTANT_VIEW_JSON_CACHE.get(url);
    }

    private final String sourceUrl;
    private final String json;
    private final ArrayList<BrowserPage.PageBlock> blocks = new ArrayList<>();
    private final ArrayList<BrowserPage.PageBlock> localBlocks = new ArrayList<>();
    private final ArrayList<BrowserPage.PageBlockPhoto> photoBlocks = new ArrayList<>();
    private final HashMap<String, Integer> anchors = new HashMap<>();
    private BrowserPage cachedPage;

    private RecyclerView recyclerView;
    private TextView emptyView;
    private FrameLayout contentRoot;
    private InstantAdapter instantAdapter;
    private int currentHeaderHeight;
    @Nullable private Delegate delegate;
    @Nullable private ValueAnimator headerAnimator;
    @Nullable private int[] itemHeights;
    @Nullable private int[] sumItemHeights;
    private int fullContentHeight;
    private int contentHeightWidth;
    private boolean measuringContentHeight;
    private boolean halfSheetPadding;
    private float fontScale = 1.0f;
    private float readingProgress;
    // TODO(photoviewer): @Nullable private PhotoViewer activePhotoViewer;

    interface Delegate {
        void onBackPressed();

        void onHeaderHeightChanged(int height);

        void onReadingProgressChanged(float progress);

        default void onArticleScrolled() {
        }

        void onOpenExternal(String url);

        void onCopyLink();

        void onFontSizeRequested();

        void onOpenInstantLink(String url, String json);

        default boolean shouldCollapseHeader() {
            return true;
        }
    }

    BrowserInstantViewFragment(String sourceUrl, String json) {
        this.sourceUrl = sourceUrl;
        this.json = json;
        cacheInstantViewJson(sourceUrl, json);
    }

    void setDelegate(@Nullable Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public View createView(Context context) {
        halfSheetPadding = false;
        WebActionBar webActionBar = new WebActionBar(context, new WebActionBar.Delegate() {
            @Override
            public void onBackPressed() {
                finishFragment();
            }

            @Override
            public void onAddressSubmitted(String value) {
            }

            @Override
            public void onMenuItem(int id) {
                if (id == WebActionBar.MENU_OPEN_EXTERNAL) {
                    openExternal(sourceUrl);
                } else if (id == WebActionBar.MENU_COPY_LINK) {
                    copyLink();
                } else if (id == WebActionBar.MENU_FONT_SIZE) {
                    showFontSizeDialog();
                } else if (id == WebActionBar.MENU_MINIMIZE) {
                    finishFragment();
                }
            }

            @Override
            public void onSearchUpdated(String text) {
            }
        }) {
            @Override
            public boolean shouldAddToContainer() {
                return false;
            }

            @Override
            protected void onOpenedMenu() {
                if (recyclerView != null) {
                    recyclerView.stopScroll();
                }
                checkScrollAnimated(null);
            }
        };
        actionBar = webActionBar;
        webActionBar.setCanMinimize(true);
        webActionBar.setForwardAvailable(false);
        webActionBar.setHistoryAvailable(false);
        webActionBar.setInstantViewAvailable(false);
        webActionBar.setFontSizeAvailable(true);
        webActionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        webActionBar.setColors(Theme.getColor(Theme.key_windowBackgroundWhite), false);
        currentHeaderHeight = AndroidUtilities.dp(56);
        webActionBar.setHeight(currentHeaderHeight);
        int actionBarHeight = webActionBar.getCurrentActionBarHeight();

        fontScale = BrowserStorage.getInstantViewFontScale(context);

        FrameLayout contentView = new FrameLayout(context);
        contentRoot = contentView;
        contentView.setBackgroundColor(Theme.getColor(Theme.key_iv_background));

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        instantAdapter = new InstantAdapter();
        recyclerView.setAdapter(instantAdapter);
        recyclerView.setBackgroundColor(Theme.getColor(Theme.key_iv_background));
        recyclerView.setClipToPadding(false);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(24));
        recyclerView.setItemAnimator(null);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() == 0) {
                    return;
                }
                recyclerView.invalidate();
                if (dy != 0) {
                    checkScroll(dy);
                }
                if (delegate != null) {
                    delegate.onArticleScrolled();
                }
                updateReadingProgress();
            }

        });
        contentView.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(R.string.app_browser_instant_view_unavailable);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(16));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        emptyView.setVisibility(View.GONE);
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        contentView.addView(webActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        buildRows();
        updateEmptyState();
        updateActionBarContent();
        contentView.post(this::updateReadingProgress);

        return fragmentView = contentView;
    }

    View createEmbeddedView(Context context, int topPadding) {
        halfSheetPadding = true;
        actionBar = null;
        currentHeaderHeight = AndroidUtilities.dp(56);
        fontScale = BrowserStorage.getInstantViewFontScale(context);

        FrameLayout contentView = new FrameLayout(context);
        contentRoot = contentView;
        contentView.setBackgroundColor(Theme.getColor(Theme.key_iv_background));

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        instantAdapter = new InstantAdapter();
        recyclerView.setAdapter(instantAdapter);
        recyclerView.setBackgroundColor(Theme.getColor(Theme.key_iv_background));
        recyclerView.setClipToPadding(false);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setPadding(0, topPadding, 0, AndroidUtilities.dp(24));
        recyclerView.setItemAnimator(null);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() == 0) {
                    return;
                }
                recyclerView.invalidate();
                if (dy != 0) {
                    checkScroll(dy);
                }
                if (delegate != null) {
                    delegate.onArticleScrolled();
                }
                updateReadingProgress();
            }
        });
        contentView.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(R.string.app_browser_instant_view_unavailable);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(16));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        emptyView.setVisibility(View.GONE);
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        buildRows();
        updateEmptyState();
        contentView.post(this::updateReadingProgress);

        return fragmentView = contentView;
    }

    private void buildRows() {
        blocks.clear();
        localBlocks.clear();
        photoBlocks.clear();
        anchors.clear();
        try {
            if (cachedPage == null) {
                cachedPage = BrowserPage.parse(json);
            }
            BrowserPage page = cachedPage;
            if (!TextUtils.isEmpty(page.sourceName)) {
                addBlock(new BrowserPage.PageBlockKicker(page.sourceName), 0, 0, 0);
            }
            if (!TextUtils.isEmpty(page.title) && !startsWithHeader(page)) {
                addBlock(new BrowserPage.PageBlockTitle(new BrowserPage.RichText(page.title, new ArrayList<>())), 0, 0, 0);
            }
            String authorDate = buildAuthorDate(page);
            if (!TextUtils.isEmpty(authorDate)) {
                addBlock(new BrowserPage.PageBlockAuthorDate(authorDate), 0, 0, 0);
            }
            if (!TextUtils.isEmpty(page.excerpt)) {
                addBlock(new BrowserPage.PageBlockSubtitle(new BrowserPage.RichText(page.excerpt, new ArrayList<>())), 0, 0, 0);
            }
            if (!TextUtils.isEmpty(page.heroImageUrl) && !matchesFirstPhoto(page)) {
                addBlock(new BrowserPage.PageBlockPhoto(page.heroImageUrl, 0, 0, null, emptyRichText(), emptyRichText()), 0, 0, 0);
            }
            for (int i = 0; i < page.blocks.size(); i++) {
                addBlock(page.blocks.get(i), 0, 0, i);
            }
            if (!TextUtils.isEmpty(sourceUrl)) {
                addBlock(new BrowserPage.PageBlockSource(sourceUrl), 0, 0, page.blocks.size());
            }
            updateRows();
        } catch (Exception ignored) {
            cachedPage = null;
            blocks.clear();
            localBlocks.clear();
            photoBlocks.clear();
        }
        updateActionBarContent();
        updateEmptyState();
        resetCachedHeights();
        if (instantAdapter != null) {
            instantAdapter.notifyDataSetChanged();
        }
    }

    private boolean startsWithHeader(@NonNull BrowserPage page) {
        if (page.blocks.isEmpty()) {
            return false;
        }
        BrowserPage.PageBlock firstBlock = page.blocks.get(0);
        return firstBlock instanceof BrowserPage.PageBlockHeader;
    }

    private void addBlock(@NonNull BrowserPage.PageBlock block, int level, int listLevel, int position) {
        BrowserPage.PageBlock originalBlock = block;
        if (block instanceof BrowserPage.PageBlockDetailsChild) {
            block = ((BrowserPage.PageBlockDetailsChild) block).block;
        }
        BrowserPage.PageBlock effectiveBlock = getLastNonListPageBlock(block);
        if (effectiveBlock instanceof BrowserPage.PageBlockAnchor) {
            BrowserPage.PageBlockAnchor anchor = (BrowserPage.PageBlockAnchor) effectiveBlock;
            if (!TextUtils.isEmpty(anchor.name)) {
                anchors.put(anchor.name.toLowerCase(Locale.US), blocks.size());
            }
            return;
        }
        addPhotoBlock(effectiveBlock);
        collectAnchors(effectiveBlock, blocks.size());
        if (!(effectiveBlock instanceof BrowserPage.PageBlockList || effectiveBlock instanceof BrowserPage.PageBlockOrderedList)) {
            blocks.add(originalBlock);
        }
        if (effectiveBlock instanceof BrowserPage.PageBlockDetails) {
            BrowserPage.PageBlockDetails details = (BrowserPage.PageBlockDetails) effectiveBlock;
            for (int i = 0; i < details.blocks.size(); i++) {
                BrowserPage.PageBlockDetailsChild child = new BrowserPage.PageBlockDetailsChild();
                child.parent = originalBlock;
                child.block = details.blocks.get(i);
                addBlock(child, level + 1, listLevel, position);
            }
        } else if (effectiveBlock instanceof BrowserPage.PageBlockList) {
            addListBlock(originalBlock, (BrowserPage.PageBlockList) effectiveBlock, level, listLevel, position);
        } else if (effectiveBlock instanceof BrowserPage.PageBlockOrderedList) {
            addOrderedListBlock(originalBlock, (BrowserPage.PageBlockOrderedList) effectiveBlock, level, listLevel, position);
        }
    }

    private void addListBlock(@NonNull BrowserPage.PageBlock originalBlock, @NonNull BrowserPage.PageBlockList list, int level, int listLevel, int position) {
        BrowserPage.PageBlockListParent parent = new BrowserPage.PageBlockListParent();
        parent.pageBlockList = list;
        parent.level = listLevel;
        for (int i = 0; i < list.items.size(); i++) {
            BrowserPage.PageListItem item = list.items.get(i);
            BrowserPage.PageBlockListItem listItem = new BrowserPage.PageBlockListItem();
            listItem.index = i;
            listItem.parent = parent;
            listItem.num = "\u2022";
            parent.items.add(listItem);

            ArrayList<BrowserPage.PageBlock> itemBlocks = null;
            if (item instanceof BrowserPage.PageListItemText) {
                listItem.textItem = ((BrowserPage.PageListItemText) item).text;
            } else if (item instanceof BrowserPage.PageListItemBlocks) {
                itemBlocks = ((BrowserPage.PageListItemBlocks) item).blocks;
                if (!itemBlocks.isEmpty()) {
                    listItem.blockItem = itemBlocks.get(0);
                }
            }
            addListItem(originalBlock, listItem, level, listLevel, position, i == 0);
            if (itemBlocks != null) {
                for (int j = 1; j < itemBlocks.size(); j++) {
                    BrowserPage.PageBlockListItem nextItem = new BrowserPage.PageBlockListItem();
                    nextItem.parent = parent;
                    nextItem.blockItem = itemBlocks.get(j);
                    parent.items.add(nextItem);
                    addListItem(originalBlock, nextItem, level, listLevel, position, false);
                }
            }
        }
    }

    private void addOrderedListBlock(@NonNull BrowserPage.PageBlock originalBlock, @NonNull BrowserPage.PageBlockOrderedList list, int level, int listLevel, int position) {
        BrowserPage.PageBlockOrderedListParent parent = new BrowserPage.PageBlockOrderedListParent();
        parent.pageBlockOrderedList = list;
        parent.level = listLevel;
        for (int i = 0; i < list.items.size(); i++) {
            BrowserPage.PageListOrderedItem item = list.items.get(i);
            BrowserPage.PageBlockOrderedListItem listItem = new BrowserPage.PageBlockOrderedListItem();
            listItem.index = i;
            listItem.parent = parent;
            listItem.num = buildOrderedListNum(item.num, i);
            parent.items.add(listItem);

            ArrayList<BrowserPage.PageBlock> itemBlocks = null;
            if (item instanceof BrowserPage.PageListOrderedItemText) {
                listItem.textItem = ((BrowserPage.PageListOrderedItemText) item).text;
            } else if (item instanceof BrowserPage.PageListOrderedItemBlocks) {
                itemBlocks = ((BrowserPage.PageListOrderedItemBlocks) item).blocks;
                if (!itemBlocks.isEmpty()) {
                    listItem.blockItem = itemBlocks.get(0);
                }
            }
            addOrderedListItem(originalBlock, listItem, level, listLevel, position, i == 0);
            if (itemBlocks != null) {
                for (int j = 1; j < itemBlocks.size(); j++) {
                    BrowserPage.PageBlockOrderedListItem nextItem = new BrowserPage.PageBlockOrderedListItem();
                    nextItem.parent = parent;
                    nextItem.blockItem = itemBlocks.get(j);
                    parent.items.add(nextItem);
                    addOrderedListItem(originalBlock, nextItem, level, listLevel, position, false);
                }
            }
        }
    }

    private void addListItem(@NonNull BrowserPage.PageBlock originalBlock, @NonNull BrowserPage.PageBlockListItem listItem, int level, int listLevel, int position, boolean first) {
        if (originalBlock instanceof BrowserPage.PageBlockDetailsChild) {
            BrowserPage.PageBlockDetailsChild detailsChild = (BrowserPage.PageBlockDetailsChild) originalBlock;
            BrowserPage.PageBlockDetailsChild child = new BrowserPage.PageBlockDetailsChild();
            child.parent = detailsChild.parent;
            child.block = listItem;
            addBlock(child, level, listLevel + 1, position);
        } else {
            addBlock(first ? fixListBlock(originalBlock, listItem) : listItem, level, listLevel + 1, position);
        }
    }

    private void addOrderedListItem(@NonNull BrowserPage.PageBlock originalBlock, @NonNull BrowserPage.PageBlockOrderedListItem listItem, int level, int listLevel, int position, boolean first) {
        if (originalBlock instanceof BrowserPage.PageBlockDetailsChild) {
            BrowserPage.PageBlockDetailsChild detailsChild = (BrowserPage.PageBlockDetailsChild) originalBlock;
            BrowserPage.PageBlockDetailsChild child = new BrowserPage.PageBlockDetailsChild();
            child.parent = detailsChild.parent;
            child.block = listItem;
            addBlock(child, level, listLevel + 1, position);
        } else {
            addBlock(first ? fixListBlock(originalBlock, listItem) : listItem, level, listLevel + 1, position);
        }
    }

    private String buildOrderedListNum(@Nullable String num, int index) {
        return TextUtils.isEmpty(num) ? (index + 1) + "." : num + ".";
    }

    private BrowserPage.PageBlock fixListBlock(@NonNull BrowserPage.PageBlock parentBlock, @NonNull BrowserPage.PageBlock childBlock) {
        if (parentBlock instanceof BrowserPage.PageBlockListItem) {
            ((BrowserPage.PageBlockListItem) parentBlock).blockItem = childBlock;
            return parentBlock;
        }
        if (parentBlock instanceof BrowserPage.PageBlockOrderedListItem) {
            ((BrowserPage.PageBlockOrderedListItem) parentBlock).blockItem = childBlock;
            return parentBlock;
        }
        return childBlock;
    }

    @Nullable
    private BrowserPage.PageBlock getLastNonListPageBlock(@Nullable BrowserPage.PageBlock block) {
        if (block instanceof BrowserPage.PageBlockListItem) {
            BrowserPage.PageBlockListItem item = (BrowserPage.PageBlockListItem) block;
            return item.blockItem == null ? null : getLastNonListPageBlock(item.blockItem);
        }
        if (block instanceof BrowserPage.PageBlockOrderedListItem) {
            BrowserPage.PageBlockOrderedListItem item = (BrowserPage.PageBlockOrderedListItem) block;
            return item.blockItem == null ? null : getLastNonListPageBlock(item.blockItem);
        }
        return block;
    }

    private void addPhotoBlock(@Nullable BrowserPage.PageBlock block) {
        BrowserPage.PageBlock unwrapped = unwrapRenderableBlock(block);
        if (unwrapped instanceof BrowserPage.PageBlockPhoto) {
            photoBlocks.add((BrowserPage.PageBlockPhoto) unwrapped);
        } else if (unwrapped instanceof BrowserPage.PageBlockSlideshow) {
            BrowserPage.PageBlockSlideshow slideshow = (BrowserPage.PageBlockSlideshow) unwrapped;
            for (int i = 0; i < slideshow.items.size(); i++) {
                addPhotoBlock(slideshow.items.get(i));
            }
        } else if (unwrapped instanceof BrowserPage.PageBlockCollage) {
            BrowserPage.PageBlockCollage collage = (BrowserPage.PageBlockCollage) unwrapped;
            for (int i = 0; i < collage.items.size(); i++) {
                addPhotoBlock(collage.items.get(i));
            }
        }
    }

    private void collectAnchors(@Nullable BrowserPage.PageBlock block, int row) {
        block = unwrapRenderableBlock(block);
        if (block instanceof BrowserPage.PageBlockParagraph) {
            collectAnchors(((BrowserPage.PageBlockParagraph) block).text, row);
        } else if (block instanceof BrowserPage.PageBlockHeader) {
            collectAnchors(((BrowserPage.PageBlockHeader) block).text, row);
        } else if (block instanceof BrowserPage.PageBlockSubheader) {
            collectAnchors(((BrowserPage.PageBlockSubheader) block).text, row);
        } else if (block instanceof BrowserPage.PageBlockSubtitle) {
            collectAnchors(((BrowserPage.PageBlockSubtitle) block).text, row);
        } else if (block instanceof BrowserPage.PageBlockFooter) {
            collectAnchors(((BrowserPage.PageBlockFooter) block).text, row);
        } else if (block instanceof BrowserPage.PageBlockBlockquote) {
            BrowserPage.PageBlockBlockquote quote = (BrowserPage.PageBlockBlockquote) block;
            collectAnchors(quote.text, row);
            collectAnchors(quote.caption, row);
        } else if (block instanceof BrowserPage.PageBlockPullquote) {
            BrowserPage.PageBlockPullquote quote = (BrowserPage.PageBlockPullquote) block;
            collectAnchors(quote.text, row);
            collectAnchors(quote.caption, row);
        } else if (block instanceof BrowserPage.PageBlockPhoto) {
            BrowserPage.PageBlockPhoto photo = (BrowserPage.PageBlockPhoto) block;
            collectAnchors(photo.caption, row);
            collectAnchors(photo.credit, row);
        } else if (block instanceof BrowserPage.PageBlockEmbed) {
            BrowserPage.PageBlockEmbed embed = (BrowserPage.PageBlockEmbed) block;
            collectAnchors(embed.caption, row);
            collectAnchors(embed.credit, row);
        } else if (block instanceof BrowserPage.PageBlockVideo) {
            BrowserPage.PageBlockVideo video = (BrowserPage.PageBlockVideo) block;
            collectAnchors(video.caption, row);
            collectAnchors(video.credit, row);
        } else if (block instanceof BrowserPage.PageBlockAudio) {
            BrowserPage.PageBlockAudio audio = (BrowserPage.PageBlockAudio) block;
            collectAnchors(audio.caption, row);
            collectAnchors(audio.credit, row);
        } else if (block instanceof BrowserPage.PageBlockTable) {
            BrowserPage.PageBlockTable table = (BrowserPage.PageBlockTable) block;
            collectAnchors(table.title, row);
            for (int i = 0; i < table.rows.size(); i++) {
                BrowserPage.PageTableRow tableRow = table.rows.get(i);
                for (int j = 0; j < tableRow.cells.size(); j++) {
                    collectAnchors(tableRow.cells.get(j).text, row);
                }
            }
        } else if (block instanceof BrowserPage.PageBlockDetails) {
            collectAnchors(((BrowserPage.PageBlockDetails) block).title, row);
        } else if (block instanceof BrowserPage.PageBlockList) {
            BrowserPage.PageBlockList list = (BrowserPage.PageBlockList) block;
            for (int i = 0; i < list.items.size(); i++) {
                BrowserPage.PageListItem item = list.items.get(i);
                if (item instanceof BrowserPage.PageListItemText) {
                    collectAnchors(((BrowserPage.PageListItemText) item).text, row);
                } else if (item instanceof BrowserPage.PageListItemBlocks) {
                    collectAnchors(((BrowserPage.PageListItemBlocks) item).blocks, row);
                }
            }
        } else if (block instanceof BrowserPage.PageBlockOrderedList) {
            BrowserPage.PageBlockOrderedList list = (BrowserPage.PageBlockOrderedList) block;
            for (int i = 0; i < list.items.size(); i++) {
                BrowserPage.PageListOrderedItem item = list.items.get(i);
                if (item instanceof BrowserPage.PageListOrderedItemText) {
                    collectAnchors(((BrowserPage.PageListOrderedItemText) item).text, row);
                } else if (item instanceof BrowserPage.PageListOrderedItemBlocks) {
                    collectAnchors(((BrowserPage.PageListOrderedItemBlocks) item).blocks, row);
                }
            }
        } else if (block instanceof BrowserPage.PageBlockListItem) {
            BrowserPage.PageBlockListItem item = (BrowserPage.PageBlockListItem) block;
            collectAnchors(item.textItem, row);
            collectAnchors(item.blockItem, row);
        } else if (block instanceof BrowserPage.PageBlockOrderedListItem) {
            BrowserPage.PageBlockOrderedListItem item = (BrowserPage.PageBlockOrderedListItem) block;
            collectAnchors(item.textItem, row);
            collectAnchors(item.blockItem, row);
        }
    }

    private void collectAnchors(@Nullable ArrayList<BrowserPage.PageBlock> blocks, int row) {
        if (blocks == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            collectAnchors(blocks.get(i), row);
        }
    }

    private void collectAnchors(@Nullable BrowserPage.RichText richText, int row) {
        if (richText == null) {
            return;
        }
        for (int i = 0; i < richText.entities.size(); i++) {
            BrowserPage.Entity entity = richText.entities.get(i);
            if ("anchor".equals(entity.type) && !TextUtils.isEmpty(entity.url)) {
                anchors.put(entity.url.toLowerCase(Locale.US), row);
            }
        }
    }

    @Nullable
    private BrowserPage.PageBlock unwrapRenderableBlock(@Nullable BrowserPage.PageBlock block) {
        BrowserPage.PageBlock current = block;
        while (true) {
            if (current instanceof BrowserPage.PageBlockDetailsChild) {
                current = ((BrowserPage.PageBlockDetailsChild) current).block;
                continue;
            }
            if (current instanceof BrowserPage.PageBlockCover) {
                current = ((BrowserPage.PageBlockCover) current).block;
                continue;
            }
            return current;
        }
    }

    private void updateRows() {
        localBlocks.clear();
        for (int i = 0; i < blocks.size(); i++) {
            BrowserPage.PageBlock originalBlock = blocks.get(i);
            BrowserPage.PageBlock block = getLastNonListPageBlock(originalBlock);
            if (block instanceof BrowserPage.PageBlockDetailsChild && !isBlockOpened((BrowserPage.PageBlockDetailsChild) block)) {
                continue;
            }
            if (originalBlock instanceof BrowserPage.PageBlockDetailsChild && !isBlockOpened((BrowserPage.PageBlockDetailsChild) originalBlock)) {
                continue;
            }
            localBlocks.add(originalBlock);
        }
    }

    private boolean isBlockOpened(@NonNull BrowserPage.PageBlockDetailsChild child) {
        BrowserPage.PageBlock parentBlock = getLastNonListPageBlock(child.parent);
        if (parentBlock instanceof BrowserPage.PageBlockDetails) {
            return ((BrowserPage.PageBlockDetails) parentBlock).open;
        }
        if (parentBlock instanceof BrowserPage.PageBlockDetailsChild) {
            BrowserPage.PageBlockDetailsChild parent = (BrowserPage.PageBlockDetailsChild) parentBlock;
            BrowserPage.PageBlock block = getLastNonListPageBlock(parent.block);
            if (block instanceof BrowserPage.PageBlockDetails && !((BrowserPage.PageBlockDetails) block).open) {
                return false;
            }
            return isBlockOpened(parent);
        }
        return false;
    }

    private boolean matchesFirstPhoto(@NonNull BrowserPage page) {
        if (TextUtils.isEmpty(page.heroImageUrl) || page.blocks.isEmpty()) {
            return false;
        }
        BrowserPage.PageBlock firstBlock = page.blocks.get(0);
        if (!(firstBlock instanceof BrowserPage.PageBlockPhoto)) {
            return false;
        }
        return TextUtils.equals(page.heroImageUrl, ((BrowserPage.PageBlockPhoto) firstBlock).url);
    }

    @NonNull
    private String buildAuthorDate(@NonNull BrowserPage page) {
        Context context = getContext();
        boolean hasByline = !TextUtils.isEmpty(page.byline);
        boolean hasPublishedTime = !TextUtils.isEmpty(page.publishedTime);
        if (context != null && hasByline && hasPublishedTime) {
            return context.getString(R.string.app_browser_reader_date_by_author, page.publishedTime, page.byline);
        }
        if (context != null && hasByline) {
            return context.getString(R.string.app_browser_reader_by_author, page.byline);
        }
        if (hasPublishedTime) {
            return page.publishedTime;
        }
        if (hasByline) {
            return page.byline;
        }
        return "";
    }

    @NonNull
    private BrowserPage.RichText emptyRichText() {
        return new BrowserPage.RichText("", new ArrayList<>());
    }

    private void updateEmptyState() {
        boolean empty = localBlocks.isEmpty();
        if (recyclerView != null) {
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private void checkScroll(int dy) {
        if (delegate != null && !delegate.shouldCollapseHeader()) {
            return;
        }
        if (headerAnimator != null) {
            headerAnimator.cancel();
            headerAnimator = null;
        }
        setCurrentHeaderHeight(currentHeaderHeight - dy);
    }

    private void checkScrollAnimated(@Nullable Runnable callback) {
        int maxHeight = AndroidUtilities.dp(56);
        if (currentHeaderHeight == maxHeight) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        headerAnimator = ValueAnimator.ofInt(currentHeaderHeight, maxHeight);
        headerAnimator.setDuration(callback == null ? 180 : 90);
        headerAnimator.setInterpolator(new DecelerateInterpolator());
        headerAnimator.addUpdateListener(animation -> setCurrentHeaderHeight((int) animation.getAnimatedValue()));
        headerAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                headerAnimator = null;
                if (callback != null) {
                    callback.run();
                }
            }
        });
        headerAnimator.start();
    }

    private void setCurrentHeaderHeight(int newHeight) {
        if (actionBar instanceof WebActionBar) {
            WebActionBar webActionBar = (WebActionBar) actionBar;
            if (webActionBar.isSearching() || webActionBar.isAddressing()) {
                return;
            }
        }
        int clamped = Math.max(AndroidUtilities.dp(24), Math.min(AndroidUtilities.dp(56), newHeight));
        if (currentHeaderHeight == clamped) {
            return;
        }
        currentHeaderHeight = clamped;
        if (actionBar != null) {
            actionBar.setHeight(currentHeaderHeight);
        }
        if (delegate != null) {
            delegate.onHeaderHeightChanged(currentHeaderHeight);
        }
    }

    private void updateActionBarContent() {
        if (!(actionBar instanceof WebActionBar)) {
            return;
        }
        WebActionBar webActionBar = (WebActionBar) actionBar;
        BrowserPage page = cachedPage;
        String title = "";
        String subtitle = "";
        if (page != null) {
            if (!TextUtils.isEmpty(page.sourceName)) {
                title = page.sourceName;
            } else if (!TextUtils.isEmpty(page.title)) {
                title = page.title;
            }
            if (!TextUtils.isEmpty(page.title) && !TextUtils.equals(title, page.title)) {
                subtitle = "";
            }
        } else if (!TextUtils.isEmpty(sourceUrl)) {
            title = sourceUrl;
        }
        webActionBar.setTitle(title);
        webActionBar.setSubtitle(subtitle);
    }

    private void updateReadingProgress() {
        if (recyclerView == null || recyclerView.getLayoutManager() == null || localBlocks.isEmpty()) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int first = linearLayoutManager.findFirstVisibleItemPosition();
        View view = linearLayoutManager.findViewByPosition(first);
        if (first == RecyclerView.NO_POSITION || view == null) {
            return;
        }
        ensureContentHeightCache(recyclerView.getWidth());
        if (sumItemHeights == null || itemHeights == null || fullContentHeight <= 0) {
            return;
        }
        int beforeIndex = first - 1;
        int offset = beforeIndex >= 0 && beforeIndex < sumItemHeights.length ? sumItemHeights[beforeIndex] : 0;
        offset += halfSheetPadding && first == 0 ? 0 : (halfSheetPadding ? -view.getTop() : recyclerView.getPaddingTop() - view.getTop());
        offset = Math.max(0, offset);
        int viewportHeight = halfSheetPadding
                ? Math.max(1, recyclerView.getHeight())
                : Math.max(1, recyclerView.getHeight() - recyclerView.getPaddingTop() - recyclerView.getPaddingBottom());
        int scrollableHeight = Math.max(1, fullContentHeight - viewportHeight);
        float progress = offset / (float) scrollableHeight;
        float clamped = Math.max(0f, Math.min(1f, progress));
        readingProgress = clamped;
        if (actionBar instanceof WebActionBar) {
            ((WebActionBar) actionBar).setProgress(clamped);
        }
        if (delegate != null) {
            delegate.onReadingProgressChanged(clamped);
        }
    }

    private void ensureContentHeightCache(int width) {
        if (width <= 0 || localBlocks.isEmpty()) {
            return;
        }
        int itemCount = localBlocks.size() + (halfSheetPadding ? 1 : 0);
        if (itemHeights != null && sumItemHeights != null && contentHeightWidth == width && itemHeights.length == itemCount) {
            return;
        }
        itemHeights = new int[itemCount];
        sumItemHeights = new int[itemCount];
        fullContentHeight = 0;
        contentHeightWidth = width;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measuringContentHeight = true;
        try {
            for (int i = 0; i < itemCount; i++) {
                int blockIndex = halfSheetPadding ? i - 1 : i;
                BrowserPage.PageBlock block = blockIndex < 0 ? null : localBlocks.get(blockIndex);
                int height = blockIndex < 0 ? 0 : measureBlockHeight(block, widthSpec, heightSpec, blockIndex == 0);
                itemHeights[i] = height;
                fullContentHeight += height;
                sumItemHeights[i] = fullContentHeight;
            }
        } finally {
            measuringContentHeight = false;
        }
    }

    private int measureBlockHeight(@Nullable BrowserPage.PageBlock block, int widthSpec, int heightSpec, boolean first) {
        int width = View.MeasureSpec.getSize(widthSpec);
        int viewType = getTypeForBlock(block);
        if (recyclerView == null || viewType < 0) {
            return AndroidUtilities.dp(64);
        }
        View view = createBlockViewFor(recyclerView.getContext(), viewType);
        if (view == null) {
            return AndroidUtilities.dp(64);
        }
        bindBlockToView(view, block, false, first);
        view.measure(widthSpec, heightSpec);
        int height = Math.max(1, view.getMeasuredHeight());
        if (block != null) {
            block.cachedWidth = width;
            block.cachedHeight = height;
        }
        return height;
    }

    private void resetCachedHeights() {
        itemHeights = null;
        sumItemHeights = null;
        fullContentHeight = 0;
        contentHeightWidth = 0;
        for (int i = 0; i < localBlocks.size(); i++) {
            BrowserPage.PageBlock block = localBlocks.get(i);
            block.cachedWidth = 0;
            block.cachedHeight = 0;
        }
    }

    private boolean openExternal(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (delegate != null) {
            delegate.onOpenExternal(url);
            return true;
        }
        try {
            Context context = getContext();
            if (context == null) {
                return false;
            }
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void copyLink() {
        if (TextUtils.isEmpty(sourceUrl)) {
            return;
        }
        if (delegate != null) {
            delegate.onCopyLink();
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        ClipboardManager clipboardManager = context.getSystemService(ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("link", sourceUrl));
            Toast.makeText(context, R.string.app_browser_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Resolves a link click by first checking the in-process Instant View JSON cache. When a
     * matching entry exists, a sibling {@link BrowserInstantViewFragment} is pushed onto the
     * host ActionBarLayout so the user keeps the same swipe-back stack semantics. Otherwise the
     * link falls back to the system browser.
     */
    private void openLinkOrInstantView(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        if (scrollToAnchorFromUrl(url)) {
            return;
        }
        if (TextUtils.equals(url, sourceUrl)) {
            // Avoid opening the same article on top of itself.
            return;
        }
        String cached = getCachedInstantViewJson(url);
        if (!TextUtils.isEmpty(cached) && delegate != null) {
            delegate.onOpenInstantLink(url, cached);
            return;
        }
        if (!TextUtils.isEmpty(cached) && getParentLayout() != null) {
            BrowserInstantViewFragment next = new BrowserInstantViewFragment(url, cached);
            if (presentFragment(next)) {
                return;
            }
        }
        openExternal(url);
    }

    private boolean scrollToAnchorFromUrl(@NonNull String url) {
        Uri uri = Uri.parse(url);
        String fragment = uri.getFragment();
        if (TextUtils.isEmpty(fragment)) {
            return false;
        }
        String targetBase = stripFragment(url);
        String sourceBase = stripFragment(getArticleUrl());
        String fallbackBase = stripFragment(sourceUrl);
        if (!TextUtils.equals(targetBase, sourceBase) && !TextUtils.equals(targetBase, fallbackBase)) {
            return false;
        }
        Integer row = anchors.get(fragment.toLowerCase(Locale.US));
        if (row == null || recyclerView == null) {
            return false;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            int position = row + (halfSheetPadding ? 1 : 0);
            int offset = Math.max(AndroidUtilities.dp(8), currentHeaderHeight + AndroidUtilities.dp(8));
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, offset);
            updateReadingProgress();
            return true;
        }
        return false;
    }

    @Nullable
    private String stripFragment(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return url;
        }
        int index = url.indexOf('#');
        return index < 0 ? url : url.substring(0, index);
    }

    private float scaledSp(float baseDp) {
        return baseDp * fontScale;
    }

    private void enableSelectableText(@NonNull TextView textView) {
        // setTextIsSelectable(true) internally swaps movement method to ArrowKeyMovementMethod and
        // also flips focus/long-click flags. Set it first, then restore LinkMovementMethod so
        // ClickableSpan still fires while the underlying mTextIsSelectable flag keeps long-press
        // selection working.
        textView.setTextIsSelectable(true);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setHighlightColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 38));
    }

    private void showFontSizeDialog() {
        if (delegate != null) {
            delegate.onFontSizeRequested();
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        CharSequence[] labels = new CharSequence[] {
                context.getString(R.string.app_browser_font_size_small),
                context.getString(R.string.app_browser_font_size_medium),
                context.getString(R.string.app_browser_font_size_large),
                context.getString(R.string.app_browser_font_size_xlarge)
        };
        int currentIndex = BrowserStorage.instantViewFontScaleIndex(fontScale);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.app_browser_font_size)
                .setSingleChoiceItems(labels, currentIndex, (d, which) -> {
                    if (which < 0 || which >= BrowserStorage.INSTANT_VIEW_FONT_SCALES.length) {
                        return;
                    }
                    float chosen = BrowserStorage.INSTANT_VIEW_FONT_SCALES[which];
                    if (chosen == fontScale) {
                        d.dismiss();
                        return;
                    }
                    fontScale = chosen;
                    BrowserStorage.setInstantViewFontScale(getContext(), fontScale);
                    refreshFontScale();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    BrowserPage getPage() {
        if (cachedPage == null) {
            try {
                cachedPage = BrowserPage.parse(json);
            } catch (Exception ignored) {
                return null;
            }
        }
        return cachedPage;
    }

    String getDisplayTitle() {
        BrowserPage page = getPage();
        if (page == null) {
            return "";
        }
        if (!TextUtils.isEmpty(page.sourceName)) {
            return page.sourceName;
        }
        if (!TextUtils.isEmpty(page.title)) {
            return page.title;
        }
        return "";
    }

    String getArticleUrl() {
        BrowserPage page = getPage();
        if (page != null && !TextUtils.isEmpty(page.url)) {
            return page.url;
        }
        return sourceUrl;
    }

    void openFontSizeDialog() {
        Delegate oldDelegate = delegate;
        delegate = null;
        showFontSizeDialog();
        delegate = oldDelegate;
    }

    boolean isAtTop() {
        return recyclerView == null || !recyclerView.canScrollVertically(-1);
    }

    float getListTop() {
        if (recyclerView == null || recyclerView.getChildCount() == 0) {
            return recyclerView == null ? 0 : recyclerView.getHeight();
        }
        float top = recyclerView.getHeight();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int position = recyclerView.getChildAdapterPosition(child);
            top = Math.min(top, halfSheetPadding && position == 0 ? child.getBottom() : child.getTop());
        }
        return top;
    }

    float getReadingProgress() {
        return readingProgress;
    }

    private void refreshFontScale() {
        if (recyclerView == null) {
            return;
        }
        // Clear the recycled view pool so cells with constructor-set text sizes are recreated
        // with the new scale, then rebuild rows. A simple notifyDataSetChanged is not enough
        // because some cell sizes are configured outside bind().
        recyclerView.getRecycledViewPool().clear();
        resetCachedHeights();
        instantAdapter = new InstantAdapter();
        if (recyclerView.getAdapter() != null) {
            recyclerView.swapAdapter(instantAdapter, false);
        }
        recyclerView.post(this::updateReadingProgress);
    }

    void scrollToInitialSheetPosition() {
        if (recyclerView == null || !halfSheetPadding) {
            return;
        }
        recyclerView.post(() -> {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                        localBlocks.isEmpty() ? 0 : 1,
                        AndroidUtilities.dp(56 - 24)
                );
                updateReadingProgress();
            }
        });
    }

    private void openImageViewer(@Nullable Bitmap initial, @Nullable String url, @Nullable View sourceView) {
        // TODO(photoviewer): wire PhotoViewer.getInstance().openPhoto(...) once
        // telegram/ui/PhotoViewer compiles. Body intentionally a no-op so :app
        // can build while we backfill the telegram/ dependency graph.
    }

    @Nullable
    private static Activity findHostActivity(@Nullable Context context) {
        Context ctx = context;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                return (Activity) ctx;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    @SuppressWarnings("unused")
    @Nullable
    private static RectF sourceImageRectInWindow(@Nullable View sourceView, @NonNull RectF sourceViewRect) {
        if (!(sourceView instanceof ImageView)) {
            return null;
        }
        ImageView imageView = (ImageView) sourceView;
        Bitmap bitmap;
        if (imageView instanceof RoundedImageView) {
            bitmap = ((RoundedImageView) imageView).getBitmap();
        } else if (imageView.getDrawable() instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        } else {
            return null;
        }
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0
                || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) {
            return null;
        }
        float scale = Math.min(
                imageView.getWidth() / (float) bitmap.getWidth(),
                imageView.getHeight() / (float) bitmap.getHeight());
        float drawnWidth = bitmap.getWidth() * scale;
        float drawnHeight = bitmap.getHeight() * scale;
        float left = sourceViewRect.left + (imageView.getWidth() - drawnWidth) / 2f;
        float top = sourceViewRect.top + (imageView.getHeight() - drawnHeight) / 2f;
        return new RectF(left, top, left + drawnWidth, top + drawnHeight);
    }

    // TODO(photoviewer): private ArrayList<PhotoViewer.PhotoEntry> buildPhotoEntries(...)
    // TODO(photoviewer): private int findPhotoEntry(ArrayList<PhotoViewer.PhotoEntry>, String)

    @Nullable
    private String choosePhotoEntryUrl(@NonNull BrowserPage.PageBlockPhoto block, @NonNull String currentUrl) {
        if (TextUtils.equals(currentUrl, block.url) || block.urls.contains(currentUrl)) {
            return currentUrl;
        }
        for (int i = 0; i < block.urls.size(); i++) {
            String candidate = block.urls.get(i);
            if (!TextUtils.isEmpty(candidate) && getCachedInstantImage(candidate) != null) {
                return candidate;
            }
        }
        if (!TextUtils.isEmpty(block.url)) {
            return block.url;
        }
        for (int i = 0; i < block.urls.size(); i++) {
            String candidate = block.urls.get(i);
            if (!TextUtils.isEmpty(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private Bitmap getCachedInstantImage(@Nullable String url) {
        // TODO(photoviewer): re-enable ImageLoader.get(url) once telegram/messenger/ImageLoader compiles.
        return null;
    }

    @Override
    public boolean onBackPressed() {
        // TODO(photoviewer): re-add activePhotoViewer guard.
        return super.onBackPressed();
    }

    private CharSequence buildRichText(@Nullable BrowserPage.RichText richText) {
        if (richText == null || TextUtils.isEmpty(richText.text)) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(richText.text);
        int markColor = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), .14f);
        int codeColor = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .1f);
        for (int i = 0; i < richText.entities.size(); i++) {
            BrowserPage.Entity entity = richText.entities.get(i);
            int start = Math.max(0, Math.min(builder.length(), entity.start));
            int end = Math.max(start, Math.min(builder.length(), entity.end));
            if (start >= end) {
                continue;
            }
            if ("bold".equals(entity.type)) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("italic".equals(entity.type)) {
                builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("strike".equals(entity.type)) {
                builder.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("inline_code".equals(entity.type)) {
                builder.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new BackgroundColorSpan(codeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("underline".equals(entity.type)) {
                builder.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("highlight".equals(entity.type)) {
                builder.setSpan(new BackgroundColorSpan(markColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("superscript".equals(entity.type)) {
                builder.setSpan(new SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("subscript".equals(entity.type)) {
                builder.setSpan(new SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("small".equals(entity.type)) {
                builder.setSpan(new RelativeSizeSpan(.84f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("kbd".equals(entity.type)) {
                builder.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new BackgroundColorSpan(codeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("image".equals(entity.type)) {
                builder.setSpan(new InlineImagePlaceholderSpan(entity.width, entity.height), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (("link".equals(entity.type) || "phone".equals(entity.type) || "email".equals(entity.type)) && !TextUtils.isEmpty(entity.url)) {
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openLinkOrInstantView(entity.url);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        ds.setUnderlineText(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private final class InlineImagePlaceholderSpan extends ReplacementSpan {
        private final int width;
        private final int height;

        InlineImagePlaceholderSpan(int width, int height) {
            int maxWidth = AndroidUtilities.dp(72);
            int maxHeight = AndroidUtilities.dp(48);
            int imageWidth = width > 0 ? AndroidUtilities.dp(width) : AndroidUtilities.dp(18);
            int imageHeight = height > 0 ? AndroidUtilities.dp(height) : AndroidUtilities.dp(18);
            if (imageWidth > maxWidth) {
                float scale = maxWidth / (float) imageWidth;
                imageWidth = maxWidth;
                imageHeight = Math.max(AndroidUtilities.dp(12), Math.round(imageHeight * scale));
            }
            if (imageHeight > maxHeight) {
                float scale = maxHeight / (float) imageHeight;
                imageHeight = maxHeight;
                imageWidth = Math.max(AndroidUtilities.dp(12), Math.round(imageWidth * scale));
            }
            this.width = Math.max(AndroidUtilities.dp(18), imageWidth);
            this.height = Math.max(AndroidUtilities.dp(18), imageHeight);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                fm.ascent = -height;
                fm.descent = 0;
                fm.top = fm.ascent;
                fm.bottom = 0;
            }
            return width + AndroidUtilities.dp(2);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            Paint.Style oldStyle = paint.getStyle();
            int oldColor = paint.getColor();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .14f));
            float rectTop = y - height;
            canvas.drawRoundRect(x, rectTop, x + width, y, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .35f));
            canvas.drawCircle(x + width / 2f, rectTop + height / 2f, Math.min(width, height) / 5f, paint);
            paint.setColor(oldColor);
            paint.setStyle(oldStyle);
        }
    }

    private CharSequence flattenBlocks(@Nullable ArrayList<BrowserPage.PageBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            CharSequence text = flattenBlock(blocks.get(i));
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text);
        }
        return builder;
    }

    private CharSequence flattenBlock(@Nullable BrowserPage.PageBlock block) {
        block = unwrapRenderableBlock(block);
        if (block instanceof BrowserPage.PageBlockParagraph) {
            return buildRichText(((BrowserPage.PageBlockParagraph) block).text);
        }
        if (block instanceof BrowserPage.PageBlockHeader) {
            return buildRichText(((BrowserPage.PageBlockHeader) block).text);
        }
        if (block instanceof BrowserPage.PageBlockSubheader) {
            return buildRichText(((BrowserPage.PageBlockSubheader) block).text);
        }
        if (block instanceof BrowserPage.PageBlockPullquote) {
            BrowserPage.PageBlockPullquote quote = (BrowserPage.PageBlockPullquote) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(buildRichText(quote.text));
            CharSequence attribution = buildRichText(quote.caption);
            if (!TextUtils.isEmpty(attribution)) {
                builder.append('\n').append(attribution);
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockBlockquote) {
            BrowserPage.PageBlockBlockquote quote = (BrowserPage.PageBlockBlockquote) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(buildRichText(quote.text));
            CharSequence attribution = buildRichText(quote.caption);
            if (!TextUtils.isEmpty(attribution)) {
                builder.append('\n').append(attribution);
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockPreformatted) {
            return ((BrowserPage.PageBlockPreformatted) block).text == null ? "" : ((BrowserPage.PageBlockPreformatted) block).text;
        }
        if (block instanceof BrowserPage.PageBlockFooter) {
            return buildRichText(((BrowserPage.PageBlockFooter) block).text);
        }
        if (block instanceof BrowserPage.PageBlockList) {
            BrowserPage.PageBlockList list = (BrowserPage.PageBlockList) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int i = 0; i < list.items.size(); i++) {
                BrowserPage.PageListItem item = list.items.get(i);
                CharSequence itemText = "";
                if (item instanceof BrowserPage.PageListItemText) {
                    itemText = buildRichText(((BrowserPage.PageListItemText) item).text);
                } else if (item instanceof BrowserPage.PageListItemBlocks) {
                    itemText = flattenBlocks(((BrowserPage.PageListItemBlocks) item).blocks);
                }
                if (TextUtils.isEmpty(itemText)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("\u2022 ").append(itemText);
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockOrderedList) {
            BrowserPage.PageBlockOrderedList list = (BrowserPage.PageBlockOrderedList) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int i = 0; i < list.items.size(); i++) {
                BrowserPage.PageListOrderedItem item = list.items.get(i);
                CharSequence itemText = "";
                if (item instanceof BrowserPage.PageListOrderedItemText) {
                    itemText = buildRichText(((BrowserPage.PageListOrderedItemText) item).text);
                } else if (item instanceof BrowserPage.PageListOrderedItemBlocks) {
                    itemText = flattenBlocks(((BrowserPage.PageListOrderedItemBlocks) item).blocks);
                }
                if (TextUtils.isEmpty(itemText)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(buildOrderedListNum(item.num, i)).append(' ').append(itemText);
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockDetails) {
            BrowserPage.PageBlockDetails details = (BrowserPage.PageBlockDetails) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(buildRichText(details.title));
            CharSequence body = flattenBlocks(details.blocks);
            if (!TextUtils.isEmpty(body)) {
                builder.append('\n').append(body);
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockPhoto) {
            BrowserPage.PageBlockPhoto image = (BrowserPage.PageBlockPhoto) block;
            if (!TextUtils.isEmpty(image.alt)) {
                return image.alt;
            }
            return buildRichText(image.caption);
        }
        if (block instanceof BrowserPage.PageBlockTable) {
            BrowserPage.PageBlockTable table = (BrowserPage.PageBlockTable) block;
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (int i = 0; i < table.rows.size(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                BrowserPage.PageTableRow row = table.rows.get(i);
                for (int j = 0; j < row.cells.size(); j++) {
                    if (j > 0) {
                        builder.append(" | ");
                    }
                    builder.append(buildRichText(row.cells.get(j).text));
                }
            }
            return builder;
        }
        if (block instanceof BrowserPage.PageBlockEmbed) {
            BrowserPage.PageBlockEmbed embed = (BrowserPage.PageBlockEmbed) block;
            return TextUtils.isEmpty(embed.title) ? getStringSafe(R.string.app_browser_reader_embed) : embed.title;
        }
        if (block instanceof BrowserPage.PageBlockVideo) {
            return getStringSafe(R.string.app_browser_reader_video);
        }
        if (block instanceof BrowserPage.PageBlockAudio) {
            BrowserPage.PageBlockAudio audio = (BrowserPage.PageBlockAudio) block;
            return TextUtils.isEmpty(audio.title) ? getStringSafe(R.string.app_browser_reader_audio) : audio.title;
        }
        if (block instanceof BrowserPage.PageBlockSlideshow) {
            return flattenBlocks(((BrowserPage.PageBlockSlideshow) block).items);
        }
        if (block instanceof BrowserPage.PageBlockCollage) {
            return flattenBlocks(((BrowserPage.PageBlockCollage) block).items);
        }
        if (block instanceof BrowserPage.PageBlockUnsupported) {
            return getStringSafe(R.string.app_browser_reader_unsupported);
        }
        return "";
    }

    private String getStringSafe(int resId) {
        Context context = getContext();
        return context == null ? "" : context.getString(resId);
    }

    private void applyText(@NonNull TextView textView, @Nullable CharSequence text) {
        textView.setText(text);
        enableSelectableText(textView);
    }

    private void applyText(@NonNull TextView textView, @Nullable CharSequence text, boolean centered) {
        applyText(textView, text);
        textView.setGravity(centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        textView.setTextAlignment(centered ? View.TEXT_ALIGNMENT_CENTER : View.TEXT_ALIGNMENT_VIEW_START);
    }

    private void appendBlockViews(@NonNull LinearLayout container, @NonNull ArrayList<BrowserPage.PageBlock> blocks) {
        appendBlockViews(container, blocks, false);
    }

    private void appendBlockViews(@NonNull LinearLayout container, @NonNull ArrayList<BrowserPage.PageBlock> blocks, boolean compact) {
        for (int i = 0; i < blocks.size(); i++) {
            appendBlockView(container, blocks.get(i), compact);
        }
    }

    private void appendBlockView(@NonNull LinearLayout container, @NonNull BrowserPage.PageBlock block) {
        appendBlockView(container, block, false);
    }

    private void appendBlockView(@NonNull LinearLayout container, @NonNull BrowserPage.PageBlock block, boolean compact) {
        Context context = container.getContext();
        View childView = createBlockViewFor(context, getTypeForBlock(block));
        if (childView == null) {
            return;
        }
        bindBlockToView(childView, block, compact, false);
        container.addView(childView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    @Nullable
    private DrawingText createDrawingText(@Nullable CharSequence text, @NonNull TextPaint paint, int width, @NonNull Layout.Alignment alignment) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        CharSequence normalized = text;
        if (normalized.charAt(normalized.length() - 1) == '\n') {
            normalized = normalized.subSequence(0, normalized.length() - 1);
        }
        int layoutWidth = Math.max(1, width);
        StaticLayout layout = StaticLayout.Builder.obtain(normalized, 0, normalized.length(), paint, layoutWidth)
                .setAlignment(alignment)
                .setLineSpacing(AndroidUtilities.dp(4), 1.0f)
                .setIncludePad(false)
                .build();
        return new DrawingText(layout);
    }

    private boolean handleDrawingTextTouch(@NonNull MotionEvent event, @NonNull DrawingText drawingText, int layoutX, int layoutY) {
        if (!(drawingText.getText() instanceof Spanned)) {
            return false;
        }
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_UP) {
            return action == MotionEvent.ACTION_DOWN && findClickableSpan(event, drawingText, layoutX, layoutY) != null;
        }
        ClickableSpan span = findClickableSpan(event, drawingText, layoutX, layoutY);
        if (span == null) {
            return false;
        }
        span.onClick(this.recyclerView == null ? fragmentView : recyclerView);
        return true;
    }

    @Nullable
    private ClickableSpan findClickableSpan(@NonNull MotionEvent event, @NonNull DrawingText drawingText, int layoutX, int layoutY) {
        StaticLayout layout = drawingText.layout;
        int x = Math.round(event.getX()) - layoutX;
        int y = Math.round(event.getY()) - layoutY;
        if (x < 0 || y < 0 || x > layout.getWidth() || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical(y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        Spanned spanned = (Spanned) layout.getText();
        ClickableSpan[] spans = spanned.getSpans(offset, offset, ClickableSpan.class);
        return spans.length == 0 ? null : spans[0];
    }

    final class DrawingText {
        private final StaticLayout layout;
        int x;
        int y;

        DrawingText(@NonNull StaticLayout layout) {
            this.layout = layout;
        }

        void draw(Canvas canvas) {
            layout.draw(canvas);
        }

        void draw(Canvas canvas, View view) {
            layout.draw(canvas);
        }

        CharSequence getText() {
            return layout.getText();
        }

        int getLineCount() {
            return layout.getLineCount();
        }

        int getLineAscent(int line) {
            return layout.getLineAscent(line);
        }

        float getLineWidth(int line) {
            return layout.getLineWidth(line);
        }

        float getLineLeft(int line) {
            return layout.getLineLeft(line);
        }

        int getHeight() {
            return layout.getHeight();
        }
    }

    @Nullable
    private View createBlockViewFor(@NonNull Context context, int viewType) {
        if (viewType == ROW_SHEET_PADDING) {
            return new SheetPaddingCell(context);
        }
        if (viewType == ROW_KICKER) {
            return new KickerCell(context);
        }
        if (viewType == ROW_TITLE) {
            return new TitleCell(context);
        }
        if (viewType == ROW_AUTHOR_DATE) {
            return new AuthorDateCell(context);
        }
        if (viewType == ROW_SUBTITLE) {
            return new SubtitleCell(context);
        }
        if (viewType == ROW_SOURCE) {
            return new SourceCell(context);
        }
        if (viewType == ROW_HEADER) {
            return new HeaderBlockCell(context);
        }
        if (viewType == ROW_SUBHEADER) {
            return new SubheaderBlockCell(context);
        }
        if (viewType == ROW_PARAGRAPH) {
            return new ParagraphBlockCell(context);
        }
        if (viewType == ROW_PHOTO) {
            return new ImageBlockCell(context);
        }
        if (viewType == ROW_EMBED || viewType == ROW_VIDEO || viewType == ROW_AUDIO || viewType == ROW_UNSUPPORTED) {
            return new MediaBlockCell(context);
        }
        if (viewType == ROW_SLIDESHOW || viewType == ROW_COLLAGE) {
            return new GalleryBlockCell(context);
        }
        if (viewType == ROW_FOOTER) {
            return new FooterCell(context);
        }
        if (viewType == ROW_PULLQUOTE || viewType == ROW_BLOCKQUOTE) {
            return new QuoteCell(context);
        }
        if (viewType == ROW_LIST_ITEM) {
            return new BlockListItemCell(context);
        }
        if (viewType == ROW_ORDERED_LIST_ITEM) {
            return new BlockOrderedListItemCell(context);
        }
        if (viewType == ROW_CODE) {
            return new CodeCell(context);
        }
        if (viewType == ROW_DIVIDER) {
            return new DividerCell(context);
        }
        if (viewType == ROW_TABLE) {
            return new TableBlockCell(context);
        }
        if (viewType == ROW_DETAILS) {
            return new DetailsCell(context);
        }
        if (viewType == ROW_DETAILS_BOTTOM) {
            return new DetailsBottomCell(context);
        }
        return null;
    }

    private int getTypeForBlock(@Nullable BrowserPage.PageBlock block) {
        block = unwrapRenderableBlock(block);
        if (block instanceof BrowserPage.PageBlockParagraph) {
            return ROW_PARAGRAPH;
        }
        if (block instanceof BrowserPage.PageBlockHeader) {
            return ROW_HEADER;
        }
        if (block instanceof BrowserPage.PageBlockDivider) {
            return ROW_DIVIDER;
        }
        if (block instanceof BrowserPage.PageBlockEmbed) {
            return ROW_EMBED;
        }
        if (block instanceof BrowserPage.PageBlockSubtitle) {
            return ROW_SUBTITLE;
        }
        if (block instanceof BrowserPage.PageBlockVideo) {
            return ROW_VIDEO;
        }
        if (block instanceof BrowserPage.PageBlockPullquote) {
            return ROW_PULLQUOTE;
        }
        if (block instanceof BrowserPage.PageBlockBlockquote) {
            return ROW_BLOCKQUOTE;
        }
        if (block instanceof BrowserPage.PageBlockSlideshow) {
            return ROW_SLIDESHOW;
        }
        if (block instanceof BrowserPage.PageBlockPhoto) {
            return ROW_PHOTO;
        }
        if (block instanceof BrowserPage.PageBlockAuthorDate) {
            return ROW_AUTHOR_DATE;
        }
        if (block instanceof BrowserPage.PageBlockTitle) {
            return ROW_TITLE;
        }
        if (block instanceof BrowserPage.PageBlockListItem) {
            return ROW_LIST_ITEM;
        }
        if (block instanceof BrowserPage.PageBlockFooter) {
            return ROW_FOOTER;
        }
        if (block instanceof BrowserPage.PageBlockPreformatted) {
            return ROW_CODE;
        }
        if (block instanceof BrowserPage.PageBlockSubheader) {
            return ROW_SUBHEADER;
        }
        if (block instanceof BrowserPage.PageBlockKicker) {
            return ROW_KICKER;
        }
        if (block instanceof BrowserPage.PageBlockCollage) {
            return ROW_COLLAGE;
        }
        if (block instanceof BrowserPage.PageBlockAudio) {
            return ROW_AUDIO;
        }
        if (block instanceof BrowserPage.PageBlockOrderedListItem) {
            return ROW_ORDERED_LIST_ITEM;
        }
        if (block instanceof BrowserPage.PageBlockDetails) {
            return ROW_DETAILS;
        }
        if (block instanceof BrowserPage.PageBlockTable) {
            return ROW_TABLE;
        }
        if (block instanceof BrowserPage.PageBlockDetailsBottom) {
            return ROW_DETAILS_BOTTOM;
        }
        if (block instanceof BrowserPage.PageBlockSource) {
            return ROW_SOURCE;
        }
        if (block instanceof BrowserPage.PageBlockUnsupported) {
            return ROW_UNSUPPORTED;
        }
        return -1;
    }

    private void bindBlockToView(@NonNull View view, @Nullable BrowserPage.PageBlock originalBlock, boolean compact, boolean first) {
        BrowserPage.PageBlock block = unwrapRenderableBlock(originalBlock);
        if (view instanceof KickerCell && block instanceof BrowserPage.PageBlockKicker) {
            ((KickerCell) view).bind(((BrowserPage.PageBlockKicker) block).text, first);
        } else if (view instanceof TitleCell && block instanceof BrowserPage.PageBlockTitle) {
            ((TitleCell) view).bind(buildRichText(((BrowserPage.PageBlockTitle) block).text), first);
        } else if (view instanceof AuthorDateCell && block instanceof BrowserPage.PageBlockAuthorDate) {
            ((AuthorDateCell) view).bind(((BrowserPage.PageBlockAuthorDate) block).text);
        } else if (view instanceof SubtitleCell && block instanceof BrowserPage.PageBlockSubtitle) {
            ((SubtitleCell) view).bind(buildRichText(((BrowserPage.PageBlockSubtitle) block).text));
        } else if (view instanceof SourceCell && block instanceof BrowserPage.PageBlockSource) {
            ((SourceCell) view).bind((BrowserPage.PageBlockSource) block);
        } else if (view instanceof HeaderBlockCell && block instanceof BrowserPage.PageBlockHeader) {
            ((HeaderBlockCell) view).bind((BrowserPage.PageBlockHeader) block, compact);
        } else if (view instanceof SubheaderBlockCell && block instanceof BrowserPage.PageBlockSubheader) {
            ((SubheaderBlockCell) view).bind((BrowserPage.PageBlockSubheader) block, compact);
        } else if (view instanceof ParagraphBlockCell && block instanceof BrowserPage.PageBlockParagraph) {
            ((ParagraphBlockCell) view).bind((BrowserPage.PageBlockParagraph) block, compact);
        } else if (view instanceof ImageBlockCell && block instanceof BrowserPage.PageBlockPhoto) {
            ((ImageBlockCell) view).bind((BrowserPage.PageBlockPhoto) block, first, compact);
        } else if (view instanceof MediaBlockCell
                && (block instanceof BrowserPage.PageBlockEmbed
                || block instanceof BrowserPage.PageBlockVideo
                || block instanceof BrowserPage.PageBlockAudio
                || block instanceof BrowserPage.PageBlockUnsupported)) {
            ((MediaBlockCell) view).bind(block, compact);
        } else if (view instanceof GalleryBlockCell && (block instanceof BrowserPage.PageBlockSlideshow || block instanceof BrowserPage.PageBlockCollage)) {
            ((GalleryBlockCell) view).bind(block, compact);
        } else if (view instanceof FooterCell && block instanceof BrowserPage.PageBlockFooter) {
            ((FooterCell) view).bind((BrowserPage.PageBlockFooter) block, compact);
        } else if (view instanceof QuoteCell && (block instanceof BrowserPage.PageBlockPullquote || block instanceof BrowserPage.PageBlockBlockquote)) {
            ((QuoteCell) view).bind(block, compact);
        } else if (view instanceof BlockListItemCell && block instanceof BrowserPage.PageBlockListItem) {
            ((BlockListItemCell) view).setBlock((BrowserPage.PageBlockListItem) block);
        } else if (view instanceof BlockOrderedListItemCell && block instanceof BrowserPage.PageBlockOrderedListItem) {
            ((BlockOrderedListItemCell) view).setBlock((BrowserPage.PageBlockOrderedListItem) block);
        } else if (view instanceof CodeCell && block instanceof BrowserPage.PageBlockPreformatted) {
            ((CodeCell) view).bind((BrowserPage.PageBlockPreformatted) block);
        } else if (view instanceof TableBlockCell && block instanceof BrowserPage.PageBlockTable) {
            ((TableBlockCell) view).bind((BrowserPage.PageBlockTable) block);
        } else if (view instanceof DetailsCell && block instanceof BrowserPage.PageBlockDetails) {
            ((DetailsCell) view).bind((BrowserPage.PageBlockDetails) block);
        }
    }

    private void loadImage(@NonNull ImageView imageView, @Nullable String url, @Nullable Runnable onLoaded) {
        ArrayList<String> urls = new ArrayList<>();
        addImageUrlCandidate(urls, url);
        loadImage(imageView, urls, onLoaded);
    }

    private void loadImage(@NonNull ImageView imageView, @NonNull BrowserPage.PageBlockPhoto photo, @Nullable Runnable onLoaded) {
        ArrayList<String> urls = new ArrayList<>();
        for (int i = 0; i < photo.urls.size(); i++) {
            addImageUrlCandidate(urls, photo.urls.get(i));
        }
        addImageUrlCandidate(urls, photo.url);
        loadImage(imageView, urls, onLoaded);
    }

    private void loadImage(@NonNull ImageView imageView, @NonNull ArrayList<String> urls, @Nullable Runnable onLoaded) {
        String requestKey = TextUtils.join("\n", urls);
        imageView.setTag(requestKey);
        if (urls.isEmpty()) {
            imageView.setImageDrawable(null);
            if (imageView instanceof RoundedImageView) {
                ((RoundedImageView) imageView).imageReceiver.setImageBitmap((Bitmap) null);
            }
            imageView.setVisibility(View.GONE);
            return;
        }
        imageView.setVisibility(View.VISIBLE);
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            Bitmap cached = null; // TODO(photoviewer): ImageLoader.get(url);
            if (cached != null) {
                imageView.setTag(url);
                imageView.setImageBitmap(cached);
                if (imageView instanceof RoundedImageView) {
                    ((RoundedImageView) imageView).imageReceiver.setImageBitmap(cached);
                }
                if (onLoaded != null) {
                    onLoaded.run();
                }
                return;
            }
        }
        imageView.setImageDrawable(null);
        if (imageView instanceof RoundedImageView) {
            ((RoundedImageView) imageView).imageReceiver.setImageBitmap((Bitmap) null);
        }
        IMAGE_EXECUTOR.execute(() -> {
            LoadedImage loadedImage = null;
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                Bitmap bitmap = null; // TODO(photoviewer): ImageLoader.load(url);
                if (bitmap != null) {
                    loadedImage = new LoadedImage(url, bitmap);
                    break;
                }
            }
            LoadedImage finalLoadedImage = loadedImage;
            MAIN_HANDLER.post(() -> {
                if (!requestKey.equals(imageView.getTag())) {
                    return;
                }
                if (finalLoadedImage != null) {
                    imageView.setTag(finalLoadedImage.url);
                    imageView.setImageBitmap(finalLoadedImage.bitmap);
                    if (imageView instanceof RoundedImageView) {
                        ((RoundedImageView) imageView).imageReceiver.setImageBitmap(finalLoadedImage.bitmap);
                    }
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                } else {
                    imageView.setImageDrawable(null);
                    if (imageView instanceof RoundedImageView) {
                        ((RoundedImageView) imageView).imageReceiver.setImageBitmap((Bitmap) null);
                    }
                }
            });
        });
    }

    private void addImageUrlCandidate(@NonNull ArrayList<String> urls, @Nullable String url) {
        if (TextUtils.isEmpty(url) || urls.contains(url)) {
            return;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            urls.add(url);
        }
    }

    private PhotoImageSize photoImageSize(@NonNull BrowserPage.PageBlockPhoto block, int width) {
        int photoX;
        int photoWidth = Math.max(1, width);
        if (block.level > 0) {
            photoX = AndroidUtilities.dp(14 * block.level) + AndroidUtilities.dp(18);
            photoWidth = Math.max(1, photoWidth - photoX - AndroidUtilities.dp(18));
        } else {
            photoX = 0;
        }
        int imageWidth = block.width > 0 && block.height > 0 ? block.width : 16;
        int imageHeight = block.width > 0 && block.height > 0 ? block.height : 9;
        float scale = photoWidth / (float) imageWidth;
        int photoHeight = Math.max(1, (int) (scale * imageHeight));
        int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(56)) * 0.9f);
        if (photoHeight > maxHeight) {
            photoHeight = Math.max(1, maxHeight);
            scale = photoHeight / (float) imageHeight;
            photoWidth = Math.max(1, (int) (scale * imageWidth));
            photoX += (width - photoX - photoWidth) / 2;
        }
        return new PhotoImageSize(photoX, photoWidth, photoHeight);
    }

    private static final class PhotoImageSize {
        final int x;
        final int width;
        final int height;

        PhotoImageSize(int x, int width, int height) {
            this.x = x;
            this.width = width;
            this.height = height;
        }
    }

    @Nullable
    private String defaultUserAgent(@NonNull Context context) {
        try {
            return WebSettings.getDefaultUserAgent(context);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class LoadedImage {
        final String url;
        final Bitmap bitmap;

        LoadedImage(String url, Bitmap bitmap) {
            this.url = url;
            this.bitmap = bitmap;
        }
    }

    private final class InstantAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            if (halfSheetPadding && position == 0) {
                return ROW_SHEET_PADDING;
            }
            return getTypeForBlock(localBlocks.get(halfSheetPadding ? position - 1 : position));
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = createBlockViewFor(parent.getContext(), viewType);
            if (view == null) {
                view = new View(parent.getContext());
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (halfSheetPadding && position == 0) {
                return;
            }
            int blockIndex = halfSheetPadding ? position - 1 : position;
            bindBlockToView(holder.itemView, localBlocks.get(blockIndex), false, blockIndex == 0);
        }

        @Override
        public int getItemCount() {
            return localBlocks.size() + (halfSheetPadding ? 1 : 0);
        }
    }

    private static final class SheetPaddingCell extends View {
        SheetPaddingCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) (AndroidUtilities.displaySize.y * .4f));
        }
    }

    private final class RoundedImageView extends androidx.appcompat.widget.AppCompatImageView {
        final ImageReceiver imageReceiver = new ImageReceiver(this);
        private int aspectWidth;
        private int aspectHeight;

        RoundedImageView(Context context) {
            super(context);
            imageReceiver.setScaleType(ImageReceiver.ScaleType.FIT_CENTER);
            imageReceiver.setRoundRadius(0);
        }

        void setAspectRatio(int width, int height) {
            if (aspectWidth == width && aspectHeight == height) {
                return;
            }
            aspectWidth = Math.max(0, width);
            aspectHeight = Math.max(0, height);
            if (!measuringContentHeight) {
                requestLayout();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            imageReceiver.setImageCoords(0, 0, w, h);
        }

        @Override
        public void setImageBitmap(Bitmap bm) {
            super.setImageDrawable(null);
            imageReceiver.setImageBitmap(bm);
        }

        @Nullable
        Bitmap getBitmap() {
            return imageReceiver.getBitmap();
        }

        @Override
        public void setImageDrawable(@Nullable Drawable drawable) {
            Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
            super.setImageDrawable(null);
            imageReceiver.setImageBitmap(bitmap);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (aspectWidth > 0 && aspectHeight > 0 && width > 0) {
                int height = Math.max(1, Math.round(width * (aspectHeight / (float) aspectWidth)));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private static LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int marginDp) {
        params.topMargin = AndroidUtilities.dp(marginDp);
        return params;
    }

    private abstract class ArticleTextCell extends FrameLayout {
        protected final TextView textView;

        ArticleTextCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            textView = new TextView(context);
            textView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            textView.setIncludeFontPadding(false);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        }

        protected final void bindText(@Nullable CharSequence text, boolean centered, float textSizeDp, int textColor, @Nullable Typeface typeface,
                                      int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
            textView.setTextColor(textColor);
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(textSizeDp));
            textView.setTypeface(typeface);
            textView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            applyText(textView, text, centered);
        }

        int firstTextBaseline() {
            return getPaddingTop() + textView.getBaseline();
        }
    }

    private final class KickerCell extends ArticleTextCell {
        KickerCell(Context context) {
            super(context);
        }

        void bind(@Nullable CharSequence text, boolean first) {
            bindText(text, false, 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT,
                    AndroidUtilities.dp(18), AndroidUtilities.dp(first ? 16 : 8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        }
    }

    private final class TitleCell extends ArticleTextCell {
        TitleCell(Context context) {
            super(context);
        }

        void bind(@Nullable CharSequence text, boolean first) {
            bindText(text, false, 23, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT_BOLD,
                    AndroidUtilities.dp(18), AndroidUtilities.dp(first ? 16 : 8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        }
    }

    private final class AuthorDateCell extends ArticleTextCell {
        AuthorDateCell(Context context) {
            super(context);
        }

        void bind(@Nullable CharSequence text) {
            bindText(text, false, 14, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), Typeface.DEFAULT,
                    AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        }
    }

    private final class SubtitleCell extends ArticleTextCell {
        SubtitleCell(Context context) {
            super(context);
        }

        void bind(@Nullable CharSequence text) {
            bindText(text, false, 20, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT_BOLD,
                    AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        }
    }

    private final class SourceCell extends ArticleTextCell {
        SourceCell(Context context) {
            super(context);
        }

        void bind(@NonNull BrowserPage.PageBlockSource block) {
            bindText(buildRichText(block.text), false, 14,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), Typeface.DEFAULT,
                    AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        }
    }

    private final class FooterCell extends ArticleTextCell {
        FooterCell(Context context) {
            super(context);
        }

        void bind(@NonNull BrowserPage.PageBlockFooter block, boolean compact) {
            int horizontalPadding = compact ? 0 : AndroidUtilities.dp(18);
            bindText(buildRichText(block.text), block.text.centered, 14,
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), Typeface.DEFAULT,
                    horizontalPadding, AndroidUtilities.dp(8), horizontalPadding, AndroidUtilities.dp(8));
        }
    }

    private final class HeaderBlockCell extends ArticleTextCell {
        HeaderBlockCell(Context context) {
            super(context);
        }

        void bind(@NonNull BrowserPage.PageBlockHeader block, boolean compact) {
            int horizontalPadding = compact ? 0 : AndroidUtilities.dp(18);
            bindText(buildRichText(block.text), block.text.centered, 20,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT_BOLD,
                    horizontalPadding, AndroidUtilities.dp(8), horizontalPadding, AndroidUtilities.dp(8));
        }
    }

    private final class SubheaderBlockCell extends ArticleTextCell {
        SubheaderBlockCell(Context context) {
            super(context);
        }

        void bind(@NonNull BrowserPage.PageBlockSubheader block, boolean compact) {
            int horizontalPadding = compact ? 0 : AndroidUtilities.dp(18);
            bindText(buildRichText(block.text), block.text.centered, 17,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT_BOLD,
                    horizontalPadding, AndroidUtilities.dp(8), horizontalPadding, AndroidUtilities.dp(8));
        }
    }

    private final class ParagraphBlockCell extends ArticleTextCell {
        ParagraphBlockCell(Context context) {
            super(context);
        }

        void bind(@NonNull BrowserPage.PageBlockParagraph block, boolean compact) {
            int horizontalPadding = compact ? 0 : AndroidUtilities.dp(18);
            int topPadding = compact ? 0 : AndroidUtilities.dp(8);
            bindText(buildRichText(block.text), block.text.centered, 16,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Typeface.DEFAULT,
                    horizontalPadding, topPadding, horizontalPadding, AndroidUtilities.dp(8));
        }
    }

    private final class QuoteCell extends FrameLayout {
        private final FrameLayout container;
        private final View stripeView;
        private final LinearLayout contentLayout;
        private final LinearLayout blocksLayout;
        private final FrameLayout.LayoutParams contentParams;
        private final TextView textView;
        private final TextView footerView;
        private final GradientDrawable blockquoteStripe;

        QuoteCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            setPadding(0, 0, 0, 0);

            container = new FrameLayout(context);
            FrameLayout.LayoutParams containerParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            containerParams.leftMargin = AndroidUtilities.dp(18);
            containerParams.rightMargin = AndroidUtilities.dp(18);
            addView(container, containerParams);

            stripeView = new View(context);
            blockquoteStripe = new GradientDrawable();
            blockquoteStripe.setColor(Theme.multAlpha(Theme.getColor(Theme.key_telegram_color), .9f));
            blockquoteStripe.setCornerRadius(AndroidUtilities.dp(1));
            stripeView.setBackground(blockquoteStripe);
            container.addView(stripeView, LayoutHelper.createFrame(2, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

            contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            contentParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            contentParams.leftMargin = AndroidUtilities.dp(14);
            container.addView(contentLayout, contentParams);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(15));
            textView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            contentLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            footerView = new TextView(context);
            footerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            footerView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(14));
            footerView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            contentLayout.addView(footerView, topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 8));

            blocksLayout = new LinearLayout(context);
            blocksLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.addView(blocksLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        void bind(BrowserPage.PageBlock block) {
            bind(block, false);
        }

        void bind(BrowserPage.PageBlock block, boolean compact) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
            params.leftMargin = compact ? 0 : AndroidUtilities.dp(18);
            params.rightMargin = compact ? 0 : AndroidUtilities.dp(18);
            container.setLayoutParams(params);
            if (block instanceof BrowserPage.PageBlockPullquote) {
                BrowserPage.PageBlockPullquote quote = (BrowserPage.PageBlockPullquote) block;
                container.setBackground(null);
                stripeView.setVisibility(GONE);
                contentLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                contentParams.leftMargin = 0;
                textView.setVisibility(VISIBLE);
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(15));
                textView.setLineSpacing(0, 1.0f);
                applyText(textView, buildRichText(quote.text), quote.text.centered);
                CharSequence footer = buildRichText(quote.caption);
                footerView.setVisibility(TextUtils.isEmpty(footer) ? GONE : VISIBLE);
                applyText(footerView, footer, quote.caption.centered);
                ((LinearLayout.LayoutParams) footerView.getLayoutParams()).topMargin = AndroidUtilities.dp(2);
                blocksLayout.removeAllViews();
                blocksLayout.setVisibility(GONE);
            } else {
                BrowserPage.PageBlockBlockquote quote = (BrowserPage.PageBlockBlockquote) block;
                container.setBackground(null);
                stripeView.setVisibility(VISIBLE);
                contentLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                contentParams.leftMargin = AndroidUtilities.dp(14);
                textView.setVisibility(VISIBLE);
                applyText(textView, buildRichText(quote.text), quote.text.centered);
                CharSequence footer = buildRichText(quote.caption);
                footerView.setVisibility(TextUtils.isEmpty(footer) ? GONE : VISIBLE);
                applyText(footerView, footer, quote.caption.centered);
                blocksLayout.removeAllViews();
                blocksLayout.setVisibility(GONE);
            }
            contentLayout.setLayoutParams(contentParams);
        }
    }

    private abstract class ListItemCellBase extends ViewGroup {
        private final TextPaint listTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint listMarkerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        @Nullable private DrawingText markerLayout;
        @Nullable private DrawingText textLayout;
        private View blockView;
        private int blockViewType = -1;
        private boolean ordered;
        private boolean lastItem;
        private boolean verticalAlign;
        private boolean drawDot;
        private int index;
        private int level;
        private int maxNumWidth;
        private String num;
        private BrowserPage.RichText textItem;
        private BrowserPage.PageBlock blockItem;
        private int textX;
        private int textY;
        private int blockX;
        private int blockY;
        private int numOffsetY;

        ListItemCellBase(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
        }

        void setCommonBlock(boolean ordered, int index, int level, int maxNumWidth, boolean lastItem,
                            @Nullable String num, @Nullable BrowserPage.RichText textItem, @Nullable BrowserPage.PageBlock blockItem) {
            this.ordered = ordered;
            this.index = index;
            this.level = level;
            this.maxNumWidth = maxNumWidth;
            this.lastItem = lastItem;
            this.num = num;
            this.textItem = textItem;
            this.blockItem = blockItem;

            int newBlockType = blockItem == null ? -1 : getTypeForBlock(blockItem);
            if (newBlockType != blockViewType) {
                if (blockView != null) {
                    removeView(blockView);
                    blockView = null;
                }
                blockViewType = newBlockType;
                if (newBlockType >= 0) {
                    blockView = createBlockViewFor(getContext(), newBlockType);
                    if (blockView != null) {
                        addView(blockView);
                    }
                }
            }
            if (blockView != null) {
                blockView.setVisibility(blockItem == null ? GONE : VISIBLE);
            }
            if (blockView != null && blockItem != null) {
                bindBlockToView(blockView, blockItem, true, false);
            }
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            int step = ordered ? 20 : 12;
            textX = AndroidUtilities.dp(18 + 6) + maxNumWidth + level * AndroidUtilities.dp(step);
            textY = index == 0 && level == 0 ? AndroidUtilities.dp(10) : 0;
            numOffsetY = 0;
            verticalAlign = false;
            drawDot = !ordered;
            markerLayout = createDrawingText(TextUtils.isEmpty(num) ? "" : num, listMarkerPaint(ordered), Math.max(1, maxNumWidth), Layout.Alignment.ALIGN_NORMAL);
            textLayout = null;

            int maxWidth = Math.max(1, width - AndroidUtilities.dp(18) - textX);
            if (textItem != null) {
                textLayout = createDrawingText(buildRichText(textItem), listTextPaint(), maxWidth,
                        textItem.centered ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL);
                if (textLayout != null && markerLayout != null && textLayout.getLineCount() > 0 && markerLayout.getLineCount() > 0) {
                    int ascent = textLayout.getLineAscent(0);
                    numOffsetY = markerLayout.getLineAscent(0) - ascent + (ordered ? 0 : AndroidUtilities.dp(2.5f));
                    height += textLayout.getHeight() + AndroidUtilities.dp(8);
                }
            } else if (blockItem != null && blockView != null) {
                blockX = textX;
                blockY = textY;
                if (blockItem instanceof BrowserPage.PageBlockParagraph) {
                    blockY -= AndroidUtilities.dp(8);
                    blockX -= AndroidUtilities.dp(18);
                    maxWidth += AndroidUtilities.dp(18);
                    height -= AndroidUtilities.dp(8);
                } else if (blockItem instanceof BrowserPage.PageBlockHeader
                        || blockItem instanceof BrowserPage.PageBlockSubheader
                        || blockItem instanceof BrowserPage.PageBlockTitle
                        || blockItem instanceof BrowserPage.PageBlockSubtitle) {
                    blockX -= AndroidUtilities.dp(18);
                    maxWidth += AndroidUtilities.dp(18);
                } else if (isListItemBlock(blockItem)) {
                    blockX = 0;
                    blockY = 0;
                    textY = 0;
                    maxWidth = width;
                    if (!ordered && index == 0 && level == 0) {
                        height -= AndroidUtilities.dp(10);
                    }
                    height -= AndroidUtilities.dp(8);
                } else if (blockItem instanceof BrowserPage.PageBlockTable) {
                    blockX -= AndroidUtilities.dp(18);
                    maxWidth += AndroidUtilities.dp(36);
                }
                blockView.measure(
                        MeasureSpec.makeMeasureSpec(Math.max(1, maxWidth), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                );
                if (blockItem instanceof BrowserPage.PageBlockDetails) {
                    verticalAlign = true;
                    blockY = 0;
                    if (!ordered && index == 0 && level == 0) {
                        height -= AndroidUtilities.dp(10);
                    }
                    height -= AndroidUtilities.dp(8);
                } else if (blockView instanceof BlockListItemCell) {
                    verticalAlign = ((BlockListItemCell) blockView).isVerticalAlign();
                } else if (blockView instanceof BlockOrderedListItemCell) {
                    verticalAlign = ((BlockOrderedListItemCell) blockView).isVerticalAlign();
                }
                if (verticalAlign && markerLayout != null) {
                    textY = (blockView.getMeasuredHeight() - markerLayout.getHeight()) / 2 - AndroidUtilities.dp(ordered ? 0 : 4);
                    drawDot = false;
                } else if (blockItem instanceof BrowserPage.PageBlockParagraph && blockView instanceof ParagraphBlockCell) {
                    alignMarkerToBlockBaseline((ParagraphBlockCell) blockView, ordered ? 0 : AndroidUtilities.dp(2.5f));
                }
                height += blockView.getMeasuredHeight() + AndroidUtilities.dp(8);
            } else {
                height = AndroidUtilities.dp(1);
            }
            if (lastItem) {
                height += AndroidUtilities.dp(8);
            }
            if (index == 0 && level == 0) {
                height += AndroidUtilities.dp(10);
            }
            setMeasuredDimension(width, Math.max(1, height));
        }

        private void alignMarkerToBlockBaseline(@NonNull ArticleTextCell cell, int extraOffset) {
            int blockBaseline = cell.firstTextBaseline();
            if (markerLayout != null && markerLayout.getLineCount() > 0 && blockBaseline > 0) {
                numOffsetY = markerLayout.getLineAscent(0) - (blockBaseline - blockY) + extraOffset;
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (blockView != null) {
                blockView.layout(blockX, blockY, blockX + blockView.getMeasuredWidth(), blockY + blockView.getMeasuredHeight());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (markerLayout != null) {
                canvas.save();
                int markerLeft = ordered
                        ? AndroidUtilities.dp(18) + maxNumWidth - (int) Math.ceil(markerLayout.getLineWidth(0)) + level * AndroidUtilities.dp(20)
                        : AndroidUtilities.dp(15) + maxNumWidth - (int) Math.ceil(markerLayout.getLineWidth(0)) + level * AndroidUtilities.dp(12);
                canvas.translate(markerLeft, textY + numOffsetY - (drawDot ? AndroidUtilities.dp(1) : 0));
                markerLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (textLayout != null && handleDrawingTextTouch(event, textLayout, textX, textY)) {
                return true;
            }
            return super.onTouchEvent(event);
        }

        boolean isVerticalAlign() {
            return verticalAlign;
        }

        private TextPaint listTextPaint() {
            listTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            listTextPaint.setTypeface(Typeface.DEFAULT);
            listTextPaint.setTextSize(AndroidUtilities.dpf2(scaledSp(16)));
            return listTextPaint;
        }

        private TextPaint listMarkerPaint(boolean ordered) {
            listMarkerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            listMarkerPaint.setTypeface(ordered ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
            listMarkerPaint.setTextSize(AndroidUtilities.dpf2(scaledSp(ordered ? 16 : 19)));
            return listMarkerPaint;
        }
    }

    private final class BlockListItemCell extends ListItemCellBase {
        BlockListItemCell(Context context) {
            super(context);
        }

        void setBlock(@NonNull BrowserPage.PageBlockListItem block) {
            BrowserPage.PageBlockListParent parent = block.parent;
            int maxNumWidth = updateListParentMaxNumWidth(parent);
            setCommonBlock(false, block.index, parent.level, maxNumWidth, parent.items.get(parent.items.size() - 1) == block,
                    block.num, block.textItem, block.blockItem);
        }
    }

    private final class BlockOrderedListItemCell extends ListItemCellBase {
        BlockOrderedListItemCell(Context context) {
            super(context);
        }

        void setBlock(@NonNull BrowserPage.PageBlockOrderedListItem block) {
            BrowserPage.PageBlockOrderedListParent parent = block.parent;
            int maxNumWidth = updateOrderedListParentMaxNumWidth(parent);
            setCommonBlock(true, block.index, parent.level, maxNumWidth, parent.items.get(parent.items.size() - 1) == block,
                    block.num, block.textItem, block.blockItem);
        }
    }

    private boolean isListItemBlock(@Nullable BrowserPage.PageBlock block) {
        return block instanceof BrowserPage.PageBlockListItem || block instanceof BrowserPage.PageBlockOrderedListItem;
    }

    private int updateListParentMaxNumWidth(@NonNull BrowserPage.PageBlockListParent parent) {
        TextPaint numPaint = listTextNumPaint();
        TextPaint bulletPaint = listTextPointerPaint();
        int max = (int) Math.ceil(numPaint.measureText("00."));
        for (int i = 0; i < parent.items.size(); i++) {
            String num = parent.items.get(i).num;
            if (!TextUtils.isEmpty(num)) {
                max = Math.max(max, (int) Math.ceil(bulletPaint.measureText(num)));
            }
        }
        parent.maxNumWidth = max;
        return max;
    }

    private int updateOrderedListParentMaxNumWidth(@NonNull BrowserPage.PageBlockOrderedListParent parent) {
        TextPaint paint = listTextNumPaint();
        int max = (int) Math.ceil(paint.measureText("00."));
        for (int i = 0; i < parent.items.size(); i++) {
            String num = parent.items.get(i).num;
            if (!TextUtils.isEmpty(num)) {
                max = Math.max(max, (int) Math.ceil(paint.measureText(num)));
            }
        }
        parent.maxNumWidth = max;
        return max;
    }

    private TextPaint listTextNumPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(AndroidUtilities.dpf2(scaledSp(16)));
        return paint;
    }

    private TextPaint listTextPointerPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(AndroidUtilities.dpf2(scaledSp(19)));
        return paint;
    }

    private final class CodeCell extends FrameLayout {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final HorizontalScrollView scrollView;
        private final TextView textView;

        CodeCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .08f));
            scrollView = new HorizontalScrollView(context);
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));
            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(14));
            textView.setTypeface(Typeface.MONOSPACE);
            textView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            textView.setPadding(0, 0, 0, 0);
            HorizontalScrollView.LayoutParams params = new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = AndroidUtilities.dp(16);
            params.rightMargin = AndroidUtilities.dp(16);
            params.topMargin = AndroidUtilities.dp(12);
            params.bottomMargin = AndroidUtilities.dp(12);
            scrollView.addView(textView, params);
            setWillNotDraw(false);
        }

        void bind(BrowserPage.PageBlockPreformatted codeBlock) {
            textView.setText(codeBlock.text == null ? "" : codeBlock.text);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, AndroidUtilities.dp(8), getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(8), backgroundPaint);
        }
    }

    private static final class DividerCell extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DividerCell(Context context) {
            super(context);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .22f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(18));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getMeasuredWidth() / 3;
            int top = AndroidUtilities.dp(8);
            int bottom = AndroidUtilities.dp(10);
            canvas.drawRoundRect(width, top, width * 2f, bottom, AndroidUtilities.dp(1), AndroidUtilities.dp(1), paint);
        }
    }

    private final class ImageBlockCell extends LinearLayout {
        private final RoundedImageView imageView;
        private final TextView captionView;
        private final TextView creditView;
        private final LinearLayout.LayoutParams imageParams;
        private BrowserPage.PageBlockPhoto currentBlock;
        private boolean currentFirst;
        private boolean compact;

        ImageBlockCell(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            setPadding(0, 0, 0, AndroidUtilities.dp(8));

            imageView = new RoundedImageView(context);
            imageView.setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .08f));
            imageParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            addView(imageView, imageParams);

            captionView = new TextView(context);
            captionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            captionView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(14));
            captionView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            LinearLayout.LayoutParams captionParams = topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 8);
            captionParams.leftMargin = AndroidUtilities.dp(18);
            captionParams.rightMargin = AndroidUtilities.dp(18);
            addView(captionView, captionParams);

            creditView = new TextView(context);
            creditView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            creditView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(12));
            creditView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            LinearLayout.LayoutParams creditParams = topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 4);
            creditParams.leftMargin = AndroidUtilities.dp(18);
            creditParams.rightMargin = AndroidUtilities.dp(18);
            creditParams.bottomMargin = AndroidUtilities.dp(8);
            addView(creditView, creditParams);
        }

        void bind(BrowserPage.PageBlockPhoto block, boolean hero) {
            bind(block, hero, false);
        }

        void bind(BrowserPage.PageBlockPhoto block, boolean hero, boolean compact) {
            currentBlock = block;
            currentFirst = hero;
            this.compact = compact;
            int textHorizontalPadding = compact ? 0 : AndroidUtilities.dp(18);
            LinearLayout.LayoutParams captionParams = (LinearLayout.LayoutParams) captionView.getLayoutParams();
            captionParams.leftMargin = textHorizontalPadding;
            captionParams.rightMargin = textHorizontalPadding;
            captionView.setLayoutParams(captionParams);
            LinearLayout.LayoutParams creditParams = (LinearLayout.LayoutParams) creditView.getLayoutParams();
            creditParams.leftMargin = textHorizontalPadding;
            creditParams.rightMargin = textHorizontalPadding;
            creditView.setLayoutParams(creditParams);
            if (!measuringContentHeight) {
                loadImage(imageView, block, () -> {
                    Bitmap loadedBitmap = imageView.getBitmap();
                    if (loadedBitmap == null || block.width > 0 && block.height > 0) {
                        return;
                    }
                    int width = loadedBitmap.getWidth();
                    int height = loadedBitmap.getHeight();
                    if (width > 0 && height > 0) {
                        block.width = width;
                        block.height = height;
                        requestLayout();
                        resetCachedHeights();
                    }
                });
            }
            String imageUrl = block.url;
            if (TextUtils.isEmpty(imageUrl)) {
                imageView.setOnClickListener(null);
                imageView.setClickable(false);
            } else {
                imageView.setClickable(true);
                imageView.setOnClickListener(v -> {
                    Bitmap initial = imageView.getBitmap();
                    String openUrl = imageUrl;
                    Object tag = imageView.getTag();
                    if (tag instanceof String && block.urls.contains(tag)) {
                        openUrl = (String) tag;
                    }
                    openImageViewer(initial, openUrl, imageView);
                });
            }
            CharSequence caption = buildRichText(block.caption);
            captionView.setVisibility(TextUtils.isEmpty(caption) ? GONE : VISIBLE);
            applyText(captionView, caption, block.caption.centered);

            CharSequence credit = buildRichText(block.credit);
            creditView.setVisibility(TextUtils.isEmpty(credit) ? GONE : VISIBLE);
            applyText(creditView, credit, block.credit.centered);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (currentBlock != null) {
                PhotoImageSize imageSize = photoImageSize(currentBlock, width);
                imageParams.width = imageSize.width;
                imageParams.height = imageSize.height;
                imageParams.leftMargin = imageSize.x;
                imageParams.rightMargin = Math.max(0, width - imageSize.x - imageSize.width);
                imageParams.topMargin = currentFirst || compact || currentBlock.level > 0 ? 0 : AndroidUtilities.dp(8);
                imageView.setAspectRatio(imageSize.width, imageSize.height);
                imageView.setLayoutParams(imageParams);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private final class MediaBlockCell extends LinearLayout {
        private final LinearLayout cardView;
        private final RoundedImageView posterView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView captionView;
        private final TextView creditView;
        private final GradientDrawable cardBackground;

        MediaBlockCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));

            cardBackground = new GradientDrawable();
            cardBackground.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .07f));
            cardBackground.setStroke(AndroidUtilities.dp(1), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .18f));
            cardBackground.setCornerRadius(AndroidUtilities.dp(6));

            cardView = new LinearLayout(context);
            cardView.setOrientation(VERTICAL);
            cardView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            cardView.setBackground(cardBackground);
            addView(cardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            posterView = new RoundedImageView(context);
            posterView.setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .1f));
            cardView.addView(posterView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(15));
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setSingleLine(false);
            titleView.setLineSpacing(AndroidUtilities.dp(3), 1.0f);
            cardView.addView(titleView, topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 8));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(13));
            subtitleView.setSingleLine(false);
            subtitleView.setLineSpacing(AndroidUtilities.dp(3), 1.0f);
            cardView.addView(subtitleView, topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 4));

            captionView = new TextView(context);
            captionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            captionView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(14));
            captionView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            addView(captionView, topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 8));

            creditView = new TextView(context);
            creditView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            creditView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(12));
            creditView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            addView(creditView, topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 4));
        }

        void bind(@NonNull BrowserPage.PageBlock block, boolean compact) {
            setPadding(compact ? 0 : AndroidUtilities.dp(18), AndroidUtilities.dp(8), compact ? 0 : AndroidUtilities.dp(18), AndroidUtilities.dp(8));
            String url = null;
            String poster = null;
            int width = 0;
            int height = 0;
            CharSequence title;
            CharSequence caption = "";
            CharSequence credit = "";
            if (block instanceof BrowserPage.PageBlockEmbed) {
                BrowserPage.PageBlockEmbed embed = (BrowserPage.PageBlockEmbed) block;
                url = embed.url;
                width = embed.width;
                height = embed.height;
                title = TextUtils.isEmpty(embed.title) ? getStringSafe(R.string.app_browser_reader_embed) : embed.title;
                caption = buildRichText(embed.caption);
                credit = buildRichText(embed.credit);
            } else if (block instanceof BrowserPage.PageBlockVideo) {
                BrowserPage.PageBlockVideo video = (BrowserPage.PageBlockVideo) block;
                url = video.url;
                poster = video.posterUrl;
                width = video.width;
                height = video.height;
                title = getStringSafe(R.string.app_browser_reader_video);
                caption = buildRichText(video.caption);
                credit = buildRichText(video.credit);
            } else if (block instanceof BrowserPage.PageBlockAudio) {
                BrowserPage.PageBlockAudio audio = (BrowserPage.PageBlockAudio) block;
                url = audio.url;
                title = TextUtils.isEmpty(audio.title) ? getStringSafe(R.string.app_browser_reader_audio) : audio.title;
                caption = buildRichText(audio.caption);
                credit = buildRichText(audio.credit);
            } else {
                BrowserPage.PageBlockUnsupported unsupported = (BrowserPage.PageBlockUnsupported) block;
                url = unsupported.url;
                title = TextUtils.isEmpty(unsupported.title) ? getStringSafe(R.string.app_browser_reader_unsupported) : unsupported.title;
            }

            boolean hasPoster = !TextUtils.isEmpty(poster);
            posterView.setVisibility(hasPoster ? VISIBLE : GONE);
            if (hasPoster) {
                posterView.setAspectRatio(width > 0 && height > 0 ? width : 16, width > 0 && height > 0 ? height : 9);
                if (!measuringContentHeight) {
                    loadImage(posterView, poster, null);
                }
            } else {
                posterView.setImageDrawable(null);
                posterView.imageReceiver.setImageBitmap((Bitmap) null);
            }

            titleView.setText(title);
            String subtitle = displayHost(url);
            subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
            subtitleView.setText(subtitle);

            String openUrl = url;
            cardView.setClickable(!TextUtils.isEmpty(openUrl));
            cardView.setOnClickListener(TextUtils.isEmpty(openUrl) ? null : v -> openExternal(openUrl));

            captionView.setVisibility(TextUtils.isEmpty(caption) ? GONE : VISIBLE);
            applyText(captionView, caption);
            creditView.setVisibility(TextUtils.isEmpty(credit) ? GONE : VISIBLE);
            applyText(creditView, credit);
        }
    }

    private final class GalleryBlockCell extends LinearLayout {
        private final LinearLayout itemsLayout;
        private final TextView captionView;
        private final TextView creditView;

        GalleryBlockCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));

            itemsLayout = new LinearLayout(context);
            itemsLayout.setOrientation(VERTICAL);
            addView(itemsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            captionView = new TextView(context);
            captionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            captionView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(14));
            captionView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            LinearLayout.LayoutParams captionParams = topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 8);
            captionParams.leftMargin = AndroidUtilities.dp(18);
            captionParams.rightMargin = AndroidUtilities.dp(18);
            addView(captionView, captionParams);

            creditView = new TextView(context);
            creditView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            creditView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, scaledSp(12));
            creditView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            LinearLayout.LayoutParams creditParams = topMargin(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT), 4);
            creditParams.leftMargin = AndroidUtilities.dp(18);
            creditParams.rightMargin = AndroidUtilities.dp(18);
            creditParams.bottomMargin = AndroidUtilities.dp(8);
            addView(creditView, creditParams);
        }

        void bind(@NonNull BrowserPage.PageBlock block, boolean compact) {
            itemsLayout.removeAllViews();
            ArrayList<BrowserPage.PageBlock> items;
            BrowserPage.RichText caption;
            BrowserPage.RichText credit;
            if (block instanceof BrowserPage.PageBlockSlideshow) {
                BrowserPage.PageBlockSlideshow slideshow = (BrowserPage.PageBlockSlideshow) block;
                items = slideshow.items;
                caption = slideshow.caption;
                credit = slideshow.credit;
            } else {
                BrowserPage.PageBlockCollage collage = (BrowserPage.PageBlockCollage) block;
                items = collage.items;
                caption = collage.caption;
                credit = collage.credit;
            }
            appendBlockViews(itemsLayout, items, compact);
            CharSequence captionText = buildRichText(caption);
            captionView.setVisibility(TextUtils.isEmpty(captionText) ? GONE : VISIBLE);
            applyText(captionView, captionText, caption.centered);
            CharSequence creditText = buildRichText(credit);
            creditView.setVisibility(TextUtils.isEmpty(creditText) ? GONE : VISIBLE);
            applyText(creditView, creditText, credit.centered);
        }
    }

    @Nullable
    private String displayHost(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost();
        } catch (Exception ignored) {
            return url;
        }
    }

    private final class TableBlockCell extends ViewGroup {
        @Nullable private BrowserPage.PageBlockTable currentBlock;
        @Nullable private DrawingText titleLayout;
        private final HorizontalScrollView scrollView;
        private final BrowserInstantTableLayout tableLayout;
        private int listX;
        private int listY;
        private int textX;
        private int textY;

        TableBlockCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));

            scrollView = new HorizontalScrollView(context) {
                @Override
                public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
                    boolean intercept = super.onInterceptTouchEvent(ev);
                    if (tableLayout.getMeasuredWidth() > getMeasuredWidth() - AndroidUtilities.dp(36) && intercept) {
                        requestDisallowInterceptTouchEvent(true);
                    }
                    return intercept;
                }

                @Override
                public boolean onTouchEvent(android.view.MotionEvent ev) {
                    if (tableLayout.getMeasuredWidth() <= getMeasuredWidth() - AndroidUtilities.dp(36)) {
                        return false;
                    }
                    return super.onTouchEvent(ev);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    tableLayout.measure(
                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight(), MeasureSpec.UNSPECIFIED),
                            heightMeasureSpec
                    );
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), tableLayout.getMeasuredHeight());
                }
            };
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.setClipToPadding(false);
            scrollView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            tableLayout = new BrowserInstantTableLayout(context, new BrowserInstantTableLayout.TableLayoutDelegate() {
                @Override
                public DrawingText createTextLayout(BrowserPage.PageTableCell cell, int maxWidth) {
                    return createDrawingText(buildRichText(cell.text), tableTextPaint(), maxWidth, tableAlignment(cell));
                }

                @Override
                public Paint getLinePaint() {
                    return tableLinePaint();
                }

                @Override
                public Paint getHalfLinePaint() {
                    return tableHalfLinePaint();
                }

                @Override
                public Paint getHeaderPaint() {
                    return tableHeaderPaint();
                }

                @Override
                public Paint getStripPaint() {
                    return tableStripPaint();
                }

                @Override
                public void onLayoutChild(DrawingText text, int x, int y) {
                }
            });
            tableLayout.setOrientation(BrowserInstantTableLayout.HORIZONTAL);
            tableLayout.setRowOrderPreserved(true);
            scrollView.addView(tableLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setWillNotDraw(false);
        }

        void bind(BrowserPage.PageBlockTable block) {
            currentBlock = block;
            tableLayout.removeAllChildrens();
            tableLayout.setDrawLines(block.bordered);
            tableLayout.setStriped(block.striped);
            tableLayout.setRtl(false);
            int maxCols = 0;
            if (!block.rows.isEmpty()) {
                BrowserPage.PageTableRow row = block.rows.get(0);
                for (int i = 0; i < row.cells.size(); i++) {
                    maxCols += Math.max(1, row.cells.get(i).colspan);
                }
            }
            for (int rowIndex = 0; rowIndex < block.rows.size(); rowIndex++) {
                int cols = 0;
                BrowserPage.PageTableRow row = block.rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < row.cells.size(); cellIndex++) {
                    BrowserPage.PageTableCell cell = row.cells.get(cellIndex);
                    int colspan = Math.max(1, cell.colspan);
                    tableLayout.addChild(cell, cols, rowIndex, colspan);
                    cols += colspan;
                }
            }
            tableLayout.setColumnCount(maxCols);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            for (int i = 0; i < tableLayout.getChildCount(); i++) {
                BrowserInstantTableLayout.Child child = tableLayout.getChildAt(i);
                if (child != null && child.textLayout != null
                        && handleDrawingTextTouch(event, child.textLayout, scrollView.getPaddingLeft() - scrollView.getScrollX() + listX + child.getTextX(), listY + child.getTextY())) {
                    return true;
                }
            }
            return titleLayout != null && handleDrawingTextTouch(event, titleLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentBlock != null) {
                listX = 0;
                textX = AndroidUtilities.dp(18);
                int textWidth = width - AndroidUtilities.dp(36);
                titleLayout = createDrawingText(buildRichText(currentBlock.title), tableTextPaint(), textWidth, Layout.Alignment.ALIGN_CENTER);
                if (titleLayout != null) {
                    textY = 0;
                    height += titleLayout.getHeight() + AndroidUtilities.dp(8);
                    listY = height;
                    titleLayout.x = textX;
                    titleLayout.y = textY;
                } else {
                    listY = AndroidUtilities.dp(8);
                }
                scrollView.measure(
                        MeasureSpec.makeMeasureSpec(width - listX, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                );
                height += scrollView.getMeasuredHeight() + AndroidUtilities.dp(8);
            } else {
                height = 1;
                titleLayout = null;
            }
            setMeasuredDimension(width, height);
            updateChildTextPositions();
        }

        private void updateChildTextPositions() {
            for (int i = 0; i < tableLayout.getChildCount(); i++) {
                BrowserInstantTableLayout.Child child = tableLayout.getChildAt(i);
                if (child != null && child.textLayout != null) {
                    child.textLayout.x = child.getTextX() + listX + AndroidUtilities.dp(18) - scrollView.getScrollX();
                    child.textLayout.y = child.getTextY() + listY;
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            scrollView.layout(listX, listY, listX + scrollView.getMeasuredWidth(), listY + scrollView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null || titleLayout == null) {
                return;
            }
            canvas.save();
            canvas.translate(textX, textY);
            titleLayout.draw(canvas);
            canvas.restore();
        }
    }

    private Paint tableLinePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setColor(Theme.getColor(Theme.key_divider));
        return paint;
    }

    private Paint tableHalfLinePaint() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1) / 2.0f);
        paint.setColor(Theme.getColor(Theme.key_divider));
        return paint;
    }

    private Paint tableHeaderPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int base = Theme.getColor(Theme.key_telegram_color);
        paint.setColor(Color.argb(34, Color.red(base), Color.green(base), Color.blue(base)));
        return paint;
    }

    private Paint tableStripPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int base = Theme.getColor(Theme.key_telegram_color);
        paint.setColor(Color.argb(20, Color.red(base), Color.green(base), Color.blue(base)));
        return paint;
    }

    private TextPaint tableTextPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(AndroidUtilities.dpf2(scaledSp(15)));
        return paint;
    }

    private Layout.Alignment tableAlignment(@NonNull BrowserPage.PageTableCell cell) {
        if (cell.alignRight) {
            return Layout.Alignment.ALIGN_OPPOSITE;
        }
        if (cell.alignCenter) {
            return Layout.Alignment.ALIGN_CENTER;
        }
        return Layout.Alignment.ALIGN_NORMAL;
    }

    private final class DetailsCell extends View implements Drawable.Callback {
        private BrowserPage.PageBlockDetails currentBlock;
        private final AnimatedArrowDrawable arrowDrawable;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Nullable private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(44 + 6);
        private int textY = AndroidUtilities.dp(11) + 1;

        DetailsCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Theme.getColor(Theme.key_iv_background));
            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
            arrowDrawable = new AnimatedArrowDrawable(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), true);
            setOnClickListener(v -> toggle());
        }

        void bind(BrowserPage.PageBlockDetails block) {
            currentBlock = block;
            arrowDrawable.setCallback(this);
            updateExpandedState();
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(39);
            textLayout = null;
            if (currentBlock != null) {
                textLayout = createDrawingText(buildRichText(currentBlock.title), detailsTextPaint(), width - AndroidUtilities.dp(36 + 16), Layout.Alignment.ALIGN_NORMAL);
                if (textLayout != null) {
                    height = Math.max(height, AndroidUtilities.dp(21) + textLayout.getHeight());
                    textY = (textLayout.getHeight() + AndroidUtilities.dp(21) - textLayout.getHeight()) / 2;
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            }
            setMeasuredDimension(width, height + 1);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (textLayout != null && handleDrawingTextTouch(event, textLayout, textX, textY)) {
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            canvas.save();
            int top = (getMeasuredHeight() - AndroidUtilities.dp(13) - 1) / 2;
            canvas.translate(AndroidUtilities.dp(18), top);
            arrowDrawable.draw(canvas);
            canvas.restore();
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                textLayout.draw(canvas);
                canvas.restore();
            }
            int y = getMeasuredHeight() - 1;
            canvas.drawLine(0, y, getMeasuredWidth(), y, dividerPaint);
        }

        private void toggle() {
            if (currentBlock == null || currentBlock.blocks.isEmpty()) {
                return;
            }
            int position = adapterPositionForDetailsRow();
            int oldCount = localBlocks.size();
            currentBlock.open = !currentBlock.open;
            updateRows();
            int newCount = localBlocks.size();
            int changeCount = Math.abs(newCount - oldCount);
            resetCachedHeights();
            arrowDrawable.setAnimationProgressAnimated(currentBlock.open ? 0f : 1f);
            invalidate();
            if (instantAdapter != null) {
                if (position >= 0 && changeCount != 0) {
                    if (currentBlock.open) {
                        instantAdapter.notifyItemRangeInserted(position + 1, changeCount);
                    } else {
                        instantAdapter.notifyItemRangeRemoved(position + 1, changeCount);
                    }
                } else {
                    instantAdapter.notifyDataSetChanged();
                }
            }
            updateEmptyState();
            updateReadingProgress();
        }

        private int adapterPositionForDetailsRow() {
            if (recyclerView != null) {
                View row = this;
                while (true) {
                    if (row.getParent() == recyclerView) {
                        int position = recyclerView.getChildAdapterPosition(row);
                        if (position != RecyclerView.NO_POSITION) {
                            return position;
                        }
                        break;
                    }
                    if (!(row.getParent() instanceof View)) {
                        break;
                    }
                    row = (View) row.getParent();
                }
            }
            int position = localBlocks.indexOf(currentBlock);
            if (position < 0) {
                position = localBlocks.indexOf(unwrapRenderableBlock(currentBlock));
            }
            return halfSheetPadding && position >= 0 ? position + 1 : position;
        }

        private void updateExpandedState() {
            boolean hasBody = currentBlock != null && !currentBlock.blocks.isEmpty();
            setClickable(hasBody);
            float target = currentBlock != null && currentBlock.open ? 0f : 1f;
            arrowDrawable.setAnimationProgress(target);
            invalidate();
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidate();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        }
    }

    private static final class DetailsBottomCell extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DetailsBottomCell(Context context) {
            super(context);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .22f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1 + AndroidUtilities.dp(4));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(0, 0, getMeasuredWidth(), 0, paint);
        }
    }

    private static final class AnimatedArrowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float animProgress;
        private float animateToProgress;
        private long lastUpdateTime;
        private final boolean small;

        AnimatedArrowDrawable(int color, boolean small) {
            this.small = small;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            updatePath();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(path, paint);
            checkAnimation();
        }

        void setAnimationProgress(float progress) {
            animProgress = progress;
            animateToProgress = progress;
            updatePath();
            invalidateSelf();
        }

        void setAnimationProgressAnimated(float progress) {
            if (animateToProgress == progress) {
                return;
            }
            animateToProgress = progress;
            lastUpdateTime = SystemClock.elapsedRealtime();
            invalidateSelf();
        }

        private void checkAnimation() {
            if (animateToProgress == animProgress) {
                return;
            }
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            if (animProgress < animateToProgress) {
                animProgress += dt / 180.0f;
                if (animProgress > animateToProgress) {
                    animProgress = animateToProgress;
                }
            } else {
                animProgress -= dt / 180.0f;
                if (animProgress < animateToProgress) {
                    animProgress = animateToProgress;
                }
            }
            updatePath();
            invalidateSelf();
        }

        private void updatePath() {
            path.reset();
            float p = animProgress * 2f - 1f;
            if (small) {
                path.moveTo(AndroidUtilities.dp(3), AndroidUtilities.dp(6) - AndroidUtilities.dp(2) * p);
                path.lineTo(AndroidUtilities.dp(8), AndroidUtilities.dp(6) + AndroidUtilities.dp(2) * p);
                path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(6) - AndroidUtilities.dp(2) * p);
            } else {
                path.moveTo(AndroidUtilities.dp(4.5f), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
                path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(12) + AndroidUtilities.dp(4) * p);
                path.lineTo(AndroidUtilities.dp(21.5f), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(26);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(26);
        }
    }

    private TextPaint detailsTextPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(AndroidUtilities.dpf2(scaledSp(15)));
        return paint;
    }
}
