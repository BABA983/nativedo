package io.github.baba983.nativedo.topic

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
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A read resumed into the middle of a long topic, and the pages above it.
 *
 * <p>The failure this pins: a topic read is opened where the site left the reader - a window from
 * the middle of the stream - and the floors above that window are never fetched. The reader who
 * scrolls up reaches the top of the window and stops, looking at a topic that appears to begin
 * halfway through. The fix pages the stream both ways: the slice before the loaded window is
 * fetched when the list nears its top, and it is prepended, not rebound - the reader's place and
 * every row already drawn are left exactly where they were.</p>
 *
 * <p>The ids, floors and stream positions are deliberately non-contiguous and mutually distinct, so
 * a client that paged by the wrong one of the three lands somewhere else. A page that lands after
 * the window it belonged to was replaced must draw nothing: the window is the owner of the rows, and
 * a read still in flight for a window nobody is looking at is dropped. Forward paging carries the
 * same guard.</p>
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
internal class TopicHistoryPagingTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private val artifacts: java.io.File = java.io.File(repoRoot(), ".amp/in/artifacts/topic-history")

    private fun repoRoot(): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (java.io.File(dir, "fixtures/discourse").isDirectory) {
                return dir
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root")
    }

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        runCatching { org.telegram.ui.Components.Bulletin.hideVisible() }
        runCatching { harness.idleClock(200) }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The slice before a resumed window is fetched when the reader nears the top, by the stream's
     * own ids - not by floor numbers, and none of the ids already on screen are re-asked.
     */
    @Test
    fun nearingTheTopOfAResumedWindowFetchesThePreviousStreamSlice() {
        serveResumeSite()
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.idle()

        harness.await("the previous slice to load") { rowCount() > WINDOW_PAGE }
        val floors = requestedFloors(lastPostsQuery())
        println("history: prefetched floors=$floors rows=${rowCount()}")
        assertTrue("more than a single id was prefetched: $floors", floors.size > 1)
        assertEquals(
            "the slice is a contiguous run of the stream",
            floors.sorted(),
            (floors.min()!!..floors.max()!!).toList(),
        )
        assertEquals(
            "the slice ends at the floor immediately above the loaded window",
            WINDOW_FLOOR_FIRST - 1,
            floors.max(),
        )
        assertTrue("no id already on screen is re-fetched: $floors", floors.all { it < WINDOW_FLOOR_FIRST })
        assertEquals("the slice was prepended", WINDOW_PAGE + floors.size, rowCount())
        assertEquals("the first floor on screen is the new top", floors.min(), floorsOf(fragment).first().postNumber)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A prepend keeps the reader's place: the message that was at the top of the viewport is still
     * at the same pixel, it is the same MessageObject (everything already drawn is not rebuilt), and
     * the floor/bookkeeping maps describe the new list.
     */
    @Test
    fun prependingKeepsTheAnchorAtItsPixelAndDoesNotRebuildTheLoadedFloors() {
        serveResumeSite()
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        val bubblesBefore = bubblesOf(fragment)
        val anchor = bubblesBefore[0]
        list.scrollBy(0, 1)
        val anchorCell = laidOutCells().firstOrNull { it.messageObject === anchor }
            ?: throw AssertionError("the anchor row is not on screen before the prepend")
        val anchorTop = anchorCell.top
        // The reader opens the anchor's own details block. Upstream's rich blocks are not views: the
        // layout hit-tests a touch and hands it to the block's own handler, so the block is driven
        // the same way - the delegate upstream sets before the dispatch, then the down/up the handler
        // reads - rather than a flag set by hand.
        val details = anchor.richLayout.blocks
            .filterIsInstance<org.telegram.messenger.RichMessageLayout.RichDetailsBlock>()
            .firstOrNull() ?: throw AssertionError("the anchor carries no details block")
        assertTrue("the details start collapsed", !details.isOpen())
        clickDetails(anchorCell, details)
        harness.idle()
        assertTrue("the tap opened the details", details.isOpen())

        harness.await("the previous slice to load") { rowCount() > WINDOW_PAGE }
        harness.layoutApp()
        harness.idle()

        val bubblesAfter = bubblesOf(fragment)
        val added = bubblesAfter.size - WINDOW_PAGE
        assertTrue("something was prepended", added > 0)
        assertSame(
            "an already-loaded floor keeps its own MessageObject (the list is not rebuilt)",
            anchor,
            bubblesAfter[added],
        )
        // The unrelated prepend leaves the opened details open: the anchor's row was not rebuilt, so
        // the state the reader put on it is still there.
        val detailsAfter = anchor.richLayout.blocks
            .filterIsInstance<org.telegram.messenger.RichMessageLayout.RichDetailsBlock>()
            .firstOrNull() ?: throw AssertionError("the anchor's details block left")
        assertTrue("the details stay open across an unrelated prepend", detailsAfter.isOpen())
        val anchorAfter = laidOutCells().firstOrNull { it.messageObject === anchor }
            ?: throw AssertionError("the anchor row left the screen after the prepend")
        assertTrue(
            "the anchor keeps its pixel offset (was $anchorTop, now ${anchorAfter.top})",
            kotlin.math.abs(anchorAfter.top - anchorTop) <= 1,
        )
        // The parallel maps still line up: the bubble indexes a real floor, whose number is the
        // anchor's own.
        val bubbleFloors = bubbleFloorsOf(fragment)
        assertEquals("one floor index per bubble", bubblesAfter.size, bubbleFloors.size)
        assertEquals("one prepared body per bubble", bubblesAfter.size, bubbleBodiesOf(fragment).size)
        assertEquals(
            "the anchor bubble still points at the anchor's floor",
            WINDOW_FLOOR_FIRST,
            floorsOf(fragment)[bubbleFloors[added]].postNumber,
        )
        writeVisual("topic-history-prepended")
    }

    /**
     * The reply/avatar maps survive a prepend: a floor that answers one brought in by the page gains
     * its preview (targeted by floor number, not by a stale index), and the new author's avatar is
     * registered.
     */
    @Test
    fun prependingResolvesRepliesAndAvatarsAgainstTheNewFloors() {
        serveResumeSite()
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the previous slice to load") { rowCount() > WINDOW_PAGE }
        harness.layoutApp()
        harness.idle()

        val replyTargets = replyTargetFloorsOf(fragment)
        assertTrue(
            "the floor answering the newly loaded floor resolves to its number, not an index: $replyTargets",
            replyTargets.containsValue(REPLY_TARGET_FLOOR),
        )
        val avatars = avatarTemplatesOf(fragment)
        assertTrue(
            "the newly loaded author's avatar template is registered",
            avatars.containsKey(DiscourseRows.userId("reader$REPLY_TARGET_FLOOR")),
        )
    }

    /**
     * A prepend that also rebuilds the anchor's floor must still keep the reader's place. The floor
     * at the top of the viewport answers a floor the arriving slice brings in, so the prepend
     * rebuilds that floor's rows and the MessageObject captured as the anchor is gone: a restore
     * that looks the anchor up by object identity finds nothing, and the reader is left wherever the
     * prepend pushed them. The reader's place is the floor and the row within it, not the identity
     * of the object a rebuild happened to replace.
     */
    @Test
    fun aPrependThatRebuildsTheAnchorFloorStillKeepsTheReaderInPlace() {
        serveResumeSite()
        val release = CountDownLatch(1)
        val held = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (held.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(postsResponse(requestedFloors(lastPostsQuery())))
        }
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        // The reader reaches the top, so the slice above is fetched - and held while they scroll on.
        list.scrollBy(0, 1)
        harness.await("the previous slice to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/posts.json")
        }
        // Before the answer lands, the reader keeps scrolling: the floor that answers a floor of
        // the arriving slice becomes the first visible row, a little above the viewport's top edge.
        val anchorFloor = floorsOf(fragment).indexOfFirst { it.postNumber == REPLYING_FLOOR }
        val anchorRow = bubbleFloorsOf(fragment).indexOfFirst { it == anchorFloor }
        assertTrue("the answering floor is in the loaded window", anchorFloor >= 0 && anchorRow >= 0)
        list.scrollToPosition(anchorRow)
        harness.layoutApp()
        val preCell = laidOutCells().firstOrNull { it.messageObject === bubblesOf(fragment)[anchorRow] }
            ?: throw AssertionError("the anchor row is not on screen before the prepend")
        // Put the anchor a little above the viewport's top edge, still the first visible row.
        list.scrollBy(0, preCell.top + 10)
        harness.layoutApp()
        val anchor = bubblesOf(fragment)[anchorRow]
        val anchorCell = laidOutCells().firstOrNull { it.messageObject === anchor }
            ?: throw AssertionError("the anchor row is not on screen before the prepend")
        val anchorTop = anchorCell.top
        assertTrue("the anchor is scrolled a little above the top edge ($anchorTop)", anchorTop < 0)

        release.countDown()
        harness.await("the previous slice to load") { rowCount() > WINDOW_PAGE }
        harness.layoutApp()
        harness.idle()

        // The answering floor's rows were rebuilt: the captured object is not the row any more.
        assertNotSame(
            "the answering floor's rows were rebuilt by the prepend",
            anchor,
            bubblesOf(fragment)[anchorRow],
        )
        // The reader's place is the floor and the row within it, so that floor is still the first
        // visible row at the same pixel.
        val list2 = listView()!!
        val first = (list2.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
            .findFirstVisibleItemPosition()
        val floorNumber = floorsOf(fragment)[bubbleFloorsOf(fragment)[first]].postNumber
        println("history-rebuild-anchor: anchorTop=$anchorTop first=$first floor=$floorNumber rows=${rowCount()}")
        assertEquals("the first visible floor is still the answering floor", REPLYING_FLOOR, floorNumber)
        val anchorAfter = laidOutCells()
            .firstOrNull { it.messageObject === bubblesOf(fragment)[first] }
            ?: throw AssertionError("the anchor row left the screen after the prepend")
        assertTrue(
            "the anchor keeps its pixel offset (was $anchorTop, now ${anchorAfter.top})",
            kotlin.math.abs(anchorAfter.top - anchorTop) <= 1,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A previous slice the site answers with nothing - every post in it was deleted, or the reader
     * may not see them - must still move the cursor past it. A page that drew nothing and left the
     * cursor where it was would ask for that same empty slice forever, and an earlier slice the site
     * really has would never be reached.
     */
    @Test
    fun anEmptyPreviousSliceAdvancesTheCursorSoAnEarlierSliceIsReached() {
        serveResumeSite()
        // Floors 16..35 are the first slice above the resumed window: the site answers it empty.
        val deleted = (16..35).toSet()
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            val floors = requestedFloors(lastPostsQuery())
            val body = if (floors.isNotEmpty() && floors.all { it in deleted }) {
                JSONObject().put("post_stream", JSONObject().put("posts", JSONArray())).toString()
            } else {
                postsResponse(floors)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        // The empty slice is consumed: the cursor moves to the top of the slice that was asked for.
        harness.await("the empty slice to be consumed") { windowStartOf(fragment) == 15 }
        assertEquals("the empty slice drew nothing", WINDOW_PAGE, rowCount())

        // The next approach asks for the earlier slice, which the site does have, and it is shown.
        list.scrollBy(0, 1)
        harness.await("the earlier slice to load") { rowCount() > WINDOW_PAGE }
        assertEquals("the earlier slice is prepended", 1, firstFloor(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A previous slice that failed must not move the cursor: the reader asks for the same slice
     * again on the next approach, rather than skipping past a page that never arrived.
     */
    @Test
    fun aFailedPreviousSliceDoesNotAdvanceTheCursor() {
        serveResumeSite()
        site.serveResponse("t/$TOPIC_ID/posts.json") { MockResponse().setResponseCode(503) }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        val before = windowStartOf(fragment)
        list.scrollBy(0, 1)
        harness.await("the failing slice to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/posts.json")
        }
        harness.idle()
        assertEquals("a failure keeps the cursor where it was", before, windowStartOf(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page-down jump that overtook a history page must not weld the history paging shut. The jump
     * bumps the window generation, so the in-flight backward page's callback is dropped - and with
     * it the only code that would have cleared the loading flag. Unless the jump clears that flag as
     * it takes the window over, the next approach to the top finds the direction "already loading"
     * and never asks again.
     */
    @Test
    fun aFailedJumpDoesNotLockTheHistoryPagingItOvertook() {
        serveResumeSite()
        val release = CountDownLatch(1)
        val held = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (held.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(postsResponse(requestedFloors(lastPostsQuery())))
        }
        site.serveResponse(JUMP_PATH) { MockResponse().setResponseCode(503) }
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the previous page to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/posts.json")
        }

        // The reader jumps while the history page is still on its way, and the jump fails.
        loadPageDownWindow(fragment, JUMP_FLOOR)
        harness.await("the failed jump to be asked for") { site.requestPaths.contains(JUMP_PATH) }
        harness.idle()

        // The held page lands now, but it belongs to the window the jump replaced.
        release.countDown()
        settle()

        // The next approach to the top must still be able to retry the previous slice.
        list.scrollToPosition(0)
        harness.layoutApp()
        list.scrollBy(0, 1)
        harness.await("the history page to retry") { rowCount() > WINDOW_PAGE }
        harness.assertNoTelegramSideEffects()
    }

    /** Scrolling the retained rows during a window switch must not start pages for the old stream. */
    @Test
    fun scrollingDuringAWindowReadDoesNotGiveOldRowsTheNewGeneration() {
        serveResumeSite()
        val release = CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID/$WINDOW_2.json") {
            release.await(20, TimeUnit.SECONDS)
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(windowJson(WINDOW_2))
        }
        val fragment = openResumeWindow()
        try {
            val before = site.queriesFor("t/$TOPIC_ID/posts.json").size
            loadWindow(fragment, WINDOW_2, WINDOW_2)
            harness.await("the replacement window request") {
                site.requestPaths.contains("t/$TOPIC_ID/$WINDOW_2.json")
            }
            val list = listView()!!
            scrollToTop(list)
            list.scrollBy(0, 1)
            settle()
            assertEquals("the pending window owns navigation, not the retained rows' paging",
                before, site.queriesFor("t/$TOPIC_ID/posts.json").size)
            release.countDown()
            harness.await("the replacement window") { firstFloor(fragment) == WINDOW_2_FLOOR_FIRST }
            assertEquals("the replacement still carries only its own window", WINDOW_PAGE, rowCount())
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * A backward page that failed keeps every row and every place: the reader is not moved, nothing
     * is thrown away, and the failure hint's own retry re-asks for the same slice from the cursor the
     * failure left - no scroll involved, and only that one slice.
     */
    @Test
    fun aFailedPreviousPageKeepsTheRowsAndTheHintRetriesIt() {
        serveResumeSite()
        val failing = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (failing.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(postsResponse(requestedFloors(lastPostsQuery())))
            }
        }
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        val floorsBefore = floorsOf(fragment).map { it.postNumber }
        list.scrollBy(0, 1)
        harness.await("the failing page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }
        val bulletin = awaitBulletin()
        println("history-failure: rows=${rowCount()} first=${floorsOf(fragment).first().postNumber}")
        assertEquals("a failed page keeps the rows already on screen", WINDOW_PAGE, rowCount())
        assertEquals("a failed page does not move the reader", floorsBefore, floorsOf(fragment).map { it.postNumber })
        assertEquals("the failed page was asked for once", 1, site.queriesFor("t/$TOPIC_ID/posts.json").size)
        writeScrollVisual("history-page-failure-bulletin")

        // The site answers again: the hint's own retry re-asks, no scroll involved.
        failing.set(false)
        val button = retryButtonOf(bulletin)
        assertNotNull("the failure hint offers an explicit retry", button)
        button!!.performClick()
        harness.await("the retried page to load") { rowCount() > WINDOW_PAGE }
        assertEquals("the tap asked for exactly one more slice", 2, site.queriesFor("t/$TOPIC_ID/posts.json").size)
        val retried = requestedFloors(site.queriesFor("t/$TOPIC_ID/posts.json").last())
        assertEquals("the retried page is prepended above the window", retried.min(), floorsOf(fragment).first().postNumber)
        assertEquals("the retried page is prepended", WINDOW_PAGE + retried.size, rowCount())
        writeScrollVisual("history-scroll-retry-recovered")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A failed backward page is asked for once for this visit to the top. The failure this pins:
     * without a per-edge latch, each near-top callback - a few-pixel nudge, a repeated layout, time
     * passing - fires another request and another hint at the same broken slice, so the reader is
     * buried under Bulletins. Only leaving the top edge and coming back is a new visit.
     */
    @Test
    fun aFailedPreviousPageIsNotReAskedByEveryNearTopScroll() {
        serveResumeSite()
        site.serveResponse("t/$TOPIC_ID/posts.json") { MockResponse().setResponseCode(503) }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the failing page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }
        awaitBulletin()
        assertEquals("the failed page was asked for once", 1, site.queriesFor("t/$TOPIC_ID/posts.json").size)

        // Each nudge is given real time to land, so a re-ask cannot hide in flight.
        repeat(3) {
            list.scrollBy(0, -1)
            harness.layoutApp()
            harness.idle()
            settle(120)
            list.scrollBy(0, 1)
            harness.idle()
            settle(120)
        }
        harness.idleClock(1_000)
        settle()
        println("history-repeat: queries=" + site.queriesFor("t/$TOPIC_ID/posts.json").size + " rows=" + rowCount())
        assertEquals(
            "nudging at the failed top edge does not re-ask",
            1,
            site.queriesFor("t/$TOPIC_ID/posts.json").size,
        )
        assertEquals("the rows already read stay", WINDOW_PAGE, rowCount())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A failure hint's retry belongs to the window the failure happened in. The failure this pins: a
     * retry that only re-runs the last failure, with no window guard, reads a slice of the old window
     * into whatever is on screen now. Once the reader opens a different window, tapping the old hint
     * must read nothing at all.
     */
    @Test
    fun aRetryForAReplacedWindowDoesNotReadTheOldWindow() {
        serveResumeSite()
        site.serveResponse("t/$TOPIC_ID/posts.json") { MockResponse().setResponseCode(503) }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the failing page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }
        val bulletin = awaitBulletin()
        val before = site.queriesFor("t/$TOPIC_ID/posts.json").size
        assertEquals("the failed page was asked for once", 1, before)

        // The reader opens another window while the hint is up.
        loadWindow(fragment, WINDOW_2, WINDOW_2)
        harness.await("the replacement window to load") { firstFloor(fragment) == WINDOW_2_FLOOR_FIRST }

        val button = retryButtonOf(bulletin)
        assertNotNull("the failure hint offers an explicit retry", button)
        button!!.performClick()
        harness.idleClock(400)
        println("history-stale-retry: queries=" + site.queriesFor("t/$TOPIC_ID/posts.json").size + " first=" + firstFloor(fragment))
        assertEquals(
            "a retry for a replaced window reads nothing",
            before,
            site.queriesFor("t/$TOPIC_ID/posts.json").size,
        )
        assertEquals("the replacement window is untouched", WINDOW_PAGE, rowCount())
        harness.assertNoTelegramSideEffects()
    }

    /** A queued challenge retry must not open login after navigation supersedes its window. */
    @Test
    fun aStaleChallengeRetryDoesNotOpenLoginForAReplacedWindow() {
        serveResumeSite()
        val release = CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            release.await(5, TimeUnit.SECONDS)
            MockResponse().setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Just a moment</html>")
        }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        release.countDown()
        val button = retryButtonOf(awaitBulletin())!!
        // Capture the real button action to exercise a queued tap independently of Bulletin's
        // detach cleanup. Clicking an already detached button would only test upstream's no-op.
        val actionField = org.telegram.ui.Components.Bulletin.UndoButton::class.java
            .getDeclaredField("undoAction").apply { isAccessible = true }
        val queuedTap = actionField.get(button) as Runnable

        loadWindow(fragment, WINDOW_2, WINDOW_2)
        harness.await("the replacement window") { firstFloor(fragment) == WINDOW_2_FLOOR_FIRST }
        queuedTap.run()
        harness.idleClock(400)

        assertSame("an obsolete challenge must not navigate to login", fragment, harness.visibleFragment())
        assertEquals("no stale page was retried", 1, site.queriesFor("t/$TOPIC_ID/posts.json").size)
        harness.assertNoTelegramSideEffects()
    }

    /** Leaving and returning may recover the page, making its old retry action obsolete. */
    @Test
    fun anOldRetryDoesNotFetchAnotherSliceAfterTheFailedPageArrives() {
        serveResumeSite()
        val failing = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (failing.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(postsResponse(requestedFloors(lastPostsQuery())))
            }
        }
        val fragment = openResumeWindow()
        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        val button = retryButtonOf(awaitBulletin())!!

        failing.set(false)
        list.scrollToPosition(10)
        harness.layoutApp()
        list.scrollBy(0, 1)
        harness.idle()
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("returning to the edge recovers the failed slice") { rowCount() > WINDOW_PAGE }
        val before = site.queriesFor("t/$TOPIC_ID/posts.json").size
        val cursor = windowStartOf(fragment)
        assertEquals("only the failed page was retried", 2, before)

        button.performClick()
        settle(120)
        assertEquals("the old hint cannot fetch the slice above the recovered page",
            before, site.queriesFor("t/$TOPIC_ID/posts.json").size)
        assertEquals("the cursor is unchanged by an obsolete retry", cursor, windowStartOf(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A failed forward page must not silence the backward direction: the two edges fail and retry
     * independently, so a reader who could not read on can still read back.
     */
    @Test
    fun aFailedForwardPageDoesNotBlockABackwardPage() {
        serveResumeSite()
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            val floors = requestedFloors(lastPostsQuery())
            val forward = floors.isNotEmpty() && floors.min() > WINDOW_FLOOR_FIRST + WINDOW_PAGE - 1
            if (forward) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(postsResponse(floors))
            }
        }
        val fragment = openResumeWindow()
        val list = listView()!!
        harness.layoutApp()
        // Down to the bottom: the next page is asked for and fails.
        list.scrollBy(0, list.height * 10)
        harness.await("the forward page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }
        awaitBulletin()
        assertEquals("the forward page failed once", 1, site.queriesFor("t/$TOPIC_ID/posts.json").size)
        assertEquals("the failed forward page keeps the window", WINDOW_PAGE, rowCount())

        // The reader is at the top next: the slice above must still be read.
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the backward page to load") { rowCount() > WINDOW_PAGE }
        println("history-direction: rows=" + rowCount() + " first=" + firstFloor(fragment))
        assertTrue("a backward page above the window was read", firstFloor(fragment)!! < WINDOW_FLOOR_FIRST)
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

    /** Draws the screen to a retained artifact for this pass's failure/recovery inspection. */
    private fun writeScrollVisual(name: String) {
        harness.layoutApp()
        harness.idle()
        val view = harness.activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/topic-scroll-failure/$name.png")
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("history scroll render: $out")
    }

    /**
     * A backward page still in flight when the reader jumps to another window must draw nothing when
     * it lands: the rows belong to the window nobody is looking at any more.
     */
    @Test
    fun aLatePreviousPageDoesNotPolluteAWindowOpenedWhileItWasInFlight() {
        serveResumeSite()
        val release = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (hold.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(postsResponse(requestedFloors(lastPostsQuery())))
        }
        val fragment = openResumeWindow()

        val list = listView()!!
        scrollToTop(list)
        list.scrollBy(0, 1)
        harness.await("the previous page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }

        // The reader jumps to a different window while the first page is still on its way.
        loadWindow(fragment, WINDOW_2, WINDOW_2)
        harness.await("the new window to load") { firstFloor(fragment) == WINDOW_2_FLOOR_FIRST }

        release.countDown()
        settle()

        println("history-late: rows=${rowCount()} first=${firstFloor(fragment)}")
        assertEquals("the new window was not grown by a stale prepend", WINDOW_PAGE, rowCount())
        assertEquals("the new window is still the window the reader opened", WINDOW_2_FLOOR_FIRST, firstFloor(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The forward page carries the same guard: an append still in flight when the window is replaced
     * must not be applied to the new window.
     */
    @Test
    fun aLateForwardPageDoesNotAppendIntoAWindowOpenedWhileItWasInFlight() {
        serveResumeSite()
        val release = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            if (hold.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(postsResponse(requestedFloors(lastPostsQuery())))
        }
        val fragment = openResumeWindow()

        val list = listView()!!
        harness.layoutApp()
        list.scrollBy(0, list.height * 10)
        harness.await("the next page to be asked for") { site.requestPaths.contains("t/$TOPIC_ID/posts.json") }

        loadWindow(fragment, WINDOW_2, WINDOW_2)
        harness.await("the new window to load") { firstFloor(fragment) == WINDOW_2_FLOOR_FIRST }

        release.countDown()
        settle()

        println("history-late-forward: rows=${rowCount()} first=${firstFloor(fragment)}")
        assertEquals("the new window was not grown by a stale append", WINDOW_PAGE, rowCount())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A window read that lands after the reader has opened a different window must not overwrite it -
     * the same isolation the pages use, at the source: the head read here, a window opened over it.
     * A head answer that was overtaken must not send the screen after its floor again either.
     */
    @Test
    fun aLateHeadAnswerDoesNotOverwriteAWindowOpenedWhileItWasInFlight() {
        val release = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID.json") {
            if (hold.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(headJson(lastRead = RESUME_FLOOR))
        }
        site.serveJson("t/$TOPIC_ID/$WINDOW_1.json", windowJson(WINDOW_1))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val fragment = TopicFragment.forTopic(TOPIC_ID, "A long topic")
        harness.present(fragment)
        harness.await("the head read to be asked for") { site.requestPaths.contains("t/$TOPIC_ID.json") }

        // The reader is sent to a floor while the plain read is still on its way.
        loadWindow(fragment, WINDOW_1, WINDOW_1)
        harness.await("the window to load") { firstFloor(fragment) == WINDOW_FLOOR_FIRST }

        release.countDown()
        settle()

        // The window endpoint (t/{id}/{floor}.json) was read exactly once: the reader's own jump.
        // A head answer that was overtaken must not send the screen after its floor again.
        val windowReads = site.requestPaths.count { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) }
        println("history-late-head: rows=${rowCount()} first=${firstFloor(fragment)} windowReads=$windowReads")
        assertEquals("the late head answer does not re-read a window", 1, windowReads)
        assertEquals("the window is not replaced by the late head answer", WINDOW_PAGE, rowCount())
        assertEquals("the window the reader opened is still on screen", WINDOW_FLOOR_FIRST, firstFloor(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The discussion page is not a topic read: nearing its top must never page the topic's stream.
     */
    @Test
    fun aDiscussionPageNeverPagesTheTopicStream() {
        val parentId = idFor(PARENT_FLOOR)
        site.serveJson("t/$TOPIC_ID/$PARENT_FLOOR.json", detailJson(listOf(PARENT_FLOOR), lastRead = PARENT_FLOOR))
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query -> postsResponse(requestedFloors(query)) }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val thread = TopicFragment.forReplies(TOPIC_ID, "A topic", parentId, PARENT_FLOOR, 0)
        harness.present(thread)
        harness.await("the discussion root to draw") { rowCount() >= 1 }
        assertTrue("the fragment is the discussion page", thread.isThreadMode())

        val list = listView()!!
        harness.layoutApp()
        list.scrollBy(0, 1)
        list.scrollBy(0, -1)
        harness.idle()

        assertTrue(
            "a discussion page does not fetch the topic's stream: ${site.queriesFor("t/$TOPIC_ID/posts.json")}",
            site.queriesFor("t/$TOPIC_ID/posts.json").isEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ---- site plumbing -------------------------------------------------------

    private fun serveResumeSite() {
        site.serveJson("t/$TOPIC_ID.json", headJson(lastRead = RESUME_FLOOR))
        site.serveJson("t/$TOPIC_ID/$WINDOW_1.json", windowJson(WINDOW_1))
        site.serveJson("t/$TOPIC_ID/$WINDOW_2.json", windowJson(WINDOW_2))
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query -> postsResponse(requestedFloors(query)) }
    }

    /** Presents the topic and waits for the window the reader left off in. */
    private fun openResumeWindow(): TopicFragment {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A long topic"))
        harness.await("the resumed window to load") { rowCount() == WINDOW_PAGE }
        harness.layoutApp()
        return harness.visibleFragment() as TopicFragment
    }

    private fun scrollToTop(list: RecyclerListView) {
        list.scrollToPosition(0)
        harness.layoutApp()
        harness.idle()
    }

    /**
     * Pumps the main looper in real time for a while, so a response that was released can be parsed
     * and its callback delivered. A held response's own flag is set when the request is first seen,
     * not when the answer lands, so waiting on it would return before the answer exists.
     */
    private fun settle(iterations: Int = 400) {
        repeat(iterations) {
            harness.idle()
            harness.idleUpstreamQueues()
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
    }

    private fun loadWindow(fragment: TopicFragment, postNumber: Int, scrollTo: Int) {
        val method = TopicFragment::class.java.getDeclaredMethod(
            "loadWindow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(fragment, postNumber, scrollTo)
        harness.idle()
    }

    /** Drives the ordinary read's page-down jump the way its own tap does. */
    private fun loadPageDownWindow(fragment: TopicFragment, floor: Int) {
        val method = TopicFragment::class.java.getDeclaredMethod(
            "loadPageDownWindow",
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(fragment, floor)
        harness.idle()
    }

    /** The stream index of the first post the loaded window carries: how the backward cursor moves. */
    private fun windowStartOf(fragment: TopicFragment): Int = field(fragment, "windowStart") as Int

    // ---- payloads ------------------------------------------------------------

    /** A plain read of the head: the first page only, so the resumed floor needs a second read. */
    private fun headJson(lastRead: Int): String =
        detailJson((1..WINDOW_PAGE).toList(), lastRead = lastRead)

    /** The window the site answers when a floor is named: [WINDOW_PAGE] floors around it. */
    private fun windowJson(center: Int): String {
        val first = center - WINDOW_PAGE / 2 + 1
        return detailJson((first until first + WINDOW_PAGE).toList(), lastRead = center)
    }

    private fun detailJson(floors: List<Int>, lastRead: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("stream", JSONArray((1..TOTAL_FLOORS).map { idFor(it) }))
        stream.put("posts", JSONArray(floors.map { postJson(it) }))
        base.put("id", TOPIC_ID)
        base.put("title", "A long topic")
        base.put("posts_count", TOTAL_FLOORS)
        base.put("highest_post_number", TOTAL_FLOORS)
        base.put("last_read_post_number", lastRead)
        return base.toString()
    }

    private fun postsResponse(floors: List<Int>): String =
        JSONObject()
            .put("post_stream", JSONObject().put("posts", JSONArray(floors.map { postJson(it) })))
            .toString()

    private fun postJson(floor: Int): JSONObject =
        JSONObject().apply {
            put("id", idFor(floor))
            put("post_number", floor)
            put("username", "reader$floor")
            put("name", "Reader $floor")
            put("reply_count", 0)
            put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
            // Tall enough that a whole window does not fit on screen: the reader has somewhere to
            // scroll up to, so a backward prefetch is the thing that keeps a window from being the
            // end of the topic, and the forward prefetch is not spuriously reached.
            val filler = (1..6).joinToString("") { "<p>第 $floor 楼，正文第 $it 段，用来撑高这条回复。</p>" }
            // The window's top floor carries an upstream details block: the identity test opens it
            // through upstream's own handler and shows it survives an unrelated prepend.
            val details = if (floor == WINDOW_FLOOR_FIRST) {
                "<details><summary>展开更多</summary><p>楼下折叠的内容</p></details>"
            } else {
                ""
            }
            put("cooked", "<p>第 $floor 楼</p>$filler$details")
            put("avatar_template", "/user_avatar/linux.do/reader$floor/{size}.png")
            if (floor == REPLYING_FLOOR) {
                put("reply_to_post_number", REPLY_TARGET_FLOOR)
            }
        }

    private fun requestedFloors(query: String): List<Int> =
        Regex("post_ids%5B%5D=(\\d+)").findAll(query)
            .map { floorForId(it.groupValues[1].toLong()) }
            .toList()

    private fun lastPostsQuery(): String =
        site.queriesFor("t/$TOPIC_ID/posts.json").last()

    private fun idFor(floor: Int): Long = ID_BASE + floor.toLong() * ID_STEP

    private fun floorForId(id: Long): Int = ((id - ID_BASE) / ID_STEP).toInt()

    // ---- fragment internals --------------------------------------------------

    private fun firstFloor(fragment: TopicFragment): Int? = floorsOf(fragment).firstOrNull()?.postNumber

    @Suppress("UNCHECKED_CAST")
    private fun bubblesOf(fragment: TopicFragment): List<MessageObject> =
        field(fragment, "bubbles") as List<MessageObject>

    @Suppress("UNCHECKED_CAST")
    private fun floorsOf(fragment: TopicFragment): List<io.github.baba983.nativedo.core.model.Post> =
        field(fragment, "floors") as List<io.github.baba983.nativedo.core.model.Post>

    @Suppress("UNCHECKED_CAST")
    private fun bubbleFloorsOf(fragment: TopicFragment): List<Int> =
        field(fragment, "bubbleFloors") as List<Int>

    @Suppress("UNCHECKED_CAST")
    private fun bubbleBodiesOf(fragment: TopicFragment): List<Any?> =
        field(fragment, "bubbleBodies") as List<Any?>

    @Suppress("UNCHECKED_CAST")
    private fun replyTargetFloorsOf(fragment: TopicFragment): Map<MessageObject, Int> =
        field(fragment, "replyTargetFloors") as Map<MessageObject, Int>

    @Suppress("UNCHECKED_CAST")
    private fun avatarTemplatesOf(fragment: TopicFragment): Map<Long, String> =
        field(fragment, "avatarTemplates") as Map<Long, String>

    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    /** Draws the screen as it stands to a retained artifact under [artifacts]. */
    private fun writeVisual(name: String) {
        val view = harness.activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val out = java.io.File(artifacts, "$name.png")
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("history render: $out")
    }

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    /**
     * Drives a rich details block the way upstream does: it is not a view, so the layout hit-tests a
     * touch and hands it to the block's own handler. The delegate upstream sets before the dispatch
     * is set here too, then the block gets the down/up its handler reads - a real toggle (flag flips,
     * arrow animates, the cell rebinds), not a flag set by hand.
     */
    private fun clickDetails(cell: ChatMessageCell, block: org.telegram.messenger.RichMessageLayout.RichDetailsBlock) {
        val delegateField = ChatMessageCell::class.java.getDeclaredField("delegate").apply { isAccessible = true }
        val delegate = delegateField.get(cell) as? ChatMessageCell.ChatMessageCellDelegate
        cell.getMessageObject().richLayout.setChatMessageCellDelegate(cell, delegate)
        val t = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val up = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_UP, 0f, 0f, 0)
        block.onTouchEvent(down)
        block.onTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 424242L
        const val TOTAL_FLOORS = 70
        /** One page of the fake site's window, and the page size the client is expected to fetch. */
        const val WINDOW_PAGE = 20
        /** Where the reader left off; the head does not carry this floor, so it is read as a window. */
        const val RESUME_FLOOR = 45
        /** The first window the reader is dropped into, and the first floor it carries. */
        const val WINDOW_1 = 45
        const val WINDOW_FLOOR_FIRST = WINDOW_1 - WINDOW_PAGE / 2 + 1
        /** A second window, reached while a page for the first is still in flight. */
        const val WINDOW_2 = 60
        const val WINDOW_2_FLOOR_FIRST = WINDOW_2 - WINDOW_PAGE / 2 + 1
        /** A floor in [WINDOW_1] answers a floor the previous slice brings in (16..35). */
        const val REPLYING_FLOOR = 45
        const val REPLY_TARGET_FLOOR = 20
        /** The discussion page's root. */
        const val PARENT_FLOOR = 12
        /** A floor below the loaded window, read as a page-down jump (which the test fails). */
        const val JUMP_FLOOR = 60
        const val JUMP_PATH = "t/$TOPIC_ID/$JUMP_FLOOR.json"
        /** Ids are deliberately far from floors and from stream positions. */
        const val ID_BASE = 700_000L
        const val ID_STEP = 131L
    }
}
