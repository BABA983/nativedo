## ADDED Requirements

### Requirement: Telegram rewrite work must be delivered through phase-scoped units
The Telegram parity rewrite SHALL be executed through phase-scoped change units instead of a single monolithic execution backlog.

#### Scenario: New rewrite work is planned
- **WHEN** additional Telegram parity implementation work is proposed after this change
- **THEN** the work MUST be assigned to a defined phase unit with explicit scope and verification gates

#### Scenario: Monolithic backlog remains as reference
- **WHEN** `full-rewrite-telegram-parity` is retained after phase planning
- **THEN** it MUST serve only as umbrella reference and not as the sole execution checklist for new implementation

### Requirement: Phase order must follow dependency boundaries
The rewrite SHALL respect dependency order so that foundational Telegram substrate work completes before dependent navigation, rendering, runtime, or feature work proceeds.

#### Scenario: Navigation work is proposed
- **WHEN** a change includes `ActionBar`, `BaseFragment`, or `ActionBarLayout` parity work
- **THEN** the related substrate utilities, threading primitives, theme primitives, and audit prerequisites MUST already be defined in an earlier phase

#### Scenario: Feature page work is proposed
- **WHEN** a change includes Topics, TopicDetail, Browser, Profile, Search, or Login scene implementation
- **THEN** the required substrate, navigation, rendering, and runtime phases MUST already be established as preceding dependencies

### Requirement: Each phase must declare scope and exit gates
Every phase unit MUST define its file scope, dependency boundary, verification method, and exit criteria before implementation is treated as complete.

#### Scenario: Phase artifact is created
- **WHEN** a new phase unit is documented
- **THEN** it MUST list the owned files or subsystems, prerequisites, required verification, and conditions for exit

#### Scenario: Phase is marked complete
- **WHEN** a phase is proposed as complete
- **THEN** the recorded verification and exit criteria MUST have been satisfied for that phase

### Requirement: Phase 0 must audit existing parity work before forward progress
The rewrite SHALL complete a governance and audit phase before dependent phases rely on existing Telegram-related files as trusted parity substrate.

#### Scenario: Existing 1.x, 2.x, or 3.x files are present
- **WHEN** phase planning begins
- **THEN** the first phase MUST map those files to classification, provenance status, parity status, and disposition

#### Scenario: Later phase needs an audited file
- **WHEN** a later phase depends on a file that was already implemented before phase split
- **THEN** the later phase MUST consume the phase 0 audit result rather than assuming the file is already parity-complete

### Requirement: Verification style must match phase risk
Each phase SHALL use verification appropriate to its responsibilities instead of applying one uniform test style to all rewrite work.

#### Scenario: UI substrate phase is verified
- **WHEN** a phase focuses on gesture, animation, layout, or drawing substrate
- **THEN** the phase MUST require build validation and host-level smoke verification instead of standalone unit tests by default

#### Scenario: Contract-heavy phase is verified
- **WHEN** a phase focuses on HTML parsing, session restoration, network contracts, or other logic with strong data contracts
- **THEN** the phase MUST require contract or integration-style tests for those behaviors
