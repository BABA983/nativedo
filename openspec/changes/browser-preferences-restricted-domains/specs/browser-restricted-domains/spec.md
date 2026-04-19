## ADDED Requirements

### Requirement: Browser shall support restricted domains that bypass in-app browser opening
The browser SHALL support a user-managed restricted-domain list that forces matching browser-target links to bypass the in-app browser.

#### Scenario: Matching restricted domain is opened
- **WHEN** a link resolves to a hostname on the restricted-domain list and would otherwise open in the in-app browser
- **THEN** the system SHALL bypass the in-app browser target for that link
- **AND** the link SHALL be handed off through the configured external opening path

#### Scenario: Non-matching domain is opened
- **WHEN** a link does not match the restricted-domain list
- **THEN** the system SHALL continue evaluating the normal browser routing rules
- **AND** the restricted-domain feature SHALL NOT affect unrelated hosts

### Requirement: Restricted-domain entries shall persist and be manageable
The browser SHALL persist restricted-domain entries locally and expose a browser-owned management flow for them.

#### Scenario: Relaunch after adding restricted domain
- **WHEN** the user adds one or more restricted domains and later relaunches the app
- **THEN** the restricted-domain store SHALL restore those domains
- **AND** matching links SHALL continue bypassing the in-app browser

#### Scenario: User removes restricted domain
- **WHEN** the user removes a restricted-domain entry from browser settings
- **THEN** future matching links SHALL return to normal routing behavior
- **AND** the removed entry SHALL no longer appear in the management surface
