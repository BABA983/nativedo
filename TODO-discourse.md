# TODO: Discourse 渲染能力补全

> 对照 Telegram Android 源码，逐项记录 NativeDo 缺失的渲染能力及对应的 Telegram 参考实现路径。
> Telegram 仓库路径前缀：`/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/`

---

## 一、内联文本样式（低成本 · 高收益）

### 1. `<u>` 下划线

- **Discourse 场景**：Discourse 支持 `[u]text[/u]` BBCode 输出 `<u>` 标签
- **当前状态**：`PostTextDocumentBuilder.kt` 的 `isInlineRenderableElement()` 列出了 `"u"` 但 `appendElement()` 未处理，会 fallthrough 到 `else` 分支仅递归子节点，不应用下划线
- **Telegram 实现**：
  - `ui/Components/TextStyleSpan.java#L97` — `FLAG_STYLE_UNDERLINE = 16`
  - `ui/Components/TextStyleSpan.java#L62-L66` — `applyStyle()` 里 `p.setFlags(p.getFlags() | Paint.UNDERLINE_TEXT_FLAG)`
  - `messenger/MessageObject.java#L7536-L7537` — Entity 映射：`TL_messageEntityUnderline → FLAG_STYLE_UNDERLINE`
  - `ui/ArticleViewer.java#L2940-L2941` — Instant View 里同样用 `TextPaint.UNDERLINE_TEXT_FLAG`
- **实现方案**：
  - `PostTextDocument.kt` 添加 `TextEntity.Underline(start, end)`
  - `PostTextDocumentBuilder.kt` 的 `appendElement()` 增加 `"u"` 分支
  - `PostTextDocumentView.kt` 的 `buildTextDocumentSpannable()` 增加 `android.text.style.UnderlineSpan()`
  - `PostInlineText.kt` 的 `PostInline.Styled.toSpans()` 增加 `InlineStyle.Underline` 分支

---

### 2. `<mark>` 高亮标记

- **Discourse 场景**：`<mark>text</mark>` 高亮文本（Discourse 通过插件或 HTML 支持）
- **当前状态**：`isInlineRenderableElement()` 列出了 `"mark"` 但无实际实体处理
- **Telegram 实现**：
  - `ui/Components/TextPaintMarkSpan.java` — 完整实现，通过 `TextPaint.bgColor` 设置高亮背景
  - `ui/ArticleViewer.java#L2378` — `TEXT_FLAG_MARKED = 64`
  - `ui/ArticleViewer.java#L2455-L2456` — `TL_textMarked → TEXT_FLAG_MARKED`
  - `ui/ArticleViewer.java#L2591-L2597` — `getText()` 中用 `TextPaintMarkSpan` 包裹
- **实现方案**：
  - `TextEntity.Highlight(start, end)` + `BackgroundColorSpan(highlightColor)`
  - 颜色建议使用 `NativeDoThemeExtras.colors` 中的 accent 色 20% 不透明度

---

### 3. `<sup>` 上标

- **Discourse 场景**：脚注引用 `<sup>` 常见于学术帖
- **当前状态**：`isInlineRenderableElement()` 列出了 `"sup"` 但无实体处理
- **Telegram 实现**：
  - `ui/ArticleViewer.java#L2380` — `TEXT_FLAG_SUP = 256`
  - `ui/ArticleViewer.java#L2453-L2454` — `TL_textSuperscript → TEXT_FLAG_SUP`
  - `ui/ArticleViewer.java#L2894-L2896` — 字号缩小 `textSize -= dp(4)`
  - `ui/ArticleViewer.java#L2947-L2948` — 基线偏移 `paint.baselineShift -= dp(6.0f)`
- **实现方案**：
  - `TextEntity.Superscript(start, end)`
  - 使用 Android 内置 `android.text.style.SuperscriptSpan()` + `RelativeSizeSpan(0.75f)`

---

### 4. `<sub>` 下标

- **Discourse 场景**：化学公式 H₂O 等
- **当前状态**：`isInlineRenderableElement()` 列出了 `"sub"` 但无实体处理
- **Telegram 实现**：
  - `ui/ArticleViewer.java#L2379` — `TEXT_FLAG_SUB = 128`
  - `ui/ArticleViewer.java#L2451-L2452` — `TL_textSubscript → TEXT_FLAG_SUB`
  - `ui/ArticleViewer.java#L2894-L2896` — 字号缩小 `textSize -= dp(4)`
  - `ui/ArticleViewer.java#L2949-L2950` — 基线偏移 `paint.baselineShift += dp(2.0f)`
- **实现方案**：
  - `TextEntity.Subscript(start, end)`
  - 使用 `android.text.style.SubscriptSpan()` + `RelativeSizeSpan(0.75f)`

---

### 5. `<kbd>` 键盘按键

