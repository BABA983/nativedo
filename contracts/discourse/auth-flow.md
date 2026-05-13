# Auth Flow

The v1 login design is WebView-backed and cookie-based.

1. Open linux.do in WebView.
2. Let the user complete normal web login and any Cloudflare challenge.
3. Read cookies such as `_t`, `_forum_session`, and `cf_clearance` from the web cookie store.
4. Sync those cookies into the native OkHttp cookie jar.
5. Use native HTTP requests for JSON endpoints after login.
6. Prefer `/session/current.json` to confirm the authenticated username after cookie sync.
7. Parse homepage bootstrap HTML for current user identity and CSRF hints when the current-session lookup is unavailable or when a bootstrap CSRF fallback is needed.
8. Keep the login WebView open until native session restore reaches an authenticated state. If `_t` is already present and the user backs out, sync and verify cookies before returning to Profile.
9. Prefer `/session/csrf` to refresh the CSRF token, but keep the bootstrap token as a fallback when the refresh endpoint fails transiently during session restore.
10. If Discourse explicitly reports `logged_out` or `not_logged_in`, clear native and WebView cookies before sending the user back to WebView.
11. If session restore fails transiently or Cloudflare blocks native requests, send the user back to WebView without destroying the in-progress WebView cookies.
