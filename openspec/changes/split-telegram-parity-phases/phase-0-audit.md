# Phase 0 Audit

## 1. Freeze Outcome

- `full-rewrite-telegram-parity` 不再作为后续 Telegram rewrite 的唯一执行清单；它保留总目标、历史背景和原始 backlog 的参考价值
- 新的实现工作必须进入本 change 定义的 phase batch；未进入 phase batch 的新实现不再视为有效推进
- 在对应 phase batch 被明确恢复之前，以下工作保持暂停：
  - `AndroidUtilities` / `Utilities` / `DispatchQueue` / `FileLog` / `NotificationCenter` / `LayoutHelper` 的额外 helper 扩张
  - `ActionBar` / `ActionBarLayout` / `BaseFragment` 的“已经 Telegram parity 完成”式结论
  - `SpringAnimationUtils.kt`、`AndroidUtilitiesTest.kt`、`NotificationCenterTest.kt` 这类无真实 caller / 无回归价值的恢复
  - 任意 feature scene 在 substrate、navigation、runtime phase batch 之前的越级实现

## 2. Telegram-derived GPL Header Standard

- 仓库标准模板文件：`apps/android/app/telegram-derived-gpl-v2-header.kt.txt`
- 每个 `Telegram-derived` 文件必须至少包含：
  - upstream class 或 upstream path
  - source reference
  - Telegram copyright
  - NativeDo copyright
  - SPDX `GPL-2.0-only`
- 当前已审计的 landed `Telegram-derived` 文件均已带有合规头；本轮 Phase 0 不需要额外补头，只需要把该模板继续作为后续 phase 的强制入口门禁

## 3. Current Landed Inventory

| File | Current caller status | Upstream reference | Classification | GPL header | Parity status | Disposition | Audit note |
|---|---|---|---|---|---|---|---|
| `apps/android/app/src/main/java/io/github/baba983/nativedo/NdApp.kt` | `NativeDoApplication.kt` | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | App bootstrap host，不能伪装成 `ApplicationLoader` port |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/NdRuntime.kt` | `NdApp.kt`、`NativeDoApplication.kt` | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | 单 runtime/service registry，不宣称为 `AccountInstance` port |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApplication.kt` | launcher app | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | 仅负责调用 `NdApp.init()` |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/LaunchBootstrap.kt` | `MainActivity.kt` startup path | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `rework` | 当前只挂 `HelloFragment`，Phase 4 必须替换为真实 session/bootstrap 决策 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/AndroidUtilities.kt` | `NdRuntime.kt`、`NdTheme.kt`、`ActionBarLayout.kt`、`BackDrawable.kt`、`ActionBar.kt`、`HelloFragment.kt` | `org.telegram.messenger.AndroidUtilities` | `Telegram-derived` | present | `documented divergence` | `keep and tighten` | `runOnUIThread()` 仍归本类；`initializedContext` 是 Android bootstrap 偏离，已记录 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/Utilities.kt` | `AndroidUtilities.kt` | `org.telegram.messenger.Utilities` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 只保留数学与少量 non-UI-thread helper |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/DispatchQueue.kt` | `FileLog.kt` | `org.telegram.messenger.DispatchQueue` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 不额外包第二层 queue helper |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/FileLog.kt` | `NdRuntime.kt`、`LaunchBootstrap.kt`、`AndroidUtilities.kt` | `org.telegram.messenger.FileLog` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 继续以 Telegram 风格主入口为准，不扩张 tag-first 风格 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/LayoutHelper.kt` | 无外部 caller | `org.telegram.ui.Components.LayoutHelper` | `Telegram-derived` | present | `documented divergence` | `rework` | `createScroll()` 偏离已记录，但当前无 caller，保持冻结直到 Phase 1 恢复 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/CubicBezierInterpolator.kt` | `NdTheme.kt`、`ActionBarLayout.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt` | `org.telegram.ui.Components.CubicBezierInterpolator` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 当前常量集合已回到 Telegram 命名 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/AnimatedFloat.kt` | 无外部 caller | `org.telegram.ui.Components.AnimatedFloat` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 当前无真实 consumer，Phase 1 前不再扩张 API |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/AnimatedColor.kt` | 无外部 caller | `org.telegram.ui.Components.AnimatedColor` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 当前无真实 consumer，Phase 1 前不再扩张 API |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/event/NotificationCenter.kt` | `NdRuntime.kt`、`NdTheme.kt`、`AppLifecycleMonitor.kt`、`MainActivity.kt`、`ActionBarLayout.kt`、`ActionBar.kt`、`HelloFragment.kt` | `org.telegram.messenger.NotificationCenter` | `Telegram-derived` | present | `documented divergence` | `keep and tighten` | 只允许预定义 global event ids，阻止 screen-local event drift |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/event/AppLifecycleMonitor.kt` | `NdRuntime.kt` | 无 Telegram 同名公共类 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | NativeDo 全局基础设施，不再额外长 typed wrapper |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/theme/NdTheme.kt` | `MainActivity.kt`、`ActionBar.kt`、`ActionBarLayout.kt`、`HelloFragment.kt` | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | 是 `Theme.java` 思路借鉴，不是同名 strict port |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/theme/NdUiConfig.kt` | `NdRuntime.kt`、`NdTheme.kt`、`ActionBar.kt` | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | 只保留极小的全局 UI flag 子集 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/INavigationLayout.kt` | `MainActivity.kt`、`ActionBarLayout.kt`、`LaunchBootstrap.kt` | `org.telegram.ui.ActionBar.INavigationLayout` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | Adopted minimal interface，可继续作为 Phase 2 基线 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/BaseFragment.kt` | `ActionBarLayout.kt`、`LaunchBootstrap.kt`、`HelloFragment.kt` | `org.telegram.ui.ActionBar.BaseFragment` | `Telegram-derived` | present | `documented divergence` | `keep and tighten` | page scope 和 host wrapping 属于当前 adopted subset |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/ActionBarLayout.kt` | `MainActivity.kt` root、`LaunchBootstrap.kt` | `org.telegram.ui.ActionBar.ActionBarLayout` | `Telegram-derived` | present | `documented divergence` | `rework` | 代码里已有 push/pop、threshold、velocity 逻辑，但尚未通过 Telegram motion contract 验证，state restore 也未闭合 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/BackDrawable.kt` | `ActionBar.kt` | `org.telegram.ui.ActionBar.BackDrawable` | `Telegram-derived` | present | `strict subset` | `keep and tighten` | 保持最小 back/close 态，不额外引入非必要 icon helper |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/ActionBar.kt` | `BaseFragment.kt`、`HelloFragment.kt` | `org.telegram.ui.ActionBar.ActionBar` | `Telegram-derived` | present | `documented divergence` | `rework` | 当前仍用 `TextView`，且存在 `updateTitleLayout()` / `refreshTheme()` 私有漂移点，Phase 2 必须重审 |
| `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/HelloFragment.kt` | `LaunchBootstrap.kt` | 无 | `Telegram-inspired project-specific` | N/A | `not parity` | `keep and tighten` | 仅作为 host smoke fragment，不是产品页面，不参与 parity 宣称 |

