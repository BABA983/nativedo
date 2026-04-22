## Context

`Phase 0` 已经把 `INavigationLayout.kt`、`BaseFragment.kt`、`ActionBarLayout.kt`、`BackDrawable.kt`、`ActionBar.kt` 审计为 Telegram-derived phase2 owner，并明确：

- `ActionBarLayout.kt` 当前虽然已有 push/pop、threshold、velocity 逻辑，但没有经过 Telegram motion contract 和状态恢复闭环验证，仍按 `rework` 处理
- `ActionBar.kt` 当前仍是临时宿主形态：标题和副标题仍用 `TextView`，还引入了 `updateTitleLayout()` / `refreshTheme()` 这类 phase0 已明确不该固化的 drift helper
- `HelloFragment.kt` 只是 host smoke 页面，不应被扩成产品能力

当前 NativeDo 的 Phase 2 代码已经能够运行，但偏差主要集中在两类：

- 导航层：现有 `ActionBarLayout` 只有 lifecycle 单测，没有真正保护 push/pop 位移、前页左移、左缘手势、`35%` threshold、velocity dismiss、以及最小状态恢复
- 顶栏层：现有 `ActionBar` 的结构和 Telegram upstream 责任边界不一致，导致 top bar 看起来不像 Telegram，同时命名和私有 helper 继续漂移

## Goals / Non-Goals

**Goals:**
- 让 `ActionBarLayout` 回到 Telegram adopted subset 的导航契约，而不是停留在“当前代码里看起来有这些逻辑”
- 为 `ActionBarLayout` 补上最小 stack save/restore，覆盖配置变更和进程重建后的恢复路径
- 让 `ActionBar` 的 header 结构、title/subtitle 渲染方式、back area 和 theme 更新边界回到更接近 Telegram 的形状
- 明确 `SimpleTextView`、`ActionBarMenu`、`ActionBarMenuItem`、search mode 在当前 phase 的 adopted subset 边界，不再模糊挂起

**Non-Goals:**
- 不在本 phase 引入 Telegram 全量 `ActionBarLayout` 能力，如 preview mode、drawer、bubble、sheet、predictive back 等
- 不把 `HelloFragment` 改造成真实产品页面
- 不提前实现后续 feature scene 才会使用的 menu/search 复杂行为
- 不新增 screen router、window host 或其他 phase0 已否决的宿主抽象

## Decisions

### D1. Phase 2 以“重审并收口已 landed 的导航/顶栏子集”为主，不做全量翻译

当前代码不是从零开始，Phase 2 的工作是把它收口到可验证的 adopted subset。`ActionBarLayout` 和 `ActionBar` 只恢复当前 host 真正需要的 Telegram 责任边界，不追求把 upstream 所有分支一次性带过来。

这样做的原因：
- 用户当前指出的问题集中在 motion contract、topbar 结构和 restore gate，不在于缺少 Telegram 全量高级分支
- 一次性把 preview/sheet/search/menu 全搬进来，会明显放大 diff 和 drift 面

不采用的替代方案：
- 直接完整 port upstream `ActionBarLayout` / `ActionBar`。对当前仓库和当前 host 来说过大，且会引入很多无 caller 的分支

### D2. `ActionBarLayout` 需要回到 Telegram 的前后页双层转场语义

当前实现虽然有 `translationX`、`BackgroundShiftFactor`、`SwipeDismissThreshold` 和 velocity 判断，但 Phase 2 不能只接受“代码里存在这些常量”。实现需要围绕 Telegram 的 adopted subset 重新闭合这几个行为：

- push: 新页从右进入，上一页左移，系统栏状态跟随 top fragment
- pop: 当前页右移退出，前页复位，fragment lifecycle / transition hooks 对齐
- swipe-back: 左缘触发、跟手位移、`35%` threshold 或 velocity dismiss、取消动画
- restore: stack 中每个 fragment 的最小 descriptor 与 args 可以保存并恢复

