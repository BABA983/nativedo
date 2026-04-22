# Phase 4 Runtime / Auth / Discourse Integration

## Scope

- `NdApp.kt`
- `NdRuntime.kt`
- `LaunchBootstrap.kt`
- `Discourse models`
- `DiscourseApi.kt`
- `DiscourseClient.kt`
- `CookieSessionStore.kt`
- `AuthSessionManager.kt`
- `CsrfTokenProvider.kt`
- `CloudflareInterceptor.kt`
- `BootstrapParser.kt`
- `TopicStreamPager.kt`
- `Offline cache`
- startup wiring

## Upstream References

- Telegram host responsibility split around `ApplicationLoader` / `LaunchActivity`
- Telegram account/session/network manager style naming only as architecture influence

## Prerequisites

- `Phase 0` audit complete
- `Phase 1` complete
- `LaunchBootstrap.kt` 当前 `HelloFragment` 占位路径未被误认为完成态

## Exit Gates

- 冷启动必须从 `LaunchBootstrap` 真实决定 `TopicsFragment` 或 `LoginFragment`，不能再停留在 `HelloFragment`
- auth/session/network/bootstrap 采用 contract-heavy verification：JSON、cookie/session、CSRF、HTML parsing、pagination、error normalization
- host 仍保持单 `MainActivity`
- 不重新引入 `Coordinator` 命名或 legacy dual-root 兼容层

## Carried Backlog

- `full-rewrite-telegram-parity` `1.5-1.6`
- `full-rewrite-telegram-parity` `13.1-13.7`
- `full-rewrite-telegram-parity` `14.1-14.6`
- `full-rewrite-telegram-parity` `15.1-15.6`
- `full-rewrite-telegram-parity` `16.1-16.4`
- `full-rewrite-telegram-parity` `17.1-17.4`
- `full-rewrite-telegram-parity` `18.1-18.4`
- `full-rewrite-telegram-parity` `20.1-20.6`
- `full-rewrite-telegram-parity` `21.1-21.6`
- `full-rewrite-telegram-parity` `22.1-22.5`

## Current Findings To Carry Forward

- `NdApp.kt` / `NdRuntime.kt` 是 project-specific host/runtime，不是 Telegram-derived
- `LaunchBootstrap.kt` 当前只服务于 smoke host；Phase 4 是它第一次真正进入产品主链路的 owner phase
