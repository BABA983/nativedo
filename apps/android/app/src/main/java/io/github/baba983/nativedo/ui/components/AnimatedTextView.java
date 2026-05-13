// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Components/AnimatedTextView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - NativeDo's AnimatedTextDrawable is a standalone class rather than an inner
//     class of AnimatedTextView. AnimatedTextView provides the View-host surface
//     that AnimatedTextView provides in upstream, using our AnimatedTextDrawable as
//     the rendering delegate.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/AnimatedTextView.java
 * and its inner AnimatedTextDrawable.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedTextView.java
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
package io.github.baba983.nativedo.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A lightweight {@code View} that hosts a single {@link AnimatedTextDrawable} as its
 * rendering delegate, matching the surface that Telegram's {@code AnimatedTextView}
 * provides for title/subtitle text in action-bar chrome.
 */
public final class AnimatedTextView extends View implements Drawable.Callback {

    private final AnimatedTextDrawable textDrawable;
    private CharSequence text = "";

    public AnimatedTextView(@NonNull Context context) {
        this(context, new AnimatedTextDrawable(false, true, true));
    }

    public AnimatedTextView(@NonNull Context context, @NonNull AnimatedTextDrawable delegate) {
        super(context);
        textDrawable = delegate;
        textDrawable.setCallback(this);
        textDrawable.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textDrawable.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void setTextColor(int color) {
        textDrawable.setTextColor(color);
    }

    public void setTextSize(float sp) {
        textDrawable.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                getResources().getDisplayMetrics()
        ));
    }

    public void setText(@Nullable CharSequence value, boolean animated, boolean moveDown) {
        text = value == null ? "" : value;
        textDrawable.setText(text, animated, moveDown);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        textDrawable.setBounds(0, 0, w, h);
        textDrawable.setOverrideFullWidth(w);
        textDrawable.setText(text, false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        textDrawable.draw(canvas);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidate();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        postDelayed(what, Math.max(0, when - android.os.SystemClock.uptimeMillis()));
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        removeCallbacks(what);
    }
}
