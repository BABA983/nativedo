## ADDED Requirements

### Requirement: Reader scene shall be available only for supported browser pages
The system SHALL expose Instant View or reader entry only when the active browser page is supported by the browser family's reader pipeline.

#### Scenario: Supported page exposes reader entry
- **WHEN** the active browser page is recognized as reader-capable
- **THEN** the browser chrome SHALL expose an Instant View or reader entry for that page
- **AND** invoking that entry SHALL open the reader scene for the same source session

#### Scenario: Unsupported page does not expose reader entry
- **WHEN** the active browser page is not reader-capable
- **THEN** the browser SHALL keep the user in the normal browser surface
- **AND** reader-specific entry points SHALL remain unavailable for that page

### Requirement: Reader scene shall open as a browser-family follow-up surface
The system SHALL present reader content as a dedicated browser-family follow-up surface rather than flattening it into the same web page container.

#### Scenario: Open reader from active browser session
- **WHEN** the user opens Instant View from a supported browser page
- **THEN** the browser-family host SHALL present a reader surface above the source browser session
- **AND** the source browser session SHALL remain recoverable while the reader is active

#### Scenario: Reader surface becomes the active foreground browser-family surface
- **WHEN** the reader scene opens successfully
- **THEN** the reader SHALL become the active foreground browser-family surface
- **AND** reader-specific navigation and content controls SHALL replace the regular browser page view

### Requirement: Reader scene shall preserve return continuity to its source browser session
The system SHALL return control to the originating browser session when the reader closes, provided that source session still exists.

#### Scenario: Close reader and return to source browser
- **WHEN** the user closes an active reader scene whose source browser session still exists
- **THEN** the browser-family host SHALL restore that same source browser session
- **AND** the user SHALL return to the previously opened page context instead of a fresh browser request

#### Scenario: Minimize and later restore reader scene
- **WHEN** an active reader scene is minimized and later restored
- **THEN** the browser-family host SHALL restore the same reader scene
- **AND** the source browser session SHALL remain recoverable when the reader later closes
