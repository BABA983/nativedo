package io.github.baba983.nativedo.messages;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.chat.PublicChannelsFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.feature.messages.data.PrivateMessageBox;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.NewTopicFragment;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The reader's private messages, the way the site lists them: an inbox, a sent box and the unread
 * ones. Each is the standard {@code topic_list} envelope behind its own
 * {@code topics/private-messages*} route, so a private message is a topic and its row is the same
 * {@link DialogCell} a public topic list draws - the site's own excerpt, date and unread badge.
 *
 * <p>Nothing upstream can be a whole screen here: Telegram's own message screens read accounts,
 * chats and sessions this client does not have. What is reused is the row, the shimmer that stands
 * in for rows that have not arrived ({@link FlickerLoadingView}, DialogsActivity's own), and the
 * session-state rows the other native screens show.</p>
 *
 * <p>A private message is opened with the topic screen - the same {@link TopicFragment} public
 * topics use - because a private message <em>is</em> a topic. Starting one is the compose button
 * (a {@link FragmentFloatingButton}), in the composer's private-message mode.</p>
 *
 * <p>The three boxes are Telegram's own horizontal tab strip ({@link ScrollSlidingTextTabStrip},
 * the component the profile's media pages put under their bar): a tap moves the strip's indicator
 * and reads that box's route, so the box on screen is the tab under the indicator. The strip's
 * colours come from the theme keys, so the day/night switch recolours it in place. The public
 * channels live behind their own screen and are offered from the overflow menu.</p>
 */
public class PrivateMessagesFragment extends BaseFragment {

    private static final int TYPE_MESSAGE = 0;
    private static final int TYPE_NOTE = 1;
    /** The row a session state draws in place of the list: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 2;

    private static final int OPTIONS_ITEM = 1;

    /** The tab ids the strip carries, one per box: the tap maps straight back to a box. */
    private static final int TAB_INBOX = 0;
    private static final int TAB_SENT = 1;
    private static final int TAB_UNREAD = 2;

    /** The strip's own band under the bar: the height Telegram's tab strips reserve. */
    private static final int TAB_STRIP_HEIGHT_DP = 48;
    /**
     * The room the list keeps below its last row for the compose button: the button's own 48dp
     * plus its 14dp bottom margin, rounded up, so a row can always be scrolled clear of it.
     */
    private static final int FAB_LIST_CLEARANCE_DP = 64;

    /** How close to the end the list comes before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    private RecyclerListView listView;
    /** DialogsActivity's own shimmer (4059-4064): the rows drawn while no answer has landed. */
    private FlickerLoadingView progressView;
    /** The box strip (the profile's media tabs, SharedMediaLayout): a tap reads that box. */
    private ScrollSlidingTextTabStrip tabsView;
    /** The compose button: DialogsActivity's own floating button, bottom-right (3956, 4719). */
    private FragmentFloatingButton composeButton;
    private ActionBarMenuItem optionsItem;

    private final List<TopicSummary> items = new ArrayList<>();
    /** One author per row, so the picture is fetched once and the row re-bound when it lands. */
    private final List<TLRPC.User> authors = new ArrayList<>();
    /** The ids already listed, so a page that overlaps the last one adds no second row. */
    private final Set<Long> seenIds = new HashSet<>();

    private AppContainer container;
    private PrivateMessageBox box = PrivateMessageBox.Inbox;
    private boolean loading;
    /** Whether the box holds nothing at all, which is its own row rather than a blank list. */
    private boolean empty;
    private int page;
    /** Set when a page came back with nothing new: that is the end of the list. */
    private boolean reachedTheEnd;

    /**
     * The explicit session state the list shows in place of rows, or {@code null} while it shows
     * content. Its value survives neither an identity change nor a view recreation: both drop it.
     */
    private SessionUiState stateUi;
    /** The session value the screen last acted on, so a change can be told from a repeat. */
    private AppSessionState lastSessionState;
    /**
     * Bumped whenever the account changes or the views go away. A read captures it before it starts
     * and drops its own answer if it no longer matches, so a late page from the previous account
     * (or another box) cannot repopulate the list.
     */
    private int loadEpoch;
    /** The session-state observer; cancelled with the views and started again with the next ones. */
    private kotlinx.coroutines.Job sessionObserver;

    /**
     * The day/night switch recolours the strip, the button and the rows in place: this screen is
     * not rebuilt by the switch (the composer, the topic list and the settings page are the model
     * here), so the colours are re-read where they are painted. Registered once per fragment, with
     * the fragment's own lifecycle.
     */
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != NotificationCenter.needSetDayNightTheme && id != NotificationCenter.didSetNewTheme) {
                    return;
                }
                AndroidUtilities.runOnUIThread(this::updateColors);
            };

    private int navigationBarHeight;

    public PrivateMessagesFragment(Bundle args) {
        super(args);
    }

    public PrivateMessagesFragment() {
        super();
    }

    /** The inbox, which is where the 私信 entries open. */
    public static PrivateMessagesFragment create() {
        return new PrivateMessagesFragment();
    }

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("私信");
        actionBar.setSubtitle(boxLabel(box));
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        // The one bar action left is the overflow, where the public channels live. Starting a
        // message is the floating compose button now, the way DialogsActivity starts a chat.
        optionsItem = actionBar.createMenu().addItem(
                OPTIONS_ITEM, org.telegram.messenger.R.drawable.ic_ab_other);
        optionsItem.setContentDescription("更多");
        optionsItem.setOnClickListener(v -> showItemOptions());

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new MessageAdapter());
        // The rows are DialogCells, so the chat list's own shimmer is the matching one
        // (DialogsActivity:4059-4064, 4695): it is the list's empty view, and only until the first
        // answer lands.
        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progressView.setVisibility(View.GONE);
        listView.setEmptyView(progressView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        listView.setVerticalScrollBarEnabled(false);
        checkUi_listPadding();
        listView.setOnItemClickListener((view, position) -> {
            if (stateUi != null) {
                onStateAction();
            } else if (position >= 0 && position < items.size()) {
                open(items.get(position));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadNextPageIfNeeded();
            }
        });

        // The box strip under the bar, over the list: the same component the profile's media pages
        // place under their bar. A tap moves the indicator and reads that box's own route.
        tabsView = new ScrollSlidingTextTabStrip(context, resourceProvider);
        tabsView.setDelegate(new BoxTabsDelegate());
        buildTabs();

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(tabsView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, TAB_STRIP_HEIGHT_DP));
        column.addView(listView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f));

        FrameLayout frame = new FrameLayout(context);
        frame.addView(column, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // The shimmer rides over the list, centred, the way DialogsActivity draws its own (4062).
        frame.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        // The compose button: DialogsActivity's own floating button, bottom-right (3963, 4719).
        // Its default layout puts it 14dp above the list's bottom; checkUi_listPadding lifts it
        // over the navigation inset and reserves the matching room under the last row.
        composeButton = new FragmentFloatingButton(context, resourceProvider);
        composeButton.setImageResource(org.telegram.messenger.R.drawable.filled_fab_compose_32);
        composeButton.setContentDescription("写私信");
        composeButton.setOnClickListener(v -> presentNewMessage(null));
        frame.addView(composeButton, FragmentFloatingButton.createDefaultLayoutParams());

        fragmentView = frame;
        updateColors();
        checkUi_listPadding();
        container = NativeDo.container(getParentActivity());
        // The session is read before the list: an account with no usable session gets the explicit
        // state row (and the read is not even sent).
        stateUi = null;
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        observeSession();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            loadPage();
        }
        return fragmentView;
    }

    /**
     * The day/night observer is registered once with the fragment (not with the views): a switch
     * that happens while this screen is behind another one still has to recolour it, and the
     * callback null-checks the views it recolours.
     */
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    /**
     * The tab/view host drops a screen's views without destroying the fragment: the observer belongs
     * to the views, so it is dropped here and started again from {@link #createView}. Bumping the
     * epoch drops any read still in flight for the old views.
     */
    @Override
    public void clearViews() {
        stopSessionObservation();
        loadEpoch++;
        super.clearViews();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
        stopSessionObservation();
        loadEpoch++;
        if (progressView != null) {
            listView.setEmptyView(null);
        }
        super.onFragmentDestroy();
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
     * A new session value. The first one for this view was already acted on in {@link #createView},
     * so a value equal to it changes nothing; anything else drops the old account's rows and, when
     * the session is usable again, reads them again - immediately, so an open screen never keeps
     * showing the previous account.
     */
    private void onSessionState(AppSessionState state) {
        boolean identityChanged = !SessionUi.identity(lastSessionState).equals(SessionUi.identity(state));
        lastSessionState = state;
        if (identityChanged) {
            loadEpoch++;
            resetForReload();
        }
        if (state instanceof AppSessionState.Authenticated) {
            if (identityChanged) {
                stateUi = null;
                notifyRowsChanged();
                loadPage();
            }
        } else {
            stateUi = SessionUi.stateFor(state);
            loading = false;
            notifyRowsChanged();
        }
    }

    /** Drops every row and restarts the paging bookkeeping, so nothing of the old account stays. */
    private void resetForReload() {
        items.clear();
        authors.clear();
        seenIds.clear();
        empty = false;
        loading = false;
        page = 0;
        reachedTheEnd = false;
    }

    /** The tap on a state row: recover the session, or read again. */
    private void onStateAction() {
        if (stateUi == null) {
            return;
        }
        SessionUiAction action = SessionUi.action(stateUi);
        if (action == SessionUiAction.Retry) {
            stateUi = null;
            resetForReload();
            notifyRowsChanged();
            loadPage();
            return;
        }
        openRecovery(action == SessionUiAction.OpenVerification);
    }

    /**
     * The session is dealt with where the site can be: the recovery flow in the login WebView. The
     * WebView's own completion publishes the usable session; this screen's observer re-reads when
     * that changes the account, and the explicit completion re-reads it when it does not.
     */
    private void openRecovery(boolean verification) {
        if (getParentActivity() == null) {
            return;
        }
        final String identityAtStart = SessionUi.identity(lastSessionState);
        LoginWebViewFragment[] holder = new LoginWebViewFragment[1];
        holder[0] = new LoginWebViewFragment(() -> {
            holder[0].finishFragment();
            onRecoveryCompleted(identityAtStart);
        }, verification);
        presentFragment(holder[0]);
    }

    /**
     * The recovery WebView's explicit completion ran. A completion that changed the account is
     * already re-read by {@link #onSessionState}; this reads only the same-account case, exactly
     * once, and never writes the session.
     */
    private void onRecoveryCompleted(String identityAtStart) {
        if (container == null) {
            return;
        }
        AppSessionState now = container.getSessionCoordinator().getSessionState().getValue();
        if (!SessionUi.identity(now).equals(identityAtStart)) {
            return;
        }
        lastSessionState = now;
        loadEpoch++;
        stateUi = null;
        resetForReload();
        notifyRowsChanged();
        loadPage();
    }

    /** The overflow menu: which box is shown (checked) and the public channels screen. */
    private void showItemOptions() {
        if (optionsItem == null) {
            return;
        }
        ItemOptions io = ItemOptions.makeOptions(this, optionsItem);
        io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
        io.setDimAlpha(0x08);
        // The boxes ride the strip, but the menu keeps the same choice and marks the one shown, so
        // the existing flow keeps working and the current box is legible either way.
        io.addChecked(box == PrivateMessageBox.Inbox,
                org.telegram.messenger.R.drawable.settings_devices, boxLabel(PrivateMessageBox.Inbox),
                () -> selectBox(PrivateMessageBox.Inbox));
        io.addChecked(box == PrivateMessageBox.Sent,
                org.telegram.messenger.R.drawable.outline_saved_24, boxLabel(PrivateMessageBox.Sent),
                () -> selectBox(PrivateMessageBox.Sent));
        io.addChecked(box == PrivateMessageBox.Unread,
                org.telegram.messenger.R.drawable.settings_sounds, boxLabel(PrivateMessageBox.Unread),
                () -> selectBox(PrivateMessageBox.Unread));
        io.addGap();
        io.add(org.telegram.messenger.R.drawable.msg_channel, "公共频道",
                () -> presentFragment(new PublicChannelsFragment()));
        io.show();
        // Upstream raises its own menu by this constant; the popup otherwise hangs lower than the
        // icon it belongs to (DialogsActivity's own options menu).
        io.setTranslationY(-AndroidUtilities.dp(64));
    }

    /**
     * Switches the box shown, from the strip's tap or the menu. The strip has already moved for a
     * tap ({@code syncTabs} is false there); the menu has not, so it is pointed at the new box
     * there. Either way the old box's rows are dropped and a read still in flight for it lands on
     * nothing (the epoch and box are both checked when it answers).
     */
    private void selectBox(PrivateMessageBox newBox) {
        selectBox(newBox, false);
    }

    private void selectBox(PrivateMessageBox newBox, boolean syncTabs) {
        if (newBox == box) {
            if (syncTabs) {
                syncTabsToBox();
            }
            return;
        }
        box = newBox;
        actionBar.setSubtitle(boxLabel(box));
        if (syncTabs) {
            syncTabsToBox();
        }
        loadEpoch++;
        stateUi = null;
        resetForReload();
        notifyRowsChanged();
        loadPage();
    }

    /** Points the strip at the box on screen without re-entering the tap path. */
    private void syncTabsToBox() {
        if (tabsView != null) {
            tabsView.selectTabWithId(tabForBox(box), 1f);
        }
    }

    private static String boxLabel(PrivateMessageBox box) {
        switch (box) {
            case Sent:
                return "已发送";
            case Unread:
                return "未读";
            case Inbox:
            default:
                return "收件箱";
        }
    }

    /** The strip's tab id for a box, and back: the strip speaks ints. */
    private static int tabForBox(PrivateMessageBox box) {
        switch (box) {
            case Sent:
                return TAB_SENT;
            case Unread:
                return TAB_UNREAD;
            case Inbox:
            default:
                return TAB_INBOX;
        }
    }

    private static PrivateMessageBox boxForTab(int tabId) {
        switch (tabId) {
            case TAB_SENT:
                return PrivateMessageBox.Sent;
            case TAB_UNREAD:
                return PrivateMessageBox.Unread;
            case TAB_INBOX:
            default:
                return PrivateMessageBox.Inbox;
        }
    }

    /** Builds the three box tabs once, with the current box selected and the rest themed. */
    private void buildTabs() {
        if (tabsView == null) {
            return;
        }
        tabsView.addTextTab(TAB_INBOX, boxLabel(PrivateMessageBox.Inbox));
        tabsView.addTextTab(TAB_SENT, boxLabel(PrivateMessageBox.Sent));
        tabsView.addTextTab(TAB_UNREAD, boxLabel(PrivateMessageBox.Unread));
        tabsView.setInitialTabId(tabForBox(box));
        tabsView.finishAddingTabs();
    }

    /** Starts a private message; [recipient] is prefilled when an entry point already knows it. */
    private void presentNewMessage(String recipient) {
        if (getParentActivity() == null) {
            return;
        }
        presentFragment(NewTopicFragment.privateMessage(recipient));
    }

    /** A private message is a topic: the topic screen opens it, exactly as a public topic does. */
    private void open(TopicSummary topic) {
        presentFragment(TopicFragment.forTopic(topic.getId(), topic.getTitle()));
    }

    private void loadPage() {
        String username = sessionUsername();
        if (container == null || loading || reachedTheEnd || username == null) {
            return;
        }
        // Without a usable session there is nothing private to read: the state row is the screen.
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        loading = true;
        final int asked = page;
        final int epoch = loadEpoch;
        final PrivateMessageBox askedBox = box;
        final String askedUsername = username;
        PrivateMessagesLoader.INSTANCE.page(container, username, box, asked, loaded -> {
            if (epoch != loadEpoch || askedBox != box || !askedUsername.equals(sessionUsername())) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            stateUi = null;
            page = asked + 1;
            int added = 0;
            for (TopicSummary topic : loaded.getTopics()) {
                if (seenIds.add(topic.getId())) {
                    items.add(topic);
                    authors.add(author(topic));
                    added++;
                }
            }
            // The site's own `more_topics_url` is what says another page follows; a page that
            // brought nothing new ends the list too, so a site that repeats a page cannot loop.
            reachedTheEnd = loaded.getMoreTopicsUrl() == null || added == 0;
            empty = items.isEmpty();
            notifyRowsChanged();
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch || askedBox != box || !askedUsername.equals(sessionUsername())) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            FileLog.e("private messages failed", error);
            // A read that did not arrive is its own explicit state, not a blank list - but only
            // while there is nothing to read: a page that failed with rows already on screen leaves
            // them standing, and the next scroll asks again.
            if (items.isEmpty()) {
                stateUi = SessionUi.stateForLoadFailure(error);
            }
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Asks for the next page once the reader is within a few rows of the end of this one. */
    private void loadNextPageIfNeeded() {
        if (listView == null || listView.getAdapter() == null || items.isEmpty() || reachedTheEnd) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null
                || manager.findLastVisibleItemPosition()
                < listView.getAdapter().getItemCount() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        loadPage();
    }

    /** Whose messages these are: the site answers the box only for the reader themselves. */
    private String sessionUsername() {
        if (container == null || container.getSessionStore().snapshot() == null) {
            return null;
        }
        String username = container.getSessionStore().snapshot().getUsername();
        return username == null || username.isEmpty() ? null : username;
    }

    private TLRPC.User author(TopicSummary topic) {
        return DiscourseRows.topicAuthor(topic, container.getBaseUrl());
    }

    /** Each row's author picture is fetched once, then the list is bound again. */
    private void fetchAvatars() {
        for (int index = 0; index < authors.size(); index++) {
            final int row = index;
            String template = index < items.size() ? items.get(index).getAuthorAvatarTemplate() : "";
            String url = DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(authors.get(index))) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, authors.get(index), () -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyItemChanged(row);
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
    }

    /**
     * The list's own padding, and the compose button's lift. The button's default layout pins it
     * 14dp above the frame's bottom, so the navigation inset is what its translation has to clear;
     * the list reserves the button's room plus the inset under its last row, so a row can always be
     * scrolled clear of the button and the system gesture area.
     */
    private void checkUi_listPadding() {
        if (listView != null) {
            listView.setPadding(0, AndroidUtilities.dp(4), 0,
                    AndroidUtilities.dp(4) + navigationBarHeight
                            + AndroidUtilities.dp(FAB_LIST_CLEARANCE_DP));
        }
        if (composeButton != null) {
            composeButton.setTranslationY(-navigationBarHeight);
        }
    }

    /**
     * The day/night switch. The strip, the button, the bar and the rows all read their colours at
     * draw time or re-read them here; this screen is not rebuilt by the switch, so this is the
     * recolor.
     */
    private void updateColors() {
        if (tabsView != null) {
            tabsView.updateColors();
        }
        if (composeButton != null) {
            composeButton.updateColors();
        }
        if (progressView != null) {
            progressView.invalidate();
        }
        notifyRowsChanged();
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            actionBar.setSubtitleColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.updateColors();
        }
        if (fragmentView != null) {
            fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            fragmentView.invalidate();
        }
    }

    /**
     * The screen's own actions, as a screen test drives them: the overflow menu (which the reader
     * opens with the 更多 item) and the compose action. Neither invents a path - they are the same
     * calls the menu item's own click makes.
     */
    void openBoxMenu() {
        showItemOptions();
    }

    void askForNewMessage() {
        presentNewMessage(null);
    }

    /** The box currently shown, so a test can say a switch happened. */
    PrivateMessageBox currentBox() {
        return box;
    }

    /** The box strip, so a test can drive a tab the way the reader taps one. */
    ScrollSlidingTextTabStrip tabsView() {
        return tabsView;
    }

    /** The compose button, so a test can tap it and read its inset lift. */
    FragmentFloatingButton composeButton() {
        return composeButton;
    }

    /** The list, so a test can read its own padding against the inset. */
    RecyclerListView listView() {
        return listView;
    }

    /** The strip's own delegate: a tab tap reads that box's route. */
    private class BoxTabsDelegate implements ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {

        @Override
        public void onPageSelected(int pageId, boolean forward) {
            // The strip has already moved its indicator onto the tab; the box follows it.
            selectBox(boxForTab(pageId));
        }

        /** A second tap on the tab in front of the reader: the list goes back to its top. */
        @Override
        public void onSamePageSelected() {
            if (listView != null) {
                listView.smoothScrollToPosition(0);
            }
        }

        @Override
        public void onPageScrolled(float progress) {
            // The list has no second page to slide: the strip's own indicator animation is the
            // whole movement, so there is nothing to do per frame.
        }
    }

    private class MessageAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == TYPE_NOTE || viewType == TYPE_STATE) {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextInfoPrivacyCell) {
                String text = stateUi != null
                        ? SessionUi.text(holder.itemView.getContext(), stateUi).toString()
                        : "还没有私信";
                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                return;
            }
            TopicSummary topic = items.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            // All boxes carry the site's own unread count, including replies to a PM we started.
            cell.setDialog(DiscourseRows.topicRow(topic, false));
            // `setDialog` clears the row's avatar receiver, so the author's photo goes in after it.
            TLRPC.User user = authors.get(position);
            ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(user);
            if (avatar != null) {
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null, user, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (stateUi != null) {
                return TYPE_STATE;
            }
            return empty ? TYPE_NOTE : TYPE_MESSAGE;
        }

        @Override
        public int getItemCount() {
            if (stateUi != null) {
                return 1;
            }
            return empty ? 1 : items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (stateUi != null) {
                return SessionUi.hasAction(stateUi);
            }
            return !empty && !(holder.itemView instanceof TextInfoPrivacyCell);
        }
    }
}
