package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.TopicDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How long one floor counts as read for.
 *
 * <p>The site advances a reader's position on any positive dwell and takes the time from the report,
 * while what this screen knows is *which* floors the viewport passed - not how long the reader
 * looked at each. The nominal time is this client's report of that fact, and it is the same number
 * whatever the reader is reading.</p>
 */
private const val READ_MILLIS_PER_FLOOR = 1_000

/** How long the reader has to stop moving before the floors reached so far are reported. */
private const val REPORT_DEBOUNCE_MILLIS = 250L

/**
 * The reader's place in one topic, and the report of it the site asks for.
 *
 * <p>Discourse learns where a reader got to from {@code POST /topics/timings} - the dwell time of
 * the floors that were read. A topic screen is the thing that knows which floors those are, so the
 * report goes out from here as the reader moves on, debounced: a report per floor would be a request
 * per scroll, and a flick through a topic is not a floor-by-floor read. Leaving the topic reports
 * immediately, because that is when the reader stopped reading and the site's answer - which is what
 * a topic list's unread counts are made of - is about to be read again.</p>
 *
 * <p>The position is kept on the device as well as sent: a report that does not get through must not
 * cost the reader their place, and a topic this reader has been in opens where they left it whether
 * or not the site has caught up.</p>
 */
class TopicReadReporter(
    private val topicId: Long,
    private val repository: TopicRepository,
    private val store: TopicReadPositionStore,
    private val key: TopicReadPositionKey?,
    private val scope: CoroutineScope,
) {
    /** The topic's last floor; nothing past it is ever reported as read. */
    private var highestFloor = 0
    /** The highest floor the reader has reached (or had already reached before this screen). */
    private var reachedFloor = 0
    /** The highest floor the site is known to have been told about. */
    private var reportedFloor = 0
    private var reportJob: Job? = null
    /** Whether a report is on its way: one at a time, and it is the one that carries the position. */
    private var running = false
    private var started = false

    /**
     * Takes the site's own answer as the starting point, and returns the floor the topic opens at.
     *
     * <p>Two things have been said about where the reader is: the site's {@code
     * last_read_post_number} - where their reports got it to - and this device's own record, which
     * is further whenever a report did not get through. The reader's place is the further one, and
     * the difference is owed to the site: opening a topic the site has fallen behind on reports the
     * floors it missed, which is what makes its unread counts agree with the reader's own memory.
     */
    fun start(detail: TopicDetail): Int {
        if (!started) {
            started = true
            highestFloor = detail.highestPostNumber
            reportedFloor = detail.lastReadPostNumber.coerceAtLeast(0)
            reachedFloor = maxOf(reportedFloor, key?.let(store::load) ?: 0)
                .coerceAtMost(highestFloor.takeIf { it > 0 } ?: Int.MAX_VALUE)
            if (reachedFloor > reportedFloor) {
                schedule(0L)
            }
        }
        return reachedFloor
    }

    /**
     * The reader has reached [postNumber]: the viewport is past that floor. Monotonic - a reader who
     * scrolls back up has not un-read anything - and never past the topic's last floor.
     */
    fun advanceTo(postNumber: Int) {
        val floor = postNumber.coerceAtMost(
            highestFloor.takeIf { it > 0 } ?: postNumber,
        )
        if (floor <= reachedFloor) {
            return
        }
        reachedFloor = floor
        key?.let { store.save(it, floor) }
        schedule(REPORT_DEBOUNCE_MILLIS)
    }

    /** The reader is leaving: what they reached is owed to the site now, without the usual wait. */
    fun flush() {
        if (reachedFloor > reportedFloor) {
            schedule(0L)
        }
    }

    /**
     * Starts the one report this reporter runs at a time.
     *
     * <p>A position reached while a report is on its way is not a reason to start a second one: the
     * report that is running re-reads the position after each request, and it only stops when there
     * is nothing left to carry - which it checks once more after it has given up the runner, because
     * until then a new position looks like "a report is already running".</p>
     */
    private fun schedule(delayMillis: Long) {
        if (running) {
            return
        }
        running = true
        reportJob = scope.launch {
            try {
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
                if (report()) {
                    running = false
                    if (reachedFloor > reportedFloor) {
                        schedule(0L)
                    }
                }
            } finally {
                running = false
            }
        }
    }

    /**
     * Reports the floors the site has not been told about yet, and whether that is all of them: the
     * first report that fails leaves the position where it is, and the next attempt carries it.
     */
    private suspend fun report(): Boolean {
        while (true) {
            val target = reachedFloor
            if (target <= reportedFloor) {
                return true
            }
            val floors = (reportedFloor + 1)..target
            val result = repository.markTopicRead(
                topicId = topicId,
                topicTimeMillis = floors.count().toLong() * READ_MILLIS_PER_FLOOR,
                postTimings = floors.associateWith { READ_MILLIS_PER_FLOOR },
            )
            if (result.isFailure) {
                return false
            }
            reportedFloor = target
        }
    }

    /** Whether the reader has read the whole topic, which is what clears a list row's badge. */
    fun readToTheEnd(): Boolean = highestFloor > 0 && reachedFloor >= highestFloor
}
