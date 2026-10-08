package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView

/**
 * The home list's own two behaviours: what a return from a topic does not do, and how the list
 * pages past its first page.
 *
 * <p>The failure modes pinned here:
 *  * coming back to the list reads the feed again - a request the reader did not ask for, which
 *    throws away the place they were reading and the count the new-topics pill was holding, and
 *    (because the answer races the topic screen's own read report) can keep a badge on the topic
 *    they have just finished;
 *  * the end of a page is the end of the list - no next page is read, or it is read more than once
 *    for one arrival, or a page already held is appended again, or the reads go on after the site
 *    said there is no more;
 *  * a page that did not arrive reads as "the list just ends here", with no way to ask again.</p>
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
internal class HomeFeedTest {

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
     * A return from a topic is not a read of the site. The list is the reader's own and updates in
     * place: the topic screen's finished-mark (`TopicReadMarks`, the same signal the topic sets
     * when its last floor is reached) takes the row's badge away, and no request follows the
     * return. The failure this pins: a refresh on the return, which loses the reader's place and
     * races the read report the topic screen has just sent.
     */
    @Test
    fun comingBackFromATopicUpdatesTheRowWithoutReadingTheFeedAgain() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 2 }
        harness.drawApp()
        assertEquals("the feed's own count is on the row", 3, rowUnread(0))

        // The topic screen marks a topic its reader reached the end of. The row takes the badge
        // off right then - the mark is the reader's own signal, and the list listens to it - and
        // no request follows.
        io.github.baba983.nativedo.feature.topic.data.TopicReadMarks.markReadInFull(101L)
        harness.idle()
        assertEquals("the finished topic's badge left the row", 0, rowUnread(0))

        val reads = site.queriesFor("latest.json").count { it.contains("page=0") }
        (harness.visibleFragment() as NativeDoMainTabsActivity.TabPage).onParentBecameVisible()
        harness.idleClock(200)
        harness.drawApp()

        assertEquals(
            "the return reads nothing",
            reads,
            site.queriesFor("latest.json").count { it.contains("page=0") },
        )
        assertEquals("and the row stays without its badge", 0, rowUnread(0))
        assertEquals("the other row keeps what the feed said", 0, rowUnread(1))
        harness.renderApp("home-return-after-topic")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The end of a page reads the next one and appends it: one read for one arrival, duplicates
     * dropped, the loading footer up while the page is on its way, and no read at all once the
     * site's answer carries no more_topics_url. The failure modes: a list that ends at its first
     * page, two reads for one arrival (both answers appending over each other), a doubled row,
     * and reads that go on past the last page.
     */
    @Test
    fun theEndOfAPageReadsTheNextOneOnceAndTheSiteSaysWhenTheListIsOver() {
        val held = java.util.concurrent.CountDownLatch(1)
        site.serveJsonFor("latest.json") { query ->
            if (query.contains("page=1")) {
                held.await(10, java.util.concurrent.TimeUnit.SECONDS)
                // One id the first page already had (to drop) and five the reader has not seen.
                topicsPage(count = 5, startId = 900100, more = null, duplicateId = 900000)
            } else {
                topicsPage(count = 30, startId = 900000, more = "/latest.json?page=1")
            }
        }

        harness.bootApp()
        harness.await("the first page") { rowCount() == 30 }
        harness.drawApp()
        val list = shownList() ?: throw AssertionError("no topic list on screen")

        // The reader reaches the end: the next page is read once, and repeated scrolls at the end
        // while it is on its way do not ask again.
        list.scrollBy(0, AndroidUtilities.dp(4000f))
        harness.idleClock(16)
        repeat(3) {
            list.scrollBy(0, AndroidUtilities.dp(100f))
            harness.idle()
        }
        // requestMore fires synchronously from onScrolled, but the page=1 request leaves on
        // OkHttp's own thread: wait (real time, the way await does) until the site has seen it,
        // then assert it was asked for exactly once while the held answer is on its way.
        harness.await("the next page to be asked for") {
            site.queriesFor("latest.json").count { it.contains("page=1") } >= 1
        }
        assertEquals(
            "one read of page 1 while it is on its way",
            1,
            site.queriesFor("latest.json").count { it.contains("page=1") },
        )

        // The footer is upstream's own: one chat-list shimmer cell under the last row.
        val footerIndex = rowCount() - 1
        val footer = list.findViewHolderForAdapterPosition(footerIndex)?.itemView
            ?: throw AssertionError("no loading footer under the last row")
        assertTrue("the loading footer is drawn", footer.isShown)
        assertTrue(
            "with upstream's single-cell chat-row shimmer",
            views(footer).any {
                it is FlickerLoadingView && it.isShown &&
                    it.viewType == FlickerLoadingView.DIALOG_CELL_TYPE
            },
        )
        harness.renderApp("home-motion-more-loading")

        held.countDown()
        harness.await("the appended page") { rowCount() == 35 }
        harness.drawApp()

        assertEquals(
            "one read of page 1 for the whole end",
            1,
            site.queriesFor("latest.json").count { it.contains("page=1") },
        )
        assertEquals("every row is in the list once", 35, fragment().rows().size)
        assertEquals("and no id is doubled by the page", 35, fragment().rows().map { it.id }.distinct().size)

        // The site said there is no more: the end of the list reads nothing, and no footer stays.
        assertEquals("no footer lingers under the rows", fragment().rows().size, rowCount())
        list.scrollBy(0, AndroidUtilities.dp(300f))
        harness.idleClock(200)
        assertTrue(
            "nothing is read after the last page",
            site.queriesFor("latest.json").none { it.contains("page=2") },
        )
        harness.renderApp("home-motion-more-end")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page that did not arrive keeps its own footer: the shimmer becomes the sentence below the
     * last row, and the tap there reads that page again - not the whole list. The failure this
     * pins: a failed page that reads as the end of the list, with nothing to tap.
     */
    @Test
    fun aPageThatDidNotArriveIsRetriedFromItsOwnFooter() {
        var failedOnce = false
        site.serveJsonFor("latest.json") { query ->
            when {
                !query.contains("page=1") -> topicsPage(count = 30, startId = 900000, more = "/latest.json?page=1")
                !failedOnce -> {
                    failedOnce = true
                    // A page the client cannot read: the footer has to say so.
                    "{ \"topic_list\": \"not a page\" }"
                }
                else -> topicsPage(count = 5, startId = 900100, more = null)
            }
        }

        harness.bootApp()
        harness.await("the first page") { rowCount() == 30 }
        harness.drawApp()
        val list = shownList() ?: throw AssertionError("no topic list on screen")
        list.scrollBy(0, AndroidUtilities.dp(4000f))
        harness.await("the failed page's footer") {
            harness.idleClock(60)
            footerRetryVisible(list)
        }
        // The footer is a new row under the one the reader was at, so it arrives just below the
        // fold; the reader scrolls the little that is left to see it, and so does this.
        list.scrollBy(0, AndroidUtilities.dp(150f))
        harness.layoutApp()
        harness.renderApp("home-motion-more-failed")

        val footerIndex = rowCount() - 1
        val footer = list.findViewHolderForAdapterPosition(footerIndex)?.itemView
            ?: throw AssertionError("no footer under the last row")
        footer.performClick()
        harness.await("the page the tap asked for") { rowCount() == 35 }
        assertEquals(
            "the retry read the page it belongs to, once",
            2,
            site.queriesFor("latest.json").count { it.contains("page=1") },
        )
        assertEquals("and the rows arrived", 35, fragment().rows().size)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * One answer of `latest-topics.json` with its own ids: page 0 starts at 900000, and a later
     * page can carry one id the first page already had, to be dropped.
     */
    private fun topicsPage(count: Int, startId: Int, more: String?, duplicateId: Long? = null): String {
        val base = org.json.JSONObject(harness.fixture("latest-topics.json"))
        val topicList = base.getJSONObject("topic_list")
        val source = topicList.getJSONArray("topics")
        val topics = org.json.JSONArray()
        for (index in 0 until count) {
            val topic = org.json.JSONObject(source.getJSONObject(index % source.length()).toString())
            topic.put("id", startId + index)
            topic.put("title", "topic #" + index)
            topics.put(topic)
        }
        if (duplicateId != null) {
            val topic = org.json.JSONObject(source.getJSONObject(0).toString())
            topic.put("id", duplicateId)
            topic.put("title", "topic already held")
            topics.put(topic)
        }
        topicList.put("topics", topics)
        topicList.put("more_topics_url", more ?: org.json.JSONObject.NULL)
        return base.toString()
    }

    /** The unread count the row at [position] was last bound with. */
    private fun rowUnread(position: Int): Int {
        val list = shownList() ?: throw AssertionError("no topic list on screen")
        val cell = list.findViewHolderForAdapterPosition(position)?.itemView
            ?: throw AssertionError("no row at $position")
        val dialog = DialogCell::class.java.getDeclaredField("customDialog").apply { isAccessible = true }
            .get(cell) as DialogCell.CustomDialog
        return dialog.unread_count
    }

    /** Whether the footer under the last row is showing the retry line. */
    private fun footerRetryVisible(list: RecyclerListView): Boolean {
        val index = list.adapter?.itemCount?.minus(1) ?: return false
        val footer = list.findViewHolderForAdapterPosition(index)?.itemView ?: return false
        return views(footer).any {
            it is android.widget.TextView && it.isShown &&
                it.text == harness.context.getString(io.github.baba983.nativedo.R.string.topic_list_more_failed)
        }
    }

    private fun views(view: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(v: View) {
            found += v
            if (v is ViewGroup) {
                for (index in 0 until v.childCount) walk(v.getChildAt(index))
            }
        }
        walk(view)
        return found
    }

    private fun fragment(): TopicListFragment =
        harness.visibleFragment() as? TopicListFragment
            ?: throw AssertionError("the shell shows no topic list")

    private fun shownList(): RecyclerListView? =
        contentLists(harness.activity.findViewById(android.R.id.content)).firstOrNull { it.isShown }

    private fun contentLists(view: View?): List<RecyclerListView> = when (view) {
        null -> emptyList()
        // The strip's own tabs container is a list too; the topic rows are the other one.
        is RecyclerListView -> if (view.parent is org.telegram.ui.Components.FilterTabsView) {
            emptyList()
        } else {
            listOf(view)
        }
        is ViewGroup -> (0 until view.childCount).flatMap { contentLists(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun rowCount(): Int = shownList()?.adapter?.itemCount ?: 0
}
