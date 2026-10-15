package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.RecyclerListView

/**
 * The two screens under an outage, end to end: the real client, the real fragment and the stock
 * cells, with the local site refusing to answer.
 *
 * <p>Each screen is read once while the site is up (so its answer is in the cache), then the site
 * is broken and the screen is read again. The failure modes pinned here:
 *  * an outage on the topic list leaves the reader with its cached rows and an explicit retry, not
 *    an empty page;
 *  * the same for a topic, whose cached floors are drawn with the retry said in passing;
 *  * a write is not sent while those cached rows are on screen, because the permissions behind them
 *    are the site's answer, and the site has not answered - and a reply or an edit the reader
 *    pressed there keeps its text and its strip, since the strip's close button is the only way
 *    back out of a reply or an edit;
 *  * a refusal (403) is not answered from the cache - the site was reached and said no;
 *  * the retry replaces the cached rows with the site's new answer and clears the screen's
 *    persistent offline marker (the bar's / the capsule's own line), not merely asks again.</p>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class OfflineReadingTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
        // A Bulletin left by an earlier test in the shared sandbox must not be mistaken for this
        // screen's own offline hint.
        runCatching { Bulletin.hideVisible() }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        // The session is established before the shell boots: the cached reads belong to an
        // authenticated reader, and the shell's own reads must be that reader's to be cached.
        kotlinx.coroutines.runBlocking {
            NativeDo.container(harness.context).sessionCoordinator.completeLoginFromWebViewStaged("baba")
        }
    }

    @After
    fun tearDown() {
        runCatching { Bulletin.hideVisible() }
        site.stop()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    /**
     * The list is read while the site is up, then the site stops answering: the next read draws the
     * cached rows and a hint whose own button asks the site again.
     */
    @Test
    fun theTopicListFallsBackToTheCacheAndItsRetryReadsTheSiteAgain() {
        harness.bootApp()
        harness.await("the live feed") { rowCount() == 2 }
        harness.drawApp()

        runCatching { Bulletin.hideVisible() }
        site.serveResponse("latest.json") { disconnect() }
        invokeNoArg(harness.visibleFragment() as TopicListFragment, "refreshCurrentFeed")

        // The model in front is the cache's - and the bar keeps saying so, not just the Bulletin
        // that says it once.
        val bulletin = awaitOfflineHint()
        assertTrue("the feed on screen is cached", homeFeedFromCache() == true)
        assertEquals("the cached feed keeps the rows it was read from", 2, rowCount())
        assertEquals("the persistent offline marker", offlineLabel(), homeSubtitle())
        val retry = retryButtonOf(bulletin)
        assertNotNull("the outage is said in passing, with its own retry", retry)
        harness.idleClock(250, maxTasks = 8)
        harness.renderComposite(".amp/in/artifacts/nativedo/home-offline-cached.png", maxTasks = 8)
        val host = harness.topFragment() as io.github.baba983.nativedo.app.NativeDoMainTabsActivity
        val tabs = host.javaClass.getDeclaredField("tabsView").apply { isAccessible = true }.get(host) as View
        val retryBounds = android.graphics.Rect()
        val tabBounds = android.graphics.Rect()
        assertTrue("the retry is visible", retry!!.getGlobalVisibleRect(retryBounds))
        assertTrue("the main tabs are visible", tabs.getGlobalVisibleRect(tabBounds))
        assertTrue("the tabs must not cover the retry action", retryBounds.bottom <= tabBounds.top)
        val feed = harness.visibleFragment() as TopicListFragment
        val compose = feed.javaClass.getDeclaredField("startTopicButton").apply { isAccessible = true }.get(feed) as View
        val composeBounds = android.graphics.Rect()
        if (compose.getGlobalVisibleRect(composeBounds)) {
            assertFalse("the floating compose button must not cover retry", android.graphics.Rect.intersects(retryBounds, composeBounds))
        }

        // The site answers again with a *different* first title: the retry has to replace the cached
        // rows with the live answer and clear the marker, not merely re-ask.
        val liveTitle = "Welcome to NativeDo Android (live)"
        val gate = java.util.concurrent.CountDownLatch(1)
        val requested = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("latest.json") {
            requested.countDown()
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            json(harness.fixture("latest-topics.json").replace(ORIGINAL_TITLE, liveTitle))
        }
        retry!!.performClick()
        harness.await("the live retry in flight") { requested.count == 0L }
        assertTrue("cached permissions remain read-only until a live answer arrives", homeFeedFromCache() == true)
        assertEquals("retrying does not call old content fresh", offlineLabel(), homeSubtitle())
        gate.countDown()
        harness.await("the live answer to replace the cached rows") {
            homeFeedFromCache() == false && homeFeedTitles().contains(liveTitle)
        }
        assertFalse("the cached first row is gone", homeFeedTitles().contains(ORIGINAL_TITLE))
        assertNull("the offline marker is cleared", homeSubtitle())

        // And the same state on the night theme: the marker and the hint are drawn from the theme's
        // own colours, so the cached screen is retained next to its day render.
        runCatching { Bulletin.hideVisible() }
        site.serveResponse("latest.json") { disconnect() }
        invokeNoArg(harness.visibleFragment() as TopicListFragment, "refreshCurrentFeed")
        harness.await("the cached feed again") { homeFeedFromCache() == true }
        switchTheme(toDark = true)
        harness.drawApp()
        harness.renderComposite(".amp/in/artifacts/nativedo/home-offline-cached-night.png")
        switchTheme(toDark = false)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A topic the reader opened while the site was up is drawn from the cache while the site is
     * away: the floors are drawn, the hint says so in passing, and its own button reads the topic
     * live once the site answers again.
     */
    @Test
    fun theTopicFallsBackToTheCacheAndItsRetryReadsTheSiteAgain() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, ORIGINAL_TITLE))
        harness.await("the live topic") { rowCount() == 1 }
        harness.drawApp()

        runCatching { Bulletin.hideVisible() }
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }
        val topic = harness.visibleFragment() as TopicFragment
        invokeNoArg(topic, "runRetry")

        // The screen's own state says the rows are the cache's, and the capsule keeps saying so.
        val bulletin = awaitOfflineHint()
        assertTrue("the topic on screen is cached", topicShowingCached(topic))
        assertTrue("the persistent offline marker", topicCapsuleSubtitle(topic).contains(offlineLabel()))
        val retry = retryButtonOf(bulletin)
        assertNotNull("the cached topic offers the retry", retry)
        harness.idleClock(250, maxTasks = 8)
        harness.renderComposite(".amp/in/artifacts/nativedo/topic-offline-cached.png", maxTasks = 8)

        // The site answers again with a *different* title: the retry has to replace the cached topic
        // and clear the marker, not merely re-read.
        val liveTitle = "Welcome to NativeDo Android (live)"
        site.serveResponse("t/$TOPIC_ID.json") {
            json(harness.fixture("topic-detail.json").replace(ORIGINAL_TITLE, liveTitle))
        }
        retry!!.performClick()
        harness.await("the live topic to replace the cached one") {
            !topicShowingCached(topic) && topicTitle(topic) == liveTitle
        }
        assertFalse(
            "the capsule no longer claims the rows are the cache's",
            topicCapsuleSubtitle(topic).contains(offlineLabel()),
        )

        // And the same cached topic on the night theme, retained next to the day render.
        runCatching { Bulletin.hideVisible() }
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }
        invokeNoArg(topic, "runRetry")
        harness.await("the cached topic again") { topicShowingCached(topic) }
        switchTheme(toDark = true)
        harness.drawApp()
        harness.renderComposite(".amp/in/artifacts/nativedo/topic-offline-cached-night.png")
        switchTheme(toDark = false)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A write attempted while the cached topic is on screen is refused: the permissions behind the
     * cached rows are the site's answer, and the site has not answered. Not even one request leaves
     * for the bookmark.
     */
    @Test
    fun aWriteIsSuppressedWhileTheCachedTopicIsShown() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Welcome to NativeDo Android"))
        harness.await("the live topic") { rowCount() == 1 }

        runCatching { Bulletin.hideVisible() }
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }
        val topic = harness.visibleFragment() as TopicFragment
        invokeNoArg(topic, "runRetry")
        harness.await("the cached topic") { Bulletin.getVisibleBulletin() != null }

        val toggle = TopicFragment::class.java.getDeclaredMethod("toggleBookmark", Int::class.javaPrimitiveType)
        toggle.isAccessible = true
        toggle.invoke(topic, 0)
        harness.idleClock(200)
        assertTrue(
            "no write is sent while the cached rows are on screen",
            site.recordedRequests.none { it.startsWith("POST ") && it.contains("bookmarks.json") },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An edit sent while the cached topic is on screen: the write is refused, and the reader keeps
     * the edit, the strip that labels it and the way out of edit mode.
     *
     * <p>Upstream's enter view clears its own field and takes the reply/edit strip down at the start
     * of <em>every</em> send (ChatActivityEnterView 7396-7412), so a write that did not land has to
     * put both back. The edit path is the one that cannot be recovered by hand: the strip's close
     * button is the only way out of edit mode (`cancelEdit` is what `exitEditMode` is reachable
     * from, and `startEdit` refuses while one is open), so losing the strip traps the reader in an
     * edit of a post they can no longer read - and losing the text throws their work away.</p>
     */
    @Test
    fun anEditSuppressedWhileTheCachedTopicIsShownKeepsTheTextAndTheStrip() {
        val raw = "Hello from **Android**."
        val pressed = "a pressed edit the site never takes"
        site.serveTopic(TOPIC_ID) { post ->
            post.put("can_edit", true)
            post.put("can_delete", true)
            post.put("raw", raw)
        }
        site.serveJson(
            "posts/$POST_ID.json",
            """{"id":$POST_ID,"post_number":1,"reply_to_post_number":null,"reply_count":0,
                "username":"baba","name":"Baba","avatar_template":"/user_avatar/linux.do/baba/{size}/1.png",
                "created_at":"2024-01-01T10:00:00.000Z","updated_at":"2024-01-01T10:05:00.000Z","version":2,
                "yours":true,"can_edit":true,"can_delete":true,"can_recover":false,"deleted_at":null,
                "bookmarked":false,"cooked":"<p>Hello from <strong>Android</strong>.</p>",
                "raw":"Hello from **Android**.","polls":[]}""",
        )
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, ORIGINAL_TITLE))
        harness.await("the live topic") { rowCount() == 1 }
        harness.await("the first row to be drawn") { harness.layoutApp(); laidOutCells().isNotEmpty() }

        // The reader opens the editor while the site still answers, and writes.
        openMenuItem("编辑")
        harness.await("the editor to open on the stored markdown") {
            composer().getFieldText()?.toString() == raw
        }
        composer().setFieldText(pressed)
        harness.idle()
        harness.layoutApp()
        harness.await("the edit's strip to be up") { composer().isTopViewVisible() }

        // The site goes away and the cached window takes over.
        runCatching { Bulletin.hideVisible() }
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }
        val topic = harness.visibleFragment() as TopicFragment
        invokeNoArg(topic, "runRetry")
        harness.await("the cached topic") {
            topicShowingCached(topic) && topicCapsuleSubtitle(topic).contains(offlineLabel())
        }
        harness.idleClock(400)
        assertEquals("the cached window is still the post being edited", pressed,
            composer().getFieldText()?.toString())

        composer().sendMessage()
        harness.idleClock(600)
        assertTrue(
            "no write is sent while the cached rows are on screen",
            site.recordedRequests.none { it.startsWith("PUT ") },
        )
        assertEquals(
            "the reader's edit is not thrown away",
            pressed,
            composer().getFieldText()?.toString(),
        )
        harness.await("the edit's strip to still be up") { composer().isTopViewVisible() }
        val close = findCloseButton(composer()) ?: throw AssertionError("the edit strip has no close button")
        close.performClick()
        harness.idleClock(400)
        harness.layoutApp()
        assertFalse("and the reader can still leave the edit", composer().isTopViewVisible())
        harness.assertNoTelegramSideEffects()
    }

    /** A live appended chunk must not make the older cached rows' permissions fresh. */
    @Test
    fun aLiveStreamPageKeepsAnExistingCachedWindowReadOnly() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, ORIGINAL_TITLE))
        harness.await("the live topic") { rowCount() == 1 }
        val topic = harness.visibleFragment() as TopicFragment
        site.serveResponse("t/$TOPIC_ID.json") { disconnect() }
        invokeNoArg(topic, "runRetry")
        harness.await("the cached topic") { topicShowingCached(topic) }

        // Connectivity returns for the next chunk, but floor one's body and permissions still came
        // from the old topic payload. Appending those live floors is not a refresh of the window.
        site.serveJson("t/$TOPIC_ID/posts.json", harness.fixture("topic-posts.json"))
        invokeNoArg(topic, "loadNextStreamPage")
        harness.await("the live chunk to join the cached floor") { rowCount() == 3 }
        assertTrue("the existing cached floor keeps the window read-only", topicShowingCached(topic))
        val bookmark = TopicFragment::class.java.getDeclaredMethod("toggleBookmark", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        bookmark.invoke(topic, 0)
        harness.idleClock(200)
        assertTrue("cached permissions cannot authorize a write", site.recordedRequests.none {
            it.startsWith("POST ") && it.contains("bookmarks.json")
        })

        // The opposite mix: a live head followed by the chunk just cached above, now under an
        // outage. The persistent header must identify that cached content too, after the hint fades.
        site.serveResponse("t/$TOPIC_ID.json") { json(harness.fixture("topic-detail.json")) }
        site.serveResponse("t/$TOPIC_ID/posts.json") { disconnect() }
        invokeNoArg(topic, "runRetry")
        harness.await("the cached chunk to join a live head") { rowCount() == 3 && topicShowingCached(topic) }
        assertTrue("cached incremental rows also keep the offline marker", topicCapsuleSubtitle(topic).contains(offlineLabel()))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A topic the reader read while the site was up, then the site refused (403): the cached payload
     * is not drawn. The site was reached and said no, and serving the last answer would contradict
     * it. The screen says so instead.
     */
    @Test
    fun aRefusedTopicIsNotDrawnFromTheCache() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Welcome to NativeDo Android"))
        harness.await("the live topic, now cached") { rowCount() == 1 }

        site.serveResponse("t/$TOPIC_ID.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["forbidden"]}""")
        }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Welcome to NativeDo Android"))
        harness.await("the refusal to be shown") { failureVisible() }

        assertEquals("the refused topic is not drawn from the cache", 0, rowCount())
        harness.renderApp("topic-refusal-not-cached")
        harness.assertNoTelegramSideEffects()
    }

    /** A first read without a cache must offer recovery without exposing transport diagnostics. */
    @Test
    fun aFirstReadFailureHasReadableRecoveryAndRetryRestoresTheTopic() {
        val topicId = 901L
        site.serveResponse("t/$topicId.json") { disconnect() }
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(topicId, "首次加载失败"))
        harness.await("the first-read failure") { failureVisible() }
        harness.idleClock(200)
        val failure = TopicFragment::class.java.getDeclaredField("failureView")
            .apply { isAccessible = true }.get(harness.visibleFragment()) as View

        fun readableText(view: View): String {
            val info = view.createAccessibilityNodeInfo()
            val own = listOfNotNull(info.text, info.contentDescription).joinToString(" ")
            info.recycle()
            val children = (view as? android.view.ViewGroup)?.let { group ->
                (0 until group.childCount).joinToString(" ") { readableText(group.getChildAt(it)) }
            }.orEmpty()
            return "$own $children".trim()
        }
        val text = readableText(failure)
        println("first-read failure copy: $text")
        assertTrue("the reader has a localized recovery action: $text", text.contains("重试"))
        assertTrue("the recovery surface is a compact chat notice, not a full-screen tap target",
            failure.height < harness.height / 3)
        assertFalse("raw transport diagnostics must not be rendered: $text",
            text.contains("unexpected end", ignoreCase = true) || text.contains("http://") ||
                text.contains("connection closed", ignoreCase = true))
        harness.renderComposite(".amp/in/artifacts/image-lifecycle/topic-first-read-failed.png")
        switchTheme(toDark = true)
        harness.renderComposite(".amp/in/artifacts/image-lifecycle/topic-first-read-failed-night.png")
        switchTheme(toDark = false)

        site.serveResponse("t/$topicId.json") { json(harness.fixture("topic-detail.json")) }
        failure.performClick()
        harness.await("retry restores the actual topic") { rowCount() == 1 && !failureVisible() }
        val cells = contentListOf(harness.activity)!!
        assertTrue("retry restores readable post content", (0 until cells.childCount)
            .map { cells.getChildAt(it) }.filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
            .any { it.messageObject?.richLayout?.joinedText?.isNotBlank() == true })
        harness.renderComposite(".amp/in/artifacts/image-lifecycle/topic-first-read-recovered.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A real transport drop: the site reads the request and closes the connection before writing a
     * response, so the client sees a transport `IOException` with no answer - the outage the cache
     * exists for.
     *
     * <p>`SocketPolicy.DISCONNECT_AT_START` is deliberately not used here: a custom dispatcher never
     * returns it from `peek()`, so the accept loop serves the connection and the response written is
     * the default empty 200. That is a site that answered with an undecodable body, not an outage,
     * and it never reaches the cache fallback.</p>
     */
    private fun disconnect(): MockResponse =
        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    private fun json(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun invokeNoArg(target: Any, name: String) {
        val method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(target)
    }

    /** Observe the transient hint before a broad looper drain advances through its entire lifetime. */
    private fun awaitOfflineHint(): Bulletin {
        repeat(10_000) {
            harness.idle(1)
            val bulletin = Bulletin.getVisibleBulletin()
            val layout = bulletin?.layout as? Bulletin.LottieLayout
            if (bulletin != null && layout?.textView?.text?.toString()?.contains("离线中") == true) return bulletin
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
        }
        throw AssertionError("the offline retry hint never appeared")
    }

    /** The hint's own explicit action, when the Bulletin carries a button. */
    private fun retryButtonOf(bulletin: Bulletin): View? {
        val layout = bulletin.layout
        if (layout !is Bulletin.ButtonLayout) return null
        return layout.button
    }

    private fun failureVisible(): Boolean {
        val fragment = harness.visibleFragment() as? TopicFragment ?: return false
        val field = TopicFragment::class.java.getDeclaredField("failureView").apply { isAccessible = true }
        val view = field.get(fragment) as? View ?: return false
        return view.visibility == View.VISIBLE
    }

    private fun invokeResult(target: Any, name: String): Any? {
        val method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        return method.invoke(target)
    }

    /**
     * The feed the visible topic list is showing, reached the way the screen reaches it
     * (`currentPage().feed`): the model itself, not a render or a request count.
     */
    private fun homeFeed(): Any? {
        val fragment = harness.visibleFragment() as? TopicListFragment ?: return null
        val page = invokeResult(fragment, "currentPage") ?: return null
        return page.javaClass.getDeclaredField("feed").apply { isAccessible = true }.get(page)
    }

    private fun homeFeedFromCache(): Boolean? =
        homeFeed()?.let { feed ->
            feed.javaClass.getDeclaredField("fromCache").apply { isAccessible = true }.getBoolean(feed)
        }

    private fun homeFeedTitles(): List<String> {
        val feed = homeFeed() ?: return emptyList()
        val field = feed.javaClass.getDeclaredField("topics").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val topics = field.get(feed) as List<*>
        return topics.mapNotNull { (it as? io.github.baba983.nativedo.core.model.TopicSummary)?.title }
    }

    /** The bar's own persistent offline marker (`offline_indicator`), or null. */
    private fun homeSubtitle(): String? {
        val fragment = harness.visibleFragment() ?: return null
        var type: Class<*>? = fragment.javaClass
        while (type != null) {
            val field = runCatching { type.getDeclaredField("actionBar") }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return (field.get(fragment) as? org.telegram.ui.ActionBar.ActionBar)?.subtitle
            }
            type = type.superclass
        }
        return null
    }

    private fun topicShowingCached(fragment: TopicFragment): Boolean =
        TopicFragment::class.java.getDeclaredField("showingCachedContent").apply { isAccessible = true }
            .getBoolean(fragment)

    private fun topicTitle(fragment: TopicFragment): String? {
        val detail = TopicFragment::class.java.getDeclaredField("topicDetail").apply { isAccessible = true }
            .get(fragment) as? io.github.baba983.nativedo.core.model.TopicDetail
        return detail?.title
    }

    /** The topic capsule's own subtitle line, where the topic screen's offline marker lives. */
    private fun topicCapsuleSubtitle(fragment: TopicFragment): String {
        val capsule = TopicFragment::class.java.getDeclaredField("avatarContainer").apply { isAccessible = true }
            .get(fragment) as? org.telegram.ui.Components.ChatAvatarContainer ?: return ""
        val text = capsule.subtitleTextView as? org.telegram.ui.ActionBar.SimpleTextView ?: return ""
        return text.text?.toString().orEmpty()
    }

    private fun offlineLabel(): String =
        ApplicationLoader.applicationContext.getString(io.github.baba983.nativedo.R.string.offline_indicator)

    /**
     * The day/night switch the home and topic screens both observe: posts the notification the
     * appearance control posts and waits for the theme to land, so a cached state can be retained on
     * the night theme too.
     */
    private fun switchTheme(toDark: Boolean) {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
            org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
            if (toDark) org.telegram.ui.ActionBar.Theme.getTheme("Dark Blue")
            else org.telegram.ui.ActionBar.Theme.getTheme("Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                org.telegram.ui.ActionBar.Theme.isCurrentThemeDark() == toDark
            }
            harness.layoutApp()
        } finally {
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
    }

    /** The visible topic's own list is empty: nothing from the cache was drawn. */
    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    /** The visible topic's composer, reached the way the screen reaches it. */
    private fun composer(): org.telegram.ui.Components.ChatActivityEnterView {
        val view = harness.visibleFragment()?.fragmentView ?: error("the screen has no view")
        return viewsUnder(view).filterIsInstance<org.telegram.ui.Components.ChatActivityEnterView>().lastOrNull()
            ?: error("the screen has no composer")
    }

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    /** The first row's post menu, opened the way the reader's own long press opens it. */
    private fun openMenuItem(label: String) {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), label)
        harness.idle()
    }

    /** The strip's own close button, whichever action it is labelled for. */
    private fun findCloseButton(view: View): View? {
        if (view.contentDescription == "取消回复" || view.contentDescription == "取消编辑") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findCloseButton(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return found
    }

    private companion object {
        const val TOPIC_ID = 101L
        const val POST_ID = 1001L
        const val ORIGINAL_TITLE = "Welcome to NativeDo Android"
    }
}
