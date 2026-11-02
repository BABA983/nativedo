package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * The reply footer on a floor's last bubble: how many direct replies the *site* says a post has, and
 * the faces of the direct replies this session has actually loaded.
 *
 * <p>The failures this pins. Discourse carries the authoritative total on the post itself
 * (`reply_count`) while a session only ever holds some of the direct replies, so a footer that drew
 * its own count would under-report, and one that invented faces for the replies not yet read would
 * lie about who answered. The footer belongs to the *end* of a floor: a body that splits into a run
 * of bubbles (text, a poll, more text) draws it once, not on every bubble. And nothing reads the
 * replies endpoint just to draw the footer - the endpoint carries the replies themselves, so it is
 * opened lazily, when the reader asks for the thread.</p>
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
internal class TopicReplyFooterTest {

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

    /**
     * A floor whose body splits into three bubbles (text, a poll, text) draws the footer only on
     * its last bubble, and the footer's count is the site's own `reply_count`, not the number of
     * direct replies this session happens to hold. A poll is the one cut a body still keeps: a post's
     * own links, onebox or not, stay in the bubble they were written in.
     */
    @Test
    fun aMultiSegmentFloorDrawsTheFooterOnceAndKeepsTheSitesOwnCount() {
        val poll = """
            <div class="poll" data-poll-name="q1"><div class="poll-container"><ul>
            <li data-poll-option-id="opt-a">A</li></ul></div></div>
        """.trimIndent()
        // Floor 1: three bubbles, five direct replies per the site, three of them loaded (alice
        // twice and carol once). Floor 5 answers nobody and carries no replies.
        val posts = listOf(
            post(id = 99001, floor = 1, username = "op", name = "Op", replyCount = 5,
                cooked = "<p>intro</p>$poll<p>tail</p>", polls = pollPayload()),
            post(id = 99002, floor = 2, username = "alice", name = "Alice", replyTo = 1, cooked = "<p>a</p>"),
            post(id = 99003, floor = 3, username = "carol", name = "Carol", replyTo = 1, cooked = "<p>c</p>"),
            post(id = 99004, floor = 4, username = "alice", name = "Alice", replyTo = 1, cooked = "<p>a again</p>"),
            post(id = 99005, floor = 5, username = "dave", name = "Dave", cooked = "<p>d</p>"),
        )
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, posts.map { it.getLong("id") }))

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply footer"))
        harness.await("the topic to load") { rowCount() == 7 }

        val fragment = harness.visibleFragment() as TopicFragment
        val bubbles = field(fragment, "bubbles") as List<MessageObject>
        // Floor 1's three bubbles lead the list; floor 2, 3, 4, 5 are one each.
        assertEquals("floor 1 is three bubbles", 3, floorBubbleCount(fragment, 0))
        val first = bubbles[0]
        val middle = bubbles[1]
        val last = bubbles[2]

        assertTrue("the last bubble of the floor carries the footer", last.hasReplies())
        assertEquals("the count is the site's own reply_count", 5, last.getRepliesCount())
        assertFalse("the floor's first bubble draws no footer", first.hasReplies())
        assertFalse("neither does the poll bubble in the middle", middle.hasReplies())

        // Faces come only from replies this session loaded, deduplicated by author: alice wrote two
        // of them, so two faces, never five.
        val repliers = last.messageOwner.replies.recent_repliers
        assertEquals("only the loaded authors are shown, deduplicated", 2, repliers.size)
        val ids = repliers.map { (it as TLRPC.TL_peerUser).user_id }.toSet()
        assertEquals(
            "the faces are the loaded direct replies' authors",
            setOf(DiscourseRows.userId("alice"), DiscourseRows.userId("carol")),
            ids,
        )

        // And a floor with no replies carries no footer.
        val lastFloor = bubbles[bubbles.size - 1]
        assertFalse("a floor nobody answered draws no footer", lastFloor.hasReplies())

        // The stock cell reports the footer through its own draw flag once it has measured.
        listView()!!.scrollToPosition(0)
        harness.layoutApp()
        harness.idleClock(200)
        val cells = laidOutCells()
        val lastCell = cells.firstOrNull { it.messageObject === last }
        assertNotNull("the last bubble drew a cell", lastCell)
        println(
            "reply footer: count=${last.getRepliesCount()} " +
                "faces=${last.messageOwner.replies.recent_repliers.size} " +
                "footerDrawn=${lastCell!!.hasCommentLayout()}",
        )
        assertTrue("the cell draws the comment footer", lastCell.hasCommentLayout())
        assertFalse(
            "the first bubble's cell draws no footer",
            cells.first { it.messageObject === first }.hasCommentLayout(),
        )

        // Drawing the footer never asked the site for the replies themselves.
        assertFalse(
            "the footer is drawn without reading the replies endpoint",
            site.requestPaths.any { it.contains("/replies") },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A floor opened on its own (a window that does not carry floor 1) still draws the footer from
     * its own payload, and the count stays the site's even though none of its replies are loaded.
     */
    @Test
    fun aWindowWithoutTheAnsweredParentStillCarriesItsOwnCount() {
        // Opened at floor 12: the window carries 8..16, and floor 12 announces two direct replies,
        // neither of which is in the window (the site lists a reply under its own floor, later).
        val window = (8..16).map { floor ->
            post(id = 90000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                replyCount = if (floor == 12) 2 else 0, cooked = "<p>post number $floor</p>")
        }
        site.serveJson("t/$TOPIC_ID/12.json", topicJson(window, (1..40).map { 90000L + it }))

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forFloor(TOPIC_ID, "Window", 12))
        harness.await("the window") { rowCount() == 9 }

        val fragment = harness.visibleFragment() as TopicFragment
        val bubbles = field(fragment, "bubbles") as List<MessageObject>
        val floorTwelve = bubbles.first { it.messageOwner.id == 90012 }
        assertTrue("floor 12 announces replies", floorTwelve.hasReplies())
        assertEquals("and the site's own count", 2, floorTwelve.getRepliesCount())
        assertTrue(
            "no face is invented for a reply that was not loaded",
            floorTwelve.messageOwner.replies.recent_repliers.isEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A direct reply that arrives with a later page keeps the floor's footer and adds its face: the
     * floor is rebuilt when the page lands, and the count is still the site's own.
     */
    @Test
    fun aLaterDirectReplyKeepsTheFooterAndAddsItsFace() {
        val total = 25
        val firstPage = 20
        fun payload(numbers: Set<Int>): String {
            val base = JSONObject(harness.fixture("topic-detail.json"))
            val stream = JSONArray()
            for (n in 1..total) stream.put(80000L + n)
            val posts = JSONArray()
            for (n in numbers.sorted()) {
                posts.put(JSONObject().apply {
                    put("id", 80000L + n)
                    put("post_number", n)
                    put("username", "u$n")
                    put("name", "User $n")
                    put("reply_count", if (n == 1) 3 else 0)
                    put("created_at", "2024-01-01T10:00:00.000Z")
                    put("cooked", "<p>post $n</p>")
                    put("avatar_template", "")
                    if (n == 2 || n == 21) put("reply_to_post_number", 1)
                })
            }
            base.put("posts_count", total)
            base.getJSONObject("post_stream").put("stream", stream)
            base.getJSONObject("post_stream").put("posts", posts)
            return base.toString()
        }
        site.serveJson("t/$TOPIC_ID.json", payload((1..firstPage).toSet()))
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query ->
            val numbers = Regex("post_ids%5B%5D=(\\d+)").findAll(query)
                .map { it.groupValues[1].toLong() - 80000L }
                .map { it.toInt() }
                .toSet()
            payload(numbers)
        }

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Paged replies"))
        harness.await("the first page") { rowCount() == firstPage }

        val fragment = harness.visibleFragment() as TopicFragment
        var floorOne = (field(fragment, "bubbles") as List<MessageObject>).first()
        assertEquals("the site's count", 3, floorOne.getRepliesCount())
        assertEquals("one loaded face at first", 1, floorOne.messageOwner.replies.recent_repliers.size)

        val list = listView()!!
        harness.layoutApp()
        harness.idle()
        list.scrollToPosition(firstPage - 1)
        harness.layoutApp()
        harness.idle()
        list.scrollBy(0, 400)
        harness.await("the rest of the stream") { rowCount() == total }

        floorOne = (field(fragment, "bubbles") as List<MessageObject>).first()
        assertTrue("the footer survives the paging rebuild", floorOne.hasReplies())
        assertEquals("and keeps the site's count", 3, floorOne.getRepliesCount())
        assertEquals(
            "the later direct reply adds its face",
            2,
            floorOne.messageOwner.replies.recent_repliers.size,
        )
        val replierIds = floorOne.messageOwner.replies.recent_repliers
            .map { (it as TLRPC.TL_peerUser).user_id }
        assertTrue("face of floor 21's author", replierIds.contains(DiscourseRows.userId("u21")))
        harness.assertNoTelegramSideEffects()
    }

    /** How many bubbles the floor at [floorIndex] draws. */
    private fun floorBubbleCount(fragment: TopicFragment, floorIndex: Int): Int {
        val bubbleFloors = field(fragment, "bubbleFloors") as List<Int>
        return bubbleFloors.count { it == floorIndex }
    }

    private fun post(
        id: Long, floor: Int, username: String, name: String, cooked: String,
        replyTo: Int? = null, replyCount: Int = 0, polls: JSONArray? = null,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("post_number", floor)
        put("username", username)
        put("name", name)
        put("reply_count", replyCount)
        put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
        put("cooked", cooked)
        put("avatar_template", "")
        if (replyTo != null) put("reply_to_post_number", replyTo)
        if (polls != null) put("polls", polls)
    }

    /** The site's `polls[]` payload for the `q1` poll the body of floor 1 names. */
    private fun pollPayload(): JSONArray = JSONArray().put(JSONObject().apply {
        put("id", 7001L)
        put("name", "q1")
        put("type", "regular")
        put("status", "open")
        put("results", "always")
        put("voters", 3)
        put("options", JSONArray().put(JSONObject().apply {
            put("id", "opt-a")
            put("html", "A")
            put("votes", 3)
        }))
    })

    private fun topicJson(posts: List<JSONObject>, streamIds: List<Long>): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray(posts))
        stream.put("stream", JSONArray(streamIds))
        return base.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 917L
    }
}
