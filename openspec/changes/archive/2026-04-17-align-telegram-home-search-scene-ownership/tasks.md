## 1. Home Search Ownership

- [x] 1.1 Keep `NativeDoReaderScaffold` mounted in both normal home and home-search modes
- [x] 1.2 Move home search from a full-page branch to a `HomeRoute` scene-local mode
- [x] 1.3 Ensure home search no longer relies on `RootDestination.Search` for its primary entry path

## 2. Search Composition Split

- [x] 2.1 Split the current standalone search UI into a reusable results-body composable plus a scene-owned search header
- [x] 2.2 Add a home-owned search top bar or equivalent header that stays inside the scaffold top-bar contract
- [x] 2.3 Preserve existing topic and user result callbacks while the search UI is rehosted under `HomeRoute`

## 3. Shell Coordination

- [x] 3.1 Publish home-search active state from `HomeRoute` through the root-shell wiring
- [x] 3.2 Hide the root bottom tabs while home search is active
- [x] 3.3 Verify the search-mode layout no longer overlaps the system status bar or bypasses normal content padding

## 4. Route Cleanup And Follow-Up

- [x] 4.1 Decide whether `RootDestination.Search` should be removed or repurposed after the home-search migration
- [x] 4.2 Document or queue the follow-up change that migrates login toward browser-family full-route ownership
