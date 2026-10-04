package io.github.baba983.nativedo.messagebus

import io.github.baba983.nativedo.core.messagebus.MessageBusClient
import io.github.baba983.nativedo.core.messagebus.MessageBusMessage
import io.github.baba983.nativedo.core.messagebus.MessageBusPollOutcome
import io.github.baba983.nativedo.core.messagebus.MessageBusState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

/**
 * The wire contract of the message-bus poll.
 *
 * <p>Failure modes pinned here, each one a way a poller goes wrong against the gem's protocol:
 * a channel's id advancing from another channel's message or not being carried into the next
 * request; a `/__status` message being treated as informational instead of authoritative (the
 * client then asks for messages the bus never has and never receives the skipped ones); a chunked
 * body being parsed as one JSON document (nothing after the first frame arrives); a malformed
 * frame taking its neighbours down; a 5xx or a 429 answered with an immediate retry, which is a
 * request storm against a site that is failing or rate-limiting; and a stopped client that keeps
 * long-polling behind the app's back.</p>
 */
internal class MessageBusClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun lastIdsAdvancePerChannelAndAreResentOnTheNextPoll() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/new", 10L, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":7}}""") +
                    "," +
                    message("/latest", 5L, """{"topic_id":900,"message_type":"latest","payload":{"category_id":7}}""") +
                    "]",
            ),
        )
        server.enqueue(jsonResponse("[]"))
        val client = newClient()
        client.subscribe("/new")
        client.subscribe("/latest")

        val outcome = runBlocking { client.pollOnce() }
        assertEquals(MessageBusPollOutcome.Delivered, outcome)
        assertEquals("the id of the message on /new", 10L, client.lastIdOf("/new"))
        assertEquals("the id of the message on /latest", 5L, client.lastIdOf("/latest"))

        runBlocking { client.pollOnce() }

        val firstRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        val firstFields = firstRequest.formFields()
        assertEquals("a fresh channel subscribes to new messages only", "-1", firstFields["/new"])
        assertEquals("-1", firstFields["/latest"])
        assertEquals("the sequence number counts requests", "1", firstFields["__seq"])
        assertEquals("true", firstRequest.headers["Dont-Chunk"])
        assertEquals("true", firstRequest.headers["X-SILENCE-LOGGER"])

        val secondRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        val secondFields = secondRequest.formFields()
        assertEquals("each channel resends its own last id", "10", secondFields["/new"])
        assertEquals("5", secondFields["/latest"])
        assertEquals("2", secondFields["__seq"])
    }

    @Test
    fun statusMessageSetsAndRewindsChannelIds() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/new", 99L, """{"topic_id":1,"message_type":"new_topic","payload":{"category_id":1}}""") +
                    "]",
            ),
        )
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/__status", -1L, """{"/new":41,"/latest":7,"/unsubscribed":500}""") +
                    "]",
            ),
        )
        server.enqueue(jsonResponse("[]"))
        val client = newClient()
        client.subscribe("/new")
        client.subscribe("/latest")

        runBlocking { client.pollOnce() }
        assertEquals(99L, client.lastIdOf("/new"))

        runBlocking { client.pollOnce() }
        assertEquals("the server's position rewinds an id the client was ahead of", 41L, client.lastIdOf("/new"))
        assertEquals(7L, client.lastIdOf("/latest"))
        assertNull("a status entry for a channel nobody subscribed is ignored", client.lastIdOf("/unsubscribed"))

        runBlocking { client.pollOnce() }
        server.takeRequest(5, TimeUnit.SECONDS)
        server.takeRequest(5, TimeUnit.SECONDS)
        val thirdFields = server.takeRequest(5, TimeUnit.SECONDS)!!.formFields()
        assertEquals("the rewound id is what the next poll sends", "41", thirdFields["/new"])
        assertEquals("7", thirdFields["/latest"])
    }

    @Test
    fun chunkedFramesAreSplitAndIdsAdvance() {
        val body =
            "[" +
                message("/new", 11L, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":7}}""") +
                "]" +
                "\r\n|\r\n" +
                "[" +
                message("/latest", 12L, """{"topic_id":901,"message_type":"latest","payload":{"category_id":8}}""") +
                "]"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(body),
        )
        val client = newClient(chunked = true)
        val received = mutableListOf<MessageBusMessage>()
        client.subscribe("/new") { received += it }
        client.subscribe("/latest") { received += it }

        val outcome = runBlocking { client.pollOnce() }
        assertEquals(MessageBusPollOutcome.Delivered, outcome)
        assertEquals("both frames were parsed, in order", listOf(11L, 12L), received.map { it.messageId })
        assertEquals(12L, client.lastIdOf("/latest"))

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull("a chunked poll does not ask the server to stop chunking", request.headers["Dont-Chunk"])
    }

    @Test
    fun aMalformedFrameDoesNotAbortTheFramesAroundIt() {
        val body = "this is not json\r\n|\r\n" +
            "[" +
            message("/latest", 21L, """{"topic_id":1,"message_type":"latest","payload":{"category_id":1}}""") +
            "]\r\n|\r\n" +
            "[{\"global_id\":1,\"message_id\":22,\"channel\":\"/latest\""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(body),
        )
        val client = newClient(chunked = true)
        client.subscribe("/latest")

        val outcome = runBlocking { client.pollOnce() }
        assertEquals("the valid frame still counts as data", MessageBusPollOutcome.Delivered, outcome)
        assertEquals("the partial trailing frame neither crashes nor advances the id", 21L, client.lastIdOf("/latest"))
    }

    @Test
    fun aServerErrorBacksOffInsteadOfRefiringImmediately() {
        server.enqueue(MockResponse().setResponseCode(500))
        val client = newClient(pollIntervalMillis = 60_000L)
        client.subscribe("/latest")
        client.start()

        val state = runBlocking {
            withTimeout(5_000L) { client.state.first { it is MessageBusState.BackingOff } }
        } as MessageBusState.BackingOff
        assertEquals(1, state.consecutiveFailures)
        assertTrue("a failed poll waits at least one poll interval", state.delayMillis >= 60_000L)

        Thread.sleep(300)
        assertEquals("the poller did not refire after the error", 1, server.requestCount)
        client.stop()
    }

    @Test
    fun aRateLimitWaitsOutTheRetryAfterFloor() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        val client = newClient(pollIntervalMillis = 60_000L)
        client.subscribe("/latest")
        client.start()

        val state = runBlocking {
            withTimeout(5_000L) { client.state.first { it is MessageBusState.BackingOff } }
        } as MessageBusState.BackingOff
        assertEquals("Retry-After 3s is floored to the reference client's 15s", 15_000L, state.delayMillis)

        Thread.sleep(200)
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun consecutiveFailuresGrowTheWaitLinearlyAndCapIt() {
        val client = newClient(pollIntervalMillis = 3_000L)
        val failed = MessageBusPollOutcome.Failed(statusCode = 503, error = null)
        assertEquals(3_000L, client.nextDelayMillis(failed, elapsedMillis = 0L, consecutiveFailures = 1))
        assertEquals(6_000L, client.nextDelayMillis(failed, elapsedMillis = 0L, consecutiveFailures = 2))
        assertEquals(
            "the wait is capped, so an outage cannot grow it forever",
            180_000L,
            client.nextDelayMillis(failed, elapsedMillis = 0L, consecutiveFailures = 1_000),
        )
        assertEquals(
            "a delivered message retries almost immediately",
            100L,
            client.nextDelayMillis(MessageBusPollOutcome.Delivered, elapsedMillis = 0L, consecutiveFailures = 0),
        )
    }

    @Test
    fun stopStopsTheBackgroundPoller() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                jsonResponse("[]")
        }
        val client = newClient(pollIntervalMillis = 150L)
        client.subscribe("/latest")
        client.start()

        Thread.sleep(600L)
        client.stop()
        val pollsAtStop = server.requestCount
        Thread.sleep(600L)

        assertEquals("no polls happened after stop", pollsAtStop, server.requestCount)
        assertFalse(client.isPolling)
    }

    private fun newClient(
        chunked: Boolean = false,
        pollIntervalMillis: Long = 60_000L,
    ): MessageBusClient = MessageBusClient(
        httpClient = OkHttpClient(),
        baseUrl = server.url("/"),
        enableChunkedEncoding = chunked,
        pollIntervalMillis = pollIntervalMillis,
    )

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)

    private fun message(channel: String, messageId: Long, data: String): String =
        """{"global_id":$messageId,"message_id":$messageId,"channel":"$channel","data":$data}"""

    private fun RecordedRequest.formFields(): Map<String, String> =
        body.readUtf8()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { field ->
                val separator = field.indexOf('=')
                val name = java.net.URLDecoder.decode(field.substring(0, separator), "UTF-8")
                val value = java.net.URLDecoder.decode(field.substring(separator + 1), "UTF-8")
                name to value
            }
}
