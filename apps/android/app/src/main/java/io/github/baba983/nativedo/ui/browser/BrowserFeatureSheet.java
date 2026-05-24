/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's browser subfeature sheet entry points.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import org.telegram.ui.ActionBar.BaseFragment;

final class BrowserFeatureSheet {

    interface UrlLoader {
        void load(String url);
    }

    private BrowserFeatureSheet() {
    }

    static void showHistory(BaseFragment hostFragment, BrowserListFragment.Mode mode, UrlLoader urlLoader) {
        if (hostFragment == null || hostFragment.getContext() == null) {
            return;
        }
        final BrowserListFragment[] fragment = new BrowserListFragment[1];
        fragment[0] = new BrowserListFragment(mode, entry -> {
            if (fragment[0] != null) {
                fragment[0].finishFragment();
            }
            if (entry != null && urlLoader != null) {
                urlLoader.load(entry.url);
            }
        });
        hostFragment.showAsSheet(fragment[0], transitionFromRightParams(null));
    }

    static void showSettings(BaseFragment hostFragment, UrlLoader urlLoader, Runnable onDismiss) {
        if (hostFragment == null || hostFragment.getContext() == null) {
            return;
        }
        hostFragment.showAsSheet(new BrowserSettingsFragment(urlLoader), transitionFromRightParams(onDismiss));
    }

    private static BaseFragment.BottomSheetParams transitionFromRightParams(Runnable onDismiss) {
        BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
        params.transitionFromLeft = true;
        params.onDismiss = onDismiss;
        return params;
    }
}
