/*
 * This file is part of NativeDo.
 *
 * Minimal stub of org.telegram.ui.Components.spoilers.SpoilerEffect.
 * The upstream class (982 lines) implements animated blur/censorship effects
 * over spoiler text spans. NativeDo doesn't need this feature; the stub
 * satisfies the API surface consumed by SimpleTextView.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components.spoilers;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.text.Layout;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Stack;

/**
 * Stub that satisfies the {@code SpoilerEffect} API used by
 * {@link org.telegram.ui.ActionBar.SimpleTextView}.
 *
 * <p>Upstream calls:
 * <ul>
 *   <li>{@link #addSpoilers(View, Layout, int, int, Stack, List)} — populates the list</li>
 *   <li>{@code getBounds()} — returns clip rect (inherited from Drawable)</li>
 *   <li>{@code draw(Canvas)} — renders the blur effect</li>
 * </ul>
 *
 * <p>This stub makes {@code addSpoilers} a no-op (no spoilers detected) and
 * {@code draw} empty, so text renders normally without blur.
 */
public class SpoilerEffect extends android.graphics.drawable.Drawable {

    /**
     * Stub — always clears the list so no spoilers are rendered.
     */
    public static void addSpoilers(
            @Nullable View view,
            @Nullable Layout textLayout,
            int left,
            int right,
            @Nullable Stack<SpoilerEffect> pool,
            @NonNull List<SpoilerEffect> out) {
        // no-op: spoilers not supported
    }

    // --- Drawable contract (no-ops) ---

    @Override
    public void draw(@NonNull Canvas canvas) {
        // no-op
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
}
