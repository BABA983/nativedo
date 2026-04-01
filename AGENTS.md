# NativeDo Agent Guide

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
- Telegram is a visual reference, not a requirement to port Telegram internals. Prefer borrowing chrome hierarchy, spacing, and glass semantics over copying complex blur infrastructure.
- Preserve support for `System`, `Light`, and `Dark` theme modes unless the user asks to simplify them.
- When editing Compose UI, preserve the current design direction: layered backgrounds, elevated glass surfaces, and a distinct non-default visual identity.

## Verification

- For Android code changes, run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`.
- For changes that could affect runtime behavior or resources, also run `./apps/android/gradlew -p apps/android :app:assembleDebug`.
- For docs-only changes, verification can be limited to checking the edited Markdown for accuracy and consistency.
