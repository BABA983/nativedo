package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.Post
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
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ChatActivityEnterView
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * The composer's `@mention` autocomplete, end to end: a real Topic screen drives a real
 * [`/u/search/users`] request through the module's own retrofit client, and the panel upstream's
 * `MentionsContainerView` owns draws the rows.
 *
 * <p>Each test names the failure mode it pins: an `@` in an email or a URL opening the panel, a
 * slow abandoned answer replacing a newer one, a caret in the middle of a name rewriting the wrong
 * text, a new topic sending a made-up context, and a panel reopened (or left up) after the screen is
 * paused or gone.</p>
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
internal class DiscourseMentionsTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private var mentions: DiscourseMentions? = null
    private var owner: BaseFragment? = null

    /** A template of this run's own, so another test's cached avatar is never drawn here. */
    private val avatarTemplate = "/user_avatar/linux.do/orchid.hu/{size}/${System.nanoTime()}.png"

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("u/search/users", harness.fixture("mention-users.json"))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        owner?.onFragmentDestroy()
        site.stop()
    }

    @Test
    fun blankAtShowsLocalParticipantsWhileTheTopicAwareAnswerArrives() {
        val screen = openTopic()
        mentions = install(screen, participants = topicParticipants())

        type(screen.composer, "@")
        // Before any request leaves, the topic's participants and loaded authors are already shown.
        val localCount = panelCount()
        println("mentions: local rows after '@' = $localCount requests=${site.requestPaths}")
        assertTrue("a blank @ shows the topic's own candidates at once", localCount >= 3)
        assertTrue("and the panel is up", mentions!!.panel().isShowing())

        settle()
        harness.await("the site's ranked answer to merge in") { panelCount() >= 4 }
        val usernames = rows().map { it.username }
        println("mentions: merged rows = $usernames")
        // The site's ranked answer leads (its user, then its mentionable group); the page's own
        // authors the site did not name follow it, so a long author list cannot starve the groups.
        assertEquals(listOf("orchid.hu", "team", "bob", "carol"), usernames)
        harness.renderApp("mentions-blank-at")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun serverOrderingAndMentionableGroupsArePreserved() {
        val screen = openTopic()
        mentions = install(screen, participants = emptyList())

        type(screen.composer, "@or")
        settle()
        harness.await("the site's answer") { panelCount() >= 2 }
        val rows = rows()
        println("mentions: server-ordered rows = ${rows.map { it.username to it.isGroup }}")
        assertEquals(listOf("orchid.hu", "team"), rows.map { it.username })
        assertTrue("the mentionable group is kept as a group", rows[1].isGroup)
        harness.renderApp("mentions-groups")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anAtSignInsideAnEmailOrUrlNeverOpensThePanel() {
        val screen = openTopic()
        mentions = install(screen, participants = topicParticipants())

        type(screen.composer, "write to me@example.com")
        assertFalse("an @ inside an email opens nothing", mentions!!.panel().isShowing())
        assertEquals(0, site.requestPaths.count { it == "u/search/users" })

        type(screen.composer, "see https://linux.do/u/@baba")
        assertFalse("an @ inside a URL path opens nothing", mentions!!.panel().isShowing())
        harness.idleClock(400)
        assertEquals(
            "and no topic-aware request was sent either",
            0,
            site.requestPaths.count { it == "u/search/users" },
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun pickingACandidateRewritesOnlyTheTokenUnderTheCaret() {
        val screen = openTopic()
        mentions = install(
            screen,
            participants = listOf(MentionCandidate(username = "alice", displayName = "Alice")),
        )
        val field = screen.composer.editField!!
        // A caret in the middle of @ali, with a suffix that must survive untouched.
        field.setText("say @ali later")
        field.setSelection(8)
        harness.idle()
        settle()
        harness.await("the matching candidate") { panelCount() >= 1 }
        println(
            "mentions: pick state sel=${field.selectionStart} " +
                "token=${mentionTokenAt(field.text, field.selectionStart)} " +
                "rows=${rows().map { it.username }}",
        )

        val listener = mentions!!.panel().listView.onItemClickListener
            ?: throw AssertionError("the panel has no row listener")
        // Pick the local candidate by name, not by a position: the server's ranked rows lead the
        // merge, so where "alice" lands is the merge's business, not this test's.
        val aliceRow = rows().indexOfFirst { it.username == "alice" }
        assertTrue("the local candidate is offered", aliceRow >= 0)
        listener.onItemClick(View(screen.composer.context), aliceRow + 1)
        harness.idle()
        println("mentions: field after picking a candidate = '${field.text}'")
        assertEquals("say @alice later", field.text.toString())
        assertEquals("the caret follows the inserted mention", 10, field.selectionStart)
        assertFalse("the panel closes after a pick", mentions!!.panel().isShowing())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anAbandonedSlowAnswerCannotReplaceTheNewerOne() {
        val screen = openTopic()
        // The first query's answer is held back with a body delay; the second's is immediate. A
        // panel that applied whichever answer landed last would end up on the abandoned token.
        var first = true
        site.serveResponse("u/search/users") {
            val slow = first
            first = false
            val body = if (slow) jsonResponse("alice") else jsonResponse("bob")
            val response = MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body)
            if (slow) response.setBodyDelay(700, TimeUnit.MILLISECONDS)
            response
        }
        mentions = install(screen, participants = emptyList())

        type(screen.composer, "@al")
        harness.idleClock(300) // the slow request leaves
        type(screen.composer, "@bo")
        harness.idleClock(300) // the fast request leaves and answers
        harness.await("the newer answer") { rows().any { it.username == "bob" } }
        harness.idleClock(1000) // the slow request's body finally arrives
        harness.await("the late body to be drained") { true }
        harness.idleClock(200)

        val usernames = rows().map { it.username }
        println("mentions: rows after the late answer landed = $usernames")
        assertEquals("only the newer token's candidate is shown", listOf("bob"), usernames)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun pausingSilencesTheFieldUntilResumeBringsThePanelBack() {
        val screen = openTopic()
        mentions = install(screen, participants = topicParticipants())

        type(screen.composer, "@")
        assertTrue(mentions!!.panel().isShowing())

        mentions!!.onPause()
        harness.idle()
        assertFalse("a paused screen shows no panel", mentions!!.panel().isShowing())

        // A write into the composer while the screen is behind another (the rich editor applies
        // back into the field) must not reopen the panel or send a query.
        val before = site.requestPaths.count { it == "u/search/users" }
        type(screen.composer, "@or")
        harness.idleClock(400)
        assertFalse(
            "a paused controller does not reopen on a field edit",
            mentions!!.panel().isShowing(),
        )
        assertEquals(
            "and sends no query while paused",
            before,
            site.requestPaths.count { it == "u/search/users" },
        )

        mentions!!.onResume()
        harness.idle()
        assertTrue(
            "resuming answers the token under the caret again",
            mentions!!.panel().isShowing(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun destroyLeavesNoWatcherBehind() {
        val screen = openTopic()
        mentions = install(screen, participants = topicParticipants())

        type(screen.composer, "@")
        mentions!!.onDestroy()
        mentions = null
        harness.idle()
        val before = site.requestPaths.count { it == "u/search/users" }
        type(screen.composer, "@ab")
        harness.idleClock(400)
        assertEquals(
            "a destroyed controller leaves no watcher behind",
            before,
            site.requestPaths.count { it == "u/search/users" },
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aNewTopicAsksWithoutAFakeTopicId() {
        site.serveFixture("categories.json", "categories.json")
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val fragment = NewTopicFragment.create()
        harness.present(fragment)
        harness.layoutApp()
        owner = fragment
        val screen = Screen(fragment, fragment.fragmentView as ViewGroup,
            viewTree().filterIsInstance<ChatActivityEnterView>().last())
        mentions = install(screen, participants = emptyList())

        type(screen.composer, "@a")
        settle()
        harness.await("the new-topic search") { site.queriesFor("u/search/users").isNotEmpty() }
        val query = site.queriesFor("u/search/users").last()
        println("mentions: new-topic query = $query")
        assertFalse("a new topic sends no topic context at all", query.contains("topic_id"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theCandidatesAvatarIsFetchedThroughTheBridgeAndDrawn() {
        val screen = openTopic()
        site.serveImage(site.avatarPath(avatarTemplate), magentaPng())
        site.serveResponse("u/search/users") {
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse("orchid.hu"))
        }
        mentions = install(
            screen,
            participants = topicParticipants(),
        )

        type(screen.composer, "@or")
        settle()
        harness.await("the site's answer") { panelCount() >= 1 }
        println(
            "mentions: avatar state showing=${mentions!!.panel().isShowing()} " +
                "rows=${panelCount()} children=${mentions!!.panel().listView.childCount} " +
                "requests=${site.requestPaths.filter { it.startsWith("user_avatar") }}",
        )
        val user = DiscourseAvatars.user("orchid.hu", "Orchid Hu", avatarTemplate, site.baseUrl)
        harness.await("the avatar to land where upstream looks for it") { DiscourseAvatars.isCached(user) }
        harness.await("the row to hold the picture it downloaded", iterations = 200) {
            harness.drawApp()
            magentaPixels(harness.renderApp("mentions-avatar")) > 200
        }
        val drawn = magentaPixels(harness.renderApp("mentions-avatar"))
        println("mentions: magenta pixels in the panel render = $drawn")
        assertTrue("the row drew the avatar the bridge fetched", drawn > 200)
        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers ----

    private fun openTopic(topicId: Long = TOPIC_ID, waitForPosts: Boolean = true): Screen {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(topicId, "Mentions"))
        if (waitForPosts) {
            harness.await("the topic to load") { rowCount() == 1 }
        } else {
            harness.await("the screen to arrive") { harness.visibleFragment() is TopicFragment }
        }
        harness.layoutApp()
        val fragment = harness.visibleFragment() as TopicFragment
        owner = fragment
        val host = fragment.fragmentView as ViewGroup
        val composer = viewTree().filterIsInstance<ChatActivityEnterView>().lastOrNull()
            ?: throw AssertionError("the screen has no composer")
        return Screen(fragment, host, composer)
    }

    private fun install(
        screen: Screen,
        participants: List<MentionCandidate>,
    ): DiscourseMentions {
        // Use the controller installed by the production screen, never a second panel/watcher.
        val toolsField = screen.fragment.javaClass.getDeclaredField("composerTools").apply { isAccessible = true }
        val controller = (toolsField.get(screen.fragment) as DiscourseComposerTools).mentions
        controller.setLocalCandidates(participants, loadedPosts())
        return controller
    }

    private fun type(composer: ChatActivityEnterView, text: String) {
        val field = composer.editField ?: return
        field.setText(text)
        field.setSelection(text.length)
        harness.idle()
    }

    /** Past the debounce, then wait out the site's answer on real time. */
    private fun settle() {
        harness.idleClock(300)
        harness.await("the mention search to leave") {
            site.requestPaths.any { it == "u/search/users" }
        }
        harness.idleClock(200)
        harness.layoutApp()
    }

    private fun panelCount(): Int =
        (mentions!!.panel().adapter as DiscourseMentionsAdapter).candidateCount()

    private fun rows(): List<MentionCandidate> {
        val adapter = mentions!!.panel().adapter as DiscourseMentionsAdapter
        return (0 until adapter.candidateCount()).mapNotNull { adapter.itemAt(it) }
    }

    private fun topicParticipants(): List<MentionCandidate> = listOf(
        MentionCandidate(
            username = "orchid.hu",
            displayName = "Orchid Hu",
            avatarTemplate = avatarTemplate,
        ),
    )

    private fun loadedPosts(): List<Post> = listOf(
        Post(id = 1, authorUsername = "bob", authorDisplayName = "Bob", cookedHtml = ""),
        Post(id = 2, authorUsername = "carol", authorDisplayName = "Carol", cookedHtml = ""),
    )

    private fun jsonResponse(user: String): String =
        """{"users":[{"username":"$user","name":"$user","avatar_template":"$avatarTemplate","priority_group":0}],""" +
            """"groups":[]}"""

    private fun magentaPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(255, 0, 255))
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun magentaPixels(file: java.io.File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) > 200 && Color.blue(it) > 200 && Color.green(it) < 80 }
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun viewTree(): List<View> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(content)
        return found
    }

    private data class Screen(
        val fragment: BaseFragment,
        val host: ViewGroup,
        val composer: ChatActivityEnterView,
    )

    private companion object {
        const val TOPIC_ID = 445566L
    }
}
