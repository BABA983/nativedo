package io.github.baba983.nativedo.settings;

import android.animation.ValueAnimator;
import android.content.Context;
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
import org.telegram.messenger.ImageLocation;
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
import org.telegram.ui.SettingsActivity.SettingCell;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoActivity;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.bookmarks.BookmarksFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
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

    /** Upstream's header block: a 120dp avatar container and the two titles, 200dp minus the 12dp
     * the list adds above it ({@code SettingsActivity.fillItems}'s {@code asCustomShadow(topView,
     * 200 - 12)}). */
    private static final int HEADER_HEIGHT_DP = 200 - 12;

    private AppContainer container;
    private String username = "";
    private String displayName = "";
    private String avatarTemplate = "";

    private FrameLayout contentView;
    private UniversalRecyclerView listView;
    private View actionBarBackground;
    private FrameLayout topView;
    private BackupImageView avatarView;
    private AvatarDrawable avatarDrawable;
    private TextView titleView;
    private TextView subtitleView;
    private TLRPC.User avatarUser;
    private int bottomInset;

    private ValueAnimator actionBarVisibleAnimator;
    private boolean actionBarVisible;

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
        SessionSnapshot snapshot = container.getSessionStore().snapshot();
        if (snapshot != null) {
            if (snapshot.getUsername() != null) {
                username = snapshot.getUsername();
            }
            if (snapshot.getDisplayName() != null) {
                displayName = snapshot.getDisplayName();
            }
            if (snapshot.getAvatarTemplate() != null) {
                avatarTemplate = snapshot.getAvatarTemplate();
            }
        }

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
        titleView.setText(displayName.isEmpty() ? username : displayName);
        topView.addView(titleView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 16, 138.333f - 12, 16, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(username.isEmpty() ? "" : "@" + username);
        topView.addView(subtitleView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 168 - 12, 0, 0));

        avatarDrawable = new AvatarDrawable();
        avatarUser = DiscourseAvatars.INSTANCE.user(
                username, displayName, avatarTemplate, container.getBaseUrl());
        avatarDrawable.setInfo(avatarUser);
        bindAvatar();
        fetchAvatar();
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
     * The rows, in Telegram's grouping: the avatar header, then 外观 (upstream's own appearance
     * page), 链接 (the in-app browser choice, drawn as {@code WebBrowserSettings} draws it), 我的
     * (the bookmarks) and 账号 (the verification page and leaving the account). The old 账号 row
     * that opened the reader's profile is gone: the profile is the 我的 tab of the shell now, and
     * this page's header already is the account.
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

        items.add(UItem.asHeader("我的"));
        items.add(SettingCell.Factory.of(ROW_BOOKMARKS,
                IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom,
                org.telegram.messenger.R.drawable.outline_saved_24, "书签", "收藏的话题"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("账号"));
        items.add(SettingCell.Factory.of(ROW_VERIFY,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                org.telegram.messenger.R.drawable.settings_privacy, "打开验证页", "重新通过站点的验证"));
        items.add(SettingCell.Factory.of(ROW_DEVICE_LOGIN,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                org.telegram.messenger.R.drawable.settings_devices, "设备码登录（实验）", "在站内浏览器里确认授权码"));
        items.add(UItem.asButton(ROW_SIGN_OUT, "退出登录").red());
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item) {
        if (item.id == ROW_THEME) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (item.id == ROW_OPEN_IN_APP) {
            // The reader's choice; the browser hook reads the same preference on the next link.
            LinkOpenSettings.INSTANCE.setInApp(getContext(), !LinkOpenSettings.INSTANCE.isInApp(getContext()));
            listView.adapter.update(true);
        } else if (item.id == ROW_BOOKMARKS) {
            presentFragment(new BookmarksFragment());
        } else if (item.id == ROW_VERIFY) {
            presentFragment(new LoginWebViewFragment(null, true));
        } else if (item.id == ROW_DEVICE_LOGIN) {
            presentFragment(new io.github.baba983.nativedo.auth.device.DeviceLoginFragment());
        } else if (item.id == ROW_SIGN_OUT) {
            confirmSignOut();
        }
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
        if (actionBarVisibleAnimator != null) {
            actionBarVisibleAnimator.cancel();
            actionBarVisibleAnimator = null;
        }
        super.onFragmentDestroy();
    }
}
