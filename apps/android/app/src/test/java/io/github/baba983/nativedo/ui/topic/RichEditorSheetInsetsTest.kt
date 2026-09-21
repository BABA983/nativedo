package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The editor sheet's insets arithmetic: the page has to know how much of its bottom edge the system
 * actually covers, and the two ways the same keyboard can arrive (the window resized for it, or the
 * keyboard overlaying the window) must not both lift the toolbar. These pin the pure computation
 * [RichEditorSheet] feeds from the window's real WindowInsets.
 */
class RichEditorSheetInsetsTest {

    @Test
    fun aKeyboardOverTheWindowLiftsByTheKeyboard() {
        val values = RichEditorSheetInsets.compute(
            hostTop = 0,
            hostBottom = 2000,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 48,
            imeBottom = 800,
            minTopInset = 8,
        )

        assertThat(values.bottom).isEqualTo(800)
        // the status bar is uncovered (the page starts at the window's top)
        assertThat(values.top).isEqualTo(24)
    }

    @Test
    fun aWindowResizedForTheKeyboardIsNotLiftedAgain() {
        val values = RichEditorSheetInsets.compute(
            hostTop = 24,
            hostBottom = 1200,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 48,
            imeBottom = 800,
            minTopInset = 8,
        )

        // the page's bottom edge already ends above the keyboard
        assertThat(values.bottom).isEqualTo(0)
    }

    @Test
    fun withNoKeyboardTheNavigationBarIsTheCover() {
        val values = RichEditorSheetInsets.compute(
            hostTop = 24,
            hostBottom = 2000,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 48,
            imeBottom = 0,
            minTopInset = 8,
        )

        assertThat(values.bottom).isEqualTo(48)
        // the page already starts below the status bar, so it keeps its own base top
        assertThat(values.top).isEqualTo(8)
    }

    @Test
    fun aPageUnderTheStatusBarAddsItToItsBaseTop() {
        val values = RichEditorSheetInsets.compute(
            hostTop = 0,
            hostBottom = 2000,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 0,
            imeBottom = 0,
            minTopInset = 8,
        )

        assertThat(values.top).isEqualTo(24)
        assertThat(values.bottom).isEqualTo(0)
    }

    @Test
    fun onlyTheCoveredPartOfThePagesBottomEdgeIsReported() {
        val values = RichEditorSheetInsets.compute(
            hostTop = 24,
            hostBottom = 1900,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 48,
            imeBottom = 300,
            minTopInset = 8,
        )

        // the keyboard starts at 2000 - 300; the page's bottom edge is only 200px inside it
        assertThat(values.bottom).isEqualTo(200)
    }

    @Test
    fun theSheetsOwnDp8BottomGapIsNotASystemInset() {
        // the container keeps Telegram's dp(8) bottom padding (`BottomSheet.java`) inside the
        // window, so a page that ends there has nothing to lift for the system
        val values = RichEditorSheetInsets.compute(
            hostTop = 24,
            hostBottom = 1960,
            windowHeight = 2000,
            systemBarsTop = 24,
            systemBarsBottom = 0,
            imeBottom = 0,
            minTopInset = 8,
        )

        assertThat(values.bottom).isEqualTo(0)
        assertThat(values.top).isEqualTo(8)
    }
}
