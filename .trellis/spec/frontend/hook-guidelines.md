# Hook Guidelines

> How hooks are used in this project.

---

## Overview

This repository does not use React hooks. The closest equivalents are Jetpack Compose state/effect primitives and lifecycle-aware state collection.

When adapting this template for NativeDo, think in terms of Compose idioms rather than inventing a custom hooks abstraction layer.

---

## Custom Hook Patterns

Closest equivalents used in this repo:

- `remember` for memoized objects tied to composition
- `rememberSaveable` for UI state that should survive recreation
- `LaunchedEffect` for side effects triggered by key changes
- `DisposableEffect` for lifecycle-bound setup/cleanup
- `rememberUpdatedState` to avoid stale captures in effects
- `derivedStateOf` for cheap derived UI state
- `BackHandler` for local back-press handling

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`

---

## Data Fetching

- Screen data fetching is usually initiated by the `ViewModel`, not directly inside composables.
- Composables observe `StateFlow` with `collectAsStateWithLifecycle()`.
- `LaunchedEffect` is used for UI-triggered refreshes, event collection, or one-off reactions to changing keys.
- There is no React Query / SWR equivalent in this codebase.

Examples:

- `feature/home/ui/HomeRoute.kt` collects `sessionState`, `authVersion`, `categories`, `uiState`, and search state with lifecycle awareness.
- `feature/home/ui/HomeViewModel.kt` performs loading and pagination in `viewModelScope`.

---

## Naming Conventions

- Compose helpers usually read like functions describing state or behavior (`buildHomeSearchChromeState`, `homeSearchCollapseProgress`).
- Remembered factories are named after the value they produce (`rememberSearchViewModel`).
- Avoid introducing `use*` naming because it implies React-style hooks that the rest of the codebase does not use.

---

## Common Mistakes

- Do not fetch server data directly from composables when a `ViewModel` should own it.
- Do not overuse `LaunchedEffect(Unit)` for business logic that belongs in initialization code or the `ViewModel`.
- Do not invent a cross-project "hook" abstraction unless there is repeated Compose logic in at least several screens.
- Do not use non-lifecycle-aware flow collection in screen composables when `collectAsStateWithLifecycle()` fits.