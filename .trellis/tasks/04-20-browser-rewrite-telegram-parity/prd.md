# Browser Rewrite Telegram Parity

## Goal
Replace the current in-app browser implementation with a new Compose-based browser architecture that follows Telegram Android as the primary reference for layout, motion, gestures, and interaction behavior.

## Summary
- The current browser feature is considered structurally unsound and bug-prone.
- The rewrite should discard the existing browser implementation rather than incrementally patching it.
- Telegram Android is the 1:1 behavioral and visual reference.
- Existing router/session/persistence/theme foundations should be reused where explicitly marked as keep.

## Confirmed Requirements
- Rewrite the browser feature from scratch around a new BrowserHost → BrowserSheet → BrowserActionBar / BrowserWebContainer / BrowserReaderScene architecture.
- Replace old `AppBrowser*` files with new `Browser*` files.
- Keep `LinkRouter`, `CookieSyncBridge`, browser history/bookmark persistence backends, reader content models, shared rendering primitives, and theme/shell foundations.
- Match Telegram-style sheet, dock, overlay, reader, and transition behavior as closely as practical in Compose.
- Validate each phase with compilation, and keep/add unit coverage for settle logic, brightness logic, tab-state serialization, and ViewModel actions.
- The first delivery phases must preserve these user-visible capabilities: opening and browsing links; top bar back/forward/menu/progress; address editing and suggestions; in-page search; Instant View / Reader; history/bookmarks/settings panels; stable login state and cookie synchronization.
- Tabs dock / overlay and advanced platform handling (for example file chooser, downloads, permission requests, and SSL error flows) may land after the first must-have slice if needed.

## Implementation Decisions
- Use phase-by-phase delivery. Each phase should stay compilable, manually testable, and incrementally replace old browser pieces where practical.
- When true 1:1 Telegram parity is constrained by Compose or Android platform differences, prioritize: (1) visual and interaction feel parity, (2) Telegram-like transition and gesture logic, and (3) no logic bugs or janky animation/interaction regressions introduced by the Compose implementation.
- Treat the rewrite as a clean break for old browser runtime state. Preserve login state, cookies, history, bookmarks, and search records, but do not preserve old docked-tab/session-restore state from the `AppBrowser*` implementation.

## Open Questions
None for the initial implementation brief.

## Acceptance Criteria
- [ ] New browser architecture is implemented and integrated into `NativeDoApp`.
- [ ] Old browser implementation files are removed or replaced.
- [ ] Browser open/close/minimize/restore flows work correctly.
- [ ] Tabs dock and tabs overlay work correctly.
- [ ] Reader scene works with the new architecture.
- [ ] Browser-related unit tests and compile checks pass.
- [ ] Manual validation checklist from the spec is completed by the user.

## Technical Notes
- Primary source spec: `docs/superpowers/specs/2026-04-19-browser-rewrite-telegram-parity.md`
- Reference implementation: `/Users/baba/Documents/workspace_github/Telegram`
- Expected task type: frontend-heavy, architecture-heavy, multi-phase rewrite
- This task requires brainstorm before implementation because it spans many files and includes architecture and parity decisions.