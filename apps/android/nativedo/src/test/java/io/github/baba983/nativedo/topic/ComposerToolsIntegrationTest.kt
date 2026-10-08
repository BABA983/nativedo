package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.shadows.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.MentionCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.chat.ChatInputViewsContainer
import java.io.ByteArrayOutputStream
import java.io.File

/** Production screens, not manually mounted panels: entry, insertion, insets and lifecycle. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class, ShadowNativeByteBuffer::class, ShadowUtilities::class,
        ShadowMediaController::class, ShadowIntro::class, ShadowMrzRecognizer::class,
        ShadowRLottieNative::class, ShadowAnimatedFileNative::class, ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class, ShadowSQLitePreparedStatement::class,
    ],
)
internal class ComposerToolsIntegrationTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A native composer over a Discourse post.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("u/search/users", harness.fixture("mention-users.json"))
        site.serveJson("emojis.json", """{"custom":[{"name":"site_wave","url":"${site.url("emoji/site-wave.png")}"}]}""")
        val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(245, 170, 30))
        site.serveImage("emoji/site-wave.png", ByteArrayOutputStream().also {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray())
        image.recycle()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        harness.visibleFragment()?.onFragmentDestroy()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        site.stop()
    }

    /**
     * A candidate list taller than the panel's host must not have its first row sliced: the band
     * has to reach the first candidate instead of clamping to a short host edge below it. Device
     * screenshots `11-mentions-light` / `24-mentions-dark` showed the top row cut (and only 2 of 4
     * rows whole) because the panel host was shorter than the list's own scroll padding, which
     * over-shifts the first row above the panel's top.
     */
    @Test fun mentionPanelDoesNotSliceTheFirstCandidate() {
        site.serveJson("u/search/users", MENTION_FOUR)
        val fragment = open(true)
        val composer = composer(fragment)
        val field = composer.editField!!
        field.setText("hello @a")
        field.setSelection(field.length())
        harness.idleClock(400)
        val panel = descendants(fragment.fragmentView).filterIsInstance<DiscourseMentionsPanel>().single()
        harness.await("four candidates") {
            (panel.adapter as DiscourseMentionsAdapter).candidateCount() == 4 && panel.isShowing
        }
        harness.idleClock(600)
        harness.layoutApp()

        val rows = descendants(panel).filterIsInstance<MentionCell>()
        assertEquals("all four candidates are attached", 4, rows.size)
        val panelTop = IntArray(2).also { panel.getLocationInWindow(it) }[1]
        val panelBottom = panelTop + panel.height
        val firstTop = IntArray(2).also { rows[0].getLocationInWindow(it) }[1]
        assertTrue(
            "the first candidate is not sliced by the panel's clip band " +
                "(first row top $firstTop, panel top $panelTop)",
            firstTop >= panelTop,
        )
        assertTrue(
            "the first candidate is fully drawn inside the panel",
            firstTop + rows[0].height <= panelBottom,
        )
        val fieldTop = IntArray(2).also { field.getLocationInWindow(it) }[1]
        assertTrue("the candidate band still stops above the capsule", panelBottom <= fieldTop)
        retainInFixFolder("production-new-topic-mentions-four")
    }

    @Test fun theReplyCapsuleOpensSiteEmojiAndBackKeepsTheTopic() = checkEmoji(false)
    @Test fun theNewTopicCapsuleOpensSiteEmojiAndBackKeepsTheDraft() = checkEmoji(true)
    @Test fun replyMentionsFollowTheCapsuleAndPauseSilencesDraftWrites() = checkMentions(false)
    @Test fun newTopicMentionsHaveNoInventedTopicContext() = checkMentions(true)

    private fun checkEmoji(newTopic: Boolean) {
        val fragment = open(newTopic)
        val composer = composer(fragment)
        val field = composer.editField!!
        field.setText("before selected after")
        field.setSelection(7, 15)
        val input = descendants(fragment.fragmentView).filterIsInstance<ChatInputViewsContainer>().single()
        if (newTopic) assertFalse("an unopened picker does not fetch a catalog",
            site.requestPaths.contains("emojis.json"))
        composer.emojiButton.performClick()
        val picker = descendants(fragment.fragmentView).filterIsInstance<DiscourseEmojiPicker>().single()
        harness.await("the production emoji grid") { grid(picker).adapter!!.itemCount == 1 }
        harness.layoutApp()
        assertTrue("the smile button opens the site picker", picker.isOpen())
        assertTrue("the in-app panel lifts the capsule", input.inputBubbleBottom < input.height - AndroidUtilities.dp(200f))
        val grid = grid(picker)
        harness.await("the selectable emoji tile") { harness.layoutApp(); grid.getChildAt(0) != null }
        grid.onItemClickListener.onItemClick(grid.getChildAt(0), 0)
        assertEquals("before :site_wave: after", field.text.toString())
        retain("${label(newTopic)}-emoji")
        assertFalse("predictive Back is claimed without closing the panel", fragment.onBackPressed(false))
        assertTrue("a predictive probe has no side effect", picker.isOpen())
        assertFalse("invoked Back closes the panel, not its owner", fragment.onBackPressed(true))
        harness.idleClock(500)
        harness.layoutApp()
        assertFalse(picker.isOpen())
        assertSame(fragment, harness.visibleFragment())
        assertEquals("the draft survives panel dismissal", "before :site_wave: after", field.text.toString())
        assertTrue("Back releases the inset without waiting for an IME", input.inputBubbleBottom > input.height - AndroidUtilities.dp(100f))
        harness.assertNoTelegramSideEffects()
    }

    private fun checkMentions(newTopic: Boolean) {
        val fragment = open(newTopic)
        val composer = composer(fragment)
        val field = composer.editField!!
        field.setText("hello @or")
        field.setSelection(field.length())
        harness.idleClock(300)
        val panel = descendants(fragment.fragmentView).filterIsInstance<DiscourseMentionsPanel>().single()
        harness.await("automatic site candidates") {
            (panel.adapter as DiscourseMentionsAdapter).candidateCount() == 2 && panel.isShowing
        }
        val adapter = panel.adapter as DiscourseMentionsAdapter
        assertEquals(listOf("orchid.hu", "team"), (0..1).map { adapter.itemAt(it)!!.username })
        val query = site.queriesFor("u/search/users").last()
        if (newTopic) assertFalse(query.contains("topic_id"))
        else assertTrue(query.contains("topic_id=$TOPIC_ID"))

        // Unequal nav/IME heights detect double-counting and a panel left at the screen bottom.
        for (imeHeight in listOf(0, AndroidUtilities.dp(271f))) {
            val nav = AndroidUtilities.dp(23f)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, nav))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                .build()
            ViewCompat.dispatchApplyWindowInsets(fragment.fragmentView, insets)
            harness.idleClock(500)
            harness.layoutApp()
            val panelLocation = IntArray(2).also { panel.getLocationInWindow(it) }
            val fieldLocation = IntArray(2).also { field.getLocationInWindow(it) }
            assertTrue("candidates remain above the capsule, with IME=$imeHeight",
                panelLocation[1] + panel.height <= fieldLocation[1])
        }
        retain("${label(newTopic)}-mentions")

        fragment.onPause()
        harness.idleClock(300)
        val before = site.queriesFor("u/search/users").size
        field.setText("applied editor text @ab")
        field.setSelection(field.length())
        harness.idleClock(400)
        assertFalse("editor writes cannot open a paused parent's panel", panel.isShowing)
        assertEquals("editor writes cannot search behind the active screen", before, site.queriesFor("u/search/users").size)
        fragment.onResume()
        harness.idleClock(300)
        harness.await("mention search resumes with the owner") { site.queriesFor("u/search/users").size > before }
        harness.await("the resumed answer reaches the visible panel") { panel.isShowing }
        assertFalse(fragment.onBackPressed(false))
        assertTrue(panel.isShowing)
        assertFalse(fragment.onBackPressed(true))
        assertFalse(panel.isShowing)
        assertSame(fragment, harness.visibleFragment())
        harness.assertNoTelegramSideEffects()
    }

    private fun open(newTopic: Boolean): BaseFragment {
        harness.bootApp()
        harness.await("home feed") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        val fragment = if (newTopic) NewTopicFragment.create() else TopicFragment.forTopic(TOPIC_ID, "Composer tools")
        // Exercise newly constructed tools in both themes without changing the system under test.
        if (newTopic) Theme.applyTheme(Theme.getTheme("Dark Blue"), false, false)
        harness.present(fragment)
        harness.await("composer owner") { harness.visibleFragment() === fragment }
        if (!newTopic) harness.await("posts") { contentListOf(harness.activity)?.adapter?.itemCount == 1 }
        harness.layoutApp()
        return fragment
    }

    private fun composer(fragment: BaseFragment) = descendants(fragment.fragmentView)
        .filterIsInstance<ChatActivityEnterView>().single()
    private fun grid(picker: DiscourseEmojiPicker) = descendants(picker).filterIsInstance<RecyclerListView>().single()
    private fun label(newTopic: Boolean) = if (newTopic) "new-topic" else "reply"
    private fun retain(name: String) {
        val file = harness.renderApp("production-$name")
        val out = File("../../../.amp/in/artifacts/composer-next-wave/integration").apply { mkdirs() }
        file.copyTo(File(out, "$name.png"), overwrite = true)
    }

    /** The retained screenshot for the mention-panel layout fix. */
    private fun retainInFixFolder(name: String) {
        val file = harness.renderApp(name)
        val out = File("../../../.amp/in/artifacts/composer-next-wave/mentions-layout-fix").apply { mkdirs() }
        file.copyTo(File(out, "$name.png"), overwrite = true)
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    private companion object {
        const val TOPIC_ID = 445566L
        const val MENTION_FOUR = """{"users":[
            {"username":"orchid.hu","name":"Orchid Hu","avatar_template":"/user_avatar/linux.do/orchid.hu/{size}/1.png","priority_group":0},
            {"username":"teamlead","name":"Team Lead","avatar_template":"","priority_group":0},
            {"username":"alpha","name":"Alpha","avatar_template":"","priority_group":0},
            {"username":"beta","name":"Beta","avatar_template":"","priority_group":0}
        ],"groups":[]}"""
    }
}
