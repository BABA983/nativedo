package io.github.baba983.nativedo.chat;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.baba983.nativedo.app.GlassPanelHost;
import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.messagebus.ChatChannelStateHolder;
import io.github.baba983.nativedo.core.messagebus.ChatLiveEvent;
import io.github.baba983.nativedo.core.messagebus.MessageBusClient;
import io.github.baba983.nativedo.core.model.ChatChannel;
import io.github.baba983.nativedo.core.model.ChatMessage;
import io.github.baba983.nativedo.core.model.ChatMessagePage;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseComposerDelegate;
import io.github.baba983.nativedo.topic.DiscourseImageBridge;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.PostListView;
import io.github.baba983.nativedo.topic.PreparedPostBody;

/**
 * One public linux.do chat channel: its conversation, drawn the way a chat draws one.
 *
 * <p>Every message is an upstream {@link ChatMessageCell} over the shared post->bubble bridge
 * ({@link DiscourseRows#postMessage}), and the composer is upstream's own
 * {@link ChatActivityEnterView} in its floating {@link ChatInputViewsContainer} island - the same
 * field a topic reply uses - so this client's conversation reads and writes like the rest of the
 * app instead of a homegrown bubble list. The list itself is the topic list's own
 * {@link PostListView}, which draws the author's avatar under a run of messages and answers a
 * leftward swipe with a reply target.</p>
 *
 * <p>States that are not a conversation are said out loud rather than hidden: a channel the reader
 * has not joined carries a join row (and no composer), a channel that is read-only or closed says
 * so and hides the composer, and a read that failed is its own state with a retry - a challenge or a
 * lost session opens the login WebView, an outage simply asks again.</p>
 */
public class ChatChannelFragment extends BaseFragment {

    private static final String ARG_CHANNEL_ID = "chat_channel_id";
    private static final String ARG_CHANNEL_TITLE = "chat_channel_title";

    private static final int TYPE_MESSAGE = 0;
    private static final int TYPE_NOTE = 1;
    /** A session state row: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 2;

    private static final int LOAD_OLDER_THRESHOLD = 4;

    private long channelId;
    private String channelTitle;

    private AppContainer container;
    private ChatChannel channel;
    private SizeNotifierFrameLayout contentView;
    private PostListView listView;
    private ChatActivityEnterView composer;
    private ChatInputViewsContainer inputViews;
    private WindowInsetsStateHolder inputInsets;
    private GlassPanelHost glassHost;
    private ChatMessageSharedResources sharedResources;

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final List<MessageObject> bubbles = new ArrayList<>();
    private final List<PreparedPostBody> bodies = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    private final java.util.HashSet<Long> seenIds = new java.util.HashSet<>();

    /** The message a swipe asked to answer, or null when the next message is a plain one. */
    private Long replyTargetId;
    private String replyTargetLabel;
    private android.widget.TextView replyStrip;

    private boolean loading;
    private boolean loadingOlder;
    private boolean canLoadMorePast;
    private boolean canLoadMoreFuture;
    private boolean sending;
    private boolean joining;
    private int loadEpoch;
    private int navigationBarHeight;
    private int composerHeight;

