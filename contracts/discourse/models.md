# Discourse Models

These are screen-facing model semantics shared between Android today and iOS later.

- `TopicSummary` - list item model for feeds and search topic hits
  - Feed author identity may come from root `users[]` plus `topic_list.topics[].posters[].user_id`
  - Feed topics may include optional `image_url` for list thumbnails
- `TopicListPage` - topic summaries plus `moreTopicsUrl` for feed pagination
- `TopicDetail` - topic header plus ordered `Post` items, `totalPosts`, and full `streamPostIds`
- `Post` - author, created timestamp, cooked HTML body, and extracted image URLs
- `SearchResult` - topic hits plus lightweight user hits
- `UserProfile` - profile header data for user and self pages
- `AppError` - normalized error surface for API and parsing failures
