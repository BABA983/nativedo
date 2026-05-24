package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.telegram.messenger.ImageReceiver;

/**
 * Stub for Telegram animated emoji drawable. Not used in NativeDo v1.
 * Methods added for BackupImageView compatibility.
 */
public class AnimatedEmojiDrawable extends Drawable {

    /** V1 stub: no-op for BackupImageView. */
    public void addView(Object view) {}

    /** V1 stub: no-op for BackupImageView. */
    public void removeView(Object view) {}

    /** V1 stub: returns null for BackupImageView. */
    @Nullable
    public ImageReceiver getImageReceiver() {
        return null;
    }

    @Override
    public void draw(android.graphics.Canvas canvas) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
