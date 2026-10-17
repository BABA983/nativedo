package io.github.baba983.nativedo.messagecenter

import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.ViewPagerFixed
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
     * The capsule must keep the row it controls inside itself on every one of its three states.
     *
     * <p>The failures this pins, measured on the drawn strip rather than on the theme or the page:
     * a strip that is laid out before its closed-capsule inset exists, so the tab row starts at the
     * strip's own edge while the capsule's clip - and the tap highlight - start six dp inside it:
     * the selected label then sits flush to the capsule's left and off-centre in its highlight. And
     * a wrap-content strip whose tabs already fit can still be scrolled by that inset, so choosing
     * 聊天 shifts the whole row twelve dp left: 通知's label is cut at the capsule's left edge and
     * 聊天's highlight runs past its right one, and the shift stays while that page is chosen. Both
     * are visible in the light capsule and clearer in the dark one.</p>
     */
    @Test
    fun theCapsuleKeepsEveryTabsLabelAndHighlightInsideItselfOnAllThreePages() {
        serveNotifications()
        servePrivateMessages()
        site.serveJson("chat/api/channels.json", harness.fixture("chat-channels.json"))

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        harness.layoutApp()
        val center = messageCenter()
        val strip = stripOf(center)

        // The row of tabs sits inside the strip's own closed-capsule padding, which is what gives
        // the highlight and the clip the same six dp to stand on. A strip measured before that
        // padding existed has its row at the strip's edge, and a scroll range as wide as the
        // padding - which is what let a later tap scroll it.
        assertEquals(
            "the tab row is laid out inside the strip's own inset",
            strip.paddingLeft,
            strip.tabsContainer.left,
        )
        assertEquals(
            "and the capsule has no room to scroll a row that fits",
            strip.tabsContainer.measuredWidth + strip.paddingLeft + strip.paddingRight,
            strip.measuredWidth,
        )

        for (page in 0 until 3) {
            tapStripTab(center, page)
            assertCapsuleHoldsEveryTab(strip, page)
            capture(LIGHT_CAPTURES[page])
        }

        // The two states the device showed wrong, in the dark capsule where the cut edge and the
        // off-centre highlight are clearest: 通知 chosen (its label sat flush at the left) and 聊天
        // chosen (the row the scroll moved out from under the capsule).
        val original = org.telegram.ui.ActionBar.Theme.getCurrentTheme()
        try {
            tapStripTab(center, 0)
            ensureDarkTheme()
            assertCapsuleHoldsEveryTab(strip, 0)
            capture("message-center-capsule-notifications-dark")
            tapStripTab(center, 2)
            ensureDarkTheme()
            assertCapsuleHoldsEveryTab(strip, 2)
            capture("message-center-capsule-chats-dark")
        } finally {
            org.telegram.ui.ActionBar.Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /** Settle the host's animated theme switch before capturing either dark page. */
    private fun ensureDarkTheme() {
        harness.idleClock(1000)
        org.telegram.ui.ActionBar.Theme.applyTheme(
            org.telegram.ui.ActionBar.Theme.getTheme("Dark Blue"), false, false,
        )
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
            org.telegram.messenger.NotificationCenter.didSetNewTheme, true,
        )
        assertTrue(org.telegram.ui.ActionBar.Theme.isCurrentThemeDark())
    }

    /**
     * The capsule holds the row on [page]: nothing is scrolled, every tab's own bounds are inside
     * the capsule's clip, and the selected tab's highlight is centred on the label it marks.
     */
    private fun assertCapsuleHoldsEveryTab(strip: ScrollSlidingTextTabStrip, page: Int) {
        // The highlight's own bounds are written during the strip's draw pass, so read them after
        // one: a stale highlight would say the previous page was still marked.
        harness.layoutApp()
        harness.drawApp()
        val capsule = RectF().also { strip.clipPath.computeBounds(it, true) }
        val tabs = strip.tabsContainer
        assertEquals("page $page does not scroll a capsule whose tabs fit", 0, strip.scrollX)

        for (index in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(index)
            val left = tabs.left + tab.left - strip.scrollX
            val right = left + tab.width
            assertTrue(
                "page $page: tab $index starts inside the capsule (${left} vs ${capsule.left})",
                left >= capsule.left - 0.5f,
            )
            assertTrue(
                "page $page: tab $index ends inside the capsule (${right} vs ${capsule.right})",
                right <= capsule.right + 0.5f,
            )
        }

        val selected = tabs.getChildAt(page)
        val highlight = strip.selectorDrawable.bounds
        assertTrue(
            "page $page: the highlight clears the capsule's left edge",
            highlight.left > capsule.left,
        )
        assertTrue(
            "page $page: the highlight clears the capsule's right edge",
            highlight.right < capsule.right,
        )
        val labelCentre = tabs.left + selected.left + selected.width / 2f
        assertEquals(
            "page $page: the highlight is centred on its own label",
            labelCentre,
            highlight.exactCenterX(),
            1.5f,
        )
    }

    /**
     * The capsule is a fixed surface over the pages, not a band the pages start below.
     *
     * <p>The failures this pins, measured on the shipped screen and each page's own list: a pager
     * laid out under the strip, so no row can ever be drawn in the capsule's band, and the first
     * row starting beneath an opaque strip; and the opposite - moving the pages up without the
     * pages knowing, so a long list's first row starts hidden behind the capsule. Each page must
     * begin where the bar ends (its list starts at the band's top edge), reserve the capsule's own
     * height above its first row while still drawing into that padding
     * ({@code clipToPadding=false}), and then let rows pass through the band as it scrolls. The
     * band around the glass capsule carries no surface of its own, so what shows there is the page.</p>
     */
    @Test
    fun thePagesScrollUnderTheFixedTopCapsuleInsteadOfBelowAnOpaqueBand() {
        site.serveJson("notifications.json", manyNotifications(30))
        site.serveJsonFor("topics/private-messages/baba.json") { path ->
            if (path.contains("page=1")) EMPTY_PAGE else manyPrivateMessages(30)
        }
        site.serveJson("chat/api/channels.json", manyChannels(30))

        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        tapMainTab(findTabs(shell.findViewById(android.R.id.content))!!, 1)
        harness.await("the message center") { harness.visibleFragmentName() == "MessageCenterFragment" }
        // The header's own layout pass is what moves the pager under the bar (the margins are
        // applied from the bar's layout listener), so read the geometry after a second pass.
        harness.layoutApp()
        harness.layoutApp()
        val center = messageCenter()
        val band = stripOf(center).parent as View
        val pager = views(center.fragmentView).filterIsInstance<ViewPagerFixed>().single()
        val actionBar = views(center.fragmentView).filterIsInstance<ActionBar>().single()

        // The page area begins where the bar ends, so it includes the band the capsule floats in.
        // The failure: the pager laid out below the band, which no row can be drawn under.
        assertEquals(
            "the pages start under the capsule's band, not below it",
            actionBar.bottom, pager.top,
        )
        assertEquals("the band sits over the pages, at the bar's edge", actionBar.bottom, band.top)
        assertEquals(
            "and the pages fill the window under the band",
            center.fragmentView.height - actionBar.bottom, pager.height,
        )
        assertTrue("the band draws no surface of its own", isTransparent(band.background))
        val bandTop = locationOf(band)[1]
        val bandBottom = bandTop + band.height

        for (page in 0 until 3) {
            val name = PAGE_NAMES[page]
            tapStripTab(center, page)
            // The current page's own list, not a row list the switch has already detached: the
            // previous page's view leaves the pager with its rows still in its adapter.
            harness.await("the $name rows") {
                harness.layoutApp()
                (listIn(center.currentVisibleFragment?.fragmentView)?.adapter?.itemCount ?: 0) >= 20
            }
            harness.layoutApp()
            val list = listIn(center.currentVisibleFragment?.fragmentView)
                ?: throw AssertionError("$name has no list")
            assertFalse("$name draws its rows into its top padding", list.clipToPadding)
            assertTrue(
                "$name reserves the capsule's height above its first row " +
                    "(${list.paddingTop} vs ${band.height})",
                list.paddingTop >= band.height,
            )
            assertEquals("$name's list begins at the band's edge", bandTop, locationOf(list)[1])
            val firstRow = list.getChildAt(0) ?: throw AssertionError("$name drew no rows")
            assertTrue(
                "$name's first row starts below the capsule " +
                    "(${locationOf(firstRow)[1]} vs $bandBottom)",
                locationOf(firstRow)[1] >= bandBottom - 1,
            )
            capture("message-center-scroll-$name-top")

            // A long row continues through the band: the capsule is fixed over the page, so
            // scrolling moves rows under it instead of stopping the list at the band's edge.
            list.scrollBy(0, dp(100))
            harness.layoutApp()
            harness.drawApp()
            assertEquals("the capsule stays put while $name scrolls", bandTop, locationOf(band)[1])
            val rows = (0 until list.childCount).mapNotNull { list.getChildAt(it) }
            val underCapsule = rows.filter { row ->
                val top = locationOf(row)[1]
                top < bandBottom && top + row.height > bandTop
            }
            assertTrue("$name draws rows under the capsule, not below its band", underCapsule.isNotEmpty())
            assertTrue(
                "$name's rows run on above the band's top edge instead of being cut there",
                underCapsule.any { locationOf(it)[1] < bandTop },
            )
            capture("message-center-scroll-$name-middle")

            repeat(3) { list.scrollBy(0, 100000); harness.layoutApp() }
            harness.idleClock(100)
            harness.layoutApp()
            capture("message-center-scroll-$name-bottom")
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

    /** Where [view] sits on the screen, so a fixed surface and a scrolling row are comparable. */
    private fun locationOf(view: View): IntArray = IntArray(2).also(view::getLocationOnScreen)

    /** A drawable that paints nothing: no background, or a fully transparent colour. */
    private fun isTransparent(background: android.graphics.drawable.Drawable?): Boolean =
        background == null ||
            (background is ColorDrawable && background.color ushr 24 == 0)

    /** The row list inside [root]: a page's own list, wherever the page nests it. */
    private fun listIn(root: View?): RecyclerListView? {
        if (root == null) return null
        if (root is RecyclerListView && root.isShown) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                listIn(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun dp(value: Int): Int = AndroidUtilities.dp(value.toFloat())

    /** A notifications page of [count] rows: enough for rows to pass the fixed capsule. */
    private fun manyNotifications(count: Int): String {
        val rows = (0 until count).joinToString(",") { index ->
            """{"id": ${4100 + index}, "user_id": 7, "notification_type": 2,
               "read": false, "high_priority": false,
               "created_at": "2026-10-0${1 + index % 9}T09:20:00.000Z",
               "post_number": ${index + 1}, "topic_id": ${4000 + index},
               "slug": "notification-$index", "fancy_title": "第 $index 条通知",
               "data": {"topic_title": "第 $index 条通知", "display_username": "searcher",
                        "display_name": "Searcher", "original_username": "searcher",
                        "original_post_id": ${88000 + index}, "original_post_type": 1,
                        "revision_number": null, "reply_to_post_number": null},
               "acting_user_name": "Searcher",
               "acting_user_avatar_template": "/user_avatar/linux.do/searcher/{size}/3.png"}"""
        }
        return """{"notifications": [$rows], "total_rows_notifications": $count,
                   "seen_notification_id": 0, "load_more_notifications": null}"""
    }

    /** A topic_list page of [count] private messages, like the inbox fixture's rows. */
    private fun manyPrivateMessages(count: Int): String {
        val topics = (0 until count).joinToString(",") { index ->
            """{"id": ${6000 + index}, "title": "第 $index 条私信", "slug": "pm-$index",
               "posts_count": 2, "reply_count": 1, "views": 1, "like_count": 0,
               "last_posted_at": "2026-10-01T00:00:00.000Z", "last_poster_username": "alice",
               "category_id": null, "tags": [], "excerpt": "第 $index 条私信的内容",
               "notification_level": 2, "unread_posts": 0, "unseen": false,
               "posters": [{"description": "Original Poster", "user_id": 8}]}"""
        }
        return """{"users": [{"id": 7, "username": "baba"}, {"id": 8, "username": "alice"}],
                   "topic_list": {"more_topics_url": null, "topics": [$topics]}}"""
    }

    /** A channels page of [count] public channels, like the channels fixture's rows. */
    private fun manyChannels(count: Int): String {
        val channels = (0 until count).joinToString(",") { index ->
            """{"id": ${71 + index}, "title": "频道 $index", "slug": "channel-$index",
               "description": "第 $index 个频道", "status": "open",
               "memberships_count": ${10 + index},
               "last_message": {"id": ${9000 + index}, "message": "第 $index 条消息",
                                "cooked": "<p>第 $index 条消息</p>",
                                "created_at": "2026-10-02T09:02:00.000Z",
                                "chat_channel_id": ${71 + index},
                                "user": {"id": 8, "username": "alice", "name": "Alice",
                                         "avatar_template": "/user_avatar/linux.do/alice/{size}/2.png"}},
               "meta": {"can_join_chat_channel": true,
                        "message_bus_last_ids": {"channel_message_bus_last_id": ${4100 + index},
                                                 "new_messages": ${4200 + index}}}}"""
        }
        return """{"channels": [$channels], "meta": {"load_more_url": null}}"""
    }

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
        /** The three capsule states, named for the page each one shows. */
        val LIGHT_CAPTURES = arrayOf(
            "message-center-capsule-notifications",
            "message-center-capsule-messages",
            "message-center-capsule-chats",
        )
        /** The three pages, named for the scroll-under captures. */
        val PAGE_NAMES = arrayOf("notifications", "messages", "channels")
        const val TOPIC_ID = 1L
        const val CHANNEL_ID = 71L
        const val EMPTY_PAGE = """{"topic_list":{"more_topics_url":null,"topics":[]}}"""
    }
}
