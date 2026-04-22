## ADDED Requirements

### Requirement: NativeDo theme runtime substrate must remain project-specific
`NdTheme.kt`, `LightTheme.kt`, `DarkTheme.kt`, `NdUiConfig.kt`, and `AppLifecycleMonitor.kt` SHALL remain NativeDo-specific runtime substrate rather than being treated as Telegram-derived strict ports.

#### Scenario: Theme runtime file is reviewed
- **WHEN** a Phase 1 theme or lifecycle substrate file is changed
- **THEN** the file MUST be kept within its project-specific responsibility boundary and MUST NOT claim Telegram-derived parity it does not actually implement

#### Scenario: Global state helper is proposed
- **WHEN** a new global theme, lifecycle, or UI-config helper is proposed in Phase 1
- **THEN** the helper MUST be justified by a real current runtime need and MUST NOT grow into a generic catch-all global utility

### Requirement: Theme runtime substrate must provide a stable Phase 1 gate
Phase 1 SHALL provide a stable runtime theme baseline before later phases depend on it.

#### Scenario: Theme mode changes at runtime
- **WHEN** the current theme mode changes between `Light`, `Dark`, or `System`
- **THEN** `NdTheme` MUST update its active palette, persist the mode, and post `themeDidChange`

#### Scenario: Theme-dependent host is active
- **WHEN** the current host screen is already attached
- **THEN** Phase 1 MUST allow the host to react to theme change through the existing runtime notification path and themed drawable helpers

### Requirement: Phase 1 runtime substrate must be verified through focused tests and host smoke
Phase 1 MUST verify its runtime substrate using focused verification instead of broad standalone substrate coverage.

#### Scenario: Contract-like substrate behavior is tested
- **WHEN** a retained test protects a parity edge case or an important runtime contract
- **THEN** the test MAY remain as a focused verification artifact

#### Scenario: Theme and lifecycle substrate are proposed as complete
- **WHEN** Phase 1 is marked complete
- **THEN** the current host MUST be able to exercise theme notification, themed drawables, and minimal lifecycle notifications without requiring later phases to patch the substrate first
