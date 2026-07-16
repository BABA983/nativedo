package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.dynamicanimation.animation.FloatValueHolder
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

internal class TopicSwipeReplyRecyclerViewScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun activeSwipeDrawsReplyControlAbovePostCell() {
        paparazziRule.snapshot { context ->
            SwipePreviewList(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(180f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper))
            }
        }
    }

    private class SwipePreviewList(context: Context) : TopicSwipeReplyRecyclerView(context) {
        private var gestureApplied = false

        init {
            layoutManager = SwipeReplyLayoutManager(context)
            itemAnimator = null
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                    object : RecyclerView.ViewHolder(TopicPostCell(parent.context)) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val html = "<p>Swipe this text-only post to reply.</p>"
                    (holder.itemView as TopicPostCell).setPost(
                        Post(
                            id = 42L,
                            postNumber = 2,
                            authorUsername = "alice",
                            authorDisplayName = "Alice",
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

                override fun getItemCount(): Int = 1
            }
            setSwipeReplyDelegate(object : Delegate {
                override fun canStartSwipeReply() = true

                override fun onSwipeReply(cell: TopicPostCell, post: Post) = Unit
            })
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (gestureApplied || childCount == 0) return
            gestureApplied = true
            dispatch(MotionEvent.ACTION_DOWN, 320f, 60f, 1_000L)
            dispatch(MotionEvent.ACTION_MOVE, 240f, 61f, 1_016L)
            dispatch(MotionEvent.ACTION_MOVE, 160f, 61f, 1_032L)
            // Paparazzi records one frame, so freeze the upstream spring at its
            // reached state after exercising the real touch takeover path.
            forceVisualProgress("slidingDrawableVisibilityProgress", 2_000f)
            invalidate()
        }

        private fun dispatch(action: Int, x: Float, y: Float, eventTime: Long) {
            MotionEvent.obtain(1_000L, eventTime, action, x, y, 0).also { event ->
                dispatchTouchEvent(event)
                event.recycle()
            }
        }

        private fun forceVisualProgress(fieldName: String, value: Float) {
            val holder = TopicSwipeReplyRecyclerView::class.java
                .getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .get(this) as FloatValueHolder
            holder.value = value
        }
    }
}
