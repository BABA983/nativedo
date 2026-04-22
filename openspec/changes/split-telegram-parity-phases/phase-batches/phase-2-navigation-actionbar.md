# Phase 2 Navigation & ActionBar

## Scope

- `INavigationLayout.kt`
- `BaseFragment.kt`
- `ActionBarLayout.kt`
- `BackDrawable.kt`
- `ActionBar.kt`
- `SimpleTextView.kt`
- `ActionBarMenu.kt`
- `ActionBarMenuItem.kt`
- `HelloFragment.kt` 仅作为 host smoke，不作为产品能力

## Upstream References

- `org.telegram.ui.ActionBar.INavigationLayout`
- `org.telegram.ui.ActionBar.BaseFragment`
- `org.telegram.ui.ActionBar.ActionBarLayout`
- `org.telegram.ui.ActionBar.BackDrawable`
- `org.telegram.ui.ActionBar.ActionBar`

## Prerequisites

- `Phase 0` audit complete
- `Phase 1` core substrate gate complete
- 所有对 `ActionBarLayout` / `ActionBar` 的 parity 完成宣称暂停，直到本 phase 明确恢复

## Exit Gates

- `ActionBarLayout` 必须用 host smoke 验证 push/pop 位移、前页左移、edge swipe-back、`35%` threshold、velocity dismiss、以及系统栏联动
- `ActionBarLayout` 的最小状态恢复必须闭合，不能再停留在“后面再补”
- `ActionBar` 必须明确是否继续 strict parity subset；`TextView` 占位实现和 `updateTitleLayout()` / `refreshTheme()` 私有 helper 不能作为永久结论
- `SimpleTextView`、menu/search 模式是否进入 adopted subset，必须由本 phase 明确，不再模糊挂起

## Carried Backlog

- `full-rewrite-telegram-parity` `6.1-6.9`
- `full-rewrite-telegram-parity` `7.1-7.6`

## Current Findings To Carry Forward

- `ActionBarLayout.kt` 代码中已有 threshold 和 velocity 逻辑，但没有通过 Telegram motion contract 验证，先按 `rework` 处理
- `ActionBar.kt` 已 landed，但仍存在明显临时宿主形态，不应继续无约束扩张
