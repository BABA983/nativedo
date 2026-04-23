package io.github.baba983.nativedo

import android.app.Application
import io.github.baba983.nativedo.core.AndroidUtilities

class NativeDoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidUtilities.init(this)
    }
}
