package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog

/**
 * The topic list's tag filter.
 *
 * <p>The failure this pins: linux.do runs on tags as much as on categories, so a list that can only
 * be narrowed by category hides most of its topics. The bar carries the site's tags - read from
 * {@code /tags.json} - in a dialog, and picking one asks for that tag's own feed
 * ({@code /tag/{slug}/l/latest.json}); picking a category as well asks for the site's combined
 * route, and clearing returns to what the category alone asks for. What the filter is set to is
 * visible in the title and droppable from the bar, because a filter a reader cannot see is one they
 * cannot trust.</p>
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
internal class TopicTagsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("c/develop/4/l/latest.json", "category-latest.json")
        site.serveFixture("tags.json", "tags.json")
        site.serveFixture("tag/android/l/latest.json", "tag-latest.json")
        site.serveFixture("tags/c/develop/4/android/12/l/latest.json", "category-latest.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theBarOffersTheSitesTagsAndOneTagNarrowsTheFeedUntilItIsDropped() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the site's tags to arrive") { site.requestPaths.contains("tags.json") }
        harness.layoutApp()
        harness.renderApp("topic-tags-all")
        assertEquals(
            "no tag is in hand, so the way out of one is not offered",
            View.GONE,
            menuItem(TopicListFragment.CLEAR_TAG_ITEM)?.visibility,
        )

        pickTag("#android")

        harness.await("the tag's own feed") {
            site.requestPaths.contains("tag/android/l/latest.json")
        }
        harness.await("the list to hold that tag's topics") { rowCount() == 1 }
        println("tags: asked=" + site.queriesFor("tag/android/l/latest.json"))
        assertEquals(
            "the tag travels as its own slug on the site's route for one",
            "/tag/android/l/latest.json?page=0",
            site.recordedRequests.last { it.startsWith("GET /tag/") }.removePrefix("GET "),
        )
        harness.layoutApp()
        harness.drawApp()
        harness.idleClock(400)
        assertEquals(
            "the tag in hand is named in the title",
            "linux.do · #android",
            title(),
        )
        assertEquals(
            "and the bar carries the one-tap way out of it",
            View.VISIBLE,
            menuItem(TopicListFragment.CLEAR_TAG_ITEM)?.visibility,
        )
        harness.renderApp("topic-tags")

        clickMenuItem(TopicListFragment.CLEAR_TAG_ITEM)
        harness.await("the unfiltered feed") {
            // The head of the feed, not a later page of it: one read at boot and one when the
            // tag was dropped.
            site.queriesFor("latest.json").count { it.contains("page=0") } == 2
        }
        harness.await("the list to hold it again") { rowCount() == 2 }
        assertEquals(
            "clearing the tag leaves nothing selected",
            "linux.do",
            title(),
        )
        assertEquals(
            "and the way out is gone with it",
            View.GONE,
            menuItem(TopicListFragment.CLEAR_TAG_ITEM)?.visibility,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aTagAndACategoryTogetherAskTheSitesCombinedRoute() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the site's categories to arrive") { site.requestPaths.contains("categories.json") }
        harness.await("the site's tags to arrive") { site.requestPaths.contains("tags.json") }

        // The category first, the way the strip hands it over.
        harness.layoutApp()
        val strip = viewsUnder(harness.activity.window.decorView)
            .filterIsInstance<org.telegram.ui.Components.FilterTabsView>().firstOrNull()
            ?: throw AssertionError("the topic list carries no category strip")
        val tabPosition = (0 until strip.tabsCount).first { position ->
            strip.getTab(position)!!.title.toString().contains(CATEGORY)
        }
        val container = strip.tabsContainer
        val tab = (0 until container.childCount)
            .map { index -> container.getChildAt(index) }
            .first { it.id == strip.getTab(tabPosition)!!.id }
        container.clickItem(tab, tabPosition)
        harness.idle()
        harness.await("that category's own feed") { site.requestPaths.contains("c/develop/4/l/latest.json") }
        harness.await("its rows") { rowCount() == 3 }

        pickTag("#android")

        harness.await("the combined route") {
            site.requestPaths.contains("tags/c/develop/4/android/12/l/latest.json")
        }
        println("tags: with a category, asked=" +
            site.queriesFor("tags/c/develop/4/android/12/l/latest.json"))
        harness.layoutApp()
        harness.drawApp()
        harness.idleClock(400)
        assertEquals(
            "the title names both filters",
            "linux.do · $CATEGORY · #android",
            title(),
        )
        harness.renderApp("topic-category-tag")

        clickMenuItem(TopicListFragment.CLEAR_TAG_ITEM)
        harness.await("the category on its own again") {
            // The category's head, not a later page of it: one read at the tab, one at the drop.
            site.queriesFor("c/develop/4/l/latest.json").count { it.contains("page=0") } == 2
        }
        assertEquals(
            "dropping the tag keeps the category",
            "linux.do · $CATEGORY",
            title(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** The dialog the bar's tag item opens, with the site's own tags in it. */
    private fun pickTag(label: String) {
        // The tags arrive over the network, so the first press can land before the list is in hand
        // and open nothing: press until the dialog is there rather than assume the race went well.
        var open: AlertDialog? = null
        for (attempt in 0 until 50) {
            clickMenuItem(TopicListFragment.TAG_ITEM)
            open = picker()
            if (open != null) break
            java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L)
        }
        val picker = open ?: throw AssertionError("the tag item opened no dialog")
        val rows = pickerRows(picker)
        println("tags: the picker offers " + rows.map { (_, text) -> text })
        val row = rows.firstOrNull { (_, text) -> text.startsWith(label) }
            ?: throw AssertionError("the picker has no $label: " + rows.map { it.second })
        row.first.performClick()
        harness.idle()
    }

    private fun picker(): AlertDialog? = ShadowDialog.getLatestDialog() as? AlertDialog

    /** The dialog's own rows, each with the text it draws. */
    private fun pickerRows(picker: AlertDialog): List<Pair<View, String>> {
        val window = picker.window ?: return emptyList()
        return viewsUnder(window.decorView)
            .filterIsInstance<AlertDialog.AlertDialogCell>()
            .map { cell ->
                val label = viewsUnder(cell).filterIsInstance<android.widget.TextView>()
                    .firstOrNull()?.text?.toString().orEmpty()
                cell as View to label
            }
    }

    /** The action bar's own item for [id], the way its menu holds one. */
    private fun menuItem(id: Int): View? =
        viewsUnder(harness.activity.window.decorView)
            .filterIsInstance<ActionBar>()
            .firstOrNull()
            ?.createMenu()
            ?.getItem(id)

    private fun clickMenuItem(id: Int) {
        harness.layoutApp()
        val item = menuItem(id) ?: throw AssertionError("the bar has no item $id")
        println("tags: clicking the bar item $id")
        item.performClick()
        harness.idle()
    }

    /** The bar's title, which is the text upstream's own title view is drawing. */
    private fun title(): String =
        viewsUnder(harness.activity.window.decorView)
            .filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>()
            .map { it.text.toString() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
        return found
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private companion object {
        const val CATEGORY = "开发调优"
    }
}
