package io.github.baba983.nativedo.topic

import android.os.SystemClock
import android.view.MotionEvent
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * A floor that carries a poll, end to end: the site's `polls` payload becomes the poll cell upstream
 * draws, inside the floor's run of bubbles, and a tap on an answer votes on the site and updates the
 * floor from the site's answer.
 *
 * <p>The failure this pins: upstream decides what a message is once, from its media
 * (`MessageObject.setType()` reads `rich_message` before it ever looks at a poll), so a body that
 * has both text and a poll cannot be one message - a client that built one bubble would show the
 * text and silently drop the poll. The other half is the vote: the counts and the reader's own
 * choice come from the post's `polls`/`polls_votes`, not from the cooked HTML, and the answer to a
 * vote is the only place the new counts are known.</p>
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
internal class PollTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.servePostFixture(TOPIC_ID, "post-polls.json")
        site.serveFixture("polls/vote", "poll-vote-response.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theTextAroundThePollIsItsOwnBubblesAndThePollIsUpstreamsPollCell() {
        openTopic()

        val cells = laidOutCells()
        assertEquals("the body around the poll and the poll itself", 3, cells.size)
        assertTrue("the first bubble is the body's own text", isRichText(cells[0].messageObject!!))
        assertTrue("the last bubble is the body's own text", isRichText(cells[2].messageObject!!))
        assertEquals(
            "the middle bubble is the poll",
            MessageObject.TYPE_POLL,
            cells[1].messageObject!!.type,
        )

        val media = MessageObject.getMedia(cells[1].messageObject!!.messageOwner) as TLRPC.TL_messageMediaPoll
        assertEquals(
            "the site's own poll title is the bubble's question",
            "1. Frontend work",
            media.poll.question.text,
        )
        assertEquals("the site's options", 2, media.poll.answers.size)
        assertEquals(
            "Tool A",
            media.poll.answers[0]
                .text.text,
        )
        assertEquals(
            "Tool B",
            media.poll.answers[1]
                .text.text,
        )
        assertEquals("the digest the site votes by travels as the option", OPTION_A, digestOf(media.poll.answers[0]))
        assertEquals("the counts come from polls[].options[].votes", 12, media.results.results[0].voters)
        assertEquals(5, media.results.results[1].voters)
        assertEquals("and the poll's own total", 17, media.results.total_voters)
        assertEquals("a public poll, as the site declared it", true, media.poll.public_voters)
        assertFalse("nothing has been voted yet", cells[1].messageObject!!.isVoted())
        assertFalse(
            "a poll bubble offers no forward arrow",
            cells.any { it.messageObject!!.needDrawShareButton() },
        )

        // The run of one author's bubbles, the way upstream's own chat groups them: a bubble joined
        // to the one above it is pinned (upstream's own flag) on top, one joined to the one below is
        // pinned at the bottom, and only the run's first and last keep their rounded ends.
        assertFalse("the first bubble starts the run", cells[0].isPinnedTop)
        assertTrue("and is joined to the one below", cells[0].isPinnedBottom)
        assertTrue("the poll is joined to the bubble above", cells[1].isPinnedTop)
        assertTrue("and to the one below", cells[1].isPinnedBottom)
        assertTrue("the last bubble is joined to the one above", cells[2].isPinnedTop)
        assertFalse("and ends the run", cells[2].isPinnedBottom)

        harness.drawApp()
        harness.renderApp("poll-group")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site has no cap on a poll's options, and Telegram's cell sizes its answer checkboxes from
     * its own cap (12). A poll with more options than that must still draw every option and keep
     * every answer tappable; the crash this pins is the cell indexing its shorter array.
     */
    @Test
    fun aPollWithMoreAnswersThanTelegramsCapStillDrawsEveryAnswer() {
        site.servePostFixture(TOPIC_ID, "post-polls-many.json")
        openTopic()

        val pollCell = laidOutCells().firstOrNull { it.messageObject?.type == MessageObject.TYPE_POLL }
            ?: throw AssertionError("the poll drew no cell")
        val media = MessageObject.getMedia(pollCell.messageObject!!.messageOwner) as TLRPC.TL_messageMediaPoll
        assertEquals("every option the site sent is drawn", MANY_ANSWERS, media.poll.answers.size)
        assertEquals("and each one has its own answer button", MANY_ANSWERS, pollCell.pollButtons.size)
        harness.drawApp()
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun tappingAnAnswerVotesOnTheSiteAndTheAnswerUpdatesTheFloor() {
        openTopic()
        harness.drawApp()
        harness.renderApp("poll-before-vote")

        val pollCell = laidOutCells().first { it.messageObject!!.type == MessageObject.TYPE_POLL }
        tapFirstAnswer(pollCell)

        val reached =
            runCatching {
                harness.await("the vote to reach the site") {
                    site.recordedRequests.any { it.startsWith("PUT /polls/vote") }
                }
            }
        println("poll: requests = " + site.recordedRequests)
        reached.exceptionOrNull()?.let { throw it }

        val body = site.requestBodyFor("polls/vote").orEmpty()
        println("poll: vote body = $body csrf=${site.csrfHeaderFor("polls/vote")}")
        assertTrue("the vote names the post: $body", body.contains("post_id=2001"))
        assertTrue("and the poll: $body", body.contains("poll_name=frontend"))
        assertTrue("and the option's digest, not its label: $body", body.contains("options%5B%5D=$OPTION_A"))
        assertEquals("the write carries the session's CSRF token", "csrf-token-demo", site.csrfHeaderFor("polls/vote"))

        // The site's answer is the poll's new state, and the floor is rebuilt from it: a new row
        // carries the new counts and the reader's own choice, without the topic being read again.
        val voted =
            runCatching {
                harness.await("the answer to reach the floor") {
                    val cell = laidOutCells().firstOrNull { it.messageObject!!.type == MessageObject.TYPE_POLL }
                    cell?.messageObject?.isVoted() == true
                }
            }
        voted.exceptionOrNull()?.let { throw it }

        val votedCell = laidOutCells().first { it.messageObject!!.type == MessageObject.TYPE_POLL }
        val media = MessageObject.getMedia(votedCell.messageObject!!.messageOwner) as TLRPC.TL_messageMediaPoll
        assertEquals("the chosen answer's new count", 13, media.results.results[0].voters)
        assertEquals("the poll's new total", 18, media.results.total_voters)
        assertEquals(
            "the reader's own choice is what the site answered with",
            OPTION_A,
            digestOf(media.poll.answers[0]),
        )
        assertTrue("and it is marked as chosen", media.results.results[0].chosen)
        assertTrue("the row was rebuilt, not just refreshed", votedCell !== pollCell)

        harness.drawApp()
        harness.renderApp("poll-after-vote")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site's own title travels in two places: `polls[].title` (which the bubble draws above the
     * options) and a `div.poll-title` in the body's markup. Discourse's own cook puts that markup
     * inside the poll, and a payload that carries it next to the poll instead must not end up
     * saying the title twice - while a poll the site left untitled must keep whatever the body has.
     */
    @Test
    fun aTitledPollIsDrawnWithItsTitleAndTheBodyDoesNotSayItAgain() {
        val body = """<p>Before.</p>
            <div class="poll-title">Which tool?</div>
            <div class="poll" data-poll-name="tools"><div class="poll-container"><ul>
            <li data-poll-option-id="d">A</li></ul></div></div>
            <p>After.</p>"""

        val titled = DiscoursePostBody.segments(body, mapOf("tools" to "Which tool?"))
        assertEquals("text, poll, text", 3, titled.size)
        assertFalse("the body does not say the title again", titled[0].html.contains("Which tool?"))
        assertTrue("and keeps the rest of its text", titled[0].html.contains("Before."))
        assertTrue("and the text after the poll", titled[2].html.contains("After."))

        val untitled = DiscoursePostBody.segments(body)
        assertTrue(
            "a poll with no site title keeps whatever the body carries",
            untitled[0].html.contains("Which tool?"),
        )
    }

    /** One tap on the first answer of a poll cell, the way a finger reaches the cell's own button. */
    private fun tapFirstAnswer(cell: ChatMessageCell) {
        harness.idle()
        val button = cell.pollButtons.firstOrNull() ?: throw AssertionError("the poll drew no answers")
        val x = (button.x + AndroidUtilities.dp(10f)).toFloat()
        val y = (button.y + cell.namesOffset - AndroidUtilities.dp(13f) + AndroidUtilities.dp(6f)).toFloat()
        val downTime = SystemClock.uptimeMillis()
        cell.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        harness.idle()
        cell.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0))
        harness.idle()
    }

    private fun openTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Poll"))
        // The list's rows include the day's own date row; what the screen draws for the floor is
        // the run of bubbles, so the wait is on those.
        harness.await("the topic to load") { laidOutCells().size == 3 }
    }

    private fun digestOf(answer: TLRPC.PollAnswer): String = String(answer.option, java.nio.charset.StandardCharsets.UTF_8)

    /** A rich body drew as its own bubble, not as a poll: the article upstream imports for it. */
    private fun isRichText(message: MessageObject): Boolean =
        message.type == MessageObject.TYPE_ARTICLE && message.messageOwner.rich_message != null

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 2001L
        const val OPTION_A = "e539a9df8700d0d05c69356a07b768cf"

        /** The options of the many-answer poll fixture: more than Telegram's 12-answer cap. */
        const val MANY_ANSWERS = 13
    }
}
