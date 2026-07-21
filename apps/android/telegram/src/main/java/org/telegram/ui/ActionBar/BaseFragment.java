/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/BaseFragment.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java
 *
 * NativeDo milestone slice: keeps fragment view, action bar, parent layout, dialog ownership,
 * attached sheets, and navigation lifecycle hooks; omits Telegram menus, stories, and arguments.
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
import android.app.Activity;
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
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ArticleViewer;

public abstract class BaseFragment implements ViewModelStoreOwner {

    protected View fragmentView;
    protected View photoViewerScaleView;
    protected INavigationLayout parentLayout;
    protected ActionBar actionBar;
    protected int currentAccount = UserConfig.selectedAccount;
    public Dialog visibleDialog;
    /** Upstream-shaped request/owner id used by UniversalRecyclerView/UniversalAdapter. */
    protected int classGuid = 0;
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

        default org.telegram.ui.Components.BulletinFactory getBulletinFactory() {
            return null;
        }

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

    public boolean onFragmentCreate() {
        return true;
    }

    public void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    onRemoveFromParent();
                    parent.removeViewInLayout(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            fragmentView = null;
        }
        photoViewerScaleView = null;
        if (actionBar != null) {
            ViewGroup parent = (ViewGroup) actionBar.getParent();
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            actionBar = null;
        }
        clearSheets();
        parentLayout = null;
    }

    public void onRemoveFromParent() {
        if (sheetsStack == null || sheetsStack.isEmpty()) {
            return;
        }
        updateSheetsVisibility();
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

    public boolean drawEdgeNavigationBar() {
        return true;
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
        dismissCurrentDialog();
    }

    public void onPause() {
        if (visibleDialog != null && visibleDialog.isShowing() && dismissDialogOnPause(visibleDialog)) {
            dismissCurrentDialog();
        }
    }

    public void onResume() {
    }

    public void onFragmentDestroy() {
        dismissCurrentDialog();
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

    public Activity getParentActivity() {
        Context context = getContext();
        return context instanceof Activity ? (Activity) context : null;
    }

    public int getClassGuid() {
        return classGuid;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public ViewGroup getLayoutContainer() {
        return parentLayout instanceof ViewGroup ? (ViewGroup) parentLayout : null;
    }

    public ViewGroup getBulletinLayoutContainer() {
        if (fragmentView != null && fragmentView.getParent() instanceof ViewGroup) {
            return (ViewGroup) fragmentView.getParent();
        }
        return getLayoutContainer();
    }

    public Theme.ResourcesProvider getResourceProvider() {
        return null;
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, getResourceProvider());
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

    public ArticleViewer createArticleViewer(boolean forceRecreate) {
        if (sheetsStack == null) {
            sheetsStack = new ArrayList<>();
        }
        if (!forceRecreate) {
            AttachedSheet lastSheet = getLastSheet();
            if (lastSheet instanceof ArticleViewer.Sheet && lastSheet.isShown()) {
                return ((ArticleViewer.Sheet) lastSheet).getArticleViewer();
            }
            if (parentLayout instanceof ActionBarLayout) {
                BaseFragment lastFragment = ((ActionBarLayout) parentLayout).getLastFragment();
                if (lastFragment != null && lastFragment != this) {
                    AttachedSheet sheet = lastFragment.getLastSheet();
                    if (sheet instanceof ArticleViewer.Sheet && sheet.isShown()) {
                        return ((ArticleViewer.Sheet) sheet).getArticleViewer();
                    }
                }
            }
        }
        ArticleViewer articleViewer = ArticleViewer.makeSheet(this);
        addSheet(articleViewer.sheet);
        BottomSheetTabDialog.checkSheet(articleViewer.sheet);
        return articleViewer;
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

    public Dialog showDialog(Dialog dialog) {
        return showDialog(dialog, null);
    }

    public Dialog showDialog(Dialog dialog, final Dialog.OnDismissListener onDismissListener) {
        if (dialog == null) {
            return null;
        }
        if (sheetsStack != null) {
            for (int i = sheetsStack.size() - 1; i >= 0; i--) {
                AttachedSheet sheet = sheetsStack.get(i);
                if (sheet.isShown() && sheet.showDialog(dialog)) {
                    return dialog;
                }
            }
        }
        dismissCurrentDialog();
        visibleDialog = dialog;
        visibleDialog.setCanceledOnTouchOutside(true);
        visibleDialog.setOnDismissListener(dismissed -> {
            if (onDismissListener != null) {
                onDismissListener.onDismiss(dismissed);
            }
            onDialogDismiss((Dialog) dismissed);
            if (dismissed == visibleDialog) {
                visibleDialog = null;
            }
        });
        visibleDialog.show();
        return visibleDialog;
    }

    public void dismissCurrentDialog() {
        Dialog dialog = visibleDialog;
        visibleDialog = null;
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    public boolean dismissDialogOnPause(Dialog dialog) {
        return true;
    }

    protected void onDialogDismiss(Dialog dialog) {
    }

    public Dialog getVisibleDialog() {
        return visibleDialog;
    }

    public void setVisibleDialog(Dialog dialog) {
        visibleDialog = dialog;
    }

    public org.telegram.messenger.AccountInstance getAccountInstance() {
        return org.telegram.messenger.AccountInstance.getInstance(currentAccount);
    }

    public org.telegram.messenger.MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    public org.telegram.messenger.NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    /** Pin edge-to-edge hook; subclasses override. */
    public void onInsets(int left, int top, int right, int bottom) {
    }

    /**
     * Upstream declarative theme recolor list. Default empty; fragments override
     * so ActionBarLayout / ThemeDescription refresh can re-tint without manual
     * child walks. Currently used by host call sites that opt in.
     */
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

}
