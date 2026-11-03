package io.github.baba983.nativedo.topic

import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * The site's user titles beside a post's author name.
 *
 * <p>Discourse answers each post with the author's own {@code user_title}; the chat cell already has
 * a slot for a short tag beside the name (Telegram's admin rank), so the title is written there
 * rather than drawn by a second piece of layout. The site's theme paints a few titles with an
 * animated gradient - the web's `user-title--种子用户` rule - and those arrive as text too, so the
 * gradient is applied to the tag's paint, not to the wire.</p>
 *
 * <p>Failures this pins: a title that is mapped but never drawn; a holographic title rendered as
 * the plain muted tag; and a gradient that never advances while such a post is on screen.</p>
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
internal class PostUserTitleTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveJsonFor("t/$TOPIC_ID.json") { topicJson() }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        runCatching { harness.fragmentStack().forEach { fragment -> fragment.onFragmentDestroy() } }
        runCatching { harness.idle() }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * Both posts carry a title: the poster's holographic one is painted with a moving gradient,
     * the other keeps the plain muted tag.
     */
    @Test
    fun aPostCarriesItsAuthorsTitleAsTheChatsNameTag() {
        openTopic()

        val holographic = cellFor("种子用户")
        val plain = cellFor("骐骥驰骋")

        assertEquals(
            "the title is the tag text the cell draws",
            "种子用户",
            tagLayout(holographic).text.toString(),
        )
        assertEquals(
            "the other title is drawn too",
            "骐骥驰骋",
            tagLayout(plain).text.toString(),
        )
        assertNotNull(
            "the holographic tag paints its text with a shader",
            tagPaint(holographic).shader,
        )
        assertNull(
            "an ordinary title keeps the chat's plain tag paint",
            tagPaint(plain).shader,
        )

        harness.layoutApp()
        harness.renderApp("post-user-title")

        // The gradient travels while the post is on screen: the phase is what every animated tag
        // reads, and the screen's frame loop is what moves it.
        val phases = linkedSetOf<Float>()
        repeat(6) {
            phases += DiscourseUserTitles.phase
            harness.idleClock(300)
        }
        assertTrue("the gradient advances while the post is on screen: $phases", phases.size > 1)

        // A long title is one line of tag, and the name keeps its own ellipsis instead of being
        // starved by it: the two bounds the web's plain text does not have.
        val long = cellById(LONG_ID)
        val longTag = tagLayout(long)
        val longName = field(long, "nameLayout") as StaticLayout
        println(
            "title: long tag=\"${longTag.text}\" lines=${longTag.lineCount} " +
                "width=${longTag.getLineWidth(0)} name=\"${longName.text}\"",
        )
        assertEquals("a long tag stays on one line", 1, longTag.lineCount)
        assertTrue(
            "a long tag is ellipsized at the tag's own bound",
            longTag.text.length < LONG_TITLE.length,
        )
        assertTrue(
            "the tag never grows past its bound",
            longTag.getLineWidth(0) <= AndroidUtilities.dp(100f) + 2,
        )
        assertEquals("the name line never wraps", 1, longName.lineCount)
        assertTrue(
            "the author's name is ellipsized rather than emptied",
            longName.text.isNotEmpty() && longName.text.length < LONG_NAME.length,
        )
        harness.layoutApp()
        harness.renderApp("post-user-title-long")
        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers -------------------------------------------------------------

    private fun openTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { contentRowCount(harness) == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Titles"))
        harness.await("the topic") { contentRowCount(harness) == 3 }
        harness.layoutApp()
        harness.idleClock(300)
    }

    /** The drawn cell whose message id is [id]. */
    private fun cellById(id: Long): ChatMessageCell {
        val list = listView() ?: throw AssertionError("no topic list")
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index) as? ChatMessageCell ?: continue
            if (cell.messageObject?.messageOwner?.id?.toLong() == id) {
                return cell
            }
        }
        throw AssertionError("no cell draws message $id")
    }

    /** The drawn cell whose tag is [title]. */
    private fun cellFor(title: String): ChatMessageCell {
        val list = listView() ?: throw AssertionError("no topic list")
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index) as? ChatMessageCell ?: continue
            val tag = fieldOrNull(cell, "adminLayout") as? StaticLayout ?: continue
            if (tag.text.toString() == title) {
                return cell
            }
        }
        throw AssertionError("no cell draws the tag \"$title\"")
    }

    /** The layout the cell draws the tag with, at the top-right of the name line. */
    private fun tagLayout(cell: ChatMessageCell): StaticLayout =
        field(cell, "adminLayout") as StaticLayout

    /** The tag's paint after its own spans applied it, as the cell draws it. */
    private fun tagPaint(cell: ChatMessageCell): TextPaint {
        val text = tagLayout(cell).text
        val paint = TextPaint()
        if (text is Spanned) {
            for (span in text.getSpans(0, text.length, CharacterStyle::class.java)) {
                span.updateDrawState(paint)
            }
        }
        return paint
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    private fun fieldOrNull(target: Any, name: String): Any? {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target)
    }

    // ---- payload -------------------------------------------------------------

    private fun topicJson(): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        base.put("id", TOPIC_ID)
        base.put("title", "Titles")
        base.put("posts_count", 3)
        base.put("highest_post_number", 3)
        base.put("last_read_post_number", 3)
        val stream = base.getJSONObject("post_stream")
        stream.put("stream", JSONArray(listOf(HOLOGRAPHIC_ID, PLAIN_ID, LONG_ID)))
        stream.put(
            "posts",
            JSONArray(
                listOf(
                    postJson(1, HOLOGRAPHIC_ID, "op", "楼主", "种子用户"),
                    postJson(2, PLAIN_ID, "guest", "路人", "骐骥驰骋"),
                    postJson(3, LONG_ID, "long", LONG_NAME, LONG_TITLE),
                ),
            ),
        )
        return base.toString()
    }

    private fun postJson(floor: Int, id: Long, username: String, name: String, title: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("post_number", floor)
            .put("username", username)
            .put("name", name)
            .put("user_title", title)
            .put("reply_count", 0)
            .put("created_at", "2024-01-01T10:00:00.000Z")
            .put("cooked", "<p>第 $floor 楼，作者的标题在名字旁边。</p>")
            .put("avatar_template", "/user_avatar/linux.do/$username/{size}.png")

    private companion object {
        const val TOPIC_ID = 7800L
        const val HOLOGRAPHIC_ID = 7801L
        const val PLAIN_ID = 7802L
        const val LONG_ID = 7803L

        /** Long enough that the tag and the name both have to be cut to fit the name line. */
        const val LONG_TITLE = "这是一个非常非常长的头衔会超出名字行能放下的宽度"
        const val LONG_NAME = "A very long display name that keeps going well past the name line"
    }
}
