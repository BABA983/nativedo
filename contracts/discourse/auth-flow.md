# Auth Flow

The v1 login design is WebView-backed and cookie-based.

1. Open linux.do in WebView.
2. Let the user complete normal web login and any Cloudflare challenge.
3. Read cookies such as `_t`, `_forum_session`, and `cf_clearance` from the web cookie store.
4. Sync those cookies into the native OkHttp cookie jar.
5. Use native HTTP requests for JSON endpoints after login.
6. Parse homepage bootstrap HTML for current user identity and CSRF hints.
7. If session restore fails or Cloudflare blocks native requests, send the user back to WebView.
