/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public class SizeNotifierFrameLayout extends FrameLayout {

    protected int keyboardHeight;
    private int bottomClip;
    protected SizeNotifierFrameLayoutDelegate delegate;
    protected final ArrayList<SizeNotifierFrameLayoutDelegate> delegates = new ArrayList<>();

    /**
     * Upstream interface used by BaseCell for delegated invalidation.
     * BaseCell sets an invalidateCallback via listenInvalidate(Runnable)
     * and calls invalidateCallback.run() to notify its parent that a
     * cached RenderNode frame needs refreshing.
     */
    public interface IViewWithInvalidateCallback {
        void listenInvalidate(Runnable callback);
    }

    public interface SizeNotifierFrameLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public static class SimplerCanvas extends android.graphics.Canvas {
    }

    public SizeNotifierFrameLayout(Context context) {
        super(context);
        setFitsSystemWindows(false);
    }

    public void setDelegate(SizeNotifierFrameLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    public void addDelegate(SizeNotifierFrameLayoutDelegate delegate) {
        if (delegate != null && !delegates.contains(delegate)) {
            delegates.add(delegate);
        }
    }

    public void removeDelegate(SizeNotifierFrameLayoutDelegate delegate) {
        delegates.remove(delegate);
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public int measureKeyboardHeight() {
        return 0;
    }

    public void setBottomClip(int value) {
        if (bottomClip != value) {
            bottomClip = value;
            invalidate();
        }
    }

    public void notifyHeightChanged() {
        if (delegate == null && delegates.isEmpty()) {
            return;
        }
        keyboardHeight = measureKeyboardHeight();
        final boolean isWidthGreater = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
        post(() -> {
            if (delegate != null) {
                delegate.onSizeChanged(keyboardHeight, isWidthGreater);
            }
            for (int i = 0; i < delegates.size(); i++) {
                delegates.get(i).onSizeChanged(keyboardHeight, isWidthGreater);
            }
        });
    }

    @Override
    protected boolean drawChild(android.graphics.Canvas canvas, View child, long drawingTime) {
        if (bottomClip != 0) {
            int save = canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - bottomClip);
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(save);
            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}
