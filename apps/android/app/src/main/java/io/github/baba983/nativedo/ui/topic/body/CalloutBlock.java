/*
 * This file is part of NativeDo.
 *
 * Native Discourse callout renderer adapted to the Telegram-style topic body
 * block pipeline.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.GalleryImage;
import io.github.baba983.nativedo.core.ui.InlineTextImagesKt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;

final class CalloutBlock implements EmbeddedPostBodyBlock {

    private static final class CalloutTitleBlock extends TextLayoutBlock {
        CalloutTitleBlock(String html, PostBodyBuildContext context, TextPaint paint, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
            super(html, context, paint, inlineState, inlineSpoilerStateKey);
        }

        @Override
        protected int topPaddingPx() {
            return 0;
        }

        @Override
        protected int bottomPaddingPx() {
            return 0;
        }
    }

    private static final class CalloutBodyTextBlock extends TextLayoutBlock {
        CalloutBodyTextBlock(String html, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
            super(html, context, context.getQuotePaint(), inlineState, inlineSpoilerStateKey);
        }
    }

    private static final class BodyLayout {
        final List<PostBodyBlock> blocks;
        final int[] blockTops;
        final int[] blockHeights;

        BodyLayout(List<PostBodyBlock> blocks) {
            this.blocks = blocks;
            blockTops = new int[blocks.size()];
            blockHeights = new int[blocks.size()];
        }
    }

    private final class CalloutBlockView extends ViewGroup {
        CalloutBlockView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), measuredHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutEmbeddedChildren();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawCallout(canvas, getMeasuredWidth(), true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return CalloutBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }

    private final CookedPostBlock.Callout block;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState inlineState;
    private final String stateKey;
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF backgroundRect = new RectF();
    private final Drawable calloutIconDrawable;
    private final CalloutTitleBlock titleBlock;
    private final BodyLayout bodyLayout;
    private int contentLeft;
    private int titleLeft;
    private int bodyTop;
    private int bodyWidth;
    private int titleWidth;
    private int blockWidth;
    private int measuredHeight;
    private int activeChildTouchIndex = -1;
    private boolean activeTitleTouch;
    private CalloutBlockView calloutBlockView;

    CalloutBlock(
            CookedPostBlock.Callout block,
            PostBodyBuildContext context,
            List<GalleryImage> gallery,
            PostBodyBlockState inlineState,
            String stateKey
    ) {
        this.block = block;
        this.context = context;
        this.inlineState = inlineState;
        this.stateKey = stateKey;
        titlePaint.setColor(context.getTextColor());
        titlePaint.setTextSize(AndroidUtilities.dp(14f));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        int color = parseColor(block.getColor(), context.getAccentColor());
        linePaint.setColor(color);
        linePaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Theme.multAlpha(color, 0.10f));
        calloutIconDrawable = InlineTextImagesKt.renderInlineSvgIconDrawable(
                context.getSvgSpriteMarkup(),
                block.getIcon(),
                block.getColor()
        );
        String titleHtml = calloutTitleHtml(block);
        titleBlock = new CalloutTitleBlock(
                titleHtml,
                context,
                titlePaint,
                inlineState,
                TextLayoutBlock.inlineSpoilerStateKey("callout-title:" + stateKey, titleHtml)
        );
        bodyLayout = buildBodyLayout(block, context, gallery, inlineState, stateKey);
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        blockWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        contentLeft = AndroidUtilities.dp(18f + 12f);
        bodyWidth = Math.max(blockWidth - contentLeft - AndroidUtilities.dp(18f), AndroidUtilities.dp(80f));
        int iconSlot = calloutIconDrawable != null ? calloutIconSizePx() + AndroidUtilities.dp(6f) : 0;
        titleLeft = contentLeft + iconSlot;
        titleWidth = Math.max(bodyWidth - iconSlot, AndroidUtilities.dp(80f));
        int titleY = AndroidUtilities.dp(10f);
        int titleHeight = titleBlock.measure(titleWidth);
        int bodyHeight = measureBody(bodyLayout, bodyWidth);
        bodyTop = titleY + titleHeight + (bodyHeight > 0 ? AndroidUtilities.dp(8f) : 0);
        measuredHeight = bodyTop + bodyHeight + AndroidUtilities.dp(10f);
        if (calloutBlockView != null) {
            calloutBlockView.measure(
                    View.MeasureSpec.makeMeasureSpec(blockWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            );
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        drawCallout(canvas, parentWidth, false);
    }

    @Override
    public View embeddedView() {
        if (calloutBlockView == null) {
            calloutBlockView = new CalloutBlockView(context.getHost().getContext());
            addEmbeddedChildren(calloutBlockView);
        }
        return calloutBlockView;
    }

    @Override
    public void layoutEmbeddedView(int left, int top, int width, int height) {
        embeddedView().layout(left, top, left + width, top + height);
        layoutEmbeddedChildren();
    }

    private void drawCallout(Canvas canvas, int parentWidth, boolean skipEmbeddedChildren) {
        backgroundRect.set(
                AndroidUtilities.dp(18f),
                AndroidUtilities.dp(4f),
                blockWidth - AndroidUtilities.dp(18f),
                measuredHeight - AndroidUtilities.dp(4f)
        );
        canvas.drawRoundRect(backgroundRect, AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), backgroundPaint);
        canvas.drawRoundRect(
                backgroundRect.left,
                backgroundRect.top,
                backgroundRect.left + AndroidUtilities.dp(3f),
                backgroundRect.bottom,
                AndroidUtilities.dp(2f),
                AndroidUtilities.dp(2f),
                linePaint
        );

        canvas.save();
        canvas.translate(titleLeft, AndroidUtilities.dp(10f));
        titleBlock.draw(canvas, titleWidth);
        canvas.restore();
        drawCalloutIcon(canvas);

        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            PostBodyBlock child = bodyLayout.blocks.get(i);
            if (skipEmbeddedChildren && child instanceof EmbeddedPostBodyBlock) {
                continue;
            }
            canvas.save();
            canvas.translate(contentLeft, bodyTop + bodyLayout.blockTops[i]);
            child.draw(canvas, bodyWidth);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        int action = event.getActionMasked();
        if (activeTitleTouch && action != MotionEvent.ACTION_DOWN) {
            boolean handled = titleBlock.onTouch(event, localX - titleLeft, localY - AndroidUtilities.dp(10f));
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activeTitleTouch = false;
            }
            return handled;
        }
        if (activeChildTouchIndex >= 0 && action != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, localX, localY);
        }
        if (localX >= titleLeft && localY >= AndroidUtilities.dp(10f) && localY <= bodyTop) {
            boolean handled = titleBlock.onTouch(event, localX - titleLeft, localY - AndroidUtilities.dp(10f));
            if (handled && action == MotionEvent.ACTION_DOWN) {
                activeTitleTouch = true;
            }
            if (handled) {
                return true;
            }
        }
        if (localX >= contentLeft && localY >= bodyTop && localY <= measuredHeight) {
            float bodyY = localY - bodyTop;
            for (int i = 0; i < bodyLayout.blocks.size(); i++) {
                int top = bodyLayout.blockTops[i];
                int bottom = top + bodyLayout.blockHeights[i];
                if (bodyY >= top && bodyY <= bottom) {
                    boolean handled = bodyLayout.blocks.get(i).onTouch(event, localX - contentLeft, bodyY - top);
                    if (handled && action == MotionEvent.ACTION_DOWN) {
                        activeChildTouchIndex = i;
                    }
                    return handled;
                }
            }
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        titleBlock.fillTextLayoutBlocks(blocks, x + titleLeft, y + AndroidUtilities.dp(10f));
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            bodyLayout.blocks.get(i).fillTextLayoutBlocks(
                    blocks,
                    x + contentLeft,
                    y + bodyTop + bodyLayout.blockTops[i]
            );
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            RectF bounds = bodyLayout.blocks.get(i).imageBoundsInBlock(imageUrl);
            if (bounds != null) {
                RectF result = new RectF(bounds);
                result.offset(contentLeft, bodyTop + bodyLayout.blockTops[i]);
                return result;
            }
        }
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        for (PostBodyBlock child : bodyLayout.blocks) {
            ImageReceiver receiver = child.imageReceiverForUrl(imageUrl);
            if (receiver != null) {
                return receiver;
            }
        }
        return null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        ImageReceiver receiver = imageReceiverForUrl(imageUrl);
        return receiver != null ? receiver.getBitmapSafe() : null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        ImageReceiver receiver = imageReceiverForUrl(imageUrl);
        if (receiver == null) {
            return null;
        }
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }

    @Override
    public void onAttachedToWindow() {
        titleBlock.onAttachedToWindow();
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        titleBlock.onDetachedFromWindow();
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onDetachedFromWindow();
        }
    }

    @Override
    public void applyTheme() {
        titlePaint.setColor(context.getTextColor());
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.applyTheme();
        }
    }

    private boolean dispatchActiveChildTouch(MotionEvent event, float localX, float localY) {
        int index = activeChildTouchIndex;
        if (index < 0 || index >= bodyLayout.blocks.size()) {
            activeChildTouchIndex = -1;
            return false;
        }
        float bodyY = localY - bodyTop;
        boolean handled = bodyLayout.blocks.get(index).onTouch(
                event,
                localX - contentLeft,
                bodyY - bodyLayout.blockTops[index]
        );
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeChildTouchIndex = -1;
        }
        return handled;
    }

    private static BodyLayout buildBodyLayout(
            CookedPostBlock.Callout block,
            PostBodyBuildContext context,
            List<GalleryImage> gallery,
            PostBodyBlockState inlineState,
            String stateKey
    ) {
        List<PostBodyBlock> bodyBlocks;
        if (block.getChildren() != null && !block.getChildren().isEmpty()) {
            bodyBlocks = PostBodyBlockFactory.INSTANCE.build(block.getChildren(), context, inlineState, gallery, stateKey + ".children", false);
        } else {
            bodyBlocks = Collections.<PostBodyBlock>singletonList(new CalloutBodyTextBlock(
                    block.getHtml(),
                    context,
                    inlineState,
                    TextLayoutBlock.inlineSpoilerStateKey("callout-text:" + stateKey, block.getHtml())
            ));
        }
        return new BodyLayout(bodyBlocks);
    }

    private static int measureBody(BodyLayout layout, int maxWidth) {
        int y = 0;
        for (int i = 0; i < layout.blocks.size(); i++) {
            layout.blockTops[i] = y;
            int height = layout.blocks.get(i).measure(maxWidth);
            layout.blockHeights[i] = height;
            y += height;
        }
        return y;
    }

    private void addEmbeddedChildren(ViewGroup parent) {
        for (PostBodyBlock child : bodyLayout.blocks) {
            if (child instanceof EmbeddedPostBodyBlock) {
                View view = ((EmbeddedPostBodyBlock) child).embeddedView();
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                parent.addView(view);
            }
        }
    }

    private void layoutEmbeddedChildren() {
        if (calloutBlockView == null) {
            return;
        }
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            PostBodyBlock child = bodyLayout.blocks.get(i);
            if (child instanceof EmbeddedPostBodyBlock) {
                ((EmbeddedPostBodyBlock) child).layoutEmbeddedView(
                        contentLeft,
                        bodyTop + bodyLayout.blockTops[i],
                        bodyWidth,
                        bodyLayout.blockHeights[i]
                );
            }
        }
    }

    private static String calloutTitleHtml(CookedPostBlock.Callout block) {
        String titleHtml = block.getTitleHtml();
        if (titleHtml == null || titleHtml.trim().isEmpty()) {
            titleHtml = TextUtils.htmlEncode(block.getType());
        }
        return titleHtml;
    }

    private void drawCalloutIcon(Canvas canvas) {
        if (calloutIconDrawable == null) {
            return;
        }
        int size = calloutIconSizePx();
        int baseline = AndroidUtilities.dp(10f) + titleBlock.firstTextBaselinePx();
        float centerY = baseline + (titlePaint.getFontMetrics().ascent + titlePaint.getFontMetrics().descent) / 2f;
        int left = contentLeft;
        int top = Math.round(centerY - size / 2f);
        calloutIconDrawable.setBounds(left, top, left + size, top + size);
        calloutIconDrawable.draw(canvas);
    }

    private int calloutIconSizePx() {
        return Math.max(1, Math.round(titlePaint.getTextSize() * 0.92f));
    }

    private static int parseColor(String rawColor, int fallback) {
        if (rawColor == null || rawColor.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Color.parseColor(rawColor);
        } catch (IllegalArgumentException ignore) {
            return fallback;
        }
    }
}
