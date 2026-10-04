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

/**
 * The home's category pages and the day/night switch over them.
 *
 * <p>The failure modes pinned here:
 *  * a theme switch rebuilds the home's views instead of recolouring them: the strip vanishes
 *    until {@code /categories.json} answers again, the feed is read a second time and the
 *    reader's tab and place in the list are lost;
 *  * a category tap replaces the list in place instead of sliding the page pair: no indicator
 *    animation, no second page, and coming back to the first tab re-reads and loses it;
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
        assertEquals("the pair is on screen mid-slide", 2, shownLists().size)
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

    // ------------------------------------------------------------------ helpers

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
}
