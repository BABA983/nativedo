package io.github.baba983.nativedo.auth.device

import android.util.Log

/**
 * Fixed event names and server statuses for `adb logcat -s NativeDoDeviceLogin`. It never records
 * the User API Key, the device code, cookies, or request bodies. The `user_code` may appear: it is
 * the short-lived code the reader is meant to see, and it is useless without a signed-in approval.
 */
internal object DeviceLoginDiagnostics {

    private const val TAG = "NativeDoDeviceLogin"

    fun record(event: String, vararg fields: Pair<String, Any?>) {
        runCatching {
            val tail = fields
                .filter { it.second != null }
                .joinToString(" ") { (name, value) -> "$name=$value" }
            Log.i(TAG, if (tail.isEmpty()) "event=$event" else "event=$event $tail")
        }
    }
}
