package io.github.baba983.nativedo.core.model

data class TopicListPage(
    val topics: List<TopicSummary>,
    val moreTopicsUrl: String?,
    /**
     * Whether this page was answered from the persisted offline cache rather than the site. A live
     * answer leaves it false; the UI suppresses writes and says so while it is true.
     */
    val fromCache: Boolean = false,
)
