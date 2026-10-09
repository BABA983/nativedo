package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.core.storage.DiscourseReadCache
import io.github.baba983.nativedo.core.storage.SharedPreferencesDiscourseReadCachePersistence
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The cache's identity rules, driven through the real client against a local site.
 *
 * <p>The failure modes pinned here, none of which a render could show:
 *  * the reader's own reads are served from the cache when the site cannot be reached;
 *  * the same reader's cache survives a session generation change (the key is the persisted
 *    identity, site + username + route, never the in-memory generation) - so it is there after a
 *    restart or a re-login;
 *  * a read whose session moved on *while it was in flight* is not answered from the cache: the
 *    generation is the per-flight fence;
 *  * another account is never served the first reader's cached reads;
 *  * an explicit logout drops the cached reads with the session;
 *  * a different window or page is a different entry, not a stale hit;
 *  * a refusal from the site is never answered from the cache - the site was reached and said no;
 *  * a 200 whose body cannot be decoded is never answered from the cache either - the site was
 *    reached, and its own (unreadable) answer is what stands, not the last one.</p>
 */
@RunWith(RobolectricTestRunner::class)
internal class OfflineCacheIdentityTest {

    private val site = FakeDiscourseSite()
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun theReadersOwnFeedComesFromTheCacheWhenTheSiteCannotBeReached() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        val warm = runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }
        assertFalse("a live read is not a cached one", warm.fromCache)
        assertEquals("the live read carries the feed", 2, warm.topics.size)

        site.serveResponse("latest.json") { disconnect() }

        val served = runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }
        assertTrue("an outage falls back to the cache", served.fromCache)
        assertEquals("the cached feed is the one that was read", 2, served.topics.size)
    }

    @Test
    fun theSameReadersCacheSurvivesASessionGenerationChange() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }

        // The session moves on (a re-login as the same reader bumps the generation). The key does
        // not carry the generation, so the read is still this reader's and stays reusable.
        stageLogin(container)
        site.serveResponse("latest.json") { disconnect() }

        val served = runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }
        assertTrue("the same reader reuses the persisted cache across a generation change", served.fromCache)
        assertEquals(2, served.topics.size)
    }

    @Test
    fun aReadWhoseSessionMovedOnMidFlightIsNotAnsweredFromTheCache() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }

        // The site is reached, but the session is bumped while the request is still out: the cached
        // answer belongs to the generation the read started under, so the fence refuses it and the
        // outage is what surfaces.
        site.serveResponse("latest.json") {
            runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ChallengeRequired) }
            disconnect()
        }

        val failure = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("a mid-flight session change keeps the outage", failure.isFailure)
    }

    @Test
    fun anotherReaderIsNotServedTheFirstReadersCachedFeed() {
        val baba = site.authenticatedContainer(context)
        stageLogin(baba)
        runBlocking { baba.discourseApiClient.fetchFeed(null, null, 0) }
        site.serveResponse("latest.json") { disconnect() }

        // A different reader on the same site and the same device: the persisted identity is alice's.
        val alice = site.authenticatedContainer(context)
        alice.sessionStore.save(
            SessionSnapshot(
                username = "alice",
                displayName = "Alice",
                avatarTemplate = "",
                lastValidatedAt = System.currentTimeMillis(),
            ),
        )
        stageLogin(alice, username = "alice")

        val failure = runCatching { runBlocking { alice.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("another reader gets no cache fallback", failure.isFailure)
        assertTrue(
            "the outage is what alice sees, not someone else's rows",
            failure.exceptionOrNull() is java.io.IOException,
        )
    }

    @Test
    fun anExplicitLogoutDropsTheCachedReads() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }

        runBlocking { container.sessionCoordinator.onLogout().join() }

        // The same reader signs back in. If the logout had not dropped the cache, this reader's own
        // read would still be reusable; the point is that it is gone.
        site.seedSharedJarCookie("_t=session-token; Path=/; HttpOnly")
        stageLogin(container)
        site.serveResponse("latest.json") { disconnect() }

        val failure = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("a logged-out reader's cache does not come back with a new login", failure.isFailure)
    }

    /** A prepared write may reach the cache after logout cleared it; its ownership must be checked at commit. */
    @Test
    fun aPreparedReadCannotRepopulateThePersistentCacheAfterLogout() {
        val preferences = context.getSharedPreferences("cache-logout-race", android.content.Context.MODE_PRIVATE)
        val persistence = SharedPreferencesDiscourseReadCachePersistence(preferences)
        val cache = DiscourseReadCache(persistence)
        var session = 1
        val preparedSession = session
        cache.write("reader|latest", "previous answer") { session == preparedSession }

        // The response has already been prepared by the old request when logout changes ownership
        // and clears the store. The late commit must not recreate data on disk for a later login.
        session = 2
        cache.clear()
        cache.write("reader|latest", "late old answer") { session == preparedSession }

        assertEquals("logout stays cleared after a restart", null, DiscourseReadCache(persistence).read("reader|latest"))
    }

    /** Chinese content exceeds a byte cap sooner than ASCII; counting characters permits oversized payloads. */
    @Test
    fun thePersistedPayloadBoundCountsUtf8BytesInsteadOfCharacters() {
        val preferences = context.getSharedPreferences("cache-size-bound", android.content.Context.MODE_PRIVATE)
        val persistence = SharedPreferencesDiscourseReadCachePersistence(preferences)
        val cache = DiscourseReadCache(persistence, maxBytes = 6)
        cache.write("older", "abc") { true }
        cache.write("newer", "中文") { true }

        val restarted = DiscourseReadCache(persistence, maxBytes = 6)
        assertEquals("the eldest payload is evicted, not left above the cap", null, restarted.read("older"))
        assertEquals("the most recent payload fits exactly", "中文", restarted.read("newer"))
    }

    @Test
    fun theCacheKeyDistinguishesWindowAndPage() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        runBlocking {
            container.discourseApiClient.fetchFeed(null, null, 0)
            container.discourseApiClient.fetchTopic(TOPIC_ID, null)
        }

        site.serveResponse("latest.json") { disconnect() }
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }

        // A page the reader never read, and a window of a topic they read but never at that floor:
        // neither is a hit, so neither is invented.
        val pageOne = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 1) } }
        val otherWindow = runCatching { runBlocking { container.discourseApiClient.fetchTopic(TOPIC_ID, 5) } }
        assertTrue("an unread page is not served from the cache", pageOne.isFailure)
        assertTrue("an unread window is not served from the cache", otherWindow.isFailure)

        // The exact reads are still there.
        val feed = runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }
        val topic = runBlocking { container.discourseApiClient.fetchTopic(TOPIC_ID, null) }
        assertTrue("page zero falls back", feed.fromCache)
        assertTrue("the head window falls back", topic.fromCache)
    }

    @Test
    fun theCachedReadsSurviveARecreatedContainer() {
        val first = site.authenticatedContainer(context)
        stageLogin(first)
        runBlocking { first.discourseApiClient.fetchFeed(null, null, 0) }

        // The container is rebuilt (a restart): a fresh cache store over the same persisted file.
        val restarted = site.authenticatedContainer(context)
        stageLogin(restarted)
        site.serveResponse("latest.json") { disconnect() }

        val served = runBlocking { restarted.discourseApiClient.fetchFeed(null, null, 0) }
        assertTrue("the persisted read is there after the container is recreated", served.fromCache)
        assertEquals(2, served.topics.size)
    }

    @Test
    fun aRefusalIsNotAnsweredFromTheCache() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }

        site.serveResponse("latest.json") { refuse() }

        val failure = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("a refusal surfaces as a failure", failure.isFailure)
        assertTrue(
            "and it is typed as a refusal, not answered with cached rows",
            failure.exceptionOrNull() is DiscourseRefusalException,
        )
    }

    /**
     * The site is reached and answers 200, but the body is not the JSON the read decodes. Serving
     * the last answer would contradict a site that answered; the decode failure has to surface, and
     * this is exactly what a `DISCONNECT_AT_START` response used to look like from the client's side
     * (an empty 200), which is why it is not mistaken for an outage.
     */
    @Test
    fun a200WhoseBodyCannotBeDecodedIsNeverAnsweredFromTheCache() {
        val container = site.authenticatedContainer(context)
        stageLogin(container)
        val warm = runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) }
        assertFalse("a live read is not a cached one", warm.fromCache)

        // An empty body, then a truncated one: both are 200s from a site that was reached.
        site.serveResponse("latest.json") { json("") }
        val empty = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("an empty 200 surfaces as a failure", empty.isFailure)

        site.serveResponse("latest.json") { json("""{"topic_list":""") }
        val truncated = runCatching { runBlocking { container.discourseApiClient.fetchFeed(null, null, 0) } }
        assertTrue("a truncated 200 surfaces as a failure", truncated.isFailure)

        // A transport outage would have been answered with the cached rows (the read would have
        // succeeded); an undecodable answer is not one, so the decode failure is what comes back.
        assertFalse(
            "a decode failure is not the transport outage the cache answers for",
            empty.exceptionOrNull() is java.io.IOException ||
                truncated.exceptionOrNull() is java.io.IOException,
        )
    }

    /** Puts the container into an authenticated state, the way a completed login would. */
    private fun stageLogin(container: AppContainer, username: String = "baba") {
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged(username) }
    }

    /**
     * A real transport drop: the site reads the request and closes the connection before writing a
     * response, so the client sees a transport [java.io.IOException] with no answer - the outage the
     * cache exists for.
     *
     * <p>[SocketPolicy.DISCONNECT_AT_START] is deliberately not used: a custom dispatcher never
     * returns it from `peek()`, so the accept loop serves the connection and the response written is
     * the default empty 200 - a site that answered, which is not an outage at all.</p>
     */
    private fun disconnect(): MockResponse =
        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    private fun json(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun refuse(): MockResponse =
        MockResponse()
            .setResponseCode(403)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"errors":["forbidden"]}""")

    private companion object {
        const val TOPIC_ID = 101L
    }
}
