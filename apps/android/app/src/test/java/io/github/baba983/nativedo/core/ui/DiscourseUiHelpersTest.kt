package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscourseUiHelpersTest {
    @Test
    fun resolveAvatarUrl_expandsTemplateAgainstBaseUrl() {
        val url = resolveAvatarUrl(
            baseUrl = "https://linux.do/",
            avatarTemplate = "/user_avatar/linux.do/alice/{size}/1.png",
            size = 96,
        )

        assertThat(url).isEqualTo("https://linux.do/user_avatar/linux.do/alice/96/1.png")
    }

    @Test
    fun resolveAvatarUrl_prefersProtocolRelativeAnimatedAvatar() {
        val url = resolveAvatarUrl(
            baseUrl = "https://linux.do/",
            avatarTemplate = "/user_avatar/linux.do/alice/{size}/1.png",
            animatedAvatarUrl = "//linuxdo-uploads.s3.linux.do/original/avatar.gif",
            size = 96,
        )

        assertThat(url).isEqualTo("https://linuxdo-uploads.s3.linux.do/original/avatar.gif")
        assertThat(isAnimatedDiscourseImageUrl(url)).isTrue()
        assertThat(avatarImageFilter("//linuxdo-uploads.s3.linux.do/original/avatar.gif", 50))
            .isEqualTo("50_50")
    }

    @Test
    fun discourseImageUrlHelpers_classifyImageFlairUrls() {
        assertThat(looksLikeDiscourseImageUrl("//cdn.example.com/flair.gif")).isTrue()
        assertThat(looksLikeDiscourseImageUrl("https://cdn.example.com/flair.svg")).isTrue()
        assertThat(looksLikeDiscourseImageUrl("battery-quarter")).isFalse()
        assertThat(isAnimatedDiscourseImageUrl("https://cdn.example.com/flair.GIF?version=1#badge")).isTrue()
        assertThat(isAnimatedDiscourseImageUrl("https://cdn.example.com/flair.SVG?version=1#badge")).isTrue()
        assertThat(isAnimatedDiscourseImageUrl("https://cdn.example.com/flair.svgz")).isTrue()
        assertThat(isAnimatedDiscourseImageUrl("https://cdn.example.com/flair.webp")).isFalse()
    }
}
