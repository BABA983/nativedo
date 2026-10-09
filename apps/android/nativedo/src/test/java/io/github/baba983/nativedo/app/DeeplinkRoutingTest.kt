package io.github.baba983.nativedo.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.telegram.messenger.ApplicationLoader

/**
 * The app's own topic links: where they resolve, and how a cold start and a warm delivery route
 * through the session gate without opening private content early or stacking the topic twice.
 *
 * <p>The app is not a Telegram client, so no activity of this package may claim a Telegram entry
 * route (`tg`, `tgb`, `tonsite`, `telegram.me`, `telegram.dog`, `t.me`); that is checked through
 * the {@code PackageManager} against the merged manifest, not by reading the XML.</p>
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
internal class DeeplinkRoutingTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serve(1L, "topic-detail.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        resetShellCount()
        NativeDoHooks.setBrowserHost(null)
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        resetShellCount()
        NativeDoHooks.setBrowserHost(null)
        site.stop()
    }

    // ------------------------------------------------------------------ the manifest's routes

    /**
     * No activity of this package claims a Telegram entry route. This is the merged manifest read
     * through the platform resolver: if the removal overrides ever stop applying, the upstream
     * LaunchActivity/ShareActivity filters come back and this goes red.
     */
    @Test
    fun noActivityInThisPackageClaimsATelegramEntryRoute() {
        val telegramRoutes = listOf(
            "tg://resolve?domain=telegram",
            "tgb://open",
            "tonsite://open",
            "https://t.me/telegram",
            "https://telegram.me/telegram",
            "https://telegram.dog/telegram",
        )
        for (url in telegramRoutes) {
            val claimers = claimersInThisPackage(url)
            println("deeplink: $url -> $claimers")
            assertTrue("no activity of this package may claim $url: $claimers", claimers.isEmpty())
        }
    }

    /** The app's own routes resolve to the shell: the site URL and both custom-scheme aliases. */
    @Test
    fun theAppOwnedRoutesResolveToTheShell() {
        val appRoutes = listOf(
            "https://linux.do/t/welcome-to-linux-do/123",
            "https://linux.do/t/123/4",
            "nativedo://topic/123",
            "nativedo://topic/123/4",
            "linuxdo://topic/123",
            "linuxdo://topic/123/4",
            "nativedo://linux.do/t/123/4",
        )
        for (url in appRoutes) {
            val names = claimersInThisPackage(url)
            println("deeplink: $url -> $names")
            assertTrue(
                "$url should resolve to the shell, got $names",
                names.any { it.endsWith("NativeDoActivity") },
            )
        }
    }

    // ------------------------------------------------------------------ navigation

    /** A cold start by custom-scheme link opens the topic once the gate has answered. */
    @Test
    fun aColdStartCustomSchemeLinkOpensTheTopicAfterTheGate() {
        val shell = harness.bootApp(viewIntent("nativedo://topic/1/3"))
        harness.await("the linked topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the cold-start link opened its topic", 1L, topicIdOf(shell))
        assertEquals("on the floor it named", 3, topicFloorOf(shell))
        assertTrue(
            "the tab shell is behind it, so back returns to the feed",
            harness.fragmentStack().any { it.javaClass.simpleName == "NativeDoMainTabsActivity" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A link delivered to the running shell (`onNewIntent`) opens the topic on the floor it names. */
    @Test
    fun aWarmCustomSchemeLinkOpensTheTopicItNames() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }

        controllerOf(shell).newIntent(viewIntent("linuxdo://topic/1/3"))
        harness.await("the linked topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the warm link opened its topic", 1L, topicIdOf(shell))
        assertEquals("on the floor it named", 3, topicFloorOf(shell))
        harness.assertNoTelegramSideEffects()
    }

    /** A link that arrives while signed out stays pending: it opens only after login completes. */
    @Test
    fun aColdStartLinkWhileSignedOutWaitsForLogin() {
        NativeDo.setContainerFactory { context -> AppContainer(context, site.baseUrl, null, null) }
        harness.bootApp(viewIntent("nativedo://topic/1/3"))
        harness.await("the login gate") { harness.visibleFragmentName() == "LoginWebViewFragment" }
        assertFalse(
            "the link did not open private content before the gate let it through",
            harness.fragmentStack().any { it.javaClass.simpleName == "TopicFragment" },
        )

        completeLoginWithTheSite()
        harness.await("the topic the link held") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        assertEquals("the waiting link opens its topic", 1L, topicIdOf(harness.activity as NativeDoActivity))
        assertEquals("and the floor it named", 3, topicFloorOf(harness.activity as NativeDoActivity))
        harness.assertNoTelegramSideEffects()
    }

    /** A recreation after a cold-start link comes back to exactly one copy of the topic. */
    @Test
    fun aRecreationAfterAColdStartLinkOpensTheTopicOnce() {
        val shell = harness.bootApp(viewIntent("nativedo://topic/1/3"))
        harness.await("the linked topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }

        recreateShell(shell)
        harness.await("the restored topic") {
            harness.topFragment()?.javaClass?.simpleName == "TopicFragment"
        }
        harness.layoutApp()
        val topics = harness.fragmentStack().filter { it.javaClass.simpleName == "TopicFragment" }
        assertEquals("the topic is restored exactly once", 1, topics.size)
        assertEquals("and it is the linked topic", 1L, topicIdOf(harness.activity as NativeDoActivity))
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /** The activities of this package the platform resolver reports for [url]. */
    private fun claimersInThisPackage(url: String): List<String> {
        val intent = viewIntent(url)
        return harness.context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName == harness.context.packageName }
            .map { it.activityInfo.name }
    }

    private fun viewIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun topicIdOf(shell: NativeDoActivity): Long =
        harness.topFragment()!!.arguments?.getLong("topic_id", 0L) ?: 0L

    private fun topicFloorOf(shell: NativeDoActivity): Int =
        harness.topFragment()!!.arguments?.getInt("floor", 0) ?: 0

    /** Drives the login WebView the way the site's own page does (see ShellRecreationTest). */
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

    private fun recreateShell(shell: NativeDoActivity) {
        val controller = controllerOf(shell)
        val state = Bundle()
        controller.saveInstanceState(state)
        controller.pause().stop().destroy()
        val recreated = withFrameClockHeld {
            Robolectric.buildActivity(NativeDoActivity::class.java).setup(state)
        }
        harness.idle()
        harness.activity = recreated.get()
    }

    private fun <T> withFrameClockHeld(block: () -> T): T {
        val paused = org.robolectric.shadows.ShadowChoreographer.isPaused()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        return try {
            block()
        } finally {
            org.robolectric.shadows.ShadowChoreographer.setPaused(paused)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun controllerOf(activity: Activity): ActivityController<NativeDoActivity> {
        val shadow = shadowOf(activity)
        val field = shadow.javaClass.getDeclaredField("controller").apply { isAccessible = true }
        return field.get(shadow) as ActivityController<NativeDoActivity>
    }

    private fun resetShellCount() {
        runCatching {
            NativeDoActivity::class.java.getDeclaredField("activeInstanceCount")
                .apply { isAccessible = true }.setInt(null, 0)
        }
    }
}
