// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.network.EmojiDto
import io.github.baba983.nativedo.core.network.EmojiGroupsDto
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
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import org.telegram.ui.Components.SearchField
import org.telegram.ui.Components.chat.ChatInputViewsContainer
import org.telegram.ui.Components.inset.WindowInsetsStateHolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The linux.do emoji picker, driven through the production new-topic screen.
 *
 * <p>Each test names the failure it pins. The panel is not mounted by hand: the screen is upstream's
 * own [NewTopicFragment], presented on the real shell [UpstreamUiHarness.bootApp] boots, and the
 * panel is the picker [DiscourseComposerTools] added to the composer's own
 * [ChatInputViewsContainer]. The reader opens it with the production emoji affordance
 * ([ChatActivityEnterView.getEmojiButton]), inserts into the production field
 * ([ChatActivityEnterView.getEditField]), and the panel reports its height through the fragment's
 * own [WindowInsetsStateHolder] - so what is exercised is the wiring the app ships, not a stand-in
 * composer. The panel reads its catalog from the real
 * [io.github.baba983.nativedo.core.network.DiscourseApiClient] against [FakeDiscourseSite], so the
 * `/emojis.json` wire format and the DTO mapping run for real.</p>
 *
 * <p>Race cases drive the panel directly ([DiscourseEmojiPicker.show]/`dismiss`): that is the same
 * entry point the composer button uses, minus the click. The cold store is dropped before the shell
 * boots, so the read under test is the cold-cache path; the image decode dispatcher seam
 * [DiscourseEmojiPicker.imageDecodeDispatcher] is left available for the late-decode guard.</p>
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
internal class DiscourseEmojiPickerTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    /** Every screen a test presented, so teardown can run its destroy path (and its picker's). */
    private val screens = mutableListOf<Screen>()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        // Installed before bootApp (the shell builds the container on first use); a reset also drops
        // any container an earlier test left behind.
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        // Destroying the fragment runs DiscourseComposerTools.onDestroy, which destroys the picker
        // and cancels its scope.
        screens.forEach { screen -> screen.fragment.onFragmentDestroy() }
        // Closing an open keyboard animates its inset. Drain that animation before Robolectric
        // drops the looper, or its NotificationCenter lock blocks subsequent theme/media tests.
        harness.idleClock(500)
        screens.clear()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    // ------------------------------------------------------------------ insertion

    /**
     * Failure mode: the picker inserts a Telegram document/emoji id, or it replaces the whole field
     * instead of the caret. What must be inserted is the site's exact `:name:` - at the caret, over
     * a selection - leaving the text around it byte-for-byte.
     */
    @Test
    fun anEmojiInsertsTheExactShortcodeAtTheCaretAndLeavesTheTextAroundIt() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))
        assertFalse(
            "an unopened picker does not fetch a catalog",
            site.requestPaths.contains("emojis.json"),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the catalog grid") { grid(screen)?.adapter?.itemCount == 3 }
        harness.await("the tile to settle") { harness.layoutApp(); cellFor(screen, ":smile:") != null }

        // A caret in the middle of the text.
        screen.field.setText("hello world")
        screen.field.setSelection(5)
        clickEmoji(screen, 0)
        assertEquals(
            "the shortcode goes in at the caret, the text around it is untouched",
            "hello:smile: world",
            screen.field.text.toString(),
        )
        assertEquals("the caret follows the inserted shortcode", 12, screen.field.selectionStart)

        // A selection, replaced rather than appended.
        screen.field.setText("hello world")
        screen.field.setSelection(6, 11)
        clickEmoji(screen, 0)
        assertEquals(
            "a selection is replaced by the shortcode, not duplicated",
            "hello :smile:",
            screen.field.text.toString(),
        )

        // Nothing but text: no Telegram span, no document, no media id.
        val telegramSpans = screen.field.text.getSpans(0, screen.field.text.length, Any::class.java)
            .filter { span -> span.javaClass.name.startsWith("org.telegram") }
        assertEquals("the inserted shortcode carries no Telegram span", 0, telegramSpans.size)

        retain(harness.renderApp("emoji-picker-insert"), "emoji-picker-insert.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ category + search

    /**
     * Failure mode: the panel shows Telegram's fixed categories or searches by Telegram's keys, so a
     * site query or a site category returns the wrong tiles. Category tabs must switch to the site's
     * own group, and the search box must filter by the site's shortcode name across all groups.
     */
    @Test
    fun theSiteCategoriesSwitchTheGridAndSearchFiltersBySiteName() {
        val screen = openScreen()
        site.serveJson(
            "emojis.json",
            catalog(
                "smileys" to listOf("smile" to imageUrl("smile"), "joy" to imageUrl("joy")),
                "objects" to listOf("book" to imageUrl("book"), "smiley_cat" to imageUrl("smiley_cat")),
            ),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the continuous catalog") { harness.layoutApp(); boundEmojiNames(screen).size == 4 }
        assertEquals(
            "both site groups share one continuous grid, in site order",
            listOf(":smile:", ":joy:", ":book:", ":smiley_cat:"),
            boundEmojiNames(screen),
        )

        // Category navigation scrolls to the second group rather than replacing the catalog.
        tabView(screen, 1)!!.performClick()
        harness.await("the second group") {
            harness.idleClock(100); harness.layoutApp(); boundEmojiNames(screen) == listOf(":book:", ":smiley_cat:")
        }
        assertEquals(
            "the tab switches to the site's own second group",
            listOf(":book:", ":smiley_cat:"),
            boundEmojiNames(screen),
        )

        // Search by the site's name, across groups, case-insensitively.
        search(screen)!!.searchEditText.setText("SMI")
        harness.layoutApp()
        assertEquals(
            "search filters across groups by the site's shortcode name",
            listOf(":smile:", ":smiley_cat:"),
            boundEmojiNames(screen),
        )

        // A query that matches nothing is a distinct state, not an error.
        search(screen)!!.searchEditText.setText("zzz")
        harness.layoutApp()
        assertEquals("no matches", 0, grid(screen)!!.adapter!!.itemCount)
        assertTrue("the empty view says there is no match", visible(empty(screen)))

        // Clearing the query restores the group the reader was on.
        search(screen)!!.searchEditText.setText("")
        harness.layoutApp()
        harness.await("the restored group") {
            harness.layoutApp(); boundEmojiNames(screen) == listOf(":book:", ":smiley_cat:")
        }
        assertEquals(
            "clearing the query restores the selected group",
            listOf(":book:", ":smiley_cat:"),
            boundEmojiNames(screen),
        )

        retain(harness.renderApp("emoji-picker-search"), "emoji-picker-search.png")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun scrollingTheCatalogUpdatesTheCompactCategoryStrip() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog(
            "smileys" to (1..63).map { "face_$it" to imageUrl("face_$it") },
            "objects" to (1..54).map { "object_$it" to imageUrl("object_$it") },
        ))
        openPicker(screen)
        harness.await("the catalog") { harness.layoutApp(); cellFor(screen, ":face_1:") != null }
        assertTrue("a category is compact, not half the panel width",
            tabView(screen, 0)!!.width <= AndroidUtilities.dp(56f))
        tabView(screen, 1)!!.performClick()
        harness.await("the category jump") {
            harness.idleClock(100); harness.layoutApp()
            boundEmojiNames(screen).firstOrNull()?.startsWith(":object_") == true
        }
        grid(screen)!!.scrollBy(0, -AndroidUtilities.dp(800f))
        harness.await("manual scrolling after the jump selects smileys") {
            harness.layoutApp(); tabs(screen)!!.currentTabId == 0
        }
        grid(screen)!!.scrollBy(0, AndroidUtilities.dp(410f))
        harness.await("objects scrolled into view") {
            harness.layoutApp(); tabs(screen)!!.currentTabId == 1
        }
        assertTrue("the second group is visible", boundEmojiNames(screen).any { it.startsWith(":object_") })
        grid(screen)!!.scrollBy(0, -AndroidUtilities.dp(800f))
        harness.await("scrolling back selects smileys") {
            harness.layoutApp(); tabs(screen)!!.currentTabId == 0
        }
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun thePanelBackspaceDeletesAShortcodeSelectionAndRepeatsOnlyWhileHeld() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))
        openPicker(screen)
        harness.await("the emoji") { harness.layoutApp(); cellFor(screen, ":smile:") != null }
        val button = descendants(screen.picker).first { it.contentDescription == "退格" }
        screen.field.setText("前:smile:后")
        screen.field.setSelection(8)
        button.performClick()
        assertEquals("one delete removes the site's entire emoji token", "前后", screen.field.text.toString())
        screen.field.setText("beforeXYZafter")
        screen.field.setSelection(6, 9)
        button.performClick()
        assertEquals("a selected range is removed without touching its neighbours", "beforeafter", screen.field.text.toString())
        screen.field.setText("abcdefghij")
        screen.field.setSelection(10)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
        button.dispatchTouchEvent(down)
        down.recycle()
        harness.idleClock(850)
        assertTrue("holding backspace repeats deletion", screen.field.text.length < 9)
        val cancel = MotionEvent.obtain(0, 850, MotionEvent.ACTION_CANCEL, 5f, 5f, 0)
        button.dispatchTouchEvent(cancel)
        cancel.recycle()
        val afterCancel = screen.field.text.toString()
        harness.idleClock(1000)
        assertEquals("cancel stops deletion", afterCancel, screen.field.text.toString())
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ category icons

    /**
     * Failure mode: the category tabs still show the group's title as text instead of the group's
     * own picture, or they pick the wrong picture - a later emoji, or a fixed per-name table rather
     * than the group's real first emoji. linux.do's own picker draws each section's tab as the
     * group's first emoji (`frontend/discourse/app/components/emoji-picker/content.gjs`: the nav
     * button's `<img src={{tonableEmojiUrl (get emojis "0")}}>`), so the first emoji of each group,
     * in the site's own order, is the tab icon. The pictures are given asymmetric colours so the
     * assertion pins which picture landed on which tab, and the group's readable name must stay on
     * the tab for a screen reader.
     */
    @Test
    fun theCategoryTabsDrawEachGroupsFirstEmojiAsItsIconAndKeepTheNameForScreenReaders() {
        val screen = openScreen()
        val red = Color.rgb(214, 40, 40)
        val blue = Color.rgb(40, 90, 214)
        val green = Color.rgb(40, 190, 80)
        val yellow = Color.rgb(230, 200, 40)
        site.serveImage("emoji/smile.png", solidPng(red, 96))
        site.serveImage("emoji/joy.png", solidPng(blue, 96))
        site.serveImage("emoji/book.png", solidPng(green, 96))
        site.serveImage("emoji/leaf.png", solidPng(yellow, 96))
        site.serveJson(
            "emojis.json",
            catalog(
                "smileys" to listOf("smile" to imageUrl("smile"), "joy" to imageUrl("joy")),
                "objects" to listOf("book" to imageUrl("book"), "leaf" to imageUrl("leaf")),
            ),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the category tabs") { tabs(screen)?.tabsCount == 2 }

        // The readable group name is kept on the tab for a screen reader; the tab no longer shows
        // that name as its text.
        assertEquals(
            "the first tab keeps the group's name for a screen reader",
            "Smileys",
            tabView(screen, 0)?.contentDescription,
        )
        assertEquals(
            "the second tab keeps the group's name for a screen reader",
            "Objects",
            tabView(screen, 1)?.contentDescription,
        )
        assertTrue(
            "the tab shows an icon, not the group's title text",
            tabView(screen, 0)?.text?.toString() != "Smileys",
        )

        // The icon is the group's *first* emoji (red / green), not a later one (blue / yellow).
        harness.await("the first group's icon") { harness.layoutApp(); stripShows(screen, 0, red) }
        harness.await("the second group's icon") { harness.layoutApp(); stripShows(screen, 1, green) }
        assertTrue(
            "the first tab draws its group's first emoji",
            stripShows(screen, 0, red),
        )
        assertFalse(
            "the first tab does not draw a later emoji from its group",
            stripShows(screen, 0, blue),
        )
        assertTrue(
            "the second tab draws its own group's first emoji",
            stripShows(screen, 1, green),
        )
        assertFalse(
            "the second tab does not draw a later emoji from its group",
            stripShows(screen, 1, yellow),
        )

        retain(harness.renderApp("emoji-picker-category-icons"), "emoji-picker-category-icons.png")

        // Switching to the second group keeps its own icon on the tab that is now selected.
        tabView(screen, 1)!!.performClick()
        harness.layoutApp()
        harness.await("the second group selected") { tabs(screen)!!.currentTabId == 1 }
        assertTrue(
            "the second group's tab keeps its own icon once selected",
            stripShows(screen, 1, green),
        )
        retain(harness.renderApp("emoji-picker-category-selected"), "emoji-picker-category-selected.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Failure mode: a custom group - one the site added that is not among Discourse's standard
     * groups - gets no icon (or a guessed one) because the picker only knows the standard names. A
     * custom group must show a suitable icon taken from its own real catalog: its first emoji, the
     * same rule the standard groups follow. The group's own name stays on the tab for a screen
     * reader.
     */
    @Test
    fun aCustomSiteGroupDrawsItsOwnFirstEmojiAsItsIcon() {
        val screen = openScreen()
        val magenta = Color.rgb(200, 40, 190)
        site.serveImage("emoji/site_wave.png", solidPng(magenta, 96))
        site.serveJson(
            "emojis.json",
            catalog("site_wave" to listOf("site_wave" to imageUrl("site_wave"))),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the custom group's tab") { tabs(screen)?.tabsCount == 1 }

        assertEquals(
            "the custom group's own name is kept for a screen reader",
            "Site Wave",
            tabView(screen, 0)?.contentDescription,
        )
        harness.await("the custom group's icon") { harness.layoutApp(); stripShows(screen, 0, magenta) }
        assertTrue(
            "a custom group draws its own first emoji as the tab icon",
            stripShows(screen, 0, magenta),
        )
        retain(harness.renderApp("emoji-picker-custom-category"), "emoji-picker-custom-category.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ states + failure

    /**
     * Failure mode: a catalog read that fails is shown as an empty site (or an empty site is shown as
     * a failure), with no way back. Loading, empty and error must be three distinct states, and the
     * error must retry into the loaded grid.
     */
    @Test
    fun loadingEmptyAndErrorAreDistinctAndTheErrorRetries() {
        val screen = openScreen()
        var serving: String? = null
        val good = catalog("smileys" to listOf("smile" to imageUrl("smile")))
        val emptyCatalog = catalog("smileys" to emptyList())
        site.serveResponse("emojis.json") {
            val body = serving
            if (body == null) {
                MockResponse().setResponseCode(500).setBody("nope")
            } else {
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
            }
        }

        // Loading is entered synchronously and is not the loaded grid.
        openPicker(screen)
        assertTrue("the panel reports loading before the answer", visible(empty(screen)))
        assertEquals("no tiles while loading", 0, grid(screen)!!.adapter!!.itemCount)

        harness.await("the failed read to surface") { visible(empty(screen)) && textOf(empty(screen)!!) == ERROR_TEXT }
        assertEquals("a failed read is the error state", ERROR_TEXT, textOf(empty(screen)!!))
        retain(harness.renderApp("emoji-picker-error"), "emoji-picker-error.png")

        // The retry is the real user path: a tap on the error.
        serving = good
        tap(empty(screen)!!)
        harness.layoutApp()
        harness.await("the retry to load the grid") { grid(screen)!!.adapter!!.itemCount == 3 }
        assertFalse("the loaded grid clears the state view", visible(empty(screen)))

        // An empty site is its own state, not the error one. A separate cold read: the kept catalog
        // is dropped and the panel re-opened, so the site is the only place an answer can come from.
        serving = emptyCatalog
        clearEmojiStore(screen.container)
        screen.picker.dismiss()
        screen.picker.show()
        harness.layoutApp()
        harness.await("the empty site") { visible(empty(screen)) && textOf(empty(screen)!!) == EMPTY_TEXT }
        assertEquals("an empty site is the empty state", EMPTY_TEXT, textOf(empty(screen)!!))

        retain(harness.renderApp("emoji-picker-empty"), "emoji-picker-empty.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ image fallback

    /**
     * Failure mode: an emoji whose picture cannot be produced draws a blank tile, so the reader
     * cannot tell which emoji it is. A tile that has no decodable picture must show the site's own
     * readable name, and a tile whose picture arrives must draw it.
     */
    @Test
    fun aTileWithNoDecodablePictureFallsBackToItsReadableName() {
        val screen = openScreen()
        val brokenUrl = site.url("emoji/not-a-picture.png")
        // A 200 whose body is not a picture: the cache must refuse to publish it.
        site.serveResponse("emoji/not-a-picture.png") {
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>not a picture</html>")
        }
        site.serveImage("emoji/book.png", solidPng(Color.rgb(0, 128, 255), 48))
        site.serveJson(
            "emojis.json",
            catalog("objects" to listOf("book" to site.url("emoji/book.png"), "torn" to brokenUrl)),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the tiles") { harness.layoutApp(); boundEmojiNames(screen).size == 2 }
        harness.await("the fallback to settle") {
            harness.layoutApp()
            imageLoaded(screen, ":book:") && !imageLoaded(screen, ":torn:")
        }

        assertTrue("the tile with a real picture draws it", imageLoaded(screen, ":book:"))
        assertFalse("the tile whose picture is not a picture draws no image", imageLoaded(screen, ":torn:"))
        assertEquals(
            "the un-decodable tile shows its site name instead of a blank square",
            "torn",
            fallbackLabel(screen, ":torn:"),
        )
        assertEquals(
            "the readable description is the shortcode the reader would get",
            ":torn:",
            cellFor(screen, ":torn:")!!.contentDescription.toString(),
        )

        retain(harness.renderApp("emoji-picker-fallback"), "emoji-picker-fallback.png")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ relative catalog URL

    /**
     * Failure mode: the site answers `/emojis.json` with root-relative image paths (`/images/...`),
     * which the picker hands to okhttp untouched; okhttp refuses a URL with no scheme
     * (`Expected URL scheme 'http' or 'https' but no scheme was found for /image...`), so every tile
     * falls back to its name and the reader never sees an emoji picture (device
     * `.amp/in/artifacts/composer-next-wave/fixed-device/33-emoji-image-error.txt`, 172 throws). A
     * catalog path that is not absolute must be resolved against the site before the tile fetches
     * it.
     */
    @Test
    fun aRootRelativeEmojiImageIsResolvedAgainstTheSiteAndDrawn() {
        val screen = openScreen()
        // The site answers the way linux.do does: a path, not an absolute URL.
        site.serveImage("emoji/relative-smile.png", solidPng(Color.rgb(0, 160, 90), 48))
        site.serveJson(
            "emojis.json",
            catalog("smileys" to listOf("relative_smile" to "/emoji/relative-smile.png")),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the tile") { harness.layoutApp(); cellFor(screen, ":relative_smile:") != null }
        harness.await("the tile to draw") {
            harness.layoutApp()
            imageLoaded(screen, ":relative_smile:")
        }

        assertTrue(
            "a root-relative catalog path is resolved against the site and drawn",
            imageLoaded(screen, ":relative_smile:"),
        )
        assertTrue(
            "the resolved path is what the site was asked for",
            site.requestPaths.contains("emoji/relative-smile.png"),
        )
        retain(harness.renderApp("emoji-picker-relative-url"), "emoji-picker-relative-url.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Failure mode: the other half of the same resolution - an absolute catalog URL (a CDN, or a
     * site that answers one) must be left exactly as it is, not re-based onto the site. If it were
     * prefixed, the fetch would not be the served path and the tile would fall back to its name.
     */
    @Test
    fun anAbsoluteEmojiImageUrlIsLeftAsItIsAndDrawn() {
        val screen = openScreen()
        site.serveImage("emoji/cdn-smile.png", solidPng(Color.rgb(160, 0, 90), 48))
        site.serveJson(
            "emojis.json",
            catalog("smileys" to listOf("cdn_smile" to site.url("emoji/cdn-smile.png"))),
        )

        openPicker(screen)
        harness.layoutApp()
        harness.await("the tile") { harness.layoutApp(); cellFor(screen, ":cdn_smile:") != null }
        harness.await("the tile to draw") {
            harness.layoutApp()
            imageLoaded(screen, ":cdn_smile:")
        }

        assertTrue(
            "an absolute catalog URL is left as it is and drawn",
            imageLoaded(screen, ":cdn_smile:"),
        )
        assertTrue(
            "the absolute path is what the site was asked for",
            site.requestPaths.contains("emoji/cdn-smile.png"),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ search + system IME

    /**
     * Failure mode: focusing the picker's own search field opens the real system IME over the panel's
     * normal place - the host pins the in-app keyboard to the screen bottom and the holder lifts the
     * capsule by `max(IME, in-app)` - so the search field and the result grid sit behind the keyboard
     * and cannot be reached (device `.amp/in/artifacts/composer-next-wave/fixed-device/12-search-grin.png`:
     * the panel is gone, only the keyboard and the capsule show). With the IME up the results must
     * ride above it, and Back must release the lift without leaving the form.
     *
     * <p>The IME and the navigation bar are given unequal heights: the same number used for both
     * would hide a panel that double-counts one of them.</p>
     */
    @Test
    fun searchWithTheKeyboardUpKeepsTheResultsAboveTheImeAndBackReleases() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("grin" to imageUrl("grin"))))

        openPicker(screen)
        harness.layoutApp()
        harness.await("the grid") { grid(screen)!!.adapter!!.itemCount == 3 }
        harness.await("the tile to bind") { harness.layoutApp(); cellFor(screen, ":grin:") != null }

        search(screen)!!.searchEditText.setText("grin")
        harness.layoutApp()
        // The reader taps the search field; the real IME opens over the panel's normal place.
        assertTrue("the search field can take focus", search(screen)!!.searchEditText.requestFocus())
        harness.layoutApp()
        val ime = AndroidUtilities.dp(312f)
        val nav = AndroidUtilities.dp(23f)
        applyInsets(screen.insets, navigationBar = nav, ime = ime)
        harness.idleClock(500)
        harness.layoutApp()

        val imeTop = windowBottom(screen) - ime
        val field = search(screen)!!.searchEditText
        val fieldBottom = windowTop(field) + field.height
        assertTrue(
            "the search field rides above the system IME [imeTop=$imeTop ime=$ime nav=$nav " +
                "inputTop=${windowTop(screen.input)} inputHeight=${screen.input.height} " +
                "pickerTop=${windowTop(screen.picker)} pickerHeight=${screen.picker.height} " +
                "fieldTop=${windowTop(field)} fieldBottom=$fieldBottom " +
                "composerTop=${windowTop(screen.composer)} composerHeight=${screen.composer.height} " +
                "inApp=${screen.insets.inAppKeyboardHeight} " +
                "maxBottom=${screen.insets.animatedMaxBottomInset} " +
                "imeInset=${screen.insets.animatedImeBottomInset}]",
            fieldBottom <= imeTop,
        )
        val cell = cellFor(screen, ":grin:")!!
        val cellBottom = windowTop(cell) + cell.height
        assertTrue(
            "a result tile rides above the system IME (tile bottom $cellBottom, IME top $imeTop)",
            cellBottom <= imeTop,
        )
        val backspace = descendants(screen.picker).first { it.contentDescription == "退格" }
        val visibleBounds = android.graphics.Rect()
        assertTrue("backspace has a nonempty on-screen hit target", backspace.getGlobalVisibleRect(visibleBounds))
        assertEquals("the IME search band does not clip the backspace control", backspace.height, visibleBounds.height())
        assertTrue("backspace has its full touch-target height", backspace.height >= AndroidUtilities.dp(48f))
        assertTrue(
            "the panel's backspace remains reachable above the IME",
            windowTop(backspace) + backspace.height <= imeTop,
        )
        assertTrue(
            "the capsule stays on screen above the panel",
            windowTop(screen.composer) >= 0 && windowTop(screen.composer) < windowTop(screen.picker),
        )
        retain(harness.renderApp("emoji-picker-search-ime"), "emoji-picker-search-ime.png")

        // Back releases the lift at once and keeps the new-topic form.
        assertFalse(
            "a back press is consumed by the panel, not the screen",
            screen.fragment.onBackPressed(true),
        )
        harness.idleClock(500)
        harness.layoutApp()
        assertEquals(
            "back releases the in-app height even with the IME up",
            0,
            screen.insets.inAppKeyboardHeight,
        )
        assertFalse("the panel is closed", screen.picker.isOpen())
        assertTrue("the form is kept", harness.visibleFragment() === screen.fragment)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ warm start

    /**
     * Failure mode: the panel opens on a warm app - the catalog the app kept, and a tile picture
     * already on disk. Opening it binds the grid, and a tile whose picture is not decoded yet starts
     * its image read. That read can return without ever suspending (a disk hit, or a failure that
     * skips the decode), so with the requestImage coroutine started on `Dispatchers.Main.immediate`
     * its completion runs inline inside `GridAdapter.onBindViewHolder` and calls
     * `adapter.notifyDataSetChanged()` while the RecyclerView is still computing layout. AndroidX
     * forbids that, and it is the fatal crash the device pass recorded
     * (`.amp/in/artifacts/composer-next-wave/integrated-device/crash-emoji.txt`:
     * `requestImage$1.invokeSuspend(DiscourseEmojiPicker.kt:492)` reached from `EmojiCell.bind`
     * during `GridLayoutManager.onLayoutChildren`, its coroutine on `Dispatchers.Main.immediate`).
     * The read - and so the completion that follows it - must be scheduled onto the loop, never run
     * inline. This test reads the observable that separates the two: whether the read itself runs
     * while the RecyclerView computes layout. (The crash itself is not raised here: the decode is
     * held open, which is what keeps the illegal notify from firing and is also what makes the
     * timing deterministic.)
     */
    @Test
    fun openingOntoAWarmCatalogAndAWarmTilePictureDoesNotRunItsImageReadDuringLayout() {
        val screen = openScreen()
        val url = site.url("emoji/smile.png")
        site.serveImage("emoji/smile.png", solidPng(Color.rgb(0, 128, 255), 48))

        // The tile's file is already on disk, the way a prior open left it.
        runBlocking { DiscourseReactionEmoji.cache(screen.container.imageOkHttpClient, url) }
        // The catalog is warm too: show() answers from the kept store and the grid binds on the next
        // layout pass - the device's own warm-start path.
        screen.container.emojiCatalogStore.save(mapOf("smileys" to listOf(EmojiDto("smile", url))))

        // Hold the decode open so the read's own timing is what is observed, not the decoder's.
        val gate = GatedDecodeDispatcher()
        screen.picker.imageDecodeDispatcher = gate
        // The read is a disk hit that returns without suspending. Record whether it ran while the
        // RecyclerView was computing layout. Production reads the real cache through
        // DiscourseReactionEmoji.cache; this replaces only the read, so the timing is deterministic
        // rather than a race with the disk.
        var readDuringLayout: Boolean? = null
        screen.picker.imageFetcher = { _ ->
            readDuringLayout = grid(screen)!!.isComputingLayout()
            DiscourseReactionEmoji.file(url)
        }

        openPicker(screen)
        harness.await("the tile's picture read") { harness.layoutApp(); readDuringLayout != null }
        gate.releaseDecode()

        assertFalse(
            "the tile's picture read must not run inline while the RecyclerView computes layout " +
                "(its completion would then notify the adapter mid-layout - the device crash)",
            readDuringLayout == true,
        )

        harness.await("the tile picture") { harness.layoutApp(); imageLoaded(screen, ":smile:") }
        assertTrue("the warm-start tile draws its picture", imageLoaded(screen, ":smile:"))
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ in-app keyboard

    /**
     * Failure mode: a plain back press closes the panel but leaves the in-app keyboard height
     * reported to the holder, so the host stays lifted (the wait-for-IME path holds it until the
     * system keyboard opens, or a second, whichever is first). Dismissing the panel with back must
     * release the height at once.
     */
    @Test
    fun plainBackDismissalReleasesTheInAppHeightImmediately() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))

        openPicker(screen)
        harness.layoutApp()
        harness.await("the grid") { grid(screen)?.adapter?.itemCount == 3 }
        assertTrue("showing the panel lifts the in-app height", screen.insets.inAppKeyboardHeight > 0)

        // The production back path: the fragment offers the press to the composer tools first, and
        // false means the panel consumed it rather than the screen finishing.
        assertFalse(
            "a back press is consumed by the panel, not the screen",
            screen.fragment.onBackPressed(true),
        )

        assertEquals(
            "a plain back press must release the in-app height at once",
            0,
            screen.insets.inAppKeyboardHeight,
        )
        // It stays released without a clock advance: the wait-for-IME path would hold the height
        // until the fallback a second later.
        harness.idle()
        assertEquals("the height is not re-reported after release", 0, screen.insets.inAppKeyboardHeight)
        assertFalse("the panel is closed", screen.picker.isOpen())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Failure mode: the panel does not take the container's navigation-bar inset, so the last grid
     * row sits under the system bar. A panel in the in-app keyboard bubble must implement upstream's
     * `InAppKeyboardInsetView` and pad itself by the actual nav inset.
     */
    @Test
    fun theNavigationBarInsetPadsThePanel() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))
        openPicker(screen)
        harness.layoutApp()
        harness.await("the grid") { grid(screen)?.adapter?.itemCount == 3 }

        val navPx = AndroidUtilities.dp(37f)
        applyInsets(screen.insets, navigationBar = navPx, ime = 0)
        // The production holder animates an inset change; the clock has to move for the inset to
        // reach the panel, the way it does on the screen's own window.
        harness.idleClock(500)
        harness.layoutApp()
        assertEquals(
            "the container's nav inset pads the panel bottom",
            navPx,
            screen.picker.paddingBottom,
        )

        applyInsets(screen.insets, navigationBar = 0, ime = 0)
        harness.idleClock(500)
        harness.layoutApp()
        assertEquals("the padding follows the inset back to zero", 0, screen.picker.paddingBottom)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ late decode

    /**
     * Failure mode: a tile's `BitmapFactory.decodeFile` runs off the main thread; when the panel
     * closes while it is decoding, the finished bitmap is stored and the adapter is notified, so a
     * closed panel repaints itself and keeps a tile it no longer shows (a later destroy would
     * recycle what it holds, but a dismiss only bumps the generation). The decode must recheck
     * liveness after it returns.
     *
     * <p>A `destroy` cancels the picker's scope, which already stops the coroutine at its next
     * suspension; the liveness recheck is what covers the generation arm, and the same guard covers
     * the destroyed one.</p>
     */
    @Test
    fun aTileDecodeThatLandsAfterThePanelClosesIsDropped() {
        val screen = openScreen()
        site.serveImage("emoji/smile.png", solidPng(Color.rgb(0, 128, 255), 48))
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))

        val gate = GatedDecodeDispatcher()
        screen.picker.imageDecodeDispatcher = gate
        openPicker(screen)
        harness.layoutApp()
        harness.await("the tile to bind") {
            harness.layoutApp()
            grid(screen)?.adapter?.itemCount == 3 && cellFor(screen, ":smile:") != null
        }
        harness.await("the tile decode to be parked off the main thread") { gate.decodeStarted }

        var repaints = 0
        val observer = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                repaints++
            }
        }
        val adapter = grid(screen)!!.adapter!!
        adapter.registerAdapterDataObserver(observer)

        // The panel closes while its tile is still decoding off the main thread.
        screen.picker.dismiss()
        gate.releaseDecode()
        harness.await("the parked decode to finish") { gate.decodeFinished }
        harness.idle()

        assertEquals(
            "a decode that lands after the panel closed is not stored",
            0,
            keptTileCount(screen.picker),
        )
        assertEquals("a decode that lands after the panel closed does not repaint it", 0, repaints)
        adapter.unregisterAdapterDataObserver(observer)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ catalog refresh

    /**
     * Failure mode: a refreshed catalog keeps the old tabs - the strip is only ever filled when it
     * is empty - so a group the site added (or dropped) never shows. A refreshed catalog must reset
     * the strip and rebuild the tabs from it.
     */
    @Test
    fun aRefreshedCatalogRebuildsTheCategoryTabs() {
        val screen = openScreen()
        val red = Color.rgb(214, 40, 40)
        val green = Color.rgb(40, 190, 80)
        val yellow = Color.rgb(230, 200, 40)
        site.serveImage("emoji/smile.png", solidPng(red, 96))
        site.serveImage("emoji/book.png", solidPng(green, 96))
        site.serveImage("emoji/leaf.png", solidPng(yellow, 96))
        val gate = GatedDecodeDispatcher()
        screen.picker.imageDecodeDispatcher = gate
        site.serveJson(
            "emojis.json",
            catalog(
                "smileys" to listOf("smile" to imageUrl("smile")),
                "objects" to listOf("book" to imageUrl("book")),
            ),
        )
        openPicker(screen)
        harness.layoutApp()
        harness.await("the first catalog's tabs") { tabs(screen)?.tabsCount == 2 }
        harness.await("the first catalog's parked image decode") { gate.decodeStarted }
        assertEquals("the first catalog drew its own tabs", 2, tabs(screen)!!.tabsCount)

        // The kept catalog is refreshed (a topic read re-saved it) with a different set of groups;
        // the next open must draw the new groups, not keep the two the strip already had.
        screen.container.emojiCatalogStore.save(
            mapOf(
                "smileys" to listOf(EmojiDto("smile", imageUrl("smile"))),
                "objects" to listOf(EmojiDto("book", imageUrl("book"))),
                "nature" to listOf(EmojiDto("leaf", imageUrl("leaf"))),
            ),
        )
        screen.picker.dismiss()
        screen.picker.show()
        gate.releaseDecode()
        harness.layoutApp()

        assertEquals(
            "the refreshed catalog's tabs replace the old ones",
            3,
            tabs(screen)!!.tabsCount,
        )
        // The group the refresh added draws its own first emoji as its tab icon, and the group that
        // stayed keeps its own - the strip was rebuilt, not kept.
        harness.await("the refreshed group's icon") { harness.layoutApp(); stripShows(screen, 2, yellow) }
        harness.await("the retained group's icon after reopening during decode") { harness.layoutApp(); stripShows(screen, 1, green) }
        assertTrue("the group the refresh added draws its first emoji", stripShows(screen, 2, yellow))
        assertTrue("the group that stayed keeps its own icon", stripShows(screen, 1, green))
        assertEquals(
            "the added group's name is kept for a screen reader",
            "Nature",
            tabView(screen, 2)?.contentDescription,
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ close race

    /**
     * Failure mode: a catalog answer that lands after the panel closed repaints the closed panel, so
     * reopening flashes a stale grid. A response that arrives after [DiscourseEmojiPicker.dismiss]
     * must warm the store but leave the panel untouched.
     */
    @Test
    fun aCatalogAnswerArrivingAfterCloseWarmsTheStoreButNotThePanel() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))

        // The request is in flight and the panel is closed before any answer can be delivered: the
        // test thread never drains the main looper in between, so the continuation cannot have run.
        openPicker(screen)
        screen.picker.dismiss()

        harness.await("the dropped answer to still warm the store") {
            screen.container.emojiCatalogStore.groups.value.isNotEmpty()
        }
        harness.layoutApp()

        assertFalse("the closed panel is not shown", screen.picker.isOpen())
        assertEquals(
            "an answer that arrived after close painted nothing into the closed panel",
            0,
            grid(screen)!!.adapter!!.itemCount,
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ no side effects

    /**
     * Failure mode: assembling the panel boots Telegram - tgnet, its database, its native layer - or
     * reaches its media. The picker is site-only; opening it and selecting from it must touch none of
     * them.
     */
    @Test
    fun openingAndSelectingNeverTouchesTelegram() {
        val screen = openScreen()
        site.serveJson("emojis.json", catalog("smileys" to listOf("smile" to imageUrl("smile"))))

        openPicker(screen)
        harness.layoutApp()
        harness.await("the grid") { grid(screen)!!.adapter!!.itemCount == 3 }
        screen.field.setText("abc")
        screen.field.setSelection(1)
        clickEmoji(screen, 0)
        assertEquals("a:smile:bc", screen.field.text.toString())

        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ screen + host

    /**
     * The production new-topic screen and the pieces of it the picker rides in. The picker is the
     * child the composer tools added to [ChatInputViewsContainer.getInAppKeyboardBubbleContainer];
     * the field is the composer's own; the insets holder is the fragment's own, read by reflection
     * because the fragment owns it privately (the same way these tests read the picker's private
     * state).
     */
    private class Screen(
        val fragment: BaseFragment,
        val picker: DiscourseEmojiPicker,
        val composer: ChatActivityEnterView,
        val container: AppContainer,
        val field: EditTextBoldCursor,
        val input: ChatInputViewsContainer,
        val insets: WindowInsetsStateHolder,
    )

    /**
     * Boots the shell against [FakeDiscourseSite] and presents the real [NewTopicFragment]. The kept
     * emoji catalog is dropped first, so the container the shell builds reads a cold store and the
     * picker's read is the cold-cache path. NewTopic preloads no catalog, so nothing fetches before
     * the emoji affordance is pressed.
     */
    private fun openScreen(): Screen {
        coldEmojiStore()
        harness.bootApp()
        harness.await("the topic list") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        val fragment = NewTopicFragment.create()
        harness.present(fragment)
        harness.await("the new-topic screen") { harness.visibleFragment() === fragment }
        harness.layoutApp()

        val tree = viewTree()
        val composer = tree.filterIsInstance<ChatActivityEnterView>().single()
        val input = tree.filterIsInstance<ChatInputViewsContainer>().single()
        val picker = descendants(input).filterIsInstance<DiscourseEmojiPicker>().single()
        val screen = Screen(
            fragment = fragment,
            picker = picker,
            composer = composer,
            container = NativeDo.container(harness.activity),
            field = composer.editField!!,
            input = input,
            insets = fragmentInsets(fragment),
        )
        screens += screen
        return screen
    }

    /** The production emoji affordance is what opens the panel for the normal-path tests. */
    private fun openPicker(screen: Screen) {
        screen.composer.emojiButton.performClick()
    }

    // ------------------------------------------------------------------ helpers

    private fun catalog(vararg groups: Pair<String, List<Pair<String, String>>>): String {
        val obj = JSONObject()
        groups.forEach { (group, emojis) ->
            val array = JSONArray()
            emojis.forEach { (name, url) -> array.put(JSONObject().put("name", name).put("url", url)) }
            obj.put(group, array)
        }
        return obj.toString()
    }

    private fun imageUrl(name: String): String = site.url("emoji/$name.png")

    private fun solidPng(color: Int, size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun clickEmoji(screen: Screen, position: Int) {
        val list = grid(screen) ?: throw AssertionError("no grid to click")
        harness.layoutApp()
        val view = (0 until list.childCount).map { list.getChildAt(it) }
            .filter { it.contentDescription?.startsWith(":") == true }
            .getOrNull(position) ?: throw AssertionError("no emoji tile $position to click")
        list.onItemClickListener?.onItemClick(view, list.getChildAdapterPosition(view))
        harness.idle()
    }

    private fun tap(view: View) {
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 4f, 4f, 0)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 4f, 4f, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        harness.idle()
    }

    private fun textOf(view: View): String? =
        descendants(view).filterIsInstance<TextView>().firstOrNull { it.text.isNotEmpty() }?.text?.toString()

    private fun cellFor(screen: Screen, shortcode: String): View? =
        grid(screen)?.let { list ->
            (0 until list.childCount).map { list.getChildAt(it) }
                .firstOrNull { it.contentDescription?.toString() == shortcode }
        }

    private fun imageLoaded(screen: Screen, shortcode: String): Boolean {
        val cell = cellFor(screen, shortcode) ?: return false
        val image = descendants(cell).filterIsInstance<BackupImageView>().firstOrNull() ?: return false
        return image.visibility == View.VISIBLE && image.imageReceiver?.bitmap != null
    }

    private fun fallbackLabel(screen: Screen, shortcode: String): String? {
        val cell = cellFor(screen, shortcode) ?: return null
        return descendants(cell)
            .filterIsInstance<TextView>()
            .firstOrNull { it.visibility == View.VISIBLE && it.text.isNotEmpty() }
            ?.text?.toString()
    }

    private fun boundEmojiNames(screen: Screen): List<String> =
        grid(screen)?.let { list ->
            (0 until list.childCount).map { list.getChildAt(it) }
                .filter { it.bottom > 0 && it.top < list.height }
                .mapNotNull { it.contentDescription?.toString() }
                .filter { it.startsWith(":") }
        } ?: emptyList()

    /** The picker's own pieces, scoped to the panel so the composer's and the panel's lists never mix. */
    private fun grid(screen: Screen): RecyclerListView? =
        descendants(screen.picker).filterIsInstance<RecyclerListView>().singleOrNull()

    private fun empty(screen: Screen): EmptyTextProgressView? =
        descendants(screen.picker).filterIsInstance<EmptyTextProgressView>().singleOrNull()

    private fun tabs(screen: Screen): ScrollSlidingTextTabStrip? =
        descendants(screen.picker).filterIsInstance<ScrollSlidingTextTabStrip>().singleOrNull()

    /** The tab view the strip drew at [index]; its text is the icon the reader sees. */
    private fun tabView(screen: Screen, index: Int): TextView? =
        tabs(screen)?.tabsContainer?.getChildAt(index) as? TextView

    /**
     * Whether the strip's tab at [index] actually paints [color]. The tab strip is drawn on its own
     * (the frame the reader sees), and the tab's own bounds are mapped into it, so a match is the
     * icon the reader sees on that tab - not a stand-in's property. This is the observable that
     * separates an icon from the title text: text pixels are never the emoji's own colour.
     */
    private fun stripShows(screen: Screen, index: Int, color: Int): Boolean {
        val tabs = tabs(screen) ?: return false
        val container = tabs.tabsContainer
        val tab = container.getChildAt(index) ?: return false
        val width = tabs.width
        val height = tabs.height
        if (width <= 0 || height <= 0 || tab.width <= 0 || tab.height <= 0) {
            return false
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            tabs.draw(Canvas(bitmap))
            val left = (container.left + tab.left - tabs.scrollX).coerceIn(0, width - 1)
            val top = (container.top + tab.top).coerceIn(0, height - 1)
            val right = (left + tab.width).coerceAtMost(width)
            val bottom = (top + tab.height).coerceAtMost(height)
            for (y in top until bottom) {
                for (x in left until right) {
                    if (closeColor(bitmap.getPixel(x, y), color)) {
                        return true
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
        return false
    }

    /** A pixel close enough to [expected] to be that flat-colour picture (ignores the alpha edge). */
    private fun closeColor(pixel: Int, expected: Int, tolerance: Int = 40): Boolean {
        if (Color.alpha(pixel) < 200) {
            return false
        }
        return kotlin.math.abs(Color.red(pixel) - Color.red(expected)) <= tolerance &&
            kotlin.math.abs(Color.green(pixel) - Color.green(expected)) <= tolerance &&
            kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) <= tolerance
    }

    private fun search(screen: Screen): SearchField? =
        descendants(screen.picker).filterIsInstance<SearchField>().singleOrNull()

    private fun visible(view: View?): Boolean = view?.visibility == View.VISIBLE

    /** A view's top in window coordinates, the frame the IME inset is measured against. */
    private fun windowTop(view: View): Int = IntArray(2).also { view.getLocationInWindow(it) }[1]

    /** The bottom of the window the panel rides in: the composer host reaches the screen bottom. */
    private fun windowBottom(screen: Screen): Int = windowTop(screen.input) + screen.input.height

    /** The whole shipped tree, so a lookup is against the screen the reader sees. */
    private fun viewTree(): List<View> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return descendants(content)
    }

    private fun descendants(root: View): List<View> {
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

    /** Feeds the holder a real window-insets object, the way the screen's window reports one. */
    private fun applyInsets(holder: WindowInsetsStateHolder, navigationBar: Int, ime: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
            .build()
        holder.setInsets(insets)
    }

    // ------------------------------------------------------------------ private-state access

    /**
     * Drops the kept emoji catalog before the shell boots, so the new-topic screen's picker reads a
     * cold store and goes to the site rather than answering from a catalog an earlier test kept.
     */
    private fun coldEmojiStore() {
        harness.context.getSharedPreferences(EMOJI_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /**
     * Empties the in-memory catalog of a container already built, for a second read in the same
     * screen (the empty-catalog state after the error retry). The store's public surface only ever
     * grows, so the kept value is dropped the way these tests read the picker's private state.
     */
    private fun clearEmojiStore(container: AppContainer) {
        val store = container.emojiCatalogStore
        val field = store.javaClass.getDeclaredField("_groups").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(store) as MutableStateFlow<EmojiGroupsDto>).value = emptyMap()
    }

    /** The fragment's own insets holder; the fragment owns it privately, so reflection reads it. */
    private fun fragmentInsets(fragment: BaseFragment): WindowInsetsStateHolder {
        val field = fragment.javaClass.getDeclaredField("inputInsets").apply { isAccessible = true }
        return field.get(fragment) as WindowInsetsStateHolder
    }

    /**
     * The tile pictures the panel still keeps, read from the panel's own map. The panel holds them
     * privately; a destroyed panel must hold none, so this is the observable a late decode must not
     * change.
     */
    @Suppress("UNCHECKED_CAST")
    private fun keptTileCount(picker: DiscourseEmojiPicker): Int {
        val field = DiscourseEmojiPicker::class.java.getDeclaredField("bitmaps").apply { isAccessible = true }
        val bitmaps = field.get(picker) as Map<String, Bitmap>
        return bitmaps.values.count { bitmap -> !bitmap.isRecycled }
    }

    private fun retain(image: File, name: String) {
        image.copyTo(File(artifactDir(), name), overwrite = true)
        println("emoji-picker: retained ${image.length()} bytes as $name")
    }

    private fun artifactDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "fixtures/discourse").isDirectory) {
                return File(dir, ".amp/in/artifacts/composer-next-wave/emoji-live-fixes").apply { mkdirs() }
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val EMOJI_PREFS = "nativedo.emoji_catalog"
        const val EMPTY_TEXT = "这个站点还没有表情"
        const val ERROR_TEXT = "表情加载失败，点击重试"
    }
}

/**
 * A dispatcher that parks each task handed to it until the test releases it, then runs it on
 * [Dispatchers.IO]. The late-decode test uses it to hold a tile's decode off the main thread while
 * the panel is destroyed, so the post-decode liveness check is exercised deterministically rather
 * than by racing the scheduler.
 */
private class GatedDecodeDispatcher : CoroutineDispatcher() {

    private val delegate = Dispatchers.IO
    private val release = CountDownLatch(1)

    @Volatile
    var decodeStarted = false
        private set

    @Volatile
    var decodeFinished = false
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(
            context,
            Runnable {
                decodeStarted = true
                release.await(10, TimeUnit.SECONDS)
                block.run()
                decodeFinished = true
            },
        )
    }

    fun releaseDecode() {
        release.countDown()
    }
}
