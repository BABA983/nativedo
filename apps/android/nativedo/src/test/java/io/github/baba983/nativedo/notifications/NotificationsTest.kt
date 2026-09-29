package io.github.baba983.nativedo.notifications

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.RecyclerListView

/**
 * The reader's notifications, end to end.
 *
 * <p>The failures this pins: the site's notification list is one page of "someone did something",
 * where the actor is in `data` and `user_id` is the reader the notification is addressed to — a
 * screen that reads `user_id` as the actor names the reader in every row. And a notification is
 * about one post: tapping it has to open that topic at that notification's own floor, while the
 * types the site gives no post to (a badge) must not pretend to open anything.</p>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class NotificationsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("notifications.json", "notifications.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        // The reply notification of the fixture is floor 7 of its topic.
        site.serveJson("t/$TOPIC_ID/$FLOOR.json", topicAround(FLOOR))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theReadersNotificationsAreRowsAndOneOpensItsFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        val screen = NotificationsFragment()
        harness.present(screen)
        harness.await("the notifications to arrive") { rowCount() == FIXTURE_ROWS }
        harness.layoutApp()
        harness.renderApp("notifications")

        val rows = notificationCells()
        println("notifications: rows=" + rows.size + " unread=" + rows.map { it.hasUnread() })
        assertEquals("one row per notification the site sent", FIXTURE_ROWS, rows.size)
        // The site's unread/read state reaches the row it belongs to: the reply and the mention are
        // unread, the reaction and the badge are not.
        assertEquals(
            "the two unread notifications first",
            listOf(true, true, false, false),
            rows.map { it.hasUnread() },
        )

        // The reply the fixture's newest notification is about opens its topic at floor 7; the badge
        // the fixture ends with has no post to open and is not a link at all.
        val list = listView()!!
        assertFalse("a topic-less notification is not a link", isEnabled(rows.last()))

        val reply = rows.first()
        list.clickItem(reply, positionOf(reply))
        harness.await("the topic at the notification's floor") {
            site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json")
        }
        harness.await("the floor to be drawn") { drawnPostIds().isNotEmpty() }
        val drawn = drawnPostIds().first()
        println("notifications: opened at post " + drawn)
        assertEquals("the floor the notification is about", POST_ID_BASE + FLOOR, drawn)
        harness.renderApp("notification-floor")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anEmptyInboxSaysSo() {
        site.serveJson("notifications.json", """{"notifications":[],"total_rows_notifications":0}""")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        harness.present(NotificationsFragment())
        harness.await("the empty note") { rowCount() == 1 }
        harness.layoutApp()
        harness.renderApp("notifications-empty")

        assertTrue("no rows to open", notificationCells().isEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /** Whether the list lets a row be tapped, which is what its adapter answers. */
    private fun isEnabled(cell: DialogCell): Boolean {
        val list = listView()!!
        val adapter = list.adapter as RecyclerListView.SelectionAdapter
        return adapter.isEnabled(list.getChildViewHolder(cell))
    }

    /** The rows drawn by upstream's own list row, in the order the screen shows them. */
    private fun notificationCells(): List<DialogCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<DialogCell>()
    }

    /** Whether a row draws an unread badge; the row's own mark for what the reader has not seen. */
    private fun DialogCell.hasUnread(): Boolean = getHasUnread()

    private fun positionOf(view: android.view.View): Int {
        val list = listView() ?: return -1
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return -1
        val index = (0 until list.childCount).firstOrNull { list.getChildAt(it) === view } ?: return -1
        return first + index
    }

    private fun drawnPostIds(): List<Long> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return chatCells(content).map { it.messageObject!!.messageOwner.id.toLong() }
    }

    private fun chatCells(view: android.view.View): List<ChatMessageCell> {
        if (view is ChatMessageCell && view.isShown) return listOf(view)
        if (view is ViewGroup) {
            return (0 until view.childCount).flatMap { chatCells(view.getChildAt(it)) }
        }
        return emptyList()
    }

    /** A topic answered as the site answers a request for one floor: the window, and the stream. */
    private fun topicAround(floor: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = JSONArray()
        val posts = JSONArray()
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        for (number in 1..TOTAL_FLOORS) {
            stream.put(POST_ID_BASE + number)
            if (number < floor || number > floor + 2) continue
            val post = JSONObject(template.toString())
            post.put("id", POST_ID_BASE + number)
            post.put("post_number", number)
            post.put("cooked", "<p>第 $number 楼</p>")
            posts.put(post)
        }
        base.getJSONObject("post_stream").put("stream", stream)
        base.getJSONObject("post_stream").put("posts", posts)
        base.put("posts_count", TOTAL_FLOORS)
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findRecycler(content)
    }

    private fun findRecycler(view: android.view.View): RecyclerListView? {
        if (view is RecyclerListView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val TOPIC_ID = 4002L
        const val FLOOR = 7
        const val FIXTURE_ROWS = 4
        const val POST_ID_BASE = 1000L
        const val TOTAL_FLOORS = 12
    }
}
