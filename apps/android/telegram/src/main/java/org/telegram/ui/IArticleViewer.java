/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Zark X, 2013-2018.
 *
 * NativeDo: thin vendored extract of Telegram 12.8.1 IArticleViewer.
 * API surface only — fields stay on ArticleViewer until a later field-move PR.
 * openPhoto uses TLRPC.PageBlock (S1), not TL_iv.PageBlock.
 */

package org.telegram.ui;

import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.TextPaintUrlSpan;

/**
 * Shared host surface for ArticleViewer (and future block helpers that take a parent viewer).
 * <p>
 * Upstream (12.8.1) also parks link/search/video fields on this base so DrawingText /
 * Block*Cell can take {@code IArticleViewer parent}. We introduce the type + abstract
 * methods first; field relocation is a separate Phase B2 change.
 */
public abstract class IArticleViewer {

    public abstract int getCurrentAccount();

    public abstract int getTextColor();

    public abstract int getLinkTextColor();

    public abstract int getGrayTextColor();

    public abstract int getThemedColor(int colorKey);

    public abstract Theme.ResourcesProvider getResourcesProvider();

    /**
     * Upstream returns a paint-cache {@link ArticleViewer.Resources}. Local paints still
     * live as statics on ArticleViewer; subclasses may return null until B2.
     */
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

    public void openWebpageUrl(String url, String anchor, Browser.Progress progress) {
    }

    public void checkLayoutForLinks(MotionEvent event, View parentView) {
    }

    public boolean scrollToAnchor(String anchor, boolean animated) {
        return false;
    }

    public void handleLinkClick(ArticleViewer.WebpageAdapter adapter, TextPaintUrlSpan span) {
    }

    public void reset() {
    }

    public ArticleViewer.WebpageAdapter getAdapter() {
        return null;
    }

    /** S1: TLRPC page blocks (not TL_iv). */
    public boolean openPhoto(TLRPC.PageBlock block, ArticleViewer.WebpageAdapter adapter) {
        return false;
    }
}
