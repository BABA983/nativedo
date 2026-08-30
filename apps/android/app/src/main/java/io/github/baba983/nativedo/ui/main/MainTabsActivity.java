/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of org/telegram/ui/MainTabsActivity.java.
 */
package io.github.baba983.nativedo.ui.main;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowInsetsCompat;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import io.github.baba983.nativedo.ui.profile.ProfileFragment;
import io.github.baba983.nativedo.ui.settings.SettingsFragment;
import io.github.baba983.nativedo.ui.topic.TopicListFragment;

public class MainTabsActivity extends ViewPagerActivity implements MainTabsController {

    public static final int TABS_COUNT = 3;
    public static final int POSITION_HOME = 0;
    public static final int POSITION_SETTINGS = 1;
    public static final int POSITION_PROFILE = 2;
    public static final int MAIN_TABS_HEIGHT = 56;
    public static final int MAIN_TABS_MARGIN = 8;
    public static final int MAIN_TABS_HEIGHT_WITH_MARGINS = MAIN_TABS_HEIGHT + MAIN_TABS_MARGIN * 2;
    // Telegram keeps the main glass pill at a stable width across the four-tab
    // and reduced-tab variants. Keep that width for NativeDo's three roots too;
    // the visible tab count must not make the chrome collapse.
    static final int MAIN_TABS_MIN_CONTENT_WIDTH = 320;
    static final int MAIN_TABS_MAX_WIDTH = 328 + MAIN_TABS_MARGIN * 2;

    public GlassTabView[] tabs;
    private MainTabsLayout tabsView;
    private FrameLayout tabsViewWrapper;
    private BlurredBackgroundDrawable tabsViewBackground;
    private View fadeView;
    private ViewPositionWatcher blurViewPositionWatcher;
    private final BlurredBackgroundSourceColor blurSourceColor;
    private final @Nullable BlurredBackgroundSourceRenderNode blurSourceTabs;
    private int navigationBarHeight;
    private float tabsVisibilityFactor = 1f;
    private ValueAnimator tabsVisibilityAnimator;

    private boolean hasProfileAvatar;
    private String profileAvatarUrl;
    private String profileAvatarFilter;
    private String profileAvatarName;

