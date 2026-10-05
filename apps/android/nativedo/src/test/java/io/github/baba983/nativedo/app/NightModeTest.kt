package io.github.baba983.nativedo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.glass.GlassTabView
import org.telegram.ui.DialogsActivity
import org.telegram.ui.MainTabsLayout

/**
 * Night mode on the shell's always-visible chrome: the glass tab strip's capsule.
 *
 * <p>The failure this pins: the day/night switch recoloured the strip's drawable and its tab
 * labels but never repainted the view the capsule is recorded in, so on the device the capsule
 * kept drawing the light theme's glass - white labels on a white pill - until something else
 * happened to invalidate it. Upstream's own {@code MainTabsActivity} repaints the strip in
 * `blur3_updateColors` (1214-1231) next to `tabsViewBackground.updateColors()`, and this host now
 * mirrors that; the switch both ways must also keep the very same strip and tabs.</p>
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
internal class NightModeTest {

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

    /**
     * The strip's capsule follows the switch in place, both ways: its drawable changes colour, the
     * view that records the capsule is repainted, and the strip and its tabs are the same views.
     */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theTabCapsuleFollowsTheDayNightSwitchInPlace() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()
        harness.idle()

        val host = harness.topFragment() as NativeDoMainTabsActivity
        val tabsView = hostField(host, "tabsView") as MainTabsLayout
        val tabsBefore = (hostField(host, "tabs") as Array<GlassTabView>).copyOf()

        val lightCapsule = capsuleColor(hostField(host, "tabsViewBackground")!!)

        // Arm the check before the notification so a synchronous dispatch cannot hide it.
        shadowOf(tabsView).clearWasInvalidated()
        switchTheme("Dark Blue", toDark = true)
        val darkCapsule = capsuleColor(hostField(host, "tabsViewBackground")!!)

        assertTrue(
            "the strip that paints the capsule was asked to repaint",
            shadowOf(tabsView).wasInvalidated(),
        )
        assertSame("the same strip survives the switch", tabsView, hostField(host, "tabsView"))
        val tabsAfter = hostField(host, "tabs") as Array<GlassTabView>
        for (index in tabsBefore.indices) {
            assertSame("tab $index survives the switch", tabsBefore[index], tabsAfter[index])
        }
        assertNotEquals("the capsule's colour changed with the theme", lightCapsule, darkCapsule)
        val darkPixel = capsulePixel(shell)
        assertTrue("the capsule drew the night theme's glass (was ${hex(darkPixel)})", triSum(darkPixel) < 260)

        // And back: the same repaint has to bring the light glass back in place.
        shadowOf(tabsView).clearWasInvalidated()
        switchTheme("Blue", toDark = false)
        val restoredCapsule = capsuleColor(hostField(host, "tabsViewBackground")!!)
        assertTrue("the strip repaints on the way back too", shadowOf(tabsView).wasInvalidated())
        assertNotEquals("the capsule is the day theme's glass again", darkCapsule, restoredCapsule)
        val restoredPixel = capsulePixel(shell)
        assertTrue("the capsule drew light again (was ${hex(restoredPixel)})", triSum(restoredPixel) > 480)

        harness.assertNoTelegramSideEffects()
    }

    private fun switchTheme(themeName: String, toDark: Boolean) {
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(themeName),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the $themeName theme") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            harness.layoutApp()
            harness.idle()
        } finally {
            DialogsActivity.switchingTheme = false
        }
    }

    /** The capsule's drawn fill between two tabs, away from every glyph. */
    private fun capsulePixel(shell: NativeDoActivity): Int {
        val root = shell.findViewById<ViewGroup>(android.R.id.content)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 37))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
        harness.layoutApp()
        val tabsView = views(root).filterIsInstance<MainTabsLayout>().first()
        val first = tabsView.getChildAt(0)
        val location = IntArray(2)
        tabsView.getLocationInWindow(location)
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val x = (location[0] + first.right - org.telegram.messenger.AndroidUtilities.dp(2f)).coerceIn(0, bitmap.width - 1)
        val y = (location[1] + (first.top + first.bottom) / 2).coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    private fun triSum(color: Int) = Color.red(color) + Color.green(color) + Color.blue(color)

    private fun capsuleColor(drawable: Any): Int {
        var type: Class<*>? = drawable.javaClass
        while (type != null) {
            try {
                return type.getDeclaredField("backgroundColor").apply { isAccessible = true }.getInt(drawable)
            } catch (ignored: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw AssertionError("no backgroundColor on ${drawable.javaClass}")
    }

    private fun hostField(host: NativeDoMainTabsActivity, name: String): Any? =
        host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host)

    private fun hex(color: Int) = String.format("0x%08X", color)

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }
}
