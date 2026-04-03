# NativeDo Discourse 帖子渲染迁移设计

**日期：** 2026-04-02
**分支：** main
**目标：** 将当前 Android 端对 Discourse `cooked HTML` 的渲染方式，从“继续堆积 `HtmlBlock` 特判”升级为“以块级 AST 为主、逐步演进的原生渲染模型”，优先改善高频阅读内容的可读性与可维护性。

---

## 背景与问题

当前帖子渲染已经具备一部分原生能力：

- 通过 `parsePostBodyBlocks()` 将内容拆成 `HtmlBlock`、`ImageBlock`、`ImageGridBlock`
- 通过 `prepareInlineContent()` 处理 `emoji`、`site-icon`
- 通过 `buildPostBodyText()` 处理站内 topic/user 链接

这条路线是正确的，但现在出现了两个实际问题：

1. 越来越多的 Discourse 结构仍然会退回 `HtmlBlock`，例如 `details / summary`、后续的 `blockquote`、`pre/code`、`list`
2. 如果继续在 `HtmlBlock + HtmlCompat` 上补特判，解析逻辑、渲染逻辑和测试都会逐渐失控

这个设计不追求“一次性做完完整 HTML 引擎”，而是明确一个可渐进落地的迁移方案。

---

## 目标与非目标

### 目标

- 保持当前阅读体验可用，不做大爆炸重写
- 先立住 block 边界，再考虑 inline AST
- 优先原生支持 Discourse 高频结构
- 未识别结构仍可安全 fallback
- 让后续新增支持项时，落点明确、测试可追踪

### 非目标

- 本轮不重写完整 `HtmlCompat` 文本渲染
- 本轮不处理所有 HTML tag
- 本轮不覆盖所有插件注入内容
- 本轮不引入大型第三方 HTML 渲染框架
- 本轮不改变现有网络返回、登录态、图片查看器等非渲染行为

---

## 方案比较

### 方案一：继续基于 `HtmlBlock` 叠加特判

优点：

- 实现最快
- 局部需求可以快速补齐

缺点：

- 解析与渲染边界继续混乱
- 后续 `quote / code / list / onebox` 会越来越难加
- 测试粒度会越来越粗

### 方案二：混合 AST，推荐

优点：

- 兼容当前实现，迁移成本最低
- 可以一块一块把高频结构从 `HtmlBlock` 拆出来
- 未知结构仍能 fallback，不会一次性打坏整贴渲染

缺点：

- 过渡期会同时存在 `HtmlBlock` 与多个原生 block
- 需要先接受“不是一口气做完”

### 方案三：一次性重写完整 DOM/inline renderer

优点：

- 理论上模型最纯
- 长期可以最灵活

缺点：

- 初始成本明显过高
- 对当前项目阶段不合适
- 风险过大，容易拖慢实际交付

### 结论

选择**方案二：混合 AST**。

原则是：

- 先做块级 AST
- 保留 `HtmlBlock` 作为 fallback
- 等块级结构稳定后，再进入 inline AST

---

## 当前结构与演进方向

当前的 `PostBodyBlock` 已经是可演进的入口，但能力还不够完整。建议扩展为：

```kotlin
sealed interface PostBodyBlock {
    data class HtmlBlock(
        val html: String,
    ) : PostBodyBlock

    data class ImageBlock(
        val image: PostBodyImage,
    ) : PostBodyBlock

    data class ImageGridBlock(
        val images: List<PostBodyImage>,
        val columns: Int,
    ) : PostBodyBlock

    data class DetailsBlock(
        val summaryHtml: String,
        val children: List<PostBodyBlock>,
        val expandedByDefault: Boolean,
    ) : PostBodyBlock

    data class QuoteBlock(
        val children: List<PostBodyBlock>,
    ) : PostBodyBlock

    data class CodeBlock(
        val code: String,
        val languageHint: String?,
    ) : PostBodyBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItemBlock>,
    ) : PostBodyBlock
}

data class ListItemBlock(
    val children: List<PostBodyBlock>,
)
```

设计意图：

- `HtmlBlock` 不删除，但明确视为 fallback
- `ImageBlock / ImageGridBlock` 完全保留现有能力
- 先把高频块结构独立出来：`DetailsBlock / QuoteBlock / CodeBlock / ListBlock`
- 第二阶段再补 `RichText / PostInline`

---

## 文件边界

### 继续保留

- `app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
  - 顶层入口 composable
  - block 渲染分发
  - 图片查看器相关逻辑

- `app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyInternalLinks.kt`
  - 继续处理 `HtmlBlock` 内部的 topic/user 链接

- `app/src/main/java/io/github/baba983/nativedo/core/ui/DiscourseUiHelpers.kt`
  - 继续处理 `emoji/site-icon` 等 inline 归一化

### 建议新增

- `app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
  - 承担 `Node -> PostBodyBlock` 的解析职责
  - 从 `HtmlPostBodyView.kt` 抽出 `parsePostBodyBlocks()` 相关逻辑

