package io.github.baba983.nativedo.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.auth.SessionStarter;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.hooks.NativeDoHooks;

/**
 * NativeDo's shell: it hosts upstream's {@link DrawerLayoutContainer} and {@link ActionBarLayout},
 * gates everything on the Discourse session, and pushes our fragments into them.
 *
 * <p>{@code LaunchActivity.onCreate()} starts Telegram's account services and gates navigation on
 * {@code UserConfig.isClientActivated()}. This host instead wires the public navigation, insets and
 * theme APIs to the Discourse session; the tab shell itself is
 * {@link NativeDoMainTabsActivity}, presented as the root fragment the way upstream presents its
 * own {@code MainTabsActivity}.</p>
 */
public class NativeDoActivity extends Activity implements NotificationCenter.NotificationCenterDelegate,
        INavigationLayout.INavigationLayoutDelegate {

    private ActionBarLayout actionBarLayout;
    /** Upstream's browser hands its minimized pages to its host's overlay; this is that view. */
    private BottomSheetTabsOverlay browserTabsOverlay;
    private LineProgressView sessionProgress;
    private DrawerLayoutContainer drawerLayoutContainer;
    private FrameLayout contentView;
    /**
     * The back callbacks, held as {@link Object} exactly as upstream's {@code LaunchActivity} holds
     * them (870-871): the API-33 and API-34 types only exist on those platforms, so neither is a
     * field type the class can name everywhere.
     */
    private Object onBackAnimationCallback;
    private Object onBackInvokedCallback;
    /**
     * The day/night circular reveal's snapshot, the way upstream's {@code LaunchActivity} draws it:
     * the old look in an {@link ImageView} over (or under) the layout while a circular clip animates.
     */
    private ImageView themeSwitchImageView;
    /** The system-bar colors stay put while the reveal runs, as in upstream's handler. */
    private boolean isNavigationBarColorFrozen;
    /**
     * Upstream's power-saver feed (LaunchActivity:380, 412): the battery level drives LiteMode's
     * preset, and the preset is what the glass (chat blur / liquid glass) reads.
     */
    private final LiteMode.BatteryReceiver batteryReceiver = new LiteMode.BatteryReceiver();
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

        // LaunchActivity:729-730, in upstream's order: the window is told to lay out behind the
        // system bars before the edge-to-edge helper registers the cutout mode and the un-enforced
        // navigation-bar contrast. The activity overload is the one that also zeroes the
        // navigation-bar divider.
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        AndroidUtilities.enableEdgeToEdge(this);
        if (Build.VERSION.SDK_INT >= 24) {
            AndroidUtilities.isInMultiwindow = isInMultiWindowMode();
        }
        // LaunchActivity:412: the sticky battery broadcast is what LiteMode's power-saver level
        // (and with it the glass presets) follows while the app is up.
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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

        // The upstream layout owns system/IME insets and the browser's minimized tabs. It does not
        // know about the root tab strip: that belongs to NativeDoMainTabsActivity, which pads its
        // own pages and steps its strip aside for the browser through this shell.
        actionBarLayout = new ActionBarLayout(this, true) {
            @Override
            public void relayout() {
                super.relayout();
                // Tabs appearing and disappearing is the browser telling the layout the bottom bar
                // changes hands; the shell's own strip yields to it.
                if (drawerLayoutContainer != null && contentView != null) {
                    updateBrowserTabs();
                }
            }
        };
        actionBarLayout.setFragmentStack(new ArrayList<>());
        // LaunchActivity:552. The one answer this shell takes from the delegate is
        // needCloseLastFragment below: a back that would close the last fragment is the activity's
        // own leave, the way upstream's answers it.
        actionBarLayout.setDelegate(this);
        // LaunchActivity:565-568: the bars follow the fragment the layout now shows. Opening a
        // screen, closing one or replacing the stack recomputes the status-bar icon tint and the
        // navigation-bar band from the new top fragment.
        actionBarLayout.setFragmentStackChangedListener(this::updateSystemBarColors);
        // LaunchActivity:553 loads the chat wallpaper here, so a chat screen can draw it at once.
        Theme.loadWallpaper(true);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setActionBarLayout(actionBarLayout);
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

        contentView = new FrameLayout(this);
        contentView.addView(drawerLayoutContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The snapshot sits at index 0 for a switch to night (under the layout, which the circular
        // reveal un-clips) and is inserted at index 1 for a switch to day (over the layout, shrinking
        // away); both insertions are the way upstream's handler does it.
        themeSwitchImageView = new ImageView(this);
        themeSwitchImageView.setVisibility(View.GONE);
        drawerLayoutContainer.addView(actionBarLayout.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentView.addView(browserTabsOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(contentView);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            ViewCompat.dispatchApplyWindowInsets(drawerLayoutContainer, insets);
            return WindowInsetsCompat.CONSUMED;
        });
        updateSystemBarColors();
        ViewCompat.requestApplyInsets(contentView);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        // LaunchActivity's global observer group: a screen (or a sheet) that changes what the bars
        // must look like asks for the recompute; the applied-theme notification does the same after
        // Theme has recoloured everything.
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needCheckSystemBarColors);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        // LaunchActivity:735-849 verbatim in shape. API 34 gets the animation callback (the layout
        // follows the finger: ActionBarLayout.onBackStarted/onBackProgress transform the page and
        // onBackCancelled/onBackInvoked end or undo it); API 33 gets the plain callback, which pops
        // on invoke only. The 34 callback starts the layout lazily on the first progressed event,
        // because Android delivers onBackStarted before it knows whether the gesture will commit.
        // No tablet branch: this host has one actionBarLayout. The checks LaunchActivity's
        // onBackPressed(boolean) makes before any of this (sheets, PhotoViewer, ArticleViewer) are
        // below, adapted to the consumers this app has.
        if (Build.VERSION.SDK_INT >= 34) {
            if (onBackAnimationCallback == null) {
                onBackAnimationCallback = new OnBackAnimationCallback() {
                    private AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
                    private boolean locked;

                    private boolean started = false;
                    private boolean invoked = false;

                    @Override
                    public void onBackInvoked() {
                        invoked = true;
                        if (locked) {
                            locker.unlock();
                            locked = false;
                        }

                        if (!onBackPressed(true))
                            return;
                        if (actionBarLayout != null) {
                            actionBarLayout.onBackInvoked();
                        } else {
                            onBackPressed();
                        }
                    }

                    @Override
                    public void onBackStarted(@NonNull BackEvent backEvent) {
                        started = true;
                        invoked = false;
                        predictiveBackStarted = false;
                    }

                    private void onBackStartedInternal(BackEvent backEvent) {
                        if (!onBackPressed(false)) return;
                        if (actionBarLayout != null) {
                            boolean started = actionBarLayout.onBackStarted(backEvent.getTouchX(), backEvent.getTouchY());
                            if (started && !locked) {
                                locker.lock();
                                locked = true;
                            }
                        }
                    }

                    private static final float LAZY_START = 0.015f;
                    private boolean predictiveBackStarted;
                    private boolean predictiveBackInvoked;

                    @Override
                    public void onBackProgressed(@NonNull BackEvent backEvent) {
                        if (started && invoked) return;

                        final float progress = backEvent.getProgress();
                        if (!predictiveBackStarted && progress > LAZY_START) {
                            predictiveBackStarted = true;
                            onBackStartedInternal(backEvent);
                        }

                        final float fixedProgress = Math.max(0, progress - LAZY_START) / (1 - LAZY_START);

                        if (actionBarLayout != null) {
                            actionBarLayout.onBackProgress(fixedProgress);
                        }
                    }

                    @Override
                    public void onBackCancelled() {
                        started = false;
                        invoked = false;
                        if (locked) {
                            locker.unlock();
                            locked = false;
                        }

                        if (actionBarLayout != null) {
                            actionBarLayout.onBackCancelled();
                        }
                    }
                };
            }
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (OnBackAnimationCallback) onBackAnimationCallback
            );
        } else if (Build.VERSION.SDK_INT >= 33) {
            if (onBackInvokedCallback == null) {
                onBackInvokedCallback = new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (!onBackPressed(true))
                            return;
                        if (actionBarLayout != null) {
                            actionBarLayout.onBackInvoked();
                        } else {
                            onBackPressed();
                        }
                    }
                };
            }
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (OnBackInvokedCallback) onBackInvokedCallback
            );
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
     * The shell's strip lives in {@link NativeDoMainTabsActivity}, so the states that used to hide
     * this activity's own strip - the home search opening, the browser's minimized pages taking
     * the bottom bar - are forwarded to the host fragment that owns it. A screen pushed from a
     * page covers the strip, and the login page leaves no host on the stack, so neither needs a
     * flag here.
     */
    public void setSearchOpen(boolean open) {
        if (actionBarLayout == null) {
            return;
        }
        for (BaseFragment stacked : actionBarLayout.getFragmentStack()) {
            if (stacked instanceof NativeDoMainTabsActivity) {
                ((NativeDoMainTabsActivity) stacked).setSearchOpen(open);
            }
        }
    }

    /** Whether upstream's browser has a minimized page; its strip occupies the bottom bar then. */
    private boolean browserTabsExpanded() {
        BottomSheetTabs browserTabs = actionBarLayout == null ? null : actionBarLayout.getBottomSheetTabs();
        return browserTabs != null && !browserTabs.getTabs().isEmpty();
    }

    /** The strip yields to the browser's minimized pages through the host fragment's own state. */
    private void updateBrowserTabs() {
        if (actionBarLayout == null) {
            return;
        }
        boolean expanded = browserTabsExpanded();
        for (BaseFragment stacked : actionBarLayout.getFragmentStack()) {
            if (stacked instanceof NativeDoMainTabsActivity) {
                ((NativeDoMainTabsActivity) stacked).setBrowserTabsExpanded(expanded);
            }
        }
    }

    private void updateSystemBarColors() {
        // While the reveal is running upstream keeps the system-bar colours from before the switch;
        // recomputing them here would paint the bars in the new theme before the circle reaches
        // them. The reveal itself resets the flag and calls this again when it ends.
        if (isNavigationBarColorFrozen) {
            return;
        }
        updateSystemBarColors(true, true, true);
    }

    /**
     * LaunchActivity's {@code checkSystemBarColors} (1115-1185), for the two bars this shell owns.
     * {@code useCurrentFragment} is upstream's own flag: when false the navigation bar takes the
     * window's default gray instead of the top fragment's colour - the notifications the screens
     * post carry it.
     */
    private void updateSystemBarColors(boolean useCurrentFragment, boolean checkStatusBar, boolean checkNavigationBar) {
        if (actionBarLayout == null || drawerLayoutContainer == null || contentView == null) {
            return;
        }
        BaseFragment fragment = actionBarLayout.getLastFragment();
        if (fragment != null && (fragment.isRemovingFromStack() || fragment.isInPreviewMode())) {
            List<BaseFragment> stack = actionBarLayout.getFragmentStack();
            fragment = stack.size() > 1 ? stack.get(stack.size() - 2) : null;
        }
        if (checkStatusBar) {
            // Upstream's own answer (1120-1146): the top fragment decides, unless a sheet over it
            // owns the bar (the article viewer says whether its page is light); with no fragment at
            // all the tint follows the theme's action-bar colour.
            boolean enable;
            if (fragment != null) {
                enable = fragment.isLightStatusBar();
                if (fragment.getParentLayout() instanceof ActionBarLayout) {
                    ActionBarLayout layout = (ActionBarLayout) fragment.getParentLayout();
                    BaseFragment sheetFragment = layout.getSheetFragment(false);
                    if (sheetFragment != null && sheetFragment.getLastSheet() != null) {
                        BaseFragment.AttachedSheet sheet = sheetFragment.getLastSheet();
                        if (sheet.isShown()) {
                            enable = sheet.isAttachedLightStatusBar();
                        }
                    } else if (fragment.sheetsStack != null && !fragment.sheetsStack.isEmpty()) {
                        BaseFragment.AttachedSheet sheet =
                                fragment.sheetsStack.get(fragment.sheetsStack.size() - 1);
                        if (sheet.isShown()) {
                            enable = sheet.isAttachedLightStatusBar();
                        }
                    }
                }
            } else {
                enable = ColorUtils.calculateLuminance(
                        Theme.getColor(Theme.key_actionBarDefault, null, true)) > 0.7f;
            }
            AndroidUtilities.setLightStatusBar(this, enable);
        }
        if (checkNavigationBar) {
            int navigationColor = fragment != null && useCurrentFragment
                    ? fragment.getNavigationBarColor()
                    : Theme.getColor(Theme.key_windowBackgroundGray);
            // A sheet above the screen (the in-app browser is one) owns the bar color; upstream's
            // own checkSystemBarColors carries the same rule.
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
            AndroidUtilities.setLightNavigationBar(this,
                    AndroidUtilities.computePerceivedBrightness(navigationColor) >= 0.721f);
        }
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        // The tab strip belongs to the root fragment, and a theme change has to reach it even while
        // a screen that fragment pushed (the appearance page) is on top: every host in the stack is
        // told, not only the last fragment.
        for (BaseFragment stacked : actionBarLayout.getFragmentStack()) {
            if (stacked instanceof NativeDoMainTabsActivity) {
                ((NativeDoMainTabsActivity) stacked).updateColors();
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

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.needCheckSystemBarColors) {
            // LaunchActivity:7555-7558. The screens post their ask with the flag: true means the
            // navigation bar takes the fragment's own colour, false the window's default gray.
            if (!isNavigationBarColorFrozen) {
                updateSystemBarColors(args.length > 0 && (boolean) args[0], true, true);
            }
            return;
        }
        if (id == NotificationCenter.didSetNewTheme) {
            // LaunchActivity:7330-7350: a theme change recolours the bars, but the circular reveal
            // freezes the navigation bar and a running transition owns the screen for now.
            boolean checkNavigationBarColor = args.length > 1 && (boolean) args[1];
            if (checkNavigationBarColor && !isNavigationBarColorFrozen
                    && !actionBarLayout.isTransitionAnimationInProgress()) {
                updateSystemBarColors(args.length > 2 && (boolean) args[2], true, true);
            }
            return;
        }
        if (id != NotificationCenter.needSetDayNightTheme) {
            return;
        }
        Theme.ThemeInfo theme = (Theme.ThemeInfo) args[0];
        boolean nightTheme = (Boolean) args[1];
        int[] pos = args.length > 2 ? (int[]) args[2] : null;
        int accentId = args.length > 3 ? (Integer) args[3] : -1;
        boolean toDark = args.length > 4 && args[4] != null && (Boolean) args[4];
        Runnable calcInBackgroundEnd = args.length > 7 ? (Runnable) args[7] : null;

        // The menu's own switch carries the icon's centre; upstream's handler turns that into a
        // circular reveal of the new theme, and the per-view colour animation is skipped for it
        // (`instant`) because the reveal is what the reader sees. Any other switch (the appearance
        // page) has no position and keeps the colour animation.
        boolean instant = false;
        if (pos != null) {
            if (themeSwitchImageView.getVisibility() == View.VISIBLE) {
                return;
            }
            try {
                int w = drawerLayoutContainer.getMeasuredWidth();
                int h = drawerLayoutContainer.getMeasuredHeight();
                isNavigationBarColorFrozen = true;
                invalidateCachedViews(drawerLayoutContainer);

                Bitmap bitmap = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bitmap = AndroidUtilities.getBitmapFromWindow(getWindow());
                }
                if (bitmap == null) {
                    bitmap = AndroidUtilities.snapshotView(drawerLayoutContainer);
                }
                contentView.removeView(themeSwitchImageView);
                themeSwitchImageView = new ImageView(this);
                if (toDark) {
                    contentView.addView(themeSwitchImageView, 0, LayoutHelper.createFrame(
                            LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                } else {
                    contentView.addView(themeSwitchImageView, 1, LayoutHelper.createFrame(
                            LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                }
                themeSwitchImageView.setImageBitmap(bitmap);
                themeSwitchImageView.setVisibility(View.VISIBLE);
                float finalRadius = (float) Math.max(
                        Math.sqrt((w - pos[0]) * (w - pos[0]) + (h - pos[1]) * (h - pos[1])),
                        Math.sqrt(pos[0] * pos[0] + (h - pos[1]) * (h - pos[1])));
                float finalRadius2 = (float) Math.max(
                        Math.sqrt((w - pos[0]) * (w - pos[0]) + pos[1] * pos[1]),
                        Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1]));
                finalRadius = Math.max(finalRadius, finalRadius2);
                Animator anim = ViewAnimationUtils.createCircularReveal(
                        toDark ? drawerLayoutContainer : themeSwitchImageView, pos[0], pos[1],
                        toDark ? 0 : finalRadius, toDark ? finalRadius : 0);
                anim.setDuration(400);
                anim.setInterpolator(Easings.easeInOutQuad);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        drawerLayoutContainer.invalidate();
                        themeSwitchImageView.invalidate();
                        themeSwitchImageView.setImageDrawable(null);
                        themeSwitchImageView.setVisibility(View.GONE);
                        DialogsActivity.switchingTheme = false;
                    }
                });
                // Upstream holds the system-bar colours through the reveal and sets them once the
                // circle has passed the navigation bar; the same delay and constant here.
                AndroidUtilities.runOnUIThread(() -> {
                    if (isNavigationBarColorFrozen) {
                        isNavigationBarColorFrozen = false;
                        updateSystemBarColors();
                    }
                }, toDark ? (h - pos[1]) / AndroidUtilities.dp(2.25f) : 50);
                anim.start();
                instant = true;
            } catch (Throwable e) {
                FileLog.e(e);
                try {
                    themeSwitchImageView.setImageDrawable(null);
                    contentView.removeView(themeSwitchImageView);
                    DialogsActivity.switchingTheme = false;
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
                isNavigationBarColorFrozen = false;
            }
        } else {
            DialogsActivity.switchingTheme = false;
        }

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
                theme, accentId, nightTheme, instant);
        settings.applyTheme = settings.applyTrulyTheme = false;
        settings.animationProgress = progress -> updateSystemBarColors();
        settings.afterAnimationRunnable = () -> {
            DialogsActivity.switchingTheme = false;
            updateSystemBarColors();
        };
        actionBarLayout.animateThemedValues(settings, null);
        if (calcInBackgroundEnd != null) {
            calcInBackgroundEnd.run();
        }
        updateSystemBarColors();
        // Upstream's own theme path recolours in place: the host fragment returns theme
        // descriptions that reach its pages (ViewPagerActivity.getThemeDescriptions, 241-250),
        // and each mounted page repaints itself. No view is rebuilt, so the strip, the feed and
        // the reader's place in it survive the switch - only their colours change under the
        // reveal. The fragments below a pushed screen are still cleared by upstream's own
        // animation (ActionBarLayout.animateThemedValues), which is what makes a screen pushed
        // from the home come back in the new theme.
    }

    /**
     * The recursive invalidation upstream's handler runs before a reveal snapshot, so no view with
     * a cached hardware layer keeps drawing the old theme while the circle grows.
     */
    private void invalidateCachedViews(View parent) {
        if (parent.getLayerType() != View.LAYER_TYPE_NONE) {
            parent.invalidate();
        }
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                invalidateCachedViews(viewGroup.getChildAt(i));
            }
        }
    }

    @Override
    protected void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needCheckSystemBarColors);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        // LaunchActivity:6857: the sticky battery broadcast is dropped with the activity.
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            FileLog.e(e);
        }
        NativeDoHooks.setBrowserHost(null);
        // LaunchActivity:6886-6894. (Upstream's API-33 branch tests the wrong field at 6891 - the
        // plain callback is the one registered there - so this unregisters the one it registered.)
        if (Build.VERSION.SDK_INT >= 34) {
            if (onBackAnimationCallback instanceof OnBackAnimationCallback) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackAnimationCallback) onBackAnimationCallback);
            }
        } else if (Build.VERSION.SDK_INT >= 33) {
            if (onBackInvokedCallback instanceof OnBackInvokedCallback) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) onBackInvokedCallback);
            }
        }
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
        // LaunchActivity:6977-6983: the app-wide "in front" flag the viewers read, and the heavy
        // drawing gate that goes with it, follow the shell's resume.
        NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.startAllHeavyOperations, 4096);
        ApplicationLoader.mainInterfacePaused = false;
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions();
        }
        if (actionBarLayout != null) {
            actionBarLayout.onResume();
            updateSystemBarColors();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // LaunchActivity:6732-6733. PhotoViewer's video controls ask this flag before they hide,
        // and the theme's ambient sensor asks it before it reacts; a value stuck at its default
        // true would leave the controls on screen and the sensor deaf.
        NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.stopAllHeavyOperations, 4096);
        ApplicationLoader.mainInterfacePaused = true;
        if (actionBarLayout != null) {
            actionBarLayout.onPause();
        }
    }

    /**
     * Upstream's activity handles the same configuration set (the manifest keeps this activity
     * across them), and a rotation must reach the layout math: {@code checkDisplaySize} refreshes
     * density, display size, refresh rate and the touch slop, {@code resetTabletFlag} lets the
     * tablet decision be remade. The system auto-night watch is the other winner of a uiMode
     * change (LaunchActivity:7117-7160).
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        AndroidUtilities.checkDisplaySize(this, newConfig);
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        super.onConfigurationChanged(newConfig);
        AndroidUtilities.resetTabletFlag();
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions();
        }
    }

    /** LaunchActivity:7147-7152. Upstream's fragments ask this before they lay out a split. */
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        AndroidUtilities.isInMultiwindow = isInMultiWindowMode;
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    /** LaunchActivity:8353-8357: the layout passes the low-memory hint to its fragments. */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (actionBarLayout != null) {
            actionBarLayout.onLowMemory();
        }
    }

    /**
     * The message bus belongs to the app, not to a screen: the host is the one place that knows
     * whether the app is in front, so it is the one that tells the session. The session itself
     * follows sign-in and sign-out, so a logged-out app has nothing polling.
     */
    @Override
    protected void onStart() {
        super.onStart();
        NativeDo.container(this).getMessageBusSession().onForeground();
    }

    @Override
    protected void onStop() {
        super.onStop();
        NativeDo.container(this).getMessageBusSession().onBackground();
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

    /** Presents the tab shell as the root, replacing whatever was there. */
    private void presentRoot() {
        if (actionBarLayout == null) {
            return;
        }
        actionBarLayout.removeAllFragments();
        actionBarLayout.presentFragment(new NativeDoMainTabsActivity(), false);
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
            showRootOrPendingTopic();
        } else {
            actionBarLayout.presentFragment(new LoginWebViewFragment(this::showRootOrPendingTopic), false);
        }
    }

    /**
     * The roots first, then the topic a link asked for on top of them, so back returns to the feed
     * rather than leaving the app. A plain launch has no pending link and just shows the feed.
     */
    private void showRootOrPendingTopic() {
        if (pendingTopicId == 0) {
            presentRoot();
            return;
        }
        // A link wants two screens at once: the feed, so back returns to it, and the topic over it.
        // Presenting both in the same turn drops the second - the first transition is still running
        // and presentFragment refuses while one is - so the feed goes on the stack without an
        // animation and the topic is presented over it.
        actionBarLayout.removeAllFragments();
        actionBarLayout.addFragmentToStack(new NativeDoMainTabsActivity());
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
     * login page replaces the stack, and the tab shell comes back once there is a reader again.
     */
    public void signedOut() {
        if (actionBarLayout == null) {
            return;
        }
        // Nothing behind the login page is usable without a session, so the tab shell goes with it
        // and comes back when the reader is back.
        actionBarLayout.removeAllFragments();
        actionBarLayout.presentFragment(new LoginWebViewFragment(this::showRootOrPendingTopic), false);
    }

    /** Pushes a screen on top of the current one; back returns to it. */
    public void present(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, false);
    }

    /** Replaces the current screen, for a step that must not stay in the back stack. */
    public void replace(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, true);
    }

    /**
     * Back on the activity goes through the same consumers upstream's {@code LaunchActivity}
     * consults, then the layout: it is the layout that pops a fragment, closes a sheet or animates
     * the predictive-back gesture to its end (LaunchActivity:8300-8320).
     */
    @Override
    public void onBackPressed() {
        if (!onBackPressed(true)) {
            return;
        }
        if (actionBarLayout != null) {
            actionBarLayout.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Whether this back belongs to the layout at all, and - with {@code invoked} - the doing of it.
     * LaunchActivity:8322-8355, with the consumers this host actually has: the browser's minimized
     * tabs overlay takes the back first, then the full-screen viewers. {@code invoked == false} is
     * the gesture-start question ("would you consume this?"), so nothing may be dismissed then; a
     * viewer that answers false stops the page from starting its predictive-back shrink.
     */
    public boolean onBackPressed(boolean invoked) {
        if (browserTabsOverlay != null && browserTabsOverlay.isOpen) {
            if (invoked) browserTabsOverlay.onBackPressed();
            return false;
        }
        // Telegram-only consumers of upstream's list (passcode, secret media, the debug drawer)
        // are absent here; every viewer this client can show is asked, in upstream's own order
        // (ContentPreviewViewer, PhotoViewer, ArticleViewer).
        if (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible()) {
            if (invoked) ContentPreviewViewer.getInstance().closeWithMenu();
            return false;
        }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            if (invoked) PhotoViewer.getInstance().closePhoto(true, false);
            return false;
        }
        if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            if (invoked) ArticleViewer.getInstance().close(true, false);
            return false;
        }
        return true;
    }

    /**
     * The way upstream's non-tablet branch leaves the app (LaunchActivity:8694-8714): closing the
     * last fragment is {@code finish()}, not an empty layout. The back callback reaches this
     * through {@code ActionBarLayout.onBackInvoked() -> onBackPressed() -> closeLastFragment()},
     * so at the root the gesture hands the back to the system's own finish - and the system's
     * back-to-home animation - instead of a pop this shell has nothing to reveal behind.
     */
    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (layout.getFragmentStack().size() <= 1) {
            finish();
            return false;
        }
        return true;
    }
}
