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

import androidx.annotation.NonNull;

/**
 * NativeDo stub of org.telegram.ui.Components.AvatarDrawable (778 lines upstream).
 *
 * <p>The full upstream class draws monogram letters, applies palette gradients,
 * handles user/chat/forum avatars, and integrates with MessagesController +
 * AvatarSpan + AnimatedEmojiDrawable. None of that is wired in NativeDo yet.
 * For now, expose the API surface ImageReceiver touches ({@code setRoundRadius})
 * so the receiver pipeline compiles.
 */
public class AvatarDrawable extends Drawable {

    private int[] roundRadius = new int[]{0, 0, 0, 0};

    public void setRoundRadius(int radius) {
        roundRadius[0] = roundRadius[1] = roundRadius[2] = roundRadius[3] = radius;
    }

    public void setRoundRadius(int[] radii) {
        if (radii != null && radii.length >= 4) {
            System.arraycopy(radii, 0, roundRadius, 0, 4);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // no-op
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
