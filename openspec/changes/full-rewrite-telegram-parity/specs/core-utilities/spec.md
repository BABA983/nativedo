## ADDED Requirements

### Requirement: Telegram parity for adopted utility APIs
For utility classes and methods intentionally introduced with the same public name as Telegram substrate APIs, the system SHALL preserve the corresponding Telegram public behavior contract by default, even when only a minimal subset is implemented. The system SHALL not add convenience side effects that change the upstream semantics unless the deviation is explicitly documented in the change artifacts before implementation.

#### Scenario: Minimal subset keeps upstream semantics
- **WHEN** the rewrite adopts a Telegram-named helper such as `showKeyboard()`, `hideKeyboard()`, `DispatchQueue`, or `LayoutHelper`
- **THEN** the implemented subset keeps Telegram-compatible method semantics for the adopted APIs rather than redefining them as project-local convenience wrappers

#### Scenario: Intentional deviation is documented first
- **WHEN** an adopted Telegram-named API must diverge because of project constraints or removed Telegram-only dependencies
- **THEN** the deviation and rationale are written into the change artifacts before code using that API is merged

### Requirement: No speculative helper surface
The system SHALL NOT add new `core/util` helper classes or wrapper APIs unless they either directly port an adopted Telegram source API or have an immediate in-tree consumer in the same phase. Helper APIs introduced only to satisfy checklist completeness SHALL be deferred until the first real caller exists.

#### Scenario: Helper has no Telegram source and no caller
- **WHEN** a proposed utility class has no Telegram public counterpart and no current screen or infrastructure consumer
- **THEN** the helper is not introduced yet as a standalone file

### Requirement: LayoutHelper keeps Telegram layout param semantics
The system SHALL implement `LayoutHelper` as a parity-first subset port of Telegram `org.telegram.ui.Components.LayoutHelper`, preserving the imported method names, return types, and layout param semantics for the adopted subset.

#### Scenario: Scroll params preserve Telegram type
- **WHEN** `LayoutHelper.createScroll(...)` is used in the adopted subset
- **THEN** it returns `ScrollView.LayoutParams` with Telegram-compatible gravity and margin semantics rather than a project-local substitute type

### Requirement: dp conversion utility
The system SHALL provide a `dp(value: Int): Int` function that converts density-independent pixels to actual pixels based on the current display density. The system SHALL also provide `dpf2(value: Float): Float` for float-precision conversion.

#### Scenario: Standard density conversion
- **WHEN** `dp(16)` is called on a device with density 2.0
- **THEN** the function returns 32

#### Scenario: Float precision conversion
- **WHEN** `dpf2(16.5f)` is called on a device with density 2.0
- **THEN** the function returns 33.0f

### Requirement: Linear interpolation utility
The system SHALL provide a `lerp(a: Float, b: Float, t: Float): Float` function that linearly interpolates between two values.

#### Scenario: Midpoint interpolation
- **WHEN** `lerp(0f, 100f, 0.5f)` is called
- **THEN** the function returns 50f

#### Scenario: Edge values
- **WHEN** `lerp(10f, 20f, 0f)` is called
- **THEN** the function returns 10f

### Requirement: Screen metrics access
The system SHALL provide global access to display metrics including `displaySize` (Point), `statusBarHeight` (Int), `navigationBarHeight` (Int), and `density` (Float). These values SHALL be updated on configuration changes.

#### Scenario: Screen metrics initialization
- **WHEN** the application starts
- **THEN** `displaySize`, `statusBarHeight`, `navigationBarHeight`, and `density` are populated with current device values

#### Scenario: Configuration change updates metrics
- **WHEN** a configuration change occurs (rotation, multi-window)
- **THEN** all screen metrics are recalculated from the current display

### Requirement: Color utilities
The system SHALL provide `blendColors(color1: Int, color2: Int, factor: Float): Int` for blending two ARGB colors, and `setAlpha(color: Int, alpha: Int): Int` for modifying alpha.

#### Scenario: Color blending
- **WHEN** `blendColors(Color.BLACK, Color.WHITE, 0.5f)` is called
- **THEN** the function returns a gray color (#FF808080)

### Requirement: Keyboard utilities
The system SHALL provide functions to show and hide the soft keyboard for a given View, and to detect the current keyboard height.

#### Scenario: Show keyboard
- **WHEN** `showKeyboard(view)` is called
- **THEN** the soft keyboard is requested for that view

#### Scenario: Hide keyboard
- **WHEN** `hideKeyboard(view)` is called
- **THEN** the soft keyboard is dismissed
