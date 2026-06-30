package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ReplyMessageLine

@RunWith(RobolectricTestRunner::class)
class OneboxBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.displaySize.set(AndroidUtilities.dp(393f), AndroidUtilities.dp(852f))
    }

    @Test
    fun linkPreviewGeometry_matchesChatMessageCellSmallPreviewContract() {
        assertThat(OneboxBlock.outerHorizontalInsetPx()).isEqualTo(PostBodyBlock.DEFAULT_HORIZONTAL_INSET)
        assertThat(OneboxBlock.linkPreviewXOffsetPx()).isEqualTo(-AndroidUtilities.dp(1.33f))
        assertThat(OneboxBlock.textLeftPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(OneboxBlock.textRightPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(OneboxBlock.linkPreviewTopInsetPx()).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(OneboxBlock.linkPreviewBottomInsetPx()).isEqualTo(0)
        assertThat(OneboxBlock.linkPreviewBackgroundBottomInsetPx()).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(OneboxBlock.linkPreviewOutsideBottomInsetPx()).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(OneboxBlock.siteNameTextYOffsetPx()).isEqualTo(-AndroidUtilities.dp(1f))
        assertThat(OneboxBlock.previewTextYOffsetPx()).isEqualTo(-AndroidUtilities.dp(3f))
        assertThat(OneboxBlock.smallImageStartYPx(OneboxBlock.linkPreviewTopInsetPx()))
            .isEqualTo(AndroidUtilities.dp(5f))
        assertThat(OneboxBlock.smallImageBottomInsetPx()).isEqualTo(AndroidUtilities.dp(1f))
        assertThat(OneboxBlock.smallImageRightInsetPx()).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(OneboxBlock.smallImageSidePx()).isEqualTo(AndroidUtilities.dp(48f))
        assertThat(OneboxBlock.smallImageSideMarginPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(OneboxBlock.smallImageRoundRadiusPx()).isEqualTo(AndroidUtilities.dp(4f))
        assertThat(OneboxBlock.interLayoutGapPx()).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(OneboxBlock.linkPreviewBackgroundRadiusDp())
            .isEqualTo(Math.floor(SharedConfig.bubbleRadius / 3.0).toFloat())
        assertThat(OneboxBlock.titleSpacingAfterSiteNamePx()).isEqualTo(AndroidUtilities.dp(4f))
    }

    @Test
    fun linkPreviewMediaRadius_matchesChatMessageCellMinRadiusBranch() {
        val oldRadius = SharedConfig.bubbleRadius
        try {
            SharedConfig.bubbleRadius = 17
            assertThat(OneboxBlock.linkPreviewMediaRoundRadiusPx()).isEqualTo(AndroidUtilities.dp(4f))
            assertThat(OneboxBlock.smallImageRoundRadiusPx()).isEqualTo(OneboxBlock.linkPreviewMediaRoundRadiusPx())

            SharedConfig.bubbleRadius = 5
            assertThat(OneboxBlock.linkPreviewMediaRoundRadiusPx()).isEqualTo(AndroidUtilities.dp(3f))

            SharedConfig.bubbleRadius = 2
            assertThat(OneboxBlock.linkPreviewMediaRoundRadiusPx()).isEqualTo(AndroidUtilities.dp(2f))
        } finally {
            SharedConfig.bubbleRadius = oldRadius
        }
    }

    @Test
    fun horizontalInset_matchesChatMessageCellPreviewColumn() {
        val block = OneboxBlock(
            CookedPostBlock.Onebox(
                href = "https://example.dev/post",
                domain = "example.dev",
                siteIconUrl = null,
                thumbnailUrl = null,
                title = "Example",
                description = "Preview text",
            ),
            buildContext(),
            null,
            "onebox:test",
        )

        assertThat(block.horizontalInsetPx()).isEqualTo(PostBodyBlock.DEFAULT_HORIZONTAL_INSET)
        assertThat(block.horizontalInsetPx() + OneboxBlock.linkPreviewXOffsetPx() + OneboxBlock.textLeftPx())
            .isEqualTo(PostBodyBlock.DEFAULT_HORIZONTAL_INSET - AndroidUtilities.dp(1.33f) + AndroidUtilities.dp(10f))
    }

    @Test
    fun previewBounds_applyChatMessageCellLinkXShiftWithoutMovingRightEdge() {
        val width = AndroidUtilities.dp(320f)
        val cardBounds = OneboxBlock.linkPreviewCardBoundsPx(width, AndroidUtilities.dp(80f))
        val hitBounds = OneboxBlock.linkPreviewHitBoundsPx(
            width,
            OneboxBlock.linkPreviewTopInsetPx(),
            OneboxBlock.linkPreviewChromeBottomPx(AndroidUtilities.dp(42f)),
        )
        val thumbnailBounds = OneboxBlock.largeImageBoundsInPreviewPx(width, 1280, 720)

        assertThat(cardBounds.left).isEqualTo(OneboxBlock.linkPreviewXOffsetPx().toFloat())
        assertThat(cardBounds.right).isEqualTo(width.toFloat())
        assertThat(hitBounds.left).isEqualTo(0f)
        assertThat(hitBounds.right).isEqualTo(width.toFloat())
        assertThat(thumbnailBounds.left)
            .isEqualTo((OneboxBlock.linkPreviewXOffsetPx() + OneboxBlock.largeImageLeftInsetPx()).toFloat())
        assertThat(thumbnailBounds.right)
            .isEqualTo((width - OneboxBlock.largeImageRightInsetPx()).toFloat())
    }

    @Test
    fun linkPreviewHitBounds_useUnshiftedTextColumnLikeChatMessageCell() {
        val width = AndroidUtilities.dp(320f)
        val chromeBottom = OneboxBlock.linkPreviewChromeBottomPx(AndroidUtilities.dp(42f))
        val previewLayout = previewLayoutForTest(
            cardBounds = OneboxBlock.linkPreviewCardBoundsPx(width, AndroidUtilities.dp(60f)),
            hitBounds = OneboxBlock.linkPreviewHitBoundsPx(
                width,
                OneboxBlock.linkPreviewTopInsetPx(),
                chromeBottom,
            ),
        )
        val y = OneboxBlock.linkPreviewTopInsetPx().toFloat()

        assertThat(previewLayout.hitTargetForTest(OneboxBlock.linkPreviewXOffsetPx() / 2f, y)).isNull()
        assertThat(previewLayout.hitTargetForTest(0f, y)).isNotNull()
    }

    @Test
    fun shiftedTextX_matchesChatMessageCellLinkPreviewOffset() {
        val previewWidth = AndroidUtilities.dp(320f)
        val layoutWidth = previewWidth - OneboxBlock.textLeftPx() - OneboxBlock.textRightPx()
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(14f).toFloat()
        }
        val shiftedLayout = StaticLayout(
            "Preview",
            paint,
            layoutWidth,
            Layout.Alignment.ALIGN_OPPOSITE,
            1.0f,
            0f,
            false,
        )
        assertThat(shiftedLayout.getLineLeft(0)).isGreaterThan(1f)

        val expected = OneboxBlock.textLeftPx() +
            previewWidth -
            AndroidUtilities.dp(20f) -
            shiftedLayout.getLineWidth(0).toInt() -
            shiftedLayout.getLineLeft(0).toInt()

        assertThat(OneboxBlock.previewTextXPx(previewWidth, shiftedLayout, false, 0))
            .isEqualTo(expected.toFloat())
    }

    @Test
    fun linkPreviewTextColors_matchChatMessageCellDrawOrder() {
        val context = buildContext(textColor = 0xFFCC0000.toInt())
        val block = OneboxBlock(
            CookedPostBlock.Onebox(
                href = "https://example.dev/post",
                domain = "example.dev",
                siteIconUrl = null,
                thumbnailUrl = null,
                title = "Example",
                description = "Preview text",
            ),
            context,
            null,
            "onebox:test",
        )
        val linkLine = ReplyMessageLine(context.host)
        val linkNameColor = linkLine.check(null, null, null, null, ReplyMessageLine.TYPE_LINK)

        assertThat(block.textPaintColor("domainPaint")).isEqualTo(linkNameColor)
        assertThat(linkNameColor).isEqualTo(linkLine.color)
        assertThat(block.textPaintColor("titlePaint")).isEqualTo(context.textColor)
        assertThat(block.textPaintColor("descriptionPaint")).isEqualTo(context.textColor)
        assertThat(block.textPaintLinkColor("descriptionPaint")).isEqualTo(linkLine.color)
    }

    @Test
    fun linkPreviewTextPaints_followChatMessageCellReplyPaintSizing() {
        val oldFontSize = SharedConfig.fontSize
        try {
            SharedConfig.fontSize = 19
            val context = buildContext(textColor = 0xFFCC0000.toInt())
            val block = OneboxBlock(
                CookedPostBlock.Onebox(
                    href = "https://example.dev/post",
                    domain = "example.dev",
                    siteIconUrl = null,
                    thumbnailUrl = null,
                    title = "Example",
                    description = "Preview text",
                ),
                context,
                null,
                "onebox:test",
            )
            val expectedSmallerDp = (2 * SharedConfig.fontSize + 10) / 3f
            val expectedTextSize = AndroidUtilities.dp(expectedSmallerDp).toFloat()

            assertThat(block.textPaintForTest("domainPaint").textSize).isEqualTo(expectedTextSize)
            assertThat(block.textPaintForTest("titlePaint").textSize).isEqualTo(expectedTextSize)
            assertThat(block.textPaintForTest("descriptionPaint").textSize).isEqualTo(expectedTextSize)
            assertThat(block.textPaintForTest("domainPaint").typeface).isEqualTo(Theme.chat_replyNamePaint.typeface)
            assertThat(block.textPaintForTest("titlePaint").typeface).isEqualTo(Theme.chat_replyNamePaint.typeface)
            assertThat(block.textPaintForTest("descriptionPaint").typeface).isEqualTo(Theme.chat_replyTextPaint.typeface)
        } finally {
            SharedConfig.fontSize = oldFontSize
            Theme.updateChatMessageTextPaints()
        }
    }

    @Test
    fun replyMessageLineTypeLinkBackgroundAlpha_matchesChatMessageCellLinkPreviewBranch() {
        val darkProvider = object : Theme.ResourcesProvider {
            override fun isDark(): Boolean = true
        }
        val linkLine = ReplyMessageLine(View(RuntimeEnvironment.getApplication()))
        linkLine.check(null, null, null, darkProvider, ReplyMessageLine.TYPE_LINK)

        assertThat(linkLine.backgroundColor).isEqualTo(Theme.multAlpha(linkLine.color, 0.10f))

        val replyLine = ReplyMessageLine(View(RuntimeEnvironment.getApplication()))
        replyLine.check(null, null, null, darkProvider, ReplyMessageLine.TYPE_REPLY)

        assertThat(replyLine.backgroundColor).isEqualTo(Theme.multAlpha(replyLine.color, 0.12f))
    }

    @Test
    fun replyMessageLineLinkPreviewBackgroundRadii_matchChatMessageCellBubbleRadiusFloor() {
        val oldRadius = SharedConfig.bubbleRadius
        try {
            SharedConfig.bubbleRadius = 17
            val line = ReplyMessageLine(View(RuntimeEnvironment.getApplication()))
            line.check(null, null, null, null, ReplyMessageLine.TYPE_LINK)

            line.drawBackground(
                Canvas(Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)),
                RectF(0f, 0f, 80f, 40f),
                1f,
                1f,
                1f,
                1f,
            )

            val linkPreviewRadius = AndroidUtilities.dp(Math.floor(SharedConfig.bubbleRadius / 3.0).toFloat()).toFloat()
            assertThat(line.radii[0]).isEqualTo(linkPreviewRadius)
            assertThat(line.radii[1]).isEqualTo(linkPreviewRadius)
            assertThat(line.radii[2]).isEqualTo(AndroidUtilities.dp(1f).toFloat())
            assertThat(line.radii[3]).isEqualTo(AndroidUtilities.dp(1f).toFloat())
            assertThat(line.radii[6]).isEqualTo(linkPreviewRadius)
            assertThat(line.radii[7]).isEqualTo(linkPreviewRadius)
        } finally {
            SharedConfig.bubbleRadius = oldRadius
        }
    }

    @Test
    fun linkPreviewTextLimits_matchChatMessageCellGenerateStaticLayoutPath() {
        assertThat(OneboxBlock.previewTextLineSpacingPx()).isEqualTo(AndroidUtilities.dp(1f))
        assertThat(OneboxBlock.regularSiteNameLineSpacingPx()).isEqualTo(0)
        assertThat(OneboxBlock.smallImageSiteNameLineSpacingPx()).isEqualTo(OneboxBlock.previewTextLineSpacingPx())
        assertThat(OneboxBlock.smallImageTextLines()).isEqualTo(3)
        assertThat(OneboxBlock.titleMaxLines()).isEqualTo(4)
        assertThat(OneboxBlock.descriptionMaxLines()).isEqualTo(6)
    }

    @Test
    fun siteNameLayoutWidth_matchesChatMessageCellMeasuredWidthForRegularPreview() {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(14f).toFloat()
        }
        val maxWidth = AndroidUtilities.dp(220f)

        assertThat(OneboxBlock.siteNameLayoutWidthPx("example.dev", paint, maxWidth))
            .isEqualTo(kotlin.math.ceil(paint.measureText("example.dev") + 1f).toInt())
        assertThat(OneboxBlock.siteNameLayoutWidthPx("a very long example domain name", paint, AndroidUtilities.dp(20f)))
            .isEqualTo(AndroidUtilities.dp(20f))
    }

    @Test
    fun regularSiteNameLayout_usesMeasuredWidthInsteadOfFullTextColumn() {
        val width = AndroidUtilities.dp(320f)
        val context = buildContext()
        val block = OneboxBlock(
            onebox(previewType = null).copy(
                thumbnailUrl = null,
                thumbnailWidthPx = 0,
                thumbnailHeightPx = 0,
                title = "example.dev",
                titleHtml = null,
                description = null,
                descriptionHtml = null,
            ),
            context,
            null,
            "onebox:test",
        )

        block.measure(width)

        val domainPaint = block.textPaintForTest("domainPaint")
        val textWidth = width - OneboxBlock.textLeftPx() - OneboxBlock.textRightPx()
        assertThat(block.domainLayoutWidthForTest())
            .isEqualTo(OneboxBlock.siteNameLayoutWidthPx("example.dev", domainPaint, textWidth))
        assertThat(block.domainLayoutWidthForTest()).isLessThan(textWidth)
    }

    @Test
    fun linkPreviewChromeHeight_usesChatMessageCellMeasureGaps() {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(14f).toFloat()
        }
        val domain = staticLayout("example.dev", paint)
        val title = staticLayout("Example title", paint)
        val description = staticLayout("Example description", paint)

        assertThat(OneboxBlock.linkPreviewChromeHeightPx(domain, title, description))
            .isEqualTo(domain.height + title.height + description.height + AndroidUtilities.dp(2f) * 2)
        val finalChromeHeight = OneboxBlock.linkPreviewChromeHeightPx(domain, title, description) +
            OneboxBlock.linkPreviewBackgroundBottomInsetPx()

        assertThat(OneboxBlock.linkPreviewChromeBottomPx(finalChromeHeight))
            .isEqualTo(
                OneboxBlock.linkPreviewTopInsetPx() +
                    domain.height +
                    title.height +
                    description.height +
                    AndroidUtilities.dp(2f) * 2 +
                    OneboxBlock.linkPreviewBackgroundBottomInsetPx() +
                    OneboxBlock.linkPreviewBottomInsetPx(),
            )
    }

    @Test
    fun smallImageChromeHeight_matchesChatMessageCellLinkPreviewTrim() {
        val shortContentHeight = AndroidUtilities.dp(20f)
        val tallContentHeight = AndroidUtilities.dp(64f)

        assertThat(OneboxBlock.smallImageMinChromeHeightPx()).isEqualTo(AndroidUtilities.dp(50f))
        assertThat(OneboxBlock.smallImageChromeBottomTrimPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(OneboxBlock.smallImageChromeHeightPx(shortContentHeight))
            .isEqualTo(AndroidUtilities.dp(42f))
        assertThat(OneboxBlock.smallImageChromeHeightPx(tallContentHeight))
            .isEqualTo(AndroidUtilities.dp(56f))
        assertThat(OneboxBlock.linkPreviewTopInsetPx() + OneboxBlock.smallImageChromeHeightPx(shortContentHeight))
            .isEqualTo(AndroidUtilities.dp(48f))
        assertThat(
            OneboxBlock.linkPreviewChromeBottomPx(
                OneboxBlock.smallImageChromeHeightPx(shortContentHeight) +
                    OneboxBlock.linkPreviewBackgroundBottomInsetPx() +
                    OneboxBlock.mediaTextExtraChromePx(),
            ),
        ).isEqualTo(AndroidUtilities.dp(48f) + OneboxBlock.linkPreviewBackgroundBottomInsetPx() + OneboxBlock.mediaTextExtraChromePx())
    }

    @Test
    fun linkPreviewBouncePivot_matchesChatMessageCellPreviewContentCenter() {
        val chromeBottom = OneboxBlock.linkPreviewChromeBottomPx(
            AndroidUtilities.dp(42f) +
                OneboxBlock.linkPreviewBackgroundBottomInsetPx() +
                OneboxBlock.mediaTextExtraChromePx(),
        )
        val smallImageCardTotalHeight = OneboxBlock.smallImageStartYPx(OneboxBlock.linkPreviewTopInsetPx()) +
            OneboxBlock.smallImageSidePx() +
            OneboxBlock.smallImageBottomInsetPx() +
            OneboxBlock.linkPreviewOutsideBottomInsetPx()

        assertThat(OneboxBlock.linkPreviewBouncePivotYPx(chromeBottom))
            .isEqualTo(OneboxBlock.linkPreviewTopInsetPx() + (chromeBottom - OneboxBlock.linkPreviewTopInsetPx()) / 2f)
        assertThat(OneboxBlock.linkPreviewBouncePivotYPx(chromeBottom))
            .isNotEqualTo(smallImageCardTotalHeight / 2f)
    }

    @Test
    fun smallImageDescriptionTextWidth_reservesExtraChatMessageCellMargin() {
        val textWidth = AndroidUtilities.dp(220f)
        val thumbSize = OneboxBlock.smallImageSidePx()

        assertThat(OneboxBlock.smallImageTextWidthPx(textWidth, thumbSize))
            .isEqualTo(textWidth - thumbSize - OneboxBlock.smallImageSideMarginPx())
        assertThat(OneboxBlock.smallImageDescriptionTextWidthPx(textWidth, thumbSize))
            .isEqualTo(textWidth - thumbSize - OneboxBlock.smallImageSideMarginPx() * 2)
    }

    @Test
    fun rtlTitleSmallImageDescriptionWidth_matchesChatMessageCellRtlBranch() {
        val textWidth = AndroidUtilities.dp(220f)
        val thumbSize = OneboxBlock.smallImageSidePx()
        val rtlDescriptionWidth = OneboxBlock.descriptionTextWidthAfterTitlePx(
            textWidth,
            thumbSize,
            true,
        )

        assertThat(OneboxBlock.descriptionTextWidthAfterTitlePx(textWidth, thumbSize, false))
            .isEqualTo(textWidth)
        assertThat(rtlDescriptionWidth).isEqualTo(textWidth - thumbSize)
        assertThat(OneboxBlock.smallImageDescriptionTextWidthPx(rtlDescriptionWidth, thumbSize))
            .isEqualTo(textWidth - thumbSize * 2 - OneboxBlock.smallImageSideMarginPx() * 2)
    }

    @Test
    fun mediaLayout_usesTelegramSmallImageTypeWhitelist() {
        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = "article"))).isTrue()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = "app"))).isTrue()

        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = "video"))).isFalse()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = "image"))).isFalse()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = null))).isFalse()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(onebox(previewType = "telegram_aicomposetone"))).isTrue()
    }

    @Test
    fun smallImageThumbnailTap_opensLinkLikeChatMessageCellInstantPress() {
        var clickedLink: String? = null
        var imageClicks = 0
        val context = buildContext(
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) {
                    clickedLink = url
                }

                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit

                override fun onImageClick(
                    url: String,
                    gallery: List<GalleryImage>,
                    sourceBoundsInBlock: RectF?,
                    hostView: View,
                    sourceBoundsProvider: (String) -> RectF?,
                    sourceImageReceiverProvider: (String) -> org.telegram.messenger.ImageReceiver?,
                    sourceBitmapProvider: (String) -> org.telegram.messenger.ImageReceiver.BitmapHolder?,
                    sourceRadiusProvider: (String) -> IntArray?,
                ) {
                    imageClicks++
                }

                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
            },
        )
        val block = OneboxBlock(onebox(previewType = "article"), context, null, "onebox:test")
        val thumbnailBounds = RectF(
            AndroidUtilities.dp(240f).toFloat(),
            OneboxBlock.smallImageStartYPx(OneboxBlock.linkPreviewTopInsetPx()).toFloat(),
            AndroidUtilities.dp(288f).toFloat(),
            (OneboxBlock.smallImageStartYPx(OneboxBlock.linkPreviewTopInsetPx()) + OneboxBlock.smallImageSidePx()).toFloat(),
        )
        block.setPreviewLayoutForTest(
            previewLayoutForTest(
                cardBounds = RectF(0f, 0f, AndroidUtilities.dp(320f).toFloat(), AndroidUtilities.dp(72f).toFloat()),
                hitBounds = RectF(
                    0f,
                    OneboxBlock.linkPreviewTopInsetPx().toFloat(),
                    AndroidUtilities.dp(320f).toFloat(),
                    AndroidUtilities.dp(64f).toFloat(),
                ),
                thumbnailBounds = thumbnailBounds,
            ),
        )
        val x = thumbnailBounds.centerX()
        val y = thumbnailBounds.centerY()

        assertThat(OneboxBlock.shouldOpenThumbnailImage(onebox(previewType = "article"))).isFalse()
        block.setPressedTargetForTest("Thumbnail")
        assertThat(block.onTouch(motionEvent(MotionEvent.ACTION_UP, x, y), x, y)).isTrue()

        assertThat(clickedLink).isEqualTo("https://example.dev/post")
        assertThat(imageClicks).isEqualTo(0)
    }

    @Test
    fun linkPreviewMoveWithinSameTarget_keepsPressLikeChatMessageCellHotspotMove() {
        var clickedLink: String? = null
        val context = buildContext(
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) {
                    clickedLink = url
                }

                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit
                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
            },
        )
        val block = OneboxBlock(onebox(previewType = "article"), context, null, "onebox:test")
        block.setPreviewLayoutForTest(
            previewLayoutForTest(
                cardBounds = RectF(0f, 0f, AndroidUtilities.dp(320f).toFloat(), AndroidUtilities.dp(72f).toFloat()),
                hitBounds = RectF(
                    0f,
                    OneboxBlock.linkPreviewTopInsetPx().toFloat(),
                    AndroidUtilities.dp(320f).toFloat(),
                    AndroidUtilities.dp(64f).toFloat(),
                ),
            ),
        )
        block.setPressedTargetForTest("Card")
        val movedX = AndroidUtilities.dp(96f).toFloat()
        val movedY = OneboxBlock.linkPreviewTopInsetPx() + AndroidUtilities.dp(12f).toFloat()

        assertThat(block.onTouch(motionEvent(MotionEvent.ACTION_MOVE, movedX, movedY), movedX, movedY)).isTrue()
        assertThat(block.onTouch(motionEvent(MotionEvent.ACTION_UP, movedX, movedY), movedX, movedY)).isTrue()

        assertThat(clickedLink).isEqualTo("https://example.dev/post")
    }

    @Test
    fun linkPreviewMoveAcrossPreviewTargets_keepsWholePreviewPressLikeChatMessageCell() {
        var clickedLink: String? = null
        var imageClicks = 0
        val context = buildContext(
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) {
                    clickedLink = url
                }

                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit

                override fun onImageClick(
                    url: String,
                    gallery: List<GalleryImage>,
                    sourceBoundsInBlock: RectF?,
                    hostView: View,
                    sourceBoundsProvider: (String) -> RectF?,
                    sourceImageReceiverProvider: (String) -> org.telegram.messenger.ImageReceiver?,
                    sourceBitmapProvider: (String) -> org.telegram.messenger.ImageReceiver.BitmapHolder?,
                    sourceRadiusProvider: (String) -> IntArray?,
                ) {
                    imageClicks++
                }

                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
            },
        )
        val block = OneboxBlock(onebox(previewType = "video"), context, null, "onebox:test")
        val thumbnailBounds = RectF(
            AndroidUtilities.dp(180f).toFloat(),
            OneboxBlock.linkPreviewTopInsetPx().toFloat(),
            AndroidUtilities.dp(300f).toFloat(),
            AndroidUtilities.dp(80f).toFloat(),
        )
        block.setPreviewLayoutForTest(
            previewLayoutForTest(
                cardBounds = RectF(0f, 0f, AndroidUtilities.dp(320f).toFloat(), AndroidUtilities.dp(96f).toFloat()),
                hitBounds = RectF(
                    0f,
                    OneboxBlock.linkPreviewTopInsetPx().toFloat(),
                    AndroidUtilities.dp(320f).toFloat(),
                    AndroidUtilities.dp(88f).toFloat(),
                ),
                thumbnailBounds = thumbnailBounds,
            ),
        )
        block.setPressedTargetForTest("Card")
        val x = thumbnailBounds.centerX()
        val y = thumbnailBounds.centerY()

        assertThat(block.onTouch(motionEvent(MotionEvent.ACTION_MOVE, x, y), x, y)).isTrue()
        assertThat(block.onTouch(motionEvent(MotionEvent.ACTION_UP, x, y), x, y)).isTrue()

        assertThat(clickedLink).isEqualTo("https://example.dev/post")
        assertThat(imageClicks).isEqualTo(0)
    }

    @Test
    fun mediaLayout_respectsTelegramForceLargeMediaEquivalent() {
        val forcedLargeArticle = onebox(previewType = "article").copy(forceLargeMedia = true)

        assertThat(OneboxBlock.shouldUseSmallImageLayout(forcedLargeArticle)).isFalse()
        assertThat(OneboxBlock.shouldOpenThumbnailImage(forcedLargeArticle)).isTrue()
    }

    @Test
    fun mediaLayout_respectsTelegramForceSmallMediaEquivalent() {
        val forcedSmallVideo = onebox(previewType = "video").copy(forceSmallMedia = true)
        val forcedSmallAndLargeVideo = onebox(previewType = "video").copy(
            forceLargeMedia = true,
            forceSmallMedia = true,
        )

        assertThat(OneboxBlock.shouldUseSmallImageLayout(forcedSmallVideo)).isTrue()
        assertThat(OneboxBlock.shouldOpenThumbnailImage(forcedSmallVideo)).isFalse()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(forcedSmallAndLargeVideo)).isTrue()
    }

    @Test
    fun mediaLayout_doesNotUseSmallImageForParserFallbackTitleOnly() {
        val fallbackDomainTitle = onebox(previewType = "article").copy(
            title = "example.dev",
            titleHtml = null,
            description = null,
            descriptionHtml = null,
        )
        val fallbackHrefTitle = onebox(previewType = "article").copy(
            domain = null,
            title = "https://example.dev/post",
            titleHtml = null,
            description = null,
            descriptionHtml = null,
        )

        assertThat(OneboxBlock.shouldUseSmallImageLayout(fallbackDomainTitle)).isFalse()
        assertThat(OneboxBlock.shouldUseSmallImageLayout(fallbackHrefTitle)).isFalse()
    }

    @Test
    fun largeImageHeight_usesChatMessageCellPhotoScalingBounds() {
        val measureWidth = AndroidUtilities.dp(320f)

        assertThat(OneboxBlock.largeImageHeightPx(measureWidth, 1280, 720))
            .isEqualTo((720 / (1280 / measureWidth.toFloat())).toInt())
        assertThat(OneboxBlock.largeImageHeightPx(measureWidth, 320, 2000))
            .isEqualTo(OneboxBlock.largeImageMaxHeightPx())
        assertThat(OneboxBlock.largeImageHeightPx(AndroidUtilities.dp(240f), 3000, 300))
            .isEqualTo(OneboxBlock.largeImageMinHeightPx())
        assertThat(OneboxBlock.mediaTextExtraChromePx()).isEqualTo(AndroidUtilities.dp(2.66f))
    }

    @Test
    fun largeImageBounds_usesTelegramLinkPreviewInsets() {
        val width = AndroidUtilities.dp(320f)

        val bounds = OneboxBlock.largeImageBoundsPx(width, 1280, 720)

        assertThat(bounds.left).isEqualTo(OneboxBlock.largeImageLeftInsetPx().toFloat())
        assertThat(bounds.right).isEqualTo((width - OneboxBlock.largeImageRightInsetPx()).toFloat())
        assertThat(bounds.height()).isEqualTo(
            OneboxBlock.largeImageHeightPx(
                OneboxBlock.largeImageMeasureWidthPx(width),
                1280,
                720,
            ).toFloat(),
        )
    }

    @Test
    fun largeImageWidths_matchChatMessageCellMeasureAndDrawWidths() {
        val width = AndroidUtilities.dp(320f)

        assertThat(OneboxBlock.largeImageMeasureWidthPx(width))
            .isEqualTo(width - AndroidUtilities.dp(2f))
        assertThat(OneboxBlock.largeImageDrawWidthPx(width))
            .isEqualTo(width - OneboxBlock.largeImageLeftInsetPx() - OneboxBlock.largeImageRightInsetPx())
    }

    @Test
    fun linkPreviewWidth_usesVisiblePreviewContentInsteadOfAlwaysFillingColumn() {
        val width = AndroidUtilities.dp(320f)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(14f).toFloat()
        }
        val title = staticLayout("Short", paint)
        val expectedCompactWidth = maxOf(
            AndroidUtilities.dp(120f),
            title.getLineWidth(0).toInt() + OneboxBlock.textLeftPx() + OneboxBlock.textRightPx(),
        )

        assertThat(
            OneboxBlock.linkPreviewWidthPx(
                width,
                0,
                false,
                null,
                0,
                title,
                0,
                null,
                0,
            ),
        ).isEqualTo(expectedCompactWidth)

        assertThat(
            OneboxBlock.linkPreviewWidthPx(
                width,
                0,
                true,
                null,
                0,
                title,
                0,
                null,
                0,
            ),
        ).isEqualTo(width)
    }

    @Test
    fun linkPreviewHitBounds_startAtTelegramPreviewYNotBackgroundPadding() {
        val previewLayout = previewLayoutForTest(
            cardBounds = RectF(0f, 0f, AndroidUtilities.dp(320f).toFloat(), AndroidUtilities.dp(60f).toFloat()),
            hitBounds = RectF(
                0f,
                OneboxBlock.linkPreviewTopInsetPx().toFloat(),
                AndroidUtilities.dp(320f).toFloat(),
                AndroidUtilities.dp(60f).toFloat(),
            ),
        )

        assertThat(previewLayout.hitTargetForTest(AndroidUtilities.dp(12f).toFloat(), 0f)).isNull()
        assertThat(
            previewLayout.hitTargetForTest(
                AndroidUtilities.dp(12f).toFloat(),
                OneboxBlock.linkPreviewTopInsetPx().toFloat(),
            ),
        ).isNotNull()
    }

    @Test
    fun linkPreviewHitBounds_bottomMatchesChatMessageCellExtraInset() {
        val finalChromeHeight = OneboxBlock.smallImageChromeHeightPx(AndroidUtilities.dp(20f)) +
            OneboxBlock.linkPreviewBackgroundBottomInsetPx() +
            OneboxBlock.mediaTextExtraChromePx()
        val chromeBottom = OneboxBlock.linkPreviewChromeBottomPx(finalChromeHeight)
        val hitBottom = OneboxBlock.linkPreviewHitBottomPx(chromeBottom)
        val previewLayout = previewLayoutForTest(
            cardBounds = RectF(
                0f,
                0f,
                AndroidUtilities.dp(320f).toFloat(),
                (hitBottom + OneboxBlock.linkPreviewOutsideBottomInsetPx()).toFloat(),
            ),
            hitBounds = RectF(
                0f,
                OneboxBlock.linkPreviewTopInsetPx().toFloat(),
                AndroidUtilities.dp(320f).toFloat(),
                hitBottom.toFloat(),
            ),
        )

        assertThat(hitBottom).isEqualTo(chromeBottom + AndroidUtilities.dp(8f))
        assertThat(hitBottom).isGreaterThan(chromeBottom)
        assertThat(
            previewLayout.hitTargetForTest(
                AndroidUtilities.dp(12f).toFloat(),
                (hitBottom - 1).toFloat(),
            ),
        ).isNotNull()
        assertThat(
            previewLayout.hitTargetForTest(
                AndroidUtilities.dp(12f).toFloat(),
                (hitBottom + 1).toFloat(),
            ),
        ).isNull()
    }

    @Test
    fun previewHtmlWithoutLinks_unwrapsOneboxAnchorsButKeepsInlineMarkup() {
        val html = """
            <a href="https://example.dev"><strong>Example</strong></a>
            <span class="spoiler">hidden</span>
        """.trimIndent()

        val sanitized = OneboxBlock.previewHtmlWithoutLinks(html)

        assertThat(sanitized).doesNotContain("<a ")
        assertThat(sanitized).contains("<strong>Example</strong>")
        assertThat(sanitized).contains("class=\"spoiler\"")
    }

    private fun onebox(previewType: String?): CookedPostBlock.Onebox {
        return CookedPostBlock.Onebox(
            href = "https://example.dev/post",
            domain = "example.dev",
            siteIconUrl = null,
            thumbnailUrl = "https://cdn.example.dev/thumb.jpg",
            thumbnailWidthPx = 1280,
            thumbnailHeightPx = 720,
            previewType = previewType,
            title = "Example",
            description = "Preview text",
        )
    }

    private fun staticLayout(text: String, paint: TextPaint): StaticLayout {
        return StaticLayout(
            text,
            paint,
            AndroidUtilities.dp(220f),
            Layout.Alignment.ALIGN_NORMAL,
            1.0f,
            0f,
            false,
        )
    }

    private fun buildContext(
        textColor: Int = 0xFF111111.toInt(),
        host: View = object : View(RuntimeEnvironment.getApplication()) {
            override fun playSoundEffect(soundConstant: Int) = Unit
        },
        delegate: PostBodyDelegate = object : PostBodyDelegate {
            override fun onLinkClick(url: String) = Unit
            override fun onTopicClick(topicId: Long) = Unit
            override fun onUserClick(username: String) = Unit
            override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
        },
    ): PostBodyBuildContext {
        return PostBodyBuildContext(
            host = host,
            baseUrl = "https://linux.do/",
            delegate = delegate,
            textColor = textColor,
            secondaryTextColor = 0xFF666666.toInt(),
            accentColor = 0xFF1677FF.toInt(),
            chipBackgroundColor = 0x221677FF,
            dividerColor = 0xFFE0E0E0.toInt(),
        )
    }

    private fun OneboxBlock.textPaintColor(fieldName: String): Int {
        val field = OneboxBlock::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return (field.get(this) as TextPaint).color
    }

    private fun OneboxBlock.textPaintLinkColor(fieldName: String): Int {
        val field = OneboxBlock::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return (field.get(this) as TextPaint).linkColor
    }

    private fun OneboxBlock.textPaintForTest(fieldName: String): TextPaint {
        val field = OneboxBlock::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(this) as TextPaint
    }

    private fun OneboxBlock.domainLayoutWidthForTest(): Int {
        return (positionedLayoutForTest("domainLayout")!!.layoutForTest() as StaticLayout).width
    }

    private fun OneboxBlock.positionedLayoutForTest(fieldName: String): Any? {
        val previewLayoutField = OneboxBlock::class.java.getDeclaredField("previewLayout")
        previewLayoutField.isAccessible = true
        val previewLayout = previewLayoutField.get(this)
        val positionedLayoutField = previewLayout.javaClass.getDeclaredField(fieldName)
        positionedLayoutField.isAccessible = true
        return positionedLayoutField.get(previewLayout)
    }

    private fun Any.layoutForTest(): Any {
        val layoutField = javaClass.getDeclaredField("layout")
        layoutField.isAccessible = true
        return layoutField.get(this)
    }

    private fun previewLayoutForTest(cardBounds: RectF, hitBounds: RectF, thumbnailBounds: RectF? = null): Any {
        val positionedLayoutClass = Class.forName("${OneboxBlock::class.java.name}\$PositionedLayout")
        val previewLayoutClass = Class.forName("${OneboxBlock::class.java.name}\$OneboxPreviewLayout")
        val constructor = previewLayoutClass.getDeclaredConstructor(
            RectF::class.java,
            RectF::class.java,
            RectF::class.java,
            positionedLayoutClass,
            positionedLayoutClass,
            positionedLayoutClass,
            Integer.TYPE,
            Integer.TYPE,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            cardBounds,
            hitBounds,
            thumbnailBounds,
            null,
            null,
            null,
            AndroidUtilities.dp(48f),
            AndroidUtilities.dp(60f),
        )
    }

    private fun OneboxBlock.setPreviewLayoutForTest(previewLayout: Any) {
        val previewLayoutField = OneboxBlock::class.java.getDeclaredField("previewLayout")
        previewLayoutField.isAccessible = true
        previewLayoutField.set(this, previewLayout)
    }

    private fun OneboxBlock.setPressedTargetForTest(name: String) {
        val targetClass = Class.forName("${OneboxBlock::class.java.name}\$OneboxHitTarget")
        val target = targetClass.enumConstants.single { (it as Enum<*>).name == name }
        val pressedTargetField = OneboxBlock::class.java.getDeclaredField("pressedTarget")
        pressedTargetField.isAccessible = true
        pressedTargetField.set(this, target)
    }

    private fun motionEvent(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0)
    }

    private fun Any.hitTargetForTest(x: Float, y: Float): Any? {
        val method = javaClass.getDeclaredMethod(
            "hitTarget",
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
        )
        method.isAccessible = true
        return method.invoke(this, x, y)
    }
}
