## ADDED Requirements

### Requirement: Dual-source CSRF token
The system SHALL provide a `CsrfTokenProvider` that obtains CSRF tokens from two sources: (1) primary: `GET /session/csrf` API endpoint, (2) fallback: parsing the homepage bootstrap HTML. The primary source SHALL be preferred.

#### Scenario: Token from API
- **WHEN** `csrfProvider.getToken()` is called and `/session/csrf` returns successfully
- **THEN** the API-provided token is returned and cached

#### Scenario: API fails, fallback to bootstrap
- **WHEN** `csrfProvider.getToken()` is called and `/session/csrf` fails
- **THEN** the token parsed from the last bootstrap HTML is returned

### Requirement: Token caching and refresh
The system SHALL cache the CSRF token and automatically refresh it when it's older than 30 minutes or when a 403 response suggests token expiry.

#### Scenario: Stale token refresh
- **WHEN** a request fails with HTTP 403 and the CSRF token is older than 10 minutes
- **THEN** the token is refreshed from `/session/csrf` and the request is retried once

### Requirement: Thread-safe token access
The system SHALL ensure that `getToken()` is thread-safe and that concurrent callers do not trigger duplicate refresh requests.

#### Scenario: Concurrent token access
- **WHEN** 5 concurrent requests all need a fresh CSRF token
- **THEN** only one `/session/csrf` request is made, and all 5 callers receive the same token
