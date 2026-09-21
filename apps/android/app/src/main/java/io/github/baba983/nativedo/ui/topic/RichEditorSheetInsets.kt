/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic

/**
 * The editor sheet's WindowInsets -> page-insets arithmetic, kept pure so it can be exercised
 * without a device.
 *
 * The editor page ([org.telegram.ui.iv.RichEditorHostLayout]) lays its toolbar and list against
 * two numbers - a top inset (a status bar the page is under) and a bottom inset (a navigation bar
 * or a keyboard that covers the page's bottom) - and [RichEditorSheet] feeds them from the
 * window's real insets instead of a hardcoded `AndroidUtilities.navigationBarHeight`. What makes
 * the arithmetic non-trivial is that the same system inset can arrive in two shapes:
 *
 *  - the window may have been resized for the IME (adjustResize), so the page's bottom edge already
 *    rides above the keyboard and must *not* be lifted again, or
 *  - the keyboard may simply overlay the window, so the part of the page the keyboard covers has
 *    to be lifted by exactly that overlap.
 *
 * Both are the same question - "how much of the page's bottom edge does the system actually cover"
 * - so this computes the overlap from the page's own window-relative edges and the insets, which
 * cannot double-count: a resized window's bottom edge is already above the keyboard, so its
 * overlap is 0. The top mirrors it: the status bar only adds inset where the page actually reaches
 * under it, and the sheet's own base top is never shrunk (pass it as [minTopInset]).
 */
internal object RichEditorSheetInsets {

    /** The pair [org.telegram.ui.iv.RichEditorHostLayout] lays the page against. */
    data class Values(val top: Int, val bottom: Int)

    /**
     * @param hostTop      the page's top edge in window coordinates (`getLocationInWindow()[1]`)
     * @param hostBottom   the page's bottom edge in window coordinates (`hostTop` + the page height)
     * @param windowHeight the window's height in window coordinates
     * @param systemBarsTop    the status bar / cutout inset
     * @param systemBarsBottom the navigation bar inset
     * @param imeBottom    the IME inset (0 while the keyboard is hidden)
     * @param minTopInset  the page's own base top (the sheet's handle gap); never removed
     */
    fun compute(
        hostTop: Int,
        hostBottom: Int,
        windowHeight: Int,
        systemBarsTop: Int,
        systemBarsBottom: Int,
        imeBottom: Int,
        minTopInset: Int,
    ): Values {
        val top = maxOf(minTopInset, maxOf(0, systemBarsTop - hostTop))
        // While the keyboard is up it is what covers the bottom (it sits over the navigation bar);
        // with it hidden the navigation bar is. In both cases only the part of the page's bottom
        // edge the system actually covers is reported, so a resized window reports 0.
        val coverHeight = if (imeBottom > 0) imeBottom else systemBarsBottom
        val bottom = maxOf(0, hostBottom - (windowHeight - coverHeight))
        return Values(top, bottom)
    }
}
