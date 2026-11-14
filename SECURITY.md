# Security policy

## Reporting a vulnerability

Please report security problems privately through GitHub's **Security advisories**
for this repository ("Security" tab -> "Report a vulnerability"). Do not open a
public issue, pull request or discussion for a vulnerability, and do not include
live cookies, tokens, passwords or another person's data in a report.

Include:

- the affected version or commit and the Android version;
- the steps to reproduce, and whether a signed-in session is required;
- the impact you observed;
- any relevant log lines with secrets and personal data removed.

We will acknowledge a report as soon as we can, investigate it, and coordinate a
fix and disclosure with you. This is a volunteer project, so we cannot promise a
fixed response time.

## Scope

In scope: NativeDo's own code under `apps/android/nativedo`, the session and cookie
handling, the WebView login bridge, the native transport and storage, and the build
and release tooling in this repository.

Out of scope: the linux.do server and its configuration (report those to linux.do),
the upstream Telegram Android project (report those to
[DrKLO/Telegram](https://github.com/DrKLO/Telegram)), and vulnerabilities that
require a compromised device or a rooted OS.

## Session and cookie handling

These are the principles a security report can rely on:

- Login is WebView-backed and cookie-based. NativeDo does not implement a separate
  username/password HTTP client; the login form is rendered and submitted by the
  WebView.
- `_t`, `_forum_session` and `cf_clearance` are treated as secrets. They are kept in
  the app's WebView cookie jar and are never logged, included in diagnostics, or
  sent to a host outside the forum's own domain.
- A Cloudflare challenge is not logout, and an authenticated cookie does not prove a
  native write will be accepted. Recovery uses the visible WebView rather than a
  hidden workaround.
- Diagnostics use fixed event names and status codes. They never record URLs,
  request or response bodies, identities or cookie values.
- Credentials saved for the login form's autofill stay on the device; the password
  is sealed with an Android Keystore key. See [PRIVACY.md](PRIVACY.md).

If you find a path that violates one of these principles, treat it as a security
report even if you are not sure it is exploitable.
