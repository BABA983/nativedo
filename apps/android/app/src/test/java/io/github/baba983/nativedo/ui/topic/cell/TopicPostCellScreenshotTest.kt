package io.github.baba983.nativedo.ui.topic.cell

import android.content.Context
import android.view.ViewGroup
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import org.junit.Rule
import org.junit.Test

internal class TopicPostCellScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun paragraph() {
        paparazziRule.snapshot { context ->
            topicPostCell(context)
        }
    }

    @Test
    fun persistentSearchHighlight() {
        paparazziRule.snapshot { context ->
            topicPostCell(context).apply {
                setHighlightedText("linux")
            }
        }
    }

    @Test
    fun cjkAdjacentSearchHighlight() {
        paparazziRule.snapshot { context ->
            topicPostCell(context, CJK_POST_HTML).apply {
                setHighlightedText("linux")
            }
        }
    }

    private fun topicPostCell(
        context: Context,
        html: String = POST_HTML,
    ): TopicPostCell {
        val post = Post(
            id = 1001L,
            postNumber = 43,
            authorUsername = "alice",
            authorDisplayName = "Alice Chen",
            cookedHtml = html,
        )
        return TopicPostCell(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPost(
                post,
                null,
                null,
                true,
                true,
                2566145L,
                "https://linux.do/",
                emptyMap(),
                emptyMap(),
                emptyList(),
                0,
                emptyList(),
                parseCookedPostBody(html, "https://linux.do/"),
                PostBodyBlockState(),
                0,
                null,
            )
        }
    }

    private companion object {
        const val POST_HTML =
            "<p>NativeDo keeps linux.do topics readable and highlights search results.</p>"
        const val CJK_POST_HTML =
            "<p>在Linux环境里，NativeDo keeps linux.do topics readable.</p>"
    }
}
