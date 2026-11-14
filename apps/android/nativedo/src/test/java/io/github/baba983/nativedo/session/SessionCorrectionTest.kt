package io.github.baba983.nativedo.session

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
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
import io.github.baba983.nativedo.notifications.NotificationsFragment
import io.github.baba983.nativedo.profile.ProfileFragment
import io.github.baba983.nativedo.settings.SettingsFragment
import kotlinx.coroutines.runBlocking
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.DialogsActivity
import org.telegram.ui.MainTabsLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The explicit signed-out / expired / challenge / network states of the tab roots, and what a
 * recovered session refreshes.
 *
 * <p>The failures this pins. A read that fails left the notifications and profile screens blank
 * (the loaders removed the empty view and hid the shimmer, and nothing replaced them), so a reader
 * could not tell an expired session from an empty inbox. A session that expired while a tab was
 * already open never reached that tab, so stale private content stayed on screen. And a login or a
 * recovery only changed the session - it never re-read the data the tab had cached, so the screen
 * kept showing the previous account.</p>
 *
 * <p>These drive the real client against a local Discourse site; each state is produced by the
 * classification the production HTTP stack already applies, not by stubbing a model.</p>
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
internal class SessionCorrectionTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba.json", "user-profile.json")
        NativeDo.setContainerFactory { context ->
            site.authenticatedContainer(context).also { container = it }
        }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    // ------------------------------------------------------------------ notifications states

    /** A challenge on the notifications read: the state is not blank, and the way through is the site. */
    @Test
    fun challengeOnNotificationsShowsStateAndOpensTheChallengeWebView() {
        site.serveResponse("notifications.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html>challenge-platform</html>")
        }
        bootAndPresent(NotificationsFragment())

        harness.await("a notifications state view instead of a blank list") { rowCount() >= 1 }
        harness.layoutApp()
        harness.renderApp("session-correction/notifications-challenge-state")
        assertTrue(
            "a challenge keeps the session's cookies",
            container.cookieJar.hasAuthenticatedCookies(),
        )

        clickFirstRow()
        harness.await("the verification WebView") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        val web = webView()
        harness.await("the challenge page to load") { shadowOf(web).lastLoadedUrl != null }
        assertEquals(
            "a challenge opens the site's challenge page as an explicit recovery",
            site.url("/challenge"),
            shadowOf(web).lastLoadedUrl,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A plain 403 is a refusal, not a logout: the session stays and the read can be tried again. */
    @Test
    fun plainForbiddenKeepsTheSessionAndRetriesWithoutAWebView() {
        site.serveResponse("notifications.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["forbidden"]}""")
        }
        bootAndPresent(NotificationsFragment())

        harness.await("a notifications state view") { rowCount() >= 1 }
        val before = notificationsRequests()
        assertTrue("403 must not clear the session cookies", container.cookieJar.hasAuthenticatedCookies())
        assertNotNull("403 must not forget the account", container.sessionStore.snapshot())

        clickFirstRow()
        harness.await("the read to be tried again") { notificationsRequests() > before }
        assertEquals("403 is not a login prompt", "NotificationsFragment", harness.visibleFragmentName())
        assertTrue("403 keeps the cookies", container.cookieJar.hasAuthenticatedCookies())
        harness.assertNoTelegramSideEffects()
    }

    /** An outage is neither a logout nor a challenge: the same read goes out again. */
    @Test
    fun offlineNotificationsRetriesWithoutAWebView() {
        site.serveResponse("notifications.json") { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) }
        bootAndPresent(NotificationsFragment())

        harness.await("a notifications state view") { rowCount() >= 1 }
        val before = notificationsRequests()
        clickFirstRow()
        harness.await("the read to be tried again") { notificationsRequests() > before }
        assertEquals("an outage is not a login prompt", "NotificationsFragment", harness.visibleFragmentName())
        assertTrue("an outage keeps the cookies", container.cookieJar.hasAuthenticatedCookies())
        harness.assertNoTelegramSideEffects()
    }

    /** A proven logout expires the session: the cookies are gone and the state offers the login page. */
    @Test
    fun unauthorizedNotificationsExpireTheSessionAndOfferLogin() {
        site.serveResponse("notifications.json") {
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error_type":"not_logged_in"}""")
        }
        bootAndPresent(NotificationsFragment())

        harness.await("a notifications state view") { rowCount() >= 1 }
        assertFalse("a proven logout clears the cookies", container.cookieJar.hasAuthenticatedCookies())
        assertNull("a proven logout forgets the account", container.sessionStore.snapshot())
        assertTrue(
            "the session reads as expired",
            container.sessionCoordinator.sessionState.value is AppSessionState.Expired,
        )

        clickFirstRow()
        harness.await("the login WebView") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        val web = webView()
        harness.await("the login page to load") { shadowOf(web).lastLoadedUrl != null }
        assertEquals(
            "an expired session offers the login page",
            site.url("/login"),
            shadowOf(web).lastLoadedUrl,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Cancelling the challenge WebView is not a recovery: the state stays and nothing is re-read. */
    @Test
    fun cancellingChallengeVerificationIsNotSuccess() {
        site.serveResponse("notifications.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html>challenge-platform</html>")
        }
        bootAndPresent(NotificationsFragment())
        harness.await("a notifications state view") { rowCount() >= 1 }

        val readsBefore = notificationsRequests()
        clickFirstRow()
        harness.await("the verification WebView") { harness.visibleFragmentName() == "LoginWebViewFragment" }

        harness.activity.onBackPressed()
        harness.idle()
        harness.await("cancel back to notifications") { harness.visibleFragmentName() == "NotificationsFragment" }
        harness.await("the state is still there") { rowCount() >= 1 }
        assertEquals("cancel does not re-read the private list", readsBefore, notificationsRequests())
        assertTrue("cancel keeps the session", container.cookieJar.hasAuthenticatedCookies())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ expiry of an open tab

    /** An already-open notifications tab reaches the recovery state, and a staged login re-reads it. */
    @Test
    fun openNotificationsTabNeedsRecoveryThenStagedLoginReloadsTheRead() {
        site.serveFixture("notifications.json", "notifications.json")
        bootAndPresent(NotificationsFragment())
        harness.await("the notifications to arrive") { rowCount() == FIXTURE_ROWS }
        val readsBefore = notificationsRequests()

        requireRecovery()
        harness.await("the state replaces the old rows") { rowCount() == 1 }
        harness.layoutApp()
        harness.renderApp("session-correction/notifications-recovery-state")

        seedSessionCookies()
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("baba", "Baba") }
        harness.await("the list to be read for the signed-in account again") {
            rowCount() == FIXTURE_ROWS && notificationsRequests() > readsBefore
        }
        assertTrue(
            "recovery only reads; it never reacts, replies or reports timings",
            site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A response that lands after the account changed must not repopulate the private list. */
    @Test
    fun aLateOldAccountResponseDoesNotRepopulateTheList() {
        val release = CountDownLatch(1)
        site.serveResponse("notifications.json") {
            release.await(30, TimeUnit.SECONDS)
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("notifications.json"))
        }
        bootAndPresent(NotificationsFragment())
        harness.await("the first request to reach the site") { notificationsRequests() >= 1 }

        expireSession()
        harness.await("the expired state") { rowCount() == 1 }

        release.countDown()
        harness.idleClock(500)
        assertEquals("the old account's page cannot repopulate the list", 1, rowCount())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ profile

    /** An already-open own-profile tab reaches the recovery state, and a staged login re-reads it. */
    @Test
    fun openProfileTabNeedsRecoveryThenStagedLoginReloadsIt() {
        site.serveFixture("u/baba.json", "user-profile.json")
        bootAndPresent(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile read") { site.requestPaths.contains("u/baba.json") }
        harness.await("the profile rows") { rowCount() >= 1 }
        val readsBefore = site.requestPaths.count { it == "u/baba.json" }

        requireRecovery()
        harness.await("the state replaces the profile rows") { rowCount() == 1 }
        harness.layoutApp()
        harness.renderApp("session-correction/profile-session-state")

        seedSessionCookies()
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("baba", "Baba") }
        harness.await("the profile to be read again for the recovered account") {
            site.requestPaths.count { it == "u/baba.json" } > readsBefore
        }
        assertTrue(
            "recovery only reads",
            site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ settings

    /** The settings tab keeps its own rows while its account row tells the truth about the session. */
    @Test
    fun settingsAccountRowStaysTruthfulWhileItRemainsUsable() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings tab") { harness.visibleFragmentName() == "SettingsFragment" }
        harness.layoutApp()
        assertTrue("the account is shown while signed in", rowTexts().any { it.startsWith("@baba") })

        requireRecovery()
        harness.await("the account row to stop claiming the lost session") {
            rowTextsAcrossTheList().none { it.startsWith("@baba") }
        }
        val rows = rowTextsAcrossTheList()
        assertTrue("appearance stays reachable", "外观" in rows)
        assertTrue("help stays reachable", "帮助" in rows)
        assertTrue("the account row offers signing in", rows.any { it.contains("登录") })
        harness.layoutApp()
        harness.renderApp("session-correction/settings-session-state")

        // And back: a staged login shows the account again.
        seedSessionCookies()
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("baba", "Baba") }
        harness.await("the account row to report the recovered session") {
            rowTextsAcrossTheList().any { it.startsWith("@baba") }
        }
        harness.assertNoTelegramSideEffects()
    }

    /** The two states render in both themes, for inspection. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun sessionStatesRenderInBothThemes() {
        val initiallyDark = Theme.isCurrentThemeDark()
        site.serveResponse("notifications.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html>challenge-platform</html>")
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        // A fresh page is presented under each theme: the harness' pushed pages do not repaint on a
        // switch, so a render that names its theme has to build the page under that theme.
        switchTheme("Blue", false)
        harness.present(NotificationsFragment())
        harness.await("the challenge state") { rowCount() >= 1 }
        harness.layoutApp()
        harness.renderApp("session-correction/notifications-challenge-light")

        switchTheme("Dark Blue", true)
        harness.present(NotificationsFragment())
        harness.await("the challenge state in the dark theme") { rowCount() >= 1 }
        harness.layoutApp()
        harness.renderApp("session-correction/notifications-challenge-dark")

        // Restore the theme the sandbox entered with.
        switchTheme(if (initiallyDark) "Dark Blue" else "Blue", initiallyDark)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * The tab host rebuilds a page's views (a theme switch) without destroying the fragment. The
     * observer belongs to the views, so it must be dropped and started again - a rebuilt settings
     * page still has to follow the session.
     */
    @Test
    fun theSessionObserverReattachesAfterTheTabViewsAreRebuilt() {
        harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings tab") { harness.visibleFragmentName() == "SettingsFragment" }
        harness.layoutApp()
        assertTrue(rowTexts().any { it.startsWith("@baba") })

        val initiallyDark = Theme.isCurrentThemeDark()
        switchTheme("Blue", false)
        switchTheme("Dark Blue", true)
        switchTheme(if (initiallyDark) "Dark Blue" else "Blue", initiallyDark)
        harness.await("the settings screen to survive the rebuild") {
            harness.visibleFragmentName() == "SettingsFragment"
        }

        requireRecovery()
        harness.await("the rebuilt page to still follow the session") {
            rowTextsAcrossTheList().none { it.startsWith("@baba") }
        }
        assertTrue("appearance stays reachable after the rebuild", "外观" in rowTextsAcrossTheList())
        harness.assertNoTelegramSideEffects()
    }

    /** A read that lands after the screen is gone must be dropped, not drawn onto a dead view. */
    @Test
    fun aReadThatLandsAfterTheScreenIsGoneIsSafe() {
        val release = CountDownLatch(1)
        site.serveResponse("notifications.json") {
            release.await(30, TimeUnit.SECONDS)
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("notifications.json"))
        }
        bootAndPresent(NotificationsFragment())
        harness.await("the first request to reach the site") { notificationsRequests() >= 1 }

        harness.activity.onBackPressed()
        harness.idle()
        release.countDown()
        harness.idleClock(300)
        assertTrue(
            "the notifications screen is gone and nothing was drawn onto it",
            harness.visibleFragmentName() != "NotificationsFragment",
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ account change / recovery

    /**
     * A read that hits the site's challenge while the session is *still* the signed-in account:
     * the coordinator keeps [AppSessionState.Authenticated], the page shows its challenge state, and
     * the reader solves it in the recovery WebView. The explicit completion publishes the same
     * account (the identity does not change), so the failed page must re-read on that confirmation
     * alone - otherwise it keeps showing its challenge over a session that already works. This is
     * the same-account refresh the observer cannot see, and it must read without cancelling the
     * WebView or writing the session.
     */
    @Test
    fun aChallengeReadOnAnAuthenticatedSessionIsRefreshedByTheExplicitCompletion() {
        val challenged = java.util.concurrent.atomic.AtomicBoolean(false)
        site.serveResponse("notifications.json") {
            if (!challenged.getAndSet(true)) {
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("cf-mitigated", "challenge")
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>challenge-platform</html>")
            } else {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("notifications.json"))
            }
        }
        bootAndPresent(NotificationsFragment())
        harness.await("the challenge state") { rowCount() >= 1 }
        assertTrue(
            "a read challenge does not change the account the coordinator holds",
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )
        harness.layoutApp()
        harness.renderApp("session-correction/notifications-challenge-authenticated")

        val readsBefore = notificationsRequests()
        clickFirstRow()
        harness.await("the verification WebView") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        val web = webView()
        harness.await("the challenge page to load") { shadowOf(web).lastLoadedUrl != null }

        // The reader solves the challenge and confirms; the page reports the same account.
        finishWebPage(web, site.baseUrl)
        completeVerification()
        shadowOf(web).lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""),
        )
        harness.await("the same account's list to be read again after the confirmation") {
            notificationsRequests() > readsBefore && rowCount() == FIXTURE_ROWS
        }
        assertTrue("the session survives the recovery", container.cookieJar.hasAuthenticatedCookies())
        assertTrue(
            "recovery only reads; it never reacts, replies or reports timings",
            site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") },
        )
        harness.renderApp("session-correction/notifications-recovered-same-account")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The same page, completed by a *different* account. The observer's identity change already
     * re-reads the list, so the explicit completion must not add a second read: the account the app
     * holds is the new one either way, and the failed page is refreshed exactly once.
     */
    @Test
    fun aDifferentAccountCompletionReloadsTheFailedPageExactlyOnce() {
        val challenged = java.util.concurrent.atomic.AtomicBoolean(false)
        site.serveResponse("notifications.json") {
            if (!challenged.getAndSet(true)) {
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("cf-mitigated", "challenge")
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>challenge-platform</html>")
            } else {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("notifications.json"))
            }
        }
        bootAndPresent(NotificationsFragment())
        harness.await("the challenge state") { rowCount() >= 1 }

        val readsBefore = notificationsRequests()
        clickFirstRow()
        harness.await("the verification WebView") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        val web = webView()
        harness.await("the challenge page to load") { shadowOf(web).lastLoadedUrl != null }

        finishWebPage(web, site.baseUrl)
        completeVerification()
        shadowOf(web).lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"carol","name":"Carol"}"""),
        )
        harness.await("the new account's list") { rowCount() == FIXTURE_ROWS }
        assertEquals(
            "a changed account is refreshed once, not twice",
            1,
            notificationsRequests() - readsBefore,
        )
        assertTrue(
            "the new account is the one the app holds",
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The shell's own {@code 我的} tab is whatever account is signed in. The old flow bound the tab
     * root to the username captured when the shell was built, so a different reader's login left
     * the root reading and naming the previous account, and the previous account's activity stream
     * could repopulate its tabs. The root must follow the new identity - read, name, tabs and
     * streams - and drop any answer that belonged to the old one.
     */
    @Test
    fun theRootProfileFollowsANewAccountAndDropsTheOldStream() {
        site.serveFixture("u/baba.json", "user-profile.json")
        val carol = org.json.JSONObject(harness.fixture("user-profile.json"))
        carol.getJSONObject("user").put("username", "carol").put("name", "Carol")
        site.serveJson("u/carol.json", carol.toString())
        val oldStreamReleased = CountDownLatch(1)
        site.serveJsonFor("user_actions.json") { path ->
            val username = path.substringAfter("username=").substringBefore('&')
            val filter = path.substringAfter("filter=").substringBefore('&')
            val offset = path.substringAfter("offset=").substringBefore('&').toIntOrNull() ?: 0
            if (username == "baba") {
                oldStreamReleased.await(30, TimeUnit.SECONDS)
            }
            userActionsPage(username, filter, offset)
        }

        harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        tapTab(3)
        harness.await("the root profile") { harness.visibleFragmentName() == "ProfileFragment" }
        val profile = harness.visibleFragment() as ProfileFragment
        harness.await("the old account's profile") { site.requestPaths.contains("u/baba.json") }
        harness.await("the tab area to build on the old account") {
            harness.layoutApp()
            field<Any?>(profile, "mediaView") != null
        }
        harness.await("the old account's activity stream") {
            site.queriesFor("user_actions.json").any { it.contains("username=baba") }
        }
        harness.layoutApp()
        harness.renderApp("session-correction/root-profile-old-account")

        // A different reader signs in while the root tab is open.
        runBlocking { container.sessionCoordinator.completeLoginFromWebViewStaged("carol", "Carol") }
        harness.idle()

        harness.await("the root to read the new account's profile") {
            site.requestPaths.count { it == "u/carol.json" } >= 1
        }
        harness.await("the new account's activity stream") {
            site.queriesFor("user_actions.json").any {
                it.contains("username=carol") && it.contains("filter=4")
            }
        }
        // The dangerous window is the new feed's request being in flight: let the old account's
        // blocked answer through now, before the new rows are bound. A stale generation must drop
        // it; if it does not, the baba rows would land in the tab below.
        oldStreamReleased.countDown()
        val name = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "nameTextView")
        val online = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "onlineTextView")
        harness.await("the root header to name the new account") {
            name.text?.toString() == "Carol" && online.text?.toString() == "@carol"
        }
        harness.await("the new account's own profile to load") {
            val loaded = field<io.github.baba983.nativedo.core.model.UserProfile?>(profile, "profile")
            loaded != null && loaded.username == "carol"
        }
        val tabs = field<Array<Any?>>(profile, "tabPages")
        val items = field<MutableList<Any?>>(tabs[0]!!, "items")
        // The request reaching the site is not the stream arriving: wait for the new account's
        // rows to be bound, then give the already-released old answer time to be processed.
        harness.await("the new account's stream to fill the tab") {
            items.filterIsInstance<io.github.baba983.nativedo.core.model.TopicSummary>()
                .map { it.title }
                .let { titles -> titles.isNotEmpty() && titles.all { it.startsWith("carol") } }
        }
        harness.idleClock(600)
        val titles = items.filterIsInstance<io.github.baba983.nativedo.core.model.TopicSummary>()
            .map { it.title }
        assertTrue("the tab shows the new account's stream", titles.isNotEmpty())
        assertFalse(
            "no row of the previous account's stream survived",
            titles.any { it.startsWith("baba") },
        )
        harness.layoutApp()
        harness.renderApp("session-correction/root-profile-new-account")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ settings callback safety

    /**
     * The recovery WebView's completion callback can outlive the settings tab's views (the tab was
     * torn down while the page was up). It must not dereference those views or start a read for a
     * dead screen.
     */
    @Test
    fun theSettingsRecoveryCallbackDoesNothingAfterTheViewsAreGone() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings tab") { harness.visibleFragmentName() == "SettingsFragment" }
        val settings = harness.visibleFragment() as SettingsFragment

        // The host tears the page's views down without destroying the fragment.
        settings.clearViews()
        harness.idle()
        val readsBefore = site.requestPaths.count { it == "u/baba.json" }

        // Whatever the WebView would have called on completion lands on a screen with no views.
        invoke<Any?>(settings, "onSessionRecovered")
        // Give a read the completion might start real time to reach the site (the client runs off
        // the main thread); a guarded callback starts none, so the count never moves.
        repeat(300) {
            harness.idle()
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }

        assertEquals(
            "a torn-down settings page does not start a read for itself",
            readsBefore,
            site.requestPaths.count { it == "u/baba.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun bootAndPresent(fragment: org.telegram.ui.ActionBar.BaseFragment) {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(fragment)
    }

    private fun expireSession() {
        runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ServerLoggedOut) }
        harness.idle()
    }

    /**
     * The session stops being usable through a challenge: the cookies are kept (the production
     * behaviour for a challenge), so the recovery that follows can read the identity back.
     */
    private fun requireRecovery() {
        runBlocking { container.sessionCoordinator.markExpired(SessionExpiredReason.ChallengeRequired) }
        harness.idle()
    }

    private fun seedSessionCookies() {
        site.seedSharedJarCookie("_t=session-token; Path=/; HttpOnly")
        site.seedSharedJarCookie("_forum_session=forum-session; Path=/; HttpOnly")
        site.seedSharedJarCookie("cf_clearance=clearance; Path=/")
    }

    private fun notificationsRequests(): Int = site.requestPaths.count { it == "notifications.json" }

    private fun clickFirstRow() {
        harness.drawApp()
        val list = contentListOf(harness.activity) ?: error("no list")
        harness.layoutApp()
        val child = list.getChildAt(0) ?: error("no row to click")
        list.clickItem(child, 0)
        harness.idle()
    }

    private fun webView(): WebView =
        viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<WebView>().last()

    /** A same-origin page the recovery WebView finished loading, as its own client would report it. */
    private fun finishWebPage(web: WebView, url: String) {
        web.loadUrl(url)
        val client = shadowOf(web).webViewClient
        client.onPageStarted(web, url, null)
        client.onPageFinished(web, url)
        harness.idle()
    }

    /** The explicit completion on the verification page: the reader taps the menu's 完成验证. */
    private fun completeVerification() {
        val fragment = harness.visibleFragment() as org.telegram.ui.ActionBar.BaseFragment
        val item = viewsUnder(fragment.actionBar)
            .filterIsInstance<org.telegram.ui.ActionBar.ActionBarMenuItem>()
            .single { menu ->
                viewsUnder(menu).filterIsInstance<android.widget.TextView>()
                    .any { "完成验证" in it.text.toString() }
            }
        item.performClick()
        harness.idle()
    }

    /** One `user_actions.json` page, titled by the account it belongs to. */
    private fun userActionsPage(username: String, filter: String, offset: Int): String {
        val size = if (offset == 0) 20 else 5
        val actions = org.json.JSONArray()
        for (i in 0 until size) {
            actions.put(
                org.json.JSONObject()
                    .put("id", offset + i + 100)
                    .put("post_id", offset + i + 1000)
                    .put("topic_id", 3000 + offset + i)
                    .put("title", "$username $filter row ${offset + i}")
                    .put("excerpt", "body")
                    .put("username", username)
                    .put("created_at", "2026-05-01T00:00:00.000Z")
                    .put("post_number", i + 1),
            )
        }
        return org.json.JSONObject().put("user_actions", actions).toString()
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> invoke(target: Any, name: String): T {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val method = type.getDeclaredMethod(name)
                method.isAccessible = true
                return method.invoke(target) as T
            } catch (ignored: NoSuchMethodException) {
                type = type.superclass
            }
        }
        throw NoSuchMethodException(name)
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun tapTab(index: Int) {
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<MainTabsLayout>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0,
            )
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
    }

    private fun rowTexts(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { textsOf(list.getChildAt(it)) }
    }

    private fun rowTextsAcrossTheList(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        val count = list.adapter?.itemCount ?: 0
        val texts = LinkedHashSet<String>()
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            texts += rowTexts()
        }
        list.scrollToPosition(0)
        harness.layoutApp()
        return texts.toList()
    }

    private fun textsOf(view: View): List<String> {
        val texts = viewsUnder(view)
        return texts.filterIsInstance<android.widget.TextView>().map { it.text.toString() } +
            texts.filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>()
                .mapNotNull { it.text?.toString() } +
            texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                .mapNotNull { it.text?.toString() }
    }

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
        return found
    }

    private fun switchTheme(themeName: String, toDark: Boolean) {
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(themeName),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the $themeName theme") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            harness.layoutApp()
            harness.idle()
        } finally {
            DialogsActivity.switchingTheme = false
        }
    }

    private companion object {
        const val FIXTURE_ROWS = 4
    }
}
