## ADDED Requirements

### Requirement: Phase 1 Telegram-derived substrate must stay within the approved subset
Phase 1 SHALL maintain its Telegram-derived substrate files within the `Phase 0` approved strict subset or documented divergence boundary.

#### Scenario: Derived substrate file is edited
- **WHEN** `AndroidUtilities.kt`, `Utilities.kt`, `DispatchQueue.kt`, `FileLog.kt`, `LayoutHelper.kt`, `CubicBezierInterpolator.kt`, `AnimatedFloat.kt`, `AnimatedColor.kt`, or `NotificationCenter.kt` is changed in Phase 1
- **THEN** the file MUST keep its existing GPL provenance header and MUST NOT add new project-local convenience wrapper APIs outside the approved subset

#### Scenario: Documented divergence file is retained
- **WHEN** a Phase 1 file already has an approved documented divergence from Telegram upstream
- **THEN** the implementation MUST stay inside the recorded divergence boundary instead of widening it implicitly

### Requirement: No-caller substrate must remain frozen
Telegram-derived substrate with no current real caller MUST remain frozen in Phase 1 until a real consumer requires the surface.

#### Scenario: No-caller helper is considered for expansion
- **WHEN** `LayoutHelper.kt`, `AnimatedFloat.kt`, or `AnimatedColor.kt` has no real business caller
- **THEN** Phase 1 MUST NOT expand its public API surface just to improve perceived parity completeness

#### Scenario: Real caller appears later
- **WHEN** a later phase introduces the first real consumer for a currently frozen substrate file
- **THEN** that later phase MUST own the public surface expansion and document the adopted subset it needs

### Requirement: Low-value standalone substrate tests must not survive by default
Phase 1 MUST remove standalone substrate tests that do not protect a parity edge case or a known regression.

#### Scenario: Test only checks generic queue behavior
- **WHEN** a standalone substrate test only validates generic helper or queue behavior without protecting a parity edge case or a known regression
- **THEN** the test MUST be removed in Phase 1

#### Scenario: Test protects adopted parity behavior
- **WHEN** a substrate test protects an adopted parity edge case that is easy to silently regress
- **THEN** Phase 1 MAY keep the test as an explicit exception to the default rule
