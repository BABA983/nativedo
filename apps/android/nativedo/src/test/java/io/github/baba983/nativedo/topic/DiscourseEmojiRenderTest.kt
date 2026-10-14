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
 *   <li>an emoji whose picture is already on disk must reach upstream's rich renderer as an
 *       `animated-emoji` tag - a `textCustomEmoji` / `AnimatedEmojiSpan` with a decoded bitmap -
 *       beside zh/en/bold/anchor runs, not as its `:shortcode:` label;</li>
 *   <li>an emoji whose picture is still arriving must read as its label at first, then become the
 *       artwork once the fetch completes, without the list moving or its adapter being replaced;</li>
 *   <li>an emoji the site will not hand over (or serves as something undecodable) must stay its
 *       readable label rather than a blank square;</li>
 *   <li>two distinct emoji must never share a picture;</li>
 *   <li>a raw shortcode is an emoji only when the loaded catalog names it, and never inside
 *       code/pre.</li>
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
     * A picture that is blocked mid-transfer: the emoji must read as its label at first, then, once
     * the transfer finishes, become the artwork - on the same visible row, with the reader's place,
     * the floor and any expanded details untouched. The failure this pins is the emoji refresh
     * replacing the floor's rows: a remove/insert (or a change notice the cell animation cannot
     * reuse) re-creates the visible {@code ChatMessageCell}, so the floor flashes and the reader's
     * row is recycled. The adapter, the visible row's identity, its pixel anchor and its content
     * must all survive. Nothing navigates and nothing reaches tgnet while it waits.
     */
    @Test
    fun blockedEmojiFetchStaysALabelThenUpgradesInPlaceWithoutNavigation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val path = "emoji/${runToken()}-blocked.png"
        val bytes = solidPng(Color.rgb(220, 40, 120), 40)
        try {
            openWith({ s ->
                s.serveResponse(path) {
                    entered.countDown()
                    check(release.await(60, TimeUnit.SECONDS))
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                }
                "<p>Anchor <img class='emoji' alt=':slow:' src='${s.url(path)}' width='20' height='20'> tail</p>" +
                    "<details><summary>More</summary><p>Expanded while emoji waits</p></details>"
            }, count = 20)
            harness.await("the emoji request to be in flight") { entered.count == 0L }

            val list = list()
            val adapter = list.adapter
            val manager = list.layoutManager as LinearLayoutManager
            manager.scrollToPositionWithOffset(8, -37)
            harness.layoutApp()
            harness.idleClock(100)
            val before = cell()
            val top = before.top
            val floorId = before.messageObject.id
            assertEquals("test must start away from the first floor", 8, manager.findFirstVisibleItemPosition())
            assertTrue("no artwork may exist before the picture is on disk", emojiHolders().isEmpty())
            assertTrue("the emoji must read as its label first",
                before.messageObject.richLayout.joinedText.contains(":slow:"))
            assertTrue("nothing may navigate while the fetch waits", harness.topFragment() is TopicFragment)
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
            renderTo("emoji-blocked-label")

            release.countDown()
            harness.await("the blocked emoji to become artwork") {
                harness.drawApp()
                emojiHolders().any { it.drawable?.imageReceiver?.bitmap != null }
            }

            assertSame("the adapter must not be replaced by the upgrade", adapter, list.adapter)
            assertSame("the visible row must be kept, not removed and re-created", before, cell())
            assertTrue("the upgrade must not navigate", harness.topFragment() is TopicFragment)
            assertEquals("emoji completion must retain the visible floor", floorId, cell().messageObject.id)
            assertEquals("the floor's pixel anchor must not move", top, cell().top)
            assertTrue("emoji completion must not close the reader's expanded details",
                cell().messageObject.richLayout.joinedText.contains("Expanded while emoji waits"))
            assertTrue("the upgrade must replace the label with artwork",
                emojiHolders().any { it.span?.documentId != null })
            renderTo("emoji-blocked-upgraded")
            assertNoSideEffects()
        } finally {
            release.countDown()
        }
    }

    /** An emoji the site refuses, or answers with something that is not a picture, stays a label. */
    @Test
    fun anEmojiTheSiteWillNotHandOverOrServesGarbageStaysALabel() {
        val absent = "emoji/" + runToken() + "-absent.png"
        val garbage = "emoji/" + runToken() + "-garbage.png"
        openWith({ s ->
            // One path is never served (404); the other answers 200 with bytes that are not a picture.
            s.serveResponse(garbage) {
                MockResponse().setHeader("Content-Type", "image/png").setBody("this is not a png")
            }
            "<p>Keep <img class='emoji' alt=':missing:' src='${s.url(absent)}' width='20' height='20'> " +
                "and <img class='emoji' alt=':bad:' src='${s.url(garbage)}' width='20' height='20'> readable " +
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
        assertTrue("an undrawable picture must leave no emoji span", emojiHolders().isEmpty())
        val joined = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("a refused picture must not blank the run: $joined", joined.contains("Keep :missing:"))
        assertTrue("a garbage picture must not blank the run: $joined", joined.contains(":bad: readable"))
        assertTrue("missing src must keep the label: $joined", joined.contains(":no_source:"))
        assertTrue("unsupported src must keep the title: $joined", joined.contains(":bad_source:"))
        renderTo("emoji-missing-label", "rich-message-layout/emoji")
        assertNoSideEffects()
    }

    /**
     * A raw shortcode the cook left as text: only a shortcode the loaded catalog names becomes an
     * emoji, and inside a `<code>` block even a known one stays literal.
     */
    @Test
    fun aRawShortcodeIsResolvedOnlyWhenTheLoadedCatalogNamesIt() {
        val wave = "emoji/${runToken()}-wave.png"
        openWith({ s ->
            s.serveImage(wave, solidPng(Color.rgb(80, 200, 120), 40))
            // The catalog the topic read carries: only `site_wave` is a known shortcode.
            s.serveJson("emojis.json", """{"custom":[{"name":"site_wave","url":"${s.url(wave)}"}]}""")
            "<p>hi :site_wave: and :not_a_thing: and <code>:site_wave:</code></p>"
        })

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
                list().adapter?.itemCount == 1 &&
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
        val metrics = cell().messageObject.richLayout.textPaint.fontMetricsInt
        val line = (kotlin.math.abs(metrics.ascent) + kotlin.math.abs(metrics.descent)).toFloat()
        return holder.drawableBounds.width().toFloat() to line
    }

    private fun emojiHolders(): List<AnimatedEmojiSpan.AnimatedEmojiHolder> =
        richTextBlocks().flatMap { block -> block.text.animatedEmojiStack?.holders?.toList().orEmpty() }

    private fun richTextBlocks(): List<RichMessageLayout.RichTextBlock> =
        cell().messageObject.richLayout.blocks.filterIsInstance<RichMessageLayout.RichTextBlock>()

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
            list().adapter?.itemCount == count && cells().isNotEmpty()
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
        val manager = list().layoutManager as LinearLayoutManager
        return manager.findViewByPosition(manager.findFirstVisibleItemPosition()) as ChatMessageCell
    }

    private companion object {
        const val TOPIC = 8517L
        const val EMOJI_PREFS = "nativedo.emoji_catalog"
    }
}
