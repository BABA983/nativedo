package io.github.baba983.nativedo.ui.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicHeaderCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCellDelegate
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

/** Topic-detail content hosted by the Telegram-shaped theme preview shell. */
internal class TopicThemePreviewContent(context: Context) : FrameLayout(context), Theme.Colorable {
    private val wallpaperView = WallpaperView(context)
    private val headerCell = TopicHeaderCell(context)
    private val postCells = mutableListOf<TopicPostCell>()

    init {
        addView(wallpaperView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val scrollView = NestedScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(20f))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)
        }
        scrollView.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        headerCell.bind("Porting Telegram themes to a native topic detail", 18, 1240, listOf("development", "android"))
        content.addView(headerCell, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val posts = previewPosts()
        posts.forEachIndexed { index, post ->
            val cell = TopicPostCell(context).apply {
                delegate = object : TopicPostCellDelegate {}
                setPost(
                    post,
                    posts.getOrNull(index - 1),
                    posts.getOrNull(index + 1),
                    index == 0,
                    index == posts.lastIndex,
                    42L,
                    "https://linux.do/",
                    emptyMap(),
                    emptyMap(),
                    listOf("heart", "+1", "clap"),
                    post.replyCount,
                    emptyList(),
                    null,
                    PostBodyBlockState(),
                    0,
                    null,
                )
            }
            postCells += cell
            content.addView(cell, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = AndroidUtilities.dp(8f)
            })
        }
        updateColors()
    }

    override fun updateColors() {
        wallpaperView.updateWallpaper()
        headerCell.updateColors()
        postCells.forEach(TopicPostCell::updateColors)
        invalidate()
    }

    fun updateTextSizes() {
        headerCell.updateTextSizes()
        postCells.forEach(TopicPostCell::updateTextSizes)
        requestLayout()
    }

    private class WallpaperView(context: Context) : View(context) {
        private var wallpaper: Drawable? = null

        fun updateWallpaper() {
            wallpaper = Theme.getCachedWallpaper()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val current = wallpaper
            if (current == null) {
                canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper))
                return
            }
            current.setBounds(0, 0, width, height)
            current.draw(canvas)
        }
    }

    private companion object {
        fun previewPosts(): List<Post> = listOf(
            Post(
                id = 4201L,
                postNumber = 1,
                authorUsername = "native_do",
                authorDisplayName = "NativeDo",
                authorTitle = "theme porter",
                createdAt = "2026-07-20T10:24:00Z",
                replyCount = 3,
                reactions = listOf(PostReaction("heart", 12, reacted = true), PostReaction("+1", 8)),
                currentUserReaction = "heart",
                reactionUsersCount = 20,
                cookedHtml = """
                    <p>This preview uses the same renderer as a real topic detail.</p>
                    <blockquote><p>Theme colors, typography, quotes, links and embedded blocks update together.</p></blockquote>
                    <ul><li>Telegram preview interaction</li><li>NativeDo topic content</li></ul>
                    <pre><code>Theme.applyThemeTemporary(theme, true);</code></pre>
                """.trimIndent(),
            ),
            Post(
                id = 4202L,
                postNumber = 2,
                replyToPostNumber = 1,
                authorUsername = "linux_do_user",
                authorDisplayName = "linux.do member",
                createdAt = "2026-07-20T10:31:00Z",
                reactions = listOf(PostReaction("clap", 5)),
                reactionUsersCount = 5,
                cookedHtml = """
                    <p>The wallpaper, accent, blur, motion and pattern controls stay on the second preview page.</p>
                    <details open><summary>What is preserved?</summary><p>Apply and cancel remain transactional, matching Telegram theme preview semantics.</p></details>
                """.trimIndent(),
            ),
        )
    }
}
