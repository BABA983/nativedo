package io.github.baba983.nativedo.auth

import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.RecyclerListView

/**
 * The session gate and what the session does to the network: with no cookies the app opens the
 * login WebView, and once the session cookies are in the store every request carries them.
 *
 * <p>The cookies are seeded directly rather than by driving a real WebView: Robolectric's WebView
 * is a stub, and the requirement under test is that the app's *requests* carry `_t`,
 * `_forum_session` and `cf_clearance`, not that the site's own login page works.</p>
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
internal class SessionGateTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        baseUrl = site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
    }

    @After
    fun tearDown() {
        site.stop()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    /** No cookies: the gate must hand over to the login WebView, not to the topic list. */
    @Test
    fun withoutASessionTheGateOpensTheLoginScreen() {
        containerWithNoSession()

        harness.bootApp()
        harness.await("the gate to resolve") { currentFragmentName() != null }

        println("gate: fragment = " + currentFragmentName())
        assertEquals("LoginWebViewFragment", currentFragmentName())
        harness.assertNoTelegramSideEffects()
    }

    /** With session cookies the gate opens the topic list, and the request carries the cookies. */
    @Test
    fun withASessionTheGateOpensTheTopicListAndRequestsCarryTheCookies() {
        val container = containerWithSession()

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        println("gate: fragment = " + currentFragmentName())
        println("gate: cookie header = " + site.lastRequestCookieHeader)
        assertEquals("TopicListFragment", currentFragmentName())

        val cookieHeader = site.lastRequestCookieHeader
        assertNotNull("the request must carry the session cookies", cookieHeader)
        assertTrue("_t must be sent", cookieHeader!!.contains("_t="))
        assertTrue("_forum_session must be sent", cookieHeader.contains("_forum_session="))
        assertTrue("cf_clearance must be sent", cookieHeader.contains("cf_clearance="))

        // The store is the source of truth for the API client, so it must have kept them too.
        assertTrue("the cookie store kept the session", container.cookieStore.hasAuthenticatedCookies())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ fixtures

    private fun containerWithNoSession(): AppContainer {
        NativeDo.setContainerFactory { context -> AppContainer(context, baseUrl, null, null) }
        return NativeDo.container(harness.context)
    }

    private fun containerWithSession(): AppContainer {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        return NativeDo.container(harness.context)
    }

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragment(): Any? {
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        val stack = stackField.get(layout) as List<*>
        return stack.lastOrNull()
    }

    private fun currentFragmentName(): String? = currentFragment()?.javaClass?.simpleName

    private fun rowCount(): Int =
        contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TOPIC_ID = 101L
    }
}