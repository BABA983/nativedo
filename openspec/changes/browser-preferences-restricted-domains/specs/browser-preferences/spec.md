## ADDED Requirements

### Requirement: Browser preferences shall persist across app relaunch
The browser SHALL persist browser-scoped preferences locally so user choices survive app relaunch.

#### Scenario: Relaunch after changing browser preferences
- **WHEN** the user updates a browser preference and later relaunches the app
- **THEN** the browser preference store SHALL restore the last saved preference values
- **AND** browser behavior SHALL reflect those restored values without requiring reconfiguration

### Requirement: Browser shall support a default external-link preference
The browser SHALL support a persisted preference that controls how eligible external web links are opened when no stricter routing rule applies.

#### Scenario: Eligible external web link follows browser preference
- **WHEN** a link resolves to an eligible external web target and no native route or explicit restricted-domain override applies
- **THEN** the browser SHALL choose the in-app browser or system browser according to the saved external-link preference

#### Scenario: Non-browser external app target bypasses browser preference
- **WHEN** a link resolves to a non-browser external app target such as `mailto:`, `tel:`, or `intent://`
- **THEN** the system SHALL continue using external-app handling
- **AND** the default external-link preference SHALL NOT override that behavior

### Requirement: Browser shall support configurable address-search engine preference
The browser SHALL support a persisted preference that determines which search engine is used when address input is classified as a search query.

#### Scenario: Address input is treated as search query
- **WHEN** the user submits address input that is not resolved as a direct URL
- **THEN** the browser SHALL build the search request using the configured search-engine preference

#### Scenario: Search-engine preference changes
- **WHEN** the user changes the browser search-engine preference
- **THEN** subsequent address-search submissions SHALL use the updated engine
- **AND** previously visited URLs SHALL remain unaffected
