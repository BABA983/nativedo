package io.github.baba983.nativedo.core.messagebus

import android.util.Log

/**
 * What the message bus did, in fixed event names, counts and status codes only - never URLs,
 * identities or cookie values. A device pass reads these lines out of logcat
 * (`adb logcat -s NativeDoMessageBus`), and the wrapper keeps a plain JVM test without an Android
 * runtime from failing on the call.
 */
internal object MessageBusDiagnostics {

    private const val TAG = "NativeDoMessageBus"

    fun event(name: String, detail: String = "") {
        runCatching { Log.i(TAG, if (detail.isEmpty()) "event=$name" else "event=$name $detail") }
    }

    fun poll(outcome: MessageBusPollOutcome, elapsedMillis: Long, waitMillis: Long, failures: Int) {
        val name = when (outcome) {
            MessageBusPollOutcome.Idle -> "idle"
            MessageBusPollOutcome.Delivered -> "delivered"
            MessageBusPollOutcome.Empty -> "empty"
            is MessageBusPollOutcome.RateLimited -> "rate_limited"
            is MessageBusPollOutcome.Failed -> "failed"
        }
        val status = when (outcome) {
            is MessageBusPollOutcome.Failed -> outcome.statusCode ?: 0
            else -> 200
        }
        event(
            "poll",
            "outcome=$name status=$status elapsed=${elapsedMillis}ms wait=${waitMillis}ms failures=$failures",
        )
    }

    /** What one poll answer's transport looked like; the site's own framing is what is observed. */
    fun response(status: Int, contentLength: Long?, chunked: Boolean) {
        event("response", "status=$status length=${contentLength ?: -1} chunked=$chunked")
    }
}
