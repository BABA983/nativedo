/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.view.View;

/** Minimal helper used by Telegram's FragmentSearchField animations. */
public final class FragmentFloatingButton {
    private FragmentFloatingButton() {}

    public static void setAnimatedVisibility(View view, float factor) {
        if (view == null) {
            return;
        }
        view.setAlpha(factor);
        view.setScaleX(factor);
        view.setScaleY(factor);
        view.setVisibility(factor <= 0.01f ? View.GONE : View.VISIBLE);
    }
}
