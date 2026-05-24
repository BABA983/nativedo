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
import android.graphics.drawable.GradientDrawable;

/**
 * NativeDo stub of org.telegram.ui.Components.BackgroundGradientDrawable.
 * Upstream paints animated multi-stop chat backgrounds. NativeDo only needs
 * the orientation helper ImageLoader references.
 */
public class BackgroundGradientDrawable extends Drawable {

    public static GradientDrawable.Orientation getGradientOrientation(int rotation) {
        switch (rotation) {
            case 45:   return GradientDrawable.Orientation.TL_BR;
            case 90:   return GradientDrawable.Orientation.LEFT_RIGHT;
            case 135:  return GradientDrawable.Orientation.BL_TR;
            case 180:  return GradientDrawable.Orientation.BOTTOM_TOP;
            case 225:  return GradientDrawable.Orientation.BR_TL;
            case 270:  return GradientDrawable.Orientation.RIGHT_LEFT;
            case 315:  return GradientDrawable.Orientation.TR_BL;
            default:   return GradientDrawable.Orientation.TOP_BOTTOM;
        }
    }

    @Override public void draw(Canvas canvas) {}
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
