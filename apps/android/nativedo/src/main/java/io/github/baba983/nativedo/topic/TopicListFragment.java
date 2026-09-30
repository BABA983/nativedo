package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.CategorySummary;
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

    /** The search item in the action bar, which opens the search screen. */
    private static final int SEARCH_ITEM = 1;

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
    private RecyclerListView listView;
    private final List<TopicSummary> topics = new ArrayList<>();
    /** One author per row, kept so the avatar download can refresh the row it belongs to. */
    private final List<TLRPC.User> authors = new ArrayList<>();
    /** The site's top-level categories, in its own order; a subcategory is not a tab of its own. */
    private final List<CategorySummary> categories = new ArrayList<>();
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
                if (id == SEARCH_ITEM) {
                    presentFragment(new io.github.baba983.nativedo.search.SearchFragment());
                }
            }
        });
        // Upstream's own search item: the icon that opens the field, which the search screen then
        // uses for the query.
        actionBar.createMenu().addItem(SEARCH_ITEM, org.telegram.messenger.R.drawable.outline_header_search);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new TopicListAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
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
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.addView(contentView, LayoutHelper.createFrame(
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
        loadFeed();
        loadCategories();
        return fragmentView;
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
    }

    private void presentTopic(TopicSummary topic) {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(TopicFragment.forTopic(topic.getId(), topic.getTitle()));
        }
    }

    /** The feed of the selected tab: the site's latest list, or one category's own. */
    private void loadFeed() {
        CategorySummary category = categoryOf(selectedTabId);
        if (category == null) {
            container = NativeDo.container(getParentActivity());
            DiscourseLoader.INSTANCE.latestTopics(container, this::showTopics, error -> {
                FileLog.e("topic list failed", error);
                return kotlin.Unit.INSTANCE;
            });
            return;
        }
        DiscourseLoader.INSTANCE.categoryTopics(container, category, this::showTopics, error -> {
            FileLog.e("topic list failed", error);
            return kotlin.Unit.INSTANCE;
        });
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