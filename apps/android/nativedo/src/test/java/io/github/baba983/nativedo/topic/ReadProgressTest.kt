package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    @get:org.junit.Rule
    val testName = org.junit.rules.TestName()

    @Before
    fun setUp() {
        site.start()
        println("read-test-start: ${testName.methodName}")
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

        // The reader sits with the first floor on screen for a while: the dwell that reaches the
        // site has to be that time, not a nominal second per floor.
        harness.idleClock(3_000)

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
            "every floor the reader reached is reported",
            (1..TOPIC_FLOORS).toList(),
            reports.flatMap { fields -> fields.keys }
                .filter { key -> key.startsWith("timings[") }
                .map { key -> key.removePrefix("timings[").removeSuffix("]").toInt() }
                .distinct()
                .sorted(),
        )
        assertTrue(
            "a report names a positive dwell for each floor, and never more than the site takes",
            reports.flatMap { fields -> fields.filterKeys { key -> key.startsWith("timings[") }.values }
                .all { dwell -> dwell.toInt() in 1..60_000 },
        )
        assertTrue(
            "and the topic's own dwell is the reader's real time, not a second per floor",
            reports.sumOf { fields -> fields["topic_time"]!!.toInt() } >= 3_000,
        )
        assertEquals(
            "with the token the site asks its writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("topics/timings"),
        )
        harness.layoutApp()
        harness.renderApp("read-progress-topic")

        // The reader goes back to the list. A return is not a refresh: the feed's head is not read
        // again, and the topic they have read in full drops its badge on the row that is already
        // there - the reader's own read-mark, taken as the topic screen closes, is what the row is
        // bound from.
        val headReadsBefore = site.queriesFor("latest.json").count { it.contains("page=0") }
        leaveTheTopic()
        harness.idleClock(600)
        harness.layoutApp()
        harness.drawApp()
        assertEquals(
            "the return did not read the feed's head again",
            headReadsBefore,
            site.queriesFor("latest.json").count { it.contains("page=0") },
        )
        assertEquals(
            "the topic the reader finished is not unread any more",
            0,
            rowUnread(0),
        )
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
     * The dwell has to be the reader's own time. A client that reports a fixed amount makes the
     * site's own reading statistics - and the trust levels built on them - disagree with what its
     * readers did, and one that keeps counting while the app is in the background does the same in
     * the other direction. The clock here is the simulated one, so the test can watch five seconds
     * pass without waiting five seconds.
     */
    @Test
    fun theDwellIsTheReadersOwnForegroundTime() {
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveJson("topics/timings", "{}")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        openFirstTopic()
        harness.await("the topic to load") { drawnFloors().isNotEmpty() }
        // The floors are only on screen once the list has been through a layout, and only the
        // floors on screen are being read.
        harness.layoutApp()
        harness.idle()
        val floorsOnScreen = drawnFloors()
        println("dwell: floors on opening = $floorsOnScreen")

        // Opening the topic schedules a report. Pause and wait for its response before taking the
        // measurement baseline, so no opening report can be mistaken for the ten-second window.
        harness.await("the floors it opened at to be reported") {
            site.requestBodiesFor("topics/timings").isNotEmpty()
        }
        pauseApp()
        awaitNetwork("the opening reports to finish while paused") { topicReporter().hasSettled() }
        // Pause owes no additional POST if the opening report already consumed all the dwell.
        val beforeWindow = site.requestBodiesFor("topics/timings").size
        resumeApp()

        harness.idleClock(10_000)
        pauseApp()
        val first = awaitReport(beforeWindow + 1).let(::fieldsOf)
        awaitNetwork("the first measured report to finish") { topicReporter().hasSettled() }
        println("dwell: after 10s in front, the site was told $first")
        val firstTopicTime = first["topic_time"]!!.toInt()
        assertTrue(
            "ten seconds in front are reported as about ten seconds, not a nominal slice",
            firstTopicTime in 9_000..12_000,
        )
        assertTrue(
            "and the floor on screen carries the same time",
            floorsOnScreen.all { floor ->
                (first["timings[$floor]"]?.toInt() ?: 0) in 9_000..12_000
            },
        )

        // Ten seconds in the background must not count; ten more seconds in front must.
        val beforeHidden = site.requestBodiesFor("topics/timings").size
        harness.idleClock(10_000)
        assertEquals(
            "a screen in the background reports nothing while it is away",
            beforeHidden,
            site.requestBodiesFor("topics/timings").size,
        )
        resumeApp()
        harness.idleClock(10_000)
        pauseApp()
        val second = awaitReport(beforeHidden + 1).let(::fieldsOf)
        println("dwell: after 10s hidden and 10s back, the site was told $second")
        val secondTopicTime = second["topic_time"]!!.toInt()
        assertTrue(
            "only the ten seconds back in front are reported",
            secondTopicTime in 9_000..12_000,
        )
        assertTrue(
            "the time in the background is not among them",
            secondTopicTime < 15_000,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Waits for the [count]-th timing report to have reached the site, and returns its body. */
    private fun awaitReport(count: Int): String {
        awaitWithClock("timing report $count") { site.requestBodiesFor("topics/timings").size >= count }
        return site.requestBodiesFor("topics/timings")[count - 1]
    }

    /** The app going to the background, the way the platform tells the activity. */
    private fun pauseApp() {
        lifecycle("onPause")
    }

    private fun resumeApp() {
        lifecycle("onResume")
    }

    private fun lifecycle(name: String) {
        val method = harness.activity.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(harness.activity)
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

    /** The topic screen's own reporter, so a test can drive the recovery event it answers to. */
    private fun topicReporter(): io.github.baba983.nativedo.feature.topic.data.TopicReadReporter {
        val fragment = fragments().filterIsInstance<TopicFragment>().lastOrNull()
            ?: throw AssertionError("no topic screen is open")
        val field = TopicFragment::class.java.getDeclaredField("reporter").apply { isAccessible = true }
        return field.get(fragment) as io.github.baba983.nativedo.feature.topic.data.TopicReadReporter
    }

    /** The floors the topic screen has drawn, as the post numbers behind its bubbles. */
    /**
     * The floors the viewport holds, by the list's own visible range - the same range the reporter
     * is told about. A cell can be attached and VISIBLE while it sits in the RecyclerView's extra
     * layout space below the fold, so walking every child would count a floor the reader cannot see.
     */
    private fun drawnFloors(): List<Int> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        val manager = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return emptyList()
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first < 0 || last < first) return emptyList()
        return (first..last).mapNotNull { position ->
            val cell = manager.findViewByPosition(position) as? ChatMessageCell ?: return@mapNotNull null
            val id = cell.messageObject?.messageOwner?.id ?: return@mapNotNull null
            (id - POST_ID_BASE).toInt().takeIf { it in 1..1_000 }
        }
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

    /** The tab host's topic list, which is the page the shell is showing while no topic is open. */
    private fun topicList(): TopicListFragment? = harness.visibleFragment() as? TopicListFragment

    /** The unread count the list's row at [position] was last bound with. */
    private fun rowUnread(position: Int): Int {
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list to read a row from")
        val cell = list.findViewHolderForAdapterPosition(position)?.itemView
            ?: throw AssertionError("the list drew no row at $position")
        val dialog = org.telegram.ui.Cells.DialogCell::class.java
            .getDeclaredField("customDialog").apply { isAccessible = true }
            .get(cell) as org.telegram.ui.Cells.DialogCell.CustomDialog
        return dialog.unread_count
    }

    private fun fragments(): List<Any> {
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.fragmentStack.toList()
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

    /**
     * A read-progress report the edge challenged. The report is a write, and a challenge is not
     * something a timer can solve: one attempt, then the automatic reports end. The old ticker
     * re-armed every second against a stale `lastFlushAt`, so a challenged report went out again and
     * again until the reader left the topic.
     */
    @Test
    fun aChallengeOnAReadProgressReportEndsTheAutomaticReportsInsteadOfRetrying() {
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveResponse("topics/timings") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head></html>")
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        openFirstTopic()
        harness.await("the topic to load") { drawnFloors().isNotEmpty() }
        harness.idleClock(3_000)
        scrollTopicToTheEnd()

        awaitWithClock("the challenged report to be attempted") {
            site.recordedRequests.any { it.startsWith("POST /topics/timings") }
        }
        awaitNetwork("the challenge to block the reporter") { topicReporter().hasSettled() }
        val attempts = site.recordedRequests.count { it.startsWith("POST /topics/timings") }
        // A scroll, a flush and leaving/returning to the screen must none of them re-arm it.
        topicReporter().flush()
        scrollTopicToTheEnd()
        lifecycle("onPause")
        lifecycle("onResume")
        harness.idleClock(70_000)
        harness.idle()
        java.util.concurrent.locks.LockSupport.parkNanos(300_000_000L)
        harness.idleClock(5_000)
        val after = site.recordedRequests.count { it.startsWith("POST /topics/timings") }
        println("read-progress-challenge: attempts=$attempts after=$after")
        assertEquals("the report was attempted once", 1, attempts)
        assertEquals("a challenged report is attempted once, not retried every tick", attempts, after)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A challenged reporter is released only by a confirmed recovery: a resume is not one, and the
     * recovery event the session layer raises is.
     */
    @Test
    fun aChallengedReporterIsReArmedOnlyByAConfirmedRecovery() {
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveResponse("topics/timings") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head></html>")
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        openFirstTopic()
        harness.await("the topic to load") { drawnFloors().isNotEmpty() }
        harness.idleClock(3_000)
        scrollTopicToTheEnd()
        awaitWithClock("the challenged report to be attempted") {
            site.recordedRequests.any { it.startsWith("POST /topics/timings") }
        }
        // Let the failed report finish before the recovery, so the recovery is not racing it: the
        // point of this case is that only the recovery event re-arms, not that a recovery in flight
        // is honored (that race is the reporter's own and has its own handling).
        harness.await("the challenged report to be processed") {
            topicReporter().hasSettled()
        }
        val before = site.recordedRequests.count { it.startsWith("POST /topics/timings") }

        lifecycle("onPause")
        lifecycle("onResume")
        harness.idleClock(5_000)
        harness.idle()
        assertEquals(
            "coming back to the screen does not release a challenged reporter",
            before,
            site.recordedRequests.count { it.startsWith("POST /topics/timings") },
        )

        topicReporter().onSessionRecovered()
        awaitNetwork("a confirmed recovery to send and settle the next report") {
            site.recordedRequests.count { it.startsWith("POST /topics/timings") } > before &&
                topicReporter().hasSettled()
        }
        assertEquals(
            "a confirmed recovery releases one attempt; another challenge blocks it again",
            before + 1,
            site.recordedRequests.count { it.startsWith("POST /topics/timings") },
        )
        println("read-progress-recovered: clocks=${site.requestClocksFor("POST", "topics/timings")}")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRecoveryWhileAChallengedReportIsInFlightIsNotLostOnCompletion() {
        recoverWhileReportIsInFlight(403)
    }

    @Test
    fun aRecoveryWhileATransientFailureIsInFlightDoesNotLeaveAStaleRecovery() {
        recoverWhileReportIsInFlight(500)
    }

    @Test
    fun aRecoveryWhileASuccessfulReportIsInFlightDoesNotReleaseANewerChallenge() {
        recoverWhileReportIsInFlight(200)
    }

    /**
     * Holds the first response until recovery, not until a guessed sleep elapses. A stale failure
     * must release a new attempt after the runner is released. A successful first report consumes
     * the recovery before its next batch, so that batch's challenge is new evidence and must block.
     */
    private fun recoverWhileReportIsInFlight(firstStatus: Int) {
        val received = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveResponse("topics/timings") {
            val status = if (attempts.incrementAndGet() == 1) {
                received.countDown()
                check(release.await(4, java.util.concurrent.TimeUnit.SECONDS)) { "response was never released" }
                firstStatus
            } else 403
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", if (status == 403) "text/html" else "application/json")
                .setBody(if (status == 403) "<html><head><title>Just a moment...</title></head></html>" else "{}")
        }

        try {
            harness.bootApp()
            harness.await("the topic list to load") { rowCount() == 1 }
            openFirstTopic()
            harness.await("the first report to be held at the site") { received.count == 0L }
            assertTrue("the held response is still in flight", !topicReporter().hasSettled())
            // The first request carried the opening floors. This adds debt for the next batch.
            scrollTopicToTheEnd()
            topicReporter().onSessionRecovered()
            release.countDown()
            awaitNetwork("the in-flight recovery to send and settle the next report") {
                attempts.get() >= 2 && topicReporter().hasSettled()
            }
            assertEquals("the new challenge ends the reports again", 2, attempts.get())
            topicReporter().flush()
            lifecycle("onPause")
            lifecycle("onResume")
            harness.idleClock(70_000)
            harness.idle()
            assertEquals("no pending recovery leaks past the newer challenge", 2, attempts.get())
            println("read-progress-in-flight: firstStatus=$firstStatus clocks=${site.requestClocksFor("POST", "topics/timings")}")
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * Pumps the response callbacks between network steps within the same 300 ms real-time budget
     * the old test slept for. One sleep followed by one drain can start CSRF's POST only at the
     * assertion, leaving the test to race that request instead of observing its completion.
     */
    private fun awaitNetwork(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 300_000_000L
        do {
            harness.idle()
            if (condition()) return
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        } while (System.nanoTime() < deadline)
        throw AssertionError("timed out waiting for $what")
    }

    /**
     * A read-progress report that broke in transit. Unlike a challenge this is worth another try,
     * but not on every tick: it is waited out, and the backoff is not forever.
     */
    @Test
    fun aReadProgressReportThatBrokeInTransitIsWaitedOutNotRetriedEveryTick() {
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveResponse("topics/timings") {
            okhttp3.mockwebserver.MockResponse().setResponseCode(500).setBody("{}")
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        openFirstTopic()
        harness.await("the topic to load") { drawnFloors().isNotEmpty() }
        harness.idleClock(3_000)
        scrollTopicToTheEnd()

        awaitWithClock("the broken report to be attempted") {
            site.recordedRequests.any { it.startsWith("POST /topics/timings") }
        }
        awaitNetwork("the broken report to establish its backoff") { topicReporter().hasSettled() }
        // Leaving and returning must not skip the wait. The injected-clock case below also pins the
        // exact deadline; here the screen resumes only after the error has established that wait.
        lifecycle("onPause")
        lifecycle("onResume")
        // The wait starts at failure completion, not at request receipt. Advance past it only after
        // the error has settled, then pump both CSRF and POST callbacks rather than racing the POST.
        harness.idleClock(70_000)
        awaitNetwork("the waited-out retry to finish") {
            site.requestBodiesFor("topics/timings").size >= 2 && topicReporter().hasSettled()
        }

        // The clock the screen's timers run on, as each attempt reached the site. Robolectric's idle
        // advances that clock through the reporter's own wait, so the count alone says nothing; the
        // gaps are what tell a waited-out retry from one that went out on a tick.
        val clocks = site.requestClocksFor("POST", "topics/timings")
        val gaps = clocks.zipWithNext { earlier, later -> later - earlier }
        println("read-progress-transient: clocks=$clocks gaps=$gaps")
        assertTrue("the report was attempted more than once", clocks.size >= 2)
        assertTrue(
            "every retry waited the backoff out, not a tick: $gaps",
            gaps.all { gap -> gap >= 60_000 },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Pins both sides of the deadline through the real repository, Retrofit and cookie interceptor.
     * The UI clock also advances on worker drains, so it cannot place a settled failure's resume at
     * an exact instant. The reporter already accepts its own clock; no response or model is mocked.
     */
    @Test
    fun aSettledTransientFailureKeepsItsDeadlineAcrossResumeAndFlush() {
        site.serveJsonFor("latest.json") { latestFeedWithUnread(TOPIC_FLOORS, TOPIC_FLOORS) }
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(floor = 1, total = TOPIC_FLOORS, lastRead = 0) }
        site.serveResponse("topics/timings") {
            okhttp3.mockwebserver.MockResponse().setResponseCode(500).setBody("{}")
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 1 }
        val container = NativeDo.container(harness.context)
        val repository = io.github.baba983.nativedo.feature.topic.data.TopicRepository(container.discourseApiClient)
        val detail = kotlinx.coroutines.runBlocking { repository.load(TOPIC_ID).getOrThrow() }
        val job = kotlinx.coroutines.SupervisorJob()
        val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.Main.immediate)
        var now = 100_000L
        val reporter = io.github.baba983.nativedo.feature.topic.data.TopicReadReporter(
            topicId = TOPIC_ID,
            repository = repository,
            store = container.topicReadPositionStore,
            key = null,
            scope = scope,
            clock = { now },
        )
        fun attempts(): Int = site.requestBodiesFor("topics/timings").size
        try {
            reporter.start(detail)
            reporter.setForeground(true)
            reporter.visibleFloors(listOf(1, 2, 3))
            reporter.advanceTo(3)
            // This recovery precedes the debounced request, so its later failure is not stale.
            reporter.onSessionRecovered()
            harness.idleClock(300)
            awaitNetwork("the first failure to settle at the fixed clock") {
                attempts() == 1 && reporter.hasSettled()
            }
            reporter.setForeground(false)
            now += 59_999L
            reporter.setForeground(true)
            reporter.advanceTo(6)
            reporter.flush()
            harness.idleClock(500)
            awaitNetwork("the resumed reporter to settle before its deadline") { reporter.hasSettled() }
            assertEquals("resume, scroll and flush cannot skip the remaining millisecond", 1, attempts())
            now += 1L
            reporter.flush()
            awaitNetwork("the report at the deadline to finish") { attempts() >= 2 && reporter.hasSettled() }
            assertEquals("one retry is released at the deadline", 2, attempts())
            val retry = fieldsOf(site.requestBodiesFor("topics/timings").last())
            assertTrue("the failed report's floors are still owed alongside the newly reached ones",
                (1..6).all { retry.containsKey("timings[$it]") })
            println("read-progress-deadline: first=100000 resume=159999 retry=$now attempts=${attempts()}")
            harness.assertNoTelegramSideEffects()
        } finally {
            job.cancel()
        }
    }

    private fun rowCount(): Int = contentRowCount(harness)

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
