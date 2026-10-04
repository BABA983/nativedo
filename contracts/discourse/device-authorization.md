# Device Authorization (User API Key)

linux.do's `/login/device` is the verification page of Discourse's **User API Key device
authorization flow** (RFC 8628-shaped; core PR #40189, Discourse 2026.6+). The device gets a
short-lived code, the reader approves it in a signed-in browser, and the device polls until the
server hands it a **User API Key** encrypted to the device's RSA public key.

The result is a header credential (`User-Api-Key`), **not** a `_t` cookie session. The app's
cookie session keeps driving the client; the stored key is a separate, revocable credential
(settings -> 账号 -> 设备码登录（实验）).

## Routes (core; linux.do probe adds its own `verification_uri`)

- `POST /user-api-key/device.json` - create a grant. JSON body: `nonce`, `scopes` (comma list),
  `client_id`, `application_name`, `public_key` (SPKI PEM, RSA 2048-8192), `padding` (`oaep` or
  `pkcs1`). No registration is needed for a `client_id`; an unknown one is shown as an
  unregistered application on the approval page. Response: `device_code`, `user_code`
  (`XXXX-XXXX`, alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`), `verification_uri`,
  `verification_uri_with_request` (`?request=<8-char token>`), `expires_in` (600), `interval` (5).
- `GET /user-api-key/activate` (linux.do presents it at `/login/device`) - the reader's page;
  `?request=` pre-loads the pending grant, then the reader approves or denies.
- `POST /user-api-key/device/poll.json` - `{"device_code"}` -> `{"status":"authorization_pending"}`,
  `{"status":"authorized","payload":"<base64 RSA-OAEP>"}`,
  `{"status":"access_denied"}`, or `{"status":"expired_token"}`.
- `POST /user-api-key/revoke` - with a `User-Api-Key` header, revokes that key. The user-facing
  alternative is `/{username}/preferences/apps`.

## Payload

The authorized `payload` is Base64 (Ruby wraps at 60 chars) over an RSA-sealed JSON body:
`{"key":"<User API Key>","nonce":"<the create request's nonce>","push":false,"api":4[, "expires_at"]}`.
The device must decrypt with its private key and **reject any payload whose nonce is not the one
it sent**.

The requested `padding` is `oaep`; core's Ruby maps that through
`rsa_padding_mode=oaep`, which OpenSSL defaults to SHA-1 + MGF1-SHA1. **linux.do does not honor
it**: the one live login observed (2026-10-04) was sealed with **PKCS#1 v1.5** - the Android
client opened it with `RSA/ECB/PKCS1Padding` after every OAEP variant failed (`payload_opened
transform=pkcs1`). The client therefore tries the known transformations in order
(`oaep-sha1`, `oaep-sha256`, `oaep-sha512`, `pkcs1`) and records which one worked, instead of
depending on the site's mapping matching the request.

Authorized payloads live in Redis for only ~1 minute; polls are serialized by a 30 s lock, so a
poll can return `authorization_pending` once more right after approval. A grant lives 10 minutes.
Rate limits: create 20/min/IP, poll 60/min (per IP and per device code), activation 10/min/user.

## Scopes

- `read` matches any GET; `write` matches any method; `notifications`, `session_info`,
  `user_status`, `bookmarks_calendar`, `message_bus`, `push` map to specific routes
  (`UserApiKeyScope`).
- `one_time_password` is **forbidden in this flow** (`DISALLOWED_DEVICE_SCOPES`) and its route
  matcher is empty; it cannot be used here to mint a `_t` via `/session/otp/:token`.
- The site setting `allow_user_api_key_scopes` gates every request, so linux.do's actual list
  decides what is usable (probe results below). The app's own audit asks for `read`,
  `read,write`, `notifications`, `session_info`, `one_time_password` in that order and records
  the server's answers.

## Using the key

`User-Api-Key: <key>` (optional `User-Api-Client-Id`) authenticates JSON requests; with the
header, state-changing requests are CSRF-exempt. This round's client only asks
`GET /session/current.json` with the key and stores it encrypted (AndroidKeyStore) for later.

### linux.do probe (2026-10-04, `emulator-5554`, against `/user-api-key/device.json`)

| `scopes` | answer |
|---|---|
| `read` | **403** `invalid_access` ("您没有权限查看请求的资源。") |
| `read,write` | **403** `invalid_access` |
| `notifications` | **200** (grant created; `user_code` issued) |
| `session_info` | **200** (grant created; `user_code` issued) |
| `one_time_password` | **400** `invalid_parameters` (core's `DISALLOWED_DEVICE_SCOPES`) |

So on linux.do the allow-list currently carries `notifications` and `session_info` (not
`read`/`write`), and the device flow cannot mint a one-time password. `session_info` is the
scope the app's login asks for; it covers `GET /session/current.json` and
`users#topic_tracking_state`. No client registration was needed: an unknown `client_id` (the
app's per-install `nativedo-<8 hex>`) creates grants and only earns the approval page's
"unregistered application" warning.

Two more live findings from the same pass:

- the create response's `verification_uri` path is **`/user-api-key/activate`** (core), not
  `/login/device` - the fork's `/login/device` page is not what the API names;
- the approved payload was **PKCS#1 v1.5**, see Payload above.

The end-to-end pass then: approved in the app's own in-app browser while signed in as the test
user, polled and opened the payload, stored the key, made one `GET /session/current.json` with
`User-Api-Key` + `User-Api-Client-Id` (**200, the right username**), and revoked the key through
`POST /user-api-key/revoke` (**200**). The site's 应用 page listed the key (到期时间: 永不) and
was empty of it after the revoke.

A key-mode client would plug into `:nativedo` as a request interceptor alongside the cookie
interceptor: when a stored key is active, add `User-Api-Key`/`User-Api-Client-Id`, skip the CSRF
fetch, and gate endpoints by the stored scope list (`session_info`/`notifications`; no writes on
linux.do today). The cookie jar stays the fallback for Cloudflare recovery and for the WebView
login, and a 401 from a keyed request revokes/clears the key instead of touching the cookie
session.
