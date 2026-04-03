# Discourse Post Renderer Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Android post rendering to a block-first Phase 1 AST by extracting parser boundaries and adding native `DetailsBlock`, `QuoteBlock`, `CodeBlock`, and `ListBlock` support while preserving `HtmlBlock` fallback.

**Architecture:** Extract shared post body block models and parser logic out of `HtmlPostBodyView.kt`, then add a small block renderer dispatcher for new native blocks. Keep `HtmlBlock` as the compatibility path for unknown or still-unsupported structures, and explicitly defer inline AST work to a follow-up plan.

**Tech Stack:** Kotlin, Jetpack Compose, Jsoup, HtmlCompat, JUnit4, Truth, Gradle unit tests

---

## File Map

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
  - Shared block models used by parser and renderer
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
  - `cooked HTML -> List<PostBodyBlock>` parsing
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
  - Compose renderers for native blocks and the top-level block dispatcher
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
  - Keep entrypoint, image viewer, and `HtmlTextBlock`; delegate parsing and block rendering
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`
  - Keep parser regressions in one place; add new block-structure tests

Out of scope for this plan:

- `PostInline` / inline AST
- `font-size` to span mapping
- `onebox`, `poll`, plugin-specific blocks

---

### Task 1: Extract Shared Block Models And Parser Boundary

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add one characterization test that proves the parser split does not change existing image behavior**

```kotlin
@Test
fun parsePostBodyBlocks_keepsExistingSingleAndGridImageBehavior() {
    val html = """
        <p>Hello</p>
        <a class="lightbox" href="/uploads/default/original/1X/original.png">
          <img src="/uploads/default/optimized/1X/thumb_2_690x388.png" width="690" height="388" />
        </a>
        <div class="d-image-grid" data-columns="2">
          <img src="/uploads/default/original/1X/first.png" width="1200" height="900" />
          <img src="/uploads/default/original/1X/second.png" width="900" height="1200" />
        </div>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks[0]).isInstanceOf(PostBodyBlock.HtmlBlock::class.java)
    assertThat(blocks[1]).isInstanceOf(PostBodyBlock.ImageBlock::class.java)
    assertThat(blocks[2]).isInstanceOf(PostBodyBlock.ImageGridBlock::class.java)
}
```

- [ ] **Step 2: Run the targeted parser test to capture the green baseline**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest"
```

Expected: PASS. This is a characterization checkpoint before the file split.

- [ ] **Step 3: Create `PostBodyBlocks.kt` and move the existing shared models there**

```kotlin
package io.github.baba983.nativedo.core.ui

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
}

data class PostBodyImage(
    val rawUrl: String,
    val originalUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
```

- [ ] **Step 4: Create `PostBodyBlockParser.kt` and move the current parser and image extraction helpers there without changing behavior**

```kotlin
package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val defaultGridColumns = 2
private val standaloneImageContainers = setOf("p", "div", "figure")

internal fun parsePostBodyBlocks(html: String): List<PostBodyBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val blocks = mutableListOf<PostBodyBlock>()
    val htmlBuffer = mutableListOf<Node>()

    fun flushHtmlBuffer() {
        if (htmlBuffer.isEmpty()) return
        val fragment = htmlBuffer.joinToString(separator = "") { node -> node.outerHtml() }.trim()
        htmlBuffer.clear()
        if (fragment.isNotBlank()) {
            blocks += PostBodyBlock.HtmlBlock(fragment)
        }
    }

    body.childNodes().forEach { node ->
        when {
            node is TextNode && node.text().isBlank() -> {
                if (htmlBuffer.isNotEmpty()) htmlBuffer += node.clone()
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
            node is Element -> {
                val image = node.extractStandaloneImage()
                if (image != null) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.ImageBlock(image)
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
```

- [ ] **Step 5: Slim `HtmlPostBodyView.kt` so it imports parser/models instead of declaring them inline**

```kotlin
@Composable
fun HtmlPostBodyView(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { parsePostBodyBlocks(html) }
    val galleryImages = remember(blocks) { collectGalleryImages(blocks) }
    // existing rendering remains unchanged in this task
}
```

- [ ] **Step 6: Re-run the parser test to confirm the file split is behavior-neutral**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest"
```

Expected: PASS with the new files in use.

- [ ] **Step 7: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "refactor(renderer): 拆出帖子块级解析骨架"
```

---

### Task 2: Introduce `DetailsBlock` And Native Expandable Rendering

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add a failing test that requires `details` to become a real block instead of ad hoc expanded HTML/image fragments**

```kotlin
@Test
fun parsePostBodyBlocks_mapsDetailsToDetailsBlock() {
    val html = """
        <details open="">
          <summary>给大家看一下</summary>
          <div class="lightbox-wrapper">
            <a class="lightbox" href="/uploads/default/original/1X/original.jpeg">
              <img src="/uploads/default/optimized/1X/thumb_2_375x500.jpeg" width="375" height="500" />
            </a>
          </div>
          <p>愚人节快乐</p>
        </details>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks).containsExactly(
        PostBodyBlock.DetailsBlock(
            summaryHtml = "给大家看一下",
            expandedByDefault = true,
            children = listOf(
                PostBodyBlock.ImageBlock(
                    PostBodyImage(
                        rawUrl = "/uploads/default/optimized/1X/thumb_2_375x500.jpeg",
                        originalUrl = "/uploads/default/original/1X/original.jpeg",
                        width = 375,
                        height = 500,
                    ),
                ),
                PostBodyBlock.HtmlBlock("<p>愚人节快乐</p>"),
            ),
        ),
    )
}
```

- [ ] **Step 2: Run the targeted test and verify it fails because `DetailsBlock` does not exist yet**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsDetailsToDetailsBlock"
```

