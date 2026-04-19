## ADDED Requirements

### Requirement: Exactly one scene owns the visible top bar
The application SHALL resolve a single visible top bar owner for each active navigation scene among root, reader/topic, and browser. The system MUST NOT enter a steady state where no scene owns the top bar while a screen requiring navigation chrome is active.

#### Scenario: Root scene owns the top bar
- **WHEN** the active destination is a root-owned screen such as home or profile
- **THEN** the root top bar SHALL be the visible owner
- **AND** browser and reader/topic top bars SHALL not remain active owners

#### Scenario: Topic or reader scene owns the top bar
- **WHEN** the active destination is a topic or other reader-style screen
- **THEN** the application SHALL show a reader/topic-compatible top bar with back navigation semantics
- **AND** root bottom-tab chrome state SHALL not override that scene ownership

#### Scenario: Browser scene owns the top bar
- **WHEN** the active destination is `APP_BROWSER_ROUTE`
- **THEN** the browser top bar SHALL be the visible owner
- **AND** the root top bar SHALL not remain visible as a second active owner

### Requirement: Shared Telegram-aligned top bar geometry
The application SHALL use a consistent top bar geometry across root, reader/topic, and browser scenes that mirrors Telegram's arrangement: a leading navigation control region, a central title container region, and a trailing action region aligned to the right edge.

#### Scenario: Root scene action placement
- **WHEN** a root-owned screen exposes search or contextual actions
- **THEN** those actions SHALL appear in the trailing region
- **AND** the title container SHALL remain visually centered or weight-balanced between the leading and trailing regions

#### Scenario: Topic scene title placement
- **WHEN** a topic or reader-style screen is visible
- **THEN** the back navigation control SHALL occupy the leading region
- **AND** the topic title SHALL render inside the central title container region

#### Scenario: Browser scene action placement
- **WHEN** the browser is in its normal browse scene
- **THEN** the leading region SHALL present back or close semantics
- **AND** the title container SHALL show page identity in the center region
- **AND** the primary menu or options entry SHALL be anchored in the trailing region

### Requirement: Scene transitions preserve top bar continuity
The application SHALL preserve visible top bar continuity during transitions between root, reader/topic, and browser scenes. The transition MUST NOT expose a blank top bar state or two competing top bars at the same time.

#### Scenario: Topic opens app browser
- **WHEN** a user opens a link from a topic or post into the in-app browser
- **THEN** the transition SHALL keep a visible top bar throughout the handoff
- **AND** once the browser route is active the browser top bar SHALL be the only visible owner

#### Scenario: Browser closes back to previous scene
- **WHEN** the browser route is dismissed
- **THEN** the previous scene's top bar SHALL become visible without a blank intermediate state
- **AND** the browser top bar SHALL stop being the active owner
