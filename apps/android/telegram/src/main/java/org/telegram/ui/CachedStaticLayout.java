/*
 * This file is part of NativeDo.
 *
 * Lightweight host-wired subset of Telegram's CachedStaticLayout used by
 * SpoilerEffect's patched-layout rendering path.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui;

import android.graphics.Canvas;
import android.text.StaticLayout;
import android.text.TextPaint;

public class CachedStaticLayout {
    public final StaticLayout layout;

    public CachedStaticLayout(StaticLayout source) {
        layout = source;
    }

    public void draw(Canvas canvas) {
        layout.draw(canvas);
    }

    public CharSequence getText() {
        return layout.getText();
    }

    public TextPaint getPaint() {
        return layout.getPaint();
    }

    public int getWidth() {
        return layout.getWidth();
    }

    public int getHeight() {
        return layout.getHeight();
    }

    public int getLineCount() {
        return layout.getLineCount();
    }

    public int getLineTop(int line) {
        return layout.getLineTop(line);
    }

    public int getLineBottom(int line) {
        return layout.getLineBottom(line);
    }

    public float getLineLeft(int line) {
        return layout.getLineLeft(line);
    }

    public float getLineRight(int line) {
        return layout.getLineRight(line);
    }

    public float getLineWidth(int line) {
        return layout.getLineWidth(line);
    }

    public float getPrimaryHorizontal(int offset) {
        return layout.getPrimaryHorizontal(offset);
    }

    public int getLineEnd(int line) {
        return layout.getLineEnd(line);
    }

    public int getLineStart(int line) {
        return layout.getLineStart(line);
    }
}
