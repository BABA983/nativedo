package io.github.baba983.nativedo.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hold-to-record rules, pinned without a device.
 *
 * <p>The failure modes this exists for, none of which a device microphone is needed to decide: a
 * tap that leaves a recording behind, a hold timer that races a release and starts a capture after
 * the finger is gone, a too-short take that is sent instead of discarded, a slide that is not a
 * cancel, and a system cancel that still delivers.</p>
 */
internal class HoldToRecordGestureTest {

    private val gesture = HoldToRecordGesture(
        holdThresholdMillis = 150L,
        minimumMillis = 700L,
        slideCancelFraction = 0.12f,
    )

    @Test
    fun aQuickTapRecordsNothingAndOnlyHints() {
        assertEquals(HoldToRecordGesture.Event.Armed, gesture.onDown(0L))
        // Released before the hold threshold: this is a tap, not a recording.
        assertEquals(HoldToRecordGesture.Event.ShortTap, gesture.onUp(80L))
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
        // The armed timer fires after the finger is already up: it must not start a capture.
        assertEquals(HoldToRecordGesture.Event.None, gesture.onHoldElapsed(150L))
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
    }

    @Test
    fun aHeldPressRecordsAndARealHoldCompletes() {
        assertEquals(HoldToRecordGesture.Event.Armed, gesture.onDown(0L))
        assertEquals(HoldToRecordGesture.Event.BeginRecording, gesture.onHoldElapsed(150L))
        assertEquals(HoldToRecordGesture.State.Recording, gesture.state)
        assertEquals(2_050L, gesture.elapsedMillis(2_200L))
        assertEquals(HoldToRecordGesture.Event.Complete, gesture.onUp(2_200L))
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
    }

    @Test
    fun aHoldReleasedTooSoonIsDiscardedNotSent() {
        gesture.onDown(0L)
        gesture.onHoldElapsed(150L)
        // 400 ms of audio: below the minimum, so it is thrown away rather than uploaded.
        assertEquals(HoldToRecordGesture.Event.Discard, gesture.onUp(550L))
    }

    @Test
    fun slidingPastTheCancelPointDiscardsAndTheReleaseDeliversNothing() {
        gesture.onDown(0L)
        gesture.onHoldElapsed(150L)
        // A small move is not a cancel.
        assertEquals(HoldToRecordGesture.Event.None, gesture.onMove(0.6f))
        // Far enough left: cancel.
        assertEquals(HoldToRecordGesture.Event.Discard, gesture.onMove(0.05f))
        assertEquals(HoldToRecordGesture.State.Cancelled, gesture.state)
        // The following release must not deliver the discarded take.
        assertEquals(HoldToRecordGesture.Event.None, gesture.onUp(3_000L))
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
    }

    @Test
    fun aSystemCancelStopsALiveCaptureAndDeliversNothing() {
        gesture.onDown(0L)
        gesture.onHoldElapsed(150L)
        assertEquals(HoldToRecordGesture.Event.Discard, gesture.onCancel())
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
        assertEquals(HoldToRecordGesture.Event.None, gesture.onUp(5_000L))
    }

    @Test
    fun aCancelBeforeTheHoldStartsLeavesNothingToRecord() {
        gesture.onDown(0L)
        assertEquals(HoldToRecordGesture.Event.None, gesture.onCancel())
        assertEquals(HoldToRecordGesture.State.Idle, gesture.state)
        assertEquals(HoldToRecordGesture.Event.None, gesture.onHoldElapsed(150L))
    }

    @Test
    fun aSecondPressWhileBusyIsIgnored() {
        gesture.onDown(0L)
        assertTrue(gesture.onDown(50L) == HoldToRecordGesture.Event.None)
        assertEquals(HoldToRecordGesture.State.Holding, gesture.state)
    }
}
