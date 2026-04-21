## ADDED Requirements

### Requirement: WebView-based login
The system SHALL provide a `LoginFragment` extending `BaseFragment` that displays a WebView loading `https://linux.do/login`. The user SHALL complete the standard Discourse web login flow (including any Cloudflare challenges) within the WebView. `LoginFragment` SHALL remain an auth-specific scene and SHALL reuse the shared WebView infrastructure without being modeled as `BrowserFragment` opening `/login`.

#### Scenario: Successful login
- **WHEN** the user completes the login form and Discourse sets session cookies
- **THEN** the system detects the login completion, syncs cookies, and navigates to the Topics screen

### Requirement: Login detection
The system SHALL detect successful login by monitoring cookie changes in the WebView. When `_t` cookie is set for `linux.do`, the login is considered successful.

#### Scenario: Cookie-based login detection
- **WHEN** the WebView's CookieManager has a `_t` cookie set for `.linux.do`
- **THEN** `CookieSessionStore.syncFromWebView()` is called and the login flow proceeds into `AuthSessionManager`

### Requirement: Post-login session verification
After cookie sync, `AuthSessionManager` SHALL call `/session/current.json` to verify the session and obtain the current username.

#### Scenario: Session verification
- **WHEN** cookies are synced and `/session/current.json` is called
- **THEN** the response confirms the username and `AuthSessionManager` persists an authenticated session snapshot for app navigation

#### Scenario: Session verification fails
- **WHEN** `/session/current.json` returns an error after cookie sync
- **THEN** the system falls back to `BootstrapParser` to extract user identity from homepage HTML

### Requirement: Cloudflare challenge pass-through
The WebView SHALL handle Cloudflare challenges (JavaScript challenges, CAPTCHA) transparently. The user completes the challenge in the WebView, and the resulting `cf_clearance` cookie is synced.

#### Scenario: Cloudflare challenge during login
- **WHEN** Cloudflare presents a challenge page before the login form
- **THEN** the user completes the challenge in the WebView, `cf_clearance` is set, and the login page loads

### Requirement: Auto-login on app start
The system SHALL attempt session restore on app start via `LaunchBootstrap`, which delegates to `AuthSessionManager`. If a valid cookie-backed session can be restored, the system SHALL skip the login screen and go directly to the Topics screen. If session restore fails, the login screen SHALL be shown.

#### Scenario: Session restore success
- **WHEN** the app starts with a valid `_t` cookie
- **THEN** the Topics screen is shown directly (no login screen)

#### Scenario: Session restore failure
- **WHEN** the app starts with an expired `_t` cookie
- **THEN** the Login screen is shown
