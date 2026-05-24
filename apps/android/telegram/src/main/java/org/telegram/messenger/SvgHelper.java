/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * NativeDo stub of org.telegram.messenger.SvgHelper.
 * Upstream parses Telegram-stripped path SVGs into Drawables. NativeDo doesn't
 * render those placeholders yet; only the inner {@code SvgDrawable} type and
 * the static helpers ImageReceiver references are exposed.
 */
public class SvgHelper {

    public static class SvgResult {
        public android.graphics.Bitmap bitmap;
        public int w;
        public int h;

        public android.graphics.Bitmap getBitmap() {
            return bitmap;
        }

        public java.util.ArrayList<Object> getGiftPatternPositions() {
            return null;
        }
    }

    public static SvgResult getSvgBitmap(java.io.File file, int w, int h, boolean white) {
        return null;
    }

    public static class SvgDrawable extends Drawable {
        public boolean isPaint;

        public void updateLiteValues() {
        }

        public void setColorKey(int colorKey) {
        }

        public void setupGradient(int colorKey, float dy, boolean shadow) {
        }

        public void setAspectFill(boolean aspectFill) {
        }

        public void setParent(org.telegram.messenger.ImageReceiver parent) {
        }

        public void drawInternal(Canvas canvas, boolean drawInBackground, int threadIndex, long currentTime, float x, float y, float w, float h) {
        }

        @Override public void draw(Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static SvgDrawable getDrawable(String xml) {
        return null;
    }

    public static SvgDrawable getDrawableByPath(android.graphics.Path path, int w, int h) {
        return null;
    }

    public static android.graphics.Bitmap getBitmap(int res, int width, int height, int color) {
        return null;
    }

    public static android.graphics.Bitmap getBitmap(java.io.File file, int width, int height, boolean white) {
        return null;
    }
}
