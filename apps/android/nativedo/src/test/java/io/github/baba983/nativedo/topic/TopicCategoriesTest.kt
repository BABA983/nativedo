package io.github.baba983.nativedo.topic

import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.RecyclerListView

/**
 * The topic list's category filter.
 *
 * <p>The failure this pins: the site's categories are only reachable by their own address, so a
 * reader who wants one category has to find a topic that happens to be in it. The strip is built
 * from {@code /categories.json} - the site's own categories, in its own order, with its own colors -
 * and a tab loads that category's own {@code /c/{slug}/{id}/l/latest.json} instead of the head of
 * the list.</p>
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
internal class TopicCategoriesTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("c/develop/4/l/latest.json", "category-latest.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theStripsTabsAreTheSitesCategoriesAndATabLoadsItsOwnFeed() {
        harness.bootApp()
        harness.await("the all-topics feed to load") { rowCount() == 2 }
        harness.await("the site's categories to arrive as tabs") { tabIds().size == 5 }

        val tabs = tabTitles().map { it.toString() }
        println("categories: tabs=$tabs dots=${tabDotColors()}")
        assertEquals(
            "the all-topics tab, then the site's own categories in its own order",
            listOf("全部", "● 运营反馈", "● 开发调优", "● 搞七捻三", "● 国内吹水"),
            tabs,
        )
        assertEquals(
            "each tab carries the site's own color for its category",
            listOf(null, Color.rgb(0x80, 0x82, 0x81), Color.rgb(0x32, 0xC3, 0xC3),
                Color.rgb(0x3A, 0xB5, 0x4A), Color.rgb(0xD1, 0x2C, 0x25)),
            tabDotColors(),
        )
        assertFalse(
            "a subcategory rides inside its parent, it is not a tab of its own",
            tabs.any { it.contains("一号水区") },
        )
        harness.layoutApp()
        harness.renderApp("topic-categories-all")

        tapTab(4)
        harness.await("the tab's own feed") {
            site.requestPaths.contains("c/develop/4/l/latest.json")
        }
        harness.await("its own rows") { rowCount() == 3 }
        println("categories: asked=" + site.queriesFor("c/develop/4/l/latest.json"))
        assertTrue(
            "the tab asked the site for that category's feed",
            site.requestPaths.contains("c/develop/4/l/latest.json"),
        )
        assertEquals(
            "and the head of the list was only read once, at boot",
            1,
            site.queriesFor("latest.json").count { it.contains("page=0") },
        )
        harness.drawApp()
        harness.idleClock(400)
        harness.renderApp("topic-categories")
        harness.assertNoTelegramSideEffects()
    }

    /** The tab strip the screen put over the list: upstream's own filter control. */
    private fun strip(): FilterTabsView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findStrip(content)
    }

    private fun findStrip(view: android.view.View): FilterTabsView? {
        if (view is FilterTabsView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findStrip(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun tabIds(): List<Int> {
        val strip = strip() ?: return emptyList()
        return (0 until strip.tabsCount).map { position -> strip.getTab(position)!!.id }
    }

    private fun tabTitles(): List<CharSequence> {
        val strip = strip() ?: return emptyList()
        return (0 until strip.tabsCount).map { position -> strip.getTab(position)!!.title }
    }

    /** The color the tab's dot is drawn in, which is the site's color for that category. */
    private fun tabDotColors(): List<Int?> = tabTitles().map { title ->
        (title as? Spanned)
            ?.getSpans(0, title.length, ForegroundColorSpan::class.java)
            ?.firstOrNull()
            ?.foregroundColor
    }

    /** A tap on a tab, handed over the way upstream's own list hands one over. */
    private fun tapTab(id: Int) {
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        harness.layoutApp()
        val position = tabIds().indexOf(id)
        if (position < 0) throw AssertionError("the strip has no tab $id: " + tabIds())
        val container = strip.tabsContainer
        val tab = (0 until container.childCount)
            .map { index -> container.getChildAt(index) }
            .firstOrNull { it.id == id }
            ?: throw AssertionError("the strip drew no view for tab $id")
        println("categories: tapping $id at position $position (" + tab.javaClass.simpleName + ")")
        container.clickItem(tab, position)
        harness.idle()
    }

    private fun rowCount(): Int = contentRowCount(harness)
}
