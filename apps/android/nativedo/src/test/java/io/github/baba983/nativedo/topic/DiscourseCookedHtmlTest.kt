package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.harness.UpstreamUiHarness
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.iv.RichHtml

/**
 * Pins why a Discourse table has to be unwrapped before upstream's importer sees it.
 *
 * Upstream's `RichHtml` does understand `<table>` — it builds a `pageBlockTable` — but only when
 * the table is a direct child of the node it is walking. Any other `<div>` is treated as a
 * paragraph, and Discourse wraps every table in `<div class="md-table">`, so the table arrives one
 * level down and is flattened into one run of text per row. Both halves are asserted here so the
 * normalization cannot be removed without a failure that says what breaks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
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
internal class DiscourseCookedHtmlTest {

    private val harness = UpstreamUiHarness()

    @Before
    fun setUp() {
        harness.bootViews()
    }

    @Test
    fun upstreamImporterReadsABareTableAsAGrid() {
        val rows = RichHtml.parse("<table><tbody><tr><td>a</td><td>b</td></tr></tbody></table>")

        assertEquals(1, rows.size)
        val table = rows[0].block as? TL_iv.pageBlockTable
        assertTrue("a bare <table> becomes a table block", table != null)
        assertEquals("with its row", 1, table!!.rows.size)
        assertEquals("and its cells", 2, table.rows[0].cells.size)
    }

    @Test
    fun discourseTableWrapperWouldFlattenTheGrid() {
        val cooked = harness.fixture("cooked-table-lightbox-blockquote.html")
        assertTrue("the fixture is wrapped the way Discourse wraps it", cooked.contains("md-table"))

        val blocks = RichHtml.parse(cooked).map { it.block }
        println("importer blocks without unwrapping: " + blocks.map { it.javaClass.simpleName })

        assertEquals(
            "without unwrapping, the table is gone: the importer saw a <div>",
            0,
            blocks.filterIsInstance<TL_iv.pageBlockTable>().size,
        )
    }

    @Test
    fun unwrappingTheWrapperKeepsTheGrid() {
        val cooked = harness.fixture("cooked-table-lightbox-blockquote.html")

        val blocks = RichHtml.parse(DiscourseCookedHtml.unwrapDiscourseWrappers(cooked)).map { it.block }
        println("importer blocks after unwrapping: " + blocks.map { it.javaClass.simpleName })

        val tables = blocks.filterIsInstance<TL_iv.pageBlockTable>()
        assertEquals("the unwrapped table survives as one table block", 1, tables.size)
        assertEquals("with a row per <tr>", 3, tables[0].rows.size)
        assertEquals("and a cell per <td>", 2, tables[0].rows[0].cells.size)
    }
}