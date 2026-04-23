/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/BaseFragment.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java
 *
 * NativeDo milestone slice: keeps fragment view, action bar, parent layout, and navigation
 * lifecycle hooks; omits Telegram dialogs, sheets, themes, menus, stories, and arguments.
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public abstract class BaseFragment {

    protected View fragmentView;
    protected INavigationLayout parentLayout;
    protected ActionBar actionBar;

    public View createView(Context context) {
        return null;
    }

    public void setParentLayout(INavigationLayout layout) {
        parentLayout = layout;
        if (actionBar == null && layout instanceof ViewGroup) {
            actionBar = new ActionBar(((ViewGroup) layout).getContext());
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    finishFragment();
                }
            });
        }
    }

    public boolean presentFragment(BaseFragment fragment) {
        return parentLayout != null && parentLayout.presentFragment(fragment);
    }

    public void finishFragment() {
        if (parentLayout != null) {
            if (parentLayout instanceof ActionBarLayout) {
                ((ActionBarLayout) parentLayout).handleBackPressed();
            } else {
                parentLayout.closeLastFragment(true);
            }
        }
    }

    public boolean onBackPressed() {
        return true;
    }

    public boolean canBeginSlide() {
        return true;
    }

    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    public void onBeginSlide() {
    }

    public void onPause() {
    }

    public void onResume() {
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public void setFragmentView(View fragmentView) {
        this.fragmentView = fragmentView;
    }

    public INavigationLayout getParentLayout() {
        return parentLayout;
    }
}