Expected: FAIL with unresolved `DetailsBlock` or assertion mismatch against the current ad hoc expansion.

- [ ] **Step 3: Add `DetailsBlock` to `PostBodyBlocks.kt`**

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
}
```

- [ ] **Step 4: Parse `details` recursively in `PostBodyBlockParser.kt`**

```kotlin
private fun parsePostBodyNodes(nodes: List<Node>): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val htmlBuffer = mutableListOf<Node>()

    fun flushHtmlBuffer() { /* keep existing flush logic */ }

    nodes.forEach { node ->
        when {
            node is Element && node.tagName() == "details" -> {
                flushHtmlBuffer()
                blocks += node.toDetailsBlock()
            }
            // keep image-grid / image / fallback branches
        }
    }

    flushHtmlBuffer()
    return blocks
}

private fun Element.toDetailsBlock(): PostBodyBlock.DetailsBlock {
    val summaryElement = children().firstOrNull { child -> child.tagName() == "summary" }
    val childNodes = childNodes().filterNot { node ->
        node is Element && node.tagName() == "summary"
    }
    return PostBodyBlock.DetailsBlock(
        summaryHtml = summaryElement?.html().orEmpty().trim(),
        children = parsePostBodyNodes(childNodes),
        expandedByDefault = hasAttr("open"),
    )
}
```

- [ ] **Step 5: Add a native `DetailsBlock` renderer and dispatch it from `HtmlPostBodyView.kt`**

```kotlin
@Composable
internal fun RenderPostBodyBlock(
    block: PostBodyBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is PostBodyBlock.HtmlBlock -> HtmlTextBlock(
            html = block.html,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            modifier = modifier,
        )
        is PostBodyBlock.ImageBlock -> PostImageView(/* existing args */)
        is PostBodyBlock.ImageGridBlock -> PostImageGrid(/* existing args */)
        is PostBodyBlock.DetailsBlock -> DetailsBlockView(
            block = block,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onImageClick = onImageClick,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 6: Verify the parser test and debug build both pass**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsDetailsToDetailsBlock"
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

Expected: both PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "feat(renderer): 支持 details 块渲染"
```

---

### Task 3: Introduce `QuoteBlock`

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add a failing parser test for blockquote**

```kotlin
@Test
fun parsePostBodyBlocks_mapsBlockquoteToQuoteBlock() {
    val html = """
        <blockquote>
          <p>引用内容</p>
          <p>第二行</p>
        </blockquote>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks).containsExactly(
        PostBodyBlock.QuoteBlock(
            children = listOf(
                PostBodyBlock.HtmlBlock("<p>引用内容</p><p>第二行</p>"),
            ),
        ),
    )
}
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsBlockquoteToQuoteBlock"
```

Expected: FAIL because `QuoteBlock` is missing.

- [ ] **Step 3: Add `QuoteBlock` to the model and parse `blockquote` recursively**

```kotlin
data class QuoteBlock(
    val children: List<PostBodyBlock>,
) : PostBodyBlock
```

```kotlin
node is Element && node.tagName() == "blockquote" -> {
    flushHtmlBuffer()
    blocks += PostBodyBlock.QuoteBlock(
        children = parsePostBodyNodes(node.childNodes()),
    )
}
```

- [ ] **Step 4: Render the quote block with a left accent bar and recursive children**

```kotlin
@Composable
internal fun QuoteBlockView(
    block: PostBodyBlock.QuoteBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            block.children.forEach { child ->
                RenderPostBodyBlock(
                    block = child,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onImageClick = onImageClick,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Re-run the quote test and the full parser suite**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsBlockquoteToQuoteBlock"
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "feat(renderer): 支持引用块渲染"
```

---

### Task 4: Introduce `CodeBlock`

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add a failing parser test for fenced code**

```kotlin
@Test
fun parsePostBodyBlocks_mapsPreCodeToCodeBlock() {
    val html = """
        <pre><code class="language-kotlin">val answer = 42</code></pre>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks).containsExactly(
        PostBodyBlock.CodeBlock(
            code = "val answer = 42",
            languageHint = "kotlin",
        ),
    )
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsPreCodeToCodeBlock"
```

Expected: FAIL because `CodeBlock` is not implemented.

- [ ] **Step 3: Add `CodeBlock` to the shared model and parse `pre > code`**

```kotlin
data class CodeBlock(
    val code: String,
    val languageHint: String?,
) : PostBodyBlock
```

```kotlin
node is Element && node.tagName() == "pre" -> {
    val codeElement = node.children().firstOrNull { child -> child.tagName() == "code" }
    if (codeElement != null) {
        flushHtmlBuffer()
        val languageHint = codeElement.classNames()
            .firstOrNull { className -> className.startsWith("language-") }
            ?.removePrefix("language-")
        blocks += PostBodyBlock.CodeBlock(
            code = codeElement.text(),
            languageHint = languageHint,
        )
    } else {
        htmlBuffer += node.clone()
    }
}
```

- [ ] **Step 4: Render the code block with a monospace style and container background**

```kotlin
@Composable
internal fun CodeBlockView(
    block: PostBodyBlock.CodeBlock,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        block.languageHint?.takeIf { it.isNotBlank() }?.let { language ->
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
```

- [ ] **Step 5: Re-run the targeted test and `assembleDebug`**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsPreCodeToCodeBlock"
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "feat(renderer): 支持代码块渲染"
```

---

### Task 5: Introduce `ListBlock`

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`

- [ ] **Step 1: Add failing tests for unordered and ordered lists**

```kotlin
@Test
fun parsePostBodyBlocks_mapsUnorderedListToListBlock() {
    val html = """
        <ul>
          <li><p>第一项</p></li>
          <li><p>第二项</p></li>
        </ul>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat(blocks).containsExactly(
        PostBodyBlock.ListBlock(
            ordered = false,
            items = listOf(
                ListItemBlock(children = listOf(PostBodyBlock.HtmlBlock("<p>第一项</p>"))),
                ListItemBlock(children = listOf(PostBodyBlock.HtmlBlock("<p>第二项</p>"))),
            ),
        ),
    )
}

@Test
fun parsePostBodyBlocks_mapsOrderedListToListBlock() {
    val html = """
        <ol>
          <li><p>一</p></li>
          <li><p>二</p></li>
        </ol>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)

    assertThat((blocks.single() as PostBodyBlock.ListBlock).ordered).isTrue()
}
```

- [ ] **Step 2: Run the two list tests and confirm they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsUnorderedListToListBlock" --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsOrderedListToListBlock"
```

Expected: FAIL because `ListBlock` and `ListItemBlock` do not exist yet.

- [ ] **Step 3: Add the list models and parse `ul / ol / li` recursively**

```kotlin
data class ListBlock(
    val ordered: Boolean,
    val items: List<ListItemBlock>,
) : PostBodyBlock

data class ListItemBlock(
    val children: List<PostBodyBlock>,
)
```

```kotlin
node is Element && (node.tagName() == "ul" || node.tagName() == "ol") -> {
    flushHtmlBuffer()
    blocks += PostBodyBlock.ListBlock(
        ordered = node.tagName() == "ol",
        items = node.children()
            .filter { child -> child.tagName() == "li" }
            .map { item ->
                ListItemBlock(
                    children = parsePostBodyNodes(item.childNodes()),
                )
            },
    )
}
```

- [ ] **Step 4: Render the list block with markers and recursive children**

```kotlin
@Composable
internal fun ListBlockView(
    block: PostBodyBlock.ListBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item.children.forEach { child ->
                        RenderPostBodyBlock(
                            block = child,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            onImageClick = onImageClick,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Re-run the list tests and the full parser suite**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsUnorderedListToListBlock" --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsOrderedListToListBlock"
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "feat(renderer): 支持列表块渲染"
```

---

### Task 6: Mixed-Nesting Regression And Final Verification

**Files:**
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`

- [ ] **Step 1: Add a failing regression test for a nested structure that combines the new blocks**

```kotlin
@Test
fun parsePostBodyBlocks_mapsNestedDetailsQuoteAndList() {
    val html = """
        <details open="">
          <summary>展开查看</summary>
          <blockquote>
            <ul>
              <li><p>第一项</p></li>
            </ul>
          </blockquote>
        </details>
    """.trimIndent()

    val blocks = parsePostBodyBlocks(html)
    val details = blocks.single() as PostBodyBlock.DetailsBlock
    val quote = details.children.single() as PostBodyBlock.QuoteBlock
    val list = quote.children.single() as PostBodyBlock.ListBlock

    assertThat(details.summaryHtml).isEqualTo("展开查看")
    assertThat(list.ordered).isFalse()
    assertThat(list.items).hasSize(1)
}
```

- [ ] **Step 2: Run the nested regression test and fix any missing recursion edge cases**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.ui.HtmlPostBodyViewTest.parsePostBodyBlocks_mapsNestedDetailsQuoteAndList"
```

Expected: FAIL if recursion, wrapper flattening, or renderer dispatch is incomplete.

- [ ] **Step 3: Make the minimal recursion fixes in parser/renderer only where the test points**

```kotlin
private fun Element.toDetailsBlock(): PostBodyBlock.DetailsBlock {
    val summaryElement = children().firstOrNull { child -> child.tagName() == "summary" }
    val childNodes = childNodes().filterNot { node ->
        node is Element && node.tagName() == "summary"
    }
    return PostBodyBlock.DetailsBlock(
        summaryHtml = summaryElement?.html().orEmpty().trim(),
        children = parsePostBodyNodes(childNodes),
        expandedByDefault = hasAttr("open"),
    )
}
```

```kotlin
when (block) {
    is PostBodyBlock.DetailsBlock -> DetailsBlockView(/* existing args */)
    is PostBodyBlock.QuoteBlock -> QuoteBlockView(/* existing args */)
    is PostBodyBlock.CodeBlock -> CodeBlockView(block = block, modifier = modifier)
    is PostBodyBlock.ListBlock -> ListBlockView(/* existing args */)
    // keep existing HtmlBlock/ImageBlock/ImageGridBlock branches
}
```

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
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt \
  apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt \
  apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt
git commit -m "test(renderer): 补齐块级渲染回归覆盖"
```

---

## Self-Review

### Spec Coverage

- `DetailsBlock` has a dedicated task
- `QuoteBlock` has a dedicated task
- `CodeBlock` has a dedicated task
- `ListBlock` has a dedicated task
- Parser extraction and block renderer boundaries have a dedicated task
- Fallback retention is preserved because `HtmlBlock` is never removed in Phase 1

### Intentional Deferral

The spec’s Phase 2 inline AST is intentionally excluded from this plan. That is not a gap; it is the explicit scope boundary for this Phase 1 plan.

### Placeholder Scan

- No `TBD` / `TODO`
- All code-touching steps include code snippets
- All test steps include exact commands and expected outcomes

