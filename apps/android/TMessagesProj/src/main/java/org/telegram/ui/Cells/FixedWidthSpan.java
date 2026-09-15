/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Cells;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/**
 * A span that occupies a fixed width and draws nothing.
 * Used by SimpleTextView to reserve space for an inline drawable.
 */
public class FixedWidthSpan extends ReplacementSpan {

    private final int width;

    public FixedWidthSpan(int width) {
        this.width = width;
    }

    @Override
    public int getSize(
            @androidx.annotation.NonNull Paint paint,
            CharSequence text,
            int start,
            int end,
            Paint.FontMetricsInt fm) {
        if (fm != null) {
            fm.top = -1;
            fm.ascent = -1;
            fm.bottom = 0;
            fm.descent = 0;
        }
        return width;
    }

    @Override
    public void draw(
            @androidx.annotation.NonNull Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            @androidx.annotation.NonNull Paint paint) {
        // draws nothing — this is a width-reserving spacer
    }
}
