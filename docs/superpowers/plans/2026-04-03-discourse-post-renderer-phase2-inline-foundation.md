# Discourse Post Renderer Phase 2 Inline Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal inline AST and `RichTextBlock` path on Android so common Discourse text content stops depending on `HtmlCompat`, while keeping `HtmlBlock` fallback for unsupported structures.

**Architecture:** Keep the Phase 1 block AST as-is for images, lists, code, quotes, and unknown HTML. Add a small `PostInline` model plus an inline parser for common inline tags, then introduce `RichTextBlock` for text-like blocks (`p`, `h1`, `h2`, `h3`) and `details > summary`. Render those blocks through a shared TextView-backed spannable builder that reuses the current inline image placeholder pipeline and render-time internal link resolution.

**Tech Stack:** Kotlin, Jetpack Compose, Android TextView/Spannable, Jsoup, JUnit4, Truth, Gradle unit tests

---

## File Map

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlines.kt`
  - Shared inline models and text block style enums
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineParser.kt`
  - `HTML fragment -> List<PostInline>` parsing for common inline tags
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineText.kt`
  - `List<PostInline> -> CharSequence` builder and `PostInlineTextBlock` composable
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
  - Add `RichTextBlock` and move `DetailsBlock.summaryHtml` to structured summary content
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
  - Map text-like block tags to `RichTextBlock`
  - Parse `details > summary` into structured inline content
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
  - Render `RichTextBlock`
  - Render `DetailsBlock.summary` through the inline text path
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
  - Keep `HtmlTextBlock` as fallback only
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineParserTest.kt`
  - Parser regression tests for common inline tags
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineTextTest.kt`
  - Spannable/text-builder regression tests
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`
  - Block parser regression tests for `RichTextBlock` and `details > summary`

Out of scope for this plan:

- mention / `@username` dedicated inline node
- `del` / strike-through inline styling
- `onebox`, `poll`, `table`, plugin-specific blocks
- replacing every remaining `HtmlBlock`
- syntax highlighting, copy button, or extra code block chrome

---

### Task 1: Introduce `PostInline` And The Inline Parser Boundary

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlines.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineParser.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineParserTest.kt`

- [ ] **Step 1: Add failing parser tests for common inline structures**

```kotlin
package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostInlineParserTest {
    @Test
    fun parseInlineHtml_mapsLinkEmojiSiteIconAndLineBreak() {
        val inlines = parseInlineHtml(
            """
                hello<a href="/t/topic/1875662/1">topic</a><br>
                <img class="emoji" src="/images/emoji/twemoji/distorted_face.png?v=15" title=":distorted_face:" alt=":distorted_face:">
                <img class="site-icon" src="https://github.com/favicon.ico" alt="">GitHub
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Text("hello"),
            PostInline.Link(
                children = listOf(PostInline.Text("topic")),
                rawUrl = "/t/topic/1875662/1",
            ),
            PostInline.LineBreak,
            PostInline.Emoji(
                rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                fallback = ":distorted_face:",
            ),
            PostInline.SiteIcon(
                rawUrl = "https://github.com/favicon.ico",
                fallback = "",
            ),
            PostInline.Text("GitHub"),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_mapsBoldItalicCodeAndLargeTextSpan() {
        val inlines = parseInlineHtml(
            """
                <strong>加粗</strong><em>斜体</em><code>val answer = 42</code><span style="font-size: 400%">大字</span>
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Styled(
                style = InlineStyle.Bold,
                children = listOf(PostInline.Text("加粗")),
            ),
            PostInline.Styled(
                style = InlineStyle.Italic,
                children = listOf(PostInline.Text("斜体")),
            ),
            PostInline.Styled(
                style = InlineStyle.Code,
                children = listOf(PostInline.Text("val answer = 42")),
            ),
            PostInline.Styled(
                style = InlineStyle.LargeText,
                children = listOf(PostInline.Text("大字")),
            ),
        ).inOrder()
    }
}
```

- [ ] **Step 2: Run the parser tests to verify the current code does not support them**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.PostInlineParserTest"
```

