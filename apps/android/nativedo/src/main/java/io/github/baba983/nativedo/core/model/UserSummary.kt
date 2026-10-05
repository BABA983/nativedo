package io.github.baba983.nativedo.core.model

/**
 * The stats block of `GET /u/{username}/summary.json`.
 *
 * <p>The profile endpoint carries the reader's read time and badge count; the rest of the numbers a
 * Discourse profile's summary shows - how many topics and posts they wrote, how many likes they
 * gave and received, how many days they visited - exist only here. A field the site does not send
 * (a private profile hides these) stays 0 and is left off the screen, never drawn as a zero.</p>
 */
data class UserSummary(
    val topicCount: Int = 0,
    val postCount: Int = 0,
    val likesGiven: Int = 0,
    val likesReceived: Int = 0,
    val daysVisited: Int = 0,
    val postsReadCount: Int = 0,
    val topicsEntered: Int = 0,
    val bookmarkCount: Int = 0,
    val timeRead: Int = 0,
)