Phase 2 不引入 Telegram 全量 `containerView/containerViewBack` 所有分支，但会把当前 NativeDo 实现收口到同样的前后页转场语义和 host restore contract。

### D3. 最小状态恢复采用“fragment descriptor + args bundle + bootstrap registry”闭环

为了避免发明新的 router 或反射恢复系统，Phase 2 的 restore 采用最小协议：

- `BaseFragment` 暴露最小可恢复 descriptor
- `ActionBarLayout` 只负责保存和恢复 stack entries
- `LaunchBootstrap` / 当前宿主提供 descriptor 到 fragment 的恢复入口

这样做的原因：
- 满足 phase2 明确要求的 stack save/restore
- 不需要引入额外路由层
- 后续 phase 只需要给新 fragment 注册自己的 descriptor 恢复逻辑

不采用的替代方案：
- 用类名反射恢复 fragment。风险高，且不符合当前仓库追求的清晰可维护边界

### D4. `ActionBar` 采用 Telegram-like structure，title/subtitle 迁回 `SimpleTextView`

当前 `ActionBar.kt` 最大 drift 是：

- `TextView`/`LinearLayout` 宿主结构代替了 Telegram 的 `SimpleTextView` title/subtitle 语义
- `updateTitleLayout()` / `refreshTheme()` 这种 project-local helper 继续扩大了非 upstream 责任

Phase 2 的 adopted subset 会把 `ActionBar` 收口到：

- back button area
- title/subtitle `SimpleTextView`
- 右侧 menu 容器的最小结构
- 基于 `NdTheme` 的 background/title/subtitle/icon 配色更新

这一步不要求完整搜索模式，也不要求 popup submenu，但 title/subtitle/back/menu 区域布局不能继续靠当前临时实现维持。

### D5. `ActionBarMenu` / `ActionBarMenuItem` / search mode 以“明确边界”为主

Phase batch 要求它们不能继续模糊挂起。当前没有真实 caller 的前提下，Phase 2 采取：

- 若 `ActionBar` 当前最小 adopted subset 需要 menu 容器，则实现最小 `ActionBarMenu` / `ActionBarMenuItem` 结构
- search mode 若无 caller，则显式保持 absent，不再通过伪 API 或占位 helper 宣称已支持

不采用的替代方案：
- 继续把 menu/search 写在 backlog 里但不做边界决定。这样会让后续任何 header 改动继续漂移

## Risks / Trade-offs

- [导航语义收口后，当前 `HelloFragment` 视觉 smoke 仍可能不明显] → 补充更贴近 motion contract 的 contract test，并在 host smoke 中显式验证 push/pop/swipe/restore
- [最小 restore 协议会给 `BaseFragment` 增加一点恢复责任] → 只保留 descriptor/args 子集，不扩成通用导航框架
- [迁回 `SimpleTextView` 会带来 ActionBar 测量/布局实现成本] → 只 port title/subtitle/back/menu 当前需要的子集，避免引入 upstream 其余装饰能力
- [menu/search 本 phase 只明确边界，后续真实页面仍可能追加能力] → 在 spec 中明确 adopted subset 与 keep-absent 范围，避免误判为 drift

## Migration Plan

1. 建立 `Phase 2` change，并把 `6.x/7.x` backlog 迁成导航与顶栏两个 capability
2. 先收口 `ActionBarLayout` motion contract 与 restore contract，再更新对应测试和 host smoke
3. 再收口 `ActionBar` header 结构，迁入 `SimpleTextView`，去掉当前临时 helper drift
4. 明确 menu/search 在当前 host 下的 adopted subset 或 keep-absent 决策
5. 运行 focused unit tests 和 host build 验证，确认 phase2 可继续作为后续 scene 的基础

## Open Questions

- `ActionBarMenu` / `ActionBarMenuItem` 在当前无 caller 的情况下，是只实现最小空容器结构，还是继续 keep-absent；这要以当前 `ActionBar` 重构后的实际需要为准
