package io.github.baba983.nativedo.chat;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

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
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.ChatChannel;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.messagecenter.MessageCenterChild;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;

/**
 * The public linux.do chat channels, the way the site lists them: a scrollable list of channels,
 * each a stock {@link DialogCell} row carrying the channel's title and its last message.
 *
 * <p>Public channels are category channels only - a direct-message channel is not in this list.
 * The row is the same {@code DialogCell} a topic list draws, the shimmer is DialogsActivity's own,
 * and a channel that did not arrive is its own explicit state rather than a blank list. Tapping a
 * channel opens {@link ChatChannelFragment}, which is where joining, reading and writing happen.</p>
 *
 * <p>When the message center hosts this page it passes {@code messageCenter=true}: the center owns
 * the single title bar, so this page draws no bar of its own and keeps it out of the host - its
 * refresh action is reached through {@link #showMessageCenterOptions(View)} from the center's menu
 * instead. With {@code hasMainTabs=true} the list reserves the main-tab strip's room under its last
 * row. The list, the reads and the session handling are the same either way.</p>
 */
public class PublicChannelsFragment extends BaseFragment implements MessageCenterChild {

    private static final int TYPE_CHANNEL = 0;
    private static final int TYPE_NOTE = 1;
    /** A session state row: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 2;

    private static final int REFRESH_ITEM = 1;
    private static final int LOAD_MORE_THRESHOLD = 3;
    /** One page of channels; the site's own default, and the step the next offset advances by. */
    private static final int CHANNELS_PAGE = 25;

    private RecyclerListView listView;
    private FlickerLoadingView progressView;
    private ActionBarMenuItem optionsItem;

    private final List<ChatChannel> channels = new ArrayList<>();
    private final List<TLRPC.User> avatars = new ArrayList<>();
    private final Set<Long> seenIds = new HashSet<>();

    private AppContainer container;
    private boolean loading;
    private boolean empty;
    private int offset;
    /** Set when a page came back with no new channel: that is the end of the list. */
    private boolean reachedTheEnd;

    private SessionUiState stateUi;
    private AppSessionState lastSessionState;
    private int loadEpoch;
    private kotlinx.coroutines.Job sessionObserver;

