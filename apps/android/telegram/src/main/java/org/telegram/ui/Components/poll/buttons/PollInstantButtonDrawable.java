/*
 * This file is part of NativeDo.
 *
 * NativeDo-scoped subset of Telegram for Android's PollInstantButtonDrawable.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components.poll.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;

public class PollInstantButtonDrawable extends PollButtonDrawableBase {
    private final AnimatedTextView.AnimatedTextDrawable buttonTextAnimatedDrawable;

    public PollInstantButtonDrawable(View parent) {
        super(null);
        buttonTextAnimatedDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false);
        buttonTextAnimatedDrawable.setTypeface(AndroidUtilities.bold());
        buttonTextAnimatedDrawable.setTextSize(dp(13));
        buttonTextAnimatedDrawable.setGravity(Gravity.CENTER);
        buttonTextAnimatedDrawable.setCallback(parent);
        setSelectorsColor(Theme.getColor(Theme.key_listSelector));
    }

    public void setButtonText(CharSequence text, boolean animated) {
        buttonTextAnimatedDrawable.setText(text, animated);
    }

    public void setButtonTextColor(int color) {
        buttonTextAnimatedDrawable.setTextColor(color);
    }

    public void setLoading(boolean loading, boolean animated) {
    }

    public float getProgressFactor() {
        return 0f;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        buttonTextAnimatedDrawable.draw(canvas);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        buttonTextAnimatedDrawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    @Override
    public void setupCallbacks(Drawable.Callback callback) {
        super.setupCallbacks(callback);
        buttonTextAnimatedDrawable.setCallback(callback);
    }

    @Override
    protected void onAlphaChanged(int alpha) {
        super.onAlphaChanged(alpha);
        buttonTextAnimatedDrawable.setAlpha(alpha);
    }

    @Override
    public boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == buttonTextAnimatedDrawable;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
