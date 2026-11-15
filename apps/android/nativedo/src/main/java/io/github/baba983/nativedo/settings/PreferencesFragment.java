package io.github.baba983.nativedo.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.UserPreferences;

/**
 * A linux.do preference page drawn the way Telegram draws its own settings sub-pages: a
 * {@code UniversalRecyclerView} of upstream's cells, section headers over shadow lines, check
 * rows for switches and value rows whose tap opens the choice dialog.
 *
 * <p>There is no upstream screen to host here. Telegram's Notifications and Privacy pages read
 * the Telegram account through {@code MessagesController}/{@code ContactsController} - its chats,
 * its blocked peers, its account RPCs - and this client has none of that behind it. What the
 * reader actually changes on linux.do is a set of per-account options, which the site serves from
 * {@code GET /u/{username}.json}'s {@code user_option} and writes back through
 * {@code PUT /u/{username}.json}; this screen draws exactly those options, with the site's own
 * enum values, so a switch flips the same value the site's own preferences page flips.</p>
 */
public class PreferencesFragment extends BaseFragment {

    /** The reader's notification and tracking options: what linux.do tells them about. */
    public static final int MODE_NOTIFICATIONS = 0;
    /** The reader's profile visibility and private-message options. */
    public static final int MODE_PRIVACY = 1;

    private static final int ROW_LOADING = 1;
    private static final int ROW_RETRY = 2;
    private static final int ROW_LIKE_FREQUENCY = 10;
    private static final int ROW_LINKED_POSTS = 11;
    private static final int ROW_REPLY_LEVEL = 12;
    private static final int ROW_AUTO_TRACK = 13;
    private static final int ROW_NEW_TOPIC = 14;
    private static final int ROW_HIDE_PROFILE = 20;
    private static final int ROW_ALLOW_PM = 21;

    /*
     * The site's own enums, not re-numbered: UserOption::like_notification_frequency_type,
     * NotificationLevels, and the preference arrays of Discourse's tracking preferences page.
     */
    private static final int[] LIKE_FREQUENCY_VALUES = {0, 1, 2, 3};
    private static final String[] LIKE_FREQUENCY_LABELS = {"总是", "首次及每日摘要", "仅首次", "从不"};
    private static final int[] REPLY_LEVEL_VALUES = {3, 2, 1};
    private static final String[] REPLY_LEVEL_LABELS = {"关注话题", "跟踪话题", "不做任何事"};
    private static final int[] AUTO_TRACK_VALUES = {-1, 0, 30000, 60000, 120000, 180000, 240000, 300000, 600000};
    private static final String[] AUTO_TRACK_LABELS =
            {"从不", "立即", "30 秒后", "1 分钟后", "2 分钟后", "3 分钟后", "4 分钟后", "5 分钟后", "10 分钟后"};
    private static final int[] NEW_TOPIC_VALUES = {-1, 1440, 2880, 10080, 20160, -2};
    private static final String[] NEW_TOPIC_LABELS =
            {"尚未查看的", "1 天内的", "2 天内的", "1 周内的", "2 周内的", "我上次访问后的"};

    private final int mode;
    private AppContainer container;
    private String username = "";
    private UserPreferences preferences;
    private boolean loading = true;
    private boolean failed;

    private UniversalRecyclerView listView;

