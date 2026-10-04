package io.github.baba983.nativedo.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ThemeActivity;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoActivity;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.bookmarks.BookmarksFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.profile.ProfileFragment;

/**
 * linux.do's settings: what the reader changes about the app, and the reader's own account.
 *
 * <p>Upstream's appearance page is still where the look is chosen - it is the page that knows the
 * themes - so this screen is the way in to it rather than a second theme list. The rows are the
 * cells upstream's own settings screen is made of ({@code HeaderCell}, {@code TextSettingsCell}),
 * so a list of settings looks like upstream's list of settings without upstream's Telegram account
 * model underneath it.</p>
 */
public class SettingsFragment extends BaseFragment implements NativeDoMainTabsActivity.TabPage {

    private static final int ROW_APPEARANCE_HEADER = 0;
    private static final int ROW_THEME = 1;
    private static final int ROW_MINE_HEADER = 2;
    private static final int ROW_BOOKMARKS = 3;
    private static final int ROW_ACCOUNT_HEADER = 4;
    private static final int ROW_ACCOUNT = 5;
    private static final int ROW_VERIFY = 6;
    private static final int ROW_SIGN_OUT = 7;
    private static final int ROW_COUNT = 8;

    private AppContainer container;
    private String username = "";
    private String displayName = "";

    private RecyclerListView listView;
    private int navigationBarHeight;
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
        final boolean hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;

        actionBar.setTitle("设置");
        actionBar.setAllowOverlayTitle(true);
        // The page carries its own bar: inside the tab host the bar belongs to the page's screen,
        // and the strip (a sibling of the pager) draws over the pages, never under them.
        actionBar.setAddToContainer(false);

        container = NativeDo.container(getParentActivity());
        SessionSnapshot snapshot = container.getSessionStore().snapshot();
        if (snapshot != null && snapshot.getUsername() != null) {
            username = snapshot.getUsername();
        }
        if (snapshot != null && snapshot.getDisplayName() != null) {
            displayName = snapshot.getDisplayName();
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new SettingsAdapter());
        listView.setVerticalScrollBarEnabled(false);
        // The list reaches the bottom of the window and its rows scroll behind the glass strip;
        // the last row stays reachable above it through the padding below.
        listView.setClipToPadding(false);
        checkUi_listPadding();
        listView.setOnItemClickListener((view, position) -> {
            if (position == ROW_THEME) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            } else if (position == ROW_BOOKMARKS) {
                presentFragment(new BookmarksFragment());
            } else if (position == ROW_ACCOUNT) {
                presentFragment(ProfileFragment.forUser(username, displayName));
            } else if (position == ROW_VERIFY) {
                presentFragment(new LoginWebViewFragment(null, true));
            } else if (position == ROW_SIGN_OUT) {
                confirmSignOut();
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        // The page's own action bar sits above the content; checkUi_contentTop() moves the content
        // below it once it has been measured.
        frame.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = frame;
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        actionBar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> checkUi_contentTop());
        checkUi_contentTop();
        return fragmentView;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
    }

    private void checkUi_listPadding() {
        if (listView != null) {
            listView.setPadding(0, 0, 0, navigationBarHeight + additionNavigationBarHeight);
        }
    }

    /** Keeps the page's content below its own action bar, including after a status bar change. */
    private void checkUi_contentTop() {
        final int top = actionBar != null ? actionBar.getMeasuredHeight() : 0;
        if (top <= 0 || listView == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) listView.getLayoutParams();
        if (params != null && params.topMargin != top) {
            params.topMargin = top;
            listView.setLayoutParams(params);
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

    /** The rows upstream's own settings screen draws its entries with. */
    private class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = viewType == 0 ? new HeaderCell(getParentActivity()) : new TextSettingsCell(getParentActivity());
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof HeaderCell) {
                ((HeaderCell) holder.itemView).setText(headerAt(position));
                return;
            }
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            if (position == ROW_THEME) {
                cell.setText("主题", true);
            } else if (position == ROW_BOOKMARKS) {
                cell.setText("书签", true);
            } else if (position == ROW_ACCOUNT) {
                cell.setTextAndValue("已登录", username.isEmpty() ? "" : "@" + username, true);
            } else if (position == ROW_VERIFY) {
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                cell.setText(getParentActivity().getString(io.github.baba983.nativedo.R.string.reply_failed_verify), true);
            } else if (position == ROW_SIGN_OUT) {
                cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                cell.setText("退出登录", false);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return isHeader(position) ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT;
        }

        private boolean isHeader(int position) {
            return position == ROW_APPEARANCE_HEADER
                    || position == ROW_MINE_HEADER
                    || position == ROW_ACCOUNT_HEADER;
        }

        private CharSequence headerAt(int position) {
            if (position == ROW_APPEARANCE_HEADER) {
                return "外观";
            }
            if (position == ROW_MINE_HEADER) {
                return "我的";
            }
            return "账号";
        }
    }
}
