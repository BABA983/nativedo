/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBar.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java
 *
 * NativeDo milestone slice: keeps title, back button, status-bar height, and menu click
 * callback behavior; omits Telegram menus, search, action mode, themes, and overlays.
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
package io.github.baba983.nativedo.ui.actionbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.core.SharedConfig;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class ActionBar extends FrameLayout {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {
        }
    }

    private final ImageView backButtonImageView;
    private final SimpleTextView titleTextView;
    private ActionBarMenuOnItemClick actionBarMenuOnItemClick;
    private boolean occupyStatusBar = true;

    public ActionBar(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setWillNotDraw(false);

        backButtonImageView = new ImageView(context);
        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
        backButtonImageView.setBackground(AppCompatResources.getDrawable(context, android.R.drawable.list_selector_background));
        backButtonImageView.setContentDescription(context.getString(R.string.action_back));
        backButtonImageView.setVisibility(GONE);
        backButtonImageView.setOnClickListener(v -> {
            if (actionBarMenuOnItemClick != null) {
                actionBarMenuOnItemClick.onItemClick(-1);
            }
        });
        addView(backButtonImageView, LayoutHelper.createFrame(54, 56, Gravity.START | Gravity.TOP));

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(Color.parseColor("#111827"));
        titleTextView.setTypeface(titleTextView.getTypeface(), android.graphics.Typeface.BOLD);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.START));
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
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public boolean shouldAddToContainer() {
        return true;
    }

    public void setOccupyStatusBar(boolean value) {
        if (occupyStatusBar != value) {
            occupyStatusBar = value;
            requestLayout();
        }
    }

    public int getCurrentActionBarHeight() {
        return AndroidUtilities.dp(56) + (occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = AndroidUtilities.dp(56);
        int totalHeight = topInset + barHeight;
        setMeasuredDimension(width, totalHeight);

        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.measure(
            MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        titleTextView.measure(
            MeasureSpec.makeMeasureSpec(Math.max(0, width - titleLeft - AndroidUtilities.dp(16)), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = AndroidUtilities.dp(56);
        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.layout(0, topInset, buttonSize, topInset + barHeight);

        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        titleTextView.layout(
            titleLeft,
            topInset,
            titleLeft + titleTextView.getMeasuredWidth(),
            topInset + barHeight
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (SharedConfig.drawActionBarShadow) {
            canvas.drawColor(Color.TRANSPARENT);
            canvas.save();
            canvas.clipRect(0, getHeight() - 1, getWidth(), getHeight());
            canvas.drawColor(Color.parseColor("#1F000000"));
            canvas.restore();
        }
    }
}
