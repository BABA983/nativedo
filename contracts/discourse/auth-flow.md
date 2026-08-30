# Auth Flow

NativeDo uses Discourse's browser session. It does not collect or submit a
linux.do username/password in native code.

## Login and Restore

1. Open `/login` in the WebView. Before loading it, copy the native jar's
   cookies into the WebView when they exist.
2. Let the normal linux.do page complete login, SSO, and any Cloudflare
   challenge. Do not infer success from a redirect alone.
3. When a same-origin page outside `/login` is reached, wait until the WebView
   exposes a non-empty `_t` and current-user identity. The completion path
   retries briefly because Chromium writes cookies asynchronously.
4. At that boundary, read `_t`, `_forum_session`, `cf_clearance`, and other
   current-domain cookies and merge the values that are actually visible into
   the native jar. A transient or blank `CookieManager` result, or a missing
   cookie name, is not a deletion signal and must not erase the native jar.
5. The WebView DOM identity is stored as a `WebView`-freshness snapshot so the
   user can leave the login page even when Cloudflare blocks OkHttp. Normal
   startup then verifies the identity with `/session/current.json`.
6. Prefer `/session/current.json` for verification. If that request fails
   temporarily, try the homepage bootstrap (`meta[name=current-username]` or
   `#data-preloaded`) before showing a recovery state.

## Native Requests

1. Replay the persisted cookies through the OkHttp `CookieJar` for JSON calls.
   Response `Set-Cookie` headers, including expired/deleted values, update the
   same `(name, normalized domain, path)` cookie identity; an expired `_t`
   removes the previous value.
2. Fetch `/session/csrf` immediately before state-changing requests and send
   the returned value as `X-CSRF-Token`.
3. A CSRF refresh or JSON parse failure is recoverable unless the response also
   proves that the session is logged out.

## Invalidation Boundary

Only these signals authorize clearing both native and WebView cookies:

- HTTP `401`;
- `discourse-logged-out: true` (or `1`);
- a JSON `error_type`/`error` of `not_logged_in` or `logged_out`;
- a successful `/session/current.json` response whose `current_user` is null.

Cloudflare challenge pages (`403` with challenge evidence), DNS/timeouts,
network failures, `5xx`, and HTML/JSON parse failures do **not** prove logout.
Keep `_t`, `_forum_session`, and `cf_clearance`, show a recovery action, and
let the WebView solve the challenge or retry later.

Session invalidation is generation-bound. A `401` from a request started under
an older cookie generation must not clear cookies established by a newer
WebView login. WebView cookie deletion is asynchronous, so logout/invalidation
must await deletion and serialize it against the next login cookie import.
