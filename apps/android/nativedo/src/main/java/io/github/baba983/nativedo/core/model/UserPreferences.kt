package io.github.baba983.nativedo.core.model

/**
 * The reader's own Discourse preferences, the part of `GET /u/{username}.json`'s `user_option`
 * this client offers. The site carries dozens of options; the ones here are the notification,
 * tracking and profile-visibility choices that change what a linux.do reader sees and receives.
 *
 * <p>Every field mirrors the site's option name in snake case; the values are the site's own
 * enums, not re-numbered. A field the site did not send keeps the default declared here, and the
 * screen draws that row from the fetched object only - so a value the site refuses to answer is
 * never shown as if it were the reader's.</p>
 */
data class UserPreferences(
    /** `like_notification_frequency`: 0 always, 1 first time and daily, 2 first time, 3 never. */
    val likeNotificationFrequency: Int = 0,
    /** `notify_on_linked_posts`: someone quotes, mentions or links to the reader. */
    val notifyOnLinkedPosts: Boolean = true,
    /**
     * `notification_level_when_replying`: the level a topic gets once the reader replies in it.
     * Discourse's NotificationLevels: 1 regular, 2 tracking, 3 watching.
     */
    val notificationLevelWhenReplying: Int = 2,
    /** `auto_track_topics_after_msecs`: how long a topic is read before it is tracked; -1 never. */
    val autoTrackTopicsAfterMsecs: Int = 300000,
    /**
     * `new_topic_duration_minutes`: what the reader considers a new topic; -1 not viewed yet,
     * -2 since the last visit, otherwise a duration in minutes.
     */
    val newTopicDurationMinutes: Int = 2880,
    /** `allow_private_messages`: other users may send the reader private messages. */
    val allowPrivateMessages: Boolean = true,
    /**
     * `hide_profile_and_presence`: profile and online presence hidden from other users. The model
     * also carries `hide_profile` and `hide_presence`; this is the combined value the site's own
     * profile page writes, and the one the client's single switch follows.
     */
    val hideProfileAndPresence: Boolean = false,
)
