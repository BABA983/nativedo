package io.github.baba983.nativedo.topic

import android.app.Application
import android.view.View
import android.view.ViewGroup
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.Components.RecyclerListView

/**
 * The home overflow's new/unread entrances, end to end.
 *
 * <p>The failures this pins: an overflow row that opens nothing; a new/unread entrance that reads
 * the wrong route (the latest feed) or the reader's own route with a stale bundle; and a feed that
 * carries a category strip or tag filter the site's unfiltered routes cannot honour, which would
 * offer a filter whose tap does nothing.</p>
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
internal class HomeFeedModesTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("c/feedback/2/l/latest.json", "category-latest.json")
        site.serveFixture("c/develop/4/l/latest.json", "category-latest.json")
        site.serveFixture("new.json", "latest-topics.json")
        site.serveFixture("unread.json", "unread-topics.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theOverflowOpensTheNewAndUnreadListsAsUnfilteredFeeds() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.layoutApp()

        openHomeMenu()
        val rows = popupRowTexts()
        println("home feed modes: rows = $rows")
        assertTrue("the menu starts a new-topic read", rows.contains("新话题"))
        assertTrue("and the reader's unread list", rows.contains("未读"))

        clickPopupRow("新话题")
        harness.await("the new-topics feed to be read") { site.requestPaths.contains("new.json") }
        harness.await("the new list's rows") { rowCount() == 2 }
        harness.layoutApp()
        val newList = harness.visibleFragment() as TopicListFragment
        assertEquals("the pushed screen is in the new feed mode", "new", fragmentField(newList, "feedMode"))
        assertTrue(
            "an unfiltered feed carries no category strip",
            (fragmentField(newList, "stripHasTabs") as Boolean).not(),
        )
        assertTrue("and no tag item", fragmentField(newList, "tagItem") == null)
        harness.renderApp("home-new-topics")

        harness.topFragment()!!.finishFragment()
        harness.await("back on the home feed") {
            harness.visibleFragment() is TopicListFragment &&
                harness.fragmentStack().size == 1 &&
                fragmentField(harness.visibleFragment()!!, "feedMode") == "latest"
        }

        openHomeMenu()
        clickPopupRow("未读")
        harness.await("the unread feed to be read") { site.requestPaths.contains("unread.json") }
        harness.await("the unread list's rows") { rowCount() == 2 }
        harness.layoutApp()
        val unreadList = harness.visibleFragment() as TopicListFragment
        assertEquals("the pushed screen is in the unread feed mode", "unread", fragmentField(unreadList, "feedMode"))
        harness.renderApp("home-unread")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ the options menu

    private fun openHomeMenu() {
        val item = views(harness.visibleFragment()?.fragmentView)
            .firstOrNull { it.contentDescription == "更多" }
            ?: throw AssertionError("the home action bar has no options item")
        item.performClick()
        harness.idle()
    }

    private fun popupRowTexts(): List<String> =
        popupRows().mapNotNull { (it as? ActionBarMenuSubItem)?.textView?.text?.toString() }

    private fun clickPopupRow(text: String) {
        val row = popupRows().firstOrNull {
            it is ActionBarMenuSubItem && it.textView.text?.toString() == text
        } ?: throw AssertionError("the options menu has no \"$text\" row")
        row.performClick()
        harness.idle()
    }

    private fun popupRows(): List<View> {
        val popup = Shadows.shadowOf(harness.context as Application).latestPopupWindow
            ?: throw AssertionError("the options item showed no popup menu")
        return views(popup.contentView)
    }

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }

    private fun fragmentField(fragment: org.telegram.ui.ActionBar.BaseFragment, name: String): Any? {
        val field = TopicListFragment::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(fragment)
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)
}
