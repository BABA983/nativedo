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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoActivity;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.core.model.PostPoll;
import io.github.baba983.nativedo.core.model.PostReaction;
import io.github.baba983.nativedo.core.model.TopicDetail;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.feature.topic.data.TopicReadReporter;

/**
 * One linux.do topic: every post is drawn by upstream's stock {@link ChatMessageCell} from a
 * {@code TL_message.rich_message} body, which is how upstream itself previews a locally built rich
 * message. Taps route to upstream's article photo viewer and {@link Browser}.
 */
public class TopicFragment extends BaseFragment {

    private static final String ARG_TOPIC_ID = "topic_id";
    private static final String ARG_TOPIC_TITLE = "topic_title";
    private static final String ARG_FLOOR = "floor";

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

    /** The right capsule's overflow item and the sub-items behind it. */
    private static final int MENU_ITEM_MORE = 1;
    private static final int MENU_ITEM_BOOKMARK = 2;
    private static final int MENU_ITEM_COPY_LINK = 3;
    private static final int MENU_ITEM_OPEN_BROWSER = 4;

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
    /** Per bubble, the prepared body a text bubble draws from; a poll bubble has none. */
    private final List<PreparedPostBody> bubbleBodies = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    /** Per author, the avatar template that produced their profile photo; the file is fetched from it. */
    private final HashMap<Long, String> avatarTemplates = new HashMap<>();
    private org.telegram.messenger.ChatMessageSharedResources sharedResources;
    private AppContainer container;
    private long topicId;
    private ChatActivityEnterView composer;
    /** The floating center capsule: the topic author's avatar, the title and the subtitle. */
    private org.telegram.ui.Components.ChatAvatarContainer avatarContainer;
    /** The bar's blur source; the post list is the content it records. */
    private io.github.baba983.nativedo.app.GlassPanelHost glassHost;
    /** The topic as last read; the capsule and the bookmark item draw from it. */
    private TopicDetail topicDetail;
    /** The topic's original poster, as the synthetic user the rows also draw. */
    private TLRPC.User topicAuthor;
    /** The reader's own topic-level bookmark, mirrored from the site's answer. */
    private boolean topicBookmarked;
    /** What the list keeps above its first post for the floating bar (bar height + 4dp). */
    private int listPaddingTop;
    /** The overflow item's 书签 row, whose label and icon follow [topicBookmarked]. */
    private org.telegram.ui.ActionBar.ActionBarMenuSubItem bookmarkMenuItem;
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
                if (id != NotificationCenter.needSetDayNightTheme) {
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
                }
            };
    /** The frame that draws the chat wallpaper behind the posts (ChatActivity's contentView). */
    private SizeNotifierFrameLayout wallpaperView;

    /** ChatActivity.updateBackground (44180-44191): the current chat wallpaper, with its motion. */
    private void updateBackground() {
        if (wallpaperView == null) {
            return;
        }
        wallpaperView.setBackgroundImage(
                org.telegram.ui.ActionBar.Theme.getCachedWallpaper(),
                org.telegram.ui.ActionBar.Theme.isWallpaperMotion());
        if (glassHost != null) {
            // setBackgroundImage may have just created the wallpaper view; the source records it.
            glassHost.setBackgroundView(wallpaperView.backgroundView);
        }
    }
    /** The floor this reply answers, when the reader picked one in the post menu. */
    private Integer replyToPostNumber;
    /** Our own attach entry point, for the files upstream's button cannot offer (see its builder). */
    private View attachStrip;
    private android.widget.TextView attachStripLabel;
    private boolean uploadingAttachment;
    /** Post numbers per row: replying is by number, and the payload does not always carry them. */
    private final List<Integer> postNumbers = new ArrayList<>();
    private List<Long> streamPostIds = new ArrayList<>();
    private int nextPageStart;
    private boolean loadingPage;
    /** The reader's place in this topic, and the report of it the site asks for. */
    private TopicReadReporter reporter;
    /** What is shown instead of the list when the topic could not be read at all. */
    private org.telegram.ui.Components.EmptyTextProgressView failureView;
    /** Why the last read failed, in the terms the retry tap acts on. */
    private TopicLoadFailure lastFailure;
    /** What a retry tap runs: the read that just failed, with the floor it asked for. */
    private Runnable retryAction;

    public static TopicFragment forTopic(long topicId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        return new TopicFragment(args);
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

    /**
     * The picker's answer: the file is uploaded to the site, and what the field receives is the
     * markdown the site's own composer would have written for it.
     */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != PICK_ATTACHMENT_REQUEST
                || resultCode != android.app.Activity.RESULT_OK
                || data == null
                || getParentActivity() == null
                || container == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        io.github.baba983.nativedo.core.media.PickedMedia picked =
                io.github.baba983.nativedo.core.media.PickedMediaResolver.INSTANCE.resolve(getParentActivity(), uri);
        if (picked == null) {
            showReplyError(new java.io.IOException("无法读取所选文件"));
            return;
        }
        setUploadingAttachment(true);
        DiscourseLoader.INSTANCE.uploadReplyMedia(container, picked, markup -> {
            io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(
                    composer == null ? null : composer.getEditField(), markup);
            setUploadingAttachment(false);
            updateComposerActions();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            setUploadingAttachment(false);
            showReplyError(error);
            return kotlin.Unit.INSTANCE;
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
                } else if (id == MENU_ITEM_OPEN_BROWSER) {
                    openTopicInBrowser();
                }
            }
        });
        // The chat header's own overflow (its 12.10.5 edit item is the same ic_ab_other,
        // ChatActivity:4281): one item whose sub-items are this topic's actions. The menu is
        // created before setupGlass because that is where it is switched into glass mode
        // (ActionBar.java:245-248).
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        org.telegram.ui.ActionBar.ActionBarMenuItem overflowItem =
                menu.addItem(MENU_ITEM_MORE, org.telegram.messenger.R.drawable.ic_ab_other);
        overflowItem.setContentDescription("更多");
        bookmarkMenuItem = overflowItem.addSubItem(
                MENU_ITEM_BOOKMARK, org.telegram.messenger.R.drawable.msg_fave, "书签");
        overflowItem.addSubItem(
                MENU_ITEM_COPY_LINK, org.telegram.messenger.R.drawable.msg_link, "复制链接");
        overflowItem.addSubItem(
                MENU_ITEM_OPEN_BROWSER, org.telegram.messenger.R.drawable.msg_openin, "在浏览器打开");

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
        // switches off while a post is being dragged (the ported processTouchEvent).
        androidx.recyclerview.widget.GridLayoutManagerFixed layoutManager =
                new androidx.recyclerview.widget.GridLayoutManagerFixed(
                        context, 1000, LinearLayoutManager.VERTICAL, false);
        layoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1000;
            }
        });
        listView.setLayoutManager(layoutManager);
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
            if (position < 0 || position >= bubbleFloors.size()) {
                return false;
            }
            showMessageMenu(view, bubbleFloors.get(position));
            return true;
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                maybeLoadNextPage();
                reportViewport();
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
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && textSelectionHelper != null) {
                    textSelectionHelper.stopScrolling();
                }
            }
        });
        // The first layout is what fills the viewport, and a viewport that never scrolls never
        // reports a scroll: the floors the reader has seen are reported from the layout too.
        listView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                reportViewport());

        // The bar floats at the top of the list and the composer at its bottom, so the list keeps
        // both clear: the top padding is the bar's measured height (the status bar is already in
        // it), the same term ChatActivity's own list padding adds (11947-11952).
        updateListPadding();
        contentView.addView(listView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        attachStrip = createAttachStrip(context);
        contentView.addView(attachStrip, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(STRIP_HEIGHT_DP),
                Gravity.BOTTOM | Gravity.LEFT));
        attachStrip.setVisibility(View.GONE);
        contentView.addView(createComposer(contentView), org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

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

        // A read that failed left the list empty, which reads the same as a topic with no posts.
        // This says which it was, and a tap on it either retries the read or opens the login WebView
        // when the session is what is wrong.
        failureView = new org.telegram.ui.Components.EmptyTextProgressView(context);
        failureView.setText(context.getString(io.github.baba983.nativedo.R.string.topic_failed));
        failureView.showTextView();
        failureView.setVisibility(View.GONE);
        // The view consumes touches in its own constructor; the tap that acts has to get through.
        failureView.setOnTouchListener(null);
        failureView.setOnClickListener(view -> onFailureTap());
        contentView.addView(failureView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));

        // The center capsule is the chat's own ChatAvatarContainer in glass mode, added to the bar
        // the way ChatActivity adds it (4134-4162, 4224: WRAP_CONTENT next to MATCH_PARENT, margins
        // 52/52). The bar itself draws the back, center and menu capsules around it and animates
        // their widths (ActionBar.java:2219-2270), and setupGlass is what turns the three glass
        // drawables on with the chat header's own provider (ActionBar.java:210-255;
        // ChatActivity:4569-4572). The list is the content the capsules' blur source records.
        avatarContainer = new org.telegram.ui.Components.ChatAvatarContainer(
                context, this, false, resourceProvider);
        avatarContainer.setGlassMode();
        avatarContainer.setClipChildren(false);
        avatarContainer.setActionBar(actionBar);
        // A tap on the capsule is the reader asking for the author, the same as a tap on a post's
        // avatar or name (ChatActivity opens the profile from its own listener the same way).
        avatarContainer.setOnClickListener(view -> openTopicAuthorProfile());
        if (title != null && !title.trim().isEmpty()) {
            avatarContainer.setTitle(title.trim());
        }
        actionBar.setChatAvatarContainer(avatarContainer);
        actionBar.addView(avatarContainer, 0, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                Gravity.TOP | Gravity.LEFT, 52, 0, 52, 0));
        actionBar.createMenu().bringToFront();
        glassHost = new io.github.baba983.nativedo.app.GlassPanelHost(listView, contentView);
        // The wallpaper chain is part of what a capsule samples (ChatActivity's navbar source draws
        // the wallpaper under the posts); the frame owns that view and hands it over here.
        glassHost.setBackgroundView(contentView.backgroundView);
        actionBar.setupGlass(
                glassHost.getFactory(),
                org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
                        .topPanelChatActivity(resourceProvider));
        contentView.addView(actionBar);

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
        // The capsules' glass and the capsule's own colours follow the day/night switch, the way
        // the topic list's strip is told.
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.didSetNewWallpapper);
        loadTopic(topicId);
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
                getParentActivity(), contentView, null, true, resourceProvider);
        view.setAllowStickersAndGifs(false, false, false);
        view.setDelegate(new ReplyDelegate());
        // The composer decides the button again whenever it is laid out (a keyboard, an emoji panel,
        // a first layout), so the decision is repeated there too.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateComposerActions();
            placeAttachStrip(v.getHeight());
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
        strip.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_chat_messagePanelBackground));

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
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0f, 8, 0, 0, 0));

        strip.setContentDescription("添加图片或附件");
        strip.setOnClickListener(v -> openAttachmentPicker());
        return strip;
    }

    /** The system's file picker; what it answers is uploaded by [onActivityResultFragment]. */
    private void openAttachmentPicker() {
        if (getParentActivity() == null || uploadingAttachment) {
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_ATTACHMENT_REQUEST);
    }

    /**
     * Where the strip sits: directly on top of the composer, whose height is only known once it has
     * been laid out (it grows with a second line, an emoji panel, the keyboard).
     */
    private void placeAttachStrip(int composerHeight) {
        if (attachStrip == null) {
            return;
        }
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
        int wanted = hasText ? View.VISIBLE : View.GONE;
        if (attachStrip.getVisibility() != wanted) {
            attachStrip.setVisibility(wanted);
            // The strip is an overlay on the list, so the list keeps its last post clear of it too
            // - and keeps the floating bar's own padding above it.
            applyListPadding();
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
        int barHeight = actionBar != null && actionBar.getMeasuredHeight() > 0
                ? actionBar.getMeasuredHeight()
                : org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
        listPaddingTop = AndroidUtilities.dp(4) + barHeight;
        applyListPadding();
    }

    /** Writes the list's paddings from what the bar and the composer need, together. */
    private void applyListPadding() {
        if (listView == null) {
            return;
        }
        boolean hasText = composer != null && composer.getEditField() != null
                && composer.getEditField().length() > 0;
        listView.setPadding(0, listPaddingTop, 0,
                AndroidUtilities.dp(hasText ? 52 + STRIP_HEIGHT_DP : 52));
    }

    /**
     * The system insets are where the status bar is; the bar's measured height follows them
     * (ActionBar.onMeasure adds {@code AndroidUtilities.statusBarHeight}, ActionBar.java:1400), so
     * the list's padding is rebuilt here - ChatActivity recomputes its own the same way when its
     * insets change.
     */
    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
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

    private void loadTopic(long topicId) {
        this.topicId = topicId;
        // The first page is on its way: a chat shows its ring while it has no messages, and this
        // is the same moment (ChatActivity:9197).
        showProgressView(bubbles.isEmpty());
        container = NativeDo.container(getParentActivity());
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
        DiscourseLoader.INSTANCE.topic(container, topicId, null, detail -> {
            int position = reporter.start(detail);
            if (position > 1 && !carriesFloor(detail, position)) {
                loadWindow(position, position);
                return kotlin.Unit.INSTANCE;
            }
            show(detail, position > 1 ? position : 0);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showTopicFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The window of posts around [postNumber], with the list left on [scrollTo] when it is given. */
    private void loadWindow(int postNumber, int scrollTo) {
        retryAction = () -> loadWindow(postNumber, scrollTo);
        DiscourseLoader.INSTANCE.topic(container, topicId, postNumber, detail -> {
            show(detail, scrollTo);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showTopicFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** What has arrived: the topic's stream, its floors, and where in it the reader is standing. */
    private kotlin.Unit show(TopicDetail detail, int scrollTo) {
        // The page is here: ChatActivity turns its ring off with the same call it turned it on
        // (9197 -> showProgressView(false)).
        showProgressView(false);
        if (failureView != null) {
            failureView.setVisibility(View.GONE);
        }
        topicDetail = detail;
        topicBookmarked = detail.getBookmarked();
        updateBookmarkMenuItem();
        // The site's own title is the authoritative one; a link that named no title gets it here,
        // and it goes into the center capsule the way a chat's title does.
        if (avatarContainer != null) {
            String loadedTitle = detail.getTitle();
            if (loadedTitle != null && !loadedTitle.trim().isEmpty()) {
                avatarContainer.setTitle(loadedTitle.trim());
            }
            bindTopicAuthor(detail);
            bindSubtitle(detail);
        }
        streamPostIds = detail.getStreamPostIds();
        reporter.start(detail);
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

    /** Whether the answer the site gave already carries that floor, which needs no second read. */
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
            return;
        }
        topicAuthor = DiscourseAvatars.INSTANCE.user(
                author.getUsername(),
                author.getDisplayName(),
                author.getAvatarTemplate(),
                container.getBaseUrl());
        // The container's avatar ask goes through MessagesController's own user table, so the
        // synthetic user is registered there first (the rows do the same when they bind).
        MessagesController.getInstance(currentAccount).putUser(topicAuthor, true);
        avatarContainer.setUserAvatar(topicAuthor);
        bindAuthorAvatarImage();
        String url = DiscourseAvatars.INSTANCE.url(
                author.getAvatarTemplate(), container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
        if (url != null && !DiscourseAvatars.INSTANCE.isCached(topicAuthor)) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, topicAuthor, () -> {
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
        ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(topicAuthor);
        if (location == null) {
            return;
        }
        org.telegram.ui.Components.BackupImageView avatarView = avatarContainer.getAvatarImageView();
        if (avatarView != null) {
            avatarView.setImage(location, "50_50", avatarView.getImageReceiver().getDrawable(), topicAuthor);
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
        if (container == null || detail.getCategoryId() <= 0) {
            setCapsuleSubtitle(null);
            return;
        }
        DiscourseLoader.INSTANCE.categoryName(container, detail.getCategoryId(), name -> {
            setCapsuleSubtitle(name);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void setCapsuleSubtitle(String categoryName) {
        if (avatarContainer == null) {
            return;
        }
        int replies = topicDetail == null ? 0 : Math.max(0, topicDetail.getTotalPosts() - 1);
        String subtitle = categoryName == null || categoryName.isEmpty() ? null : categoryName;
        if (replies > 0) {
            String count = replies + " 回复";
            String composed = subtitle == null ? count : subtitle + " · " + count;
            if (subtitleFits(composed)) {
                subtitle = composed;
            }
        }
        avatarContainer.setSubtitle(subtitle);
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
     * (ChatAvatarContainer's container listener).
     */
    private void openTopicAuthorProfile() {
        if (topicAuthor == null || topicAuthor.username == null || topicAuthor.username.isEmpty()) {
            return;
        }
        if (getParentActivity() instanceof NativeDoActivity) {
            ((NativeDoActivity) getParentActivity()).present(
                    io.github.baba983.nativedo.profile.ProfileFragment.forUser(
                            topicAuthor.username, topicAuthor.first_name));
        }
    }

    /**
     * What the viewport holds: how far the reader has come, and which floors are being read. The
     * dwell clock is only credited to the floors on screen, so the window is reported with the
     * position rather than the position alone.
     */
    private void reportViewport() {
        if (reporter == null || listView == null) {
            return;
        }
        androidx.recyclerview.widget.LinearLayoutManager manager =
                (androidx.recyclerview.widget.LinearLayoutManager) listView.getLayoutManager();
        if (manager == null) {
            return;
        }
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        if (last < 0) {
            return;
        }
        java.util.List<Integer> visible = new ArrayList<>();
        for (int position = Math.max(0, first); position <= last && position < bubbleFloors.size(); position++) {
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
        if (reporter != null) {
            reporter.setForeground(true);
        }
    }

    /**
     * Back means the selection first, the topic second - upstream's chat does the same: a back press
     * while text is selected drops the selection and the topic stays.
     */
    @Override
    public boolean onBackPressed(boolean invoked) {
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            if (invoked) {
                textSelectionHelper.clear();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onPause() {
        super.onPause();
        leaveTopic();
    }

    @Override
    public void onFragmentDestroy() {
        if (reporter != null) {
            // The screen is gone: the ticker must be too, or it keeps crediting floors of a topic
            // nobody is looking at.
            reporter.stop();
        }
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.didSetNewWallpapper);
        if (avatarContainer != null) {
            avatarContainer.onDestroy();
        }
        leaveTopic();
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
        for (int index = 0; index < bubbleFloors.size(); index++) {
            Integer number = floors.get(bubbleFloors.get(index)).getPostNumber();
            if (number != null && number == floor) {
                listView.scrollToPosition(index);
                return;
            }
        }
    }

    private void bind(TopicDetail detail) {
        floors.clear();
        bubbles.clear();
        bubbleFloors.clear();
        bubbleBodies.clear();
        users.clear();
        avatarTemplates.clear();
        List<Post> loaded = firstPage(detail);
        for (Post post : loaded) {
            add(post, detail.getId());
        }
        // The stock cell resolves an author through MessagesController, not through the map it was
        // built with, so the authors have to be registered there before the cells bind. putUser with
        // fromCache=true is a map write: no request, no database.
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (TLRPC.User user : users.values()) {
            controller.putUser(user, true);
        }
        notifyPostsChanged();
        for (int index = 0; index < bubbles.size(); index++) {
            fetchPostMedia(index);
        }
        fetchAvatars();
    }

    /**
     * Avatars are part of the author, not of the post, so they are fetched once per participant and
     * the rows are re-bound when one lands: the cell asked for the avatar when it was bound, and
     * upstream's list only draws one that has finished loading.
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
                // The cell resolves its author through MessagesController on every bind, so the
                // refreshed user has to go back in there before the row is rebuilt.
                MessagesController.getInstance(currentAccount).putUser(user, true);
                rebuildRows();
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
        nextPageStart = streamPositionAfter(all);
        return all;
    }

    /**
     * Where the stream continues from the posts the payload already carries. A topic opened at a
     * floor arrives as a window from the middle of the thread, so the next page is the stream id
     * after the last post here - which is the same thing as the size of the window only when the
     * window starts at the first floor.
     */
    private int streamPositionAfter(List<Post> loaded) {
        if (!loaded.isEmpty()) {
            int index = streamPostIds.indexOf(loaded.get(loaded.size() - 1).getId());
            if (index >= 0) {
                return index + 1;
            }
        }
        return Math.min(loaded.size(), streamPostIds.size());
    }

    /** One floor, drawn as the bubbles its body splits into: its text segments and its polls. */
    private void add(Post post, long topicId) {
        int floor = floors.size();
        floors.add(post);
        List<MessageObject> floorBubbles = new ArrayList<>();
        List<PreparedPostBody> floorBodies = new ArrayList<>();
        buildBubbles(post, topicId, floorBubbles, floorBodies);
        for (int index = 0; index < floorBubbles.size(); index++) {
            bubbles.add(floorBubbles.get(index));
            bubbleBodies.add(floorBodies.get(index));
            bubbleFloors.add(floor);
        }
        String avatarTemplate = post.getAuthorAvatarTemplate();
        if (avatarTemplate != null && !avatarTemplate.isEmpty()) {
            avatarTemplates.put(DiscourseRows.userId(post.getAuthorUsername()), avatarTemplate);
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
            if (segment.isPoll()) {
                PostPoll poll = pollNamed(post, segment.pollName);
                if (poll == null) {
                    continue;
                }
                outBubbles.add(DiscoursePolls.message(post, poll, topicId, users, container.getBaseUrl()));
                outBodies.add(null);
            } else if (segment.isOnebox()) {
                DiscourseOnebox.Card card = DiscourseOnebox.card(segment.html, container.getBaseUrl());
                if (card == null) {
                    continue;
                }
                outBubbles.add(DiscourseOnebox.message(post, card, topicId, users, container.getBaseUrl()));
                // The card's thumbnail is a post image: the list downloads it and rebinds the row.
                outBodies.add(card.body);
            } else {
                PreparedPostBody body = DiscourseImageBridge.INSTANCE.prepare(
                        segment.html, container.getBaseUrl());
                MessageObject message = DiscourseRows.postMessage(
                        post, topicId, users, body, container.getBaseUrl());
                if (message.messageOwner.rich_message.blocks.isEmpty()
                        && message.messageOwner.rich_message.documents.isEmpty()) {
                    continue;
                }
                outBubbles.add(message);
                outBodies.add(body);
            }
        }
        if (!outBubbles.isEmpty()) {
            DiscourseRows.attachReactions(outBubbles.get(outBubbles.size() - 1), post);
        }
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

    /** Fetches the next slice of the topic's stream once the list nears its end. */
    private void maybeLoadNextPage() {
        if (loadingPage || container == null || listView == null) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null || manager.findLastVisibleItemPosition() < bubbles.size() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        if (nextPageStart >= streamPostIds.size()) {
            return;
        }
        int end = Math.min(nextPageStart + PAGE_SIZE, streamPostIds.size());
        List<Long> page = new ArrayList<>(streamPostIds.subList(nextPageStart, end));
        loadingPage = true;
        long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        DiscourseLoader.INSTANCE.topicPosts(container, topicId, page, loaded -> {
            loadingPage = false;
            nextPageStart = end;
            int firstNew = bubbles.size();
            for (Post post : loaded) {
                add(post, topicId);
            }
            MessagesController controller = MessagesController.getInstance(currentAccount);
            for (TLRPC.User user : users.values()) {
                controller.putUser(user, true);
            }
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemRangeInserted(firstNew, bubbles.size() - firstNew);
            }
            for (int index = firstNew; index < bubbles.size(); index++) {
                fetchPostMedia(index);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            loadingPage = false;
            // The topic is on screen, so the rows stay; the failure is said and the next scroll
            // asks for the page again.
            showTopicFailure(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void toggleReaction(long postId, String reactionId) {
        if (container == null) {
            return;
        }
        DiscourseLoader.INSTANCE.toggleReaction(container, postId, reactionId, () -> {
            // The site is the only place that knows the new count, so the topic is read again.
            loadTopic(topicId);
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
            if (bubbleFloors.get(index) == floor) {
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

    /**
     * What the reader can do with a post: copy its text, copy a link to it, bookmark it, or reply
     * to it. The choices are upstream's own scrim popup, anchored on the bubble that was pressed —
     * the shape a chat uses for a message's menu — so the bubble stays lit above the dim while the
     * reader decides.
     */
    private void showMessageMenu(android.view.View scrim, int floor) {
        if (getParentActivity() == null || scrim == null || floor < 0 || floor >= floors.size()) {
            return;
        }
        boolean bookmarked = floors.get(floor).isBookmarked();
        org.telegram.ui.Components.ItemOptions options =
                org.telegram.ui.Components.ItemOptions.makeOptions(this, scrim);
        options.add(org.telegram.messenger.R.drawable.msg_copy, "复制",
                () -> AndroidUtilities.addToClipboard(plainText(floor)));
        options.add(org.telegram.messenger.R.drawable.msg_link, "复制链接", () -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", postUrl(floor)));
        });
        options.add(
                bookmarked
                        ? org.telegram.messenger.R.drawable.msg_unfave
                        : org.telegram.messenger.R.drawable.msg_fave,
                bookmarked ? "取消书签" : "添加书签",
                () -> toggleBookmark(floor));
        options.add(org.telegram.messenger.R.drawable.menu_reply, "回复", () -> replyTo(floor));
        if (scrim instanceof ChatMessageCell && hasSelectableText((ChatMessageCell) scrim)) {
            options.add(org.telegram.messenger.R.drawable.msg_select, "选择文本",
                    () -> startTextSelection((ChatMessageCell) scrim));
        }
        options.show();
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
        return container.getBaseUrl() + "t/" + topicId + "/" + floors.get(floor).getId();
    }

    /** The floor a bubble draws; -1 when the message is not one of the list's bubbles. */
    private int floorOf(MessageObject message) {
        int index = bubbles.indexOf(message);
        return index >= 0 && index < bubbleFloors.size() ? bubbleFloors.get(index) : -1;
    }

    /**
     * What the composer's own state decides for the reader: the send button and our attach strip.
     * Upstream offers its voice button while the field is empty and this client has nothing behind
     * it, so the button is hidden until there is something to send. It is told after every change
     * upstream makes, because those are the moments it decides the button's state again.
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
        android.view.View button = composer.getSendButton();
        if (button == null) {
            return;
        }
        int wanted = hasText ? android.view.View.VISIBLE : android.view.View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
        }
        // The composer's own decision runs last: it is what draws the send arrow into the button, and
        // our visibility write above is only for the shape of the button's absence. See the same
        // comment on the new-topic screen: a change that arrives in one piece (a paste, a draft)
        // would otherwise be drawn from the state before it.
        composer.checkSendButton(false);
    }

    /**
     * Replying to one post is a post number, not a post id: the site numbers posts within a topic
     * and the composer does not need to know which floor it answers.
     */
    private void replyTo(int floor) {
        if (floor < 0 || floor >= floors.size()) {
            return;
        }
        Post post = floors.get(floor);
        Integer number = post.getPostNumber();
        replyToPostNumber = number;
        // Discourse addresses a reply with @username, and the author of the post is what the
        // reader answers to.
        String author = post.getAuthorUsername();
        if (composer != null && author != null && !author.isEmpty() && number != null) {
            composer.setFieldText("@" + author + " ");
            composer.setFieldFocused();
            AndroidUtilities.showKeyboard(composer.getEditField());
        }
    }

    private void sendReply(String raw, Integer replyToPostNumber) {
        if (container == null || raw.trim().isEmpty()) {
            return;
        }
        DiscourseLoader.INSTANCE.reply(container, topicId, raw, replyToPostNumber, () -> {
            if (composer != null) {
                composer.setFieldText("");
            }
            this.replyToPostNumber = null;
            loadTopic(topicId);
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
            showReplyError(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void showReplyError(Throwable error) {
        showError("回复失败", error);
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
        // The read is over, so the ring goes with it (ChatActivity:20606 shows the same).
        showProgressView(false);
        lastFailure = TopicLoadFailure.of(error);
        org.telegram.messenger.FileLog.e("topic failed", error);
        if (!bubbles.isEmpty()) {
            // The topic is on screen, so the rows the reader has are still good: this was a page
            // that did not arrive, not the topic. It is said in passing - a modal would stop the
            // reader over something they did not ask for - and the next scroll asks for it again.
            android.widget.Toast.makeText(
                    org.telegram.messenger.ApplicationLoader.applicationContext,
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            io.github.baba983.nativedo.R.string.topic_page_failed),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (failureView == null) {
            return;
        }
        failureView.setText(failureText(lastFailure, error));
        failureView.showTextView();
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
            runRetry();
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

    private String failureText(TopicLoadFailure reason, Throwable error) {
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
        String line = org.telegram.messenger.ApplicationLoader.applicationContext.getString(id);
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? line : line + "\n" + message.trim();
    }

    /** What the composer tells us: the text, and that the send button was pressed. */
    private class ReplyDelegate extends DiscourseComposerDelegate {

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
            String raw = message == null ? "" : message.toString().trim();
            if (!raw.isEmpty()) {
                sendReply(raw, replyToPostNumber);
            }
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }

        /** Attachments in a reply go to the site first: what the field gets is the markdown its own composer would write. */
        @Override
        public void didPressAttachButton() {
            openAttachmentPicker();
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
     * The media blocks size themselves from the photo, so a cell can be bound before its image
     * exists; re-binding the row is what makes the cell ask again and find the file. A document
     * does the same with its button state, which only turns into "open" once the file is there.
     */
    private void fetchPostMedia(int index) {
        if (container == null || index < 0 || index >= bubbles.size()) {
            return;
        }
        Runnable onReady = () -> {
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemChanged(index);
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
        MessageObject message = bubbles.get(index);
        Runnable onAttachmentsReady = () -> {
            DiscourseImageBridge.INSTANCE.applyAttachmentFiles(message.messageOwner, attachments);
            onReady.run();
        };
        DiscourseLoader.INSTANCE.cacheImages(container, images, () -> {
            onReady.run();
            return kotlin.Unit.INSTANCE;
        });
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
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class PostAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(
                    parent.getContext(), currentAccount, false, sharedResources, resourceProvider);
            cell.setDelegate(new PostCellDelegate());
            cell.isChat = true;
            cell.setFullyDraw(false);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
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
            if (bubbleFloors.get(index) == floor) {
                return index;
            }
        }
        return position;
    }

    /** The last bubble of the floor [position] draws. */
    private int lastBubbleOfFloor(int position) {
        int floor = bubbleFloors.get(position);
        for (int index = bubbleFloors.size() - 1; index >= 0; index--) {
            if (bubbleFloors.get(index) == floor) {
                return index;
            }
        }
        return position;
    }

    /** Upstream's own callbacks; a tap on a photo or a link leaves for upstream's viewers. */
    private class PostCellDelegate implements ChatMessageCell.ChatMessageCellDelegate {

        /**
         * A tap on one of the reaction chips. The id comes back from the emoticon the chip drew,
         * which is the only field a chip carries, so the mapping has to be reversible.
         */
        @Override
        public void didPressReaction(ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
            if (longpress || reaction == null || !(reaction.reaction instanceof TLRPC.TL_reactionEmoji)) {
                return;
            }
            int floor = floorOf(cell.getMessageObject());
            if (floor < 0) {
                return;
            }
            String reactionId = DiscourseReactions.INSTANCE.idOf(
                    ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon);
            toggleReaction(floors.get(floor).getId(), reactionId);
        }

        /** Long press on any bubble of a floor: the menu a reader opens on a post. */
        @Override
        public void didLongPress(ChatMessageCell cell, float x, float y) {
            int floor = floorOf(cell.getMessageObject());
            if (floor < 0) {
                return;
            }
            showMessageMenu(cell, floor);
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
                return ArticlePhotoViewer.open(TopicFragment.this, message, block);
            }
            DiscourseLoader.INSTANCE.fetchVideo(
                    container,
                    video,
                    () -> {
                        ArticlePhotoViewer.open(TopicFragment.this, message, block);
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> {
                        ArticlePhotoViewer.stream(TopicFragment.this, block);
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
            // turned on. Anything else is a real external link and goes to the browser.
            io.github.baba983.nativedo.core.web.DiscourseTopicLink topicLink =
                    container == null ? null
                            : io.github.baba983.nativedo.core.web.DiscourseLinks.topicLink(
                                    target, container.getBaseUrl());
            if (topicLink != null && topicLink.getTopicId() > 0) {
                Integer floor = topicLink.getFloor() > 0 ? topicLink.getFloor() : null;
                presentFragment(TopicFragment.forFloor(topicLink.getTopicId(), null, floor));
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
