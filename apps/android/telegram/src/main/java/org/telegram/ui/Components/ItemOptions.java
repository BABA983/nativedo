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
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

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
    private final int[] rootLocation = new int[2];
    private final int[] anchorLocation = new int[2];
    private final Rect visibleFrame = new Rect();
    private Drawable scrimViewBackground;
    private ActionBarPopupWindow actionBarPopupWindow;
    private Runnable dismissListener;
    private DimView dimView;
    private ValueAnimator dimAnimator;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private boolean dismissAnimated = true;
    private int dimAlpha;
    private float translateX;
    private float translateY;
    private int minWidth;
    private int textColor;
    private int iconColor;
    private int selectorColor;
    private int gapColor;

    private ItemOptions(ViewGroup container, Theme.ResourcesProvider resourcesProvider, View scrimView) {
        this.container = container;
        this.context = container.getContext();
        this.resourcesProvider = resourcesProvider;
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705f ? 0x66 : 0x33;
        this.layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        this.textColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
        this.iconColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider);
        this.selectorColor = Theme.multAlpha(textColor, .12f);
        this.gapColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider);
        Drawable background = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert4);
        if (background != null) {
            layout.setBackgroundDrawable(background.mutate());
        }
    }

    public ItemOptions add(CharSequence text, Runnable onClickListener) {
        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        if (minWidth > 0) {
            subItem.setMinimumWidth(AndroidUtilities.dp(minWidth));
        }
        subItem.setTextAndIcon(text, 0);
        subItem.setColors(textColor, iconColor);
        subItem.setSelectorColor(selectorColor);
        subItem.setOnClickListener(view -> {
            if (onClickListener != null) {
                onClickListener.run();
            }
            dismiss();
        });
        layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return this;
    }

    public ItemOptions add(int iconResId, CharSequence text, Runnable onClickListener) {
        add(text, onClickListener);
        ActionBarMenuSubItem item = getLast();
        if (item != null) {
            item.setTextAndIcon(text, iconResId);
        }
        return this;
    }

    public ItemOptions addGap() {
        View gap = new View(context);
        gap.setBackgroundColor(gapColor);
        layout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 6, 0, 6));
        return this;
    }

    public ItemOptions setDimAlpha(int dimAlpha) {
        this.dimAlpha = dimAlpha;
        return this;
    }

    public ItemOptions setColors(int textColor, int iconColor) {
        this.textColor = textColor;
        this.iconColor = iconColor;
        for (int i = 0; i < layout.getItemsCount(); i++) {
            View child = layout.getItemAt(i);
            if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setColors(textColor, iconColor);
            }
        }
        return this;
    }

    public ItemOptions translate(int x, int y) {
        translateX = x;
        translateY = y;
        return this;
    }

    public ItemOptions setMinWidth(int width) {
        minWidth = width;
        updateItemMinimumWidths();
        return this;
    }

    public ItemOptions setSelectorColor(int color) {
        selectorColor = color;
        for (int i = 0; i < layout.getItemsCount(); i++) {
            View child = layout.getItemAt(i);
            if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setSelectorColor(color);
            }
        }
        return this;
    }

    public ItemOptions setGapBackgroundColor(int color) {
        gapColor = color;
        for (int i = 0; i < layout.getItemsCount(); i++) {
            View child = layout.getItemAt(i);
            if (!(child instanceof ActionBarMenuSubItem)) {
                child.setBackgroundColor(color);
            }
        }
        return this;
    }

    private void updateItemMinimumWidths() {
        if (minWidth <= 0) {
            return;
        }
        int minimumWidth = AndroidUtilities.dp(minWidth);
        for (int i = 0; i < layout.getItemsCount(); i++) {
            View child = layout.getItemAt(i);
            if (child instanceof ActionBarMenuSubItem) {
                child.setMinimumWidth(minimumWidth);
            }
        }
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
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(AndroidUtilities.dp(6));
        layout.setBackgroundDrawable(background);
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
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.AT_MOST)
        );
        View rootView = container.getRootView();
        if (!(rootView instanceof ViewGroup)) {
            rootView = container;
        }
        rootView.getLocationInWindow(rootLocation);
        scrimView.getLocationInWindow(anchorLocation);
        rootView.getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.isEmpty()) {
            visibleFrame.set(0, 0, rootView.getWidth(), rootView.getHeight());
        } else {
            visibleFrame.offset(-rootLocation[0], -rootLocation[1]);
        }
        int anchorLeft = anchorLocation[0] - rootLocation[0];
        int anchorTop = anchorLocation[1] - rootLocation[1];
        int anchorRight = anchorLeft + scrimView.getMeasuredWidth();
        int anchorBottom = anchorTop + scrimView.getMeasuredHeight();
        int x = anchorRight - (layout.getMeasuredWidth() - layout.getPaddingRight()) + (int) translateX;
        int y = anchorBottom + (int) translateY;
        int bottom = Math.min(visibleFrame.bottom, rootView.getHeight()) - AndroidUtilities.dp(8);
        if (y + layout.getMeasuredHeight() > bottom) {
            y = anchorTop - layout.getMeasuredHeight() + (int) translateY;
        }
        int left = visibleFrame.left + AndroidUtilities.dp(8);
        int right = Math.min(visibleFrame.right, rootView.getWidth()) - AndroidUtilities.dp(8);
        x = Math.max(left, Math.min(x, right - layout.getMeasuredWidth()));
        y = Math.max(visibleFrame.top + AndroidUtilities.dp(8), y);

        if (dimAlpha > 0) {
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
        }

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
        actionBarPopupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, x, y);
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
