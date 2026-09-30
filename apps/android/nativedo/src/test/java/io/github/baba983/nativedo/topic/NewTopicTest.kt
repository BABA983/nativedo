package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.FragmentFloatingButton
import java.net.URLDecoder

/**
 * Starting a topic from the topic list.
 *
 * <p>The failure this pins: a client that can read a forum but not write to it is a viewer. The
 * list's own floating button - upstream's, the one its chat list starts a chat with - opens the
 * site's way of starting a topic (a title, a category, a body), and what reaches the site is
 * {@code POST /posts.json} carrying the title, the category's id and the body, with the session's
 * CSRF token. A topic missing any of the three is never posted.</p>
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
internal class NewTopicTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theListsOwnButtonStartsATopicAndTheSiteGetsItsTitleCategoryAndBody() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the site's categories to arrive") { site.requestPaths.contains("categories.json") }
        harness.layoutApp()
        harness.renderApp("topic-list-start-topic")

        startTopic()
        harness.await("the composer of a new topic") { composer() != null }
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        println("new topic: composer = " + composer.javaClass.name)
        harness.layoutApp()
        harness.renderApp("new-topic-empty")
        assertEquals(
            "an empty form has nothing to send",
            View.GONE,
            composer.getSendButton()?.visibility,
        )

        titleField()?.setText(TITLE)
        composer.setFieldText(BODY)
        harness.idle()
        assertEquals(
            "and a topic without a category is nothing to send either",
            View.GONE,
            composer.getSendButton()?.visibility,
        )

        categoryRow()?.performClick()
        harness.idle()
        val picker = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("the category row offers the site's own categories", picker)
        println("new topic: the picker offers " + picker!!.itemsCount + " categories")
        assertEquals("every top-level category of the site, and nothing else", 4, picker.itemsCount)
        val offered = pickerRows(picker)
        assertEquals(
            "the site's own categories, in the site's own order",
            listOf("● 运营反馈", "● 开发调优", "● 搞七捻三", "● 国内吹水"),
            offered.map { (_, label) -> label },
        )
        offered.first { (_, label) -> label.contains(CATEGORY) }.first.performClick()
        harness.idle()

        assertEquals(
            "with all three the topic can be sent",
            View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.layoutApp()
        harness.renderApp("new-topic")

        composer.sendMessage()
        harness.idleClock(400)
        println("new topic: requests = " + site.recordedRequests)
        harness.await("the topic to reach the site") {
            site.recordedRequests.contains("POST /posts.json")
        }

        val body = site.requestBodyFor("posts.json")
        println("new topic: body = $body csrf = " + site.csrfHeaderFor("posts.json"))
        assertNotNull("the topic carried a body", body)
        val fields = body!!.split('&').associate { field -> field.substringBefore('=') to field.substringAfter('=') }
        assertEquals("the title travels as the site's own field", TITLE, URLDecoder.decode(fields["title"], "UTF-8"))
        assertEquals("the category travels as its id", "4", fields["category"])
        assertEquals("and the body as raw", BODY, URLDecoder.decode(fields["raw"], "UTF-8"))
        assertNull("the site is making the topic, so there is no topic_id", fields["topic_id"])
        assertEquals(
            "and with the token the site asks its writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("posts.json"),
        )

        harness.await("the composer to close itself") { composer() == null }
        harness.await("the list to be read again, with the new topic on it") {
            site.queriesFor("latest.json").size >= 2
        }
        harness.assertNoTelegramSideEffects()
    }

    /** The list's own floating button, which is upstream's. */
    private fun startTopic() {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw AssertionError("the shell has no content view")
        val button = viewsUnder(content).filterIsInstance<FragmentFloatingButton>().firstOrNull()
            ?: throw AssertionError("the topic list carries no floating button")
        println("new topic: the button is " + button.contentDescription)
        harness.layoutApp()
        button.performClick()
        harness.idle()
    }

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<ChatActivityEnterView>().firstOrNull()
    }

    /** The title, in upstream's own field for one, and the row the category is chosen on. */
    private fun titleField(): EditTextBoldCursor? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<EditTextBoldCursor>().firstOrNull()
    }

    private fun categoryRow(): View? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<org.telegram.ui.Cells.TextSettingsCell>().firstOrNull()
    }

    /** The dialog's own rows, each with the text the dialog draws on it. */
    private fun pickerRows(picker: AlertDialog): List<Pair<View, String>> {
        val window = picker.window ?: throw AssertionError("the picker has no window")
        return viewsUnder(window.decorView)
            .filterIsInstance<AlertDialog.AlertDialogCell>()
            .map { cell ->
                val label = viewsUnder(cell).filterIsInstance<android.widget.TextView>()
                    .firstOrNull()?.text?.toString().orEmpty()
                cell as View to label
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

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TITLE = "构建脚本大家都是怎么组织的"
        const val BODY = "第一次在这里发帖，先问一下大家怎么组织自己的构建脚本。"
        const val CATEGORY = "开发调优"
    }
}
