/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * NativeDo stub of org.telegram.ui.Components.MotionBackgroundDrawable.
 * Upstream renders Telegram's animated multi-colour chat background. NativeDo
 * doesn't use it; only the type signature must exist so ImageLoader compiles.
 */
public class MotionBackgroundDrawable extends Drawable {

    public static int patternColor = 0;

    public MotionBackgroundDrawable() {
    }

    public MotionBackgroundDrawable(int color1, int color2, int color3, int color4, boolean preview) {
    }

    public static int getPatternColor(int color1, int color2, int color3, int color4) {
        return color1;
    }

    @Override public void draw(Canvas canvas) {}
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    public void setColors(int color1, int color2, int color3, int color4) {
    }

    public void setPatternBitmap(int intensity, android.graphics.Bitmap bitmap) {
    }
}
