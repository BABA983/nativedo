# NativeDo Agent Guide

## Project reference

- `/Users/baba/Documents/workspace_github/Telegram` — primary reference for UI components, animations, gesture interactions (swipe, long-press, pull-to-refresh), navigation transitions, and list performance patterns.
- `/Users/baba/Documents/workspace_github/stripe-android` — modern Android SDK reference for architecture and code patterns.
- `/Users/baba/Documents/workspace_github/fluxdo` — reference for linux.do-specific Discourse features and flows.
- <https://docs.discourse.org/> — canonical Discourse API documentation.

## Project Scope

- This repository builds a native client for `https://linux.do/`, not a generic Discourse SDK.
- Android is the active implementation target under `apps/android`; `apps/ios` is reserved for future parity work.
- `v1` should prioritize reading, search, profile, login, and session recovery before expanding into posting or other write-heavy flows.
- Do not add AI-assisted posting, replying, or other content generation features unless the user explicitly asks for them.

## Source Of Truth

- Start with `README.md` for the current project layout and local run commands.
- Treat `docs/superpowers/specs/2026-03-31-linuxdo-native-client-design.md` as the main product and architecture brief.
- Treat `docs/superpowers/specs/2026-04-01-nativedo-liquid-glass-theme-design.md` as the main UI/theme direction.
- Treat `contracts/discourse/*.md` as the canonical reference for endpoints, models, and auth/session flow.
- Treat `fixtures/discourse/*` as the sample payloads that should stay aligned with the contracts.

## Android Architecture

- Keep the Android implementation in a single app module unless the user explicitly asks for module splitting.
- Maintain the existing package boundaries: `core/`, `feature/`, `navigation/`, and `ui/theme`.
- Prefer manual dependency wiring through `AppContainer`; do not introduce Hilt, Koin, or another DI framework without a clear user request or a concrete scaling problem.
- Prefer `ViewModel + StateFlow<UiState>` for screen state and keep UI state production out of composables when the logic is non-trivial.
- Use explicit names such as `Repository`, `UseCase`, `Store`, `Coordinator`, and `Provider`; avoid catch-all names like `Manager` when a narrower term exists.
- When adding a new feature, keep obvious one-screen factories local, but move long-lived dependencies into `AppContainer` once they are shared across screens.

## Network And Auth

- `linux.do` is a Discourse site. Prefer standard Discourse JSON endpoints and documented session flows over ad-hoc scraping.
- Login is WebView-backed and cookie-based. Do not introduce native username/password handling unless the user explicitly asks for a different auth strategy.
- Session-sensitive changes must account for `_t`, `_forum_session`, `cf_clearance`, bootstrap HTML parsing, and `/session/csrf`.
- If you change auth, cookies, CSRF, or endpoint semantics, update the matching files in `contracts/discourse/` and `fixtures/discourse/` in the same task when appropriate.
- Cloudflare handling should favor a reliable WebView fallback over brittle native workarounds.

## UI And Theme

- Keep using `NativeDoTheme`, `NativeDoThemeExtras`, and the existing semantic color model instead of scattering raw colors through features.
- Avoid Material 3 component styles (Button, Card, TopAppBar, etc.); build custom composables whose look, animation, and interaction patterns reference the Telegram Android codebase (`/Users/baba/Documents/workspace_github/Telegram`). Material icons may be used as placeholders.
- Preserve support for `System`, `Light`, and `Dark` theme modes unless the user asks to simplify them.
- When editing Compose UI, preserve the current design direction: glass surfaces are reserved for chrome elements (tab bars, navigation bars, overlays); content areas and list items use standard opaque styling.

## Build, Lint, And Test

- Do not run gradlew commands in parallel; add `--quiet` when possible.
- **All unit tests:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- **Single test class:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest' --quiet`
- **Assemble (runtime/resource changes):** `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- For docs-only changes, verification can be limited to checking the edited Markdown for accuracy and consistency.

## Code Style

- Kotlin, JVM 17, compileSdk 35, minSdk 28. Compose UI (custom components, not M3 widgets).
- Trailing commas on multi-line parameter/argument lists. No wildcard imports.
- Sealed interfaces for UI state variants (`sealed interface HomeUiState`); data classes for state snapshots.
- Networking: Retrofit + OkHttp + kotlinx.serialization (not Gson/Moshi). Images: Coil 3. HTML parsing: Jsoup.
- Test deps: JUnit 4, Truth assertions, MockWebServer, Turbine for Flow, Robolectric for Android framework.
