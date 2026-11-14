package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.model.Post

/**
 * The topic screen's reading window, without a View.
 *
 * <p>It owns where the loaded window sits in the site's stream, the two paging cursors and the
 * reads on their way, the reading mode (只看楼主) and the mode the rows on screen were read in,
 * and the date rule a prepended page shares with the window it joins. The screen binds what this
 * reports; a read that was replaced, a page that failed and a switch that never arrived are
 * decided here rather than by whichever callback happens to run.</p>
 *
 * <p>The screen still owns the rows, their cells and the scroll: this class never sees a View.</p>
 */
class TopicReadingWindow {

    /** The stream index of the first post the loaded window carries. */
    var windowStart: Int = 0
        private set

    /** The stream index after the last post the loaded window carries: the forward cursor. */
    var nextPageStart: Int = 0
        private set

    /**
     * Which window the reads belong to. A head read, a window read and a stream page capture this
     * value when they are asked; a callback whose value has moved on belongs to a window nobody is
     * looking at and must draw nothing.
     */
    var generation: Int = 0
        private set

    var isLoadingWindow: Boolean = false
        private set
    var isLoadingForwardPage: Boolean = false
        private set
    var isLoadingBackwardPage: Boolean = false
        private set

    /** A page that failed latches its own direction until the reader leaves that edge. */
    var isForwardPageFailed: Boolean = false
        private set
    var isBackwardPageFailed: Boolean = false
        private set

    /** Whether the list carries only the original poster's floors. */
    var isOnlyOp: Boolean = false
        private set

    /**
     * The mode the rows on screen were read in. A read that fails leaves those rows exactly as
     * they were, so the mode - and the capsule and overflow row that name it - goes back to this
     * one, even when two switches raced and the last tap is not the state on screen.
     */
    private var rowsOnlyOp: Boolean = false

    /** The reader asked for the filter before the site's answer named the poster. */
    private var pendingOnlyOpToggle: Boolean = false

    /**
     * Starts a window read that replaces the window on screen and returns the generation its
     * callbacks carry. The page flags the replaced reads owned are cleared here: their callbacks
     * will never run to clear them.
     */
    fun beginWindow(): Int {
        val next = supersedeWindow()
        isLoadingWindow = true
        return next
    }

    /**
     * Drops every read that belonged to the window on screen without starting another. The search
     * owns the list in that interval, so nothing from the window may land under it.
     */
    fun supersedeWindow(): Int {
        generation++
        isLoadingWindow = false
        isLoadingForwardPage = false
        isLoadingBackwardPage = false
        isForwardPageFailed = false
        isBackwardPageFailed = false
        return generation
    }

    fun isCurrent(generation: Int): Boolean = generation == this.generation

    /** The window's answer arrived and its rows are about to be bound in the mode they were read in. */
    fun windowShown() {
        isLoadingWindow = false
        rowsOnlyOp = isOnlyOp
    }

    /**
     * A window read did not arrive. The rows on screen stay, so the reading mode goes back to the
     * one those rows were read in.
     *
     * @return true when the mode changed and the row and the capsule must be redrawn.
     */
    fun failWindowRead(): Boolean {
        isLoadingWindow = false
        if (isOnlyOp == rowsOnlyOp) return false
        isOnlyOp = rowsOnlyOp
        return true
    }

    /**
     * The window [loaded] opens: where it starts in [stream] and where the page after it begins.
     * A post the stream does not name keeps the old fallback - the start at the head, the next
     * page after the rows that are here.
     */
    fun bindWindow(stream: List<Long>, loaded: List<Post>) {
        if (loaded.isEmpty()) {
            windowStart = 0
            nextPageStart = 0
            return
        }
        val first = stream.indexOf(loaded.first().id)
        windowStart = if (first >= 0) first else 0
        val last = stream.indexOf(loaded.last().id)
        nextPageStart = if (last >= 0) last + 1 else minOf(loaded.size, stream.size)
    }

    /** A new window's rows are bound: no page from the window before is still on its way. */
    fun rebindPages() {
        isLoadingForwardPage = false
        isLoadingBackwardPage = false
    }

