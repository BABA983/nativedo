## Context

当前 `full-rewrite-telegram-parity` 已经进入进行中状态，但它把 substrate、导航、主题、渲染、Discourse 业务层和页面重建都放在同一个 change 里推进。现有 `design.md` 已经写出了 parity-first、GPL 边界和 anti-drift 原则，问题不在“完全没说”，而在于这些原则没有变成独立的 phase 边界、验收门禁和审计动作，所以实现时很容易出现三类偏差：

- 同名 Telegram port 被当成“差不多就行”的参考实现，公开 API 和语义逐步漂移
- Telegram-derived 文件身份不清楚，GPL 头、upstream path/source reference 没有统一落地
- substrate 未稳定时就继续铺页面和高级行为，导致本该在底层阶段解决的问题被分散到各处

这个 change 的目标不是继续实现产品功能，而是把 rewrite 的推进方式重新收紧，让后续实现可以按 phase 审核、按 phase 验收。

## Goals / Non-Goals

**Goals:**
- 把 Telegram-derived、strict parity port、anti-drift 从“设计原则”提升为可执行的治理规则
- 定义 phase 化交付模型，明确每个 phase 的范围、前置依赖和出口门禁
- 规定已有 1.x、2.x、3.x 实现必须先经过 parity audit，再继续向前推进
- 让后续 review 能快速判断一个文件到底是 Telegram-derived strict port，还是 NativeDo 的 project-specific 实现

**Non-Goals:**
- 不重新定义 `full-rewrite-telegram-parity` 的全部产品范围和页面清单
- 不在这个 change 中直接实现 ActionBarLayout、主题、页面或 Discourse 功能
- 不要求“只要 Telegram 仓库里有文件就全部机械搬运”；只有当前明确 adopt 的 upstream 类才按 strict parity port 约束
- 不把所有 project-specific host/integration 文件伪装成 Telegram-derived

## Decisions

### D1. 继续保留 `full-rewrite-telegram-parity` 作为 umbrella reference，但停止把它当作唯一执行单元

后续实现不再直接围绕一个 193 项的大清单铺开，而是按更小的 phase change 或 phase task batch 推进。`full-rewrite-telegram-parity` 保留为总目标和溯源文档，但不再承担“所有任务都在这里继续打钩”的角色。

这样做的原因：
- 现有大清单混合了基础设施、集成和页面重建，review 粒度过粗
- 一旦某个基础类出现 drift，很难判断问题属于哪个阶段、该在哪里拦截
- 把 phase 独立出来后，可以先把 substrate 收紧，再决定后续页面是否允许进入实现

不采用的替代方案：
- 继续沿用当前大清单，只在评审时口头提醒 parity 规则。这个方案已经证明不够强，无法阻止 drift。

### D2. Telegram 相关文件必须先定性，再允许实现或继续扩展

所有涉及 Telegram 参考的文件都必须先被归类为以下两种之一：

- `Telegram-derived`：文件的公开 API、结构或实现语义明确以 Telegram upstream 同名类/方法为契约，属于 strict parity port 或其明确子集
- `Telegram-inspired project-specific`：只借鉴 Telegram 的职责划分或架构思路，但实现本身是 NativeDo 为自身场景编写的文件

分类结论必须写进 phase artifact 或 audit 表，而不是只留在代码评审口头判断里。只要文件中存在实质性的 Telegram 译写实现块，就按从严原则归为 `Telegram-derived`。

不采用的替代方案：
- 让代码注释或 reviewer 自行推断文件来源。这个方案会让 GPL 和 parity 审计无法落地。

### D3. 对 adopted upstream 类，strict parity port 是默认规则，不是可选风格

当当前阶段决定采用 Telegram 的同名类、同名 API 或相同职责边界时，默认要求：

- 先对齐 upstream 的公开 API 名称、主入口、核心状态机和行为语义
- 只裁剪当前真正需要的子集，不额外改造成 NativeDo 私有 helper/wrapper 形状
- 允许的变化仅限于 Java→Kotlin 语法适配、Android 平台 API 差异、单账号约束、以及与 MTProto/native 强耦合能力的剔除

