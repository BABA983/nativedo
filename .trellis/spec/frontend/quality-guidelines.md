# Quality Guidelines

> Code quality standards for frontend development.

---

## Overview

This project favors explicit architecture, small focused state holders, and verification through JVM unit tests. The active frontend is the Android Compose app, so quality checks should match Kotlin/Compose workflows rather than web frontend conventions.

---

## Forbidden Patterns

- Do not introduce Hilt, Koin, or another DI framework without an explicit user request. `AppContainer` is the current dependency boundary.
- Do not introduce Room or SQL persistence by default; the repo currently uses SharedPreferences-backed stores.
- Do not scatter raw colors, shapes, or motion constants through feature code when theme tokens already exist.
- Do not parallelize Gradle commands in this repo.
- Do not use wildcard imports.

Evidence:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/SessionStore.kt`
- `AGENTS.md`

---

## Required Patterns

- Prefer `ViewModel + StateFlow<UiState>` for non-trivial screen logic.
- Use explicit names such as `Repository`, `UseCase`, `Store`, `Coordinator`, and `Provider`.
- Collect flows in Compose with `collectAsStateWithLifecycle()`.
- Use theme tokens and shared chrome primitives before adding new visual systems.
- Keep route-level screen wiring in `*Route` composables.

Examples:

- `feature/home/ui/HomeViewModel.kt`
- `feature/home/ui/HomeRoute.kt`
- `core/di/AppContainer.kt`

---

## Testing Requirements

- For behavior changes, add or update JVM unit tests first when practical.
- Prefer deterministic tests around `ViewModel`, network mapping, and state transitions.
- Use Truth assertions, coroutines-test, Turbine, MockWebServer, and Robolectric where appropriate.
- For docs-only changes, manual verification of Markdown accuracy is enough.
- Recommended commands are documented in `AGENTS.md`, especially `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`.

Examples:

- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt`
- `apps/android/app/build.gradle.kts`

---

## Code Review Checklist

- Does the change stay within existing package boundaries (`core`, `feature`, `navigation`, `ui/theme`)?
- Is non-trivial UI logic still owned by a `ViewModel` instead of a composable?
- Are theme tokens reused instead of new ad-hoc visuals?
- Are network / persistence changes aligned with current Retrofit + kotlinx.serialization + SharedPreferences patterns?
- Were relevant unit tests updated or added?
- If browser/auth/contracts changed, were related docs/contracts/fixtures updated too as required by `AGENTS.md`?