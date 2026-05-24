/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/LineProgressView.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/LineProgressView.java
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
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Thin horizontal progress line that smoothly chases the latest target value and fades out once
 * the page reports completion. Mirrors Telegram's WebActionBar progress strip.
 */
public class LineProgressView extends View {

    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat progressFloat = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat alphaFloat = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);

    private float targetProgress;
    private boolean visibleTarget;

    public LineProgressView(Context context) {
        super(context);
        progressPaint.setColor(0xff58a8ed);
        backPaint.setColor(Color.TRANSPARENT);
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    public void setBackColor(int color) {
        backPaint.setColor(color);
        invalidate();
    }

    public void setProgress(float value, boolean animated) {
        float clamped = Math.max(0f, Math.min(1f, value));
        targetProgress = clamped;
        boolean nextVisible = clamped > 0f && clamped < 1f;
        if (visibleTarget != nextVisible) {
            visibleTarget = nextVisible;
        }
        if (!animated) {
            progressFloat.force(clamped);
            alphaFloat.force(visibleTarget ? 1f : 0f);
        }
        invalidate();
    }

    public float getCurrentProgress() {
        return progressFloat.get();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float a = alphaFloat.set(visibleTarget ? 1f : 0f);
        if (a <= 0f) {
            // Reset filled progress once the alpha has settled at 0 so the next show starts clean.
            progressFloat.force(0f);
            return;
        }
        float p = progressFloat.set(targetProgress);
        int width = getWidth();
        int height = getHeight();
        int alpha = (int) (0xFF * a);

        if ((backPaint.getColor() & 0xFF000000) != 0) {
            int prev = backPaint.getAlpha();
            backPaint.setAlpha((int) (prev * a));
            canvas.drawRect(0, 0, width, height, backPaint);
            backPaint.setAlpha(prev);
        }

        progressPaint.setAlpha(alpha);
        canvas.drawRect(0, 0, width * p, height, progressPaint);
        progressPaint.setAlpha(0xFF);
    }
}
