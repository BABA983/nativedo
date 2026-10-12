// SPDX-License-Identifier: GPL-2.0-or-later
// Search state and animation wiring adapted from Telegram's SearchViewPager.
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package io.github.baba983.nativedo.search;

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
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.SearchTabsAndFiltersLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.app.GlassPanelHost;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.SearchResult;
import io.github.baba983.nativedo.core.model.SearchUserSummary;
import io.github.baba983.nativedo.core.model.Tag;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.topic.CategoryLabels;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;

/**
 * The site's search, as upstream's own search panel: a {@link ViewPagerFixed} whose pill
 * {@link ViewPagerFixed.TabsView} carries the kinds of hit this site has, over a page per kind.
 *
 * <p>The shape is DialogsActivity's search: the pager's tabs are created with
 * {@code createTabsView(false, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE)} (7494-7500) and the tab
 * capsule is a {@code SearchTabsAndFiltersLayout} with the top panel's blur background at radius 18
 * and padding 6.666 (4715-4723). Every page lists its rows through an upstream cell: a topic or post
 * hit is the topic list's own {@link DialogCell} row, a person a {@link ProfileSearchCell} (the cell
 * upstream draws a person with in its own search), a tag or category the {@link HashtagSearchCell}
 * upstream draws a search result tag with.</p>
 *
 * <p>Three of the five pages are the site's generic search answer, {@code /search.json}: the topics
 * it matched, the posts, and the people - the same three fields the pushed search screen read. The
 * other two are the site's own lists, {@code /categories.json} and {@code /tags.json}, narrowed to
 * what the reader typed; that is what upstream does with its own folders, which have no server-side
 * search either. A hit opens what it is: a topic or a post its topic at the floor it was found on, a
 * person their site profile, a category or tag the home list filtered by it.</p>
 */
public class SearchPanel extends FrameLayout {

    /** What a chosen row asks the screen to do. */
    public interface Delegate {

        void openTopic(TopicSummary topic);

        void openPerson(SearchUserSummary person);

        void filterByCategory(CategorySummary category);

        void filterByTag(Tag tag);
    }

    static final int PAGE_TOPICS = 0;
    static final int PAGE_POSTS = 1;
    static final int PAGE_USERS = 2;
    static final int PAGE_CATEGORIES = 3;
    static final int PAGE_TAGS = 4;
    static final int PAGE_COUNT = 5;

    /**
     * How long the field waits after the last keystroke before the site is asked. The pushed screen
     * waited 300ms, and that is the delay upstream's own search adapter uses
     * (DialogsSearchAdapter.java:1278, 1324).
     */
    private static final int SEARCH_DELAY_MS = 300;

    /**
     * The pill capsule's top edge: the bar's own lower edge, where DialogsActivity lays its search
     * capsule (4717; its onLayout 1286-1290 puts that child at {@code actionBar.getMeasuredHeight()}).
     * This container's own top is that edge already - the topic list puts the search container at
     * the bar's measured height - so the capsule starts at 0 there, right under the field's open
     * slot.
     */
    private static final int TABS_TOP_DP = 0;
    private static final int TABS_HEIGHT_DP = DialogsActivity.SEARCH_TABS_HEIGHT;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Delegate delegate;
    private final ViewPagerFixed pager;
    private final SearchTabsAndFiltersLayout tabsContainer;
    /** The capsule's blur source; the recorded content is the pager the capsule floats over. */
    private final GlassPanelHost glassHost;
    private final List<Page> pages = new ArrayList<>();
    private AppContainer container;
    private String query = "";
    private Runnable pendingSearch;
    private int requestGeneration;
    private boolean destroyed;
    private boolean categoriesRequested;
    private boolean tagsRequested;
    /** Whether the site's own list (categories, tags) has been read; drives that page's state. */
    private boolean categoriesLoaded;
    private boolean tagsLoaded;

