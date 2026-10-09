# Discourse Models

These are screen-facing model semantics shared between Android today and iOS later.

- `TopicSummary` - list item model for feeds and search topic hits
  - `targetPostNumber` distinguishes post/reply activity rows from topic-only rows. User-action rows additionally carry `targetPostId` and `rowIdentity` from `post_id`, action `id`, or a stable payload fallback so same-topic activities without `post_number` remain distinct and can still load/jump to the exact post; ordinary topic rows still use `(topic id, target post number)` identity.
  - Feed author identity may come from root `users[]` plus `topic_list.topics[].posters[].user_id`
  - Feed topics may include optional `image_url` for list thumbnails
  - Feed topics may include `pinned` / `pinned_globally` for pinned row rendering
  - Feed topics may include `is_hot` for hot-badge rendering (Discourse Hot badge)
  - Feed topics may include `like_count` and `view_count` for popularity indicators
  - Feed topics may include `excerpt` for message-preview text
  - `notification_level` maps to `isMuted` (level 0) and `unreadCount` (level ≥2 shows unread posts)
  - Feed topics include lightweight `tags` (string list; API may return objects with `name`/`slug`)
  - Feed topics include `last_poster_username` for richer list rendering
- `CategorySummary` - category badge metadata for list rendering, including `name`, `slug`, `color`, optional `textColor`, and optional parent linkage
- `TopicListPage` - topic summaries plus `moreTopicsUrl` for feed pagination
- Private-message boxes reuse `TopicSummary`/`TopicListPage`: the three `topics/private-messages*` routes answer the standard `topic_list` envelope, so an inbox/sent/unread row is a topic row and paging follows `moreTopicsUrl` exactly like a feed. The box only chooses which route is read; the model is the same one a feed maps.
- `CreatedTopic` - the `topicId`, `slug` and `title` a `POST /posts.json` write created (from the answer's `topic_id`/`topic_slug`/`topic_title`). The private-message composer opens the created topic by this id; a response without a `topic_id` is a failure rather than a silent success.
- `TopicDetail` - topic header metadata (`createdAt`, `lastPostedAt`, `viewCount`, `tags`) plus ordered `Post` items, `totalPosts`, full `streamPostIds`, and `details.participants` mapped as ranked mention candidates. `highestPostNumber`, `lastReadPostNumber`, and optional `lastReadPostId` carry the server read state. `notificationLevel` (from `details.notification_level`) is the reader's own level for the topic (0 mute, 1 regular, 2 tracking, 3 watching; regular when the site names none) and drives the topic overflow's checked choices; the level only moves after the site takes a write (`POST /t/{id}/notifications.json`). For authenticated sessions, `lastReadPostNumber = 0` means the user has no recorded reads, so the page-down counter starts at `highestPostNumber`; anonymous sessions do not show or advance the counter. Opening a topic restores `lastReadPostId` (or loads `lastReadPostNumber`), and the page-down button targets `lastReadPostNumber + 1` when unread posts remain. Scrolling advances `lastReadPostNumber` monotonically and reports it back as `topics/timings`. Composer autocomplete uses server-ranked participants before supplementing them with loaded post authors.
- `Post` - post number, optional replied floor target, authoritative direct `replyCount`, author username, optional display name/title/flair icon metadata, created timestamp, optional edit timestamp/edit count metadata, cooked HTML body, and extracted image URLs. A post's own payload also carries the site's authorship and permissions - `yours`, `canEdit`, `canDelete`, `canRecover` and `deletedAt` - and the reader's own post read (`GET /posts/{id}.json`) additionally carries `raw`, the markdown the site stores. The post menu offers edit/delete/recover from those booleans alone (a username is never enough to offer a write the site would refuse); `raw` is what an edit opens on, and the cooked body is never turned back into markdown by a client. `isDeleted` distinguishes a post the site (or a successful `DELETE`) has removed; such a post keeps its content for its author and offers recovery instead of edit/delete.
- `PostReactionUsersGroup` - one reaction id, its server-wide `count`, and only the users included by the endpoint. `users.size` is not the total: the current discourse-reactions controller returns at most 27 recent users per group, so UI completeness and pagination must never be inferred from list length.
- `PollOptionRef` - poll option digest/label plus the poll payload's authoritative vote total. Voter pages do not include totals, so the UI must keep this value instead of replacing it with the current page's `users.size`.
- `PostPoll` / `PostPollOption` - one poll of a post, from the post's own `polls[]` (PollSerializer). `id`, `name`, `type`, `status`, `results`, `options`, `voters`, `chart_type` are always present; `public` appears (as `true`) only when the poll's visibility is `everyone`, `min`/`max` only on `number`/`multiple`, `step` only on `number`, and `close`, `closed_at`, `closed_by`, `preloaded_voters`, `ranked_choice_outcome` only when the poll uses them. `type` is one of `regular`, `multiple`, `number`, `ranked_choice`; `status` is `open` or `closed`; `results` is `always`, `on_vote`, `on_close` or `staff_only`. An option is `{id, html, votes}` where `id` is the option's MD5 digest - the value a vote request sends - and `html` is the label markup the client renders as plain text. **`votes` is absent, not null**, whenever `can_see_results?` is false: `always` always shows results, `on_vote` shows them once the reader has voted or is the poll's author, any closed poll shows them, `staff_only` shows them to staff. A client that reads `votes` as a required field fails to decode those payloads, and one that reads a missing `votes` as zero reports a wrong count instead of "not visible yet".
- Poll votes - a post's `polls_votes` maps a poll `name` to the digests the reader chose (`ranked_choice` sends `{digest, rank}` objects instead). **The whole `polls_votes` key is absent when the reader is anonymous or has not voted**, so an absent key means "no vote recorded", never "voted for nothing"; its absence is what a client must not confuse with an empty selection.
- `PostPollVote` - one digest the reader chose, plus the rank the site assigned for a `ranked_choice` poll.
- `SearchResult` - distinct topic hits, post hits adapted to `TopicSummary` with optional `targetPostNumber`, and lightweight user hits
- `MentionSearchResult` / `MentionCandidate` - ordered topic-aware composer candidates from `/u/search/users`; candidates preserve user avatar and `priority_group` metadata or a mentionable group identity. They remain separate from generic `SearchResult` because server ordering and group semantics are part of the composer contract.
- `UserProfile` - profile header data for user and self pages, including optional avatar flair metadata, the optional `location`/`website`/`website_name` fields, and the site's own action state: `canSendPrivateMessage`, `canFollow`/`isFollowed`, `canMuteUser`/`canIgnoreUser` and the current `muted`/`ignored`. The booleans drive the header's action row, so a field the site did not send (false) removes that action; `actionAvailable`-style state is read straight from these fields rather than guessed from a username.
- `UserSummary` - the profile's stats from `/u/{username}/summary.json`: `topicCount`, `postCount`, `likesGiven`, `likesReceived`, `daysVisited`, `postsReadCount`, `topicsEntered`, `bookmarkCount`, `timeRead`. All fields default to 0, and the screen only shows them when the summary arrived (a hidden summary is a failed read, not a profile of zeroes).
- `UserBadge` - one badge grant joined with its definition: `id`, `badgeId`, `name`, cooked-HTML `description` read as text, `icon`, `imageUrl`, `grantedAt`, `count`. The join happens in the mapper; a grant whose definition is absent is dropped.
- `AppError` - normalized error surface for API and parsing failures
- `MessageBusMessage` - one delivered message-bus message: `global_id`, `channel-local message_id`, `channel` and the channel's own `data` payload (see the message-bus endpoint in endpoints.md). The `message_id` is the only id a client stores and sends back; it is compared within one channel, never across channels, and it advances monotonically except when a `/__status` message names a corrected value for the channel. A message is not a screen model: the two holders below are the only consumers, so a new screen that wants a channel adds a holder rather than decoding ids at call sites.
- `IncomingTopics` - the topic list's "N new/updated topics" state: `byTopicId` maps each topic id to its `payload.category_id` (null when the message did not name one), and the helpers count or list per category (`null` = all). Fed from `/latest` (`message_type: latest`) and `/new` (`message_type: new_topic`); each topic id counts once until `clear()`, which the list calls after refreshing - so first-seen wins for the category, exactly like the web client's `newIncoming` array. `muted`/`unmuted`/`read` state messages must not grow the count.
- `NotificationUnreadCounts` - `unread` (`unread_notifications`), `highPriority` (`unread_high_priority_notifications`) and `allUnread` (`all_unread_notifications_count`) from `/notification/{userId}`. A payload that omits one of the three leaves that count as it was; zero means "none unread", so a client must not substitute zero for a field the payload did not carry. The site's own header badge shows `all_unread_notifications_count`; `unread_high_priority_notifications` is the subset of high-priority items the user menu highlights. The count is the site's, so a write that changes it (mark-read) re-reads the page the badge is seeded from rather than moving the number locally; that readback is dropped if a newer `/notification/{userId}` message arrived while it was in flight, and if the session ended meanwhile - a slow readback must never roll the badge back or repaint a signed-out app.
- `MessageBusBootstrap` - the site-provided subscription positions from a rendered page: `channelLastIds` (the `topicTrackingStateMeta` object: channel name to last id, e.g. `"/latest"`, `"/new"`, `"/unread/{userId}"`), `currentUserId` and `notificationChannelPosition` (from the preloaded `currentUser`; the latter is the id `/notification/{userId}` starts at), plus `sharedSessionKey` when the page names a cross-origin polling key and `longPollingBaseUrl` when the page names a poll origin that is not the site itself (Discourse's `siteSettings.long_polling_base_url`; `/` means the site and is treated as no value). The same preloaded user is also the badge's first value: `notificationCounts` carries `unread_notifications` / `unread_high_priority_notifications` / `all_unread_notifications_count` when the page names any of them, and an absent field starts at zero because there is no earlier value to keep (a *live* channel message that omits a field instead leaves that count unchanged - the two sources differ exactly there). Absent data (anonymous page, plain JSON caller) is an empty map and nulls, and every channel then starts at `-1`; `lastIdFor(channel)` provides that default. The server ships each preloaded value either as an inline object or as a JSON string containing it, and the element itself is a JSON `script#data-preloaded` on linux.do while older renders put the same data in a div attribute; both shapes must parse to the same model.

## Local topic viewport

The highest read post remains monotonic and is reported through topic timings.
The local exit viewport is separate: site/account/topic scoped post number and
pixel offset relative to list content padding. It may move backward and is never
sent as a lower read position. Explicit post links override this viewport; list
navigation restores it before falling back to read progress. Position restoration
must yield to a user drag. Debug builds offer a from-top entry that ignores and
does not overwrite the stored viewport; `NativeDoTopicScroll` logs programmatic
positioning and layout movement without post content or account identity.

## Discourse Chat (public channels)

- `ChatChannel` - one public (category) chat channel from `GET /chat/api/channels.json`, mapped from
  `Chat::ChannelSerializer`: `id`, `title` (the serializer's `name || title`), `slug`, optional
  `description`, `status` (`open` / `read_only` / `closed` / `archived`, the site's own enum), the
  site's `memberships_count` (`user_count`), the last message as `ChatLastMessage`, the reader's own
  `currentUserMembership` (null when they are not a member), and the live-update seeds. A DirectMessage
  channel never maps here. `isJoined` is `currentUserMembership != null`, `isOpen` is `status == "open"`,
  `canPost` is `isJoined && isOpen`, and `canJoin` comes from `meta.can_join_chat_channel` - the site
  alone decides whether this reader may join, so the client never guesses it from the status.
- `ChatLastMessage` - the channel's own preview: `id`, raw `message`, `cooked`, `createdAt` and the
  author as `ChatUser`. It is what the channel row draws; it is not a loaded message.
- `ChatUser` - a chat participant as `MessageUserSerializer` / `BasicUserSerializer` writes one: `id`,
  `username`, optional `name`, `avatarTemplate`.
- `ChatMembership` - the reader's own membership: `following` and `lastReadMessageId` (absent, not
  zero, when the site names none).
- `ChatChannelPage` - a page of `ChatChannel`s plus the envelope's own `loadMoreUrl`; its presence is
  what says another page follows, exactly like a topic feed's `more_topics_url`.
- `ChatMessage` - one conversation message from `GET /chat/api/channels/{id}/messages.json`: `id`, raw
  `message`, the site's rendered `cooked` HTML (the client never cooks the raw markdown itself),
  `createdAt`, `channelId`, the author as `ChatUser`, and `inReplyTo` (a `ChatReplyTarget`) when the
  message answers another. The list keeps the site's chronological order and dedupes overlapping
  windows by id.
- `ChatReplyTarget` - the message a `ChatMessage` answers, as the serializer's own preview carries it:
  `id`, `cooked`, `excerpt` and its `ChatUser`.
- `ChatMessagePage` - a window of `ChatMessage`s plus the site's own `canLoadMorePast` /
  `canLoadMoreFuture` / `targetMessageId`. The two booleans are asymmetric (an older window and a newer
  one are asked for separately), and `targetMessageId` is the anchor the site answered around.
- `ChatDraft` / `ChatDraftKey` / `ChatDraftStore` - a channel's half-written message (the text and the
  message it was answering), keyed by site + account + channel. A draft never follows the reader to
  another account and two channels never share a field.
