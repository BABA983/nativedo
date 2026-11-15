package io.github.baba983.nativedo.profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ProfileButtonsGlass;
import org.telegram.ui.Components.ProfileGooeyView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

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
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.core.model.UserProfile;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.feature.profile.data.ProfileExtrasRepository;
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.NewTopicFragment;
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
     * coordinate space. Upstream captures the same X for a profile opened from the chat header
     * ({@code prevAvatarTranslation}, through {@code ViewPositionWatcher.computeXCoordinateInParent},
     * ProfileActivity.java:10080), and ProfileActivity:8623-8690 lays the avatar out along the path
     * this argument starts. Its presence is also what selects the flight; the transition around it
     * is the ordinary header one (see {@link #createView}).
     */
    private static final String ARG_AVATAR_X = "avatarX";
    private static final String ARG_AVATAR_Y = "avatarY";
    private static final String ARG_AVATAR_SIZE = "avatarSize";

    private static final int TYPE_SECTION = 1;
    private static final int TYPE_DETAIL = 2;
    private static final int TYPE_BIO = 3;
    private static final int TYPE_ACTION = 4;
    /** The tab area - the strip and its pages - the last row of the info list. */
    private static final int TYPE_MEDIA = 6;
    /** The row a session state draws in place of the info rows: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 7;
    private static final int TYPE_SHADOW = 8;

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
    /** The action row's glass, from the fragment's own simple-glass source. */
    private ProfileButtonsGlass buttonsGlass;
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
    /** The initials the header draws until the site's picture is on disk. */
    private AvatarDrawable avatarPlaceholder;
    private AppContainer container;
    /** Summary stats from `/u/{u}/summary.json`; null until (and if) they arrive. */
    private io.github.baba983.nativedo.core.model.UserSummary summary;

    /**
     * The explicit session state the list shows in place of the info rows, or {@code null} while it
     * shows a loaded profile. It is dropped on an identity change and on a view recreation.
     */
    private SessionUiState stateUi;
    /** The session value the screen last acted on, so a change can be told from a repeat. */
    private AppSessionState lastSessionState;
    /**
     * Bumped whenever the account changes or the views go away. A read captures it before it starts
     * and drops its own answer if it no longer matches, so a late profile from the previous account
     * cannot repopulate the screen.
     */
    private int loadEpoch;
    /** The session-state observer; cancelled with the views and started again with the next ones. */
    private kotlinx.coroutines.Job sessionObserver;

    // The opening animation, ported from ProfileActivity's `playProfileAnimation`: 1 is the
    // ordinary header transition (`ChatAvatarContainer.openProfile` picks it for a header tap, and
    // 2 for the tap on the avatar itself), 0 is no transition at all. This screen has one header
    // tap target - the topic's capsule - so it is the ordinary transition; `avatarFlight` is the
    // host extra that keeps the capsule's flight (ProfileActivity.java:9895-10265 lays type 2 out,
    // and the geometry here is that path, landing in the collapsed header instead of the expanded
    // photo).
    private int playProfileAnimation;
    /** Whether the avatar flies from the capsule ({@link #forUserFromAvatar}); the ordinary palette
     *  and the 180 ms/DecelerateInterpolator clock stay those of upstream's type 1. */
    private boolean avatarFlight;
    private boolean openAnimationInProgress;
    /**
     * Whether this fragment's own enter or exit transition is running, animated or not: upstream
     * holds the profile's own view updates for its whole length (`setAnimationInProgress`,
     * released by `onAnimationFinish`, ProfileActivity.java:9727-9778).
     */
    private boolean transitionAnimationInProgress;
    private float avatarAnimationProgress;
    private float prevAvatarTranslation;
    private float prevAvatarY;
    private float prevAvatarSize;
    private org.telegram.messenger.ImageReceiver transitionAvatar;
    /**
     * captureBackwardInitialValues (ProfileActivity.java:8155-8191): what the close animation
     * travels back to. Upstream keeps sixteen slots; this screen keeps the ones its own views
     * have - the avatar (scale, X, Y), the action row's alpha, the title's scale/Y/X and the
     * online line's Y/X (upstream's [7]-[13]). [14]/[15] are the avatar frame's captured width
     * and height and [16] the avatar's own captured round radius, so an ordinary close keeps the
     * header's round corners while a deliberate expanded-photo close starts from the square the
     * pull left it.
     */
    private final float[] backwardInitialValues = new float[17];
    /** The list offset the close started from; backwardAnimationLayout's diff is read against it. */
    private float backwardTransitionFromExtraHeight;
    private boolean allowProfileAnimation = true;
    /** Whether the fragment is being opened (upstream's {@code isFragmentOpened = isOpen}). */
    private boolean isFragmentOpened;
    /**
     * The screen below, when it is a topic: during the profile's flight its action bar's children
     * - the capsule avatar and the title - are not drawn, the way upstream hides the chat header's
     * ({@code previousActionBar.setSkipDrawChild(true)}, ProfileActivity.java:9940/10229).
     */
    private org.telegram.ui.ActionBar.ActionBar previousTransitionActionBar;


    // The collapsing header, ported from ProfileActivity: the gooey avatar and the two text views
    // its needLayout() translates and scales as the list scrolls.
    private TopView topView;
    private ProfileActivity.AvatarImageView avatarImage;
    private FrameLayout avatarContainer;
    private FrameLayout avatarContainer2;
    private ProfileGooeyView avatarGooey;
    private SimpleTextView nameTextView;
    private LinkSpanDrawable.ClickableSmallTextView onlineTextView;
    /**
     * The media counter (ProfileActivity.java:5655-5668): the line that takes the status line's
     * place under the name while the tab strip is pinned - upstream's {@code mediaCounterTextView},
     * in the same slot and size, drawn from {@code key_player_actionBarSubtitle}. Upstream fills it
     * with the current tab's count; here it carries the tab the reader is on.
     */
    private TextView mediaCounterTextView;
    // The media header, ported from ProfileActivity.setMediaHeaderVisible (7802-7950) and its
    // ACTIONBAR_HEADER_PROGRESS property (7713-7800): when the tab area pins under the action bar
    // the bar takes the shared-media state - the title and item colours interpolate to the
    // player/action-mode pair, the status line hands over to the tab counter, the strip opens and
    // the shadow recedes - with upstream's 150 ms and CubicBezierInterpolator.DEFAULT.
    private AnimatorSet headerAnimatorSet;
    private AnimatorSet headerShadowAnimatorSet;
    private float mediaHeaderAnimationProgress;
    private boolean mediaHeaderVisible;
    private float headerShadowAlpha = 1.0f;
    private final org.telegram.ui.Components.AnimationProperties.FloatProperty<ProfileFragment> ACTIONBAR_HEADER_PROGRESS =
            new org.telegram.ui.Components.AnimationProperties.FloatProperty<ProfileFragment>("mediaHeaderAnimationProgress") {
                @Override
                public void setValue(ProfileFragment object, float value) {
                    mediaHeaderAnimationProgress = value;
                    applyMediaHeaderColors();
                }

                @Override
                public Float get(ProfileFragment object) {
                    return mediaHeaderAnimationProgress;
                }
            };
    /** HEADER_SHADOW (ProfileActivity.java:760-771): the shadow under the media header. */
    private final org.telegram.ui.Components.AnimationProperties.FloatProperty<ProfileFragment> HEADER_SHADOW =
            new org.telegram.ui.Components.AnimationProperties.FloatProperty<ProfileFragment>("headerShadow") {
                @Override
                public void setValue(ProfileFragment object, float value) {
                    headerShadowAlpha = value;
                    if (topView != null) {
                        topView.invalidate();
                    }
                }

                @Override
                public Float get(ProfileFragment object) {
                    return headerShadowAlpha;
                }
            };
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
                applyThemeColors();
                runAfterFlight(this::notifyRowsChanged);
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
    /** View updates wait for the profile transition, like upstream's deferred notifications. */
    private final java.util.ArrayDeque<Runnable> deferredUntilFlightEnd = new java.util.ArrayDeque<>();
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
     * to go back to. Upstream's own profile tab is the same state ({@code hasMainTabs}): its back
     * button becomes the QR button for the account's own link (ProfileActivity.java:2480-2491),
     * which is what this screen shows there.
     */
    public static ProfileFragment forRoot(String username, String displayName) {
        ProfileFragment fragment = forUser(username, displayName);
        fragment.getArguments().putBoolean(ARG_ROOT, true);
        fragment.getArguments().putBoolean("hasMainTabs", true);
        return fragment;
    }

    /**
     * ChatAvatarContainer.openProfile's avatar flight, with the host's actual capsule geometry.
     */
    public static ProfileFragment forUserFromAvatar(String username, String displayName,
            org.telegram.ui.Components.BackupImageView avatar, ViewGroup parent) {
        ProfileFragment fragment = forUser(username, displayName);
        int[] source = new int[2];
        int[] origin = new int[2];
        avatar.getLocationOnScreen(source);
        parent.getLocationOnScreen(origin);
        float scale = avatar.getScaleX();
        for (android.view.ViewParent p = avatar.getParent(); p instanceof View && p != parent;
                p = p.getParent()) {
            scale *= ((View) p).getScaleX();
        }
        fragment.getArguments().putFloat(ARG_AVATAR_X, source[0] - origin[0]);
        fragment.getArguments().putFloat(ARG_AVATAR_Y, source[1] - origin[1]);
        fragment.getArguments().putFloat(ARG_AVATAR_SIZE, avatar.getWidth() * scale);
        fragment.transitionAvatar = avatar.getImageReceiver();
        return fragment;
    }

    public ProfileFragment(Bundle args) {
        super(args);
    }

    @Override
    protected View createView(Context context) {
        final boolean hasMainTabs = getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
        // ChatAvatarContainer.openProfile sets `setPlayProfileAnimation(byAvatar ? 2 : 1)`: the tap
        // on the topic header's capsule is a header tap, so upstream gives it the ordinary header
        // transition - 180 ms, DecelerateInterpolator, the profile's own palette, no expansion of
        // the action row. The port keeps the capsule's flight as a host extra (`avatarFlight`): the
        // avatar still travels from the drawn capsule to the collapsed header's round 96 dp rest,
        // but nothing else passes through the expanded photo's state. That photo is opened only by
        // an explicit pull (startAvatarExpand).
        final boolean fromAvatar = getArguments() != null && getArguments().containsKey(ARG_AVATAR_X);
        playProfileAnimation = fromAvatar ? 1 : 0;
        avatarFlight = fromAvatar;
        prevAvatarTranslation = getArguments() == null ? 0f : getArguments().getFloat(ARG_AVATAR_X, 0f);
        prevAvatarY = getArguments() == null ? 0f : getArguments().getFloat(ARG_AVATAR_Y, 0f);
        prevAvatarSize = getArguments() == null ? dp(42) : getArguments().getFloat(ARG_AVATAR_SIZE, dp(42));
        allowProfileAnimation = playProfileAnimation != 0 && !hasMainTabs;
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        container = NativeDo.container(getParentActivity());
        // The profile draws behind its own transparent action bar, the way upstream's profile does
        // (ProfileActivity.java:2470-2478), so it keeps its view the whole screen.
        actionBar.setAddToContainer(false);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setForceSkipTouches(true);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        if (hasMainTabs) {
            // Upstream's own profile tab turns the back button into the QR button
            // (ProfileActivity.java:2480-2491): the same BackDrawable slot, the QR glyph in it,
            // the header's own colour filter on the image (upstream re-applies it from its
            // setItemsColor override, 2459-2460/2439-2446 - ActionBar.setItemsColor recolours a
            // vector drawable the same way, so the icon follows every header animation), and the
            // tap opens the QR screen for the profile's own link.
            actionBar.setBackButtonDrawable(new org.telegram.ui.ActionBar.BackDrawable(false));
            actionBar.getBackButton().setTranslationX(dp(2));
            actionBar.backButtonImageView.setContentDescription(
                    org.telegram.messenger.LocaleController.getString(R.string.QrCode));
            actionBar.backButtonImageView.setImageResource(R.drawable.outline_header_qr_24);
            actionBar.backButtonImageView.setColorFilter(
                    getThemedColor(Theme.key_actionBarDefaultIcon), android.graphics.PorterDuff.Mode.SRC_IN);
            actionBar.getBackButton().setOnClickListener(v -> presentFragment(ProfileQrFragment.forProfile(
                    username(), displayName(), profile == null ? null : profile.getAvatarTemplate(), profileUrl())));
        } else {
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

        // Upstream's profile buttons on glass (ProfileActivity.setupHeaderGlass): the fragment's
        // simple-glass source supplies the fill, the action row draws through its factory.
        buttonsGlass = new ProfileButtonsGlass(glassEngine, getBaseSimpleGlass());
        buttonsGlass.setButtonColor(getThemedColor(Theme.key_windowBackgroundWhite), false);
        actionsView.setGlassFactory(buttonsGlass.actionsFactory);

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
                            listView.smoothScrollBy(0, layoutManager.getDecoratedTop(view) - listView.getMeasuredWidth()
                                    - getActionsExtraHeight() + actionBarHeight,
                                    CubicBezierInterpolator.EASE_OUT_QUINT);
                        } else {
                            listView.smoothScrollBy(0, layoutManager.getDecoratedTop(view) - getHeaderExtraHeight(),
                                    CubicBezierInterpolator.EASE_OUT_QUINT);
                        }
                    }
                }
                return result;
            }
        };
        headerSeeded = false;
        listView.setSections();
        listView.applyPaddingToSections = false;
        // The scroll clamp is upstream's (ProfileActivity.java:4283-4312): the stretch is damped
        // while the finger drags it, and a scroll toward the rows gives the pull-down its rest.
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                final View view = findViewByPosition(0);
                if (view != null) {
                    final int canScroll = getDecoratedTop(view) - getHeaderExtraHeight();
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
        // The info rows are text to read; the one tappable thing they can carry is the session
        // state's own row, whose tap recovers the session or reads again.
        listView.setOnItemClickListener((view, position) -> {
            if (stateUi != null) {
                onStateAction();
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        // ProfileActivity.java:4223 sets the same surface on its own list; the screen keeps one
        // because the open animation makes the fragment view itself transparent
        // (`fragmentView.setBackgroundColor(0)`, ProfileActivity.java:9986) and only restores the
        // cover through what the list and the header paint themselves. Without it a later
        // swipe-back drag shows the topic through the profile's rows.
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView.setPadding(0, getHeaderExtraHeight(), 0, listBottomPadding());
        // The rows of the info list are text to read; the tappable things are the action row's
        // buttons and the pages' own rows, each with their own click handling.
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkListViewScroll();
                // ProfileActivity.java:5783: the tab area's own pinned state, which upstream hands
                // to its SharedMediaLayout (it drives the fast-scroll halo there; here it is the
                // state the harness pins and the strip's own bookkeeping).
                if (mediaView != null) {
                    mediaView.setPinnedToTop(mediaView.getTop() <= 0);
                }
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        // DialogsActivity's own first-load view (4059-4062): the row this list is about to draw,
        // standing in for it while the profile has not answered. It rides behind the header, so
        // the screen still shows what it already knows; settleHeaderInList anchors it at the
        // header's rest height, where the first real row lands.
        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progressView.setVisibility(View.GONE);
        listView.setEmptyView(progressView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        frameLayout.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

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
        if (transitionAvatar != null) {
            // The capsule's picture whole, or the initials if it has not drawn yet.
            avatarImage.setImageDrawable(transitionAvatar.getDrawable() != null
                    ? transitionAvatar.getDrawable() : headerAvatarDrawable());
            avatarImage.setAnimateFromImageReceiver(transitionAvatar);
            allowPullingDown = canPullDown();
        } else {
            // A pushed profile has no source to fly: the initials stand in until the bytes land.
            avatarImage.setImageDrawable(headerAvatarDrawable());
        }

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

        // The counter line the media header hands the status line over to, in upstream's own slot
        // (ProfileActivity.java:5655-5668: key_player_actionBarSubtitle, 13.5 dp, 109.33 left).
        mediaCounterTextView = new TextView(context);
        mediaCounterTextView.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));
        mediaCounterTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, AndroidUtilities.dp(13.5f));
        mediaCounterTextView.setSingleLine(true);
        mediaCounterTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mediaCounterTextView.setGravity(Gravity.LEFT);
        mediaCounterTextView.setAlpha(0.0f);
        mediaCounterTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        avatarContainer2.addView(mediaCounterTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 109.33f, -2, 8, 0));

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
        // The session is read before the profile: an account with no usable session gets the
        // explicit state (and the read is not even sent), and a tab opened before a login catches
        // up the moment the session lands.
        stateUi = null;
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        observeSession();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            loadProfile(false);
        }
        ViewCompat.requestApplyInsets(frameLayout);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
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
        deferredUntilFlightEnd.clear();
        super.clearViews();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        stopSessionObservation();
        loadEpoch++;
        deferredUntilFlightEnd.clear();
        transitionAnimationInProgress = false;
        // A flight torn down mid-way (the host destroyed) must not leave the topic's header
        // hidden: the animator's own end listener restores it otherwise.
        if (previousTransitionActionBar != null) {
            previousTransitionActionBar.setSkipDrawChild(false);
            previousTransitionActionBar = null;
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
     * so a value equal to it changes nothing; anything else drops the old account's profile and,
     * when the session is usable again, reads it again - immediately, so an open tab never keeps
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
                if (isRootProfile()) {
                    // The own tab root is whatever account is signed in: a new one is that
                    // account's own profile, so the header, the tabs and their streams follow it.
                    refreshHeaderIdentity();
                    resetMediaForReload();
                }
                notifyRowsChanged();
                // The cache may hold the previous read of this account; a recovery re-reads it.
                loadProfile(true);
            }
        } else {
            stateUi = SessionUi.stateFor(state);
            buildStateRow();
            notifyRowsChanged();
        }
    }

    /** Drops everything the tab area read for the previous account, so a new identity's tabs are its own. */
    private void resetMediaForReload() {
        topicsFeed = null;
        repliesFeed = null;
        likesFeed = null;
        for (TabPageState page : tabPages) {
            if (page != null) {
                page.resetForNewIdentity();
            }
        }
        for (TabPageState page : tabPages) {
            if (page != null) {
                page.loadFirstIfNeeded();
            }
        }
    }

    /** Drops everything read for the previous identity, including the header's picture. */
    private void resetForReload() {
        profile = null;
        summary = null;
        avatarUser = null;
        expandedAvatarUser = null;
        expandedAvatarBound = false;
        isFollowed = false;
        isMuted = false;
        rows.clear();
        mediaRow = -1;
        bookmarksRow = -1;
    }

    /** The single row a session state draws in place of the profile's own info block. */
    private void buildStateRow() {
        rows.clear();
        mediaRow = -1;
        bookmarksRow = -1;
        // The follow / message / share buttons act on a session that is not usable; they go with
        // the profile they belong to and come back when a read does.
        if (actionsView != null) {
            actionsView.setVisibility(View.GONE);
        }
        rows.add(Row.state(SessionUi.text(getContext(), stateUi)));
    }

    /** The tap on the state row: recover the session, or read again. */
    private void onStateAction() {
        if (stateUi == null) {
            return;
        }
        SessionUiAction action = SessionUi.action(stateUi);
        if (action == SessionUiAction.Retry) {
            stateUi = null;
            resetForReload();
            rows.clear();
            notifyRowsChanged();
            loadProfile(true);
            return;
        }
        openRecovery(action == SessionUiAction.OpenVerification);
    }

    /**
     * The session is dealt with where the site can be: the recovery flow in the login WebView. Its
     * own completion publishes the usable session; this screen's observer re-reads the profile when
     * that changes the account, and the explicit completion re-reads it when it does not, so a
     * cancelled verification neither recovers nor refreshes.
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
        if (isRootProfile()) {
            resetMediaForReload();
        }
        notifyRowsChanged();
        loadProfile(true);
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

    /**
     * The page reaches the bottom of the window itself, pushed or not: the shell reserves no band
     * for the gesture bar, and this screen pads its own last row clear of it.
     */
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
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

    /**
     * Whether this screen is the shell's own {@code 我的} tab root. It is the reader's own profile,
     * so the account it shows is whatever the session currently is; a pushed profile is about one
     * viewed reader and keeps that target even when the account behind the app changes.
     */
    private boolean isRootProfile() {
        return getArguments() != null && getArguments().getBoolean(ARG_ROOT, false);
    }

    private AppSessionState sessionState() {
        if (container == null && getParentActivity() != null) {
            container = NativeDo.container(getParentActivity());
        }
        return container == null ? null : container.getSessionCoordinator().getSessionState().getValue();
    }

    /** The signed-in account's username, which only the own tab root follows; empty when there is none. */
    private String sessionUsername() {
        AppSessionState state = sessionState();
        if (state instanceof AppSessionState.Authenticated) {
            String name = ((AppSessionState.Authenticated) state).getSnapshot().getUsername();
            return name == null ? "" : name;
        }
        return "";
    }

    private String sessionDisplayName() {
        AppSessionState state = sessionState();
        if (state instanceof AppSessionState.Authenticated) {
            String name = ((AppSessionState.Authenticated) state).getSnapshot().getDisplayName();
            return name == null ? "" : name;
        }
        return "";
    }

    private String username() {
        if (isRootProfile()) {
            String session = sessionUsername();
            if (!session.isEmpty()) {
                return session;
            }
        }
        return getArguments() == null ? "" : getArguments().getString(ARG_USERNAME, "");
    }

    private String displayName() {
        if (isRootProfile()) {
            String session = sessionDisplayName();
            if (!session.isEmpty()) {
                return session;
            }
        }
        String name = getArguments() == null ? "" : getArguments().getString(ARG_DISPLAY_NAME, "");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return username();
    }

    /**
     * The own tab root's header follows the account: a different reader's login is a different
     * profile, so the name and @username lines are re-derived from the new session. A pushed
     * profile is never touched by this - it keeps the reader it was opened for.
     */
    private void refreshHeaderIdentity() {
        if (nameTextView != null) {
            nameTextView.setText(displayName());
        }
        if (onlineTextView != null) {
            onlineTextView.setText("@" + username());
        }
        // A changed reading is a changed stand-in: re-seed the initials if no picture is drawn.
        if (avatarImage != null && avatarImage.getImageReceiver().getBitmap() == null) {
            avatarImage.setImageDrawable(headerAvatarDrawable());
        }
    }

    private void loadProfile(boolean forceRefresh) {
        container = NativeDo.container(getParentActivity());
        // Without a usable session there is nothing private to read: the state row is the screen.
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        final int epoch = loadEpoch;
        kotlin.jvm.functions.Function1<UserProfile, kotlin.Unit> onLoaded = loaded -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            // Accept the model now; rebuilding views mid-flight would move the landing target.
            profile = loaded;
            isFollowed = loaded.isFollowed();
            isMuted = loaded.getMuted();
            ownProfile = isOwnProfile();
            avatarUser = DiscourseAvatars.INSTANCE.user(
                    loaded.getUsername(),
                    loaded.getDisplayName(),
                    loaded.getAvatarTemplate(),
                    container.getBaseUrl(),
                    loaded.getAnimatedAvatarUrl());
            // The pull-down draws the same picture across the whole screen width; the site's own
            // template takes a size, so the expanded state reads a larger copy of it.
            expandedAvatarUser = DiscourseAvatars.INSTANCE.user(
                    loaded.getUsername(),
                    loaded.getDisplayName(),
                    loaded.getAvatarTemplate(),
                    container.getBaseUrl(),
                    loaded.getAnimatedAvatarUrl(),
                    DiscourseAvatars.EXPANDED_SIZE);
            runAfterFlight(() -> {
                // The views or session may have changed while this answer waited.
                if (epoch != loadEpoch) {
                    return;
                }
                stateUi = null;
                buildRows(loaded);
                buildActions(loaded);
                buildMedia();
                notifyRowsChanged();
                bindAvatar();
                fetchAvatar();
                loadSummary();
            });
            return kotlin.Unit.INSTANCE;
        };
        kotlin.jvm.functions.Function1<Throwable, kotlin.Unit> onFailed = error -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            FileLog.e("profile failed", error);
            // A profile that did not arrive is its own explicit state, not a blank screen: the
            // failure's kind decides whether the tap recovers the session or just reads again.
            stateUi = SessionUi.stateForLoadFailure(error);
            runAfterFlight(() -> {
                if (epoch != loadEpoch) {
                    return;
                }
                buildStateRow();
                notifyRowsChanged();
            });
            return kotlin.Unit.INSTANCE;
        };
        if (forceRefresh) {
            ProfileLoader.INSTANCE.refresh(container, username(), onLoaded, onFailed);
        } else {
            ProfileLoader.INSTANCE.load(container, username(), onLoaded, onFailed);
        }
    }

    /**
     * The stats the profile endpoint does not carry. The summary is drawn as soon as it arrives
     * (the list re-binds); a site that hides it, or an outage, leaves the fields the profile did
     * send standing.
     */
    private void loadSummary() {
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        final int epoch = loadEpoch;
        ProfileLoader.INSTANCE.loadSummary(container, username(), loaded -> {
            if (epoch != loadEpoch) {
                return kotlin.Unit.INSTANCE;
            }
            summary = loaded;
            runAfterFlight(() -> {
                if (epoch == loadEpoch && profile != null) {
                    buildRows(profile);
                    notifyRowsChanged();
                }
            });
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
            avatarImage.setImage(avatar, "50_50", headerAvatarDrawable(), avatarUser);
            // The pull-down is only offered once the head has a real picture to grow.
            allowPullingDown = canPullDown();
        } else if (avatarImage.getImageReceiver().getBitmap() == null) {
            // The bytes are not on disk yet: the initials stand in, not an empty spot.
            avatarImage.setImageDrawable(headerAvatarDrawable());
        }
    }

    /** The header's stand-in initials, from the answered profile or the identity it opened for. */
    private AvatarDrawable headerAvatarDrawable() {
        if (avatarPlaceholder == null) {
            avatarPlaceholder = new AvatarDrawable();
        }
        TLRPC.User user = avatarUser;
        if (user == null) {
            user = DiscourseAvatars.INSTANCE.user(username(), displayName(), "",
                    container == null ? "" : container.getBaseUrl());
        }
        avatarPlaceholder.setInfo(0, user);
        return avatarPlaceholder;
    }

    /**
     * The avatar travels from the site's own template the way the topic list's rows fetch theirs,
     * and the expanded header's larger copy travels with it - the site's template takes a size, so
     * the square the pull-down opens draws the same picture at full resolution.
     */
    private void fetchAvatar() {
        final int epoch = loadEpoch;
        String url = DiscourseAvatars.INSTANCE.url(
                profile == null ? null : profile.getAvatarTemplate(),
                container.getBaseUrl(),
                DiscourseAvatars.DEFAULT_SIZE);
        if (url != null && avatarUser != null && !DiscourseAvatars.INSTANCE.isCached(avatarUser)) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, avatarUser, () -> {
                // A picture that lands after the account changed belongs to the previous profile.
                if (epoch != loadEpoch) {
                    return kotlin.Unit.INSTANCE;
                }
                // The header and action rows share the avatar; rebind them after the flight.
                runAfterFlight(() -> {
                    if (epoch != loadEpoch) {
                        return;
                    }
                    bindAvatar();
                    notifyRowsChanged();
                });
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
                if (epoch != loadEpoch) {
                    return kotlin.Unit.INSTANCE;
                }
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
                && (avatarImage.getImageReceiver().hasNotThumb()
                    || transitionAvatar != null && transitionAvatar.hasNotThumb())
                && !AndroidUtilities.isAccessibilityScreenReaderEnabled();
    }

    /**
     * The screen's rows, in the order it shows them: the bio upstream's info block leads with
     * (ProfileActivity's AboutLinkCell slot), then the reader's fields, then their stats. Every
     * field the site sent and left non-empty becomes one row; a field it did not send is not a row
     * of dashes.
     */
    private void buildRows(UserProfile loaded) {
        Row anchor = null;
        int anchorOffset = 0;
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                int position = listView.getChildAdapterPosition(child);
                if (child.getBottom() > 0 && position >= 0 && position < rows.size()) {
                    anchor = rows.get(position);
                    anchorOffset = layoutManager.getDecoratedTop(child) - listView.getPaddingTop();
                    break;
                }
            }
        }
        rows.clear();
        // The site sends the bio as cooked HTML; the head and the bio row both draw text, so the
        // tags come off once, here.
        bioText = loaded.getBio() == null ? "" : org.jsoup.Jsoup.parse(loaded.getBio()).text().trim();
        if (!bioText.isEmpty()) {
            rows.add(Row.bio(bioText));
            rows.add(Row.shadow("资料"));
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
        rows.add(Row.shadow("统计"));
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
        // The tab area is the list's last row, and it is re-appended here whenever the rows are
        // rebuilt (a late summary read rebuilds them): a rows list without it would leave the
        // already-built tab area unreachable below the last stat row.
        if (mediaView != null) {
            rows.add(Row.shadow("动态"));
            mediaRow = rows.size();
            rows.add(Row.media());
        }
        // Statistics insert above the reader. Preserve the same row, not its old adapter position.
        if (anchor != null) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.type == anchor.type && TextUtils.equals(row.label, anchor.label)) {
                    layoutManager.scrollToPositionWithOffset(i, anchorOffset);
                    break;
                }
            }
        }
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
     * write the same way (ProfileActivity.java:6911-6916). Public so a screen test can press the
     * button the view draws.
     */
    public void onActionClicked(int key, float x, float y) {
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
     * 私信 opens this client's own private-message composer, with the profile's own user filled in as
     * its recipient. The action is only offered while the profile says the reader may send one
     * ({@code can_send_private_message_to_user}), and the site still owns the final permission
     * check: a write it refuses comes back as its own message. The composer writes a private message
     * on the same {@code POST /posts.json} route a new topic takes, so nothing of the site's
     * rendered {@code /new-message} page is needed.
     */
    private void openNewMessage() {
        if (getParentActivity() == null || container == null) {
            return;
        }
        presentFragment(NewTopicFragment.privateMessage(username()));
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
                        // updateSelectedMediaTabText (ProfileActivity.java:8048): the counter line
                        // follows the tab the reader is on.
                        updateSelectedMediaTabText();
                        // The pager added the incoming page inside its own touch of the tree; the
                        // row above it (the list) swallows layout requests while it is mid-layout,
                        // so the screen's own root asks for the pass that measures the new page.
                        if (frameLayout != null) {
                            frameLayout.requestLayout();
                        }
                    }

                    @Override
                    public void onPageReselected(int index) {
                        TabPageState state = tabPages[tabAt(index)];
                        if (state != null) {
                            state.listView.smoothScrollToPosition(0);
                        }
                    }
                }, getBaseSimpleGlass().factory, glassEngine::invalidate);
        // The strip's glass samples the pages under it, so the fragment's own simple-glass source
        // records the pager in the media view's coordinate space (SharedMediaLayout's drawListForBlur).
        final BaseFragmentSimpleGlass glass = getBaseSimpleGlass();
        glass.setViews(frameLayout, null, resourceProvider);
        glass.setClipView(mediaView.getPager());
        glass.setCapture(this::captureMedia);
        mediaView.buildTabs();
        // The row the info list holds; settleHeaderInList gives it the list's visible height. The
        // rows are rebuilt here so the row is appended by the one place that knows the order.
        mediaView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (profile != null) {
            buildRows(profile);
        }
        pageState(TAB_TOPICS).loadFirstIfNeeded();
    }

    /** The media pages under the strip, in the fragment's own glass coordinate space. */
    private final PointF mediaCapturePoint = new PointF();

    private void captureMedia(Canvas canvas, RectF position) {
        if (mediaView == null) {
            return;
        }
        final View pager = mediaView.getPager();
        if (!ViewPositionWatcher.computeCoordinatesInParent(pager, frameLayout, mediaCapturePoint)) {
            return;
        }
        canvas.save();
        canvas.clipRect(position);
        canvas.translate(mediaCapturePoint.x, mediaCapturePoint.y);
        pager.draw(canvas);
        canvas.restore();
    }

    private TabPageState pageState(int tab) {
        if (tabPages[tab] == null) {
            tabPages[tab] = new TabPageState(tab);
        }
        return tabPages[tab];
    }

    /**
     * The day/night switch: the surfaces that had their colour baked in when they were built read
     * the theme again (the two lists' page surfaces and the tab strip's band).
     */
    private void applyThemeColors() {
        if (listView != null) {
            listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        }
        for (TabPageState page : tabPages) {
            if (page != null) {
                page.listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            }
        }
        if (mediaView != null) {
            mediaView.updateColors();
        }
        // ProfileActivity.updateColors (11964-11978) re-applies the media header's own pair after
        // the theme's colors changed: the header keeps the state it is in.
        if (mediaCounterTextView != null) {
            mediaCounterTextView.setTextColor(getThemedColor(Theme.key_player_actionBarSubtitle));
        }
        applyMediaHeaderColors();
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
        /**
         * Bumped when the account the root follows changes. A page captures it before a read and
         * drops its own answer if it no longer matches, so a late page of the previous account's
         * stream cannot repopulate the new account's tab.
         */
        int generation;
        final java.util.Set<String> avatarRequests = new java.util.HashSet<>();

        TabPageState(int tab) {
            this.tab = tab;
            Context context = frameLayout.getContext();
            // The pager adds a page with whatever LayoutParams it finds on it (ViewPagerFixed's
            // updateViewForIndex calls a bare addView), and a page without any would measure to
            // zero - the page fills the pager, the way the pages are laid out in the pager's own
            // screens.
            root = new FrameLayout(context);
            root.setLayoutParams(LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context));
            adapter = new MediaRowsAdapter(this);
            listView.setAdapter(adapter);
            listView.setVerticalScrollBarEnabled(false);
            listView.setClipToPadding(false);
            // The same page surface the info list carries: the pager area's own rows scroll over
            // it, and the fragment view behind has no background of its own after the flight.
            listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
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

        /**
         * The account the root follows changed: the page forgets the previous account's rows and
         * stream, and any read still in flight is discarded by its bumped [generation].
         */
        void resetForNewIdentity() {
            generation++;
            items.clear();
            offset = 0;
            loading = false;
            finished = false;
            firstLoaded = false;
            avatarRequests.clear();
            adapter.notifyDataSetChanged();
            updateLoadingState();
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
            // The account this read is for; if it changes before the page lands, the page is dropped.
            final int epoch = generation;
            switch (tab) {
                case TAB_BOOKMARKS: {
                    final int page = offset;
                    ProfileLoader.INSTANCE.loadBookmarks(container, username(), page, loaded -> {
                        if (epoch != generation) {
                            return kotlin.Unit.INSTANCE;
                        }
                        loading = false;
                        offset = page + 1;
                        finished = loaded.size() < BOOKMARK_PAGE_SIZE;
                        items.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    }, error -> {
                        if (epoch != generation) {
                            return kotlin.Unit.INSTANCE;
                        }
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
                        if (epoch != generation) {
                            return kotlin.Unit.INSTANCE;
                        }
                        loading = false;
                        finished = true;
                        items.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        updateLoadingState();
                        return kotlin.Unit.INSTANCE;
                    }, error -> {
                        if (epoch != generation) {
                            return kotlin.Unit.INSTANCE;
                        }
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
                        if (epoch != generation) {
                            return kotlin.Unit.INSTANCE;
                        }
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
                final int epoch = state.generation;
                DiscourseLoader.INSTANCE.cacheAvatar(container, url, author, () -> {
                    // A picture that lands after the account changed belongs to the old tab.
                    if (epoch == state.generation && state.listView.getAdapter() != null) {
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

    /** Upstream also holds profile updates between setAnimationInProgress/onAnimationFinish. */
    private void runAfterFlight(Runnable action) {
        if (openAnimationInProgress || transitionAnimationInProgress) {
            deferredUntilFlightEnd.add(action);
        } else {
            action.run();
        }
    }

    /** The flight landed: hand every deferred answer to the views, in the order it arrived. */
    private void drainDeferredUntilFlightEnd() {
        while (!deferredUntilFlightEnd.isEmpty()) {
            deferredUntilFlightEnd.poll().run();
        }
    }

    /** RecyclerView forbids adapter notifications during its layout pass. */
    private void notifyRowsChanged() {
        if (listView != null && listView.isInLayout()) {
            listView.post(this::flushRowsChanged);
            return;
        }
        flushRowsChanged();
    }

    /** Rebind after a layout pass; row rebuilds preserve their semantic anchor in buildRows. */
    private void flushRowsChanged() {
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        settleHeaderInList();
        listView.getAdapter().notifyDataSetChanged();
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
        // The shimmer starts at the header's rest height, where the first real row lands, the way
        // DialogsActivity keeps its progress view under its own top panel (2093-2095).
        if (progressView != null) {
            ViewGroup.LayoutParams params = progressView.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                int top = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                        + ActionBar.getCurrentActionBarHeight() + getHeaderExtraHeight();
                if (((FrameLayout.LayoutParams) params).topMargin != top) {
                    ((FrameLayout.LayoutParams) params).topMargin = top;
                    progressView.setLayoutParams(params);
                }
            }
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
                    HeaderCell cell = new HeaderCell(context, 18, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_STATE: {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_SHADOW:
                    return new RecyclerListView.Holder(new ShadowSectionCell(context, resourceProvider));
                case TYPE_MEDIA: {
                    // The tab area is a row of the info list the way SharedMediaLayout is a row of
                    // ProfileActivity's own list (ProfileActivity.java:10824, 14360): its height is
                    // set to the list's visible height in settleHeaderInList, so it can pin itself
                    // to the list's top and its pages then scroll inside it.
                    if (mediaView.getLayoutParams() == null) {
                        mediaView.setLayoutParams(new RecyclerView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    mediaView.setTag(RecyclerListView.TAG_NOT_SECTION);
                    return new RecyclerListView.Holder(mediaView);
                }
                default: {
                    TextDetailCell cell = new TextDetailCell(context, 18, resourceProvider, true, true);
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
                cell.setTextAndValue(row.value, row.label, false);
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
            // the pages' own rows, each with their own click handling. The session state's own row
            // is the exception: its tap is the whole action.
            return stateUi != null && SessionUi.hasAction(stateUi);
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
        if (openAnimationInProgress) {
            return;
        }
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
        // Section decoration is spacing, not a pull on the avatar header.
        int top = child == null ? 0 : layoutManager.getDecoratedTop(child);
        int adapterPosition = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
        if (top >= 0 && adapterPosition == 0) {
            newOffset = top;
        }

        // ProfileActivity.java:8024-8036: the tab area is pinned once its row reaches the list's
        // top; that is where the action bar turns into the shared-media header, and where it turns
        // back when the reader scrolls down again.
        boolean mediaHeaderVisible;
        boolean searchVisible = actionBar.isSearchFieldVisible();
        if (mediaRow != -1 && !searchVisible) {
            RecyclerView.ViewHolder mediaHolder = listView.findViewHolderForAdapterPosition(mediaRow);
            mediaHeaderVisible = mediaHolder != null && mediaHolder.itemView.getTop() <= 0;
        } else {
            mediaHeaderVisible = searchVisible;
        }
        setMediaHeaderVisible(mediaHeaderVisible);

        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            // checkListViewScroll (ProfileActivity.java:8041-8044): a profile the reader has
            // scrolled past its header no longer plays the avatar flight on the way out - the
            // avatar's rest is no longer where the flight began.
            if (playProfileAnimation != 0) {
                allowProfileAnimation = extraHeight > getActionsExtraHeight();
            }
            needLayout(true);
        }
    }

    /**
     * setMediaHeaderVisible (ProfileActivity.java:7802-7950): the action bar's shared-media state,
     * entered when the tab area pins under it and left when the reader scrolls back down. Upstream
     * swaps its call/video/edit/other items for the media search and options items; this screen has
     * no such items, so what is ported is the rest of the state: the title and item colours
     * interpolate (the ACTIONBAR_HEADER_PROGRESS property above), the status line hands over to the
     * tab counter, the strip opens (its padding and shadow), and the header's shadow recedes and
     * returns. Upstream's clocks: 150 ms with CubicBezierInterpolator.DEFAULT for the state, 100 ms
     * for the shadow's return.
     */
    private void setMediaHeaderVisible(boolean visible) {
        if (mediaHeaderVisible == visible || mediaView == null || onlineTextView == null || mediaCounterTextView == null) {
            return;
        }
        mediaHeaderVisible = visible;
        if (headerAnimatorSet != null) {
            headerAnimatorSet.cancel();
        }
        if (headerShadowAnimatorSet != null) {
            headerShadowAnimatorSet.cancel();
        }
        updateSelectedMediaTabText();

        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(this, ACTIONBAR_HEADER_PROGRESS, visible ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(onlineTextView, View.ALPHA, visible ? 0.0f : 1.0f));
        animators.add(ObjectAnimator.ofFloat(mediaCounterTextView, View.ALPHA, visible ? 1.0f : 0.0f));
        mediaView.getTabStrip().setOpen(visible);
        if (visible) {
            animators.add(ObjectAnimator.ofFloat(this, HEADER_SHADOW, 0.0f));
        }

        headerAnimatorSet = new AnimatorSet();
        headerAnimatorSet.playTogether(animators);
        headerAnimatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        headerAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerAnimatorSet != null && !mediaHeaderVisible) {
                    // ProfileActivity.java:7905-7920: the shadow comes back on its own, over 100 ms.
                    headerShadowAnimatorSet = new AnimatorSet();
                    headerShadowAnimatorSet.playTogether(
                            ObjectAnimator.ofFloat(ProfileFragment.this, HEADER_SHADOW, 1.0f));
                    headerShadowAnimatorSet.setDuration(100);
                    headerShadowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            headerShadowAnimatorSet = null;
                        }
                    });
                    headerShadowAnimatorSet.start();
                }
                headerAnimatorSet = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                headerAnimatorSet = null;
            }
        });
        headerAnimatorSet.setDuration(150);
        headerAnimatorSet.start();
        // ProfileActivity.java:7933: entering or leaving the media header changes what is behind the
        // status line, so the shell recomputes the icon contrast.
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
    }

    /**
     * The ACTIONBAR_HEADER_PROGRESS body (ProfileActivity.java:7713-7800) and what upstream's
     * updateColors re-applies after a theme switch (11964-11978): the title and the bar's items
     * blend from the profile's own pair to the player/action-mode pair. The verified/premium
     * pieces of upstream's body have no linux.do counterpart.
     */
    private void applyMediaHeaderColors() {
        if (nameTextView == null || actionBar == null) {
            return;
        }
        final int c = AndroidUtilities.getOffsetColor(
                getThemedColor(Theme.key_profile_title),
                getThemedColor(Theme.key_player_actionBarTitle),
                mediaHeaderAnimationProgress, 1.0f);
        nameTextView.setTextColor(ColorUtils.blendARGB(c, Color.WHITE, currentExpandAnimatorValue));

        actionBar.setItemsColor(AndroidUtilities.getOffsetColor(
                getThemedColor(Theme.key_actionBarDefaultIcon),
                getThemedColor(Theme.key_actionBarActionModeDefaultIcon),
                mediaHeaderAnimationProgress, 1.0f), false);
        actionBar.setItemsBackgroundColor(AndroidUtilities.getOffsetColor(
                getThemedColor(Theme.key_avatar_actionBarSelectorBlue),
                getThemedColor(Theme.key_actionBarActionModeDefaultSelector),
                mediaHeaderAnimationProgress, 1.0f), false);
        if (topView != null) {
            topView.invalidate();
        }
    }

    /**
     * ProfileActivity.isLightStatusBar (15779-15793), for the two states this screen has. The
     * expanded photo draws under the status line, so its icons go light there; while the tab area is
     * pinned the bar behind them is the window background (the media header), not the profile's own
     * action-bar colour; otherwise the profile's own colour decides, with the action-mode colour
     * while a selection is up. The peer-colour branch upstream has between those two has no
     * counterpart here. The shell recomputes from this answer when the state changes (see
     * {@code startAvatarExpand}, {@code startAvatarCollapse}, {@code setMediaHeaderVisible}).
     */
    @Override
    public boolean isLightStatusBar() {
        if (isPulledDown) {
            return false;
        }
        int color;
        if (actionBar != null && actionBar.isActionModeShowed()) {
            color = getThemedColor(Theme.key_actionBarActionModeDefault);
        } else if (mediaHeaderVisible) {
            color = getThemedColor(Theme.key_windowBackgroundWhite);
        } else {
            color = getThemedColor(Theme.key_actionBarDefault);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    /**
     * updateSelectedMediaTabText (ProfileActivity.java:8048-8130): the line under the name while
     * the media header is showing. Upstream writes the selected tab's count ("Photos, Videos");
     * this screen's tabs are the site's activity kinds, so the counter carries the tab's own name.
     */
    private void updateSelectedMediaTabText() {
        if (mediaView == null || mediaCounterTextView == null) {
            return;
        }
        final int tab = mediaView.getCurrentTab();
        if (tab >= 0) {
            mediaCounterTextView.setText(tabTitle(tabAt(tab)));
        }
    }

    /** needLayout (ProfileActivity.java:8368); the open-animation branch and the write button are left out. */
    private void needLayout(boolean animated) {
        final int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        final float diff = calculateHeaderExtraDiff();
        boolean textMeasured = false;

        FrameLayout.LayoutParams layoutParams;
        if (listView != null && !openAnimationInProgress) {
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

        // needLayout (ProfileActivity.java:8389-8392): while the profile is on its way out, the
        // header follows the recorded backward path instead of the scroll-driven one.
        if (avatarContainer != null && !isFragmentOpened && openAnimationInProgress) {
            backwardAnimationLayout();
            return;
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
                        syncMediaCounterFromOnline();
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
                        syncMediaCounterFromOnline();
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
                if (!(openAnimationInProgress && avatarFlight)) {
                    avatarContainer.setScaleX(avatarScale);
                    avatarContainer.setScaleY(avatarScale);
                    fixAvatarImageInCenter();
                    avatarContainer.setTranslationY((float) Math.ceil(avatarY));
                }
                // needLayout (ProfileActivity.java:8752-8755): the title's ride down from the
                // action bar follows the avatar's own growth while the flight is running.
                final float avatarBottom = (float) Math.floor(endNameY) + (avatarContainer.getHeight() * avatarContainer.getScaleY() + dpf2(8)) * (openAnimationInProgress ? avatarAnimationProgress : diff);
                nameY = avatarBottom + dp(1.3f) + dp(7) * diff;
                onlineY = avatarBottom + dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff;
                // needLayout (ProfileActivity.java:8756): while the flight is running the title
                // starts where the capsule's own title was (its left edge is the avatar's + 48dp).
                final float minimizedX = openAnimationInProgress && avatarFlight
                        ? prevAvatarTranslation - dp(109) + dp(48)
                        : -dpf2(42 + 4);

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

                if (expandAnimator == null || !expandAnimator.isRunning()) {
                    nameTextView.setTranslationX(nameX);
                    nameTextView.setTranslationY(nameY);
                    onlineTextView.setTranslationX(onlineX);
                    onlineTextView.setTranslationY(onlineY);
                    syncMediaCounterFromOnline();
                }
                updateTextLayoutBasedOnTranslation();
            }
            if (openAnimationInProgress && avatarFlight) {
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
        // needLayoutText (ProfileActivity.java:8958-8961): during the flight the title is laid
        // out as wide as the action-bar copy upstream keeps behind it (109dp of chrome), not as
        // the narrow collapsed bar.
        int buttonsWidth = AndroidUtilities.dp((openAnimationInProgress ? 109 : 47) + 8 + 40);
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
        // ProfileActivity.java:8462-8463: the expanded photo changes the status-bar contrast, so the
        // shell is asked to recompute the icons over the now-full-screen picture.
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
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
            listView.smoothScrollBy(0, layoutManager.getDecoratedTop(view) - listView.getMeasuredWidth() - getActionsExtraHeight() + actionBarHeight,
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
        // ProfileActivity.java:8543-8545: handing the header back to its small avatar hands the
        // status-bar contrast back with it.
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
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
        // ProfileActivity.java:6362-6363: the counter rides the status line's own slot.
        mediaCounterTextView.setTranslationX(onlineTextView.getTranslationX());
        mediaCounterTextView.setTranslationY(onlineTextView.getTranslationY());

        // Over the picture the two lines go white, and the back arrow with them, the way upstream
        // fades them on the big picture (ProfileActivity.java:6379-6391); with the media header up
        // the title's base comes from its own blend (upstream 11978).
        nameTextView.setTextColor(ColorUtils.blendARGB(
                ColorUtils.blendARGB(
                        getThemedColor(Theme.key_profile_title),
                        getThemedColor(Theme.key_player_actionBarTitle),
                        mediaHeaderAnimationProgress),
                Color.WHITE, value));
        onlineTextView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_actionBarDefaultSubtitle), 0xB3FFFFFF, value));
        actionBar.setItemsColor(ColorUtils.blendARGB(
                AndroidUtilities.getOffsetColor(
                        getThemedColor(Theme.key_actionBarDefaultIcon),
                        getThemedColor(Theme.key_actionBarActionModeDefaultIcon),
                        mediaHeaderAnimationProgress, 1.0f),
                Color.WHITE, value), false);

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

    /** The counter rides the status line's own slot (ProfileActivity.java:8272-8278). */
    private void syncMediaCounterFromOnline() {
        if (mediaCounterTextView == null || onlineTextView == null) {
            return;
        }
        mediaCounterTextView.setTranslationX(onlineTextView.getTranslationX());
        mediaCounterTextView.setTranslationY(onlineTextView.getTranslationY());
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
    // The opening animation. The topic header's tap is upstream's ordinary header transition:
    // ProfileActivity.java:9895-10265 sets the clock by type (`playProfileAnimation == 2 ? 250 : 180`
    // and `CubicBezierInterpolator.DEFAULT` only for 2), so this runs at the ordinary 180 ms with a
    // DecelerateInterpolator, keeps the profile's own palette (never the expanded photo's white
    // labels or its avatar-average backdrop) and never expands the action row - all of that is the
    // explicit pull's setAvatarExpandProgress. The host extra is the flight itself: the avatar still
    // travels from the drawn capsule (ProfileActivity.java:8623-8690's layout) to the collapsed
    // header's round 96 dp rest, and upstream's 50 ms start delay is kept
    // (`AndroidUtilities.runOnUIThread(animatorSet::start, 50)`, ProfileActivity.java:10264).
    // ---------------------------------------------------------------------------------------------

    @Override
    public AnimatorSet onCustomTransitionAnimation(final boolean isOpen, final Runnable callback) {
        if (getArguments() != null && getArguments().getBoolean("hasMainTabs", false)) {
            return null;
        }
        if (!(playProfileAnimation != 0 && allowProfileAnimation && (!isPulledDown || avatarFlight))) {
            return null;
        }

        // ProfileActivity.java:9931-9942: the screen below keeps its action bar, but the bar's
        // children - the chat header's avatar and title, here the topic's capsule - are not drawn
        // while the profile's avatar is on its way: the one flying copy is the only one seen.
        previousTransitionActionBar = null;
        if (getParentLayout() != null && getParentLayout().getFragmentStack() != null
                && getParentLayout().getFragmentStack().size() >= 2) {
            BaseFragment previous = getParentLayout().getFragmentStack()
                    .get(getParentLayout().getFragmentStack().size() - 2);
            if (previous instanceof TopicFragment) {
                previousTransitionActionBar = previous.getActionBar();
            }
        }
        if (previousTransitionActionBar != null) {
            previousTransitionActionBar.setSkipDrawChild(true);
        }

        final AnimatorSet animatorSet = new AnimatorSet();
        // ProfileActivity.java:9952: `playProfileAnimation == 2 ? 250 : 180`; the header tap is the
        // ordinary transition.
        animatorSet.setDuration(180);
        if (listView != null) {
            listView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        if (isOpen) {
            setAvatarAnimationProgress(0);
            if (frameLayout != null) {
                frameLayout.setBackgroundColor(0);
            }
            // Upstream's type-1 isOpen leaves the bar's colours alone (only its type-2 branch sets
            // the white labels, the avatar's average colour and the white item selector); this
            // screen's palette is already the profile's, so nothing here has to be undone at the end.
            nameTextView.setAlpha(0.0f);
            onlineTextView.setAlpha(0.0f);

            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(this, "avatarAnimationProgress", 0.0f, 1.0f));
            avatarImage.setCrossfadeProgress(1f);
            animators.add(ObjectAnimator.ofFloat(avatarImage, ProfileActivity.AvatarImageView.CROSSFADE_PROGRESS, 0f));
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
            animators.add(ObjectAnimator.ofFloat(avatarImage, ProfileActivity.AvatarImageView.CROSSFADE_PROGRESS, 1f));
            // ProfileActivity.java:10163-10166: the copy that travels is the one the reader sees;
            // upstream fades its header copy ([1]) out while its bar copy ([0]) fades in. This
            // screen keeps one copy, and it is the header's: it leaves with the flight (the topic's
            // own title comes back when the capsule's bar is drawn again).
            animators.add(ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(onlineTextView, View.ALPHA, 0.0f));
            if (actionsView != null) {
                animators.add(ObjectAnimator.ofFloat(actionsView, View.ALPHA, 0.0f));
            }
            animatorSet.playTogether(animators);
        }

        // ProfileActivity.java:10262: `playProfileAnimation == 2 ? CubicBezierInterpolator.DEFAULT :
        // new DecelerateInterpolator()`.
        animatorSet.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // ProfileActivity.java:10227-10236: the hardware layer and the hidden header go
                // back as the flight lands, in both directions.
                if (listView != null) {
                    listView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                if (previousTransitionActionBar != null) {
                    previousTransitionActionBar.setSkipDrawChild(false);
                    previousTransitionActionBar = null;
                }
                callback.run();
            }
        });

        AndroidUtilities.runOnUIThread(animatorSet::start, 50);
        return animatorSet;
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isFragmentOpened = isOpen;
        if (isOpen && actionsView != null) {
            actionsView.startAnimatedActions();
        }
        if (isOpen || backward) {
            // This fragment's own enter/exit transition: upstream holds the profile's own view
            // updates for its whole length, animated or not (ProfileActivity.java:9727-9778).
            transitionAnimationInProgress = true;
        }
        if ((!isOpen && backward || isOpen && !backward)
                && playProfileAnimation != 0 && allowProfileAnimation && (!isPulledDown || avatarFlight)) {
            openAnimationInProgress = true;
            if (!isOpen) {
                captureBackwardInitialValues();
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        // The animator's own end listener restores these; a cancelled flight ends here instead
        // (the layout calls the callback after a cancel too), so the previous screen is whole.
        if (listView != null) {
            listView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        if (previousTransitionActionBar != null) {
            previousTransitionActionBar.setSkipDrawChild(false);
            previousTransitionActionBar = null;
        }
        openAnimationInProgress = false;
        transitionAnimationInProgress = false;
        drainDeferredUntilFlightEnd();
        if (isOpen && !backward && playProfileAnimation != 0) {
            // The flight lands in the collapsed header's own layout - the round 96 dp avatar under
            // the action bar - so an ordinary tap opens a normal profile and the photo only opens
            // further on a deliberate pull. The pull-down's fraction is handed back to 0 so a later
            // expand starts from rest.
            isPulledDown = false;
            if (actionsView != null) {
                actionsView.setParentExpanded(0f);
            }
            currentExpanAnimatorFracture = 0f;
            currentExpandAnimatorValue = 0f;
            expandAnimatorValues[0] = 0f;
            expandAnimatorValues[1] = 1f;
            if (avatarImage != null) {
                avatarImage.setRoundRadiusForExpand(getSmallAvatarRoundRadius());
            }
            restoreTransitionColors();
            checkListViewScroll();
            needLayout(true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
        } else if (!isOpen && listView != null) {
            listView.setAlpha(1f);
            if (progressView != null) {
                progressView.setAlpha(1f);
                progressView.setTranslationX(0f);
            }
        }
    }

    /** setAvatarAnimationProgress (ProfileActivity.java:9797-9835) */
    @androidx.annotation.Keep
    public void setAvatarAnimationProgress(float progress) {
        avatarAnimationProgress = currentExpandAnimatorValue = progress;
        if (listView != null) {
            listView.setAlpha(progress);
            listView.setTranslationX(dp(48) - dp(48) * progress);
        }
        // The shimmer is the list's sibling, not a row of it: it follows the same fade and slide.
        if (progressView != null) {
            progressView.setAlpha(progress);
            progressView.setTranslationX(dp(48) - dp(48) * progress);
        }
        // Upstream's ordinary transition (its `playProfileAnimation == 2` blocks are the expanded
        // photo's) blends the backdrop from the screen it is leaving - transparent, so the screen
        // behind shows through - to the profile's own surface, and leaves the title, the status and
        // the action row on the profile's palette. Nothing here has to be undone when the flight
        // lands; the white labels, the avatar's average colour and the expanded action row belong
        // to the explicit pull (setAvatarExpandProgress).
        if (topView != null) {
            int from = androidx.core.graphics.ColorUtils.setAlphaComponent(
                    getThemedColor(Theme.key_actionBarDefault), 0);
            topView.setTransitionColor(androidx.core.graphics.ColorUtils.blendARGB(
                    from, getThemedColor(Theme.key_windowBackgroundGray), progress));
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
        if (progressView != null) {
            progressView.setAlpha(1f);
            progressView.setTranslationX(0f);
        }
    }

    /**
     * ProfileActivity.java:8623-8690: the capsule's photo along the flight path, seeded with this
     * host's actual capsule geometry (X, Y and scaled source size, {@link #forUserFromAvatar}).
     * Upstream's type 2 ends on the full-width expanded photo; this screen lands in the collapsed
     * header's own rest instead - the round 96 dp avatar under the action bar - so an ordinary tap
     * opens a normal profile and the photo only opens further on a deliberate pull. The avatar's
     * round corners are kept, not opened.
     */
    private void openAnimationAvatarLayout(float newTop) {
        float avX = prevAvatarTranslation;
        float avY = prevAvatarY;

        avatarScale = lerp(prevAvatarSize / AndroidUtilities.density, 96, avatarAnimationProgress) / 100f;
        pullUpProgress = 0.0f;

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        final int prevWidth = params.width;
        final int prevHeight = params.height;
        params.width = dp(100);
        params.height = dp(100);
        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);

        // The rest the collapsed header's own needLayout puts the avatar at (its `startY` at
        // diff == 1): the 100 dp frame below the action bar, centered. Upstream's end state is the
        // full-width square (translation (0, 0), scale 138/100); this screen's is that rest.
        final int statusBar = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
        final float restX = listView.getMeasuredWidth() / 2f - (params.width * avatarScale * 0.5f);
        final float restY = (float) Math.ceil(statusBar + ActionBar.getCurrentActionBarHeight()
                - dp(21) + actionBar.getTranslationY());
        avatarContainer.setTranslationX(AndroidUtilities.lerp(avX, restX, avatarAnimationProgress));
        avatarContainer.setTranslationY(AndroidUtilities.lerp((float) Math.ceil(avY), restY, avatarAnimationProgress));
        // The frame is the collapsed header's own 100 dp one for the whole flight - only the
        // transform moves - so a frame that leaves its bounds unchanged must not ask for another
        // layout pass (upstream requests one there because its container's size really grows).
        if (params.width != prevWidth || params.height != prevHeight) {
            avatarContainer.requestLayout();
        }

        avatarImage.setRoundRadiusForExpand(getSmallAvatarRoundRadius());
        avatarImage.setBlurRadiusProgressForExpand(avatarAnimationProgress, avatarScale, false);

        // needLayout (ProfileActivity.java:8752-8755): the title starts at the capsule's own title
        // and rides the avatar's growing bottom edge down to its rest place below the picture.
        final float diff = calculateHeaderExtraDiff();
        final float endNameY = statusBar
                + ActionBar.getCurrentActionBarHeight() / 2f * (1f + diff)
                - dp(21)
                + actionBar.getTranslationY();
        final float avatarBottom = (float) Math.floor(endNameY)
                + (avatarContainer.getHeight() * avatarContainer.getScaleY() + dpf2(8)) * avatarAnimationProgress;
        final float nameX = this.nameX;
        final float onlineX = this.onlineX;
        nameTextView.setTranslationX(nameX);
        nameTextView.setTranslationY(avatarBottom + dp(1.3f) + dp(7) * diff);
        onlineTextView.setTranslationX(onlineX);
        onlineTextView.setTranslationY(avatarBottom + dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff);
        syncMediaCounterFromOnline();
    }

    /**
     * backwardAnimationLayout (ProfileActivity.java:8193-8290): the reverse of the flight, driven
     * by the values captured when the close began. The avatar travels from wherever the header
     * rested (scale, X, Y) back to the capsule's 42 dp spot, and the title and status slide back
     * toward the capsule's own title line, losing their scale on the way. Upstream's Telegram-only
     * overlays (stories, gifts, the rating) and its second title copy are left out.
     */
    private void backwardAnimationLayout() {
        if (expandAnimator != null && expandAnimator.isRunning()) {
            expandAnimator.cancel();
        }
        // The animator's progress is the diff upstream reads off the ramping extraHeight
        // (`extraHeight = initialAnimationExtraHeight * progress`, ProfileActivity.java:9853).
        final float backwardDiff = Utilities.clamp01(avatarAnimationProgress);
        final float backwardDiffHalf = (backwardDiff - 0.5f) / 0.5f;
        final int statusBar = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;

        avatarScale = lerp(prevAvatarSize / dp(100f), backwardInitialValues[0], backwardDiff);
        avatarX = lerp(prevAvatarTranslation, backwardInitialValues[1], backwardDiff);
        avatarY = lerp(prevAvatarY, backwardInitialValues[2], backwardDiff);
        pullUpProgress = lerp(backwardInitialValues[11], 0f, backwardDiff);
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        final int prevWidth = params.width;
        final int prevHeight = params.height;
        params.width = (int) lerp(dp(100), backwardInitialValues[14], backwardDiff);
        params.height = (int) lerp(dp(100), backwardInitialValues[15], backwardDiff);
        // The reverse of the flight's own frame rule (see openAnimationAvatarLayout): an ordinary
        // close leaves the avatar's 100 dp frame unchanged, so a frame that does not move its
        // bounds must not ask for another layout pass.
        if (params.width != prevWidth || params.height != prevHeight) {
            avatarContainer.requestLayout();
        }
        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarContainer.setTranslationX(avatarX);
        avatarContainer.setTranslationY(avatarY);
        avatarImage.setAlpha(1f);
        avatarContainer.setAlpha(1f);
        // The corners travel from the radius the close actually started at - the header's round
        // one for an ordinary close, the square a deliberate pull left it - back to the capsule's.
        avatarImage.setRoundRadiusForExpand((int) lerp(
                getSmallAvatarRoundRadius(), backwardInitialValues[16], backwardDiff));
        avatarImage.setBlurRadiusProgressForExpand(backwardDiff, avatarScale, true);
        if (expandedAvatarBound) {
            avatarImage.setForegroundAlpha(backwardDiff);
        }

        final float minEndNameY = statusBar
                + ActionBar.getCurrentActionBarHeight() / 2f
                - 21 * AndroidUtilities.density
                + actionBar.getTranslationY();
        final float minNameY = (float) Math.floor(minEndNameY) + dp(1.3f);
        final float minOnlineY = minNameY + dpf2(22.7f);

        float nameScale = lerp(1f, backwardInitialValues[7], backwardDiff);
        nameY = AndroidUtilities.lerp(minNameY, backwardInitialValues[8], backwardDiff);
        onlineY = AndroidUtilities.lerp(minOnlineY, backwardInitialValues[9], backwardDiff);
        float nameX = lerp(prevAvatarTranslation - dp(109) + dp(48), backwardInitialValues[12], backwardDiff);
        float onlineX = lerp(prevAvatarTranslation - dp(109) + dp(48), backwardInitialValues[13], backwardDiff);

        nameTextView.setTranslationX(nameX);
        nameTextView.setTranslationY(nameY);
        onlineTextView.setTranslationX(onlineX);
        onlineTextView.setTranslationY(onlineY);
        mediaCounterTextView.setTranslationX(onlineX);
        mediaCounterTextView.setTranslationY(onlineY);
        nameTextView.setScaleX(nameScale);
        nameTextView.setScaleY(nameScale);
        this.nameX = nameX;
        this.onlineX = onlineX;

        final int newTop = statusBar + ActionBar.getCurrentActionBarHeight();
        updateExtraViews(newTop);
        needLayoutText(backwardDiff, (int) backwardInitialValues[10], true);

        if (actionsView != null) {
            actionsView.setAlpha(AndroidUtilities.lerp(0, backwardInitialValues[3], backwardDiffHalf));
        }
    }

    private void captureBackwardInitialValues() {
        backwardTransitionFromExtraHeight = extraHeight;
        backwardInitialValues[0] = avatarScale;
        backwardInitialValues[1] = avatarContainer == null ? 0f : avatarContainer.getTranslationX();
        backwardInitialValues[2] = avatarContainer == null ? 0f : avatarContainer.getTranslationY();
        backwardInitialValues[3] = actionsView == null ? 1f : actionsView.getAlpha();
        backwardInitialValues[14] = avatarContainer.getLayoutParams().width;
        backwardInitialValues[15] = avatarContainer.getLayoutParams().height;
        backwardInitialValues[16] = avatarImage == null
                ? getSmallAvatarRoundRadius() : avatarImage.getRoundRadiusForExpand();
        // ProfileActivity.java:8168-8174: the title's scale, its Y and the online line's Y, the
        // width it was laid out with, where the pull-up left the gooey, and the X both titles
        // were translated to - the values backwardAnimationLayout() lerps away from.
        if (nameTextView == null || onlineTextView == null) {
            return;
        }
        backwardInitialValues[7] = nameTextView.getScaleX();
        backwardInitialValues[8] = nameTextView.getTranslationY();
        backwardInitialValues[9] = onlineTextView.getTranslationY();
        backwardInitialValues[10] = nameTextView.getLayoutParams() == null
                ? 0f : nameTextView.getLayoutParams().width;
        backwardInitialValues[11] = pullUpProgress;
        backwardInitialValues[12] = nameTextView.getTranslationX();
        backwardInitialValues[13] = onlineTextView.getTranslationX();
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
        if (openAnimationInProgress && avatarFlight && avatarContainer != null) {
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
    public boolean hasAction(int key) {
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
     * background over the rows scrolling under the header. The ordinary opening blends into
     * the theme surface; the expanded photo supplies its own backdrop colour on a pull.
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
            // Upstream also updates action colours while painting its header, including theme animation.
            final int actionColor = getThemedColor(Theme.key_windowBackgroundWhite);
            actionsView.setActionsColor(actionColor, false);
            if (buttonsGlass != null) {
                buttonsGlass.setButtonColor(actionColor, false);
            }
            final int height = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            final float v = height + extraHeight;
            final int y1 = (int) v;
            if (y1 != 0) {
                paint.setColor(transitionColor != 0 ? transitionColor : getThemedColor(Theme.key_windowBackgroundGray));
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
            // Root tabs can lay out again without a scroll or another onResume/pre-draw hook.
            // Header translations depend on the children's measured bounds, not just extraHeight.
            if (!openAnimationInProgress) {
                needLayout(false);
            }
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
            if (target == listView && pageList != null && mediaView != null && mediaView.getTop() == 0) {
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
            if (target != listView || mediaView == null || listView == null) {
                return;
            }
            int top = mediaView.getTop();
            if (dy < 0 && top <= 0) {
                RecyclerListView pageList = currentPageList();
                if (pageList != null) {
                    LinearLayoutManager manager = (LinearLayoutManager) pageList.getLayoutManager();
                    int pos = manager == null ? RecyclerView.NO_POSITION : manager.findFirstVisibleItemPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        View holder = manager.findViewByPosition(pos);
                        int childTop = holder != null ? holder.getTop() : -1;
                        int paddingTop = pageList.getPaddingTop();
                        if (childTop != paddingTop || pos != 0) {
                            consumed[1] = pos != 0 ? dy : Math.max(dy, childTop - paddingTop);
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
            return new Row(TYPE_BIO, "关于我", text);
        }

        static Row shadow(String section) {
            return new Row(TYPE_SHADOW, section, null);
        }

        static Row media() {
            return new Row(TYPE_MEDIA, null, null);
        }

        static Row state(CharSequence text) {
            return new Row(TYPE_STATE, null, text == null ? "" : text.toString());
        }
    }
}
