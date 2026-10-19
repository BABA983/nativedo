package io.github.baba983.nativedo.topic

import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.View
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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout
import org.telegram.ui.Components.RecyclerListView

/**
 * Attaching a file to a reply, end to end: the composer's own attach button, the site's upload
 * endpoint, and the markdown the field receives.
 *
 * <p>The failure this pins: a reply that references a file the site never accepted. Discourse only
 * serves the URL it answered with, and it embeds a relative URL in a post differently from an
 * absolute one, so the markdown has to be built from the answer and the base URL together.</p>
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
internal class UploadReplyTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun anImageIsUploadedAndTheFieldGetsTheSitesOwnImageMarkdown() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openComposerAndAttach()
        // Upstream offers the attach button only while the field is empty, so this is the flow the
        // reader takes: pick a file, then write the sentence around its markup.
        pickFile("screenshot.png", transparentPng())

        // The request lands on the site before its answer is read, so what is waited for is the
        // field the answer fills.
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }

        val body = site.requestBodyFor("uploads.json")
        println("upload: body starts with " + body?.take(120))
        assertNotNull("the upload carried a body", body)
        assertTrue("sent as the composer's own upload", body!!.contains("name=\"upload_type\"") && body.contains("composer"))
        assertTrue(
            "and asked for a synchronous answer, which is the one that carries the URL",
            body.contains("name=\"synchronous\"") && body.contains("true"),
        )
        assertTrue("with the file as its own part", body.contains("name=\"file\""))
        assertEquals(
            "and with the token the site asks writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("uploads.json"),
        )
        val part = filePart(site.requestBytesFor("uploads.json")!!)
        assertEquals(
            "the part is named after the file the reader picked, not the cache copy that carries it",
            "screenshot.png",
            part.first,
        )
        assertArrayEquals(
            "and its bytes are the picked file's, not the copy's own naming or anything re-encoded",
            transparentPng(),
            part.second,
        )

        val field = composer.fieldText.toString()
        println("upload: field = $field")
        assertEquals(
            "the field holds the markdown the site's own composer writes: its short handle and the dimensions",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            field,
        )
        assertEquals(
            "and the send button is there as soon as the upload lands",
            View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.renderApp("topic-reply-upload")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anythingThatIsNotAnImageBecomesTheSitesAttachmentMarkdown() {
        site.serveJson("uploads.json", harness.fixture("upload-response-attachment.json"))
        val composer = openComposerAndAttach()
        pickFile("session-notes.zip", "PK\u0003\u0004zip".toByteArray())

        harness.await("the field to hold the uploaded file") {
            !composer.getFieldText().isNullOrEmpty()
        }

        val field = composer.fieldText.toString()
        println("upload: attachment field = $field")
        assertEquals(
            "a file the site will not render inline keeps its name, its marker and its size",
            "[session-notes.zip|attachment](upload://1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d.zip) (1.2 MB)\n",
            field,
        )
        harness.renderApp("topic-reply-upload-attachment")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Two files the reader picks can report the same name; only the copies we make of them differ,
     * by the prefix that keeps them apart in the cache. The site records the name it is sent, so
     * that prefix must not travel: each part keeps the picker's name and its own bytes.
     */
    @Test
    fun twoPicksWithTheSameNameAreUploadedUnderThatNameWithTheirOwnBytes() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", "first screenshot".toByteArray(), source = "first")
        harness.await("the first upload to land in the field") {
            !composer.getFieldText().isNullOrEmpty()
        }

        // The cache copy is stamped with the clock, so the second pick has to land on a later tick
        // to be a copy of its own rather than an overwrite of the first.
        harness.idleClock(1_000)
        harness.layoutApp()
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        strip.performClick()
        harness.idle()
        openSystemFilePicker()
        harness.idle()
        pickFile("screenshot.png", "second screenshot".toByteArray(), source = "second")
        harness.await("the second upload to land in the field") {
            composer.getFieldText()?.count { it == '\n' } == 2
        }

        val parts = site.requestBodiesFor("uploads.json").map { body -> filePart(body.toByteArray()) }
        assertEquals("both picks reached the site as their own upload", 2, parts.size)
        assertEquals(
            "each part is named after its pick, with no cache prefix in front of it",
            listOf("screenshot.png", "screenshot.png"),
            parts.map { part -> part.first },
        )
        assertEquals(
            "and each carries the bytes of the pick it was uploaded from, not its same-named neighbour's",
            listOf("first screenshot", "second screenshot"),
            parts.map { part -> part.second.toString(Charsets.UTF_8) },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Boots the topic, presses the composer's own attach button and hands back the composer. */
    private fun openComposerAndAttach(): ChatActivityEnterView {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Upload"))
        harness.await("the topic to load") { rowCount() == 1 }
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.getAttachButton().performClick()
        harness.idle()
        // The paperclip opens the attachment sheet; the file action is what reaches the picker.
        openSystemFilePicker()
        harness.idle()
        return composer
    }

    /** The files tab now stays inline; its storage row is the explicit handoff to Android SAF. */
    private fun openSystemFilePicker() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowEnvironment.addExternalDir(Environment.getExternalStorageDirectory().absolutePath)
        shadowOf(harness.context.getSystemService(AppOpsManager::class.java)).setMode(
            "android:manage_external_storage", harness.context.applicationInfo.uid,
            harness.context.packageName, AppOpsManager.MODE_ERRORED,
        )
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as? DiscourseAttachSheet
            ?: throw AssertionError("the paperclip opened no sheet")
        dialog.selectAction(DiscourseAttachSheet.Action.FILES)
        harness.idleClock(500)
        val pane = viewsUnder(dialog.window!!.decorView).filterIsInstance<ChatAttachAlertDocumentLayout>().first()
        val list = ChatAttachAlertDocumentLayout::class.java.getDeclaredField("listView")
            .apply { isAccessible = true }.get(pane) as RecyclerListView
        val adapter = list.adapter!!
        val getItem = adapter.javaClass.getMethod("getItem", Integer.TYPE).apply { isAccessible = true }
        val storagePosition = (0 until adapter.itemCount).first { position ->
            val item = getItem.invoke(adapter, position) ?: return@first false
            val icon = item.javaClass.getField("icon").apply { isAccessible = true }.getInt(item)
            icon == org.telegram.messenger.R.drawable.files_storage ||
                icon == org.telegram.messenger.R.drawable.files_internal
        }
        list.clickItem(null, storagePosition)
        harness.idleClock(500)
    }

    private fun viewsUnder(root: android.view.View): List<android.view.View> {
        val found = mutableListOf<android.view.View>()
        fun walk(view: android.view.View) {
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

    @Test
    fun aSecondFileAppendsToTheFirstThroughTheStripsOwnButton() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the first upload to land in the field") {
            !composer.getFieldText().isNullOrEmpty()
        }

        // A laid-out composer has already decided what to offer: with text in the field upstream's
        // own paperclip is gone, and ours is the only way to a second file.
        harness.layoutApp()
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        assertEquals("shown while the field has something in it", View.VISIBLE, strip.visibility)
        // Upstream's own paperclip is not what the reader sees once the field has text (compare
        // topic-reply-empty.png with the render below), so a second file goes through the strip.
        println(
            "upload: upstream paperclip while the field has text — alpha=" + composer.getAttachButton()?.alpha +
                " drawnAt=" + composer.getAttachButton()?.left,
        )

        strip.performClick()
        harness.idle()
        openSystemFilePicker()
        harness.idle()
        pickFile("second.png", transparentPng())
        harness.await("the second upload to land in the field") {
            composer.getFieldText()?.count { it == '\n' } == 2
        }

        val field = composer.fieldText.toString()
        println("upload: two files = $field")
        assertEquals(
            "each upload keeps its own line and its own handle",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n" +
                "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            field,
        )
        harness.renderApp("topic-reply-upload-second")
        println(
            "upload: strip sits on the composer — margin=" +
                (strip.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin +
                " composerHeight=" + composer.height,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anAnswerWithoutAShortHandleIsStillLinkedToTheUpload() {
        site.serveJson(
            "uploads.json",
            harness.fixture("upload-response.json")
                .replace("\"short_url\": \"upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png\",", "")
                // A site-relative answer is what this test is about: it has to be resolved.
                .replace("\"url\": \"https://linux.do/", "\"url\": \"/"),
        )
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        val field = composer.fieldText.toString()
        println("upload: no short handle = $field")
        assertEquals(
            "without a handle the field points at the answer's own URL, resolved against the site",
            "![screenshot.png|1080x720](${site.url("/uploads/default/original/2X/9/9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png")})\n",
            field,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anImageTheSiteCouldNotMeasureKeepsItsHandleWithoutDimensions() {
        site.serveJson(
            "uploads.json",
            harness.fixture("upload-response.json")
                .replace("\"width\": 1080,", "\"width\": null,")
                .replace("\"height\": 720,", "\"height\": null,"),
        )
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        println("upload: unmeasured image = " + composer.fieldText)
        assertEquals(
            "an image whose size the site did not read is embedded without one",
            "![screenshot.png](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            composer.fieldText.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Answers the picker the attach button opened, the way the system would. [source] only makes
     * two picks of the same filename different documents; the name the app reads is [name] either
     * way, because it is the last segment.
     */
    private fun pickFile(name: String, bytes: ByteArray, source: String = "picked"): Uri {
        val activity = harness.activity
        val requested = shadowOf(activity).nextStartedActivityForResult
            ?: throw AssertionError("the attach button opened no picker")
        assertEquals(
            "the picker takes any file the provider can open",
            Intent.ACTION_OPEN_DOCUMENT,
            requested.intent.action,
        )
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$source/$name")
        shadowOf(activity.contentResolver).registerInputStream(uri, bytes.inputStream())
        shadowOf(activity).receiveResult(requested.intent, Activity.RESULT_OK, Intent().setData(uri))
        harness.idle()
        return uri
    }

    /**
     * The file part of a multipart body, read from the bytes as they were sent: the name in its
     * `Content-Disposition` and its payload. ISO-8859-1 makes every character one byte, so the
     * header scan indexes the same bytes it slices.
     */
    private fun filePart(bytes: ByteArray): Pair<String, ByteArray> {
        val wire = bytes.toString(Charsets.ISO_8859_1)
        val header = "Content-Disposition: form-data; name=\"file\"; filename=\""
        val headerAt = wire.indexOf(header)
        assertTrue("the body carries a file part", headerAt >= 0)
        val nameAt = headerAt + header.length
        val name = wire.substring(nameAt, wire.indexOf('"', nameAt))
        val payloadAt = wire.indexOf("\r\n\r\n", headerAt) + 4
        val payloadEnd = wire.indexOf("\r\n--", payloadAt)
        assertTrue("the file part has a payload of its own", payloadAt >= 4 && payloadEnd > payloadAt)
        return name to bytes.copyOfRange(payloadAt, payloadEnd)
    }

    /** Our own attach strip, by the label it shows while it is idle. */
    private fun attachStrip(): View? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findAttachStrip(content)
    }

    private fun findAttachStrip(view: View): View? {
        if (view is android.widget.TextView && view.text?.startsWith("添加") == true) return view.parent as? View
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findAttachStrip(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** A 1x1 PNG: what the uploader sends is the file's bytes, and any real image would do. */
    private fun transparentPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findComposer(content)
    }

    private fun findComposer(view: View): ChatActivityEnterView? {
        if (view is ChatActivityEnterView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 809L
    }
}
