## 1. Browser Preference Persistence Foundation

- [x] 1.1 Add browser preference models and SharedPreferences-backed persistence for default external-link handling, search-engine selection, and restricted-domain entries
- [x] 1.2 Wire the new browser preference store through `AppContainer` using the existing manual browser-store pattern

## 2. Routing And Address Preference Integration

- [x] 2.1 Update `LinkOpenPolicy.kt` so restricted domains can bypass in-app browser targets without changing native topic or native user routing semantics
- [x] 2.2 Integrate the default external-link preference into browser-target routing decisions while preserving external-app handling for non-browser schemes
- [x] 2.3 Extract or update browser address-search resolution so search submissions use the configured search-engine preference

## 3. Browser Settings Surface And Verification

- [x] 3.1 Expand `AppBrowserSettingsPanel.kt` and any supporting browser subpanels to let users inspect and edit restricted domains, external-link preference, and search-engine preference
- [x] 3.2 Add or update tests for browser preference persistence, restricted-domain routing decisions, and search-engine-based address resolution
- [ ] 3.3 Manually smoke test browser settings changes, relaunch persistence, restricted-domain bypass behavior, and address-search engine switching before implementation handoff
