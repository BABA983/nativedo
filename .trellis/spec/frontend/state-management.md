# State Management

> How state is managed in this project.

---

## Overview

The main pattern is `ViewModel` + `MutableStateFlow` / `StateFlow` for screen state, with Compose local state reserved for ephemeral UI details. Shared long-lived state and persistence-backed stores are created in `AppContainer`.

There is no Redux-style global store and no dedicated server-state library.

---

## State Categories

### Screen state

- Owned by a `ViewModel`
- Exposed as immutable `StateFlow<UiState>`
- Updated with `copy(...)` on a data-class snapshot

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt`

### Local ephemeral UI state

- Use `remember` / `rememberSaveable` inside composables
- Good for flags like expanded menus, active search mode, handled tokens, or scroll-derived presentation state

Example:

- `isSearchMode`, `searchOpenedFromHeader`, and `contextualOptionsExpanded` in `feature/home/ui/HomeRoute.kt`

### Shared app/session state

- Lives in long-lived coordinators/stores created by `AppContainer`
- Exposed via flow-backed state holders or plain store APIs

Examples:

- `sessionCoordinator` in `core/di/AppContainer.kt`
- `AppBrowserPreferenceStore` and `SessionStore` in `core/storage/`

### Persisted state

- Stored with SharedPreferences-backed persistence objects that serialize JSON snapshots
- There is no Room database layer at the moment

Examples:

- `core/storage/SessionStore.kt`
- `core/storage/AppBrowserPreferenceStore.kt`

---

## When to Use Global State

Promote state out of a composable when at least one of these is true:

- multiple screens consume it
- it must survive process / activity recreation via persistence
- it represents session or app-wide state
- it is business logic rather than local interaction state

If the state is only needed to drive one screen's immediate presentation, keep it local.

---

## Server State

- Server reads are performed through repositories / use-cases backed by `DiscourseApiClient`.
- ViewModels own loading, refreshing, and pagination.
- The common pattern is storing loading flags, items, paging cursors, and error messages in a `UiState` snapshot.
- Stale-response protection is sometimes handled explicitly with auth/session version checks.

Examples:

- `feature/home/ui/HomeViewModel.kt` uses `currentSessionVersion()` to ignore stale results after auth changes.
- `feature/home/domain/LoadHomeFeedUseCase.kt` and feature repositories provide the data boundary.

---

## Common Mistakes

- Do not move every boolean into a `ViewModel`; local UI-only toggles belong in Compose state.
- Do not keep shared business state in composables when `AppContainer` or a `ViewModel` should own it.
- Do not introduce a global state library without a concrete problem that current `ViewModel`/store patterns cannot solve.
- Do not assume persistence implies a database; in this repo persistence is mostly SharedPreferences-backed JSON snapshots.