# NativeDo Discourse 渲染 V2 设计

**日期：** 2026-04-05  
**分支：** main  
**目标：** 在 Android 端为 Discourse `cooked HTML` 建立一条更稳定的原生渲染链，借鉴 Telegram 的文本实体与排版思路，但保留适合 Discourse 结构内容的块级 AST，优先解决“容易漏渲染”和“文本风格不统一”两个核心问题。

---

## 背景与问题

当前帖子渲染已经具备一条可演进的原生路线：

- 通过 `PostBodyBlockParser` 将内容拆成图片、图片组、引用、代码块、列表、onebox 等 block
- 通过 `PostInlineParser` 和 `PostInlineText` 处理基础 inline 富文本
- 通过 `HtmlBlock` 保留部分 fallback 能力

这条路线方向是正确的，但还存在三个结构性问题：

1. **块级支持在增长，但文本系统仍然偏弱**
   - 现在的 inline 模型更接近“标签树映射”，不是“统一的文本实体系统”
   - 一旦遇到复杂嵌套、特殊标签或样式组合，边界会逐渐散掉

2. **渲染风格还没有形成统一的消息文本系统**
   - code / quote / list / paragraph 目前更像若干独立组件
   - 这会让阅读体验更像“内容卡片堆叠”，而不是 Telegram 那种内聚的消息排版

3. **fallback 机制还不够工程化**
   - 当前 parser 已经能 fallback，但还没有“未知节点不可静默丢失”的明确约束
   - 对真实 Discourse `cooked HTML` 的覆盖率也缺少系统审计

因此，这一轮设计不再继续沿着“新增一个标签，就加一个特判组件”的路线推进，而是要把渲染系统收成更稳的结构。

---

## 目标与非目标

### 目标

- 保留当前块级 AST 方向，不做大爆炸重写
- 引入更接近 Telegram 的文本实体思想，统一 paragraph / heading / inline style 的渲染能力
- 建立明确的 fallback 与 unknown block 策略，避免漏渲染
- 让后续新增 Discourse 标签或插件块时，有明确落点和测试路径
- 让样式统一收口到“论坛阅读版 Telegram 排版”，而不是继续叠加独立小卡片

### 非目标

- 不直接复用 Telegram Android 源码或其 GPL 代码实现
- 不把 Discourse 阅读端改造成 Telegram 协议或 `MessageEntity` 数据结构
- 不在本轮实现发帖编辑器的 markdown 输入解析
- 不在本轮一次性覆盖所有 Discourse 插件块
- 不在本轮推翻现有 Compose + `AndroidView(TextView)` 的混合渲染路径

---

## 方案比较

### 方案一：继续沿现有 AST 按标签补支持

优点：

- 改动最小
- 局部需求响应最快

缺点：

- parser 和 renderer 会继续发散
- 文本排版难以统一收口
- 每种特殊内容都需要新的视觉特判
- 未来仍然容易漏渲染

### 方案二：保留块级 AST，引入 Telegram 风格的文本实体层，推荐

优点：

- 与 Discourse HTML 结构匹配
- 能保留现有 block renderer 的投入
- 文本渲染可统一收口到一套实体系统
- 更适合逐步补充 `spoiler / mark / kbd / table cell / mention / code highlight`
- 可以明确建立 unknown fallback 和 coverage 审计

缺点：

- 需要调整现有 inline 模型
- 过渡期会同时存在旧 inline 与新 `TextDocument`

### 方案三：全面改成 Telegram 那种 offset-only 实体模型

优点：

- 文本排版模型最纯
- 思想上最接近 Telegram

缺点：

- 不适合 Discourse 的 DOM/块结构输入
- 图片、onebox、details、plugin block 的建模会更别扭
- 迁移成本过高

### 结论

选择**方案二：保留块级 AST，引入文本实体层**。

核心判断：

- Discourse 适合块级结构优先
- Telegram 适合文本实体优先
- NativeDo 应该做的是“结构走 Discourse，文字走 Telegram”

---

## 核心设计

### 总体渲染管线

建议将渲染链路收敛为：

```text
Discourse cooked HTML
  -> DOM 归一化
  -> Block Parser
  -> Block AST + TextDocument
  -> Android Renderer
     - Compose 负责块级布局
     - TextView/Spannable 负责文本实体渲染
```

这个设计保留当前项目的优势：

- Compose 继续负责图片、卡片、列表、引用容器、滚动与手势
- TextView/Spannable 继续负责复杂文本排版

同时借入 Telegram 的核心经验：

- 文本样式统一进入一套实体系统
- inline code / pre / quote / spoiler 等不再各自散落实现

---

## 数据模型

### 块级模型

当前 `PostBodyBlock` 已经接近正确方向，V2 设计建议将 block 明确收敛为以下职责：

