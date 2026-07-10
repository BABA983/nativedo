/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockBlockquoteCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import io.github.baba983.nativedo.core.html.GalleryImage;
import io.github.baba983.nativedo.core.ui.DiscourseUiHelpersKt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AvatarDrawable;

final class QuoteBlock implements EmbeddedPostBodyBlock {
    static final float TEXT_TOP_DP = 8f;
    static final float LINE_LEFT_DP = 18f;
    static final float LINE_RIGHT_DP = 20f;
    static final float TEXT_INSET_AFTER_LINE_DP = 14f;
    static final float SIDE_PADDING_DP = 18f;
    static final float LINE_VERTICAL_INSET_DP = 6f;
    static final float TITLE_BODY_GAP_DP = 8f;
    static final float TITLE_AVATAR_DP = 24f;
    /** Gap between avatar and title text; mirrors BlockEmbedPostCell 14dp author-to-text inset at smaller avatar. */
    static final float TITLE_AVATAR_TEXT_GAP_DP = 10f;
    static final float TITLE_CHEVRON_SIZE_DP = 13f;
    static final float TITLE_CHEVRON_HIT_DP = 36f;
    static final float TITLE_CATEGORY_CHIP_V_GAP_DP = 4f;
    static final float TITLE_CATEGORY_CHIP_H_PAD_DP = 6f;
    static final float TITLE_CATEGORY_CHIP_V_PAD_DP = 2f;
    static final float TITLE_CATEGORY_CHIP_TEXT_DP = 12f;
    static final float TITLE_CATEGORY_CHIP_RADIUS_DP = 4f;
    static final float BODY_BOTTOM_PADDING_DP = 8f;

    private static final class QuoteTitleBlock extends TextLayoutBlock {
        private final int maxLines;

        QuoteTitleBlock(
                String html,
                PostBodyBuildContext context,
                TextPaint paint,
                PostBodyBlockState inlineState,
                String inlineSpoilerStateKey,
                int maxLines
        ) {
            super(html, context, paint, inlineState, inlineSpoilerStateKey);
            this.maxLines = maxLines;
        }

