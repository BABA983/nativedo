package io.github.baba983.nativedo.topic;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.RecyclerListViewScroller;

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
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
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
public class TopicListFragment extends BaseFragment
        implements NativeDoMainTabsActivity.TabPage, FactorAnimator.Target {

    /** The tag item: it opens the site's tags, and its icon is upstream's own tag filter. */
    static final int TAG_ITEM = 2;

    /**
     * The item that drops the tag filter. It is in the action bar rather than behind the tag dialog
     * because dropping a filter is one tap, not a dialog and a row.
     */
    static final int CLEAR_TAG_ITEM = 3;

    /**
     * The bar's search item: what the field collapses into, and the way back into the search from
     * the collapsed list. Upstream's own id for it is 0 (DialogsActivity:3220).
     */
    static final int SEARCH_ITEM = 0;

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

    /** The site's filterable latest list; the home tab's own feed. */
    private static final String FEED_LATEST = "latest";
    /** The home overflow's new-topics entrance: `/new.json`, no category or tag filter. */
    private static final String FEED_NEW = "new";
    /** The home overflow's unread entrance: `/unread.json`, no category or tag filter. */
    private static final String FEED_UNREAD = "unread";

    /** The strip's own height, as upstream's chat list gives it (36 + 7 + 7). */
    private static final int TAB_STRIP_HEIGHT_DP = 50;

    /** The strip's side margin, as upstream's floating panel has it. */
    private static final int TAB_STRIP_MARGIN_DP = 4;

    /** DialogsActivity's own animator ids for the three factors this screen also animates (303-305). */
    private static final int ANIMATOR_ID_SEARCH_VISIBLE = 1;
    private static final int ANIMATOR_ID_SHADOW_VISIBLE = 4;
    private static final int ANIMATOR_ID_SEARCH_BUTTON_VISIBLE = 5;

    private FrameLayout contentStack;
    /**
     * DialogsActivity's own pair of pages (500, 4052-4110): the page in front and the one that
     * slides in. Only these two views move; the feed itself lives in {@link #feedCache}, so a
     * category that comes back finds its items and the reader's place in them.
     */
    private ViewPage[] viewPages;
    /** DialogsActivity's own tab-slide state and constants (722-737). */
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private float additionalOffset;
    private boolean backAnimation;
    private int maximumVelocity;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private android.view.VelocityTracker swipeVelocityTracker;
    /**
     * DialogsActivity's own page-settle interpolator (730-733): the quintic ease-out the chat
     * list's filter pages come to rest on.
     */
    private static final android.view.animation.Interpolator PAGES_INTERPOLATOR = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };
    /** One feed per category tab, as the reader left it: its items, its place, its state. */
    private final android.util.SparseArray<CategoryFeed> feedCache = new android.util.SparseArray<>();
    private FilterTabsView tabsView;
    /** The strip's blur source; the recorded content is the list it floats over. */
    private io.github.baba983.nativedo.app.GlassPanelHost glassHost;
    /** DialogsActivity.ContentView's own blurBounds (888) and actionBarDefaultPaint (566). */
    private final Rect blurBounds = new Rect();
    private final Paint actionBarDefaultPaint = new Paint();
    /**
     * The day/night switch recolours the strip, the blur source and the rows in place. The
     * fragment's own {@link ThemeDescription}s also reach {@link #updateColors()}; this listener
     * keeps the recolor working for the notification alone (the appearance page's switch), the
     * same double wiring upstream keeps.
     */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
                updateColors();
            };

    /**
     * Re-reads every colour this screen paints from the theme - the way DialogsActivity's own
     * theme delegate does it (12022-12033): the header's band, the bar's glass, the strip, the
     * search field, the pill, the floating button and the rows, all in place. Called from the
     * global theme notification and from the shell's theme animation, which applies this
     * fragment's {@link ThemeDescription}s.
     */
    private void updateColors() {
        if (this.glassHost != null) {
            this.glassHost.updateColors();
        }
        if (this.tabsView != null) {
            this.tabsView.updateColors();
        }
        if (this.searchField != null) {
            this.searchField.updateColors();
        }
        if (this.searchPanel != null) {
            this.searchPanel.updateColors();
        }
        if (this.newTopicsPill != null) {
            // The service paints are shared and the cell reads them at draw time
            // (ChatActionCell.drawBackground, 3319-3321); a redraw is the whole recolor.
            this.newTopicsPill.invalidate();
        }
        // The floating button's colours are set once in its own updateColors
        // (FragmentFloatingButton:150), the way DialogsActivity's delegate refreshes its
        // floatingButton3 (12077).
        if (this.startTopicButton != null) {
            this.startTopicButton.updateColors();
        }
        // DialogsActivity's theme delegate rebinds its attached DialogCells
        // (12022-12033); the same rows live here, so the same rebind.
        if (viewPages != null) {
            for (ViewPage page : viewPages) {
                refreshPageRows(page);
                // The shimmer reads the theme colours at draw time (FlickerLoadingView's
                // updateColors is its own onDraw); a redraw is its recolor.
                if (page.progressView != null) {
                    page.progressView.invalidate();
                }
            }
        }
        actionBarDefaultPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (this.actionBar != null) {
            this.actionBar.updateColors();
            this.actionBar.invalidate();
        }
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    private FragmentFloatingButton startTopicButton;
    private org.telegram.ui.ActionBar.ActionBarMenuItem tagItem;
    private org.telegram.ui.ActionBar.ActionBarMenuItem clearTagItem;
    /** Upstream's overflow item: the three-dot menu this screen's actions live in. */
    private org.telegram.ui.ActionBar.ActionBarMenuItem optionsItem;
    /** The bar's search icon, shown while the field is collapsed (checkUi_itemSearchVisibility, 14119). */
    private org.telegram.ui.ActionBar.ActionBarMenuItem searchBarItem;
    /** The page in front's list; the working alias of {@code viewPages[0].listView}. */
    private RecyclerListView listView;
    /**
     * The "N new topics" pill: the chat's own floating date view, a {@link ChatActionCell} with
     * custom text. ChatActivity builds its floating date exactly this way (7043-7100), so the
     * pill is the service-message capsule the action bar's date uses - background and text from
     * {@code key_paint_chatActionBackground} / {@code key_paint_chatActionText}, the same paints
     * upstream picks when there is no chat wallpaper.
     */
    private ChatActionCell newTopicsPill;
    /** ChatActivity's own floatingDateAnimation (13612-13624): one 150 ms alpha set, tag-guarded. */
    private AnimatorSet newTopicsPillAnimation;
    /** The pill's feed from the message bus, detached when the screen is destroyed. */
    private MessageBusHandle incomingTopicsObserver;
    /**
     * The reader's own finished-topic mark, while this screen is alive to re-bind a row for it
     * ({@link TopicReadMarks#addListener}): the mark lands when the topic screen is destroyed,
     * which is before this screen is resumed, so a rebind that waited for the resume would keep
     * the badge the reader has just cleared.
     */
    private kotlin.jvm.functions.Function1<Long, kotlin.Unit> readMarkListener;
    /** What the pill currently counts; nulls name no category, so all of them count. */
    private IncomingTopics incomingTopics = new IncomingTopics();
    /**
     * DialogsActivity's scrollYOffset (345): how far the top panel is collapsed, in [-48dp, 0].
     * The list's own position is what sets it (ContentView.dispatchDraw, 957-1006).
     */
    private float scrollYOffset;
    /** DialogsActivity's invalidateScrollY (348): recompute the offset once per draw after a scroll. */
    private boolean invalidateScrollY = true;
    /**
     * The offset the current category slide started from. The slide's own progress moves the
     * header from it to the offset the page coming to the front keeps (see
     * {@link #followSlideWithPanelOffset()}), so the field and the strip travel with the swipe
     * instead of jumping when the pair swaps at the end.
     */
    private float slideStartScrollYOffset;
    /** DialogsActivity's animatorShadowVisible (313, 4605-4606): the shadow under the header's edge. */
    private final BoolAnimator animatorShadowVisible = new BoolAnimator(ANIMATOR_ID_SHADOW_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    /** DialogsActivity's animatorSearchVisible (307, 7464): the search's own 350 ms factor. The
     * field's slot, the strip's departure, the bar's items and the search capsule all read it;
     * searchAnimationProgress is the 200/180 ms animation of the two sides themselves. */
    private final BoolAnimator animatorSearchVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    /** DialogsActivity's animatorSearchButtonVisible (317, 14054): the bar's search icon's factor. */
    private final BoolAnimator animatorSearchButtonVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_BUTTON_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    /** Upstream's own search field, the one 12.10.5's chat list shows instead of a bar icon. */
    private org.telegram.ui.Components.FragmentSearchField searchField;
    /** The search pager's home; the pager itself is built on the first search. */
    private FrameLayout searchContainer;
    private io.github.baba983.nativedo.search.SearchPanel searchPanel;
    /** Whether the search is open; it is what the list and the shell's strip stand aside for. */
    private boolean searching;
    /** 0 at the list, 1 in the search: drives the strip, the field's glass and the floating button. */
    private float searchAnimationProgress;
    private android.animation.AnimatorSet searchAnimator;
    /** Whether the site answered any categories, so the strip has tabs to show. */
    private boolean stripHasTabs;
    /** The site's top-level categories, in its own order; a subcategory is not a tab of its own. */
    private final List<CategorySummary> categories = new ArrayList<>();
    /** The site's tags, in its own order, read once for the tag dialog. */
    private final List<Tag> tags = new ArrayList<>();
    /** The tag the list is filtered by, or null while it is not filtered by one. */
    private Tag selectedTag;
    /**
     * The feed this screen is: the site's filterable latest list, or one of the home overflow's
     * two unfiltered entrances (`/new.json`, `/unread.json`). A mode that is not {@link #FEED_LATEST}
     * carries no category strip and no tag filter - the site's routes for it are not filterable.
     */
    private String feedMode = FEED_LATEST;
    private AppContainer container;
    /** The tab whose feed the page in front shows, so a re-selection is not a reload. */
    private int selectedTabId = ALL_TAB_ID;

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
        feedMode = arguments == null ? FEED_LATEST : arguments.getString("feed", FEED_LATEST);
        final boolean filteredFeed = isFilteredFeed();
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        additionNavigationBarHeight = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;

        // The bar floats over the list the way DialogsActivity's own bar does: it is kept out of
        // the container (3527-3529 setAddToContainer(false) / setCastShadows(false) /
        // setClipContent(true)) and handed to this screen's frame, which paints its own glass and
        // its own shadow (5425, 1091).
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);
        // The title lives in the bar's own container, as DialogsActivity gives its own bar
        // (3172): the search fades and shrinks that container while it takes the bar over.
        actionBar.setUseContainerForTitles();
        actionBar.setTitle("linux.do");
        // The page carries its own bar: inside the tab host the bar belongs to the page's screen,
        // and the strip (a sibling of the pager) draws over the pages, never under them.
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == TAG_ITEM) {
                    pickTag();
                } else if (id == CLEAR_TAG_ITEM) {
                    selectTag(null);
                }
            }
        });
        // The bar's search item (DialogsActivity:3220-3236): the field takes over the screen when
        // it is tapped, and it is what the bar shows once the list has scrolled the field away
        // (checkUi_itemSearchVisibility, 14119-14128). Upstream adds it before the other items.
        searchBarItem = actionBar.createMenu().addItem(
                SEARCH_ITEM, org.telegram.messenger.R.drawable.outline_header_search);
        searchBarItem.setContentDescription(context.getString(R.string.search_hint));
        searchBarItem.setOnClickListener(v -> openSearchFromBar());
        // The tag filter is a filter, not a second strip: the list's own space belongs to the topics
        // (the topic list's strip is categories), so this is one item whose dialog holds the tags.
        // The unfiltered feeds (new/unread) have no such routes on the site, so no such item.
        if (filteredFeed) {
            tagItem = actionBar.createMenu().addItem(TAG_ITEM, org.telegram.messenger.R.drawable.menu_tag_filter);
            clearTagItem = actionBar.createMenu().addItem(CLEAR_TAG_ITEM, org.telegram.messenger.R.drawable.msg_close);
        }
        // The options item goes last, so it is the rightmost - upstream's chat list puts its own
        // three-dot menu at that edge with the same id and icon.
        optionsItem = actionBar.createMenu().addItem(OPTIONS_ITEM, org.telegram.messenger.R.drawable.ic_ab_other);
        optionsItem.setContentDescription("更多");
        optionsItem.setOnClickListener(v -> showItemOptions());
        optionsItem.setOnLongClickListener(v -> {
            showItemOptions();
            return true;
        });
        updateSearchBarItem();

        // The bar's own measured height, status bar included: this screen's frame starts at the
        // top of the screen now that the bar is a child of it, so every offset below counts from
        // there (DialogsActivity lays its children against actionBar.getMeasuredHeight(), 1148).
        final int barHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
        final int searchFieldHeight = AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
        // DialogsActivity reads its fling ceiling off the ViewConfiguration (3208); the settle of
        // a category swipe does the same.
        maximumVelocity = android.view.ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        // The two pages the list slides between (DialogsActivity's own pair, 4052-4110): each
        // carries its own list, its own adapter and its own empty state; the feed cache behind
        // them keeps every category's items and the reader's place in them.
        contentStack = new FrameLayout(context);
        viewPages = new ViewPage[2];
        for (int index = 0; index < viewPages.length; index++) {
            viewPages[index] = createViewPage(context);
            contentStack.addView(viewPages[index], LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[index].setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        }
        selectCurrentPage(viewPages[0]);
        viewPages[1].selectedType = viewPages[0].selectedType;

        // The frame, and the pages on their own: the pair is what leaves while search is open
        // (showSearch's viewPages[0] animators) and it is what the panels' blur source records.
        ContentView frame = new ContentView(context);
        frame.addView(contentStack, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // The search pager's home, under the strip and the bar the way DialogsActivity adds it
        // under its own (4713: its own child index is below the filter strip's and the bar's).
        // It is built on the first search; until then it is the empty frame that holds its place
        // in the draw order. Its own top is the bar's height: the pager's layout counts from the
        // bar's lower edge, as the whole screen's did before the bar was this screen's own.
        searchContainer = new FrameLayout(context);
        searchContainer.setVisibility(View.GONE);
        frame.addView(searchContainer, LayoutHelper.createFrameMarginPx(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP,
                0, barHeight, 0, 0));

        tabsView = new FilterTabsView(context, resourceProvider);
        tabsView.setDelegate(new CategoryTabsDelegate());
        // The strip carries the all-topics folder from the first frame (buildTabs adds it before
        // the site's categories can land), so it is never a band of nothing: loadCategories builds
        // that tab and shows it, and the site's answer only adds to it.
        tabsView.setVisibility(View.GONE);

        // The strip is the floating glass capsule 12.10.5 presents its own filter tabs as:
        // DialogsActivity gives it the top panel's blur background, radius 18, padding 6.666, a
        // 7dp inner padding and 4dp side margins (5151-5155), over a list that scrolls under it.
        // The source records the page pair, so a slide between categories is under the glass too.
        glassHost = new io.github.baba983.nativedo.app.GlassPanelHost(contentStack, frame);
        tabsView.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));
        BlurredBackgroundDrawable stripBackground = glassHost.createPanel(
                tabsView, BlurredBackgroundProviderImpl.topPanel(resourceProvider));
        stripBackground.setRadius(AndroidUtilities.dp(18));
        stripBackground.setPadding(AndroidUtilities.dp(6.666f));
        tabsView.setBlurredBackground(stripBackground);

        // The strip sits in the frame rather than in the list side: it slides with the scroll on
        // its own (updateContextViewPosition, 6560-6596), it does not scale or move with the list.
        // Its top is the bar plus one field's height, the slot the field itself occupies
        // (DialogsActivity's onLayout, 1149-1151). The margins are pixels: the bar's height is
        // measured, not a dp value.
        frame.addView(tabsView, LayoutHelper.createFrameMarginPx(
                LayoutHelper.MATCH_PARENT, TAB_STRIP_HEIGHT_DP, Gravity.TOP,
                AndroidUtilities.dp(TAB_STRIP_MARGIN_DP), barHeight + searchFieldHeight,
                AndroidUtilities.dp(TAB_STRIP_MARGIN_DP), 0));

        // The bar itself, above the strip: the frame paints the glass behind it and behind the
        // field (DialogsActivity adds it between the two, 5331-5338, and gives its background up
        // to the frame at 5425).
        actionBar.setDrawBlurBackground(frame);
        // DialogsActivity's own bar carries no scrim colour of its own - its createActionBar
        // (3101-3196) never sets a background, so ActionBar.dispatchDraw's own blur rect
        // (2272-2279) stays off and the frame's band is the header. BaseFragment.createActionBar
        // leaves this bar with key_actionBarDefault (435), which would draw that rect with a
        // colour nothing re-reads on a theme switch: the band would keep painting the old theme
        // while everything under it recolours. Drop it here, where the bar hands the band to the
        // frame, so the frame's actionBarDefaultPaint is the header, as upstream's is.
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Upstream's own search field: DialogsActivity's chat list shows this instead of a bar
        // icon (3220-3250, field at 3279-3300), in the same frame (48dp wide margins 7/7 at
        // 5337-5339, its top set against the bar's own measured height at 1148-1160). Tapping it
        // is what opens the search; its close button is what leaves it.
        searchField = new org.telegram.ui.Components.FragmentSearchField(context, resourceProvider) {
            // DialogsActivity's own field (3281-3286): a field the list has scrolled away is
            // not there to be touched.
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && getAlpha() < 0.25f) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        searchField.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4),
                AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        searchField.setPivotX(0);
        searchField.setPivotY(0);
        searchField.setupBlurredBackground(glassHost.createPanel(
                searchField, BlurredBackgroundProviderImpl.topPanel(resourceProvider)));
        searchField.setHintText("", false);
        // The hint is the TextView's own, which is what DialogsActivity's field carries
        // (checkUi_searchFieldHint, 14055-14062): the animated hint drawable that
        // FragmentSearchField.setHintText feeds does not paint on this screen at rest.
        searchField.editText.setHint(context.getString(R.string.search_hint));
        searchField.editText.setContentDescription(context.getString(R.string.search_hint));
        searchField.setCloseButtonOnClickListener(this::closeSearch);
        // Opening on the field's own focus, the way upstream's chat list opens its search
        // (DialogsActivity's field focus listener at 3311-3315): a tap hands the field focus and
        // the keyboard, and that focus is the same gesture. The first keystroke opens it too, for
        // a field that somehow has focus without the search being up.
        searchField.editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                openSearch();
            }
        });
        searchField.editText.setOnClickListener(view -> openSearch());
        searchField.editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    openSearch();
                }
                if (searching && searchPanel != null) {
                    searchPanel.search(s.toString());
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        // DialogsActivity's own field layout: one bar's height down the frame plus its own 2dp
        // offset (1148-1160, pixels there too); the rest offset of getSearchFieldAdditionOffset
        // (1624-1626) is carried by the draw pass, which is also where the field follows the
        // scroll.
        frame.addView(searchField, LayoutHelper.createFrameMarginPx(
                LayoutHelper.MATCH_PARENT, org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT,
                Gravity.TOP, AndroidUtilities.dp(7), barHeight + AndroidUtilities.dp(2),
                AndroidUtilities.dp(7), 0));
        // A fetch that failed leaves a page's list empty, which reads the same as a site with
        // nothing to show. Each page carries its own state view (EmptyTextProgressView, built
        // with the page) that says which it is, and a tap on it asks the site again.
        startTopicButton = new FragmentFloatingButton(context, resourceProvider);
        startTopicButton.setImageResource(org.telegram.messenger.R.drawable.filled_fab_compose_32);
        startTopicButton.setContentDescription("发新帖");
        startTopicButton.setOnClickListener(v -> presentNewTopic());
        frame.addView(startTopicButton, FragmentFloatingButton.createDefaultLayoutParams());
        // The chat's own floating date view, carrying the reader's count instead of a date
        // (ChatActivity:7043-7100): a ChatActionCell whose background is the service message's
        // paint, so the pill is the capsule Telegram shows under the action bar. It floats over
        // the rows and rides the header's collapse on its own translation - it never takes a band
        // in the list's layout.
        //
        // Upstream's floating date is also not reporting isFloating(): that flag is read only by
        // drawBackground, and it multiplies the capsule's alpha by three quarters
        // (ChatActionCell:3496-3505). A chat can afford the translucency because a wallpaper sits
        // behind the date; this list has none, and at three quarters the first row's white title
        // showed through the night capsule (the collision on the device). The floating position is
        // the layout and the translation below, not that flag, so the capsule draws at the
        // provider's full alpha while the pill still floats over the rows.
        newTopicsPill = new ChatActionCell(context, false, new PillThemeProvider()) {
            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    invalidate();
                }
                super.setTranslationY(translationY);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getAlpha() == 0) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (getAlpha() == 0) {
                    return false;
                }
                return super.onTouchEvent(event);
            }

            // ChatActivity's own override (7093-7096): an alpha of zero is what takes the view
            // out, so the fade is the only visibility state there is.
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        };
        newTopicsPill.setAlpha(0.0f);
        newTopicsPill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        newTopicsPill.setInvalidateColors(true);
        newTopicsPill.setOnActionClickListener(view -> onNewTopicsTapped());
        frame.addView(newTopicsPill, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        fragmentView = frame;
        org.telegram.ui.Components.Bulletin.addDelegate(this, new org.telegram.ui.Components.Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                int bottom = navigationBarHeight + additionNavigationBarHeight;
                if (fragmentView != null && startTopicButton != null && startTopicButton.getVisibility() == View.VISIBLE) {
                    bottom = Math.max(bottom, fragmentView.getHeight() - (int) startTopicButton.getY() + AndroidUtilities.dp(4));
                }
                return bottom;
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
        actionBar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> checkUi_contentTop());
        container = NativeDo.container(getParentActivity());
        // A theme switch that happens while a screen is pushed over this one clears the views
        // below (upstream's own animation), so a second createView has to let go of the previous
        // view's listener: otherwise every switch leaves a collector holding a dead view.
        if (incomingTopicsObserver != null) {
            incomingTopicsObserver.close();
            incomingTopicsObserver = null;
        }
        // The topic screen's finished-mark lands while this screen is behind it (its destroy runs
        // before this screen's resume), so the row is re-bound from the mark itself.
        if (readMarkListener != null) {
            TopicReadMarks.INSTANCE.removeListener(readMarkListener);
        }
        readMarkListener = topicId -> {
            refreshPageRows(currentPage());
            return kotlin.Unit.INSTANCE;
        };
        TopicReadMarks.INSTANCE.addListener(readMarkListener);
        incomingTopicsObserver = container.getMessageBusSession().observeIncomingTopics(topics -> {
            this.incomingTopics = topics;
            updateNewTopicsPill();
        });
        // The colour the header's own glass is read through (DialogsActivity:5381), re-read by the
        // theme observer below.
        actionBarDefaultPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        // The strip's glass and the strip itself capture theme colours; the day/night switch tells
        // this screen the same way it tells the shell's own strip.
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        checkUi_contentTop();
        checkUi_floatingButton();
        updateTagActions();
        // The page in front reads its category; the pair starts on the all-topics tab. A new/unread
        // feed is the same screen with the site's own unfiltered route, so it builds no strip and
        // no tag list.
        preparePage(viewPages[0], viewPages[0].selectedType, false);
        if (filteredFeed) {
            loadCategories();
            loadTags();
        }
        return fragmentView;
    }

    /**
     * DialogsActivity's own theme descriptions, for this screen's surfaces (12041-12180): the
     * frame's header band is a paint the theme animation writes the new colour into (12136),
     * and the bar's items, title and selector travel with it (12122-12134). The delegate
     * recolours the rest of the page in place. The shell's theme animation reaches these through
     * {@code NativeDoMainTabsActivity.getThemeDescriptions()}, which forwards to its pages the
     * way {@code ViewPagerActivity}'s does (241-250).
     */
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, actionBarDefaultPaint,
                null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR,
                null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR,
                null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR,
                null, null, null, null, Theme.key_actionBarDefaultSelector));

        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate,
                Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate,
                Theme.key_dialogBackground));
        return themeDescriptions;
    }

    /**
     * One of the pair of pages the category list slides between, shaped after upstream's
     * {@code DialogsActivity.ViewPage} (360): its own list, its own scroller, its own state.
     */
    public class ViewPage extends FrameLayout {

        public RecyclerListView listView;
        public LinearLayoutManager layoutManager;
        public RecyclerListViewScroller scroller;
        public EmptyTextProgressView emptyView;
        /** DialogsActivity's own ViewPage.progressView (373, 4059-4062): the chat-list shimmer. */
        public FlickerLoadingView progressView;
        public TopicListAdapter adapter;
        /** The tab whose feed this page shows (DialogsActivity's own selectedType). */
        public int selectedType = ALL_TAB_ID;
        /** The feed in front of this page, out of {@link #feedCache}. */
        public CategoryFeed feed;

        public ViewPage(Context context) {
            super(context);
        }

        /**
         * DialogsActivity.ViewPage.setTranslationX (434-455): a page's own movement is where the
         * slide is noticed. Upstream re-points the strip's indicator from here while the release
         * settle is animating the pair (446-453); this screen's header offset is what follows the
         * same progress, so the field and the strip travel with the pair instead of jumping when
         * it swaps. Without the indicator re-point the strip only moves while the finger is down
         * and freezes at the release point for the whole settle, then jumps on its last frame.
         */
        @Override
        public void setTranslationX(float translationX) {
            if (getTranslationX() != translationX) {
                super.setTranslationX(translationX);
                if (tabsAnimationInProgress && viewPages[0] == this) {
                    float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                    tabsView.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                }
                followSlideWithPanelOffset();
            }
        }
    }

    /** One category's feed as the reader left it: its items, its place, its loading state. */
    private static class CategoryFeed {

        final int tabId;
        final List<TopicSummary> topics = new ArrayList<>();
        /** One author per row, kept so the avatar download can refresh the row it belongs to. */
        final List<TLRPC.User> authors = new ArrayList<>();
        boolean loaded;
        boolean loading;
        boolean failed;
        /** Whether the rows on screen came from the offline cache rather than the site. */
        boolean fromCache;
        /** The page number the next page's route is asked for; the first answer is page 0. */
        int nextPage = 1;
        /** Whether the last answer carried a {@code more_topics_url}: another page follows. */
        boolean hasMore;
        boolean loadingMore;
        boolean moreFailed;
        /** Where the reader was: the first visible row and its offset under the list's top. */
        int scrollPosition;
        int scrollOffset;
        /** Bumped by every read, so an answer for a feed that moved on is dropped. */
        int generation;

        CategoryFeed(int tabId) {
            this.tabId = tabId;
        }
    }

    /**
     * One page and everything it owns, built the way upstream builds each {@code ViewPage}
     * (4052-4110, its list listener at 4470-4610): list, layout manager, scroller, row adapter,
     * and the state view that stands in while its feed is being read or could not be read.
     */
    private ViewPage createViewPage(Context context) {
        final ViewPage page = new ViewPage(context);
        page.adapter = new TopicListAdapter(page);
        // DialogsActivity's own shimmer (4059-4064): the chat list's rows drawn while it holds
        // none, as the list's empty view, so it is up only until the first answer lands. It is
        // added before the list, the way upstream adds its own.
        page.progressView = new FlickerLoadingView(context);
        page.progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        page.progressView.setVisibility(View.GONE);
        page.addView(page.progressView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        page.listView = new RecyclerListView(context);
        page.listView.setLayoutManager(page.layoutManager = new LinearLayoutManager(context) {
            private boolean lastDragging;

            /**
             * DialogsActivity's own layout manager (4149-4160): when the finger comes up, a
             * partly collapsed panel either snaps open or away, or the fling is let through.
             */
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                boolean isDragging = page.listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;
                if (isDragging != lastDragging) {
                    lastDragging = isDragging;
                    if (!isDragging) {
                        if (checkAutoscrollToStories(page)) {
                            return 0;
                        }
                    }
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        });
        page.listView.setAdapter(page.adapter);
        page.listView.setVerticalScrollBarEnabled(false);
        // DialogsActivity's own empty-view wiring (4064, 4695): while the adapter has no rows the
        // list falls back to the shimmer, with upstream's own alpha animation. See
        // updatePageState for the failed page, where the shimmer stands aside for the sentence.
        page.listView.setEmptyView(page.progressView);
        page.listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        // The list keeps the bar, the field and the strip as padding, the way DialogsActivity's
        // own list does (2065-2095): the first row starts under the strip and the rows scroll
        // beneath it. checkUi_listPadding(page) owns the whole padding - the header's resting
        // height at the top and the navigation-bar/strip space at the bottom, so the last row
        // stays reachable above the glass tabs.
        page.listView.setClipToPadding(false);
        checkUi_listPadding(page);
        page.listView.setOnItemClickListener((view, position) -> {
            if (page.feed != null && position >= 0 && position < page.feed.topics.size()) {
                presentTopic(page.feed.topics.get(position));
            }
        });
        // DialogsActivity's own scroller (4066), which the snap rides the list with.
        page.scroller = new RecyclerListViewScroller(page.listView);
        // DialogsActivity's list listener (4470-4610): the floating button leaves while the reader
        // scrolls down and comes back when they scroll up, the top panel follows the same scroll,
        // and a stopped scroll snaps a half-collapsed panel.
        page.listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int prevPosition;
            private int prevTop;
            private boolean scrollUpdated;
            private boolean scrollingManually;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (page != currentPage()) {
                    // The incoming page is only moving because the finger is; the snap and the
                    // floating button belong to the page the reader is leaving.
                    return;
                }
                scrollingManually = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // DialogsActivity's own listener (4475-4479): a drag takes the keyboard away
                    // from an empty field, so it cannot sit over the list it is scrolling.
                    if (searchField != null && searchField.editText.getText().length() == 0
                            && searchField.editText.hasFocus()) {
                        AndroidUtilities.hideKeyboard(searchField.editText);
                        searchField.editText.clearFocus();
                    }
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // DialogsActivity's list listener (4505-4506): the snap of the panel's own
                    // half-open state.
                    checkAutoscrollToStories(page);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // DialogsActivity's own listener (4521): a scroll is where the next page is asked
                // for. DialogsActivity's onScrolled (4522-4524): the draw pass recomputes the top
                // panel's offset from the list's own position, and the frame is the view whose
                // draw pass that is - a child's own scroll does not redraw it.
                checkListLoad(page);
                invalidateScrollY = true;
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
                int firstVisiblePosition = RecyclerView.NO_POSITION;
                int firstViewTop = 0;
                if (recyclerView.getChildCount() > 0) {
                    firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager())
                            .findFirstVisibleItemPosition();
                    if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                        RecyclerView.ViewHolder holder =
                                recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition);
                        firstViewTop = holder != null ? holder.itemView.getTop() : 0;
                    }
                }
                if (firstVisiblePosition != RecyclerView.NO_POSITION && startTopicButton != null) {
                    boolean goingDown;
                    boolean changed = true;
                    if (prevPosition == firstVisiblePosition) {
                        final int topDelta = prevTop - firstViewTop;
                        goingDown = firstViewTop < prevTop;
                        changed = Math.abs(topDelta) > 1;
                    } else {
                        goingDown = firstVisiblePosition > prevPosition;
                    }
                    if (changed && scrollUpdated && (goingDown || scrollingManually)) {
                        startTopicButton.setButtonVisible(!goingDown, true);
                    }
                    prevPosition = firstVisiblePosition;
                    prevTop = firstViewTop;
                    scrollUpdated = true;
                    // DialogsActivity's onScrolled (4605-4606): the header's shadow is up while a
                    // row has gone under the header's lower edge.
                    final boolean shadowVisible = !(firstVisiblePosition <= 0
                            && (firstViewTop - scrollYOffset + AndroidUtilities.dp(5)) >= page.listView.getPaddingTop());
                    animatorShadowVisible.setValue(shadowVisible, true);
                }
            }
        });
        // The page's own state: the failure text with a tap that asks the site again. While the
        // feed has not arrived at all, the shimmer above stands in for its rows.
        page.emptyView = new EmptyTextProgressView(context);
        page.emptyView.setVisibility(View.GONE);
        // The view consumes touches in its own constructor; the tap that retries has to get through.
        page.emptyView.setOnTouchListener(null);
        page.emptyView.setOnClickListener(view -> {
            if (page.feed != null) {
                page.feed.failed = false;
                requestFeed(page, page.feed);
            }
        });
        page.addView(page.listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        page.addView(page.emptyView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        return page;
    }

    /** The page in front; the pair's other page is the one a slide is bringing in. */
    private ViewPage currentPage() {
        return viewPages == null || viewPages.length == 0 ? null : viewPages[0];
    }

    /** The front page changed: the alias the rest of the screen reads and the tab follow it. */
    private void selectCurrentPage(ViewPage page) {
        viewPages[0] = page;
        listView = page.listView;
        selectedTabId = page.selectedType;
    }

    /** Whether this screen is the filterable latest list (as opposed to new/unread). */
    private boolean isFilteredFeed() {
        return FEED_LATEST.equals(feedMode);
    }

    /**
     * Reads one page of the feed this screen is in: the filterable `latest` list (with the
     * reader's chosen category and tag), or one of the site's own unfiltered routes. One call
     * site, because which route that is is the mode's business and not the paging's.
     */
    private void loadFeed(
            AppContainer container,
            int tabId,
            int pageNumber,
            kotlin.jvm.functions.Function1<io.github.baba983.nativedo.core.model.TopicListPage, kotlin.Unit> onLoaded,
            kotlin.jvm.functions.Function1<Throwable, kotlin.Unit> onFailed) {
        if (FEED_NEW.equals(feedMode)) {
            DiscourseLoader.INSTANCE.newTopics(container, pageNumber, onLoaded, onFailed);
        } else if (FEED_UNREAD.equals(feedMode)) {
            DiscourseLoader.INSTANCE.unreadTopics(container, pageNumber, onLoaded, onFailed);
        } else {
            DiscourseLoader.INSTANCE.feed(container, categoryOf(tabId), selectedTag, pageNumber, onLoaded, onFailed);
        }
    }

    private CategoryFeed feedFor(int tabId) {
        CategoryFeed feed = feedCache.get(tabId);
        if (feed == null) {
            feed = new CategoryFeed(tabId);
            feedCache.put(tabId, feed);
        }
        return feed;
    }

    /**
     * Binds {@code page} to the tab's feed - its items and its state - and starts the read when
     * the feed has none yet. {@code restoreScroll} is for a page that is arriving: it goes back
     * to where the reader was in that category, which is what coming back means.
     */
    private void preparePage(ViewPage page, int tabId, boolean restoreScroll) {
        final CategoryFeed feed = feedFor(tabId);
        page.selectedType = tabId;
        page.feed = feed;
        page.adapter.notifyDataSetChanged();
        if (restoreScroll) {
            page.layoutManager.scrollToPositionWithOffset(feed.scrollPosition, feed.scrollOffset);
        }
        updatePageState(page);
        if (!feed.loaded && !feed.loading && !feed.failed) {
            requestFeed(page, feed);
        }
    }

    /**
     * Reads {@code feed}'s first page from the site. The page that asked may not be the one showing
     * the feed when the site answers - a slide can move on first - so the answer is written into
     * the feed and the page is bound again only if it still shows that feed. The answer's
     * {@code topic_list.more_topics_url} is what says another page exists.
     */
    private void requestFeed(ViewPage page, CategoryFeed feed) {
        final int generation = ++feed.generation;
        feed.loading = true;
        feed.failed = false;
        // A first page restarts the paging: whatever was loaded behind it is a different list.
        feed.loadingMore = false;
        feed.moreFailed = false;
        feed.hasMore = false;
        feed.nextPage = 1;
        updatePageState(page);
        container = NativeDo.container(getParentActivity());
        loadFeed(container, feed.tabId, 0,
                loadedPage -> {
                    if (feed.generation != generation) {
                        return kotlin.Unit.INSTANCE;
                    }
                    final boolean fromCache = loadedPage.getFromCache();
                    final List<TopicSummary> loaded = loadedPage.getTopics();
                    feed.loading = false;
                    feed.loaded = true;
                    feed.failed = false;
                    feed.fromCache = fromCache;
                    feed.topics.clear();
                    feed.topics.addAll(loaded);
                    feed.hasMore = hasMoreTopics(loadedPage);
                    feed.authors.clear();
                    for (TopicSummary topic : loaded) {
                        // The site has caught up with a topic this device had only reported: the
                        // feed is the only thing the row is drawn from again.
                        TopicReadMarks.INSTANCE.siteAgrees(topic.getId(), topic.getUnreadCount());
                        feed.authors.add(DiscourseRows.topicAuthor(topic, container.getBaseUrl()));
                    }
                    if (page.feed == feed) {
                        page.adapter.notifyDataSetChanged();
                        updatePageState(page);
                    }
                    if (fromCache) {
                        showCachedFeedNotice(page, feed);
                    }
                    // A new feed is a new list: the header's offset is read from what the list
                    // now holds.
                    invalidateScrollY = true;
                    if (fragmentView != null) {
                        fragmentView.invalidate();
                    }
                    fetchAvatars(feed);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (feed.generation != generation) {
                        return kotlin.Unit.INSTANCE;
                    }
                    feed.loading = false;
                    feed.failed = true;
                    FileLog.e("topic list failed", error);
                    if (page.feed == feed) {
                        updatePageState(page);
                    }
                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * One more page of {@code feed}, appended where the reader is reading it. The site's
     * {@code more_topics_url} is only a flag here - the route and the page number it belongs to
     * are already known - and the page it names is asked for by number, the way Discourse's own
     * client pages ({@code /latest.json?page=1}, the contract's own route). One read at a time:
     * {@code loadingMore} and the generation are the guard, a duplicate topic id never doubles a
     * row, and an answer without a {@code more_topics_url} ends the list.
     */
    private void requestMore(ViewPage page, CategoryFeed feed) {
        final int generation = feed.generation;
        final int pageNumber = feed.nextPage;
        feed.loadingMore = true;
        feed.moreFailed = false;
        page.adapter.notifyDataSetChanged();
        loadFeed(container, feed.tabId, pageNumber,
                loadedPage -> {
                    if (feed.generation != generation) {
                        return kotlin.Unit.INSTANCE;
                    }
                    final boolean fromCache = loadedPage.getFromCache();
                    feed.loadingMore = false;
                    feed.nextPage = pageNumber + 1;
                    feed.hasMore = hasMoreTopics(loadedPage);
                    // A page appended from the cache keeps the list flagged as cached: writes that
                    // assume the site's permissions must stay suppressed until a full read lands.
                    feed.fromCache = feed.fromCache || fromCache;
                    for (TopicSummary topic : loadedPage.getTopics()) {
                        if (hasTopic(feed, topic.getId())) {
                            continue;
                        }
                        TopicReadMarks.INSTANCE.siteAgrees(topic.getId(), topic.getUnreadCount());
                        feed.topics.add(topic);
                        feed.authors.add(DiscourseRows.topicAuthor(topic, container.getBaseUrl()));
                    }
                    if (page.feed == feed) {
                        page.adapter.notifyDataSetChanged();
                    }
                    updateOfflineMarker();
                    fetchAvatars(feed);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    if (feed.generation != generation) {
                        return kotlin.Unit.INSTANCE;
                    }
                    feed.loadingMore = false;
                    feed.moreFailed = true;
                    FileLog.e("topic list page failed", error);
                    if (page.feed == feed) {
                        page.adapter.notifyDataSetChanged();
                    }
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** Whether the site said another page follows this one ({@code more_topics_url} present). */
    private static boolean hasMoreTopics(io.github.baba983.nativedo.core.model.TopicListPage page) {
        String more = page.getMoreTopicsUrl();
        return more != null && !more.isEmpty();
    }

    /**
     * DialogsActivity.checkListLoad (7794-7850): the next page is read when the last row the
     * reader can see is inside the feed's last ten, and never while the pair is sliding (the
     * incoming page's position is not the reader's yet) or while a read is already running. The
     * page is the one that scrolled, as upstream passes its own ViewPage.
     */
    private void checkListLoad(ViewPage page) {
        if (page == null || page.feed == null || page.layoutManager == null) {
            return;
        }
        if (isPageSlideInProgress() || searching) {
            return;
        }
        final CategoryFeed feed = page.feed;
        if (!feed.loaded || feed.loading || feed.loadingMore || feed.moreFailed || !feed.hasMore) {
            return;
        }
        final int lastVisibleItem = page.layoutManager.findLastVisibleItemPosition();
        if (lastVisibleItem != RecyclerView.NO_POSITION
                && lastVisibleItem >= feed.topics.size() - 10) {
            requestMore(page, feed);
        }
    }

    /** Whether a topic id is already one of the feed's rows, so a page cannot double it. */
    private static boolean hasTopic(CategoryFeed feed, long topicId) {
        for (TopicSummary topic : feed.topics) {
            if (topic.getId() == topicId) {
                return true;
            }
        }
        return false;
    }

    /** Re-reads the page in front's feed, for a read the reader asked for. */
    private void refreshCurrentFeed() {
        final ViewPage page = currentPage();
        if (page == null || page.feed == null) {
            return;
        }
        requestFeed(page, page.feed);
    }

    /** Keeps where the reader is in the page's feed, so coming back finds them there. */
    private void savePageScroll(ViewPage page) {
        if (page == null || page.feed == null || page.listView == null || page.layoutManager == null) {
            return;
        }
        final int position = page.layoutManager.findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        page.feed.scrollPosition = position;
        final View first = page.layoutManager.findViewByPosition(position);
        page.feed.scrollOffset = first == null ? 0 : first.getTop() - page.listView.getPaddingTop();
    }

    /**
     * The list's own offline signal: the site could not be reached and the rows on screen are the
     * last answer kept for this reader. Upstream's Bulletin carries the retry explicitly, so a reader
     * who does not think to pull the list has a way to ask again; the read it starts clears the
     * cached flag the moment the site answers.
     */
    private void showCachedFeedNotice(ViewPage page, CategoryFeed feed) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.chats_infotip,
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            R.string.topic_list_offline),
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            R.string.topic_list_offline_retry),
                    org.telegram.ui.Components.Bulletin.DURATION_PROLONG,
                    () -> {
                        requestFeed(page, feed);
                    }).show();
        } catch (Exception bulletinError) {
            FileLog.e(bulletinError);
        }
    }

    /**
     * Whether a write must be suppressed because the list is showing cached rows whose permissions
     * the site has not confirmed since. Says so in passing and refuses; a live read clears the flag.
     */
    private boolean denyWriteWhileOffline() {
        final ViewPage page = currentPage();
        if (page == null || page.feed == null || !page.feed.fromCache) {
            return false;
        }
        try {
            org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(
                    org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                            R.string.topic_offline_readonly)).show();
        } catch (Exception bulletinError) {
            FileLog.e(bulletinError);
        }
        return true;
    }

    /**
     * The list's persistent offline marker: while the feed in front came from the cache, the bar's
     * own subtitle says so and keeps saying so (the Bulletin only says it once). A live read clears
     * it. The Bulletin still carries the explicit retry for the screen the reader is on.
     */
    private void updateOfflineMarker() {
        if (actionBar == null) {
            return;
        }
        final ViewPage page = currentPage();
        final boolean cached = page != null && page.feed != null && page.feed.fromCache;
        actionBar.setSubtitle(cached
                ? org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                        R.string.offline_indicator)
                : null);
    }

    /**
     * The state a page draws while its feed is not in: upstream's shimmer while the page has no
     * feed yet (the list's empty view, so a kept feed shows nothing), the failure sentence when
     * the read did not arrive, or nothing once the feed is there. A loaded-but-empty feed is the
     * list's own empty state - the shimmer is not a stand-in for "nothing", it is a stand-in for
     * "not yet".
     */
    private void updatePageState(ViewPage page) {
        if (page == null || page.emptyView == null || page.feed == null) {
            return;
        }
        // The bar's own offline marker follows whatever feed is in front, so a kept (cached) feed
        // still says so after a tab switch or a return to the screen.
        updateOfflineMarker();
        final CategoryFeed feed = page.feed;
        if (feed.loaded) {
            page.emptyView.setVisibility(View.GONE);
            if (page.adapter.getItemCount() == 0) {
                // A feed that arrived and held nothing: no shimmer stands for "nothing". A feed
                // that arrived with rows is left to the list's own empty view, which fades the
                // shimmer out the way upstream's does.
                page.listView.setEmptyView(null);
                page.progressView.setVisibility(View.GONE);
            }
            return;
        }
        if (feed.failed) {
            // A failure is a sentence with a tap, not shimmering rows (DialogsActivity's own
            // fallback for a list that cannot be read, 9300-9306).
            page.listView.setEmptyView(null);
            page.progressView.setVisibility(View.GONE);
            page.emptyView.setVisibility(View.VISIBLE);
            page.emptyView.setText(org.telegram.messenger.ApplicationLoader.applicationContext.getString(
                    R.string.topic_list_failed));
            page.emptyView.showTextView();
            return;
        }
        // No feed yet: DialogsActivity's own view for the same moment (setEmptyView, 4695).
        page.listView.setEmptyView(page.progressView);
        page.progressView.setVisibility(View.VISIBLE);
        page.emptyView.setVisibility(View.GONE);
    }

    /**
     * Re-binds a page's attached rows, the way DialogsActivity's theme delegate does with its
     * own {@code DialogCell}s (12022-12033): the day/night switch recolours rows in place
     * instead of the views being built again.
     */
    private void refreshPageRows(ViewPage page) {
        if (page == null || page.listView == null) {
            return;
        }
        for (int index = 0; index < page.listView.getChildCount(); index++) {
            final View child = page.listView.getChildAt(index);
            if (child instanceof DialogCell) {
                final int position = page.listView.getChildAdapterPosition(child);
                if (position != RecyclerView.NO_POSITION) {
                    bindRow((DialogCell) child, page, position);
                }
            } else if (child instanceof MoreFooterCell) {
                // The footer's shimmer reads its colours at draw time (FlickerLoadingView's own
                // updateColors); the retry line's colour is set when it is bound, so the switch
                // re-sets it here.
                ((MoreFooterCell) child).failure.setTextColor(
                        getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            }
        }
    }

    /** One row of a page, bound from that page's feed: what the adapter and the theme both use. */
    private void bindRow(DialogCell cell, ViewPage page, int position) {
        if (page.feed == null || position < 0 || position >= page.feed.topics.size()) {
            return;
        }
        final TopicSummary topic = page.feed.topics.get(position);
        cell.setDialog(DiscourseRows.topicRow(topic, TopicReadMarks.INSTANCE.isReadInFull(topic.getId())));
        // `setDialog` clears the row's avatar receiver, so the author's photo goes in after it.
        // The initials stay as the placeholder until the photo is on disk.
        final ImageLocation avatar = position < page.feed.authors.size()
                ? DiscourseAvatars.INSTANCE.imageLocation(page.feed.authors.get(position))
                : null;
        if (avatar != null) {
            // The row's own drawable (the initials) stays as the placeholder: `setDialog` put it
            // there, and passing it back is what upstream does when it sets a real avatar.
            cell.avatarImage.setImage(
                    avatar, "50_50", cell.avatarImage.getDrawable(), null,
                    page.feed.authors.get(position), 0);
        }
    }

    /** The tag filter moved: every cached feed is a different list now. */
    private void invalidateFeeds() {
        for (int index = 0; index < feedCache.size(); index++) {
            final CategoryFeed feed = feedCache.valueAt(index);
            feed.generation++;
            feed.loaded = false;
            feed.loading = false;
            feed.failed = false;
            feed.hasMore = false;
            feed.loadingMore = false;
            feed.moreFailed = false;
            feed.nextPage = 1;
            feed.topics.clear();
            feed.authors.clear();
            feed.scrollPosition = 0;
            feed.scrollOffset = 0;
        }
    }

    /** The rows of the page in front; the render harness reads what a row would draw from them. */
    java.util.List<TopicSummary> rows() {
        final ViewPage page = currentPage();
        return page == null || page.feed == null ? java.util.Collections.emptyList() : page.feed.topics;
    }

    /** The rows draw the author's initials until the avatar lands, then ask to be re-bound. */
    private void fetchAvatars(CategoryFeed feed) {
        for (int index = 0; index < feed.authors.size(); index++) {
            final TLRPC.User author = feed.authors.get(index);
            final int row = index;
            String template = index < feed.topics.size() ? feed.topics.get(index).getAuthorAvatarTemplate() : null;
            String url = template == null ? null : DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(author)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, author, () -> {
                if (viewPages != null) {
                    for (ViewPage bound : viewPages) {
                        if (bound.feed == feed && bound.adapter != null) {
                            bound.adapter.notifyItemChanged(row);
                        }
                    }
                }
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * The frame upstream's own chat list draws its header through: a {@link SizeNotifierFrameLayout}
     * whose draw pass is where the top panel's collapse happens
     * ({@code DialogsActivity.ContentView}, DialogsActivity:787).
     */
    private class ContentView extends SizeNotifierFrameLayout {

        ContentView(Context context) {
            super(context);
        }

        /** DialogsActivity.ContentView.getActionBarFullHeight (862-877): the bar plus the field. */
        public int getActionBarFullHeight() {
            return (int) (actionBar.getHeight()
                    + AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT)
                    * (1f - searchAnimationProgress));
        }

        /** DialogsActivity.ContentView.getActionBarTop (879-886): the header's own top edge. */
        public int getActionBarTop() {
            return (int) (scrollYOffset * (1f - searchAnimationProgress));
        }

        /* DialogsActivity.ContentView's own tab swipe (1308-1355, 1380-1580): a horizontal drag
         * on the list side moves between the category pages, the strip's indicator following the
         * finger, and the pair settling on the page the gesture pointed at. */

        private int startedTrackingPointerId;
        private int startedTrackingX;
        private int startedTrackingY;
        private android.view.VelocityTracker velocityTracker;

        /**
         * DialogsActivity.ContentView.prepareForMoving (797-825): the incoming page is pointed at
         * the neighbour in that direction and put off the edge the drag is coming from.
         */
        private boolean prepareForMoving(MotionEvent ev, boolean forward) {
            int id = tabsView.getNextPageId(forward);
            if (id < 0) {
                return false;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            maybeStartTracking = false;
            startedTracking = true;
            // The slide starts where the header stands; its frames move it to the incoming page.
            slideStartScrollYOffset = scrollYOffset;
            startedTrackingX = (int) (ev.getX() + additionalOffset);
            actionBar.setEnabled(false);
            tabsView.setEnabled(false);
            viewPages[1].selectedType = id;
            preparePage(viewPages[1], id, true);
            viewPages[1].setVisibility(View.VISIBLE);
            animatingForward = forward;
            if (forward) {
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
            } else {
                viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
            }
            return true;
        }

        /** DialogsActivity.ContentView.checkTabsAnimationInProgress (827-856), scrollbars aside. */
        public boolean checkTabsAnimationInProgress() {
            if (tabsAnimationInProgress) {
                boolean cancel = false;
                if (backAnimation) {
                    if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                        viewPages[0].setTranslationX(0);
                        viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                        cancel = true;
                    }
                } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                    viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                    viewPages[1].setTranslationX(0);
                    cancel = true;
                }
                if (cancel) {
                    if (tabsAnimation != null) {
                        tabsAnimation.cancel();
                        tabsAnimation = null;
                    }
                    tabsAnimationInProgress = false;
                }
                return tabsAnimationInProgress;
            }
            return false;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return checkTabsAnimationInProgress()
                    || tabsView != null && tabsView.isAnimatingIndicator()
                    || onTouchEvent(ev);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (maybeStartTracking && !startedTracking) {
                onTouchEvent(null);
            }
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        /**
         * DialogsActivity.ContentView.onTouchEvent (1363-1583): the drag itself, with upstream's
         * own slop (0.3 cm), pointing (the list's own width), velocity thresholds (3000 to start
         * a fling, 1500 to decide its direction, 3500 to ignore it), the third-of-a-width
         * back-swipe rule and the 150-600 ms settle.
         */
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (getParentLayout() != null
                    && tabsView != null && !tabsView.isEditing()
                    && !searching
                    && !getParentLayout().checkTransitionAnimation()
                    && !getParentLayout().isInPreviewMode()
                    && !getParentLayout().isPreviewOpenAnimationInProgress()
                    && (ev == null || startedTracking
                            || ev.getY() > getActionBarTop() + getActionBarFullHeight())
                    // This client has no Telegram account, so there is no chat-swipe setting to
                    // read: the category pages are always the list's own horizontal gesture, the
                    // half upstream's "swipe to folders" option is set to.
                    && stripHasTabs) {
                if (ev != null) {
                    if (velocityTracker == null) {
                        velocityTracker = android.view.VelocityTracker.obtain();
                    }
                    velocityTracker.addMovement(ev);
                }
                if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
                    startedTracking = true;
                    startedTrackingPointerId = ev.getPointerId(0);
                    startedTrackingX = (int) ev.getX();
                    // A hand on a pair that is still moving: the header continues from where it is.
                    slideStartScrollYOffset = scrollYOffset;
                    if (animatingForward) {
                        if (startedTrackingX < viewPages[0].getMeasuredWidth() + viewPages[0].getTranslationX()) {
                            additionalOffset = viewPages[0].getTranslationX();
                        } else {
                            ViewPage page = viewPages[0];
                            viewPages[0] = viewPages[1];
                            viewPages[1] = page;
                            animatingForward = false;
                            additionalOffset = viewPages[0].getTranslationX();
                            tabsView.selectTabWithId(viewPages[0].selectedType, 1f);
                            tabsView.selectTabWithId(viewPages[1].selectedType, additionalOffset / viewPages[0].getMeasuredWidth());
                            preparePage(viewPages[1], viewPages[1].selectedType, true);
                        }
                    } else {
                        if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                            ViewPage page = viewPages[0];
                            viewPages[0] = viewPages[1];
                            viewPages[1] = page;
                            animatingForward = true;
                            additionalOffset = viewPages[0].getTranslationX();
                            tabsView.selectTabWithId(viewPages[0].selectedType, 1f);
                            tabsView.selectTabWithId(viewPages[1].selectedType, -additionalOffset / viewPages[0].getMeasuredWidth());
                            preparePage(viewPages[1], viewPages[1].selectedType, true);
                        } else {
                            additionalOffset = viewPages[0].getTranslationX();
                        }
                    }
                    tabsAnimation.removeAllListeners();
                    tabsAnimation.cancel();
                    tabsAnimationInProgress = false;
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                    additionalOffset = 0;
                    // A fresh gesture starts the header where it stands.
                    slideStartScrollYOffset = scrollYOffset;
                }
                if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking && tabsView.getVisibility() == View.VISIBLE) {
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    velocityTracker.clear();
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    int dx = (int) (ev.getX() - startedTrackingX + additionalOffset);
                    int dy = Math.abs((int) ev.getY() - startedTrackingY);
                    if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                        if (!prepareForMoving(ev, dx < 0)) {
                            maybeStartTracking = true;
                            startedTracking = false;
                            viewPages[0].setTranslationX(0);
                            viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                            tabsView.selectTabWithId(viewPages[1].selectedType, 0);
                        }
                    }
                    if (maybeStartTracking && !startedTracking) {
                        float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                        int dxLocal = (int) (ev.getX() - startedTrackingX);
                        if (Math.abs(dxLocal) >= touchSlop && Math.abs(dxLocal) > dy) {
                            prepareForMoving(ev, dx < 0);
                        }
                    } else if (startedTracking) {
                        viewPages[0].setTranslationX(dx);
                        if (animatingForward) {
                            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                        } else {
                            viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                        }
                        float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                        tabsView.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                    }
                } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId
                        && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                    float velX;
                    float velY;
                    if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                        velX = velocityTracker.getXVelocity();
                        velY = velocityTracker.getYVelocity();
                        if (!startedTracking) {
                            if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                prepareForMoving(ev, velX < 0);
                            }
                        }
                    } else {
                        velX = 0;
                        velY = 0;
                    }
                    if (startedTracking) {
                        float x = viewPages[0].getX();
                        tabsAnimation = new AnimatorSet();
                        if (additionalOffset != 0) {
                            if (Math.abs(velX) > 1500) {
                                backAnimation = animatingForward ? velX > 0 : velX < 0;
                            } else {
                                if (animatingForward) {
                                    backAnimation = (viewPages[1].getX() > (viewPages[0].getMeasuredWidth() >> 1));
                                } else {
                                    backAnimation = (viewPages[0].getX() < (viewPages[0].getMeasuredWidth() >> 1));
                                }
                            }
                        } else {
                            backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f
                                    && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                        }
                        float dx;
                        if (backAnimation) {
                            dx = Math.abs(x);
                            if (animatingForward) {
                                tabsAnimation.playTogether(
                                        ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                        ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, viewPages[1].getMeasuredWidth())
                                );
                            } else {
                                tabsAnimation.playTogether(
                                        ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                        ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -viewPages[1].getMeasuredWidth())
                                );
                            }
                        } else {
                            dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                            if (animatingForward) {
                                tabsAnimation.playTogether(
                                        ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, -viewPages[0].getMeasuredWidth()),
                                        ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                );
                            } else {
                                tabsAnimation.playTogether(
                                        ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, viewPages[0].getMeasuredWidth()),
                                        ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                );
                            }
                        }
                        tabsAnimation.setInterpolator(PAGES_INTERPOLATOR);

                        int width = getMeasuredWidth();
                        int halfWidth = width / 2;
                        float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                        float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                        velX = Math.abs(velX);
                        int duration;
                        if (velX > 0) {
                            duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                        } else {
                            float pageDelta = dx / getMeasuredWidth();
                            duration = (int) ((pageDelta + 1.0f) * 100.0f);
                        }
                        duration = Math.max(150, Math.min(duration, 600));

                        tabsAnimation.setDuration(duration);
                        tabsAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(android.animation.Animator animator) {
                                tabsAnimation = null;
                                if (!backAnimation) {
                                    swapPagesAtRest();
                                    if (tabsView != null) {
                                        tabsView.selectTabWithId(viewPages[0].selectedType, 1.0f);
                                    }
                                }
                                viewPages[1].setVisibility(View.GONE);
                                // The pair has come to rest: the header takes the offset of the
                                // page that stayed in front (the one that just took it, when the
                                // slide landed).
                                updateScrollYOffsetFromList();
                                tabsAnimationInProgress = false;
                                maybeStartTracking = false;
                                actionBar.setEnabled(true);
                                tabsView.setEnabled(true);
                            }
                        });
                        tabsAnimation.start();
                        tabsAnimationInProgress = true;
                        startedTracking = false;
                    } else {
                        maybeStartTracking = false;
                        actionBar.setEnabled(true);
                        tabsView.setEnabled(true);
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
                return startedTracking;
            }
            return false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            // 1012-1045: the frame paints the header's glass behind the bar and the field; the
            // rect shrinks to the bar alone - and the rows clipped below follow it - as the list
            // is scrolled up. It is also the rectangle the recording is read under, so it is what
            // the source has to record (DialogsActivity's own position list, 14163-14202).
            final int top = getActionBarTop();
            blurBounds.set(0, Math.max(0, top), getMeasuredWidth(),
                    top + getActionBarFullHeight() - AndroidUtilities.dp(2 * searchAnimationProgress));
            // The panels' blur source records the list before the frame draws its children, so a
            // panel samples the rows behind it and never itself.
            glassHost.setSourceRect(blurBounds);
            glassHost.record();
            // DialogsActivity.ContentView.dispatchDraw (957-1006): the offset the top panel follows
            // is recomputed from the list itself, once per draw after a scroll. The search takes
            // the list's place, so the panel rests while it is up (onSearchExpand, 3343-3345), and
            // a pair that is sliding owns the offset frame by frame (followSlideWithPanelOffset).
            if (invalidateScrollY && !searching && !isPageSlideInProgress()) {
                invalidateScrollY = false;
                updateScrollYOffsetFromList();
            }
            // 1081-1087: the field follows the offset and travels into the bar as the search takes
            // the screen; checkUi_searchFieldVisibility (14036-14056) fades it out over the same
            // distance and hands the bar its search icon.
            final float panelOffset = AndroidUtilities.lerp(scrollYOffset, 0f, searchAnimationProgress);
            updateSearchFieldVisibility();
            if (searchField != null) {
                searchField.setTranslationY(searchFieldTranslation());
            }
            // updateContextViewPosition (6560-6596): the strip follows the same offset, is pushed
            // down by the search capsule's own height as the search opens, and is lifted by the
            // field's rest offset while the field is visible.
            if (tabsView != null) {
                final float searchTabsHeight = searchPanel == null
                        ? 0f : AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_TABS_HEIGHT);
                tabsView.setTranslationY(scrollYOffset
                        + searchTabsHeight * searchAnimationProgress
                        - AndroidUtilities.dp(4) * searchFieldAlpha());
            }
            // The new-topics pill hangs under the strip, so it rides the same offset: it stays
            // where the strip's lower edge is while the header collapses.
            if (newTopicsPill != null) {
                newTopicsPill.setTranslationY(panelOffset);
            }
            // 1012-1045: the frame paints the header's glass behind the bar and the field.
            drawBlurRect(canvas, 0, blurBounds, actionBarDefaultPaint, true);
            super.dispatchDraw(canvas);
            // 1091 + drawHeaderShadow (14284-14314): the soft line under the header's edge.
            drawHeaderShadow(canvas, getActionBarTop() + getActionBarFullHeight());
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == contentStack) {
                // DialogsActivity.ContentView.drawChild (896-901): the list is clipped at the
                // header's lower edge, so a row is never drawn over the bar or the field.
                canvas.save();
                canvas.clipRect(0, getActionBarTop() + getActionBarFullHeight(),
                        getMeasuredWidth(), getMeasuredHeight());
                boolean result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return result;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
            // DialogsActivity.ContentView.drawBlurRect (930-950): the recorded content under the
            // rectangle is what the header is read through; without the blur - a LOW device,
            // upstream's own fallback - the scrim alone is the header.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled()
                    || glassHost == null || !glassHost.hasSource()
                    || !BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
                canvas.drawRect(rectTmp, blurScrimPaint);
                return;
            }
            final boolean isThemeLight = resourceProvider != null
                    ? !resourceProvider.isDark() : !Theme.isCurrentThemeDark();
            final int blurAlpha = isThemeLight ? 216 : org.telegram.ui.ChatActivity.ACTION_BAR_BLUR_ALPHA;
            canvas.save();
            canvas.translate(0, -y);
            glassHost.drawSource(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
            canvas.restore();
            final int oldScrimAlpha = blurScrimPaint.getAlpha();
            blurScrimPaint.setAlpha(blurAlpha);
            canvas.drawRect(rectTmp, blurScrimPaint);
            blurScrimPaint.setAlpha(oldScrimAlpha);
        }
    }

    /**
     * DialogsActivity.ContentView.dispatchDraw (957-1006): the header's offset is read from the
     * list itself - how far its first row has gone past the list's own padding - and clamped to
     * the field's height. The same reading answers for either page of the pair, which is what a
     * slide needs while it moves.
     */
    private float computeScrollYOffset(RecyclerListView view) {
        if (view == null) {
            return 0;
        }
        final int maxScrollYOffset = AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
        RecyclerView.ViewHolder holder = view.findViewHolderForLayoutPosition(0);
        if (holder != null) {
            float visiblePartAfterScroll = view.getPaddingTop() - holder.itemView.getY();
            if (visiblePartAfterScroll >= 0) {
                float newTranslation = -visiblePartAfterScroll;
                if (newTranslation < -maxScrollYOffset) {
                    return -maxScrollYOffset;
                } else if (newTranslation > 0) {
                    return 0;
                }
                return newTranslation;
            }
            return 0;
        } else if (view.getAdapter() == null || view.getAdapter().getItemCount() == 0) {
            // An empty list has no row the panel could stand aside for; upstream's null holder is
            // a first row scrolled out of the list, and an empty feed is not that.
            return 0;
        }
        return -maxScrollYOffset;
    }

    /** The page in front's offset, the way its own draw pass reads it. */
    private void updateScrollYOffsetFromList() {
        if (listView == null || fragmentView == null) {
            return;
        }
        setScrollY(computeScrollYOffset(listView));
    }

    /** The offset a page keeps: its laid-out list's own, or the place {@link #preparePage} put it. */
    private float pagePanelOffset(ViewPage page) {
        if (page == null || page.listView == null) {
            return 0;
        }
        if (page.listView.getChildCount() == 0 && page.feed != null) {
            // Not laid out yet (a page that has just arrived): the place preparePage restored is
            // what the draw pass will read from it once it is.
            if (page.feed.scrollPosition > 0) {
                return -AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
            }
            return MathUtils.clamp(-page.feed.scrollOffset,
                    -AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT), 0f);
        }
        return computeScrollYOffset(page.listView);
    }

    /** Whether the pair is moving between pages right now. */
    private boolean isPageSlideInProgress() {
        return startedTracking || tabsAnimationInProgress
                || (tabsView != null && tabsView.isAnimatingIndicator());
    }

    /**
     * The header follows the page coming to the front while the pair slides: at the slide's
     * progress the offset sits between where the slide started and the offset that page keeps.
     * The field, the strip and the pill read {@link #scrollYOffset}, so they travel with the swipe
     * - the field fades in when the incoming page is at its top - and the swap at the end has
     * nothing left to change.
     *
     * <p>DialogsActivity's switchToCurrentSelectedMode (6827) instead puts the incoming page's
     * list at the offset the header already has, because its lists are in memory when a tab is
     * tapped; a feed here is read over the network and may land at the top after the tap, so the
     * offset is what moves to meet the page instead of the page being moved to meet the offset.</p>
     */
    private void followSlideWithPanelOffset() {
        if (viewPages == null || viewPages[1] == null || viewPages[1].getVisibility() != View.VISIBLE
                || searching || !isPageSlideInProgress()) {
            return;
        }
        final float width = viewPages[0].getMeasuredWidth();
        if (width <= 0) {
            return;
        }
        final float progress = 1f - Math.min(1f, Math.abs(viewPages[1].getTranslationX()) / width);
        setScrollY(AndroidUtilities.lerp(
                slideStartScrollYOffset, pagePanelOffset(viewPages[1]), progress));
    }

    /** DialogsActivity.setScrollY (8918-8940): the one value the header's views read. */
    private void setScrollY(float value) {
        if (fragmentView == null || value == scrollYOffset) {
            return;
        }
        scrollYOffset = value;
        updateSearchFieldVisibility();
        fragmentView.invalidate();
    }

    /**
     * checkUi_searchFieldVisibility (14036-14056): the field is on while the search animator or
     * the list's own offset says so; over the offset's own distance it fades out, and the bar's
     * search icon comes up once it is gone.
     */
    private void updateSearchFieldVisibility() {
        if (searchField == null) {
            return;
        }
        final float alphaByScrollOffset = 1f - MathUtils.clamp(
                -scrollYOffset / (float) AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT),
                0f, 1f);
        final float alpha = Math.max(animatorSearchVisible.getFloatValue(), alphaByScrollOffset);
        searchField.setAlpha(alpha);
        searchField.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
        animatorSearchButtonVisible.setValue(alpha <= 0.01f, true);
    }

    /** The field's own alpha, as updateContextViewPosition (6560-6596) reads it. */
    private float searchFieldAlpha() {
        return searchField != null ? searchField.getAlpha() : 1f;
    }

    /**
     * DialogsActivity.getSearchFieldAdditionOffset (1624-1626): -4dp at rest, the field's own
     * height once the search is up - the bar's slot, which the field takes over.
     */
    private float getSearchFieldAdditionOffset() {
        return -AndroidUtilities.lerp(AndroidUtilities.dp(4),
                AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT),
                animatorSearchVisible.getFloatValue());
    }

    /**
     * ContentView.dispatchDraw's own field translation (1081-1087), which is what the animated
     * search uses: from the rest slot ({@code scrollYOffset - 4dp}) up to the bar itself
     * ({@code -48dp}) as searchAnimationProgress runs. The two ends are the numbers
     * {@link #getSearchFieldAdditionOffset()} calculates, so the settled state reads that helper.
     */
    private float searchFieldTranslation() {
        final float searchFieldHeight = AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
        if (searchAnimationProgress == 0f && !animatorSearchVisible.getValue()) {
            return scrollYOffset + getSearchFieldAdditionOffset();
        }
        return AndroidUtilities.lerp(scrollYOffset - AndroidUtilities.dp(4),
                -searchFieldHeight, searchAnimationProgress);
    }

    /** checkUi_itemSearchVisibility (14119-14128): the bar's search icon is up once the field is gone. */
    private void updateSearchBarItem() {
        FragmentFloatingButton.setAnimatedVisibility(
                searchBarItem, animatorSearchButtonVisible.getFloatValue());
    }

    /**
     * DialogsActivity's search item (3222-3236): the field wakes, takes the query and the keyboard;
     * this is also the only way back into the search while the list has scrolled the field away.
     */
    private void openSearchFromBar() {
        if (searchField == null) {
            return;
        }
        searchField.setVisibility(View.VISIBLE);
        searchField.setAlpha(1f);
        searchField.editText.requestFocus();
        openSearch();
    }

    /**
     * DialogsActivity.checkAutoscrollToStories (5760-5808), the no-stories half: a scroll that
     * stopped with the panel partly collapsed is snapped to fully shown or fully hidden, at half
     * the field's height. The page is the one that scrolled (upstream passes its ViewPage too).
     */
    private boolean checkAutoscrollToStories(ViewPage page) {
        final int scrollY = (int) -scrollYOffset;
        final int actionBarHeight = AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
        if (scrollY != 0 && scrollY != actionBarHeight) {
            if (page == null || page.listView == null || page.scroller == null
                    || !page.listView.canScrollVertically(-1)) {
                return false;
            }
            final int h = AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
            final int s = scrollY;
            if (s < h / 2) {
                page.scroller.smoothScrollBy(-s);
            } else {
                page.scroller.smoothScrollBy(h - s);
            }
            return true;
        }
        return false;
    }

    /** DialogsActivity.drawHeaderShadow (14284-14314): the shadow under the header's lower edge. */
    private void drawHeaderShadow(Canvas canvas, int sy) {
        if (getParentLayout() == null || actionBar == null) {
            return;
        }
        final float headerShadowAlphaBase = animatorShadowVisible.getFloatValue()
                * (1f - searchAnimationProgress) * (1f - searchAnimationProgress);
        if (headerShadowAlphaBase == 0 || sy <= 0) {
            return;
        }
        getParentLayout().drawHeaderShadow(canvas, (int) (255 * headerShadowAlphaBase), sy);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SEARCH_VISIBLE) {
            // DialogsActivity (13844-13851): one factor drives what the bar shows, what the field
            // looks like and where the panels sit.
            checkUi_menuItems();
            updateSearchFieldVisibility();
            checkUi_filterTabsVisible();
            checkUi_searchFiltersVisibility();
            checkUi_searchFieldStyle();
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        } else if (id == ANIMATOR_ID_SHADOW_VISIBLE) {
            // DialogsActivity (13857-13860): the shadow is this screen's own draw pass.
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        } else if (id == ANIMATOR_ID_SEARCH_BUTTON_VISIBLE) {
            updateSearchBarItem();
        }
    }

    /** checkUi_menuItems (14168-14177): the bar's items leave as the search takes the bar over. */
    private void checkUi_menuItems() {
        final float factor = 1f - animatorSearchVisible.getFloatValue();
        FragmentFloatingButton.setAnimatedVisibility(tagItem, factor);
        FragmentFloatingButton.setAnimatedVisibility(clearTagItem,
                selectedTag == null ? 0f : factor);
        FragmentFloatingButton.setAnimatedVisibility(optionsItem, factor);
        updateSearchBarItem();
    }

    /** checkUi_filterTabsVisible (13990-14010): the strip leaves by 1 - the search's factor. */
    private void checkUi_filterTabsVisible() {
        if (tabsView == null) {
            return;
        }
        final float factor = 1f - animatorSearchVisible.getFloatValue();
        tabsView.setAlpha(factor);
        final float s = AndroidUtilities.lerp(0.98f, 1f, factor);
        tabsView.setScaleX(s);
        tabsView.setScaleY(s);
        tabsView.setVisibility(stripHasTabs && factor > 0 ? View.VISIBLE : View.GONE);
    }

    /** checkUi_searchFiltersVisibility (13927-13939): the search's pill capsule arrives by it. */
    private void checkUi_searchFiltersVisibility() {
        if (searchPanel != null) {
            searchPanel.setTabsVisibility(animatorSearchVisible.getFloatValue());
        }
    }

    /** checkUi_searchFieldStyle (14058-14060): the field puts its glass on with the search. */
    private void checkUi_searchFieldStyle() {
        if (searchField != null) {
            searchField.setBlurredBackgroundVisibility(animatorSearchVisible.getFloatValue());
        }
    }

    /**
     * The action bar's own title leaves as the search takes the screen: DialogsActivity's
     * updateStoriesViewAlpha (1659-1694) fades and shrinks the titles container by
     * {@code 1 - clamp(searchAnimationProgress * 2)} - the first half of the search's own
     * animation, around a base at the left of the bar. (There are no stories here; this is the
     * block's action-bar half.)
     */
    private void updateTitlesContainerVisibility() {
        if (actionBar == null || actionBar.getTitlesContainer() == null) {
            return;
        }
        final float factorSearch = MathUtils.clamp(searchAnimationProgress * 2f, 0f, 1f);
        final float containersAlpha = 1f - factorSearch;
        final android.view.View titles = actionBar.getTitlesContainer();
        titles.setPivotY(AndroidUtilities.statusBarHeight);
        titles.setPivotX(AndroidUtilities.dp(20));
        final float s = 0.4f + 0.6f * containersAlpha;
        titles.setScaleY(s);
        titles.setScaleX(s);
        titles.setAlpha(containersAlpha);
        titles.setVisibility(containersAlpha > 0 ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
    }

    @Override
    public void onFragmentDestroy() {
        // The pill's feed, the strip's own theme listener and the reader's read-mark listener do
        // not outlive the screen: a destroyed page leaves no listener behind that would draw into
        // a dead view.
        if (searchPanel != null) {
            searchPanel.destroy();
        }
        if (incomingTopicsObserver != null) {
            incomingTopicsObserver.close();
            incomingTopicsObserver = null;
        }
        if (readMarkListener != null) {
            TopicReadMarks.INSTANCE.removeListener(readMarkListener);
            readMarkListener = null;
        }
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        super.onFragmentDestroy();
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
        if (viewPages != null) {
            for (ViewPage page : viewPages) {
                checkUi_listPadding(page);
            }
        }
    }

    private void checkUi_listPadding(ViewPage page) {
        if (page != null && page.listView != null) {
            // The list's bottom padding keeps the last row reachable above the navigation bar and
            // the glass tabs; the top keeps the bar, the field and the strip as the header's own
            // resting height (the same numbers DialogsActivity's list pads with). The pill is not
            // in this number: it floats over the first row the way the chat's date does over its
            // messages, and the list keeps the place it had.
            final int barHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
            final int topPadding = barHeight + AndroidUtilities.dp(
                    org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT
                            + TAB_STRIP_HEIGHT_DP - 5);
            page.listView.setPadding(0, topPadding, 0,
                    AndroidUtilities.dp(4) + navigationBarHeight + additionNavigationBarHeight);
            // The shimmer's own rows start below the header, the way DialogsActivity keeps its
            // progress view under its top panel (2093-2095).
            if (page.progressView != null) {
                page.progressView.setPaddingTop(topPadding);
            }
        }
    }

    private void checkUi_floatingButton() {
        if (startTopicButton != null) {
            startTopicButton.setTranslationY(-navigationBarHeight - additionFloatingButtonOffset);
        }
    }

    /** Keeps the retry prompt below the page's own action bar, including after a status bar change. */
    private void checkUi_contentTop() {
        final int top = actionBar != null ? actionBar.getMeasuredHeight() : 0;
        if (top <= 0) {
            return;
        }
        if (viewPages != null) {
            for (ViewPage page : viewPages) {
                applyTopMargin(page != null ? page.emptyView : null, top);
            }
        }
        checkUi_pillTop();
    }

    /**
     * The pill hangs under what is above the list: the action bar, the search field's own slot and
     * the category strip while the site's categories are shown. A strip that is not there does not
     * reserve its height. The cell draws its own capsule at its top + 4dp - ChatActivity cancels
     * that same inset when it places the floating date (11968) - so the margin carries the resting
     * gap alone.
     */
    private void checkUi_pillTop() {
        if (newTopicsPill == null || actionBar == null) {
            return;
        }
        int top = actionBar.getMeasuredHeight()
                + AndroidUtilities.dp(org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT);
        if (tabsView != null && tabsView.getVisibility() == View.VISIBLE) {
            top += AndroidUtilities.dp(TAB_STRIP_HEIGHT_DP);
        }
        top += AndroidUtilities.dp(8) - AndroidUtilities.dp(4);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) newTopicsPill.getLayoutParams();
        if (params != null && params.topMargin != top) {
            params.topMargin = top;
            newTopicsPill.setLayoutParams(params);
        }
    }

    /**
     * Draws the incoming count for whichever filter is in front of the reader: 全部 means every
     * category, a category tab only its own. The text goes into the chat's own floating date view
     * and the show/hide is upstream's own animation (ChatActivity:13600-13660).
     */
    private void updateNewTopicsPill() {
        if (newTopicsPill == null) {
            return;
        }
        Integer categoryId = selectedTabId == ALL_TAB_ID ? null : selectedTabId;
        // The unfiltered new/unread feeds are already the answer to "what is new": no pill.
        int count = isFilteredFeed() ? incomingTopics.countForCategory(categoryId) : 0;
        // The search owns the screen while it is open (its pager is where the rows go), so the
        // pill keeps its count but stays off the pager; showSearch re-asks when it closes.
        boolean show = count > 0 && !searching;
        if (show) {
            newTopicsPill.setCustomText(count + " 个新话题");
        }
        setNewTopicsPillVisible(show);
        checkUi_pillTop();
    }

    /**
     * ChatActivity's own floating-date arrival and departure (13600-13660): the pill appears by
     * one 150 ms alpha animator and leaves by the same, with the tag saying whether it is up.
     * Upstream's hide also carries its scrolling hide-dateDelay; there is no scrolling date here,
     * so nothing delays the pill's departure.
     */
    private void setNewTopicsPillVisible(boolean show) {
        if (newTopicsPill == null) {
            return;
        }
        if (show) {
            if (newTopicsPill.getTag() == null) {
                if (newTopicsPillAnimation != null) {
                    newTopicsPillAnimation.cancel();
                }
                newTopicsPill.setTag(1);
                newTopicsPillAnimation = new AnimatorSet();
                newTopicsPillAnimation.setDuration(150);
                newTopicsPillAnimation.playTogether(
                        ObjectAnimator.ofFloat(newTopicsPill, View.ALPHA, 1.0f));
                newTopicsPillAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        if (animation.equals(newTopicsPillAnimation)) {
                            newTopicsPillAnimation = null;
                        }
                    }
                });
                newTopicsPillAnimation.start();
            }
        } else if (newTopicsPill.getTag() != null) {
            newTopicsPill.setTag(null);
            newTopicsPillAnimation = new AnimatorSet();
            newTopicsPillAnimation.setDuration(150);
            newTopicsPillAnimation.playTogether(
                    ObjectAnimator.ofFloat(newTopicsPill, View.ALPHA, 0.0f));
            newTopicsPillAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (animation.equals(newTopicsPillAnimation)) {
                        newTopicsPillAnimation = null;
                    }
                }
            });
            newTopicsPillAnimation.start();
        }
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
        refreshCurrentFeed();
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
        // The site's own new/unread lists: the same topic-list screen, in its unfiltered feed mode.
        io.add(org.telegram.messenger.R.drawable.outline_groups_24, "新话题", this::presentNewTopics);
        io.add(org.telegram.messenger.R.drawable.msg_folders_read, "未读", this::presentUnread);
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
        if (denyWriteWhileOffline()) {
            return;
        }
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

    /** The site's own new-topics list: this screen, opened in its unfiltered new feed mode. */
    private void presentNewTopics() {
        presentFeedMode(FEED_NEW);
    }

    /** The site's own unread list: this screen, opened in its unfiltered unread feed mode. */
    private void presentUnread() {
        presentFeedMode(FEED_UNREAD);
    }

    /**
     * Opens this screen over the current one for one of the site's unfiltered feeds. The mode is
     * carried in the bundle the same way the tab host carries {@code hasMainTabs}, so the pushed
     * screen builds the same rows, shimmer and paging - just against `/new.json` or `/unread.json`
     * and with no category strip or tag filter.
     */
    private void presentFeedMode(String mode) {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            Bundle args = new Bundle();
            args.putString("feed", mode);
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(new TopicListFragment(args));
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
     * The screen is the reader's again after a topic (or the app) was in front of it. Telegram's
     * chat list does not read the site again here - its rows are updated in place
     * (DialogsActivity.onResume, 7029-7036, and onBecomeFullyVisible, 7353) - so neither does this
     * list: the rows on screen are re-bound from what is already in hand, which is what picks up a
     * topic the reader has just finished ({@link TopicReadMarks}, the same mark the topic screen
     * sets) without a request. A read is something the reader asks for: the first load, the retry
     * on a failed page, the new-topics pill's tap, and the day/night switch's own path.
     *
     * <p>A topic read only partway has no local signal on this screen - the feed's own
     * {@code unread_posts} is the site's answer, and the topic screen's floor position is not kept
     * here - so such a row keeps the count the feed gave it until the next read.</p>
     */
    @Override
    public void onParentBecameVisible() {
        refreshPageRows(currentPage());
        // A categories read that failed (a challenge, an outage, a session that was not ready yet)
        // left the strip empty - and the swipe that switches categories is gated on the strip
        // having tabs, so the gesture went with it. The list is in front again, so it is asked
        // once more; the cached answer, when there is one, is already up. A new/unread feed has no
        // strip to fill.
        if (isFilteredFeed() && categories.isEmpty()) {
            loadCategories();
        }
    }

    /** A second tap on the reader's tab: the list goes back to its top. */
    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    /**
     * DialogsActivity.canParentTabsSlide (13601-13629): while the strip has a category to slide
     * to in that direction, a horizontal swipe on the list belongs to the list; the first tab's
     * backward swipe and the last tab's forward swipe are what the root pager takes. A touch on
     * the action bar is always the pager's, upstream says so too.
     */
    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (searching || tabsView != null && tabsView.isEditing()) {
            return false;
        }
        if (ev.getY() < actionBar.getMeasuredHeight()) {
            return true;
        }
        final boolean isFirstTab = tabsView == null || tabsView.getTabsCount() < 2
                || tabsView.getCurrentTabId() == tabsView.getFirstTabId();
        final boolean isLastTab = tabsView == null || tabsView.getTabsCount() < 2
                || tabsView.getCurrentTabId() == tabsView.getLastTabId();
        if (forward) {
            return isLastTab && !isFirstTab;
        }
        return isFirstTab;
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
     * The search opens when the reader touches the field, the way upstream's own chat list opens
     * it: on focus (DialogsActivity's field focus listener at 3311-3315).
     */
    private void openSearch() {
        if (searching) {
            return;
        }
        ensureSearchPanel();
        showSearch(true);
    }

    /** The field's close button and the system back both leave the search. */
    private void closeSearch() {
        if (!searching) {
            return;
        }
        searchField.editText.getText().clear();
        AndroidUtilities.hideKeyboard(searchField.editText);
        searchField.editText.clearFocus();
        showSearch(false);
    }

    /** The pager is built on the first search: its five pages are what the field then drives. */
    private void ensureSearchPanel() {
        if (searchPanel != null || getParentActivity() == null) {
            return;
        }
        searchPanel = new io.github.baba983.nativedo.search.SearchPanel(
                getParentActivity(), resourceProvider, new SearchDelegate());
        searchPanel.setContainer(NativeDo.container(getParentActivity()));
        searchContainer.addView(searchPanel, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /**
     * Opening and closing the search, DialogsActivity's own animation (showSearch's animator set,
     * 7530-7660): the list leaves and the pager arrives - the list to alpha 0 (and 0.95 of its
     * size off a low-performance device), the pager from 1.05 to 1 - over 200ms in and 180ms plus
     * a 20ms delay out, EASE_OUT, with a progress value that drives the field and the strip.
     */
    private void showSearch(boolean show) {
        if (searching == show) {
            return;
        }
        searching = show;
        // DialogsActivity.showSearch (7464): the search's own 350 ms factor runs beside the
        // 200/180 ms animation of the two sides; the field's slot, the strip, the bar's items and
        // the search capsule all read it.
        animatorSearchVisible.setValue(show, true);
        // The pill's count is the reader's own, but while the search pager is up it must not float
        // over it: updateNewTopicsPill reads `searching`, so it hides on open and comes back on
        // close.
        updateNewTopicsPill();
        if (show) {
            // DialogsActivity.onSearchExpand (3343-3345): the top panel is at rest while the
            // search is up, wherever the list had left it.
            invalidateScrollY = false;
            setScrollY(0);
            searchField.setCloseButtonVisible(true);
            // The strip is off while searching: the pager's pill capsule is the panel that floats,
            // and the panel records its own page for the blur.
            glassHost.setRecordingEnabled(false);
            searchPanel.clear();
            if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
                ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity()).setSearchOpen(true);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (searching) {
                    AndroidUtilities.showKeyboard(searchField.editText);
                }
            }, 100);
        } else {
            searchField.setCloseButtonVisible(false);
            glassHost.setRecordingEnabled(true);
            searchPanel.clear();
            // The title was only faded out while the search was up; the feed it names may have
            // moved on behind the search.
            updateTitle();
            // The offset is read from the list again, so the panel comes back where the list
            // actually is (ContentView.dispatchDraw, 957-1006).
            invalidateScrollY = true;
            if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
                ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity()).setSearchOpen(false);
            }
        }
        animateSearch(show);
    }

    private void animateSearch(boolean show) {
        if (searchAnimator != null) {
            searchAnimator.cancel();
            searchAnimator = null;
        }
        boolean budget = org.telegram.messenger.SharedConfig.getDevicePerformanceClass()
                == org.telegram.messenger.SharedConfig.PERFORMANCE_CLASS_LOW
                || !org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_CHAT_SCALE);
        if (show) {
            searchContainer.setVisibility(View.VISIBLE);
            searchContainer.setAlpha(0f);
        } else {
            contentStack.setVisibility(View.VISIBLE);
        }
        AnimatorSet set = new AnimatorSet();
        java.util.ArrayList<android.animation.Animator> animators = new java.util.ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(contentStack, View.ALPHA, show ? 0f : 1f));
        animators.add(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, show ? 1f : 0f));
        if (!budget) {
            animators.add(ObjectAnimator.ofFloat(contentStack, View.SCALE_X, show ? 0.95f : 1f));
            animators.add(ObjectAnimator.ofFloat(contentStack, View.SCALE_Y, show ? 0.95f : 1f));
            animators.add(ObjectAnimator.ofFloat(searchContainer, View.SCALE_X, show ? 1f : 1.05f));
            animators.add(ObjectAnimator.ofFloat(searchContainer, View.SCALE_Y, show ? 1f : 1.05f));
        } else {
            contentStack.setScaleX(1);
            contentStack.setScaleY(1);
            searchContainer.setScaleX(1);
            searchContainer.setScaleY(1);
        }
        ValueAnimator progress = ValueAnimator.ofFloat(searchAnimationProgress, show ? 1f : 0f);
        progress.addUpdateListener(
                animation -> setSearchAnimationProgress((float) animation.getAnimatedValue()));
        animators.add(progress);
        set.playTogether(animators);
        set.setDuration(show ? 200 : 180);
        set.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT);
        if (!show) {
            set.setStartDelay(20);
        }
        final AnimatorSet started = set;
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (searchAnimator != started) {
                    return;
                }
                if (show) {
                    contentStack.setVisibility(View.GONE);
                } else {
                    searchContainer.setVisibility(View.GONE);
                    searchContainer.setAlpha(0f);
                }
                setSearchAnimationProgress(show ? 1f : 0f);
                searchAnimator = null;
            }
        });
        searchAnimator = set;
        set.start();
    }

    /**
     * One value drives the two sides and the title, the way DialogsActivity's
     * searchAnimationProgress does. Everything else that moves with the search - the strip, the
     * search's pill capsule, the field's glass, the bar's items - reads animatorSearchVisible
     * (checkUi_filterTabsVisible 13990-14010, checkUi_searchFiltersVisibility 13927-13947,
     * checkUi_searchFieldStyle 14058, checkUi_menuItems 14168-14177's item half).
     */
    private void setSearchAnimationProgress(float progress) {
        searchAnimationProgress = progress;
        FragmentFloatingButton.setAnimatedVisibility(startTopicButton, 1f - progress);
        updateTitlesContainerVisibility();
        // The field's own alpha and the bar's search icon follow the search the same way they
        // follow the list (checkUi_searchFieldVisibility, 14036-14056), and the strip's own
        // translation reads the field's alpha, so the frame is drawn again.
        updateSearchFieldVisibility();
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // The search is closed by back before the screen behind it is left: true says the stack may
        // pop, false says this screen took the back.
        if (searching) {
            if (invoked) {
                closeSearch();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    /** What a chosen search row asks for: open it, or filter the home list by it. */
    private class SearchDelegate implements io.github.baba983.nativedo.search.SearchPanel.Delegate {

        @Override
        public void openTopic(TopicSummary topic) {
            presentFragment(TopicFragment.forFloor(
                    topic.getId(), topic.getTitle(), topic.getTargetPostNumber(),
                    topic.getAuthorUsername(), topic.getAuthorAvatarTemplate()));
        }

        @Override
        public void openPerson(io.github.baba983.nativedo.core.model.SearchUserSummary person) {
            presentFragment(io.github.baba983.nativedo.profile.ProfileFragment.forUser(
                    person.getUsername(), person.getName()));
        }

        @Override
        public void filterByCategory(CategorySummary category) {
            closeSearch();
            // Search includes children even though the default home strip only lists parents.
            // Keep the selected category's own slug/id so the tap opens its feed, not its parent.
            if (categoryOf(category.getId()) == null) {
                categories.add(category);
                buildTabs();
            }
            // The strip's own selection, completed: this is the same step the reader's tap makes,
            // so the indicator and the page pair animate the same way. A category already in
            // front is read again - the search asked for it, which is a statement of its own.
            if (viewPages[0].selectedType == category.getId()) {
                refreshCurrentFeed();
                return;
            }
            final int position = categoryTabPosition(category.getId());
            if (position >= 0 && tabsView != null && tabsView.getTab(position) != null) {
                tabsView.scrollToTab(tabsView.getTab(position), position);
            }
        }

        @Override
        public void filterByTag(Tag tag) {
            closeSearch();
            selectTag(tag);
        }
    }

    /**
     * The site's categories, one tab each. The site answers the strip over {@code /categories.json};
     * the tabs are built once, and a failure leaves the list on the all-topics feed rather than
     * blocking it - the reader can still read the forum, which is what this screen is for.
     */
    private void loadCategories() {
        // What the last launch read is up first, so the strip is there in the first frame the way
        // upstream's own folder strip is (its folders come from local storage); the site's answer
        // then replaces it.
        publishCategories(container.getCategoryStore().cachedCategories());
        DiscourseLoader.INSTANCE.categories(container,
                loaded -> {
                    publishCategories(loaded);
                    return kotlin.Unit.INSTANCE;
                },
                error -> {
                    FileLog.e("topic list categories failed", error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * The categories the strip is built from: the site's top-level ones, in its own order. It runs
     * twice - the cached list, then the site's answer - so the tabs are cleared first. It builds
     * even with no categories: the all-topics folder is a tab of its own, so the strip stands from
     * the first frame and the site's answer only adds to it.
     */
    private void publishCategories(List<CategorySummary> loaded) {
        categories.clear();
        for (CategorySummary category : loaded) {
            if (category.getParentCategoryId() == null) {
                categories.add(category);
            }
        }
        buildTabs();
    }

    private void buildTabs() {
        tabsView.removeTabs();
        // The all-topics tab is upstream's "default" folder: FilterTabsView.onMeasure builds the
        // position tables (positionToX/Width/Count) only when it finds a default tab, and the
        // selected capsule interpolates between those tables. With none, they stay empty, the
        // indicator width is 0 and the capsule is not drawn at all while it travels - it jumps.
        // Upstream renames the default tab to its own "All chats" string as it measures, so this
        // screen's name for the folder ("全部") is supplied through that string resource (see
        // nativedo/src/main/res/values/strings.xml).
        tabsView.addTab(ALL_TAB_ID, 0, "全部", false, true, false);
        for (CategorySummary category : categories) {
            tabsView.addTab(
                    category.getId(), category.getId(), CategoryLabels.of(category), false, false, false);
        }
        tabsView.finishAddingTabs(false);
        // The all-topics folder is always a tab of the strip, so the strip is never the empty band
        // that would justify hiding it: it is shown from the first frame, before the site's
        // categories land, and stays shown when they never do (the list is still readable).
        stripHasTabs = true;
        checkUi_filterTabsVisible();
        checkUi_pillTop();
    }

    private void presentTopic(TopicSummary topic) {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            // The row already downloaded the author's picture: its user and template go to the
            // topic screen, so the center capsule is bound before the topic is read.
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(TopicFragment.forTopic(topic));
        }
    }

    /**
     * The title for the feed the page in front is showing: the site's latest list, one category,
     * one tag, or the two of them together. The title says which, because a filtered list is a
     * different list.
     */
    private void updateTitle() {
        // While the search is open the title is faded out by the search's own progress
        // (updateTitlesContainerVisibility); its text waits for the fade to bring it back.
        if (searching) {
            return;
        }
        // The unfiltered entrances name themselves: their feed is not the site's latest list.
        if (!isFilteredFeed()) {
            actionBar.setTitle(FEED_UNREAD.equals(feedMode) ? "未读" : "新话题");
            return;
        }
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
        // Every cached category is a different list now; the page in front reads its own again,
        // and the rest read theirs when they are visited again.
        invalidateFeeds();
        final ViewPage page = currentPage();
        if (page != null) {
            preparePage(page, page.selectedType, false);
        }
    }

    /** What the tag filter looks like: the tag in the title, and the way out of it in the bar. */
    private void updateTagActions() {
        // The clear item is up while a tag is in hand and the search has not taken the bar; its
        // alpha and visibility belong to the search fade (checkUi_menuItems).
        checkUi_menuItems();
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

    /** The strip's own position of a tab: the all-topics tab is first, then the site's order. */
    private int categoryTabPosition(int tabId) {
        if (tabId == ALL_TAB_ID) {
            return 0;
        }
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).getId() == tabId) {
                return index + 1;
            }
        }
        return -1;
    }

    private class TopicListAdapter extends RecyclerListView.SelectionAdapter {

        /** One topic row. */
        private static final int VIEW_TYPE_TOPIC = 0;
        /** The page's footer: the loading shimmer, or the tap that reads the page again. */
        private static final int VIEW_TYPE_MORE = 1;

        /** The page this adapter binds rows for; its feed is the data. */
        private final ViewPage page;

        TopicListAdapter(ViewPage page) {
            this.page = page;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_MORE) {
                MoreFooterCell footer = new MoreFooterCell(parent.getContext());
                footer.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(footer);
            }
            DialogCell cell = new DialogCell(null, parent.getContext(), false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == VIEW_TYPE_MORE) {
                bindMoreFooter((MoreFooterCell) holder.itemView, page, page.feed);
                return;
            }
            bindRow((DialogCell) holder.itemView, page, position);
        }

        @Override
        public int getItemViewType(int position) {
            return page.feed != null && position >= page.feed.topics.size()
                    ? VIEW_TYPE_MORE : VIEW_TYPE_TOPIC;
        }

        @Override
        public int getItemCount() {
            if (page.feed == null) {
                return 0;
            }
            return page.feed.topics.size() + (showsMoreFooter(page.feed) ? 1 : 0);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return getItemViewType(holder.getAdapterPosition()) != VIEW_TYPE_MORE
                    || (page.feed != null && page.feed.moreFailed);
        }
    }

    /**
     * DialogsAdapter's own footer while a page is being read (its VIEW_TYPE_FLICKER, 1786-1792):
     * one single-cell {@link FlickerLoadingView} of the chat list's row type. It doubles as the
     * page's retry: a read that did not arrive swaps the shimmer for the sentence and takes the
     * tap itself.
     */
    private class MoreFooterCell extends FrameLayout {

        final FlickerLoadingView shimmer;
        final android.widget.TextView failure;

        MoreFooterCell(Context context) {
            super(context);
            shimmer = new FlickerLoadingView(context);
            shimmer.setIsSingleCell(true);
            shimmer.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
            addView(shimmer, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            failure = new android.widget.TextView(context);
            failure.setGravity(Gravity.CENTER);
            failure.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            failure.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            failure.setText(R.string.topic_list_more_failed);
            failure.setVisibility(GONE);
            // One row's own height, in dp (LayoutHelper's sizes are dp, the cell's own params are
            // pixels), so the line sits where the row it replaces would.
            addView(failure, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, 72, Gravity.CENTER));
        }
    }

    /** Whether the feed has a next page being read, or one that failed to arrive. */
    private static boolean showsMoreFooter(CategoryFeed feed) {
        return feed.hasMore && (feed.loadingMore || feed.moreFailed);
    }

    /** The footer's own state: the shimmer while the page is on its way, the line when it is not. */
    private void bindMoreFooter(MoreFooterCell footer, ViewPage page, CategoryFeed feed) {
        if (feed == null) {
            return;
        }
        footer.shimmer.setVisibility(feed.moreFailed ? View.GONE : View.VISIBLE);
        footer.failure.setVisibility(feed.moreFailed ? View.VISIBLE : View.GONE);
        footer.failure.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        footer.setOnClickListener(feed.moreFailed ? view -> {
            if (feed.moreFailed && !feed.loadingMore) {
                feed.moreFailed = false;
                page.adapter.notifyDataSetChanged();
                requestMore(page, feed);
            }
        } : null);
    }

    /**
     * The strip's own delegate. Upstream's chat list answers the same messages for chat folders;
     * the parts that belong to its folder editing (deleting, reordering, the lock icon) have no
     * meaning over a topic list and are answered with "nothing to do".
     */
    private class CategoryTabsDelegate implements FilterTabsView.FilterTabsViewDelegate {

        @Override
        public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
            if (tab == null || viewPages == null || viewPages[0].selectedType == tab.id) {
                return;
            }
            // DialogsActivity's own tap path (3614-3634): the incoming page is pointed at the
            // tab's feed and put just off the edge; the strip's indicator animation then drives
            // onPageScrolled until the pair swaps.
            selectCategoryTab(tab.id, forward);
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
            // Upstream's own answer (3636-3639): the strip is inert while the search is up.
            return !searching;
        }

        @Override
        public void onPageScrolled(float progress) {
            // DialogsActivity's own slide (3641-3667): the strip's indicator progress moves both
            // pages; at its end the incoming page takes the front.
            if (progress == 1f && viewPages[1].getVisibility() != View.VISIBLE && !searching) {
                return;
            }
            if (animatingForward) {
                viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
            } else {
                viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
            }
            if (progress == 1f) {
                swapPagesAtRest();
            }
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

    /**
     * DialogsActivity's own tab tap (3614-3634): the incoming page is pointed at the tab's feed,
     * put off the edge the slide comes from, and the strip's indicator animation runs (started by
     * the strip itself when the tab was tapped) with each of its frames landing in
     * {@link CategoryTabsDelegate#onPageScrolled}.
     */
    private void selectCategoryTab(int tabId, boolean forward) {
        if (viewPages == null || viewPages[0].selectedType == tabId) {
            return;
        }
        final ViewPage incoming = viewPages[1];
        incoming.selectedType = tabId;
        preparePage(incoming, tabId, true);
        incoming.setVisibility(View.VISIBLE);
        incoming.setTranslationX(viewPages[0].getMeasuredWidth());
        animatingForward = forward;
        // The header travels from where it stands to the incoming page's own offset while the
        // strip's indicator animation runs (followSlideWithPanelOffset).
        slideStartScrollYOffset = scrollYOffset;
        // The tab and the count in front of the reader change with the tap, before the new feed
        // lands; the slide's end re-says them from the page that took the front.
        selectedTabId = tabId;
        updateTitle();
        updateNewTopicsPill();
    }

    /** The slide landed: the incoming page takes the front, the outgoing one goes back to GONE. */
    private void swapPagesAtRest() {
        savePageScroll(viewPages[0]);
        final ViewPage temp = viewPages[0];
        viewPages[0] = viewPages[1];
        viewPages[1] = temp;
        viewPages[1].setVisibility(View.GONE);
        viewPages[1].setTranslationX(0);
        if (tabsView != null) {
            tabsView.stopAnimatingIndicator();
        }
        selectCurrentPage(viewPages[0]);
        animatingForward = true;
        // The header reads its offset from the page in front, which is the page that just arrived:
        // the slide's own end is that page's offset, so nothing moves at the swap. The page that
        // arrived may already stand at its feed's end, so the next page is asked for as upstream
        // does when a filter switch lands (3661).
        updateScrollYOffsetFromList();
        checkListLoad(viewPages[0]);
        invalidateScrollY = true;
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
        updateTitle();
        updateNewTopicsPill();
    }

    /**
     * The service paint the pill draws with, for a screen that has no chat wallpaper.
     *
     * <p>Upstream's floating date is readable because its capsule is a sample of the chat
     * wallpaper: {@link Theme#applyChatServiceMessageColor} puts the wallpaper bitmap in
     * {@code chat_actionBackgroundPaint} and forces its alpha to {@code 0xff}
     * (Theme.java:8749-8759), so the date never shows what passes under it. There is no wallpaper
     * here: the branch that runs leaves the paint the theme's own translucent
     * {@code key_chat_serviceBackground} tint (Theme.java:8766-8773), which is picked to sit on a
     * chat wallpaper. On the day theme that tint still reads over the white window background; on
     * the dark ones it all but vanishes against the window background and the first row's title
     * shines through the pill.</p>
     *
     * <p>This delegate - the paint source {@code ChatActivity.ThemeDelegate} gives its floating
     * date (ChatActionCell.getThemedPaint, 3988-3991) - gives the night capsule the same service
     * colour at the same full alpha the wallpaper branch uses whenever there is no wallpaper to
     * sample. The pill does not report {@code isFloating()}, so {@code drawBackground}'s floating
     * factor (ChatActionCell:3496-3505) never quarters that alpha; the capsule covers the row
     * behind it. A wallpaper gradient present keeps the shared paint untouched, and the day theme
     * keeps its own tint, which its pass on the device checked as correct. The colour is re-read
     * on every draw, so the pill follows the day/night switch in place.</p>
     */
    private static final class PillThemeProvider implements ChatActionCell.ThemeDelegate {

        private final android.graphics.Paint backgroundPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        @Override
        public int getColor(int key) {
            return Theme.getColor(key);
        }

        @Override
        public int getCurrentColor() {
            return Theme.currentColor;
        }

        @Override
        public android.graphics.Paint getPaint(String paintKey) {
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
