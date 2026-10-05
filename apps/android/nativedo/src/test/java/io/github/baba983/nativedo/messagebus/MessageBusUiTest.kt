package io.github.baba983.nativedo.messagebus

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatActionCell
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.DialogsActivity
import org.telegram.ui.Components.glass.GlassTabView

/**
 * The message-bus wiring a reader can see: the topic list's "N new topics" pill and the
 * notifications tab's unread badge.
 *
 * <p>Failure modes pinned here: a badge that only moves when a message happens to arrive (the
 * page's own counts are the seed); a pill whose number does not match the category tab in front of
 * the reader; a pill that survives the refresh it just asked for; and a pill that blocks the FAB or
 * the glass tabs instead of sitting under the filter strip.</p>
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
internal class MessageBusUiTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("c/feedback/2/l/latest.json", "category-latest.json")
        site.serveFixture("c/develop/4/l/latest.json", "category-latest.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        // The session belongs to the container, not to the test: letting go of it here is the same
        // call the app's own host makes when it goes to the back.
        NativeDo.container(harness.context).messageBusSession.onBackground()
        site.stop()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    /**
     * The pill counts what the site published for the tab in front of the reader, and acting on it
     * refreshes the list, takes the list to its top and clears the count.
     */
    @Test
    fun theNewTopicsPillFollowsTheCategoryFilterAndClearsWhenTapped() {
        servePollResponses(
            batch(
                latest(topicId = 900, categoryId = 4),
                newTopic(topicId = 901, categoryId = 2),
                newTopic(topicId = 902, categoryId = 4),
            ),
        )

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the bus to count three new topics") {
            harness.idleClock(60)
            pillText() == "3 个新话题" && pill().visibility == View.VISIBLE
        }

        // The pill hangs under the category strip, not over the list's own controls.
        harness.layoutApp()
        val pillLocation = IntArray(2)
        pill().getLocationInWindow(pillLocation)
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        val stripLocation = IntArray(2)
        strip.getLocationInWindow(stripLocation)
        assertTrue(
            "the pill starts below the category strip",
            pillLocation[1] >= stripLocation[1] + strip.height - 1,
        )

        tapCategoryTab(2)
        harness.await("the count of that category") { pillText() == "1 个新话题" }
        tapCategoryTab(4)
        harness.await("the count of the other category") { pillText() == "2 个新话题" }
        tapCategoryTab(0)
        harness.await("all categories again") { pillText() == "3 个新话题" }

        val feedReads = site.requestPaths.count { it == "latest.json" }
        tapPill()
        harness.await("the pill's own refresh") {
            site.requestPaths.count { it == "latest.json" } > feedReads
        }
        harness.await("the pill to go away") {
            harness.idleClock(120)
            pill().visibility != View.VISIBLE
        }
        assertTrue(
            "the count the reader acted on is gone",
            NativeDo.container(harness.context).messageBusSession.incomingTopics.value.isEmpty,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Coming back to the list is not a refresh of it. The pill's count is the reader's own until
     * they act on it (the tap above), which is the only place the count is cleared from; a return
     * from a topic reads nothing and leaves the count where it was. The failure this pins: a
     * return that reads the feed again, which loses the rows the reader came back to and hands
     * them a count they never asked to have reset.
     */
    @Test
    fun aReturnToTheListDoesNotRefreshItOrClearTheCount() {
        servePollResponses(batch(newTopic(topicId = 901, categoryId = 2)))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the bus to count a new topic") { pillText() == "1 个新话题" }
        harness.drawApp()

        val feedReads = site.requestPaths.count { it == "latest.json" }
        val page = harness.visibleFragment() as NativeDoMainTabsActivity.TabPage
        page.onParentBecameVisible()
        harness.idleClock(200)
        harness.drawApp()

        assertEquals(
            "the return reads nothing",
            feedReads,
            site.requestPaths.count { it == "latest.json" },
        )
        assertEquals("and the count is still the reader's", "1 个新话题", pillText())
        assertTrue(
            "the reader has not acted, so the bus still counts it",
            NativeDo.container(harness.context).messageBusSession.incomingTopics.value
                .countForCategory(2) == 1,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The notifications badge is right before the bus says anything (the page's own counts are the
     * seed) and then follows the server, which is the only thing that knows what has been read.
     */
    @Test
    fun theNotificationBadgeIsSeededFromTheBootstrapAndFollowsTheBus() {
        // The first poll waits until the test has seen the page's own count, so the seed and the
        // live message are not asked to be visible in the same instant.
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveMessageBusPoll {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            batch(notification(allUnread = 3))
        }

        harness.bootApp()
        harness.await("the tab host") { harness.topFragment() is NativeDoMainTabsActivity }
        harness.await("the badge seeded by the page") { badgeText() == "5" }
        gate.countDown()
        harness.await("the badge from the notification channel") { badgeText() == "3" }

        val pollPath = site.requestPaths.firstOrNull { it.startsWith("message-bus/") }
            ?: throw AssertionError("the bus never polled")
        assertTrue(
            "the first poll subscribed the reader's own notification channel",
            site.requestBodyFor(pollPath).orEmpty().contains("%2Fnotification%2F7"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pill is the chat's own floating date: it floats over the first row instead of taking a
     * band in the list's layout, and the day/night switch keeps it in place. The failure modes: a
     * pill that pushes the list down (the first row moves when the count arrives), a switch that
     * rebuilds the screen (a second pill instance, drawn from scratch), and - on the night theme -
     * a translucent capsule the first row shows through (the row's white title crossed the pill's
     * label on the device).
     */
    @Test
    fun thePillFloatsOverTheFirstRowAndTheThemeSwitchKeepsIt() {
        servePollResponses(batch(newTopic(topicId = 901, categoryId = 2)))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.layoutApp()
        val list = shownList() ?: throw AssertionError("no topic list on screen")
        val paddingBefore = list.paddingTop
        val firstRowTopBefore = firstRowTop(list)

        harness.await("the pill") {
            harness.idleClock(60)
            pill().visibility == View.VISIBLE
        }
        harness.idleClock(200)
        harness.layoutApp()

        assertEquals("the pill takes no band in the list", paddingBefore, list.paddingTop)
        assertEquals("the first row keeps the place it had", firstRowTopBefore, firstRowTop(list))

        val pill = pill()
        val pillLocation = IntArray(2)
        pill.getLocationInWindow(pillLocation)
        val row = list.findViewHolderForAdapterPosition(0)?.itemView
            ?: throw AssertionError("the list drew no first row")
        val rowLocation = IntArray(2)
        row.getLocationInWindow(rowLocation)
        assertTrue(
            "the pill floats over the first row",
            pillLocation[1] < rowLocation[1] + row.height
                    && pillLocation[1] + pill.height > rowLocation[1],
        )
        harness.renderApp("pill-light")
        val lightCapsule = capsuleFill(pill, Theme.getColor(Theme.key_windowBackgroundWhite))

        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            if (toDark) Theme.getTheme("Dark Blue") else Theme.getTheme("Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark && !DialogsActivity.switchingTheme
            }
            harness.layoutApp()
            assertSame("the switch keeps the pill's own view", pill, fragmentField("newTopicsPill"))
            assertEquals("and keeps it on screen", View.VISIBLE, pill.visibility)
            harness.renderApp("pill-dark")
            val darkCapsule = capsuleFill(pill, Theme.getColor(Theme.key_windowBackgroundWhite))
            assertCapsuleHidesTheRowBehindIt(darkCapsule, capsuleFill(pill, 0xFFFFFFFF.toInt()))
            assertNotEquals("the capsule's colour follows the switch", lightCapsule, darkCapsule)
        } finally {
            Theme.applyTheme(Theme.getTheme("Blue"), false, false)
            DialogsActivity.switchingTheme = false
        }
    }

    /**
     * The failure mode: the night capsule is translucent, so the first row's white title - the
     * content the pill floats over - ghosts through it and the two texts collide (the device
     * showed a bright title stroke crossing the pill's label). Upstream's chat date can be
     * translucent because a chat hides a wallpaper behind it (ChatActionCell:3496-3505 applies
     * the floating factor, three quarters); this list has no wallpaper to hide behind, so the
     * night capsule has to cover what passes under it: the same pixels whether the row behind is
     * the window background or the title's white. The day theme keeps the theme's own tint.
     */
    private fun assertCapsuleHidesTheRowBehindIt(overWindow: Int, overWhite: Int) {
        val influence = Math.abs(Color.red(overWhite) - Color.red(overWindow))
        println("night pill capsule: overWindow=${String.format("0x%08X", overWindow)} overWhite=${String.format("0x%08X", overWhite)} influence=$influence/255")
        assertTrue(
            "the night capsule must be opaque so the row behind cannot show through " +
                "(the row tinted it by $influence of 255)",
            influence <= 2,
        )
    }

    /** The capsule fill the pill draws over [backdrop], sampled above its text line. */
    private fun capsuleFill(pill: ChatActionCell, backdrop: Int): Int {
        val path = ChatActionCell::class.java.getDeclaredField("backgroundPath").apply { isAccessible = true }
            .get(pill) as android.graphics.Path
        val rect = android.graphics.RectF()
        path.computeBounds(rect, true)
        val bitmap = android.graphics.Bitmap.createBitmap(
            pill.width.coerceAtLeast(1), pill.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(backdrop)
        pill.draw(canvas)
        return bitmap.getPixel(rect.centerX().toInt(), (rect.top + 8).toInt())
    }

    // ------------------------------------------------------------------------------------------

    /** Queues one answer per poll; later polls get an empty batch. */
    private fun servePollResponses(vararg answers: String) {
        val index = AtomicInteger(0)
        site.serveMessageBusPoll {
            val next = index.getAndIncrement()
            if (next < answers.size) answers[next] else "[]"
        }
    }

    private fun latest(topicId: Long, categoryId: Int): String = message(
        channel = "/latest",
        messageId = 9000L + topicId,
        data = """{"topic_id":$topicId,"message_type":"latest","payload":{"category_id":$categoryId}}""",
    )

    private fun newTopic(topicId: Long, categoryId: Int): String = message(
        channel = "/new",
        messageId = 9100L + topicId,
        data = """{"topic_id":$topicId,"message_type":"new_topic","payload":{"category_id":$categoryId}}""",
    )

    private fun notification(allUnread: Int): String = message(
        channel = "/notification/7",
        messageId = 9200L,
        data = """{"unread_notifications":$allUnread,"unread_high_priority_notifications":0,"all_unread_notifications_count":$allUnread}""",
    )

    private fun message(channel: String, messageId: Long, data: String): String =
        """{"global_id":$messageId,"message_id":$messageId,"channel":"$channel","data":$data}"""

    private fun batch(vararg messages: String): String =
        messages.joinToString(",", prefix = "[", postfix = "]")

    private fun fragment(): io.github.baba983.nativedo.topic.TopicListFragment =
        harness.visibleFragment() as io.github.baba983.nativedo.topic.TopicListFragment

    private fun pill(): ChatActionCell =
        fragmentField("newTopicsPill") as ChatActionCell

    /** The cell's custom text, where the count is drawn from (ChatActionCell.customText). */
    private fun pillText(): CharSequence? {
        val field = ChatActionCell::class.java.getDeclaredField("customText").apply { isAccessible = true }
        return field.get(pill()) as CharSequence?
    }

    /**
     * A tap on the pill, as the window would hand one over: upstream's own cell turns a touch
     * inside its drawn capsule into the action (ChatActionCell.onTouchEvent), so the capsule's
     * bounds have to exist - that is what drawApp's pass is for - and the touch has to land in
     * the middle of the cell, where the capsule is drawn.
     */
    private fun tapPill() {
        val pill = pill()
        harness.drawApp()
        val x = pill.width / 2f
        val y = pill.height / 2f
        val now = android.os.SystemClock.uptimeMillis()
        pill.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        pill.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_UP, x, y, 0))
        harness.idle()
    }

    private fun shownList(): RecyclerListView? = contentListOf(harness.activity)

    /** The first row's own top, in the list's coordinates: where the pill must not push it. */
    private fun firstRowTop(list: RecyclerListView): Int =
        list.findViewHolderForAdapterPosition(0)?.itemView?.top ?: Int.MIN_VALUE

    private fun strip(): FilterTabsView? = fragmentField("tabsView") as FilterTabsView?

    private fun fragmentField(name: String): Any? {
        val field = io.github.baba983.nativedo.topic.TopicListFragment::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
        return field.get(fragment())
    }

    private fun badgeText(): CharSequence? {
        val host = harness.topFragment() as? NativeDoMainTabsActivity ?: return null
        val tabsField = NativeDoMainTabsActivity::class.java.getDeclaredField("tabs").apply { isAccessible = true }
        val tabs = tabsField.get(host) as Array<*>
        val tab = tabs[1] as GlassTabView
        val counterField = GlassTabView::class.java.getDeclaredField("counter").apply { isAccessible = true }
        val counter = counterField.get(tab) as org.telegram.ui.Components.AnimatedTextView.AnimatedTextDrawable
        return counter.text
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    /** A tap on a category tab, the way the strip's own list hands one over. */
    private fun tapCategoryTab(id: Int) {
        val strip = strip() ?: throw AssertionError("the topic list carries no category strip")
        harness.layoutApp()
        val position = (0 until strip.tabsCount)
            .map { index -> strip.getTab(index)!!.id }
            .indexOf(id)
        if (position < 0) throw AssertionError("the strip has no tab $id")
        val container = strip.tabsContainer
        val tab = (0 until container.childCount)
            .map { index -> container.getChildAt(index) }
            .firstOrNull { it.id == id }
            ?: throw AssertionError("the strip drew no view for tab $id")
        container.clickItem(tab, position)
        harness.idle()
    }
}
