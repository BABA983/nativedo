package io.github.baba983.nativedo.messages

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.feature.messages.data.PrivateMessageBox
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.telegram.ui.Components.ViewPagerFixed
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 *
 * <p>Embedded in the message center ({@code messageCenter=true}) the failures are different: a
 * second row of tabs (a strip of its own under the center's), a bar left addable to the host (an
 * empty band above the list), a center with no way to reach 已发送/未读, a duplicated public-channels
 * entry, and a page that lets the main-tab strip trap its last row or its compose button. And a row
 * that shows a replier count ("last poster · 0") instead of the site's excerpt, or names a person
 * the row's picture does not draw.</p>
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
        val first = list.getChildAt(0) as DialogCell
        // The row is information, not just a title: the site's title is its name and its own excerpt
        // is the line under it.
        assertEquals(
            "the row's name is the site's own title",
            "关于构建脚本的私信",
            first.nameLayout?.text?.toString(),
        )
        assertTrue(
            "and its line is the site's own excerpt",
            rowLine(first).contains("我把自己的构建脚本发给你看看"),
        )
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
     * The boxes are a pager of their own, not a single list the strip repaints: a finger drag slides
     * the two pages (the incoming one is translated, not parked at the edge), the strip's indicator
     * follows it, and on release the pager commits the box the finger landed on. The sent box's
     * answer is held while the swipe happens, so the drag cannot be waiting on it - and the sent
     * page keeps its own loading state. Swiping back finds the inbox's rows still there, read once.
     */
    @Test
    fun theBoxesSlideUnderTheStripWithAFingerAndKeepTheirOwnRows() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }
        // Held until the test releases it: the swipe must commit whether or not the box has answered.
        val sentAnswer = CountDownLatch(1)
        site.serveResponse("topics/private-messages-sent/baba.json") {
            sentAnswer.await(20, TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("private-messages-sent.json"))
        }

        try {
            harness.bootApp()
            harness.await("the topic list to load") { listRowCount() == 2 }
            harness.present(PrivateMessagesFragment.create())
            harness.await("the inbox rows") { listRowCount() == 2 }
            harness.layoutApp()
            harness.idleClock(400)

            val fragment = fragment()
            val strip = fragment.tabsView() ?: throw AssertionError("no box strip")
            val pager = pagerOf() ?: throw AssertionError("the boxes have no pager to slide")
            assertEquals("the inbox is the page at rest", 0, pager.getCurrentPosition())
            assertEquals("the strip agrees", 0, strip.currentTabId)
            val indicatorAtRest = field<Int>(strip, "indicatorX")
            // A short inbox may prefetch page 1 during layout. Only another page 0 would restart it.
            val inboxReadsBeforeSwipe = site.queriesFor("topics/private-messages/baba.json")
                .count { it == "page=0" }

            // A finger drag forward, held before release: the incoming page is off its edge and on
            // its way in - the pages slide, not only the strip's indicator.
            dragForward(pager, 0.8f)
            val incoming = pager.getViewPages()[1] ?: throw AssertionError("the incoming page was not built")
            val width = pager.measuredWidth
            assertTrue(
                "the incoming page is sliding in, not parked at the edge",
                Math.abs(incoming.translationX) > 1f && Math.abs(incoming.translationX) < width,
            )
            val animatedPosition = pager.getPositionAnimated()
            assertTrue(
                "the pager is between the two boxes mid-drag (was $animatedPosition)",
                animatedPosition > 0f && animatedPosition < 1f,
            )
            assertTrue(
                "the strip's indicator left the inbox mid-drag",
                field<Int>(strip, "indicatorX") != indicatorAtRest,
            )
            println(
                "pm swipe mid: pagerWidth=$width page0Tx=${pager.getViewPages()[0]?.translationX} " +
                    "page1Tx=${incoming.translationX} animated=${animatedPosition} " +
                    "indicator=${field<Int>(strip, "indicatorX")} atRest=$indicatorAtRest",
            )
            harness.renderApp("pm-telegram-swipe-mid")

            // Release: the pager settles on the sent page. The sent answer is still held, so this is
            // the pager's own clock, never the box's answer.
            releaseAt(pager, 0.2f)
            harness.idleClock(700)
            assertEquals("the pager landed on the sent page", 1, pager.getCurrentPosition())
            assertEquals("the strip followed the pager", 1, strip.currentTabId)
            assertEquals("the screen's own box", PrivateMessageBox.Sent, fragment.currentBox())
            harness.await("the sent box's own route") {
                site.requestPaths.contains("topics/private-messages-sent/baba.json")
            }
            harness.layoutApp()
            assertEquals(
                "sliding to the sent box does not restart the inbox",
                inboxReadsBeforeSwipe,
                site.queriesFor("topics/private-messages/baba.json").count { it == "page=0" },
            )
            // The sent page is on screen with its answer still held: its own loading state, not a
            // blank list and not the inbox's rows.
            harness.renderApp("pm-telegram-swipe-loading")

            // The held answer lands on the sent page only.
            sentAnswer.countDown()
            harness.await("the sent rows") {
                harness.layoutApp()
                fragment.listView()?.adapter?.itemCount == 1
            }
            harness.renderApp("pm-telegram-swipe-sent")

            // Swiping back: the inbox keeps its rows and is not read a second time.
            dragBackward(pager, 0.8f)
            releaseAt(pager, 0.8f)
            harness.idleClock(700)
            assertEquals("back on the inbox page", 0, pager.getCurrentPosition())
            assertEquals("the strip followed back", 0, strip.currentTabId)
            harness.await("the inbox rows are still there") { listRowCount() == 2 }
            assertEquals(
                "switching back does not restart the inbox",
                inboxReadsBeforeSwipe,
                site.queriesFor("topics/private-messages/baba.json").count { it == "page=0" },
            )
            harness.renderApp("pm-telegram-swipe-back")
        } finally {
            sentAnswer.countDown()
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A box the site has nothing in is not a list forever waiting: it says so in its own row, and
     * its shimmer is not left running as if an answer were still on its way.
     */
    @Test
    fun anEmptyBoxSaysSoRatherThanStayingABlankShimmer() {
        site.serveJson("topics/private-messages/baba.json", EMPTY_PAGE)

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox to say it is empty") {
            shownText().any { text -> text == "还没有私信" }
        }
        harness.layoutApp()
        assertEquals("an empty box is its own row, not a blank list", 1, listRowCount())
        harness.renderApp("pm-telegram-empty")
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

    /**
     * The message center hosts this screen as a page under its own title bar ({@code
     * messageCenter=true}). The page must not stack a second row of tabs under the center's, and it
     * must not leave a bar the host would add as an empty band above the list; the default box is
     * the inbox and its rows load either way.
     */
    @Test
    fun anEmbeddedPageDrawsNoTabRowAndKeepsItsBarOutOfTheHost() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment(messageCenterArgs()))
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()

        val fragment = fragment()
        assertNull("the embedded page adds no second tab row", fragment.tabsView())
        assertNull(
            "and no box strip is anywhere under the page",
            findView(fragment.fragmentView, ScrollSlidingTextTabStrip::class.java),
        )
        // The host adds a page's bar only while it says it may; this one says it may not, so no
        // empty band is drawn above the list.
        assertFalse(
            "the page keeps its bar out of the host",
            fragment.getActionBar().shouldAddToContainer(),
        )
        assertNull(
            "and the bar is not part of the page's own view either",
            findView(fragment.fragmentView, org.telegram.ui.ActionBar.ActionBar::class.java),
        )
        assertEquals("the embedded page opens on the inbox", PrivateMessageBox.Inbox, fragment.currentBox())
        assertNotNull("the compose button stays", fragment.composeButton())
        harness.renderApp("pm-telegram-center")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The center's own menu is where its boxes are chosen, since the page has no strip. It keeps
     * 收件箱/已发送/未读 and switches the box (reading that box's route), but not the public channels:
     * the center already carries the chats as a page of its own, so that entry would be a duplicate.
     */
    @Test
    fun theCenterMenuSwitchesBoxesAndOffersNoChannels() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }
        site.serveJson("topics/private-messages-sent/baba.json", harness.fixture("private-messages-sent.json"))

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        val center = io.github.baba983.nativedo.messagecenter.MessageCenterFragment.create(
            io.github.baba983.nativedo.messagecenter.MessageCenterFragment.PAGE_MESSAGES,
        )
        harness.present(center)
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()

        val fragment = fragment()
        // Use the actual title action: a root-view scrim would recursively draw its own popup.
        views(center.actionBar).first { it.contentDescription == "更多" }.performClick()
        harness.layoutApp()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        assertTrue(
            "the center's menu offers the three boxes",
            labels.containsAll(listOf("收件箱", "已发送", "未读")),
        )
        assertFalse(
            "and not the public channels, which the center carries itself",
            labels.contains("公共频道"),
        )
        harness.renderComposite(".amp/in/artifacts/nativedo/pm-telegram-center-menu.png")

        clickMenuItem(latestItemOptionsMenu(harness.activity), "已发送")
        // The choice slides the page pair under the pager's own clock; let it settle before reading.
        harness.idle()
        harness.idleClock(400)
        harness.await("the sent box's own route and rows") {
            site.requestPaths.contains("topics/private-messages-sent/baba.json") && listRowCount() == 1
        }
        harness.layoutApp()
        assertEquals("the menu's choice is the box on screen", PrivateMessageBox.Sent, fragment.currentBox())
        assertNull("and the page still shows no strip", fragment.tabsView())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A private-message row is information, not a replier count. The title stays its name; under it
     * is the site's own excerpt when it has one (pinned in the inbox test above), and otherwise a
     * description of the message that names the author the row's picture is drawn from - never the
     * site's {@code last_poster_username} paired with the reply count, which reads as a reply that
     * never happened, and never a name whose face the row does not draw.
     */
    @Test
    fun aPrivateMessageRowDescribesTheMessageInsteadOfAReplierCount() {
        site.serveJson("topics/private-messages/baba.json", NO_EXCERPT_PAGE)

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(PrivateMessagesFragment.create())
        harness.await("the inbox rows") { listRowCount() == 2 }
        harness.layoutApp()

        val list = fragment().listView() ?: throw AssertionError("no list")
        val first = list.getChildAt(0) as DialogCell
        val second = list.getChildAt(1) as DialogCell

        // The other party started this one, so the row names them - and its last poster is the
        // reader, whose name and reply count must not be shown as if they had replied.
        assertEquals("the title is still the row's name", "关于部署的私信", first.nameLayout?.text?.toString())
        assertEquals("a message the other party started names them", "与 alice 的私信", rowLine(first))
        assertFalse("not the last poster the list happens to carry", rowLine(first).contains("baba"))

        // The reader started this one: say that much, never a replier count for the other party.
        assertEquals("a message the reader started says so", "你发起的私信", rowLine(second))
        assertFalse("and is not a replier count", rowLine(second).contains(" · "))
        harness.renderApp("pm-telegram-center-rows")
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
        // The tap slides the page pair under the pager's own clock; let it settle before reading.
        harness.idleClock(400)
    }

    /** The boxes' pager, wherever it sits under the activity's content view. */
    private fun pagerOf(): ViewPagerFixed? =
        findView(harness.activity.findViewById(android.R.id.content), ViewPagerFixed::class.java)

    /**
     * Drags the pager [travelFraction] of its width to the left (toward the next box) without
     * releasing. The events go through the pager's own touch path, so the pages move the way a
     * finger moves them.
     */
    private fun dragForward(pager: ViewPagerFixed, travelFraction: Float) {
        val width = pager.measuredWidth
        val y = pager.measuredHeight * 0.5f
        val startX = width * 0.9f
        pager.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX, y, 0))
        val steps = 16
        for (step in 1..steps) {
            val x = startX - width * travelFraction * (step.toFloat() / steps)
            pager.onTouchEvent(MotionEvent.obtain(0, step * 16L, MotionEvent.ACTION_MOVE, x, y, 0))
            harness.idle()
        }
    }

    /** The same drag to the right: the previous box, for a swipe back. Held before release. */
    private fun dragBackward(pager: ViewPagerFixed, travelFraction: Float) {
        val width = pager.measuredWidth
        val y = pager.measuredHeight * 0.5f
        val startX = width * 0.1f
        pager.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX, y, 0))
        val steps = 16
        for (step in 1..steps) {
            val x = startX + width * travelFraction * (step.toFloat() / steps)
            pager.onTouchEvent(MotionEvent.obtain(0, step * 16L, MotionEvent.ACTION_MOVE, x, y, 0))
            harness.idle()
        }
    }

    /** Releases the held drag at [fraction] of the pager's width, the way a finger lifts. */
    private fun releaseAt(pager: ViewPagerFixed, fraction: Float) {
        val y = pager.measuredHeight * 0.5f
        pager.onTouchEvent(MotionEvent.obtain(0, 400, MotionEvent.ACTION_UP, pager.measuredWidth * fraction, y, 0))
        harness.idle()
    }

    private fun <T : View> findView(root: View?, type: Class<T>): T? {
        if (root == null) {
            return null
        }
        if (type.isInstance(root)) {
            return type.cast(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findView(root.getChildAt(index), type)?.let { return it }
            }
        }
        return null
    }

    /** A private field of an upstream view, read the way the render tests read the strip's own. */
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

    /** The one line the row draws under its title, read from the cell's own message layout. */
    private fun rowLine(cell: View): String =
        field<android.text.Layout?>(cell, "messageLayout")?.text?.toString().orEmpty()

    /** The bundle the message center passes when it hosts this page. */
    private fun messageCenterArgs(): Bundle = Bundle().apply { putBoolean("messageCenter", true) }

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

        /**
         * Two inbox messages the site has no excerpt for: one a counterpart started (its last poster
         * is the reader) and one the reader started (its last poster is the counterpart). Neither
         * row may read as `last poster · reply count`.
         */
        val NO_EXCERPT_PAGE = """
            {
              "users": [
                {"id": 7, "username": "baba", "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"},
                {"id": 8, "username": "alice", "avatar_template": "/user_avatar/linux.do/alice/{size}/2.png"}
              ],
              "topic_list": {
                "more_topics_url": null,
                "topics": [
                  {
                    "id": 5201, "title": "关于部署的私信", "slug": "pm-deploy",
                    "posts_count": 2, "reply_count": 1, "views": 1, "like_count": 0,
                    "last_posted_at": "2026-10-01T00:00:00.000Z", "last_poster_username": "baba",
                    "category_id": null, "tags": [], "excerpt": "",
                    "notification_level": 2, "unread_posts": 0, "unseen": false,
                    "posters": [{"description": "Original Poster", "user_id": 8}]
                  },
                  {
                    "id": 5202, "title": "我来问你个事", "slug": "pm-ask",
                    "posts_count": 2, "reply_count": 1, "views": 1, "like_count": 0,
                    "last_posted_at": "2026-10-01T01:00:00.000Z", "last_poster_username": "alice",
                    "category_id": null, "tags": [], "excerpt": "",
                    "notification_level": 2, "unread_posts": 0, "unseen": false,
                    "posters": [{"description": "Original Poster", "user_id": 7}]
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
