package io.github.baba983.nativedo.app;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.ViewPagerActivity;

import java.util.ArrayList;

import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.messagebus.MessageBusHandle;
import io.github.baba983.nativedo.core.messagebus.MessageBusSession;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.core.session.SessionSnapshot;
import io.github.baba983.nativedo.messagecenter.MessageCenterFragment;
import io.github.baba983.nativedo.settings.SettingsFragment;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.TopicListFragment;

/**
 * NativeDo's tab shell, shaped like upstream's {@code MainTabsActivity}: the roots live in a
 * {@link ViewPagerActivity}, upstream's {@link MainTabsLayout} draws the glass strip over them, a
 * tap selects a page and a swipe moves between them. Upstream's class cannot be reused directly
 * because its {@code createBaseFragmentAt} builds Telegram's own contacts/calls/profile screens;
 * this is the host wiring around the same pager, strip and glass background.
 *
 * <p>The strip belongs to the pages' screen, not to the Activity: a screen pushed from a page (a
 * topic, the search field) is presented over this fragment and covers the strip, and the strip is
 * only gone from the window when the reader has left the roots entirely — at the login page.</p>
 */
public class NativeDoMainTabsActivity extends ViewPagerActivity {

    public static final int TABS_COUNT = 4;
    private static final int POSITION_TOPICS = 0;
    private static final int POSITION_NOTIFICATIONS = 1;
    private static final int POSITION_SETTINGS = 2;
    private static final int POSITION_PROFILE = 3;

    /** The pages get their tab inset by name, the way upstream passes {@code hasMainTabs}. */
    public static Bundle mainTabsArgs() {
        Bundle args = new Bundle();
        args.putBoolean("hasMainTabs", true);
        return args;
    }

    private MainTabsLayout tabsView;
    private FrameLayout tabsViewWrapper;
    private BlurredBackgroundDrawable tabsViewBackground;
    private GlassTabView[] tabs;
    private View fadeView;
    /** The notifications badge's feed from the message bus, detached when the host is destroyed. */
    private MessageBusHandle badgeObserver;
    /** The fourth tab's picture, re-bound from the session so it follows the confirmed reader. */
    private BackupImageView profileAvatarView;
    /** The account the picture is currently bound to; a late download for another must not land. */
    private String profileAvatarIdentity;
    /** A neutral circle for the tab while no confirmed reader names a picture. */
    private AvatarDrawable placeholderAvatar;
    /** The session observer that keeps the profile tab's picture current; dropped with the views. */
    private kotlinx.coroutines.Job profileObserver;
    /** The home screen's search is up: the strip steps away for its pager. */
    private boolean searchOpen;
    /** Upstream's browser owns the bottom bar while it has minimized pages. */
    private boolean browserTabsExpanded;
    /** Whether the strip went away because of the search, so it comes back the same way. */
    private boolean stripHiddenForSearch;
    /** The strip's current shown state, so a hide request that changes nothing is not re-animated. */
    private boolean stripVisible = true;

    private int insetLeft;
    private int insetRight;
    private int navigationBarHeight;
    /** The page the shell first builds on; the first tab unless a restore named another. */
    private final int startTabPosition;

    private final GlassSourceColor iBlur3SourceColor = new GlassSourceColor();
    private final GlassSourceRenderNode iBlur3SourceTabGlass;

    public NativeDoMainTabsActivity() {
        this(POSITION_TOPICS);
    }

