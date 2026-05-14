// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Components/AnimatedTextView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - NativeDo's AnimatedTextDrawable is a standalone class rather than an inner
//     class of AnimatedTextView. AnimatedTextView provides the View-host surface
//     that AnimatedTextView provides in upstream, using our AnimatedTextDrawable as
//     the rendering delegate.
// ADAPTED-BACKEND:
//   - Constructor signature matches upstream: (Context, boolean, boolean, boolean)
//   - setText(CharSequence, boolean) overload added for GraySectionCell/HeaderCell compatibility.
// OMITTED:
//   - AnimatedEmojiSpan, gradient ellipsize, color animation helpers.
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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A lightweight {@code View} that hosts a single {@link AnimatedTextDrawable} as its
 * rendering delegate, matching the surface that Telegram's {@code AnimatedTextView}
 * provides for title/subtitle text in action-bar chrome.
 *
 * Not final so that ported code can subclass it (e.g. GraySectionCell).
 */
public class AnimatedTextView extends View implements Drawable.Callback {

    private final AnimatedTextDrawable textDrawable;
    private CharSequence text = "";
    private boolean first = true;

    public AnimatedTextView(@NonNull Context context) {
        this(context, false, false, false);
    }

    /** Matches upstream constructor: (Context, splitByWords, preserveIndex, startFromEnd). */
    public AnimatedTextView(@NonNull Context context, boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
        super(context);
        textDrawable = new AnimatedTextDrawable(splitByWords, preserveIndex, startFromEnd);
        textDrawable.setCallback(this);
        textDrawable.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textDrawable.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void setTextColor(int color) {
        textDrawable.setTextColor(color);
    }

    public void setTextSize(float px) {
        textDrawable.setTextSize(px);
    }

    public void setTypeface(Typeface typeface) {
        textDrawable.setTypeface(typeface);
    }

    public void setGravity(int gravity) {
        textDrawable.setGravity(gravity);
    }

    public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, Interpolator interpolator) {
        textDrawable.setAnimationProperties(moveAmplitude, startDelay, duration, interpolator);
    }

    public void setText(@Nullable CharSequence value) {
        setText(value, true, true);
    }

    public void setText(@Nullable CharSequence value, boolean animated) {
        setText(value, animated, true);
    }

    public void setText(@Nullable CharSequence value, boolean animated, boolean moveDown) {
        animated = !first && animated;
        first = false;
        text = value == null ? "" : value;
        textDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        textDrawable.setText(text, animated, moveDown);
        invalidate();
    }

    public CharSequence getText() {
        return textDrawable.getText();
    }

    public AnimatedTextDrawable getDrawable() {
        return textDrawable;
    }

    public void setAllowCancel(boolean allow) {
        textDrawable.setAllowCancel(allow);
    }

    public void setOnWidthUpdatedListener(Runnable listener) {
        textDrawable.setOnWidthUpdatedListener(listener);
    }

    public void setIncludeFontPadding(boolean includeFontPadding) {
        textDrawable.setIncludeFontPadding(includeFontPadding);
    }

    public void setRightPadding(float rightPadding) {
        textDrawable.setRightPadding(rightPadding);
    }

    public void setEllipsizeByGradient(boolean enabled) {
        textDrawable.setEllipsizeByGradient(enabled);
    }

    public void cancelAnimation() {
        textDrawable.cancelAnimation();
    }

    public boolean isAnimating() {
        return textDrawable.isAnimating();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        textDrawable.setBounds(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
        textDrawable.setOverrideFullWidth(w - getPaddingLeft() - getPaddingRight());
        textDrawable.setText(text, false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        textDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        textDrawable.draw(canvas);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        super.invalidateDrawable(who);
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
