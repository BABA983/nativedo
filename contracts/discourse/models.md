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
- `TopicDetail` - topic header metadata (`createdAt`, `lastPostedAt`, `viewCount`, `tags`) plus ordered `Post` items, `totalPosts`, full `streamPostIds`, and `details.participants` mapped as ranked mention candidates. `highestPostNumber`, `lastReadPostNumber`, and optional `lastReadPostId` carry the server read state. For authenticated sessions, `lastReadPostNumber = 0` means the user has no recorded reads, so the page-down counter starts at `highestPostNumber`; anonymous sessions do not show or advance the counter. Opening a topic restores `lastReadPostId` (or loads `lastReadPostNumber`), and the page-down button targets `lastReadPostNumber + 1` when unread posts remain. Scrolling advances `lastReadPostNumber` monotonically and reports it back as `topics/timings`. Composer autocomplete uses server-ranked participants before supplementing them with loaded post authors.
- `Post` - post number, optional replied floor target, authoritative direct `replyCount`, author username, optional display name/title/flair icon metadata, created timestamp, optional edit timestamp/edit count metadata, cooked HTML body, and extracted image URLs
- `PostReactionUsersGroup` - one reaction id, its server-wide `count`, and only the users included by the endpoint. `users.size` is not the total: the current discourse-reactions controller returns at most 27 recent users per group, so UI completeness and pagination must never be inferred from list length.
- `PollOptionRef` - poll option digest/label plus the poll payload's authoritative vote total. Voter pages do not include totals, so the UI must keep this value instead of replacing it with the current page's `users.size`.
- `SearchResult` - distinct topic hits, post hits adapted to `TopicSummary` with optional `targetPostNumber`, and lightweight user hits
- `MentionSearchResult` / `MentionCandidate` - ordered topic-aware composer candidates from `/u/search/users`; candidates preserve user avatar and `priority_group` metadata or a mentionable group identity. They remain separate from generic `SearchResult` because server ordering and group semantics are part of the composer contract.
- `UserProfile` - profile header data for user and self pages, including optional avatar flair metadata
- `AppError` - normalized error surface for API and parsing failures

## Local topic viewport

The highest read post remains monotonic and is reported through topic timings.
The local exit viewport is separate: site/account/topic scoped post number and
pixel offset relative to list content padding. It may move backward and is never
sent as a lower read position. Explicit post links override this viewport; list
navigation restores it before falling back to read progress. Position restoration
must yield to a user drag. Debug builds offer a from-top entry that ignores and
does not overwrite the stored viewport; `NativeDoTopicScroll` logs programmatic
positioning and layout movement without post content or account identity.
