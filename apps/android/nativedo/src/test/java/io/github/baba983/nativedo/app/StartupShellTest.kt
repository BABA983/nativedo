package io.github.baba983.nativedo.app

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.core.session.SessionExpiredReason
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.MainTabsLayout
import org.telegram.ui.ViewPagerActivity
import java.util.concurrent.CountDownLatch

/**
 * The launch the reader actually sees while the session is still a network round-trip away: the real
 * tab shell and its home are on screen at once, the home shows its shimmer and its cached category
 * tabs, and nothing that needs the session - the feed, the categories, the tags, or any write - has
 * been sent yet. When the session answers the same root and the same home carry on; a link that
 * arrived meanwhile opens once, on top of them.
 *
 * <p>The failures this pins. The shell used to wait behind an invisible progress line until the
 * session answered, so a cold start was a blank screen for as long as the round-trip took. The home
 * used to read the feed the moment it was built, which sent the previous account's cookies before
 * the identity behind them had been confirmed. And an outage used to look like a logout: the reader
 * was handed the login form with no way to just try again.</p>
 *
 * <p>The session is held with a releasable latch, released in a finally block so a failed assertion
 * cannot leave the site thread blocked.</p>
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
internal class StartupShellTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serve(1L, "topic-detail.json")
    }

    @After
    fun tearDown() {
        site.stop()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    /**
     * The session's own answer is held open: the shell must already be showing the real tab shell
     * and its home shimmer, and must not have sent a single session read.
     */
    @Test
    fun theRealHomeIsUpWhileTheSessionIsStillAnsweringAndNothingPrivateIsRequested() {
        val held = CountDownLatch(1)
        try {
            holdSession(held)
            authenticate()
            harness.bootApp()

            harness.await("the real tab shell while the session answers") {
                harness.visibleFragmentName() == "TopicListFragment"
            }
            harness.layoutApp()
            harness.renderApp("startup-shell/home-restoring")

            val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
            assertNotNull("the shell shows upstream's tab strip before the session answers", tabs)
            assertEquals("with all four entries", 4, tabs!!.childCount)

            val list = contentListOf(harness.activity) ?: throw AssertionError("no topic list")
            assertTrue(
                "the home shows its shimmer while the feed is not in",
                list.emptyView?.isShown == true,
            )

            val requested = site.requestPaths
            assertTrue("no feed is read before the session answers: $requested",
                requested.none { it == "latest.json" })
            assertTrue("no category or tag read before the session answers: $requested",
                requested.none { it == "categories.json" || it == "tags.json" })
            assertTrue("no private read before the session answers: $requested",
                requested.none { it.startsWith("notifications") || it.startsWith("private-messages") })
            assertTrue("no write before the session answers: ${site.recordedRequests}",
                site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") })
            harness.assertNoTelegramSideEffects()
        } finally {
            held.countDown()
        }
    }

    /**
     * The session answers with the same root and the same home still in place; the link that
     * arrived while it was asking opens once, on top of them, not before and not twice.
     */
    @Test
    fun whenTheSessionAnswersTheSameRootContinuesAndTheHeldLinkOpensOnce() {
        val held = CountDownLatch(1)
        try {
            holdSession(held)
            authenticate()
            harness.bootApp(viewIntent("nativedo://topic/1/3"))

            harness.await("the root before the session answers") {
                harness.visibleFragmentName() == "TopicListFragment"
            }
            val root = harness.fragmentStack().first()
            val home = harness.visibleFragment()
            assertTrue(
                "the link stays pending until the session answers",
                harness.fragmentStack().none { it.javaClass.simpleName == "TopicFragment" },
            )

            held.countDown()

            harness.await("the held link's topic") {
                harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
            }
            harness.layoutApp()
            assertSame("the same tab shell carries on", root, harness.fragmentStack().first())
            assertSame(
                "the same home page carries on",
                home,
                (root as ViewPagerActivity).currentVisibleFragment,
            )
            assertEquals(
                "the linked topic",
                1L,
                harness.topFragment()!!.arguments?.getLong("topic_id", 0L),
            )
            assertEquals("on the floor it named", 3, harness.topFragment()!!.arguments?.getInt("floor", 0))
            assertEquals(
                "opened exactly once",
                1,
                harness.fragmentStack().count { it.javaClass.simpleName == "TopicFragment" },
            )
            harness.assertNoTelegramSideEffects()
        } finally {
            held.countDown()
        }
    }

    /**
     * Offline with no stored identity: the shell is the real home and its state says the read can be
     * tried again - it is not the login form, and the cookies are kept. The retry recovers.
     */
    @Test
    fun anOfflineStartWithNoStoredIdentityOffersRetryInsteadOfTheLoginForm() {
        site.serveResponse("session/current.json") {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("c/feedback/2/l/latest.json", "category-latest.json")
        authenticate(withSnapshot = false)
        val created = NativeDo.container(harness.context)
        runBlocking { created.categoryStore.refresh().getOrThrow() }
        harness.bootApp()

        harness.await("the offline retry state on the real home") {
            harness.visibleFragmentName() == "TopicListFragment" && stateViews().any { it.isShown }
        }
        val tabs = field<View>(harness.visibleFragment()!!, "tabsView") as org.telegram.ui.Components.FilterTabsView
        val category = (0 until tabs.tabsContainer.childCount)
            .map { tabs.tabsContainer.getChildAt(it) }.first { it.id == 2 }
        tabs.tabsContainer.clickItem(category, 1)
        harness.idleClock(400)
        assertTrue("switching a cached category must keep the actionable recovery state",
            stateViews().any { it.isShown })
        assertTrue(
            "an outage is an expired session, not a silent logout",
            container.sessionCoordinator.sessionState.value is AppSessionState.Expired,
        )
        assertTrue(
            "an outage keeps the session cookies for the retry",
            container.cookieJar.hasAuthenticatedCookies(),
        )

        site.serveResponse("session/current.json") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"current_user":{"username":"baba","name":"Baba","avatar_template":""}}""")
        }
        clickStateView()
        harness.await("the feed after the retry") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) > 0
        }
        assertEquals("the retry never left the real home", "TopicListFragment", harness.visibleFragmentName())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Offline with a stored identity: the cached identity carries the shell straight to the home,
     * whose failed read says so and can be tried again; the retry recovers.
     */
    @Test
    fun anOfflineStartWithAStoredIdentityKeepsTheHomeAndRecoversOnRetry() {
        site.serveResponse("session/current.json") {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        site.serveResponse("latest.json") {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        authenticate(withSnapshot = true)
        harness.bootApp()

        harness.await("the cached identity used as a fallback") {
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated
        }
        harness.await("the cached identity's home") {
            harness.visibleFragmentName() == "TopicListFragment"
        }
        assertTrue(
            "the cached identity is used as a fallback, not left blank",
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )
        harness.await("the failed read's retry state") { stateViews().any { it.isShown } }

        site.serveResponse("latest.json") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("latest-topics.json"))
        }
        clickStateView()
        harness.await("the feed after the retry") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) > 0
        }
        assertEquals("the retry never left the real home", "TopicListFragment", harness.visibleFragmentName())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A challenge solved from the home's own recovery page: the held link opens once the reader
     * confirms, on the same home. The verification WebView is still running its close animation
     * when the session lands, so the open rides the navigation lifecycle rather than a delay.
     */
    @Test
    fun aChallengeConfirmedInTheHomeOpensTheHeldLinkExactlyOnce() {
        site.serveBootstrap()
        site.serve(1L, "topic-detail.json")
        site.serveResponse("session/current.json") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"current_user":{"username":"baba","name":"Baba","avatar_template":""}}""")
        }
        authenticate()
        harness.bootApp()
        harness.await("the home's feed") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) == 2
        }
        val home = harness.fragmentStack().first()

        runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ChallengeRequired) }
        harness.await("the challenge state on the home") {
            harness.visibleFragmentName() == "TopicListFragment" && stateViews().any { it.isShown }
        }

        deliverViewLink("nativedo://topic/1/3")
        assertTrue(
            "a link received while the challenge is up stays pending",
            harness.fragmentStack().none { it.javaClass.simpleName == "TopicFragment" },
        )

        clickStateView()
        harness.await("the recovery page") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        val web = webView()
        harness.await("the recovery page to load") {
            org.robolectric.Shadows.shadowOf(web).lastLoadedUrl != null
        }
        // The reader solves the challenge and confirms; the page reports the same account.
        finishWebPage(web, site.baseUrl)
        completeVerification()
        org.robolectric.Shadows.shadowOf(web).lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""),
        )

        harness.await("the held link's topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertSame("the recovery does not rebuild the home", home, harness.fragmentStack().first())
        assertEquals("the held link opened its topic", 1L, harness.topFragment()!!.arguments?.getLong("topic_id", 0L))
        assertEquals(
            "opened exactly once",
            1,
            harness.fragmentStack().count { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ the identity gates the home's own entrances

    /**
     * The real home is up while the session answers, but nothing that needs the identity may be
     * entered yet: the compose button, the options menu's private entries and the search all fire
     * their own reads or writes. Clicking them must be a no-op and ask the site nothing; the tab
     * navigation (notifications/settings/profile) stays available.
     */
    @Test
    fun theHomeWithholdsComposeBookmarksAndSearchUntilTheIdentityIsConfirmed() {
        val held = CountDownLatch(1)
        try {
            holdSession(held)
            authenticate()
            harness.bootApp()
            harness.await("the real home while the session answers") {
                harness.visibleFragmentName() == "TopicListFragment"
            }
            val home = harness.visibleFragment()!!
            harness.layoutApp()
            val stackBefore = harness.fragmentStack().size

            // The compose button is withheld and a tap on it opens nothing.
            val compose = field<View>(home, "startTopicButton") as org.telegram.ui.Components.FragmentFloatingButton
            assertFalse("compose is not offered before the identity is confirmed", compose.buttonVisible)
            compose.performClick()
            harness.idle()
            assertTrue(
                "the compose tap opens no composer",
                harness.fragmentStack().none { it.javaClass.simpleName == "NewTopicFragment" },
            )

            // The options menu's private entries are withheld too.
            val optionsItem = field<View>(home, "optionsItem")
            optionsItem.performClick()
            harness.idle()
            clickMenuItem(latestItemOptionsMenu(harness.activity), "书签")
            harness.idle()
            optionsItem.performClick()
            harness.idle()
            clickMenuItem(latestItemOptionsMenu(harness.activity), "新话题")
            harness.idle()

            // Typing in the field opens the box but must not ask the site for anything.
            val editText = (field<View>(home, "searchField") as org.telegram.ui.Components.FragmentSearchField).editText
            editText.setText("linux")
            harness.idleClock(500)

            assertEquals("no private screen was pushed for the unconfirmed session", stackBefore, harness.fragmentStack().size)
            assertSame("the home carries on", home, harness.visibleFragment())
            val asked = site.requestPaths
            assertTrue(
                "nothing private was read while the identity was unconfirmed: $asked",
                asked.none {
                    it == "latest.json" || it == "categories.json" || it == "tags.json" ||
                        it == "search.json" || it.startsWith("notifications") ||
                        it.startsWith("private-messages") || it.startsWith("bookmarks") ||
                        it == "new.json" || it == "unread.json"
                },
            )
            harness.assertNoTelegramSideEffects()
        } finally {
            held.countDown()
        }
    }

    /**
     * The session is lost after the home already holds the reader's rows: the previous account's
     * rows must go, not stay behind the session state.
     */
    @Test
    fun aLostSessionDropsTheAccountsRowsFromTheHome() {
        authenticate()
        harness.bootApp()
        harness.await("the home's feed") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) == 2
        }

        runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ServerLoggedOut) }
        harness.await("the session state to replace the rows") { stateViews().any { it.isShown } }

        assertEquals(
            "the lost session's rows do not stay on the home",
            0,
            contentListOf(harness.activity)?.adapter?.itemCount ?: 0,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A new login must re-enable session observation, not just the one-off login callback. */
    @Test
    fun aSessionLostAfterSigningInAgainStillHoldsIncomingLinks() {
        authenticate()
        val shell = harness.bootApp()
        harness.await("the authenticated home") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) == 2
        }
        runBlocking { container.sessionCoordinator.onLogout().join() }
        shell.signedOut()
        harness.await("login after sign-out") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        harness.idleClock(500)
        val web = webView()
        harness.await("login page") { org.robolectric.Shadows.shadowOf(web).lastLoadedUrl != null }
        // ShadowCookieManager retains the expired domain cookie as an empty duplicate.
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().setCookie(site.baseUrl, "_t=another-login; Path=/")
        android.webkit.CookieManager.getInstance().flush()
        assertTrue("the renewed shared cookie is readable", container.cookieJar.hasAuthenticatedCookies())
        web.loadUrl(site.baseUrl)
        val client = org.robolectric.Shadows.shadowOf(web).webViewClient
        client.onPageStarted(web, site.baseUrl, null)
        client.onPageFinished(web, site.baseUrl)
        org.robolectric.Shadows.shadowOf(web).lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""),
        )
        harness.await("the new session is committed") {
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated
        }
        harness.await("signed in again") { harness.visibleFragmentName() == "TopicListFragment" }
        runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ServerLoggedOut) }
        harness.await("the second session's expired state") { stateViews().any { it.isShown } }
        deliverViewLink("nativedo://topic/1/3")
        assertTrue("a later session loss must not open a private topic",
            harness.fragmentStack().none { it.javaClass.simpleName == "TopicFragment" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The categories read is still out when a different reader signs in: its answer must not build
     * the strip for the new one.
     */
    @Test
    fun aCategoriesAnswerFromThePreviousIdentityDoesNotBuildTheStrip() {
        val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val release = CountDownLatch(1)
        site.serveResponse("categories.json") {
            if (first.getAndSet(false)) {
                release.await(30, java.util.concurrent.TimeUnit.SECONDS)
                categoriesResponse(harness.fixture("categories.json"))
            } else {
                categoriesResponse("""{"category_list":{"can_create_category":false,"can_create_topic":true,"categories":[]}}""")
            }
        }
        mockAuthenticatedSession()
        authenticate()
        harness.bootApp()
        harness.await("the previous reader's categories read") {
            site.requestPaths.contains("categories.json")
        }
        harness.await("the home's feed") {
            (contentListOf(harness.activity)?.adapter?.itemCount ?: 0) == 2
        }
        val home = harness.visibleFragment()!!
        val tabs = field<View>(home, "tabsView") as org.telegram.ui.Components.FilterTabsView
        assertEquals("only the all-topics tab is in the strip while the read is out", 1, tabs.tabsCount)

        // A different reader signs in while the previous reader's categories read is still out.
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("carol", "Carol") }
        harness.idle()

        release.countDown()
        harness.idleClock(600)
        assertEquals(
            "the previous identity's categories do not build the strip",
            1,
            tabs.tabsCount,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An offline start with cookies but no stored identity: the real home offers the retry, and a
     * warm VIEW link that arrives meanwhile stays pending. The retry recovers the session, and the
     * held link then opens once - on the same home, not a rebuilt one.
     */
    @Test
    fun theWarmLinkTheHomeRecoveredOpensOnceWithoutRebuildingTheHome() {
        site.serveResponse("session/current.json") {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        authenticate(withSnapshot = false)
        harness.bootApp()
        harness.await("the offline retry state on the real home") {
            harness.visibleFragmentName() == "TopicListFragment" && stateViews().any { it.isShown }
        }
        harness.layoutApp()
        harness.renderApp("startup-shell/home-retry")
        val home = harness.fragmentStack().first()

        deliverViewLink("nativedo://topic/1/3")
        assertTrue(
            "a link that arrives while the session is unusable stays pending",
            harness.fragmentStack().none { it.javaClass.simpleName == "TopicFragment" },
        )

        mockAuthenticatedSession()
        clickStateView()
        harness.await("the recovered link's topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertSame("the recovery does not rebuild the home", home, harness.fragmentStack().first())
        assertEquals("the held link opened its topic", 1L, harness.topFragment()!!.arguments?.getLong("topic_id", 0L))
        assertEquals("on the floor it named", 3, harness.topFragment()!!.arguments?.getInt("floor", 0))
        assertEquals(
            "opened exactly once",
            1,
            harness.fragmentStack().count { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The fourth tab's picture: a confirmed reader whose avatar is not downloaded yet still shows
     * its initials (the {@code AvatarDrawable} placeholder), not an empty circle.
     */
    @Test
    fun theFourthTabShowsAPlaceholderUntilTheAvatarIsIn() {
        mockAuthenticatedSession()
        authenticate()
        harness.bootApp()
        harness.await("the home") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.await("the confirmed reader") {
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated
        }
        val host = harness.topFragment() as io.github.baba983.nativedo.app.NativeDoMainTabsActivity
        val avatar = field<View>(host, "profileAvatarView") as org.telegram.ui.Components.BackupImageView
        harness.await("the fourth tab's placeholder") {
            avatar.imageReceiver.drawable != null
        }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /** Serves `session/current.json` with the confirmed reader, so a normal start lands on the home. */
    private fun mockAuthenticatedSession(username: String = "baba", name: String = "Baba") {
        site.serveResponse("session/current.json") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"current_user":{"username":"$username","name":"$name","avatar_template":""}}""")
        }
    }

    private fun categoriesResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    /** Delivers a linux.do VIEW link the way the system hands one to a running activity. */
    private fun deliverViewLink(url: String) {
        controllerOf(harness.activity).newIntent(viewIntent(url))
        harness.idle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun controllerOf(activity: android.app.Activity): org.robolectric.android.controller.ActivityController<android.app.Activity> {
        val shadow = org.robolectric.Shadows.shadowOf(activity)
        val field = shadow.javaClass.getDeclaredField("controller").apply { isAccessible = true }
        return field.get(shadow) as org.robolectric.android.controller.ActivityController<android.app.Activity>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val declared = type.getDeclaredField(name)
                declared.isAccessible = true
                return declared.get(target) as T
            } catch (ignored: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun webView(): android.webkit.WebView =
        views(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<android.webkit.WebView>().last()

    /** A same-origin page the recovery WebView finished loading, as its own client would report it. */
    private fun finishWebPage(web: android.webkit.WebView, url: String) {
        web.loadUrl(url)
        val client = org.robolectric.Shadows.shadowOf(web).webViewClient
        client.onPageStarted(web, url, null)
        client.onPageFinished(web, url)
        harness.idle()
    }

    /** The explicit completion on the verification page: the reader taps the menu's 完成验证. */
    private fun completeVerification() {
        val fragment = harness.visibleFragment() as org.telegram.ui.ActionBar.BaseFragment
        val item = views(fragment.actionBar)
            .filterIsInstance<org.telegram.ui.ActionBar.ActionBarMenuItem>()
            .single { menu ->
                views(menu).filterIsInstance<android.widget.TextView>()
                    .any { "完成验证" in it.text.toString() }
            }
        item.performClick()
        harness.idle()
    }


    /** Holds the session's own answer open until the returned latch is released. */
    private fun holdSession(latch: CountDownLatch) {
        site.serveResponse("session/current.json") {
            latch.await()
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"current_user":{"username":"baba","name":"Baba","avatar_template":""}}""")
        }
    }

    private fun authenticate(withSnapshot: Boolean = true) {
        NativeDo.setContainerFactory { context ->
            AppContainer(context, site.baseUrl, null, null).also { created ->
                site.seedSharedJarCookie("_t=session-token; Path=/; HttpOnly")
                site.seedSharedJarCookie("_forum_session=forum-session; Path=/; HttpOnly")
                site.seedSharedJarCookie("cf_clearance=clearance; Path=/")
                if (withSnapshot) {
                    created.sessionStore.save(
                        SessionSnapshot(
                            username = "baba",
                            displayName = "Baba",
                            avatarTemplate = "",
                            lastValidatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                container = created
            }
        }
    }

    private fun viewIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun stateViews(): List<EmptyTextProgressView> =
        views(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<EmptyTextProgressView>()

    private fun clickStateView() {
        harness.idle()
        val view = stateViews().firstOrNull { it.isShown } ?: stateViews().first()
        view.performClick()
        harness.idle()
    }

    private fun findTabs(view: View?): MainTabsLayout? {
        if (view is MainTabsLayout) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTabs(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
