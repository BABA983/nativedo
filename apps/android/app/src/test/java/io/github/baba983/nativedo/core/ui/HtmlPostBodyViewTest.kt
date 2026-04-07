package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.IntRect
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class HtmlPostBodyViewTest {
    @Test
    fun calculatePostImageViewerLaunchSource_translatesClippedBoundsIntoScreenSpace() {
        val launchSource = calculatePostImageViewerLaunchSource(
            imageBoundsOnScreen = IntRect(left = 24, top = 220, right = 324, bottom = 620),
            visibleBoundsOnScreen = IntRect(left = 24, top = 276, right = 324, bottom = 516),
            cornerRadiusPx = 18f,
        )

        assertThat(launchSource).isEqualTo(
            PostImageViewerLaunchSource(
                boundsOnScreen = IntRect(left = 24, top = 276, right = 324, bottom = 516),
                fullBoundsOnScreen = IntRect(left = 24, top = 220, right = 324, bottom = 620),
                cornerRadiusPx = 18f,
            ),
        )
    }

    @Test
    fun calculatePostImageViewerLaunchSource_returnsNullWhenClippedBoundsCollapse() {
        assertThat(
            calculatePostImageViewerLaunchSource(
                imageBoundsOnScreen = IntRect(left = 24, top = 220, right = 324, bottom = 620),
                visibleBoundsOnScreen = IntRect(left = 24, top = 276, right = 24, bottom = 516),
                cornerRadiusPx = 18f,
            ),
        ).isNull()
    }

    @Test
    fun parsePostBodyBlocks_extractsLightboxImageBetweenHtmlBlocks() {
        val html = """
            <p>Hello</p>
            <a class="lightbox" href="/uploads/default/original/1X/original.png">
              <img src="/uploads/default/optimized/1X/thumb_2_690x388.png" width="690" height="388" />
            </a>
            <p>World</p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(3)
        assertThat(blocks[0]).isEqualTo(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "Hello",
                    entities = emptyList(),
                ),
            ),
        )
        assertThat((blocks[1] as PostBodyBlock.ImageBlock).image).isEqualTo(
            PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/thumb_2_690x388.png",
                originalUrl = "/uploads/default/original/1X/original.png",
                width = 690,
                height = 388,
            ),
        )
        assertThat(blocks[2]).isEqualTo(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "World",
                    entities = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsFixtureTopicDetailIntoVisibleBlocks() {
        val cooked = loadFixtureCookedPosts("discourse/topic-detail.json").single()

        assertThat(parsePostBodyBlocks(cooked)).containsExactly(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "Hello from Android.",
                    entities = emptyList(),
                ),
            ),
            PostBodyBlock.ImageBlock(
                image = PostBodyImage(
                    rawUrl = "https://linux.do/uploads/default/original/1X/demo.png",
                ),
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_splitsParagraphWrappedImageIntoImageAndParagraphBlocks() {
        val blocks = parsePostBodyBlocks(
            """
                <p dir="auto">
                  <img src="https://cdn3.linux.do/original/4X/8/2/d/demo.png" alt="image" width="153" height="69">
                  <br>
                  <img src="/images/emoji/twemoji/grin.png?v=14" title=":grin:" class="emoji" alt=":grin:">
                  支持一手
                </p>
            """.trimIndent(),
        )

        assertThat(blocks).containsExactly(
            PostBodyBlock.ImageBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/original/4X/8/2/d/demo.png",
                    width = 153,
                    height = 69,
                ),
            ),
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = ":grin:支持一手",
                    entities = listOf(
                        TextEntity.Emoji(
                            start = 0,
                            end = 6,
                            rawUrl = "/images/emoji/twemoji/grin.png?v=14",
                            fallback = ":grin:",
                        ),
                    ),
                ),
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_splitsParagraphWrappedImageWithoutDroppingInlineSpaces() {
        val blocks = parsePostBodyBlocks(
            """
                <p><strong>Hello</strong> world <img src="/uploads/default/original/1X/demo.png" width="120" height="90"></p>
            """.trimIndent(),
        )

        assertThat(blocks).containsExactly(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "Hello world",
                    entities = listOf(
                        TextEntity.Bold(start = 0, end = 5),
                    ),
                ),
            ),
            PostBodyBlock.ImageBlock(
                image = PostBodyImage(
                    rawUrl = "/uploads/default/original/1X/demo.png",
                    width = 120,
                    height = 90,
                ),
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_extractsDiscourseImageGrid() {
        val html = """
            <div class="d-image-grid" data-columns="2">
              <img src="/uploads/default/original/1X/first.png" width="1200" height="900" />
              <img src="/uploads/default/original/1X/second.png" width="900" height="1200" />
            </div>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        val grid = blocks.single() as PostBodyBlock.ImageGridBlock
        assertThat(grid.columns).isEqualTo(2)
        assertThat(grid.images).containsExactly(
            PostBodyImage(
                rawUrl = "/uploads/default/original/1X/first.png",
                width = 1200,
                height = 900,
            ),
            PostBodyImage(
                rawUrl = "/uploads/default/original/1X/second.png",
                width = 900,
                height = 1200,
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_mapsEmojiImageToParagraphBlock() {
        val html = """
            <p><img class="emoji" src="/images/emoji/twemoji/face_savoring_food.png?v=15" title=":face_savoring_food:" alt=":face_savoring_food:" width="20" height="20" /></p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isEqualTo(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = ":face_savoring_food:",
                    entities = listOf(
                        TextEntity.Emoji(
                            start = 0,
                            end = ":face_savoring_food:".length,
                            rawUrl = "/images/emoji/twemoji/face_savoring_food.png?v=15",
                            fallback = ":face_savoring_food:",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsSiteIconToParagraphBlock() {
        val html = """
            <p><img class="site-icon" src="https://github.com/favicon.ico" width="16" height="16" />GitHub</p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isEqualTo(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "GitHub",
                    entities = listOf(
                        TextEntity.SiteIcon(
                            start = 0,
                            end = 0,
                            rawUrl = "https://github.com/favicon.ico",
                            fallback = "",
                        ),
                    ),
                ),
            ),
        )
    }

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

        assertThat(blocks).hasSize(3)
        assertThat(blocks[0]).isInstanceOf(PostBodyBlock.ParagraphBlock::class.java)
        assertThat(blocks[1]).isInstanceOf(PostBodyBlock.ImageBlock::class.java)
        assertThat(blocks[2]).isInstanceOf(PostBodyBlock.ImageGridBlock::class.java)
    }

    @Test
    fun parsePostBodyBlocks_mapsParagraphToTextDocumentBlock() {
        val html = """
            <p><strong>Hello</strong><br><span style="font-size:400%">World</span></p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "Hello\nWorld",
                    entities = listOf(
                        TextEntity.Bold(start = 0, end = 5),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_trimsTrailingBrBeforeFollowingLightbox() {
        val html = """
            <p dir="auto">用冰佬的公益  这是日志情况<br></p>
            <div class="lightbox-wrapper" dir="auto">
              <a class="lightbox" href="https://cdn3.linux.do/original/demo.png" title="image">
                <img src="https://cdn3.linux.do/optimized/demo.png" alt="image" width="690" height="359">
                <div class="meta">
                  <span class="filename">image</span>
                  <span class="informations">1464×762 65.9 KB</span>
                </div>
              </a>
            </div>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "用冰佬的公益 这是日志情况",
                    entities = emptyList(),
                ),
            ),
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/optimized/demo.png",
                    originalUrl = "https://cdn3.linux.do/original/demo.png",
                    width = 690,
                    height = 359,
                ),
                filename = "image",
                information = "1464×762 65.9 KB",
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_mapsHeadingToTextDocumentBlock() {
        val block = parsePostBodyBlocks("<h2>标题</h2>").single() as PostBodyBlock.HeadingBlock

        assertThat(block.level).isEqualTo(2)
        assertThat(block.content).isEqualTo(
            TextDocument(
                text = "标题",
                entities = emptyList(),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_splitsHeadingWrappedLightboxIntoHeadingAndLightboxBlocks() {
        val html = """
            <h2 dir="auto">
              <a name="p-1" class="anchor" href="#p-1" aria-label="标题链接"></a>改前<br>
              <div class="lightbox-wrapper">
                <a class="lightbox" href="https://cdn3.linux.do/original/heading-demo.jpeg" title="image">
                  <img src="https://cdn3.linux.do/optimized/heading-demo.jpeg" alt="image" width="690" height="163">
                  <div class="meta">
                    <span class="filename">image</span>
                    <span class="informations">2936×696 184 KB</span>
                  </div>
                </a>
              </div>
            </h2>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.HeadingBlock(
                level = 2,
                content = TextDocument(
                    text = "改前",
                    entities = emptyList(),
                ),
            ),
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/optimized/heading-demo.jpeg",
                    originalUrl = "https://cdn3.linux.do/original/heading-demo.jpeg",
                    width = 690,
                    height = 163,
                ),
                filename = "image",
                information = "2936×696 184 KB",
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_mapsDetailsSummaryToTextDocument() {
        val html = """
            <details open="">
              <summary><img class="emoji" src="/images/emoji/twemoji/distorted_face.png?v=15" title=":distorted_face:" alt=":distorted_face:">展开查看</summary>
              <p>正文</p>
            </details>
        """.trimIndent()

        val details = parsePostBodyBlocks(html).single() as PostBodyBlock.DetailsBlock

        assertThat(details.summary).isEqualTo(
            TextDocument(
                text = ":distorted_face:展开查看",
                entities = listOf(
                    TextEntity.Emoji(
                        start = 0,
                        end = ":distorted_face:".length,
                        rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                        fallback = ":distorted_face:",
                    ),
                ),
            ),
        )
    }

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
                summary = TextDocument(
                    text = "给大家看一下",
                    entities = emptyList(),
                ),
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
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "愚人节快乐",
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsDiscourseDetailsSnippetToStructuredSummaryAndLargeTextBody() {
        val html = """
            <details open="">
              <summary>给大家看一下我花钱购买的 CODEX ，和我品鉴它的真实感受</summary>
              <p><span style="font-size:400%">愚人节快乐！</span>（虽然有点晚了）</p>
            </details>
        """.trimIndent()

        val details = parsePostBodyBlocks(html).single() as PostBodyBlock.DetailsBlock
        val paragraph = details.children.single() as PostBodyBlock.ParagraphBlock

        assertThat(details.summary).isEqualTo(
            TextDocument(
                text = "给大家看一下我花钱购买的 CODEX ，和我品鉴它的真实感受",
                entities = emptyList(),
            ),
        )
        assertThat(paragraph.content).isEqualTo(
            TextDocument(
                text = "愚人节快乐！（虽然有点晚了）",
                entities = emptyList(),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_keepsUnsupportedTableAsUnknownBlock() {
        val html = """
            <table>
              <tr><td>单元格</td></tr>
            </table>
        """.trimIndent()

        val block = parsePostBodyBlocks(html).single() as PostBodyBlock.UnknownBlock

        assertThat(block.tagName).isEqualTo("table")
        assertThat(block.rawHtml).contains("<table>")
        assertThat(block.rawHtml).contains("<td>单元格</td>")
    }

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
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "引用内容",
                            entities = emptyList(),
                        ),
                    ),
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "第二行",
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsMarkdownCalloutBlockquoteToCalloutBlock() {
        val html = """
            <blockquote>
              <p>[!NOTE] 用途</p>
              <p>可以完美CC</p>
            </blockquote>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.CalloutBlock(
                spec = DiscourseCalloutSpec(
                    type = "note",
                    icon = "far-pen-to-square",
                    color = "#086ddd",
                ),
                title = TextDocument(
                    text = "用途",
                    entities = emptyList(),
                ),
                children = listOf(
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "可以完美CC",
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsRenderedCalloutBlockquoteToCalloutBlock() {
        val html = """
            <blockquote data-callout-type="note" class="callout">
              <div class="callout-title">
                <span class="callout-icon">
                  <svg class="fa d-icon d-icon-triangle-exclamation svg-icon">
                    <use href="#triangle-exclamation"></use>
                  </svg>
                </span>
                <span class="callout-title-inner">注意</span>
              </div>
              <div class="callout-content">
                <p>有风险</p>
              </div>
            </blockquote>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.CalloutBlock(
                spec = DiscourseCalloutSpec(
                    type = "note",
                    icon = "triangle-exclamation",
                    color = "#086ddd",
                ),
                title = TextDocument(
                    text = "注意",
                    entities = emptyList(),
                ),
                children = listOf(
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "有风险",
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsDiscourseQuoteAsideToDiscourseQuoteBlock() {
        val html = """
            <aside class="quote no-group" data-username="bigcat2333" data-display-name="大猫">
              <div class="title" data-can-navigate-to-post="/t/topic/1888983">
                <img alt="" width="24" height="24" src="https://cdn.linux.do/user_avatar/linux.do/bigcat2333/48/970018_2.png" class="avatar">
                大猫:
                <a href="/t/topic/1888983">引用的帖子</a>
                <div class="quote-controls">
                  <a href="/t/topic/1888983" class="btn no-text btn-flat back">返回</a>
                </div>
              </div>
              <blockquote id="quote-id-1888983-1-0">
                <div class="post__contents-cooked-quote">
                  <p dir="auto">引用内容</p>
                </div>
              </blockquote>
            </aside>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.DiscourseQuoteBlock(
                authorDisplayName = "大猫",
                authorUsername = "bigcat2333",
                avatarUrl = "https://cdn.linux.do/user_avatar/linux.do/bigcat2333/48/970018_2.png",
                title = TextDocument(
                    text = "引用的帖子",
                    entities = listOf(
                        TextEntity.Link(start = 0, end = 5, rawUrl = "/t/topic/1888983"),
                    ),
                ),
                children = listOf(
                    PostBodyBlock.ParagraphBlock(
                        content = TextDocument(
                            text = "引用内容",
                            entities = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsOneboxAsideToOneboxBlock() {
        val html = """
            <aside class="onebox allowlistedgeneric" data-onebox-src="https://firebase.studio" dir="auto">
              <header class="source">
                <img src="https://cdn3.linux.do/original/4X/a/6/8/a68a61b53e6d7f89a9307027d713418f7c1ddf91.png" class="site-icon" alt="" width="32" height="32">
                <a href="https://firebase.studio" target="_blank" rel="noopener nofollow ugc" class="normal-external-link-icon">firebase.studio</a>
              </header>
              <article class="onebox-body">
                <div class="aspect-image" style="--aspect-ratio:690/362;"><img src="https://cdn3.linux.do/optimized/4X/f/0/9/f09a44211edd39a45aeca1ac9ca2207d57c93b6d_2_690x362.jpeg" class="thumbnail" alt="" width="690" height="362"></div>
                <h3><a href="https://firebase.studio" target="_blank" rel="noopener nofollow ugc" class="normal-external-link-icon">Firebase Studio</a></h3>
                <p>Firebase Studio is an entirely web-based workspace for full-stack application development, complete with the latest generative AI from Gemini, and full-fidelity app previews, powered by cloud emulators.</p>
              </article>
            </aside>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.OneboxBlock(
                sourceLabel = "firebase.studio",
                sourceUrl = "https://firebase.studio",
                sourceIconUrl = "https://cdn3.linux.do/original/4X/a/6/8/a68a61b53e6d7f89a9307027d713418f7c1ddf91.png",
                title = "Firebase Studio",
                targetUrl = "https://firebase.studio",
                description = "Firebase Studio is an entirely web-based workspace for full-stack application development, complete with the latest generative AI from Gemini, and full-fidelity app previews, powered by cloud emulators.",
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/optimized/4X/f/0/9/f09a44211edd39a45aeca1ac9ca2207d57c93b6d_2_690x362.jpeg",
                    width = 690,
                    height = 362,
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsLightboxWithMetaToLightboxBlock() {
        val html = """
            <a class="lightbox" href="https://cdn3.linux.do/original/4X/2/3/2/2328032b9df484b357faea793f31a24be265f3c0.png" data-download-href="/uploads/short-url/510r89dit4ivW8enSX0MpxM2Qog.png?dl=1" title="image">
              <img src="https://cdn3.linux.do/optimized/4X/2/3/2/2328032b9df484b357faea793f31a24be265f3c0_2_618x500.png" alt="image" width="618" height="500">
              <div class="meta">
                <span class="filename">image</span>
                <span class="informations">670×542 31.3 KB</span>
              </div>
            </a>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/optimized/4X/2/3/2/2328032b9df484b357faea793f31a24be265f3c0_2_618x500.png",
                    originalUrl = "https://cdn3.linux.do/original/4X/2/3/2/2328032b9df484b357faea793f31a24be265f3c0.png",
                    width = 618,
                    height = 500,
                ),
                filename = "image",
                information = "670×542 31.3 KB",
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsStrongWrappedLightboxWrapperToLightboxBlock() {
        val html = """
            <strong dir="auto">
              <div class="lightbox-wrapper">
                <a class="lightbox" href="https://cdn3.linux.do/original/4X/6/c/5/6c5132c477309e7152318f1feedfe00657f3fcbf.png" data-download-href="/uploads/short-url/fsdx4kPsCrt9cmifzxCEyRIdut9.png?dl=1" title="image">
                  <img src="https://cdn3.linux.do/original/4X/6/c/5/6c5132c477309e7152318f1feedfe00657f3fcbf.png" alt="image" width="690" height="476">
                  <div class="meta">
                    <svg class="fa d-icon d-icon-far-image svg-icon" aria-hidden="true"></svg>
                    <span class="filename">image</span>
                    <span class="informations">720×497 30.9 KB</span>
                    <svg class="fa d-icon d-icon-discourse-expand svg-icon" aria-hidden="true"></svg>
                  </div>
                </a>
              </div>
            </strong>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/original/4X/6/c/5/6c5132c477309e7152318f1feedfe00657f3fcbf.png",
                    originalUrl = "https://cdn3.linux.do/original/4X/6/c/5/6c5132c477309e7152318f1feedfe00657f3fcbf.png",
                    width = 690,
                    height = 476,
                ),
                filename = "image",
                information = "720×497 30.9 KB",
            ),
        )
    }

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
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.ParagraphBlock(
                                content = TextDocument(
                                    text = "第一项",
                                    entities = emptyList(),
                                ),
                            ),
                        ),
                    ),
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.ParagraphBlock(
                                content = TextDocument(
                                    text = "第二项",
                                    entities = emptyList(),
                                ),
                            ),
                        ),
                    ),
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

    @Test
    fun parsePostBodyBlocks_mapsHrToHorizontalRuleBlock() {
        val blocks = parsePostBodyBlocks(
            """
                <p>上文</p>
                <hr>
                <p>下文</p>
            """.trimIndent(),
        )

        assertThat(blocks).containsExactly(
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "上文",
                    entities = emptyList(),
                ),
            ),
            PostBodyBlock.HorizontalRuleBlock,
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "下文",
                    entities = emptyList(),
                ),
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_mergesTopLevelInlineNoteAfterLightbox() {
        val html = """
            <div class="lightbox-wrapper" dir="auto">
              <a class="lightbox" href="https://cdn3.linux.do/original/demo.png" title="image">
                <img src="https://cdn3.linux.do/optimized/demo.png" alt="image" width="690" height="350">
                <div class="meta">
                  <span class="filename">image</span>
                  <span class="informations">2756×1400 182 KB</span>
                </div>
              </a>
            </div>
            <br>
            * <em dir="auto">数据图示来自 SimilarWeb，仅供参考。</em>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "https://cdn3.linux.do/optimized/demo.png",
                    originalUrl = "https://cdn3.linux.do/original/demo.png",
                    width = 690,
                    height = 350,
                ),
                filename = "image",
                information = "2756×1400 182 KB",
            ),
            PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "* 数据图示来自 SimilarWeb，仅供参考。",
                    entities = listOf(
                        TextEntity.Italic(
                            start = 2,
                            end = "* 数据图示来自 SimilarWeb，仅供参考。".length,
                        ),
                    ),
                ),
            ),
        ).inOrder()
    }

    @Test
    fun parsePostBodyBlocks_mergesInlineListItemContentIntoSingleParagraphBlock() {
        val html = """
            <ul>
              <li><strong>资源有限</strong>：因为是公益项目，服务器资源并不宽裕。</li>
            </ul>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.ListBlock(
                ordered = false,
                items = listOf(
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.ParagraphBlock(
                                content = TextDocument(
                                    text = "资源有限：因为是公益项目，服务器资源并不宽裕。",
                                    entities = listOf(
                                        TextEntity.Bold(
                                            start = 0,
                                            end = "资源有限".length,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_keepsListItemBrInsideParagraphBlock() {
        val html = """
            <ul>
              <li><strong>反馈与建议</strong>：<br>有任何问题欢迎反馈。</li>
            </ul>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.ListBlock(
                ordered = false,
                items = listOf(
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.ParagraphBlock(
                                content = TextDocument(
                                    text = "反馈与建议：\n有任何问题欢迎反馈。",
                                    entities = listOf(
                                        TextEntity.Bold(
                                            start = 0,
                                            end = "反馈与建议".length,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_keepsUnsupportedListItemTableAsUnknownBlock() {
        val html = """
            <ul>
              <li><table><tr><td>不支持</td></tr></table></li>
            </ul>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).containsExactly(
            PostBodyBlock.ListBlock(
                ordered = false,
                items = listOf(
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.UnknownBlock(
                                rawHtml = """
                                    <table>
                                     <tbody>
                                      <tr>
                                       <td>不支持</td>
                                      </tr>
                                     </tbody>
                                    </table>
                                """.trimIndent(),
                                tagName = "table",
                                classes = emptySet(),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

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

        assertThat(details.summary).isEqualTo(
            TextDocument(
                text = "展开查看",
                entities = emptyList(),
            ),
        )
        assertThat(list.ordered).isFalse()
        assertThat(list.items).hasSize(1)
    }

    @Test
    fun resolvePostImageUrl_expandsRelativePathAgainstBaseUrl() {
        val resolved = resolvePostImageUrl(
            baseUrl = "https://linux.do/",
            rawUrl = "/uploads/default/original/1X/demo.png",
        )

        assertThat(resolved).isEqualTo("https://linux.do/uploads/default/original/1X/demo.png")
    }

    @Test
    fun collectGalleryImages_keepsPostOrderAcrossSingleAndGridImages() {
        val blocks = listOf(
            PostBodyBlock.HtmlBlock("<p>Hello</p>"),
            PostBodyBlock.ImageBlock(
                PostBodyImage(
                    rawUrl = "/uploads/default/optimized/1X/single-thumb.png",
                    originalUrl = "/uploads/default/original/1X/single.png",
                ),
            ),
            PostBodyBlock.LightboxBlock(
                image = PostBodyImage(
                    rawUrl = "/uploads/default/optimized/1X/lightbox-thumb.png",
                    originalUrl = "/uploads/default/original/1X/lightbox.png",
                ),
                filename = "lightbox",
                information = "800×600",
            ),
            PostBodyBlock.ImageGridBlock(
                images = listOf(
                    PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-1.png"),
                    PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-2.png"),
                ),
                columns = 2,
            ),
        )

        val galleryImages = collectGalleryImages(blocks)

        assertThat(galleryImages).containsExactly(
            PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/single-thumb.png",
                originalUrl = "/uploads/default/original/1X/single.png",
            ),
            PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/lightbox-thumb.png",
                originalUrl = "/uploads/default/original/1X/lightbox.png",
            ),
            PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-1.png"),
            PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-2.png"),
        ).inOrder()
    }

    @Test
    fun collectGalleryImages_recursesIntoNestedBlocks() {
        val blocks = listOf(
            PostBodyBlock.DetailsBlock(
                summary = TextDocument(
                    text = "详情",
                    entities = emptyList(),
                ),
                expandedByDefault = true,
                children = listOf(
                    PostBodyBlock.ImageBlock(
                        PostBodyImage(
                            rawUrl = "/uploads/default/original/1X/detail.png",
                        ),
                    ),
                    PostBodyBlock.QuoteBlock(
                        children = listOf(
                            PostBodyBlock.ListBlock(
                                ordered = false,
                                items = listOf(
                                    ListItemBlock(
                                        children = listOf(
                                            PostBodyBlock.ImageGridBlock(
                                                images = listOf(
                                                    PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-1.png"),
                                                    PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-2.png"),
                                                ),
                                                columns = 2,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val galleryImages = collectGalleryImages(blocks)

        assertThat(galleryImages).containsExactly(
            PostBodyImage(rawUrl = "/uploads/default/original/1X/detail.png"),
            PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-1.png"),
            PostBodyImage(rawUrl = "/uploads/default/original/1X/grid-2.png"),
        ).inOrder()
    }

    @Test
    fun collectGalleryImages_recursesIntoDiscourseQuoteBlock() {
        val galleryImages = collectGalleryImages(
            listOf(
                PostBodyBlock.DiscourseQuoteBlock(
                    authorDisplayName = "大猫",
                    authorUsername = "bigcat2333",
                    avatarUrl = null,
                    title = null,
                    children = listOf(
                        PostBodyBlock.ImageBlock(
                            PostBodyImage(
                                rawUrl = "/uploads/default/original/1X/quote-image.png",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertThat(galleryImages).containsExactly(
            PostBodyImage(rawUrl = "/uploads/default/original/1X/quote-image.png"),
        )
    }

    @Test
    fun findGalleryStartIndex_matchesClickedGridImage() {
        val galleryImages = listOf(
            PostBodyImage(rawUrl = "/uploads/default/original/1X/first.png"),
            PostBodyImage(rawUrl = "/uploads/default/original/1X/second.png"),
            PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/third-thumb.png",
                originalUrl = "/uploads/default/original/1X/third.png",
            ),
        )

        val startIndex = findGalleryStartIndex(
            galleryImages = galleryImages,
            clickedImage = PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/third-thumb.png",
                originalUrl = "/uploads/default/original/1X/third.png",
            ),
        )

        assertThat(startIndex).isEqualTo(2)
    }

    private fun loadFixtureCookedPosts(resourcePath: String): List<String> {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource(resourcePath),
        ).readText()

        return Json.parseToJsonElement(payload)
            .jsonObject["post_stream"]
            ?.jsonObject
            ?.get("posts")
            ?.jsonArray
            ?.mapNotNull { post ->
                post.jsonObject["cooked"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf(String::isNotBlank)
            }
            .orEmpty()
    }
}
