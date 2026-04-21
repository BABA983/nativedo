## ADDED Requirements

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
- **WHEN** `showKeyboard(editText)` is called
- **THEN** the soft keyboard is shown and the editText receives focus

#### Scenario: Hide keyboard
- **WHEN** `hideKeyboard(view)` is called
- **THEN** the soft keyboard is dismissed