- `app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
  - 放 `DetailsBlock / QuoteBlock / CodeBlock / ListBlock` 的渲染实现
  - 避免 `HtmlPostBodyView.kt` 继续膨胀

这样可以保持：

- parser 只关心结构识别
- renderer 只关心视觉与交互
- `HtmlPostBodyView` 只负责装配

---

## 第一阶段：块级 AST 迁移

第一阶段只做 block，不进入 inline AST。

### 1. `DetailsBlock`

解析规则：

- `details` 解析成 `DetailsBlock`
- `summary` 提取为 `summaryHtml`
- 非 `summary` 子节点递归走 block parser
- `open` 属性映射为 `expandedByDefault`

渲染规则：

- 使用原生可展开容器
- `summaryHtml` 先复用现有 `HtmlTextBlock`
- `children` 递归渲染

### 2. `QuoteBlock`

解析规则：

- `blockquote` 解析成 `QuoteBlock`
- 子节点递归解析

渲染规则：

- 左侧竖线 + 内边距
- 内部 children 递归渲染

### 3. `CodeBlock`

解析规则：

- `pre > code` 解析成 `CodeBlock`
- 提取纯文本代码
- 从 class 中尝试读取语言提示，例如 `language-kotlin`

渲染规则：

- 先做基础代码块样式
- 使用等宽字体
- 不在这一阶段做语法高亮

### 4. `ListBlock`

解析规则：

- `ul / ol` 解析成 `ListBlock`
- 每个 `li` 解析成 `ListItemBlock`
- `li` 内部允许继续递归 block

渲染规则：

- 无序列表显示圆点
- 有序列表显示编号
- 每个 item 递归渲染 children

### 5. 图片结构

保留现有实现：

- 单图 `ImageBlock`
- `lightbox` 提取原图地址
- `d-image-grid` 解析为 `ImageGridBlock`

这部分不在本次设计中重做。

### 6. 未知结构 fallback

任何未识别、但又不值得立刻支持的结构，继续退回 `HtmlBlock`。

这是迁移过程中的硬约束：

- 不因为 parser 升级而牺牲已有帖子可读性
- 不认识的结构先降级，不硬做半成品

---

## 第二阶段：Inline AST

块级结构稳定后，再做 inline AST。

建议模型：

```kotlin
sealed interface PostInline {
    data class Text(val text: String) : PostInline
    data class Link(val text: String, val destination: PostBodyInternalDestination?) : PostInline
    data class Emoji(val rawUrl: String, val fallback: String) : PostInline
    data class SiteIcon(val rawUrl: String, val fallback: String) : PostInline
    data class Mention(val username: String) : PostInline
    data class Styled(val style: InlineStyle, val children: List<PostInline>) : PostInline
    data object LineBreak : PostInline
}

enum class InlineStyle {
    Bold,
    Italic,
    Strike,
    Code,
    LargeText,
}
```

第二阶段才开始替换的内容：

- `strong / em / del / code`
- `font-size`
- mention
- 更明确的链接语义
- 更少依赖 `HtmlCompat`

这样可以避免在 block 尚未稳定时，同时重写两层复杂度。

---

## 示例映射

以下 Discourse 结构：

```html
<details open>
  <summary>给大家看一下</summary>
  <div class="lightbox-wrapper">
    <a class="lightbox" href="/original.jpeg">
      <img src="/thumb.jpeg" width="375" height="500">
    </a>
  </div>
  <p>愚人节快乐！</p>
</details>
```

在第一阶段应映射为：

```kotlin
DetailsBlock(
    summaryHtml = "给大家看一下",
    expandedByDefault = true,
    children = listOf(
        ImageBlock(
            PostBodyImage(
                rawUrl = "/thumb.jpeg",
                originalUrl = "/original.jpeg",
                width = 375,
                height = 500,
            ),
        ),
        HtmlBlock("<p>愚人节快乐！</p>"),
    ),
)
```

这说明第一阶段已经能正确表达结构，不需要立即进入 inline AST。

---

## 测试策略

继续沿用当前 `core/ui` 的 JVM 单测风格。

重点文件：

- `app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

第一阶段至少覆盖：

1. `details -> DetailsBlock`
2. `blockquote -> QuoteBlock`
3. `pre/code -> CodeBlock`
4. `ul/ol -> ListBlock`
5. 混合嵌套结构
   - 例如 `details > quote`
   - 例如 `details > image`
   - 例如 `list > paragraph`

测试原则：

- 先验证结构是否被正确拆成 block
- 暂不追求复杂 UI snapshot
- 每补一个高频结构，就补对应 parser regression test

---

## 实施顺序

建议按以下顺序执行：

1. 抽出 `PostBodyBlockParser.kt`
2. 把现有 `parsePostBodyBlocks()` 迁移到 parser 文件
3. 正式引入 `DetailsBlock`
4. 引入 `QuoteBlock`
5. 引入 `CodeBlock`
6. 引入 `ListBlock`
7. 补齐第一阶段 parser tests
8. 再决定是否进入第二阶段 inline AST

不建议跳过前两步直接继续堆功能，否则 parser 会继续与 UI 绑死。

---

## 风险与权衡

### 风险一：过渡期双体系并存

`HtmlBlock` 与原生 block 会同时存在。

这是可接受的，因为它能换来：

- 渐进迁移
- 更低风险
- 更清晰的 fallback 路径

### 风险二：部分帖子短期仍然不完全等同网页

例如：

- onebox
- poll
- 插件注入内容
- 复杂 inline style

这些在本阶段明确允许 fallback，不视为设计缺陷。

### 风险三：过早引入 inline AST 导致范围失控

因此本设计明确规定：

- 第一阶段只做块级 AST
- inline AST 只能作为第二阶段

---

## 最终建议

当前仓库最适合的不是继续补 `HtmlBlock` 特判，也不是一次性重写 HTML 渲染，而是：

- 以 `PostBodyBlock` 为主干
- 优先拆出高频 Discourse block
- 保留 `HtmlBlock` fallback
- 在块级结构稳定后，再进入 `PostInline`

这条路线与当前代码结构兼容，风险最低，也最容易持续演进。
