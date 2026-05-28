/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/SimpleTextView.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/SimpleTextView.java
 *
 * NativeDo native port: single-line text view that supports the profile
 * collapsible title/subtitle animation (getTextWidth, setRightPadding,
 * setEllipsizeByGradient). Omits upstream emoji, spoiler, drawable, and
 * multi-line features not needed for the profile use case.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Minimal native port of Telegram's SimpleTextView for profile animation.
 *
 * Key features preserved from upstream (forced_exact: measurement, drawing):
 * - Custom StaticLayout-based text measurement ({@link #getTextWidth()})
 * - Right-padding clipping for scroll-to-collapse animation ({@link #setRightPadding(int)})
 * - Gradient ellipsize fade for text that overflows ({@link #setEllipsizeByGradient(boolean)})
 *
 * Omitted: emoji/spans/drawables/multi-line/scrollNonFitText.
 */
public class SimpleTextView extends View implements Drawable.Callback {

    private TextPaint textPaint;
    private Layout layout;
    private CharSequence text;
    private int gravity = Gravity.LEFT | Gravity.TOP;
    private int textWidth;
    private int textHeight;
    private int offsetX;
    private int offsetY;
    private float layoutX, layoutY;

    // Right padding (used by ProfileActivity2.updateScrollLayout)
    private int paddingRight;

    // Ellipsize by gradient (fade out right edge when text overflows)
    private boolean ellipsizeByGradient;
    private Paint fadeEllipsizePaint;
    private int fadeEllipsizePaintWidth;

    public SimpleTextView(Context context) {
        super(context);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    // --- Setters matching upstream API ---

    public void setTextSize(int sizeInDp) {
        int newSize = AndroidUtilities.dp(sizeInDp);
        if (newSize == (int) textPaint.getTextSize()) return;
        textPaint.setTextSize(newSize);
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setGravity(int value) {
        gravity = value;
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
    }

    public void setText(CharSequence text) {
        if (text == null) text = "";
        if (TextUtils.equals(this.text, text)) return;
        this.text = text;
        if (!recreateLayoutMaybe()) {
            invalidate();
        }
    }

    public CharSequence getText() {
        return text;
    }

    public void setEllipsizeByGradient(boolean value) {
        if (ellipsizeByGradient == value) return;
        ellipsizeByGradient = value;
        updateFadeEllipsizePaint();
        requestLayout();
    }

    public void setScrollNonFitText(boolean value) {
        // In upstream this enables auto-scrolling when text overflows.
        // For our profile use case, gradient ellipsize handles the overflow.
        // No-op: kept for API parity with upstream.
    }

    public void setRightPadding(int padding) {
        if (paddingRight == padding) return;
        paddingRight = padding;
        if (!recreateLayoutMaybe()) {
            calcOffset();
            invalidate();
        }
    }

    public void setPivotX(float pivotX) {
        super.setPivotX(pivotX);
    }

    // --- Getters matching upstream API ---

    /** Returns the measured text pixel width (from StaticLayout). */
    public int getTextWidth() {
        return textWidth;
    }

    public TextPaint getPaint() {
        return textPaint;
    }

    public Layout getLayout() {
        return layout;
    }

    public float getLayoutX() {
        return layoutX;
    }

    public float getLayoutY() {
        return layoutY;
    }

    // --- Layout & draw (forced_exact: measurement, drawing) ---

    private int getAvailableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - paddingRight;
    }

    private boolean recreateLayoutMaybe() {
        if (getWidth() == 0) return false;
        createLayout(getAvailableWidth());
        return true;
    }

    private void createLayout(int width) {
        if (width <= 0) width = AndroidUtilities.displaySize.x;
        if (text != null) {
            try {
                CharSequence string;
                if (ellipsizeByGradient) {
                    string = text;
                } else {
                    string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                }
                layout = new StaticLayout(
                    string, 0, string.length(), textPaint,
                    ellipsizeByGradient ? AndroidUtilities.dp(2000) : width + AndroidUtilities.dp(8),
                    Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false
                );
                calcOffset();
            } catch (Exception ignore) {
            }
        } else {
            layout = null;
            textWidth = 0;
            textHeight = 0;
        }
        invalidate();
    }

    private void calcOffset() {
        if (layout == null || layout.getLineCount() <= 0) return;
        textWidth = (int) Math.max(
            Math.ceil(layout.getLineWidth(0)),
            Math.ceil(layout.getLineRight(0) - layout.getLineLeft(0))
        );
        textHeight = layout.getLineBottom(0);

        if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
            offsetX = (getAvailableWidth() - textWidth) / 2 - (int) layout.getLineLeft(0);
        } else if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
            offsetX = -(int) layout.getLineLeft(0);
        } else {
            offsetX = getAvailableWidth() - textWidth;
        }
        offsetX += getPaddingLeft();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        createLayout(width - getPaddingLeft() - getPaddingRight() - paddingRight);

        int finalHeight;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            finalHeight = height;
        } else {
            finalHeight = getPaddingTop() + textHeight + getPaddingBottom();
        }
        setMeasuredDimension(width, finalHeight);

        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
            offsetY = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - textHeight) / 2;
        } else {
            offsetY = getPaddingTop();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        layoutX = 0;
        layoutY = 0;
        if (layout == null) return;

        int restore = Integer.MIN_VALUE;
        if (ellipsizeByGradient) {
            restore = canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }

        canvas.save();
        canvas.translate(offsetX, offsetY);
        layoutX += offsetX;
        layoutY += offsetY;
        layout.draw(canvas);
        canvas.restore();

        if (ellipsizeByGradient && fadeEllipsizePaint != null) {
            int fadeWidth = AndroidUtilities.dp(16);
            int fadeX = getWidth() - getPaddingRight() - fadeWidth;
            if (fadeX < getWidth()) {
                canvas.drawRect(fadeX, 0, getWidth() - getPaddingRight(), getMeasuredHeight(), fadeEllipsizePaint);
            }
            canvas.restoreToCount(restore);
        }
    }

    private void updateFadeEllipsizePaint() {
        if (ellipsizeByGradient) {
            int fadeWidth = AndroidUtilities.dp(16);
            if (fadeEllipsizePaint == null || fadeEllipsizePaintWidth != fadeWidth) {
                fadeEllipsizePaint = new Paint();
                fadeEllipsizePaintWidth = fadeWidth;
            }
            fadeEllipsizePaint.setShader(new LinearGradient(
                0, 0, fadeWidth, 0,
                new int[]{0, 0xFFFFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
            ));
            fadeEllipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        } else {
            fadeEllipsizePaint = null;
        }
    }

    // --- Drawable.Callback (required by interface, no-op) ---

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidate();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
    }

    @Override
    public void unscheduleDrawable(Drawable who) {
    }
}
