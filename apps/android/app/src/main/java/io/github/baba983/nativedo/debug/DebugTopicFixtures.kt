package io.github.baba983.nativedo.debug

/**
 * Curated linux.do topics for native render QA. Keep in sync with [docs/note.md].
 */
data class DebugTopicFixture(
    val topicId: Long,
    val label: String,
    val category: String,
)

object DebugTopicFixtures {
    val entries: List<DebugTopicFixture> = listOf(
        DebugTopicFixture(2193859L, "Joverna 公益站", "杂项"),
        DebugTopicFixture(1779614L, "Any 路由清障器", "杂项"),
        DebugTopicFixture(2140307L, "公益站列表", "杂项"),
        DebugTopicFixture(2214637L, "冰", "杂项"),
        DebugTopicFixture(2214964L, "list nested pic", "列表 / 图片"),
        DebugTopicFixture(2215753L, "spoiler", "spoiler"),
        DebugTopicFixture(2310531L, "topic list 首页标题 bug 换行", "列表"),
        DebugTopicFixture(2229268L, "投票 #1", "投票"),
        DebugTopicFixture(2537621L, "投票 #2", "投票"),
        DebugTopicFixture(1993635L, "很多回复", "回复"),
        DebugTopicFixture(2336669L, "又来修一下 CC", "杂项"),
        DebugTopicFixture(2501882L, "auto scroll issue", "滚动"),
        DebugTopicFixture(2503776L, "emoji title", "标题"),
        DebugTopicFixture(2790689L, "渲染极限：bbcode/HTML", "富文本"),
    )
}
