package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.view.View
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.DialogsActivity
import okhttp3.mockwebserver.MockResponse

/**
 * The home's category pages and the day/night switch over them.
 *
 * <p>The failure modes pinned here:
 *  * a theme switch rebuilds the home's views instead of recolouring them: the strip vanishes
 *    until {@code /categories.json} answers again, the feed is read a second time and the
 *    reader's tab and place in the list are lost;
 *  * a category tap replaces the list in place instead of sliding the page pair: no indicator
 *    animation, no second page, and coming back to the first tab re-reads and loses it;
 *  * the header stays where it was through a swipe and jumps 48dp in one frame when the pair
 *    swaps (the field appearing only at the end instead of travelling with the finger);
 *  * the root pager takes the horizontal swipe while the list still has a category to slide to
 *    (and does not take it when the strip is at the end it belongs to).</p>
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
internal class HomeCategoryPagerTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        // Both feeds are long enough to scroll, and their lengths differ, so a page's own feed is
        // told apart from the other page's: the place the switch and the return have to keep.
        site.serveJson("latest.json", manyTopics("latest-topics.json", 30))
        site.serveFixture("categories.json", "categories.json")
        site.serveJson("c/develop/4/l/latest.json", manyTopics("category-latest.json", 40))
        // The neighbour tab a horizontal drag lands on (运营反馈, the site's first category).
        site.serveJson("c/feedback/2/l/latest.json", manyTopics("category-latest.json", 20))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The options menu's switch posts {@code needSetDayNightTheme}; the strip must recolor in
     * place: same view, same selected tab, same rows, same place in the list, no second read.
     */
    @Test
    fun theDayNightSwitchRecoloursTheStripAndTheFeedWithoutRebuildingThem() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 30 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        tapTab(4)
        harness.await("the category's own feed") { rowCount() == 40 }
        harness.idleClock(400)
        harness.layoutApp()

        val fragment = fragment()
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        val pageView = fragment.fragmentView
        val list = shownList() ?: throw AssertionError("no topic list on screen")
        val manager = list.layoutManager as LinearLayoutManager
        list.scrollBy(0, AndroidUtilities.dp(600f))
        harness.layoutApp()
        val firstBefore = manager.findFirstVisibleItemPosition()
        assertTrue("the list really is scrolled", firstBefore >= 5)
        val categoryRequests = site.requestPaths.count { it == "c/develop/4/l/latest.json" }
        val allRequests = site.requestPaths.count { it == "latest.json" }
        val pill = fragmentField("newTopicsPill")
        val shimmer = progressViews()[0]
        harness.renderApp("home-category-theme-light")

        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            if (toDark) Theme.getTheme("Dark Blue") else Theme.getTheme("Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            assertFalse("the switch is not left half-done", DialogsActivity.switchingTheme)
            harness.layoutApp()

            assertSame("the switch keeps the page's own view", pageView, fragment.fragmentView)
            assertSame("and keeps the strip", strip, strip())
            // The pill and the first-load shimmer are views of this screen too: the switch must
            // recolour them, not replace them (a rebuilt pill loses its count, and the shimmer
            // would come back for a feed that is still there).
            assertSame("and keeps the pill", pill, fragmentField("newTopicsPill"))
            assertSame("and keeps the shimmer", shimmer, progressViews()[0])
            assertEquals("the strip stays visible", View.VISIBLE, strip.visibility)
            assertEquals("on the tab the reader chose", 4, strip.currentTabId)
            assertEquals("the feed is not read a second time", categoryRequests,
                site.requestPaths.count { it == "c/develop/4/l/latest.json" })
            assertEquals("nor is the all-topics head", allRequests,
                site.requestPaths.count { it == "latest.json" })
            assertEquals("the rows are the ones the reader had", 40, rowCount())
            assertEquals("and the reader's place in them", firstBefore,
                manager.findFirstVisibleItemPosition())
            assertEquals(40, list.adapter!!.itemCount)
            harness.renderApp("home-category-theme-dark")
            harness.assertNoTelegramSideEffects()
        } finally {
            Theme.applyTheme(Theme.getTheme("Blue"), false, false)
            DialogsActivity.switchingTheme = false
        }
    }

    /** A tab tap slides the page pair; the first page keeps its items and place for the return. */
    @Test
    fun aTabTapSlidesToTheCategorysOwnPageAndComesBackToTheFirstOnes() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 30 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        val first = shownList() ?: throw AssertionError("no topic list on screen")
        val firstManager = first.layoutManager as LinearLayoutManager
        first.scrollBy(0, AndroidUtilities.dp(600f))
        harness.layoutApp()
        val firstPlace = firstManager.findFirstVisibleItemPosition()
        assertTrue("the first page really is scrolled", firstPlace >= 5)

        // The tap itself is synchronous (RecyclerListView.clickItem hands the click over): the
        // strip's indicator animation is running and the incoming page is already on screen when
        // the click returns. The queued frames are stepped by hand, so a middle frame can be drawn.
        val container = strip.tabsContainer
        val tab = (0 until container.childCount)
            .map { index -> container.getChildAt(index) }
            .firstOrNull { it.id == 4 }
            ?: throw AssertionError("the strip drew no view for tab 4")
        container.clickItem(tab, tabIds().indexOf(4))
        assertTrue("the strip animates its indicator", strip.isAnimatingIndicator)
        // Both page frames are on screen mid-slide; the incoming page's own list may already be
        // standing behind its shimmer (the page has no feed yet), which is DialogsActivity's own
        // empty-view behaviour, so the pair is counted by its frames.
        assertEquals("the pair is on screen mid-slide", 2, visiblePages())
        harness.idle(2)
        harness.renderAppFrame("home-category-slide-mid")
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("the tap settles on one page", 1, shownLists().size)
        assertEquals("showing the category's own feed", 40, rowCount())
        assertEquals("without reading the all-topics head again", 1,
            site.requestPaths.count { it == "latest.json" })
        harness.renderApp("home-category-slide-category")

        tapTab(0)
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("going back lands on the first page again", 1, shownLists().size)
        assertSame("and its list is the one the reader left", first, shownList())
        assertEquals("with the feed it already had", 30, rowCount())
        assertEquals("read once, not again", 1, site.requestPaths.count { it == "latest.json" })
        assertEquals("and the reader's place kept", firstPlace, firstManager.findFirstVisibleItemPosition())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * While the list still has a category to slide to, the root pager must leave the swipe to the
     * list; only at the first tab (back) and the last tab (forward) does the root pager take it -
     * DialogsActivity.canParentTabsSlide's own rule.
     */
    @Test
    fun theRootPagerLeavesTheCategorySwipeToTheListUntilTheStripsEnds() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 30 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        val fragment = fragment()
        val belowTheBar = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1500f, 0)

        assertFalse("a swipe forward at the first tab is the list's",
            fragment.canParentTabsSlide(belowTheBar, true))
        assertTrue("a swipe back at the first tab leaves for the previous root tab",
            fragment.canParentTabsSlide(belowTheBar, false))

        tapTab(tabIds().last())
        harness.idleClock(400)
        harness.layoutApp()

        assertTrue("a swipe forward at the last tab leaves for the next root tab",
            fragment.canParentTabsSlide(belowTheBar, true))
        assertFalse("a swipe back at the last tab is the list's",
            fragment.canParentTabsSlide(belowTheBar, false))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A horizontal drag on the list moves to the neighbouring category (and back): the strip's
     * indicator follows the finger and the page pair settles on the neighbour. The failure: the
     * drag does nothing (the gesture dies in the list) or the root pager takes it.
     */
    @Test
    fun aHorizontalDragOnTheListMovesToTheNeighbouringCategory() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 30 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        val frame = harness.visibleFragment()!!.fragmentView

        drag(frame, fromX = harness.width * 0.6f, toX = harness.width * 0.15f)
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("the drag lands on the next category", tabIds()[1], strip.currentTabId)
        assertEquals("with that category's own feed", 20, rowCount())

        drag(frame, fromX = harness.width * 0.4f, toX = harness.width * 0.85f)
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("a drag back returns to the all-topics tab", 0, strip.currentTabId)
        assertEquals("and its feed is the one it had", 30, rowCount())
        assertEquals("read once, not again", 1, site.requestPaths.count { it == "latest.json" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The first-load shimmer: DialogsActivity's own view for a page that has no feed yet. It is
     * up while the page is empty and goes with the first answer; a page that kept its feed -
     * coming back to a tab that already has one - never shows it again. The failure modes: no
     * skeleton while the site is still being read, and a skeleton that comes back for a kept
     * page (it would stand in front of rows that are there).
     */
    @Test
    fun theFirstLoadShimmerOnlyStandsInForAPageWithNoFeedYet() {
        // The first read of the all-topics feed is held at the site, so the shimmer is examined
        // while the page really is still loading rather than after a race with the answer.
        val answer = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("latest.json") {
            answer.await(10, java.util.concurrent.TimeUnit.SECONDS)
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(manyTopics("latest-topics.json", 30))
        }

        harness.bootApp()
        harness.await("the shimmer while the page has no feed") {
            progressViews().any { it.isShown }
        }
        assertEquals("no rows are drawn under the shimmer", 0, rowCount())
        harness.renderApp("home-first-load-skeleton")

        answer.countDown()
        harness.await("the first answer") { rowCount() == 30 }
        harness.await("the shimmer to go with it") {
            harness.idleClock(120)
            progressViews().none { it.isShown }
        }

        // A kept page: the category feed is read once, and the all-topics feed is still in hand
        // when the reader comes back - neither return shows a shimmer again.
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        tapTab(4)
        harness.await("the category's own feed") { rowCount() == 40 }
        harness.await("its shimmer is gone too") {
            harness.idleClock(120)
            progressViews().none { it.isShown }
        }
        tapTab(0)
        harness.await("the kept feed") { rowCount() == 30 }
        harness.idleClock(400)
        assertTrue("a kept page shows no shimmer", progressViews().none { it.isShown })
        assertEquals("and its feed was not read again", 1, site.requestPaths.count { it == "latest.json" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The header follows the pair while a category swipe moves it: the field's collapse offset
     * travels from where the reader left the list to the offset the page coming in keeps, so the
     * field fades/slides in with the finger and the swap at the end has nothing left to change.
     * The failure this pins: the offset standing still for the whole drag and jumping 48dp in one
     * frame when the pages swap.
     */
    @Test
    fun theHeaderFollowsACategorySwipeToTheIncomingPagesOffset() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 30 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }
        val fragment = fragment()
        val frame = fragment.fragmentView
        val list = shownList() ?: throw AssertionError("no topic list on screen")
        list.scrollBy(0, AndroidUtilities.dp(600f))
        harness.drawApp()
        val field = fragmentField("searchField") as View
        val collapsed = offset()
        assertTrue("the list really is collapsed", collapsed < -AndroidUtilities.dp(30f))
        assertTrue("and the field left with it", field.alpha < 0.01f)

        // The swipe itself, sampled between moves: the header has to move with the finger, one
        // way, with no single frame carrying the whole travel.
        val startY = harness.height * 0.45f
        val fromX = harness.width * 0.85f
        val toX = harness.width * 0.15f
        fun event(action: Int, x: Float, time: Long): MotionEvent =
            MotionEvent.obtain(0L, time, action, x, startY, 0)
        val samples = mutableListOf<Float>()
        var midFieldAlpha = -1f
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, fromX, 0))
        harness.idle()
        val steps = 8
        for (step in 1..steps) {
            val x = fromX + (toX - fromX) * step / steps
            frame.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, step * 16L))
            harness.idle()
            samples += offset()
            if (step == steps / 2) {
                midFieldAlpha = field.alpha
                harness.renderAppFrame("home-motion-category-swipe-mid")
            }
        }
        frame.dispatchTouchEvent(event(MotionEvent.ACTION_UP, toX, steps * 16L + 16L))
        harness.idle()

        assertTrue(
            "the swipe starts without moving the header",
            samples.first() >= collapsed - 1f,
        )
        assertTrue("the header moves with the swipe's first steps", samples[1] > collapsed)
        for (index in 1 until samples.size) {
            assertTrue(
                "the header travels one way only",
                samples[index] >= samples[index - 1] - 1f,
            )
        }
        val travelled = samples.last() - collapsed
        val biggestStep = (1 until samples.size).maxOf { samples[it] - samples[it - 1] }
        assertTrue("no single frame carries the whole travel", biggestStep <= travelled * 0.5f)
        assertTrue("halfway in, the field is half-visible", midFieldAlpha > 0.05f && midFieldAlpha < 0.95f)
        val released = offset()
        assertTrue(
            "the release does not jump the header either",
            released >= samples.last() - 1f && released < 1f,
        )

        harness.idleClock(600)
        harness.await("the settled category page") { shownLists().size == 1 && rowCount() == 20 }
        harness.drawApp()
        assertTrue(
            "the header landed on the incoming page's own offset",
            offset() > -AndroidUtilities.dp(1f),
        )
        assertTrue("and the field is back at its rest", field.alpha > 0.99f)
        assertEquals("the swipe landed on the next category", tabIds()[1], strip()!!.currentTabId)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /** The header's collapse offset, the value the field and the strip are drawn from. */
    private fun offset(): Float = fragmentField("scrollYOffset") as Float

    /** A finger's horizontal drag, dispatched the way the window's would be. */
    private fun drag(view: View, fromX: Float, toX: Float) {
        val startY = harness.height * 0.45f
        val down = 0L
        fun event(action: Int, x: Float, time: Long): MotionEvent =
            MotionEvent.obtain(down, down + time, action, x, startY, 0)
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, fromX, 0))
        harness.idle()
        val steps = 8
        for (step in 1..steps) {
            val x = fromX + (toX - fromX) * step / steps
            view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, step * 16L))
            harness.idle()
        }
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, toX, steps * 16L + 16L))
        harness.idle()
    }

    /** One of the fixture's feeds, with [count] rows: every row is a copy with its own id. */
    private fun manyTopics(fixture: String, count: Int): String {
        val base = org.json.JSONObject(harness.fixture(fixture))
        val topicList = base.getJSONObject("topic_list")
        val source = topicList.getJSONArray("topics")
        val topics = org.json.JSONArray()
        for (index in 0 until count) {
            val topic = org.json.JSONObject(source.getJSONObject(index % source.length()).toString())
            topic.put("id", 900000 + index)
            topic.put("title", topic.getString("title") + " #" + index)
            topics.put(topic)
        }
        topicList.put("topics", topics)
        return base.toString()
    }

    private fun fragment(): TopicListFragment =
        harness.visibleFragment() as? TopicListFragment
            ?: throw AssertionError("the shell shows no topic list")

    /** The tab strip the screen put over the list: upstream's own filter control. */
    private fun strip(): FilterTabsView? = findStrip(harness.activity.findViewById(android.R.id.content))

    private fun findStrip(view: View?): FilterTabsView? = when (view) {
        null -> null
        is FilterTabsView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findStrip(view.getChildAt(it)) }
        else -> null
    }

    private fun tabIds(): List<Int> {
        val strip = strip() ?: return emptyList()
        return (0 until strip.tabsCount).map { position -> strip.getTab(position)!!.id }
    }

    /** The list the reader is looking at: the shown one, not a page waiting behind the pair. */
    private fun shownList(): RecyclerListView? = shownLists().firstOrNull()

    private fun shownLists(): List<RecyclerListView> =
        contentLists(harness.activity.findViewById(android.R.id.content)).filter { it.isShown }

    private fun contentLists(view: View?): List<RecyclerListView> = when (view) {
        null -> emptyList()
        is RecyclerListView -> if (view.parent is FilterTabsView) emptyList() else listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { contentLists(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** A tap on a tab, handed over the way upstream's own list hands one over. */
    private fun tapTab(id: Int) {
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        harness.layoutApp()
        val position = tabIds().indexOf(id)
        if (position < 0) throw AssertionError("the strip has no tab $id: " + tabIds())
        val container = strip.tabsContainer
        val tab = (0 until container.childCount)
            .map { index -> container.getChildAt(index) }
            .firstOrNull { it.id == id }
            ?: throw AssertionError("the strip drew no view for tab $id")
        container.clickItem(tab, position)
        harness.idle()
    }

    private fun rowCount(): Int = shownList()?.adapter?.itemCount ?: 0

    /** A field of the screen under test, read the way the other tests read one. */
    private fun fragmentField(name: String): Any? {
        val field = TopicListFragment::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(fragment())
    }

    /** Both pages' first-load shimmers, shown page and waiting page alike. */
    private fun progressViews(): List<View> {
        val listFragment = harness.visibleFragment() as? TopicListFragment ?: return emptyList()
        val field = TopicListFragment::class.java.getDeclaredField("viewPages").apply { isAccessible = true }
        val pages = field.get(listFragment) as Array<*>
        return pages.mapNotNull { page ->
            val pageField = page!!.javaClass.getField("progressView")
            pageField.get(page) as? View
        }
    }

    /** The page pair's own frames that are on screen; a page whose shimmer is up is still one. */
    private fun visiblePages(): Int {
        val field = TopicListFragment::class.java.getDeclaredField("viewPages").apply { isAccessible = true }
        val pages = field.get(fragment()) as Array<*>
        return pages.count { (it as? View)?.visibility == View.VISIBLE }
    }
}