## 4. Keep-Absent Decisions

| File | Current state | Disposition | Note |
|---|---|---|---|
| `apps/android/app/src/main/java/io/github/baba983/nativedo/core/util/SpringAnimationUtils.kt` | absent | `delete / keep absent` | 无真实 consumer 前不恢复 |
| `apps/android/app/src/test/java/io/github/baba983/nativedo/core/util/AndroidUtilitiesTest.kt` | absent | `delete / keep absent` | 无回归保护价值前不恢复 |
| `apps/android/app/src/test/java/io/github/baba983/nativedo/core/event/NotificationCenterTest.kt` | absent | `delete / keep absent` | 无回归保护价值前不恢复 |

## 5. Approved Divergence Register

| Upstream | Current file | Reason | Boundary | Affected callers |
|---|---|---|---|---|
| `org.telegram.messenger.AndroidUtilities` | `AndroidUtilities.kt` | Android 端需要显式 app-context bootstrap 和 `ComponentCallbacks` 注册 | 仅限 `init(context)` / `initializedContext` 守卫；公开工具与 UI-thread API 仍按 adopted subset 对齐 | `NdRuntime.kt` 和所有现有工具调用方 |
| `org.telegram.ui.Components.LayoutHelper` | `LayoutHelper.kt` | Android SDK 公开类型限制，`createScroll()` 不能直接依赖 Telegram 使用的 concrete type | 仅限 `createScroll()` 的底层返回类型实现；gravity / margin 语义保持 Telegram 对齐 | 当前无 caller；未来 scroll host |
| `org.telegram.messenger.NotificationCenter` | `NotificationCenter.kt` | 必须强制限制为 truly global events，避免把 page-local state 塞进全局总线 | 只允许预定义 event ids，不开放任意 int id 注册 | `NdTheme.kt`、`AppLifecycleMonitor.kt`、`MainActivity.kt`、`ActionBarLayout.kt`、`ActionBar.kt`、`HelloFragment.kt` |

## 6. Rework And Removal Queue

- `ActionBarLayout.kt` 不能在 Phase 2 host smoke 之前被标记为“Telegram parity 完成”；必须实测 push/pop 位移、前页左移、左缘 swipe-back、`35%` 阈值、velocity dismiss、以及状态恢复
- `ActionBar.kt` 当前 `TextView` 标题实现和 `updateTitleLayout()` / `refreshTheme()` 私有 helper 只允许视为临时宿主实现；Phase 2 必须回到 upstream 责任边界重新审查
- `LaunchBootstrap.kt` 当前返回 `HelloFragment()` 只是 smoke scaffolding；Phase 4 必须改为 `AuthSessionManager` + `TopicsFragment` / `LoginFragment` 的真实冷启动决策
- `LayoutHelper.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt` 当前没有真实业务 caller；在首个 consumer 出现前，不再扩张这些文件的 public surface