        @Override
        protected StaticLayout buildLayout(CharSequence spannable, int width) {
            StaticLayout.Builder builder = ArticleTextLayout.builder(spannable, paint, width);
            if (maxLines > 0) {
                builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
            }
            return builder.build();
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

    private final class QuoteBlockView extends ViewGroup implements Drawable.Callback {
        QuoteBlockView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void invalidateDrawable(Drawable drawable) {
            invalidate();
        }

        @Override
        public void scheduleDrawable(Drawable drawable, Runnable what, long when) {
        }

        @Override
        public void unscheduleDrawable(Drawable drawable, Runnable what) {
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
    private final QuoteTitleBlock collapsedTitleBlock;
    private final QuoteTitleBlock expandedTitleBlock;
    private QuoteTitleBlock titleBlock;
    private final ImageReceiver titleAvatarReceiver;
    private final AvatarDrawable titleAvatarDrawable;
    private final int titleAvatarSizePx;
    private final int titleAvatarTextGapPx;
    private final AnimatedArrowDrawable expandArrow;
    private final TextPaint categoryChipPaint;
    private final Paint categoryChipBgPaint;
    private final int touchSlop;
    private final boolean showExpandChevron;
    private final String categoryName;
    private final int categoryChipBgColor;
    private final int categoryChipTextColor;
    private int titleContentHeight;
    private int titleLinkLayoutHeight;
    private int titleRowHeight;
    private int chevronHitLeft;
    private int expandChevronReservePx;
    private boolean pressedTitleRowExpand;
    private float titleRowDownX;
    private float titleRowDownY;
    private int titleTextLeft;
    private int titleTextWidth;
    private BodyLayout bodyLayout;
    private final BodyLayout collapsedBodyLayout;
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
        titleAvatarSizePx = AndroidUtilities.dp(TITLE_AVATAR_DP);
        titleAvatarTextGapPx = AndroidUtilities.dp(TITLE_AVATAR_TEXT_GAP_DP);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
        showExpandChevron = canExpandBySource(block);
        expandChevronReservePx = showExpandChevron ? titleChevronHitPx() : 0;
        categoryName = normalizeCategoryName(block.getTitleCategoryName());
        categoryChipBgColor = parseCssColor(block.getTitleCategoryColor(), context.getChipBackgroundColor());
        categoryChipTextColor = parseCssColor(
                block.getTitleCategoryTextColor(),
                contrastOnBackground(categoryChipBgColor, context.getTextColor(), Color.BLACK)
        );
        categoryChipPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        categoryChipPaint.setTextSize(AndroidUtilities.dp(TITLE_CATEGORY_CHIP_TEXT_DP));
        categoryChipPaint.setColor(categoryChipTextColor);
        categoryChipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        categoryChipBgPaint.setColor(categoryChipBgColor);
        expandArrow = showExpandChevron
                ? new AnimatedArrowDrawable(context.getSecondaryTextColor(), true)
                : null;
        if (expandArrow != null) {
            expandArrow.setAnimationProgress(expanded ? 0.0f : 1.0f);
        }
        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
        titlePaint.setColor(context.getTextColor());
        titlePaint.setTextSize(AndroidUtilities.dp(14f));
        titlePaint.linkColor = context.getAccentColor();
        String titleHtml = quoteTitleHtml(block);
        String titleAvatarUrl = block.getTitleAvatarUrl();
        if (titleAvatarUrl != null && !titleAvatarUrl.trim().isEmpty()) {
            titleAvatarDrawable = new AvatarDrawable();
            String titleLabel = block.getTitle();
            if (titleLabel == null || titleLabel.trim().isEmpty()) {
                titleLabel = "?";
            }
            titleAvatarDrawable.setInfo(0, titleLabel);
            titleAvatarDrawable.setRoundRadius(titleAvatarSizePx / 2);
            titleAvatarReceiver = context.getImagePool().obtain("quote-title-avatar:" + stateKey + ":" + titleAvatarUrl);
            titleAvatarReceiver.setRoundRadius(titleAvatarSizePx / 2);
            titleAvatarReceiver.setAllowDecodeSingleFrame(
                    !DiscourseUiHelpersKt.isAnimatedDiscourseImageUrl(titleAvatarUrl)
            );
            titleAvatarReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> context.getHost().invalidate());
            titleAvatarReceiver.setVisibleInvalidate(context.getHost()::invalidate);
            titleAvatarReceiver.setImage(
                    ImageLocation.getForPath(titleAvatarUrl),
                    DiscourseUiHelpersKt.avatarImageFilter("", titleAvatarSizePx),
                    titleAvatarDrawable,
                    null,
                    titleAvatarUrl,
                    0
            );
        } else {
            titleAvatarDrawable = null;
            titleAvatarReceiver = null;
        }
        String titleSpoilerKey = titleHtml != null
                ? TextLayoutBlock.inlineSpoilerStateKey("quote-title:" + stateKey, titleHtml)
                : null;
        collapsedTitleBlock = titleHtml != null
                ? new QuoteTitleBlock(titleHtml, context, titlePaint, inlineState, titleSpoilerKey, 1)
                : null;
        expandedTitleBlock = titleHtml != null
                ? new QuoteTitleBlock(titleHtml, context, titlePaint, inlineState, titleSpoilerKey, 0)
                : null;

        collapsedBodyLayout = buildBodyLayout(block.getChildren(), block.getHtml(), gallery, "body");
        bodyLayout = collapsedBodyLayout;
        if (state != null) {
            String html = state.getExpandedQuotesByIndex().get(stateIndex);
            if (html != null) {
                expandedHtml = html;
                bodyLayout = buildExpandedBodyLayout(html);
                expanded = true;
                if (expandArrow != null) {
                    expandArrow.setAnimationProgress(0.0f);
                }
            }
        }
    }

    private static boolean canExpandBySource(CookedPostBlock.Quote block) {
        return block.getSourceTopicId() != null && block.getSourcePostNumber() != null;
    }

    private static String normalizeCategoryName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseCssColor(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String normalized = raw.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        try {
            return Color.parseColor(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int contrastOnBackground(int background, int light, int dark) {
        double luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0;
        return luminance > 0.6 ? dark : light;
    }

    private void layoutTitleRow(int availableTextWidth) {
        int titleAvatarColumnPx = titleAvatarReceiver != null ? titleAvatarSizePx + titleAvatarTextGapPx : 0;
        titleTextLeft = textLeft + titleAvatarColumnPx;
        titleTextWidth = Math.max(availableTextWidth - titleAvatarColumnPx - expandChevronReservePx, AndroidUtilities.dp(40f));
        chevronHitLeft = blockWidth - expandChevronReservePx;
        titleBlock = expanded ? expandedTitleBlock : collapsedTitleBlock;
        titleLinkLayoutHeight = 0;
        if (titleBlock != null) {
            titleLinkLayoutHeight = titleBlock.measure(titleTextWidth);
        }
        titleContentHeight = titleLinkLayoutHeight;
        if (categoryName != null) {
            int chipHeight = categoryChipHeightPx();
            titleContentHeight += (titleLinkLayoutHeight > 0 ? titleCategoryChipVerticalGapPx() : 0) + chipHeight;
        }
        if (titleAvatarReceiver != null) {
            titleContentHeight = Math.max(titleContentHeight, titleLinkLayoutHeight > 0
                    ? Math.max(titleAvatarSizePx, titleLinkLayoutHeight)
                    : titleAvatarSizePx);
        }
        titleRowHeight = titleContentHeight;
    }

    private void syncExpandArrowProgress(boolean animated) {
        if (expandArrow == null) {
            return;
        }
        float target = expanded ? 0f : 1f;
        if (animated) {
            expandArrow.setAnimationProgressAnimated(target);
        } else {
            expandArrow.setAnimationProgress(target);
        }
    }

    private void afterExpandStateChanged() {
        syncExpandArrowProgress(true);
        measuredHeight = 0;
        context.getSelectionGeometryInvalidator().invoke();
        if (quoteBlockView != null) {
            quoteBlockView.requestLayout();
            quoteBlockView.invalidate();
        }
        context.getHost().requestLayout();
        context.getHost().invalidate();
    }

    private boolean hasTitleRow() {
        return titleBlock != null || titleAvatarReceiver != null || categoryName != null || showExpandChevron;
    }

    private int titleRowBottom() {
        return hasTitleRow() ? textTopPx() + titleRowHeight : textTopPx();
    }

    private int categoryChipHeightPx() {
        Paint.FontMetrics fm = categoryChipPaint.getFontMetrics();
        return (int) Math.ceil(-fm.ascent + fm.descent) + 2 * titleCategoryChipVerticalPadPx();
    }

    private void drawCategoryChip(Canvas canvas, int left, int top) {
        if (categoryName == null) {
            return;
        }
        float textWidth = categoryChipPaint.measureText(categoryName);
        int hPad = titleCategoryChipHorizontalPadPx();
        int vPad = titleCategoryChipVerticalPadPx();
        int chipHeight = categoryChipHeightPx();
        RectF rect = new RectF(left, top, left + textWidth + 2 * hPad, top + chipHeight);
        canvas.drawRoundRect(rect, titleCategoryChipRadiusPx(), titleCategoryChipRadiusPx(), categoryChipBgPaint);
        float textY = top + vPad - categoryChipPaint.getFontMetrics().ascent;
        canvas.drawText(categoryName, left + hPad, textY, categoryChipPaint);
    }

    private boolean isInTitleRow(float localX, float localY) {
        return hasTitleRow() && localX >= textLeft && localX < blockWidth && localY >= textTopPx() && localY < titleRowBottom();
    }

    private boolean handleTitleRowTouch(MotionEvent event, float localX, float localY) {
        float titleX = localX - titleTextLeft;
        float titleY = localY - textTopPx();
        int action = event.getActionMasked();
        if (titleBlock != null && titleY < titleLinkLayoutHeight) {
            boolean linkHandled = titleBlock.onTouch(event, titleX, titleY);
            if (linkHandled) {
                if (action == MotionEvent.ACTION_DOWN) {
                    activeTitleTouch = true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    activeTitleTouch = false;
                }
                return true;
            }
        }
        if (!showExpandChevron) {
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                pressedTitleRowExpand = true;
                titleRowDownX = localX;
                titleRowDownY = localY;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!pressedTitleRowExpand) {
                    return false;
                }
                if (Math.abs(localX - titleRowDownX) > touchSlop || Math.abs(localY - titleRowDownY) > touchSlop) {
                    pressedTitleRowExpand = false;
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!pressedTitleRowExpand) {
                    return false;
                }
                pressedTitleRowExpand = false;
                toggleExpanded();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedTitleRowExpand = false;
                return true;
            default:
                return false;
        }
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        blockWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        int quoteLevel = block.getLevel();
        textLeft = textLeftPx(quoteLevel);
        bodyWidth = Math.max(blockWidth - textLeft - sidePaddingPx(quoteLevel), AndroidUtilities.dp(80f));
        layoutTitleRow(bodyWidth);
        bodyTop = textTopPx() + (hasTitleRow() ? titleRowHeight + titleBodyGapPx() : 0);
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
            if (expandArrow != null) {
                expandArrow.setCallback(quoteBlockView);
            }
            syncExpandArrowProgress(false);
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
        titlePaint.setColor(context.getTextColor());
        categoryChipPaint.setColor(categoryChipTextColor);
        categoryChipBgPaint.setColor(categoryChipBgColor);
        if (expandArrow != null) {
            expandArrow.setColor(context.getSecondaryTextColor());
        }
        int quoteLevel = block.getLevel();
        int titleTop = textTopPx();
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
        if (titleAvatarReceiver != null) {
            float avatarTop = titleTop + Math.max(0, (titleLinkLayoutHeight - titleAvatarSizePx) / 2f);
            titleAvatarReceiver.setImageCoords(
                    textLeft,
                    avatarTop,
                    titleAvatarSizePx,
                    titleAvatarSizePx
            );
            titleAvatarReceiver.draw(canvas);
        }
        if (titleBlock != null) {
            canvas.save();
            canvas.translate(titleTextLeft, titleTop);
            titleBlock.draw(canvas, titleTextWidth);
            canvas.restore();
        }
        if (categoryName != null) {
            int chipTop = titleTop + titleLinkLayoutHeight + (titleLinkLayoutHeight > 0 ? titleCategoryChipVerticalGapPx() : 0);
            drawCategoryChip(canvas, titleTextLeft, chipTop);
        }
        if (expandArrow != null && showExpandChevron) {
            canvas.save();
            float arrowLeft = chevronHitLeft + (expandChevronReservePx - titleChevronSizePx()) / 2f;
            float arrowTop = titleTop + (titleRowHeight - titleChevronSizePx()) / 2f;
            canvas.translate(arrowLeft, arrowTop);
            expandArrow.draw(canvas);
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
            boolean handled = titleBlock != null && titleBlock.onTouch(event, localX - titleTextLeft, localY - textTopPx());
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activeTitleTouch = false;
            }
            return handled;
        }
        if (pressedTitleRowExpand && action != MotionEvent.ACTION_DOWN) {
            return handleTitleRowTouch(event, localX, localY);
        }
        if (activeChildTouchIndex >= 0 && action != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, localX, localY);
        }
        if (isInTitleRow(localX, localY)) {
            return handleTitleRowTouch(event, localX, localY);
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
                    return handled;
                }
            }
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (titleBlock != null) {
            titleBlock.fillTextLayoutBlocks(blocks, x + titleTextLeft, y + textTopPx());
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
        context.getImagePool().onAttachedToWindow();
        if (titleAvatarReceiver != null) {
            titleAvatarReceiver.onAttachedToWindow();
        }
        if (collapsedTitleBlock != null) {
            collapsedTitleBlock.onAttachedToWindow();
        }
        if (expandedTitleBlock != null) {
            expandedTitleBlock.onAttachedToWindow();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        pressedTitleRowExpand = false;
        if (titleAvatarReceiver != null) {
            titleAvatarReceiver.onDetachedFromWindow();
        }
        if (collapsedTitleBlock != null) {
            collapsedTitleBlock.onDetachedFromWindow();
        }
        if (expandedTitleBlock != null) {
            expandedTitleBlock.onDetachedFromWindow();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onDetachedFromWindow();
        }
    }

    @Override
    public void applyTheme() {
        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
        titlePaint.setColor(context.getTextColor());
        titlePaint.linkColor = context.getAccentColor();
        categoryChipPaint.setColor(categoryChipTextColor);
        categoryChipBgPaint.setColor(categoryChipBgColor);
        if (expandArrow != null) {
            expandArrow.setColor(context.getSecondaryTextColor());
        }
        if (collapsedTitleBlock != null) {
            collapsedTitleBlock.applyTheme();
        }
        if (expandedTitleBlock != null) {
            expandedTitleBlock.applyTheme();
        }
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.applyTheme();
        }
    }

    private void toggleExpanded() {
        if (expanded) {
            collapse();
        } else {
            expand();
        }
    }

    private void collapse() {
        if (!expanded) {
            return;
        }
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        pressedTitleRowExpand = false;
        for (PostBodyBlock child : bodyLayout.blocks) {
            child.onDetachedFromWindow();
        }
        if (quoteBlockView != null) {
            quoteBlockView.removeAllViews();
        }
        bodyLayout = collapsedBodyLayout;
        expanded = false;
        if (quoteBlockView != null) {
            addEmbeddedChildren(quoteBlockView);
        }
        if (context.getHost().isAttachedToWindow()) {
            for (PostBodyBlock child : bodyLayout.blocks) {
                child.onAttachedToWindow();
            }
        }
        afterExpandStateChanged();
    }

    private void expand() {
        Long topicId = block.getSourceTopicId();
        Integer postNumber = block.getSourcePostNumber();
        if (topicId == null || postNumber == null || loading || expanded) {
            return;
        }
        if (expandedHtml != null) {
            applyExpandedHtml(expandedHtml);
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
            applyExpandedHtml(html);
            return Unit.INSTANCE;
        });
    }

