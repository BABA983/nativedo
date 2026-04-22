package io.github.baba983.nativedo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.NativeDoApp

class MainActivity : ComponentActivity() {
    private var rewriteRoot: ActionBarLayout? = null

    private val rewriteBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val handled = rewriteRoot?.onBackPressed() == true
            if (handled) {
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, rewriteBackCallback)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()
        when (resolveMainRootHostMode(intent)) {
            MainRootHostMode.LegacyCompose -> {
                rewriteBackCallback.isEnabled = false
                setContent {
                    NativeDoApp()
                }
            }

            MainRootHostMode.Rewrite -> {
                rewriteRoot = createRewriteRoot(this)
                rewriteBackCallback.isEnabled = true
                setContentView(rewriteRoot)
            }
        }
    }
}
