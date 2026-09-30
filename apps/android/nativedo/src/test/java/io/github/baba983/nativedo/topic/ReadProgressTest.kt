package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell

/**
 * The reader's place in a topic.
 *
 * <p>Two failures are pinned here. A reader who had read half of a topic had it opened at its first
 * floor again, and a topic they finished kept its unread badge in the list they went back to -
 * because nothing told the site where they got to. Discourse's own report is
 * {@code POST /topics/timings} (the dwell of the floors that were read), the site's answer to a plain
 * read carries {@code last_read_post_number}, and this device remembers the place in between.</p>
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
internal class ReadProgressTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun readingATopicReportsTheFloorsReachedAndTheListFollowsTheSite() {
        // The site answers its feed the way it does: a topic's unread count is what the reader's own
        // reports have left unread, and floors this client reported are not unread any more.
        site.serveJsonFor("latest.json") {
            latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS - reportedFloors())
        }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        // A timing report is a write: the site takes it and answers nothing of its own.
        site.serveJson("topics/timings", "{}")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        val list = topicList() ?: throw AssertionError("the shell shows no topic list")
        assertEquals(
            "the feed's own count of what is unread",
            TOPIC_FLOORS,
            list.rows().first().unreadCount,
        )

        openFirstTopic()
        harness.await("the topic to load") { drawnFloors().isNotEmpty() }
        println("read: drawn floors on opening = " + drawnFloors())

        // The reader reads on: the viewport is moved to the end of the topic.
        scrollTopicToTheEnd()
        println("read: drawn floors after reading = " + drawnFloors())

        // The report goes out while the reader is still in the topic, not when they leave - and as
        // the reader moves, so one topic may be reported more than once.
        awaitWithClock("the site to be told the whole topic") { reportedFloors() == TOPIC_FLOORS }
        val bodies = site.requestBodiesFor("topics/timings")
        println("read: timings = $bodies")
        val reports = bodies.map(::fieldsOf)
        assertTrue(
            "the report is about the topic the reader is in",
            reports.all { fields -> fields["topic_id"] == TOPIC_ID.toString() },
        )
        assertEquals(
            "every floor the reader reached is reported, once",
            (1..TOPIC_FLOORS).toList(),
            reports.flatMap { fields -> fields.keys }
                .filter { key -> key.startsWith("timings[") }
                .map { key -> key.removePrefix("timings[").removeSuffix("]").toInt() }
                .sorted(),
        )
        assertTrue(
            "each of them with the dwell one read floor is reported as",
            reports.flatMap { fields -> fields.filterKeys { key -> key.startsWith("timings[") }.values }
                .all { dwell -> dwell == "1000" },
        )
        assertEquals(
            "and the dwell of the whole topic is what its reports add up to",
            6000,
            reports.sumOf { fields -> fields["topic_time"]!!.toInt() },
        )
        assertEquals(
            "with the token the site asks its writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("topics/timings"),
        )
        harness.layoutApp()
        harness.renderApp("read-progress-topic")

        // The reader goes back to the list. The topic they have read in full draws no badge there,
        // and the site's own answer - which the list is read again for - agrees.
        leaveTheTopic()
        harness.await("the list to be read again") { site.queriesFor("latest.json").size >= 2 }
        assertEquals(
            "the topic the reader finished is not unread any more",
            0,
            (topicList() ?: throw AssertionError("the list went away")).rows().first().unreadCount,
        )
        // The list is drawn once it is the screen in front again, not while it is still arriving.
        harness.idleClock(600)
        harness.layoutApp()
        harness.renderApp("read-progress-list")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aTopicTheReaderWasInOpensAtTheFloorTheSiteSays() {
        // A topic longer than the window the site answers a plain read with: the floor the reader
        // was at is not in that answer, so it needs its own read.
        site.serveJsonFor("latest.json") { latestFeedWithUnread(LONG_TOPIC, LONG_TOPIC) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = LONG_TOPIC, lastRead = READ_TO) }
        site.serveJsonFor("t/$TOPIC_ID/$READ_TO.json") { topicJson(floor = READ_TO, total = LONG_TOPIC, lastRead = READ_TO) }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }

        openFirstTopic()
        harness.await("the site to be asked for the floor the reader was at") {
            site.requestPaths.contains("t/$TOPIC_ID/$READ_TO.json")
        }
        harness.await("that floor to be drawn") { drawnFloors().contains(READ_TO) }
        println("read: opening at the site's own answer drew floors " + drawnFloors())
        assertEquals(
            "the window the reader was at, not the head of the thread",
            READ_TO,
            drawnFloors().first(),
        )
        harness.layoutApp()
        harness.renderApp("read-progress-jump")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Waits for work that is both delayed and off the device: the report is debounced on the main
     * looper's clock, which only advances while it is driven, and it is a request, which finishes on
     * a thread of its own.
     */
    private fun awaitWithClock(what: String, condition: () -> Boolean) {
        repeat(60) {
            harness.idleClock(100)
            harness.idle()
            if (condition()) {
                return
            }
            java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L)
        }
        throw AssertionError("timed out waiting for $what")
    }

    /** A tap on the list's first row, the way a reader opens a topic. */
    private fun openFirstTopic() {
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list to open a topic from")
        val row = list.getChildAt(0) ?: throw AssertionError("the list drew no rows")
        list.clickItem(row, positionOf(row))
        harness.idle()
    }

    private fun scrollTopicToTheEnd() {
        val list = contentListOf(harness.activity) ?: throw AssertionError("the topic drew no list")
        harness.layoutApp()
        var guard = 0
        do {
            list.scrollBy(0, list.height / 2)
            harness.layoutApp()
            harness.idle()
            val manager = list.layoutManager as? LinearLayoutManager ?: break
            if (manager.findLastVisibleItemPosition() >= list.adapter!!.itemCount - 1) {
                break
            }
        } while (guard++ < 40)
        harness.idleClock(400)
    }

    private fun leaveTheTopic() {
        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).onBackPressed()
        harness.idle()
    }

    /** The floors the topic screen has drawn, as the post numbers behind its bubbles. */
    private fun drawnFloors(): List<Int> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return chatCells(content)
            .mapNotNull { cell -> cell.messageObject?.messageOwner?.rich_message?.let { cell } }
            .mapNotNull { cell -> (cell.messageObject!!.messageOwner.id - POST_ID_BASE).toInt() }
            .filter { it in 1..1_000 }
    }

    private fun chatCells(view: android.view.View): List<ChatMessageCell> {
        if (view is ChatMessageCell && view.isShown) return listOf(view)
        if (view is ViewGroup) {
            return (0 until view.childCount).flatMap { chatCells(view.getChildAt(it)) }
        }
        return emptyList()
    }

    private fun positionOf(view: android.view.View): Int {
        val list = contentListOf(harness.activity) ?: return -1
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return -1
        val index = (0 until list.childCount).firstOrNull { list.getChildAt(it) === view } ?: return -1
        return first + index
    }

    /** The shell's own topic list, whichever fragment in the stack it is. */
    private fun topicList(): TopicListFragment? = fragments().filterIsInstance<TopicListFragment>().firstOrNull()

    private fun fragments(): List<Any> {
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        return (stackField.get(layout) as List<*>).filterNotNull()
    }

    /**
     * A form body's own fields. Retrofit encodes the field *names* too - `timings[1]` travels as
     * `timings%5B1%5D`, which is what the site decodes back - so the names are decoded before they
     * are read, the way the site reads them.
     */
    private fun fieldsOf(body: String): Map<String, String> =
        java.net.URLDecoder.decode(body, "UTF-8")
            .split('&')
            .associate { field -> field.substringBefore('=') to field.substringAfter('=') }

    /** The last floor this client has reported to the fake site, which is what it calls unread. */
    private fun reportedFloors(): Int =
        site.requestBodiesFor("topics/timings")
            .flatMap { body ->
                fieldsOf(body).keys
                    .filter { key -> key.startsWith("timings[") }
                    .map { key -> key.removePrefix("timings[").removeSuffix("]").toInt() }
            }
            .maxOrNull() ?: 0

    private fun latestFeedWithUnread(total: Int, unread: Int): String {
        val base = JSONObject(harness.fixture("latest-topics.json"))
        val topic = base.getJSONObject("topic_list").getJSONArray("topics").getJSONObject(0)
            .put("posts_count", total)
            .put("highest_post_number", total)
            .put("unread_posts", unread.coerceIn(0, total))
        base.put(
            "topic_list",
            JSONObject()
                .put("more_topics_url", "/latest.json?page=1")
                .put("topics", JSONArray().put(topic)),
        )
        return base.toString()
    }

    /** One topic answered the way the site answers a window of it, at [total] floors long. */
    private fun topicJson(floor: Int, total: Int, lastRead: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        val stream = JSONArray()
        val posts = JSONArray()
        for (number in 1..total) {
            stream.put(POST_ID_BASE + number)
            if (number < floor || number > floor + WINDOW - 1) {
                continue
            }
            posts.put(
                JSONObject(template.toString())
                    .put("id", POST_ID_BASE + number)
                    .put("post_number", number)
                    .put("cooked", "<p>第 $number 楼。</p>" + TALL_FLOOR.repeat(PARAGRAPHS_PER_FLOOR)),
            )
        }
        base.put("highest_post_number", total)
        base.put("posts_count", total)
        base.put("last_read_post_number", lastRead)
        base.put("post_stream", JSONObject().put("stream", stream).put("posts", posts))
        return base.toString()
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TOPIC_ID = 101L
        const val POST_ID_BASE = 100_000L
        const val TOPIC_FLOORS = 6

        /** The floors the site answers a window of a topic with, as its own per_page does. */
        const val WINDOW = 20
        const val LONG_TOPIC = 60
        const val READ_TO = 40

        /**
         * A floor with a body of its own: a topic whose floors all fit on one screen needs no
         * scrolling, and this test is about a reader moving through a topic.
         */
        const val PARAGRAPHS_PER_FLOOR = 6
        const val TALL_FLOOR = "<p>这一段只是把楼层撑高，好让阅读需要滚动。</p>"
    }
}
