package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
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
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.messagebus.IncomingTopics;
import io.github.baba983.nativedo.core.messagebus.MessageBusHandle;
import io.github.baba983.nativedo.core.messagebus.MessageBusSession;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.Tag;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;

/**
 * linux.do's topic list: {@code /latest.json} - or one category's own feed - rendered with
 * upstream's {@link DialogCell} through its own {@link DialogCell.CustomDialog} row type, which
 * exists exactly for rows that are not Telegram chats.
 *
 * <p>The filter over the list is upstream's {@link FilterTabsView}: the control its own chat list
 * filters chat folders with, so a tab strip over a list is the shape upstream already has. One tab
 * per category of the site, ordered the way the site orders them, plus the all-topics tab, and the
 * site's own color as a dot in front of the name - the strip is where a reader reads it.</p>
 */
public class TopicListFragment extends BaseFragment implements NativeDoMainTabsActivity.TabPage {

    /** The search item in the action bar, which opens the search screen. */
    private static final int SEARCH_ITEM = 1;

    /** The tag item: it opens the site's tags, and its icon is upstream's own tag filter. */
    static final int TAG_ITEM = 2;

    /**
     * The item that drops the tag filter. It is in the action bar rather than behind the tag dialog
     * because dropping a filter is one tap, not a dialog and a row.
     */
    static final int CLEAR_TAG_ITEM = 3;

    /**
     * The home's options item: upstream's own overflow item, the id and icon its chat list gives
     * its three-dot menu ({@code DialogsActivity.optionsItem}).
     */
    static final int OPTIONS_ITEM = 4;

    /**
     * The all-topics tab. Upstream's tabs are identified by an int, and the all-topics feed is not
     * a category, so it takes the id no category can have.
     */
    private static final int ALL_TAB_ID = 0;

    /** The strip's own height, as upstream's chat list gives it (36 + 7 + 7). */
    private static final int TAB_STRIP_HEIGHT_DP = 50;

    private LinearLayout contentView;
    private FilterTabsView tabsView;
    private FragmentFloatingButton startTopicButton;
    private org.telegram.ui.ActionBar.ActionBarMenuItem clearTagItem;
    /** Upstream's overflow item: the three-dot menu this screen's actions live in. */
    private org.telegram.ui.ActionBar.ActionBarMenuItem optionsItem;
    private RecyclerListView listView;
    /**
     * The "N new topics" pill: upstream's own rounded action control
     * ({@code ButtonWithCounterView}, the button its story recorder and bottom sheets use), shown
     * when the message bus has counted topics the list does not show yet.
     */
    private ButtonWithCounterView newTopicsPill;
    /** The pill's feed from the message bus, detached when the screen is destroyed. */
    private MessageBusHandle incomingTopicsObserver;
    /** What the pill currently counts; nulls name no category, so all of them count. */
    private IncomingTopics incomingTopics = new IncomingTopics();
    /** Whether the pill is on screen, so the list can start below it instead of under it. */
    private boolean newTopicsPillShown;
    /** What is shown instead of the list when the feed could not be read. */
    private org.telegram.ui.Components.EmptyTextProgressView emptyView;
    private final List<TopicSummary> topics = new ArrayList<>();
    /** One author per row, kept so the avatar download can refresh the row it belongs to. */
    private final List<TLRPC.User> authors = new ArrayList<>();
    /** The site's top-level categories, in its own order; a subcategory is not a tab of its own. */
    private final List<CategorySummary> categories = new ArrayList<>();
    /** The site's tags, in its own order, read once for the tag dialog. */
    private final List<Tag> tags = new ArrayList<>();
    /** The tag the list is filtered by, or null while it is not filtered by one. */
    private Tag selectedTag;
    private AppContainer container;
    /** The tab whose feed the list currently shows, so a re-selection is not a reload. */
    private int selectedTabId = ALL_TAB_ID;
    /** Set once a feed has arrived, so coming back to the screen reloads rather than double-loads. */
    private boolean feedLoaded;

