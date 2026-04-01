# Discourse Endpoints

Android v1 uses standard Discourse JSON endpoints and homepage bootstrap HTML.

- `GET /latest.json?page={page}` - latest topic feed
- `GET /new.json?page={page}` - new topic feed
- `GET /top.json?page={page}` - top topic feed
- `GET /t/{id}.json` - topic detail with posts stream
- `GET /search.json?q={query}` - topic and user search
- `GET /u/{username}.json` - public profile payload
- `GET /session/csrf` - CSRF token refresh for authenticated writes
- `GET /` - homepage HTML used for bootstrap parsing and WebView fallback
