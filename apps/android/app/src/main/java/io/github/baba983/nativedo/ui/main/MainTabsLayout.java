// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/MainTabsLayout.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: custom-layout=L1
// HOST-WIRING:
//   - AnimatedLinearLayout -> LinearLayout; NativeDo does not use ListAnimator visibility holders.
//   - isViewVisible(...) -> child.getVisibility() != GONE.
// ADAPTED:
//   - minTotalWidthForTabs uses 160dp instead of upstream 320dp because NativeDo currently has fixed Home/Profile tabs.
//   - Zero-visible-child guard is kept for this standalone LinearLayout host before tabs are attached.
// OMITTED:
//   - AnimatedLinearLayout / ListAnimator animated add/remove tab set - fixed Home/Profile tabs do not add/remove at runtime.
//   - tabsLeftPos / calculateTotalSizesAfterMeasure bookkeeping - tied to the omitted AnimatedLinearLayout host.
//   - Tab-add-from-overflow popup wiring - overflow tabs are out of scope until multi-tab editing ships.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/MainTabsLayout.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/MainTabsLayout.java
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
package io.github.baba983.nativedo.ui.main;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import io.github.baba983.nativedo.core.AndroidUtilities;

public class MainTabsLayout extends LinearLayout {

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;
    private int visibleChildCount;

    public MainTabsLayout(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        measureTabTexts();

        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = Math.min(AndroidUtilities.dp(160), maxTotalWidthForTabs);
        final int tabPadding = AndroidUtilities.dp(16);
        if (visibleChildCount == 0) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), height);
            return;
        }
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / visibleChildCount) - tabPadding * 2;

        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, n = getChildCount(); a < n; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() == GONE) {
                tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }
            if (tabsTextWidth[a] > maxTabTextWidthIfEq) {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + AndroidUtilities.dp(13) * 2;
            } else {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + AndroidUtilities.dp(16) * 2;
            }
            tabsWeight[a] = tabsTextWidthWithMargin[a] > maxTabTextWidthIfEq + AndroidUtilities.dp(16) * 2 ? 0 : 1;
            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (visibleChildCount > 0 && totalWeight == 0) {
            for (int a = 0, n = getChildCount(); a < n; a++) {
                tabsWeight[a] = getChildAt(a).getVisibility() != GONE ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, n = getChildCount(); a < n; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs && totalWeight > 0) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;
            for (int a = 0, n = getChildCount(); a < n; a++) {
                tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
            }
        }

        int measuredWidth = getPaddingLeft() + getPaddingRight();
        for (int a = 0, n = getChildCount(); a < n; a++) {
            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            measuredWidth += tabsWidth[a];
        }

        setMeasuredDimension(measuredWidth, height);
        for (int a = 0, n = getChildCount(); a < n; a++) {
            getChildAt(a).measure(
                    MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        for (int a = 0, n = getChildCount(); a < n; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setVisualWidth(child.getMeasuredWidth());
            }
            childLeft += child.getMeasuredWidth();
        }
    }

    private void measureTabTexts() {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsWidth = new int[childCount];
        }

        visibleChildCount = 0;
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() == GONE) {
                tabsTextWidth[a] = -1;
                continue;
            }
            tabsTextWidth[a] = child instanceof Tab ? ((Tab) child).measureTextWidth() : 0;
            visibleChildCount++;
        }
    }

    public interface Tab {
        float measureTextWidth();
    }
}
