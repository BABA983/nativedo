## Why

NativeDo browser chrome and container routing have matured, but the shared WebView runtime still has user-visible gaps: `target="_blank"` and `window.open()` are not handled deterministically, downloads and site permission requests have no browser-scoped flow, and page failures do not consistently surface recoverable actions. Now that both full-route and sheet browser containers reuse `AppBrowserRoute`, these runtime gaps should be closed once at the shared browser layer instead of being left to WebView defaults.

## What Changes

- Add deterministic browser runtime handling for popup or new-window requests triggered by `target="_blank"` and `window.open()` so they stay inside the browser routing model instead of falling back to undefined WebView behavior.
- Add browser-scoped handling for download requests and Web permission requests so browser pages can request file downloads, camera, and microphone access through explicit app behavior.
- Add a recoverable browser error surface for main-frame load failures and HTTP failures with actions such as retry, copy link, and open in system browser.
- Keep the change scoped to runtime hardening for the shared browser route and shared WebView wrapper; do not change browser container selection rules or top-bar scene structure.

## Capabilities

### New Capabilities
- `browser-webview-runtime`: Defines deterministic handling for browser popup requests, downloads, Web permission requests, and recoverable page-error flows in NativeDo browser containers.

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserWebView.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
- Affected systems:
  - Shared WebView runtime behavior across full-route and sheet browser containers
  - Browser-scoped download, permission, and error recovery flows
  - Browser verification coverage for WebView runtime edge cases
