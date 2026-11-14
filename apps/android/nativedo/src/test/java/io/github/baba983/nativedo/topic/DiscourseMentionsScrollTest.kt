package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.MentionCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import java.io.File

/**
 * The `@mention` panel over a long candidate list, scrolled.
 *
 * <p>The failure this pins: `DiscourseComposerTools` hosts the panel full-height (the geometry the
 * first candidate needs) and hands it a glass background. Upstream paints that background over the
 * band its own list reports - but when the list scrolls and its padding row leaves the window the
 * band's own top collapses to the panel's top, so the panel would paint over the whole screen
 * instead of hugging its rows. This drives the real production screen with twenty candidates,
 * scrolls to the end, and checks that the reader still sees the posts behind a bounded band.</p>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    // A short screen: twenty candidates are taller than the panel host, so the list really scrolls
    // and its padding row really leaves the window - the case the full-height band is judged on.
    qualifiers = "w360dp-h600dp-xhdpi",
    shadows = [
        ShadowConnectionsManager::class, ShadowNativeByteBuffer::class, ShadowUtilities::class,
        ShadowMediaController::class, ShadowIntro::class, ShadowMrzRecognizer::class,
        ShadowRLottieNative::class, ShadowAnimatedFileNative::class, ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class, ShadowSQLitePreparedStatement::class,
    ],
)
internal class DiscourseMentionsScrollTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A post under a long mention list.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("u/search/users", twentyUsers())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        harness.visibleFragment()?.onFragmentDestroy()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        site.stop()
    }

    @Test
    fun scrolledTwentyCandidatesKeepABoundedBandInBothThemes() {
        for (dark in listOf(false, true)) {
            if (dark) Theme.applyTheme(Theme.getTheme("Dark Blue"), false, false)
            val fragment = open()
            val composer = composers(fragment).single()
            val field = composer.editField!!
            field.setText("hello @a")
            field.setSelection(field.length())
            harness.idleClock(400)
            val panel = descendants(fragment.fragmentView).filterIsInstance<DiscourseMentionsPanel>().single()
            harness.await("twenty candidates") {
                (panel.adapter as DiscourseMentionsAdapter).candidateCount() == 20 && panel.isShowing
            }
            harness.idleClock(400)
            harness.layoutApp()

            val list = panel.getListView() as RecyclerListView
            list.layoutManager!!.scrollToPosition(19)
            harness.idleClock(300)
            harness.layoutApp()

            val diagnostics = diagnostics(panel)
            println("mentions-scroll dark=$dark $diagnostics")
            val file = harness.renderApp("mentions-scroll-${if (dark) "dark" else "light"}")
            retain(file, "mentions-scroll-${if (dark) "dark" else "light"}")

            // The band the panel paints must hug its visible rows, and - with the host capped - it
            // must never reach the top of the screen: the reader keeps the posts above the band.
            val bandTop = diagnostics["bandTop"] as Int
            val firstRowTop = diagnostics["firstRowTop"] as Int
            val panelTop = diagnostics["panelTop"] as Int
            assertTrue(
                "the scrolled panel's band still hugs the first visible row " +
                    "(bandTop=$bandTop firstRowTop=$firstRowTop, dark=$dark)",
                firstRowTop - bandTop <= AndroidUtilities.dp(12f),
            )
            assertTrue(
                "a long candidate list does not paint its band over the top of the screen " +
                    "(bandTop=$bandTop panelTop=$panelTop, dark=$dark)",
                bandTop >= AndroidUtilities.dp(40f),
            )
            fragment.onFragmentDestroy()
            harness.idle()
        }
        harness.assertNoTelegramSideEffects()
    }

    /** The panel's own band geometry, read the way the panel computes it. */
    private fun diagnostics(panel: DiscourseMentionsPanel): Map<String, Any> {
        val containerTop = field(panel, "containerTop") as Float
        val containerBottom = field(panel, "containerBottom") as Float
        val firstRow = descendants(panel).filterIsInstance<MentionCell>().firstOrNull()
        val panelTop = IntArray(2).also { panel.getLocationInWindow(it) }[1]
        val rowTop = firstRow?.let { IntArray(2).also { arr -> it.getLocationInWindow(arr) }[1] } ?: panelTop
        return mapOf(
            "bandTop" to (panelTop + containerTop).toInt(),
            "bandBottom" to (panelTop + containerBottom).toInt(),
            "firstRowTop" to rowTop,
            "panelTop" to panelTop,
            "panelHeight" to panel.height,
        )
    }

    private fun field(instance: Any, name: String): Any {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            runCatching {
                val f = type!!.getDeclaredField(name).apply { isAccessible = true }
                return f.get(instance)!!
            }
            type = type.superclass
        }
        error("no field $name")
    }

    private fun open(): BaseFragment {
        harness.bootApp()
        harness.await("home feed") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        val fragment = TopicFragment.forTopic(TOPIC_ID, "Mentions scroll")
        harness.present(fragment)
        harness.await("composer owner") { harness.visibleFragment() === fragment }
        harness.await("posts") { contentRowCount(harness) == 1 }
        harness.layoutApp()
        return fragment
    }

    private fun composers(fragment: BaseFragment) = descendants(fragment.fragmentView)
        .filterIsInstance<ChatActivityEnterView>()

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun retain(file: File, name: String) {
        val out = File("../../../.amp/in/artifacts/topic-fidelity-wave/interactions").apply { mkdirs() }
        file.copyTo(File(out, "$name.png"), overwrite = true)
    }

    private fun twentyUsers(): String {
        val users = (1..20).joinToString(",") { index ->
            """{"username":"member$index","name":"Member $index","avatar_template":"","priority_group":0}"""
        }
        return """{"users":[$users],"groups":[]}"""
    }

    private companion object {
        const val TOPIC_ID = 445599L
    }
}
