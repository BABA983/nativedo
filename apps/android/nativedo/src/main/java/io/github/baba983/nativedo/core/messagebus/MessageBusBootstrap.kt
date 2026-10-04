package io.github.baba983.nativedo.core.messagebus

import io.github.baba983.nativedo.core.network.NetworkJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

/**
 * The message-bus positions a rendered Discourse page carries, so a client can subscribe where the
 * site's own web client would instead of starting from scratch.
 *
 * `#data-preloaded` holds `topicTrackingStateMeta` (the site's `MessageBus.last_ids` for `/latest`,
 * `/new`, `/unread`, `/unread/{userId}`, `/recover`, `/delete`, `/destroy` - each key is one poll
 * channel and its value is the last id at render time) and `currentUser` (whose
 * `notification_channel_position` is `MessageBus.last_id("/notification/{userId}")`, synced right
 * before the page is rendered, and whose `unread_notifications` /
 * `unread_high_priority_notifications` / `all_unread_notifications_count` are the counts the site's
 * own header badge is seeded from). Values may be JSON objects or JSON strings that themselves
 * contain the object, which is how the server ships them; both shapes are accepted. A page with no
 * preload data (anonymous, or a plain JSON caller) yields an empty map and nulls - callers then
 * start from [MessageBusClient.INITIAL_LAST_ID] and let the `/__status` message fill the ids in.
 *
 * `long_polling_base_url` and its `shared_session_key` (a meta tag, when the site polls a separate
 * origin) are carried too; the key is what the poll then sends as `X-Shared-Session-Key`.
 */
