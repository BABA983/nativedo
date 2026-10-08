package io.github.baba983.nativedo.app

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import org.junit.Assert.assertSame
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
 * <p>The failures this pins: a tab switch that rebuilds the page (scroll position and rows lost,
 * the feed read again), a pushed screen that leaves the strip in front of the window, and a shell
 * that reserves the strip's height so the list stops above it with the window background showing
 * behind the glass.</p>
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
        site.serveFixture("u/baba.json", "user-profile.json")
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

        // Switching away and back must keep the page: the reader's rows and scroll position cannot
        // be thrown away by a tab tap, and the feed cannot be read again for the same visit.
        val topics = harness.visibleFragment()
        val feedReads = site.requestPaths.count { it.startsWith("latest.json") }
        tapTab(tabs, 2)
        harness.await("the settings screen to open") { currentFragmentName() == "SettingsFragment" }
        harness.renderApp("settings")
        assertEquals("the tab host is still the shell's only root", 1, fragmentStackSize(shell))
        tapTab(tabs, 0)
        harness.await("the topic list again") { currentFragmentName() == "TopicListFragment" }
        assertSame("the same topic page survives the switch", topics, harness.visibleFragment())
        assertEquals("and its feed is not read again for it", feedReads,
            site.requestPaths.count { it.startsWith("latest.json") })

        // Open a topic the way the list does: the pushed screen owns the window, strip included.
        shell.present(io.github.baba983.nativedo.topic.TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }
        val topic = harness.topFragment()!!
        assertTrue("the topic is drawn in the shell", topic.fragmentView.parent != null)
        assertTrue(
            "the tab host stays under it, so back returns to the feed",
            harness.fragmentStack().indexOf(topic) == harness.fragmentStack().size - 1 &&
                harness.fragmentStack().any { it.javaClass.simpleName == "NativeDoMainTabsActivity" },
        )
        shell.onBackPressed()
        harness.await("back to the tabs") { harness.topFragment() === harness.fragmentStack().first() }
        assertSame("the strip is back on the same page", topics, harness.visibleFragment())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The fourth entry: the reader's own profile, drawn as upstream's avatar tab. The failure this
     * pins: the profile being reachable only from a settings row, and the tab drawn as an icon
     * instead of the reader's own picture.
     */
    @Test
    fun theProfileTabShowsTheReadersOwnProfile() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName() == "TopicListFragment" }
        val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
            ?: throw AssertionError("no main tabs")
        assertEquals("the fourth entry is there", 4, tabs.childCount)

        val profileTab = tabs.getChildAt(3)
        assertEquals("and it is the reader's own tab", "我的", profileTab.contentDescription)
        assertTrue(
            "drawn as upstream's avatar tab, not as an icon",
            views(profileTab).any { it is org.telegram.ui.Components.BackupImageView },
        )

        tapTab(tabs, 3)
        harness.await("the reader's own profile") { currentFragmentName() == "ProfileFragment" }
        harness.layoutApp()
        harness.renderApp("home-profile")
        assertTrue(
            "the profile was read from the session's own handle",
            site.requestPaths.contains("u/baba.json"),
        )
        assertEquals("the tab host is still the shell's only root", 1, fragmentStackSize(shell))
        assertTrue("its tab is the selected one", (tabs.getChildAt(3) as GlassTabView).isTabSelected)
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
            assertTrue("system icons remain readable on the dark background",
                root.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR == 0)
            // The theme animation clears the lower fragment, so the strip the test held before the
            // switch is gone: the shell rebuilt it at the same page.
            val restored = findTabs(root) ?: throw AssertionError("the strip did not come back")
            assertTrue("the selected settings tab survives the change", (restored.getChildAt(2) as GlassTabView).isTabSelected)
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
        val tabsLocation = IntArray(2)
        tabs.getLocationInWindow(tabsLocation)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        assertEquals(
            "the navigation bar is reserved once",
            37L,
            (rootLocation[1] + root.height - (tabsLocation[1] + tabs.height)).toLong(),
        )
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
        // The reply box is now upstream's floating input island: its bottom sits the navigation
        // inset above the window edge plus the island's own bottom gap (INPUT_BUBBLE_BOTTOM), the
        // same 9dp the chat's composer floats on.
        assertEquals(
            "hiding the keyboard leaves the navigation inset and the island's own gap",
            (root.height - 37 - org.telegram.messenger.AndroidUtilities.dp(
                org.telegram.ui.Components.chat.ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM.toFloat())).toLong(),
            (position[1] + composer.height).toLong(),
        )
        shell.onBackPressed()
        harness.await("return to notifications") { fragmentStackSize(shell) == 1 }
        harness.layoutApp()
        assertEquals("the tab host is back on the notifications page",
            "NotificationsFragment", currentFragmentName())
        harness.renderApp("main-tabs-insets")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The failure: the shell handing the strip's height to the fragment layout, which then pads the
     * root at the bottom and paints the padding with the window background, so the list stops above
     * the tabs and a gray band shows behind the glass.
     */
    @Test
    fun theListReachesBehindTheTabsWithoutAReservedBand() {
        val shell = harness.bootApp()
        harness.await("the feed") { currentFragmentName() == "TopicListFragment" }
        val root = (shell.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
        harness.layoutApp()

        val container = actionBarContainer(shell)
        assertEquals("the shell reserves no band at the bottom of the root", 0L, container.paddingBottom.toLong())
        val host = harness.topFragment()?.fragmentView ?: throw AssertionError("no tab host view")
        val list = contentListOf(harness.activity) ?: throw AssertionError("no topic list")
        val listLocation = IntArray(2)
        list.getLocationInWindow(listLocation)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        assertEquals(
            "the list reaches the bottom of the window",
            (rootLocation[1] + root.height).toLong(),
            (listLocation[1] + list.height).toLong(),
        )
        assertEquals(
            "with the strip's and the navigation bar's height kept as scrollable padding",
            (org.telegram.messenger.AndroidUtilities.dp(4f) + 37 + org.telegram.messenger.AndroidUtilities.dp(72f)).toLong(),
            list.paddingBottom.toLong(),
        )
        assertFalse("and rows may draw through that padding", list.clipToPadding)
        harness.assertNoTelegramSideEffects()
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragmentName(): String? = harness.visibleFragmentName()

    private fun fragmentStackSize(shell: NativeDoActivity): Int = harness.fragmentStack().size

    private fun actionBarContainer(shell: NativeDoActivity): org.telegram.ui.ActionBar.ActionBarLayout.LayoutContainer {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell) as org.telegram.ui.ActionBar.ActionBarLayout
        return layout.containerView
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
