## Context

NativeDo 当前的帖子内容系统已经具备完整的 HTML -> block -> inline 渲染链路，`TopicPostCard` 负责作者头部与正文容器，`HtmlPostBodyView` 与 `PostBodyBlockRenderers` 负责正文 block 级渲染。现状的问题不在 parser 能力，而在 topic 卡片的信息层级仍偏论坛卡片：reply target、quote、likes、replies 与 reader 语义没有明确拆层，导致后续想对齐 Telegram 风格时容易把关系信息、正文内容与互动反馈混在同一层。

这次变更跨越 topic UI、正文 block renderer、reader 边界与半屏面板交互，适合先在实现前明确结构决策。另一个关键约束是：需要参考 Telegram 点击 sticker 后弹出的半屏面板交互，但实现上仍应基于 Android / Compose 现有 bottom sheet 能力，而不是引入新的外部依赖或模仿 Telegram 内部组件命名。

## Goals / Non-Goals

**Goals:**
- 让 topic 帖子卡片形成稳定的四层结构：Header、ReplyPreview、Body、Footer。
- 让 reply preview、正文 quote、长 quote 折叠、footer 互动条在语义和视觉上清晰分层。
- 为 replies 入口提供 Telegram 风格的半屏底部面板交互：底部进入、默认半高、内容可滚动、可继续上拉扩展。
- 保持 reader 为独立阅读模式，不继承 topic 卡片的社交反馈层。
- 以最小实现分阶段推进，优先验证 topic 卡片结构、footer 与 replies 面板，再补正文 quote 增强。

**Non-Goals:**
- 不追求 1:1 复刻 Telegram 内部类名、资源组织或 GPL 实现代码。
- 不在首版引入完整多 reaction 体系；首版只需要单 like/reaction chip 语义。
- 不在首版让 replies 面板成为完整帖子页替代品；深度阅读仍通过跳转原回复完成。
- 不改动现有 HTML parser 的基础模型，除非实现折叠 quote 时发现当前 block 信息不足。

## Decisions

### 1. 以 TopicPostCard 重组为入口，而不是先改 parser
- **Decision**: 首批实现集中在 `TopicPostCard`、topic 侧新 footer / sheet 组件和有限的 block renderer 调整，不主动改 `parsePostBodyBlocks` 或 `TextDocument` 模型。
- **Rationale**: 当前正文系统已经支持 paragraph、quote、code、image、onebox 等语义，问题主要在信息层级与容器结构。先改 parser 会把结构问题误解成语法问题，增加风险。
- **Alternatives considered**:
  - 先改 HTML / TextDocument 模型：能更系统，但首批收益低、回归面大。
  - 直接全局重写 topic 正文组件：风险过高，且会把 reader/profile 也一起带动。

### 2. Reply preview 作为关系层，不作为正文 block
- **Decision**: `TopicReplyPreview` 独立于 `HtmlPostBodyView`，位于 header 与 body 之间，仅显示“被回复人 + 楼层 + 摘要”两行信息。
- **Rationale**: Telegram 中 reply quote 属于 reply metadata，而 blockquote 属于正文内容。把 reply preview 留在 topic card 外层，才能从结构上避免与正文 quote 混淆。
- **Alternatives considered**:
  - 继续把 reply target 做成头部第一行 pill：实现简单，但会挤占头部信息、也偏论坛而不够 Telegram。
  - 把 reply preview 做成正文开头的 quote block：视觉上会与 blockquote 冲突。

### 3. Footer 作为独立互动层，承载 replies 与 like chip
- **Decision**: 在正文后新增 `TopicPostFooter`，左侧为 replies entry，右侧为 like/reaction chip。
- **Rationale**: Telegram 风格里互动反馈是正文后的轻量层，不应塞入 header。这样既保留正文宽度，又为后续 replies 面板与 reaction 扩展提供稳定锚点。
- **Alternatives considered**:
  - 把 replies / likes 塞回头部：会导致头部信息过密。
  - 把 likes 合并进正文末尾文本：不利于交互热点识别。

### 4. Replies 面板采用 modal bottom sheet 语义
- **Decision**: replies 点击后打开一个半屏 modal bottom sheet，交互基线参考 Telegram 点击 sticker 后的面板：从底部进入、默认半高、内容区可滚动、允许向上扩展。
- **Rationale**: 这是最贴近 Telegram 行为的 Android/Compose 语义，也最适合 topic 二级回复的“扫读后跳转”场景。
- **Alternatives considered**:
  - 新 route / 全屏页：更完整，但切换过重，不像 Telegram 的轻量二级面板。
  - 普通 dialog：缺少底部进入与拖拽层级感。
  - 自定义 overlay 容器：成本更高，首版没有必要。

