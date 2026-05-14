// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/ui/Components/Shaker.java
// EXACT-SUBSYSTEMS: none
// HOST-WIRING:
//   - Provides Shaker(View) constructor + concat(Canvas,float,float,float) used
//     by ScrollSlidingTextTabStrip's reorder visualisation.
// ADAPTED-PRODUCT:
//   - No-op stub. NativeDo doesn't ship Telegram's drag-reorder shake animation;
//     reorder feature is unused (delegate.canReorder always false in NativeDo).
// OMITTED:
//   - Real shake transform animation.

package io.github.baba983.nativedo.ui.components;

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
