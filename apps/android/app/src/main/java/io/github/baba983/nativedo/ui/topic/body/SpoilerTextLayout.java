/*
 * This file is part of NativeDo.
 *
 * Native helper derived from Telegram for Android's
 * Components/spoilers/SpoilersTextView and SpoilersClickDetector.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Spanned;
import android.view.SoundEffectConstants;
import android.view.View;
import java.util.ArrayList;
import java.util.Stack;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class SpoilerTextLayout {
    private SpoilerTextLayout() {
    }

    static void collect(View host, Layout layout, Stack<SpoilerEffect> pool, ArrayList<SpoilerEffect> spoilers, boolean revealed) {
        pool.addAll(spoilers);
        spoilers.clear();
        if (!revealed && layout != null && layout.getText() instanceof Spanned) {
            SpoilerEffect.addSpoilers(host, layout, -2, -2, pool, spoilers);
        }
    }

    static void draw(Canvas canvas, Layout layout, ArrayList<SpoilerEffect> spoilers, Path boundsPath, Path ripplePath) {
        if (spoilers.isEmpty()) {
            layout.draw(canvas);
            return;
        }
        canvas.save();
        SpoilerEffect.clipOutCanvas(canvas, spoilers);
        layout.draw(canvas);
        canvas.restore();

        if (spoilers.get(0).hasRipplePath()) {
            boundsPath.rewind();
            for (int i = 0; i < spoilers.size(); i++) {
                Rect bounds = spoilers.get(i).getBounds();
                boundsPath.addRect(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW);
            }
            ripplePath.rewind();
            spoilers.get(0).getRipplePath(ripplePath);
            canvas.save();
            canvas.clipPath(boundsPath);
            canvas.clipPath(ripplePath);
            layout.draw(canvas);
            canvas.restore();
        }

        for (int i = 0; i < spoilers.size(); i++) {
            spoilers.get(i).draw(canvas);
        }
    }

    static boolean contains(ArrayList<SpoilerEffect> spoilers, float x, float y) {
        return spoilerAt(spoilers, x, y) != null;
    }

    static boolean reveal(View host, Layout layout, Stack<SpoilerEffect> pool, ArrayList<SpoilerEffect> spoilers, float x, float y, Runnable onRevealed) {
        SpoilerEffect clicked = spoilerAt(spoilers, x, y);
        if (clicked == null) {
            return false;
        }
        host.playSoundEffect(SoundEffectConstants.CLICK);
        clicked.setOnRippleEndCallback(() -> host.post(() -> {
            setSpansRevealed(layout);
            pool.addAll(spoilers);
            spoilers.clear();
            onRevealed.run();
            host.invalidate();
        }));
        float radius = (float) Math.sqrt(Math.pow(layout.getWidth(), 2) + Math.pow(layout.getHeight(), 2));
        for (int i = 0; i < spoilers.size(); i++) {
            spoilers.get(i).startRipple(x, y, radius);
        }
        host.invalidate();
        return true;
    }

    private static SpoilerEffect spoilerAt(ArrayList<SpoilerEffect> spoilers, float x, float y) {
        for (int i = 0; i < spoilers.size(); i++) {
            SpoilerEffect spoiler = spoilers.get(i);
            if (spoiler.getBounds().contains((int) x, (int) y)) {
                return spoiler;
            }
        }
        return null;
    }

    private static void setSpansRevealed(Layout layout) {
        if (!(layout.getText() instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) layout.getText();
        TextStyleSpan[] spans = spanned.getSpans(0, spanned.length(), TextStyleSpan.class);
        for (int i = 0; i < spans.length; i++) {
            if (spans[i].isSpoiler()) {
                spans[i].setSpoilerRevealed(true);
            }
        }
    }
}
