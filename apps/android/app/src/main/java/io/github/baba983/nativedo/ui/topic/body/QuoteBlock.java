/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockBlockquoteCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;

final class QuoteBlock implements EmbeddedPostBodyBlock {
    static final float TEXT_TOP_DP = 8f;
    static final float LINE_LEFT_DP = 18f;
    static final float LINE_RIGHT_DP = 20f;
    static final float TEXT_INSET_AFTER_LINE_DP = 14f;
    static final float SIDE_PADDING_DP = 18f;
    static final float LINE_VERTICAL_INSET_DP = 6f;
    static final float TITLE_BODY_GAP_DP = 8f;
    static final float BODY_BOTTOM_PADDING_DP = 8f;

    private static final class QuoteTitleBlock extends TextLayoutBlock {
        QuoteTitleBlock(String html, PostBodyBuildContext context, TextPaint paint, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
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

    private static final class QuoteTextBlock extends TextLayoutBlock {
        QuoteTextBlock(String html, PostBodyBuildContext context) {
            super(html, context, context.getQuotePaint());
        }

        QuoteTextBlock(String html, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
            super(html, context, context.getQuotePaint(), inlineState, inlineSpoilerStateKey);
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

    private static final class BodyLayout {
        final List<PostBodyBlock> blocks;
        final int[] blockTops;
        final int[] blockHeights;

        BodyLayout(List<PostBodyBlock> blocks) {
            this.blocks = blocks;
            this.blockTops = new int[blocks.size()];
            this.blockHeights = new int[blocks.size()];
        }
    }

    private final class QuoteBlockView extends ViewGroup {
        QuoteBlockView(android.content.Context context) {
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
            drawQuote(canvas, getMeasuredWidth(), true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return QuoteBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }

    private final CookedPostBlock.Quote block;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState state;
    private final int stateIndex;
    private final PostBodyBlockState inlineState;
    private final String stateKey;
    private final Paint quoteLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final QuoteTitleBlock titleBlock;
    private BodyLayout bodyLayout;
    private int bodyTop;
    private int bodyWidth;
    private int textLeft;
    private int blockWidth;
    private int measuredHeight;
    private int activeChildTouchIndex = -1;
    private boolean activeTitleTouch;
    private boolean expanded;
    private boolean loading;
    private String expandedHtml;
    private QuoteBlockView quoteBlockView;

    QuoteBlock(
            CookedPostBlock.Quote block,
            PostBodyBuildContext context,
            PostBodyBlockState state,
            int stateIndex,
            List<GalleryImage> gallery,
            PostBodyBlockState inlineState,
            String stateKey
    ) {
        this.block = block;
        this.context = context;
        this.state = state;
        this.stateIndex = stateIndex;
        this.inlineState = inlineState;
        this.stateKey = stateKey;
        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
        titlePaint.setColor(context.getSecondaryTextColor());
        titlePaint.setTextSize(AndroidUtilities.dp(14f));
        String titleHtml = quoteTitleHtml(block);
        titleBlock = titleHtml != null
                ? new QuoteTitleBlock(
                        titleHtml,
                        context,
                        titlePaint,
                        inlineState,
                        TextLayoutBlock.inlineSpoilerStateKey("quote-title:" + stateKey, titleHtml)
                )
                : null;

        bodyLayout = buildBodyLayout(block.getChildren(), block.getHtml(), gallery, "body");
        if (state != null) {
            String html = state.getExpandedQuotesByIndex().get(stateIndex);
            if (html != null) {
                expandedHtml = html;
                bodyLayout = buildExpandedBodyLayout(html);
                expanded = true;
            }
        }
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        blockWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        int textY = textTopPx();
        int quoteLevel = block.getLevel();
        textLeft = textLeftPx(quoteLevel);
        bodyWidth = Math.max(blockWidth - textLeft - sidePaddingPx(quoteLevel), AndroidUtilities.dp(80f));
        int titleHeight = 0;
        if (titleBlock != null) {
            titleHeight = titleBlock.measure(bodyWidth);
        }
        bodyTop = textY + (titleBlock != null ? titleHeight + titleBodyGapPx() : 0);
        int bodyHeight = measureBody(bodyLayout, bodyWidth);
        measuredHeight = bodyTop + bodyHeight + bodyBottomPaddingPx();
        if (quoteBlockView != null) {
            quoteBlockView.measure(
                    View.MeasureSpec.makeMeasureSpec(blockWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            );
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        drawQuote(canvas, parentWidth, false);
    }

    @Override
    public View embeddedView() {
        if (quoteBlockView == null) {
            quoteBlockView = new QuoteBlockView(context.getHost().getContext());
            addEmbeddedChildren(quoteBlockView);
        }
        return quoteBlockView;
    }

    @Override
    public void layoutEmbeddedView(int left, int top, int width, int height) {
        embeddedView().layout(left, top, left + width, top + height);
        layoutEmbeddedChildren();
    }

    private void drawQuote(Canvas canvas, int parentWidth, boolean skipEmbeddedChildren) {
        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
        titlePaint.setColor(context.getSecondaryTextColor());
        int quoteLevel = block.getLevel();
        canvas.drawRect(
                lineLeftPx(quoteLevel),
                lineVerticalInsetPx(),
                lineRightPx(quoteLevel),
                measuredHeight - lineVerticalInsetPx(),
                quoteLinePaint
        );
        if (quoteLevel > 0) {
            canvas.drawRect(
                    parentLineLeftPx(quoteLevel),
                    0,
                    parentLineRightPx(quoteLevel),
                    measuredHeight,
                    quoteLinePaint
            );
        }
        if (titleBlock != null) {
            canvas.save();
            canvas.translate(textLeft, textTopPx());
            titleBlock.draw(canvas, bodyWidth);
            canvas.restore();
        }
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            PostBodyBlock child = bodyLayout.blocks.get(i);
            if (skipEmbeddedChildren && child instanceof EmbeddedPostBodyBlock) {
                continue;
            }
            canvas.save();
            canvas.translate(textLeft, bodyTop + bodyLayout.blockTops[i]);
            child.draw(canvas, bodyWidth);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        int action = event.getActionMasked();
        if (activeTitleTouch && action != MotionEvent.ACTION_DOWN) {
            boolean handled = titleBlock != null && titleBlock.onTouch(event, localX - textLeft, localY - textTopPx());
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activeTitleTouch = false;
            }
            return handled;
        }
        if (activeChildTouchIndex >= 0 && action != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, localX, localY);
        }
        if (titleBlock != null && localX >= textLeft && localY >= textTopPx() && localY <= bodyTop) {
            boolean handled = titleBlock.onTouch(event, localX - textLeft, localY - textTopPx());
            if (handled && action == MotionEvent.ACTION_DOWN) {
                activeTitleTouch = true;
            }
            if (handled) {
                return true;
            }
        }
        if (localX >= textLeft && localY >= bodyTop && localY <= measuredHeight) {
            float bodyY = localY - bodyTop;
            for (int i = 0; i < bodyLayout.blocks.size(); i++) {
                int top = bodyLayout.blockTops[i];
                int bottom = top + bodyLayout.blockHeights[i];
                if (bodyY >= top && bodyY <= bottom) {
                    boolean handled = bodyLayout.blocks.get(i).onTouch(event, localX - textLeft, bodyY - top);
                    if (handled && action == MotionEvent.ACTION_DOWN) {
                        activeChildTouchIndex = i;
                    }
                    if (handled) {
                        return true;
                    }
                    break;
                }
            }
        }
        if (localX >= 0f
                && localX <= blockWidth
                && localY >= 0f
                && localY <= measuredHeight
                && canExpand()
                && !expanded) {
            if (action == MotionEvent.ACTION_UP) {
                expand();
            }
            return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP;
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (titleBlock != null) {
            titleBlock.fillTextLayoutBlocks(blocks, x + textLeft, y + textTopPx());
        }
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            bodyLayout.blocks.get(i).fillTextLayoutBlocks(
                    blocks,
                    x + textLeft,
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
                result.offset(textLeft, bodyTop + bodyLayout.blockTops[i]);
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
        if (titleBlock != null) {
            titleBlock.onAttachedToWindow();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        if (titleBlock != null) {
            titleBlock.onDetachedFromWindow();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onDetachedFromWindow();
        }
    }

    @Override
    public void applyTheme() {
        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
        titlePaint.setColor(context.getSecondaryTextColor());
        if (titleBlock != null) {
            titleBlock.applyTheme();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.applyTheme();
        }
    }

    private boolean canExpand() {
        return block.getSourceTopicId() != null && block.getSourcePostNumber() != null;
    }

    private void expand() {
        Long topicId = block.getSourceTopicId();
        Integer postNumber = block.getSourcePostNumber();
        if (topicId == null || postNumber == null || loading) {
            return;
        }
        if (expandedHtml != null) {
            setExpandedHtml(expandedHtml);
            return;
        }
        loading = true;
        context.getDelegate().onQuoteExpand(topicId, postNumber, html -> {
            loading = false;
            if (html == null || html.trim().isEmpty()) {
                return Unit.INSTANCE;
            }
            expandedHtml = html;
            if (state != null) {
                state.getExpandedQuotesByIndex().put(stateIndex, html);
            }
            setExpandedHtml(html);
            return Unit.INSTANCE;
        });
    }

    private void setExpandedHtml(String html) {
        if (expanded) {
            return;
        }
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onDetachedFromWindow();
        }
        if (quoteBlockView != null) {
            quoteBlockView.removeAllViews();
        }
        bodyLayout = buildExpandedBodyLayout(html);
        expanded = true;
        if (quoteBlockView != null) {
            addEmbeddedChildren(quoteBlockView);
        }
        if (context.getHost().isAttachedToWindow()) {
            for (PostBodyBlock child : bodyLayout.blocks) {
                child.onAttachedToWindow();
            }
        }
        context.getHost().requestLayout();
        context.getHost().invalidate();
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
                localX - textLeft,
                bodyY - bodyLayout.blockTops[index]
        );
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeChildTouchIndex = -1;
        }
        return handled;
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
        if (quoteBlockView == null) {
            return;
        }
        for (int i = 0; i < bodyLayout.blocks.size(); i++) {
            PostBodyBlock child = bodyLayout.blocks.get(i);
            if (child instanceof EmbeddedPostBodyBlock) {
                ((EmbeddedPostBodyBlock) child).layoutEmbeddedView(
                        textLeft,
                        bodyTop + bodyLayout.blockTops[i],
                        bodyWidth,
                        bodyLayout.blockHeights[i]
                );
            }
        }
    }

    private BodyLayout buildBodyLayout(
            List<CookedPostBlock> children,
            String html,
            List<GalleryImage> gallery,
            String childKey
    ) {
        List<PostBodyBlock> bodyBlocks;
        if (children != null && !children.isEmpty()) {
            bodyBlocks = PostBodyBlockFactory.INSTANCE.build(children, context, inlineState, gallery, stateKey + "." + childKey, false);
        } else {
            bodyBlocks = Collections.<PostBodyBlock>singletonList(new QuoteTextBlock(
                    html,
                    context,
                    inlineState,
                    TextLayoutBlock.inlineSpoilerStateKey("quote-text:" + stateKey + "." + childKey, html)
            ));
        }
        return new BodyLayout(bodyBlocks);
    }

    private BodyLayout buildExpandedBodyLayout(String html) {
        List<CookedPostBlock> blocks = CookedPostBodyParserKt.parseCookedPostBody(html, context.getBaseUrl());
        return buildBodyLayout(
                blocks,
                html,
                CookedPostBodyParserKt.cookedGalleryImages(blocks, context.getBaseUrl()),
                "expanded"
        );
    }

    private int measureBody(BodyLayout layout, int maxWidth) {
        int y = 0;
        for (int i = 0; i < layout.blocks.size(); i++) {
            layout.blockTops[i] = y;
            int height = layout.blocks.get(i).measure(maxWidth);
            layout.blockHeights[i] = height;
            y += height;
        }
        return y;
    }

    static int textTopPx() {
        return AndroidUtilities.dp(TEXT_TOP_DP);
    }

    static int lineLeftPx() {
        return lineLeftPx(0);
    }

    static int lineLeftPx(int level) {
        // Nested quote blocks are drawn inside the parent quote's text column.
        // A level>0 quote therefore starts its own line at local x=0, which
        // lands on ArticleViewer's absolute dp(18 + 14 * level) line.
        return level > 0 ? 0 : AndroidUtilities.dp(LINE_LEFT_DP);
    }

    static int lineRightPx() {
        return lineRightPx(0);
    }

    static int lineRightPx(int level) {
        return lineLeftPx(level) + AndroidUtilities.dp(LINE_RIGHT_DP - LINE_LEFT_DP);
    }

    static int parentLineLeftPx(int level) {
        if (level <= 0) {
            return lineLeftPx(0);
        }
        return -AndroidUtilities.dp(TEXT_INSET_AFTER_LINE_DP * level);
    }

    static int parentLineRightPx(int level) {
        return parentLineLeftPx(level) + AndroidUtilities.dp(LINE_RIGHT_DP - LINE_LEFT_DP);
    }

    static int textInsetAfterLinePx() {
        return AndroidUtilities.dp(TEXT_INSET_AFTER_LINE_DP);
    }

    static int textLeftPx() {
        return textLeftPx(0);
    }

    static int textLeftPx(int level) {
        return lineLeftPx(level) + textInsetAfterLinePx();
    }

    static int sidePaddingPx() {
        return sidePaddingPx(0);
    }

    static int sidePaddingPx(int level) {
        if (level > 0) {
            return 0;
        }
        return AndroidUtilities.dp(SIDE_PADDING_DP);
    }

    static int lineVerticalInsetPx() {
        return AndroidUtilities.dp(LINE_VERTICAL_INSET_DP);
    }

    static int titleBodyGapPx() {
        return AndroidUtilities.dp(TITLE_BODY_GAP_DP);
    }

    static int bodyBottomPaddingPx() {
        return AndroidUtilities.dp(BODY_BOTTOM_PADDING_DP);
    }

    static int bodyBlockSpacingPx() {
        return 0;
    }

    private static String quoteTitleHtml(CookedPostBlock.Quote block) {
        String titleHtml = block.getTitleHtml();
        if (titleHtml != null && !titleHtml.trim().isEmpty()) {
            return titleHtml;
        }
        String title = block.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            return TextUtils.htmlEncode(title);
        }
        return null;
    }
}
