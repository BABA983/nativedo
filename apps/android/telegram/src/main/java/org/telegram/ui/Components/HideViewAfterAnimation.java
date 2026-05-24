/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorListenerAdapter;
import android.view.View;

/**
 * NativeDo stub of org.telegram.ui.Components.HideViewAfterAnimation.
 * Upstream is an animator listener that toggles a view's GONE/INVISIBLE state
 * when the animation finishes.
 */
public class HideViewAfterAnimation extends AnimatorListenerAdapter {

    private final View view;
    private final int hideMode;

    public HideViewAfterAnimation(View view) {
        this(view, View.GONE);
    }

    public HideViewAfterAnimation(View view, int hideMode) {
        this.view = view;
        this.hideMode = hideMode;
    }

    @Override
    public void onAnimationEnd(android.animation.Animator animation) {
        if (view != null) {
            view.setVisibility(hideMode);
        }
    }
}
