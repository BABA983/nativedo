// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/ActionBar/BaseFragment.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: lifecycle-orchestration, navigation-contract
// HOST-WIRING:
//   - NativeDo fragments use this as the bridge between View-system navigation and Compose content.
//   - Implements AndroidX ViewModelStoreOwner so NativeDo feature pages can keep state in BaseFragment.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - Automatically creates a minimal NativeDo ActionBar when attached to an INavigationLayout.
//   - Keeps the AttachedSheet stack surface needed by NativeDo browser sheets.
//   - Exposes showAsSheet(...) for Telegram browser subfeatures through NativeDo's attached-sheet host.
//   - Keeps Telegram's edge-to-edge method surface so ActionBarLayout/ViewPagerActivity can route insets.
//   - Adds onFragmentDestroy() to clear NativeDo ViewModels and attached sheets when local fragments leave the stack.
// OMITTED:
//   - Telegram dialogs, themes, menus, stories, arguments, and account/activity plumbing.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/BaseFragment.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java
 *
 * NativeDo milestone slice: keeps fragment view, action bar, parent layout, attached sheets,
 * and navigation lifecycle hooks; omits Telegram dialogs, themes, menus, stories, and arguments.
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

import android.app.Dialog;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;

public abstract class BaseFragment implements ViewModelStoreOwner {

    protected View fragmentView;
    protected View photoViewerScaleView;
    protected INavigationLayout parentLayout;
    protected ActionBar actionBar;
    public ArrayList<AttachedSheet> sheetsStack;
    private final ViewModelStore viewModelStore = new ViewModelStore();

    public interface AttachedSheet {
        View getWindowView();

        boolean isShown();

        void dismiss();

        default void dismiss(boolean tabs) {
            dismiss();
        }

        void release();

        boolean isFullyVisible();

        boolean attachedToParent();

        boolean onAttachedBackPressed();

        boolean showDialog(Dialog dialog);

        void setKeyboardHeightFromParent(int keyboardHeight);

        boolean isAttachedLightStatusBar();

        int getNavigationBarColor(int color);

        void setOnDismissListener(Runnable onDismiss);

        default void setLastVisible(boolean lastVisible) {
        }
    }

    public interface AttachedSheetWindow {
    }

    public static class BottomSheetParams {
        public boolean transitionFromLeft;
        public Runnable onDismiss;
        public Runnable onPreFinished;
        public Runnable onOpenAnimationFinished;
    }

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
                ActionBarLayout layout = (ActionBarLayout) parentLayout;
                if (!layout.handleBackPressed()) {
                    layout.dismissRootSheet();
                }
            } else {
                parentLayout.closeLastFragment(true);
            }
        }
    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment) {
        return showAsSheet(fragment, null);
    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params) {
        if (fragment == null || getContext() == null) {
            return null;
        }
        ActionBarSheet sheet = new ActionBarSheet(this, fragment, params);
        if (!sheet.show()) {
            return null;
        }
        return new INavigationLayout[] { sheet.getActionBarLayout() };
    }

    public boolean onBackPressed() {
        AttachedSheet sheet = getLastSheet();
        if (sheet != null && sheet.isShown()) {
            return !sheet.onAttachedBackPressed();
        }
        return true;
    }

    public boolean canBeginSlide() {
        return true;
    }

    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    public boolean isSupportEdgeToEdge() {
        return false;
    }

    public boolean isLightStatusBar() {
        if (sheetsStack != null) {
            for (int i = sheetsStack.size() - 1; i >= 0; i--) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet.attachedToParent() && sheet.isShown()) {
                    return sheet.isAttachedLightStatusBar();
                }
            }
        }
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault)) > .721f;
    }

    public int getNavigationBarColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundGray);
        if (sheetsStack != null) {
            for (int i = 0; i < sheetsStack.size(); i++) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet.attachedToParent()) {
                    color = sheet.getNavigationBarColor(color);
                }
            }
        }
        return color;
    }

    public WindowInsetsCompat onInsetsInternal(View view, WindowInsetsCompat insets) {
        return insets;
    }

    public void onBeginSlide() {
    }

    public void onPause() {
    }

    public void onResume() {
    }

    public void onFragmentDestroy() {
        clearSheets();
        viewModelStore.clear();
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return viewModelStore;
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public View getPhotoViewerScaleView() {
        return photoViewerScaleView != null ? photoViewerScaleView : fragmentView;
    }

    public void setPhotoViewerScaleView(View view) {
        photoViewerScaleView = view;
    }

    public void setFragmentView(View fragmentView) {
        this.fragmentView = fragmentView;
    }

    public INavigationLayout getParentLayout() {
        return parentLayout;
    }

    public Context getContext() {
        if (parentLayout instanceof ViewGroup) {
            return ((ViewGroup) parentLayout).getContext();
        }
        return fragmentView == null ? null : fragmentView.getContext();
    }

    public AttachedSheet getLastSheet() {
        if (sheetsStack == null || sheetsStack.isEmpty()) {
            return null;
        }
        for (int i = sheetsStack.size() - 1; i >= 0; i--) {
            AttachedSheet sheet = sheetsStack.get(i);
            if (sheet.isShown()) {
                return sheet;
            }
        }
        return null;
    }

    public boolean hasSheet() {
        return sheetsStack != null && !sheetsStack.isEmpty();
    }

    public boolean hasShownSheet() {
        return getLastSheet() != null;
    }

    public boolean hasShownFullyVisibleSheet() {
        if (sheetsStack == null || sheetsStack.isEmpty()) {
            return false;
        }
        for (int i = sheetsStack.size() - 1; i >= 0; i--) {
            AttachedSheet sheet = sheetsStack.get(i);
            if (sheet.isShown() && sheet.isFullyVisible()) {
                return true;
            }
        }
        return false;
    }

    public void addSheet(AttachedSheet sheet) {
        if (sheet == null) {
            return;
        }
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        sheetsStack.add(sheet);
        updateSheetsVisibility();
    }

    public void removeSheet(AttachedSheet sheet) {
        if (sheetsStack == null) {
            return;
        }
        sheetsStack.remove(sheet);
        updateSheetsVisibility();
    }

    public void clearSheets() {
        if (sheetsStack == null || sheetsStack.isEmpty()) {
            return;
        }
        for (int i = sheetsStack.size() - 1; i >= 0; i--) {
            sheetsStack.get(i).dismiss(true);
        }
        sheetsStack.clear();
    }

    public void updateSheetsVisibility() {
        if (sheetsStack == null) {
            return;
        }
        for (int i = 0; i < sheetsStack.size(); i++) {
            AttachedSheet sheet = sheetsStack.get(i);
            sheet.setLastVisible(i == sheetsStack.size() - 1 && sheet.isShown());
        }
        notifyParentSystemBarsChanged();
    }

    public void notifyParentSystemBarsChanged() {
        if (parentLayout instanceof ActionBarLayout) {
            ((ActionBarLayout) parentLayout).updateSystemBars();
        }
    }
}