    /** The site's answer, one list per page. */
    private final List<TopicSummary> topicHits = new ArrayList<>();
    private final List<TopicSummary> postHits = new ArrayList<>();
    private final List<SearchUserSummary> userHits = new ArrayList<>();
    private final List<CategorySummary> allCategories = new ArrayList<>();
    private final List<Tag> allTags = new ArrayList<>();
    private final List<CategorySummary> categoryHits = new ArrayList<>();
    private final List<Tag> tagHits = new ArrayList<>();
    /** The synthetic site user each topic hit draws a picture for, parallel to the hit list. */
    private final List<TLRPC.User> topicPeers = new ArrayList<>();
    private final List<TLRPC.User> postPeers = new ArrayList<>();
    private final List<TLRPC.User> userPeers = new ArrayList<>();

    public SearchPanel(Context context, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.delegate = delegate;

        String[] titles = {
                context.getString(R.string.search_tab_topics),
                context.getString(R.string.search_tab_posts),
                context.getString(R.string.search_tab_users),
                context.getString(R.string.search_tab_categories),
                context.getString(R.string.search_tab_tags),
        };
        for (int kind = 0; kind < PAGE_COUNT; kind++) {
            pages.add(new Page(kind, titles[kind]));
        }

        pager = new ViewPagerFixed(context, resourcesProvider) {
            @Override
            protected int tabMarginDp() {
                return 16;
            }
        };
        pager.setAdapter(new PagesAdapter());
        addView(pager, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // DialogsActivity's search tab capsule (4715-4723): the pill tabs inside the top panel's
        // glass, radius 18 and padding 6.666, 7dp of inner padding, 4dp in from each side.
        glassHost = new GlassPanelHost(pager, this);
        tabsContainer = new SearchTabsAndFiltersLayout(context);
        tabsContainer.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));
        BlurredBackgroundDrawable background = glassHost.createPanel(
                tabsContainer, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        background.setRadius(AndroidUtilities.dp(18));
        background.setPadding(AndroidUtilities.dp(6.666f));
        tabsContainer.setBlurredBackground(background);
        ViewPagerFixed.TabsView tabsView = pager.createTabsView(
                false, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE);
        tabsContainer.addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        addView(tabsContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, TABS_HEIGHT_DP, Gravity.TOP, 4, TABS_TOP_DP, 4, 0));
    }

    /** The site this panel asks; the screen hands in the container it booted with. */
    public void setContainer(AppContainer container) {
        this.container = container;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // The pill capsule's blur source records the page behind it, before the capsule is drawn.
        glassHost.record();
        super.dispatchDraw(canvas);
    }

    /**
     * The reader's query, debounced the way upstream waits: the site is asked once the typing
     * stops, and an answer to a query the reader has since changed is dropped.
     */
    public void search(String text) {
        if (destroyed) {
            return;
        }
        query = text == null ? "" : text.trim();
        requestGeneration++;
        if (pendingSearch != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingSearch);
            pendingSearch = null;
        }
        topicHits.clear();
        postHits.clear();
        userHits.clear();
        topicPeers.clear();
        postPeers.clear();
        userPeers.clear();
        for (int page = PAGE_TOPICS; page <= PAGE_USERS; page++) {
            pages.get(page).listView.scrollToPosition(0);
            if (query.isEmpty()) {
                pages.get(page).showIdle();
            } else {
                pages.get(page).showLoading();
            }
        }
        applySiteLists();
        if (query.isEmpty()) {
            return;
        }
        if (!categoriesLoaded) {
            pages.get(PAGE_CATEGORIES).showLoading();
        }
        if (!tagsLoaded) {
            pages.get(PAGE_TAGS).showLoading();
        }
        ensureSiteLists();
        pendingSearch = () -> {
            pendingSearch = null;
            askTheSite();
        };
        AndroidUtilities.runOnUIThread(pendingSearch, SEARCH_DELAY_MS);
    }

    /** The search is closing: drop what is pending and put the pages back to their idle state. */
    public void clear() {
        search("");
    }

    /** The owning fragment is gone; queued work must not update its retained views. */
    public void destroy() {
        destroyed = true;
        requestGeneration++;
        if (pendingSearch != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingSearch);
            pendingSearch = null;
        }
        for (Page page : pages) {
            page.itemsEnterAnimator.cancel();
        }
    }

    /** Retries the query in the field, for the empty state's own tap. */
    public void retry() {
        if (!query.isEmpty()) {
            askTheSite();
        }
    }

    /** The capsule arrives and leaves with the search animation, from 0.98 of its size. */
    public void setTabsVisibility(float factor) {
        tabsContainer.setAlpha(factor);
        float scale = 0.98f + 0.02f * factor;
        tabsContainer.setScaleX(scale);
        tabsContainer.setScaleY(scale);
        tabsContainer.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    /** Re-reads the theme colours, for the day/night switch. */
    public void updateColors() {
        glassHost.updateColors();
        tabsContainer.updateColors();
        for (Page page : pages) {
            page.notifyRows();
            // The pages are not rebuilt on the switch, so the empty view's text takes the new
            // colours the way upstream's own search re-applies them (FilteredSearchView:1965-1966).
            page.updateEmptyViewColors();
        }
    }

    /** One page's list, for the render harness and the screen's own checks. */
    RecyclerListView pageList(int page) {
        return pages.get(page).listView;
    }

    /** One page's shimmer view, for the render harness and the screen's own checks. */
    FlickerLoadingView pageLoadingView(int page) {
        return (FlickerLoadingView) pages.get(page).emptyView.progressView;
    }

    private void askTheSite() {
        if (container == null || destroyed || query.isEmpty()) {
            return;
        }
        final String asked = query;
        final int generation = ++requestGeneration;
        ensureSiteLists();
        for (int page = PAGE_TOPICS; page <= PAGE_USERS; page++) {
            pages.get(page).showLoading();
        }
        // The site's own lists are read once; until that read has answered their pages shimmer
        // too, with the row type upstream's own search list uses for its hashtag rows.
        if (!categoriesLoaded) {
            pages.get(PAGE_CATEGORIES).showLoading();
        }
        if (!tagsLoaded) {
            pages.get(PAGE_TAGS).showLoading();
        }
        DiscourseLoader.INSTANCE.search(container, asked, result -> {
            if (!destroyed && generation == requestGeneration) {
                applyResults(result);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("search failed", error);
            if (!destroyed && generation == requestGeneration) {
                for (int page = PAGE_TOPICS; page <= PAGE_USERS; page++) {
                    pages.get(page).showFailure();
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private void applyResults(SearchResult result) {
        topicHits.clear();
        topicHits.addAll(result.getTopics());
        postHits.clear();
        postHits.addAll(result.getPosts());
        userHits.clear();
        userHits.addAll(result.getUsers());
        topicPeers.clear();
        postPeers.clear();
        userPeers.clear();
        String baseUrl = container == null ? "" : container.getBaseUrl();
        for (TopicSummary topic : topicHits) {
            topicPeers.add(DiscourseRows.topicAuthor(topic, baseUrl));
        }
        for (TopicSummary post : postHits) {
            postPeers.add(DiscourseRows.topicAuthor(post, baseUrl));
        }
        for (SearchUserSummary person : userHits) {
            userPeers.add(DiscourseAvatars.INSTANCE.user(
                    person.getUsername(), person.getName(), person.getAvatarTemplate(), baseUrl));
        }
        for (int page = PAGE_TOPICS; page <= PAGE_USERS; page++) {
            pages.get(page).showResults();
        }
        fetchAvatars();
    }

    /**
     * The site's categories and tags, read once and narrowed to the query here: neither list has a
     * search endpoint of its own, and upstream narrows its own folders the same way.
     */
    private void ensureSiteLists() {
        if (container == null) {
            return;
        }
        if (!categoriesRequested) {
            categoriesRequested = true;
            DiscourseLoader.INSTANCE.categories(container,
                    loaded -> {
                        if (destroyed) {
                            return kotlin.Unit.INSTANCE;
                        }
                        allCategories.clear();
                        allCategories.addAll(loaded);
                        categoriesLoaded = true;
                        applySiteLists();
                        return kotlin.Unit.INSTANCE;
                    },
                    error -> {
                        if (destroyed) {
                            return kotlin.Unit.INSTANCE;
                        }
                        categoriesRequested = false;
                        FileLog.e("search categories failed", error);
                        if (!query.isEmpty() && !categoriesLoaded) {
                            pages.get(PAGE_CATEGORIES).showFailure();
                        }
                        return kotlin.Unit.INSTANCE;
                    });
        }
        if (!tagsRequested) {
            tagsRequested = true;
            DiscourseLoader.INSTANCE.tags(container,
                    loaded -> {
                        if (destroyed) {
                            return kotlin.Unit.INSTANCE;
                        }
                        allTags.clear();
                        allTags.addAll(loaded);
                        tagsLoaded = true;
                        applySiteLists();
                        return kotlin.Unit.INSTANCE;
                    },
                    error -> {
                        if (destroyed) {
                            return kotlin.Unit.INSTANCE;
                        }
                        tagsRequested = false;
                        FileLog.e("search tags failed", error);
                        if (!query.isEmpty() && !tagsLoaded) {
                            pages.get(PAGE_TAGS).showFailure();
                        }
                        return kotlin.Unit.INSTANCE;
                    });
        }
    }

    /** What the site lists show for the query in the field: a name that contains it, or all. */
    private void applySiteLists() {
        categoryHits.clear();
        tagHits.clear();
        String needle = query.toLowerCase(Locale.ROOT);
        for (CategorySummary category : allCategories) {
            if (needle.isEmpty() || category.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                categoryHits.add(category);
            }
        }
        for (Tag tag : allTags) {
            if (needle.isEmpty() || tag.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                tagHits.add(tag);
            }
        }
        // Each page reports for itself: one list answering does not end the other's shimmer.
        if (categoriesLoaded) {
            pages.get(PAGE_CATEGORIES).showResults();
        } else if (query.isEmpty()) {
            pages.get(PAGE_CATEGORIES).showIdle();
        }
        if (tagsLoaded) {
            pages.get(PAGE_TAGS).showResults();
        } else if (query.isEmpty()) {
            pages.get(PAGE_TAGS).showIdle();
        }
    }

    /**
     * The rows draw the author's initials until their picture is on disk, so each row's avatar is
     * fetched once and the page is re-bound when it lands.
     */
    private void fetchAvatars() {
        if (container == null) {
            return;
        }
        String baseUrl = container.getBaseUrl();
        fetchAvatarsFor(topicHits, topicPeers, baseUrl);
        fetchAvatarsFor(postHits, postPeers, baseUrl);
        for (int index = 0; index < userHits.size() && index < userPeers.size(); index++) {
            String url = DiscourseAvatars.INSTANCE.url(
                    userHits.get(index).getAvatarTemplate(), baseUrl, DiscourseAvatars.DEFAULT_SIZE);
            fetchAvatar(url, userPeers.get(index));
        }
    }

    private void fetchAvatarsFor(List<TopicSummary> hits, List<TLRPC.User> peers, String baseUrl) {
        for (int index = 0; index < hits.size() && index < peers.size(); index++) {
            String url = DiscourseAvatars.INSTANCE.url(
                    hits.get(index).getAuthorAvatarTemplate(), baseUrl, DiscourseAvatars.DEFAULT_SIZE);
            fetchAvatar(url, peers.get(index));
        }
    }

    private void fetchAvatar(String url, TLRPC.User peer) {
        if (url == null || peer == null || DiscourseAvatars.INSTANCE.isCached(peer)) {
            return;
        }
        DiscourseLoader.INSTANCE.cacheAvatar(container, url, peer, () -> {
            if (!destroyed) {
                for (int page = PAGE_TOPICS; page <= PAGE_USERS; page++) {
                    pages.get(page).notifyRows();
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Upstream's own shimmer rows per kind of result: the message/dialog search list loads with
     * the dialog row (DialogsSearchAdapter:1628-1629), the people list with the profile search
     * cell (SearchAdapter:375-377), and the site's category/tag lists are the rows of upstream's
     * global search list, whose loading row is the same dialog row.
     */
    private static int upstreamLoadingViewType(int kind) {
        if (kind == PAGE_USERS) {
            return FlickerLoadingView.PROFILE_SEARCH_CELL;
        }
        return FlickerLoadingView.DIALOG_TYPE;
    }

    /** The pager's own pages: five of them, one kind of hit each. */
    private class PagesAdapter extends ViewPagerFixed.Adapter {

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public View createView(int viewType) {
            return pages.get(viewType).container;
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            // Each page's own adapter draws its rows; nothing changes when the pager re-binds one.
        }

        @Override
        public int getItemId(int position) {
            return position;
        }

        @Override
        public CharSequence getItemTitle(int position) {
            return pages.get(position).title;
        }
    }

    /** One page: its list, its empty/loading view, and the rows the site answered with. */
    private class Page {

        final int kind;
        final CharSequence title;
        final FrameLayout container;
        final RecyclerListView listView;
        final StickerEmptyView emptyView;
        final PageAdapter adapter;
        final RecyclerItemsEnterAnimator itemsEnterAnimator;

        Page(int kind, CharSequence title) {
            this.kind = kind;
            this.title = title;
            Context context = getContext();
            container = new FrameLayout(context);
            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context));
            adapter = new PageAdapter(kind);
            listView.setAdapter(adapter);
            listView.setVerticalScrollBarEnabled(false);
            listView.setClipToPadding(false);
            itemsEnterAnimator = new RecyclerItemsEnterAnimator(listView, true);
            // The pill capsule floats over the rows the way the home strip does: the pages keep
            // the capsule's height as padding (plus the 4dp the field's own blur reaches below
            // its bounds), so the first row starts under it and the rows scroll beneath
            // (DialogsActivity's own search page padding, 14014-14026; the bar's height is the
            // container's own top here).
            listView.setPadding(0, AndroidUtilities.dp(TABS_HEIGHT_DP) + AndroidUtilities.dp(4),
                    0, AndroidUtilities.dp(4));
            listView.setOnItemClickListener((view, position) -> adapter.open(position));

            FlickerLoadingView loadingView = new FlickerLoadingView(context, resourcesProvider);
            loadingView.setViewType(upstreamLoadingViewType(kind));
            loadingView.setPaddingTop(listView.getPaddingTop());
            emptyView = new StickerEmptyView(
                    context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider);
            emptyView.subtitle.setVisibility(View.GONE);
            emptyView.addView(loadingView, 0);
            emptyView.showProgress(true, false);
            emptyView.setOnClickListener(view -> retry());
            container.addView(emptyView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            container.addView(listView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
            listView.setEmptyView(emptyView);
            showIdle();
        }

        void notifyRows() {
            adapter.notifyDataSetChanged();
        }

        /** The rows changed: the list is told, and the empty view follows what is there now. */
        private void refresh(boolean loading, int emptyTitle) {
            itemsEnterAnimator.cancel();
            emptyView.title.setText(getContext().getString(emptyTitle));
            emptyView.showProgress(loading, listView.isAttachedToWindow());
            adapter.notifyDataSetChanged();
        }

        /** Nothing asked yet: the empty view says how to start. */
        void showIdle() {
            refresh(false, R.string.search_idle);
        }

        /** The site is being asked: upstream's flicker skeleton runs while the answer is on its way. */
        void showLoading() {
            refresh(true, R.string.search_idle);
        }

        /** An answer arrived; the empty view only remains if it has nothing for this page. */
        void showResults() {
            refresh(false, R.string.search_no_results);
            if (listView.isAttachedToWindow() && adapter.getItemCount() > 0) {
                itemsEnterAnimator.showItemsAnimated(0);
            }
        }

        void showFailure() {
            refresh(false, R.string.search_failed);
        }

        /** The day/night switch keeps these pages; their colours are re-applied in place. */
        void updateEmptyViewColors() {
            emptyView.title.setTextColor(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            emptyView.subtitle.setTextColor(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            emptyView.invalidate();
        }
    }

    /** The rows of one page, drawn by the cell that fits the kind of hit it is. */
    private class PageAdapter extends RecyclerListView.SelectionAdapter {

        private final int kind;

        PageAdapter(int kind) {
            this.kind = kind;
        }

        /** The row that was tapped, sent to the screen as the thing it stands for. */
        private void open(int position) {
            switch (kind) {
                case PAGE_TOPICS:
                    if (position < topicHits.size()) {
                        delegate.openTopic(topicHits.get(position));
                    }
                    break;
                case PAGE_POSTS:
                    if (position < postHits.size()) {
                        delegate.openTopic(postHits.get(position));
                    }
                    break;
                case PAGE_USERS:
                    if (position < userHits.size()) {
                        delegate.openPerson(userHits.get(position));
                    }
                    break;
                case PAGE_CATEGORIES:
                    if (position < categoryHits.size()) {
                        delegate.filterByCategory(categoryHits.get(position));
                    }
                    break;
                case PAGE_TAGS:
                    if (position < tagHits.size()) {
                        delegate.filterByTag(tagHits.get(position));
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public int getItemCount() {
            switch (kind) {
                case PAGE_TOPICS:
                    return topicHits.size();
                case PAGE_POSTS:
                    return postHits.size();
                case PAGE_USERS:
                    return userHits.size();
                case PAGE_CATEGORIES:
                    return categoryHits.size();
                case PAGE_TAGS:
                    return tagHits.size();
                default:
                    return 0;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (kind == PAGE_USERS) {
                // The cell upstream draws a person with in its own search results.
                ProfileSearchCell cell = new ProfileSearchCell(context, resourcesProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            if (kind == PAGE_CATEGORIES || kind == PAGE_TAGS) {
                // Upstream's own search-result tag row: one line, the tag, a divider.
                HashtagSearchCell cell = new HashtagSearchCell(context);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(49)));
                return new RecyclerListView.Holder(cell);
            }
            // A topic hit and a post hit are both a topic: the row the topic list draws, with the
            // excerpt the site sent. A post hit's excerpt is the post's own blurb.
            DialogCell cell = new DialogCell(null, context, false, false, 0, resourcesProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (kind == PAGE_USERS) {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                SearchUserSummary person = userHits.get(position);
                TLRPC.User peer = position < userPeers.size() ? userPeers.get(position) : null;
                cell.setData(peer, null, highlight(person.getName()), highlight("@" + person.getUsername()), false, false);
                cell.useSeparator = position < getItemCount() - 1;
                bindAvatar(cell.avatarImage, peer);
                return;
            }
            if (kind == PAGE_CATEGORIES) {
                HashtagSearchCell cell = (HashtagSearchCell) holder.itemView;
                cell.setText(highlight(CategoryLabels.of(categoryHits.get(position))));
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                cell.setNeedDivider(position < getItemCount() - 1);
                return;
            }
            if (kind == PAGE_TAGS) {
                HashtagSearchCell cell = (HashtagSearchCell) holder.itemView;
                cell.setText(highlight("#" + tagHits.get(position).getName()));
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                cell.setNeedDivider(position < getItemCount() - 1);
                return;
            }
            boolean postHit = kind == PAGE_POSTS;
            List<TopicSummary> hits = postHit ? postHits : topicHits;
            List<TLRPC.User> peers = postHit ? postPeers : topicPeers;
            TopicSummary topic = hits.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            TLRPC.User peer = peers.get(position);
            MessagesController.getInstance(0).putUser(peer, true);
            DialogCell.CustomDialog row = DiscourseRows.topicRow(topic, false);
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = (int) (topic.getTargetPostId() == null ? topic.getId() : topic.getTargetPostId());
            message.date = row.date;
            message.message = row.message;
            message.from_id = DiscourseRows.replierPeer(topic.getAuthorUsername());
            message.peer_id = message.from_id;
            HashMap<Long, TLRPC.User> users = new HashMap<>();
            users.put(peer.id, peer);
            MessageObject object = DiscourseRows.messageObject(message, users);
            object.setQuery(query);
            cell.setTitleOverride(topic.getTitle());
            cell.setDialog(peer.id, object, row.date, false, false);
            cell.useSeparator = position < getItemCount() - 1;
            bindAvatar(cell.avatarImage, peer);
        }

        private CharSequence highlight(CharSequence text) {
            CharSequence marked = AndroidUtilities.highlightText(text, query.toLowerCase(Locale.ROOT), resourcesProvider);
            return marked == null ? text : marked;
        }

        private void bindAvatar(org.telegram.messenger.ImageReceiver avatarImage, TLRPC.User peer) {
            if (avatarImage == null || peer == null) {
                return;
            }
            ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(peer);
            if (location != null) {
                // The row's own drawable (the initials) stays as the placeholder.
                avatarImage.setImage(location, "50_50", null, null, peer, 0);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}
