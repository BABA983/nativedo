package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

/**
 * Minimal stub of upstream LoadingDrawable for v1.
 * Only the type signature + basic methods needed by ButtonWithCounterView.
 */
public class LoadingDrawable extends Drawable {

    public final Paint strokePaint = new Paint();
    private Path path;
    private boolean disappearing;

    public LoadingDrawable() {
    }

    public LoadingDrawable(Theme.ResourcesProvider resourcesProvider) {
    }

    public void setGradientScale(float scale) {}
    public void setAppearByGradient(boolean appear) {}
    public void setColors(int... colors) {}
    public void resetDisappear() {}
    public void setRadiiDp(float radiiDp) {}
    public void disappear() { disappearing = true; }
    public void reset() { disappearing = false; }
    public boolean isDisappeared() { return true; }
    public boolean isDisappearing() { return disappearing; }
    public long timeToDisappear() { return 0; }
    public void usePath(Path path) { this.path = path; }
    public void updateBounds() {}

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (path != null) {
            canvas.drawPath(path, strokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() { return PixelFormat.TRANSPARENT; }
}
