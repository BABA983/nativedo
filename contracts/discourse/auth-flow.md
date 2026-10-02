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
   retries briefly because Chromium writes cookies asynchronously, including when
   the DOM already exposes the username. Recheck same-origin navigation after
   reading JavaScript. Wait for native cookie and identity persistence before
   publishing WebView login completion.
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

## Visible session recovery

The full-upstream `:nativedo` settings account section and topic recovery actions
open the same login WebView in recovery mode. Keep the existing cookies, identity,
topic position and reply draft. Loading a page or pressing Back never completes
recovery, even with authenticated cookies; Back cancels, including pending DOM
callbacks. Recovery does not autofill credentials.

After the reader finishes the site's verification, they press `完成验证` on a
successfully loaded same-origin page outside `/login`. Reuse the login completion
use case to import cookies and persist the session. A main-frame 403 document
stays visible so its challenge can be used, but cannot complete recovery until
a successful navigation. Normal first login still completes automatically.

Authenticated means the login is usable, not that Cloudflare permits native
writes. Do not send a write probe or replay a reply on opening or completion.
Topic read recovery may retry the failed read and re-arm its existing timing
reporter only after explicit completion. A reply still requires a new user send.

## Native Requests

1. Replay persisted cookies through a network interceptor for JSON and image
   calls, including each redirect. Capture the cookie revision and actual sent
   values together. Login/logout fence earlier requests; `_t` changes advance
   the revision. A response whose revision moved under it drops its stale cookie
   write, but it is still *returned* when it is a plain success: the payload does
   not depend on the jar, and Discourse rotates `_t` roughly every ten minutes,
   so a cold start's parallel requests lose that race routinely. Only a negative
   answer - not successful, or classified as anything but `Other` - loses its
   meaning in that race and fails the request, so a stale response never reports
   a logout, a challenge or a rejection. `CookieJar` alone lacks the request
   context needed for this check.
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
- a successful `/session/current.json` response explicitly containing
  `current_user: null`. Missing fields or a user without a valid username are
  inconclusive and must not clear cookies.

Upstream currently returns 404 when `/session/current.json` has no user;
linux.do may differ. An unqualified 404 falls back to homepage verification,
not blanket logout. Do not treat every 404 from other endpoints as auth failure.

Cloudflare challenge pages (`403` with challenge evidence), DNS/timeouts,
network failures, `5xx`, and HTML/JSON parse failures do **not** prove logout.
Keep `_t`, `_forum_session`, and `cf_clearance`, show a recovery action, and
let the WebView solve the challenge or retry later.

Session invalidation is generation-bound. A `401` from a request started under
an older cookie generation must not clear cookies established by a newer
WebView login. WebView cookie deletion is asynchronous, so logout/invalidation
must await deletion and serialize it against the next login cookie import.

## Cookie ownership and recovery

On startup, keep the native jar authoritative when `_t` exists. If it is missing
and a cached identity exists, attempt a guarded WebView import, then verify the
account. Do not use the old cached account as authenticated fallback after this
import. Without a cached identity, do not import browser cookies automatically:
explicit logout removes that identity before asynchronous browser deletion.

Hidden homepage WebViews can rotate `_t`. Await native-to-browser synchronization
and import browser changes after loading only if the native revision has not
changed in the meantime.

### `_t` rotates, so a rejected token is not a logout

`_t` is a per-device `UserAuthToken` (upstream: `lib/auth/default_current_user_provider.rb`,
`app/models/user_auth_token.rb`). It is rotated roughly every ten minutes
(`UserAuthToken::ROTATE_TIME_MINS = 10`; a token that was never seen rotates after one minute),
and the rotation hands the new value to whichever client made that request. The previous value
stays acceptable for exactly one more request (`lookup` matches `auth_token OR prev_auth_token`,
then clears `auth_token_seen`), after which it resolves to no user: the server answers
`not_logged_in` and deletes the cookie (`refresh_session`: `cookie_jar.delete(TOKEN_COOKIE) if
!user`). That is the `status=404 evidence=LoggedOut deletesToken=true` shape this app logs.

A rejected token therefore proves only that the token *this* request carried is stale - the
browser jar (login WebView, article viewer, challenge page) may hold the rotation the native jar
missed. Reconcile before clearing anything:

1. adopt the browser's values for the current revision (`CookieSyncBridge.syncFromWebViewIfCurrent`),
2. re-run verification once,
3. only then treat `ServerLoggedOut` as real, which clears both jars and the identity.

A browser jar that is blank, unreadable, or holds the same rejected token changes nothing: the
second verify fails and the session expires as before. Adoption never happens after a
*successful* native request - a stale browser jar must not replace a rotation the native jar
already has, which is the same rule the startup path follows.

Login completion repeats the direction: when the browser has authenticated but the import
produced no `_t` (Chromium flushes asynchronously; a conflicting pair is reported, not chosen),
the native verify must not run - it would answer "not logged in" and clear the session the
browser just created. Keep both jars and report recovery instead.

One `_t` belongs to one client. Copying a browser session into the app, or running two app
instances on the same token, gives two clients one rotating token, and the server logs the
second one out within a rotation. Multiple devices are fine - each logs in for itself.

`CookieManager.getCookie` only exposes name/value pairs. Preserve known cookie
attributes when possible and never rewrite unchanged browser cookies. Unknown
cookies use a host-only root scope; expiry cannot be inferred. Conflicting
same-name auth values require recovery rather than choosing an arbitrary token.
Full browser attribute fidelity remains limited by the Android CookieManager API.

## Diagnostics

`adb logcat -s NativeDoSession` reports fixed event names, cookie revision,
HTTP status, response classification, fixed route categories, presence of
`_t`/`_forum_session`/`cf_clearance`/`_rt`, token update/deletion flags and
acceptance flags. The full-upstream client also labels responses with fixed
`operation=posts`, `operation=timings` or `operation=other`. It never records
cookie values, usernames, URLs, query strings or response
bodies. Events cover native responses, stale browser imports,
startup recovery, missing auth cookies, committed login and expiration reason,
plus the rotation reconciles: `webview_session_adopted` (the browser's values
were taken for a rejected native token), `webview_session_unavailable` (the
browser jar could not be read), `webview_login_unreadable` (a login the
native jar could not read, kept for recovery instead of verified) and
`fenced_read_returned` (a successful read whose cookie write was dropped
because a parallel response advanced the revision).

## 403 classification

A `cf-mitigated: challenge` header or recognizable challenge HTML means browser
verification is required. That evidence takes precedence over logout-looking
headers from a proxy response. Challenge responses must not overwrite/delete
`_t` or `_forum_session`. Inspect bounded response previews, decoding gzip at
the network interceptor layer without consuming the caller's response body.

`server: cloudflare` alone does not establish a challenge: normal Discourse
responses also pass through Cloudflare. A 403 with `["BAD CSRF"]` is a request
verification failure; a subsequent user retry fetches fresh CSRF. Do not replay
writes automatically. Other 403s report an unclassified denial and preserve the
session unless explicit logout evidence is present. A 429 without challenge
signals reports rate limiting.

The Android client uses the WebView user agent for both transports. Do not copy
desktop browser client hints or navigation headers to native JSON calls.
`cf_clearance` presence does not prove acceptance by a different HTTP/TLS client;
verification may still require the visible WebView. `_rt` is synchronized but
its linux.do-specific lifetime is not inferred from its opaque value.
