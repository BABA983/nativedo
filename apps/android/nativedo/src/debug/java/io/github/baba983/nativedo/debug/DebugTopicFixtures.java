package io.github.baba983.nativedo.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Curated linux.do topics for native render QA. Debug-only (this file lives in {@code src/debug}),
 * and read-only: a jump opens the topic, it never writes to the site.
 */
public final class DebugTopicFixtures {

    /**
     * One curated topic: its id, the floor to land on when the point is a post rather than the
     * topic's head (0 opens the head), the label it is known by, and the rendering area it
     * exercises.
     */
    public static final class Fixture {
        public final long topicId;
        public final int floor;
        public final String label;
        public final String category;

        Fixture(long topicId, String label, String category) {
            this(topicId, 0, label, category);
        }

        Fixture(long topicId, int floor, String label, String category) {
            this.topicId = topicId;
            this.floor = floor;
            this.label = label;
            this.category = category;
        }
    }

    private static final List<Fixture> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Fixture(2193859L, "Joverna 公益站", "杂项"),
            new Fixture(1779614L, "Any 路由清障器", "杂项"),
            new Fixture(2140307L, "公益站列表", "杂项"),
            new Fixture(2214637L, "冰", "杂项"),
            new Fixture(2214964L, "list nested pic", "列表 / 图片"),
            new Fixture(2215753L, "spoiler", "spoiler"),
            new Fixture(2310531L, "topic list 首页标题 bug 换行", "列表"),
            new Fixture(2229268L, "投票 #1", "投票"),
            new Fixture(2537621L, "投票 #2", "投票"),
            new Fixture(1993635L, "很多回复", "回复"),
            new Fixture(2336669L, "又来修一下 CC", "杂项"),
            new Fixture(2501882L, "auto scroll issue", "滚动"),
            new Fixture(2503776L, "emoji title", "标题"),
            new Fixture(2790689L, "渲染极限：bbcode/HTML", "富文本"),
            new Fixture(2924095L, 19, "用户头衔渐变", "头衔")
    ));

    private DebugTopicFixtures() {
    }

    /** The curated topics, in the order they are shown. */
    public static List<Fixture> entries() {
        return ENTRIES;
    }

    /** A copy of the ids, for a caller that only needs them. */
    public static List<Long> ids() {
        List<Long> ids = new ArrayList<>(ENTRIES.size());
        for (Fixture entry : ENTRIES) {
            ids.add(entry.topicId);
        }
        return ids;
    }
}
