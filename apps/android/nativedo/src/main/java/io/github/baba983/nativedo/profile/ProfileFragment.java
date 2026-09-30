package io.github.baba983.nativedo.profile;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.core.model.UserProfile;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * A linux.do reader's profile: {@code /u/{username}.json} drawn with upstream's own list cells.
 *
 * <p>Upstream's {@code ProfileActivity} cannot be this screen. It is a Telegram account's profile:
 * {@code onFragmentCreate} loads the full user through tgnet ({@code loadFullUser} sends
 * {@code TL_users_getFullUser} and the answer is written into Telegram's database), and what it
 * shows beyond the user is the account's own photos, stories, gifts and contacts. A linux.do reader
 * has none of those, and this client never speaks to Telegram - so the head of the screen is the
 * cell upstream draws a person with in its own lists, a {@link ProfileSearchCell} (the reader's
 * picture, their name, and one line under it), the sections are {@link HeaderCell}, the fields are
 * {@link TextDetailCell} and the bio is a {@link TextInfoPrivacyCell}. Every one of those cells is
 * upstream's, drawn the way upstream draws a list row.</p>
 */
public class ProfileFragment extends BaseFragment {

    private static final String ARG_USERNAME = "username";
    private static final String ARG_DISPLAY_NAME = "displayName";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_DETAIL = 2;
    private static final int TYPE_BIO = 3;
    private static final int TYPE_ACTION = 4;
    /** A row of the reader's own things, which opens a screen of its own. */
    private static final int TYPE_LINK = 5;

    /** The activity stream's own heading, above the rows of the reader's actions. */
    private static final String ACTION_SECTION = "动态";

    /** How close to the end the list comes before the feed asks the site for more. */
    private static final int LOAD_MORE_THRESHOLD = 3;

    private RecyclerListView listView;
    private final List<Row> rows = new ArrayList<>();
    /** The row of the reader's own bookmarks, or -1 when this is not their own profile. */
    private int bookmarksRow = -1;
    /** The reader's actions, as the site sent them; the list's rows after the profile's own. */
    private final List<TopicSummary> actions = new ArrayList<>();
    private ProfileActivityFeed feed;
    private boolean loadingActions;
    private UserProfile profile;
    /** The site's bio with its cooked tags taken off, which both the head and the bio row draw. */
    private String bioText = "";
    private TLRPC.User avatarUser;
    private AppContainer container;

    public static ProfileFragment forUser(String username, String displayName) {
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username == null ? "" : username);
        args.putString(ARG_DISPLAY_NAME, displayName == null ? "" : displayName);
        return new ProfileFragment(args);
    }

    public ProfileFragment(Bundle args) {
        super(args);
    }

    @Override
    protected View createView(Context context) {
        actionBar.setTitle(displayName());
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new ProfileAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView.setOnItemClickListener((view, position) -> {
            if (position == bookmarksRow) {
                presentFragment(new io.github.baba983.nativedo.bookmarks.BookmarksFragment());
                return;
            }
            TopicSummary action = actionAt(position);
            if (action != null) {
                presentAction(action);
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                loadNextActionsIfNeeded();
            }
        });

        fragmentView = listView;
        loadProfile();
        return fragmentView;
    }

    /**
     * An action opens the topic it happened in, at the floor it happened on: the site answers a
     * request for a floor with the window of posts around it, so the reader lands on the post the
     * row is about rather than at the top of a topic that may be hundreds of floors long.
     */
    private void presentAction(TopicSummary action) {
        presentFragment(TopicFragment.forFloor(
                action.getId(), action.getTitle(), action.getTargetPostNumber()));
    }

    private String username() {
        return getArguments() == null ? "" : getArguments().getString(ARG_USERNAME, "");
    }

    private String displayName() {
        String name = getArguments() == null ? "" : getArguments().getString(ARG_DISPLAY_NAME, "");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return username();
    }

    private void loadProfile() {
        container = NativeDo.container(getParentActivity());
        feed = new ProfileActivityFeed(container.getProfileFeedRepository(), username());
        loadActions();
        ProfileLoader.INSTANCE.load(container, username(), loaded -> {
            profile = loaded;
            avatarUser = DiscourseAvatars.INSTANCE.user(
                    loaded.getUsername(),
                    loaded.getDisplayName(),
                    loaded.getAvatarTemplate(),
                    container.getBaseUrl());
            buildRows(loaded);
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            fetchAvatar();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("profile failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The avatar travels from the site's own template the way the topic list's rows fetch theirs. */
    private void fetchAvatar() {
        String url = DiscourseAvatars.INSTANCE.url(
                profile == null ? null : profile.getAvatarTemplate(),
                container.getBaseUrl(),
                DiscourseAvatars.DEFAULT_SIZE);
        if (url == null || avatarUser == null || DiscourseAvatars.INSTANCE.isCached(avatarUser)) {
            return;
        }
        DiscourseLoader.INSTANCE.cacheAvatar(container, url, avatarUser, () -> {
            // The head and every action row draw the same picture, so all of them are re-bound.
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The screen's rows, in the order it shows them. Every field the site sent and left non-empty
     * becomes one row; a field the reader's own site profile does not carry is not a row of dashes.
     */
    private void buildRows(UserProfile loaded) {
        rows.clear();
        rows.add(Row.header());
        rows.add(Row.section("资料"));
        rows.add(Row.detail("信任等级", String.valueOf(loaded.getTrustLevel())));
        addIfPresent("头衔", loaded.getTitle());
        addIfPresent("用户组", loaded.getPrimaryGroupName());
        addIfPresent("加入时间", date(loaded.getCreatedAt()));
        addIfPresent("最近活跃", date(loaded.getLastSeenAt()));
        rows.add(Row.section("统计"));
        addIfPresent("阅读时长", readTime(loaded.getTimeRead()));
        rows.add(Row.detail("浏览", String.valueOf(loaded.getProfileViewCount())));
        rows.add(Row.detail("徽章", String.valueOf(loaded.getBadgeCount())));
        rows.add(Row.detail("得分", String.valueOf(loaded.getGamificationScore())));
        rows.add(Row.detail("关注者", String.valueOf(loaded.getTotalFollowers())));
        rows.add(Row.detail("关注中", String.valueOf(loaded.getTotalFollowing())));
        rows.add(Row.detail("被采纳回复", String.valueOf(loaded.getAcceptedAnswers())));
        // The site sends the bio as cooked HTML; the head and the bio row both draw text, so the
        // tags come off once, here.
        bioText = loaded.getBio() == null ? "" : org.jsoup.Jsoup.parse(loaded.getBio()).text().trim();
        if (!bioText.isEmpty()) {
            rows.add(Row.section("关于我"));
            rows.add(Row.bio(bioText));
        }
        addOwnRows();
        // Upstream separates the rows of a section with a divider, and the last row of each section
        // ends the list's section rather than continuing it.
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.type != TYPE_DETAIL) {
                continue;
            }
            Row next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            row.divider = next != null && next.type == TYPE_DETAIL;
        }
    }

    /**
     * The reader's own things, which only their own profile lists: the site answers a bookmark list
     * for the reader themselves and nobody else, so a row that opened it from another reader would
     * open the reader's own list under someone else's name.
     */
    private void addOwnRows() {
        bookmarksRow = -1;
        if (!isOwnProfile()) {
            return;
        }
        rows.add(Row.section("我的"));
        bookmarksRow = rows.size();
        rows.add(Row.link("书签"));
    }

    private boolean isOwnProfile() {
        if (container == null || container.getSessionStore().snapshot() == null || profile == null) {
            return false;
        }
        String reader = container.getSessionStore().snapshot().getUsername();
        String shown = profile.getUsername();
        return reader != null && shown != null && reader.equalsIgnoreCase(shown);
    }

    private void addIfPresent(String label, String value) {
        if (value != null && !value.isEmpty()) {
            rows.add(Row.detail(label, value));
        }
    }

    /**
     * The reader's activity: the topics they started and the replies they wrote, as one stream
     * newest first ({@link ProfileActivityFeed} merges the site's two filters). Its rows go after
     * everything the profile itself says, and the next page arrives as the reader reaches the end of
     * the list - a profile has no "load more" button on the site either.
     */
    private void loadActions() {
        if (feed == null || loadingActions) {
            return;
        }
        loadingActions = true;
            feed.loadMore(() -> {
            loadingActions = false;
            List<TopicSummary> merged = feed.actions();
            actions.clear();
            actions.addAll(merged);
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Asks for the next page once the reader is within a few rows of the end of what is there. */
    private void loadNextActionsIfNeeded() {
        if (listView == null || listView.getAdapter() == null || actions.isEmpty()) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null
                || manager.findLastVisibleItemPosition()
                < listView.getAdapter().getItemCount() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        loadActions();
    }

    /** The action the row at [position] draws, or null when it is a row of the profile itself. */
    private TopicSummary actionAt(int position) {
        int index = position - rows.size();
        return index >= 1 && index <= actions.size() ? actions.get(index - 1) : null;
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    /** The site writes ISO timestamps; a reader reads them as a date. */
    private static String date(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(timestamp));
        } catch (Exception e) {
            return timestamp;
        }
    }

    /** The site counts seconds read; a reader reads them as hours once there are any. */
    private static String readTime(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        return (seconds / 3600) + " 小时";
    }

    private class ProfileAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            switch (viewType) {
                case TYPE_HEADER: {
                    ProfileSearchCell cell = new ProfileSearchCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_SECTION: {
                    HeaderCell cell = new HeaderCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_BIO: {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_LINK: {
                    org.telegram.ui.Cells.TextSettingsCell cell = new org.telegram.ui.Cells.TextSettingsCell(context, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
                case TYPE_ACTION: {
                    // An action is a topic, so its row is the row the topic list draws.
                    DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
                    return new RecyclerListView.Holder(cell);
                }
                default: {
                    TextDetailCell cell = new TextDetailCell(context, resourceProvider, true, true);
                    // The same one line `ProfileActivity` sets on every cell of its info block: a
                    // reader who hears the row hears the answer first.
                    cell.setContentDescriptionValueFirst(true);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rowAt(position);
            if (holder.itemView instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                TLRPC.User person = avatarUser != null ? avatarUser : headUser();
                // `setData` asks for the account's own photo of that user, which this client has
                // none of; the site's picture goes in after it, the way the topic list's rows do.
                cell.setData(person, null, displayName(), headSubtitle(), false, false);
                ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(person);
                if (avatar != null) {
                    cell.avatarImage.setImage(avatar, "50_50", cell.avatarImage.getDrawable(), null, person, 0);
                }
            } else if (holder.itemView instanceof HeaderCell) {
                ((HeaderCell) holder.itemView).setText(row.label);
            } else if (holder.itemView instanceof TextInfoPrivacyCell) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.value);
            } else if (holder.itemView instanceof DialogCell) {
                // An action is drawn as the topic it happened in: its title, the excerpt the site
                // computed, and when it happened. The picture is the reader's own, the one row and
                // head share.
                DialogCell cell = (DialogCell) holder.itemView;
                cell.setDialog(DiscourseRows.topicRow(
                        row.action, TopicReadMarks.INSTANCE.isReadInFull(row.action.getId())));
                ImageLocation avatar = avatarUser == null
                        ? null
                        : DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
                if (avatar != null) {
                    cell.avatarImage.setImage(
                            avatar, "50_50", cell.avatarImage.getDrawable(), null, avatarUser, 0);
                }
            } else if (holder.itemView instanceof org.telegram.ui.Cells.TextSettingsCell) {
                ((org.telegram.ui.Cells.TextSettingsCell) holder.itemView)
                        .setTextAndValue(row.label, null, true);
            } else if (holder.itemView instanceof TextDetailCell) {
                // Upstream's own convention, as `ProfileActivity`'s info block uses it: the cell's
                // first argument is the answer and lands in its 16 dp line, the second is the field's
                // name and lands in its 13 dp line (the phone number above the word "Mobile").
                TextDetailCell cell = (TextDetailCell) holder.itemView;
                cell.setTextAndValue(row.value, row.label, row.divider);
            }
        }

        /** The screen's rows: what the profile says, then the reader's own actions. */
        private Row rowAt(int position) {
            if (position < rows.size()) {
                return rows.get(position);
            }
            int index = position - rows.size();
            return index == 0 ? Row.section(ACTION_SECTION) : Row.action(actions.get(index - 1));
        }

        @Override
        public int getItemViewType(int position) {
            return rowAt(position).type;
        }

        @Override
        public int getItemCount() {
            return rows.size() + (actions.isEmpty() ? 0 : actions.size() + 1);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // Only an action is a link to somewhere; the profile's own rows are text to read.
            return holder.itemView instanceof DialogCell;
        }
    }

    /**
     * The head's person: the reader the screen was opened for, as a peer upstream can draw. The
     * picture comes with the profile; until it lands this is the same person without one.
     */
    private TLRPC.User headUser() {
        return DiscourseAvatars.INSTANCE.user(username(), displayName(), "", container.getBaseUrl());
    }

    /** The line under the head's name: how the site names them, and the bio when there is one. */
    private String headSubtitle() {
        String handle = "@" + username();
        return bioText == null || bioText.isEmpty() ? handle : handle + " · " + bioText;
    }

    /** One row of the screen. */
    private static class Row {
        final int type;
        final String label;
        final String value;
        /** The action an action row draws; null for every other row. */
        final TopicSummary action;
        boolean divider;

        private Row(int type, String label, String value, TopicSummary action) {
            this.type = type;
            this.label = label;
            this.value = value;
            this.action = action;
        }

        static Row header() {
            return new Row(TYPE_HEADER, null, null, null);
        }

        static Row section(String label) {
            return new Row(TYPE_SECTION, label, null, null);
        }

        static Row detail(String label, String value) {
            return new Row(TYPE_DETAIL, label, value, null);
        }

        static Row bio(String text) {
            return new Row(TYPE_BIO, null, text, null);
        }

        static Row link(String label) {
            return new Row(TYPE_LINK, label, null, null);
        }

        static Row action(TopicSummary action) {
            return new Row(TYPE_ACTION, null, null, action);
        }
    }
}
