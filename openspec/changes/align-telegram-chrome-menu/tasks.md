## 1. Shared Chrome Primitives

- [x] 1.1 Audit current browser/viewer popup and chrome primitives, then extract the missing shared APIs for icon morph, anchor highlight, popup motion, and chrome visibility orchestration
- [x] 1.2 Introduce two popup presenter paths, `AnchoredPopup` and `ContextualOptions`, without collapsing them into a single generic menu abstraction
- [x] 1.3 Define the shared state and motion contracts needed for menu-expanded, history-expanded, and chrome-visible interactions across scenes

## 2. Browser Scene Chrome

- [x] 2.1 Refactor browser chrome state into explicit browse, search, address editing, history popup, overflow menu, and subpanel scenes
- [x] 2.2 Update browser back-history and overflow surfaces to consume the new anchored popup contracts from the top bar
- [x] 2.3 Ensure browser title, host, progress, and back/forward availability stay synchronized with active page state in every scene

## 3. Immersive Viewer Chrome

- [x] 3.1 Refine media viewer state so chrome visibility, menu expansion, and auto-hide suspension are modeled explicitly
- [x] 3.2 Update viewer top and bottom chrome to animate as one immersive overlay lifecycle instead of independent elements
- [x] 3.3 Scope viewer popup actions to the active media item and restore auto-hide behavior correctly after popup dismissal

## 4. Root Contextual Chrome

- [x] 4.1 Replace the current static root top bar behavior with a root chrome state model covering normal, search, action mode, and contextual options states
- [x] 4.2 Implement root more-actions as a contextual options panel anchored to the header control instead of reusing browser-style overflow presentation
- [x] 4.3 Integrate root action mode chrome transitions so selection-driven header changes do not break existing home search and navigation flows

## 5. Verification And Follow-up

- [x] 5.1 Add or update targeted verification for browser chrome, viewer chrome, and root chrome state transitions where practical in the current Android setup
- [x] 5.2 Run manual smoke checks for anchored popup behavior, chrome visibility transitions, and cross-scene back handling on root, browser, and media viewer flows
- [x] 5.3 Update any project-facing notes or follow-up docs needed to explain the new chrome layering and popup presenter boundaries before implementation handoff
