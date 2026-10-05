package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView

/**
 * The topic screen's fade band, end to end: ChatActivity's own [ChatActivityFadeView] over the post
 * list, under the floating bar and the composer.
 *
 * <p>The failure this pins is layering, which the screenshots cannot state on their own: a band
 * added after the composer paints its bottom zone over the input, and a band added before the list
 * is hidden under it. Its two zones are read back and printed as the retained number for the device
 * pass: the top zone is the bar's own measured height (the status bar is inside it, so the whole
 * status-bar band and the gaps between the capsules blur), and the bottom zone covers the composer
 * so the posts fade out above the input.</p>
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
internal class TopicFadeBandTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        NativeDo.setContainerFactory { context -> io.github.baba983.nativedo.core.di.AppContainer(context) }
        site.stop()
    }

    /**
     * The band draws between the list and everything that floats over it, and its zones reach the
     * bar and the composer - the geometry the chat's own band has, in this screen's numbers.
     */
    @Test
    fun theFadeBandDrawsBetweenTheListAndTheBarAndComposer() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Fade band"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.idleClock(200)
        harness.layoutApp()

        val fragment = harness.visibleFragment() as TopicFragment
        val content = fragment.fragmentView as ViewGroup
        val fade = field(fragment, "fadeView") as ChatActivityFadeView
        val composer = field(fragment, "composer") as View
        val list = contentListOf(harness.activity) as RecyclerListView
        val bar = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<ActionBar>()
            .first()

        println(
            "fade: content=" + content.height + " bar=" + bar.measuredHeight +
                " composer=" + composer.height + " listIndex=" + content.indexOfChild(list) +
                " fadeIndex=" + content.indexOfChild(fade) +
                " barIndex=" + content.indexOfChild(bar) +
                " composerIndex=" + content.indexOfChild(composer) +
                " zoneTop=" + zone(fragment, "fadeZoneTop") +
                " zoneBottom=" + zone(fragment, "fadeZoneBottom"),
        )

        assertTrue(
            "the band must draw over the posts (a band under the list is invisible)",
            content.indexOfChild(fade) > content.indexOfChild(list),
        )
        assertTrue(
            "the band must draw under the bar (the capsules own the top)",
            content.indexOfChild(fade) < content.indexOfChild(bar),
        )
        assertTrue(
            "the band must draw under the composer (or its bottom zone hides the input)",
            content.indexOfChild(fade) < content.indexOfChild(composer),
        )
        assertTrue(
            "the top zone reaches the bar's measured height, the status bar included",
            zone(fragment, "fadeZoneTop") >= bar.measuredHeight,
        )
        assertTrue(
            "the bottom zone covers the composer",
            zone(fragment, "fadeZoneBottom") >= composer.height,
        )
        // The band is a View over the whole content; it must stay an overlay and never become an
        // input, or a touch on the status bar zone would stop reaching the posts under it.
        val probe = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN,
            content.width / 2f, bar.measuredHeight / 2f, 0)
        try {
            assertFalse(
                "the band must not consume the touches over the posts",
                fade.dispatchTouchEvent(probe),
            )
        } finally {
            probe.recycle()
        }

        harness.renderApp("topic-fade-band")
        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            if (toDark) Theme.getTheme("Dark Blue") else Theme.getTheme("Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        harness.await("the theme to switch") {
            harness.idleClock(60)
            Theme.isCurrentThemeDark() == toDark
        }
        harness.layoutApp()
        harness.renderApp("topic-fade-band-dark")

        // What the band and the capsules draw is recorded from the posts; a view that draws from
        // it keeps the frame it was last recorded with until it is told to redraw (its display
        // list is cached), so a screen whose list moves has to tell every attached view. The
        // failure is a frozen band/capsule; the device pass cannot scroll the HIGH phone and the
        // emulator never blurs, so this is the only gate that can pin it. Added after the renders
        // so the second band does not paint into them.
        val host = field(fragment, "glassHost") as io.github.baba983.nativedo.app.GlassPanelHost
        val counted = CountingFadeView(harness.context)
        content.addView(counted, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        counted.setup(host.getFactory())
        harness.layoutApp()
        counted.invalidateCount = 0
        host.invalidateAttachedViews()
        assertTrue(
            "a view drawing from the recording was not told to redraw when it moves",
            counted.invalidateCount >= 1,
        )
        harness.assertNoTelegramSideEffects()
    }

    private fun field(fragment: TopicFragment, name: String): Any {
        val field = TopicFragment::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(fragment)!!
    }

    /** The zone a [ChatActivityFadeView] holds for one edge, read back the way the chat sets it. */
    private fun zone(fragment: TopicFragment, name: String): Int {
        val view = field(fragment, "fadeView") as ChatActivityFadeView
        val field = ChatActivityFadeView::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.getInt(view)
    }

    /** A band that counts the redraws it is told to do; a real one cannot be observed from here. */
    private class CountingFadeView(context: android.content.Context) : ChatActivityFadeView(context) {
        var invalidateCount = 0

        override fun invalidate() {
            invalidateCount++
            super.invalidate()
        }
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TOPIC_ID = 101L
    }
}