Expected: FAIL with unresolved `PostInline`, `InlineStyle`, or `parseInlineHtml`.

- [ ] **Step 3: Create `PostInlines.kt` with the minimal inline model**

```kotlin
package io.github.baba983.nativedo.core.ui

sealed interface PostInline {
    data class Text(
        val text: String,
    ) : PostInline

    data class Link(
        val children: List<PostInline>,
        val rawUrl: String,
    ) : PostInline

    data class Emoji(
        val rawUrl: String,
        val fallback: String,
    ) : PostInline

    data class SiteIcon(
        val rawUrl: String,
        val fallback: String,
    ) : PostInline

    data class Styled(
        val style: InlineStyle,
        val children: List<PostInline>,
    ) : PostInline

    data object LineBreak : PostInline
}

enum class InlineStyle {
    Bold,
    Italic,
    Code,
    LargeText,
}

enum class RichTextBlockStyle {
    Paragraph,
    Heading1,
    Heading2,
    Heading3,
}
```

- [ ] **Step 4: Create `PostInlineParser.kt` and keep it independent from block parsing**

```kotlin
package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun parseInlineHtml(html: String): List<PostInline> {
    val body = Jsoup.parseBodyFragment(html).body()
    return mergeAdjacentText(parseInlineNodes(body.childNodes()))
}

private fun parseInlineNodes(nodes: List<Node>): List<PostInline> {
    return nodes.flatMap { node ->
        when (node) {
            is TextNode -> listOf(PostInline.Text(node.text()))
            is Element -> node.toPostInlines()
            else -> emptyList()
        }
    }
}

private fun Element.toPostInlines(): List<PostInline> {
    return when {
        tagName() == "br" -> listOf(PostInline.LineBreak)
        tagName() == "a" -> listOf(
            PostInline.Link(
                children = parseInlineNodes(childNodes()),
                rawUrl = attr("href"),
            ),
        )
        tagName() == "img" -> listOfNotNull(toInlineImage())
        tagName() in setOf("strong", "b") -> listOf(
            PostInline.Styled(InlineStyle.Bold, parseInlineNodes(childNodes())),
        )
        tagName() in setOf("em", "i") && !isDecorativeInlineIcon() -> listOf(
            PostInline.Styled(InlineStyle.Italic, parseInlineNodes(childNodes())),
        )
        tagName() == "code" -> listOf(
            PostInline.Styled(InlineStyle.Code, parseInlineNodes(childNodes())),
        )
        tagName() == "span" && hasLargeFontSize() -> listOf(
            PostInline.Styled(InlineStyle.LargeText, parseInlineNodes(childNodes())),
        )
        isDecorativeInlineIcon() -> listOf(PostInline.Text(attr("aria-label").ifBlank { attr("title") }))
        else -> parseInlineNodes(childNodes())
    }
}

private fun Element.toInlineImage(): PostInline? {
    val rawUrl = attr("src").ifBlank { return PostInline.Text(inlineFallbackText()) }
    return when {
        hasClass("emoji") -> PostInline.Emoji(rawUrl = rawUrl, fallback = inlineFallbackText())
        hasClass("site-icon") -> PostInline.SiteIcon(rawUrl = rawUrl, fallback = inlineFallbackText())
        else -> PostInline.Text(inlineFallbackText())
    }
}
```

- [ ] **Step 5: Finish the parser helpers needed by the tests and merge adjacent text nodes**

