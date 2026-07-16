package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.baba983.nativedo.feature.topic.ui.TopicReplySheetItem
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.ui.ActionBar.Theme

internal class TopicRepliesSheetScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun replyRowRendersUserEmojiAndThemeColors() {
        paparazziRule.snapshot { context ->
            FrameLayout(context).apply {
                setBackgroundColor(Theme.getColor(Theme.key_dialogBackground))
                addView(
                    replyRow(context),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
    }

    private fun replyRow(context: Context): View {
        val rowClass = Class.forName(
            "io.github.baba983.nativedo.ui.topic.TopicDetailFragment\$TopicRepliesSheet\$ReplySheetRowView",
        )
        val row = rowClass
            .getDeclaredConstructor(Context::class.java, String::class.java)
            .apply { isAccessible = true }
            .newInstance(context, "https://linux.do/") as View
        row.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        rowClass
            .getDeclaredMethod("bind", TopicReplySheetItem::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(
                row,
                TopicReplySheetItem(
                    postId = 43L,
                    postNumber = 43,
                    authorUsername = "alice",
                    authorDisplayName = "Alice 👋",
                    authorAvatarTemplate = "",
                    authorAnimatedAvatarUrl = "",
                    createdAt = "2026-07-14T10:00:00Z",
                    previewText = "Reply preview with an emoji ✨ and a long enough message to wrap.",
                ),
                true,
            )
        return row
    }
}
