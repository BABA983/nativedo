## MODIFIED Requirements

### Requirement: Browser presentation target shall be explicit
The system SHALL resolve an explicit in-app browser-family target for browserable content instead of splitting ordinary browsing across multiple in-app browser containers.

#### Scenario: Browser target is chosen
- **WHEN** a requested URL resolves to app-browser navigation
- **THEN** the system SHALL choose the regular browser target
- **AND** it SHALL NOT promote the request into a separate full-route browser target solely because the flow becomes auth-sensitive, challenge-heavy, upload-capable, or hosted on a sibling subdomain

### Requirement: Reader target shall remain capability-gated
The system SHALL expose Instant View or reader presentation only for pages that are explicitly supported by the local support rules.

#### Scenario: Page supports Instant View
- **WHEN** the current page satisfies the Instant View support rules
- **THEN** the system SHALL allow the user to enter the reader target for that page

#### Scenario: Page does not support Instant View
- **WHEN** the current page does not satisfy the Instant View support rules
- **THEN** the system SHALL NOT expose Instant View as if it were universally available

### Requirement: Browserable flows shall stay in the same browser container
The system SHALL keep browserable HTTP flows inside the active regular browser container until link routing resolves to native or external navigation.

#### Scenario: Redirect stays in browser
- **WHEN** the active browser page redirects to another browserable HTTP page
- **THEN** the system SHALL continue the flow in the same browser container
- **AND** it SHALL NOT replace the scene with a different browser presentation container

#### Scenario: Complex web flow stays in browser
- **WHEN** the active browser flow enters authentication, Cloudflare challenge, upload, popup, or multi-step form states and routing still resolves to app-browser navigation
- **THEN** the system SHALL keep that flow in the same browser container
- **AND** browser history, address state, and return path SHALL remain continuous

#### Scenario: Sibling subdomain hop stays in browser
- **WHEN** the active browser page navigates to a sibling subdomain that is still intended for in-app browser navigation
- **THEN** the system SHALL keep the flow inside the same browser container
- **AND** it SHALL NOT reopen the request as a separate right-to-left browser scene
