## Why

NativeDo currently has two competing ownership models for search:

- `HomeRoute` already contains a home-local search mode.
- `NativeDoNavHost` still exposes a standalone `RootDestination.Search` route.

That split makes the shell boundary unclear. In practice, home search currently bypasses the same scaffold and top-bar contract used by the normal home scene, which can surface as status-bar overlap and inconsistent chrome ownership. The bottom tab bar also has no clear contract for what should happen while home search is active.

Telegram's home search is not a pushed page. It stays inside the home scene (`DialogsActivity`) and swaps the content area into a search-specific scene while the same container remains in charge of header, tabs visibility, and scene transitions.

## What Changes

- Realign home search so it becomes a scene-local mode owned by `HomeRoute` instead of depending on a standalone root search route.
- Keep home search inside the same `NativeDoReaderScaffold` and top-bar ownership contract as the normal home scene.
- Split reusable search-results content from the current standalone search screen so home search can own its own search header while reusing the results body.
- Let the root shell react explicitly to home-search activity so the root bottom tabs can hide while home search is active.
- Mark the standalone `RootDestination.Search` route as transitional after the home-search migration instead of treating it as the long-term primary home-search surface.
- Record the future direction that login should eventually align with the browser family's auth-sensitive full-route container, without making that browser migration part of this change's implementation scope.

## Capabilities

### New Capabilities

- `home-root-search`: Defines home search as a root-scene-local mode with stable scaffold ownership and shell coordination.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModel.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/RootDestination.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/RootTabsHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoRootChrome.kt`
- Affected systems:
  - Home root-scene search ownership
  - Root shell tab-bar coordination during search
  - Search header/results composition boundaries
  - Future route-boundary cleanup for standalone search
