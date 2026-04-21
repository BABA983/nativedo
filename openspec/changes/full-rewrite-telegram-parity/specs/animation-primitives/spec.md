## ADDED Requirements

### Requirement: Cubic bezier interpolator
The system SHALL provide a `CubicBezierInterpolator` class implementing `android.view.animation.Interpolator` with configurable control points (x1, y1, x2, y2). The system SHALL include predefined instances: `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, `EASE_OUT_QUINT`, `DEFAULT`, and `DECELERATE`.

#### Scenario: Custom cubic bezier curve
- **WHEN** `CubicBezierInterpolator(0.4f, 0f, 0.2f, 1f).getInterpolation(0.5f)` is called
- **THEN** the function returns the correct bezier curve value for t=0.5

#### Scenario: Predefined EASE_OUT usage
- **WHEN** `CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f)` is called
- **THEN** the function returns 1.0f

### Requirement: AnimatedFloat value wrapper
The system SHALL provide an `AnimatedFloat` class that wraps a float value and smoothly transitions between old and new values over a configurable duration. The transition SHALL use a configurable interpolator (default: `CubicBezierInterpolator.DEFAULT`).

#### Scenario: Value transition
- **WHEN** `animatedFloat.set(100f)` is called with current value 0f and duration 300ms
- **THEN** `animatedFloat.get()` returns interpolated values between 0f and 100f over the 300ms duration

#### Scenario: Immediate set
- **WHEN** `animatedFloat.set(100f, animated = false)` is called
- **THEN** `animatedFloat.get()` immediately returns 100f with no animation

### Requirement: AnimatedColor value wrapper
The system SHALL provide an `AnimatedColor` class that smoothly transitions between two ARGB color values, interpolating each channel (alpha, red, green, blue) independently.

#### Scenario: Color transition
- **WHEN** `animatedColor.set(Color.RED)` is called with current value Color.BLUE and duration 200ms
- **THEN** `animatedColor.get()` returns blended colors transitioning from BLUE to RED over 200ms

### Requirement: Spring animation utility
The system SHALL provide a spring-based animation utility with configurable stiffness and damping ratio, suitable for overscroll and bounce effects.

#### Scenario: Spring bounce
- **WHEN** a spring animation is started with stiffness=300 and dampingRatio=0.6
- **THEN** the animated value overshoots the target before settling
