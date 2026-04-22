# Phase Plan

## 1. Umbrella Status

- `full-rewrite-telegram-parity` 现在是 umbrella reference，不再是唯一执行清单
- 新实现必须进入下面的 phase batch；没有 owner phase 的实现不再被视为有效推进
- `Phase 0` 由当前 `split-telegram-parity-phases` change 自身承担，后续 `Phase 1-6` 按 batch 推进

## 2. Task Group Mapping From `full-rewrite-telegram-parity`

| Full rewrite task group | Owner phase batch | Note |
|---|---|---|
| `0. Ground Rules` | `Phase 0` | 作为 umbrella 级治理边界保留 |
| `1. Project Scaffolding & Build Config` | `Phase 0` + `Phase 4` | `1.1-1.4` 归治理与脚手架，`1.5-1.6` 归 runtime / host |
| `2. Core Utilities` | `Phase 1` | Telegram substrate 核心 |
| `3. Animation Primitives` | `Phase 1` | Telegram substrate 核心 |
| `4. Event Bus` | `Phase 1` | Telegram substrate 核心 |
| `5. Theme Engine` | `Phase 1` | `NdTheme` / `NdUiConfig` 与 runtime visual gate 一起处理 |
| `6. Navigation Stack` | `Phase 2` | `ActionBarLayout` / `BaseFragment` 核心闭环 |
| `7. ActionBar Component` | `Phase 2` | 顶栏与导航闭环一起推进 |
| `8. RecyclerListView` | `Phase 3` | 交互型渲染 substrate |
| `9. Canvas Cells` | `Phase 3` | 渲染 substrate |
| `10. Bottom Sheet` | `Phase 3` | 非首阶段 UI substrate |
| `11. Avatar Drawable` | `Phase 3` | 渲染 substrate |
| `12. Image Pipeline` | `Phase 3` | Cell / media substrate |
| `13. Discourse Models` | `Phase 4` | 合约型 runtime 依赖 |
| `14. Discourse Client` | `Phase 4` | 合约型 runtime 依赖 |
| `15. Cookie Session` | `Phase 4` | 合约型 runtime 依赖 |
| `16. CSRF Token Provider` | `Phase 4` | 合约型 runtime 依赖 |
| `17. Cloudflare Guard` | `Phase 4` | 合约型 runtime 依赖 |
| `18. Bootstrap Parser` | `Phase 4` | 合约型 runtime 依赖 |
| `19. Cooked HTML Renderer` | `Phase 3` | 渲染 substrate，但依赖 Phase 4 的 fixture / contract 输入 |
| `20. Topic Stream Pager` | `Phase 4` | 业务 runtime 依赖 |
| `21. Offline Cache` | `Phase 4` | 业务 runtime 依赖 |
| `22. App Startup Wiring` | `Phase 4` | `LaunchBootstrap` / auth bootstrap 主链路 |
| `23. Topics Screen` | `Phase 5 Topics` | 单页面 batch |
| `24. Topic Detail Screen` | `Phase 5 Topic Detail` | 单页面 batch |
| `25. Browser Screen` | `Phase 5 Browser` | 单页面 batch |
| `26. Profile Screen` | `Phase 5 Profile` | 单页面 batch |
| `27. Search Screen` | `Phase 5 Search` | 单页面 batch |
| `28. Login Screen` | `Phase 5 Login` | 单页面 batch |
| `29. NdComposeTheme Bridge` | `Phase 6` | 最终整合/补桥接 |
| `30. Integration & Cleanup` | `Phase 6` | `30.1` root tab shell 也留到 scene batch 落地之后 |

## 3. Current Pause List

- `Phase 1` 之外，不再继续改 `AndroidUtilities.kt`、`Utilities.kt`、`DispatchQueue.kt`、`FileLog.kt`、`NotificationCenter.kt`、`LayoutHelper.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt`
- `Phase 2` 之外，不再继续把 `ActionBar.kt` / `ActionBarLayout.kt` 的现状描述成“已经 Telegram parity 完成”
- `Phase 3` 之外，不提前实现 `RecyclerListView`、cells、`ImageReceiver`、`CookedHtmlRenderer`
- `Phase 4` 之外，不继续扩张 `LaunchBootstrap.kt`、auth/session/network/bootstrap 的实际业务逻辑
- `Phase 5` 必须按页面 batch 推进；不再把 Topics/Detail/Browser/Profile/Search/Login 重新塞回一个统一 feature 清单

## 4. Phase Batches

- [Phase 1 Core Telegram Substrate](./phase-batches/phase-1-core-telegram-substrate.md)
- [Phase 2 Navigation & ActionBar](./phase-batches/phase-2-navigation-actionbar.md)
- [Phase 3 Cells / Media / Rendering Substrate](./phase-batches/phase-3-cells-media-rendering.md)
- [Phase 4 Runtime / Auth / Discourse Integration](./phase-batches/phase-4-runtime-auth-discourse.md)
- [Phase 5 Topics Scene](./phase-batches/phase-5-topics-scene.md)
- [Phase 5 Topic Detail Scene](./phase-batches/phase-5-topic-detail-scene.md)
- [Phase 5 Browser Scene](./phase-batches/phase-5-browser-scene.md)
- [Phase 5 Profile Scene](./phase-batches/phase-5-profile-scene.md)
- [Phase 5 Search Scene](./phase-batches/phase-5-search-scene.md)
- [Phase 5 Login Scene](./phase-batches/phase-5-login-scene.md)
- [Phase 6 Polish & Cleanup](./phase-batches/phase-6-polish-cleanup.md)

## 5. Readiness Rule

- `Phase 1` ready only after `Phase 0` audit, classification, provenance, and disposition are complete
- `Phase 2` ready only after `Phase 1` 的 substrate owner 明确恢复对应文件
- `Phase 3` ready only after `Phase 1-2` 的 substrate / navigation gate 满足
- `Phase 4` ready only after `Phase 1` 的 core substrate gate 满足
- `Phase 5` scenes ready only after `Phase 3-4` 的渲染与 runtime gate 满足
- `Phase 6` ready only after 场景 batch 已经落地，不再存在越级未归属任务
