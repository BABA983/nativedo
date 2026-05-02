// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/ArticleViewer.java; org/telegram/ui/ActionBar/BaseFragment.java; org/telegram/ui/web/HistoryFragment.java; org/telegram/ui/web/BookmarksFragment.java; org/telegram/ui/web/WebBrowserSettings.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses NativeDo BaseFragment.showAsSheet(...) to mirror Telegram's browser subfeature entry points.
// ADAPTED-BACKEND:
//   - NativeDo URL loading callbacks replace Telegram ArticleViewer page loaders.
// OMITTED:
//   - Telegram Saved Messages bookmark navigation and full BottomSheet Dialog internals.
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

import io.github.baba983.nativedo.ui.actionbar.BaseFragment;

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
