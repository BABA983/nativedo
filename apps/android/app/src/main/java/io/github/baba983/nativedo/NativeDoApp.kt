package io.github.baba983.nativedo

import android.app.Application
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.ui.actionbar.Theme

class NativeDoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidUtilities.init(this)
        Theme.init(this)
    }
}
