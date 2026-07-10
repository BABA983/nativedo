# Discourse Endpoints

Android v1 uses standard Discourse JSON endpoints and homepage bootstrap HTML.

- `GET /latest.json?page={page}` - latest topic feed with optional `topic_list.more_topics_url`
- `GET /new.json?page={page}` - new topic feed with optional `topic_list.more_topics_url`
- `GET /top.json?page={page}` - top topic feed with optional `topic_list.more_topics_url`
- `GET /site.json` - site metadata including category definitions for category badge rendering
- `GET /t/{id}.json` - topic detail with `posts_count` and `post_stream.stream`
- `GET /t/{id}/posts.json?post_ids[]={id}` - incremental topic post chunk fetch
- `GET /search.json?q={query}` - topic and user search
- `GET /u/{username}.json` - public profile payload; beyond the core user fields, the profile screen also reads linux.do/Discourse plugin fields `gamification_score`, `cakedate`, `total_followers`, `total_following`, `accepted_answers`, `primary_group_name`, and `last_posted_at`
- `GET /session/current.json` - preferred current-user identity lookup during session restore
- `GET /session/csrf` - preferred CSRF token refresh for authenticated writes; bootstrap HTML still provides a fallback hint during session restore
- `POST /posts.json` - authenticated topic reply/create post with form fields `topic_id`, `raw`, optional `reply_to_post_number`, and `X-CSRF-Token`
- `POST /bookmarks.json` - authenticated post bookmark create with form fields `bookmarkable_id`, `bookmarkable_type=Post`, `auto_delete_preference`, and `X-CSRF-Token`
- `DELETE /posts/{id}/bookmark` - authenticated post bookmark removal with `X-CSRF-Token`
- `PUT /polls/vote` - authenticated poll vote with form fields `post_id`, `poll_name`, `options[]`, and `X-CSRF-Token`; the discourse-poll engine registers this route as `PUT` (not `POST`). Response body is `{ "poll": {...}, "vote": ["<option_id>", ...] }` with the updated poll and the current user's normalized selection.
- `DELETE /polls/vote` - authenticated poll vote retraction with form fields `post_id`, `poll_name`, and `X-CSRF-Token`; allowed when the poll is open, the topic/post is still active, the user can post in the topic, and (if configured) the user belongs to the poll’s allowed groups. Discourse web UI also retracts on single-choice re-tap of the selected option.
- `GET /polls/voters.json?post_id={id}&poll_name={name}&page={page}&limit={limit}` - public poll voter list grouped by option id; fixture at `fixtures/discourse/poll-voters.json`
- `GET /` - homepage HTML used for bootstrap parsing and WebView fallback
