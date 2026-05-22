// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/Components/ItemOptions.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: popup-menu=L1, dim-scrim=L1, popup-animation=L0
// HOST-WIRING:
//   - Telegram imports/resources rewritten to NativeDo.
//   - Uses NativeDo's existing ActionBarPopupWindow L1 slice for popup rendering.
// ADAPTED:
//   - Keeps only the menu shape used by ArticleViewer back-history:
//     makeOptions(container, scrimView), add(), getLast(), setScrimViewBackground(),
//     setBackgroundColor(), updateColors(), setOnDismiss(), show(), dismiss().
//   - Keeps upstream non-moving scrim drawing: the cloned scrimView is drawn at full alpha
//     while the dim background animates, avoiding a semi-transparent duplicate over the
//     original back button.
//   - Ports upstream OnPreDraw invalidation for the scrim clone so animated drawables inside
//     the scrimView (BackDrawable rotation) keep repainting while options are shown.
// OMITTED:
//   - Swipe-back nested menus, blur, profile/account rows, premium helpers, custom positioning
//     variants, moving scrim, Telegram resource-provider overrides beyond the constructor shape.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/Components/ItemOptions.java.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenuSubItem;
import io.github.baba983.nativedo.ui.actionbar.ActionBarPopupWindow;
import io.github.baba983.nativedo.ui.actionbar.Theme;

