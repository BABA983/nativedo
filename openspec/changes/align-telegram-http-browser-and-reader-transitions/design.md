## Context

NativeDo’s browser family is no longer a single ad-hoc WebView page: the codebase already contains a route browser, a sheet browser container, a reader scene, a browser overlay host, a presentation policy, Telegram-inspired browser chrome, browser runtime hardening, browser preferences, and richer address suggestions. However, these parts still behave more like parallel browser experiments than a single coherent Telegram-style browser family.

Telegram’s regular HTTP browser on Android is not just “a WebView page with a toolbar.” The main HTTP browser behaves like one bottom-up in-app browser surface with its own browser chrome semantics, while Instant View or article reading behaves like a distinct right-to-left follow-up scene. Redirects, auth, challenge-heavy pages, uploads, and sibling-subdomain hops do not become a separate in-app browser presentation target; they stay inside the same browser session unless routing leaves the browser family entirely. History, bookmarks, settings, address suggestions, and menu interactions are then layered on top of those container semantics. NativeDo currently has many of those feature pieces, but the presentation split between `FullRoute`, `Sheet`, and reader still prevents the browser family from reading as one Telegram-like system.

The repository already has overlapping changes in flight:
- `telegram-browser-containers-instant-view` introduced the explicit `FullRoute / Sheet / InstantView` matrix and the root overlay host.
- `browser-webview-runtime-hardening` hardened the shared browser runtime.
- `browser-preferences-restricted-domains` added routing preferences.
- `browser-address-suggestions-upgrade` deepened address editing.

This new change should not duplicate those efforts. Instead, it should consolidate them into a single Telegram-style HTTP browser behavior model: what opens as the normal browser, how it enters and exits, how reader flows transition, and how the surrounding chrome should behave once container semantics are fixed.

## Goals / Non-Goals

**Goals:**
- Make ordinary HTTP browser interactions default to the Telegram-like bottom-up browser container and treat that container as the only in-app browser presentation surface.
- Keep auth-sensitive and complex WebView behavior inside that same browser container instead of preserving `FullRoute` as a separate browser-family fallback.
- Define Instant View or reader as a distinct right-to-left scene transition rather than just another browser panel or alternate route style.
- Align browser chrome surfaces—address, history, bookmarks, settings, overflow, and back-history—with the chosen Telegram-like container behavior.
- Preserve NativeDo’s Compose architecture and existing browser feature state ownership while tightening behavior toward Telegram’s interaction model.

**Non-Goals:**
- Do not copy Telegram Java class structure, visual assets, or GPL implementation details.
- Do not promise full Telegram browser parity in one pass, such as tabs, minimize, or every browser settings option.
- Do not replace the current browser runtime hardening work; reuse it under the new container defaults.
- Do not move browser logic into a separate app module or introduce a dependency injection framework.

## Decisions

### 1. Collapse the in-app browser family to one regular browser container plus Instant View follow-up
Within the in-app browser family, NativeDo will distinguish only:
- the regular browser container
- the Instant View or reader follow-up scene

Why:
- Telegram’s Android browser model does not expose a separate in-app browser presentation target for “ordinary browsing” versus “complex web flow.”
- NativeDo’s current `FullRoute / Sheet / InstantView` matrix overstates container differences that Telegram instead solves inside one browser session.
- The shared browser runtime already handles the real difficult cases at the browser layer, so keeping a second browser presentation target adds more UX discontinuity than engineering value.

Alternative considered:
- Keep `FullRoute` as a separate browser-family target for auth-sensitive and complex web flows.
  - Rejected because it preserves the exact mismatch the user is trying to remove and causes redirects or sibling-subdomain hops to look like container switches instead of one continuous browser session.

### 2. Treat auth, challenge, upload, and sibling-subdomain behavior as browser runtime concerns
Authentication, Cloudflare challenge handling, uploads, file chooser, popup handling, permissions, SSL recovery, and sibling-subdomain redirects will remain browser-scoped runtime problems, but they will no longer justify promoting the user into a separate `FullRoute` presentation target.

Why:
- These are real runtime concerns, but they are not evidence that the user needs a second in-app browser container.
- `AppBrowserRoute` already centralizes popup handling, downloads, permissions, SSL recovery, and browser error UI for both current browser containers.
- The Telegram comparison indicates the right abstraction boundary is “shared browser runtime, one browser presentation,” not “runtime concern implies a different browser scene.”

