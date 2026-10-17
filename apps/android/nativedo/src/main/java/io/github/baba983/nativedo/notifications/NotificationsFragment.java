package io.github.baba983.nativedo.notifications;

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
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.NotificationItem;
import io.github.baba983.nativedo.core.model.NotificationPage;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.feature.notifications.data.NotificationRepository;
import io.github.baba983.nativedo.messagecenter.MessageCenterChild;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The reader's own notifications, {@code /notifications.json}, drawn by upstream's list row.
 *
 * <p>Nothing upstream can be reused as a screen: Telegram's notification screens are an account's
 * own (its chats, its message deliveries, its muted dialogs), and this client has none of that
 * behind it. What <em>can</em> be reused is the row, and a notification is what a
 * {@link DialogCell} already draws - a title, a line under it and a date - so the site's fields go
 * in there and a tap opens the topic at the floor the notification is about.</p>
 *
 * <p>When the message center hosts this page it passes {@code messageCenter=true}: the center owns
 * the single title bar, so this page draws no bar of its own and keeps it out of the host - its
 * mark-all-read action is reached through {@link #showMessageCenterOptions(View)} from the center's
 * menu instead. The center's capsule strip is drawn over the page, so the page also takes the
 * strip's height as its list's top padding ({@link #setMessageCenterTopInset(int)}): the first row
 * starts below the capsule and the rows after it scroll behind it. The list, the reads and the
 * session handling are the same either way.</p>
 */
public class NotificationsFragment extends BaseFragment
        implements NativeDoMainTabsActivity.TabPage, MessageCenterChild {

    private static final int TYPE_NOTIFICATION = 0;
    private static final int TYPE_NOTE = 1;
    /** The row a session state draws in place of the list: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 2;

    /** The action bar's own "mark everything read" item. */
    private static final int MENU_ITEM_MARK_ALL_READ = 1;

    /** How close to the end the list comes before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    private RecyclerListView listView;
    /**
     * DialogsActivity's own shimmer (4059-4064): the row the chat list draws, while it has no
     * row. A notification is a {@link DialogCell}, so that is the matching skeleton; it stands
     * until the first page of notifications has been answered, and a failed answer ends it too.
     */
    private FlickerLoadingView progressView;
    private final List<NotificationItem> items = new ArrayList<>();
    /**
     * One actor per row, so the avatar a row draws is fetched once and the row it belongs to can be
     * re-bound when the picture lands.
     */
    private final List<TLRPC.User> actors = new ArrayList<>();
    private AppContainer container;
    private boolean loading;
    /** Whether the reader has no notifications at all, which is its own row. */
    private boolean empty;
    private int offset;
    /** How many notifications the site has in total; every page moves [offset] towards it. */
    private int total = Integer.MAX_VALUE;

    /**
     * The explicit session state the list shows in place of rows, or {@code null} while it shows
     * content. Its value survives neither an identity change nor a view recreation: both drop it and
     * read again.
     */
    private SessionUiState stateUi;
    /** The session value the screen last acted on, so a change can be told from a repeat. */
    private AppSessionState lastSessionState;
    /**
     * Bumped whenever the account changes or the views go away. A read captures it before it
     * starts and drops its own answer if it no longer matches, so a late page from the previous
     * account cannot repopulate the list.
     */
    private int loadEpoch;
    /**
     * Whether a mark-read write is out. One write at a time keeps a double tap from telling the
     * site twice, and an all-read from overlapping a single row's write.
     */
    private boolean markReadInFlight;
    /** The session-state observer; cancelled with the views and started again with the next ones. */
    private kotlinx.coroutines.Job sessionObserver;

    /** Whether the screen is a page of the tab host, which adds the strip's height to the insets. */
    private boolean hasMainTabs;
    /** Whether the message center hosts this page: it owns the bar, this page draws none. */
    private boolean messageCenter;
    /**
     * The center's strip height in pixels: the room this page keeps above its first row, so the
     * list begins below the capsule and its later rows scroll under it. Zero when the page is not
     * a center page, which is the same padding as before.
     */
    private int messageCenterTopInset;
    private int additionNavigationBarHeight;
    private int navigationBarHeight;

    /**
     * The day/night switch recolours the rows and the bar in place: this page is a page of the
     * tab host, whose views are not rebuilt by the switch (the home's strip and this list keep
     * their views; see NativeDoActivity's theme handler).
     */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> AndroidUtilities.runOnUIThread(() -> {
                notifyRowsChanged();
                if (progressView != null) {
                    // The shimmer reads the theme colours at draw time; a redraw is its recolour.
                    progressView.invalidate();
                }
                if (actionBar != null) {
                    actionBar.updateColors();
                    actionBar.invalidate();
                }
                if (fragmentView != null) {
                    fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    fragmentView.invalidate();
                }
            });

    /** The tab host builds its pages with the bundle that says a strip is under them. */
    public NotificationsFragment(Bundle args) {
        super(args);
    }

    public NotificationsFragment() {
        super();
    }

    @Override
    protected View createView(Context context) {
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        messageCenter = arguments != null && arguments.getBoolean("messageCenter", false);
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;

        if (!messageCenter) {
            actionBar.setTitle("通知");
            // The page carries its own bar: inside the tab host the bar belongs to the page's screen,
            // and the strip (a sibling of the pager) draws over the pages, never under them.
            actionBar.setAddToContainer(false);
            // The bar owns one action: mark the whole inbox read. It is the site's own mark-read
            // route with no id, and the only place the reader clears everything at once.
            org.telegram.ui.ActionBar.ActionBarMenuItem markAllReadItem = actionBar.createMenu().addItem(
                    MENU_ITEM_MARK_ALL_READ, org.telegram.messenger.R.drawable.msg_markread);
            markAllReadItem.setContentDescription("全部已读");
            markAllReadItem.setOnClickListener(v -> markAllRead());
        } else {
            // The message center's bar is the page's bar: keeping this one out of the host stops it
            // sitting as an empty band above the list. Mark-all-read moves to the center's menu.
            actionBar.setAddToContainer(false);
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new NotificationAdapter());
        // The rows are DialogCells, so the chat list's own shimmer is the matching one
        // (DialogsActivity:4059-4064, 4695): it is the list's empty view, and only until the
        // first answer lands.
        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progressView.setVisibility(View.GONE);
        listView.setEmptyView(progressView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        listView.setVerticalScrollBarEnabled(false);
        // The list reaches the bottom of the window and its rows scroll behind the glass strip;
        // the last row stays reachable above it through the padding below.
        listView.setClipToPadding(false);
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

        FrameLayout frame = new FrameLayout(context);
        frame.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        // DialogsActivity's own layout (4062): the shimmer is centred over the list, its rows
        // pushed below the bar by checkUi_contentTop.
        frame.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        // The page's own action bar sits above the content; checkUi_contentTop() moves the content
        // below it once it has been measured. An embedded page has no bar of its own to sit under.
        if (!messageCenter) {
            frame.addView(actionBar, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        }
        fragmentView = frame;
        org.telegram.ui.Components.Bulletin.addDelegate(this, new org.telegram.ui.Components.Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return navigationBarHeight + additionNavigationBarHeight;
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        if (!messageCenter) {
            actionBar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> checkUi_contentTop());
            checkUi_contentTop();
        }
        container = NativeDo.container(getParentActivity());
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.didSetNewTheme);
        // The session is read before the list: an account with no usable session gets the explicit
        // state (and the read is not even sent), and a tab opened before a login catches up the
        // moment the session lands.
        stateUi = null;
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        observeSession();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            loadPage();
        }
        return fragmentView;
    }

    /**
     * The tab host rebuilds a page's views (a theme switch) without destroying the fragment: the
     * observer belongs to the views, so it is dropped here and started again from
     * {@link #createView}. Bumping the epoch drops any read still in flight for the old views.
     */
    @Override
    public void clearViews() {
        stopSessionObservation();
        loadEpoch++;
        super.clearViews();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.didSetNewTheme);
        stopSessionObservation();
        loadEpoch++;
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
     * the session is usable again, reads them again - immediately, so an open tab never keeps
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
        actors.clear();
        empty = false;
        loading = false;
        offset = 0;
        total = Integer.MAX_VALUE;
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
     * WebView's own completion publishes the usable session; this screen's observer re-reads the
     * list when that changes the account, and the explicit completion re-reads it when it does not,
     * so a cancelled verification neither recovers nor refreshes.
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
     * The recovery WebView's explicit completion ran: the reader confirmed it, so the session the
     * site is holding is the one to read with. A completion that changed the account is already
     * re-read by {@link #onSessionState} (its identity differs), so this reads only the
     * same-account case - a challenge solved by the account already signed in, which publishes the
     * same identity and would otherwise leave the failed page showing its challenge - exactly once.
     * It only reads: it never cancels the observation and never writes the session.
     */
    private void onRecoveryCompleted(String identityAtStart) {
        if (container == null) {
            return;
        }
        AppSessionState now = container.getSessionCoordinator().getSessionState().getValue();
        if (!SessionUi.identity(now).equals(identityAtStart)) {
            // A different account (or a lost session): the observer's own identity change is the
            // refresh, and reading again here would only duplicate it.
            return;
        }
        lastSessionState = now;
        loadEpoch++;
        stateUi = null;
        resetForReload();
        notifyRowsChanged();
        loadPage();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
    }

    private void checkUi_listPadding() {
        if (listView != null) {
            // The center's strip is drawn over the page, so the list keeps its height above the
            // first row: the rows begin below the capsule and the rest scroll behind it.
            listView.setPadding(0, AndroidUtilities.dp(4) + messageCenterTopInset, 0,
                    AndroidUtilities.dp(4) + navigationBarHeight + additionNavigationBarHeight);
        }
    }

    /**
     * The center hands its strip's height over: the page begins under the strip, so its first row
     * keeps that room while later rows scroll behind the capsule (the list already clips off its
     * padding). The center may call this before the views exist; the next view takes the field.
     */
    @Override
    public void setMessageCenterTopInset(int inset) {
        int next = Math.max(0, inset);
        if (next == messageCenterTopInset) {
            return;
        }
        messageCenterTopInset = next;
        checkUi_listPadding();
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
        // The shimmer's own rows start below the bar too, the way DialogsActivity keeps its
        // progress view under its top panel (2093-2095).
        if (progressView != null) {
            progressView.setPaddingTop(top);
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
     * A tap on a notification does up to two things the site separates. Any notification is marked
     * read - including the topic-less ones a badge or consolidated likes produce, which have
     * nothing to open. One the site gave a post to also opens that post, but only once the read
     * has been taken: navigating first would show the topic even after a refused read, and the row
     * would go on saying it was unread. A notification already read opens at once; a topic-less one
     * never opens.
     */
    private void open(NotificationItem item) {
        boolean opens = DiscourseNotifications.INSTANCE.hasDestination(item);
        if (item.isRead()) {
            if (opens) {
                openFloor(item);
            }
            return;
        }
        markRead(item, opens);
    }

    /** Opens the post a notification is about, on the reader's own stack. */
    private void openFloor(NotificationItem item) {
        if (getParentActivity() == null) {
            return;
        }
        presentFragment(TopicFragment.forFloor(item.getTopicId(), item.getTitle(), item.getPostNumber()));
    }

    /**
     * Marks one notification read on the site, then - only when the site took the write - moves the
     * row and, if the tap was also meant to open a post, opens it. A write already out blocks a
     * second one, so a double tap is one write. The callbacks carry the account and view generation
     * the write started under; a late answer for a session that has since changed drops instead of
     * marking the next account's rows or re-reading its badge. A refusal keeps the row as it was
     * and says so, with the retry the failure offers.
     */
    private void markRead(NotificationItem item, boolean navigateOnSuccess) {
        if (container == null || markReadInFlight) {
            return;
        }
        markReadInFlight = true;
        final long id = item.getId();
        final NotificationItem target = item;
        final WriteGuard guard = writeGuard();
        NotificationsLoader.INSTANCE.markRead(container, id, () -> {
            markReadInFlight = false;
            if (guard.isStale()) {
                return kotlin.Unit.INSTANCE;
            }
            markItemRead(id);
            refreshBadge();
            if (navigateOnSuccess) {
                openFloor(target);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            markReadInFlight = false;
            if (guard.isStale()) {
                return kotlin.Unit.INSTANCE;
            }
            FileLog.e("mark notification read failed", error);
            showMarkReadFailure(id);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The action bar's all-read item: the same write with no id, so the whole inbox goes read. */
    private void markAllRead() {
        if (container == null || markReadInFlight
                || !(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        markReadInFlight = true;
        final WriteGuard guard = writeGuard();
        // Only the rows on screen when the write started may be called read by its answer: a
        // notification that arrives while the write is out was not part of it.
        final List<Long> idsAtStart = new ArrayList<>();
        for (NotificationItem item : items) {
            idsAtStart.add(item.getId());
        }
        NotificationsLoader.INSTANCE.markRead(container, null, () -> {
            markReadInFlight = false;
            if (guard.isStale()) {
                return kotlin.Unit.INSTANCE;
            }
            for (int index = 0; index < items.size(); index++) {
                if (idsAtStart.contains(items.get(index).getId())) {
                    items.set(index, DiscourseNotifications.INSTANCE.markedRead(items.get(index)));
                }
            }
            notifyRowsChanged();
            refreshBadge();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            markReadInFlight = false;
            if (guard.isStale()) {
                return kotlin.Unit.INSTANCE;
            }
            FileLog.e("mark all notifications read failed", error);
            showMarkReadFailure(null);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The message center's own bar menu, hung from the view its title bar hands over: the one action
     * this page keeps - mark the whole inbox read - reached from the center since the page draws no
     * bar of its own.
     */
    @Override
    public void showMessageCenterOptions(View anchor) {
        if (anchor == null || getParentActivity() == null) {
            return;
        }
        ItemOptions io = ItemOptions.makeOptions(this, anchor);
        io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
        io.setDimAlpha(0x08);
        io.add(org.telegram.messenger.R.drawable.msg_markread, "全部已读", () -> markAllRead());
        io.show();
    }

    /**
     * The account and view generation a write started under: the view epoch, the session identity,
     * and the coordinator's request generation. A callback whose guard no longer matches is for a
     * screen or a reader that has moved on and must change nothing.
     */
    private WriteGuard writeGuard() {
        return new WriteGuard(
                loadEpoch,
                SessionUi.identity(lastSessionState),
                container == null ? 0 : container.getSessionCoordinator().currentRequestContext().getGeneration());
    }

    private final class WriteGuard {
        private final int epoch;
        private final String identity;
        private final int sessionGeneration;

        WriteGuard(int epoch, String identity, int sessionGeneration) {
            this.epoch = epoch;
            this.identity = identity;
            this.sessionGeneration = sessionGeneration;
        }

        boolean isStale() {
            if (epoch != loadEpoch) {
                return true;
            }
            if (!SessionUi.identity(lastSessionState).equals(identity)) {
                return true;
            }
            return container == null
                    || container.getSessionCoordinator().currentRequestContext().getGeneration() != sessionGeneration;
        }
    }

    /** Flips the row for [id] to read, once the site has taken the write. */
    private void markItemRead(long id) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getId() == id) {
                items.set(index, DiscourseNotifications.INSTANCE.markedRead(items.get(index)));
                notifyRowsChanged();
                return;
            }
        }
    }

    private NotificationItem itemById(long id) {
        for (NotificationItem item : items) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    /**
     * The badge is the site's own count, so a write that changes it re-reads the page it is seeded
     * from rather than moving the number here. The session guards that readback against a live
     * channel message and an account change; this only asks for it.
     */
    private void refreshBadge() {
        if (container != null) {
            container.getMessageBusSession().refreshNotificationCounts();
        }
    }

    /**
     * A read the site refused. The row is left as it was and the failure is said in passing with
     * the tap that tries again, so nothing the reader sees claims a change that did not happen.
     */
    private void showMarkReadFailure(Long id) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.chats_infotip,
                    "标记已读失败",
                    "重试",
                    org.telegram.ui.Components.Bulletin.DURATION_PROLONG,
                    () -> {
                        if (id == null) {
                            markAllRead();
                        } else {
                            NotificationItem item = itemById(id);
                            if (item != null) {
                                open(item);
                            }
                        }
                    }).show();
        } catch (Exception bulletinError) {
            FileLog.e(bulletinError);
        }
    }

    private void loadPage() {
        if (container == null || loading || offset >= total) {
            return;
        }
        // Without a usable session there is nothing private to read: the state row is the screen.
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        loading = true;
        final int asked = offset;
        // The account this read is for; if it changes before the page lands, the page is dropped.
        final int epoch = loadEpoch;
        NotificationsLoader.INSTANCE.page(container, asked, page -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            stateUi = null;
            offset = asked + page.getItems().size();
            total = page.getTotalRows();
            for (NotificationItem item : page.getItems()) {
                items.add(item);
                actors.add(actor(item));
            }
            empty = items.isEmpty();
            notifyRowsChanged();
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            FileLog.e("notifications failed", error);
            // A read that did not arrive is its own explicit state, not a blank list - but only
            // while there is nothing to read: a page that failed with rows already on screen leaves
            // them standing, the way a topic page does, and the next scroll asks again.
            if (items.isEmpty()) {
                stateUi = SessionUi.stateForLoadFailure(error);
            }
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Asks for the next page once the reader is within a few rows of the end of this one. */
    private void loadNextPageIfNeeded() {
        if (listView == null || listView.getAdapter() == null || items.isEmpty() || offset >= total) {
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

    /** The reader who caused a notification, as a peer upstream can draw a picture for. */
    private TLRPC.User actor(NotificationItem item) {
        return DiscourseAvatars.INSTANCE.user(
                item.getActorUsername(),
                item.getActorName().isEmpty() ? item.getActorUsername() : item.getActorName(),
                item.getActorAvatarTemplate(),
                container.getBaseUrl());
    }

    /**
     * The rows draw their actor's initials until the picture is on disk, so each actor's avatar is
     * fetched once, then the list is bound again.
     */
    private void fetchAvatars() {
        for (int index = 0; index < actors.size(); index++) {
            TLRPC.User actor = actors.get(index);
            String template = index < items.size() ? items.get(index).getActorAvatarTemplate() : "";
            String url = DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(actor)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, actor, () -> {
                notifyRowsChanged();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class NotificationAdapter extends RecyclerListView.SelectionAdapter {

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
                String text;
                if (stateUi != null) {
                    text = SessionUi.text(holder.itemView.getContext(), stateUi).toString();
                } else {
                    text = "还没有通知";
                }
                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                return;
            }
            NotificationItem item = items.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            cell.setDialog(DiscourseNotifications.INSTANCE.row(item));
            // `setDialog` clears the row's avatar receiver, so the actor's picture goes in after it.
            TLRPC.User actor = actors.get(position);
            ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(actor);
            if (avatar != null) {
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null, actor, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (stateUi != null) {
                return TYPE_STATE;
            }
            return empty ? TYPE_NOTE : TYPE_NOTIFICATION;
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
            if (empty || holder.itemView instanceof TextInfoPrivacyCell) {
                return false;
            }
            // Every notification is tappable: a tap marks it read, and one the site gave a post to
            // also opens it. A topic-less notification marks read without pretending to open.
            return true;
        }
    }
}
