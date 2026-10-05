package io.github.baba983.nativedo.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.ActionBar.Theme

/**
 * The shell's system-bar contract, the half that is derivable without a device: which state the
 * bars are computed from, and that the process keeps the state upstream's code reads.
 *
 * <p>Two failure modes, both invisible in a screenshot of a healthy run:</p>
 * <ul>
 *   <li>the bars are computed once and never again - opening a screen whose own answer differs
 *       leaves the previous tint/band up, and a theme switch leaves the icons at the old theme's
 *       contrast;</li>
 *   <li>the shell never learns the screen state upstream's theme and media code reads, so
 *       {@code ApplicationLoader.isScreenOn} keeps its field default for the process's life.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = NativeDoApplication::class,
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
internal class ShellSystemBarsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> AppContainer(context) }
        site.stop()
    }

    /**
     * Upstream's own {@code ScreenReceiver} answers screen on/off; the shell's part of it must too,
     * or every consumer of {@code ApplicationLoader.isScreenOn} reads a value that never changes.
     */
    @Test
    fun theScreenStateBroadcastsReachUpstreamsFlag() {
        harness.bootApp()
        harness.await("the shell root") { harness.visibleFragment() != null }

        val context = harness.context
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        harness.idle()
        assertFalse(
            "screen off must reach the flag upstream reads",
            ApplicationLoader.isScreenOn,
        )

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        harness.idle()
        assertTrue(
            "screen on must reach it again",
            ApplicationLoader.isScreenOn,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The status-bar tint and the navigation-bar band are answers of the fragment on top, not of
     * the fragment the shell happened to start with: each push recomputes them, the theme switch
     * recomputes them again, and closing the pushed screens hands them back to the root.
     */
    @Test
    fun theBarsFollowTheTopFragmentAndTheThemeSwitch() {
        val shell = harness.bootApp()
        harness.await("the shell root") { harness.visibleFragment() != null }
        harness.layoutApp()

        val root = harness.visibleFragment()!!
        assertEquals(
            "the boot tint is the root fragment's own answer",
            root.isLightStatusBar(),
            lightStatusBar(),
        )

        val forced = ForcedBarFragment(light = !root.isLightStatusBar(), navigationColor = NAV_COLOR)
        harness.present(forced)
        assertEquals(
            "a pushed screen's light-status-bar answer wins",
            forced.isLightStatusBar(),
            lightStatusBar(),
        )
        assertEquals(
            "and its navigation-bar colour is the band that is drawn",
            NAV_COLOR,
            navbarBandColor(),
        )

        assertNotEquals(
            "the forced tint differs from the root's, so the change above was observable",
            root.isLightStatusBar(),
            forced.isLightStatusBar(),
        )

        shell.onBackPressed()
        harness.await("back on the root") {
            harness.fragmentStack().size == 1
        }
        harness.idle()
        val rootAgain = harness.visibleFragment()!!
        assertEquals(
            "closing the screen hands the band back to the root's colour",
            rootAgain.getNavigationBarColor(),
            navbarBandColor(),
        )
        assertEquals(
            "and the tint back to the root's answer",
            rootAgain.isLightStatusBar(),
            lightStatusBar(),
        )

        // The theme switch recolours everything; the icons must follow the theme the reader now
        // sees, not the one the flags were computed for.
        val lightBeforeSwitch = rootAgain.isLightStatusBar()
        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            if (toDark) Theme.getTheme("Dark Blue") else Theme.getTheme("Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            harness.idle()
            val afterSwitch = harness.visibleFragment()!!
            println(
                "shell bars: theme dark=${Theme.isCurrentThemeDark()} " +
                    "root light before=${lightBeforeSwitch} after=${afterSwitch.isLightStatusBar()}",
            )
            assertEquals(
                "after the switch the tint is the top fragment's new answer",
                afterSwitch.isLightStatusBar(),
                lightStatusBar(),
            )
            assertNotEquals(
                "and that answer actually changed with the theme",
                lightBeforeSwitch,
                afterSwitch.isLightStatusBar(),
            )
        } finally {
            Theme.applyTheme(Theme.getTheme("Blue"), false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /** The flag upstream's {@code AndroidUtilities.setLightStatusBar} writes on the decor view. */
    private fun lightStatusBar(): Boolean {
        val flags = (harness.activity as NativeDoActivity).window.decorView.systemUiVisibility
        return flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
    }

    /** The colour the navigation-bar band is painted with right now. */
    private fun navbarBandColor(): Int {
        val field = NativeDoActivity::class.java.getDeclaredField("drawerLayoutContainer")
            .apply { isAccessible = true }
        val container = field.get(harness.activity) as DrawerLayoutContainer
        return container.internalNavbarPaint.color
    }

    /**
     * A screen with its own answers, the way upstream's fragments carry theirs: a fixed
     * status-bar tint and a navigation-bar colour of its own.
     */
    private class ForcedBarFragment(
        private val light: Boolean,
        private val navigationColor: Int,
    ) : BaseFragment() {
        override fun isLightStatusBar(): Boolean = light

        override fun getNavigationBarColor(): Int = navigationColor

        override fun createView(context: Context): View = FrameLayout(context)
    }

    private companion object {
        const val NAV_COLOR = 0xFF123456.toInt()
    }
}
