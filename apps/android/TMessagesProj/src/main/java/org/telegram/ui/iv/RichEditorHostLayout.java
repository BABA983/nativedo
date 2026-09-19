/*
 * NativeDo: the page that hosts the rich editor - the block list and its toolbar.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Derived from org/telegram/ui/iv/ChatAttachAlertRichLayout.java at the pinned Telegram commit,
 * which is the same page as an `AttachAlertLayout` inside `ChatAttachAlert`. This tree has no
 * attach sheet (the block editor is a page our own sheet mounts, `:app`), so the class is a plain
 * FrameLayout and the pin's chat glue is dropped: the attach sheet's raise and type buttons, the
 * emoji view, the AI compose sheet and its editor, the schedule/notify send preview, the location
 * picker, the premium gates, the chat drafts and the pan transition. What only the page's host
 * (:app) can do - pick a file, open an emoji panel, send, leave - is a Delegate.
 *
 * Kept upstream-shaped because the toolbar and the list were built against these numbers:
 * `updateToolbarTopOffset` rides the toolbar's top row above the first visible row and never
 * higher than the page's top inset; the list is padded `dp(52)` above and `dp(TOOLBAR_BOTTOM_INSET
 * + 50)` below, plus the insets the host reports; the selection overlay owns touches before the
 * list does; and the formatting/block-type state the toolbar paints is read from the list's own
 * selection helpers.
 *
 * NativeDo wire-outs, recorded in docs/render-model-alignment.md: inline-button and block-button
 * editing (Telegram's button rows, which Discourse markdown cannot express), the location picker
 * and the slash commands.
 */
package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

/**
 * The rich editor as a page: {@link RichEditorListView} filling it, {@link RichEditorToolbar}
 * floating on top. The page owns the editor's own behaviour (load, undo, the formatting and
 * block-type state, the selection overlay, the media uploads) and hands the four things it cannot
 * do itself to the {@link Delegate}: leave, pick a file, open an emoji panel, send.
 */
public class RichEditorHostLayout extends FrameLayout {

    public interface Delegate {
        /** The toolbar's back button, or a back press that deselected nothing. */
        void onBackPressed();

        /**
         * A row (or the toolbar's attach button) asked for media. The page's host picks a file and
         * hands it back with {@link #attachMedia} or {@link #attachExternalMedia}.
         */
        void onAttachRequested();

        /** The toolbar's emoji button; a host without an emoji panel can leave this empty. */
        void onEmojiRequested();

        /** The send button, pressed after the empty/upload/limit guards passed. */
        void onSendPressed();
    }

    /** The pin's number: the bottom toolbar's own height (8 + 44 + 8). */
    private static final int TOOLBAR_BOTTOM_INSET = 8 + 44 + 8;

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final RichEditorListView listView;
    private final RichEditorToolbar toolbar;

    private Delegate delegate;
    private ItemOptions menu;

    /** How far the page's own safe area is from its top; the sheet that mounts it sets this. */
    private int topInset = dp(8);
    /** The navigation bar, unless the keyboard is up - then the host reports 0. */
    private int bottomInset;
    /** An emoji panel covering the page's bottom; 0 while it is closed. */
    private int emojiInset;

    private int appliedPaddingTop = -1;
    private int appliedPaddingBottom = -1;

