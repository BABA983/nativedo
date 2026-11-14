package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading window's rules the shipped screen cannot drive deterministically: two reads whose
 * answers arrive out of order, a filter switch that fails after another tap, the date row a
 * prepend leaves at its seam, and the first floor a mid-stream window does not carry.
 *
 * <p>These are isolation rules rather than a second copy of the screen's behaviour: the screen's
 * own flows (paging, the filter, the prepend) are driven end to end by [TopicHistoryPagingTest],
 * [TopicOnlyOpTest] and [TopicPageDownTest].</p>
 */
internal class TopicReadingWindowTest {

    /**
     * The reader taps 只看楼主 and then 显示全部 before either answer lands, and the second read
     * fails. The rows on screen never changed, so the mode must be the one they were read in - the
     * last tap's "before" state is not that mode.
     */
    @Test
    fun twoSwitchesBeforeTheAnswerLeaveTheModeTheRowsWereReadIn() {
        val reading = TopicReadingWindow()
        reading.beginWindow()
        reading.windowShown() // the whole topic is on screen

        reading.beginOnlyOpSwitch(true)
        reading.beginWindow()
        reading.beginOnlyOpSwitch(false) // a second tap before the filtered answer
        reading.beginWindow()

        reading.failWindowRead()

        assertFalse(
            "the rows are the whole topic, so a failed read cannot leave the filter on",
            reading.isOnlyOp,
        )
    }

    /**
     * The same race on the first read: 只看楼主 is tapped before the site named the poster, the
     * head answer takes the mode, and 显示全部 is tapped before the filtered window arrives. The
     * failed filtered read leaves the head's unfiltered rows on screen.
     */
    @Test
    fun aPendingFilterAnsweredByTheHeadIsPutBackWhenItsReadFails() {
        val reading = TopicReadingWindow()
        reading.requestOnlyOpBeforeAuthor()
        reading.beginWindow()
        reading.windowShown() // the head answer, still unfiltered
        assertTrue("the head answer consumes the pending tap", reading.takePendingOnlyOpToggle())

        reading.beginOnlyOpSwitch(true)
        reading.beginWindow()
        reading.beginOnlyOpSwitch(false)
        reading.beginWindow()

        reading.failWindowRead()

        assertFalse(
            "the failed filtered read leaves the unfiltered head rows on screen",
            reading.isOnlyOp,
        )
    }

    /**
     * A prepended page whose last date row is the day the window already opens on. The window's
     * own opening row for that day is the duplicate and must go; a page that ends on another day
     * keeps both rows.
     */
    @Test
    fun aPageEndingOnTheWindowsOpeningDayKeepsOneDateRow() {
        val reading = TopicReadingWindow()
        val day = 2024 * 1000 + 1

        assertTrue(
            "the page's last date row is the window's opening day",
            reading.dropsOpeningDateRow(pageLastDay = day, windowOpeningDay = day),
        )
        assertFalse(
            "a page ending on another day keeps the window's own row",
            reading.dropsOpeningDateRow(pageLastDay = day, windowOpeningDay = day + 1),
        )
        assertFalse(
            "a page with no usable date row leaves the window's row alone",
            reading.dropsOpeningDateRow(pageLastDay = -1, windowOpeningDay = day),
        )
    }

    /**
     * 回到一楼: a window read from the middle of the stream does not carry floor 1, so the control
     * must read it; only a window that already starts at the head is a plain scroll.
     */
    @Test
    fun backToTheFirstFloorReadsItWhenTheWindowStartsMidStream() {
        val reading = TopicReadingWindow()
        assertFalse("nothing loaded yet, so row 0 is floor 1", reading.needsFirstFloorRead())

        val generation = reading.beginWindow()
        reading.bindWindow((1L..30L).toList(), posts(11..30))
        assertTrue(
            "a window that starts at floor 11 does not carry floor 1",
            reading.needsFirstFloorRead(),
        )

        reading.finishBackwardPage(generation, 0)
        assertFalse("the head is loaded, so row 0 is floor 1 again", reading.needsFirstFloorRead())
    }

    /**
     * A page asked for by a window that was replaced arrives late. It must not move the new
     * window's forward cursor, even though its own request had a valid cursor when it went out.
     */
    @Test
    fun aPageFromAReplacedWindowCannotMoveTheNewWindowsCursor() {
        val reading = TopicReadingWindow()
        val replaced = reading.beginWindow()
        reading.bindWindow((1L..100L).toList(), posts(1..20))
        assertTrue("the first window may page forward", reading.canStartForwardPage(100))
        reading.startForwardPage()

        val current = reading.beginWindow()
        reading.bindWindow((1L..100L).toList(), posts(41..60))
        assertEquals("the new window starts at floor 41", 40, reading.windowStart)
        assertEquals("and its forward cursor follows floor 60", 60, reading.nextPageStart)

        assertFalse(
            "the page belonged to the window that was replaced",
            reading.finishForwardPage(replaced, 40),
        )
        assertEquals("the new window's cursor did not move", 60, reading.nextPageStart)
        assertTrue(reading.isCurrent(current))
    }

    private fun posts(floors: IntRange): List<Post> = floors.map { floor ->
        Post(
            id = floor.toLong(),
            postNumber = floor,
            authorUsername = "reader$floor",
            cookedHtml = "<p>$floor</p>",
        )
    }
}
