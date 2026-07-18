package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.telegram.messenger.ImageReceiver;

/**
 * Stub for Telegram animated emoji drawable. Not used in NativeDo v1.
 * Methods added for BackupImageView compatibility.
 */
public class AnimatedEmojiDrawable extends Drawable {
    public static final int CACHE_TYPE_MESSAGES = 0;
    public static final int CACHE_TYPE_MESSAGES_LARGE = 1;

    /** Host stub: pin WebsiteView uses document-id favicons; return empty drawable. */
    public static AnimatedEmojiDrawable make(int account, int cacheType, long documentId) {
        return new AnimatedEmojiDrawable();
    }

    public static AnimatedEmojiDrawable make(int account, int cacheType, long documentId, String absolutePath) {
        return new AnimatedEmojiDrawable();
    }

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

    /**
     * Stub of upstream SwapAnimatedEmojiDrawable.
     * SimpleTextView uses {@code instanceof} checks against this type to
     * determine right-drawable outside padding. The stub satisfies the type
     * check without any drawing behaviour.
     */
    public static class SwapAnimatedEmojiDrawable extends Drawable {
        public SwapAnimatedEmojiDrawable(android.view.View parent, int size, boolean invalidateParent, int cacheType) {}
        public SwapAnimatedEmojiDrawable(android.view.View parent, int size, int cacheType) {}
        @Override public void draw(android.graphics.Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
