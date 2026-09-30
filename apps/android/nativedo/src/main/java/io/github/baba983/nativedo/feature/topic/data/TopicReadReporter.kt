package io.github.baba983.nativedo.feature.topic.data

import android.os.SystemClock
import io.github.baba983.nativedo.core.model.TopicDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the clock is sampled: Discourse's own screen-track ticks once a second. */
private const val TICK_MILLIS = 1_000L

/**
 * How long a reader's last scroll keeps the clock running. Discourse stops counting time read when
 * the page has not been scrolled for three minutes ({@code PAUSE_UNLESS_SCROLLED}), because a topic
 * left open on a desk is not a topic being read.
 */
private const val INTERACTION_WINDOW_MILLIS = 3 * 60 * 1000L

/** One floor stops counting for this visit after six minutes of dwell (Discourse's own ceiling). */
private const val MAX_FLOOR_MILLIS = 6 * 60 * 1000L

/** The site clamps one report to a minute of topic time and a minute per floor. */
private const val MAX_BATCH_MILLIS = 60 * 1000L

/** How long a reader may keep reading before the dwell so far is sent on. */
private const val FLUSH_INTERVAL_MILLIS = 60 * 1000L

/** How long the reader has to stop moving before the floors reached so far are reported. */
private const val REPORT_DEBOUNCE_MILLIS = 250L