    private SessionUiState stateUi;
    private AppSessionState lastSessionState;
    private kotlinx.coroutines.Job sessionObserver;
    private ChatChannelStateHolder liveHolder;
    private io.github.baba983.nativedo.feature.chat.data.ChatDraftKey draftOwner;
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> AndroidUtilities.runOnUIThread(this::updateColors);

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        return super.onFragmentCreate();
    }

    public ChatChannelFragment() {
        super();
    }

    public ChatChannelFragment(Bundle args) {
        super(args);
    }

    /** One public channel's conversation, opened from the channel list. */
    public static ChatChannelFragment forChannel(long channelId, String title) {
        Bundle args = new Bundle();
        args.putLong(ARG_CHANNEL_ID, channelId);
        args.putString(ARG_CHANNEL_TITLE, title);
        return new ChatChannelFragment(args);
    }

    @Override
    protected View createView(Context context) {
        channelId = arguments == null ? 0L : arguments.getLong(ARG_CHANNEL_ID);
        channelTitle = arguments == null ? null : arguments.getString(ARG_CHANNEL_TITLE);
        container = NativeDo.container(getParentActivity());
        sharedResources = new ChatMessageSharedResources(context);

        actionBar.setTitle(channelTitle == null || channelTitle.isEmpty() ? "频道" : channelTitle);
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    if (!sending && !joining) loadMessages(null, null, true);
                }
            }
        });
        actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.msg_retry)
                .setContentDescription("刷新");

        contentView = new SizeNotifierFrameLayout(context);
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        listView = new PostListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new MessageAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setAvatarSource(senderId -> users.get(senderId));
        listView.setSwipeReplyListener(this::onSwipeReply);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadOlderIfNeeded();
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        // The reply target strip, upstream's own reply bar shape: one line under the action bar.
        replyStrip = new android.widget.TextView(context);
        replyStrip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        replyStrip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        replyStrip.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourceProvider));
        replyStrip.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6),
                AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        replyStrip.setSingleLine(true);
        replyStrip.setVisibility(View.GONE);
        replyStrip.setOnClickListener(v -> clearReplyTarget());
        contentView.addView(replyStrip, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // The composer rides in upstream's floating island the way the reply box does.
        glassHost = new GlassPanelHost(listView, contentView);
        inputViews = new ChatInputViewsContainer(context);
        inputViews.setClipChildren(false);
        inputInsets = new WindowInsetsStateHolder(() -> {
            if (inputViews != null) {
                inputViews.checkInsets();
                applyListPadding();
            }
        });
        inputViews.setWindowInsetsProvider(inputInsets);
        inputViews.getInputIslandBubbleContainer().setClipChildren(false);
        org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider inputProvider =
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .bottomPanelChatActivity(resourceProvider);
        inputViews.setInputIslandBubbleDrawable(glassHost.createPanel(inputViews, inputProvider));
        inputViews.setUnderKeyboardBackgroundDrawable(glassHost.createPanel(inputViews, inputProvider));
        contentView.addView(inputViews, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        inputViews.getInputIslandBubbleContainer().addView(createComposer(contentView),
                LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.BOTTOM, 7, 0, 7, 0));
        // The composer is offered only once the channel says the reader may write; it is hidden
        // until then rather than showing a field whose send the site would refuse.
        inputViews.setVisibility(View.GONE);

        fragmentView = contentView;
        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onInsetsInternal);

        stateUi = null;
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        draftOwner = lastSessionState instanceof AppSessionState.Authenticated ? draftKey() : null;
        observeSession();
        restoreDraft();
        updateColors();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            loadChannel();
        }
        return fragmentView;
    }

    private View createComposer(SizeNotifierFrameLayout parent) {
        ChatActivityEnterView view = new ChatActivityEnterView(
                getParentActivity(), parent, null, true, resourceProvider) {
            @Override
            protected void onChangedIslandTotalHeight(float height) {
                if (inputViews != null) {
                    inputViews.setInputBubbleHeight(Math.max(height, AndroidUtilities.dp(44)));
                    composerHeight = (int) Math.max(height, AndroidUtilities.dp(44));
                    applyListPadding();
                }
            }
        };
        view.shouldDrawBackground = false;
        view.setMinimumHeight(AndroidUtilities.dp(51));
        view.setInAppInsetsController(inputInsets);
        view.setViewParentForEmoji(inputViews.getInAppKeyboardBubbleContainer());
        view.setAllowStickersAndGifs(false, false, false);
        view.setDelegate(new SendDelegate());
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateComposerActions();
            if (inputViews != null) {
                inputViews.setInputBubbleHeight(Math.max(view.getIslandTotalHeight(false), AndroidUtilities.dp(44)));
                composerHeight = (int) Math.max(view.getIslandTotalHeight(false), AndroidUtilities.dp(44));
                inputViews.checkInsets();
                applyListPadding();
            }
        });
        composer = view;
        return view;
    }

    // ---------------------------------------------------------------------------------------------
    // Session / loading
    // ---------------------------------------------------------------------------------------------

    @Override
    public void clearViews() {
        saveDraft();
        stopSessionObservation();
        closeLiveHolder();
        loadEpoch++;
        super.clearViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (composer != null) composer.onResume();
        updateColors();
        startLiveUpdates();
    }

    @Override
    public void onPause() {
        saveDraft();
        closeLiveHolder();
        if (composer != null) composer.onPause();
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        stopSessionObservation();
        closeLiveHolder();
        loadEpoch++;
        saveDraft();
        if (composer != null) {
            composer.onDestroy();
            composer = null;
        }
        inputViews = null;
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
        if (identityChanged) {
            saveDraft();
            draftOwner = null;
            if (composer != null) composer.setFieldText("");
            loadEpoch++;
            closeLiveHolder();
            resetForReload();
            stateUi = null;
            notifyRowsChanged();
        }
        lastSessionState = state;
        if (state instanceof AppSessionState.Authenticated) {
            if (identityChanged) {
                draftOwner = draftKey();
                restoreDraft();
                loadChannel();
            }
        } else {
            stateUi = SessionUi.stateFor(state);
            loading = false;
            notifyRowsChanged();
        }
        updateComposerVisibility();
    }

    private void resetForReload() {
        channel = null;
        chatMessages.clear();
        bubbles.clear();
        bodies.clear();
        users.clear();
        seenIds.clear();
        canLoadMorePast = false;
        canLoadMoreFuture = false;
        loading = false;
        loadingOlder = false;
        sending = false;
        joining = false;
        clearReplyTarget();
    }

    private void loadChannel() {
        if (container == null) {
            return;
        }
        final int epoch = loadEpoch;
        ChatLoader.INSTANCE.channel(container, channelId, loaded -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            channel = loaded;
            applyChannelHeader();
            loadMessages(null, null, true);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            FileLog.e("chat channel failed", error);
            stateUi = SessionUi.stateForLoadFailure(error);
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The channel's own state, once it is known: the header, the composer and the join affordance. */
    private void applyChannelHeader() {
        if (channel == null) {
            return;
        }
        actionBar.setTitle(channel.getTitle().isEmpty() ? "频道" : channel.getTitle());
        actionBar.setSubtitle(statusLabel(channel));
        updateComposerVisibility();
        notifyRowsChanged();
    }

    private void loadMessages(Long targetMessageId, String direction, boolean replace) {
        if (container == null || loading || sending) {
            return;
        }
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        final int epoch = loadEpoch;
        if (replace) {
            loading = true;
        } else {
            loadingOlder = true;
        }
        final boolean askedReplace = replace;
        // Start at the latest window; older history uses an exclusive past cursor.
        final Boolean fetchFromLastRead = null;
        ChatLoader.INSTANCE.messages(container, channelId, targetMessageId, direction,
                fetchFromLastRead,
                loaded -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    loading = false;
                    loadingOlder = false;
                    stateUi = null;
                    applyPage(loaded, askedReplace);
                    return kotlin.Unit.INSTANCE;
                }, error -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    loading = false;
                    loadingOlder = false;
                    FileLog.e("chat messages failed", error);
                    // A read that did not arrive with nothing on screen is its own state; one that
                    // failed with messages already drawn leaves them standing.
                    if (chatMessages.isEmpty()) {
                        stateUi = SessionUi.stateForLoadFailure(error);
                    }
                    notifyRowsChanged();
                    return kotlin.Unit.INSTANCE;
                });
    }

    private void applyPage(ChatMessagePage page, boolean replace) {
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        int anchor = manager == null ? -1 : manager.findFirstVisibleItemPosition();
        View anchorView = manager == null ? null : manager.findViewByPosition(anchor);
        int anchorTop = anchorView == null ? 0 : anchorView.getTop() - listView.getPaddingTop();
        if (replace) {
            chatMessages.clear();
            bubbles.clear();
            bodies.clear();
            seenIds.clear();
        }
        int added = 0;
        if (replace) {
            for (ChatMessage message : page.getMessages()) {
                if (seenIds.add(message.getId())) {
                    chatMessages.add(message);
                    added++;
                }
            }
        } else {
            // An older window: prepend in order, one message at a time.
            List<ChatMessage> older = new ArrayList<>();
            for (ChatMessage message : page.getMessages()) {
                if (seenIds.add(message.getId())) {
                    older.add(message);
                }
            }
            chatMessages.addAll(0, older);
            added = older.size();
        }
        canLoadMorePast = page.getCanLoadMorePast();
        canLoadMoreFuture = page.getCanLoadMoreFuture();
        rebuildBubbles();
        notifyRowsChanged();
        if (replace) {
            startLiveUpdates();
            scrollToNewest();
            listView.post(this::markNewestRead);
        } else if (added > 0 && anchor >= 0 && manager != null) {
            manager.scrollToPositionWithOffset(anchor + added, anchorTop);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bubbles
    // ---------------------------------------------------------------------------------------------

    /** Builds one bubble per message, in the order the site sent them. */
    private void rebuildBubbles() {
        bubbles.clear();
        bodies.clear();
        users.clear();
        for (ChatMessage message : chatMessages) {
            bubbles.add(buildBubble(message));
        }
        for (TLRPC.User user : users.values()) {
            getMessagesController().putUser(user, true);
        }
    }

    private MessageObject buildBubble(ChatMessage message) {
        io.github.baba983.nativedo.core.model.Post post = ChatRows.post(message);
        PreparedPostBody body = DiscourseImageBridge.INSTANCE.prepare(
                post.getCookedHtml(), container.getBaseUrl());
        TLRPC.Message replyTo = null;
        if (message.getInReplyTo() != null) {
            io.github.baba983.nativedo.core.model.Post target = ChatRows.replyTarget(message.getInReplyTo());
            replyTo = DiscourseRows.replySnapshot(
                    target, channelId, users, container.getBaseUrl(), message.getInReplyTo().getExcerpt());
        }
        bodies.add(body);
        return DiscourseRows.postMessage(post, channelId, users, body, container.getBaseUrl(), replyTo);
    }

    private void notifyRowsChanged() {
        for (TLRPC.User user : users.values()) {
            getMessagesController().putUser(user, true);
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        fetchAvatars();
    }

    private void fetchAvatars() {
        if (container == null) {
            return;
        }
        for (ChatMessage message : chatMessages) {
            if (message.getUser() == null) {
                continue;
            }
            String template = message.getUser().getAvatarTemplate();
            if (template == null || template.isEmpty()) {
                continue;
            }
            TLRPC.User user = users.get(DiscourseRows.userId(message.getUser().getUsername()));
            if (user == null) {
                continue;
            }
            String url = DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(user)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /** The older window a scroll to the top asks for. */
    private void loadOlderIfNeeded() {
        if (listView == null || listView.getAdapter() == null || chatMessages.isEmpty()
                || loadingOlder || !canLoadMorePast) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null || manager.findFirstVisibleItemPosition() > LOAD_OLDER_THRESHOLD) {
            return;
        }
        loadMessages(chatMessages.get(0).getId(), "past", false);
    }

    private void scrollToNewest() {
        if (listView != null && listView.getAdapter() != null && chatMessages.size() > 0) {
            listView.scrollToPosition(chatMessages.size() - 1);
        }
    }

    private void markNewestRead() {
        if (container == null || chatMessages.isEmpty() || channel == null || !channel.isJoined()
                || !(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        ChatMessage newest = chatMessages.get(chatMessages.size() - 1);
        ChatLoader.INSTANCE.markRead(container, channelId, newest.getId());
    }

    // ---------------------------------------------------------------------------------------------
    // Joining
    // ---------------------------------------------------------------------------------------------

    private void join() {
        if (container == null || joining || channel == null || !channel.getCanJoin()) {
            return;
        }
        joining = true;
        final int epoch = loadEpoch;
        notifyRowsChanged();
        ChatLoader.INSTANCE.join(container, channelId, membership -> {
            if (epoch != loadEpoch) return kotlin.Unit.INSTANCE;
            joining = false;
            // Re-read the channel so the membership and the composer follow the site's answer.
            loadChannel();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch) return kotlin.Unit.INSTANCE;
            joining = false;
            FileLog.e("chat join failed", error);
            stateUi = SessionUi.stateForLoadFailure(error);
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------------------------------

    private void send(CharSequence text) {
        if (container == null || sending || channel == null || !channel.getCanPost()) {
            return;
        }
        String message = text == null ? "" : text.toString().trim();
        if (message.isEmpty()) {
            return;
        }
        sending = true;
        final int epoch = loadEpoch;
        final Long inReplyTo = replyTargetId;
        updateComposerVisibility();
        ChatLoader.INSTANCE.send(container, channelId, message, inReplyTo, acceptedId -> {
            if (epoch != loadEpoch) return kotlin.Unit.INSTANCE;
            sending = false;
            if (composer != null) composer.setFieldText("");
            updateComposerVisibility();
            // Read the cooked result around the accepted id; a past cursor excludes its anchor.
            clearReplyTarget();
            saveDraft();
            loadMessages(acceptedId, null, true);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch) return kotlin.Unit.INSTANCE;
            sending = false;
            FileLog.e("chat send failed", error);
            // A refused write keeps the text so it can be fixed and sent again - never replayed by
            // the client itself.
            if (composer != null) {
                composer.setFieldText(message);
            }
            updateComposerVisibility();
            showError("发送失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void updateComposerVisibility() {
        boolean canPost = lastSessionState instanceof AppSessionState.Authenticated
                && channel != null && channel.getCanPost() && !sending;
        if (inputViews != null) {
            inputViews.setVisibility(canPost ? View.VISIBLE : View.GONE);
        }
        applyListPadding();
    }

    private void updateComposerActions() {
        if (composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null && composer.getEditField().length() > 0;
        View button = composer.getSendButton();
        if (button != null) {
            button.setVisibility(hasText ? View.VISIBLE : View.GONE);
        }
        composer.checkSendButton(false);
    }

    private void onSwipeReply(ChatMessageCell cell) {
        MessageObject object = cell.getMessageObject();
        if (object == null) {
            return;
        }
        int index = bubbles.indexOf(object);
        if (index < 0 || index >= chatMessages.size()) {
            return;
        }
        ChatMessage target = chatMessages.get(index);
        replyTargetId = target.getId();
        String who = target.getUser() == null ? "" : target.getUser().getUsername();
        replyTargetLabel = "回复 " + who + "：" + excerptOf(target);
        if (replyStrip != null) {
            replyStrip.setText(replyTargetLabel + "   点一下取消");
            replyStrip.setVisibility(View.VISIBLE);
        }
        if (composer != null) {
            composer.setFieldFocused();
            AndroidUtilities.showKeyboard(composer.getEditField());
        }
    }

    private void clearReplyTarget() {
        replyTargetId = null;
        replyTargetLabel = null;
        if (replyStrip != null) {
            replyStrip.setVisibility(View.GONE);
        }
    }

    private String excerptOf(ChatMessage message) {
        String text = message.getMessage();
        if (text == null || text.isEmpty()) {
            text = message.getCooked().replaceAll("<[^>]+>", "");
        }
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    private void showError(String title, Throwable error) {
        if (getParentActivity() == null) {
            return;
        }
        FileLog.e(title, error);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(error.getMessage() == null ? error.toString() : error.getMessage());
        builder.setPositiveButton("好", null);
        showDialog(builder.create());
    }

    // ---------------------------------------------------------------------------------------------
    // Draft
    // ---------------------------------------------------------------------------------------------

    private void saveDraft() {
        if (container == null || channelId <= 0L || draftOwner == null) {
            return;
        }
        String raw = composer == null || composer.getFieldText() == null
                ? "" : composer.getFieldText().toString();
        container.getChatDraftStore().save(draftOwner,
                new io.github.baba983.nativedo.feature.chat.data.ChatDraft(raw, replyTargetId));
    }

    private io.github.baba983.nativedo.feature.chat.data.ChatDraftKey draftKey() {
        String username = container.getSessionStore().snapshot() == null
                ? "" : container.getSessionStore().snapshot().getUsername();
        return new io.github.baba983.nativedo.feature.chat.data.ChatDraftKey(
                container.getBaseUrl(), username == null ? "" : username, channelId);
    }

    private void restoreDraft() {
        if (container == null || composer == null || channelId <= 0L || draftOwner == null) {
            return;
        }
        io.github.baba983.nativedo.feature.chat.data.ChatDraft draft =
                container.getChatDraftStore().load(draftOwner);
        if (!draft.getRaw().isEmpty()) {
            composer.setFieldText(draft.getRaw());
        }
        if (draft.getInReplyToId() != null) {
            replyTargetId = draft.getInReplyToId();
        }
        updateComposerActions();
    }

    // ---------------------------------------------------------------------------------------------
    // Live updates
    // ---------------------------------------------------------------------------------------------

    private void startLiveUpdates() {
        closeLiveHolder();
        if (container == null || channel == null || !(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        MessageBusClient client = container.getMessageBusSession().getRunningClient();
        if (client == null) {
            // No foreground poller (signed out, backgrounded, or the bootstrap has not landed): the
            // screen falls back to explicit refresh and never claims a background push.
            return;
        }
        long seed = channel.getChannelMessageBusLastId() == null
                ? MessageBusClient.INITIAL_LAST_ID : channel.getChannelMessageBusLastId();
        long seedNew = channel.getNewMessagesBusLastId() == null
                ? MessageBusClient.INITIAL_LAST_ID : channel.getNewMessagesBusLastId();
        final int epoch = loadEpoch;
        liveHolder = new ChatChannelStateHolder(client, channelId, seed, seedNew,
                event -> onLiveEvent(event, epoch));
    }

    private kotlin.Unit onLiveEvent(ChatLiveEvent event, int epoch) {
        // The holder runs on the poller's dispatcher; the view tree is the main thread's.
        AndroidUtilities.runOnUIThread(() -> {
            if (epoch == loadEpoch) applyLiveEvent(event);
        });
        return kotlin.Unit.INSTANCE;
    }

    private void applyLiveEvent(ChatLiveEvent event) {
        if (event.getDeletedId() != null) {
            ChatMessage removed = null;
            for (ChatMessage message : chatMessages) {
                if (message.getId() == event.getDeletedId()) {
                    removed = message;
                    break;
                }
            }
            if (removed != null) {
                chatMessages.remove(removed);
                seenIds.remove(removed.getId());
                rebuildBubbles();
                notifyRowsChanged();
            }
            return;
        }
        ChatMessage message = event.getMessage();
        if (message == null) {
            return;
        }
        // Replace an edited message in place, or append a new one; never draw a message twice.
        for (int index = 0; index < chatMessages.size(); index++) {
            if (chatMessages.get(index).getId() == message.getId()) {
                chatMessages.set(index, message);
                rebuildBubbles();
                notifyRowsChanged();
                return;
            }
        }
        if (seenIds.add(message.getId())) {
            chatMessages.add(message);
            bubbles.add(buildBubble(message));
            notifyRowsChanged();
            scrollToNewest();
        }
    }

    private void closeLiveHolder() {
        if (liveHolder != null) {
            liveHolder.close();
            liveHolder = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Insets / layout
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public org.telegram.ui.ActionBar.EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return org.telegram.ui.ActionBar.EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat insets) {
        androidx.core.graphics.Insets bars = AndroidUtilities.getDefaultWindowInsets(insets, false);
        onInsets(bars.left, bars.top, bars.right, bars.bottom);
        if (inputInsets != null) {
            inputInsets.setInsets(insets);
        }
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        navigationBarHeight = bottom;
        if (inputViews != null) {
            inputViews.checkInsets();
        }
        applyListPadding();
    }

    private void applyListPadding() {
        if (listView == null) {
            return;
        }
        boolean composerShown = inputViews != null && inputViews.getVisibility() == View.VISIBLE;
        int bottom = composerShown ? composerHeight + AndroidUtilities.dp(8) : 0;
        listView.setPadding(0, AndroidUtilities.dp(4), 0, bottom + navigationBarHeight + AndroidUtilities.dp(4));
    }

    private void updateColors() {
        if (contentView != null) contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            actionBar.setSubtitleColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.updateColors();
        }
        if (glassHost != null) glassHost.updateColors();
        if (inputViews != null) inputViews.updateColors();
        if (composer != null) {
            composer.updateColors();
            composer.getEditField().setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
            composer.getEditField().setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
            composer.getEditField().setCursorColor(getThemedColor(Theme.key_chat_messagePanelCursor));
        }
        if (replyStrip != null) {
            replyStrip.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            replyStrip.setBackgroundColor(getThemedColor(Theme.key_chat_messagePanelBackground));
        }
        notifyRowsChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Test hooks
    // ---------------------------------------------------------------------------------------------

    PostListView listViewForTest() {
        return listView;
    }

    int messageCount() {
        return chatMessages.size();
    }

    boolean isComposerShown() {
        return inputViews != null && inputViews.getVisibility() == View.VISIBLE;
    }

    SessionUiState stateForTest() {
        return stateUi;
    }

    void joinForTest() {
        join();
    }

    void sendForTest(CharSequence text) {
        send(text);
    }

    void setComposerTextForTest(CharSequence text) {
        if (composer != null) {
            composer.setFieldText(text);
        }
    }

    /** The composer's current text, for a test that wants to know a refused write kept it. */
    String composerTextForTest() {
        if (composer == null || composer.getFieldText() == null) {
            return "";
        }
        return composer.getFieldText().toString();
    }

    ChatChannel channelForTest() {
        return channel;
    }

    private String statusLabel(ChatChannel channel) {
        if (!channel.isJoined()) {
            return channel.getCanJoin() ? "未加入" : "只读频道";
        }
        switch (channel.getStatus()) {
            case "read_only":
                return "只读";
            case "closed":
                return "已关闭";
            case "archived":
                return "已归档";
            case "open":
            default:
                return channel.getMembershipsCount() + " 人";
        }
    }

    /** The note a non-message row shows, or null when there is nothing to say. */
    private String noteText() {
        if (channel == null) {
            return null;
        }
        if (!channel.isJoined()) {
            return channel.getCanJoin() ? "未加入，点一下加入" : "该频道不可加入";
        }
        if (!channel.isOpen()) {
            return "频道当前为" + statusLabel(channel) + "，无法发言";
        }
        return null;
    }

    private boolean isNoteTappable() {
        return channel != null && !channel.isJoined() && channel.getCanJoin();
    }

    private void onRowTap() {
        if (stateUi != null) {
            onStateAction();
            return;
        }
        if (noteText() != null && isNoteTappable()) {
            join();
        }
    }

    private void onStateAction() {
        if (stateUi == null) {
            return;
        }
        SessionUiAction action = SessionUi.action(stateUi);
        if (action == SessionUiAction.Retry) {
            stateUi = null;
            notifyRowsChanged();
            if (channel == null) {
                loadChannel();
            } else {
                loadMessages(null, null, true);
            }
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
        loadChannel();
    }

    // ---------------------------------------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------------------------------------

    private boolean hasNoteRow() {
        return stateUi == null && noteText() != null;
    }

    private int messageRowOffset() {
        return hasNoteRow() ? 1 : 0;
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
            ChatMessageCell cell = new ChatMessageCell(
                    context, currentAccount, false, sharedResources, resourceProvider);
            cell.setDelegate(new CellDelegate());
            cell.isChat = true;
            cell.hasDiscussion = false;
            cell.linkedChatId = 0;
            cell.setFullyDraw(false);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextInfoPrivacyCell) {
                String text = stateUi != null
                        ? SessionUi.text(holder.itemView.getContext(), stateUi).toString()
                        : noteText();
                TextInfoPrivacyCell privacy = (TextInfoPrivacyCell) holder.itemView;
                privacy.setText(text == null ? "" : text);
                if (stateUi != null) {
                    privacy.setOnClickListener(SessionUi.hasAction(stateUi) ? v -> onStateAction() : null);
                } else {
                    privacy.setOnClickListener(isNoteTappable() ? v -> join() : null);
                }
                return;
            }
            int index = position - messageRowOffset();
            if (index < 0 || index >= bubbles.size()) {
                return;
            }
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = bubbles.get(index);
            boolean pinnedTop = index > 0 && sameAuthor(index - 1, index);
            boolean pinnedBottom = index < bubbles.size() - 1 && sameAuthor(index, index + 1);
            cell.setMessageObject(message, null, pinnedBottom, pinnedTop, false);
            if (message.richLayout != null) {
                message.richLayout.attach(cell);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (stateUi != null) {
                return TYPE_STATE;
            }
            if (hasNoteRow() && position == 0) {
                return TYPE_NOTE;
            }
            return TYPE_MESSAGE;
        }

        @Override
        public int getItemCount() {
            if (stateUi != null) {
                return 1;
            }
            return messageRowOffset() + bubbles.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (stateUi != null) {
                return SessionUi.hasAction(stateUi);
            }
            if (holder.itemView instanceof TextInfoPrivacyCell) {
                return isNoteTappable();
            }
            return false;
        }
    }

    private boolean sameAuthor(int a, int b) {
        ChatMessage first = chatMessages.get(a);
        ChatMessage second = chatMessages.get(b);
        String ua = first.getUser() == null ? "" : first.getUser().getUsername();
        String ub = second.getUser() == null ? "" : second.getUser().getUsername();
        return !ua.isEmpty() && ua.equals(ub);
    }

    /** Upstream's own callbacks; nothing here invents a path Telegram's cells do not own. */
    private class CellDelegate implements ChatMessageCell.ChatMessageCellDelegate {
        @Override
        public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
            cell.forceResetMessageObject();
            cell.requestLayout();
            cell.invalidate();
        }

        @Override
        public void forceUpdate(ChatMessageCell cell, boolean anchorScroll, boolean scrollByTop) {
            forceUpdate(cell, anchorScroll);
        }
    }

    private class SendDelegate extends DiscourseComposerDelegate {
        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate,
                int scheduleRepeatPeriod, long payStars) {
            send(message);
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
            saveDraft();
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }
    }
}
