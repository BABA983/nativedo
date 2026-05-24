// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/INavigationLayout.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: navigation-contract=L1
// ADAPTED:
//   - Keeps only present, stack, close, and NavigationParams API used by this shell.
//   - Keeps Telegram's bottom-tabs height surface so NativeDo tab hosts can follow browser-tab motion.
// OMITTED:
//   - Telegram delegate, preview menu, sheet navigation, theme animation, and broader launch surfaces.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/INavigationLayout.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/INavigationLayout.java
 *
 * NativeDo milestone slice: keeps NavigationParams-backed present, stack, and close API
 * used by this shell; omits Telegram delegate, preview menu, and sheet navigation surface.
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

import java.util.List;

public interface INavigationLayout {

    boolean presentFragment(NavigationParams params);

    boolean addFragmentToStack(BaseFragment fragment, int position);

    void closeLastFragment(boolean animated, boolean forceNoAnimation);

    List<BaseFragment> getFragmentStack();

    default boolean addFragmentToStack(BaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    default boolean presentFragment(BaseFragment fragment) {
        return presentFragment(new NavigationParams(fragment));
    }

    default void closeLastFragment(boolean animated) {
        closeLastFragment(animated, false);
    }

    default int getBottomTabsHeight(boolean animated) {
        return 0;
    }

    class NavigationParams {
        public BaseFragment fragment;
        public boolean removeLast;
        public boolean noAnimation;
        public boolean checkPresentFromDelegate = true;
        public boolean preview;
        public boolean needDelayWithoutAnimation;

        public boolean isFromDelay;
        public boolean delayDone;

        public NavigationParams(BaseFragment fragment) {
            this.fragment = fragment;
        }

        public NavigationParams setRemoveLast(boolean removeLast) {
            this.removeLast = removeLast;
            return this;
        }

        public NavigationParams setNoAnimation(boolean noAnimation) {
            this.noAnimation = noAnimation;
            return this;
        }

        public NavigationParams setCheckPresentFromDelegate(boolean checkPresentFromDelegate) {
            this.checkPresentFromDelegate = checkPresentFromDelegate;
            return this;
        }

        public NavigationParams setPreview(boolean preview) {
            this.preview = preview;
            return this;
        }

        public NavigationParams setNeedDelayWithoutAnimation(boolean needDelayWithoutAnimation) {
            this.needDelayWithoutAnimation = needDelayWithoutAnimation;
            return this;
        }
    }
}