    private void applyExpandedHtml(String html) {
        if (expanded) {
            return;
        }
        activeChildTouchIndex = -1;
        activeTitleTouch = false;
        pressedTitleRowExpand = false;
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
        afterExpandStateChanged();
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

    static int titleChevronSizePx() {
        return AndroidUtilities.dp(TITLE_CHEVRON_SIZE_DP);
    }

    static int titleChevronHitPx() {
        return AndroidUtilities.dp(TITLE_CHEVRON_HIT_DP);
    }

    static int titleCategoryChipVerticalGapPx() {
        return AndroidUtilities.dp(TITLE_CATEGORY_CHIP_V_GAP_DP);
    }

    static int titleCategoryChipHorizontalPadPx() {
        return AndroidUtilities.dp(TITLE_CATEGORY_CHIP_H_PAD_DP);
    }

    static int titleCategoryChipVerticalPadPx() {
        return AndroidUtilities.dp(TITLE_CATEGORY_CHIP_V_PAD_DP);
    }

    static int titleCategoryChipRadiusPx() {
        return AndroidUtilities.dp(TITLE_CATEGORY_CHIP_RADIUS_DP);
    }

    static int titleAvatarSizePx() {
        return AndroidUtilities.dp(TITLE_AVATAR_DP);
    }

    static int titleAvatarTextGapPx() {
        return AndroidUtilities.dp(TITLE_AVATAR_TEXT_GAP_DP);
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
