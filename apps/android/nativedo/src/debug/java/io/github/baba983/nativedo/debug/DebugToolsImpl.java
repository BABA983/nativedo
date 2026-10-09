package io.github.baba983.nativedo.debug;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;

import java.util.ArrayList;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.web.DiscourseTopicLink;
import io.github.baba983.nativedo.settings.DebugTools;
import io.github.baba983.nativedo.settings.SettingsFragment;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The debug menu the settings screen shows in a debug build: a curated-topic sheet and a typed
 * jump, drawn with upstream's own widgets ({@link UItem}, {@code UniversalRecyclerView},
 * {@code BottomSheet}, {@code AlertDialog}, {@code EditTextBoldCursor}). Debug-only: the class
 * lives in {@code src/debug} and is loaded reflectively by {@link DebugTools#load()} only while
 * {@code BuildConfig.DEBUG}, so it is not in a release build at all.
 *
 * <p>Every jump validates before it navigates and never writes to the site.</p>
 */
public final class DebugToolsImpl implements DebugTools {

    /** The typed jump: a numeric topic id, or a topic link with an optional floor. */
    static final int ROW_JUMP = 9001;
    /** The curated render-QA topics, opened from a sheet. */
    static final int ROW_FIXTURES = 9002;

    public DebugToolsImpl() {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void fillItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("调试"));
        items.add(UItem.asButton(ROW_FIXTURES, "渲染测试帖", "精选 linux.do 帖子，一键打开验收渲染"));
        items.add(UItem.asButton(ROW_JUMP, "跳转话题", "话题 ID，或本站话题链接"));
        items.add(UItem.asShadow("仅调试构建可见；只读，不会写入站点。"));
    }

    @Override
    public boolean onItemClick(int id, SettingsFragment fragment) {
        if (id == ROW_FIXTURES) {
            DebugTopicFixtureSheet.show(fragment);
            return true;
        }
        if (id == ROW_JUMP) {
            showJumpDialog(fragment);
            return true;
        }
        return false;
    }

    /**
     * The typed jump: an {@link EditTextBoldCursor} in an {@link AlertDialog}. The text is read by
     * {@link DebugTopicInput} - the app's own link rule plus a bare id - and an entry that names no
     * topic leaves the dialog open with an error instead of navigating.
     */
    private void showJumpDialog(SettingsFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AppContainer container = NativeDo.container(activity);
        String baseUrl = container.getBaseUrl();

        EditTextBoldCursor input = new EditTextBoldCursor(activity);
        input.setHint("话题 ID 或链接");
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogIcon, fragment.getResourceProvider()));
        input.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);

        FrameLayout field = new FrameLayout(activity);
        field.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        field.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP));

        AlertDialog dialog = new AlertDialog.Builder(activity, fragment.getResourceProvider())
                .setTitle("跳转话题")
                .setMessage("输入数字话题 ID，或 nativedo://topic/<id> / https://linux.do/t/... 链接（可带楼层）。")
                .setView(field)
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .setPositiveButton("打开", (d, which) -> {
                    DiscourseTopicLink link = validate(input, baseUrl);
                    if (link == null) {
                        return;
                    }
                    d.dismiss();
                    navigate(fragment, link);
                })
                .create();
        // The button does not dismiss on its own: the entry is validated first, and only a valid one
        // closes the dialog.
        dialog.setDismissDialogByButtons(false);
        input.setOnEditorActionListener((v, actionId, event) -> {
            DiscourseTopicLink link = validate(input, baseUrl);
            if (link != null) {
                dialog.dismiss();
                navigate(fragment, link);
            }
            return true;
        });
        fragment.showDialog(dialog);
    }

    /** The topic the entry names, or null (with the field marked) when it names none. */
    private static DiscourseTopicLink validate(EditTextBoldCursor input, String baseUrl) {
        DiscourseTopicLink link = DebugTopicInput.parse(input.getText().toString(), baseUrl);
        if (link == null) {
            input.setError("无效的话题 ID 或链接");
        }
        return link;
    }

    private static void navigate(SettingsFragment fragment, DiscourseTopicLink link) {
        Integer floor = link.getFloor() > 0 ? link.getFloor() : null;
        fragment.presentFragment(TopicFragment.forFloor(link.getTopicId(), null, floor));
    }
}
