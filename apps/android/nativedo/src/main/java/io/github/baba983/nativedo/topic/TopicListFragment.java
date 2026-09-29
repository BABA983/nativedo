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

/**
 * linux.do's topic list: {@code /latest.json} rendered with upstream's {@link DialogCell} through
 * its own {@link DialogCell.CustomDialog} row type, which exists exactly for rows that are not
 * Telegram chats.
 */
public class TopicListFragment extends BaseFragment {

    private RecyclerListView listView;
    private final List<TopicSummary> topics = new ArrayList<>();

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("linux.do");

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
        AppContainer container = NativeDo.container(getParentActivity());
        DiscourseLoader.INSTANCE.latestTopics(container, loaded -> {
            topics.clear();
            topics.addAll(loaded);
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            org.telegram.messenger.FileLog.e("topic list failed", error);
            return kotlin.Unit.INSTANCE;
        });
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