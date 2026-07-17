/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Zark X, 2013-2018.
 *
 * NativeDo: vendored extract of Telegram 12.8.1 IArticleViewer.
 * Phase B1: abstract API. Phase B2: shared fields (link/search/video) live here
 * so block helpers can take IArticleViewer parent. openPhoto uses TLRPC.PageBlock (S1).
 */

package org.telegram.ui;

import android.view.MotionEvent;
import android.view.View;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.video.VideoPlayerHolderBase;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.TextPaintUrlSpan;

import java.util.ArrayList;

/**
 * Shared host surface for ArticleViewer block/chrome helpers.
 * <p>
 * Fields match upstream 12.8.1 so a future DrawingText(IArticleViewer) reshape can
 * reference {@code parent.pressedLink} etc. without casting to ArticleViewer.
 */
public abstract class IArticleViewer {

    public abstract int getCurrentAccount();

    public int selectedFont = 0;

    public abstract int getTextColor();

    public abstract int getLinkTextColor();

    public abstract int getGrayTextColor();

    public abstract int getThemedColor(int colorKey);

    public abstract Theme.ResourcesProvider getResourcesProvider();

    /** Upstream-shaped paint cache; local subclasses expose their own map set here. */
    public abstract ArticleViewer.Resources getResources();

    /** Horizontal content inset in dp (upstream constant 18). */
    public int padx() {
        return 18;
    }

    /** Vertical content inset in dp (upstream constant 8). */
    public int pady() {
        return 8;
    }

    public abstract TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper(View view);

    public boolean allowTouches() {
        return true;
    }

    public boolean canStartSelection(View block) {
        return false;
    }

    public LinkSpanDrawable<TextPaintUrlSpan> pressedLink;
    public LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector();
    public ArticleViewer.DrawingText pressedLinkOwnerLayout;
    public int pressedLayoutY;
    public View pressedLinkOwnerView;

    public void openWebpageUrl(String url, String anchor, Browser.Progress progress) {
    }

    public void checkLayoutForLinks(MotionEvent event, View parentView) {
    }

    public boolean scrollToAnchor(String anchor, boolean animated) {
        return false;
    }

    public boolean drawBlockSelection;

    public void handleLinkClick(ArticleViewer.WebpageAdapter adapter, TextPaintUrlSpan span) {
    }

    public TLRPC.Chat loadedChannel;
    public boolean loadingChannel;
    public View loadingLinkView;
    public TextPaintUrlSpan loadingLink;
    public ArticleViewer.DrawingText loadingText;
    public LoadingDrawable loadingLinkDrawable;

    public VideoPlayerHolderBase videoPlayer;
    public ArticleViewer.BlockVideoCell currentPlayer;
    public LongSparseArray<ArticleViewer.BlockVideoCellState> videoStates = new LongSparseArray<>();

    public void reset() {
        loadedChannel = null;
        loadingChannel = false;
    }

    public ArrayList<ArticleViewer.SearchResult> searchResults = new ArrayList<>();
    public String searchText;
    public int currentSearchIndex;

    public ArticleViewer.WebpageAdapter getAdapter() {
        return null;
    }

    /** S1: TLRPC page blocks (not TL_iv). */
    public boolean openPhoto(TLRPC.PageBlock block, ArticleViewer.WebpageAdapter adapter) {
        return false;
    }

    public ActionBarPopupWindow popupWindow;
    public BottomSheet linkSheet;
}
