## Context

`Phase 0` 已经完成了 Telegram-related 文件分类、GPL provenance 审计、approved divergence register 和 phase ownership 切分。当前 `Phase 1` 范围内的代码并不是空白状态，而是已经有一批 landed substrate：

- Telegram-derived：`AndroidUtilities.kt`、`Utilities.kt`、`DispatchQueue.kt`、`FileLog.kt`、`LayoutHelper.kt`、`CubicBezierInterpolator.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt`、`NotificationCenter.kt`
- NativeDo project-specific：`AppLifecycleMonitor.kt`、`NdTheme.kt`、`LightTheme.kt`、`DarkTheme.kt`、`NdUiConfig.kt`

这些文件当前最大的问题不是“完全没写”，而是 owner boundary 还没有真正收口：有的文件还存在 drift 风险，有的文件没有真实 caller 但 public surface 已经长出来，有的 standalone substrate test 与 Phase 0 刚定下的测试策略冲突。

## Goals / Non-Goals

**Goals:**
- 收紧当前 landed Telegram-derived core substrate，使其继续停留在 approved strict subset / documented divergence 边界内
- 明确 `NdTheme` / `NdUiConfig` / `AppLifecycleMonitor` 这类 NativeDo-specific runtime substrate 的 owner boundary 和完成门禁
- 清理或保留 substrate tests 时只依据 parity/regression 价值，而不是覆盖率冲动
- 为 `Phase 2` 之前的所有后续工作提供一个稳定、可验证的 substrate 基线

**Non-Goals:**
- 不在这个 phase 中实现 `ActionBar`、`ActionBarLayout`、`BaseFragment` 等导航能力
- 不在这个 phase 中提前实现 `RecyclerListView`、`ImageReceiver`、`CookedHtmlRenderer` 或 feature scenes
- 不把 `NdTheme`、`NdUiConfig`、`AppLifecycleMonitor` 伪装成 Telegram strict ports
- 不因为“Phase 1 已开始”就解冻 `LayoutHelper.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt` 的 public surface 扩张；只有真实 caller 出现时才恢复

## Decisions

### D1. Phase 1 以“收口 landed substrate”为主，不重做整包移植

本 phase 默认基于当前已经 landed 的 substrate 继续推进，而不是重新从 Telegram 或从空白代码再写一遍。只有当某个文件已经被 `Phase 0` 标记为 `rework` 且现状明显无法作为后续基线时，才做局部修正或删除。

这样做的原因：
- 大部分 Phase 1 文件已经存在，重复重做只会增加噪音
- 当前真正需要的是减少 drift 面，而不是制造第二份实现

不采用的替代方案：
- 完整重做一遍 `Phase 1` 范围内所有文件。这个方案没有必要，也会让 diff 膨胀。

### D2. Telegram-derived 文件继续按 approved subset 维护，不新增 convenience wrapper

`AndroidUtilities.kt`、`Utilities.kt`、`DispatchQueue.kt`、`FileLog.kt`、`LayoutHelper.kt`、`CubicBezierInterpolator.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt`、`NotificationCenter.kt` 继续按 `Phase 0` 审计结论推进：

- 公开 API 只保留 adopted subset
- 保持现有 GPL provenance 头
- 不因“更 Kotlin”或“更方便”增加 project-local wrapper

已登记的 documented divergence 只允许维持在原边界：
- `AndroidUtilities.kt` 的 app-context bootstrap / `initializedContext`
- `LayoutHelper.kt` 的 `createScroll()` 平台类型限制
- `NotificationCenter.kt` 的 predefined global event id 边界

### D3. 无真实 caller 的 substrate 继续冻结

`LayoutHelper.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt` 当前没有真实业务 caller。Phase 1 不把“已经进入 owner phase”理解为允许继续扩 public surface；相反，它们继续保持冻结，直到真实 consumer 出现并把新增 surface 绑定到具体使用场景。

不采用的替代方案：
- 先把 API 补全得更像 Telegram。这个方案会让无 caller 的 API 漫出来，回到 `Phase 0` 想阻止的问题。

### D4. Theme/runtime substrate 作为 project-specific owner 收口

`NdTheme.kt`、`LightTheme.kt`、`DarkTheme.kt`、`NdUiConfig.kt`、`AppLifecycleMonitor.kt` 属于 NativeDo-specific substrate，不参加 Telegram-derived 判定，但本 phase 需要把它们收口到可以支撑后续 phase 的完成态：

- `NdTheme` 负责 color keys、mode persistence、runtime switching、drawable helpers、`themeDidChange`
- `NdUiConfig` 只持有最小 UI flags，不继续扩散为全局配置仓库
- `AppLifecycleMonitor` 只负责 truly global foreground/network state，不长 typed event wrapper

### D5. Standalone substrate tests 只保留确有 parity / regression 价值的极少数

本 phase 不按“substrate 都该有单测”推进。测试取舍规则是：

- 若测试只是在验证线程/工具类的常规行为、没有实际回归历史，也不保护特别反直觉的 adopted parity 语义，则删除
- 若测试保护了已登记的 parity edge case 或者已知回归风险，可以保留

当前直接执行策略：
- `DispatchQueueTest.kt` 作为低价值 standalone substrate test 删除
- `UtilitiesTest.kt` 只在其继续表达 adopted parity edge case 时保留；否则一并删除

### D6. Phase 1 完成依赖 focused verification，而不是覆盖率

本 phase 的 gate 由两部分组成：

- focused unit/integration verification：仅针对保留下来的有价值测试
- host smoke verification：通过当前宿主验证 theme/event/util/runtime 基元仍可运行

不采用的替代方案：
- 要么全删测试，要么给每个 helper 补更多测试。这两种都不符合 `Phase 0` 定下的策略。

## Risks / Trade-offs

- 只做收口而不重做，可能会保留少量历史形状 → 通过 approved divergence register 和冻结策略约束它们不继续扩散
- 删除 standalone substrate tests 会减少表面测试数 → 通过保留真正有 parity / regression 价值的测试降低风险
- `NdTheme` 继续作为 project-specific substrate，意味着它不会拥有 Telegram 同名参考带来的“自动正确”错觉 → 通过明确 owner boundary 和 host smoke gate 管住它

## Migration Plan

1. 建立 `Phase 1` change，并把 owner scope 固定在 util / event / theme substrate
2. 对照 `Phase 0` 审计结果，确认哪些 Telegram-derived 文件只需 keep-and-tighten，哪些需要继续冻结
3. 清理低价值 standalone substrate tests，只保留 justified parity/regression tests
4. 校验 `NdTheme` / `NdUiConfig` / `AppLifecycleMonitor` 的当前实现是否已满足 Phase 1 gate，必要时做最小修正
5. 跑 focused verification，确认 `Phase 1` substrate 可以作为 `Phase 2` 的依赖基线

## Open Questions

- `UtilitiesTest.kt` 最终是否保留，取决于它是否仍然表达 adopted parity edge case；若只是给 helper 补覆盖率，应在本 phase 删除
