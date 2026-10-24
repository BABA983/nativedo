package io.github.baba983.nativedo.core.presence

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.messagebus.MessageBusClient
import io.github.baba983.nativedo.core.model.ChatUser
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * The typing-presence controller against a fake site and a real message bus.
 *
 * <p>Failure modes pinned here, none of which a screen render can reach: announcing a reader
 * whose `hide_presence` is true, unknown or unanswered (the site's own composer treats only
 * `false` as permission); re-announcing a screen under a different account after the session
 * moved; applying a presence delta whose message id skipped, which would leave the visible list
 * permanently wrong; announcing into a channel whose `/presence/get` answered null; a stop that
 * races an in-flight present and leaves the reader visible; a failed update that retries in a
 * tight loop; and a burst of composer changes that sends the site a request per keystroke.
 *
 * <p>Everything below the public controller is real: a Retrofit [DiscourseApiClient] against a
 * MockWebServer serving the site's own wire shapes, and a real [MessageBusClient] polling that
 * same server. The assertions read the visible callback/`getUsers()` and the requests the site
 * received, so they fail if the controller announces the wrong thing or draws the wrong list.
 */
internal class TypingPresenceControllerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DiscourseApiClient
    private lateinit var scope: CoroutineScope
    private lateinit var clientFlow: MutableStateFlow<MessageBusClient?>

    private val controllers = CopyOnWriteArrayList<TypingPresenceController>()
    private val buses = CopyOnWriteArrayList<MessageBusClient>()
    private val seenUsers = CopyOnWriteArrayList<List<ChatUser>>()

    /** One-off policy bodies; an empty queue serves [defaultPolicyJson]. */
    private val policyBodies = ConcurrentLinkedQueue<String>()
    private val policyReads = AtomicInteger(0)

    @Volatile
    private var policyStatus = 200

    @Volatile
    private var defaultPolicyJson = policyJson("baba", 7, hidePresence = false)

    private val snapshotAnswers = ConcurrentLinkedQueue<Pair<Int, String>>()

    @Volatile
    private var defaultSnapshot = snapshot(lastMessageId = 1, users = emptyList())

    private val presenceGetQueries = CopyOnWriteArrayList<List<String>>()

    private val updates = CopyOnWriteArrayList<PresenceUpdate>()
    private val updateFailures = ConcurrentLinkedQueue<Int>()
    private val pollMessages = ConcurrentLinkedQueue<String>()

    @Volatile
    private var updateGate: CountDownLatch? = null

    @Volatile
    private var presentVerdict = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?').removePrefix("/")
                val method = request.method.orEmpty()
                return when {
                    path == "session/csrf" -> json("""{"csrf":"csrf-test"}""")
                    path == "session/current.json" -> {
                        policyReads.incrementAndGet()
                        val oneOff = policyBodies.poll()
                        if (oneOff != null) {
                            json(oneOff)
                        } else if (policyStatus == 200) {
                            json(defaultPolicyJson)
                        } else {
                            MockResponse().setResponseCode(policyStatus)
                        }
                    }
                    path == "presence/get" && method == "GET" -> {
                        presenceGetQueries.add(request.requestUrl?.queryParameterValues("channels[]").orEmpty().filterNotNull())
                        val answer = snapshotAnswers.poll() ?: (200 to defaultSnapshot)
                        if (answer.first == 200) json(answer.second) else MockResponse().setResponseCode(answer.first)
                    }
                    path == "presence/update" && method == "POST" -> handleUpdate(request)
                    path.startsWith("message-bus/") -> json(nextPollBody())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        api = DiscourseApiClient(
            service = Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DiscourseApiService::class.java),
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        clientFlow = MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        controllers.forEach { controller -> controller.stop() }
        controllers.clear()
        buses.forEach { bus -> bus.stop() }
        buses.clear()
        scope.cancel()
        server.shutdown()
    }

    // ------------------------------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------------------------------

    @Test
    fun theSnapshotShowsOtherReadersAndNeverTheReaderThemselves() {
        defaultSnapshot = snapshot(
            lastMessageId = 3,
            users = listOf(user(7, "baba"), user(8, "alice"), user(9, "bob")),
        )
        val controller = controller()

        controller.start(CHANNEL_NAME, "baba")

        await("the snapshot's other readers") {
            latestUsers().map { it.username } == listOf("alice", "bob")
        }
        assertEquals(listOf("alice", "bob"), controller.getUsers().map { it.username })
        assertFalse("reading is not announcing", controller.isPresent())
        assertEquals(
            "the read names the chat plugin's own presence channel",
            listOf(CHANNEL_NAME),
            presenceGetQueries.last(),
        )
        assertTrue("nothing was written for a read", updates.isEmpty())
    }

    @Test
    fun enteringAndLeavingDeltasFollowContiguousIds() {
        defaultSnapshot = snapshot(2, listOf(user(8, "alice")))
        attachBus()
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the snapshot reader") { latestUsers().map { it.username } == listOf("alice") }
        await("the message-bus subscription") { bus().lastIdOf(BUS_CHANNEL) != null }
        assertEquals(
            "the subscription starts from the snapshot's message-bus position",
            2L,
            bus().lastIdOf(BUS_CHANNEL),
        )

        publishDelta(3, """{"entering_users":[${userJson(9, "bob")}]}""")
        await("bob entering") { latestUsers().map { it.username } == listOf("alice", "bob") }

        publishDelta(4, """{"leaving_user_ids":[8]}""")
        await("alice leaving") { latestUsers().map { it.username } == listOf("bob") }

        publishDelta(5, """{"entering_users":[${userJson(9, "bob")}]}""")
        Thread.sleep(200)
        assertEquals("a duplicate enter is the same user, not two", listOf("bob"), latestUsers().map { it.username })
        assertEquals("one snapshot read is enough for contiguous deltas", 1, presenceGetQueries.size)
    }

    @Test
    fun aSkippedMessageIdResnapshotsInsteadOfGuessing() {
        snapshotAnswers.add(200 to snapshot(2, listOf(user(8, "alice"))))
        snapshotAnswers.add(200 to snapshot(4, listOf(user(8, "alice"), user(10, "carol"))))
        attachBus()
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("alice from the first snapshot") { latestUsers().map { it.username } == listOf("alice") }
        await("the message-bus subscription") { bus().lastIdOf(BUS_CHANNEL) != null }

        // Id 3 never arrived: the entering user it carried is unknowable, so the only honest
        // answer is the site's fresh state (which contains carol), not a guessed append.
        publishDelta(4, """{"entering_users":[${userJson(9, "bob")}]}""")

        await("the resnapshot") { latestUsers().map { it.username } == listOf("alice", "carol") }
        assertEquals("the gap caused one new read", 2, presenceGetQueries.size)
        await("the resubscription at the new position") { bus().lastIdOf(BUS_CHANNEL) == 4L }

        publishDelta(5, """{"leaving_user_ids":[8]}""")
        await("the delta after the resubscription") { latestUsers().map { it.username } == listOf("carol") }
    }

    @Test
    fun aSnapshotOutageIsRetriedSilently() {
        snapshotAnswers.add(500 to "")
        snapshotAnswers.add(200 to snapshot(2, listOf(user(8, "alice"))))
        val controller = controller(timing(snapshotRetry = 50))
        controller.start(CHANNEL_NAME, "baba")

        await("the retried snapshot") { latestUsers().map { it.username } == listOf("alice") }
        assertTrue("the outage surfaced only as a retry", presenceGetQueries.size >= 2)
    }

    @Test
    fun aBusThatArrivesAfterTheScreenStillSubscribesAndAppliesDeltas() {
        defaultSnapshot = snapshot(2, listOf(user(8, "alice")))
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the snapshot before any bus") { latestUsers().map { it.username } == listOf("alice") }
        assertNull("no subscription without a client", clientFlow.value)

        attachBus()
        await("the late subscription") { bus().lastIdOf(BUS_CHANNEL) != null }
        publishDelta(3, """{"entering_users":[${userJson(9, "bob")}]}""")

        await("the delta on the late bus") {
            latestUsers().map { it.username } == listOf("alice", "bob")
        }
    }

    @Test
    fun anInaccessibleChannelIsNeverAnnounced() {
        defaultSnapshot = """{"$CHANNEL_NAME":null}"""
        attachBus()
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")

        await("the authoritative null") { presenceGetQueries.isNotEmpty() }
        controller.textChanged(true)
        Thread.sleep(300)

        assertEquals("no users on an inaccessible channel", emptyList<ChatUser>(), controller.getUsers())
        assertFalse(controller.isPresent())
        assertTrue("the site was never asked to announce into a channel it hid", updates.isEmpty())
        assertNull("and nothing was subscribed for it", bus().lastIdOf(BUS_CHANNEL))
        assertTrue("the reader's own policy is still read for a later channel", policyReads.get() >= 1)
    }

    // ------------------------------------------------------------------------------------------
    // Announcing
    // ------------------------------------------------------------------------------------------

    @Test
    fun typingAnnouncesWithOneClientIdForPresentAndLeave() {
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        await("the present write") { updates.any { it.present.isNotEmpty() } }
        val present = updates.first { it.present.isNotEmpty() }
        assertEquals(listOf(CHANNEL_NAME), present.present)
        assertTrue("a present does not also name a leave", present.leave.isEmpty())
        assertEquals("the update carries the session's CSRF", "csrf-test", present.csrf)
        assertTrue(
            "the client id is the site's own 32 hex character shape",
            Regex("[0-9a-f]{32}").matches(present.clientId),
        )
        assertEquals(
            "presence writes channel names only, never composer text",
            setOf("client_id", "present_channels[]"),
            present.fields,
        )
        await("the acknowledgement") { controller.isPresent() }

        controller.textChanged(false)
        await("the leave write") { updates.any { it.leave.isNotEmpty() } }
        val leave = updates.first { it.leave.isNotEmpty() }
        assertEquals(listOf(CHANNEL_NAME), leave.leave)
        assertEquals("the leave uses the same client id", present.clientId, leave.clientId)
        assertTrue(leave.present.isEmpty())
        await("the reader is no longer present") { !controller.isPresent() }
    }

    @Test
    fun aReaderWhoHidesPresenceStillSeesOthersButIsNeverAnnounced() {
        defaultPolicyJson = policyJson("baba", 7, hidePresence = true)
        defaultSnapshot = snapshot(2, listOf(user(8, "alice")))
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the other reader") { latestUsers().map { it.username } == listOf("alice") }

        controller.textChanged(true)
        Thread.sleep(400)

        assertTrue("hide_presence=true is never overridden by typing", updates.isEmpty())
        assertFalse(controller.isPresent())
        assertEquals("reading others is still allowed", listOf("alice"), controller.getUsers().map { it.username })
    }

    @Test
    fun aPresenceOptionTheSiteDidNotAnswerIsTreatedAsHidden() {
        defaultPolicyJson = """{"current_user":{"id":7,"username":"baba","user_option":{}}}"""
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the option read") { policyReads.get() >= 1 && presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        Thread.sleep(400)

        assertTrue("an absent hide_presence is an unanswered option, not permission", updates.isEmpty())
        assertFalse(controller.isPresent())
    }

    @Test
    fun aPolicyThatCannotBeReadNeverBecomesPermission() {
        policyStatus = 500
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the failed option read") { policyReads.get() >= 1 && presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        Thread.sleep(400)

        assertTrue("a failed privacy read is privacy-safe", updates.isEmpty())
        assertFalse(controller.isPresent())
    }

    @Test
    fun aRefusedPresentIsNotRetriedInALoop() {
        presentVerdict = false
        val controller = controller()
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        await("the refused present") { updates.any { it.present.isNotEmpty() } }
        Thread.sleep(400)

        assertEquals("the site's false is authoritative", 1, updates.count { it.present.isNotEmpty() })
        assertFalse(controller.isPresent())

        controller.textChanged(true)
        Thread.sleep(250)
        assertEquals(
            "a refused channel stays refused until a new start/resume",
            1,
            updates.count { it.present.isNotEmpty() },
        )
    }

    @Test
    fun aLoggedOutPresentIsNotReplayed() {
        updateFailures.add(401)
        val controller = controller(timing(refresh = 100, idle = 2_000))
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }
        controller.textChanged(true)
        await("the refused write") { updates.isNotEmpty() }
        Thread.sleep(400)
        assertEquals("logout must not restart the writer", 1, updates.size)
        assertFalse(controller.isPresent())
    }

    @Test
    fun aTransientFailureIsRetriedWithTheSameState() {
        updateFailures.add(500)
        val controller = controller(timing(retryBase = 60, retryMax = 200))
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        await("the retry to be accepted") { controller.isPresent() }
        assertEquals("one failed write, then one accepted", 2, updates.count { it.present.isNotEmpty() })
    }

    @Test
    fun theRefreshKeepsPresenceAliveAndTheTypingWindowLeaves() {
        val controller = controller(timing(refresh = 150, idle = 900))
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        await("a refreshed present") { updates.count { it.present.isNotEmpty() } >= 2 }

        // No further input: the typing window (scaled here) retracts the presence while the draft
        // itself is untouched.
        await("the idle leave") { updates.any { it.leave.isNotEmpty() } && !controller.isPresent() }
        assertFalse(controller.isPresent())

        controller.textChanged(true)
        await("typing again re-enters") { updates.count { it.present.isNotEmpty() } >= 3 }
    }

    @Test
    fun aBurstOfComposerChangesCoalescesToTheFinalState() {
        val controller = controller(timing(refresh = 3_000, throttle = 300, idle = 2_000))
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }

        controller.textChanged(true)
        await("the first present") { updates.any { it.present.isNotEmpty() } }

        controller.textChanged(false)
        controller.textChanged(true)
        controller.textChanged(false)
        controller.textChanged(true)
        controller.textChanged(false)

        await("the coerced leave") { updates.any { it.leave.isNotEmpty() } }
        Thread.sleep(350)
        assertEquals(
            "one present and one leave for a burst, not a request per keystroke",
            2,
            updates.size,
        )
        assertTrue("the final state is not present", updates.last().leave.isNotEmpty())
        assertFalse(controller.isPresent())
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle and session boundaries
    // ------------------------------------------------------------------------------------------

    @Test
    fun pauseLeavesAndResumeReattachesWithoutReannouncing() {
        val controller = controller(timing(refresh = 3_000, idle = 2_000))
        attachBus()
        controller.start(CHANNEL_NAME, "baba")
        await("the initial read") { presenceGetQueries.isNotEmpty() }
        await("the subscription") { bus().lastIdOf(BUS_CHANNEL) != null }

        controller.textChanged(true)
        await("the present") { controller.isPresent() }

        controller.pause()
        await("the leave on pause") { updates.any { it.leave.isNotEmpty() } && !controller.isPresent() }
        assertFalse(controller.isPresent())

        controller.textChanged(true)
        Thread.sleep(300)
        assertEquals(
            "typing behind a paused screen does not announce",
            1,
            updates.count { it.present.isNotEmpty() },
        )

        val readsBefore = presenceGetQueries.size
        controller.resume()
        await("the resumed read") { presenceGetQueries.size > readsBefore }
        Thread.sleep(200)
        assertEquals("resume does not announce by itself", 1, updates.count { it.present.isNotEmpty() })

        controller.textChanged(true)
        await("typing after resume announces") { updates.count { it.present.isNotEmpty() } == 2 }
    }

    @Test
    fun aStopDuringAnInFlightPresentStillLeavesAndUnsubscribes() {
        val gate = CountDownLatch(1)
        updateGate = gate
        attachBus()
        val controller = controller(timing(refresh = 3_000, idle = 2_000))
        controller.start(CHANNEL_NAME, "baba")
        await("the read") { presenceGetQueries.isNotEmpty() }
        await("the subscription") { bus().lastIdOf(BUS_CHANNEL) != null }

        controller.textChanged(true)
        await("the in-flight present") { updates.any { it.present.isNotEmpty() } }
        val present = updates.first { it.present.isNotEmpty() }

        controller.stop()
        assertEquals("the visible list is cleared at once", emptyList<ChatUser>(), controller.getUsers())
        assertTrue("the callback is told", seenUsers.last().isEmpty())

        gate.countDown()
        await("the leave after the in-flight present") { updates.any { it.leave.isNotEmpty() } }
        val leave = updates.first { it.leave.isNotEmpty() }
        assertEquals("the leave uses the stopped binding's id", present.clientId, leave.clientId)
        Thread.sleep(300)
        assertEquals("no present is replayed after the stop", 1, updates.count { it.present.isNotEmpty() })
        assertNull("the subscription is gone", bus().lastIdOf(BUS_CHANNEL))
        assertTrue("and nothing remains visible", controller.getUsers().isEmpty())
    }

    @Test
    fun aNewSessionMustProveTheSameReaderBeforeAnythingIsAnnounced() {
        attachBus()
        val controller = controller(timing(refresh = 3_000, idle = 2_000))
        controller.start(CHANNEL_NAME, "baba")
        await("the subscription") { bus().lastIdOf(BUS_CHANNEL) != null }
        controller.textChanged(true)
        await("the first reader's present") { controller.isPresent() }
        val writesBefore = updates.size

        // A new bus client is a new signed-in session. The same screen is still bound to "baba",
        // but the account behind it is now someone else.
        defaultPolicyJson = policyJson("someone-else", 99, hidePresence = false)
        attachBus()

        await("the second subscription") {
            buses.size == 2 && buses[1].lastIdOf(BUS_CHANNEL) != null
        }
        controller.textChanged(true)
        Thread.sleep(500)

        assertEquals(
            "a new account never inherits the old screen's announcement",
            writesBefore,
            updates.size,
        )
        assertFalse(controller.isPresent())
    }

    // ------------------------------------------------------------------------------------------
    // The fake site
    // ------------------------------------------------------------------------------------------

    private fun controller(policy: TypingPresencePolicy = timing()): TypingPresenceController {
        val created = TypingPresenceController(
            client = api,
            runningClient = clientFlow,
            policy = policy,
            scope = scope,
            callbacks = Dispatchers.Unconfined,
        )
        created.setUsersListener { users -> seenUsers.add(users) }
        controllers.add(created)
        return created
    }

    private fun attachBus(): MessageBusClient {
        val created = MessageBusClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/"),
            scope = scope,
            pollIntervalMillis = 50L,
            maxPollIntervalMillis = 150L,
            minPollIntervalMillis = 10L,
            rateLimitedFloorMillis = 100L,
            readTimeoutMillis = 5_000L,
        )
        buses.add(created)
        clientFlow.value = created
        created.start()
        return created
    }

    private fun bus(): MessageBusClient = buses.last()

    private fun latestUsers(): List<ChatUser> = seenUsers.lastOrNull().orEmpty()

    private fun handleUpdate(request: RecordedRequest): MockResponse {
        val fields = formFields(request.body.readUtf8())
        val clientId = fields["client_id"].orEmpty().single()
        val present = fields["present_channels[]"].orEmpty()
        val leave = fields["leave_channels[]"].orEmpty()
        updates.add(
            PresenceUpdate(
                clientId = clientId,
                present = present,
                leave = leave,
                csrf = request.getHeader("X-CSRF-Token"),
                fields = fields.keys,
            ),
        )
        if (present.isNotEmpty()) {
            val gate = updateGate
            if (gate != null) {
                updateGate = null
                gate.await(5, TimeUnit.SECONDS)
            }
        }
        updateFailures.poll()?.let { status -> return MockResponse().setResponseCode(status) }
        // The controller's `leave` answer is `{}` - the controller does not need a leave verdict.
        val answer = if (present.isEmpty()) {
            "{}"
        } else {
            present.joinToString(",", "{", "}") { channel -> "\"$channel\":$presentVerdict" }
        }
        return json(answer)
    }

    private fun nextPollBody(): String {
        val messages = mutableListOf<String>()
        while (true) {
            val message = pollMessages.poll() ?: break
            messages += message
        }
        return if (messages.isEmpty()) "[]" else messages.joinToString(",", "[", "]")
    }

    private fun publishDelta(messageId: Long, data: String) {
        pollMessages.add(
            """{"channel":"$BUS_CHANNEL","message_id":$messageId,"global_id":$messageId,"data":$data}""",
        )
    }

    private fun json(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun formFields(body: String): Map<String, List<String>> =
        body.split('&')
            .filter { it.isNotEmpty() }
            .map { pair ->
                val separator = pair.indexOf('=')
                val name = if (separator < 0) pair else pair.substring(0, separator)
                val value = if (separator < 0) "" else pair.substring(separator + 1)
                URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
            .groupBy({ it.first }, { it.second })

    private fun await(message: String, timeoutMillis: Long = 4_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10L)
        }
        assertTrue("timed out waiting for $message", condition())
    }

    private fun policyJson(username: String, userId: Long, hidePresence: Boolean): String =
        "{\"current_user\":{\"id\":$userId,\"username\":\"$username\",\"name\":\"Reader\"," +
            "\"avatar_template\":\"/u/$username/{size}.png\"," +
            "\"user_option\":{\"hide_presence\":$hidePresence}}}"

    private fun snapshot(lastMessageId: Long, users: List<ChatUser>): String {
        val list = users.joinToString(",", "[", "]") { user ->
            "{\"id\":${user.id},\"username\":\"${user.username}\",\"name\":\"${user.name}\"," +
                "\"avatar_template\":\"${user.avatarTemplate}\"}"
        }
        return "{\"$CHANNEL_NAME\":{\"count\":${users.size},\"last_message_id\":$lastMessageId,\"users\":$list}}"
    }

    private fun user(id: Long, username: String): ChatUser = ChatUser(
        id = id,
        username = username,
        name = username,
        avatarTemplate = "/u/$username/{size}.png",
    )

    private fun userJson(id: Long, username: String): String =
        "{\"id\":$id,\"username\":\"$username\",\"name\":\"$username\"," +
            "\"avatar_template\":\"/u/$username/{size}.png\"}"

    private fun timing(
        refresh: Long = 2_000L,
        throttle: Long = 40L,
        idle: Long = 1_000L,
        retryBase: Long = 60L,
        retryMax: Long = 250L,
        snapshotRetry: Long = 60L,
    ): TypingPresencePolicy = TypingPresencePolicy(
        refreshIntervalMillis = refresh,
        throttleMillis = throttle,
        typingIdleMillis = idle,
        retryBaseMillis = retryBase,
        retryMaxMillis = retryMax,
        snapshotRetryMillis = snapshotRetry,
    )

    private data class PresenceUpdate(
        val clientId: String,
        val present: List<String>,
        val leave: List<String>,
        val csrf: String?,
        val fields: Set<String>,
    )

    private companion object {
        const val CHANNEL = 71L
        const val CHANNEL_NAME = "/chat-reply/$CHANNEL"
        const val BUS_CHANNEL = "/presence$CHANNEL_NAME"
    }
}