不允许的偏离包括：
- 以“更 Kotlin”或“更方便调用”为由随意新增同名类的 convenience API
- 把 Telegram 原有主 API 改名，或把行为拆散到多个项目私有 helper 中
- 先落地 drift，再在后续 review 中补解释

若确需偏离，必须先在 artifact 中写明：
- upstream 对应类/方法
- 偏离原因
- 偏离边界
- 受影响调用方

不采用的替代方案：
- 以“功能看起来差不多”作为验收标准。这个标准过于模糊，是当前偏差的根源。

### D4. Telegram-derived 文件的 GPL provenance 是 phase 入口门禁

凡是被归类为 `Telegram-derived` 的文件，必须在继续实现前补齐统一 derived GPL 文件头，至少包含：

- upstream class 名称
- upstream 源文件路径
- source reference
- Telegram copyright
- NativeDo copyright
- SPDX 标识

没有完成 provenance 标注的文件，不允许被视为“已经完成 parity port”，也不允许继续在其基础上叠加新行为。

不采用的替代方案：
- 等整体 rewrite 接近完成时再统一补头。这个方案会让文件来源越来越难追溯，也会让 review 漏掉混合文件。

### D5. 不再允许“先长一个 helper 再说”，helper / wrapper 只有在 upstream 不存在且存在真实调用方时才成立

对 Telegram strict parity port，默认禁止自由增加 `*Utils`、`*Helper`、`update*`、`refresh*`、`initialized*` 一类 project-local convenience API。只有满足以下条件时才允许新增 project-specific 支撑文件：

- Telegram 没有对应公共类或对应能力明确不适用
- 当前 phase 已存在真实调用方
- 新文件不伪装成 Telegram 同名 parity port
- 理由和边界已在 artifact 中记录

这样做的原因：
- 现在很多 drift 正是从“先加个很小的 helper”开始，随后调用点增多，最终回不到 upstream 语义

不采用的替代方案：
- 先保留 helper，等后面再统一清理。这个方案通常不会发生“后面再清理”。

### D6. Rewrite 按固定 phase 拆解，并以依赖顺序推进

后续 Telegram parity rewrite 按以下 phase 拆解：

1. `Phase 0: Governance & Audit`
   - 冻结 monolithic backlog 的继续扩张
   - 审计已有 1.x、2.x、3.x 文件的分类、GPL 头、parity 状态和处置结论
   - 把 `full-rewrite-telegram-parity` 当前任务映射到后续 phase
2. `Phase 1: Core Telegram Substrate`
   - `AndroidUtilities`、`Utilities`、`DispatchQueue`、`FileLog`、`NotificationCenter`
   - `AnimatedFloat`、`AnimatedColor`、`CubicBezierInterpolator`、`LayoutHelper`
   - 最小 `NdTheme` / `NdUiConfig` 等底座边界
3. `Phase 2: Navigation & ActionBar`
   - `SimpleTextView`、`BackDrawable`、`ActionBar`、`BaseFragment`、`ActionBarLayout`
   - push/pop 位移、前页左移、edge swipe-back、阈值和速度 dismiss 等 Telegram 转场契约
4. `Phase 3: Cells / Media / Rendering Substrate`
   - `RecyclerListView` 最小子集
   - `BaseCell`、`ImageReceiver`、`AvatarDrawable`
   - cooked HTML block renderer 的 substrate 和非页面级渲染基元
5. `Phase 4: Runtime / Auth / Discourse Integration`
   - `NdApp`、`NdRuntime`、`LaunchBootstrap`
   - `AuthSessionManager`、`LinkManager`、network/session/bootstrap 链路
6. `Phase 5: Feature Scenes`
   - Topics、TopicDetail、Browser、Profile、Search、Login 按单页面或小批次 change 推进
