## ADDED Requirements

### Requirement: Cloudflare challenge detection
The system SHALL provide a `CloudflareInterceptor` (OkHttp interceptor) that detects Cloudflare challenge responses (HTTP 403 with `cf-mitigated` header or challenge HTML body) and signals that a WebView fallback is needed.

#### Scenario: Cloudflare challenge detected
- **WHEN** an API request receives a 403 response with Cloudflare challenge indicators
- **THEN** the interceptor posts `NotificationCenter.cloudflareChallenge` event with the request URL

#### Scenario: Normal 403 passes through
- **WHEN** a non-Cloudflare 403 response is received (e.g., insufficient permissions)
- **THEN** the response is passed through without triggering WebView fallback

### Requirement: WebView challenge resolution flow
The system SHALL provide a mechanism to open a WebView to the challenged URL, allow the user to complete the Cloudflare challenge, capture the resulting `cf_clearance` cookie, and sync it back to the OkHttp jar.

#### Scenario: Challenge resolution
- **WHEN** a Cloudflare challenge is detected and the user completes the WebView challenge
- **THEN** `cf_clearance` is synced to OkHttp, and the original request is retried

### Requirement: Challenge throttling
The system SHALL NOT show the Cloudflare WebView more than once every 5 minutes to avoid UX annoyance from repeated challenges.

#### Scenario: Repeated challenges throttled
- **WHEN** two Cloudflare challenges occur within 3 minutes
- **THEN** only the first triggers the WebView; the second returns an error to the caller