data class MessageBusBootstrap(
    val channelLastIds: Map<String, Long> = emptyMap(),
    val currentUserId: Long? = null,
    val notificationChannelPosition: Long? = null,
    val sharedSessionKey: String? = null,
    val notificationCounts: NotificationUnreadCounts? = null,
    /**
     * Where the site's own web client polls from, when the rendered page names a base that is not
     * the site itself (a separate message-bus process). Null means the site's own origin.
     */
    val longPollingBaseUrl: String? = null,
) {
    /** The site's position for [channel], or the no-lookbehind default when it did not name one. */
    fun lastIdFor(channel: String): Long = channelLastIds[channel] ?: MessageBusClient.INITIAL_LAST_ID

    /** The channel a current user's notification counts arrive on, or null when the id is unknown. */
    val notificationChannel: String?
        get() = currentUserId?.let { "/notification/$it" }

    companion object {
        /** Parses the homepage/bootstrap HTML the site renders for an authenticated session. */
        fun parse(html: String, json: Json = NetworkJson.instance): MessageBusBootstrap {
            val document = Jsoup.parse(html)
            val sharedSessionKey = document.selectFirst("meta[name=shared_session_key]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }
            val preloadedElement = document.selectFirst("#data-preloaded")
            // linux.do (and current Discourse) ship the preload as a JSON <script>: the element is
            // `script#data-preloaded` and its *text* is the object. Older renders put the same JSON
            // in the div's `data-preloaded` attribute, so both are read.
            val preloadedText = when {
                preloadedElement == null -> null
                preloadedElement.tagName() == "script" -> preloadedElement.data().takeIf { it.isNotBlank() }
                else -> preloadedElement.attr("data-preloaded").takeIf { it.isNotBlank() }
            }
            val preloaded = preloadedText?.let { text ->
                runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            } ?: JsonObject(emptyMap())
            val setup = document.selectFirst("#data-discourse-setup")
            val currentUserKeys = unwrap(preloaded["currentUser"], json)?.keys?.sorted()?.joinToString(",") ?: "-"
            MessageBusDiagnostics.event(
                "bootstrap_shape",
                "title=[${document.title()}] htmlLength=${html.length} " +
                    "preloadTag=${preloadedElement?.tagName() ?: "-"} " +
                    "preloadTextLen=${preloadedText?.length ?: -1} " +
                    "currentUsernameMeta=${document.selectFirst("meta[name=current-username]") != null} " +
                    "userKeys=[$currentUserKeys] " +
                    "preloaded=[${preloaded.keys.sorted().joinToString(",")}] " +
                    "setup=[${setup?.attributes()?.map { it.key }?.sorted()?.joinToString(",") ?: "-"}] " +
                    "metas=[${document.select("meta[name]").map { it.attr("name") }.distinct().sorted().joinToString(",")}]",
            )
            return fromPreloadedJson(preloaded, sharedSessionKey, json)
        }

        /** The same read for a caller that already holds the parsed `#data-preloaded` object. */
        fun fromPreloadedJson(
            preloaded: JsonObject,
            sharedSessionKey: String? = null,
            json: Json = NetworkJson.instance,
        ): MessageBusBootstrap {
            val currentUser = unwrap(preloaded["currentUser"], json)
            val channelLastIds = (unwrap(preloaded["topicTrackingStateMeta"], json) ?: JsonObject(emptyMap()))
                .mapNotNull { (channel, value) ->
                    (value as? JsonPrimitive)?.longOrNull?.let { lastId -> channel to lastId }
                }
                .toMap()
            return MessageBusBootstrap(
                channelLastIds = channelLastIds,
                currentUserId = (currentUser?.get("id") as? JsonPrimitive)?.longOrNull,
                notificationChannelPosition = (currentUser?.get("notification_channel_position") as? JsonPrimitive)
                    ?.longOrNull,
                sharedSessionKey = sharedSessionKey,
                notificationCounts = readNotificationCounts(currentUser),
                longPollingBaseUrl = readLongPollingBaseUrl(preloaded, json),
            )
        }

        /**
         * Where the page says its web client polls. Discourse puts it in the preload: current
         * versions under `siteSettings.long_polling_base_url` (the initializer reads exactly that
         * and treats `/` as "the site itself"), older ones under `site.long_polling_base_url` or a
         * top-level `longPollingBaseUrl`. The message-bus process may be a separate origin (a CDN
         * or an origin server Cloudflare does not front), in which case the site's own
         * `/message-bus` route is not served at all and the poll authenticates with
         * [sharedSessionKey]. A missing value means the site's own origin.
         */
        private fun readLongPollingBaseUrl(preloaded: JsonObject, json: Json): String? {
            for (key in listOf("longPollingBaseUrl", "long_polling_base_url")) {
                (preloaded[key] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() && it != "/" }
                    ?.let { return it }
            }
            val containers = listOfNotNull(unwrap(preloaded["site"], json), unwrap(preloaded["siteSettings"], json))
            for (container in containers) {
                for (key in listOf("long_polling_base_url", "longPollingBaseUrl")) {
                    (container[key] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.isNotBlank() && it != "/" }
                        ?.let { return it }
                }
            }
            return null
        }

        /**
         * The counts the preloaded user carries, or null when the page names none of them. A page
         * that names only some is still a seed: the absent ones start at zero, because there is no
         * earlier value to keep (unlike a live [NotificationCountStateHolder] message, where an
         * omitted field means "unchanged").
         */
        private fun readNotificationCounts(currentUser: JsonObject?): NotificationUnreadCounts? {
            if (currentUser == null) return null
            val unread = (currentUser["unread_notifications"] as? JsonPrimitive)?.intOrNull
            val highPriority = (currentUser["unread_high_priority_notifications"] as? JsonPrimitive)?.intOrNull
            val allUnread = (currentUser["all_unread_notifications_count"] as? JsonPrimitive)?.intOrNull
            if (unread == null && highPriority == null && allUnread == null) return null
            return NotificationUnreadCounts(
                unread = unread ?: 0,
                highPriority = highPriority ?: 0,
                allUnread = allUnread ?: 0,
            )
        }

        private fun unwrap(element: JsonElement?, json: Json): JsonObject? = when (element) {
            null -> null
            is JsonObject -> element
            is JsonPrimitive -> element.contentOrNull
                ?.let { text -> runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject }
            else -> null
        }
    }
}
