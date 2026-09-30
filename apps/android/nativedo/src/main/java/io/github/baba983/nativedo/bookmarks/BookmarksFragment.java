package io.github.baba983.nativedo.bookmarks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.Bookmark;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The reader's own bookmarks, the way the site lists them.
 *
 * <p>A bookmark is a post, so its row is the row a topic list draws for a post's topic - the title,
 * the excerpt the site computed, when the reader bookmarked it - and a tap opens that topic at that
 * post's own floor. The site answers this list only for the reader themselves.</p>
 */
public class BookmarksFragment extends BaseFragment {

    /** How close to the end the list comes before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    private RecyclerListView listView;
    private final List<Bookmark> bookmarks = new ArrayList<>();
    /** One author per row, so the picture is fetched once and the row can be re-bound for it. */
    private final List<TLRPC.User> authors = new ArrayList<>();
    private AppContainer container;
    private boolean loading;
    private int page;
    /** Set when a page came back short, or with nothing at all: that is the end of the list. */
    private boolean reachedTheEnd;

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("书签");
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new BookmarkAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < bookmarks.size()) {
                open(bookmarks.get(position));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadNextPageIfNeeded();
            }
        });

        fragmentView = listView;
        container = NativeDo.container(getParentActivity());
        loadPage();
        return fragmentView;
    }

    /** A bookmark opens the topic it is in, at the floor the post is on. */
    private void open(Bookmark bookmark) {
        if (bookmark.getPostNumber() <= 0) {
            presentFragment(TopicFragment.forTopic(bookmark.getTopicId(), bookmark.getTitle()));
            return;
        }
        presentFragment(TopicFragment.forFloor(
                bookmark.getTopicId(), bookmark.getTitle(), bookmark.getPostNumber()));
    }

    private void loadPage() {
        String username = sessionUsername();
        if (container == null || loading || reachedTheEnd || username == null) {
            return;
        }
        loading = true;
        final int asked = page;
        BookmarksLoader.INSTANCE.page(container, username, asked, loaded -> {
            loading = false;
            page = asked + 1;
            reachedTheEnd = loaded.isEmpty();
            for (Bookmark bookmark : loaded) {
                bookmarks.add(bookmark);
                authors.add(author(bookmark));
            }
            if (loaded.isEmpty()) {
                // Nothing more to draw: a list with nothing on it stays empty rather than growing a
                // row that says so - the reader got here from a row of their own profile.
                page = Integer.MAX_VALUE;
            }
            notifyRowsChanged();
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            loading = false;
            FileLog.e("bookmarks failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void loadNextPageIfNeeded() {
        if (listView == null || listView.getAdapter() == null
                || bookmarks.isEmpty() || reachedTheEnd) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null
                || manager.findLastVisibleItemPosition()
                < listView.getAdapter().getItemCount() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        loadPage();
    }

    /** Whose bookmarks these are: the site answers the list only for the reader themselves. */
    private String sessionUsername() {
        if (container == null || container.getSessionStore().snapshot() == null) {
            return null;
        }
        String username = container.getSessionStore().snapshot().getUsername();
        return username == null || username.isEmpty() ? null : username;
    }

    private TLRPC.User author(Bookmark bookmark) {
        return DiscourseAvatars.INSTANCE.user(
                bookmark.getAuthorUsername(),
                bookmark.getAuthorDisplayName(),
                bookmark.getAuthorAvatarTemplate(),
                container.getBaseUrl());
    }

    private void fetchAvatars() {
        for (int index = 0; index < authors.size(); index++) {
            final int row = index;
            String url = DiscourseAvatars.INSTANCE.url(
                    bookmarks.get(index).getAuthorAvatarTemplate(),
                    container.getBaseUrl(),
                    DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(authors.get(index))) {
                continue;
            }
            io.github.baba983.nativedo.topic.DiscourseLoader.INSTANCE.cacheAvatar(
                    container, url, authors.get(index), () -> {
                        if (listView != null && listView.getAdapter() != null) {
                            listView.getAdapter().notifyItemChanged(row);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
        }
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class BookmarkAdapter extends RecyclerListView.SelectionAdapter {

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
            Bookmark bookmark = bookmarks.get(position);
            cell.setDialog(DiscourseRows.bookmarkRow(bookmark));
            // `setDialog` clears the row's avatar receiver, so the author's photo goes in after it.
            ImageLocation avatar = position < authors.size()
                    ? DiscourseAvatars.INSTANCE.imageLocation(authors.get(position))
                    : null;
            if (avatar != null) {
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null,
                        authors.get(position), 0);
            }
        }

        @Override
        public int getItemCount() {
            return bookmarks.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}
