# Auth Flow

NativeDo uses Discourse's browser session. It does not collect or submit a
linux.do username/password in native code.

## The one cookie jar

`android.webkit.CookieManager` is the single cookie store for the site's own
hosts (`linux.do` and its subdomains). The login WebView, upstream's in-app
browser and OkHttp all read and write that one jar:

- the network interceptor reads the request's cookies with
  `CookieManager.getCookie(url)` and applies every response `Set-Cookie` value
  verbatim with `CookieManager.setCookie(url, header)`, preserving attributes
  OkHttp's model does not carry (SameSite, Partitioned);
- the WebViews write their cookies through Chromium as usual;
- `android.webkit.CookieManager.flush()` is called at app-visible boundaries
  (page finished/paused, after the jar's own writes) so the store reaches disk.

There is no native snapshot and no copy between jars. An earlier design kept
one, and two copies of a rotating `_t` cannot both stay valid: the stale reuse
made Discourse log the session out (`expired_ServerLoggedOut`). Writes and
reads are restricted to the site's own hosts, so a linux.do cookie is never
sent to another site and another site's cookies are never cleared. One `_t`
belongs to one client: a build that still keeps its own native copy (or any
second client using the token) will rotate the session out from under this jar
and the server will log it out; this client cannot protect the session from
another build sharing the same app data.

## Login and Restore

1. Open `/login` in the WebView. The WebView reads the shared jar directly;
   nothing is copied in.
2. Let the normal linux.do page complete login, SSO, and any Cloudflare
   challenge. Do not infer success from a redirect alone.
3. When a same-origin page outside `/login` is reached, wait until the shared
   jar exposes a non-empty `_t` and the DOM exposes the current-user identity.
   The completion path retries briefly because Chromium writes cookies
   asynchronously, including when the DOM already exposes the username.
   Recheck same-origin navigation after reading JavaScript. Wait for identity
   persistence before publishing WebView login completion.
4. The WebView DOM identity is stored as a `WebView`-freshness snapshot so the
   user can leave the login page even when Cloudflare blocks OkHttp. Normal
   startup then verifies the identity with `/session/current.json`.
5. Prefer `/session/current.json` for verification. If that request fails
   temporarily, try the homepage bootstrap (`meta[name=current-username]` or
   `#data-preloaded`) before showing a recovery state.
6. A cold start with a `_t` in the shared jar and an empty native identity store
   is the normal path: verify the jar and adopt the account. This is exactly the
   state left behind by signing in through the in-app browser while the app was
   closed, so it must not show as logged out.

## Visible session recovery

The full-upstream `:nativedo` settings account section and topic recovery actions
open the same login WebView in recovery mode, initially at `/challenge`.
Normal first login still starts at `/login`. Keep the existing cookies, identity,
topic position and reply draft. Loading a page or pressing Back never completes
recovery, even with authenticated cookies; Back cancels, including pending DOM
callbacks. Recovery does not autofill credentials.

After the reader finishes the site's verification, they press `完成验证` on a
same-origin page outside `/login`. The tap asks the session itself - the shared
jar's cookies and the native session check - rather than the navigation
bookkeeping: a challenge that solved in place leaves the navigation generations
disagreeing while the site already accepts the session. A main-frame 403
document stays visible so its challenge can be used, and the tap may complete
from it once that check succeeds; the document itself never completes recovery,
and a page outside the site cannot. Normal first login still completes
automatically.

Authenticated means the login is usable, not that Cloudflare permits native
writes. Do not send a write probe or replay a reply on opening or completion.
Topic read recovery may retry the failed read and re-arm its existing timing
reporter only after explicit completion. A reply still requires a new user send.

Before showing the visible recovery state for a challenged native read, the
client may load the homepage once in a hidden same-origin WebView so Cloudflare
can mint a fresh `cf_clearance`, then replay that `GET`/`HEAD` once. Only a
changed `cf_clearance` or a same-origin page that finished without a challenge
counts as a successful refresh; a page that has not answered yet is not evidence
that the old clearance works, and nothing is replayed on that guess. A write is
never replayed automatically; it still requires a new user send.

## Native Requests

1. Each exchange reads the request's cookies from the shared jar and applies
   each response `Set-Cookie` value back to it, including redirects and expired
   values (an expired `_t` deletes the value). No stored snapshot is replayed.
2. Fencing is by the `_t` value itself, not by a store revision: capture the
   `_t` the request was sent with; when the response arrives, re-read the jar.
   If the jar now holds a different non-empty `_t`, another participant (the
   login WebView, the in-app browser, a parallel response) rotated the token
   while this exchange was in flight - that newer rotation is the live one, so
   this response may not write or delete `_t`/`_forum_session`. A plain success
   is still returned (Discourse rotates `_t` roughly every ten minutes, so a
   cold start's parallel requests lose that race routinely); a negative answer
   - not successful, or classified as anything but `Other` - loses its meaning
   in that race and fails the request with a retryable `IOException`, so a
   stale response never reports a logout, a challenge or a rejection. A token
   the jar no longer has at all (cleared, e.g. by a logout in the browser) is
   not a rotation: the response's logout evidence is real.
3. A Cloudflare challenge response must not overwrite or delete
   `_t`/`_forum_session`; other response cookies may still be applied.
4. Fetch `/session/csrf` immediately before state-changing requests and send
   the returned value as `X-CSRF-Token`.
5. A CSRF refresh or JSON parse failure is recoverable unless the response also
   proves that the session is logged out.

## Invalidation Boundary

Only these signals authorize clearing the site's cookies and the native
identity:

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
WebView login.

Clearing means: delete the cookies the site's own hosts present (their names are
read from the jar and each is expired for `/`, host-only and domain scoped) and
remove the stored identity. Other hosts the in-app browser visited keep their
own cookies. CookieManager has no domain-scoped removal API, so a path-scoped
cookie the site never shows for `/` can survive until the browser's own logout
removes it.

### `_t` rotates, so a rejected token is not a logout

`_t` is a per-device `UserAuthToken` (upstream: `lib/auth/default_current_user_provider.rb`,
`app/models/user_auth_token.rb`). It is rotated roughly every ten minutes
(`UserAuthToken::ROTATE_TIME_MINS = 10`; a token that was never seen rotates after one minute),
and the rotation hands the new value to whichever request made that rotation.
The previous value stays acceptable for exactly one more request (`lookup`
matches `auth_token OR prev_auth_token`, then clears `auth_token_seen`), after
which it resolves to no user: the server answers `not_logged_in` and deletes the
cookie (`refresh_session`: `cookie_jar.delete(TOKEN_COOKIE) if !user`). That is
the `status=404 evidence=LoggedOut deletesToken=true` shape this app logs.

Because all participants share one jar, a rotation performed by any of them is
the value the next request carries; a request that went out before the rotation
can still draw that `ServerLoggedOut` shape, and the fence above keeps it from
clearing anything - the jar's newer token is the live one. Only a rejection of
the token the jar itself holds ends the session.

### Web-side logout

The site's own Log Out removes `_t` in the shared jar. The next cold start sees
no `_t` and shows the login gate (with the cached identity as an expired state,
or anonymous when there is none). While the app is running, the first call that
needs authentication checks the jar before sending: with no `_t` it reports
`CookieMissing`, requests login and throws `LoginRequiredException` instead of
going out anonymous. Public reads may still succeed anonymously until then.

## Device authorization (experimental)

Settings -> 账号 -> 设备码登录（实验） runs Discourse's User API Key device flow against
linux.do: the app POSTs `/user-api-key/device.json` with a device-generated RSA public key and a
nonce, shows the returned `user_code`, opens `verification_uri_with_request` in the in-app
browser (which already carries the jar's signed-in session), polls
`/user-api-key/device/poll.json`, decrypts the authorized payload with the device key, verifies
the nonce, and stores the resulting **User API Key** sealed by AndroidKeyStore.

This credential is a `User-Api-Key` header, not the `_t` cookie the app's session is made of.
This round does not switch the app to it: the cookie session and WebView login are unchanged,
and the key is only probed with one authenticated `GET /session/current.json`. The full contract
(and the linux.do scope probe results) is in `contracts/discourse/device-authorization.md`;
wire shapes are in `fixtures/discourse/user-api-key-device.json`.

## Diagnostics

`adb logcat -s NativeDoSession` reports fixed event names, the jar write
counter, HTTP status, response classification, fixed route categories,
presence of `_t`/`_forum_session`/`cf_clearance`/`_rt`, token update/deletion
flags and acceptance flags. The full-upstream client also labels responses with
fixed `operation=posts`, `operation=timings` or `operation=other`. It never
records cookie values, usernames, URLs, query strings or response bodies.
Events cover native responses, missing auth cookies, committed login and
expiration reason, plus `legacy_cookie_migration` (the retired native snapshot
was read once) and `fenced_read_returned` (a successful read whose auth writes
were dropped because another participant had already rotated `_t`).

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
desktop browser client hints or navigation headers to native JSON calls. The
login WebView sets that same string explicitly and upstream's in-app browser
uses `WebSettings.getDefaultUserAgent`, so `cf_clearance` is issued to the same
User-Agent the native client sends. `cf_clearance` presence does not prove
acceptance by a different HTTP/TLS client; verification may still require the
visible WebView. `_rt` is shared through the same jar; its linux.do-specific
lifetime is not inferred from its opaque value.
