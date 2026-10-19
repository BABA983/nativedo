package io.github.baba983.nativedo.topic

import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowEnvironment
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MediaController
import org.telegram.messenger.VideoEditedInfo
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatAttachAlert
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.Paint.Views.LPhotoPaintView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.PhotoViewer
import org.telegram.ui.iv.RichEditorToolbar
import java.io.File
import java.io.FileOutputStream

/**
 * The attachment sheet, end to end, on the screens that open it.
 *
 * <p>Every case drives the shipped flow: the composer's own paperclip (or, once the field has text,
 * the screen's own attach strip) opens the sheet, its own tab list picks a pane, its own gallery
 * action, poll form and editor toolbar do the rest, and the site's own upload endpoint receives the
 * file. Nothing here constructs a [DiscourseAttachSheet] directly or calls one of its callbacks by
 * hand - the "sheet" a case sees is the one the fragment itself showed.</p>
 *
 * <p>The failures this pins, kept from the case's first draft: a paperclip that drops the reader
 * into a bare picker instead of the Telegram-shaped sheet; a poll written as a Telegram poll message
 * instead of the site's own `[poll]` raw; a gallery/files selection that never reaches this client's
 * upload path (and so never lands in the field); an upload that loses the reader's draft when the
 * site refuses it; and a sheet whose tabs hand off and leave an empty panel behind. The assertions
 * end at the seams the screens consume - the field's text, the picker the screen asked for, the
 * request the site received - so they pin the host wiring rather than a stand-in for it.</p>
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
internal class AttachSheetTest {

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
        // The gallery pane's selection is a process-wide map; a case must not inherit the last one's.
        clearGallerySelection()
    }

    @After
    fun tearDown() {
        // The viewer is its own window, not this case's dialog; a case that failed before its
        // finally must not leave it over the next sheet.
        runCatching {
            if (PhotoViewer.hasInstance()) PhotoViewer.getInstance().destroyPhotoViewer()
        }
        // The sheet is this case's own window: close it before the activity it hangs off goes away,
        // so the next case starts from no dialog and no fragment whose dialog is still attached.
        runCatching {
            (ShadowDialog.getLatestDialog() as? android.app.Dialog)?.takeIf { it.isShowing }?.dismiss()
        }
        runCatching { harness.visibleFragment()?.onPause() }
        clearGallerySelection()
        site.stop()
    }

    /**
     * The paperclip opens the site's own sheet, not a bare file picker, and the sheet offers the
     * four actions the site can carry. This is the case that says the reader can still reach a poll
     * and the editor at all. The default pane (the site's own gallery) is captured for inspection.
     */
    @Test
    fun thePaperclipOpensUpstreamsOwnSheetNotABarePicker() {
        val composer = openTopicWithComposer()
        composer.getAttachButton().performClick()
        harness.idle()

        assertNull(
            "the paperclip must not drop the reader straight into a picker",
            shadowOf(harness.activity).nextStartedActivityForResult,
        )
        val sheet = latestSheet()
        assertEquals("the sheet offers the site's own four actions", 4, tabCount(sheet))

        // The sheet opens on the gallery pane: the state a reader sees first, before any pick. Run
        // the open animation to its end so the capture is the settled sheet, not a frame of it.
        harness.idleClock(SETTLE_MS)
        captureSheet(sheet, "attach-sheet-default")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The gallery pane's Done, through the sheet's own action button: the photos the reader selected
     * become local URIs for the screen's own upload path and land in the reply field as the site's
     * markdown - never a Telegram media object.
     */
    @Test
    fun theGalleryDoneHandsTheSelectionToTheUploadPath() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.GALLERY)
        harness.idle()
        captureSheet(sheet, "attach-sheet-gallery")

        // An edited/cropped photo must upload its edited bytes, not silently fall back to the
        // original. Only the editor's output exists, so choosing the original cannot pass this flow.
        selectGalleryPhoto(File(harness.activity.cacheDir, "unavailable-original.png").absolutePath)
        (ChatAttachAlertPhotoLayout.selectedPhotos[GALLERY_PHOTO_ID] as MediaController.PhotoEntry)
            .imagePath = writeCachePng("capture.png").absolutePath
        sheetWriteButton(sheet).performClick()
        harness.idle()

        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        assertEquals(
            "the gallery's Done uploads the site's own markdown into the field",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            composer.getFieldText()?.toString(),
        )
        assertTrue(
            "the sheet closes once its selection was handed to the upload path",
            sheet.isDismissed(),
        )
        assertFalse(
            "a handed-off selection is not something to ask the reader to discard",
            ShadowDialog.getLatestDialog() is AlertDialog,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A video the reader edited in the sheet's own editor cannot be attached. The editor's work -
     * the trim, the crop - lives in the entry's `VideoEditedInfo`/`cropState`, and the upload path
     * can only send the file the entry points at, so uploading it would silently replace what the
     * reader edited with the original.
     *
     * <p>The refusal comes before the first hand-off, not per file: the photo ahead of the videos in
     * the batch must not land in the field while the edited video is still being judged. The reader
     * is told why, the sheet, the selection and the draft stay as they were, and unselecting the
     * videos is all it takes to attach the rest.</p>
     */
    @Test
    fun anEditedVideoIsRefusedBeforeAnyUploadAndTheSheetSaysWhy() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.setFieldText("这段草稿不该被动")
        harness.idle()

        val sheet = openSheetFromStrip()
        sheet.selectAction(DiscourseAttachSheet.Action.GALLERY)
        harness.idle()
        // The reader's batch: an ordinary photo first, then two videos the editor has touched -
        // one trimmed, one cropped - which is exactly what the upload path would flatten.
        selectGalleryPhoto(writeCachePng("before.png").absolutePath)
        selectGalleryVideo(GALLERY_TRIMMED_VIDEO_ID, writeCacheVideo("trimmed.mp4").absolutePath) { entry ->
            entry.editedInfo = VideoEditedInfo().apply {
                start = 0.25f
                end = 0.7f
                startTime = 1_500
                endTime = 4_200
                originalDuration = 6_000
                estimatedDuration = 6_000
                originalWidth = 1280
                originalHeight = 720
                resultWidth = 1280
                resultHeight = 720
            }
        }
        selectGalleryVideo(GALLERY_CROPPED_VIDEO_ID, writeCacheVideo("cropped.mp4").absolutePath) { entry ->
            entry.isCropped = true
            entry.cropState = MediaController.CropState().apply {
                cropPw = 0.5f
                cropPh = 0.5f
            }
        }
        harness.idle()
        sheetWriteButton(sheet).performClick()
        harness.idle()

        val refusal = ShadowDialog.getLatestDialog() as? AlertDialog
            ?: throw AssertionError("Done on an edited video uploaded it instead of explaining itself")
        assertTrue(
            "the reader is told, in the sheet's own words, why the video cannot be attached",
            shownText().any { it.contains("已编辑的视频") },
        )
        assertEquals(
            "not even the photo ahead of the videos in the batch is sent",
            0,
            site.requestPaths.count { it == "uploads.json" },
        )
        assertEquals(
            "and the draft is exactly as the reader left it",
            "这段草稿不该被动",
            composer.getFieldText()?.toString(),
        )
        assertTrue("the sheet stays, with its selection", sheet.isShowing)
        assertFalse("an edited video does not discard the sheet", sheet.isDismissed())
        captureDialog(refusal, "attach-edited-video-refusal")

        // Unselecting the videos and pressing Done again attaches the rest: the refusal is a
        // statement about the videos, not a dead end for the batch.
        refusal.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        harness.idle()
        clearGalleryVideos()
        sheetWriteButton(sheet).performClick()
        harness.await("the photo left in the selection to upload") {
            site.requestPaths.count { it == "uploads.json" } == 1
        }
        harness.await("the field to hold its markup") {
            composer.getFieldText()?.toString().orEmpty().contains("upload://")
        }
        assertTrue("the sheet closes once the batch is clean", sheet.isDismissed())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An unedited video is an ordinary attachment: the reader never opened it in the editor, so
     * there is nothing to lose by sending it, and its own bytes are what the site receives - not a
     * re-encode and not the photo the entry also names.
     */
    @Test
    fun theGalleryDoneUploadsAnUneditedVideoAsItsOwnFile() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        val clip = writeCacheVideo("clip.mp4")
        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.GALLERY)
        harness.idle()
        selectGalleryVideo(GALLERY_VIDEO_ID, clip.absolutePath)
        harness.idle()
        sheetWriteButton(sheet).performClick()
        harness.idle()

        harness.await("the field to hold the uploaded video") {
            !composer.getFieldText().isNullOrEmpty()
        }
        val part = filePart(site.requestBytesFor("uploads.json")!!)
        assertEquals("the part is the video's own file", "clip.mp4", part.first)
        assertArrayEquals(
            "and its bytes are the file the reader picked, untouched",
            clip.readBytes(),
            part.second,
        )
        assertTrue(
            "the field carries the site's own handle for it",
            composer.getFieldText()?.toString().orEmpty().contains("upload://"),
        )
        assertTrue("the sheet closes once the video was handed off", sheet.isDismissed())
        harness.assertNoTelegramSideEffects()
    }

    /** A selection must not continue uploading, or insert its late answer, for a different reader. */
    @Test
    fun anAccountChangeDuringAGalleryBatchStopsTheRemainingUploads() {
        val composer = openTopicWithComposer()
        val container = NativeDo.container(harness.context)
        site.serveResponse("uploads.json") {
            container.sessionStore.save(
                io.github.baba983.nativedo.core.session.SessionSnapshot(
                    "other-reader", "Other", "", System.currentTimeMillis(),
                ),
            )
            okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("upload-response.json"))
        }
        val sheet = openSheetFromPaperclip()
        selectGalleryPhoto(writeCachePng("first.png").absolutePath)
        ChatAttachAlertPhotoLayout.selectedPhotos[9] = MediaController.PhotoEntry(
            0, 9, 0L, writeCachePng("second.png").absolutePath, 0, false, 0, 0, 0L,
        )
        ChatAttachAlertPhotoLayout.selectedPhotosOrder.add(9)
        sheetWriteButton(sheet).performClick()

        harness.await("the stopped batch to report its failure") {
            shownText().any { it.contains("回复失败") }
        }
        assertEquals("the next selected file is never sent for the new reader", 1,
            site.requestPaths.count { it == "uploads.json" })
        assertTrue("the old upload cannot enter the new reader's field", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The gallery pane's own top bar is the reader's way out of an expanded gallery. It carries a
     * back action, the bottom tab capsule gives way to it while it is up (the pane hides the
     * capsule), and backing out closes the whole sheet to the composer - the draft the reader had
     * written is untouched, and the pane's untouched selection is not uploaded.
     *
     * <p>The gallery is the sheet's default pane, so the captures are the two heights the reader
     * sees: the half-height opening (its own drag handle at the top, the bottom tab capsule still
     * showing) and the pane raised under its own ActionBar (the tab capsule gone).</p>
     */
    @Test
    fun theGallerysTopBarBackClosesTheSheetToTheComposerWithoutTouchingTheDraft() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.setFieldText("这段草稿不该被相册动")
        harness.idle()

        val sheet = openSheetFromStrip()
        captureSheet(sheet, "attachment-navigation/gallery-half-height")
        assertEquals(
            "the bottom tab capsule shows while the gallery sits at half height",
            View.VISIBLE,
            tabCapsule(sheet).visibility,
        )

        // The pane's own scroll-to-top is the expand the reader sees; the alert raises its bar.
        sheet.selectAction(DiscourseAttachSheet.Action.GALLERY)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        captureSheet(sheet, "attachment-navigation/gallery-expanded")
        assertEquals(
            "the gallery pane uses the alert's own top bar",
            View.VISIBLE,
            sheet.actionBar.visibility,
        )
        assertEquals(
            "the bottom tab capsule gives way to the gallery's top bar",
            View.INVISIBLE,
            tabCapsule(sheet).visibility,
        )

        val back = sheet.actionBar.getBackButton()
            ?: throw AssertionError("the alert's top bar has no back action")
        back.performClick()
        harness.idle()

        assertTrue(
            "the gallery's back closes the sheet all the way to the composer",
            sheet.isDismissed(),
        )
        assertTrue("the screen underneath is still the topic", harness.visibleFragment() is TopicFragment)
        assertEquals(
            "backing out of the gallery leaves the draft alone",
            "这段草稿不该被相册动",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The files tab opens the site's own document browser, and its system-file entry is Android's
     * SAF picker - exactly the picker the screen already answers for an image. The pick is uploaded
     * and its markdown lands in the field.
     *
     * <p>The pane offers that entry only off the device's own storage state: its root list carries a
     * storage row while external storage is mounted, and its click handler hands the reader to SAF
     * only while the app has no all-files access. Robolectric defaults to the opposite of both (its
     * app-ops shadow answers `MODE_ALLOWED`, and the storage state starts `MEDIA_REMOVED`), so the
     * case sets the device's own state the way a real one is - that is the environment, not the
     * assertion.</p>
     */
    @Test
    fun theFilesTabOpensTheDocumentPaneAndItsSystemEntryHandsOffToTheSafPicker() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        mountExternalStorageWithoutAllFilesAccess()
        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.FILES)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        captureSheet(sheet, "attach-sheet-files")

        // The pane's own system-file row, the way a reader reaches Android's picker.
        val list = documentList(documentPane(sheet))
        list.clickItem(null, storageRowPosition(list))
        harness.idle()

        val requested = shadowOf(harness.activity).nextStartedActivityForResult
            ?: throw AssertionError("the system-file entry opened no picker")
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, requested.intent.action)
        assertEquals("the file action takes any file the provider can open", "*/*", requested.intent.type)
        answerPicker(requested.intent, "screenshot.png", transparentPng())

        harness.await("the field to hold the uploaded file") {
            !composer.getFieldText().isNullOrEmpty()
        }
        assertEquals(
            "the picked file reaches the field as the site's own markdown",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The files pane walks up one directory before the sheet closes. A reader who opened a folder
     * expects the sheet's back action to return to the parent listing, not to drop the whole sheet;
     * only at the root does back close it to the composer. The pane's own title is the observable
     * that says which directory the reader is in.
     *
     * <p>The reader's real list drives it: the pane's root carries the app's external "Telegram"
     * folder - the one real directory upstream always offers there - so the click and the two back
     * presses are the shipped flow. The captures are the reader's two file-pane heights: the root at
     * half height, and the same root raised under the pane's own top bar.</p>
     */
    @Test
    fun theFilesBackGoesUpOneDirectoryBeforeClosingTheSheetAtTheRoot() {
        val composer = openTopicWithComposer()
        mountExternalStorageWithAnAppFolder()
        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.FILES)
        harness.idle()
        harness.idleClock(SETTLE_MS)

        val list = documentList(documentPane(sheet))
        captureSheet(sheet, "attachment-navigation/files-half-height")
        assertEquals(
            "the bottom tab capsule shows while the files pane sits at half height",
            View.VISIBLE,
            tabCapsule(sheet).visibility,
        )

        // The reader's own scroll raises the file pane's top bar; the tab capsule gives way to it.
        list.scrollBy(0, harness.height)
        harness.idle()
        layoutSheet(sheet)
        harness.idleClock(SETTLE_MS)
        captureSheet(sheet, "attachment-navigation/files-expanded")
        assertEquals(
            "the files pane uses the alert's own top bar once raised",
            View.VISIBLE,
            sheet.actionBar.visibility,
        )
        assertEquals(
            "the bottom tab capsule gives way to the files pane's top bar",
            View.INVISIBLE,
            tabCapsule(sheet).visibility,
        )

        val rootTitle = sheet.actionBar.title?.toString()
        list.clickItem(null, documentRowByTitle(list, "Telegram"))
        harness.idle()
        assertEquals(
            "entering the folder makes the pane's own title the folder",
            "Telegram",
            sheet.actionBar.title?.toString(),
        )
        assertTrue("entering a folder keeps the sheet open", sheet.isShowing)

        sheet.actionBar.getBackButton().performClick()
        harness.idle()
        assertTrue("back inside a folder keeps the sheet open", sheet.isShowing)
        assertEquals(
            "back inside a folder returns to the root listing",
            rootTitle,
            sheet.actionBar.title?.toString(),
        )

        sheet.actionBar.getBackButton().performClick()
        harness.idle()
        assertTrue("back at the root closes the sheet to the composer", sheet.isDismissed())
        assertTrue("the screen underneath is still the topic", harness.visibleFragment() is TopicFragment)
        assertTrue("closing the files pane writes nothing to the field", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The article tab is seeded from what the composer already holds, and its own Apply writes the
     * site's markdown back into the field - the [DiscourseRichText] bridge, not a Telegram article.
     */
    @Test
    fun theArticleTabSeedsFromTheComposerAndApplyWritesBackIntoTheField() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.setFieldText("a **bold** reply")
        harness.idle()

        val sheet = openSheetFromStrip()
        sheet.selectAction(DiscourseAttachSheet.Action.RICH_EDITOR)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        captureSheet(sheet, "attach-sheet-article")

        val toolbar = viewsUnder(sheet.window!!.decorView).filterIsInstance<RichEditorToolbar>().firstOrNull()
            ?: throw AssertionError("the article tab opened no editor toolbar")
        // Formula blocks have no Discourse round-trip: exposing the action would lose authored math.
        val formulaLabel = org.telegram.messenger.LocaleController.getString(
            org.telegram.messenger.R.string.AccDescrIVFormula,
        )
        assertTrue(
            "the article toolbar must not offer a formula the site cannot preserve",
            viewsUnder(toolbar).none { it.isShown && it.contentDescription == formulaLabel },
        )
        // The pane's own Apply (its toolbar's send action), not a callback called from the test.
        toolbar.getSendButton().performClick()
        harness.idle()

        assertTrue(
            "Apply hands the article's markdown back to the composer's field",
            composer.getFieldText()?.toString().orEmpty().contains("**bold**"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The poll tab keeps the site's own poll raw: the form is filled in place and its Create
     * pressed, and the reply field receives the site's `[poll]` markdown - never a Telegram poll
     * message, and never a second popup.
     */
    @Test
    fun thePollTabKeepsItsFormInlineAndWritesTheSitesOwnRawIntoTheField() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet()
        captureSheet(sheet, "attach-sheet-poll")

        assertTrue(
            "the poll form stays inside the sheet, not a second window",
            ShadowDialog.getLatestDialog() === sheet,
        )
        val pane = pollPane(sheet)
        val fields = viewsUnder(pane).filterIsInstance<EditTextBoldCursor>()
        assertEquals("a question field and at least two option fields", 3, fields.size)
        fields[0].setText("周末怎么过")
        fields[1].setText("宅家")
        fields[2].setText("出门")
        createButton(pane).performClick()
        harness.idle()

        assertEquals(
            "the site's own poll raw lands in the reply field - never a Telegram poll message",
            "$POLL_RAW\n",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** The pane's multiple-answers setting is the site's own poll type, not a Telegram field. */
    @Test
    fun thePollSettingsChangeTheRawTheFormWrites() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet()
        val pane = pollPane(sheet)
        val fields = viewsUnder(pane).filterIsInstance<EditTextBoldCursor>()
        fields[0].setText("多选")
        fields[1].setText("甲")
        fields[2].setText("乙")
        val multiple = viewsUnder(pane).filterIsInstance<org.telegram.ui.Cells.TextCheckCell>().firstOrNull()
            ?: throw AssertionError("the poll form has no multiple-answers row")
        multiple.performClick()
        createButton(pane).performClick()
        harness.idle()

        assertEquals(
            "the multiple-answers setting is the site's own poll type",
            "[poll type=multiple results=always chartType=bar]\n# 多选\n* 甲\n* 乙\n[/poll]\n",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A poll without its question or its two options is refused in place, and writes nothing. */
    @Test
    fun thePollFormNamesTheMissingQuestionAndTheMissingOptionsSeparately() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet()
        val pane = pollPane(sheet)
        val fields = viewsUnder(pane).filterIsInstance<EditTextBoldCursor>()

        // A question but only one option: the option rule fails and nothing is written.
        fields[0].setText("只有标题")
        fields[1].setText("唯一选项")
        createButton(pane).performClick()
        harness.idle()
        assertTrue(
            "the reader is told a poll needs two options",
            texts(pane).any { it.contains("至少需要两个选项") },
        )
        assertTrue("nothing reached the field", composer.getFieldText().isNullOrEmpty())
        assertTrue("and the sheet stays for the reader", sheet.isShowing)

        // Two options but no question: the question rule fails, separately.
        fields[0].setText("")
        fields[2].setText("第二个")
        createButton(pane).performClick()
        harness.idle()
        assertTrue(
            "the reader is told a poll needs a question",
            texts(pane).any { it.contains("需要一个标题") },
        )
        assertTrue("still nothing reached the field", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /** Cancelling the sheet - the reply strip's own exit - leaves the composer exactly as it was. */
    @Test
    fun cancellingTheSheetKeepsTheDraft() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.setFieldText("一段还没写完的回复")
        harness.idle()

        val sheet = openSheetFromStrip()
        sheet.dismiss()
        harness.idle()

        assertEquals(
            "cancelling the sheet leaves the field alone",
            "一段还没写完的回复",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A file the site refuses: the reader is told, and the draft they had written is untouched - the
     * failure the original case pinned.
     */
    @Test
    fun aRefusedUploadKeepsTheDraftAndSaysSo() {
        site.serveResponse("uploads.json") {
            okhttp3.mockwebserver.MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.setFieldText("保留我写的内容")
        harness.idle()

        val sheet = openSheetFromStrip()
        selectGalleryPhoto(writeCachePng("broken.png").absolutePath)
        sheetWriteButton(sheet).performClick()
        harness.idle()

        harness.await("the site to refuse the upload") { site.requestPaths.contains("uploads.json") }
        harness.await("the reader to be told") { shownText().any { it.contains("回复失败") } }
        assertEquals(
            "a refused upload does not touch the draft",
            "保留我写的内容",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A URI the screen is handed directly - the seam a recorder drives - uploads without any picker
     * and lands in the field. The picker is one way in, not the only one.
     */
    @Test
    fun aLocalUriUploadsWithoutAnyPicker() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        val fragment = harness.visibleFragment() as TopicFragment
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/recorded.png")
        shadowOf(harness.activity.contentResolver)
            .registerInputStream(uri, transparentPng().inputStream())

        fragment.attachMediaUri(uri)

        harness.await("the field to hold the uploaded file") {
            !composer.getFieldText().isNullOrEmpty()
        }
        assertNull("no picker was opened", shadowOf(harness.activity).nextStartedActivityForResult)
        assertTrue("the upload went to the site", site.requestPaths.contains("uploads.json"))
        assertTrue(
            "and the field carries the site's own handle",
            composer.getFieldText()?.toString().orEmpty().contains("upload://"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A poll abandoned before Create writes nothing at all - the field is left exactly as it was. */
    @Test
    fun cancellingTheInlinePollWritesNothing() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet()
        val pane = pollPane(sheet)
        val fields = viewsUnder(pane).filterIsInstance<EditTextBoldCursor>()
        fields[0].setText("半途而废")
        fields[1].setText("一")
        fields[2].setText("二")

        sheet.dismiss()
        harness.idle()

        assertTrue("dismissing the sheet writes no poll", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The poll pane rides the alert's own top bar: the bar the pane claims carries its title and a
     * back action, exactly like the gallery's. A pane that answers `needsActionBar() == 0` leaves
     * the reader no way back out of the poll pane, which is the defect this pins.
     */
    @Test
    fun thePollPaneClaimsTheSheetsTopBarWithABackAction() {
        openTopicWithComposer()
        val sheet = openPollPane()

        assertEquals(
            "the poll pane uses the alert's own top bar",
            View.VISIBLE,
            sheet.actionBar.visibility,
        )
        val back = sheet.actionBar.getBackButton()
            ?: throw AssertionError("the alert's top bar has no back action")
        assertEquals("the bar draws its back action", View.VISIBLE, back.visibility)
        assertTrue(
            "the back action is named for the reader",
            !back.contentDescription.isNullOrEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pane opens at half height with its top bar out of the way and its bottom tab capsule
     * showing, and scrolling the pane's own list up brings the bar in while the capsule gives way to
     * it - the half-height/full-height transition the reader saw. A capsule left overlapping the
     * pane's form, or a bar that never arrives, is the defect this pins.
     */
    @Test
    fun thePollPaneKeepsItsTopBarHiddenUntilTheReaderScrollsItUp() {
        openTopicWithComposer()
        val sheet = openPollPane()
        layoutSheet(sheet)

        assertEquals(
            "the top bar stays out of the way while the pane sits at half height",
            0f,
            sheet.actionBar.alpha,
            0.01f,
        )
        assertEquals(
            "the bottom tab capsule shows while the poll pane sits at half height",
            View.VISIBLE,
            tabCapsule(sheet).visibility,
        )
        captureSheet(sheet, "attachment-navigation/poll-half-height")

        // The reader drags the pane's own list up: the opening padding scrolls away and the pane
        // comes up to full height under the bar.
        val list = pollList(pollPane(sheet))
        list.scrollBy(0, harness.height)
        harness.idle()
        layoutSheet(sheet)
        harness.idleClock(SETTLE_MS)

        assertEquals(
            "scrolling the pane's own list up brings the top bar and its back action",
            1f,
            sheet.actionBar.alpha,
            0.01f,
        )
        assertEquals(
            "the bottom tab capsule gives way to the poll pane's top bar",
            View.INVISIBLE,
            tabCapsule(sheet).visibility,
        )
        captureSheet(sheet, "attachment-navigation/poll-expanded")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The top bar's back action closes the whole sheet, back to the composer underneath - not an
     * in-place return to the gallery pane. Nothing is written while the poll stays unbuilt.
     */
    @Test
    fun leavingThePollPaneByBackClosesTheWholeSheet() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        val sheet = openPollPane()

        pollList(pollPane(sheet)).scrollBy(0, harness.height)
        layoutSheet(sheet)
        harness.idleClock(SETTLE_MS)
        sheet.actionBar.getBackButton().performClick()
        harness.idle()

        assertTrue(
            "back closes the sheet all the way to the composer, not just the pane",
            sheet.isDismissed(),
        )
        assertTrue(
            "the screen underneath is still the topic",
            harness.visibleFragment() is TopicFragment,
        )
        assertTrue("nothing was written to the composer's field", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A poll that was started but never created is not dropped silently: back asks first, and only
     * the confirmation's own discard action closes the sheet. The composer keeps its field either
     * way.
     */
    @Test
    fun anAbandonedPollDraftAsksBeforeClosingTheSheet() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        val sheet = openPollPane()
        viewsUnder(pollPane(sheet)).filterIsInstance<EditTextBoldCursor>()[0].setText("还没写完的投票")
        harness.idle()

        pollList(pollPane(sheet)).scrollBy(0, harness.height)
        layoutSheet(sheet)
        harness.idleClock(SETTLE_MS)
        sheet.actionBar.getBackButton().performClick()
        harness.idle()

        val confirm = ShadowDialog.getLatestDialog()
        assertTrue(
            "back with a typed poll asks before discarding it",
            confirm is AlertDialog,
        )
        assertTrue("the sheet waits for the reader's answer", !sheet.isDismissed())
        captureDialog(confirm as Dialog, "attachment-navigation/poll-discard")

        (confirm as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        harness.idle()
        assertFalse("cancelling the discard keeps the sheet open", sheet.isDismissed())
        assertEquals(
            "cancelling keeps the poll question",
            "还没写完的投票",
            viewsUnder(pollPane(sheet)).filterIsInstance<EditTextBoldCursor>()[0].text.toString(),
        )
        sheet.actionBar.getBackButton().performClick()
        harness.idle()
        (ShadowDialog.getLatestDialog() as AlertDialog)
            .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        harness.idle()

        assertTrue("confirming discards the sheet", sheet.isDismissed())
        assertTrue(
            "the composer's field never received the abandoned poll",
            composer.getFieldText().isNullOrEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pane's own list is backed by the alert's recycler, so switching tabs must not reset the
     * form or close the sheet: what the reader typed in the poll stays put when they look at the
     * gallery and return, and the sheet is still the one the reader opened.
     */
    @Test
    fun thePollFormKeepsItsDraftAcrossTabs() {
        openTopicWithComposer()
        val sheet = openPollPane()
        viewsUnder(pollPane(sheet)).filterIsInstance<EditTextBoldCursor>()[0].setText("保留的问题")
        harness.idle()

        sheet.selectAction(DiscourseAttachSheet.Action.GALLERY)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        assertTrue("leaving the poll tab does not close the sheet", sheet.isShowing)
        sheet.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        assertTrue("returning to the poll tab keeps the sheet open", sheet.isShowing)
        layoutSheet(sheet)

        assertTrue(
            "the poll form keeps what the reader typed when they visit another tab",
            viewsUnder(pollPane(sheet)).filterIsInstance<EditTextBoldCursor>()
                .any { it.text?.toString() == "保留的问题" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The new-topic screen's paperclip opens the same sheet a reply does, over the same backends:
     * its poll tab writes the site's own raw into the body field. The second host the shared sheet
     * feeds.
     */
    @Test
    fun theNewTopicPaperclipOpensTheSameSheet() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(NewTopicFragment.create())
        harness.await("the new-topic composer") { composer() != null }
        val composer = composer() ?: throw AssertionError("the new-topic screen has no composer")

        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        layoutSheet(sheet)
        val pane = pollPane(sheet)
        val fields = viewsUnder(pane).filterIsInstance<EditTextBoldCursor>()
        fields[0].setText("新帖投票")
        fields[1].setText("甲")
        fields[2].setText("乙")
        createButton(pane).performClick()
        harness.idle()

        assertEquals(
            "the same sheet's poll writes the site's raw into the new topic's body",
            "[poll type=regular results=always chartType=bar]\n# 新帖投票\n* 甲\n* 乙\n[/poll]\n",
            composer.getFieldText()?.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A hosted still offers the local paint and filter tools; every other kind of gallery media,
     * every other selection type and every alert that is not this sheet keeps upstream's hidden
     * state. This is the boundary the viewer's two visibility passes call, so a regression that
     * widens it is caught before it can reach a Telegram-backed path.
     */
    @Test
    fun theHostedSheetsStillOffersPaintAndFilterWhileEveryOtherMediaStaysHidden() {
        openTopic()
        val sheet = openSheetFromPaperclip()
        val still = MediaController.PhotoEntry(
            0, GALLERY_PHOTO_ID, 0L, writeCachePng("hosted-still.png").absolutePath,
            0, false, 1080, 720, 0L,
        )
        val paint = View(harness.activity)
        val tune = View(harness.activity)

        fun upstreamHidden() {
            paint.visibility = View.GONE
            tune.visibility = View.GONE
            paint.tag = null
            tune.tag = null
        }

        fun assertHidden(why: String, type: Int, media: Any, documents: Boolean = false) {
            upstreamHidden()
            assertFalse(
                "$why must not be offered to the hosted editor",
                NativeDoHooks.hostedStillEditAvailable(sheet, type, media, documents),
            )
            NativeDoHooks.applyHostedStillEditVisibility(sheet, type, media, documents, paint, tune)
            assertEquals("$why keep upstream's hidden paint", View.GONE, paint.visibility)
            assertEquals("$why keep upstream's hidden filter", View.GONE, tune.visibility)
            assertNull("$why get no upstream tag", paint.tag)
        }

        upstreamHidden()
        NativeDoHooks.applyHostedStillEditVisibility(sheet, 4, still, false, paint, tune)
        assertEquals("the hosted sheet's own still can be painted", View.VISIBLE, paint.visibility)
        assertEquals("and filtered", View.VISIBLE, tune.visibility)
        assertEquals("the editor reads upstream's own shown tag", 1, paint.tag)
        assertEquals(1, tune.tag)

        assertHidden(
            "videos",
            4,
            MediaController.PhotoEntry(
                0, 72, 0L, writeCacheVideo("hosted-clip.mp4").absolutePath,
                6, true, 1280, 720, 2048L,
            ),
        )
        assertHidden("GIF animations", 4, MediaController.PhotoEntry(
            0, 73, 0L, writeCacheGif("hosted-animation.gif").absolutePath, 0, false, 320, 180, 0L,
        ))
        assertHidden("GIF search results", 4, MediaController.SearchImage())
        assertHidden("a document-picker still", 4, still, documents = true)
        assertHidden("the sheet's other selection types", 5, still)

        // A gallery that is not this sheet is a real Telegram alert: not an instance of the host,
        // so even the gallery-only type never reaches the local editor.
        upstreamHidden()
        NativeDoHooks.applyHostedStillEditVisibility(null, 4, still, false, paint, tune)
        assertEquals("a real Telegram gallery keeps paint hidden", View.GONE, paint.visibility)
        assertEquals("and filter hidden", View.GONE, tune.visibility)

        assertTrue(
            "every editor the hosted sheet opens gets the safe toolbar",
            NativeDoHooks.hostedAttachSheetEditor(sheet),
        )
        assertFalse(
            "a real Telegram alert's editor keeps upstream's full toolbar",
            NativeDoHooks.hostedAttachSheetEditor(null),
        )
    }

    /**
     * The viewer's first open and every later switch ask the host boundary. A still picked in the
     * hosted gallery shows both tools on open and on switching back, and an animated GIF keeps
     * them hidden while it is the current item.
     */
    @Test
    fun theHostedStillViewerOffersTheToolsOnFirstOpenAndOnEveryStillSwitch() {
        openTopic()
        val sheet = openSheetFromPaperclip()
        val still = selectedStill(writeCachePng("viewer-still.png").absolutePath)
        val gif = MediaController.PhotoEntry(
            0, 74, 0L, writeCacheGif("viewer-animation.gif").absolutePath, 0, false, 320, 180, 0L,
        )
        val viewer = openHostedStillViewer(sheet, arrayListOf<Any>(still, gif))
        try {
            val paint = editorControl(viewer, "paintItem")
            val tune = editorControl(viewer, "tuneItem")
            assertEquals("the first still can be painted", View.VISIBLE, paint.visibility)
            assertEquals("and filtered", View.VISIBLE, tune.visibility)
            captureViewer("attachment-navigation/hosted-still-viewer")

            switchImageIndex(viewer, 1)
            assertEquals("an animated GIF keeps upstream's hidden paint", View.GONE, paint.visibility)
            assertEquals("and filter", View.GONE, tune.visibility)
            captureViewer("attachment-navigation/hosted-gif-viewer")

            switchImageIndex(viewer, 0)
            assertEquals("switching back to the still offers paint again", View.VISIBLE, paint.visibility)
            assertEquals("and filter", View.VISIBLE, tune.visibility)
        } finally {
            closeViewer()
        }
    }

    /**
     * The hosted still editor reaches no Telegram sticker/GIF search or account-backed photo
     * picker: its sticker tab is hidden and the tab's action refuses even if it is invoked, so no
     * EmojiBottomSheet and no fake-ChatActivity photo picker is built. Cancelling the editor
     * leaves the entry as it was, and the sheet's Done then uploads exactly the reader's original.
     */
    @Test
    fun theHostedStillPaintToolbarHasNoTelegramStickerOrPhotoEntryPoint() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        openTopic()
        val sheet = openSheetFromPaperclip()
        val original = writeCachePng("paint-still.png")
        val still = selectedStill(original.absolutePath)
        val viewer = openHostedStillViewer(sheet, arrayListOf<Any>(still))
        try {
            editorControl(viewer, "paintItem").performClick()
            harness.await("the hosted paint editor") { field(viewer, "photoPaintView") != null }
            harness.idleClock(SETTLE_MS)
            val editor = field(viewer, "photoPaintView") as LPhotoPaintView

            val stickerTab = field(editor, "stickerTab") as View
            assertEquals("the gallery's Telegram sticker/GIF tab is hidden", View.GONE, stickerTab.visibility)
            assertFalse("and is not an enabled action", stickerTab.isEnabled)
            captureViewer("attachment-navigation/hosted-still-paint")

            val dialog = ShadowDialog.getLatestDialog()
            stickerTab.performClick()
            harness.idleClock(SETTLE_MS)
            assertSame(
                "the hidden tab builds no EmojiBottomSheet and no Telegram photo picker",
                dialog,
                ShadowDialog.getLatestDialog(),
            )
            assertNull("and no emoji/sticker view was created", field(editor, "emojiView"))

            editor.getCancelView().performClick()
            harness.idleClock(SETTLE_MS)
            assertNull("cancelling paint leaves the original file to upload", still.imagePath)
            assertFalse("and records no paint", still.isPainted)
        } finally {
            closeViewer()
        }

        // The cancelled entry is exactly the file the reader picked: the sheet's own Done uploads
        // it, byte for byte, and nothing the editor never wrote replaces it.
        sheetWriteButton(sheet).performClick()
        harness.await("the cancelled still to upload") { site.requestPaths.contains("uploads.json") }
        val part = filePart(site.requestBytesFor("uploads.json")!!)
        assertArrayEquals("the upload is the reader's original file", original.readBytes(), part.second)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ the sheet, opened

    private fun openTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Attach"))
        harness.await("the topic to load") { rowCount() == 1 }
    }

    private fun openTopicWithComposer(): ChatActivityEnterView {
        openTopic()
        return composer() ?: throw AssertionError("the screen has no composer")
    }

    /** The sheet as the empty-field composer opens it: the paperclip. */
    private fun openSheetFromPaperclip(): DiscourseAttachSheet {
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.getAttachButton().performClick()
        harness.idle()
        harness.idleClock(SETTLE_MS)
        return latestSheet()
    }

    /**
     * The sheet once the field has text. Upstream's own paperclip is gone then, and this screen's
     * attach strip is the only entry; it opens the same sheet.
     */
    private fun openSheetFromStrip(): DiscourseAttachSheet {
        harness.layoutApp()
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        strip.performClick()
        harness.idle()
        harness.idleClock(SETTLE_MS)
        return latestSheet()
    }

    /**
     * The sheet, and its poll pane selected and laid out - the state the poll cases start from.
     * The pane's form lives in the alert's own recycler, so it has a form to read only once the
     * sheet's own window has been through a layout pass.
     */
    private fun openShippedSheet(): DiscourseAttachSheet = openPollPane()

    private fun openPollPane(): DiscourseAttachSheet {
        val sheet = openSheetFromPaperclip()
        sheet.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        layoutSheet(sheet)
        return sheet
    }

    private fun latestSheet(): DiscourseAttachSheet {
        val dialog = ShadowDialog.getLatestDialog()
        return dialog as? DiscourseAttachSheet
            ?: throw AssertionError("the paperclip opened no sheet (latest dialog: ${dialog?.javaClass?.name})")
    }

    // ------------------------------------------------------------------ the sheet's panes

    /** The tab list's own item count, read off the alert's own adapter. */
    private fun tabCount(sheet: DiscourseAttachSheet): Int {
        val field = ChatAttachAlert::class.java.getDeclaredField("buttonsRecyclerView")
            .apply { isAccessible = true }
        val list = field.get(sheet) as RecyclerListView
        return list.adapter?.itemCount ?: 0
    }

    /**
     * The sheet's own bottom tab capsule (the Gallery/File/Article/Poll strip). The alert hides it
     * when a pane's own top bar comes up and the pane answers `shouldHideBottomButtons()`, so its
     * visibility is the observable for the capsule's show/hide.
     */
    private fun tabCapsule(sheet: DiscourseAttachSheet): View {
        val field = ChatAttachAlert::class.java.getDeclaredField("buttonsRecyclerViewWrapper")
            .apply { isAccessible = true }
        return field.get(sheet) as View
    }

    private fun pollPane(sheet: DiscourseAttachSheet): DiscoursePollLayout =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<DiscoursePollLayout>().firstOrNull()
            ?: throw AssertionError("the poll tab opened no poll pane")

    /** The poll pane's own list - the recycler the reader scrolls. */
    private fun pollList(pane: View): RecyclerListView =
        viewsUnder(pane).filterIsInstance<RecyclerListView>().firstOrNull()
            ?: throw AssertionError("the poll pane has no list")

    private fun documentPane(sheet: DiscourseAttachSheet): ChatAttachAlertDocumentLayout =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<ChatAttachAlertDocumentLayout>().firstOrNull()
            ?: throw AssertionError("the files tab opened no document pane")

    /** The document pane's own file list - the recycler the reader scrolls. */
    private fun documentList(pane: ChatAttachAlertDocumentLayout): RecyclerListView {
        val field = ChatAttachAlertDocumentLayout::class.java.getDeclaredField("listView")
            .apply { isAccessible = true }
        return field.get(pane) as RecyclerListView
    }

    /** The poll form's own Create action, by the label it draws. */
    private fun createButton(pane: View): View =
        viewsUnder(pane).filterIsInstance<TextView>().firstOrNull { it.text?.toString() == "创建" }
            ?: throw AssertionError("the poll form has no Create action")

    /** The sheet's own action (done/send) button - the gallery pane's Done. */
    private fun sheetWriteButton(sheet: DiscourseAttachSheet): View =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<ChatActivityEnterView.SendButton>()
            .firstOrNull()
            ?: throw AssertionError("the sheet has no action button")

    /**
     * The document pane's system-file row, found by the icon its own click handler matches - the
     * row that hands the reader to Android's SAF picker. The pane's adapter is upstream's own.
     */
    private fun storageRowPosition(list: RecyclerListView): Int {
        val adapter = list.adapter ?: throw AssertionError("the file pane has no adapter")
        for (position in 0 until adapter.itemCount) {
            val item = adapterItem(adapter, position) ?: continue
            val icon = intField(item, "icon")
            if (icon == org.telegram.messenger.R.drawable.files_storage ||
                icon == org.telegram.messenger.R.drawable.files_internal
            ) {
                return position
            }
        }
        throw AssertionError("the file pane offers no system-file entry")
    }

    /**
     * The document pane's row for [title], found by the label the pane's own adapter holds. The
     * adapter is upstream's own private list, so the lookup goes through the row object it returns.
     */
    private fun documentRowByTitle(list: RecyclerListView, title: String): Int {
        val adapter = list.adapter ?: throw AssertionError("the file pane has no adapter")
        for (position in 0 until adapter.itemCount) {
            val item = adapterItem(adapter, position) ?: continue
            if (stringField(item, "title") == title) return position
        }
        throw AssertionError("the file pane offers no $title row")
    }

    private fun adapterItem(adapter: Any, position: Int): Any? {
        val method = adapter.javaClass.getMethod("getItem", Integer.TYPE)
        method.isAccessible = true
        return method.invoke(adapter, position)
    }

    private fun intField(target: Any, name: String): Int {
        val field = target.javaClass.getField(name).apply { isAccessible = true }
        return field.getInt(target)
    }

    private fun stringField(target: Any, name: String): String? {
        val field = target.javaClass.getField(name).apply { isAccessible = true }
        return field.get(target) as? String
    }

    /**
     * The device state the document pane's system-file entry needs: external storage mounted (so the
     * pane's root list carries its storage row at all), and no all-files access (so the app-op the
     * row is gated on answers the way it does for an app without `MANAGE_EXTERNAL_STORAGE`).
     */
    private fun mountExternalStorageWithoutAllFilesAccess() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowEnvironment.addExternalDir(Environment.getExternalStorageDirectory().absolutePath)
        val appOps = harness.context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            "android:manage_external_storage",
            harness.context.applicationInfo.uid,
            harness.context.packageName,
            AppOpsManager.MODE_ERRORED,
        )
    }

    /**
     * Mounts external storage and creates the app's own external "Telegram" folder - the one real
     * directory upstream's file pane always offers at its root. The folder is what lets a case walk
     * into a directory and back out, without depending on the storage row's app-op, which only
     * chooses between navigating and the SAF hand-off.
     */
    private fun mountExternalStorageWithAnAppFolder() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowEnvironment.addExternalDir(Environment.getExternalStorageDirectory().absolutePath)
        val base = harness.context.getExternalFilesDir(null)
            ?: throw AssertionError("external storage gave the app no files directory")
        File(base, "Telegram").mkdirs()
    }

    // ------------------------------------------------------------------ the reader's interaction

    /**
     * The gallery pane's selection, as the pane itself keeps it: one decoded photo, keyed by its
     * image id and ordered. This is the same map the pane's grid writes when a reader checks a cell.
     */
    private fun selectGalleryPhoto(path: String) {
        val entry = MediaController.PhotoEntry(0, GALLERY_PHOTO_ID, 0L, path, 0, false, 0, 0, 0L)
        clearGallerySelection()
        ChatAttachAlertPhotoLayout.selectedPhotos[GALLERY_PHOTO_ID] = entry
        ChatAttachAlertPhotoLayout.selectedPhotosOrder.add(GALLERY_PHOTO_ID)
    }

    /** A selected still, as the sheet will upload it: the very entry the viewer edits. */
    private fun selectedStill(path: String): MediaController.PhotoEntry {
        selectGalleryPhoto(path)
        return ChatAttachAlertPhotoLayout.selectedPhotos[GALLERY_PHOTO_ID] as MediaController.PhotoEntry
    }

    /**
     * Opens the hosted gallery's viewer with the sheet's own provider and the gallery-only
     * selection type its photo layout uses - the same call `ChatAttachAlertPhotoLayout` makes.
     * The viewer decodes the local file on upstream's workers, so wait for the bitmap before a
     * case enters an editor that needs its size.
     */
    private fun openHostedStillViewer(
        sheet: DiscourseAttachSheet,
        photos: ArrayList<Any>,
    ): PhotoViewer {
        val viewer = PhotoViewer.getInstance()
        val fragment = sheet.baseFragment ?: harness.topFragment()
            ?: throw AssertionError("the sheet has no host fragment")
        viewer.setParentActivity(fragment)
        viewer.setParentAlert(sheet)
        viewer.setMaxSelectedPhotos(-1, true)
        assertTrue(
            "the hosted gallery opens its still in upstream's viewer",
            viewer.openPhotoForSelect(photos, 0, 4, false, sheet.photoLayout.photoViewerProvider, null),
        )
        harness.await("the hosted still to decode") { viewer.centerImage.bitmap != null }
        harness.idleClock(SETTLE_MS)
        assertViewerShowsTheStill(viewer)
        return viewer
    }

    /**
     * The viewer's own decoded image is the reader's file: the pixels at its centre are the accent
     * colour [stillPng] draws. A viewer showing a placeholder, a broken decode or nothing at all
     * fails here, so the cases below are about a real still being edited, not a visibility flag.
     */
    private fun assertViewerShowsTheStill(viewer: PhotoViewer) {
        val decoded = viewer.centerImage.bitmap
            ?: throw AssertionError("the hosted still never decoded")
        val pixel = decoded.getPixel(decoded.width / 2, decoded.height / 2)
        val near = { actual: Int, expected: Int -> kotlin.math.abs(actual - expected) < 24 }
        assertTrue(
            "the viewer draws the picked still's own pixels (got #${Integer.toHexString(pixel)})",
            near(android.graphics.Color.red(pixel), 0x3d) &&
                near(android.graphics.Color.green(pixel), 0x7f) &&
                near(android.graphics.Color.blue(pixel), 0xbf),
        )
    }

    private fun closeViewer() {
        runCatching { PhotoViewer.getInstance().closePhoto(true, false) }
        harness.idleClock(SETTLE_MS)
    }

    /** One of the viewer's own edit controls; they are private, read by reflection in tests only. */
    private fun editorControl(viewer: PhotoViewer, name: String): View = field(viewer, name) as View

    /** Switches the viewer the way a page swipe does, through its own index setter. */
    private fun switchImageIndex(viewer: PhotoViewer, index: Int) {
        val method = PhotoViewer::class.java.getDeclaredMethod(
            "setImageIndex",
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(viewer, index, false, false)
        harness.idleClock(SETTLE_MS)
    }

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            type = type.superclass
        }
        throw NoSuchFieldException(name)
    }

    /**
     * One video in the same selection. [edit] is what the alert's own editor records when the
     * reader trims, crops or annotates it - the entry, never the file the entry points at, which is
     * why the upload path cannot carry such a video.
     */
    private fun selectGalleryVideo(
        imageId: Int,
        path: String,
        edit: (MediaController.PhotoEntry) -> Unit = {},
    ) {
        val entry = MediaController.PhotoEntry(0, imageId, 0L, path, 6, true, 1280, 720, 4096L)
        edit(entry)
        ChatAttachAlertPhotoLayout.selectedPhotos[imageId] = entry
        ChatAttachAlertPhotoLayout.selectedPhotosOrder.add(imageId)
    }

    private fun clearGalleryVideos() {
        for (imageId in listOf(GALLERY_VIDEO_ID, GALLERY_TRIMMED_VIDEO_ID, GALLERY_CROPPED_VIDEO_ID)) {
            ChatAttachAlertPhotoLayout.selectedPhotos.remove(imageId)
            ChatAttachAlertPhotoLayout.selectedPhotosOrder.remove(imageId)
        }
    }

    private fun clearGallerySelection() {
        ChatAttachAlertPhotoLayout.selectedPhotos.clear()
        ChatAttachAlertPhotoLayout.selectedPhotosOrder.clear()
    }

    /** Answers the picker the screen opened, the way the system would. */
    private fun answerPicker(intent: Intent, name: String, bytes: ByteArray): Uri {
        val activity = harness.activity
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$name")
        shadowOf(activity.contentResolver).registerInputStream(uri, bytes.inputStream())
        shadowOf(activity).receiveResult(intent, Activity.RESULT_OK, Intent().setData(uri))
        harness.idle()
        return uri
    }

    /** A still the reader picked, as a real image the gallery's viewer can decode. */
    private fun writeCachePng(name: String): File {
        val file = File(File(harness.context.cacheDir, "attach-sheet"), name)
        file.parentFile?.mkdirs()
        file.writeBytes(stillPng(320, 240))
        return file
    }

    /**
     * An animated GIF picked from the gallery: a real two-frame GIF, the bytes a picker hands over.
     * The viewer draws the first frame (upstream treats a local photo entry as a still), and only
     * the entry's own extension marks it as an animation, which is what the boundary keeps out of
     * the editor.
     */
    private fun writeCacheGif(name: String): File {
        val file = File(File(harness.context.cacheDir, "attach-sheet"), name)
        file.parentFile?.mkdirs()
        file.writeBytes(animatedGif())
        return file
    }

    /** A video the reader picked: its bytes stand in for a real clip, and must survive untouched. */
    private fun writeCacheVideo(name: String): File {
        val file = File(File(harness.context.cacheDir, "attach-sheet"), name)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(2048) { index -> (index * 31 + 7).toByte() })
        return file
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

    /**
     * A still image of [width]x[height], encoded by the platform's own PNG encoder. The gallery's
     * viewer and paint editor decode this file on upstream's image workers, so the bytes have to be
     * a decodable image - a hand-copied base64 blob that decodes to a bad IDAT chunk leaves
     * `centerImage` empty forever and no editor can open.
     */
    private fun stillPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(0x3d, 0x7f, 0xbf))
            val bytes = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            return bytes.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /** A 32x18 two-frame GIF: a real animated image, not a still with a `.gif` name. */
    private fun animatedGif(): ByteArray = android.util.Base64.decode(
        "R0lGODlhIAASAPAAAD1/vwAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAAAAAACwAAAAAIAASAAACFoSPqcvtD6OctNqLs968+w+G4kiWUwEAIfkEAAAAAAAsAAAAACAAEgCAv389AAAAAhaEj6nL7Q+jnLTai7PevPsPhuJIllMBADs=",
        android.util.Base64.DEFAULT,
    )

    /**
     * A 1x1 transparent PNG, the file a picker answers with where the case only needs *a* file: the
     * upload path sends the reader's bytes untouched, and this is still a real, decodable PNG.
     */
    private fun transparentPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNgAAIAAAUAAen63NgAAAAASUVORK5CYII=",
        android.util.Base64.DEFAULT,
    )

    // ------------------------------------------------------------------ capture

    /**
     * Draws the sheet's real window and writes it under `.amp/in/artifacts/nativedo/`.
     *
     * <p>[UpstreamUiHarness.renderApp] draws `android.R.id.content`, which is the activity's own
     * hierarchy; a dialog (the sheet is one) lives in its own window and is not under it, so a
     * renderApp capture would miss the sheet entirely. Drawing the sheet's own decor is the same
     * real window the window manager composites, chrome and all - nothing is drawn by hand.</p>
     */
    private fun captureSheet(sheet: DiscourseAttachSheet, name: String) {
        layoutSheet(sheet)
        captureDecor(sheet.window!!.decorView, name)
    }

    /** Draws a dialog's own window the same way, for the states that live in a second window. */
    private fun captureDialog(dialog: Dialog, name: String) {
        val decor = dialog.window!!.decorView
        repeat(2) {
            // A dialog's window is not full screen: measure it to its own natural size, or the
            // card inside it is stretched away from its buttons.
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.AT_MOST),
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
            harness.idle(120)
        }
        captureDecor(decor, name, decor.measuredWidth, decor.measuredHeight)
    }

    private fun captureDecor(decor: View, name: String, width: Int = harness.width, height: Int = harness.height) {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            decor.draw(Canvas(bitmap))
            val out = File(artifactsDir(), "$name.png")
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("attach sheet capture: $out (${out.length()} bytes)")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Draws the viewer's own window - upstream's `windowView`, the full-screen view the window
     * manager composites the photo and its tools into - over the screen it was opened from. The
     * capture is therefore the reader's actual state (the decoded still, or the editor, under the
     * tools the boundary shows or hides), not a visibility flag the case set.
     */
    private fun captureViewer(name: String) {
        val window = field(PhotoViewer.getInstance(), "windowView") as View
        window.measure(
            View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY),
        )
        window.layout(0, 0, harness.width, harness.height)
        harness.idle()
        harness.idleClock(120)

        val bitmap = Bitmap.createBitmap(harness.width, harness.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            harness.activity.findViewById<ViewGroup>(android.R.id.content)?.draw(Canvas(bitmap))
            window.draw(Canvas(bitmap))
            val out = File(artifactsDir(), "$name.png")
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("hosted still capture: $out (${out.length()} bytes)")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Lays the sheet's own window out the way the window manager does before a capture or a scroll.
     * Two passes: the pane's list measures its rows, binds its form, and only then reports real
     * geometry, so the second pass is the settled one.
     */
    private fun layoutSheet(sheet: DiscourseAttachSheet) {
        val decor = sheet.window!!.decorView
        repeat(2) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, harness.width, harness.height)
            harness.idle(120)
        }
    }

    private fun artifactsDir(): File = repoFile(".amp/in/artifacts/nativedo")

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (File(dir, "fixtures/discourse").isDirectory) {
                return File(dir, relative)
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root from ${System.getProperty("user.dir")}")
    }

    // ------------------------------------------------------------------ the screen's views

    /** Our own attach strip, by the label it shows while it is idle. */
    private fun attachStrip(): View? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findAttachStrip(content)
    }

    private fun findAttachStrip(view: View): View? {
        if (view is TextView && view.text?.startsWith("添加") == true) return view.parent as? View
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findAttachStrip(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<ChatActivityEnterView>().firstOrNull()
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    /** Every piece of text the screen shows, dialogs included. */
    private fun shownText(): List<String> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(content, out)
        (ShadowDialog.getLatestDialog() as? android.app.Dialog)?.window?.decorView?.let { dialog ->
            collectText(dialog, out)
        }
        return out
    }

    private fun texts(root: View): List<String> {
        val out = mutableListOf<String>()
        collectText(root, out)
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
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

    private companion object {
        const val TOPIC_ID = 809L

        /** The gallery pane's own photo key, matching the single photo the capture case selects. */
        const val GALLERY_PHOTO_ID = 1

        /** A video the reader picked and never opened in the editor. */
        const val GALLERY_VIDEO_ID = 2

        /** A video the editor trimmed: the edit is the entry's own `VideoEditedInfo`. */
        const val GALLERY_TRIMMED_VIDEO_ID = 3

        /** A video the editor cropped: the edit is the entry's own `cropState`. */
        const val GALLERY_CROPPED_VIDEO_ID = 4

        /** How long the sheet's open and pane transitions are given to settle before a capture. */
        const val SETTLE_MS = 400L

        /** The site's own poll raw for the question and two options the reply-field case fills in. */
        const val POLL_RAW =
            "[poll type=regular results=always chartType=bar]\n# 周末怎么过\n* 宅家\n* 出门\n[/poll]"
    }
}
