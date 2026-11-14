package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoActivity;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.ChatUser;
import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.core.model.PostPoll;
import io.github.baba983.nativedo.core.model.PostReaction;
import io.github.baba983.nativedo.core.model.TopicDetail;
import io.github.baba983.nativedo.core.presence.TypingPresenceController;
import io.github.baba983.nativedo.core.presence.TypingPresencePolicy;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.feature.topic.data.TopicReadReporter;
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft;
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey;
import io.github.baba983.nativedo.hooks.NativeDoHooks;

/**
 * One linux.do topic: every post is drawn by upstream's stock {@link ChatMessageCell} from a
 * {@code TL_message.rich_message} body, which is how upstream itself previews a locally built rich
 * message. Taps route to upstream's article photo viewer and {@link Browser}.
 */
public class TopicFragment extends BaseFragment
        implements me.vkryl.android.animator.FactorAnimator.Target {

    private static final String ARG_TOPIC_ID = "topic_id";
    private static final String ARG_TOPIC_TITLE = "topic_title";
    private static final String ARG_FLOOR = "floor";
    /**
     * The author a row already drew when it opened this screen. The capsule can show the same
     * picture before the topic is read, instead of an empty spot until {@code /t/{id}.json}
     * answers.
     */
    private static final String ARG_AUTHOR_USERNAME = "author_username";
    private static final String ARG_AUTHOR_AVATAR_TEMPLATE = "author_avatar_template";
    private static final String ARG_REPLY_COUNT = "reply_count";
    private static final String ARG_CATEGORY_ID = "category_id";

    /**
     * The discussion page a floor's reply footer opens. It is the same screen as a topic read, in a
     * second mode: the list is seeded with the one post whose thread this is and then pages its
     * direct replies from {@code /posts/{id}/replies}. The three arguments are the floor's real
     * identity, so a page rebuilt by the system - where the in-memory seed is gone - resolves the
     * parent again from the site by its own floor.
     */
    private static final String ARG_THREAD_PARENT_POST_ID = "thread_parent_post_id";
    private static final String ARG_THREAD_PARENT_FLOOR = "thread_parent_floor";
    private static final String ARG_THREAD_REPLY_COUNT = "thread_reply_count";

    /**
     * How many posts one page of a long topic asks for. Discourse servers the ids of the whole
     * stream with the topic, so a page is a fetch of the next slice of ids.
     */
    private static final int PAGE_SIZE = 20;

    /** How close to the end the list has to come before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 4;

    /** The document picker's request code; both attach entry points start it. */
    private static final int PICK_ATTACHMENT_REQUEST = 0x4e44;

    /** The height of our own attach strip, which sits on top of the composer. */
    private static final int STRIP_HEIGHT_DP = 36;

    /**
     * The page-down button's own visibility, animated the way upstream's
     * {@code ChatActivitySideControlsButtonsLayout} shows its buttons: a bool factor drives alpha,
     * scale and the slide, and the view's visibility is the factor's. Only one animator on this
     * screen, so the id is its own.
     */
    private static final int ANIMATOR_ID_PAGE_DOWN_VISIBLE = 0x50;
    /** The way back to the first floor, animated with the same shape as the page-down button. */
    private static final int ANIMATOR_ID_PAGE_UP_VISIBLE = 0x51;

    /** One stacked floating button plus the gap between the two of them, in dp. */
    private static final int PAGE_BUTTON_STEP_DP = 56 + 8;

    /** The copy the empty discussion page shows when the site says the root has no replies at all. */
    private static final String THREAD_EMPTY_LINE = "还没有回复";

    /** The line above the first floor the reader has not seen, as upstream's chat writes it. */
    private static final String UNREAD_DIVIDER_LINE = "未读消息";

    /**
     * The copy an empty discussion page shows when the site's own count promises replies but the
     * endpoint answered with none: the page says it cannot show any, not that there are none.
     */
    private static final String THREAD_EMPTY_UNSEEN_LINE = "当前没有可见的回复";

    /**
     * The discussion page's own retry: the stock pass-through Bulletin announces it (the page's own
     * affordance is the floating page-down button, whose stock spinner and badge carry the state and
     * whose tap is the retry). Unlike the topic paging copy it does not promise a scroll retry,
     * because a discussion page is driven by a tap on that button, not by its list.
     */
    private static final String THREAD_RETRY_LINE = "这一页没能读出来，点击重试";

    /** The right capsule's overflow item and the sub-items behind it. */
    private static final int MENU_ITEM_MORE = 1;
    private static final int MENU_ITEM_BOOKMARK = 2;
    private static final int MENU_ITEM_COPY_LINK = 3;
    private static final int MENU_ITEM_OPEN_BROWSER = 4;
    private static final int MENU_ITEM_SEARCH = 5;
    private static final int MENU_ITEM_SHARE = 6;
    /** The overflow's notification-level entry; its own ItemOptions carries the four choices. */
    private static final int MENU_ITEM_NOTIFICATION = 7;
    /** The overflow's only-original-poster toggle; the label says which way the next tap goes. */
    private static final int MENU_ITEM_ONLY_OP = 8;

    /** Upstream fragments take their arguments through the constructor, not a setter. */
    public TopicFragment(android.os.Bundle args) {
        super(args);
    }

    private PostListView listView;
    /**
     * Upstream's own text selection for chat lists, the one a chat's message list drives: the
     * posts hand it their touches the way {@code ChatMessageCell} does, and the overlay it draws
     * (handles, highlight and the copy bar) is added to the content view.
     */
    private org.telegram.ui.Cells.TextSelectionHelper.ChatListTextSelectionHelper textSelectionHelper;
    /** The topic's floors, in order: one post each, drawn as one or more bubbles. */
    private final List<Post> floors = new ArrayList<>();
    /** Every bubble of every floor, in order; this is what the list binds. */
    private final List<MessageObject> bubbles = new ArrayList<>();
    /** Per bubble, the floor it draws. */
    private final List<Integer> bubbleFloors = new ArrayList<>();
    /**
     * The divider above the first floor the site says the reader has not read, as a row of its own.
     * Null until a window carrying that floor is bound.
     */
    private MessageObject unreadDivider;
    /**
     * The floating date pill a chat shows: the day of the row at the top of the viewport, drawn over
     * the posts while that day's own date row is above the list's top edge.
     */
    private org.telegram.ui.Cells.ChatActionCell floatingDate;
    /** How far the pill is pushed up as the day's own date row leaves the top edge. */
    private float floatingDateOffset;
    /** Whether the pill is up because that date row is scrolled away; upstream tracks the same. */
    private boolean currentFloatingDateOnScreen;
    /** Whether the row at the top of the viewport is neither a bubble nor a date row. */
    private boolean currentFloatingTopIsNotMessage;
    /** Whether the reader is dragging: the pill only comes up for a scroll the reader started. */
    private boolean scrollingFloatingDate;
    private android.animation.AnimatorSet floatingDateAnimation;
    /** Per bubble, the prepared body a text bubble draws from; a poll bubble has none. */
    private final List<PreparedPostBody> bubbleBodies = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    /** Per author, the avatar template that produced their profile photo; the file is fetched from it. */
    private final HashMap<Long, String> avatarTemplates = new HashMap<>();
    private org.telegram.messenger.ChatMessageSharedResources sharedResources;
    private AppContainer container;
    private long topicId;
    private ChatActivityEnterView composer;
    /**
     * The composer's own delegate, kept so the voice recording host can be bound to it (the delegate
     * carries upstream's microphone gesture in, see {@link DiscourseComposerDelegate}).
     */
    private DiscourseComposerDelegate composerDelegate;
    /**
     * The post menu as upstream's scrim popup. It is its own window, so the fragment keeps it to
     * take it down when the screen leaves: a popup nobody dismisses outlives the fragment.
     */
    private DiscourseReactionUi.Controller messageMenu;
    /**
     * The discussion page's own state, when this screen is the thread a floor's reply footer opens.
     * [threadMode] is the second mode of this fragment: the list holds one root post and the direct
     * replies to it, read one page at a time from {@code /posts/{id}/replies}, instead of the
     * topic's window of floors.
     */
    private boolean threadMode;
    /** The root floor's real post id, which the replies endpoint is addressed by. */
    private long threadParentPostId;
    /** The root floor's own post number, which is the reply target the composer sends to by default. */
    private int threadParentFloor;
    /** The site's authoritative count of the root's direct replies. */
    private int threadReplyCount;
    /** The cursor: the greatest valid floor a page has carried so far; a page asks for what is after it. */
    private int threadAfter = 1;
    private boolean threadLoading;
    private boolean threadExhausted;
    private boolean threadFailed;
    /** The reply ids already held, so an overlapping page adds nothing twice. */
    private final Set<Long> threadLoadedIds = new HashSet<>();
    /**
     * Which thread a replies read belongs to. A read still in flight when the page is destroyed or
     * reloaded must not draw into the new one.
     */
    private int threadGeneration;
    /** The root the caller already held, so the thread draws before the site answers; null after a rebuild. */
    private Post threadSeedRoot;
    /** The topic the root belongs to, seeded so the header and mentions draw at once. */
    private TopicDetail threadSeedDetail;
    /**
     * The page's own read-on/retry affordance: upstream's floating round page-down button, the same
     * one a chat floats over its composer (ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN).
     * Its stock spinner shows a page in flight, its stock badge counts the site's remaining direct
     * replies, and its tap reads on or retries. Null until the screen is built.
     */
    private org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton threadPageDown;
    /**
     * The button's own visibility factor, upstream's own show animation (the one its
     * {@code ChatActivitySideControlsButtonsLayout} plays): alpha, scale and the slide from below.
     * Ordinary mode only - a discussion page toggles the button instantly, the way its tests drive
     * it.
     */
    private final me.vkryl.android.animator.BoolAnimator pageDownVisible =
            new me.vkryl.android.animator.BoolAnimator(
                    ANIMATOR_ID_PAGE_DOWN_VISIBLE,
                    this,
                    me.vkryl.android.AnimatorUtils.DECELERATE_INTERPOLATOR,
                    280);
    /** The button's resting bottom offset from the frame's own bottom edge; see [placeThreadStatus]. */
    private int pageDownBottom;
    /**
     * The way back to the first floor: the same floating round button with the chevron turned up,
     * floating above the page-down control while the first post is off-screen. Null until the
     * screen is built.
     */
    private org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton threadPageUp;
    /** The up button's own visibility factor; the same show animation as the page-down button. */
    private final me.vkryl.android.animator.BoolAnimator pageUpVisible =
            new me.vkryl.android.animator.BoolAnimator(
                    ANIMATOR_ID_PAGE_UP_VISIBLE,
                    this,
                    me.vkryl.android.AnimatorUtils.DECELERATE_INTERPOLATOR,
                    280);
    /**
     * Whether an ordinary read's jump is on its way. The button's stock spinner shows it, the button
     * refuses a second tap, and only a read that belongs to the window it was asked for clears it.
     */
    private boolean pageDownJumping;
    /**
     * The empty discussion's own note, upstream's stock service pill (a ChatActionCell carrying
     * {@code setCustomText}), centred above the composer when the root is the whole thread - the
     * stock shape the made-up full-width strip used to stand in for.
     */
    private org.telegram.ui.Cells.ChatActionCell threadEmptyCell;
    /** What the empty note says; null whenever the note is not the state to show. */
    private CharSequence threadEmptyText;
    /**
     * Whether a loaded floor carries one of the site's holographic titles. The frame loop below
     * only runs while one is loaded, so a topic without one pays no frames.
     */
    private boolean animatedTitlesLoaded;
    private boolean titleGradientTicking;
    /** One frame of the holographic gradient: advance it, redraw the rows, ask for the next. */
    private final Runnable titleGradientTick = new Runnable() {
        @Override
        public void run() {
            titleGradientTicking = false;
            if (!animatedTitlesLoaded || listView == null || fragmentView == null
                    || getParentActivity() == null || isPaused()) {
                return;
            }
            // A tag that scrolled away costs nothing: the loop stops and the next bind (or scroll)
            // re-arms it, so an invisible title never keeps the list redrawing.
            if (!animatedTagVisible()) {
                return;
            }
            // The rows, not the list: a group's display list is reused as-is, so invalidating the
            // RecyclerView alone would leave each cell's cached text on screen. The cells that draw
            // a tag re-record, and their StaticLayout re-applies the span with the new phase.
            DiscourseUserTitles.INSTANCE.advance();
            invalidateAnimatedTags();
            titleGradientTicking = true;
            listView.postOnAnimationDelayed(this, 33);
        }
    };
    /** Upstream's floating-composer host: the island bubble the enter view rides in. */
    private org.telegram.ui.Components.chat.ChatInputViewsContainer inputViews;
    private org.telegram.ui.Components.inset.WindowInsetsStateHolder inputInsets;
    private DiscourseComposerTools composerTools;
    /** The native reply strip above the composer, created the first time the reader answers a post. */
    private DiscourseReplyPanel replyPanel;
    /**
     * Who else is writing a reply to this topic, from the site's own presence channel. The screen
     * reads the channel and announces its own composer through the same controller a chat channel
     * uses; the reply channel is `discourse-presence`'s, not the chat plugin's.
     */
    private TypingPresenceController replyPresence;
    /** The capsule's own line, kept while another replier's line takes the capsule over. */
    private CharSequence capsuleSubtitle;
    /** The repliers line, or empty when nobody else is replying. */
    private String repliersSubtitle = "";
    /** Upstream's animated typing dots, drawn before the replier line while it is shown. */
    private final TypingDotsSubtitle repliersDots = new TypingDotsSubtitle(false);
    /** The floating center capsule: the topic author's avatar, the title and the subtitle. */
    private org.telegram.ui.Components.ChatAvatarContainer avatarContainer;
    /** The bar's blur source; the post list is the content it records. */
    private io.github.baba983.nativedo.app.GlassPanelHost glassHost;
    private io.github.baba983.nativedo.search.TopicSearch topicSearch;
    private TopicDetail searchReadingDetail;
    private List<Post> searchReadingPosts;
    private Runnable searchReadingRetry;
    private int searchReadingPosition;
    private int searchReadingOffset;
    private org.telegram.ui.Components.RecyclerAnimationScrollHelper searchScroll;
    /**
     * The bar's search width factor (ActionBar:2244): while the search field owns the bar the
     * center glass capsule must span it instead of the avatar container's own width, or the hint
     * is drawn left of the capsule. ChatActivity drives the same factor from its search animation
     * (ChatActivity:46603-46608).
     */
    private android.animation.ValueAnimator searchFactorAnimator;
    private float searchFactorValue;
    /**
     * ChatActivity's own top and bottom blur band ({@code chatActivityFadeView},
     * ChatActivity.java:6969-6973): the status bar and the bar's gaps show the recorded content
     * through a fading glass strip instead of raw text scrolling by.
     */
    private org.telegram.ui.Components.chat.layouts.ChatActivityFadeView fadeView;
    /** The system's bottom inset, from [onInsets]; the composer sits inside it. */
    private int bottomInset;
    /** The topic as last read; the capsule and the bookmark item draw from it. */
    private TopicDetail topicDetail;
    /**
     * The site's own suggestion for what to read after this topic - the first valid entry of the
     * topic read's `suggested_topics` that is not this topic - or null when it suggested none.
     * Nothing here invents one, reorders the site's list or claims an unread.
     */
    private io.github.baba983.nativedo.core.model.TopicSummary nextTopicCandidate;
    /** Set the moment a release asks for the candidate, so no late callback asks twice. */
    private boolean nextTopicNavigationStarted;
    /** The topic's original poster, as the synthetic user the rows also draw. */
    private TLRPC.User topicAuthor;
    /** The reader's own topic-level bookmark, mirrored from the site's answer. */
    private boolean topicBookmarked;
    /** What the list keeps above its first post for the floating bar (bar height + 4dp). */
    private int listPaddingTop;
    /** The overflow item's 书签 row, whose label and icon follow [topicBookmarked]. */
    private org.telegram.ui.ActionBar.ActionBarMenuSubItem bookmarkMenuItem;
    /** The overflow item's notification-level row; it opens the four checked choices. */
    private org.telegram.ui.ActionBar.ActionBarMenuSubItem notificationLevelMenuItem;
    /** The overflow item's only-original-poster row; null on a discussion page, which is a subset. */
    private org.telegram.ui.ActionBar.ActionBarMenuSubItem onlyOpMenuItem;
    /** The overflow item itself, which the level ItemOptions anchors on. */
    private org.telegram.ui.ActionBar.ActionBarMenuItem overflowItem;
    /**
     * The bar's real search item (ChatActivity:4244-4248 wires the same one). Its own
     * EditTextBoldCursor is the in-topic search field, so the bar's back capsule stays and the
     * search capsule replaces the center/menu while it is open. The item is held GONE: the reader
     * opens it from the overflow's 话题内搜索 row, never from an icon of its own on the bar.
     */
    private org.telegram.ui.ActionBar.ActionBarMenuItem searchItem;
    /**
     * The reader's own notification level for this topic (`details.notification_level`): 0 mute,
     * 1 regular, 2 tracking, 3 watching. It only moves when the site took a write, so a refusal
     * leaves the row saying what it said.
     */
    private int topicNotificationLevel = 1;
    /**
     * ChatActivity's own first-load view (6984-7016): chats do not show the chat list's shimmer;
     * they show a centred ring on the service background while the first page of messages is on
     * its way, and this screen does the same.
     */
    private FrameLayout progressView;
    private View progressView2;
    private RadialProgressView progressBar;
    /** ChatActivity's animateProgressViewTo (11865): the state the ring is animating towards. */
    private boolean animateProgressViewTo;
    /**
     * The day/night switch updates the capsules' glass and the center capsule's own text colours;
     * a chat activity is rebuilt on a theme change, this screen is not.
     */
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id == NotificationCenter.didSetNewWallpapper) {
                    // ChatActivity:23988 - a new wallpaper replaces the one the screen draws.
                    updateBackground();
                    if (progressView2 != null) {
                        progressView2.invalidate();
                    }
                    return;
                }
                if (id != NotificationCenter.needSetDayNightTheme && id != NotificationCenter.didSetNewTheme) {
                    return;
                }
                updateBackground();
                if (progressBar != null) {
                    // Upstream's own description for the ring (ChatActivity:42981, key_chat_serviceText);
                    // this screen is not rebuilt on the switch, so the colour is re-set in place.
                    progressBar.setProgressColor(
                            getThemedColor(Theme.key_chat_serviceText));
                    progressBar.invalidate();
                }
                if (progressView2 != null) {
                    progressView2.invalidate();
                }
                if (glassHost != null) {
                    glassHost.updateColors();
                    // Every view that draws from the source holds a cached frame until it is
                    // invalidated, the capsules' bar included.
                    glassHost.invalidateAttachedViews();
                }
                if (topicSearch != null) {
                    topicSearch.updateColors();
                }
                if (fadeView != null) {
                    // ChatActivity is rebuilt on a theme change and builds the band again; this
                    // screen is not rebuilt, so its band is told the way the capsules are.
                    fadeView.updateColors();
                }
                if (inputViews != null) {
                    // The composer island's blur drawables carry their own colours too.
                    inputViews.updateColors();
                }
                if (composer != null) {
                    composer.updateColors();
                    composer.getEditField().setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
                    composer.getEditField().setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
                    composer.getEditField().setCursorColor(getThemedColor(Theme.key_chat_messagePanelCursor));
                }
                if (this.attachStrip != null) {
                    this.attachStrip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                            getThemedColor(Theme.key_chat_messagePanelBackground)));
                    this.attachStripLabel.setTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
                    this.richTextButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                }
                if (this.threadPageDown != null) {
                    // The button's blur drawable caches its own colours and its icon is themed too;
                    // upstream re-reads both in place on a theme switch.
                    this.threadPageDown.updateColors();
                }
                if (this.threadPageUp != null) {
                    this.threadPageUp.updateColors();
                }
                if (this.threadEmptyCell != null) {
                    // The service pill re-reads its paints on every draw; a redraw is the recolor.
                    this.threadEmptyCell.invalidate();
                }
                if (this.failureView != null) {
                    this.failureView.invalidate();
                }
                if (actionBar != null) {
                    actionBar.updateColors();
                    actionBar.setItemsColor(
                            getThemedColor(org.telegram.ui.ActionBar.Theme.key_actionBarDefaultIcon), false);
                }
                if (avatarContainer != null) {
                    avatarContainer.updateColors();
                    avatarContainer.setTitleColors(
                            getThemedColor(org.telegram.ui.ActionBar.Theme.key_actionBarDefaultTitle),
                            getThemedColor(org.telegram.ui.ActionBar.Theme.key_actionBarDefaultSubtitle));
                    // The typing dots carry their own paint, so a theme switch recolours them by
                    // redrawing the line they belong to.
                    refreshCapsuleSubtitle();
                }
            };
    /** The frame that draws the chat wallpaper behind the posts (ChatActivity's contentView). */
    private SizeNotifierFrameLayout wallpaperView;

    /** ChatActivity.updateBackground (44180-44191): the current chat wallpaper, with its motion. */
    private void updateBackground() {
        if (wallpaperView == null) {
            return;
        }
        final android.graphics.drawable.Drawable wallpaper =
                org.telegram.ui.ActionBar.Theme.getCachedWallpaper();
        wallpaperView.setBackgroundImage(
                wallpaper,
                org.telegram.ui.ActionBar.Theme.isWallpaperMotion());
        if (glassHost != null) {
            // The panels stand on the wallpaper the frame draws (ChatActivity:17229-17245).
            glassHost.setWallpaperDrawable(wallpaper);
        }
    }
    /** The floor this reply answers, when the reader picked one in the post menu. */
    private Integer replyToPostNumber;
    /** True while a stored draft is being put back into the field, so the restore is not re-saved. */
    private boolean restoringDraft;
    private TopicReplyDraftKey replyDraftKey;
    /**
     * The floor the composer is editing, or -1 while it writes a reply. Edit and reply are separate
     * modes over one field: starting an edit takes the reader's reply aside, and ending the edit
     * puts that reply - text and target together - back untouched.
     */
    private int editFloor = -1;
    /**
     * The identity of the post under edit. An edit's write is sent against the post id and its
     * answer applied to the floor its number names, so a window that reloaded between opening the
     * editor and sending the edit cannot land the write on whatever floor now sits at [editFloor].
     */
    private long editPostId = -1;
    private Integer editPostNumber;
    /** The reply the field held when the edit began; it is what a cancel or a finish restores. */
    private TopicReplyDraft editReplyBackup;
    /** True while an edit write is in flight, so a second press is refused: one press, one write. */
    private boolean sendingEdit;
    /** True while a delete or a recover is in flight, so a second press is refused. */
    private boolean sendingDelete;
    /** Bumped when the screen is destroyed, so a late write callback cannot touch a dead view. */
    private int editGeneration;
    /** Our own attach entry point, for the files upstream's button cannot offer (see its builder). */
    private View attachStrip;
    private android.widget.TextView attachStripLabel;
    /** The attachment sheet, kept so a screen that leaves takes its dialog down with it. */
    private DiscourseAttachSheet attachSheet;
    /** Our own rich-editor entry, on our own strip; upstream's composer is untouched. */
    private android.widget.TextView richTextButton;
    private boolean uploadingAttachment;
    /** Post numbers per row: replying is by number, and the payload does not always carry them. */
    private final List<Integer> postNumbers = new ArrayList<>();
    /** The loaded floor for each post number, so a reply's target is found without scanning rows. */
    private final HashMap<Integer, Post> postByNumber = new HashMap<>();
    /**
     * Which window the maps above describe. The site answers a reply target asynchronously, and the
     * screen can rebind (another topic, another floor, a fresh load) before it lands: a result that
     * belongs to the window it was asked for must not touch the one that replaced it.
     */
    private int bindGeneration;
    /** The floor each answering bubble replies to, so a tap on its preview jumps to that floor. */
    private final HashMap<MessageObject, Integer> replyTargetFloors = new HashMap<>();
    /** Floor numbers whose reply target is being read from the site, so each is asked for once. */
    private final java.util.HashSet<Integer> replyRequestsInFlight = new java.util.HashSet<>();
    private List<Long> streamPostIds = new ArrayList<>();
    /**
     * Where the loaded window sits in that stream, the pages on their way and the reading mode the
     * rows on screen were read in. The screen renders what it reports; the state, its races and
     * its failure recovery live there.
     */
    private final TopicReadingWindow reading = new TopicReadingWindow();
    /** The reader's place in this topic, and the report of it the site asks for. */
    private TopicReadReporter reporter;
    /** What is shown instead of the list when the topic could not be read at all. */
    private org.telegram.ui.Cells.ChatActionCell failureView;
    /** Why the last read failed, in the terms the retry tap acts on. */
    private TopicLoadFailure lastFailure;
    /** What a retry tap runs: the read that just failed, with the floor it asked for. */
    private Runnable retryAction;
    /**
     * Whether the rows on screen came from the offline cache rather than the site. While true the
     * screen says so and refuses writes that assume the site's answer about permissions; a live read
     * clears it.
     */
    private boolean showingCachedContent;

    public static TopicFragment forTopic(long topicId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        return new TopicFragment(args);
    }

    /**
     * The same screen, opened from a row that already knows who wrote the topic: the capsule shows
     * that author's picture at once, and the answer from the site only rebinds when it names a
     * different person. A row without an author (a notification, a bookmark) keeps the plain
     * [forTopic].
     */
    public static TopicFragment forTopic(
            long topicId, String title, String authorUsername, String authorAvatarTemplate) {
        TopicFragment fragment = forTopic(topicId, title);
        if (authorUsername != null && !authorUsername.isEmpty()) {
            fragment.getArguments().putString(ARG_AUTHOR_USERNAME, authorUsername);
            if (authorAvatarTemplate != null && !authorAvatarTemplate.isEmpty()) {
                fragment.getArguments().putString(ARG_AUTHOR_AVATAR_TEMPLATE, authorAvatarTemplate);
            }
        }
        return fragment;
    }

    public static TopicFragment forTopic(io.github.baba983.nativedo.core.model.TopicSummary topic) {
        TopicFragment fragment = forTopic(topic.getId(), topic.getTitle(),
                topic.getAuthorUsername(), topic.getAuthorAvatarTemplate());
        fragment.getArguments().putInt(ARG_REPLY_COUNT, topic.getReplyCount());
        if (topic.getCategoryId() != null) {
            fragment.getArguments().putInt(ARG_CATEGORY_ID, topic.getCategoryId());
        }
        return fragment;
    }

    /**
     * The same screen, opened on one floor of the topic: a feed row and a search result both point
     * at the post they are about, not at the top of a thread that may be hundreds of floors long.
     * A floor the site does not give is the topic's first one.
     */
    public static TopicFragment forFloor(long topicId, String title, Integer floor) {
        TopicFragment fragment = forTopic(topicId, title);
        if (floor != null && floor > 0) {
            fragment.getArguments().putInt(ARG_FLOOR, floor);
        }
        return fragment;
    }

    /** [forFloor], with the author the row already drew (see [forTopic]'s four-argument form). */
    public static TopicFragment forFloor(
            long topicId, String title, Integer floor, String authorUsername, String authorAvatarTemplate) {
        TopicFragment fragment = forTopic(topicId, title, authorUsername, authorAvatarTemplate);
        if (floor != null && floor > 0) {
            fragment.getArguments().putInt(ARG_FLOOR, floor);
        }
        return fragment;
    }

    /**
     * The discussion page a floor's reply footer opens: the same screen, in its thread mode. It
     * holds the one post whose thread this is and the direct replies to it, addressed by the root's
     * real post <em>id</em> and paged by the greatest reply <em>floor</em> it already holds.
     *
     * <p>The identity travels in the arguments, so a page the system rebuilds resolves the parent
     * again by its own floor; the caller that already holds the post hands it (and the topic) over
     * with [seedThread], so the page draws before the site answers.</p>
     */
    public static TopicFragment forReplies(
            long topicId, String title, long parentPostId, int parentFloor, int replyCount) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        args.putLong(ARG_THREAD_PARENT_POST_ID, parentPostId);
        args.putInt(ARG_THREAD_PARENT_FLOOR, Math.max(0, parentFloor));
        args.putInt(ARG_THREAD_REPLY_COUNT, Math.max(0, replyCount));
        return new TopicFragment(args);
    }

    /**
     * The root the caller already drew, and the topic it belongs to. The thread binds them at once,
     * so the reader sees the floor they tapped while the replies are on their way; on a rebuild the
     * seed is null and the parent is resolved from the site instead.
     */
    public TopicFragment seedThread(Post root, TopicDetail detail) {
        threadSeedRoot = root;
        threadSeedDetail = detail;
        return this;
    }

    /** Whether this screen is a floor's discussion page rather than a topic read. */
    public boolean isThreadMode() {
        return getArguments() != null && getArguments().containsKey(ARG_THREAD_PARENT_POST_ID);
    }

    /** Whether the thread still has an unread page, per the site's own reply count. */
    public boolean threadHasMore() {
        return threadMode && !threadExhausted
                && Math.max(0, floors.size() - 1) < threadReplyCount;
    }

    /**
     * The page's own read-on/retry affordance: upstream's floating page-down button, the control a
     * reader drives a page they cannot scroll to. Exposed so a test reaches the real affordance it
     * acts on, not a stand-in for it.
     */
    public View threadStatusView() {
        return threadPageDown;
    }

    /**
     * The picker's answer: the file is uploaded to the site, and what the field receives is the
     * markdown the site's own composer would have written for it. The pick is one way in; the same
     * path is [attachMediaUri], so a locally written URI (a recording) needs no Activity result.
     */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != PICK_ATTACHMENT_REQUEST
                || resultCode != android.app.Activity.RESULT_OK
                || data == null) {
            return;
        }
        attachMediaUri(data.getData());
    }

    /**
     * Uploads one readable URI into the reply field. This is the seam a recorder or another screen
     * can drive: it resolves the URI, uploads it on the session-aware client, and inserts the
     * site's markdown - no Activity result, no picker. A URI the app cannot read, or one the site
     * refuses, leaves the field exactly as it was and reports the failure.
     */
    public void attachMediaUri(android.net.Uri uri) {
        if (uri != null) attachMediaUris(java.util.Collections.singletonList(uri));
    }

    /**
     * The same path for a URI the reader's keyboard committed or pasted. The upload owner copies
     * the URI into the app's cache before it returns, so the IME's read grant -
     * {@code releasePermission}, a no-op for a paste - is released once those bytes are ours. A
     * screen that can no longer take the file (closed, or already uploading) releases it without
     * inserting anything, so the grant never outlives its commit.
     */
    public void attachMediaUri(android.net.Uri uri, Runnable releasePermission) {
        try {
            attachMediaUri(uri);
        } finally {
            if (releasePermission != null) releasePermission.run();
        }
    }

    private void attachMediaUris(java.util.List<android.net.Uri> uris) {
        if (getParentActivity() == null || container == null || uploadingAttachment) {
            return;
        }
        DiscourseAttachmentUpload.upload(getParentActivity(), container, uris,
                new DiscourseAttachmentUpload.Sink() {
                    @Override
                    public void onUploadStarted() {
                        setUploadingAttachment(true);
                    }

                    @Override
                    public void onUploaded(String markup) {
                        setUploadingAttachment(false);
                        io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(
                                composer == null ? null : composer.getEditField(), markup);
                        updateComposerActions();
                        saveDraft();
                    }

                    @Override
                    public void onUploadFailed(Throwable error) {
                        setUploadingAttachment(false);
                        showReplyError(error);
                    }
                });
    }

    @Override
    public boolean onFragmentCreate() {
        currentAccount = 0;
        return super.onFragmentCreate();
    }

    @Override
    protected View createView(Context context) {
        long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        String title = getArguments() == null ? null : getArguments().getString(ARG_TOPIC_TITLE);
        // The bar floats over the post list exactly the way a chat header does: it is kept out of
        // the container (ChatActivity:3678 setAddToContainer(false)) and given to this screen's own
        // content view, with no background and the chat's own back arrow (3680-3686).
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setBackground(null);
        actionBar.setBackButtonDrawable(new org.telegram.ui.ActionBar.BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed(true)) {
                    finishFragment();
                } else if (id == MENU_ITEM_BOOKMARK) {
                    toggleTopicBookmark();
                } else if (id == MENU_ITEM_COPY_LINK) {
                    copyTopicLink();
                } else if (id == MENU_ITEM_SHARE) {
                    shareTopic();
                } else if (id == MENU_ITEM_OPEN_BROWSER) {
                    openTopicInBrowser();
                } else if (id == MENU_ITEM_SEARCH) {
                    openTopicSearchFromMenu();
                } else if (id == MENU_ITEM_NOTIFICATION) {
                    showNotificationLevelOptions();
                } else if (id == MENU_ITEM_ONLY_OP) {
                    toggleOnlyOp();
                }
            }
        });
        // The chat header's own overflow (its 12.10.5 edit item is the same ic_ab_other,
        // ChatActivity:4281): one item whose sub-items are this topic's actions. The menu is
        // created before setupGlass because that is where it is switched into glass mode
        // (ActionBar.java:245-248).
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        overflowItem = menu.addItem(MENU_ITEM_MORE, org.telegram.messenger.R.drawable.ic_ab_other);
        overflowItem.setContentDescription("更多");
        bookmarkMenuItem = overflowItem.addSubItem(
                MENU_ITEM_BOOKMARK, org.telegram.messenger.R.drawable.msg_fave, "书签");
        notificationLevelMenuItem = overflowItem.addSubItem(
                MENU_ITEM_NOTIFICATION, org.telegram.messenger.R.drawable.msg_folders_read, "");
        updateNotificationLevelMenuItem();
        // Reading only the topic's original poster, as the web client's own author filter does.
        // A discussion page is already a subset of one floor, so the row belongs to an ordinary
        // read; the label says which way the next tap goes, like the bookmark row.
        if (!isThreadMode()) {
            onlyOpMenuItem = overflowItem.addSubItem(
                    MENU_ITEM_ONLY_OP, org.telegram.messenger.R.drawable.msg_openprofile, "只看楼主");
            updateOnlyOpMenuItem();
        }
        overflowItem.addSubItem(
                MENU_ITEM_COPY_LINK, org.telegram.messenger.R.drawable.msg_link, "复制链接");
        overflowItem.addSubItem(
                MENU_ITEM_SHARE, org.telegram.messenger.R.drawable.msg_share, "分享");
        overflowItem.addSubItem(
                MENU_ITEM_OPEN_BROWSER, org.telegram.messenger.R.drawable.msg_openin, "在浏览器打开");
        // The reader's entry to the in-topic search: a row of the overflow, never an icon of its
        // own on the bar. This is the topic's 话题内搜索 item; the bar's real search field sits
        // behind it (the GONE searchItem below) and this row opens it (openTopicSearchFromMenu).
        overflowItem.addSubItem(
                MENU_ITEM_SEARCH, org.telegram.messenger.R.drawable.msg_search, "话题内搜索");
        // The chat header's own search: a real ActionBar search item, not a drawn imitation and not
        // an invisible bar. It is kept GONE so the bar's right side stays the single overflow
        // capsule; the overflow's 话题内搜索 row above is what expands it. Opening hands the field
        // to this screen (onSearchExpand), its clear button empties the query, the bar's back
        // capsule and hardware Back collapse it (onSearchCollapse), and the menu is switched into
        // glass mode by setupGlass below (ChatActivity:4244-4248; ActionBarMenuItem.toggleSearch,
        // ActionBar:1304-1326).
        searchItem = menu.addItem(MENU_ITEM_SEARCH, org.telegram.messenger.R.drawable.msg_search);
        searchItem.setContentDescription("话题内搜索");
        searchItem.setIsSearchField(true);
        searchItem.setSearchPaddingStart(7);
        searchItem.setSearchFieldHint("搜索当前话题");
        searchItem.setActionBarMenuItemSearchListener(
                new org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                if (!openTopicSearch()) {
                    // The window is not readable yet (a load is in flight): do not leave an empty
                    // search capsule with no session behind it. The collapse is posted because this
                    // callback runs inside the expand, before ActionBar has marked its own field
                    // visible; closeSearchField is a no-op until then (ActionBar:1308-1313).
                    actionBar.post(() -> actionBar.closeSearchField(false));
                }
            }

            @Override
            public void onSearchCollapse() {
                onTopicSearchCollapsed();
            }

            @Override
            public void onTextChanged(android.widget.EditText editText) {
                if (topicSearch != null) {
                    topicSearch.changeQuery(editText.getText().toString());
                }
            }

            @Override
            public void onSearchPressed(android.widget.EditText editText) {
                if (topicSearch != null) {
                    topicSearch.onImeSearch();
                }
            }
        });
        // GONE, not merely un-drawn: ActionBar sizes the glass menu circle from the visible items'
        // span (ActionBarMenu.getItemsWidth skips a GONE item, ActionBar:2203-2217), so a hidden
        // search item leaves the overflow capsule exactly as it is instead of reserving a second
        // icon next to it. ActionBarMenuItem.toggleSearch re-shows the item on every collapse
        // (ActionBarMenuItem:987), so restoreBarMenu puts it back to GONE after each close.
        searchItem.setVisibility(View.GONE);

        sharedResources = new org.telegram.messenger.ChatMessageSharedResources(context);
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // ChatActivity's own dispatch does this: a tap away from the selection's handles
                // ends the selection and must not also reach what it landed on.
                if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
                    org.telegram.ui.Cells.TextSelectionHelper.TextSelectionOverlay selectionOverlay =
                            textSelectionHelper.getOverlayView(getContext());
                    if (selectionOverlay.checkOnTap(ev)) {
                        ev.setAction(MotionEvent.ACTION_CANCEL);
                    }
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public void onViewAdded(View child) {
                super.onViewAdded(child);
                if (composer != null && child == composer.controlsView && glassHost != null) {
                    composer.controlsView.setBlurredBackgroundFactory(glassHost.getFactory());
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (composer == null) return;
                // ChatActivityFragmentView: recording overlays share the input island's inset.
                int inset = inputInsets == null ? 0 : inputInsets.getCurrentMaxBottomInset();
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (composer.isRecordCircleOrControlsView(child)) {
                        child.offsetTopAndBottom(-inset - AndroidUtilities.dp(7));
                        child.offsetLeftAndRight(-AndroidUtilities.dp(3));
                    } else if (child == composer.recordedAudioPanel) {
                        child.offsetTopAndBottom(-inset - AndroidUtilities.dp(9));
                    }
                }
            }

            // ChatActivityFragmentView (17652-17660): the bar floats over the posts, so the
            // wallpaper starts at the top of the screen instead of below a bar and status bar.
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                // The capsules' blur source records the post list before the frame draws its
                // children, so a capsule samples the posts behind it and never itself - the same
                // wiring the topic list's floating strip uses.
                if (glassHost != null) {
                    glassHost.record();
                }
                super.dispatchDraw(canvas);
            }
        };
        // ChatActivity.updateBackground (44180-44191): the posts sit on the chat wallpaper, which is
        // also what makes the white glass capsules read as capsules.
        wallpaperView = contentView;
        updateBackground();
        // The capsules' bottom edge sits one dp past the bar's own height, and the bar's shadows
        // live at its edges: neither may be clipped by the frame (ChatActivity does the same,
        // 7797).
        contentView.setClipChildren(false);
        listView = new PostListView(context);
        // ChatActivity:6421 - the list's top padding is the floating bar's own, and a post scrolled
        // up is drawn through it to the frame's top edge (under the capsules and the status bar)
        // instead of being cut off in a line at the bar's bottom edge.
        listView.setClipToPadding(false);
        listView.setAvatarSource(senderId -> users.get(senderId));
        // Upstream's own chat-list manager: it is the one whose vertical scroll the swipe gesture
        // switches off while a post is being dragged (the ported processTouchEvent). The list makes
        // it, so it can carry the pull the reader makes once the stream is at its end.
        androidx.recyclerview.widget.GridLayoutManagerFixed layoutManager =
                listView.createChatLayoutManager(context);
        layoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1000;
            }
        });
        listView.setLayoutManager(layoutManager);
        // The pull from the end of the stream: the list owns the gesture, this screen owns the site's
        // candidate and the states that forbid the pull (see canPullNextTopic).
        listView.setNextTopicPullHost(new PostListView.NextTopicPullHost() {
            @Override
            public boolean canPullNextTopic() {
                return TopicFragment.this.canPullNextTopic();
            }

            @Override
            public void onPullNextTopic() {
                openNextTopicFromPull();
            }
        });
        listView.setAdapter(new PostAdapter());
        // One leftward drag on a post is the menu's 回复: the composer opens on that floor.
        listView.setSwipeReplyListener(cell -> {
            int floor = floorOf(cell.getMessageObject());
            if (floor >= 0) {
                replyTo(floor);
            }
        });
        // Upstream's cell only arms its own long press when the touch landed on something it calls
        // pressable, which a rich message's text is not, so the row asks for the menu itself. Same
        // floor, same menu as a long press would open, anchored on the row that was pressed.
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= bubbleFloors.size() || isDividerRow(position)) {
                return false;
            }
            showMessageMenu(view, bubbleFloors.get(position));
            return true;
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                maybeLoadNextPage(dy);
                reportViewport();
                // A holographic tag that scrolled back into view re-arms the frame loop.
                startTitleGradient();
                if (glassHost != null) {
                    // What the band and the capsules draw is recorded from the list each frame,
                    // and a view keeps the frame it was last recorded with until it is told to
                    // redraw: ChatActivity's chat list drives invalidateAllGlassAttachedViews
                    // from its own scroll invalidation (6792/6872 -> 46715).
                    glassHost.invalidateAttachedViews();
                }
                if (reporter != null) {
                    // Scrolling is what tells the dwell clock the reader is still reading; without
                    // it a topic left open on a desk would count as read time.
                    reporter.noteActivity();
                }
                if (textSelectionHelper != null) {
                    // A scroll moves the selection's coordinates with the list; upstream's chat does
                    // the same in its own scroll listener.
                    textSelectionHelper.onParentScrolled();
                }
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    showFloatingDateView();
                }
                updateFloatingDate();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (textSelectionHelper != null) {
                        textSelectionHelper.stopScrolling();
                    }
                    // The pill is a scroll aid: it leaves once the day's own row is back on screen,
                    // and stays up while that row is above the edge (ChatActivity 6775-6784).
                    scrollingFloatingDate = false;
                    hideFloatingDateView(true);
                    updateFloatingDate();
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollingFloatingDate = true;
                }
            }
        });
        // The first layout is what fills the viewport, and a viewport that never scrolls never
        // reports a scroll: the floors the reader has seen are reported from the layout too.
        listView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            reportViewport();
            // The pill's own text and offset are read from the rows, which a layout just moved.
            updateFloatingDate();
        });

        // The bar floats at the top of the list and the composer at its bottom, so the list keeps
        // both clear: the top padding is the bar's measured height (the status bar is already in
        // it), the same term ChatActivity's own list padding adds (11947-11952).
        updateListPadding();
        // ChatActivity builds the glass source before anything else in the screen (2620-2661) and
        // hands the same factory to the bar's capsules and to the fade band below (6969-6973).
        glassHost = new io.github.baba983.nativedo.app.GlassPanelHost(listView, contentView);
        // The wallpaper chain is part of what a capsule samples (ChatActivity's navbar source draws
        // the wallpaper under the posts); updateBackground ran before this host existed, so the
        // drawable it just gave the frame is handed over here.
        glassHost.setWallpaperDrawable(org.telegram.ui.ActionBar.Theme.getCachedWallpaper());
        contentView.addView(listView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        // The floating date, the way a chat shows it: the day of the top row, drawn over the posts
        // while that day's own date row is above the list's top edge, and pushed up as that row
        // leaves so the two hand over to each other. The overrides are ChatActivity's own
        // (7043-7099): the pill is clipped to the edge its list starts at and only takes touches
        // while it is up.
        floatingDate = new org.telegram.ui.Cells.ChatActionCell(context, false, resourceProvider) {
            @Override
            public boolean isFloating() {
                return true;
            }

            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    invalidate();
                }
                super.setTranslationY(translationY);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return getAlpha() != 0 && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return getAlpha() != 0 && super.onTouchEvent(event);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                // The list does not clip its children to its padding (see the list's own setup), so
                // the pill is clipped to the edge its list starts at, as upstream's is (7074-7087).
                float clipTop = listView.getY() + listPaddingTop - getY() - AndroidUtilities.dp(4);
                if (clipTop > 0) {
                    if (clipTop < getMeasuredHeight()) {
                        canvas.save();
                        canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
                        super.onDraw(canvas);
                        canvas.restore();
                    }
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        };
        floatingDate.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDate.setAlpha(0f);
        floatingDate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floatingDate.setInvalidateColors(true);
        contentView.addView(floatingDate, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        // The pill rests under the edge its list starts at; the first read puts it there.
        updateFloatingDate();
        // ChatActivity's own fade band (6969-6973): added right after the list, so the posts scroll
        // behind it and every overlay - composer, progress, bar - draws over it. It is built from
        // the wallpaper factory and not from the capsules' one (6970 -> 2652): the band is the
        // wallpaper fading in over the posts, so what the status bar and the bar's own gaps cover
        // is the chat's own backdrop rather than a recording of the posts that is itself scrolling.
        fadeView = new org.telegram.ui.Components.chat.layouts.ChatActivityFadeView(context);
        fadeView.setup(glassHost.getWallpaperFactory());
        fadeView.setFadeHeightTop(AndroidUtilities.dp(48));
        fadeView.setFadeHeightBottom(AndroidUtilities.dp(48));
        contentView.addView(fadeView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        attachStrip = createAttachStrip(context);
        contentView.addView(attachStrip, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                STRIP_HEIGHT_DP,
                Gravity.BOTTOM | Gravity.LEFT, 7, 0, 7, 0));
        attachStrip.setVisibility(View.GONE);
        // The discussion page's own read-on affordance: upstream's floating round page-down button,
        // built the way the chat's side controls build it (ChatActivitySideControlsButtonsLayout
        // .java:231 -> ChatActivityBlurredRoundPageDownButton.create(context, 56, 48, ...)) from the
        // same glass source as the composer island. It floats at the trailing edge over the real
        // composer - a 56dp circle with its remaining-count badge and stock spinner - instead of the
        // made-up full-width TextView strip this screen used to draw. Its tap reads on while a page
        // is left and retries one that failed; it is never a private method a production caller
        // never touches.
        org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider buttonProvider =
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .bottomPanelChatActivity(resourceProvider);
        threadPageDown =
                org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton.create(
                        context,
                        56, 48,
                        resourceProvider,
                        glassHost.getFactory(),
                        buttonProvider,
                        org.telegram.messenger.R.drawable.pagedown);
        threadPageDown.setClipChildren(false);
        threadPageDown.setClipToPadding(false);
        threadPageDown.setVisibility(View.GONE);
        // Upstream's own pivot for the page-down button (ChatActivitySideControlsButtonsLayout
        // .getOrCreateButtonHolder): the circle's centre, lifted by the badge's own 8dp.
        threadPageDown.setPivotX(AndroidUtilities.dp(28));
        threadPageDown.setPivotY(AndroidUtilities.dp(28 + 8));
        threadPageDown.setOnClickListener(view -> onPageDownTap());
        contentView.addView(threadPageDown, org.telegram.ui.Components.LayoutHelper.createFrame(
                56,
                64,
                Gravity.RIGHT | Gravity.BOTTOM));
        // The same control, pointing the other way: a long discussion read from the middle keeps
        // its first post one tap away. Same glass source, size, pivot and animation as the
        // page-down button, floating directly above it so the pair reads as one stack.
        threadPageUp =
                org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton.create(
                        context,
                        56, 48,
                        resourceProvider,
                        glassHost.getFactory(),
                        buttonProvider,
                        io.github.baba983.nativedo.R.drawable.ic_nativedo_page_up);
        threadPageUp.setClipChildren(false);
        threadPageUp.setClipToPadding(false);
        threadPageUp.setVisibility(View.GONE);
        threadPageUp.setPivotX(AndroidUtilities.dp(28));
        threadPageUp.setPivotY(AndroidUtilities.dp(28 + 8));
        threadPageUp.setContentDescription("回到顶部");
        threadPageUp.setOnClickListener(view -> {
            if (reading.needsFirstFloorRead()) {
                // The loaded window starts part-way into the stream, so row 0 is not floor 1:
                // read the head and land on the first floor instead of scrolling to the window's top.
                loadWindow(1, 1);
            } else if (listView != null) {
                listView.smoothScrollToPosition(0);
            }
        });
        contentView.addView(threadPageUp, org.telegram.ui.Components.LayoutHelper.createFrame(
                56,
                64,
                Gravity.RIGHT | Gravity.BOTTOM));
        // The empty discussion's own note: upstream's stock service pill, a ChatActionCell carrying
        // setCustomText (the shape a chat draws its "no messages" line with), centred where the old
        // styled bar stood. It is shown only while the root is the whole thread, and its copy never
        // claims there are no replies when the site's own count says there are.
        threadEmptyCell = new org.telegram.ui.Cells.ChatActionCell(context, false, resourceProvider);
        threadEmptyCell.setVisibility(View.GONE);
        contentView.addView(threadEmptyCell, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.LEFT, 7, 0, 7, 0));
        // Upstream's floating composer host (ChatActivity.createView:4587-4599): the island bubble
        // the enter view rides in, its blur drawn from the same source as the bar's capsules. The
        // enter view is added to that island - not to the screen - which is the whole difference
        // between the full-width strip this screen used to draw and the chat's floating capsule.
        inputViews = new org.telegram.ui.Components.chat.ChatInputViewsContainer(context);
        inputViews.setClipChildren(false);
        inputInsets = new org.telegram.ui.Components.inset.WindowInsetsStateHolder(() -> {
            if (inputViews != null) {
                inputViews.checkInsets();
                placeAttachStrip();
                applyListPadding();
                checkUi_BlurHeight();
            }
        });
        inputViews.setWindowInsetsProvider(inputInsets);
        inputViews.getInputIslandBubbleContainer().setClipChildren(false);
        org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider inputProvider =
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .bottomPanelChatActivity(resourceProvider);
        inputViews.setInputIslandBubbleDrawable(glassHost.createPanel(inputViews, inputProvider));
        inputViews.setUnderKeyboardBackgroundDrawable(glassHost.createPanel(inputViews, inputProvider));
        contentView.addView(inputViews, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        inputViews.getInputIslandBubbleContainer().addView(createComposer(contentView),
                org.telegram.ui.Components.LayoutHelper.createFrame(
                        org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                        org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.BOTTOM, 7, 0, 7, 0));
        // The affordance floats on the island's own measured height, which is only known after a
        // layout: reposition it whenever the island's size changes, not only on an inset change.
        inputViews.getInputIslandBubbleContainer().addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        placeThreadStatus());

        // ChatActivity's own first-load view (6984-7016): a 36dp round rect on the service
        // background with a 32dp progress ring (its size 28dp) on it, centred. Shown and hidden
        // by showProgressView with upstream's 150ms alpha/scale animation; a chat does not show
        // the list shimmer, and neither does this screen.
        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        contentView.addView(progressView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                Gravity.TOP | Gravity.LEFT));
        progressView2 = new View(context) {
            private final RectF rect = new RectF();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                applyServiceShaderMatrix();
                canvas.drawRoundRect(rect, AndroidUtilities.dp(18), AndroidUtilities.dp(18),
                        getThemedPaint(Theme.key_paint_chatActionBackground));
                if (resourceProvider != null ? resourceProvider.hasGradientService()
                        : Theme.hasGradientService()) {
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(18), AndroidUtilities.dp(18),
                            getThemedPaint(Theme.key_paint_chatActionBackgroundDarken));
                }
                super.dispatchDraw(canvas);
            }

            /** ChatActivity:7001-7006 - the rect is read through the chat's own service shader. */
            private void applyServiceShaderMatrix() {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(),
                        contentView.getMeasuredHeight(), getX(), getY());
            }
        };
        progressView.addView(progressView2, org.telegram.ui.Components.LayoutHelper.createFrame(
                36, 36, Gravity.CENTER));
        progressBar = new RadialProgressView(context, resourceProvider);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(getThemedColor(Theme.key_chat_serviceText));
        progressView.addView(progressBar, org.telegram.ui.Components.LayoutHelper.createFrame(
                32, 32, Gravity.CENTER));

        // The same wallpaper-backed service bubble a chat uses for its empty notice. A failure
        // offers retry or session recovery, without exposing the transport's diagnostic message.
        failureView = new org.telegram.ui.Cells.ChatActionCell(context, false, resourceProvider);
        failureView.setCustomText(context.getString(io.github.baba983.nativedo.R.string.topic_failed));
        failureView.setVisibility(View.GONE);
        failureView.setOnActionClickListener(view -> onFailureTap());
        failureView.setOnClickListener(view -> onFailureTap());
        contentView.addView(failureView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
        failureView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                failureView.setVisiblePart(t, contentView.getHeight()));

        // The center capsule is the chat's own ChatAvatarContainer in glass mode, added to the bar
        // the way ChatActivity adds it (4134-4162, 4224: WRAP_CONTENT next to MATCH_PARENT, margins
        // 52/52). The right margin is the single-icon one: this bar carries only the overflow
        // capsule - the in-topic search is a row inside that menu, not an icon beside it - so the
        // title stops before the one icon the way a chat header with a lone overflow does. The bar
        // itself draws the back, center and menu capsules around it and animates their widths
        // (ActionBar.java:2219-2270), and setupGlass is what turns the three glass drawables on with
        // the chat header's own provider (ActionBar.java:210-255; ChatActivity:4569-4572). The list
        // is the content the capsules' blur source records.
        avatarContainer = new org.telegram.ui.Components.ChatAvatarContainer(
                context, this, false, resourceProvider);
        avatarContainer.setGlassMode();
        avatarContainer.setClipChildren(false);
        avatarContainer.setActionBar(actionBar);
        avatarContainer.getTitleTextView().setScrollNonFitText(true);
        // A tap on the capsule is the reader asking for the author, the same as a tap on a post's
        // avatar or name (ChatActivity opens the profile from its own listener the same way).
        avatarContainer.setOnClickListener(view -> openTopicAuthorProfile());
        if (title != null && !title.trim().isEmpty()) {
            setCapsuleTitle(title.trim());
        }
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
        // ChatActivityFragmentView.onLayout re-reads the fade zone on every layout (18627); the
        // bar's measured height follows the status bar inset, so the zone is re-read with it.
        contentView.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> checkUi_topFade());
        checkUi_topFade();
        checkUi_BlurHeight();

        // Upstream's selection, wired the way ChatActivity wires it: the overlay is a child of the
        // content view, the message list is its parent (that is where a handle's coordinates are
        // computed from), and the callback ends a sliding gesture that the selection supersedes.
        textSelectionHelper = new org.telegram.ui.Cells.TextSelectionHelper.ChatListTextSelectionHelper();
        textSelectionHelper.setCallback(new org.telegram.ui.Cells.TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                if (isSelected && listView != null) {
                    // A selection that starts ends a sliding gesture, upstream's callback does the
                    // same: the two touches can never belong to one drag.
                    listView.cancelSlidingView();
                }
            }

            @Override
            public void onTextCopied() {
            }
        });
        View selectionOverlay = textSelectionHelper.getOverlayView(context);
        contentView.addView(selectionOverlay);
        textSelectionHelper.setParentView(listView);
        listView.setTextSelectionHelper(textSelectionHelper);

        fragmentView = contentView;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onInsetsInternal);
        // The capsules' glass and the capsule's own colours follow the day/night switch, the way
        // the topic list's strip is told.
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
        if (isThreadMode()) {
            // The root the caller held is drawn while its replies are read: a discussion page, not a
            // window at the top of the topic.
            readThreadArguments();
            loadThread();
        } else {
            loadTopic(topicId);
        }
        // Whatever the reader left in this field last time is theirs again; the restore runs after
        // the composer exists and before any change can be saved back over it, and for a thread it
        // is the thread's own field (scoped by the root floor), never the topic's.
        loadDraft();
        if (threadMode) {
            // A target the stored thread draft actually named is shown again, so an explicit
            // restored reply is honoured. With nothing stored this is a no-op: a discussion page
            // does not open a strip for a default target (upstream's own discussion composer shows
            // none either).
            applyReplyTarget();
        }
        return fragmentView;
    }

    /**
     * Upstream's own composer, the one a chat screen uses. It takes the chat it writes to as a
     * parameter, and this client has no chat: everything that reads it belongs to Telegram's send
     * paths (attachments, stickers, voice, bots), which this screen does not offer, so it is given
     * none. What is left is the field, the emoji panel and the send button, and the send button
     * reports to the delegate — which is where the post goes to Discourse.
     */
    private android.view.View createComposer(SizeNotifierFrameLayout contentView) {
        ChatActivityEnterView view = new ChatActivityEnterView(
                getParentActivity(), contentView, null, true, resourceProvider) {
            @Override
            protected void onChangedIslandTotalHeight(float height) {
                if (inputViews != null) {
                    inputViews.setInputBubbleHeight(Math.max(height, AndroidUtilities.dp(44)));
                    placeAttachStrip();
                    applyListPadding();
                    checkUi_BlurHeight();
                }
            }
        };
        view.shouldDrawBackground = false;
        view.setMinimumHeight(AndroidUtilities.dp(51));
        view.setInAppInsetsController(inputInsets);
        view.setViewParentForEmoji(inputViews.getInAppKeyboardBubbleContainer());
        view.setAllowStickersAndGifs(false, false, false);
        composerDelegate = new ReplyDelegate();
        view.setDelegate(composerDelegate);
        // The composer decides the button again whenever it is laid out (a keyboard, an emoji panel,
        // a first layout), so the decision is repeated there too.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateComposerActions();
            // ChatActivity re-reads the island height and the insets when the input's height
            // changes (checkUi_inputIslandHeight, 46388-46403); the list padding and the fade zone
            // below then follow the real input instead of a fixed strip.
            if (inputViews != null) {
                inputViews.setInputBubbleHeight(Math.max(view.getIslandTotalHeight(false), AndroidUtilities.dp(44)));
                inputViews.checkInsets();
                placeAttachStrip();
                applyListPadding();
            }
            checkUi_BlurHeight();
        });
        composer = view;
        return view;
    }

    /**
     * The composer's own attach button is offered only while the field is empty — that is upstream's
     * design, and a forum reply often wants a second file after the first one has already put text
     * in the field. This strip appears exactly then (the reader keeps the paperclip while the field
     * is empty), and it appends the next upload's markdown to whatever is already written.
     */
    private View createAttachStrip(Context context) {
        android.widget.LinearLayout strip = new android.widget.LinearLayout(context);
        strip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        strip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                getThemedColor(Theme.key_chat_messagePanelBackground)));

        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(org.telegram.messenger.R.drawable.msg_input_attach2);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_chat_messagePanelIcons),
                android.graphics.PorterDuff.Mode.MULTIPLY));
        strip.addView(icon, org.telegram.ui.Components.LayoutHelper.createLinear(
                AndroidUtilities.dp(24), AndroidUtilities.dp(24)));

        attachStripLabel = new android.widget.TextView(context);
        attachStripLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        attachStripLabel.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_chat_messagePanelHint));
        attachStripLabel.setText("添加图片或附件");
        attachStripLabel.setSingleLine(true);
        strip.addView(attachStripLabel, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));

        // The rich editor's own entry: the reader types here, then opens the block editor to build
        // the post properly, and what comes back is the markdown the site stores. It is our own
        // button on our own strip, so upstream's composer is untouched.
        richTextButton = new android.widget.TextView(context);
        richTextButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        richTextButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        richTextButton.setText("富文本");
        richTextButton.setSingleLine(true);
        richTextButton.setGravity(Gravity.CENTER_VERTICAL);
        richTextButton.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        richTextButton.setOnClickListener(v -> openRichEditor());
        strip.addView(richTextButton, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));

        strip.setContentDescription("添加图片或附件");
        strip.setOnClickListener(v -> openAttachmentSheet());
        return strip;
    }

    /**
     * The reader's paperclip: the Telegram-shaped attachment sheet, not a bare system picker.
     *
     * <p>Upstream's own attach menu is account-backed, so this is this client's sheet - the same
     * shape over the actions the site can carry (see {@link DiscourseAttachSheet}). The sheet is
     * the only entry point; it hands back the chosen action and the screen does the rest.</p>
     */
    private void openAttachmentSheet() {
        if (getParentActivity() == null || uploadingAttachment || container == null) {
            return;
        }
        CharSequence raw = composer == null ? null : composer.getFieldText();
        attachSheet = new DiscourseAttachSheet(getParentActivity(), this, resourceProvider,
                new DiscourseAttachSheet.Listener() {
                    @Override
                    public void onAttachAction(DiscourseAttachSheet.Action action) {
                        TopicFragment.this.onAttachAction(action);
                    }

                    @Override
                    public void onAttachUris(java.util.List<android.net.Uri> uris) {
                        attachMediaUris(uris);
                    }

                    @Override
                    public void onRichText(String applied) {
                        if (composer != null) composer.setFieldText(applied);
                        saveDraft();
                    }
                }, this::insertPollRaw, raw == null ? "" : raw.toString());
        attachSheet.init();
        showDialog(attachSheet);
    }

    /** The sheet's answer: a picker for a file or the rich editor. The poll stays in the sheet. */
    private void onAttachAction(DiscourseAttachSheet.Action action) {
        switch (action) {
            case GALLERY:
                openAttachmentPicker("image/*");
                break;
            case FILES:
                openAttachmentPicker("*/*");
                break;
            case RICH_EDITOR:
                openRichEditor();
                break;
        }
    }

    /**
     * The system's content picker; what it answers is uploaded by [onActivityResultFragment]. The
     * gallery asks for images and the file action for anything the provider can open.
     */
    private void openAttachmentPicker(String mimeType) {
        if (getParentActivity() == null || uploadingAttachment) {
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        startActivityForResult(intent, PICK_ATTACHMENT_REQUEST);
    }

    /**
     * The inline poll's raw, written into the field at the caret like any other markup - the site's
     * own poll syntax, never a Telegram poll. The sheet hands it here when its own Poll form is
     * submitted, so the poll is written where it is read instead of in a second popup.
     */
    private void insertPollRaw(String raw) {
        if (composer == null) {
            return;
        }
        io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(composer.getEditField(), raw);
        updateComposerActions();
        saveDraft();
    }

    /** Opens the sheet's upstream article pane on the field's current Markdown. */
    private void openRichEditor() {
        if (getParentActivity() == null || composer == null) {
            return;
        }
        openAttachmentSheet();
        if (attachSheet != null) attachSheet.openArticle();
    }

    /**
     * The strip follows the island's animated height, not the enter view's last layout height.
     */
    private void placeAttachStrip() {
        if (attachStrip == null || inputViews == null) {
            return;
        }
        if (composerTools != null) {
            composerTools.updatePosition(attachStrip.getVisibility() == View.VISIBLE
                    ? AndroidUtilities.dp(STRIP_HEIGHT_DP + 4) : 0);
        }
        placeThreadStatus();
        int composerHeight = Math.round(inputViews.getInputBubbleHeight()) + AndroidUtilities.dp(16)
                + (inputInsets == null ? 0 : Math.round(inputInsets.getAnimatedMaxBottomInset()));
        ViewGroup.LayoutParams params = attachStrip.getLayoutParams();
        if (params instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams frame = (android.widget.FrameLayout.LayoutParams) params;
            if (frame.bottomMargin != composerHeight) {
                frame.bottomMargin = composerHeight;
                attachStrip.setLayoutParams(frame);
            }
        }
    }

    /** Shows the strip only while the field has something in it; see [createAttachStrip]. */
    private void updateAttachStrip() {
        if (attachStrip == null || composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null && composer.getEditField().length() > 0;
        int wanted = hasText && topicSearch == null ? View.VISIBLE : View.GONE;
        if (attachStrip.getVisibility() != wanted) {
            attachStrip.setVisibility(wanted);
            // The strip is an overlay on the list, so the list keeps its last post clear of it too
            // - and keeps the floating bar's own padding above it.
            applyListPadding();
            placeAttachStrip();
        }
    }

    /**
     * What the list keeps above its first post: the floating bar's own height (the status bar is
     * already in it, since the bar occupies the status bar the way a chat header does) plus the
     * 4dp every one of upstream's lists keeps (ChatActivity's own list padding starts with it,
     * 11947).
     */
    private void updateListPadding() {
        if (listView == null) {
            return;
        }
        listPaddingTop = AndroidUtilities.dp(4) + actionBarHeight();
        applyListPadding();
        // The pill rests just under the edge its list starts at, so a padding change moves it too.
        updateFloatingDate();
        checkUi_topFade();
        checkUi_BlurHeight();
    }

    /** The bar's measured height; before the first layout its resting one, as upstream reads it. */
    private int actionBarHeight() {
        return actionBar != null && actionBar.getMeasuredHeight() > 0
                ? actionBar.getMeasuredHeight()
                : org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
    }

    /**
     * ChatActivity.checkUi_topFade (46433-46450): the top zone the fading band covers. Upstream
     * adds the bar's measured height, a dp(7-6) for its bottom edge, the pinned panel and the
     * search/tag strips; this screen's header is only the bar, so the bar is the whole zone.
     */
    private void checkUi_topFade() {
        if (fadeView == null) {
            return;
        }
        float fadeHeight = actionBarHeight();
        fadeHeight += AndroidUtilities.dp(7 - 6);
        fadeView.setFadeZoneTop((int) fadeHeight);
    }

    /**
     * ChatActivity.checkUi_BlurHeight (46406-46411): the bottom zone, the composer plus the dp(7)
     * above its top edge and the dp(9) below it. Upstream adds the animation's bottom inset
     * because its content view is full screen; here the frame ends where the composer does (the
     * window resizes for the keyboard, and the composer carries the navigation bar's own space),
     * so the composer's measured height is that whole bottom band.
     */
    private void checkUi_BlurHeight() {
        if (fadeView == null) {
            return;
        }
        if (topicSearch != null) {
            fadeView.setFadeZoneBottom(listView.getPaddingBottom());
            return;
        }
        // The zone covers the whole input, capsule included: the enter view's own measured height
        // (it grows with a second line or the emoji panel) rather than a fixed strip, so the band
        // always sits under whatever the input became.
        int composerHeight;
        if (composer != null && composer.getMeasuredHeight() > 0) {
            composerHeight = composer.getMeasuredHeight();
        } else if (inputViews != null && inputViews.getInputBubbleHeight() > 0) {
            composerHeight = Math.round(inputViews.getInputBubbleHeight());
        } else {
            composerHeight = AndroidUtilities.dp(44) + bottomInset;
        }
        fadeView.setFadeZoneBottom(composerHeight + AndroidUtilities.dp(16)
                + (inputInsets == null ? 0 : Math.round(inputInsets.getAnimatedMaxBottomInset())));
    }

    /**
     * The band a screen-bottom hint sits above: the composer's own measured height plus the
     * floating island's bottom offset and the keyboard, the row {@link #applyListPadding} clears for
     * the list. ChatActivity's Bulletin delegate returns the same band (ChatActivity.java:29789).
     */
    private int bottomInputHeight() {
        int composerHeight;
        if (composer != null && composer.getMeasuredHeight() > 0) {
            composerHeight = composer.getMeasuredHeight();
        } else if (inputViews != null && inputViews.getInputBubbleHeight() > 0) {
            composerHeight = Math.round(inputViews.getInputBubbleHeight());
        } else {
            composerHeight = AndroidUtilities.dp(44) + bottomInset;
        }
        return composerHeight
                + AndroidUtilities.dp(org.telegram.ui.Components.chat.ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM)
                + (inputInsets == null ? 0 : Math.round(inputInsets.getAnimatedMaxBottomInset()))
                + AndroidUtilities.dp(7);
    }

    /** Writes the list's paddings from what the bar and the composer need, together. */
    private void applyListPadding() {
        if (listView == null) {
            return;
        }
        if (topicSearch != null) {
            int bottom = inputInsets == null ? bottomInset
                    : Math.max(bottomInset, Math.round(inputInsets.getAnimatedMaxBottomInset()));
            listView.setPadding(0, listPaddingTop, 0, AndroidUtilities.dp(64) + bottom);
            topicSearch.updateInsets(listPaddingTop, bottom);
            return;
        }
        // The list keeps the real input clear, not a guessed strip: the enter view's own measured
        // height (it grows with a second line, the emoji panel, the keyboard) plus the floating
        // island's bottom offset, so the last post scrolls just above the capsule.
        int composerHeight;
        if (composer != null && composer.getMeasuredHeight() > 0) {
            composerHeight = composer.getMeasuredHeight();
        } else if (inputViews != null && inputViews.getInputBubbleHeight() > 0) {
            composerHeight = Math.round(inputViews.getInputBubbleHeight());
        } else {
            composerHeight = AndroidUtilities.dp(44) + bottomInset;
        }
        int bottom = composerHeight
                + AndroidUtilities.dp(org.telegram.ui.Components.chat.ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM)
                + (inputInsets == null ? 0 : Math.round(inputInsets.getAnimatedMaxBottomInset()));
        if (attachStrip != null && attachStrip.getVisibility() == View.VISIBLE) {
            bottom += AndroidUtilities.dp(STRIP_HEIGHT_DP);
        }
        // The page-down button and the empty note float over the composer's own band; neither takes
        // a strip in the list's padding, so no full-width row is reserved for the affordance.
        listView.setPadding(0, listPaddingTop, 0, bottom);
    }

    /**
     * The system insets are where the status bar is; the bar's measured height follows them
     * (ActionBar.onMeasure adds {@code AndroidUtilities.statusBarHeight}, ActionBar.java:1400), so
     * the list's padding is rebuilt here - ChatActivity recomputes its own the same way when its
     * insets change.
     */
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public androidx.core.view.WindowInsetsCompat onInsetsInternal(@NonNull View view,
            @NonNull androidx.core.view.WindowInsetsCompat insets) {
        androidx.core.graphics.Insets bars = AndroidUtilities.getDefaultWindowInsets(insets, false);
        onInsets(bars.left, bars.top, bars.right, bars.bottom);
        if (inputInsets != null) {
            inputInsets.setInsets(insets);
        }
        return androidx.core.view.WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        bottomInset = bottom;
        // The floating composer reads the bar inset through WindowInsetsStateHolder; the same call upstream
        // makes when the window's insets change (ChatActivity:9024, 9049).
        if (inputViews != null) {
            inputViews.checkInsets();
        }
        updateListPadding();
    }

    /** A pick that is being uploaded; the strip says so and takes no second tap. */
    private void setUploadingAttachment(boolean uploading) {
        uploadingAttachment = uploading;
        if (attachStrip == null || attachStripLabel == null) {
            return;
        }
        attachStrip.setEnabled(!uploading);
        attachStrip.setAlpha(uploading ? 0.5f : 1f);
        attachStripLabel.setText(uploading ? "上传中…" : "添加图片或附件");
    }

    /**
     * ChatActivity.showProgressView (11866-11904): the first-load ring arrives by alpha and scale
     * - 0.3 to 1 over 150 ms - and leaves the same way; without animations it is simply shown or
     * hidden. Upstream shows it for {@code loading && messages.isEmpty()}, which is the same
     * condition here: a reload of a topic that already has posts shows nothing.
     */
    private void showProgressView(boolean show) {
        if (progressView == null) {
            return;
        }
        if (fragmentView != null && org.telegram.messenger.SharedConfig.animationsEnabled()) {
            if (show == animateProgressViewTo) {
                return;
            }
            animateProgressViewTo = show;
            if (show) {
                if (progressView.getVisibility() != View.VISIBLE) {
                    progressView.setVisibility(View.VISIBLE);
                    progressView.setAlpha(0f);
                    progressView.setScaleX(0.3f);
                    progressView.setScaleY(0.3f);
                }
                progressView.animate().setListener(null).cancel();
                progressView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            } else {
                progressView.animate().setListener(null).cancel();
                progressView.animate().alpha(0).scaleX(0.3f).scaleY(0.3f).setDuration(150)
                        .setListener(new android.animation.AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(android.animation.Animator animation) {
                                progressView.setVisibility(View.INVISIBLE);
                            }
                        }).start();
            }
        } else {
            animateProgressViewTo = show;
            progressView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Drops every read that belonged to the window on screen without starting another. The search
     * owns the list in that interval, and leaving the screen ends the window, so nothing from it
     * may land afterwards. A read that replaces the window uses [TopicReadingWindow.beginWindow].
     */
    private void supersedeWindow() {
        reading.supersedeWindow();
    }

    private void loadTopic(long topicId) {
        this.topicId = topicId;
        // A read begins: whatever was still in flight for the window that was here belongs to it, and
        // this read carries the generation that says the answers now on their way are not its.
        final int generation = reading.beginWindow();
        // A page-down jump that was still on its way for the window this read replaces is dropped
        // with it; the button must not be left on its spinner.
        pageDownJumping = false;
        // The first page is on its way: a chat shows its ring while it has no messages, and this
        // is the same moment (ChatActivity:9197).
        showProgressView(bubbles.isEmpty());
        container = NativeDo.container(getParentActivity());
        if (composerTools == null) {
            composerTools = new DiscourseComposerTools(
                    this, container, composer, inputViews, inputInsets, glassHost, topicId,
                    composerDelegate);
        }
        // The row that opened this screen already knew who wrote the topic: the capsule shows that
        // author, and the picture the row drew, before anything is read.
        bindTopicAuthorFromArguments();
        if (getArguments() != null && getArguments().containsKey(ARG_REPLY_COUNT)) {
            io.github.baba983.nativedo.core.model.CategorySummary category = container.getCategoryStore()
                    .getCategories().getValue().get(getArguments().getInt(ARG_CATEGORY_ID));
            setCapsuleSubtitle(category == null ? null : category.getName(),
                    getArguments().getInt(ARG_REPLY_COUNT));
        }
        reporter = DiscourseLoader.INSTANCE.readReporter(container, topicId);
        int floor = getArguments() == null ? 0 : getArguments().getInt(ARG_FLOOR, 0);
        if (floor > 0) {
            // An explicit floor - a feed row, a search hit - is where the reader was sent.
            loadWindow(floor, floor);
            return;
        }
        // Otherwise where the reader was is in the site's own answer to a plain read of the topic,
        // so the head is read once to learn it and the window they were at is loaded from there.
        // Opening a topic at its first floor would show a reader half-way through a thread the part
        // they have already read.
        retryAction = () -> loadTopic(topicId);
        final String authorFilter = reading.isOnlyOp() ? opUsername() : null;
        DiscourseLoader.INSTANCE.topic(container, topicId, null, authorFilter, detail -> {
            if (!reading.isCurrent(generation)) {
                // The reader left this read behind - another floor, another window, the screen gone -
                // before the head arrived; the answer names a window nobody is looking at.
                return kotlin.Unit.INSTANCE;
            }
            noteServedFromCache(detail.getFromCache());
            int position = reporter.start(detail);
            // The head answer's header is bound even when its posts are not: a resumed read loads
            // a window next, and an answer that leaves details out must not leave the capsule
            // without the author an earlier answer named.
            bindHeader(detail);
            if (position > 1 && !carriesFloor(detail, position)) {
                loadWindow(position, position);
                return kotlin.Unit.INSTANCE;
            }
            show(detail, position > 1 ? position : 0);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            showTopicFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The window of posts around [postNumber], with the list left on [scrollTo] when it is given. */
    private void loadWindow(int postNumber, int scrollTo) {
        // A window read replaces whatever window was here: the answer on its way belongs to the one
        // this call is opening, not to the one it replaced.
        final int generation = reading.beginWindow();
        // A page-down jump that was still on its way is dropped with the window it belonged to.
        pageDownJumping = false;
        retryAction = () -> loadWindow(postNumber, scrollTo);
        final String authorFilter = reading.isOnlyOp() ? opUsername() : null;
        DiscourseLoader.INSTANCE.topic(container, topicId, postNumber, authorFilter, detail -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            noteServedFromCache(detail.getFromCache());
            show(detail, scrollTo);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            showTopicFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    // ---- the discussion page (thread mode) ----

    /** The parent floor's real identity, read from the arguments the factory wrote. */
    private void readThreadArguments() {
        threadMode = true;
        if (getArguments() == null) {
            return;
        }
        threadParentPostId = getArguments().getLong(ARG_THREAD_PARENT_POST_ID, 0);
        threadParentFloor = getArguments().getInt(ARG_THREAD_PARENT_FLOOR, 0);
        threadReplyCount = getArguments().getInt(ARG_THREAD_REPLY_COUNT, 0);
    }

    /**
     * Opens the discussion page: the one post whose replies these are, then its direct replies read
     * a page at a time. The caller's own post draws at once; a page the system rebuilt (no seed)
     * resolves the parent from the site by the real floor it was opened for.
     *
     * <p>No read-position reporter is created: a thread is a handful of a topic's floors, and
     * crediting them as read - or advancing the topic's cursor past floors never shown - would be a
     * lie about a topic the reader did not read.</p>
     */
    private void loadThread() {
        threadMode = true;
        this.topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        // A read begins: whatever was still in flight for the page that was here before belongs to
        // that page, and this read carries the generation that says so.
        threadGeneration++;
        showProgressView(threadSeedRoot == null && bubbles.isEmpty());
        container = NativeDo.container(getParentActivity());
        if (composerTools == null) {
            composerTools = new DiscourseComposerTools(
                    this, container, composer, inputViews, inputInsets, glassHost, topicId,
                    composerDelegate);
        }
        retryAction = this::loadThread;
        if (threadSeedRoot != null) {
            applyThreadSeed(threadSeedRoot, threadSeedDetail);
            return;
        }
        // A rebuilt page: the seed is gone, so the parent is read from the site by its own floor -
        // the id alone would not name a floor, and the endpoint's replies are named by floor.
        final int generation = threadGeneration;
        DiscourseLoader.INSTANCE.topic(container, topicId,
                threadParentFloor <= 0 ? null : threadParentFloor, detail -> {
                    if (generation != threadGeneration) {
                        // The page was destroyed or reloaded while the parent was on its way; the
                        // answer belongs to a page nobody is looking at.
                        return kotlin.Unit.INSTANCE;
                    }
                    noteServedFromCache(detail.getFromCache());
                    Post root = findThreadParent(detail);
                    if (root == null) {
                        showTopicFailure(new java.io.IOException("原帖没有找到"));
                        return kotlin.Unit.INSTANCE;
                    }
                    applyThreadSeed(root, detail);
                    return kotlin.Unit.INSTANCE;
                }, error -> {
                    if (generation != threadGeneration) {
                        return kotlin.Unit.INSTANCE;
                    }
                    showTopicFailure(error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** The parent post in a window the site answered: by its real id first, then by its floor. */
    private Post findThreadParent(TopicDetail detail) {
        if (detail == null) {
            return null;
        }
        for (Post post : detail.getPosts()) {
            if (post.getId() == threadParentPostId) {
                return post;
            }
        }
        for (Post post : detail.getPosts()) {
            Integer number = post.getPostNumber();
            if (number != null && number == threadParentFloor) {
                return post;
            }
        }
        return null;
    }

    /** Binds the root, then asks the site for the first page of its direct replies. */
    private void applyThreadSeed(Post root, TopicDetail detail) {
        showProgressView(false);
        if (failureView != null) {
            failureView.setVisibility(View.GONE);
        }
        if (detail != null) {
            topicDetail = detail;
        }
        // The site's own count of this root's replies, from the root the site answered with: never a
        // fabricated count, and never the page's launch-time copy once the site has a newer one.
        if (root != null) {
            threadReplyCount = Math.max(0, root.getReplyCount());
        }
        applyThreadHeader(root);
        threadGeneration++;
        // A new bind is accepted: the read it invalidated is over, so the page is not still loading
        // and may ask for its own first page.
        threadLoading = false;
        bindPosts(Collections.singletonList(root));
        threadLoadedIds.clear();
        threadLoadedIds.add(root.getId());
        threadAfter = 1;
        threadExhausted = false;
        threadFailed = false;
        updateThreadStatus();
        startThreadReplies();
    }

    /**
     * The page's header: the topic's own title and the floor whose replies these are. The capsule's
     * author is the root's author, so the header says who the thread is about, and the subtitle
     * names the floor being discussed together with the site's own count of its replies.
     */
    private void applyThreadHeader(Post root) {
        if (avatarContainer == null) {
            return;
        }
        String title = topicDetail != null && topicDetail.getTitle() != null
                && !topicDetail.getTitle().trim().isEmpty()
                ? topicDetail.getTitle().trim()
                : (getArguments() == null ? null : getArguments().getString(ARG_TOPIC_TITLE));
        if (title != null && !title.trim().isEmpty()) {
            setCapsuleTitle(title.trim());
        }
        if (root != null && container != null) {
            bindTopicAuthorUser(
                    DiscourseAvatars.INSTANCE.user(
                            root.getAuthorUsername(),
                            root.getAuthorDisplayName() == null ? "" : root.getAuthorDisplayName(),
                            root.getAuthorAvatarTemplate() == null ? "" : root.getAuthorAvatarTemplate(),
                            container.getBaseUrl(),
                            root.getAuthorAnimatedAvatarUrl()),
                    root.getAuthorAvatarTemplate());
        }
        int floor = root != null && root.getPostNumber() != null ? root.getPostNumber() : threadParentFloor;
        String subtitle = floor > 0 ? ("回复 #" + floor) : null;
        if (subtitle != null) {
            // The floor it is about, and the site's own count of that floor's replies - the way the
            // topic's capsule carries its reply count beside its category (setCapsuleSubtitle).
            subtitle += " · " + Math.max(0, threadReplyCount) + " 回复";
        }
        setCapsuleSubtitle(subtitle);
    }

    /** The first page of replies, once the root is drawn. */
    private void startThreadReplies() {
        if (threadReplyCount <= 0) {
            // The site says the floor has no replies: there is nothing to read, and the thread is
            // its root alone.
            threadExhausted = true;
            updateThreadStatus();
            return;
        }
        loadThreadPage();
    }

    /** Reads the next page, from the cursor the last one advanced to. */
    private void loadThreadPage() {
        if (threadLoading || threadExhausted || threadFailed || container == null) {
            return;
        }
        threadLoading = true;
        updateThreadStatus();
        final int generation = threadGeneration;
        final int cursor = threadAfter;
        DiscourseLoader.INSTANCE.postReplies(container, threadParentPostId, cursor, page -> {
            if (generation != threadGeneration) {
                // The page was destroyed or reloaded while this was in flight; the rows belong to a
                // thread nobody is looking at.
                return kotlin.Unit.INSTANCE;
            }
            onThreadReplies(page);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (generation != threadGeneration) {
                return kotlin.Unit.INSTANCE;
            }
            onThreadPageFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * A page of replies. Only the replies to this parent are kept (an answer whose
     * {@code reply_to_post_number} names another floor is not this floor's reply), a row already
     * held is dropped (the site overlaps its pages), and the cursor advances to the greatest valid
     * floor read. A page that adds nothing new, or whose new rows carry no usable floor and so
     * cannot move the cursor, ends the listing however many the count still promises.
     */
    private void onThreadReplies(List<Post> page) {
        threadLoading = false;
        int added = 0;
        int cursorBefore = threadAfter;
        int greatest = threadAfter;
        int firstNew = bubbles.size();
        if (page != null) {
            for (Post post : page) {
                if (post == null) {
                    continue;
                }
                Integer parent = post.getReplyToPostNumber();
                if (parent != null && parent != threadParentFloor) {
                    // An answer to somebody else: the endpoint answered with a window that is not
                    // this parent's own replies, and this is not a row of this thread.
                    continue;
                }
                if (!threadLoadedIds.add(post.getId())) {
                    continue;
                }
                Integer floor = post.getPostNumber();
                if (floor != null && floor > 0) {
                    postByNumber.put(floor, post);
                    if (floor > greatest) {
                        greatest = floor;
                    }
                }
                add(post, topicId);
                added++;
            }
        }
        if (added > 0) {
            threadAfter = greatest;
            resolveMissingReplies();
            registerUsersInController();
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemRangeInserted(firstNew, bubbles.size() - firstNew);
            }
            for (int index = firstNew; index < bubbles.size(); index++) {
                fetchPostMedia(index);
            }
            fetchAvatars();
        }
        // The listing ends when the page added nothing new, and also when the rows it added carry no
        // usable floor: the cursor did not move, so asking it again would only re-read ids already
        // held. The rows themselves stay - they are legitimately readable.
        if (added == 0 || greatest <= cursorBefore) {
            threadExhausted = true;
        }
        updateThreadStatus();
    }

    /**
     * A page that did not arrive: what is already read stays, the failure is said, and the reader's
     * own line carries the retry. Nothing retries on its own - a page that failed every tick would
     * hammer the site and never tell the reader.
     */
    private void onThreadPageFailure(Throwable error) {
        threadLoading = false;
        threadFailed = true;
        lastFailure = TopicLoadFailure.of(error);
        org.telegram.messenger.FileLog.e("thread replies failed", error);
        if (getParentActivity() != null) {
            try {
                // The same copy the page's own line shows: this page retries on a tap, so it must
                // not promise a scroll retry the way the topic paging copy does.
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createErrorBulletin(THREAD_RETRY_LINE).show();
            } catch (Exception bulletinError) {
                org.telegram.messenger.FileLog.e(bulletinError);
            }
        }
        updateThreadStatus();
    }

    /**
     * The reader's affordance: upstream's stock floating page-down button. It is shown while a page
     * is left to read, a page is in flight, or a page failed; its stock spinner is the read in
     * flight, its stock badge is the site's own remaining count of the root's direct replies, and
     * its description names more, retry or loading. The empty note is the stock service pill, shown
     * only while the root is the whole thread.
     */
    private void updateThreadStatus() {
        if (topicSearch != null) {
            return;
        }
        if (!threadMode) {
            // An ordinary read floats the same button over the same composer; its state is the
            // stream below the viewport, not a thread's cursor.
            updateTopicPageDown();
            if (threadEmptyCell != null) {
                threadEmptyCell.setVisibility(View.GONE);
            }
            threadEmptyText = null;
            return;
        }
        // What is left to read, from the site's own count minus what is already drawn - never a
        // fabricated number.
        int remaining = Math.max(0, threadReplyCount - Math.max(0, floors.size() - 1));
        boolean showButton = threadFailed || threadLoading || threadHasMore();
        if (threadPageDown != null) {
            // The stock spinner is the pending read, and a read in flight also refuses another tap,
            // so a double tap cannot make the site read the same page twice.
            threadPageDown.showLoading(threadLoading, true);
            threadPageDown.setEnabled(!threadLoading, true);
            threadPageDown.setCount(threadFailed ? 0 : remaining, true);
            threadPageDown.setContentDescription(threadAffordanceDescription(remaining));
            // A discussion page toggles instantly; its tests drive the real button on that state.
            applyPageDownVisible(showButton, false);
        }
        // The empty note: only the root is drawn and no page is left or coming, so say it with the
        // stock service pill - and never claim there are no replies when the site's own count says
        // there are, only that none are visible.
        boolean showEmpty = !showButton && floors.size() <= 1;
        threadEmptyText = showEmpty
                ? (threadReplyCount > 0 ? THREAD_EMPTY_UNSEEN_LINE : THREAD_EMPTY_LINE)
                : null;
        if (threadEmptyCell != null) {
            if (threadEmptyText != null) {
                threadEmptyCell.setCustomText(threadEmptyText);
            }
            threadEmptyCell.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }
        applyListPadding();
        placeThreadStatus();
        checkUi_BlurHeight();
    }

    /** The page-down button's description: what a tap does, by the state the page is in. */
    private CharSequence threadAffordanceDescription(int remaining) {
        if (threadFailed) {
            // A fixed line, not the transport's own words: what the reader needs is to know a page
            // did not arrive and that this control retries it.
            return THREAD_RETRY_LINE;
        }
        if (threadLoading) {
            return "正在加载更多回复";
        }
        return remaining > 0
                ? ("还有 " + remaining + " 条回复，点按加载更多")
                : "点按加载更多回复";
    }

    /**
     * The button's tap, whichever read it belongs to: an ordinary read jumps to what is still
     * below, a discussion page reads on or retries its own next page.
     */
    private void onPageDownTap() {
        if (threadMode) {
            onThreadStatusTap();
        } else {
            onTopicPageDownTap();
        }
    }

    /**
     * An ordinary read's page-down state: unread stream posts after the monotonic read mark, and
     * an arrow whenever real posts remain below the viewport, even if they are already read.
     *
     * <p>The number is made of the stream the site sent - {@code stream_post_ids} - so a topic with
     * deleted floors counts only the posts that really exist. A floor outside the loaded window
     * may have no known stream id; without that boundary, keep the arrow but omit the number rather
     * than count already-read posts or invent a floor-to-id mapping.</p>
     */
    private void updateTopicPageDown() {
        if (topicSearch != null || threadMode || threadPageDown == null) {
            return;
        }
        int count = 0;
        boolean show = false;
        int streamIndex = lastVisibleStreamIndex();
        if (streamIndex >= 0) {
            show = streamIndex < streamPostIds.size() - 1;
        }
        if (reading.isOnlyOp()) {
            // The filtered stream is the poster's floors: the number is how many of those are still
            // below the viewport. Nothing here claims "unread" - this screen reports no read
            // position while the filter is on, so no cursor moves as the reader scrolls.
            count = show ? streamPostIds.size() - streamIndex - 1 : 0;
        } else if (reporter != null) {
            int reached = reporter.getReachedFloorMark();
            Post readPost = postByNumber.get(reached);
            Long readId = readPost == null ? null : readPost.getId();
            if (readId == null && topicDetail != null && reached == topicDetail.getLastReadPostNumber()) {
                readId = topicDetail.getLastReadPostId();
            }
            int readIndex = readId == null ? -1 : streamPostIds.indexOf(readId);
            if (reached == 0 || readIndex >= 0) {
                count = streamPostIds.size() - readIndex - 1;
            }
        }
        // The spinner is the jump in flight and a jump in flight also refuses another tap, so a
        // double tap cannot ask the site for the same window twice.
        threadPageDown.showLoading(pageDownJumping, true);
        threadPageDown.setEnabled(!pageDownJumping, true);
        // ChatActivity animates unread count changes through the stock CounterView.
        threadPageDown.setCount(show ? count : 0, true);
        threadPageDown.setContentDescription(
                reading.isOnlyOp() ? onlyOpPageDownDescription(count) : topicPageDownDescription(count));
        applyPageDownVisible(show, true);
    }

    /** The stream index of the floor at the bottom of the viewport, or -1. */
    private int lastVisibleStreamIndex() {
        int last = lastVisibleBubbleRow();
        if (last < 0) {
            return -1;
        }
        int floorIndex = bubbleFloors.get(last);
        if (floorIndex < 0 || floorIndex >= floors.size()) {
            return -1;
        }
        return streamPostIds.indexOf(floors.get(floorIndex).getId());
    }

    /** The ordinary button's description: what a tap does, as the accessibility contract. */
    private CharSequence topicPageDownDescription(int remaining) {
        if (remaining > 0) {
            return "还有 " + remaining + " 条未读帖子，点按查看";
        }
        return reporter != null && reporter.getReachedFloorMark() < reporter.getHighestFloorMark()
                ? "查看未读帖子" : "回到最新帖子";
    }

    /** The filtered button's description: the poster's floors, never an unread claim. */
    private CharSequence onlyOpPageDownDescription(int remaining) {
        return remaining > 0
                ? "还有 " + remaining + " 条楼主的帖子，点按查看"
                : "点按查看楼主的帖子";
    }

    /**
     * The reader's tap on an ordinary read's page-down button: to the first unread below the
     * viewport, or to the last message. A target already loaded is a plain scroll; one the window
     * does not carry is read as a window and then landed on.
     */
    private void onTopicPageDownTap() {
        if (pageDownJumping || listView == null || listView.getLayoutManager() == null) {
            return;
        }
        int target = topicPageDownTargetFloor();
        if (target <= 0) {
            return;
        }
        int index = rowIndexOrAfter(target);
        if (index >= 0) {
            // Already loaded: only the list moves. No read is made and no spinner is shown, and the
            // reporter advances only when the jump really puts the floor in the viewport.
            listView.smoothScrollToPosition(index);
            return;
        }
        pageDownJumping = true;
        updateTopicPageDown();
        loadPageDownWindow(target);
    }

    /**
     * Where an ordinary read's page-down tap goes: the first unread after the reporter's monotonic
     * reading high-water mark when that floor sits below the viewport (so the reader reads on in
     * order), otherwise the topic's last floor. When everything is read there is no unread start to
     * prefer and the last floor is the target - the arrow a reader scrolling back through history
     * taps goes to the newest post.
     */
    private int topicPageDownTargetFloor() {
        if (reading.isOnlyOp()) {
            // The filter tracks no read cursor: a tap moves to the next floor after the viewport,
            // and the filtered window read lands on the first poster's floor at or after it.
            Integer last = lastVisibleFloorNumber();
            return last == null ? 0 : last + 1;
        }
        if (reporter == null) {
            return 0;
        }
        int highest = reporter.getHighestFloorMark();
        if (highest <= 0) {
            return 0;
        }
        int unreadStart = reporter.getReachedFloorMark() + 1;
        if (unreadStart <= highest) {
            Integer lastVisible = lastVisibleFloorNumber();
            if (lastVisible != null && unreadStart > lastVisible) {
                return unreadStart;
            }
        }
        return highest;
    }

    /**
     * The last row of the viewport that draws a floor's bubble, or -1. A day's date row and the
     * unread divider stand above a floor rather than in it, so the floor they name is not on screen
     * yet and the row below the viewport is not counted as seen.
     */
    private int lastVisibleBubbleRow() {
        LinearLayoutManager manager = listView == null
                ? null : (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null) {
            return -1;
        }
        int last = Math.min(manager.findLastVisibleItemPosition(), bubbles.size() - 1);
        for (int position = last; position >= 0; position--) {
            if (!isDividerRow(position)) {
                return position;
            }
        }
        return -1;
    }

    /** The floor number of the list's last visible row, or null when there is none. */
    private Integer lastVisibleFloorNumber() {
        int last = lastVisibleBubbleRow();
        if (last < 0) {
            return null;
        }
        int floorIndex = bubbleFloors.get(last);
        if (floorIndex < 0 || floorIndex >= floors.size()) {
            return null;
        }
        return floors.get(floorIndex).getPostNumber();
    }

    /**
     * The first row whose floor's number is [floor], or the first row after it; -1 when the window
     * carries nothing at or after [floor]. A floor is drawn as one or more rows (a poll splits one),
     * so the row's own index - what the list is told to scroll to - is not the floor's index; a
     * deleted floor is therefore skipped rather than missed, and the reader lands on the next row
     * the site really has.
     */
    private int rowIndexOrAfter(int floor) {
        for (int index = 0; index < bubbleFloors.size(); index++) {
            // A day's date row stands above the floor rather than in it, and a jump lands on the
            // floor itself, not on the pill above it.
            if (isDividerRow(index)) {
                continue;
            }
            Integer number = floors.get(bubbleFloors.get(index)).getPostNumber();
            if (number == null) {
                continue;
            }
            if (number >= floor) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Reads the window around [floor] for an ordinary read's jump, then lands the list on the first
     * loaded floor at or after it. The read belongs to the window generation it was asked for: an
     * answer for a window the reader has already replaced draws nothing. A failure keeps the rows
     * and the reader's place, says so in passing, and leaves the button enabled to try again.
     */
    private void loadPageDownWindow(int floor) {
        final int generation = reading.beginWindow();
        final long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        retryAction = () -> loadPageDownWindow(floor);
        final String authorFilter = reading.isOnlyOp() ? opUsername() : null;
        DiscourseLoader.INSTANCE.topic(container, topicId, floor, authorFilter, detail -> {
            if (!reading.isCurrent(generation)) {
                // The window moved on while this read was on its way; the rows belong to a window
                // nobody is looking at.
                return kotlin.Unit.INSTANCE;
            }
            noteServedFromCache(detail.getFromCache());
            pageDownJumping = false;
            show(detail, floor);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            pageDownJumping = false;
            updateTopicPageDown();
            showPageDownFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Shows or hides the button through its own factor; a discussion page toggles it instantly. */
    private void applyPageDownVisible(boolean show, boolean animated) {
        if (threadPageDown == null) {
            return;
        }
        pageDownVisible.setValue(show, animated);
    }

    /**
     * Draws the button at its current visibility factor: upstream's
     * {@code ChatActivitySideControlsButtonsLayout.checkButtonsPositionsAndVisibility} for a single
     * button - alpha and scale from 0.7, an 80dp slide, and the frame's visibility from the factor -
     * over the resting bottom offset that keeps it above the composer.
     */
    private void applyPageDownTransform() {
        if (threadPageDown == null) {
            return;
        }
        float factor = pageDownVisible.getFloatValue();
        if (composer != null && (composer.isRecordingAudioVideo() || composer.hasAudioToSend())) {
            factor = 0f;
        }
        threadPageDown.setVisibility(factor > 0f ? View.VISIBLE : View.GONE);
        threadPageDown.setAlpha(factor);
        threadPageDown.setScaleX(org.telegram.messenger.AndroidUtilities.lerp(0.7f, 1f, factor));
        threadPageDown.setScaleY(org.telegram.messenger.AndroidUtilities.lerp(0.7f, 1f, factor));
        threadPageDown.setTranslationY(-pageDownBottom + AndroidUtilities.dp(80) * (1f - factor));
    }

    /**
     * The way back up is shown once the first post has scrolled under the bar - the list can still
     * scroll up and more than a bar's height is above the viewport - so the button does not flicker
     * on the first pixel of a scroll. A discussion page and the search overlay own their own
     * affordances, so neither shows it.
     */
    private void updateTopicPageUp() {
        if (threadPageUp == null) {
            return;
        }
        if (topicSearch != null || threadMode || listView == null) {
            applyPageUpVisible(false, true);
            return;
        }
        boolean scrolledPastTheTop = listView.canScrollVertically(-1)
                && listView.computeVerticalScrollOffset() > AndroidUtilities.dp(48);
        applyPageUpVisible(scrolledPastTheTop, true);
    }

    /** Shows or hides the up button through its own factor. */
    private void applyPageUpVisible(boolean show, boolean animated) {
        if (threadPageUp == null) {
            return;
        }
        pageUpVisible.setValue(show, animated);
    }

    /** The page-down button's own show animation, one band higher. */
    private void applyPageUpTransform() {
        if (threadPageUp == null) {
            return;
        }
        float factor = pageUpVisible.getFloatValue();
        if (composer != null && (composer.isRecordingAudioVideo() || composer.hasAudioToSend())) {
            factor = 0f;
        }
        // The up button rides the page-down button's own factor: while the down arrow is on its way
        // out (the real end of the stream), the way back up slides into the slot it frees, so the
        // stack collapses instead of leaving a hole, and it rides back up when the down arrow
        // returns. One animator drives both, which is why a mid-flight frame is never a gap.
        float down = pageDownVisible.getFloatValue();
        int restingBottom = pageDownBottom
                + Math.round(AndroidUtilities.dp(PAGE_BUTTON_STEP_DP) * down);
        threadPageUp.setVisibility(factor > 0f ? View.VISIBLE : View.GONE);
        threadPageUp.setAlpha(factor);
        threadPageUp.setScaleX(org.telegram.messenger.AndroidUtilities.lerp(0.7f, 1f, factor));
        threadPageUp.setScaleY(org.telegram.messenger.AndroidUtilities.lerp(0.7f, 1f, factor));
        threadPageUp.setTranslationY(-restingBottom + AndroidUtilities.dp(80) * (1f - factor));
    }

    /** Redraws every visible row that draws a holographic tag. */
    private void invalidateAnimatedTags() {
        if (listView == null) {
            return;
        }
        for (int index = 0; index < listView.getChildCount(); index++) {
            android.view.View child = listView.getChildAt(index);
            if (!(child instanceof org.telegram.ui.Cells.ChatMessageCell)) {
                continue;
            }
            org.telegram.messenger.MessageObject message =
                    ((org.telegram.ui.Cells.ChatMessageCell) child).getMessageObject();
            if (message != null && message.messageOwner != null
                    && DiscourseUserTitles.INSTANCE.isAnimated(message.messageOwner.from_rank)) {
                child.invalidate();
            }
        }
    }

    /** Whether a visible row draws one of the site's holographic tags right now. */
    private boolean animatedTagVisible() {
        if (listView == null) {
            return false;
        }
        for (int index = 0; index < listView.getChildCount(); index++) {
            android.view.View child = listView.getChildAt(index);
            if (!(child instanceof org.telegram.ui.Cells.ChatMessageCell)) {
                continue;
            }
            org.telegram.messenger.MessageObject message =
                    ((org.telegram.ui.Cells.ChatMessageCell) child).getMessageObject();
            if (message != null && message.messageOwner != null
                    && DiscourseUserTitles.INSTANCE.isAnimated(message.messageOwner.from_rank)) {
                return true;
            }
        }
        return false;
    }

    /** Starts the title gradient's frame loop once a holographic tag is loaded, and only then. */
    private void startTitleGradient() {
        if (titleGradientTicking || !animatedTitlesLoaded || listView == null
                || getParentActivity() == null || isPaused()) {
            return;
        }
        titleGradientTicking = true;
        listView.postOnAnimation(titleGradientTick);
    }

    private void stopTitleGradient() {
        titleGradientTicking = false;
        if (listView != null) {
            listView.removeCallbacks(titleGradientTick);
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction,
            me.vkryl.android.animator.FactorAnimator callee) {
        if (id == ANIMATOR_ID_PAGE_DOWN_VISIBLE) {
            applyPageDownTransform();
            // The stacked button's resting offset is a function of this factor (see above), so it
            // has to be redrawn on every frame of the down arrow's show/hide. A hidden up button
            // has nothing to move; it reads the factor when it appears.
            if (pageUpVisible.getFloatValue() > 0f) {
                applyPageUpTransform();
            }
        } else if (id == ANIMATOR_ID_PAGE_UP_VISIBLE) {
            applyPageUpTransform();
        }
    }

    /** The reader's tap on the page's own control: read on while there is a page, retry one that failed. */
    private void onThreadStatusTap() {
        if (threadLoading) {
            // A read is already in flight; a second tap must not ask the site again.
            return;
        }
        if (!threadFailed) {
            loadThreadPage();
            return;
        }
        if (lastFailure == TopicLoadFailure.Challenge || lastFailure == TopicLoadFailure.LoginRequired) {
            // The way through is the verification page; once the session is usable the page is asked
            // for again from the same cursor.
            retryAction = this::retryThreadPage;
            recoverSessionThenRetry();
        } else {
            retryThreadPage();
        }
    }

    private void retryThreadPage() {
        if (threadLoading) {
            return;
        }
        threadFailed = false;
        updateThreadStatus();
        loadThreadPage();
    }

    /**
     * Reads the page again, after a reply this page sent was accepted or a floor's reactions
     * changed: the page's own replies are read from the first page at once, and the root's
     * authoritative count is refreshed from the site's floor endpoint and applied to the root's own
     * row when it lands.
     */
    private void reloadThread() {
        threadGeneration++;
        // The reload invalidated the read that was in flight; the page is not loading any more.
        threadLoading = false;
        Post root = floors.isEmpty() ? threadSeedRoot : floors.get(0);
        if (root == null) {
            loadThread();
            return;
        }
        applyThreadSeed(root, topicDetail);
        if (container == null) {
            return;
        }
        // The floor endpoint answers the root's own window: the accepted write changed the floor's
        // reply count on the site, and the page draws that number, not the launch-time copy it was
        // seeded with.
        final int generation = threadGeneration;
        DiscourseLoader.INSTANCE.topic(container, topicId,
                threadParentFloor <= 0 ? null : threadParentFloor, detail -> {
                    if (generation != threadGeneration) {
                        // Another reload or a leave overtook this one.
                        return kotlin.Unit.INSTANCE;
                    }
                    noteServedFromCache(detail.getFromCache());
                    Post refreshed = findThreadParent(detail);
                    if (refreshed != null) {
                        threadReplyCount = Math.max(0, refreshed.getReplyCount());
                        Integer number = refreshed.getPostNumber();
                        int at = number == null ? -1 : floorIndexForNumber(number);
                        if (at >= 0) {
                            floors.set(at, refreshed);
                            postByNumber.put(number, refreshed);
                            rebuildFloor(at);
                        }
                        applyThreadHeader(refreshed);
                        updateThreadStatus();
                    }
                    return kotlin.Unit.INSTANCE;
                }, error -> {
                    // The refresh did not arrive; the page already has its replies, and the root's
                    // count is left as it was rather than replaced with a guess.
                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * The band the affordance floats above: the real composer's measured top edge (the same edge
     * upstream floats its page-down button on, ChatActivity.updatePagedownButtonsPosition), plus,
     * before the first layout, the island's own height and gap as a stand-in, and the reply strip
     * when it is open.
     */
    private int threadAffordanceBottom() {
        View root = fragmentView;
        int stripGap = attachStrip != null && attachStrip.getVisibility() == View.VISIBLE
                ? AndroidUtilities.dp(STRIP_HEIGHT_DP + 4) : 0;
        if (root != null && composer != null && root.getHeight() > 0 && composer.getHeight() > 0) {
            // The measured top of the real composer, in the frame's own coordinates: that is what
            // "above the input" means, and what upstream floats its page-down button on
            // (ChatActivity.updatePagedownButtonsPosition). The window offsets cancel in the
            // difference, and getLocationInWindow accounts for the island's own translation.
            int[] rootAt = new int[2];
            int[] composerAt = new int[2];
            root.getLocationInWindow(rootAt);
            composer.getLocationInWindow(composerAt);
            int composerTop = composerAt[1] - rootAt[1];
            if (composerTop > 0) {
                return root.getHeight() - composerTop + stripGap;
            }
        }
        // Before the first layout: the island's own height and gap stand in for the measured top.
        int input;
        if (inputViews != null && inputViews.getInputBubbleHeight() > 0) {
            input = Math.round(inputViews.getInputBubbleHeight());
        } else if (composer != null && composer.getMeasuredHeight() > 0) {
            input = composer.getMeasuredHeight();
        } else {
            input = AndroidUtilities.dp(44) + bottomInset;
        }
        return input
                + AndroidUtilities.dp(org.telegram.ui.Components.chat.ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM)
                + (inputInsets == null ? 0 : Math.round(inputInsets.getAnimatedMaxBottomInset()))
                + stripGap;
    }

    /**
     * The page-down button floats above the real composer at the trailing edge (upstream's own
     * placement: a {@code Gravity.RIGHT | Gravity.BOTTOM} frame lifted by the input band), and the
     * empty note rides the same band, centred. Neither takes a row in the list.
     */
    private void placeThreadStatus() {
        int bottom = threadAffordanceBottom();
        pageDownBottom = bottom;
        // The button's own slide (its show animation) is measured from this resting offset, and the
        // up button's is one control plus its gap above it until the down arrow leaves; the factor
        // is re-applied whenever the band moves.
        applyPageDownTransform();
        applyPageUpTransform();
        if (threadEmptyCell != null) {
            ViewGroup.LayoutParams params = threadEmptyCell.getLayoutParams();
            if (params instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams frame =
                        (android.widget.FrameLayout.LayoutParams) params;
                if (frame.bottomMargin != bottom) {
                    frame.bottomMargin = bottom;
                    threadEmptyCell.setLayoutParams(frame);
                }
            }
        }
    }

    /** What has arrived: the topic's stream, its floors, and where in it the reader is standing. */
    private kotlin.Unit show(TopicDetail detail, int scrollTo) {
        // The answer is here and its rows are about to be bound in the mode the read was asked for.
        reading.windowShown();
        // The page is here: ChatActivity turns its ring off with the same call it turned it on
        // (9197 -> showProgressView(false)).
        showProgressView(false);
        if (failureView != null) {
            failureView.setVisibility(View.GONE);
        }
        topicDetail = detail;
        nextTopicNavigationStarted = false;
        bindNextTopicCandidate(detail.getSuggestedTopics(), detail.getId());
        topicBookmarked = detail.getBookmarked();
        updateBookmarkMenuItem();
        topicNotificationLevel = detail.getNotificationLevel();
        updateNotificationLevelMenuItem();
        // The site's own title is the authoritative one; a link that named no title gets it here,
        // and it goes into the center capsule the way a chat's title does.
        bindHeader(detail);
        if (reading.takePendingOnlyOpToggle()) {
            // The reader asked for the poster's floors before any answer named who that is. The
            // answer in hand is the one that can name them; the mode is then taken on a read of the
            // filtered stream rather than by hiding rows this window happens to hold.
            if (opUsername() == null) {
                showPassingNote("站点没有给出楼主的名字");
            } else {
                reading.beginOnlyOpSwitch(true);
                updateOnlyOpMenuItem();
                bindSubtitle(detail);
                loadTopic(topicId);
                return kotlin.Unit.INSTANCE;
            }
        }
        streamPostIds = detail.getStreamPostIds();
        if (reporter != null) {
            reporter.start(detail);
        }
        bind(detail);
        if (scrollTo > 0) {
            scrollToFloor(scrollTo);
        }
        // The rows were bound inside a layout pass, so a message queued now runs once that pass has
        // placed them: the viewport is only known after it, and a topic whose floors all fit has no
        // scroll to report them from.
        listView.post(this::reportViewport);
        return kotlin.Unit.INSTANCE;
    }

    /**
     * Takes the site's own suggestion for what to read next: the first entry of this topic read's
     * `suggested_topics` that is a real topic other than this one, kept in the order the site
     * ranked it. The initial window or final incremental page can supply it; binding the candidate
     * never resets the floors, header or scroll, or re-arms a release already navigating.
     */
    private void bindNextTopicCandidate(
            List<io.github.baba983.nativedo.core.model.TopicSummary> suggested, long topicId) {
        if (nextTopicNavigationStarted) {
            return;
        }
        nextTopicCandidate = null;
        for (io.github.baba983.nativedo.core.model.TopicSummary topic : suggested) {
            if (topic == null || topic.getId() <= 0 || topic.getId() == topicId) {
                continue;
            }
            String title = topic.getTitle();
            if (title == null || title.trim().isEmpty()) {
                continue;
            }
            nextTopicCandidate = topic;
            break;
        }
        if (listView != null) {
            listView.pullDrawable().setTarget(nextTopicCandidate == null ? null : nextTopicCandidate.getTitle());
            listView.invalidate();
        }
    }

    /**
     * Whether the reader may pull the end of this list into the next topic right now. The screen
     * owns the answer because it owns the states: the in-topic search, a discussion page, offline
     * content, a page or window still loading or failed, a text selection, and a composer the
     * reader is writing in (a draft or an attachment) or with its keyboard up all keep the pull
     * away. A topic the site suggested nothing for keeps it away too.
     */
    private boolean canPullNextTopic() {
        if (nextTopicCandidate == null || listView == null) {
            return false;
        }
        if (threadMode || topicSearch != null || showingCachedContent || reading.isOnlyOp()) {
            return false;
        }
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            return false;
        }
        if (composer != null && composer.getEditField() != null
                && composer.getEditField().length() > 0) {
            return false;
        }
        if (uploadingAttachment || editFloor >= 0) {
            return false;
        }
        if (attachStrip != null && attachStrip.getVisibility() == View.VISIBLE) {
            return false;
        }
        if (composer != null && (composer.isKeyboardVisible() || composer.isWaitingForKeyboard())) {
            return false;
        }
        // The real end of the stream, with no page or window still coming and no edge latched: a
        // topic with more pages, one still loading, or one whose page failed must not navigate.
        if (reading.hasPageInFlight() || !reading.isAtStreamEnd(streamPostIds.size())
                || reading.isForwardPageFailed() || reading.isBackwardPageFailed()) {
            return false;
        }
        return true;
    }

    /**
     * The reader let go past the threshold: play the card's own check animation, then open the
     * candidate through the existing navigation, replacing this screen so a chain of topics does
     * not stack and Back still returns to the list the reader came from. A release already asked
     * for, a screen that outlived the request, or a candidate that is gone by the time the card
     * settles all decline instead of navigating.
     */
    private void openNextTopicFromPull() {
        if (nextTopicNavigationStarted) {
            return;
        }
        final io.github.baba983.nativedo.core.model.TopicSummary candidate = nextTopicCandidate;
        if (candidate == null || !canPullNextTopic()) {
            if (listView != null) {
                listView.cancelNextTopicPull();
            }
            return;
        }
        nextTopicNavigationStarted = true;
        final int generation = reading.getGeneration();
        Runnable navigate = () -> {
            // The screen left, or another window replaced this one, while the card settled: the
            // release belongs to a screen nobody is looking at.
            if (!reading.isCurrent(generation) || listView == null) {
                return;
            }
            openNextTopic(candidate);
        };
        if (listView != null && listView.pullDrawable().animationIsRunning()) {
            listView.pullDrawable().runOnAnimationFinish(navigate);
        } else {
            navigate.run();
        }
    }

    private void openNextTopic(io.github.baba983.nativedo.core.model.TopicSummary candidate) {
        if (listView != null) {
            listView.cancelNextTopicPull();
        }
        TopicFragment next = TopicFragment.forTopic(candidate);
        if (getParentActivity() instanceof NativeDoActivity) {
            // Replace, so continuous reading does not stack a screen per topic; Back returns to the
            // list the reader came from.
            ((NativeDoActivity) getParentActivity()).replace(next);
        } else {
            presentFragment(next);
        }
    }

    /**
     * Whether the answer the site gave already carries that floor, which needs no second read. */
    private static boolean carriesFloor(TopicDetail detail, int postNumber) {
        for (Post post : detail.getPosts()) {
            Integer number = post.getPostNumber();
            if (number != null && number == postNumber) {
                return true;
            }
        }
        return false;
    }

    /**
     * The center capsule's picture: the topic's original poster, as the same synthetic user the
     * rows draw (DiscourseAvatars). The container asks for the picture by user
     * ({@code setUserAvatar}, ChatAvatarContainer.java:1383-1413), and the file the session
     * downloaded is handed to that receiver by path once it lands - the same hand-over a row's
     * avatar needs (PostListView.ensureAvatar), because a synthetic photo's own location names no
     * path the image loader can read.
     */
    private void bindTopicAuthor(TopicDetail detail) {
        if (avatarContainer == null || container == null) {
            return;
        }
        io.github.baba983.nativedo.core.model.MentionCandidate author = detail.getCreatedBy();
        if (author == null || author.getUsername().isEmpty()) {
            // An answer that names no author (a windowed page can leave details out) must not
            // leave the capsule empty when an earlier answer already named one.
            return;
        }
        bindTopicAuthorUser(
                DiscourseAvatars.INSTANCE.user(
                        author.getUsername(),
                        author.getDisplayName(),
                        author.getAvatarTemplate(),
                        container.getBaseUrl(),
                        author.getAnimatedAvatarUrl()),
                author.getAvatarTemplate());
    }

    /** The author a row handed over, bound before the topic is read; see [ARG_AUTHOR_USERNAME]. */
    private void bindTopicAuthorFromArguments() {
        if (avatarContainer == null || container == null || getArguments() == null) {
            return;
        }
        String username = getArguments().getString(ARG_AUTHOR_USERNAME);
        if (username == null || username.isEmpty()) {
            return;
        }
        String template = getArguments().getString(ARG_AUTHOR_AVATAR_TEMPLATE, "");
        if (template == null) {
            template = "";
        }
        // The same synthetic user the list row drew (same username, same template), so the file
        // the row's own download put on disk is the file this capsule is handed below.
        bindTopicAuthorUser(
                DiscourseAvatars.INSTANCE.user(username, "", template, container.getBaseUrl()),
                template);
    }

    /**
     * One author on the capsule. The synthetic user's id comes from the username alone, so a row
     * and this screen can share the downloaded asset. Keep it across repeated binds, but replace
     * it when a later response supplies a different asset, including an animated avatar.
     */
    private void bindTopicAuthorUser(TLRPC.User user, String avatarTemplate) {
        if (avatarContainer == null || user == null) {
            return;
        }
        boolean sameAvatar = topicAuthor != null && topicAuthor.id == user.id
                && (topicAuthor.photo == null ? 0 : topicAuthor.photo.photo_id)
                == (user.photo == null ? 0 : user.photo.photo_id);
        topicAuthor = user;
        if (!sameAvatar) {
            // The container's avatar ask goes through MessagesController's own user table, so the
            // synthetic user is registered there first (the rows do the same when they bind).
            MessagesController.getInstance(currentAccount).putUser(user, true);
            avatarContainer.setUserAvatar(user);
        }
        // The file the row already downloaded, handed to the receiver by path; a same-author bind
        // keeps the picture that is drawn instead of resetting the spot to the initials.
        bindAuthorAvatarImage();
        if (container == null) {
            return;
        }
        String url = DiscourseAvatars.INSTANCE.url(
                avatarTemplate, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
        if (url != null && !DiscourseAvatars.INSTANCE.isCached(user)) {
            // The row that opened this screen already asked for this file; this call answers at
            // once when the file is on disk, and a row's download still in flight is the same
            // request the second caller would make.
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                bindAuthorAvatarImage();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /** The downloaded avatar at the path the image loader reads; the ask by user has no path. */
    private void bindAuthorAvatarImage() {
        if (avatarContainer == null || topicAuthor == null) {
            return;
        }
        org.telegram.ui.Components.BackupImageView avatarView = avatarContainer.getAvatarImageView();
        if (avatarView == null) {
            return;
        }
        // A picture is here or is being read (the same guard a row's hand-over uses): re-setting
        // it would clear the spot back to the initials until the loader delivered it again.
        if (avatarView.getImageReceiver().getBitmap() != null
                || avatarView.getImageReceiver().getImageDrawable() != null) {
            return;
        }
        ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(topicAuthor);
        if (location == null) {
            return;
        }
        avatarView.setImage(location, "50_50", avatarView.getImageReceiver().getDrawable(), topicAuthor);
    }

    /**
     * The capsule's own two lines and its author, from one answer: the title (the site's own is
     * authoritative over the link's), the topic author, and the category/reply subtitle. Called
     * for every answer the site gives, including the head answer whose posts a resumed read
     * throws away in favour of a window.
     */
    private void bindHeader(TopicDetail detail) {
        if (avatarContainer == null) {
            return;
        }
        String loadedTitle = detail.getTitle();
        if (loadedTitle != null && !loadedTitle.trim().isEmpty()) {
            setCapsuleTitle(loadedTitle.trim());
        }
        bindTopicAuthor(detail);
        bindSubtitle(detail);
    }

    // SimpleTextView can retain its empty-title bounds after a late title arrives. Re-measure
    // changed text so the marquee's hardware layer does not clip it to the old height.
    private void setCapsuleTitle(String title) {
        if (avatarContainer == null || title == null || title.trim().isEmpty()) {
            return;
        }
        String trimmed = title.trim();
        org.telegram.ui.ActionBar.SimpleTextView titleView = avatarContainer.getTitleTextView();
        CharSequence before = titleView == null ? null : titleView.getText();
        avatarContainer.setTitle(trimmed);
        if (titleView != null
                && !android.text.TextUtils.equals(titleView.getText(), before)) {
            titleView.requestLayout();
        }
    }

    /**
     * The capsule's second line: the topic's category, and - while the capsule still fits - how
     * many replies the topic has. The name comes from the category store the topic list fills; a
     * category it cannot name leaves the reply count to stand alone.
     */
    private void bindSubtitle(TopicDetail detail) {
        if (avatarContainer == null) {
            return;
        }
        io.github.baba983.nativedo.core.model.CategorySummary cached = container == null ? null
                : container.getCategoryStore().getCategories().getValue().get(detail.getCategoryId());
        setCapsuleSubtitle(cached == null ? null : cached.getName(), Math.max(0, detail.getTotalPosts() - 1));
        if (container == null || detail.getCategoryId() <= 0) {
            return;
        }
        DiscourseLoader.INSTANCE.categoryName(container, detail.getCategoryId(), name -> {
            if (topicDetail == detail) {
                setCapsuleSubtitle(name, Math.max(0, detail.getTotalPosts() - 1));
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private void setCapsuleSubtitle(String categoryName, int replies) {
        if (avatarContainer == null) {
            return;
        }
        String subtitle = categoryName == null || categoryName.isEmpty() ? null : categoryName;
        // While the list is the poster's floors only, the site's total reply count would describe
        // rows that are not on screen; the line names the filter instead, so the reader can always
        // tell why the topic looks shorter than its own count.
        String tail = null;
        if (reading.isOnlyOp()) {
            tail = "只看楼主";
        } else if (replies >= 0) {
            tail = Math.max(0, replies) + " 回复";
        }
        if (tail != null) {
            String composed = subtitle == null ? tail : subtitle + " · " + tail;
            // The filter label is short and must not be what the width check drops: a reader
            // looking at filtered rows has to be able to see why.
            if (reading.isOnlyOp() || subtitleFits(composed)) {
                subtitle = composed;
            }
        }
        // A persistent marker: while the rows on screen are the cache's, the capsule keeps saying so
        // (the Bulletin only says it once). A live read calls this again and clears it.
        if (showingCachedContent) {
            String marker = org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                    io.github.baba983.nativedo.R.string.offline_indicator);
            subtitle = subtitle == null ? marker : marker + " · " + subtitle;
        }
        setCapsuleSubtitle(subtitle);
    }

    /** Stores the capsule's own line and draws it unless a replier's line is taking it over. */
    private void setCapsuleSubtitle(CharSequence subtitle) {
        capsuleSubtitle = subtitle;
        refreshCapsuleSubtitle();
    }

    /**
     * Draws the capsule's line: the repliers take it while anyone else is composing a reply, and
     * the topic's own line (category, count, offline marker) comes back when the last one leaves.
     * The replier line carries upstream's animated typing dots before it, the way Telegram's chat
     * header draws a printing status.
     */
    private void refreshCapsuleSubtitle() {
        if (avatarContainer == null) {
            return;
        }
        boolean repliers = !repliersSubtitle.isEmpty();
        avatarContainer.setSubtitle(repliers ? repliersSubtitle : capsuleSubtitle);
        setCapsuleTypingDots(repliers);
    }

    /**
     * Attaches and runs (or stops and detaches) upstream's typing dots on the capsule's subtitle,
     * in the line's own colour. This is the component {@code ChatAvatarContainer} itself uses for a
     * chat's printing status; the dots sit inside the text line, before the words.
     */
    private void setCapsuleTypingDots(boolean show) {
        android.view.View subtitleView = avatarContainer.getSubtitleTextView();
        if (!(subtitleView instanceof org.telegram.ui.ActionBar.SimpleTextView)) {
            return;
        }
        org.telegram.ui.ActionBar.SimpleTextView subtitle =
                (org.telegram.ui.ActionBar.SimpleTextView) subtitleView;
        if (show) {
            repliersDots.show(subtitle, avatarContainer.getSubtitlePaint().getColor());
        } else {
            repliersDots.hide(subtitle);
        }
    }

    /**
     * Binds this screen to the topic's reply presence. The channel is `discourse-presence`'s own
     * (`/discourse-presence/reply/{topicId}`): the web client subscribes to it at the topic's footer
     * and enters it from the reply composer, so a reader here is visible to the site's own clients
     * and theirs are visible here. A screen that is not signed in has no reader to announce and no
     * channel to read.
     */
    private void startReplyPresence() {
        if (replyPresence != null) {
            // Back from the background: the controller re-reads the channel and its own policy.
            replyPresence.resume();
            return;
        }
        if (container == null || topicId <= 0 || isFinished || isPaused()) {
            return;
        }
        SessionSnapshot owner = container.getSessionStore().snapshot();
        if (owner == null || owner.getUsername().isEmpty()) {
            return;
        }
        // The site's reply composer keeps its presence for 10 s after the last input
        // (discourse-presence's KEEP_ALIVE), longer than a chat channel's 5 s.
        replyPresence = new TypingPresenceController(
                container.getDiscourseApiClient(),
                container.getMessageBusSession().getRunningClientFlow(),
                new TypingPresencePolicy(30_000L, 1_000L, 10_000L));
        replyPresence.setUsersListener(repliers -> {
            if (isFinished || isPaused()) {
                return;
            }
            repliersSubtitle = repliersLabel(repliers);
            refreshCapsuleSubtitle();
        });
        replyPresence.start("/discourse-presence/reply/" + topicId, owner.getUsername());
    }

    /** The capsule's line for the site's repliers, in the chat header's own wording. */
    private static String repliersLabel(java.util.List<ChatUser> repliers) {
        if (repliers.isEmpty()) {
            return "";
        }
        // The status verb comes first so a long name ellipsizes without taking the verb with it.
        String first = repliers.get(0).getUsername();
        if (repliers.size() == 1) {
            return "正在回复：" + first;
        }
        return "正在回复：" + first + " 等 " + repliers.size() + " 人";
    }

    /** The screen is gone or hidden: nobody is composing here, and the capsule stops saying they are. */
    private void stopReplyPresence(boolean destroy) {
        if (replyPresence != null) {
            replyPresence.setUsersListener(null);
            if (destroy) {
                replyPresence.stop();
                replyPresence = null;
            } else {
                replyPresence.pause();
            }
        }
        repliersSubtitle = "";
        refreshCapsuleSubtitle();
    }

    /**
     * Whether the header's center capsule still fits this line. ActionBar clamps the capsule to
     * the bar minus the back and menu capsules (ActionBar.java:2155, the same
     * dp(6+46+6+6+46+6) there), and the container spends a 52dp avatar plus 18dp on the text
     * (ChatAvatarContainer.getVisualWidth) and draws the capsule 6dp past the text on each side
     * (setupGlass). What is left is what the line may hold; the reply count is only added while
     * the composed line still fits there.
     */
    private boolean subtitleFits(String subtitle) {
        if (avatarContainer == null || actionBar == null) {
            return false;
        }
        int barWidth = actionBar.getMeasuredWidth();
        if (barWidth <= 0) {
            return true;
        }
        float available = barWidth
                - AndroidUtilities.dp(6 + 46 + 6 + 6 + 46 + 6)
                - AndroidUtilities.dp(52 + 18);
        return avatarContainer.getSubtitlePaint().measureText(subtitle) <= available;
    }

    /** The 书签 row says what the next tap does, from the topic's own state. */
    private void updateBookmarkMenuItem() {
        if (bookmarkMenuItem == null) {
            return;
        }
        bookmarkMenuItem.setTextAndIcon(
                topicBookmarked ? "取消书签" : "书签",
                topicBookmarked
                        ? org.telegram.messenger.R.drawable.msg_unfave
                        : org.telegram.messenger.R.drawable.msg_fave);
    }

    /** The topic's own bookmark, from where it is to where it is not (and back). */
    private void toggleTopicBookmark() {
        if (container == null || topicId <= 0) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        boolean wanted = !topicBookmarked;
        DiscourseLoader.INSTANCE.setTopicBookmark(container, topicId, wanted, () -> {
            topicBookmarked = wanted;
            updateBookmarkMenuItem();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showError("书签失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The overflow's level row says what the reader's current level is. */
    private void updateNotificationLevelMenuItem() {
        if (notificationLevelMenuItem == null) {
            return;
        }
        notificationLevelMenuItem.setTextAndIcon(
                "通知级别 · " + notificationLevelLabel(topicNotificationLevel),
                org.telegram.messenger.R.drawable.msg_folders_read);
    }

    /** The overflow's filter row says which way the next tap goes; the capsule says the mode. */
    private void updateOnlyOpMenuItem() {
        if (onlyOpMenuItem == null) {
            return;
        }
        onlyOpMenuItem.setTextAndIcon(
                reading.isOnlyOp() ? "显示全部" : "只看楼主",
                org.telegram.messenger.R.drawable.msg_openprofile);
    }

    /**
     * The topic's original poster: the site's own `details.created_by`, or the author the row that
     * opened this screen already drew. Null when neither names one.
     */
    private String opUsername() {
        if (topicDetail != null && topicDetail.getCreatedBy() != null) {
            String username = topicDetail.getCreatedBy().getUsername();
            if (username != null && !username.isEmpty()) {
                return username;
            }
        }
        String fromRow = getArguments() == null ? null : getArguments().getString(ARG_AUTHOR_USERNAME);
        return fromRow == null || fromRow.isEmpty() ? null : fromRow;
    }

    /**
     * The overflow's 只看楼主 row. The site owns the filter (`username_filters`), so the screen
     * re-reads the topic at the reader's place in the new mode instead of hiding rows it happens
     * to hold: a window in the middle of a thread may carry none of the poster's floors, and the
     * filtered stream is what paging, the badge and the capsule then describe. A discussion page
     * has no row at all - its list is already one floor's replies.
     */
    private void toggleOnlyOp() {
        if (threadMode || isThreadMode()) {
            return;
        }
        if (opUsername() == null) {
            // The site names the author in its own answer; a tap before that answer arrived reads
            // the head once and takes the mode when it names one (see [show]).
            reading.requestOnlyOpBeforeAuthor();
            if (topicDetail == null && !reading.isLoadingWindow()) {
                loadTopic(topicId);
            }
            return;
        }
        setOnlyOp(!reading.isOnlyOp());
    }

    private void setOnlyOp(boolean only) {
        if (reading.isOnlyOp() == only || threadMode || isThreadMode()) {
            return;
        }
        reading.beginOnlyOpSwitch(only);
        updateOnlyOpMenuItem();
        // Nothing on this screen is credited as read while the filter is on: the rows are the
        // poster's, and reporting them would advance the topic's cursor past floors by others the
        // reader never saw. The dwell that was accumulating stops at the mode change.
        if (reporter != null) {
            reporter.visibleFloors(java.util.Collections.emptyList());
        }
        if (topicDetail != null) {
            bindSubtitle(topicDetail);
        }
        // Re-read at the top of the viewport: the filtered answer is a window of the filtered
        // stream around that floor, and the list lands on the first poster's floor at or after it.
        Integer anchor = firstVisibleFloorNumber();
        if (anchor == null || anchor <= 0) {
            loadTopic(topicId);
        } else {
            loadWindow(anchor, anchor);
        }
    }

    /** The floor drawn by the first row of the viewport, or null; divider rows name none. */
    private Integer firstVisibleFloorNumber() {
        LinearLayoutManager manager = listView == null
                ? null : (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null || bubbles.isEmpty()) {
            return null;
        }
        int first = Math.max(0, manager.findFirstVisibleItemPosition());
        int last = Math.min(manager.findLastVisibleItemPosition(), bubbles.size() - 1);
        for (int position = first; position <= last; position++) {
            if (isDividerRow(position)) {
                continue;
            }
            int floorIndex = bubbleFloors.get(position);
            if (floorIndex < 0 || floorIndex >= floors.size()) {
                continue;
            }
            Integer number = floors.get(floorIndex).getPostNumber();
            if (number != null) {
                return number;
            }
        }
        return null;
    }

    /** A short note about something the screen could not do, said in passing. */
    private void showPassingNote(String text) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(org.telegram.messenger.R.drawable.msg_info, text)
                    .show();
        } catch (Exception bulletinError) {
            org.telegram.messenger.FileLog.e(bulletinError);
        }
    }

    private static String notificationLevelLabel(int level) {
        switch (level) {
            case 0:
                return "静音";
            case 2:
                return "跟踪";
            case 3:
                return "关注";
            default:
                return "常规";
        }
    }

    /**
     * The four levels the web client's topic-tracking control offers, as upstream's own checked
     * menu (the same {@link org.telegram.ui.Components.ItemOptions} the home options use). The
     * reader's current level is the checked one; choosing another writes it.
     */
    private void showNotificationLevelOptions() {
        if (getParentActivity() == null || overflowItem == null) {
            return;
        }
        org.telegram.ui.Components.ItemOptions io =
                org.telegram.ui.Components.ItemOptions.makeOptions(this, overflowItem);
        io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
        io.setDimAlpha(0x08);
        io.addChecked(topicNotificationLevel == 0, "静音", () -> setTopicNotificationLevel(0));
        io.addChecked(topicNotificationLevel == 1, "常规", () -> setTopicNotificationLevel(1));
        io.addChecked(topicNotificationLevel == 2, "跟踪", () -> setTopicNotificationLevel(2));
        io.addChecked(topicNotificationLevel == 3, "关注", () -> setTopicNotificationLevel(3));
        io.show();
        // Upstream raises its own topic menu by this constant; the popup otherwise hangs lower
        // than the item it belongs to.
        io.setTranslationY(-AndroidUtilities.dp(64));
    }

    /**
     * Writes the reader's chosen level and only moves the row once the site has taken it. A
     * refusal keeps the level the reader had and says so in passing; a level already in force is
     * not written at all.
     */
    private void setTopicNotificationLevel(int level) {
        if (container == null || topicId <= 0 || level == topicNotificationLevel) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        final int wanted = level;
        DiscourseLoader.INSTANCE.setTopicNotificationLevel(container, topicId, wanted, () -> {
            topicNotificationLevel = wanted;
            updateNotificationLevelMenuItem();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            org.telegram.messenger.FileLog.e("topic notification level failed", error);
            if (getParentActivity() != null) {
                try {
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createErrorBulletin("设置失败，请重试").show();
                } catch (Exception bulletinError) {
                    org.telegram.messenger.FileLog.e(bulletinError);
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The topic's canonical address, slug when the site named one and the bare id otherwise. */
    private String topicUrl() {
        if (container == null) {
            return "";
        }
        String slug = topicDetail == null ? null : topicDetail.getSlug();
        if (slug == null || slug.trim().isEmpty()) {
            return container.getBaseUrl() + "t/" + topicId;
        }
        return container.getBaseUrl() + "t/" + slug.trim() + "/" + topicId;
    }

    /** What the menu's 复制链接 leaves on the clipboard: the topic's own address. */
    private void copyTopicLink() {
        if (getParentActivity() == null) {
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", topicUrl()));
    }

    /** The overflow's 分享: hand the topic's address to whatever the reader shares links with. */
    private void shareTopic() {
        shareUrl(topicUrl());
    }

    /** A floor's 分享: its own address, the canonical `t/<topic>/<floor>` the site routes. */
    private void sharePost(int floor) {
        shareUrl(postUrl(floor));
    }

    /**
     * Hands [url] to the system's share sheet. The chooser wraps an {@code ACTION_SEND} of plain
     * text, so the reader picks the app - the same shape upstream uses for a link it shares.
     */
    private void shareUrl(String url) {
        if (getParentActivity() == null || url == null || url.isEmpty()) {
            return;
        }
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_TEXT, url);
        getParentActivity().startActivity(
                android.content.Intent.createChooser(send, "分享"));
    }

    /** 在浏览器打开: upstream's own browser, in-app by default (see the link-open setting). */
    private void openTopicInBrowser() {
        if (getParentActivity() == null) {
            return;
        }
        Browser.openUrl(getParentActivity(), topicUrl());
    }

    /**
     * A tap on the center capsule asks for the person who started the topic; it is the same screen
     * a tap on their post's avatar opens, and the same the chat header opens on a tap of its own
     * (ChatAvatarContainer's container listener). The avatar's X travels with the request: the
     * profile plays the chat header's own flight for it (ChatAvatarContainer.openProfile sets
     * ProfileActivity's `playProfileAnimation = 2` the same way), measured with the same
     * ViewPositionWatcher call upstream makes before its animation (ProfileActivity.java:10080).
     */
    private void openTopicAuthorProfile() {
        if (topicAuthor == null || topicAuthor.username == null || topicAuthor.username.isEmpty()) {
            return;
        }
        if (getParentActivity() instanceof NativeDoActivity) {
            ((NativeDoActivity) getParentActivity()).present(
                    io.github.baba983.nativedo.profile.ProfileFragment.forUserFromAvatar(
                            topicAuthor.username, topicAuthor.first_name,
                            avatarContainer.getAvatarImageView(), wallpaperView));
        }
    }

    /**
     * What the viewport holds: how far the reader has come, and which floors are being read. The
     * dwell clock is only credited to the floors on screen, so the window is reported with the
     * position rather than the position alone.
     */
    private void reportViewport() {
        // The way back up follows the viewport alone; it is not part of the read report.
        updateTopicPageUp();
        // The saved reading window also fences a queued callback after the search UI is destroyed.
        if (searchReadingPosts != null) {
            return;
        }
        if (reading.isOnlyOp()) {
            // The filtered list is the poster's floors; crediting them would advance the site's
            // cursor past floors by others that were never shown. No floor is on screen as far as
            // the report is concerned, and the arrow's number is the filtered stream below.
            if (reporter != null) {
                reporter.visibleFloors(java.util.Collections.emptyList());
            }
            updateTopicPageDown();
            return;
        }
        if (reporter == null || listView == null) {
            updateTopicPageDown();
            return;
        }
        androidx.recyclerview.widget.LinearLayoutManager manager =
                (androidx.recyclerview.widget.LinearLayoutManager) listView.getLayoutManager();
        if (manager == null) {
            updateTopicPageDown();
            return;
        }
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        if (last < 0) {
            updateTopicPageDown();
            return;
        }
        java.util.List<Integer> visible = new ArrayList<>();
        for (int position = Math.max(0, first); position <= last && position < bubbleFloors.size(); position++) {
            // A day's date row and the unread divider stand above a floor rather than in it, so
            // only a bubble row is what the reader has actually seen.
            if (isDividerRow(position)) {
                continue;
            }
            // The row holds the floor's index; what a report is made of is the site's own number for
            // the floor, which is the one an open of the topic is answered with.
            Integer number = floors.get(bubbleFloors.get(position)).getPostNumber();
            if (number != null) {
                visible.add(number);
            }
        }
        reporter.visibleFloors(visible);
        if (!visible.isEmpty()) {
            reporter.advanceTo(visible.get(visible.size() - 1));
        }
        // Apply the new read mark before drawing its counter, including on a viewport that reaches
        // the end. Scrolling backward changes the arrow's visibility, never the read mark.
        updateTopicPageDown();
    }

    /**
     * The reader is leaving the topic. What they reached is reported now rather than after the
     * debounce, and a topic they read to its end stops drawing a badge in the list they are going
     * back to - the list's own read of the feed may be answered before the site has taken the report
     * in (see TopicReadMarks).
     */
    @Override
    public void onResume() {
        super.onResume();
        if (reporter != null && topicSearch == null) {
            reporter.setForeground(true);
        }
        // The failure hint is upstream's Bulletin, which anchors itself above whatever the screen's
        // bottom band is; the delegate reports the composer's own height (ChatActivity.java:29789).
        org.telegram.ui.Components.Bulletin.addDelegate(this,
                new org.telegram.ui.Components.Bulletin.Delegate() {
                    @Override
                    public int getBottomOffset(int tag) {
                        int bottom = bottomInputHeight();
                        // A retry hint must leave the arrow it names visible and tappable.
                        if (threadPageDown != null && threadPageDown.getVisibility() == View.VISIBLE) {
                            bottom = Math.max(bottom, threadAffordanceBottom()
                                    + threadPageDown.getHeight() + AndroidUtilities.dp(4));
                        }
                        return bottom;
                    }

                    @Override
                    public boolean allowLayoutChanges() {
                        return false;
                    }
                });
        // Upstream's composer holds a keyboard and a set of notification observers; a screen that
        // comes back has to tell it, exactly as ChatActivity does (ChatActivity.java onResume).
        if (composer != null) {
            composer.onResume();
        }
        if (composerTools != null) {
            composerTools.onResume();
        }
        startTitleGradient();
        if (attachSheet != null) attachSheet.onResume();
        startReplyPresence();
    }

    /**
     * Back means the selection first, the topic second - upstream's chat does the same: a back press
     * while text is selected drops the selection and the topic stays.
     */
    @Override
    public boolean onBackPressed(boolean invoked) {
        if (topicSearch != null || (actionBar != null && actionBar.isSearchFieldVisible())) {
            if (invoked) {
                // The bar's own search field and the back capsule's lifecycle: collapsing it runs
                // onSearchCollapse, which restores the capsule and the reading window. When the
                // fragment is driven directly (no ActionBarLayout in front), close the session here.
                if (actionBar != null && actionBar.isSearchFieldVisible()) {
                    actionBar.closeSearchField();
                } else {
                    closeTopicSearch();
                }
            }
            return false;
        }
        if (composerTools != null && composerTools.onBackPressed(invoked)) {
            return false;
        }
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            if (invoked) {
                textSelectionHelper.clear();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean dismissDialogOnPause(android.app.Dialog dialog) {
        return dialog != attachSheet && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTitleGradient();
        if (attachSheet != null) attachSheet.onPause();
        // The reader is leaving: what is in the field is written down before anything else (a
        // process death after this loses only the uncommitted keystroke, the same as Telegram).
        saveDraft();
        if (composerTools != null) {
            composerTools.onPause();
        }
        if (composer != null) {
            composer.onPause();
        }
        stopReplyPresence(false);
        leaveTopic();
    }

    /** The microphone permission answer; the recording host waits for it before a second request. */
    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (attachSheet != null) attachSheet.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (composerTools != null) {
            composerTools.onPermissionResult(requestCode);
        }
    }

    @Override
    public void onFragmentDestroy() {
        // A dialog nobody dismisses outlives the screen that opened it; the sheet goes with this one.
        if (attachSheet != null) {
            attachSheet.dismissInternal();
            attachSheet = null;
        }
        if (topicSearch != null) {
            topicSearch.destroy();
            topicSearch = null;
        }
        // The screen is gone: a block that outlives it must not call back to start a fetch for a row
        // nobody is looking at.
        for (Map.Entry<String, Runnable> entry : photoStarters.entrySet()) {
            DiscourseImageBridge.INSTANCE.clearPhotoStarter(entry.getKey(), entry.getValue());
        }
        photoStarters.clear();
        if (searchScroll != null) {
            searchScroll.cancel();
        }
        if (reporter != null) {
            // The screen is gone: the ticker must be too, or it keeps crediting floors of a topic
            // nobody is looking at.
            reporter.stop();
        }
        // The button's own show factor is clock-driven; a screen that leaves with it mid-flight must
        // drop the frame callback rather than let it draw into a dead view.
        pageDownVisible.cancel();
        pageUpVisible.cancel();
        stopTitleGradient();
        cancelFloatingDateAnimation();
        pageDownJumping = false;
        if (listView != null) {
            // A smooth scroll in flight also posts frame callbacks; a screen that leaves must stop
            // its list rather than keep ticking against a dead view tree.
            listView.stopScroll();
        }
        saveDraft();
        stopReplyPresence(true);
        if (composerTools != null) {
            composerTools.onDestroy();
            composerTools = null;
        }
        if (composer != null) {
            // The composer registered notification observers and holds a keyboard; upstream calls
            // this from ChatActivity.onFragmentDestroy (3359-3360) and leaking it outlives the
            // screen (a rebuilt composer would then double every observer it adds).
            composer.onDestroy();
            composer = null;
        }
        inputViews = null;
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
        if (avatarContainer != null) {
            avatarContainer.onDestroy();
        }
        leaveTopic();
        // The screen is gone: any reply target still being read must not call back into it.
        bindGeneration++;
        // And any edit write or post read still in flight belongs to the screen that left.
        editGeneration++;
        // And any stream page or window read still in flight belongs to the screen that left.
        reading.supersedeWindow();
        // The post menu is its own window; leaving the screen with it open would leave a stale menu
        // anchored over whatever is presented next.
        if (messageMenu != null) {
            messageMenu.dismiss();
            messageMenu = null;
        }
        // A replies read still in flight belongs to the page that is gone; bumping the generation
        // makes its callback a no-op instead of a write into a dead view.
        threadGeneration++;
        super.onFragmentDestroy();
    }

    private void leaveTopic() {
        if (reporter == null) {
            return;
        }
        reportViewport();
        if (reporter.readToTheEnd()) {
            TopicReadMarks.INSTANCE.markReadInFull(topicId);
        }
        reporter.setForeground(false);
        reporter.flush();
    }

    /**
     * Puts the floor the screen was opened for at the top of it. The site answered the request with
     * the window of posts around that floor, so the row is among the first ones bound and the list
     * only has to be told where to start.
     */
    private void scrollToFloor(int floor) {
        if (floor <= 0 || listView == null) {
            return;
        }
        // The floor the site was asked to land on may itself have been deleted: the row to put at
        // the top is then the first floor the site really has at or after it.
        int index = rowIndexOrAfter(floor);
        if (index >= 0) {
            if (topicSearch != null) {
                final int generation = reading.getGeneration();
                listView.post(() -> {
                    if (topicSearch == null || !reading.isCurrent(generation)) {
                        return;
                    }
                    // No unfenced animator-finished callback may scroll an older query after Back.
                    listView.getItemAnimator().endAnimations();
                    searchScroll.scrollToPosition(index, AndroidUtilities.dp(12), false, true);
                    View view = listView.getLayoutManager().findViewByPosition(index);
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setHighlightedAnimated();
                    }
                });
            } else {
                listView.scrollToPosition(index);
            }
        }
    }

    /**
     * The overflow's 话题内搜索 row opens the bar's own search item. This is the item's own expand
     * animation and its own field, the same path a tap on a shown search icon took
     * ({@link org.telegram.ui.ActionBar.ActionBarMenuItem#openSearch}); the item itself is GONE, so
     * nothing on the bar has to be tapped twice.
     */
    private void openTopicSearchFromMenu() {
        if (searchItem == null) {
            return;
        }
        searchItem.openSearch(true);
        final android.widget.EditText field = searchItem.getSearchField();
        if (field == null) {
            return;
        }
        // The overflow is a focusable window of its own. While it plays out its close, the bar's
        // own window has no focus (and on the very first open the field has no size yet), so the
        // expand's own showKeyboard is dropped and the keyboard would only appear on a second open.
        // Ask for the IME again once this window really has focus - the field is focused and laid
        // out by then, and a re-open finds exactly this state - instead of guessing a delay.
        if (field.hasWindowFocus()) {
            AndroidUtilities.showKeyboard(field);
            return;
        }
        field.getViewTreeObserver().addOnWindowFocusChangeListener(
                new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
                    @Override
                    public void onWindowFocusChanged(boolean hasFocus) {
                        if (!hasFocus) {
                            return;
                        }
                        field.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        if (searchItem != null && searchItem.isSearchFieldVisible()) {
                            AndroidUtilities.showKeyboard(field);
                        }
                    }
                });
    }

    /**
     * Opens the session behind the bar's own search item (ActionBarMenuItem's onSearchExpand).
     * Returns false when the window cannot be read yet; the caller then collapses the field again.
     */
    private boolean openTopicSearch() {
        if (topicSearch != null) {
            return true;
        }
        if (topicDetail == null || reading.isLoadingWindow()) {
            return false;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        searchReadingPosition = Math.max(0, manager.findFirstVisibleItemPosition());
        View anchor = manager.findViewByPosition(searchReadingPosition);
        searchReadingOffset = anchor == null ? 0 : anchor.getTop() - listView.getPaddingTop();
        searchReadingDetail = topicDetail;
        searchReadingPosts = new ArrayList<>(floors);
        searchReadingRetry = retryAction;
        org.telegram.ui.Components.Bulletin.hideVisible();
        if (threadMode) {
            threadGeneration++;
            threadLoading = false;
        }
        supersedeWindow();
        if (reporter != null) {
            reporter.setForeground(false);
        }
        if (textSelectionHelper != null) {
            textSelectionHelper.clear();
        }
        listView.stopScroll();
        searchScroll = new org.telegram.ui.Components.RecyclerAnimationScrollHelper(listView, manager);
        // The bar stays where it is: the search capsule is its own item's field, and the back
        // capsule is what closes it. Only the chat's own bottom islands go away.
        showTopicCapsule(false);
        // The menu's own item goes too, not just its icon: ActionBar sizes the glass menu circle
        // from the visible items' span (checkMenuItemsWidth, ActionBar.java:2203-2217; drawn at
        // 2266-2270), so a still-visible overflow leaves an empty circle on the right and stops
        // the search capsule short. A GONE item is not counted, so the circle collapses and the
        // capsule reaches the edge. Close and Back restore it (closeTopicSearch).
        if (overflowItem != null) {
            overflowItem.setVisibility(View.GONE);
        }
        inputViews.setVisibility(View.GONE);
        attachStrip.setVisibility(View.GONE);
        applyPageDownVisible(false, false);
        applyPageUpVisible(false, false);
        threadEmptyCell.setVisibility(View.GONE);
        topicSearch = new io.github.baba983.nativedo.search.TopicSearch(
                getContext(), container, topicId, (android.widget.FrameLayout) fragmentView,
                listView, searchItem.getSearchField(), actionBar, glassHost, resourceProvider,
                listPaddingTop, bottomInset,
                new io.github.baba983.nativedo.search.TopicSearch.Host() {
                    @Override public void queryChanged() {
                        searchScroll.cancel();
                        supersedeWindow();
                        showProgressView(false);
                    }
                    @Override public void navigate(int floor, boolean forward) {
                        searchScroll.cancel();
                        searchScroll.setScrollDirection(forward
                                ? org.telegram.ui.Components.RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN
                                : org.telegram.ui.Components.RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
                        boolean loaded = false;
                        for (Post post : floors) {
                            if (post.getPostNumber() != null && post.getPostNumber() == floor) {
                                loaded = true;
                                break;
                            }
                        }
                        // Even an already-loaded target supersedes a previous target's late response.
                        supersedeWindow();
                        if (loaded) {
                            scrollToFloor(floor);
                        } else {
                            loadWindow(floor, floor);
                        }
                    }
                });
        applyListPadding();
        return true;
    }

    /** Collapse: the bar's field is already on its way out; restore the capsule and the topic. */
    private void onTopicSearchCollapsed() {
        showTopicCapsule(true);
        // Whether or not a session was behind the field, the bar's right side is the overflow
        // again: toggleSearch re-showed the search item on the way out (ActionBarMenuItem:987).
        restoreBarMenu();
        if (topicSearch != null) {
            closeTopicSearch();
        }
    }

    /**
     * The bar's menu is whole again: the overflow item was GONE while the search field owned the
     * bar, which is what collapsed its glass circle (openTopicSearch). The search item itself never
     * stands on its own - ActionBarMenuItem.toggleSearch re-shows it on every collapse
     * (ActionBarMenuItem:987) and the reader's entry is the overflow's own row - so it is held GONE
     * here, which is also what keeps it from reserving width next to the overflow
     * (ActionBarMenu.getItemsWidth skips a GONE item, ActionBar:2203-2217).
     */
    private void restoreBarMenu() {
        if (overflowItem != null) {
            overflowItem.setVisibility(View.VISIBLE);
        }
        if (searchItem != null) {
            searchItem.setVisibility(View.GONE);
        }
    }

    /** The center capsule fades with the bar's own search animation (ActionBar:1207-1219). */
    private void showTopicCapsule(boolean visible) {
        if (avatarContainer == null) {
            return;
        }
        avatarContainer.animate().cancel();
        if (visible) {
            avatarContainer.setVisibility(View.VISIBLE);
        }
        avatarContainer.animate().alpha(visible ? 1f : 0f).setDuration(150)
                .withEndAction(() -> {
                    if (!visible && avatarContainer != null) {
                        avatarContainer.setVisibility(View.GONE);
                    }
                }).start();
        animateSearchFactor(visible ? 0f : 1f);
    }

    /**
     * ActionBar sizes its center glass capsule from the avatar container and re-centres it until
     * {@link org.telegram.ui.ActionBar.ActionBar#setSearchFactor} tells it the search field owns
     * the bar (ActionBar:2244): only at factor 1 does the capsule span {@code leftDefault..
     * rightDefault}, which is where the bar lays the search field across the menu
     * (ActionBar.onLayout, {@code menuLeft}). It moves with the capsule's own 150ms fade.
     */
    private void animateSearchFactor(float to) {
        if (actionBar == null || searchFactorValue == to) {
            return;
        }
        if (searchFactorAnimator != null) {
            searchFactorAnimator.cancel();
        }
        searchFactorAnimator = android.animation.ValueAnimator.ofFloat(searchFactorValue, to);
        searchFactorAnimator.setDuration(150);
        searchFactorAnimator.addUpdateListener(animation -> {
            searchFactorValue = (float) animation.getAnimatedValue();
            actionBar.setSearchFactor(searchFactorValue);
        });
        searchFactorAnimator.start();
    }

    private void closeTopicSearch() {
        searchScroll.cancel();
        topicSearch.destroy();
        topicSearch = null;
        supersedeWindow();
        showProgressView(false);
        if (failureView != null) {
            failureView.setVisibility(View.GONE);
        }
        topicDetail = searchReadingDetail;
        streamPostIds = topicDetail.getStreamPostIds();
        noteServedFromCache(topicDetail.getFromCache());
        reading.bindWindow(streamPostIds, searchReadingPosts);
        bindPosts(searchReadingPosts);
        searchReadingPosts = null;
        searchReadingDetail = null;
        retryAction = searchReadingRetry;
        searchReadingRetry = null;
        bindHeader(topicDetail);
        // The bar's menu is whole again: the overflow item was GONE while the search field owned
        // the bar, which is what collapsed its glass circle (openTopicSearch), and the search item
        // is held GONE so it never stands as its own icon.
        restoreBarMenu();
        inputViews.setVisibility(View.VISIBLE);
        updateAttachStrip();
        applyListPadding();
        ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(
                searchReadingPosition, searchReadingOffset);
        if (reporter != null) {
            reporter.start(topicDetail);
            reporter.setForeground(true);
        }
        updateThreadStatus();
        listView.post(this::reportViewport);
    }

    /**
     * Moves the list to a reply's target: a floor already loaded scrolls into view, and one the
     * window does not carry is read the way the floor's own link is, which lands the list on it.
     */
    private void jumpToFloor(int floor) {
        for (int index = 0; index < bubbleFloors.size(); index++) {
            Integer number = floors.get(bubbleFloors.get(index)).getPostNumber();
            if (number != null && number == floor) {
                if (listView != null && listView.getLayoutManager() != null) {
                    listView.getLayoutManager().scrollToPosition(index);
                }
                return;
            }
        }
        if (threadMode) {
            // The target is not one of this thread's floors. A discussion page must not replace its
            // filtered list with a topic window: the reader asked for the post, so the normal topic
            // screen is pushed at that floor, and this page is left exactly as it was.
            presentFragment(TopicFragment.forFloor(
                    topicId, topicDetail == null ? null : topicDetail.getTitle(), floor));
            return;
        }
        loadWindow(floor, floor);
    }

    /**
     * Opens the direct replies to the floor the pressed bubble draws: the same screen, in its thread
     * mode, pushed over this one. The site reads them by the post's own id, while each reply is
     * named by its floor. The pushed page carries the post that was tapped and the topic it belongs
     * to, so it draws at once; this screen - its place in the list and the composer's draft - is
     * left exactly where it was, and comes back unchanged.
     */
    private void openRepliesThread(MessageObject message) {
        if (container == null || getParentActivity() == null || message == null) {
            return;
        }
        int floor = floorOf(message);
        if (floor < 0 || floor >= floors.size()) {
            return;
        }
        Post post = floors.get(floor);
        Integer number = post.getPostNumber();
        if (number == null) {
            return;
        }
        TopicFragment thread = TopicFragment.forReplies(
                topicId,
                topicDetail == null ? null : topicDetail.getTitle(),
                post.getId(),
                number,
                post.getReplyCount());
        thread.seedThread(post, topicDetail);
        presentFragment(thread);
    }

    private void bind(TopicDetail detail) {
        bindPosts(firstPage(detail));
    }

    /**
     * Binds a set of floors: the clearing, the reply-target map, the rows, and the follow-ups (the
     * media, the avatars, the reply targets not in this set). Shared by a topic window and by a
     * discussion page - which binds its one root here and appends its replies as they arrive.
     */
    private void bindPosts(List<Post> loaded) {
        // A new window owns its titles too: the frame loop stops with the old rows and starts again
        // when a rebound floor carries a holographic title.
        stopTitleGradient();
        animatedTitlesLoaded = false;
        // A new window: any target read still in flight belongs to the window before it.
        bindGeneration++;
        // And any stream page on its way belonged to that window too: the flag it left behind must
        // not stop the new window's first page, and its own callback is dropped by generation.
        reading.rebindPages();
        floors.clear();
        bubbles.clear();
        bubbleFloors.clear();
        bubbleBodies.clear();
        unreadDivider = null;
        users.clear();
        avatarTemplates.clear();
        postByNumber.clear();
        replyTargetFloors.clear();
        replyRequestsInFlight.clear();
        // A new window owns its own writes: a delete or recover still on its way belongs to the
        // window that was here, and its flag must not block the one that replaces it.
        sendingDelete = false;
        // Every floor of the window is known before any bubble is built, so a reply that answers a
        // floor carried *later* in the same window (Discourse lists a window in floor order, so the
        // answering floor is always built first) still finds its target.
        for (Post post : loaded) {
            Integer number = post.getPostNumber();
            if (number != null) {
                postByNumber.put(number, post);
            }
        }
        for (Post post : loaded) {
            add(post, topicId);
        }
        placeUnreadDivider();
        // The stock cell resolves an author through MessagesController, not through the map it was
        // built with, so the authors have to be registered there before the cells bind. putUser with
        // fromCache=true is a map write: no request, no database.
        registerUsersInController();
        notifyPostsChanged();
        for (int index = 0; index < bubbles.size(); index++) {
            fetchPostMedia(index);
        }
        fetchAvatars();
        // A reply whose target was not in this window is read from the site now; the list it sits on
        // is not reloaded, and only that floor's rows are rebuilt when the target lands.
        resolveMissingReplies();
        // And a reply target a restored draft carried is named again, now that the floors exist.
        applyReplyTarget();
        // The rows are laid out after this bind, so the visibility check runs on the next frame.
        if (animatedTitlesLoaded && listView != null) {
            listView.post(this::startTitleGradient);
        }
    }

    /**
     * Reads the target of every reply the loaded window does not carry, once each, and rebuilds every
     * answering floor when the target arrives. The whole list is never reset: the reader's place and
     * the rest of the rows are left exactly where they were.
     *
     * <p>Two floors can answer the same target, so the read is deduplicated but the result is applied
     * to all of them; a target read for a window the screen has since left is dropped.</p>
     */
    private void resolveMissingReplies() {
        if (container == null) {
            return;
        }
        final int generation = bindGeneration;
        // Distinct, in order: the same target may be named by more than one loaded floor.
        java.util.LinkedHashSet<Integer> wanted = new java.util.LinkedHashSet<>();
        for (int floor = 0; floor < floors.size(); floor++) {
            Integer number = floors.get(floor).getReplyToPostNumber();
            if (number == null || number <= 0 || postByNumber.containsKey(number)) {
                continue;
            }
            wanted.add(number);
        }
        for (Integer number : wanted) {
            if (!replyRequestsInFlight.add(number)) {
                continue;
            }
            final int targetNumber = number;
            DiscourseLoader.INSTANCE.resolvePost(container, topicId, targetNumber, target -> {
                if (generation != bindGeneration) {
                    // The screen rebound while this was in flight; the target belongs elsewhere.
                    return kotlin.Unit.INSTANCE;
                }
                replyRequestsInFlight.remove(targetNumber);
                if (target != null && target.getPostNumber() != null
                        && target.getPostNumber() == targetNumber) {
                    postByNumber.put(targetNumber, target);
                    // Every floor that answers this target is rebuilt by its own current identity,
                    // not by an index captured when the read began; and the target's author is
                    // registered so the stock reply header can name it.
                    rebuildFloorsReplyingTo(targetNumber, floors.size(), true);
                    registerUsersInController();
                }
                return kotlin.Unit.INSTANCE;
            }, error -> {
                if (generation != bindGeneration) {
                    return kotlin.Unit.INSTANCE;
                }
                replyRequestsInFlight.remove(targetNumber);
                // The read failed; the reply simply keeps no preview, and the floor's menu still
                // offers its own "回复". A later page or scroll asks again.
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * Rebuilds every floor at index below [maxFloorExclusive] whose reply names [targetNumber]. A
     * floor is found by its own current identity, so a rebuild is never applied to whatever floor
     * happens to sit at an index captured earlier.
     *
     * <p>Async reply targets update the existing row. Paging callers keep adapter notifications:
     * their inserted rows have not been laid out yet, so visible-position lookups are stale.</p>
     */
    private void rebuildFloorsReplyingTo(int targetNumber, int maxFloorExclusive, boolean inPlace) {
        for (int floor = 0; floor < maxFloorExclusive && floor < floors.size(); floor++) {
            Integer number = floors.get(floor).getReplyToPostNumber();
            if (number != null && number == targetNumber) {
                if (inPlace) {
                    refreshReplyInPlace(floor);
                } else {
                    rebuildFloor(floor);
                }
            }
        }
    }

    /**
     * Registers the floors' authors in MessagesController. The stock cell names a reply preview's
     * author through that controller rather than through the map the message was built with, so a
     * target resolved later than the rest of the window is registered the moment it lands.
     */
    private void registerUsersInController() {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (TLRPC.User user : users.values()) {
            controller.putUser(user, true);
        }
    }

    /**
     * Avatars are part of the author, not of the post, so they are fetched once per participant and
     * the visible author rows are redrawn when one lands. PostListView resolves the cached path
     * during drawing; replacing the adapter would discard the reader's scroll anchor.
     */
    private void fetchAvatars() {
        if (container == null) {
            return;
        }
        for (Map.Entry<Long, String> entry : avatarTemplates.entrySet()) {
            TLRPC.User user = users.get(entry.getKey());
            String url = DiscourseAvatars.INSTANCE.url(
                    entry.getValue(), container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (user == null || url == null || DiscourseAvatars.INSTANCE.isCached(user)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                MessagesController.getInstance(currentAccount).putUser(user, true);
                if (listView != null) {
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View child = listView.getChildAt(i);
                        if (child instanceof ChatMessageCell) {
                            MessageObject message = ((ChatMessageCell) child).getMessageObject();
                            if (message != null && MessageObject.getFromChatId(message.messageOwner) == user.id) {
                                child.invalidate();
                            }
                        }
                    }
                    listView.invalidate();
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * The topic payload carries the posts the reader sees first plus the ids of the whole stream;
     * the pages after it fetch the next slice of those ids. A topic short enough to arrive whole
     * leaves nothing to fetch.
     */
    private List<Post> firstPage(TopicDetail detail) {
        List<Post> all = detail.getPosts();
        reading.bindWindow(streamPostIds, all);
        return all;
    }

    /** One floor, drawn as its date row (when it opens a day) and the bubbles its body splits into. */
    private void add(Post post, long topicId) {
        armAnimatedTitle(post);
        int floor = floors.size();
        floors.add(post);
        appendFloorRows(post, topicId, floor, bubbles, bubbleBodies, bubbleFloors);
        Integer number = post.getPostNumber();
        if (number != null) {
            postByNumber.put(number, post);
        }
        String avatarTemplate = post.getAuthorAvatarTemplate();
        if (avatarTemplate != null && !avatarTemplate.isEmpty()) {
            avatarTemplates.put(DiscourseRows.userId(post.getAuthorUsername()), avatarTemplate);
        }
    }

    /** A floor that carries a holographic title needs the frame loop, whichever path loaded it. */
    private void armAnimatedTitle(Post post) {
        if (!animatedTitlesLoaded && DiscourseUserTitles.INSTANCE.isAnimated(post.getAuthorTitle())) {
            animatedTitlesLoaded = true;
            startTitleGradient();
        }
    }

    /**
     * The divider upstream's chat draws above the first message the reader has not seen. The site
     * owns the mark: its own last-read floor says where reading stopped, so a window that carries a
     * later floor gets the divider in front of that floor's first bubble - and no divider at all
     * when the reader is already at the first unread floor.
     */
    private void placeUnreadDivider() {
        if (threadMode || topicDetail == null) {
            return;
        }
        int lastRead = topicDetail.getLastReadPostNumber();
        if (lastRead <= 0) {
            return;
        }
        int row = -1;
        for (int index = 0; index < bubbleFloors.size(); index++) {
            // A date row above the floor names that floor too; the divider goes below it, above the
            // floor's own first bubble, the way a chat writes it.
            if (isDividerRow(index)) {
                continue;
            }
            Integer number = floors.get(bubbleFloors.get(index)).getPostNumber();
            if (number != null && number > lastRead) {
                row = index;
                break;
            }
        }
        if (row <= 0) {
            return;
        }
        unreadDivider = unreadDividerMessage();
        bubbles.add(row, unreadDivider);
        bubbleBodies.add(row, null);
        // The divider introduces the same floor its next row draws, so every mapping stays aligned.
        bubbleFloors.add(row, bubbleFloors.get(row));
    }

    /** The divider's own message: upstream's date row, carrying the line a chat writes there. */
    private MessageObject unreadDividerMessage() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = Integer.MAX_VALUE;
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.message = "";
        MessageObject object = new MessageObject(currentAccount, message, false, false);
        object.type = MessageObject.TYPE_DATE;
        object.messageText = UNREAD_DIVIDER_LINE;
        return object;
    }

    /** Whether the row at [position] is the unread divider rather than a floor's bubble. */
    private boolean isUnreadDividerRow(int position) {
        return unreadDivider != null && position >= 0 && position < bubbles.size()
                && bubbles.get(position) == unreadDivider;
    }

    /**
     * Whether the row at [position] is a day's date row or the unread divider rather than a floor's
     * bubble. Both stand above a floor and hold that floor's index in [bubbleFloors], so a mapping
     * read from a divider row names the floor it introduces.
     */
    private boolean isDividerRow(int position) {
        if (position < 0 || position >= bubbles.size()) {
            return false;
        }
        MessageObject row = bubbles.get(position);
        return row.isDateObject || row == unreadDivider;
    }

    /** The day the last row of [rows] belongs to; the day a floor that follows it opens against. */
    private int lastRowDay(List<MessageObject> rows) {
        if (rows.isEmpty()) {
            return -1;
        }
        MessageObject last = rows.get(rows.size() - 1);
        return last.messageOwner == null ? -1 : reading.dayKey(last.messageOwner.date);
    }

    /**
     * The date row a chat writes above a day's first message: the day itself, at its midnight, with
     * the day's own text (ChatActivity 21132-21155). The date is the day's, not the message's hour,
     * so the row and the floating pill always name the same day.
     */
    private MessageObject dateRow(Post post) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(DiscourseRows.epochSeconds(post.getCreatedAt()) * 1000L);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        int midnight = (int) (calendar.getTimeInMillis() / 1000);
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = LocaleController.formatDateChat(midnight);
        message.id = 0;
        message.date = midnight;
        MessageObject row = new MessageObject(currentAccount, message, false, false);
        row.type = MessageObject.TYPE_DATE;
        row.contentType = 1;
        row.isDateObject = true;
        return row;
    }

    /**
     * One floor's rows: the day's own date row first when the floor opens a day, then the floor's
     * bubbles. The date row is the one a chat writes above the first message of each day; the rows
     * of the same day that follow it carry none.
     */
    private void appendFloorRows(Post post, long topicId, int floor,
            List<MessageObject> rows, List<PreparedPostBody> bodies, List<Integer> rowFloors) {
        int day = reading.dayKey(DiscourseRows.epochSeconds(post.getCreatedAt()));
        if (reading.opensDateRow(lastRowDay(rows), day)) {
            rows.add(dateRow(post));
            bodies.add(null);
            // The row stands above this floor and introduces it, so it names the floor below it.
            rowFloors.add(floor);
        }
        List<MessageObject> floorBubbles = new ArrayList<>();
        List<PreparedPostBody> floorBodies = new ArrayList<>();
        buildBubbles(post, topicId, floorBubbles, floorBodies);
        for (int index = 0; index < floorBubbles.size(); index++) {
            rows.add(floorBubbles.get(index));
            bodies.add(floorBodies.get(index));
            rowFloors.add(floor);
        }
    }

    /**
     * The floating date, read the way the chat reads it (ChatActivity 16220-16405): the top row's day
     * is written into the pill, the day's own row is hidden once it slides under the list's top edge,
     * and the pill is pushed up as that row leaves so the two hand over to each other.
     */
    private void updateFloatingDate() {
        if (floatingDate == null || listView == null) {
            return;
        }
        int clipTop = listPaddingTop;
        View minChild = null;
        View minMessageChild = null;
        View minDateChild = null;
        int minBottom = Integer.MAX_VALUE;
        int minDateBottom = Integer.MAX_VALUE;
        for (int index = 0; index < listView.getChildCount(); index++) {
            View child = listView.getChildAt(index);
            int position = listView.getChildAdapterPosition(child);
            if (position < 0 || position >= bubbles.size()) {
                continue;
            }
            boolean dateRow = child instanceof org.telegram.ui.Cells.ChatActionCell
                    && bubbles.get(position).isDateObject;
            if (child.getBottom() <= clipTop) {
                // A date row that has slid under the top edge is not drawn there; the pill is.
                if (dateRow && child.getAlpha() != 0f) {
                    child.setAlpha(0f);
                }
                continue;
            }
            int bottom = child.getBottom();
            if (bottom < minBottom) {
                minBottom = bottom;
                minChild = child;
                minMessageChild = child instanceof ChatMessageCell
                        || child instanceof org.telegram.ui.Cells.ChatActionCell ? child : null;
            }
            if (dateRow && bottom < minDateBottom) {
                minDateBottom = bottom;
                minDateChild = child;
            }
        }
        if (minMessageChild != null) {
            MessageObject message = messageOf(minMessageChild);
            if (message != null && message.messageOwner != null) {
                floatingDate.setCustomDate(message.messageOwner.date, false, true);
            }
        }
        currentFloatingDateOnScreen = false;
        currentFloatingTopIsNotMessage = minChild == null
                || !(minChild instanceof ChatMessageCell
                        || minChild instanceof org.telegram.ui.Cells.ChatActionCell);
        if (minDateChild != null) {
            boolean showFloating = false;
            if (minDateChild.getY() > clipTop || currentFloatingTopIsNotMessage) {
                // The day's own row is still on screen: it is the date, and the pill steps aside.
                if (minDateChild.getAlpha() != 1f) {
                    minDateChild.setAlpha(1f);
                }
                hideFloatingDateView(!currentFloatingTopIsNotMessage);
            } else {
                if (minDateChild.getAlpha() != 0f) {
                    minDateChild.setAlpha(0f);
                }
                showFloating = true;
            }
            int pillHeight = floatingDate.getMeasuredHeight();
            float offset = minDateChild.getY() + minDateChild.getMeasuredHeight() - clipTop;
            if (pillHeight > 0 && offset > pillHeight && offset < pillHeight * 2) {
                floatingDateOffset = -pillHeight * 2 + offset;
            } else {
                floatingDateOffset = 0;
            }
            if (showFloating) {
                cancelFloatingDateAnimation();
                if (floatingDate.getTag() == null) {
                    floatingDate.setTag(1);
                }
                if (floatingDate.getAlpha() != 1f) {
                    floatingDate.setAlpha(1f);
                }
                currentFloatingDateOnScreen = true;
            }
        } else {
            hideFloatingDateView(true);
            floatingDateOffset = 0;
        }
        floatingDate.setTranslationY(
                listView.getY() + listPaddingTop + floatingDateOffset - AndroidUtilities.dp(4));
    }

    /** The message a row draws; null for a row this screen does not own. */
    private MessageObject messageOf(View view) {
        if (view instanceof ChatMessageCell) {
            return ((ChatMessageCell) view).getMessageObject();
        }
        if (view instanceof org.telegram.ui.Cells.ChatActionCell) {
            return ((org.telegram.ui.Cells.ChatActionCell) view).getMessageObject();
        }
        return null;
    }

    /** Brings the pill up, with upstream's own 150ms fade (ChatActivity 13604-13630). */
    private void showFloatingDateView() {
        if (floatingDate == null) {
            return;
        }
        if (floatingDate.getTag() == null) {
            cancelFloatingDateAnimation();
            floatingDate.setTag(1);
            floatingDateAnimation = new android.animation.AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(
                    android.animation.ObjectAnimator.ofFloat(floatingDate, View.ALPHA, 1f));
            floatingDateAnimation.start();
        }
    }

    /** Lets the pill go once the day's own row is back on screen (ChatActivity 13632-13657). */
    private void hideFloatingDateView(boolean animated) {
        if (floatingDate == null || floatingDate.getTag() == null || currentFloatingDateOnScreen
                || (scrollingFloatingDate && !currentFloatingTopIsNotMessage)) {
            return;
        }
        floatingDate.setTag(null);
        if (animated) {
            cancelFloatingDateAnimation();
            floatingDateAnimation = new android.animation.AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(
                    android.animation.ObjectAnimator.ofFloat(floatingDate, View.ALPHA, 0f));
            floatingDateAnimation.setStartDelay(500);
            floatingDateAnimation.start();
        } else {
            cancelFloatingDateAnimation();
            floatingDate.setAlpha(0f);
        }
    }

    private void cancelFloatingDateAnimation() {
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
    }

    /**
     * Builds one floor's bubbles, in body order: a run of markup per text segment, and one bubble
     * per poll. A segment with nothing to draw (whitespace between two polls, a body that is only a
     * poll) leaves no bubble behind. The floor's reactions go on the last bubble of the run.
     */
    private void buildBubbles(
            Post post, long topicId, List<MessageObject> outBubbles, List<PreparedPostBody> outBodies) {
        for (DiscoursePostBody.Segment segment : DiscoursePostBody.segments(post.getCookedHtml(), pollTitles(post))) {
            // Only the floor's first bubble carries the reply, whatever kind of bubble it is: a floor
            // whose body opens with a poll is as answerable as one that opens with a
            // paragraph. Upstream draws the quote once, at the top of the run, the way a chat shows it
            // on the message that answers.
            boolean firstBubble = outBubbles.isEmpty();
            TLRPC.Message replyTo = firstBubble ? replySnapshotFor(post) : null;
            MessageObject message;
            if (segment.isPoll()) {
                PostPoll poll = pollNamed(post, segment.pollName);
                if (poll == null) {
                    continue;
                }
                message = DiscoursePolls.message(post, poll, topicId, users, container.getBaseUrl(), replyTo);
                outBodies.add(null);
            } else {
                PreparedPostBody body = DiscourseImageBridge.INSTANCE.prepare(
                        segment.html, container.getBaseUrl(),
                        topicDetail == null ? Collections.emptyMap() : topicDetail.getEmojiCatalog());
                message = DiscourseRows.postMessage(
                        post, topicId, users, body, container.getBaseUrl(), replyTo);
                if (message.messageOwner.rich_message.blocks.isEmpty()
                        && message.messageOwner.rich_message.documents.isEmpty()) {
                    continue;
                }
                outBodies.add(body);
            }
            outBubbles.add(message);
            if (replyTo != null) {
                replyTargetFloors.put(message, post.getReplyToPostNumber());
            }
        }
        if (!outBubbles.isEmpty()) {
            MessageObject lastBubble = outBubbles.get(outBubbles.size() - 1);
            DiscourseRows.attachReactions(lastBubble, post);
            attachReplyFooter(lastBubble, post, topicId);
        }
    }

    /**
     * The reply footer on the floor's last bubble: how many direct replies the site says the floor
     * has ({@code reply_count}), and the faces of the direct replies this session has actually
     * loaded - the loaded floors whose {@code reply_to_post_number} names this floor.
     *
     * <p>The total is the site's, never the loaded subset, so a floor with more replies than are on
     * screen still says the real number. A reply the window does not carry contributes no face, and
     * none is invented. The footer goes on the last bubble only, so a body that splits into a run
     * of bubbles (text, a onebox, more text) draws it once. Nothing here reads the replies endpoint;
     * the replies themselves are read lazily, when the reader opens the floor's replies.</p>
     */
    private void attachReplyFooter(MessageObject bubble, Post post, long topicId) {
        if (bubble == null || post == null) {
            return;
        }
        int count = post.getReplyCount();
        if (count <= 0) {
            return;
        }
        Integer number = post.getPostNumber();
        List<TLRPC.Peer> repliers = new ArrayList<>();
        if (number != null) {
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (Integer floor : new java.util.TreeSet<>(postByNumber.keySet())) {
                Post other = postByNumber.get(floor);
                Integer parent = other == null ? null : other.getReplyToPostNumber();
                if (parent == null || !parent.equals(number)) {
                    continue;
                }
                long id = DiscourseRows.userId(other.getAuthorUsername());
                if (seen.add(id)) {
                    repliers.add(DiscourseRows.replierPeer(other.getAuthorUsername()));
                }
            }
        }
        DiscourseRows.attachReplyFooter(bubble, count, topicId, repliers);
    }

    /** The floor index whose post carries [number], or -1. */
    private int floorIndexForNumber(int number) {
        for (int floor = 0; floor < floors.size(); floor++) {
            Integer loaded = floors.get(floor).getPostNumber();
            if (loaded != null && loaded == number) {
                return floor;
            }
        }
        return -1;
    }

    /**
     * The target of [post]'s reply as the plain message its preview draws, or null when the floor
     * answers nobody or the target is not loaded yet.
     *
     * <p>The target is looked up by floor number, which is what a Discourse reply names; the target
     * floor's own text is read here so the preview shows the words, not just the author.</p>
     */
    private TLRPC.Message replySnapshotFor(Post post) {
        Integer number = post.getReplyToPostNumber();
        if (number == null || number <= 0 || container == null) {
            return null;
        }
        Post target = postByNumber.get(number);
        if (target == null) {
            return null;
        }
        return DiscourseRows.replySnapshot(
                target, topicId, users, container.getBaseUrl(), cookedPlainText(target));
    }

    /** A post's text, without its markup: what a reply preview shows of the target. */
    private static String cookedPlainText(Post post) {
        String cooked = post.getCookedHtml();
        return org.jsoup.Jsoup.parse(cooked == null ? "" : cooked).text();
    }

    /** The poll a segment names, from the post's own payload; null when the site sent no data. */
    private static PostPoll pollNamed(Post post, String name) {
        for (PostPoll poll : post.getPolls()) {
            if (poll.getName().equals(name)) {
                return poll;
            }
        }
        return null;
    }

    /**
     * The post's polls that the site gave a title to, by name. Their titles are drawn as the poll
     * bubble's question, so the copy the body carries next to the poll is dropped from the text.
     */
    private static HashMap<String, String> pollTitles(Post post) {
        HashMap<String, String> titles = new HashMap<>();
        for (PostPoll poll : post.getPolls()) {
            if (poll.getTitle() != null && !poll.getTitle().trim().isEmpty()) {
                titles.put(poll.getName(), poll.getTitle().trim());
            }
        }
        return titles;
    }

    /**
     * Reads the thread's next page when the reader reaches the end of what is loaded. Only a real
     * scroll asks (a layout-induced one is not the reader moving), and a page that failed is not
     * re-asked from here: the reader's own line is the retry, so a scroll cannot turn a broken read
     * into a loop. A thread whose rows all fit never scrolls; its line is how it is read on.
     */
    private void maybeLoadThreadReplies(int dy) {
        if (dy <= 0 || !threadMode || threadLoading || threadFailed || threadExhausted) {
            return;
        }
        if (container == null || listView == null || !threadHasMore()) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null || manager.findLastVisibleItemPosition() < bubbles.size() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        loadThreadPage();
    }

    /**
     * Fetches the next slice of the topic's stream once the list nears either end. The reader
     * scrolling down reaches the end of what is loaded and the page after it is fetched; the reader
     * scrolling up reaches the top of a window opened in the middle of a thread and the page before
     * it is fetched too, so the topic does not appear to begin where the reader left off - the way
     * Telegram's own chat loads near either end (ChatActivity.checkScrollForLoad:13773).
     */
    private void maybeLoadNextPage(int dy) {
        if (topicSearch != null) {
            return;
        }
        if (threadMode) {
            // A discussion page pages its replies, not the topic's stream.
            maybeLoadThreadReplies(dy);
            return;
        }
        if (reading.isLoadingWindow() || container == null || listView == null || bubbles.isEmpty()) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null) {
            return;
        }
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        if (first < 0 || last < 0) {
            return;
        }
        // Downward: near the end of what is loaded, the next slice of the stream is fetched. The
        // direction is latched after a failure (see [showPagingFailure]): every callback the finger
        // produces at the same edge would otherwise re-request the same broken page and say so again.
        // Leaving the edge re-arms it, so coming back can ask again.
        boolean nearBottom = last >= bubbles.size() - 1 - LOAD_MORE_THRESHOLD;
        if (dy != 0 && !nearBottom) {
            reading.clearForwardPageFailure();
        }
        if (nearBottom) {
            loadNextStreamPage();
        }
        // Upward: near the top of what is loaded, the slice before it is fetched too. The scroll's
        // direction is not the gate - proximity is - so a layout-induced dy of 0 still counts, and a
        // failure is latched the same way until the reader leaves the top edge.
        boolean nearTop = first <= LOAD_MORE_THRESHOLD;
        if (dy != 0 && !nearTop) {
            reading.clearBackwardPageFailure();
        }
        if (nearTop) {
            loadPreviousStreamPage();
        }
    }

    /** Appends the next slice of the stream below what is loaded. */
    private void loadNextStreamPage() {
        if (!reading.canStartForwardPage(streamPostIds.size())) {
            return;
        }
        final int generation = reading.getGeneration();
        final int end = Math.min(reading.getNextPageStart() + PAGE_SIZE, streamPostIds.size());
        // The slice that reaches the real end of the stream is the one the site answers the read-on
        // suggestion with: the head window of a topic a next page follows carries none.
        final boolean lastStreamPage = end >= streamPostIds.size();
        List<Long> page = new ArrayList<>(
                streamPostIds.subList(reading.getNextPageStart(), end));
        reading.startForwardPage();
        final long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        DiscourseLoader.INSTANCE.topicPosts(container, topicId, page, lastStreamPage, pagePayload -> {
            if (!reading.isCurrent(generation)) {
                // The window moved on while this page was on its way; the rows belong to a window
                // nobody is looking at. The flag is the new window's to reset, not this callback's.
                return kotlin.Unit.INSTANCE;
            }
            List<Post> loaded = pagePayload.getPosts();
            noteServedFromCache(showingCachedContent || pagePayload.getFromCache() || anyPostFromCache(loaded));
            reading.finishForwardPage(generation, end);
            int firstNew = bubbles.size();
            int floorsBefore = floors.size();
            // The page's floors are known before its bubbles are built, so a reply among them that
            // answers another new floor still finds its target.
            for (Post post : loaded) {
                Integer number = post.getPostNumber();
                if (number != null) {
                    postByNumber.put(number, post);
                }
            }
            for (Post post : loaded) {
                add(post, topicId);
            }
            // A reply loaded here may answer a floor an earlier page carried, and a reply an earlier
            // page carried may answer a floor loaded here; both are resolved from what is on screen.
            resolveMissingReplies();
            if (composerTools != null && topicDetail != null) {
                composerTools.setLocalCandidates(topicDetail.getParticipants(), floors);
            }
            registerUsersInController();
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemRangeInserted(firstNew, bubbles.size() - firstNew);
            }
            // A floor an earlier page carried may answer one this page just brought; its preview can
            // be drawn now, and only its own rows are rebound.
            for (Post post : loaded) {
                Integer number = post.getPostNumber();
                if (number != null) {
                    rebuildFloorsReplyingTo(number, floorsBefore, false);
                }
            }
            // And a floor the page just brought may answer one an earlier page carried: that floor's
            // reply footer gains the new face, so its own rows are rebuilt too.
            java.util.TreeSet<Integer> parentsToRefresh = new java.util.TreeSet<>();
            for (Post post : loaded) {
                if (post.getReplyToPostNumber() != null) {
                    parentsToRefresh.add(post.getReplyToPostNumber());
                }
            }
            for (Integer parent : parentsToRefresh) {
                int parentFloor = floorIndexForNumber(parent);
                if (parentFloor >= 0 && parentFloor < floorsBefore) {
                    // Keep footer rebuilds on paging's adapter update path alongside its new rows.
                    rebuildFloor(parentFloor);
                }
            }
            for (int index = firstNew; index < bubbles.size(); index++) {
                fetchPostMedia(index);
            }
            if (lastStreamPage) {
                // The real end of the stream: the site's own suggestion for what to read next came
                // with this page. Bind it without resetting the list, the header or the scroll.
                bindNextTopicCandidate(pagePayload.getSuggestedTopics(), topicId);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            // The topic is on screen, so the rows stay and the cursor does not move. The direction
            // is latched for this visit to the bottom edge; the hint carries the retry explicitly.
            reading.failForwardPage(generation);
            showPagingFailure(error, true);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Prepends the slice of the stream above what is loaded: the page before the window the reader
     * was dropped into, fetched by the stream's own ids. A failure keeps the rows already on screen
     * and is retried the next time the reader nears the top.
     */
    private void loadPreviousStreamPage() {
        if (!reading.canStartBackwardPage(streamPostIds.size())) {
            return;
        }
        final int generation = reading.getGeneration();
        final int start = Math.max(0, reading.getWindowStart() - PAGE_SIZE);
        List<Long> page = new ArrayList<>(
                streamPostIds.subList(start, reading.getWindowStart()));
        if (page.isEmpty()) {
            return;
        }
        reading.startBackwardPage();
        final long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        DiscourseLoader.INSTANCE.topicPosts(container, topicId, page, false, pagePayload -> {
            if (!reading.isCurrent(generation)) {
                // The window moved on while this page was on its way; the rows belong to a window
                // nobody is looking at.
                return kotlin.Unit.INSTANCE;
            }
            List<Post> loaded = pagePayload.getPosts();
            noteServedFromCache(showingCachedContent || pagePayload.getFromCache() || anyPostFromCache(loaded));
            reading.finishBackwardPage(generation, start);
            if (loaded == null || loaded.isEmpty()) {
                // The slice holds no visible posts (deleted, or not visible to this reader): the
                // cursor still moves past it, so the next approach asks for the slice above it
                // instead of asking for this empty one forever.
                return kotlin.Unit.INSTANCE;
            }
            prependStreamPage(loaded);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (!reading.isCurrent(generation)) {
                return kotlin.Unit.INSTANCE;
            }
            // The rows already on screen stay and the cursor does not move. The direction is latched
            // for this visit to the top edge; the hint carries the retry explicitly.
            reading.failBackwardPage(generation);
            showPagingFailure(error, false);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Puts the slice above the loaded window, keeping the reader exactly where they were. The rows
     * already drawn are not rebuilt - their MessageObjects, and anything opened on them, survive -
     * and the message that was at the top of the viewport is put back at the same pixel.
     */
    private void prependStreamPage(List<Post> loaded) {
        if (loaded == null || loaded.isEmpty() || listView == null) {
            return;
        }
        final long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        int anchorPosition = manager == null ? -1 : manager.findFirstVisibleItemPosition();
        MessageObject anchor = anchorPosition >= 0 && anchorPosition < bubbles.size()
                ? bubbles.get(anchorPosition) : null;
        // The floor the anchor draws and which of its rows the anchor is. A rebuild among the floors
        // below replaces the anchor's object, so the reader's place has to be restored by the floor
        // and the row within it, not by the identity of the object a rebuild happened to replace.
        int anchorFloor = anchor != null && anchorPosition < bubbleFloors.size()
                ? bubbleFloors.get(anchorPosition) : -1;
        Post anchorPost = anchorFloor >= 0 && anchorFloor < floors.size() ? floors.get(anchorFloor) : null;
        int anchorOrdinal = anchorFloor >= 0 ? anchorPosition - firstBubbleOfFloor(anchorPosition) : 0;
        View anchorView = anchorPosition < 0 || manager == null
                ? null : manager.findViewByPosition(anchorPosition);
        int anchorOffset = anchorView == null ? 0 : anchorView.getTop() - listView.getPaddingTop();

        // The slice's floors are known before its bubbles are built, so a reply among them that
        // answers another floor of the slice still finds its target.
        for (Post post : loaded) {
            Integer number = post.getPostNumber();
            if (number != null) {
                postByNumber.put(number, post);
            }
        }
        List<Post> newFloors = new ArrayList<>();
        List<MessageObject> newBubbles = new ArrayList<>();
        List<Integer> newBubbleFloors = new ArrayList<>();
        List<PreparedPostBody> newBubbleBodies = new ArrayList<>();
        for (Post post : loaded) {
            int floor = newFloors.size();
            newFloors.add(post);
            appendFloorRows(post, topicId, floor, newBubbles, newBubbleBodies, newBubbleFloors);
            // A slice is a load path of its own: a holographic title it brings arms the same frame
            // loop a floor bound with the window does.
            armAnimatedTitle(post);
            String avatarTemplate = post.getAuthorAvatarTemplate();
            if (avatarTemplate != null && !avatarTemplate.isEmpty()) {
                avatarTemplates.put(DiscourseRows.userId(post.getAuthorUsername()), avatarTemplate);
            }
        }
        int addedFloors = newFloors.size();
        int addedBubbles = newBubbles.size();

        // A slice whose last date row is the day the window already opens on leaves the window's
        // own date row inside that same day: the slice's row is the day's (it stands above the
        // day's first floor), so the old row goes and the seam carries one date row, not two. The
        // slice's last *row* is a floor's bubble, so the day is read from its last date row.
        int pageLastDay = -1;
        for (int index = newBubbles.size() - 1; index >= 0; index--) {
            MessageObject row = newBubbles.get(index);
            if (row.isDateObject && row.messageOwner != null) {
                pageLastDay = reading.dayKey(row.messageOwner.date);
                break;
            }
        }
        int windowOpeningDay = !bubbles.isEmpty() && bubbles.get(0).isDateObject
                && bubbles.get(0).messageOwner != null
                ? reading.dayKey(bubbles.get(0).messageOwner.date) : -1;
        if (reading.dropsOpeningDateRow(pageLastDay, windowOpeningDay)) {
            bubbles.remove(0);
            bubbleBodies.remove(0);
            bubbleFloors.remove(0);
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyItemRemoved(0);
            }
        }

        // Every floor already loaded now sits below the new ones: the floor indexes the loaded
        // bubbles hold are shifted by what was prepended. The bubbles themselves are untouched, so
        // the rows already drawn keep their own MessageObject and any state opened on them.
        for (int index = 0; index < bubbleFloors.size(); index++) {
            bubbleFloors.set(index, bubbleFloors.get(index) + addedFloors);
        }
        floors.addAll(0, newFloors);
        bubbles.addAll(0, newBubbles);
        bubbleBodies.addAll(0, newBubbleBodies);
        bubbleFloors.addAll(0, newBubbleFloors);

        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyItemRangeInserted(0, addedBubbles);
        }
        // A reply the new floors carry may answer a floor already on screen, and a floor already on
        // screen may answer one the new floors bring; both are resolved from what is loaded now.
        resolveMissingReplies();
        for (Post post : loaded) {
            Integer number = post.getPostNumber();
            if (number == null) {
                continue;
            }
            // Only the floors that were already on screen are rebuilt: a new floor was built with
            // its target already known, and rebuilding it would churn rows the reader just got.
            for (int floor = addedFloors; floor < floors.size(); floor++) {
                Integer replyTo = floors.get(floor).getReplyToPostNumber();
                if (replyTo != null && replyTo.equals(number)) {
                    // Same turn as the prepend's own insert: the rows are replaced, not re-bound.
                    rebuildFloor(floor);
                }
            }
        }
        java.util.TreeSet<Integer> parentsToRefresh = new java.util.TreeSet<>();
        for (Post post : loaded) {
            if (post.getReplyToPostNumber() != null) {
                parentsToRefresh.add(post.getReplyToPostNumber());
            }
        }
        for (Integer parent : parentsToRefresh) {
            int parentFloor = floorIndexForNumber(parent);
            if (parentFloor >= addedFloors) {
                // Same turn as the prepend's own insert: the rows are replaced, not re-bound.
                rebuildFloor(parentFloor);
            }
        }
        if (composerTools != null && topicDetail != null) {
            composerTools.setLocalCandidates(topicDetail.getParticipants(), floors);
        }
        registerUsersInController();
        for (int index = 0; index < addedBubbles; index++) {
            fetchPostMedia(index);
        }
        fetchAvatars();
        // The reader's place: the message that was on top is put back at the exact pixel it was at,
        // now that the rebuilds above may have changed the row counts around it.
        if (manager != null) {
            int at = anchor == null ? -1 : bubbles.indexOf(anchor);
            if (at < 0 && anchorPost != null) {
                // The anchor's row was rebuilt (or otherwise replaced). Find it again by the floor it
                // draws and the row within that floor, so the reader's place is kept even though the
                // object is not the one the scroll started from.
                int floorIndex = -1;
                for (int i = 0; i < floors.size(); i++) {
                    if (floors.get(i) == anchorPost) {
                        floorIndex = i;
                        break;
                    }
                }
                if (floorIndex >= 0) {
                    int first = -1;
                    for (int i = 0; i < bubbleFloors.size(); i++) {
                        // The floor's first bubble, not the date row that names the floor: the
                        // reader's place is a row of the floor's own content.
                        if (bubbleFloors.get(i) == floorIndex && !isDividerRow(i)) {
                            first = i;
                            break;
                        }
                    }
                    if (first >= 0) {
                        at = Math.min(first + anchorOrdinal, lastBubbleOfFloor(first));
                    }
                }
            }
            if (at >= 0) {
                manager.scrollToPositionWithOffset(at, anchorOffset);
            }
        }
    }

    private void toggleReaction(long postId, String reactionId) {
        if (container == null) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        DiscourseLoader.INSTANCE.toggleReaction(container, postId, reactionId, () -> {
            if (threadMode) {
                // The floor is a row of this discussion page: refresh the page's own root and
                // replies, rather than replace it with the topic's unrelated rows.
                reloadThread();
            } else {
                // The site is the only place that knows the new count, so the topic is read again.
                loadTopic(topicId);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showError("回应失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The poll a poll bubble draws, from its floor's own payload; null for any other bubble. */
    private PostPoll pollOf(MessageObject message) {
        int floor = floorOf(message);
        if (floor < 0 || message == null || message.messageOwner == null) {
            return null;
        }
        return DiscoursePolls.pollOf(floors.get(floor), MessageObject.getMedia(message.messageOwner));
    }

    /**
     * Sends one vote (or its retraction) and puts the site's answer back into the floor. The answer
     * is the only place the new counts and the reader's own selection are known - the payload the
     * screen loaded is already stale by then - so the floor's bubbles are built again from it and
     * only that floor's rows are rebound; the rest of the topic is not re-read.
     */
    private void vote(MessageObject message, PostPoll poll, PollVoteSelection selection) {
        int floor = floorOf(message);
        if (floor < 0 || container == null || poll == null || selection == null) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        Post post = floors.get(floor);
        DiscourseLoader.INSTANCE.votePoll(container, post.getId(), poll.getName(), selection, updated -> {
            if (updated != null) {
                floors.set(floor, post.withUpdatedPoll(updated));
                rebuildFloor(floor);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showError("投票失败", error);
            // Upstream's vote animation has already started; binding the row again is what ends it.
            rebuildFloor(floor);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Builds one floor's bubbles again and rebinds exactly its rows. The count can change (the
     * site's answer decides how many options a poll has), so the rows are replaced, not refreshed.
     */
    private void rebuildFloor(int floor) {
        int first = -1;
        int count = 0;
        for (int index = 0; index < bubbleFloors.size(); index++) {
            // A date row above the floor names the floor too; it is not one of the floor's rows.
            if (bubbleFloors.get(index) == floor && !isDividerRow(index)) {
                if (first < 0) {
                    first = index;
                }
                count++;
            }
        }
        if (first < 0) {
            return;
        }
        List<MessageObject> rebuilt = new ArrayList<>();
        List<PreparedPostBody> rebuiltBodies = new ArrayList<>();
        buildBubbles(floors.get(floor), topicId, rebuilt, rebuiltBodies);
        for (int index = 0; index < count; index++) {
            replyTargetFloors.remove(bubbles.get(first));
            bubbles.remove(first);
            bubbleBodies.remove(first);
            bubbleFloors.remove(first);
        }
        for (int index = 0; index < rebuilt.size(); index++) {
            bubbles.add(first + index, rebuilt.get(index));
            bubbleBodies.add(first + index, rebuiltBodies.get(index));
            bubbleFloors.add(first + index, floor);
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyItemRangeRemoved(first, count);
            listView.getAdapter().notifyItemRangeInserted(first, rebuilt.size());
        }
    }

    // A late reply target changes only the first bubble's preview. Keep the message and rich
    // layout so expanded details and in-flight media callbacks still belong to the visible row.
    private void refreshReplyInPlace(int floor) {
        TLRPC.Message reply = replySnapshotFor(floors.get(floor));
        if (reply == null) {
            return;
        }
        registerUsersInController();
        for (int index = 0; index < bubbleFloors.size(); index++) {
            if (bubbleFloors.get(index) == floor && !isDividerRow(index)) {
                MessageObject message = bubbles.get(index);
                DiscourseRows.applyReply(message.messageOwner, reply);
                message.replyMessageObject = DiscourseRows.messageObject(reply, users);
                replyTargetFloors.put(message, floors.get(floor).getReplyToPostNumber());
                rebindRowInPlace(index);
                return;
            }
        }
    }

    // Avoid DefaultItemAnimator's old/new-cell crossfade for a preview-only update.
    private void rebindRowInPlace(int at) {
        if (listView == null || at < 0 || at >= bubbles.size()) {
            return;
        }
        MessageObject message = bubbles.get(at);
        ChatMessageCell cell = null;
        // A page insertion may still be waiting for layout. Match the message, not its old position.
        for (int index = 0; index < listView.getChildCount(); index++) {
            View child = listView.getChildAt(index);
            if (child instanceof ChatMessageCell
                    && ((ChatMessageCell) child).getMessageObject() == message) {
                cell = (ChatMessageCell) child;
                break;
            }
        }
        if (cell == null) {
            return;
        }
        boolean pinnedTop = at > firstBubbleOfFloor(at);
        boolean pinnedBottom = at < lastBubbleOfFloor(at);
        // The message object is unchanged; force the cell to re-read its reply fields.
        cell.forceResetMessageObject();
        cell.setMessageObject(message, null, pinnedBottom, pinnedTop, false);
        if (message != null && message.richLayout != null) {
            message.richLayout.attach(cell);
        }
        cell.requestLayout();
        cell.invalidate();
    }

    /**
     * What the reader can do with a post: copy its text, copy a link to it, bookmark it, or reply
     * to it. The choices are upstream's own scrim popup, anchored on the bubble that was pressed —
     * the shape a chat uses for a message's menu — and the site's reactions lead it as upstream's
     * own rail, a sibling bubble above the rows rather than a row inside them, so the reader swipes
     * every reaction the site permits and a tap answers with the reaction's site id.
     */
    private void showMessageMenu(android.view.View scrim, int floor) {
        if (scrim == null) {
            return;
        }
        showMessageMenu(scrim, floor, scrim.getWidth() / 2f, scrim.getHeight() / 2f);
    }

    private void showMessageMenu(android.view.View scrim, int floor, float pressX, float pressY) {
        if (getParentActivity() == null || scrim == null || floor < 0 || floor >= floors.size()) {
            return;
        }
        boolean bookmarked = floors.get(floor).isBookmarked();
        final Post reactionPost = floors.get(floor);
        java.util.List<DiscourseReactionUi.Action> actions = new java.util.ArrayList<>();
        actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_copy, "复制",
                () -> AndroidUtilities.addToClipboard(plainText(floor))));
        actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_link, "复制链接", () -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", postUrl(floor)));
        }));
        actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_share, "分享",
                () -> sharePost(floor)));
        actions.add(new DiscourseReactionUi.Action(
                bookmarked
                        ? org.telegram.messenger.R.drawable.msg_unfave
                        : org.telegram.messenger.R.drawable.msg_fave,
                bookmarked ? "取消书签" : "添加书签",
                () -> toggleBookmark(floor)));
        actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.menu_reply, "回复",
                () -> replyTo(floor)));
        // The reader's own post: what the site says they may do to it, never a username. A field
        // the site did not send (or sent false) leaves that action off rather than offering a write
        // the site would refuse.
        if (reactionPost.getCanEdit()) {
            actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_edit, "编辑",
                    () -> startEdit(floor)));
        }
        if (reactionPost.getCanRecover()) {
            actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.chats_undo, "恢复",
                    () -> recoverFloor(floor)));
        }
        if (reactionPost.getCanDelete()) {
            actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_delete, "删除",
                    () -> confirmDelete(floor)));
        }
        if (scrim instanceof ChatMessageCell && hasSelectableText((ChatMessageCell) scrim)) {
            actions.add(new DiscourseReactionUi.Action(org.telegram.messenger.R.drawable.msg_select, "选择文本",
                    () -> startTextSelection((ChatMessageCell) scrim)));
        }
        if (messageMenu != null) {
            messageMenu.dismiss();
        }
        messageMenu = DiscourseReactionUi.showMenu(this, container, scrim,
                scrim instanceof ChatMessageCell ? ((ChatMessageCell) scrim).getMessageObject() : null,
                topicDetail, reactionPost, actions, bottomInputHeight(), pressX, pressY, id -> {
                    toggleReaction(reactionPost.getId(), id);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * Whether the bubble draws text a selection can take. A poll bubble has none of its own - its
     * answers are buttons - and neither has a bubble whose body was empty.
     */
    private static boolean hasSelectableText(ChatMessageCell cell) {
        return cell.getMessageObject() != null
                && cell.getMessageObject().richLayout != null
                && !cell.getMessageObject().richLayout.textBlocks.isEmpty()
                && cell.getMessageObject().richLayout.joinedText.length() > 0;
    }

    /**
     * The reader asked for the post's text. Upstream starts a selection by holding a finger on a
     * message's text, and the message has to be in the state its menu put it in; our menu is a popup
     * that owns the screen while it is up, so the menu is where the selection starts: the whole text
     * is picked, and the handles are what narrows it. From there the overlay is upstream's own.
     *
     * <p>A post's body is a rich layout, and the helper draws a selection over one only while it is
     * told so — a touch sets that flag on its way in, and a selection started from the menu has to
     * set it itself, or there is no highlight and the handles are measured against the wrong
     * layout. The cell is asked to draw again for the same reason: its draw pass is what hands the
     * helper the text's position inside it.</p>
     */
    private void startTextSelection(ChatMessageCell cell) {
        if (textSelectionHelper == null || cell == null || cell.getMessageObject() == null
                || cell.getMessageObject().richLayout == null) {
            return;
        }
        CharSequence text = cell.getMessageObject().richLayout.joinedText;
        if (text == null || text.length() == 0) {
            return;
        }
        textSelectionHelper.isRich = true;
        textSelectionHelper.select(cell, 0, text.length());
        cell.invalidate();
    }

    /**
     * The reader's bookmark on one floor, from where it is to where it is not (and back). What the
     * site answered is what the floor keeps, so the menu that offered to add a bookmark offers to
     * take it back the next time it is opened.
     */
    private void toggleBookmark(int floor) {
        if (container == null || floor < 0 || floor >= floors.size()) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        Post post = floors.get(floor);
        boolean bookmarked = !post.isBookmarked();
        DiscourseLoader.INSTANCE.setBookmark(container, post.getId(), bookmarked, () -> {
            floors.set(floor, post.withBookmark(bookmarked));
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showError("书签失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** What a copy takes: the floor's own text, without its markup. */
    private String plainText(int floor) {
        String cooked = floors.get(floor).getCookedHtml();
        return org.jsoup.Jsoup.parse(cooked == null ? "" : cooked).text();
    }

    private String postUrl(int floor) {
        if (container == null || floor < 0 || floor >= floors.size()) {
            return container == null ? "" : container.getBaseUrl();
        }
        // The address names the post by its site number (`t/<topic>/<floor>`), not its id: the id is
        // not a floor, and a link built from it resolves to nothing. The site always sends the
        // number; the list's own 1-based position is only a fallback for a response that omits it.
        Integer number = floors.get(floor).getPostNumber();
        int address = number == null || number <= 0 ? floor + 1 : number;
        return container.getBaseUrl() + "t/" + topicId + "/" + address;
    }

    /** The floor a bubble draws; -1 when the message is not one of the list's bubbles. */
    private int floorOf(MessageObject message) {
        if (message == null || message.isDateObject) {
            return -1;
        }
        int index = bubbles.indexOf(message);
        return index >= 0 && index < bubbleFloors.size() ? bubbleFloors.get(index) : -1;
    }

    /**
     * What the composer's own state decides for the reader: the send button and our attach strip.
     * The button is the send arrow while the field has text and this client's own microphone while
     * it is empty, so it is offered whenever either exists - upstream draws the right one from the
     * field's own state in {@code checkSendButton}. It is told after every change upstream makes,
     * because those are the moments it decides the button's state again.
     */
    private void updateComposerActions() {
        updateSendButtonVisibility();
        updateAttachStrip();
    }

    private void updateSendButtonVisibility() {
        if (composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null
                && composer.getEditField().length() > 0;
        // Editing an existing post has no microphone: that field is a post, not a new reply.
        boolean hasMicrophone = composerTools != null && composerTools.isVoiceReady() && editFloor < 0;
        android.view.View button = composer.getSendButton();
        if (button == null) {
            return;
        }
        int wanted = hasText || hasMicrophone ? android.view.View.VISIBLE : android.view.View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
        }
        // The composer's own decision runs last: it is what draws the send arrow (or the microphone)
        // into the button, and our visibility write above is only for the shape of the button's
        // absence. See the same comment on the new-topic screen: a change that arrives in one piece
        // (a paste, a draft) would otherwise be drawn from the state before it.
        composer.checkSendButton(false);
    }

    /** The screen's own voice host, so a test drives the real record/review/upload contract. */
    ComposerVoiceController voiceControllerForTest() {
        return composerTools == null ? null : composerTools.getVoice();
    }

    /**
     * The key a draft is stored under: the site, the account and the topic. A reader with two
     * accounts, or the same account on another site, never inherits a draft that is not theirs.
     */
    private TopicReplyDraftKey draftKey() {
        if (replyDraftKey != null) {
            return replyDraftKey;
        }
        String username = "";
        if (container != null) {
            SessionSnapshot snapshot = container.getSessionStore().snapshot();
            if (snapshot != null) {
                username = snapshot.getUsername();
            }
        }
        // A discussion page writes to its own field: the root floor scopes the key, so the text of
        // a thread never overwrites the topic's draft, and two threads of one topic never share one.
        Integer rootScope = isThreadMode() && threadParentFloor > 0 ? threadParentFloor : null;
        return replyDraftKey = new TopicReplyDraftKey(
                container == null ? "" : container.getBaseUrl(), username, topicId, rootScope);
    }

    /** Puts back what the reader left: the text and, if they were answering a floor, the target. */
    private void loadDraft() {
        if (container == null || composer == null) {
            return;
        }
        TopicReplyDraft draft = container.getTopicReplyDraftStore().load(draftKey());
        // Only what the reader actually stored. A discussion page does not pre-set its root floor as
        // the target, so no reply strip is opened by default (upstream's own discussion composer
        // shows none either); the root floor is chosen at send time instead.
        replyToPostNumber = draft.getReplyToPostNumber();
        if (!draft.getRaw().isEmpty()) {
            restoringDraft = true;
            try {
                composer.setFieldText(draft.getRaw());
            } finally {
                restoringDraft = false;
            }
            updateComposerActions();
        }
    }

    /**
     * Writes the field and the target down. A restore does not write: the value it would write is
     * the one that was just read.
     */
    private void saveDraft() {
        if (container == null || composer == null || restoringDraft) {
            return;
        }
        if (editFloor >= 0) {
            // The field holds the post being edited, not a reply: the reader's reply draft is kept
            // aside and must not be overwritten with the edit's markdown.
            return;
        }
        CharSequence text = composer.getFieldText();
        String raw = text == null ? "" : text.toString();
        container.getTopicReplyDraftStore().save(draftKey(), new TopicReplyDraft(raw, replyToPostNumber));
    }

    /** Only a write the site accepted earns this; a failure keeps the draft for the next press. */
    private void clearDraft() {
        if (container == null) {
            return;
        }
        container.getTopicReplyDraftStore().save(draftKey(), new TopicReplyDraft("", null));
        this.replyToPostNumber = null;
        if (replyPanel != null) {
            replyPanel.hide();
        }
    }

    /**
     * Answers one post. The target is the native reply strip above the field - upstream's own reply
     * panel - not text written into the field: a draft the reader already typed is never overwritten
     * to insert an {@code @name}, and cancelling the strip leaves the draft exactly as it was.
     */
    private void replyTo(int floor) {
        if (floor < 0 || floor >= floors.size()) {
            return;
        }
        Post post = floors.get(floor);
        Integer number = post.getPostNumber();
        replyToPostNumber = number;
        if (composer != null && number != null) {
            ensureReplyPanel();
            showReplyTarget(number, post);
            composer.setFieldFocused();
            AndroidUtilities.showKeyboard(composer.getEditField());
        }
        saveDraft();
    }

    /** Creates the reply strip on first use, so a screen that never replies adds no extra view. */
    private void ensureReplyPanel() {
        if (replyPanel == null && composer != null) {
            replyPanel = new DiscourseReplyPanel(composer, resourceProvider, () -> {
                if (editFloor >= 0) {
                    // The strip is the edit's own close button while an edit is open: the reader's
                    // reply comes back exactly as it was.
                    cancelEdit();
                    return;
                }
                // The close button: the reader is no longer answering, so the target goes with the
                // strip and the draft (text and target together) is written down.
                replyToPostNumber = null;
                if (replyPanel != null) {
                    replyPanel.hide();
                }
                saveDraft();
            });
        }
    }

    /** Fills the strip for a target whose post is loaded, and shows it. */
    private void showReplyTarget(int number, Post target) {
        if (replyPanel == null) {
            return;
        }
        replyPanel.show(replyName(target), cookedPlainText(target), () -> jumpToFloor(number));
    }

    /**
     * Puts the composer's strip back to match the state the field is still in - upstream's own name
     * for exactly this (ChatActivity.fallbackFieldPanel, 10940).
     *
     * <p>Upstream's enter view takes the reply/edit strip down at the start of <em>every</em> send
     * (the send runnable calls {@code hideTopView(true)} before the delegate is asked,
     * ChatActivityEnterView 7396-7412) and a Telegram send that fails leaves the field empty with
     * the failure drawn as a retry row in the list, so ChatActivity never has to put it back. Here
     * the reader's text and target deliberately stay in the field instead, so a send that did not
     * land has to put the strip back with them: otherwise a reader still answering a floor sees
     * nothing saying the next send will target it, and one still editing a post loses the strip's
     * close button - the only way out of edit mode.</p>
     */
    private void fallbackFieldPanel() {
        if (composer == null) {
            return;
        }
        if (editFloor >= 0) {
            ensureReplyPanel();
            if (replyPanel != null) {
                Integer number = editPostNumber;
                // The strip is the edit's own label and its close button is the cancel, labelled
                // for the edit rather than for a reply.
                replyPanel.showEdit("编辑 #" + (number == null ? "?" : number.toString()));
            }
            return;
        }
        if (replyToPostNumber != null) {
            applyReplyTarget();
            return;
        }
        if (replyPanel != null) {
            replyPanel.hide();
        }
    }

    /** The name the reply strip shows for a post: its author's display name, else their username. */
    private static String replyName(Post post) {
        String display = post.getAuthorDisplayName();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }
        String username = post.getAuthorUsername();
        return username == null ? "" : username;
    }

    /**
     * Shows the reply strip for the target a restored draft carried. Called once the posts are bound,
     * so a target that is loaded is named properly; one that is not is named by its floor number and
     * then read from the site, so the strip shows the real author rather than the number forever.
     *
     * <p>The read belongs to this target only: if the reader cancels the strip or retargets it, or
     * the window rebinds, the late answer is dropped and the current target is left alone.</p>
     */
    private void applyReplyTarget() {
        Integer number = replyToPostNumber;
        if (number == null || number <= 0 || composer == null) {
            return;
        }
        ensureReplyPanel();
        Post target = postByNumber.get(number);
        if (target != null) {
            showReplyTarget(number, target);
            return;
        }
        if (replyPanel == null || container == null) {
            return;
        }
        replyPanel.show("回复 #" + number, "", () -> jumpToFloor(number));
        final int generation = bindGeneration;
        final int targetNumber = number;
        DiscourseLoader.INSTANCE.resolvePost(container, topicId, targetNumber, resolved -> {
            if (generation != bindGeneration) {
                return kotlin.Unit.INSTANCE;
            }
            Integer current = replyToPostNumber;
            if (current == null || current != targetNumber) {
                // The reader cancelled or retargeted the strip while this was in flight.
                return kotlin.Unit.INSTANCE;
            }
            if (resolved != null && resolved.getPostNumber() != null
                    && resolved.getPostNumber() == targetNumber) {
                postByNumber.put(targetNumber, resolved);
                registerUsersInController();
                showReplyTarget(targetNumber, resolved);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            // The read failed; the strip keeps naming the floor, and the reply itself is unaffected.
            return kotlin.Unit.INSTANCE;
        });
    }

    private void sendReply(String raw, Integer replyToPostNumber) {
        if (container == null || raw.trim().isEmpty()) {
            return;
        }
        if (denyWriteWhileOffline()) {
            // The composer clears its own field before handing the text over, so a refused send has
            // to put it back; the reader can press send again once the site is reachable.
            if (composer != null && !raw.trim().isEmpty()) {
                CharSequence current = composer.getFieldText();
                if (current == null || current.length() == 0) {
                    composer.setFieldText(raw);
                }
            }
            // The enter view took the reply strip down on its way out; the target is still the one
            // this press was for, so the strip comes back with the text.
            fallbackFieldPanel();
            return;
        }
        DiscourseLoader.INSTANCE.reply(container, topicId, raw, replyToPostNumber, () -> {
            if (composer != null) {
                composer.setFieldText("");
            }
            // The site took it, so there is nothing left to remember. Only this path clears the
            // draft; a failure below keeps it.
            clearDraft();
            if (threadMode) {
                // The accepted reply belongs to this thread: read its replies again rather than
                // replace the page with the topic.
                reloadThread();
            } else {
                loadTopic(topicId);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            // The composer clears its own field before it hands the text over, so a write that did
            // not land would lose what the reader wrote. Put it back: the reader can press send
            // again, and nothing here resends a write whose outcome is unknown.
            if (composer != null && !raw.trim().isEmpty()) {
                CharSequence current = composer.getFieldText();
                if (current == null || current.length() == 0) {
                    composer.setFieldText(raw);
                }
            }
            // The write did not land, so the draft - text and target - is written down again for
            // the next open.
            saveDraft();
            // The enter view took the strip down on its way out and the target is still set: put it
            // back with the text, or the reader is answering a floor with nothing saying so while
            // the next send still targets it.
            fallbackFieldPanel();
            showReplyError(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void showReplyError(Throwable error) {
        showError("回复失败", error);
    }

    // ---- the reader's own posts: edit, delete, recover ----

    /** The account this screen is reading as, so a write's answer is only drawn for its author. */
    private String currentUsername() {
        if (container == null) {
            return "";
        }
        SessionSnapshot snapshot = container.getSessionStore().snapshot();
        return snapshot == null ? "" : snapshot.getUsername();
    }

    private boolean sameAccount(String username) {
        return username.equals(currentUsername());
    }

    /**
     * The account the field's draft belongs to. The reply draft key is resolved when the field is
     * opened and it carries the site, the account and the topic, so a write is that account's -
     * never whoever happens to be signed in when the callback lands.
     */
    private String draftAccount() {
        return container == null ? "" : draftKey().getUsername();
    }

    /**
     * The session's operation generation right now. A logout and a fresh login of the same username
     * bump it, so comparing it on an answer tells that apart from the write's own session - which a
     * username comparison alone cannot.
     */
    private int sessionGeneration() {
        if (container == null) {
            return Integer.MIN_VALUE;
        }
        return container.getSessionCoordinator().currentRequestContext().getGeneration();
    }

    /** Whether the session a write went out under is still the live one. */
    private boolean sameSession(int generation) {
        return container != null
                && container.getSessionCoordinator().currentRequestContext().getGeneration() == generation;
    }

    /**
     * Whether a write's answer still belongs to the reader who sent it: the same session generation
     * and the same account. A screen that has left, a window that reloaded, a different account, and
     * a logout-and-login of the same name are all answered false, so the answer is dropped rather
     * than drawn for the wrong reader.
     */
    private boolean writeStillOwned(int generation, String account) {
        return sameSession(generation) && sameAccount(account);
    }

    /** What the composer's field holds right now, or "" when there is no field. */
    private String fieldText() {
        if (composer == null) {
            return "";
        }
        CharSequence text = composer.getFieldText();
        return text == null ? "" : text.toString();
    }

    /** Puts [text] into the field without letting the restore be written back as a reply draft. */
    private void setFieldTextSilently(String text) {
        if (composer == null) {
            return;
        }
        restoringDraft = true;
        try {
            composer.setFieldText(text);
        } finally {
            restoringDraft = false;
        }
    }

    /**
     * The reader asked to edit one of their own posts. Only the site's own {@code can_edit} opens
     * this - a username is never enough. The markdown is not in the topic window (that carries
     * cooked bodies), so the post's own read supplies it and the editor opens on that, never on the
     * cooked text.
     */
    private void startEdit(int floor) {
        if (container == null || getParentActivity() == null
                || floor < 0 || floor >= floors.size()
                || editFloor >= 0 || sendingEdit) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        final Post post = floors.get(floor);
        if (!post.getCanEdit()) {
            return;
        }
        final Integer number = post.getPostNumber();
        final String account = draftAccount();
        final int session = sessionGeneration();
        // A new edit owns the callbacks it starts; a later one, a cancel, or the screen leaving
        // drops this read instead of drawing into whatever replaced it.
        final int generation = ++editGeneration;
        DiscourseLoader.INSTANCE.fetchPost(container, post.getId(), loaded -> {
            if (generation != editGeneration || !writeStillOwned(session, account)
                    || getParentActivity() == null || composer == null) {
                return kotlin.Unit.INSTANCE;
            }
            if (!loaded.getCanEdit()) {
                // The site no longer offers the edit; nothing is opened.
                return kotlin.Unit.INSTANCE;
            }
            // A reload under the read can have shifted the window: the editor opens on the floor
            // the post's own number names now, not the index it sat at when the read began.
            int resolved = number == null
                    ? (floor < floors.size() ? floor : -1)
                    : floorIndexForNumber(number);
            if (resolved < 0) {
                // The post is no longer in the window; there is nothing to open the editor on.
                return kotlin.Unit.INSTANCE;
            }
            beginEditing(resolved, loaded);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (generation == editGeneration && writeStillOwned(session, account)) {
                showError("编辑失败", error);
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Puts the post's markdown into the field and switches the composer into edit mode. The reply
     * the field held is set aside first: cancelling the edit, or finishing it, hands that reply
     * back exactly as it was.
     */
    private void beginEditing(int floor, Post post) {
        if (composer == null) {
            return;
        }
        editReplyBackup = new TopicReplyDraft(fieldText(), replyToPostNumber);
        replyToPostNumber = null;
        editFloor = floor;
        editPostId = post.getId();
        editPostNumber = post.getPostNumber();
        setFieldTextSilently(post.getRaw() == null ? "" : post.getRaw());
        updateComposerActions();
        // The strip is the edit's own label and its close button is the cancel; the same call a send
        // that did not land uses to put it back.
        fallbackFieldPanel();
        composer.setFieldFocused();
        AndroidUtilities.showKeyboard(composer.getEditField());
    }

    /** The strip's close button while an edit is open: the reader's own reply comes back. */
    private void cancelEdit() {
        if (editFloor < 0 || sendingEdit) {
            return;
        }
        exitEditMode();
    }

    /**
     * Ends edit mode and hands the reader's reply back. Both a cancel and a successful edit land
     * here: on success the screen has already replaced the floor with the site's own post, and the
     * reply the reader was writing is theirs, not the edit's.
     */
    private void exitEditMode() {
        editFloor = -1;
        editPostId = -1;
        editPostNumber = null;
        if (replyPanel != null) {
            replyPanel.hide();
        }
        TopicReplyDraft backup = editReplyBackup;
        editReplyBackup = null;
        setFieldTextSilently(backup == null ? "" : backup.getRaw());
        replyToPostNumber = backup == null ? null : backup.getReplyToPostNumber();
        if (replyToPostNumber != null) {
            applyReplyTarget();
        }
        updateComposerActions();
        saveDraft();
    }

    /**
     * Sends the edit. A second press while one is out is refused (one press, one write), and the
     * answer is only applied while the same account is still signed in and this screen is alive.
     */
    private void sendEdit(String raw) {
        if (container == null || editFloor < 0 || sendingEdit || raw.trim().isEmpty()) {
            return;
        }
        if (denyWriteWhileOffline()) {
            // The enter view cleared the field before it handed the text over, so a refused send has
            // to put the edit back: the reader keeps editing the same post, with the same text and
            // the same strip - whose close button is the only way out of edit mode.
            if (composer != null && fieldText().isEmpty()) {
                setFieldTextSilently(raw);
            }
            updateComposerActions();
            fallbackFieldPanel();
            return;
        }
        final long postId = editPostId;
        final Integer number = editPostNumber;
        if (postId <= 0) {
            return;
        }
        final String account = draftAccount();
        if (!sameAccount(account)) {
            // The field's draft belongs to another reader than the one signed in; the write is not
            // this reader's to send.
            return;
        }
        final int session = sessionGeneration();
        final int generation = editGeneration;
        sendingEdit = true;
        updateComposerActions();
        DiscourseLoader.INSTANCE.editPost(container, postId, raw, null, updated -> {
            if (generation != editGeneration) {
                return kotlin.Unit.INSTANCE;
            }
            if (!writeStillOwned(session, account)) {
                // The signed-in reader changed under the write; the answer is not theirs to draw.
                sendingEdit = false;
                return kotlin.Unit.INSTANCE;
            }
            sendingEdit = false;
            applyServerPost(number, updated);
            exitEditMode();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (generation != editGeneration) {
                return kotlin.Unit.INSTANCE;
            }
            sendingEdit = false;
            if (!writeStillOwned(session, account)) {
                // The refusal belongs to the reader who sent it, not to whoever is here now.
                return kotlin.Unit.INSTANCE;
            }
            // The write did not land: the edit text stays in the field so the reader can send again,
            // and the strip comes back with it - the enter view took it down on the way out, and its
            // close button is the only way out of edit mode.
            if (composer != null && fieldText().isEmpty()) {
                setFieldTextSilently(raw);
            }
            updateComposerActions();
            fallbackFieldPanel();
            showError("编辑失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Replaces the floor with the post the site answered - its cooked body and its refreshed
     * permissions - and rebinds exactly that floor's rows. The floor is found by its own post number
     * so a window that changed under the write cannot apply the answer to the wrong row, and nothing
     * here derives cooked from raw.
     */
    private void applyServerPost(Integer postNumber, Post post) {
        if (postNumber == null || post == null) {
            return;
        }
        int floor = floorIndexForNumber(postNumber);
        if (floor < 0) {
            return;
        }
        floors.set(floor, post);
        postByNumber.put(postNumber, post);
        rebuildFloor(floor);
        // The site's own cook can name media the floor did not draw before (an edit that added a
        // picture): the rebuilt rows are handed to the same download path every other row goes
        // through, so what the site cooked actually loads rather than leaving a placeholder.
        refreshFloorMedia(floor);
    }

    /** Hands one floor's rebuilt rows to the media downloader, the path a freshly loaded row uses. */
    private void refreshFloorMedia(int floor) {
        for (int index = 0; index < bubbles.size(); index++) {
            if (bubbleFloors.get(index) == floor && !isDividerRow(index)) {
                fetchPostMedia(index);
            }
        }
    }

    /** The reader's delete, behind the confirmation upstream's own AlertDialog draws. */
    private void confirmDelete(int floor) {
        if (getParentActivity() == null || floor < 0 || floor >= floors.size() || sendingDelete) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        if (!floors.get(floor).getCanDelete()) {
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("删除帖子");
        builder.setMessage("删除后其他人将看不到这篇帖子。确定删除吗？");
        builder.setPositiveButton("删除", (dialog, which) -> deleteFloor(floor));
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    private void deleteFloor(int floor) {
        if (container == null || floor < 0 || floor >= floors.size() || sendingDelete) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        final Post post = floors.get(floor);
        if (!post.getCanDelete()) {
            return;
        }
        final long postId = post.getId();
        final Integer number = post.getPostNumber();
        final String account = currentUsername();
        final int session = sessionGeneration();
        final int generation = bindGeneration;
        sendingDelete = true;
        DiscourseLoader.INSTANCE.deletePost(container, postId, () -> {
            if (generation != bindGeneration || !writeStillOwned(session, account)) {
                sendingDelete = false;
                return kotlin.Unit.INSTANCE;
            }
            // The site removed it (its own 204 carries no body). Its permissions are the site's,
            // not ours to guess: the row is refreshed from the post's own read rather than a
            // made-up can_recover. The flag stays set until that read settles, so one confirmation
            // still means one delete and the readback cannot race a second one.
            DiscourseLoader.INSTANCE.fetchPost(container, postId, refreshed -> {
                if (generation != bindGeneration || !writeStillOwned(session, account)) {
                    sendingDelete = false;
                    return kotlin.Unit.INSTANCE;
                }
                sendingDelete = false;
                applyServerPost(number, refreshed);
                return kotlin.Unit.INSTANCE;
            }, readError -> {
                if (generation != bindGeneration || !writeStillOwned(session, account)) {
                    sendingDelete = false;
                    return kotlin.Unit.INSTANCE;
                }
                sendingDelete = false;
                // The delete landed but its readback did not: the row cannot be refreshed from an
                // answer nobody got. This is not a refusal - nothing here resends the delete - and
                // the reader is offered a reload that reads the topic again.
                showDeleteRefreshFailure();
                return kotlin.Unit.INSTANCE;
            });
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (generation != bindGeneration || !writeStillOwned(session, account)) {
                sendingDelete = false;
                return kotlin.Unit.INSTANCE;
            }
            sendingDelete = false;
            // The site refused; the post is exactly as it was.
            showError("删除失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The reader's undelete: the site answers the recovered post, which replaces the floor. */
    private void recoverFloor(int floor) {
        if (container == null || floor < 0 || floor >= floors.size() || sendingDelete) {
            return;
        }
        if (denyWriteWhileOffline()) {
            return;
        }
        final Post post = floors.get(floor);
        if (!post.getCanRecover()) {
            return;
        }
        final Integer number = post.getPostNumber();
        final String account = currentUsername();
        final int session = sessionGeneration();
        final int generation = bindGeneration;
        sendingDelete = true;
        DiscourseLoader.INSTANCE.recoverPost(container, post.getId(), recovered -> {
            if (generation != bindGeneration || !writeStillOwned(session, account)) {
                sendingDelete = false;
                return kotlin.Unit.INSTANCE;
            }
            sendingDelete = false;
            applyServerPost(number, recovered);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (generation != bindGeneration || !writeStillOwned(session, account)) {
                sendingDelete = false;
                return kotlin.Unit.INSTANCE;
            }
            sendingDelete = false;
            // The site refused; the post keeps the permission it had and is exactly as it was.
            showError("恢复失败", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The delete landed (the site's own 204) but the post could not be read back. The row cannot be
     * refreshed from an answer nobody got, and this is deliberately not the refusal dialog: nothing
     * here resends the delete and the delete is not reported as failed. The reader's way out is a
     * fresh read of the topic, which either shows the post as the site keeps it or - if the site
     * never really took the write - shows it as it is. The reload is the only action.
     */
    private void showDeleteRefreshFailure() {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("帖子已删除");
        builder.setMessage("帖子已删除，但没能读到它的最新状态。重新加载后可以看到。");
        builder.setPositiveButton("重新加载", (dialog, which) -> {
            if (container != null && topicId > 0) {
                loadTopic(topicId);
            }
        });
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    private void showError(String title, Throwable error) {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.messenger.FileLog.e(title, error);
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(error.getMessage() == null ? error.toString() : error.getMessage());
        TopicLoadFailure reason = TopicLoadFailure.of(error);
        if (reason == TopicLoadFailure.Challenge || reason == TopicLoadFailure.LoginRequired) {
            // The way through is the verification page, and it is the reader who takes it: the write
            // this dialog is about has an unknown outcome, so nothing replays it. The draft is in
            // the field and the reader's own press sends it again.
            builder.setPositiveButton(
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            io.github.baba983.nativedo.R.string.reply_failed_verify),
                    (dialog, which) -> openVerificationPage());
            builder.setNegativeButton("取消", null);
        } else {
            builder.setPositiveButton("好", null);
        }
        showDialog(builder.create());
    }

    /**
     * The verification page, the same WebView the app logs in with, with no retry behind it: solving
     * the challenge there is what makes the site answer the app again, and the write that was left
     * uncertain is sent again only by the reader. The session is kept - this is a detour through the
     * site, not a logout.
     */
    private void openVerificationPage() {
        if (getParentActivity() == null) {
            return;
        }
        io.github.baba983.nativedo.auth.LoginWebViewFragment[] holder =
                new io.github.baba983.nativedo.auth.LoginWebViewFragment[1];
        holder[0] = new io.github.baba983.nativedo.auth.LoginWebViewFragment(() -> {
            holder[0].finishFragment();
            if (reporter != null) {
                reporter.onSessionRecovered();
            }
        }, true);
        presentFragment(holder[0]);
    }

    /**
     * A read that did not arrive. While the topic is empty the whole screen says so and a tap acts
     * on it; a page that failed with the topic already on screen is said as a dialog, because the
     * rows the reader has are still good.
     */
    private void showTopicFailure(Throwable error) {
        showFailure(error, io.github.baba983.nativedo.R.string.topic_page_failed, "topic failed");
    }

    /**
     * A stream page that did not arrive while the topic is on screen. The rows, the cursor and the
     * reader's place all stay; the failure is said in passing - upstream's own Bulletin, anchored
     * above the composer - and that hint carries the retry as an explicit action, so a reader who
     * does not think to scroll still has a way to bring the page back.
     *
     * <p>The hint owns its own error and action. It deliberately does not write the screen-level
     * [lastFailure] or [retryAction], so it can neither hijack nor be hijacked by a full-screen
     * read's retry - the two surfaces are independent.</p>
     */
    private void showPagingFailure(Throwable error, boolean forward) {
        showProgressView(false);
        if (bubbles.isEmpty()) {
            // No rows on screen: this is the whole screen's failure, not a page's. Paging does not run
            // in this state, but the full-screen surface is the honest one if it ever does.
            showTopicFailure(error);
            return;
        }
        final TopicLoadFailure reason = TopicLoadFailure.of(error);
        org.telegram.messenger.FileLog.e(
                forward ? "topic next page failed" : "topic previous page failed", error);
        final int generation = reading.getGeneration();
        final int cursor = forward ? reading.getNextPageStart() : reading.getWindowStart();
        // A page that needs the session is not an outage: the hint says which it is, and its own
        // action opens the login/verification WebView before asking for the page again.
        final int messageRes;
        final int actionRes;
        if (reason == TopicLoadFailure.Challenge) {
            messageRes = io.github.baba983.nativedo.R.string.page_verify_required;
            actionRes = io.github.baba983.nativedo.R.string.page_verify;
        } else if (reason == TopicLoadFailure.LoginRequired) {
            messageRes = io.github.baba983.nativedo.R.string.page_login_required;
            actionRes = io.github.baba983.nativedo.R.string.page_login;
        } else {
            messageRes = io.github.baba983.nativedo.R.string.topic_page_retry_failed;
            actionRes = io.github.baba983.nativedo.R.string.topic_page_retry;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.chats_infotip,
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(messageRes),
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(actionRes),
                    org.telegram.ui.Components.Bulletin.DURATION_PROLONG,
                    () -> retryStreamPage(forward, generation, cursor, reason)).show();
        } catch (Exception bulletinError) {
            org.telegram.messenger.FileLog.e(bulletinError);
        }
    }

    /**
     * The failure hint's own retry: the exact direction that failed, in the window it failed in, from
     * the cursor the failure left. A window that has moved on, or a screen that is gone, drops the
     * tap; a duplicate tap while the read is out is refused by the loading flag the read itself owns.
     */
    private void retryStreamPage(boolean forward, int generation, int cursor, TopicLoadFailure reason) {
        if (!reading.isCurrent(generation) || getParentActivity() == null
                || cursor != (forward ? reading.getNextPageStart() : reading.getWindowStart())
                || (forward ? reading.isLoadingForwardPage() : reading.isLoadingBackwardPage())) {
            return;
        }
        // Validate the hint before opening login, and again once recovery finishes: navigation or
        // another successful attempt can supersede the failed page during either interval.
        if (reason == TopicLoadFailure.Challenge || reason == TopicLoadFailure.LoginRequired) {
            recoverSessionThenRetry(() -> retryStreamPage(forward, generation, cursor, null));
            return;
        }
        if (forward) {
            // Clearing the latch is what lets this one attempt through; a failure sets it again.
            reading.clearForwardPageFailure();
            loadNextStreamPage();
        } else {
            reading.clearBackwardPageFailure();
            loadPreviousStreamPage();
        }
    }

    /**
     * A page-down jump that did not arrive. The rows and the reader's place stay and the button comes
     * back, but the hint names the retry the reader actually has - a tap on the arrow - rather than
     * the topic paging copy's promise that scrolling will try again.
     */
    private void showPageDownFailure(Throwable error) {
        showFailure(error, io.github.baba983.nativedo.R.string.topic_page_down_failed,
                "page-down jump failed");
    }

    /**
     * A read that did not arrive. While the topic is empty the whole screen says so and a tap acts
     * on it; a page that failed with the topic already on screen is said in passing - upstream's own
     * Bulletin, anchored above the composer - with [pagingLineRes] as its copy.
     */
    private void showFailure(Throwable error, int pagingLineRes, String logLabel) {
        // A window that did not arrive leaves the rows on screen as they were, so the mode goes
        // back to the one those rows were read in instead of naming a filter that is not in force.
        if (reading.failWindowRead()) {
            updateOnlyOpMenuItem();
            if (topicDetail != null) {
                bindSubtitle(topicDetail);
            }
        }
        // The read is over, so the ring goes with it (ChatActivity:20606 shows the same).
        showProgressView(false);
        lastFailure = TopicLoadFailure.of(error);
        org.telegram.messenger.FileLog.e(logLabel, error);
        if (!bubbles.isEmpty()) {
            // The topic is on screen, so the rows the reader has are still good: this was a page
            // that did not arrive, not the topic. It is said in passing - a modal would stop the
            // reader over something they did not ask for.
            try {
                org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(
                        org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                                pagingLineRes)).show();
            } catch (Exception bulletinError) {
                org.telegram.messenger.FileLog.e(bulletinError);
            }
            return;
        }
        if (failureView == null) {
            return;
        }
        failureView.setCustomText(failureText(lastFailure));
        failureView.setVisibility(View.VISIBLE);
    }

    /** The tap on the failure view: recover the session where that is what is wrong, else re-read. */
    private void onFailureTap() {
        if (lastFailure == TopicLoadFailure.Challenge || lastFailure == TopicLoadFailure.LoginRequired) {
            recoverSessionThenRetry();
            return;
        }
        runRetry();
    }

    /**
     * A challenge or a lost session is dealt with in the same WebView the app logs in with: solving
     * the challenge there is what makes the site answer the app again. The WebView is a detour, not
     * the destination - once the session is usable it goes away and the read is tried again, with
     * the floor it asked for.
     */
    private void recoverSessionThenRetry() {
        recoverSessionThenRetry(this::runRetry);
    }

    /**
     * The same recovery, for a caller whose retry is not the screen-level [retryAction]: the action
     * given here runs once the session is usable, under whatever guard the caller already holds.
     */
    private void recoverSessionThenRetry(Runnable afterRecovery) {
        if (getParentActivity() == null) {
            return;
        }
        io.github.baba983.nativedo.auth.LoginWebViewFragment[] holder =
                new io.github.baba983.nativedo.auth.LoginWebViewFragment[1];
        holder[0] = new io.github.baba983.nativedo.auth.LoginWebViewFragment(() -> {
            holder[0].finishFragment();
            if (reporter != null) {
                reporter.onSessionRecovered();
            }
            afterRecovery.run();
        }, true);
        presentFragment(holder[0]);
    }

    private void runRetry() {
        if (failureView != null) {
            failureView.setVisibility(View.GONE);
        }
        Runnable action = retryAction;
        if (action != null) {
            action.run();
        }
    }

    /**
     * Whether any row of a page the site answered came from the offline cache. A page of cached
     * posts is recognised by the first row that says so; an empty or live page says no.
     */
    private static boolean anyPostFromCache(List<Post> posts) {
        if (posts == null) {
            return false;
        }
        for (Post post : posts) {
            if (post.getFromCache()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The read was served from the offline cache (or from the site). Sets the screen's cached flag
     * and, on the first cached answer, says so and offers the retry. A live answer clears the flag,
     * which is what lets writes go through again.
     */
    private void noteServedFromCache(boolean fromCache) {
        boolean firstCached = fromCache && !showingCachedContent;
        showingCachedContent = fromCache;
        if (firstCached) {
            if (topicDetail != null) {
                bindSubtitle(topicDetail);
            }
            showCachedTopicNotice();
        }
    }

    /**
     * The topic's offline signal: the rows on screen are the last answer kept for this reader. The
     * Bulletin carries the retry explicitly; its tap runs whatever read the screen last asked for, so
     * a live answer clears the cached flag and writes are allowed again.
     */
    private void showCachedTopicNotice() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.chats_infotip,
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            io.github.baba983.nativedo.R.string.topic_offline),
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            io.github.baba983.nativedo.R.string.topic_offline_retry),
                    org.telegram.ui.Components.Bulletin.DURATION_PROLONG,
                    this::runRetry).show();
        } catch (Exception bulletinError) {
            org.telegram.messenger.FileLog.e(bulletinError);
        }
    }

    /**
     * Whether a write must be suppressed because cached rows are on screen whose permissions the site
     * has not confirmed since. Says so in passing and refuses; a live read clears the flag.
     */
    private boolean denyWriteWhileOffline() {
        if (!showingCachedContent) {
            return false;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            io.github.baba983.nativedo.R.string.topic_offline_readonly)).show();
        } catch (Exception bulletinError) {
            org.telegram.messenger.FileLog.e(bulletinError);
        }
        return true;
    }

    private String failureText(TopicLoadFailure reason) {
        int id;
        switch (reason) {
            case Challenge:
                id = io.github.baba983.nativedo.R.string.topic_failed_challenge;
                break;
            case LoginRequired:
                id = io.github.baba983.nativedo.R.string.topic_failed_login;
                break;
            case Forbidden:
                id = io.github.baba983.nativedo.R.string.topic_failed_forbidden;
                break;
            case RateLimited:
                id = io.github.baba983.nativedo.R.string.topic_failed_rate_limited;
                break;
            case Network:
                id = io.github.baba983.nativedo.R.string.topic_failed_network;
                break;
            default:
                id = io.github.baba983.nativedo.R.string.topic_failed;
                break;
        }
        return org.telegram.messenger.ApplicationLoader.applicationContext.getString(id);
    }

    /** What the composer tells us: the text, and that the send button was pressed. */
    private class ReplyDelegate extends DiscourseComposerDelegate {

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
            String raw = message == null ? "" : message.toString().trim();
            if (!raw.isEmpty()) {
                if (editFloor >= 0) {
                    // The field is the post being edited, not a reply: this press sends an edit.
                    sendEdit(raw);
                    return;
                }
                Integer target = replyToPostNumber;
                if (target == null && threadMode && threadParentFloor > 0) {
                    // An ordinary answer on a discussion page answers its root floor. The target is
                    // chosen here, not restored as a default one, so no strip is opened for it and
                    // clearing the target (a send, a cancelled strip) returns to this same default.
                    target = threadParentFloor;
                }
                sendReply(raw, target);
            }
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
            // Every keystroke is the reader's current draft; the field is the source of truth and
            // the store is written from it (the send path clears it, a failure keeps it).
            saveDraft();
            if (!fromDraft && replyPresence != null) {
                // A post being edited is not a reply: the site has its own edit channel for that,
                // and announcing here would tell the topic somebody is answering it.
                replyPresence.textChanged(editFloor < 0 && text != null
                        && text.toString().trim().length() > 0);
            }
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }

        @Override
        public void onAudioVideoInterfaceUpdated() {
            applyPageDownTransform();
        }

        /** Attachments in a reply go to the site first: what the field gets is the markdown its own composer would write. */
        @Override
        public void didPressAttachButton() {
            openAttachmentSheet();
        }

        /** A keyboard's GIF/sticker commit or a pasted image is an attachment, never a Telegram photo. */
        @Override
        public void onComposerMediaInput(android.net.Uri uri, Runnable releasePermission) {
            attachMediaUri(uri, releasePermission);
        }
    }

    /**
     * The pictures behind a floor's reactions. Upstream's chip looks each emoticon up in a table
     * that nothing in this client fills - its entries come from `messages.getAvailableReactions` -
     * so the entry is handed to it from here; see {@link DiscourseReactionEmoji}. Nothing is drawn
     * until the row is bound again, which is what {@code onReady} does.
     */
    private void fetchReactionEmoji(int index, Runnable onReady) {
        if (index >= bubbleFloors.size()) {
            return;
        }
        Integer floorIndex = bubbleFloors.get(index);
        if (floorIndex == null || floorIndex < 0 || floorIndex >= floors.size()) {
            return;
        }
        List<PostReaction> reactions = floors.get(floorIndex).getReactions();
        if (reactions.isEmpty()) {
            return;
        }
        DiscourseLoader.INSTANCE.cacheReactionEmoji(container, reactions, () -> {
            onReady.run();
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Media blocks can be bound before their local files exist. Refresh retained photo receivers
     * when the cache fills, then rebind the row for its other assets and download-button state.
     */
    private void fetchPostMedia(int index) {
        if (container == null || index < 0 || index >= bubbles.size() || isDividerRow(index)) {
            return;
        }
        // The row is identified by the message it draws, not by the index it happened to sit at:
        // a prepend shifts every index, and a fetched body must re-bind the row it belongs to rather
        // than whatever now sits where that row used to be.
        final MessageObject message = bubbles.get(index);
        Runnable onReady = () -> {
            if (listView != null && listView.getAdapter() != null) {
                int at = bubbles.indexOf(message);
                if (at >= 0) {
                    // Retained cells need upstream's refresh signals when local assets arrive.
                    message.forceUpdate = true;
                    message.reactionsChanged = true;
                    listView.getAdapter().notifyItemChanged(at);
                }
            }
        };
        fetchReactionEmoji(index, onReady);
        // A poll bubble has no images of its own; its options are text.
        PreparedPostBody body = bubbleBodies.get(index);
        if (body == null) {
            return;
        }
        List<PostImage> images = body.getImages();
        List<PostAttachment> attachments = body.getAttachments();
        List<PostVideo> videos = body.getVideos();
        List<PostImage> emojis = body.getEmojis();
        Runnable onEmojisReady = () -> {
            // The span and the box it fills were laid out from the body's first draw, so a picture
            // that lands now only has to reach the drawable already bound to its document: the
            // artwork replaces the placeholder the span is showing, and nothing about the row, the
            // body or the message is rebuilt. A picture that never lands keeps the placeholder.
            for (PostImage emoji : emojis) {
                DiscourseImageBridge.INSTANCE.applyEmojiArtwork(emoji);
            }
        };
        Runnable onAttachmentsReady = () -> {
            DiscourseImageBridge.INSTANCE.applyAttachmentFiles(message.messageOwner, attachments);
            onReady.run();
        };
        DiscourseLoader.INSTANCE.cachePostPhotos(container, images, photo -> {
            refreshPhotoRowsContaining(photo);
            return kotlin.Unit.INSTANCE;
        });
        for (PostImage image : images) {
            registerPhotoStarter(image);
        }
        if (!emojis.isEmpty()) {
            DiscourseLoader.INSTANCE.cacheImages(container, emojis, () -> {
                onEmojisReady.run();
                return kotlin.Unit.INSTANCE;
            });
        }
        DiscourseLoader.INSTANCE.cacheAttachments(container, attachments, () -> {
            onAttachmentsReady.run();
            return kotlin.Unit.INSTANCE;
        });
        DiscourseLoader.INSTANCE.cacheVideoCovers(container, videos, () -> {
            onReady.run();
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * How a press on one picture's own download button starts (or retries) its fetch. The starter is
     * registered while the row that draws the picture is bound, and dropped when the screen leaves,
     * so a block that outlives its topic cannot call back into a screen that is gone.
     */
    private final Map<String, Runnable> photoStarters = new HashMap<>();

    private void registerPhotoStarter(PostImage image) {
        String name = DiscourseImageBridge.INSTANCE.photoFileName(image);
        if (photoStarters.containsKey(name)) return;
        Runnable starter =
                () -> DiscourseLoader.INSTANCE.fetchPostPhoto(container, image, this::onPhotoBytes);
        DiscourseImageBridge.INSTANCE.registerPhotoStarter(name, starter);
        photoStarters.put(name, starter);
    }

    /**
     * Draws whatever the fetch has put on disk for one picture: the site's preview while the
     * original is still on its way, the original once it lands. A row already on screen keeps its
     * {@link MessageObject} and its place - the retained block re-reads its file and the row is
     * re-bound, rather than replaced.
     */
    private void refreshPhotoRowsContaining(PostImage image) {
        if (isFinished || listView == null || listView.getAdapter() == null) {
            return;
        }
        for (int index = 0; index < bubbles.size(); index++) {
            PreparedPostBody body = bubbleBodies.get(index);
            if (body == null || !body.getImages().contains(image)) {
                continue;
            }
            MessageObject row = bubbles.get(index);
            refreshPhotoBlocks(row);
            row.forceUpdate = true;
            row.reactionsChanged = true;
            listView.getAdapter().notifyItemChanged(index);
        }
    }

    /** The row-side of {@link #refreshPhotoRowsContaining}, for a fetch's callback. */
    private kotlin.Unit onPhotoBytes(PostImage image) {
        refreshPhotoRowsContaining(image);
        return kotlin.Unit.INSTANCE;
    }

    /**
     * Makes the retained photo blocks of one row read their file again: a block built before the
     * bytes existed never asked for them, and upstream's block only re-reads on a fresh
     * {@code setImage}. The block's own cancel stops its pending Telegram file request, so the
     * layout and everything else the row holds stay exactly as they are.
     */
    private void refreshPhotoBlocks(MessageObject message) {
        if (message == null || message.richLayout == null) {
            return;
        }
        FileLoader loader = FileLoader.getInstance(0);
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
            // Upstream's own block only re-reads its file on a fresh setImage. A retained block may
            // have been built before the bytes existed, so it is told again whenever anything it
            // reads has landed - the preview first, then the original. What is handed over is only
            // what is already on disk: a location with no file behind it would send upstream's own
            // loader looking for a datacenter this client does not have, and its "download" would
            // never end. This does not rebuild the row: the message, the layout and its expanded
            // state are all kept.
            photo.imageReceiver.cancelLoadImage();
            photo.imageReceiver.setImage(null, null,
                    fullExists ? io.github.baba983.nativedo.hooks.NativeDoHooks.photoImageLocation(photo.sizeFull, photo.photo) : null, null,
                    photo.strippedSize != null && previewExists
                            ? io.github.baba983.nativedo.hooks.NativeDoHooks.photoImageLocation(photo.strippedSize, photo.photo) : null,
                    "b1", null, photo.sizeFull.size, null, message, 1);
            photo.updateButtonState(true);
        }
    }

    /**
     * Builds the rows again.
     *
     * <p>A cell asks for its author's avatar once and skips the request while it still holds the
     * same one, and a row is always bound before the picture has been fetched; assigning the
     * adapter again is what replaces the cells, and with them the state that remembered the
     * unanswered request.</p>
     */
    private void rebuildRows() {
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        listView.setAdapter(listView.getAdapter());
    }

    private void notifyPostsChanged() {
        if (composerTools != null && topicDetail != null) {
            composerTools.setLocalCandidates(topicDetail.getParticipants(), floors);
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class PostAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) {
                // Upstream's own "Unread Messages" row: the themed bar with the arrow, the same one
                // a chat inserts where the reader stopped reading.
                org.telegram.ui.Cells.ChatUnreadCell cell = new org.telegram.ui.Cells.ChatUnreadCell(
                        parent.getContext(), resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                cell.setOnClickListener(view -> {
                    if (listView == null) {
                        return;
                    }
                    int position = listView.getChildAdapterPosition(cell);
                    if (position >= 0 && position + 1 < bubbles.size()) {
                        listView.smoothScrollToPosition(position + 1);
                    }
                });
                return new RecyclerListView.Holder(cell);
            }
            if (viewType == 2) {
                // Upstream's own date row: the service pill a chat writes above a day's first message,
                // drawn by the same cell the chat draws it with (ChatActivity 37397, 38008-38015).
                org.telegram.ui.Cells.ChatActionCell cell =
                        new org.telegram.ui.Cells.ChatActionCell(
                                parent.getContext(), false, resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            ChatMessageCell cell = new ChatMessageCell(
                    parent.getContext(), currentAccount, false, sharedResources, resourceProvider);
            cell.setDelegate(new PostCellDelegate());
            cell.isChat = true;
            // The stock cell draws the reply/comment footer only when the screen tells it the chat
            // has a linked discussion. This screen's "discussion" is the topic itself, and there is
            // no distinct linked-chat id: 0 is upstream's own "any" (a message with replies is
            // linked). That is the metadata the cell reads - not a Telegram broadcast, and no chat
            // is behind it. On the discussion page itself the footer is off: its rows are already
            // the replies, and a footer that pushed another copy of the same page would be a loop.
            cell.hasDiscussion = !threadMode;
            cell.linkedChatId = 0;
            cell.setFullyDraw(false);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public int getItemViewType(int position) {
            if (bubbles.get(position).isDateObject) {
                return 2;
            }
            return isUnreadDividerRow(position) ? 1 : 0;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof org.telegram.ui.Cells.ChatUnreadCell) {
                ((org.telegram.ui.Cells.ChatUnreadCell) holder.itemView).setText(UNREAD_DIVIDER_LINE);
                return;
            }
            if (holder.itemView instanceof org.telegram.ui.Cells.ChatActionCell) {
                org.telegram.ui.Cells.ChatActionCell cell =
                        (org.telegram.ui.Cells.ChatActionCell) holder.itemView;
                cell.setMessageObject(bubbles.get(position));
                // A row that was recycled while it sat under the top edge was faded out; the chat
                // puts it back on bind (ChatActivity 38013).
                cell.setAlpha(1f);
                return;
            }
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = bubbles.get(position);
            // A floor's bubbles are a run of messages by one author, the shape upstream's own chat
            // groups: the first bubble keeps its rounded top and draws the name, the last keeps its
            // rounded bottom (and draws the avatar, which this list adds), and the bubbles between
            // them are pinned to their neighbours.
            boolean pinnedTop = position > firstBubbleOfFloor(position);
            boolean pinnedBottom = position < lastBubbleOfFloor(position);
            cell.setMessageObject(message, null, pinnedBottom, pinnedTop, false);
            // Upstream binds a rich body by creating its layout and attaching it in the same breath,
            // but only while the cell is already on screen. A cell that is bound before it is
            // attached (and every re-bind after a download) would leave the body's blocks without a
            // view, which is what they open files and photos through. The cell detaches the layout
            // again when it leaves the screen, so attaching here is what upstream does, just not
            // conditional on the order of the two.
            if (message.richLayout != null) {
                message.richLayout.attach(cell);
            }
            // The row is bound, which is the moment a holographic tag becomes drawable: the first
            // frame after the bind sees it. A bind is the one event the initial layout and every
            // recycling pass share, so the loop cannot miss the tag until a scroll happens.
            if (message.messageOwner != null
                    && DiscourseUserTitles.INSTANCE.isAnimated(message.messageOwner.from_rank)) {
                startTitleGradient();
            }
        }

        @Override
        public int getItemCount() {
            return bubbles.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    /** The first bubble of the floor [position] draws; a floor with one bubble is its own first. */
    private int firstBubbleOfFloor(int position) {
        int floor = bubbleFloors.get(position);
        for (int index = 0; index < bubbleFloors.size(); index++) {
            if (bubbleFloors.get(index) == floor && !isDividerRow(index)) {
                return index;
            }
        }
        return position;
    }

    /** The last bubble of the floor [position] draws. */
    private int lastBubbleOfFloor(int position) {
        int floor = bubbleFloors.get(position);
        for (int index = bubbleFloors.size() - 1; index >= 0; index--) {
            if (bubbleFloors.get(index) == floor && !isDividerRow(index)) {
                return index;
            }
        }
        return position;
    }

    /** Upstream's own callbacks; a tap on a photo or a link leaves for upstream's viewers. */
    private class PostCellDelegate implements ChatMessageCell.ChatMessageCellDelegate {

        @Override
        public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
            forceUpdate(cell, anchorScroll, false);
        }

        /** Details changes the rich layout in place; remeasure the cell without replacing rows. */
        @Override
        public void forceUpdate(ChatMessageCell cell, boolean anchorScroll, boolean scrollByTop) {
            LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
            int position = scrollByTop ? listView.getChildAdapterPosition(cell)
                    : manager.findFirstVisibleItemPosition();
            View anchor = manager.findViewByPosition(position);
            int offset = anchor == null ? 0 : anchor.getTop() - listView.getPaddingTop();
            cell.forceResetMessageObject();
            cell.requestLayout();
            cell.invalidate();
            if (anchorScroll && anchor != null) {
                manager.scrollToPositionWithOffset(position, offset);
            }
        }

        /**
         * A tap on one of the reaction chips. The id comes back from the emoticon the chip drew,
         * which is the only field a chip carries, so the mapping has to be reversible.
         */
        @Override
        public void didPressReaction(ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
            if (reaction == null || !(reaction.reaction instanceof TLRPC.TL_reactionEmoji)) {
                return;
            }
            int floor = floorOf(cell.getMessageObject());
            if (floor < 0) {
                return;
            }
            String reactionId = DiscourseReactions.INSTANCE.idOf(
                    ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon);
            if (longpress) {
                showDialog(DiscourseReactionUi.users(TopicFragment.this, container,
                        floors.get(floor).getId(), reactionId, (username, displayName) -> {
                            presentFragment(io.github.baba983.nativedo.profile.ProfileFragment.forUser(username, displayName));
                            return kotlin.Unit.INSTANCE;
                        }));
            } else {
                toggleReaction(floors.get(floor).getId(), reactionId);
            }
        }

        /** Long press on any bubble of a floor: the menu a reader opens on a post, anchored at the press. */
        @Override
        public void didLongPress(ChatMessageCell cell, float x, float y) {
            int floor = floorOf(cell.getMessageObject());
            if (floor < 0) {
                return;
            }
            showMessageMenu(cell, floor, x, y);
        }

        /**
         * A tap on a bubble's reply preview, which points at the floor the post answers. The floor
         * number was recorded when the bubble was built, so the jump never reads the target's post id
         * as if it were a floor number. A target already on screen scrolls into view; one the window
         * does not carry is read the way the floor's own link would be.
         */
        @Override
        public void didPressReplyMessage(ChatMessageCell cell, int id, float x, float y, boolean longpress) {
            if (longpress || cell == null) {
                return;
            }
            Integer target = replyTargetFloors.get(cell.getMessageObject());
            if (target != null) {
                jumpToFloor(target);
            }
        }

        /**
         * A tap on a floor's reply footer. It opens the direct replies to that floor as the pushed
         * discussion page: the page reads them from the site by the post's own id, and each row's tap
         * jumps to that reply's floor. Opening and closing the page leaves this screen's list and
         * composer untouched, so the reader's place and draft are left exactly where they were.
         */
        @Override
        public void didPressCommentButton(ChatMessageCell cell) {
            if (cell == null) {
                return;
            }
            openRepliesThread(cell.getMessageObject());
        }

        /**
         * "View Votes" on a poll: the site's own voters list, one section per option, and a tap on a
         * voter opens that reader's profile. Upstream's cell only asks its host for this on a poll
         * whose voters are public, which is exactly when the site answers with a list.
         */
        @Override
        public void didPressInstantButton(ChatMessageCell cell, int type) {
            if (cell == null || type != ChatMessageCell.INSTANT_BUTTON_TYPE_VIEW_VOTES_PUBLIC_LIST) {
                return;
            }
            MessageObject message = cell.getMessageObject();
            PostPoll poll = pollOf(message);
            int floor = floorOf(message);
            if (poll == null || floor < 0) {
                return;
            }
            showDialog(DiscoursePollVotersUi.voters(TopicFragment.this, container,
                    floors.get(floor).getId(), poll, (username, displayName) -> {
                        presentFragment(io.github.baba983.nativedo.profile.ProfileFragment.forUser(username, displayName));
                        return kotlin.Unit.INSTANCE;
                    }));
        }

        /**
         * A tap on a poll's answers. Upstream hands over the answers that are being voted for - the
         * one that was tapped in a single-choice poll, the whole checked set in a multiple-choice
         * one - and this client sends exactly those to the site.
         */
        @Override
        public void didPressVoteButtons(ChatMessageCell cell, ArrayList<TLRPC.PollAnswer> buttons, int showCount, int x, int y) {
            if (buttons == null || buttons.isEmpty()) {
                // Upstream's own hint path (a vote the server restricted); this client has nothing
                // to hint about, and nothing was chosen.
                return;
            }
            MessageObject message = cell.getMessageObject();
            PostPoll poll = pollOf(message);
            if (poll == null) {
                return;
            }
            ArrayList<String> digests = new ArrayList<>();
            for (TLRPC.PollAnswer answer : buttons) {
                if (answer != null && answer.option != null) {
                    digests.add(new String(answer.option, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            if (digests.isEmpty()) {
                return;
            }
            vote(message, poll, new PollVoteSelection(digests, false));
        }

        /**
         * A tap on an answer of a poll the reader has already voted in - upstream routes those here,
         * not to [didPressVoteButtons]. The tap changes the vote, and a tap on the answer that is
         * already chosen takes the vote back; a poll that takes no tap (closed, or a ranked one
         * whose order a tap cannot express) opens the floor's menu instead.
         */
        @Override
        public boolean didLongPressPollOption(ChatMessageCell cell, TLRPC.PollAnswer answer) {
            MessageObject message = cell.getMessageObject();
            PostPoll poll = pollOf(message);
            if (poll == null || answer == null || answer.option == null) {
                return false;
            }
            String digest = new String(answer.option, java.nio.charset.StandardCharsets.UTF_8);
            PollVoteSelection selection = DiscoursePolls.selectionFor(poll, digest);
            if (selection == null) {
                showMessageMenu(cell, floorOf(message));
                return true;
            }
            vote(message, poll, selection);
            return true;
        }

        /**
         * Upstream asks the delegate for the selection helper on every touch a message receives;
         * handing it over is what lets a post's text be selected at all.
         */
        @Override
        public org.telegram.ui.Cells.TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
            return textSelectionHelper;
        }

        /**
         * Upstream gates every touch on this. Its own chat answers true whenever no action mode or
         * report screen is up, and this screen never has one - a poll answer, a link, a reaction chip
         * and a long press are all reached through the cell's own touch handling, so a client that
         * left the default (false) would draw a poll nobody could vote in.
         */
        @Override
        public boolean canPerformActions() {
            return true;
        }

        /**
         * A tap on an image in a post body; the cell routes rich media through this one.
         *
         * <p>A clip is not fetched with the page, so the tap is what fetches it: the fetch runs
         * here, the block draws its own loading state while it does, and upstream's viewer opens
         * on the file once it is here. A clip this client will not fetch, or one the site will not
         * hand over, goes to the device's own player, which streams it from the site's address.</p>
         */
        @Override
        public boolean openArticlePhoto(ChatMessageCell cell, org.telegram.tgnet.tl.TL_iv.PageBlock block) {
            MessageObject message = cell.getMessageObject();
            PostVideo video = ArticlePhotoViewer.videoOf(block);
            if (video == null || container == null) {
                return ArticlePhotoViewer.open(TopicFragment.this, message, block, listView);
            }
            DiscourseLoader.INSTANCE.fetchVideo(
                    container,
                    video,
                    () -> {
                        if (!isFinished && isLastFragment() && !isPaused()) {
                            ArticlePhotoViewer.open(TopicFragment.this, message, block, listView);
                        }
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> {
                        if (!isFinished && isLastFragment() && !isPaused()) {
                            ArticlePhotoViewer.stream(TopicFragment.this, block);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            return true;
        }

        @Override
        public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
            if (longPress || !(url instanceof URLSpan)) {
                return;
            }
            String target = ((URLSpan) url).getURL();
            // A link to this site's own topic opens the topic, not a web page: the reader asked
            // for the topic, and the app can draw it natively whether or not the in-app browser is
            // turned on. Plain same-site user profiles also have a native destination.
            io.github.baba983.nativedo.core.web.DiscourseTopicLink topicLink =
                    container == null ? null
                            : io.github.baba983.nativedo.core.web.DiscourseLinks.topicLink(
                                    target, container.getBaseUrl());
            if (topicLink != null && topicLink.getTopicId() > 0) {
                Integer floor = topicLink.getFloor() > 0 ? topicLink.getFloor() : null;
                presentFragment(TopicFragment.forFloor(topicLink.getTopicId(), null, floor));
                return;
            }
            String username = container == null ? null
                    : io.github.baba983.nativedo.core.web.DiscourseLinks.profileUsername(
                            target, container.getBaseUrl());
            if (username != null) {
                presentFragment(io.github.baba983.nativedo.profile.ProfileFragment.forUser(username, username));
                return;
            }
            Browser.openUrl(getParentActivity(), target);
        }

        /**
         * A tap on a link preview (an onebox): upstream's own delegate default calls
         * {@code Browser.openUrl} directly, and the shared CookieManager makes that the
         * signed-in page for linux.do URLs.
         */
        @Override
        public void didPressWebPage(ChatMessageCell cell, TLRPC.WebPage webpage, String url, boolean safe) {
            if (url == null || url.isEmpty()) {
                return;
            }
            Browser.openUrl(getParentActivity(), url);
        }

        /**
         * A tap on the author's avatar, or on the name above the bubble: upstream asks its delegate
         * for both. A linux.do post's author is a site reader, so the tap opens the site's own
         * profile rather than anything Telegram knows about them.
         */
        @Override
        public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
            if (user == null || user.username == null || user.username.isEmpty()) {
                return;
            }
            if (getParentActivity() instanceof NativeDoActivity) {
                ((NativeDoActivity) getParentActivity()).present(
                        io.github.baba983.nativedo.profile.ProfileFragment.forUser(user.username, user.first_name));
            }
        }
    }
}
