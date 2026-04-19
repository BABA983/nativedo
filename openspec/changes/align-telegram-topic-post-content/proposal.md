## Why

NativeDo 的 topic 帖子内容已经具备较完整的正文解析与渲染能力，但帖子卡片的信息层级仍更接近论坛卡片，而不是 Telegram 风格的消息内容容器。现在 replies、likes、reply preview、quote 与 reader 语义还没有清晰分层，导致后续想补 Telegram 风格的互动条、半屏回复面板与可折叠引用时缺少统一的结构基座。

## What Changes

- 将 topic 帖子卡片重组为 Telegram 风格的四层结构：Header、ReplyPreview、Body、Footer，同时保持正文全宽、头像只占头部。
- 为 topic 帖子增加底部互动条，在正文后展示 replies 入口与 like/reaction chip，并把 replies 入口作为查看二级回复的主入口。
- 新增 Telegram 风格的 replies 半屏底部面板：从底部进入、默认半高、内容可滚动，可展示回复用户、摘要内容与跳转原回复入口；交互基线参考 Telegram 点击 sticker 后弹出的半屏面板语义。
- 明确 reply preview 与正文 quote 的职责边界：reply preview 仅表达回复关系与摘要，quote 仍作为正文 block 渲染，避免两者在视觉和语义上混淆。
- 为长 quote 增加类似 Telegram 的可折叠引用能力，在不压缩短引用可读性的前提下限制超长引用块高度。
- 保持 Reader 为独立阅读模式，不继承 topic 社交 footer、reply preview、likes 或 replies 结构。
- 按最小实现分阶段推进：先完成 topic 卡片结构、footer 与 replies 面板，再补 quote 轻量化与折叠增强。

## Capabilities

### New Capabilities
- `telegram-topic-post-content`: 定义 topic 帖子在 Telegram 风格下的卡片结构、reply preview、footer 互动条、replies 面板与长引用折叠行为。
- `topic-replies-sheet`: 定义 topic 帖子 replies 入口触发的半屏底部面板交互、布局与最小内容展示要求。

### Modified Capabilities
- `instant-view-reader`: 保持 reader 为独立阅读模式，不继承 topic 卡片的社交 footer、reply preview 或 reaction 结构。

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicPostCard.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserReaderScene.kt`
- Affected systems:
  - topic post card information hierarchy
  - topic reply relationship presentation
  - topic interaction footer and reply discovery flow
  - bottom sheet interaction model for reply participants
  - quote rendering hierarchy and long-quote containment
  - reader/topic style boundary
- Data/model impact:
  - topic 帖子需要暴露 reply count、recent reply user preview、like count 以及 replies sheet 所需的最小回复摘要数据；首版允许通过 mock 或降级策略推进 UI 结构实现。
