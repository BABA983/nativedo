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
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.TextPaintSpan
import org.telegram.ui.Components.TextPaintUrlSpan

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
        // Avoid Theme.init(): pulls media action drawables not on the unit-test classpath.
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
        val text = TLRPC.TL_textPlain()
        val block = TLRPC.TL_pageBlockPreformatted()

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

        val plain = TLRPC.TL_textPlain().apply { text = "hello" }
        val bold = TLRPC.TL_textBold().apply {
            text = plain
            plain.parentRichText = this
        }
        val urlInner = TLRPC.TL_textPlain().apply { text = "docs" }
        val url = TLRPC.TL_textUrl().apply {
            text = urlInner
            this.url = "https://linux.do/t/1"
            urlInner.parentRichText = this
        }
        val concat = TLRPC.TL_textConcat().apply {
            texts.add(bold)
            texts.add(url)
            bold.parentRichText = this
            url.parentRichText = this
        }
        val block = TLRPC.TL_pageBlockParagraph()

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

        val plain = TLRPC.TL_textPlain().apply { text = "hello layout" }
        val block = TLRPC.TL_pageBlockParagraph()

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

        val block = TLRPC.TL_pageBlockEmbedPost().apply { author = "alice" }
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

        val nested = TLRPC.TL_pageBlockParagraph().apply {
            level = 2
            bottom = true
        }
        val canvas = android.graphics.Canvas()
        // Parent-driven single-level gutter path (local S1 has no quoteLevels mask).
        ArticleViewer.drawQuoteLines(canvas, host, nested, /* height = */ 120)
        // Null-safe no-ops for incomplete hosts/blocks.
        ArticleViewer.drawQuoteLines(canvas, host, null, 120)
        ArticleViewer.drawQuoteLines(canvas, null, nested, 120)
        ArticleViewer.drawQuoteLines(canvas, host, TLRPC.TL_pageBlockParagraph().apply { level = 0 }, 120)
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
        val plain = TLRPC.TL_textPlain().apply { text = "paragraph pilot" }
        cell.setBlock(TLRPC.TL_pageBlockParagraph().apply {
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
        cell.setBlock(TLRPC.TL_pageBlockHeader().apply {
            text = TLRPC.TL_textPlain().apply { text = "header pilot" }
        })

        cell.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.measuredHeight).isGreaterThan(0)
        assertThat(isolated.headerTextPaints.size()).isAtLeast(1)
    }
}
