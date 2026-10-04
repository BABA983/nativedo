package io.github.baba983.nativedo.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.DialogsActivity

/**
 * The home action bar's options menu: upstream's own overflow item over linux.do's three entries.
 *
 * <p>Failure modes pinned here: an item that opens nothing (the bookmarks and new-topic rows run the
 * same actions as the screens that already have them, and the theme row names the mode the tap
 * moves to), and a theme switch that rebuilds the tab host without leaving the old views'
 * message-bus listeners collecting: the host and its pages keep their views across a theme switch,
 * so the rebuilt views attach a second listener unless the first one is let go.</p>
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
internal class HomeMenuTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serveFixture("u/baba/bookmarks.json", "user-bookmarks.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /** The menu's rows open what they name, through the popup the options item shows. */
    @Test
    fun theOptionsMenuOpensTheScreensItsItemsName() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()

        openHomeMenu()
        val rows = popupRowTexts()
        println("home menu: rows = $rows")
        val modeRow = if (Theme.isCurrentThemeDark()) "日间模式" else "夜间模式"
        assertTrue("the theme row names the mode the tap moves to", rows.contains(modeRow))
        assertTrue("the menu starts a topic", rows.contains("发新帖"))
        assertTrue("and opens the reader's bookmarks", rows.contains("书签"))

        clickPopupRow("书签")
        harness.await("the bookmark list") { harness.visibleFragmentName() == "BookmarksFragment" }
        shell.onBackPressed()
        harness.await("back on the feed") {
            harness.fragmentStack().size == 1 && harness.visibleFragmentName() == "TopicListFragment"
        }

        openHomeMenu()
        clickPopupRow("发新帖")
        harness.await("the form a new topic starts in") { harness.visibleFragmentName() == "NewTopicFragment" }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The menu's theme switch, driven through the notification the item posts (the item's own click
     * carries the icon-centre position, which the host turns into a circular reveal and the device
     * pass checks; this test posts it without a position so the rebuild and the listener bookkeeping
     * are what is exercised). The failures: a switch that does not build the host's pages again
     * (they would keep the old theme's colours), one that restarts the message-bus session, and one
     * that leaves the previous views' listeners attached.
     */
    @Test
    fun theMenuThemeSwitchRebuildsTheHostWithoutLeavingItsListenersBehind() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val container = site.authenticatedContainer(harness.context, scope)
        NativeDo.setContainerFactory { container }

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.await("the bus to start") { container.messageBusSession.clientId != null }
        harness.layoutApp()
        harness.idle()

        val listenersBefore = settleListenerCount(scope)
        assertTrue("the host and the page are listening to the bus", listenersBefore >= 2)
        val clientId = container.messageBusSession.clientId
        val pageView = harness.visibleFragment()!!.fragmentView

        val toDark = !Theme.isCurrentThemeDark()
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
            org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
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
            harness.await("the page's views to be built again") {
                harness.layoutApp()
                harness.visibleFragmentName() == "TopicListFragment" &&
                    harness.visibleFragment()!!.fragmentView !== pageView
            }
            assertEquals(
                "the switch does not restart the message-bus session",
                clientId,
                container.messageBusSession.clientId,
            )
            harness.await("the old views' listeners to let go") {
                listenerCount(scope) <= listenersBefore
            }
            assertEquals("the rebuilt views attach one listener instead of two", listenersBefore, listenerCount(scope))
            harness.assertNoTelegramSideEffects()
        } finally {
            Theme.applyTheme(Theme.getTheme("Blue"), false, false)
            DialogsActivity.switchingTheme = false
        }
    }

    // ------------------------------------------------------------------ the options menu

    private fun openHomeMenu() {
        val item = views(harness.visibleFragment()?.fragmentView)
            .firstOrNull { it.contentDescription == "更多" }
            ?: throw AssertionError("the home action bar has no options item")
        item.performClick()
        harness.idle()
    }

    /** The rows of the popup the options item just showed, by their labels. */
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

    // ------------------------------------------------------------------ the bus listeners

    private fun listenerCount(scope: CoroutineScope): Int =
        (scope.coroutineContext[Job] ?: throw AssertionError("the test scope has no job")).children.count()

    /** The count once it stops moving: the bootstrap's own transient jobs are gone by then. */
    private fun settleListenerCount(scope: CoroutineScope): Int {
        var last = -1
        harness.await("the session's listeners to settle") {
            val now = listenerCount(scope)
            val stable = now == last
            last = now
            stable
        }
        return last
    }

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }
}