```kotlin
sealed interface PostBodyBlock {
    data class Paragraph(val content: TextDocument) : PostBodyBlock
    data class Heading(val level: Int, val content: TextDocument) : PostBodyBlock
    data class Quote(
        val title: TextDocument? = null,
        val children: List<PostBodyBlock>,
        val collapsible: Boolean = false,
    ) : PostBodyBlock
    data class CodeBlock(
        val code: String,
        val language: String?,
    ) : PostBodyBlock
    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItemBlock>,
    ) : PostBodyBlock
    data class ImageBlock(...) : PostBodyBlock
    data class ImageGridBlock(...) : PostBodyBlock
    data class OneboxBlock(...) : PostBodyBlock
    data class DetailsBlock(...) : PostBodyBlock
    data class UnknownBlock(
        val rawHtml: String,
        val tagName: String,
        val classes: Set<String>,
    ) : PostBodyBlock
}
```

设计意图：

- `Paragraph / Heading` 从当前 `RichTextBlock` 演进而来
- `Quote` 统一承载普通 blockquote 和 Discourse quote aside 的主体能力
- `CodeBlock` 保持块级代码语义，不混入 paragraph 样式系统
- `UnknownBlock` 成为“不可丢内容”的结构化 fallback

### 文本模型

当前 `PostInline` 适合作为过渡模型，但长期建议替换为：

```kotlin
data class TextDocument(
    val text: String,
    val entities: List<TextEntity>,
)

sealed interface TextEntity {
    data class Bold(val start: Int, val end: Int) : TextEntity
    data class Italic(val start: Int, val end: Int) : TextEntity
    data class Strike(val start: Int, val end: Int) : TextEntity
    data class Link(val start: Int, val end: Int, val rawUrl: String) : TextEntity
    data class Mention(val start: Int, val end: Int, val username: String) : TextEntity
    data class InlineCode(val start: Int, val end: Int) : TextEntity
    data class Spoiler(val start: Int, val end: Int) : TextEntity
    data class Emoji(val start: Int, val end: Int, val rawUrl: String, val fallback: String) : TextEntity
    data class SiteIcon(val start: Int, val end: Int, val rawUrl: String, val fallback: String) : TextEntity
}
```

设计意图：

- 对齐 Telegram 的“文本 + 实体区间”思路
- 降低复杂嵌套标签对 renderer 的影响
- 为 future 的 `syntax highlight / spoiler / mark / kbd / mention` 打基础

---

## 解析层设计

### 1. DOM 归一化

在进入 block parser 前增加一层 DOM 归一化，职责如下：

- 清理纯装饰 icon、无语义 wrapper
- 标准化空白和换行
- 识别 Discourse 特有 class 与 block 边界
- 将以下结构优先提升为语义节点：
  - `aside.quote`
  - `aside.onebox`
  - `div.d-image-grid`
  - `pre > code`
  - `details > summary`

这层只负责“结构变得更可解析”，不负责视觉。

### 2. Block Parser

Block parser 的规则应改成：

- 先识别已知块级节点
- 已知块优先生成原生 block
- 无法识别但仍有内容的节点进入 `UnknownBlock`
- 只有在确实不值得结构化建模时，才退回 legacy `HtmlBlock`

第一批原生支持矩阵：

- `p`
- `h1 / h2 / h3`
- `blockquote`
- `pre > code`
- `ul / ol / li`
- `img / figure / a.lightbox`
- `details / summary`
- `aside.quote`
- `aside.onebox`
- `div.d-image-grid`

第二批可扩展矩阵：

- `table`
- `hr`
- `kbd`
- `mark`
- `sup / sub`
- plugin block

### 3. Inline Document Builder

块内文本节点不再直接保留为树状 `PostInline`，而是 flatten 成 `TextDocument`。

例如：

```html
<p>Hello <strong>world</strong> <code>x = 1</code> <a href="/u/test">@test</a></p>
```

生成：

```kotlin
TextDocument(
    text = "Hello world x = 1 @test",
    entities = listOf(
        Bold(6, 11),
        InlineCode(12, 17),
        Mention(18, 23, "test"),
    ),
)
```

这样可以统一解决：

- 强调样式
- 链接和 mention
- inline code
- emoji/site icon
- 未来的 spoiler/mark 等富文本能力

---

## Android 渲染层设计

### Compose 负责块级

Compose 负责：

- 帖子外层卡片与块间距
- 图片和图片组
- onebox
- details
- quote / code block 的容器与滚动
- 图片查看器与跳转交互

### TextView / Spannable 负责文本级

TextView/Spannable 负责：

- paragraph
- heading
- list item 文本
- quote 内部正文
- mention/link
- inline code
- emoji/site icon
- spoiler

这个组合延续现有 `PostInlineText` 的技术路线，但把输入从“inline tag tree”升级成“TextDocument + entities”。

这样做的优势：

- 文字排版能力更强
- 复杂 span 更容易做
- 更贴近 Telegram Android 的实体渲染思路

---

## 样式原则

### 正文排版

