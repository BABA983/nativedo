/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

public class SizeNotifierFrameLayout extends FrameLayout {

    /**
     * Upstream interface used by BaseCell for delegated invalidation.
     * BaseCell sets an invalidateCallback via listenInvalidate(Runnable)
     * and calls invalidateCallback.run() to notify its parent that a
     * cached RenderNode frame needs refreshing.
     */
    public interface IViewWithInvalidateCallback {
        void listenInvalidate(Runnable callback);
    }

    public SizeNotifierFrameLayout(Context context) {
        super(context);
        setFitsSystemWindows(false);
    }

    public int measureKeyboardHeight() {
        return 0;
    }

    public void setBottomClip(int value) {
    }

    public void notifyHeightChanged() {
    }
}
