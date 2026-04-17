package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderBlock
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableCell
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableRow
import org.junit.Test

class AppBrowserReaderSceneTest {
    @Test
    fun appBrowserReaderTypography_usesTelegramLikeArticleScale() {
        val spec = appBrowserReaderTypography()

        assertThat(spec.title.fontSize.value).isEqualTo(23f)
        assertThat(spec.subtitle.fontSize.value).isEqualTo(20f)
        assertThat(spec.paragraph.fontSize.value).isEqualTo(16f)
        assertThat(spec.paragraph.lineHeight.value).isEqualTo(24f)
        assertThat(spec.quote.fontSize.value).isEqualTo(15f)
        assertThat(spec.preformatted.fontSize.value).isEqualTo(14f)
        assertThat(spec.caption.fontSize.value).isEqualTo(14f)
        assertThat(spec.credit.fontSize.value).isEqualTo(12f)
    }

    @Test
    fun appBrowserReaderBlockMetrics_matchTelegramLikeSpacing() {
        val metrics = appBrowserReaderBlockMetrics()

        assertThat(metrics.horizontalContentPadding.value).isEqualTo(18f)
        assertThat(metrics.headerTopSpacing.value).isEqualTo(4f)
        assertThat(metrics.headerBottomSpacing.value).isEqualTo(12f)
        assertThat(metrics.blockSpacing(AppBrowserReaderBlock.Paragraph(content = testDocument())).value)
            .isEqualTo(0f)
        assertThat(metrics.blockSpacing(AppBrowserReaderBlock.CodeBlock(code = "println(1)", languageHint = "kotlin")).value)
            .isEqualTo(0f)
        assertThat(metrics.blockSpacing(AppBrowserReaderBlock.Table(rows = emptyList())).value)
            .isEqualTo(0f)
    }

    @Test
    fun readerImageMaxHeight_prefersTelegramLikeViewportClampOnPortraitScreens() {
        val height = readerImageMaxHeight(
            screenWidthDp = 412,
            screenHeightDp = 915,
        )

        assertThat(height.value).isWithin(0.01f).of(388f)
    }

    @Test
    fun readerImageMaxHeight_staysReasonableOnSmallScreens() {
        val height = readerImageMaxHeight(
            screenWidthDp = 320,
            screenHeightDp = 568,
        )

        assertThat(height.value).isWithin(0.01f).of(296f)
    }

    @Test
    fun resolveReaderTableLayout_placesCellsWithRowspanAndColspan() {
        val layout = resolveReaderTableLayout(
            rows = listOf(
                AppBrowserReaderTableRow(
                    cells = listOf(
                        AppBrowserReaderTableCell(
                            content = testDocument(),
                            rowSpan = 2,
                        ),
                        AppBrowserReaderTableCell(
                            content = testDocument(),
                            columnSpan = 2,
                        ),
                    ),
                ),
                AppBrowserReaderTableRow(
                    cells = listOf(
                        AppBrowserReaderTableCell(content = testDocument()),
                        AppBrowserReaderTableCell(content = testDocument()),
                    ),
                ),
            ),
        )

        assertThat(layout.columnCount).isEqualTo(3)
        assertThat(layout.rowCount).isEqualTo(2)
        assertThat(layout.placements).hasSize(4)
        assertThat(layout.placements[0].row).isEqualTo(0)
        assertThat(layout.placements[0].column).isEqualTo(0)
        assertThat(layout.placements[1].row).isEqualTo(0)
        assertThat(layout.placements[1].column).isEqualTo(1)
        assertThat(layout.placements[2].row).isEqualTo(1)
        assertThat(layout.placements[2].column).isEqualTo(1)
        assertThat(layout.placements[3].row).isEqualTo(1)
        assertThat(layout.placements[3].column).isEqualTo(2)
    }

    @Test
    fun readerListMarkerWidth_matchesTelegramLikeGutterBuckets() {
        assertThat(readerListMarkerWidth(ordered = false, itemCount = 2).value).isEqualTo(26f)
        assertThat(readerListMarkerWidth(ordered = true, itemCount = 3).value).isEqualTo(26f)
        assertThat(readerListMarkerWidth(ordered = true, itemCount = 10).value).isEqualTo(30f)
        assertThat(readerListMarkerWidth(ordered = true, itemCount = 100).value).isEqualTo(34f)
    }
}

private fun testDocument() = io.github.baba983.nativedo.core.ui.TextDocument(
    text = "test",
    entities = emptyList(),
)
