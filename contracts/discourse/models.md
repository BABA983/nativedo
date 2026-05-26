# Discourse Models

These are screen-facing model semantics shared between Android today and iOS later.

- `TopicSummary` - list item model for feeds and search topic hits
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
- `TopicDetail` - topic header metadata (`createdAt`, `lastPostedAt`, `viewCount`, `tags`) plus ordered `Post` items, `totalPosts`, and full `streamPostIds`
- `Post` - post number, optional replied floor target, author username, optional display name/title/flair icon metadata, created timestamp, optional edit timestamp/edit count metadata, cooked HTML body, and extracted image URLs
- `SearchResult` - topic hits plus lightweight user hits
- `UserProfile` - profile header data for user and self pages, including optional avatar flair metadata
- `AppError` - normalized error surface for API and parsing failures
