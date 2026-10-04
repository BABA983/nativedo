package io.github.baba983.nativedo.core.messagebus

import io.github.baba983.nativedo.core.network.NetworkJson
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

/** One message delivered by a Discourse message-bus channel, exactly as the poll response carried it. */
data class MessageBusMessage(
    val channel: String,
    val messageId: Long,
    val globalId: Long,
    val data: JsonElement,
)

/** A channel subscription's callback; see [MessageBusClient.subscribe]. */
typealias MessageBusListener = (MessageBusMessage) -> Unit

/** What the poller is doing, for a screen that wants to show a connection state. */
sealed interface MessageBusState {
    data object Stopped : MessageBusState
    data object Polling : MessageBusState
    data class BackingOff(val delayMillis: Long, val consecutiveFailures: Int) : MessageBusState
}

/** What one poll request ended as; drives the retry policy below. */
internal sealed interface MessageBusPollOutcome {
    /** No subscriptions, so no request was made. */
    data object Idle : MessageBusPollOutcome

    /** A 2xx answer that carried at least one message. */
    data object Delivered : MessageBusPollOutcome

    /** A 2xx answer with nothing on the subscribed channels (the normal long-poll answer). */
    data object Empty : MessageBusPollOutcome

    /** HTTP 429; [retryAfterSeconds] is the server's `Retry-After` when it sent one. */
    data class RateLimited(val retryAfterSeconds: Long?) : MessageBusPollOutcome

    /** A transport error or a non-2xx answer other than 429. */
    data class Failed(val statusCode: Int?, val error: Throwable?) : MessageBusPollOutcome
}

/**
 * Discourse's message bus (`message_bus` gem), as the site's own web client drives it.
 *
 * One long poll per turn: `POST {base}/message-bus/{clientId}/poll` with a form body of
 * `channel=lastReceivedMessageId` pairs plus `__seq`, which increments with every request.
 * `-1` means "only messages published from now on"; the server answers such a subscription with a
 * `/__status` message carrying each channel's current last id, and it sends that message again when
 * the client asked for an id the channel never reached (deliveries were filtered or skipped) - the
 * client must take the server's value back, not keep its own. Every delivered `message_id` becomes
 * the next request's `last_id` for that channel. See contracts/discourse/endpoints.md.
 *
 * Host wiring: this class does not own cookies, CSRF, or the user agent. It takes the app's
 * [OkHttpClient] (see AppContainer) so the session-cookie and request-header interceptors run
 * unchanged, and it never starts a login flow: a challenge, a logout or an outage is only a failed
 * poll that backs off. Message-bus routes are Rack middleware and need no CSRF token.
 *
 * Chunked streaming is off by default and the request then carries `Dont-Chunk: true`, so every
 * answer is one JSON array and a long poll ends after the server's own interval. When
 * [enableChunkedEncoding] is on, the response is read as a stream of JSON arrays separated by
 * `\r\n|\r\n` (the gem escapes a literal separator inside a frame as `\r\n||\r\n`).
 */
