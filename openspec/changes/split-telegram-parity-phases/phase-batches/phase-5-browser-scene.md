# Phase 5 Browser Scene

## Scope

- shared WebView infrastructure
- `LinkManager.kt`
- `BrowserFragment.kt`
- browser controls / progress / cookie sync / native interception

## Prerequisites

- `Phase 2` navigation complete
- `Phase 4` auth/session/runtime complete

## Exit Gates

- `LinkManager` 仍只做 URL 分类，不接管 fragment navigation
- `BrowserFragment` 与 `LoginFragment` 共享 WebView substrate，但 scene 职责不混合
- smoke 覆盖前进后退、internal link classification、cookie sync

## Carried Backlog

- `full-rewrite-telegram-parity` `25.1-25.8`
