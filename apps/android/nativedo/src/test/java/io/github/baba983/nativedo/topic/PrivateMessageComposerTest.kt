package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
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
import io.github.baba983.nativedo.profile.ProfileActionsView
import io.github.baba983.nativedo.profile.ProfileFragment
import org.json.JSONObject
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
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import java.net.URLDecoder

/**
 * Starting a private message: the composer's private-message mode, and the two entry points that
 * reach it (a profile's 私信 action and the inbox's new-message action).
 *
 * <p>The failures pinned here: a private message sent on the wrong wire (the site's route is
 * {@code POST /posts.json} with {@code archetype=private_message} and {@code target_recipients} -
 * never {@code target_usernames}, and with no category), a refusal that is swallowed instead of
 * shown, a writer whose recipients, title or body are lost on a failed write, a success that does
 * not open the topic the site created, and a profile 私信 that leaves the native client for the
 * site's rendered page.</p>
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
internal class PrivateMessageComposerTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun sendingAPrivateMessageCarriesTitleBodyArchetypeAndRecipients() {
        site.serveJson("posts.json", harness.fixture("private-message-post.json"))
        site.serve(createdTopicId(), "topic-detail.json")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        harness.present(NewTopicFragment.privateMessage("alice"))
        harness.await("the private-message composer") { composer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        assertTrue("the form is in private-message mode", fragment.isPrivateMessageMode())
        assertEquals(
            "the profile's recipient is filled in",
            "alice",
            fragment.recipientsField()?.text?.toString(),
        )
        assertNull("a private message has no category row", fragment.categoryRow())

        fragment.titleField()?.setText(PM_TITLE)
        val composer = composer() ?: throw AssertionError("the composer is gone")
        composer.setFieldText(PM_BODY)
        harness.idle()
        assertEquals(
            "a body is what the composer's own send button stands for",
            View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.layoutApp()
        harness.renderApp("private-message-composer")

        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the site to take the message") {
            site.recordedRequests.contains("POST /posts.json")
        }

        val body = site.requestBodyFor("posts.json")
        println("private message: body = $body csrf = " + site.csrfHeaderFor("posts.json"))
        assertNotNull("the message carried a body", body)
        val fields = body!!.split('&').associate { field ->
            field.substringBefore('=') to URLDecoder.decode(field.substringAfter('='), "UTF-8")
        }
        assertEquals("the title travels as the site's own field", PM_TITLE, fields["title"])
        assertEquals("the body as raw", PM_BODY, fields["raw"])
        assertEquals(
            "the archetype routes it to a private message",
            "private_message",
            fields["archetype"],
        )
        assertEquals(
            "the recipients travel as the site's own target_recipients",
            "alice",
            fields["target_recipients"],
        )
        assertNull("not the old target_usernames field", fields["target_usernames"])
        assertNull("and no category, which a private message does not have", fields["category"])
        assertNull("the site is making the topic, so there is no topic_id", fields["topic_id"])
        assertEquals(
            "with the token the site asks its writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("posts.json"),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSentPrivateMessageOpensTheTopicTheSiteCreated() {
        site.serveJson("posts.json", harness.fixture("private-message-post.json"))
        site.serve(createdTopicId(), "topic-detail.json")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        // The inbox's new-message action knows no recipient, so the field starts empty.
        harness.present(NewTopicFragment.privateMessage(null))
        harness.await("the private-message composer") { composer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        fragment.titleField()?.setText(PM_TITLE)
        fragment.recipientsField()?.setText("alice")
        val composer = composer() ?: throw AssertionError("the composer is gone")
        composer.setFieldText(PM_BODY)
        harness.idle()

        composer.sendMessage()
        harness.await("the topic the site created") {
            site.requestPaths.contains("t/${createdTopicId()}.json")
        }
        harness.await("the composer replaced by the created topic") {
            harness.visibleFragmentName() == "TopicFragment"
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A private message the site refuses. The site's own message is the only thing that says what to
     * change, and nothing may be treated as sent - the recipients, the title and the body all stay
     * for the reader to fix.
     */
    @Test
    fun aRefusedPrivateMessageIsWhatTheReaderIsToldAndNothingIsLost() {
        site.serveResponse("posts.json") {
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        harness.present(NewTopicFragment.privateMessage("blocked-user"))
        harness.await("the private-message composer") { composer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        fragment.titleField()?.setText(PM_TITLE)
        val composer = composer() ?: throw AssertionError("the composer is gone")
        composer.setFieldText(PM_BODY)
        harness.idle()

        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the site to refuse the message") {
            site.recordedRequests.contains("POST /posts.json")
        }
        harness.await("the reader to be told what the site said") {
            shownText().any { text -> text.contains("标题过短") }
        }
        println("private message: the reader is told " +
            shownText().filter { it.contains("标题过短") })
        // Nothing was sent, so the form stands with what the reader wrote.
        assertNotNull("the composer stays for the reader to fix it", composer())
        assertEquals("the body is not discarded", PM_BODY, composer.getFieldText()?.toString())
        assertEquals(
            "nor are the recipients",
            "blocked-user",
            fragment.recipientsField()?.text?.toString(),
        )
        assertEquals("nor is the title", PM_TITLE, fragment.titleField()?.text?.toString())
        harness.layoutApp()
        harness.renderComposite(".amp/in/artifacts/nativedo/private-message-refused.png")
        assertTrue(
            "and no topic was opened, because nothing was sent",
            !site.requestPaths.contains("t/${createdTopicId()}.json"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The profile's 私信 action no longer leaves the client for the site's rendered
     * {@code /new-message} page: it opens the native composer with the profile's own user already in
     * the recipients field. The site still owns whether the message may be sent.
     */
    @Test
    fun theProfileMessageActionOpensTheNativeComposerWithTheRecipient() {
        val profile = JSONObject(harness.fixture("user-profile.json"))
        profile.getJSONObject("user").apply {
            put("username", "alice")
            put("name", "alice")
            put("can_send_private_message_to_user", true)
        }
        site.serveJson("u/alice.json", profile.toString())

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        harness.present(ProfileFragment.forUser("alice", "alice"))
        harness.await("the profile and its 私信 action") {
            (harness.visibleFragment() as? ProfileFragment)
                ?.hasAction(ProfileActionsView.KEY_MESSAGE) == true
        }
        val profileFragment = harness.visibleFragment() as ProfileFragment
        profileFragment.onActionClicked(ProfileActionsView.KEY_MESSAGE, 0f, 0f)

        harness.await("the native composer") {
            harness.visibleFragmentName() == "NewTopicFragment"
        }
        val composer = harness.visibleFragment() as NewTopicFragment
        assertTrue("it is the private-message mode", composer.isPrivateMessageMode())
        assertEquals(
            "the profile's user is the recipient",
            "alice",
            composer.recipientsField()?.text?.toString(),
        )
        assertNull("a private message has no category row", composer.categoryRow())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aPrivateDraftCannotBeSentByAnotherAccount() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(NewTopicFragment.privateMessage("alice"))
        harness.await("the private composer") { composer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        fragment.titleField()!!.setText(PM_TITLE)
        composer()!!.setFieldText(PM_BODY)
        val container = NativeDo.container(harness.context)
        container.sessionStore.save(container.sessionStore.snapshot()!!.copy(username = "another-reader"))

        composer()!!.sendMessage()
        harness.idleClock(300)

        assertTrue("the old reader's draft must not be posted as the new reader",
            site.recordedRequests.none { it == "POST /posts.json" })
        assertEquals("the original draft text is retained", PM_BODY, composer()!!.fieldText.toString())
    }

    @Test
    fun aLateMessageFromAnEarlierLoginCannotOpenATopicInTheNewSession() {
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("posts.json") {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("private-message-post.json"))
        }
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(NewTopicFragment.privateMessage("alice"))
        harness.await("the private composer") { composer() != null }
        val fragment = harness.visibleFragment() as NewTopicFragment
        fragment.titleField()!!.setText(PM_TITLE)
        composer()!!.setFieldText(PM_BODY)
        composer()!!.sendMessage()
        harness.await("the in-flight message") { site.recordedRequests.contains("POST /posts.json") }
        kotlinx.coroutines.runBlocking {
            NativeDo.container(harness.context).sessionCoordinator.markExpired(
                io.github.baba983.nativedo.core.session.SessionExpiredReason.RecoveryFailed)
        }
        gate.countDown()
        val sending = NewTopicFragment::class.java.getDeclaredField("sending").apply { isAccessible = true }
        harness.await("the response to finish") { !sending.getBoolean(fragment) }
        assertEquals("the old session's response cannot navigate", fragment, harness.visibleFragment())
        assertTrue("no created topic is read for the new session",
            site.requestPaths.none { it == "t/${createdTopicId()}.json" })
    }

    // ------------------------------------------------------------------ helpers

    private fun createdTopicId(): Long = 5300L

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<ChatActivityEnterView>().firstOrNull()
    }

    private fun rowCount(): Int =
        io.github.baba983.nativedo.harness.contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    /** Every piece of text the screen shows, dialogs included. */
    private fun shownText(): List<String> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(content, out)
        (ShadowDialog.getLatestDialog() as? android.app.Dialog)?.window?.decorView?.let { dialog ->
            collectText(dialog, out)
        }
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
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

    private companion object {
        const val PM_TITLE = "关于构建脚本的一点问题"
        const val PM_BODY = "你好，想请教一下你的构建脚本是怎么组织的。"
    }
}
