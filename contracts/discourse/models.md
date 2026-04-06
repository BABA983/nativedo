# Discourse Models

These are screen-facing model semantics shared between Android today and iOS later.

- `TopicSummary` - list item model for feeds and search topic hits
  - Feed author identity may come from root `users[]` plus `topic_list.topics[].posters[].user_id`
  - Feed topics may include optional `image_url` for list thumbnails
  - Feed topics may include `pinned`, lightweight `tags`, and `last_poster_username` for richer list rendering
- `CategorySummary` - category badge metadata for list rendering, including `name`, `slug`, `color`, optional `textColor`, and optional parent linkage
- `TopicListPage` - topic summaries plus `moreTopicsUrl` for feed pagination
- `TopicDetail` - topic header metadata (`createdAt`, `lastPostedAt`, `viewCount`, `tags`) plus ordered `Post` items, `totalPosts`, and full `streamPostIds`
- `Post` - post number, optional replied floor target, author username, optional display name/title/flair icon metadata, created timestamp, optional edit timestamp/edit count metadata, cooked HTML body, and extracted image URLs
- `SearchResult` - topic hits plus lightweight user hits
- `UserProfile` - profile header data for user and self pages
- `AppError` - normalized error surface for API and parsing failures
