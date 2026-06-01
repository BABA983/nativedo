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
import androidx.lifecycle.lifecycleScope
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.LaunchActivity
import io.github.baba983.nativedo.ui.main.MainTabsActivity
// TODO(photoviewer): re-enable when telegram/ui/PhotoViewer can compile.
// import org.telegram.ui.PhotoViewer
import kotlinx.coroutines.launch

private const val LAZY_START = 0.015f

class MainActivity : ComponentActivity() {

    private lateinit var actionBarLayout: ActionBarLayout
    private lateinit var mainTabsActivity: MainTabsActivity
    private var onBackAnimationCallback: OnBackAnimationCallback? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)
        LaunchActivity.instance = LaunchActivity()
        enableEdgeToEdge()

        mainTabsActivity = MainTabsActivity()
        actionBarLayout = ActionBarLayout(this)
        // Register the root layout as the global last-fragment provider. Browser sheets
        // (bookmarks/history/settings) resolve their host via LaunchActivity.getSafeLastFragment(),
        // so the global must stay pinned to the root and never be overwritten by a sheet's layout.
        LaunchActivity.setActionBarLayout(actionBarLayout)
        actionBarLayout.addFragmentToStack(mainTabsActivity)
        setContentView(actionBarLayout)
        lifecycleScope.launch {
            (applicationContext as NativeDoApp).appContainer.sessionCoordinator.start()
        }
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
        if (::actionBarLayout.isInitialized) {
            val fragments = actionBarLayout.fragmentStack
            for (index in fragments.size - 1 downTo 0) {
                fragments[index].onFragmentDestroy()
            }
            fragments.clear()
        }
        LaunchActivity.instance = null
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
                    // TODO(photoviewer): if (PhotoViewer.hasActiveViewer()) return
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
                    // TODO(photoviewer): if (PhotoViewer.hasActiveViewer()) return
                    actionBarLayout.onBackCancelled()
                }

                override fun onBackInvoked() {
                    invoked = true
                    // TODO(photoviewer): if (PhotoViewer.handleActiveBack()) return
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
                // TODO(photoviewer): re-add PhotoViewer.handleActiveBack() guard.
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
                        // TODO(photoviewer): re-add PhotoViewer.handleActiveBack() guard.
                        if (!actionBarLayout.handleBackPressed() && !mainTabsActivity.handleBackPressed()) {
                            finish()
                        }
                    }
                },
            )
        }
    }
}
