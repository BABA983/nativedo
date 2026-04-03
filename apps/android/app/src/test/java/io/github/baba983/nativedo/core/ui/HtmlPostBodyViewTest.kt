package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlPostBodyViewTest {
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
