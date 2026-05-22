// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/ui/Adapters/DialogsAdapter.java (viewType pattern reference)
// HOST-WIRING:
//   - Wraps NativeDo's TopicCell, FlickerLoadingView, EmptyCell into a
//     RecyclerListView.SelectionAdapter compatible with the ported list view.
// ADAPTED-BACKEND:
//   - Reads from io.github.baba983.nativedo.feature.home.ui.HomeUiState (TopicSummary list).
// OMITTED:
//   - Telegram archive/folder header, user-status row, contacts row, hints row.
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.ui.cells.EmptyCell;
import io.github.baba983.nativedo.ui.cells.TopicCell;
import io.github.baba983.nativedo.ui.components.FlickerLoadingView;
import io.github.baba983.nativedo.ui.components.RecyclerListView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the topic list. ViewTypes mirror {@code DialogsAdapter}'s pattern:
 * topic cell rows, optional flicker-loading footer, optional empty placeholder.
 */
public class TopicListAdapter extends RecyclerListView.SelectionAdapter {

    public static final int VIEW_TYPE_TOPIC = 0;
    public static final int VIEW_TYPE_FOOTER_LOADING = 1;
    public static final int VIEW_TYPE_EMPTY = 2;

    private final Context context;
    private final String baseUrl;

    private List<TopicSummary> topics = Collections.emptyList();
    private Map<Integer, CategorySummary> categories = Collections.emptyMap();
    private boolean showLoadingFooter;
    private boolean showInitialLoading;

    public TopicListAdapter(@NonNull Context context, @NonNull String baseUrl) {
        this.context = context;
        this.baseUrl = baseUrl;
    }

    public void submit(@NonNull List<TopicSummary> topics,
                       @NonNull Map<Integer, CategorySummary> categories,
                       boolean showLoadingFooter,
                       boolean showInitialLoading) {
        this.topics = topics;
        this.categories = categories;
        this.showLoadingFooter = showLoadingFooter;
        this.showInitialLoading = showInitialLoading;
        notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == VIEW_TYPE_TOPIC;
    }

    @Override
    public int getItemCount() {
        if (showInitialLoading) {
            // Show ~7 flicker rows on first load.
            return 7;
        }
        int count = topics.size();
        if (showLoadingFooter) count += 1;
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (showInitialLoading) return VIEW_TYPE_FOOTER_LOADING;
        if (position >= topics.size()) return VIEW_TYPE_FOOTER_LOADING;
        return VIEW_TYPE_TOPIC;
    }

    public @androidx.annotation.Nullable TopicSummary getTopicAt(int position) {
        if (showInitialLoading) return null;
        if (position < 0 || position >= topics.size()) return null;
        return topics.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_FOOTER_LOADING: {
                FlickerLoadingView flicker = new FlickerLoadingView(context);
                flicker.setIsSingleCell(true);
                flicker.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                flicker.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
                view = flicker;
                break;
            }
            case VIEW_TYPE_EMPTY: {
                view = new EmptyCell(context);
                view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(120)));
                break;
            }
            case VIEW_TYPE_TOPIC:
            default: {
                TopicCell cell = new TopicCell(context);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                view = cell;
                break;
            }
        }
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() != VIEW_TYPE_TOPIC) return;
        TopicSummary topic = getTopicAt(position);
        if (topic == null) return;
        TopicCell cell = (TopicCell) holder.itemView;
        cell.setCategories(categories);
        cell.setTopic(topic, baseUrl);
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
