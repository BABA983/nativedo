package io.github.baba983.nativedo.chat

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell

/**
 * The public chat channels: the list the 聊天 entry opens.
 *
 * <p>The failure modes pinned here: a channel the site lists but the client cannot draw, a page that
 * did not arrive drawn as a silently empty list instead of saying so and offering to read again, and
 * a row that does not open the conversation it names.</p>
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
internal class PublicChannelsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        // A conversation marks its newest message read on load; an unserved route would only add a
        // 404 to the log, so it is served for the tests that open a channel.
        site.serveJson("chat/api/channels/71/read.json", harness.fixture("chat-read.json"))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theListShowsPublicChannelsAndARowOpensItsConversation() {
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))
        site.serveJson("chat/api/channels/71.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }

        harness.present(PublicChannelsFragment.create())
        harness.await("the site for the public channels") {
            site.requestPaths.contains("chat/api/channels.json")
        }
        harness.await("the channel rows") { listRowCount() == 2 }
        assertEquals(
            "the first page is asked with the site's own default page size, from the start",
            listOf("limit=25", "offset=0"),
            site.queriesFor("chat/api/channels.json").first().split('&').sorted(),
        )
        harness.layoutApp()
        val firstCell = contentListOf(harness.activity)!!.getChildAt(0) as DialogCell
        assertEquals("the first row is the channel's title", "站点反馈", firstCell.nameLayout.text.toString())

        harness.idleClock(400)
        harness.renderApp("chat-channels-light")

        // A row opens the conversation it names - the same call a tap makes.
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list")
        val first = list.getChildAt(0) ?: throw AssertionError("the list drew no rows")
        list.clickItem(first, positionOf(first))
        harness.await("the conversation the row names") {
            site.requestPaths.contains("chat/api/channels/71.json")
        }
        assertTrue(
            "the conversation screen is the one on top",
            harness.visibleFragment() is ChatChannelFragment,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theListSwitchesToNightInPlace() {
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())
        harness.await("the channel rows") { listRowCount() == 2 }

        val fragment = harness.visibleFragment() as PublicChannelsFragment
        val listBefore = contentListOf(harness.activity)!!
        val original = Theme.getCurrentTheme()
        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(if (toDark) "Dark Blue" else "Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            assertEquals("the list is the same one after the switch", listBefore, contentListOf(harness.activity))
            harness.idleClock(400)
            harness.layoutApp()
            harness.renderApp("chat-channels-night")
        } finally {
            Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A read that did not arrive is its own state with a tap that reads again; the second read's
     * rows replace it. A blank list would read as "there are no channels", which is a different
     * thing from "the site could not be reached".
     */
    @Test
    fun aPageThatDidNotArriveOffersToReadAgain() {
        val attempts = AtomicInteger(0)
        site.serveResponse("chat/api/channels.json") {
            if (attempts.incrementAndGet() == 1) {
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"errors":["boom"]}""")
            } else {
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("chat-channels.json"))
            }
        }

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())

        harness.await("the failed read to say so rather than show an empty list") {
            shownText().any { text -> text.contains("点一下重试") }
        }
        assertEquals("the failure is not a blank list", 1, listRowCount())

        val fragment = harness.visibleFragment() as PublicChannelsFragment
        fragment.refreshForTest()
        harness.await("the second read to bring the rows") { listRowCount() == 2 }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun listRowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun shownText(): List<String> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(content, out)
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
    }

    private fun positionOf(view: View): Int {
        val list = contentListOf(harness.activity) ?: return 0
        val first = (list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.findFirstVisibleItemPosition() ?: return 0
        val visibleIndex = (0 until list.childCount).firstOrNull { index ->
            list.getChildAt(index).bottom > list.paddingTop
        } ?: return 0
        val index = (0 until list.childCount).firstOrNull { list.getChildAt(it) === view } ?: return 0
        return first + (index - visibleIndex)
    }
}
