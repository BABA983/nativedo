// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java
// EXACT-SUBSYSTEMS: none
// HOST-WIRING:
//   - Provides API surface (setRadius / setShadowAlpha) needed by ported callers.
// ADAPTED-PRODUCT:
//   - No-op stub. NativeDo doesn't ship the blur3 pipeline; tab-strip blur
//     background is not used in current product.
// OMITTED:
//   - Real Stack-Blur compositing.

package io.github.baba983.nativedo.ui.components.blur3.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * NativeDo placeholder for Telegram's BlurredBackgroundDrawable. Allows ported
 * callers (e.g. ScrollSlidingTextTabStrip) to compile while the blur subsystem
 * is OMITTED. All draw operations are no-ops.
 */
public class BlurredBackgroundDrawable extends Drawable {

    public void setRadius(float radius) {
        // OMITTED: blur radius unused in NativeDo.
    }

    public void setShadowAlpha(float alpha) {
        // OMITTED: shadow alpha unused in NativeDo.
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // OMITTED: no-op stub.
    }

    @Override
    public void setAlpha(int alpha) {
        // OMITTED.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // OMITTED.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