- 正文、标题、列表、引用正文应共享同一套 typography token
- 不同 block 间的文字大小变化要克制
- 行高、段距、图片距正文的节奏统一由 post body token 管理

### Inline Code

- 只做轻背景 + mono
- 不做胶囊按钮感
- 与正文保持同一行排版节奏

### Code Block

- 更像嵌入正文的预格式文本 slab，而不是一张独立卡片
- 优先保证：
  - monospace
  - 最大高度
  - 内部滚动
  - 后续 syntax highlighting
- 语言标签可以弱化
- 复制操作不要求常驻 header 按钮

### Quote

- 视为段落组，不视为卡片中的另一张卡片
- 强调：
  - 左侧引导线
  - 清晰但克制的背景层级
  - 折叠能力可作为第二阶段特性

这个方向参考的是 Telegram 的文本系统能力，而不是照搬 Telegram 的 chat bubble 视觉。

---

## Fallback 与防漏策略

### 原则一：未知内容不能 silent drop

任何未识别节点都必须：

- 进入 `UnknownBlock`
- 或 legacy `HtmlBlock`

不能在 parser 中直接跳过。

### 原则二：保留原始上下文

`UnknownBlock` 至少保留：

- 原始 HTML
- tagName
- classNames

便于：

- 调试
- 日后新增原生支持
- 真机回归定位

### 原则三：覆盖率要可观测

对真实 fixture 中的 `cooked HTML` 做覆盖审计，统计：

- 解析中遇到的 tag/class
- 原生 block 命中情况
- fallback 命中情况
- unknown block 命中情况

目标不是一开始把 unknown 清零，而是让 unknown 可被观测和追踪。

---

## 测试策略

### 1. Parser Regression

每新增一种 block 或 entity，都必须有 parser regression test。

重点覆盖：

- `blockquote + paragraph`
- `pre + language`
- `details + quote`
- `list + nested paragraph`
- `emoji + mention + inline code`
- `onebox + image-grid`

### 2. Fixture Audit

对真实 `topic-detail` fixture 追加审计测试：

- 收集 cooked HTML 中出现的 tag/class
- 验证 parser 至少将其映射为：
  - native block
  - unknown block
  - fallback block

### 3. Typography Snapshot / Characterization

至少保留对以下内容的特征测试：

- paragraph font size / line height
- code block spacing / max height
- quote spacing / padding
- image grid spacing

### 4. Build Verification

Android 端最终仍按项目既有规则验证：

- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

---

## 实施阶段

### 阶段 1：Fallback 与 Coverage 底座

目标：

- parser 不再 silent drop
- 引入 `UnknownBlock`
- 建立 tag/class 覆盖审计

收益：

- 先解决“漏渲染”
- 给后续重构提供可观测基础

### 阶段 2：引入 `TextDocument + TextEntity`

目标：

- paragraph / heading / list item 文本切换到统一文本模型
- `PostInlineParser` 开始向 `TextDocumentBuilder` 迁移

收益：

- 给 Telegram 风格文本系统打底
- 降低 inline 规则分散

### 阶段 3：统一文本渲染

目标：

- `PostInlineText` 以实体渲染为中心重构
- mention/link/emoji/inline code 进入统一 span 生成流程

收益：

- 文本表现一致
- 更容易补 spoiler、mark、kbd 等能力

### 阶段 4：重做 Quote / Code 的表现

目标：

- quote 从“卡片样式”收敛到“段落组样式”
- code block 从“组件感”收敛到“预格式文本感”
- 评估并接入 syntax highlight

收益：

- 更接近 Telegram 的阅读体验
- 更适合论坛长帖

### 阶段 5：补充 Discourse 扩展块

目标：

- 逐步补 `table / hr / kbd / mark / plugin blocks`

收益：

- 阅读覆盖率持续提升
- unknown block 逐步减少

---

## 风险与权衡

### 风险一：过渡期模型并存

短期内 `PostInline` 与 `TextDocument` 可能并存。

策略：

- 按 block 类型逐步迁移
- 不要求一次性切换全部 renderer

### 风险二：TextView/Spannable 与 Compose 混用增加复杂度

这是现实成本，但对 Android 富文本阅读是合理取舍。

策略：

- 明确边界：Compose 管块，TextView 管文
- 不在同一块里混杂两套文字系统

### 风险三：syntax highlighting 容易扩 scope

策略：

- 先完成结构和 fallback
- highlight 作为第四阶段增强，不提前阻塞整体迁移

---

## 结论

NativeDo 的帖子渲染 V2 不应继续沿着“补标签特判”的方向推进，而应明确演进为：

- **结构上：保留 Discourse 友好的块级 AST**
- **文本上：引入 Telegram 风格的实体化文本系统**
- **工程上：建立 unknown fallback 与 coverage 审计**

最终目标不是复制 Telegram 的聊天视觉，而是借它在 Android 上成熟的文本排版与实体渲染思想，做出更稳定、更完整、也更适合论坛长文阅读的 Discourse 原生渲染链。
