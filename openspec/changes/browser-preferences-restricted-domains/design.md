## Context

NativeDo browser currently persists history and bookmarks through browser-specific stores wired in `AppContainer`, but browser preferences stop at whatever the route hardcodes today. `LinkOpenPolicy` resolves topic and user routes plus browser or external handoff, and address submission currently falls back to linux.do search when the input is not recognized as a URL. There is no persisted user control for default external-link handling, no never-open-in-app domain list, and no browser-owned search-engine preference.

The repository already has the right implementation pattern for this type of change: browser data is stored in feature-level stores with `SharedPreferences` persistence in `core/storage`, manually wired via `AppContainer`, and consumed in routes through reactive state. This change should extend that pattern instead of introducing a new settings framework.

## Goals / Non-Goals

**Goals:**
- Add persisted browser preferences for default external-link handling and address-search engine selection.
- Add a persisted restricted-domain list that forces matching browser-target links to bypass the in-app browser.
- Surface these controls from the existing browser settings feature instead of waiting for a separate global settings module.
- Keep routing deterministic and compatible with the current manual `AppContainer` wiring.

**Non-Goals:**
- Do not create a full general-purpose app settings feature or migrate existing storage to DataStore.
- Do not change native topic or native user routing semantics.
- Do not introduce wildcard, regex, or path-level restricted-domain rules in the first version.
- Do not add remote sync for browser preferences.

## Decisions

### 1. Browser preferences will use a dedicated store and SharedPreferences persistence, matching current browser storage patterns
A new browser preference store will live in `core/storage` and be wired through `AppContainer` alongside browser history and bookmarks. Preference snapshots will be serialized with the same lightweight `kotlinx.serialization + SharedPreferences` pattern already used elsewhere.

Why:
- The project explicitly prefers manual dependency wiring through `AppContainer`.
- Browser preferences are local and modest in scope, so they do not justify a new persistence stack.

Alternative considered:
- Introduce DataStore or a generic settings repository.
  - Rejected because it increases scope and creates a parallel storage pattern for a focused browser change.

### 2. Restricted domains will be stored as normalized hostnames and only affect browser-target link decisions
The restricted-domain list will store normalized hostnames. During routing, it will apply to links that would otherwise be opened in the in-app browser, forcing them to bypass that target. Native topic and user routes remain native; external-app schemes continue to use existing external-app handling.

Why:
- Hostname-only matching is easy to explain, persist, and test.
- Applying the override only to browser-target links avoids surprising regressions for existing native routes.

Alternative considered:
- Let restricted domains override all routing, including native topic and user routes.
  - Rejected because it would weaken established native navigation semantics.
- Add path-aware or wildcard rules immediately.
  - Rejected because it complicates both UI and routing behavior before the simple case is proven.

### 3. Default external-link preference will be modeled as a browser preference, not as a global app policy
The new default external-link preference will live in the browser feature and apply when the routing result is otherwise ambiguous between in-app browser and system browser. Non-browser external apps, `intent://`, `mailto:`, and `tel:` continue to bypass this preference.

Why:
- The current scope is specifically browser behavior.
- This keeps the setting close to the browser settings surface where users will expect it.

Alternative considered:
- Add a root-level app-wide link-opening policy first.
  - Rejected because the repository does not yet have a dedicated settings architecture, and browser is the only consumer today.

### 4. Search-engine selection will be consumed through a pure address-resolution helper instead of hardcoding linux.do search inside the ViewModel
Address-input classification will be moved or extracted into a pure helper that accepts the current search-engine preference in addition to base URL context. The ViewModel can remain a screen-state owner without taking a direct dependency on Android storage.

Why:
- Search-engine choice is dynamic browser preference, not immutable ViewModel construction input.
- A pure helper keeps address resolution testable and reusable while preserving the current architecture.

Alternative considered:
- Keep address resolution hardcoded to linux.do search and only update the UI labels.
  - Rejected because it would make the setting fake.
- Inject the full preference store into the ViewModel.
  - Rejected because route-owned preference collection and helper-based resolution are simpler for the current architecture.

### 5. Browser settings UI will stay feature-local and may use browser subpanels for management flows
Restricted-domain management and search-engine selection may open lightweight browser subpanels from the existing browser settings panel instead of requiring a dedicated app settings destination.

Why:
- The browser feature already has settings, history, and bookmarks surfaces.
- This keeps the change incremental and avoids cross-feature navigation work.

Alternative considered:
- Delay all UI until a future settings feature exists.
  - Rejected because the routing behavior would then remain effectively hidden and hard to control.

## Risks / Trade-offs

- [Multiple preference sources could make routing harder to reason about] → Keep a single browser preference snapshot model and make routing consume one resolved input at a time.
- [Users may expect restricted domains to affect native topic or user routes] → Scope the first implementation explicitly to browser-target links and document that behavior in the settings copy.
- [Search-engine support can sprawl] → Start with a small fixed enum of supported engines and add more only if needed.
- [Feature-local settings may later need to migrate into a global settings screen] → Keep the storage and models reusable so future UI relocation does not require data migration.

## Migration Plan

1. Add browser preference models, persistence, and `AppContainer` wiring with defaults that preserve today’s behavior.
2. Update routing so browser-target decisions consult restricted domains and default external-link preference.
3. Extract address-search resolution to a helper that accepts the selected search engine.
4. Expand the browser settings UI with rows and management flows for the new preferences.
5. Add tests for persistence and routing/address-resolution helpers, then smoke test browser settings and link opening flows.

## Open Questions

- What is the smallest useful initial search-engine set: linux.do, Google, and Bing, or only linux.do plus one external engine?
- Should restricted-domain management live as an inline list inside browser settings or as a dedicated browser subpanel?
- Do we want separate preferences for “default external links” and “links opened from inside browser pages,” or is one shared browser policy enough for v1?