/**
 * The reader's place in one topic, and the report of it the site asks for.
 *
 * <p>Discourse learns both things from one request, {@code POST /topics/timings}: which floors the
 * reader got to and how long they were on screen. The site adds what it is sent to the row it keeps
 * per floor, so each report carries the time since the last one, and it advances the reader's
 * {@code last_read_post_number} to the highest floor the report names - which is what a topic list's
 * unread counts are made of.</p>
 *
 * <p>The time is the reader's real time, as Discourse's own screen-track measures it: sampled once a
 * second, and only while the screen is in front, a floor is in the viewport, and the reader has
 * scrolled within the last three minutes. This matters beyond the report being honest - the site's
 * trust levels count reading time ({@code user_stats.time_read}), so a client that reports a nominal
 * second per floor makes its readers look slower than they are.</p>
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
    /** The clock the dwell is measured against; tests drive it instead of waiting in real time. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    /** The topic's last floor; nothing past it is ever reported as read. */
    private var highestFloor = 0
    /** The highest floor the reader has reached (or had already reached before this screen). */
    private var reachedFloor = 0
    /** The highest floor the site is known to have been told about. */
    private var reportedFloor = 0
    private var reportJob: Job? = null
    private var ticker: Job? = null
    /** Whether a report is on its way: one at a time, and it is the one that carries the position. */
    private var running = false
    private var started = false
    /** Whether the screen is in front; a screen in the background is not being read. */
    private var foreground = false
    private var lastTickAt = 0L
    private var lastFlushAt = 0L
    /** The reader's last scroll, which is what keeps the clock running. */
    private var lastActivityAt = 0L
    /** The floors the viewport holds, by the site's own numbers for them. */
    private val floorsOnScreen = mutableSetOf<Int>()
    /** Dwell accumulated since the last report, per floor; each report carries the difference. */
    private val floorMillis = mutableMapOf<Int, Long>()
    /** Dwell of the whole visit, per floor, for the six-minute ceiling. */
    private val floorVisitMillis = mutableMapOf<Int, Long>()
    /** Foreground time since the last report. */
    private var topicMillis = 0L

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
     * Whether the screen is in front. Going back starts nothing by itself - the clock re-arms at the
     * moment the reader returns, so the time the app spent in the background is never counted.
     */
    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) {
            return
        }
        this.foreground = foreground
        if (foreground) {
            val now = clock()
            lastTickAt = now
            lastActivityAt = now
            lastFlushAt = now
            startTicker()
        } else {
            stopTicker()
            flush()
        }
    }

    /** The floors the viewport holds now: only these are being read. */
    fun visibleFloors(floors: Collection<Int>) {
        floorsOnScreen.clear()
        floorsOnScreen.addAll(floors)
    }

    /** The reader moved: scrolling is what says a screen is still being read. */
    fun noteActivity() {
        lastActivityAt = clock()
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

    /** The reader is leaving: what they reached and read is owed to the site now, without the wait. */
    fun flush() {
        if (hasSomethingToReport()) {
            schedule(0L)
        }
    }

    /** Somebody other than the reader is done with this screen: the clock stops and the rest goes. */
    fun stop() {
        stopTicker()
        flush()
    }

    /** Whether the site has not been told about some floor, some dwell, or both. */
    private fun hasSomethingToReport(): Boolean =
        reachedFloor > reportedFloor || topicMillis > 0 || floorMillis.values.any { it > 0 }

    /**
     * One tick of the clock: the time since the last one is credited to the topic and to every floor
     * the viewport holds - but only when the screen is in front and the reader has scrolled recently
     * enough, which is the three gates Discourse's own screen-track has.
     */
    private fun tick() {
        val now = clock()
        val diff = now - lastTickAt
        lastTickAt = now
        if (!foreground || diff <= 0) {
            return
        }
        if (now - lastActivityAt > INTERACTION_WINDOW_MILLIS) {
            return
        }
        topicMillis += diff
        for (floor in floorsOnScreen) {
            val visit = floorVisitMillis[floor] ?: 0
            if (visit >= MAX_FLOOR_MILLIS) {
                continue
            }
            floorMillis[floor] = (floorMillis[floor] ?: 0) + diff
            floorVisitMillis[floor] = (visit + diff).coerceAtMost(MAX_FLOOR_MILLIS)
        }
        if (!running && now - lastFlushAt >= FLUSH_INTERVAL_MILLIS) {
            schedule(0L)
        }
    }

    private fun startTicker() {
        if (ticker != null) {
            return
        }
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                tick()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
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
                    if (hasSomethingToReport()) {
                        schedule(0L)
                    }
                }
            } finally {
                running = false
            }
        }
    }

    /**
     * Reports what the site has not been told about yet - the floors reached, and the dwell of the
     * ones that were on screen - and whether that is all of them: the first report that fails leaves
     * everything where it is, and the next attempt carries it.
     */
    private suspend fun report(): Boolean {
        while (true) {
            val target = reachedFloor
            val reached = ((reportedFloor + 1)..target).toList()
            val dwell = floorMillis.filterValues { it > 0 }
            if (reached.isEmpty() && dwell.isEmpty() && topicMillis <= 0) {
                return true
            }
            val timings = LinkedHashMap<Int, Long>()
            dwell.forEach { (floor, millis) -> timings[floor] = millis }
            // A floor the viewport passed is a floor the reader saw even when the clock did not tick
            // while it was there. The site advances the reader's place to the highest floor the
            // report names, so one that is not named would stay unread for good; naming it with the
            // smallest dwell a report can carry (which is what Discourse's own client does for the
            // floors a gap skipped) is what keeps the site's counts equal to what was on screen.
            for (floor in reached) {
                timings.putIfAbsent(floor, 1L)
            }
            val sent = timings.mapValues { (_, millis) -> millis.coerceAtMost(MAX_BATCH_MILLIS) }
            val sentTopicMillis = topicMillis.coerceAtMost(MAX_BATCH_MILLIS)
            val result = repository.markTopicRead(
                topicId = topicId,
                topicTimeMillis = sentTopicMillis,
                postTimings = sent.mapValues { (_, millis) -> millis.toInt() },
            )
            if (result.isFailure) {
                return false
            }
            lastFlushAt = clock()
            // What was sent is consumed; a slice the site's own ceiling cut keeps the rest for the
            // next report rather than dropping time the reader really spent.
            topicMillis -= sentTopicMillis
            for ((floor, millis) in sent) {
                val left = (floorMillis[floor] ?: 0) - millis
                if (left > 0) {
                    floorMillis[floor] = left
                } else {
                    floorMillis.remove(floor)
                }
            }
            reportedFloor = maxOf(reportedFloor, target)
        }
    }

    /** Whether the reader has read the whole topic, which is what clears a list row's badge. */
    fun readToTheEnd(): Boolean = highestFloor > 0 && reachedFloor >= highestFloor
}
