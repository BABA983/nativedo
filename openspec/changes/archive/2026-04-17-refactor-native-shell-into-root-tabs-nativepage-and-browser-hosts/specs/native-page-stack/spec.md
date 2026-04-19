## ADDED Requirements

### Requirement: Native page stack shall host pushed native pages above root scenes
The system SHALL present native pages opened from root scenes in a dedicated `NativePageStackHost` layered above `RootTabsHost`, while keeping the current root scene mounted behind the stack for transition continuity.

#### Scenario: Open topic from home
- **WHEN** the user opens a topic from the home root scene
- **THEN** the system MUST push a topic entry into `NativePageStackHost`
- **THEN** the topic page MUST appear above the current root scene instead of replacing the root scene route

#### Scenario: Push another native page from a native page
- **WHEN** the active native page opens another native page such as another profile or topic
- **THEN** the system MUST append a new native page entry on top of the existing native page stack
- **THEN** the previously active native page MUST remain available as the immediate back target

#### Scenario: Return to root when stack becomes empty
- **WHEN** the top native page is closed and the native page stack becomes empty
- **THEN** the system MUST reveal the previously active root scene as the foreground scene
- **THEN** the root scene MUST resume ownership of its own chrome

### Requirement: Native page stack shall centralize Telegram-style back interactions
The system SHALL centralize predictive back, left-edge swipe-back, and stack close animations for native pages inside `NativePageStackHost` instead of delegating those interactions to individual native pages.

#### Scenario: Predictive back updates active page transform
- **WHEN** the system back gesture progresses while a native page is active
- **THEN** `NativePageStackHost` MUST update the active page translation, scale, and pivot transform from gesture progress
- **THEN** the underlying previous scene MUST be revealed by the host transition rather than by page-specific animation code

#### Scenario: Cancelling predictive back restores the current page
- **WHEN** the user abandons a predictive back or edge-swipe gesture before the close threshold
- **THEN** `NativePageStackHost` MUST animate the active page back to its fully open position
- **THEN** the native page stack depth MUST remain unchanged

#### Scenario: Completing predictive back pops the active entry
- **WHEN** the user completes a predictive back or edge-swipe gesture past the close threshold
- **THEN** `NativePageStackHost` MUST animate the active native page out of the stack
- **THEN** the stack MUST remove only the top entry and reveal the previous scene

### Requirement: Native page stack shall let native pages publish host chrome data
The system SHALL allow pushed native pages to publish host-level chrome data such as title and subtitle to `NativePageStackHost` while the host retains ownership of back affordances and container chrome layout.

#### Scenario: Topic page updates host title
- **WHEN** a topic page reaches the state where its collapsed title should appear in host chrome
- **THEN** the topic page MUST be able to publish that title to `NativePageStackHost`
- **THEN** the host chrome MUST update without moving title ownership back to app root

#### Scenario: Profile page updates host title
- **WHEN** a profile page resolves a display name or username for the current native page
- **THEN** the profile page MUST be able to publish the resolved title to `NativePageStackHost`
- **THEN** the host chrome MUST display the latest published title until the page is closed or replaced
