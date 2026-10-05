package io.github.baba983.nativedo.settings

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.MainTabsLayout

/**
 * The linux.do preference screens.
 *
 * <p>The failure this pins: a switch on a server-backed setting is a request, not a local flag.
 * When linux.do refuses the write, the switch has to go back - a client that leaves it flipped
 * tells the reader the site holds a setting it answered 422 to - and the refusal has to be named,
 * not swallowed.</p>
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
internal class PreferencesTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba.json", "user-preferences.json")
        NativeDo.setContainerFactory { context ->
            site.authenticatedContainer(context).also { container = it }
        }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aRefusedPreferenceWritePutsTheSwitchBackAndSaysWhy() {
        val shell = bootToSettings()
        clickNamedRow("资料可见性与私信")
        harness.await("the privacy preferences") { currentFragmentName(shell) == "PreferencesFragment" }
        harness.layoutApp()
        val before = checkRow("隐藏公开资料与在线状态")
            ?: throw AssertionError("the privacy screen draws no visibility switch")
        assertFalse("the switch starts from the site's own value", before.isChecked)

        // The screen has loaded; from here the site answers the write with a refusal.
        site.serveResponse("u/baba.json") {
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["资料设置被拒绝"],"error_type":"invalid_parameters"}""")
        }

        clickRow("隐藏公开资料与在线状态")
        harness.await("the refused write to reach the site") {
            site.recordedRequests.any { it == "PUT /u/baba.json" }
        }
        println("preferences: writes = " + site.requestBodiesFor("u/baba.json"))
        assertTrue(
            "the write carries the site's own field name and encoding",
            site.requestBodyFor("u/baba.json")!!.contains("hide_profile_and_presence=true"),
        )
        assertEquals(
            "and the token the site asks writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("u/baba.json"),
        )

        harness.await("the refusal dialog") {
            (ShadowDialog.getLatestDialog() as? AlertDialog) != null
        }
        harness.layoutApp()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val texts = viewsUnder(dialog.window!!.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }
        println("preferences: the refusal dialog says $texts")
        assertTrue("the refusal is named", texts.any { "没能保存" in it })
        assertTrue("with the site's own message", texts.any { "资料设置被拒绝" in it })

        val after = checkRow("隐藏公开资料与在线状态")
            ?: throw AssertionError("the switch row disappeared")
        assertFalse("a refused write leaves the switch where the site has it", after.isChecked)
        assertEquals(
            "and only one write went out",
            1,
            site.recordedRequests.count { it == "PUT /u/baba.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ shell traversal

    private fun bootToSettings(): NativeDoActivity {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<MainTabsLayout>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val tab = tabs.getChildAt(2)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        return shell
    }

    private fun currentFragmentName(shell: NativeDoActivity): String? = harness.visibleFragmentName()

    private fun textsOf(view: View): List<String> {
        val texts = viewsUnder(view)
        return texts.filterIsInstance<TextView>().map { it.text.toString() } +
            texts.filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>()
                .mapNotNull { it.text?.toString() } +
            texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                .mapNotNull { it.text?.toString() }
    }

    /** The switch row labelled [label] on the preference screen, as a cell. */
    private fun checkRow(label: String): TextCheckCell? {
        val list = contentListOf(harness.activity) ?: return null
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<TextCheckCell>()
            .firstOrNull { label in textsOf(it) }
    }

    /** A tap on the row labelled [label], scrolling the list down when the row is below the fold. */
    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list on the screen")
        fun find(): View? = (0 until list.childCount).map { list.getChildAt(it) }
            .firstOrNull { label in textsOf(it) }
        var child = find()
        if (child == null) {
            list.scrollToPosition((list.adapter?.itemCount ?: 1) - 1)
            harness.layoutApp()
            child = find()
        }
        val row = child ?: throw AssertionError("no $label row")
        list.clickItem(row, list.getChildAdapterPosition(row))
        harness.idle()
    }

    private fun clickRow(label: String) = clickNamedRow(label)

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
