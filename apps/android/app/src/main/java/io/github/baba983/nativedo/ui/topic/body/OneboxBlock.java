/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ChatMessageCell link-preview
 * measurement, drawing, and hit-testing patterns.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class OneboxBlock implements PostBodyBlock {
    private static final int[] PRESSED_STATE = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private static final float OUTER_HORIZONTAL_INSET_DP = 18f;
    private static final float LINK_PREVIEW_X_OFFSET_DP = 1.33f;
    private static final float TEXT_LEFT_DP = 10f;
    private static final float TEXT_RIGHT_DP = 10f;
    private static final float LINK_PREVIEW_TOP_INSET_DP = 6f;
    private static final float LINK_PREVIEW_BOTTOM_INSET_DP = 0f;
    private static final float LINK_PREVIEW_BACKGROUND_BOTTOM_INSET_DP = 6f;
    private static final float LINK_PREVIEW_OUTSIDE_BOTTOM_INSET_DP = 6f;
    private static final float SITE_NAME_TEXT_Y_OFFSET_DP = 1f;
    private static final float PREVIEW_TEXT_Y_OFFSET_DP = 3f;
    private static final float SMALL_IMAGE_RIGHT_INSET_DP = 6f;
    private static final float SMALL_IMAGE_BOTTOM_INSET_DP = 1f;
    private static final float SMALL_IMAGE_SIDE_DP = 48f;
    private static final float SMALL_IMAGE_SIDE_MARGIN_DP = 10f;
    private static final float SMALL_IMAGE_ROUND_RADIUS_DP = 4f;
    private static final float LARGE_IMAGE_LEFT_INSET_DP = 10f;
    private static final float LARGE_IMAGE_RIGHT_INSET_DP = 7f;
    private static final float LARGE_IMAGE_TOP_GAP_DP = 2f;
    private static final float LARGE_IMAGE_MIN_HEIGHT_DP = 60f;
    private static final float MEDIA_TEXT_EXTRA_CHROME_DP = 2.66f;
    private static final float INTER_LAYOUT_GAP_DP = 2f;
    private static final float PREVIEW_TEXT_LINE_SPACING_DP = 1f;
    private static final int SMALL_IMAGE_TEXT_LINES = 3;
    private static final int TITLE_MAX_LINES = 4;
    private static final int DESCRIPTION_MAX_LINES = 6;

    private enum OneboxHitTarget {
        Card,
        Thumbnail,
        Description
    }

    private static final class PositionedLayout {
        final StaticLayout layout;
        final BuiltText builtText;
        final float x;
        final float y;
        final RectF bounds;
        final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
        final Stack<SpoilerEffect> spoilersPool = new Stack<>();
        final Path spoilerBoundsPath = new Path();
        final Path spoilerRipplePath = new Path();
        final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
        BuiltText.Handle handle;
        boolean spoilersRevealed;
        boolean pressedSpoiler;
        float downX;
        float downY;

        PositionedLayout(StaticLayout layout, float x, float y) {
            this(layout, null, x, y, false);
        }

        PositionedLayout(StaticLayout layout, BuiltText builtText, float x, float y) {
            this(layout, builtText, x, y, false);
        }

        PositionedLayout(StaticLayout layout, BuiltText builtText, float x, float y, boolean spoilersRevealed) {
            this.layout = layout;
            this.builtText = builtText;
            this.x = x;
            this.y = y;
            this.bounds = new RectF(x, y, x + layout.getWidth(), y + layout.getHeight());
            this.spoilersRevealed = spoilersRevealed;
            SpoilerTextLayout.collect(null, layout, spoilersPool, spoilers, spoilersRevealed);
        }

        void draw(Canvas canvas, Runnable invalidate) {
            canvas.save();
            canvas.translate(x, y);
            linkPressState.draw(canvas, invalidate);
            SpoilerTextLayout.draw(canvas, layout, spoilers, spoilerBoundsPath, spoilerRipplePath);
            canvas.restore();
        }

        TextSelectionHelper.TextLayoutBlock asSelectionBlock(int parentX, int parentY) {
            return new PostSelectionLayoutBlock(layout, parentX + (int) x, parentY + (int) y, 0, null);
        }

        void attach(android.view.View host) {
            if (builtText == null || handle != null) {
                return;
            }
            handle = builtText.attach(host);
            for (int i = 0; i < spoilers.size(); i++) {
                spoilers.get(i).setParentView(host);
            }
        }

        void detach() {
            if (handle != null) {
                handle.detach();
                handle = null;
            }
            linkPressState.reset();
            pressedSpoiler = false;
        }

        boolean hasPressedRichText() {
            return linkPressState.isPressed() || pressedSpoiler;
        }

        boolean onRichTextTouch(MotionEvent event, float localX, float localY, PostBodyBuildContext context, int touchSlop, Runnable onRevealed) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (linkPressState.onTouch(event, localX, localY, layout, builtText, context, touchSlop, context.getHost()::invalidate)) {
                        return true;
                    }
                    if (!SpoilerTextLayout.contains(spoilers, localX, localY)) {
                        return false;
                    }
                    pressedSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, layout, builtText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (!pressedSpoiler) {
                        return false;
                    }
                    if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                        pressedSpoiler = false;
                        return false;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, layout, builtText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (!pressedSpoiler) {
                        return false;
                    }
                    pressedSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), layout, spoilersPool, spoilers, localX, localY, () -> {
                        spoilersRevealed = true;
                        onRevealed.run();
                    });
                case MotionEvent.ACTION_CANCEL:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, layout, builtText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (pressedSpoiler) {
                        pressedSpoiler = false;
                        return true;
                    }
                    return false;
                default:
                    return hasPressedRichText();
            }
        }
    }

    private static final class SmallImageTextResult {
        final StaticLayout layout;
        final SpannableStringBuilder text;
        final ArrayList<Integer> insertedBreakOffsets;

        SmallImageTextResult(StaticLayout layout, SpannableStringBuilder text, ArrayList<Integer> insertedBreakOffsets) {
            this.layout = layout;
            this.text = text;
            this.insertedBreakOffsets = insertedBreakOffsets;
        }
    }

    private static final class PreviewBuildResult {
        final StaticLayout layout;
        final BuiltText builtText;

        PreviewBuildResult(StaticLayout layout, BuiltText builtText) {
            this.layout = layout;
            this.builtText = builtText;
        }
    }

    private static final class OneboxPreviewLayout {
        final RectF cardBounds;
        final RectF hitBounds;
        final RectF thumbnailBounds;
        final PositionedLayout domainLayout;
        final PositionedLayout titleLayout;
        final PositionedLayout descriptionLayout;
        final int chromeBottom;
        final int totalHeight;

        OneboxPreviewLayout(
                RectF cardBounds,
                RectF hitBounds,
                RectF thumbnailBounds,
                PositionedLayout domainLayout,
                PositionedLayout titleLayout,
                PositionedLayout descriptionLayout,
                int chromeBottom,
                int totalHeight
        ) {
            this.cardBounds = cardBounds;
            this.hitBounds = hitBounds;
            this.thumbnailBounds = thumbnailBounds;
            this.domainLayout = domainLayout;
            this.titleLayout = titleLayout;
            this.descriptionLayout = descriptionLayout;
            this.chromeBottom = chromeBottom;
            this.totalHeight = totalHeight;
        }

        OneboxHitTarget hitTarget(float x, float y) {
            if (!hitBounds.contains(x, y)) {
                return null;
            }
            if (thumbnailBounds != null && thumbnailBounds.contains(x, y)) {
                return OneboxHitTarget.Thumbnail;
            }
            if (descriptionLayout != null && descriptionLayout.bounds.contains(x, y)) {
                return OneboxHitTarget.Description;
            }
            return OneboxHitTarget.Card;
        }

        void attachText(android.view.View host) {
            if (domainLayout != null) {
                domainLayout.attach(host);
            }
            if (titleLayout != null) {
                titleLayout.attach(host);
            }
            if (descriptionLayout != null) {
                descriptionLayout.attach(host);
            }
        }

        void detachText() {
            if (domainLayout != null) {
                domainLayout.detach();
            }
            if (titleLayout != null) {
                titleLayout.detach();
            }
            if (descriptionLayout != null) {
                descriptionLayout.detach();
            }
        }
    }

    private final CookedPostBlock.Onebox block;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState inlineState;
    private final String titleSpoilerStateKey;
    private final String descriptionSpoilerStateKey;
    private final ReplyMessageLine linkLine;
    private final RectF linkLineRect = new RectF();
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String resolvedHref;
    private final String thumbnailUrl;
    private final ImageReceiver thumbnailReceiver;
    private final TextPaint domainPaint;
    private final TextPaint titlePaint;
    private final TextPaint descriptionPaint;
    private final PostBodyTextBuilder textBuilder;
    private final ButtonBounce linkPreviewBounce;
    private final int touchSlop;
    private Drawable linkPreviewSelector;
    private int linkPreviewSelectorColor;
    private OneboxPreviewLayout previewLayout;
    private OneboxHitTarget pressedTarget;
    private boolean titleSpoilersRevealed;
    private boolean descriptionSpoilersRevealed;

    OneboxBlock(CookedPostBlock.Onebox block, PostBodyBuildContext context, PostBodyBlockState inlineState, String stateKey) {
        this.block = block;
        this.context = context;
        this.inlineState = inlineState;
        linkLine = new ReplyMessageLine(context.getHost());
        int domainColor = linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        placeholderPaint.setColor(context.getMediaPlaceholderColor());
        resolvedHref = resolveUrlOrFallback(context.getBaseUrl(), block.getHref(), block.getHref());
        thumbnailUrl = block.getThumbnailUrl() != null
                ? CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), block.getThumbnailUrl())
                : null;
        titleSpoilerStateKey = stateKey != null
                ? TextLayoutBlock.inlineSpoilerStateKey(stateKey + ":title", block.getTitleHtml() != null ? block.getTitleHtml() : block.getTitle())
                : null;
        titleSpoilersRevealed = inlineState != null
                && titleSpoilerStateKey != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(titleSpoilerStateKey);
        descriptionSpoilerStateKey = stateKey != null
                ? TextLayoutBlock.inlineSpoilerStateKey(stateKey + ":description", block.getDescriptionHtml() != null ? block.getDescriptionHtml() : block.getDescription())
                : null;
        descriptionSpoilersRevealed = inlineState != null
                && descriptionSpoilerStateKey != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(descriptionSpoilerStateKey);
        if (thumbnailUrl != null) {
            thumbnailReceiver = context.getImagePool().obtain("onebox:" + resolvedHref + ":" + thumbnailUrl);
            thumbnailReceiver.setRoundRadius(linkPreviewMediaRoundRadiusPx());
            thumbnailReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> context.getHost().invalidate());
            thumbnailReceiver.setVisibleInvalidate(() -> context.getHost().invalidate());
            context.getImagePool().setImage(thumbnailReceiver, thumbnailUrl, null);
        } else {
            thumbnailReceiver = null;
        }
        Theme.createChatResources(context.getHost().getContext(), false);
        domainPaint = cloneTextPaint(Theme.chat_replyNamePaint, domainColor);
        titlePaint = cloneTextPaint(Theme.chat_replyNamePaint, context.getTextColor());
        descriptionPaint = cloneTextPaint(Theme.chat_replyTextPaint, context.getTextColor());
        descriptionPaint.linkColor = linkLine.getColor();
        textBuilder = new PostBodyTextBuilder(context);
        linkPreviewBounce = new ButtonBounce(context.getHost(), 2.0f, 2.0f);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
    }

    @Override
    public int horizontalInsetPx() {
        return outerHorizontalInsetPx();
    }

    @Override
    public int measure(int maxWidthPx) {
        int linkPreviewY = linkPreviewTopInsetPx();
        int textLeft = textLeftPx();
        int textRight = textRightPx();
        boolean smallImage = shouldUseSmallImageLayout(block);
        int thumbSize = thumbnailReceiver != null && smallImage ? smallImageSidePx() : 0;
        int textX = textLeft;
        int textWidth = Math.max(maxWidthPx - textX - textRight, AndroidUtilities.dp(120f));
        int smallTextWidth = thumbSize > 0
                ? smallImageTextWidthPx(textWidth, thumbSize)
                : textWidth;
        int restLinesCount = thumbSize > 0 ? smallImageTextLines() : 0;
        int domainReservedLines = restLinesCount;
        StaticLayout domainRawLayout = block.getDomain() != null && !block.getDomain().trim().isEmpty()
                ? buildSiteNameLayout(block.getDomain(), domainPaint, textWidth, smallTextWidth, restLinesCount)
                : null;
        if (domainRawLayout != null && thumbSize > 0) {
            restLinesCount = Math.max(0, restLinesCount - domainRawLayout.getLineCount());
        }
        int titleReservedLines = restLinesCount;
        PositionedLayout titleRawLayout = buildTitleLayout(textWidth, smallTextWidth, restLinesCount);
        if (thumbSize > 0 && titleRawLayout != null) {
            restLinesCount = Math.max(0, restLinesCount - titleRawLayout.layout.getLineCount());
        }
        int descriptionReservedLines = restLinesCount;
        int descriptionTextWidth = descriptionTextWidthAfterTitlePx(
                textWidth,
                thumbSize,
                thumbSize > 0 && titleRawLayout != null && isRtlTitleLayout(titleRawLayout.layout)
        );
        int descriptionSmallTextWidth = thumbSize > 0
                ? smallImageDescriptionTextWidthPx(descriptionTextWidth, thumbSize)
                : descriptionTextWidth;
        PositionedLayout descriptionRawLayout = buildDescriptionLayout(descriptionTextWidth, descriptionSmallTextWidth, restLinesCount);
        boolean largeImage = thumbnailReceiver != null && !smallImage;
        int previewRight = linkPreviewWidthPx(
                maxWidthPx,
                thumbSize,
                largeImage,
                domainRawLayout,
                domainReservedLines,
                titleRawLayout != null ? titleRawLayout.layout : null,
                titleReservedLines,
                descriptionRawLayout != null ? descriptionRawLayout.layout : null,
                descriptionReservedLines
        );
        int linkPreviewX = linkPreviewXOffsetPx();
        int previewWidth = linkPreviewWidthFromRightPx(previewRight);

        int drawY = linkPreviewY;
        int measuredContentHeight = linkPreviewY;
        int smallImageStartY = thumbSize > 0 ? smallImageStartYPx(linkPreviewY) : 0;
        PositionedLayout positionedDomain = null;
        if (domainRawLayout != null) {
            positionedDomain = new PositionedLayout(
                    domainRawLayout,
                    linkPreviewX + previewTextXPx(previewWidth, domainRawLayout, thumbSize > 0, domainReservedLines),
                    drawY + siteNameTextYOffsetPx()
            );
            drawY += domainRawLayout.getHeight();
            measuredContentHeight += domainRawLayout.getHeight();
        }
        PositionedLayout positionedTitle = null;
        if (titleRawLayout != null) {
            if (drawY != linkPreviewY) {
                drawY += titleSpacingAfterSiteNamePx();
                measuredContentHeight += interLayoutGapPx();
            }
            positionedTitle = new PositionedLayout(
                    titleRawLayout.layout,
                    titleRawLayout.builtText,
                    linkPreviewX + previewTextXPx(previewWidth, titleRawLayout.layout, thumbSize > 0, titleReservedLines),
                    drawY + previewTextYOffsetPx(),
                    titleSpoilersRevealed
            );
            drawY += titleRawLayout.layout.getHeight();
            measuredContentHeight += titleRawLayout.layout.getHeight();
        }
        PositionedLayout positionedDescription = null;
        if (descriptionRawLayout != null) {
            if (drawY != linkPreviewY) {
                drawY += interLayoutGapPx();
                measuredContentHeight += interLayoutGapPx();
            }
            positionedDescription = new PositionedLayout(
                    descriptionRawLayout.layout,
                    descriptionRawLayout.builtText,
                    linkPreviewX + previewDescriptionTextXPx(previewWidth, descriptionRawLayout.layout, descriptionReservedLines),
                    drawY + previewTextYOffsetPx(),
                    descriptionSpoilersRevealed
            );
            drawY += descriptionRawLayout.layout.getHeight();
            measuredContentHeight += descriptionRawLayout.layout.getHeight();
        }

        int chromeHeight = linkPreviewChromeHeightPx(
                domainRawLayout,
                titleRawLayout != null ? titleRawLayout.layout : null,
                descriptionRawLayout != null ? descriptionRawLayout.layout : null
        );
        RectF thumbnailBounds = null;
        if (largeImage) {
            if (drawY != linkPreviewY) {
                drawY += largeImageTopGapPx();
                measuredContentHeight += largeImageTopGapPx();
                chromeHeight += largeImageTopGapPx();
            }
            thumbnailBounds = largeImageBoundsInPreviewPx(previewRight, block.getThumbnailWidthPx(), block.getThumbnailHeightPx());
            thumbnailBounds.offset(0f, drawY);
            drawY += (int) thumbnailBounds.height();
            measuredContentHeight += (int) thumbnailBounds.height();
            chromeHeight += (int) thumbnailBounds.height();
        }

        boolean previewHasText = hasPreviewText(
                domainRawLayout,
                titleRawLayout != null ? titleRawLayout.layout : null,
                descriptionRawLayout != null ? descriptionRawLayout.layout : null
        );
        if (thumbSize > 0) {
            chromeHeight = smallImageChromeHeightPx(chromeHeight);
        }
        chromeHeight += linkPreviewBackgroundBottomInsetPx();
        if (thumbnailReceiver != null && previewHasText) {
            chromeHeight += mediaTextExtraChromePx();
        }
        int chromeBottom = linkPreviewChromeBottomPx(chromeHeight);
        int totalHeight = Math.max(
                Math.max(measuredContentHeight, chromeBottom),
                thumbSize > 0 ? smallImageStartY + thumbSize + smallImageBottomInsetPx() : 0
        ) + linkPreviewOutsideBottomInsetPx();
        if (thumbSize > 0) {
            thumbnailBounds = smallImageBoundsInPreviewPx(previewWidth, thumbSize, smallImageStartY);
        }
        OneboxPreviewLayout previousLayout = previewLayout;
        if (previousLayout != null) {
            previousLayout.detachText();
        }
        previewLayout = new OneboxPreviewLayout(
                linkPreviewCardBoundsPx(previewRight, totalHeight),
                linkPreviewHitBoundsPx(previewRight, linkPreviewY, chromeBottom),
                thumbnailBounds,
                positionedDomain,
                positionedTitle,
                positionedDescription,
                chromeBottom,
                totalHeight
        );
        if (context.getHost().isAttachedToWindow()) {
            previewLayout.attachText(context.getHost());
        }
        return totalHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        OneboxPreviewLayout layout = previewLayout;
        if (layout == null) {
            return;
        }
        float scale = linkPreviewBounce.getScale(0.01f);
        int restoreCount = -1;
        if (scale != 1f) {
            restoreCount = canvas.save();
            canvas.scale(scale, scale, layout.cardBounds.centerX(), linkPreviewBouncePivotYPx(layout.chromeBottom));
        }
        int domainColor = linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        domainPaint.setColor(domainColor);
        descriptionPaint.linkColor = linkLine.getColor();
        linkLineRect.set(layout.cardBounds.left, 0f, layout.cardBounds.right, layout.chromeBottom);
        float rad = linkPreviewBackgroundRadiusDp();
        linkLine.drawBackground(canvas, linkLineRect, rad, rad, rad, 1f);
        drawLinkPreviewSelector(canvas);
        linkLine.drawLine(canvas, linkLineRect, 1f);
        if (thumbnailReceiver != null) {
            RectF bounds = layout.thumbnailBounds;
            if (bounds != null) {
                boolean hidden = Objects.equals(context.getHiddenImageUrlProvider().invoke(), thumbnailUrl);
                boolean loading = !thumbnailReceiver.hasBitmapImage() || thumbnailReceiver.getCurrentAlpha() != 1.0f;
                if (hidden || loading) {
                    int mediaRadius = linkPreviewMediaRoundRadiusPx();
                    canvas.drawRoundRect(bounds, mediaRadius, mediaRadius, placeholderPaint);
                }
                if (!hidden) {
                    thumbnailReceiver.setImageCoords(bounds);
                    thumbnailReceiver.draw(canvas);
                }
            }
        }
        if (layout.domainLayout != null) {
            layout.domainLayout.draw(canvas, context.getHost()::invalidate);
        }
        if (layout.titleLayout != null) {
            layout.titleLayout.draw(canvas, context.getHost()::invalidate);
        }
        if (layout.descriptionLayout != null) {
            layout.descriptionLayout.draw(canvas, context.getHost()::invalidate);
        }
        if (restoreCount >= 0) {
            canvas.restoreToCount(restoreCount);
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        OneboxPreviewLayout layout = previewLayout;
        if (layout == null) {
            return;
        }
        if (layout.domainLayout != null) {
            blocks.add(layout.domainLayout.asSelectionBlock(x, y));
        }
        if (layout.titleLayout != null) {
            blocks.add(layout.titleLayout.asSelectionBlock(x, y));
        }
        if (layout.descriptionLayout != null) {
            blocks.add(layout.descriptionLayout.asSelectionBlock(x, y));
        }
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        OneboxPreviewLayout layout = previewLayout;
        if (layout == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (layout.titleLayout != null
                        && layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed)) {
                    return true;
                }
                if (layout.descriptionLayout != null
                        && layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed)) {
                    return true;
                }
                pressedTarget = layout.hitTarget(localX, localY);
                if (pressedTarget != null) {
                    setLinkPreviewPressed(true, localX, localY);
                    linkPreviewBounce.setPressed(true);
                    context.getHost().invalidate();
                }
                return pressedTarget != null;
            case MotionEvent.ACTION_MOVE:
                if (layout.titleLayout != null && layout.titleLayout.hasPressedRichText()) {
                    return layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed);
                }
                if (layout.descriptionLayout != null && layout.descriptionLayout.hasPressedRichText()) {
                    return layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed);
                }
                if (pressedTarget != null && layout.hitTarget(localX, localY) == null) {
                    setLinkPreviewPressed(false, localX, localY);
                    pressedTarget = null;
                    linkPreviewBounce.setPressed(false);
                    context.getHost().invalidate();
                    return false;
                }
                if (pressedTarget != null) {
                    setLinkPreviewHotspot(localX, localY);
                }
                return pressedTarget != null;
            case MotionEvent.ACTION_UP:
                if (layout.titleLayout != null && layout.titleLayout.hasPressedRichText()) {
                    return layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed);
                }
                if (layout.descriptionLayout != null && layout.descriptionLayout.hasPressedRichText()) {
                    return layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed);
                }
                OneboxHitTarget target = pressedTarget;
                if (target == null) {
                    return false;
                }
                setLinkPreviewPressed(false, localX, localY);
                pressedTarget = null;
                linkPreviewBounce.setPressed(false);
                if (layout.hitTarget(localX, localY) == null) {
                    return false;
                }
                context.getHost().playSoundEffect(SoundEffectConstants.CLICK);
                if (target == OneboxHitTarget.Thumbnail && shouldOpenThumbnailImage(block)) {
                    List<GalleryImage> gallery = thumbnailUrl != null
                            ? Collections.singletonList(new GalleryImage(thumbnailUrl, thumbnailUrl, block.getThumbnailWidthPx(), block.getThumbnailHeightPx(), null))
                            : Collections.emptyList();
                    context.getDelegate().onImageClick(
                            thumbnailUrl != null ? thumbnailUrl : resolvedHref,
                            gallery,
                            layout.thumbnailBounds != null ? new RectF(layout.thumbnailBounds) : null,
                            context.getHost(),
                            imageUrl -> Objects.equals(imageUrl, thumbnailUrl) && layout.thumbnailBounds != null ? new RectF(layout.thumbnailBounds) : null,
                            imageUrl -> Objects.equals(imageUrl, thumbnailUrl) ? thumbnailReceiver : null,
                            imageUrl -> Objects.equals(imageUrl, thumbnailUrl) && thumbnailReceiver != null ? thumbnailReceiver.getBitmapSafe() : null,
                            imageUrl -> Objects.equals(imageUrl, thumbnailUrl) && thumbnailReceiver != null ? copyRadius(thumbnailReceiver) : null
                    );
                } else {
                    context.getDelegate().onLinkClick(resolvedHref);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (layout.titleLayout != null && layout.titleLayout.hasPressedRichText()) {
                    return layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed);
                }
                if (layout.descriptionLayout != null && layout.descriptionLayout.hasPressedRichText()) {
                    return layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed);
                }
                setLinkPreviewPressed(false, localX, localY);
                pressedTarget = null;
                linkPreviewBounce.setPressed(false);
                return true;
            default:
                break;
        }
        return pressedTarget != null;
    }

    @Override
    public void onAttachedToWindow() {
        if (thumbnailReceiver != null) {
            thumbnailReceiver.onAttachedToWindow();
        }
        if (previewLayout != null) {
            previewLayout.attachText(context.getHost());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (thumbnailReceiver != null) {
            thumbnailReceiver.onDetachedFromWindow();
        }
        if (previewLayout != null) {
            previewLayout.detachText();
        }
        setLinkPreviewPressed(false, 0f, 0f);
        pressedTarget = null;
        linkPreviewBounce.setPressed(false);
    }

    @Override
    public void applyTheme() {
        int domainColor = linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        Theme.createChatResources(context.getHost().getContext(), false);
        copyTextPaint(domainPaint, Theme.chat_replyNamePaint, domainColor);
        copyTextPaint(titlePaint, Theme.chat_replyNamePaint, context.getTextColor());
        copyTextPaint(descriptionPaint, Theme.chat_replyTextPaint, context.getTextColor());
        descriptionPaint.linkColor = linkLine.getColor();
        if (thumbnailReceiver != null) {
            thumbnailReceiver.setRoundRadius(linkPreviewMediaRoundRadiusPx());
        }
        if (linkPreviewSelector != null) {
            Theme.setSelectorDrawableColor(linkPreviewSelector, linkPreviewSelectorColor = linkLine.getBackgroundColor(), true);
        }
    }

    private void drawLinkPreviewSelector(Canvas canvas) {
        int rippleColor = linkLine.getBackgroundColor();
        if (linkPreviewSelector == null) {
            linkPreviewSelector = Theme.createRadSelectorDrawable(linkPreviewSelectorColor = rippleColor, 0, 0);
            linkPreviewSelector.setCallback(context.getHost());
            Theme.setMaskDrawableRad(linkPreviewSelector, 3, 6, 6, 3);
        } else if (linkPreviewSelectorColor != rippleColor) {
            Theme.setSelectorDrawableColor(linkPreviewSelector, linkPreviewSelectorColor = rippleColor, true);
        }
        linkPreviewSelector.setBounds(
                (int) linkLineRect.left,
                (int) linkLineRect.top,
                (int) linkLineRect.right,
                (int) linkLineRect.bottom
        );
        linkPreviewSelector.draw(canvas);
    }

    private void setLinkPreviewPressed(boolean pressed, float x, float y) {
        if (linkPreviewSelector == null) {
            if (!pressed) {
                return;
            }
            linkPreviewSelector = Theme.createRadSelectorDrawable(linkPreviewSelectorColor = linkLine.getBackgroundColor(), 0, 0);
            linkPreviewSelector.setCallback(context.getHost());
            Theme.setMaskDrawableRad(linkPreviewSelector, 3, 6, 6, 3);
        }
        linkPreviewSelector.setHotspot(x, y);
        linkPreviewSelector.setState(pressed ? PRESSED_STATE : StateSet.NOTHING);
    }

    private void setLinkPreviewHotspot(float x, float y) {
        if (linkPreviewSelector != null) {
            linkPreviewSelector.setHotspot(x, y);
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return Objects.equals(imageUrl, thumbnailUrl) && previewLayout != null && previewLayout.thumbnailBounds != null
                ? new RectF(previewLayout.thumbnailBounds)
                : null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        return Objects.equals(imageUrl, thumbnailUrl) ? thumbnailReceiver : null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        return Objects.equals(imageUrl, thumbnailUrl) && thumbnailReceiver != null ? thumbnailReceiver.getBitmapSafe() : null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        return Objects.equals(imageUrl, thumbnailUrl) && thumbnailReceiver != null ? copyRadius(thumbnailReceiver) : null;
    }

    private static TextPaint cloneTextPaint(TextPaint source, int color) {
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        copyTextPaint(paint, source, color);
        return paint;
    }

    private static void copyTextPaint(TextPaint target, TextPaint source, int color) {
        target.set(source);
        target.setTextSize(source.getTextSize());
        target.setTypeface(source.getTypeface());
        target.setColor(color);
    }

    private static StaticLayout buildPreviewLayout(CharSequence text, TextPaint paint, int width, int smallWidth, int smallLinesCount, int maxLines, float spacingExtraDp) {
        CharSequence source = text.length() > 360 ? text.subSequence(0, 360) : text;
        if (smallLinesCount > 0 && smallWidth < width) {
            return buildSmallImageLayout(source, paint, width, smallWidth, smallLinesCount, maxLines, spacingExtraDp).layout;
        }
        return createPreviewStaticLayout(source, paint, width, maxLines, spacingExtraDp);
    }

    private static StaticLayout buildSiteNameLayout(CharSequence text, TextPaint paint, int width, int smallWidth, int smallLinesCount) {
        CharSequence source = text.length() > 360 ? text.subSequence(0, 360) : text;
        if (smallLinesCount > 0 && smallWidth < width) {
            return buildSmallImageLayout(source, paint, width, smallWidth, smallLinesCount, 1, PREVIEW_TEXT_LINE_SPACING_DP).layout;
        }
        return new StaticLayout(source, paint, siteNameLayoutWidthPx(source, paint, width), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    static int siteNameLayoutWidthPx(CharSequence text, TextPaint paint, int maxWidthPx) {
        return Math.max(1, Math.min((int) Math.ceil(paint.measureText(text.toString()) + 1f), maxWidthPx));
    }

    private static PreviewBuildResult buildRichPreviewLayout(BuiltText builtText, TextPaint paint, int width, int smallWidth, int smallLinesCount, int maxLines, float spacingExtraDp) {
        CharSequence source = builtText.getSpannable();
        if (smallLinesCount > 0 && smallWidth < width) {
            SmallImageTextResult smallResult = buildSmallImageLayout(source, paint, width, smallWidth, smallLinesCount, maxLines, spacingExtraDp);
            return new PreviewBuildResult(smallResult.layout, shiftBuiltText(builtText, smallResult));
        }
        StaticLayout layout = createPreviewStaticLayout(source, paint, width, maxLines, spacingExtraDp);
        return new PreviewBuildResult(layout, builtText);
    }

    private static SmallImageTextResult buildSmallImageLayout(CharSequence source, TextPaint paint, int maxWidth, int smallWidth, int linesCount, int maxLines, float spacingExtraDp) {
        CharSequence emojiSource = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(emojiSource);
        ArrayList<Integer> insertedBreakOffsets = new ArrayList<>();
        int addedChars = 0;
        StaticLayout smallLayout = new StaticLayout(
                emojiSource,
                paint,
                Math.max(smallWidth, 1),
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0.0f,
                false
        );
        int lineLimit = Math.min(Math.min(linesCount, smallLayout.getLineCount()), maxLines);
        for (int i = 0; i < lineLimit; i++) {
            int lineStart = smallLayout.getLineStart(i);
            int lineEnd = smallLayout.getLineEnd(i);
            if (smallLayout.getLineLeft(i) != 0
                    || isRtlCharAt(smallLayout, lineStart)
                    || isRtlCharAt(smallLayout, Math.max(lineStart, lineEnd - 1))) {
                maxWidth = smallWidth;
            }
            int pos = lineEnd;
            if (pos >= emojiSource.length()) {
                break;
            }
            pos--;
            int builderPos = pos + addedChars;
            if (builderPos < 0 || builderPos >= stringBuilder.length()) {
                break;
            }
            if (stringBuilder.charAt(builderPos) == ' ') {
                stringBuilder.replace(builderPos, builderPos + 1, "\n");
            } else if (stringBuilder.charAt(builderPos) != '\n') {
                stringBuilder.insert(builderPos, "\n");
                insertedBreakOffsets.add(pos);
                addedChars++;
            }
            if (i == smallLayout.getLineCount() - 1 || i == maxLines - 1) {
                break;
            }
        }
        StaticLayout layout = createPreviewStaticLayoutRaw(stringBuilder, paint, maxWidth, maxLines, spacingExtraDp);
        return new SmallImageTextResult(layout, stringBuilder, insertedBreakOffsets);
    }

    private static BuiltText shiftBuiltText(BuiltText builtText, SmallImageTextResult smallResult) {
        if (smallResult.insertedBreakOffsets.isEmpty()) {
            return new BuiltText(smallResult.text, builtText.getPendingImages(), builtText.getLinks());
        }
        ArrayList<LinkRegion> shiftedLinks = new ArrayList<>(builtText.getLinks().size());
        for (LinkRegion link : builtText.getLinks()) {
            shiftedLinks.add(new LinkRegion(
                    shiftStartOffset(link.getStart(), smallResult.insertedBreakOffsets),
                    shiftEndOffset(link.getEnd(), smallResult.insertedBreakOffsets),
                    link.getUrl(),
                    link.getOnClick()
            ));
        }
        return new BuiltText(smallResult.text, builtText.getPendingImages(), shiftedLinks);
    }

    private static int shiftStartOffset(int offset, ArrayList<Integer> insertedBreakOffsets) {
        int shifted = offset;
        for (int i = 0; i < insertedBreakOffsets.size(); i++) {
            if (offset >= insertedBreakOffsets.get(i)) {
                shifted++;
            }
        }
        return shifted;
    }

    private static int shiftEndOffset(int offset, ArrayList<Integer> insertedBreakOffsets) {
        int shifted = offset;
        for (int i = 0; i < insertedBreakOffsets.size(); i++) {
            if (offset > insertedBreakOffsets.get(i)) {
                shifted++;
            }
        }
        return shifted;
    }

    private static boolean isRtlCharAt(StaticLayout layout, int offset) {
        return offset >= 0 && offset < layout.getText().length() && layout.isRtlCharAt(offset);
    }

    private static StaticLayout createPreviewStaticLayout(CharSequence source, TextPaint paint, int width, int maxLines, float spacingExtraDp) {
        if (source.length() > 0 && source.charAt(source.length() - 1) == '\n') {
            source = source.subSequence(0, source.length() - 1);
        }
        source = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        return createPreviewStaticLayoutRaw(source, paint, width, maxLines, spacingExtraDp);
    }

    private static StaticLayout createPreviewStaticLayoutRaw(CharSequence source, TextPaint paint, int width, int maxLines, float spacingExtraDp) {
        int safeWidth = Math.max(width, 1);
        StaticLayout layout = StaticLayoutEx.createStaticLayout(
                source,
                paint,
                safeWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                AndroidUtilities.dp(spacingExtraDp),
                false,
                TextUtils.TruncateAt.END,
                safeWidth,
                maxLines,
                true
        );
        if (layout != null) {
            return layout;
        }
        return previewBuilderRaw(source, paint, width, spacingExtraDp)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static StaticLayout.Builder previewBuilderRaw(CharSequence source, TextPaint paint, int width, float spacingExtraDp) {
        return StaticLayout.Builder.obtain(source, 0, source.length(), paint, Math.max(width, 1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(AndroidUtilities.dp(spacingExtraDp), 1.0f)
                .setIncludePad(false);
    }

    private PositionedLayout buildTitleLayout(int textWidth, int smallTextWidth, int smallLinesCount) {
        if (!hasPreviewTitle(block)) {
            return null;
        }
        String html = block.getTitleHtml();
        if (html == null || html.trim().isEmpty()) {
            html = TextUtils.htmlEncode(block.getTitle());
        } else {
            html = previewHtmlWithoutLinks(html);
        }
        BuiltText builtText = textBuilder.build(html, titlePaint);
        PreviewBuildResult buildResult = buildRichPreviewLayout(builtText, titlePaint, textWidth, smallTextWidth, smallLinesCount, titleMaxLines(), PREVIEW_TEXT_LINE_SPACING_DP);
        return new PositionedLayout(
                buildResult.layout,
                buildResult.builtText,
                0f,
                0f
        );
    }

    private PositionedLayout buildDescriptionLayout(int textWidth, int smallTextWidth, int smallLinesCount) {
        String html = block.getDescriptionHtml();
        if (html == null || html.trim().isEmpty()) {
            String description = block.getDescription();
            if (description == null || description.trim().isEmpty()) {
                return null;
            }
            html = TextUtils.htmlEncode(description);
        } else {
            html = previewHtmlWithoutLinks(html);
        }
        BuiltText builtText = textBuilder.build(html, descriptionPaint);
        PreviewBuildResult buildResult = buildRichPreviewLayout(builtText, descriptionPaint, textWidth, smallTextWidth, smallLinesCount, descriptionMaxLines(), PREVIEW_TEXT_LINE_SPACING_DP);
        return new PositionedLayout(
                buildResult.layout,
                buildResult.builtText,
                0f,
                0f
        );
    }

    private void markTitleSpoilersRevealed() {
        titleSpoilersRevealed = true;
        if (inlineState != null && titleSpoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(titleSpoilerStateKey);
        }
    }

    private void markDescriptionSpoilersRevealed() {
        descriptionSpoilersRevealed = true;
        if (inlineState != null && descriptionSpoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(descriptionSpoilerStateKey);
        }
    }

    private static String resolveUrlOrFallback(String baseUrl, String rawUrl, String fallback) {
        try {
            return CookedPostBodyParserKt.resolvePostImageUrl(baseUrl, rawUrl);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    static int textLeftPx() {
        return AndroidUtilities.dp(TEXT_LEFT_DP);
    }

    static int outerHorizontalInsetPx() {
        return AndroidUtilities.dp(OUTER_HORIZONTAL_INSET_DP);
    }

    static int linkPreviewXOffsetPx() {
        return -AndroidUtilities.dp(LINK_PREVIEW_X_OFFSET_DP);
    }

    static int linkPreviewWidthFromRightPx(int previewRightPx) {
        return previewRightPx - linkPreviewXOffsetPx();
    }

    static int textRightPx() {
        return AndroidUtilities.dp(TEXT_RIGHT_DP);
    }

    static int linkPreviewTopInsetPx() {
        return AndroidUtilities.dp(LINK_PREVIEW_TOP_INSET_DP);
    }

    static int linkPreviewBottomInsetPx() {
        return AndroidUtilities.dp(LINK_PREVIEW_BOTTOM_INSET_DP);
    }

    static int linkPreviewBackgroundBottomInsetPx() {
        return AndroidUtilities.dp(LINK_PREVIEW_BACKGROUND_BOTTOM_INSET_DP);
    }

    static int linkPreviewOutsideBottomInsetPx() {
        return AndroidUtilities.dp(LINK_PREVIEW_OUTSIDE_BOTTOM_INSET_DP);
    }

    static int linkPreviewChromeBottomPx(int chromeHeightPx) {
        return linkPreviewTopInsetPx() + chromeHeightPx + linkPreviewBottomInsetPx();
    }

    static int linkPreviewHitBottomPx(int chromeBottomPx) {
        return chromeBottomPx + AndroidUtilities.dp(8f);
    }

    static float linkPreviewBouncePivotYPx(int chromeBottomPx) {
        return linkPreviewTopInsetPx() + (chromeBottomPx - linkPreviewTopInsetPx()) / 2f;
    }

    static int siteNameTextYOffsetPx() {
        return -AndroidUtilities.dp(SITE_NAME_TEXT_Y_OFFSET_DP);
    }

    static int previewTextYOffsetPx() {
        return -AndroidUtilities.dp(PREVIEW_TEXT_Y_OFFSET_DP);
    }

    static int smallImageStartYPx(int linkPreviewY) {
        return linkPreviewY - AndroidUtilities.dp(1f);
    }

    static int smallImageBottomInsetPx() {
        return AndroidUtilities.dp(SMALL_IMAGE_BOTTOM_INSET_DP);
    }

    static int smallImageChromeHeightPx(int contentHeightPx) {
        int height = contentHeightPx;
        if (smallImageMinChromeHeightPx() > height) {
            height = smallImageMinChromeHeightPx();
        }
        return Math.max(0, height - smallImageChromeBottomTrimPx());
    }

    static int smallImageMinChromeHeightPx() {
        return AndroidUtilities.dp(50f);
    }

    static int smallImageChromeBottomTrimPx() {
        return AndroidUtilities.dp(8f);
    }

    static int smallImageRightInsetPx() {
        return AndroidUtilities.dp(SMALL_IMAGE_RIGHT_INSET_DP);
    }

    static int smallImageSidePx() {
        return AndroidUtilities.dp(SMALL_IMAGE_SIDE_DP);
    }

    static int smallImageSideMarginPx() {
        return AndroidUtilities.dp(SMALL_IMAGE_SIDE_MARGIN_DP);
    }

    static int smallImageTextWidthPx(int textWidth, int thumbSize) {
        return Math.max(textWidth - thumbSize - smallImageSideMarginPx(), AndroidUtilities.dp(80f));
    }

    static int smallImageDescriptionTextWidthPx(int textWidth, int thumbSize) {
        return Math.max(textWidth - thumbSize - smallImageSideMarginPx() * 2, AndroidUtilities.dp(80f));
    }

    static int descriptionTextWidthAfterTitlePx(int textWidth, int thumbSize, boolean titleIsRtl) {
        if (!titleIsRtl || thumbSize <= 0) {
            return textWidth;
        }
        return Math.max(textWidth - thumbSize, AndroidUtilities.dp(80f));
    }

    static int linkPreviewWidthPx(
            int maxWidthPx,
            int thumbSize,
            boolean largeImage,
            StaticLayout domainLayout,
            int domainReservedLines,
            StaticLayout titleLayout,
            int titleReservedLines,
            StaticLayout descriptionLayout,
            int descriptionReservedLines
    ) {
        if (largeImage) {
            return maxWidthPx;
        }
        int contentWidth = 0;
        boolean smallImage = thumbSize > 0;
        int reservedWidth = thumbSize + smallImageSideMarginPx();
        if (domainLayout != null) {
            contentWidth = Math.max(contentWidth, previewLayoutWidthPx(domainLayout, smallImage, domainReservedLines, reservedWidth));
        }
        if (titleLayout != null) {
            contentWidth = Math.max(contentWidth, previewLayoutWidthPx(titleLayout, smallImage, titleReservedLines, reservedWidth));
        }
        if (descriptionLayout != null) {
            contentWidth = Math.max(contentWidth, previewDescriptionLayoutWidthPx(descriptionLayout, descriptionReservedLines));
        }
        int minWidth = AndroidUtilities.dp(120f);
        if (smallImage) {
            minWidth = Math.max(minWidth, thumbSize + smallImageRightInsetPx() + textLeftPx() + smallImageSideMarginPx());
        }
        int previewWidth = contentWidth + textLeftPx() + textRightPx();
        return Math.max(Math.min(previewWidth, maxWidthPx), Math.min(minWidth, maxWidthPx));
    }

    static float previewTextXPx(int previewWidth, StaticLayout layout, boolean smallImage, int reservedLines) {
        int layoutLeft = layoutLeftPx(layout);
        if (Math.abs(layoutLeft) <= 1) {
            return textLeftPx();
        }
        int layoutWidth = previewLayoutWidthPx(layout, smallImage, reservedLines, smallImageSidePx() + smallImageSideMarginPx());
        return textLeftPx() + previewWidth - AndroidUtilities.dp(20f) - layoutWidth - layoutLeft;
    }

    static float previewDescriptionTextXPx(int previewWidth, StaticLayout layout, int reservedLines) {
        int layoutLeft = layoutLeftPx(layout);
        if (Math.abs(layoutLeft) <= 1) {
            return textLeftPx();
        }
        int layoutWidth = previewDescriptionLayoutWidthPx(layout, reservedLines);
        return textLeftPx() + previewWidth - AndroidUtilities.dp(20f) - layoutWidth - layoutLeft;
    }

    private static int layoutLeftPx(StaticLayout layout) {
        int layoutLeft = layout.getWidth();
        for (int i = 0; i < layout.getLineCount(); i++) {
            layoutLeft = (int) Math.min(layoutLeft, layout.getLineLeft(i));
        }
        return layoutLeft;
    }

    private static int previewLayoutWidthPx(StaticLayout layout, boolean smallImage, int reservedLines, int reservedWidth) {
        int layoutLeft = layout.getWidth();
        int layoutWidth = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            layoutLeft = (int) Math.min(layoutLeft, layout.getLineLeft(i));
            float lineWidth = layout.getLineWidth(i);
            if (i < reservedLines || layoutLeft != 0 && smallImage) {
                lineWidth += reservedWidth;
            }
            layoutWidth = (int) Math.max(layoutWidth, lineWidth);
        }
        return layoutWidth;
    }

    private static int previewDescriptionLayoutWidthPx(StaticLayout layout, int reservedLines) {
        int layoutLeft = layout.getWidth();
        int layoutRight = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            layoutLeft = (int) Math.min(layoutLeft, layout.getLineLeft(i));
            int addRight = 0;
            if (i < reservedLines || reservedLines != 0 && layoutLeft != 0) {
                addRight = smallImageSidePx() + smallImageSideMarginPx();
            }
            layoutRight = (int) Math.max(layoutRight, layout.getLineRight(i) + addRight);
        }
        return Math.abs(layoutRight - layoutLeft);
    }

    static int smallImageRoundRadiusPx() {
        return linkPreviewMediaRoundRadiusPx();
    }

    static int linkPreviewMediaRoundRadiusPx() {
        int minRad = AndroidUtilities.dp(SMALL_IMAGE_ROUND_RADIUS_DP);
        int rad = SharedConfig.bubbleRadius > 2
                ? AndroidUtilities.dp(SharedConfig.bubbleRadius - 2f)
                : AndroidUtilities.dp(SharedConfig.bubbleRadius);
        return Math.min(minRad, rad);
    }

    static int largeImageLeftInsetPx() {
        return AndroidUtilities.dp(LARGE_IMAGE_LEFT_INSET_DP);
    }

    static int largeImageRightInsetPx() {
        return AndroidUtilities.dp(LARGE_IMAGE_RIGHT_INSET_DP);
    }

    static int largeImageTopGapPx() {
        return AndroidUtilities.dp(LARGE_IMAGE_TOP_GAP_DP);
    }

    static int largeImageMinHeightPx() {
        return AndroidUtilities.dp(LARGE_IMAGE_MIN_HEIGHT_DP);
    }

    static int mediaTextExtraChromePx() {
        return AndroidUtilities.dp(MEDIA_TEXT_EXTRA_CHROME_DP);
    }

    static int largeImageMaxHeightPx() {
        return AndroidUtilities.displaySize.y / 3;
    }

    static int largeImageMeasureWidthPx(int maxWidthPx) {
        return Math.max(maxWidthPx - AndroidUtilities.dp(2f), 1);
    }

    static int largeImageDrawWidthPx(int maxWidthPx) {
        return Math.max(maxWidthPx - largeImageLeftInsetPx() - largeImageRightInsetPx(), 1);
    }

    static int largeImageHeightPx(int widthPx, int sourceWidthPx, int sourceHeightPx) {
        int width = sourceWidthPx > 0 ? sourceWidthPx : 30;
        int height = sourceHeightPx > 0 ? sourceHeightPx : 50;
        float scale = width / (float) Math.max(widthPx, 1);
        height = (int) (height / scale);
        int maxHeight = largeImageMaxHeightPx();
        if (height > maxHeight) {
            height = maxHeight;
        }
        if (height < largeImageMinHeightPx()) {
            height = largeImageMinHeightPx();
        }
        return height;
    }

    static RectF largeImageBoundsPx(int maxWidthPx, int sourceWidthPx, int sourceHeightPx) {
        int photoWidth = largeImageDrawWidthPx(maxWidthPx);
        int photoHeight = largeImageHeightPx(largeImageMeasureWidthPx(maxWidthPx), sourceWidthPx, sourceHeightPx);
        return new RectF(
                largeImageLeftInsetPx(),
                0f,
                largeImageLeftInsetPx() + photoWidth,
                photoHeight
        );
    }

    static RectF largeImageBoundsInPreviewPx(int previewRightPx, int sourceWidthPx, int sourceHeightPx) {
        RectF bounds = largeImageBoundsPx(linkPreviewWidthFromRightPx(previewRightPx), sourceWidthPx, sourceHeightPx);
        bounds.offset(linkPreviewXOffsetPx(), 0f);
        return bounds;
    }

    static RectF smallImageBoundsInPreviewPx(int previewWidthPx, int thumbSizePx, int smallImageStartYPx) {
        int right = linkPreviewXOffsetPx() + previewWidthPx - smallImageRightInsetPx();
        return new RectF(right - thumbSizePx, smallImageStartYPx, right, smallImageStartYPx + thumbSizePx);
    }

    static RectF linkPreviewCardBoundsPx(int previewRightPx, int totalHeightPx) {
        return new RectF(linkPreviewXOffsetPx(), 0f, previewRightPx, totalHeightPx);
    }

    static RectF linkPreviewHitBoundsPx(int previewRightPx, int linkPreviewYPx, int chromeBottomPx) {
        return new RectF(0f, linkPreviewYPx, previewRightPx, linkPreviewHitBottomPx(chromeBottomPx));
    }

    static int linkPreviewChromeHeightPx(StaticLayout domainLayout, StaticLayout titleLayout, StaticLayout descriptionLayout) {
        int height = 0;
        if (domainLayout != null) {
            height += domainLayout.getHeight();
        }
        if (titleLayout != null) {
            if (height != 0) {
                height += interLayoutGapPx();
            }
            height += titleLayout.getHeight();
        }
        if (descriptionLayout != null) {
            if (height != 0) {
                height += interLayoutGapPx();
            }
            height += descriptionLayout.getHeight();
        }
        return height;
    }

    private static boolean hasPreviewText(StaticLayout domainLayout, StaticLayout titleLayout, StaticLayout descriptionLayout) {
        return domainLayout != null || titleLayout != null || descriptionLayout != null;
    }

    static boolean shouldUseSmallImageLayout(CookedPostBlock.Onebox block) {
        return block.getThumbnailUrl() != null
                && (hasPreviewTitle(block) || hasPreviewDescription(block))
                && (block.getForceSmallMedia()
                || !block.getForceLargeMedia() && isSmallImageLinkPreviewType(block.getPreviewType()));
    }

    static boolean shouldOpenThumbnailImage(CookedPostBlock.Onebox block) {
        return block.getThumbnailUrl() != null && !shouldUseSmallImageLayout(block);
    }

    private static boolean hasPreviewTitle(CookedPostBlock.Onebox block) {
        String titleHtml = block.getTitleHtml();
        if (titleHtml != null && !titleHtml.trim().isEmpty()) {
            return true;
        }
        String title = block.getTitle();
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        String trimmedTitle = title.trim();
        boolean fallbackTitle = trimmedTitle.equals(block.getDomain()) || trimmedTitle.equals(block.getHref());
        if (!fallbackTitle) {
            return true;
        }
        return !hasPreviewDescription(block)
                && (block.getDomain() == null || block.getDomain().trim().isEmpty())
                && (block.getThumbnailUrl() == null || block.getThumbnailUrl().trim().isEmpty());
    }

    private static boolean hasPreviewDescription(CookedPostBlock.Onebox block) {
        String descriptionHtml = block.getDescriptionHtml();
        if (descriptionHtml != null && !descriptionHtml.trim().isEmpty()) {
            return true;
        }
        String description = block.getDescription();
        return description != null && !description.trim().isEmpty();
    }

    private static boolean isSmallImageLinkPreviewType(String type) {
        return "app".equals(type) || "profile".equals(type) ||
                "article".equals(type) || "telegram_bot".equals(type) ||
                "telegram_user".equals(type) || "telegram_channel".equals(type) || "telegram_channel_direct".equals(type) ||
                "telegram_megagroup".equals(type) || "telegram_voicechat".equals(type) || "telegram_videochat".equals(type) ||
                "telegram_livestream".equals(type) || "telegram_channel_boost".equals(type) || "telegram_group_boost".equals(type) ||
                "telegram_aicomposetone".equals(type);
    }

    private static boolean isRtlTitleLayout(StaticLayout layout) {
        int titleLayoutLeft = layout.getWidth();
        for (int i = 0; i < layout.getLineCount(); i++) {
            titleLayoutLeft = (int) Math.min(titleLayoutLeft, layout.getLineLeft(i));
        }
        return titleLayoutLeft != 0;
    }

    static int interLayoutGapPx() {
        return AndroidUtilities.dp(INTER_LAYOUT_GAP_DP);
    }

    static float linkPreviewBackgroundRadiusDp() {
        return (float) Math.floor(SharedConfig.bubbleRadius / 3f);
    }

    static int titleSpacingAfterSiteNamePx() {
        return AndroidUtilities.dp(4f);
    }

    static int previewTextLineSpacingPx() {
        return AndroidUtilities.dp(PREVIEW_TEXT_LINE_SPACING_DP);
    }

    static int regularSiteNameLineSpacingPx() {
        return 0;
    }

    static int smallImageSiteNameLineSpacingPx() {
        return previewTextLineSpacingPx();
    }

    static int smallImageTextLines() {
        return SMALL_IMAGE_TEXT_LINES;
    }

    static int titleMaxLines() {
        return TITLE_MAX_LINES;
    }

    static int descriptionMaxLines() {
        return DESCRIPTION_MAX_LINES;
    }

    static String previewHtmlWithoutLinks(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        for (Element link : document.body().select("a")) {
            link.unwrap();
        }
        return document.body().html();
    }

    private static int[] copyRadius(ImageReceiver receiver) {
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }
}
