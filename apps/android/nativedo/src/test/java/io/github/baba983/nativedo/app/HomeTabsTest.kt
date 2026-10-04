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
import org.telegram.ui.MainTabsLayout
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.glass.GlassTabView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The home structure: four entries, upstream's tab strip, and the topic list as the first screen.
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
    fun theSettingsTabOpensOurOwnSettingsScreen() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName() == "TopicListFragment" }
        harness.renderApp("home-tabs")

        val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
        assertTrue("the shell shows upstream's tab strip", tabs != null)
        assertEquals("with all four entries", 4, tabs!!.childCount)
        assertTrue("the topic tab is selected", (tabs.getChildAt(0) as GlassTabView).isTabSelected)

        // Open a topic the way the list does, then switch tabs: the topic must not survive behind
        // the settings screen, which is what a pushed-on-top tab would leave.
        shell.present(io.github.baba983.nativedo.topic.TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { fragmentStackSize(shell) == 2 }
        // The topic screen owns the whole window: the strip slides away with it.
        println("tabs: alpha=" + tabs.alpha + " translationY=" + tabs.translationY)
        assertTrue(
            "the tab strip is hidden while a topic is open",
            tabs.alpha == 0f && tabs.translationY > 0f,
        )
        tapTab(tabs, 2)
        harness.await("the settings screen to open") { currentFragmentName() == "SettingsFragment" }
        harness.renderApp("settings")

        assertEquals(
            "the tab replaced the root instead of stacking on it",
            1,
            fragmentStackSize(shell),
        )
        assertTrue("and the strip is back", tabs.alpha == 1f && tabs.translationY == 0f)

        // The fourth tab is the reader's own profile, and it too is a root: tapping it must not
        // leave the settings screen behind the back button.
        tapTab(tabs, 3)
        harness.await("the reader's own profile") { currentFragmentName() == "ProfileFragment" }
        assertEquals("the profile tab replaced the root as well", 1, fragmentStackSize(shell))
        assertTrue("and the avatar tab is selected",
            (tabs.getChildAt(3) as GlassTabView).isTabSelected)
        harness.assertNoTelegramSideEffects()
    }

    /** The drawer's default black inset paint must not turn a light page's glass backdrop black. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theLightThemeAlsoPaintsTheNavigationSafeAreaLight() {
        val shell = harness.bootApp()
        harness.await("the feed") { currentFragmentName() == "TopicListFragment" }
        val root = (shell.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
        val screenshot = android.graphics.BitmapFactory.decodeFile(harness.renderApp("main-tabs-light-safe-area").path)
        val safeArea = screenshot.getPixel(2, screenshot.height - 2)
        assertTrue("the light page extends to the system gesture area",
            android.graphics.Color.red(safeArea) > 200 && android.graphics.Color.green(safeArea) > 200 &&
                android.graphics.Color.blue(safeArea) > 200)
        harness.assertNoTelegramSideEffects()
    }

    /** Upstream's theme selector emits an event; without a host listener the UI never changes. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theUpstreamThemeSelectorUpdatesThePageTabsAndSystemBars() {
        val shell = harness.bootApp()
        harness.await("the feed") { currentFragmentName() == "TopicListFragment" }
        harness.layoutApp()
        val root = shell.findViewById<ViewGroup>(android.R.id.content)
        val tabs = findTabs(root) ?: throw AssertionError("no main tabs")
        tapTab(tabs, 2)
        harness.await("settings") { currentFragmentName() == "SettingsFragment" }
        val original = org.telegram.ui.ActionBar.Theme.getCurrentTheme()
        shell.present(org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC))
        harness.await("appearance") { currentFragmentName() == "ThemeActivity" }
        harness.layoutApp()
        val selector = views(root).filterIsInstance<org.telegram.ui.Cells.TextCell>().single {
            it.textView.text == org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SettingsSwitchToNightMode)
        }
        try {
            selector.performClick()
            harness.idleClock(400)
            assertTrue("the selector's event applies its theme", org.telegram.ui.ActionBar.Theme.isCurrentThemeDark())
            shell.onBackPressed()
            harness.await("settings in the new theme") { fragmentStackSize(shell) == 1 }
            val image = android.graphics.BitmapFactory.decodeFile(harness.renderApp("main-tabs-night").path)
            val background = image.getPixel(2, image.height - 2)
            assertTrue("the page and glass backdrop stay dark at the bottom",
                android.graphics.Color.red(background) < 100 && android.graphics.Color.green(background) < 100 &&
                    android.graphics.Color.blue(background) < 100)
            assertTrue("the selected settings tab survives the change", (tabs.getChildAt(2) as GlassTabView).isTabSelected)
            assertTrue("system icons remain readable on the dark background",
                root.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR == 0)
            assertTrue("a theme change cannot write to the forum",
                site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") })
            harness.assertNoTelegramSideEffects()
        } finally {
            org.telegram.ui.ActionBar.Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
    }

    /** Asymmetric cutouts and an IME must not cover tabs/composer or reserve the navigation bar twice. */
    @Test
    fun systemInsetsAndKeyboardLeaveTheTabsAndReplyReachable() {
        val shell = harness.bootApp()
        harness.await("the feed") { currentFragmentName() == "TopicListFragment" }
        val root = (shell.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        val tabs = findTabs(root) ?: throw AssertionError("no upstream main tabs")
        val system = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 31, 0, 0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 31, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(17, 0, 29, 0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(), Insets.of(17, 0, 29, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, system)
        harness.layoutApp()
        assertTrue("the pill clears the left cutout", tabs.left >= 17)
        assertTrue("the pill clears the right cutout", tabs.right <= root.width - 29)
        assertEquals("the navigation bar is reserved once", 37, root.height - tabs.bottom)
        tapTab(tabs, 1)
        harness.await("notifications") { currentFragmentName() == "NotificationsFragment" }
        assertTrue("selection follows the navigation", (tabs.getChildAt(1) as GlassTabView).isTabSelected)

        shell.present(io.github.baba983.nativedo.topic.TopicFragment.forTopic(1L, "A topic"))
        harness.await("reply screen") { fragmentStackSize(shell) == 2 }
        val keyboard = WindowInsetsCompat.Builder(system)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 260))
            .setVisible(WindowInsetsCompat.Type.ime(), true).build()
        ViewCompat.dispatchApplyWindowInsets(root, keyboard)
        harness.layoutApp()
        val composer = views(root).filterIsInstance<ChatActivityEnterView>().single()
        val position = IntArray(2)
        composer.getLocationInWindow(position)
        assertTrue("the reply stays above the keyboard", position[1] + composer.height <= root.height - 260)
        ViewCompat.dispatchApplyWindowInsets(root, system)
        harness.layoutApp()
        composer.getLocationInWindow(position)
        assertEquals("hiding the keyboard leaves only the navigation inset", root.height - 37,
            position[1] + composer.height)
        shell.onBackPressed()
        harness.await("return to notifications") { fragmentStackSize(shell) == 1 }
        harness.layoutApp()
        assertTrue("tabs return after leaving the reply", tabs.alpha == 1f && tabs.translationY == 0f)
        harness.renderApp("main-tabs-insets")
        harness.assertNoTelegramSideEffects()
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragmentName(): String? = currentFragment(harness.activity as NativeDoActivity)
        ?.javaClass?.simpleName

    private fun currentFragment(shell: NativeDoActivity): Any? {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.fragmentStack.lastOrNull()
    }

    private fun fragmentStackSize(shell: NativeDoActivity): Int {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.fragmentStack.size
    }

    private fun findTabs(view: View?): MainTabsLayout? {
        if (view is MainTabsLayout) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTabs(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Taps the real upstream child through the strip's touch dispatch. */
    private fun tapTab(tabs: MainTabsLayout, index: Int) {
        harness.idle()
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        tabs.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        tabs.dispatchTouchEvent(up)
        harness.idle()
    }
}