    private int navigationBarHeight;
    /** Whether the message center hosts this page: it owns the bar, this page draws none. */
    private boolean messageCenter;
    /** Whether a main-tab strip sits under this page, whose room the list must clear. */
    private boolean hasMainTabs;
    private int additionNavigationBarHeight;
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> AndroidUtilities.runOnUIThread(this::updateColors);

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        return super.onFragmentCreate();
    }

    public PublicChannelsFragment() {
        super();
    }

    public PublicChannelsFragment(Bundle args) {
        super(args);
    }

    /** The 聊天 entry: the list of public channels. */
    public static PublicChannelsFragment create() {
        return new PublicChannelsFragment();
    }

    @Override
    protected View createView(Context context) {
        messageCenter = arguments != null && arguments.getBoolean("messageCenter", false);
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs
                ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;

        if (!messageCenter) {
            actionBar.setTitle("聊天");
            actionBar.setSubtitle("公开频道");
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
            optionsItem = actionBar.createMenu().addItem(
                    REFRESH_ITEM, org.telegram.messenger.R.drawable.msg_retry);
            optionsItem.setContentDescription("刷新");
            optionsItem.setOnClickListener(v -> refresh());
        } else {
            // The message center's bar is the page's bar: keeping this one out of the host stops it
            // sitting as an empty band above the list. Refresh moves to the center's menu.
            actionBar.setAddToContainer(false);
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new ChannelAdapter());
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
            } else if (position >= 0 && position < channels.size()) {
                open(channels.get(position));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadNextPageIfNeeded();
            }
        });

        fragmentView = listView;
        // The list takes its own window insets: presented on its own the shell wires this for the
        // fragment, but hosted as a page of the message center it is this listener that hands it its
        // insets (NotificationsFragment wires the very same one for the same reason).
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        updateColors();
        container = NativeDo.container(getParentActivity());
        stateUi = null;
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        observeSession();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            loadPage();
        }
        return fragmentView;
    }

    @Override
    public void clearViews() {
        stopSessionObservation();
        loadEpoch++;
        super.clearViews();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        stopSessionObservation();
        loadEpoch++;
        if (progressView != null && listView != null) {
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

    private void resetForReload() {
        channels.clear();
        avatars.clear();
        seenIds.clear();
        empty = false;
        loading = false;
        offset = 0;
        reachedTheEnd = false;
    }

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

    /** Re-reads the list from the first page; a refresh never merges into a stale page. */
    private void refresh() {
        loadEpoch++;
        stateUi = null;
        resetForReload();
        notifyRowsChanged();
        loadPage();
    }

    private void open(ChatChannel channel) {
        if (getParentActivity() == null) {
            return;
        }
        presentFragment(ChatChannelFragment.forChannel(channel.getId(), channel.getTitle()));
    }

    private void loadPage() {
        if (container == null || loading || reachedTheEnd) {
            return;
        }
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        loading = true;
        final int asked = offset;
        final int epoch = loadEpoch;
        ChatLoader.INSTANCE.channels(container, asked, loaded -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            stateUi = null;
            offset = asked + CHANNELS_PAGE;
            int added = 0;
            for (ChatChannel channel : loaded.getChannels()) {
                if (seenIds.add(channel.getId())) {
                    channels.add(channel);
                    avatars.add(avatar(channel));
                    added++;
                }
            }
            // The site's own `load_more_url` is what says another page follows; a page that brought
            // nothing new ends the list too, so a site that repeats a page cannot loop.
            reachedTheEnd = loaded.getLoadMoreUrl() == null || added == 0;
            empty = channels.isEmpty();
            notifyRowsChanged();
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            loading = false;
            FileLog.e("chat channels failed", error);
            if (channels.isEmpty()) {
                stateUi = SessionUi.stateForLoadFailure(error);
            }
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void loadNextPageIfNeeded() {
        if (listView == null || listView.getAdapter() == null || channels.isEmpty() || reachedTheEnd) {
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

    private TLRPC.User avatar(ChatChannel channel) {
        String username = channel.getSlug();
        if (username == null || username.isEmpty()) {
            username = channel.getTitle();
        }
        String template = channel.getLastMessage() == null || channel.getLastMessage().getUser() == null
                ? "" : channel.getLastMessage().getUser().getAvatarTemplate();
        return DiscourseAvatars.INSTANCE.user(
                username,
                channel.getTitle(),
                template == null ? "" : template,
                container.getBaseUrl());
    }

    private void fetchAvatars() {
        for (int index = 0; index < avatars.size(); index++) {
            final int row = index;
            String template = index < channels.size()
                    ? (channels.get(index).getLastMessage() == null
                            || channels.get(index).getLastMessage().getUser() == null
                            ? "" : channels.get(index).getLastMessage().getUser().getAvatarTemplate())
                    : "";
            String url = DiscourseAvatars.INSTANCE.url(
                    template == null ? "" : template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(avatars.get(index))) {
                continue;
            }
            final String avatarUrl = url;
            io.github.baba983.nativedo.topic.DiscourseLoader.INSTANCE.cacheAvatar(
                    container, avatarUrl, avatars.get(index), () -> {
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

    private void updateColors() {
        if (fragmentView != null) fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            actionBar.setSubtitleColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.updateColors();
        }
        notifyRowsChanged();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
    }

    private void checkUi_listPadding() {
        if (listView != null) {
            listView.setPadding(0, AndroidUtilities.dp(4), 0,
                    AndroidUtilities.dp(4) + navigationBarHeight + additionNavigationBarHeight);
        }
    }

    /**
     * The message center's own bar menu, hung from the view its title bar hands over: the one action
     * this page keeps - read the channels again - reached from the center since the page draws no
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
        io.add(org.telegram.messenger.R.drawable.msg_retry, "刷新", () -> refresh());
        io.show();
    }

    /** The list, for a screen test. */
    RecyclerListView listViewForTest() {
        return listView;
    }

    /** How many channel rows the list holds. */
    int channelCount() {
        return channels.size();
    }

    /** The state row's own value, or null while the list shows content. */
    SessionUiState stateForTest() {
        return stateUi;
    }

    /** Reads the list again, the same call the refresh item makes. */
    void refreshForTest() {
        refresh();
    }

    /** Opens the channel at [index], the same call a row tap makes. */
    void openForTest(int index) {
        if (index >= 0 && index < channels.size()) {
            open(channels.get(index));
        }
    }

    private class ChannelAdapter extends RecyclerListView.SelectionAdapter {

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
                        : "还没有公开频道";
                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                return;
            }
            ChatChannel channel = channels.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            cell.setDialog(ChatRows.channelRow(channel));
            if (ChatRows.isJoined(channel)) {
                cell.setDialogSelected(true);
            }
            TLRPC.User user = avatars.get(position);
            ImageLocation av = DiscourseAvatars.INSTANCE.imageLocation(user);
            if (av != null) {
                cell.avatarImage.setImage(av, "50_50", cell.avatarImage.getDrawable(), null, user, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (stateUi != null) {
                return TYPE_STATE;
            }
            return empty ? TYPE_NOTE : TYPE_CHANNEL;
        }

        @Override
        public int getItemCount() {
            if (stateUi != null) {
                return 1;
            }
            return empty ? 1 : channels.size();
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
