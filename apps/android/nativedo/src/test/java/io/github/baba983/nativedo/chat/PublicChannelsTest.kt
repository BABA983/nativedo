package io.github.baba983.nativedo.chat

import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
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
import org.junit.Assert.assertFalse
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
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip

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

    /**
     * A site whose chat API route does not exist (the plugin is off, or the route was removed) is
     * not a network outage: the reader has nothing to fix by waiting, and the app must not send them
     * to a WebView to sign in again for it. The failure this pins is the generic "网络错误" the list
     * showed for a 404 as well as for a timeout, which is why a report of that state cannot be
     * diagnosed from the screen.
     */
    @Test
    fun aSiteWithoutTheChatApiSaysTheFeatureIsUnavailable() {
        val attempts = AtomicInteger(0)
        site.serveResponse("chat/api/channels.json") {
            if (attempts.incrementAndGet() == 1) {
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"errors":["Not Found"]}""")
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

        harness.await("the missing chat route to be named as such") {
            shownText().any { text -> text.contains("站点没有提供聊天频道") }
        }
        assertTrue(
            "an absent chat API is not reported as a network error",
            shownText().none { text -> text.contains("网络错误") },
        )
        assertEquals("the failure is not a blank list", 1, listRowCount())

        harness.renderApp("chat-channels-unavailable")
        val list = contentListOf(harness.activity)!!
        val retryRow = list.getChildAt(0)
        list.clickItem(retryRow, positionOf(retryRow))
        harness.await("the retry to bring the rows once the route answers") { listRowCount() == 2 }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An answer that is not channel data - an HTML page from a proxy or a site that answered with
     * something else - is malformed data, not "the network is down". The failure this pins is the
     * same generic state, which sends the reader to retry a request that can never succeed unchanged
     * and hides that the site's answer was the problem.
     */
    @Test
    fun anAnswerThatIsNotChannelDataIsNotCalledANetworkError() {
        site.serveResponse("chat/api/channels.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>maintenance</body></html>")
        }

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())

        harness.await("the unreadable answer to be named as such") {
            shownText().any { text -> text.contains("聊天频道数据无法识别") }
        }
        assertTrue(
            "an unreadable answer is not reported as a network error",
            shownText().none { text -> text.contains("网络错误") },
        )
        assertEquals("the failure is not a blank list", 1, listRowCount())
        harness.renderApp("chat-channels-malformed")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A refusal is a permission state, never a network error and never a logout: the site answered,
     * and the session is still the one the reader signed in with.
     */
    @Test
    fun aRefusedReadIsAPermissionStateNotANetworkError() {
        site.serveResponse("chat/api/channels.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["您没有权限查看聊天频道"],"error_type":"invalid_access"}""")
        }

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())

        harness.await("the refusal to be its own state") {
            shownText().any { text -> text.contains("站点拒绝了这次请求") }
        }
        assertTrue(
            "a refusal is not reported as a network error",
            shownText().none { text -> text.contains("网络错误") },
        )
        assertEquals("the failure is not a blank list", 1, listRowCount())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A challenge on the channel list stays the site's verification state: the session is not
     * logged out, and the list does not pretend the answer was unreadable data or a network error.
     * The failure this pins is a challenge being classified with the chat-list failures above and
     * losing the one tap that can actually resolve it.
     */
    @Test
    fun aChallengeOnTheChannelListIsTheVerificationStateNotALogout() {
        site.serveResponse("chat/api/channels.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head><body>challenge-platform</body></html>")
        }

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())

        harness.await("the challenge to offer its verification page") {
            shownText().any { text -> text.contains("站点要求完成验证") }
        }
        assertTrue(
            "a challenge is not a logout",
            shownText().none { text -> text.contains("登录已失效") },
        )
        assertTrue(
            "a challenge is not the malformed-answer state",
            shownText().none { text -> text.contains("聊天频道数据无法识别") },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The center's capsule and the main tabs are overlays, not a frame: the chats page is under
     * both, so a row can pass behind either of them while the first and last rows still come to
     * rest clear of them.
     *
     * <p>The failures this pins, read from the shipped screen rather than from the padding
     * constants: the page's list starts at the capsule's bottom, so no row can ever be drawn under
     * the capsule; and the list clips at its own padding, so rows stop dead at the tab strip's band
     * instead of passing behind it. A list that half-shows the row above the first and below the
     * last - the shapes the retained capture has, under the capsule and under the tabs - cannot be
     * produced by either fault.</p>
     */
    @Test
    fun theChannelListScrollsBehindTheCapsuleAndTheMainTabs() {
        site.serveJson("chat/api/channels.json", manyChannels(40))

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        val tabs = harness.activity.findViewById<ViewGroup>(android.R.id.content).let(::mainTabsOf)
            ?: throw AssertionError("no main tabs")
        tapMainTab(tabs, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        harness.layoutApp()
        val center = harness.visibleFragment() as io.github.baba983.nativedo.messagecenter.MessageCenterFragment
        val strip = findView(center.fragmentView, ScrollSlidingTextTabStrip::class.java)
            ?: throw AssertionError("no capsule strip")
        tapTab(strip, "聊天")
        harness.await("the channel rows") {
            harness.layoutApp()
            (center.currentVisibleFragment as? PublicChannelsFragment)
                ?.listViewForTest()?.adapter?.itemCount == 40
        }
        harness.layoutApp()
        val page = center.currentVisibleFragment as PublicChannelsFragment
        val list = page.listViewForTest() ?: throw AssertionError("no list")

        // The capsule is an overlay: the page starts at its top, never below its bottom, or no row
        // could be drawn behind it.
        val listAt = IntArray(2).also(list::getLocationOnScreen)
        val stripAt = IntArray(2).also(strip::getLocationOnScreen)
        val capsuleBottom = stripAt[1] + strip.height
        assertTrue(
            "the chats page starts above the capsule so its rows can pass behind it " +
                "(list top ${listAt[1]}, capsule top ${stripAt[1]})",
            listAt[1] <= stripAt[1] + 1,
        )
        assertFalse(
            "and the list lets rows draw through its own padding instead of cutting them there",
            list.clipToPadding,
        )
        assertTrue(
            "the capsule's band is kept above the first row (padding top ${list.paddingTop})",
            listAt[1] + list.paddingTop >= capsuleBottom,
        )
        val firstRow = topRowOf(list)
        assertTrue(
            "the first row begins just below the capsule (row top ${firstRow.top + listAt[1]}, " +
                "capsule bottom $capsuleBottom)",
            firstRow.top + listAt[1] >= capsuleBottom - 1 &&
                firstRow.top + listAt[1] <= capsuleBottom + dp(16),
        )

        // Half-way down the list: one row is partly behind the capsule and another partly behind
        // the tab strip. These are laid-out rows in the padding bands - the region a clipping list
        // would leave empty - not padding numbers.
        repeat(2) { list.scrollBy(0, dp(130)); harness.layoutApp() }
        harness.idleClock(100)
        harness.layoutApp()
        assertTrue(
            "a visible row straddles the capsule band's lower edge",
            (0 until list.childCount).map(list::getChildAt).any {
                it.top < list.paddingTop && it.bottom > list.paddingTop
            },
        )
        var bottomRow = bottomRowOf(list)
        if (bottomRow.bottom == list.height - list.paddingBottom) {
            // The rows happened to land exactly on the band's edge; a few more dp put one inside
            // it (the band is 76dp and a row is 72dp, so this cannot need a second nudge).
            list.scrollBy(0, dp(8))
            harness.layoutApp()
            bottomRow = bottomRowOf(list)
        }
        assertTrue(
            "and another lies inside the tab strip's band (row bottom ${bottomRow.bottom}, " +
                "list ${list.height} with ${list.paddingBottom} reserved)",
            bottomRow.bottom > list.height - list.paddingBottom && bottomRow.top < list.height,
        )
        harness.drawApp()
        harness.renderApp("chat-channels-scroll-under")

        // The end is still reachable: the last row comes to rest at the reserved band, which is
        // the tab strip's own, not a blank band above it.
        repeat(6) { list.scrollBy(0, dp(100000)); harness.layoutApp() }
        harness.idleClock(100)
        harness.layoutApp()
        val last = bottomRowOf(list)
        val resting = list.height - list.paddingBottom
        assertTrue(
            "the last row comes to rest at the reserved band (bottom ${last.bottom}, band $resting)",
            Math.abs(last.bottom - resting) <= 1,
        )
        val lastAt = IntArray(2).also(last::getLocationOnScreen)
        val tabsAt = IntArray(2).also(tabs::getLocationOnScreen)
        val gap = tabsAt[1] - (lastAt[1] + last.height)
        assertTrue("the last row is reachable above the tabs (gap $gap px)", gap >= 0)
        assertTrue("and no blank band is left between them (gap $gap px)", gap <= dp(8))
        harness.drawApp()
        harness.renderApp("chat-channels-scroll-under-end")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The center measures its bar and capsule once and may hand that band over before the page has
     * any views. The page stores it and pads from it when the list is built; a re-read of the list
     * keeps it, and a second measurement replaces it rather than adding to it.
     *
     * <p>The failures this pins: an inset dropped because the page had no list yet (the first rows
     * then start under the capsule); a list whose padding is recomputed from scratch on a reload
     * and loses the band; and an inset that accumulates on every callback, walking the list further
     * down the screen.</p>
     */
    @Test
    fun theCentersTopInsetArrivesBeforeTheViewsAndSurvivesAReload() {
        site.serveJson("chat/api/channels.json", manyChannels(40))

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        // The band arrives first: the host has measured its bar and capsule and the page is built
        // after it, which is the order the center's own layout listener produces.
        val inset = dp(120)
        val page = PublicChannelsFragment(messageCenterArgs(hasMainTabs = true))
        page.setMessageCenterTopInset(inset)
        harness.present(page)
        harness.await("the channel rows") { page.listViewForTest()?.adapter?.itemCount == 40 }
        harness.layoutApp()
        val list = page.listViewForTest() ?: throw AssertionError("no list")
        assertEquals(
            "the capsule band is kept above the first row, over the page's own 4dp",
            dp(4) + inset,
            list.paddingTop,
        )
        assertEquals(
            "and the tab band is the bottom reservation",
            dp(4) + dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS),
            list.paddingBottom,
        )

        // A re-read of the list keeps the band: the list is the same view, padded the same way.
        val reads = site.requestPaths.count { it == "chat/api/channels.json" }
        page.refreshForTest()
        harness.await("the list read again") {
            site.requestPaths.count { it == "chat/api/channels.json" } > reads
        }
        harness.await("the refreshed rows") { page.listViewForTest()?.adapter?.itemCount == 40 }
        harness.layoutApp()
        assertEquals("a re-read keeps the capsule band", dp(4) + inset, list.paddingTop)
        assertFalse("and the rows still draw through it", list.clipToPadding)

        // The navigation inset is added below the tab band once, and a later measurement of the
        // capsule replaces the stored band instead of accumulating on it.
        page.onInsets(0, 0, 0, 37)
        assertEquals(
            "the navigation inset is added to the band once",
            dp(4) + 37 + dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS),
            list.paddingBottom,
        )
        page.setMessageCenterTopInset(inset + dp(10))
        assertEquals(
            "the second measurement replaces the first",
            dp(4) + inset + dp(10),
            list.paddingTop,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Without a host, the list has no capsule over it: the page's own bar is above it, so the list
     * keeps its own 4dp at the top and only the navigation inset below its last row. The failure
     * this pins: giving the standalone list the center's band as well, which pushes its first row a
     * capsule's height down the screen.
     */
    @Test
    fun aStandaloneChannelListKeepsItsOwnTopPadding() {
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))

        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.present(PublicChannelsFragment.create())
        harness.await("the channel rows") { listRowCount() == 2 }
        harness.layoutApp()
        val page = harness.visibleFragment() as PublicChannelsFragment
        val list = page.listViewForTest() ?: throw AssertionError("no list")

        assertEquals("the standalone list has no capsule band above its rows", dp(4), list.paddingTop)
        page.onInsets(0, 0, 0, 37)
        assertEquals(
            "and reserves only the navigation inset below its last row",
            dp(4) + 37,
            list.paddingBottom,
        )
        assertFalse("while its rows draw through that padding", list.clipToPadding)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /** A channels page of [count] public channels, each a plain row with a last message. */
    private fun manyChannels(count: Int): String {
        val channels = (0 until count).joinToString(",") { index ->
            """{"id": ${700 + index}, "title": "频道 $index", "slug": "channel-$index",
               "status": "open", "memberships_count": 3,
               "last_message": {"id": ${9000 + index}, "message": "第 $index 条消息",
                 "created_at": "2026-10-02T09:02:00.000Z", "chat_channel_id": ${700 + index},
                 "user": {"id": 8, "username": "alice", "name": "Alice"}},
               "meta": {"can_join_chat_channel": true}}"""
        }
        return """{"channels": [$channels]}"""
    }

    /** Directly below the main tabs is where a hosted page's own band starts. */
    private fun dp(value: Int): Int = org.telegram.messenger.AndroidUtilities.dp(value.toFloat())

    /** The bundle the message center passes when it hosts this page. */
    private fun messageCenterArgs(hasMainTabs: Boolean = false): Bundle = Bundle().apply {
        putBoolean("messageCenter", true)
        putBoolean("hasMainTabs", hasMainTabs)
    }

    /** The row whose own bounds reach highest in [list]: the one scrolling out under the top band. */
    private fun topRowOf(list: RecyclerListView): View =
        (0 until list.childCount).map { list.getChildAt(it) }.minByOrNull { it.top }
            ?: throw AssertionError("the list drew no rows")

    /** The row whose own bounds reach lowest in [list]: the one scrolling in under the bottom band. */
    private fun bottomRowOf(list: RecyclerListView): View =
        (0 until list.childCount).map { list.getChildAt(it) }.maxByOrNull { it.bottom }
            ?: throw AssertionError("the list drew no rows")

    /** The main-tab strip's own view, wherever it sits under [root]. */
    private fun mainTabsOf(root: View): org.telegram.ui.MainTabsLayout? =
        findView(root, org.telegram.ui.MainTabsLayout::class.java)

    /** Taps the real upstream main tab through the strip's touch dispatch, the way a finger does. */
    private fun tapMainTab(tabs: org.telegram.ui.MainTabsLayout, index: Int) {
        harness.idle()
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        val now = android.os.SystemClock.uptimeMillis()
        tabs.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        tabs.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0))
        harness.idle()
    }

    /** A tap on the center's capsule tab labelled [label], handed over the way the strip's click is. */
    private fun tapTab(strip: ScrollSlidingTextTabStrip, label: String) {
        harness.layoutApp()
        val tab = views(strip).filterIsInstance<TextView>().firstOrNull { it.text.toString() == label }
            ?: throw AssertionError("the strip shows no \"$label\" tab")
        tab.performClick()
        harness.idle()
        // The tap slides the page pair under the pager's own clock; let it settle before reading.
        harness.idleClock(400)
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

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }

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