    /** The day/night switch recolors this page in place, the way it does the settings tab. */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(false);
                }
                if (simpleActionBar != null) {
                    simpleActionBar.updateColors();
                    simpleActionBar.invalidate();
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            };

    public PreferencesFragment(int mode) {
        this.mode = mode;
    }

    /** Upstream's simple glass header, the same one its settings sub-pages use. */
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected View createView(Context context) {
        setHasOwnBackground(true);
        container = NativeDo.container(getParentActivity());
        if (container != null && container.getSessionStore().snapshot() != null
                && container.getSessionStore().snapshot().getUsername() != null) {
            username = container.getSessionStore().snapshot().getUsername();
        }

        simpleActionBar.setTitle(mode == MODE_NOTIFICATIONS ? "通知" : "隐私");
        simpleActionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        simpleActionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        simpleActionBar.setBackground(null);

        listView = new UniversalRecyclerView(this,
                (items, adapter) -> fillItems(items),
                (item, view, position, x, y) -> onClick(item),
                (item, view, position, x, y) -> false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.setVerticalScrollBarEnabled(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = contentView;
        load();

        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private void load() {
        if (container == null || username.isEmpty()) {
            loading = false;
            failed = true;
            notifyRowsChanged();
            return;
        }
        loading = true;
        failed = false;
        notifyRowsChanged();
        PreferencesLoader.INSTANCE.load(container, username, loaded -> {
            preferences = loaded;
            loading = false;
            failed = false;
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("preferences load failed", error);
            loading = false;
            failed = true;
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items) {
        if (loading || failed || preferences == null) {
            // The checkbox-shaped shimmer stands until the site's answer arrives, and the retry
            // row stands in for one that did not.
            items.add(UItem.asFlicker(ROW_LOADING, FlickerLoadingView.CHECKBOX_TYPE));
            items.add(UItem.asFlicker(ROW_LOADING + 1, FlickerLoadingView.CHECKBOX_TYPE));
            items.add(UItem.asShadow(failed ? "没能读到偏好设置" : null));
            if (failed) {
                items.add(UItem.asButton(ROW_RETRY, "重试"));
            }
            return;
        }

        if (mode == MODE_NOTIFICATIONS) {
            items.add(UItem.asHeader("通知"));
            items.add(UItem.asSettingsCell(ROW_LIKE_FREQUENCY, "点赞通知", chooseValue(
                    LIKE_FREQUENCY_VALUES, LIKE_FREQUENCY_LABELS, preferences.getLikeNotificationFrequency())));
            items.add(UItem.asCheck(ROW_LINKED_POSTS, "有人引用或提及时通知我")
                    .setChecked(preferences.getNotifyOnLinkedPosts()));
            items.add(UItem.asShadow("这些选项保存在你的 linux.do 账号上，和网页版「偏好设置 → 通知」是同一组。"));

            items.add(UItem.asHeader("跟踪"));
            items.add(UItem.asSettingsCell(ROW_REPLY_LEVEL, "回复话题时", chooseValue(
                    REPLY_LEVEL_VALUES, REPLY_LEVEL_LABELS, preferences.getNotificationLevelWhenReplying())));
            items.add(UItem.asSettingsCell(ROW_AUTO_TRACK, "自动跟踪我阅读的话题", chooseValue(
                    AUTO_TRACK_VALUES, AUTO_TRACK_LABELS, preferences.getAutoTrackTopicsAfterMsecs())));
            items.add(UItem.asSettingsCell(ROW_NEW_TOPIC, "新话题的判定", chooseValue(
                    NEW_TOPIC_VALUES, NEW_TOPIC_LABELS, preferences.getNewTopicDurationMinutes())));
            items.add(UItem.asShadow(null));
        } else {
            items.add(UItem.asHeader("资料"));
            items.add(UItem.asCheck(ROW_HIDE_PROFILE, "隐藏公开资料与在线状态")
                    .setChecked(preferences.getHideProfile()));
            items.add(UItem.asShadow("开启后其他用户看不到你的资料页，也看不到你是否在线；网页版「偏好设置 → 个人资料」是同一个开关。"));

            items.add(UItem.asHeader("私信"));
            items.add(UItem.asCheck(ROW_ALLOW_PM, "允许用户给我发私信")
                    .setChecked(preferences.getAllowPrivateMessages()));
            items.add(UItem.asShadow("关闭后其他用户不能给你发站内私信；管理人员仍可通过其他方式联系你。"));
        }
    }

    /** The label of the value a choice row currently has, for the row's right-hand side. */
    private static String chooseValue(int[] values, String[] labels, int value) {
        for (int a = 0; a < values.length; a++) {
            if (values[a] == value) {
                return labels[a];
            }
        }
        return String.valueOf(value);
    }

    private void onClick(UItem item) {
        if (item.id == ROW_RETRY) {
            load();
            return;
        }
        if (preferences == null) {
            return;
        }
        switch (item.id) {
            case ROW_LIKE_FREQUENCY:
                choose("点赞通知", LIKE_FREQUENCY_VALUES, LIKE_FREQUENCY_LABELS,
                        preferences.getLikeNotificationFrequency(), value -> {
                            write("like_notification_frequency", String.valueOf(value),
                                    new UserPreferences(value, preferences.getNotifyOnLinkedPosts(),
                                            preferences.getNotificationLevelWhenReplying(),
                                            preferences.getAutoTrackTopicsAfterMsecs(),
                                            preferences.getNewTopicDurationMinutes(),
                                            preferences.getAllowPrivateMessages(),
                                            preferences.getHideProfile()));
                            return kotlin.Unit.INSTANCE;
                        });
                break;
            case ROW_LINKED_POSTS:
                flipBoolean(!preferences.getNotifyOnLinkedPosts(), "notify_on_linked_posts",
                        checked -> new UserPreferences(preferences.getLikeNotificationFrequency(), checked,
                                preferences.getNotificationLevelWhenReplying(),
                                preferences.getAutoTrackTopicsAfterMsecs(),
                                preferences.getNewTopicDurationMinutes(),
                                preferences.getAllowPrivateMessages(),
                                preferences.getHideProfile()));
                break;
            case ROW_REPLY_LEVEL:
                choose("回复话题时", REPLY_LEVEL_VALUES, REPLY_LEVEL_LABELS,
                        preferences.getNotificationLevelWhenReplying(), value -> {
                            write("notification_level_when_replying", String.valueOf(value),
                                    new UserPreferences(preferences.getLikeNotificationFrequency(),
                                            preferences.getNotifyOnLinkedPosts(), value,
                                            preferences.getAutoTrackTopicsAfterMsecs(),
                                            preferences.getNewTopicDurationMinutes(),
                                            preferences.getAllowPrivateMessages(),
                                            preferences.getHideProfile()));
                            return kotlin.Unit.INSTANCE;
                        });
                break;
            case ROW_AUTO_TRACK:
                choose("自动跟踪我阅读的话题", AUTO_TRACK_VALUES, AUTO_TRACK_LABELS,
                        preferences.getAutoTrackTopicsAfterMsecs(), value -> {
                            write("auto_track_topics_after_msecs", String.valueOf(value),
                                    new UserPreferences(preferences.getLikeNotificationFrequency(),
                                            preferences.getNotifyOnLinkedPosts(),
                                            preferences.getNotificationLevelWhenReplying(), value,
                                            preferences.getNewTopicDurationMinutes(),
                                            preferences.getAllowPrivateMessages(),
                                            preferences.getHideProfile()));
                            return kotlin.Unit.INSTANCE;
                        });
                break;
            case ROW_NEW_TOPIC:
                choose("新话题的判定", NEW_TOPIC_VALUES, NEW_TOPIC_LABELS,
                        preferences.getNewTopicDurationMinutes(), value -> {
                            write("new_topic_duration_minutes", String.valueOf(value),
                                    new UserPreferences(preferences.getLikeNotificationFrequency(),
                                            preferences.getNotifyOnLinkedPosts(),
                                            preferences.getNotificationLevelWhenReplying(),
                                            preferences.getAutoTrackTopicsAfterMsecs(), value,
                                            preferences.getAllowPrivateMessages(),
                                            preferences.getHideProfile()));
                            return kotlin.Unit.INSTANCE;
                        });
                break;
            case ROW_HIDE_PROFILE:
                flipBoolean(!preferences.getHideProfile(), "hide_profile",
                        checked -> new UserPreferences(preferences.getLikeNotificationFrequency(),
                                preferences.getNotifyOnLinkedPosts(),
                                preferences.getNotificationLevelWhenReplying(),
                                preferences.getAutoTrackTopicsAfterMsecs(),
                                preferences.getNewTopicDurationMinutes(),
                                preferences.getAllowPrivateMessages(), checked));
                break;
            case ROW_ALLOW_PM:
                flipBoolean(!preferences.getAllowPrivateMessages(), "allow_private_messages",
                        checked -> new UserPreferences(preferences.getLikeNotificationFrequency(),
                                preferences.getNotifyOnLinkedPosts(),
                                preferences.getNotificationLevelWhenReplying(),
                                preferences.getAutoTrackTopicsAfterMsecs(),
                                preferences.getNewTopicDurationMinutes(), checked,
                                preferences.getHideProfile()));
                break;
        }
    }

    /**
     * A switch the reader flipped: drawn in its new state at once, and put back when the site
     * does not confirm the write. A switch that stays flipped after a refused write is the one
     * wrong answer this screen must not give - the reader would believe the site holds a setting
     * it never took.
     */
    private interface PreferenceChange {
        UserPreferences apply(boolean checked);
    }

    private void flipBoolean(boolean next, String field, PreferenceChange change) {
        UserPreferences before = preferences;
        preferences = change.apply(next);
        notifyRowsChanged();
        PreferencesLoader.INSTANCE.save(container, username,
                Collections.singletonMap(field, next ? "true" : "false"),
                () -> kotlin.Unit.INSTANCE,
                error -> {
                    preferences = before;
                    notifyRowsChanged();
                    showSaveFailure(error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    private interface ValueChoice {
        kotlin.Unit apply(int value);
    }

    /** Writes the chosen value and puts the row back when the site refuses. */
    private void write(String field, String value, UserPreferences updated) {
        UserPreferences before = preferences;
        preferences = updated;
        notifyRowsChanged();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, value);
        PreferencesLoader.INSTANCE.save(container, username, fields,
                () -> kotlin.Unit.INSTANCE,
                error -> {
                    preferences = before;
                    notifyRowsChanged();
                    showSaveFailure(error);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** Upstream's own single-choice dialog: an item list the way Telegram asks for one value. */
    private void choose(String title, int[] values, String[] labels, int current, ValueChoice onChosen) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(labels, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int value = values[which];
                if (value == current) {
                    return;
                }
                onChosen.apply(value);
            }
        });
        showDialog(builder.create());
    }

    /** The site's own message when it refused the write, a plain one otherwise. */
    private void showSaveFailure(Throwable error) {
        if (getParentActivity() == null) {
            return;
        }
        String message = error != null && error.getMessage() != null
                ? error.getMessage() : "网络不通，请稍后再试";
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("没能保存");
        builder.setMessage(message);
        builder.setPositiveButton("确定", null);
        showDialog(builder.create());
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        super.onFragmentDestroy();
    }
}
