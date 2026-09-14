package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.test.NativeDoTestApplication
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.DialogsItemAnimator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = NativeDoTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class TopicListLoadTransitionTest {
    private lateinit var activity: Activity
    private lateinit var root: FrameLayout
    private lateinit var list: TopicDialogsRecyclerView
    private lateinit var loading: View
    private lateinit var adapter: TopicListAdapter
    private lateinit var transition: TopicListLoadTransition
    private var readyCount = 0

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        Theme.getActiveTheme()
        // Robolectric reuses Theme statics across tests in one sandbox. Cell-level
        // tests stub dialogs_namePaint directly, which makes createDialogsResources
        // skip the rest of the dialog paint set; reset the guard so this fixture
        // always starts from a fully initialized Theme.
        Theme.dialogs_namePaint = null
        Theme.createDialogsResources(activity)
        root = FrameLayout(activity)
        list = TopicDialogsRecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        loading = View(activity).apply { visibility = View.GONE }
        adapter = TopicListAdapter(activity, "https://linux.do")
        list.adapter = adapter
        transition = TopicListLoadTransition(list, loading) { readyCount++ }
        root.addView(list, FrameLayout.LayoutParams(-1, -1))
        root.addView(loading, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        drawFrame()
    }

    @After
    fun tearDown() {
        list.itemAnimator?.endAnimations()
        activity.finish()
    }

    @Test
    fun firstRowsStayOpaqueWhenCategoriesArriveBeforeTheirFirstLayout() {
        transition.submit(false, true) { adapter.submit(emptyList(), emptyMap(), false, false) }
        drawFrame()
        submitTopics(topics())
        transition.submit(true, false) {
            adapter.submit(topics(), mapOf(1 to CategorySummary(id = 1, name = "分享", colorHex = "0088CC")), false, false)
        }

        assertThat(loading.visibility).isEqualTo(View.VISIBLE)
        assertThat(list.itemAnimator).isNull()
        drawFrame()

        assertThat(loading.visibility).isEqualTo(View.GONE)
        assertThat(readyCount).isEqualTo(1)
        assertThat(list.childCount).isAtLeast(3)
        repeat(12) {
            assertVisibleRowsAreOpaque()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            drawFrame()
        }
    }

    @Test
    fun laterUpdatesRetainTheDialogsItemAnimator() {
        submitTopics(topics())
        drawFrame()
        val animator = list.itemAnimator
        assertThat(animator).isInstanceOf(DialogsItemAnimator::class.java)

        submitTopics(topics() + TopicSummary(id = 6L, title = "New topic", authorUsername = "alice"))

        assertThat(list.itemAnimator).isSameInstanceAs(animator)
        assertThat(transition.isContentReady).isTrue()
        assertThat(readyCount).isEqualTo(1)
    }

    @Test
    fun recycledPageReplacesItsRowsWithoutAnInsertionAnimation() {
        submitTopics(topics())
        drawFrame()

        transition.reset()
        submitTopics(topics(100L))
        assertThat(list.itemAnimator).isNull()
        drawFrame()

        assertThat(adapter.getTopicAt(0)?.id).isEqualTo(100L)
        assertThat(readyCount).isEqualTo(2)
        assertVisibleRowsAreOpaque()
    }

    @Test
    fun pendingFirstLayoutSurvivesPageDetachAndReattach() {
        transition.submit(false, true) { adapter.submit(emptyList(), emptyMap(), false, false) }
        submitTopics(topics())
        root.removeView(list)
        root.addView(list, 0, FrameLayout.LayoutParams(-1, -1))

        drawFrame()

        assertThat(loading.visibility).isEqualTo(View.GONE)
        assertThat(transition.isContentReady).isTrue()
        assertThat(readyCount).isEqualTo(1)
        assertVisibleRowsAreOpaque()
    }

    @Test
    fun emptyOrFailedLoadDoesNotWaitForRowsThatWillNeverExist() {
        transition.submit(false, true) { adapter.submit(emptyList(), emptyMap(), false, false) }
        drawFrame()
        transition.submit(false, false) { adapter.submit(emptyList(), emptyMap(), false, false) }
        drawFrame()

        assertThat(loading.visibility).isEqualTo(View.GONE)
        assertThat(transition.isContentReady).isFalse()
        assertThat(readyCount).isEqualTo(0)
    }

    private fun submitTopics(topics: List<TopicSummary>) {
        transition.submit(true, false) { adapter.submit(topics, emptyMap(), false, false) }
    }

    private fun topics(firstId: Long = 1L): List<TopicSummary> = List(5) { index ->
        TopicSummary(id = firstId + index, title = "Topic $index", authorUsername = "alice", categoryId = 1)
    }

    private fun drawFrame() {
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(640f)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        root.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun assertVisibleRowsAreOpaque() {
        for (index in 0 until list.childCount) {
            assertThat(list.getChildAt(index).alpha).isEqualTo(1f)
        }
    }
}
