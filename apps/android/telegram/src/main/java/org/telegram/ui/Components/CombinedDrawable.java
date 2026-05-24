/*
 * Stub for compile compatibility. Full port deferred.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;

/**
 * A Drawable that combines a background and a foreground drawable.
 * Stub — full port deferred.
 */
public class CombinedDrawable extends Drawable {

    private final Drawable background;
    private final Drawable foreground;

    public CombinedDrawable(Drawable background, Drawable foreground) {
        this.background = background;
        this.foreground = foreground;
    }

    public CombinedDrawable(Drawable background, Drawable foreground, int offsetX, int offsetY) {
        this.background = background;
        this.foreground = foreground;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        if (background != null) {
            background.setBounds(left, top, right, bottom);
        }
        if (foreground != null) {
            foreground.setBounds(left, top, right, bottom);
        }
    }

    @Override
    public void draw(android.graphics.Canvas canvas) {
        if (background != null) {
            background.draw(canvas);
        }
        if (foreground != null) {
            foreground.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (background != null) {
            background.setAlpha(alpha);
        }
        if (foreground != null) {
            foreground.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        if (background != null) {
            background.setColorFilter(colorFilter);
        }
        if (foreground != null) {
            foreground.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return background != null ? background.getIntrinsicWidth() : 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return background != null ? background.getIntrinsicHeight() : 0;
    }
}