    /**
     * The tab shell restored to one page: the shell's own {@code ViewPagerActivity} reads
     * {@link #getStartPosition()} when it first builds, so the page the reader was on comes back
     * without a second scroll. The first tab is the default.
     */
    public NativeDoMainTabsActivity(int startTabPosition) {
        super();
        this.startTabPosition = startTabPosition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            iBlur3SourceTabGlass = new GlassSourceRenderNode(iBlur3SourceColor);
            // The captured pages are the glass's source, so the frosted look is this blur.
            iBlur3SourceTabGlass.setBlur(dp(8));
        } else {
            iBlur3SourceTabGlass = null;
        }
    }

    /** The tab shell opened on {@code position}, for a shell restoring the reader's page. */
    public static NativeDoMainTabsActivity forTab(int position) {
        return new NativeDoMainTabsActivity(position);
    }

    /** The page the strip is on now, so the shell can save it and restore it on recreation. */
    public int getSelectedTabPosition() {
        return viewPager != null ? viewPager.getCurrentPosition() : startTabPosition;
    }

    /**
     * The root's own view: it paints the strips the pager leaves for a cutout, and it records the
     * pages into the render node the glass draws from. Upstream's host composites per-screen glass
     * sources here; this client's pages have no source of their own, so the recording draws the
     * pager itself — the glass then samples what the reader actually sees behind it.
     */
    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final int color = getEstBackgroundColor();
                if (insetLeft != 0) {
                    canvas.drawRect(0, 0, insetLeft, getHeight(), Theme.fillingPaint(color));
                }
                if (insetRight != 0) {
                    canvas.drawRect(getWidth() - insetRight, 0, getWidth(), getHeight(), Theme.fillingPaint(color));
                }
                if (canvas.isHardwareAccelerated() && iBlur3SourceTabGlass != null) {
                    final Canvas recording = iBlur3SourceTabGlass.beginRecording(getWidth(), getHeight());
                    recording.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    if (viewPager != null) {
                        recording.translate(viewPager.getLeft(), viewPager.getTop());
                        viewPager.draw(recording);
                    }
                    iBlur3SourceTabGlass.endRecording();
                    iBlur3SourceTabGlass.invalidateDisplayListForDrawables();
                }
                super.dispatchDraw(canvas);
            }
        };
    }

    @Override
    public View createView(Context context) {
        super.createView(context);

        tabsView = new MainTabsLayout(context, resourceProvider);
        tabsView.setClipChildren(false);
        final int padding = dp(DialogsActivity.MAIN_TABS_MARGIN + 4);
        tabsView.setPadding(padding, padding, padding, padding);
        tabsView.setMaxWidth(dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));

        tabs = new GlassTabView[TABS_COUNT];
        tabs[POSITION_TOPICS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, org.telegram.messenger.R.string.MainTabsChats);
        tabs[POSITION_NOTIFICATIONS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.REPLIES, org.telegram.messenger.R.string.MainTabsContacts);
        // The second entry is the message center now, holding 通知/私信/聊天 under it - not the
        // notifications screen alone - so it is labelled 消息 instead of Telegram's own "Contacts".
        tabs[POSITION_NOTIFICATIONS].setText("消息");
        tabs[POSITION_SETTINGS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, org.telegram.messenger.R.string.Settings);
        tabs[POSITION_PROFILE] = createProfileTab(context);
        final String[] descriptions = {"话题", "消息", "设置", "我的"};
        for (int index = 0; index < tabs.length; index++) {
            final GlassTabView view = tabs[index];
            final int position = index;
            view.setContentDescription(descriptions[index]);
            view.setOnClickListener(v -> {
                if (viewPager.isManualScrolling() || viewPager.isTouch()) {
                    return;
                }
                if (viewPager.getCurrentPosition() == position) {
                    final BaseFragment fragment = getCurrentVisibleFragment();
                    if (fragment instanceof TabPage) {
                        ((TabPage) fragment).onParentScrollToTop();
                    }
                    return;
                }
                selectTab(position, true);
                viewPager.scrollToPosition(position);
            });
            tabsView.addView(view);
            tabsView.setViewVisible(view, true, false);
        }
        selectTab(viewPager.getCurrentPosition(), false);
        // A theme switch clears this fragment's views without destroying the fragment, so a second
        // createView has to let go of the previous view's badge listener; otherwise every switch
        // leaves a collector drawing into a dead tab.
        if (badgeObserver != null) {
            badgeObserver.close();
            badgeObserver = null;
        }
        watchNotificationBadge();
        observeProfile();

        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));

        final ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);

        final BlurredBackgroundDrawableViewFactory glassFactory = new BlurredBackgroundDrawableViewFactory(
                iBlur3SourceTabGlass != null ? iBlur3SourceTabGlass : iBlur3SourceColor);
        glassFactory.setSourceRootView(viewPositionWatcher, contentView);
        glassFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));

        tabsViewBackground = glassFactory.create(tabsView, BlurredBackgroundProviderImpl.mainTabs(resourceProvider));
        tabsViewBackground.setRadius(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsViewBackground.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        tabsView.setBackground(tabsViewBackground);

        final BlurredBackgroundDrawableViewFactory fadeFactory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        fadeFactory.setSourceRootView(viewPositionWatcher, contentView);

        fadeView = new View(context);
        final BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(fadeFactory.create(fadeView, null));
        fadeDrawable.setFadeHeight(dp(60), true);
        fadeView.setBackground(fadeDrawable);
        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsViewWrapper = new FrameLayout(context);
        tabsViewWrapper.setOnClickListener(v -> {});
        tabsViewWrapper.addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        tabsViewWrapper.setClipToPadding(false);
        contentView.addView(tabsViewWrapper, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        return contentView;
    }

    /**
     * The home screen's search opening and closing. Upstream's own main tabs disappear while a
     * search is up (DialogsActivity.checkUi_mainTabsVisible -> MainTabsActivity.setTabsVisible),
     * and this is that answer for this strip: {@link #showTabs} takes it away in
     * MainTabsActivity's own way (380ms EASE_OUT_QUINT over dp(40), alpha and 0.85 scale).
     */
    public void setSearchOpen(boolean open) {
        if (searchOpen == open) {
            return;
        }
        searchOpen = open;
        updateTabsVisibility();
    }

    /**
     * Upstream's in-app browser has minimized pages: its strip owns the bottom bar, so this
     * shell's strip steps aside - the same yield the browser host asked of the activity-owned
     * strip before (NativeDoActivity's updateTabsVisibility).
     */
    public void setBrowserTabsExpanded(boolean expanded) {
        if (browserTabsExpanded == expanded) {
            return;
        }
        browserTabsExpanded = expanded;
        updateTabsVisibility();
    }

    private void updateTabsVisibility() {
        showTabs(!searchOpen && !browserTabsExpanded, true);
    }

    /** MainTabsActivity's animatorTabsVisible (109-110, 1072-1087), applied to this strip. */
    private void showTabs(boolean visible, boolean animated) {
        if (tabsViewWrapper == null || tabsView == null || stripVisible == visible) {
            return;
        }
        stripVisible = visible;
        if (searchOpen && !visible) {
            stripHiddenForSearch = true;
        }
        if (!searchOpen && visible) {
            stripHiddenForSearch = false;
        }
        tabsViewWrapper.animate().cancel();
        final float hiddenY = AndroidUtilities.dp(40);
        if (animated) {
            tabsViewWrapper.animate()
                    .alpha(visible ? 1f : 0f)
                    .translationY(visible ? 0f : hiddenY)
                    .scaleX(visible ? 1f : 0.85f)
                    .scaleY(visible ? 1f : 0.85f)
                    .setDuration(380)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
            if (fadeView != null) {
                fadeView.animate()
                        .alpha(visible ? 1f : 0f)
                        .setDuration(380)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .start();
            }
        } else {
            tabsViewWrapper.setAlpha(visible ? 1f : 0f);
            tabsViewWrapper.setTranslationY(visible ? 0f : hiddenY);
            tabsViewWrapper.setScaleX(visible ? 1f : 0.85f);
            tabsViewWrapper.setScaleY(visible ? 1f : 0.85f);
            if (fadeView != null) {
                fadeView.setAlpha(visible ? 1f : 0f);
            }
        }
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        if (position == POSITION_NOTIFICATIONS) {
            // The message center: one title bar and one capsule strip over the notifications,
            // private-messages and chats pages (each the existing screen, embedded).
            return MessageCenterFragment.forMainTabs();
        } else if (position == POSITION_SETTINGS) {
            return new SettingsFragment(mainTabsArgs());
        } else if (position == POSITION_PROFILE) {
            // The reader's own profile as a root, the screen a post's author opens; as a root it
            // draws no back button.
            final io.github.baba983.nativedo.core.session.SessionSnapshot snapshot =
                    NativeDo.container(getParentActivity()).getSessionStore().snapshot();
            final String username = snapshot != null && snapshot.getUsername() != null ? snapshot.getUsername() : "";
            final String displayName = snapshot != null && snapshot.getDisplayName() != null ? snapshot.getDisplayName() : "";
            return io.github.baba983.nativedo.profile.ProfileFragment.forRoot(username, displayName);
        }
        return new TopicListFragment(mainTabsArgs());
    }

    /**
     * The fourth tab, upstream's avatar tab ({@code GlassTabView.createAvatar}): the label with a
     * 22dp round picture at the 5dp the icon tabs put their lottie at, instead of an icon. Telegram
     * builds it from the account's own user; this client has no Telegram account, so the picture is
     * the session's site avatar - the same {@code DiscourseAvatars} user the profile screen draws.
     *
     * <p>The picture is bound by {@link #updateProfileAvatar} from the session, not from the stored
     * reader read here: a stored reader is not a verified one, and fetching its avatar before the
     * session has answered would be a request for the previous account. The tab shows a neutral
     * placeholder until the session lands, and the observer re-binds it then.</p>
     */
    private GlassTabView createProfileTab(Context context) {
        GlassTabView tab = new GlassTabView(context);
        tab.setText("我的");
        profileAvatarView = new BackupImageView(context);
        profileAvatarView.setRoundRadius(dp(11));
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(
                dp(22), dp(22), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        avatarParams.topMargin = dp(5);
        tab.addView(profileAvatarView, avatarParams);
        tab.updateColorsLottie();
        return tab;
    }

    /**
     * Keeps the fourth tab's picture on the session. The observer belongs to the views: it is
     * dropped in {@link #onFragmentDestroy} and started again from {@link #createView}, so a theme
     * switch's rebuilt strip never leaves a collector behind holding a dead view.
     */
    private void observeProfile() {
        stopProfileObservation();
        profileObserver = SessionUi.observe(NativeDo.container(getParentActivity()), state -> {
            updateProfileAvatar(state);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void stopProfileObservation() {
        if (profileObserver != null) {
            SessionUi.cancel(profileObserver);
            profileObserver = null;
        }
    }

    /**
     * Binds the fourth tab's picture from the session state. A confirmed reader shows their initials
     * at once (the placeholder), with the picture replacing them once it is in hand; an unresolved
     * session shows a neutral circle, never an empty hole. A change of account re-binds the new
     * reader's; a download that outlives its reader is dropped by the identity fence.
     */
    private void updateProfileAvatar(AppSessionState state) {
        if (profileAvatarView == null) {
            return;
        }
        if (!(state instanceof AppSessionState.Authenticated)) {
            profileAvatarIdentity = null;
            profileAvatarView.setImageDrawable(placeholderAvatar());
            return;
        }
        final SessionSnapshot snapshot = ((AppSessionState.Authenticated) state).getSnapshot();
        final AppContainer container = NativeDo.container(getParentActivity());
        final String username = snapshot.getUsername() != null ? snapshot.getUsername() : "";
        final String displayName = snapshot.getDisplayName() != null ? snapshot.getDisplayName() : "";
        final String avatarTemplate = snapshot.getAvatarTemplate() != null ? snapshot.getAvatarTemplate() : "";
        profileAvatarIdentity = username;
        final TLRPC.User user = DiscourseAvatars.INSTANCE.user(username, displayName, avatarTemplate, container.getBaseUrl());
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(user);
        // The reader's initials are up at once; the picture replaces them when it is in hand.
        profileAvatarView.setImageDrawable(avatarDrawable);
        ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(user);
        if (location != null) {
            profileAvatarView.setImage(location, "50_50", avatarDrawable, user);
            return;
        }
        String url = DiscourseAvatars.INSTANCE.url(avatarTemplate, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
        if (DiscourseAvatars.INSTANCE.isCached(user)) {
            bindProfileAvatar(avatarDrawable, user);
        } else if (url != null) {
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                bindProfileAvatar(avatarDrawable, user);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /** Binds a ready picture, but only while the reader it was prepared for is still confirmed. */
    private void bindProfileAvatar(AvatarDrawable avatarDrawable, TLRPC.User user) {
        String username = user.username != null ? user.username : "";
        if (!username.equals(profileAvatarIdentity) || profileAvatarView == null) {
            return;
        }
        ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(user);
        if (location != null) {
            profileAvatarView.setImage(location, "50_50", avatarDrawable, user);
        }
    }

    /** A neutral circle for the tab while no confirmed reader names a picture. */
    private AvatarDrawable placeholderAvatar() {
        if (placeholderAvatar == null) {
            placeholderAvatar = new AvatarDrawable();
            placeholderAvatar.setInfo(0L);
        }
        return placeholderAvatar;
    }

    @Override
    protected int getFragmentsCount() {
        return TABS_COUNT;
    }

    @Override
    protected int getStartPosition() {
        if (startTabPosition >= 0 && startTabPosition < TABS_COUNT) {
            return startTabPosition;
        }
        return POSITION_TOPICS;
    }

    /**
     * MainTabsActivity:781-793 verbatim in shape: on any page but the first the back is the tab
     * host's, and it goes to the first page - at commit only, so a gesture that starts here shows
     * no page animation (the layout's own size check keeps it from starting one) and a cancelled
     * gesture changes nothing. On the first page the host takes no back, which lets the root's
     * back reach the shell and end in the system's own finish.
     */
    @Override
    public boolean onBackPressed(boolean invoked) {
        final boolean result = super.onBackPressed(invoked);
        if (result) {
            final int startPosition = getStartPosition();
            if (viewPager.getCurrentPosition() != startPosition) {
                if (invoked) {
                    viewPager.scrollToPosition(startPosition);
                }
                return false;
            }
        }
        return result;
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) {
            selectTab(viewPager.getCurrentPosition(), true);
            setGestureSelectedOverride(0, false);
        }
        if (contentView != null) {
            contentView.invalidate();
        }
    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        if (tabsView != null) {
            final float position = viewPager.getPositionAnimated();
            setGestureSelectedOverride(position, !manual);
            if (!manual) {
                selectTab(Math.round(position), true);
            }
        }
        if (contentView != null) {
            contentView.invalidate();
        }
    }

    /** The page selection, the way upstream's host drives upstream's strip. */
    public void selectTab(int position, boolean animated) {
        for (int index = 0; index < tabs.length; index++) {
            tabs[index].setSelected(index == position, animated);
        }
    }

    /**
     * The notifications tab's unread badge, fed the way upstream's own host feeds its chats tab:
     * the count is the site's `all_unread_notifications_count` (the number its own header badge
     * shows), seeded from the bootstrap page and then whatever the `/notification/{userId}` message
     * bus message carries - the server publishes a new count after notifications are read, so the
     * badge follows the site instead of guessing. No count is a null counter, which is what
     * upstream's own {@code checkUnreadCount} does with zero.
     */
    private void watchNotificationBadge() {
        MessageBusSession session = NativeDo.container(getParentActivity()).getMessageBusSession();
        updateNotificationBadge(session.getNotificationCounts().getValue().getAllUnread(), false);
        badgeObserver = session.observeNotificationCounts(counts ->
                updateNotificationBadge(counts.getAllUnread(), true));
    }

    private void updateNotificationBadge(int unread, boolean animated) {
        if (tabs == null || tabs.length <= POSITION_NOTIFICATIONS) {
            return;
        }
        if (unread > 0) {
            tabs[POSITION_NOTIFICATIONS].setCounter(
                    LocaleController.formatNumber(unread, ','), false, animated);
        } else {
            tabs[POSITION_NOTIFICATIONS].setCounter(null, false, animated);
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (badgeObserver != null) {
            badgeObserver.close();
            badgeObserver = null;
        }
        stopProfileObservation();
        super.onFragmentDestroy();
    }

    /** The strip's own selection follows the finger, so the tab under it lights up while dragging. */
    public void setGestureSelectedOverride(float animatedPosition, boolean allow) {
        for (int index = 0; index < tabs.length; index++) {
            final float visibility = Math.max(0, 1f - Math.abs(index - animatedPosition));
            tabs[index].setGestureSelectedOverride(visibility, allow);
        }
        if (tabsView != null) {
            tabsView.invalidate();
        }
    }

    private int getEstBackgroundColor() {
        return ColorUtils.blendARGB(
                getThemedColor(Theme.key_windowBackgroundGray),
                getThemedColor(Theme.key_windowBackgroundWhite),
                viewPager != null ? viewPager.getPositionVisibility(0) : 1);
    }

    @NonNull
    @Override
    protected WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);

        insetLeft = systemInsets.left;
        insetRight = systemInsets.right;
        navigationBarHeight = systemInsets.bottom;

        final int fadeHeight = navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS);
        final ViewGroup.LayoutParams fadeParams = fadeView.getLayoutParams();
        if (fadeParams.height != fadeHeight) {
            fadeParams.height = fadeHeight;
            fadeView.setLayoutParams(fadeParams);
        }

        final ViewGroup.MarginLayoutParams pagerParams = (ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
        if (pagerParams.leftMargin != systemInsets.left || pagerParams.rightMargin != systemInsets.right) {
            pagerParams.leftMargin = systemInsets.left;
            pagerParams.rightMargin = systemInsets.right;
            viewPager.setLayoutParams(pagerParams);
        }

        tabsViewWrapper.setPadding(systemInsets.left, 0, systemInsets.right, navigationBarHeight);

        // The pages get the insets through the pager they live in.
        return super.onApplyWindowInsets(v, insets);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back to the front is the reader saying they are looking at the page again; a tab
        // switch in between does not go through here, so it does not reload anything.
        notifyCurrentPageVisible();
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshOnNextVisible = true;
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        refreshOnNextVisible = true;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        // A screen pushed from here (a topic) has just been closed; the page gets to catch up.
        notifyCurrentPageVisible();
    }

    /** Set while the host is away (a pushed screen, the app in the background). */
    private boolean refreshOnNextVisible;

    /**
     * The page is told the screen is its again, and it is told exactly once per absence. The first
     * presentation is not an absence: the page is loading itself while it is put on the screen, and
     * a refresh there would read the feed a second time.
     */
    private void notifyCurrentPageVisible() {
        if (!refreshOnNextVisible) {
            return;
        }
        refreshOnNextVisible = false;
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabPage) {
            ((TabPage) fragment).onParentBecameVisible();
        }
    }

    /* Slide: the pages own their vertical gestures; the host only moves between pages. */

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return canScrollInternal(ev, true);
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return canScrollInternal(ev, false);
    }

    private boolean canScrollInternal(MotionEvent ev, boolean forward) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabPage) {
            return ((TabPage) fragment).canParentTabsSlide(ev, forward);
        }
        return false;
    }

    public void updateColors() {
        // MainTabsActivity.blur3_updateFadeColors (1206-1211): the fade view draws the window
        // background the pager currently shows, blended by the page position, not one fixed key.
        iBlur3SourceColor.setColor(getEstBackgroundColor());
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        // Upstream's blur3_invalidateBlur (MainTabsActivity:1197-1205) refreshes the render node
        // it set up in its constructor; this host records the pages into the source itself on
        // every content draw (createContentView.dispatchDraw), so the invalidation below is what
        // it needs and no renderer setup is involved.
        if (fadeView != null) {
            fadeView.invalidate();
        }
        // MainTabsActivity.blur3_updateColors (1214-1231): the strip's capsule is this view's own
        // background, recorded in its display list, so the switch has to repaint the view - the
        // drawable's updateColors alone would only change a colour nothing re-records. Upstream
        // calls both, and this is the line whose absence left the capsule light on the device.
        if (tabsView != null) {
            tabsView.invalidate();
        }
        if (tabs != null) {
            for (GlassTabView tab : tabs) {
                tab.updateColorsLottie();
            }
        }
        if (contentView != null) {
            contentView.invalidate();
        }
        // The pages' own background is the window background. They are not in the shell's fragment
        // stack, so the theme animation does not reach them: repaint what is mounted here.
        for (int index = 0; index < fragmentsArr.size(); index++) {
            final View pageView = fragmentsArr.valueAt(index).fragment.getFragmentView();
            if (pageView != null) {
                pageView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();
        final ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_dialogBackground));
        return themeDescriptions;
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    /**
     * What the host asks of the pages it is showing, shaped after upstream's
     * {@code MainTabsActivity.TabFragmentDelegate}: the page the finger is on decides whether a
     * horizontal move is a page swipe or a gesture its own list takes, a second tap on its tab
     * scrolls it to the top, and coming back to the front is its chance to read the site again.
     */
    public interface TabPage {
        default boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
            return true;
        }

        default void onParentScrollToTop() {
        }

        default void onParentBecameVisible() {
        }
    }
}
