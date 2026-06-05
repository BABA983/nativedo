/*
 * This file is part of Telegram for Android.
 *
 * Copyright (C) 2013-2026 Telegram FZ-LLC
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.style.ReplacementSpan;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

public class TextSelectionHelper {
    public interface TextLayoutBlock {
        StaticLayout getLayout();
        int getX();
        int getY();
        int getRow();
        CharSequence getPrefix();
    }

    public interface ArticleSelectableView {
        void fillTextLayoutBlocks(ArrayList<TextLayoutBlock> blocks);
    }

    public interface Callback {
        default void onStateChanged(boolean isSelected) {}
        default void onTextCopied() {}
    }

    public interface TranslateCallback {
        void run(CharSequence text, String fromLang, String toLang, Runnable onAlertDismiss);
    }

    public static class IgnoreCopySpannable extends ReplacementSpan {
        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint paint) {}
    }

    public static class TextSelectionOverlay extends View {
        private ArticleTextSelectionHelper helper;

        public TextSelectionOverlay(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        private void setHelper(ArticleTextSelectionHelper helper) {
            this.helper = helper;
        }

        public boolean checkOnTap(MotionEvent event) {
            if (helper != null && helper.isInSelectionMode() && event.getActionMasked() == MotionEvent.ACTION_UP) {
                helper.clear();
                return true;
            }
            return false;
        }
    }

    public static class ArticleTextSelectionHelper {
        public final ArrayList<TextLayoutBlock> arrayList = new ArrayList<>();
        public LinearLayoutManager layoutManager;
        private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path selectionPath = new Path();
        private TextSelectionOverlay overlay;
        private ViewGroup parentView;
        private Callback callback;
        private ArticleSelectableView selectedView;
        private int selectedIndex = -1;
        private int selectionStart = -1;
        private int selectionEnd = -1;
        private int topOffset;
        private int maybeX;
        private int maybeY;
        private View maybeView;
        private ActionMode actionMode;
        private boolean finishingActionMode;

        public ArticleTextSelectionHelper() {
            selectionPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
        }

        public void setParentView(ViewGroup parentView) {
            this.parentView = parentView;
        }

        public void setCallback(Callback callback) {
            this.callback = callback;
        }

        public void setOnTranslate(TranslateCallback callback) {}

        public TextSelectionOverlay getOverlayView(Context context) {
            if (overlay == null) {
                overlay = new TextSelectionOverlay(context);
                overlay.setHelper(this);
            }
            return overlay;
        }

        public boolean isInSelectionMode() {
            return selectedView != null && selectedIndex >= 0 && selectionStart >= 0 && selectionEnd >= 0;
        }

        public boolean isSelectable(View view) {
            if (!(view instanceof ArticleSelectableView)) {
                return false;
            }
            arrayList.clear();
            ((ArticleSelectableView) view).fillTextLayoutBlocks(arrayList);
            return !arrayList.isEmpty();
        }

        public void trySelect(View view) {
            if (!isSelectable(view)) {
                return;
            }
            ArticleSelectableView selectableView = (ArticleSelectableView) view;
            TextLayoutBlock block = findSelectableBlock(view, arrayList);
            if (block == null) {
                return;
            }
            int offset = charOffsetForTouch(block);
            WordBounds bounds = wordBounds(block.getLayout().getText(), offset);
            if (bounds == null) {
                return;
            }
            selectedView = selectableView;
            selectedIndex = arrayList.indexOf(block);
            selectionStart = bounds.start;
            selectionEnd = bounds.end;
            View viewObject = (View) view;
            viewObject.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            viewObject.invalidate();
            invalidateOverlay();
            showActionMode(viewObject);
            if (callback != null) {
                callback.onStateChanged(true);
            }
        }

        public void clear() {
            clear(false);
        }

        public void clear(boolean animated) {
            ArticleSelectableView oldView = selectedView;
            selectedView = null;
            selectedIndex = -1;
            selectionStart = -1;
            selectionEnd = -1;
            if (actionMode != null && !finishingActionMode) {
                finishingActionMode = true;
                actionMode.finish();
                finishingActionMode = false;
            }
            actionMode = null;
            if (oldView instanceof View) {
                ((View) oldView).invalidate();
            }
            invalidateOverlay();
            if (callback != null) {
                callback.onStateChanged(false);
            }
        }

        public void setMaybeView(int x, int y, View view) {
            maybeX = x;
            maybeY = y;
            maybeView = view;
        }
        public void setTopOffset(int topOffset) { this.topOffset = topOffset; }
        public void invalidate() { invalidateSelectedView(); invalidateOverlay(); }
        public void stopScrolling() {}
        public void onParentScrolled() { invalidate(); }

        public void draw(Canvas canvas, ArticleSelectableView view, int index) {
            if (!isInSelectionMode() || view != selectedView) {
                return;
            }
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            if (selectedIndex < 0 || selectedIndex >= arrayList.size()) {
                return;
            }
            TextLayoutBlock block = arrayList.get(selectedIndex);
            StaticLayout layout = block.getLayout();
            CharSequence text = layout.getText();
            if (text.length() == 0) {
                return;
            }
            int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
            int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
            if (start >= end) {
                return;
            }
            selectionPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
            selectionPath.reset();
            layout.getSelectionPath(start, end, selectionPath);
            canvas.save();
            canvas.translate(block.getX(), block.getY() - topOffset);
            canvas.drawPath(selectionPath, selectionPaint);
            canvas.restore();
        }

        private TextLayoutBlock findSelectableBlock(View view, ArrayList<TextLayoutBlock> blocks) {
            if (maybeView == view) {
                for (TextLayoutBlock block : blocks) {
                    StaticLayout layout = block.getLayout();
                    if (layout == null || layout.getText().length() == 0) {
                        continue;
                    }
                    int x = maybeX - block.getX();
                    int y = maybeY - block.getY() + topOffset;
                    if (y < 0 || y > layout.getHeight()) {
                        continue;
                    }
                    int line = layout.getLineForVertical(y);
                    if (x >= layout.getLineLeft(line) && x <= layout.getLineRight(line)) {
                        return block;
                    }
                }
                return null;
            }
            for (TextLayoutBlock block : blocks) {
                if (block.getLayout() != null && block.getLayout().getText().length() > 0) {
                    return block;
                }
            }
            return null;
        }

        private int charOffsetForTouch(TextLayoutBlock block) {
            StaticLayout layout = block.getLayout();
            int x = clamp(maybeX - block.getX(), 0, layout.getWidth());
            int y = clamp(maybeY - block.getY() + topOffset, 0, Math.max(0, layout.getHeight() - 1));
            int line = layout.getLineForVertical(y);
            return clamp(layout.getOffsetForHorizontal(line, x), 0, layout.getText().length());
        }

        private WordBounds wordBounds(CharSequence text, int offset) {
            int length = text.length();
            if (length == 0) {
                return null;
            }
            int safeOffset = clamp(offset, 0, length - 1);
            if (Character.isWhitespace(text.charAt(safeOffset)) && safeOffset > 0) {
                safeOffset--;
            }
            BreakIterator iterator = BreakIterator.getWordInstance(Locale.getDefault());
            iterator.setText(text.toString());
            int start = iterator.preceding(safeOffset + 1);
            int end = iterator.following(safeOffset);
            if (start != BreakIterator.DONE && end != BreakIterator.DONE) {
                start = trimStart(text, start, end);
                end = trimEnd(text, start, end);
                if (start < end) {
                    return new WordBounds(start, end);
                }
            }
            int startFallback = safeOffset;
            int endFallback = safeOffset + 1;
            while (startFallback > 0 && sameWordClass(text.charAt(startFallback - 1), text.charAt(safeOffset))) {
                startFallback--;
            }
            while (endFallback < length && sameWordClass(text.charAt(endFallback), text.charAt(safeOffset))) {
                endFallback++;
            }
            return startFallback < endFallback ? new WordBounds(startFallback, endFallback) : null;
        }

        private int trimStart(CharSequence text, int start, int end) {
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            return start;
        }

        private int trimEnd(CharSequence text, int start, int end) {
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            return end;
        }

        private boolean sameWordClass(char value, char reference) {
            if (Character.isLetterOrDigit(reference)) {
                return Character.isLetterOrDigit(value);
            }
            return !Character.isWhitespace(value) && !Character.isLetterOrDigit(value);
        }

        private void showActionMode(View view) {
            if (actionMode != null) {
                actionMode.invalidate();
                return;
            }
            ActionMode.Callback callback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return createActionMenu(menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return handleActionItem(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    destroyActionMode();
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    fillSelectionContentRect(outRect);
                }
            } : new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return createActionMenu(menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return handleActionItem(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    destroyActionMode();
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING);
            } else {
                actionMode = view.startActionMode(callback);
            }
        }

        private boolean createActionMenu(Menu menu) {
            menu.add(Menu.NONE, android.R.id.copy, 0, "复制").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, android.R.id.selectAll, 1, "全选").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            return true;
        }

        private boolean handleActionItem(ActionMode mode, MenuItem item) {
            if (item.getItemId() == android.R.id.copy) {
                copySelectedText();
                mode.finish();
                return true;
            } else if (item.getItemId() == android.R.id.selectAll) {
                selectAllInCurrentBlock();
                mode.invalidate();
                return true;
            }
            return false;
        }

        private void destroyActionMode() {
            actionMode = null;
            if (!finishingActionMode) {
                clear();
            }
        }

        private void fillSelectionContentRect(Rect outRect) {
            outRect.set(maybeX - AndroidUtilities.dp(24), maybeY - AndroidUtilities.dp(24), maybeX + AndroidUtilities.dp(24), maybeY + AndroidUtilities.dp(24));
            if (!isInSelectionMode()) {
                return;
            }
            arrayList.clear();
            selectedView.fillTextLayoutBlocks(arrayList);
            if (selectedIndex < 0 || selectedIndex >= arrayList.size()) {
                return;
            }
            TextLayoutBlock block = arrayList.get(selectedIndex);
            StaticLayout layout = block.getLayout();
            CharSequence text = layout.getText();
            int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
            int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
            if (start >= end) {
                return;
            }
            int startLine = layout.getLineForOffset(start);
            int endLine = layout.getLineForOffset(Math.max(start, end - 1));
            int top = block.getY() - topOffset + layout.getLineTop(startLine);
            int bottom = block.getY() - topOffset + layout.getLineBottom(endLine);
            float left = layout.getPrimaryHorizontal(start);
            float right = layout.getPrimaryHorizontal(end);
            if (startLine != endLine) {
                left = 0;
                right = layout.getWidth();
            }
            outRect.set(
                    block.getX() + (int) Math.min(left, right),
                    top,
                    block.getX() + (int) Math.max(left, right),
                    bottom
            );
            outRect.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(6));
        }

        private void selectAllInCurrentBlock() {
            if (!isInSelectionMode()) {
                return;
            }
            arrayList.clear();
            selectedView.fillTextLayoutBlocks(arrayList);
            if (selectedIndex < 0 || selectedIndex >= arrayList.size()) {
                return;
            }
            selectionStart = 0;
            selectionEnd = arrayList.get(selectedIndex).getLayout().getText().length();
            invalidateSelectedView();
        }

        private void copySelectedText() {
            AndroidUtilities.addToClipboard(selectedText());
            if (callback != null) {
                callback.onTextCopied();
            }
        }

        private CharSequence selectedText() {
            if (!isInSelectionMode()) {
                return "";
            }
            arrayList.clear();
            selectedView.fillTextLayoutBlocks(arrayList);
            if (selectedIndex < 0 || selectedIndex >= arrayList.size()) {
                return "";
            }
            CharSequence text = arrayList.get(selectedIndex).getLayout().getText();
            int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
            int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text.subSequence(start, end));
            IgnoreCopySpannable[] spans = stringBuilder.getSpans(0, stringBuilder.length(), IgnoreCopySpannable.class);
            for (IgnoreCopySpannable span : spans) {
                int spanStart = stringBuilder.getSpanStart(span);
                int spanEnd = stringBuilder.getSpanEnd(span);
                if (spanStart >= 0 && spanEnd >= spanStart) {
                    stringBuilder.delete(spanStart, spanEnd);
                }
            }
            return stringBuilder;
        }

        private void invalidateSelectedView() {
            if (selectedView instanceof View) {
                ((View) selectedView).invalidate();
            }
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        private void invalidateOverlay() {
            if (overlay != null) {
                overlay.invalidate();
            }
        }

        private int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static class WordBounds {
            final int start;
            final int end;

            WordBounds(int start, int end) {
                this.start = start;
                this.end = end;
            }
        }
    }
}
