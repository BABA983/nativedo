# Phase 1 Core Telegram Substrate

## Scope

- `AndroidUtilities.kt`
- `Utilities.kt`
- `DispatchQueue.kt`
- `FileLog.kt`
- `LayoutHelper.kt`
- `CubicBezierInterpolator.kt`
- `AnimatedFloat.kt`
- `AnimatedColor.kt`
- `NotificationCenter.kt`
- `AppLifecycleMonitor.kt`
- `NdTheme.kt`
- `NdUiConfig.kt`

## Upstream References

- `org.telegram.messenger.AndroidUtilities`
- `org.telegram.messenger.Utilities`
- `org.telegram.messenger.DispatchQueue`
- `org.telegram.messenger.FileLog`
- `org.telegram.messenger.NotificationCenter`
- `org.telegram.ui.Components.LayoutHelper`
- `org.telegram.ui.Components.CubicBezierInterpolator`
- `org.telegram.ui.Components.AnimatedFloat`
- `org.telegram.ui.Components.AnimatedColor`

## Prerequisites

- `Phase 0` audit complete
- 所有 landed Telegram-derived 文件已经定性并确认 GPL provenance 状态
- `LayoutHelper.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt` 这类当前无 caller 的文件继续冻结，直到本 phase 显式恢复

## Exit Gates

- 仅保留 documented parity subset，不新增 project-local convenience wrapper
- 所有 `Telegram-derived` 文件继续使用标准 GPL header
- `LayoutHelper.createScroll()` 的平台偏离仅限已登记边界
- host smoke 能验证 theme/event/util/runtime 基元可运行
- 不以 standalone unit test 覆盖率作为完成标准；仅保留有真实契约价值的测试

## Carried Backlog

- `full-rewrite-telegram-parity` `2.1-2.8`
- `full-rewrite-telegram-parity` `3.1-3.5`
- `full-rewrite-telegram-parity` `4.1-4.6`
- `full-rewrite-telegram-parity` `5.1-5.9`

## Current Findings To Carry Forward

- `LayoutHelper.kt` 目前无外部 caller，先以 rework 结论冻结
- `AnimatedFloat.kt` / `AnimatedColor.kt` 已 landed 但尚无真实 consumer，先保持 strict subset，不扩张 surface
- `NdTheme.kt` 已先于 checklist 部分落地，但它属于 project-specific theme engine，不应反向定义 Telegram-derived 规则