### 5. Replies 面板首版展示 2~3 行受控摘要，而不是完整长文
- **Decision**: `ReplySheetItem` 首版展示头像、显示名、时间、2~3 行摘要，并提供“查看原回复”跳转；不在首版中全量展开复杂 block。
- **Rationale**: Telegram 风格的列表更强调扫读而非把面板变成第二个正文页。最小实现应先跑通互动链路，再决定是否逐步增强为轻量 HTML 渲染。
- **Alternatives considered**:
  - 全量复用 `HtmlPostBodyView`：更接近最终态，但复杂度明显更高，也更容易让 sheet 失控变高。
  - 只显示用户名列表不带摘要：信息密度不够，难以支持快速判断谁回复了什么。

### 6. 普通 quote 轻量化，长 quote 单独折叠
- **Decision**: 普通 quote 保留正文中的 quote 身份但降低卡片感；长 quote 额外支持可折叠展开。首版折叠触发条件基于结构和长度，而不追求精确按渲染行数截断。
- **Rationale**: Telegram 风格的重点是防止引用块吞掉正文高度。先用结构阈值做折叠最稳，后续如需要再优化为更精确的测量方案。
- **Alternatives considered**:
  - 所有 quote 都统一折叠：会损害短 quote 可读性。
  - 等做完更复杂的文本测量再上线：会拖慢结构改造。

### 7. Reader 保持独立模式，通过 variant / 调用边界隔离
- **Decision**: topic 的 Telegram 风格结构不直接渗透到 reader；必要时通过 `renderVariant` 或调用层约束保证 reader 不渲染 reply preview、footer 或 reactions。
- **Rationale**: reader 更接近 Instant View 文章流，不是聊天卡片。必须在设计上明确边界，避免 topic 社交元素污染阅读模式。
- **Alternatives considered**:
  - 所有内容视图共享一套容器：复用高，但风格会互相污染。

## Risks / Trade-offs

- **[Replies 数据字段暂未齐全]** → 首版允许 reply count / like count / recent reply users / reply 摘要通过 mock 或降级方案推进；结构先落地，数据接入后补齐。
- **[Sheet 若直接复用完整 HTML 渲染会过重]** → 首版限制为摘要展示 + 跳转原回复，避免复杂 block 在半屏内造成滚动与性能问题。
- **[Reply preview 与 quote 仍可能视觉接近]** → 通过位置、行数限制、颜色层级和不使用 quote 左侧重引用条来强化区分。
- **[长 quote 折叠阈值不精确]** → 首版采用结构阈值，允许在后续迭代中根据真实内容样本调整折叠条件。
- **[Topic 样式改造可能影响 profile / reader 复用路径]** → 在修改 `HtmlPostBodyView` 或 renderer 时显式保留 Chat / Reader 边界，并在 topic 场景先验证后再扩展。

## Migration Plan

1. 先在 topic UI 层引入 `TopicPostHeader`、`TopicReplyPreview`、`TopicPostFooter` 的结构拆分，不调整正文 parser。
2. 接入 replies entry 与 like chip 的最小版数据，并为 replies entry 连接 modal bottom sheet。
3. 以摘要列表形式实现 `ReplyParticipantsSheet`，先打通打开、关闭、滚动与跳转原回复。
4. 之后再轻量化正文 quote 样式，并为超长 quote 增加折叠。
5. 最后验证 reader 仍保持独立阅读模式；如引入了 variant 参数，则默认 reader 继续使用阅读型布局。

回滚策略：
- Topic 卡片结构与 replies sheet 可按 composable 级别回退，必要时恢复到旧 `TopicPostCard` 结构。
- quote 折叠逻辑可单独关闭，不影响正文基础渲染。

## Open Questions

- topic 数据层当前是否已经能直接提供 recent reply user avatars 与 like count，还是需要首版本地 mock / 拼装。
- replies sheet 首版是否只显示纯文本摘要，还是允许在少量安全 block（例如单段落、短 quote）上做轻量 HTML 渲染。
- 长 quote 的折叠阈值最终应基于 block 数、字符数还是渲染高度；首版先用结构阈值，后续是否需要升级为精确测量。
