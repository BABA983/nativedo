package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import io.github.baba983.nativedo.app.NativeDo
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.DialogsActivity
import java.io.File

/**
 * The home header across the options menu's day/night switch.
 *
 * <p>Failure mode pinned here: a day→night switch repaints the home header without a fragment
 * rebuild. The header's band is the frame's own paint (`actionBarDefaultPaint`), and the bars's
 * colours are the ones the shell's theme animation writes; a switch that forgets any of them
 * leaves the band - and with it the status-bar icons' contrast - in the old theme while the
 * list, the strip and the search field follow the new one. The device pass shows the same
 * failure; this pins it where it can be checked without a phone.</p>
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
internal class HomeHeaderThemeTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveJson("latest.json", manyTopics("latest-topics.json", 10))
        site.serveFixture("categories.json", "categories.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theDayNightSwitchRepaintsTheHomeHeaderWithoutRebuildingThePage() {
        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()

        val fragment = harness.visibleFragment() as TopicListFragment
        val pageView = fragment.fragmentView
        val paint = fragmentField("actionBarDefaultPaint") as android.graphics.Paint
        val bar = fragmentField("actionBar") as ActionBar

        assertBandFollowsTheTheme("the light header before the switch", paint)

        switchTheme(toDark = true)
        assertSame("the switch keeps the page's own view", pageView, fragment.fragmentView)
        assertBandFollowsTheTheme("the header after switching to night", paint)
        assertEquals("the title follows the theme too", Theme.getColor(Theme.key_actionBarDefaultTitle),
            bar.titleTextView.textColor)
        assertEquals(
            "the bar has no scrim colour of its own (DialogsActivity's bar has none either); a" +
                " set-once one paints the old theme over the frame's band after a switch",
            Color.TRANSPARENT,
            bar.backgroundColor,
        )

        switchTheme(toDark = false)
        assertSame("and keeps it on the way back", pageView, fragment.fragmentView)
        assertBandFollowsTheTheme("the header after switching back to day", paint)
        assertEquals("the title follows the theme back", Theme.getColor(Theme.key_actionBarDefaultTitle),
            bar.titleTextView.textColor)
    }

    /**
     * Posts the notification the menu row posts and waits for the theme to land. No position:
     * the circular reveal is what the device pass checks, and Robolectric cannot finish its
     * animator (the other home tests post the same way).
     */
    private fun switchTheme(toDark: Boolean) {
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
            harness.layoutApp()
        } finally {
            DialogsActivity.switchingTheme = false
        }
    }

    /**
     * The header's band is the theme's window background: the frame's paint is re-read, and the
     * drawn band (sampled right of the title, left of the icons) really is that colour. The
     * pre-switch repro drew the old theme's white here for both of the switched states.
     */
    private fun assertBandFollowsTheTheme(what: String, paint: android.graphics.Paint) {
        val expected = Theme.getColor(Theme.key_windowBackgroundWhite)
        assertEquals("$what: the frame's band paint is the theme's colour", expected, paint.color)
        val png = harness.renderApp("home-header-" + if (Theme.isCurrentThemeDark()) "dark" else "light")
        val band = bitmapOf(png)
        val samples = mutableListOf<Int>()
        for (y in listOf(30, AndroidUtilities.statusBarHeight + AndroidUtilities.dp(20f).toInt())) {
            for (step in 0..4) {
                val x = (band.width * (0.40f + 0.05f * step)).toInt()
                samples += band.getPixel(x, y)
            }
        }
        assertTrue(
            "$what: the drawn band is the theme's colour (was ${hex(samples)})",
            samples.all { it == expected },
        )
    }

    private fun bitmapOf(file: File): Bitmap =
        BitmapFactory.decodeFile(file.path) ?: throw AssertionError("no render at ${file.path}")

    private fun hex(colors: List<Int>): String =
        colors.joinToString(",") { "#%08x".format(it) }

    private fun fragmentField(name: String): Any? {
        var type: Class<*>? = TopicListFragment::class.java
        while (type != null) {
            val field = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return field.get(harness.visibleFragment())
            }
            type = type.superclass
        }
        throw AssertionError("no field $name")
    }

    private fun manyTopics(fixture: String, count: Int): String {
        val base = org.json.JSONObject(harness.fixture(fixture))
        val topicList = base.getJSONObject("topic_list")
        val source = topicList.getJSONArray("topics")
        val topics = org.json.JSONArray()
        for (index in 0 until count) {
            val topic = org.json.JSONObject(source.getJSONObject(index % source.length()).toString())
            topic.put("id", 900000 + index)
            topics.put(topic)
        }
        topicList.put("topics", topics)
        return base.toString()
    }
}
