package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyInternalLinksTest {
    @Test
    fun parseInternalLinkDestination_parsesTopicLinkWithSlugAndPostNumber() {
        val destination = parseInternalLinkDestination(
            baseUrl = "https://linux.do/",
            rawUrl = "/t/topic/1875662/1",
        )

        assertThat(destination).isEqualTo(
            PostBodyInternalDestination.Topic(
                topicId = 1875662,
                postNumber = 1,
            ),
        )
    }

    @Test
    fun parseInternalLinkDestination_parsesTopicLinkWithoutSlug() {
        val destination = parseInternalLinkDestination(
            baseUrl = "https://linux.do/",
            rawUrl = "https://linux.do/t/1875662",
        )

        assertThat(destination).isEqualTo(
            PostBodyInternalDestination.Topic(
                topicId = 1875662,
                postNumber = null,
            ),
        )
    }

    @Test
    fun parseInternalLinkDestination_parsesUserLink() {
        val destination = parseInternalLinkDestination(
            baseUrl = "https://linux.do/",
            rawUrl = "/u/baba/summary",
        )

        assertThat(destination).isEqualTo(
            PostBodyInternalDestination.User(username = "baba"),
        )
    }

    @Test
    fun parseInternalLinkDestination_ignoresExternalHost() {
        val destination = parseInternalLinkDestination(
            baseUrl = "https://linux.do/",
            rawUrl = "https://example.com/t/topic/1875662/1",
        )

        assertThat(destination).isNull()
    }
}
