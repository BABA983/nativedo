package io.github.baba983.nativedo.notifications

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
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
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.RecyclerListView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What a tap on a notification does, end to end.
 *
 * <p>The failures this pins: a tap that only navigates and never tells the site the notification
 * was read; a topic-less notification (a badge, the types the site gives no post) that is not
 * tappable at all, so it can never be cleared without the action bar; a read shown as done before
 * the site took the write, which turns a refusal into a lie; a badge moved by the screen's own
 * guess instead of the site's count; and an all-read action that marks one row or nothing.</p>
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
internal class NotificationsActionsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private var container: AppContainer? = null

    @Before
    fun setUp() {
        site.start()
        // The badge seeds from the page; a later page says the inbox is empty.
        site.serveJson("", bootstrapHtml(allUnread = 5))
        site.serveJson("notifications.json", notificationsJson())
        site.serveJson("notifications/mark-read.json", """{"success":"OK"}""")
        site.serveJson("t/$TOPIC_ID/$FLOOR.json", topicAround(FLOOR))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context).also { container = it } }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        container = null
        site.stop()
    }

    @Test
    fun tappingANotificationMarksItReadBeforeOpeningItsFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()

        val cells = notificationCells()
        assertEquals("one row per notification", 2, cells.size)
        listView()!!.clickItem(cells[0], positionOf(cells[0]))

        harness.await("the site to be told the notification is read") {
            site.recordedRequests.any { it == "PUT /notifications/mark-read.json" }
        }
        val body = site.requestBodyFor("notifications/mark-read.json").orEmpty()
        println("notifications: mark-read body = $body")
        assertTrue("the write names the notification the reader tapped", body.contains("id=$NOTIFICATION_ID"))

        harness.await("the topic at the notification's floor") {
            site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json")
        }
        harness.await("the floor to be drawn") { drawnPostIds().isNotEmpty() }
        assertEquals(
            "the floor the notification is about",
            POST_ID_BASE + FLOOR,
            drawnPostIds().first(),
        )
        harness.renderApp("notification-marked-read")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aTopiclessNotificationIsMarkedReadWithoutOpeningAnything() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()
        val stackBefore = harness.fragmentStack().size

        val badge = notificationCells().last()
        listView()!!.clickItem(badge, positionOf(badge))

        harness.await("the site to be told the topic-less notification is read") {
            site.recordedRequests.any { it == "PUT /notifications/mark-read.json" }
        }
        assertEquals(
            "the badge's own id, not a topic",
            "id=$BADGE_ID",
            site.requestBodyFor("notifications/mark-read.json"),
        )
        assertEquals("no screen was pushed for a notification with no post", stackBefore, harness.fragmentStack().size)
        assertTrue(
            "nothing was read as a topic",
            site.requestPaths.none { it.startsWith("t/") },
        )
        harness.await("the row to lose its unread badge") {
            notificationCells().last().getHasUnread().not()
        }
        harness.layoutApp()
        harness.renderApp("notification-topicless-read")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theAllReadActionClearsTheInboxAndFollowsTheSiteCount() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the badge seeded by the page") {
            NativeDo.container(harness.context).messageBusSession.notificationCounts.value.allUnread == 5
        }
        // The site's own counts have moved since the page was rendered: the badge must be re-read
        // from the site after the write, never moved by the screen.
        site.serveJson("", bootstrapHtml(allUnread = 0))
        presentNotifications()

        allReadItem().performClick()
        harness.await("the site to be told to clear the inbox") {
            site.recordedRequests.any { it == "PUT /notifications/mark-read.json" }
        }
        val body = site.requestBodyFor("notifications/mark-read.json").orEmpty()
        println("notifications: all-read body = '$body'")
        assertTrue("no id is the site's own all-read", !body.contains("id="))
        harness.await("every row to lose its unread badge") {
            notificationCells().all { !it.getHasUnread() }
        }
        harness.await("the badge to follow the site's count") {
            NativeDo.container(harness.context).messageBusSession.notificationCounts.value.allUnread == 0
        }
        harness.renderApp("notifications-all-read")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedReadLeavesTheRowUnreadAndSaysSo() {
        site.serveResponse("notifications/mark-read.json") {
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["不能标记已读"]}""")
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()
        val stackBefore = harness.fragmentStack().size

        val cells = notificationCells()
        listView()!!.clickItem(cells[0], positionOf(cells[0]))

        val texts = awaitBulletin("标记已读失败")
        println("notifications: refusal bulletin = $texts")
        assertTrue(
            "the row keeps its unread badge after a refusal",
            notificationCells()[0].getHasUnread(),
        )
        assertEquals("and nothing was opened", stackBefore, harness.fragmentStack().size)
        harness.renderApp("notification-refused")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A tap on a notification the site already read. The failure this pins: writing again on every
     * tap, so opening a row the reader has seen tells the site about a change that already happened.
     */
    @Test
    fun anAlreadyReadNotificationOpensWithoutAnotherWrite() {
        site.serveJson("notifications.json", notificationsJson(readFirst = true))
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()

        val cells = notificationCells()
        assertTrue("the first row is already read", !cells[0].getHasUnread())
        listView()!!.clickItem(cells[0], positionOf(cells[0]))
        harness.await("the topic at the notification's floor") {
            site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json")
        }
        assertEquals("an already-read notification is not written again", 0, markReadRequests())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A second tap while the first write is still out. The failure this pins: both taps send their
     * own mark-read, so the site is told twice (and a slow first answer can still navigate after
     * the reader has moved on).
     */
    @Test
    fun aSecondTapWhileTheWriteIsInFlightOnlyWritesOnce() {
        val release = CountDownLatch(1)
        site.serveResponse("notifications/mark-read.json") {
            release.await(30, TimeUnit.SECONDS)
            okResponse()
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()

        val cells = notificationCells()
        listView()!!.clickItem(cells[0], positionOf(cells[0]))
        harness.await("the write to reach the site") { markReadRequests() >= 1 }
        // The second tap lands while the first write is still out.
        listView()!!.clickItem(cells[0], positionOf(cells[0]))
        harness.idle()
        release.countDown()
        harness.await("the confirmed read to open its topic") {
            site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json")
        }
        assertEquals("a double tap is one write, not two", 1, markReadRequests())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A read that lands after the account changed. The failure this pins: a slow mark-read from the
     * previous reader repainting the next reader's list by id - the next account's own notification
     * with the same id would show as read, and its badge would be re-read, for a write it never
     * asked for.
     */
    @Test
    fun aLateReadFromThePreviousAccountDoesNotMarkTheNextAccountsRows() {
        val release = CountDownLatch(1)
        site.serveResponse("notifications/mark-read.json") {
            release.await(30, TimeUnit.SECONDS)
            okResponse()
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()
        val stackBefore = harness.fragmentStack().size

        val cells = notificationCells()
        listView()!!.clickItem(cells[0], positionOf(cells[0]))
        harness.await("the write to reach the site") { markReadRequests() >= 1 }

        // The account changes while the write is out; the next account's list carries the same ids.
        switchAccount("carol")
        harness.await("the next account's list") {
            rowCount() == 2 && notificationCells().getOrNull(0)?.getHasUnread() == true
        }

        release.countDown()
        harness.idleClock(400)
        assertTrue(
            "the previous account's write must not mark the next account's row",
            notificationCells()[0].getHasUnread(),
        )
        assertEquals(
            "and it must not open anything for the next account",
            stackBefore,
            harness.fragmentStack().size,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A notification that arrives while the all-read write is out. The failure this pins: the
     * answer marking every row on screen, including one that was not part of the request, so a
     * notification that arrived after the reader asked to clear the inbox is shown as already read.
     */
    @Test
    fun theAllReadOnlyMarksTheRowsThatExistedWhenItStarted() {
        val releasePage = CountDownLatch(1)
        site.serveJsonFor("notifications.json") { path ->
            if (path.contains("offset=2")) {
                // This small list automatically requests page two as soon as it is laid out. Hold
                // the answer until the all-read starts so the third row really arrives afterward.
                releasePage.await(30, TimeUnit.SECONDS)
                pageWithThirdNotification()
            } else notificationsJson(totalRows = 3)
        }
        val release = CountDownLatch(1)
        site.serveResponse("notifications/mark-read.json") {
            release.await(30, TimeUnit.SECONDS)
            okResponse()
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()
        assertEquals(2, rowCount())

        allReadItem().performClick()
        harness.await("the all-read write to reach the site") { markReadRequests() >= 1 }

        // A newer notification is already on the site and the list fetches it while the write is out.
        loadNextPage()
        releasePage.countDown()
        harness.await("the newer notification to join the list") { rowCount() == 3 }
        harness.layoutApp()

        release.countDown()
        harness.await("the rows that were there to lose their badges") {
            val cells = notificationCells()
            cells.size == 3 && cells.take(2).all { !it.getHasUnread() }
        }
        assertTrue(
            "the notification that arrived after the write started stays unread",
            notificationCells().last().getHasUnread(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An all-read and a row tap at the same time. The failure this pins: both writes going out, so
     * the all-read and the single row's write race on the site and the badge is re-read twice.
     */
    @Test
    fun aTapWhileTheAllReadIsInFlightDoesNotStartASecondWrite() {
        val release = CountDownLatch(1)
        site.serveResponse("notifications/mark-read.json") {
            release.await(30, TimeUnit.SECONDS)
            okResponse()
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        presentNotifications()

        allReadItem().performClick()
        harness.await("the all-read write to reach the site") { markReadRequests() >= 1 }

        val cells = notificationCells()
        listView()!!.clickItem(cells[0], positionOf(cells[0]))
        harness.idle()
        release.countDown()
        harness.await("every row to lose its badge") { notificationCells().all { !it.getHasUnread() } }
        assertEquals("the all-read and the row tap are one write", 1, markReadRequests())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun okResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":"OK"}""")

    /** How many mark-read writes the site actually received. */
    private fun markReadRequests(): Int =
        site.recordedRequests.count { it == "PUT /notifications/mark-read.json" }

    /** Signs the reader in as a new account in place, so the screen's own observer reacts to it. */
    private fun switchAccount(username: String) {
        site.seedSharedJarCookie("_t=session-token; Path=/; HttpOnly")
        site.seedSharedJarCookie("_forum_session=forum-session; Path=/; HttpOnly")
        site.seedSharedJarCookie("cf_clearance=clearance; Path=/")
        val active = container ?: throw AssertionError("the screen has no container")
        kotlinx.coroutines.runBlocking {
            active.sessionCoordinator.completeLoginFromWebViewStaged(username, username)
        }
        harness.idle()
    }

    /** Asks the screen for its next page the way reaching the end of the list would. */
    private fun loadNextPage() {
        val fragment = harness.visibleFragment() as NotificationsFragment
        val method = NotificationsFragment::class.java.getDeclaredMethod("loadNextPageIfNeeded")
        method.isAccessible = true
        method.invoke(fragment)
        harness.idle()
    }

    private fun presentNotifications() {
        harness.present(NotificationsFragment())
        harness.await("the notifications to arrive") { rowCount() == 2 }
        harness.layoutApp()
    }

    /** The action bar's all-read item, found the way the reader finds it: by what it says. */
    private fun allReadItem(): View {
        val fragment = harness.visibleFragment() ?: throw AssertionError("no screen is visible")
        return views(fragment.fragmentView)
            .firstOrNull { it.contentDescription?.toString() == "全部已读" }
            ?: throw AssertionError("the notifications action bar has no all-read item")
    }

    private fun views(view: View?): List<View> = when (view) {
        null -> emptyList()
        is ViewGroup -> listOf(view) + (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> listOf(view)
    }

    /** Watches the stock Bulletin one main-looper task at a time, before it hides itself. */
    private fun awaitBulletin(expected: String): List<String> {
        repeat(5_000) {
            harness.idle(1)
            val bulletin = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
            if (bulletin != null) {
                val texts = bulkText(bulletin.getLayout())
                if (texts.any { it.contains(expected) }) return texts
            }
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
        }
        throw AssertionError("the refusal Bulletin never appeared")
    }

    private fun bulkText(view: View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { bulkText(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun notificationCells(): List<DialogCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<DialogCell>()
    }

    private fun positionOf(view: View): Int {
        val list = listView() ?: return -1
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return -1
        val index = (0 until list.childCount).firstOrNull { list.getChildAt(it) === view } ?: return -1
        return first + index
    }

    private fun drawnPostIds(): List<Long> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return chatCells(content).map { it.messageObject!!.messageOwner.id.toLong() }
    }

    private fun chatCells(view: View): List<ChatMessageCell> {
        if (view is ChatMessageCell && view.isShown) return listOf(view)
        if (view is ViewGroup) {
            return (0 until view.childCount).flatMap { chatCells(view.getChildAt(it)) }
        }
        return emptyList()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    /** Two notifications: a reply on a floor, and a badge the site gave no post. */
    private fun notificationsJson(readFirst: Boolean = false, totalRows: Int = 2): String = """
        {"notifications":[
        {"id":$NOTIFICATION_ID,"user_id":7,"notification_type":2,"read":$readFirst,"high_priority":false,"created_at":"2024-06-03T09:20:00.000Z","post_number":$FLOOR,"topic_id":$TOPIC_ID,"slug":"a-reply","fancy_title":"有回复的话题","data":{"topic_title":"有回复的话题","display_username":"searcher"},"acting_user_name":"Searcher","acting_user_avatar_template":"/user_avatar/linux.do/searcher/{size}/3.png"},
        {"id":$BADGE_ID,"user_id":7,"notification_type":12,"read":false,"high_priority":false,"created_at":"2024-06-01T21:05:00.000Z","topic_id":null,"slug":null,"fancy_title":null,"data":{"badge_name":"热心肠","username":"baba"}}
        ],"total_rows_notifications":$totalRows}
    """.trimIndent()

    /** The next page's single notification: one that arrived after the all-read write started. */
    private fun pageWithThirdNotification(): String = """
        {"notifications":[
        {"id":$NEWEST_ID,"user_id":7,"notification_type":12,"read":false,"high_priority":false,"created_at":"2024-06-05T09:20:00.000Z","topic_id":null,"slug":null,"fancy_title":null,"data":{"badge_name":"热心肠","username":"baba"}}
        ],"total_rows_notifications":3}
    """.trimIndent()

    /** The bootstrap page the badge is seeded from, with the site's own counts. */
    private fun bootstrapHtml(allUnread: Int): String =
        """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":$allUnread,\"unread_high_priority_notifications\":0,\"all_unread_notifications_count\":$allUnread}"}'></div></body></html>"""

    /** A topic answered as the site answers a request for one floor: the window, and the stream. */
    private fun topicAround(floor: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = JSONArray()
        val posts = JSONArray()
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        for (number in 1..TOTAL_FLOORS) {
            stream.put(POST_ID_BASE + number)
            if (number < floor || number > floor + 2) continue
            val post = JSONObject(template.toString())
            post.put("id", POST_ID_BASE + number)
            post.put("post_number", number)
            post.put("cooked", "<p>第 $number 楼</p>")
            posts.put(post)
        }
        base.getJSONObject("post_stream").put("stream", stream)
        base.getJSONObject("post_stream").put("posts", posts)
        base.put("posts_count", TOTAL_FLOORS)
        return base.toString()
    }

    private companion object {
        const val TOPIC_ID = 4004L
        const val FLOOR = 3
        const val NOTIFICATION_ID = 7001L
        const val BADGE_ID = 7002L
        const val NEWEST_ID = 7003L
        const val POST_ID_BASE = 1000L
        const val TOTAL_FLOORS = 12
    }
}
