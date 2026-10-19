package io.github.baba983.nativedo.core.messagebus

import io.github.baba983.nativedo.core.session.AppSessionState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** One attached listener; [close] detaches it. Handles are not reusable after closing. */
class MessageBusHandle internal constructor(private val job: Job) {
    fun close() {
        job.cancel()
    }
}

/** A screen's badge listener; called on the session's own dispatcher (the main thread in the app). */
fun interface NotificationCountsListener {
    fun onCounts(counts: NotificationUnreadCounts)
}

/** A topic list's incoming-topics listener; called on the session's own dispatcher. */
fun interface IncomingTopicsListener {
    fun onTopics(topics: IncomingTopics)
}

/**
 * The app's one message-bus session, owned by the container rather than by a screen.
 *
 * It follows the session and the app's foreground state by itself:
 *  * signed in and foregrounded -> one [MessageBusClient] is created (once) and started, seeded
 *    with the positions and counts a rendered Discourse page carries ([MessageBusBootstrap], read
 *    through [fetchBootstrapHtml]), subscribing `/latest`, `/new` and `/notification/{userId}`;
 *  * background -> the poller stops (the holders and their state stay, so the badge survives and
 *    the next foreground resumes where the poll left off without refetching the bootstrap);
 *  * signed out or expired -> the client is dropped, its holders closed and both state flows reset
 *    to zero, so the next sign-in builds a fresh client with a fresh client id and the new
 *    reader's own channels.
 *
 * The bootstrap fetch is the only request this class makes, and a failure (a challenge, an outage,
 * a page that cannot be read) is only a missing start: the session retries on the next foreground,
 * and it never asks for a login. The poller itself never touches CSRF or identity - the client
 * rides the app's OkHttp stack (cookies and headers are the session's own interceptors).
 */
