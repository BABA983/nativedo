## Context

NativeDo already completed a first browser-family minimize pass: `BrowserHost` now keeps an active session plus ordered docked sessions, regular browser surfaces open bottom-up and rest full-screen, and browser or Instant View can minimize and restore without recreating session state. That work closed the earlier "close versus minimize" ambiguity, but it intentionally stopped at a simplified UI model.

The current implementation still differs from Telegram in three structural ways:
- the minimized dock is rendered as a floating `BrowserHost` overlay instead of an app-shell-owned bottom layer that changes layout and insets
- the multi-session view is a bottom `LazyColumn` overlay rather than a dedicated tabs manager with stacked previews and dedicated gestures
- minimize and restore motion is a generic card interpolation instead of a shared live-surface morph between active surface, dock card, and overview card

Telegram's Android implementation splits this system into a persistent bottom dock (`BottomSheetTabs`) plus an overlay layer (`BottomSheetTabsOverlay`). The dock owns real height and participates in relayout; the overlay owns manager presentation, blur, preview stack rendering, and the minimize or restore morph pipeline. This change carries that ownership model into the current Compose app shell without changing browser runtime scope or rebuilding browser chrome from scratch.

## Goals / Non-Goals

**Goals:**
- Move dock presentation into the app shell so minimized browser tabs behave like part of the shell rather than a floating feature overlay.
- Replace the current list overview with a Telegram-style tabs manager that supports stacked previews, direct restore, outside-dismiss, swipe-to-dismiss, and close-all.
- Introduce a shared transition pipeline that morphs browser or Instant View surfaces into the dock and back out through real geometry and preview state.
- Preserve the existing browser-family session model and runtime continuity while changing only presentation ownership and interaction behavior.
- Keep the scope narrowly focused on dock ownership, tabs manager behavior, and motion parity.

**Non-Goals:**
- Do not add persistent tab storage across process death.
- Do not redesign browser history, bookmarks, settings, or address editing beyond the changes needed for dock or manager parity.
- Do not reintroduce `FullRoute`, half-height browser resting states, or a separate reader-owned presentation tree.
- Do not attempt a line-by-line Telegram class port; the target is behavioral parity within NativeDo's Compose architecture.

## Decisions

### 1. `BrowserHost` stays the browser-family state owner, but shell-level presentation moves to `AppRoot`

`BrowserHost` will continue to own:
- active browser-family session selection
- ordered docked session state
- browser or reader runtime continuity
- minimize, restore, and destructive close intents

`AppRoot` and `NativeDoChromeScaffold` will gain shell-owned presentation slots for:
- the collapsed bottom dock
- the tabs manager overlay
- the shared transition overlay
- animated bottom spacing that accounts for dock height

Why:
- Telegram's dock affects app-shell layout; the current `BrowserHost`-local overlay cannot do that cleanly.
- Keeping session state inside `BrowserHost` avoids splitting runtime ownership while still letting the shell own bottom layout and overlay z-order.

Alternative considered:
- Move both state and presentation into a new app-shell browser dock controller.
  - Rejected because it would duplicate browser-family state ownership and make browser or reader runtime continuity harder to reason about.

### 2. The dock will have three shell presentation states: hidden, collapsed, and tabs-manager

The shell-owned browser tabs layer will expose:
- `Hidden`: no docked sessions
- `Collapsed`: at least one docked session exists, dock rests at the shell bottom edge
- `Manager`: a dedicated tabs manager overlay is visible above the current app scene

Behavior:
- one docked session: tapping the collapsed dock restores it directly
- multiple docked sessions: tapping the collapsed dock opens the tabs manager instead of restoring immediately
- the manager shows latest-first stacked previews, with the foremost card representing the newest docked session

Why:
- This matches Telegram's split between dock resting state and dedicated multi-tab browsing surface.
- It removes the current hybrid behavior where a dock tap sometimes behaves like restore and sometimes opens a bottom list that still looks like an extension of the dock.

Alternative considered:
- Keep the current bottom-aligned list overlay and only restyle it.
  - Rejected because the structural mismatch would remain: list semantics, gesture ownership, and transition geometry would still differ from Telegram.

### 3. Dock cards and manager cards will be preview surfaces over stable browser-family sessions

Each docked session will carry:
- stable session identity
- summary metadata for title, host, and kind
- a preview payload for visual presentation in the dock and manager

Active browser and reader surfaces will publish a host-facing preview contract that includes:
- current surface bounds in app-root coordinates
- current card metadata
- a capturable preview bitmap or composited snapshot for manager cards

The dock and manager will render preview cards from that payload, but the live WebView or reader runtime stays attached to the underlying session.

Why:
- Telegram's tabs manager shows recognizable page previews, not just titles.
- Rendering offstage live browser content for every docked session would be much more complex than keeping session runtime alive and refreshing preview payloads when a session leaves the foreground.

