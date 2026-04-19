# immersive-viewer-chrome Specification

## Purpose
TBD - created by archiving change align-telegram-chrome-menu. Update Purpose after archive.
## Requirements
### Requirement: Media viewer chrome shall behave as an immersive overlay
The system SHALL treat media viewer chrome as an immersive overlay with a unified visibility lifecycle for top and bottom chrome regions.

#### Scenario: Toggle viewer chrome visibility
- **WHEN** the user taps the media canvas while the viewer is interactive
- **THEN** the top chrome and bottom chrome MUST toggle visibility together
- **THEN** they MUST share the same visibility progress rather than animating independently

#### Scenario: Auto-hide viewer chrome
- **WHEN** viewer playback or idle rules request chrome dismissal
- **THEN** the system MUST hide the overlay chrome without closing the viewer content
- **THEN** the viewer content MUST remain interactive after chrome dismissal

### Requirement: Viewer action popup shall suspend chrome auto-hide
The system SHALL keep chrome interaction stable while the viewer more-actions popup is open.

#### Scenario: Open viewer more-actions
- **WHEN** the user opens the viewer more-actions popup
- **THEN** the popup MUST be anchored to the viewer menu control
- **THEN** chrome auto-hide MUST be suspended until the popup is dismissed

#### Scenario: Dismiss viewer more-actions
- **WHEN** the viewer more-actions popup is dismissed
- **THEN** the popup MUST close without forcing the viewer itself to dismiss
- **THEN** chrome auto-hide behavior MUST return to its prior rule set

### Requirement: Viewer action availability shall remain content-aware
The system SHALL scope viewer actions to the active media item and current viewer context.

#### Scenario: Show item-specific actions
- **WHEN** the active viewer item changes between different media contexts
- **THEN** the viewer popup MUST update its visible actions to match the active item
- **THEN** unavailable actions MUST not be shown as if they were always valid

