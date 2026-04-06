# Discourse Endpoints

Android v1 uses standard Discourse JSON endpoints and homepage bootstrap HTML.

- `GET /latest.json?page={page}` - latest topic feed with optional `topic_list.more_topics_url`
- `GET /new.json?page={page}` - new topic feed with optional `topic_list.more_topics_url`
- `GET /top.json?page={page}` - top topic feed with optional `topic_list.more_topics_url`
- `GET /site.json` - site metadata including category definitions for category badge rendering
- `GET /t/{id}.json` - topic detail with `posts_count` and `post_stream.stream`
- `GET /t/{id}/posts.json?post_ids[]={id}` - incremental topic post chunk fetch
- `GET /search.json?q={query}` - topic and user search
- `GET /u/{username}.json` - public profile payload
- `GET /session/current.json` - preferred current-user identity lookup during session restore
- `GET /session/csrf` - preferred CSRF token refresh for authenticated writes; bootstrap HTML still provides a fallback hint during session restore
- `GET /` - homepage HTML used for bootstrap parsing and WebView fallback
