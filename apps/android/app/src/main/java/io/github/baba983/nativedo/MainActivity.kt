package io.github.baba983.nativedo

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.main.MainTabsActivity

private const val LAZY_START = 0.015f

class MainActivity : ComponentActivity() {

    private lateinit var actionBarLayout: ActionBarLayout
    private lateinit var mainTabsActivity: MainTabsActivity
    private var onBackAnimationCallback: OnBackAnimationCallback? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)
        enableEdgeToEdge()

        mainTabsActivity = MainTabsActivity()
        actionBarLayout = ActionBarLayout(this).apply {
            addFragmentToStack(mainTabsActivity)
        }
        setContentView(actionBarLayout)
        registerBackCallbacks()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidUtilities.init(this)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onBackAnimationCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        super.onDestroy()
    }

    private fun registerBackCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : OnBackAnimationCallback {
                private var started = false
                private var invoked = false
                private var predictiveBackStarted = false

                override fun onBackStarted(backEvent: BackEvent) {
                    started = true
                    invoked = false
                    predictiveBackStarted = false
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    if (started && invoked) {
                        return
                    }

                    val progress = backEvent.progress
                    if (!predictiveBackStarted && progress > LAZY_START) {
                        if (!actionBarLayout.canStartPredictiveBack()) {
                            return
                        }
                        predictiveBackStarted = true
                        actionBarLayout.onBackStarted(backEvent.touchX, backEvent.touchY)
                    }

                    val fixedProgress = ((progress - LAZY_START) / (1f - LAZY_START)).coerceAtLeast(0f)
                    if (predictiveBackStarted) {
                        actionBarLayout.onBackProgress(fixedProgress)
                    }
                }

                override fun onBackCancelled() {
                    started = false
                    invoked = false
                    actionBarLayout.onBackCancelled()
                }

                override fun onBackInvoked() {
                    invoked = true
                    if (actionBarLayout.canHandleBack()) {
                        actionBarLayout.onBackInvoked()
                    } else if (!mainTabsActivity.handleBackPressed()) {
                        finish()
                    }
                }
            }
            onBackAnimationCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback {
                if (!actionBarLayout.handleBackPressed() && !mainTabsActivity.handleBackPressed()) {
                    finish()
                }
            }
            onBackInvokedCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        } else {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (!actionBarLayout.handleBackPressed() && !mainTabsActivity.handleBackPressed()) {
                            finish()
                        }
                    }
                },
            )
        }
    }
}
