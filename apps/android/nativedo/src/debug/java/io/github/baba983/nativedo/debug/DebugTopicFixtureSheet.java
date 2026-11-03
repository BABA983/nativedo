package io.github.baba983.nativedo.debug;

import android.app.Activity;
import android.view.Gravity;
import android.widget.FrameLayout;

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
