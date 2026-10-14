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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.RichMessageLayout;
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
import java.util.Map;

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
import io.github.baba983.nativedo.topic.DiscourseComposerTools;
import io.github.baba983.nativedo.topic.DiscourseImageBridge;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.PostImage;
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

    /** The site's own emoji / @mention tools over the composer; null until the screen is built. */
    private DiscourseComposerTools composerTools;
    /**
     * The floating round page-down button, upstream's own (the one a chat floats over its composer):
     * it carries the count of messages that arrived below a reader who is reading history, and its
     * tap jumps to them.
     */
    private org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton pendingButton;
    /** How many arrivals the reader has not seen yet; 0 hides the button. */
    private int pendingCount;

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
                // Reaching the newest is what makes the arrivals seen; the count and its button go.
                if (pendingCount > 0 && isAtBottom()) {
                    clearPending();
                    markNewestRead();
                }
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
            if (composerTools != null) {
                composerTools.updatePosition(0);
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
        // The site's own input tools over the composer: the linux.do emoji catalog, the @mention
        // panel and their Back/lifecycle - the same tools a topic reply and a new topic install.
        // A channel has no topic, so the tools are given a null topic (an @mention carries no
        // invented topic context).
        composerTools = new DiscourseComposerTools(
                this, container, composer, inputViews, inputInsets, glassHost, null);

        // The floating round page-down affordance, upstream's own (the one a chat floats over its
        // composer): a message that arrives while the reader is in history lands here with its
        // count, and the tap goes to it.
        pendingButton =
                org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton.create(
                        context, 56, 48, resourceProvider, glassHost.getFactory(), inputProvider,
                        org.telegram.messenger.R.drawable.pagedown);
        pendingButton.setClipChildren(false);
        pendingButton.setClipToPadding(false);
        pendingButton.setVisibility(View.GONE);
        pendingButton.setPivotX(AndroidUtilities.dp(28));
        pendingButton.setPivotY(AndroidUtilities.dp(28 + 8));
        pendingButton.setOnClickListener(v -> onPendingTap());
        contentView.addView(pendingButton, LayoutHelper.createFrame(
                56, 64, Gravity.RIGHT | Gravity.BOTTOM));
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
                if (composerTools != null) {
                    composerTools.updatePosition(0);
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
            if (composerTools != null) {
                composerTools.updatePosition(0);
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
        if (composerTools != null) composerTools.onResume();
        updateColors();
        startLiveUpdates();
    }

    @Override
    public void onPause() {
        saveDraft();
        closeLiveHolder();
        if (composerTools != null) composerTools.onPause();
        if (composer != null) composer.onPause();
        super.onPause();
    }

    /**
     * Back means the site's input tools first (an open emoji catalog or @mention panel), the channel
     * second - the same order a topic reply and a new topic keep.
     */
    @Override
    public boolean onBackPressed(boolean invoked) {
        if (composerTools != null && composerTools.onBackPressed(invoked)) {
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        // The screen is gone: a block that outlives it must not call back to start a fetch for a row
        // nobody is looking at.
        for (Map.Entry<String, Runnable> entry : photoStarters.entrySet()) {
            DiscourseImageBridge.INSTANCE.clearPhotoStarter(entry.getKey(), entry.getValue());
        }
        photoStarters.clear();
        stopSessionObservation();
        closeLiveHolder();
        loadEpoch++;
        saveDraft();
        if (composerTools != null) {
            composerTools.onDestroy();
            composerTools = null;
        }
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
        clearPending();
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
        loadMessages(targetMessageId, direction, replace, false);
    }

    /**
     * Reads one window. [landOnTarget] asks the list to land on the newest message when the window
     * arrives - a send's own readback and an explicit jump want that; a plain refresh keeps the
     * reader's place instead, and the initial entry lands on the newest because it starts there.
     */
    private void loadMessages(Long targetMessageId, String direction, boolean replace, boolean landOnTarget) {
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
                    applyPage(loaded, askedReplace, landOnTarget);
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

    private void applyPage(ChatMessagePage page, boolean replace, boolean landOnTarget) {
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        int anchor = manager == null ? -1 : manager.findFirstVisibleItemPosition();
        View anchorView = manager == null ? null : manager.findViewByPosition(anchor);
        int anchorTop = anchorView == null ? 0 : anchorView.getTop() - listView.getPaddingTop();
        Long anchorMessageId = null;
        if (anchor >= 0) {
            int index = anchor - messageRowOffset();
            if (index >= 0 && index < chatMessages.size()) {
                anchorMessageId = chatMessages.get(index).getId();
            }
        }
        boolean wasEmpty = chatMessages.isEmpty();
        boolean wasAtBottom = isAtBottom();
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
            // A window that replaces the view keeps the reader's place unless they were already at
            // the newest (an entry, a send's readback, or an explicit jump go to the newest). This
            // is what stops a refresh from yanking a reader out of the history they are reading.
            boolean follow = landOnTarget || wasEmpty || wasAtBottom;
            if (follow) {
                scrollToNewest();
                listView.post(this::markNewestReadIfSeen);
            } else if (anchorMessageId != null && manager != null) {
                int restored = indexOfMessage(anchorMessageId);
                if (restored >= 0) {
                    manager.scrollToPositionWithOffset(messageRowOffset() + restored, anchorTop);
                }
                // A refresh that kept the reader's place never credits the unseen tail as read.
            }
            // A fresh full window supersedes whatever arrivals the button was counting.
            clearPending();
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
            PreparedPostBody body = prepareBody(message);
            bodies.add(body);
            bubbles.add(buildBubble(message, body));
        }
        for (TLRPC.User user : users.values()) {
            getMessagesController().putUser(user, true);
        }
        // The pictures a message names are fetched after the rows exist, each reported as it lands.
        for (int index = 0; index < bodies.size(); index++) {
            fetchMessageMedia(index);
        }
    }

    /**
     * Where a message's own pictures are started (and retried): the module fetches them itself, so
     * the row's download button has to reach this screen to begin a transfer, and its state has to
     * come back from the fetch rather than from a Telegram download that does not exist.
     */
    private final Map<String, Runnable> photoStarters = new HashMap<>();

    /**
     * Fetches the pictures one message draws, reporting each as its bytes land so a picture already
     * on disk is drawn without waiting behind a slow neighbour. A picture the site serves as a
     * preview then an original reports again when the original replaces it.
     */
    private void fetchMessageMedia(int index) {
        if (container == null || index < 0 || index >= bodies.size()) {
            return;
        }
        PreparedPostBody body = bodies.get(index);
        if (body == null) {
            return;
        }
        List<PostImage> images = body.getImages();
        if (images == null || images.isEmpty()) {
            return;
        }
        for (PostImage image : images) {
            String name = DiscourseImageBridge.INSTANCE.photoFileName(image);
            if (photoStarters.containsKey(name)) continue;
            Runnable starter = () -> DiscourseLoader.INSTANCE.fetchPostPhoto(container, image, chat -> {
                refreshMessageMedia(chat);
                return kotlin.Unit.INSTANCE;
            });
            DiscourseImageBridge.INSTANCE.registerPhotoStarter(name, starter);
            photoStarters.put(name, starter);
        }
        DiscourseLoader.INSTANCE.cachePostPhotos(container, images, chat -> {
            refreshMessageMedia(chat);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Draws whatever the fetch has put on disk for one picture, in every row that names it. A row
     * keeps its {@link MessageObject} and its place: the retained block re-reads its file and the
     * row is bound again where it sits.
     */
    private void refreshMessageMedia(PostImage image) {
        if (isFinished || listView == null || !(listView.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }
        FileLoader loader = FileLoader.getInstance(0);
        for (int index = 0; index < bodies.size(); index++) {
            PreparedPostBody body = bodies.get(index);
            if (body == null || !body.getImages().contains(image)) {
                continue;
            }
            MessageObject message = bubbles.get(index);
            if (message.richLayout != null) {
                for (RichMessageLayout.RichBlock block : message.richLayout.blocks) {
                    if (!(block instanceof RichMessageLayout.RichPhotoBlock)) continue;
                    RichMessageLayout.RichPhotoBlock photo = (RichMessageLayout.RichPhotoBlock) block;
                    if (photo.sizeFull == null) continue;
                    boolean fullExists = loader.getPathToAttach(photo.sizeFull, true).exists()
                            || loader.getPathToAttach(photo.sizeFull, false).exists();
                    boolean previewExists = photo.strippedSize != null
                            && (loader.getPathToAttach(photo.strippedSize, true).exists()
                                || loader.getPathToAttach(photo.strippedSize, false).exists());
                    if (!fullExists && !previewExists) continue;
                    if (fullExists && photo.imageReceiver.getImageLocation() != null
                            || !fullExists && photo.imageReceiver.getThumbLocation() != null) {
                        photo.updateButtonState(true);
                        continue;
                    }
                    // Only what is on disk is handed over: a location with no file behind it would
                    // send upstream's loader after a datacenter this client does not have.
                    photo.imageReceiver.cancelLoadImage();
                    photo.imageReceiver.setImage(null, null,
                            fullExists ? io.github.baba983.nativedo.hooks.NativeDoHooks.photoImageLocation(photo.sizeFull, photo.photo) : null, null,
                            photo.strippedSize != null && previewExists
                                    ? io.github.baba983.nativedo.hooks.NativeDoHooks.photoImageLocation(photo.strippedSize, photo.photo) : null,
                            "b1", null, photo.sizeFull.size, null, message, 1);
                    photo.updateButtonState(true);
                }
            }
            invalidateRow(messageRowOffset() + index);
        }
    }

    /** The prepared rich body of one message's cooked HTML; what the bubble draws. */
    private PreparedPostBody prepareBody(ChatMessage message) {
        io.github.baba983.nativedo.core.model.Post post = ChatRows.post(message);
        return DiscourseImageBridge.INSTANCE.prepare(post.getCookedHtml(), container.getBaseUrl());
    }

    private MessageObject buildBubble(ChatMessage message, PreparedPostBody body) {
        io.github.baba983.nativedo.core.model.Post post = ChatRows.post(message);
        TLRPC.Message replyTo = null;
        if (message.getInReplyTo() != null) {
            io.github.baba983.nativedo.core.model.Post target = ChatRows.replyTarget(message.getInReplyTo());
            replyTo = DiscourseRows.replySnapshot(
                    target, channelId, users, container.getBaseUrl(), message.getInReplyTo().getExcerpt());
        }
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
        refreshLocalCandidates();
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
            // The row draws its author's picture from the downloaded file on its next draw, so a
            // picture arriving is a redraw of that author's rows - never a refresh of the adapter.
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                invalidateAvatarRows(user.id);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /** Redraws the rows whose author is [senderId]; a picture arriving is a draw, not a rebind. */
    private void invalidateAvatarRows(long senderId) {
        if (listView == null || !(listView.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        for (int index = 0; index < chatMessages.size(); index++) {
            ChatMessage message = chatMessages.get(index);
            if (message.getUser() == null
                    || DiscourseRows.userId(message.getUser().getUsername()) != senderId) {
                continue;
            }
            View view = manager.findViewByPosition(messageRowOffset() + index);
            if (view != null) {
                view.invalidate();
            }
        }
    }

    /** The authors the channel has shown, for the composer's own @mention candidates. */
    private void refreshLocalCandidates() {
        if (composerTools == null || container == null) {
            return;
        }
        java.util.List<io.github.baba983.nativedo.core.model.Post> posts = new ArrayList<>();
        for (ChatMessage message : chatMessages) {
            posts.add(ChatRows.post(message));
        }
        composerTools.setLocalCandidates(
                java.util.Collections.<io.github.baba983.nativedo.core.model.MentionCandidate>emptyList(),
                posts);
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
            listView.scrollToPosition(messageRowOffset() + chatMessages.size() - 1);
        }
    }

    /**
     * Whether the list is showing its very last row: upstream's own at-the-bottom test. A reader at
     * the bottom follows new messages; one anywhere else keeps their place and is told instead.
     */
    private boolean isAtBottom() {
        return listView == null || !listView.canScrollVertically(1);
    }

    private int indexOfMessage(long id) {
        for (int index = 0; index < chatMessages.size(); index++) {
            if (chatMessages.get(index).getId() == id) {
                return index;
            }
        }
        return -1;
    }

    private void markNewestRead() {
        if (container == null || chatMessages.isEmpty() || channel == null || !channel.isJoined()
                || !(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        ChatMessage newest = chatMessages.get(chatMessages.size() - 1);
        ChatLoader.INSTANCE.markRead(container, channelId, newest.getId());
    }

    /**
     * Credits the newest message as read only when it is really in front of the reader. An arrival
     * below a reader who is in history is not seen, and marking it read would silently clear a count
     * the site keeps on the reader's behalf.
     */
    private void markNewestReadIfSeen() {
        if (!isAtBottom()) {
            return;
        }
        markNewestRead();
    }

    /** Announces the arrivals a history reader has not seen yet. */
    private void showPending() {
        if (pendingButton == null) {
            return;
        }
        pendingButton.setCount(pendingCount, false);
        pendingButton.setContentDescription(pendingCount > 0
                ? "下方还有 " + pendingCount + " 条新消息" : null);
        pendingButton.setVisibility(pendingCount > 0 ? View.VISIBLE : View.GONE);
    }

    /** When the reader reaches the newest, the arrivals are no longer unseen. */
    private void clearPending() {
        pendingCount = 0;
        if (pendingButton != null) {
            pendingButton.setCount(0, false);
            pendingButton.setVisibility(View.GONE);
        }
    }

    /** The reader's tap on the arrivals: to the newest, which is then seen. */
    private void onPendingTap() {
        if (listView == null) {
            return;
        }
        scrollToNewest();
        clearPending();
        listView.post(this::markNewestRead);
    }

    /** The arrivals button floats over the composer at the trailing edge, lifted by its band. */
    private void placePending() {
        if (pendingButton == null) {
            return;
        }
        boolean composerShown = inputViews != null && inputViews.getVisibility() == View.VISIBLE;
        int bottom = (composerShown ? composerHeight + AndroidUtilities.dp(8) : AndroidUtilities.dp(8))
                + navigationBarHeight;
        ViewGroup.LayoutParams params = pendingButton.getLayoutParams();
        if (params instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams frame = (android.widget.FrameLayout.LayoutParams) params;
            if (frame.bottomMargin != bottom) {
                frame.bottomMargin = bottom;
                pendingButton.setLayoutParams(frame);
            }
        }
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
            loadMessages(acceptedId, null, true, true);
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
            removeLiveMessage(event.getDeletedId());
            return;
        }
        ChatMessage message = event.getMessage();
        if (message == null) {
            return;
        }
        // Replace an edited message in place, or append a new one; never draw a message twice.
        int index = indexOfMessage(message.getId());
        if (index >= 0) {
            applyLiveEdit(index, message);
        } else if (seenIds.add(message.getId())) {
            appendLiveMessage(message);
        }
    }

    /**
     * An edit changes one row. The row keeps its {@link MessageObject}; only the rich body it draws
     * is swapped for the freshly prepared one and the row is laid out again where it sits. Every
     * other message keeps its own object, and nothing is notified to the adapter, so no row is
     * removed, inserted or recycled (which a plain {@code notifyItemChanged} would do through the
     * list's item animator).
     */
    private void applyLiveEdit(int index, ChatMessage message) {
        chatMessages.set(index, message);
        PreparedPostBody body = prepareBody(message);
        MessageObject rebuilt = buildBubble(message, body);
        MessageObject target = bubbles.get(index);
        bodies.set(index, body);
        target.messageOwner.rich_message = rebuilt.messageOwner.rich_message;
        target.generateLayout(null);
        for (TLRPC.User user : users.values()) {
            getMessagesController().putUser(user, true);
        }
        invalidateRow(messageRowOffset() + index);
        fetchAvatars();
        refreshLocalCandidates();
    }

    /**
     * A message that arrived live: appended below what is already drawn. A reader at the bottom
     * follows it and it is seen; a reader anywhere else keeps their place and the button counts it.
     */
    private void appendLiveMessage(ChatMessage message) {
        boolean wasAtBottom = isAtBottom();
        chatMessages.add(message);
        PreparedPostBody body = prepareBody(message);
        bodies.add(body);
        bubbles.add(buildBubble(message, body));
        for (TLRPC.User user : users.values()) {
            getMessagesController().putUser(user, true);
        }
        MessageAdapter adapter = messageAdapter();
        if (adapter != null && stateUi == null) {
            int position = messageRowOffset() + bubbles.size() - 1;
            adapter.notifyItemInserted(position);
            // The run boundary above the new message may have changed: its bottom pin is recomputed.
            if (chatMessages.size() >= 2 && sameAuthor(chatMessages.size() - 2, chatMessages.size() - 1)) {
                invalidateRow(messageRowOffset() + bubbles.size() - 2);
            }
        }
        fetchAvatars();
        refreshLocalCandidates();
        if (wasAtBottom) {
            scrollToNewest();
            listView.post(this::markNewestRead);
            clearPending();
        } else {
            pendingCount++;
            showPending();
        }
    }

    /**
     * Removes the one message a deletion names, with a scoped removal notice: every other row keeps
     * its object and place, and the neighbour's run boundary is recomputed.
     */
    private void removeLiveMessage(long deletedId) {
        int index = indexOfMessage(deletedId);
        if (index < 0) {
            return;
        }
        ChatMessage removed = chatMessages.remove(index);
        seenIds.remove(removed.getId());
        bubbles.remove(index);
        if (index < bodies.size()) {
            bodies.remove(index);
        }
        MessageAdapter adapter = messageAdapter();
        if (adapter != null && stateUi == null) {
            adapter.notifyItemRemoved(messageRowOffset() + index);
            if (index > 0) {
                invalidateRow(messageRowOffset() + index - 1);
            }
            if (index < bubbles.size()) {
                invalidateRow(messageRowOffset() + index);
            }
        }
        refreshLocalCandidates();
    }

    /** The adapter, when the list has one; null before the screen is built. */
    private MessageAdapter messageAdapter() {
        if (listView == null || !(listView.getAdapter() instanceof MessageAdapter)) {
            return null;
        }
        return (MessageAdapter) listView.getAdapter();
    }

    /** Re-lays-out and redraws the row at [adapterPosition] without touching the adapter. */
    private void invalidateRow(int adapterPosition) {
        if (listView == null || listView.getLayoutManager() == null) {
            return;
        }
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(adapterPosition);
        View view = holder == null ? null : holder.itemView;
        if (view instanceof ChatMessageCell) {
            ChatMessageCell cell = (ChatMessageCell) view;
            cell.forceResetMessageObject();
            int index = adapterPosition - messageRowOffset();
            cell.setMessageObject(bubbles.get(index), null,
                    index < bubbles.size() - 1 && sameAuthor(index, index + 1),
                    index > 0 && sameAuthor(index - 1, index), false);
            view.requestLayout();
            view.invalidate();
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
        placePending();
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
        if (pendingButton != null) pendingButton.updateColors();
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

    /** The MessageObject drawn at [index], so a test can tell a rebuilt row from the same one. */
    MessageObject bubbleForTest(int index) {
        return index >= 0 && index < bubbles.size() ? bubbles.get(index) : null;
    }

    /** The model drawn at [index], so a test can tell an updated row from a replaced one. */
    ChatMessage chatMessageForTest(int index) {
        return index >= 0 && index < chatMessages.size() ? chatMessages.get(index) : null;
    }

    /** Whether the pending-arrivals button is on screen. */
    boolean isPendingShownForTest() {
        return pendingButton != null && pendingButton.getVisibility() == View.VISIBLE;
    }

    /** How many arrivals the reader has not seen yet. */
    int pendingCountForTest() {
        return pendingCount;
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

    /** The same window read the action bar's refresh asks for. */
    void refreshForTest() {
        loadMessages(null, null, true);
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
