## 1. Address Suggestion Data Foundation

- [x] 1.1 Add typed browser address-suggestion models that can represent recent links, recent searches, bookmarks, and quick browser actions
- [x] 1.2 Add a lightweight recent-search persistence store and wire it through `AppContainer` using the existing browser-store pattern
- [x] 1.3 Extract a pure address-suggestion resolver that merges browser draft input, page context, history, bookmarks, and recent searches into browser address sections

## 2. Browser Address Scene Integration

- [x] 2.1 Update browser UI-state mapping so address editing can consume the richer suggestion sections without moving complex state production into composables
- [x] 2.2 Update `AppBrowserChrome.kt` address-editing UI to render the richer address surface and handle typed suggestion selection semantics
- [x] 2.3 Ensure address submissions record recent searches when the input resolves as search intent and dismiss incompatible chrome overlays when a suggestion is applied

## 3. Spec Contract Alignment And Verification

- [x] 3.1 Keep browser address editing aligned with `browser-scene-chrome` by preserving mutual exclusivity between address editing and browse-only overlays while suggestions are shown
- [x] 3.2 Add or update tests for recent-search persistence, typed suggestion derivation, and suggestion selection semantics
- [ ] 3.3 Manually smoke test address editing with recent links, recent searches, bookmarks, quick actions, and fallback empty-state behavior before implementation handoff
