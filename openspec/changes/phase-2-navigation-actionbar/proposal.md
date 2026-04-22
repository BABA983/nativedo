## Why

`Phase 1` 已经把 Telegram-derived substrate 和 NativeDo runtime substrate 收口成可继续演进的基线，但导航和顶栏这层仍然停留在“已落地、未验收”的状态。当前 `ActionBarLayout.kt`、`ActionBar.kt`、`BaseFragment.kt` 虽然可运行，但和 Telegram 的 motion contract、header 结构、命名边界、状态恢复闭环之间仍有明显偏差，继续往后做 feature scene 只会把返工面扩大。

## What Changes

- 收口 `ActionBarLayout.kt` / `INavigationLayout.kt` / `BaseFragment.kt` 的 adopted subset，按 Telegram upstream 重新核对 push/pop 位移、前页左移、左缘 swipe-back、`35%` threshold、velocity dismiss、以及最小状态恢复
- **BREAKING**: 不再接受把当前 `ActionBarLayout` 的“已有位移/阈值逻辑”直接视为 parity 完成；phase2 必须以真实 host smoke 和代码边界审计为准
- 收口 `ActionBar.kt` / `BackDrawable.kt` / `SimpleTextView.kt` / `ActionBarMenu.kt` / `ActionBarMenuItem.kt` 的 adopted subset，明确哪些 Telegram 组件进入本 phase，哪些继续 keep-absent
- **BREAKING**: `ActionBar.kt` 当前 `TextView` 占位、`updateTitleLayout()` / `refreshTheme()` 一类 project-local helper 不能继续作为永久结论；phase2 需要回到 upstream 责任边界
- 保留 `HelloFragment.kt` 仅作为 host smoke 页面，用来验证导航与顶栏行为，不把它扩成产品能力
- 把 `full-rewrite-telegram-parity` 中 `6.x`、`7.x` backlog 收敛为 phase2 的可执行任务和退出门禁

## Capabilities

### New Capabilities
- `telegram-navigation-stack`: Telegram-derived `INavigationLayout` / `BaseFragment` / `ActionBarLayout` adopted subset and its motion/state-restore gate
- `telegram-action-bar`: Telegram-derived `ActionBar` / `BackDrawable` / `SimpleTextView` / menu/search subset and their parity boundary

### Modified Capabilities

## Impact

- 影响 `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/`
- 影响 `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt` 和 `LaunchBootstrap.kt` 的导航宿主接线
- 影响 `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/actionbar/` 的 host smoke / contract tests
- 影响 `full-rewrite-telegram-parity` 中 `6.x`、`7.x` backlog 的 owner、验收方式和完成定义
