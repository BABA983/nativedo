package io.github.baba983.nativedo

import android.content.res.Configuration
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.LaunchActivity
import org.telegram.ui.ArticleViewer
import org.telegram.ui.PhotoViewer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.ui.auth.LoginWebViewFragment
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.theme.ThemeSettingsFragment
import org.telegram.ui.Components.ThemeEditorView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val LAZY_START = 0.015f

// Profile tab avatar is drawn at 24dp; request a larger source for crispness on
// high-density screens.
private const val AVATAR_TAB_SIZE = 120
private const val THEME_TIMER_INTERVAL_MS = 1_000L

class MainActivity : LaunchActivity(), NotificationCenter.NotificationCenterDelegate, Theme.ThemeHost {

    private lateinit var actionBarLayout: ActionBarLayout
    private lateinit var mainTabsActivity: MainTabsActivity
    private var onBackAnimationCallback: OnBackAnimationCallback? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null
    private val themeTimerRunnable = object : Runnable {
        override fun run() {
            MessagesController.getInstance(UserConfig.selectedAccount).updateTimerProc()
            AndroidUtilities.runOnUIThread(this, THEME_TIMER_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)
        LaunchActivity.instance = this
        Theme.setThemeHost(this)
        enableEdgeToEdge()

        mainTabsActivity = MainTabsActivity()
        val rootFrameLayout = FrameLayout(this)
        rootFrameLayout.clipChildren = false
        rootFrameLayout.clipToPadding = false
        actionBarLayout = ActionBarLayout(this)
        drawerLayoutContainer = DrawerLayoutContainer(this)
        drawerLayoutContainer.clipChildren = false
        drawerLayoutContainer.clipToPadding = false
        // Upstream LaunchActivity wires this so drawer intercept / keyboard path can
        // consult the root ActionBarLayout. Required for DrawerLayoutContainer's
        // IME-driven requestLayout that AdjustPanLayoutHelper depends on.
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout)
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer)
        // Upstream LaunchActivity paints the strip behind IME with windowBackgroundWhite.
        // Without this, AdjustPan height freezes can flash a grey/default gap under tabs.
        drawerLayoutContainer.setBehindKeyboardColor(
            org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite),
        )
        drawerLayoutContainer.addView(
            actionBarLayout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // Register the root layout as the global last-fragment provider. Browser sheets
        // (bookmarks/history/settings) resolve their host via LaunchActivity.getSafeLastFragment(),
        // so the global must stay pinned to the root and never be overwritten by a sheet's layout.
        LaunchActivity.setActionBarLayout(actionBarLayout)
        actionBarLayout.addFragmentToStack(mainTabsActivity)
        rootFrameLayout.addView(
            drawerLayoutContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(rootFrameLayout)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.fontSizeChanged)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needCheckSystemBarColors)
        Browser.bindCustomTabsService(this)
        lifecycleScope.launch {
            (applicationContext as NativeDoApp).appContainer.sessionCoordinator.start()
        }
        observeProfileTabAvatar()
        observeSessionEvents()
        registerBackCallbacks()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidUtilities.init(this)
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions()
        }
        ThemeEditorView.getInstance()?.onConfigurationChanged()
        if (::actionBarLayout.isInitialized) {
            actionBarLayout.onConfigurationChanged(newConfig)
        }
        PhotoViewer.getInstance().onConfigurationChanged(newConfig)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 10 || requestCode == 11) {
            ThemeEditorView.getInstance()?.onActivityResult(requestCode, resultCode, data)
            return
        }
        if (::actionBarLayout.isInitialized) {
            val fragment = actionBarLayout.lastFragment
            if (fragment is ThemeSettingsFragment && fragment.handleActivityResult(requestCode, resultCode, data)) {
                return
            }
        }
        if (ArticleViewer.dispatchActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (::actionBarLayout.isInitialized) {
            val fragment = actionBarLayout.lastFragment
            if (fragment is ThemeSettingsFragment) {
                fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        ApplicationLoader.mainInterfacePaused = false
        ApplicationLoader.mainInterfaceStopped = false
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions()
        }
        AndroidUtilities.cancelRunOnUIThread(themeTimerRunnable)
        AndroidUtilities.runOnUIThread(themeTimerRunnable)
        Browser.bindCustomTabsService(this)
        if (::actionBarLayout.isInitialized) {
            actionBarLayout.onResume()
        }
        PhotoViewer.getInstance().onResume()
    }

    override fun onPause() {
        AndroidUtilities.cancelRunOnUIThread(themeTimerRunnable)
        ApplicationLoader.mainInterfacePaused = true
        if (::actionBarLayout.isInitialized) {
            actionBarLayout.onPause()
        }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (::actionBarLayout.isInitialized) {
            actionBarLayout.onUserLeaveHint()
        }
        super.onUserLeaveHint()
    }

    override fun onLowMemory() {
        if (::actionBarLayout.isInitialized) {
            actionBarLayout.onLowMemory()
        }
        super.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        ApplicationLoader.mainInterfaceStopped = false
    }

    override fun onStop() {
        ApplicationLoader.mainInterfaceStopped = true
        super.onStop()
    }

    override fun onDestroy() {
        AndroidUtilities.cancelRunOnUIThread(themeTimerRunnable)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.fontSizeChanged)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needCheckSystemBarColors)
        Browser.unbindCustomTabsService(this)
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
        PhotoViewer.getInstance().destroyPhotoViewer()
        ThemeEditorView.getInstance()?.destroy()
        Theme.setThemeHost(null)
        LaunchActivity.instance = null
        super.onDestroy()
    }

    override fun destroyThemeEditor() {
        ThemeEditorView.getInstance()?.destroy()
    }

    override fun onThemeApplied() {
        drawerLayoutContainer?.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite))
    }

    override fun openNightThemeSettings(fragment: BaseFragment) {
        fragment.presentFragment(ThemeSettingsFragment())
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (!::actionBarLayout.isInitialized) {
            return
        }
        if (id == NotificationCenter.fontSizeChanged) {
            actionBarLayout.rebuildAllFragmentViews(false)
            return
        }
        if (id == NotificationCenter.needCheckSystemBarColors) {
            // Re-read icon contrast from the visible fragment after header transitions.
            actionBarLayout.updateSystemBars()
            return
        }
        if (id != NotificationCenter.needSetDayNightTheme) return
        val theme = args.getOrNull(0) as? Theme.ThemeInfo ?: return
        val nightTheme = args.getOrNull(1) as? Boolean ?: false
        val accentId = args.getOrNull(3) as? Int ?: -1
        actionBarLayout.animateThemedValues(
            theme,
            accentId,
            nightTheme,
            !SharedConfig.animationsEnabled(),
        )
    }

    /**
     * Mirror an authenticated account's avatar onto the bottom profile tab.
     * Restoring keeps a cached identity visible during startup; anonymous and
     * expired sessions use the static profile icon.
     */
    private fun observeProfileTabAvatar() {
        val container = (applicationContext as NativeDoApp).appContainer
        lifecycleScope.launch {
            container.sessionCoordinator.sessionState.collect { state ->
                val snapshot = when (state) {
                    is AppSessionState.Authenticated -> state.snapshot
                    is AppSessionState.Restoring -> state.snapshot
                    else -> null
                } ?: run {
                    mainTabsActivity.clearProfileAvatar()
                    return@collect
                }
                val template = snapshot.avatarTemplate
                if (template.isNotBlank()) {
                    val url = resolveAvatarUrl(container.baseUrl, template, size = AVATAR_TAB_SIZE)
                    val name = snapshot.displayName.ifBlank { snapshot.username }
                    mainTabsActivity.setProfileAvatar(url, avatarImageFilter("", AVATAR_TAB_SIZE), name)
                } else {
                    mainTabsActivity.clearProfileAvatar()
                }
            }
        }
    }

    private fun observeSessionEvents() {
        val container = (applicationContext as NativeDoApp).appContainer
        lifecycleScope.launch {
            container.sessionCoordinator.events.collect { event ->
                when (event) {
                    SessionEvent.LoginRequired -> presentLoginGate()
                    else -> Unit
                }
            }
        }
    }

    private fun presentLoginGate() {
        if (!::actionBarLayout.isInitialized) return
        if (actionBarLayout.lastFragment is LoginWebViewFragment) {
            return
        }
        actionBarLayout.presentFragment(LoginWebViewFragment())
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
                    if (PhotoViewer.getInstance().isVisibleOrAnimating()) return
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
                    if (PhotoViewer.getInstance().isVisibleOrAnimating()) return
                    actionBarLayout.onBackCancelled()
                }

                override fun onBackInvoked() {
                    invoked = true
                    if (handlePhotoViewerBack()) return
                    if (actionBarLayout.canHandleBack()) {
                        actionBarLayout.onBackInvoked()
                    } else if (!mainTabsActivity.handleBackPressed()) {
                        finish()
                    }
                }
            }
            onBackAnimationCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback {
                if (handlePhotoViewerBack()) return@OnBackInvokedCallback
                if (!actionBarLayout.handleBackPressed() && !mainTabsActivity.handleBackPressed()) {
                    finish()
                }
            }
            onBackInvokedCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
        } else {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (handlePhotoViewerBack()) return
                        if (!actionBarLayout.handleBackPressed() && !mainTabsActivity.handleBackPressed()) {
                            finish()
                        }
                    }
                },
            )
        }
    }

    private fun handlePhotoViewerBack(): Boolean {
        val photoViewer = PhotoViewer.getInstance()
        if (!photoViewer.isVisibleOrAnimating()) return false
        photoViewer.closePhoto(true, false)
        return true
    }
}
