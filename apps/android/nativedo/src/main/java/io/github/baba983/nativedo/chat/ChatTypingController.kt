package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.messagebus.MessageBusClient
import io.github.baba983.nativedo.core.messagebus.MessageBusListener
import io.github.baba983.nativedo.core.messagebus.MessageBusMessage
import io.github.baba983.nativedo.core.model.ChatUser
import io.github.baba983.nativedo.core.network.ChatPresenceDelta
import io.github.baba983.nativedo.core.network.ChatPresenceState
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseRefusalException
import io.github.baba983.nativedo.core.network.SessionChangedException
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.network.decodeChatPresenceDelta
import io.github.baba983.nativedo.core.session.LoginRequiredException
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The timings of one chat typing presence, with the defaults the site's own web client uses.
 *
 * <p>[refreshIntervalMillis] mirrors `PRESENCE_INTERVAL_S` (30 s): a present client re-announces
 * before `PresenceChannel::DEFAULT_TIMEOUT` (60 s) expires it. [throttleMillis] mirrors
 * `PRESENCE_THROTTLE_MS` (1 s), and the server also allows 20 updates per 10 s per user.
 * [typingIdleMillis] mirrors the chat composer's `CHAT_PRESENCE_KEEP_ALIVE` (5 s): typing stops
 * being announced five seconds after the last input, while the draft itself stays untouched.
 * [snapshotRetryMillis] mirrors `PRESENCE_GET_RETRY_MS`; [retryBaseMillis]/[retryMaxMillis] mirror
 * the web client's exponential backoff for a failed update, capped at the 30 s refresh interval.
 *
 * <p>The values are a constructor seam so a test can drive the same state machine in milliseconds;
 * production uses the defaults.
 */
data class ChatTypingPolicy(
    val refreshIntervalMillis: Long = 30_000L,
    val throttleMillis: Long = 1_000L,
    val typingIdleMillis: Long = 5_000L,
    val retryBaseMillis: Long = 1_000L,
    val retryMaxMillis: Long = 30_000L,
    val snapshotRetryMillis: Long = 5_000L,
)

/**
 * One chat channel's typing presence: who else has something in their composer, and whether this
 * reader's own composing is announced to them.
 *
 * <p>Discourse's presence is a pair of endpoints and a message-bus channel, not a chat endpoint:
 * `GET /presence/get?channels[]=/chat-reply/{id}` returns the channel's `users`/`count` and the
 * message-bus `last_message_id`; `POST /presence/update` names this client present in
 * `present_channels[]` or absent in `leave_channels[]`; and `/presence/chat-reply/{id}` publishes
 * `entering_users`/`leaving_user_ids` deltas, whose message ids are contiguous or the subscriber
 * must resynchronise from a fresh `/presence/get`. The channel name is the chat plugin's
 * `PRESENCE_REGEXP` (`/chat-reply/{channelId}`); nothing here touches `/chat/online` and nothing
 * broadcasts this reader globally.
 *
 * <p>Privacy is the site's own rule, read from the same `GET /session/current.json` the session
 * layer verifies with: `current_user.user_option.hide_presence`. While that value is unknown - the
 * read failed, the site omitted the option, or a session boundary just moved - this controller
 * reads presence but never announces, so absence of an answer is never permission. A different
 * username in that read means the account behind this screen changed: the binding keeps reading
 * but never announces, and no queued write is replayed under the new account.
 *
 * <p>The controller is Java-friendly on purpose: the screen owns its lifecycle and the composer
 * calls [textChanged]; everything else runs on the injected scope. [UsersListener] is called on
 * the callback dispatcher, and only [ChatUser]s other than this reader are passed - the site's
 * own list includes the reader, and the reader is never a typers indicator for themselves.
 */
