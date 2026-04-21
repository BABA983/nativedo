## ADDED Requirements

### Requirement: Cookie persistence store
The system SHALL provide a `CookieSessionStore` implementing `CookieJar` that persists Discourse session cookies (`_t`, `_forum_session`, `cf_clearance`) to `SharedPreferences` (or encrypted storage). Cookies SHALL survive app restarts.

#### Scenario: Cookie persistence across restart
- **WHEN** the user logs in, cookies are saved, and the app is killed and restarted
- **THEN** the stored cookies are loaded and attached to subsequent API requests

### Requirement: WebView to OkHttp cookie sync
The system SHALL provide a method `syncFromWebView()` that reads cookies from Android's `CookieManager` (WebView cookie store) for the `linux.do` domain and copies them into the OkHttp `CookieJar`.

#### Scenario: Post-login cookie sync
- **WHEN** the user completes login in WebView and `syncFromWebView()` is called
- **THEN** `_t`, `_forum_session`, and `cf_clearance` cookies are available in the OkHttp jar

### Requirement: Session validity check
The system SHALL provide `isSessionValid(): Boolean` that checks whether a `_t` cookie exists and has not expired. The system SHALL also provide `getSessionUser(): String?` returning the current authenticated username (from last successful `/session/current.json` response).

#### Scenario: Valid session check
- **WHEN** `isSessionValid()` is called with a non-expired `_t` cookie
- **THEN** it returns `true`

#### Scenario: Expired session check
- **WHEN** `isSessionValid()` is called with an expired or missing `_t` cookie
- **THEN** it returns `false`

### Requirement: Auth manager integration
The cookie store SHALL be consumable by `AuthSessionManager` for cold-start restore, login completion from WebView, logout, and expired-session recovery flows.

#### Scenario: Login completion delegates to auth manager
- **WHEN** cookies are synced from WebView after a successful login
- **THEN** `AuthSessionManager` uses the updated cookie store to verify and publish the authenticated app session state

### Requirement: Session clear on logout
The system SHALL provide `clearSession()` that removes all Discourse cookies from both the OkHttp jar and WebView's CookieManager, and clears the stored username.

#### Scenario: Logout clears everything
- **WHEN** `clearSession()` is called
- **THEN** all Discourse cookies are removed, `isSessionValid()` returns false, and `getSessionUser()` returns null
