## Context

NativeDo already moved most browser presentation out of the native page stack, but the current browser-family model is still split across two different container owners. `BrowserHost` only tracks `sheetRequest` and `readerRequest`, while `AppBrowserSheetContainer` still carries sheet-style drag geometry and `AppBrowserReaderScene` is rendered as a separate `Dialog`. At the feature level, browser runtime state is still keyed from route-level `remember(url)` values, and browser actions only expose close-style semantics. That combination is why the app can still feel like "a sheet plus a reader dialog" instead of Telegram's browser family.

Telegram's Android implementation separates true close from minimize. `ArticleViewer.dismiss(true)` routes into the bottom tabs overlay while `dismiss(false)` destroys the surface, `BotWebViewSheet` exposes an explicit collapse action, and `BottomSheetTabsOverlay` saves live sheet state before animating it into the bottom strip. The goal here is to match that interaction model without copying Telegram's class structure.

This change is separate from `align-telegram-http-browser-and-reader-transitions`. That change intentionally stopped at browser-family simplification and full-screen bottom-up browser motion; it explicitly left minimize or tabs behavior out of scope.

## Goals / Non-Goals

**Goals:**
- Present the regular browser as a bottom-up surface whose steady state is full-screen.
- Let both regular browser and Instant View minimize into a bottom strip and later restore the same live session.
- Distinguish `Minimize` from true `Close` in browser-family chrome.
- Move Instant View ownership into the same browser-family host so minimize, restore, and close work the same way for browser and reader surfaces.
- Remove browser-only scroll-collapse behavior from the regular browser while keeping reader-specific reading interactions separate.

**Non-Goals:**
- Do not build a full Telegram-style multi-tab manager in this change.
- Do not redesign bookmarks, history, settings, or browser address editing beyond the action model changes needed for minimize and close.
- Do not reintroduce `FullRoute` or any new browser presentation target.
- Do not rewrite the shared WebView runtime, cookie handling, downloads, or permissions beyond what host-owned restore requires.

## Decisions

### 1. `BrowserHost` becomes a browser-family host with one active entry and one minimized entry

The host will move from `sheetRequest` plus `readerRequest` to a browser-family state model with:
- one active entry
- one optional minimized entry
- no separate dialog-owned reader slot

Each entry will be typed as either regular browser or reader and will own a stable host entry identity so it can survive minimize and restore without being recreated from URL alone.

Why:
- The current `sheetRequest` plus `readerRequest` split cannot represent "same surface, minimized instead of closed."
- A host-owned entry identity is the smallest change that makes restore possible without inventing a full browser tab subsystem.

Alternative considered:
- Keep route-owned `AppBrowserOpenRequest` plus reader dialog state and synthesize minimize by reopening from saved URL.
  - Rejected because it loses browser history continuity, WebView runtime continuity, and reader position, which is exactly the behavior the user wants to fix.

### 2. Reader leaves `Dialog` ownership and becomes another browser-family surface

Instant View will no longer be hosted as a separate window-level dialog. Instead, the same `BrowserHost` will render either:
- the active browser surface
- the active reader surface
- the minimized bottom-strip entry

Why:
- Telegram minimizes and restores reader state through the same bottom overlay system as browser sheets.
- Keeping reader in a separate dialog would force minimize to be a destroy-and-recreate flow or a cross-window handoff, both of which add jitter and lifecycle risk.

Alternative considered:
- Keep reader in `Dialog` and only add a bottom strip for the regular browser.
  - Rejected because it would preserve inconsistent close, minimize, and restore semantics between browser and Instant View.

### 3. Regular browser stays bottom-up, but full-screen is its only resting geometry

The regular browser will still enter from the bottom, but once open it should behave as a full-screen surface. The persistent partial-height browser state and visible sheet handle are removed from normal browser usage.

Why:
- The current half-sheet feel is one of the main visual mismatches against Telegram.
- The user explicitly called out that the normal browser should not keep scroll-collapse behavior; only Instant View should have reading-specific header motion.

