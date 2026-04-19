## Context

NativeDo browser already has address editing as part of the browser chrome state machine, with an input, page preview, copy-link action, bookmark card, and recent link entries. However, the address surface is still thin compared with Telegram’s browser: there is no mixed suggestion model, no recent-search history, and no cohesive suggestion semantics spanning links, bookmarks, searches, and quick actions. The current implementation also leans on simple `recentEntries` state, which is enough for a placeholder address scene but not for a mature browser address surface.

At the same time, the browser feature already has several foundations worth preserving:
- `AppBrowserChromeScene` already models address editing as an explicit mutually exclusive scene.
- History and bookmark stores already exist and are wired through `AppContainer`.
- Browser chrome behavior is feature-owned instead of scattered through root top-bar slots.

That makes this change a browser-surface refinement rather than a container or routing overhaul.

## Goals / Non-Goals

**Goals:**
- Introduce a typed address-suggestion model that can combine recent links, recent searches, bookmarks, and quick actions.
- Add lightweight recent-search persistence for the browser address surface.
- Upgrade address editing so selecting a suggestion preserves suggestion-specific semantics.
- Keep suggestion derivation explicit and testable instead of letting Compose-local state grow ad hoc.

**Non-Goals:**
- Do not recreate Telegram’s exact `AddressBarList` implementation or Java class structure.
- Do not change full-route versus sheet browser container behavior.
- Do not introduce remote suggestions, autocomplete from a server, or cross-device sync.
- Do not redesign the rest of browser chrome scenes outside address editing.

## Decisions

### 1. Address suggestions will use an explicit typed model instead of reusing raw history records
The browser feature will introduce typed suggestion and section models such as recent link, recent search, bookmark, and quick action. The address scene will render these models directly instead of treating everything as a flattened history list.

Why:
- Different suggestion types have different behavior when selected.
- Typed models keep the UI logic readable as the address surface becomes richer.

Alternative considered:
- Continue reusing `AppBrowserHistoryRecord` for all address suggestions.
  - Rejected because it collapses searches, bookmarks, and quick actions into the wrong semantics.

### 2. Recent-search persistence will be a lightweight browser-specific store following the same persistence pattern as bookmarks and history
A small recent-search store will be added through `core/storage` and wired in `AppContainer`. It will deduplicate and cap saved search queries, but remain intentionally simpler than browser history.

Why:
- Search history is separate from visited-page history and should not be inferred indirectly from URL visits.
- The repository already has a clear persistence pattern for browser-local stores.

Alternative considered:
- Infer recent searches from browser history URLs.
  - Rejected because it is brittle and ties search UX to whichever search engine URL happened to be loaded.

### 3. Suggestion derivation will be extracted into a pure feature-layer resolver rather than being composed ad hoc inside composables
The combined address surface will be derived by a pure resolver that takes the current draft, page context, bookmarks, recent links, and recent searches and returns sectioned address-suggestion models. The route or feature layer can invoke this resolver and map the result into browser UI state.

Why:
- The logic is non-trivial and should stay testable.
- This follows the project guidance to keep complex UI-state production out of composables.

Alternative considered:
- Build suggestion lists directly inside `AppBrowserAddressScene` with `remember` blocks.
  - Rejected because it would spread browser address logic across UI-only state and make ranking or filtering behavior harder to verify.

### 4. Address selection behavior will preserve suggestion-specific semantics
Selecting a recent link or bookmark will load that URL directly. Selecting a recent search will submit the saved query through the current browser address resolution path. Selecting a quick action will perform the matching browser action without pretending it is a URL.

Why:
- A richer address surface is only useful if suggestions behave according to their source.
- This is one of the biggest practical differences between a mature address surface and a raw text field.

Alternative considered:
- Convert every suggestion into a string and submit it through the same address field path.
  - Rejected because it would make search-history and quick-action behavior ambiguous.

### 5. The browser-scene-chrome spec will be extended so address editing explicitly includes a richer address surface
The existing browser chrome spec already owns the address-editing scene. This change will extend that contract so address editing is not just “show an input field,” but “show a browser address surface with suggestions and quick actions while suspending incompatible chrome states.”

Why:
- The richer address surface is part of browser chrome behavior, not an unrelated side panel.
- Updating the existing browser chrome contract keeps the scene model coherent.

Alternative considered:
- Treat address suggestions as a completely isolated capability with no browser-scene-chrome change.
  - Rejected because it would under-specify how address editing fits into the existing scene state machine.

## Risks / Trade-offs

- [Suggestion ranking could become noisy if all sources are shown equally] → Keep section ordering explicit and cap list sizes for each source.
- [Recent-search persistence could accumulate low-value entries] → Deduplicate normalized queries and enforce a small maximum record count.
- [Route-owned composition plus ViewModel-owned scene state can become confusing] → Keep ownership explicit: ViewModel owns the scene and draft, pure resolver owns suggestion derivation, composables only render the resulting model.
- [A richer address surface may feel visually heavy] → Preserve the current NativeDo chrome identity and limit the first version to the highest-value sections and quick actions.

## Migration Plan

1. Add recent-search persistence and typed suggestion models.
2. Extract a pure suggestion resolver that merges draft, current page context, history, bookmarks, and recent searches.
3. Expand browser UI state or address-surface mapping so the route can feed resolved suggestion sections into the address scene.
4. Update `AppBrowserChrome.kt` to render the richer surface and handle typed selection actions.
5. Add tests for suggestion derivation and recent-search persistence, then manually smoke test address editing and selection flows.

## Open Questions

- How many suggestion sections should be visible at once before the address surface becomes too dense?
- Should recent searches appear only when the draft is non-empty, or also as a default empty-state section?
- Do we want bookmark quick actions beyond open and toggle in the first version, or keep the initial quick-action set minimal?
