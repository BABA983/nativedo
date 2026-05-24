package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.view.View;

/**
 * NativeDo no-op stub for Telegram's Shaker. Lets ported callers compile while
 * the drag-reorder visual shake is OMITTED.
 */
public class Shaker {

    @SuppressWarnings("unused")
    public Shaker(View parent) {
        // OMITTED.
    }

    public void concat(Canvas canvas, float intensity, float cx, float cy) {
        // OMITTED: no transform applied.
    }
}
