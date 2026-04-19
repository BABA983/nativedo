## MODIFIED Requirements

### Requirement: Native page stack shall centralize Telegram-style back interactions
The system SHALL centralize non-interactive close, predictive back, and manual edge-swipe-back for pushed native pages inside `NativePageStackHost`, and the resulting motion semantics SHALL match Telegram Android `ActionBarLayout` instead of mixing multiple unrelated back engines.

#### Scenario: Top bar back uses Telegram standard close semantics
- **WHEN** the user taps the pushed-page top bar back button or triggers a non-interactive back while a native page is active
- **THEN** `NativePageStackHost` SHALL use Telegram's standard close family rather than the predictive-back shrink renderer
- **AND** the host SHALL restore and reveal the previous scene during the close
- **AND** the standard close motion SHALL follow Telegram's standard close distance, timing, and easing semantics

#### Scenario: Predictive back progress uses Telegram predictive geometry
- **WHEN** interactive back progress updates while a native page is active
- **THEN** `NativePageStackHost` SHALL drive the active page with Telegram predictive-back geometry, including its small progress-era translation range
- **AND** the host SHALL apply edge-aware X pivot behavior and touch-driven Y pivot behavior consistent with Telegram
- **AND** the host SHALL reveal the previous scene beneath the current page during progress

#### Scenario: Manual left-edge drag reuses the predictive renderer
- **WHEN** the user performs a manual left-edge drag on a path without system predictive back progress
- **THEN** `NativePageStackHost` SHALL normalize that drag into the same interactive-back progress model used by system predictive back
- **AND** the manual drag path SHALL NOT use a separate raw-distance renderer that diverges from Telegram predictive-back semantics

#### Scenario: Interactive back cancellation restores the page with Telegram cancel motion
- **WHEN** the user abandons an interactive back before completion
- **THEN** `NativePageStackHost` SHALL restore the current page with Telegram predictive-back cancel timing and easing
- **AND** the stack depth SHALL remain unchanged

#### Scenario: Interactive back completion settles out with Telegram completion motion
- **WHEN** the user completes an interactive back
- **THEN** `NativePageStackHost` SHALL continue the current page offscreen using Telegram predictive-back completion timing and easing
- **AND** the stack SHALL pop only the top entry and reveal the previous scene

### Requirement: Native page stack host shall own Telegram-style reveal, clipping, and shadow during back motion
The system SHALL keep previous-scene reveal, current-page clipping, and host-level shadow/scrim treatment inside `NativePageStackHost` so pushed page content does not implement its own back animation.

#### Scenario: Previous scene reveal remains host-owned
- **WHEN** any native-page back motion is active
- **THEN** the previous scene SHALL be mounted and revealed by `NativePageStackHost`
- **AND** the current page SHALL receive host-owned clipping and shadow treatment rather than page-local back animation code

#### Scenario: Pushed content remains content-owned only
- **WHEN** a pushed topic or profile page participates in back motion
- **THEN** the page SHALL continue to own only its content and host chrome data publication
- **AND** the page SHALL NOT become the owner of back-motion transforms that belong to `NativePageStackHost`
