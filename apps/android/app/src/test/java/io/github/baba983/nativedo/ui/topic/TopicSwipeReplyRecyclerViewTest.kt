package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicSwipeReplyRecyclerViewTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.density = 1f
    }

    @Test
    fun strongLeftSwipe_locksVerticalScrollUntilReleaseAndRepliesOnce() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val list = TopicSwipeReplyRecyclerView(activity, ColorDrawable(0xffffffff.toInt()).apply {
            setBounds(0, 0, 24, 24)
        })
        val post = Post(id = 42L, postNumber = 2, authorUsername = "alice", cookedHtml = "")
        var replies = 0
        val layoutManager = TopicSwipeReplyRecyclerView.SwipeReplyLayoutManager(activity)
        list.layoutManager = layoutManager
        list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(TopicPostCell(parent.context)) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TopicPostCell).setPost(
                    post,
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
                    emptyList(),
                    PostBodyBlockState(),
                    0,
                    null,
                )
            }

            override fun getItemCount(): Int = 1
        }
        list.setSwipeReplyDelegate(object : TopicSwipeReplyRecyclerView.Delegate {
            override fun canStartSwipeReply(): Boolean = true
            override fun onSwipeReply(cell: TopicPostCell, post: Post) {
                replies++
            }
        })
        activity.setContentView(list)
        list.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, 400, 300)
        shadowOf(activity.mainLooper).idle()

        val downTime = 1_000L
        dispatch(list, downTime, downTime, MotionEvent.ACTION_DOWN, 320f, 60f)
        dispatch(list, downTime, downTime + 16, MotionEvent.ACTION_MOVE, 240f, 61f)
        assertThat(layoutManager.isSwipeReplyVerticalScrollEnabled()).isFalse()
        dispatch(list, downTime, downTime + 32, MotionEvent.ACTION_MOVE, 160f, 61f)
        dispatch(list, downTime, downTime + 48, MotionEvent.ACTION_UP, 160f, 61f)

        assertThat(replies).isEqualTo(1)
        assertThat(layoutManager.isSwipeReplyVerticalScrollEnabled()).isTrue()
        assertThat((list.getChildAt(0) as TopicPostCell).slidingOffsetX).isLessThan(0f)
        list.cancelSwipeReply(false)
        assertThat((list.getChildAt(0) as TopicPostCell).slidingOffsetX).isEqualTo(0f)
        assertThat(layoutManager.isSwipeReplyVerticalScrollEnabled()).isTrue()
    }

    private fun dispatch(
        view: android.view.View,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