Alternative considered:
- Keep title-only cards or render the full offstage browser surfaces in the manager.
  - Rejected because title-only cards are not Telegram-like, and permanently mounted offstage WebViews would add unnecessary runtime and composition cost.

### 4. Minimize, restore, and manager-open transitions will use one app-root overlay with explicit geometry endpoints

The shell will own a single browser tabs transition overlay that can animate between:
- the active browser or reader surface
- the collapsed dock card
- a selected tabs-manager card

Transitions in scope:
- active surface -> dock card on minimize
- dock card -> active surface on direct restore
- dock card -> tabs manager stack on manager open
- tabs manager card -> active surface on restore from manager
- tabs manager card -> dismissed-offscreen on swipe-to-close

The overlay will consume explicit geometry from the active surface, dock card, and manager cards, plus preview payloads from the session model.

Why:
- Telegram's smoothness comes from one shared transition layer, not from several independent fades.
- Keeping the transition overlay at the app-root level prevents clipping and z-order jumps between browser content, root content, and dock UI.

Alternative considered:
- Keep separate local animations in `BrowserHost`, dock, and manager.
  - Rejected because geometry would continue to drift between owners and the animation would still read as multiple unrelated surfaces.

### 5. The tabs manager will own its own gesture system instead of inheriting list gestures from the current overview

The manager overlay will own:
- vertical scrolling through stacked tabs
- horizontal swipe-to-dismiss on individual cards
- tap on card to restore
- tap on per-card close affordance to destroy one docked session
- tap on `Close All Tabs` pill to destroy all docked sessions
- tap outside the manager to dismiss back to the collapsed dock

Why:
- Telegram's manager is a dedicated interaction surface with different gesture semantics from a bottom list.
- Gesture ownership needs to stay local to the manager so browser drag, reader drag, and root scrolling do not interfere while the manager is visible.

Alternative considered:
- Build the manager from a standard `LazyColumn` with swipe-to-dismiss rows.
  - Rejected because it would bias the UX toward list-row semantics instead of stacked-card semantics and would not match Telegram's interaction density.

### 6. Collapse and close will stay distinct across active chrome, collapsed dock, and tabs manager

The browser-family contract will remain:
- collapse minimizes the active session into the dock
- close destroys the active session
- closing from the dock or manager destroys the targeted docked session without restoring it first
- `Close All Tabs` destroys every docked session and removes the dock from the shell

Why:
- The previous change already established "collapse is not close"; this change extends that rule consistently into the tabs manager.
- A dedicated manager creates more places to close sessions, so the destructive path must stay explicit.

Alternative considered:
- Allow swipe-to-dismiss from the manager to mean "temporarily remove from manager but keep session recoverable elsewhere."
  - Rejected because Telegram treats tab dismissal as destructive, and any softer contract would reintroduce ambiguity.

## Telegram Implementation Findings To Preserve

The latest code review against Telegram's Android sources clarified that the remaining parity gap is not mainly visual. The important difference is rendering strategy and ownership discipline:

- `BottomSheetTabs` is only the resting dock and tab-state holder.
- `BottomSheetTabsOverlay` owns manager presentation, blur, gesture arbitration, and cross-surface morph transitions.
- `BotWebViewSheet` and `ArticleViewer` stay responsible for the live browser-family surfaces and expose overlay-facing drawing hooks instead of reducing every transition to a static preview card.

This matters because Telegram's smoothness comes from one rendering authority per interaction phase rather than several composables or views each animating their own approximation of the same state.

### 7. The final tabs manager parity target is an overlay-owned preview field, not a stacked lazy list

Telegram's tabs manager does not behave like a list of cards with independent item animations. `BottomSheetTabsOverlay` computes tab positions, perspective, clipping, blur, outside-dismiss behavior, vertical scroll, and horizontal swipe-dismiss inside one drawing surface.

NativeDo's current Compose manager already matches high-level behavior, but it still behaves like a stacked `LazyColumn` with per-card `graphicsLayer` transforms. For 1:1 parity, the manager target should be:

- one shell overlay owner for manager rendering
- one gesture owner for outside tap, vertical browsing, and horizontal card dismissal
- manager card transforms derived from shared stack progress rather than item-local animation only
- optional simplified card rendering only for far-offscreen cards, with the foremost cards staying on the high-fidelity path

Why:
- Telegram avoids gesture arbitration drift by not splitting manager behavior across list, card, and overlay layers.
- Telegram avoids visible desynchronization between scroll state and card transforms because one surface computes both.

Alternative considered:
- Keep the current Compose `LazyColumn` manager and continue tuning transforms, blur, and thresholds.
  - Rejected because it can get closer visually, but it still keeps list semantics and distributed gesture ownership that Telegram's manager does not have.

### 8. Shared transitions should prefer live-surface drawing contracts over bitmap-only interpolation

Telegram minimizes and restores browser-family surfaces through an overlay contract where the active surface can temporarily draw through the overlay during the transition. Static preview capture exists, but it is a fallback or parked-state representation, not the primary animation primitive.

