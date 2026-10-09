package io.github.baba983.nativedo.messages

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.feature.messages.data.PrivateMessageBox
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.menuItemLabels
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
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import java.util.concurrent.atomic.AtomicInteger

/**
 * Private messages: the reader's inbox, sent box and unread box, over Telegram's own tab strip.
 *
 * <p>The failures pinned here: a reader who cannot see the private messages the site holds for them
 * (they live behind the site's own {@code topics/private-messages*} routes, which answer the
 * standard `topic_list` envelope); a box switch that reads the wrong route or leaves another box's
 * rows standing (the tab under the indicator must be the box on screen); the compose button not
 * clearing the system navigation inset; and a read that did not arrive drawn as a silently empty
 * list instead of saying so and offering to read again.</p>
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
internal class PrivateMessagesTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theInboxListsTheSitesMessagesAndARowOpensItsTopic() {
        // The inbox is paged like a feed: its first page names a second, and the second is empty,
        // which is where the list ends.
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }
        // A private message is a topic, so its row opens the topic screen at that topic.
        site.serve(INBOX_TOPIC_ID, "topic-detail.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }

        harness.present(PrivateMessagesFragment.create())
        harness.await("the site for the reader's inbox") {
            site.requestPaths.contains("topics/private-messages/baba.json")
        }
        harness.await("the inbox rows") { listRowCount() == 2 }
        assertEquals(
            "the first page, asked the way the site pages it",
            "page=0",
            site.queriesFor("topics/private-messages/baba.json").first(),
        )

        // Tapping a row opens the topic that private message lives in.
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list")
        harness.layoutApp()
        val first = list.getChildAt(0) ?: throw AssertionError("the list drew no rows")
        list.clickItem(first, positionOf(first))
        harness.await("the topic the message is in") {
            site.requestPaths.contains("t/$INBOX_TOPIC_ID.json")
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The strip is the control: a tap on 已发送/未读 moves the indicator there and reads that box's
     * own route, replacing the previous box's rows. The sent box keeps the site's unread reply
     * badge - a reply to a message we started is still unread.
     */
    @Test
    fun theTabsSwitchBoxesAndAskEachBoxesOwnRoute() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }
        // A message we started can receive an unread reply. The sent filter is not a read receipt.
        site.serveJson("topics/private-messages-sent/baba.json", harness.fixture("private-messages-sent.json")
            .replace("\"unread_posts\": 0", "\"unread_posts\": 1")
            .replace("\"last_poster_username\": \"baba\"", "\"last_poster_username\": \"alice\""))
        site.serveJson("topics/private-messages-unread/baba.json", harness.fixture("private-messages-unread.json"))

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()
        harness.idleClock(400)

        val fragment = fragment()
        val strip = fragment.tabsView() ?: throw AssertionError("no box strip")
        assertEquals("the strip carries the three boxes", setOf("收件箱", "已发送", "未读"), tabLabels(strip))
        assertEquals("the inbox is the box under the indicator", 0, strip.currentTabId)
        harness.renderApp("pm-telegram-inbox")

        // A tap on 已发送 reads the sent box's own route and replaces the inbox rows.
        tapTab(strip, "已发送")
        harness.await("the sent box's own route") {
            site.requestPaths.contains("topics/private-messages-sent/baba.json")
        }
        harness.await("the sent rows") { listRowCount() == 1 }
        harness.layoutApp()
        assertEquals("the sent tab is the one under the indicator", 1, strip.currentTabId)
        assertEquals("the strip and the box agree", PrivateMessageBox.Sent, fragment.currentBox())
        val sentCell = fragment.listView()!!.getChildAt(0) as DialogCell
        assertTrue("the sent box keeps the site's unread reply badge", sentCell.hasUnread)
        harness.renderApp("pm-telegram-sent")

        // A tap on 未读 reads the unread route and replaces the sent rows.
        tapTab(strip, "未读")
        harness.await("the unread box's route and rows") {
            site.requestPaths.contains("topics/private-messages-unread/baba.json") &&
                (fragment.listView()!!.getChildAt(0) as? DialogCell)?.nameLayout?.text?.toString() == "关于构建脚本的私信"
        }
        harness.layoutApp()
        assertEquals("the unread tab is the one under the indicator", 2, strip.currentTabId)
        assertEquals("the strip and the box agree", PrivateMessageBox.Unread, fragment.currentBox())
        val unreadCell = fragment.listView()!!.getChildAt(0) as DialogCell
        assertTrue("the unread box shows the site's unread badge", unreadCell.hasUnread)
        harness.renderApp("pm-telegram-unread")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The compose button is the only way to start a message, and it must clear the system
     * navigation inset: lifted by the inset, with the list reserving the button's own room plus the
     * inset, so the last row is never trapped under it.
     */
    @Test
    fun theComposeButtonOpensTheComposerAndClearsTheNavigationInset() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()

        val fragment = fragment()
        val button = fragment.composeButton() ?: throw AssertionError("no compose button")
        assertEquals("the compose button says what it does", "写私信", button.contentDescription.toString())

        // The system hands the screen its bottom inset; the button lifts over it and the list keeps
        // its room, so a row can be scrolled clear of both.
        val inset = 60
        fragment.onInsets(0, 0, 0, inset)
        assertEquals(
            "the compose button lifts over the inset",
            -inset.toDouble(),
            button.translationY.toDouble(),
            0.5,
        )
        assertTrue(
            "the list keeps room for the button and the inset",
            fragment.listView()!!.paddingBottom >= inset,
        )

        // The button opens the composer in its private-message mode.
        button.performClick()
        harness.await("the private-message composer") {
            harness.visibleFragmentName() == "NewTopicFragment"
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The day/night switch recolours the strip and the list in place - the same strip, the same
     * current box - rather than rebuilding the screen and reading the box a second time. The light
     * and night renders are the retained evidence.
     */
    @Test
    fun theNightSwitchRecoloursTheStripWithoutRebuildingIt() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()
        harness.idleClock(400)
        harness.renderApp("pm-telegram-light")

        val fragment = fragment()
        val strip = fragment.tabsView() ?: throw AssertionError("no box strip")
        val inboxReads = site.requestPaths.count { it == "topics/private-messages/baba.json" }

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
            harness.layoutApp()
            assertSame("the switch keeps the strip, it does not rebuild it", strip, fragment.tabsView())
            assertEquals("and keeps the box the reader chose", PrivateMessageBox.Inbox, fragment.currentBox())
            assertEquals(
                "and does not read the box a second time",
                inboxReads,
                site.requestPaths.count { it == "topics/private-messages/baba.json" },
            )
            harness.idleClock(400)
            harness.layoutApp()
            harness.renderApp("pm-telegram-night")
        } finally {
            Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A read that did not arrive must not be drawn as a blank inbox - the reader would think they
     * have no messages - so it is its own state with a tap that reads again, and the second read's
     * rows replace it.
     */
    @Test
    fun aPageThatDidNotArriveOffersToReadAgain() {
        val attempts = AtomicInteger(0)
        site.serveResponse("topics/private-messages/baba.json") {
            if (attempts.incrementAndGet() == 1) {
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"errors":["boom"]}""")
            } else {
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("private-messages.json"))
            }
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())

        harness.await("the failed read to say so rather than show an empty list") {
            shownText().any { text -> text.contains("点一下重试") }
        }
        assertEquals("the failure is not a blank list", 1, listRowCount())
        harness.layoutApp()
        harness.renderApp("pm-telegram-error")

        // The state row is the reader's own way to read again.
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list")
        val row = list.getChildAt(0) ?: throw AssertionError("the state row was not drawn")
        list.clickItem(row, positionOf(row))
        harness.await("the second read to bring the rows") { listRowCount() == 2 }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The overflow keeps the box choice and adds the public channels screen. The entry is the only
     * thing this screen knows about it: the screen itself is the channels worker's.
     */
    @Test
    fun theOverflowOffersTheBoxesAndThePublicChannels() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox rows") { listRowCount() == 2 }

        fragment().openBoxMenu()
        harness.layoutApp()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        assertTrue("the overflow offers the three boxes", labels.containsAll(listOf("收件箱", "已发送", "未读")))
        assertTrue("and the public channels screen", labels.contains("公共频道"))
        harness.renderComposite(".amp/in/artifacts/nativedo/pm-telegram-menu.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun fragment(): PrivateMessagesFragment =
        harness.visibleFragment() as? PrivateMessagesFragment
            ?: throw AssertionError("the screen is not on top")

    /** The strip's tab labels, in the order the strip shows them. */
    private fun tabLabels(strip: ScrollSlidingTextTabStrip): Set<String> =
        views(strip).filterIsInstance<TextView>().map { it.text.toString() }.toSet()

    /** A tap on the tab labelled [label], handed over the way the strip's own click does. */
    private fun tapTab(strip: ScrollSlidingTextTabStrip, label: String) {
        harness.layoutApp()
        val tab = views(strip).filterIsInstance<TextView>().firstOrNull { it.text.toString() == label }
            ?: throw AssertionError("the strip shows no \"$label\" tab")
        tab.performClick()
        harness.idle()
    }

    private fun listRowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }

    /** Every piece of text the screen shows, dialogs included. */
    private fun shownText(): List<String> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(content, out)
        (ShadowDialog.getLatestDialog() as? android.app.Dialog)?.window?.decorView?.let { dialog ->
            collectText(dialog, out)
        }
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

    private companion object {
        const val INBOX_TOPIC_ID = 5101L

        /** A page the site has nothing more on: no rows and no `more_topics_url`. */
        const val EMPTY_PAGE = """{"topic_list":{"more_topics_url":null,"topics":[]}}"""
    }
}
