// SPDX-License-Identifier: GPL-2.0-or-later
// Port of org/telegram/ui/Adapters/MentionsAdapter.java: upstream's mention rows, bound to linux.do.

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Cells.MentionCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.core.model.MentionCandidate;

/**
 * The rows upstream's mention panel draws, bound to the site's candidates instead of Telegram peers.
 *
 * <p>Upstream's {@code MentionsAdapter} is the type {@code MentionsContainerView} holds its data
 * through, so this is that adapter with Telegram's own search surface (users, chats, bots,
 * stickers, hashtags, inline results) removed: the site answers a blank-or-prefix term with an
 * ordered list of users and mentionable groups, and each is drawn by upstream's own
 * {@link MentionCell}. Nothing here reaches tgnet: a user is a synthetic peer whose photo names a
 * file the module's own HTTP client already put on disk (see {@link DiscourseAvatars}), exactly the
 * way the topic rows draw their authors.</p>
 */
public class DiscourseMentionsAdapter extends MentionsAdapter {

    /** Upstream's own adapter has a delegate for panel visibility; this one is driven directly. */
    private static final MentionsAdapterDelegate NO_DELEGATE = new MentionsAdapterDelegate() {
        @Override
        public void needChangePanelVisibility(boolean show) {
        }

        @Override
        public void onItemCountUpdate(int oldCount, int newCount) {
        }

        @Override
        public void onContextSearch(boolean searching) {
        }

        @Override
        public void onContextClick(TLRPC.BotInlineResult result) {
        }
    };

    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final DiscourseMentionsPanel panel;

    private final List<MentionCandidate> items = new ArrayList<>();
    /** Per row, the peer upstream's cell binds; exactly one of these is non-null. */
    private final List<TLRPC.User> users = new ArrayList<>();
    private final List<TLRPC.Chat> chats = new ArrayList<>();

    public DiscourseMentionsAdapter(
            @NonNull DiscourseMentionsPanel panel,
            @NonNull Context context,
            long dialogId,
            long threadMessageId,
            Theme.ResourcesProvider resourcesProvider) {
        super(context, false, dialogId, threadMessageId, NO_DELEGATE, resourcesProvider, false);
        this.panel = panel;
        this.context = context;
        this.resourcesProvider = resourcesProvider;
    }

    /** The candidate a padded-list position refers to, or null for the padding row. */
    public MentionCandidate itemAt(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    public int candidateCount() {
        return items.size();
    }

    /**
     * Re-binds every visible row. Upstream's own {@code notifyDataSetChanged} only rebinds rows
     * whose data changed; after an avatar lands on disk the data is the same object, so the rows
     * have to be told again for upstream's cell to draw the file it now has.
     */
    public void rebindAll() {
        int count = getItemCountInternal();
        if (count > 0) {
            notifyItemRangeChanged(0, count);
        }
    }

    /**
     * Replaces the rows with the ordered candidates. A user becomes a synthetic peer whose avatar
     * file the module's HTTP client fetched; a group becomes a synthetic chat, so upstream's cell
     * draws its title and the {@code @} the site would insert.
     */
    public void submit(@NonNull List<MentionCandidate> next) {
        items.clear();
        items.addAll(next);
        users.clear();
        chats.clear();
        String baseUrl = panel.mentionBaseUrl();
        for (MentionCandidate candidate : next) {
            if (candidate.isGroup()) {
                users.add(null);
                chats.add(groupChat(candidate));
            } else {
                users.add(DiscourseAvatars.INSTANCE.user(
                        candidate.getUsername(),
                        candidate.getDisplayName(),
                        candidate.getAvatarTemplate(),
                        baseUrl,
                        candidate.getAnimatedAvatarUrl()));
                chats.add(null);
            }
        }
        notifyDataSetChanged();
    }

    /** A mentionable group, shaped so upstream's cell draws a title and a public username. */
    private static TLRPC.Chat groupChat(MentionCandidate candidate) {
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = DiscourseRows.userId(candidate.getUsername());
        chat.title = candidate.getDisplayName().trim().isEmpty()
                ? candidate.getUsername() : candidate.getDisplayName().trim();
        chat.username = candidate.getUsername();
        return chat;
    }

    @Override
    public int getItemCountInternal() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return itemAt(position);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }

    @Override
    public boolean isStickers() {
        return false;
    }

    @Override
    public boolean isBotContext() {
        return false;
    }

    @Override
    public boolean isMediaLayout() {
        return false;
    }

    @Override
    public boolean isBannedInline() {
        return false;
    }

    @Override
    public TLRPC.TL_inlineBotSwitchPM getBotContextSwitch() {
        return null;
    }

    @Override
    public TLRPC.TL_inlineBotWebView getBotWebViewSwitch() {
        return null;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        MentionCell cell = new MentionCell(context, resourcesProvider);
        cell.setIsDarkTheme(panel.mentionIsDarkTheme());
        return new RecyclerListView.Holder(cell);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        MentionCell cell = (MentionCell) holder.itemView;
        cell.setIsDarkTheme(panel.mentionIsDarkTheme());
        cell.setDivider(false);
        TLRPC.User user = position >= 0 && position < users.size() ? users.get(position) : null;
        if (user != null) {
            cell.setUser(user);
            org.telegram.messenger.ImageLocation location = DiscourseAvatars.INSTANCE.imageLocation(user);
            if (location != null) {
                // MentionCell's first child is its upstream avatar view; the synthetic peer's
                // account photo ask cannot carry the local animation location.
                org.telegram.ui.Components.BackupImageView avatar =
                        (org.telegram.ui.Components.BackupImageView) cell.getChildAt(0);
                avatar.setImage(location, "50_50", avatar.getImageReceiver().getDrawable(), user);
            }
            return;
        }
        TLRPC.Chat chat = position >= 0 && position < chats.size() ? chats.get(position) : null;
        if (chat != null) {
            cell.setChat(chat);
        } else {
            cell.setText("");
        }
    }
}
