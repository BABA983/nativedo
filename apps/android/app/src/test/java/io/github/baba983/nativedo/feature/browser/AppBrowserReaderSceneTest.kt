package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.unit.dp
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

    @Test
    fun appBrowserReaderContainerChromeState_matchesTelegramFullscreenAndHalfSheet() {
        val partial = buildAppBrowserReaderContainerChromeState(fullscreenProgress = 0f)
        val fullscreen = buildAppBrowserReaderContainerChromeState(fullscreenProgress = 1f)

        assertThat(partial.cornerRadius).isEqualTo(16.dp)
        assertThat(partial.handleWidth).isEqualTo(32.dp)
        assertThat(partial.handleAlpha).isEqualTo(1f)
        assertThat(partial.occupiesStatusBar).isFalse()

        assertThat(fullscreen.cornerRadius).isEqualTo(0.dp)
        assertThat(fullscreen.handleWidth).isEqualTo(48.dp)
        assertThat(fullscreen.handleAlpha).isEqualTo(0f)
        assertThat(fullscreen.occupiesStatusBar).isTrue()
    }

    @Test
    fun appBrowserReaderTopChromeHeight_onlyInterpolatesAfterAttachThreshold() {
        val partial = buildAppBrowserReaderTopChromeHeight(
            fullscreenProgress = 0f,
            statusBarInset = 30.dp,
        )
        val mid = buildAppBrowserReaderTopChromeHeight(
            fullscreenProgress = 0.5f,
            statusBarInset = 30.dp,
        )
        val fullscreen = buildAppBrowserReaderTopChromeHeight(
            fullscreenProgress = 1f,
            statusBarInset = 30.dp,
        )

        assertThat(partial).isEqualTo(24.dp)
        assertThat(mid).isEqualTo(24.dp)
        assertThat(fullscreen).isEqualTo(30.dp)
    }

    @Test
    fun appBrowserReaderTopChromeHeight_waitsForAttachThresholdBeforeOccupyingStatusBar() {
        val beforeAttach = buildAppBrowserReaderTopChromeHeight(
            fullscreenProgress = 0.84f,
            statusBarInset = 30.dp,
        )

        assertThat(beforeAttach).isEqualTo(24.dp)
    }

    @Test
    fun appBrowserReaderContainerChromeState_keepsSheetGeometryUntilAttachThreshold() {
        val beforeAttach = buildAppBrowserReaderContainerChromeState(fullscreenProgress = 0.84f)

        assertThat(beforeAttach.cornerRadius).isEqualTo(16.dp)
        assertThat(beforeAttach.handleWidth).isEqualTo(32.dp)
        assertThat(beforeAttach.handleAlpha).isEqualTo(1f)
        assertThat(beforeAttach.occupiesStatusBar).isFalse()
    }

    @Test
    fun appBrowserReaderFollowUpTransitionState_slidesInFromRightWithoutReusingSheetOpenMotion() {
        val entering = buildAppBrowserReaderFollowUpTransitionState(progress = 0f)
        val mid = buildAppBrowserReaderFollowUpTransitionState(progress = 0.5f)
        val shown = buildAppBrowserReaderFollowUpTransitionState(progress = 1f)

        assertThat(entering.translationFraction).isEqualTo(1f)
        assertThat(entering.alpha).isEqualTo(0.92f)
        assertThat(mid.translationFraction).isEqualTo(0.5f)
        assertThat(mid.alpha).isWithin(0.0001f).of(0.96f)
        assertThat(shown.translationFraction).isEqualTo(0f)
        assertThat(shown.alpha).isEqualTo(1f)
    }
}

private fun testDocument() = io.github.baba983.nativedo.core.ui.TextDocument(
    text = "test",
    entities = emptyList(),
)
