package io.github.baba983.nativedo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.event.NotificationCenterDelegate
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.INavigationLayoutDelegate

class MainActivity : ComponentActivity(), NotificationCenterDelegate, INavigationLayoutDelegate {
    private companion object {
        const val NavigationStateKey: String = "main_activity_navigation_state"
    }

    private val launchBootstrap = LaunchBootstrap()
    private var rootLayout: ActionBarLayout? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val handled = rootLayout?.onBackPressed() == true
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
        onBackPressedDispatcher.addCallback(this, backCallback)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()

        rootLayout = ActionBarLayout(this).apply {
            setDelegate(this@MainActivity)
        }
        setContentView(rootLayout)
        val restored = savedInstanceState
            ?.getBundle(NavigationStateKey)
            ?.let { state ->
                rootLayout?.restoreNavigationState(state, launchBootstrap::restoreFragment)
            } == true
        if (!restored) {
            ensureRootFragment(intent)
        }
        applySystemBars()
    }

    override fun onStart() {
        super.onStart()
        NotificationCenter.addObserver(this, NotificationCenter.themeDidChange)
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        rootLayout?.onResume()
        applySystemBars()
    }

    override fun onPause() {
        rootLayout?.onPause()
        super.onPause()
    }

    override fun onStop() {
        NotificationCenter.removeObserver(this, NotificationCenter.themeDidChange)
        super.onStop()
    }

    override fun onDestroy() {
        rootLayout?.setDelegate(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rootLayout?.saveNavigationState()?.let { state ->
            outState.putBundle(NavigationStateKey, state)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ensureRootFragment(intent)
        applySystemBars()
    }

    override fun didReceivedNotification(id: Int, vararg args: Any?) {
        if (id == NotificationCenter.themeDidChange) {
            applySystemBars()
        }
    }

    override fun onFragmentStackChanged(stack: List<BaseFragment>) {
        applySystemBars()
    }

    override fun onVisibleFragmentChanged(fragment: BaseFragment?) {
        applySystemBars(fragment)
    }

    private fun ensureRootFragment(launchIntent: Intent?) {
        val layout = rootLayout ?: return
        if (layout.getFragmentStack().isNotEmpty()) {
            return
        }
        launchBootstrap.attach(layout, launchIntent)
    }

    private fun applySystemBars(fragment: BaseFragment? = rootLayout?.getLastFragment()) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = fragment?.getNavigationBarColor()
            ?: NdTheme.getColor(NdTheme.key_windowBackgroundGray)
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = when {
                fragment?.hasForceLightStatusBar() == true -> true
                else -> !NdTheme.isDarkTheme()
            }
            isAppearanceLightNavigationBars = !NdTheme.isDarkTheme()
        }
    }
}
