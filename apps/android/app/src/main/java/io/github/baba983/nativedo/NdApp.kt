package io.github.baba983.nativedo

import android.content.Context
import android.os.Handler

object NdApp {
    private val lock = Any()

    @Volatile
    private var currentRuntime: NdRuntime? = null

    val runtime: NdRuntime
        get() = requireNotNull(currentRuntime) {
            "NdApp.init(context) must be called before accessing runtime"
        }

    val appContext: Context
        get() = runtime.appContext

    val mainHandler: Handler
        get() = runtime.mainHandler

    fun init(context: Context): NdRuntime {
        synchronized(lock) {
            val runtime = NdRuntime(context)
            runtime.bootstrap()
            val previousRuntime = currentRuntime
            currentRuntime = runtime
            if (previousRuntime !== runtime) {
                previousRuntime?.dispose()
            }
            return runtime
        }
    }

    fun initForTesting(runtime: NdRuntime) {
        synchronized(lock) {
            val previousRuntime = currentRuntime
            currentRuntime = runtime
            if (previousRuntime !== runtime) {
                previousRuntime?.dispose()
            }
        }
    }

    internal fun clearForTesting() {
        synchronized(lock) {
            val previousRuntime = currentRuntime
            currentRuntime = null
            previousRuntime?.dispose()
        }
    }
}
