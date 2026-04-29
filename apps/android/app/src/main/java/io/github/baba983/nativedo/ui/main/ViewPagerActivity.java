// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ViewPagerActivity.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: lifecycle-orchestration=L1, host-fragment-adapter=L2
// HOST-WIRING:
//   - Telegram ActionBar/BaseFragment imports -> NativeDo actionbar package.
//   - ViewPagerFixed.Adapter binds NativeDo BaseFragment views into FrameLayout page containers.
//   - Insets dispatch is kept in the upstream shape so MainTabs and Compose page fragments receive
//     the post-bottom-tabs insets from ActionBarLayout.
// OMITTED:
//   - getThemeDescriptions / theme-description collection - Telegram's animated theme editor is out of scope.
//   - fragmentVisibility factors / system-bar color orchestration - current milestone uses one status-bar style.
//   - aggressive fragment-cache eviction across non-current pages - only Home/Profile are in scope and can stay resident.
//   - setParentLayout / setInPreviewMode / setVisibleDialog forwarding - only current visible fragment forwarding is needed.
//   - onConfigurationChanged forwarding to all cached fragments - same fixed two-tab scope.
//   - drag-reorder tab editing - TabsView-tied and out of scope.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ViewPagerActivity.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ViewPagerActivity.java
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
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.baba983.nativedo.ui.actionbar.ActionBar;
import io.github.baba983.nativedo.ui.actionbar.BaseFragment;
import io.github.baba983.nativedo.ui.components.LayoutHelper;
import io.github.baba983.nativedo.ui.components.ViewPagerFixed;

public abstract class ViewPagerActivity extends BaseFragment {

    protected final SparseArray<FragmentState> fragmentsArr = new SparseArray<>();
    protected FrameLayout contentView;
    protected ViewPagerFixed viewPager;
    private int initialFragmentPosition = -1;
    private boolean resumed;

    protected abstract int getStartPosition();

    protected abstract int getFragmentsCount();

    protected abstract BaseFragment createBaseFragmentAt(int position);

    protected void onViewPagerScrollEnd() {
    }

    protected void onViewPagerTabAnimationUpdate(boolean manual) {
    }

    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context);
    }

    @Override
    public View createView(Context context) {
        contentView = createContentView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onScrollEnd() {
                super.onScrollEnd();
                onViewPagerScrollEnd();
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                super.onItemSelected(currentPage, oldPage, position, oldPosition);
                onPageSelected(position, oldPosition);
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                onViewPagerTabAnimationUpdate(manual);
            }

            @Override
            protected boolean canScrollBackward(MotionEvent e) {
                return ViewPagerActivity.this.canScrollBackward(e);
            }

            @Override
            protected boolean canScrollForward(MotionEvent e) {
                return ViewPagerActivity.this.canScrollForward(e);
            }

            @Override
            protected long getManualScrollDuration() {
                return 320L;
            }
        };

        if (initialFragmentPosition == -1) {
            initialFragmentPosition = getStartPosition();
        }
        viewPager.setPosition(initialFragmentPosition);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return getFragmentsCount();
            }

            @Override
            public View createView(int viewType) {
                return new FrameLayout(context);
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                FragmentState state = fragmentsArr.get(position);
                if (state == null) {
                    state = new FragmentState(createBaseFragmentAt(position));
                    fragmentsArr.put(position, state);
                }

                BaseFragment fragment = state.fragment;
                fragment.setParentLayout(getParentLayout());
                if (fragment.getFragmentView() == null) {
                    fragment.setFragmentView(fragment.createView(context));
                }

                FrameLayout container = (FrameLayout) view;
                container.removeAllViews();

                View fragmentView = fragment.getFragmentView();
                removeFromParent(fragmentView);

                int actionBarHeight = 0;
                ActionBar actionBar = fragment.getActionBar();
                if (actionBar != null && actionBar.shouldAddToContainer()) {
                    removeFromParent(actionBar);
                    actionBarHeight = actionBar.getCurrentActionBarHeight();
                }

                FrameLayout.LayoutParams contentLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
                contentLayoutParams.topMargin = actionBarHeight;
                container.addView(fragmentView, contentLayoutParams);
                if (actionBar != null && actionBar.shouldAddToContainer()) {
                    container.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, actionBarHeight, Gravity.TOP | Gravity.START));
                }
                ViewCompat.requestApplyInsets(container);
            }
        });

        contentView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = contentView;
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    protected WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets) {
        for (int i = 0, count = fragmentsArr.size(); i < count; i++) {
            FragmentState state = fragmentsArr.valueAt(i);
            if (state != null) {
                View fragmentView = state.fragment.getFragmentView();
                if (fragmentView != null) {
                    ViewCompat.dispatchApplyWindowInsets(fragmentView, insets);
                }
            }
        }
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onResume();
        }
    }

    @Override
    public void onPause() {
        BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onPause();
        }
        resumed = false;
        super.onPause();
    }

    protected BaseFragment getCurrentVisibleFragment() {
        if (viewPager == null) {
            return null;
        }
        FragmentState state = fragmentsArr.get(viewPager.getCurrentPosition());
        return state == null ? null : state.fragment;
    }

    protected void putFragmentAtPosition(int position, BaseFragment fragment) {
        fragmentsArr.put(position, new FragmentState(fragment));
    }

    protected boolean canScrollBackward(MotionEvent ev) {
        return true;
    }

    protected boolean canScrollForward(MotionEvent ev) {
        return true;
    }

    private void onPageSelected(int position, int oldPosition) {
        if (!resumed || oldPosition == position) {
            return;
        }
        FragmentState oldState = fragmentsArr.get(oldPosition);
        if (oldState != null) {
            oldState.fragment.onPause();
        }
        FragmentState newState = fragmentsArr.get(position);
        if (newState != null) {
            newState.fragment.onResume();
        }
    }

    private static void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    protected static class FragmentState {
        final BaseFragment fragment;

        FragmentState(BaseFragment fragment) {
            this.fragment = fragment;
        }
    }
}
