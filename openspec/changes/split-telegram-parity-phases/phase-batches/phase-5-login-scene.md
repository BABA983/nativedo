# Phase 5 Login Scene

## Scope

- `LoginFragment.kt`
- login WebView scene
- cookie detection / post-login restore / auto-login

## Prerequisites

- `Phase 4` auth/session/bootstrap complete
- `Phase 5 Browser` 的共享 WebView substrate 已可复用

## Exit Gates

- `LoginFragment` 继续保持独立 scene，不退化成 “Browser open /login”
- post-login flow 必须经过 `syncFromWebView()` + `AuthSessionManager`
- smoke 覆盖 login、auto-login、session verification fallback

## Carried Backlog

- `full-rewrite-telegram-parity` `28.1-28.6`
