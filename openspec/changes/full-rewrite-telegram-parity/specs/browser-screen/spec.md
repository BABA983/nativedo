## ADDED Requirements

### Requirement: In-app WebView browser
The system SHALL provide a `BrowserFragment` extending `BaseFragment` that embeds a WebView for displaying web pages. The fragment SHALL have a custom ActionBar showing the page title and URL. `BrowserFragment` SHALL reuse the shared WebView infrastructure used by other linux.do web scenes, but SHALL remain distinct from `LoginFragment`.

#### Scenario: Load URL
- **WHEN** `BrowserFragment(url = "https://linux.do/t/slug/123")` is presented
- **THEN** the WebView loads the URL and the ActionBar shows the page title once loaded

### Requirement: Navigation controls
The ActionBar SHALL include back/forward navigation buttons, a refresh button, and a share button. Back/forward buttons SHALL be enabled/disabled based on WebView's `canGoBack()`/`canGoForward()`.

#### Scenario: Navigate back in WebView
- **WHEN** the user has navigated to a second page and taps the back button
- **THEN** the WebView goes back to the previous page

#### Scenario: Share URL
- **WHEN** the user taps the share button
- **THEN** an Android share intent is launched with the current URL

### Requirement: Progress indicator
The system SHALL display a thin progress bar at the top of the WebView that tracks page loading progress (0-100%).

#### Scenario: Page loading progress
- **WHEN** a page is loading
- **THEN** the progress bar animates from 0% to 100% and disappears on completion

### Requirement: Cookie sharing with native session
The WebView SHALL share cookies with the native OkHttp session via `CookieSessionStore` sync. This ensures the user is logged in when viewing linux.do pages in the browser.

#### Scenario: Logged-in browsing
- **WHEN** the user is logged in natively and opens a linux.do URL in BrowserFragment
- **THEN** the WebView shows the page as the logged-in user

### Requirement: External URL handling
The system SHALL intercept link clicks in the WebView through `LinkManager`. Links to linux.do topics or users SHALL be handled natively. Other linux.do pages SHALL stay in the in-app browser. External URLs SHALL be classified between external app or system browser according to `LinkManager` policy.

#### Scenario: Internal link interception
- **WHEN** the user taps a link to `https://linux.do/t/some-topic/456` in the WebView
- **THEN** the link is intercepted by `LinkManager` and `TopicDetailFragment(456)` is presented instead

#### Scenario: External link stays in WebView
- **WHEN** the user taps a link to `https://github.com/...` in the WebView
- **THEN** the link is handled according to `LinkManager` classification instead of bypassing the app's unified link-opening policy

### Requirement: Swipe-back gesture
The BrowserFragment SHALL support swipe-back gesture (from ActionBarLayout) to close the browser and return to the previous fragment.

#### Scenario: Swipe back from browser
- **WHEN** the user swipes from the left edge in BrowserFragment
- **THEN** the BrowserFragment slides away and the previous fragment is shown
