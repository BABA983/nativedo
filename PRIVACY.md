# Privacy notice

NativeDo is an unofficial Android client for [linux.do](https://linux.do/). This
notice describes what the app stores and sends. It is about the app, not about the
linux.do service; the forum's own privacy policy governs the data your account has
on the server.

_Last updated: 2026-11-14._

## What stays on your device

- **Session cookies.** Login is performed in a WebView. The resulting cookies,
  including `_t`, `_forum_session` and `cf_clearance`, live in Android's WebView
  cookie store inside the app's private data directory. NativeDo reads them for its
  own requests to the forum and does not copy them anywhere else.
- **App preferences and caches.** SharedPreferences hold the signed-in user's
  username, display name and avatar template, the reader's Discourse preferences,
  theme and link-opening choices, drafts, topic read positions, and caches for
  categories, emoji, SVG sprites and read content.
- **Diagnostics.** The optional session diagnostics and the app's log lines are
  local. They use fixed event names and status codes, never URLs, request or
  response bodies, identities or cookie values. Nothing is uploaded automatically;
  exporting a diagnostics archive is a manual action.
- **Device authorization.** If you use the forum's device-authorization flow, the
  app keeps a per-install client id and a key pair in app-private storage so the
  grant can be recognized later.

NativeDo does not capture, store or inject the forum login form's username or
password. The login WebView leaves filling to Android's Autofill framework and your
password manager; data those keep is governed by those apps and their own terms,
not by NativeDo. A preference file written by an earlier build's autofill feature is
cleared the first time the app starts after upgrading.

Clearing the app's data or uninstalling it removes this local state. NativeDo sets
`android:allowBackup="false"` and explicit data-extraction rules, so app-private data
(cookies, WebView storage, preferences and caches) is not included in an Android
device backup or a device-to-device transfer.

## What the app sends

- Requests to the forum's own hosts (`linux.do` and `www.linux.do`) for the API,
  session, CSRF token and message bus.
- Requests for images, avatars and other media to the hosts named in forum content
  (for example `cdn3.ldstatic.com`). These are ordinary content requests to the
  third party that hosts the file; NativeDo does not add analytics or tracking
  parameters.
- If you open an external link, the browser or the in-app browser loads that site.

NativeDo has no telemetry, analytics, advertising or crash-reporting SDK. It does
not send your account data, reading history or diagnostics to the project
maintainers. It also does not initialize Telegram's network stack or database and
does not contact Telegram's servers; the Telegram code in the tree provides UI
components only.

## Your choices

- Use the forum's own logout to end the server session; NativeDo also clears its
  local session when you log out or when the server proves the session is gone.
- Clear the app's data to remove local cookies, preferences and caches.
- Use Android's autofill settings or your password manager to manage saved login
  credentials; NativeDo does not keep a copy.

## Contact

For a privacy question that is not a security vulnerability, open a regular issue.
For a security or session-handling problem, follow [SECURITY.md](SECURITY.md) and
report it privately.