public class ItemOptions {

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @NonNull View scrimView) {
        return new ItemOptions(container, null, scrimView);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @Nullable Theme.ResourcesProvider resourcesProvider, @NonNull View scrimView) {
        return new ItemOptions(container, resourcesProvider, scrimView);
    }

    private final ViewGroup container;
    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final View scrimView;
    private final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    private final float[] point = new float[2];
    private Drawable scrimViewBackground;
    private ActionBarPopupWindow actionBarPopupWindow;
    private Runnable dismissListener;
    private DimView dimView;
    private ValueAnimator dimAnimator;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private boolean dismissAnimated = true;
    private int dimAlpha;

    private ItemOptions(ViewGroup container, Theme.ResourcesProvider resourcesProvider, View scrimView) {
        this.container = container;
        this.context = container.getContext();
        this.resourcesProvider = resourcesProvider;
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705f ? 0x66 : 0x33;
        this.layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        Drawable background = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert4);
        if (background != null) {
            layout.setBackgroundDrawable(background.mutate());
        }
    }

    public ItemOptions add(CharSequence text, Runnable onClickListener) {
        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        subItem.setText(text);
        subItem.setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
        subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), .12f));
        subItem.setOnClickListener(view -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            dismiss();
        });
        layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return this;
    }

    public ActionBarMenuSubItem getLast() {
        if (layout.getItemsCount() <= 0) {
            return null;
        }
        View child = layout.getItemAt(layout.getItemsCount() - 1);
        return child instanceof ActionBarMenuSubItem ? (ActionBarMenuSubItem) child : null;
    }

    public int getItemsCount() {
        return layout.getItemsCount();
    }

    public ItemOptions setScrimViewBackground(Drawable drawable) {
        scrimViewBackground = drawable;
        return this;
    }

    public ItemOptions setBackgroundColor(int color) {
        Drawable background = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert4);
        if (background != null) {
            background = background.mutate();
            background.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            layout.setBackgroundDrawable(background);
        }
        return this;
    }

    public void updateColors() {
    }

    public ItemOptions setOnDismiss(Runnable dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public ItemOptions show() {
        if (actionBarPopupWindow != null || getItemsCount() <= 0) {
            return this;
        }
        getPointOnScreen(scrimView, container, point);
        RectF scrimViewBounds = new RectF(0, 0, scrimView.getMeasuredWidth(), scrimView.getMeasuredHeight());
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.AT_MOST)
        );
        int x = (int) (container.getX() + point[0] + scrimViewBounds.width() - (layout.getMeasuredWidth() - layout.getPaddingRight()));
        int y = (int) (point[1] + scrimViewBounds.height() + container.getY());
        if (y + layout.getMeasuredHeight() + AndroidUtilities.dp(16) > AndroidUtilities.displaySize.y) {
            y = (int) (point[1] - layout.getMeasuredHeight() + container.getY());
        }

        dimView = new DimView(context);
        preDrawListener = () -> {
            if (dimView != null) {
                dimView.invalidate();
            }
            return true;
        };
        container.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        dimView.setProgress(0f);
        dimAnimator = ValueAnimator.ofFloat(0f, 1f);
        dimAnimator.addUpdateListener(animation -> {
            if (dimView != null) {
                dimView.setProgress((float) animation.getAnimatedValue());
            }
        });
        dimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (dimView != null) {
                    dimView.setProgress(1f);
                }
                dimAnimator = null;
            }
        });
        dimAnimator.setDuration(150);
        dimAnimator.start();

        actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                ItemOptions.this.dismissDim(dismissAnimated);
                runDismissListener();
            }
        };
        actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                actionBarPopupWindow = null;
                dismissDim(dismissAnimated);
                runDismissListener();
            }
        });
        actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        actionBarPopupWindow.showAtLocation(container, Gravity.NO_GRAVITY, x, y);
        actionBarPopupWindow.startAnimation();
        return this;
    }

    public boolean isShown() {
        return actionBarPopupWindow != null && actionBarPopupWindow.isShowing();
    }

    public void dismiss() {
        dismiss(true);
    }

    public void dismiss(boolean animated) {
        if (actionBarPopupWindow != null) {
            ActionBarPopupWindow popupWindow = actionBarPopupWindow;
            actionBarPopupWindow = null;
            dismissAnimated = animated;
            popupWindow.dismiss();
        } else {
            dismissDim(animated);
            runDismissListener();
        }
    }

    private void dismissDim(boolean animated) {
        if (dimView == null) {
            return;
        }
        DimView dimViewFinal = dimView;
        dimView = null;
        if (dimAnimator != null) {
            dimAnimator.cancel();
            dimAnimator = null;
        }
        if (!animated) {
            dimViewFinal.setProgress(0f);
            removeFromParent(dimViewFinal);
            removePreDrawListener();
            return;
        }
        dimAnimator = ValueAnimator.ofFloat(dimViewFinal.dimProgress, 0f);
        dimAnimator.addUpdateListener(animation -> dimViewFinal.setProgress((float) animation.getAnimatedValue()));
        dimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dimViewFinal.setProgress(0f);
                removeFromParent(dimViewFinal);
                removePreDrawListener();
                dimAnimator = null;
            }
        });
        dimAnimator.setDuration(150);
        dimAnimator.start();
    }

    private void removePreDrawListener() {
        if (preDrawListener != null) {
            container.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            preDrawListener = null;
        }
    }

    private void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void runDismissListener() {
        if (dismissListener != null) {
            dismissListener.run();
            dismissListener = null;
        }
    }

    public static void getPointOnScreen(View view, ViewGroup finalContainer, float[] point) {
        float x = 0;
        float y = 0;
        while (view != null && view != finalContainer) {
            y += view.getY();
            x += view.getX();
            if (!(view.getParent() instanceof View)) {
                break;
            }
            view = (View) view.getParent();
        }
        point[0] = x - finalContainer.getPaddingLeft();
        point[1] = y - finalContainer.getPaddingTop();
    }

    private class DimView extends View {
        private final int[] scrimLocation = new int[2];
        private final int[] dimLocation = new int[2];
        private final int dim;
        public float dimProgress;

        DimView(Context context) {
            super(context);
            dim = ColorUtils.setAlphaComponent(0x00000000, dimAlpha);
        }

        void setProgress(float progress) {
            if (dimProgress == progress) {
                return;
            }
            dimProgress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.multAlpha(dim, dimProgress));
            if (scrimView == null || scrimView.getWidth() <= 0 || scrimView.getHeight() <= 0) {
                return;
            }
            scrimView.getLocationInWindow(scrimLocation);
            getLocationInWindow(dimLocation);
            float x = scrimLocation[0] - dimLocation[0];
            float y = scrimLocation[1] - dimLocation[1];
            if (scrimViewBackground != null) {
                scrimViewBackground.setBounds(
                        (int) (x + (scrimView.getWidth() - scrimViewBackground.getIntrinsicWidth()) / 2f),
                        (int) (y + (scrimView.getHeight() - scrimViewBackground.getIntrinsicHeight()) / 2f),
                        (int) (x + (scrimView.getWidth() + scrimViewBackground.getIntrinsicWidth()) / 2f),
                        (int) (y + (scrimView.getHeight() + scrimViewBackground.getIntrinsicHeight()) / 2f)
                );
                scrimViewBackground.setAlpha((int) (0xFF * dimProgress));
                scrimViewBackground.draw(canvas);
            }
            canvas.save();
            canvas.translate(x, y);
            scrimView.draw(canvas);
            canvas.restore();
        }
    }
}
