package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.ui.ActionBar.Theme;

public class MediaActionDrawable extends Drawable {
    public static final int ICON_NONE = 0;
    public static final int ICON_DOWNLOAD = 1;
    public static final int ICON_CANCEL = 2;
    public static final int ICON_GIF = 3;
    public static final int ICON_PLAY = 4;
    public static final int ICON_PAUSE = 5;
    public static final int ICON_CHECK = 6;
    public static final int ICON_EMPTY = 7;

    private int icon;
    private int previousIcon;
    private float progress;

    public void setMini(boolean mini) {}
    public boolean setIcon(int icon, boolean animated) {
        previousIcon = this.icon;
        this.icon = icon;
        return true;
    }
    public int getCurrentIcon() { return icon; }
    public int getPreviousIcon() { return previousIcon; }
    public float getTransitionProgress() { return 1f; }
    public void setProgress(float progress, boolean animated) { this.progress = progress; }
    public float getProgress() { return progress; }
    public void setDelegate(Runnable delegate) {}
    public void setBackgroundDrawable(Theme.MessageDrawable drawable) {}
    public void setBackgroundGradientDrawable(LinearGradient drawable) {}
    public void setColor(int color) {}
    public void setBackColor(int color) {}
    public void setHasOverlayImage(boolean value) {}
    public void setOverrideAlpha(float alpha) {}
    @Override public void draw(Canvas canvas) {}
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
}
