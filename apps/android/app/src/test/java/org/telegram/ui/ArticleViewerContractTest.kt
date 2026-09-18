package org.telegram.ui

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.CodeHighlighting
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.TextPaintSpan
import org.telegram.ui.Components.TextPaintUrlSpan
import org.telegram.tgnet.tl.TL_iv;

/**
 * Phase 0 contract tests for ArticleViewer public surface used by NativeDo.
 *
 * These are not full UI tests. They lock the small API surface :app and
 * topic body code depend on so ArticleViewer rebases cannot silently break:
 * - [ArticleViewer.getInstance] / [ArticleViewer.hasInstance]
 * - [ArticleViewer.DrawingText] construction
 * - [ArticleViewer.ErrorContainer] construction (login WebView error UI)
 * - [ArticleViewer.dispatchActivityResult] no-op when no file chooser owner
 *
 * Run after any ArticleViewer edit:
 * `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'org.telegram.ui.ArticleViewerContractTest'`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ArticleViewerContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        // Avoid forcing Theme class initialization: it pulls media action drawables
        // that are not on this narrow unit-test classpath.
    }
    @Test
    fun getInstance_returnsSingleton() {
        val a = ArticleViewer.getInstance()
        val b = ArticleViewer.getInstance()
        assertThat(a).isNotNull()
        assertThat(b).isSameInstanceAs(a)
        assertThat(ArticleViewer.hasInstance()).isTrue()
    }

    @Test
    fun drawingText_canBeConstructedFromInstance() {
        val viewer = ArticleViewer.getInstance()
        val viaNoArg = viewer.DrawingText()
        val viaParent = viewer.DrawingText(viewer)
        assertThat(viaNoArg).isNotNull()
        assertThat(viaParent).isNotNull()
        assertThat(viaNoArg.parentViewer).isSameInstanceAs(viewer)
        assertThat(viaParent.parentViewer).isSameInstanceAs(viewer)
        assertThat(viaNoArg.textLayout).isNull()
        assertThat(viaNoArg.parentBlock).isNull()
        // Defaults used by TableBlock / table layout bridge
        assertThat(viaNoArg.x).isEqualTo(0)
        assertThat(viaNoArg.y).isEqualTo(0)
        assertThat(viaNoArg.row).isEqualTo(0)
    }

    @Test
    fun errorContainer_constructsWithExpectedChildren() {
        val context = RuntimeEnvironment.getApplication()
        val container = ArticleViewer.ErrorContainer(context)
        assertThat(container).isNotNull()
        assertThat(container.layout).isNotNull()
        assertThat(container.buttonView).isNotNull()
        // GONE until host shows an error (LoginWebViewFragment)
        assertThat(container.visibility).isEqualTo(android.view.View.GONE)
    }

    @Test
    fun dispatchActivityResult_returnsFalseWhenNoFileChooserOwner() {
        // No active file chooser: must not crash and must not claim the result.
        val handled = ArticleViewer.dispatchActivityResult(
            /* requestCode = */ -1,
            /* resultCode = */ 0,
            Intent(),
        )
        assertThat(handled).isFalse()
    }

    @Test
    fun nonSheetConstructor_hasNullSheet() {
        val viewer = ArticleViewer()
        assertThat(viewer.isSheet).isFalse()
        assertThat(viewer.sheet).isNull()
        assertThat(viewer.pagesStack).isNotNull()
        assertThat(viewer.pagesStack).isEmpty()
    }

    @Test
    fun getResources_exposesSharedPaintMaps() {
        val viewer = ArticleViewer.getInstance()
        val res = viewer.getResources()
        assertThat(res).isNotNull()
        // B4: pin-shaped Resources holds the paint SparseArrays (static fields alias these)
        assertThat(res.paragraphTextPaints).isNotNull()
        assertThat(res.tableTextPaints).isNotNull()
        assertThat(res.quoteTextPaints).isNotNull()
        assertThat(res.photoCaptionTextPaints).isNotNull()
        assertThat(res.isRichMessage).isFalse()
    }

    @Test
    fun resources_preserveIdentityAndConstructorModes() {
        val viewer = ArticleViewer.getInstance()
        assertThat(viewer.getResources()).isSameInstanceAs(viewer.getResources())
        assertThat(ArticleViewer.Resources().isRichMessage).isFalse()
        assertThat(ArticleViewer.Resources(true).isRichMessage).isTrue()
    }

    @Test
    fun resources_instancesOwnIndependentPaintMaps() {
        val first = ArticleViewer.Resources()
        val second = ArticleViewer.Resources()

        first.paragraphTextPaints.put(7, android.text.TextPaint())

        assertThat(first.paragraphTextPaints.size()).isEqualTo(1)
        assertThat(second.paragraphTextPaints.size()).isEqualTo(0)
    }

    @Test
    fun getTextPaint_usesParentResourcesAndPinnedPreformattedFlags() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }
        val text = TL_iv.textPlain()
        val block = TL_iv.pageBlockPreformatted()

        val paint = ArticleViewer.getTextPaint(host, text, text, block)

        assertThat(isolated.preformattedTextPaints.size()).isEqualTo(1)
        assertThat(isolated.preformattedTextPaints.get(ArticleViewer.TEXT_FLAG_MONO)).isSameInstanceAs(paint)
    }

    @Test
    fun getText_parentDrivenRichTextBuildsUrlAndStyleSpans() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val plain = TL_iv.textPlain().apply { text = "hello" }
        val bold = TL_iv.textBold().apply {
            text = plain
            plain.parentRichText = this
        }
        val urlInner = TL_iv.textPlain().apply { text = "docs" }
        val url = TL_iv.textUrl().apply {
            text = urlInner
            this.url = "https://linux.do/t/1"
            urlInner.parentRichText = this
        }
        val concat = TL_iv.textConcat().apply {
            texts.add(bold)
            texts.add(url)
            bold.parentRichText = this
            url.parentRichText = this
        }
        val block = TL_iv.pageBlockParagraph()

        val built = ArticleViewer.getText(host, null as TLRPC.WebPage?, null, concat, concat, block, 320)
        assertThat(built).isInstanceOf(android.text.Spanned::class.java)
        val spanned = built as android.text.Spanned
        assertThat(spanned.toString()).isEqualTo("hellodocs")

        val paintSpans = spanned.getSpans(0, spanned.length, TextPaintSpan::class.java)
        assertThat(paintSpans).isNotEmpty()
        assertThat(isolated.paragraphTextPaints.size()).isAtLeast(1)

        val urlSpans = spanned.getSpans(0, spanned.length, TextPaintUrlSpan::class.java)
        assertThat(urlSpans).hasLength(1)
        assertThat(urlSpans[0].url).isEqualTo("https://linux.do/t/1")
        assertThat(spanned.getSpanStart(urlSpans[0])).isEqualTo(5)
        assertThat(spanned.getSpanEnd(urlSpans[0])).isEqualTo(9)

        val last = ArticleViewer.getLastRichText(bold)
        assertThat(last).isSameInstanceAs(plain)
    }

    @Test
    fun createLayoutForText_parentDrivenBuildsLayoutFromRichText() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val plain = TL_iv.textPlain().apply { text = "hello layout" }
        val block = TL_iv.pageBlockParagraph()

        val drawing = ArticleViewer.createLayoutForText(
            host,
            /* parentView = */ null,
            /* plainText = */ null,
            plain,
            /* width = */ 320,
            /* textY = */ 0,
            block,
            /* parentAdapter = */ null as ArticleViewer.WebpageAdapter?,
        )

        assertThat(drawing).isNotNull()
        assertThat(drawing!!.textLayout).isNotNull()
        assertThat(drawing.textLayout.text.toString()).isEqualTo("hello layout")
        assertThat(drawing.parentViewer).isSameInstanceAs(host)
        assertThat(drawing.parentBlock).isSameInstanceAs(block)
        assertThat(drawing.parentText).isSameInstanceAs(plain)
        assertThat(isolated.paragraphTextPaints.size()).isAtLeast(1)
        // B19: spoiler pools always allocated; empty when layout text has no TextStyleSpan spoilers.
        assertThat(drawing.spoilers).isNotNull()
        assertThat(drawing.spoilersPool).isNotNull()
        assertThat(drawing.spoilersPatchedLayout).isNotNull()
        assertThat(drawing.spoilers).isEmpty()
        assertThat(drawing.isAttached).isFalse()
    }

    @Test
    fun createLayoutForText_spoilerSpanPopulatesEffectsAndAttachLifecycle() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val plain = TL_iv.textPlain().apply { text = "hidden spoiler" }
        val block = TL_iv.pageBlockParagraph()
        // SpoilerEffect.addSpoilers scans TextStyleSpan with FLAG_STYLE_SPOILER on the laid-out text.
        // createLayoutForText builds from rich text only; inject via plainText Spanned path.
        val spoilerRun = org.telegram.ui.Components.TextStyleSpan.TextStyleRun().apply {
            flags = flags or org.telegram.ui.Components.TextStyleSpan.FLAG_STYLE_SPOILER
        }
        val spanned = android.text.SpannableStringBuilder("hidden spoiler").apply {
            setSpan(
                org.telegram.ui.Components.TextStyleSpan(spoilerRun),
                0,
                length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val drawing = ArticleViewer.createLayoutForText(
            host,
            /* parentView = */ null,
            spanned,
            /* richText = */ null,
            /* width = */ 320,
            /* textY = */ 0,
            block,
            /* parentAdapter = */ null as ArticleViewer.WebpageAdapter?,
        )

        assertThat(drawing).isNotNull()
        assertThat(drawing!!.spoilers).isNotNull()
        assertThat(drawing.spoilersPool).isNotNull()
        // SpoilerEffect may or may not allocate particles under Robolectric depending on metrics;
        // fields and draw path must remain non-null and attach/detach must be safe.
        drawing.attach(android.view.View(org.robolectric.RuntimeEnvironment.getApplication()))
        assertThat(drawing.isAttached).isTrue()
        drawing.detach(null)
        assertThat(drawing.isAttached).isFalse()
    }

    @Test
    fun getText_textSpoilerProducesTextStyleSpoilerSpan() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val plain = TL_iv.textPlain().apply { text = "secret" }
        val spoiler = TL_iv.textSpoiler().apply {
            text = plain
            plain.parentRichText = this
        }
        val block = TL_iv.pageBlockParagraph()

        val built = ArticleViewer.getText(host, null as TLRPC.WebPage?, null, spoiler, spoiler, block, 320)
        assertThat(built).isInstanceOf(android.text.Spanned::class.java)
        val spanned = built as android.text.Spanned
        assertThat(spanned.toString()).isEqualTo("secret")

        val styleSpans = spanned.getSpans(0, spanned.length, org.telegram.ui.Components.TextStyleSpan::class.java)
        assertThat(styleSpans).hasLength(1)
        assertThat(styleSpans[0].isSpoiler).isTrue()
        assertThat(spanned.getSpanStart(styleSpans[0])).isEqualTo(0)
        assertThat(spanned.getSpanEnd(styleSpans[0])).isEqualTo(6)

        val last = ArticleViewer.getLastRichText(spoiler)
        assertThat(last).isSameInstanceAs(plain)
        assertThat(ArticleViewer.getPlainText(spoiler).toString()).isEqualTo("secret")
    }

    @Test
    fun createLayoutForText_textSpoilerRichTextPathBuildsSpoilerSpans() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val plain = TL_iv.textPlain().apply { text = "hidden via rich text" }
        val spoiler = TL_iv.textSpoiler().apply {
            text = plain
            plain.parentRichText = this
        }
        val block = TL_iv.pageBlockParagraph()

        val drawing = ArticleViewer.createLayoutForText(
            host,
            /* parentView = */ null,
            /* plainText = */ null,
            spoiler,
            /* width = */ 320,
            /* textY = */ 0,
            block,
            /* parentAdapter = */ null as ArticleViewer.WebpageAdapter?,
        )

        assertThat(drawing).isNotNull()
        assertThat(drawing!!.textLayout).isNotNull()
        assertThat(drawing.textLayout.text.toString()).isEqualTo("hidden via rich text")
        assertThat(drawing.parentText).isSameInstanceAs(spoiler)
        assertThat(drawing.spoilers).isNotNull()
        assertThat(drawing.spoilersPool).isNotNull()
        assertThat(drawing.spoilersPatchedLayout).isNotNull()

        val laidOut = drawing.textLayout.text
        assertThat(laidOut).isInstanceOf(android.text.Spanned::class.java)
        val styleSpans = (laidOut as android.text.Spanned)
            .getSpans(0, laidOut.length, org.telegram.ui.Components.TextStyleSpan::class.java)
        assertThat(styleSpans).isNotEmpty()
        assertThat(styleSpans.any { it.isSpoiler }).isTrue()
    }

    @Test
    fun getTextPaint_headingBlocksUseDistinctMapsAndSizes() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }
        val plain = TL_iv.textPlain().apply { text = "H" }
        val h1 = TL_iv.pageBlockHeading1().apply { text = plain }
        val h3 = TL_iv.pageBlockHeading3().apply { text = plain }
        val h6 = TL_iv.pageBlockHeading6().apply { text = plain }
        assertThat(ArticleViewer.isHeadingBlock(h1)).isTrue()
        assertThat(ArticleViewer.isHeadingBlock(TL_iv.pageBlockHeader())).isFalse()

        val paint1 = ArticleViewer.getTextPaint(host, plain, plain, h1)
        val paint3 = ArticleViewer.getTextPaint(host, plain, plain, h3)
        val paint6 = ArticleViewer.getTextPaint(host, plain, plain, h6)
        // pin sizes: h1=18dp, h3=15dp, h6=12dp — distinct maps so sizes differ
        assertThat(paint1.textSize).isGreaterThan(paint3.textSize)
        assertThat(paint3.textSize).isGreaterThan(paint6.textSize)
        assertThat(isolated.heading1TextPaints.size()).isAtLeast(1)
        assertThat(isolated.heading6TextPaints.size()).isAtLeast(1)
    }

    @Test
    fun codeHighlighting_getHighlightedWithoutLanguageReturnsPlainSpannable() {
        val highlighted = CodeHighlighting.getHighlighted("int x = 1;", /* language = */ null)
        assertThat(highlighted.toString()).isEqualTo("int x = 1;")
        // Empty language is a no-op highlight path (pin); still returns SpannableString.
        assertThat(highlighted).isInstanceOf(android.text.SpannableString::class.java)
    }

    @Test
    fun codeHighlighting_getHighlightedWithLanguageReturnsLockedSpannable() {
        // Robolectric Application assets may or may not include codelng.gzip depending on
        // module merge; the API must still return a spannable without throwing.
        val highlighted = CodeHighlighting.getHighlighted("fun main() {}", "kotlin")
        assertThat(highlighted.toString()).isEqualTo("fun main() {}")
        assertThat(highlighted).isInstanceOf(android.text.Spannable::class.java)
    }

    @Test
    fun articleViewer_canStartSelectionWhenTouchesAllowed() {
        val viewer = ArticleViewer.getInstance()
        // Default product path: no page-switch animation → touches allowed → selection enabled.
        assertThat(viewer.allowTouches()).isTrue()
        assertThat(viewer.canStartSelection(/* block = */ null)).isTrue()
    }

    @Test
    fun drawQuoteLines_quoteLevelsMaskDrawsWithoutThrowing() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }
        // Ensure paint exists; createPaint initializes quoteLinePaint among others.
        ArticleViewer.createPaint(host, true)
        val block = TL_iv.pageBlockParagraph().apply {
            level = 0
            // bits 0 and 2 => two gutters at padx and padx+28
            quoteLevels = 0b101
        }
        val canvas = android.graphics.Canvas()
        // Must not throw; multi-layer path is the A6 pin algorithm (legacy level path still works when mask==0).
        ArticleViewer.drawQuoteLines(canvas, host, block, /* height = */ 100)
        block.quoteLevels = 0
        block.level = 2
        ArticleViewer.drawQuoteLines(canvas, host, block, 100)
    }

    @Test
    fun createLayoutForText_plainTextUsesParentColoredSpecialPaints() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xffabcdef.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
        }

        val block = TL_iv.pageBlockEmbedPost().apply { author = "alice" }
        val drawing = ArticleViewer.createLayoutForText(
            host,
            null,
            block.author,
            /* richText = */ null,
            200,
            0,
            block,
            null as ArticleViewer.WebpageAdapter?,
        )

        assertThat(drawing).isNotNull()
        assertThat(drawing!!.textLayout.text.toString()).isEqualTo("alice")
        assertThat(drawing.parentViewer).isSameInstanceAs(host)
    }

    @Test
    fun drawQuoteLines_isNullSafeAndAcceptsParentDrivenLevelGutter() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xff445566.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
        }
        ArticleViewer.createPaint(host, true)

        val nested = TL_iv.pageBlockParagraph().apply {
            level = 2
            bottom = true
        }
        val canvas = android.graphics.Canvas()
        // Parent-driven single-level gutter path (local S1 has no quoteLevels mask).
        ArticleViewer.drawQuoteLines(canvas, host, nested, /* height = */ 120)
        // Null-safe no-ops for incomplete hosts/blocks.
        ArticleViewer.drawQuoteLines(canvas, host, null, 120)
        ArticleViewer.drawQuoteLines(canvas, null, nested, 120)
        ArticleViewer.drawQuoteLines(canvas, host, TL_iv.pageBlockParagraph().apply { level = 0 }, 120)
    }

    @Test
    fun blockParagraphCell_parentDrivenConstructsAndLayoutsText() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockParagraphCell(context, host, null)
        val plain = TL_iv.textPlain().apply { text = "paragraph pilot" }
        cell.setBlock(TL_iv.pageBlockParagraph().apply {
            text = plain
            level = 0
        })

        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.textLayout).isNotNull()
        assertThat(cell.textLayout.text.toString()).isEqualTo("paragraph pilot")
        assertThat(cell.textX).isEqualTo(org.telegram.messenger.AndroidUtilities.dp(18f))
        assertThat(cell.textY).isEqualTo(org.telegram.messenger.AndroidUtilities.dp(8f))
        assertThat(cell.measuredHeight).isGreaterThan(0)
        assertThat(isolated.paragraphTextPaints.size()).isAtLeast(1)
    }

    @Test
    fun blockHeaderCell_parentDrivenConstructsAndLayoutsText() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockHeaderCell(context, host, null)
        cell.setBlock(TL_iv.pageBlockHeader().apply {
            text = TL_iv.textPlain().apply { text = "header pilot" }
        })

        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.measuredHeight).isGreaterThan(0)
        assertThat(isolated.headerTextPaints.size()).isAtLeast(1)
    }

    @Test
    fun blockPullquoteCell_parentDrivenConstructsAndLayoutsText() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockPullquoteCell(context, host, null)
        cell.setBlock(TL_iv.pageBlockPullquote().apply {
            text = TL_iv.textPlain().apply { text = "quote body" }
            caption = TL_iv.textPlain().apply { text = "— source" }
        })

        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.measuredHeight).isGreaterThan(0)
        assertThat(isolated.quoteTextPaints.size()).isAtLeast(1)
    }

    @Test
    fun blockDetailsCell_parentDrivenConstructsAndLayoutsTitle() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockDetailsCell(context, host, null)
        cell.setBlock(TL_iv.pageBlockDetails().apply {
            title = TL_iv.textPlain().apply { text = "details title" }
            open = true
        })

        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.measuredHeight).isGreaterThan(0)
        assertThat(isolated.detailsTextPaints.size()).isAtLeast(1)
    }

    @Test
    fun blockListItemCell_parentDrivenConstructsWithEmptyMeasure() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val listCell = ArticleViewer.BlockListItemCell(context, host, null)
        val orderedCell = ArticleViewer.BlockOrderedListItemCell(context, host, null)

        listCell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        orderedCell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        // Null block path: height collapses to the local 1px sentinel.
        assertThat(listCell.measuredHeight).isEqualTo(1)
        assertThat(orderedCell.measuredHeight).isEqualTo(1)
    }

    @Test
    fun blockTableCell_parentDrivenConstructsAndMeasuresEmptyBlock() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockTableCell(context, host, null)
        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        // Null block path: height collapses to the local 1px sentinel.
        assertThat(cell.measuredHeight).isEqualTo(1)
    }

    @Test
    fun blockPhotoAndChannelCells_parentDrivenConstruct() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val photo = ArticleViewer.BlockPhotoCell(context, host, null, 0)
        val video = ArticleViewer.BlockVideoCell(context, host, null, 0)
        val audio = ArticleViewer.BlockAudioCell(context, host, null)
        val map = ArticleViewer.BlockMapCell(context, host, null, 0)
        val channel = ArticleViewer.BlockChannelCell(context, host, null, 0)

        for (cell in listOf(photo, video, audio, map, channel)) {
            cell.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
            assertThat(cell.measuredHeight).isAtLeast(1)
        }
    }

    @Test
    fun blockEmbedPostCell_parentDrivenConstructsAndMeasuresEmptyBlock() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockEmbedPostCell(context, host, null)
        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        assertThat(cell.measuredHeight).isEqualTo(1)
    }

    @Test
    fun drawingText_boundsAndAccessibilityHelpers_parentDriven() {
        val isolated = ArticleViewer.Resources()
        val host = object : IArticleViewer() {
            override fun getCurrentAccount() = 0
            override fun getTextColor() = 0xff111111.toInt()
            override fun getLinkTextColor() = 0xff222222.toInt()
            override fun getGrayTextColor() = 0xff333333.toInt()
            override fun getThemedColor(colorKey: Int) = 0xffffffff.toInt()
            override fun getResourcesProvider(): Theme.ResourcesProvider? = null
            override fun getResources() = isolated
            override fun getTextSelectionHelper(view: android.view.View?): TextSelectionHelper.ArticleTextSelectionHelper? = null
            override fun padx() = 18
            override fun pady() = 8
        }
        ArticleViewer.createPaint(host, true)

        val plain = TL_iv.textPlain().apply { text = "bounds text" }
        val block = TL_iv.pageBlockParagraph()
        val drawing = ArticleViewer.createLayoutForText(
            host,
            null,
            null,
            plain,
            320,
            0,
            block,
            null as ArticleViewer.WebpageAdapter?,
        )
        assertThat(drawing).isNotNull()
        assertThat(drawing!!.boundLeft).isAtLeast(0)
        assertThat(drawing.boundRight).isAtLeast(drawing.boundLeft)
        assertThat(drawing.lastLineBoundRight).isAtLeast(0)

        val a11y = ArticleViewer.buildAccessibilityText(host, null, drawing)
        assertThat(a11y.toString()).isEqualTo("bounds text")

        val labeled = ArticleViewer.appendA11yLabel(a11y, org.telegram.messenger.R.string.AccDescrIVHeading)
        assertThat(labeled.toString()).contains("bounds text")
        assertThat(labeled.toString()).contains(",")

        val context = RuntimeEnvironment.getApplication()
        val cell = ArticleViewer.BlockParagraphCell(context, host, null)
        assertThat(cell).isInstanceOf(ArticleViewer.IBlock::class.java)
        val iblock = cell as ArticleViewer.IBlock
        assertThat(iblock.boundLeft).isEqualTo(-1)
        assertThat(iblock.boundRight).isEqualTo(-1)
        assertThat(iblock.lastLineBoundRight).isEqualTo(-1)

        val authorDate = ArticleViewer.BlockAuthorDateCell(context, host, null)
        assertThat(authorDate).isInstanceOf(ArticleViewer.IBlock::class.java)
        val pullquote = ArticleViewer.BlockPullquoteCell(context, host, null)
        assertThat(pullquote).isInstanceOf(ArticleViewer.IBlock::class.java)
        val preformatted = ArticleViewer.BlockPreformattedCell(context, host, null)
        assertThat(preformatted).isInstanceOf(ArticleViewer.IBlock::class.java)
    }
}
