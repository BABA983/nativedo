package io.github.baba983.nativedo.notifications;

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
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.NotificationItem;
import io.github.baba983.nativedo.core.model.NotificationPage;
import io.github.baba983.nativedo.feature.notifications.data.NotificationRepository;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The reader's own notifications, {@code /notifications.json}, drawn by upstream's list row.
 *
 * <p>Nothing upstream can be reused as a screen: Telegram's notification screens are an account's
 * own (its chats, its message deliveries, its muted dialogs), and this client has none of that
 * behind it. What <em>can</em> be reused is the row, and a notification is what a
 * {@link DialogCell} already draws - a title, a line under it and a date - so the site's fields go
 * in there and a tap opens the topic at the floor the notification is about.</p>
 */
public class NotificationsFragment extends BaseFragment {

    private static final int TYPE_NOTIFICATION = 0;
    private static final int TYPE_NOTE = 1;

    /** How close to the end the list comes before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    private RecyclerListView listView;
    private final List<NotificationItem> items = new ArrayList<>();
    /**
     * One actor per row, so the avatar a row draws is fetched once and the row it belongs to can be
     * re-bound when the picture lands.
     */
    private final List<TLRPC.User> actors = new ArrayList<>();
    private AppContainer container;
    private boolean loading;
    /** Whether the reader has no notifications at all, which is its own row. */
    private boolean empty;
    private int offset;
    /** How many notifications the site has in total; every page moves [offset] towards it. */
    private int total = Integer.MAX_VALUE;

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("通知");

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new NotificationAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < items.size()) {
                open(items.get(position));
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

    /**
     * A notification opens the post it is about at that post's own floor. The site's topic-less
     * notifications - a badge, likes it consolidated - have nothing to open, and their rows are not
     * tappable.
     */
    private void open(NotificationItem item) {
        if (!DiscourseNotifications.INSTANCE.hasDestination(item)) {
            return;
        }
        presentFragment(TopicFragment.forFloor(item.getTopicId(), item.getTitle(), item.getPostNumber()));
    }

    private void loadPage() {
        if (container == null || loading || offset >= total) {
            return;
        }
        loading = true;
        final int asked = offset;
        NotificationsLoader.INSTANCE.page(container, asked, page -> {
            loading = false;
            offset = asked + page.getItems().size();
            total = page.getTotalRows();
            for (NotificationItem item : page.getItems()) {
                items.add(item);
                actors.add(actor(item));
            }
            empty = items.isEmpty();
            notifyRowsChanged();
            fetchAvatars();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            loading = false;
            FileLog.e("notifications failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Asks for the next page once the reader is within a few rows of the end of this one. */
    private void loadNextPageIfNeeded() {
        if (listView == null || listView.getAdapter() == null || items.isEmpty() || offset >= total) {
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

    /** The reader who caused a notification, as a peer upstream can draw a picture for. */
    private TLRPC.User actor(NotificationItem item) {
        return DiscourseAvatars.INSTANCE.user(
                item.getActorUsername(),
                item.getActorName().isEmpty() ? item.getActorUsername() : item.getActorName(),
                item.getActorAvatarTemplate(),
                container.getBaseUrl());
    }

    /**
     * The rows draw their actor's initials until the picture is on disk, so each actor's avatar is
     * fetched once, then the list is bound again.
     */
    private void fetchAvatars() {
        for (int index = 0; index < actors.size(); index++) {
            TLRPC.User actor = actors.get(index);
            String template = index < items.size() ? items.get(index).getActorAvatarTemplate() : "";
            String url = DiscourseAvatars.INSTANCE.url(
                    template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || DiscourseAvatars.INSTANCE.isCached(actor)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, actor, () -> {
                notifyRowsChanged();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class NotificationAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == TYPE_NOTE) {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextInfoPrivacyCell) {
                ((TextInfoPrivacyCell) holder.itemView).setText("还没有通知");
                return;
            }
            NotificationItem item = items.get(position);
            DialogCell cell = (DialogCell) holder.itemView;
            cell.setDialog(DiscourseNotifications.INSTANCE.row(item));
            // `setDialog` clears the row's avatar receiver, so the actor's picture goes in after it.
            TLRPC.User actor = actors.get(position);
            ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(actor);
            if (avatar != null) {
                cell.avatarImage.setImage(
                        avatar, "50_50", cell.avatarImage.getDrawable(), null, actor, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return empty ? TYPE_NOTE : TYPE_NOTIFICATION;
        }

        @Override
        public int getItemCount() {
            return empty ? 1 : items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (empty || holder.itemView instanceof TextInfoPrivacyCell) {
                return false;
            }
            // Only what the site gave a post to open is a link; a badge is not.
            return DiscourseNotifications.INSTANCE.hasDestination(items.get(holder.getAdapterPosition()));
        }
    }
}