    public RichEditorHostLayout(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        listView = new RichEditorListView(context, currentAccount, resourcesProvider, new RichEditorListView.Delegate() {
            @Override public ItemOptions makeMenu(View anchor) {
                return menu = ItemOptions.makeOptions(RichEditorHostLayout.this, resourcesProvider, anchor, false, false, true);
            }
            @Override public void onSelectionChanged() { updateFormattingPanel(); updateToolbarBlockType(); }
            @Override public void onContentChanged() { updateHistoryButtons(); updateSendButton(); }
            @Override public void onHistoryChanged() { updateHistoryButtons(); updateSendButton(); }
            @Override public void onOpenAttachRequest(int allowedLayouts, int initialLayoutType) {
                if (delegate != null) delegate.onAttachRequested();
            }
            @Override public void onOpenLocationRequest(BlockRow row) {
                // NativeDo wire-out: no location blocks in Discourse markdown.
            }
            @Override public void onSlashSuggest(RichTextCell cell, String query) {
                // NativeDo wire-out: the editor's slash commands are Telegram's; Discourse has none.
            }
            @Override public void onListScrolled(int dy) { updateToolbarTopOffset(); }
            @Override public void onListLayoutUpdated() { updateToolbarTopOffset(); }
            @Override public void makeEditTextFocusable(RichEditText et, boolean showKeyboard) { makeFocusable(et, showKeyboard); }
            @Override public void onInlineButtonEditRequested(RichEditorListView.InlineButtonEdit edit, View anchor) {
                // NativeDo wire-out: Telegram's inline buttons have no markdown form.
            }
            @Override public void onBlockButtonEditRequested(RichEditorListView.BlockButtonEdit edit, View anchor) {
                // NativeDo wire-out: button rows are Telegram's; the markdown writer drops them.
            }
            @Override public void onReorderStart() { if (toolbar != null) toolbar.onReorderStart(); }
            @Override public boolean onReorderMove(float screenX, float screenY) { return toolbar != null && toolbar.onReorderMove(screenX, screenY); }
            @Override public void onReorderEnd() { if (toolbar != null) toolbar.onReorderEnd(); }
        });
        listView.setAdaptiveLinkDialogs(false);
        listView.setAllowTapAboveContent(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        addView(listView.getOverlayView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.seedEmptyArticle();
        listView.resetHistoryBaseline();

        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackground(null);
        setForeground(null);
        setClipChildren(false);

        toolbar = new RichEditorToolbar(context, new RichEditorToolbar.Delegate() {
            @Override public Theme.ResourcesProvider getResourcesProvider() { return resourcesProvider; }
            @Override public void onBack() { if (delegate != null) delegate.onBackPressed(); }
            @Override public void onUndo() { listView.undo(); }
            @Override public void onRedo() { listView.redo(); }
            @Override public void onEmoji() { if (delegate != null) delegate.onEmojiRequested(); }
            @Override public void onAi() {
                // NativeDo wire-out: the AI compose sheet is a chat/Bot API surface we have no host for.
            }
            @Override public void onAttach() { listView.pendingMediaRow = null; if (delegate != null) delegate.onAttachRequested(); }
            @Override public void onSend() {
                if (delegate != null && canSend()) delegate.onSendPressed();
            }
            @Override public boolean onSendLongClick(View anchor) {
                return false; // the schedule/notify preview is a chat send option
            }
            @Override public void onBlockButton(int flag, View anchor) { onBlockButtonClicked(flag, anchor); }
            @Override public void onFormatting(int styleFlag) { listView.onFormattingClicked(styleFlag); }
            @Override public void onLink() { listView.onLinkClicked(); }
            @Override public void onButton(View anchor) { listView.onInlineButtonClicked(anchor); }
            @Override public void onDate() { listView.onDateClicked(); }
            @Override public void onMath() { listView.onMathClicked(); }
            @Override public void onQuote() { listView.toggleQuoteOnSelection(); updateFormattingButtons(); }
            @Override public void onAiStyle() {
                // NativeDo wire-out: the AI style editor needs the same AI host as onAi().
            }
        });
        // The pin's page sits in the attach sheet, whose action bar owns the close button, so it
        // hides the toolbar's back arrow; our page is the whole sheet, so it keeps it.
        toolbar.setBackVisible(true);
        toolbar.setTopGradientVisible(false);
        addView(toolbar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateHistoryButtons();
        updateToolbarBlockType();
        updateSendButton();

        getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> updateToolbarBlockType());
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public RichEditorListView getEditorListView() {
        return listView;
    }

    public RichEditorToolbar getToolbar() {
        return toolbar;
    }

    /** The document to edit; the caller parses it (our markdown reader lives in `:app`). */
    public void loadRichMessage(TL_iv.RichMessage message) {
        if (message == null) message = new TL_iv.RichMessage();
        listView.clearContent();
        listView.loadRichMessage(message);
        listView.resetHistoryBaseline();
        updateHistoryButtons();
        updateToolbarBlockType();
        updateSendButton();
        post(this::updateToolbarTopOffset);
    }

    /** The document as it stands, for the caller's own writer (`:app` writes Discourse markdown). */
    public java.util.ArrayList<TL_iv.PageBlock> collectBlocks() {
        return listView.flattenRowsToBlocks();
    }

    public boolean hasContent() {
        return listView.hasAnyText();
    }

    public boolean canSend() {
        return listView.hasAnyText() && !listView.hasPendingUploads() && listView.isWithinLimits();
    }

    /** True while the page holds edits the caller may want to keep (a draft, a discard prompt). */
    public boolean hasChanges() {
        return listView.canUndo();
    }

    /** A media file the page's host picked, inserted as a new block the editor uploads. */
    public void attachMedia(MediaController.PhotoEntry entry) {
        listView.attachMedia(entry);
    }

    /** A media file the page's host picked as a content URI (the app's file pickers hand out these). */
    public void attachExternalMedia(Uri uri) {
        listView.attachExternalMedia(uri);
    }

    /** The distance from this view's top to its safe area (a status bar, a sheet's handle). */
    public void setTopInset(int px) {
        if (topInset == px) return;
        topInset = px;
        updateListPadding();
        updateToolbarTopOffset();
    }

    /**
     * The bottom insets the page must lay its toolbar above: the navigation bar (0 while the
     * keyboard covers it) and an emoji panel that covers the page's bottom (0 while closed).
     */
    public void setBottomInsets(int navInset, int emojiInset) {
        if (bottomInset == navInset && this.emojiInset == emojiInset) return;
        bottomInset = navInset;
        this.emojiInset = emojiInset;
        layoutBottomPanels();
        updateListPadding();
    }

    /** A back press: true when the page consumed it (a selection was dropped). */
    public boolean onBackPressed() {
        if (listView.deselectIfAny()) {
            return true;
        }
        if (delegate != null) delegate.onBackPressed();
        return true;
    }

    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    public void destroy() {
        if (menu != null) {
            menu.dismiss();
            menu = null;
        }
        listView.destroy();
    }

    private void makeFocusable(RichEditText et, boolean showKeyboard) {
        if (et == null) return;
        et.requestFocus();
        if (!showKeyboard) return;
        et.post(() -> {
            final InputMethodManager imm = (InputMethodManager) et.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void updateHistoryButtons() {
        if (toolbar != null) {
            toolbar.setHistoryEnabled(listView.canUndo(), listView.canRedo());
        }
    }

    private void updateSendButton() {
        if (toolbar == null) return;
        final boolean loading = listView.hasPendingUploads();
        toolbar.setSendLoading(loading);
        toolbar.setSendEnabled(listView.hasAnyText() && !loading && listView.isWithinLimits());
    }

    private void onBlockButtonClicked(int flag, View v) {
        final BlockRow row = listView.findFocusedRow();
        switch (flag) {
            case RichEditorToolbar.BLOCK_TEXT:
                showTextTypeMenu(row, v);
                break;
            case RichEditorToolbar.BLOCK_LIST:
                showListMenu(row, v);
                break;
            case RichEditorToolbar.BLOCK_TABLE:
                RichTableCell table = listView.activeCellSelectionTable;
                if (table == null) {
                    final RichTableCell focused = listView.findFocusedTableCell();
                    if (focused != null && focused.getModel() != null) {
                        final TL_iv.pageTableCell cur = listView.focusedCellOf(focused);
                        if (cur != null) {
                            listView.enterCellSelectionMode(focused, cur);
                            table = focused;
                        }
                    }
                }
                if (table != null && table.getModel() != null && table.hasCellSelection()) {
                    listView.showTableCellMenu(table);
                } else {
                    listView.addBlock(RichTextCell.newEmptyTable(2, 2));
                }
                break;
            case RichEditorToolbar.BLOCK_MATH: {
                final TL_iv.pageBlockMath math = row != null && row.block instanceof TL_iv.pageBlockMath ? (TL_iv.pageBlockMath) row.block : null;
                ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), math == null || TextUtils.isEmpty(math.source) ? "" : math.source, source -> {
                    if (math != null) {
                        math.source = source;
                        listView.adapter.update(false);
                    } else {
                        final TL_iv.pageBlockMath newMath = new TL_iv.pageBlockMath();
                        newMath.source = source;
                        listView.addBlock(newMath);
                    }
                }, resourcesProvider);
                break;
            }
            case RichEditorToolbar.BLOCK_DETAILS:
                listView.insertDetails();
                break;
        }
    }

    private void showTextTypeMenu(BlockRow row, View v) {
        if (menu != null) {
            menu.dismiss();
        }
        final ItemOptions o = ItemOptions.makeOptions(this, resourcesProvider, v, true).dontFocus();
        final ItemOptions headers = o.makeSwipeback();

        headers.add(R.drawable.ic_ab_back, getString(R.string.Back), () -> o.closeSwipeback());
        headers.addGap();
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading1(), R.drawable.iv_h1, getString(R.string.ArticleHeading1), SharedConfig.fontSize + 2, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading2(), R.drawable.iv_h2, getString(R.string.ArticleHeading2), SharedConfig.fontSize + 1, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading3(), R.drawable.iv_h3, getString(R.string.ArticleHeading3), SharedConfig.fontSize, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading4(), R.drawable.iv_h4, getString(R.string.ArticleHeading4), SharedConfig.fontSize - 1, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading5(), R.drawable.iv_h5, getString(R.string.ArticleHeading5), SharedConfig.fontSize - 2, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading6(), R.drawable.iv_h6, getString(R.string.ArticleHeading6), SharedConfig.fontSize - 3, o);

        o.addChecked(row != null && RichEditorListView.isHeading(row.block), R.drawable.iv_h1, getString(R.string.ArticleHeading), () -> o.openSwipeback(headers));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockParagraph, R.drawable.iv_text, getString(R.string.ArticleText), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockParagraph()));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockBlockquote, R.drawable.iv_quote, getString(R.string.ArticleQuote), () -> listView.turnInto(row, RichEditorListView.newBlockquote(), 0, 0, false, false));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPullquote, R.drawable.iv_pullquote, getString(R.string.ArticlePullquote), () -> listView.turnInto(row, RichEditorListView.newPullquote(), 0, 0, false, false));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPreformatted, R.drawable.iv_code, getString(R.string.ArticleCode), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockPreformatted()));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockFooter, R.drawable.iv_footer, getString(R.string.ArticleFooter), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockFooter()));
        menu = o.show();
    }

    private void addHeadingItem(ItemOptions headers, BlockRow row, TL_iv.PageBlock block, int icon, String label, int textSize, ItemOptions root) {
        headers.addChecked(row != null && row.block.getClass() == block.getClass(), icon, label, () -> { listView.turnIntoKeepList(row, block); root.dismiss(); });
        headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    }

    private void showListMenu(BlockRow row, View v) {
        if (menu != null) {
            menu.dismiss();
        }
        final ItemOptions o = ItemOptions.makeOptions(this, resourcesProvider, v).dontFocus();
        o
            .addChecked(row == null || !row.isInList(), R.drawable.field_carret_empty, getString(R.string.ArticleNone), () -> listView.turnIntoList(row, 0))
            .addChecked(row != null && row.isInList() && !row.isChecklist() && !row.isOrdered(), R.drawable.iv_list, getString(R.string.ArticleListBulleted), () -> listView.turnIntoList(row, 1))
            .addChecked(row != null && row.isInList() && !row.isChecklist() && row.isOrdered(), R.drawable.iv_ordered_list, getString(R.string.ArticleListNumbered), () -> listView.turnIntoList(row, 2))
            .addChecked(row != null && row.isInList() && row.isChecklist() && !row.isOrdered(), R.drawable.iv_todo, getString(R.string.ArticleListTodo), () -> listView.turnIntoList(row, 3))
            .addChecked(row != null && row.block instanceof TL_iv.pageBlockDetails, R.drawable.iv_details, getString(R.string.ArticleToggleBlock), listView::insertDetails);
        final boolean canIndent = listView.canIndentSelection();
        final boolean canOutdent = listView.canOutdentSelection();
        if (canIndent || canOutdent) {
            o.addGap();
            if (canIndent) o.add(R.drawable.iv_list_tab, getString(R.string.ArticleIndent), () -> { listView.indentSelection(false); o.dismiss(); });
            if (canOutdent) o.add(R.drawable.iv_list_untab, getString(R.string.ArticleOutdent), () -> { listView.indentSelection(true); o.dismiss(); });
        }
        menu = o.forceTop(true).show();
    }

    private void updateFormattingPanel() {
        if (toolbar == null) return;
        final boolean show = listView.isInSelectionMode() && listView.selectionHasInlineFormattable();
        toolbar.showFormattingPanel(show, true);
        if (show) updateFormattingButtons();
    }

    private void updateFormattingButtons() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (toolbar == null || textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return;
        toolbar.setQuoteState(listView.isSelectionQuoted());
        if (listView.isTableSelection()) { updateFormattingButtonsTable(); return; }
        if (listView.isCaptionSelection()) { updateFormattingButtonsCaption(); return; }
        final int sCell = textSelectionHelper.getStartCell();
        final int eCell = textSelectionHelper.getEndCell();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final boolean valid = sCell >= 0 && eCell >= 0 && eCell >= sCell && eCell < listView.itemRows.size();
        int mask = 0;
        if (valid) {
            for (final int flag : STYLE_FLAGS) {
                if (listView.isStyleFullyApplied(flag, sCell, sOff, eCell, eOff)) mask |= flag;
            }
        }
        toolbar.setFormattingState(mask,
            valid && listView.isLinkApplied(sCell, sOff, eCell, eOff),
            valid && listView.isDateApplied(sCell, sOff, eCell, eOff),
            valid && sCell == eCell,
            listView.canCreateInlineButtonOnSelection(),
            !listView.isSelectionAllHeadings());
    }

    private void updateFormattingButtonsTable() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        int mask = 0;
        for (final int flag : STYLE_FLAGS) {
            if (listView.isStyleFullyAppliedTable(flag, pos, sChild, sOff, eChild, eOff)) mask |= flag;
        }
        final boolean single = sChild == eChild;
        final RichEditText et = single ? listView.tableEditText(pos, sChild) : null;
        final int from = Math.max(0, Math.min(sOff, eOff));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        toolbar.setFormattingState(mask,
            et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to),
            et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to),
            single,
            listView.canCreateInlineButtonOnSelection(),
            true);
    }

    private void updateFormattingButtonsCaption() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = listView.captionEditText(pos);
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final int from = et == null ? 0 : Math.max(0, Math.min(Math.min(sOff, eOff), et.length()));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        int mask = 0;
        if (et != null && from < to) {
            for (final int flag : STYLE_FLAGS) {
                if ((et.getCurrentStyle(from, to) & flag) != 0) mask |= flag;
            }
        }
        toolbar.setFormattingState(mask,
            et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to),
            et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to),
            true,
            listView.canCreateInlineButtonOnSelection(),
            true);
    }

    private static final int[] STYLE_FLAGS = {
        RichTextStyle.BOLD, RichTextStyle.ITALIC, RichTextStyle.UNDERLINE, RichTextStyle.STRIKE,
        RichTextStyle.SPOILER, RichTextStyle.MONO, RichTextStyle.SUBSCRIPT, RichTextStyle.SUPERSCRIPT
    };

    private void updateToolbarBlockType() {
        if (toolbar == null) return;
        final BlockRow row;
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            final int sCell = textSelectionHelper.getStartCell();
            final int eCell = textSelectionHelper.getEndCell();
            row = sCell == eCell ? listView.rowForCell(sCell) : null;
        } else {
            row = listView.findFocusedRow();
        }
        int type;
        if (listView.findFocusedTableCell() != null) {
            type = RichEditorToolbar.BLOCK_TABLE;
        } else if (row == null) {
            type = 0;
        } else if (row.isChecklist() || row.isInList() || row.isOrdered() || row.block instanceof TL_iv.pageBlockDetails) {
            type = RichEditorToolbar.BLOCK_LIST;
        } else if (row.block instanceof TL_iv.pageBlockTable) {
            type = RichEditorToolbar.BLOCK_TABLE;
        } else if (row.block instanceof TL_iv.pageBlockMath) {
            type = RichEditorToolbar.BLOCK_MATH;
        } else if (RichEditorListView.isHeading(row.block)
            || row.block instanceof TL_iv.pageBlockParagraph
            || row.block instanceof TL_iv.pageBlockPreformatted
            || row.block instanceof TL_iv.pageBlockBlockquote
            || row.block instanceof TL_iv.pageBlockPullquote) {
            type = RichEditorToolbar.BLOCK_TEXT;
        } else {
            type = 0;
        }
        int icon = 0;
        if (row != null) {
            if (type == RichEditorToolbar.BLOCK_TEXT) {
                if (row.block instanceof TL_iv.pageBlockHeading1) icon = R.drawable.iv_h1;
                else if (row.block instanceof TL_iv.pageBlockHeading2) icon = R.drawable.iv_h2;
                else if (row.block instanceof TL_iv.pageBlockHeading3) icon = R.drawable.iv_h3;
                else if (row.block instanceof TL_iv.pageBlockHeading4) icon = R.drawable.iv_h4;
                else if (row.block instanceof TL_iv.pageBlockHeading5) icon = R.drawable.iv_h5;
                else if (row.block instanceof TL_iv.pageBlockHeading6) icon = R.drawable.iv_h6;
                else if (row.block instanceof TL_iv.pageBlockPreformatted) icon = R.drawable.iv_code;
                else if (row.block instanceof TL_iv.pageBlockBlockquote) icon = R.drawable.iv_quote;
                else if (row.block instanceof TL_iv.pageBlockPullquote) icon = R.drawable.iv_pullquote;
                else if (row.block instanceof TL_iv.pageBlockFooter) icon = R.drawable.iv_footer;
            } else if (type == RichEditorToolbar.BLOCK_LIST) {
                if (row.isChecklist()) icon = R.drawable.iv_todo;
                else if (row.isOrdered()) icon = R.drawable.iv_ordered_list;
            }
        }
        toolbar.setSelectedBlockType(type, icon);
    }

    private void updateToolbarTopOffset() {
        if (toolbar == null) return;
        toolbar.setTopButtonsOffset(Math.max(topInset + dp(8), firstItemTopRaw()));
    }

    private int firstItemTopRaw() {
        if (listView.getChildCount() <= 0) return listView.getPaddingTop();
        int top = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            final View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) >= 0 && child.getTop() < top) {
                top = child.getTop();
            }
        }
        return top == Integer.MAX_VALUE ? listView.getPaddingTop() : top;
    }

    /** The bottom toolbar slides above the navigation bar or an emoji panel that covers it. */
    private void layoutBottomPanels() {
        if (toolbar == null) return;
        final int inset = emojiInset > 0 ? emojiInset : bottomInset;
        toolbar.getBottomContainer().animate().cancel();
        toolbar.getBottomContainer().setTranslationY(-inset);
        toolbar.setBottomGradientTranslationY(emojiInset > 0 ? -emojiInset : 0);
    }

    private void updateListPadding() {
        final int top = topInset + dp(52);
        final int bottom = bottomInset + emojiInset + dp(TOOLBAR_BOTTOM_INSET + 50);
        if (appliedPaddingTop == top && appliedPaddingBottom == bottom) return;
        appliedPaddingTop = top;
        appliedPaddingBottom = bottom;
        listView.setPaddingWithoutRequestLayout(0, top, 0, bottom);
        updateToolbarTopOffset();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateListPadding();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (listView.textSelectionHelper.isInSelectionMode() && listView.textSelectionOverlay.onTouchEvent(ev)) {
            return true;
        }
        if (ev.getAction() != MotionEvent.ACTION_DOWN && listView.textSelectionOverlay.checkOnTap(ev)) {
            ev.setAction(MotionEvent.ACTION_CANCEL);
        }
        if (listView.handleSelectionTouch(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (listView.handleKeyEvent(ev)) return true;
        return super.dispatchKeyEvent(ev);
    }
}
