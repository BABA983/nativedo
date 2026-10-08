package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.feature.topic.data.NewTopicDraftKey
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView

/**
 * The reply and new-topic boxes survive a screen that comes and goes.
 *
 * <p>The failure this pins: a reader's draft is handed to someone else's account or another topic,
 * is thrown away by a write the site refused, or is never written at all because the screen was
 * paused without a successful send. Each is a way the reader loses what they wrote with no way to
 * know it happened.</p>
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
internal class InputDraftTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        site.serveTopic(OTHER_TOPIC_ID) { post ->
            post.put("cooked", "<p>A different topic.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        container = site.authenticatedContainer(harness.context)
        NativeDo.setContainerFactory { container }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun aDraftIsRestoredOnlyForItsOwnAccountAndTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        harness.present(TopicFragment.forTopic(TOPIC_ID, "Draft"))
        harness.await("the topic to load") { rowCount() == 1 }
        composer().setFieldText("half-written thought")
        harness.idle()
        // The reader leaves without sending.
        harness.visibleFragment()?.onPause()
        harness.idle()

        val mine = container.topicReplyDraftStore.load(replyKey(TOPIC_ID))
        println("draft: saved for this account/topic = $mine")
        assertEquals("the field was saved on pause", "half-written thought", mine.raw)

        // Another topic is a different draft.
        harness.present(TopicFragment.forTopic(OTHER_TOPIC_ID, "Other"))
        harness.await("the other topic to load") { rowCount() == 1 }
        assertEquals(
            "another topic never sees this draft",
            "",
            composer().getFieldText()?.toString().orEmpty(),
        )

        // Another account is a different draft, even for the same topic.
        container.topicReplyDraftStore.save(
            TopicReplyDraftKey(site.baseUrl, "someone-else", TOPIC_ID),
            TopicReplyDraft(raw = "not mine", replyToPostNumber = 7),
        )
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Draft"))
        harness.await("the topic to load again") { rowCount() == 1 }
        assertEquals(
            "another account's draft is not handed over",
            "half-written thought",
            composer().getFieldText()?.toString().orEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSuccessfulReplyClearsTheDraftAndItsTarget() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        replyToFirstPost()
        composer().setFieldText("thanks, that helped")
        harness.idle()

        val stored = container.topicReplyDraftStore.load(replyKey(TOPIC_ID))
        println("draft: while editing = $stored")
        assertEquals("the reply target is part of the draft", 1, stored.replyToPostNumber)
        assertEquals("and the text", "thanks, that helped", stored.raw)

        composer().sendMessage()
        harness.idleClock(400)
        harness.await("the reply to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        harness.await("the accepted reply to clear the draft") {
            container.topicReplyDraftStore.load(replyKey(TOPIC_ID)) == TopicReplyDraft()
        }
        assertEquals(
            "a write the site accepted leaves nothing behind",
            TopicReplyDraft(),
            container.topicReplyDraftStore.load(replyKey(TOPIC_ID)),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedReplyKeepsTheDraftAndItsTarget() {
        site.serveResponse("posts.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        replyToFirstPost()
        composer().setFieldText("this will be refused")
        harness.idle()
        composer().sendMessage()
        harness.idleClock(600)
        harness.await("the site to refuse the reply") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        harness.await("the refusal to put the draft back") {
            container.topicReplyDraftStore.load(replyKey(TOPIC_ID)).raw == "this will be refused"
        }
        harness.idleClock(400)

        val stored = container.topicReplyDraftStore.load(replyKey(TOPIC_ID))
        println("draft: after a refusal = $stored")
        assertEquals("the write did not land, so the text stays", "this will be refused", stored.raw)
        assertEquals("and so does the post it answered", 1, stored.replyToPostNumber)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theNewTopicFormIsRestoredOnReopen() {
        site.serveFixture("categories.json", "categories.json")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the site's categories to arrive") { site.requestPaths.contains("categories.json") }

        harness.present(NewTopicFragment.create())
        harness.await("the composer of a new topic") { newTopicComposer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        fragment.titleField().setText(TITLE)
        newTopicComposer()!!.setFieldText(BODY)
        harness.idle()
        fragment.onPause()
        harness.idle()

        val stored = container.topicReplyDraftStore.loadNewTopic(newTopicKey())
        println("draft: new-topic, on pause = $stored")
        assertEquals("the title is stored", TITLE, stored.title)
        assertEquals("and the body", BODY, stored.raw)

        // Reopening the screen hands the form back.
        harness.present(NewTopicFragment.create())
        harness.await("the form to come back") { newTopicComposer() != null }
        val reopened = harness.visibleFragment() as NewTopicFragment
        assertEquals("the title comes back on reopen", TITLE, reopened.titleField().text.toString())
        assertEquals("and the body", BODY, newTopicComposer()!!.getFieldText()?.toString().orEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Selecting 回复 shows upstream's native reply strip above the field - never writing an @name
     * into it - and the answer the reader sends carries the target's floor number.
     */
    @Test
    fun replyingShowsTheNativeStripWithoutTouchingTheDraftAndSendsTheRightFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Native reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        val composer = composer()
        composer.setFieldText("a draft I already wrote")
        harness.idle()

        replyToFirstPost()
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals(
            "the reader's own draft is not overwritten to insert an @name",
            "a draft I already wrote",
            composer.getFieldText()?.toString().orEmpty(),
        )
        assertEquals("the target is written down with the draft", 1, readTarget())
        assertTrue("the native reply strip is shown", composer.isTopViewVisible())
        val strip = findReplyStrip(composer)
            ?: throw AssertionError("the composer hosted no reply strip")
        val name = strip.current().name.text.toString()
        val obj = strip.current().obj.text.toString()
        println("reply strip: name=\"$name\" obj=\"$obj\"")
        assertTrue("the strip names the post's author", name.contains("Baba"))
        assertTrue("and shows the post it answers", obj.contains("Hello from a linux.do post."))

        composer.setFieldText("the answer")
        harness.idle()
        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the reply to reach the site") { site.recordedRequests.any { it == "POST /posts.json" } }
        val fields = site.requestBodyFor("posts.json")!!.split('&').associate { part ->
            part.substringBefore('=') to java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }
        assertEquals("the answer went to the floor the strip named", "1", fields["reply_to_post_number"])
        assertEquals("with the text the reader typed", "the answer", fields["raw"])
        harness.assertNoTelegramSideEffects()
    }

    /** Cancelling the strip forgets the target but never the text the reader wrote. */
    @Test
    fun cancellingTheReplyStripForgetsTheTargetButKeepsTheDraft() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Cancel reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        val composer = composer()
        composer.setFieldText("keep me")
        harness.idle()
        replyToFirstPost()
        harness.idleClock(400)
        harness.layoutApp()
        assertEquals(1, readTarget())

        val strip = findReplyStrip(composer) ?: throw AssertionError("no reply strip")
        // The close button is the reply strip's sibling inside the top view, not part of the container.
        val close = findCloseButton(composer) ?: throw AssertionError("the strip has no close button")
        close.performClick()
        harness.idleClock(400)
        harness.layoutApp()

        assertTrue("the strip is put away", !composer.isTopViewVisible())
        assertEquals("the target is forgotten", null, readTarget())
        assertEquals("and the draft is untouched", "keep me", composer.getFieldText()?.toString().orEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A restored draft that names a floor the loaded window does not carry. The strip names the
     * floor at first, and once the site answers the named post the strip shows the real author and
     * text - not the floor number forever.
     */
    @Test
    fun aRestoredDraftTargetIsReadAndTheStripNamesItsAuthor() {
        site.serveJson("t/$TOPIC_ID.json", windowJson((8..16).toList()))
        site.serveJson("posts/by_number/$TOPIC_ID/2.json", JSONObject().apply {
            put("id", 1002); put("post_number", 2); put("username", "u2"); put("name", "User 2")
            put("created_at", "2024-01-02T10:00:00.000Z"); put("cooked", "<p>the draft target body</p>")
            put("avatar_template", "")
        }.toString())
        container.topicReplyDraftStore.save(
            TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID),
            TopicReplyDraft(raw = "", replyToPostNumber = 2),
        )

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Restored target"))
        harness.await("the topic to load") { rowCount() == 9 }

        harness.await("the restored target to be read") {
            site.requestPaths.contains("posts/by_number/$TOPIC_ID/2.json")
        }
        harness.await("the strip to name the resolved author") {
            findReplyStrip(composer())?.current()?.name?.text?.toString()?.contains("User 2") == true
        }
        harness.layoutApp()
        val strip = findReplyStrip(composer()) ?: throw AssertionError("the composer hosted no reply strip")
        println("reply strip: restored name=" + strip.current().name.text)
        assertTrue("the strip names the resolved author, not just the floor number",
            strip.current().name.text.toString().contains("User 2"))
        assertTrue("and shows the target's text",
            strip.current().obj.text.toString().contains("the draft target body"))
        harness.assertNoTelegramSideEffects()
    }

    private fun windowJson(floors: List<Int>): String {
        val posts = JSONArray()
        for (floor in floors) {
            posts.put(JSONObject().apply {
                put("id", 1000L + floor); put("post_number", floor)
                put("username", "u$floor"); put("name", "User $floor")
                put("created_at", "2024-01-01T10:00:00.000Z")
                put("cooked", "<p>post number $floor</p>"); put("avatar_template", "")
            })
        }
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", posts)
        stream.put("stream", JSONArray((1..20).map { 1000L + it }))
        return base.toString()
    }

    private fun readTarget(): Int? =
        container.topicReplyDraftStore.load(replyKey(TOPIC_ID)).replyToPostNumber

    private fun findReplyStrip(view: View): org.telegram.ui.Components.ChatReplyContainer? = when (view) {
        is org.telegram.ui.Components.ChatReplyContainer -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findReplyStrip(view.getChildAt(it)) }
        else -> null
    }

    private fun findCloseButton(view: View): View? {
        if (view.contentDescription == "取消回复") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findCloseButton(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** The post menu's 回复 on the first floor, exactly as the reader reaches it. */
    private fun replyToFirstPost() {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "回复")
        harness.idle()
    }

    private fun replyKey(topicId: Long) = TopicReplyDraftKey(site.baseUrl, "baba", topicId)

    private fun newTopicKey() = NewTopicDraftKey(site.baseUrl, "baba")

    @Test
    fun anOpenComposersDraftDoesNotMoveToTheNextAccountWhenTheSessionChanges() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Account"))
        harness.await("the topic") { rowCount() == 1 }
        composer().setFieldText("private draft")
        harness.idle()
        val original = container.sessionStore.snapshot()!!
        container.sessionStore.save(original.copy(username = "other-reader"))
        harness.visibleFragment()!!.onPause()
        assertEquals("the draft stays with its original author", "private draft",
            container.topicReplyDraftStore.load(replyKey(TOPIC_ID)).raw)
        assertEquals("the next account cannot inherit an open field", TopicReplyDraft(),
            container.topicReplyDraftStore.load(TopicReplyDraftKey(site.baseUrl, "other-reader", TOPIC_ID)))
        harness.assertNoTelegramSideEffects()
    }

    private fun composer(): ChatActivityEnterView = composerIn() ?: error("the screen has no composer")

    private fun newTopicComposer(): ChatActivityEnterView? = composerIn()

    private fun composerIn(): ChatActivityEnterView? {
        val view = harness.visibleFragment()?.fragmentView ?: return null
        return viewsUnder(view).filterIsInstance<ChatActivityEnterView>().lastOrNull()
    }

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val view = harness.visibleFragment()?.fragmentView ?: return emptyList()
        val list = viewsUnder(view).filterIsInstance<org.telegram.ui.Components.RecyclerListView>()
            .firstOrNull() ?: return emptyList()
        val out = mutableListOf<org.telegram.ui.Cells.ChatMessageCell>()
        for (index in 0 until list.childCount) {
            (list.getChildAt(index) as? org.telegram.ui.Cells.ChatMessageCell)?.let { out += it }
        }
        return out
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

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TOPIC_ID = 2790689L
        const val OTHER_TOPIC_ID = 2790001L
        const val TITLE = "构建脚本大家都是怎么组织的"
        const val BODY = "第一次在这里发帖。"
    }
}
