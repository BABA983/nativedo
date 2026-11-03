package io.github.baba983.nativedo.debug;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.List;

import io.github.baba983.nativedo.settings.SettingsFragment;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * The curated topic list, drawn as upstream's own bottom sheet over a {@link UniversalRecyclerView}
 * of {@link UItem} rows. A row tap opens that topic's native detail page; nothing is written to the
 * site.
 *
 * <p>Debug-only: this lives in {@code src/debug} and is reached only through
 * {@link DebugToolsImpl}.</p>
 */
final class DebugTopicFixtureSheet {

    /** The id of the fixture row at {@code index}; a bare index is enough to find it again. */
    static final int FIXTURE_ROW_BASE = 9100;

    private DebugTopicFixtureSheet() {
    }

    static int rowIdForIndex(int index) {
        return FIXTURE_ROW_BASE + index;
    }

    static void show(SettingsFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final List<DebugTopicFixtures.Fixture> fixtures = DebugTopicFixtures.entries();

        final BottomSheet sheet = new BottomSheet(activity, false);
        // Telegram's sheets that paint through the system navigation bar say so explicitly: the
        // default stops the sheet above the bar, which is the gap this debug sheet used to show.
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, fragment.getResourceProvider()));
        sheet.setTitle("渲染测试帖");

        final UniversalRecyclerView list = new UniversalRecyclerView(
                activity, 0, 0, true,
                (items, adapter) -> {
                    for (int index = 0; index < fixtures.size(); index++) {
                        DebugTopicFixtures.Fixture fixture = fixtures.get(index);
                        items.add(UItem.asButton(
                                rowIdForIndex(index),
                                fixture.label,
                                "#" + fixture.topicId
                                        + (fixture.floor > 0 ? " · " + fixture.floor + " 楼" : "")
                                        + " · " + fixture.category));
                    }
                },
                (item, view, position, x, y) -> {
                    int index = item.id - FIXTURE_ROW_BASE;
                    if (index < 0 || index >= fixtures.size()) {
                        return;
                    }
                    DebugTopicFixtures.Fixture fixture = fixtures.get(index);
                    sheet.dismiss();
                    Integer floor = fixture.floor > 0 ? fixture.floor : null;
                    fragment.presentFragment(TopicFragment.forFloor(fixture.topicId, fixture.label, floor));
                },
                (item, view, position, x, y) -> false,
                fragment.getResourceProvider());
        // The sheet now runs to the screen's bottom edge, so the list reserves the system bar's
        // height for the last row instead of letting the gesture pill sit over it. The height comes
        // from this window's own insets: AndroidUtilities.navigationBarHeight is only filled by
        // screens that host upstream's drawer, not by this shell.
        int navigationBar = 0;
        WindowInsetsCompat rootInsets =
                ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (rootInsets != null) {
            navigationBar = AndroidUtilities.getDefaultWindowInsets(rootInsets, false).bottom;
        }
        if (navigationBar == 0 && Build.VERSION.SDK_INT >= 30) {
            WindowManager manager = activity.getWindowManager();
            navigationBar = manager.getCurrentWindowMetrics().getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom;
        }
        list.setPadding(
                list.getPaddingLeft(),
                list.getPaddingTop(),
                list.getPaddingRight(),
                list.getPaddingBottom() + navigationBar);
        // The padding is a viewport edge, not extra scroll room: a row must never be drawn under
        // the system bar, so the list clips its content there and the sheet's own background shows
        // behind the gesture pill.
        list.setClipToPadding(true);
        // The adapter fills its rows on update, not in the constructor; do it now so the sheet is
        // ready to be laid out (and to be driven by a test).
        list.adapter.update(false);

        FrameLayout container = new FrameLayout(activity);
        container.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, fragment.getResourceProvider()));
        int rowsHeight = AndroidUtilities.dp(52) * fixtures.size();
        int height = Math.min(AndroidUtilities.displaySize.y / 2, rowsHeight + AndroidUtilities.dp(56));
        container.addView(list, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, height, Gravity.TOP));
        sheet.setCustomView(container);
        sheet.show();
    }
}
