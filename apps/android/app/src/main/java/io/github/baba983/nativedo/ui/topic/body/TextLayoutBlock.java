/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer text block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.Stack;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

abstract class TextLayoutBlock implements PostBodyBlock {

    private final String html;
    private final PostBodyBuildContext context;
    protected final TextPaint paint;
    private final PostBodyBlockState inlineState;
    private final String inlineSpoilerStateKey;
    private final PostBodyTextBuilder textBuilder;
    private final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
    private final int touchSlop;
    @SuppressWarnings("unused")
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BuiltText builtText;
    private StaticLayout layout;
    private int measuredWidth;
    private int measuredHeight;
    private BuiltText.Handle handle;
    private final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
    private final Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private final Path spoilerBoundsPath = new Path();
    private final Path spoilerRipplePath = new Path();
    private float downX;
    private float downY;
    private boolean spoilersRevealed;
    private boolean pressedSpoiler;

    TextLayoutBlock(String html, PostBodyBuildContext context, TextPaint paint) {
        this(html, context, paint, null, null);
    }

    TextLayoutBlock(String html, PostBodyBuildContext context, TextPaint paint, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
        this.html = html;
        this.context = context;
        this.paint = paint;
        this.inlineState = inlineState;
        this.inlineSpoilerStateKey = inlineSpoilerStateKey;
        spoilersRevealed = inlineState != null
                && inlineSpoilerStateKey != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(inlineSpoilerStateKey);
        textBuilder = new PostBodyTextBuilder(context);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
        pressedPaint.setColor(Color.argb(
                36,
                Color.red(context.getAccentColor()),
                Color.green(context.getAccentColor()),
                Color.blue(context.getAccentColor())
        ));
        pressedPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Text-content blocks inherit the container's default dp(18) horizontal
     * inset, mirroring ArticleViewer's text cells (BlockParagraphCell
     * textX=dp(18), BlockHeaderCell textX=dp(18)). Full-bleed/media blocks
     * override this to 0 at their own (non-text) class.
     */
    @Override
    public int horizontalInsetPx() {
        return PostBodyBlock.DEFAULT_HORIZONTAL_INSET;
    }

    protected StaticLayout buildLayout(CharSequence spannable, int width) {
        return ArticleTextLayout.builder(spannable, paint, width).build();
    }

    @Override
    public int measure(int maxWidthPx) {
        int width = Math.max(maxWidthPx, 1);
        if (layout != null && measuredWidth == width) {
            return measuredHeight;
        }
        if (handle != null) {
            handle.detach();
            handle = null;
        }
        BuiltText text = textBuilder.build(html, paint);
        builtText = text;
        layout = buildLayout(text.getSpannable(), width);
        SpoilerTextLayout.collect(context.getHost(), layout, spoilersPool, spoilers, spoilersRevealed);
        if (context.getHost().isAttachedToWindow()) {
            handle = text.attach(context.getHost());
        }
        measuredWidth = width;
        measuredHeight = layout.getHeight() + verticalPaddingPx();
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (layout == null) {
            return;
        }
        canvas.save();
        canvas.translate(0f, topPaddingPx());
        linkPressState.draw(canvas, context.getHost()::invalidate);
        SpoilerTextLayout.draw(canvas, layout, spoilers, spoilerBoundsPath, spoilerRipplePath);
        canvas.restore();
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        if (layout == null) {
            return false;
        }
        float yInText = localY - topPaddingPx();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (linkPressState.onTouch(event, localX, yInText, layout, builtText, context, touchSlop, context.getHost()::invalidate)) {
                    return true;
                }
                if (SpoilerTextLayout.contains(spoilers, localX, yInText)) {
                    pressedSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, yInText, layout, builtText, context, touchSlop, context.getHost()::invalidate);
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
                    return linkPressState.onTouch(event, localX, yInText, layout, builtText, context, touchSlop, context.getHost()::invalidate);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), layout, spoilersPool, spoilers, localX, yInText, this::markSpoilersRevealed);
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, yInText, layout, builtText, context, touchSlop, context.getHost()::invalidate);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (layout != null) {
            blocks.add(new SelectionTextLayoutBlock(layout, x, y + topPaddingPx()));
        }
    }

    int firstTextBaselinePx() {
        if (layout == null || layout.getLineCount() == 0) {
            return -1;
        }
        return topPaddingPx() + layout.getLineBaseline(0);
    }

    int firstTextTopPx() {
        if (layout == null || layout.getLineCount() == 0) {
            return -1;
        }
        return topPaddingPx();
    }

    int firstTextLineAscentPx() {
        if (layout == null || layout.getLineCount() == 0) {
            return 0;
        }
        return layout.getLineAscent(0);
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
        if (handle != null || builtText == null) {
            return;
        }
        handle = builtText.attach(context.getHost());
    }

    @Override
    public void onDetachedFromWindow() {
        if (handle != null) {
            handle.detach();
        }
        handle = null;
        linkPressState.reset();
        pressedSpoiler = false;
    }

    @Override
    public void applyTheme() {
        // Shared TextPaint instances are updated by PostBodyBuildContext.applyTheme().
        // Link/chip spans bake accent colors, so force a remeasure rebuild.
        int accent = context.getAccentColor();
        pressedPaint.setColor(Color.argb(
                36,
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent)
        ));
        if (handle != null) {
            handle.detach();
            handle = null;
        }
        layout = null;
        measuredWidth = 0;
        measuredHeight = 0;
        builtText = null;
        spoilersPool.addAll(spoilers);
        spoilers.clear();
    }

    protected int topPaddingPx() {
        return dp(8f);
    }

    protected int bottomPaddingPx() {
        return dp(8f);
    }

    protected final int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private int verticalPaddingPx() {
        return topPaddingPx() + bottomPaddingPx();
    }

    static String inlineSpoilerStateKey(String scope, String value) {
        return scope + ":" + (value != null ? value.hashCode() : 0);
    }

    private void markSpoilersRevealed() {
        spoilersRevealed = true;
        if (inlineState != null && inlineSpoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(inlineSpoilerStateKey);
        }
    }

    private static final class SelectionTextLayoutBlock implements TextSelectionHelper.TextLayoutBlock {
        private final StaticLayout layout;
        private final int x;
        private final int y;

        SelectionTextLayoutBlock(StaticLayout layout, int x, int y) {
            this.layout = layout;
            this.x = x;
            this.y = y;
        }

        @Override
        public StaticLayout getLayout() {
            return layout;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getRow() {
            return 0;
        }

        @Override
        public CharSequence getPrefix() {
            return null;
        }
    }
}
