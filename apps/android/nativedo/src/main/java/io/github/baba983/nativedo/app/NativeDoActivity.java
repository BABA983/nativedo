package io.github.baba983.nativedo.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.glass.GlassTabView;

import java.util.ArrayList;

import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.auth.SessionStarter;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.hooks.NativeDoHooks;
import io.github.baba983.nativedo.topic.TopicListFragment;

/**
 * NativeDo's shell: it hosts upstream's {@link DrawerLayoutContainer} and {@link ActionBarLayout}
 * and pushes our fragments into them.
 *
 * <p>{@code LaunchActivity.onCreate()} starts Telegram's account services and gates navigation on
 * {@code UserConfig.isClientActivated()}. This host instead wires the public navigation, tabs,
 * insets and theme APIs to the Discourse session.</p>
 */
public class NativeDoActivity extends Activity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarLayout actionBarLayout;
    /** Upstream's browser hands its minimized pages to its host's overlay; this is that view. */
    private BottomSheetTabsOverlay browserTabsOverlay;
    private LineProgressView sessionProgress;
    private DrawerLayoutContainer drawerLayoutContainer;
    private FrameLayout contentView;
    private MainTabsLayout tabs;
    private BlurredBackgroundDrawable tabsBackground;
    private final BlurredBackgroundSourceColor tabsColorSource = new BlurredBackgroundSourceColor();
    private BlurredBackgroundSourceRenderNode tabsGlassSource;
    private Insets systemInsets = Insets.NONE;
    private boolean keyboardVisible;
    private boolean tabsVisible;
    private int selectedTab = -1;
    /** Whether the reader is on the login page, which the roots and their strip stay away from. */
    private boolean signedOut;
    /** Whether the session question has been answered, so a link can be opened right away. */
    private boolean sessionResolved;
    /** The topic a VIEW link asked for, and the floor it named; 0 while there is none. */
    private long pendingTopicId;
    private int pendingFloor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);

        AndroidUtilities.enableEdgeToEdge(getWindow());
        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        Theme.createCommonResources(this);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        Theme.createCommonMessageResources();

        // Upstream's in-app browser finds its host through LaunchActivity: its static instance,
        // its bottom-sheet tabs and the safe last fragment of its ActionBarLayout. This shell is
        // not a LaunchActivity, so it answers those questions itself through the same single seam
        // every other upstream edit uses. The overlay is created first because the layout asks for
        // it while the tabs are built, exactly as LaunchActivity's does.
        browserTabsOverlay = new BottomSheetTabsOverlay(this);
        NativeDoHooks.setBrowserHost(new NativeDoHooks.BrowserHost() {
            @Override
            public BottomSheetTabs getBottomSheetTabs() {
                return actionBarLayout == null ? null : actionBarLayout.getBottomSheetTabs();
            }

            @Override
            public BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
                return browserTabsOverlay;
            }

            @Override
            public BaseFragment getLastFragment() {
                return actionBarLayout == null ? null : actionBarLayout.getLastFragment();
            }

            @Override
            public BaseFragment getSafeLastFragment() {
                return actionBarLayout == null ? null : actionBarLayout.getSafeLastFragment();
            }

            @Override
            public void checkSystemBarColors() {
                updateSystemBarColors();
            }
        });

        // The upstream layout owns system/IME insets; only our root-tab height is supplied here.
        actionBarLayout = new ActionBarLayout(this, true) {
            @Override
            public int getBottomTabsHeight(boolean animated) {
                // The browser's minimized tabs take the bottom bar when they are up; otherwise it
                // is this shell's own strip that occupies it.
                int browserHeight = super.getBottomTabsHeight(animated);
                if (browserHeight > 0) {
                    return browserHeight;
                }
                return tabsVisible ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
            }

            @Override
            public void relayout() {
                super.relayout();
                // Tabs appearing and disappearing is the browser telling the layout the bottom bar
                // changes hands; the shell's own strip yields to it.
                if (drawerLayoutContainer != null && contentView != null) {
                    updateTabsVisibility();
                }
            }
        };
        actionBarLayout.setFragmentStack(new ArrayList<>());

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setActionBarLayout(actionBarLayout);
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

        if (Build.VERSION.SDK_INT >= 31) {
            tabsGlassSource = new BlurredBackgroundSourceRenderNode(tabsColorSource);
            tabsGlassSource.setBlur(AndroidUtilities.dp(8));
        }
        contentView = new FrameLayout(this) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (canvas.isHardwareAccelerated() && tabsVisible && tabsGlassSource != null) {
                    Canvas recording = tabsGlassSource.beginRecording(getWidth(), getHeight());
                    recording.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    drawerLayoutContainer.draw(recording);
                    tabsGlassSource.endRecording();
                    tabsGlassSource.invalidateDisplayListForDrawables();
                }
                super.dispatchDraw(canvas);
            }
        };
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        contentView.addView(drawerLayoutContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        drawerLayoutContainer.addView(actionBarLayout.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentView.addView(browserTabsOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(contentView);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
            keyboardVisible = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > systemInsets.bottom;
            updateTabsVisibility();
            updateTabInsets();
            ViewCompat.dispatchApplyWindowInsets(drawerLayoutContainer, insets);
            return WindowInsetsCompat.CONSUMED;
        });
        updateSystemBarColors();
        ViewCompat.requestApplyInsets(contentView);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }

        if (savedInstanceState == null) {
            // The session lives in cookies, so what to open with cannot be decided synchronously:
            // show progress, restore the session, then present the answer. Gating here rather than
            // in a fragment of its own keeps the first screen the app shows the real one.
            showSessionProgress();
            AppContainer container = NativeDo.container(this);
            // A linux.do link is the reader saying what to open; it is read before the session is
            // answered so the answer can act on it.
            readTopicLink(getIntent());
            SessionStarter.INSTANCE.start(container, state -> {
                onSessionResolved(state instanceof AppSessionState.Authenticated);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * A linux.do topic link opened while the app is already up. The reader asked for a topic, so it
     * is pushed on top of whatever they were reading, the same as a row tap from a feed.
     */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!readTopicLink(intent) || !sessionResolved) {
            return;
        }
        openPendingTopic();
    }

    /**
     * Reads the topic a VIEW intent names, if it names one. The parsing is the app's own Discourse
     * URL rule ({@code /t/<slug>/<id>[/<floor>]}), not a pattern invented here, and a link that is
     * not a same-origin topic is left alone rather than guessed at.
     */
    private boolean readTopicLink(android.content.Intent intent) {
        if (intent == null || !android.content.Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        android.net.Uri data = intent.getData();
        io.github.baba983.nativedo.core.web.DiscourseTopicLink link =
                io.github.baba983.nativedo.core.web.DiscourseLinks.topicLink(
                        data == null ? null : data.toString(),
                        NativeDo.container(this).getBaseUrl());
        if (link == null || link.getTopicId() <= 0) {
            return false;
        }
        pendingTopicId = link.getTopicId();
        pendingFloor = link.getFloor();
        return true;
    }

    /**
     * Three entries, upstream's own tab strip: the feeds, the reader's notifications, and settings.
     * The tabs replace the root screen instead of stacking on top of it: switching from a topic to
     * settings must not leave the topic behind the back button.
     */
    private void addTabs() {
        tabs = new MainTabsLayout(this, null);
        int padding = AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4);
        tabs.setPadding(padding, padding, padding, padding);
        tabs.setClipChildren(false);
        tabs.setMaxWidth(AndroidUtilities.dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));
        GlassTabView.TabAnimation[] icons = {
                GlassTabView.TabAnimation.CHATS, GlassTabView.TabAnimation.REPLIES, GlassTabView.TabAnimation.SETTINGS};
        int[] labels = {org.telegram.messenger.R.string.MainTabsChats,
                org.telegram.messenger.R.string.MainTabsContacts, org.telegram.messenger.R.string.Settings};
        String[] descriptions = {"话题", "通知", "设置"};
        for (int index = 0; index < labels.length; index++) {
            final int tabIndex = index;
            GlassTabView tab = GlassTabView.createMainTab(this, null, icons[index], labels[index]);
            tab.setContentDescription(descriptions[index]);
            tab.setOnClickListener(view -> showTab(tabIndex));
            tabs.addView(tab);
            tabs.setViewVisible(tab, true, false);
        }
        tabsColorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(
                tabsGlassSource != null ? tabsGlassSource : tabsColorSource);
        factory.setSourceRootView(new ViewPositionWatcher(contentView), contentView);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        tabsBackground = factory.create(tabs, BlurredBackgroundProviderImpl.mainTabs(null));
        tabsBackground.setRadius(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsBackground.setPadding(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        tabs.setBackground(tabsBackground);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        contentView.addView(tabs, params);
        updateTabInsets();
        actionBarLayout.setFragmentStackChangedListener(this::updateTabsVisibility);
        showTabs(true, false);
    }

    /**
     * The tab strip belongs to the roots. A screen opened from one of them (a topic, a settings
     * page) takes the whole window, the way a chat does in Telegram: the strip slides away and the
     * content keeps the height it frees. The browser's minimized pages take the bottom bar the
     * same way, so this shell's strip steps aside while they are up.
     */
    private void updateTabsVisibility() {
        showTabs(!signedOut && !keyboardVisible && !browserTabsExpanded()
                && actionBarLayout.getFragmentStack().size() <= 1, true);
        updateSystemBarColors();
    }

    /** Whether upstream's browser has a minimized page; its strip occupies the bottom bar then. */
    private boolean browserTabsExpanded() {
        BottomSheetTabs browserTabs = actionBarLayout == null ? null : actionBarLayout.getBottomSheetTabs();
        return browserTabs != null && !browserTabs.getTabs().isEmpty();
    }

    private void updateTabInsets() {
        if (tabs != null) {
            AndroidUtilities.setViewLayoutMargins(tabs, systemInsets.left, 0, systemInsets.right, systemInsets.bottom);
        }
    }

    private void updateSystemBarColors() {
        BaseFragment fragment = actionBarLayout.getLastFragment();
        int navigationColor = fragment != null ? fragment.getNavigationBarColor()
                : Theme.getColor(Theme.key_windowBackgroundGray);
        // A sheet above the screen (the in-app browser is one) owns the bar color; upstream's own
        // checkSystemBarColors carries the same rule.
        navigationColor = sheetNavigationBarColor(fragment, navigationColor);
        BaseFragment sheetFragment = actionBarLayout.getSheetFragment(false);
        if (sheetFragment != null) {
            navigationColor = sheetNavigationBarColor(sheetFragment, navigationColor);
        }
        drawerLayoutContainer.setInternalNavigationBarColor(navigationColor);
        BottomSheetTabs browserTabs = actionBarLayout.getBottomSheetTabs();
        if (browserTabs != null) {
            browserTabs.setNavigationBarColor(navigationColor);
        }
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), contentView);
        controller.setAppearanceLightStatusBars(fragment != null ? fragment.isLightStatusBar() : !Theme.isCurrentThemeDark());
        controller.setAppearanceLightNavigationBars(AndroidUtilities.computePerceivedBrightness(navigationColor) >= 0.721f);
        tabsColorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (tabsBackground != null) {
            tabsBackground.updateColors();
        }
        if (tabs != null) {
            for (int index = 0; index < tabs.getChildCount(); index++) {
                ((GlassTabView) tabs.getChildAt(index)).updateColorsLottie();
            }
        }
    }

    private int sheetNavigationBarColor(BaseFragment fragment, int color) {
        if (fragment == null || fragment.sheetsStack == null) {
            return color;
        }
        for (BaseFragment.AttachedSheet sheet : fragment.sheetsStack) {
            if (sheet.attachedToParent()) {
                color = sheet.getNavigationBarColor(color);
            }
        }
        return color;
    }

    private void showTabs(boolean visible, boolean animated) {
        if (tabs == null) {
            return;
        }
        boolean changed = tabsVisible != visible;
        tabsVisible = visible;
        int height = AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) + systemInsets.bottom;
        tabs.animate().cancel();
        if (animated) {
            tabs.animate()
                    .alpha(visible ? 1f : 0f)
                    .translationY(visible ? 0f : (float) height)
                    .setDuration(200)
                    .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_BOTH)
                    .start();
        } else {
            tabs.setAlpha(visible ? 1f : 0f);
            tabs.setTranslationY(visible ? 0f : (float) height);
        }
        if (changed) {
            ViewCompat.requestApplyInsets(actionBarLayout);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.needSetDayNightTheme) {
            Theme.ThemeInfo theme = (Theme.ThemeInfo) args[0];
            boolean nightTheme = (Boolean) args[1];
            int accentId = (Integer) args[3];
            if (accentId != -1) {
                theme.setCurrentAccentId(accentId);
                Theme.saveThemeAccents(theme, true, false, true, false);
            }
            // Theme's save=true also saves to a Telegram account. Keep only the local preference
            // read by its startup loader, and use its public local-only application path.
            Theme.applyTheme(theme, false, nightTheme);
            if (!nightTheme) {
                getSharedPreferences("mainconfig", MODE_PRIVATE).edit().putString("theme", theme.getKey()).apply();
            }
            INavigationLayout.ThemeAnimationSettings settings = new INavigationLayout.ThemeAnimationSettings(
                    theme, accentId, nightTheme, false);
            settings.applyTheme = settings.applyTrulyTheme = false;
            settings.animationProgress = progress -> updateSystemBarColors();
            settings.afterAnimationRunnable = () -> {
                DialogsActivity.switchingTheme = false;
                updateSystemBarColors();
            };
            Runnable onApplied = args.length > 7 ? (Runnable) args[7] : null;
            actionBarLayout.animateThemedValues(settings, null);
            if (onApplied != null) {
                onApplied.run();
            }
            updateSystemBarColors();
        }
    }

    @Override
    protected void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NativeDoHooks.setBrowserHost(null);
        super.onDestroy();
    }

    /**
     * The app going to the background is an app-wide fact, and a screen that counts reading time has
     * to know it: upstream's fragments are not AndroidX fragments and are only told what the layout
     * is told, so the activity forwards its own lifecycle the way Telegram's shell does.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (actionBarLayout != null) {
            actionBarLayout.onResume();
            updateSystemBarColors();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (actionBarLayout != null) {
            actionBarLayout.onPause();
        }
    }

    /**
     * Upstream's fragments ask the system for a result through the layout, so the layout's host has
     * to hand the answer back: the top screen is the one that asked.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        java.util.List<BaseFragment> stack = actionBarLayout.getFragmentStack();
        if (!stack.isEmpty()) {
            stack.get(stack.size() - 1).onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    /** Replaces the root with the selected tab's screen. */
    private void showTab(int index) {
        if (index == selectedTab || actionBarLayout == null) {
            return;
        }
        selectedTab = index;
        if (tabs != null) {
            tabs.setTabSelected(tabs.getChildAt(index), true);
        }
        actionBarLayout.removeAllFragments();
        actionBarLayout.presentFragment(rootFor(index), false);
    }

    private BaseFragment rootFor(int index) {
        if (index == 1) {
            return new io.github.baba983.nativedo.notifications.NotificationsFragment();
        }
        if (index == 2) {
            // Our own settings, built from upstream's cells; the appearance page itself is still
            // upstream's, one row in.
            return new io.github.baba983.nativedo.settings.SettingsFragment();
        }
        return new TopicListFragment();
    }

    private void showSessionProgress() {
        LineProgressView progress = new LineProgressView(this);
        progress.setProgressColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton));
        progress.setProgress(0f, false);
        sessionProgress = progress;
        contentView.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(2)));
    }

    private void onSessionResolved(boolean authenticated) {
        if (sessionProgress != null) {
            contentView.removeView(sessionProgress);
            sessionProgress = null;
        }
        sessionResolved = true;
        if (authenticated) {
            addTabs();
            showRootOrPendingTopic();
        } else {
            actionBarLayout.presentFragment(new LoginWebViewFragment(() -> {
                addTabs();
                showRootOrPendingTopic();
            }), false);
        }
    }

    /**
     * The roots first, then the topic a link asked for on top of them, so back returns to the feed
     * rather than leaving the app. A plain launch has no pending link and just shows the feed.
     */
    private void showRootOrPendingTopic() {
        if (pendingTopicId == 0) {
            showTab(0);
            return;
        }
        // A link wants two screens at once: the feed, so back returns to it, and the topic over it.
        // Presenting both in the same turn drops the second - the first transition is still running
        // and presentFragment refuses while one is - so the feed goes on the stack without an
        // animation and the topic is presented over it.
        selectedTab = 0;
        if (tabs != null) {
            tabs.setTabSelected(tabs.getChildAt(0), false);
        }
        actionBarLayout.removeAllFragments();
        actionBarLayout.addFragmentToStack(rootFor(0));
        openPendingTopic();
    }

    private void openPendingTopic() {
        long topicId = pendingTopicId;
        if (topicId == 0) {
            return;
        }
        pendingTopicId = 0;
        Integer floor = pendingFloor > 0 ? pendingFloor : null;
        pendingFloor = 0;
        present(io.github.baba983.nativedo.topic.TopicFragment.forFloor(topicId, null, floor));
    }

    /**
     * The reader signed out. The session is gone, so every screen built on it is gone too: the
     * login page replaces the stack, and the tabs come back once there is a reader again.
     */
    public void signedOut() {
        if (actionBarLayout == null) {
            return;
        }
        // Nothing behind the login page is usable without a session, so the tab strip goes with it
        // and comes back when the reader is back.
        signedOut = true;
        actionBarLayout.removeAllFragments();
        showTabs(false, false);
        actionBarLayout.presentFragment(new LoginWebViewFragment(() -> {
            signedOut = false;
            if (tabs == null) {
                addTabs();
            }
            showTab(0);
        }), false);
    }

    /** Pushes a screen on top of the current one; back returns to it. */
    public void present(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, false);
    }

    /** Replaces the current screen, for a step that must not stay in the back stack. */
    public void replace(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, true);
    }

    @Override
    public void onBackPressed() {
        if (browserTabsOverlay != null && browserTabsOverlay.isOpen) {
            browserTabsOverlay.onBackPressed();
            return;
        }
        if (actionBarLayout != null && actionBarLayout.getFragmentStack().size() > 1) {
            actionBarLayout.onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}
