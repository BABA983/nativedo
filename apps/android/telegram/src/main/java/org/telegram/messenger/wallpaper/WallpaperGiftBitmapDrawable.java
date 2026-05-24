package org.telegram.messenger.wallpaper;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Minimal stub of upstream WallpaperGiftBitmapDrawable for v1.
 * Only the static factory method signature is needed so ImageLoader compiles.
 */
public final class WallpaperGiftBitmapDrawable {

    private WallpaperGiftBitmapDrawable() {
    }

    @NonNull
    public static BitmapDrawable create(@Nullable Bitmap bitmap, @Nullable Object positions) {
        if (bitmap != null) {
            return new BitmapDrawable(bitmap);
        }
        return new BitmapDrawable(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
    }
}
