package io.github.baba983.nativedo.app

import android.os.SystemClock
import android.view.MotionEvent
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.FilledTabsView
import org.telegram.ui.ThemeActivity

/**
 * The home structure: two entries, upstream's tab strip, and the topic list as the first screen.
 *
 * <p>The failure this pins: switching tabs must replace the root rather than push on top of it, or
 * a reader who opens a topic and then taps settings ends up with a topic behind the back button and
 * a settings screen that cannot be left.</p>
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
internal class HomeTabsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(1L, "topic-detail.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theSettingsTabOpensUpstreamsThemePage() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName() == "TopicListFragment" }
        harness.renderApp("home-tabs")

        val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
        assertTrue("the shell shows upstream's tab strip", tabs != null)
        assertEquals("with both entries", 2, tabCount(tabs!!))

        // Open a topic the way the list does, then switch tabs: the topic must not survive behind
        // the settings screen, which is what a pushed-on-top tab would leave.
        shell.present(io.github.baba983.nativedo.topic.TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { fragmentStackSize(shell) == 2 }
        tapTab(tabs, 1)
        harness.await("the theme page to open") { currentFragmentName() == "ThemeActivity" }
        harness.renderApp("settings-theme")

        val themeField = ThemeActivity::class.java.getDeclaredField("currentType").apply { isAccessible = true }
        assertEquals(
            "the settings tab is upstream's night-theme page",
            ThemeActivity.THEME_TYPE_NIGHT,
            themeField.get(currentFragment(shell)) as Int,
        )
        assertEquals(
            "the tab replaced the root instead of stacking on it",
            1,
            fragmentStackSize(shell),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragmentName(): String? = currentFragment(harness.activity as NativeDoActivity)
        ?.javaClass?.simpleName

    private fun currentFragment(shell: NativeDoActivity): Any? {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        val stack = stackField.get(layout) as List<*>
        return stack.lastOrNull()
    }

    private fun fragmentStackSize(shell: NativeDoActivity): Int {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        return (stackField.get(layout) as List<*>).size
    }

    private fun tabCount(tabs: FilledTabsView): Int {
        val field = FilledTabsView::class.java.getDeclaredField("tabs").apply { isAccessible = true }
        return (field.get(tabs) as Array<*>).size
    }

    private fun findTabs(view: View?): FilledTabsView? {
        if (view is FilledTabsView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTabs(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Taps the middle of the tab at [index]; the strip selects on the first touch inside it. */
    private fun tapTab(tabs: FilledTabsView, index: Int) {
        harness.idle()
        val field = FilledTabsView::class.java.getDeclaredField("bounds").apply { isAccessible = true }
        val bounds = field.get(tabs) as Array<*>
        val rect = bounds[index] as android.graphics.RectF
        val x = rect.centerX()
        val y = rect.centerY()
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        tabs.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        tabs.dispatchTouchEvent(up)
        harness.idle()
    }
}
