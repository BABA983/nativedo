package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.Components.RecyclerListView

/**
 * The home header's vertical placement against the list it floats over.
 *
 * <p>Failure mode pinned here: the header's own placement (the search field's slot and the category
 * strip's slot) is baked into the frame's child margins when the page is built, while the list's top
 * padding is re-derived by {@code checkUi_listPadding} from {@code AndroidUtilities.statusBarHeight}
 * every time the insets land. That static is not fixed for the life of the page: the drawer
 * container writes the real window inset into it
 * ({@code DrawerLayoutContainer.java:63-71}, {@code :158-165}). A page built before the inset
 * settles therefore keeps the old placement while its list padding grows to the new one - a blank
 * band opens under the strip, and on a device, where the bar self-measures to the new height, the
 * title slides down under the search pill.</p>
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
internal class HomeHeaderLayoutTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        // The static the header and the list padding both read: a previous method leaves the value
        // its own insets wrote, so each test starts from the boot value the harness reports (0).
        AndroidUtilities.statusBarHeight = 0
        site.start()
        site.serveFixture("latest.json", "latest-topics.json")
        site.serveFixture("categories.json", "categories.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The page loads, then the window insets land the way they do on a phone: the header must keep
     * sitting one field's height under the bar, and the list's first row must reach the strip - the
     * 5dp the row peeks under the glass - not stop a blank band below it.
     */
    @Test
    fun theHeaderAndTheListRowsFollowTheStatusBarInsetTogether() {
        harness.bootApp()
        harness.await("the home") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.await("the rows") { (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) > 0 }
        harness.layoutApp()
        harness.renderApp("home-header-layout-rest")

        // The real inset arrives after the page was built - the case a one-shot placement cannot
        // survive. A cutout-sized status bar, the size that makes the desync obvious.
        applyStatusBarInset(AndroidUtilities.dp(44f))
        harness.renderApp("home-header-layout-inset")

        assertHeaderSitsOneHeaderAboveTheList("after the window inset landed")
    }

    /** The same reading while the header is collapsed by a scroll and while the search is up. */
    @Test
    fun theCollapsedAndSearchingHeaderStillReadsTheStatusBarInset() {
        harness.bootApp()
        harness.await("the home") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.await("the rows") { (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) > 0 }
        harness.layoutApp()
        applyStatusBarInset(AndroidUtilities.dp(44f))

        val home = harness.visibleFragment() as TopicListFragment
        val list = field<RecyclerListView>(home, "listView")
        list.scrollToPosition(20)
        harness.layoutApp()
        assertHeaderSitsOneHeaderAboveTheList("with the list collapsed by a scroll")

        home.fragmentView.requestFocus()
        val edit = (field<View>(home, "searchField") as org.telegram.ui.Components.FragmentSearchField).editText
        edit.requestFocus()
        harness.idleClock(500)
        assertHeaderSitsOneHeaderAboveTheList("with the search open")
    }

    /**
     * The header's resting height is the bar's own height plus the status bar (the field's slot and
     * the strip's slot stacked under it). The strip must sit one field below the bar; the list's top
     * padding must be that same resting height; and the first row must reach the strip, so the
     * padding may not fall below the strip's lower edge.
     */
    private fun assertHeaderSitsOneHeaderAboveTheList(what: String) {
        val home = harness.visibleFragment() as TopicListFragment
        val searchField = field<View>(home, "searchField")
        val strip = field<View>(home, "tabsView")
        val list = field<RecyclerListView>(home, "listView")
        val barHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight
        val fieldHeight = AndroidUtilities.dp(48f)

        assertEquals(
            "$what: the search field's slot is one field's height under the bar",
            (barHeight + AndroidUtilities.dp(2f)).toLong(),
            searchField.top.toLong(),
        )
        assertEquals(
            "$what: the strip's slot is the bar plus one field",
            (barHeight + fieldHeight).toLong(),
            strip.top.toLong(),
        )
        assertEquals(
            "$what: the list's top padding is the header's resting height (bar + field + strip - the" +
                " 5dp the first row peeks under the strip)",
            (barHeight + AndroidUtilities.dp(48f + 50f - 5f)).toLong(),
            list.paddingTop.toLong(),
        )
        assertTrue(
            "$what: the first row must reach the strip, not leave a blank band (strip bottom=" +
                "${strip.top + strip.height}, list padding=${list.paddingTop})",
            list.paddingTop <= strip.top + strip.height,
        )
        assertTrue(
            "$what: the search field must clear the title, so its slot cannot start under the bar",
            searchField.top >= barHeight,
        )
    }

    /** The window insets as the system delivers them once the page is already built. */
    private fun applyStatusBarInset(statusBarPx: Int) {
        val root = harness.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
        harness.layoutApp()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val declared = type.getDeclaredField(name)
                declared.isAccessible = true
                return declared.get(target) as T
            } catch (ignored: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException(name)
    }
}
