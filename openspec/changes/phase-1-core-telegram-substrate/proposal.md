## Why

`Phase 0` 已经完成了 Telegram-related 文件的分类、GPL provenance 审计和 phase ownership 切分。现在需要进入第一个真正的执行 phase，把当前已经落地但仍有 drift 风险的 core substrate 收口为可接受的 strict subset，并补齐 `NdTheme` / `NdUiConfig` 这类 project-specific runtime substrate 的完成门禁。

如果现在不把这些底座先收紧，后续 `ActionBarLayout`、renderer、runtime 和 feature scene 都会建立在不稳定基线上，继续扩大返工面。

## What Changes

- **BREAKING**: `AndroidUtilities.kt`、`Utilities.kt`、`DispatchQueue.kt`、`FileLog.kt`、`LayoutHelper.kt`、`CubicBezierInterpolator.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt`、`NotificationCenter.kt` 的进一步实现与调整，必须按 `Phase 1` owner change 推进，不再接受脱离 phase gate 的自由修改
- 收紧 Telegram-derived core substrate：保持 adopted subset、禁止额外 convenience wrapper、保留统一 GPL provenance、修正已记录的 drift 边界
- 收紧 project-specific runtime substrate：把 `NdTheme.kt`、`LightTheme.kt`、`DarkTheme.kt`、`NdUiConfig.kt`、`AppLifecycleMonitor.kt` 明确为 NativeDo 自有基础设施，并补齐完成条件与 host smoke gate
- 审视并移除低价值 standalone substrate tests；只保留有明确契约价值或回归保护价值的测试
- 把 `full-rewrite-telegram-parity` 中 `2.x`、`3.x`、`4.x`、`5.x` 相关 backlog 迁移为 `Phase 1` 的实际任务清单

## Capabilities

### New Capabilities
- `telegram-core-substrate`: Telegram-derived utilities, animation primitives, event bus, and layout helpers used as the strict-parity substrate baseline
- `nd-theme-runtime-substrate`: NativeDo-specific theme, UI config, and app lifecycle substrate that Phase 1 owns and verifies before later phases build on it

### Modified Capabilities
- 无

## Impact

- 影响 `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/`
- 影响 `apps/android/app/src/main/java/io/github/baba983/nativedo/core/event/`
- 影响 `apps/android/app/src/main/java/io/github/baba983/nativedo/core/theme/`
- 影响 `apps/android/app/src/test/java/io/github/baba983/nativedo/core/util/` 下的 substrate tests 取舍
- 影响 `full-rewrite-telegram-parity` 中 `2.x`、`3.x`、`4.x`、`5.x` 相关任务的后续 owner 与验收方式
