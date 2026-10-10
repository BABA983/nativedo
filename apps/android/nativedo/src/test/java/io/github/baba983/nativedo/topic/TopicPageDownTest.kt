package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.model.Post
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The topic page-down button: a chat's floating round page-down control, over an ordinary topic
 * read rather than a discussion page.
 *
 * <p>The badge is the number of <em>real stream posts</em> below the current viewport - counted
 * from the stream the site sent (ids, which are neither floor numbers nor stream positions), not
 * from {@code highest_post_number - visible_floor}. A topic with deleted floors has fewer posts
 * than its highest floor implies, and a client that subtracts floors shows a number that counts
 * posts the site never had.</p>
 *
 * <p>The tap jumps to the first unread after the reporter's monotonic high-water mark when that
 * mark sits below the viewport, and to the last message otherwise - including when the reader has
 * read everything and is only scrolling back through history. A target already loaded is a plain
 * scroll; one the window does not carry is read as a window and then landed on exactly. The
 * button is hidden only when nothing is below the viewport (the real end), and no number is shown
 * when there is no reliable position to count from.</p>
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
internal class TopicPageDownTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private val artifacts: java.io.File = java.io.File(repoRoot(), ".amp/in/artifacts/topic-pagedown")
    private val polishArtifacts: java.io.File =
        java.io.File(repoRoot(), ".amp/in/artifacts/ui-polish")

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        runCatching { org.telegram.ui.Components.Bulletin.hideVisible() }
        runCatching { harness.idleClock(300) }
        // Every screen this class opened carries a live read reporter (a ticking coroutine) and a
        // show animation. Left running, they spend a later test's idle budget inside the shared
        // sandbox and make that test's reads look raced, so the presented screens are torn down.
        runCatching { harness.fragmentStack().forEach { fragment -> fragment.onFragmentDestroy() } }
        runCatching { harness.idle() }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * A read resumed into the middle of a long topic: the window the site answers is the one the
     * reader left off in, and the badge counts the posts of the whole stream that are still below
     * the viewport - the number a reader would scroll through, not a floor difference.
     */
    @Test
    fun resumeInTheMiddleShowsTheStreamPostsBelowTheViewport() {
        serveLongTopic(ALL.take(WINDOW_PAGE), lastRead = 45)
        site.serveJson("t/$TOPIC_ID/45.json", detailJson(windowAround(45), 45))

        val fragment = openTopic()
        harness.await("the resumed window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        listView()!!.scrollBy(0, 1)
        harness.idleClock(400)

        val last = lastVisibleFloor(fragment)!!
        val expected = streamRemainingBelow(last)
        println("pagedown: resume lastVisible=$last expected=$expected badge=${badgeCount(fragment)}")
        assertTrue("the reader is in the middle of the topic", expected > 0 && last < HIGHEST)
        assertEquals(
            "the badge counts the real stream posts below the viewport",
            expected,
            badgeCount(fragment),
        )
        assertTrue("the affordance is shown while posts remain below", pageDownVisible(fragment))
        writeVisual("topic-pagedown-normal")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The count follows the scroll and is made of stream positions, not floor arithmetic: with two
     * floors deleted, {@code streamPostIds.size() - lastVisibleStreamIndex - 1} is not
     * {@code highest_post_number - lastVisibleFloor}.
     */
    @Test
    fun theBadgeFollowsTheScrollAndCountsStreamPositionsNotFloorArithmetic() {
        serveLongTopic(ALL, lastRead = HIGHEST)

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).size == ALL.size }
        harness.layoutApp()
        harness.idleClock(400)

        scrollToTopAndSettle(fragment)
        val atTop = lastVisibleFloor(fragment)!!
        val topRemaining = streamRemainingBelow(atTop)
        println("pagedown: top lastVisible=$atTop remaining=$topRemaining badge=${badgeCount(fragment)}")
        assertEquals("the badge at the top", topRemaining, badgeCount(fragment))
        assertTrue(
            "the count is the stream's, not highest_post_number minus the visible floor " +
                "(${topRemaining} vs ${HIGHEST - atTop})",
            topRemaining != HIGHEST - atTop,
        )

        // One viewport down: the last visible floor advances and the badge shrinks by the same
        // posts.
        listView()!!.scrollBy(0, listView()!!.height - 120)
        harness.idleClock(400)
        val lower = lastVisibleFloor(fragment)!!
        assertTrue("the viewport moved down the list ($atTop -> $lower)", lower > atTop)
        val lowerRemaining = streamRemainingBelow(lower)
        println("pagedown: lower lastVisible=$lower remaining=$lowerRemaining badge=${badgeCount(fragment)}")
        assertEquals("the badge below", lowerRemaining, badgeCount(fragment))
        assertTrue("the badge shrinks as the reader scrolls down", lowerRemaining < topRemaining)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A viewport scroll that changes the badge animates it the way a chat's does: the reader sees
     * the old and new digits in flight, not the new number swapped in at once. Telegram's chat sets
     * the page-down count animated ({@code ChatActivitySideControlsButtonsLayout.setButtonCount}),
     * and its stock {@code CounterView} draws the transition; a count set without animation is
     * already the settled image the instant the scroll reports the new viewport.
     *
     * <p>Nothing here pins the animator: the two oracles are the pixels the badge draws (the
     * counter's own view, drawn through its public {@code draw}), a real viewport scroll whose
     * digits differ, and the settled number read through the badge's own text.</p>
     */
    @Test
    fun aScrollThatChangesTheBadgeAnimatesTheStockCounter() {
        serveLongTopic(ALL, lastRead = HIGHEST)

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).size == ALL.size }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)
        // The move to the top itself changed the count; let that change finish so the frame below
        // is the resting old number, not the tail of the previous animation.
        harness.idleClock(700)
        // The badge's view is measured with the button; lay out once more so the counter has a real
        // width before it is drawn.
        harness.layoutApp()
        // Finish the first scroll/show before sampling a successive count change while reading.
        listView()!!.scrollBy(0, listView()!!.height / 2)
        harness.idleClock(700)

        val before = badgeCount(fragment)
        val beforeFrame = drawCounter(fragment)
        assertTrue("the badge starts at a two-digit count ($before)", before > 9)
        assertTrue("the badge is drawn before the scroll", beforeFrame != null)

        // A real viewport down: the last visible floor advances, so the stream posts below it - the
        // badge's own count - drop, and the digits differ.
        listView()!!.scrollBy(0, listView()!!.height - 120)
        // The instant the scroll reports, before any frame runs: a counter that is animating still
        // holds its old digits (its transition starts at progress 0), while one set without
        // animation has already swapped to the settled image.
        val immediate = drawCounter(fragment)
        val sampled = ArrayList<android.graphics.Bitmap>()
        repeat(8) {
            // A full harness drain also runs future frame tasks and would skip the whole flight.
            harness.idleClock(20, maxTasks = 1)
            drawCounter(fragment)?.let { sampled += it }
        }
        harness.idleClock(800)
        val settled = badgeCount(fragment)
        val settledFrame = drawCounter(fragment)!!

        println(
            "pagedown: counter before=$before settled=$settled frames=${sampled.size} " +
                "notSettled=${sampled.count { !it.sameAs(settledFrame) }}",
        )
        assertTrue("the scroll changed the badge ($before -> $settled)", settled < before)
        assertFalse(
            "the counter did not swap to the settled digits the instant the viewport moved",
            immediate!!.sameAs(settledFrame),
        )
        val inFlight = sampled.firstOrNull { frame ->
            val pixels = IntArray(frame.width * frame.height)
            frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            pixels.any { android.graphics.Color.alpha(it) > 0 } &&
                !frame.sameAs(beforeFrame!!) && !frame.sameAs(settledFrame)
        }
        assertTrue(
            "the counter interpolates instead of delaying an instant digit swap",
            inFlight != null,
        )

        writeFrame("topic-pagedown-counter-before", beforeFrame!!)
        sampled.forEachIndexed { index, frame ->
            writeFrame("topic-pagedown-counter-frame-$index", frame)
        }
        writeFrame("topic-pagedown-counter-inflight", inFlight!!)
        writeFrame("topic-pagedown-counter-settled", settledFrame)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The reader has scrolled far above the unread start: the tap goes there, not to the very end -
     * the window that carries it is read and the list lands exactly on the first unread floor.
     */
    @Test
    fun tappingBelowTheHighWaterJumpsToTheFirstUnread() {
        val fragment = openFloorWindow(center = 3, lastRead = 50)
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)

        val reached = reachedFloorMark(fragment)
        val last = lastVisibleFloor(fragment)!!
        val unreadStart = reached + 1
        println("pagedown: unread reached=$reached last=$last unreadStart=$unreadStart")
        assertEquals("the high-water mark is the site's read floor", 50, reached)
        assertTrue("the unread start is below the viewport", unreadStart > last)
        assertTrue(
            "the unread start is not loaded yet: ${floorsOf(fragment).mapNotNull { it.postNumber }}",
            floorsOf(fragment).none { (it.postNumber ?: 0) >= unreadStart },
        )
        val landedFloor = ALL.first { it >= unreadStart }
        site.serveJson("t/$TOPIC_ID/$unreadStart.json", detailJson(windowAround(landedFloor), landedFloor))

        pageDownButton(fragment)!!.performClick()
        harness.await("the unread window to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/$unreadStart.json")
        }
        harness.await("the unread window to load") {
            floorsOf(fragment).any { it.postNumber == landedFloor }
        }
        harness.layoutApp()
        harness.idleClock(400)

        assertEquals("the list landed on the first unread floor", landedFloor, firstVisibleFloor(fragment))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The first unread floor is already in the loaded window: the tap is a plain scroll, with no
     * window read at all.
     */
    @Test
    fun tappingAnUnreadTargetAlreadyLoadedScrollsWithoutAFetch() {
        val fragment = openFloorWindow(center = 3, lastRead = 18)
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)

        val reached = reachedFloorMark(fragment)
        val lastBefore = lastVisibleFloor(fragment)!!
        val firstBefore = firstVisibleFloor(fragment)!!
        val unreadStart = reached + 1
        println("pagedown: loaded reached=$reached last=$lastBefore unreadStart=$unreadStart")
        assertTrue("the unread start is below the viewport", unreadStart > lastBefore)
        assertTrue(
            "the target is already loaded: ${floorsOf(fragment).mapNotNull { it.postNumber }}",
            floorsOf(fragment).any { (it.postNumber ?: 0) >= unreadStart },
        )
        val landedFloor = ALL.first { it >= unreadStart }
        val windowReadsBefore = site.requestPaths.count { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) }

        pageDownButton(fragment)!!.performClick()
        val settled = settleUntilFloorVisible(fragment, landedFloor)
        assertTrue("the smooth scroll brought floor $landedFloor into view", settled)
        val first = firstVisibleFloor(fragment)!!
        val last = lastVisibleFloor(fragment)!!
        assertTrue("the jump moved the list toward the unread floor", first > firstBefore)
        assertTrue("the unread floor is in view", landedFloor in first..last)
        assertEquals(
            "an already-loaded target reads no further window",
            windowReadsBefore,
            site.requestPaths.count { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Everything is read and the reader is scrolling back through history: the arrow is still
     * drawn while posts remain below the viewport, and its tap goes to the last message - there is
     * no unread start to prefer.
     */
    @Test
    fun whenFullyReadTheArrowRemainsAndTapsToTheLastMessage() {
        serveLongTopic(ALL, lastRead = HIGHEST)

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).size == ALL.size }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)

        val last = lastVisibleFloor(fragment)!!
        assertTrue("the reader is above the last floor", last < HIGHEST)
        assertTrue("the arrow is still shown while posts remain below", pageDownVisible(fragment))
        assertEquals(
            "its badge is the posts below the viewport",
            streamRemainingBelow(last),
            badgeCount(fragment),
        )
        writeVisual("topic-pagedown-review")

        pageDownButton(fragment)!!.performClick()
        val settled = settleUntilLastFloor(fragment, HIGHEST)
        assertTrue("the jump settled on the last floor", settled)
        assertEquals("the list jumped to the last floor", HIGHEST, lastVisibleFloor(fragment))
        assertTrue(
            "no window is read for the last, already-loaded floor",
            site.requestPaths.none { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The real end: the viewport holds the last floor, nothing is below it, and the button is gone
     * - no fabricated number is left behind.
     */
    @Test
    fun atTheEndTheButtonDisappears() {
        serveLongTopic(ALL, lastRead = HIGHEST)

        val fragment = openTopic()
        harness.await("the whole topic") { floorsOf(fragment).size == ALL.size }
        harness.layoutApp()
        harness.idleClock(400)
        listView()!!.scrollBy(0, 1)
        harness.idleClock(400)

        assertEquals("the viewport holds the last floor", HIGHEST, lastVisibleFloor(fragment))
        assertTrue("nothing is left below, so the button is gone", !pageDownVisible(fragment))
        assertEquals("and no number is fabricated", 0, badgeCount(fragment))
        writeVisual("topic-pagedown-end")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A jump whose window is still on its way shows the stock spinner and refuses a second tap: a
     * page already being read must not be asked for twice.
     */
    @Test
    fun aPendingJumpShowsTheStockSpinnerAndSwallowsADuplicateTap() {
        val target = 51
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/$target.json") {
            if (first.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(detailJson(windowAround(target), target))
        }
        val fragment = openFloorWindow(center = 3, lastRead = 50)
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)
        assertEquals("the tap targets the first unread below", 51, reachedFloorMark(fragment) + 1)

        pageDownButton(fragment)!!.performClick()
        harness.await("the unread window to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/$target.json")
        }
        harness.idle()
        assertTrue("a pending jump disables the button", !pageDownButton(fragment)!!.isEnabled)
        // The button's show animation and the spinner both run on the clock, so let them advance
        // before looking: a drain alone leaves the ring at its first frame, a zero-length arc. Draw
        // once (which starts the drawable), let the clock move, then draw again and read upstream's
        // own arc - a real frame mid-animation, not the empty first one.
        harness.idleClock(400)
        harness.drawApp()
        harness.idleClock(700)
        writeVisual("topic-pagedown-pending")
        val sweep = loadingSweep(fragment)
        assertTrue("the pending jump shows upstream's spinner mid-arc (sweep=$sweep)", sweep > 1f)
        assertTrue("the spinner is drawn, not only animated", loadingIndicatorShown(fragment))

        pageDownButton(fragment)!!.performClick()
        harness.idle()
        assertEquals(
            "a duplicate tap asks for nothing more",
            1,
            site.requestPaths.count { it == "t/$TOPIC_ID/$target.json" },
        )

        release.countDown()
        harness.await("the unread window to load") {
            floorsOf(fragment).any { it.postNumber == target }
        }
        harness.idleClock(400)
        assertTrue("the button is usable again once the page arrived", pageDownButton(fragment)!!.isEnabled)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A jump that failed keeps every row and the reader's place, and the button comes back enabled so
     * the next tap retries the same window.
     */
    @Test
    fun aFailedJumpKeepsTheReadersPlaceAndRetries() {
        val target = 51
        val failing = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/$target.json") {
            if (failing.get()) {
                MockResponse().setResponseCode(503)
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(detailJson(windowAround(target), target))
            }
        }
        val fragment = openFloorWindow(center = 3, lastRead = 50)
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)
        val lastBefore = lastVisibleFloor(fragment)
        val firstBefore = firstVisibleFloor(fragment)
        val rowsBefore = floorsOf(fragment).size

        pageDownButton(fragment)!!.performClick()
        // The failure is said in passing, and its copy names the retry the reader really has - a tap
        // on the arrow - rather than the paging copy's promise to scroll. Watch one task at a time:
        // an await's full looper drain can advance past the entire transient banner before looking.
        val hint = awaitPageDownFailureBulletin()
        assertTrue("the failed jump names the arrow retry, not a scroll: $hint", hint.any { it.contains("箭头") })
        assertTrue("the failed window was asked for", site.requestPaths.contains("t/$TOPIC_ID/$target.json"))
        assertTrue("the retry button is usable again", pageDownButton(fragment)!!.isEnabled)
        harness.idleClock(350, maxTasks = 1)

        assertEquals("the rows already on screen are kept", rowsBefore, floorsOf(fragment).size)
        assertEquals("the reader's place is kept", lastBefore, lastVisibleFloor(fragment))
        assertEquals("the reader's place is kept", firstBefore, firstVisibleFloor(fragment))
        writeVisual("topic-pagedown-failed")
        val banner = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
            ?: throw AssertionError("the retry hint disappeared before it could be read")
        val bannerRect = android.graphics.Rect()
        val buttonRect = android.graphics.Rect()
        assertTrue("the retry hint is on screen", banner.getLayout().getGlobalVisibleRect(bannerRect))
        assertTrue("the retry arrow is on screen", pageDownButton(fragment)!!.getGlobalVisibleRect(buttonRect))
        assertTrue("the hint must not cover the arrow it asks the reader to tap: $bannerRect / $buttonRect",
            !android.graphics.Rect.intersects(bannerRect, buttonRect))

        failing.set(false)
        pageDownButton(fragment)!!.performClick()
        harness.await("the retried window to load") {
            floorsOf(fragment).any { it.postNumber == target }
        }
        harness.layoutApp()
        harness.idleClock(400)

        assertEquals("the retry landed on the first unread floor", target, firstVisibleFloor(fragment))
        assertEquals(
            "the window was asked twice - once failed, once retried",
            2,
            site.requestPaths.count { it == "t/$TOPIC_ID/$target.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A window opened over a pending page-down jump must bring the button back. The jump bumped the
     * window generation, so its answer is dropped - and with it the only code that would have cleared
     * the "jumping" flag. Unless the window that replaced it clears that flag as it takes over, the
     * button stays a spinner the reader can never use again.
     */
    @Test
    fun aWindowOpenedOverAPendingJumpReenablesTheButton() {
        val target = 51
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        site.serveResponse("t/$TOPIC_ID/$target.json") {
            if (first.getAndSet(false)) {
                release.await(20, TimeUnit.SECONDS)
            }
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(detailJson(windowAround(target), target))
        }
        site.serveJson("t/$TOPIC_ID/$OTHER_WINDOW.json", detailJson(windowAround(OTHER_WINDOW), OTHER_WINDOW))
        val fragment = openFloorWindow(center = 3, lastRead = 50)
        harness.await("the head window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)
        scrollToTopAndSettle(fragment)

        pageDownButton(fragment)!!.performClick()
        harness.await("the unread window to be asked for") {
            site.requestPaths.contains("t/$TOPIC_ID/$target.json")
        }
        harness.idle()
        assertTrue("a pending jump disables the button", !pageDownButton(fragment)!!.isEnabled)

        // Another window opens over the pending jump - a reply target, a floor open.
        loadWindow(fragment, OTHER_WINDOW, OTHER_WINDOW)
        harness.await("the other window to load") {
            firstFloor(fragment) == windowAround(OTHER_WINDOW).first()
        }
        harness.layoutApp()
        harness.idleClock(400)
        assertTrue(
            "the button is usable again once another window replaced the jump",
            pageDownButton(fragment)!!.isEnabled,
        )

        // The stale jump answer lands later; it must not stick the spinner back on.
        release.countDown()
        settle()
        assertTrue("the stale jump answer does not stick the spinner", pageDownButton(fragment)!!.isEnabled)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The discussion page is not an ordinary read: its button carries the site's own count of the
     * root's replies, and it never pages the topic's stream.
     */
    @Test
    fun aDiscussionPageUsesTheButtonsOwnCountNotTheTopicStream() {
        val parentId = idFor(PARENT_FLOOR)
        site.serveJson("t/$TOPIC_ID/$PARENT_FLOOR.json", discussionRootJson(parentId, replyCount = 4))
        site.serveJsonFor("posts/$parentId/replies") { _ -> repliesJson(listOf(15, 16)) }
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query -> postsResponse(requestedFloors(query)) }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val thread = TopicFragment.forReplies(TOPIC_ID, "A topic", parentId, PARENT_FLOOR, 4)
        harness.present(thread)
        harness.await("the discussion root and its first replies") { floorsOf(thread).size == 3 }
        harness.layoutApp()
        harness.idleClock(400)

        assertTrue("the fragment is the discussion page", thread.isThreadMode())
        assertTrue(
            "the affordance is upstream's page-down button",
            thread.threadStatusView() is ChatActivityBlurredRoundPageDownButton,
        )
        assertEquals(
            "the badge is the discussion's remaining replies, not a stream count",
            2,
            badgeCount(thread),
        )
        // The only topic window read is the discussion's own root, resolved by its floor.
        assertEquals(
            "only the discussion's own root is read as a topic window",
            listOf("t/$TOPIC_ID/$PARENT_FLOOR.json"),
            site.requestPaths.filter { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) },
        )

        // The affordance reads on through the replies endpoint; it never pages the topic's stream.
        thread.threadStatusView()!!.performClick()
        harness.idle()
        assertTrue(
            "the affordance's tap reads replies, never the topic's stream: " +
                "${site.queriesFor("t/$TOPIC_ID/posts.json")}",
            site.queriesFor("t/$TOPIC_ID/posts.json").isEmpty(),
        )
        assertEquals(
            "and it reads no further topic window",
            listOf("t/$TOPIC_ID/$PARENT_FLOOR.json"),
            site.requestPaths.filter { it.matches(Regex("t/$TOPIC_ID/\\d+\\.json")) },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A floor drawn as several rows (a poll splits its body) shifts the rows after it, so a jump
     * that used a floor's own index as the list row would land short. Opening on the floor after
     * the split must land on that floor, not on a row two floors above it.
     */
    @Test
    fun openingAfterAMultiBubbleFloorLandsOnTheFloorItOpenedFor() {
        val fragment = openFloorWindow(center = 21, lastRead = 21)
        harness.await("the window") { floorsOf(fragment).size >= WINDOW_PAGE }
        harness.layoutApp()
        harness.idleClock(400)

        assertTrue(
            "the multi-bubble floor is in the window",
            floorsOf(fragment).any { it.postNumber == POLL_FLOOR },
        )
        assertTrue(
            "that floor draws more rows than it has floors (${rowCount()} rows vs ${floorsOf(fragment).size} floors)",
            rowCount() > floorsOf(fragment).size,
        )
        assertEquals(
            "the list lands on the floor it opened for, not shifted by the poll's extra rows",
            21,
            firstVisibleFloor(fragment),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ---- opening -------------------------------------------------------------

    private fun serveLongTopic(headFloors: List<Int>, lastRead: Int) {
        site.serveJson("t/$TOPIC_ID.json", detailJson(headFloors, lastRead))
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query -> postsResponse(requestedFloors(query)) }
    }

    private fun openTopic(): TopicFragment {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A long topic"))
        harness.idle()
        return harness.visibleFragment() as TopicFragment
    }

    /** Opens the topic at [center], with the site's own read position set to [lastRead]. */
    private fun openFloorWindow(center: Int, lastRead: Int): TopicFragment {
        // A wide window so neither prefetch edge is reached on the open: the reader's own viewport
        // stays away from both ends, and the loaded set does not grow under the jump.
        site.serveJson("t/$TOPIC_ID/$center.json", detailJson(windowAround(center, FLOOR_WINDOW), lastRead))
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query -> postsResponse(requestedFloors(query)) }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forFloor(TOPIC_ID, "A long topic", center))
        harness.idle()
        return harness.visibleFragment() as TopicFragment
    }

    /** Puts the list at the very top and lets the badge report the new viewport. */
    private fun scrollToTopAndSettle(fragment: TopicFragment) {
        val list = listView() ?: return
        list.scrollToPosition(0)
        harness.layoutApp()
        list.scrollBy(0, 1)
        harness.idleClock(400)
    }

    /** Advances the clock until a smooth scroll has brought [floor] into view; false on timeout. */
    private fun settleUntilFloorVisible(fragment: TopicFragment, floor: Int): Boolean {
        repeat(80) {
            harness.idleClock(100)
            val first = firstVisibleFloor(fragment)
            val last = lastVisibleFloor(fragment)
            if (first != null && last != null && floor in first..last) {
                harness.idleClock(100)
                return true
            }
        }
        return false
    }

    /** Advances the clock until a smooth scroll has brought [floor] into view; false on timeout. */
    private fun settleUntilLastFloor(fragment: TopicFragment, floor: Int): Boolean {
        repeat(60) {
            harness.idleClock(100)
            if (lastVisibleFloor(fragment) == floor) {
                harness.idleClock(100)
                return true
            }
        }
        return false
    }

    // ---- reading the affordance ----------------------------------------------

    private fun pageDownButton(fragment: TopicFragment): ChatActivityBlurredRoundPageDownButton? =
        fragment.threadStatusView() as? ChatActivityBlurredRoundPageDownButton

    /** The floor of the list's first row, or null; the loaded window's own order. */
    private fun firstFloor(fragment: TopicFragment): Int? = floorsOf(fragment).firstOrNull()?.postNumber

    /** Opens another window over the presented one the way a reply-target jump does. */
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

    /** Pumps the main looper in real time so a released response can land and be parsed. */
    private fun settle(iterations: Int = 300) {
        repeat(iterations) {
            harness.idle()
            harness.idleUpstreamQueues()
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
    }

    /** Upstream's pending spinner, reached through the button's own view and drawable. */
    private fun loadingDrawable(fragment: TopicFragment): org.telegram.ui.Components.CircularProgressDrawable? {
        val button = pageDownButton(fragment) ?: return null
        val buttonViewField = button.javaClass.getDeclaredField("buttonView").apply { isAccessible = true }
        val buttonView = buttonViewField.get(button) ?: return null
        val drawableField = buttonView.javaClass.getDeclaredField("loadingIndicatorDrawable").apply { isAccessible = true }
        return drawableField.get(buttonView) as? org.telegram.ui.Components.CircularProgressDrawable
    }

    /**
     * The spinner's arc, in degrees, read from upstream's own drawable after a draw. A real frame
     * mid-animation has a non-zero sweep; the drawable's empty first frame is zero.
     */
    private fun loadingSweep(fragment: TopicFragment): Float {
        val drawable = loadingDrawable(fragment) ?: return 0f
        val segmentField = drawable.javaClass.getDeclaredField("segment").apply { isAccessible = true }
        val segment = segmentField.get(drawable) as FloatArray
        return segment[1] - segment[0]
    }

    /** Whether upstream's loading indicator view is actually drawn (visible, not transparent). */
    private fun loadingIndicatorShown(fragment: TopicFragment): Boolean {
        val button = pageDownButton(fragment) ?: return false
        val buttonViewField = button.javaClass.getDeclaredField("buttonView").apply { isAccessible = true }
        val buttonView = buttonViewField.get(button) ?: return false
        val viewField = buttonView.javaClass.getDeclaredField("loadingIndicatorView").apply { isAccessible = true }
        val view = viewField.get(buttonView) as? View ?: return false
        return view.visibility == View.VISIBLE && view.alpha > 0f
    }

    /** Watches the stock Bulletin one task at a time for a page-down failure hint, before it hides. */
    private fun awaitPageDownFailureBulletin(): List<String> {
        repeat(5_000) {
            harness.idle(1)
            val bulletin = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
            if (bulletin != null) {
                val texts = bulkText(bulletin.getLayout())
                if (texts.any { it.contains("没能读出来") }) return texts
            }
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
        }
        throw AssertionError("the page-down failure Bulletin never appeared")
    }

    /** Every text a view tree draws, for reading a Bulletin the screen showed in passing. */
    private fun bulkText(view: View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { bulkText(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun pageDownVisible(fragment: TopicFragment): Boolean =
        fragment.threadStatusView()?.visibility == View.VISIBLE

    /**
     * The badge's own number, read from upstream's counter the way the screen set it - the number
     * the reader sees, not a re-computation. The counter's integer is reset to -1 on a re-layout, so
     * the stable observable is the badge's own text.
     */
    private fun badgeCount(fragment: TopicFragment): Int {
        val button = pageDownButton(fragment) ?: return -1
        val counterField = ChatActivityBlurredRoundPageDownButton::class.java.getDeclaredField("counterView")
        counterField.isAccessible = true
        val counter = counterField.get(button) as? org.telegram.ui.Components.CounterView ?: return 0
        val drawable = counter.counterDrawable
        val textField = drawable.javaClass.getDeclaredField("currentText")
        textField.isAccessible = true
        val text = textField.get(drawable) as? CharSequence ?: return 0
        return text.toString().trim().toIntOrNull() ?: 0
    }

    /**
     * The badge's own pixels at this instant: upstream's counter is a view, so drawing it through
     * its public {@code draw} renders exactly the frame the reader sees - the old digits, a blend,
     * or the settled ones - without reading the animator's state.
     */
    private fun drawCounter(fragment: TopicFragment): android.graphics.Bitmap? {
        val counter = counterView(fragment) ?: return null
        if (counter.width <= 0 || counter.height <= 0) return null
        val bitmap = android.graphics.Bitmap.createBitmap(
            counter.width,
            counter.height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        counter.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    /** Upstream's counter, reached through the button's own view tree. */
    private fun counterView(fragment: TopicFragment): org.telegram.ui.Components.CounterView? {
        val button = pageDownButton(fragment) ?: return null
        return firstCounter(button)
    }

    private fun firstCounter(view: View): org.telegram.ui.Components.CounterView? {
        if (view is org.telegram.ui.Components.CounterView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                firstCounter(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Retains a rendered badge frame under [polishArtifacts], the ui-polish animation artifact. */
    private fun writeFrame(name: String, bitmap: android.graphics.Bitmap) {
        val out = java.io.File(polishArtifacts, "$name.png")
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("pagedown render: $out (${out.length()} bytes)")
    }

    /** The reporter's monotonic reading high-water mark, through its read-only accessor. */
    private fun reachedFloorMark(fragment: TopicFragment): Int {
        val reporter = field(fragment, "reporter")
        return reporter.javaClass.getMethod("getReachedFloorMark").invoke(reporter) as Int
    }

    private fun firstVisibleFloor(fragment: TopicFragment): Int? =
        floorNumberAt(fragment, linearManager()?.findFirstVisibleItemPosition() ?: -1)

    private fun lastVisibleFloor(fragment: TopicFragment): Int? =
        floorNumberAt(fragment, linearManager()?.findLastVisibleItemPosition() ?: -1)

    private fun floorNumberAt(fragment: TopicFragment, position: Int): Int? {
        if (position < 0) return null
        val bubbleFloors = bubbleFloorsOf(fragment)
        if (position >= bubbleFloors.size) return null
        return floorsOf(fragment)[bubbleFloors[position]].postNumber
    }

    private fun linearManager(): LinearLayoutManager? =
        listView()?.layoutManager as? LinearLayoutManager

    /** The real stream posts strictly below a floor: the number the badge is made of. */
    private fun streamRemainingBelow(floorNumber: Int): Int =
        ALL.size - ALL.indexOf(floorNumber) - 1

    // ---- fragment internals --------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun floorsOf(fragment: TopicFragment): List<Post> =
        field(fragment, "floors") as List<Post>

    @Suppress("UNCHECKED_CAST")
    private fun bubbleFloorsOf(fragment: TopicFragment): List<Int> =
        field(fragment, "bubbleFloors") as List<Int>

    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    /** Draws the screen as it stands to a retained artifact under [artifacts]. */
    private fun writeVisual(name: String) {
        val view = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val out = java.io.File(artifacts, "$name.png")
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("pagedown render: $out (${out.length()} bytes)")
    }

    // ---- payloads ------------------------------------------------------------

    /** A read of the head, carrying [floors] as the first window. */
    private fun detailJson(floors: List<Int>, lastRead: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("stream", JSONArray(ALL.map { idFor(it) }))
        stream.put("posts", JSONArray(floors.map { postJson(it) }))
        base.put("id", TOPIC_ID)
        base.put("title", "A long topic")
        base.put("posts_count", ALL.size)
        base.put("highest_post_number", HIGHEST)
        base.put("last_read_post_number", lastRead)
        return base.toString()
    }

    /** The window the site answers when a floor is named: [size] real floors around [center]. */
    private fun windowAround(center: Int, size: Int = WINDOW_PAGE): List<Int> {
        val index = ALL.indexOf(center)
        if (index < 0) return emptyList()
        val half = size / 2
        var start = (index - half).coerceAtLeast(0)
        val end = (start + size).coerceAtMost(ALL.size)
        start = (end - size).coerceAtLeast(0)
        return ALL.subList(start, end)
    }

    private fun postsResponse(floors: List<Int>): String =
        JSONObject()
            .put("post_stream", JSONObject().put("posts", JSONArray(floors.map { postJson(it) })))
            .toString()

    private fun postJson(floor: Int): JSONObject = JSONObject().apply {
        put("id", idFor(floor))
        put("post_number", floor)
        put("username", "reader$floor")
        put("name", "Reader $floor")
        put("reply_count", 0)
        put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
        val filler = (1..6).joinToString("") { "<p>第 $floor 楼，正文第 $it 段，用来撑高这条回复。</p>" }
        val body = StringBuilder("<p>第 $floor 楼</p>$filler")
        if (floor == POLL_FLOOR) {
            // One floor drawn as three rows - text, the poll, more text - so every row after it is
            // shifted by two. This is what tells a row-index jump apart from a floor-index one.
            body.append("<p>投个票</p>")
            body.append("<div class=\"poll\" data-poll-name=\"frontend\"></div>")
            body.append("<p>投票说完</p>")
            put("polls", JSONArray(pollPayload()))
            put("polls_votes", JSONObject())
        }
        put("cooked", body.toString())
        put("avatar_template", "/user_avatar/linux.do/reader$floor/{size}.png")
    }

    private fun pollPayload(): String =
        JSONObject(harness.fixture("post-polls.json")).getJSONArray("polls").toString()

    /** The discussion page's root: the floor the replies hang off, with the site's own reply count. */
    private fun discussionRootJson(parentId: Long, replyCount: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        val root = postJson(PARENT_FLOOR).apply {
            put("id", parentId)
            put("reply_count", replyCount)
            put("avatar_template", "/user_avatar/linux.do/op/{size}.png")
        }
        stream.put("stream", JSONArray(listOf(parentId)))
        stream.put("posts", JSONArray(listOf(root)))
        base.put("id", TOPIC_ID)
        base.put("title", "A topic")
        base.put("posts_count", HIGHEST)
        base.put("highest_post_number", HIGHEST)
        return base.toString()
    }

    private fun repliesJson(floors: List<Int>): String {
        val array = JSONArray()
        for (floor in floors) {
            array.put(
                JSONObject(postJson(floor).toString()).apply {
                    put("reply_to_post_number", PARENT_FLOOR)
                    put("avatar_template", "/user_avatar/linux.do/reader$floor/{size}.png")
                },
            )
        }
        return array.toString()
    }

    private fun requestedFloors(query: String): List<Int> =
        Regex("post_ids%5B%5D=(\\d+)").findAll(query)
            .map { floorForId(it.groupValues[1].toLong()) }
            .toList()

    private fun idFor(floor: Int): Long = ID_BASE + floor.toLong() * ID_STEP

    private fun floorForId(id: Long): Int = ((id - ID_BASE) / ID_STEP).toInt()

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

    private companion object {
        const val TOPIC_ID = 828281L
        const val HIGHEST = 60
        const val WINDOW_PAGE = 20
        /** The wider window the floor-open tests are dropped into, so no prefetch edge is reached. */
        const val FLOOR_WINDOW = 40
        /** A different window another read can open over a pending page-down jump. */
        const val OTHER_WINDOW = 30
        /** Ids are far from both floor numbers and stream positions on purpose. */
        const val ID_BASE = 6_000_000L
        const val ID_STEP = 61L
        const val PARENT_FLOOR = 12
        /** One floor is drawn as three rows (a poll splits it), shifting the rows after it. */
        const val POLL_FLOOR = 20
        /** Two floors were deleted: the stream is shorter than the topic's highest floor. */
        val DELETED = setOf(7, 25)
        val ALL = (1..HIGHEST).filterNot { it in DELETED }
    }
}
