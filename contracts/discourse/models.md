# Discourse Models

These are screen-facing model semantics shared between Android today and iOS later.

- `TopicSummary` - list item model for feeds and search topic hits
- `TopicDetail` - topic header plus ordered `Post` items
- `Post` - author, cooked HTML body, and extracted image URLs
- `SearchResult` - topic hits plus lightweight user hits
- `UserProfile` - profile header data for user and self pages
- `AppError` - normalized error surface for API and parsing failures
