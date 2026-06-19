/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockDetailsCell
 * and TL_pageBlockDetails expansion model.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import org.jsoup.Jsoup;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class DetailsBlock implements EmbeddedPostBodyBlock {

    static final float TITLE_TEXT_X_DP = 44f + 6f;
    static final float MIN_HEADER_HEIGHT_DP = 39f;
    static final float BOTTOM_CELL_EXTRA_HEIGHT_DP = 4f;
    static final float CHILD_LEVEL_INSET_DP = 14f;
    static final float LEVEL_LINE_LEFT_DP = 18f;
    static final float LEVEL_LINE_RIGHT_DP = 20f;
    static final float CHILD_TEXT_INSET_AFTER_LINE_DP = 14f;
    static final float CHILD_RIGHT_INSET_DP = 18f;
    static final float TITLE_WIDTH_INSET_DP = 36f + 16f;
    static final float TITLE_BASELINE_EXTRA_HEIGHT_DP = 21f;
    static final float ARROW_LEFT_DP = 18f;
    static final float ARROW_SIZE_DP = 13f;

    private static final class DetailsChildLayout {
        final List<PostBodyBlock> blocks;
        final int[] blockLefts;
        final int[] blockTops;
        final int[] blockWidths;
        final int[] blockHeights;

        DetailsChildLayout(List<PostBodyBlock> blocks) {
            this.blocks = blocks;
            blockLefts = new int[blocks.size()];
            blockTops = new int[blocks.size()];
            blockWidths = new int[blocks.size()];
            blockHeights = new int[blocks.size()];
        }
    }

    private final class DetailsBlockView extends ViewGroup {
        DetailsBlockView(android.content.Context context) {
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
            drawDetails(canvas, getMeasuredWidth(), true);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (currentBlock.open) {
                super.dispatchDraw(canvas);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return DetailsBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }

    private final PostBodyBuildContext context;
    private final PostBodyBlockState state;
    private final int stateIndex;
    private final PostBodyBlockState inlineState;
    private final String stateKey;
    private final String titleHtml;
    private final String titleSpoilerStateKey;
    private final boolean defaultOpen;
    private final TLRPC.TL_pageBlockDetails currentBlock;
    private final DetailsChildLayout childLayout;
    private final AnimatedArrowDrawable arrow;
    private final TextPaint titlePaint;
    private final PostBodyTextBuilder textBuilder;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ArrayList<SpoilerEffect> titleSpoilers = new ArrayList<>();
    private final Stack<SpoilerEffect> titleSpoilersPool = new Stack<>();
    private final Path titleSpoilerBoundsPath = new Path();
    private final Path titleSpoilerRipplePath = new Path();
    private final ArticleLinkPressState titleLinkPressState = new ArticleLinkPressState();
    private final int touchSlop;
    private BuiltText titleBuiltText;
    private BuiltText.Handle titleHandle;
    private StaticLayout textLayout;
    private DetailsBlockView detailsBlockView;
    private int textY = AndroidUtilities.dp(11) + 1;
    private int measuredWidth;
    private int measuredHeight;
    private int headerHeight = minHeaderHeightPx() + 1;
    private int activeChildTouchIndex = -1;
    private float downX;
    private float downY;
    private boolean pressedHeader;
    private boolean pressedTitleSpoiler;
    private boolean titleSpoilersRevealed;

    DetailsBlock(CookedPostBlock.Details block, PostBodyBuildContext context, PostBodyBlockState state, int stateIndex, List<GalleryImage> gallery, PostBodyBlockState inlineState, String stateKey) {
        this.context = context;
        this.state = state;
        this.stateIndex = stateIndex;
        this.inlineState = inlineState;
        this.stateKey = stateKey;
        titleHtml = detailsTitleHtml(block);
        defaultOpen = block.getOpen();
        titleSpoilerStateKey = TextLayoutBlock.inlineSpoilerStateKey("details-summary:" + stateKey, titleHtml);
        titleSpoilersRevealed = inlineState != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(titleSpoilerStateKey);
        currentBlock = toPageBlockDetails(block);
        currentBlock.open = initialOpenState(state, stateIndex, defaultOpen);
        childLayout = buildChildLayout(block, context, gallery, inlineState, stateKey);
        arrow = new AnimatedArrowDrawable(context.getSecondaryTextColor(), true);
        arrow.setAnimationProgress(currentBlock.open ? 0.0f : 1.0f);
        arrow.setCallback(context.getHost());
        titlePaint = new TextPaint(context.getQuotePaint());
        titlePaint.setColor(context.getTextColor());
        textBuilder = new PostBodyTextBuilder(context);
        dividerPaint.setColor(context.getDividerColor());
        levelLinePaint.setColor(context.getDividerColor());
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        int width = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        measuredWidth = width;
        if (titleHandle != null) {
            titleHandle.detach();
            titleHandle = null;
        }
        titleBuiltText = textBuilder.build(titleHtml, titlePaint);
        CharSequence title = titleBuiltText.getSpannable();
        textLayout = ArticleTextLayout.builder(title, titlePaint, width - titleWidthInsetPx())
                .build();
        if (context.getHost().isAttachedToWindow()) {
            titleHandle = titleBuiltText.attach(context.getHost());
        }
        SpoilerTextLayout.collect(context.getHost(), textLayout, titleSpoilersPool, titleSpoilers, titleSpoilersRevealed);
        headerHeight = Math.max(minHeaderHeightPx(), titleBaselineExtraHeightPx() + textLayout.getHeight()) + 1;
        textY = (textLayout.getHeight() + titleBaselineExtraHeightPx() - textLayout.getHeight()) / 2;

        int height = headerHeight;
        if (currentBlock.open) {
            int childrenHeight = measureChildren(childLayout, width);
            height += childrenHeight;
            if (childrenHeight > 0) {
                height += bottomCellHeightPx();
            }
        }
        measuredHeight = height;
        rect.set(0f, 0f, width, measuredHeight);
        if (detailsBlockView != null) {
            detailsBlockView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            );
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        drawDetails(canvas, parentWidth, false);
    }

    @Override
    public View embeddedView() {
        if (detailsBlockView == null) {
            detailsBlockView = new DetailsBlockView(context.getHost().getContext());
            addEmbeddedChildren(detailsBlockView);
        }
        return detailsBlockView;
    }

    @Override
    public void layoutEmbeddedView(int left, int top, int width, int height) {
        embeddedView().layout(left, top, left + width, top + height);
        layoutEmbeddedChildren();
    }

    private void drawDetails(Canvas canvas, int parentWidth, boolean skipEmbeddedChildren) {
        if (textLayout == null) {
            return;
        }
        canvas.save();
        canvas.translate(arrowLeftPx(), (headerHeight - arrowSizePx() - 1) / 2f);
        arrow.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(titleTextXPx(), textY);
        titleLinkPressState.draw(canvas, context.getHost()::invalidate);
        SpoilerTextLayout.draw(canvas, textLayout, titleSpoilers, titleSpoilerBoundsPath, titleSpoilerRipplePath);
        canvas.restore();

        int y = headerHeight - 1;
        canvas.drawLine(0, y, measuredWidth, y, dividerPaint);

        if (!currentBlock.open) {
            return;
        }
        int childrenBottom = measuredHeight - bottomCellHeightPx();
        if (!childLayout.blocks.isEmpty()) {
            canvas.drawRect(
                    levelLineLeftPx(),
                    headerHeight,
                    levelLineRightPx(),
                    childrenBottom,
                    levelLinePaint
            );
        }
        drawChildren(canvas, childLayout, headerHeight, measuredWidth, skipEmbeddedChildren);
        if (!childLayout.blocks.isEmpty()) {
            canvas.drawLine(0, childrenBottom, measuredWidth, childrenBottom, dividerPaint);
        }
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        if (activeChildTouchIndex >= 0 && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, localX, localY);
        }
        if (!rect.contains(localX, localY)) {
            return false;
        }
        if (localY <= headerHeight) {
            return handleHeaderTouch(event, localX, localY);
        }
        if (!currentBlock.open) {
            return false;
        }
        float childY = localY - headerHeight;
        for (int i = 0; i < childLayout.blocks.size(); i++) {
            int left = childLayout.blockLefts[i];
            int top = childLayout.blockTops[i];
            int width = childLayout.blockWidths[i];
            int height = childLayout.blockHeights[i];
            if (localX >= left && localX <= left + width && childY >= top && childY <= top + height) {
                boolean handled = childLayout.blocks.get(i).onTouch(event, localX - left, childY - top);
                if (handled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    activeChildTouchIndex = i;
                }
                return handled;
            }
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (textLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(textLayout, x + titleTextXPx(), y + textY, 0, null));
        }
        if (!currentBlock.open) {
            return;
        }
        for (int i = 0; i < childLayout.blocks.size(); i++) {
            childLayout.blocks.get(i).fillTextLayoutBlocks(
                    blocks,
                    x + childLayout.blockLefts[i],
                    y + headerHeight + childLayout.blockTops[i]
            );
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        if (!currentBlock.open) {
            return null;
        }
        for (int i = 0; i < childLayout.blocks.size(); i++) {
            RectF bounds = childLayout.blocks.get(i).imageBoundsInBlock(imageUrl);
            if (bounds != null) {
                RectF result = new RectF(bounds);
                result.offset(childLayout.blockLefts[i], headerHeight + childLayout.blockTops[i]);
                return result;
            }
        }
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        if (!currentBlock.open) {
            return null;
        }
        for (PostBodyBlock child : childLayout.blocks) {
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
        if (titleHandle == null && titleBuiltText != null) {
            titleHandle = titleBuiltText.attach(context.getHost());
        }
        for (int i = 0; i < titleSpoilers.size(); i++) {
            titleSpoilers.get(i).setParentView(context.getHost());
        }
        if (currentBlock.open) {
            for (PostBodyBlock child : childLayout.blocks) {
                child.onAttachedToWindow();
            }
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (titleHandle != null) {
            titleHandle.detach();
            titleHandle = null;
        }
        pressedHeader = false;
        pressedTitleSpoiler = false;
        titleLinkPressState.reset();
        activeChildTouchIndex = -1;
        for (PostBodyBlock child : childLayout.blocks) {
            child.onDetachedFromWindow();
        }
    }

    @Override
    public void applyTheme() {
        arrow.setColor(context.getSecondaryTextColor());
        titlePaint.setColor(context.getTextColor());
        dividerPaint.setColor(context.getDividerColor());
        levelLinePaint.setColor(context.getDividerColor());
        for (PostBodyBlock child : childLayout.blocks) {
            child.applyTheme();
        }
    }

    TLRPC.TL_pageBlockDetails getCurrentBlock() {
        return currentBlock;
    }

    private boolean handleHeaderTouch(MotionEvent event, float localX, float localY) {
        float titleX = localX - titleTextXPx();
        float titleY = localY - textY;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (titleLinkPressState.onTouch(event, titleX, titleY, textLayout, titleBuiltText, context, touchSlop, context.getHost()::invalidate)) {
                    return true;
                }
                if (SpoilerTextLayout.contains(titleSpoilers, titleX, titleY)) {
                    pressedTitleSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                }
                pressedHeader = true;
                downX = localX;
                downY = localY;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (titleLinkPressState.isPressed()) {
                    return titleLinkPressState.onTouch(event, titleX, titleY, textLayout, titleBuiltText, context, touchSlop, context.getHost()::invalidate);
                }
                if (pressedTitleSpoiler) {
                    if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                        pressedTitleSpoiler = false;
                        return false;
                    }
                    return true;
                }
                if (pressedHeader && (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop)) {
                    pressedHeader = false;
                    return false;
                }
                return pressedHeader;
            case MotionEvent.ACTION_UP:
                if (titleLinkPressState.isPressed()) {
                    return titleLinkPressState.onTouch(event, titleX, titleY, textLayout, titleBuiltText, context, touchSlop, context.getHost()::invalidate);
                }
                if (pressedTitleSpoiler) {
                    pressedTitleSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), textLayout, titleSpoilersPool, titleSpoilers, titleX, titleY, this::markTitleSpoilersRevealed);
                }
                if (!pressedHeader) {
                    return false;
                }
                pressedHeader = false;
                toggleOpen();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (titleLinkPressState.isPressed()) {
                    return titleLinkPressState.onTouch(event, titleX, titleY, textLayout, titleBuiltText, context, touchSlop, context.getHost()::invalidate);
                }
                if (pressedTitleSpoiler) {
                    pressedTitleSpoiler = false;
                    return true;
                }
                if (pressedHeader) {
                    pressedHeader = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void markTitleSpoilersRevealed() {
        titleSpoilersRevealed = true;
        if (inlineState != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(titleSpoilerStateKey);
        }
    }

    private boolean dispatchActiveChildTouch(MotionEvent event, float localX, float localY) {
        int index = activeChildTouchIndex;
        if (index < 0 || index >= childLayout.blocks.size()) {
            activeChildTouchIndex = -1;
            return false;
        }
        float childY = localY - headerHeight;
        boolean handled = childLayout.blocks.get(index).onTouch(
                event,
                localX - childLayout.blockLefts[index],
                childY - childLayout.blockTops[index]
        );
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeChildTouchIndex = -1;
        }
        return handled;
    }

    private void toggleOpen() {
        activeChildTouchIndex = -1;
        currentBlock.open = !currentBlock.open;
        if (state != null) {
            if (currentBlock.open) {
                state.getOpenedDetailsByIndex().add(stateIndex);
                state.getClosedDetailsByIndex().remove(stateIndex);
            } else {
                state.getOpenedDetailsByIndex().remove(stateIndex);
                if (defaultOpen) {
                    state.getClosedDetailsByIndex().add(stateIndex);
                } else {
                    state.getClosedDetailsByIndex().remove(stateIndex);
                }
            }
        }
        arrow.setAnimationProgressAnimated(currentBlock.open ? 0.0f : 1.0f);
        measuredHeight = 0;
        context.getHost().requestLayout();
        context.getSelectionGeometryInvalidator().invoke();
        if (detailsBlockView != null) {
            detailsBlockView.requestLayout();
            detailsBlockView.invalidate();
        }
        if (context.getHost().isAttachedToWindow()) {
            for (PostBodyBlock child : childLayout.blocks) {
                if (currentBlock.open) {
                    child.onAttachedToWindow();
                } else {
                    child.onDetachedFromWindow();
                }
            }
        }
        context.getHost().invalidate();
    }

    private static DetailsChildLayout buildChildLayout(CookedPostBlock.Details block, PostBodyBuildContext context, List<GalleryImage> gallery, PostBodyBlockState inlineState, String stateKey) {
        List<PostBodyBlock> bodyBlocks;
        if (block.getChildren() != null && !block.getChildren().isEmpty()) {
            bodyBlocks = PostBodyBlockFactory.INSTANCE.build(block.getChildren(), context, inlineState, gallery, stateKey + ".children", false);
        } else {
            bodyBlocks = Collections.<PostBodyBlock>singletonList(new ParagraphBlock(
                    block.getHtml(),
                    context,
                    inlineState,
                    TextLayoutBlock.inlineSpoilerStateKey("details-text:" + stateKey, block.getHtml())
            ));
        }
        return new DetailsChildLayout(bodyBlocks);
    }

    private static boolean initialOpenState(PostBodyBlockState state, int stateIndex, boolean defaultOpen) {
        if (state != null && state.getClosedDetailsByIndex().contains(stateIndex)) {
            return false;
        }
        if (state != null && state.getOpenedDetailsByIndex().contains(stateIndex)) {
            return true;
        }
        return defaultOpen;
    }

    private static int measureChildren(DetailsChildLayout layout, int maxWidth) {
        int y = 0;
        for (int i = 0; i < layout.blocks.size(); i++) {
            PostBodyBlock child = layout.blocks.get(i);
            int childLeft = detailsChildLeft(child);
            int childWidth = Math.max(maxWidth - childLeft - childRightInsetPx(), AndroidUtilities.dp(120f));
            layout.blockLefts[i] = childLeft;
            layout.blockTops[i] = y;
            layout.blockWidths[i] = childWidth;
            int height = child.measure(childWidth);
            layout.blockHeights[i] = height;
            y += height;
        }
        return y;
    }

    private static int detailsChildLeft(PostBodyBlock child) {
        int inset = Math.max(child.horizontalInsetPx(), 0);
        if (inset > 0) {
            return childLevelInsetPx() + inset;
        }
        if ("QuoteBlock".equals(child.getClass().getSimpleName()) || "CalloutBlock".equals(child.getClass().getSimpleName())) {
            return childLevelInsetPx();
        }
        return childTextLeftPx();
    }

    static int titleTextXPx() {
        return AndroidUtilities.dp(TITLE_TEXT_X_DP);
    }

    static int minHeaderHeightPx() {
        return AndroidUtilities.dp(MIN_HEADER_HEIGHT_DP);
    }

    static int bottomCellHeightPx() {
        return 1 + AndroidUtilities.dp(BOTTOM_CELL_EXTRA_HEIGHT_DP);
    }

    static int childLevelInsetPx() {
        return AndroidUtilities.dp(CHILD_LEVEL_INSET_DP);
    }

    static int levelLineLeftPx() {
        return AndroidUtilities.dp(LEVEL_LINE_LEFT_DP);
    }

    static int levelLineRightPx() {
        return AndroidUtilities.dp(LEVEL_LINE_RIGHT_DP);
    }

    static int childTextInsetAfterLinePx() {
        return AndroidUtilities.dp(CHILD_TEXT_INSET_AFTER_LINE_DP);
    }

    static int childTextLeftPx() {
        return levelLineLeftPx() + childTextInsetAfterLinePx();
    }

    static int childRightInsetPx() {
        return AndroidUtilities.dp(CHILD_RIGHT_INSET_DP);
    }

    static int titleWidthInsetPx() {
        return AndroidUtilities.dp(TITLE_WIDTH_INSET_DP);
    }

    static int titleBaselineExtraHeightPx() {
        return AndroidUtilities.dp(TITLE_BASELINE_EXTRA_HEIGHT_DP);
    }

    static int arrowLeftPx() {
        return AndroidUtilities.dp(ARROW_LEFT_DP);
    }

    static int arrowSizePx() {
        return AndroidUtilities.dp(ARROW_SIZE_DP);
    }

    private void addEmbeddedChildren(ViewGroup parent) {
        for (PostBodyBlock child : childLayout.blocks) {
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
        if (detailsBlockView == null) {
            return;
        }
        for (int i = 0; i < childLayout.blocks.size(); i++) {
            PostBodyBlock child = childLayout.blocks.get(i);
            if (child instanceof EmbeddedPostBodyBlock) {
                if (currentBlock.open) {
                    ((EmbeddedPostBodyBlock) child).layoutEmbeddedView(
                            childLayout.blockLefts[i],
                            headerHeight + childLayout.blockTops[i],
                            childLayout.blockWidths[i],
                            childLayout.blockHeights[i]
                    );
                } else {
                    ((EmbeddedPostBodyBlock) child).embeddedView().layout(0, 0, 0, 0);
                }
            }
        }
    }

    private static void drawChildren(Canvas canvas, DetailsChildLayout layout, int top, int width, boolean skipEmbeddedChildren) {
        for (int i = 0; i < layout.blocks.size(); i++) {
            PostBodyBlock child = layout.blocks.get(i);
            if (skipEmbeddedChildren && child instanceof EmbeddedPostBodyBlock) {
                continue;
            }
            canvas.save();
            canvas.translate(layout.blockLefts[i], top + layout.blockTops[i]);
            child.draw(canvas, layout.blockWidths[i]);
            canvas.restore();
        }
    }

    private static TLRPC.TL_pageBlockDetails toPageBlockDetails(CookedPostBlock.Details block) {
        TLRPC.TL_pageBlockDetails details = new TLRPC.TL_pageBlockDetails();
        details.title = richTextFromHtml(detailsTitleHtml(block));
        details.open = block.getOpen();
        if (block.getChildren() != null && !block.getChildren().isEmpty()) {
            for (CookedPostBlock child : block.getChildren()) {
                details.blocks.add(toPageBlock(child));
            }
        } else {
            details.blocks.add(paragraphBlock(block.getHtml()));
        }
        return details;
    }

    private static TLRPC.PageBlock toPageBlock(CookedPostBlock block) {
        if (block instanceof CookedPostBlock.Text) {
            return paragraphBlock(((CookedPostBlock.Text) block).getHtml());
        } else if (block instanceof CookedPostBlock.Heading) {
            TLRPC.PageBlock pageBlock = ((CookedPostBlock.Heading) block).getLevel() <= 2
                    ? new TLRPC.TL_pageBlockHeader()
                    : new TLRPC.TL_pageBlockSubheader();
            pageBlock.text = richTextFromHtml(((CookedPostBlock.Heading) block).getHtml());
            return pageBlock;
        } else if (block instanceof CookedPostBlock.Quote) {
            TLRPC.TL_pageBlockBlockquote pageBlock = new TLRPC.TL_pageBlockBlockquote();
            CookedPostBlock.Quote quote = (CookedPostBlock.Quote) block;
            pageBlock.text = richTextFromHtml(quote.getHtml());
            pageBlock.caption = richTextFromHtml(quote.getTitleHtml() != null ? quote.getTitleHtml() : quote.getTitle());
            return pageBlock;
        } else if (block instanceof CookedPostBlock.Callout) {
            TLRPC.TL_pageBlockBlockquote pageBlock = new TLRPC.TL_pageBlockBlockquote();
            CookedPostBlock.Callout callout = (CookedPostBlock.Callout) block;
            pageBlock.text = richTextFromHtml(callout.getHtml());
            pageBlock.caption = richTextFromHtml(callout.getTitleHtml() != null ? callout.getTitleHtml() : callout.getType());
            return pageBlock;
        } else if (block instanceof CookedPostBlock.Code) {
            TLRPC.TL_pageBlockPreformatted pageBlock = new TLRPC.TL_pageBlockPreformatted();
            pageBlock.text = plain(((CookedPostBlock.Code) block).getCode());
            pageBlock.language = "";
            return pageBlock;
        } else if (block instanceof CookedPostBlock.ListItems) {
            return listBlock((CookedPostBlock.ListItems) block);
        } else if (block instanceof CookedPostBlock.Table) {
            return tableBlock((CookedPostBlock.Table) block);
        } else if (block instanceof CookedPostBlock.Image) {
            return photoBlock((CookedPostBlock.Image) block);
        } else if (block instanceof CookedPostBlock.Collage) {
            TLRPC.TL_pageBlockCollage pageBlock = new TLRPC.TL_pageBlockCollage();
            pageBlock.caption = emptyCaption();
            for (CookedPostBlock.Image image : ((CookedPostBlock.Collage) block).getImages()) {
                pageBlock.items.add(photoBlock(image));
            }
            return pageBlock;
        } else if (block instanceof CookedPostBlock.Details) {
            return toPageBlockDetails((CookedPostBlock.Details) block);
        } else if (block == CookedPostBlock.Rule.INSTANCE) {
            return new TLRPC.TL_pageBlockDivider();
        }
        return new TLRPC.TL_pageBlockUnsupported();
    }

    private static TLRPC.TL_pageBlockList listBlock(CookedPostBlock.ListItems block) {
        TLRPC.TL_pageBlockList pageBlock = new TLRPC.TL_pageBlockList();
        pageBlock.ordered = block.getOrdered();
        for (CookedPostBlock.ListItem item : block.getItems()) {
            if (item.getBlocks() != null && !item.getBlocks().isEmpty()) {
                TLRPC.TL_pageListItemBlocks pageListItem = new TLRPC.TL_pageListItemBlocks();
                for (CookedPostBlock child : item.getBlocks()) {
                    pageListItem.blocks.add(toPageBlock(child));
                }
                pageBlock.items.add(pageListItem);
            } else {
                TLRPC.TL_pageListItemText pageListItem = new TLRPC.TL_pageListItemText();
                pageListItem.text = richTextFromHtml(item.getHtml());
                pageBlock.items.add(pageListItem);
            }
        }
        return pageBlock;
    }

    private static TLRPC.TL_pageBlockTable tableBlock(CookedPostBlock.Table block) {
        TLRPC.TL_pageBlockTable pageBlock = new TLRPC.TL_pageBlockTable();
        pageBlock.bordered = true;
        pageBlock.striped = true;
        pageBlock.title = plain("");
        if (!block.getHeaders().isEmpty()) {
            TLRPC.TL_pageTableRow headerRow = new TLRPC.TL_pageTableRow();
            for (CookedPostBlock.TableCell cell : block.getHeaders()) {
                headerRow.cells.add(tableCell(cell, true));
            }
            pageBlock.rows.add(headerRow);
        }
        for (List<CookedPostBlock.TableCell> row : block.getRows()) {
            TLRPC.TL_pageTableRow pageRow = new TLRPC.TL_pageTableRow();
            for (CookedPostBlock.TableCell cell : row) {
                pageRow.cells.add(tableCell(cell, false));
            }
            pageBlock.rows.add(pageRow);
        }
        return pageBlock;
    }

    private static TLRPC.TL_pageTableCell tableCell(CookedPostBlock.TableCell cell, boolean header) {
        TLRPC.TL_pageTableCell pageCell = new TLRPC.TL_pageTableCell();
        pageCell.header = header;
        pageCell.align_center = cell.getAlign() == CookedPostBlock.TableCellAlign.Center;
        pageCell.align_right = cell.getAlign() == CookedPostBlock.TableCellAlign.End;
        pageCell.colspan = Math.max(1, cell.getColspan());
        pageCell.rowspan = Math.max(1, cell.getRowspan());
        pageCell.text = !cell.getHtml().trim().isEmpty()
                ? richTextFromHtml(cell.getHtml())
                : plain(cell.getText());
        return pageCell;
    }

    private static TLRPC.TL_pageBlockPhoto photoBlock(CookedPostBlock.Image image) {
        TLRPC.TL_pageBlockPhoto pageBlock = new TLRPC.TL_pageBlockPhoto();
        pageBlock.url = image.getSrc();
        pageBlock.w = image.getWidthPx();
        pageBlock.h = image.getHeightPx();
        String captionHtml = image.getCaptionHtml();
        if ((captionHtml == null || captionHtml.trim().isEmpty()) && image.getAlt() != null && !image.getAlt().trim().isEmpty()) {
            captionHtml = TextUtils.htmlEncode(image.getAlt());
        }
        pageBlock.caption = pageCaption(captionHtml);
        return pageBlock;
    }

    private static String detailsTitleHtml(CookedPostBlock.Details block) {
        String summaryHtml = block.getSummaryHtml();
        if (summaryHtml != null && !summaryHtml.trim().isEmpty()) {
            return summaryHtml;
        }
        String summary = block.getSummary();
        if (summary != null && !summary.trim().isEmpty()) {
            return TextUtils.htmlEncode(summary);
        }
        return "Details";
    }

    private static TLRPC.TL_pageBlockParagraph paragraphBlock(String html) {
        TLRPC.TL_pageBlockParagraph pageBlock = new TLRPC.TL_pageBlockParagraph();
        pageBlock.text = richTextFromHtml(html);
        return pageBlock;
    }

    private static TLRPC.TL_pageCaption pageCaption(String html) {
        TLRPC.TL_pageCaption caption = new TLRPC.TL_pageCaption();
        caption.text = html != null && !html.trim().isEmpty() ? richTextFromHtml(html) : new TLRPC.TL_textEmpty();
        caption.credit = new TLRPC.TL_textEmpty();
        return caption;
    }

    private static TLRPC.TL_pageCaption emptyCaption() {
        return pageCaption(null);
    }

    private static TLRPC.TL_textPlain richTextFromHtml(String html) {
        return plain(htmlText(html));
    }

    private static TLRPC.TL_textPlain plain(String text) {
        TLRPC.TL_textPlain richText = new TLRPC.TL_textPlain();
        richText.text = text != null ? text : "";
        return richText;
    }

    private static String htmlText(String html) {
        return Jsoup.parseBodyFragment(html != null ? html : "").body().text();
    }
}
