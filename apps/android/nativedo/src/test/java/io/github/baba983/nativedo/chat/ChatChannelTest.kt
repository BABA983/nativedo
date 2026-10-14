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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterView
import io.github.baba983.nativedo.topic.DiscourseEmojiPicker
import io.github.baba983.nativedo.topic.DiscourseMentionsPanel

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

    /**
     * A plain refresh is not a jump to the newest: a reader who is in history keeps their place, and
     * the refresh must not credit the unseen tail as read. The failure this pins is a refresh that
     * rebuilds the window and scrolls to the bottom, losing the reader's place.
     */
    @Test
    fun aRefreshKeepsAHistoryReadersPlaceAndDoesNotMarkTheTailRead() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", messagesFixture(25))

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the window") { fragment.messageCount() == 25 }
        harness.idleClock(200)
        harness.layoutApp()
        harness.await("the initial visible tail to be marked read") {
            site.requestPaths.contains("chat/api/channels/71/read.json")
        }
        fragment.listViewForTest().scrollToPosition(0)
        harness.layoutApp()
        harness.idleClock(200)
        assertEquals("the reader is in history", 0, firstVisiblePosition(fragment))

        val readWrites = site.requestPaths.count { it == "chat/api/channels/71/read.json" }
        site.serveJson("chat/api/channels/71/messages.json",
            messagesFixture(25).replace("消息 9001", "刷新后的旧消息"))
        fragment.refreshForTest()
        harness.await("the refreshed content to be applied") {
            fragment.chatMessageForTest(0)?.message == "刷新后的旧消息"
        }
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("a refresh never yanks a history reader to the newest", 0, firstVisiblePosition(fragment))
        assertEquals(
            "a refresh does not credit the unseen tail as read",
            readWrites,
            site.requestPaths.count { it == "chat/api/channels/71/read.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reader who scrolled up into the channel's history must not be yanked to the newest message
     * when one arrives live: the incoming message is appended below the viewport and announced,
     * never allowed to move the reader's place. The failure this pins is the current behaviour -
     * every arrival scrolls the list to the bottom - which loses the reader's place in the middle of
     * a channel they are reading.
     */
    @Test
    fun anIncomingMessageDoesNotPullAHistoryReaderToTheNewest() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", messagesFixture(25))
        site.serveBootstrap()
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveMessageBusPoll {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            busBatch(busMessage("/chat/71", 5001L, chatSent(9100L, "刚到的新消息")))
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the window") { fragment.messageCount() == 25 }
        harness.layoutApp()
        // The reader scrolls up into history: the first row the viewport holds is the first message.
        fragment.listViewForTest().scrollToPosition(0)
        harness.layoutApp()
        harness.idleClock(200)
        assertEquals(
            "the reader is in history, away from the newest message",
            0,
            firstVisiblePosition(fragment),
        )

        gate.countDown()
        harness.await("the live arrival") { fragment.messageCount() == 26 }
        harness.idleClock(300)
        harness.layoutApp()

        assertEquals(
            "an arrival below the viewport never moves the history reader",
            0,
            firstVisiblePosition(fragment),
        )
        assertTrue("the unseen arrival is announced", fragment.isPendingShownForTest())
        assertEquals("the button counts the arrivals the reader has not seen", 1, fragment.pendingCountForTest())
        harness.idleClock(200)
        harness.layoutApp()
        harness.renderApp("chat-history-arrival-pending")
        harness.assertNoTelegramSideEffects()
    }

    /** An arrival follows a reader already at the newest, instead of creating an unseen badge. */
    @Test
    fun aReaderAtTheNewestFollowsAnIncomingMessageWithoutPendingBadge() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", messagesFixture(25))
        site.serveBootstrap()
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveMessageBusPoll {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            busBatch(busMessage("/chat/71", 5002L, chatSent(9101L, "在底部收到的新消息", "baba", 7L)))
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the initial newest message") { fragment.messageCount() == 25 }
        harness.idleClock(300)
        harness.layoutApp()
        val manager = fragment.listViewForTest().layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        assertEquals("the reader starts at the newest", 24, manager.findLastCompletelyVisibleItemPosition())
        val previous = manager.findViewByPosition(24) as org.telegram.ui.Cells.ChatMessageCell
        assertFalse("the previous tail has no following bubble yet", previous.isPinnedBottom)

        gate.countDown()
        harness.await("the live arrival") { fragment.messageCount() == 26 }
        harness.idleClock(300)
        harness.layoutApp()
        assertEquals("the new tail remains visible to a reader who was at bottom", 25, manager.findLastCompletelyVisibleItemPosition())
        assertFalse("a seen arrival does not create a pending badge", fragment.isPendingShownForTest())
        val grouped = manager.findViewByPosition(24) as org.telegram.ui.Cells.ChatMessageCell
        assertSame("grouping does not replace the preceding visible cell", previous, grouped)
        assertTrue("successive messages by one author join the preceding bubble", grouped.isPinnedBottom)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An edit updates only the edited row: every other message keeps the MessageObject the reader was
     * already looking at, so the list is not rebuilt around the one message that changed. The
     * failure this pins is the current whole-bubble reconstruction ({@code rebuildBubbles} on every
     * edit/delete), which replaces every row's object and can drop the reader's place.
     */
    @Test
    fun anEditedRowKeepsUnrelatedMessagesAndTheirObjects() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", messagesFixture(6))
        site.serveBootstrap()
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveMessageBusPoll {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            busBatch(busMessage("/chat/71", 5101L, chatEdit(9002L, "被编辑后的内容")))
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the window") { fragment.messageCount() == 6 }
        harness.layoutApp()
        val untouchedObject = fragment.bubbleForTest(0)
        val untouchedMessage = fragment.chatMessageForTest(0)
        val neighbourObject = fragment.bubbleForTest(3)

        gate.countDown()
        harness.await("the edit") { fragment.chatMessageForTest(1)?.message == "被编辑后的内容" }
        harness.idleClock(300)
        harness.layoutApp()

        assertSame("an unrelated row keeps its own MessageObject", untouchedObject, fragment.bubbleForTest(0))
        assertSame("and the model behind it", untouchedMessage, fragment.chatMessageForTest(0))
        assertSame("a neighbouring row is not rebuilt either", neighbourObject, fragment.bubbleForTest(3))
        harness.assertNoTelegramSideEffects()
    }

    /** A deletion removes its own row; every other message keeps its object and place. */
    @Test
    fun aDeletedRowLeavesUnrelatedMessagesAndTheirObjectsStanding() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", messagesFixture(6))
        site.serveBootstrap()
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveMessageBusPoll {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            busBatch(busMessage("/chat/71", 5201L, """{"type":"delete","deleted_id":9003}"""))
        }

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the window") { fragment.messageCount() == 6 }
        harness.layoutApp()
        val surviving = fragment.bubbleForTest(0)
        val removedId = fragment.chatMessageForTest(2)?.id

        gate.countDown()
        harness.await("the deletion") { fragment.messageCount() == 5 }
        harness.idleClock(200)
        harness.layoutApp()

        assertEquals("the deleted message was the one named", 9003L, removedId)
        assertSame("an unrelated row keeps its own MessageObject", surviving, fragment.bubbleForTest(0))
        assertNull("the deleted message no longer has a bubble", fragment.bubbleForTest(5))
        val manager = fragment.listViewForTest().layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        val preceding = manager.findViewByPosition(1) as org.telegram.ui.Cells.ChatMessageCell
        val following = manager.findViewByPosition(2) as org.telegram.ui.Cells.ChatMessageCell
        assertTrue("deleting the intervening author joins the preceding bubble", preceding.isPinnedBottom)
        assertTrue("the following bubble joins the same author above", following.isPinnedTop)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The channel's composer carries the site's own input tools: the smile opens the linux.do emoji
     * catalog, and Back closes that catalog before it closes the channel. The failure this pins is a
     * composer with no tools installed, where the smile does nothing and Back leaves the screen.
     */
    @Test
    fun theSmileOpensTheSiteCatalogAndBackClosesItBeforeTheChannel() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("emojis.json", siteEmojiCatalog())
        site.serveImage("emoji/site-wave.png", siteEmojiTile())

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the messages") { fragment.messageCount() == 3 }
        harness.await("the composer") { fragment.isComposerShown() }
        harness.layoutApp()

        val composer = descendants(fragment.fragmentView).filterIsInstance<ChatActivityEnterView>().single()
        composer.emojiButton.performClick()
        harness.await("the site's own emoji catalog") { site.requestPaths.contains("emojis.json") }
        harness.idleClock(600)
        harness.idleUpstreamQueues()
        harness.layoutApp()

        val picker = descendants(fragment.fragmentView).filterIsInstance<DiscourseEmojiPicker>().firstOrNull()
        assertNotNull("the smile opens the site picker", picker)
        assertTrue("the site catalog panel is open", picker!!.isOpen())
        harness.renderApp("chat-site-emoji")

        assertFalse("predictive Back is claimed without closing the panel", fragment.onBackPressed(false))
        assertTrue("a predictive probe has no side effect", picker.isOpen())
        assertFalse("invoked Back closes the panel, not the channel", fragment.onBackPressed(true))
        harness.idleClock(400)
        assertFalse(picker.isOpen())
        assertSame("the channel is still the screen", fragment, harness.visibleFragment())

        // A paused screen's Back belongs to the screen again: the tools let go on pause.
        fragment.onPause()
        harness.idleClock(200)
        assertTrue("a paused screen's Back is not swallowed by the tools", fragment.onBackPressed(true))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The same tools carry the site @mention list over the channel's composer; the channel's own
     * authors answer locally, and Back closes the panel before the channel.
     */
    @Test
    fun theComposerOffersTheSitesMentionList() {
        serveJoinedChannel()
        site.serveJson("chat/api/channels/71/messages.json", harness.fixture("chat-messages.json"))
        site.serveJson("u/search/users", """{"users":[],"groups":[]}""")

        val fragment = openChannel(71L, "站点反馈")
        harness.await("the messages") { fragment.messageCount() == 3 }
        harness.await("the composer") { fragment.isComposerShown() }
        harness.layoutApp()

        val composer = descendants(fragment.fragmentView).filterIsInstance<ChatActivityEnterView>().single()
        val field = composer.editField!!
        field.setText("@ba")
        field.setSelection(field.text.length)
        harness.idleClock(400)
        harness.layoutApp()

        val panel = descendants(fragment.fragmentView).filterIsInstance<DiscourseMentionsPanel>().firstOrNull()
        assertNotNull("the @mention panel is part of the channel composer", panel)
        assertTrue("the channel's own authors are offered", panel!!.isShowing())
        harness.renderApp("chat-site-mention")

        assertFalse("Back is claimed by the panel first", fragment.onBackPressed(false))
        assertTrue("a predictive probe leaves the panel open", panel.isShowing())
        assertFalse("Back closes the panel, not the channel", fragment.onBackPressed(true))
        harness.idleClock(300)
        assertFalse("the panel is closed", panel.isShowing())
        assertSame("the channel is still the screen", fragment, harness.visibleFragment())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun descendants(view: android.view.View?): List<android.view.View> = buildList {
        if (view == null) return@buildList
        add(view)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
    }

    /** The first row the channel's list is showing, for a reader whose place must not move. */
    private fun firstVisiblePosition(fragment: ChatChannelFragment): Int =
        (fragment.listViewForTest().layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
            .findFirstVisibleItemPosition()

    private fun siteEmojiCatalog(): String =
        """{"custom":[{"name":"site_wave","url":"${site.url("emoji/site-wave.png")}"}]}"""

    /** A small tile image so the site emoji's picture really can be fetched and drawn. */
    private fun siteEmojiTile(): ByteArray {
        val image = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(245, 170, 30))
        return java.io.ByteArrayOutputStream().also {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
    }

    /** [count] messages, authored alternately, enough to overflow the viewport and scroll. */
    private fun messagesFixture(count: Int, startId: Long = 9001L): String {
        val messages = (0 until count).joinToString(",") { index ->
            val id = startId + index
            val username = if (index % 2 == 0) "baba" else "alice"
            val userId = if (index % 2 == 0) 7 else 8
            val seconds = (index % 60).toString().padStart(2, '0')
            """{"id":$id,"message":"消息 $id","cooked":"<p>消息 $id</p>","created_at":"2026-10-02T09:$seconds:00.000Z","chat_channel_id":71,"user":{"id":$userId,"username":"$username","name":"$username","avatar_template":"/user_avatar/linux.do/$username/{size}/1.png"}}"""
        }
        return """{"messages":[$messages],"meta":{"can_load_more_past":false,"can_load_more_future":false}}"""
    }

    private fun busMessage(channel: String, messageId: Long, data: String): String =
        """{"global_id":$messageId,"message_id":$messageId,"channel":"$channel","data":$data}"""

    private fun busBatch(vararg messages: String): String =
        messages.joinToString(",", prefix = "[", postfix = "]")

    private fun chatSent(id: Long, text: String, username: String = "alice", userId: Long = 8): String =
        """{"type":"sent","chat_message":{"id":$id,"message":"$text","cooked":"<p>$text</p>","created_at":"2026-10-02T10:00:00.000Z","chat_channel_id":71,"user":{"id":$userId,"username":"$username","name":"$username","avatar_template":"/user_avatar/linux.do/$username/{size}/1.png"}}}"""

    private fun chatEdit(id: Long, text: String): String =
        """{"type":"edit","chat_message":{"id":$id,"message":"$text","cooked":"<p>$text</p>","created_at":"2026-10-02T09:02:00.000Z","chat_channel_id":71,"user":{"id":8,"username":"alice","name":"alice","avatar_template":"/user_avatar/linux.do/alice/{size}/1.png"}}}"""

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
