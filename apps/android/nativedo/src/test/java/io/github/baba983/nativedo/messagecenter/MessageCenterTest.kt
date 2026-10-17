package io.github.baba983.nativedo.messagecenter

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.chat.ChatChannelFragment
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.MainTabsLayout

/**
 * The message center: the second main tab, one title bar and one capsule tab strip over 通知/私信/聊天.
 *
 * <p>The failures this pins: a second row of bars (the sub-pages stacking their own action bars
 * under the center's, or the center showing two strips); a sub-tab switch that rebuilds a page and
 * reads it again, losing the rows and the place the reader left it at; a push from a page (a channel,
 * a private message) that comes back to the wrong sub-tab; the operations the pages used to own on
 * their bars (mark-all-read, refresh) going missing once the center owns the bar; a sub-page that
 * fights the main-tab shell for the horizontal swipe; and the settings 私信 entry no longer opening
 * the private messages it names.</p>
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
internal class MessageCenterTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The center's shape: the second main tab is labelled 消息, and it shows exactly one title bar
     * ({@code 消息}) over exactly one strip - upstream's capsule {@link ScrollSlidingTextTabStrip},
     * not an underlined row - carrying 通知/私信/聊天. The failure: a duplicate bar (a page's own
     * action bar under the center's) or a second, wrong-styled strip.
     */
    @Test
    fun theSecondMainTabIsOneTitleBarAndOneCapsuleStrip() {
        site.serveFixture("notifications.json", "notifications.json")
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        val tabs = findTabs(shell.findViewById(android.R.id.content)) ?: throw AssertionError("no main tabs")
        assertEquals("the second entry is the message center", "消息", tabs.getChildAt(1).contentDescription.toString())

        tapMainTab(tabs, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        harness.layoutApp()
        val center = messageCenter()

        val actionBars = views(center.fragmentView!!).filterIsInstance<ActionBar>()
        assertEquals("exactly one title bar under the center", 1, actionBars.size)
        assertEquals("and it is the center's 消息", "消息", actionBars[0].title.toString())

        val strip = stripOf(center)
        assertEquals("upstream's capsule strip, not a second kind", ScrollSlidingTextTabStrip::class.java, strip.javaClass)
        assertEquals("with the three pages", 3, strip.tabsCount)
        assertEquals("通知/私信/聊天", setOf("通知", "私信", "聊天"), tabLabels(strip))
        harness.await("notification rows") { listRowCount() == 4 }
        capture("message-center")
        val original = org.telegram.ui.ActionBar.Theme.getCurrentTheme()
        try {
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
                org.telegram.ui.ActionBar.Theme.getTheme("Dark Blue"),
                false, null, -1, true, null, null, null, true,
            )
            harness.await("dark theme") {
                harness.idleClock(60)
                org.telegram.ui.ActionBar.Theme.isCurrentThemeDark()
            }
            capture("message-center-dark")
        } finally {
            org.telegram.ui.ActionBar.Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Switching pages keeps each page's own state: the notifications page, read once, is the same
     * instance with its rows still there when the reader returns - it is not rebuilt and its feed is
     * not read again. The failures: a sub-tab switch that throws away a page's rows, or re-reads it.
     */
    @Test
    fun switchingPagesKeepsEachPagesOwnStateAndReadsEachOnce() {
        serveNotifications()
        servePrivateMessages()
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        val center = messageCenter()

        harness.await("the notifications page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "NotificationsFragment" &&
                site.requestPaths.contains("notifications.json")
        }
        harness.layoutApp()
        val notifications = center.currentVisibleFragment
        val notificationsReads = site.requestPaths.count { it == "notifications.json" }

        tapStripTab(center, MessageCenterFragment.PAGE_MESSAGES)
        harness.await("the private messages page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "PrivateMessagesFragment" &&
                site.requestPaths.contains("topics/private-messages/baba.json")
        }
        harness.await("private message rows") { listRowCount() == 2 }
        capture("message-center-private")
        val compose = views(center.fragmentView).first { it.contentDescription == "写私信" }
        val mainTabs = findTabs(shell.findViewById(android.R.id.content))!!
        val composeLocation = IntArray(2).also(compose::getLocationOnScreen)
        val tabsLocation = IntArray(2).also(mainTabs::getLocationOnScreen)
        assertTrue("the compose button clears the actual main-tab strip",
            composeLocation[1] + compose.height <= tabsLocation[1])
        tapStripTab(center, MessageCenterFragment.PAGE_CHANNELS)
        harness.await("the chats page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "PublicChannelsFragment" &&
                site.requestPaths.contains("chat/api/channels.json")
        }
        harness.await("channel rows") { listRowCount() == 2 }
        capture("message-center-channels")

        tapStripTab(center, MessageCenterFragment.PAGE_NOTIFICATIONS)
        harness.await("back on the notifications page") { center.currentVisibleFragment === notifications }
        harness.layoutApp()
        assertEquals("notification rows are still present", 4, listRowCount())
        assertEquals(
            "and its feed is not read again for the switch",
            notificationsReads,
            site.requestPaths.count { it == "notifications.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A push from a sub-page and back keeps the sub-page: opening a channel from 聊天 puts the channel
     * screen on top, and back returns to the center still on 聊天 with its list. The failure: a route
     * that resets the center to its first page, so the reader loses the page they were on.
     */
    @Test
    fun aPushFromASubPageAndBackReturnToTheSameSubPage() {
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))
        site.serveJson("chat/api/channels/$CHANNEL_ID.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/$CHANNEL_ID/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("chat/api/channels/$CHANNEL_ID/read.json", harness.fixture("chat-read.json"))

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        val center = messageCenter()

        tapStripTab(center, MessageCenterFragment.PAGE_CHANNELS)
        harness.await("the chats page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "PublicChannelsFragment" &&
                site.requestPaths.contains("chat/api/channels.json")
        }
        val chats = center.currentVisibleFragment
        harness.await("the channel rows") { listRowCount() == 2 }
        harness.layoutApp()

        val list = contentListOf(harness.activity) ?: throw AssertionError("no channel list")
        val first = list.getChildAt(0) ?: throw AssertionError("the list drew no rows")
        list.clickItem(first, positionOf(first))
        harness.await("the channel screen") { harness.visibleFragment() is ChatChannelFragment }

        shell.onBackPressed()
        harness.await("back in the center") { harness.visibleFragment() is MessageCenterFragment }
        harness.layoutApp()
        assertSame("the center returns to the same chats page", chats, center.currentVisibleFragment)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The notifications page keeps its mark-all-read action, now reached from the center's menu (the
     * page draws no bar). The failure: the operation lost when the center took the bar, so the reader
     * can no longer clear the inbox.
     */
    @Test
    fun theNotificationsPageKeepsMarkAllReadInTheCenterMenu() {
        serveNotifications()
        site.serveJson("notifications/mark-read.json", """{"success":"OK"}""")

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        val center = messageCenter()
        harness.await("the notifications page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "NotificationsFragment" &&
                site.requestPaths.contains("notifications.json")
        }
        harness.layoutApp()

        openOptions(center)
        harness.layoutApp()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        assertTrue("the center's menu keeps 全部已读", labels.contains("全部已读"))
        clickMenuItem(latestItemOptionsMenu(harness.activity), "全部已读")

        harness.await("the site to be told to clear the inbox") {
            site.recordedRequests.any { it == "PUT /notifications/mark-read.json" }
        }
        val body = site.requestBodyFor("notifications/mark-read.json").orEmpty()
        assertTrue("and it is the site's own all-read, with no id", !body.contains("id="))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The chats page keeps its refresh action, now reached from the center's menu. The failure: the
     * refresh lost when the center took the bar, so a reader cannot read the channels again.
     */
    @Test
    fun theChatsPageKeepsRefreshInTheCenterMenu() {
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        val center = messageCenter()
        tapStripTab(center, MessageCenterFragment.PAGE_CHANNELS)
        harness.await("the chats page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "PublicChannelsFragment" &&
                site.requestPaths.contains("chat/api/channels.json")
        }
        harness.layoutApp()
        val reads = site.requestPaths.count { it == "chat/api/channels.json" }

        openOptions(center)
        harness.layoutApp()
        val labels = menuItemLabels(latestItemOptionsMenu(harness.activity))
        assertTrue("the center's menu keeps 刷新", labels.contains("刷新"))
        clickMenuItem(latestItemOptionsMenu(harness.activity), "刷新")

        harness.await("the channels read again") {
            site.requestPaths.count { it == "chat/api/channels.json" } > reads
        }
        harness.assertNoTelegramSideEffects()
    }

    /** A horizontal drag switches the visible message category, not the outer main tab. */
    @Test
    fun aHorizontalDragSwitchesMessageCategoriesWithoutLeavingTheCenter() {
        serveNotifications()
        servePrivateMessages()
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        val center = messageCenter()
        harness.await("the notifications page") {
            center.currentVisibleFragment?.javaClass?.simpleName == "NotificationsFragment"
        }
        harness.layoutApp()

        val pager = views(center.fragmentView).filterIsInstance<org.telegram.ui.Components.ViewPagerFixed>().first()
        dragForward(pager, 0.8f)
        harness.idleClock(500)
        harness.await("the swipe opens private messages") {
            center.currentVisibleFragment?.javaClass?.simpleName == "PrivateMessagesFragment" &&
                site.requestPaths.contains("topics/private-messages/baba.json")
        }
        assertSame("the outer tab stays on messages", center, harness.visibleFragment())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The settings 私信 entry still opens the private messages it names - now as the message center's
     * 私信 page, under the center's own bar, and back returns to settings. The failure: the entry left
     * opening nothing, or opening the center on the wrong page.
     */
    @Test
    fun theSettingsMessageEntryOpensTheCenterOnTheMessagesPage() {
        servePrivateMessages()
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 2)
        harness.await("the settings screen") { harness.visibleFragmentName() == "SettingsFragment" }
        harness.layoutApp()

        clickNamedRow("私信")
        harness.await("the message center") { harness.topFragment() is MessageCenterFragment }
        harness.layoutApp()
        val center = harness.topFragment() as MessageCenterFragment
        assertEquals("the settings entry opens the 私信 page", "PrivateMessagesFragment", center.currentVisibleFragment.javaClass.simpleName)
        harness.await("the inbox route") { site.requestPaths.contains("topics/private-messages/baba.json") }
        assertTrue("and nothing else was built for it", site.requestPaths.none { it == "notifications.json" })

        shell.onBackPressed()
        harness.await("back on settings") { harness.visibleFragmentName() == "SettingsFragment" }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun stripOf(center: MessageCenterFragment): ScrollSlidingTextTabStrip =
        views(center.fragmentView).filterIsInstance<ScrollSlidingTextTabStrip>().single()

    private fun openOptions(center: MessageCenterFragment) {
        views(center.actionBar).first { it.contentDescription == "更多" }.performClick()
    }

    private fun capture(name: String) {
        harness.layoutApp()
        // Upstream's capsule clipping animates at draw time; capture the settled frame.
        repeat(30) { harness.idleClock(20); harness.drawApp() }
        harness.renderApp(name)
    }

    private fun messageCenter(): MessageCenterFragment =
        harness.visibleFragment() as? MessageCenterFragment
            ?: throw AssertionError("the message center is not on top")

    private fun serveNotifications() {
        site.serveFixture("notifications.json", "notifications.json")
    }

    private fun servePrivateMessages() {
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else harness.fixture("private-messages.json")
        }
    }

    /** The main-tab strip's child count/list, wherever it sits. */
    private fun findTabs(view: View?): MainTabsLayout? {
        if (view is MainTabsLayout) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findTabs(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    /** Taps the real upstream tab through the strip's touch dispatch, the way a finger does. */
    private fun tapMainTab(tabs: MainTabsLayout, index: Int) {
        harness.idle()
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        val now = android.os.SystemClock.uptimeMillis()
        tabs.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        tabs.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0))
        harness.idle()
    }

    /** Taps the center's capsule tab for [page], the way the strip's own click listener does. */
    private fun tapStripTab(center: MessageCenterFragment, page: Int) {
        harness.layoutApp()
        val strip = stripOf(center)
        val tab = strip.tabsContainer.getChildAt(page)
            ?: throw AssertionError("the strip has no tab at $page")
        tab.performClick()
        harness.idle()
        // The tap slides the page pair under the pager's own clock; let it settle before reading.
        harness.idleClock(500)
    }

    private fun dragForward(pager: org.telegram.ui.Components.ViewPagerFixed, travelFraction: Float) {
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
        pager.onTouchEvent(MotionEvent.obtain(0, (steps + 1) * 16L, MotionEvent.ACTION_UP, startX - width * travelFraction, y, 0))
        harness.idle()
    }

    private fun tabLabels(strip: ScrollSlidingTextTabStrip): Set<String> =
        views(strip).filterIsInstance<TextView>().map { it.text.toString() }.toSet()

    private fun listRowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

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

    /** A tap on the settings row labelled [label], scrolling the list row by row when below the fold. */
    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        val count = list.adapter?.itemCount ?: 0
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            val child = (0 until list.childCount).map { list.getChildAt(it) }
                .firstOrNull { label in textsOf(it) }
            if (child != null) {
                list.clickItem(child, list.getChildAdapterPosition(child))
                harness.idle()
                return
            }
        }
        throw AssertionError("no $label row")
    }

    private fun textsOf(view: View): List<String> {
        val all = views(view)
        return all.filterIsInstance<TextView>().map { it.text.toString() } +
            all.filterIsInstance<SimpleTextView>().mapNotNull { it.text?.toString() } +
            all.filterIsInstance<AnimatedTextView>().mapNotNull { it.text?.toString() }
    }

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }

    private companion object {
        const val TOPIC_ID = 1L
        const val CHANNEL_ID = 71L
        const val EMPTY_PAGE = """{"topic_list":{"more_topics_url":null,"topics":[]}}"""
    }
}
