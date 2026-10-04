package io.github.baba983.nativedo.messagebus

import io.github.baba983.nativedo.core.messagebus.MessageBusClient
import io.github.baba983.nativedo.core.messagebus.NotificationCountStateHolder
import io.github.baba983.nativedo.core.messagebus.NotificationUnreadCounts
import io.github.baba983.nativedo.core.messagebus.TopicTrackingStateHolder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screen-facing state the message bus feeds.
 *
 * <p>Failure modes pinned here: the same topic arriving twice (a replay or a reconnect) showing
 * "2 new topics" for one topic; messages that only change a topic's state (`muted`, `read`) growing
 * the "new topics" pill; `clear()` not resetting the count, so a refreshed list still claims
 * unseen topics; and a notification payload that omits a count zeroing the badge, which looks
 * exactly like "everything read".</p>
 */
internal class MessageBusStateHoldersTest {

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
    fun aDuplicateNewTopicIsCountedOnce() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/new", 1L, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":7}}""") +
                    "," +
                    message("/new", 2L, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":7}}""") +
                    "]",
            ),
        )
        val client = newClient()
        val holder = TopicTrackingStateHolder(client)

        runBlocking { client.pollOnce() }

        assertEquals("one topic id is one incoming topic", 1, holder.incoming.value.count)
        assertEquals(1, holder.incoming.value.countForCategory(7))
        holder.close()
    }

    @Test
    fun clearStartsCountingFromZero() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/new", 1L, """{"topic_id":900,"message_type":"new_topic","payload":{"category_id":7}}""") +
                    "," +
                    message("/latest", 2L, """{"topic_id":901,"message_type":"latest","payload":{"category_id":8}}""") +
                    "]",
            ),
        )
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/new", 3L, """{"topic_id":902,"message_type":"new_topic","payload":{"category_id":8}}""") +
                    "]",
            ),
        )
        val client = newClient()
        val holder = TopicTrackingStateHolder(client)

        runBlocking { client.pollOnce() }
        assertEquals(2, holder.incoming.value.count)
        assertEquals(1, holder.incoming.value.countForCategory(7))
        assertEquals(listOf(901L), holder.incoming.value.topicIdsForCategory(8))

        holder.clear()
        assertEquals("a refreshed list has no incoming topics", 0, holder.incoming.value.count)
        assertEquals(0, holder.incoming.value.countForCategory(8))
        assertTrue(holder.incoming.value.isEmpty)

        runBlocking { client.pollOnce() }
        assertEquals("counting restarts after the refresh", 1, holder.incoming.value.count)
        assertEquals(0, holder.incoming.value.countForCategory(7))
        assertEquals(1, holder.incoming.value.countForCategory(8))
        holder.close()
    }

    @Test
    fun onlyNewTopicAndLatestMessagesGrowTheCount() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/latest", 1L, """{"topic_id":900,"message_type":"muted"}""") +
                    "," +
                    message("/latest", 2L, """{"topic_id":901,"message_type":"unmuted"}""") +
                    "," +
                    message("/new", 3L, """{"topic_id":902,"message_type":"new_topic","payload":{"category_id":7}}""") +
                    "]",
            ),
        )
        val client = newClient()
        val holder = TopicTrackingStateHolder(client)

        runBlocking { client.pollOnce() }

        assertEquals("state-change messages are not incoming topics", 1, holder.incoming.value.count)
        assertEquals(listOf(902L), holder.incoming.value.byTopicId.keys.toList())
        holder.close()
    }

    @Test
    fun theNotificationBadgeFollowsTheChannelAndMissingFieldsKeepTheirValue() {
        server.enqueue(
            jsonResponse(
                "[" +
                    message(
                        "/notification/7",
                        5L,
                        """{"unread_notifications":4,"unread_high_priority_notifications":2,"all_unread_notifications_count":6,"recent":[[1,true]]}""",
                    ) +
                    "]",
            ),
        )
        server.enqueue(
            jsonResponse(
                "[" +
                    message("/notification/7", 6L, """{"unread_high_priority_notifications":0}""") +
                    "]",
            ),
        )
        val client = newClient()
        val holder = NotificationCountStateHolder(
            client,
            userId = 7L,
            initialCounts = NotificationUnreadCounts(unread = 9, highPriority = 9, allUnread = 9),
            initialLastId = 55L,
        )

        val firstRequest = runBlocking {
            client.pollOnce()
            server.takeRequest(5, TimeUnit.SECONDS)!!
        }
        assertEquals("the subscription starts at the site's own channel position", "55", firstRequest.formFields()["/notification/7"])
        assertEquals(NotificationUnreadCounts(unread = 4, highPriority = 2, allUnread = 6), holder.counts.value)

        runBlocking {
            client.pollOnce()
            server.takeRequest(5, TimeUnit.SECONDS)
        }
        assertEquals(
            "a payload without a count leaves that count alone",
            NotificationUnreadCounts(unread = 4, highPriority = 0, allUnread = 6),
            holder.counts.value,
        )
        holder.close()
    }

    private fun newClient(): MessageBusClient = MessageBusClient(
        httpClient = OkHttpClient(),
        baseUrl = server.url("/"),
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
