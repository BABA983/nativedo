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
 * Kept upstream-shaped because the toolbar and the list were built against these numbers, and the
 * page keeps the pin's own layout formulas (`ChatAttachAlertRichLayout.java` at the pin):
 * `updateToolbarTopOffset` rides the back/history row above the first visible row but never higher
 * than the pin's `minTop` (`topInset + (ActionBarHeight - dp(44)) / 2 - dp(8)`); the list's padding
 * is the pin's `onPreMeasure` - `dp(52)` while the keyboard or an emoji panel owns the bottom, two
 * fifths of the available height (a third and a half in landscape) `dp(52)` above otherwise, plus
 * the page's top inset and the action bar's height, and `bottomInset + dp(TOOLBAR_BOTTOM_INSET + 50)
 * + emojiPadding` below; the bottom panel slides above whatever the sheet reports covers the page's
 * bottom edge; the selection overlay only wins touches between the two toolbar strips; and the
 * formatting/block-type state the toolbar paints is read from the list's own selection helpers.
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
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.nativedo.RichMediaUploadBridge;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AiButtonDrawable;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

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
    /** The pin's `keyboardVisible`: the IME covers the page's bottom (its inset over `dp(20)`). */
    private boolean keyboardVisible;

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
        // the pin's page (ChatAttachAlertRichLayout.java): no focus highlight on the page itself
        if (Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
        }
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
        disableUnwiredToolbarButtons();
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

    /**
     * The two toolbar buttons this product cannot answer - the pin's AI key (the compose sheet's,
     * next to the block row, and the style editor's, in the format panel) and the emoji key - stay
     * at the pin's geometry (hiding them would move the block row the pin centers) but must not
     * look like live controls: the pin has no "unavailable" state for them, so they are dimmed to
     * the alpha the editor's own disabled buttons use (`RichEditor.Button.setEnabled`) and stop
     * taking touches. The AI compose sheet and the style editor are chat/Bot API surfaces this tree
     * has no host for, and the emoji panel belongs to the composer's input island, not this sheet
     * (`docs/render-model-alignment.md`, "The ui/iv project"). The delegate's `onAi`/`onEmoji`
     * stay as the toolbar's contract, but nothing reaches them.
     */
    private void disableUnwiredToolbarButtons() {
        final View emojiButton = toolbar.getEmojiButton();
        if (emojiButton != null) {
            setUnwired(emojiButton);
        }
        disableAiButtons(toolbar);
    }

    /** Every AI button in the toolbar paints an {@link AiButtonDrawable} (pin's `aiButton`/`aiStyleButton`). */
    private static void disableAiButtons(View view) {
        if (view instanceof ImageView && ((ImageView) view).getDrawable() instanceof AiButtonDrawable) {
            setUnwired(view);
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableAiButtons(group.getChildAt(i));
            }
        }
    }

    private static void setUnwired(View button) {
        button.setOnClickListener(null);
        button.setOnLongClickListener(null);
        button.setClickable(false);
        // the disabled alpha every editor button uses (`RichEditor.Button.setEnabled` animates to
        // it; set immediately so the button is dimmed before its first frame)
        button.setEnabled(false);
        button.setAlpha(0.5f);
    }

    /**
     * The document to edit; the caller parses it (our markdown reader lives in `:app`).
     *
     * <p>Loading replaces whatever the page held. The page is cleared *without* the empty-article
     * seed - {@link RichEditorListView#clearContent()} leaves a title row and a paragraph behind for
     * a blank editor, and on top of a document those two rows would be a permanent extra pair of
     * lines. A document that turns out to hold nothing is still given the editor's editable seed.
     * Media blocks that carry only a URL (a post loaded from the site) are imported first, so the
     * editor holds them like any other media instead of dropping them.</p>
     */
    public void loadRichMessage(TL_iv.RichMessage message) {
        if (message == null) message = new TL_iv.RichMessage();
        importRemoteMedia(message);
        listView.clearContent();
        listView.rows.clear();
        listView.loadRichMessage(message);
        // `flattenRowsToBlocks` drops the empty text blocks, so an empty result means the document
        // held nothing to show; anything else (a divider, a media block, a table) counts as content
        // and must not be replaced by the seed.
        if (listView.flattenRowsToBlocks().isEmpty()) {
            listView.seedEmptyArticle();
        }
        listView.resetHistoryBaseline();
        updateHistoryButtons();
        updateToolbarBlockType();
        updateSendButton();
        post(this::updateToolbarTopOffset);
    }

    /**
     * NativeDo import: the markdown reader turns a site image/video/audio/document into a block
     * that carries only a {@code url}, while the editor's resolve path only holds media whose
     * {@code TLRPC} object is ready. The URL is registered with {@link RichMediaUploadBridge} - the
     * same id → URL seam the uploader uses, run backwards - and the object the editor stores is
     * fabricated around it, so the block survives the page's collect/send path and renders. The walk
     * covers the block containers a document nests media in (galleries, details, quotes, list
     * items).
     */
    private void importRemoteMedia(TL_iv.RichMessage message) {
        if (message == null || message.blocks == null) return;
        if (message.photos == null) message.photos = new java.util.ArrayList<>();
        if (message.documents == null) message.documents = new java.util.ArrayList<>();
        importRemoteMedia(message, message.blocks);
    }

    private void importRemoteMedia(TL_iv.RichMessage message, List<TL_iv.PageBlock> blocks) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            if (block == null) continue;
            if (block instanceof TL_iv.pageBlockPhoto) {
                final TL_iv.pageBlockPhoto photo = (TL_iv.pageBlockPhoto) block;
                if (photo.photo_id == 0 && !TextUtils.isEmpty(photo.url)) {
                    photo.photo_id = RichMediaUploadBridge.registerUrl(photo.url);
                    message.photos.add(RichMediaUploadBridge.fabricatePhoto(photo.photo_id, 0, 0));
                }
            } else if (block instanceof TL_iv.pageBlockVideo) {
                final TL_iv.pageBlockVideo video = (TL_iv.pageBlockVideo) block;
                if (video.video_id == 0 && !TextUtils.isEmpty(video.url)) {
                    video.video_id = RichMediaUploadBridge.registerUrl(video.url);
                    message.documents.add(RichMediaUploadBridge.fabricateDocument(video.video_id, video.url, null, true, false, 0, 0, 0));
                }
            } else if (block instanceof TL_iv.pageBlockAudio) {
                final TL_iv.pageBlockAudio audio = (TL_iv.pageBlockAudio) block;
                if (audio.audio_id == 0 && !TextUtils.isEmpty(audio.url)) {
                    audio.audio_id = RichMediaUploadBridge.registerUrl(audio.url);
                    message.documents.add(RichMediaUploadBridge.fabricateDocument(audio.audio_id, audio.url, null, false, true, 0, 0, 0));
                }
            } else if (block instanceof TL_iv.pageBlockDocument) {
                final TL_iv.pageBlockDocument document = (TL_iv.pageBlockDocument) block;
                if (document.document_id == 0 && !TextUtils.isEmpty(document.url)) {
                    document.document_id = RichMediaUploadBridge.registerUrl(document.url);
                    message.documents.add(RichMediaUploadBridge.fabricateDocument(document.document_id, document.url, null, false, false, 0, 0, 0));
                }
            } else if (RichEditorListView.isGallery(block)) {
                importRemoteMedia(message, RichEditorListView.galleryItems(block));
            } else {
                if (block.blocks != null && !block.blocks.isEmpty()) {
                    importRemoteMedia(message, block.blocks);
                }
                if (block instanceof TL_iv.pageBlockList) {
                    for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) block).items) {
                        if (item != null) importRemoteMedia(message, item.blocks);
                    }
                } else if (block instanceof TL_iv.pageBlockOrderedList) {
                    for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) block).items) {
                        if (item != null) importRemoteMedia(message, item.blocks);
                    }
                }
            }
        }
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
        requestLayout();
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
        requestLayout();
    }

    /**
     * The keyboard's own overlap with the page, the pin's `keyboardVisible` seam (the attach sheet's
     * `sizeNotifierFrameLayout.measureKeyboardHeight() > dp(20)`): the sheet reads the IME inset off
     * the window's decor and reports it here, and the page walks its top padding back to `dp(52)`
     * while the IME is up.
     */
    public void setImeInset(int px) {
        final boolean visible = px > dp(20);
        if (keyboardVisible == visible) return;
        keyboardVisible = visible;
        requestLayout();
        updateToolbarTopOffset();
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
        // the toolbar's own setSendLoading only invalidates its subclassed button; the ported
        // SendButton draws the pin's loading arc, so the host drives that too
        toolbar.getSendButton().setLoading(loading, ChatActivityEnterView.SendButton.INFINITE_LOADING);
        updateSendButtonEnabled();
    }

    /**
     * The pin's `updateSendButtonEnabled` (ChatAttachAlertRichLayout.java): the send button's
     * enabled state is the editor's own limit check and nothing else. An empty document is within
     * the limits, so it stays enabled there and the click's own guard ({@link #canSend()}) is what
     * keeps an empty page from actually sending.
     */
    private void updateSendButtonEnabled() {
        if (toolbar != null) toolbar.setSendEnabled(listView.isWithinLimits());
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

    /**
     * The pin's formula (ChatAttachAlertRichLayout.java): the top row rides above the first visible
     * row, but never higher than the action bar's own centre - the pin's `statusBarHeight` is this
     * page's `topInset`, which the sheet reports from the window's real insets.
     */
    private void updateToolbarTopOffset() {
        if (toolbar == null) return;
        // Telegram hides this toolbar's back button because ChatAttachAlert supplies navigation in
        // its action bar. NativeDo's full-page sheet keeps the button, so place the shared back /
        // history row one toolbar strip above the first editor row; using the pin's raw row top
        // would put our additional back circle directly over a heading. The minTop floor remains
        // the pin's exact formula.
        toolbar.setTopButtonsOffset(Math.max(getToolbarTopControlsMinOffset(), firstItemTopRaw() - dp(8 + 44)));
    }

    /**
     * The pin's `minTop`: `topInset + (ActionBar.getCurrentActionBarHeight() - dp(44)) / 2 - dp(8)`,
     * the floor {@link #updateToolbarTopOffset()} holds the back/history row at.
     */
    public int getToolbarTopControlsMinOffset() {
        return topInset + (ActionBar.getCurrentActionBarHeight() - dp(44)) / 2 - dp(8);
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

    /**
     * The pin slides the toolbar's bottom container above whatever owns the page's bottom edge: an
     * emoji panel when one is open, the navigation bar otherwise (`ChatAttachAlertRichLayout.layoutBottomPanels`).
     * The pin's `bottomNavInset()` is 0 while the keyboard is up, which this page's `bottomInset`
     * already encodes - the sheet reports the IME's own overlap, and 0 once the window was resized
     * for it, so the lift happens exactly once - and the attach sheet's pan translation and its
     * `attachRaise` do not exist here. The bottom gradient follows the emoji panel only, as upstream.
     */
    private void layoutBottomPanels() {
        if (toolbar == null) return;
        final int inset = emojiInset > 0 ? emojiInset : bottomInset;
        toolbar.getBottomContainer().animate().cancel();
        toolbar.getBottomContainer().setTranslationY(-inset);
        toolbar.setBottomGradientTranslationY(emojiInset > 0 ? -emojiInset : 0);
    }

    /**
     * The pin's `onPreMeasure` (ChatAttachAlertRichLayout.java), with this page's seam in the
     * attach sheet's place: while the keyboard or an emoji panel owns the bottom the list starts at
     * `dp(52)`; otherwise its first row rides at two fifths of the available height (a third and a
     * half in landscape) and `dp(52)` above that, clamped at zero - and the page's own top inset
     * plus the action bar's height is added on top (the pin's `statusBarHeight +
     * ActionBar.getCurrentActionBarHeight()`, with the sheet's reported top inset standing in for
     * the status bar). The bottom keeps the pin's `bottomNavInset() + dp(TOOLBAR_BOTTOM_INSET + 50)
     * + attachButtonsExtra + emojiPadding`; this page's `bottomInset` is the sheet's WindowInsets
     * seam where the pin read `navigationBarHeight` (0 while the keyboard is up), and the attach
     * sheet's raise (`attachButtonsExtra`) does not exist here.
     */
    private void applyListPadding(int availableHeight) {
        final int paddingTop;
        if (keyboardVisible || emojiInset > dp(20)) {
            paddingTop = dp(52);
        } else {
            final int fromHeight;
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                fromHeight = (int) (availableHeight / 3.5f);
            } else {
                fromHeight = (availableHeight / 5 * 2);
            }
            paddingTop = Math.max(0, fromHeight - dp(52));
        }
        final int top = paddingTop + topInset + ActionBar.getCurrentActionBarHeight();
        final int bottom = bottomInset + emojiInset + dp(TOOLBAR_BOTTOM_INSET + 50);
        if (appliedPaddingTop != top || appliedPaddingBottom != bottom) {
            appliedPaddingTop = top;
            appliedPaddingBottom = bottom;
            listView.setPaddingWithoutRequestLayout(0, top, 0, bottom);
        }
        updateToolbarTopOffset();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        applyListPadding(View.MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * The pin's page guards (ChatAttachAlertRichLayout.java): the selection overlay only wins a tap
     * in the content area, between the top row's own strip (`dp(8 + 44 + 8)`) and the bottom
     * panel's, so neither toolbar's buttons are eaten by a selection; the emoji-panel branch that
     * closed the popup on an outside tap is dropped with the panel (our emoji panel belongs to the
     * composer), as is the attach sheet's `attachRaise`.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (listView.textSelectionHelper.isInSelectionMode() && listView.textSelectionOverlay.onTouchEvent(ev)) {
            return true;
        }
        final int top = getSelectionTouchBottomBoundary();
        if ((ev.getAction() != MotionEvent.ACTION_DOWN || (ev.getY() > dp(8 + 44 + 8) && ev.getY() < top))
            && listView.textSelectionOverlay.checkOnTap(ev)) {
            ev.setAction(MotionEvent.ACTION_CANCEL);
        }
        if (ev.getY() < top && listView.handleSelectionTouch(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * The pin's bottom boundary of the content area: `getHeight() - emojiPadding - dp(8 + 44 + 8)`
     * (the bottom panel's own strip starts here, so a tap in it is the toolbar's, not the page's).
     * The pin subtracts the attach sheet's `attachRaise` too; this page has no attach sheet.
     */
    public int getSelectionTouchBottomBoundary() {
        return getHeight() - emojiInset - dp(8 + 44 + 8);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (listView.handleKeyEvent(ev)) return true;
        return super.dispatchKeyEvent(ev);
    }
}
