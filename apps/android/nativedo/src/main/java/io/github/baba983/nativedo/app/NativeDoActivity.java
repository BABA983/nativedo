package io.github.baba983.nativedo.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.window.OnBackInvokedDispatcher;
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
        tabs = new FilledTabsView(this);
        tabs.setTabs("话题", "通知", "设置");
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
        showTabs(!signedOut && actionBarLayout.getFragmentStack().size() <= 1, true);
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
            tabs.setSelected(0);
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
        if (actionBarLayout != null && actionBarLayout.getFragmentStack().size() > 1) {
            actionBarLayout.onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}