class MessageBusSession(
    private val httpClient: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val sessionState: StateFlow<AppSessionState>,
    private val fetchBootstrapHtml: suspend () -> String,
    private val scope: CoroutineScope,
) {

    private val _incomingTopics = MutableStateFlow(IncomingTopics())

    /** Topics that arrived since the list last refreshed. */
    val incomingTopics: StateFlow<IncomingTopics> = _incomingTopics.asStateFlow()

    private val _notificationCounts = MutableStateFlow(NotificationUnreadCounts())

    /** The reader's notification badge counts. */
    val notificationCounts: StateFlow<NotificationUnreadCounts> = _notificationCounts.asStateFlow()

    @Volatile
    private var client: MessageBusClient? = null

    private val _runningClient = MutableStateFlow<MessageBusClient?>(null)

    /**
     * The running client as a flow: a signed-in foreground session publishes the one client it
     * started, and a dropped session (sign-out, expiry) publishes null. A screen that attaches to
     * the bus - chat presence, a chat channel's live updates - follows this instead of reading
     * [runningClient] once, because the bootstrap may not have landed when the screen opens and a
     * new reader always gets a new client.
     */
    val runningClientFlow: StateFlow<MessageBusClient?> = _runningClient.asStateFlow()

    /** The running client's id, or null while no session is running; diagnostics and tests. */
    val clientId: String?
        get() = client?.clientId

    /**
     * The running poller, for a screen that wants to subscribe its own channels (the chat screen's
     * `/chat/{id}` updates). Null while no session runs - signed out, backgrounded, or the initial
     * bootstrap has not landed yet - and such a caller falls back to explicit refresh.
     */
    val runningClient: MessageBusClient?
        get() = client

    /** Whether a poll loop is running right now (signed in and foregrounded). */
    val isPolling: Boolean
        get() = client?.isPolling == true

    private var topicTracking: TopicTrackingStateHolder? = null
    private var notificationState: NotificationCountStateHolder? = null
    private var startJob: Job? = null
    private val holderJobs = mutableListOf<Job>()
    private val foregrounded = AtomicBoolean(false)
    private val generationLock = Any()

    /**
     * Bumped whenever the session changes (a start begins, a stop happens). A holder collector
     * carries the generation it was born with and drops what a superseded session published, so a
     * message in flight while the reader signs out cannot repaint the cleared badge.
     */
    @Volatile
    private var generation = 0L

    /**
     * The newest readback request of the session; see [refreshNotificationCounts]. Guarded by
     * [generationLock] and reset with the session, so only the latest readback a screen asked for
     * can apply its page.
     */
    private var refreshToken = 0L

    init {
        scope.launch {
            sessionState.collect { state -> onSessionState(state) }
        }
    }

    /** The app came to the front; a signed-in session starts polling. */
    fun onForeground() {
        foregrounded.set(true)
        if (sessionState.value is AppSessionState.Authenticated) {
            ensureStarted()
        }
    }

    /** The app went to the back; the poller lets go of its connection. */
    fun onBackground() {
        foregrounded.set(false)
        client?.stop()
    }

    /** The list refreshed: the topics it now shows are no longer "new". */
    fun clearIncomingTopics() {
        topicTracking?.clear()
        _incomingTopics.value = IncomingTopics()
    }

    /**
     * Re-reads the site's own badge counts after a write that changed them (a mark-read), without
     * restarting the bus: the bootstrap page carries the reader's counts, and the running
     * [NotificationCountStateHolder] adopts them.
     *
     * <p>The readback is guarded three ways. Only the latest readback the screen asked for may
     * apply - an older request that happens to finish first is dropped - so two concurrent
     * readbacks cannot leave the stale one standing. The session [generation] is captured before
     * the page is fetched and re-checked before it is applied, so a sign-out or a new reader that
     * happened meanwhile drops it. The holder's revision is captured too: a channel message that
     * arrives while the page is in flight is newer than a page rendered before it, so the readback
     * is dropped and the live count stands. Without a running session (signed out) there is
     * nothing to read for, and the call is a no-op.</p>
     */
    fun refreshNotificationCounts() {
        val readback = synchronized(generationLock) {
            val running = notificationState ?: return
            refreshToken += 1
            PendingReadback(running, generation, running.currentRevision(), refreshToken)
        }
        scope.launch {
            MessageBusDiagnostics.event("notifications_refresh")
            val html = try {
                fetchBootstrapHtml()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                return@launch
            }
            val bootstrap = try {
                MessageBusBootstrap.parse(html)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                return@launch
            }
            val counts = bootstrap.notificationCounts ?: return@launch
            synchronized(generationLock) {
                // The session moved on, or a newer readback was asked for meanwhile and this page
                // is the older one: drop it, so whichever answer finishes first cannot beat the
                // latest request. The holder's own revision still guards against a live message
                // that arrived while even the newest readback was out.
                if (readback.generation != generation || readback.token != refreshToken) {
                    return@synchronized
                }
                readback.holder.applyReadback(counts, readback.revision)
            }
        }
    }

    /** Starts [listener] on the session's dispatcher; close the handle to detach it. */
    fun observeIncomingTopics(listener: IncomingTopicsListener): MessageBusHandle =
        MessageBusHandle(scope.launch { incomingTopics.collect { listener.onTopics(it) } })

    /** Starts [listener] on the session's dispatcher; close the handle to detach it. */
    fun observeNotificationCounts(listener: NotificationCountsListener): MessageBusHandle =
        MessageBusHandle(scope.launch { notificationCounts.collect { listener.onCounts(it) } })

    /** Drops the session for good; the container does this only when it is being discarded. */
    fun close() {
        stopSession(clearState = true)
    }

    private fun onSessionState(state: AppSessionState) {
        if (state is AppSessionState.Authenticated) {
            if (foregrounded.get()) {
                ensureStarted()
            }
        } else {
            stopSession(clearState = true)
        }
    }

    /**
     * One client per signed-in session: an already-built client is only restarted, and while a
     * start (the bootstrap fetch) is in flight a second call is a no-op. The check after the fetch
     * is what keeps a sign-out or a backgrounding that happened meanwhile from leaving a poller
     * behind. The guard is held under [generationLock] because the two callers can arrive on
     * different threads (a foreground callback and the session-state collector on a multi-thread
     * dispatcher); without it both would build a client and one poll stream would leak.
     */
    private fun ensureStarted() = synchronized(generationLock) {
        val running = client
        if (running != null) {
            running.start()
            return@synchronized
        }
        if (startJob?.isActive == true) {
            return@synchronized
        }
        startJob = scope.launch {
            MessageBusDiagnostics.event("bootstrap_fetch")
            val html = try {
                fetchBootstrapHtml()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                MessageBusDiagnostics.event("bootstrap_failed", "reason=${error.javaClass.simpleName}")
                return@launch
            }
            val bootstrap = try {
                MessageBusBootstrap.parse(html)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                MessageBusDiagnostics.event("bootstrap_failed", "reason=${error.javaClass.simpleName}")
                return@launch
            }
            if (!foregrounded.get() || sessionState.value !is AppSessionState.Authenticated) {
                return@launch
            }
            val startedGeneration = beginGenerationIfLive() ?: return@launch
            commitSession(startedGeneration, bootstrap)
        }
    }

    /** Starts a session generation, or null when the app signed out or went back meanwhile. */
    private fun beginGenerationIfLive(): Long? = synchronized(generationLock) {
        if (!foregrounded.get() || sessionState.value !is AppSessionState.Authenticated) {
            null
        } else {
            generation += 1
            generation
        }
    }

    /**
     * Puts the fetched bootstrap into service: the badge seed, the client and its holders. It is
     * committed under the same lock as [stopSession], so a sign-out that happened while the page
     * was being read either beats this commit (nothing is built) or is beaten by it (its stop then
     * tears the fresh client down); there is no window where a signed-out app keeps a poller.
     */
    private fun commitSession(startedGeneration: Long, bootstrap: MessageBusBootstrap) =
        synchronized(generationLock) {
            if (startedGeneration != generation) {
                return@synchronized
            }
            val counts = bootstrap.notificationCounts ?: NotificationUnreadCounts()
            _notificationCounts.value = counts
            val pollBase = bootstrap.longPollingBaseUrl
                ?.let { runCatching { it.toHttpUrl() }.getOrNull() }
                ?: baseUrl
            val bus = MessageBusClient(
                httpClient = httpClient,
                baseUrl = pollBase,
                scope = scope,
                sharedSessionKey = bootstrap.sharedSessionKey,
            )
            client = bus
            _runningClient.value = bus
            attachHolders(bus, bootstrap, counts, startedGeneration)
            MessageBusDiagnostics.event(
                "session_started",
                "hasUser=${bootstrap.currentUserId != null} " +
                    "channels=${bootstrap.channelLastIds.size} " +
                    "counts=${counts.allUnread} " +
                    "ownPollBase=${pollBase == baseUrl} " +
                    "hasSharedKey=${!bootstrap.sharedSessionKey.isNullOrBlank()}",
            )
            bus.start()
        }

    private fun attachHolders(
        bus: MessageBusClient,
        bootstrap: MessageBusBootstrap,
        counts: NotificationUnreadCounts,
        startedGeneration: Long,
    ) {
        val tracking = TopicTrackingStateHolder(bus, bootstrap.channelLastIds)
        topicTracking = tracking
        holderJobs += scope.launch {
            tracking.incoming.collect { topics ->
                if (startedGeneration == generation) _incomingTopics.value = topics
            }
        }

        val userId = bootstrap.currentUserId
        if (userId != null) {
            val notification = NotificationCountStateHolder(
                client = bus,
                userId = userId,
                initialCounts = counts,
                initialLastId = bootstrap.notificationChannelPosition
                    ?: bootstrap.lastIdFor("/notification/$userId"),
            )
            notificationState = notification
            holderJobs += scope.launch {
                notification.counts.collect { live ->
                    if (startedGeneration == generation) _notificationCounts.value = live
                }
            }
        }
    }

    private fun stopSession(clearState: Boolean) = synchronized(generationLock) {
        generation += 1
        refreshToken = 0
        startJob?.cancel()
        startJob = null
        client?.stop()
        if (!clearState) {
            return@synchronized
        }
        topicTracking?.close()
        notificationState?.close()
        topicTracking = null
        notificationState = null
        holderJobs.forEach(Job::cancel)
        holderJobs.clear()
        _incomingTopics.value = IncomingTopics()
        _notificationCounts.value = NotificationUnreadCounts()
        // Last, so a caller that sees no client also sees the cleared state: this is what the
        // screens' observers read on a sign-out.
        client = null
        _runningClient.value = null
        MessageBusDiagnostics.event("session_stopped")
    }

    /** One in-flight readback: the holder and session it was started for, and the token of the request. */
    private class PendingReadback(
        val holder: NotificationCountStateHolder,
        val generation: Long,
        val revision: Long,
        val token: Long,
    )
}
