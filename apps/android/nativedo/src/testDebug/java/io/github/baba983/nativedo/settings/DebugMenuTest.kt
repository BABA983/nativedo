package io.github.baba983.nativedo.settings

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.debug.DebugTopicFixtures
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.MainTabsLayout

/**
 * The debug-only dev-tools section of the settings screen: it is there in a debug build, its
 * curated-topic rows open the topic they name, and a typed jump validates before it navigates.
 *
 * <p>Failures pinned here: the section silently missing (the regression this restores), a fixture
 * that jumps to the wrong topic, and a typed entry that navigates on text it should have refused -
 * or that silently does nothing because the app was about to write to the site.</p>
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
internal class DebugMenuTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba/bookmarks.json", "user-bookmarks.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serve(1L, "topic-detail.json")
        site.serve(DebugTopicFixtures.entries().first().topicId, "topic-detail.json")
        val floorFixture = DebugTopicFixtures.entries().first { it.floor > 0 }
        site.serve(floorFixture.topicId, "topic-detail.json")
        site.serveJson(
            "t/${floorFixture.topicId}/${floorFixture.floor}.json",
            harness.fixture("topic-detail.json"),
        )
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /** The dev-tools rows are drawn in a debug build, above the version footer. */
    @Test
    fun theDebugSectionIsThereAndShowsItsCuratedFixtures() {
        val shell = openSettings()
        val rows = rowTextsAcrossTheList()
        println("debug menu: rows = $rows")

        assertTrue("the section header is drawn", "调试" in rows)
        assertTrue("the curated-topic entry is drawn", "渲染测试帖" in rows)
        assertTrue("the typed-jump entry is drawn", "跳转话题" in rows)

        val list = contentListOf(harness.activity)!!
        list.scrollToPosition(list.adapter!!.itemCount - 1)
        harness.layoutApp()
        harness.idleClock(200)
        assertTrue("the capture includes the debug entry", "跳转话题" in rowTexts())
        harness.drawApp()
        harness.renderApp("debug-menu-section")
        harness.assertNoTelegramSideEffects()
    }

    /** A curated row opens the topic it names - here the first entry. */
    @Test
    fun aCuratedFixtureOpensTheTopicItNames() {
        val shell = openSettings()
        val fixture = DebugTopicFixtures.entries().first()

        clickNamedRow("渲染测试帖")
        harness.idle()
        val list = sheetList()

        assertEquals(
            "the sheet carries every curated fixture",
            DebugTopicFixtures.entries().size,
            list.adapter?.itemCount,
        )
        // The sheet paints through the system bar and the list reserves its height, so the gesture
        // pill never sits on the last row.
        val sheet = ShadowDialog.getLatestDialog() as org.telegram.ui.ActionBar.BottomSheet
        assertTrue("the sheet paints through the system navigation bar", sheet.drawNavigationBar)
        assertTrue(
            "the list clears the system bar at the bottom",
            list.paddingBottom >= AndroidUtilities.navigationBarHeight,
        )
        assertTrue("the list clips its rows above that padding", list.clipToPadding)

        harness.renderComposite(".amp/in/artifacts/nativedo/debug-menu-fixtures.png")

        val dialog = ShadowDialog.getLatestDialog()!!
        val title = viewsUnder(dialog.window!!.decorView).filterIsInstance<TextView>()
            .single { it.text.toString() == "渲染测试帖" }
        val first = list.findViewHolderForAdapterPosition(0)!!.itemView
        val titlePosition = IntArray(2)
        val rowPosition = IntArray(2)
        title.getLocationInWindow(titlePosition)
        first.getLocationInWindow(rowPosition)
        assertTrue("the first fixture is fully below the sheet title",
            rowPosition[1] >= titlePosition[1] + title.height)

        // Position 0 is the first curated entry, whose row id is the fixture base.
        list.clickItem(null, 0)
        harness.await("the fixture's topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the curated row opened the fixture's own topic", fixture.topicId, topicIdOf(shell))
        assertEquals("no floor was named, so the topic opens at its top", 0, topicFloorOf(shell))
        harness.assertNoTelegramSideEffects()
    }

    /** A curated entry can name a floor: the row lands on that post, not on the topic's head. */
    @Test
    fun aCuratedFixtureOpensAtTheFloorItNames() {
        val shell = openSettings()
        val fixture = DebugTopicFixtures.entries().first { it.floor > 0 }

        clickNamedRow("渲染测试帖")
        harness.idle()
        val list = sheetList()
        val index = DebugTopicFixtures.entries().indexOf(fixture)
        list.clickItem(null, index)
        harness.await("the fixture's topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the curated row opened its own topic", fixture.topicId, topicIdOf(shell))
        assertEquals("and the floor the entry names", fixture.floor, topicFloorOf(shell))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The typed jump validates before it navigates: a foreign origin, another scheme, a zero id and
     * a non-numeric entry all leave the dialog open and nothing on the stack, while a real entry
     * opens the topic (on the floor a link names).
     */
    @Test
    fun aTypedJumpValidatesBeforeItNavigates() {
        val shell = openSettings()
        clickNamedRow("跳转话题")
        harness.idle()
        val dialog = latestAlertDialog()
        val input = viewsUnder(dialog.window!!.decorView).filterIsInstance<EditTextBoldCursor>().first()

        for (invalid in listOf("tg://resolve?domain=telegram", "https://evil.example.com/t/1", "0", "abc")) {
            input.setText(invalid)
            pressDialogButton(dialog, "打开")
            harness.idle()
            assertTrue("the dialog stays open for \"$invalid\"", dialog.isShowing)
            assertFalse(
                "the invalid entry \"$invalid\" did not open a topic",
                harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
            )
        }

        harness.renderComposite(".amp/in/artifacts/nativedo/debug-menu-input.png")

        // A full app-owned alias, with a floor: the same rule the deeplink entry uses.
        input.setText("nativedo://topic/1/3")
        pressDialogButton(dialog, "打开")
        harness.await("the typed topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the typed link opened its topic", 1L, topicIdOf(shell))
        assertEquals("and the floor it named", 3, topicFloorOf(shell))

        // No debug jump writes to the site.
        assertTrue(
            "the debug jumps only GET",
            site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun openSettings(): NativeDoActivity {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings screen") { harness.visibleFragmentName() == "SettingsFragment" }
        harness.layoutApp()
        return shell
    }

    private fun topicIdOf(shell: NativeDoActivity): Long =
        harness.topFragment()!!.arguments?.getLong("topic_id", 0L) ?: 0L

    private fun topicFloorOf(shell: NativeDoActivity): Int =
        harness.topFragment()!!.arguments?.getInt("floor", 0) ?: 0

    private fun sheetList(): RecyclerListView {
        val dialog = ShadowDialog.getLatestDialog() ?: throw AssertionError("the fixture sheet never showed")
        return viewsUnder(dialog.window!!.decorView).filterIsInstance<RecyclerListView>().first()
    }

    private fun latestAlertDialog(): AlertDialog =
        ShadowDialog.getLatestDialog() as? AlertDialog ?: throw AssertionError("no dialog")

    private fun pressDialogButton(dialog: AlertDialog, label: String) {
        val button = viewsUnder(dialog.window!!.decorView)
            .filterIsInstance<TextView>()
            .lastOrNull { it.text.toString() == label }
            ?: throw AssertionError("the dialog has no $label button")
        button.performClick()
        harness.idle()
    }

    private fun tapTab(index: Int) {
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<MainTabsLayout>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
    }

    private fun rowTexts(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { index -> textsOf(list.getChildAt(index)) }
    }

    private fun rowTextsAcrossTheList(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        val count = list.adapter?.itemCount ?: 0
        val texts = LinkedHashSet<String>()
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            texts += rowTexts()
        }
        list.scrollToPosition(0)
        harness.layoutApp()
        return texts.toList()
    }

    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        val count = list.adapter?.itemCount ?: 0
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            val child = (0 until list.childCount).map { list.getChildAt(it) }
                .firstOrNull { label in textsOf(it) }
            if (child != null) {
                list.clickItem(child, list.getChildAdapterPosition(child))
                harness.idle()
                return
            }
        }
        throw AssertionError("no $label row")
    }

    private fun textsOf(view: View): List<String> {
        val texts = viewsUnder(view)
        return texts.filterIsInstance<TextView>().map { it.text.toString() } +
            texts.filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>().mapNotNull { it.text?.toString() } +
            texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>().mapNotNull { it.text?.toString() }
    }

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
}