    // ---- stream paging ------------------------------------------------------

    fun canStartForwardPage(streamSize: Int): Boolean =
        !isLoadingForwardPage && !isForwardPageFailed && nextPageStart < streamSize

    fun startForwardPage() {
        isLoadingForwardPage = true
    }

    /**
     * The forward page arrived. [generation] is the window it was asked for: a page from a window
     * that was replaced must not move the new window's cursor.
     */
    fun finishForwardPage(generation: Int, cursor: Int): Boolean {
        if (!isCurrent(generation)) return false
        isLoadingForwardPage = false
        isForwardPageFailed = false
        nextPageStart = cursor
        return true
    }

    fun failForwardPage(generation: Int) {
        if (!isCurrent(generation)) return
        isLoadingForwardPage = false
        isForwardPageFailed = true
    }

    fun clearForwardPageFailure() {
        isForwardPageFailed = false
    }

    fun canStartBackwardPage(streamSize: Int): Boolean =
        !isLoadingBackwardPage && !isBackwardPageFailed &&
            windowStart > 0 && windowStart <= streamSize

    fun startBackwardPage() {
        isLoadingBackwardPage = true
    }

    /** The slice above the window arrived at [start]; it is prepended at that stream index. */
    fun finishBackwardPage(generation: Int, start: Int): Boolean {
        if (!isCurrent(generation)) return false
        isLoadingBackwardPage = false
        isBackwardPageFailed = false
        windowStart = start
        return true
    }

    fun failBackwardPage(generation: Int) {
        if (!isCurrent(generation)) return
        isLoadingBackwardPage = false
        isBackwardPageFailed = true
    }

    fun clearBackwardPageFailure() {
        isBackwardPageFailed = false
    }

    /** The stream the window ends on: no page follows, so a read-on release may navigate. */
    fun isAtStreamEnd(streamSize: Int): Boolean = nextPageStart >= streamSize

    fun hasPageInFlight(): Boolean =
        isLoadingWindow || isLoadingForwardPage || isLoadingBackwardPage

    // ---- 只看楼主 -------------------------------------------------------------

    /** The overflow's 只看楼主 row: take the mode optimistically; its read changes the rows. */
    fun beginOnlyOpSwitch(only: Boolean) {
        isOnlyOp = only
    }

    /** A tap before the site named the poster: remember it for the answer that can name one. */
    fun requestOnlyOpBeforeAuthor() {
        pendingOnlyOpToggle = true
    }

    /** Consume a pending request; true when the answer should now take the mode. */
    fun takePendingOnlyOpToggle(): Boolean {
        if (!pendingOnlyOpToggle) return false
        pendingOnlyOpToggle = false
        return true
    }

    // ---- navigation and date rows -------------------------------------------

    /**
     * The 回到一楼 control: a window that starts mid-stream does not carry floor 1, so it reads
     * it; one that starts at the head only scrolls to row 0.
     */
    fun needsFirstFloorRead(): Boolean = windowStart > 0

    /**
     * The day a floor's date row names: the day's own midnight in the reader's calendar, or -1
     * when the site gave no usable timestamp. [epochSeconds] is the floor's own time.
     */
    fun dayKey(epochSeconds: Long): Int {
        if (epochSeconds <= 0) return -1
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = epochSeconds * 1000L
        return calendar.get(java.util.Calendar.YEAR) * 1000 +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** Whether a floor opens a day of its own: a chat writes a date row above a day's first floor. */
    fun opensDateRow(previousDay: Int, floorDay: Int): Boolean =
        floorDay >= 0 && floorDay != previousDay

    /**
     * Whether a prepended page leaves the window's opening date row a duplicate. The page's last
     * date row already names the day the window opens on, so the window's own row for that day
     * goes and the seam carries one date row.
     */
    fun dropsOpeningDateRow(pageLastDay: Int, windowOpeningDay: Int): Boolean =
        pageLastDay >= 0 && pageLastDay == windowOpeningDay
}
