package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.IntRect
import com.google.common.truth.Truth.assertThat
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
            PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(PostInline.Text("Hello")),
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
            PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(PostInline.Text("World")),
            ),
        )
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
    fun parsePostBodyBlocks_mapsEmojiImageToRichTextBlock() {
        val html = """
            <p><img class="emoji" src="/images/emoji/twemoji/face_savoring_food.png?v=15" title=":face_savoring_food:" alt=":face_savoring_food:" width="20" height="20" /></p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isEqualTo(
            PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(
                    PostInline.Emoji(
                        rawUrl = "/images/emoji/twemoji/face_savoring_food.png?v=15",
                        fallback = ":face_savoring_food:",
                    ),
                ),
            ),
        )
    }

    @Test
    fun parsePostBodyBlocks_mapsSiteIconToRichTextBlock() {
        val html = """
            <p><img class="site-icon" src="https://github.com/favicon.ico" width="16" height="16" />GitHub</p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isEqualTo(
            PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(
                    PostInline.SiteIcon(
                        rawUrl = "https://github.com/favicon.ico",
                        fallback = "",
                    ),
                    PostInline.Text("GitHub"),
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
        assertThat(blocks[0]).isInstanceOf(PostBodyBlock.RichTextBlock::class.java)
        assertThat(blocks[1]).isInstanceOf(PostBodyBlock.ImageBlock::class.java)
        assertThat(blocks[2]).isInstanceOf(PostBodyBlock.ImageGridBlock::class.java)
    }

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
                summary = PostBodyBlock.RichTextBlock(
                    style = RichTextBlockStyle.Paragraph,
                    inlines = listOf(PostInline.Text("给大家看一下")),
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
                    PostBodyBlock.RichTextBlock(
                        style = RichTextBlockStyle.Paragraph,
                        inlines = listOf(PostInline.Text("愚人节快乐")),
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

    @Test
    fun parsePostBodyBlocks_keepsUnsupportedTableAsHtmlBlock() {
        val html = """
            <table>
              <tr><td>单元格</td></tr>
            </table>
        """.trimIndent()

        val block = parsePostBodyBlocks(html).single() as PostBodyBlock.HtmlBlock

        assertThat(block.html).contains("<table>")
        assertThat(block.html).contains("<td>单元格</td>")
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
                    PostBodyBlock.RichTextBlock(
                        style = RichTextBlockStyle.Paragraph,
                        inlines = listOf(PostInline.Text("引用内容")),
                    ),
                    PostBodyBlock.RichTextBlock(
                        style = RichTextBlockStyle.Paragraph,
                        inlines = listOf(PostInline.Text("第二行")),
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
                titleInlines = listOf(
                    PostInline.Link(
                        children = listOf(PostInline.Text("引用的帖子")),
                        rawUrl = "/t/topic/1888983",
                    ),
                ),
                children = listOf(
                    PostBodyBlock.RichTextBlock(
                        style = RichTextBlockStyle.Paragraph,
                        inlines = listOf(PostInline.Text("引用内容")),
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
                            PostBodyBlock.RichTextBlock(
                                style = RichTextBlockStyle.Paragraph,
                                inlines = listOf(PostInline.Text("第一项")),
                            ),
                        ),
                    ),
                    ListItemBlock(
                        children = listOf(
                            PostBodyBlock.RichTextBlock(
                                style = RichTextBlockStyle.Paragraph,
                                inlines = listOf(PostInline.Text("第二项")),
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
            PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(PostInline.Text("展开查看")),
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
                summary = PostBodyBlock.RichTextBlock(
                    style = RichTextBlockStyle.Paragraph,
                    inlines = listOf(PostInline.Text("详情")),
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
                    titleInlines = emptyList(),
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
}
