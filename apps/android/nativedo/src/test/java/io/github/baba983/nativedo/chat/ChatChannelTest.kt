package io.github.baba983.nativedo.chat

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
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme

/**
 * One public chat channel's conversation: the messages, the composer, joining and sending.
 *
 * <p>The failure modes pinned here: a channel the reader has not joined must offer to join rather
 * than send; a send's acknowledgement is an id, not the cooked message, so the screen reads it back
 * (and must not invent or replay the message when the readback fails); a refused write must keep the
 * reader's text rather than drop it; and a channel the site will not answer is its own state.</p>
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
internal class ChatChannelTest {

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
    fun theConversationShowsTheSitesMessagesAndAComposer() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the messages") { fragment.messageCount() == 3 }
        assertTrue("a joined, open channel offers the composer", fragment.isComposerShown())

        harness.layoutApp()
        harness.idleClock(400)
        harness.renderApp("chat-conversation-light")

        val original = Theme.getCurrentTheme()
        val toDark = !Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(if (toDark) "Dark Blue" else "Blue"),
            false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the theme to switch") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            harness.idleClock(400)
            harness.layoutApp()
            harness.renderApp("chat-conversation-night")
        } finally {
            Theme.applyTheme(original, false, false)
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A channel the reader is not in offers to join, not to send: the composer stays away until the
     * site answers the join - the site, not the client, decides who may join.
     */
    @Test
    fun joiningAChannelBringsTheComposer() {
        val joined = AtomicBoolean(false)
        site.serveJsonFor("chat/api/channels/72.json") { _ ->
            if (joined.get()) joinedLoungeFixture() else harness.fixture("chat-channel-unjoined.json")
        }
        site.serveJson("chat/api/channels/72/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson(
            "chat/api/channels/72/memberships/me.json",
            harness.fixture("chat-membership.json"),
        )
        site.serveJson("chat/api/channels/72/read.json", harness.fixture("chat-read.json"))

        val fragment = openChannel(72L, "闲聊")
        harness.await("the channel") { fragment.channelForTest() != null }
        assertFalse("a channel the reader has not joined offers no composer", fragment.isComposerShown())

        // The site answers the join with a membership; the re-read then carries it, and the
        // composer follows the site's answer rather than the tap.
        joined.set(true)
        fragment.joinForTest()
        harness.await("the composer after joining") { fragment.isComposerShown() }
        assertTrue(
            "joining is the site's own membership route",
            site.requestPaths.contains("chat/api/channels/72/memberships/me.json"),
        )
        harness.layoutApp()
        harness.idleClock(400)
        harness.renderApp("chat-join")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A send's acknowledgement carries an id, not the cooked message: the screen reads the accepted
     * id back through the messages route rather than drawing the write itself.
     */
    @Test
    fun sendingAMessageReadsTheAcceptedIdBack() {
        serveJoinedChannel()
        site.serveJsonFor("chat/api/channels/71/messages.json") { path ->
            // Discourse's past cursor excludes its anchor. Only an around-target read includes
            // the accepted message; returning it for direction=past would mask a wire bug.
            if (path.contains("target_message_id=9004") && !path.contains("direction=")) {
                harness.fixture("chat-messages-sent.json")
            } else {
                harness.fixture("chat-messages.json")
            }
        }
        site.serveJson("chat/71.json", harness.fixture("chat-message-sent.json"))

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the first window") { fragment.messageCount() == 3 }

        fragment.setComposerTextForTest("这条是我刚发的")
        fragment.sendForTest("这条是我刚发的")
        harness.await("the accepted message read back") { fragment.messageCount() == 4 }
        assertEquals(
            "the write went to the chat controller route, once",
            1,
            site.requestPaths.count { it == "chat/71.json" },
        )
        assertTrue(
            "the readback asked for the accepted id",
            site.queriesFor("chat/api/channels/71/messages.json").any { it.contains("target_message_id=9004") },
        )
        assertEquals("an accepted message is not left ready to send again", "", fragment.composerTextForTest())
        harness.layoutApp()
        harness.idleClock(400)
        harness.renderApp("chat-send")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun losingTheSessionHidesTheComposerWithoutDroppingItsOwnersDraft() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        val fragment = openChannel(71L, "站点反馈")
        harness.await("the messages") { fragment.messageCount() == 3 }
        fragment.setComposerTextForTest("只属于旧账号的草稿")
        val container = NativeDo.container(harness.context)
        kotlinx.coroutines.runBlocking {
            container.sessionCoordinator.markExpired(
                io.github.baba983.nativedo.core.session.SessionExpiredReason.CookieMissing,
            )
        }
        harness.await("the expired-session screen") { fragment.stateForTest() != null }
        assertFalse("the old account's send field is hidden", fragment.isComposerShown())
        assertEquals("old private draft text is not displayed", "", fragment.composerTextForTest())
        assertEquals("the original owner can still restore the draft", "只属于旧账号的草稿",
            container.chatDraftStore.load(io.github.baba983.nativedo.feature.chat.data.ChatDraftKey(
                site.baseUrl, "baba", 71L,
            )).raw)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aSessionLostDuringCsrfAcquisitionNeverPostsTheOldDraft() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        val fragment = openChannel(71L, "站点反馈")
        harness.await("the messages") { fragment.messageCount() == 3 }
        harness.idleClock(200)
        val container = NativeDo.container(harness.context)
        val expired = AtomicBoolean(false)
        site.serveJsonFor("session/csrf") {
            kotlinx.coroutines.runBlocking {
                container.sessionCoordinator.markExpired(
                    io.github.baba983.nativedo.core.session.SessionExpiredReason.CookieMissing,
                )
            }
            expired.set(true)
            """{"csrf":"old-session-token"}"""
        }
        site.serveJson("chat/71.json", harness.fixture("chat-message-sent.json"))
        fragment.setComposerTextForTest("不能用下一会话发送")
        fragment.sendForTest("不能用下一会话发送")
        harness.await("the session lost during csrf") { expired.get() && fragment.stateForTest() != null }
        harness.idleClock(300)
        assertFalse("no write follows a stale csrf response", site.requestPaths.contains("chat/71.json"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A readback that failed must not make the client resend the message: the write already landed,
     * and replaying it would post a second copy. The screen keeps what it had.
     */
    @Test
    fun aReadbackThatFailsDoesNotResendTheMessage() {
        serveJoinedChannel()
        site.serveResponseByQuery("chat/api/channels/71/messages.json") { path ->
            if (path.contains("target_message_id=9004")) {
                MockResponse().setResponseCode(500).setBody("""{"errors":["boom"]}""")
            } else {
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("chat-messages.json"))
            }
        }
        site.serveJson("chat/71.json", harness.fixture("chat-message-sent.json"))

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the first window") { fragment.messageCount() == 3 }

        fragment.setComposerTextForTest("这条不能发两次")
        fragment.sendForTest("这条不能发两次")
        harness.await("the write to be recorded") {
            site.requestPaths.count { it == "chat/71.json" } == 1
        }
        harness.idleClock(300)

        assertEquals(
            "the failed readback never replays the write",
            1,
            site.requestPaths.count { it == "chat/71.json" },
        )
        assertEquals("the client does not invent the message it could not read", 3, fragment.messageCount())
        harness.assertNoTelegramSideEffects()
    }

    /** A refused write keeps the reader's text, so it can be fixed and sent again - never dropped. */
    @Test
    fun aRefusedWriteKeepsTheText() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveFor("chat/71.json") {
            MockResponse().setResponseCode(422).setBody("""{"errors":["消息太长"]}""")
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the first window") { fragment.messageCount() == 3 }

        fragment.setComposerTextForTest("这条会被拒")
        fragment.sendForTest("这条会被拒")
        harness.await("the refusal") { site.requestPaths.contains("chat/71.json") }
        harness.idleClock(200)

        assertEquals("a refused write changes no message", 3, fragment.messageCount())
        assertEquals("the refused text stays in the field", "这条会被拒", fragment.composerTextForTest())
        harness.assertNoTelegramSideEffects()
    }

    /** A channel the site will not answer is its own state, not a blank conversation. */
    @Test
    fun aChannelTheSiteRefusesSaysSo() {
        site.serveResponse("chat/api/channels/71.json") {
            MockResponse().setResponseCode(403).setBody("""{"errors":["forbidden"]}""")
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the refusal to be its own state") { fragment.stateForTest() != null }
        assertEquals("nothing is drawn as a message", 0, fragment.messageCount())
        harness.layoutApp()
        harness.idleClock(400)
        harness.renderApp("chat-channel-error")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun serveJoinedChannel() {
        site.serveJson("chat/api/channels/71.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/71/read.json", harness.fixture("chat-read.json"))
    }

    private fun openChannel(channelId: Long, title: String): ChatChannelFragment {
        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        val fragment = ChatChannelFragment.forChannel(channelId, title)
        harness.present(fragment)
        return fragment
    }

    /** The unjoined 闲聊 channel with the reader's own membership spliced in, as its re-read answers. */
    private fun joinedLoungeFixture(): String =
        harness.fixture("chat-channel-unjoined.json").replace(
            "\"meta\":",
            "\"current_user_membership\": {\"following\": true, \"last_read_message_id\": 8001},\n    \"meta\":",
        )
}
