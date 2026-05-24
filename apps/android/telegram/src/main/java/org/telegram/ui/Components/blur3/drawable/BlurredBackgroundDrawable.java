package org.telegram.ui.Components.blur3.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Minimal stub of upstream BlurredBackgroundDrawable for v1.
 * Only the type signature is needed so ScrollSlidingTextTabStrip compiles;
 * actual blur rendering is not used in v1.
 */
public class BlurredBackgroundDrawable extends Drawable {

    @Override
    public void draw(@NonNull Canvas canvas) {
        // No-op for v1
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /** V1 stub for ScrollSlidingTextTabStrip compatibility. */
    public void setRadius(float radius) {}

    /** V1 stub for ScrollSlidingTextTabStrip compatibility. */
    public void setShadowAlpha(float alpha) {}
}
