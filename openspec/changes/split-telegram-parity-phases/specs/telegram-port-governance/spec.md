## ADDED Requirements

### Requirement: Telegram-related files must have explicit classification
The Telegram parity rewrite SHALL assign every Telegram-related file one explicit classification before the file is treated as implemented or extended: `Telegram-derived` or `Telegram-inspired project-specific`.

#### Scenario: Adopted upstream class is ported
- **WHEN** a phase adopts a Telegram upstream class or same-name public API as its implementation contract
- **THEN** the file MUST be classified as `Telegram-derived` unless the artifact explicitly records why it is not

#### Scenario: Project-specific host file references Telegram architecture
- **WHEN** a NativeDo host or integration file only borrows Telegram responsibility boundaries without translating Telegram implementation
- **THEN** the file MUST be classified as `Telegram-inspired project-specific`

### Requirement: Telegram-derived files must carry provenance headers
Every `Telegram-derived` file MUST include the repository's Telegram-derived GPL header before the file can be considered compliant.

#### Scenario: Strict parity port is added
- **WHEN** a new strict parity port file is introduced
- **THEN** the file MUST include upstream class name, upstream source path, source reference, Telegram copyright, NativeDo copyright, and SPDX information

#### Scenario: Mixed file contains translated Telegram implementation
- **WHEN** a file contains substantial translated or adapted Telegram implementation blocks even if other parts are project-specific
- **THEN** the file MUST be treated as `Telegram-derived` and MUST include the derived provenance header

### Requirement: Strict parity port is the default for adopted upstream APIs
If a phase adopts a Telegram upstream class, same-name public API, or matching responsibility boundary as a parity port target, the implementation MUST preserve the upstream public API shape and behavior semantics except for documented platform-driven adaptations.

#### Scenario: Upstream API exists for the adopted class
- **WHEN** the rewrite ports an adopted Telegram class
- **THEN** the implementation MUST keep the upstream primary API names, core state transitions, and behavior semantics as the default contract

#### Scenario: Necessary divergence is introduced
- **WHEN** the implementation must diverge because of Kotlin language adaptation, Android platform constraints, single-account scope, or removal of MTProto/native-only behavior
- **THEN** the phase artifact MUST record the upstream source, divergence reason, divergence boundary, and affected callers before the code is accepted

### Requirement: Unnecessary helper and wrapper drift is prohibited
The rewrite MUST NOT introduce project-local helper, wrapper, or convenience APIs around adopted Telegram parity classes unless upstream lacks the capability and a real current caller requires the addition.

#### Scenario: Convenience API is proposed for a parity class
- **WHEN** a new helper, wrapper, renamed method, or extra convenience entry point is proposed around an adopted Telegram parity class
- **THEN** the change MUST be rejected unless the artifact records why upstream is insufficient and why the new API is project-specific rather than parity

#### Scenario: No real caller exists
- **WHEN** a proposed helper or support file has no current caller in the active phase
- **THEN** the file MUST NOT be added preemptively

### Requirement: Existing parity files must receive audit disposition
Already-landed Telegram-related files in the rewrite SHALL receive a parity audit result before the phase that depends on them can close.

#### Scenario: Existing file is reviewed during phase 0
- **WHEN** an already-landed 1.x, 2.x, or 3.x file is audited
- **THEN** the audit output MUST record its classification, parity status, and disposition as one of: keep and tighten, rework, delete, or reclassify

#### Scenario: Phase depends on unaudited existing file
- **WHEN** a later phase depends on a Telegram-related file that still lacks audit disposition
- **THEN** that later phase MUST remain blocked until the audit result exists
