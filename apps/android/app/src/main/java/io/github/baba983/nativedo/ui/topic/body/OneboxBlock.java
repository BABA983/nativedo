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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.util.StateSet;
import android.view.MotionEvent;
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
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class OneboxBlock implements PostBodyBlock {
    private static final int[] PRESSED_STATE = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private static final float TEXT_LEFT_DP = 10f;
    private static final float SMALL_IMAGE_SIDE_DP = 48f;
    private static final float SMALL_IMAGE_SIDE_MARGIN_DP = 10f;
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
        final RectF thumbnailBounds;
        final PositionedLayout domainLayout;
        final PositionedLayout titleLayout;
        final PositionedLayout descriptionLayout;
        final int totalHeight;

        OneboxPreviewLayout(
                RectF cardBounds,
                RectF thumbnailBounds,
                PositionedLayout domainLayout,
                PositionedLayout titleLayout,
                PositionedLayout descriptionLayout,
                int totalHeight
        ) {
            this.cardBounds = cardBounds;
            this.thumbnailBounds = thumbnailBounds;
            this.domainLayout = domainLayout;
            this.titleLayout = titleLayout;
            this.descriptionLayout = descriptionLayout;
            this.totalHeight = totalHeight;
        }

        OneboxHitTarget hitTarget(float x, float y) {
            if (thumbnailBounds != null && thumbnailBounds.contains(x, y)) {
                return OneboxHitTarget.Thumbnail;
            }
            if (descriptionLayout != null && descriptionLayout.bounds.contains(x, y)) {
                return OneboxHitTarget.Description;
            }
            if (titleLayout.bounds.contains(x, y)
                    || (domainLayout != null && domainLayout.bounds.contains(x, y))
                    || cardBounds.contains(x, y)) {
                return OneboxHitTarget.Card;
            }
            return null;
        }

        void attachText(android.view.View host) {
            if (domainLayout != null) {
                domainLayout.attach(host);
            }
            titleLayout.attach(host);
            if (descriptionLayout != null) {
                descriptionLayout.attach(host);
            }
        }

        void detachText() {
            if (domainLayout != null) {
                domainLayout.detach();
            }
            titleLayout.detach();
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
    private final String siteIconUrl;
    private final ImageReceiver siteIconReceiver;
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
    private float pressedTargetDownX;
    private float pressedTargetDownY;
    private boolean titleSpoilersRevealed;
    private boolean descriptionSpoilersRevealed;

    OneboxBlock(CookedPostBlock.Onebox block, PostBodyBuildContext context, PostBodyBlockState inlineState, String stateKey) {
        this.block = block;
        this.context = context;
        this.inlineState = inlineState;
        linkLine = new ReplyMessageLine(context.getHost());
        linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        placeholderPaint.setColor(context.getMediaPlaceholderColor());
        resolvedHref = resolveUrlOrFallback(context.getBaseUrl(), block.getHref(), block.getHref());
        siteIconUrl = block.getSiteIconUrl() != null
                ? CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), block.getSiteIconUrl())
                : null;
        if (siteIconUrl != null) {
            siteIconReceiver = context.getImagePool().obtain("onebox-icon:" + resolvedHref + ":" + siteIconUrl);
            siteIconReceiver.setRoundRadius(AndroidUtilities.dp(4f));
            siteIconReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> context.getHost().invalidate());
            context.getImagePool().setImage(siteIconReceiver, siteIconUrl, null);
        } else {
            siteIconReceiver = null;
        }
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
            thumbnailReceiver.setRoundRadius(AndroidUtilities.dp(8f));
            thumbnailReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> context.getHost().invalidate());
            thumbnailReceiver.setVisibleInvalidate(() -> context.getHost().invalidate());
            context.getImagePool().setImage(thumbnailReceiver, thumbnailUrl, null);
        } else {
            thumbnailReceiver = null;
        }
        domainPaint = textPaint(linkLine.getColor(), 14f, true);
        titlePaint = textPaint(context.getTextColor(), 14f, true);
        descriptionPaint = textPaint(context.getTextColor(), 14f, false);
        textBuilder = new PostBodyTextBuilder(context);
        linkPreviewBounce = new ButtonBounce(context.getHost(), 2.0f, 2.0f);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
    }

    @Override
    public int horizontalInsetPx() {
        return PostBodyBlock.DEFAULT_HORIZONTAL_INSET;
    }

    @Override
    public int measure(int maxWidthPx) {
        int padding = AndroidUtilities.dp(12f);
        int textLeft = AndroidUtilities.dp(TEXT_LEFT_DP);
        int thumbSize = thumbnailReceiver != null ? AndroidUtilities.dp(SMALL_IMAGE_SIDE_DP) : 0;
        int iconInset = siteIconReceiver != null ? AndroidUtilities.dp(20f) : 0;
        int textX = textLeft + iconInset;
        int textWidth = Math.max(maxWidthPx - textX - padding, AndroidUtilities.dp(120f));
        int smallTextWidth = thumbSize > 0
                ? Math.max(textWidth - thumbSize - AndroidUtilities.dp(SMALL_IMAGE_SIDE_MARGIN_DP), AndroidUtilities.dp(80f))
                : textWidth;
        int restLinesCount = thumbSize > 0 ? SMALL_IMAGE_TEXT_LINES : 0;
        StaticLayout domainRawLayout = block.getDomain() != null && !block.getDomain().trim().isEmpty()
                ? buildPreviewLayout(block.getDomain(), domainPaint, textWidth, smallTextWidth, restLinesCount, 1, 0f)
                : null;
        if (domainRawLayout != null && thumbSize > 0) {
            restLinesCount = Math.max(0, restLinesCount - domainRawLayout.getLineCount());
        }
        PositionedLayout titleRawLayout = buildTitleLayout(textWidth, smallTextWidth, restLinesCount);
        if (thumbSize > 0) {
            restLinesCount = Math.max(0, restLinesCount - titleRawLayout.layout.getLineCount());
        }
        PositionedLayout descriptionRawLayout = buildDescriptionLayout(textWidth, smallTextWidth, restLinesCount);

        int y = padding;
        PositionedLayout positionedDomain = null;
        if (domainRawLayout != null) {
            positionedDomain = new PositionedLayout(domainRawLayout, textX, y);
            y += domainRawLayout.getHeight() + AndroidUtilities.dp(2f);
        }
        PositionedLayout positionedTitle = new PositionedLayout(
                titleRawLayout.layout,
                titleRawLayout.builtText,
                textX,
                y,
                titleSpoilersRevealed
        );
        y += titleRawLayout.layout.getHeight() + (descriptionRawLayout != null ? AndroidUtilities.dp(2f) : 0);
        PositionedLayout positionedDescription = null;
        if (descriptionRawLayout != null) {
            positionedDescription = new PositionedLayout(
                    descriptionRawLayout.layout,
                    descriptionRawLayout.builtText,
                    textX,
                    y,
                    descriptionSpoilersRevealed
            );
            y += descriptionRawLayout.layout.getHeight();
        }

        int totalHeight = Math.max(y + padding, thumbSize > 0 ? thumbSize + padding * 2 : 0);
        RectF thumbnailBounds = thumbSize > 0
                ? new RectF(maxWidthPx - padding - thumbSize, padding, maxWidthPx - padding, padding + thumbSize)
                : null;
        OneboxPreviewLayout previousLayout = previewLayout;
        if (previousLayout != null) {
            previousLayout.detachText();
        }
        previewLayout = new OneboxPreviewLayout(
                new RectF(0f, 0f, maxWidthPx, totalHeight),
                thumbnailBounds,
                positionedDomain,
                positionedTitle,
                positionedDescription,
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
            canvas.scale(scale, scale, layout.cardBounds.centerX(), layout.cardBounds.centerY());
        }
        linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        domainPaint.setColor(linkLine.getColor());
        linkLineRect.set(
                0f,
                AndroidUtilities.dp(5f),
                layout.cardBounds.right,
                layout.totalHeight - AndroidUtilities.dp(3f)
        );
        linkLine.drawBackground(canvas, linkLineRect, 5, 5, 5, 1f, true, false);
        drawLinkPreviewSelector(canvas);
        linkLine.drawLine(canvas, linkLineRect, 1f);
        if (siteIconReceiver != null) {
            float iconSize = AndroidUtilities.dp(16f);
            siteIconReceiver.setImageCoords(AndroidUtilities.dp(TEXT_LEFT_DP), AndroidUtilities.dp(12f), iconSize, iconSize);
            siteIconReceiver.draw(canvas);
        }
        if (thumbnailReceiver != null) {
            RectF bounds = layout.thumbnailBounds;
            if (bounds != null) {
                boolean hidden = Objects.equals(context.getHiddenImageUrlProvider().invoke(), thumbnailUrl);
                boolean loading = !thumbnailReceiver.hasBitmapImage() || thumbnailReceiver.getCurrentAlpha() != 1.0f;
                if (hidden || loading) {
                    canvas.drawRoundRect(bounds, AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), placeholderPaint);
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
        layout.titleLayout.draw(canvas, context.getHost()::invalidate);
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
        blocks.add(layout.titleLayout.asSelectionBlock(x, y));
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
                if (layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed)) {
                    return true;
                }
                if (layout.descriptionLayout != null
                        && layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed)) {
                    return true;
                }
                pressedTarget = layout.hitTarget(localX, localY);
                pressedTargetDownX = localX;
                pressedTargetDownY = localY;
                if (pressedTarget != null) {
                    setLinkPreviewPressed(true, localX, localY);
                    linkPreviewBounce.setPressed(true);
                    context.getHost().invalidate();
                }
                return pressedTarget != null;
            case MotionEvent.ACTION_MOVE:
                if (layout.titleLayout.hasPressedRichText()) {
                    return layout.titleLayout.onRichTextTouch(event, localX - layout.titleLayout.x, localY - layout.titleLayout.y, context, touchSlop, this::markTitleSpoilersRevealed);
                }
                if (layout.descriptionLayout != null && layout.descriptionLayout.hasPressedRichText()) {
                    return layout.descriptionLayout.onRichTextTouch(event, localX - layout.descriptionLayout.x, localY - layout.descriptionLayout.y, context, touchSlop, this::markDescriptionSpoilersRevealed);
                }
                if (pressedTarget != null
                        && (layout.hitTarget(localX, localY) != pressedTarget
                        || Math.abs(localX - pressedTargetDownX) > touchSlop
                        || Math.abs(localY - pressedTargetDownY) > touchSlop)) {
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
                if (layout.titleLayout.hasPressedRichText()) {
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
                if (layout.hitTarget(localX, localY) != target) {
                    return false;
                }
                if (target == OneboxHitTarget.Thumbnail) {
                    List<GalleryImage> gallery = thumbnailUrl != null
                            ? Collections.singletonList(new GalleryImage(thumbnailUrl, thumbnailUrl, 0, 0, null))
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
                if (layout.titleLayout.hasPressedRichText()) {
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
        if (siteIconReceiver != null) {
            siteIconReceiver.onAttachedToWindow();
        }
        if (thumbnailReceiver != null) {
            thumbnailReceiver.onAttachedToWindow();
        }
        if (previewLayout != null) {
            previewLayout.attachText(context.getHost());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (siteIconReceiver != null) {
            siteIconReceiver.onDetachedFromWindow();
        }
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
        linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK);
        domainPaint.setColor(linkLine.getColor());
        if (linkPreviewSelector != null) {
            Theme.setSelectorDrawableColor(linkPreviewSelector, linkPreviewSelectorColor = linkLine.getBackgroundColor(), false);
        }
    }

    private void drawLinkPreviewSelector(Canvas canvas) {
        int rippleColor = linkLine.getBackgroundColor();
        if (linkPreviewSelector == null) {
            linkPreviewSelector = Theme.createRadSelectorDrawable(linkPreviewSelectorColor = rippleColor, 0, 0);
            linkPreviewSelector.setCallback(context.getHost());
            Theme.setMaskDrawableRad(linkPreviewSelector, 3, 6, 6, 3);
        } else if (linkPreviewSelectorColor != rippleColor) {
            Theme.setSelectorDrawableColor(linkPreviewSelector, linkPreviewSelectorColor = rippleColor, false);
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

    private static TextPaint textPaint(int color, float sizeSp, boolean bold) {
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(AndroidUtilities.dp(sizeSp));
        if (bold) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }
        return paint;
    }

    private static StaticLayout buildPreviewLayout(CharSequence text, TextPaint paint, int width, int smallWidth, int smallLinesCount, int maxLines, float spacingExtraDp) {
        CharSequence source = text.length() > 360 ? text.subSequence(0, 360) : text;
        if (smallLinesCount > 0 && smallWidth < width) {
            return buildSmallImageLayout(source, paint, width, smallWidth, smallLinesCount, maxLines, spacingExtraDp).layout;
        }
        return previewBuilder(source, paint, width, spacingExtraDp)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static PreviewBuildResult buildRichPreviewLayout(BuiltText builtText, TextPaint paint, int width, int smallWidth, int smallLinesCount, int maxLines, float spacingExtraDp) {
        CharSequence source = builtText.getSpannable();
        if (smallLinesCount > 0 && smallWidth < width) {
            SmallImageTextResult smallResult = buildSmallImageLayout(source, paint, width, smallWidth, smallLinesCount, maxLines, spacingExtraDp);
            return new PreviewBuildResult(smallResult.layout, shiftBuiltText(builtText, smallResult));
        }
        StaticLayout layout = previewBuilder(source, paint, width, spacingExtraDp)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        return new PreviewBuildResult(layout, builtText);
    }

    private static SmallImageTextResult buildSmallImageLayout(CharSequence source, TextPaint paint, int maxWidth, int smallWidth, int linesCount, int maxLines, float spacingExtraDp) {
        CharSequence emojiSource = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(emojiSource);
        ArrayList<Integer> insertedBreakOffsets = new ArrayList<>();
        int addedChars = 0;
        StaticLayout smallLayout = previewBuilderRaw(emojiSource, paint, smallWidth, 0f).build();
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
        StaticLayout layout = previewBuilderRaw(stringBuilder, paint, maxWidth, spacingExtraDp)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
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

    private static StaticLayout.Builder previewBuilder(CharSequence source, TextPaint paint, int width, float spacingExtraDp) {
        if (source.length() > 0 && source.charAt(source.length() - 1) == '\n') {
            source = source.subSequence(0, source.length() - 1);
        }
        source = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        return previewBuilderRaw(source, paint, width, spacingExtraDp);
    }

    private static StaticLayout.Builder previewBuilderRaw(CharSequence source, TextPaint paint, int width, float spacingExtraDp) {
        return StaticLayout.Builder.obtain(source, 0, source.length(), paint, Math.max(width, 1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(AndroidUtilities.dp(spacingExtraDp), 1.0f)
                .setIncludePad(false);
    }

    private PositionedLayout buildTitleLayout(int textWidth, int smallTextWidth, int smallLinesCount) {
        String html = block.getTitleHtml();
        if (html == null || html.trim().isEmpty()) {
            html = TextUtils.htmlEncode(block.getTitle());
        }
        BuiltText builtText = textBuilder.build(html, titlePaint);
        PreviewBuildResult buildResult = buildRichPreviewLayout(builtText, titlePaint, textWidth, smallTextWidth, smallLinesCount, TITLE_MAX_LINES, 1f);
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
        }
        BuiltText builtText = textBuilder.build(html, descriptionPaint);
        PreviewBuildResult buildResult = buildRichPreviewLayout(builtText, descriptionPaint, textWidth, smallTextWidth, smallLinesCount, DESCRIPTION_MAX_LINES, 1f);
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

    private static int[] copyRadius(ImageReceiver receiver) {
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }
}