class ChatTypingController @JvmOverloads constructor(
    private val client: DiscourseApiClient,
    private val runningClient: StateFlow<MessageBusClient?>,
    scope: CoroutineScope = hostScope,
    private val callbacks: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val policy: ChatTypingPolicy = ChatTypingPolicy(),
) : AutoCloseable {

    private val scope: CoroutineScope = scope

    /** The users currently composing in this channel, this reader excluded. */
    fun interface UsersListener {
        fun onUsers(users: List<ChatUser>)
    }

    private enum class Action { Present, Leave }

    private val lock = Any()
    private var binding: Binding? = null
    private var observerJob: Job? = null

    @Volatile
    private var usersListener: UsersListener? = null

    @Volatile
    private var publishedUsers: List<ChatUser> = emptyList()

    /** Bumped under [lock] with every visible-list change, so an older callback cannot land last. */
    private var publishRevision = 0L

    init {
        require(policy.refreshIntervalMillis > 0L) { "refreshIntervalMillis must be positive" }
        require(policy.typingIdleMillis > 0L) { "typingIdleMillis must be positive" }
        require(policy.retryBaseMillis > 0L) { "retryBaseMillis must be positive" }
        require(policy.retryMaxMillis >= policy.retryBaseMillis) { "retryMaxMillis is below retryBaseMillis" }
        require(policy.snapshotRetryMillis > 0L) { "snapshotRetryMillis must be positive" }
    }

    /** Receives every change of the visible list; the callback runs on [callbacks]. */
    fun setUsersListener(listener: UsersListener?) {
        usersListener = listener
        var current: List<ChatUser> = emptyList()
        var revision = 0L
        synchronized(lock) {
            current = publishedUsers
            revision = publishRevision
        }
        if (listener != null && current.isNotEmpty()) {
            val deliverTo = listener
            val deliver = current
            scope.launch(callbacks) {
                if (synchronized(lock) { revision == publishRevision }) deliverTo.onUsers(deliver)
            }
        }
    }

    /**
     * Binds this controller to [channelId] for [ownerUsername] and starts observing the channel.
     * Idempotent for the same channel and owner (treated as [resume]). Announcing is not implied:
     * the composer reports its text through [textChanged], and a non-empty composer is what makes
     * this reader present. A different channel or owner retires the old binding (it is left as
     * soon as it had announced itself) and starts a new one.
     */
    fun start(channelId: Long, ownerUsername: String) {
        require(channelId > 0L) { "channelId must be positive" }
        val owner = ownerUsername.trim()
        require(owner.isNotEmpty()) { "ownerUsername must not be blank" }

        var previous: Binding? = null
        var fresh = false
        val target = synchronized(lock) {
            val existing = binding
            if (existing != null && !existing.closed && existing.channelId == channelId &&
                existing.owner.equals(owner, ignoreCase = true)
            ) {
                existing.active = true
                existing.paused = false
                existing
            } else {
                previous = existing
                fresh = true
                Binding(channelId, owner).also { created ->
                    created.observedClient = runningClient.value
                    binding = created
                }
            }
        }

        if (previous != null) retire(previous!!)
        if (fresh) publishUsers(emptyList(), target, force = true)
        observeRunningClient(target)
        refreshSnapshot(target)
    }

    /**
     * The composer's text changed: non-empty means this reader is composing and should be
     * announced (subject to `hide_presence`), empty means they are not and must leave now. Every
     * non-empty call also restarts the typing idle window; content that simply sits in the box
     * stops being announced once the window passes.
     */
    fun textChanged(hasContent: Boolean) {
        val target = synchronized(lock) { binding } ?: return
        synchronized(lock) {
            if (target.closed) return
            target.hasContent = hasContent
            if (hasContent) {
                target.typingActive = true
            } else {
                target.typingActive = false
                target.idleJob?.cancel()
                target.idleJob = null
            }
        }
        if (!hasContent) {
            signal(target)
            return
        }

        restartIdleWindow(target)
        val known = synchronized(lock) {
            target.policyLoaded && target.policyOwner.equals(target.owner, ignoreCase = true)
        }
        if (!known) maybeLoadPolicy(target)
        val announceNow = synchronized(lock) {
            target.active && !target.paused && !target.denied &&
                !target.announced && !target.presentRequested &&
                target.hidePresence == false && target.observedClient === runningClient.value
        }
        if (announceNow) signal(target)
    }

    /**
     * The screen is no longer in front: stop announcing and leave the channel now. Viewing may
     * continue on the bus; [resume] re-reads the snapshot and the privacy option. A draft in the
     * composer is left untouched - pausing is not clearing.
     */
    fun pause() {
        val target = synchronized(lock) { binding } ?: return
        synchronized(lock) {
            if (target.closed) return
            target.paused = true
            target.typingActive = false
            target.idleJob?.cancel()
            target.idleJob = null
        }
        publishFor(target)
        signal(target)
    }

    /**
     * The screen came back: re-read the channel's presence and the reader's privacy option, and
     * reattach to the running bus if it was replaced meanwhile. Typing is not re-announced from a
     * pause; the next [textChanged] does that.
     */
    fun resume() {
        val target = synchronized(lock) { binding } ?: return
        synchronized(lock) {
            if (target.closed) return
            target.paused = false
            target.denied = false
            // A screen that comes back is not "still typing": the idle window was cut by the
            // pause, and only the composer's next input may announce again.
            target.typingActive = false
            // The reader may have changed hide_presence in the settings screen meanwhile.
            target.policyLoaded = false
            target.hidePresence = null
        }
        observeRunningClient(target)
        refreshSnapshot(target)
        // If this reader was announced, the now-unknown policy retracts it until the next input
        // and the fresh policy read; a resume never keeps speaking for an unverified setting.
        signal(target)
    }

    /**
     * The screen is gone: unsubscribe, clear the visible list, and ask the site to drop this
     * client from the channel (a leave only ever removes presence, so it is safe even if the
     * session moved on). The final leave is attempted in the background; the server's 60 s
     * expiry is the backstop if it cannot be delivered.
     */
    fun stop() {
        val target = synchronized(lock) {
            val current = binding
            binding = null
            current
        } ?: return
        synchronized(lock) {
            target.active = false
            target.closed = true
            target.paused = true
            target.typingActive = false
            target.idleJob?.cancel()
            target.idleJob = null
        }
        observerJob?.cancel()
        observerJob = null
        unsubscribe(target)
        signal(target)
        publishUsers(emptyList(), null, force = true)
    }

    override fun close() = stop()

    /** The last list delivered to the listener, this reader already excluded. */
    fun getUsers(): List<ChatUser> = publishedUsers

    /** Whether the site has acknowledged this client as present in the bound channel. */
    fun isPresent(): Boolean = synchronized(lock) { binding?.announced == true }

    // ------------------------------------------------------------------------------------------
    // The binding
    // ------------------------------------------------------------------------------------------

    /**
     * One (screen, channel, reader) binding and its own message-bus subscription and writer. A
     * retired binding keeps its writer alive only long enough to send its leave, so a screen that
     * switches channels cannot leave the old channel announced and a stop cannot be overtaken by
     * an in-flight present.
     */
    private inner class Binding(
        val channelId: Long,
        val owner: String,
    ) {
        /** The presence name, as `PresenceChannel` names it (`/chat-reply/{id}`). */
        val channel: String = "/chat-reply/$channelId"

        /** The message bus channel that carries this presence's deltas. */
        val busChannel: String = "/presence$channel"

        val clientId: String = newPresenceClientId()
        val wake: Channel<Unit> = Channel(Channel.CONFLATED)

        var active = true
        var paused = false
        var closed = false
        var hasContent = false
        var typingActive = false
        var denied = false

        var viewable = false
        var rawUsers: List<ChatUser> = emptyList()
        var count = 0
        var countOnly = false
        var lastSeenId = 0L

        var subscribedClient: MessageBusClient? = null
        var subscribedListener: MessageBusListener? = null
        var observedClient: MessageBusClient? = null

        var policyLoaded = false
        var policyOwner: String? = null
        var hidePresence: Boolean? = null
        var selfUserId: Long? = null
        var policyFailures = 0
        var policyRetryAtMillis = 0L

        var announced = false
        var presentRequested = false
        var lastPostNanos = 0L

        var writerJob: Job? = null
        var idleJob: Job? = null
        var snapshotJob: Job? = null
        var snapshotPending = false
        var policyJob: Job? = null
    }

    private fun restartIdleWindow(target: Binding) {
        synchronized(lock) {
            target.idleJob?.cancel()
            target.idleJob = scope.launch {
                delay(policy.typingIdleMillis)
                synchronized(lock) {
                    target.typingActive = false
                    target.idleJob = null
                }
                signal(target)
            }
        }
    }

    private fun signal(target: Binding) {
        target.wake.trySend(Unit)
        synchronized(lock) {
            if (target.writerJob?.isActive != true) {
                target.writerJob = scope.launch { writeLoop(target) }
            }
        }
    }

    private fun retire(old: Binding) {
        synchronized(lock) {
            old.active = false
            old.closed = true
            old.paused = true
            old.typingActive = false
            old.idleJob?.cancel()
            old.idleJob = null
        }
        unsubscribe(old)
        signal(old)
    }

    /**
     * The only writer for a binding: it makes the site reflect [desired]. A present is re-sent
     * every refresh interval while the composer stays active; a leave is sent once and the writer
     * then waits for the next input (or exits if the binding is closed).
     */
    private suspend fun writeLoop(target: Binding) {
        try {
            var presentFailures = 0
            var leaveFailures = 0
            while (true) {
                target.wake.receive()
                while (true) {
                    val action = synchronized(lock) { desired(target) } ?: break
                    val throttleWait = synchronized(lock) {
                        val elapsedMillis = (System.nanoTime() - target.lastPostNanos) / 1_000_000L
                        (policy.throttleMillis - elapsedMillis).coerceAtLeast(0L)
                    }
                    if (throttleWait > 0L) delay(throttleWait)
                    if (synchronized(lock) { desired(target) } != action) continue
                    synchronized(lock) {
                        target.lastPostNanos = System.nanoTime()
                        if (action == Action.Present) target.presentRequested = true
                    }

                    val accepted = try {
                        update(target, action)
                    } catch (cancelled: CancellationException) {
                        // A session that changed under the request aborts it (the CSRF helper's
                        // fence). Never replay it against the next session; the old presence, if
                        // it landed, expires with the server's 60 s timeout.
                        synchronized(lock) {
                            target.announced = false
                            target.presentRequested = false
                            target.denied = true
                        }
                        throw cancelled
                    } catch (error: Throwable) {
                        if (isSessionFatal(error)) {
                            // The session is gone or moved. Do not retry: the next account must
                            // never inherit this binding's announcement.
                            synchronized(lock) {
                                target.announced = false
                                target.presentRequested = false
                                target.denied = true
                            }
                            return
                        }
                        if (action == Action.Present) {
                            presentFailures += 1
                            delay(backoff(presentFailures))
                        } else {
                            leaveFailures += 1
                            if (leaveFailures >= MAX_LEAVE_ATTEMPTS) {
                                // The server's own 60 s expiry is the backstop; stop retrying.
                                synchronized(lock) {
                                    target.announced = false
                                    target.presentRequested = false
                                }
                                return
                            }
                            delay(backoff(leaveFailures))
                        }
                        continue
                    }

                    if (action == Action.Present) {
                        presentFailures = 0
                        if (!accepted) {
                            // The site refused this reader for this channel. Keep reading; never
                            // announce here again until a new start/resume re-reads the channel.
                            synchronized(lock) {
                                target.announced = false
                                target.presentRequested = false
                                target.denied = true
                            }
                            break
                        }
                        synchronized(lock) { target.announced = true }
                        withTimeoutOrNull(policy.refreshIntervalMillis) { target.wake.receive() }
                    } else {
                        leaveFailures = 0
                        synchronized(lock) {
                            target.announced = false
                            target.presentRequested = false
                        }
                        break
                    }
                }
                val done = synchronized(lock) {
                    target.closed && desired(target) == null
                }
                if (done) return
            }
        } finally {
            synchronized(lock) { target.writerJob = null }
            val restart = synchronized(lock) { desired(target) != null }
            if (restart) signal(target)
        }
    }

    /** What the site should currently be told, or null when it already agrees. */
    private fun desired(target: Binding): Action? {
        if (target.closed) {
            return if (target.presentRequested || target.announced) Action.Leave else null
        }
        val wantPresent = binding === target && target.active && target.viewable && !target.paused &&
            target.hasContent && target.typingActive && !target.denied &&
            target.hidePresence == false && target.observedClient === runningClient.value
        if (wantPresent) return Action.Present
        return if (target.presentRequested || target.announced) Action.Leave else null
    }

    private suspend fun update(target: Binding, action: Action): Boolean {
        val verdicts = if (action == Action.Present) {
            client.updatePresence(target.clientId, presentChannels = listOf(target.channel))
        } else {
            client.updatePresence(target.clientId, leaveChannels = listOf(target.channel))
        }
        return verdicts[target.channel] == true
    }

    // ------------------------------------------------------------------------------------------
    // The snapshot and the message-bus subscription
    // ------------------------------------------------------------------------------------------

    private fun refreshSnapshot(target: Binding) {
        synchronized(lock) {
            if (binding !== target || target.closed) return
            if (target.snapshotJob?.isActive == true) {
                target.snapshotPending = true
                return
            }
            target.snapshotJob = scope.launch { snapshotLoop(target) }
        }
    }

    private suspend fun snapshotLoop(target: Binding) {
        try {
            while (true) {
                if (!isBound(target)) return
                if (loadSnapshot(target)) return
                delay(policy.snapshotRetryMillis)
            }
        } finally {
            synchronized(lock) {
                target.snapshotJob = null
                if (target.snapshotPending) {
                    target.snapshotPending = false
                    refreshSnapshot(target)
                }
            }
        }
    }

    /**
     * One `GET /presence/get` read and the subscription it seeds. The subscription is detached
     * first, so a delta that arrives while the read is out cannot be applied on top of a snapshot
     * that already contains it: the read's `last_message_id` is the next id the subscriber expects.
     * Returns true when the read answered (including its authoritative null for an inaccessible
     * channel) and false when the read failed and should be retried.
     */
    private suspend fun loadSnapshot(target: Binding): Boolean {
        val observed = synchronized(lock) {
            if (binding !== target || target.closed) return true
            target.observedClient
        }
        unsubscribe(target)
        val state: ChatPresenceState?
        try {
            state = client.fetchPresence(listOf(target.channel))[target.channel]
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (isSessionFatal(error)) {
                synchronized(lock) {
                    target.viewable = false
                    target.rawUsers = emptyList()
                    target.count = 0
                }
                publishFor(target)
                return true
            }
            return false
        }

        synchronized(lock) {
            if (binding !== target || target.closed) return true
            // A read that went out under one session must not be drawn for the next one: the
            // users it lists were the previous reader's answer, not this reader's. The flow is
            // checked too, so a bus replacement that has not reached the observer yet still
            // fences the read out.
            if (target.observedClient !== observed || runningClient.value !== observed) return false
            target.viewable = state != null
            target.rawUsers = state?.users ?: emptyList()
            target.countOnly = state != null && state.users == null
            target.count = state?.count ?: 0
            // A channel with no messages answers null; the first message the bus gives it is 1,
            // so 0 is the position before it (the web client's `null + 1`).
            target.lastSeenId = state?.lastMessageId ?: 0L
        }
        publishFor(target)
        if (state != null) subscribe(target) else unsubscribe(target)
        maybeLoadPolicy(target)
        signal(target)
        return true
    }

    private fun subscribe(target: Binding) {
        val bus = runningClient.value ?: return
        var previousBus: MessageBusClient? = null
        var previousListener: MessageBusListener? = null
        var seed = 0L
        val listener: MessageBusListener = { message -> onPresenceMessage(target, bus, message) }
        synchronized(lock) {
            if (binding !== target || target.closed || !target.viewable) return
            // The snapshot this subscription is seeded from must belong to the same session.
            if (target.observedClient !== bus) return
            if (target.subscribedClient === bus) return
            previousBus = target.subscribedClient
            previousListener = target.subscribedListener
            target.subscribedClient = bus
            target.subscribedListener = listener
            seed = target.lastSeenId
        }
        val oldBus = previousBus
        val oldListener = previousListener
        if (oldBus != null && oldListener != null) oldBus.unsubscribe(target.busChannel, oldListener)
        bus.subscribe(target.busChannel, seed, listener)
    }

    private fun unsubscribe(target: Binding) {
        var bus: MessageBusClient? = null
        var listener: MessageBusListener? = null
        synchronized(lock) {
            bus = target.subscribedClient
            listener = target.subscribedListener
            target.subscribedClient = null
            target.subscribedListener = null
        }
        val subscribedBus = bus
        val subscribedListener = listener
        if (subscribedBus != null && subscribedListener != null) {
            subscribedBus.unsubscribe(target.busChannel, subscribedListener)
        }
    }

    private fun observeRunningClient(target: Binding) {
        if (!isBound(target)) return
        observerJob?.cancel()
        observerJob = scope.launch {
            runningClient.collect { bus -> onRunningClient(target, bus) }
        }
    }

    /**
     * A message-bus client is one signed-in session. Its arrival or departure is a session
     * boundary: the policy must be read again for the new reader, any announcement learned under
     * the old one is dropped (never replayed), and the live subscription starts over from a fresh
     * snapshot. A null client is a signed-out app: nothing is left to read with.
     */
    private fun onRunningClient(target: Binding, bus: MessageBusClient?) {
        synchronized(lock) {
            if (binding !== target || target.closed) return
            if (target.observedClient === bus) return
            target.observedClient = bus
            target.policyLoaded = false
            target.policyOwner = null
            target.hidePresence = null
            target.selfUserId = null
            target.announced = false
            target.presentRequested = false
            target.typingActive = false
            target.denied = false
            target.rawUsers = emptyList()
            target.count = 0
            target.viewable = false
        }
        unsubscribe(target)
        publishFor(target)
        if (bus != null) refreshSnapshot(target)
    }

    private fun onPresenceMessage(target: Binding, bus: MessageBusClient, message: MessageBusMessage) {
        var gap = false
        var changed = false
        synchronized(lock) {
            if (binding !== target || target.closed) return
            // A message already in flight on a replaced client's poll belongs to the previous
            // session; it must not be drawn over the new session's cleared list.
            if (target.subscribedClient !== bus) return
            if (message.messageId <= target.lastSeenId) return
            if (message.messageId != target.lastSeenId + 1) {
                // A skipped id means the client cannot know who entered or left in between; the
                // site's own client re-reads its initial state in exactly this case.
                gap = true
            } else {
                val delta = decodeChatPresenceDelta(message.data)
                if (delta == null) {
                    gap = true
                } else {
                    target.lastSeenId = message.messageId
                    applyDelta(target, delta)
                    changed = true
                }
            }
        }
        if (gap) refreshSnapshot(target) else if (changed) publishFor(target)
    }

    private fun applyDelta(target: Binding, delta: ChatPresenceDelta) {
        if (delta.enteringUsers.isEmpty() && delta.leavingUserIds.isEmpty() && delta.countDelta != null) {
            target.count = (target.count + delta.countDelta).coerceAtLeast(0)
            return
        }
        val users = target.rawUsers.toMutableList()
        for (user in delta.enteringUsers) {
            if (users.none { existing -> existing.id == user.id }) users += user
        }
        if (delta.leavingUserIds.isNotEmpty()) {
            val leaving = delta.leavingUserIds.toSet()
            users.removeAll { user -> user.id in leaving }
        }
        target.rawUsers = users
        if (!target.countOnly) target.count = users.size
    }

    // ------------------------------------------------------------------------------------------
    // The reader's own policy
    // ------------------------------------------------------------------------------------------

    private fun maybeLoadPolicy(target: Binding) {
        synchronized(lock) {
            if (binding !== target || target.closed || target.policyLoaded) return
            if (target.policyJob?.isActive == true) return
            if (System.currentTimeMillis() < target.policyRetryAtMillis) return
            target.policyJob = scope.launch { loadPolicy(target) }
        }
    }

    private suspend fun loadPolicy(target: Binding) {
        val observed = synchronized(lock) {
            if (binding !== target || target.closed) return
            target.observedClient
        }
        val result = try {
            client.fetchCurrentUserPresence()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            null
        }

        synchronized(lock) {
            if (binding !== target || target.closed) return
            if (target.observedClient !== observed || runningClient.value !== observed) {
                // The session changed while this reader's policy was being read. The answer
                // describes the old session; re-read under the new one instead of adopting it.
                target.policyJob = null
                return
            }
            target.policyJob = null
            if (result == null) {
                target.policyFailures += 1
                target.policyRetryAtMillis = System.currentTimeMillis() + backoff(target.policyFailures)
                return
            }
            target.policyFailures = 0
            target.policyRetryAtMillis = 0L
            target.policyLoaded = true
            if (!result.username.equals(target.owner, ignoreCase = true)) {
                // A different account is signed in behind a screen bound to this owner. Reading
                // the channel is harmless, but announcing would be this reader speaking for
                // someone else, so the binding is denied for good.
                target.policyOwner = null
                target.hidePresence = null
                target.selfUserId = null
                target.denied = true
                target.announced = false
                target.presentRequested = false
            } else {
                target.policyOwner = result.username
                target.hidePresence = result.hidePresence
                target.selfUserId = result.userId
            }
        }
        publishFor(target)
        maybeLoadPolicy(target)
        // The writer recomputes: it announces when the policy allows it and retracts a presence
        // the reader has since asked to hide.
        signal(target)
    }

    // ------------------------------------------------------------------------------------------
    // Visible state
    // ------------------------------------------------------------------------------------------

    private fun publishFor(target: Binding) {
        var listener: UsersListener? = null
        var users: List<ChatUser>? = null
        var revision = 0L
        synchronized(lock) {
            if (binding !== target) return
            val visible = visibleUsers(target)
            if (visible == publishedUsers) return
            publishedUsers = visible
            publishRevision += 1
            revision = publishRevision
            listener = usersListener
            users = visible
        }
        val deliverTo = listener ?: return
        val deliver = users ?: return
        scope.launch(callbacks) {
            // A newer list (a delta, a resnapshot) or a new binding makes this one stale; drop it
            // so an answer that arrived late cannot repaint the screen with older presence.
            if (synchronized(lock) { binding === target && revision == publishRevision }) {
                deliverTo.onUsers(deliver)
            }
        }
    }

    /**
     * The site's list without this reader. The username bound at [start] is the first guard; the
     * id and username from the reader's own session are the second, so a renamed account or a
     * second tab of the same identity is still excluded.
     */
    private fun visibleUsers(target: Binding): List<ChatUser> {
        if (target.paused || !target.viewable) return emptyList()
        val selfId = target.selfUserId
        val selfNames = listOfNotNull(target.owner, target.policyOwner)
        return target.rawUsers.filter { user ->
            (selfId == null || user.id != selfId) &&
                selfNames.none { name -> user.username.equals(name, ignoreCase = true) }
        }
    }

    private fun publishUsers(users: List<ChatUser>, forBinding: Binding?, force: Boolean = false) {
        var listener: UsersListener? = null
        var revision = 0L
        synchronized(lock) {
            if (forBinding != null && binding !== forBinding) return
            if (!force && users == publishedUsers) return
            publishedUsers = users
            publishRevision += 1
            revision = publishRevision
            listener = usersListener
        }
        val deliverTo = listener ?: return
        scope.launch(callbacks) {
            val stillCurrent = synchronized(lock) {
                revision == publishRevision && (forBinding == null || binding === forBinding)
            }
            if (stillCurrent) deliverTo.onUsers(users)
        }
    }

    private fun isBound(target: Binding): Boolean =
        synchronized(lock) { binding === target && !target.closed }

    private fun backoff(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        return (policy.retryBaseMillis shl exponent).coerceAtMost(policy.retryMaxMillis)
    }

    private fun isSessionFatal(error: Throwable): Boolean =
        error is LoginRequiredException ||
            error is SessionLoggedOutException ||
            error is SessionRecoveryRequiredException ||
            error is SessionChangedException ||
            (error is DiscourseRefusalException && error.status == 401)

    private companion object {
        /** The web client queues a failed leave and gives up on it; presence expires server-side. */
        const val MAX_LEAVE_ATTEMPTS = 3

        /**
         * The process-lived scope a screen gets when it does not bring its own. The screen's own
         * scope may be cancelled before the final leave has gone out, so the default outlives any
         * one screen; a controller still cancels every job it owns on [stop].
         */
        val hostScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    }
}

/** A per-binding random id, 32 lowercase hex characters like the site's own presence client ids. */
private fun newPresenceClientId(): String = UUID.randomUUID().toString().replace("-", "")
