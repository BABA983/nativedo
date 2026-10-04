package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import java.net.URLDecoder

/**
 * Replying, end to end: upstream's composer, and the post it sends to Discourse.
 *
 * <p>The failure this pins: a reply that reaches the site without the CSRF token, or with the wrong
 * topic or floor, is accepted by nobody — the composer's send button is the only place the reader
 * can see that the text left.</p>
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
internal class ReplyTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        // The site answers a created post with the post it created.
        site.serveJson("posts.json", createdPostJson())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theComposerSendsTheReplyToTheSiteWithTheSessionAndTheFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        val composer = composer() ?: throw AssertionError("the screen has no composer")
        println("reply: composer = " + composer.javaClass.name)
        // An empty field would offer Telegram's voice button, and this client has no voice behind it.
        harness.renderApp("topic-reply-empty")
        assertEquals(
            "no button while there is nothing to send",
            android.view.View.GONE,
            composer.getSendButton()?.visibility,
        )

        // Answering the first post, the way the post menu does.
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        val rows = latestItemOptionsMenu(harness.activity)
        assertTrue("the post menu opened", rows.isNotEmpty())
        clickMenuItem(rows, "回复")
        harness.idle()
        println("reply: after the menu picked a reply, the field is \"" + composer.fieldText + "\"")
        composer.setFieldText("thanks, that helped")
        harness.idle()
        assertEquals(
            "and a send button as soon as there is",
            android.view.View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.renderApp("topic-reply")

        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the reply to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }

        val body = site.requestBodyFor("posts.json")
        println("reply: body = $body csrf = " + site.csrfHeaderFor("posts.json"))
        assertNotNull("the reply carried a body", body)
        val fields = body!!.split('&').associate { part ->
            val name = part.substringBefore('=')
            name to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }
        assertEquals("the reply went to this topic", TOPIC_ID.toString(), fields["topic_id"])
        assertEquals("with the text the reader typed", "thanks, that helped", fields["raw"])
        assertEquals("as an answer to the floor the menu picked", "1", fields["reply_to_post_number"])
        assertEquals("and with the token the site asks writes for", "csrf-token-demo", site.csrfHeaderFor("posts.json"))
        assertTrue("the composer was cleared", composer.fieldText.isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reply the edge challenged. One press sends one write, and a challenge ends it: the failure
     * is shown at once, the reader's draft is kept, and the way through is a verification page they
     * open themselves - nothing replays a write whose outcome is unknown.
     */
    @Test
    fun aReplyTheEdgeChallengedIsSentOnceKeepsTheDraftAndOffersTheVerificationPage() {
        site.serveResponse("posts.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head></html>")
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        val composer = composer() ?: throw AssertionError("the screen has no composer")
        val marker = "NativeDo verification reply marker"
        composer.setFieldText(marker)
        harness.idle()
        composer.sendMessage()
        harness.idleClock(400)

        val dialog = awaitReplyFailureDialog()
        assertEquals("one press sent one write", 1, postAttempts())
        // Several more ticks and a debounce window: a challenge must not be resent on a timer.
        harness.idleClock(5_000)
        harness.idle()
        java.util.concurrent.locks.LockSupport.parkNanos(300_000_000L)
        harness.idleClock(5_000)
        assertEquals("and no automatic resend", 1, postAttempts())

        assertEquals("the reader's draft is kept", marker, composer.fieldText.toString())
        assertTrue(
            "the dialog offers the verification page",
            dialogButtonTexts(dialog).any { text -> text.contains("验证") },
        )
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell) as org.telegram.ui.ActionBar.INavigationLayout
        assertEquals("the action is verification, not a generic dismissal", "打开验证页",
            (dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE) as android.widget.TextView).text.toString())
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        harness.idle()
        harness.await("verification opened from the reply failure") {
            layout.fragmentStack.last().javaClass.simpleName == "LoginWebViewFragment"
        }
        val verification = layout.fragmentStack.last()
        assertEquals("LoginWebViewFragment", verification.javaClass.simpleName)
        val web = verification.javaClass.getDeclaredField("webView").apply { isAccessible = true }
            .get(verification) as android.webkit.WebView
        val shadow = org.robolectric.Shadows.shadowOf(web)
        harness.await("the verification navigation") { shadow.lastLoadedUrl != null }
        web.loadUrl(site.baseUrl)
        shadow.webViewClient.onPageStarted(web, site.baseUrl, null)
        shadow.webViewClient.onPageFinished(web, site.baseUrl)
        harness.idle()
        assertEquals("old auth must not complete recovery on page load", verification, layout.fragmentStack.last())
        shell.onBackPressed()
        harness.idle()
        assertEquals("cancel returns to the same topic", "TopicFragment", layout.fragmentStack.last().javaClass.simpleName)
        assertEquals("the original composer and draft survive", marker, composer.fieldText.toString())
        harness.idleClock(12_000)
        assertEquals("opening and cancelling verification never replays the reply", 1, postAttempts())
        harness.assertNoTelegramSideEffects()
    }

    /** The reply-failure dialog, which may not be the last dialog another test left behind. */
    private fun awaitReplyFailureDialog(): org.telegram.ui.ActionBar.AlertDialog {
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layout = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
            .get(shell) as org.telegram.ui.ActionBar.INavigationLayout
        repeat(60) {
            harness.idleClock(100)
            harness.idle()
            val shown = layout.fragmentStack.last().visibleDialog as? org.telegram.ui.ActionBar.AlertDialog
            if (shown != null && shown.isShowing && "回复失败" in dialogButtonTexts(shown)) {
                return shown
            }
            java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L)
        }
        throw AssertionError("the reply failure was never shown")
    }

    private fun dialogButtonTexts(dialog: org.telegram.ui.ActionBar.AlertDialog): List<String> {
        val out = mutableListOf<String>()
        collectText(dialog.window?.decorView, out)
        return out
    }

    private fun collectText(view: android.view.View?, out: MutableList<String>) {
        when (view) {
            is android.widget.TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
    }

    private fun postAttempts(): Int =
        site.recordedRequests.count { request -> request == "POST /posts.json" }

    private fun createdPostJson(): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        return base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0).toString()
    }

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findComposer(content)
    }

    private fun findComposer(view: android.view.View): ChatActivityEnterView? {
        if (view is ChatActivityEnterView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 808L
    }
}
