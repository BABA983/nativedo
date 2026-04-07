package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscourseUiHelpersTest {
    @Test
    fun shouldRenderAvatarFallback_returnsTrueWhenAvatarUrlIsBlank() {
        assertThat(
            shouldRenderAvatarFallback(
                resolvedUrl = "",
                isImageLoadSuccessful = false,
            ),
        ).isTrue()
    }

    @Test
    fun shouldRenderAvatarFallback_returnsFalseAfterImageLoadsSuccessfully() {
        assertThat(
            shouldRenderAvatarFallback(
                resolvedUrl = "https://linux.do/user_avatar/linux.do/baba/120/1.gif",
                isImageLoadSuccessful = true,
            ),
        ).isFalse()
    }

    @Test
    fun resolveAvatarUrl_expandsTemplateAgainstBaseUrl() {
        val resolved = resolveAvatarUrl(
            baseUrl = "https://linux.do/",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            size = 120,
        )

        assertThat(resolved).isEqualTo("https://linux.do/user_avatar/linux.do/baba/120/1.png")
    }

    @Test
    fun resolveAvatarUrl_preservesGifAvatarTemplateAgainstBaseUrl() {
        val resolved = resolveAvatarUrl(
            baseUrl = "https://linux.do/",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.gif",
            size = 120,
        )

        assertThat(resolved).isEqualTo("https://linux.do/user_avatar/linux.do/baba/120/1.gif")
    }

    @Test
    fun resolveAvatarUrl_prefersAnimatedAvatarUrlWhenPresent() {
        val resolved = resolveAvatarUrl(
            baseUrl = "https://linux.do/",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            animatedAvatarUrl = "/user_avatar/linux.do/baba/120/1.gif",
            size = 120,
        )

        assertThat(resolved).isEqualTo("https://linux.do/user_avatar/linux.do/baba/120/1.gif")
    }

    @Test
    fun replaceEmojiImagesWithText_usesTitleForEmojiImage() {
        val html = """
            <p>Hello <img class="emoji" src="/images/emoji/apple/slight_smile.png?v=12" title=":slight_smile:" alt="slight_smile"> world</p>
        """.trimIndent()

        val normalized = replaceEmojiImagesWithText(html)

        assertThat(normalized).contains(":slight_smile:")
        assertThat(normalized).doesNotContain("class=\"emoji\"")
        assertThat(normalized).doesNotContain("<img")
    }

    @Test
    fun replaceEmojiImagesWithText_removesSiteIconImageWithoutLeavingImgTag() {
        val html = """
            <p><a href="https://github.com"><img class="site-icon" src="https://github.com/favicon.ico" width="16" height="16">GitHub</a></p>
        """.trimIndent()

        val normalized = replaceEmojiImagesWithText(html)

        assertThat(normalized).contains("GitHub")
        assertThat(normalized).doesNotContain("site-icon")
        assertThat(normalized).doesNotContain("<img")
    }

    @Test
    fun normalizeInlineHtml_replacesDecorativeIconElementWithAriaLabel() {
        val html = """
            <p><i class="fa fa-github" aria-label="GitHub"></i> repo</p>
        """.trimIndent()

        val normalized = normalizeInlineHtml(html)

        assertThat(normalized).contains("GitHub")
        assertThat(normalized).doesNotContain("<i")
    }

    @Test
    fun prepareInlineContent_extractsEmojiImageAsInlineSpec() {
        val prepared = prepareInlineContent(
            """
                <p>Hello <img class="emoji" src="/images/emoji/apple/slight_smile.png?v=12" title=":slight_smile:" alt="slight_smile"> world</p>
            """.trimIndent(),
        )

        assertThat(prepared.html).contains("inline_image_0")
        assertThat(prepared.inlineImages).hasSize(1)
        assertThat(prepared.inlineImages.single().rawUrl)
            .isEqualTo("/images/emoji/apple/slight_smile.png?v=12")
        assertThat(prepared.inlineImages.single().contentDescription)
            .isEqualTo(":slight_smile:")
    }

    @Test
    fun prepareInlineContent_extractsSiteIconAsInlineSpec() {
        val prepared = prepareInlineContent(
            """
                <p><img class="site-icon" src="https://github.com/favicon.ico" width="16" height="16" alt="">GitHub</p>
            """.trimIndent(),
        )

        assertThat(prepared.html).contains("inline_image_0")
        assertThat(prepared.inlineImages).hasSize(1)
        assertThat(prepared.inlineImages.single().rawUrl)
            .isEqualTo("https://github.com/favicon.ico")
    }
}