Alternative considered:
- Keep promotion or reopen logic so complex flows can jump from sheet browser into `FullRoute`.
  - Rejected because it splits browser history, address continuity, and perceived ownership at the exact moments when Telegram keeps them continuous.

### 3. The regular browser container opens from the bottom but rests full-size
The regular browser container will continue to use bottom-up presentation semantics, but its resting state should be full-size. Partial height may exist as transient drag geometry during open or dismiss, not as the normal steady-state browser presentation.

Why:
- Telegram’s regular browser feels like a full-screen browser scene that rises from the bottom, not like a half-sheet that optionally expands.
- This is the direct fix for browser pages opening half-height when the desired behavior is “bottom-up, but full-screen.”
- Keeping a persistent partial-height browser state makes normal redirects, top-bar geometry, and dismiss thresholds behave more like a generic sheet than Telegram’s browser container.

Alternative considered:
- Preserve `Partial / Fullscreen / Closed` as equal browser resting states.
  - Rejected because it keeps the normal browser geometry misaligned with Telegram and makes drag settle logic harder to stabilize.

### 3.1 Browser motion should be owned by one coordinate system, not separate entry and exit animations
NativeDo should treat regular browser motion as one continuous coordinate model that drives:
- initial open from bottom
- drag and settle behavior
- full-size resting geometry
- fullscreen or fullsize anchor changes
- dismiss or minimize handoff

Why:
- Telegram’s `BotWebViewSheet` does not model these as unrelated animations. `show()`, drag settle, and fullscreen all operate through the same swipe-container coordinates and top anchor contract.
- A separate “entry animation” layered on top of a different drag model is one of the main reasons Compose recreations feel assembled rather than physically continuous.
- This is the behavioral boundary that makes browser open, drag, and restore read as one live surface instead of multiple presentation states.

Alternative considered:
- Keep the current model where open, dismiss, and fullscreen are mostly separate animated properties or helper transitions.
  - Rejected because it preserves timing drift between browser shell, gesture settling, and chrome attachment.

### 4. Redirects and browserable subdomain hops stay in the same browser container
If routing still resolves to in-app browser navigation, redirects, `window.open` replacements, auth callbacks, challenge pages, uploads, and sibling-subdomain hops should keep the user in the same browser container instead of reopening in another browser scene.

Why:
- The user-visible continuity problem is not just entry animation; it is that the same browsing session can currently change container ownership mid-flow.
- Telegram keeps browser history, title/address ownership, and return semantics continuous across those transitions.
- NativeDo’s current promotion logic is the direct cause of issues like a sibling-subdomain redirect appearing as a separate right-to-left full-screen browser.

Alternative considered:
- Allow redirects or complex-flow detection to swap browser containers as long as state is copied across.
  - Rejected because copied state still feels like a container switch to the user and introduces more edge cases than it removes.

### 5. Define Instant View or reader as a right-to-left follow-up scene that is sourced from browser context
Instant View should behave as a distinct reading scene with its own transition semantics, source restoration, and chrome density. The browser should open normally first, and supported pages can then transition into reader with a right-to-left scene motion, closer to Telegram’s `ArticleViewer` model.

Why:
- Telegram differentiates “browser open” from “reader follow-up.”
- NativeDo already has a reader scene, but the transition semantics need to be treated as part of the browser-family contract rather than a detached optional feature.

Alternative considered:
- Treat reader as just another browser subpanel or reuse browser open animation.
  - Rejected because it collapses browser and reader semantics into one surface.

### 5.1 Top attachment should be a geometry handoff, not a header fade
When browser or reader content reaches the top attachment threshold, NativeDo should treat that as a geometry transition where the surface attaches into top-bar space. The visual result should come from one attached-progress signal that can drive:
- corner radius reduction
- handle morph or disappearance
- header background takeover
- top shadow and inset changes
- title or chrome alignment

Why:
- Telegram’s reader and browser family do not simply fade a header in. They cross a threshold and then animate attachment as a continuous physical state.
- This is a major part of why Telegram’s browser family feels “latched” into the app chrome instead of overlaid on top of it.
- A threshold plus attached-progress model gives NativeDo one stable place to coordinate browser and reader chrome geometry.

Alternative considered:
- Keep top attachment as a combination of alpha or translation transitions on independent header composables.
  - Rejected because it tends to reveal handoff seams and does not preserve the feeling that the surface itself is attaching.

### 6. Browser chrome stays feature-owned, but must be revalidated against the simplified browser container model
Browser chrome state remains owned by the browser feature (`AppBrowserUiState`, `AppBrowserChromeScene`, route-owned derivation). However, once the browser family no longer distinguishes `FullRoute` versus `Sheet`, all chrome surfaces—address, history popup, bookmarks, settings, overflow menu, search panel—must be reviewed to ensure they no longer carry full-route-only or auxiliary-sheet-only assumptions.

