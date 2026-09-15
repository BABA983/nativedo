/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.view.View;

/** Minimal helper used by Telegram's FragmentSearchField animations. */
public final class FragmentFloatingButton {
    private FragmentFloatingButton() {}

    public static void setAnimatedVisibility(View view, float factor) {
        if (view == null) {
            return;
        }
        view.setAlpha(factor);
        view.setScaleX(lerp(0.4f, 1f, factor));
        view.setScaleY(lerp(0.4f, 1f, factor));
        view.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }
}
