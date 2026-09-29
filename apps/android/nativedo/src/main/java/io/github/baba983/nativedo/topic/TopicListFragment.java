package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;

/**
 * linux.do's topic list: {@code /latest.json} rendered with upstream's {@link DialogCell} through
 * its own {@link DialogCell.CustomDialog} row type, which exists exactly for rows that are not
 * Telegram chats.
 */
public class TopicListFragment extends BaseFragment {

    /** The search item in the action bar, which opens the search screen. */
    private static final int SEARCH_ITEM = 1;

    private RecyclerListView listView;
    private final List<TopicSummary> topics = new ArrayList<>();
    /** One author per row, kept so the avatar download can refresh the row it belongs to. */
    private final List<TLRPC.User> authors = new ArrayList<>();
    private AppContainer container;

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

        fragmentView = listView;
        loadLatest();
        return fragmentView;
    }

    private void presentTopic(TopicSummary topic) {
        if (getParentActivity() instanceof io.github.baba983.nativedo.app.NativeDoActivity) {
            ((io.github.baba983.nativedo.app.NativeDoActivity) getParentActivity())
                    .present(TopicFragment.forTopic(topic.getId(), topic.getTitle()));
        }
    }

    private void loadLatest() {
        container = NativeDo.container(getParentActivity());
        DiscourseLoader.INSTANCE.latestTopics(container, loaded -> {
            topics.clear();
            authors.clear();
            topics.addAll(loaded);
            for (TopicSummary topic : loaded) {
                authors.add(DiscourseRows.topicAuthor(topic, container.getBaseUrl()));
            }
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            org.telegram.messenger.FileLog.e("topic list failed", error);
            return kotlin.Unit.INSTANCE;
        });
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
            cell.setDialog(DiscourseRows.topicRow(topics.get(position)));
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