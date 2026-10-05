package io.github.baba983.nativedo.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ProfileGooeyView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.core.model.UserProfile;
import io.github.baba983.nativedo.feature.profile.data.ProfileExtrasRepository;
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * A linux.do reader's profile: {@code /u/{username}.json} drawn with upstream's own list cells and
 * upstream's own collapsing profile header.
 *
 * <p>Upstream's {@code ProfileActivity} cannot be this screen. It is a Telegram account's profile:
 * {@code onFragmentCreate} loads the full user through tgnet ({@code loadFullUser} sends
 * {@code TL_users_getFullUser} and the answer is written into Telegram's database), and what it
 * shows beyond the user is the account's own photos, stories, gifts and contacts. A linux.do reader
 * has none of those, so this screen keeps its own {@link BaseFragment} and builds the head out of
 * upstream's own pieces: a {@link ProfileGooeyView} holding the avatar, and the two text views
 * upstream moves into the action bar as the header collapses - the same header behavior the owner
 * sees in Telegram's own profile. The screen's own action bar hosts that header the way upstream's
 * profile does (transparent, inside the fragment's view), and the sections below it are
 * {@link HeaderCell}, {@link TextDetailCell} and {@link TextInfoPrivacyCell}, every one of them
 * upstream's, drawn the way upstream draws a list row.</p>
 */
public class ProfileFragment extends BaseFragment implements NativeDoMainTabsActivity.TabPage {

    private static final String ARG_USERNAME = "username";
    private static final String ARG_DISPLAY_NAME = "displayName";
    /** Whether the screen is a shell tab root, which has nothing behind it to go back to. */
    private static final String ARG_ROOT = "root";

    /**
     * The screen the avatar flies in from: the X of the chat header's avatar in the profile's own
     * coordinate space. Upstream plays the same animation for a profile opened from the chat header
     * ({@code playProfileAnimation == 2}, with {@code prevAvatarTranslation} captured through
     * {@code ViewPositionWatcher.computeXCoordinateInParent}, ProfileActivity.java:10080), and
     * ProfileActivity:8623-8690 lays the avatar out along the path this argument starts.
     */
    private static final String ARG_AVATAR_X = "avatarX";

    private static final int TYPE_SECTION = 1;
    private static final int TYPE_DETAIL = 2;
    private static final int TYPE_BIO = 3;
    private static final int TYPE_ACTION = 4;
    /** A row of the reader's own things, which opens a screen of its own. */
    private static final int TYPE_LINK = 5;
    /** The tab area - the strip and its pages - the last row of the info list. */
    private static final int TYPE_MEDIA = 6;

    /** 话题: the topics the reader started (`/user_actions.json?filter=4`). */
    private static final int TAB_TOPICS = 0;
    /** 回复: the posts they wrote (`filter=5`). */
    private static final int TAB_REPLIES = 1;
    /** 点赞: the posts they liked (`filter=1`). */
    private static final int TAB_LIKES = 2;
    /** 书签: the reader's own bookmarks - the site answers this list for nobody else. */
    private static final int TAB_BOOKMARKS = 3;
    /** 徽章: the badges the site granted them (`/user-badges/{u}.json`). */
    private static final int TAB_BADGES = 4;
    private static final int TAB_COUNT = 5;

    /** How close a page's list comes to its end before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    /** The profile's own writes: the discourse-follow plugin's route pair (see endpoints.md). */
    private static final String FOLLOW_LEVEL_MUTED = "mute";
    private static final String FOLLOW_LEVEL_NORMAL = "normal";

    private FrameLayout frameLayout;
    private RecyclerListView listView;
    /**
     * DialogsActivity's own shimmer (4059-4064): the row this list draws - the reader's activity
     * rows are {@link DialogCell}s, the same as the topic list's - while the profile has drawn
     * none yet. It is the list's empty view, so it stands only until the first answer.
     */
    private FlickerLoadingView progressView;
    private final List<Row> rows = new ArrayList<>();
    /** The row of the reader's own bookmarks, or -1 when this is not their own profile. */
    private int bookmarksRow = -1;
    /** The row of the tab area, or -1 while the profile has not answered. */
    private int mediaRow = -1;
    /** The four actions under the header, upstream's own row (ProfileActivity.java:3880-3973). */
    private ProfileActionsView actionsView;
    private boolean actionsBuilt;
    private ProfileSharedMediaView mediaView;
    /** One page per tab; the first {@link #pageCount()} entries are in use. */
    private final TabPageState[] tabPages = new TabPageState[TAB_COUNT];
    /** The activity streams by filter, one pager each, in the order the tabs ask for them. */
    private ProfileTabFeed topicsFeed;
    private ProfileTabFeed repliesFeed;
    private ProfileTabFeed likesFeed;
    private UserProfile profile;
    /** The site's own action state, which the buttons flip after a successful write. */
    private boolean isFollowed;
    private boolean isMuted;
    private boolean ownProfile;
    /** The gesture bar the list has to clear; the shell forwards it, this screen pads its end. */
    private int bottomInset;
    /** The site's bio with its cooked tags taken off, which both the head and the bio row draw. */
    private String bioText = "";
    private TLRPC.User avatarUser;
    private AppContainer container;
    /** Summary stats from `/u/{u}/summary.json`; null until (and if) they arrive. */
    private io.github.baba983.nativedo.core.model.UserSummary summary;

    // The opening animation, ported from ProfileActivity's `playProfileAnimation`:
    // 2 is the avatar's flight from the chat header's capsule (ChatAvatarContainer.openProfile
    // picks it when the tap was on the avatar), 0 is the ordinary transition. Keep upstream's
    // duration (250 ms), its CubicBezierInterpolator.DEFAULT and its layout path.
    private int playProfileAnimation;
    private boolean openAnimationInProgress;
    private float avatarAnimationProgress;
    private float prevAvatarTranslation;
    private int avatarColor;
    private final float[] backwardInitialValues = new float[4];
    private boolean allowProfileAnimation = true;


    // The collapsing header, ported from ProfileActivity: the gooey avatar and the two text views
    // its needLayout() translates and scales as the list scrolls.
    private TopView topView;
    private ProfileActivity.AvatarImageView avatarImage;
    private FrameLayout avatarContainer;
    private FrameLayout avatarContainer2;
    private ProfileGooeyView avatarGooey;
    private SimpleTextView nameTextView;
    private LinkSpanDrawable.ClickableSmallTextView onlineTextView;
    /** The list's offset from the header's rest position; 0 when the header is fully collapsed. */
    private float extraHeight;
    private float pullUpProgress;
    private float avatarX;
    private float avatarY;
    private float avatarScale;
    private float nameX;
    private float nameY;
    private float onlineX;
    private float onlineY;

    // The pull-down expansion, ported from ProfileActivity's expandAnimator branch: the list keeps
    // the header's full width as its own top padding, so the strip above the first row is the
    // stretch room; the finger past a third of it commits to the full-width square, and the
    // release either finishes the expansion or snaps the header back to its rest position.
    private LinearLayoutManager layoutManager;
    private boolean isPulledDown;
    private boolean allowPullingDown;
    private boolean justFullyExpanded;
    private float expandProgress;
    private float listViewVelocityY;
    private VelocityTracker velocityTracker;
    private ValueAnimator expandAnimator;
    private final float[] expandAnimatorValues = new float[]{0f, 1f};
    private float currentExpanAnimatorFracture;

    /**
     * The day/night switch recolours the profile in place (its views are not rebuilt when the tab
     * host is the only fragment on the stack; see NativeDoActivity's theme handler): the rows are
     * bound again, and the header's own colours are re-applied at the expansion the reader had.
     */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
                if (actionBar != null) {
                    actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_avatar_actionBarSelectorBlue), false);
                    actionBar.updateColors();
                    actionBar.invalidate();
                }
                if (avatarContainer != null && listView != null) {
                    setAvatarExpandProgress(currentExpanAnimatorFracture);
                }
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (progressView != null) {
                    // The shimmer reads the theme colours at draw time; a redraw is its recolour.
                    progressView.invalidate();
                }
                if (topView != null) {
                    topView.invalidate();
                }
                if (frameLayout != null) {
                    frameLayout.invalidate();
                }
            };
    private float currentExpandAnimatorValue;
    private float expandNameYStartedFrom;
    private float expandOnlineYStartedFrom;
    /** The list's head room has been seeded once its width and its first rows are known. */
    private boolean headerSeeded;
    private int seededWidth;
    /** The site's larger avatar, drawn over the small one while the header is expanded. */
    private TLRPC.User expandedAvatarUser;
    private boolean expandedAvatarBound;

    /** The tab host's strip height, added to the gesture bar's own so the last row clears both. */
    private int additionNavigationBarHeight;

    public static ProfileFragment forUser(String username, String displayName) {
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username == null ? "" : username);
        args.putString(ARG_DISPLAY_NAME, displayName == null ? "" : displayName);
        return new ProfileFragment(args);
    }

    /**
     * The reader's own profile as a shell tab root: the same screen, but there is nothing behind it
     * to go back to, so it draws no back button - upstream's own profile tab does the same
     * ({@code ProfileActivity}'s {@code hasMainTabs} draws the back button only when it is a step,
     * not a root).
     */
    public static ProfileFragment forRoot(String username, String displayName) {
        ProfileFragment fragment = forUser(username, displayName);
        fragment.getArguments().putBoolean(ARG_ROOT, true);
        fragment.getArguments().putBoolean("hasMainTabs", true);
        return fragment;
    }

    /**
     * The same screen, opened from the chat header's capsule: {@code avatarX} is the capsule
     * avatar's X in this screen's own coordinate space, and the avatar flies from there to its
     * centered rest position exactly as upstream's {@code playProfileAnimation == 2} does
     * (ChatAvatarContainer.openProfile → {@code setPlayProfileAnimation(2)}).
     */
    public static ProfileFragment forUserFromAvatar(String username, String displayName, float avatarX) {
        ProfileFragment fragment = forUser(username, displayName);
        fragment.getArguments().putFloat(ARG_AVATAR_X, avatarX);
        return fragment;
    }

    public ProfileFragment(Bundle args) {
        super(args);
    }

    @Override
    protected View createView(Context context) {
        final boolean root = getArguments() != null && getArguments().getBoolean(ARG_ROOT, false);
        final boolean hasMainTabs = getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
        // The avatar's flight is the chat header's animation (ProfileActivity's
        // `playProfileAnimation == 2`); the ordinary transition is everything else.
        playProfileAnimation = getArguments() != null && getArguments().containsKey(ARG_AVATAR_X) ? 2 : 0;
        prevAvatarTranslation = getArguments() == null ? 0f : getArguments().getFloat(ARG_AVATAR_X, 0f);
        allowProfileAnimation = playProfileAnimation != 0 && !hasMainTabs;
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        // The profile draws behind its own transparent action bar, the way upstream's profile does
        // (ProfileActivity.java:2470-2478), so it keeps its view the whole screen.
        actionBar.setAddToContainer(false);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setForceSkipTouches(true);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        if (!root) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        actionsView = new ProfileActionsView(context, AndroidUtilities.dp(74));
        actionsView.setOnActionClickListener(this::onActionClicked);
        actionsView.setVisibility(View.GONE);

        extraHeight = getHeaderExtraHeight();

        // The root of the screen is upstream's NestedFrameLayout (ProfileActivity.java:1710-1810):
        // the outer list is its child, and while the media row is pinned to the list's top an
        // upward drag the list cannot take continues into the page's own list.
        frameLayout = new NestedFrameLayout(context);

        // The list's own touch handling is upstream's ClippedListView (ProfileActivity.java:4129-4235):
        // the release either finishes the expansion or snaps the header back to rest, and the
        // velocity it measured is what the animators' duration is derived from.
        listView = new RecyclerListView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                final int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                    velocityTracker.addMovement(e);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(e);
                        velocityTracker.computeCurrentVelocity(1000);
                        listViewVelocityY = velocityTracker.getYVelocity(e.getPointerId(e.getActionIndex()));
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (velocityTracker != null) {
                        if (action == MotionEvent.ACTION_UP) {
                            velocityTracker.addMovement(e);
                            velocityTracker.computeCurrentVelocity(1000);
                            listViewVelocityY = velocityTracker.getYVelocity(e.getPointerId(e.getActionIndex()));
                        }
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
                boolean result = super.onTouchEvent(e);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    final View view = layoutManager.findViewByPosition(0);
                    if (view != null && allowPullingDown) {
                        if (isPulledDown) {
                            final int actionBarHeight = ActionBar.getCurrentActionBarHeight()
                                    + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
                            listView.smoothScrollBy(0, view.getTop() - listView.getMeasuredWidth()
                                    - getActionsExtraHeight() + actionBarHeight,
                                    CubicBezierInterpolator.EASE_OUT_QUINT);
                        } else {
                            listView.smoothScrollBy(0, view.getTop() - getHeaderExtraHeight(),
                                    CubicBezierInterpolator.EASE_OUT_QUINT);
                        }
                    }
                }
                return result;
            }
        };
        // The scroll clamp is upstream's (ProfileActivity.java:4283-4312): the stretch is damped
        // while the finger drags it, and a scroll toward the rows gives the pull-down its rest.
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                final View view = findViewByPosition(0);
                if (view != null) {
                    final int canScroll = view.getTop() - getHeaderExtraHeight();
                    if (!allowPullingDown && canScroll > dy) {
                        dy = canScroll;
                        allowPullingDown = canPullDown();
                    } else if (allowPullingDown) {
                        if (dy >= canScroll) {
                            dy = canScroll;
                            allowPullingDown = false;
                        } else if (listView.getScrollState() == RecyclerListView.SCROLL_STATE_DRAGGING
                                && !isPulledDown) {
                            dy /= 2;
                        }
                    }
                }
                if (justFullyExpanded && !listView.isFlingerWorking()) {
                    return 0;
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        };
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(new ProfileAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, getHeaderExtraHeight(), 0, listBottomPadding());
        // The rows of the info list are text to read; the tappable things are the action row's
        // buttons and the pages' own rows, each with their own click handling.
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkListViewScroll();
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        // DialogsActivity's own first-load view (4059-4062): the row this list is about to draw,
        // standing in for it while the profile has not answered. It rides behind the header, so
        // the screen still shows what it already knows.
        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progressView.setVisibility(View.GONE);
        listView.setEmptyView(progressView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        frameLayout.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // The header's backdrop: the rows that scroll under the avatar and the name stay behind it.
        topView = new TopView(context);
        frameLayout.addView(topView);

        avatarContainer2 = new FrameLayout(context);
        avatarContainer2.addView(avatarGooey = new ProfileGooeyView(context),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Upstream's avatar container and picture: the container's scale is what the gooey view
        // follows, and its measure and scale updates are where the gooey view is driven from
        // (ProfileActivity.java:5220-5232 and 5326-5357).
        avatarContainer = new FrameLayout(context) {
            @Override
            public void setScaleX(float scaleX) {
                super.setScaleX(scaleX);
                updateGooey();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                updateGooey();
            }
        };
        avatarContainer.setPivotX(0);
        avatarContainer.setPivotY(0);
        avatarGooey.addView(avatarContainer, LayoutHelper.createFrame(100, 100, Gravity.TOP | Gravity.LEFT));
        avatarImage = new ProfileActivity.AvatarImageView(context);
        avatarImage.createBlurEffect(getActionsExtraHeight());
        avatarImage.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarImage.setRoundRadiusForExpand(getSmallAvatarRoundRadius());
        avatarImage.setPivotX(0);
        avatarImage.setPivotY(0);
        avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // The two text views upstream's needLayout() moves into the action bar as the header
        // collapses (ProfileActivity.java:5548-5637); the name is the header's readable copy.
        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(getThemedColor(Theme.key_profile_title));
        nameTextView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(4));
        nameTextView.setTextSizePx(AndroidUtilities.dp(17.5f));
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        nameTextView.setPivotX(0);
        nameTextView.setPivotY(0);
        nameTextView.setText(displayName());
        nameTextView.setScrollNonFitText(true);
        nameTextView.setEllipsizeByGradient(true);
        avatarContainer2.addView(nameTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 109, -6, 0, 0));

        onlineTextView = new LinkSpanDrawable.ClickableSmallTextView(context);
        onlineTextView.setEllipsizeByGradient(true);
        onlineTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        onlineTextView.setTextSizePx(AndroidUtilities.dp(13.5f));
        onlineTextView.setGravity(Gravity.LEFT);
        onlineTextView.setAlpha(1.0f);
        onlineTextView.setPivotX(AndroidUtilities.dp(8));
        onlineTextView.setPivotY(AndroidUtilities.dp(8));
        onlineTextView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));
        onlineTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        onlineTextView.setText("@" + username());
        avatarContainer2.addView(onlineTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 105, -2, 4, 0));

        // Upstream's release animator (ProfileActivity.java:5791-5815): the fraction it reports is
        // what carries the avatar, the round radius and the two titles between the two layouts.
        expandAnimator = ValueAnimator.ofFloat(0f, 1f);
        expandAnimator.addUpdateListener(anim -> setAvatarExpandProgress(anim.getAnimatedFraction()));
        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

        frameLayout.addView(avatarContainer2, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START));
        // The action row rides inside the header container the way ProfileActivity places it
        // (avatarContainer2.addView(actionsView, createFrame(-1, -1)), ProfileActivity.java:5491);
        // its Y is what needLayout() moves as the header collapses.
        avatarContainer2.addView(actionsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(actionBar);

        // The shell's insets land on the fragment view the way NativeDoActivity's land on its
        // content view; this screen keeps the last list row clear of the gesture bar. The bottom
        // is read from the same helper the shell uses, so the list pads by exactly the bar.
        ViewCompat.setOnApplyWindowInsetsListener(frameLayout, (view, insets) -> {
            bottomInset = AndroidUtilities.getDefaultWindowInsets(insets, false).bottom;
            listView.setPadding(0, listView.getPaddingTop(), 0, listBottomPadding());
            // The pages clear the same gesture bar the info list does.
            for (TabPageState page : tabPages) {
                if (page != null) {
                    page.listView.setPadding(0, page.listView.getPaddingTop(), 0, listBottomPadding());
                }
            }
            return WindowInsetsCompat.CONSUMED;
        });

        fragmentView = frameLayout;
        loadProfile();
        ViewCompat.requestApplyInsets(frameLayout);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        super.onFragmentDestroy();
    }

    /** The room the list keeps under its last row: the usual list end plus the gesture bar and
     * the tab host's strip. */
    private int listBottomPadding() {
        return AndroidUtilities.dp(4) + bottomInset + additionNavigationBarHeight;
    }

    /** A second tap on the reader's tab: the list goes back to its top. */
    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    /** The page reaches the bottom of the window itself; a pushed profile is laid out as before. */
    @Override
    public boolean isSupportEdgeToEdge() {
        return getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    /**
     * An action opens the topic it happened in, at the floor it happened on: the site answers a
     * request for a floor with the window of posts around it, so the reader lands on the post the
     * row is about rather than at the top of a topic that may be hundreds of floors long.
     */
    private void presentAction(TopicSummary action) {
        presentFragment(TopicFragment.forFloor(
                action.getId(), action.getTitle(), action.getTargetPostNumber()));
    }

    private String username() {
        return getArguments() == null ? "" : getArguments().getString(ARG_USERNAME, "");
    }

    private String displayName() {
        String name = getArguments() == null ? "" : getArguments().getString(ARG_DISPLAY_NAME, "");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return username();
    }

    private void loadProfile() {
        container = NativeDo.container(getParentActivity());
        ProfileLoader.INSTANCE.load(container, username(), loaded -> {
            profile = loaded;
            isFollowed = loaded.isFollowed();
            isMuted = loaded.getMuted();
            ownProfile = isOwnProfile();
            avatarUser = DiscourseAvatars.INSTANCE.user(
                    loaded.getUsername(),
                    loaded.getDisplayName(),
                    loaded.getAvatarTemplate(),
                    container.getBaseUrl());
            // The pull-down draws the same picture across the whole screen width; the site's own
            // template takes a size, so the expanded state reads a larger copy of it.
            expandedAvatarUser = DiscourseAvatars.INSTANCE.user(
                    loaded.getUsername(),
                    loaded.getDisplayName(),
                    loaded.getAvatarTemplate(),
                    container.getBaseUrl(),
                    DiscourseAvatars.EXPANDED_SIZE);
            buildRows(loaded);
            buildActions(loaded);
            buildMedia();
            notifyRowsChanged();
            bindAvatar();
            fetchAvatar();
            loadSummary();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("profile failed", error);
            // The profile never arrived: the shimmer ends with the read rather than turning over
            // an empty screen, and the list stays as empty as it was.
            if (listView != null) {
                listView.setEmptyView(null);
            }
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The stats the profile endpoint does not carry. The summary is drawn as soon as it arrives
     * (the list re-binds); a site that hides it, or an outage, leaves the fields the profile did
     * send standing.
     */
    private void loadSummary() {
        ProfileLoader.INSTANCE.loadSummary(container, username(), loaded -> {
            summary = loaded;
            if (profile != null) {
                buildRows(profile);
                notifyRowsChanged();
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("profile summary failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The header's picture, once the site's bytes are on disk, the way the head cell used to read it. */
    private void bindAvatar() {
        if (avatarImage == null || avatarUser == null) {
            return;
        }
        ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
        if (avatar != null) {
            avatarImage.setImage(avatar, "50_50", avatarImage.getImageReceiver().getDrawable(), avatarUser);
            // The pull-down is only offered once the head has a real picture to grow.
            allowPullingDown = canPullDown();
        }
    }

    /**
     * The avatar travels from the site's own template the way the topic list's rows fetch theirs,
     * and the expanded header's larger copy travels with it - the site's template takes a size, so
     * the square the pull-down opens draws the same picture at full resolution.
     */
    private void fetchAvatar() {
        String url = DiscourseAvatars.INSTANCE.url(
                profile == null ? null : profile.getAvatarTemplate(),
                container.getBaseUrl(),
                DiscourseAvatars.DEFAULT_SIZE);
        if (url != null && avatarUser != null && !DiscourseAvatars.INSTANCE.isCached(avatarUser)) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, avatarUser, () -> {
                // The head and every action row draw the same picture, so all of them are re-bound.
                bindAvatar();
                notifyRowsChanged();
                return kotlin.Unit.INSTANCE;
            });
        }
        String expandedUrl = DiscourseAvatars.INSTANCE.url(
                profile == null ? null : profile.getAvatarTemplate(),
                container.getBaseUrl(),
                DiscourseAvatars.EXPANDED_SIZE);
        if (expandedUrl != null && expandedAvatarUser != null
                && !DiscourseAvatars.INSTANCE.isCached(expandedAvatarUser)) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, expandedUrl, expandedAvatarUser, () -> {
                bindExpandedAvatar();
                return kotlin.Unit.INSTANCE;
            });
        } else {
            bindExpandedAvatar();
        }
    }

    /**
     * The large copy, drawn as the avatar's own foreground layer while the header is expanded (the
     * way upstream's AvatarImageView foreground carries the gallery's picture across the animation);
     * its alpha is the expansion's fraction, so the small picture hands over as the square opens.
     */
    private void bindExpandedAvatar() {
        if (avatarImage == null || expandedAvatarUser == null) {
            return;
        }
        ImageLocation big = DiscourseAvatars.INSTANCE.imageLocation(expandedAvatarUser);
        if (big != null) {
            avatarImage.setForegroundImage(big, null, avatarImage.getImageReceiver().getDrawable());
            expandedAvatarBound = true;
        }
    }

    /** Upstream's own gate on the pull-down (ProfileActivity.java:12869): a real photo, portrait, no reader. */
    private boolean canPullDown() {
        return !AndroidUtilities.isTablet()
                && AndroidUtilities.displaySize.x <= AndroidUtilities.displaySize.y
                && avatarImage != null
                && avatarImage.getImageReceiver().hasNotThumb()
                && !AndroidUtilities.isAccessibilityScreenReaderEnabled();
    }

    /**
     * The screen's rows, in the order it shows them: the bio upstream's info block leads with
     * (ProfileActivity's AboutLinkCell slot), then the reader's fields, then their stats. Every
     * field the site sent and left non-empty becomes one row; a field it did not send is not a row
     * of dashes.
     */
    private void buildRows(UserProfile loaded) {
        rows.clear();
        // The site sends the bio as cooked HTML; the head and the bio row both draw text, so the
        // tags come off once, here.
        bioText = loaded.getBio() == null ? "" : org.jsoup.Jsoup.parse(loaded.getBio()).text().trim();
        if (!bioText.isEmpty()) {
            rows.add(Row.section("关于我"));
            rows.add(Row.bio(bioText));
        }
        rows.add(Row.section("资料"));
        addIfPresent("用户名", loaded.getUsername());
        rows.add(Row.detail("信任等级", String.valueOf(loaded.getTrustLevel())));
        addIfPresent("头衔", loaded.getTitle());
        addIfPresent("用户组", loaded.getPrimaryGroupName());
        addIfPresent("所在地", loaded.getLocation());
        addIfPresent("个人网站", loaded.getWebsiteName().isEmpty() ? loaded.getWebsite() : loaded.getWebsiteName());
        addIfPresent("加入时间", date(loaded.getCreatedAt()));
        addIfPresent("最近活跃", date(loaded.getLastSeenAt()));
        addIfPresent("最后发帖", date(loaded.getLastPostedAt()));
        rows.add(Row.section("统计"));
        // The summary's own numbers first: they are the ones the site's profile page shows, and a
        // summary the reader may not see leaves them out rather than printing zeroes.
        if (summary != null) {
            rows.add(Row.detail("话题", String.valueOf(summary.getTopicCount())));
            rows.add(Row.detail("帖子", String.valueOf(summary.getPostCount())));
            rows.add(Row.detail("获赞", String.valueOf(summary.getLikesReceived())));
            rows.add(Row.detail("送出赞", String.valueOf(summary.getLikesGiven())));
            rows.add(Row.detail("访问天数", String.valueOf(summary.getDaysVisited())));
            rows.add(Row.detail("阅读帖子", String.valueOf(summary.getPostsReadCount())));
        }
        addIfPresent("阅读时长", readTime(loaded.getTimeRead()));
        rows.add(Row.detail("浏览", String.valueOf(loaded.getProfileViewCount())));
        rows.add(Row.detail("徽章", String.valueOf(loaded.getBadgeCount())));
        rows.add(Row.detail("得分", String.valueOf(loaded.getGamificationScore())));
        rows.add(Row.detail("关注者", String.valueOf(loaded.getTotalFollowers())));
        rows.add(Row.detail("关注中", String.valueOf(loaded.getTotalFollowing())));
        rows.add(Row.detail("被采纳回复", String.valueOf(loaded.getAcceptedAnswers())));
        // Upstream separates the rows of a section with a divider, and the last row of each section
        // ends the list's section rather than continuing it.
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.type != TYPE_DETAIL) {
                continue;
            }
            Row next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            row.divider = next != null && next.type == TYPE_DETAIL;
        }
        // The tab area is the list's last row; its own row placeholder only exists once its view is
        // built (buildMedia), so the index is appended there.
    }

    private boolean isOwnProfile() {
        if (container == null || container.getSessionStore().snapshot() == null || profile == null) {
            return false;
        }
        String reader = container.getSessionStore().snapshot().getUsername();
        String shown = profile.getUsername();
        return reader != null && shown != null && reader.equalsIgnoreCase(shown);
    }

    private void addIfPresent(String label, String value) {
        if (value != null && !value.isEmpty()) {
            rows.add(Row.detail(label, value));
        }
    }

    /**
     * The row under the header, from the site's own answer: an action the site did not offer - a
     * private message it will not deliver, a follow it will not take - is not a button, and the
     * share action is always there because the profile link always is.
     */
    private void buildActions(UserProfile loaded) {
        if (actionsView == null) {
            return;
        }
        actionsView.mode = ownProfile ? ProfileActionsView.MODE_MY_PROFILE : ProfileActionsView.MODE_USER;
        actionsView.beginApplyingActions();
        if (ownProfile) {
            actionsView.addBookmarks();
            actionsView.addSettings();
            actionsView.addShare();
        } else {
            actionsView.set(ProfileActionsView.KEY_MESSAGE, loaded.getCanSendPrivateMessage());
            actionsView.set(ProfileActionsView.KEY_FOLLOW, loaded.getCanFollow() && !isFollowed);
            actionsView.set(ProfileActionsView.KEY_UNFOLLOW, loaded.getCanFollow() && isFollowed);
            // The mute action is only offered when the site says the reader may mute this user;
            // setNotifications also gives it its Mute/Unmute label from the current state.
            actionsView.set(ProfileActionsView.KEY_NOTIFICATION, loaded.getCanMuteUser());
            if (loaded.getCanMuteUser()) {
                actionsView.setNotifications(!isMuted);
            }
            actionsView.set(ProfileActionsView.KEY_SHARE, true);
        }
        actionsView.commitActions();
        actionsView.setVisibility(View.VISIBLE);
    }

    /**
     * What a button does. The writes come back through [ProfileLoader], and a successful one flips
     * the site's own state before the row is rebuilt - upstream re-applies its actions after a
     * write the same way (ProfileActivity.java:6911-6916). Package-visible so a screen test can
     * press the button the view draws.
     */
    void onActionClicked(int key, float x, float y) {
        switch (key) {
            case ProfileActionsView.KEY_MESSAGE:
                openNewMessage();
                break;
            case ProfileActionsView.KEY_FOLLOW:
                setFollow(true);
                break;
            case ProfileActionsView.KEY_UNFOLLOW:
                setFollow(false);
                break;
            case ProfileActionsView.KEY_NOTIFICATION:
                setMuted(!isMuted);
                break;
            case ProfileActionsView.KEY_SHARE:
                shareProfile();
                break;
            case ProfileActionsView.KEY_BOOKMARKS:
                presentFragment(new io.github.baba983.nativedo.bookmarks.BookmarksFragment());
                break;
            case ProfileActionsView.KEY_SETTINGS:
                presentFragment(new io.github.baba983.nativedo.settings.SettingsFragment());
                break;
            default:
                break;
        }
    }

    private void setFollow(boolean follow) {
        if (container == null || profile == null) {
            return;
        }
        ProfileLoader.INSTANCE.setFollow(container, username(), follow, () -> {
            isFollowed = follow;
            if (actionsView != null && profile != null) {
                actionsView.set(ProfileActionsView.KEY_FOLLOW, profile.getCanFollow() && !isFollowed);
                actionsView.set(ProfileActionsView.KEY_UNFOLLOW, profile.getCanFollow() && isFollowed);
            }
            showBulletin(follow ? "已关注" : "已取消关注", true);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("follow failed", error);
            showBulletin("操作失败，请重试", false);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void setMuted(boolean muted) {
        if (container == null || profile == null) {
            return;
        }
        ProfileLoader.INSTANCE.setMuted(container, username(), muted, () -> {
            isMuted = muted;
            if (actionsView != null) {
                actionsView.setNotifications(!isMuted);
            }
            showBulletin(muted ? "已静音" : "已取消静音", true);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("mute failed", error);
            showBulletin("操作失败，请重试", false);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * 私信 opens the site's own composer in the in-app browser: Discourse writes a private message
     * at `/new-message?username={u}`, and the browser shares this session's cookies, so the page
     * arrives signed in. The action is only offered while the profile says the reader may send one.
     */
    private void openNewMessage() {
        if (getParentActivity() == null || container == null) {
            return;
        }
        org.telegram.messenger.browser.Browser.openUrl(
                getParentActivity(),
                container.getBaseUrl().replaceAll("/+$", "") + "/new-message?username=" + username());
    }

    /** 分享 hands the profile's site URL to the system share sheet, the clipboard as fallback. */
    private void shareProfile() {
        if (getParentActivity() == null) {
            return;
        }
        String url = profileUrl();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, url);
        try {
            getParentActivity().startActivity(
                    android.content.Intent.createChooser(intent, null));
        } catch (Exception e) {
            FileLog.e(e);
            org.telegram.messenger.AndroidUtilities.addToClipboard(url);
            showBulletin("链接已复制", true);
        }
    }

    private String profileUrl() {
        String base = container == null ? "" : container.getBaseUrl().replaceAll("/+$", "");
        return base + "/u/" + username();
    }

    private void showBulletin(String text, boolean success) {
        try {
            org.telegram.ui.Components.BulletinFactory factory =
                    org.telegram.ui.Components.BulletinFactory.of(this);
            if (success) {
                factory.createSuccessBulletin(text).show();
            } else {
                factory.createErrorBulletin(text).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }


    // ---------------------------------------------------------------------------------------------
    // The tab area, ported from SharedMediaLayout's mechanism: a strip, a pager, and one
    // Discourse page per site list. The pages are RecyclerListViews of our own; nothing here
    // touches Telegram's own media loading.
    // ---------------------------------------------------------------------------------------------

    /** The tabs in the order the strip shows them; 书签 is the reader's own list only. */
    private static final int[] OWN_TABS = {TAB_TOPICS, TAB_REPLIES, TAB_LIKES, TAB_BOOKMARKS, TAB_BADGES};
    private static final int[] OTHER_TABS = {TAB_TOPICS, TAB_REPLIES, TAB_LIKES, TAB_BADGES};

    /** The site pages `/u/{u}/bookmarks.json` twenty at a time. */
    private static final int BOOKMARK_PAGE_SIZE = 20;

    private int pageCount() {
        return (ownProfile ? OWN_TABS : OTHER_TABS).length;
    }

    private int tabAt(int index) {
        int[] tabs = ownProfile ? OWN_TABS : OTHER_TABS;
        return index >= 0 && index < tabs.length ? tabs[index] : TAB_TOPICS;
    }

    private CharSequence tabTitle(int tab) {
        switch (tab) {
            case TAB_REPLIES:
                return "回复";
            case TAB_LIKES:
                return "点赞";
            case TAB_BOOKMARKS:
                return "书签";
            case TAB_BADGES:
                return "徽章";
            default:
                return "话题";
        }
    }

    private String emptyText(int tab) {
        switch (tab) {
            case TAB_REPLIES:
                return "还没有回复";
            case TAB_LIKES:
                return "还没有点赞";
            case TAB_BOOKMARKS:
                return "还没有书签";
            case TAB_BADGES:
                return "还没有徽章";
            default:
                return "还没有话题";
        }
    }

    /**
     * Builds the tab area and appends it to the info list. Called once the profile - and with it
     * whether this is the reader's own screen - is known.
     */
    private void buildMedia() {
        if (mediaView != null || frameLayout == null || listView == null) {
            return;
        }
        mediaView = new ProfileSharedMediaView(frameLayout.getContext(), resourceProvider,
                new ProfileSharedMediaView.Delegate() {
                    @Override
                    public int getTabCount() {
                        return pageCount();
                    }

                    @Override
                    public CharSequence getTabTitle(int index) {
                        return tabTitle(tabAt(index));
                    }

                    @Override
                    public View onCreatePageView(int index) {
                        return pageState(tabAt(index)).root;
                    }

                    @Override
                    public void onPageSelected(int index) {
                        pageState(tabAt(index)).loadFirstIfNeeded();
                    }

                    @Override
                    public void onPageReselected(int index) {
                        TabPageState state = tabPages[tabAt(index)];
                        if (state != null) {
                            state.listView.smoothScrollToPosition(0);
                        }
                    }
                });
        mediaView.buildTabs();
        mediaRow = rows.size();
        rows.add(Row.media());
        pageState(TAB_TOPICS).loadFirstIfNeeded();
    }

    private TabPageState pageState(int tab) {
        if (tabPages[tab] == null) {
            tabPages[tab] = new TabPageState(tab);
        }
        return tabPages[tab];
    }

    /** The strip's current page list, which is where a pinned scroll goes (see NestedFrameLayout). */
    private RecyclerListView currentPageList() {
        if (mediaView == null) {
            return null;
        }
        int tab = mediaView.getCurrentTab();
        return tab >= 0 && tab < tabPages.length && tabPages[tab] != null
                ? tabPages[tab].listView
                : null;
    }

    private ProfileTabFeed feedFor(int tab) {
        switch (tab) {
            case TAB_REPLIES:
                if (repliesFeed == null) {
                    repliesFeed = new ProfileTabFeed(
                            container.getProfileFeedRepository(), username(), ProfileFeedRepository.FILTER_REPLIES);
                }
                return repliesFeed;
            case TAB_LIKES:
                if (likesFeed == null) {
                    likesFeed = new ProfileTabFeed(
                            container.getProfileFeedRepository(), username(), ProfileExtrasRepository.FILTER_LIKES);
                }
                return likesFeed;
            default:
                if (topicsFeed == null) {
                    topicsFeed = new ProfileTabFeed(
                            container.getProfileFeedRepository(), username(), ProfileFeedRepository.FILTER_TOPICS);
                }
                return topicsFeed;
        }
    }

    /**
     * One page: its own list, its own shimmer while the first page is in flight, and the site's
     * empty answer when there is nothing. The list keeps the strip's height as its top padding,
     * the way SharedMediaLayout pads its page lists (SharedMediaLayout.java:3100).
     */
    private class TabPageState {

        final int tab;
        final FrameLayout root;
        final RecyclerListView listView;
        final FlickerLoadingView flicker;
        final EmptyTextProgressView empty;
        final MediaRowsAdapter adapter;
        final List<Object> items = new ArrayList<>();

        int offset;
        boolean loading;
        boolean finished;
        boolean firstLoaded;
        final java.util.Set<String> avatarRequests = new java.util.HashSet<>();

        TabPageState(int tab) {
            this.tab = tab;
            Context context = frameLayout.getContext();
            root = new FrameLayout(context);

            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context));
            adapter = new MediaRowsAdapter(this);
            listView.setAdapter(adapter);
            listView.setVerticalScrollBarEnabled(false);
            listView.setClipToPadding(false);
            listView.setPadding(0, AndroidUtilities.dp(ProfileSharedMediaView.PAGE_TOP_PADDING_DP), 0, listBottomPadding());
            listView.setOnItemClickListener((view, position) -> {
                Object item = position >= 0 && position < items.size() ? items.get(position) : null;
                if (item instanceof TopicSummary) {
                    presentAction((TopicSummary) item);
                } else if (item instanceof io.github.baba983.nativedo.core.model.Bookmark) {
                    openBookmark((io.github.baba983.nativedo.core.model.Bookmark) item);
                }
            });
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    loadMoreIfNeeded();
                }
            });

            flicker = new FlickerLoadingView(context);
            flicker.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
            flicker.setPaddingTop(AndroidUtilities.dp(ProfileSharedMediaView.PAGE_TOP_PADDING_DP));
            flicker.setVisibility(View.GONE);

            empty = new EmptyTextProgressView(context);
            empty.setText(emptyText(tab));
            empty.showTextView();
            empty.setVisibility(View.GONE);
            // The view consumes touches in its own constructor; a page with nothing to show has
            // nothing to tap, but the swipe between pages must stay possible.
            empty.setOnTouchListener(null);
            empty.setClickable(false);

            root.addView(listView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            root.addView(flicker, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            root.addView(empty, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void loadFirstIfNeeded() {
            if (firstLoaded) {
                return;
            }
            firstLoaded = true;
            loadPage();
        }

        void loadMoreIfNeeded() {
            if (loading || finished || items.isEmpty()) {
                return;
            }
            LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
            if (manager == null
                    || manager.findLastVisibleItemPosition()
                    < adapter.getItemCount() - 1 - LOAD_MORE_THRESHOLD) {
                return;
            }
            loadPage();
        }

        void loadPage() {
            if (loading || finished || container == null) {
                return;
            }
            loading = true;
            updateLoadingState();
            switch (tab) {
                case TAB_BOOKMARKS: {
                    final int page = offset;
                    ProfileLoader.INSTANCE.loadBookmarks(container, username(), page, loaded -> {
                        loading = false;
                        offset = page + 1;
                        finished = loaded.size() < BOOKMARK_PAGE_SIZE;
                        items.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    }, error -> {
                        loading = false;
                        finished = true;
                        FileLog.e("profile bookmarks failed", error);
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    });
                    break;
                }
                case TAB_BADGES: {
                    ProfileLoader.INSTANCE.loadBadges(container, username(), loaded -> {
                        loading = false;
                        finished = true;
                        items.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    }, error -> {
                        loading = false;
                        finished = true;
                        FileLog.e("profile badges failed", error);
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    });
                    break;
                }
                default: {
                    final ProfileTabFeed feed = feedFor(tab);
                    feed.load(() -> {
                        loading = false;
                        finished = !feed.hasMore();
                        items.clear();
                        items.addAll(feed.rows());
                        adapter.notifyDataSetChanged();
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    });
                    break;
                }
            }
        }

        void updateLoadingState() {
            boolean hasRows = !items.isEmpty();
            flicker.setVisibility(loading && !hasRows ? View.VISIBLE : View.GONE);
            empty.setVisibility(!loading && !hasRows && firstLoaded ? View.VISIBLE : View.GONE);
        }
    }

    /** A page's rows: the site's topics, the reader's bookmarks and their badges. */
    private class MediaRowsAdapter extends RecyclerListView.SelectionAdapter {

        private final TabPageState state;

        MediaRowsAdapter(TabPageState state) {
            this.state = state;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == TYPE_DETAIL) {
                TextDetailCell cell = new TextDetailCell(context, resourceProvider, true, true);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            // A row of the activity pages is a topic, so it is the row the topic list draws.
            DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = position < state.items.size() ? state.items.get(position) : null;
            if (holder.itemView instanceof TextDetailCell) {
                if (item instanceof io.github.baba983.nativedo.core.model.UserBadge) {
                    io.github.baba983.nativedo.core.model.UserBadge badge =
                            (io.github.baba983.nativedo.core.model.UserBadge) item;
                    boolean divider = position + 1 < state.items.size()
                            && state.items.get(position + 1) instanceof io.github.baba983.nativedo.core.model.UserBadge;
                    ((TextDetailCell) holder.itemView).setTextAndValue(
                            badge.getName(), date(badge.getGrantedAt()), divider);
                }
                return;
            }
            DialogCell cell = (DialogCell) holder.itemView;
            if (item instanceof io.github.baba983.nativedo.core.model.Bookmark) {
                bindBookmark(cell, (io.github.baba983.nativedo.core.model.Bookmark) item);
            } else if (item instanceof TopicSummary) {
                TopicSummary action = (TopicSummary) item;
                cell.setDialog(DiscourseRows.topicRow(
                        action, TopicReadMarks.INSTANCE.isReadInFull(action.getId())));
                ImageLocation avatar = avatarUser == null
                        ? null
                        : DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
                if (avatar != null) {
                    cell.avatarImage.setImage(
                            avatar, "50_50", cell.avatarImage.getDrawable(), null, avatarUser, 0);
                }
            }
        }

        /** A bookmark draws the post's author, fetched the way BookmarksFragment fetches it. */
        private void bindBookmark(DialogCell cell, io.github.baba983.nativedo.core.model.Bookmark bookmark) {
            cell.setDialog(DiscourseRows.bookmarkRow(bookmark));
            if (bookmark.getAuthorUsername().isEmpty() || container == null) {
                return;
            }
            TLRPC.User author = DiscourseAvatars.INSTANCE.user(
                    bookmark.getAuthorUsername(),
                    bookmark.getAuthorDisplayName(),
                    bookmark.getAuthorAvatarTemplate(),
                    container.getBaseUrl());
            ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(author);
            if (avatar != null) {
                cell.avatarImage.setImage(avatar, "50_50", cell.avatarImage.getDrawable(), null, author, 0);
            }
            String url = DiscourseAvatars.INSTANCE.url(
                    bookmark.getAuthorAvatarTemplate(), container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url != null && !DiscourseAvatars.INSTANCE.isCached(author)
                    && state.avatarRequests.add(bookmark.getAuthorUsername())) {
                DiscourseLoader.INSTANCE.cacheAvatar(container, url, author, () -> {
                    if (state.listView.getAdapter() != null) {
                        state.adapter.notifyDataSetChanged();
                    }
                    return kotlin.Unit.INSTANCE;
                });
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position < state.items.size()
                    && state.items.get(position) instanceof io.github.baba983.nativedo.core.model.UserBadge
                    ? TYPE_DETAIL
                    : TYPE_ACTION;
        }

        @Override
        public int getItemCount() {
            return state.items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return !(holder.itemView instanceof TextDetailCell);
        }
    }

    /** A bookmark opens the topic it is in, at the floor the post is on. */
    private void openBookmark(io.github.baba983.nativedo.core.model.Bookmark bookmark) {
        if (bookmark.getPostNumber() <= 0) {
            presentFragment(TopicFragment.forTopic(bookmark.getTopicId(), bookmark.getTitle()));
            return;
        }
        presentFragment(TopicFragment.forFloor(
                bookmark.getTopicId(), bookmark.getTitle(), bookmark.getPostNumber()));
    }

    private void notifyRowsChanged() {
        settleHeaderInList();
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }


    /**
     * The list carries the header's height as its own top padding, and the first row is seeded
     * under the header the way upstream seeds it (ProfileActivity.java:3230-3245): the padding is
     * the pull-down's room, so what puts the rows at the header's rest position is the scroll
     * offset, not the padding. Seeded once the list has a width and rows to place.
     */
    private void settleHeaderInList() {
        if (isPulledDown || listView == null || listView.getMeasuredWidth() <= 0) {
            return;
        }
        final int paddingTop = listView.getMeasuredWidth() + getActionsExtraHeight();
        if (listView.getPaddingTop() != paddingTop) {
            listView.setPadding(0, paddingTop, 0, listBottomPadding());
        }
        // The tab area is one row as tall as the list's own viewport: that is what lets it pin
        // itself to the list's top and scroll its pages inside. Upstream keeps the same relation
        // (ProfileActivity.java:8006-8008 hands sharedMediaLayout the list's visible height).
        if (mediaView != null) {
            int mediaHeight = Math.max(0, listView.getMeasuredHeight() - listBottomPadding());
            ViewGroup.LayoutParams params = mediaView.getLayoutParams();
            if (params != null && params.height != mediaHeight) {
                params.height = mediaHeight;
                mediaView.setLayoutParams(params);
            }
        }
        // The shimmer draws its rows from below the header too, the way DialogsActivity keeps its
        // progress view under its own top panel (2093-2095).
        if (progressView != null) {
            progressView.setPaddingTop(paddingTop);
        }
        if ((!headerSeeded || seededWidth != listView.getMeasuredWidth())
                && listView.getAdapter() != null && listView.getAdapter().getItemCount() > 0) {
            headerSeeded = true;
            seededWidth = listView.getMeasuredWidth();
            layoutManager.scrollToPositionWithOffset(0, getHeaderExtraHeight() - paddingTop);
        }
    }

    /** The site writes ISO timestamps; a reader reads them as a date. */
    private static String date(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(timestamp));
        } catch (Exception e) {
            return timestamp;
        }
    }

    /** The site counts seconds read; a reader reads them as hours once there are any. */
    private static String readTime(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        return (seconds / 3600) + " 小时";
    }

    private class ProfileAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            switch (viewType) {
                case TYPE_SECTION: {
                    HeaderCell cell = new HeaderCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_BIO: {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_MEDIA: {
                    // The tab area is a row of the info list the way SharedMediaLayout is a row of
                    // ProfileActivity's own list (ProfileActivity.java:10824, 14360): its height is
                    // set to the list's visible height in settleHeaderInList, so it can pin itself
                    // to the list's top and its pages then scroll inside it.
                    mediaView.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(mediaView);
                }
                default: {
                    TextDetailCell cell = new TextDetailCell(context, resourceProvider, true, true);
                    // The same one line `ProfileActivity` sets on every cell of its info block: a
                    // reader who hears the row hears the answer first.
                    cell.setContentDescriptionValueFirst(true);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rowAt(position);
            if (holder.itemView instanceof HeaderCell) {
                ((HeaderCell) holder.itemView).setText(row.label);
            } else if (holder.itemView instanceof TextInfoPrivacyCell) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.value);
            } else if (holder.itemView instanceof TextDetailCell) {
                // Upstream's own convention, as `ProfileActivity`'s info block uses it: the cell's
                // first argument is the answer and lands in its 16 dp line, the second is the field's
                // name and lands in its 13 dp line (the phone number above the word "Mobile").
                TextDetailCell cell = (TextDetailCell) holder.itemView;
                cell.setTextAndValue(row.value, row.label, row.divider);
            }
        }

        private Row rowAt(int position) {
            return rows.get(position);
        }

        @Override
        public int getItemViewType(int position) {
            return rowAt(position).type;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // The info rows are text to read; the tappable things are the action row's buttons and
            // the pages' own rows, each with their own click handling.
            return false;
        }
    }

    /**
     * The action row's own height, upstream's `dp(74)` (ProfileActivity.java:6033-6039): it is the
     * room the list leaves between the header and the first info row, and what the pull-down
     * expansion's thresholds are measured against.
     */
    private int getActionsExtraHeight() {
        return AndroidUtilities.dp(74);
    }

    private int getHeaderOnlyExtraHeight() {
        if (getActionsExtraHeight() == 0) {
            return dp(168f);
        }
        return dp(152f);
    }

    private int getHeaderExtraHeight() {
        return getHeaderOnlyExtraHeight() + getActionsExtraHeight();
    }

    /** The avatar's own round radius: a linux.do reader is not a topic, so it is the round one. */
    private int getSmallAvatarRoundRadius() {
        return dp(50);
    }

    /** calculateHeaderExtraDiff (ProfileActivity.java:6411) */
    private float calculateHeaderExtraDiff() {
        return Utilities.clamp01((extraHeight - getActionsExtraHeight()) / (getHeaderOnlyExtraHeight()));
    }

    /** checkListViewScroll (ProfileActivity.java:8002) */
    private void checkListViewScroll() {
        if (listView.getVisibility() != View.VISIBLE) {
            return;
        }
        if (listView.getChildCount() <= 0) {
            return;
        }

        int newOffset = 0;
        View child = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAdapterPosition(listView.getChildAt(i)) == 0) {
                child = listView.getChildAt(i);
                break;
            }
        }
        RecyclerListView.Holder holder = child == null ? null : (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child == null ? 0 : child.getTop();
        int adapterPosition = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
        if (top >= 0 && adapterPosition == 0) {
            newOffset = top;
        }

        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            needLayout(true);
        }
    }

    /** needLayout (ProfileActivity.java:8368); the open-animation branch and the write button are left out. */
    private void needLayout(boolean animated) {
        final int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        final float diff = calculateHeaderExtraDiff();
        boolean textMeasured = false;

        FrameLayout.LayoutParams layoutParams;
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
            }
        }

        if (avatarContainer != null) {
            listView.setTopGlowOffset((int) extraHeight);
            listView.setOverScrollMode(extraHeight > getHeaderExtraHeight() && extraHeight < listView.getMeasuredWidth() + getActionsExtraHeight() - newTop ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_ALWAYS);
        }

        final int headerExtraHeight = getHeaderExtraHeight();

        if (avatarContainer != null) {
            float endNameY =
                (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight() / 2f * (1f + diff)
                - dp(21)
                + actionBar.getTranslationY();

            float endY = -dp(29);
            if (avatarGooey != null && avatarGooey.notchInfo != null && avatarGooey.notchInfo.isLikelyCircle) {
                endY = avatarGooey.notchInfo.bounds.centerY();
            }
            float startY =
                (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                    + ActionBar.getCurrentActionBarHeight()
                    - dp(21)
                    + actionBar.getTranslationY();
            avatarY = lerp(endY, startY, diff);

            float h = extraHeight;
            if (h > headerExtraHeight || isPulledDown) {
                // Upstream's pull-down expansion (ProfileActivity.java:8427-8537): the fraction of
                // the stretch that is open is the expansion's progress, and the avatar's own scale
                // grows to 138% of its 100 dp frame as the finger carries it the first third.
                expandProgress = Math.max(0f, Math.min(1f, (h - headerExtraHeight) / (listView.getMeasuredWidth() - newTop - getHeaderOnlyExtraHeight())));
                avatarScale = lerp(96 / 42f, 138 / 42f, Math.min(1f, expandProgress * 3f)) / 100f * 42f;
                pullUpProgress = 0.0f;

                final float durationFactor = Math.min(dpf2(2000f), Math.max(dpf2(1100f), Math.abs(listViewVelocityY))) / dpf2(1100f);

                if (allowPullingDown && expandProgress >= 0.33f) {
                    if (!isPulledDown) {
                        startAvatarExpand(durationFactor);
                    }
                    if (!expandAnimator.isRunning()) {
                        nameTextView.setTranslationX(dpf2(18f) - nameTextView.getLeft());
                        nameTextView.setTranslationY(newTop + h - getActionsExtraHeight() - dpf2(30f) - nameTextView.getBottom());
                        onlineTextView.setTranslationX(dpf2(16f) - onlineTextView.getLeft());
                        onlineTextView.setTranslationY(newTop + h - getActionsExtraHeight() - dpf2(10f) - onlineTextView.getBottom());
                    }
                } else {
                    if (isPulledDown) {
                        startAvatarCollapse(durationFactor);
                    }
                    avatarContainer.setScaleX(avatarScale);
                    avatarContainer.setScaleY(avatarScale);
                    fixAvatarImageInCenter();
                    if (expandAnimator == null || !expandAnimator.isRunning()) {
                        refreshNameAndOnlineY();
                        nameTextView.setTranslationY(nameY);
                        onlineTextView.setTranslationX(onlineX);
                        onlineTextView.setTranslationY(onlineY);
                    }
                }
            } else {
                float intoSize = 24f;
                if (avatarGooey != null && avatarGooey.notchInfo != null && avatarGooey.notchInfo.isLikelyCircle) {
                    intoSize = 0.5f * avatarGooey.notchInfo.bounds.width() / AndroidUtilities.density;
                }
                avatarScale = lerp(intoSize, 96, diff) / 100f;
                pullUpProgress = 1.0f - diff;

                final float nameScale = 1.0f + 0.12f * diff;
                if (!(openAnimationInProgress && playProfileAnimation == 2)) {
                    avatarContainer.setScaleX(avatarScale);
                    avatarContainer.setScaleY(avatarScale);
                    fixAvatarImageInCenter();
                    avatarContainer.setTranslationY((float) Math.ceil(avatarY));
                }
                final float avatarBottom = (float) Math.floor(endNameY) + (avatarContainer.getHeight() * avatarContainer.getScaleY() + dpf2(8)) * diff;
                nameY = avatarBottom + dp(1.3f) + dp(7) * diff;
                onlineY = avatarBottom + dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff;
                final float minimizedX = -dpf2(42 + 4);

                int viewportWidth = listView.getMeasuredWidth();
                // Upstream runs this over two copies of the text; the one that collapses with the
                // header is the second one, which is the only one this screen builds.
                nameTextView.setScaleX(nameScale);
                nameTextView.setScaleY(nameScale);
                textMeasured = true;
                needLayoutText(diff, 0, false);

                FrameLayout.LayoutParams params1 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
                float nameX = viewportWidth / 2f - (params1.leftMargin + Math.min(nameTextView.getExactWidth(), params1.width) * nameScale * 0.5f);
                FrameLayout.LayoutParams params2 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
                float onlineX = viewportWidth / 2f - (params2.leftMargin + Math.min(onlineTextView.getExactWidth(), params2.width) * 0.5f);

                this.nameX = nameX;
                this.onlineX = onlineX;
                nameX = AndroidUtilities.lerp(minimizedX, nameX, diff);
                onlineX = AndroidUtilities.lerp(minimizedX, onlineX, diff);

                nameTextView.setTranslationX(nameX);
                nameTextView.setTranslationY(nameY);
                onlineTextView.setTranslationX(onlineX);
                onlineTextView.setTranslationY(onlineY);
                updateTextLayoutBasedOnTranslation();
            }
            if (openAnimationInProgress && playProfileAnimation == 2) {
                openAnimationAvatarLayout(newTop);
            }
            if (!textMeasured) {
                needLayoutText(diff);
            }
        }

        updateExtraViews(newTop);
        if (diff >= 1f) {
            avatarImage.setAlpha(1f);
        }
    }

    /** needLayoutText (ProfileActivity.java:8935); the media-counter and its copies are left out. */
    private void needLayoutText(float diff, int startWidth, boolean invalidate) {
        FrameLayout.LayoutParams layoutParams;
        float minScale, maxScale;
        if (extraHeight > getHeaderExtraHeight()) {
            minScale = maxScale = nameTextView.getScaleX();
        } else if (diff >= 1f) {
            minScale = maxScale = Math.max(nameTextView.getScaleX(), 1.12f);
        } else {
            minScale = 1f;
            maxScale = 1.12f;
        }

        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        // Upstream adds 48 dp for each action-bar item it may show (search, edit, call, video);
        // this screen's bar carries the back button only, so those counts stay zero.
        int extra = 0;
        int buttonsWidth = AndroidUtilities.dp(47 + 8 + (40 + extra));
        int minOnlineWidth = viewWidth - buttonsWidth;
        int minWidth = (int) (minOnlineWidth / (startWidth != 0 ? 1f : minScale));
        int maxOnlineWidth = startWidth != 0 ? startWidth : viewWidth - AndroidUtilities.dp(18) * 2;
        int maxWidth = startWidth != 0 ? startWidth : (int) (maxOnlineWidth / maxScale);

        int width = AndroidUtilities.lerp(minWidth, maxWidth, CubicBezierInterpolator.EASE_IN.getInterpolation(diff));
        layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        int prevWidth = layoutParams.width;
        layoutParams.width = (int) Math.ceil(width);

        if (invalidate) {
            float x = layoutParams.leftMargin + nameTextView.getTranslationX();
            layoutParams.width = Math.min(layoutParams.width, (int) ((viewWidth - AndroidUtilities.dp(18f)) / nameTextView.getScaleX() - x));
        }
        if (invalidate && layoutParams.width != prevWidth) {
            nameTextView.requestLayout();
        }

        width = AndroidUtilities.lerp(minOnlineWidth, maxOnlineWidth, CubicBezierInterpolator.EASE_IN.getInterpolation(diff));
        layoutParams = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
        prevWidth = layoutParams.width;
        layoutParams.width = (int) Math.ceil(width);
        if (prevWidth != layoutParams.width) {
            onlineTextView.requestLayout();
        }
    }

    private void needLayoutText(float diff) {
        needLayoutText(diff, 0, true);
    }

    /** updateTextLayoutBasedOnTranslation (ProfileActivity.java:8925) */
    private void updateTextLayoutBasedOnTranslation() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        int old = params.width;
        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        float x = params.leftMargin + nameTextView.getTranslationX();
        params.width = Math.min(params.width, (int) ((viewWidth - AndroidUtilities.dp(18f)) / nameTextView.getScaleX() - x));
        if (params.width != old) {
            nameTextView.requestLayout();
        }
    }

    /** fixAvatarImageInCenter (ProfileActivity.java:6420) */
    private void fixAvatarImageInCenter() {
        if (listView == null) return;
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        avatarX = listView.getMeasuredWidth() / 2f - (params.width * avatarScale * 0.5f);
        avatarContainer.setTranslationX(avatarX);
    }

    /**
     * The expand half of upstream's threshold release (ProfileActivity.java:8443-8537): the header
     * commits, the list scrolls itself the rest of the way to the square, and the animator carries
     * the avatar and the titles from wherever the finger left them. The gallery, its menu and its
     * haptics are Telegram's; the haptic on commit is kept.
     */
    private void startAvatarExpand(float durationFactor) {
        isPulledDown = true;
        expandAnimator.cancel();
        float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture);
        expandAnimatorValues[0] = value;
        expandAnimatorValues[1] = 1f;
        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        expandAnimator.setDuration((long) ((1f - value) * 250f / durationFactor));
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                justFullyExpanded = false;
                listView.canStopFlinger = true;
                expandAnimator.removeListener(this);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                justFullyExpanded = false;
                listView.canStopFlinger = true;
            }
        });
        final View view = layoutManager.findViewByPosition(0);
        if (view != null) {
            justFullyExpanded = true;
            final int actionBarHeight = ActionBar.getCurrentActionBarHeight()
                    + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            listView.smoothScrollBy(0, view.getTop() - listView.getMeasuredWidth() - getActionsExtraHeight() + actionBarHeight,
                    (int) expandAnimator.getDuration(), (Interpolator) expandAnimator.getInterpolator());
            listView.canStopFlinger = false;
        }
        expandNameYStartedFrom = nameTextView.getTranslationY();
        expandOnlineYStartedFrom = onlineTextView.getTranslationY();
        expandAnimator.start();
        try {
            avatarContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignore) {
        }
    }

    /**
     * The collapse half (ProfileActivity.java:8543-8602): a scroll toward the rows under the square
     * hands the header back to its small avatar, and the animator runs it from the fraction the
     * scroll left. The gallery hand-back and the media counter are left out.
     */
    private void startAvatarCollapse(float durationFactor) {
        isPulledDown = false;
        expandAnimator.cancel();
        avatarImage.getImageReceiver().setAllowStartAnimation(true);
        avatarImage.getImageReceiver().startAnimation();
        float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture);
        expandAnimatorValues[0] = value;
        expandAnimatorValues[1] = 0f;
        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        expandAnimator.setDuration((long) (value * 250f / durationFactor));
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                expandAnimator.removeListener(this);
            }
        });
        expandAnimator.start();
        try {
            avatarContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignore) {
        }
    }

    /**
     * setAvatarExpandProgress (ProfileActivity.java:6254): the fraction the release animator
     * reports. The avatar grows out of its 100 dp frame and its round corners open, the two titles
     * travel upstream's own quadratic path to the square's bottom edge and fade to white, and the
     * photo's larger copy takes over as the alpha of the avatar's foreground layer. The gallery,
     * stories, gifts and action placements are Telegram-only.
     */
    private void setAvatarExpandProgress(float animatedFraction) {
        if (actionBar == null) {
            return;
        }
        final int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        final float value = currentExpandAnimatorValue = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture = animatedFraction);

        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarContainer.setTranslationY(lerp((float) Math.ceil(avatarY), 0f, value));
        avatarImage.setRoundRadiusForExpand((int) AndroidUtilities.lerp(getSmallAvatarRoundRadius(), 0f, value));
        avatarImage.setBlurRadiusProgressForExpand(value, avatarScale, isPulledDown);
        if (expandedAvatarBound) {
            avatarImage.setForegroundAlpha(value);
        }

        final float diff = calculateHeaderExtraDiff();

        if (diff >= 1f) {
            avatarImage.setAlpha(1f);
        }

        if (!isPulledDown) {
            refreshNameAndOnlineYBasedOnExpand(diff, newTop);
        } else if (extraHeight >= getHeaderOnlyExtraHeight() && expandProgress < 0.33f) {
            refreshNameAndOnlineY();
        }

        float nameX = this.nameX;
        float onlineX = this.onlineX;

        if (diff < 1f) {
            nameX = AndroidUtilities.lerp(-dpf2(42 + 21), nameX, diff);
            onlineX = AndroidUtilities.lerp(-dpf2(42 + 21), onlineX, diff);
        }
        final float kx = dpf2(8);
        final float ky = isPulledDown ? dpf2(8) : dpf2(-24);

        final float nameTextViewXEnd = AndroidUtilities.dpf2(18f) - ((FrameLayout.LayoutParams) nameTextView.getLayoutParams()).leftMargin;
        final float nameTextViewYEnd = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) - nameTextView.getBottom();
        final float nameTextViewCx = kx + nameX + (nameTextViewXEnd - nameX) / 2f;
        final float nameTextViewCy = ky + nameY + (nameTextViewYEnd - nameY) / 2f;
        final float nameTextViewX = (1 - value) * (1 - value) * nameX + 2 * (1 - value) * value * nameTextViewCx + value * value * nameTextViewXEnd;
        final float nameTextViewY = (1 - value) * (1 - value) * nameY + 2 * (1 - value) * value * nameTextViewCy + value * value * nameTextViewYEnd;

        final float onlineTextViewXEnd = AndroidUtilities.dpf2(16f) - ((FrameLayout.LayoutParams) onlineTextView.getLayoutParams()).leftMargin;
        final float onlineTextViewYEnd = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) - onlineTextView.getBottom();
        final float onlineTextViewCx = kx + onlineX + (onlineTextViewXEnd - onlineX) / 2f;
        final float onlineTextViewCy = ky + onlineY + (onlineTextViewYEnd - onlineY) / 2f;
        final float onlineTextViewX = (1 - value) * (1 - value) * onlineX + 2 * (1 - value) * value * onlineTextViewCx + value * value * onlineTextViewXEnd;
        final float onlineTextViewY = (1 - value) * (1 - value) * onlineY + 2 * (1 - value) * value * onlineTextViewCy + value * value * onlineTextViewYEnd;

        final float minEndNameY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight() / 2f
                - 21 * AndroidUtilities.density
                + actionBar.getTranslationY();
        final float minNameY = (float) Math.floor(minEndNameY) + AndroidUtilities.dp(1.3f);
        final float minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f);

        nameTextView.setTranslationX(nameTextViewX);
        nameTextView.setTranslationY(Math.max(minNameY, nameTextViewY));
        onlineTextView.setTranslationX(onlineTextViewX);
        onlineTextView.setTranslationY(Math.max(minOnlineY, onlineTextViewY));

        // Over the picture the two lines go white, and the back arrow with them, the way upstream
        // fades them on the big picture (ProfileActivity.java:6379-6391).
        nameTextView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_profile_title), Color.WHITE, value));
        onlineTextView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_actionBarDefaultSubtitle), 0xB3FFFFFF, value));
        actionBar.setItemsColor(ColorUtils.blendARGB(getThemedColor(Theme.key_actionBarDefaultIcon), Color.WHITE, value), false);

        if (extraHeight > getHeaderOnlyExtraHeight()) {
            nameTextView.setPivotY(AndroidUtilities.lerp(0, nameTextView.getMeasuredHeight(), value));
            nameTextView.setScaleX(AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value));
            nameTextView.setScaleY(AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value));
        }
        needLayoutText(Math.min(1f, diff));

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = (int) lerp(dpf2(100), listView.getMeasuredWidth() / avatarScale, value);
        params.height = (int) lerp(dpf2(100), (extraHeight + newTop) / avatarScale, value);
        fixAvatarImageInCenter();
        avatarContainer.requestLayout();
        topView.invalidate();
    }

    /** refreshNameAndOnlineY (ProfileActivity.java:8884); the media counter is left out. */
    private void refreshNameAndOnlineY() {
        if (isPulledDown && expandAnimator != null && expandAnimator.isRunning()) {
            nameY = expandNameYStartedFrom;
            onlineY = expandOnlineYStartedFrom;
            return;
        }
        final int newTop = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        refreshNameAndOnlineYBasedOnExpand(calculateHeaderExtraDiff(), newTop);
    }

    /** refreshNameAndOnlineYBasedOnExpand (ProfileActivity.java:8897); the rating offset is left out. */
    private void refreshNameAndOnlineYBasedOnExpand(float diff, float newTop) {
        float scale;
        float expand = Math.max(0f, Math.min(1f, (extraHeight - getHeaderExtraHeight()) / (listView.getMeasuredWidth() - newTop - getHeaderOnlyExtraHeight())));
        if (extraHeight < getHeaderExtraHeight() && expand < 0.33f) {
            scale = (24 + (54f + (42 - 24)) * diff) / 42.0f;
        } else {
            scale = AndroidUtilities.lerp((42f + 54f) / 42f, (42f + 42f + 54f) / 42f, Math.min(1f, expand * 3f));
        }

        float endNameY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight() / 2f * (1f + diff)
                - 21 * AndroidUtilities.density
                + actionBar.getTranslationY();

        float avatarBottom = (float) Math.floor(endNameY) + (AndroidUtilities.dp(42) * scale + dpf2(8)) * diff;
        nameY = avatarBottom + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7) * diff;
        onlineY = avatarBottom + AndroidUtilities.dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff;
    }

    // ---------------------------------------------------------------------------------------------
    // The opening animation, ProfileActivity's `playProfileAnimation == 2` (9895-10265, and the
    // layout at 8623-8690): the avatar flies from the chat header's capsule to the big centered
    // picture, its round corners open, the list fades in under the header and the background
    // blends to the avatar's average colour. Upstream's 250 ms duration and its
    // CubicBezierInterpolator.DEFAULT are kept, as is the 50 ms start delay it runs the animator
    // with (`AndroidUtilities.runOnUIThread(animatorSet::start, 50)`, ProfileActivity.java:10264).
    // ---------------------------------------------------------------------------------------------

    @Override
    public AnimatorSet onCustomTransitionAnimation(final boolean isOpen, final Runnable callback) {
        if (getArguments() != null && getArguments().getBoolean("hasMainTabs", false)) {
            return null;
        }
        if (!(playProfileAnimation != 0 && allowProfileAnimation && !isPulledDown)) {
            return null;
        }

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(playProfileAnimation == 2 ? 250 : 180);
        if (listView != null) {
            listView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        if (isOpen) {
            setAvatarAnimationProgress(0);
            if (frameLayout != null) {
                frameLayout.setBackgroundColor(0);
            }
            avatarColor = getAverageColor();
            nameTextView.setTextColor(Color.WHITE);
            onlineTextView.setTextColor(0xB3FFFFFF);
            actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
            nameTextView.setAlpha(0.0f);
            onlineTextView.setAlpha(0.0f);

            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "avatarAnimationProgress", 0.0f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(onlineTextView, View.ALPHA, 1.0f));
            if (actionsView != null) {
                actionsView.setAlpha(0.0f);
                animators.add(ObjectAnimator.ofFloat(actionsView, View.ALPHA, 1.0f));
            }
            animatorSet.playTogether(animators);
        } else {
            captureBackwardInitialValues();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "avatarAnimationProgress", 1.0f, 0.0f));
            animatorSet.playTogether(animators);
        }

        animatorSet.setInterpolator(playProfileAnimation == 2
                ? CubicBezierInterpolator.DEFAULT
                : new android.view.animation.DecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.run();
            }
        });

        AndroidUtilities.runOnUIThread(animatorSet::start, 50);
        return animatorSet;
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (isOpen && actionsView != null) {
            actionsView.startAnimatedActions();
        }
        if ((!isOpen && backward || isOpen && !backward) && playProfileAnimation != 0
                && allowProfileAnimation && !isPulledDown) {
            openAnimationInProgress = true;
            if (!isOpen) {
                captureBackwardInitialValues();
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (openAnimationInProgress) {
            openAnimationInProgress = false;
        }
        if (isOpen && !backward && playProfileAnimation != 0) {
            if (actionsView != null) {
                actionsView.setParentExpanded(0);
            }
            restoreTransitionColors();
            checkListViewScroll();
            needLayout(true);
        } else if (!isOpen && listView != null) {
            listView.setAlpha(1f);
        }
    }

    /** setAvatarAnimationProgress (ProfileActivity.java:9797-9835) */
    @androidx.annotation.Keep
    public void setAvatarAnimationProgress(float progress) {
        avatarAnimationProgress = currentExpandAnimatorValue = progress;
        if (actionsView != null) {
            actionsView.setParentExpanded(progress);
        }
        if (listView != null) {
            listView.setAlpha(progress);
            listView.setTranslationX(dp(48) - dp(48) * progress);
        }
        // The background under the header blends from the chat screen's colour - transparent, so
        // the screen behind shows through - to the avatar's average colour.
        int color = avatarColor != 0 ? avatarColor : getThemedColor(Theme.key_windowBackgroundWhite);
        int from = androidx.core.graphics.ColorUtils.setAlphaComponent(getThemedColor(Theme.key_actionBarDefault), 0);
        if (topView != null) {
            topView.setTransitionColor(androidx.core.graphics.ColorUtils.blendARGB(from, color, progress));
        }
        if (nameTextView != null) {
            nameTextView.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(
                    getThemedColor(Theme.key_profile_title), Color.WHITE, progress));
            onlineTextView.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(
                    getThemedColor(Theme.key_actionBarDefaultSubtitle), 0xB3FFFFFF, progress));
        }
        updateActionsPosition();
        needLayout(true);
    }

    private void restoreTransitionColors() {
        if (topView != null) {
            topView.setTransitionColor(0);
        }
        if (nameTextView == null) {
            return;
        }
        nameTextView.setTextColor(getThemedColor(Theme.key_profile_title));
        onlineTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_avatar_actionBarSelectorBlue), false);
        if (listView != null) {
            listView.setAlpha(1f);
            listView.setTranslationX(0f);
        }
    }

    /**
     * The avatar along the flight path, ProfileActivity.java:8623-8690 with the Telegram-only
     * overlays (stories, gifts, the time item, the emoji status) left out: it starts as the 42 dp
     * capsule picture at the header avatar's X and grows to 138% of the 100 dp frame as its round
     * corners open to the full-width square.
     */
    private void openAnimationAvatarLayout(float newTop) {
        float avX = prevAvatarTranslation;
        float avY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight() / 2.0f
                - 21 * AndroidUtilities.density
                + actionBar.getTranslationY();

        avatarScale = lerp(42, 138, avatarAnimationProgress) / 100f;
        pullUpProgress = 0.0f;

        avatarImage.setRoundRadiusForExpand(
                (int) AndroidUtilities.lerp(getSmallAvatarRoundRadius(), 0f, avatarAnimationProgress));
        avatarContainer.setTranslationX(AndroidUtilities.lerp(avX, 0, avatarAnimationProgress));
        avatarContainer.setTranslationY(AndroidUtilities.lerp((float) Math.ceil(avY), 0f, avatarAnimationProgress));
        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarImage.setBlurRadiusProgressForExpand(avatarAnimationProgress, avatarScale, true);

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = (int) lerp(dpf2(100), listView.getMeasuredWidth() / avatarScale, avatarAnimationProgress);
        params.height = (int) lerp(dpf2(100), (extraHeight + newTop) / avatarScale, avatarAnimationProgress);
        avatarContainer.requestLayout();
        fixAvatarImageInCenter();

        // The title keeps the scale the flight ends at, pivoted from its base, the way upstream's
        // collapsing copy does (nameTextView[1].setPivotY(measuredHeight), 1.38f).
        nameTextView.setPivotY(nameTextView.getMeasuredHeight());
        nameTextView.setScaleX(AndroidUtilities.lerp(1f, 1.38f, avatarAnimationProgress));
        nameTextView.setScaleY(AndroidUtilities.lerp(1f, 1.38f, avatarAnimationProgress));
    }

    private void captureBackwardInitialValues() {
        backwardInitialValues[0] = avatarScale;
        backwardInitialValues[1] = avatarContainer == null ? 0f : avatarContainer.getTranslationX();
        backwardInitialValues[2] = avatarContainer == null ? 0f : avatarContainer.getTranslationY();
        backwardInitialValues[3] = actionsView == null ? 1f : actionsView.getAlpha();
    }

    /** The avatar's own average colour, as ProfileActivity.java:10268-10273 reads it. */
    private int getAverageColor() {
        try {
            if (avatarImage != null && avatarImage.getImageReceiver().getBitmap() != null) {
                return AndroidUtilities.calcBitmapColor(avatarImage.getImageReceiver().getBitmap());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    /** updateExtraViews (ProfileActivity.java:8802-8817); the emoji-status and action placements are Telegram-only. */
    private void updateExtraViews(float newTop) {
        if (topView != null) {
            topView.invalidate();
        }
        updateActionsPosition();
    }

    /** updateActionsPosition (ProfileActivity.java:11198-11230); the call and music actions are Telegram-only. */
    private void updateActionsPosition() {
        if (actionsView == null || listView == null) {
            return;
        }
        final int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight();
        actionsView.isOpeningLayout = openAnimationInProgress;
        if (openAnimationInProgress && playProfileAnimation == 2 && avatarContainer != null) {
            float vh = avatarContainer.getHeight() * avatarContainer.getScaleY();
            actionsView.clipHeight = avatarContainer.getY() + vh;
            actionsView.setAlpha(avatarAnimationProgress);
            actionsView.updatePosition(listView.getMeasuredWidth(), AndroidUtilities.dp(74));
        } else {
            actionsView.clipHeight = -1;
            float bottom = extraHeight + newTop;
            float height = Math.min(AndroidUtilities.dp(74), bottom - newTop);
            actionsView.updatePosition(bottom - height, height);
        }
    }

    /**
     * Whether the header's action row currently shows [key] (one of [ProfileActionsView]'s KEY_*):
     * the observable answer to what the site said this reader may do.
     */
    boolean hasAction(int key) {
        return actionsView != null && actionsView.hasAction(key);
    }

    /** updateGooey (ProfileActivity.java:6009); the topic and stories branches are Telegram-only. */
    private void updateGooey() {
        avatarGooey.setPullProgress(pullUpProgress);
        avatarGooey.setBlurIntensity(Math.min((MathUtils.clamp(pullUpProgress, 0.2f, 0.7f) - 0.2f) / 0.5f, 0.75f));
        avatarGooey.setGooeyEnabled(pullUpProgress > 0 && pullUpProgress < 1);
        avatarGooey.setVisibility(pullUpProgress >= 1.0f ? View.GONE : View.VISIBLE);
    }

    /** fixLayout (ProfileActivity.java:8996) */
    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    checkListViewScroll();
                    needLayout(true);
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    /**
     * Upstream's header backdrop (ProfileActivity.java:1214), the view that paints the screen's
     * background over the rows scrolling under the header. Its peer-color gradient is left out;
     * during the opening animation it carries the blend from the chat's header colour to the
     * avatar's average colour (ProfileActivity.java:9820-9832).
     */
    private class TopView extends FrameLayout {

        private final Paint paint = new Paint();
        private int transitionColor;

        public TopView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void setTransitionColor(int color) {
            transitionColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int height = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            final float v = height + extraHeight;
            final int y1 = (int) v;
            if (y1 != 0) {
                paint.setColor(transitionColor != 0 ? transitionColor : getThemedColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, 0, getMeasuredWidth(), y1, paint);
            }
        }
    }

    /**
     * The root of the screen, upstream's {@code NestedFrameLayout}
     * (ProfileActivity.java:1710-1810): while the tab area is pinned to the list's top, a drag the
     * outer list cannot take is handed to the visible page's own list, so the pages scroll the way
     * SharedMediaLayout's do.
     */
    private class NestedFrameLayout extends FrameLayout implements NestedScrollingParent3 {

        private final NestedScrollingParentHelper nestedScrollingParentHelper =
                new NestedScrollingParentHelper(this);

        NestedFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            // The head room is settled before the children lay out, so the seeded offset is
            // what this very pass places the first row with.
            settleHeaderInList();
            super.onLayout(changed, left, top, right, bottom);
            checkListViewScroll();
        }

        @Override
        public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
            return mediaView != null && axes == ViewCompat.SCROLL_AXIS_VERTICAL;
        }

        @Override
        public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes, type);
        }

        @Override
        public void onStopNestedScroll(@NonNull View target, int type) {
            nestedScrollingParentHelper.onStopNestedScroll(target, type);
        }

        @Override
        public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                   int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
            // ProfileActivity.NestedFrameLayout.onNestedScroll: an upward drag the outer list
            // cannot take continues into the page's list while the tab area is pinned.
            RecyclerListView pageList = currentPageList();
            if (pageList != null && mediaView != null && mediaView.getTop() <= 0) {
                consumed[1] = dyUnconsumed;
                pageList.scrollBy(0, dyUnconsumed);
            }
        }

        @Override
        public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                   int dxUnconsumed, int dyUnconsumed, int type) {
            // The Parent3 overload above is the one the platform calls.
        }

        @Override
        public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            // ProfileActivity.NestedFrameLayout.onNestedPreScroll: a drag down the pinned tab area
            // returns its page to the top before the outer list scrolls back up.
            if (mediaView == null || listView == null) {
                return;
            }
            int top = mediaView.getTop();
            if (dy > 0 && top <= 0) {
                RecyclerListView pageList = currentPageList();
                if (pageList != null) {
                    LinearLayoutManager manager = (LinearLayoutManager) pageList.getLayoutManager();
                    int pos = manager == null ? RecyclerView.NO_POSITION : manager.findFirstVisibleItemPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        View holder = manager.findViewByPosition(pos);
                        int childTop = holder != null ? holder.getTop() : -1;
                        int paddingTop = pageList.getPaddingTop();
                        if (childTop != paddingTop || pos != 0) {
                            consumed[1] = dy;
                            pageList.scrollBy(0, dy);
                        }
                    }
                }
            }
        }
    }

    /** One row of the info list. */
    private static class Row {
        final int type;
        final String label;
        final String value;
        boolean divider;

        private Row(int type, String label, String value) {
            this.type = type;
            this.label = label;
            this.value = value;
        }

        static Row section(String label) {
            return new Row(TYPE_SECTION, label, null);
        }

        static Row detail(String label, String value) {
            return new Row(TYPE_DETAIL, label, value);
        }

        static Row bio(String text) {
            return new Row(TYPE_BIO, null, text);
        }

        static Row media() {
            return new Row(TYPE_MEDIA, null, null);
        }
    }
}

