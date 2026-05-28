/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBar.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java
 *
 * NativeDo milestone slice: keeps title, back button, status-bar height, menu creation,
 * menu click callback behavior, and theme color wiring; omits Telegram search, action
 * mode, and overlays.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBar extends FrameLayout {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {
        }
    }

    private final ImageView backButtonImageView;
    private final SimpleTextView titleTextView;
    public ActionBarMenu menu;
    public int itemsColor;
    public int itemsBackgroundColor;
    private int titleColor;
    private ActionBarMenuOnItemClick actionBarMenuOnItemClick;
    private boolean occupyStatusBar = true;
    private boolean castShadows = SharedConfig.drawActionBarShadow;
    private boolean addToContainer = true;
    private RecyclerView adaptiveBackgroundListView;
    private final RecyclerView.OnScrollListener adaptiveBackgroundScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateAdaptiveBackground();
        }
    };

    public ActionBar(Context context) {
        super(context);
        setWillNotDraw(false);

        backButtonImageView = new ImageView(context);
        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
        backButtonImageView.setContentDescription(context.getString(R.string.action_back));
        backButtonImageView.setVisibility(GONE);
        backButtonImageView.setOnClickListener(v -> {
            if (actionBarMenuOnItemClick != null) {
                actionBarMenuOnItemClick.onItemClick(-1);
            }
        });
        addView(backButtonImageView, LayoutHelper.createFrame(54, 56, Gravity.START | Gravity.TOP));

        titleTextView = new SimpleTextView(context);
        titleTextView.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.START));
        applyTheme();
    }

    public void setTitle(CharSequence value) {
        titleTextView.setText(value);
    }

    public CharSequence getTitle() {
        return titleTextView.getText();
    }

    public void setBackButtonDrawable(Drawable drawable) {
        backButtonImageView.setImageDrawable(drawable);
        backButtonImageView.setVisibility(drawable == null ? GONE : VISIBLE);
        applyBackButtonColor(drawable);
    }

    public void setBackButtonImage(int resId) {
        setBackButtonDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public ActionBarMenuOnItemClick getActionBarMenuOnItemClick() {
        return actionBarMenuOnItemClick;
    }

    protected ImageView getBackButtonImageView() {
        return backButtonImageView;
    }

    public ActionBarMenu createMenu() {
        if (menu != null) {
            return menu;
        }
        menu = new ActionBarMenu(getContext(), this);
        addView(menu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        return menu;
    }

    public void applyTheme() {
        itemsColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
        itemsBackgroundColor = Theme.getColor(Theme.key_actionBarDefaultSelector);
        titleColor = Theme.getColor(Theme.key_actionBarDefaultTitle);
        titleTextView.setTextColor(titleColor);
        backButtonImageView.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor));
        applyBackButtonColor(backButtonImageView.getDrawable());
        if (menu != null) {
            menu.updateItemsBackgroundColor();
            menu.updateItemsColor();
            menu.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), true);
            menu.setPopupItemsSelectorColor(Theme.getColor(Theme.key_listSelector));
            menu.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        }
        updateAdaptiveBackground();
    }

    public void setItemsColor(int color, boolean isActionMode) {
        itemsColor = color;
        applyBackButtonColor(backButtonImageView.getDrawable());
        if (menu != null) {
            menu.updateItemsColor();
        }
        invalidate();
    }

    public void setItemsBackgroundColor(int color, boolean isActionMode) {
        itemsBackgroundColor = color;
        backButtonImageView.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor));
        if (menu != null) {
            menu.updateItemsBackgroundColor();
        }
        invalidate();
    }

    public void setTitleColor(int color) {
        titleColor = color;
        titleTextView.setTextColor(color);
        invalidate();
    }

    public void setAdaptiveBackground(@Nullable RecyclerView listView) {
        if (adaptiveBackgroundListView == listView) {
            return;
        }
        if (adaptiveBackgroundListView != null) {
            adaptiveBackgroundListView.removeOnScrollListener(adaptiveBackgroundScrollListener);
        }
        adaptiveBackgroundListView = listView;
        if (adaptiveBackgroundListView != null) {
            adaptiveBackgroundListView.addOnScrollListener(adaptiveBackgroundScrollListener);
        }
        updateAdaptiveBackground();
    }

    private void updateAdaptiveBackground() {
        if (adaptiveBackgroundListView == null) {
            setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            setCastShadows(SharedConfig.drawActionBarShadow);
            return;
        }
        int scrollOffset = adaptiveBackgroundListView.computeVerticalScrollOffset();
        float progress = Math.min(1f, scrollOffset / (float) AndroidUtilities.dp(24));
        setBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_actionBarDefault), (int) (255 * progress)));
        setCastShadows(progress > 0.98f && SharedConfig.drawActionBarShadow);
    }

    public boolean shouldAddToContainer() {
        return addToContainer;
    }

    public void setAddToContainer(boolean value) {
        addToContainer = value;
    }

    public void setCastShadows(boolean value) {
        if (castShadows != value) {
            castShadows = value;
            invalidate();
        }
    }

    public void setOccupyStatusBar(boolean value) {
        if (occupyStatusBar != value) {
            occupyStatusBar = value;
            requestLayout();
        }
    }

    private boolean clipContent;

    public void setClipContent(boolean value) {
        clipContent = value;
    }

    public int getCurrentActionBarHeight() {
        return AndroidUtilities.dp(56) + (occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
    }

    public static int getCurrentActionBarHeightStatic() {
        if (AndroidUtilities.isTablet()) {
            return AndroidUtilities.dp(64);
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return AndroidUtilities.dp(48);
        } else {
            return AndroidUtilities.dp(56);
        }
    }

    public void setHeight(int height) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = AndroidUtilities.dp(56);
        int totalHeight = topInset + barHeight;
        setMeasuredDimension(width, totalHeight);

        int menuWidth = 0;
        if (menu != null && menu.getVisibility() != GONE) {
            menu.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
            );
            menuWidth = menu.getMeasuredWidth();
        }

        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.measure(
            MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        titleTextView.measure(
            MeasureSpec.makeMeasureSpec(Math.max(0, width - titleLeft - menuWidth - AndroidUtilities.dp(16)), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == backButtonImageView || child == titleTextView || child == menu || child.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(
                child,
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                0,
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY),
                0
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = AndroidUtilities.dp(56);
        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.layout(0, topInset, buttonSize, topInset + barHeight);

        if (menu != null && menu.getVisibility() != GONE) {
            int menuLeft = getMeasuredWidth() - menu.getMeasuredWidth();
            menu.layout(menuLeft, topInset, getMeasuredWidth(), topInset + barHeight);
        }

        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        titleTextView.layout(
            titleLeft,
            topInset,
            titleLeft + titleTextView.getMeasuredWidth(),
            topInset + barHeight
        );
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == backButtonImageView || child == titleTextView || child == menu || child.getVisibility() == GONE) {
                continue;
            }
            child.layout(0, topInset, getMeasuredWidth(), topInset + barHeight);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (castShadows) {
            canvas.save();
            canvas.clipRect(0, getHeight() - 1, getWidth(), getHeight());
            canvas.drawColor(Color.parseColor("#1F000000"));
            canvas.restore();
        }
    }

    private void applyBackButtonColor(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof BackDrawable) {
            ((BackDrawable) drawable).setColor(itemsColor);
            ((BackDrawable) drawable).setRotatedColor(itemsColor);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.MULTIPLY));
        }
    }
}
