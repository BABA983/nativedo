package io.github.baba983.nativedo.messages;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.auth.LoginWebViewFragment;
import io.github.baba983.nativedo.chat.PublicChannelsFragment;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.core.session.AppSessionState;
import io.github.baba983.nativedo.feature.messages.data.PrivateMessageBox;
import io.github.baba983.nativedo.messagecenter.MessageCenterChild;
import io.github.baba983.nativedo.sessionui.SessionUi;
import io.github.baba983.nativedo.sessionui.SessionUiAction;
import io.github.baba983.nativedo.sessionui.SessionUiState;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.NewTopicFragment;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The reader's private messages, the way the site lists them: an inbox, a sent box and the unread
 * ones. Each is the standard {@code topic_list} envelope behind its own
 * {@code topics/private-messages*} route, so a private message is a topic and its row is the same
 * {@link DialogCell} a public topic list draws - the site's own excerpt, date and unread badge.
 *
 * <p>Nothing upstream can be a whole screen here: Telegram's own message screens read accounts,
 * chats and sessions this client does not have. What is reused is the row, the shimmer that stands
 * in for rows that have not arrived ({@link FlickerLoadingView}, DialogsActivity's own), and the
 * session-state rows the other native screens show.</p>
 *
 * <p>A private message is opened with the topic screen - the same {@link TopicFragment} public
 * topics use - because a private message <em>is</em> a topic. Starting one is the compose button
 * (a {@link FragmentFloatingButton}), in the composer's private-message mode.</p>
 *
 * <p>The three boxes are Telegram's own horizontal tab strip ({@link ScrollSlidingTextTabStrip},
 * the component the profile's media pages put under their bar) over upstream's own
 * {@link ViewPagerFixed}, the pager the profile's media pages and DialogsActivity's search use. A
 * tap slides the pager onto that box, a finger swipe slides the two pages under the strip and
 * commits the box the finger landed on, and the strip's indicator follows the pager's own clock -
 * exactly the wiring {@code ProfileSharedMediaView} gives its pages. Each box is a page of its own:
 * its own list, its own scroll, its own loading and empty state and its own read, so nothing of one
 * box survives onto another and switching back destroys nothing. The strip's colours come from the
 * theme keys, so the day/night switch recolours it in place. The public channels live behind their
 * own screen and are offered from the overflow menu.</p>
 *
 * <p>When the message center opens this screen it passes {@code messageCenter=true}: the center
 * owns the title bar and already carries the chats as a page of its own, so this page draws no bar
 * (and keeps its bar out of the host, or it would sit as an empty band), shows no box strip - only
 * the center's own menu, reached through {@link #showMessageCenterOptions(View)}, chooses a box -
 * and offers no public-channels entry, so the center never shows two rows of tabs. The center's
 * capsule is drawn over the page, so the page also takes the strip's height as its lists' top
 * padding ({@link #setMessageCenterTopInset(int)}): each box starts below the capsule and its rows
 * scroll behind it, while the last row stays clear of the main-tab strip through the bottom
 * padding. The boxes' lists, scroll and reads are the same either way. The standalone 私信 entry
 * still opens the screen with its own bar, strip and channels entry.</p>
 */
public class PrivateMessagesFragment extends BaseFragment implements MessageCenterChild {

    private static final int TYPE_MESSAGE = 0;
    private static final int TYPE_NOTE = 1;
    /** The row a session state draws in place of the list: the state and the tap that acts on it. */
    private static final int TYPE_STATE = 2;

    private static final int OPTIONS_ITEM = 1;

    /** The tab ids the strip carries, one per box: the tap maps straight back to a box. */
    private static final int TAB_INBOX = 0;
    private static final int TAB_SENT = 1;
    private static final int TAB_UNREAD = 2;

    /** The strip's own band under the bar: the height Telegram's tab strips reserve. */
    private static final int TAB_STRIP_HEIGHT_DP = 48;
    /**
     * The room the list keeps below its last row for the compose button: the button's own 48dp
     * plus its 14dp bottom margin, rounded up, so a row can always be scrolled clear of it. It is
     * one of two reservations, not an addition: a page hosted over the main-tab strip already
     * keeps that strip's band (72dp), which reaches further down than the button, so there the band
     * is the room and the button floats over the list's last rows as it does on the topic list.
     */
    private static final int FAB_LIST_CLEARANCE_DP = 64;

    /** How close to the end the list comes before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    /** The boxes, in the order the strip shows them: a tab id is its index here. */
    private static final PrivateMessageBox[] BOXES = {
            PrivateMessageBox.Inbox, PrivateMessageBox.Sent, PrivateMessageBox.Unread,
    };

    /** The pager that slides the three boxes: upstream's own, the profile's media pages' pager. */
    private ViewPagerFixed pager;
    /** The box strip (the profile's media tabs, SharedMediaLayout): a tap reads that box. */
    private ScrollSlidingTextTabStrip tabsView;
    /** The compose button: DialogsActivity's own floating button, bottom-right (3956, 4719). */
    private FragmentFloatingButton composeButton;
    private ActionBarMenuItem optionsItem;

    /**
     * The three boxes, each with its own rows, scroll and read state. They outlive the views: a
     * screen whose views are dropped and built again (the tab host does that) keeps the rows it has
     * already read, so nothing re-reads and no page loses its place.
     */
    private final BoxPage[] pages = {
            new BoxPage(PrivateMessageBox.Inbox, TAB_INBOX),
            new BoxPage(PrivateMessageBox.Sent, TAB_SENT),
            new BoxPage(PrivateMessageBox.Unread, TAB_UNREAD),
    };
    /** The box on screen, as its index in {@link #BOXES}. */
    private int currentTab;

    /** The context the pager builds its page views in, captured for the adapter (see createView). */
    private Context viewContext;

    private AppContainer container;

    /** The session value the screen last acted on, so a change can be told from a repeat. */
    private AppSessionState lastSessionState;
    /**
     * Bumped whenever the account changes or the views go away. A read captures it before it starts
     * and drops its own answer if it no longer matches, so a late page from the previous account
     * (or another box) cannot repopulate a list.
     */
    private int loadEpoch;
    /** The session-state observer; cancelled with the views and started again with the next ones. */
    private kotlinx.coroutines.Job sessionObserver;

    /**
     * The day/night switch recolours the strip, the button and the rows in place: this screen is
     * not rebuilt by the switch (the composer, the topic list and the settings page are the model
     * here), so the colours are re-read where they are painted. Registered once per fragment, with
     * the fragment's own lifecycle.
     */
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != NotificationCenter.needSetDayNightTheme && id != NotificationCenter.didSetNewTheme) {
                    return;
                }
                AndroidUtilities.runOnUIThread(this::updateColors);
            };

    private int navigationBarHeight;
    /**
     * The room the message center's own title bar and capsule strip take at the top of the window,
     * in pixels, as the center measures them. The page sits under that band, so each box's list
     * keeps it above its first row while rows scrolled past draw behind the capsule. Zero while
     * nothing hosts this page, which is why the standalone screen keeps its own geometry.
     */
    private int messageCenterTopInset;

    /**
     * Whether the message center is hosting this screen. The center owns the title bar and offers
     * the box choice from its own menu, so an embedded page draws no bar of its own (and keeps its
     * bar out of the host, or it would sit as an empty band above the list) and no box strip - only
     * the standalone 私信 entry does, which is why the center never shows two rows of tabs.
     */
    private boolean embedded;
    /** Whether a main-tab strip sits under this screen, which the list and the button must clear. */
    private boolean hasMainTabs;
    private int additionNavigationBarHeight;
    private int additionFloatingButtonOffset;

    public PrivateMessagesFragment(Bundle args) {
        super(args);
    }

    public PrivateMessagesFragment() {
        super();
    }

    /** The inbox, which is where the 私信 entries open. */
    public static PrivateMessagesFragment create() {
        return new PrivateMessagesFragment();
    }

    @Override
    protected View createView(Context context) {
        viewContext = context;
        // Which way this screen was opened: the message center embeds it as one page under the
        // center's own title bar (its menu switches boxes and it already carries the chat page), so
        // the page draws no bar of its own, keeps its bar out of the host, and shows no strip - only
        // the standalone 私信 entry, whose own bar and strip would otherwise stack a second row of
        // tabs under the center's.
        embedded = arguments != null && arguments.getBoolean("messageCenter", false);
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs
                ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs
                ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN)
                : 0;

        if (embedded) {
            // The center's bar is the screen's bar. Keeping this one out of the container is what
            // stops the host adding it: an added bar would sit above the list as an empty band.
            actionBar.setAddToContainer(false);
        } else {
            actionBar.setTitle("私信");
            actionBar.setSubtitle(boxLabel(BOXES[currentTab]));
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
            // The one bar action left is the overflow, where the public channels live. Starting a
            // message is the floating compose button now, the way DialogsActivity starts a chat.
            optionsItem = actionBar.createMenu().addItem(
                    OPTIONS_ITEM, org.telegram.messenger.R.drawable.ic_ab_other);
            optionsItem.setContentDescription("更多");
            optionsItem.setOnClickListener(v -> showItemOptions());
        }

        // The box strip under the bar, over the pager: the same component the profile's media pages
        // place under their bar. A tap moves the indicator and slides the pager onto that box. The
        // embedded page has no strip: the center's own menu is where its boxes are chosen.
        if (!embedded) {
            tabsView = new ScrollSlidingTextTabStrip(context, resourceProvider);
            // The strip's own tap animation and the pager's slide use the same clock, so they settle
            // together (ProfileSharedMediaView does the same with its 320 ms).
            tabsView.animationDuration = 320;
            tabsView.setDelegate(new BoxTabsDelegate());
            buildTabs();
        }

        // The pages: upstream's ViewPagerFixed, the profile's media pager. A tap calls its
        // scrollToPosition; a finger swipe is its own touch path. Its strip-following hooks are the
        // same calls ProfileSharedMediaView makes, so the pages and the strip share one clock.
        pager = new ViewPagerFixed(context, resourceProvider) {
            @Override
            protected long getManualScrollDuration() {
                return 320;
            }

            @Override
            protected boolean canScroll(MotionEvent e) {
                // Embedded boxes change from the menu; drags belong to the message categories.
                return !embedded;
            }

            @Override
            protected boolean canScrollBackward(MotionEvent e) {
                return !embedded;
            }

            @Override
            public boolean canScrollHorizontally(int direction) {
                return !embedded && super.canScrollHorizontally(direction);
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                followStripWithPager();
            }

            @Override
            protected void onTabPageSelected(int position, boolean forward) {
                onPagerLanded(position);
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                // A finger swipe committed (swapViews calls this): the box follows the page.
                onPagerLanded(position);
            }
        };
        pager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return pages.length;
            }

            @Override
            public View createView(int viewType) {
                return createPageView(viewType);
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                // A page's rows are bound by its own adapter when they arrive.
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public CharSequence getItemTitle(int position) {
                return boxLabel(BOXES[position]);
            }
        });
        // The pager always starts on page 0; point it at the box the reader last chose (a view
        // built again after the tab host dropped it), without animating.
        if (currentTab != 0) {
            pager.setPosition(currentTab);
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        if (tabsView != null) {
            column.addView(tabsView, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, TAB_STRIP_HEIGHT_DP));
        }
        column.addView(pager, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f));

        FrameLayout frame = new FrameLayout(context);
        frame.addView(column, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // The compose button: DialogsActivity's own floating button, bottom-right (3963, 4719).
        // Its default layout puts it 14dp above the list's bottom; checkUi_listPadding lifts it
        // over the navigation inset and reserves the matching room under the last row.
        composeButton = new FragmentFloatingButton(context, resourceProvider);
        composeButton.setImageResource(org.telegram.messenger.R.drawable.filled_fab_compose_32);
        composeButton.setContentDescription("写私信");
        composeButton.setOnClickListener(v -> presentNewMessage(null));
        frame.addView(composeButton, FragmentFloatingButton.createDefaultLayoutParams());

        fragmentView = frame;
        updateColors();
        checkUi_listPadding();
        if (embedded) {
            // The page is one page of the center's host, as the notifications page is a page of the
            // tab host: the host does not hand a page its window insets, so the page takes its own
            // (NotificationsFragment wires the very same listener for the same reason).
            ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        }
        container = NativeDo.container(getParentActivity());
        // The session is read before the list: an account with no usable session gets the explicit
        // state row (and the read is not even sent).
        lastSessionState = container.getSessionCoordinator().getSessionState().getValue();
        observeSession();
        if (lastSessionState instanceof AppSessionState.Authenticated) {
            onPagerLanded(currentTab);
        } else {
            onSessionState(lastSessionState);
        }
        return fragmentView;
    }

    /**
     * The day/night observer is registered once with the fragment (not with the views): a switch
     * that happens while this screen is behind another one still has to recolour it, and the
     * callback null-checks the views it recolours.
     */
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    /**
     * The tab/view host drops a screen's views without destroying the fragment: the observer belongs
     * to the views, so it is dropped here and started again from {@link #createView} - and the page
     * views go with them, while the rows each box has read stay. Bumping the epoch drops any read
     * still in flight for the old views; a page that was mid-read is marked unread so it is asked
     * again, a page that already answered keeps its rows and is not.
     */
    @Override
    public void clearViews() {
        stopSessionObservation();
        loadEpoch++;
        for (BoxPage page : pages) {
            if (page.loading) {
                page.loading = false;
                page.loaded = false;
            }
            page.detachViews();
        }
        pager = null;
        tabsView = null;
        composeButton = null;
        optionsItem = null;
        super.clearViews();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, NotificationCenter.didSetNewTheme);
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
     * so a value equal to it changes nothing; anything else drops the old account's rows from every
     * box and, when the session is usable again, reads the box on screen again - immediately, so an
     * open screen never keeps showing the previous account.
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
                notifyRowsChanged();
                ensureLoaded(pages[currentTab]);
            }
        } else {
            for (BoxPage page : pages) {
                page.stateUi = SessionUi.stateFor(state);
                page.loading = false;
            }
            notifyRowsChanged();
        }
    }

    /** Drops every box's rows and restarts its paging bookkeeping, so nothing of the old account stays. */
    private void resetForReload() {
        for (BoxPage page : pages) {
            page.reset();
        }
    }

    /** The tap on a state row: recover the session, or read again - the stateless retry reads this box. */
    private void onStateAction(BoxPage page) {
        if (page == null || page.stateUi == null) {
            return;
        }
        SessionUiAction action = SessionUi.action(page.stateUi);
        if (action == SessionUiAction.Retry) {
            page.reset();
            notifyRowsChanged(page);
            loadPage(page);
            return;
        }
        openRecovery(action == SessionUiAction.OpenVerification);
    }

    /**
     * The session is dealt with where the site can be: the recovery flow in the login WebView. The
     * WebView's own completion publishes the usable session; this screen's observer re-reads when
     * that changes the account, and the explicit completion re-reads it when it does not.
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
     * The recovery WebView's explicit completion ran. A completion that changed the account is
     * already re-read by {@link #onSessionState}; this reads only the same-account case, exactly
     * once, and never writes the session.
     */
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
        resetForReload();
        notifyRowsChanged();
        ensureLoaded(pages[currentTab]);
    }

    /** The standalone overflow: which box is shown (checked) and the public channels screen. */
    private void showItemOptions() {
        if (optionsItem == null) {
            return;
        }
        ItemOptions io = ItemOptions.makeOptions(this, optionsItem);
        buildBoxMenu(io, true);
        io.show();
        // Upstream raises its own menu by this constant; the popup otherwise hangs lower than the
        // icon it belongs to (DialogsActivity's own options menu).
        io.setTranslationY(-AndroidUtilities.dp(64));
    }

    /**
     * The message center's own bar menu, hung from the view its title bar hands over. The boxes are
     * the same three entries the standalone overflow offers, so the center keeps the way to reach
     * 收件箱/已发送/未读 and marks the box on screen - but the center already carries the chats (the
     * public channels) as a page of its own, so that entry is not repeated here.
     */
    @Override
    public void showMessageCenterOptions(View anchor) {
        if (anchor == null) {
            return;
        }
        ItemOptions io = ItemOptions.makeOptions(this, anchor);
        buildBoxMenu(io, false);
        io.show();
    }

    /**
     * The box choice both menus offer: the three entries, the box on screen checked, and - for the
     * standalone screen only - the public channels screen.
     */
    private void buildBoxMenu(ItemOptions io, boolean includeChannels) {
        PrivateMessageBox box = BOXES[currentTab];
        io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
        io.setDimAlpha(0x08);
        // The boxes ride the strip, but the menu keeps the same choice and marks the one shown, so
        // the existing flow keeps working and the current box is legible either way.
        io.addChecked(box == PrivateMessageBox.Inbox,
                org.telegram.messenger.R.drawable.settings_devices, boxLabel(PrivateMessageBox.Inbox),
                () -> selectBox(PrivateMessageBox.Inbox));
        io.addChecked(box == PrivateMessageBox.Sent,
                org.telegram.messenger.R.drawable.outline_saved_24, boxLabel(PrivateMessageBox.Sent),
                () -> selectBox(PrivateMessageBox.Sent));
        io.addChecked(box == PrivateMessageBox.Unread,
                org.telegram.messenger.R.drawable.settings_sounds, boxLabel(PrivateMessageBox.Unread),
                () -> selectBox(PrivateMessageBox.Unread));
        if (includeChannels) {
            io.addGap();
            io.add(org.telegram.messenger.R.drawable.msg_channel, "公共频道",
                    () -> presentFragment(new PublicChannelsFragment()));
        }
    }

    /**
     * Switches the box shown from the menu: the pager slides there under its own clock, and the
     * landing ({@link #onPagerLanded}) points the strip at it, reads it if it has not answered, and
     * drops nothing of the box it is leaving.
     */
    private void selectBox(PrivateMessageBox newBox) {
        int tab = tabForBox(newBox);
        if (tab == currentTab) {
            return;
        }
        if (pager != null) {
            pager.scrollToPosition(tab);
        } else {
            onPagerLanded(tab);
        }
    }

    private static String boxLabel(PrivateMessageBox box) {
        switch (box) {
            case Sent:
                return "已发送";
            case Unread:
                return "未读";
            case Inbox:
            default:
                return "收件箱";
        }
    }

    /** The strip's tab id for a box, and back: the strip speaks ints. */
    private static int tabForBox(PrivateMessageBox box) {
        switch (box) {
            case Sent:
                return TAB_SENT;
            case Unread:
                return TAB_UNREAD;
            case Inbox:
            default:
                return TAB_INBOX;
        }
    }

    /** Builds the three box tabs once, with the box on screen selected and the rest themed. */
    private void buildTabs() {
        if (tabsView == null) {
            return;
        }
        tabsView.addTextTab(TAB_INBOX, boxLabel(PrivateMessageBox.Inbox));
        tabsView.addTextTab(TAB_SENT, boxLabel(PrivateMessageBox.Sent));
        tabsView.addTextTab(TAB_UNREAD, boxLabel(PrivateMessageBox.Unread));
        tabsView.setInitialTabId(currentTab);
        tabsView.finishAddingTabs();
    }

    /**
     * The pager landed on [position]: that box is now the one on screen. Called from a tap's
     * {@code onTabPageSelected} (the strip has already animated itself there, upstream's own way)
     * and from a finger swipe's committed {@code onItemSelected}; either way the box's own read is
     * asked for only if it has not answered yet, so switching back never re-reads it.
     */
    private void onPagerLanded(int position) {
        if (position < 0 || position >= pages.length) {
            return;
        }
        currentTab = position;
        if (actionBar != null) {
            actionBar.setSubtitle(boxLabel(BOXES[position]));
        }
        if (tabsView != null && tabsView.getCurrentTabId() != position) {
            tabsView.selectTabWithId(position, 1f);
        }
        ensureLoaded(pages[position]);
    }

    private void ensureLoaded(BoxPage page) {
        if (!page.loaded) {
            loadPage(page);
        }
    }

    /**
     * The strip follows the pager while a swipe is in flight - the indicator slides between the two
     * tabs and their text colours interpolate - exactly the way {@code ProfileSharedMediaView} drives
     * its strip from upstream's pager (the fraction is how far the pager's animated position has
     * come from the tab it is on toward the tab it is heading to, and back to 0 when the drag is
     * cancelled). The pager owns the clock here, so the strip is driven from it.
     */
    private void followStripWithPager() {
        if (tabsView == null || tabsView.getTabsCount() < 2 || pager == null) {
            return;
        }
        View[] pageViews = pager.getViewPages();
        if (pageViews == null || pageViews.length < 2 || pageViews[1] == null) {
            return;
        }
        final int current = pager.getCurrentPosition();
        final int next = pager.getNextPosition();
        if (next == current || next < 0 || next >= tabsView.getTabsCount()) {
            return;
        }
        final float p = pager.getPositionAnimated();
        final float frac = Utilities.clamp(Math.abs(p - current) / Math.abs(next - current), 1f, 0f);
        tabsView.selectTabWithId(next, frac);
    }

    /** Starts a private message; [recipient] is prefilled when an entry point already knows it. */
    private void presentNewMessage(String recipient) {
        if (getParentActivity() == null) {
            return;
        }
        presentFragment(NewTopicFragment.privateMessage(recipient));
    }

    /** A private message is a topic: the topic screen opens it, exactly as a public topic does. */
    private void open(TopicSummary topic) {
        presentFragment(TopicFragment.forTopic(topic.getId(), topic.getTitle()));
    }

    /**
     * Draws one box: its own list (upstream's list of {@link DialogCell} rows), its own shimmer
     * while no answer has landed, and its own scroll. The view is the pager's page, so the pager
     * builds it once and keeps it: the rows are the box's own, never another box's.
     */
    private View createPageView(int index) {
        final BoxPage page = pages[index];
        final Context context = viewContext;
        final RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        page.adapter = new MessageAdapter(page);
        list.setAdapter(page.adapter);
        // The rows are DialogCells, so the chat list's own shimmer is the matching one
        // (DialogsActivity:4059-4064, 4695): it is the list's empty view, and only until the first
        // answer lands.
        FlickerLoadingView progress = new FlickerLoadingView(context);
        progress.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progress.setVisibility(View.GONE);
        list.setEmptyView(progress);
        list.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        list.setVerticalScrollBarEnabled(false);
        // The list reaches the bottom of the window and its rows scroll behind the main-tab strip
        // and the message center's capsule; the last row stays reachable above the strip, and the
        // first below the capsule, through the padding. A list that clipped at its padding would
        // cut every row at the band's edge instead.
        list.setClipToPadding(false);
        list.setOnItemClickListener((view, position) -> {
            if (page.stateUi != null) {
                onStateAction(page);
            } else if (position >= 0 && position < page.items.size()) {
                open(page.items.get(position));
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadNextPageIfNeeded(page);
            }
        });
        page.listView = list;
        page.progressView = progress;
        // The pager detaches a page when it slides away; its rows stay in the box, and so does its
        // place: the scroll is saved on detach and put back on the next attach.
        FrameLayout pageView = new FrameLayout(context) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (page.layoutManagerState != null && page.listView != null
                        && page.listView.getLayoutManager() != null) {
                    page.listView.getLayoutManager().onRestoreInstanceState(page.layoutManagerState);
                    page.layoutManagerState = null;
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                if (page.listView != null && page.listView.getLayoutManager() != null) {
                    page.layoutManagerState = page.listView.getLayoutManager().onSaveInstanceState();
                }
                super.onDetachedFromWindow();
            }
        };
        pageView.addView(list, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // The shimmer rides over the list, centred, the way DialogsActivity draws its own (4062).
        pageView.addView(progress, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        applyListPadding(page);
        return pageView;
    }

    private void loadPage(BoxPage page) {
        String username = sessionUsername();
        if (container == null || page.loading || page.reachedTheEnd || username == null) {
            return;
        }
        // Without a usable session there is nothing private to read: the state row is the screen.
        if (!(lastSessionState instanceof AppSessionState.Authenticated)) {
            return;
        }
        page.loaded = true;
        page.loading = true;
        final int asked = page.page;
        final int epoch = loadEpoch;
        final String askedUsername = username;
        final PrivateMessageBox askedBox = page.box;
        PrivateMessagesLoader.INSTANCE.page(container, username, askedBox, asked, loaded -> {
            if (epoch != loadEpoch || !askedUsername.equals(sessionUsername())) {
                return kotlin.Unit.INSTANCE;
            }
            page.loading = false;
            page.stateUi = null;
            page.page = asked + 1;
            int added = 0;
            for (TopicSummary topic : loaded.getTopics()) {
                if (page.seenIds.add(topic.getId())) {
                    page.items.add(topic);
                    page.authors.add(author(topic));
                    added++;
                }
            }
            // The site's own `more_topics_url` is what says another page follows; a page that
            // brought nothing new ends the list too, so a site that repeats a page cannot loop.
            page.reachedTheEnd = loaded.getMoreTopicsUrl() == null || added == 0;
            page.empty = page.items.isEmpty();
            notifyRowsChanged(page);
            fetchAvatars(page);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            if (epoch != loadEpoch || !askedUsername.equals(sessionUsername())) {
                return kotlin.Unit.INSTANCE;
            }
            page.loading = false;
            FileLog.e("private messages failed", error);
            // A read that did not arrive is its own explicit state, not a blank list - but only
            // while there is nothing to read: a page that failed with rows already on screen leaves
            // them standing, and the next scroll asks again.
            if (page.items.isEmpty()) {
                page.stateUi = SessionUi.stateForLoadFailure(error);
            }
            notifyRowsChanged(page);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Asks for the next page once the reader is within a few rows of the end of this one. */
    private void loadNextPageIfNeeded(BoxPage page) {
        if (page.listView == null || page.listView.getAdapter() == null
                || page.items.isEmpty() || page.reachedTheEnd) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) page.listView.getLayoutManager();
        if (manager == null
                || manager.findLastVisibleItemPosition()
                < page.listView.getAdapter().getItemCount() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        loadPage(page);
    }

    /** Whose messages these are: the site answers the box only for the reader themselves. */
    private String sessionUsername() {
        if (container == null || container.getSessionStore().snapshot() == null) {
            return null;
        }
        String username = container.getSessionStore().snapshot().getUsername();
        return username == null || username.isEmpty() ? null : username;
    }

    /**
     * The row's own line under the title. The site's excerpt when it has one, and otherwise a plain
     * description of the private message built only from the two participants the list actually
     * names. The description follows the author - the person the row's picture is drawn from - so
     * the line and the face are one identity, and it never pairs the last poster with the reply
     * count ("baba · 0"), which reads as a reply that never happened. Nothing is fetched for it.
     */
    private String rowSummary(TopicSummary topic) {
        String excerpt = topic.getExcerpt();
        if (excerpt != null && !excerpt.trim().isEmpty()) {
            return excerpt;
        }
        String author = topic.getAuthorUsername();
        if (author == null || author.isEmpty()) {
            return "私信";
        }
        String me = sessionUsername();
        if (me != null && me.equalsIgnoreCase(author)) {
            // The list names only the reader: a message the reader started, said as much and no more.
            return "你发起的私信";
        }
        return "与 " + author + " 的私信";
    }

    private TLRPC.User author(TopicSummary topic) {
        return DiscourseRows.topicAuthor(topic, container.getBaseUrl());
    }

    /** Each row's author picture is fetched once, then the list is bound again. */
    private void fetchAvatars(BoxPage page) {
        for (int index = 0; index < page.authors.size(); index++) {
            final int row = index;
            String template = index < page.items.size() ? page.items.get(index).getAuthorAvatarTemplate() : "";
            String url = DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(page.authors.get(index))) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, page.authors.get(index), () -> {
                if (page.listView != null && page.listView.getAdapter() != null) {
                    page.listView.getAdapter().notifyItemChanged(row);
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private void notifyRowsChanged(BoxPage page) {
        if (page != null && page.listView != null && page.listView.getAdapter() != null) {
            page.listView.getAdapter().notifyDataSetChanged();
        }
    }

    private void notifyRowsChanged() {
        for (BoxPage page : pages) {
            notifyRowsChanged(page);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
    }

    /**
     * The message center's title bar and capsule, in pixels: the band its pages sit under.
     *
     * <p>The value can arrive before this page has views - the host measures its bar and strip once
     * and hands the number over, which may happen before the page is built - so it is stored and
     * applied whenever a list is padded, rather than only to the list that exists at the call. It
     * replaces the previous measurement instead of adding to it, so a bar that changes height moves
     * the band rather than stacking a second one.</p>
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

    /**
     * The list's own padding, and the compose button's lift. The button's default layout pins it
     * 14dp above the frame's bottom, so the navigation inset is what its translation has to clear
     * (plus the main-tab strip's room when this page sits over one); the list reserves the room
     * under its last row that the page actually has something in: the main-tab strip's band when it
     * hosts this page, or the compose button's own clearance when the button is the only thing
     * down there. Whichever reaches further, not both - the sum is a blank band below the last row
     * with the floating button stranded in the middle of it. Above the first row the list keeps the
     * message center's bar and capsule when it hosts this page, so the box starts under them while
     * rows scrolled past draw behind them (the list does not clip to its padding). Every box's list
     * keeps both rooms, so a swipe onto a box that has not been laid out yet still lands with its
     * first row clear of the capsule and its last row clear of what is below it.
     */
    private void checkUi_listPadding() {
        for (BoxPage page : pages) {
            applyListPadding(page);
        }
        if (composeButton != null) {
            composeButton.setTranslationY(-navigationBarHeight - additionFloatingButtonOffset);
        }
    }

    private void applyListPadding(BoxPage page) {
        if (page != null && page.listView != null) {
            page.listView.setPadding(0, AndroidUtilities.dp(4) + messageCenterTopInset, 0,
                    AndroidUtilities.dp(4) + navigationBarHeight
                            + Math.max(additionNavigationBarHeight,
                                    AndroidUtilities.dp(FAB_LIST_CLEARANCE_DP)));
        }
    }

    /**
     * The day/night switch. The strip, the button, the bar and the rows all read their colours at
     * draw time or re-read them here; this screen is not rebuilt by the switch, so this is the
     * recolor.
     */
    private void updateColors() {
        if (tabsView != null) {
            tabsView.updateColors();
        }
        if (composeButton != null) {
            composeButton.updateColors();
        }
        for (BoxPage page : pages) {
            if (page.progressView != null) {
                page.progressView.invalidate();
            }
        }
        notifyRowsChanged();
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            actionBar.setSubtitleColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.updateColors();
        }
        if (fragmentView != null) {
            fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            fragmentView.invalidate();
        }
    }

    /**
     * The screen's own actions, as a screen test drives them: the overflow menu (which the reader
     * opens with the 更多 item) and the compose action. Neither invents a path - they are the same
     * calls the menu item's own click makes.
     */
    void openBoxMenu() {
        showItemOptions();
    }

    void askForNewMessage() {
        presentNewMessage(null);
    }

    /** The box currently shown, so a test can say a switch happened. */
    PrivateMessageBox currentBox() {
        return BOXES[currentTab];
    }

    /** The box strip, so a test can drive a tab the way the reader taps one. */
    ScrollSlidingTextTabStrip tabsView() {
        return tabsView;
    }

    /** The pager that slides the boxes, so a test can drive a finger the way the reader does. */
    ViewPagerFixed pager() {
        return pager;
    }

    /** The compose button, so a test can tap it and read its inset lift. */
    FragmentFloatingButton composeButton() {
        return composeButton;
    }

    /** The list of the box on screen, so a test can read its own padding and rows. */
    RecyclerListView listView() {
        BoxPage page = pages[currentTab];
        return page == null ? null : page.listView;
    }

    /** One box: its rows, its own list and scroll, its own read state, fenced by the load epoch. */
    private final class BoxPage {

        final PrivateMessageBox box;
        final int tab;
        /** One author per row, so the picture is fetched once and the row re-bound when it lands. */
        final List<TopicSummary> items = new ArrayList<>();
        final List<TLRPC.User> authors = new ArrayList<>();
        /** The ids already listed, so a page that overlaps the last one adds no second row. */
        final Set<Long> seenIds = new HashSet<>();

        SessionUiState stateUi;
        RecyclerListView listView;
        FlickerLoadingView progressView;
        MessageAdapter adapter;
        /** The page's scroll, saved when the pager detaches the view and put back when it returns. */
        Parcelable layoutManagerState;

        boolean loading;
        /** Whether the box holds nothing at all, which is its own row rather than a blank list. */
        boolean empty;
        /** Set once a read has been sent for this box: switching back to it does not re-read it. */
        boolean loaded;
        int page;
        /** Set when a page came back with nothing new: that is the end of the list. */
        boolean reachedTheEnd;

        BoxPage(PrivateMessageBox box, int tab) {
            this.box = box;
            this.tab = tab;
        }

        /** Drops every row and restarts the paging bookkeeping, so nothing of the old account stays. */
        void reset() {
            items.clear();
            authors.clear();
            seenIds.clear();
            stateUi = null;
            loading = false;
            empty = false;
            loaded = false;
            page = 0;
            reachedTheEnd = false;
            layoutManagerState = null;
        }

        /** The views are gone with the screen: keep the scroll, drop the references. */
        void detachViews() {
            if (listView != null && listView.getLayoutManager() != null) {
                layoutManagerState = listView.getLayoutManager().onSaveInstanceState();
            }
            listView = null;
            adapter = null;
            progressView = null;
        }
    }

    /** The strip's own delegate: a tab tap slides the pager onto that box. */
    private class BoxTabsDelegate implements ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate {

        @Override
        public void onPageSelected(int pageId, boolean forward) {
            // The strip has already moved its indicator onto the tab; the pager follows it there,
            // and its landing reads that box (the tap's own clock is getManualScrollDuration).
            if (pager != null) {
                pager.scrollToPosition(pageId);
            } else {
                onPagerLanded(pageId);
            }
        }

        /** A second tap on the tab in front of the reader: the box's list goes back to its top. */
        @Override
        public void onSamePageSelected() {
            BoxPage page = pages[currentTab];
            if (page != null && page.listView != null) {
                page.listView.smoothScrollToPosition(0);
            }
        }

        @Override
        public void onPageScrolled(float progress) {
            // The pager owns the clock here, so the strip follows it (followStripWithPager);
            // upstream's pages are the ones that follow this callback.
        }
    }

    private class MessageAdapter extends RecyclerListView.SelectionAdapter {

        private final BoxPage page;

        MessageAdapter(BoxPage page) {
            this.page = page;
        }

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
                String text = page.stateUi != null
                        ? SessionUi.text(holder.itemView.getContext(), page.stateUi).toString()
                        : "还没有私信";
                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                return;
            }
            TopicSummary topic = page.items.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            // The title is the row's name (topicRow keeps it) and its line is the site's own excerpt
            // when it has one, or a plain description of the private message when it does not -
            // never the "last poster · reply count" pairing, which reads as a reply that never
            // happened. The unread badge stays the site's own count (replies to a PM we started are
            // unread too). Only fields the list already carries are used: nothing is read for a row.
            DialogCell.CustomDialog row = DiscourseRows.topicRow(topic, false);
            row.message = rowSummary(topic);
            cell.setDialog(row);
            // `setDialog` clears the row's avatar receiver, so the author's photo goes in after it.
            // The description above names that same author, so the face and the line agree.
            TLRPC.User user = page.authors.get(position);
            ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(user);
            if (avatar != null) {
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null, user, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (page.stateUi != null) {
                return TYPE_STATE;
            }
            return page.empty ? TYPE_NOTE : TYPE_MESSAGE;
        }

        @Override
        public int getItemCount() {
            if (page.stateUi != null) {
                return 1;
            }
            return page.empty ? 1 : page.items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (page.stateUi != null) {
                return SessionUi.hasAction(page.stateUi);
            }
            return !page.empty && !(holder.itemView instanceof TextInfoPrivacyCell);
        }
    }
}