- **Discourse 场景**：`<kbd>Ctrl</kbd>+<kbd>C</kbd>` 快捷键展示
- **当前状态**：`isInlineRenderableElement()` 列出了 `"kbd"` 但无实体处理
- **Telegram 实现**：
  - 无直接 `<kbd>` 支持，但等效实现可参考 `URLSpanMono`：
  - `ui/Components/URLSpanMono.java#L46-L52` — Monospace 字体 + 缩小 1dp
  - 配合 `BackgroundColorSpan` 和 `RoundedBackgroundSpan`（自定义）实现边框效果
- **实现方案**：
  - `TextEntity.Kbd(start, end)`
  - `TypefaceSpan("monospace")` + `BackgroundColorSpan` + `RelativeSizeSpan(0.9f)`
  - 可选：自定义 `ReplacementSpan` 绘制圆角边框 + 背景，参考 Telegram 的 `CodeHighlighting.Span`

---

### 6. `<small>` 缩小文本

- **Discourse 场景**：附注、免责声明等
- **当前状态**：`isInlineRenderableElement()` 列出了 `"small"` 但无实体处理
- **Telegram 实现**：
  - 无直接 `<small>` 支持，但等效实现可参考：
  - `ui/Components/QuoteSpan.java#L127-L128` — `QuoteStyleSpan` 中 `tp.setTextSize(dp(SharedConfig.fontSize - 2))`
  - `messenger/MessageObject.java` 中通过 `RelativeSizeSpan` 调整系统消息文字大小
- **实现方案**：
  - `TextEntity.Small(start, end)`
  - `RelativeSizeSpan(0.85f)`

---

### 7. 内联 `<code>` 字号缩小

- **当前状态**：`TypefaceSpan("monospace") + BackgroundColorSpan`，未缩小字号
- **Telegram 实现**：
  - `ui/Components/URLSpanMono.java#L46-L47` — `p.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize - 1))`，比正文小 1dp
- **实现方案**：
  - 在 `buildTextDocumentSpannable()` 处理 `TextEntity.InlineCode` 时增加 `RelativeSizeSpan(0.9f)`

---

## 二、Spoiler 剧透效果（中等难度）

### 8. Spoiler（`TextEntity.Spoiler` 已定义但未渲染）

- **Discourse 场景**：`<span class="spoiler">text</span>` 或 `[spoiler]text[/spoiler]`
- **当前状态**：`TextEntity.Spoiler` 已在 `PostTextDocument.kt#L44-L47` 定义，`buildTextDocumentSpannable()` 中写了 `is TextEntity.Spoiler -> Unit` 直接跳过
- **Telegram 实现**（粒子动画方案，可简化）：
  - `ui/Components/spoilers/SpoilerEffect.java` — 粒子系统 Drawable，982 行，核心是 `draw()` 方法用 `canvas.drawPoints` 绘制运动粒子
  - `ui/Components/spoilers/SpoilersTextView.java#L39-L93` — 管理 spoiler 区域和粒子效果列表
  - `ui/Components/spoilers/SpoilersClickDetector.java#L12-L81` — 点击检测与揭示
  - `ui/Components/TextStyleSpan.java#L73-L75` — 揭示后设置 `p.bgColor` 半透明背景
  - `ui/Components/TextStyleSpan.java#L101` — `FLAG_STYLE_SPOILER = 256`
- **实现方案**（建议简化版）：
  - 阶段一：用 `BackgroundColorSpan(textColor)` + `ForegroundColorSpan(textColor)` 让文字隐藏，点击后移除
  - 阶段二：可选实现 Telegram 风格的粒子动画（参考 `SpoilerEffect.java`，但建议只做模糊遮罩）

---

## 三、代码高亮增强（中等难度）

### 9. 语法高亮语言覆盖

- **当前状态**：`PostCodeHighlighter.kt` 仅支持 kotlin/java/json/bash 四种语言，且只识别 keyword 和 string 两种 token
- **Telegram 实现**：
  - `messenger/CodeHighlighting.java` — 完整的基于 Prism.js 风格的 tokenizer
  - `#L567-L631` — 从 `codelng.gzip` 资产文件加载预编译正则模式（几十种语言）
  - `#L49-L58` — 7 种 token 类型：keyword/operator/constant/string/number/comment/function
  - `#L107-L130` — `ColorSpan` 为每种 token 映射主题色
  - `#L206-L250` — 后台线程 tokenize，通过 `LockedSpannableString` 避免渲染闪烁
  - `#L60-L64` — 动态字号：代码 >120 字符缩 5dp，>50 缩 3dp，否则缩 2dp