    public MainTabsActivity() {
        blurSourceColor = new BlurredBackgroundSourceColor();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurSourceTabs = new BlurredBackgroundSourceRenderNode(blurSourceColor);
            blurSourceTabs.setupRenderer(new RenderNodeWithHash.Renderer() {
                @Override
                public void renderNodeUpdateDisplayList(Canvas canvas) {
                    canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    if (viewPager == null) {
                        return;
                    }
                    canvas.save();
                    canvas.translate(viewPager.getX(), viewPager.getY());
                    viewPager.draw(canvas);
                    canvas.restore();
                }
            });
        } else {
            blurSourceTabs = null;
        }
    }

    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkUiTabsPosition();
                checkUiFadeView();
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                blur3InvalidateBlur();
            }
        };
    }

    @Override
    public View createView(Context context) {
        super.createView(context);
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        tabsView = new MainTabsLayout(context);
        tabsView.setClipChildren(false);
        tabsView.setPadding(
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f),
                AndroidUtilities.dp(MAIN_TABS_MARGIN + 4f)
        );
        tabsView.setMaxWidth(AndroidUtilities.dp(MAIN_TABS_MAX_WIDTH));

        tabs = new GlassTabView[TABS_COUNT];
        tabs[POSITION_HOME] = GlassTabView.createMainTab(context, GlassTabView.TabIcon.HOME, R.string.tab_home);
        tabs[POSITION_SETTINGS] = GlassTabView.createMainTab(context, GlassTabView.TabIcon.SETTINGS, R.string.tab_settings);
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
        applyProfileAvatar();

        blurSourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        blurViewPositionWatcher = new ViewPositionWatcher(contentView);

        BlurredBackgroundDrawableViewFactory glassFactory = new BlurredBackgroundDrawableViewFactory(
                blurSourceTabs != null ? blurSourceTabs : blurSourceColor
        );
        glassFactory.setSourceRootView(blurViewPositionWatcher, contentView);
        glassFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        tabsViewBackground = glassFactory.create(
                tabsView,
                BlurredBackgroundProviderImpl.mainTabs(getResourceProvider())
        );
        tabsViewBackground.setRadius(AndroidUtilities.dp(MAIN_TABS_HEIGHT / 2f));
        tabsViewBackground.setPadding(AndroidUtilities.dp(MAIN_TABS_MARGIN - 0.334f));
        tabsView.setBackground(tabsViewBackground);

        BlurredBackgroundDrawableViewFactory fadeFactory = new BlurredBackgroundDrawableViewFactory(blurSourceColor);
        fadeFactory.setSourceRootView(blurViewPositionWatcher, contentView);
        fadeView = new View(context);
        BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(
                fadeFactory.create(fadeView, null)
        );
        fadeDrawable.setFadeHeight(AndroidUtilities.dp(60f), true);
        fadeView.setBackground(fadeDrawable);
        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsViewWrapper = new FrameLayout(context);
        tabsViewWrapper.setOnClickListener(v -> {
        });
        tabsViewWrapper.setClipToPadding(false);
        tabsViewWrapper.addView(
                tabsView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
        );
        contentView.addView(tabsViewWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        return contentView;
    }

    @Override
    protected WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets) {
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        checkUiTabsPosition();
        checkUiFadeView();
        return super.onApplyWindowInsets(view, insets);
    }

    @Override
    public void onResume() {
        super.onResume();
        blur3UpdateColors();
        checkUiTabsPosition();
        checkUiFadeView();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        if (blurViewPositionWatcher != null) {
            blurViewPositionWatcher.shutdown();
            blurViewPositionWatcher = null;
        }
        super.onFragmentDestroy();
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
        if (!super.onBackPressed()) {
            return true;
        }
        BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null && !fragment.onBackPressed()) {
            return true;
        }
        for (int i = 0; i < fragmentsArr.size(); i++) {
            BaseFragment otherFragment = fragmentsArr.valueAt(i).fragment;
            if (otherFragment != fragment && otherFragment.hasShownSheet() && !otherFragment.onBackPressed()) {
                return true;
            }
        }
        if (viewPager != null && viewPager.getCurrentPosition() != POSITION_HOME) {
            selectTab(POSITION_HOME, true);
            viewPager.scrollToPosition(POSITION_HOME);
            return true;
        }
        return false;
    }

    @Override
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
        if (position == POSITION_SETTINGS) {
            return new SettingsFragment();
        }
        if (position == POSITION_PROFILE) {
            return new ProfileFragment();
        }
        return new TopicListFragment(this);
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return viewPager != null &&
                viewPager.getCurrentPosition() > POSITION_HOME &&
                canCurrentFragmentSlide(ev, false);
    }

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return viewPager != null &&
                viewPager.getCurrentPosition() < POSITION_PROFILE &&
                canCurrentFragmentSlide(ev, true);
    }

    private boolean canCurrentFragmentSlide(MotionEvent event, boolean forward) {
        BaseFragment fragment = getCurrentVisibleFragment();
        return fragment instanceof TabFragmentDelegate &&
                ((TabFragmentDelegate) fragment).canParentTabsSlide(event, forward);
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
        // Fade and blur depend on the pager's animated position.
        checkUiFadeView();
        blur3InvalidateBlur();
    }

    @Override
    public java.util.ArrayList<ThemeDescription> getThemeDescriptions() {
        java.util.ArrayList<ThemeDescription> descriptions = super.getThemeDescriptions();
        ThemeDescription.ThemeDescriptionDelegate chromeDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                blur3UpdateColors();
            }
        };
        if (contentView != null) {
            descriptions.add(new ThemeDescription(contentView, ThemeDescription.FLAG_BACKGROUND, null,
                    (Paint[]) null, null, chromeDelegate, Theme.key_windowBackgroundGray, null));
        }
        View tabView = tabs != null && tabs.length > 0 ? tabs[0] : contentView;
        if (tabView != null) {
            descriptions.add(new ThemeDescription(tabView, 0, null,
                    (Paint[]) null, null, chromeDelegate, Theme.key_windowBackgroundWhite, null));
            descriptions.add(new ThemeDescription(tabView, 0, null,
                    (Paint[]) null, null, chromeDelegate, Theme.key_glass_tabUnselected, null));
            descriptions.add(new ThemeDescription(tabView, 0, null,
                    (Paint[]) null, null, chromeDelegate, Theme.key_glass_tabSelected, null));
            descriptions.add(new ThemeDescription(tabView, 0, null,
                    (Paint[]) null, null, chromeDelegate, Theme.key_glass_tabSelectedText, null));
        }
        return descriptions;
    }

    /**
     * Render the signed-in account's avatar on the profile tab. Called from
     * MainActivity as the session state changes. Safe to call before the view is
     * created — the request is stored and applied in {@link #createView(Context)}.
     */
    public void setProfileAvatar(String url, String filter, String name) {
        hasProfileAvatar = true;
        profileAvatarUrl = url;
        profileAvatarFilter = filter;
        profileAvatarName = name;
        applyProfileAvatar();
    }

    public void clearProfileAvatar() {
        hasProfileAvatar = false;
        profileAvatarUrl = null;
        profileAvatarFilter = null;
        profileAvatarName = null;
        if (tabs != null) {
            tabs[POSITION_PROFILE].clearAvatar();
        }
    }

    private void applyProfileAvatar() {
        if (tabs == null) {
            return;
        }
        if (hasProfileAvatar) {
            tabs[POSITION_PROFILE].setAvatar(profileAvatarUrl, profileAvatarFilter, profileAvatarName);
        } else {
            tabs[POSITION_PROFILE].clearAvatar();
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
        if (viewPager == null || fadeView == null) {
            return;
        }
        int height = navigationBarHeight + AndroidUtilities.dp(MAIN_TABS_HEIGHT_WITH_MARGINS);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fadeView.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            fadeView.setLayoutParams(lp);
        }
        // Let profile content own this area while gesture navigation is active.
        final float animatedPosition = viewPager.getPositionAnimated();
        final float isProfile = 1f - MathUtils.clamp(Math.abs(POSITION_PROFILE - animatedPosition), 0, 1);
        final float hide = 1f - AndroidUtilities.getNavigationBarThirdButtonsFactor(navigationBarHeight);
        float alpha = (1f - isProfile * hide) * tabsVisibilityFactor;
        fadeView.setAlpha(alpha);
        fadeView.setTranslationY(isProfile * AndroidUtilities.dp(48));
        fadeView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
    }

    public interface TabFragmentDelegate {
        void onParentScrollToTop();

        default boolean canParentTabsSlide(MotionEvent event, boolean forward) {
            return false;
        }
    }

    private void blur3InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || blurSourceTabs == null || contentView == null) {
            return;
        }
        int width = contentView.getMeasuredWidth();
        int height = contentView.getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        blurSourceTabs.setSize(width, height);
        blurSourceTabs.updateDisplayListIfNeeded();
    }

    private void blur3UpdateColors() {
        blurSourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        blur3InvalidateBlur();
        if (fadeView != null) {
            fadeView.invalidate();
        }
        if (tabsView != null) {
            tabsView.invalidate();
        }
    }
}
