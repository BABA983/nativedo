/*
 * This file is part of Telegram for Android.
 *
 * Copyright (C) 2013-2026 Telegram FZ-LLC
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.CornerPathEffect;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.SpanWatcher;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.Magnifier;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CornerPath;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class TextSelectionHelper<Cell extends TextSelectionHelper.SelectableView> {
    public interface SelectableView {
        void fillTextLayoutBlocks(ArrayList<TextLayoutBlock> blocks);
    }

    public interface TextLayoutBlock {
        StaticLayout getLayout();
        int getX();
        int getY();
        int getRow();
        CharSequence getPrefix();
        default StaticLayout getPrefixLayout() { return null; }
        default int getPrefixX() { return getX(); }
        default int getPrefixY() { return getY(); }
        default Rect getClipBounds() { return null; }
    }

    public interface TextSelectionBoundsProvider {
        RectF getSelectionBounds(Paint paint, CharSequence text, int start, int end, float x, int top, int y, int bottom);
        float getSelectionCornerRadius();
    }

    public interface ArticleSelectableView extends SelectableView {
    }

    public interface Callback {
        default void onStateChanged(boolean isSelected) {}
        default void onTextCopied() {}
    }

    public interface TranslateCallback {
        void run(CharSequence text, String fromLang, String toLang, Runnable onAlertDismiss);
    }

    public interface QuoteCallback {
        void run(ArticleSelectableView view, CharSequence text);
    }

    protected final LayoutBlock layoutBlock = new LayoutBlock();

    protected abstract CharSequence getText(Cell view, boolean maybe);
    protected abstract int getCharOffsetFromCord(int x, int y, int offsetX, int offsetY, Cell view, boolean maybe);
    protected abstract void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe);
    protected abstract int getLineHeight();
    protected abstract void onTextSelected(Cell newView, Cell oldView);

    protected static class LayoutBlock {
        int charOffset;
        Layout layout;
        float xOffset;
        float yOffset;
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
        private float pressedX;
        private float pressedY;
        private long pressedTime;

        public TextSelectionOverlay(Context context) {
            super(context);
            setWillNotDraw(false);
            setVisibility(GONE);
        }

        private void setHelper(ArticleTextSelectionHelper helper) {
            this.helper = helper;
        }

        public boolean checkOnTap(MotionEvent event) {
            if (helper == null || !helper.isInSelectionMode()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressedX = event.getX();
                    pressedY = event.getY();
                    pressedTime = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                    int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (System.currentTimeMillis() - pressedTime < 200
                            && Math.abs(event.getX() - pressedX) < touchSlop
                            && Math.abs(event.getY() - pressedY) < touchSlop) {
                        helper.clear();
                        return true;
                    }
                    break;
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (helper != null) {
                helper.drawOverlay(canvas);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return helper != null && helper.onOverlayTouchEvent(event);
        }
    }

    public static class ArticleTextSelectionHelper extends TextSelectionHelper<ArticleSelectableView> {
        public final ArrayList<TextLayoutBlock> arrayList = new ArrayList<>();
        public LinearLayoutManager layoutManager;
        private final float cornerRadius = AndroidUtilities.dp(6f);
        private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectionHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final CornerPath selectionPath = new CornerPath();
        private final Path selectionHandlePath = new Path();
        private final ScalablePath tempPath = new ScalablePath();
        private final OvershootInterpolator handleInterpolator = new OvershootInterpolator();
        private final RectF startHandleRect = new RectF();
        private final RectF endHandleRect = new RectF();
        private final Rect selectionContentRect = new Rect();
        private final int[] tmpLocation = new int[2];
        private final int[] overlayLocation = new int[2];
        private final Map<Integer, CharSequence> textByPosition = new HashMap<>();
        private final Map<Integer, CharSequence> prefixByPosition = new HashMap<>();
        private final Map<Integer, Integer> childCountByPosition = new HashMap<>();
        private TextSelectionOverlay overlay;
        private ViewGroup parentView;
        private RecyclerView parentRecyclerView;
        private RecyclerView.Adapter<?> observedAdapter;
        private Callback callback;
        private TranslateCallback translateCallback;
        private QuoteCallback quoteCallback;
        private ArticleSelectableView selectedView;
        private int selectedIndex = -1;
        private int selectionStart = -1;
        private int selectionEnd = -1;
        private int startViewPosition = -1;
        private int startViewChildPosition = -1;
        private int startViewOffset = -1;
        private int endViewPosition = -1;
        private int endViewChildPosition = -1;
        private int endViewOffset = -1;
        private int topOffset;
        private int maybeX;
        private int maybeY;
        private View maybeView;
        private ActionMode actionMode;
        private ValueAnimator handleAnimator;
        private float handleProgress;
        private boolean usingFloatingToolbar;
        private boolean finishingActionMode;
        private boolean movingHandle;
        private boolean movingStartHandle;
        private boolean activeEndpointStart;
        private float movingOffsetX;
        private float movingOffsetY;
        private boolean movingDirectionSettling;
        private boolean snap;
        private boolean clearOnTapUp;
        private float clearPressedX;
        private float clearPressedY;
        private long clearPressedTime;
        private boolean autoScrolling;
        private boolean autoScrollDown;
        private float autoScrollTargetX;
        private float autoScrollTargetY;
        private Magnifier magnifier;
        private final RecyclerView.AdapterDataObserver adapterDataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (isInSelectionMode()) {
                    clear(false);
                }
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                if (rangeTouchesSelection(positionStart, itemCount)) {
                    clear(false);
                }
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                onItemRangeChanged(positionStart, itemCount);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (isInSelectionMode() && positionStart <= normalizedSelection().endPosition) {
                    clear(false);
                }
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                if (isInSelectionMode() && positionStart <= normalizedSelection().endPosition) {
                    clear(false);
                }
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                if (!isInSelectionMode()) {
                    return;
                }
                if (Math.min(fromPosition, toPosition) <= normalizedSelection().endPosition) {
                    clear(false);
                }
            }
        };

        private final Runnable autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!autoScrolling || parentRecyclerView == null) {
                    return;
                }
                parentRecyclerView.scrollBy(0, autoScrollDown ? AndroidUtilities.dp(8f) : -AndroidUtilities.dp(8f));
                if (movingHandle) {
                    updateSelectionFromOverlayPoint(autoScrollTargetX, autoScrollTargetY, movingStartHandle);
                    showMagnifier(autoScrollTargetX, autoScrollTargetY);
                    invalidate();
                }
                AndroidUtilities.runOnUIThread(this, 16);
            }
        };

        public ArticleTextSelectionHelper() {
            selectionPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
            selectionHandlePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
            selectionPaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(6f)));
            selectionPath.setRectsUnionDiffDelta(1f);
            handlePaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
        }

        public void setParentView(ViewGroup parentView) {
            this.parentView = parentView;
        }

        public void setRecyclerView(RecyclerView recyclerView) {
            if (observedAdapter != null) {
                observedAdapter.unregisterAdapterDataObserver(adapterDataObserver);
                observedAdapter = null;
            }
            parentRecyclerView = recyclerView;
            layoutManager = recyclerView != null && recyclerView.getLayoutManager() instanceof LinearLayoutManager
                    ? (LinearLayoutManager) recyclerView.getLayoutManager()
                    : null;
            if (recyclerView != null && recyclerView.getAdapter() != null) {
                observedAdapter = recyclerView.getAdapter();
                observedAdapter.registerAdapterDataObserver(adapterDataObserver);
            }
        }

        public void setCallback(Callback callback) {
            this.callback = callback;
        }

        public void setOnTranslate(TranslateCallback callback) {
            translateCallback = callback;
        }

        public void setOnQuote(QuoteCallback callback) {
            quoteCallback = callback;
        }

        public TextSelectionOverlay getOverlayView(Context context) {
            if (overlay == null) {
                overlay = new TextSelectionOverlay(context);
                overlay.setHelper(this);
            }
            return overlay;
        }

        @Override
        protected CharSequence getText(ArticleSelectableView view, boolean maybe) {
            if (view == null) {
                return "";
            }
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            int index = maybe ? findClosestLayoutIndex(maybeX, maybeY, view) : selectedIndex;
            if (index < 0 || index >= arrayList.size() || arrayList.get(index).getLayout() == null) {
                return "";
            }
            return arrayList.get(index).getLayout().getText();
        }

        @Override
        protected int getCharOffsetFromCord(int x, int y, int offsetX, int offsetY, ArticleSelectableView view, boolean maybe) {
            if (view == null) {
                return -1;
            }
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            int index = maybe ? findClosestLayoutIndex(x, y, view) : selectedIndex;
            if (index < 0 || index >= arrayList.size()) {
                return -1;
            }
            TextLayoutBlock block = arrayList.get(index);
            StaticLayout layout = block.getLayout();
            if (layout == null || layout.getText().length() == 0) {
                return -1;
            }
            if (isPointInsidePrefix(block, x, y)) {
                return 0;
            }
            int localX = clamp(x - block.getX(), 0, layout.getWidth());
            int localY = clamp(y - block.getY() + topOffset, 0, Math.max(0, layout.getHeight() - 1));
            int line = layout.getLineForVertical(localY);
            return clamp(layout.getOffsetForHorizontal(line, localX), 0, layout.getText().length());
        }

        @Override
        protected void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe) {
            ArticleSelectableView view = maybe ? (maybeView instanceof ArticleSelectableView ? (ArticleSelectableView) maybeView : null) : selectedView;
            if (view == null) {
                layoutBlock.layout = null;
                return;
            }
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            int index = maybe ? findClosestLayoutIndex(maybeX, maybeY, view) : selectedIndex;
            if (index < 0 || index >= arrayList.size()) {
                layoutBlock.layout = null;
                return;
            }
            TextLayoutBlock block = arrayList.get(index);
            layoutBlock.layout = block.getLayout();
            layoutBlock.charOffset = 0;
            layoutBlock.xOffset = block.getX();
            layoutBlock.yOffset = block.getY() - topOffset;
        }

        @Override
        protected int getLineHeight() {
            if (selectedView == null) {
                return 0;
            }
            arrayList.clear();
            selectedView.fillTextLayoutBlocks(arrayList);
            if (selectedIndex < 0 || selectedIndex >= arrayList.size()) {
                return 0;
            }
            StaticLayout layout = arrayList.get(selectedIndex).getLayout();
            if (layout == null || layout.getLineCount() == 0) {
                return 0;
            }
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < layout.getLineCount(); i++) {
                min = Math.min(min, layout.getLineBottom(i) - layout.getLineTop(i));
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        }

        @Override
        protected void onTextSelected(ArticleSelectableView newView, ArticleSelectableView oldView) {
            selectedView = newView;
            int position = getAdapterPosition((View) newView);
            if (position < 0) {
                return;
            }
            startViewPosition = endViewPosition = position;
            startViewChildPosition = endViewChildPosition = selectedIndex;
            startViewOffset = selectionStart;
            endViewOffset = selectionEnd;
            cacheTextForView(newView, position);
            if (oldView instanceof View) {
                ((View) oldView).invalidate();
            }
        }

        public boolean isInSelectionMode() {
            return startViewPosition >= 0
                    && startViewChildPosition >= 0
                    && startViewOffset >= 0
                    && endViewPosition >= 0
                    && endViewChildPosition >= 0
                    && endViewOffset >= 0;
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
            WordBounds bounds = wordBounds(block.getLayout(), offset);
            if (bounds == null) {
                return;
            }
            View viewObject = (View) view;
            selectedView = selectableView;
            selectedIndex = arrayList.indexOf(block);
            selectionStart = bounds.start;
            selectionEnd = bounds.end;
            int position = getAdapterPosition(viewObject);
            if (position < 0) {
                selectedView = null;
                selectedIndex = -1;
                selectionStart = selectionEnd = -1;
                return;
            }
            startViewPosition = endViewPosition = position;
            startViewChildPosition = endViewChildPosition = selectedIndex;
            startViewOffset = selectionStart;
            endViewOffset = selectionEnd;
            activeEndpointStart = false;
            cacheTextForView(selectableView, position);
            viewObject.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            if (overlay != null) {
                overlay.setVisibility(View.VISIBLE);
            }
            viewObject.invalidate();
            invalidateOverlay();
            showHandleViews();
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
            startViewPosition = endViewPosition = -1;
            startViewChildPosition = endViewChildPosition = -1;
            startViewOffset = endViewOffset = -1;
            activeEndpointStart = false;
            textByPosition.clear();
            prefixByPosition.clear();
            childCountByPosition.clear();
            stopAutoScroll();
            clearOnTapUp = false;
            hideMagnifier();
            if (handleAnimator != null) {
                handleAnimator.cancel();
                handleAnimator = null;
            }
            handleProgress = 0f;
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
            }
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
        public void stopScrolling() {
            stopAutoScroll();
            if (!isInSelectionMode()) {
                return;
            }
            syncActiveViewFromEndpoint();
            invalidate();
            if (overlay != null && selectedView instanceof View) {
                showActionMode((View) selectedView);
            }
        }
        public void onParentScrolled() {
            if (!isInSelectionMode()) {
                return;
            }
            cacheVisibleTextForSelection(normalizedSelection());
            syncActiveViewFromEndpoint();
            invalidate();
            hideActionMode();
        }

        public void destroy() {
            clear(false);
            stopAutoScroll();
            hideMagnifier();
            if (handleAnimator != null) {
                handleAnimator.cancel();
                handleAnimator = null;
            }
            if (overlay != null) {
                overlay.setHelper(null);
                overlay.setVisibility(View.GONE);
            }
            overlay = null;
            parentRecyclerView = null;
            layoutManager = null;
            if (observedAdapter != null) {
                observedAdapter.unregisterAdapterDataObserver(adapterDataObserver);
                observedAdapter = null;
            }
            parentView = null;
            callback = null;
            translateCallback = null;
            quoteCallback = null;
            maybeView = null;
        }

        public void startOneTouchSelection() {
            if (!isInSelectionMode()) {
                return;
            }
            movingHandle = true;
            movingStartHandle = false;
            activeEndpointStart = false;
            movingOffsetX = 0;
            movingOffsetY = 0;
            movingDirectionSettling = true;
            snap = false;
            hideActionMode();
        }

        public boolean continueOneTouchSelection(View view, MotionEvent event) {
            if (!isInSelectionMode() || overlay == null) {
                return false;
            }
            view.getLocationInWindow(tmpLocation);
            overlay.getLocationInWindow(overlayLocation);
            float targetX = event.getX() + tmpLocation[0] - overlayLocation[0];
            float targetY = event.getY() + tmpLocation[1] - overlayLocation[1];
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (!movingHandle) {
                        startOneTouchSelection();
                    }
                    updateSelectionFromOverlayPoint(targetX, targetY, movingStartHandle);
                    autoScrollTargetX = targetX;
                    autoScrollTargetY = targetY;
                    updateAutoScroll(targetY);
                    showMagnifier(targetX, targetY);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (movingHandle) {
                        movingHandle = false;
                        movingOffsetX = 0;
                        movingOffsetY = 0;
                        movingDirectionSettling = false;
                        snap = false;
                        stopAutoScroll();
                        hideMagnifier();
                        if (selectedView instanceof View) {
                            showActionMode((View) selectedView);
                        }
                        invalidate();
                    }
                    return true;
            }
            return true;
        }

        public void draw(Canvas canvas, ArticleSelectableView view, int index) {
            if (!isInSelectionMode()) {
                return;
            }
            int position = getAdapterPosition((View) view);
            if (position < 0 || !positionIntersectsSelection(position)) {
                return;
            }
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            selectionPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
            selectionHandlePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inTextSelectionHighlight), 0.38f));
            handlePaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
            cacheTextForView(view, position);
            NormalizedSelection normalized = normalizedSelection();
            for (int i = 0; i < arrayList.size(); i++) {
                TextLayoutBlock block = arrayList.get(i);
                StaticLayout layout = block.getLayout();
                if (layout == null) {
                    continue;
                }
                CharSequence text = layout.getText();
                if (text.length() == 0) {
                    continue;
                }
                int[] range = selectionRangeForBlock(normalized, position, i, text.length());
                if (range == null || range[0] >= range[1]) {
                    continue;
                }
                StaticLayout prefixLayout = block.getPrefixLayout();
                if (prefixLayout != null && range[0] == 0 && prefixLayout.getText().length() > 0) {
                    canvas.save();
                    Rect clipBounds = block.getClipBounds();
                    if (clipBounds != null) {
                        canvas.clipRect(clipBounds);
                    }
                    canvas.translate(block.getPrefixX(), block.getPrefixY() - topOffset);
                    drawSelection(canvas, prefixLayout, 0, prefixLayout.getText().length(), false, false);
                    canvas.restore();
                }
                canvas.save();
                Rect clipBounds = block.getClipBounds();
                if (clipBounds != null) {
                    canvas.clipRect(clipBounds);
                }
                canvas.translate(block.getX(), block.getY() - topOffset);
                drawSelection(canvas, layout, range[0], range[1],
                        position == normalized.startPosition && i == normalized.startChild,
                        position == normalized.endPosition && i == normalized.endChild);
                canvas.restore();
            }
        }

        protected void drawSelection(Canvas canvas, Layout layout, int start, int end, boolean hasStart, boolean hasEnd) {
            if (layout == null || layout.getText() == null) {
                return;
            }
            int length = layout.getText().length();
            start = clamp(start, 0, length);
            end = clamp(end, 0, length);
            if (start >= end) {
                return;
            }
            selectionPath.reset();
            selectionHandlePath.reset();
            final float radius = cornerRadius * 1.65f;
            final int halfRadius = (int) (cornerRadius / 2f);
            int startLine = layout.getLineForOffset(start);
            int endLine = layout.getLineForOffset(end);
            if (startLine == endLine) {
                if (!tryAddCustomSelectionPath(layout, startLine, start, end, !hasStart, !hasEnd)) {
                    drawLine(layout, startLine, start, end, !hasStart, !hasEnd, 0);
                }
            } else {
                int lineEnd = layout.getLineEnd(startLine);
                Rect firstLineTail = null;
                if (layout.getParagraphDirection(startLine) != StaticLayout.DIR_RIGHT_TO_LEFT && lineEnd > 0) {
                    lineEnd--;
                    CharSequence text = layout.getText();
                    int s = (int) layout.getPrimaryHorizontal(lineEnd);
                    int e;
                    if (layout.isRtlCharAt(lineEnd)) {
                        int endIndex = lineEnd;
                        while (layout.isRtlCharAt(endIndex)) {
                            if (endIndex == 0) break;
                            endIndex--;
                        }
                        e = layout.getLineForOffset(endIndex) == layout.getLineForOffset(lineEnd) ? (int) layout.getPrimaryHorizontal(endIndex + 1) : (int) layout.getLineLeft(startLine);
                    } else {
                        e = (int) layout.getLineRight(startLine);
                    }
                    int left = Math.min(s, e);
                    int right = Math.max(s, e);
                    if (lineEnd > 0 && lineEnd < text.length() && !Character.isWhitespace(text.charAt(lineEnd - 1))) {
                        firstLineTail = new Rect(left - halfRadius, layout.getLineTop(startLine), right + halfRadius, layout.getLineBottom(startLine));
                    }
                }
                drawLineWithCustomSelectionPath(layout, startLine, start, lineEnd, !hasStart, true, 0);
                if (firstLineTail != null) {
                    AndroidUtilities.rectTmp.set(firstLineTail);
                    selectionPath.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                }
                for (int i = startLine + 1; i < endLine; i++) {
                    int lineStart = layout.getLineStart(i);
                    int lineEndOffset = layout.getLineEnd(i);
                    if (!tryAddCustomSelectionPath(layout, i, lineStart, lineEndOffset, true, true)) {
                        int s = (int) layout.getLineLeft(i);
                        int e = (int) layout.getLineRight(i);
                        int left = Math.min(s, e);
                        int right = Math.max(s, e);
                        selectionPath.addRect(left - halfRadius, layout.getLineTop(i), right + halfRadius, layout.getLineBottom(i) + 1, Path.Direction.CW);
                    }
                }
                drawLineWithCustomSelectionPath(layout, endLine, layout.getLineStart(endLine), end, true, !hasEnd, 0);
            }
            boolean restore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
            if (restore) {
                canvas.save();
            }
            float startLeft = layout.getPrimaryHorizontal(start);
            float endRight = layout.getPrimaryHorizontal(end);
            float startBottom = layout.getLineBottom(startLine);
            float endBottom = layout.getLineBottom(endLine);
            if (hasStart && hasEnd && startBottom == endBottom && Math.abs(endRight - startLeft) < radius) {
                float left = Math.min(startLeft, endRight);
                float right = Math.max(startLeft, endRight);
                AndroidUtilities.rectTmp2.set((int) left, (int) (startBottom - radius), (int) right, (int) startBottom);
                AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                selectionHandlePath.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutRect(AndroidUtilities.rectTmp2);
                }
            } else {
                if (hasStart && !layout.isRtlCharAt(start)) {
                    AndroidUtilities.rectTmp2.set((int) startLeft, (int) (startBottom - radius), (int) Math.min(startLeft + radius, layout.getLineRight(startLine)), (int) startBottom);
                    AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                    selectionHandlePath.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        AndroidUtilities.rectTmp2.set(AndroidUtilities.rectTmp2.left - (int) radius, AndroidUtilities.rectTmp2.top, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.bottom);
                        canvas.clipOutRect(AndroidUtilities.rectTmp2);
                    }
                }
                if (hasEnd && !layout.isRtlCharAt(end)) {
                    AndroidUtilities.rectTmp2.set((int) Math.max(endRight - radius, layout.getLineLeft(endLine)), (int) (endBottom - radius), (int) endRight, (int) endBottom);
                    AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                    selectionHandlePath.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        canvas.clipOutRect(AndroidUtilities.rectTmp2);
                    }
                }
            }
            selectionPath.closeRects();
            canvas.drawPath(selectionPath, selectionPaint);
            if (restore) {
                canvas.restore();
                canvas.drawPath(selectionHandlePath, selectionHandlePaint);
            }
        }

        private void drawLine(Layout layout, int line, int start, int end, boolean padAtStart, boolean padAtEnd, float minX) {
            tempPath.reset();
            layout.getSelectionPath(start, end, tempPath);
            float scaleY = 1f;
            float centerY = 0f;
            if (tempPath.lastBottom < layout.getLineBottom(line)) {
                int lineTop = layout.getLineTop(line);
                int lineBottom = layout.getLineBottom(line);
                float lineHeight = lineBottom - lineTop;
                float pathHeight = tempPath.lastBottom - lineTop;
                if (pathHeight > 0) {
                    scaleY = lineHeight / pathHeight;
                    centerY = lineTop;
                }
            }
            for (int i = 0; i < tempPath.rectsCount; i++) {
                RectF rect = tempPath.rects.get(i);
                rect.set(
                        (int) (Math.max(minX, rect.left) - (padAtStart ? cornerRadius / 2f : 0)),
                        (int) ((rect.top - centerY) * scaleY + centerY),
                        (int) (Math.max(minX, rect.right) + (padAtEnd ? cornerRadius / 2f : 0)),
                        (int) ((rect.bottom - centerY) * scaleY + centerY)
                );
                selectionPath.addRect(rect, Path.Direction.CW);
            }
            if (tempPath.rectsCount == 0 && !padAtEnd) {
                int left = (int) layout.getPrimaryHorizontal(start);
                int right = (int) layout.getPrimaryHorizontal(end);
                int top = layout.getLineTop(line);
                int bottom = layout.getLineBottom(line);
                selectionPath.addRect(left - cornerRadius / 2f, top, right + cornerRadius / 4f, bottom, Path.Direction.CW);
            }
        }

        private void drawLineWithCustomSelectionPath(Layout layout, int line, int start, int end, boolean padAtStart, boolean padAtEnd, float minX) {
            if (!tryAddCustomSelectionPath(layout, line, start, end, padAtStart, padAtEnd)) {
                drawLine(layout, line, start, end, padAtStart, padAtEnd, minX);
            }
        }

        private boolean tryAddCustomSelectionPath(Layout layout, int line, int start, int end, boolean padAtStart, boolean padAtEnd) {
            if (start >= end) {
                return false;
            }
            CharSequence text = layout.getText();
            if (!(text instanceof Spanned)) {
                return false;
            }
            Spanned spanned = (Spanned) text;
            TextSelectionBoundsProvider[] spans = spanned.getSpans(start, end, TextSelectionBoundsProvider.class);
            if (spans.length == 0) {
                return false;
            }
            java.util.Arrays.sort(spans, (first, second) -> {
                int firstStart = spanned.getSpanStart(first);
                int secondStart = spanned.getSpanStart(second);
                if (firstStart != secondStart) {
                    return firstStart - secondStart;
                }
                return spanned.getSpanEnd(first) - spanned.getSpanEnd(second);
            });

            boolean addedCustomPath = false;
            int cursor = start;
            for (TextSelectionBoundsProvider span : spans) {
                int spanStart = spanned.getSpanStart(span);
                int spanEnd = spanned.getSpanEnd(span);
                if (spanStart < 0 || spanEnd <= spanStart || spanEnd <= start || spanStart >= end) {
                    continue;
                }
                if (line != layout.getLineForOffset(spanStart) || line != layout.getLineForOffset(Math.max(spanStart, spanEnd - 1))) {
                    continue;
                }
                int segmentStart = Math.max(start, spanStart);
                int segmentEnd = Math.min(end, spanEnd);
                RectF bounds = span.getSelectionBounds(
                        layout.getPaint(),
                        spanned,
                        spanStart,
                        spanEnd,
                        layout.getPrimaryHorizontal(spanStart),
                        layout.getLineTop(line),
                        layout.getLineBaseline(line),
                        layout.getLineBottom(line)
                );
                if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) {
                    continue;
                }
                if (cursor < segmentStart) {
                    drawLine(layout, line, cursor, segmentStart, !addedCustomPath && padAtStart, false, 0);
                }
                float radius = span.getSelectionCornerRadius();
                if (radius > 0f) {
                    selectionPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
                } else {
                    selectionPath.addRect(bounds, Path.Direction.CW);
                }
                addedCustomPath = true;
                cursor = Math.max(cursor, segmentEnd);
            }
            if (!addedCustomPath) {
                return false;
            }
            if (cursor < end) {
                drawLine(layout, line, cursor, end, false, padAtEnd, 0);
            }
            return true;
        }

        private TextLayoutBlock findSelectableBlock(View view, ArrayList<TextLayoutBlock> blocks) {
            if (maybeView == view) {
                for (TextLayoutBlock block : blocks) {
                    StaticLayout layout = block.getLayout();
                    if (layout == null || layout.getText().length() == 0) {
                        continue;
                    }
                    if (isPointInsidePrefix(block, maybeX, maybeY)) {
                        return block;
                    }
                    int x = maybeX - block.getX();
                    int y = maybeY - block.getY() + topOffset;
                    if (y < 0 || y > layout.getHeight()) {
                        continue;
                    }
                    int line = layout.getLineForVertical(y);
                    if (isPointInsideCustomSelectionSpan(layout, line, x, y)) {
                        return block;
                    }
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

        private int findClosestLayoutIndex(int x, int y, ArticleSelectableView view) {
            if (view == null) {
                return -1;
            }
            ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
            view.fillTextLayoutBlocks(blocks);
            int minDistance = Integer.MAX_VALUE;
            int minIndex = -1;
            int bestInsideYDistanceX = Integer.MAX_VALUE;
            int bestInsideYIndex = -1;
            int bestInsideYRow = 0;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                TextLayoutBlock block = blocks.get(i);
                StaticLayout layout = block.getLayout();
                if (layout == null || layout.getText().length() == 0) {
                    continue;
                }
                if (isPointInsidePrefix(block, x, y)) {
                    return i;
                }
                int top = block.getY();
                int bottom = top + layout.getHeight();
                if (y >= top && y <= bottom) {
                    int localX = x - block.getX();
                    int localY = y - block.getY() + topOffset;
                    int line = layout.getLineForVertical(clamp(localY, 0, Math.max(0, layout.getHeight() - 1)));
                    if (isPointInsideCustomSelectionSpan(layout, line, localX, localY)) {
                        return i;
                    }
                    int left = (int) Math.min(layout.getLineLeft(line), layout.getLineRight(line));
                    int right = (int) Math.max(layout.getLineLeft(line), layout.getLineRight(line));
                    if (localX >= left && localX <= right) {
                        return i;
                    }
                    int distanceX = localX < left ? left - localX : localX - right;
                    if (distanceX < bestInsideYDistanceX) {
                        bestInsideYDistanceX = distanceX;
                        bestInsideYIndex = i;
                        bestInsideYRow = block.getRow();
                    }
                    continue;
                }
                int distance = Math.min(Math.abs(y - top), Math.abs(y - bottom));
                if (distance < minDistance) {
                    minDistance = distance;
                    minIndex = i;
                }
            }
            if (bestInsideYIndex >= 0) {
                if (bestInsideYRow > 0) {
                    int minDistanceX = bestInsideYDistanceX;
                    int minIndexX = bestInsideYIndex;
                    for (int i = blocks.size() - 1; i >= 0; i--) {
                        TextLayoutBlock block = blocks.get(i);
                        StaticLayout layout = block.getLayout();
                        if (layout == null || block.getRow() != bestInsideYRow) {
                            continue;
                        }
                        int localX = x - block.getX();
                        int left = block.getX();
                        int right = left + layout.getWidth();
                        if (x >= left && x <= right) {
                            return i;
                        }
                        int distanceX = Math.min(Math.abs(x - left), Math.abs(x - right));
                        if (distanceX < minDistanceX) {
                            minDistanceX = distanceX;
                            minIndexX = i;
                        }
                    }
                    return minIndexX;
                }
                return bestInsideYIndex;
            }
            if (minIndex >= 0) {
                int row = blocks.get(minIndex).getRow();
                if (row > 0 && minDistance < AndroidUtilities.dp(24f)) {
                    int minDistanceX = Integer.MAX_VALUE;
                    int minIndexX = minIndex;
                    for (int i = blocks.size() - 1; i >= 0; i--) {
                        TextLayoutBlock block = blocks.get(i);
                        StaticLayout layout = block.getLayout();
                        if (layout == null || block.getRow() != row) {
                            continue;
                        }
                        int left = block.getX();
                        int right = left + layout.getWidth();
                        if (x >= left && x <= right) {
                            return i;
                        }
                        int distanceX = Math.min(Math.abs(x - left), Math.abs(x - right));
                        if (distanceX < minDistanceX) {
                            minDistanceX = distanceX;
                            minIndexX = i;
                        }
                    }
                    return minIndexX;
                }
            }
            return minIndex;
        }

        private int charOffsetForTouch(TextLayoutBlock block) {
            StaticLayout layout = block.getLayout();
            if (isPointInsidePrefix(block, maybeX, maybeY)) {
                return 0;
            }
            int x = clamp(maybeX - block.getX(), 0, layout.getWidth());
            int y = clamp(maybeY - block.getY() + topOffset, 0, Math.max(0, layout.getHeight() - 1));
            int line = layout.getLineForVertical(y);
            WordBounds customBounds = customSelectionSpanBoundsAtPoint(layout, line, x, y);
            if (customBounds != null) {
                return customBounds.start;
            }
            return clamp(layout.getOffsetForHorizontal(line, x), 0, layout.getText().length());
        }

        private boolean isPointInsideCustomSelectionSpan(Layout layout, int line, int x, int y) {
            return customSelectionSpanBoundsAtPoint(layout, line, x, y) != null;
        }

        private boolean isPointInsidePrefix(TextLayoutBlock block, int x, int y) {
            StaticLayout prefixLayout = block.getPrefixLayout();
            if (prefixLayout == null || prefixLayout.getText().length() == 0) {
                return false;
            }
            int localX = x - block.getPrefixX();
            int localY = y - block.getPrefixY() + topOffset;
            if (localY < 0 || localY > prefixLayout.getHeight()) {
                return false;
            }
            int line = prefixLayout.getLineForVertical(clamp(localY, 0, Math.max(0, prefixLayout.getHeight() - 1)));
            float left = prefixLayout.getLineLeft(line);
            float right = prefixLayout.getLineRight(line);
            return localX >= Math.min(left, right) && localX <= Math.max(left, right);
        }

        private int prefixHitScore(TextLayoutBlock block, int x, int y) {
            StaticLayout prefixLayout = block.getPrefixLayout();
            if (prefixLayout == null || prefixLayout.getText().length() == 0) {
                return Integer.MAX_VALUE;
            }
            int localX = x - block.getPrefixX();
            int localY = y - block.getPrefixY() + topOffset;
            int distanceY = 0;
            if (localY < 0) distanceY = -localY;
            if (localY > prefixLayout.getHeight()) distanceY = localY - prefixLayout.getHeight();
            int distanceX = 0;
            if (localX < 0) distanceX = -localX;
            if (localX > prefixLayout.getWidth()) distanceX = localX - prefixLayout.getWidth();
            return distanceY * 10000 + distanceX;
        }

        private WordBounds wordBounds(Layout layout, int offset) {
            if (layout == null) {
                return null;
            }
            return wordBounds(layout.getText(), offset, layout);
        }

        private WordBounds wordBounds(CharSequence text, int offset) {
            return wordBounds(text, offset, null);
        }

        private WordBounds wordBounds(CharSequence text, int offset, Layout layout) {
            int length = text.length();
            if (length == 0) {
                return null;
            }
            int safeOffset = clamp(offset, 0, length - 1);
            WordBounds customSpanBounds = customSelectionSpanBounds(text, safeOffset, layout);
            if (customSpanBounds != null) {
                return customSpanBounds;
            }
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

        public static boolean isInterruptedCharacter(char value) {
            return Character.isLetter(value) || Character.isDigit(value) || value == '_';
        }

        private int getAdapterPosition(View view) {
            if (view == null) {
                return -1;
            }
            if (parentRecyclerView != null) {
                View child = view;
                ViewParent parent = child.getParent();
                while (parent != null && parent != parentRecyclerView) {
                    if (parent instanceof View) {
                        child = (View) parent;
                        parent = child.getParent();
                    } else {
                        return -1;
                    }
                }
                return parent == parentRecyclerView ? parentRecyclerView.getChildAdapterPosition(child) : -1;
            }
            return parentView != null ? parentView.indexOfChild(view) : -1;
        }

        private void cacheTextForView(ArticleSelectableView view, int position) {
            if (view == null || position < 0) {
                return;
            }
            ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
            view.fillTextLayoutBlocks(blocks);
            childCountByPosition.put(position, blocks.size());
            for (int i = 0; i < blocks.size(); i++) {
                TextLayoutBlock block = blocks.get(i);
                if (block.getLayout() != null) {
                    cacheLayoutBlock(position, i, block);
                }
            }
        }

        private void cacheLayoutBlock(int position, int childPosition, TextLayoutBlock block) {
            final int key = cacheKey(position, childPosition);
            textByPosition.put(key, detachedText(block.getLayout().getText()));
            final CharSequence prefix = block.getPrefix();
            if (prefix == null) {
                prefixByPosition.remove(key);
            } else {
                prefixByPosition.put(key, detachedText(prefix));
            }
        }

        private static CharSequence detachedText(CharSequence text) {
            if (text == null) {
                return "";
            }
            if (!(text instanceof Spanned)) {
                return text.toString();
            }
            final Spanned source = (Spanned) text;
            final SpannableStringBuilder copy = new SpannableStringBuilder(text.toString());
            final Object[] spans = source.getSpans(0, source.length(), Object.class);
            for (Object span : spans) {
                if (span instanceof TextWatcher || span instanceof SpanWatcher || span instanceof NoCopySpan) {
                    continue;
                }
                final int start = source.getSpanStart(span);
                final int end = source.getSpanEnd(span);
                if (start < 0 || end < start || start > copy.length()) {
                    continue;
                }
                copy.setSpan(span, start, Math.min(end, copy.length()), source.getSpanFlags(span));
            }
            return copy;
        }

        private int cacheKey(int position, int childPosition) {
            return position + (childPosition << 16);
        }

        private boolean comesBefore(int positionA, int childA, int offsetA, int positionB, int childB, int offsetB) {
            if (positionA != positionB) return positionA < positionB;
            if (childA != childB) return childA < childB;
            return offsetA <= offsetB;
        }

        private NormalizedSelection normalizedSelection() {
            if (comesBefore(startViewPosition, startViewChildPosition, startViewOffset, endViewPosition, endViewChildPosition, endViewOffset)) {
                return new NormalizedSelection(startViewPosition, startViewChildPosition, startViewOffset, endViewPosition, endViewChildPosition, endViewOffset);
            }
            return new NormalizedSelection(endViewPosition, endViewChildPosition, endViewOffset, startViewPosition, startViewChildPosition, startViewOffset);
        }

        private boolean positionIntersectsSelection(int position) {
            NormalizedSelection normalized = normalizedSelection();
            return position >= normalized.startPosition && position <= normalized.endPosition;
        }

        private int[] selectionRangeForBlock(NormalizedSelection normalized, int position, int childPosition, int textLength) {
            int start = 0;
            int end = textLength;
            if (position == normalized.startPosition && childPosition < normalized.startChild) return null;
            if (position == normalized.endPosition && childPosition > normalized.endChild) return null;
            if (position == normalized.startPosition) {
                if (childPosition == normalized.startChild) {
                    start = normalized.startOffset;
                } else if (childPosition < normalized.startChild) {
                    return null;
                }
            }
            if (position == normalized.endPosition) {
                if (childPosition == normalized.endChild) {
                    end = normalized.endOffset;
                } else if (childPosition > normalized.endChild) {
                    return null;
                }
            }
            start = clamp(start, 0, textLength);
            end = clamp(end, 0, textLength);
            return start < end ? new int[]{start, end} : null;
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
                    return updateActionMenu(menu);
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
                    return updateActionMenu(menu);
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
                usingFloatingToolbar = true;
                FloatingToolbar floatingToolbar = new FloatingToolbar(view.getContext(), overlay != null ? overlay : view, FloatingToolbar.STYLE_THEME, null);
                actionMode = new FloatingActionMode(view.getContext(), (ActionMode.Callback2) callback, overlay != null ? overlay : view, floatingToolbar);
                callback.onCreateActionMode(actionMode, actionMode.getMenu());
                actionMode.invalidate();
            } else {
                usingFloatingToolbar = false;
                actionMode = view.startActionMode(callback);
            }
        }

        private boolean createActionMenu(Menu menu) {
            Context context = overlay != null ? overlay.getContext() : null;
            menu.add(Menu.NONE, android.R.id.copy, 0, context != null ? context.getString(android.R.string.copy) : "Copy").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, R.id.menu_quote, 1, context != null ? context.getString(R.string.Quote) : "Quote").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, android.R.id.selectAll, 2, context != null ? context.getString(android.R.string.selectAll) : "Select all").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, R.id.menu_translate, 3, context != null ? context.getString(R.string.TranslateMessage) : "Translate").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            return updateActionMenu(menu);
        }

        private boolean updateActionMenu(Menu menu) {
            boolean canCopy = canCopySelection();
            MenuItem copyItem = menu.findItem(android.R.id.copy);
            if (copyItem != null) {
                copyItem.setVisible(canCopy);
            }
            MenuItem quoteItem = menu.findItem(R.id.menu_quote);
            if (quoteItem != null) {
                quoteItem.setVisible(quoteCallback != null && canCopy && canQuoteSelection());
            }
            MenuItem selectAllItem = menu.findItem(android.R.id.selectAll);
            if (selectAllItem != null) {
                selectAllItem.setVisible(canCopy && canSelectAllCurrentView());
            }
            MenuItem translateItem = menu.findItem(R.id.menu_translate);
            if (translateItem != null) {
                translateItem.setVisible(translateCallback != null && canCopy);
            }
            return true;
        }

        private boolean handleActionItem(ActionMode mode, MenuItem item) {
            if (item.getItemId() == android.R.id.copy) {
                copySelectedText();
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.menu_quote && quoteCallback != null) {
                quoteCallback.run(selectedView, selectedText());
                mode.finish();
                return true;
            } else if (item.getItemId() == android.R.id.selectAll) {
                selectAllInCurrentBlock();
                mode.invalidate();
                return true;
            } else if (item.getItemId() == R.id.menu_translate && translateCallback != null) {
                CharSequence text = selectedText();
                String translateToLanguage = LocaleController.getInstance().getCurrentLocale().getLanguage();
                hideActionMode();
                translateCallback.run(text, null, translateToLanguage, () -> {
                    if (isInSelectionMode() && selectedView instanceof View) {
                        showActionMode((View) selectedView);
                    }
                });
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
            if (!isInSelectionMode()) {
                fillFallbackContentRect(outRect);
                return;
            }
            if (fillVisibleSelectionContentRect(outRect)) {
                outRect.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(6));
                return;
            }
            if (selectedView == null) {
                outRect.set(-1, -1, -1, -1);
                return;
            }
            fillFallbackContentRect(outRect);
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
            int offsetX = 0;
            int offsetY = 0;
            if (selectedView instanceof View) {
                ((View) selectedView).getLocationInWindow(tmpLocation);
                offsetX = tmpLocation[0];
                offsetY = tmpLocation[1];
            }
            outRect.set(
                    offsetX + block.getX() + (int) Math.min(left, right),
                    offsetY + top,
                    offsetX + block.getX() + (int) Math.max(left, right),
                    offsetY + bottom
            );
            outRect.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(6));
        }

        private void fillFallbackContentRect(Rect outRect) {
            int left = maybeX - AndroidUtilities.dp(24);
            int top = maybeY - AndroidUtilities.dp(24);
            int right = maybeX + AndroidUtilities.dp(24);
            int bottom = maybeY + AndroidUtilities.dp(24);
            if (maybeView != null) {
                maybeView.getLocationInWindow(tmpLocation);
                left += tmpLocation[0];
                right += tmpLocation[0];
                top += tmpLocation[1];
                bottom += tmpLocation[1];
            }
            outRect.set(left, top, right, bottom);
        }

        private boolean fillVisibleSelectionContentRect(Rect outRect) {
            NormalizedSelection normalized = normalizedSelection();
            boolean hasRect = false;
            selectionContentRect.setEmpty();
            if (parentRecyclerView != null) {
                for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                    View child = parentRecyclerView.getChildAt(i);
                    if (!(child instanceof ArticleSelectableView)) {
                        continue;
                    }
                    int position = getAdapterPosition(child);
                    if (!positionIntersectsSelection(position)) {
                        continue;
                    }
                    hasRect = appendSelectionContentRect(selectionContentRect, hasRect, (ArticleSelectableView) child, child, position, normalized);
                }
            } else if (selectedView instanceof View) {
                int position = getAdapterPosition((View) selectedView);
                hasRect = appendSelectionContentRect(selectionContentRect, hasRect, selectedView, (View) selectedView, position, normalized);
            }
            if (hasRect) {
                outRect.set(selectionContentRect);
            }
            return hasRect;
        }

        private boolean appendSelectionContentRect(Rect outRect, boolean hasRect, ArticleSelectableView selectableView, View view, int position, NormalizedSelection normalized) {
            if (position < 0 || !positionIntersectsSelection(position)) {
                return hasRect;
            }
            arrayList.clear();
            selectableView.fillTextLayoutBlocks(arrayList);
            view.getLocationInWindow(tmpLocation);
            for (int i = 0; i < arrayList.size(); i++) {
                TextLayoutBlock block = arrayList.get(i);
                StaticLayout layout = block.getLayout();
                if (layout == null || layout.getText().length() == 0) {
                    continue;
                }
                int[] range = selectionRangeForBlock(normalized, position, i, layout.getText().length());
                if (range == null || range[0] >= range[1]) {
                    continue;
                }
                int startLine = layout.getLineForOffset(range[0]);
                int endLine = layout.getLineForOffset(Math.max(range[0], range[1] - 1));
                float left = startLine == endLine ? Math.min(layout.getPrimaryHorizontal(range[0]), layout.getPrimaryHorizontal(range[1])) : 0;
                float right = startLine == endLine ? Math.max(layout.getPrimaryHorizontal(range[0]), layout.getPrimaryHorizontal(range[1])) : layout.getWidth();
                int rectLeft = tmpLocation[0] + block.getX() + (int) left;
                int rectTop = tmpLocation[1] + block.getY() - topOffset + layout.getLineTop(startLine);
                int rectRight = tmpLocation[0] + block.getX() + (int) right;
                int rectBottom = tmpLocation[1] + block.getY() - topOffset + layout.getLineBottom(endLine);
                Rect clipBounds = block.getClipBounds();
                if (clipBounds != null) {
                    int clipLeft = tmpLocation[0] + clipBounds.left;
                    int clipTop = tmpLocation[1] + clipBounds.top;
                    int clipRight = tmpLocation[0] + clipBounds.right;
                    int clipBottom = tmpLocation[1] + clipBounds.bottom;
                    rectLeft = Math.max(rectLeft, clipLeft);
                    rectTop = Math.max(rectTop, clipTop);
                    rectRight = Math.min(rectRight, clipRight);
                    rectBottom = Math.min(rectBottom, clipBottom);
                    if (rectLeft >= rectRight || rectTop >= rectBottom) {
                        continue;
                    }
                }
                if (!hasRect) {
                    outRect.set(rectLeft, rectTop, rectRight, rectBottom);
                    hasRect = true;
                } else {
                    outRect.union(rectLeft, rectTop, rectRight, rectBottom);
                }
                StaticLayout prefixLayout = block.getPrefixLayout();
                if (prefixLayout != null && range[0] == 0 && prefixLayout.getText().length() > 0) {
                    int prefixLeft = tmpLocation[0] + block.getPrefixX();
                    int prefixTop = tmpLocation[1] + block.getPrefixY() - topOffset;
                    int prefixRight = prefixLeft + prefixLayout.getWidth();
                    int prefixBottom = prefixTop + prefixLayout.getHeight();
                    if (clipBounds != null) {
                        int clipLeft = tmpLocation[0] + clipBounds.left;
                        int clipTop = tmpLocation[1] + clipBounds.top;
                        int clipRight = tmpLocation[0] + clipBounds.right;
                        int clipBottom = tmpLocation[1] + clipBounds.bottom;
                        prefixLeft = Math.max(prefixLeft, clipLeft);
                        prefixTop = Math.max(prefixTop, clipTop);
                        prefixRight = Math.min(prefixRight, clipRight);
                        prefixBottom = Math.min(prefixBottom, clipBottom);
                    }
                    if (prefixLeft >= prefixRight || prefixTop >= prefixBottom) {
                        continue;
                    }
                    outRect.union(prefixLeft, prefixTop, prefixRight, prefixBottom);
                }
            }
            return hasRect;
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
            int position = getAdapterPosition((View) selectedView);
            int lastIndex = arrayList.size() - 1;
            startViewPosition = endViewPosition = position;
            startViewChildPosition = selectedIndex = 0;
            endViewChildPosition = lastIndex;
            startViewOffset = selectionStart = 0;
            endViewOffset = selectionEnd = arrayList.get(lastIndex).getLayout().getText().length();
            cacheTextForView(selectedView, position);
            invalidateSelectedView();
            invalidateOverlay();
        }

        private boolean canCopySelection() {
            return isInSelectionMode() && selectedText().length() > 0;
        }

        private boolean canSelectAllCurrentView() {
            if (!isInSelectionMode() || selectedView == null) {
                return false;
            }
            int position = getAdapterPosition((View) selectedView);
            NormalizedSelection normalized = normalizedSelection();
            if (position < 0 || normalized.startPosition != position || normalized.endPosition != position) {
                return false;
            }
            arrayList.clear();
            selectedView.fillTextLayoutBlocks(arrayList);
            int lastIndex = -1;
            int lastLength = 0;
            for (int i = arrayList.size() - 1; i >= 0; i--) {
                StaticLayout layout = arrayList.get(i).getLayout();
                if (layout != null) {
                    lastIndex = i;
                    lastLength = layout.getText().length();
                    break;
                }
            }
            if (lastIndex < 0) {
                return false;
            }
            return !(normalized.startChild == 0
                    && normalized.startOffset <= 0
                    && normalized.endChild == lastIndex
                    && normalized.endOffset >= lastLength);
        }

        private boolean canQuoteSelection() {
            if (!isInSelectionMode() || selectedView == null) {
                return false;
            }
            NormalizedSelection normalized = normalizedSelection();
            return normalized.startPosition == normalized.endPosition
                    && getAdapterPosition((View) selectedView) == normalized.startPosition;
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
            NormalizedSelection normalized = normalizedSelection();
            cacheVisibleTextForSelection(normalized);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            for (int position = normalized.startPosition; position <= normalized.endPosition; position++) {
                int firstChild = position == normalized.startPosition ? normalized.startChild : 0;
                int childCount = childCountByPosition.containsKey(position) ? childCountByPosition.get(position) : 0;
                int lastChild = position == normalized.endPosition ? normalized.endChild : childCount - 1;
                for (int child = firstChild; child <= lastChild; child++) {
                    CharSequence text = textByPosition.get(cacheKey(position, child));
                    if (text == null) {
                        continue;
                    }
                    int start = position == normalized.startPosition && child == normalized.startChild ? normalized.startOffset : 0;
                    int end = position == normalized.endPosition && child == normalized.endChild ? normalized.endOffset : text.length();
                    start = clamp(start, 0, text.length());
                    end = clamp(end, 0, text.length());
                    if (start < end) {
                        CharSequence prefix = prefixByPosition.get(cacheKey(position, child));
                        if (stringBuilder.length() > 0) {
                            stringBuilder.append('\n');
                        }
                        if (prefix != null && prefix.length() > 0 && start == 0) {
                            stringBuilder.append(prefix).append(' ');
                        }
                        stringBuilder.append(text.subSequence(start, end));
                    }
                    if (position == normalized.endPosition && child >= normalized.endChild) {
                        break;
                    }
                }
            }
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

        private void cacheVisibleTextForSelection(NormalizedSelection normalized) {
            if (selectedView != null) {
                cacheTextForView(selectedView, getAdapterPosition((View) selectedView));
            }
            if (parentRecyclerView == null) {
                return;
            }
            for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                View child = parentRecyclerView.getChildAt(i);
                if (!(child instanceof ArticleSelectableView)) {
                    continue;
                }
                int position = getAdapterPosition(child);
                if (position < normalized.startPosition || position > normalized.endPosition) {
                    continue;
                }
                cacheTextForView((ArticleSelectableView) child, position);
            }
        }

        private void drawOverlay(Canvas canvas) {
            if (!isInSelectionMode()) {
                return;
            }
            Endpoint start = endpointFor(true);
            Endpoint end = endpointFor(false);
            handlePaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
            drawHandle(canvas, start, true, startHandleRect);
            drawHandle(canvas, end, false, endHandleRect);
            if (usingFloatingToolbar && actionMode instanceof FloatingActionMode) {
                actionMode.invalidateContentRect();
                ((FloatingActionMode) actionMode).updateViewLocationInWindow();
            }
        }

        private void drawHandle(Canvas canvas, Endpoint endpoint, boolean start, RectF outRect) {
            if (endpoint == null) {
                outRect.setEmpty();
                return;
            }
            float handleSize = AndroidUtilities.dp(22f);
            float progress = handleInterpolator.getInterpolation(handleProgress);
            float x = endpoint.x;
            float y = endpoint.y;
            Path path = selectionHandlePath;
            path.reset();
            boolean drawLeftHandle = start != endpoint.rtl;
            if (drawLeftHandle) {
                canvas.save();
                canvas.translate(x - handleSize, y);
                canvas.scale(progress, progress, handleSize / 2f, handleSize / 2f);
                path.addCircle(handleSize / 2f, handleSize / 2f, handleSize / 2f, Path.Direction.CCW);
                path.addRect(handleSize / 2f, 0, handleSize, handleSize / 2f, Path.Direction.CCW);
                canvas.drawPath(path, handlePaint);
                canvas.restore();
                outRect.set(x - handleSize, y - handleSize, x, y + handleSize);
            } else {
                canvas.save();
                canvas.translate(x, y);
                canvas.scale(progress, progress, handleSize / 2f, handleSize / 2f);
                path.addCircle(handleSize / 2f, handleSize / 2f, handleSize / 2f, Path.Direction.CCW);
                path.addRect(0, 0, handleSize / 2f, handleSize / 2f, Path.Direction.CCW);
                canvas.drawPath(path, handlePaint);
                canvas.restore();
                outRect.set(x, y - handleSize, x + handleSize, y + handleSize);
            }
            outRect.inset(-AndroidUtilities.dp(8f), -AndroidUtilities.dp(8f));
        }

        private boolean onOverlayTouchEvent(MotionEvent event) {
            if (!isInSelectionMode()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (startHandleRect.contains(event.getX(), event.getY())) {
                        startMovingHandle(true, event);
                        return true;
                    }
                    if (endHandleRect.contains(event.getX(), event.getY())) {
                        startMovingHandle(false, event);
                        return true;
                    }
                    movingHandle = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!movingHandle) {
                        return false;
                    }
                    float targetX = event.getX() + movingOffsetX;
                    float targetY = event.getY() + movingOffsetY;
                    updateSelectionFromOverlayPoint(targetX, targetY, movingStartHandle);
                    autoScrollTargetX = targetX;
                    autoScrollTargetY = targetY;
                    updateAutoScroll(event.getY());
                    showMagnifier(targetX, targetY);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (!movingHandle) {
                        return false;
                    }
                    movingHandle = false;
                    movingOffsetX = 0;
                    movingOffsetY = 0;
                    movingDirectionSettling = false;
                    snap = false;
                    stopAutoScroll();
                    hideMagnifier();
                    if (selectedView instanceof View) {
                        showActionMode((View) selectedView);
                    }
                    invalidate();
                    return true;
            }
            return false;
        }

        private void startMovingHandle(boolean startHandle, MotionEvent event) {
            Endpoint endpoint = endpointFor(startHandle);
            movingHandle = true;
            movingStartHandle = startHandle;
            activeEndpointStart = startHandle;
            movingOffsetX = endpoint == null ? 0 : endpoint.x - event.getX();
            movingOffsetY = endpoint == null ? 0 : endpoint.y - event.getY();
            movingDirectionSettling = false;
            snap = false;
            hideActionMode();
        }

        private void showHandleViews() {
            if (handleProgress == 1f || overlay == null) {
                return;
            }
            if (handleAnimator != null) {
                handleAnimator.cancel();
            }
            handleAnimator = ValueAnimator.ofFloat(handleProgress, 1f);
            handleAnimator.setDuration((long) (Math.abs(1f - handleProgress) * 250));
            handleAnimator.addUpdateListener(animation -> {
                handleProgress = (float) animation.getAnimatedValue();
                overlay.invalidate();
            });
            handleAnimator.start();
        }

        private void hideActionMode() {
            if (actionMode != null) {
                finishingActionMode = true;
                actionMode.finish();
                finishingActionMode = false;
                actionMode = null;
            }
        }

        private void updateAutoScroll(float overlayY) {
            // Product: do not auto-scroll the topic list while dragging text selection
            // handles near the edges (NativeDo preference; Telegram keeps this).
            stopAutoScroll();
        }

        private void stopAutoScroll() {
            autoScrolling = false;
            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        }

        private void showMagnifier(float overlayX, float overlayY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || overlay == null) {
                return;
            }
            if (magnifier == null) {
                magnifier = new Magnifier(overlay);
            }
            magnifier.show(overlayX, overlayY - AndroidUtilities.dp(24f));
        }

        private void hideMagnifier() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && magnifier != null) {
                magnifier.dismiss();
                magnifier = null;
            }
        }

        private void updateSelectionFromOverlayPoint(float overlayX, float overlayY, boolean startHandle) {
            Hit hit = findHitForOverlayPoint(overlayX, overlayY);
            if (hit == null) {
                return;
            }
            hit.offset = snapOffsetToWordBoundary(hit, startHandle);
            boolean changed = startHandle
                    ? startViewPosition != hit.position || startViewChildPosition != hit.childPosition || startViewOffset != hit.offset
                    : endViewPosition != hit.position || endViewChildPosition != hit.childPosition || endViewOffset != hit.offset;
            if (startHandle) {
                startViewPosition = hit.position;
                startViewChildPosition = hit.childPosition;
                startViewOffset = hit.offset;
                selectionStart = hit.offset;
            } else {
                endViewPosition = hit.position;
                endViewChildPosition = hit.childPosition;
                endViewOffset = hit.offset;
                selectionEnd = hit.offset;
            }
            selectedView = hit.view;
            selectedIndex = hit.childPosition;
            cacheTextForView(hit.view, hit.position);
            settleMovingHandleDirection();
            syncActiveViewFromEndpoint();
            if (changed && overlay != null) {
                AndroidUtilities.vibrateCursor(overlay);
            }
        }

        private int snapOffsetToWordBoundary(Hit hit, boolean startHandle) {
            cacheTextForView(hit.view, hit.position);
            Layout hitLayout = layoutForHit(hit);
            CharSequence text = hitLayout != null
                    ? hitLayout.getText()
                    : textByPosition.get(cacheKey(hit.position, hit.childPosition));
            if (text == null || text.length() == 0) {
                return hit.offset;
            }
            int offset = clamp(hit.offset, 0, text.length());
            WordBounds customSpanBounds = customSelectionSpanBounds(text, Math.max(0, Math.min(offset, text.length() - 1)), hitLayout);
            if (customSpanBounds != null && offset > customSpanBounds.start && offset < customSpanBounds.end) {
                return startHandle ? customSpanBounds.start : customSpanBounds.end;
            }
            if (startHandle) {
                while (offset > 0 && isInterruptedCharacter(text.charAt(offset - 1))) {
                    offset--;
                    snap = true;
                }
            } else {
                while (offset < text.length() && isInterruptedCharacter(text.charAt(offset))) {
                    offset++;
                    snap = true;
                }
            }
            return offset;
        }

        private Layout layoutForHit(Hit hit) {
            if (hit == null || hit.view == null || hit.childPosition < 0) {
                return null;
            }
            ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
            hit.view.fillTextLayoutBlocks(blocks);
            if (hit.childPosition >= blocks.size()) {
                return null;
            }
            return blocks.get(hit.childPosition).getLayout();
        }

        private WordBounds customSelectionSpanBounds(CharSequence text, int offset) {
            return customSelectionSpanBounds(text, offset, null);
        }

        private WordBounds customSelectionSpanBounds(CharSequence text, int offset, Layout layout) {
            if (!(text instanceof Spanned)) {
                return null;
            }
            Spanned spanned = (Spanned) text;
            int length = spanned.length();
            if (length == 0) {
                return null;
            }
            int start = clamp(offset, 0, length - 1);
            int end = Math.min(length, start + 1);
            TextSelectionBoundsProvider[] spans = spanned.getSpans(start, end, TextSelectionBoundsProvider.class);
            for (TextSelectionBoundsProvider span : spans) {
                int spanStart = spanned.getSpanStart(span);
                int spanEnd = spanned.getSpanEnd(span);
                if (spanStart >= 0 && spanEnd > spanStart && offset >= spanStart && offset < spanEnd) {
                    if (layout != null) {
                        int line = layout.getLineForOffset(spanStart);
                        if (line != layout.getLineForOffset(Math.max(spanStart, spanEnd - 1))) {
                            continue;
                        }
                        RectF bounds = span.getSelectionBounds(
                                layout.getPaint(),
                                spanned,
                                spanStart,
                                spanEnd,
                                layout.getPrimaryHorizontal(spanStart),
                                layout.getLineTop(line),
                                layout.getLineBaseline(line),
                                layout.getLineBottom(line)
                        );
                        if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) {
                            continue;
                        }
                    }
                    return new WordBounds(spanStart, spanEnd);
                }
            }
            return null;
        }

        private WordBounds customSelectionSpanBoundsAtPoint(Layout layout, int line, float x, float y) {
            if (layout == null || !(layout.getText() instanceof Spanned)) {
                return null;
            }
            Spanned spanned = (Spanned) layout.getText();
            int length = spanned.length();
            if (length == 0 || line < 0 || line >= layout.getLineCount()) {
                return null;
            }
            int lineStart = layout.getLineStart(line);
            int lineEnd = layout.getLineEnd(line);
            TextSelectionBoundsProvider[] spans = spanned.getSpans(lineStart, lineEnd, TextSelectionBoundsProvider.class);
            for (TextSelectionBoundsProvider span : spans) {
                int spanStart = spanned.getSpanStart(span);
                int spanEnd = spanned.getSpanEnd(span);
                if (spanStart < 0 || spanEnd <= spanStart) {
                    continue;
                }
                if (line != layout.getLineForOffset(spanStart) || line != layout.getLineForOffset(Math.max(spanStart, spanEnd - 1))) {
                    continue;
                }
                RectF bounds = span.getSelectionBounds(
                        layout.getPaint(),
                        spanned,
                        spanStart,
                        spanEnd,
                        layout.getPrimaryHorizontal(spanStart),
                        layout.getLineTop(line),
                        layout.getLineBaseline(line),
                        layout.getLineBottom(line)
                );
                if (bounds != null && bounds.width() > 0f && bounds.height() > 0f && bounds.contains(x, y)) {
                    return new WordBounds(spanStart, spanEnd);
                }
            }
            return null;
        }

        private void settleMovingHandleDirection() {
            if (comesBefore(startViewPosition, startViewChildPosition, startViewOffset, endViewPosition, endViewChildPosition, endViewOffset)) {
                return;
            }
            int position = startViewPosition;
            int child = startViewChildPosition;
            int offset = startViewOffset;
            startViewPosition = endViewPosition;
            startViewChildPosition = endViewChildPosition;
            startViewOffset = endViewOffset;
            endViewPosition = position;
            endViewChildPosition = child;
            endViewOffset = offset;
            selectionStart = startViewOffset;
            selectionEnd = endViewOffset;
            movingStartHandle = !movingStartHandle;
            activeEndpointStart = !activeEndpointStart;
            movingDirectionSettling = false;
        }

        private void syncActiveViewFromEndpoint() {
            if (!isInSelectionMode()) {
                selectedView = null;
                selectedIndex = -1;
                return;
            }
            NormalizedSelection normalized = normalizedSelection();
            int position = activeEndpointStart ? normalized.startPosition : normalized.endPosition;
            int childPosition = activeEndpointStart ? normalized.startChild : normalized.endChild;
            View view = findAttachedViewByPosition(position);
            if (!(view instanceof ArticleSelectableView)) {
                selectedView = null;
                selectedIndex = -1;
                return;
            }
            ArticleSelectableView selectableView = (ArticleSelectableView) view;
            ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
            selectableView.fillTextLayoutBlocks(blocks);
            if (childPosition < 0 || childPosition >= blocks.size() || blocks.get(childPosition).getLayout() == null) {
                selectedView = null;
                selectedIndex = -1;
                return;
            }
            int textLength = blocks.get(childPosition).getLayout().getText().length();
            selectedView = selectableView;
            selectedIndex = childPosition;
            selectionStart = position == normalized.startPosition && childPosition == normalized.startChild
                    ? clamp(normalized.startOffset, 0, textLength)
                    : 0;
            selectionEnd = position == normalized.endPosition && childPosition == normalized.endChild
                    ? clamp(normalized.endOffset, 0, textLength)
                    : textLength;
            cacheTextForView(selectableView, position);
        }

        private View findAttachedViewByPosition(int position) {
            View view = layoutManager != null ? layoutManager.findViewByPosition(position) : null;
            if (view != null || parentRecyclerView == null) {
                return view;
            }
            for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                View child = parentRecyclerView.getChildAt(i);
                if (getAdapterPosition(child) == position) {
                    return child;
                }
            }
            return null;
        }

        private boolean rangeTouchesSelection(int positionStart, int itemCount) {
            if (!isInSelectionMode() || itemCount <= 0) {
                return false;
            }
            NormalizedSelection normalized = normalizedSelection();
            int positionEnd = positionStart + itemCount - 1;
            return positionStart <= normalized.endPosition && positionEnd >= normalized.startPosition;
        }

        private Hit findHitForOverlayPoint(float overlayX, float overlayY) {
            if (overlay == null || parentRecyclerView == null) {
                return null;
            }
            overlay.getLocationInWindow(overlayLocation);
            float windowX = overlayX + overlayLocation[0];
            float windowY = overlayY + overlayLocation[1];
            Hit bestHit = null;
            int bestScore = Integer.MAX_VALUE;
            for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                View child = parentRecyclerView.getChildAt(i);
                if (!(child instanceof ArticleSelectableView)) {
                    continue;
                }
                child.getLocationInWindow(tmpLocation);
                int localX = (int) (windowX - tmpLocation[0]);
                int localY = (int) (windowY - tmpLocation[1]);
                ArticleSelectableView selectableView = (ArticleSelectableView) child;
                ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
                selectableView.fillTextLayoutBlocks(blocks);
                for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                    TextLayoutBlock block = blocks.get(blockIndex);
                    StaticLayout layout = block.getLayout();
                    if (layout == null || layout.getText().length() == 0) {
                        continue;
                    }
                    int prefixScore = prefixHitScore(block, localX, localY);
                    if (prefixScore != Integer.MAX_VALUE && prefixScore <= bestScore) {
                        bestScore = prefixScore;
                        bestHit = new Hit(selectableView, getAdapterPosition(child), blockIndex, 0);
                        if (prefixScore == 0) {
                            return bestHit;
                        }
                    }
                    int blockY = localY - block.getY() + topOffset;
                    int distance = 0;
                    if (blockY < 0) distance = -blockY;
                    if (blockY > layout.getHeight()) distance = blockY - layout.getHeight();
                    int blockX = localX - block.getX();
                    int distanceX = 0;
                    if (blockX < 0) distanceX = -blockX;
                    if (blockX > layout.getWidth()) distanceX = blockX - layout.getWidth();
                    int score = distance * 10000 + distanceX;
                    if (score > bestScore) {
                        continue;
                    }
                    int x = clamp(blockX, 0, layout.getWidth());
                    int y = clamp(blockY, 0, Math.max(0, layout.getHeight() - 1));
                    int line = layout.getLineForVertical(y);
                    int offset = clamp(layout.getOffsetForHorizontal(line, x), 0, layout.getText().length());
                    bestScore = score;
                    bestHit = new Hit(selectableView, getAdapterPosition(child), blockIndex, offset);
                    if (score == 0) {
                        return bestHit;
                    }
                }
            }
            return bestHit;
        }

        private Endpoint endpointFor(boolean start) {
            NormalizedSelection normalized = normalizedSelection();
            int position = start ? normalized.startPosition : normalized.endPosition;
            int childPosition = start ? normalized.startChild : normalized.endChild;
            int offset = start ? normalized.startOffset : normalized.endOffset;
            View view = layoutManager != null ? layoutManager.findViewByPosition(position) : null;
            if (view == null && parentRecyclerView != null) {
                for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                    View child = parentRecyclerView.getChildAt(i);
                    if (getAdapterPosition(child) == position) {
                        view = child;
                        break;
                    }
                }
            }
            if (!(view instanceof ArticleSelectableView) || overlay == null) {
                return null;
            }
            ArrayList<TextLayoutBlock> blocks = new ArrayList<>();
            ((ArticleSelectableView) view).fillTextLayoutBlocks(blocks);
            if (childPosition < 0 || childPosition >= blocks.size()) {
                return null;
            }
            TextLayoutBlock block = blocks.get(childPosition);
            StaticLayout layout = block.getLayout();
            if (layout == null || layout.getText().length() == 0) {
                return null;
            }
            offset = clamp(offset, 0, layout.getText().length());
            StaticLayout prefixLayout = block.getPrefixLayout();
            if (offset == 0 && prefixLayout != null && prefixLayout.getText().length() > 0) {
                int prefixLine = 0;
                float prefixLeft = Math.min(prefixLayout.getLineLeft(prefixLine), prefixLayout.getLineRight(prefixLine));
                float x = block.getPrefixX() + prefixLeft;
                float y = block.getPrefixY() - topOffset + prefixLayout.getLineBottom(prefixLine);
                view.getLocationInWindow(tmpLocation);
                overlay.getLocationInWindow(overlayLocation);
                return new Endpoint(tmpLocation[0] - overlayLocation[0] + x, tmpLocation[1] - overlayLocation[1] + y, false);
            }
            int line = layout.getLineForOffset(Math.max(0, Math.min(offset, layout.getText().length() - 1)));
            float x = block.getX() + layout.getPrimaryHorizontal(offset);
            float y = block.getY() - topOffset + layout.getLineBottom(line);
            boolean rtl = layout.isRtlCharAt(Math.max(0, Math.min(offset, layout.getText().length() - 1)));
            view.getLocationInWindow(tmpLocation);
            overlay.getLocationInWindow(overlayLocation);
            return new Endpoint(tmpLocation[0] - overlayLocation[0] + x, tmpLocation[1] - overlayLocation[1] + y, rtl);
        }

        private void invalidateSelectedView() {
            if (selectedView instanceof View) {
                ((View) selectedView).invalidate();
            }
            if (parentRecyclerView != null) {
                for (int i = 0; i < parentRecyclerView.getChildCount(); i++) {
                    parentRecyclerView.getChildAt(i).invalidate();
                }
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

        private static class NormalizedSelection {
            final int startPosition;
            final int startChild;
            final int startOffset;
            final int endPosition;
            final int endChild;
            final int endOffset;

            NormalizedSelection(int startPosition, int startChild, int startOffset, int endPosition, int endChild, int endOffset) {
                this.startPosition = startPosition;
                this.startChild = startChild;
                this.startOffset = startOffset;
                this.endPosition = endPosition;
                this.endChild = endChild;
                this.endOffset = endOffset;
            }
        }

        private static class Endpoint {
            final float x;
            final float y;
            final boolean rtl;

            Endpoint(float x, float y, boolean rtl) {
                this.x = x;
                this.y = y;
                this.rtl = rtl;
            }
        }

        private static class Hit {
            final ArticleSelectableView view;
            final int position;
            final int childPosition;
            int offset;

            Hit(ArticleSelectableView view, int position, int childPosition, int offset) {
                this.view = view;
                this.position = position;
                this.childPosition = childPosition;
                this.offset = offset;
            }
        }

        private static class ScalablePath extends Path {
            final ArrayList<RectF> rects = new ArrayList<>();
            int rectsCount;
            float lastBottom;

            @Override
            public void reset() {
                super.reset();
                rectsCount = 0;
                lastBottom = 0;
            }

            @Override
            public void addRect(float left, float top, float right, float bottom, Direction dir) {
                RectF rect;
                if (rectsCount < rects.size()) {
                    rect = rects.get(rectsCount);
                    rect.set(left, top, right, bottom);
                } else {
                    rect = new RectF(left, top, right, bottom);
                    rects.add(rect);
                }
                rectsCount++;
                lastBottom = bottom;
                super.addRect(left, top, right, bottom, dir);
            }

            @Override
            public void addRect(RectF rect, Direction dir) {
                addRect(rect.left, rect.top, rect.right, rect.bottom, dir);
            }
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
