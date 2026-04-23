package io.github.baba983.nativedo.ui.topic

data class MockTopic(
    val id: Int,
    val title: String,
    val category: String,
    val author: String,
    val replyCount: Int,
    val viewCount: Int,
    val likeCount: Int,
    val excerpt: String,
)

object MockTopics {
    val all: List<MockTopic> = listOf(
        MockTopic(
            id = 101,
            title = "Telegram 风格重构第一步应该先搭哪些基础设施？",
            category = "Android",
            author = "baba983",
            replyCount = 42,
            viewCount = 1280,
            likeCount = 96,
            excerpt = "先把导航容器、ActionBar、边缘返回和 predictive back 这些交互骨架固定下来。",
        ),
        MockTopic(
            id = 102,
            title = "Compose 页面如何挂进 Telegram 式的 fragment 栈里？",
            category = "Kotlin",
            author = "monday",
            replyCount = 18,
            viewCount = 860,
            likeCount = 41,
            excerpt = "页面内容继续用 Compose，但导航 source of truth 只保留一套。",
        ),
        MockTopic(
            id = 103,
            title = "把 ActionBarLayout 直接搬进项目会遇到哪些依赖问题？",
            category = "Discourse",
            author = "native-do",
            replyCount = 29,
            viewCount = 920,
            likeCount = 53,
            excerpt = "真正难的不是文件拷贝，而是要控制好依赖扩散范围。",
        ),
        MockTopic(
            id = 104,
            title = "predictive back 接入后，系统返回和手势返回怎样共用一套动画？",
            category = "Android",
            author = "pixelwave",
            replyCount = 12,
            viewCount = 540,
            likeCount = 24,
            excerpt = "Activity 只做事件转发，真正的位移和栈切换都交给容器。",
        ),
        MockTopic(
            id = 105,
            title = "linux.do 原生客户端的 topic detail 页面第一阶段需要做到多完整？",
            category = "产品",
            author = "bridge",
            replyCount = 9,
            viewCount = 430,
            likeCount = 18,
            excerpt = "先验证能多层进入和多层返回，再去补真实内容和后续交互。",
        ),
    )

    fun topicFor(id: Int): MockTopic = all.firstOrNull { it.id == id } ?: all.first()

    fun relatedTopicsFor(id: Int): List<MockTopic> = all.filterNot { it.id == id }.take(3)
}