    /** Whether the screen is a page of the tab host, which adds the strip's height to the insets. */
    private boolean hasMainTabs;
    private int additionNavigationBarHeight;
    private int additionFloatingButtonOffset;
    private int navigationBarHeight;

    /** The tab host builds its pages with the bundle that says a strip is under them. */
    public TopicListFragment(Bundle args) {
        super(args);
    }

    public TopicListFragment() {
        super();
    }

    @Override
    protected View createView(Context context) {
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;

        actionBar.setTitle("linux.do");
        // The page carries its own bar: inside the tab host the bar belongs to the page's screen,
        // and the strip (a sibling of the pager) draws over the pages, never under them.
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == SEARCH_ITEM) {
                    presentFragment(new io.github.baba983.nativedo.search.SearchFragment());
                } else if (id == TAG_ITEM) {
                    pickTag();
                } else if (id == CLEAR_TAG_ITEM) {
                    selectTag(null);
                }
            }
        });
        // Upstream's own search item: the icon that opens the field, which the search screen then
        // uses for the query.
        actionBar.createMenu().addItem(SEARCH_ITEM, org.telegram.messenger.R.drawable.outline_header_search);
        // The tag filter is a filter, not a second strip: the list's own space belongs to the topics
        // (the topic list's strip is categories), so this is one item whose dialog holds the tags.
        actionBar.createMenu().addItem(TAG_ITEM, org.telegram.messenger.R.drawable.menu_tag_filter);
        clearTagItem = actionBar.createMenu().addItem(CLEAR_TAG_ITEM, org.telegram.messenger.R.drawable.msg_close);
        // The options item goes last, so it is the rightmost - upstream's chat list puts its own
        // three-dot menu at that edge with the same id and icon.
        optionsItem = actionBar.createMenu().addItem(OPTIONS_ITEM, org.telegram.messenger.R.drawable.ic_ab_other);
        optionsItem.setContentDescription("更多");
        optionsItem.setOnClickListener(v -> showItemOptions());
        optionsItem.setOnLongClickListener(v -> {
            showItemOptions();
            return true;
        });

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new TopicListAdapter());
        listView.setVerticalScrollBarEnabled(false);
        // The list reaches the bottom of the window and its rows scroll behind the glass strip;
        // checkUi_listPadding() keeps the last row reachable above it.
        listView.setClipToPadding(false);
        checkUi_listPadding();
        listView.setOnItemClickListener((view, position) -> {
            TopicSummary topic = topics.get(position);
            presentTopic(topic);
        });

        tabsView = new FilterTabsView(context, resourceProvider);
        tabsView.setDelegate(new CategoryTabsDelegate());
        // A strip with no tabs in it is a band of nothing over the list: it is shown by the tabs
        // arriving, not before them, so a site whose categories could not be read leaves the list
        // the whole screen rather than an empty row above it.
        tabsView.setVisibility(View.GONE);

        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(tabsView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, TAB_STRIP_HEIGHT_DP));
        contentView.addView(listView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1f));

        // Upstream's own floating button, the one its chat list starts a new chat with: this list's
        // one action is to start a topic, so it is the same button in the same place.
        FrameLayout frame = new FrameLayout(context);
        frame.addView(contentView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        // A fetch that failed leaves the list empty, which reads the same as a site with nothing to
        // show. This says which it was, and a tap on it asks the site again.
        emptyView = new org.telegram.ui.Components.EmptyTextProgressView(context);
        emptyView.setText(context.getString(R.string.topic_list_failed));
        emptyView.showTextView();
        emptyView.setVisibility(View.GONE);
        // The view consumes touches in its own constructor; the tap that retries has to get through.
        emptyView.setOnTouchListener(null);
        emptyView.setOnClickListener(view -> loadFeed());
        frame.addView(emptyView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        startTopicButton = new FragmentFloatingButton(context, resourceProvider);
        startTopicButton.setImageResource(org.telegram.messenger.R.drawable.filled_fab_compose_32);
        startTopicButton.setContentDescription("发新帖");
        startTopicButton.setOnClickListener(v -> presentNewTopic());
        frame.addView(startTopicButton, FragmentFloatingButton.createDefaultLayoutParams());
        // Upstream's own pill: the rounded control the story recorder and bottom sheets draw, used
        // here for the count a refreshed list would show. It rides below the action bar and the
        // category strip, centered, so the FAB (bottom right) and the glass tabs stay clear of it.
        newTopicsPill = new ButtonWithCounterView(context, resourceProvider);
        newTopicsPill.setUseWrapContent(true);
        newTopicsPill.setRoundRadius(17);
        newTopicsPill.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        newTopicsPill.setVisibility(View.GONE);
        newTopicsPill.setOnClickListener(view -> onNewTopicsTapped());
        frame.addView(newTopicsPill, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, 34, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        // The page's own action bar sits above the content, the way upstream's tab pages carry
        // theirs; checkUi_contentTop() moves the content below it once it has been measured.
        frame.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = frame;
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        actionBar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> checkUi_contentTop());
        container = NativeDo.container(getParentActivity());
        // A theme switch rebuilds the page's views without destroying the fragment (upstream's
        // animation clears the views of the fragments below the last), so a second createView has
        // to let go of the previous view's listener: otherwise every switch leaves a collector
        // holding a dead view.
        if (incomingTopicsObserver != null) {
            incomingTopicsObserver.close();
            incomingTopicsObserver = null;
        }
        incomingTopicsObserver = container.getMessageBusSession().observeIncomingTopics(topics -> {
            this.incomingTopics = topics;
            updateNewTopicsPill();
        });
        checkUi_contentTop();
        checkUi_floatingButton();
        updateTagActions();
        loadFeed();
        loadCategories();
        loadTags();
        return fragmentView;
    }

    /**
     * The strip's and the navigation bar's height, so the list's own bottom padding lets its rows
     * scroll behind both while the last row is still reachable. Upstream's chat list reads the same
     * two numbers for the same reason.
     */
    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listPadding();
        checkUi_floatingButton();
    }

    private void checkUi_listPadding() {
        if (listView != null) {
            // The pill floats over the top of the list; while it is there the first row starts
            // below it rather than under it.
            int pillOffset = newTopicsPillShown ? AndroidUtilities.dp(50) : 0;
            listView.setPadding(0, AndroidUtilities.dp(4) + pillOffset, 0,
                    AndroidUtilities.dp(4) + navigationBarHeight + additionNavigationBarHeight);
        }
    }

    private void checkUi_floatingButton() {
        if (startTopicButton != null) {
            startTopicButton.setTranslationY(-navigationBarHeight - additionFloatingButtonOffset);
        }
    }

    /** Keeps the page's content below its own action bar, including after a status bar change. */
    private void checkUi_contentTop() {
        final int top = actionBar != null ? actionBar.getMeasuredHeight() : 0;
        if (top <= 0) {
            return;
        }
        applyTopMargin(contentView, top);
        applyTopMargin(emptyView, top);
        checkUi_pillTop();
    }

    /**
     * The pill hangs under what is above the list: the action bar, plus the category strip while
     * the site's categories are shown. A strip that is not there does not reserve its height.
     */
    private void checkUi_pillTop() {
        if (newTopicsPill == null || actionBar == null) {
            return;
        }
        int top = actionBar.getMeasuredHeight();
        if (tabsView != null && tabsView.getVisibility() == View.VISIBLE) {
            top += AndroidUtilities.dp(TAB_STRIP_HEIGHT_DP);
        }
        top += AndroidUtilities.dp(8);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) newTopicsPill.getLayoutParams();
        if (params != null && params.topMargin != top) {
            params.topMargin = top;
            newTopicsPill.setLayoutParams(params);
        }
    }

    /**
     * Draws the incoming count for whichever filter is in front of the reader: 全部 means every
     * category, a category tab only its own. The pill is upstream's animated control, so the text
     * change animates and the show/hide uses upstream's own 150 ms alpha/scale constant.
     */
    private void updateNewTopicsPill() {
        if (newTopicsPill == null) {
            return;
        }
        Integer categoryId = selectedTabId == ALL_TAB_ID ? null : selectedTabId;
        int count = incomingTopics.countForCategory(categoryId);
        boolean show = count > 0;
        if (show) {
            newTopicsPill.setText(count + " 个新话题", true);
        }
        AndroidUtilities.updateViewVisibilityAnimated(newTopicsPill, show, 0.5f, true);
        if (newTopicsPillShown != show) {
            newTopicsPillShown = show;
            // RecyclerView keeps its anchor when the padding changes, so the new space only shows
            // up once the list is re-anchored at its start - done here, and only when the reader
            // is already at the top, so a reader who is scrolled down is not yanked anywhere.
            boolean atTop = listView != null && !listView.canScrollVertically(-1);
            checkUi_listPadding();
            if (atTop) {
                listView.scrollToPosition(0);
            }
        }
        checkUi_pillTop();
    }

    /**
     * The reader asked for the new topics: the list is read again, its top is where they start, and
     * the count they just acted on is no longer news - Discourse's web client resets its tracking
     * state the same way after the list refresh.
     */
    private void onNewTopicsTapped() {
        if (container != null) {
            container.getMessageBusSession().clearIncomingTopics();
        }
        incomingTopics = new IncomingTopics();
        updateNewTopicsPill();
        if (listView != null) {
            listView.scrollToPosition(0);
        }
        loadFeed();
    }

    /**
     * The home's options menu, ported from upstream's {@code DialogsActivity.showItemOptions()}:
     * upstream's own popup anchored on the options item, with the same dim and the same order -
     * the theme switch first, a gap, then the actions. Only the entries with a linux.do meaning
     * are here; upstream's Telegram-only entries (contacts, privacy, communities, archive) are
     * not.
     */
    private void showItemOptions() {
        org.telegram.ui.Components.ItemOptions io =
                org.telegram.ui.Components.ItemOptions.makeOptions(this, optionsItem);
        io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
        io.setDimAlpha(0x08);

        final boolean isCurrentThemeDark = Theme.isCurrentThemeDark();
        io.add(isCurrentThemeDark ? org.telegram.messenger.R.drawable.menu_day_mode_24
                        : org.telegram.messenger.R.drawable.menu_night_mode_24,
                org.telegram.messenger.LocaleController.getString(isCurrentThemeDark
                        ? org.telegram.messenger.R.string.SwitchThemeToDay
                        : org.telegram.messenger.R.string.SwitchThemeToNight), () -> {
                    if (DialogsActivity.switchingTheme) {
                        return;
                    }
                    DialogsActivity.switchingTheme = true;
                    android.content.SharedPreferences preferences = org.telegram.messenger.ApplicationLoader.applicationContext
                            .getSharedPreferences("themeconfig", android.app.Activity.MODE_PRIVATE);
                    String dayThemeName = preferences.getString("lastDayTheme", "Blue");
                    if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                        dayThemeName = "Blue";
                    }
                    String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
                    if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                        nightThemeName = "Dark Blue";
                    }
                    Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
                    if (dayThemeName.equals(nightThemeName)) {
                        if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                            dayThemeName = "Blue";
                        } else {
                            nightThemeName = "Dark Blue";
                        }
                    }

                    boolean toDark;
                    if (toDark = dayThemeName.equals(themeInfo.getKey())) {
                        themeInfo = Theme.getTheme(nightThemeName);
                    } else {
                        themeInfo = Theme.getTheme(dayThemeName);
                    }
                    switchTheme(themeInfo, toDark);
                    Theme.turnOffAutoNight(org.telegram.ui.Components.BulletinFactory.of(this), () -> {
                        presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_NIGHT));
                    });
                });
        io.addGap();
        io.add(org.telegram.messenger.R.drawable.outline_groups_24, "发新帖", this::presentNewTopic);
        io.add(org.telegram.messenger.R.drawable.outline_saved_24, "书签", this::presentBookmarks);
        io.show();
        // Upstream raises its own chat-list menu by this constant; the popup otherwise hangs lower
        // than the icon it belongs to.
        io.setTranslationY(-AndroidUtilities.dp(64));
    }

    /**
     * The menu's theme switch posts the icon's centre, so the host's circular reveal grows out of
     * the menu item the reader just tapped - the same notification and the same position upstream's
     * {@code DialogsActivity.switchTheme} posts.
     */
    private void switchTheme(Theme.ThemeInfo themeInfo, boolean toDark) {
        if (optionsItem == null) {
            return;
        }
        int[] pos = new int[2];
        optionsItem.getLocationInWindow(pos);
        pos[0] += optionsItem.getIconView().getMeasuredWidth() / 2;
        pos[1] += optionsItem.getIconView().getMeasuredHeight() / 2;
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
                themeInfo, false, pos, -1, toDark, null, null, null, true);
    }

    /** The list's one action: the floating button and the menu's own 发新帖 row do the same thing. */
    private void presentNewTopic() {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(NewTopicFragment.create());
        }
    }

    /** The reader's own bookmarks, the list the settings and profile screens already open. */
    private void presentBookmarks() {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(new io.github.baba983.nativedo.bookmarks.BookmarksFragment());
        }
    }

    private static void applyTopMargin(View view, int top) {
        if (view == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (params == null || params.topMargin == top) {
            return;
        }
        params.topMargin = top;
        view.setLayoutParams(params);
    }

    /**
     * A feed that has already been read is read again when the reader comes back to the screen: the
     * site is what knows how much of a topic is still unread, and a topic just read is exactly what
     * changed. The tab host only calls this when the reader returns to the screen (a topic was
     * closed, or the app came back to the front) - a tab switch does not reload anything.
     *
     * <p>This is the reader saying "show me what is there now", so the topics the refresh answers
     * with stop being news. A filter change ({@link #loadFeed()} without the flag) is not a refresh:
     * it is the same site state through a narrower window, so the counts the reader has not seen do
     * not vanish.</p>
     */
    @Override
    public void onParentBecameVisible() {
        if (feedLoaded) {
            loadFeed(true);
        }
    }

    /** A second tap on the reader's tab: the list goes back to its top. */
    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    /** The page reaches the bottom of the window itself; the shell must not reserve the bar. */
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    /**
     * The site's categories, one tab each. The site answers the strip over {@code /categories.json};
     * the tabs are built once, and a failure leaves the list on the all-topics feed rather than
     * blocking it - the reader can still read the forum, which is what this screen is for.
     */
    private void loadCategories() {
        DiscourseLoader.INSTANCE.categories(container,
                loaded -> {
                    categories.clear();
                    for (CategorySummary category : loaded) {
                        if (category.getParentCategoryId() == null) {
                            categories.add(category);
                        }
                    }
                    buildTabs();
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    FileLog.e("topic list categories failed", error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    private void buildTabs() {
        // The all-topics tab is not upstream's "default" tab: upstream renames the default tab to
        // its own "All chats" string while it measures (FilterTabsView.onMeasure), because in its
        // own chat list the default tab *is* that folder. This screen's own name for it is the one
        // that belongs on it.
        tabsView.addTab(ALL_TAB_ID, 0, "全部", false, false, false);
        for (CategorySummary category : categories) {
            tabsView.addTab(
                    category.getId(), category.getId(), CategoryLabels.of(category), false, false, false);
        }
        tabsView.finishAddingTabs(false);
        tabsView.setVisibility(categories.isEmpty() ? View.GONE : View.VISIBLE);
        checkUi_pillTop();
    }

    private void presentTopic(TopicSummary topic) {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(TopicFragment.forTopic(topic.getId(), topic.getTitle()));
        }
    }

    /**
     * The feed for what the reader has chosen: the site's latest list, one category, one tag, or the
     * two of them together. The title says which, because a filtered list is a different list.
     */
    private void loadFeed() {
        loadFeed(false);
    }

    /**
     * @param clearIncomingWhenLoaded whether a successful load is a refresh the new-topics count
     *                               answers to (see {@link #onParentBecameVisible()}).
     */
    private void loadFeed(boolean clearIncomingWhenLoaded) {
        container = NativeDo.container(getParentActivity());
        updateTitle();
        DiscourseLoader.INSTANCE.feed(container, categoryOf(selectedTabId), selectedTag,
                loaded -> showTopics(loaded, clearIncomingWhenLoaded), error -> {
                    FileLog.e("topic list failed", error);
                    showFailure();
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** The feed could not be read: the list stays as it is, and the failure is said out loud. */
    private void showFailure() {
        if (emptyView == null) {
            return;
        }
        emptyView.setText(org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                R.string.topic_list_failed));
        emptyView.showTextView();
        emptyView.setVisibility(View.VISIBLE);
    }

    private void updateTitle() {
        CategorySummary category = categoryOf(selectedTabId);
        StringBuilder title = new StringBuilder("linux.do");
        if (category != null) {
            title.append(" · ").append(category.getName());
        }
        if (selectedTag != null) {
            title.append(" · #").append(selectedTag.getName());
        }
        actionBar.setTitle(title.toString());
    }

    /**
     * The site's tags, for the tag dialog. A failure leaves the item opening nothing, which is what a
     * site that could not be read can offer - the list itself is unaffected.
     */
    private void loadTags() {
        DiscourseLoader.INSTANCE.tags(container,
                loaded -> {
                    tags.clear();
                    tags.addAll(loaded);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    FileLog.e("topic list tags failed", error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** The site's tags as a dialog, the way upstream offers a choice of its own. */
    private void pickTag() {
        if (getParentActivity() == null || tags.isEmpty()) {
            return;
        }
        // The clear entry first, so the tag rows keep one offset whether or not a tag is in hand.
        final boolean hasClear = selectedTag != null;
        final int offset = hasClear ? 1 : 0;
        CharSequence[] items = new CharSequence[tags.size() + offset];
        if (hasClear) {
            items[0] = "清除标签";
        }
        for (int index = 0; index < tags.size(); index++) {
            Tag tag = tags.get(index);
            items[offset + index] = tag.getCount() > 0
                    ? "#" + tag.getName() + " · " + tag.getCount()
                    : "#" + tag.getName();
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("按标签筛选");
        builder.setItems(items, (dialog, which) -> {
            if (hasClear && which == 0) {
                selectTag(null);
                return;
            }
            int index = which - offset;
            if (index >= 0 && index < tags.size()) {
                selectTag(tags.get(index));
            }
        });
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    /** Picks a tag (or drops the one in hand) and reads the list again for it. */
    private void selectTag(Tag tag) {
        selectedTag = tag;
        updateTagActions();
        loadFeed();
    }

    /** What the tag filter looks like: the tag in the title, and the way out of it in the bar. */
    private void updateTagActions() {
        if (clearTagItem != null) {
            clearTagItem.setVisibility(selectedTag == null ? View.GONE : View.VISIBLE);
        }
        updateTitle();
    }

    private CategorySummary categoryOf(int tabId) {
        for (CategorySummary category : categories) {
            if (category.getId() == tabId) {
                return category;
            }
        }
        return null;
    }

    private kotlin.Unit showTopics(List<TopicSummary> loaded, boolean clearIncoming) {
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        topics.clear();
        authors.clear();
        topics.addAll(loaded);
        for (TopicSummary topic : loaded) {
            // The site has caught up with a topic this device had only reported: the feed is the
            // only thing the row is drawn from again.
            TopicReadMarks.INSTANCE.siteAgrees(topic.getId(), topic.getUnreadCount());
        }
        for (TopicSummary topic : loaded) {
            authors.add(DiscourseRows.topicAuthor(topic, container.getBaseUrl()));
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        fetchAvatars();
        feedLoaded = true;
        // A refresh the reader asked for (the screen came back to the front) makes the listed
        // topics the new baseline: what the pill counted has been shown. A filter change does not.
        if (clearIncoming && container != null) {
            container.getMessageBusSession().clearIncomingTopics();
        }
        return kotlin.Unit.INSTANCE;
    }

    /**
     * The pill's feed and nothing else of the screen outlives it: a destroyed page leaves no
     * listener behind that would draw into a dead view.
     */
    @Override
    public void onFragmentDestroy() {
        if (incomingTopicsObserver != null) {
            incomingTopicsObserver.close();
            incomingTopicsObserver = null;
        }
        super.onFragmentDestroy();
    }

    /**
     * The strip's own delegate. Upstream's chat list answers the same messages for chat folders;
     * the parts that belong to its folder editing (deleting, reordering, the lock icon) have no
     * meaning over a topic list and are answered with "nothing to do".
     */
    private class CategoryTabsDelegate implements FilterTabsView.FilterTabsViewDelegate {

        @Override
        public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
            if (tab == null || tab.id == selectedTabId) {
                return;
            }
            selectedTabId = tab.id;
            // The count in front of the reader changes with the filter, before the new feed lands.
            updateNewTopicsPill();
            loadFeed();
        }

        /** A second tap on the tab in front of the reader: the list goes back to its top. */
        @Override
        public void onSamePageSelected() {
            if (listView != null) {
                listView.smoothScrollToPosition(0);
            }
        }

        /**
         * No count rides on a tab: a category's unread count is not in the site's category list, and
         * each row already carries its own badge - a number invented here would be a second answer
         * to the same question.
         */
        @Override
        public int getTabCounter(int tabId) {
            return 0;
        }

        @Override
        public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
            return true;
        }

        @Override
        public boolean canPerformActions() {
            return true;
        }

        @Override
        public void onPageScrolled(float progress) {
        }

        @Override
        public boolean isTabMenuVisible() {
            return false;
        }

        @Override
        public void onDeletePressed(int id) {
        }

        @Override
        public void onPageReorder(int fromId, int toId) {
        }
    }

    /** The rows the list is showing; the render harness reads what a row would draw from them. */
    java.util.List<TopicSummary> rows() {
        return topics;
    }

    /** The rows draw the author's initials until the avatar lands, then ask to be re-bound. */
    private void fetchAvatars() {
        for (int index = 0; index < authors.size(); index++) {
            TLRPC.User author = authors.get(index);
            final int row = index;
            String template = index < topics.size() ? topics.get(index).getAuthorAvatarTemplate() : null;
            String url = template == null ? null : DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(author)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, author, () -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyItemChanged(row);
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private class TopicListAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, parent.getContext(), false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DialogCell cell = (DialogCell) holder.itemView;
            TopicSummary topic = topics.get(position);
            cell.setDialog(DiscourseRows.topicRow(
                    topic, TopicReadMarks.INSTANCE.isReadInFull(topic.getId())));
            // `setDialog` clears the row's avatar receiver, so the author's photo goes in after it.
            // The initials stay as the placeholder until the photo is on disk.
            ImageLocation avatar = position < authors.size()
                    ? DiscourseAvatars.INSTANCE.imageLocation(authors.get(position))
                    : null;
            if (avatar != null) {
                // The row's own drawable (the initials) stays as the placeholder: `setDialog` put it
                // there, and passing it back is what upstream does when it sets a real avatar.
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null,
                        authors.get(position), 0);
            }
        }

        @Override
        public int getItemCount() {
            return topics.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}