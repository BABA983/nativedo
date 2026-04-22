## ADDED Requirements

### Requirement: Telegram parity for adopted utility APIs
For utility classes and methods intentionally introduced with the same public name as Telegram substrate APIs, the system SHALL preserve the corresponding Telegram public behavior contract by default, even when only a minimal subset is implemented. The system SHALL not add convenience side effects that change the upstream semantics unless the deviation is explicitly documented in the change artifacts before implementation.

#### Scenario: Minimal subset keeps upstream semantics
- **WHEN** the rewrite adopts a Telegram-named helper such as `showKeyboard()`, `hideKeyboard()`, `DispatchQueue`, or `LayoutHelper`
- **THEN** the implemented subset keeps Telegram-compatible method semantics for the adopted APIs rather than redefining them as project-local convenience wrappers

#### Scenario: Intentional deviation is documented first
- **WHEN** an adopted Telegram-named API must diverge because of project constraints or removed Telegram-only dependencies
- **THEN** the deviation and rationale are written into the change artifacts before code using that API is merged

### Requirement: Telegram-derived utility files carry explicit GPL provenance
For utility files classified in the change artifacts as `Telegram-derived`, each file SHALL carry the repository's Telegram-derived GPL v2 header template with the upstream class/path, source reference, Telegram + NativeDo copyright notices, and SPDX line. Files that are only `Telegram-inspired project-specific` SHALL NOT claim derivation unless code was actually translated or excerpted from Telegram source.

#### Scenario: Direct parity port includes derived header
- **WHEN** a file implements a parity-first port of a Telegram utility class such as `AndroidUtilities`, `DispatchQueue`, `FileLog`, `LayoutHelper`, `CubicBezierInterpolator`, `AnimatedFloat`, or `AnimatedColor`
- **THEN** that file includes the Telegram-derived GPL header with upstream provenance details before the implementation is considered complete

#### Scenario: Project-specific host utility does not overclaim derivation
- **WHEN** a file is newly written for NativeDo-specific host/integration concerns while only borrowing Telegram's architectural boundaries
- **THEN** it is documented as `Telegram-inspired project-specific` and does not use the Telegram-derived file header by default

### Requirement: No speculative helper surface
The system SHALL NOT add new `core/util` helper classes or wrapper APIs unless they either directly port an adopted Telegram source API or have an immediate in-tree consumer in the same phase. Helper APIs introduced only to satisfy checklist completeness SHALL be deferred until the first real caller exists.

#### Scenario: Helper has no Telegram source and no caller
- **WHEN** a proposed utility class has no Telegram public counterpart and no current screen or infrastructure consumer
- **THEN** the helper is not introduced yet as a standalone file

### Requirement: Adopted subset boundary is explicit
When a Telegram utility class is only partially adopted in the current phase, the change artifacts SHALL explicitly list the adopted public subset or state that full upstream public surface is expected. Unlisted members remain deferred until a real caller exists and SHALL NOT be silently redefined as project-local convenience APIs.

#### Scenario: Explicit current-phase subset for LayoutHelper
- **WHEN** the current phase adopts only part of Telegram `LayoutHelper`
- **THEN** the artifacts explicitly list the adopted subset (for example `MATCH_PARENT`, `WRAP_CONTENT`, and the `createFrame(...)`, `createLinear(...)`, `createScroll(...)` families), while helpers such as `createRelative()` or `measureSpecExactly*()` remain deferred until a real caller appears

#### Scenario: Small helper omits a public member
- **WHEN** a Telegram helper class with a small public surface omits a constructor or method in the current phase
- **THEN** the omission is called out in the change artifacts as an excluded member rather than left ambiguous

### Requirement: Java-to-Kotlin adaptation stays minimal and documented
When porting Telegram Java source to Kotlin, the implementation SHALL preserve the Telegram source's declared return types, overload structure, and semantic intent. Kotlin language-level adaptations are permitted only when they are mechanically necessary (e.g., `object` instead of `static`, `apply {}` instead of sequential setters) and SHALL NOT change the caller-observable contract. Adaptations that go beyond syntax-level translation SHALL be classified and documented in change artifacts before merging.

#### Adaptation categories

| Category | Permitted without documentation | Examples |
|---|---|---|
| **Syntax-level** | Yes | `object` singleton, `apply {}`, `val`/`var`, expression body, named/default params replacing overload fan-out |
| **Type-level** | Yes, if Kotlin resolves the same SDK type | inherited Android layout params that remain the same concrete SDK class at call sites |
| **Overload-collapse** | Yes, with care | Kotlin default params may replace Java overload fans, but the resulting function must accept all original call-site patterns; if a Java overload has different **parameter types** (e.g., `int` margins vs `float` margins), it SHALL remain a separate function rather than be merged via defaults |
| **Platform-level** | Requires documentation | Telegram API depends on a removed dependency (`LocaleController`, `AccountInstance`, native JNI) — document what was removed and how the gap is handled |
| **Semantic-level** | **Prohibited** unless documented first | Changing return type, adding side effects, redefining parameter meaning, merging two methods with different behavior into one |

#### Scenario: Platform-equivalent return type is documented
- **WHEN** Telegram declares `createScroll(...)` returning `ScrollView.LayoutParams`, but Android SDK exposes only `FrameLayout.LayoutParams`
- **THEN** the Kotlin port returns `FrameLayout.LayoutParams` while preserving Telegram-compatible gravity and margin semantics, and that platform-level deviation is documented in change artifacts before merge

#### Scenario: Overload fan with different param types stays separate
- **WHEN** Telegram has `createLinear(int, int, int gravity, int lm, int tm, int rm, int bm)` and `createLinear(int, int, int gravity, float lm, float tm, float rm, float bm)` as separate overloads
- **THEN** these remain separate functions in Kotlin (e.g., with distinct names or parameter types) rather than being collapsed into a single function with default params, because the `int` variant passes raw dp values while the `float` variant applies `AndroidUtilities.dp()` conversion

#### Scenario: Kotlin default params replace simple overload fan
- **WHEN** Telegram has `createFrame(int, int, int)` and `createFrame(int, float, int, float, float, float, float)` where the shorter form is the longer form with zero margins
- **THEN** the Kotlin port MAY use default parameter values to collapse these, provided all original Java call-site patterns still compile with identical behavior

### Requirement: UI-thread scheduling stays on AndroidUtilities
For the adopted Telegram utility subset in this change, the UI-thread scheduling entry points `runOnUIThread()` and `cancelRunOnUIThread()` SHALL remain part of `AndroidUtilities`, matching Telegram's public contract. `Utilities` SHALL stay limited to math/general non-UI-thread helpers unless a deviation is documented first.

#### Scenario: Main-thread task scheduling
- **WHEN** infrastructure code needs to post or cancel work on the main thread
- **THEN** it uses `AndroidUtilities.runOnUIThread()` / `AndroidUtilities.cancelRunOnUIThread()` rather than redefining those entry points on `Utilities`

### Requirement: LayoutHelper keeps Telegram layout param semantics
The system SHALL implement `LayoutHelper` as a parity-first subset port of Telegram `org.telegram.ui.Components.LayoutHelper`, preserving the imported method names and layout param semantics for the adopted subset, while allowing documented Android SDK-equivalent return types where Telegram's declared nested type is not exposed.

#### Scenario: Scroll params preserve Telegram semantics
- **WHEN** `LayoutHelper.createScroll(...)` is used in the adopted subset
- **THEN** it returns the Android SDK-equivalent `FrameLayout.LayoutParams` with Telegram-compatible gravity and margin semantics rather than a project-local substitute type

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
