/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.ImageReceiver;

/**
 * NativeDo stub of org.telegram.ui.Components.VectorAvatarThumbDrawable (243 lines upstream).
 *
 * <p>Renders avatar thumbnails from Telegram's TL_videoSize emoji/sticker markup.
 * NativeDo has no markup pipeline, but ImageReceiver references the type
 * directly, so the type signature must exist.
 */
public class VectorAvatarThumbDrawable extends Drawable {

    public static final int TYPE_STATIC = 0;
    public static final int TYPE_PROFILE = 1;
    public static final int TYPE_FORUM = 2;

    public VectorAvatarThumbDrawable() {
    }

    public VectorAvatarThumbDrawable(Object vectorImageMarkup, boolean isPremium, int vectorType) {
    }

    public void setParent(ImageReceiver parent) {
    }

    @Override
    public void draw(Canvas canvas) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
