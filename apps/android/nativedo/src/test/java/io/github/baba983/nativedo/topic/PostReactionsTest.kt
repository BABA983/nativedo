package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * Reactions on a post, end to end: Discourse's reaction list becomes the chips upstream draws, and a
 * tap on a chip toggles the reaction the chip stands for.
 *
 * <p>The failure this pins: a chip carries an emoticon and nothing else, so a client that maps
 * Discourse ids to glyphs without a way back would toggle a reaction the reader never picked.</p>
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
internal class PostReactionsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Nice tool, thanks for sharing.</p>")
            post.put("reaction_users_count", 4)
            post.put("reactions", JSONArray().apply {
                put(JSONObject().put("id", "heart").put("count", 3))
                put(JSONObject().put("id", "tieba_087").put("count", 1))
            })
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theChipsShowThePostsReactionsAndATapTogglesTheOneThatWasShown() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        val reactions = cell.messageObject!!.messageOwner.reactions
        assertTrue("the post carries reactions", reactions != null && reactions.results.isNotEmpty())
        assertEquals("one chip per reaction", 2, reactions!!.results.size)
        val chips = cell.reactionsLayoutInBubble
        harness.idle()
        println("reactions: chips drawn = " + !chips.isEmpty + " cell=" + cell.width)
        assertFalse("the cell drew the chips", chips.isEmpty)
        harness.renderApp("post-reactions")

        // The first chip is the heart, and a tap must go back to the id the site named.
        val first = reactions.results[0]
        val emoticon = (first.reaction as TLRPC.TL_reactionEmoji).emoticon
        assertEquals("the chip shows the reaction's glyph", "\u2764", emoticon)
        assertEquals("and the glyph maps back to the site's id", "heart", DiscourseReactions.idOf(emoticon))

        cell.delegate.didPressReaction(cell, first, false, 0f, 0f)
        val reached = runCatching {
            harness.await("the toggle to reach the site") {
                site.recordedRequests.any { it.startsWith("PUT /discourse-reactions/posts/") }
            }
        }
        println("reactions: requests = " + site.recordedRequests)
        reached.exceptionOrNull()?.let { throw it }
        val toggle = site.recordedRequests.first { it.startsWith("PUT /discourse-reactions/posts/") }
        println("reactions: toggle request = $toggle")
        assertTrue(
            "the toggle names the post and the reaction that was tapped: $toggle",
            toggle.startsWith("PUT /discourse-reactions/posts/1001/custom-reactions/heart/toggle.json"),
        )
        harness.assertNoTelegramSideEffects()
    }

    private fun rowCount(): Int = contentRowCount(harness)

    @Test
    fun longPressReadsOnlyTheChosenReactionUsersWithoutToggling() {
        site.serveFixture("discourse-reactions/posts/1001/reactions-users.json", "reaction-users.json")
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("post") { rowCount() == 1 }
        val cell = laidOutCells().first()
        val reaction = cell.messageObject.messageOwner.reactions.results.first()
        cell.delegate.didPressReaction(cell, reaction, true, 0f, 0f)
        harness.await("reactors request") {
            site.requestPaths.contains("discourse-reactions/posts/1001/reactions-users.json")
        }
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        harness.await("reactors header") { texts(dialog.window!!.decorView).any { it.contains("50 人回应") } }
        renderDialog(dialog, "reaction-users-loaded")
        val labels = texts(dialog.window!!.decorView)
        assertTrue("the chosen heart group is displayed", labels.any { it.contains("Baba") })
        assertFalse("the other group's users are not mixed in", labels.any { it.contains("Alice") })
        assertFalse("long press is a read, never a toggle", site.recordedRequests.any { it.startsWith("PUT ") })
        dialog.dismiss()
        if (ShadowConnectionsManager.SEND_REQUEST_CALLS > 0) println("native request trace: ${ShadowConnectionsManager.SEND_REQUEST_TRACE}")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aFailedReactorsReadCanRetryAndAnEmptyGroupDoesNotInventUsers() {
        val path = "discourse-reactions/posts/1001/reactions-users.json"
        site.serveResponse(path) { okhttp3.mockwebserver.MockResponse().setResponseCode(503) }
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("post") { rowCount() == 1 }
        val cell = laidOutCells().first()
        cell.delegate.didPressReaction(cell, cell.messageObject.messageOwner.reactions.results.first(), true, 0f, 0f)
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        harness.await("retry state") { texts(dialog.window!!.decorView).contains("加载失败，点击重试") }
        renderDialog(dialog, "reaction-users-error")
        site.serveResponse(path) { okhttp3.mockwebserver.MockResponse()
            .setHeader("Content-Type", "application/json").setBody("""{"reaction_users":[]}""") }
        fun state(view: android.view.View): org.telegram.ui.Components.EmptyTextProgressView? {
            if (view is org.telegram.ui.Components.EmptyTextProgressView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) state(view.getChildAt(i))?.let { return it }
            return null
        }
        state(dialog.window!!.decorView)!!.performClick()
        harness.await("empty state") { texts(dialog.window!!.decorView).contains("暂无回应者") }
        assertEquals(2, site.requestPaths.count { it == path })
        renderDialog(dialog, "reaction-users-empty")
        dialog.dismiss()
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun dismissingTheReactorsSheetDropsALateResponse() {
        val release = java.util.concurrent.CountDownLatch(1)
        val path = "discourse-reactions/posts/1001/reactions-users.json"
        site.serveJsonFor(path) {
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            harness.fixture("reaction-users.json")
        }
        try {
            harness.bootApp()
            harness.await("feed") { rowCount() == 2 }
            harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
            harness.await("post") { rowCount() == 1 }
            val cell = laidOutCells().first()
            cell.delegate.didPressReaction(cell, cell.messageObject.messageOwner.reactions.results.first(), true, 0f, 0f)
            harness.await("read in flight") { site.requestPaths.contains(path) }
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            renderDialog(dialog, "reaction-users-loading")
            dialog.dismiss()
            release.countDown()
            harness.idleClock(500)
            assertFalse(dialog.isShowing)
            assertFalse("dismissed sheet is not rebound", texts(dialog.window!!.decorView).any { it.contains("50 人回应") })
            assertFalse(site.recordedRequests.any { it.startsWith("PUT ") })
        } finally {
            release.countDown()
        }
    }

    @Test
    fun theRailOffersOnlyAllowedReactionsAndATapSendsTheChosenId() {
        val topic = org.json.JSONObject(harness.fixture("topic-detail.json"))
        topic.put("valid_reactions", org.json.JSONArray(listOf("heart", "confetti_ball", "tada")))
        site.serveJson("t/$TOPIC_ID.json", topic.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("post") { rowCount() == 1 }
        val cell = laidOutCells().first()
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        // The rail is upstream's own picker, hosted directly by the menu (no sheet step).
        val rail = popupRoot(harness.activity)?.let { findPicker(it) }
            ?: throw AssertionError("the menu hosted no reactions rail")
        harness.await("permitted reactions") { rail.allReactionsList.size == 3 }
        assertEquals(listOf("❤", "🎊", "🎉"), rail.allReactionsList.map { it.emojicon })
        val list = rail.recyclerListView
        layoutPopup(popupRoot(harness.activity)!!)
        // The failure this pins: upstream hands a reaction's picture to the loader under the "tgs"
        // extension it uses for Telegram's Lottie reactions. A plain PNG path tagged that way is
        // decoded as a Lottie animation, so a tile carries a non-null but fully transparent
        // drawable and the rail paints a blank capsule. A decoded picture is therefore not
        // enough - the picture the rail actually draws has to have pixels.
        harness.await("every permitted reaction tile draws an opaque picture") {
            (0..2).all { position -> pickerTileHasOpaquePixels(list, position) }
        }
        renderDialogForRail(rail, "reaction-rail")
        val tile = list.findViewHolderForAdapterPosition(1)!!.itemView
        list.clickItem(tile, 1)
        harness.await("chosen reaction toggle") {
            site.recordedRequests.any { it.startsWith("PUT /discourse-reactions/posts/1001/custom-reactions/confetti_ball/toggle.json") }
        }
        harness.assertNoTelegramSideEffects()
    }

    /** The menu's popup window: the top-level view the window manager was handed that carries a rail. */
    private fun popupRoot(activity: android.app.Activity): android.view.View? {
        val windowManager = org.robolectric.shadow.api.Shadow.extract(activity.windowManager)
            as org.robolectric.shadows.ShadowWindowManagerImpl
        for (view in windowManager.views.asReversed()) {
            if (view === activity.window.decorView) continue
            if (findPicker(view) != null) return view
        }
        return null
    }

    private fun layoutPopup(root: android.view.View) {
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        repeat(2) {
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.AT_MOST),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            harness.idle(40)
        }
    }

    private fun renderDialogForRail(rail: org.telegram.ui.Components.ReactionsContainerLayout, name: String) {
        val root = popupRoot(harness.activity) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/topic-fidelity-wave/interactions/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("rail render: $out")
    }

    private fun findPicker(view: android.view.View): org.telegram.ui.Components.ReactionsContainerLayout? {
        if (view is org.telegram.ui.Components.ReactionsContainerLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPicker(view.getChildAt(i))?.let { return it }
        return null
    }

    /**
     * Whether the picture the picker draws for [position] really has pixels: the tile's visible
     * image view is attached and its decoded picture carries at least one opaque pixel. A tile that
     * decoded an empty Lottie drawable is non-null but fully transparent - the blank capsule this
     * guards against.
     */
    private fun pickerTileHasOpaquePixels(
        list: org.telegram.ui.Components.RecyclerListView, position: Int,
    ): Boolean {
        val row = list.findViewHolderForAdapterPosition(position)?.itemView
            as? org.telegram.ui.Components.ReactionsContainerLayout.ReactionHolderView ?: return false
        val view = if (row.loopImageView.visibility == android.view.View.VISIBLE) row.loopImageView
            else row.enterImageView
        if (view.visibility != android.view.View.VISIBLE || !view.isAttachedToWindow) return false
        val bitmap = view.imageReceiver.bitmap ?: return false
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return false
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) ushr 24 != 0) return true
            }
        }
        return false
    }

    private fun texts(view: android.view.View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun renderDialog(dialog: android.app.Dialog, name: String) {
        val view = dialog.window!!.decorView
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        repeat(2) {
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            harness.idle(40)
        }
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(root, ".amp/in/artifacts/folders-reactions-wave/reactions/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("reaction render: $out")
    }

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 606L
    }
}
