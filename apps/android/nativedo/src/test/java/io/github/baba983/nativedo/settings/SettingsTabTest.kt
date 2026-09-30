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
import org.telegram.ui.Components.FilledTabsView
import org.telegram.ui.Components.RecyclerListView

/**
 * The settings tab.
 *
 * <p>The failure this pins: a reader has one place for what they change about the app, and the
 * account lives in it. The tab has to be this client's own screen - upstream's appearance page is
 * one row in, not the whole tab - built from upstream's cell styles, and leaving the account has to
 * actually leave it: the cookies the session is made of are gone before the login page opens, or
 * that page opens with the reader still signed in.</p>
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
internal class SettingsTabTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba/bookmarks.json", "user-bookmarks.json")
        site.serveFixture("u/baba.json", "user-profile.json")
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
    fun theTabIsOurOwnScreenAndItsRowsOpenTheThingsTheyName() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }

        tapTab(2)
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        harness.drawApp()
        val rows = rowTexts()
        println("settings: rows = $rows")
        assertTrue("the settings tab is this client's own screen", "外观" in rows)
        assertTrue("and it carries the appearance entry", "主题" in rows)
        assertTrue("the bookmarks entry is kept here as well", "书签" in rows)
        assertTrue("with the account the session belongs to", rows.any { it == "@baba" })
        harness.renderApp("settings")
        harness.assertNoTelegramSideEffects()

        clickRow(1)
        harness.await("upstream's appearance page") { currentFragmentName(shell) == "ThemeActivity" }
        val themeField = org.telegram.ui.ThemeActivity::class.java
            .getDeclaredField("currentType").apply { isAccessible = true }
        assertEquals(
            "the appearance row opens upstream's own page for it",
            org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC,
            themeField.get(currentFragment(shell)) as Int,
        )
        harness.layoutApp()
        harness.renderApp("settings-theme")

        back()
        harness.await("back on the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        clickRow(3)
        harness.await("the bookmark list") { currentFragmentName(shell) == "BookmarksFragment" }

        back()
        harness.await("back on the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun leavingTheAccountClearsTheSessionBeforeTheLoginPageOpens() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        assertTrue("the reader is signed in to start with", container.cookieStore.hasAuthenticatedCookies())

        clickRow(6)
        val confirm = ShadowDialog.getLatestDialog() as? AlertDialog
            ?: throw AssertionError("leaving the account asked nothing")
        println("settings: the dialog says " + dialogTexts(confirm))
        confirmButton(confirm, "退出登录")

        harness.await("the login screen") { currentFragmentName(shell) == "LoginWebViewFragment" }
        assertFalse(
            "the cookies the session was made of are gone",
            container.cookieStore.hasAuthenticatedCookies(),
        )
        assertEquals(
            "the signed-out account is not remembered either",
            null,
            container.sessionStore.snapshot(),
        )
        harness.layoutApp()
        harness.renderApp("settings-signed-out")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragmentName(shell: NativeDoActivity): String? = currentFragment(shell)
        ?.javaClass?.simpleName

    private fun currentFragment(shell: NativeDoActivity): Any? {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        val stack = stackField.get(layout) as List<*>
        return stack.lastOrNull()
    }

    private fun back() {
        (harness.activity as NativeDoActivity).onBackPressed()
        harness.idle()
    }

    private fun tapTab(index: Int) {
        // The strip's touch targets are laid down while it draws, so it has to have drawn once.
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<FilledTabsView>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val field = FilledTabsView::class.java.getDeclaredField("bounds").apply { isAccessible = true }
        val bounds = field.get(tabs) as Array<*>
        val rect = bounds[index] as android.graphics.RectF
        val x = rect.centerX()
        val y = rect.centerY()
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
    }

    /**
     * The rows the settings list draws, top to bottom. A row's value is upstream's animated text
     * view, which paints its own glyphs rather than being a {@link TextView}, so both are read.
     */
    private fun rowTexts(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { index ->
            val texts = viewsUnder(list.getChildAt(index))
            texts.filterIsInstance<TextView>().map { it.text.toString() } +
                texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                    .mapNotNull { it.text?.toString() }
        }.filter { it.isNotEmpty() }
    }

    /** A tap on the row at the adapter's [position], the way the list hands one over. */
    private fun clickRow(position: Int) {
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        harness.layoutApp()
        val first = (list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.findFirstVisibleItemPosition() ?: 0
        val child = list.getChildAt(position - first)
            ?: throw AssertionError("no row drew at $position")
        list.clickItem(child, position)
        harness.idle()
    }

    private fun dialogTexts(dialog: AlertDialog): List<String> =
        viewsUnder(dialog.window!!.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }
            .filter { it.isNotEmpty() }

    /** The dialog's own button with that label: the reader's answer, not the title that says it. */
    private fun confirmButton(dialog: AlertDialog, label: String) {
        val button = viewsUnder(dialog.window!!.decorView)
            .filterIsInstance<TextView>()
            .lastOrNull { it.text.toString() == label }
            ?: throw AssertionError("the dialog has no $label button")
        println("settings: pressing " + button.text + " (" + button.javaClass.simpleName + ")")
        button.performClick()
        harness.idle()
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
