package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.collection.LongSparseArray
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.shadows.*
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.iv.RichHtml
import org.telegram.ui.iv.TableModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Failure modes: lost root text/emoji, flattened containers, clipped long bodies, and avatar
 * completion replacing the reader's scrolled cell. All bodies enter through the real topic API. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [ShadowConnectionsManager::class, ShadowNativeByteBuffer::class,
        ShadowUtilities::class, ShadowMediaController::class, ShadowIntro::class,
        ShadowMrzRecognizer::class, ShadowRLottieNative::class, ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class, ShadowSQLiteCursor::class, ShadowSQLitePreparedStatement::class],
)
internal class TopicBodyFidelityTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @After
    fun tearDown() {
        // The night theme is process-wide; leaving it on would colour whatever runs next.
        runCatching { if (Theme.isCurrentThemeDark()) switchTo(dark = false) }
        NativeDo.setContainerFactory { AppContainer(it) }
        site.stop()
    }

    @Test
    fun bareTextAndInlineEmojiSurviveAlongsideParagraphs() {
        open("Bare start <b>bold</b><p>Middle <img class='emoji' alt=':innocent:' " +
            "src='/images/emoji/innocent.png'> end</p>Bare tail")
        val text = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("root text before an element was dropped: $text", text.startsWith("Bare start bold"))
        assertTrue("inline emoji must retain its readable label: $text", text.contains("Middle :innocent: end"))
        assertTrue("root text after an element was dropped: $text", text.endsWith("Bare tail"))
        harness.renderApp("body-inline-text")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Paragraph whitespace is browser whitespace, not the pretty-printer's. A real linux.do body
     * carries `<br>\n` (the site's own cook writes the newline after the break), a newline between
     * inline runs, an intentional boundary space around a bold/anchor run, and leading/trailing
     * padding around a paragraph; a code block carries its own newlines. All of those must reach
     * the stock renderer exactly as the browser lays them out: one space per whitespace run, no
     * blank line after a `<br>`, no line-leading padding, code untouched.
     *
     * <p>The expected geometry is not a copied dp value: it is upstream's own importer reading the
     * equivalent plain body, and the drawn height, per-block origins and joined text are required
     * to match it.</p>
     */
    @Test
    fun whitespaceMatchesTheUpstreamImportWithoutExtraLinesOrParagraphPadding() {
        val tricky = "<p>第一行<br>\n第二行 <b>粗体</b> <a href='/x'>链接</a>\n第三行</p>" +
            "<p>  mixed ENGLISH and 中文   </p>" +
            "<pre><code>docker run \\\n  --rm image\nline3\n</code></pre>"
        val plain = "<p>第一行<br>第二行 <b>粗体</b> <a href='/x'>链接</a> 第三行</p>" +
            "<p>mixed ENGLISH and 中文</p>" +
            "<pre><code>docker run \\\n  --rm image\nline3\n</code></pre>"

        open(tricky)

        // Retained evidence, drawn before the assertions so a failing run still leaves the picture.
        renderTo("rich-body-whitespace-light")
        switchTo(dark = true)
        rebindPosts()
        renderTo("rich-body-whitespace-dark")
        switchTo(dark = false)

        val expected = listOf(
            "第一行\n第二行 粗体 链接 第三行",
            "mixed ENGLISH and 中文",
            "docker run \\\n  --rm image\nline3",
        )
        val host = cell().messageObject
        val hostLayout = host.richLayout
        val hostText = host.messageOwner.rich_message.blocks.map { RichMessageLayout.getString(it.text) }
        val reference = referenceLayout(plain, list().width)
        val referenceText = reference.messageObject.messageOwner.rich_message.blocks
            .map { RichMessageLayout.getString(it.text) }
        println("rich-body: host blocks = " + hostText.map { esc(it) })
        println("rich-body: host height=${hostLayout.height} origins=${hostLayout.blocks.map { it.currY }} " +
            "joined=${esc(hostLayout.joinedText)}")
        println("rich-body: import blocks = " + referenceText.map { esc(it) })
        println("rich-body: import height=${reference.height} origins=${reference.blocks.map { it.currY }} " +
            "joined=${esc(reference.joinedText)}")

        assertEquals("the imported paragraph text must be the browser's", expected, hostText)
        assertEquals("upstream's importer must read the same text", expected, referenceText)
        assertEquals(
            "a <br> must not gain a blank line and a paragraph must not keep its line padding; " +
                "host=${esc(hostLayout.joinedText)}, import=${esc(reference.joinedText)}",
            reference.joinedText.toString(), hostLayout.joinedText.toString(),
        )
        assertEquals(
            "paragraph and code origins must line up with upstream's import",
            reference.blocks.map { it.currY }, hostLayout.blocks.map { it.currY },
        )
        assertEquals(
            "the body's drawn height must match upstream's import",
            reference.height, hostLayout.height,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Whitespace at an inline element's edge is the same run as the text beside it: `<b> bold </b>
     * <a> link </a>` is one space between words, not two, and a `<br>` drops the indent the next
     * inline element would otherwise carry onto the new line (`<br><b>  Second</b>` starts at the
     * margin). Consecutive breaks stay two breaks, an inline `<code>` keeps its spacings, and a
     * non-breaking space stays a non-breaking space.
     *
     * <p>The expected text is the browser's, written out here; the expected geometry is upstream's
     * own importer reading the already-collapsed body, so neither half can pass by echoing this
     * serializer.</p>
     */
    @Test
    fun adjacentInlineRunsAndBoldAfterBreakCollapseLikeABrowser() {
        val tricky = "<p>First <b> bold </b> <a href='/x'> link </a><br><b>  Second</b></p>" +
            "<p>alpha <i>  beta  </i>  gamma</p>" +
            "<p>keep&nbsp;nbsp <b> and </b> tail</p>" +
            "<p>one<br><br>two</p>" +
            "<p>code <code>a  b</code> tail</p>"
        val plain = "<p>First <b>bold</b> <a href='/x'>link</a><br><b>Second</b></p>" +
            "<p>alpha <i>beta</i> gamma</p>" +
            "<p>keep&nbsp;nbsp <b>and</b> tail</p>" +
            "<p>one<br><br>two</p>" +
            "<p>code <code>a  b</code> tail</p>"

        open(tricky)

        // Retained evidence, drawn before the assertions so a failing run still leaves the picture.
        renderTo("rich-body-whitespace-edge-light", "rich-message-layout/whitespace-edge")
        switchTo(dark = true)
        rebindPosts()
        renderTo("rich-body-whitespace-edge-dark", "rich-message-layout/whitespace-edge")
        switchTo(dark = false)

        val expected = listOf(
            "First bold link\nSecond",
            "alpha beta gamma",
            "keep\u00A0nbsp and tail",
            "one\n\ntwo",
            "code a  b tail",
        )
        val host = cell().messageObject
        val hostLayout = host.richLayout
        val hostText = host.messageOwner.rich_message.blocks.map { RichMessageLayout.getString(it.text) }
        val reference = referenceLayout(plain, list().width)
        val referenceText = reference.messageObject.messageOwner.rich_message.blocks
            .map { RichMessageLayout.getString(it.text) }
        println("rich-body-edge: host blocks = " + hostText.map { esc(it) })
        println("rich-body-edge: host height=${hostLayout.height} origins=${hostLayout.blocks.map { it.currY }} " +
            "joined=${esc(hostLayout.joinedText)}")
        println("rich-body-edge: import blocks = " + referenceText.map { esc(it) })
        println("rich-body-edge: import height=${reference.height} origins=${reference.blocks.map { it.currY }} " +
            "joined=${esc(reference.joinedText)}")

        assertEquals("a space at an inline element's edge is the browser's, not the markup's", expected, hostText)
        assertEquals("upstream's importer must read the same text", expected, referenceText)
        assertEquals(
            "adjacent inline runs must not double their spacing: host=${esc(hostLayout.joinedText)}, " +
                "import=${esc(reference.joinedText)}",
            reference.joinedText.toString(), hostLayout.joinedText.toString(),
        )
        assertEquals(
            "the paragraphs and the preserved code must line up with upstream's import",
            reference.blocks.map { it.currY }, hostLayout.blocks.map { it.currY },
        )
        assertEquals("the body's drawn height must match upstream's import", reference.height, hostLayout.height)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun discourseQuoteWrapperKeepsTheAuthorListAndPreparedPhoto() {
        // The site's aside/title wrapper must not flatten a quote into a paragraph, leak its
        // controls, or treat a downloaded photo as an editor upload that is not ready to send.
        open("<aside class='quote' data-username='writer'><div class='title'>" +
            "<img class='avatar' src='/quote-avatar.png'><span class='quote-controls'>Expand quote</span>" +
            "<a href='/t/ref/300/2'>Quoted writer</a></div><blockquote><p>Quoted paragraph</p>" +
            "<ul><li>Quoted list item</li></ul><img width='240' height='120' src='/uploads/quoted-photo.png'>" +
            "</blockquote></aside><p>Unquoted tail</p>", beforeBoot = {
                val image = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
                val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                site.serveImage("uploads/quoted-photo.png", bytes.toByteArray())
            })
        val rich = cell().messageObject.messageOwner.rich_message
        assertEquals(2, rich.blocks.size)
        assertTrue("Discourse's aside must become an upstream quote container", rich.blocks[0] is TL_iv.pageBlockBlockquoteBlocks)
        val quote = rich.blocks[0] as TL_iv.pageBlockBlockquoteBlocks
        assertEquals("Quoted writer", RichMessageLayout.getString(quote.caption))
        assertEquals(3, quote.blocks.size)
        assertTrue(quote.blocks[1] is TL_iv.pageBlockList)
        val photo = quote.blocks[2] as TL_iv.pageBlockPhoto
        assertTrue("prepared photo was discarded as an unready editor upload", photo.photo_id != 0L)
        assertEquals(photo.photo_id, rich.photos.single().id)
        assertEquals("Unquoted tail", RichMessageLayout.getString(rich.blocks[1].text))
        assertFalse(cell().messageObject.richLayout.joinedText.contains("Expand quote"))
        harness.await("quoted photo decoded") {
            harness.drawApp()
            cell().messageObject.richLayout.blocks.filterIsInstance<RichMessageLayout.RichPhotoBlock>()
                .any { it.imageReceiver.bitmap != null }
        }
        harness.renderApp("body-discourse-quote")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A post's own `<div>` groupings are not presentational the way `md-table` is: they wrap blocks.
     * Upstream's importer reads an unclassified `<div>` as a single paragraph, so a heading and a
     * table inside one used to be flattened out of existence. The wrapper must keep its children as
     * their own blocks, in order, with the table still a grid.
     */
    @Test
    fun genericWrapperKeepsHeadingsAndTableGridInOrder() {
        open("<div class='post-content'>" +
            "<h2>Heading Alpha</h2>" +
            "<p>Paragraph Beta</p>" +
            "<table><tbody><tr><td>left-top</td><td>right-top</td></tr>" +
            "<tr><td>left-bottom</td><td>right-bottom</td></tr></tbody></table>" +
            "<div>Paragraph Gamma</div><div>Paragraph Delta</div>" +
            "</div>")
        val rich = cell().messageObject.messageOwner.rich_message
        println("body-wrapper: blocks = " + rich.blocks.map { it.javaClass.simpleName })
        assertEquals("a generic wrapper must preserve each block boundary", 5, rich.blocks.size)
        assertTrue("the heading must survive as a heading", rich.blocks[0] is TL_iv.pageBlockHeading2)
        assertEquals("Heading Alpha", RichMessageLayout.getString(rich.blocks[0].text))
        assertEquals("Paragraph Beta", RichMessageLayout.getString(rich.blocks[1].text))
        val table = rich.blocks[2] as? TL_iv.pageBlockTable
        assertNotNull("a table inside a generic wrapper must stay a grid", table)
        assertEquals("two rows", 2, table!!.rows.size)
        assertEquals("left-top", TableModel.readPlainText(table.rows[0].cells[0]))
        assertEquals("right-top", TableModel.readPlainText(table.rows[0].cells[1]))
        assertEquals("left-bottom", TableModel.readPlainText(table.rows[1].cells[0]))
        assertEquals("right-bottom", TableModel.readPlainText(table.rows[1].cells[1]))
        assertEquals("Paragraph Gamma", RichMessageLayout.getString(rich.blocks[3].text))
        assertEquals("Paragraph Delta", RichMessageLayout.getString(rich.blocks[4].text))
        renderTo("body-generic-wrapper-table")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Media written inside a list item, a quote or a details block belongs to that container. The
     * picture must stay nested in its item (with the text on either side of it), and the item must
     * not be lifted out of the list or the quote.
     */
    @Test
    fun mediaInsideListsQuotesAndDetailsStaysNestedInItsContainer() {
        open("<p>Lead paragraph.</p>" +
            "<ul><li>list-alpha</li><li>list-beta <img width='240' height='120' src='/uploads/list-photo.png'> list-omega</li></ul>" +
            "<blockquote><p>quote-lead</p><ul><li>quoted-alpha <img width='240' height='120' src='/uploads/quote-photo.png'></li>" +
            "<li>quoted-omega</li></ul><p>quote-tail</p></blockquote>" +
            "<details open><summary>Fold title</summary><p>fold-lead</p>" +
            "<img width='240' height='120' src='/uploads/fold-photo.png'></details>" +
            "<p>Tail paragraph.</p>", beforeBoot = {
                site.serveImage("uploads/list-photo.png", solidPng(240, 120, Color.MAGENTA))
                site.serveImage("uploads/quote-photo.png", solidPng(240, 120, Color.CYAN))
                site.serveImage("uploads/fold-photo.png", solidPng(240, 120, Color.YELLOW))
            })
        val rich = cell().messageObject.messageOwner.rich_message
        println("body-nested: top = " + rich.blocks.map { it.javaClass.simpleName })
        assertTrue("no content photo may escape to the top level",
            rich.blocks.none { it is TL_iv.pageBlockPhoto })
        assertEquals("the list, quote and details stay top-level siblings, with lead and tail", 5, rich.blocks.size)

        val list = rich.blocks[1] as TL_iv.pageBlockList
        assertEquals("list-alpha", RichMessageLayout.getString((list.items[0] as TL_iv.TL_pageListItemText).text))
        val listItem = list.items[1] as? TL_iv.TL_pageListItemBlocks
        assertNotNull("a list item with a picture must keep it nested inside the item", listItem)
        assertEquals("the item's text stays on either side of the picture",
            listOf("list-beta", "list-omega"),
            listItem!!.blocks.filterIsInstance<TL_iv.pageBlockParagraph>()
                .map { RichMessageLayout.getString(it.text) })
        val listPhotos = collectPhotos(listItem.blocks)
        assertEquals("the list item owns one picture", 1, listPhotos.size)
        assertTrue("the nested picture is a message photo", rich.photos.any { it.id == listPhotos.single() })

        val quote = rich.blocks[2] as TL_iv.pageBlockBlockquoteBlocks
        assertEquals("the quote owns its list's picture", 1, collectPhotos(quote.blocks).size)
        val quotedItem = quote.blocks.filterIsInstance<TL_iv.pageBlockList>().single().items[0]
        assertTrue("a quoted list item keeps its picture inside the quote",
            quotedItem is TL_iv.TL_pageListItemBlocks)
        assertEquals(1, collectPhotos((quotedItem as TL_iv.TL_pageListItemBlocks).blocks).size)

        val details = rich.blocks[3] as TL_iv.pageBlockDetails
        assertEquals("the details block owns its picture", 1, collectPhotos(details.blocks).size)

        val joined = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("list text must survive in order: $joined",
            joined.indexOf("list-alpha") < joined.indexOf("list-beta") &&
                joined.indexOf("list-beta") < joined.indexOf("list-omega"))
        assertTrue("quote text must survive in order: $joined",
            joined.indexOf("quote-lead") < joined.indexOf("quoted-alpha") &&
                joined.indexOf("quoted-alpha") < joined.indexOf("quote-tail"))
        assertTrue("details text must survive: $joined", joined.contains("fold-lead"))
        harness.await("the nested pictures decoded") {
            harness.drawApp()
            cell().messageObject.richLayout.blocks.filterIsInstance<RichMessageLayout.RichPhotoBlock>()
                .count { it.imageReceiver.bitmap != null } >= 3
        }
        renderTo("body-nested-media")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A table cell holds inline text in upstream's model, so a picture written in one cannot be a
     * media block. It must not vanish or be lifted out of the cell: it degrades to a tappable link
     * to its own source, in place, between the text that surrounded it.
     */
    @Test
    fun tableCellMediaDegradesToASourceLinkInPlace() {
        val photo = "uploads/table-photo.png"
        open("<div class='post-content'><table><tbody><tr>" +
            "<td>before-cell</td>" +
            "<td>cell-alpha <img width='320' height='180' src='/$photo'> cell-omega</td>" +
            "<td>after-cell</td>" +
            "</tr></tbody></table></div>", beforeBoot = {
                site.serveImage(photo, solidPng(320, 180, Color.CYAN))
            })
        val rich = cell().messageObject.messageOwner.rich_message
        assertTrue("a cell picture must not be promoted to a media block",
            rich.blocks.none { it is TL_iv.pageBlockPhoto })
        val table = rich.blocks.filterIsInstance<TL_iv.pageBlockTable>().firstOrNull()
        assertNotNull("the generic wrapper must keep the table a grid", table)
        assertEquals("the cell reads as its surrounding text with the source link in place",
            listOf("before-cell", "cell-alpha ${site.url(photo)} cell-omega", "after-cell"),
            table!!.rows[0].cells.map { TableModel.readPlainText(it) })
        val styled = TableModel.readStyledText(table.rows[0].cells[1]) as android.text.Spannable
        val links = styled.getSpans(0, styled.length, URLSpanReplacement::class.java)
        assertEquals("the cell media degrades to a tappable link to its own source",
            listOf(site.url(photo)), links.map { it.url })
        assertTrue("nothing may be drawn as gallery media",
            cell().messageObject.richLayout.blocks.none { it is RichMessageLayout.RichPhotoBlock })
        renderTo("body-table-cell-source-link")
        // The long source makes this grid wider than the viewport. Drag the production cell,
        // rather than changing private scroll fields, to show the trailing column is reachable.
        val tableBlock = cell().messageObject.richLayout.blocks
            .filterIsInstance<RichMessageLayout.RichTableBlock>().single()
        val start = cell().textX + tableBlock.padding.left + 100f
        val y = cell().textY + tableBlock.currY + tableBlock.height / 2f
        val now = SystemClock.uptimeMillis()
        for ((action, x) in listOf(MotionEvent.ACTION_DOWN to start,
            MotionEvent.ACTION_MOVE to start - 600f, MotionEvent.ACTION_CANCEL to start - 600f)) {
            val event = MotionEvent.obtain(now, now + 100, action, x, y, 0)
            val handled = cell().onTouchEvent(event)
            if (action != MotionEvent.ACTION_CANCEL) {
                assertTrue("the table must handle horizontal drag action $action", handled)
            }
            event.recycle()
        }
        renderTo("body-table-cell-source-link-scrolled")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun nestedListsQuotesAndDetailsKeepTheirChildrenAndAuthors() {
        open("<p>Outside before</p><ol><li>First<ul><li>Nested A</li><li>Nested B</li></ul></li>" +
            "<li>Second</li></ol><blockquote><p>Outer quote</p><blockquote><p>Inner quote</p>" +
            "<cite>Inner author</cite></blockquote><details><summary>Quoted fold</summary>" +
            "<p>Inside fold</p><details open><summary>Inner fold</summary><p>Deep fold</p></details>" +
            "</details><cite>Outer author</cite></blockquote><p>Outside after</p>")
        val blocks = cell().messageObject.messageOwner.rich_message.blocks
        assertEquals("list, quote and trailing paragraph must remain top-level siblings", 4, blocks.size)
        val ordered = blocks[1] as TL_iv.pageBlockOrderedList
        assertEquals(2, ordered.items.size)
        val first = ordered.items[0] as TL_iv.TL_pageListOrderedItemBlocks
        assertEquals("1.", first.num)
        assertEquals("First", RichMessageLayout.getString(first.blocks[0].text))
        val nested = first.blocks[1] as TL_iv.pageBlockList
        assertEquals(listOf("Nested A", "Nested B"), nested.items.map {
            RichMessageLayout.getString((it as TL_iv.TL_pageListItemText).text)
        })
        assertEquals("Second", RichMessageLayout.getString((ordered.items[1] as TL_iv.TL_pageListOrderedItemText).text))
        val quote = blocks[2] as TL_iv.pageBlockBlockquoteBlocks
        assertEquals("Outer author", RichMessageLayout.getString(quote.caption))
        assertEquals(3, quote.blocks.size)
        val innerQuote = quote.blocks[1] as TL_iv.pageBlockBlockquote
        assertEquals("Inner quote", RichMessageLayout.getString(innerQuote.text))
        assertEquals("Inner author", RichMessageLayout.getString(innerQuote.caption))
        val details = quote.blocks[2] as TL_iv.pageBlockDetails
        assertEquals(2, details.blocks.size)
        assertEquals("Inside fold", RichMessageLayout.getString(details.blocks[0].text))
        assertEquals("Deep fold", RichMessageLayout.getString((details.blocks[1] as TL_iv.pageBlockDetails).blocks[0].text))
        assertEquals("Outside after", RichMessageLayout.getString(blocks[3].text))
        val layout = cell().messageObject.richLayout
        assertFalse("closed details content leaked into visible selection text", layout.joinedText.contains("Inside fold"))
        harness.renderApp("body-nested-closed")
        val closedHeight = cell().height
        val anchorCell = cell()
        val anchorTop = anchorCell.top
        val header = layout.blocks.filterIsInstance<RichMessageLayout.RichDetailsBlock>().first()
        val x = cell().textX + header.padding.left + 80f
        val y = cell().textY + header.currY + header.height / 2f
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
            assertTrue("details tap must be handled by the production cell", cell().onTouchEvent(event))
            event.recycle()
        }
        harness.idleClock(600)
        harness.drawApp()
        assertTrue("details tap did not open its model", details.open)
        assertTrue("opening the outer details exposes its own child", layout.joinedText.contains("Inside fold"))
        assertTrue("the already open nested details belongs inside it", layout.joinedText.contains("Deep fold"))
        assertTrue("expanded details must increase the real cell height, not paint beyond the bubble", cell().height > closedHeight)
        assertSame("details expansion replaced the cell", anchorCell, cell())
        assertEquals("details expansion moved its top anchor", anchorTop, cell().top)
        harness.renderApp("body-nested-open")
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
            assertTrue(cell().onTouchEvent(event))
            event.recycle()
        }
        harness.idleClock(600)
        harness.drawApp()
        assertFalse("second tap must close the details", details.open)
        assertEquals("closing restores the bubble height", closedHeight, cell().height)
        assertEquals("closing moved the top anchor", anchorTop, cell().top)
        assertFalse(layout.joinedText.contains("Inside fold"))
        harness.renderApp("body-nested-closed-again")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theFortiethParagraphCanBeScrolledIntoTheViewport() {
        open((1..40).joinToString("") { "<p>Paragraph $it. A long body must retain every paragraph.</p>" } +
            "<p>END OF LONG BODY</p>")
        val list = list()
        val message = cell().messageObject
        assertFalse("full topic bodies must not use upstream's partial-message cap", message.messageOwner.rich_message.part)
        assertTrue(message.richLayout.joinedText.toString().endsWith("END OF LONG BODY"))
        assertTrue("the test must exercise a body taller than the viewport", cell().height > list.height * 2)
        list.scrollBy(0, cell().height)
        harness.drawApp()
        val tail = cell().messageObject.richLayout.textBlocks.last()
        val tailCell = cell()
        assertTrue("last paragraph must be inside the viewport after scrolling, cell bottom=${tailCell.bottom}",
            tailCell.bottom > list.paddingTop && tailCell.bottom <= list.height - list.paddingBottom + 1)
        assertTrue(tail.layout.text.toString().contains("END OF LONG BODY"))
        harness.renderApp("body-long-tail")
        println("body: long tail reachable; cell bottom=${tailCell.bottom}, viewport=${list.height}, rich height=${message.richLayout.height}")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun delayedAvatarUpdatesTheExistingCellWithoutMovingItsAnchor() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val avatarPath = "uploads/body-avatar-${System.nanoTime()}.png"
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        try {
            open("<p>Anchor post with enough text for several visible rows.</p>", count = 24,
                avatar = "/$avatarPath", beforeBoot = {
                    site.serveResponse(avatarPath) {
                        entered.countDown()
                        check(release.await(60, TimeUnit.SECONDS))
                        MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                    }
                })
            harness.await("pending avatar request") { entered.count == 0L }
            val list = list()
            val manager = list.layoutManager as LinearLayoutManager
            manager.scrollToPositionWithOffset(8, -37)
            harness.drawApp()
            val position = manager.findFirstVisibleItemPosition()
            val anchor = manager.findViewByPosition(position) as ChatMessageCell
            val top = anchor.top
            val adapter = list.adapter
            assertTrue("test must be away from the initial anchor", position >= 7)
            assertNull("avatar must still be pending", anchor.avatarImage.bitmap)
            harness.renderApp("body-avatar-pending")
            release.countDown()
            val user = DiscourseAvatars.user("baba", "Baba", "/$avatarPath", site.baseUrl)
            harness.await("delayed avatar decoded in the visible cell") {
                harness.drawApp()
                DiscourseAvatars.isCached(user) && cells().any { it.avatarImage.bitmap != null }
            }
            assertSame("the adapter must not be replaced", adapter, list.adapter)
            assertEquals("avatar completion moved the first visible post", position, manager.findFirstVisibleItemPosition())
            assertSame("avatar completion replaced the cell", anchor, manager.findViewByPosition(position))
            assertEquals("avatar completion moved the post's pixel anchor", top, anchor.top)
            harness.renderApp("body-avatar-arrived")
            println("body: delayed avatar preserved position=$position top=$top and cell identity")
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * The expected layout of the plain body, built by upstream's own importer with the test's own
     * text: the same `RichHtml.parse` the topic path ends in, but reached without the site's
     * serializers, so the comparison cannot pass by copying the implementation's own output.
     */
    private fun referenceLayout(html: String, width: Int): RichMessageLayout {
        val rich = TL_iv.RichMessage().apply {
            blocks = ArrayList(RichHtml.parse(html).map { it.block })
            photos = ArrayList()
            documents = ArrayList()
        }
        val message = TLRPC.TL_message().apply {
            id = 1
            date = 0
            dialog_id = 1
            flags = 257 + 8
            this.message = "whitespace reference"
            media = TLRPC.TL_messageMediaEmpty()
            out = false
            from_id = TLRPC.TL_peerUser().apply { user_id = 1L }
            peer_id = TLRPC.TL_peerUser().apply { user_id = 1L }
            rich_message = rich
        }
        val messageObject = MessageObject(
            0, message, null, HashMap<Long, TLRPC.User>(), HashMap<Long, TLRPC.Chat>(),
            LongSparseArray(), LongSparseArray(),
            true, false, 0, false, false, false,
        )
        return RichMessageLayout(messageObject, width, null)
    }

    /** Draws the topic screen under `rich-message-layout/` as retained evidence of what the cell drew. */
    private fun renderTo(name: String, dir: String = "rich-message-layout") {
        harness.layoutApp()
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bitmap = Bitmap.createBitmap(
            content.measuredWidth.coerceAtLeast(1),
            content.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        content.draw(Canvas(bitmap))
        val out = File(repoRoot(), ".amp/in/artifacts/$dir/$name.png")
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("rich-body: wrote $out (${out.length()} bytes)")
    }

    /** Switches the running screen's theme; a presented fragment is what applies it. */
    private fun switchTo(dark: Boolean) {
        if (Theme.isCurrentThemeDark() == dark) {
            return
        }
        Theme.applyTheme(Theme.getTheme(if (dark) "Dark Blue" else "Blue"), false, dark)
        harness.await("the body theme to switch") {
            harness.idleClock(60)
            Theme.isCurrentThemeDark() == dark
        }
    }

    /** The stock cells cache their colours, so a theme change has to reach them by re-binding. */
    private fun rebindPosts() {
        list().adapter?.notifyDataSetChanged()
        harness.layoutApp()
        harness.idleClock(200)
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "fixtures/discourse").isDirectory }

    private fun esc(s: CharSequence?): String = (s ?: "").toString().replace("\n", "\\n")

    /** The id of every picture a block list carries, descending into the containers that own one. */
    private fun collectPhotos(blocks: List<TL_iv.PageBlock>): List<Long> = blocks.flatMap { block ->
        when (block) {
            is TL_iv.pageBlockPhoto -> listOf(block.photo_id)
            is TL_iv.pageBlockList -> block.items.flatMap { item ->
                if (item is TL_iv.TL_pageListItemBlocks) collectPhotos(item.blocks) else emptyList()
            }
            is TL_iv.pageBlockOrderedList -> block.items.flatMap { item ->
                if (item is TL_iv.TL_pageListOrderedItemBlocks) collectPhotos(item.blocks) else emptyList()
            }
            is TL_iv.pageBlockBlockquoteBlocks -> collectPhotos(block.blocks)
            is TL_iv.pageBlockDetails -> collectPhotos(block.blocks)
            else -> emptyList()
        }
    }

    private fun solidPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun open(html: String, count: Int = 1, avatar: String = "", beforeBoot: () -> Unit = {}) {
        site.start()
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        val stream = topic.getJSONObject("post_stream")
        val template = stream.getJSONArray("posts").getJSONObject(0)
        stream.put("posts", JSONArray().apply {
            for (number in 1..count) put(JSONObject(template.toString()).apply {
                put("id", 4000 + number).put("post_number", number).put("cooked", html)
                put("avatar_template", avatar)
            })
        })
        stream.put("stream", JSONArray((1..count).map { 4000 + it }))
        topic.put("id", TOPIC).put("posts_count", count).put("highest_post_number", count)
        topic.getJSONObject("details").getJSONObject("created_by").put("avatar_template", "")
        topic.put("last_read_post_number", 1)
        site.serveJson("t/$TOPIC.json", topic.toString())
        beforeBoot()
        NativeDo.setContainerFactory { site.authenticatedContainer(it) }
        harness.bootApp()
        harness.await("home rows") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        harness.present(TopicFragment.forTopic(TOPIC, "Body fidelity"))
        harness.await("topic rows") {
            harness.layoutApp()
            list().adapter?.itemCount == count && cells().isNotEmpty()
        }
        harness.drawApp()
    }

    private fun list(): RecyclerListView = contentListOf(harness.activity)!!
    private fun cells(): List<ChatMessageCell> = (0 until list().childCount)
        .map { list().getChildAt(it) }.filterIsInstance<ChatMessageCell>()
    private fun cell(): ChatMessageCell = cells().first()

    private companion object { const val TOPIC = 8409L }
}
