package io.github.baba983.nativedo.topic

import android.view.ViewGroup
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
import org.telegram.ui.Components.RecyclerListView

/**
 * A long topic, end to end: the first page arrives with the topic, and the posts above it are
 * fetched as the reader reaches the end of the list.
 *
 * <p>The failure this pins: a topic payload carries only the first page of a long thread, so a
 * client that treats it as the whole topic stops halfway down and looks like a topic whose author
 * never wrote more. The second fetch has to ask for the ids the stream lists, which the assertion
 * on the query does.</p>
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
internal class TopicPagingTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveJson("t/$TOPIC_ID.json", topicJson(firstPage = FIRST_PAGE))
        // A page of posts answers with the posts it was asked for, the way the site does.
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query ->
            val requested = Regex("post_ids%5B%5D=(\\d+)").findAll(query)
                .map { it.groupValues[1].toLong() - 1000L }.toList()
            topicJson(firstPage = 0, only = requested)
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        runCatching { org.telegram.ui.Components.Bulletin.hideVisible() }
        runCatching { harness.idleClock(200) }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun reachingTheEndOfTheFirstPageFetchesTheRest() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Long topic"))
        harness.await("the first page to load") { rowCount() == FIRST_PAGE }

        val fragment = harness.visibleFragment() as TopicFragment
        val toolsField = TopicFragment::class.java.getDeclaredField("composerTools").apply { isAccessible = true }
        val tools = toolsField.get(fragment) as DiscourseComposerTools
        val composerField = TopicFragment::class.java.getDeclaredField("composer").apply { isAccessible = true }
        val composer = composerField.get(fragment) as org.telegram.ui.Components.ChatActivityEnterView
        composer.setFieldText("@late")
        assertTrue("an author not yet loaded is not a local candidate", !tools.mentions.panel().isShowing)
        val list = listView()!!
        harness.idle()
        list.scrollBy(0, list.height * 4)
        harness.await("the next page to load") { rowCount() == TOTAL_POSTS }
        val candidates = tools.mentions.panel().adapter as DiscourseMentionsAdapter
        assertEquals("paging refreshes candidates for the token still under the caret",
            "late_reader", candidates.itemAt(0)?.username)
        assertTrue("the newly loaded author is offered without another keystroke", tools.mentions.panel().isShowing)
        harness.renderApp("topic-paged")

        val queries = site.queriesFor("t/$TOPIC_ID/posts.json")
        println("paging: rows=" + rowCount() + " page queries=" + queries)
        assertEquals("the rest of the stream took one request", 1, queries.size)
        val query = queries[0]
        assertTrue(
            "the page starts at the id after the first page: $query",
            query.contains("post_ids%5B%5D=" + (1000L + FIRST_PAGE + 1)),
        )
        assertTrue(
            "and does not ask for the posts it already has: $query",
            !query.contains("post_ids%5B%5D=1001"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page that did not arrive while the topic is on screen. The failure this pins: a page fetch
     * that fails after the first page is up must not throw the rows away or move the reader, must
     * say so in passing rather than with a modal, and must re-ask on the next scroll once the site
     * answers again. The hint is upstream's own Bulletin, anchored above the composer, not a toast
     * that flashes and leaves no trace.
     */
    @Test
    fun aFailedPageSaysSoInABulletinAboveTheComposerAndTheNextScrollRetriesIt() {
        val failPage = java.util.concurrent.atomic.AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (failPage.get()) {
                okhttp3.mockwebserver.MockResponse().setResponseCode(503)
            } else {
                val query = site.queriesFor("t/$TOPIC_ID/posts.json").lastOrNull().orEmpty()
                val requested = Regex("post_ids%5B%5D=(\\d+)").findAll(query)
                    .map { it.groupValues[1].toLong() - 1000L }.toList()
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(topicJson(firstPage = 0, only = requested))
            }
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val topic = TopicFragment.forTopic(TOPIC_ID, "Failed page")
        harness.present(topic)
        harness.await("the first page to load") { rowCount() == FIRST_PAGE }

        val list = listView()!!
        harness.idle()
        val rowsBefore = rowCount()
        // A Bulletin shown by an earlier test in the shared sandbox must not be mistaken for this
        // page's hint: clear any standing one before the scroll that triggers the failure.
        org.telegram.ui.Components.Bulletin.hideVisible()
        harness.idle()
        list.scrollBy(0, list.height * 4)
        // The failure lands on the main looper and shows a Bulletin that hides itself after a while.
        // A full drain slides past that, so the hint is watched for one task at a time.
        val bulletin = awaitBulletin()
        assertTrue("the failed page was asked for", site.requestPaths.any { it == "t/$TOPIC_ID/posts.json" })
        val shown = bulkText(bulletin.getLayout())
        println("paging-failure: bulletin=$shown rows=${rowCount()}")
        assertTrue(
            "the page failure is said in passing: $shown",
            shown.any { it.contains("这一页没能读出来") },
        )

        // The rows already read stay, and the reader is not moved by a page that did not arrive.
        assertEquals("a failed page keeps the rows already on screen", rowsBefore, rowCount())
        assertEquals(
            "a failed page does not move the reader",
            rowsBefore, list.adapter!!.itemCount,
        )

        // The hint sits above the composer: the fragment reports the composer's own height as the
        // Bulletin's bottom offset, the way the chat screen's own delegate does.
        val delegate = topic.getBulletinDelegate()
        println("paging-failure: delegate=" + delegate?.getBottomOffset(0))
        assertTrue(
            "the failure is anchored above the composer/keyboard",
            delegate != null && delegate.getBottomOffset(0) > 0,
        )
        assertNotNull(
            "the failure offers an explicit retry, not just a promise that scrolling will help",
            retryButtonOf(bulletin),
        )
        harness.layoutApp()
        renderContent("page-failure-bulletin")

        // The site answers again: the next scroll re-asks and the page arrives.
        failPage.set(false)
        harness.idle()
        list.scrollBy(0, -list.height * 2)
        harness.idle()
        list.scrollBy(0, list.height * 4)
        harness.await("the retried page to load") { rowCount() == TOTAL_POSTS }
        harness.renderApp("topic-paged-after-failure")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page that failed is asked for once, not on every twitch of the reader's finger. The failure
     * this pins: with no per-edge latch, every near-edge callback - a real few-pixel scroll, a
     * repeated layout pass, time passing - fires another request and another hint at the same broken
     * page, so the reader is buried under Bulletins. Only leaving the bottom edge and coming back is
     * a new edge visit; a nudge that stays inside it is not.
     */
    @Test
    fun aFailedNextPageIsNotReAskedByEveryNearEdgeScroll() {
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            okhttp3.mockwebserver.MockResponse().setResponseCode(503)
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Repeated failure"))
        harness.await("the first page to load") { rowCount() == FIRST_PAGE }

        val list = listView()!!
        harness.idle()
        org.telegram.ui.Components.Bulletin.hideVisible()
        harness.idle()
        list.scrollBy(0, list.height * 4)
        harness.await("the failing page to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/posts.json")
        }
        awaitBulletin()
        assertEquals(
            "the failed page was asked for once",
            1,
            site.queriesFor("t/$TOPIC_ID/posts.json").size,
        )

        // Still at the bottom, the reader nudges the list: a real few-pixel scroll each way, repeated
        // layout passes, and time passing. None of these is a new edge visit, so none may re-ask.
        // Each nudge is given real time to land, so a re-ask cannot hide in flight.
        repeat(3) {
            list.scrollBy(0, -3)
            harness.layoutApp()
            harness.idle()
            settle(120)
            list.scrollBy(0, 3)
            harness.idle()
            settle(120)
        }
        harness.idleClock(1_000)
        settle()

        println("paging-repeat: queries=" + site.queriesFor("t/$TOPIC_ID/posts.json").size + " rows=" + rowCount())
        assertEquals(
            "nudging at the failed edge does not re-ask",
            1,
            site.queriesFor("t/$TOPIC_ID/posts.json").size,
        )
        assertEquals("the rows already read stay", FIRST_PAGE, rowCount())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The failure hint's own retry. The failure this pins: a hint that only promises a scroll retry
     * has no explicit action, so a reader who does not think to scroll has no way to bring the page
     * back. The tap asks for the same failed page again, from the cursor the failure left - no scroll
     * involved - and only that one page.
     */
    @Test
    fun tappingTheFailedPageHintRetriesThatPage() {
        val failPage = java.util.concurrent.atomic.AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (failPage.get()) {
                okhttp3.mockwebserver.MockResponse().setResponseCode(503)
            } else {
                val query = site.queriesFor("t/$TOPIC_ID/posts.json").lastOrNull().orEmpty()
                val requested = Regex("post_ids%5B%5D=(\\d+)").findAll(query)
                    .map { it.groupValues[1].toLong() - 1000L }.toList()
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(topicJson(firstPage = 0, only = requested))
            }
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Retry"))
        harness.await("the first page to load") { rowCount() == FIRST_PAGE }

        val list = listView()!!
        harness.idle()
        org.telegram.ui.Components.Bulletin.hideVisible()
        harness.idle()
        list.scrollBy(0, list.height * 4)
        val bulletin = awaitBulletin()
        assertEquals(
            "the failed page was asked for once",
            1,
            site.queriesFor("t/$TOPIC_ID/posts.json").size,
        )
        assertEquals("a failed page keeps the rows already on screen", FIRST_PAGE, rowCount())

        // The site answers again; the reader taps the hint's own retry rather than scrolling.
        failPage.set(false)
        val button = retryButtonOf(bulletin)
        assertNotNull("the failure hint offers an explicit retry", button)
        button!!.performClick()
        harness.await("the retried page to load") { rowCount() == TOTAL_POSTS }

        val queries = site.queriesFor("t/$TOPIC_ID/posts.json")
        println("paging-retry: queries=" + queries.size + " rows=" + rowCount())
        assertEquals("the tap asked for exactly one more page", 2, queries.size)
        assertTrue(
            "the retry re-asked for the page after the first: ${queries.last()}",
            queries.last().contains("post_ids%5B%5D=" + (1000L + FIRST_PAGE + 1)),
        )
        renderContent("topic-scroll-retry-recovered")
        harness.assertNoTelegramSideEffects()
    }

    /** Waits for the failure Bulletin one main-looper task at a time; a full drain slides past it. */
    private fun awaitBulletin(): org.telegram.ui.Components.Bulletin {
        var bulletin: org.telegram.ui.Components.Bulletin? = null
        repeat(3_000) {
            if (bulletin == null) {
                harness.idle(1)
                bulletin = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
                java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
            }
        }
        assertNotNull("the page failure is said in passing, not silently", bulletin)
        return bulletin!!
    }

    /** The hint's own explicit action, when the Bulletin carries a button. */
    private fun retryButtonOf(bulletin: org.telegram.ui.Components.Bulletin): android.view.View? {
        val layout = bulletin.getLayout()
        if (layout !is org.telegram.ui.Components.Bulletin.ButtonLayout) return null
        return layout.getButton()
    }

    /**
     * Pumps the main looper in real time for a while, so a request a scroll started can finish on
     * OkHttp's own thread and be seen by the site. Robolectric pauses the sandbox clock, so
     * LockSupport - not Thread.sleep - is the wait.
     */
    private fun settle(iterations: Int = 300) {
        repeat(iterations) {
            harness.idle()
            harness.idleUpstreamQueues()
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
    }

    /** Draws the screen as it stands to a retained artifact for this pass. */
    private fun renderContent(name: String) {
        val view = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/topic-scroll-failure/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("paging render: $out")
    }

    private fun bulkText(view: android.view.View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { bulkText(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** A topic payload the way Discourse sends one: the posts of one page plus the id stream. */
    private fun topicJson(firstPage: Int, only: List<Long> = emptyList()): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = JSONArray()
        val posts = JSONArray()
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        for (number in 1..TOTAL_POSTS) {
            stream.put(1000L + number)
            if (number > firstPage && number.toLong() !in only) continue
            val post = JSONObject(template.toString())
            post.put("id", 1000L + number)
            post.put("post_number", number)
            post.put("cooked", "<p>第 $number 楼</p>")
            if (number > FIRST_PAGE) post.put("username", "late_reader")
            posts.put(post)
        }
        base.getJSONObject("post_stream").put("stream", stream)
        base.getJSONObject("post_stream").put("posts", posts)
        base.put("posts_count", TOTAL_POSTS)
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 303L
        const val FIRST_PAGE = 20
        const val TOTAL_POSTS = 25
    }
}