class MessageBusClient(
    httpClient: OkHttpClient,
    baseUrl: HttpUrl,
    private val json: Json = NetworkJson.instance,
    val clientId: String = newMessageBusClientId(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val maxPollIntervalMillis: Long = DEFAULT_MAX_POLL_INTERVAL_MILLIS,
    private val minPollIntervalMillis: Long = DEFAULT_MIN_POLL_INTERVAL_MILLIS,
    private val rateLimitedFloorMillis: Long = DEFAULT_RATE_LIMITED_FLOOR_MILLIS,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    private val enableChunkedEncoding: Boolean = false,
    private val sharedSessionKey: String? = null,
    private val longPollingEnabled: Boolean = true,
) {
    private val http: OkHttpClient = httpClient.newBuilder()
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    private val pollUrl: HttpUrl = baseUrl.newBuilder()
        .addPathSegments(POLL_PATH)
        .addPathSegment(clientId)
        .addPathSegment("poll")
        .build()

    private class Subscription(var lastId: Long) {
        val listeners = mutableListOf<MessageBusListener>()
    }

    private val lock = Any()
    private val subscriptions = LinkedHashMap<String, Subscription>()
    private var sequence = 0L
    private var failureCount = 0

    @Volatile
    private var generation = 0L

    @Volatile
    private var pollingJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    private val _state = MutableStateFlow<MessageBusState>(MessageBusState.Stopped)

    /** The poller's own state; [MessageBusState.BackingOff] also carries the computed wait. */
    val state: StateFlow<MessageBusState> = _state.asStateFlow()

    /** Whether a poll loop is currently running (i.e. [start] was called and [stop] was not). */
    val isPolling: Boolean
        get() = pollingJob?.isActive == true

    /**
     * Registers interest in [channel], starting from [lastId].
     *
     * A fresh channel with the default [INITIAL_LAST_ID] gets only messages published from now on;
     * passing the site's own position (see [MessageBusBootstrap]) also replays what arrived since
     * that position. Subscribing an already-subscribed channel only adds [listener]; a [lastId]
     * that is further ahead than the stored one moves it forward, so a bootstrap id that arrives
     * after an early `-1` subscription is still honored.
     */
    fun subscribe(
        channel: String,
        lastId: Long = INITIAL_LAST_ID,
        listener: MessageBusListener = {},
    ) {
        require(channel.isNotEmpty()) { "channel must not be empty" }
        synchronized(lock) {
            val existing = subscriptions[channel]
            if (existing == null) {
                subscriptions[channel] = Subscription(lastId).also { it.listeners += listener }
            } else {
                existing.listeners += listener
                if (lastId > existing.lastId) existing.lastId = lastId
            }
        }
    }

    /**
     * Removes [listener] from [channel], or the whole channel when [listener] is null. Removing the
     * last listener drops the channel, so the next poll no longer asks for it.
     */
    fun unsubscribe(channel: String, listener: MessageBusListener? = null) {
        synchronized(lock) {
            val existing = subscriptions[channel] ?: return
            if (listener == null) {
                subscriptions.remove(channel)
                return
            }
            existing.listeners.remove(listener)
            if (existing.listeners.isEmpty()) subscriptions.remove(channel)
        }
    }

    /** The id the next poll will send for [channel], or null when it is not subscribed. */
    fun lastIdOf(channel: String): Long? = synchronized(lock) { subscriptions[channel]?.lastId }

    /** Starts the poll loop; repeated calls are a no-op while it runs. */
    @Synchronized
    fun start() {
        if (pollingJob?.isActive == true) return
        generation += 1
        val startedGeneration = generation
        failureCount = 0
        _state.value = MessageBusState.Polling
        pollingJob = scope.launch {
            while (isActive && startedGeneration == generation) {
                val startedAtNanos = System.nanoTime()
                val outcome = pollOnce()
                if (startedGeneration != generation || !isActive) break
                when (outcome) {
                    is MessageBusPollOutcome.Failed -> failureCount += 1
                    is MessageBusPollOutcome.Delivered,
                    is MessageBusPollOutcome.Empty,
                    -> failureCount = 0
                    else -> Unit
                }
                val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                val waitMillis = nextDelayMillis(outcome, elapsedMillis, failureCount)
                _state.value = when (outcome) {
                    is MessageBusPollOutcome.Failed,
                    is MessageBusPollOutcome.RateLimited,
                    -> MessageBusState.BackingOff(waitMillis, failureCount)
                    else -> MessageBusState.Polling
                }
                MessageBusDiagnostics.poll(outcome, elapsedMillis, waitMillis, failureCount)
                delay(waitMillis)
            }
            if (startedGeneration == generation) _state.value = MessageBusState.Stopped
        }
    }

    /**
     * Stops the loop and cancels a long poll that is in flight, so a backgrounded app lets go of the
     * connection instead of waiting out the server's poll interval.
     */
    @Synchronized
    fun stop() {
        generation += 1
        activeCall?.cancel()
        activeCall = null
        pollingJob?.cancel()
        pollingJob = null
        _state.value = MessageBusState.Stopped
    }

    /**
     * One poll turn: sends the currently subscribed channels and their last ids, parses what comes
     * back, and updates ids and listeners. Exposed to tests; production uses [start].
     */
    internal suspend fun pollOnce(): MessageBusPollOutcome = withContext(Dispatchers.IO) {
        val request = synchronized(lock) {
            if (subscriptions.isEmpty()) return@withContext MessageBusPollOutcome.Idle
            sequence += 1
            val form = FormBody.Builder()
            for ((channel, subscription) in subscriptions) {
                form.add(channel, subscription.lastId.toString())
            }
            form.add(SEQ_FIELD, sequence.toString())
            Request.Builder()
                .url(if (longPollingEnabled) pollUrl else pollUrl.newBuilder().addQueryParameter("dlp", "t").build())
                .post(form.build())
                .header("X-SILENCE-LOGGER", "true")
                .apply {
                    if (!enableChunkedEncoding) header("Dont-Chunk", "true")
                    if (!sharedSessionKey.isNullOrBlank()) header("X-Shared-Session-Key", sharedSessionKey)
                }
                .build()
        }
        val call = http.newCall(request)
        activeCall = call
        try {
            call.execute().use { response -> handleResponse(response) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (call.isCanceled()) throw CancellationException("message bus poll cancelled")
            MessageBusPollOutcome.Failed(statusCode = null, error = error)
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    /**
     * The wait before the next poll, mirroring the gem's own JavaScript client: a delivered message
     * retries almost at once, an empty long poll waits out the remaining callback interval, a 429
     * honors `Retry-After` with a 15 s floor, and transport/server failures back off linearly up to
     * [maxPollIntervalMillis].
     */
    internal fun nextDelayMillis(
        outcome: MessageBusPollOutcome,
        elapsedMillis: Long,
        consecutiveFailures: Int,
    ): Long = when (outcome) {
        MessageBusPollOutcome.Idle -> IDLE_WAIT_MILLIS
        MessageBusPollOutcome.Delivered -> minPollIntervalMillis
        MessageBusPollOutcome.Empty -> (pollIntervalMillis - elapsedMillis).coerceAtLeast(minPollIntervalMillis)
        is MessageBusPollOutcome.RateLimited -> maxOf(
            rateLimitedFloorMillis,
            (outcome.retryAfterSeconds ?: 0L) * 1_000L,
        )
        is MessageBusPollOutcome.Failed ->
            (pollIntervalMillis * consecutiveFailures.coerceAtLeast(1))
                .coerceAtMost(maxPollIntervalMillis)
                .coerceAtLeast(minPollIntervalMillis)
    }

    private fun handleResponse(response: Response): MessageBusPollOutcome {
        MessageBusDiagnostics.response(
            status = response.code,
            contentLength = response.header("Content-Length")?.trim()?.toLongOrNull(),
            chunked = response.header("Transfer-Encoding")?.contains("chunked", ignoreCase = true) == true,
        )
        if (response.code == 429) {
            return MessageBusPollOutcome.RateLimited(parseRetryAfter(response.header("Retry-After")))
        }
        if (!response.isSuccessful) {
            return MessageBusPollOutcome.Failed(statusCode = response.code, error = null)
        }
        val body = response.body ?: return MessageBusPollOutcome.Empty
        val messages = body.use { payload ->
            val contentType = response.header("Content-Type").orEmpty()
            val streaming = enableChunkedEncoding && !contentType.contains("application/json", ignoreCase = true)
            if (streaming) readChunkedPayloads(payload.source()) else parseMessageArray(payload.string())
        }
        if (messages.isEmpty()) return MessageBusPollOutcome.Empty
        deliver(messages)
        return MessageBusPollOutcome.Delivered
    }

    /**
     * Applies ids exactly as the messages arrived: a delivered message advances its own channel's
     * id monotonically, while `/__status` sets (or rewinds) the ids of the subscribed channels it
     * names and is not delivered to channel listeners.
     */
    private fun deliver(messages: List<MessageBusMessage>) {
        for (message in messages) {
            val listeners = synchronized(lock) {
                if (message.channel == STATUS_CHANNEL) {
                    applyStatusLocked(message.data)
                    subscriptions[STATUS_CHANNEL]?.listeners?.toList()
                } else {
                    val subscription = subscriptions[message.channel]
                    if (subscription != null && message.messageId > subscription.lastId) {
                        subscription.lastId = message.messageId
                    }
                    subscription?.listeners?.toList()
                }
            }
            listeners?.forEach { listener ->
                try {
                    listener(message)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // A listener bug must not take the poller down; later messages still count.
                }
            }
        }
    }

    /** Must be called under [lock]. */
    private fun applyStatusLocked(data: JsonElement) {
        val statuses = data as? JsonObject ?: return
        for ((channel, value) in statuses) {
            val lastId = (value as? JsonPrimitive)?.longOrNull ?: continue
            subscriptions[channel]?.lastId = lastId
        }
    }

    private fun parseMessageArray(payload: String): List<MessageBusMessage> {
        val element = try {
            json.parseToJsonElement(payload)
        } catch (_: SerializationException) {
            return emptyList()
        }
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item -> (item as? JsonObject)?.let(::toMessage) }
    }

    private fun toMessage(element: JsonObject): MessageBusMessage? {
        val channel = (element["channel"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (channel.isEmpty()) return null
        val messageId = (element["message_id"] as? JsonPrimitive)?.longOrNull ?: -1L
        val globalId = (element["global_id"] as? JsonPrimitive)?.longOrNull ?: -1L
        return MessageBusMessage(channel, messageId, globalId, element["data"] ?: JsonNull)
    }

    /**
     * Reads a streamed answer: complete frames end at `\r\n|\r\n` and a trailing frame is taken at
     * end of stream. A frame that does not parse is skipped without disturbing the frames around it.
     */
    private fun readChunkedPayloads(source: BufferedSource): List<MessageBusMessage> {
        val messages = mutableListOf<MessageBusMessage>()
        val separator = CHUNK_SEPARATOR.encodeUtf8()
        while (true) {
            val separatorIndex = source.indexOf(separator)
            if (separatorIndex < 0L) break
            val frame = source.readUtf8(separatorIndex)
            source.readByteArray(separator.size.toLong())
            messages += parseMessageArray(frame.replace(CHUNK_ESCAPE, CHUNK_SEPARATOR))
        }
        val tail = source.readUtf8().trim()
        if (tail.isNotEmpty()) messages += parseMessageArray(tail)
        return messages
    }

    private fun parseRetryAfter(value: String?): Long? =
        value?.trim()?.toLongOrNull()?.takeIf { it >= 0L }

    companion object {
        /** A subscription with no lookbehind: only messages published from now on. */
        const val INITIAL_LAST_ID: Long = -1L

        /** The gem's status channel, whose data maps channel name to that channel's last id. */
        const val STATUS_CHANNEL: String = "/__status"

        private const val POLL_PATH = "message-bus"
        private const val SEQ_FIELD = "__seq"
        private const val CHUNK_SEPARATOR = "\r\n|\r\n"
        private const val CHUNK_ESCAPE = "\r\n||\r\n"
        private const val IDLE_WAIT_MILLIS = 1_000L
        private const val DEFAULT_POLL_INTERVAL_MILLIS = 3_000L
        private const val DEFAULT_MIN_POLL_INTERVAL_MILLIS = 100L
        private const val DEFAULT_MAX_POLL_INTERVAL_MILLIS = 180_000L
        private const val DEFAULT_RATE_LIMITED_FLOOR_MILLIS = 15_000L
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 60_000L
    }
}

/** A per-process client id, 32 hex characters like the web client's own random id. */
private fun newMessageBusClientId(): String = UUID.randomUUID().toString().replace("-", "")