NativeDo's current transition system already has the correct shell owner and geometry publication model, but it still renders the transition overlay from preview payloads and captured bitmaps. The parity target should shift toward:

- active browser and reader surfaces exposing an overlay-facing draw contract
- a transition phase that can temporarily suppress normal surface drawing while the overlay renders the live surface
- preview bitmaps remaining as parked-state and fallback content for dock cards, manager cards, and cases where live drawing is unavailable

Why:
- Telegram's morph reads as one continuous surface because the same live content participates in the move.
- Bitmap-only interpolation loses scroll position fidelity, active media state, and subtle chrome attachment cues at the exact moment the user is most sensitive to motion quality.

Alternative considered:
- Keep geometry parity but accept bitmap-based overlay rendering as "good enough."
  - Rejected because that preserves the most visible difference between the current implementation and Telegram during minimize or restore.

### 9. Browser-family runtime continuity includes preserving reusable WebView or reader surfaces, not just URL-level state

Telegram's docked tab state keeps enough runtime material to reopen the same browser-family session, including reusable WebView instances or reader state, then captures a preview after the session has been parked. This separation is important:

- session continuity is owned by the docked session model
- preview imagery is owned by the presentation layer
- transition smoothness is owned by the overlay

NativeDo should preserve that same separation. The docked session model should remain the source of truth for reusable browser or reader runtime continuity, while preview snapshots stay presentation artifacts rather than the restoration mechanism itself.

Why:
- This keeps "same tab restored" meaningfully true at runtime, not just visually similar.
- It prevents preview capture work from becoming coupled to session correctness.

### 10. Browser chrome parity depends on reducing distributed animation ownership in the active surface

Telegram's browser chrome feels unusually smooth because `WebActionBar` is largely self-drawn and couples color interpolation, title transition, search or address expansion, and icon state changes in one owner. Browser sheet and reader containers also coordinate open, dismiss, and action-bar attachment from the same presentation host.

This change should not absorb a full browser chrome rewrite, but it must preserve one requirement for follow-up browser-motion work:

- tabs-manager and minimize/restore work must not lock NativeDo into a bitmap-only or card-only transition model that makes a future single-owner browser chrome harder to add

Why:
- Telegram's tabs manager motion and browser chrome motion are not independent systems. They feel coherent because both treat the browser-family surface as a presentation-controlled object, not a loose collection of separately animated children.

Scope note:
- Full browser chrome self-draw parity, action-bar attachment nuances, and browser open/close motion tuning should stay in the browser-motion aligned changes rather than expanding this change beyond dock, manager, and transition ownership.

## Risks / Trade-offs

- [Preview capture from browser or reader surfaces can become expensive if refreshed too often] -> Refresh preview payloads on minimize, manager open, and major active-surface updates instead of trying to stream every frame.
- [Dock height animation can conflict with the root bottom bar and inset math] -> Let one shell layer compute bottom spacing and have the dock publish one animated occupied height to the scaffold.
- [Geometry drift between active surface and dock or manager cards can make morphs look worse than the current simple animation] -> Require every participating surface to publish app-root-relative bounds before starting a transition and fall back only when geometry is unavailable.
- [Manager gestures can fight with browser or reader gestures if visibility handoff is late] -> Disable browser-family pointer interaction beneath the manager while the manager is visible or animating.
- [Scope can balloon into a full browser redesign] -> Keep this change limited to dock ownership, manager behavior, and transition parity, and leave unrelated chrome polish to later changes.
- [Trying to force Telegram-like manager behavior through `LazyColumn` semantics can create endless tuning work without true parity] -> Treat the current Compose manager as an intermediate step and explicitly budget a move to overlay-owned rendering when this change continues.
- [Preview bitmaps can become mistaken for the real transition contract] -> Keep live-surface drawing as the parity target and treat bitmap snapshots as fallback presentation assets.

## Migration Plan

1. Add shell-owned browser-tabs presentation slots and animated bottom spacing in `AppRoot` or `NativeDoChromeScaffold` while leaving `BrowserHost` as the state owner.
2. Move the collapsed dock out of the current `BrowserHost` overlay path and make it publish real occupied bottom height.
3. Replace the current `LazyColumn` overview with a dedicated tabs manager overlay and wire single-tab restore, multi-tab manager open, card close, swipe dismiss, outside dismiss, and close-all.
4. Add the preview or geometry publishing contract for active browser and reader surfaces, then rework minimize or restore transitions onto one app-root overlay.
5. Remove the old dock overview and generic dock-transition overlay once the new shell-owned dock and manager pipeline is in place.
6. Verify dock layout behavior, manager interactions, minimize or restore motion, and browser or reader continuity together before implementation is considered complete.

Rollback strategy:
- If the manager overlay or transition pipeline regresses badly, the shell can temporarily keep the new dock state model while falling back to direct restore only, without reverting browser-family session ownership.

## Open Questions

- None for this scope.
