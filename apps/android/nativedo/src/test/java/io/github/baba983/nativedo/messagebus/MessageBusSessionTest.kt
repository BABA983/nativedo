package io.github.baba983.nativedo.messagebus

import io.github.baba983.nativedo.core.messagebus.MessageBusClient
import io.github.baba983.nativedo.core.messagebus.MessageBusSession
import io.github.baba983.nativedo.core.messagebus.NotificationUnreadCounts
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app-owned message-bus session: one client per signed-in reader, started when the app is in
 * front and stopped when the session ends.
 *
 * <p>Failure modes pinned here, none of which the screen tests can reach: a sign-out that leaves
 * the poller running (and keeps hitting the site without a session); repeated "app came to front"
 * calls or a rebuilt screen that build a second client; a new reader reusing the old reader's
 * client, channel positions and badge; and a bootstrap fetch that fails once wedging the bus
 * forever - or, worse, being treated as a login problem.</p>
 */
internal class MessageBusSessionTest {

    private lateinit var server: MockWebServer

    /** One answer per poll request; exhausted means an empty batch. */
    private val enqueued = ConcurrentLinkedQueue<String>()

    /**
     * Each test's session runs on its own dispatcher rather than the JVM-wide
     * {@code Dispatchers.Default}: these tests share a JVM with the Robolectric screen tests, and a
     * loaded machine must not turn "the coroutine was scheduled late" into a false failure of the
     * session's own logic.
     */
    private val dispatchers = mutableListOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                jsonResponse(enqueued.poll() ?: "[]")
        }
        server.start()
    }

    @After
    fun tearDown() {
        dispatchers.forEach { it.close() }
        dispatchers.clear()
        server.shutdown()
    }

    @Test
    fun thePollerStopsOnSignOutAndTheNextReaderGetsAFreshClient() {
        val state = MutableStateFlow<AppSessionState>(AppSessionState.Unknown)
        val session = newSession(state)

        session.onForeground()
        assertNull("a signed-out app has nothing polling", session.clientId)

        state.value = authenticated("baba")
        await("the session to start polling") { session.isPolling && session.clientId != null }
        val firstClient = session.clientId!!
        val firstPoll = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("the bus reaches the site", firstPoll)
        assertTrue(firstPoll!!.path!!.startsWith("/message-bus/"))

        // The app coming to the front again, the tab host being rebuilt, a theme recreation: none
        // of them is a second reader, so none of them is a second client.
        session.onForeground()
        session.onForeground()
        Thread.sleep(200)
        assertEquals("repeated foreground calls reuse the one client", firstClient, session.clientId)
        assertEquals("and do not add a second poll stream", 1, server.requestCount)

        state.value = AppSessionState.Anonymous
        await("the poller to stop on sign-out") {
            session.clientId == null && !session.isPolling &&
                session.notificationCounts.value == NotificationUnreadCounts() &&
                session.incomingTopics.value.isEmpty
        }
        assertEquals(NotificationUnreadCounts(), session.notificationCounts.value)
        assertTrue(session.incomingTopics.value.isEmpty)
        assertNull("a signed-out app sends no polls", server.takeRequest(300, TimeUnit.MILLISECONDS))

        state.value = authenticated("someone-else")
        await("the new reader's own client") { session.clientId != null && session.clientId != firstClient }
        assertNotNull("which starts polling", server.takeRequest(5, TimeUnit.SECONDS))
        session.close()
    }

    @Test
    fun theRunningClientIsObservableAndClearedWithTheSession() {
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = newSession(state)
        val seen = java.util.concurrent.CopyOnWriteArrayList<MessageBusClient?>()
        val collector = newScope().launch {
            session.runningClientFlow.collect { client -> seen.add(client) }
        }

        session.onForeground()
        await("the session's client to be observable") { seen.any { it != null } }
        val running = session.runningClient
        assertNotNull("a signed-in foreground session has a client", running)
        assertTrue(
            "the flow publishes the client that is actually polling",
            seen.contains(running),
        )

        state.value = AppSessionState.Anonymous
        await("the sign-out to clear the observed client") { seen.isNotEmpty() && seen.last() == null }
        assertNull("and the getter agrees", session.runningClient)
        collector.cancel()
        session.close()
    }

    @Test
    fun theBootstrapSeedsTheBadgeAndTheSubscriptionsStartAtTheSitesPositions() {
        val gate = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                gate.await(5, TimeUnit.SECONDS)
                return jsonResponse("[]")
            }
        }
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = newSession(state, html = BOOTSTRAP_HTML)

        session.onForeground()
        val firstPoll = server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("no poll")
        val fields = formFields(firstPoll)

        assertEquals("the site's own /latest position", "4106", fields["/latest"])
        assertEquals("the site's own /new position", "4107", fields["/new"])
        assertEquals("the user's notification channel position", "4108", fields["/notification/7"])
        assertEquals("the sequence starts at one", "1", fields["__seq"])
        assertEquals(
            "the badge is seeded from the page before any message arrives",
            NotificationUnreadCounts(unread = 4, highPriority = 2, allUnread = 5),
            session.notificationCounts.value,
        )

        gate.countDown()
        session.onBackground()
        await("the poller to stop") { !session.isPolling }
        assertEquals("backgrounding keeps the badge", 5, session.notificationCounts.value.allUnread)
        session.close()
    }

    @Test
    fun aChannelMessageMovesTheBadgeAndCountsAnIncomingTopic() {
        enqueued += arrayOf(
            message("/new", 4109, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":4}}"""),
            message("/notification/7", 4110, """{"unread_notifications":2,"unread_high_priority_notifications":1,"all_unread_notifications_count":3}"""),
        ).joinToString(",", prefix = "[", postfix = "]")
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = newSession(state, html = BOOTSTRAP_HTML)

        session.onForeground()
        await("the message to move the badge") {
            session.notificationCounts.value == NotificationUnreadCounts(2, 1, 3)
        }
        // The bus keeps polling and now gets empty answers: a topic is still one incoming topic,
        // never a growing count.
        Thread.sleep(300)
        assertEquals(1, session.incomingTopics.value.count)
        assertEquals(1, session.incomingTopics.value.countForCategory(4))
        assertEquals(0, session.incomingTopics.value.countForCategory(2))

        session.clearIncomingTopics()
        Thread.sleep(200)
        assertEquals("a refresh-cleared count stays cleared", 0, session.incomingTopics.value.count)
        session.close()
    }

    @Test
    fun aBootstrapThatCannotBeReadDoesNotWedgeTheBusOrChangeTheSession() {
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = {
                if (attempts.incrementAndGet() == 1) throw IOException("the site answered a challenge page")
                BOOTSTRAP_HTML
            },
            scope = newScope(),
        )

        session.onForeground()
        await("the first fetch to fail") { attempts.get() == 1 }
        Thread.sleep(100)
        assertNull("a failed bootstrap does not start a poller", session.clientId)
        assertNull("and sends nothing", server.takeRequest(200, TimeUnit.MILLISECONDS))
        assertTrue("and never touches the session", state.value is AppSessionState.Authenticated)

        // The next time the app comes to the front the fetch is tried again and the bus starts.
        session.onForeground()
        await("the retry to start the bus") { session.clientId != null }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        session.close()
    }

    @Test
    fun repeatedForegroundCallsDuringAStartBuildOneClient() {
        // The bootstrap fetch is held open, so the second call arrives while the start is in
        // flight - the race that used to build two clients (one of them polling forever, never
        // reachable or stoppable through the session).
        val gate = CountDownLatch(1)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = {
                gate.await(5, TimeUnit.SECONDS)
                BOOTSTRAP_HTML
            },
            scope = newScope(),
        )

        session.onForeground()
        session.onForeground()
        gate.countDown()
        await("the one client to start") { session.clientId != null }
        val clientId = session.clientId
        await("its first poll") { server.requestCount >= 1 }
        Thread.sleep(300)
        assertEquals("a second start call is not a second client", clientId, session.clientId)
        assertEquals("and not a second poll stream", 1, server.requestCount)
        session.close()
    }

    // ------------------------------------------------------------------------------------------

    /**
     * The badge is the site's count, so a mark-read re-reads the page the badge is seeded from.
     * The failure this pins: a write that changes the count leaving the badge where it was until a
     * channel message happens to arrive, which is not guaranteed and can be minutes late.
     */
    @Test
    fun aServerReadbackMovesTheBadgeWithoutALiveMessage() {
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = { if (calls.getAndIncrement() == 0) BOOTSTRAP_HTML else BOOTSTRAP_HTML_EMPTY },
            scope = newScope(),
        )

        session.onForeground()
        await("the badge seeded by the page") {
            session.notificationCounts.value == NotificationUnreadCounts(4, 2, 5)
        }

        session.refreshNotificationCounts()
        await("the badge to follow the re-read page") {
            session.notificationCounts.value == NotificationUnreadCounts(0, 0, 0)
        }
        session.close()
    }

    /**
     * A readback that lands after the reader signed out. The failure this pins: a page rendered for
     * the old reader repainting the cleared badge of a signed-out app (or the next reader's).
     */
    @Test
    fun aReadbackIsDroppedWhenTheSessionEnded() {
        val gate = CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = {
                if (calls.getAndIncrement() == 0) {
                    BOOTSTRAP_HTML
                } else {
                    gate.await(5, TimeUnit.SECONDS)
                    BOOTSTRAP_HTML_SEVEN
                }
            },
            scope = newScope(),
        )

        session.onForeground()
        await("the badge seeded by the page") { session.notificationCounts.value.allUnread == 5 }

        session.refreshNotificationCounts()
        await("the readback to be in flight") { calls.get() >= 2 }

        state.value = AppSessionState.Anonymous
        await("the sign-out to clear the badge") {
            session.clientId == null && session.notificationCounts.value == NotificationUnreadCounts()
        }
        gate.countDown()
        Thread.sleep(300)
        assertEquals(
            "a readback for a reader who signed out must not repaint the badge",
            NotificationUnreadCounts(),
            session.notificationCounts.value,
        )
        session.close()
    }

    /**
     * A channel message that arrives while the readback is out. The failure this pins: a slow page
     * read undoing a count the channel has already moved past, which would show the reader a stale
     * badge until the next message.
     */
    @Test
    fun aReadbackLosesToANewerLiveMessage() {
        val gate = CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = {
                if (calls.getAndIncrement() == 0) {
                    BOOTSTRAP_HTML
                } else {
                    gate.await(5, TimeUnit.SECONDS)
                    BOOTSTRAP_HTML_SEVEN
                }
            },
            scope = newScope(),
        )

        session.onForeground()
        await("the badge seeded by the page") { session.notificationCounts.value.allUnread == 5 }

        session.refreshNotificationCounts()
        await("the readback to be in flight") { calls.get() >= 2 }

        // A poll answer is one JSON array of messages; a bare message object is not a batch and
        // the client would skip it, so the live count would never arrive.
        enqueued += "[" + message(
            "/notification/7",
            9999,
            """{"unread_notifications":2,"unread_high_priority_notifications":1,"all_unread_notifications_count":3}""",
        ) + "]"
        await("the live message to move the badge") {
            session.notificationCounts.value == NotificationUnreadCounts(2, 1, 3)
        }

        gate.countDown()
        Thread.sleep(300)
        assertEquals(
            "a slow readback must not undo a newer live message",
            NotificationUnreadCounts(2, 1, 3),
            session.notificationCounts.value,
        )
        session.close()
    }

    /**
     * Two readbacks out at once. The failure this pins: the one that started first - whose page is
     * therefore the older - finishing first and bumping the revision the newer readback captured,
     * so the newer (the one that should win) is dropped and the stale page stands. The session's
     * latest-request-wins token has to settle it, not the order the fetches happen to finish in.
     */
    @Test
    fun theNewerOfTwoConcurrentReadbacksWins() {
        val older = CountDownLatch(1)
        val newer = CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val state = MutableStateFlow<AppSessionState>(authenticated("baba"))
        val session = MessageBusSession(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            sessionState = state,
            fetchBootstrapHtml = {
                when (calls.getAndIncrement()) {
                    0 -> BOOTSTRAP_HTML
                    1 -> {
                        older.await(5, TimeUnit.SECONDS)
                        BOOTSTRAP_HTML_ONE
                    }
                    else -> {
                        newer.await(5, TimeUnit.SECONDS)
                        BOOTSTRAP_HTML_NINE
                    }
                }
            },
            scope = newScope(),
        )

        session.onForeground()
        await("the badge seeded by the page") { session.notificationCounts.value.allUnread == 5 }

        session.refreshNotificationCounts()
        await("the older readback to be in flight") { calls.get() >= 2 }
        session.refreshNotificationCounts()
        await("the newer readback to be in flight") { calls.get() >= 3 }

        // The older request finishes first; its page must not stand in the newer request's way.
        older.countDown()
        Thread.sleep(200)
        newer.countDown()
        await("the newer readback's counts to win") {
            session.notificationCounts.value == NotificationUnreadCounts(9, 9, 9)
        }
        Thread.sleep(200)
        assertEquals(
            "the newest readback wins, not the one that happened to finish first",
            NotificationUnreadCounts(9, 9, 9),
            session.notificationCounts.value,
        )
        session.close()
    }

    // ------------------------------------------------------------------------------------------

    private fun newScope(): CoroutineScope {
        val dispatcher = java.util.concurrent.Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        dispatchers += dispatcher
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    private fun newSession(
        state: MutableStateFlow<AppSessionState>,
        html: String = BOOTSTRAP_HTML,
    ): MessageBusSession = MessageBusSession(
        httpClient = OkHttpClient(),
        baseUrl = server.url("/"),
        sessionState = state,
        fetchBootstrapHtml = { html },
        scope = newScope(),
    )

    private fun authenticated(username: String): AppSessionState.Authenticated =
        AppSessionState.Authenticated(
            snapshot = SessionSnapshot(
                username = username,
                displayName = username,
                avatarTemplate = "",
                lastValidatedAt = 0L,
            ),
            freshness = SessionFreshness.Verified,
        )

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun message(channel: String, messageId: Long, data: String): String =
        """{"global_id":$messageId,"message_id":$messageId,"channel":"$channel","data":$data}"""

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun formFields(request: RecordedRequest): Map<String, String> =
        request.body.readUtf8()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { field ->
                val separator = field.indexOf('=')
                java.net.URLDecoder.decode(field.substring(0, separator), "UTF-8") to
                    java.net.URLDecoder.decode(field.substring(separator + 1), "UTF-8")
            }

    private companion object {
        /** The shape linux.do ships: string-wrapped preloaded objects, ids and counts named. */
        const val BOOTSTRAP_HTML: String =
            """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":4,\"unread_high_priority_notifications\":2,\"all_unread_notifications_count\":5}"}'></div></body></html>"""

        /** The same page after a mark-read cleared the inbox. */
        const val BOOTSTRAP_HTML_EMPTY: String =
            """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":0,\"unread_high_priority_notifications\":0,\"all_unread_notifications_count\":0}"}'></div></body></html>"""

        /** A page carrying counts that differ from both the seed and the live message. */
        const val BOOTSTRAP_HTML_SEVEN: String =
            """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":7,\"unread_high_priority_notifications\":7,\"all_unread_notifications_count\":7}"}'></div></body></html>"""

        /** The older of two concurrent readbacks' pages. */
        const val BOOTSTRAP_HTML_ONE: String =
            """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":1,\"unread_high_priority_notifications\":1,\"all_unread_notifications_count\":1}"}'></div></body></html>"""

        /** The newer of two concurrent readbacks' pages; its counts must be the ones that stand. */
        const val BOOTSTRAP_HTML_NINE: String =
            """<html><body><div id="data-preloaded" data-preloaded='{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"username\":\"baba\",\"notification_channel_position\":4108,\"unread_notifications\":9,\"unread_high_priority_notifications\":9,\"all_unread_notifications_count\":9}"}'></div></body></html>"""
    }
}