- **实现方案**：
  - 方案 A：接入 [highlight.js](https://github.com/nicklockwood/Highlight) 或 [Prism4j](https://github.com/nicklockwood/Highlight) 类似的 JVM 库
  - 方案 B：参考 Telegram 打包预编译正则到 assets，Kotlin 实现 tokenizer
  - 先扩充 token 类型到 7 种（加 operator/constant/comment/function）

---

## 四、表格渲染（高难度）

### 10. `<table>` HTML 表格

- **Discourse 场景**：用户帖子中的 Markdown 表格渲染为 `<table>` HTML
- **当前状态**：落入 `UnknownBlock`，通过 `HtmlTextBlock` 的 `TextView` fallback 渲染，效果差
- **Telegram 实现**：
  - `ui/Components/TableLayout.java` — 2090 行的完整自定义 `View`，支持 colspan/rowspan
  - `ui/ArticleViewer.java#L8703-L8982` — `BlockTableCell` 在 `HorizontalScrollView` 中嵌套 `TableLayout`
  - `ui/ArticleViewer.java#L8850-L8861` — 处理 `TL_pageTableCell` 的 colspan/rowspan
  - `ui/ArticleViewer.java#L1300-L1310` — 表格线画笔：`tableLinePaint`、`tableHeaderPaint`、斑马纹
- **实现方案**：
  - `PostBodyBlock.TableBlock(headers, rows, hasHeaderRow)`
  - `PostBodyBlockParser.kt` 中解析 `<table>` → `<thead>/<tbody>/<tr>/<td>/<th>`
  - Compose 实现：`Row` + `Column` 布局 + `HorizontalScrollState` 支持宽表横滑
  - 参考 Telegram `TableLayout` 的 border/stripe 绘制逻辑但用 Compose `drawBehind` 实现

---

## 五、引用块增强（低难度）

### 11. 引用块左侧竖线装饰

- **当前状态**：`QuoteBlockView` 已有 `drawBehind` 绘制竖线和背景，但可以增强
- **Telegram 实现**：
  - `ui/Components/QuoteSpan.java#L406-L422` — `Block.draw()` 绘制引用块
  - `#L417-L422` — 背景：左侧无圆角（`radii[0]=radii[1]=radii[6]=radii[7]=0`），右侧 `dp(4)` 圆角
  - `#L83-L84` — `linePaint` 竖线 + `backgroundPaint` 背景（颜色 12% alpha）
  - `#L99-L104` — `setColor()` 同时更新竖线色和背景色（`ColorUtils.setAlphaComponent(color, 0x1e)`）
  - `#L137-L149` — `chooseHeight()` 在首行/末行额外增加 padding（2-7dp）
  - `#L65-L66` — 折叠支持：`COLLAPSE_LINES = 3`
- **实现方案**：确认现有实现是否已包含这些细节，必要时对齐 Telegram 的间距和配色

---

## 六、Heading 补全（低成本）

### 12. `<h4>/<h5>/<h6>` 标题

- **Discourse 场景**：Discourse 支持完整 h1-h6 标题
- **当前状态**：`PostBodyBlockParser.kt#L9` 中 `headingTags = setOf("h1", "h2", "h3")`，h4-h6 落入 UnknownBlock
- **Telegram 实现**：
  - `ui/ArticleViewer.java#L2913-L2915` — Instant View 中 header/subheader 各有独立字号和字体
- **实现方案**：
  - 扩展 `headingTags` 为 `setOf("h1", "h2", "h3", "h4", "h5", "h6")`
  - `toHeadingTextStyle()` 扩展 h4→`titleMedium`，h5→`titleSmall`，h6→`labelLarge`

---

## 七、其他 Discourse 特有元素（未来考虑）

### 13. 数学公式 `<span class="math">`

- **Telegram**：无对应实现
- **方案**：接入 KaTeX Android 渲染库（如 [noties/Markwon 的 KaTeX 扩展](https://github.com/noties/Markwon)）

### 14. 投票 `<div class="poll">`

- **Telegram**：有投票功能但实现完全不同（`TL_messageMediaPoll`）
- **方案**：需专门设计 `PostBodyBlock.PollBlock`

### 15. `<abbr>` 缩写

- **Telegram**：无直接实现
- **方案**：`TextEntity.Abbreviation(start, end, title)` + 虚线下划线 Span，长按显示 tooltip

---

## 优先级排序

| 优先级 | 项目 | 预估工作量 |
|--------|------|-----------|
| P0 | #1 `<u>` 下划线 | 0.5h |
| P0 | #3 `<sup>` 上标 | 0.5h |
| P0 | #4 `<sub>` 下标 | 0.5h |
| P0 | #6 `<small>` 缩小 | 0.5h |
| P0 | #12 `<h4>-<h6>` 标题 | 0.5h |
| P1 | #2 `<mark>` 高亮 | 1h |
| P1 | #5 `<kbd>` 按键 | 1h |
| P1 | #7 内联代码字号缩小 | 0.5h |
| P1 | #8 Spoiler 基础版 | 2h |
| P2 | #9 语法高亮增强 | 4-8h |
| P2 | #10 表格渲染 | 4-8h |
| P3 | #11 引用块增强 | 1h |
| P3 | #13 数学公式 | 4h |
| P3 | #14 投票 | 8h |
| P3 | #15 `<abbr>` | 1h |
