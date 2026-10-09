package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.menuItemLabels
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
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Managing the reader's own posts: editing, deleting and recovering through the site's own
 * permissions.
 *
 * <p>The failures this pins, each one reachable from the reads/writes this screen performs:</p>
 * <ul>
 *   <li>a post menu that offers a write the site would refuse (a username guessed from, not the
 *       site's own `can_edit`/`can_delete`/`can_recover`);</li>
 *   <li>an editor that opens on the cooked HTML instead of the markdown the site stores, so saving
 *       silently mangles the post;</li>
 *   <li>an edit answer applied to the row without reading back what the site actually cooked;</li>
 *   <li>a failed edit that throws the reader's work away;</li>
 *   <li>an edit that eats the reply draft the field was holding;</li>
 *   <li>a second press that sends a second write;</li>
 *   <li>a write the site refused that still changes the row;</li>
 *   <li>a delete whose 204 leaves the row claiming permissions the site never confirmed (a guessed
 *       `can_recover`), and a delete whose confirming readback fails being reported as a refusal or
 *       replayed;</li>
 *   <li>an edited body's picture not being downloaded for the rebuilt row;</li>
 *   <li>a recover whose answer is not drawn; and</li>
 *   <li>an answer that lands after the signed-in reader changed - by a different account, or by a
 *       logout and a login of the same name - and is drawn for the wrong session.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
internal class OwnPostManagementTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    /**
     * What `GET /posts/{id}.json` answers. It is the markdown an edit opens on and, after a delete,
     * the post the server actually keeps - its own permissions included.
     */
    @Volatile
    private var servedPost: JSONObject = JSONObject(harness.fixture("own-post.json"))

    /** What `PUT /posts/{id}.json` answers: the site's new cooked body, wrapped under `post`. */
    private var updateAnswer: JSONObject = updateResponse(
        postJson(cooked = "<p>Server <em>rewrote</em> this.</p>", raw = "Edited **markdown**."),
    )

    /**
     * What the site's deleted post keeps. The server owns `can_recover`; a client that guessed it
     * would offer a write the site refuses.
     */
    private var deleteAllowsRecover = true

    /**
     * What `DELETE /posts/{id}.json` answers. Discourse's own success is a bodyless 204, and this
     * dispatcher also leaves the post in the deleted state the confirming readback will find, the
     * way the site does. A test overrides it for a refusal or for a delete whose readback fails.
     */
    private var deleteAnswer: () -> MockResponse = {
        servedPost = deletedPost(canRecover = deleteAllowsRecover)
        MockResponse().setResponseCode(204)
    }

    /** What `PUT /posts/{id}/recover.json` answers: the recovered post, bare. */
    private var recoverAnswer: JSONObject =
        postJson(cooked = "<p>Recovered **body**.</p>", raw = "Recovered **body**.", version = 4)

    @Before
    fun setUp() {
        site.start()
        installRoutes()
        container = site.authenticatedContainer(harness.context)
        NativeDo.setContainerFactory { container }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    /**
     * The menu is built from the site's own booleans, not from a name: a post the reader did not
     * write but may edit (a moderator's) offers 编辑/删除, and the reader's own post the site says is
     * not editable offers neither.
     */
    @Test
    fun theMenuFollowsTheSitesPermissionsNotTheAuthorsName() {
        // A post the reader did not author, but the site says they may edit and delete.
        servedPost = postJson(username = "someone-else", yours = false, canEdit = true, canDelete = true)
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Perms"))
        harness.await("the topic to load") { rowCount() == 1 }

        val labels = openMenuLabels()
        println("own-post: permitted menu = $labels")
        assertTrue("a post the site lets the reader edit offers it", labels.any { it == "编辑" })
        assertTrue("and delete", labels.any { it == "删除" })
        assertFalse("nothing deleted, so no recovery", labels.any { it == "恢复" })
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-menu.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The other direction: the reader's own post (`yours`) whose site permissions are all false
     * offers none of the writes - the name is not enough.
     */
    @Test
    fun theReadersOwnPostWithNoPermissionsOffersNoWrites() {
        servedPost = postJson(username = "baba", yours = true, canEdit = false, canDelete = false)
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Mine"))
        harness.await("the topic to load") { rowCount() == 1 }

        val labels = openMenuLabels()
        println("own-post: unpermitted own menu = $labels")
        assertFalse("no edit without can_edit", labels.any { it == "编辑" })
        assertFalse("no delete without can_delete", labels.any { it == "删除" })
        assertFalse("no recover without can_recover", labels.any { it == "恢复" })
        assertEquals("and the ordinary actions are untouched", ORDINARY_MENU_ITEMS, labels.size)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Opening the editor: it reads the post's own endpoint for `raw` and puts that markdown in the
     * field - the cooked HTML is never what the reader edits, or saving would store the markup.
     */
    @Test
    fun editingOpensOnTheStoredMarkdownNotTheCookedHtml() {
        servedPost = postJson(raw = "Hello from **Android**.", cooked = "<p>Hello from <strong>Android</strong>.</p>")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Edit"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the post's own read to open the editor") {
            composer().getFieldText()?.toString() == "Hello from **Android**."
        }
        println("own-post: edit field = " + composer().getFieldText())
        assertEquals(
            "the field holds the stored markdown",
            "Hello from **Android**.",
            composer().getFieldText()?.toString(),
        )
        assertTrue(
            "the post's own endpoint supplied it",
            site.recordedRequests.contains("GET /posts/$POST_ID.json"),
        )
        harness.layoutApp()
        harness.renderApp("own-post-edit")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Sending the edit: the write carries the reader's markdown (not the cooked HTML), and the row
     * is rebuilt from the post the site answered - its own cooked body, not the text locally
     * translated.
     */
    @Test
    fun anEditSendsTheMarkdownAndTheRowShowsWhatTheSiteCooked() {
        servedPost = postJson(raw = "Hello from **Android**.", cooked = "<p>Hello from <strong>Android</strong>.</p>")
        updateAnswer = updateResponse(
            postJson(cooked = "<p>Server <em>rewrote</em> this.</p>", raw = "Edited **markdown**."),
        )
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Edit"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open on the markdown") {
            composer().getFieldText()?.toString() == "Hello from **Android**."
        }
        composer().setFieldText("Edited **markdown**.")
        harness.idle()
        composer().sendMessage()

        harness.await("the edit to reach the site") {
            site.recordedRequests.contains("PUT /posts/$POST_ID.json")
        }
        // The edit mode ends on success (the field is handed back to the reader's reply).
        harness.await("the edit to finish") { composer().getFieldText()?.toString().orEmpty().isEmpty() }

        val body = site.requestBodyFor("posts/$POST_ID.json")
        assertNotNull("the edit carried a body", body)
        val fields = formFields(body!!)
        println("own-post: edit body = $fields csrf=" + site.csrfHeaderFor("posts/$POST_ID.json"))
        assertEquals("the markdown went to the site, not the cooked body", "Edited **markdown**.", fields["post[raw]"])
        assertEquals("with the token the site asks writes for", "csrf-token-demo", site.csrfHeaderFor("posts/$POST_ID.json"))

        harness.await("the row to be rebuilt from the site's own cooked body") {
            harness.layoutApp()
            firstRowText()?.contains("Server rewrote this.") == true
        }
        assertTrue("the row shows the site's cooked body", firstRowText()?.contains("Server rewrote this.") == true)
        harness.renderApp("own-post-edited")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site's own cook is what is drawn, and the picture it named is downloaded for the rebuilt
     * floor: a floor replaced from a write goes through the same media path every other row does, so
     * an edit that added a picture shows it rather than a placeholder.
     */
    @Test
    fun anEditedBodyWithANewPictureDownloadsItForTheRebuiltRow() {
        val imagePath = "uploads/edited-${System.nanoTime()}.png"
        servedPost = postJson(raw = "Hello from **Android**.", cooked = "<p>Hello from <strong>Android</strong>.</p>")
        updateAnswer = updateResponse(
            postJson(
                cooked = """<p>Edited with a picture.</p><p><div class="lightbox-wrapper"><a class="lightbox" href="${site.url(imagePath)}" title="edited"><img src="$imagePath" alt="edited" width="320" height="180"></a></div></p>""",
                raw = "Edited with a picture.\n\n![edited](upload://edited.png)",
            ),
        )
        site.serveImage(imagePath, onePixelPng())
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "EditMedia"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open on the markdown") {
            composer().getFieldText()?.toString() == "Hello from **Android**."
        }
        composer().setFieldText("Edited with a picture.\n\n![edited](upload://edited.png)")
        harness.idle()
        composer().sendMessage()

        harness.await("the edit to land") { site.recordedRequests.contains("PUT /posts/$POST_ID.json") }
        // The row is rebuilt from the site's cook; only the media refresh hands that cook's picture
        // to the downloader, so this GET is what proves the edited image actually loads.
        harness.await("the edited row's picture to be downloaded") {
            site.cookieHeaderFor(imagePath) != null
        }
        assertNotNull("the rebuilt row fetched the site's picture", site.cookieHeaderFor(imagePath))
        harness.renderApp("own-post-edited-image")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A refused edit keeps the reader's work: the field still holds it and only one write went out.
     */
    @Test
    fun aRefusedEditKeepsTheTextAndSendsOnce() {
        servedPost = postJson(raw = "Hello from **Android**.")
        site.serveFor("posts/$POST_ID.json") { method ->
            when (method) {
                "GET" -> jsonResponse(servedPost.toString())
                "PUT" -> MockResponse().setResponseCode(422)
                    .setHeader("Content-Type", "application/json")
                    .setBody(harness.fixture("error-422-post.json"))
                "DELETE" -> deleteAnswer()
                else -> MockResponse().setResponseCode(405)
            }
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Refused"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open on the markdown") {
            composer().getFieldText()?.toString() == "Hello from **Android**."
        }
        composer().setFieldText("still mine to send")
        harness.idle()
        composer().sendMessage()
        harness.await("the site to refuse the edit") {
            site.recordedRequests.contains("PUT /posts/$POST_ID.json")
        }
        val dialog = awaitDialog("编辑失败")
        println("own-post: refusal dialog = " + dialogButtonTexts(dialog))
        assertEquals("the reader's edit is kept", "still mine to send", composer().getFieldText()?.toString())
        harness.idleClock(5_000)
        assertEquals(
            "one press sent one write",
            1,
            site.recordedRequests.count { it == "PUT /posts/$POST_ID.json" },
        )
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-edit-failed.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Cancelling the edit hands the reply back untouched: the text, the reply target, and the
     * native strip that names it.
     */
    @Test
    fun cancellingAnEditRestoresTheReplyDraftAndItsTarget() {
        servedPost = postJson(raw = "The post's markdown.")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Cancel"))
        harness.await("the topic to load") { rowCount() == 1 }

        // The reader was answering floor 1.
        replyToFirstPost()
        composer().setFieldText("my reply in progress")
        harness.idle()
        assertEquals("the target is recorded with the draft", 1, storedTarget())

        openMenuItem("编辑")
        harness.await("the editor to open") { composer().getFieldText()?.toString() == "The post's markdown." }
        harness.layoutApp()
        assertTrue("the strip says this is an edit", composer().isTopViewVisible())

        val close = findCloseButton(composer()) ?: throw AssertionError("the strip has no close button")
        close.performClick()
        harness.idleClock(400)
        harness.layoutApp()

        assertEquals("the reply text comes back", "my reply in progress", composer().getFieldText()?.toString())
        assertEquals("the target comes back", 1, storedTarget())
        assertTrue("and the reply strip is shown again", composer().isTopViewVisible())
        harness.renderApp("own-post-edit-cancelled")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A second press while a write is out is refused: one press, one write.
     */
    @Test
    fun aSecondSendWhileAnEditIsOutGoesNowhere() {
        servedPost = postJson(raw = "Hello from **Android**.")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Once"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open") { composer().getFieldText()?.toString() == "Hello from **Android**." }
        composer().setFieldText("sent once")
        harness.idle()
        composer().sendMessage()
        composer().sendMessage()
        harness.await("the edit to reach the site") {
            site.recordedRequests.contains("PUT /posts/$POST_ID.json")
        }
        harness.idleClock(1_000)
        assertEquals(
            "two presses made one write",
            1,
            site.recordedRequests.count { it == "PUT /posts/$POST_ID.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Deleting goes through the confirmation dialog; confirming sends the delete and then reads the
     * post back - the 204 carries no permissions, so the row follows the site's own post rather than
     * a client's guess.
     */
    @Test
    fun deletingConfirmsThenRefreshesTheRowFromTheReadback() {
        servedPost = postJson()
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Delete"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("删除")
        val confirm = awaitDialog("删除帖子")
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-delete-confirm.png")
        println("own-post: delete dialog = " + dialogButtonTexts(confirm))
        confirm.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()

        harness.await("the delete to reach the site") {
            site.recordedRequests.contains("DELETE /posts/$POST_ID.json")
        }
        // The 204 is only the accepted write; the row follows the post's own read, which is a second
        // round trip. Wait for the state the reader sees - the menu - rather than a fixed drain.
        val labels = awaitMenuShowing("恢复")
        println("own-post: after delete = $labels")
        assertTrue(
            "the delete is refreshed by reading the post back",
            site.recordedRequests.contains("GET /posts/$POST_ID.json"),
        )
        assertFalse("edit is gone", labels.any { it == "编辑" })
        assertFalse("delete is gone", labels.any { it == "删除" })
        harness.layoutApp()
        assertTrue(
            "the words the reader wrote are kept",
            firstRowText()?.contains("Hello from Android.") == true,
        )
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-deleted.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The permissions after a delete are the server's: a post the server says cannot be recovered
     * offers no recovery, because the client never guesses `can_recover`.
     */
    @Test
    fun aDeletedPostTheServerSaysCannotRecoverOffersNoWrites() {
        servedPost = postJson()
        deleteAllowsRecover = false
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "NoRecover"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("删除")
        awaitDialog("删除帖子").getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        harness.await("the delete to reach the site") {
            site.recordedRequests.contains("DELETE /posts/$POST_ID.json")
        }
        // The row follows the server's post; wait for the state the reader sees (no write offered).
        val labels = awaitMenu("the row's menu to drop the write actions") {
            !it.contains("编辑") && !it.contains("删除")
        }
        println("own-post: server-deleted menu = $labels")
        assertFalse("the site did not offer recovery", labels.any { it == "恢复" })
        assertEquals("and the ordinary actions are untouched", ORDINARY_MENU_ITEMS, labels.size)
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-deleted-no-recover.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A delete the site accepted but whose confirming readback failed: the row cannot be refreshed
     * from an answer nobody got, so it is not reported as a refusal and the delete is not replayed -
     * the reader is offered a reload of the topic instead.
     */
    @Test
    fun anAcceptedDeleteWhoseReadbackFailsSaysSoAndOffersAReload() {
        servedPost = postJson()
        site.serveFor("posts/$POST_ID.json") { method ->
            when (method) {
                "GET" -> MockResponse().setResponseCode(500)
                "PUT" -> jsonResponse(updateAnswer.toString())
                "DELETE" -> deleteAnswer()
                else -> MockResponse().setResponseCode(405)
            }
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "ReadbackFailed"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("删除")
        awaitDialog("删除帖子").getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        harness.await("the delete to reach the site") {
            site.recordedRequests.contains("DELETE /posts/$POST_ID.json")
        }

        val dialog = awaitDialog("帖子已删除")
        val texts = dialogButtonTexts(dialog)
        println("own-post: readback-failure dialog = $texts")
        assertTrue("the failure names the reload", texts.any { it.contains("重新加载") })
        assertFalse("the delete is not reported as refused", texts.any { it.contains("删除失败") })
        assertTrue("the readback was attempted", site.recordedRequests.contains("GET /posts/$POST_ID.json"))
        harness.idleClock(5_000)
        assertEquals(
            "the accepted delete is not replayed",
            1,
            site.recordedRequests.count { it == "DELETE /posts/$POST_ID.json" },
        )
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-delete-refresh-failed.png")
        harness.assertNoTelegramSideEffects()
    }

    /** The dialog's negative button cancels: no delete leaves the device. */
    @Test
    fun cancellingTheDeleteConfirmationSendsNothing() {
        servedPost = postJson()
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "NoDelete"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("删除")
        val confirm = awaitDialog("删除帖子")
        confirm.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        harness.idleClock(400)

        assertFalse(
            "the cancelled confirmation sent nothing",
            site.recordedRequests.contains("DELETE /posts/$POST_ID.json"),
        )
        val labels = openMenuLabels()
        assertTrue("and the post is still editable", labels.any { it == "编辑" })
        harness.assertNoTelegramSideEffects()
    }

    /** A delete the site refuses changes nothing: the wording is shown and the row is as it was. */
    @Test
    fun aRefusedDeleteKeepsThePostAndShowsTheSitesReason() {
        servedPost = postJson()
        deleteAnswer = {
            MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"errors\":[\"不能删除该帖子\"]}")
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "RefusedDelete"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("删除")
        awaitDialog("删除帖子").getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        val dialog = awaitDialog("删除失败")
        println("own-post: refused delete dialog = " + dialogButtonTexts(dialog))
        assertTrue(
            "the site's own reason is shown",
            dialogButtonTexts(dialog).any { it.contains("不能删除该帖子") },
        )
        assertEquals(
            "one confirmation sent one delete",
            1,
            site.recordedRequests.count { it == "DELETE /posts/$POST_ID.json" },
        )
        val labels = openMenuLabels()
        assertTrue("the refused post is still editable", labels.any { it == "编辑" })
        assertTrue("and still deletable", labels.any { it == "删除" })
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-delete-refused.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A deleted post offers recovery and nothing else; recovering draws the post the site answered -
     * its cooked body and its refreshed permissions - over the floor.
     */
    @Test
    fun recoveringShowsTheSitesPostAndRestoresTheWriteActions() {
        servedPost = postJson(
            canEdit = false,
            canDelete = false,
            canRecover = true,
            deletedAt = "2026-04-05T09:30:00.000Z",
        )
        recoverAnswer = postJson(cooked = "<p>Recovered <strong>body</strong>.</p>", raw = "Recovered **body**.")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Recover"))
        harness.await("the topic to load") { rowCount() == 1 }

        val before = openMenuLabels()
        println("own-post: deleted menu = $before")
        assertTrue("a deleted post offers recovery", before.any { it == "恢复" })
        assertFalse("not edit", before.any { it == "编辑" })

        clickMenuItem(latestItemOptionsMenu(harness.activity), "恢复")
        harness.idle()
        harness.await("the recover to reach the site") {
            site.recordedRequests.contains("PUT /posts/$POST_ID/recover.json")
        }
        harness.await("the row to show the recovered post") {
            harness.layoutApp()
            firstRowText()?.contains("Recovered body.") == true
        }
        val after = openMenuLabels()
        println("own-post: recovered menu = $after")
        assertTrue("the recovered post is editable again", after.any { it == "编辑" })
        assertTrue("and deletable", after.any { it == "删除" })
        assertFalse("recovery is no longer offered", after.any { it == "恢复" })
        harness.renderComposite(".amp/in/artifacts/nativedo/own-post-recovered.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A recover the site refuses leaves the post's own permission as it was: it is still the
     * recoverable post the row already showed, and only one write went out.
     */
    @Test
    fun aRefusedRecoverKeepsThePostsOriginalPermission() {
        servedPost = postJson(
            canEdit = false,
            canDelete = false,
            canRecover = true,
            deletedAt = "2026-04-05T09:30:00.000Z",
        )
        site.serveFor("posts/$POST_ID/recover.json") { _ ->
            MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"errors\":[\"不能恢复该帖子\"]}")
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "RefusedRecover"))
        harness.await("the topic to load") { rowCount() == 1 }

        val before = openMenuLabels()
        assertTrue("the deleted post offers recovery", before.any { it == "恢复" })

        clickMenuItem(latestItemOptionsMenu(harness.activity), "恢复")
        val dialog = awaitDialog("恢复失败")
        println("own-post: refused recover dialog = " + dialogButtonTexts(dialog))
        assertTrue(
            "the site's own reason is shown",
            dialogButtonTexts(dialog).any { it.contains("不能恢复该帖子") },
        )
        assertEquals(
            "one press sent one recover",
            1,
            site.recordedRequests.count { it == "PUT /posts/$POST_ID/recover.json" },
        )
        val after = openMenuLabels()
        println("own-post: after refused recover = $after")
        assertTrue("the refusal keeps the recovery permission", after.any { it == "恢复" })
        assertFalse("and offers nothing else", after.any { it == "编辑" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An answer that arrives after the signed-in reader changed is not drawn: the row keeps what it
     * had, because the post the write touched belongs to the account that is no longer here.
     */
    @Test
    fun anEditAnswerLandingAfterTheAccountChangedIsNotDrawn() {
        servedPost = postJson(raw = "Hello from **Android**.")
        updateAnswer = updateResponse(
            postJson(cooked = "<p>SHOULD NOT APPEAR</p>", raw = "changed"),
        )
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Stale"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open") { composer().getFieldText()?.toString() == "Hello from **Android**." }
        composer().setFieldText("an edit for the old account")
        harness.idle()
        // The write has to be genuinely in flight before the reader changes: the field's draft
        // belongs to "baba", so a write dispatched after the store moved would not be sent at all.
        // The site holds the PUT's answer until the account has changed under it.
        val (arrived, release) = holdTheEditAnswer { jsonResponse(updateAnswer.toString()) }
        composer().sendMessage()
        harness.await("the edit to be in flight at the site") { arrived.count == 0L }
        container.sessionStore.save(container.sessionStore.snapshot()!!.copy(username = "other-reader"))
        release.countDown()
        // The answer now lands for the reader who sent it, not the one here now.
        settleReleasedAnswer()
        harness.layoutApp()
        val text = firstRowText()
        println("own-post: stale row text = $text")
        assertTrue("the row still shows the reader's own post", text?.contains("Hello from Android.") == true)
        assertFalse("the stale answer is not drawn", text?.contains("SHOULD NOT APPEAR") == true)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A refusal that lands after the reader changed is not shown either: the old reader's failure is
     * not the new reader's to see.
     */
    @Test
    fun aRefusedEditLandingAfterTheAccountChangedIsNotShown() {
        servedPost = postJson(raw = "Hello from **Android**.")
        // The refusal is held at the site until the account has changed, so it lands after the
        // write's own reader is gone and must not be shown to the one here now.
        val (arrived, release) = holdTheEditAnswer {
            MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "StaleRefusal"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open") { composer().getFieldText()?.toString() == "Hello from **Android**." }
        composer().setFieldText("an edit for the old account")
        harness.idle()
        composer().sendMessage()
        harness.await("the write to be in flight at the site") { arrived.count == 0L }
        container.sessionStore.save(container.sessionStore.snapshot()!!.copy(username = "other-reader"))
        release.countDown()
        settleReleasedAnswer()
        val shown = harness.visibleFragment()?.visibleDialog as? AlertDialog
        val texts = shown?.let(::dialogButtonTexts).orEmpty()
        println("own-post: stale refusal dialog = $texts")
        assertFalse("the old reader's refusal is not shown", texts.any { it == "编辑失败" })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A logout and a fresh login of the same name: the username alone cannot tell the answer apart,
     * so the write's coordinator generation is what rejects it.
     */
    @Test
    fun anEditAnswerLandingAfterAReloginOfTheSameReaderIsNotDrawn() {
        servedPost = postJson(raw = "Hello from **Android**.")
        updateAnswer = updateResponse(
            postJson(cooked = "<p>SHOULD NOT APPEAR</p>", raw = "changed"),
        )
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Relogin"))
        harness.await("the topic to load") { rowCount() == 1 }

        openMenuItem("编辑")
        harness.await("the editor to open") { composer().getFieldText()?.toString() == "Hello from **Android**." }
        composer().setFieldText("an edit for the earlier session")
        harness.idle()
        // The write is genuinely in flight before the session moves on, so its answer really lands
        // after the change rather than racing it.
        val (arrived, release) = holdTheEditAnswer { jsonResponse(updateAnswer.toString()) }
        composer().sendMessage()
        harness.await("the edit to be in flight at the site") { arrived.count == 0L }
        // The name in the store is deliberately kept, so only the coordinator's generation can tell
        // this session apart from the write's own.
        runBlocking {
            container.sessionCoordinator.markExpired(SessionExpiredReason.RecoveryFailed)
        }
        assertEquals("the username is unchanged", "baba", container.sessionStore.snapshot()?.username)
        release.countDown()
        settleReleasedAnswer()
        harness.layoutApp()
        val text = firstRowText()
        println("own-post: relogin row text = $text")
        assertTrue("the row still shows the reader's own post", text?.contains("Hello from Android.") == true)
        assertFalse("the stale answer is not drawn", text?.contains("SHOULD NOT APPEAR") == true)
        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers ----

    private fun installRoutes() {
        // The topic body is built at request time so a test can change `servedPost` before the
        // screen reads it.
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson(servedPost) }
        site.serveFor("posts/$POST_ID.json") { method ->
            when (method) {
                "GET" -> jsonResponse(servedPost.toString())
                "PUT" -> jsonResponse(updateAnswer.toString())
                "DELETE" -> deleteAnswer()
                else -> MockResponse().setResponseCode(405)
            }
        }
        site.serveFor("posts/$POST_ID/recover.json") { _ -> jsonResponse(recoverAnswer.toString()) }
    }

    /**
     * Holds the edit route's `PUT` at the site until the test releases it, so a test can change the
     * session while the write is genuinely in flight rather than racing the send. The returned pair
     * is the latch that counts down when the write has entered the site ([arrived]) and the latch
     * that lets its answer out ([release]); the site's own `GET`/`DELETE` answers are unchanged.
     */
    private fun holdTheEditAnswer(answer: () -> MockResponse): Pair<CountDownLatch, CountDownLatch> {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        site.serveFor("posts/$POST_ID.json") { method ->
            when (method) {
                "GET" -> jsonResponse(servedPost.toString())
                "PUT" -> {
                    arrived.countDown()
                    // A bounded wait, not an unbounded one: a test that failed before releasing must
                    // not leave the dispatcher thread blocked forever.
                    release.await(4, TimeUnit.SECONDS)
                    answer()
                }
                "DELETE" -> deleteAnswer()
                else -> MockResponse().setResponseCode(405)
            }
        }
        return arrived to release
    }

    /** Wait for the write callback to settle before asserting that its stale body/refusal was dropped. */
    private fun settleReleasedAnswer() {
        val fragment = harness.visibleFragment() as TopicFragment
        val pending = TopicFragment::class.java.getDeclaredField("sendingEdit").apply { isAccessible = true }
        harness.await("the released edit callback to settle") { !pending.getBoolean(fragment) }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun topicJson(post: JSONObject): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray().put(post))
        stream.put("stream", JSONArray().put(post.getLong("id")))
        base.put("posts_count", 1)
        base.put("highest_post_number", 1)
        base.put("last_read_post_number", 0)
        base.put("last_read_post_id", JSONObject.NULL)
        return base.toString()
    }

    private fun updateResponse(post: JSONObject): JSONObject = JSONObject().put("post", post)

    /** The post the site keeps after a delete: the words stay, and the permissions are the site's. */
    private fun deletedPost(canRecover: Boolean): JSONObject = postJson(
        canEdit = false,
        canDelete = false,
        canRecover = canRecover,
        deletedAt = "2026-04-05T09:30:00.000Z",
    )

    private fun postJson(
        id: Long = POST_ID,
        floor: Int = 1,
        username: String = "baba",
        name: String = "Baba",
        cooked: String = "<p>Hello from <strong>Android</strong>.</p>",
        raw: String = "Hello from **Android**.",
        yours: Boolean = true,
        canEdit: Boolean = true,
        canDelete: Boolean = true,
        canRecover: Boolean = false,
        deletedAt: String? = null,
        version: Int = 2,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("post_number", floor)
        put("reply_to_post_number", JSONObject.NULL)
        put("reply_count", 0)
        put("username", username)
        put("name", name)
        put("avatar_template", "/user_avatar/linux.do/$username/{size}/1.png")
        put("created_at", "2026-04-05T09:07:00.000Z")
        put("updated_at", "2026-04-05T09:12:00.000Z")
        put("version", version)
        put("yours", yours)
        put("can_edit", canEdit)
        put("can_delete", canDelete)
        put("can_recover", canRecover)
        put("deleted_at", deletedAt ?: JSONObject.NULL)
        put("bookmarked", false)
        put("cooked", cooked)
        put("raw", raw)
        put("polls", JSONArray())
    }

    private fun formFields(body: String): Map<String, String> =
        body.split('&').associate { part ->
            URLDecoder.decode(part.substringBefore('='), "UTF-8") to
                URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private fun firstRowText(): String? =
        laidOutCells().firstOrNull()?.getMessageObject()?.richLayout?.joinedText?.toString()

    /** Long-presses the first row and returns the labels of the menu it opened. */
    private fun openMenuLabels(): List<String> {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        return latestItemOptionsMenu(harness.activity).let(::menuItemLabels)
    }

    /**
     * Waits for the first row's menu to satisfy [predicate] and returns the labels. The wait is on
     * the state the reader sees, not a fixed drain: a delete's own readback is a second round trip,
     * and a fixed idle either races it or over-waits. Each press replaces the previous menu (the
     * fragment dismisses the open one first), so the menu finally left up is the one the assertions
     * read.
     */
    private fun awaitMenu(what: String, predicate: (List<String>) -> Boolean): List<String> {
        var labels: List<String> = emptyList()
        harness.await(what) {
            labels = openMenuLabels()
            predicate(labels)
        }
        return labels
    }

    private fun awaitMenuShowing(label: String): List<String> =
        awaitMenu("the first row's menu to offer $label") { labels -> labels.any { it == label } }

    private fun openMenuItem(label: String) {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), label)
        harness.idle()
    }

    /** The post menu's 回复 on the first floor, exactly as the reader reaches it. */
    private fun replyToFirstPost() {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "回复")
        harness.idle()
    }

    private fun composer(): ChatActivityEnterView {
        val view = harness.visibleFragment()?.fragmentView ?: error("the screen has no view")
        return viewsUnder(view).filterIsInstance<ChatActivityEnterView>().lastOrNull()
            ?: error("the screen has no composer")
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

    private fun storedTarget(): Int? =
        container.topicReplyDraftStore.load(replyKey(TOPIC_ID)).replyToPostNumber

    private fun replyKey(topicId: Long) = TopicReplyDraftKey(site.baseUrl, "baba", topicId)

    private fun awaitDialog(title: String): AlertDialog {
        repeat(120) {
            harness.idleClock(100)
            harness.idle()
            val dialog = harness.visibleFragment()?.visibleDialog as? AlertDialog
            if (dialog != null && dialog.isShowing && title in dialogButtonTexts(dialog)) {
                return dialog
            }
            java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L)
        }
        throw AssertionError("the \"$title\" dialog was never shown")
    }

    private fun dialogButtonTexts(dialog: AlertDialog): List<String> {
        val out = mutableListOf<String>()
        collectText(dialog.window?.decorView, out)
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is android.widget.TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
    }

    /** A 1x1 PNG: enough for the loader to have a picture to draw. */
    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(200, 120, 60))
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private companion object {
        const val TOPIC_ID = 991_001L
        const val POST_ID = 1001L
        const val ORDINARY_MENU_ITEMS = 6
    }
}
