// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/MainTabsActivity.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: bridged-adapter=L2, theme/resource-mapping=L2, tabs-visibility-animation=L0
// HOST-WIRING:
//   - Bridges Telegram-derived ViewPagerFixed/MainTabsLayout/GlassTabView to TopicListFragment/ProfileFragment.
//   - Telegram Theme / resource lookups -> NativeDo Theme and R resources.
//   - blur3 background/fade drawables -> local TabsBackgroundDrawable and BottomFadeView.
// ADAPTED:
//   - TABS_COUNT = 2 and positions are Home/Profile only; upstream uses chats/contacts/calls-or-settings/profile.
//   - MAIN_TABS_MIN_CONTENT_WIDTH = 160dp; upstream tab wrapper is sized for a wider dynamic tab set.
// OMITTED:
//   - Account switching, contacts/calls/settings tabs, unread counters, app-update layout, blur3 render-node capture,
//     RLottie icons, and overlay/collapsed browser tab containers - deferred until those milestones exist.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/MainTabsActivity.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java
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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.ActionBar;
import io.github.baba983.nativedo.ui.actionbar.BaseFragment;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;
import io.github.baba983.nativedo.ui.profile.ProfileFragment;
import io.github.baba983.nativedo.ui.topic.TopicListFragment;

public class MainTabsActivity extends ViewPagerActivity implements Theme.ThemeDelegate {

    public static final int TABS_COUNT = 2;
    public static final int POSITION_HOME = 0;
    public static final int POSITION_PROFILE = 1;
    public static final int MAIN_TABS_HEIGHT = 56;
    public static final int MAIN_TABS_MARGIN = 8;
    public static final int MAIN_TABS_HEIGHT_WITH_MARGINS = MAIN_TABS_HEIGHT + MAIN_TABS_MARGIN * 2;
    private static final int MAIN_TABS_MIN_CONTENT_WIDTH = 160;
    private static final int MAIN_TABS_WIDTH_WITH_MARGINS = MAIN_TABS_MIN_CONTENT_WIDTH + (MAIN_TABS_MARGIN + 4) * 2;

    public GlassTabView[] tabs;
    private MainTabsLayout tabsView;
    private FrameLayout tabsViewWrapper;
    private TabsBackgroundDrawable tabsViewBackground;
    private BottomFadeView fadeView;
    private int navigationBarHeight;
    private float tabsVisibilityFactor = 1f;
    private ValueAnimator tabsVisibilityAnimator;

    @Override
    public View createView(Context context) {
        super.createView(context);
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        tabsView = new MainTabsLayout(context);
        tabsView.setClipChildren(false);
        tabsView.setPadding(
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f)
        );
        tabsView.setElevation(AndroidUtilities.dpf2(2f));

        tabs = new GlassTabView[TABS_COUNT];
        tabs[POSITION_HOME] = GlassTabView.createMainTab(context, GlassTabView.TabIcon.HOME, R.string.tab_home);
        tabs[POSITION_PROFILE] = GlassTabView.createMainTab(context, GlassTabView.TabIcon.PROFILE, R.string.tab_profile);
        for (int index = 0; index < tabs.length; index++) {
            final int position = index;
            final GlassTabView tab = tabs[index];
            tab.setOnClickListener(v -> {
                if (viewPager.isManualScrolling() || viewPager.isTouch()) {
                    return;
                }
                if (viewPager.getCurrentPosition() == position) {
                    BaseFragment fragment = getCurrentVisibleFragment();
                    if (fragment instanceof TabFragmentDelegate) {
                        ((TabFragmentDelegate) fragment).onParentScrollToTop();
                    }
                    return;
                }
                selectTab(position, true);
                viewPager.scrollToPosition(position);
            });
            tabsView.addView(tab);
        }
        selectTab(viewPager.getCurrentPosition(), false);

        tabsViewBackground = new TabsBackgroundDrawable();
        tabsView.setBackground(tabsViewBackground);

