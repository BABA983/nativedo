## Context

The current Android shell already moved toward explicit container ownership:

- `RootTabsHost` owns root scenes.
- `NativePageStackHost` owns pushed native pages.
- `BrowserHost` owns browser-family presentation.

Home search is the remaining boundary that still feels split-brain. The codebase already has a home-local search mode inside `HomeRoute`, but it also still exposes a standalone `RootDestination.Search` route in `NativeDoNavHost`. At the same time, the home-local search branch does not stay inside the same scaffold and top-bar contract as the normal home scene, which makes search-mode layout easier to break and is consistent with the reported status-bar overlap bug.

Telegram's home search is useful here as an ownership reference, not an implementation template. In Telegram, the home scene keeps the same container and switches its content area into a search-specific scene. It does not push a separate page to search home content.

## Goals / Non-Goals

**Goals:**

- Make home search an internal scene mode of `HomeRoute`.
- Keep home search inside the same scaffold and top-inset contract as the normal home scene.
- Separate search header ownership from reusable search-results content.
- Give the root shell an explicit signal for whether home search is active so bottom tabs can respond cleanly.
- Record the route-boundary direction that standalone home search should become transitional rather than permanent.

**Non-Goals:**

- Do not implement the browser-family login migration in this change.
- Do not recreate Telegram's Java `SearchViewPager` class structure or every search tab variant in one step.
- Do not redesign topic/user result cells beyond what is necessary to fit the new ownership model.
- Do not solve every global-search use case; this change is specifically about home search ownership.

## Decisions

### 1. Home search becomes a `HomeRoute` scene mode instead of a standalone root route

`HomeRoute` should own a local scene mode that switches between:

- `Feed`
- `Search`

The root shell should treat home search as a state inside the home root scene, not as another page in `NavHost`.

Why:

- This matches the Telegram ownership model more closely.
- It removes the split between "home search as an internal mode" and "home search as a standalone route."
- It keeps the normal home scene mounted and available for search-mode transitions.

Alternative considered:

- Keep the standalone root search route as the primary home-search path and only patch the current overlap bug.
  - Rejected because it preserves the ownership split that caused the bug in the first place.

### 2. Home search must stay inside the same scaffold contract as normal home content

The outer structure should remain:

```text
HomeRoute
└─ NativeDoReaderScaffold
   ├─ topBar
   └─ content
```

Search mode should not bypass that scaffold. Instead, the scaffold should stay mounted while the home body switches from feed content to search content.

Why:

- The scaffold already owns top-bar placement and content padding.
- Keeping search inside that boundary removes the current top-inset ambiguity and avoids status-bar overlap.
- It makes home search transitions scene-local instead of route-level.

Alternative considered:

- Keep search as a separate full-screen branch that manually recreates spacing and header rules.
  - Rejected because it duplicates layout ownership and makes chrome regressions more likely.

### 3. Home search header and search results body should be split into separate responsibilities

The current `SearchScreen` mixes:

- search header/input ownership
- search-results content ownership

For home search, those concerns should be split:

- `HomeSearchTopBar` or equivalent local search-header composable stays in the scaffold `topBar` slot.
- `SearchResultsContent` or equivalent reusable results-body composable stays in the scaffold `content` slot.

Why:

- The home scene needs local ownership of its search header.
- The results body can still be reused without forcing home search to reuse a standalone search page structure.
- This lets the search header participate in home-scene chrome transitions cleanly.

Alternative considered:

- Reuse `SearchScreen` unchanged inside `HomeRoute`.
  - Rejected because it keeps header ownership inside a body composable and recreates the same layout-boundary problem.

### 4. The root shell should hide bottom tabs while home search is active

When home search is active, the root bottom tab bar should hide. The shell should not infer this indirectly from routes, because home search will no longer be a standalone route. Instead, `HomeRoute` should publish whether its local search mode is active, and the shell should react to that state.

Why:

- Telegram hides main tabs while home search is active.
- Once home search is scene-local, route-based logic can no longer determine whether tabs should be visible.
- An explicit upward signal keeps ownership clear.

Alternative considered:

- Keep bottom tabs visible during home search.
  - Rejected because it weakens focus and diverges from the intended Telegram-like search behavior.

### 5. `RootDestination.Search` becomes transitional after the home-search migration

The standalone `RootDestination.Search` route should no longer be treated as the canonical home-search surface. After the home migration lands, that route should either:

- be removed, or
- be repurposed for a different standalone search experience

This change records that direction, but does not force the cleanup sequence to happen in the same commit as the first ownership fix.

Why:

- It avoids bundling route cleanup with the first bug-fix batch.
- It keeps the migration incremental while still making the long-term direction explicit.

Alternative considered:

- Keep the standalone search route indefinitely alongside home-local search.
  - Rejected because it leaves two overlapping ownership models in place.

### 6. Login should later align with browser-family full-route ownership, but not in this change

The exploration also clarified that login behaves more like an auth-sensitive WebView flow than a native pushed page. That means its long-term home is the browser family's full-route container rather than a standalone global route. This direction should be recorded here as follow-up context, but not folded into this change's implementation scope.

Why:

- It keeps the current change focused on home search.
- It records a shell-boundary decision discovered during exploration so later browser/auth work does not need to rediscover it.

## Architecture Sketch

```text
AppRoot
└─ RootTabsHost
   └─ HomeRoute
      └─ NativeDoReaderScaffold
         ├─ HomeTopBar / HomeSearchTopBar
         └─ HomeSceneHost
            ├─ HomeFeedScene
            └─ HomeSearchScene
               └─ SearchResultsContent
```

The important ownership boundary is that `NativeDoReaderScaffold` stays mounted in both home modes.

## Risks / Trade-offs

- [Risk] Splitting `SearchScreen` may temporarily duplicate some UI code.
  - Mitigation: Keep the first split minimal and reuse the existing search result list components wherever possible.

- [Risk] Bottom-tab visibility could drift if the shell signal is not wired carefully.
  - Mitigation: Make home-search activity an explicit upward callback or state publication instead of route-derived inference.

- [Risk] Leaving `RootDestination.Search` in place for one migration phase may confuse future readers.
  - Mitigation: Document it as transitional in proposal, design, and tasks, then clean it up in a dedicated follow-up step.

- [Risk] Pulling login into the same change would blur scope.
  - Mitigation: Record login's future browser-family ownership as context only and keep it out of the implementation tasks.

## Migration Plan

1. Keep `NativeDoReaderScaffold` mounted for both home feed and home search.
2. Introduce a home-local search scene and move the current search-mode branch into the scaffold content area.
3. Split reusable search-results content from the current standalone search page structure.
4. Publish home-search active state upward so the root shell can hide bottom tabs.
5. After the home-local search path is stable, clean up or repurpose `RootDestination.Search`.

## Implementation Notes

- `RootDestination.Search` stays in place as a transitional standalone search route for now, but it now owns its own search header inside `NativeDoReaderScaffold` instead of relying on app-root chrome ownership.
- The primary home search path now stays inside `HomeRoute`, and the root shell only reacts to the explicit home-search active signal.
- The login route remains unchanged in this change; its move toward browser-family full-route ownership should happen in a dedicated follow-up change.

## Open Questions

- Should the first pass of home search remain a mixed results list, or should it immediately introduce tabs or pager structure similar to Telegram's search surfaces?
- When we revisit standalone search, should `RootDestination.Search` be deleted outright or repurposed for a non-home global-search entry point?
- What exact follow-up change should own the eventual login-to-browser migration: a focused auth/browser change or a broader global-route cleanup?
