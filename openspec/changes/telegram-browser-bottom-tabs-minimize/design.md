## Context

NativeDo already moved browser-family presentation out of the native page stack and added a first minimize path, but the current result is still a single minimized strip entry. That does not match Telegram's browser-family model, where browser sheets and Instant View both minimize into a shared bottom tabs dock, restore from that dock, and stay distinct from true close.

Telegram's Android implementation splits this into two layers: a persistent bottom dock (`BottomSheetTabs`) and an animation or overview overlay (`BottomSheetTabsOverlay`). Browser sheets and `ArticleViewer` both save live state into that dock on minimize, and the dock either reopens the latest session directly or expands into a bottom tabs overview when multiple entries exist. NativeDo needs the same ownership model, but mapped onto the current Compose-based `AppRoot` and `BrowserHost`.

This change follows the archived `telegram-browser-family-minimize-overlay` work. That earlier change intentionally stopped at one minimized strip entry so the app could separate minimize from close first. The next step is to replace that temporary strip model with a real dock-backed browser-family session system.

## Goals / Non-Goals

**Goals:**
- Move browser-family minimize from a single strip entry to a shared bottom tabs dock.
- Let regular browser and Instant View use the same dock-backed minimize, restore, and close lifecycle.
- Support multiple docked browser-family sessions with latest-first ordering.
- Keep the regular browser full-screen while active and restore the same live session when reopened from the dock.
- Match Telegram's collapsed dock versus expanded overview interaction model closely enough that minimize no longer feels like a custom NativeDo invention.

**Non-Goals:**
- Do not build a general-purpose browser tab manager with new-tab creation, tab search, or persistent tab storage.
- Do not persist docked browser-family sessions across cold start or process death in this change.
- Do not redesign browser address editing, bookmarks, or history beyond the dock and action-model changes required for minimize.
- Do not reintroduce `FullRoute`, half-height browser resting states, or a separate dialog-owned Instant View path.
- Do not require full Telegram parity for overview-only gestures such as swipe-to-dismiss if tap restore and tap close already cover the needed behavior.

## Decisions

### 1. `BrowserHost` will own one active session and an ordered docked session list

`BrowserHost` will move from "active request plus optional minimized strip entry" to a browser-family session model with:
- one active session
- zero or more docked sessions
- stable session IDs
- a session kind of regular browser or Instant View

Newly minimized sessions will be inserted as the latest docked entry. Restoring a docked entry will remove it from the dock and make it active again without rebuilding it from URL-only state.

Why:
- A true bottom tabs dock needs more than one minimized entry.
- The current single-entry strip cannot represent Telegram's "latest tab on top, older tabs behind it" behavior.

Alternative considered:
- Keep the existing single minimized slot and fake a multi-entry dock in the UI.
  - Rejected because the state model would still collapse multiple minimize operations into one slot and lose the real restore order.

### 2. The dock will have two presentation states: collapsed dock and expanded overview

The new bottom dock will be anchored to the bottom edge of the app shell and will expose:
- a collapsed state that stays visible while docked sessions exist
- an expanded overview state that presents the docked sessions as a Telegram-style stack or list overlay

Behavior:
- If exactly one docked session exists, tapping the collapsed dock restores it directly.
- If multiple docked sessions exist, the collapsed dock stays compact and tapping it opens the expanded overview instead of restoring immediately.
- The expanded overview owns docked-entry selection, docked-entry close, and dismissal back to the collapsed dock.

Why:
- Telegram uses a bottom-attached dock for the "resting" state and a separate overlay for multi-entry browsing.
- Always opening a full manager or always restoring the latest tab would miss one of those two interaction modes.

Alternative considered:
- Use a floating chip or always-expanded sheet for minimized browser-family entries.
  - Rejected because it would not match Telegram's docked visual hierarchy or its single-entry versus multi-entry behavior.

### 3. Minimize and restore transitions will use a shared overlay animation layer

NativeDo will add a host-owned overlay animation layer that can animate between:
- the currently active browser-family surface bounds
- the collapsed dock card bounds
- a selected overview entry bounds when restoring from the expanded dock

