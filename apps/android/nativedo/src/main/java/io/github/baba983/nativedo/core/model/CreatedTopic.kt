package io.github.baba983.nativedo.core.model

/**
 * A topic the site created for a write (a new topic, a private message), as the site's own answer
 * names it: `POST /posts.json` replies with the first floor and the topic it was written into.
 *
 * The topic id is what a private message's screen navigates by - unlike a public new topic, there
 * is no list the reader came from to return to, so the created topic is where the writer lands.
 */
data class CreatedTopic(
    val topicId: Long,
    val slug: String = "",
    val title: String = "",
)
