package io.github.baba983.nativedo.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.Components.FilledTabsView;
import org.telegram.ui.Components.LineProgressView;

import java.util.ArrayList;

import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.auth.SessionStarter;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.topic.TopicListFragment;

/**
 * NativeDo's shell: it hosts upstream's {@link DrawerLayoutContainer} and {@link ActionBarLayout}
 * and pushes our fragments into them.
 *
 * <p>Upstream's own {@code LaunchActivity} cannot be reused. It is Telegram's onboarding flow, not a
 * shell: it calls {@code ApplicationLoader.postInitApplication()} (which builds a
 * {@code MessagesController} and a {@code MediaController} for every account, opening Telegram's
 * database), keys everything off {@code UserConfig.getInstance(...).isClientActivated()}, and
 * returns {@code new LoginActivity()} when no Telegram account exists. None of that applies to a
 * linux.do client, and the hooks that would disable it are the entire Telegram account model.</p>
 */
public class NativeDoActivity extends Activity {

    private ActionBarLayout actionBarLayout;
    private LineProgressView sessionProgress;
    private DrawerLayoutContainer drawerLayoutContainer;
    private FrameLayout contentView;
    private FilledTabsView tabs;
    private int selectedTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);

        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        Theme.createCommonResources(this);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        Theme.createCommonMessageResources();

        // `main == false`: the main layout is Telegram's tabbed shell, and it dereferences
        // LaunchActivity (which this client does not have) while creating its bottom-sheet tab bar.
        // The two behaviours `main` controls are that bar and its height, neither of which applies here.
        actionBarLayout = new ActionBarLayout(this, false);
        actionBarLayout.setFragmentStack(new ArrayList<>());

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setActionBarLayout(actionBarLayout);
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

        contentView = new FrameLayout(this);
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        contentView.addView(drawerLayoutContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        drawerLayoutContainer.addView(actionBarLayout.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(contentView);

        if (savedInstanceState == null) {
            // The session lives in cookies, so what to open with cannot be decided synchronously:
            // show progress, restore the session, then present the answer. Gating here rather than
            // in a fragment of its own keeps the first screen the app shows the real one.
            showSessionProgress();
            AppContainer container = NativeDo.container(this);
            SessionStarter.INSTANCE.start(container, state -> {
                onSessionResolved(state instanceof AppSessionState.Authenticated);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * Two entries, upstream's own tab strip. The tabs replace the root screen instead of stacking on
     * top of it: switching from a topic to settings must not leave the topic behind the back button.
     */
    private void addTabs() {
        tabs = new FilledTabsView(this);
        tabs.setTabs("话题", "设置");
        tabs.setColors(
                Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundGray),
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tabs.onTabSelected(this::showTab);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48), Gravity.BOTTOM);
        params.bottomMargin = AndroidUtilities.navigationBarHeight;
        contentView.addView(tabs, params);
        actionBarLayout.setFragmentStackChangedListener(this::updateTabsVisibility);
        showTabs(true, false);
    }

    /**
     * The tab strip belongs to the roots. A screen opened from one of them (a topic, a settings
     * page) takes the whole window, the way a chat does in Telegram: the strip slides away and the
     * content keeps the height it frees.
     */
    private void updateTabsVisibility() {
        showTabs(actionBarLayout.getFragmentStack().size() <= 1, true);
    }

    private void showTabs(boolean visible, boolean animated) {
        if (tabs == null) {
            return;
        }
        int height = AndroidUtilities.dp(48) + AndroidUtilities.navigationBarHeight;
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
        // The strip is an overlay, so the screens inside the layout are inset by it while it is
        // there and use the whole window while it is away.
        actionBarLayout.getView().setPadding(0, 0, 0, visible ? height : 0);
    }

    /** Shows one of the two roots; the tab strip can only ever hold one of them. */
    private void showTab(int index) {
        if (index == selectedTab || actionBarLayout == null) {
            return;
        }
        selectedTab = index;
        if (tabs != null) {
            tabs.setSelected(index);
        }
        actionBarLayout.removeAllFragments();
        actionBarLayout.presentFragment(rootFor(index), false);
    }

    private BaseFragment rootFor(int index) {
        if (index == 1) {
            // Upstream's appearance settings: day/night, the theme list, accents and text size.
            return new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC);
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
        if (authenticated) {
            addTabs();
            showTab(0);
        } else {
            actionBarLayout.presentFragment(new LoginWebViewFragment(() -> {
                addTabs();
                showTab(0);
            }), false);
        }
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
        if (actionBarLayout != null && actionBarLayout.getFragmentStack().size() > 1) {
            actionBarLayout.onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}