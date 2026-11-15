package io.github.baba983.nativedo.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
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

import io.github.baba983.nativedo.R;
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
import io.github.baba983.nativedo.core.media.VoiceRecording;
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback;
import io.github.baba983.nativedo.core.presence.TypingPresenceController;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.core.web.DiscourseLinks;
import io.github.baba983.nativedo.core.web.DiscourseTopicLink;
import io.github.baba983.nativedo.feature.chat.data.ChatAttachmentState;
import io.github.baba983.nativedo.feature.chat.data.ChatAttachmentStaging;
import io.github.baba983.nativedo.feature.chat.data.StagedChatAttachment;
import io.github.baba983.nativedo.hooks.NativeDoHooks;
import io.github.baba983.nativedo.profile.ProfileFragment;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.ArticlePhotoViewer;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseComposerDelegate;
import io.github.baba983.nativedo.topic.DiscourseComposerTools;
import io.github.baba983.nativedo.topic.ComposerVoiceInput;
import io.github.baba983.nativedo.topic.ComposerVoicePanel;
import io.github.baba983.nativedo.topic.DiscourseImageBridge;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.PostAttachment;
import io.github.baba983.nativedo.topic.PostImage;
import io.github.baba983.nativedo.topic.PostListView;
import io.github.baba983.nativedo.topic.PostVideo;
import io.github.baba983.nativedo.topic.PreparedPostBody;
import io.github.baba983.nativedo.topic.TopicFragment;
import io.github.baba983.nativedo.topic.TypingDotsSubtitle;

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

    /** The system picker's own request code; the chat screen never shares it with a topic. */
    private static final int PICK_ATTACHMENT_REQUEST = 0x4e48;

    /** A chat message carries a few files, not a gallery: the band stays readable and bounded. */
    private static final int MAX_STAGED_ATTACHMENTS = 4;

    /** The header's overflow capsule; its rows are the channel's actions. */
    private static final int MENU_ITEM_MORE = 1;
    /** The overflow's reload row: read the newest window again. */
    private static final int MENU_ITEM_REFRESH = 2;

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
    /** The floating header capsule: the channel's picture, name and status or typers. */
    private org.telegram.ui.Components.ChatAvatarContainer avatarContainer;
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
     * The composer's own delegate, kept so the recording host can be bound to it (the delegate
     * carries upstream's microphone gesture in, see {@link DiscourseComposerDelegate}). Null until
     * the composer exists.
     */
    private DiscourseComposerDelegate composerDelegate;
    /** This client's hold-to-record microphone over the composer's mic affordance. */
    private ComposerVoiceInput voiceInput;
    /** The local take preview and received-audio player; one at a time. */
    private io.github.baba983.nativedo.core.media.MediaPlayerVoiceRecordingPreview voicePreview;
    /** The voice band over the composer: live recording, the finished take, and its retry. */
    private ComposerVoicePanel voicePanel;
    /** The finished take awaiting send, or null. Kept across a failed upload/send for a retry. */
    private VoiceRecording pendingRecording;
    /** The upload id the site already accepted for [pendingRecording]; a retry reuses it. */
    private Long pendingUploadId;
    /** True while the take is on its way to the site (upload or send), so no second capture starts. */
    private boolean uploadingVoice;
    private TypingPresenceController typingController;
    private String typingSubtitle = "";
    /** Upstream's animated typing dots before the header's typing line, while it is shown. */
    private final TypingDotsSubtitle typingDots = new TypingDotsSubtitle(true);

    /**
     * The files the reader staged for the next message, in pick order, with the upload ids the site
     * accepted for them. A pick stages; only the composer's Send writes, and the accepted ids are
     * kept across a failed write so its retry never uploads the same file twice.
     */
    private final ChatAttachmentStaging stagedAttachments = new ChatAttachmentStaging();
    /** The staged-file band over the composer; null until the screen is built. */
    private ComposerAttachmentStrip attachmentStrip;
    /** The received audio clip currently playing through [voicePreview], or null. */
    private String playingAudioUrl;
    /**
     * The floating round page-down button, upstream's own (the one a chat floats over its composer):
     * it carries the count of messages that arrived below a reader who is reading history, and its
     * tap jumps to them.
     */
    private org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton pendingButton;
    /** How many arrivals the reader has not seen yet; 0 hides the button. */
    private int pendingCount;

    /**
     * The chat's own floating date capsule: upstream's floating date view
     * (ChatActivity:7043-7100), a {@link ChatActionCell} whose text is the chat date the message at
     * the top of the viewport itself carries. It floats over the rows while the reader scrolls and
     * leaves once the scroll settles (ChatActivity:13604-13658). It is never a list row.
     */
    private ChatActionCell floatingDateView;
    /** ChatActivity's own floatingDateAnimation (13612-13624): one 150 ms alpha set, tag-guarded. */
    private AnimatorSet floatingDateAnimation;
    /**
     * Upstream hides the floating date from the list's own idle callback (6778-6782). That callback
     * is not guaranteed on every host, so the same fade is also armed by the scroll itself and goes
     * this many milliseconds after the last scroll event - the pause upstream puts in
     * {@code hideDateDelay} (13628/13656).
     */
    private static final int FLOATING_DATE_HIDE_DELAY_MS = 500;

    /** How long a short-tap hold hint stays up before the voice band puts itself away. */
    private static final int HOLD_HINT_MILLIS = 1_200;
    /** The deferred fade armed by a scroll; cancelled on idle and on destroy. */
    private final Runnable floatingDateHide = () -> {
        if (!isFinished) {
            hideFloatingDate();
        }
    };

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

        // The chat header floats over the conversation the way the topic's does: the bar is kept
        // out of the container (ChatActivity:3678) and given to this screen's own frame, with no
        // background and the chat's own back arrow (3680-3686). Its actions live in the overflow
        // capsule, not as icons beside the title.
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setBackground(null);
        actionBar.setBackButtonDrawable(new org.telegram.ui.ActionBar.BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ITEM_REFRESH) {
                    if (!sending && !joining) loadMessages(null, null, true);
                }
            }
        });
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        org.telegram.ui.ActionBar.ActionBarMenuItem overflow =
                menu.addItem(MENU_ITEM_MORE, org.telegram.messenger.R.drawable.ic_ab_other);
        overflow.setContentDescription("更多");
        overflow.addSubItem(MENU_ITEM_REFRESH, org.telegram.messenger.R.drawable.msg_retry, "刷新");

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
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                // ChatActivity:6778-6782: the scroll has settled, so the floating date goes (and any
                // settle-timer it armed is dropped).
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    AndroidUtilities.cancelRunOnUIThread(floatingDateHide);
                    hideFloatingDate();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadOlderIfNeeded();
                // Reaching the newest is what makes the arrivals seen; the count and its button go.
                if (pendingCount > 0 && isAtBottom()) {
                    clearPending();
                    markNewestRead();
                }
                if (dy != 0) {
                    // ChatActivity:6876-6881: while the reader scrolls, the floating capsule carries
                    // the chat date of the message now at the top of the viewport.
                    updateFloatingDate();
                    showFloatingDate();
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        // The screen's one glass source: the header capsules and the composer island both sample
        // it, so it is built before either (ChatActivity builds it before its views, 2620-2661).
        glassHost = new GlassPanelHost(listView, contentView);

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

        // The chat's own floating date capsule (ChatActivity:7043-7100): a ChatActionCell whose
        // text is the chat date of the message at the top of the viewport. It is a child of the
        // screen's frame, not a list row - it floats under the header at the top centre, so it
        // consumes no row and can never reach the composer. The theme delegate keeps the night
        // capsule opaque, the same fix the topic list's own pill uses (TopicListFragment:3498).
        floatingDateView = new ChatActionCell(context, false, new DatePillThemeProvider()) {
            // ChatActivity:7090-7094: an alpha of zero is what takes the view out; the fade is the
            // only visibility state there is.
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        };
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floatingDateView.setInvalidateColors(true);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // The composer rides in upstream's floating island the way the reply box does.
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

        // The voice band floats over the composer's own band, above the mic it answers to. It is
        // hidden until a capture starts and never a list row, so it cannot reach the adapter.
        voicePanel = new ComposerVoicePanel(context, resourceProvider);
        voicePanel.setVisibility(View.GONE);
        contentView.addView(voicePanel, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM, 0, 0, 0, 0));
        bindVoiceInput();

        // The staged files sit on the same band the voice panel uses, above the field they will be
        // sent with. A chip is a preview and its remove; the composer's own Send is the write.
        attachmentStrip = new ComposerAttachmentStrip(context, resourceProvider);
        attachmentStrip.setVisibility(View.GONE);
        attachmentStrip.setOnRemove(id -> {
            removeStagedAttachment(id);
            return kotlin.Unit.INSTANCE;
        });
        contentView.addView(attachmentStrip, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM, 0, 0, 0, 0));
        refreshAttachments();

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

        // The chat's own header capsule: the channel's name, its status or the readers typing,
        // and its picture. It is the same ChatAvatarContainer the topic header uses, with the same
        // glass capsules for the back button and the overflow, floating over the conversation.
        avatarContainer = new org.telegram.ui.Components.ChatAvatarContainer(
                context, this, false, resourceProvider);
        avatarContainer.setGlassMode();
        avatarContainer.setClipChildren(false);
        avatarContainer.setActionBar(actionBar);
        avatarContainer.getTitleTextView().setScrollNonFitText(true);
        // A tap on the capsule has no destination here: a channel has no native profile screen.
        avatarContainer.setOnClickListener(null);
        setCapsuleTitle(channelTitle == null || channelTitle.isEmpty() ? "频道" : channelTitle);
        setCapsuleAvatar(channelTitle);
        actionBar.setChatAvatarContainer(avatarContainer);
        actionBar.addView(avatarContainer, 0, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                Gravity.TOP | Gravity.LEFT, 52, 0, 52, 0));
        actionBar.createMenu().bringToFront();
        actionBar.setupGlass(
                glassHost.getFactory(),
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .topPanelChatActivity(resourceProvider));
        contentView.addView(actionBar);
        applyListPadding();

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
        composerDelegate = new SendDelegate();
        view.setDelegate(composerDelegate);
        installRecordAccessibility(view);
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
    // Voice recording
    // ---------------------------------------------------------------------------------------------

    /**
     * Binds this client's hold-to-record microphone to the composer's own delegate. The delegate
     * carries upstream's touch gesture in and forwards it here; no process-wide table is involved.
     * The recording feedback is the voice band, and [captureAllowed] keeps a hold from starting
     * while a send, an upload or a recovery is in flight.
     */
    private void bindVoiceInput() {
        if (composer == null || composerDelegate == null || getParentActivity() == null) {
            return;
        }
        voicePreview = new io.github.baba983.nativedo.core.media.MediaPlayerVoiceRecordingPreview();
        voiceInput = ComposerVoiceInput.bind(
                getParentActivity(),
                composer,
                composerDelegate,
                voiceFeedback(),
                recording -> {
                    onRecordingFinished(recording);
                    return kotlin.Unit.INSTANCE;
                },
                () -> !uploadingVoice && !sending && !joining && stagedAttachments.isEmpty()
                        && lastSessionState instanceof AppSessionState.Authenticated);
        installVoicePanelActions();
    }

    /** The band's own recording feedback; the panel draws, the input drives it. */
    private VoiceRecordingFeedback voiceFeedback() {
        return new VoiceRecordingFeedback() {
            @Override
            public void onHoldHint() {
                showVoicePanel();
                if (voicePanel == null) {
                    return;
                }
                voicePanel.showHoldHint();
                voicePanel.postDelayed(() -> {
                    if (voicePanel != null && voicePanel.getState() == ComposerVoicePanel.State.Recording
                            && voiceInput != null && !voiceInput.isRecording()) {
                        voicePanel.hide();
                    }
                }, HOLD_HINT_MILLIS);
            }

            @Override
            public void onRecordingStarted() {
                showVoicePanel();
                if (voicePanel != null) voicePanel.showRecording();
            }

            @Override
            public void onRecordingTick(long durationMillis) {
                if (voicePanel != null) voicePanel.updateRecording(durationMillis);
            }

            @Override
            public void onRecordingFinished() {
                if (voicePanel != null) voicePanel.hide();
            }

            @Override
            public void onRecordingDiscarded() {
                if (voicePanel != null) voicePanel.hide();
            }

            @Override
            public void onRecordingFailed(String message) {
                if (voicePanel != null) voicePanel.showFailure(message == null ? "无法开始录音" : message);
                showVoicePanel();
            }

            @Override
            public void onPermissionRequired() {
                // The permission prompt is the feedback; the band says nothing extra.
            }
        };
    }

    /**
     * A finished local take: it becomes the review band, never a message. The reader checks it,
     * plays it back, and either sends it (uploaded as `chat-composer`, then referenced by
     * `upload_ids`) or throws it away. Nothing reaches the site until Send.
     */
    private void onRecordingFinished(VoiceRecording recording) {
        pendingRecording = recording;
        pendingUploadId = null;
        if (voicePanel != null) {
            voicePanel.showReview(recording.getDurationMillis());
        }
        showVoicePanel();
    }

    private void showVoicePanel() {
        if (voicePanel == null) {
            return;
        }
        voicePanel.setVisibility(View.VISIBLE);
        positionVoicePanel();
    }

    /** The band sits on top of the composer's own band, above the mic it answers to. */
    private void positionVoicePanel() {
        if (voicePanel == null || !(voicePanel.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams)) {
            return;
        }
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) voicePanel.getLayoutParams();
        boolean composerShown = inputViews != null && inputViews.getVisibility() == View.VISIBLE;
        int bottom = (composerShown ? composerHeight : 0) + navigationBarHeight;
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            voicePanel.setLayoutParams(params);
        }
        positionAttachmentStrip();
    }

    /**
     * The staged-file band sits on the composer's own band, and above the voice panel when that one
     * is up. A file and a take never share a write, but the two views can still be on screen
     * together for the moment a session change takes them down.
     */
    private void positionAttachmentStrip() {
        if (attachmentStrip == null
                || !(attachmentStrip.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams)) {
            return;
        }
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) attachmentStrip.getLayoutParams();
        boolean composerShown = inputViews != null && inputViews.getVisibility() == View.VISIBLE;
        int bottom = (composerShown ? composerHeight : 0) + navigationBarHeight;
        if (composerShown && inputViews.getHeight() > 0) {
            bottom = Math.round(contentView.getHeight() - inputViews.getY() - inputViews.getInputBubbleTop());
        }
        if (voicePanel != null && voicePanel.getVisibility() == View.VISIBLE) {
            int height = voicePanel.getHeight();
            bottom += height > 0 ? height : AndroidUtilities.dp(56);
        }
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            attachmentStrip.setLayoutParams(params);
        }
    }

    /** Wires the band's presses: play/pause the take, send it, discard it, retry a failed write. */
    private void installVoicePanelActions() {
        if (voicePanel == null) {
            return;
        }
        voicePanel.setOnPlayPause(this::toggleTakePreview);
        voicePanel.setOnSend(this::sendPendingRecording);
        voicePanel.setOnCancel(this::cancelPendingRecording);
        voicePanel.setOnRetry(this::sendPendingRecording);
    }

    private void toggleTakePreview() {
        if (pendingRecording == null || voicePreview == null) {
            return;
        }
        if (voicePreview.getPlaying() != null) {
            voicePreview.stop();
            if (voicePanel != null) voicePanel.setPlaying(false);
            return;
        }
        voicePreview.play(pendingRecording.getFile());
        if (voicePanel != null) voicePanel.setPlaying(voicePreview.getPlaying() != null);
    }

    /**
     * Sends the finished take. An upload the site already accepted is reused on a retry (a failed
     * message write must not upload the clip twice); a fresh take is uploaded first, as the site's
     * `chat-composer` kind, and the accepted id goes on the message write. The write is never
     * replayed by the client: an accepted id is read back through [loadMessages], and a readback
     * that fails keeps the message rather than resending it.
     */
    private void sendPendingRecording() {
        if (container == null || pendingRecording == null || uploadingVoice || sending
                || channel == null || !channel.getCanPost() || !stagedAttachments.isEmpty()) {
            // A staged file is its own message: the take waits rather than riding the same write.
            return;
        }
        final VoiceRecording recording = pendingRecording;
        final int epoch = loadEpoch;
        uploadingVoice = true;
        stopTakePreview();
        if (voicePanel != null) voicePanel.showBusy("正在发送语音…");
        showVoicePanel();
        if (pendingUploadId != null) {
            sendRecordingUpload(recording, pendingUploadId, epoch);
            return;
        }
        ChatLoader.INSTANCE.uploadVoice(
                container,
                recording.getFile(),
                recording.getMimeType(),
                progress -> kotlin.Unit.INSTANCE,
                uploaded -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    pendingUploadId = uploaded.getId();
                    sendRecordingUpload(recording, uploaded.getId(), epoch);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    onVoiceWriteFailed(error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    private void sendRecordingUpload(VoiceRecording recording, long uploadId, int epoch) {
        final Long inReplyTo = replyTargetId;
        ChatLoader.INSTANCE.send(
                container,
                channelId,
                "",
                inReplyTo,
                acceptedId -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    onVoiceAccepted(recording, acceptedId);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    onVoiceWriteFailed(error);
                    return kotlin.Unit.INSTANCE;
                },
                java.util.Collections.singletonList(uploadId));
    }

    /** The site accepted the write: the take is spent, the composer clears, the message is read back. */
    private void onVoiceAccepted(VoiceRecording recording, long acceptedId) {
        uploadingVoice = false;
        recording.getFile().delete();
        pendingRecording = null;
        pendingUploadId = null;
        if (voicePanel != null) voicePanel.hide();
        if (composer != null) composer.setFieldText("");
        clearReplyTarget();
        saveDraft();
        updateComposerVisibility();
        // Read the cooked result around the accepted id; a past cursor excludes its anchor.
        loadMessages(acceptedId, null, true, true);
    }

    /** A failed upload or write: the take is kept and the retry is offered; nothing is resent. */
    private void onVoiceWriteFailed(Throwable error) {
        uploadingVoice = false;
        FileLog.e("chat voice send failed", error);
        if (voicePanel != null) {
            voicePanel.showError("语音发送失败");
        }
        showVoicePanel();
    }

    /** The reader threw the take away: the private file goes with it. */
    private void cancelPendingRecording() {
        stopTakePreview();
        if (pendingRecording != null) {
            pendingRecording.getFile().delete();
        }
        pendingRecording = null;
        pendingUploadId = null;
        uploadingVoice = false;
        if (voicePanel != null) voicePanel.hide();
    }

    private void stopTakePreview() {
        if (voicePreview != null) {
            voicePreview.stop();
        }
        playingAudioUrl = null;
        if (voicePanel != null) voicePanel.setPlaying(false);
    }

    // ---------------------------------------------------------------------------------------------
    // Received audio
    // ---------------------------------------------------------------------------------------------

    /**
     * Plays a received voice message: the upload is fetched with the session-aware client (some
     * uploads are served to a signed-in reader only) and the local file is played by the device's
     * own player. A second press on the same clip stops it. Never Telegram's account-backed media
     * controller. Returns true when [url] was a message's own audio clip, so the caller can leave
     * every other link to its own handling.
     */
    private boolean playReceivedAudio(String url) {
        if (container == null || url == null) {
            return false;
        }
        ChatMessage message = messageWithAudioUrl(url);
        if (message == null) {
            return false;
        }
        String resolved = ChatVoiceAudio.audioUrl(message, container.getBaseUrl());
        if (resolved == null) {
            return false;
        }
        if (voicePreview != null && resolved.equals(playingAudioUrl)) {
            stopTakePreview();
            return true;
        }
        ChatLoader.INSTANCE.receivedAudio(
                container,
                resolved,
                file -> {
                    if (isFinished || voicePreview == null) {
                        return kotlin.Unit.INSTANCE;
                    }
                    stopTakePreview();
                    voicePreview.play(file);
                    playingAudioUrl = resolved;
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    FileLog.e("chat audio playback failed", error);
                    return kotlin.Unit.INSTANCE;
                });
        return true;
    }

    private ChatMessage messageWithAudioUrl(String url) {
        for (ChatMessage message : chatMessages) {
            if (ChatVoiceAudio.matches(message, container.getBaseUrl(), url)) {
                return message;
            }
        }
        return null;
    }

    /**
     * Drops every voice activity a screen change must not carry across: a live capture, a pending
     * take, its preview, and any in-flight upload's result (the epoch fence covers that). A screen
     * calls this on pause, on destroy and on any session change, so a stale screen's recording never
     * lands in a new draft.
     */
    private void cancelVoice() {
        if (voiceInput != null) {
            voiceInput.cancelCapture();
        }
        stopTakePreview();
        if (pendingRecording != null) {
            pendingRecording.getFile().delete();
        }
        pendingRecording = null;
        pendingUploadId = null;
        uploadingVoice = false;
        if (voicePanel != null) voicePanel.hide();
    }

    /**
     * The microphone is a touch-only affordance; a screen reader needs an alternative. The mic's
     * own click action toggles a capture (one activation starts, the next finishes and delivers),
     * which is the same flow without the hold.
     */
    private void installRecordAccessibility(ChatActivityEnterView view) {
        View mic = view.getAudioVideoButtonContainer();
        if (mic == null) {
            return;
        }
        mic.setContentDescription("按住录音");
        View.AccessibilityDelegate delegate = new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    if (voiceInput != null) {
                        voiceInput.toggleFromAccessibility();
                    }
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        };
        mic.setAccessibilityDelegate(delegate);
    }

    // ---------------------------------------------------------------------------------------------
    // Session / loading
    // ---------------------------------------------------------------------------------------------

    @Override
    public void clearViews() {
        saveDraft();
        stopSessionObservation();
        closeLiveHolder();
        stopTyping();
        clearFloatingDate();
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
        if (typingController != null) typingController.resume();
        else startTyping();
    }

    @Override
    public void onPause() {
        saveDraft();
        closeLiveHolder();
        if (typingController != null) typingController.pause();
        typingSubtitle = "";
        updateTypingSubtitle();
        if (composerTools != null) composerTools.onPause();
        // A live capture or a playing take does not survive the screen going to the background: the
        // recorder is released and the playback stops. A finished take that has not been sent is
        // kept, so a transient pause does not throw away a message the reader just recorded.
        if (voiceInput != null) voiceInput.onPause();
        stopTakePreview();
        if (voicePanel != null && voicePanel.getState() == ComposerVoicePanel.State.Recording) {
            voicePanel.hide();
        }
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

    /** The microphone permission answer; the recording host waits for it before a second request. */
    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (requestCode == ComposerVoiceInput.RECORD_PERMISSION_REQUEST_CODE && voiceInput != null) {
            voiceInput.onPermissionResult();
        }
    }

    @Override
    public void onFragmentDestroy() {
        stopTyping();
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
        // The screen is gone: a live capture, a pending take and any playback go with it, and the
        // recording host releases only its own delegate binding.
        cancelVoice();
        // The staged copies are this screen's private files; nothing else can send them.
        attachmentStrip = null;
        discardStagedAttachments();
        if (voiceInput != null) {
            voiceInput.onDestroy();
            voiceInput = null;
        }
        voicePreview = null;
        voicePanel = null;
        if (composerTools != null) {
            composerTools.onDestroy();
            composerTools = null;
        }
        if (composer != null) {
            composer.onDestroy();
            composer = null;
        }
        // A fade armed by a scroll must not outlive the screen and call back into a dead view.
        clearFloatingDate();
        floatingDateView = null;
        if (avatarContainer != null) {
            avatarContainer.onDestroy();
            avatarContainer = null;
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
            stopTyping();
            saveDraft();
            // A capture, a pending take or a playback belongs to the account it started under: an
            // account switch cancels all of it rather than letting a stale take land in the new
            // reader's draft. A staged file and its upload ids are the same kind of private state.
            cancelVoice();
            discardStagedAttachments();
            sending = false;
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
            stopTyping();
            stateUi = SessionUi.stateFor(state);
            loading = false;
            clearFloatingDate();
            notifyRowsChanged();
        }
        updateComposerVisibility();
    }

    private void resetForReload() {
        channel = null;
        sending = false;
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
        clearFloatingDate();
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
            clearFloatingDate();
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The channel's own state, once it is known: the header, the composer and the join affordance. */
    private void applyChannelHeader() {
        if (channel == null) {
            return;
        }
        setCapsuleTitle(channel.getTitle().isEmpty() ? "频道" : channel.getTitle());
        setCapsuleAvatar(channel.getTitle());
        updateTypingSubtitle();
        startTyping();
        updateComposerVisibility();
        notifyRowsChanged();
    }

    /** The capsule's title: the channel's own name. */
    private void setCapsuleTitle(String title) {
        if (avatarContainer != null && title != null && !title.trim().isEmpty()) {
            avatarContainer.setTitle(title.trim());
        }
    }

    /**
     * The capsule's picture. A channel carries no image in this client, so upstream's avatar
     * drawable draws the initials a chat without a photo gets; the title is what it initials.
     */
    private void setCapsuleAvatar(String title) {
        if (avatarContainer == null || container == null) {
            return;
        }
        String name = title == null || title.trim().isEmpty() ? "频道" : title.trim();
        avatarContainer.setUserAvatar(DiscourseAvatars.INSTANCE.user(
                "channel-" + channelId, name, "", container.getBaseUrl(), ""));
    }

    private void startTyping() {
        if (typingController != null || container == null || channel == null || isFinished || isPaused()) return;
        SessionSnapshot owner = container.getSessionStore().snapshot();
        if (owner == null || owner.getUsername().isEmpty()) return;
        typingController = new TypingPresenceController(container.getDiscourseApiClient(),
                container.getMessageBusSession().getRunningClientFlow());
        typingController.setUsersListener(typers -> {
            if (isFinished || isPaused()) return;
            // The status verb comes first so a long name ellipsizes without taking the verb with
            // it; the capsule can only grow as far as the space between the bar's capsules.
            typingSubtitle = typers.isEmpty() ? ""
                    : typers.size() == 1 ? "正在输入：" + typers.get(0).getUsername()
                    : "正在输入：" + typers.get(0).getUsername() + " 等 " + typers.size() + " 人";
            updateTypingSubtitle();
        });
        typingController.start("/chat-reply/" + channelId, owner.getUsername());
    }

    private void stopTyping() {
        if (typingController != null) {
            typingController.setUsersListener(null);
            typingController.stop();
            typingController = null;
        }
        typingSubtitle = "";
        updateTypingSubtitle();
    }

    private void updateTypingSubtitle() {
        if (avatarContainer == null || channel == null) {
            return;
        }
        avatarContainer.setSubtitle(typingSubtitle.isEmpty() ? statusLabel(channel) : typingSubtitle);
        android.view.View subtitleView = avatarContainer.getSubtitleTextView();
        if (!(subtitleView instanceof SimpleTextView)) {
            return;
        }
        SimpleTextView subtitle = (SimpleTextView) subtitleView;
        if (typingSubtitle.isEmpty()) {
            typingDots.hide(subtitle);
        } else {
            typingDots.show(subtitle, avatarContainer.getSubtitlePaint().getColor());
        }
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
                    // failed with messages already drawn leaves them standing and says in passing
                    // what the next step is.
                    if (chatMessages.isEmpty()) {
                        stateUi = SessionUi.stateForLoadFailure(error);
                        clearFloatingDate();
                    } else {
                        showPagingFailure(error, targetMessageId, direction, askedReplace, landOnTarget);
                    }
                    notifyRowsChanged();
                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * A read that did not arrive while the conversation is on screen: an older page, a refresh, or
     * a send's own readback. The rows stay; the hint says whether the session is what the read
     * needs - its action opens the login WebView, whose completion reloads the channel - or whether
     * it is an outage, whose action repeats the read that failed with the parameters it was asked
     * with. Retrying the older cursor instead would answer a different question: a failed refresh
     * would load older history, and a failed readback would never show the message just sent.
     */
    private void showPagingFailure(Throwable error, Long targetMessageId, String direction,
            boolean replace, boolean landOnTarget) {
        final io.github.baba983.nativedo.topic.TopicLoadFailure reason =
                io.github.baba983.nativedo.topic.TopicLoadFailure.of(error);
        final boolean session = reason == io.github.baba983.nativedo.topic.TopicLoadFailure.Challenge
                || reason == io.github.baba983.nativedo.topic.TopicLoadFailure.LoginRequired;
        final int messageRes = reason == io.github.baba983.nativedo.topic.TopicLoadFailure.Challenge
                ? R.string.page_verify_required
                : reason == io.github.baba983.nativedo.topic.TopicLoadFailure.LoginRequired
                        ? R.string.page_login_required
                        : R.string.topic_page_retry_failed;
        final int actionRes = reason == io.github.baba983.nativedo.topic.TopicLoadFailure.Challenge
                ? R.string.page_verify
                : reason == io.github.baba983.nativedo.topic.TopicLoadFailure.LoginRequired
                        ? R.string.page_login
                        : R.string.topic_page_retry;
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.chats_infotip,
                    ApplicationLoader.applicationContext.getString(messageRes),
                    ApplicationLoader.applicationContext.getString(actionRes),
                    org.telegram.ui.Components.Bulletin.DURATION_PROLONG,
                    () -> {
                        if (session) {
                            openRecovery(
                                    reason == io.github.baba983.nativedo.topic.TopicLoadFailure.Challenge);
                        } else {
                            retryFailedRead(targetMessageId, direction, replace, landOnTarget);
                        }
                    }).show();
        } catch (Exception bulletinError) {
            FileLog.e(bulletinError);
        }
    }

    /** Asks for the read that failed again, with the same target, direction and landing. */
    private void retryFailedRead(Long targetMessageId, String direction, boolean replace,
            boolean landOnTarget) {
        if (loading || loadingOlder || sending) {
            return;
        }
        loadMessages(targetMessageId, direction, replace, landOnTarget);
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
        // A prepended or replaced window moves the rows; re-read the capsule so it never names the
        // message that used to be on top.
        updateFloatingDateIfShown();
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
     * preview then an original reports again when the original replaces it. One message's inline
     * emoji are fetched on the same pass, and each landing picture is handed to the drawable already
     * bound to its document, so the span the row first drew shows the artwork in place of the
     * placeholder without the message being rebuilt.
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
        if (images != null && !images.isEmpty()) {
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
        List<PostImage> emojis = body.getEmojis();
        if (emojis != null && !emojis.isEmpty()) {
            DiscourseLoader.INSTANCE.cacheImages(container, emojis, () -> {
                for (PostImage emoji : emojis) {
                    DiscourseImageBridge.INSTANCE.applyEmojiArtwork(emoji);
                }
                return kotlin.Unit.INSTANCE;
            });
        }
        // A file a message links to is fetched with the message, the way a topic fetches one: the
        // block draws the name and the size, and the tap on it opens the file - neither of which a
        // block can do while the bytes are only an address on the site.
        List<PostAttachment> attachments = body.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            DiscourseLoader.INSTANCE.cacheAttachments(container, attachments, () -> {
                refreshAttachmentRows(attachments);
                return kotlin.Unit.INSTANCE;
            });
        }
        // A clip's cover travels with the message; the clip itself is fetched when it is played.
        List<PostVideo> videos = body.getVideos();
        if (videos != null && !videos.isEmpty()) {
            DiscourseLoader.INSTANCE.cacheVideoCovers(container, videos, () -> {
                refreshVideoCoverRows(videos);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * Hands the rows that link one of [attachments] the size of what has arrived, and binds them
     * again: a document block reads its file when it is built, so a file fetched after the row was
     * drawn has to reach the message the block belongs to.
     */
    private void refreshAttachmentRows(List<PostAttachment> attachments) {
        if (isFinished || listView == null) {
            return;
        }
        for (int index = 0; index < bodies.size(); index++) {
            PreparedPostBody body = bodies.get(index);
            if (body == null || !containsAny(body.getAttachments(), attachments)) {
                continue;
            }
            DiscourseImageBridge.INSTANCE.applyAttachmentFiles(
                    bubbles.get(index).messageOwner, body.getAttachments());
            invalidateRow(messageRowOffset() + index);
        }
    }

    /**
     * Binds again the rows whose clip a cover has arrived for: the block draws the poster frame from
     * the document's own thumbnail, which it reads when it is attached, not on every draw.
     */
    private void refreshVideoCoverRows(List<PostVideo> videos) {
        if (isFinished || listView == null) {
            return;
        }
        for (int index = 0; index < bodies.size(); index++) {
            PreparedPostBody body = bodies.get(index);
            if (body == null || !containsAny(body.getVideos(), videos)) {
                continue;
            }
            invalidateRow(messageRowOffset() + index);
        }
    }

    /** Whether [values] names any of [of]; prepared bodies are plain data holders. */
    private static <T> boolean containsAny(List<T> values, List<T> of) {
        if (values == null || values.isEmpty() || of == null) {
            return false;
        }
        for (T value : values) {
            if (of.contains(value)) {
                return true;
            }
        }
        return false;
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
        // A recorded clip's own <audio> embed becomes the labeled link the bubble can act on, so a
        // received voice message is visible and playable rather than an unsupported block.
        String cooked = ChatVoiceAudio.audioLinkHtml(
                post.getCookedHtml(), message, container.getBaseUrl());
        return DiscourseImageBridge.INSTANCE.prepare(cooked, container.getBaseUrl());
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
            final int target = messageRowOffset() + chatMessages.size() - 1;
            listView.scrollToPosition(target);
            if (listView.getHeight() == 0) {
                // The window landed before the list had a size: RecyclerView consumes the pending
                // position in that zero-height layout and the rows stay at the first message.
                // Ask again once the list is measured, with the window it has by then.
                listView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        if (listView.getHeight() <= 0) {
                            return;
                        }
                        listView.removeOnLayoutChangeListener(this);
                        if (!chatMessages.isEmpty()) {
                            listView.scrollToPosition(messageRowOffset() + chatMessages.size() - 1);
                        }
                    }
                });
            }
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
    // Floating date capsule
    // ---------------------------------------------------------------------------------------------

    /**
     * Sets the capsule to the chat date the top visible message itself carries
     * (ChatActivity:16316-16325 reads {@code messageObject.messageOwner.date} of the topmost
     * message child). Status, note and header rows are not messages, so the first message row at or
     * below the top of the viewport is the one named. With no message row in front of the reader (a
     * loading, failed, empty or session state) there is nothing to say and the capsule is left
     * alone; it is cleared by {@link #clearFloatingDate()} where such a state begins.
     */
    private void updateFloatingDate() {
        int date = topVisibleMessageDate();
        if (floatingDateView == null || date == 0) {
            return;
        }
        // setCustomDate formats the same chat date a chat's own date separator shows, so a day and
        // a year boundary change it by themselves.
        floatingDateView.setCustomDate(date, false, false);
    }

    /** Re-reads the top message while the capsule is up, so a history prepend or edit stays true. */
    private void updateFloatingDateIfShown() {
        if (floatingDateView != null && floatingDateView.getTag() != null) {
            updateFloatingDate();
        }
    }

    /**
     * The epoch of the message at the top of the viewport, or 0 when no message row is in front of
     * the reader. The first visible adapter position is read fresh every time, so a prepended or
     * appended window does not leave a stale index behind.
     */
    private int topVisibleMessageDate() {
        if (stateUi != null || chatMessages.isEmpty()
                || listView == null || !(listView.getLayoutManager() instanceof LinearLayoutManager)) {
            return 0;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        int first = manager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return 0;
        }
        // A note row sits above the first message; the first message below it is the top one.
        int index = Math.max(0, first - messageRowOffset());
        if (index >= bubbles.size()) {
            return 0;
        }
        MessageObject message = bubbles.get(index);
        if (message == null || message.messageOwner == null) {
            return 0;
        }
        return message.messageOwner.date;
    }

    /**
     * ChatActivity:13604-13630: the floating date arrives by one 150 ms alpha set, guarded by the
     * tag so a second show while it is up does nothing. Upstream arms the fade from the list's idle
     * callback; the same fade is also armed here, from the scroll itself, so a host that reports a
     * scroll without a following idle still settles.
     */
    private void showFloatingDate() {
        if (floatingDateView == null || stateUi != null || chatMessages.isEmpty()) {
            return;
        }
        if (floatingDateView.getTag() == null) {
            if (floatingDateAnimation != null) {
                floatingDateAnimation.cancel();
            }
            floatingDateView.setTag(1);
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1.0f));
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(floatingDateAnimation)) {
                        floatingDateAnimation = null;
                    }
                }
            });
            floatingDateAnimation.start();
        }
        AndroidUtilities.cancelRunOnUIThread(floatingDateHide);
        AndroidUtilities.runOnUIThread(floatingDateHide, FLOATING_DATE_HIDE_DELAY_MS);
    }

    /** ChatActivity:13632-13658: the floating date leaves by the same 150 ms alpha set. */
    private void hideFloatingDate() {
        if (floatingDateView == null || floatingDateView.getTag() == null) {
            return;
        }
        floatingDateView.setTag(null);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
        }
        floatingDateAnimation = new AnimatorSet();
        floatingDateAnimation.setDuration(150);
        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0.0f));
        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(floatingDateAnimation)) {
                    floatingDateAnimation = null;
                }
            }
        });
        floatingDateAnimation.start();
    }

    /**
     * Takes the capsule out at once, for the states that are not a conversation: a load that failed,
     * an empty window or a lost session must never leave the last date standing.
     */
    private void clearFloatingDate() {
        if (floatingDateView == null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(floatingDateHide);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        floatingDateView.setTag(null);
        floatingDateView.setAlpha(0.0f);
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
    // Attachments
    // ---------------------------------------------------------------------------------------------

    /**
     * The paperclip's entry: the system's own content picker, because a chat channel takes files, not
     * a forum article or a poll. What it answers is staged and uploaded by [attachMediaUri]; no
     * message exists until the reader presses Send.
     *
     * <p>The picker's grant belongs to this Activity's lifetime, so nothing is kept beyond it:
     * [attachMediaUri] copies the bytes into the app's own cache once, and the copy - never a
     * provider stream that may go away - is what the upload reads.</p>
     */
    private void openAttachmentPicker() {
        if (getParentActivity() == null || container == null || sending || uploadingVoice
                || pendingRecording != null) {
            return;
        }
        if (stagedAttachments.count() >= MAX_STAGED_ATTACHMENTS) {
            showError("无法添加附件", new java.io.IOException(
                    "一条消息最多带 " + MAX_STAGED_ATTACHMENTS + " 个附件"));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_ATTACHMENT_REQUEST);
    }

    /** The picker's answer. A cancelled pick stages nothing and leaves the draft as it was. */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_ATTACHMENT_REQUEST) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        attachMediaUri(data.getData(), null);
    }

    /**
     * Stages one readable URI for the next message - the seam a keyboard, a recorder or the system
     * picker drives without faking an Activity result.
     *
     * <p>[releasePermission] runs exactly once, after the copy it guards exists (or after the copy
     * failed), so a caller can hand back a temporary read grant without the upload losing its
     * source. It is never kept: no grant outlives this call's copy. The bytes go to the site as the
     * `chat-composer` kind; the message they belong to is written only by the composer's Send.</p>
     */
    public void attachMediaUri(Uri uri, Runnable releasePermission) {
        if (uri == null) {
            release(releasePermission);
            showError("无法添加附件", new java.io.IOException("无法读取所选文件"));
            return;
        }
        if (getParentActivity() == null || container == null || isFinished) {
            release(releasePermission);
            return;
        }
        if (channel == null || !channel.getCanPost()) {
            // The site's own answer decides whether this channel takes a message at all; a file
            // staged for one that does not would be an upload nobody can reference.
            release(releasePermission);
            if (channel != null) {
                showError("无法添加附件", new java.io.IOException("频道当前不可发言"));
            }
            return;
        }
        if (sending || uploadingVoice || pendingRecording != null) {
            release(releasePermission);
            showError("暂时无法添加附件", new java.io.IOException("请先发送或取消当前的语音消息"));
            return;
        }
        if (stagedAttachments.count() >= MAX_STAGED_ATTACHMENTS) {
            release(releasePermission);
            showError("无法添加附件", new java.io.IOException(
                    "一条消息最多带 " + MAX_STAGED_ATTACHMENTS + " 个附件"));
            return;
        }
        final int epoch = loadEpoch;
        ChatLoader.INSTANCE.stageAttachment(
                container,
                getParentActivity(),
                uri,
                releasePermission,
                picked -> {
                    if (epoch != loadEpoch || isFinished) {
                        picked.getFile().delete();
                        return kotlin.Unit.INSTANCE;
                    }
                    StagedChatAttachment staged = stagedAttachments.stage(
                            picked.getFile(), picked.getName(), picked.getMimeType());
                    refreshAttachments();
                    uploadStagedAttachment(staged, epoch);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    showError("无法添加附件", error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    private static void release(Runnable releasePermission) {
        if (releasePermission != null) {
            releasePermission.run();
        }
    }

    /** Uploads one staged copy the reader has not sent yet; a failure stays on its chip. */
    private void uploadStagedAttachment(final StagedChatAttachment item, final int epoch) {
        uploadStagedAttachment(item, epoch, new UploadDone() {
            @Override
            public void onAccepted() {
                // The chip's own state is the whole answer: the message still needs the reader.
            }

            @Override
            public void onRefused(Throwable error) {
                FileLog.e("chat attachment upload failed", error);
            }
        });
    }

    /** What a staged file's upload means to its caller: the pick only redraws, a send continues. */
    private interface UploadDone {
        void onAccepted();

        void onRefused(Throwable error);
    }

    /**
     * Uploads [item] and reports the site's answer. An upload the site already accepted is never
     * repeated: the id on the staging is what a send retry references.
     */
    private void uploadStagedAttachment(final StagedChatAttachment item, final int epoch,
            final UploadDone done) {
        if (stagedAttachments.find(item.getId()) == null) {
            return;
        }
        stagedAttachments.markUploading(item.getId());
        refreshAttachments();
        ChatLoader.INSTANCE.uploadAttachment(
                container,
                item.getFile(),
                item.getName(),
                item.getMimeType(),
                progress -> {
                    // The upload reports from its own thread; the band is touched on ours.
                    AndroidUtilities.runOnUIThread(() -> {
                        if (epoch != loadEpoch) {
                            return;
                        }
                        stagedAttachments.markProgress(item.getId(), progress);
                        if (attachmentStrip != null) {
                            attachmentStrip.bind(stagedAttachments.items());
                        }
                    });
                    return kotlin.Unit.INSTANCE;
                },
                uploaded -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    stagedAttachments.markAccepted(item.getId(), uploaded.getId());
                    refreshAttachments();
                    done.onAccepted();
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (epoch != loadEpoch) {
                        return kotlin.Unit.INSTANCE;
                    }
                    stagedAttachments.markRefused(item.getId());
                    refreshAttachments();
                    done.onRefused(error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** Redraws the band from the staging and lets the composer offer its own Send for a file. */
    private void refreshAttachments() {
        if (attachmentStrip != null) {
            attachmentStrip.bind(stagedAttachments.items());
            attachmentStrip.setVisibility(stagedAttachments.isEmpty() ? View.GONE : View.VISIBLE);
        }
        applyListPadding();
        updateComposerActions();
    }

    /** The band's remove: the copy goes with the chip, and no message references it. */
    private void removeStagedAttachment(long id) {
        if (sending) {
            return;
        }
        StagedChatAttachment item = stagedAttachments.remove(id);
        if (item != null) {
            item.getFile().delete();
        }
        refreshAttachments();
    }

    /**
     * Drops every staged file and its private copy. A session change, a screen destroy and every
     * accepted write call this: a file staged for one reader's draft is never carried into another's
     * or staged again behind a message the site already took.
     */
    private void discardStagedAttachments() {
        for (StagedChatAttachment item : stagedAttachments.clear()) {
            item.getFile().delete();
        }
        refreshAttachments();
    }

    // ---------------------------------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------------------------------

    /**
     * The composer's own Send: the one place a chat message is written. With staged files the write
     * happens only after the site has accepted every one of them (`upload_ids[]`); a message may be
     * blank when files are the content. A picked file is uploaded when it is staged, but never sent
     * by that alone.
     */
    private void send(CharSequence text) {
        if (container == null || sending || channel == null || !channel.getCanPost()) {
            return;
        }
        String message = text == null ? "" : text.toString().trim();
        boolean hasAttachments = !stagedAttachments.isEmpty();
        if (message.isEmpty() && !hasAttachments) {
            return;
        }
        if (uploadingVoice || pendingRecording != null) {
            // A take and a file are two different writes; the reader finishes one first.
            showError("暂时无法发送", new java.io.IOException("请先发送或取消当前的语音消息"));
            return;
        }
        sending = true;
        final int epoch = loadEpoch;
        final Long inReplyTo = replyTargetId;
        updateComposerVisibility();
        if (!hasAttachments) {
            writeMessage(message, epoch, inReplyTo);
            return;
        }
        uploadStagedForSend(stagedAttachments.awaitingUpload(), 0, message, epoch, inReplyTo);
    }

    /**
     * Uploads what a send still needs, one file at a time, then writes the message. An id already on
     * the staging is reused, so a refused write's retry uploads nothing a second time.
     */
    private void uploadStagedForSend(final List<Long> ids, final int index, final String message,
            final int epoch, final Long inReplyTo) {
        if (epoch != loadEpoch) {
            return;
        }
        if (index >= ids.size()) {
            writeMessage(message, epoch, inReplyTo);
            return;
        }
        StagedChatAttachment item = stagedAttachments.find(ids.get(index));
        if (item == null || item.getUploadId() != null) {
            uploadStagedForSend(ids, index + 1, message, epoch, inReplyTo);
            return;
        }
        uploadStagedAttachment(item, epoch, new UploadDone() {
            @Override
            public void onAccepted() {
                uploadStagedForSend(ids, index + 1, message, epoch, inReplyTo);
            }

            @Override
            public void onRefused(Throwable error) {
                sending = false;
                updateComposerVisibility();
                showError("附件上传失败", error);
            }
        });
    }

    /** Writes one message: the text, the reply target, and whatever uploads the staging accepted. */
    private void writeMessage(final String message, final int epoch, final Long inReplyTo) {
        final List<Long> uploadIds = stagedAttachments.uploadIds();
        ChatLoader.INSTANCE.send(container, channelId, message, inReplyTo, acceptedId -> {
            if (epoch != loadEpoch) return kotlin.Unit.INSTANCE;
            sending = false;
            // The write is the site's now. The copies are spent before the readback, so a readback
            // that fails cannot leave them staged for a second write.
            discardStagedAttachments();
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
            // A refused write keeps the text and the staged files so it can be fixed and sent again
            // - never replayed by the client itself, and the accepted uploads are not repeated.
            if (composer != null) {
                composer.setFieldText(message);
            }
            updateComposerVisibility();
            showError("发送失败", error);
            return kotlin.Unit.INSTANCE;
        }, uploadIds);
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
        // Permission hides the whole input host in updateComposerVisibility. Do not hide the
        // upstream mic/send child while the channel is loading: checkSendButton relies on its
        // previous visibility to transition between the two controls once the channel arrives.
        // A staged file is content on its own, so the field's own Send stands in for the mic even
        // when the text is empty; that press is the only write.
        composer.setForceShowSendButton(!stagedAttachments.isEmpty(), false);
        composer.checkSendButton(false);
        if (voicePanel != null) {
            positionVoicePanel();
        }
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
        if (attachmentStrip != null && attachmentStrip.getVisibility() == View.VISIBLE) {
            bottom += Math.max(attachmentStrip.getMeasuredHeight(), AndroidUtilities.dp(64));
        }
        // The header floats over the list, so the first message starts below it: the bar's own
        // measured height (the status bar is already in it) plus the 4dp upstream's lists keep.
        int top = AndroidUtilities.dp(4) + actionBarHeight();
        listView.setPadding(0, top, 0, bottom + navigationBarHeight + AndroidUtilities.dp(4));
        // The date pill and the reply strip are bands of the list's own top edge; they move down
        // with the floating bar instead of drawing under it.
        if (floatingDateView != null) {
            floatingDateView.setTranslationY(actionBarHeight());
        }
        if (replyStrip != null) {
            replyStrip.setTranslationY(actionBarHeight());
        }
        placePending();
        positionAttachmentStrip();
    }

    /** The bar's measured height; before the first layout its resting one, as upstream reads it. */
    private int actionBarHeight() {
        return actionBar != null && actionBar.getMeasuredHeight() > 0
                ? actionBar.getMeasuredHeight()
                : org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
    }

    private void updateColors() {
        if (contentView != null) contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        if (actionBar != null) {
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.updateColors();
        }
        if (avatarContainer != null) {
            avatarContainer.updateColors();
            avatarContainer.setTitleColors(
                    getThemedColor(Theme.key_actionBarDefaultTitle),
                    getThemedColor(Theme.key_actionBarDefaultSubtitle));
            // The dots carry their own paint, so redrawing the header line recolours them too.
            updateTypingSubtitle();
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
        if (attachmentStrip != null) {
            attachmentStrip.updateColors();
        }
        if (floatingDateView != null) {
            // The capsule reads the shared service paints at draw time (ChatActionCell.drawBackground),
            // so a redraw is the whole recolor.
            floatingDateView.invalidate();
        }
        notifyRowsChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Test hooks
    // ---------------------------------------------------------------------------------------------

    PostListView listViewForTest() {
        return listView;
    }

    /** The voice band, so a test can read the state it is showing. */
    ComposerVoicePanel voicePanelForTest() {
        return voicePanel;
    }

    /** The hold-to-record host, so a test can drive its gesture without a device microphone. */
    ComposerVoiceInput voiceInputForTest() {
        return voiceInput;
    }

    /**
     * Puts a finished local take into the review band, the way a completed capture would. A host
     * test uses this because a JVM has no microphone to record with; the send/retry wire is the
     * same either way.
     */
    void attachPendingRecordingForTest(VoiceRecording recording) {
        onRecordingFinished(recording);
    }

    /** The take awaiting send, so a test can tell a kept take from a discarded one. */
    VoiceRecording pendingRecordingForTest() {
        return pendingRecording;
    }

    /** The send the band's own button performs, for a host test that has no touch to dispatch. */
    void sendPendingRecordingForTest() {
        sendPendingRecording();
    }

    /** The discard the band's own cancel performs. */
    void cancelPendingRecordingForTest() {
        cancelPendingRecording();
    }

    /** True while a take is on its way to the site. */
    boolean isUploadingVoiceForTest() {
        return uploadingVoice;
    }

    /**
     * The action a press on a received voice message performs: resolve the message's own audio
     * upload and play it through the session-aware cache. Returns false when the message carries no
     * audio clip.
     */
    boolean playReceivedAudioForTest(long messageId) {
        int index = indexOfMessage(messageId);
        if (index < 0 || container == null) {
            return false;
        }
        String resolved = ChatVoiceAudio.audioUrl(chatMessages.get(index), container.getBaseUrl());
        return resolved != null && playReceivedAudio(resolved);
    }

    /** Whether a message carries an audio upload (a received voice message). */
    boolean hasReceivedAudioForTest(long messageId) {
        int index = indexOfMessage(messageId);
        return index >= 0 && ChatVoiceAudio.audioUpload(chatMessages.get(index)) != null;
    }

    /** The received clip currently playing, or null. */
    String playingAudioUrlForTest() {
        return playingAudioUrl;
    }

    /** The floating date capsule, so a test can read the day it names and its fade. */
    ChatActionCell floatingDateForTest() {
        return floatingDateView;
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

    /** How many files are staged for the next message. */
    int stagedAttachmentCountForTest() {
        return stagedAttachments.count();
    }

    String stagedAttachmentNameForTest(int index) {
        return index >= 0 && index < stagedAttachments.count()
                ? stagedAttachments.items().get(index).getName() : null;
    }

    ChatAttachmentState stagedAttachmentStateForTest(int index) {
        return index >= 0 && index < stagedAttachments.count()
                ? stagedAttachments.items().get(index).getState() : null;
    }

    /** The private copy behind a staged chip, so a test can tell a kept file from a deleted one. */
    java.io.File stagedAttachmentFileForTest(int index) {
        return index >= 0 && index < stagedAttachments.count()
                ? stagedAttachments.items().get(index).getFile() : null;
    }

    boolean isAttachmentStripShownForTest() {
        return attachmentStrip != null && attachmentStrip.getVisibility() == View.VISIBLE;
    }

    ComposerAttachmentStrip attachmentStripForTest() {
        return attachmentStrip;
    }

    /** The chip's own remove, for a host test that has no touch to dispatch. */
    void removeStagedAttachmentForTest(int index) {
        if (index >= 0 && index < stagedAttachments.count()) {
            removeStagedAttachment(stagedAttachments.items().get(index).getId());
        }
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

    /**
     * Upstream's own callbacks; nothing here invents a path Telegram's cells do not own.
     *
     * <p>Every action belongs to the screen: a tap on a picture opens this client's viewer, a link
     * the site owns leaves for the app's own screens and anything else for the host's browser, and a
     * received voice message's own audio link is played here. A clip this screen fetched may only
     * open something while this screen is still the one in front of the reader.</p>
     */
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

        /**
         * Upstream gates every touch a message receives on this. Its own chat answers true whenever
         * no action mode or report screen is up, and this screen never has either - a picture, an
         * ordinary link and a onebox are all reached through the cell's own touch handling, so a
         * screen that left the default (false) drew media nobody could press.
         */
        @Override
        public boolean canPerformActions() {
            return !isFinished;
        }

        /**
         * A tap on a picture or a clip in a message; the cell routes rich media through this one.
         *
         * <p>A clip is not fetched with the message, so the tap is what fetches it: the block draws
         * its own loading state while the fetch runs, and upstream's viewer opens on the file once
         * it is here. A clip this client will not fetch, or one the site will not hand over, goes to
         * the device's own player, which streams it from the site's address.</p>
         */
        @Override
        public boolean openArticlePhoto(ChatMessageCell cell, TL_iv.PageBlock block) {
            if (isFinished || container == null || block == null) {
                return false;
            }
            MessageObject message = cell.getMessageObject();
            PostVideo video = ArticlePhotoViewer.videoOf(block);
            if (video == null) {
                return ArticlePhotoViewer.open(ChatChannelFragment.this, message, block, listView);
            }
            final int epoch = loadEpoch;
            DiscourseLoader.INSTANCE.fetchVideo(
                    container,
                    video,
                    () -> {
                        if (canOpenFetchedMedia(epoch)) {
                            ArticlePhotoViewer.open(ChatChannelFragment.this, message, block, listView);
                        }
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> {
                        if (canOpenFetchedMedia(epoch)) {
                            ArticlePhotoViewer.stream(ChatChannelFragment.this, block);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            return true;
        }

        /**
         * A press on a link in a message. A voice message's own audio link is this screen's to act
         * on: the clip is fetched with the session and played locally, rather than left inert or
         * handed to a browser. A link to this site's own topic or profile opens the app's own
         * screen; every other link opens in the host's browser, which is the signed-in page.
         */
        @Override
        public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
            if (longPress || isFinished || container == null || !(url instanceof URLSpan)) {
                return;
            }
            String target = ((URLSpan) url).getURL();
            if (target == null || target.isEmpty()) {
                return;
            }
            if (playReceivedAudio(target)) {
                return;
            }
            DiscourseTopicLink topicLink = DiscourseLinks.topicLink(target, container.getBaseUrl());
            if (topicLink != null && topicLink.getTopicId() > 0) {
                Integer floor = topicLink.getFloor() > 0 ? topicLink.getFloor() : null;
                presentFragment(TopicFragment.forFloor(topicLink.getTopicId(), null, floor));
                return;
            }
            String username = DiscourseLinks.profileUsername(target, container.getBaseUrl());
            if (username != null) {
                presentFragment(ProfileFragment.forUser(username, username));
                return;
            }
            Browser.openUrl(getParentActivity(), target);
        }

        /**
         * A tap on a link preview's card. The site's own oneboxes stay rich text in the bubble and
         * their links arrive as {@link #didPressUrl}; this is the counterpart for a message that
         * carries a web page of its own, and it must not fall through to Telegram's browser path.
         */
        @Override
        public void didPressWebPage(ChatMessageCell cell, TLRPC.WebPage webpage, String url, boolean safe) {
            if (isFinished || url == null || url.isEmpty()) {
                return;
            }
            Browser.openUrl(getParentActivity(), url);
        }
    }

    /**
     * Whether a fetch this screen started may still put something on the reader's screen: the screen
     * exists, the session it started under is the same one, and it is still the screen in front. A
     * clip that lands after the reader opened another channel opens neither the viewer nor the
     * device's player.
     */
    private boolean canOpenFetchedMedia(int epoch) {
        return !isFinished && !isPaused() && container != null && epoch == loadEpoch && isLastFragment();
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
            if (!fromDraft && typingController != null) {
                typingController.textChanged(channel != null && channel.getCanPost()
                        && text != null && text.toString().trim().length() > 0);
            }
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }

        /**
         * The composer's paperclip. A chat channel's attachments are their own message content, so
         * the entry is the system's file picker - not the forum sheet, whose article, poll and rich
         * editor actions post things a chat channel cannot take.
         */
        @Override
        public void didPressAttachButton() {
            openAttachmentPicker();
        }

        /**
         * Media the keyboard or clipboard commits (see
         * {@link NativeDoHooks.ComposerMediaInputHost}) is staged through the same path the picker
         * uses. The base delegate would just release the grant; this screen copies the bytes first
         * and hands the grant back, so a commit never falls into Telegram's send or photo path.
         */
        @Override
        public void onComposerMediaInput(Uri uri, Runnable releasePermission) {
            attachMediaUri(uri, releasePermission);
        }
    }

    /**
     * The service paint the floating date draws with, for a screen that has no chat wallpaper - the
     * same delegate the topic list's own pill uses (TopicListFragment:3498).
     *
     * <p>Upstream's floating date is readable because its capsule is a sample of the chat wallpaper:
     * {@link Theme#applyChatServiceMessageColor} puts the wallpaper bitmap in
     * {@code chat_actionBackgroundPaint} and forces its alpha to {@code 0xff}, so the date never
     * shows what passes under it. There is no wallpaper here: the branch that runs leaves the paint
     * the theme's own translucent {@code key_chat_serviceBackground} tint, which is picked to sit on
     * a wallpaper, and on the dark themes the first row's title shines through the capsule.</p>
     *
     * <p>This delegate gives the night capsule the same service colour at the full alpha the
     * wallpaper branch uses whenever there is no wallpaper to sample. The capsule does not report
     * {@code isFloating()} (ChatActionCell:3496-3505 quarters that alpha for a floating view), so
     * the night capsule covers the row behind it. A wallpaper gradient present keeps the shared
     * paint untouched, and the day theme keeps its own tint. The colour is re-read on every draw, so
     * the capsule follows the day/night switch in place.</p>
     */
    private static final class DatePillThemeProvider implements ChatActionCell.ThemeDelegate {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public int getColor(int key) {
            return Theme.getColor(key);
        }

        @Override
        public int getCurrentColor() {
            return Theme.currentColor;
        }

        @Override
        public Paint getPaint(String paintKey) {
            if (Theme.hasGradientService() || !Theme.isCurrentThemeDark()) {
                // A wallpaper sample, or the day theme's own tint: what a chat would draw with.
                return null;
            }
            if (!Theme.key_paint_chatActionBackground.equals(paintKey)) {
                return null;
            }
            // The colour the shared service paint would draw, at the alpha the wallpaper branch
            // forces, so the night capsule covers the row behind it instead of tinting it.
            backgroundPaint.setColor(Theme.chat_actionBackgroundPaint.getColor() | 0xFF000000);
            return backgroundPaint;
        }

        @Override
        public boolean hasGradientService() {
            return Theme.hasGradientService();
        }
    }
}
