## 1. Shared WebView Runtime Event Surface

- [x] 1.1 Extend `AppBrowserWebView.kt` with semantic callbacks for popup or new-window requests, downloads, Web permission requests, and recoverable page failures
- [x] 1.2 Add any small browser runtime models or helpers needed to represent popup, download, permission, and error events without leaking raw WebView details into the ViewModel layer

## 2. Browser Route Runtime Coordination

- [x] 2.1 Update `AppBrowserRoute.kt` to resolve popup or new-window requests through the existing browser navigation policy and current browser session flow
- [x] 2.2 Add browser-scoped download handling with explicit user feedback and a defined fallback path when the preferred handoff cannot be completed
- [x] 2.3 Add route-owned Web permission coordination so supported camera or microphone requests map to Android permission results and unsupported requests are denied explicitly
- [x] 2.4 Add a recoverable main-frame error surface with retry, copy link, and external-open actions while keeping the existing SSL dialog flow distinct

## 3. Container Reuse And Verification

- [x] 3.1 Verify full-route and sheet browser containers both inherit the hardened WebView runtime behavior without container-specific forks
- [x] 3.2 Add or update targeted tests for any pure helper logic introduced for popup routing, download fallback selection, permission coordination, or recoverable error state derivation
- [ ] 3.3 Manually smoke test popup requests, downloads, permission prompts, main-frame failures, SSL failures, and sheet or full-route browser reuse before implementation handoff
