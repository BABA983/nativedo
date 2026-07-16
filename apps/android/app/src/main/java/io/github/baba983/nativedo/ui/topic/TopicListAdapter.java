package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import org.telegram.ui.ActionBar.Theme;
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
    public static final int VIEW_TYPE_EMPTY = 3;
    public static final int VIEW_TYPE_ERROR = 4;
    public static final int VIEW_TYPE_FOOTER_ERROR = 5;

    private static final int SKELETON_ROW_COUNT = 7;

    private final Context context;
    private final String baseUrl;
    private final AsyncListDiffer<TopicSummary> differ;

    private Map<Integer, CategorySummary> categories = Collections.emptyMap();
    private boolean showLoadingFooter;
    private boolean showInitialLoading;
    @Nullable private CharSequence emptyTitle;
    @Nullable private CharSequence emptyMessage;
    @Nullable private CharSequence errorTitle;
    @Nullable private CharSequence errorMessage;
    @Nullable private CharSequence loadMoreErrorMessage;
    @Nullable private Runnable retryCallback;
    @Nullable private Runnable loadMoreRetryCallback;
    @Nullable private String searchQuery;
    private boolean showSearchExcerpt;

    public TopicListAdapter(@NonNull Context context, @NonNull String baseUrl) {
        this.context = context;
        this.baseUrl = baseUrl;
        differ = new AsyncListDiffer<>(
            new ListUpdateCallback() {
                @Override public void onInserted(int position, int count) {}
                @Override public void onRemoved(int position, int count) {}
                @Override public void onMoved(int fromPosition, int toPosition) {}
                @Override public void onChanged(int position, int count, @Nullable Object payload) {}
            },
            new AsyncDifferConfig.Builder<>(new TopicDiffCallback()).build());
    }

    public void setSearchPresentation(@Nullable String query, boolean showExcerpt) {
        String normalized = query == null ? null : query.trim();
        if (TextUtils.equals(searchQuery, normalized) && showSearchExcerpt == showExcerpt) {
            return;
        }
        searchQuery = normalized;
        showSearchExcerpt = showExcerpt;
        notifyDataSetChanged();
    }

    public void submit(@NonNull List<TopicSummary> topics,
                       @NonNull Map<Integer, CategorySummary> categories,
                       boolean showLoadingFooter,
                       boolean showInitialLoading) {
        submitList(topics, categories, showLoadingFooter, showInitialLoading);
    }

    public void submit(@NonNull List<TopicSummary> topics,
                       @NonNull Map<Integer, CategorySummary> categories,
                       boolean showLoadingFooter,
                       boolean showInitialLoading,
                       @Nullable CharSequence emptyTitle,
                       @Nullable CharSequence emptyMessage,
                       @Nullable CharSequence errorTitle,
                       @Nullable CharSequence errorMessage,
                       @Nullable CharSequence loadMoreErrorMessage,
                       @Nullable Runnable retryCallback,
                       @Nullable Runnable loadMoreRetryCallback) {
        submitList(
            topics,
            categories,
            showLoadingFooter,
            showInitialLoading,
            emptyTitle,
            emptyMessage,
            errorTitle,
            errorMessage,
            loadMoreErrorMessage,
            retryCallback,
            loadMoreRetryCallback);
    }

    public void submitList(@NonNull List<TopicSummary> topics,
                           @NonNull Map<Integer, CategorySummary> categories,
                           boolean showLoadingFooter,
                           boolean showInitialLoading) {
        submitList(topics, categories, showLoadingFooter, showInitialLoading, null, null, null, null, null, null, null);
    }

    public void submitList(@NonNull List<TopicSummary> topics,
                           @NonNull Map<Integer, CategorySummary> categories,
                           boolean showLoadingFooter,
                           boolean showInitialLoading,
                           @Nullable CharSequence emptyTitle,
                           @Nullable CharSequence emptyMessage,
                           @Nullable CharSequence errorTitle,
                           @Nullable CharSequence errorMessage,
                           @Nullable CharSequence loadMoreErrorMessage,
                           @Nullable Runnable retryCallback,
                           @Nullable Runnable loadMoreRetryCallback) {
        int oldItemCount = getItemCount();
        Map<Integer, CategorySummary> oldCategories = this.categories;
        List<TopicSummary> newTopics = new ArrayList<>(topics);
        boolean structureChanged = this.showInitialLoading != showInitialLoading ||
            this.showLoadingFooter != showLoadingFooter ||
            !TextUtils.equals(this.emptyTitle, emptyTitle) ||
            !TextUtils.equals(this.emptyMessage, emptyMessage) ||
            !TextUtils.equals(this.errorTitle, errorTitle) ||
            !TextUtils.equals(this.errorMessage, errorMessage) ||
            !TextUtils.equals(this.loadMoreErrorMessage, loadMoreErrorMessage);

        this.categories = categories;
        this.showInitialLoading = showInitialLoading;
        this.showLoadingFooter = showLoadingFooter;
        this.emptyTitle = emptyTitle;
        this.emptyMessage = emptyMessage;
        this.errorTitle = errorTitle;
        this.errorMessage = errorMessage;
        this.loadMoreErrorMessage = loadMoreErrorMessage;
        this.retryCallback = retryCallback;
        this.loadMoreRetryCallback = loadMoreRetryCallback;

        differ.submitList(showInitialLoading ? Collections.emptyList() : newTopics, this::notifyDataSetChanged);
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
        return holder.getItemViewType() == VIEW_TYPE_TOPIC || holder.getItemViewType() == VIEW_TYPE_ERROR || holder.getItemViewType() == VIEW_TYPE_FOOTER_ERROR;
    }

    @Override
    public int getItemCount() {
        if (showInitialLoading) {
            return SKELETON_ROW_COUNT;
        }
        int count = differ.getCurrentList().size();
        if (count == 0 && hasInitialError()) return 1;
        if (count == 0 && hasEmptyState()) return 1;
        if (count > 0 && showLoadingFooter) count += 1;
        if (count > 0 && hasLoadMoreError()) count += 1;
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (showInitialLoading) return VIEW_TYPE_SKELETON;
        int topicCount = differ.getCurrentList().size();
        if (topicCount == 0 && hasInitialError()) return VIEW_TYPE_ERROR;
        if (topicCount == 0 && hasEmptyState()) return VIEW_TYPE_EMPTY;
        if (position >= topicCount) {
            return hasLoadMoreError() ? VIEW_TYPE_FOOTER_ERROR : VIEW_TYPE_FOOTER_LOADING;
        }
        return VIEW_TYPE_TOPIC;
    }

    private boolean hasEmptyState() {
        return !TextUtils.isEmpty(emptyTitle) || !TextUtils.isEmpty(emptyMessage);
    }

    private boolean hasInitialError() {
        return !TextUtils.isEmpty(errorTitle) || !TextUtils.isEmpty(errorMessage);
    }

    private boolean hasLoadMoreError() {
        return !TextUtils.isEmpty(loadMoreErrorMessage);
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
            case VIEW_TYPE_EMPTY:
            case VIEW_TYPE_ERROR: {
                StateCell cell = new StateCell(context, false);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(260)));
                view = cell;
                break;
            }
            case VIEW_TYPE_FOOTER_ERROR: {
                StateCell cell = new StateCell(context, true);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                view = cell;
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
        int viewType = holder.getItemViewType();
        if (viewType == VIEW_TYPE_EMPTY) {
            ((StateCell) holder.itemView).bind(emptyTitle, emptyMessage, null, null);
            return;
        }
        if (viewType == VIEW_TYPE_ERROR) {
            ((StateCell) holder.itemView).bind(errorTitle, errorMessage, retryCallback, context.getString(R.string.action_retry));
            return;
        }
        if (viewType == VIEW_TYPE_FOOTER_ERROR) {
            ((StateCell) holder.itemView).bind(loadMoreErrorMessage, null, loadMoreRetryCallback, context.getString(R.string.action_retry));
            return;
        }
        if (viewType != VIEW_TYPE_TOPIC) return;
        TopicSummary topic = getTopicAt(position);
        if (topic == null) return;
        TopicCell cell = (TopicCell) holder.itemView;
        cell.setSearchPresentation(searchQuery, showSearchExcerpt);
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
        cell.setSearchPresentation(searchQuery, showSearchExcerpt);
        cell.setCategories(categories);
        cell.setTopic(topic, baseUrl, payloadMask);
    }

    private static final class TopicDiffCallback extends DiffUtil.ItemCallback<TopicSummary> {
        @Override
        public boolean areItemsTheSame(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
            if (oldTopic.getRowIdentity() != null || newTopic.getRowIdentity() != null) {
                return Objects.equals(oldTopic.getRowIdentity(), newTopic.getRowIdentity());
            }
            return oldTopic.getId() == newTopic.getId() &&
                Objects.equals(oldTopic.getTargetPostNumber(), newTopic.getTargetPostNumber());
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
                !Objects.equals(oldTopic.getAuthorAnimatedAvatarUrl(), newTopic.getAuthorAnimatedAvatarUrl()) ||
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

    private static final class StateCell extends LinearLayout implements Theme.Colorable {
        private final TextView titleView;
        private final TextView messageView;
        private final TextView actionView;

        StateCell(@NonNull Context context, boolean footer) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(footer ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            setPadding(
                AndroidUtilities.dp(24f),
                AndroidUtilities.dp(footer ? 14f : 28f),
                AndroidUtilities.dp(24f),
                AndroidUtilities.dp(footer ? 14f : 28f));

            titleView = new TextView(context);
            titleView.setGravity(Gravity.CENTER);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, footer ? 14f : 17f);
            titleView.setSingleLine(false);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

            messageView = new TextView(context);
            messageView.setGravity(Gravity.CENTER);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            messageView.setSingleLine(false);
            messageView.setMaxLines(3);
            messageView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = AndroidUtilities.dp(6f);
            addView(messageView, messageParams);

            actionView = new TextView(context);
            actionView.setGravity(Gravity.CENTER);
            actionView.setTypeface(Typeface.DEFAULT_BOLD);
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            actionView.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                AndroidUtilities.dp(36f));
            actionParams.gravity = Gravity.CENTER_HORIZONTAL;
            actionParams.topMargin = AndroidUtilities.dp(12f);
            addView(actionView, actionParams);
            updateColors();
        }

        void bind(@Nullable CharSequence title, @Nullable CharSequence message, @Nullable Runnable action, @Nullable CharSequence actionText) {
            titleView.setText(title);
            titleView.setVisibility(TextUtils.isEmpty(title) ? GONE : VISIBLE);
            messageView.setText(message);
            messageView.setVisibility(TextUtils.isEmpty(message) ? GONE : VISIBLE);
            actionView.setText(actionText);
            actionView.setVisibility(action == null || TextUtils.isEmpty(actionText) ? GONE : VISIBLE);
            actionView.setOnClickListener(action == null ? null : v -> action.run());
        }

        @Override
        public void updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            actionView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            actionView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(18f),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed),
                Theme.getColor(Theme.key_featuredStickers_addButton)));
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
