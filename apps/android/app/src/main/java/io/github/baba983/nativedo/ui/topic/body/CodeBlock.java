/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockPreformattedCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;

final class CodeBlock implements EmbeddedPostBodyBlock {
    static final float BACKGROUND_VERTICAL_INSET_DP = 8f;
    static final float TEXT_HORIZONTAL_MARGIN_DP = 16f;
    static final float TEXT_VERTICAL_MARGIN_DP = 12f;
    static final float TEXT_CONTAINER_END_PADDING_DP = 32f;

    private final String code;
    private final PostBodyBuildContext context;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int textContainerEndPadding = textContainerEndPaddingPx();
    private final HorizontalScrollView scrollView;
    private final View textContainer;
    private final int touchSlop;
    private StaticLayout layout;
    private int textWidth;
    private int viewportWidth;
    private int measuredHeight;
    private float fallbackDownX;
    private float fallbackDownY;
    private float fallbackLastX;
    private boolean fallbackTouchActive;
    private boolean fallbackDragging;

    CodeBlock(String code, PostBodyBuildContext context) {
        this.code = code;
        this.context = context;
        int accent = context.getAccentColor();
        backgroundPaint.setColor(Color.argb(20, Color.red(accent), Color.green(accent), Color.blue(accent)));
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
        scrollView = new HorizontalScrollView(context.getHost().getContext()) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (textContainer.getMeasuredWidth() > getMeasuredWidth()) {
                    if (CodeBlock.this.context.getHost().getParent() != null) {
                        CodeBlock.this.context.getHost().getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                boolean handled = super.onTouchEvent(ev);
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (CodeBlock.this.context.getHost().getParent() != null) {
                        CodeBlock.this.context.getHost().getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return handled;
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                CodeBlock.this.context.getSelectionGeometryInvalidator().invoke();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRect(0f, backgroundVerticalInsetPx(), getWidth(), getHeight() - backgroundVerticalInsetPx(), backgroundPaint);
                super.onDraw(canvas);
            }
        };
        scrollView.setWillNotDraw(false);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setPadding(0, backgroundVerticalInsetPx(), 0, backgroundVerticalInsetPx());

        textContainer = new View(context.getHost().getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = layout != null ? layout.getHeight() : 1;
                setMeasuredDimension(Math.max(textWidth + textContainerEndPadding, 1), Math.max(height, 1));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (layout != null) {
                    layout.draw(canvas);
                }
            }
        };
        HorizontalScrollView.LayoutParams layoutParams = new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        );
        layoutParams.leftMargin = layoutParams.rightMargin = textHorizontalMarginPx();
        layoutParams.topMargin = layoutParams.bottomMargin = textVerticalMarginPx();
        scrollView.addView(textContainer, layoutParams);
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        viewportWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        String source = code.trim().isEmpty() ? " " : code;
        int longestLineWidth = 1;
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            longestLineWidth = Math.max(longestLineWidth, (int) Math.ceil(context.getMonoPaint().measureText(line)));
        }
        textWidth = Math.max(longestLineWidth + 1, viewportWidth - maxFallbackScrollExtraPx());
        layout = ArticleTextLayout.builder(source, context.getMonoPaint(), textWidth)
                .build();
        textContainer.requestLayout();
        scrollView.measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        measuredHeight = scrollView.getMeasuredHeight();
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (layout == null || measuredHeight <= 0) {
            return;
        }
        int saveCount = canvas.save();
        canvas.drawRect(0f, backgroundVerticalInsetPx(), parentWidth, measuredHeight - backgroundVerticalInsetPx(), backgroundPaint);
        canvas.clipRect(0f, 0f, parentWidth, measuredHeight);
        canvas.translate(textHorizontalMarginPx() - scrollView.getScrollX(), textTopPx());
        layout.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public View embeddedView() {
        return scrollView;
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        int maxScroll = maxFallbackScroll();
        if (maxScroll <= 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fallbackTouchActive = true;
                fallbackDragging = false;
                fallbackDownX = localX;
                fallbackDownY = localY;
                fallbackLastX = localX;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!fallbackTouchActive) {
                    return false;
                }
                float dxTotal = localX - fallbackDownX;
                float dyTotal = localY - fallbackDownY;
                if (!fallbackDragging) {
                    if (Math.abs(dyTotal) > touchSlop && Math.abs(dyTotal) > Math.abs(dxTotal)) {
                        fallbackTouchActive = false;
                        return false;
                    }
                    if (Math.abs(dxTotal) <= touchSlop) {
                        return true;
                    }
                    fallbackDragging = true;
                    setParentInterceptBlocked(true);
                }
                int nextScroll = clamp(Math.round(scrollView.getScrollX() + (fallbackLastX - localX)), 0, maxScroll);
                if (nextScroll != scrollView.getScrollX()) {
                    scrollView.scrollTo(nextScroll, 0);
                    context.getSelectionGeometryInvalidator().invoke();
                    context.getHost().invalidate();
                }
                fallbackLastX = localX;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasActive = fallbackTouchActive;
                fallbackTouchActive = false;
                fallbackDragging = false;
                setParentInterceptBlocked(false);
                return wasActive;
            default:
                return fallbackTouchActive;
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (layout != null) {
            blocks.add(new PostSelectionLayoutBlock(
                    layout,
                    x + textContainerLeftForSelection() - scrollView.getScrollX(),
                    y + textContainerTopForSelection(),
                    0,
                    null,
                    null,
                    x,
                    y,
                    selectionClipBounds(x, y)
            ));
        }
    }

    private Rect selectionClipBounds(int x, int y) {
        return new Rect(x, y, x + viewportWidth, y + measuredHeight);
    }

    private int textContainerLeftForSelection() {
        int left = textContainer.getLeft();
        return left != 0 ? left : textHorizontalMarginPx();
    }

    private int textContainerTopForSelection() {
        int top = textContainer.getTop();
        return top != 0 ? top : textTopPx();
    }

    private int maxFallbackScroll() {
        return Math.max(textWidth + maxFallbackScrollExtraPx() - viewportWidth, 0);
    }

    private void setParentInterceptBlocked(boolean blocked) {
        if (context.getHost().getParent() != null) {
            context.getHost().getParent().requestDisallowInterceptTouchEvent(blocked);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static int backgroundVerticalInsetPx() {
        return AndroidUtilities.dp(BACKGROUND_VERTICAL_INSET_DP);
    }

    static int textHorizontalMarginPx() {
        return AndroidUtilities.dp(TEXT_HORIZONTAL_MARGIN_DP);
    }

    static int textVerticalMarginPx() {
        return AndroidUtilities.dp(TEXT_VERTICAL_MARGIN_DP);
    }

    static int textContainerEndPaddingPx() {
        return AndroidUtilities.dp(TEXT_CONTAINER_END_PADDING_DP);
    }

    static int textTopPx() {
        return backgroundVerticalInsetPx() + textVerticalMarginPx();
    }

    static int maxFallbackScrollExtraPx() {
        return textHorizontalMarginPx() * 2 + textContainerEndPaddingPx();
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        return null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        return null;
    }

    @Override
    public void onAttachedToWindow() {
    }

    @Override
    public void onDetachedFromWindow() {
        fallbackTouchActive = false;
        fallbackDragging = false;
        setParentInterceptBlocked(false);
    }

    @Override
    public void applyTheme() {
        textContainer.invalidate();
        scrollView.invalidate();
    }
}