Why:
- The current architecture is already correct in principle: scene state is feature-owned, not globally scattered.
- The problem is not state ownership; it is that the surrounding container semantics changed and the chrome contract must be updated to match.

Alternative considered:
- Rebuild browser chrome from scratch to imitate Telegram’s classes more literally.
  - Rejected because it would discard working Compose abstractions for little architectural value.

### 7. Telegram 1:1 alignment should target interaction and geometry first, and only then feature density
This change prioritizes the following order:
1. container choice
2. open or close transition semantics
3. reader transition semantics
4. browser chrome behavior under those containers
5. feature density polish

Why:
- Container semantics are the foundation for everything else the user perceives.
- Address, history, bookmarks, and settings will continue to feel “off” if the browser still opens under the wrong container semantics.

Alternative considered:
- Continue adding browser feature details while leaving current presentation behavior mostly intact.
  - Rejected because it would keep polishing the wrong foundation.

### 8. Transition parity should target live-surface continuity, with snapshots only as fallback
Where browser-family transitions move between active browser, reader, and parked or minimized states, NativeDo should target a live-surface or draw-through transition contract first. Snapshots may still exist for fallback, parked-state presentation, or recovery, but they should not define the main motion model.

Why:
- Telegram’s smoothness comes from the active surface participating in overlay motion, not only from interpolated preview rectangles.
- Snapshot-first transitions are acceptable for parked previews, but they are visibly less continuous when used as the primary active transition path.
- This keeps this change aligned with the tabs-manager exploration without forcing the tabs implementation details into this change.

Alternative considered:
- Continue using preview capture as the primary visual owner for all browser-family transitions.
  - Rejected because it cannot achieve true 1:1 parity for live minimize, restore, and cross-surface handoff.

## Risks / Trade-offs

- [Removing `FullRoute` may expose runtime gaps that used to be masked by a container split] → Revalidate popup handling, file chooser, permissions, SSL recovery, downloads, and recoverable page-failure flows inside the single browser container before claiming parity.
- [Browser sheet and reader transitions may overlap awkwardly with existing top-bar owner rules] → Revalidate owner transitions through root overlay host and keep a single visible owner at all times.
- [Reader drag and nested scroll ownership may still jitter even after browser-family simplification] → Consolidate reader dismiss and settle ownership around one drag model before tuning animation constants.
- [Trying to align too many browser surfaces at once could blur scope] → Prioritize container semantics and reader transitions first, then tighten chrome surfaces that are directly affected.
- [Telegram 1:1 expectations may pressure the project toward class-level imitation] → Keep the design explicitly constrained to behavior, geometry, transition semantics, and interaction density.
- [Tabs-overlay and browser-family motion could drift into separate contracts again] → Keep browser-family docs explicit that active transition ownership should be live-surface capable even if tabs-specific overlay implementation lands in another change.

## Migration Plan

1. Remove `FullRoute` from browser-family presentation policy and host ownership so browserable HTTP requests resolve only to the regular browser container or Instant View.
2. Tighten the bottom-up browser container so it opens full-size, dismisses downward, and no longer exposes a persistent half-height resting state.
3. Remove in-browser promotion logic so redirects, auth flows, challenge pages, uploads, and sibling-subdomain hops stay inside the same browser container whenever routing still resolves to app-browser navigation.
4. Rework reader entry and transition behavior so supported pages enter a right-to-left reader scene with stable restoration to the active browser container.
5. Unify browser motion ownership so open, drag settle, attach, fullscreen, and dismiss derive from one browser motion contract instead of stacked animations.
6. Audit browser chrome surfaces—address, history, bookmarks, settings, overflow, search—against the simplified browser container semantics and adjust where needed.
7. Verify browser runtime hardening, browser container motion, top attachment, and reader transitions together before claiming alignment.

## Open Questions

- Should the standalone login route migrate into the regular browser container in the same implementation pass, or remain a temporary non-browser-family path until follow-up work?
- Should the reader continue to use a separate dialog window, or should its host be folded more tightly into the same overlay stack as the regular browser container?
- Should the first alignment pass also retune browser dismiss gesture regions and thresholds, or only lock in full-size resting geometry and container continuity first?
- How much of Telegram’s browser settings, bookmarks, and history density do we want to pull into this change versus leaving for later polish?
