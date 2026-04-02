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
        assertThat((blocks[0] as PostBodyBlock.HtmlBlock).html).contains("Hello")
        assertThat((blocks[1] as PostBodyBlock.ImageBlock).image).isEqualTo(
            PostBodyImage(
                rawUrl = "/uploads/default/optimized/1X/thumb_2_690x388.png",
                originalUrl = "/uploads/default/original/1X/original.png",
                width = 690,
                height = 388,
            ),
        )
        assertThat((blocks[2] as PostBodyBlock.HtmlBlock).html).contains("World")
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
    fun parsePostBodyBlocks_keepsEmojiImageInsideHtmlBlock() {
        val html = """
            <p><img class="emoji" src="/images/emoji/twemoji/face_savoring_food.png?v=15" title=":face_savoring_food:" alt=":face_savoring_food:" width="20" height="20" /></p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isInstanceOf(PostBodyBlock.HtmlBlock::class.java)
    }

    @Test
    fun parsePostBodyBlocks_keepsSiteIconInsideHtmlBlock() {
        val html = """
            <p><img class="site-icon" src="https://github.com/favicon.ico" width="16" height="16" />GitHub</p>
        """.trimIndent()

        val blocks = parsePostBodyBlocks(html)

        assertThat(blocks).hasSize(1)
        assertThat(blocks.single()).isInstanceOf(PostBodyBlock.HtmlBlock::class.java)
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
