package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for the topic list. ViewTypes mirror {@code DialogsAdapter}'s pattern:
 * topic cell rows, optional flicker-loading footer, optional empty placeholder.
 */
public class TopicListAdapter extends RecyclerListView.SelectionAdapter {

    public static final int VIEW_TYPE_TOPIC = 0;
    public static final int VIEW_TYPE_FOOTER_LOADING = 1;
    public static final int VIEW_TYPE_SKELETON = 2;

    private static final int SKELETON_ROW_COUNT = 7;

    private final Context context;
    private final String baseUrl;
    private final AsyncListDiffer<TopicSummary> differ;

    private Map<Integer, CategorySummary> categories = Collections.emptyMap();
    private boolean showLoadingFooter;
    private boolean showInitialLoading;

    public TopicListAdapter(@NonNull Context context, @NonNull String baseUrl) {
        this.context = context;
        this.baseUrl = baseUrl;
        differ = new AsyncListDiffer<>(
            new AdapterListUpdateCallback(this),
            new AsyncDifferConfig.Builder<>(new TopicDiffCallback()).build());
    }

    public void submit(@NonNull List<TopicSummary> topics,
                       @NonNull Map<Integer, CategorySummary> categories,
                       boolean showLoadingFooter,
                       boolean showInitialLoading) {
        submitList(topics, categories, showLoadingFooter, showInitialLoading);
    }

    public void submitList(@NonNull List<TopicSummary> topics,
                           @NonNull Map<Integer, CategorySummary> categories,
                           boolean showLoadingFooter,
                           boolean showInitialLoading) {
        boolean wasInitialLoading = this.showInitialLoading;
        int oldTopicCount = differ.getCurrentList().size();
        Map<Integer, CategorySummary> oldCategories = this.categories;
        List<TopicSummary> newTopics = new ArrayList<>(topics);

        this.categories = categories;

        if (showInitialLoading) {
            if (this.showLoadingFooter && !wasInitialLoading) {
                this.showLoadingFooter = false;
                notifyItemRemoved(oldTopicCount);
            }
            differ.submitList(Collections.emptyList(), () -> {
                if (!this.showInitialLoading) {
                    this.showInitialLoading = true;
                    notifyItemRangeInserted(0, SKELETON_ROW_COUNT);
                }
            });
            return;
        }

        if (wasInitialLoading) {
            this.showInitialLoading = false;
            this.showLoadingFooter = false;
            notifyItemRangeRemoved(0, SKELETON_ROW_COUNT);
            differ.submitList(newTopics, () -> {
                this.showLoadingFooter = showLoadingFooter;
                if (showLoadingFooter) {
                    notifyItemInserted(newTopics.size());
                }
                notifyCategoriesChanged(oldCategories, categories, newTopics.size());
            });
            return;
        }

        boolean footerChanged = this.showLoadingFooter != showLoadingFooter;
        if (footerChanged && this.showLoadingFooter) {
            this.showLoadingFooter = false;
            notifyItemRemoved(oldTopicCount);
        }

        differ.submitList(newTopics, () -> {
            this.showLoadingFooter = showLoadingFooter;
            if (footerChanged && showLoadingFooter) {
                notifyItemInserted(newTopics.size());
            }
            notifyCategoriesChanged(oldCategories, categories, newTopics.size());
        });
    }

    private void notifyCategoriesChanged(@NonNull Map<Integer, CategorySummary> oldCategories,
                                         @NonNull Map<Integer, CategorySummary> categories,
                                         int topicCount) {
        if (!Objects.equals(oldCategories, categories)) {
            notifyItemRangeChanged(0, topicCount, TopicCell.MASK_FULL);
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == VIEW_TYPE_TOPIC;
    }

    @Override
    public int getItemCount() {
        if (showInitialLoading) {
            return SKELETON_ROW_COUNT;
        }
        int count = differ.getCurrentList().size();
        if (showLoadingFooter) count += 1;
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (showInitialLoading) return VIEW_TYPE_SKELETON;
        if (position >= differ.getCurrentList().size()) return VIEW_TYPE_FOOTER_LOADING;
        return VIEW_TYPE_TOPIC;
    }

    public @androidx.annotation.Nullable TopicSummary getTopicAt(int position) {
        if (showInitialLoading) return null;
        List<TopicSummary> topics = differ.getCurrentList();
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
            case VIEW_TYPE_SKELETON: {
                FlickerLoadingView flicker = new FlickerLoadingView(context);
                flicker.setIsSingleCell(false);
                flicker.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                flicker.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
                view = flicker;
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

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
                                 int position,
                                 @NonNull List payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        if (holder.getItemViewType() != VIEW_TYPE_TOPIC) return;
        TopicSummary topic = getTopicAt(position);
        if (topic == null) return;
        int payloadMask = 0;
        for (Object payload : payloads) {
            if (payload instanceof Integer) {
                payloadMask |= (Integer) payload;
            }
        }
        if (payloadMask == 0) {
            onBindViewHolder(holder, position);
            return;
        }
        TopicCell cell = (TopicCell) holder.itemView;
        cell.setCategories(categories);
        cell.setTopic(topic, baseUrl, payloadMask);
    }

    private static final class TopicDiffCallback extends DiffUtil.ItemCallback<TopicSummary> {
        @Override
        public boolean areItemsTheSame(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
            return oldTopic.getId() == newTopic.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
            return oldTopic.equals(newTopic);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
            int payload = 0;
            if (!Objects.equals(oldTopic.getLastPostedAt(), newTopic.getLastPostedAt())) {
                payload |= TopicCell.MASK_LAST_POSTED;
            }
            if (oldTopic.getUnreadCount() != newTopic.getUnreadCount()) {
                payload |= TopicCell.MASK_UNREAD;
            }
            if (oldTopic.getReplyCount() != newTopic.getReplyCount()) {
                payload |= TopicCell.MASK_REPLY_COUNT;
            }
            if (oldTopic.isPinned() != newTopic.isPinned() ||
                oldTopic.isPinnedGlobally() != newTopic.isPinnedGlobally()) {
                payload |= TopicCell.MASK_PINNED;
            }
            if (oldTopic.isMuted() != newTopic.isMuted()) {
                payload |= TopicCell.MASK_MUTED;
            }
            if (!Objects.equals(oldTopic.getTitle(), newTopic.getTitle()) ||
                !Objects.equals(oldTopic.getAuthorUsername(), newTopic.getAuthorUsername()) ||
                !Objects.equals(oldTopic.getAuthorAvatarTemplate(), newTopic.getAuthorAvatarTemplate()) ||
                !Objects.equals(oldTopic.getCategoryId(), newTopic.getCategoryId()) ||
                !Objects.equals(oldTopic.getLastPosterUsername(), newTopic.getLastPosterUsername()) ||
                !Objects.equals(oldTopic.getTags(), newTopic.getTags()) ||
                oldTopic.isHot() != newTopic.isHot() ||
                oldTopic.isUnseen() != newTopic.isUnseen()) {
                payload |= TopicCell.MASK_FULL;
            }
            return payload == 0 ? null : payload;
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
