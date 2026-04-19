## ADDED Requirements

### Requirement: Browser address editing shall expose mixed suggestion sources
The browser SHALL expose a richer address surface that can combine recent links, recent searches, bookmarks, and quick browser actions while address editing is active.

#### Scenario: Address editing opens with available browser data
- **WHEN** the user enters browser address editing and browser history, bookmarks, or recent searches are available
- **THEN** the browser SHALL present address suggestions drawn from the supported suggestion sources
- **AND** the surface SHALL remain browser-scoped rather than falling back to a plain input-only mode

#### Scenario: Address editing has no available suggestion data
- **WHEN** the user enters browser address editing and no suggestion sources are available
- **THEN** the browser SHALL still present the address input and available quick actions
- **AND** the browser SHALL remain fully usable without suggestion data

### Requirement: Browser recent searches shall persist independently from page history
The browser SHALL persist recent search queries independently from visited-page history so address suggestions can distinguish search intent from page visits.

#### Scenario: Search query is submitted from address surface
- **WHEN** the user submits a value from address editing that resolves as a search query
- **THEN** the browser SHALL record that search query in recent-search history
- **AND** future address-editing sessions SHALL be able to suggest it separately from visited links

#### Scenario: Relaunch after recording recent searches
- **WHEN** the user relaunches the app after recording one or more browser search queries
- **THEN** the browser recent-search store SHALL restore those queries
- **AND** address suggestions SHALL remain able to surface them

### Requirement: Address suggestion selection shall preserve suggestion-specific semantics
The browser SHALL handle address-suggestion selection according to the selected suggestion type instead of flattening every choice into a raw string submission.

#### Scenario: User selects recent link or bookmark suggestion
- **WHEN** the user selects a suggestion backed by a visited link or bookmarked page
- **THEN** the browser SHALL navigate directly to that URL
- **AND** address editing SHALL dismiss after the selection is applied

#### Scenario: User selects recent search or quick action suggestion
- **WHEN** the user selects a recent-search suggestion or browser quick action from the address surface
- **THEN** the browser SHALL execute the corresponding search submission or browser action semantics
- **AND** the selection SHALL NOT be treated as an arbitrary URL string by default
