package io.github.baba983.nativedo.topic;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
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
public class TopicListFragment extends BaseFragment {

    /** The tag item: it opens the site's tags, and its icon is upstream's own tag filter. */
    static final int TAG_ITEM = 2;

    /**
     * The item that drops the tag filter. It is in the action bar rather than behind the tag dialog
     * because dropping a filter is one tap, not a dialog and a row.
     */
    static final int CLEAR_TAG_ITEM = 3;

    /**
     * The all-topics tab. Upstream's tabs are identified by an int, and the all-topics feed is not
     * a category, so it takes the id no category can have.
     */
    private static final int ALL_TAB_ID = 0;

    /** The strip's own height, as upstream's chat list gives it (36 + 7 + 7). */
    private static final int TAB_STRIP_HEIGHT_DP = 50;

    /** The strip's side margin, as upstream's floating panel has it. */
    private static final int TAB_STRIP_MARGIN_DP = 4;

    /** The strip's top edge: the search field stands above it, 2dp up the screen. */
    private static final int STRIP_TOP_DP = org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT - 2;

    private FrameLayout contentStack;
    private FilterTabsView tabsView;
    /** The strip's blur source; the recorded content is the list it floats over. */
    private io.github.baba983.nativedo.app.GlassPanelHost glassHost;
    /** The day/night switch rebuilds the strip's own colours and the blur source's. */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
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
            };
    private FragmentFloatingButton startTopicButton;
    private org.telegram.ui.ActionBar.ActionBarMenuItem tagItem;
    private org.telegram.ui.ActionBar.ActionBarMenuItem clearTagItem;
    private RecyclerListView listView;
    /** What is shown instead of the list when the feed could not be read. */
    private org.telegram.ui.Components.EmptyTextProgressView emptyView;
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

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("linux.do");
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
        // The tag filter is a filter, not a second strip: the list's own space belongs to the topics
        // (the topic list's strip is categories), so this is one item whose dialog holds the tags.
        tagItem = actionBar.createMenu().addItem(TAG_ITEM, org.telegram.messenger.R.drawable.menu_tag_filter);
        clearTagItem = actionBar.createMenu().addItem(CLEAR_TAG_ITEM, org.telegram.messenger.R.drawable.msg_close);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new TopicListAdapter());
        listView.setVerticalScrollBarEnabled(false);
        // The strip floats over the rows, and the search field stands above the strip: the list
        // keeps both as padding, so its first row starts under the strip and the rows scroll
        // beneath it (DialogsActivity's own list padding carries the field's height and the
        // strip's, 2065-2095).
        listView.setPadding(0,
                AndroidUtilities.dp(4) + AndroidUtilities.dp(STRIP_TOP_DP + TAB_STRIP_HEIGHT_DP - 5),
                0, AndroidUtilities.dp(4));
        // DialogsActivity:4070 - the padding only makes room for the field and the strip; rows
        // scroll through it, so a row is never cut off in a line at the strip's lower edge.
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            TopicSummary topic = topics.get(position);
            presentTopic(topic);
        });
        // DialogsActivity's onScrolled (4526-4549): the floating button leaves while the reader
        // scrolls down and comes back when they scroll up, so it never sits over the last row.
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int prevPosition;
            private int prevTop;
            private boolean scrollUpdated;
            private boolean scrollingManually;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                scrollingManually = newState == RecyclerView.SCROLL_STATE_DRAGGING;
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() == 0 || startTopicButton == null) {
                    return;
                }
                int firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .findFirstVisibleItemPosition();
                if (firstVisiblePosition == RecyclerView.NO_POSITION) {
                    return;
                }
                RecyclerView.ViewHolder holder =
                        recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition);
                int firstViewTop = holder != null ? holder.itemView.getTop() : 0;
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
            }
        });

        // Upstream's own floating button, the one its chat list starts a new chat with: this list's
        // one action is to start a topic, so it is the same button in the same place.
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                // The strip's blur source is recorded before the frame draws its children, so the
                // strip samples the rows behind it and never itself.
                glassHost.record();
                super.dispatchDraw(canvas);
            }
        };

        // The list side, on its own: it is what leaves while search is open (showSearch's
        // viewPages[0] animators), and it is what the strip's blur source records.
        contentStack = new FrameLayout(context);
        contentStack.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frame.addView(contentStack, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        tabsView = new FilterTabsView(context, resourceProvider);
        tabsView.setDelegate(new CategoryTabsDelegate());
        // A strip with no tabs in it is a band of nothing over the list: it is shown by the tabs
        // arriving, not before them, so a site whose categories could not be read leaves the list
        // the whole screen rather than an empty row above it.
        tabsView.setVisibility(View.GONE);

        // The strip is the floating glass capsule 12.10.5 presents its own filter tabs as:
        // DialogsActivity gives it the top panel's blur background, radius 18, padding 6.666, a
        // 7dp inner padding and 4dp side margins (5151-5155), over a list that scrolls under it.
        glassHost = new io.github.baba983.nativedo.app.GlassPanelHost(listView, frame);
        tabsView.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));
        BlurredBackgroundDrawable stripBackground = glassHost.createPanel(
                tabsView, BlurredBackgroundProviderImpl.topPanel(resourceProvider));
        stripBackground.setRadius(AndroidUtilities.dp(18));
        stripBackground.setPadding(AndroidUtilities.dp(6.666f));
        tabsView.setBlurredBackground(stripBackground);

        // The strip sits in the frame rather than in the list side: it fades on its own while
        // search opens, it does not scale or move with the list.
        frame.addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, TAB_STRIP_HEIGHT_DP, Gravity.TOP,
                TAB_STRIP_MARGIN_DP, STRIP_TOP_DP, TAB_STRIP_MARGIN_DP, 0));

        // The search pager's home. It is built on the first search; until then it is the empty
        // frame that holds the field's place in the draw order.
        searchContainer = new FrameLayout(context);
        searchContainer.setVisibility(View.GONE);
        frame.addView(searchContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Upstream's own search field: DialogsActivity's chat list shows this instead of a bar
        // icon (3220-3250, field at 3279-3300), in the same frame (48dp, TOP, 7/-2/7/0 at
        // 5337-5339). Tapping it is what opens the search; its close button is what leaves it.
        searchField = new org.telegram.ui.Components.FragmentSearchField(context, resourceProvider);
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
        frame.addView(searchField, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, org.telegram.ui.DialogsActivity.SEARCH_FIELD_HEIGHT,
                Gravity.TOP, 7, -2, 7, 0));
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
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        startTopicButton = new FragmentFloatingButton(context, resourceProvider);
        startTopicButton.setImageResource(org.telegram.messenger.R.drawable.filled_fab_compose_32);
        startTopicButton.setContentDescription("发新帖");
        startTopicButton.setOnClickListener(v -> {
            if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
                ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                        .present(NewTopicFragment.create());
            }
        });
        frame.addView(startTopicButton, FragmentFloatingButton.createDefaultLayoutParams());
        fragmentView = frame;
        // The strip's glass and the strip itself capture theme colours; the day/night switch tells
        // this screen the same way it tells the shell's own strip.
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        updateTagActions();
        loadFeed();
        loadCategories();
        loadTags();
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
    }

    /**
     * A feed that has already been read is read again when the reader comes back to it: the site is
     * what knows how much of a topic is still unread, and a topic just read is exactly what changed.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (feedLoaded) {
            loadFeed();
        }
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
        if (show) {
            searchField.setCloseButtonVisible(true);
            // The strip is off while searching: the pager's pill capsule is the panel that floats,
            // and the panel records its own page for the blur.
            glassHost.setRecordingEnabled(false);
            searchPanel.clear();
            setActionBarForSearch(true);
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
            setActionBarForSearch(false);
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
     * One value drives everything that is not the two sides themselves, the way DialogsActivity's
     * searchAnimationProgress does: the strip leaves by {@code 1 - searchVisible}
     * (checkUi_filterTabsVisible, 13990-14010), the search's pill tabs arrive from 0.98 of their
     * size (checkUi_searchFiltersVisibility, 13927-13947), the field puts its glass on
     * (checkUi_searchFieldStyle, 14050), and the compose button leaves with the list.
     */
    private void setSearchAnimationProgress(float progress) {
        searchAnimationProgress = progress;
        tabsView.setAlpha(1f - progress);
        updateStripVisibility();
        if (searchPanel != null) {
            searchPanel.setTabsVisibility(progress);
        }
        searchField.setBlurredBackgroundVisibility(progress);
        FragmentFloatingButton.setAnimatedVisibility(startTopicButton, 1f - progress);
    }

    /** The strip is up when the categories arrived and the search has not taken over. */
    private void updateStripVisibility() {
        tabsView.setVisibility(
                stripHasTabs && searchAnimationProgress < 1f ? View.VISIBLE : View.GONE);
    }

    /** The bar belongs to the list while it is up; the search carries its own close button. */
    private void setActionBarForSearch(boolean searching) {
        if (tagItem != null) {
            tagItem.setVisibility(searching ? View.GONE : View.VISIBLE);
        }
        if (clearTagItem != null) {
            clearTagItem.setVisibility(searching || selectedTag == null ? View.GONE : View.VISIBLE);
        }
        if (searching) {
            actionBar.setTitle("");
        } else {
            updateTitle();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // The search is closed by back before the screen behind it is left: true says the stack may
        // pop, false says this screen took the back.
        if (searching) {
            closeSearch();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    /** What a chosen search row asks for: open it, or filter the home list by it. */
    private class SearchDelegate implements io.github.baba983.nativedo.search.SearchPanel.Delegate {

        @Override
        public void openTopic(TopicSummary topic) {
            presentFragment(TopicFragment.forFloor(
                    topic.getId(), topic.getTitle(), topic.getTargetPostNumber()));
        }

        @Override
        public void openPerson(io.github.baba983.nativedo.core.model.SearchUserSummary person) {
            presentFragment(io.github.baba983.nativedo.profile.ProfileFragment.forUser(
                    person.getUsername(), person.getName()));
        }

        @Override
        public void filterByCategory(CategorySummary category) {
            selectedTabId = category.getId();
            closeSearch();
            // The strip's own selection, completed: selecting the tab and loading its feed are one
            // step, and the strip must not be left showing the tab the reader was on.
            tabsView.selectTabWithId(category.getId(), 1f);
            loadFeed();
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
        stripHasTabs = !categories.isEmpty();
        updateStripVisibility();
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
        container = NativeDo.container(getParentActivity());
        updateTitle();
        DiscourseLoader.INSTANCE.feed(container, categoryOf(selectedTabId), selectedTag, this::showTopics, error -> {
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
        // While the search is open the bar carries nothing: the field is the title.
        if (searching) {
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
        loadFeed();
    }

    /** What the tag filter looks like: the tag in the title, and the way out of it in the bar. */
    private void updateTagActions() {
        if (clearTagItem != null) {
            clearTagItem.setVisibility(selectedTag == null || searching ? View.GONE : View.VISIBLE);
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

    private kotlin.Unit showTopics(List<TopicSummary> loaded) {
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
        return kotlin.Unit.INSTANCE;
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