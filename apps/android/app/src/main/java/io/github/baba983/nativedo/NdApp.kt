package io.github.baba983.nativedo

import android.content.Context

object NdApp {
    private val lock = Any()

    @Volatile
    private var currentRuntime: NdRuntime? = null

    val runtime: NdRuntime
        get() = requireNotNull(currentRuntime) {
            "NdApp.init(context) must be called before accessing runtime"
        }

    fun init(context: Context): NdRuntime {
        synchronized(lock) {
            val runtime = NdRuntime(context)
            currentRuntime = runtime
            return runtime
        }
    }

    fun initForTesting(runtime: NdRuntime) {
        synchronized(lock) {
            currentRuntime = runtime
        }
    }
}
