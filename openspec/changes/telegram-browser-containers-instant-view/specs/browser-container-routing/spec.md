## ADDED Requirements

### Requirement: Browser presentation target shall be explicit
The system SHALL resolve an explicit browser presentation target for browserable content instead of treating every browser open as the same full-screen route action.

#### Scenario: Route browser is required
- **WHEN** the requested page or action requires a stable full-screen WebView flow such as authentication, file upload, challenge handling, or complex form navigation
- **THEN** the system SHALL choose the full-route browser target

#### Scenario: Lightweight browser surface is allowed
- **WHEN** the requested page is eligible for lightweight browser presentation under the configured routing rules
- **THEN** the system SHALL choose the sheet browser target or another non-route browser target
- **AND** the chosen target SHALL preserve a clear return path to the previous scene

### Requirement: Reader target shall remain capability-gated
The system SHALL expose Instant View or reader presentation only for pages that are explicitly supported by the local support rules.

#### Scenario: Page supports Instant View
- **WHEN** the current page satisfies the Instant View support rules
- **THEN** the system SHALL allow the user to enter the reader target for that page

#### Scenario: Page does not support Instant View
- **WHEN** the current page does not satisfy the Instant View support rules
- **THEN** the system SHALL NOT expose Instant View as if it were universally available
