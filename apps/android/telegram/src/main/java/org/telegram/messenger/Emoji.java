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
 * NativeDo stub of org.telegram.messenger.Emoji helpers.
 * Upstream loads emoji glyph atlases and decorates spans. NativeDo doesn't
 * have a Telegram emoji set; we only expose the inner {@code EmojiDrawable}
 * type so ImageReceiver compiles.
 */
public final class Emoji {

    private Emoji() {
    }

    public static class EmojiDrawable extends Drawable {
        @Override public void draw(Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static boolean isValidEmoji(CharSequence code) {
        return false;
    }
}
