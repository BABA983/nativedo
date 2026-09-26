/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's RichMessageLayout.RichMathBlock.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.iv.Latex;

/**
 * Cooked display math (`div.math`), drawn the way the rich body draws it: the TeX rendered by the
 * tree's own LaTeX renderer at fontSize+4, centred, with dp(8) of vertical padding. The rich block
 * scrolls a formula wider than the body; this one scales it down to fit instead - a deliberate
 * delta, recorded in `docs/render-model-alignment.md`, because a block in this body has no
 * horizontal scroller to hand.
 *
 * A source the renderer cannot parse falls back to the TeX as text, so nothing ever disappears (the
 * inline math span behaves the same way). The bitmap is tinted at draw time, so a theme change needs
 * no re-render - only a font-size change does.
 */
final class MathBlock implements PostBodyBlock {
    private static final float VERTICAL_PADDING_DP = 8f;
    private static final float DISPLAY_SIZE_OFFSET_DP = 4f;

    private final PostBodyBuildContext context;
    private final String source;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Latex latex;
    private float renderedSizePx;

    MathBlock(String source, PostBodyBuildContext context) {
        this.source = source;
        this.context = context;
        render();
    }

    private void render() {
        renderedSizePx = displaySizePx();
        latex = latexFor(source, renderedSizePx);
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        if (latex == null) {
            fallbackPaint.setTextSize(context.getParagraphPaint().getTextSize());
            final Paint.FontMetrics metrics = fallbackPaint.getFontMetrics();
            return Math.round(-metrics.ascent + metrics.descent) + verticalPaddingPx() * 2;
        }
        return latex.height + verticalPaddingPx() * 2;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (latex == null) {
            drawFallback(canvas, parentWidth);
            return;
        }
        paint.setColor(context.getTextColor());
        final float available = Math.max(1f, parentWidth);
        if (latex.width <= available) {
            // Centred when it fits, exactly like the rich block.
            canvas.drawBitmap(latex.bitmap, (available - latex.width) / 2f, verticalPaddingPx(), paint);
        } else {
            // Wider than the body: scale to fit, so the whole formula stays readable.
            final float scale = available / latex.width;
            final Rect src = new Rect(0, 0, latex.width, latex.height);
            final RectF dst = new RectF(0f, verticalPaddingPx(), available, verticalPaddingPx() + latex.height * scale);
            canvas.drawBitmap(latex.bitmap, src, dst, paint);
        }
    }

    private void drawFallback(Canvas canvas, int parentWidth) {
        fallbackPaint.setColor(context.getTextColor());
        final float available = Math.max(1f, parentWidth);
        final float x = Math.max(0f, (available - fallbackPaint.measureText(source)) / 2f);
        canvas.drawText(source, x, verticalPaddingPx() - fallbackPaint.getFontMetrics().ascent, fallbackPaint);
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        // Display math is not interactive: no link, no scroll, nothing to select.
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        // The formula is a bitmap, not text: it contributes nothing a selection drag could pick up.
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
    }

    @Override
    public void applyTheme() {
        if (displaySizePx() != renderedSizePx) {
            render();
        }
    }

    private static float displaySizePx() {
        return AndroidUtilities.dp(SharedConfig.fontSize + DISPLAY_SIZE_OFFSET_DP);
    }

    private static int verticalPaddingPx() {
        return AndroidUtilities.dp(VERTICAL_PADDING_DP);
    }

    /** The same renderer and cache the inline math span uses, so one formula is rendered once. */
    private static Latex latexFor(String source, float textSizePx) {
        return io.github.baba983.nativedo.core.ui.MathLatexCache.render(source, textSizePx);
    }
}
