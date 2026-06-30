/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockPhotoCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class ImageBlock implements EmbeddedPostBodyBlock {
    private static final float MIN_WIDTH_DP = 120f;
    private static final float PHOTO_MAX_HEIGHT_STATUS_OFFSET_DP = 56f;
    private static final float PHOTO_MAX_HEIGHT_FACTOR = 0.9f;
    private static final float PHOTO_TOP_GAP_DP = 8f;
    private static final float PROGRESS_BUTTON_SIZE_DP = 48f;
    private static final float CAPTION_HORIZONTAL_INSET_DP = 18f;
    private static final float CAPTION_VERTICAL_GAP_DP = 8f;
    private static final float CAPTION_BOTTOM_PADDING_DP = 8f;
    private static final float CAPTION_MIN_WIDTH_DP = 80f;
    private static final float INSTANT_LINK_TOP_INSET_DP = 11f;
    private static final float INSTANT_LINK_RIGHT_INSET_DP = 11f;
    private static final float INSTANT_LINK_SIZE_DP = 24f;

    private final CookedPostBlock.Image image;
    private final List<GalleryImage> gallery;
    private final PostBodyBuildContext context;
    private final boolean isFirst;
    private final PostBodyBlockState inlineState;
    private final String inlineSpoilerStateKey;
    private final String imageUrl;
    private final String targetUrl;
    private final String externalLinkUrl;
    private final Drawable linkDrawable;
    private final ImageReceiver receiver;
    private final RadialProgress2 radialProgress;
    private ImageBlockView imageBlockView;
    private final RectF imageRect = new RectF();
    private final RectF captionRect = new RectF();
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint;
    private final PostBodyTextBuilder textBuilder;
    private final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
    private final int touchSlop;
    private BuiltText builtCaption;
    private BuiltText.Handle captionHandle;
    private StaticLayout captionLayout;
    private int captionX;
    private int captionY;
    private int measuredWidth;
    private int measuredHeight;
    private int buttonX;
    private int buttonY;
    private boolean photoPressed;
    private final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
    private final Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private final Path spoilerBoundsPath = new Path();
    private final Path spoilerRipplePath = new Path();
    private float downX;
    private float downY;
    private boolean spoilersRevealed;
    private boolean pressedSpoiler;

    ImageBlock(CookedPostBlock.Image image, List<GalleryImage> gallery, PostBodyBuildContext context, boolean isFirst, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
        this.image = image;
        this.gallery = gallery;
        this.context = context;
        this.isFirst = isFirst;
        this.inlineState = inlineState;
        this.inlineSpoilerStateKey = inlineSpoilerStateKey;
        spoilersRevealed = inlineState != null
                && inlineSpoilerStateKey != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(inlineSpoilerStateKey);
        imageUrl = CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), image.getSrc());
        String href = image.getHref();
        String hrefUrl = href != null ? CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), href) : null;
        boolean hrefIsImage = hrefUrl != null && CookedPostBodyParserKt.isLikelyPostImageUrl(hrefUrl);
        targetUrl = hrefIsImage ? hrefUrl : imageUrl;
        externalLinkUrl = hrefUrl != null && !hrefIsImage ? hrefUrl : null;
        linkDrawable = externalLinkUrl != null ? loadInstantLinkDrawable(context) : null;
        receiver = context.getImagePool().obtain("image:" + targetUrl);
        placeholderPaint.setColor(context.getMediaPlaceholderColor());
        captionPaint = new TextPaint(context.getMediaCaptionPaint());
        textBuilder = new PostBodyTextBuilder(context);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
        receiver.setRoundRadius(0);
        receiver.setAspectFit(true);
        receiver.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateBlock());
        receiver.setVisibleInvalidate(this::invalidateBlock);
        context.getImagePool().setImage(receiver, imageUrl, null);
        radialProgress = new RadialProgress2(context.getHost());
        radialProgress.setProgressColor(0xffffffff);
        radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
        radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        int width = Math.max(maxWidthPx, minWidthPx());
        measuredWidth = width;
        Float imageAspect = image.getAspectRatio();
        float aspect = imageAspect != null ? imageAspect : 1.5f;
        int imageWidth = width;
        int imageHeight = Math.round(imageWidth / aspect);
        int maxHeight = photoMaxHeightPx();
        if (imageHeight > maxHeight) {
            imageHeight = maxHeight;
            imageWidth = Math.min(Math.round(imageHeight * aspect), width);
        }
        float imageLeft = (width - imageWidth) / 2f;
        int photoY = photoTopGapPx(isFirst);
        imageRect.set(imageLeft, photoY, imageLeft + imageWidth, photoY + imageHeight);
        int buttonSize = progressButtonSizePx();
        buttonX = Math.round(imageRect.left + (imageRect.width() - buttonSize) / 2f);
        buttonY = Math.round(imageRect.top + (imageRect.height() - buttonSize) / 2f);
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
        captionX = captionXPx();
        int captionWidth = captionWidthPx(width);
        String captionHtml = image.getCaptionHtml();
        if (captionHandle != null) {
            captionHandle.detach();
            captionHandle = null;
        }
        if (captionHtml != null) {
            BuiltText builtText = textBuilder.build(captionHtml, captionPaint);
            builtCaption = builtText;
            CharSequence source = builtText.getSpannable();
            captionLayout = ArticleTextLayout.builder(source, captionPaint, captionWidth)
                    .build();
            SpoilerTextLayout.collect(context.getHost(), captionLayout, spoilersPool, spoilers, spoilersRevealed);
            if (context.getHost().isAttachedToWindow()) {
                captionHandle = builtText.attach(context.getHost());
            }
        } else {
            captionLayout = null;
            builtCaption = null;
            spoilersPool.addAll(spoilers);
            spoilers.clear();
        }
        captionY = Math.round(imageRect.bottom) + captionVerticalGapPx();
        if (captionLayout != null) {
            captionRect.set(captionX, captionY, captionX + captionLayout.getWidth(), captionY + captionLayout.getHeight());
        } else {
            captionRect.setEmpty();
        }
        measuredHeight = Math.round(imageRect.bottom)
                + (captionLayout != null ? captionVerticalGapPx() + captionLayout.getHeight() : 0)
                + captionBottomPaddingPx();
        if (imageBlockView != null) {
            imageBlockView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            );
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        boolean hidden = targetUrl.equals(context.getHiddenImageUrlProvider().invoke());
        boolean loading = !receiver.hasBitmapImage() || receiver.getCurrentAlpha() != 1.0f;
        if (hidden || loading) {
            canvas.drawRect(imageRect, placeholderPaint);
        }
        if (!hidden) {
            receiver.setImageCoords(imageRect);
            receiver.draw(canvas);
            updateProgressState(loading);
            radialProgress.draw(canvas);
            if (loading) {
                invalidateBlock();
            }
            if (linkDrawable != null) {
                int linkLeft = instantLinkLeftPx(parentWidth);
                int linkTop = instantLinkTopPx(imageRect.top);
                linkDrawable.setBounds(linkLeft, linkTop, linkLeft + instantLinkSizePx(), linkTop + instantLinkSizePx());
                linkDrawable.draw(canvas);
            }
        }
        if (captionLayout != null) {
            canvas.save();
            canvas.translate(captionX, captionY);
            linkPressState.draw(canvas, this::invalidateBlock);
            SpoilerTextLayout.draw(canvas, captionLayout, spoilers, spoilerBoundsPath, spoilerRipplePath);
            canvas.restore();
        }
    }

    @Override
    public View embeddedView() {
        if (imageBlockView == null) {
            imageBlockView = new ImageBlockView(context.getHost().getContext());
        }
        return imageBlockView;
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        if (captionLayout != null
                && (captionRect.contains(localX, localY) || linkPressState.isPressed() || pressedSpoiler)) {
            if (onCaptionTouch(event, localX - captionX, localY - captionY)) {
                return true;
            }
        }
        if (!imageRect.contains(localX, localY)) {
            if (photoPressed && (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || event.getActionMasked() == MotionEvent.ACTION_UP)) {
                photoPressed = false;
                invalidateBlock();
            }
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            photoPressed = true;
            invalidateBlock();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            photoPressed = false;
            invalidateBlock();
            if (externalLinkUrl != null && isInsideInstantLink(localX, localY)) {
                context.getDelegate().onLinkClick(externalLinkUrl);
                return true;
            }
            context.getDelegate().onImageClick(
                    targetUrl,
                    gallery,
                    imageBoundsInCell(),
                    context.getHost(),
                    imageUrl -> targetUrl.equals(imageUrl) ? imageBoundsInCell() : null,
                    imageUrl -> targetUrl.equals(imageUrl) ? receiver : null,
                    imageUrl -> targetUrl.equals(imageUrl) ? receiver.getBitmapSafe() : null,
                    imageUrl -> targetUrl.equals(imageUrl) ? copyRadius(receiver) : null
            );
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            photoPressed = false;
            invalidateBlock();
            return true;
        }
        return photoPressed;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (captionLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(captionLayout, x + captionX, y + captionY, 0, null));
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return targetUrl.equals(imageUrl) ? imageBoundsInCell() : null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        return targetUrl.equals(imageUrl) ? receiver : null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        return targetUrl.equals(imageUrl) ? receiver.getBitmapSafe() : null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        return targetUrl.equals(imageUrl) ? copyRadius(receiver) : null;
    }

    @Override
    public void onAttachedToWindow() {
        receiver.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
        if (captionHandle == null && builtCaption != null) {
            captionHandle = builtCaption.attach(context.getHost());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (captionHandle != null) {
            captionHandle.detach();
            captionHandle = null;
        }
        linkPressState.reset();
        pressedSpoiler = false;
        radialProgress.onDetachedFromWindow();
        receiver.onDetachedFromWindow();
    }

    @Override
    public void applyTheme() {
    }

    RectF imageBoundsInCell() {
        return new RectF(imageRect);
    }

    private static int[] copyRadius(ImageReceiver receiver) {
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }

    private void updateProgressState(boolean loading) {
        radialProgress.setIcon(loading ? MediaActionDrawable.ICON_EMPTY : MediaActionDrawable.ICON_NONE, true, false);
        radialProgress.setProgress(loading ? 0f : 1f, false);
    }

    private static Drawable loadInstantLinkDrawable(PostBodyBuildContext context) {
        try {
            return context.getHost().getContext().getResources().getDrawable(org.telegram.messenger.R.drawable.msg_instant_link);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private boolean isInsideInstantLink(float x, float y) {
        if (linkDrawable == null) {
            return false;
        }
        int linkLeft = instantLinkLeftPx(measuredWidth);
        int linkTop = instantLinkTopPx(imageRect.top);
        return x >= linkLeft && x <= linkLeft + instantLinkSizePx()
                && y >= linkTop && y <= linkTop + instantLinkSizePx();
    }

    private boolean onCaptionTouch(MotionEvent event, float localX, float localY) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock)) {
                    return true;
                }
                if (SpoilerTextLayout.contains(spoilers, localX, localY)) {
                    pressedSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                        pressedSpoiler = false;
                        return false;
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), captionLayout, spoilersPool, spoilers, localX, localY, this::markSpoilersRevealed);
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return true;
                }
                return false;
            default:
                return linkPressState.isPressed() || pressedSpoiler;
        }
    }

    private void markSpoilersRevealed() {
        spoilersRevealed = true;
        if (inlineState != null && inlineSpoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(inlineSpoilerStateKey);
        }
        invalidateBlock();
    }

    private void invalidateBlock() {
        context.getHost().invalidate();
        if (imageBlockView != null) {
            imageBlockView.invalidate();
        }
    }

    static int minWidthPx() {
        return AndroidUtilities.dp(MIN_WIDTH_DP);
    }

    static int photoMaxHeightPx() {
        return Math.round((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(PHOTO_MAX_HEIGHT_STATUS_OFFSET_DP)) * PHOTO_MAX_HEIGHT_FACTOR);
    }

    static int photoTopGapPx(boolean first) {
        return first ? 0 : AndroidUtilities.dp(PHOTO_TOP_GAP_DP);
    }

    static int progressButtonSizePx() {
        return AndroidUtilities.dp(PROGRESS_BUTTON_SIZE_DP);
    }

    static int captionXPx() {
        return AndroidUtilities.dp(CAPTION_HORIZONTAL_INSET_DP);
    }

    static int captionWidthPx(int parentWidth) {
        return Math.max(parentWidth - captionXPx() * 2, AndroidUtilities.dp(CAPTION_MIN_WIDTH_DP));
    }

    static int captionVerticalGapPx() {
        return AndroidUtilities.dp(CAPTION_VERTICAL_GAP_DP);
    }

    static int captionBottomPaddingPx() {
        return AndroidUtilities.dp(CAPTION_BOTTOM_PADDING_DP);
    }

    static int instantLinkTopPx(float imageTop) {
        return Math.round(imageTop) + AndroidUtilities.dp(INSTANT_LINK_TOP_INSET_DP);
    }

    static int instantLinkLeftPx(int parentWidth) {
        return parentWidth - AndroidUtilities.dp(INSTANT_LINK_RIGHT_INSET_DP + INSTANT_LINK_SIZE_DP);
    }

    static int instantLinkSizePx() {
        return AndroidUtilities.dp(INSTANT_LINK_SIZE_DP);
    }

    private final class ImageBlockView extends View {
        ImageBlockView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), measuredHeight);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            ImageBlock.this.draw(canvas, getWidth());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return ImageBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }
}