7. `Phase 6: Polish & Cleanup`
   - 高级转场、性能收尾、legacy 删除、最终清理

这里的关键不是 phase 名称，而是依赖顺序不能被跳过。页面和高级交互不能反过来定义 substrate。

不采用的替代方案：
- 继续 screen-first 推进，再在页面里补 substrate 缺口。这个方案会让同一类基础问题在多个页面重复出现。

### D7. 每个 phase 都必须有显式出口门禁

每个 phase 在继续进入下一阶段前都必须满足最小出口条件：

- artifact 已记录范围内文件的分类和 parity 结论
- 范围内 `Telegram-derived` 文件已补齐 GPL provenance
- 范围内不再存在未申报 drift
- 对应验证完成，并且验证方式与 phase 类型一致

验证基线：
- `Phase 0`：文档和 audit 表完整，当前文件有明确 disposition
- `Phase 1`：编译通过，host smoke 能验证工具类/主题/调度基元可用
- `Phase 2`：编译通过，Hello/navigation host 能验证 push/pop、swipe-back、动画阈值和系统栏联动
- `Phase 3`：mock host 能验证列表/图片/渲染 substrate
- `Phase 4`：启动、session restore、browser/login/bootstrap 主链路可跑通
- `Phase 5`：对应页面功能 smoke 与契约测试通过
- `Phase 6`：assembleDebug、关键测试和最终手工回归通过

不采用的替代方案：
- 统一要求给每个基础类补 standalone unit test。对 Telegram 式 UI substrate，这个方案既不现实，也不符合现有设计结论。

### D8. 已有实现的 audit 输出必须包含“文件 -> 分类 -> 处置”

对于当前已经落地的 1.x、2.x、3.x 文件，phase 0 必须产出明确 audit 结果。每个文件至少要给出：

- 分类：`Telegram-derived` / `Telegram-inspired project-specific`
- parity 状态：strict subset / documented divergence / not parity
- 处置：保留并收紧、回退重做、删除、改归类

这样后续评审不会再停留在“感觉有点不像 Telegram”，而是能直接判断这个文件是否符合本 change 的治理规则。

不采用的替代方案：
- 只在实现时顺手修。这个方案无法形成可跟踪的治理闭环。

## Risks / Trade-offs

- 先做 phase split 和 audit 会放慢短期编码速度 → 通过减少后续返工和重复 review 来回收成本
- strict parity port 会让一部分“更符合本项目直觉”的改写被挡住 → 通过显式 drift 申报保留必要偏离，而不是完全禁止偏离
- 同时维护 umbrella change 和 phase changes 会增加文档数量 → 通过把 `full-rewrite-telegram-parity` 降级为 reference，避免双重执行来源
- 某些现有文件可能已经累积较多 drift，回退成本不低 → 通过 phase 0 audit 先区分“收紧可救”和“应删除重来”，避免继续加码在错误基线上

## Migration Plan

1. 冻结 `full-rewrite-telegram-parity` 继续无限扩张，停止在未审计前继续声明新的 parity 完成项
2. 基于现有 `tasks.md` 和已落地文件，建立 phase 映射与 audit 清单
3. 完成 `Phase 0`：补齐分类、GPL provenance、drift 申报和处置结论
4. 按 phase 创建更小的 follow-up changes 或任务批次，并把实现工作迁移到这些 phase 单元
5. 后续所有 Telegram parity 实现只允许在 phase 单元内推进；umbrella change 只保留总目标和溯源作用
6. 当 phase 单元覆盖完现有大清单后，再决定是否归档或改写 `full-rewrite-telegram-parity`

## Open Questions

- phase 单元最终是“一 phase 一 change”还是“一个 phase 内再拆成多个小 change”，需要在 `Phase 0` 结束后根据实际规模确定；本 change 只先锁定 phase 边界和门禁
- `full-rewrite-telegram-parity` 最终是归档为 umbrella reference，还是在 child changes 成形后直接停止维护，需要在完成任务映射后决定
