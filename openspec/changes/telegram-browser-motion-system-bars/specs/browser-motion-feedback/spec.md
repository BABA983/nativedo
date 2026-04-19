## ADDED Requirements

### Requirement: Browser route shall use Telegram-aligned enter and exit feedback
The browser route SHALL enter and exit with a short layered transition that combines horizontal translation and alpha instead of relying on a slow single-axis slide.

#### Scenario: Browser route enters
- **WHEN** the app browser route becomes visible
- **THEN** the browser content SHALL animate in with horizontal translation and alpha
- **AND** the browser top bar SHALL remain visible throughout the enter transition

#### Scenario: Browser route exits
- **WHEN** the browser route is dismissed
- **THEN** the browser content SHALL animate out with horizontal translation and alpha
- **AND** the previous scene SHALL return without a blank chrome interval

### Requirement: Browser swipe dismiss shall track presentation in real time
The browser SHALL support a Telegram-like horizontal dismiss gesture that continuously drives route presentation and settles with adaptive timing.

#### Scenario: Dismiss gesture starts in browse scene
- **WHEN** the user starts a horizontal dismiss gesture while the browser is in browse scene
- **THEN** the browser SHALL accept the gesture from the configured dismiss region
- **AND** the route presentation SHALL track the gesture position in real time

#### Scenario: Dismiss gesture ends
- **WHEN** the dismiss gesture ends
- **THEN** the browser SHALL settle either back to open or to dismissed state based on distance or velocity
- **AND** the settle animation SHALL use a duration that scales with the remaining distance and SHALL NOT be shorter than 250 milliseconds

### Requirement: Browser loading and find feedback shall be smoothed
The browser SHALL smooth both loading progress and in-page find interactions so that WebView state changes do not produce abrupt or excessive UI churn.

#### Scenario: Page loading updates progress
- **WHEN** the active page reports loading progress changes
- **THEN** the browser progress indicator SHALL animate smoothly toward the reported progress
- **AND** the indicator SHALL dismiss only after loading reaches completion

#### Scenario: Search query changes rapidly
- **WHEN** the user types rapidly in the browser find field
- **THEN** the browser SHALL debounce dispatches to `findAllAsync`
- **AND** stale query results SHALL NOT overwrite the latest visible search state

### Requirement: Browser header shall collapse with page scroll
The browser SHALL derive a compact header state from page scroll deltas so that the browser chrome becomes denser while the user reads long content.

#### Scenario: User scrolls downward on long content
- **WHEN** the page scrolls downward while the browser is in browse scene
- **THEN** the browser top bar SHALL move toward its compact height
- **AND** the title container and progress presentation SHALL remain readable during the collapse

#### Scenario: User scrolls upward or returns near top
- **WHEN** the page scrolls upward or returns toward the top
- **THEN** the browser top bar SHALL restore toward its expanded state
- **AND** temporary scenes such as search or address editing SHALL suspend collapse behavior
