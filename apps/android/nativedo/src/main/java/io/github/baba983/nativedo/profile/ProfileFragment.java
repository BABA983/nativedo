package io.github.baba983.nativedo.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ProfileGooeyView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProfileActivity;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

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
 * A linux.do reader's profile: {@code /u/{username}.json} drawn with upstream's own list cells and
 * upstream's own collapsing profile header.
 *
 * <p>Upstream's {@code ProfileActivity} cannot be this screen. It is a Telegram account's profile:
 * {@code onFragmentCreate} loads the full user through tgnet ({@code loadFullUser} sends
 * {@code TL_users_getFullUser} and the answer is written into Telegram's database), and what it
 * shows beyond the user is the account's own photos, stories, gifts and contacts. A linux.do reader
 * has none of those, so this screen keeps its own {@link BaseFragment} and builds the head out of
 * upstream's own pieces: a {@link ProfileGooeyView} holding the avatar, and the two text views
 * upstream moves into the action bar as the header collapses - the same header behavior the owner
 * sees in Telegram's own profile. The screen's own action bar hosts that header the way upstream's
 * profile does (transparent, inside the fragment's view), and the sections below it are
 * {@link HeaderCell}, {@link TextDetailCell} and {@link TextInfoPrivacyCell}, every one of them
 * upstream's, drawn the way upstream draws a list row.</p>
 */
public class ProfileFragment extends BaseFragment {

    private static final String ARG_USERNAME = "username";
    private static final String ARG_DISPLAY_NAME = "displayName";

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

    private FrameLayout frameLayout;
    private RecyclerListView listView;
    private final List<Row> rows = new ArrayList<>();
    /** The row of the reader's own bookmarks, or -1 when this is not their own profile. */
    private int bookmarksRow = -1;
    /** The reader's actions, as the site sent them; the list's rows after the profile's own. */
    private final List<TopicSummary> actions = new ArrayList<>();
    private ProfileActivityFeed feed;
    private boolean loadingActions;
    private UserProfile profile;
    /** The gesture bar the list has to clear; the shell forwards it, this screen pads its end. */
    private int bottomInset;
    /** The site's bio with its cooked tags taken off, which both the head and the bio row draw. */
    private String bioText = "";
    private TLRPC.User avatarUser;
    private AppContainer container;

    // The collapsing header, ported from ProfileActivity: the gooey avatar and the two text views
    // its needLayout() translates and scales as the list scrolls.
    private TopView topView;
    private ProfileActivity.AvatarImageView avatarImage;
    private FrameLayout avatarContainer;
    private FrameLayout avatarContainer2;
    private ProfileGooeyView avatarGooey;
    private SimpleTextView nameTextView;
    private LinkSpanDrawable.ClickableSmallTextView onlineTextView;
    /** The list's offset from the header's rest position; 0 when the header is fully collapsed. */
    private float extraHeight;
    private float pullUpProgress;
    private float avatarX;
    private float avatarY;
    private float avatarScale;
    private float nameX;
    private float nameY;
    private float onlineX;
    private float onlineY;

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
        // The profile draws behind its own transparent action bar, the way upstream's profile does
        // (ProfileActivity.java:2470-2478), so it keeps its view the whole screen.
        actionBar.setAddToContainer(false);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setForceSkipTouches(true);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
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

        extraHeight = getHeaderExtraHeight();

        frameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkListViewScroll();
            }
        };

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new ProfileAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, getHeaderExtraHeight(), 0, listBottomPadding());
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
                checkListViewScroll();
                loadNextActionsIfNeeded();
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        // The header's backdrop: the rows that scroll under the avatar and the name stay behind it.
        topView = new TopView(context);
        frameLayout.addView(topView);

        avatarContainer2 = new FrameLayout(context);
        avatarContainer2.addView(avatarGooey = new ProfileGooeyView(context),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Upstream's avatar container and picture: the container's scale is what the gooey view
        // follows, and its measure and scale updates are where the gooey view is driven from
        // (ProfileActivity.java:5220-5232 and 5326-5357).
        avatarContainer = new FrameLayout(context) {
            @Override
            public void setScaleX(float scaleX) {
                super.setScaleX(scaleX);
                updateGooey();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                updateGooey();
            }
        };
        avatarContainer.setPivotX(0);
        avatarContainer.setPivotY(0);
        avatarGooey.addView(avatarContainer, LayoutHelper.createFrame(100, 100, Gravity.TOP | Gravity.LEFT));
        avatarImage = new ProfileActivity.AvatarImageView(context);
        avatarImage.createBlurEffect(getActionsExtraHeight());
        avatarImage.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarImage.setRoundRadiusForExpand(getSmallAvatarRoundRadius());
        avatarImage.setPivotX(0);
        avatarImage.setPivotY(0);
        avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // The two text views upstream's needLayout() moves into the action bar as the header
        // collapses (ProfileActivity.java:5548-5637); the name is the header's readable copy.
        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(getThemedColor(Theme.key_profile_title));
        nameTextView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(4));
        nameTextView.setTextSizePx(AndroidUtilities.dp(17.5f));
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        nameTextView.setPivotX(0);
        nameTextView.setPivotY(0);
        nameTextView.setText(displayName());
        nameTextView.setScrollNonFitText(true);
        nameTextView.setEllipsizeByGradient(true);
        avatarContainer2.addView(nameTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 109, -6, 0, 0));

        onlineTextView = new LinkSpanDrawable.ClickableSmallTextView(context);
        onlineTextView.setEllipsizeByGradient(true);
        onlineTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        onlineTextView.setTextSizePx(AndroidUtilities.dp(13.5f));
        onlineTextView.setGravity(Gravity.LEFT);
        onlineTextView.setAlpha(1.0f);
        onlineTextView.setPivotX(AndroidUtilities.dp(8));
        onlineTextView.setPivotY(AndroidUtilities.dp(8));
        onlineTextView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));
        onlineTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        onlineTextView.setText("@" + username());
        avatarContainer2.addView(onlineTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 105, -2, 4, 0));

        frameLayout.addView(avatarContainer2, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START));
        frameLayout.addView(actionBar);

        // The shell's insets land on the fragment view the way NativeDoActivity's land on its
        // content view; this screen keeps the last list row clear of the gesture bar. The bottom
        // is read from the same helper the shell uses, so the list pads by exactly the bar.
        ViewCompat.setOnApplyWindowInsetsListener(frameLayout, (view, insets) -> {
            bottomInset = AndroidUtilities.getDefaultWindowInsets(insets, false).bottom;
            listView.setPadding(0, listView.getPaddingTop(), 0, listBottomPadding());
            return WindowInsetsCompat.CONSUMED;
        });

        fragmentView = frameLayout;
        loadProfile();
        ViewCompat.requestApplyInsets(frameLayout);
        return fragmentView;
    }

    /** The room the list keeps under its last row: the usual list end plus the gesture bar. */
    private int listBottomPadding() {
        return AndroidUtilities.dp(4) + bottomInset;
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
            bindAvatar();
            fetchAvatar();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("profile failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The header's picture, once the site's bytes are on disk, the way the head cell used to read it. */
    private void bindAvatar() {
        if (avatarImage == null || avatarUser == null) {
            return;
        }
        ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
        if (avatar != null) {
            avatarImage.setImage(avatar, "50_50", avatarImage.getImageReceiver().getDrawable(), avatarUser);
        }
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
            bindAvatar();
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
            if (holder.itemView instanceof HeaderCell) {
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
     * The header's own height above the list, as upstream's profile computes it
     * (ProfileActivity.java:6036-6056). The action row Telegram draws under the header - call,
     * video, mute - is left out, which is why the actions height is zero and the header needs 168.
     */
    private int getActionsExtraHeight() {
        return 0;
    }

    private int getHeaderOnlyExtraHeight() {
        if (getActionsExtraHeight() == 0) {
            return dp(168f);
        }
        return dp(152f);
    }

    private int getHeaderExtraHeight() {
        return getHeaderOnlyExtraHeight() + getActionsExtraHeight();
    }

    /** The avatar's own round radius: a linux.do reader is not a topic, so it is the round one. */
    private int getSmallAvatarRoundRadius() {
        return dp(50);
    }

    /** calculateHeaderExtraDiff (ProfileActivity.java:6411) */
    private float calculateHeaderExtraDiff() {
        return Utilities.clamp01((extraHeight - getActionsExtraHeight()) / (getHeaderOnlyExtraHeight()));
    }

    /** checkListViewScroll (ProfileActivity.java:8002) */
    private void checkListViewScroll() {
        if (listView.getVisibility() != View.VISIBLE) {
            return;
        }
        if (listView.getChildCount() <= 0) {
            return;
        }

        int newOffset = 0;
        View child = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAdapterPosition(listView.getChildAt(i)) == 0) {
                child = listView.getChildAt(i);
                break;
            }
        }
        RecyclerListView.Holder holder = child == null ? null : (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child == null ? 0 : child.getTop();
        int adapterPosition = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
        if (top >= 0 && adapterPosition == 0) {
            newOffset = top;
        }

        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            needLayout(true);
        }
    }

    /** needLayout (ProfileActivity.java:8368); the open-animation branch and the write button are left out. */
    private void needLayout(boolean animated) {
        final int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        final float diff = calculateHeaderExtraDiff();
        boolean textMeasured = false;

        FrameLayout.LayoutParams layoutParams;
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
            }
        }

        if (avatarContainer != null) {
            listView.setTopGlowOffset((int) extraHeight);
            listView.setOverScrollMode(extraHeight > getHeaderExtraHeight() && extraHeight < listView.getMeasuredWidth() + getActionsExtraHeight() - newTop ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_ALWAYS);
        }

        final int headerExtraHeight = getHeaderExtraHeight();

        if (avatarContainer != null) {
            float endNameY =
                (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                + ActionBar.getCurrentActionBarHeight() / 2f * (1f + diff)
                - dp(21)
                + actionBar.getTranslationY();

            float endY = -dp(29);
            if (avatarGooey != null && avatarGooey.notchInfo != null && avatarGooey.notchInfo.isLikelyCircle) {
                endY = avatarGooey.notchInfo.bounds.centerY();
            }
            float startY =
                (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                    + ActionBar.getCurrentActionBarHeight()
                    - dp(21)
                    + actionBar.getTranslationY();
            avatarY = lerp(endY, startY, diff);

            float h = extraHeight;
            if (h > headerExtraHeight) {
                // Upstream's pull-down expansion (ProfileGalleryView and its expandAnimator) lives
                // in this branch; this screen leaves it out.
            } else {
                float intoSize = 24f;
                if (avatarGooey != null && avatarGooey.notchInfo != null && avatarGooey.notchInfo.isLikelyCircle) {
                    intoSize = 0.5f * avatarGooey.notchInfo.bounds.width() / AndroidUtilities.density;
                }
                avatarScale = lerp(intoSize, 96, diff) / 100f;
                pullUpProgress = 1.0f - diff;

                final float nameScale = 1.0f + 0.12f * diff;
                avatarContainer.setScaleX(avatarScale);
                avatarContainer.setScaleY(avatarScale);
                fixAvatarImageInCenter();
                avatarContainer.setTranslationY((float) Math.ceil(avatarY));
                final float avatarBottom = (float) Math.floor(endNameY) + (avatarContainer.getHeight() * avatarContainer.getScaleY() + dpf2(8)) * diff;
                nameY = avatarBottom + dp(1.3f) + dp(7) * diff;
                onlineY = avatarBottom + dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff;
                final float minimizedX = -dpf2(42 + 4);

                int viewportWidth = listView.getMeasuredWidth();
                // Upstream runs this over two copies of the text; the one that collapses with the
                // header is the second one, which is the only one this screen builds.
                nameTextView.setScaleX(nameScale);
                nameTextView.setScaleY(nameScale);
                textMeasured = true;
                needLayoutText(diff, 0, false);

                FrameLayout.LayoutParams params1 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
                float nameX = viewportWidth / 2f - (params1.leftMargin + Math.min(nameTextView.getExactWidth(), params1.width) * nameScale * 0.5f);
                FrameLayout.LayoutParams params2 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
                float onlineX = viewportWidth / 2f - (params2.leftMargin + Math.min(onlineTextView.getExactWidth(), params2.width) * 0.5f);

                this.nameX = nameX;
                this.onlineX = onlineX;
                nameX = AndroidUtilities.lerp(minimizedX, nameX, diff);
                onlineX = AndroidUtilities.lerp(minimizedX, onlineX, diff);

                nameTextView.setTranslationX(nameX);
                nameTextView.setTranslationY(nameY);
                onlineTextView.setTranslationX(onlineX);
                onlineTextView.setTranslationY(onlineY);
                updateTextLayoutBasedOnTranslation();
            }
            if (!textMeasured) {
                needLayoutText(diff);
            }
        }

        updateExtraViews(newTop);
        if (diff >= 1f) {
            avatarImage.setAlpha(1f);
        }
    }

    /** needLayoutText (ProfileActivity.java:8935); the media-counter and its copies are left out. */
    private void needLayoutText(float diff, int startWidth, boolean invalidate) {
        FrameLayout.LayoutParams layoutParams;
        float minScale, maxScale;
        if (extraHeight > getHeaderExtraHeight()) {
            minScale = maxScale = nameTextView.getScaleX();
        } else if (diff >= 1f) {
            minScale = maxScale = Math.max(nameTextView.getScaleX(), 1.12f);
        } else {
            minScale = 1f;
            maxScale = 1.12f;
        }

        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        // Upstream adds 48 dp for each action-bar item it may show (search, edit, call, video);
        // this screen's bar carries the back button only, so those counts stay zero.
        int extra = 0;
        int buttonsWidth = AndroidUtilities.dp(47 + 8 + (40 + extra));
        int minOnlineWidth = viewWidth - buttonsWidth;
        int minWidth = (int) (minOnlineWidth / (startWidth != 0 ? 1f : minScale));
        int maxOnlineWidth = startWidth != 0 ? startWidth : viewWidth - AndroidUtilities.dp(18) * 2;
        int maxWidth = startWidth != 0 ? startWidth : (int) (maxOnlineWidth / maxScale);

        int width = AndroidUtilities.lerp(minWidth, maxWidth, CubicBezierInterpolator.EASE_IN.getInterpolation(diff));
        layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        int prevWidth = layoutParams.width;
        layoutParams.width = (int) Math.ceil(width);

        if (invalidate) {
            float x = layoutParams.leftMargin + nameTextView.getTranslationX();
            layoutParams.width = Math.min(layoutParams.width, (int) ((viewWidth - AndroidUtilities.dp(18f)) / nameTextView.getScaleX() - x));
        }
        if (invalidate && layoutParams.width != prevWidth) {
            nameTextView.requestLayout();
        }

        width = AndroidUtilities.lerp(minOnlineWidth, maxOnlineWidth, CubicBezierInterpolator.EASE_IN.getInterpolation(diff));
        layoutParams = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
        prevWidth = layoutParams.width;
        layoutParams.width = (int) Math.ceil(width);
        if (prevWidth != layoutParams.width) {
            onlineTextView.requestLayout();
        }
    }

    private void needLayoutText(float diff) {
        needLayoutText(diff, 0, true);
    }

    /** updateTextLayoutBasedOnTranslation (ProfileActivity.java:8925) */
    private void updateTextLayoutBasedOnTranslation() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        int old = params.width;
        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        float x = params.leftMargin + nameTextView.getTranslationX();
        params.width = Math.min(params.width, (int) ((viewWidth - AndroidUtilities.dp(18f)) / nameTextView.getScaleX() - x));
        if (params.width != old) {
            nameTextView.requestLayout();
        }
    }

    /** fixAvatarImageInCenter (ProfileActivity.java:6420) */
    private void fixAvatarImageInCenter() {
        if (listView == null) return;
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        avatarX = listView.getMeasuredWidth() / 2f - (params.width * avatarScale * 0.5f);
        avatarContainer.setTranslationX(avatarX);
    }

    /** updateExtraViews (ProfileActivity.java:8802); the emoji-status and action placements are Telegram-only. */
    private void updateExtraViews(float newTop) {
        if (topView != null) {
            topView.invalidate();
        }
    }

    /** updateGooey (ProfileActivity.java:6009); the topic and stories branches are Telegram-only. */
    private void updateGooey() {
        avatarGooey.setPullProgress(pullUpProgress);
        avatarGooey.setBlurIntensity(Math.min((MathUtils.clamp(pullUpProgress, 0.2f, 0.7f) - 0.2f) / 0.5f, 0.75f));
        avatarGooey.setGooeyEnabled(pullUpProgress > 0 && pullUpProgress < 1);
        avatarGooey.setVisibility(pullUpProgress >= 1.0f ? View.GONE : View.VISIBLE);
    }

    /** fixLayout (ProfileActivity.java:8996) */
    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    checkListViewScroll();
                    needLayout(true);
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    /**
     * Upstream's header backdrop (ProfileActivity.java:1214), the view that paints the screen's
     * background over the rows scrolling under the header. Its peer-color gradient is left out.
     */
    private class TopView extends FrameLayout {

        private final Paint paint = new Paint();

        public TopView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int height = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            final float v = height + extraHeight;
            final int y1 = (int) v;
            if (y1 != 0) {
                paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, 0, getMeasuredWidth(), y1, paint);
            }
        }
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
