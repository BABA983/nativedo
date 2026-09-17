/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockPreformattedCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
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
    private final FrameLayout cellView;
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
        backgroundPaint.setColor(preformattedBackgroundColor());
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();

        scrollView = new HorizontalScrollView(context.getHost().getContext()) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                boolean intercept = super.onInterceptTouchEvent(ev);
                if (scrollContentWiderThanViewport() && intercept) {
                    setParentInterceptBlocked(true);
                }
                return intercept;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!scrollContentWiderThanViewport()) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                CodeBlock.this.context.getSelectionGeometryInvalidator().invoke();
            }
        };
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setPadding(0, backgroundVerticalInsetPx(), 0, backgroundVerticalInsetPx());

        textContainer = new View(context.getHost().getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = layout != null ? layout.getHeight() : 1;
                int contentWidth = layout != null ? contentWidthFromLayout(layout) : textWidth;
                setMeasuredDimension(Math.max(contentWidth + textContainerEndPadding, 1), Math.max(height, 1));
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

        cellView = new FrameLayout(context.getHost().getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = View.MeasureSpec.getSize(widthMeasureSpec);
                scrollView.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                setMeasuredDimension(width, scrollView.getMeasuredHeight());
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, getMeasuredWidth(), scrollView.getMeasuredHeight());
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRect(
                        0f,
                        backgroundVerticalInsetPx(),
                        getMeasuredWidth(),
                        getMeasuredHeight() - backgroundVerticalInsetPx(),
                        backgroundPaint
                );
            }
        };
        cellView.setWillNotDraw(false);
        cellView.addView(
                scrollView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        viewportWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        String source = code.trim().isEmpty() ? " " : code;
        int layoutBuildWidth = AndroidUtilities.dp(5000f);
        layout = ArticleTextLayout.builder(source, context.getMonoPaint(), layoutBuildWidth)
                .build();
        textWidth = contentWidthFromLayout(layout);
        textContainer.requestLayout();
        cellView.measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        measuredHeight = cellView.getMeasuredHeight();
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (layout == null || measuredHeight <= 0) {
            return;
        }
        int saveCount = canvas.save();
        canvas.drawRect(
                0f,
                backgroundVerticalInsetPx(),
                parentWidth,
                measuredHeight - backgroundVerticalInsetPx(),
                backgroundPaint
        );
        canvas.clipRect(0f, 0f, parentWidth, measuredHeight);
        canvas.translate(textHorizontalMarginPx() - scrollView.getScrollX(), textTopPx());
        layout.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public View embeddedView() {
        return cellView;
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

    private boolean scrollContentWiderThanViewport() {
        return textContainer.getMeasuredWidth() > scrollView.getMeasuredWidth();
    }

    private void setParentInterceptBlocked(boolean blocked) {
        View parent = context.getHost();
        while (parent != null) {
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).requestDisallowInterceptTouchEvent(blocked);
            }
            if (!(parent.getParent() instanceof View)) {
                break;
            }
            parent = (View) parent.getParent();
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

    static int contentWidthFromLayout(StaticLayout layout) {
        int width = 1;
        for (int i = 0, count = layout.getLineCount(); i < count; i++) {
            width = Math.max(width, (int) Math.ceil(layout.getLineWidth(i)));
        }
        return width;
    }

    static int preformattedBackgroundColor() {
        return Theme.getColor(Theme.key_chat_inArticleCodeBackground);
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
        backgroundPaint.setColor(preformattedBackgroundColor());
        textContainer.invalidate();
        cellView.invalidate();
    }
}