# Directory Structure

> How frontend code is organized in this project.

---

## Overview

This repository's active implementation is an Android app built with Kotlin and Jetpack Compose under `apps/android/app`. Frontend code is organized by stable top-level areas (`core`, `feature`, `navigation`, `ui/theme`) rather than by technical layers only.

There is no separate web frontend in this repo, and `apps/ios` is currently a placeholder rather than an active parity target.

---

## Directory Layout

```text
apps/android/app/src/main/java/io/github/baba983/nativedo/
├── core/
│   ├── di/
│   ├── log/
│   ├── model/
│   ├── network/
│   ├── session/
│   ├── storage/
│   └── ui/
├── feature/
│   ├── browser/
│   ├── home/
│   ├── search/
│   └── topic/
├── navigation/
├── ui/
└── ui/theme/
```

---

## Module Organization

- Keep app-wide infrastructure in `core/`.
- Keep user-facing product areas in `feature/<name>/`.
- Within a feature, prefer `ui/`, `data/`, and `domain/` packages when all three are justified by real code.
- Put navigation graph wiring in `navigation/`, not inside individual screens.
- Put app-wide chrome and theme primitives in `ui/` and `ui/theme/`, not inside a feature package.
- Keep dependency wiring in `core/di/AppContainer.kt` instead of introducing a DI framework.

---

## Naming Conventions

- Packages use lowercase nouns: `feature.home.ui`, `core.network`, `ui.theme`.
- Screen entry composables are usually named `*Route`, for example `HomeRoute`.
- View-model files are named `*ViewModel.kt`; state snapshots are usually `*UiState.kt`.
- Repository/use-case/store names are explicit (`HomeRepository`, `LoadHomeFeedUseCase`, `SessionStore`); avoid catch-all `Manager` names when a narrower term exists.

---

## Examples

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt` - central dependency wiring and long-lived stores.
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt` - feature-local UI wiring and route entry point.
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt` - feature data access layer.
- `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt` - app navigation graph.

---

## Common Mistakes

- Do not add new top-level architectural buckets when the code naturally fits `core/`, `feature/`, `navigation/`, or `ui/`.
- Do not place long-lived shared dependencies inside composables; move them to `AppContainer` once shared.
- Do not treat `apps/ios` as a source of active implementation patterns yet.