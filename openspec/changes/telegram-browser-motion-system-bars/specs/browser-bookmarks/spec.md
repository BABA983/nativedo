## ADDED Requirements

### Requirement: Browser shall support bookmarking the current page
The browser SHALL allow the user to add or remove a bookmark for the current page while browsing.

#### Scenario: Bookmark current page
- **WHEN** the user invokes the bookmark action for the current page
- **THEN** the browser SHALL persist a bookmark record for that page
- **AND** the bookmark action presentation SHALL update to reflect the saved state

#### Scenario: Remove existing bookmark
- **WHEN** the current page is already bookmarked and the user invokes the bookmark action again
- **THEN** the browser SHALL remove the bookmark record
- **AND** the bookmark action presentation SHALL update to reflect the unsaved state

### Requirement: Browser bookmarks shall survive app relaunch
The browser SHALL persist bookmarks independently from recent-history entries so saved pages remain available across app restarts.

#### Scenario: Relaunch app after saving bookmark
- **WHEN** the user relaunches the app after saving one or more bookmarks
- **THEN** the browser bookmark store SHALL restore the saved bookmarks
- **AND** opening a bookmarked page SHALL reflect its bookmarked state in browser chrome

### Requirement: Browser chrome shall expose bookmark entry points
The browser SHALL expose bookmark actions through browser-scoped surfaces rather than leaving bookmark UI as a placeholder.

#### Scenario: Overflow menu shows bookmark action
- **WHEN** the user opens the browser overflow menu on a bookmarkable page
- **THEN** the menu SHALL expose a bookmark add or remove action for the current page

#### Scenario: Bookmark list entry is available
- **WHEN** the user enters the browser bookmark surface
- **THEN** the browser SHALL show the saved bookmark records
- **AND** selecting a bookmark SHALL reopen that bookmarked page
