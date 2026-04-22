package io.github.baba983.nativedo

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

class NativeDoApplication : Application(), SingletonImageLoader.Factory {
    val runtime: NdRuntime
        get() = NdApp.runtime

    override fun onCreate() {
        super.onCreate()
        NdApp.init(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return runtime.newImageLoader(context)
    }
}