Alternative considered:
- Keep `Partial` as a first-class resting anchor and retune thresholds again.
  - Rejected because the wrong browser geometry is structural, not a threshold-tuning problem.

### 4. Minimize and close are separate host actions, not two outcomes of the same dismiss path

The browser-family chrome will expose a dedicated minimize action and a dedicated close action:
- `Minimize` animates the active entry into the bottom strip and keeps its state alive.
- `Close` dismisses the active or minimized entry and releases its state.

The first implementation will support button-driven minimize and restore. Any drag-based settle logic will be host-owned and must resolve into a stable `expanded`, `minimized`, or `closed` state without using header drag to drive browser reading progress.

Why:
- Telegram uses explicit collapse versus destroy behavior.
- Reusing one dismiss path for both outcomes is what makes current interactions feel ambiguous and glitch-prone.

Alternative considered:
- Treat swipe-down or back as a context-dependent "maybe minimize, maybe close" action.
  - Rejected because it blurs a core contract the user explicitly asked to separate: the collapse button minimizes, `X` truly closes.

### 5. Browser and reader runtime identity must be stable across minimize and restore

The host will keep stable entry IDs and preserve the runtime holder for the active entry while it is minimized. For the regular browser, that means preserving the existing page, history, and browser-scoped UI state. For the reader, that means preserving the current document and reading position together with a restorable link back to its source browser entry.

Why:
- Route-level `remember(url)` state cannot distinguish "same live session resumed later" from "fresh session opened on the same URL."
- Telegram's bottom strip reopens the live sheet, not a rebuilt page with copied metadata.

Alternative considered:
- Preserve only URL and title in the minimized strip and recreate runtime on restore.
  - Rejected because it would still feel like reopen, not resume.

### 6. First pass supports one minimized bottom-strip entry, not full multi-entry tabs

This change will keep one minimized browser-family entry at a time. If the user minimizes the active entry while another minimized entry already exists, the implementation can either replace the old entry or close it explicitly as part of the same host transition. Full Telegram-style multi-entry tabs management is deferred.

Why:
- The user asked for Telegram-like minimize and reopen behavior, not a full browser tab manager.
- One minimized slot is enough to establish the correct host architecture while keeping the implementation narrow.

Alternative considered:
- Build a multi-entry bottom tabs overlay immediately.
  - Rejected because it adds state management, preview ordering, and reentry policy that are not required to fix the current mismatch.

## Risks / Trade-offs

- [Host-owned entry persistence increases lifecycle complexity] -> Keep the first pass limited to one minimized entry and release state immediately on true close.
- [Moving reader out of `Dialog` can affect system bar and inset behavior] -> Reuse the existing browser overlay host layer and verify system bar ownership during expanded, minimized, and restored states.
- [Old sheet geometry assumptions may leak into new host transitions] -> Remove partial-height browser resting logic instead of trying to adapt it into minimize semantics.
- [Reader drag and restore jitter may persist if gesture ownership stays split] -> Put reader drag, settle, minimize, and restore under one host-level state machine and keep browser header progress logic out of that path.
- [Keeping scope too broad could stall implementation] -> Limit this change to one minimized entry, explicit minimize or close actions, and full-screen bottom-up browser behavior.

## Migration Plan

1. Introduce a browser-family host model in `BrowserHost` with active and minimized entry state.
2. Move reader presentation into the same host tree and remove dialog-only ownership from the browser-family path.
3. Update browser and reader chrome actions so minimize and close are distinct, and wire those actions through the host.
4. Replace persistent sheet-style browser geometry with a full-screen active browser surface that still opens from the bottom.
5. Preserve active browser or reader runtime identity across minimize and restore, then expose a bottom-strip restore affordance.
6. Verify browser open, minimize, restore, Instant View minimize, and true close behavior together before implementation is considered complete.

## Open Questions

- Should the minimized bottom strip show only title and host, or also keep a lightweight loading indicator and favicon in the first pass?
- When a new minimize request happens while one minimized entry already exists, should the first pass replace the old entry immediately or require explicit close of the old entry?
