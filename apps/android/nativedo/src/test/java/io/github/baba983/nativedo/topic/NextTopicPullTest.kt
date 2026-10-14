package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reading on to the next topic from the end of a topic, driven through the production wiring.
 *
 * <p>Once the reader has the whole real stream loaded and the list is at its end, an upward drag
 * past 110dp reveals the site's own suggestion for what to read next and opens it on release; under
 * the threshold, and on a cancel, the pull only rebounds. The candidate is the first valid,
 * non-current entry of the read's `suggested_topics`, in the site's own order. The gesture is a real
 * `MotionEvent` stream on the shipped list, the candidate comes from the site's payload, and the
 * assertions are on the screen the shell ends up showing and on what the fake site was asked for.</p>
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
internal class NextTopicPullTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private val artifacts: java.io.File =
        java.io.File(repoRoot(), ".amp/in/artifacts/topic-search-pull/next-topic/harness")

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
        site.stop()
    }

    // ---- contracts -----------------------------------------------------------

    @Test
    fun aReleasePastTheThresholdOpensTheSiteSuggestionAndBackReturnsToTheList() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        val stackBefore = harness.fragmentStack().size
        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: peak offset $peak (threshold $PULL_THRESHOLD_PX px)")
        assertTrue("the pull grew past the threshold (peak=$peak)", peak > PULL_THRESHOLD_PX)

        harness.idleClock(700)
        harness.await("the suggested topic to be read") {
            site.requestPaths.contains("t/$NEXT_TOPIC_ID.json")
        }

        assertEquals(
            "only one topic is opened: ${site.requestPaths.filter { it.startsWith("t/") }}",
            1,
            site.requestPaths.count { it == "t/$NEXT_TOPIC_ID.json" },
        )
        assertEquals("the opened screen is the site's first valid suggestion", NEXT_TOPIC_ID, topicIdOnTop())
        assertEquals(
            "continuous reading did not stack a screen (before=$stackBefore)",
            stackBefore,
            harness.fragmentStack().size,
        )

        back()
        assertTrue(
            "Back returns to the list the reader came from, not to another topic",
            harness.topFragment() !is TopicFragment,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aPullUnderTheThresholdReboundsAndStaysOnTheTopic() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        // A finger drag well under the threshold: with the reference's damping this is ~30dp of pull.
        val peak = dragUp(fingerPx = 220f, release = MotionEvent.ACTION_UP)
        println("next-topic: under-threshold peak $peak")
        assertTrue("the pull engaged (peak=$peak)", peak > 0f)
        assertTrue("but never reached the threshold (peak=$peak)", peak < PULL_THRESHOLD_PX)

        harness.idleClock(500)
        assertEquals("the pull rebound to rest", 0f, pullingOffset(), 0.5f)
        assertEquals("the reader stayed on the topic", TOPIC_ID, topicIdOnTop())
        assertTrue(
            "no topic was opened: ${site.requestPaths.filter { it.startsWith("t/") }}",
            site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anActionCancelPastTheThresholdReboundsAndStaysOnTheTopic() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_CANCEL)
        println("next-topic: cancelled peak $peak")
        assertTrue("the pull grew past the threshold (peak=$peak)", peak > PULL_THRESHOLD_PX)

        harness.idleClock(600)
        assertEquals("a cancel only puts the pull back", 0f, pullingOffset(), 0.5f)
        assertEquals("and never navigates", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun draggingBackGivesThePullBackBeforeStayingPut() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        // Pull up past the threshold, then give it all back with a reverse drag: the pull shrinks
        // ahead of the list, and the release is under the threshold, so nothing opens.
        val list = postList()
        val x = list.width / 2f
        val startY = list.height * 0.7f
        val downTime = android.os.SystemClock.uptimeMillis()
        var time = downTime
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_DOWN, x, startY, 0))
        var peak = 0f
        var y = startY
        for (i in 1..24) {
            time = downTime + i * 16L
            y = startY - 60f * i
            list.dispatchTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, x, y, 0))
            peak = maxOf(peak, pullingOffset())
        }
        val peakAtTop = pullingOffset()
        println("next-topic: reverse peak $peakAtTop")
        assertTrue("the pull grew past the threshold before the reverse (peak=$peak)", peak > PULL_THRESHOLD_PX)

        val lastY = y
        var lowest = peakAtTop
        for (i in 1..24) {
            time = downTime + (24 + i) * 16L
            y = lastY + 60f * i
            list.dispatchTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, x, y, 0))
            lowest = minOf(lowest, pullingOffset())
        }
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time + 16, MotionEvent.ACTION_UP, x, y, 0))
        println("next-topic: after reverse offset $lowest -> ${pullingOffset()}")
        assertTrue("the reverse gave the pull back ($peakAtTop -> $lowest)", lowest < peakAtTop)

        harness.idleClock(500)
        assertEquals("the pull is back at rest", 0f, pullingOffset(), 0.5f)
        assertEquals("a reverse drag never opens the next topic", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aPageStillComingAtTheEndKeepsThePullFromNavigating() {
        serveLongTopicWithSuggestions()
        serveNextTopic()
        val release = CountDownLatch(1)
        // The page below the loaded window is on its way for the whole gesture: the stream is not
        // finished, so the pull must not be offered while the read is in flight.
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            release.await(20, TimeUnit.SECONDS)
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(postsResponse(FLOORS.drop(WINDOW)))
        }

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW }
        harness.layoutApp()
        harness.idleClock(300)
        scrollToTheEnd()
        harness.await("the page below to be asked for") {
            site.queriesFor("t/$TOPIC_ID/posts.json").isNotEmpty()
        }

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: page-in-flight peak $peak")
        assertEquals("a page still coming keeps the pull away", 0f, peak, 0.5f)
        harness.idleClock(400)
        release.countDown()
        harness.idleClock(400)
        assertEquals("and never opens the suggested topic", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aFailedPageAtTheEndKeepsTheGestureFromNavigating() {
        serveLongTopicWithSuggestions()
        serveNextTopic()
        site.serveResponse("t/$TOPIC_ID/posts.json") {
            MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"errors\":[\"fixture page unavailable\"]}")
        }

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW }
        harness.layoutApp()
        harness.idleClock(300)
        scrollToTheEnd()
        harness.await("the failed page to be latched") {
            site.queriesFor("t/$TOPIC_ID/posts.json").isNotEmpty()
        }
        harness.idleClock(300)

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: failed-page peak $peak")
        assertEquals("a failed page keeps the pull away", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and never opens the suggested topic", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aComposerDraftKeepsThePullAway() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()
        composer().setFieldText("half-written reply")
        harness.idleClock(200)

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: draft peak $peak")
        assertEquals("a draft in the composer keeps the pull away", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and never opens the suggested topic", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theInTopicSearchKeepsThePullAway() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()
        openTopicSearch()
        assertTrue("the in-topic search is open", searchFieldShown())

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: search peak $peak")
        assertEquals("the in-topic search keeps the pull away", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and never opens the suggested topic", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aTopicTheSiteSuggestedNothingForHasNoPull() {
        site.serveJson("t/$TOPIC_ID.json", detailJson(FLOORS, JSONArray(), lastRead = HIGHEST))
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: no-candidate peak $peak")
        assertEquals("no suggestion means no pull", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and nothing opens", TOPIC_ID, topicIdOnTop())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theCardIsDrawnWhileThePullIsUp() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        val list = postList()
        val x = list.width / 2f
        val startY = list.height * 0.7f
        val downTime = android.os.SystemClock.uptimeMillis()
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY, 0))
        var time = downTime
        for (i in 1..24) {
            time = downTime + i * 16L
            list.dispatchTouchEvent(
                MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, x, startY - 60f * i, 0),
            )
        }
        val offset = pullingOffset()
        println("next-topic: card offset $offset")
        assertTrue("the pull is held past the threshold while drawn", offset > PULL_THRESHOLD_PX)
        writeVisual("next-topic-pull-card")
        list.dispatchTouchEvent(
            MotionEvent.obtain(downTime, time + 16, MotionEvent.ACTION_CANCEL, x, startY - 1440f, 0),
        )
        harness.idleClock(400)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSecondReleaseWhileTheFirstIsOpeningOpensOnlyOneTopic() {
        serveWholeTopicWithSuggestions()
        serveNextTopic()

        val fragment = openTopic()
        awaitWholeStream(fragment)
        settleAtTheEnd()

        val list = postList()
        val x = list.width / 2f
        val startY = list.height * 0.7f
        val downTime = android.os.SystemClock.uptimeMillis()
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY, 0))
        var time = downTime
        for (i in 1..24) {
            time = downTime + i * 16L
            list.dispatchTouchEvent(
                MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, x, startY - 60f * i, 0),
            )
        }
        assertTrue("the pull is past the threshold", pullingOffset() > PULL_THRESHOLD_PX)
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time + 16, MotionEvent.ACTION_UP, x, startY - 1440f, 0))
        // A second let-go before the first has opened anything must not open a second screen.
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time + 32, MotionEvent.ACTION_UP, x, startY - 1440f, 0))

        harness.idleClock(900)
        harness.await("the suggested topic to be read") {
            site.requestPaths.contains("t/$NEXT_TOPIC_ID.json")
        }
        harness.idleClock(300)
        assertEquals(
            "only one topic is opened by a doubled release: ${site.requestPaths.filter { it.startsWith("t/") }}",
            1,
            site.requestPaths.count { it == "t/$NEXT_TOPIC_ID.json" },
        )
        assertEquals("no second screen is stacked", 2, harness.fragmentStack().size)
        harness.assertNoTelegramSideEffects()
    }

    // ---- a long topic takes its suggestion from the last slice ---------------

    /**
     * A head window is only part of a long stream and - because a next page follows - carries no
     * `suggested_topics` at all (Discourse closes `include_suggested` there). The suggestion only
     * comes with the final slice, and only when the client asks for it. Until that slice lands
     * there is nothing to read on to; once it does, an ordinary over-threshold release navigates.
     */
    @Test
    fun aLongTopicTakesItsSuggestionFromItsFinalStreamPage() {
        serveLongTopicWithoutSuggestions()
        serveNextTopic()
        val release = CountDownLatch(1)
        serveFinalSlice(validSuggestionJson(), release)

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW }
        harness.layoutApp()
        harness.idleClock(300)
        scrollToTheEnd()
        harness.await("the final slice to be asked for") {
            site.queriesFor("t/$TOPIC_ID/posts.json").isNotEmpty()
        }

        // The head window is only part of the stream, carries no suggestion of its own, and the
        // final slice is still on its way: there is nothing to read on to yet.
        val approach = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: part-stream peak $approach")
        assertEquals("a part-stream with no suggestion has no pull", 0f, approach, 0.5f)
        assertTrue(
            "no topic was opened before the final slice: ${site.requestPaths.filter { it.startsWith("t/") }}",
            site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" },
        )

        // The final slice lands, carrying the site's suggestion because the request asked for it.
        release.countDown()
        harness.await("the whole stream") { floorsOf(fragment).size == FLOORS.size }
        harness.idleClock(400)
        settleAtTheEnd()
        assertTrue(
            "the final slice asked the site for its suggestion: ${site.queriesFor("t/$TOPIC_ID/posts.json")}",
            site.queriesFor("t/$TOPIC_ID/posts.json").any { it.contains("include_suggested=true") },
        )

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: final-slice peak $peak (threshold $PULL_THRESHOLD_PX px)")
        assertTrue("the pull grew past the threshold (peak=$peak)", peak > PULL_THRESHOLD_PX)

        harness.idleClock(700)
        harness.await("the suggested topic to be read") {
            site.requestPaths.contains("t/$NEXT_TOPIC_ID.json")
        }
        assertEquals("the opened screen is the site's first valid suggestion", NEXT_TOPIC_ID, topicIdOnTop())
        assertEquals(
            "only one topic is opened: ${site.requestPaths.filter { it.startsWith("t/") }}",
            1,
            site.requestPaths.count { it == "t/$NEXT_TOPIC_ID.json" },
        )
        back()
        assertTrue(
            "Back returns to the list the reader came from, not to another topic",
            harness.topFragment() !is TopicFragment,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** The final slice is asked for the site's suggestion and the site carries none: no pull. */
    @Test
    fun aFinalSliceWithoutASuggestionLeavesThePullAway() {
        serveLongTopicWithoutSuggestions()
        serveNextTopic()
        serveFinalSlice(suggestions = null, release = null)

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW }
        harness.layoutApp()
        harness.idleClock(300)
        scrollToTheEnd()
        harness.await("the whole stream") { floorsOf(fragment).size == FLOORS.size }
        settleAtTheEnd()
        assertTrue(
            "the final slice was still asked with the suggestion flag",
            site.queriesFor("t/$TOPIC_ID/posts.json").any { it.contains("include_suggested=true") },
        )

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: final-slice-without-suggestion peak $peak")
        assertEquals("a final slice without a suggestion leaves no pull", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and nothing opens", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A final slice that lands after the reader has jumped to another window belongs to the window
     * that asked for it. Its suggestion must not arm the replacement window's pull, even though the
     * replacement window itself ends the stream and would allow the pull if it had a candidate.
     */
    @Test
    fun aFinalSliceForAReplacedWindowDoesNotArmThePull() {
        serveLongTopicWithoutSuggestions()
        serveNextTopic()
        // The window the reader jumps to ends the stream, so only a missing candidate keeps the
        // pull away - and the stale slice is the only thing that names one.
        site.serveJson(
            "t/$TOPIC_ID/$REPLACEMENT_FLOOR.json",
            detailJson(FLOORS.takeLast(WINDOW), null, lastRead = 0),
        )
        val release = CountDownLatch(1)
        serveFinalSlice(validSuggestionJson(), release)

        val fragment = openTopic()
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW }
        harness.layoutApp()
        harness.idleClock(300)
        scrollToTheEnd()
        harness.await("the final slice to be asked for") {
            site.queriesFor("t/$TOPIC_ID/posts.json").isNotEmpty()
        }

        // The reader jumps to the end of the stream before the stale slice lands.
        loadWindow(fragment, REPLACEMENT_FLOOR, REPLACEMENT_FLOOR)
        harness.await("the replacement window") {
            floorsOf(fragment).lastOrNull()?.postNumber == HIGHEST
        }
        scrollToTheEnd()
        release.countDown()
        harness.idleClock(600)

        val peak = dragUp(fingerPx = 1400f, release = MotionEvent.ACTION_UP)
        println("next-topic: stale-slice peak $peak")
        assertEquals("a stale final slice does not arm the pull", 0f, peak, 0.5f)
        harness.idleClock(600)
        assertEquals("and nothing opens", TOPIC_ID, topicIdOnTop())
        assertTrue("no topic was opened", site.requestPaths.none { it == "t/$NEXT_TOPIC_ID.json" })
        harness.assertNoTelegramSideEffects()
    }

    // ---- opening and driving ------------------------------------------------

    private fun serveWholeTopicWithSuggestions() {
        site.serveJson("t/$TOPIC_ID.json", detailJson(FLOORS, validSuggestionJson(), lastRead = HIGHEST))
    }

    /** The site's ranked list: the current topic first, then an invalid id, then the real ones. */
    private fun validSuggestionJson(): JSONArray = JSONArray().apply {
        put(JSONObject().put("id", TOPIC_ID).put("title", "本话题，应当跳过"))
        put(JSONObject().put("id", 0).put("title", "无效条目"))
        put(JSONObject().put("id", NEXT_TOPIC_ID).put("title", "推荐的下一个话题"))
        put(JSONObject().put("id", NEXT_TOPIC_ID + 1).put("title", "第二个推荐，不应被选中"))
    }

    private fun serveLongTopicWithSuggestions() {
        site.serveJson("t/$TOPIC_ID.json", detailJson(FLOORS.take(WINDOW), validSuggestionJson(), lastRead = 0))
    }

    /**
     * A long topic whose head window is only part of the stream and, as Discourse answers when a
     * next page exists, carries no `suggested_topics` key at all.
     */
    private fun serveLongTopicWithoutSuggestions() {
        site.serveJson("t/$TOPIC_ID.json", detailJson(FLOORS.take(WINDOW), null, lastRead = 0))
    }

    /**
     * The final slice of the stream, answered from the request itself: the site's suggestion rides
     * only a request that asked for it (`include_suggested=true`), as the site closes that field
     * everywhere else. When [release] is given the answer is held until it counts down.
     */
    private fun serveFinalSlice(suggestions: JSONArray?, release: CountDownLatch?) {
        site.serveResponseByQuery("t/$TOPIC_ID/posts.json") { requestPath ->
            release?.await(20, TimeUnit.SECONDS)
            val body =
                if (suggestions != null && requestPath.contains("include_suggested=true")) {
                    postsResponseWithSuggestions(FLOORS.drop(WINDOW), suggestions)
                } else {
                    postsResponse(FLOORS.drop(WINDOW))
                }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        }
    }

    private fun serveNextTopic() {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("stream", JSONArray(listOf(idFor(1))))
        stream.put("posts", JSONArray(listOf(postJson(1))))
        base.put("id", NEXT_TOPIC_ID)
        base.put("title", "推荐的下一个话题")
        base.put("posts_count", 1)
        base.put("highest_post_number", 1)
        site.serveJson("t/$NEXT_TOPIC_ID.json", base.toString())
    }

    private fun openTopic(): TopicFragment {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A topic to read on from"))
        harness.idle()
        return harness.visibleFragment() as TopicFragment
    }

    private fun awaitWholeStream(fragment: TopicFragment) {
        harness.await("the whole stream") { floorsOf(fragment).size == FLOORS.size }
        harness.layoutApp()
        harness.idleClock(300)
    }

    /** Scrolls the list to the very end and lets the viewport report it. */
    private fun settleAtTheEnd() {
        scrollToTheEnd()
        harness.idleClock(400)
    }

    private fun scrollToTheEnd() {
        val list = postList()
        list.scrollBy(0, 100000)
        harness.layoutApp()
        list.scrollBy(0, 1)
        harness.idleClock(200)
    }

    /**
     * A real finger on the shipped list, moving up from three-quarters of the way down. Returns the
     * largest pull offset seen, so a caller can tell a pull that grew from one that never engaged.
     */
    private fun dragUp(fingerPx: Float, release: Int): Float {
        val list = postList()
        val x = list.width / 2f
        val startY = list.height * 0.7f
        val downTime = android.os.SystemClock.uptimeMillis()
        var time = downTime
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_DOWN, x, startY, 0))
        var peak = 0f
        val steps = 24
        for (i in 1..steps) {
            time = downTime + i * 16L
            val y = startY - fingerPx * i / steps
            list.dispatchTouchEvent(MotionEvent.obtain(downTime, time, MotionEvent.ACTION_MOVE, x, y, 0))
            peak = maxOf(peak, pullingOffset())
        }
        list.dispatchTouchEvent(MotionEvent.obtain(downTime, time + 16, release, x, startY - fingerPx, 0))
        return peak
    }

    private fun back() {
        harness.activity.onBackPressed()
        harness.idleClock(700)
    }

    /** Jumps the same screen to the window around [postNumber], replacing whatever window was here. */
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

    // ---- reading the screen --------------------------------------------------

    private fun postList(): PostListView {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw AssertionError("the shell has no content view")
        val lists = viewsUnder(content).filterIsInstance<PostListView>()
        return lists.firstOrNull { it.isShown } ?: lists.firstOrNull()
            ?: throw AssertionError("the topic has no post list")
    }

    private fun pullingOffset(): Float = postList().pullingDownOffset

    private fun topicIdOnTop(): Long {
        val top = harness.topFragment() as? TopicFragment
            ?: throw AssertionError("the top screen is not a topic: ${harness.topFragment()?.javaClass?.simpleName}")
        val field = TopicFragment::class.java.getDeclaredField("topicId").apply { isAccessible = true }
        return field.getLong(top)
    }

    @Suppress("UNCHECKED_CAST")
    private fun floorsOf(fragment: TopicFragment): List<Post> {
        val field = TopicFragment::class.java.getDeclaredField("floors").apply { isAccessible = true }
        return field.get(fragment) as List<Post>
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun composer(): org.telegram.ui.Components.ChatActivityEnterView =
        viewsUnder(harness.visibleFragment()?.fragmentView)
            .filterIsInstance<org.telegram.ui.Components.ChatActivityEnterView>()
            .lastOrNull() ?: throw AssertionError("the topic has no composer")

    private fun viewsUnder(root: View?): List<View> {
        if (root == null) return emptyList()
        val out = mutableListOf<View>()
        fun walk(view: View) {
            out += view
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        return out
    }

    private fun openTopicSearch() {
        val bar = (harness.topFragment() ?: throw AssertionError("no topic")).actionBar
        bar.createMenu().getItem(1).performClick()
        harness.idle()
        val rows = latestItemOptionsMenu(harness.activity)
        val row = rows.firstOrNull { menuItemLabels(listOf(it)).first().contains("话题内搜索") }
            ?: throw AssertionError("no 话题内搜索 row among ${menuItemLabels(rows)}")
        row.performClick()
        harness.await("the in-topic search field") { searchFieldShown() }
    }

    /** The bar's own search field, shown while the in-topic search is up. */
    private fun searchFieldShown(): Boolean {
        val bar = harness.topFragment()?.actionBar ?: return false
        val menu = bar.createMenu()
        for (index in 0 until menu.childCount) {
            val child = menu.getChildAt(index)
            if (child is org.telegram.ui.ActionBar.ActionBarMenuItem && child.isSearchField) {
                return child.searchField?.isShown == true
            }
        }
        return false
    }

    private fun writeVisual(name: String) {
        val view = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val out = java.io.File(artifacts, "$name.png")
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("next-topic render: $out (${out.length()} bytes)")
    }

    // ---- payloads ------------------------------------------------------------

    /**
     * A topic view: the whole stream's ids, the window of [floors] it carries, and - when
     * [suggestions] is not null - the site's ranked list. A head window that a next page follows
     * omits the key entirely, which is what `null` models here.
     */
    private fun detailJson(floors: List<Int>, suggestions: JSONArray?, lastRead: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("stream", JSONArray(FLOORS.map { idFor(it) }))
        stream.put("posts", JSONArray(floors.map { postJson(it) }))
        base.put("id", TOPIC_ID)
        base.put("title", "A topic to read on from")
        base.put("posts_count", FLOORS.size)
        base.put("highest_post_number", HIGHEST)
        base.put("last_read_post_number", lastRead)
        if (suggestions != null) {
            base.put("suggested_topics", suggestions)
        }
        return base.toString()
    }

    private fun postsResponse(floors: List<Int>): String =
        JSONObject()
            .put("post_stream", JSONObject().put("posts", JSONArray(floors.map { postJson(it) })))
            .toString()

    /** The incremental posts answer: the same post stream, and the site's suggestions when asked. */
    private fun postsResponseWithSuggestions(floors: List<Int>, suggestions: JSONArray): String =
        JSONObject()
            .put("post_stream", JSONObject().put("posts", JSONArray(floors.map { postJson(it) })))
            .put("suggested_topics", suggestions)
            .toString()

    private fun postJson(floor: Int): JSONObject = JSONObject().apply {
        put("id", idFor(floor))
        put("post_number", floor)
        put("username", "reader$floor")
        put("name", "Reader $floor")
        put("reply_count", 0)
        put("created_at", "2024-01-01T10:00:00.000Z")
        val filler = (1..6).joinToString("") { "<p>第 $floor 楼，正文第 $it 段，用来撑高这条回复。</p>" }
        put("cooked", "<p>第 $floor 楼</p>$filler")
        put("avatar_template", "/user_avatar/linux.do/reader$floor/{size}.png")
    }

    private fun idFor(floor: Int): Long = ID_BASE + floor.toLong() * ID_STEP

    private fun repoRoot(): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (java.io.File(dir, "fixtures/discourse").isDirectory) return dir
            dir = dir.parentFile
        }
        error("cannot locate the repo root")
    }

    private companion object {
        const val TOPIC_ID = 919191L
        /** Not TOPIC_ID + 1: a client that guessed the next id would open the wrong screen. */
        const val NEXT_TOPIC_ID = 919260L
        const val HIGHEST = 18
        const val WINDOW = 10
        /** A floor whose window ends the stream; the reload target for the stale-slice check. */
        const val REPLACEMENT_FLOOR = HIGHEST
        const val ID_BASE = 7_000_000L
        const val ID_STEP = 71L
        /** The 110dp threshold at the harness's own density (xxhdpi = 3x). */
        const val PULL_THRESHOLD_PX = 110f * 3f
        val FLOORS = (1..HIGHEST).toList()
    }
}
