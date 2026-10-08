package io.github.baba983.nativedo.topic

import android.text.style.URLSpan
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.web.DiscourseLinks
import io.github.baba983.nativedo.core.web.DiscourseTopicLink
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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ArticleViewer
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.LaunchActivity

/**
 * A link in a post, pressed while this shell hosts the app.
 *
 * <p>The failure this pins: upstream's `Browser.openUrl` finds its in-app browser through
 * `LaunchActivity`, and this client has none, so the link used to fall through to a custom tab or
 * the system browser - or, on a device without one, to nothing at all. The press has to land in
 * upstream's own `ArticleViewer` sheet on the screen the reader is on, and it must not make the
 * tgnet request (`messages.getWebPage`) upstream would wait on, because that request can never be
 * answered here.</p>
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
internal class PostLinkBrowserTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        // The post's id and its floor number are deliberately different, neither of them the floor
        // index: a shared floor address has to come from the number (`t/<topic>/<number>`), so a
        // link built from the id - or from the 1-based list position - is caught here.
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        val post = stream.getJSONArray("posts").getJSONObject(0)
        post.put("id", POST_ID)
        post.put("post_number", POST_NUMBER)
        post.put("cooked", "<p>Read <a href=\"$LINK\">the linked page</a> first.</p>")
        stream.put("stream", org.json.JSONArray().put(POST_ID))
        site.serveJson("t/$TOPIC_ID.json", base.toString())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun aPostLinkOpensInTheHostsInAppBrowserWithoutWakingTgnet() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val topic = TopicFragment.forTopic(TOPIC_ID, "Link")
        harness.present(topic)
        harness.await("the topic to load") { rowCount() == 1 }

        val launchInstance = LaunchActivity::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
        assertNull(
            "this test is about the case upstream does not handle",
            launchInstance.get(null),
        )

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        val attemptsBefore = ShadowConnectionsManager.REQUEST_ATTEMPTS
        // Robolectric starts an unresolvable ACTION_VIEW without complaint by default, which would
        // make Browser.openUrl take the "external app" branch this device does not have; strict
        // checking is what a real device does when nothing can handle the link.
        strictActivityResolution()
        cell.delegate.didPressUrl(cell, URLSpan(LINK), false)
        harness.idle()

        // Upstream opens the browser on its layout's own sheet fragment, not on the screen behind
        // it; the sheet is what has to be there.
        val layout = NativeDoHooks.getSafeLastFragment()?.parentLayout as? ActionBarLayout
        val sheetFragment = layout?.getSheetFragment(false)
        val sheet = sheetFragment?.getLastSheet() as? ArticleViewer.Sheet
        println("browser: sheet=$sheet hostTabs=${NativeDoHooks.getBottomSheetTabs()}")
        assertTrue(
            "the link did not reach upstream's in-app browser on this screen",
            sheet != null,
        )
        assertTrue(
            "the browser sheet is not on the layout's sheet stack",
            sheetFragment?.sheetsStack?.contains(sheet) == true,
        )
        assertEquals(
            "the browser asked Telegram for the article although tgnet cannot answer",
            attemptsBefore,
            ShadowConnectionsManager.REQUEST_ATTEMPTS,
        )

        // A link pressed inside the browser goes through ArticleViewer.openWebpageUrl; with no
        // tgnet it must load the new page directly instead of waiting on messages.getWebPage.
        val attemptsBeforeNavigation = ShadowConnectionsManager.REQUEST_ATTEMPTS
        sheet!!.getArticleViewer().openWebpageUrl(SECOND_LINK, null, null)
        harness.idle()
        assertEquals(
            "a link pressed inside the browser asked Telegram for the page",
            attemptsBeforeNavigation,
            ShadowConnectionsManager.REQUEST_ATTEMPTS,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun topicAndPostSharingUseSystemChooserWithCanonicalFloorLinks() {
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        val topic = TopicFragment.forTopic(TOPIC_ID, "Share")
        harness.present(topic)
        harness.await("post") { rowCount() == 1 }
        topic.actionBar.createMenu().getItem(1).performClick()
        harness.idle()
        io.github.baba983.nativedo.harness.clickMenuItem(
            io.github.baba983.nativedo.harness.latestItemOptionsMenu(harness.activity), "分享")
        fun sharedUrl(): String? {
            val chooser = org.robolectric.Shadows.shadowOf(harness.activity).nextStartedActivity
            assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals(android.content.Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            return send.getStringExtra(android.content.Intent.EXTRA_TEXT)
        }
        assertEquals(site.url("t/709"), sharedUrl())
        val cell = laidOutCells().first()
        cell.delegate.didLongPress(cell, 0f, 0f)
        io.github.baba983.nativedo.harness.clickMenuItem(
            io.github.baba983.nativedo.harness.latestItemOptionsMenu(harness.activity), "分享")
        assertEquals(site.url("t/709/$POST_NUMBER"), sharedUrl())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The four addresses the site gives a topic - bare id, id with a floor, slug with an id, and
     * slug with an id and a floor - read the same way the site routes them, and a link that only
     * looks like the site's is refused rather than guessed at.
     */
    @Test
    fun theDiscourseLinkReadsEverySiteShapeAndRefusesAnotherOrigin() {
        val base = "https://linux.do/"
        assertEquals(DiscourseTopicLink(101L, 0), DiscourseLinks.topicLink("https://linux.do/t/101", base))
        assertEquals(DiscourseTopicLink(101L, 3), DiscourseLinks.topicLink("https://linux.do/t/101/3", base))
        assertEquals(
            DiscourseTopicLink(101L, 0),
            DiscourseLinks.topicLink("https://linux.do/t/welcome-to-linux-do/101", base),
        )
        assertEquals(
            DiscourseTopicLink(101L, 3),
            DiscourseLinks.topicLink("https://linux.do/t/welcome-to-linux-do/101/3", base),
        )
        assertNull(DiscourseLinks.topicLink("https://evil.example.com/t/101/3", base))
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    /**
     * Makes Robolectric's instrumentation throw for an intent nothing resolves, the way a device
     * with no matching app does.
     */
    private fun strictActivityResolution() {
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
            .checkActivities(true)
    }

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 709L

        /** The post's id, deliberately not the floor [POST_NUMBER] it is addressed by. */
        const val POST_ID = 700700L
        const val POST_NUMBER = 3
        const val LINK = "https://example.com/linked-page"
        const val SECOND_LINK = "https://example.com/second-page"
    }
}
