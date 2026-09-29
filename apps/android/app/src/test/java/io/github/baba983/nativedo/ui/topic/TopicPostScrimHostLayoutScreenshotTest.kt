package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

internal class TopicPostScrimHostLayoutScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun longPressKeepsOnlyTheSelectedPostAboveTelegramStyleFullScreenDim() {
        paparazziRule.snapshot { context ->
            LongPressPreview(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(640f),
                )
            }
        }
    }

    private class LongPressPreview(context: Context) : FrameLayout(context) {
        private val host = TopicPostScrimHostLayout(context)
        private val list = TopicSwipeReplyRecyclerView(context)
        private var scrimShown = false

        init {
            setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper))
            host.setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper))
            addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            list.apply {
                layoutManager = TopicSwipeReplyRecyclerView.SwipeReplyLayoutManager(context)
                itemAnimator = null
                clipToPadding = false
                setPadding(0, AndroidUtilities.dp(56f), 0, AndroidUtilities.dp(64f))
                adapter = previewAdapter()
            }
            host.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            val topBar = TextView(context).apply {
                text = "Topic title"
                textSize = 18f
                setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(AndroidUtilities.dp(20f), 0, 0, 0)
                setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault))
            }
            host.addView(
                topBar,
                LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f), Gravity.TOP),
            )

            val composer = TextView(context).apply {
                text = "Write a reply"
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(AndroidUtilities.dp(20f), 0, 0, 0)
                setTextColor(Theme.getColor(Theme.key_chat_messagePanelHint))
                setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground))
            }
            host.addView(
                composer,
                LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(64f), Gravity.BOTTOM),
            )
            host.setScrimViewport(list, topBar, composer)

            addView(
                popupMenu(context),
                LayoutParams(AndroidUtilities.dp(196f), AndroidUtilities.dp(184f), Gravity.TOP or Gravity.START).apply {
                    leftMargin = AndroidUtilities.dp(62f)
                    topMargin = AndroidUtilities.dp(220f)
                },
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (!scrimShown && list.childCount > 1) {
                scrimShown = true
                host.showScrim(list.getChildAt(1), this, false)
            }
        }

        private fun previewAdapter() = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(TopicPostCell(parent.context)) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val html = if (position == 1) {
                    "<p>This selected post stays clear above the dim.</p><p>Its cell background is bounded to the post: no wallpaper through it, nothing spilled.</p>"
                } else {
                    "<p>This neighboring post remains behind the full-screen dim.</p>"
                }
                (holder.itemView as TopicPostCell).setPost(
                    Post(
                        id = position.toLong() + 1,
                        postNumber = position + 1,
                        authorUsername = if (position == 1) "selected" else "neighbor",
                        authorDisplayName = if (position == 1) "Selected post" else "Neighboring post",
                        cookedHtml = html,
                    ),
                    null,
                    null,
                    true,
                    true,
                    7L,
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

            override fun getItemCount() = 3
        }

        private fun popupMenu(context: Context): View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(10f), AndroidUtilities.dp(18f), AndroidUtilities.dp(10f))
            background = GradientDrawable().apply {
                setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
                cornerRadius = AndroidUtilities.dp(18f).toFloat()
            }
            listOf("Reply", "Select text", "Copy link", "Add bookmark").forEach { label ->
                addView(TextView(context).apply {
                    text = label
                    textSize = 17f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem))
                }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
    }
}
