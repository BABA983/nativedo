package io.github.baba983.nativedo.messagebus

import io.github.baba983.nativedo.core.messagebus.MessageBusBootstrap
import io.github.baba983.nativedo.core.messagebus.NotificationUnreadCounts
import io.github.baba983.nativedo.core.network.NetworkJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the positions a Discourse page ships in `#data-preloaded`.
 *
 * <p>The failure this pins: the server stores each preloaded value as a JSON *string* containing
 * the object (`"currentUser": "{\"id\":12, ...}"`), not as an inline object, so a parser that only
 * looks for nested objects silently gets no channel ids and no notification position - and the app
 * then starts every channel from scratch while the web client would not. Both shapes must land in
 * the same place; a page without preload data must yield the no-lookbehind default rather than
 * fail.</p>
 */
internal class MessageBusBootstrapTest {

    @Test
    fun stringWrappedPreloadedValuesParse() {
        val preloaded = NetworkJson.instance.parseToJsonElement(
            """
            {
              "topicTrackingStateMeta": "{\"/latest\":41,\"/new\":7,\"/unread/12\":9}",
              "currentUser": "{\"id\":12,\"notification_channel_position\":55}"
            }
            """.trimIndent(),
        ).jsonObject

        val bootstrap = MessageBusBootstrap.fromPreloadedJson(preloaded)

        assertEquals(mapOf("/latest" to 41L, "/new" to 7L, "/unread/12" to 9L), bootstrap.channelLastIds)
        assertEquals(12L, bootstrap.currentUserId)
        assertEquals(55L, bootstrap.notificationChannelPosition)
        assertEquals("/notification/12", bootstrap.notificationChannel)
        assertEquals("a channel the site did not name starts from new messages only", -1L, bootstrap.lastIdFor("/destroy"))
    }

    @Test
    fun inlineObjectPreloadedValuesParseToo() {
        val preloaded = NetworkJson.instance.parseToJsonElement(
            """{"topicTrackingStateMeta":{"/latest":41,"/new":7},"currentUser":{"id":12,"notification_channel_position":55}}""",
        ).jsonObject

        val bootstrap = MessageBusBootstrap.fromPreloadedJson(preloaded)

        assertEquals(mapOf("/latest" to 41L, "/new" to 7L), bootstrap.channelLastIds)
        assertEquals(12L, bootstrap.currentUserId)
        assertEquals(55L, bootstrap.notificationChannelPosition)
    }

    @Test
    fun parseReadsThePreloadedDivAndTheSharedSessionKeyMeta() {
        val preloadedJson =
            """{"topicTrackingStateMeta":"{\"/latest\":41}","currentUser":"{\"id\":12,\"notification_channel_position\":55}"}"""
        val html = "<html><head><meta name=\"shared_session_key\" content=\"abc123\"></head><body>" +
            "<div id=\"data-preloaded\" data-preloaded='" + preloadedJson + "'></div></body></html>"

        val bootstrap = MessageBusBootstrap.parse(html)

        assertEquals(mapOf("/latest" to 41L), bootstrap.channelLastIds)
        assertEquals(12L, bootstrap.currentUserId)
        assertEquals(55L, bootstrap.notificationChannelPosition)
        assertEquals("abc123", bootstrap.sharedSessionKey)
    }

    /**
     * The shape linux.do actually renders: `script#data-preloaded` whose *text* is the JSON object
     * (with values string-wrapped). A parser that only reads the div attribute silently returns
     * the defaults here - no ids, no user, no counts - which is exactly the failure the first
     * device pass hit.
     */
    @Test
    fun theScriptPreloadShapeThatLinuxDoRendersParses() {
        val preloadedJson =
            """{"topicTrackingStateMeta":"{\"/latest\":4106,\"/new\":4107}","currentUser":"{\"id\":7,\"notification_channel_position\":4108,\"unread_notifications\":4,\"unread_high_priority_notifications\":2,\"all_unread_notifications_count\":5}"}"""
        val html = "<html><head><meta name=\"shared_session_key\" content=\"abc123\"></head><body>" +
            "<script id=\"data-preloaded\" type=\"application/json\">$preloadedJson</script></body></html>"

        val bootstrap = MessageBusBootstrap.parse(html)

        assertEquals(mapOf("/latest" to 4106L, "/new" to 4107L), bootstrap.channelLastIds)
        assertEquals(7L, bootstrap.currentUserId)
        assertEquals(4108L, bootstrap.notificationChannelPosition)
        assertEquals(NotificationUnreadCounts(unread = 4, highPriority = 2, allUnread = 5), bootstrap.notificationCounts)
        assertEquals("abc123", bootstrap.sharedSessionKey)
    }

    /** The message-bus process may be named by the page; `/` means the site itself. */
    @Test
    fun theLongPollingBaseUrlIsReadFromTheSiteOrSiteSettings() {
        val fromSiteSettings = NetworkJson.instance.parseToJsonElement(
            """{"siteSettings":"{\"long_polling_base_url\":\"https://mb.linux.do\"}"}""",
        ).jsonObject
        assertEquals(
            "https://mb.linux.do",
            MessageBusBootstrap.fromPreloadedJson(fromSiteSettings).longPollingBaseUrl,
        )

        val default = NetworkJson.instance.parseToJsonElement(
            """{"siteSettings":"{\"long_polling_base_url\":\"/\"}"}""",
        ).jsonObject
        assertNull("the default is the site's own origin", MessageBusBootstrap.fromPreloadedJson(default).longPollingBaseUrl)
    }

    @Test
    fun aPageWithoutPreloadDataYieldsDefaults() {
        val bootstrap = MessageBusBootstrap.parse("<html><body><p>no preload</p></body></html>")

        assertTrue(bootstrap.channelLastIds.isEmpty())
        assertNull(bootstrap.currentUserId)
        assertNull(bootstrap.notificationChannel)
        assertEquals(-1L, bootstrap.lastIdFor("/latest"))
    }
}
