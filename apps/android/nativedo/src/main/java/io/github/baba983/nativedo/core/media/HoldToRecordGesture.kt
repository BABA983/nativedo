package io.github.baba983.nativedo.core.media

/**
 * The hold-to-record gesture as a pure state machine, so its rules can be pinned without a device.
 *
 * <p>The failure modes this exists for, none of which a device microphone is needed to decide:</p>
 * <ul>
 *   <li>a <em>tap</em> must not leave a recording behind - the press has to survive
 *       [holdThresholdMillis] before capturing starts, so a quick tap is only "hold to record"
 *       feedback;</li>
 *   <li>a release that arrives <em>before</em> the hold timer fires must not start a capture at all
 *       (the timer races the finger, and the state is what decides, not the timer);</li>
 *   <li>a hold released under [minimumMillis] is too short to keep and is discarded, not sent;</li>
 *   <li>sliding the finger past [slideCancelFraction] cancels, and the following release delivers
 *       nothing;</li>
 *   <li>an <code>ACTION_CANCEL</code> (a parent stealing the gesture, a call) cancels a live capture
 *       and never delivers it.</li>
 * </ul>
 *
 * <p>The host owns the clock and the recorder: it arms a timer for [holdThresholdMillis] on
 * [onDown], calls [onHoldElapsed] when it fires, and feeds [onUp]/[onMove]/[onCancel] from the touch
 * stream. The machine only answers what the host should do next.</p>
 */
class HoldToRecordGesture(
    private val holdThresholdMillis: Long = DEFAULT_HOLD_THRESHOLD_MILLIS,
    private val minimumMillis: Long = DEFAULT_MINIMUM_MILLIS,
    private val slideCancelFraction: Float = DEFAULT_SLIDE_CANCEL_FRACTION,
) {

    /** The gesture's own state; the host never sets it. */
    enum class State { Idle, Holding, Recording, Cancelled }

    /** What the host should do after an input. */
    sealed interface Event {
        /** The finger is down: arm the hold. The host schedules [onHoldElapsed] after the threshold. */
        data object Armed : Event

        /** The hold threshold passed while still down: start capturing. */
        data object BeginRecording : Event

        /** The finger lifted before the threshold: nothing was recorded, show the hint. */
        data object ShortTap : Event

        /** A real hold was released: finish and deliver the recording. */
        data object Complete : Event

        /** The capture must be discarded: too short, slid to cancel, or the gesture was cancelled. */
        data object Discard : Event

        /** Nothing to do. */
        data object None : Event
    }

    var state: State = State.Idle
        private set

    private var pressedAt = 0L
    private var startedAt = 0L

    /** How long the current capture has been running at [nowMillis], or 0 when not capturing. */
    fun elapsedMillis(nowMillis: Long): Long =
        if (state == State.Recording) (nowMillis - startedAt).coerceAtLeast(0L) else 0L

    fun onDown(nowMillis: Long): Event {
        if (state != State.Idle) return Event.None
        state = State.Holding
        pressedAt = nowMillis
        return Event.Armed
    }

    fun onHoldElapsed(nowMillis: Long): Event {
        if (state != State.Holding) return Event.None
        state = State.Recording
        startedAt = nowMillis
        return Event.BeginRecording
    }

    /**
     * A move during the hold. [slideRemaining] is 1 while the finger has not moved and 0 when it has
     * slid far enough to cancel; the host normalizes it. A move only matters once capturing.
     */
    fun onMove(slideRemaining: Float): Event {
        if (state != State.Recording) return Event.None
        if (slideRemaining <= slideCancelFraction) {
            state = State.Cancelled
            return Event.Discard
        }
        return Event.None
    }

    fun onUp(nowMillis: Long): Event = when (state) {
        State.Holding -> {
            state = State.Idle
            Event.ShortTap
        }
        State.Recording -> {
            val elapsed = (nowMillis - startedAt).coerceAtLeast(0L)
            state = State.Idle
            if (elapsed >= minimumMillis) Event.Complete else Event.Discard
        }
        State.Cancelled -> {
            state = State.Idle
            Event.None
        }
        State.Idle -> Event.None
    }

    fun onCancel(): Event = when (state) {
        State.Holding -> {
            state = State.Idle
            Event.None
        }
        State.Recording -> {
            state = State.Idle
            Event.Discard
        }
        else -> {
            state = State.Idle
            Event.None
        }
    }

    /** The hold threshold a host arms its timer with. */
    val holdThreshold: Long get() = holdThresholdMillis

    companion object {
        /** The same delay upstream's own record runnable uses before a hold becomes a recording. */
        const val DEFAULT_HOLD_THRESHOLD_MILLIS = 150L

        /** Shorter than this and the recording is discarded; long enough that a hold is not a tap. */
        const val DEFAULT_MINIMUM_MILLIS = 700L

        /** The normalized slide below which a horizontal drag cancels the recording. */
        const val DEFAULT_SLIDE_CANCEL_FRACTION = 0.12f
    }
}
