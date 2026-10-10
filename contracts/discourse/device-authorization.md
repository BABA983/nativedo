# Device authorization (experimental User API Key)

Discourse's device flow gives the reader a short-lived code to approve in a
signed-in browser, then returns a User API Key encrypted to the device's RSA key.
It resembles RFC 8628 (core PR #40189, Discourse 2026.6+).

This is a separate, revocable **header credential**, not a `_t` cookie session.
NativeDo's production client remains cookie-based. The experimental settings flow
stores the key, probes identity and allows revocation; it does not switch normal
reads/writes to key authentication. See [auth flow](auth-flow.md) and the
[wire fixture](../../fixtures/discourse/user-api-key-device.json).

## Routes and payload

- `POST /user-api-key/device.json`: JSON `nonce`, comma-separated `scopes`,
  `client_id`, `application_name`, SPKI PEM `public_key` (RSA 2048–8192), and
  `padding` (`oaep` or `pkcs1`). An unknown client id needs no registration but
  appears as an unregistered application on approval.
  Response: `device_code`, `user_code` (`XXXX-XXXX`, alphabet
  `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`), `verification_uri`,
  `verification_uri_with_request` (`?request=<8-char token>`), `expires_in` (600),
  `interval` (5). Follow the returned URI rather than assuming `/login/device`.
- `GET /user-api-key/activate`: browser approval/denial; `?request=` preloads
  the grant. linux.do also exposes a `/login/device` page.
- `POST /user-api-key/device/poll.json`: JSON `device_code`; response status is
  `authorization_pending`, `authorized` with `payload`, `access_denied`, or
  `expired_token`.
- `POST /user-api-key/revoke`: revoke the key using its `User-Api-Key` header.
  The reader can also manage keys on the site's preferences/apps page.

Authorized `payload` is Base64 (Ruby may wrap at 60 characters) over RSA-sealed JSON:

```json
{"key":"<User API Key>","nonce":"<request nonce>","push":false,"api":4}
```

`expires_at` may also be present. Decrypt with the private key and **reject a
nonce mismatch**. Requested OAEP uses core OpenSSL's SHA-1/MGF1-SHA1 default,
but the client supports known transformations in order: OAEP-SHA1, OAEP-SHA256,
OAEP-SHA512, then PKCS#1 v1.5. This compatibility path is necessary because an
observed linux.do payload did not match the requested padding; it is not a claim
that every future response uses PKCS#1.

A grant lives 10 minutes; authorized payloads remain in Redis roughly 1 minute.
A 30-second poll lock can yield another pending answer immediately after approval.
Core rate limits: create 20/min/IP, poll 60/min/IP and device code, activate 10/min/user.

## Scopes and use

`read` covers GET; `write` covers methods generally. `notifications`, `session_info`,
`user_status`, `bookmarks_calendar`, `message_bus` and `push` are route-scoped
(`UserApiKeyScope`). `session_info` covers `/session/current.json` and
`users#topic_tracking_state`. The site's `allow_user_api_key_scopes` decides which
can be requested. `one_time_password` is forbidden by `DISALLOWED_DEVICE_SCOPES`:
this flow cannot mint `_t` through `/session/otp/:token`.

`User-Api-Key` and optional `User-Api-Client-Id` authenticate requests; key-authenticated
writes are CSRF-exempt, but scope still limits permission. NativeDo currently probes
only `GET /session/current.json` and stores the credential sealed by AndroidKeyStore.
Do not infer general API access from a successful identity probe.

## Dated linux.do compatibility observations

The 2026-10-04 probe returned:

| Requested scopes | Response |
|---|---|
| `read`, or `read,write` | 403 `invalid_access` |
| `notifications`, or `session_info` | 200, grant created |
| `one_time_password` | 400 `invalid_parameters` |

The returned approval path was `/user-api-key/activate`. One approved payload
opened with PKCS#1 v1.5 despite requesting OAEP. Identity probe and revocation
were accepted. These observations explain using `session_info` and keeping cookie
login; they do **not** establish today's allow-list or a permanent site guarantee.
Recheck the capability before designing a key-mode client. Such a mode would need
its own scope and invalidation boundary, not clear the cookie session on a keyed 401.
