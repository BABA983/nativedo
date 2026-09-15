/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public class SizeNotifierFrameLayout extends FrameLayout {

    protected int keyboardHeight;
    private int bottomClip;
    private final Rect rect = new Rect();
    private Drawable backgroundDrawable;
    private boolean backgroundMotion;
    public View backgroundView;
    private boolean attached;
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
        setWillNotDraw(false);
    }

    /**
     * Telegram keeps the wallpaper in a child below the chat content.  Keeping
     * that layer here is important: a RecyclerView must stay transparent so
     * the wallpaper remains visible between rows and behind translucent chrome.
     */
    private final class BackgroundView extends View {
        BackgroundView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Drawable drawable = backgroundDrawable;
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            int width = getWidth();
            int height = getHeight();
            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmapDrawable.getTileModeX() != Shader.TileMode.REPEAT
                        && bitmapDrawable.getTileModeY() != Shader.TileMode.REPEAT
                        && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    float scale = Math.max(
                            width / (float) bitmap.getWidth(),
                            height / (float) bitmap.getHeight());
                    int scaledWidth = (int) Math.ceil(bitmap.getWidth() * scale);
                    int scaledHeight = (int) Math.ceil(bitmap.getHeight() * scale);
                    int left = (width - scaledWidth) / 2;
                    int top = (height - scaledHeight) / 2;
                    drawable.setBounds(left, top, left + scaledWidth, top + scaledHeight);
                } else {
                    drawable.setBounds(0, 0, width, height);
                }
            } else if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable
                    || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, width, height);
            } else {
                drawable.setBounds(0, 0, width, height);
            }
            drawable.draw(canvas);
        }
    }

    public void setBackgroundImage(Drawable drawable, boolean motion) {
        if (backgroundView != null && backgroundDrawable == drawable && backgroundMotion == motion) {
            backgroundView.invalidate();
            return;
        }
        if (backgroundView == null) {
            backgroundView = new BackgroundView(getContext());
            addView(backgroundView, 0, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            ));
        }

        if (attached && backgroundDrawable instanceof MotionBackgroundDrawable) {
            ((MotionBackgroundDrawable) backgroundDrawable).onDetachedFromWindow();
        }
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(null);
        }

        backgroundDrawable = drawable;
        backgroundMotion = motion;
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(backgroundView);
            if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                MotionBackgroundDrawable motionDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                motionDrawable.setParentView(backgroundView);
                if (attached) {
                    motionDrawable.onAttachedToWindow();
                }
            }
        }
        backgroundView.invalidate();
    }

    public Drawable getBackgroundImage() {
        return backgroundDrawable;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            ((MotionBackgroundDrawable) backgroundDrawable).onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            ((MotionBackgroundDrawable) backgroundDrawable).onDetachedFromWindow();
        }
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == backgroundDrawable || super.verifyDrawable(who);
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
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);
        if (rootView == null || rect.bottom == 0 && rect.top == 0) {
            return 0;
        }
        int usableViewHeight = rootView.getHeight()
                - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0)
                - AndroidUtilities.getViewInset(rootView);
        return keyboardHeight = Math.max(0, usableViewHeight - (rect.bottom - rect.top));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        notifyHeightChanged();
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
