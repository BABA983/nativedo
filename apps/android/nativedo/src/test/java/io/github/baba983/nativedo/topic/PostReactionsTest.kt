package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
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

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

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
