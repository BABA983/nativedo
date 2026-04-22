## ADDED Requirements

### Requirement: Cubic bezier interpolator
The system SHALL provide a `CubicBezierInterpolator` class implementing `android.view.animation.Interpolator` with configurable control points (x1, y1, x2, y2). For the adopted Telegram parity subset, the system SHALL expose the same predefined cubic instances as Telegram `org.telegram.ui.Components.CubicBezierInterpolator`: `DEFAULT`, `EASE_OUT`, `EASE_OUT_QUINT`, `EASE_IN`, `EASE_BOTH`, and `EASE_OUT_BACK`.

#### Scenario: Custom cubic bezier curve
- **WHEN** `CubicBezierInterpolator(0.4f, 0f, 0.2f, 1f).getInterpolation(0.5f)` is called
- **THEN** the function returns the correct bezier curve value for t=0.5

#### Scenario: Telegram EASE_BOTH usage
- **WHEN** `CubicBezierInterpolator.EASE_BOTH.getInterpolation(1.0f)` is called
- **THEN** the function returns 1.0f

### Requirement: AnimatedFloat value wrapper
The system SHALL provide an `AnimatedFloat` class matching the adopted Telegram `org.telegram.ui.Components.AnimatedFloat` public contract for the imported subset. The transition SHALL use a configurable interpolator (default: `CubicBezierInterpolator.DEFAULT`). The primary mutation APIs SHALL be `set(value)` and `set(value, force)`, with `force(value)` as the explicit no-animation path. Frame-driven callers SHALL read the animated value through `getValue()`.

#### Scenario: Value transition
- **WHEN** `animatedFloat.set(100f)` is called with current value 0f and duration 300ms
- **THEN** `animatedFloat.getValue()` returns interpolated values between 0f and 100f over the 300ms duration

#### Scenario: Immediate set
- **WHEN** `animatedFloat.set(100f, force = true)` is called
- **THEN** `animatedFloat.getValue()` immediately returns 100f with no animation

### Requirement: AnimatedColor value wrapper
The system SHALL provide an `AnimatedColor` class matching the adopted Telegram `org.telegram.ui.Components.AnimatedColor` public contract for the imported subset. The class SHALL smoothly transition between two ARGB color values, interpolating each channel (alpha, red, green, blue) independently, and SHALL expose `set(color)`, `set(color, force)`, and `force(color)` semantics consistent with Telegram.

#### Scenario: Color transition
- **WHEN** `animatedColor.set(Color.RED)` is called with current value Color.BLUE and duration 200ms
- **THEN** `animatedColor.get()` returns blended colors transitioning from BLUE to RED over 200ms

### Requirement: Spring motion is consumer-driven
The system SHALL NOT introduce a standalone public spring helper class merely to satisfy substrate completeness. Spring-based animation SHALL be introduced only when a concrete UI consumer requires it, using AndroidX DynamicAnimation directly unless a Telegram-style public API is explicitly documented in the change artifacts first.

#### Scenario: No concrete consumer yet
- **WHEN** no current screen, cell, or gesture flow depends on spring motion
- **THEN** no standalone `SpringAnimationUtils.kt` file is required by this change

#### Scenario: First real spring consumer appears later
- **WHEN** a concrete overscroll or bounce interaction later needs spring motion
- **THEN** that consumer may use AndroidX DynamicAnimation directly, and any extracted shared API must be documented in the change artifacts before introduction
