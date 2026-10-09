package io.github.baba983.nativedo.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.SettingsActivity.SettingCell;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoActivity;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.bookmarks.BookmarksFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;

/**
 * linux.do's settings: what the reader changes about the app, drawn the way upstream's own settings
 * screen draws itself.
 *
 * <p>Upstream's {@code SettingsActivity} cannot be this screen - its rows read Telegram's account,
 * suggestions, stars and attach-menu bots through {@code MessagesController} - but its skeleton can:
 * a {@code UniversalRecyclerView} with a custom avatar header on top
 * ({@code asCustomShadow(topView, 200 - 12)}), section headers, {@code SettingCell} rows with the
 * gradient icon squares from {@code IconBackgroundColors}, and a check row for the browser toggle
 * exactly as {@code WebBrowserSettings} writes it. Every existing entry is kept, regrouped the way
 * Telegram groups its settings.</p>
 */
public class SettingsFragment extends BaseFragment implements NativeDoMainTabsActivity.TabPage {

    private static final int ROW_THEME = 1;
    private static final int ROW_OPEN_IN_APP = 2;
    private static final int ROW_BOOKMARKS = 3;
    private static final int ROW_VERIFY = 4;
    private static final int ROW_DEVICE_LOGIN = 5;
    private static final int ROW_SIGN_OUT = 6;
    private static final int ROW_NOTIFICATIONS = 7;
    private static final int ROW_PRIVACY = 8;
    private static final int ROW_STORAGE = 9;
    private static final int ROW_POWER = 10;
    private static final int ROW_HELP_ABOUT = 11;
    private static final int ROW_HELP_FAQ = 12;
    private static final int ROW_HELP_TOS = 13;
    private static final int ROW_HELP_PRIVACY = 14;
    /** Signing in, offered in the account section whenever the session is not usable. */
    private static final int ROW_SIGN_IN = 15;
    /** The reader's private messages: the inbox, the sent box and the unread ones. */
    private static final int ROW_MESSAGES = 16;

    /** Upstream's header block: a 120dp avatar container and the two titles, 200dp minus the 12dp
     * the list adds above it ({@code SettingsActivity.fillItems}'s {@code asCustomShadow(topView,
     * 200 - 12)}). */
    private static final int HEADER_HEIGHT_DP = 200 - 12;

    private AppContainer container;
    private String username = "";
    private String displayName = "";
    private String avatarTemplate = "";
    /** The reader's trust level, once `/u/{username}.json` has answered; -1 until then. */
    private int trustLevel = -1;

    /** Whether the session is usable: the account section and the header read from this. */
    private boolean authenticated;
    /** The session value the screen last acted on, so a change can be told from a repeat. */
    private AppSessionState lastSessionState;
    /** The state the header shows while the session is not usable; null while authenticated. */
    private SessionUiState accountStateUi;
    /** The session-state observer; cancelled with the views and started again with the next ones. */
    private kotlinx.coroutines.Job sessionObserver;
    /**
     * Whether [createView]'s views are the live ones. The recovery WebView's completion callback can
     * fire after this screen's views are gone (a destroyed tab), and it must not dereference or
     * re-read onto them; the observer is dropped with the views for the same reason.
     */
    private boolean viewsAlive;

    /**
     * The debug-only dev-tools section, or {@code null} in a release build (see {@link DebugTools}).
     * It is a field so the rows and their taps are built from one place.
     */
    private final DebugTools debugTools = DebugTools.load();

    private FrameLayout contentView;
    private UniversalRecyclerView listView;
    private View actionBarBackground;
    private FrameLayout topView;
    private BackupImageView avatarView;
    private AvatarDrawable avatarDrawable;
    private TextView titleView;
    private TextView subtitleView;
    private TextView versionView;
    private TLRPC.User avatarUser;
    private int bottomInset;

    private ValueAnimator actionBarVisibleAnimator;
    private boolean actionBarVisible;

    /**
     * The day/night switch recolours this page in place (its views are not rebuilt when the tab
     * host is the only fragment on the stack; see NativeDoActivity's theme handler): the rows are
     * built again the way {@code SettingsActivity} builds them, and the bar and its background
     * repaint.
     */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
                if (contentView != null) {
                    contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(false);
                }
                if (actionBarBackground != null) {
                    actionBarBackground.invalidate();
                }
                if (actionBar != null) {
                    actionBar.updateColors();
                    actionBar.invalidate();
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            };

