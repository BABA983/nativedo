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
- **Login form autofill.** If you submit the forum's login form, the app may save the
  username and password for the next autofill. The username is stored as plain text;
  the password is encrypted with a non-exportable Android Keystore key. This is
  local only. You can remove it by clearing the app's data.
- **Diagnostics.** The optional session diagnostics and the app's log lines are
  local. They use fixed event names and status codes, never URLs, request or
  response bodies, identities or cookie values. Nothing is uploaded automatically;
  exporting a diagnostics archive is a manual action.
- **Device authorization.** If you use the forum's device-authorization flow, the
  app keeps a per-install client id and a key pair in app-private storage so the
  grant can be recognized later.

Clearing the app's data or uninstalling it removes this local state. The Android
manifest inherits `android:allowBackup="true"` from the upstream Telegram tree, so
if device backup is enabled on your phone, Android may include app-private data in
your own device backup. You can turn device backup off in Android settings.

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
- Clear the app's data to remove local cookies, preferences, caches and autofill
  credentials.
- Do not use the autofill feature if you do not want the app to remember your login
  form values.

## Contact

For a privacy question that is not a security vulnerability, open a regular issue.
For a security or session-handling problem, follow [SECURITY.md](SECURITY.md) and
report it privately.
