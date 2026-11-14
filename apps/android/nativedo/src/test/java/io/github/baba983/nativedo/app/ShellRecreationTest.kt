package io.github.baba983.nativedo.app

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import io.github.baba983.nativedo.topic.TopicFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.glass.GlassTabView
import org.telegram.ui.ContentPreviewViewer
import org.telegram.ui.MainTabsLayout

import java.util.concurrent.CountDownLatch
import okhttp3.mockwebserver.MockResponse

/**
 * The shell's instance state: what a recreation comes back to, and what the shell releases when it
 * is really destroyed.
 *
 * <p>Three failures pinned here, each invisible in a healthy single-shot run:</p>
 * <ul>
 *   <li>a recreation builds a fresh empty stack - the confirmed failure - so the reader is dropped
 *       to a blank shell (or, in the buggy shape, to a root that never comes back at all); the tab
 *       they were on and the topic they had open must survive;</li>
 *   <li>a recreation replays private content before the session has been revalidated, so a reader
 *       whose cookies expired sees the topic they were reading before the login gate;</li>
 *   <li>a destroyed shell leaks what its screens subscribed to, and tears down the shared viewers
 *       even while a configuration-change replacement still owns them.</li>
 * </ul>
 *
 * <p>The flows run the real host: [UpstreamUiHarness.bootApp] builds the shipped
 * [NativeDoActivity], [FakeDiscourseSite] answers its session and screen reads, and the system's own
 * {@code ActivityController.recreate()} drives save-then-rebuild.</p>
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
internal class ShellRecreationTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serve(1L, "topic-detail.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        resetShellCount()
        resetBrowserHost()
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        resetContentPreviewViewer()
        resetShellCount()
        resetBrowserHost()
        site.stop()
    }

    /**
     * The confirmed failure: onCreate cleared the stack and only bootstrapped a session when
     * {@code savedInstanceState == null}, so a recreation produced nothing to show. The reader's
     * page and the topic they were in must come back, with the topic on top of the tab host so back
     * returns to the feed.
     */
    @Test
    fun aRecreationRestoresTheSelectedRootTabAndTheOpenTopic() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
            ?: throw AssertionError("no main tabs")
        tapTab(tabs, 2)
        harness.await("the settings page") { harness.visibleFragmentName() == "SettingsFragment" }
        shell.present(TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }

        recreateShell(shell)
        harness.await("the restored topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the topic the reader had open comes back", 1L, topicIdOf(harness.topFragment()!!))
        assertTrue(
            "it is on top of the tab host, so back returns to the feed",
            harness.fragmentStack().any { it.javaClass.simpleName == "NativeDoMainTabsActivity" },
        )
        harness.renderApp("recreation-restored-topic")

        (harness.activity as NativeDoActivity).onBackPressed()
        harness.await("back on the restored tab") {
            harness.visibleFragmentName() == "SettingsFragment"
        }
        harness.renderApp("recreation-restored-settings-tab")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The gate holds across a recreation: when the session cookies are gone by the time the shell is
     * rebuilt, the login page must replace the saved navigation instead of the shell replaying the
     * topic the reader had open.
     */
    @Test
    fun aRecreationDoesNotRestorePrivateContentWhenTheSessionIsGone() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        shell.present(TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }

        // The session is gone by the time the replacement shell runs: the shared jar carries no
        // `_t`, so the gate answers anonymous.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        NativeDo.setContainerFactory { context -> AppContainer(context, site.baseUrl, null, null) }

        recreateShell(shell)
        harness.await("the gate to answer") { harness.visibleFragmentName() != null }
        assertEquals(
            "an unauthenticated recreation lands on the login page",
            "LoginWebViewFragment",
            harness.visibleFragmentName(),
        )
        assertFalse(
            "the saved topic is not rebuilt before the session is valid",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A destroyed shell must tell every screen it held: the fragments are not AndroidX fragments, so
     * the framework will not deliver {@code onFragmentDestroy} for them, and each one is where a
     * subscription (the tab host's message-bus badge) is released.
     */
    @Test
    fun aDestroyedShellCleansUpTheFragmentsItHeld() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        shell.present(TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }

        val stacked = harness.fragmentStack()
        assertTrue("there are screens to clean up", stacked.size >= 2)
        assertFalse("nothing is finished while the shell is alive", stacked.any { it.isFinished })

        controllerOf(shell).pause().stop().destroy()
        harness.idle()

        assertTrue("every screen was told the shell is gone", stacked.all { it.isFinished })
    }

    /**
     * The shared-viewer guard: a configuration change creates the replacement before the old
     * activity is destroyed, so the old destroy must not tear down a viewer that may already belong
     * to the replacement. The viewer is released only when the last shell goes.
     */
    @Test
    fun theLastShellReleasesTheViewerWhileAConfigurationChangeKeepsIt() {
        val shell = harness.bootApp()
        harness.await("the shell root") { harness.visibleFragment() != null }
        openContentPreviewViewer(shell)
        assertTrue("the viewer is open", ContentPreviewViewer.hasInstance())

        // Real Android's order for a configuration change: the replacement is created first, then
        // the old activity is destroyed. Its setup drains the looper while the glass frame clock
        // re-posts itself, so the clock is held while it boots (the harness does the same).
        val replacement = bootSecondShell()
        harness.idle()
        controllerOf(shell).pause().stop().destroy()
        harness.idle()
        assertTrue(
            "the replacement's viewer survives the old shell's destroy",
            ContentPreviewViewer.hasInstance(),
        )

        replacement.pause().stop().destroy()
        harness.idle()
        assertFalse("the last shell releases it", ContentPreviewViewer.hasInstance())
    }

    /**
     * The browser host is the process-wide seam upstream's in-app browser finds the shell through
     * (its {@code ActionBarLayout}, its minimized tabs). A configuration change creates the
     * replacement first, and its {@code onCreate} registers it; the old activity's destroy must
     * not then clear the host out from under the live replacement - which is what an unconditional
     * {@code setBrowserHost(null)} does, and it stays invisible until the browser asks a dead shell.
     */
    @Test
    fun theDestructionOfTheOldShellLeavesTheReplacementsBrowserHostStanding() {
        val shell = harness.bootApp()
        harness.await("the shell root") { harness.visibleFragment() != null }
        assertTrue("the shell registers itself as the browser host", NativeDoHooks.browserHostPresent())

        val replacement = bootSecondShell()
        harness.await("the replacement to answer as the live host") {
            NativeDoHooks.getLastFragment() != null
        }

        controllerOf(shell).pause().stop().destroy()
        harness.idle()
        assertNotNull(
            "the old shell's destroy did not clear the replacement's host",
            NativeDoHooks.getLastFragment(),
        )

        replacement.pause().stop().destroy()
        harness.idle()
        assertFalse("the last shell takes the host with it", NativeDoHooks.browserHostPresent())
    }

    /**
     * The session gate's answer can outlive the shell that asked for it: the revalidation is a
     * network round-trip, and a configuration change can destroy the asker before it answers. The
     * answer then lands on a destroyed activity whose fragment layout still exists as a field -
     * building the root there is what a late callback does, and it is invisible in a healthy
     * single-shot run.
     */
    @Test
    fun aSessionAnswerThatArrivesAfterDestroyIsNotBuiltOnTheDeadShell() {
        val gate = CountDownLatch(1)
        // Hold the homepage bootstrap the session read falls back to, so the answer cannot arrive
        // while the shell is alive.
        site.serveResponse("") {
            gate.await()
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body></body></html>")
        }
        val shell = harness.bootApp()
        controllerOf(shell).pause().stop().destroy()
        harness.idle()

        // The answer arrives now, on a shell the system has already destroyed.
        gate.countDown()
        val container = NativeDo.container(harness.context)
        harness.await("the late session answer") {
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated
        }
        harness.idle()

        assertTrue(
            "the late answer built nothing on the destroyed shell",
            harness.fragmentStack().isEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ deep links and the gate

    /**
     * A VIEW link that arrives while the reader is signed out is private content the gate has not
     * let through: it must stay pending behind the login page, not open the topic - an answer of
     * "anonymous" is not a session, and a link is not a reason to treat it as one.
     */
    @Test
    fun aViewLinkThatArrivesBeforeTheSessionIsValidIsNotOpened() {
        val shell = anonymousShell()

        deliverViewLink(shell, site.url("t/1/3"))

        assertEquals(
            "the reader is still on the login page",
            "LoginWebViewFragment",
            harness.visibleFragmentName(),
        )
        assertFalse(
            "the link did not open a topic before the gate let it through",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The link survives the login: once the reader signs in, the topic opens on the floor the link
     * named, not on the first floor the screen would otherwise start at.
     */
    @Test
    fun aViewLinkThatWaitedOutLoginOpensOnTheFloorItNamed() {
        val shell = anonymousShell()

        deliverViewLink(shell, site.url("t/1/3"))
        assertFalse(
            "the link is held back until the session is valid",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )

        completeLoginWithTheSite()
        harness.await("the topic the link named") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the linked topic", 1L, topicIdOf(harness.topFragment()!!))
        assertEquals("the floor the link named", 3, topicFloorOf(harness.topFragment()!!))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Signing out withdraws the gate's answer: a link that arrives while the login page is up is
     * private content again, and must not open the topic.
     */
    @Test
    fun aViewLinkAfterSignOutIsNotOpened() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }

        shell.signedOut()
        harness.await("the login page") { harness.visibleFragmentName() == "LoginWebViewFragment" }

        deliverViewLink(shell, site.url("t/1/3"))

        assertEquals(
            "the reader is still on the login page",
            "LoginWebViewFragment",
            harness.visibleFragmentName(),
        )
        assertFalse(
            "a signed-out shell does not open a link",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A signed-in reader's same-origin link opens the topic, on the floor it named. */
    @Test
    fun aViewLinkFromASignedInReaderOpensOnTheFloorItNamed() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }

        deliverViewLink(shell, site.url("t/1/3"))

        harness.await("the linked topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the linked topic", 1L, topicIdOf(harness.topFragment()!!))
        assertEquals("the floor the link named", 3, topicFloorOf(harness.topFragment()!!))
        harness.assertNoTelegramSideEffects()
    }

    /** A link that only looks like the site's is not this client's to open. */
    @Test
    fun aViewLinkFromAForeignOriginIsRefused() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }

        deliverViewLink(shell, "https://evil.example.com/t/1/3")

        assertFalse(
            "a foreign link is not followed",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The whole app against a site with no session: boot it and wait for the gate to answer with
     * the login page, which is the state a VIEW link has to survive.
     */
    private fun anonymousShell(): NativeDoActivity {
        NativeDo.setContainerFactory { context -> AppContainer(context, site.baseUrl, null, null) }
        val shell = harness.bootApp()
        harness.await("the login gate") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        return shell
    }

    /** Delivers a linux.do VIEW link the way the system hands one to a running activity. */
    private fun deliverViewLink(shell: NativeDoActivity, url: String) {
        controllerOf(shell).newIntent(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
        )
        harness.idle()
    }

    /**
     * Drives the login WebView the way the site's own page does: it lands same-origin with the
     * session cookie set and reports the reader, and the login completion opens what waited.
     */
    private fun completeLoginWithTheSite() {
        val fragment = harness.visibleFragment() as io.github.baba983.nativedo.auth.LoginWebViewFragment
        val web = io.github.baba983.nativedo.auth.LoginWebViewFragment::class.java
            .getDeclaredField("webView").apply { isAccessible = true }.get(fragment) as android.webkit.WebView
        harness.await("the login page to load") { shadowOf(web).lastLoadedUrl != null }
        CookieManager.getInstance().setCookie(site.baseUrl, "_t=new-login; Path=/")
        CookieManager.getInstance().flush()
        web.loadUrl(site.baseUrl)
        val shadow = shadowOf(web)
        shadow.webViewClient.onPageStarted(web, site.baseUrl, null)
        shadow.webViewClient.onPageFinished(web, site.baseUrl)
        shadow.lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""),
        )
    }

    /**
     * The restore opens the topic where the reader actually is. The fragment's arguments only carry
     * the floor the screen was *opened* at (a feed row's post, a link's floor); a reader who has
     * since scrolled on is past it, and the floor they are on is the device's own read position. A
     * recreation that saved the opening floor would send them back down a thread they had already
     * read.
     */
    @Test
    fun aRecreationResumesTheTopicAtTheCurrentReadingFloorNotTheOpeningOne() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        // The reader opened this topic on floor 3 and read on to floor 9; the position the topic
        // reporter persists is 9.
        val container = NativeDo.container(harness.context)
        container.topicReadPositionStore.save(
            TopicReadPositionKey(container.baseUrl, "baba", 1L),
            9,
        )
        shell.present(TopicFragment.forFloor(1L, "A topic", 3))
        harness.await("the topic to open") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        assertEquals(
            "the screen opened on the floor the link named",
            3,
            topicFloorOf(harness.topFragment()!!),
        )

        recreateShell(shell)
        harness.await("the restored topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        assertEquals(
            "the topic comes back at the reader's current floor, not the one it was opened at",
            9,
            topicFloorOf(harness.topFragment()!!),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Saves the shell's instance state, destroys it, and boots its replacement from that state - the
     * real save-then-rebuild a configuration change or process death performs. The replacement is
     * built with {@code setup(bundle)}, which restores the state through {@code onCreate}; the frame
     * clock is held across the boot for the reason in [withFrameClockHeld].
     */
    private fun recreateShell(shell: NativeDoActivity) {
        val controller = controllerOf(shell)
        val state = Bundle()
        controller.saveInstanceState(state)
        controller.pause().stop().destroy()
        val recreated = withFrameClockHeld {
            Robolectric.buildActivity(NativeDoActivity::class.java).setup(state)
        }
        harness.idle()
        // The replacement window finishes its restore on delayed passes, exactly like the first
        // boot: until they run, the restored page's transition counts as in progress and swallows
        // the next back.
        recreated.visible()
        harness.idleClock(300)
        harness.activity = recreated.get()
    }

    /**
     * Runs [block] with Choreographer held paused. [UpstreamUiHarness.bootApp] does the same around
     * its setup: a glass background drives upstream's permanent frame clock, and Robolectric's
     * `visible()`/`setup()` drain the looper unbounded, which a self-re-posting frame callback never
     * lets finish. Every lifecycle step that reaches `visible()` - boot, recreate - needs this.
     */
    private fun <T> withFrameClockHeld(block: () -> T): T {
        val paused = org.robolectric.shadows.ShadowChoreographer.isPaused()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        return try {
            block()
        } finally {
            org.robolectric.shadows.ShadowChoreographer.setPaused(paused)
        }
    }

    /** A second shell, booted with the frame clock held (see [withFrameClockHeld]). */
    private fun bootSecondShell(): ActivityController<NativeDoActivity> =
        withFrameClockHeld { Robolectric.buildActivity(NativeDoActivity::class.java).setup() }

    // ------------------------------------------------------------------ system's side of a rebuild

    /**
     * The controller Robolectric already ran for this activity, so {@code recreate()} can drive its
     * real save-then-rebuild. The harness builds through {@code Robolectric.buildActivity(...).setup()},
     * and the shadow keeps that controller; this reads it back rather than re-attaching a new one.
     */
    @Suppress("UNCHECKED_CAST")
    private fun controllerOf(activity: Activity): ActivityController<NativeDoActivity> {
        val shadow = shadowOf(activity)
        val field = shadow.javaClass.getDeclaredField("controller").apply { isAccessible = true }
        return field.get(shadow) as ActivityController<NativeDoActivity>
    }

    private fun topicIdOf(fragment: BaseFragment): Long =
        fragment.arguments?.getLong("topic_id", 0L) ?: 0L

    /** ContentPreviewViewer opened just enough that its destroy is not the early-return no-op. */
    private fun openContentPreviewViewer(activity: Activity) {
        val viewer = ContentPreviewViewer.getInstance()
        ContentPreviewViewer::class.java.getDeclaredField("parentActivity")
            .apply { isAccessible = true }.set(viewer, activity)
        ContentPreviewViewer::class.java.getDeclaredField("windowView")
            .apply { isAccessible = true }.set(viewer, FrameLayout(activity))
    }

    private fun resetContentPreviewViewer() {
        runCatching {
            ContentPreviewViewer::class.java.getDeclaredField("Instance")
                .apply { isAccessible = true }.set(null, null)
        }
    }

    /** The shell's static instance count is process state; a test leaves it where it found it. */
    private fun resetShellCount() {
        runCatching {
            NativeDoActivity::class.java.getDeclaredField("activeInstanceCount")
                .apply { isAccessible = true }.setInt(null, 0)
        }
    }

    /**
     * The browser host is a process-wide seam too. A test that leaves a shell alive must not hand
     * its host to the next test, where it would answer for a dead activity.
     */
    private fun resetBrowserHost() {
        NativeDoHooks.setBrowserHost(null)
    }

    private fun topicFloorOf(fragment: BaseFragment): Int =
        fragment.arguments?.getInt("floor", 0) ?: 0

    // ------------------------------------------------------------------ shell traversal

    private fun findTabs(view: View?): MainTabsLayout? {
        if (view is MainTabsLayout) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTabs(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Taps the real upstream child through the strip's touch dispatch. */
    private fun tapTab(tabs: MainTabsLayout, index: Int) {
        harness.idle()
        val tab = tabs.getChildAt(index) as GlassTabView
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        tabs.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        tabs.dispatchTouchEvent(up)
        harness.idle()
    }
}