    /** The tab host's strip height, added to the navigation bar's own so the last row clears both. */
    private int additionNavigationBarHeight;

    /** The tab host builds its pages with the bundle that says a strip is under them. */
    public SettingsFragment(Bundle args) {
        super(args);
    }

    public SettingsFragment() {
        super();
    }

    @Override
    protected View createView(Context context) {
        // The page's own strip location: its bar belongs to its screen, and the tab host's glass
        // strip floats over the pages, never under them.
        final boolean hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;

        container = NativeDo.container(getParentActivity());
        // The account the header and the account section name, taken from the session before the
        // views are built; the observer keeps it truthful when the session changes while the tab is
        // open.
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        applySession(lastSessionState);

        // Upstream's settings action bar floats over the list and its title only appears once the
        // header scrolls under it (SettingsActivity.updateActionBarVisible). The title view is
        // created inside the titles container only if the container exists first, so it is asked
        // for before the title is set, exactly as SettingsActivity's createView orders it.
        actionBar.setAllowOverlayTitle(true);
        actionBar.setUseContainerForTitles();
        actionBar.setTitle("设置");
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackground(null);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new UniversalRecyclerView(context, 0, 0, false,
                (items, adapter) -> fillItems(items),
                (item, view, position, x, y) -> onClick(item),
                (item, view, position, x, y) -> false,
                null);
        listView.setSections();
        // Upstream's own list starts under the status bar; the 12dp is what its header shadow item
        // is offset by.
        listView.setPadding(0, AndroidUtilities.statusBarHeight + AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateActionBarVisible(false, true);
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        // Upstream paints the bar as a blurred scrim; the shell's list is opaque, so the same rect
        // is filled with the bar color and the layout's own header shadow goes under it.
        actionBarBackground = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                AndroidUtilities.rectTmp2.set(0, 0, getMeasuredWidth(), actionBar.getHeight());
                paint.setColor(Theme.getColor(Theme.key_actionBarDefault));
                canvas.drawRect(AndroidUtilities.rectTmp2, paint);
                if (getParentLayout() != null) {
                    getParentLayout().drawHeaderShadow(canvas, actionBar.getHeight());
                }
            }
        };
        actionBarBackground.setAlpha(0f);
        contentView.addView(actionBarBackground, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));
        contentView.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        buildHeader(context);
        // Upstream's version footer (SettingsActivity's versionView): the same 14dp gray line the
        // Telegram settings screen ends with, carrying this app's own version.
        versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        versionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        versionView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        versionView.setGravity(Gravity.CENTER);
        versionView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        versionView.setText(appVersionName());
        // The adapter filled its list before the header existed (the list is built first, as
        // upstream builds it); this is the update that hands it the header, the way upstream
        // SettingsActivity re-runs its adapter at the end of createView.
        listView.adapter.update(false);

        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            bottomInset = AndroidUtilities.getDefaultWindowInsets(insets, false).bottom;
            checkUi_listPadding();
            return WindowInsetsCompat.CONSUMED;
        });

        fragmentView = contentView;
        updateActionBarVisible(true, false);
        ViewCompat.requestApplyInsets(contentView);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        observeSession();
        viewsAlive = true;
        return contentView;
    }

    /**
     * The list runs to the window's bottom and under the glass strip; the bottom padding holds the
     * last row above the navigation bar and the strip, the way the topic list's own does.
     */
    private void checkUi_listPadding() {
        if (listView != null) {
            listView.setPadding(0, listView.getPaddingTop(), 0,
                    AndroidUtilities.dp(4) + bottomInset + additionNavigationBarHeight);
        }
    }

    /** A second tap on the reader's tab: the list goes back to its top. */
    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    /**
     * The rows are read again whenever this page comes back to the front, the way upstream's own
     * settings pages re-run their adapters in {@code onResume}: a choice can also be flipped in a
     * hosted screen this page pushed (the appearance page's In-App Browser row writes the same
     * stored choice as the 链接 row here), and the row must not keep showing the old state.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    // ------------------------------------------------------------------ session state

    /**
     * The tab host rebuilds a page's views (a theme switch) without destroying the fragment: the
     * observer belongs to the views, so it is dropped here and started again from
     * {@link #createView}.
     */
    @Override
    public void clearViews() {
        viewsAlive = false;
        stopSessionObservation();
        super.clearViews();
    }

    private void observeSession() {
        stopSessionObservation();
        sessionObserver = SessionUi.observe(container, state -> {
            onSessionState(state);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void stopSessionObservation() {
        if (sessionObserver != null) {
            SessionUi.cancel(sessionObserver);
            sessionObserver = null;
        }
    }

    /**
     * A new session value. The first one for this view was already applied in {@link #createView},
     * so a repeated value changes nothing. A different one re-derives the account the header names
     * and re-reads its trust level: the rows are rebuilt so the account section never claims a
     * session that is gone, and an account that changed is not kept on screen.
     */
    private void onSessionState(AppSessionState state) {
        if (SessionUi.identity(lastSessionState).equals(SessionUi.identity(state))) {
            return;
        }
        lastSessionState = state;
        applySession(state);
        refreshHeader();
        if (authenticated) {
            fetchTrustLevel();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    /**
     * The identity the header and the account section name, from the session's own snapshot. An
     * expired session may keep the last snapshot (the header still knows whose account it was), but
     * the account is no longer usable.
     */
    private void applySession(AppSessionState state) {
        SessionSnapshot snapshot = null;
        if (state instanceof AppSessionState.Authenticated) {
            snapshot = ((AppSessionState.Authenticated) state).getSnapshot();
        } else if (state instanceof AppSessionState.Expired) {
            snapshot = ((AppSessionState.Expired) state).getSnapshot();
        } else if (state instanceof AppSessionState.Restoring) {
            snapshot = ((AppSessionState.Restoring) state).getSnapshot();
        }
        authenticated = state instanceof AppSessionState.Authenticated;
        accountStateUi = SessionUi.stateFor(state);
        username = snapshot != null && snapshot.getUsername() != null ? snapshot.getUsername() : "";
        displayName = snapshot != null && snapshot.getDisplayName() != null ? snapshot.getDisplayName() : "";
        avatarTemplate = snapshot != null && snapshot.getAvatarTemplate() != null ? snapshot.getAvatarTemplate() : "";
        username = authenticated ? username : "";
        displayName = authenticated ? displayName : "";
        avatarTemplate = authenticated ? avatarTemplate : "";
    }

    /** Re-derives the header's title, subtitle and picture from the account just applied. */
    private void refreshHeader() {
        if (titleView != null) {
            String title = authenticated
                    ? (displayName.isEmpty() ? username : displayName)
                    : "未登录";
            titleView.setText(title);
        }
        if (subtitleView != null) {
            subtitleView.setText(accountSubtitle());
        }
        if (container != null) {
            avatarUser = DiscourseAvatars.INSTANCE.user(
                    username, displayName, avatarTemplate, container.getBaseUrl());
        }
        if (avatarDrawable != null && avatarUser != null) {
            avatarDrawable.setInfo(avatarUser);
        }
        bindAvatar();
        if (authenticated) {
            fetchAvatar();
        }
    }

    /**
     * A recovery/login completion ran: the observer will have applied the new session, but the
     * header is rebuilt here as well so the screen is truthful even before the next emission. A
     * completion that lands after this screen's views are gone changes nothing here - the observer
     * belonged to those views, and the next [createView] applies the session it finds.
     */
    private void onSessionRecovered() {
        if (!viewsAlive || container == null) {
            return;
        }
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        applySession(lastSessionState);
        refreshHeader();
        if (authenticated) {
            fetchTrustLevel();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /** The account section's login action, or the recovery action, behind one WebView. */
    private void openSessionWebView(boolean verification) {
        if (getParentActivity() == null) {
            return;
        }
        LoginWebViewFragment[] holder = new LoginWebViewFragment[1];
        holder[0] = new LoginWebViewFragment(() -> {
            holder[0].finishFragment();
            onSessionRecovered();
        }, verification);
        presentFragment(holder[0]);
    }

    /** The page reaches the bottom of the window itself; the shell must not reserve the bar. */
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    /**
     * Upstream's header, with its own constants: a 120dp container 23-12dp from the top, a 90dp
     * round picture 15dp into it, the 22sp bold title at 138.333-12dp and the 13sp subtitle at
     * 168-12dp ({@code SettingsActivity.createView}). Telegram's camera button, avatar progress
     * and version row have nothing behind them here and are left out.
     */
    private void buildHeader(Context context) {
        topView = new FrameLayout(context);

        FrameLayout avatarContainer = new FrameLayout(context);
        topView.addView(avatarContainer, LayoutHelper.createFrame(
                120, 120, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 23 - 12, 0, 0));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(90));
        avatarContainer.addView(avatarView, LayoutHelper.createFrame(
                90, 90, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(authenticated ? (displayName.isEmpty() ? username : displayName) : "未登录");
        topView.addView(titleView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 138.333f - 12, 16, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(accountSubtitle());
        topView.addView(subtitleView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 168 - 12, 0, 0));

        avatarDrawable = new AvatarDrawable();
        avatarUser = DiscourseAvatars.INSTANCE.user(
                username, displayName, avatarTemplate, container.getBaseUrl());
        avatarDrawable.setInfo(avatarUser);
        bindAvatar();
        fetchAvatar();
        fetchTrustLevel();
    }

    /** Upstream's account header line: the username, plus the reader's trust level once known. */
    private String accountSubtitle() {
        if (!authenticated) {
            return shortStateLabel(accountStateUi);
        }
        if (username.isEmpty()) {
            return "";
        }
        String subtitle = "@" + username;
        if (trustLevel >= 0) {
            subtitle += " · 信任等级 " + trustLevel;
        }
        return subtitle;
    }

    /** A one-line label for the header while the session is not usable. */
    private static String shortStateLabel(SessionUiState state) {
        if (state == null) {
            return "";
        }
        switch (state) {
            case SignIn:
                return "未登录";
            case Expired:
                return "登录已失效";
            case Challenge:
                return "需要站点验证";
            case Retry:
            case Forbidden:
                return "暂时无法验证登录";
            case Restoring:
            default:
                return "正在同步登录状态";
        }
    }

    /**
     * The trust level is not in the session snapshot, so the reader's own profile payload is read
     * once for it. A failed read leaves the header as it was; the number is decoration, not state.
     */
    private void fetchTrustLevel() {
        if (container == null || !authenticated || username.isEmpty()) {
            return;
        }
        final String asked = username;
        SettingsLoader.INSTANCE.trustLevel(container, asked, level -> {
            if (!asked.equals(username)) {
                return kotlin.Unit.INSTANCE;
            }
            trustLevel = level;
            if (subtitleView != null) {
                subtitleView.setText(accountSubtitle());
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The header's picture once the site's bytes are on disk; the initials stand in until then. */
    private void bindAvatar() {
        if (avatarView == null || avatarUser == null) {
            return;
        }
        ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
        if (avatar != null) {
            avatarView.setImage(avatar, "90_90", avatarDrawable, avatarUser);
        }
    }

    private void fetchAvatar() {
        String url = DiscourseAvatars.INSTANCE.url(avatarTemplate, container.getBaseUrl(),
                DiscourseAvatars.DEFAULT_SIZE);
        if (url != null && avatarUser != null && !DiscourseAvatars.INSTANCE.isCached(avatarUser)) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, avatarUser, () -> {
                bindAvatar();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * The rows, in upstream's order and with upstream's own icon set and gradient squares: 外观
     * (upstream's chat settings), 链接 (the in-app browser choice, drawn as
     * {@code WebBrowserSettings} draws it), 隐私 and 通知 (the reader's linux.do options, which
     * upstream's account-driven pages cannot serve here), 数据与存储 (the caches this app fills),
     * 省电 (upstream's {@code LiteModeSettingsActivity}), 我的 (the bookmarks), 账号 (the
     * verification page, the device-code login and leaving the account) and upstream's help
     * footer, whose rows point at linux.do's own pages.
     */
    private void fillItems(ArrayList<UItem> items) {
        items.add(UItem.asCustomShadow(topView, HEADER_HEIGHT_DP));

        items.add(UItem.asHeader("外观"));
        items.add(SettingCell.Factory.of(ROW_THEME,
                IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom,
                org.telegram.messenger.R.drawable.settings_chat, "主题", "配色与夜间模式"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("链接"));
        items.add(UItem.asRippleCheck(ROW_OPEN_IN_APP, "在应用内打开链接")
                .setChecked(LinkOpenSettings.INSTANCE.isInApp(getContext())));
        items.add(UItem.asShadow("关闭后普通链接交给系统的自定义标签页；本站话题链接仍在本应用内打开。"));

        items.add(UItem.asHeader("隐私"));
        items.add(SettingCell.Factory.of(ROW_PRIVACY,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                org.telegram.messenger.R.drawable.settings_privacy, "隐私", "资料可见性与私信"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("通知"));
        items.add(SettingCell.Factory.of(ROW_NOTIFICATIONS,
                IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom,
                org.telegram.messenger.R.drawable.settings_sounds, "通知", "点赞、提及与跟踪"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("数据与存储"));
        items.add(SettingCell.Factory.of(ROW_STORAGE,
                IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom,
                org.telegram.messenger.R.drawable.settings_data, "存储使用", "缓存占用与清理"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("省电"));
        items.add(SettingCell.Factory.of(ROW_POWER,
                IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom,
                org.telegram.messenger.R.drawable.settings_power, "省电模式", "模糊与动画的取舍"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("我的"));
        items.add(SettingCell.Factory.of(ROW_BOOKMARKS,
                IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom,
                org.telegram.messenger.R.drawable.outline_saved_24, "书签", "收藏的话题"));
        items.add(SettingCell.Factory.of(ROW_MESSAGES,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                org.telegram.messenger.R.drawable.settings_devices, "私信", "收件箱、已发送与未读"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("账号"));
        if (authenticated) {
            items.add(SettingCell.Factory.of(ROW_VERIFY,
                    IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                    org.telegram.messenger.R.drawable.settings_privacy, "打开验证页", "重新通过站点的验证"));
            items.add(SettingCell.Factory.of(ROW_DEVICE_LOGIN,
                    IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                    org.telegram.messenger.R.drawable.settings_devices, "设备码登录（实验）", "在站内浏览器里确认授权码"));
            items.add(UItem.asButton(ROW_SIGN_OUT, "退出登录").red());
        } else {
            // The session is not usable - signed out, expired or challenged. The account row is
            // honest about that and offers the only thing that helps: signing in at the site.
            items.add(UItem.asButton(ROW_SIGN_IN, "登录 linux.do"));
            items.add(SettingCell.Factory.of(ROW_DEVICE_LOGIN,
                    IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                    org.telegram.messenger.R.drawable.settings_devices, "设备码登录（实验）", "在站内浏览器里确认授权码"));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("帮助"));
        items.add(SettingCell.Factory.of(ROW_HELP_ABOUT,
                IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom,
                org.telegram.messenger.R.drawable.settings_ask, "关于 linux.do"));
        items.add(SettingCell.Factory.of(ROW_HELP_FAQ,
                IconBackgroundColors.BLUE_LIGHT.top, IconBackgroundColors.BLUE_LIGHT.bottom,
                org.telegram.messenger.R.drawable.settings_faq, "常见问题"));
        items.add(SettingCell.Factory.of(ROW_HELP_TOS,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                org.telegram.messenger.R.drawable.settings_features, "服务条款"));
        items.add(SettingCell.Factory.of(ROW_HELP_PRIVACY,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                org.telegram.messenger.R.drawable.settings_policy, "隐私政策"));
        items.add(UItem.asShadow(null));

        // The dev-tools section is only there in a debug build; in release debugTools is null and
        // nothing is added.
        if (debugTools != null && debugTools.isAvailable()) {
            debugTools.fillItems(items);
        }

        items.add(UItem.asCustomShadow(versionView));
    }

    /** The installed app's own version, the way upstream's footer names its build. */
    private static String appVersionName() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(
                    ApplicationLoader.applicationContext.getPackageName(), 0);
            return "NativeDo v" + info.versionName + " (" + info.versionCode + ")";
        } catch (Exception e) {
            return "NativeDo";
        }
    }

    private void onClick(UItem item) {
        // The debug rows own their ids; a tap they handle ends here.
        if (debugTools != null && debugTools.onItemClick(item.id, this)) {
            return;
        }
        if (item.id == ROW_THEME) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (item.id == ROW_OPEN_IN_APP) {
            // The reader's choice; the browser hook reads the same preference on the next link.
            LinkOpenSettings.INSTANCE.setInApp(getContext(), !LinkOpenSettings.INSTANCE.isInApp(getContext()));
            listView.adapter.update(true);
        } else if (item.id == ROW_PRIVACY) {
            presentFragment(new PreferencesFragment(PreferencesFragment.MODE_PRIVACY));
        } else if (item.id == ROW_NOTIFICATIONS) {
            presentFragment(new PreferencesFragment(PreferencesFragment.MODE_NOTIFICATIONS));
        } else if (item.id == ROW_STORAGE) {
            presentFragment(new StorageFragment());
        } else if (item.id == ROW_POWER) {
            // Upstream's own power-saving page: its flags are what this shell's blur, liquid glass
            // and list compression read (SharedConfig/LiteMode), so the page changes this client.
            presentFragment(new LiteModeSettingsActivity());
        } else if (item.id == ROW_BOOKMARKS) {
            presentFragment(new BookmarksFragment());
        } else if (item.id == ROW_MESSAGES) {
            presentFragment(new io.github.baba983.nativedo.messages.PrivateMessagesFragment());
        } else if (item.id == ROW_VERIFY) {
            openSessionWebView(true);
        } else if (item.id == ROW_SIGN_IN) {
            // A challenged session recovers on the site's challenge page; a signed-out or expired
            // one signs in on the login page.
            openSessionWebView(accountStateUi == SessionUiState.Challenge);
        } else if (item.id == ROW_DEVICE_LOGIN) {
            presentFragment(new io.github.baba983.nativedo.auth.device.DeviceLoginFragment());
        } else if (item.id == ROW_SIGN_OUT) {
            confirmSignOut();
        } else if (item.id == ROW_HELP_ABOUT) {
            openSitePage("/about");
        } else if (item.id == ROW_HELP_FAQ) {
            openSitePage("/faq");
        } else if (item.id == ROW_HELP_TOS) {
            openSitePage("/tos");
        } else if (item.id == ROW_HELP_PRIVACY) {
            openSitePage("/privacy");
        }
    }

    /** A linux.do page opened in the app's own browser, the way upstream opens its help pages. */
    private void openSitePage(String path) {
        if (getParentActivity() == null || container == null) {
            return;
        }
        Browser.openUrl(getParentActivity(), container.getBaseUrl() + path.substring(1));
    }

    /**
     * Upstream's fade (SettingsActivity.updateActionBarVisible): the title fades in only once the
     * list has scrolled past the head, and the bar is transparent while the head is in view.
     */
    private void updateActionBarVisible(boolean force, boolean animated) {
        final boolean visible;
        if (listView.getChildCount() > 0) {
            final View firstChild = listView.getChildAt(0);
            visible = listView.getChildAdapterPosition(firstChild) > 0
                    || firstChild.getY() + firstChild.getHeight() < actionBar.getHeight();
        } else {
            visible = false;
        }
        if (actionBarVisible == visible && !force) {
            return;
        }
        actionBarVisible = visible;
        if (actionBarVisibleAnimator != null) {
            actionBarVisibleAnimator.cancel();
            actionBarVisibleAnimator = null;
        }
        if (!animated) {
            actionBar.getTitlesContainer().setAlpha(visible ? 1.0f : 0.0f);
            actionBarBackground.setAlpha(visible ? 1.0f : 0.0f);
        } else {
            actionBarVisibleAnimator = ValueAnimator.ofFloat(
                    actionBar.getTitlesContainer().getAlpha(), visible ? 1.0f : 0.0f);
            actionBarVisibleAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                actionBar.getTitlesContainer().setAlpha(t);
                actionBarBackground.setAlpha(t);
            });
            actionBarVisibleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarVisibleAnimator.setDuration(420);
            actionBarVisibleAnimator.start();
        }
    }

    /**
     * Leaving the account is not something to do by a stray tap, so it asks - and the site is told
     * before the screen changes, because the cookies are what the app's session is made of: opening
     * the login page before they are gone would open it with the reader still signed in.
     */
    private void confirmSignOut() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("退出登录");
        builder.setMessage("将清除这台设备上的登录状态，回到登录页。");
        builder.setPositiveButton("退出登录", (dialog, which) -> {
            SettingsLoader.INSTANCE.signOut(container, () -> {
                if (getParentActivity() instanceof NativeDoActivity) {
                    ((NativeDoActivity) getParentActivity()).signedOut();
                }
                return kotlin.Unit.INSTANCE;
            });
        });
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    @Override
    public void onFragmentDestroy() {
        viewsAlive = false;
        if (actionBarVisibleAnimator != null) {
            actionBarVisibleAnimator.cancel();
            actionBarVisibleAnimator = null;
        }
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        stopSessionObservation();
        super.onFragmentDestroy();
    }
}
