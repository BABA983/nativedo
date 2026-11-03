package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.menuItemLabels
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
import okhttp3.mockwebserver.MockResponse
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
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.Components.RecyclerListView

/**
 * The topic's 只看楼主 filter, through the overflow row the reader actually taps.
 *
 * <p>The filter is the site's own `username_filters`, not rows hidden on this side: the answer's
 * stream is post 1 plus the poster's posts, and that stream is what paging and the page-down count
 * are made of. A window in the middle of a topic may carry none of the poster's floors, so a client
 * that filtered what it happened to hold would show an empty list rather than the poster's next
 * window.</p>
 *
 * <p>Failures this pins: a filter that is only a label (the rows stay the whole topic); a filter
 * that reports its floors as read (the site's cursor would jump past posts by others that were
 * never shown); a page that walks the whole topic's stream; a failed switch that leaves the list
 * claiming a filter it is not showing; and a toggle that cannot be undone.</p>
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
internal class TopicOnlyOpTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        runCatching { org.telegram.ui.Components.Bulletin.hideVisible() }
        runCatching { harness.idleClock(300) }
        runCatching { harness.fragmentStack().forEach { fragment -> fragment.onFragmentDestroy() } }
        runCatching { harness.idle() }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The reader asks for the poster's floors: the site is asked with its own `username_filters`,
     * the rows are that answer, the capsule says the filter is on, and no read position is reported
     * while it is.
     */
    @Test
    fun theOverflowFiltersToTheOriginalPosterThroughTheSite() {
        serveTopic()

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).map { it.postNumber } == ALL }
        harness.layoutApp()
        harness.idleClock(700)
        assertEquals(
            "the unfiltered window carries every floor",
            ALL,
            floorsOf(fragment).map { it.postNumber },
        )

        openOverflow()
        clickRow("只看楼主")

        harness.await("the poster's window") {
            floorsOf(fragment).map { it.postNumber } == OP_FLOORS
        }
        harness.layoutApp()
        harness.idleClock(400)

        // The site's own filter was asked for, on the window around the floor the reader stood at.
        val filteredQueries = site.queriesFor("t/$TOPIC_ID/1.json")
        println("only-op: filtered queries = $filteredQueries")
        assertTrue(
            "the read carries the site's username_filters: $filteredQueries",
            filteredQueries.any { it.contains("username_filters=op") },
        )
        assertEquals("only the poster's floors are drawn", OP_FLOORS, floorsOf(fragment).map { it.postNumber })
        assertTrue(
            "every drawn post is the poster's",
            floorsOf(fragment).all { it.authorUsername == OP },
        )
        assertEquals(
            "the stream is the filtered one, so paging cannot walk the whole topic",
            OP_IDS,
            field(fragment, "streamPostIds"),
        )
        assertTrue(
            "the capsule says the list is filtered: ${capsuleSubtitle(fragment)}",
            capsuleSubtitle(fragment).contains("只看楼主"),
        )
        harness.layoutApp()
        harness.renderApp("topic-only-op")

        // Reading a filtered list credits no floor: reporting floor 10 would advance the topic's
        // cursor past floors 2..9, which the reader never saw. The dwell may still be reported -
        // it is the reader's own time - but no timings[...] field may appear.
        val reportsBefore = site.requestBodiesFor("topics/timings").size
        val reachedBefore = reachedFloorMark(fragment)
        repeat(3) {
            listView()!!.scrollBy(0, 300)
            harness.idleClock(500)
        }
        val filteredReports = site.requestBodiesFor("topics/timings").drop(reportsBefore)
        println("only-op: filtered reports = $filteredReports")
        assertTrue(
            "a filtered read reports no floor: $filteredReports",
            filteredReports.all { body -> !body.contains("timings%5B") && !body.contains("timings[") },
        )
        assertEquals(
            "and the reader's read mark does not move",
            reachedBefore,
            reachedFloorMark(fragment),
        )

        // The same row now offers the way back, and the way back re-reads the whole topic,
        // unfiltered, at the same place.
        openOverflow()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        println("only-op: overflow rows = $labels")
        assertTrue("the row offers the way back: $labels", labels.any { it.contains("显示全部") })
        clickRow("显示全部")
        harness.await("the whole topic again") { floorsOf(fragment).size == ALL.size }
        harness.layoutApp()
        harness.idleClock(300)
        assertEquals("the full window is back", ALL, floorsOf(fragment).map { it.postNumber })
        assertTrue(
            "the way back asks the site without a username filter: " +
                "${site.queriesFor("t/$TOPIC_ID/1.json")}",
            site.queriesFor("t/$TOPIC_ID/1.json").any { query -> !query.contains("username_filters") },
        )
        assertFalse(
            "the capsule stops claiming a filter: ${capsuleSubtitle(fragment)}",
            capsuleSubtitle(fragment).contains("只看楼主"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A filtered window that does not arrive leaves the rows that were already on screen; the
     * screen must not keep claiming a filter those rows are not. The row and the capsule go back
     * to the mode the rows really are, and the failure is said in passing.
     */
    @Test
    fun aFilteredWindowThatDoesNotArrivePutsTheFullListAndItsModeBack() {
        serveTopic()
        site.serveResponseByQuery("t/$TOPIC_ID/5.json") { _ ->
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["暂时不可用"]}""")
        }

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).map { it.postNumber } == ALL }
        harness.layoutApp()
        harness.idleClock(600)
        // The switch's window is the one around the floor at the top of the viewport.
        scrollFloorToTop(fragment, 5)
        harness.layoutApp()
        harness.idleClock(400)
        assertEquals("the viewport starts at floor 5", 5, firstVisibleFloor(fragment))

        openOverflow()
        clickRow("只看楼主")
        harness.await("the filtered read to be tried") {
            site.recordedRequests.any { it.contains("username_filters=op") }
        }
        // The mode is entered optimistically, then put back when the window fails: wait for that
        // rather than for a fixed slice, because the failure is a real transfer on OkHttp's pool.
        harness.await("the failed switch to be put back") { !onlyOpOf(fragment) }
        harness.idleClock(400)

        assertFalse("the mode is not entered on a window that never arrived", onlyOpOf(fragment))
        assertEquals(
            "the rows the reader already had are still the whole topic",
            ALL,
            floorsOf(fragment).map { it.postNumber },
        )
        assertFalse(
            "and the capsule does not claim a filter: ${capsuleSubtitle(fragment)}",
            capsuleSubtitle(fragment).contains("只看楼主"),
        )
        openOverflow()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        assertTrue("the row is back to offering the filter: $labels", labels.any { it.contains("只看楼主") })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Paging a filtered list asks for the filtered stream's next ids: the source of a page is the
     * stream the site answered, never the topic's whole stream with the non-poster floors dropped
     * after they were fetched.
     */
    @Test
    fun pagingAFilteredListReadsTheFilteredStreamsNextIds() {
        serveLongTopic()

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size == HEAD_PAGE }
        harness.layoutApp()
        harness.idleClock(400)

        openOverflow()
        clickRow("只看楼主")
        harness.await("the poster's first window") { floorsOf(fragment).size == FILTERED_PAGE }
        harness.layoutApp()
        harness.idleClock(400)

        // Scroll through the filtered list until its stream is exhausted.
        repeat(8) {
            listView()!!.scrollBy(0, listView()!!.height)
            harness.idleClock(400)
        }
        harness.await("every poster floor") { floorsOf(fragment).size == LONG_OP_FLOORS.size }

        val chunkQueries = site.queriesFor("t/$TOPIC_ID/posts.json")
        val requested = chunkQueries.flatMap(::requestedFloors)
        println("only-op: chunk floors = $requested")
        assertTrue("the filtered stream paged", chunkQueries.isNotEmpty())
        assertEquals(
            "every requested id is one of the poster's floors",
            requested.filterNot { it in LONG_OP_FLOORS },
            emptyList<Int>(),
        )
        assertEquals(
            "the filtered stream was read to its end",
            LONG_OP_FLOORS,
            floorsOf(fragment).map { it.postNumber },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers -------------------------------------------------------------

    private fun openTopic(): TopicFragment {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "只看楼主的测试主题"))
        harness.idle()
        return harness.visibleFragment() as TopicFragment
    }

    private fun openOverflow() {
        harness.topFragment()!!.actionBar.createMenu().getItem(1).performClick()
        harness.idle()
    }

    private fun clickRow(label: String) {
        val rows = latestItemOptionsMenu(harness.activity)
        val row: ActionBarMenuSubItem = rows.firstOrNull {
            menuItemLabels(listOf(it)).first().contains(label)
        } ?: throw AssertionError("no menu row contains \"$label\" among " + menuItemLabels(rows))
        row.performClick()
        harness.idle()
    }

    private fun capsuleSubtitle(fragment: TopicFragment): String {
        val capsule = field(fragment, "avatarContainer") as? org.telegram.ui.Components.ChatAvatarContainer
            ?: return ""
        val text = capsule.subtitleTextView as? org.telegram.ui.ActionBar.SimpleTextView ?: return ""
        return text.text?.toString().orEmpty()
    }

    /** The reporter's monotonic mark, through its read-only accessor. */
    private fun reachedFloorMark(fragment: TopicFragment): Int {
        val reporter = field(fragment, "reporter")
        return reporter.javaClass.getMethod("getReachedFloorMark").invoke(reporter) as Int
    }

    /** Whether the screen carries only the poster's floors, from its own state. */
    private fun onlyOpOf(fragment: TopicFragment): Boolean = field(fragment, "onlyOp") as Boolean

    /** Puts the row drawing [floor] at the top of the viewport. */
    private fun scrollFloorToTop(fragment: TopicFragment, floor: Int) {
        @Suppress("UNCHECKED_CAST")
        val bubbleFloors = field(fragment, "bubbleFloors") as List<Int>
        val floors = floorsOf(fragment)
        val row = bubbleFloors.indices.firstOrNull { index ->
            val at = bubbleFloors[index]
            at in floors.indices && floors[at].postNumber == floor &&
                !isDividerRow(fragment, index)
        } ?: throw AssertionError("no row draws floor $floor")
        listView()!!.scrollToPosition(row)
        listView()!!.scrollBy(0, 1)
    }

    private fun firstVisibleFloor(fragment: TopicFragment): Int? {
        val manager = listView()?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return null
        @Suppress("UNCHECKED_CAST")
        val bubbleFloors = field(fragment, "bubbleFloors") as List<Int>
        val floors = floorsOf(fragment)
        var position = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        while (position in 0..last && position < bubbleFloors.size) {
            if (!isDividerRow(fragment, position)) {
                return floors[bubbleFloors[position]].postNumber
            }
            position++
        }
        return null
    }

    /** A day's date row and the unread divider stand above a floor rather than in it. */
    private fun isDividerRow(fragment: TopicFragment, position: Int): Boolean {
        @Suppress("UNCHECKED_CAST")
        val bubbles = field(fragment, "bubbles") as List<org.telegram.messenger.MessageObject>
        if (position !in bubbles.indices) return false
        return bubbles[position].isDateObject || bubbles[position] === fieldOrNull(fragment, "unreadDivider")
    }

    private fun fieldOrNull(target: Any, name: String): Any? {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target)
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private fun rowCount(): Int = contentRowCount(harness)

    @Suppress("UNCHECKED_CAST")
    private fun floorsOf(fragment: TopicFragment): List<Post> = field(fragment, "floors") as List<Post>

    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    // ---- payloads ------------------------------------------------------------

    private fun serveTopic() {
        site.serveJsonFor("t/$TOPIC_ID.json") { query ->
            if (query.contains("username_filters=op")) {
                topicJson(OP_IDS, OP_FLOORS, total = FLOOR_COUNT, opFloors = OP_FLOORS)
            } else {
                topicJson(ALL_IDS, ALL, total = FLOOR_COUNT, opFloors = OP_FLOORS)
            }
        }
        site.serveJsonFor("t/$TOPIC_ID/1.json") { query ->
            if (query.contains("username_filters=op")) {
                topicJson(OP_IDS, OP_FLOORS, total = FLOOR_COUNT, opFloors = OP_FLOORS)
            } else {
                topicJson(ALL_IDS, ALL, total = FLOOR_COUNT, opFloors = OP_FLOORS)
            }
        }
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query ->
            postsResponse(requestedFloors(query), OP_FLOORS)
        }
        site.serveJson("topics/timings", "{}")
    }

    private fun serveLongTopic() {
        site.serveJsonFor("t/$TOPIC_ID.json") { query ->
            if (query.contains("username_filters=op")) {
                topicJson(
                    LONG_OP_IDS, LONG_OP_FLOORS.take(FILTERED_PAGE),
                    total = LONG_FLOOR_COUNT, opFloors = LONG_OP_FLOORS,
                )
            } else {
                topicJson(
                    LONG_ALL_IDS, LONG_ALL.take(HEAD_PAGE),
                    total = LONG_FLOOR_COUNT, opFloors = LONG_OP_FLOORS,
                )
            }
        }
        site.serveJsonFor("t/$TOPIC_ID/1.json") { query ->
            if (query.contains("username_filters=op")) {
                topicJson(
                    LONG_OP_IDS, LONG_OP_FLOORS.take(FILTERED_PAGE),
                    total = LONG_FLOOR_COUNT, opFloors = LONG_OP_FLOORS,
                )
            } else {
                topicJson(
                    LONG_ALL_IDS, LONG_ALL.take(HEAD_PAGE),
                    total = LONG_FLOOR_COUNT, opFloors = LONG_OP_FLOORS,
                )
            }
        }
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query ->
            postsResponse(requestedFloors(query), LONG_OP_FLOORS)
        }
        site.serveJson("topics/timings", "{}")
    }

    /** One topic answer; [stream] names the site's stream and [posts] the window it carries. */
    private fun topicJson(
        stream: List<Long>,
        posts: List<Int>,
        total: Int,
        opFloors: List<Int>,
    ): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        base.put("id", TOPIC_ID)
        base.put("title", "只看楼主的测试主题")
        base.put("posts_count", total)
        base.put("highest_post_number", total)
        base.put("last_read_post_number", 0)
        base.getJSONObject("details").put(
            "created_by",
            JSONObject()
                .put("username", OP)
                .put("name", "楼主")
                .put("avatar_template", "/user_avatar/linux.do/op/{size}.png"),
        )
        val postStream = base.getJSONObject("post_stream")
        postStream.put("stream", JSONArray(stream))
        postStream.put("posts", JSONArray(posts.map { postJson(it, opFloors) }))
        return base.toString()
    }

    private fun postsResponse(floors: List<Int>, opFloors: List<Int>): String =
        JSONObject()
            .put("post_stream", JSONObject().put("posts", JSONArray(floors.map { postJson(it, opFloors) })))
            .toString()

    private fun postJson(floor: Int, opFloors: List<Int>): JSONObject {
        val author = if (floor in opFloors) OP else "guest$floor"
        val filler = (1..6).joinToString("") { "<p>第 $floor 楼，正文第 $it 段。</p>" }
        return JSONObject().apply {
            put("id", idForV(floor))
            put("post_number", floor)
            put("username", author)
            put("name", if (author == OP) "楼主" else "路人 $floor")
            put("reply_count", 0)
            put("created_at", "2024-01-01T10:00:00.000Z")
            put("cooked", "<p>第 $floor 楼</p>$filler")
            put("avatar_template", "/user_avatar/linux.do/$author/{size}.png")
        }
    }

    private fun requestedFloors(query: String): List<Int> =
        Regex("post_ids%5B%5D=(\\d+)")
            .findAll(query)
            .map { floorForId(it.groupValues[1].toLong()) }
            .toList()

    private fun floorForId(id: Long): Int = ((id - ID_BASE) / ID_STEP).toInt()

    private companion object {
        const val TOPIC_ID = 9100L
        const val OP = "op"

        /** Short topic: twelve floors, the poster answers at 1, 4, 7 and 10. */
        const val FLOOR_COUNT = 12
        val ALL = (1..FLOOR_COUNT).toList()
        val OP_FLOORS = listOf(1, 4, 7, 10)

        /** Long topic: forty floors, the poster answers every fourth one. */
        const val LONG_FLOOR_COUNT = 40
        val LONG_ALL = (1..LONG_FLOOR_COUNT).toList()
        val LONG_OP_FLOORS = LONG_ALL.filter { it % 4 == 1 }
        const val HEAD_PAGE = 20
        const val FILTERED_PAGE = 4

        val OP_IDS = OP_FLOORS.map(::idForV)
        val ALL_IDS = ALL.map(::idForV)
        val LONG_OP_IDS = LONG_OP_FLOORS.map(::idForV)
        val LONG_ALL_IDS = LONG_ALL.map(::idForV)

        /** Ids are far from both floor numbers and stream positions on purpose. */
        const val ID_BASE = 7_000_000L
        const val ID_STEP = 73L

        fun idForV(floor: Int): Long = ID_BASE + floor.toLong() * ID_STEP
    }
}
