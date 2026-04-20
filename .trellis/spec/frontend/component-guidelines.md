# Component Guidelines

> How components are built in this project.

---

## Overview

UI is built with Jetpack Compose. Route-level composables handle screen wiring, state collection, and navigation callbacks, while reusable presentation pieces live in `core/ui` or shared `ui/` packages.

Although `androidx.compose.material3` is present, this codebase does not follow stock Material 3 visuals. The design direction is custom chrome and motion built on top of Compose primitives and `NativeDoTheme` tokens.

---

## Component Structure

Typical screen files follow this shape:

1. route entry composable (`HomeRoute`)
2. dependency lookup / `viewModel(...)` factory wiring
3. `collectAsStateWithLifecycle()` for screen state
4. local ephemeral UI state with `remember` / `rememberSaveable`
5. helper composables or small internal state types in the same file when they are screen-specific

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/RequestErrorCard.kt`

---

## Props Conventions

- Prefer explicit parameters over opaque config objects when a composable has a small, clear API.
- Pass callbacks down as lambdas (`onTopicClick`, `onUserClick`, `onRetry`).
- Keep long-lived business state in a `ViewModel`; use composable parameters for derived presentation inputs.
- Default parameters are common for optional behavior and `Modifier` should remain optional and last.

Examples:

- `HomeRoute(onTopicClick, onUserClick, refreshRequestToken, themeMode, onThemeModeSelected, onSearchModeChanged, modifier)` in `feature/home/ui/HomeRoute.kt`.
- `RequestErrorCard(presentation, onRetry, modifier, compact)` in `core/ui/RequestErrorCard.kt`.

---

## Styling Patterns

- Use `NativeDoTheme`, `NativeDoThemeExtras`, and shared theme shapes such as `NativeDoCardShape` and `NativeDoPillShape`.
- Prefer custom surfaces, borders, and animated chrome over default Material 3 component styling.
- Glass / translucent styling is reserved for chrome and overlays; content cards and list items remain mostly opaque.
- Keep reusable visual tokens in `ui/theme/` instead of scattering raw colors or shapes through features.

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/RequestErrorCard.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/`

---

## Accessibility

There is no large accessibility helper layer yet, so accessibility is mostly handled through standard Compose semantics:

- keep visible text clear and action-oriented
- prefer standard Compose components when they provide built-in semantics
- avoid icon-only actions unless the surrounding context is obvious
- preserve readable contrast by using semantic theme colors rather than ad-hoc alpha stacks

---

## Common Mistakes

- Do not move screen business logic into composables when it belongs in a `ViewModel`.
- Do not introduce stock Material 3 visuals just because a component exists in the library.
- Do not duplicate shared chrome components inside a feature if an equivalent belongs in `core/ui` or `ui/`.
- Do not scatter raw colors, easing curves, or shape literals through feature code.