        fadeView = new BottomFadeView(context);
        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsViewWrapper = new FrameLayout(context);
        tabsViewWrapper.setOnClickListener(v -> {
        });
        tabsViewWrapper.setClipToPadding(false);
        tabsViewWrapper.addView(
                tabsView,
                LayoutHelper.createFrame(MAIN_TABS_WIDTH_WITH_MARGINS, MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
        );
        contentView.addView(tabsViewWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
            navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            checkUiTabsPosition();
            checkUiFadeView();
            return insets;
        });
        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Theme.addThemeDelegate(this);
        checkUiTabsPosition();
        checkUiFadeView();
    }

    @Override
    public void onPause() {
        Theme.removeThemeDelegate(this);
        super.onPause();
    }

    @Override
    public ActionBar getActionBar() {
        return null;
    }

    @Override
    public boolean onBackPressed() {
        // BaseFragment convention: true => allow pop, false => consumed.
        return !handleBackPressed();
    }

    /**
     * Returns true when the back press was consumed by this activity (current
     * fragment handled it, or we navigated back to the Home tab). MainActivity
     * uses this in its OnBackInvoked / OnBackPressed callbacks alongside
     * ActionBarLayout.handleBackPressed(), so the return convention here matches
     * ActionBarLayout (true == consumed).
     */
    public boolean handleBackPressed() {
        BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null && !fragment.onBackPressed()) {
            return true;
        }
        if (viewPager != null && viewPager.getCurrentPosition() != POSITION_HOME) {
            selectTab(POSITION_HOME, true);
            viewPager.scrollToPosition(POSITION_HOME);
            return true;
        }
        return false;
    }

    public void setTabsVisible(boolean visible, boolean animated) {
        float target = visible ? 1f : 0f;
        if (!animated) {
            if (tabsVisibilityAnimator != null) {
                tabsVisibilityAnimator.cancel();
                tabsVisibilityAnimator = null;
            }
            tabsVisibilityFactor = target;
            checkUiTabsPosition();
            checkUiFadeView();
            return;
        }
        if (tabsVisibilityAnimator != null) {
            tabsVisibilityAnimator.cancel();
        }
        tabsVisibilityAnimator = ValueAnimator.ofFloat(tabsVisibilityFactor, target);
        tabsVisibilityAnimator.setDuration(380L);
        tabsVisibilityAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        tabsVisibilityAnimator.addUpdateListener(animation -> {
            tabsVisibilityFactor = (float) animation.getAnimatedValue();
            checkUiTabsPosition();
            checkUiFadeView();
        });
        tabsVisibilityAnimator.start();
    }

    @Override
    protected int getStartPosition() {
        return POSITION_HOME;
    }

    @Override
    protected int getFragmentsCount() {
        return TABS_COUNT;
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        if (position == POSITION_PROFILE) {
            return new ProfileFragment();
        }
        return new TopicListFragment();
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return viewPager != null && viewPager.getCurrentPosition() > POSITION_HOME;
    }

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return viewPager != null && viewPager.getCurrentPosition() < POSITION_PROFILE;
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) {
            selectTab(viewPager.getCurrentPosition(), true);
            setGestureSelectedOverride(0f, false);
        }
    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        boolean isDragByGesture = !manual;
        if (tabsView != null) {
            float position = viewPager.getPositionAnimated();
            setGestureSelectedOverride(position, isDragByGesture);
            if (isDragByGesture) {
                selectTab(Math.round(position), true);
            }
        }
    }

    @Override
    public void didSetNewTheme() {
        if (contentView != null) {
            contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        if (fadeView != null) {
            fadeView.updateColors();
        }
        if (tabs != null) {
            for (GlassTabView tab : tabs) {
                tab.updateColors();
            }
        }
    }

    public void selectTab(int position, boolean animated) {
        for (int index = 0; index < tabs.length; index++) {
            tabs[index].setSelected(index == position, animated);
        }
    }

    public void setGestureSelectedOverride(float animatedPosition, boolean allow) {
        for (int index = 0; index < tabs.length; index++) {
            float visibility = Math.max(0f, 1f - Math.abs(index - animatedPosition));
            tabs[index].setGestureSelectedOverride(visibility, allow);
        }
        tabsView.invalidate();
    }

    private void checkUiTabsPosition() {
        if (tabsViewWrapper == null || tabsView == null) {
            return;
        }
        tabsViewWrapper.setPadding(0, 0, 0, navigationBarHeight);
        float hiddenY = AndroidUtilities.dp(40f);
        float scale = AndroidUtilities.lerp(0.85f, 1f, tabsVisibilityFactor);
        tabsViewWrapper.setTranslationY(AndroidUtilities.lerp(hiddenY, 0f, tabsVisibilityFactor));
        tabsView.setScaleX(scale);
        tabsView.setScaleY(scale);
        tabsView.setAlpha(tabsVisibilityFactor);
        tabsView.setVisibility(tabsVisibilityFactor > 0f ? View.VISIBLE : View.GONE);
        tabsView.setClickable(tabsVisibilityFactor >= 1f);
        tabsView.setEnabled(tabsVisibilityFactor >= 1f);
    }

    private void checkUiFadeView() {
        if (fadeView == null) {
            return;
        }
        int height = navigationBarHeight + AndroidUtilities.dp(MAIN_TABS_HEIGHT_WITH_MARGINS);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fadeView.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            fadeView.setLayoutParams(lp);
        }
        fadeView.setAlpha(tabsVisibilityFactor);
        fadeView.setVisibility(tabsVisibilityFactor > 0f ? View.VISIBLE : View.GONE);
    }

    public interface TabFragmentDelegate {
        void onParentScrollToTop();
    }

    private static class TabsBackgroundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int alpha = 255;
        private int paintAlpha;
        private int shadowAlpha;
        private int highlightAlpha;
        private int strokeAlpha;

        TabsBackgroundDrawable() {
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(0.5f)));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(0.5f)));
            updateColors();
        }

        void updateColors() {
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            shadowPaint.setColor(0xff000000);
            highlightPaint.setColor(0xffffffff);
            strokePaint.setColor(Theme.getColor(Theme.key_divider));
            paintAlpha = Theme.isCurrentThemeDark() ? 188 : 178;
            shadowAlpha = Theme.isCurrentThemeDark() ? 36 : 18;
            highlightAlpha = Theme.isCurrentThemeDark() ? 28 : 82;
            strokeAlpha = Theme.isCurrentThemeDark() ? 58 : 92;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float inset = AndroidUtilities.dpf2(MAIN_TABS_MARGIN - 0.334f);
            rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
            rect.inset(inset, inset);
            float radius = AndroidUtilities.dpf2(MAIN_TABS_HEIGHT / 2f);
            paint.setAlpha(paintAlpha * alpha / 255);
            shadowPaint.setAlpha(shadowAlpha * alpha / 255);
            highlightPaint.setAlpha(highlightAlpha * alpha / 255);
            strokePaint.setAlpha(strokeAlpha * alpha / 255);
            rect.offset(0f, AndroidUtilities.dpf2(0.67f));
            canvas.drawRoundRect(rect, radius, radius, shadowPaint);
            rect.offset(0f, -AndroidUtilities.dpf2(0.67f));
            canvas.drawRoundRect(rect, radius, radius, paint);
            canvas.drawRoundRect(rect, radius, radius, highlightPaint);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            shadowPaint.setColorFilter(colorFilter);
            highlightPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class BottomFadeView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient shader;
        private int lastColor;

        BottomFadeView(Context context) {
            super(context);
            updateColors();
        }

        void updateColors() {
            lastColor = Theme.getColor(Theme.key_windowBackgroundWhite);
            shader = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (getHeight() <= 0) {
                return;
            }
            if (shader == null) {
                shader = new LinearGradient(
                        0f,
                        0f,
                        0f,
                        getHeight(),
                        new int[]{
                                ColorUtils.setAlphaComponent(lastColor, 0),
                                ColorUtils.setAlphaComponent(lastColor, Theme.isCurrentThemeDark() ? 210 : 230),
                        },
                        new float[]{0f, 0.72f},
                        Shader.TileMode.CLAMP
                );
                paint.setShader(shader);
            }
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        }
    }
}
