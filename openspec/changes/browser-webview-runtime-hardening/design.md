## Context

NativeDo browser now has a shared route-level implementation that is reused by both full-route and sheet containers. `AppBrowserRoute` already owns browser chrome, search, address editing, history, bookmarks, settings, pull-to-refresh in sheet mode, and SSL error prompts, while `AppBrowserWebView` wraps the shared WebView setup. That means popup requests, downloads, permission requests, and page-failure handling should also be solved once in the shared browser runtime instead of being left to default WebView behavior.

Right now the runtime gaps are concentrated in `AppBrowserWebView.kt`: navigation override is wired, but popup or new-window requests are not explicitly handled, download requests are not intercepted, site permission requests are not brokered through Android permissions, and generic main-frame load failures do not surface browser-scoped recovery actions. Since full-route and sheet browser reuse `AppBrowserRoute`, this change can harden both containers at the same time without changing container-selection policy.

## Goals / Non-Goals

**Goals:**
- Add deterministic popup or new-window handling for browser pages so `target="_blank"` and `window.open()` stay inside NativeDo browser routing.
- Add explicit browser-scoped handling for downloads and Web permission requests.
- Add a recoverable browser error surface for main-frame page failures and HTTP failures.
- Keep the runtime hardening shared across full-route and sheet browser containers.

**Non-Goals:**
- Do not redesign browser chrome, container routing policy, or reader support.
- Do not replace the existing SSL dialog flow; the new error model should coexist with it.
- Do not introduce a second in-app browser stack or tab model.
- Do not attempt to replicate Telegram’s full browser infra such as tabs, restricted domains, or browser settings in this change.

## Decisions

### 1. WebView runtime events will be surfaced as semantic browser callbacks
`AppBrowserWebView` will be extended so popup requests, downloads, permission requests, and recoverable failures are emitted upward as explicit browser callbacks instead of being handled ad hoc inside the view wrapper.

Why:
- `AppBrowserRoute` is already the stateful owner for browser interactions and is the right place to coordinate navigation policy, transient dialogs, and bulletins.
- Full-route and sheet containers both go through the same route, so a semantic callback layer avoids duplicating runtime behavior per container.

Alternative considered:
- Handle each case directly inside `AppBrowserWebView` with platform side effects.
  - Rejected because it would push navigation, permissions, and recovery UI out of the browser scene owner and make sheet/full-route behavior diverge.

### 2. Popup or new-window requests will be reclassified through browser routing instead of creating a second WebView stack
Popup requests will be captured through `WebChromeClient.onCreateWindow()` and translated into a browser navigation request that is resolved through the same routing model as normal link clicks. If the request resolves to a browserable URL, it will load in the current browser session; if it resolves to native or external navigation, the route will hand off through the existing link policy.

Why:
- The current app does not have tab management or nested browser stacks.
- Telegram-like behavior in this phase is better approximated by keeping popup navigation inside the current browser flow than by letting WebView defaults decide.

Alternative considered:
- Keep `setSupportMultipleWindows(false)` and rely on default fallback behavior.
  - Rejected because the resulting behavior is inconsistent across sites and device WebView implementations.
- Create a second in-app browser instance for every popup.
  - Rejected because it would implicitly introduce tabs or browser stacking without the surrounding scene model.

### 3. Downloads will use an explicit platform handoff with browser feedback
Browser download requests will be intercepted and handed off through an explicit platform download path. The primary path should use Android’s platform download facilities for normal `http/https` downloads; unsupported or incomplete requests may fall back to system browser handoff. The browser route will show clear user feedback for either path.

Why:
- Silent no-op download taps are one of the most user-visible browser failures.
- A platform handoff keeps the browser feature small while still making download behavior deterministic.

Alternative considered:
- Build a full in-app download manager in this change.
  - Rejected because it is too large for runtime hardening and unnecessary for v1 browser parity.
- Always open downloads in the system browser.
  - Rejected because it makes browser behavior feel inconsistent and throws away useful context such as cookies and user intent.

### 4. Web permission requests will be brokered through route-owned Android permission coordination
WebView permission requests will be mapped to supported Android runtime permissions at the route layer. Supported resources such as camera and microphone will request Android runtime permission when needed and then grant or deny the Web request; unsupported resources will be denied explicitly.

Why:
- Browser-scoped permission behavior must be understandable and safe.
- `AppBrowserRoute` already owns launchers for file chooser and can coordinate pending permission requests without leaking Android dependencies into the ViewModel.

Alternative considered:
- Auto-deny all permission requests.
  - Rejected because it would make browser-hosted upload and media flows unnecessarily broken.
- Let WebView decide without explicit Android permission mapping.
  - Rejected because it produces undefined behavior and poor UX.

### 5. Recoverable page errors will be modeled separately from SSL errors and only for the main frame
The browser route will add a recoverable error surface for main-frame failures such as network or HTTP errors, with actions like retry, copy link, and open in system browser. SSL errors will remain in the existing explicit dialog flow. Subresource failures will not replace the whole page with the error surface.

Why:
- Main-frame failures are the cases users can act on directly.
- Reusing the SSL dialog semantics for all failures would blur security-sensitive errors with ordinary loading issues.

Alternative considered:
- Treat all WebView failures the same way.
  - Rejected because SSL failures have different user risk and recovery semantics.
- Surface subresource failures as full browser errors.
  - Rejected because it would cause excessive false-positive error UI on partially healthy pages.

## Risks / Trade-offs

- [Popup URL extraction may be unreliable on some sites] → Build the popup bridge around explicit callback capture, keep a safe fallback path, and deny unresolved requests instead of creating an uncontrolled second stack.
- [Platform download handoff may not preserve every site-specific header] → Prefer passing available request metadata such as URL, user agent, and cookies where supported, and fall back to system-browser handoff when the platform path is incomplete.
- [Permission coordination can become messy if multiple requests overlap] → Keep only one pending Web permission request at a time and explicitly cancel or deny stale requests.
- [Error UI could appear too aggressively during redirects or transient failures] → Scope the recoverable surface to main-frame failures and clear it as soon as a new top-level load starts.

## Migration Plan

1. Extend `AppBrowserWebView` with semantic callbacks for popup requests, downloads, permission requests, and recoverable errors.
2. Add route-owned pending state and handlers in `AppBrowserRoute` for popup resolution, platform download handoff, Android permission prompts, and browser error UI.
3. Ensure full-route and sheet browser containers both reuse the hardened route behavior without container-specific forks.
4. Add targeted tests for any pure helper logic introduced for runtime coordination.
5. Smoke test popup flows, downloads, permissions, main-frame errors, SSL errors, and sheet/full-route reuse before handoff.

## Open Questions

- Should the first download implementation always prefer `DownloadManager`, or should some content types go straight to system browser handoff?
- Which browser-hosted permission resources do we explicitly support in v1 beyond camera and microphone?
- Do we want a dedicated browser bulletin for blocked popup requests, or is silent denial acceptable for unresolved popup URLs?
