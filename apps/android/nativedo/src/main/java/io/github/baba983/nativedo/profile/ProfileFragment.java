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
import io.github.baba983.nativedo.core.model.UserProfile;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;

/**
 * A linux.do reader's profile: {@code /u/{username}.json} drawn with upstream's own list cells.
 *
 * <p>Upstream's {@code ProfileActivity} cannot be this screen. It is a Telegram account's profile:
 * {@code onFragmentCreate} loads the full user through tgnet ({@code loadFullUser} sends
 * {@code TL_users_getFullUser} and the answer is written into Telegram's database), and what it
 * shows beyond the user is the account's own photos, stories, gifts and contacts. A linux.do reader
 * has none of those, and this client never speaks to Telegram - so the head of the screen is a
 * {@link DialogCell} row (the avatar, the reader's name, their handle), the sections are
 * {@link HeaderCell}, the fields are {@link TextDetailCell} and the bio is a
 * {@link TextInfoPrivacyCell}. Every one of those cells is upstream's, drawn the way upstream draws
 * a list row.</p>
 */
public class ProfileFragment extends BaseFragment {

    private static final String ARG_USERNAME = "username";
    private static final String ARG_DISPLAY_NAME = "displayName";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_DETAIL = 2;
    private static final int TYPE_BIO = 3;

    private RecyclerListView listView;
    private final List<Row> rows = new ArrayList<>();
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

        fragmentView = listView;
        loadProfile();
        return fragmentView;
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
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemChanged(0);
            }
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

    private void addIfPresent(String label, String value) {
        if (value != null && !value.isEmpty()) {
            rows.add(Row.detail(label, value));
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

    /** The site writes ISO timestamps; a chat row draws the epoch its own formatter reads. */
    private static int epochSeconds(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0;
        }
        try {
            return (int) Instant.parse(timestamp).getEpochSecond();
        } catch (Exception e) {
            return 0;
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
                    DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
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
                default: {
                    TextDetailCell cell = new TextDetailCell(context, resourceProvider, true, true);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(cell);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder.itemView instanceof DialogCell) {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.setDialog(headerRow(row));
                // `setDialog` clears the row's avatar receiver, so the picture goes in after it - the
                // initials stay as the placeholder until the file is on disk.
                ImageLocation avatar = avatarUser == null ? null : DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
                if (avatar != null) {
                    cell.avatarImage.setImage(avatar, "50_50", cell.avatarImage.getDrawable(), null, avatarUser, 0);
                }
            } else if (holder.itemView instanceof HeaderCell) {
                ((HeaderCell) holder.itemView).setText(row.label);
            } else if (holder.itemView instanceof TextInfoPrivacyCell) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.value);
            } else if (holder.itemView instanceof TextDetailCell) {
                ((TextDetailCell) holder.itemView).setTextAndValue(row.label, row.value, row.divider);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    /** The head of the screen: the reader's picture, their name, and the handle under it. */
    private DialogCell.CustomDialog headerRow(Row row) {
        DialogCell.CustomDialog dialog = new DialogCell.CustomDialog();
        dialog.id = (int) (DiscourseRows.userId(username()) & 0x7fffffff);
        dialog.name = displayName();
        String handle = "@" + username();
        dialog.message = bioText == null || bioText.isEmpty() ? handle : handle + " · " + bioText;
        // A chat row dates itself with the last thing that happened in it; for a reader that is the
        // last time they posted. Left at nothing when the site does not say.
        dialog.date = epochSeconds(profile == null ? null : profile.getLastPostedAt());
        return dialog;
    }

    /** One row of the screen. */
    private static class Row {
        final int type;
        final String label;
        final String value;
        boolean divider;

        private Row(int type, String label, String value) {
            this.type = type;
            this.label = label;
            this.value = value;
        }

        static Row header() {
            return new Row(TYPE_HEADER, null, null);
        }

        static Row section(String label) {
            return new Row(TYPE_SECTION, label, null);
        }

        static Row detail(String label, String value) {
            return new Row(TYPE_DETAIL, label, value);
        }

        static Row bio(String text) {
            return new Row(TYPE_BIO, null, text);
        }
    }
}