The source surface will provide enough geometry and preview data for the host to animate minimize into the dock and restore back out of it. The dock will provide the destination card geometry. This animation owner must live above both browser-family content and root content so the transition does not jump between unrelated containers.

Why:
- Telegram's minimize is a morph into the bottom tabs dock, not a simple fade plus strip swap.
- Split ownership is one of the main reasons the current minimize path feels unstable.

Alternative considered:
- Use independent fade or slide transitions for the active browser-family surface and the dock.
  - Rejected because the transition would still look like two unrelated surfaces rather than one live surface moving into the dock.

### 4. Active browser-family chrome will expose explicit collapse and close actions

The active browser and active Instant View chrome will both expose:
- a visible collapse action that minimizes into the dock
- a visible close action that destroys the active session

Overflow surfaces may still mirror those actions, but minimize cannot live only inside overflow anymore. Once a session is docked, dock-level close actions will remove it without restoring it first.

Why:
- The user explicitly wants Telegram-style "collapse to bottom, X means true close" behavior.
- Keeping minimize as a hidden or context-dependent action is what made earlier interactions ambiguous.

Alternative considered:
- Keep minimize only in the overflow menu and let close or drag choose between minimize and destroy.
  - Rejected because it does not create a stable user contract and does not match Telegram's visible collapse affordance.

### 5. Browser and reader runtime continuity will remain session-scoped

Each browser-family session will keep its own live runtime holder while docked:
- regular browser sessions keep their WebView runtime, history continuity, and browser chrome state
- Instant View sessions keep their document, reading position, and a reference back to their source browser session when relevant

The dock stores summary metadata for presentation, but it does not become the owner of browser or reader runtime state.

Why:
- Recreating from URL or article metadata would still feel like reopen, not resume.
- The dock should describe sessions, not replace their underlying runtime owners.

Alternative considered:
- Preserve only title, URL, and host in the dock and rebuild runtime on restore.
  - Rejected because that would regress page history, reader continuity, and browser-to-reader return paths.

### 6. First-pass dock interactions will stay focused on restore, close, and overview navigation

The first implementation of the expanded dock overview will guarantee:
- tap to restore a session
- tap a close affordance to destroy a docked session
- dismiss the overview back to the collapsed dock
- vertical scrolling when the dock contains more entries than fit comfortably

This change does not require Telegram's full overview gesture surface such as per-card horizontal swipe-to-dismiss or close-all actions.

Why:
- The essential parity gap is the docked session model, dock presentation, and dock-backed minimize or restore flow.
- Full gesture parity would broaden the change without changing the core contract the user asked for.

Alternative considered:
- Match every Telegram overview gesture in the same change.
  - Rejected because it adds gesture-specific complexity without being necessary to replace the current single-strip system.

## Risks / Trade-offs

- [A docked session list is more complex than a single minimized slot] -> Keep session ownership centralized in `BrowserHost` and avoid per-screen minimize stores.
- [Cross-layer geometry animation can drift if source and destination bounds come from different owners] -> Put the animation overlay at the app-root level and require both active surfaces and dock cards to publish explicit geometry.
- [Reader restore can lose its source browser return path] -> Keep a stable source browser session ID on reader sessions until true close.
- [The dock may conflict with root bottom tabs and system insets] -> Anchor the dock in `AppRoot` or `RootTabsHost` and let one shell layer own bottom spacing.
- [Live sessions cannot survive process death in the first pass] -> Treat the dock as in-memory only and clear stale dock state when the host is recreated from a cold start.

## Migration Plan

1. Add a browser-family session registry and dock presentation state to `BrowserHost`.
2. Replace the current minimized strip with a collapsed bottom dock that can hold multiple docked sessions.
3. Add an expanded dock overview overlay for multi-entry browsing, restore, and close.
4. Rewire browser and Instant View collapse actions so both minimize into the dock and restore through the same session model.
5. Remove the old single-entry strip behavior and verify single-session and multi-session dock flows before implementation is considered complete.

## Open Questions

- None for this scope.
