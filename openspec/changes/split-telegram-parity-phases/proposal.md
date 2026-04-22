## Why

`full-rewrite-telegram-parity` 把 Telegram substrate、导航、主题、Discourse 业务层和页面重建都放进了同一个超大任务集里，虽然 design 已经写了 parity-first 和 GPL 边界，但 tasks 没有把这些规则拆成可执行的 phase 和门禁，导致实现过程中持续出现 drift、命名偏差、额外 helper、以及 Telegram-derived 文件未按统一方式标注来源。

现在需要先把 rewrite 的推进方式本身收紧：把大 change 拆成阶段化交付计划，并把 Telegram-derived / strict parity port 规则提升成独立能力约束，否则后续继续实现只会放大偏差，返工成本会越来越高。

## What Changes

- **BREAKING**: 不再把 `full-rewrite-telegram-parity` 作为单一执行单元继续铺开，后续 Telegram rewrite 按 phase 拆分为更小范围的 change 或任务批次推进，每个 phase 有明确入口、出口和验证门禁
- 新增 Telegram port 治理规则：凡是采用 Telegram 同名类/同名 API 的实现，默认按 strict parity port 处理，先对齐 upstream 公开 API 形状与行为语义，再讨论必要裁剪
- 新增 Telegram-derived 文件规则：必须使用统一 GPL 文件头，标注 upstream class/path、source reference、版权信息和 SPDX，不再允许“看起来是 Telegram port，但文件身份不清楚”
- 新增 anti-drift 规则：Telegram 有对应实现时，不得随意引入项目私有 helper、wrapper、重命名 API、额外 convenience 层；如确需偏离，必须先在 artifacts 中记录原因、边界和影响
- 对当前已落地的 1.x、2.x、3.x 基础文件做 parity audit，区分“保留并收紧为 strict parity subset”、“回退/删除”、“改归类为 project-specific”
- 明确哪些内容属于 phase 0 先决条件，哪些必须后移，避免继续在 substrate 未稳定时提前实现页面和高级交互

## Capabilities

### New Capabilities
- `telegram-port-governance`: 定义 Telegram-derived 文件分类、GPL 头、strict parity port、drift 申报和已有实现审计规则
- `telegram-parity-phasing`: 定义 Telegram rewrite 的阶段拆分、每阶段范围边界、依赖顺序、验证门禁和交付节奏

### Modified Capabilities
- 无

## Impact

- 主要影响 `openspec/changes/full-rewrite-telegram-parity/` 的后续推进方式，以及后续新建的 Telegram rewrite phase changes
- 主要影响 Android rewrite 中所有声称来自 Telegram port 的基础设施文件，尤其是 `core/util/`、`core/event/`、`core/theme/`、`ui/actionbar/`、`ui/components/` 下的 substrate
- 不改变 Discourse API、auth flow 或产品功能范围，但会改变这些能力被迁移和验收的顺序
- 会新增 phase 拆解文档与治理 spec，作为后续实现和 review 的硬约束
