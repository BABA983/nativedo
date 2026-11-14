package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.telegram.messenger.RichMessageLayout
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Inline Discourse emoji in the real topic flow: a cooked `img.emoji` (standard twemoji and a
 * custom site upload), and a raw `:shortcode:` the cook left as text.
 *
 * <p>The failure modes this pins, all through `FakeDiscourseSite` -> `TopicFragment`:</p>
 * <ul>
 *   <li>a <em>cold</em> body emoji - one whose picture is not on disk yet - claims its Telegram
 *       emoji-span box at the first layout, so it reflows nothing when the artwork later arrives:
 *       the rendered line breaks and the bubble's height are the same before and after the fetch,
 *       and the label it covers stays available for copy and accessibility;</li>
 *   <li>only the artwork changes at HTTP completion - the same span, on the same visible row, draws
 *       the site's picture (its own colour) in place of the placeholder, with the reader's place,
 *       the floor and any expanded details untouched, and no full-floor reconstruction;</li>
 *   <li>a fetch the site refuses or answers with something undecodable keeps the same stable,
 *       visible placeholder - it never tightens back into a literal shortcode or blanks the run;</li>
 *   <li>a source this client cannot fetch at all (no `src`, or one that is not `http(s)`) stays the
 *       literal label it came with;</li>
 *   <li>a raw shortcode is an emoji only when the loaded catalog names it, and never inside
 *       code/pre, and it too claims its span at once rather than reflowing;</li>
 *   <li>two distinct emoji must never share a picture, and an emoji must draw inside the body text
 *       line, tracking the body font.</li>
 * </ul>
 *
 * <p>Every run also asserts the stock code reached no Telegram path: no tgnet initialisation, no
 * Telegram database, no tgnet native call.</p>
 */
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
internal class DiscourseEmojiRenderTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @After
    fun tearDown() {
        runCatching { if (Theme.isCurrentThemeDark()) switchTo(dark = false) }
        NativeDo.setContainerFactory { AppContainer(it) }
        site.stop()
    }

    /**
     * A standard cooked emoji and a custom site emoji on one line, between Chinese, bold and anchor
     * runs and English. Once their pictures land, both are `textCustomEmoji` spans with decoded
     * bitmaps; the labels survive as the covered text, and the two never share a document.
     */
    @Test
    fun cookedStandardAndCustomEmojiRenderAsArtworkBesideTextRuns() {
        val standard = "emoji/${runToken()}-laugh.png"
        val custom = "emoji/${runToken()}-wave.png"
        openWith({ s ->
            s.serveImage(standard, solidPng(Color.rgb(255, 204, 0), 40))
            s.serveImage(custom, solidPng(Color.rgb(0, 160, 220), 40))
            "<p>中文 <b>粗体</b> <img class='emoji only-emoji' alt=':laugh:' src='${s.url(standard)}' " +
                "width='20' height='20'> <a href='/x'>链接</a> English " +
                "<img class='emoji' alt=':custom_wave:' src='${s.url(custom)}' width='20' height='20'> end</p>"
        })

        harness.await("both emoji to render as artwork") {
            harness.drawApp()
            emojiHolders().count { it.drawable?.imageReceiver?.bitmap != null } == 2
        }
        renderTo("emoji-cooked-light")
        switchTo(dark = true)
        rebindPosts()
        renderTo("emoji-cooked-dark")
        switchTo(dark = false)
        rebindPosts()
        harness.await("both emoji after switching back to light") {
            harness.drawApp()
            emojiHolders().count { it.drawable?.imageReceiver?.bitmap != null } == 2
        }

        val documentIds = emojiHolders().mapNotNull { it.span?.documentId }
        assertEquals("each emoji is its own document", 2, documentIds.distinct().size)
        assertTrue("an emoji document id must be a real id", documentIds.all { it != 0L })
        val files = documentIds.map { id -> emojiFile(id) }
        assertEquals("two emoji must not share a picture", 2, files.distinct().size)
        assertTrue("both pictures must be on disk", files.all { it.exists() && it.length() > 0 })

        val joined = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("body text before the emoji was dropped: $joined", joined.contains("中文 粗体"))
        assertTrue("the emoji's readable label must stay the covered text: $joined", joined.contains(":laugh:"))
        assertTrue("a custom emoji's label must survive too: $joined", joined.contains(":custom_wave:"))
        assertTrue("text after the emoji was dropped: $joined", joined.contains("链接 English") && joined.endsWith("end"))
        val expectedColors = listOf(Color.rgb(255, 204, 0), Color.rgb(0, 160, 220))
        assertEquals("each span must decode its own artwork, not just a non-null bitmap",
            expectedColors, emojiHolders().map { holder ->
                val bitmap = holder.drawable!!.imageReceiver.bitmap!!
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            })
        val fetches = site.requestPaths.count { it.contains("-laugh.png") || it.contains("-wave.png") }
        harness.topFragment()!!.finishFragment()
        harness.idleClock(600)
        harness.present(TopicFragment.forTopic(TOPIC, "Emoji render"))
        harness.await("cached emoji to render after reopening") {
            harness.drawApp()
            cells().isNotEmpty() &&
                emojiHolders().count { it.drawable?.imageReceiver?.bitmap != null } == 2
        }
        assertEquals("reopening must not redownload cached artwork", fetches,
            site.requestPaths.count { it.contains("-laugh.png") || it.contains("-wave.png") })
        assertNoSideEffects()
    }

    /**
     * The accepted change: a cold body emoji claims its Telegram emoji-span box at the first layout
     * - a visible placeholder the size of the artwork that will replace it - and only the artwork
     * arrives later. The failure this pins is the old label-first path: a `:shortcode:` that
     * occupies text width until the fetch lands, then turns into a span, re-wrapping the paragraph
     * and rebuilding the floor. The oracle is the rendered line breaks and bubble geometry taken
     * while two distinct custom emoji are still cold and again after their pictures land: they must
     * be identical, so nothing re-wrapped, and each placeholder box must already be the emoji-span
     * square, not the label's text width. Chinese body text sits on the wrap boundary, so a
     * text-width placeholder would move a line. The reader's expanded details, the visible row, the
     * adapter and the pixel anchor must all survive, and nothing may reach tgnet.
     */
    @Test
    fun coldBodyEmojiClaimsItsSpanThenOnlyArtworkArrives() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = "emoji/${runToken()}-slow.png"
        val quick = "emoji/${runToken()}-quick.png"
        val slowColor = Color.rgb(220, 40, 120)
        val quickColor = Color.rgb(0, 170, 160)
        val bytes = solidPng(slowColor, 40)
        try {
            openWith({ s ->
                // The first emoji's picture is held mid-transfer; the second is served normally, so
                // the loader reaches it only once the first completes.
                s.serveResponse(slow) {
                    entered.countDown()
                    check(release.await(60, TimeUnit.SECONDS))
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                }
                s.serveImage(quick, solidPng(quickColor, 40))
                // Chinese original text sits on the wrap boundary: a placeholder that measured the
                // `:shortcode:` label would wrap this paragraph differently to the artwork's square.
                "<p>你说这事整的，那去朝鲜领不就行了呗，国服朝鲜，最便宜的国家奥 <img class='emoji' alt=':slow:' " +
                    "src='${s.url(slow)}' width='20' height='20'> 中文继续 <img class='emoji' alt=':quick:' " +
                    "src='${s.url(quick)}' width='20' height='20'> 结尾文字</p>" +
                    "<details><summary>More</summary><p>Expanded while emoji waits</p></details>"
            }, count = 20)
            harness.await("the emoji request to be in flight") { entered.count == 0L }

            val list = list()
            val adapter = list.adapter
            val manager = list.layoutManager as LinearLayoutManager
            manager.scrollToPositionWithOffset(8, -37)
            harness.layoutApp()
            harness.idleClock(100)
            assertEquals("test must start away from the first floor", 8, manager.findFirstVisibleItemPosition())
            assertTrue("nothing may navigate while the fetch waits", harness.topFragment() is TopicFragment)

            // Cold: both emoji are already emoji spans that reserve the emoji-span square, but no
            // artwork has arrived. The label stays the covered text, so copy/accessibility keep it.
            harness.drawApp()
            val cold = emojiHolders()
            assertEquals("a cold emoji must already be an emoji span, not a literal label", 2,
                cold.count { (it.span?.documentId ?: 0L) != 0L })
            assertTrue("no artwork may exist before a picture is on disk",
                cold.none { it.drawable?.imageReceiver?.bitmap != null })
            val coldBoxes = emojiBoxesByDocument()
            assertEquals("each cold emoji must reserve its own emoji-span box", 2, coldBoxes.size)
            assertTrue("the placeholder must occupy a real emoji-span square",
                coldBoxes.values.all { it.first >= 2 && it.second >= 2 })
            val line = bodyLineHeight()
            assertTrue("the cold placeholder must reserve an emoji-span square the size of its line, " +
                "not a text-width shortcode sliver: boxes=$coldBoxes line=$line",
                coldBoxes.values.all { it.first >= line * 0.5f && it.second >= line * 0.5f })
            assertEquals("each placeholder must cover its own label for copy/accessibility",
                setOf(":slow:", ":quick:"), cold.map { emojiCoveredText(it) }.toSet())
            assertTrue("the cold emoji must read as its label in the joined text",
                cell().messageObject.richLayout.joinedText.toString().contains(":slow:") &&
                    cell().messageObject.richLayout.joinedText.toString().contains(":quick:"))
            renderTo("emoji-cold-light")
            switchTo(dark = true)
            rebindPosts()
            renderTo("emoji-cold-dark")
            switchTo(dark = false)
            rebindPosts()
            harness.drawApp()

            // Expand details while the fetch is still blocked; the artwork upgrade must keep it.
            val before = cell()
            val header = before.messageObject.richLayout.blocks
                .filterIsInstance<RichMessageLayout.RichDetailsBlock>().first()
            val x = before.textX + header.padding.left + 80f
            val y = before.textY + header.currY + header.height / 2f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
                assertTrue("the production cell must handle the details tap", before.onTouchEvent(event))
                event.recycle()
            }
            harness.idleClock(600)
            harness.drawApp()
            assertTrue("the details must actually expand before the fetch ends",
                before.messageObject.richLayout.joinedText.contains("Expanded while emoji waits"))

            val top = before.top
            val floorId = before.messageObject.id
            val coldGeometry = bodyGeometry()
            renderTo("emoji-cold-expanded-light")

            release.countDown()
            harness.await("both blocked emoji to become artwork") {
                harness.drawApp()
                emojiHolders().count { it.drawable?.imageReceiver?.bitmap != null } == 2
            }

            // Only the drawing changed: same line breaks, same bubble, same span boxes, same row.
            assertEquals("the artwork upgrade must not re-wrap the body", coldGeometry, bodyGeometry())
            assertEquals("the emoji-span box must not move when artwork replaces the placeholder",
                coldBoxes, emojiBoxesByDocument())
            assertEquals("each span must decode its own artwork",
                setOf(slowColor, quickColor), emojiHolders().mapNotNull { holder ->
                    holder.drawable?.imageReceiver?.bitmap?.let { it.getPixel(it.width / 2, it.height / 2) }
                }.toSet())
            assertEquals("each loaded emoji must keep its label as covered text for copy/accessibility",
                setOf(":slow:", ":quick:"), emojiHolders().map { emojiCoveredText(it) }.toSet())
            assertSame("the adapter must not be replaced by the upgrade", adapter, list.adapter)
            assertSame("the visible row must be kept, not removed and re-created", before, cell())
            assertTrue("the upgrade must not navigate", harness.topFragment() is TopicFragment)
            assertEquals("emoji completion must retain the visible floor", floorId, cell().messageObject.id)
            assertEquals("the floor's pixel anchor must not move", top, cell().top)
            assertTrue("emoji completion must not close the reader's expanded details",
                cell().messageObject.richLayout.joinedText.contains("Expanded while emoji waits"))
            harness.await("the loaded artwork crossfade to finish") {
                harness.drawApp()
                emojiHolders().all { it.drawable?.imageReceiver?.currentAlpha == 1f }
            }
            renderTo("emoji-loaded-light")
            switchTo(dark = true)
            rebindPosts()
            harness.await("the artwork to remain visible in dark mode") {
                harness.drawApp()
                emojiHolders().size == 2 && emojiHolders().all {
                    it.drawable?.imageReceiver?.let { receiver -> receiver.bitmap != null && receiver.currentAlpha == 1f } == true
                }
            }
            renderTo("emoji-loaded-dark")
            switchTo(dark = false)
            rebindPosts()
            harness.drawApp()

            // Warm reopen: the cached artwork is drawn without any new download.
            val fetches = site.requestPaths.count { it.contains("-slow.png") || it.contains("-quick.png") }
            harness.topFragment()!!.finishFragment()
            harness.idleClock(600)
            harness.present(TopicFragment.forTopic(TOPIC, "Emoji render"))
            harness.await("cached emoji to render after reopening") {
                harness.drawApp()
                cells().isNotEmpty() &&
                    emojiHolders().count { it.drawable?.imageReceiver?.bitmap != null } == 2
            }
            assertEquals("reopening must not redownload cached artwork", fetches,
                site.requestPaths.count { it.contains("-slow.png") || it.contains("-quick.png") })
            assertNoSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * A picture the site refuses (404) or answers with something undecodable (a 200 that is not an
     * image) is still a recognized, valid source: the emoji keeps the same stable, visible
     * placeholder its cold sibling had instead of tightening back into a literal `:shortcode:` or
     * blanking the run. The sources this client cannot even try - no `src`, or a scheme that is not
     * `http(s)` - stay literal. A warm reopen keeps the same stable placeholder.
     */
    @Test
    fun anEmojiTheSiteWillNotHandOverOrServesGarbageKeepsItsStablePlaceholder() {
        val absent = "emoji/" + runToken() + "-absent.png"
        val garbage = "emoji/" + runToken() + "-garbage.png"
        openWith({ s ->
            // One path is never served (404); the other answers 200 with bytes that are not a picture.
            s.serveResponse(garbage) {
                MockResponse().setHeader("Content-Type", "image/png").setBody("this is not a png")
            }
            "<p>保留 <img class='emoji' alt=':missing:' src='${s.url(absent)}' width='20' height='20'> " +
                "和 <img class='emoji' alt=':bad:' src='${s.url(garbage)}' width='20' height='20'> 文字 " +
                "<img class='emoji' alt=':no_source:'> " +
                "<img class='emoji' title=':bad_source:' src='javascript:alert(1)'></p>"
        })
        harness.await("both fetches to finish") {
            harness.drawApp()
            site.requestPaths.any { it.contains("-absent.png") } &&
                site.requestPaths.any { it.contains("-garbage.png") }
        }
        harness.idleClock(300)
        harness.drawApp()

        // Both valid sources stay emoji spans in their placeholder state.
        val holders = emojiHolders()
        assertEquals("a refused and a corrupt picture must each keep an emoji span", 2,
            holders.count { (it.span?.documentId ?: 0L) != 0L })
        assertTrue("no artwork may be drawn for a refused or corrupt picture",
            holders.none { it.drawable?.imageReceiver?.bitmap != null })
        assertTrue("the failed emoji must still occupy the emoji-span square",
            emojiBoxesByDocument().values.all { it.first >= 2 && it.second >= 2 })
        val joined = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("a refused picture must not blank the run: $joined", joined.contains("保留 :missing:"))
        assertTrue("a garbage picture must not blank the run: $joined", joined.contains(":bad: 文字"))
        assertTrue("missing src must keep the label literal: $joined", joined.contains(":no_source:"))
        assertTrue("unsupported src must keep the title literal: $joined", joined.contains(":bad_source:"))
        assertEquals("a source-less or unsupported emoji must not become a span", setOf(":missing:", ":bad:"),
            holders.map { emojiCoveredText(it) }.toSet())
        val geometry = bodyGeometry()
        renderTo("emoji-failure-light")
        switchTo(dark = true)
        rebindPosts()
        harness.drawApp()
        renderTo("emoji-failure-dark")
        switchTo(dark = false)
        rebindPosts()
        harness.drawApp()

        // Warm reopen: the failure must not turn into a reflow on the next open.
        harness.topFragment()!!.finishFragment()
        harness.idleClock(600)
        harness.present(TopicFragment.forTopic(TOPIC, "Emoji render"))
        harness.await("the failed emoji to come back as placeholder spans") {
            harness.drawApp()
            cells().isNotEmpty() && emojiHolders().count { (it.span?.documentId ?: 0L) != 0L } == 2
        }
        assertTrue("a failed fetch must not start drawing artwork on reopen",
            emojiHolders().none { it.drawable?.imageReceiver?.bitmap != null })
        assertEquals("a failed fetch must reopen with the same stable geometry", geometry, bodyGeometry())
        assertTrue("the labels must survive the reopen",
            cell().messageObject.richLayout.joinedText.contains(":missing:") &&
                cell().messageObject.richLayout.joinedText.contains(":bad:"))
        assertNoSideEffects()
    }

    /**
     * A raw shortcode the cook left as text: only a shortcode the loaded catalog names becomes an
     * emoji - and it claims its span at once, so the run never reflows - while an unknown literal,
     * and any text inside a `<code>` block, stays exactly as written.
     */
    @Test
    fun aRawShortcodeBecomesASpanOnlyWhenTheLoadedCatalogNamesIt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val wave = "emoji/${runToken()}-wave.png"
        val bytes = solidPng(Color.rgb(80, 200, 120), 40)
        try {
            openWith({ s ->
                // The catalog-named shortcode's picture is held mid-transfer, so the cold state is
                // observable: a `:shortcode:` that measured as text first would reflow this run.
                s.serveResponse(wave) {
                    entered.countDown()
                    check(release.await(60, TimeUnit.SECONDS))
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                }
                // The catalog the topic read carries: only `site_wave` is a known shortcode.
                s.serveJson("emojis.json", """{"custom":[{"name":"site_wave","url":"${s.url(wave)}"}]}""")
                "<p>hi :site_wave: and :not_a_thing: and <code>:site_wave:</code></p>"
            })
            harness.await("the shortcode's request to be in flight") { entered.count == 0L }

            // The catalog-named shortcode claims its span at once; nothing else is a span, and no
            // artwork exists yet.
            harness.drawApp()
            assertEquals("the catalog-named shortcode must claim its span at once", 1,
                emojiHolders().count { (it.span?.documentId ?: 0L) != 0L })
            assertEquals("the known shortcode's span must cover its own label", setOf(":site_wave:"),
                emojiHolders().filter { (it.span?.documentId ?: 0L) != 0L }.map { emojiCoveredText(it) }.toSet())
            assertTrue("no artwork may exist before the picture is on disk",
                emojiHolders().none { it.drawable?.imageReceiver?.bitmap != null })

            release.countDown()
            harness.await("the known shortcode to render as artwork") {
                harness.drawApp()
                emojiHolders().any { it.drawable?.imageReceiver?.bitmap != null }
            }
            val joined = cell().messageObject.richLayout.joinedText.toString()
            assertEquals("only the catalog's shortcode is an emoji", 1, emojiHolders().count { it.span?.documentId != null })
            assertTrue("a known shortcode must resolve: $joined", joined.contains("hi :site_wave:"))
            assertTrue("an unknown shortcode must stay literal: $joined", joined.contains(":not_a_thing:"))
            assertTrue("a shortcode inside code must stay literal: $joined", joined.contains(":site_wave:"))
            renderTo("emoji-raw-shortcode", "rich-message-layout/emoji")
            assertNoSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * The requested size change: an inline emoji must draw inside the body text's line, not at
     * upstream's `dp(fontSize + 8)` drawn with scale 1.2 (about 1.8x the text), which bulges out of
     * the line box. The oracle is the artwork's real drawn square, measured off the rendered cell,
     * against the body text paint's own line height - not a dp constant copied from the
     * implementation - so a regression that restores the oversized span fails here.
     */
    @Test
    fun inlineEmojiDrawsWithinTheBodyTextLine() {
        val laugh = "emoji/${runToken()}-laugh.png"
        openWith({ s ->
            s.serveImage(laugh, solidPng(Color.rgb(255, 204, 0), 40))
            "<p>中文 <b>粗体</b> <img class='emoji' alt=':laugh:' src='${s.url(laugh)}' " +
                "width='20' height='20'> <a href='/x'>链接</a> English</p>"
        })
        harness.await("the emoji artwork") {
            harness.drawApp()
            emojiHolders().any { it.drawable?.imageReceiver?.bitmap != null }
        }
        harness.layoutApp()
        harness.drawApp()
        harness.drawApp()
        renderTo("render-fixes-emoji-light", "nativedo")
        switchTo(dark = true)
        rebindPosts()
        harness.drawApp()
        harness.drawApp()
        renderTo("render-fixes-emoji-night", "nativedo")
        switchTo(dark = false)
        rebindPosts()
        harness.drawApp()
        harness.drawApp()

        val (drawn, line) = emojiSideAndTextLine()
        assertTrue("the artwork must have been drawn: drawn=$drawn", drawn > 0f)
        assertTrue(
            "an inline emoji must not bulge past the body text line: drawn=$drawn line=$line",
            drawn <= line * 1.1f,
        )
        assertTrue(
            "an inline emoji must stay legible, not shrink away: drawn=$drawn line=$line",
            drawn >= line * 0.5f,
        )
        assertNoSideEffects()
    }

    /**
     * The emoji tracks the body font: at a larger body size the artwork must grow with the text and
     * still fit the line, so a fixed pixel size - or one left at upstream's constant - cannot pass.
     */
    @Test
    fun inlineEmojiTracksALargerBodyFont() {
        val laugh = "emoji/${runToken()}-laugh.png"
        val defaultFont = SharedConfig.fontSize
        try {
            openWith({ s ->
                s.serveImage(laugh, solidPng(Color.rgb(255, 204, 0), 40))
                "<p>中文 <img class='emoji' alt=':laugh:' src='${s.url(laugh)}' width='20' height='20'> " +
                    "链接 English</p>"
            })
            harness.await("the default-size artwork") {
                harness.drawApp()
                emojiHolders().any { it.drawable?.imageReceiver?.bitmap != null }
            }
            harness.layoutApp()
            harness.drawApp()
            harness.drawApp()
            val (smallDrawn, smallLine) = emojiSideAndTextLine()

            SharedConfig.fontSize = defaultFont + 8
            // The body is laid out from SharedConfig.fontSize when the message is built, so a fresh
            // page is what carries the larger font into the real renderer.
            harness.topFragment()!!.finishFragment()
            harness.idleClock(600)
            harness.present(TopicFragment.forTopic(TOPIC, "Emoji font"))
            harness.await("the larger-size artwork") {
                harness.drawApp()
                contentRowCount(harness) == 1 &&
                    emojiHolders().any { it.drawable?.imageReceiver?.bitmap != null }
            }
            harness.layoutApp()
            harness.drawApp()
            harness.drawApp()
            val (largeDrawn, largeLine) = emojiSideAndTextLine()

            assertTrue(
                "the emoji must not bulge past the line at the larger font: drawn=$largeDrawn line=$largeLine",
                largeDrawn <= largeLine * 1.1f,
            )
            assertTrue(
                "the emoji must stay legible at the larger font: drawn=$largeDrawn line=$largeLine",
                largeDrawn >= largeLine * 0.5f,
            )
            assertTrue(
                "a larger body font must draw a larger emoji: small=$smallDrawn large=$largeDrawn",
                largeDrawn > smallDrawn,
            )
            assertTrue(
                "a larger body font must give a taller text line: small=$smallLine large=$largeLine",
                largeLine > smallLine,
            )
        } finally {
            SharedConfig.fontSize = defaultFont
        }
        assertNoSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The rendered emoji's real drawn side and the body text's own line height, both in pixels. The
     * drawn side is read off the holder's drawable bounds (a square of the span's measured size),
     * which the production cell's own draw populated.
     */
    private fun emojiSideAndTextLine(): Pair<Float, Float> {
        val holder = emojiHolders().first { it.drawable?.imageReceiver?.bitmap != null }
        return holder.drawableBounds.width().toFloat() to bodyLineHeight()
    }

    /** The body text paint's own line height in pixels: what an inline emoji must fit inside. */
    private fun bodyLineHeight(): Float {
        val metrics = cell().messageObject.richLayout.textPaint.fontMetricsInt
        return (kotlin.math.abs(metrics.ascent) + kotlin.math.abs(metrics.descent)).toFloat()
    }

    private fun emojiHolders(): List<AnimatedEmojiSpan.AnimatedEmojiHolder> =
        richTextBlocks().flatMap { block -> block.text.animatedEmojiStack?.holders?.toList().orEmpty() }

    private fun richTextBlocks(): List<RichMessageLayout.RichTextBlock> =
        cell().messageObject.richLayout.blocks.filterIsInstance<RichMessageLayout.RichTextBlock>()

    /** The text an emoji span covers: the label copy and accessibility must keep. */
    private fun emojiCoveredText(holder: AnimatedEmojiSpan.AnimatedEmojiHolder): String {
        val layout = holder.layout ?: return ""
        val spannable = layout.text as? android.text.Spanned ?: return ""
        val span = holder.span ?: return ""
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        return if (start in 0 until end) spannable.subSequence(start, end).toString() else ""
    }

    /**
     * Each emoji's document id and the square the span reserved for it, in the visible cell.
     *
     * <p>[AnimatedEmojiSpan.AnimatedEmojiHolder.drawableBounds] is a draw-pass value: it is only
     * populated once the span's line has actually been drawn, and a cold span whose row is not
     * under the current scroll offset leaves it empty. The reservation the layout makes is
     * [AnimatedEmojiSpan.measuredSize], which is what decides the wrap and the bubble's height;
     * read that so the assertion is about the span's box, not about whether this frame drew it.</p>
     */
    private fun emojiBoxesByDocument(): Map<Long, Pair<Int, Int>> = emojiHolders().mapNotNull { holder ->
        val span = holder.span ?: return@mapNotNull null
        val measured = AnimatedEmojiSpan::class.java
            .getDeclaredField("measuredSize")
            .apply { isAccessible = true }
            .getInt(span)
        span.documentId to (measured to measured)
    }.toMap()

    /**
     * The visible body's own rendering geometry: the wrap points and pixel widths of every line,
     * the body block's height, the rich layout's height and the cell's position and height. A
     * placeholder that measured a shortcode label instead of the emoji-span square - or a rebuild
     * that re-wrapped the paragraph - changes this between the cold and loaded states.
     */
    private fun bodyGeometry(): String {
        val rich = cell().messageObject.richLayout
        val body = rich.blocks.filterIsInstance<RichMessageLayout.RichTextBlock>().firstOrNull() ?: return ""
        val layout = body.text.layout ?: return ""
        val lines = (0 until layout.lineCount).joinToString("|") { i ->
            "${layout.getLineStart(i)}-${layout.getLineEnd(i)}@${layout.getLineWidth(i).toInt()}"
        }
        return "cell=${cell().top},${cell().height}|bubble=${cell().backgroundDrawableLeft}," +
            "${cell().backgroundDrawableRight}|rich=${rich.getHeight()}|block=${body.getHeight()}|lines=$lines"
    }

    /** The cached file an emoji document id names: the same id/photo-size shape the tag carries. */
    private fun emojiFile(documentId: Long): File =
        DiscourseImageBridge.localFiles(PostImage(documentId, "", 20, 20, "png")).first()

    private fun assertNoSideEffects() = harness.assertNoTelegramSideEffects()

    private fun solidPng(color: Int, size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    /** A per-run token, so one test's emoji picture and document id are never another's. */
    private fun runToken(): String = "e" + System.nanoTime().toString(36)

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

    private fun rebindPosts() {
        list().adapter?.notifyDataSetChanged()
        harness.layoutApp()
        harness.idleClock(200)
    }

    private fun renderTo(name: String, dir: String = "rich-message-layout/emoji") {
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
        println("emoji: wrote $out (${out.length()} bytes)")
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
        .first { File(it, "fixtures/discourse").isDirectory }

    /**
     * Opens one floor whose cooked body [body] builds, with the site already started so the body can
     * name the site's own URLs. The emoji catalog store is emptied first, so a custom shortcode the
     * site serves is the one this read carries rather than a catalog an earlier test kept.
     */
    private fun openWith(
        body: (FakeDiscourseSite) -> String,
        count: Int = 1,
        beforeBoot: () -> Unit = {},
    ) {
        site.start()
        coldEmojiStore()
        val html = body(site)
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        val stream = topic.getJSONObject("post_stream")
        val template = stream.getJSONArray("posts").getJSONObject(0)
        stream.put("posts", JSONArray().apply {
            for (number in 1..count) put(JSONObject(template.toString()).apply {
                put("id", 4000 + number).put("post_number", number).put("cooked", html)
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
        harness.present(TopicFragment.forTopic(TOPIC, "Emoji render"))
        harness.await("topic rows") {
            harness.layoutApp()
            contentRowCount(harness) == count && cells().isNotEmpty()
        }
        harness.drawApp()
    }

    private fun coldEmojiStore() {
        harness.context.getSharedPreferences(EMOJI_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun list(): RecyclerListView = contentListOf(harness.activity)!!
    private fun cells(): List<ChatMessageCell> = (0 until list().childCount)
        .map { list().getChildAt(it) }.filterIsInstance<ChatMessageCell>()
    private fun cell(): ChatMessageCell {
        for (index in 0 until list().childCount) {
            (list().getChildAt(index) as? ChatMessageCell)?.let { return it }
        }
        throw AssertionError("the list drew no message cell")
    }

    private companion object {
        const val TOPIC = 8517L
        const val EMOJI_PREFS = "nativedo.emoji_catalog"
    }
}