```kotlin
private fun Element.hasLargeFontSize(): Boolean {
    val style = attr("style")
    val percentage = Regex("""font-size\s*:\s*(\d+)%""")
        .find(style)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return percentage != null && percentage > 100
}

private fun Element.isDecorativeInlineIcon(): Boolean {
    return classNames().any { className ->
        className == "svg-icon" ||
            className == "d-icon" ||
            className.startsWith("d-icon-") ||
            className == "fa" ||
            className.startsWith("fa-")
    }
}

private fun Element.inlineFallbackText(): String {
    return when {
        hasClass("emoji") -> attr("title").ifBlank { attr("alt") }
        else -> attr("alt").ifBlank { attr("title") }
    }
}

private fun mergeAdjacentText(inlines: List<PostInline>): List<PostInline> {
    return buildList {
        inlines.forEach { inline ->
            val previous = lastOrNull()
            if (previous is PostInline.Text && inline is PostInline.Text) {
                set(lastIndex, PostInline.Text(previous.text + inline.text))
            } else {
                add(inline)
            }
        }
    }
}
```

- [ ] **Step 6: Re-run the parser tests and confirm they pass**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.PostInlineParserTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlines.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineParser.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineParserTest.kt
git commit -m "feat(renderer): 建立行内 AST 基础"
```

---

### Task 2: Introduce `RichTextBlock` And Parse Text-Like Blocks Structurally

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add failing block parser tests for paragraph, heading, and details summary**

```kotlin
@Test
fun parsePostBodyBlocks_mapsParagraphToRichTextBlock() {
    val html = """
        <p><strong>Hello</strong><br><span style="font-size:400%">World</span></p>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks).containsExactly(
        PostBodyBlock.RichTextBlock(
            style = RichTextBlockStyle.Paragraph,
            inlines = listOf(
                PostInline.Styled(
                    style = InlineStyle.Bold,
                    children = listOf(PostInline.Text("Hello")),
                ),
                PostInline.LineBreak,
                PostInline.Styled(
                    style = InlineStyle.LargeText,
                    children = listOf(PostInline.Text("World")),
                ),
            ),
        ),
    )
}

@Test
fun parsePostBodyBlocks_mapsHeadingToRichTextBlock() {
    val block = parsePostBodyBlocks("<h2>标题</h2>").single() as PostBodyBlock.RichTextBlock

    assertThat(block.style).isEqualTo(RichTextBlockStyle.Heading2)
    assertThat(block.inlines).containsExactly(PostInline.Text("标题"))
}

@Test
fun parsePostBodyBlocks_mapsDetailsSummaryToRichTextBlock() {
    val html = """
        <details open="">
          <summary><img class="emoji" src="/images/emoji/twemoji/distorted_face.png?v=15" title=":distorted_face:" alt=":distorted_face:">展开查看</summary>
          <p>正文</p>
        </details>
    """.trimIndent()

    val details = parsePostBodyBlocks(html).single() as PostBodyBlock.DetailsBlock

    assertThat(details.summary).isEqualTo(
        PostBodyBlock.RichTextBlock(
            style = RichTextBlockStyle.Paragraph,
            inlines = listOf(
                PostInline.Emoji(
                    rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                    fallback = ":distorted_face:",
                ),
                PostInline.Text("展开查看"),
            ),
        ),
    )
}
```

- [ ] **Step 2: Run the targeted block parser tests and confirm they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsParagraphToRichTextBlock" \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsHeadingToRichTextBlock" \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsDetailsSummaryToRichTextBlock"
```

Expected: FAIL because `RichTextBlock` and `DetailsBlock.summary` do not exist yet.

- [ ] **Step 3: Add `RichTextBlock` and structured summary content to `PostBodyBlocks.kt`**

```kotlin
sealed interface PostBodyBlock {
    data class HtmlBlock(
        val html: String,
    ) : PostBodyBlock

    data class RichTextBlock(
        val style: RichTextBlockStyle,
        val inlines: List<PostInline>,
    ) : PostBodyBlock

    data class ImageBlock(
        val image: PostBodyImage,
    ) : PostBodyBlock

    data class ImageGridBlock(
        val images: List<PostBodyImage>,
        val columns: Int,
    ) : PostBodyBlock

    data class DetailsBlock(
        val summary: RichTextBlock,
        val children: List<PostBodyBlock>,
        val expandedByDefault: Boolean,
    ) : PostBodyBlock
}
```

- [ ] **Step 4: Parse `p / h1 / h2 / h3` to `RichTextBlock` without breaking standalone image extraction**

```kotlin
private val richTextTags = setOf("p", "h1", "h2", "h3")

private fun parsePostBodyNodes(
    nodes: List<Node>,
): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val htmlBuffer = mutableListOf<Node>()

    fun flushHtmlBuffer() {
        if (htmlBuffer.isEmpty()) {
            return
        }
        val fragment = htmlBuffer.joinToString(separator = "") { node ->
            node.outerHtml()
        }.trim()
        htmlBuffer.clear()
        if (fragment.isNotBlank()) {
            blocks += PostBodyBlock.HtmlBlock(fragment)
        }
    }

    nodes.forEach { node ->
        when {
            node is TextNode && node.text().isBlank() -> {
                if (htmlBuffer.isNotEmpty()) {
                    htmlBuffer += node.clone()
                }
            }
            node is Element && node.isDiscourseImageGrid() -> {
                flushHtmlBuffer()
                val images = extractGridImages(node)
                if (images.isNotEmpty()) {
                    blocks += PostBodyBlock.ImageGridBlock(
                        images = images,
                        columns = node.attr("data-columns").toIntOrNull() ?: defaultGridColumns,
                    )
                }
            }
            node is Element && node.tagName() == "details" -> {
                flushHtmlBuffer()
                blocks += node.toDetailsBlock()
            }
            node is Element && node.tagName() == "blockquote" -> {
                flushHtmlBuffer()
                blocks += PostBodyBlock.QuoteBlock(
                    children = parsePostBodyNodes(node.childNodes()),
                )
            }
            node is Element && (node.tagName() == "ul" || node.tagName() == "ol") -> {
                flushHtmlBuffer()
                blocks += node.toListBlock()
            }
            node is Element && node.tagName() == "pre" -> {
                val codeBlock = node.toCodeBlock()
                if (codeBlock != null) {
                    flushHtmlBuffer()
                    blocks += codeBlock
                } else {
                    htmlBuffer += node.clone()
                }
            }
            node is Element -> {
                val image = node.extractStandaloneImage()
                if (image != null) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.ImageBlock(image)
                } else if (node.tagName() in richTextTags) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.RichTextBlock(
                        style = node.toRichTextBlockStyle(),
                        inlines = parseInlineHtml(node.html()),
                    )
                } else {
                    htmlBuffer += node.clone()
                }
            }
            else -> htmlBuffer += node.clone()
        }
    }

    flushHtmlBuffer()
    return blocks
}

private fun Element.toRichTextBlockStyle(): RichTextBlockStyle {
    return when (tagName()) {
        "h1" -> RichTextBlockStyle.Heading1
        "h2" -> RichTextBlockStyle.Heading2
        "h3" -> RichTextBlockStyle.Heading3
        else -> RichTextBlockStyle.Paragraph
    }
}
```

- [ ] **Step 5: Parse `details > summary` with inline structure instead of raw HTML**

```kotlin
private fun Element.toDetailsBlock(): PostBodyBlock.DetailsBlock {
    val summaryElement = children().firstOrNull { child ->
        child.tagName() == "summary"
    }
    val childNodes = childNodes().filterNot { node ->
        node is Element && node.tagName() == "summary"
    }
    return PostBodyBlock.DetailsBlock(
        summary = PostBodyBlock.RichTextBlock(
            style = RichTextBlockStyle.Paragraph,
            inlines = parseInlineHtml(summaryElement?.html().orEmpty()),
        ),
        children = parsePostBodyNodes(childNodes),
        expandedByDefault = hasAttr("open"),
    )
}
```

- [ ] **Step 6: Re-run the targeted tests and the full block parser suite**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsParagraphToRichTextBlock" \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsHeadingToRichTextBlock" \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsDetailsSummaryToRichTextBlock"
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "feat(renderer): 接入富文本块解析"
```

---

### Task 3: Build A Shared Inline Text Renderer And Wire It Into Block Rendering

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineText.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineTextTest.kt`

- [ ] **Step 1: Add failing text-builder tests for spans, internal links, and inline images**

```kotlin
package io.github.baba983.nativedo.core.ui

import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostInlineTextTest {
    @Test
    fun buildPostInlineText_appliesLargeTextCodeAndInternalLinkSpans() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Link(
                    children = listOf(PostInline.Text("帖子")),
                    rawUrl = "/t/topic/1875662/1",
                ),
                PostInline.Text(" "),
                PostInline.Styled(
                    style = InlineStyle.LargeText,
                    children = listOf(PostInline.Text("大字")),
                ),
                PostInline.Text(" "),
                PostInline.Styled(
                    style = InlineStyle.Code,
                    children = listOf(PostInline.Text("val answer = 42")),
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = {},
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as android.text.Spanned
        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, RelativeSizeSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, TypefaceSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildPostInlineText_collectsEmojiAndSiteIconForDeferredLoading() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Emoji(
                    rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                    fallback = ":distorted_face:",
                ),
                PostInline.SiteIcon(
                    rawUrl = "https://github.com/favicon.ico",
                    fallback = "",
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        assertThat(rendered.pendingImages).hasSize(2)
        assertThat(rendered.text.toString()).doesNotContain("inline_image_")
    }
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.PostInlineTextTest"
```

Expected: FAIL because `buildPostInlineText` and `PostInlineTextBlock` do not exist yet.

- [ ] **Step 3: Create `PostInlineText.kt` and reuse `applyInlineImagePlaceholders()` instead of inventing a second image pipeline**

```kotlin
package io.github.baba983.nativedo.core.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.core.storage.CookieStore

internal fun buildPostInlineText(
    inlines: List<PostInline>,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    textSizePx: Float,
): AppliedInlineImages {
    val builder = SpannableStringBuilder()
    val inlineImages = mutableListOf<InlineImageSpec>()
    appendPostInlines(
        builder = builder,
        inlines = inlines,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        inlineImages = inlineImages,
    )
    return applyInlineImagePlaceholders(
        text = builder,
        inlineImages = inlineImages,
        baseUrl = baseUrl,
        textSizePx = textSizePx,
    )
}

@Composable
internal fun PostInlineTextBlock(
    inlines: List<PostInline>,
    style: RichTextBlockStyle,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val textStyle = style.toTypographyStyle()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(Color.TRANSPARENT)
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()

            textView.setTextColor(colors.textPrimary.toArgb())
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textStyle.fontSize.value)
            if (textStyle.lineHeight.value > 0f && textStyle.fontSize.value > 0f) {
                textView.setLineSpacing(0f, textStyle.lineHeight.value / textStyle.fontSize.value)
            }

            val appliedInlineImages = buildPostInlineText(
                inlines = inlines,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                textSizePx = textView.textSize,
            )
            textView.text = appliedInlineImages.text

            val handle = InlineImageRenderHandle()
            textView.tag = handle
            appliedInlineImages.pendingImages.forEach { pendingImage ->
                handle.jobs += coroutineScope.launch {
                    val bitmap = fetchDiscourseBitmap(
                        resolvedUrl = pendingImage.resolvedUrl,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                    ) ?: return@launch

                    pendingImage.drawable.setBitmap(bitmap)
                    textView.invalidate()
                }
            }
        },
    )
}

private fun RichTextBlockStyle.toTypographyStyle(): TextStyle {
    return when (this) {
        RichTextBlockStyle.Paragraph -> MaterialTheme.typography.bodyLarge
        RichTextBlockStyle.Heading1 -> MaterialTheme.typography.headlineMedium
        RichTextBlockStyle.Heading2 -> MaterialTheme.typography.headlineSmall
        RichTextBlockStyle.Heading3 -> MaterialTheme.typography.titleLarge
    }
}
```

- [ ] **Step 4: Implement span application for links and styled segments**

```kotlin
private fun appendPostInlines(
    builder: SpannableStringBuilder,
    inlines: List<PostInline>,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    inlineImages: MutableList<InlineImageSpec>,
) {
    inlines.forEach { inline ->
        when (inline) {
            is PostInline.Text -> builder.append(inline.text)
            is PostInline.LineBreak -> builder.append('\n')
            is PostInline.Emoji -> builder.appendInlineImage(inline, InlineImageKind.Emoji, inlineImages)
            is PostInline.SiteIcon -> builder.appendInlineImage(inline, InlineImageKind.SiteIcon, inlineImages)
            is PostInline.Styled -> builder.appendSpan(
                span = inline.toSpan(),
            ) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                )
            }
            is PostInline.Link -> builder.appendSpan(
                span = inline.toLinkSpan(
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                ),
            ) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                )
            }
        }
    }
}

private fun SpannableStringBuilder.appendSpan(
    span: Any,
    block: () -> Unit,
) {
    val start = length
    block()
    val end = length
    if (end > start) {
        setSpan(span, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun SpannableStringBuilder.appendInlineImage(
    inline: PostInline,
    kind: InlineImageKind,
    inlineImages: MutableList<InlineImageSpec>,
) {
    val token = "inline_image_${inlineImages.size}"
    val (rawUrl, fallback) = when (inline) {
        is PostInline.Emoji -> inline.rawUrl to inline.fallback
        is PostInline.SiteIcon -> inline.rawUrl to inline.fallback
        else -> error("Unsupported inline image: $inline")
    }
    append(token)
    inlineImages += InlineImageSpec(
        token = token,
        rawUrl = rawUrl,
        contentDescription = fallback,
        kind = kind,
    )
}

private fun PostInline.Styled.toSpan(): Any {
    return when (style) {
        InlineStyle.Bold -> android.text.style.StyleSpan(Typeface.BOLD)
        InlineStyle.Italic -> android.text.style.StyleSpan(Typeface.ITALIC)
        InlineStyle.Code -> TypefaceSpan("monospace")
        InlineStyle.LargeText -> RelativeSizeSpan(1.8f)
    }
}

private fun PostInline.Link.toLinkSpan(
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
): Any {
    val destination = parseInternalLinkDestination(baseUrl = baseUrl, rawUrl = rawUrl)
    return when (destination) {
        is PostBodyInternalDestination.Topic -> PostInlineClickSpan { onTopicClick?.invoke(destination.topicId) }
        is PostBodyInternalDestination.User -> PostInlineClickSpan { onUserClick?.invoke(destination.username) }
        null -> URLSpan(rawUrl)
    }
}

private class PostInlineClickSpan(
    private val onClick: () -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = true
    }
}
```

- [ ] **Step 5: Render `RichTextBlock` and `DetailsBlock.summary` through the new inline path**

```kotlin
// PostBodyBlockRenderers.kt
when (block) {
    is PostBodyBlock.HtmlBlock -> HtmlTextBlock(
        html = block.html,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        modifier = modifier,
    )
    is PostBodyBlock.RichTextBlock -> PostInlineTextBlock(
        inlines = block.inlines,
        style = block.style,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        modifier = modifier,
    )
    is PostBodyBlock.ImageBlock -> PostImageView(
        image = block.image,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onClick = onImageClick?.let { callback -> { callback(block.image) } },
        modifier = modifier,
    )
    is PostBodyBlock.ImageGridBlock -> PostImageGrid(
        images = block.images,
        columns = block.columns,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onImageClick = { image -> onImageClick?.invoke(image) },
        modifier = modifier,
    )
    is PostBodyBlock.DetailsBlock -> DetailsBlockView(
        block = block,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        onImageClick = onImageClick,
        modifier = modifier,
    )
    is PostBodyBlock.QuoteBlock -> QuoteBlockView(
        block = block,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        onImageClick = onImageClick,
        modifier = modifier,
    )
    is PostBodyBlock.CodeBlock -> CodeBlockView(
        block = block,
        modifier = modifier,
    )
    is PostBodyBlock.ListBlock -> ListBlockView(
        block = block,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        onImageClick = onImageClick,
        modifier = modifier,
    )
}
```

```kotlin
// DetailsBlockView summary section
PostInlineTextBlock(
    inlines = block.summary.inlines,
    style = block.summary.style,
    cookieStore = cookieStore,
    baseUrl = baseUrl,
    onTopicClick = onTopicClick,
    onUserClick = onUserClick,
    modifier = Modifier.weight(1f),
)
```

- [ ] **Step 6: Re-run inline text tests and compile the app**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.PostInlineTextTest"
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineText.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineTextTest.kt
git commit -m "feat(renderer): 接入富文本行内渲染"
```

---

### Task 4: Add Real Regression Coverage And Run Final Verification

**Files:**
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineParserTest.kt`

- [ ] **Step 1: Add one failing regression test using a real Discourse-style details snippet**

```kotlin
@Test
fun parsePostBodyBlocks_mapsDiscourseDetailsSnippetToStructuredSummaryAndLargeTextBody() {
    val html = """
        <details open="">
          <summary>给大家看一下我花钱购买的 CODEX ，和我品鉴它的真实感受</summary>
          <p><span style="font-size:400%">愚人节快乐！</span>（虽然有点晚了）</p>
        </details>
    """.trimIndent()

    val details = parsePostBodyBlocks(html).single() as PostBodyBlock.DetailsBlock
    val paragraph = details.children.single() as PostBodyBlock.RichTextBlock

    assertThat(details.summary.inlines).containsExactly(
        PostInline.Text("给大家看一下我花钱购买的 CODEX ，和我品鉴它的真实感受"),
    )
    assertThat(paragraph.inlines).containsExactly(
        PostInline.Styled(
            style = InlineStyle.LargeText,
            children = listOf(PostInline.Text("愚人节快乐！")),
        ),
        PostInline.Text("（虽然有点晚了）"),
    ).inOrder()
}
```

- [ ] **Step 2: Add one failing parser regression for decorative SVG icon fallback**

```kotlin
@Test
fun parseInlineHtml_replacesDecorativeSvgIconWithAccessibleText() {
    val inlines = parseInlineHtml(
        """
            <svg class="fa d-icon d-icon-far-image svg-icon" aria-label="图片"></svg>附件
        """.trimIndent(),
    )

    assertThat(inlines).containsExactly(
        PostInline.Text("图片附件"),
    )
}
```

- [ ] **Step 3: Run the new regression tests and fix only the gaps they expose**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsDiscourseDetailsSnippetToStructuredSummaryAndLargeTextBody" \
  --tests "io.github.baba983.nativedo.core.ui.PostInlineParserTest.parseInlineHtml_replacesDecorativeSvgIconWithAccessibleText"
```

Expected: FAIL if summary parsing, `LargeText`, or decorative icon fallback is still incomplete.

- [ ] **Step 4: Run full verification for the module**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

Expected:

- `:app:testDebugUnitTest` PASS
- `:app:assembleDebug` PASS

- [ ] **Step 5: Commit**

```bash
git add \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineParserTest.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/PostInlineTextTest.kt
git commit -m "test(renderer): 补齐行内富文本回归"
```

---

## Self-Review

### Scope Coverage

- `PostInline` minimal model has a dedicated task
- `RichTextBlock` has a dedicated task
- `font-size -> LargeText` mapping has parser and builder coverage
- `details > summary` moves off raw HTML into structured content
- Existing inline image loading is preserved by reusing `applyInlineImagePlaceholders()`
- Existing internal link parsing is preserved by reusing `parseInternalLinkDestination()`

### Intentional Deferral

- This plan does not add mention parsing
- This plan does not add strike-through parsing
- This plan does not convert every remaining `HtmlBlock`
- This plan does not add onebox, poll, table, or plugin block rendering

Those are explicitly deferred so this phase stays small and ships a stable inline foundation first.

### Placeholder Scan

- No `TODO` / `TBD`
- All code-touching steps include concrete code snippets
- All tests include exact commands and expected outcomes
- All commit steps use repo-compliant `type(scope): 中文摘要` messages
