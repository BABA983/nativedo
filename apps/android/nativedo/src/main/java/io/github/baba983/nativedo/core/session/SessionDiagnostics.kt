package io.github.baba983.nativedo.core.session

import android.util.Log

/** Only fixed event names, counters and booleans; never URLs, identities or cookie values. */
internal object SessionDiagnostics {
    fun response(revision: Long, status: Int, accepted: Boolean, evidence: String, route: String,
        operation: String,
        hasToken: Boolean, hasForumSession: Boolean, hasClearance: Boolean, hasRuntime: Boolean,
        setsToken: Boolean, deletesToken: Boolean) {
        runCatching {
            Log.i("NativeDoSession", "event=native_response revision=$revision status=$status accepted=$accepted " +
                "evidence=$evidence route=$route operation=$operation hasToken=$hasToken hasForumSession=$hasForumSession " +
                "hasClearance=$hasClearance hasRuntime=$hasRuntime setsToken=$setsToken deletesToken=$deletesToken")
        }
    }

    fun record(event: String, revision: Long, status: Int = 0, accepted: Boolean = true) {
        runCatching { Log.i("NativeDoSession", "event=$event revision=$revision status=$status accepted=$accepted") }
    }
}
