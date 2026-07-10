package io.github.baba983.nativedo

import android.content.res.Configuration
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
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.LaunchActivity
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import org.telegram.ui.PhotoViewer
import kotlinx.coroutines.launch

private const val LAZY_START = 0.015f

// Profile tab avatar is drawn at 24dp; request a larger source for crispness on
// high-density screens.
private const val AVATAR_TAB_SIZE = 120

class MainActivity : LaunchActivity() {

    private lateinit var actionBarLayout: ActionBarLayout
    private lateinit var mainTabsActivity: MainTabsActivity
    private var onBackAnimationCallback: OnBackAnimationCallback? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)
        LaunchActivity.instance = this
        enableEdgeToEdge()

        mainTabsActivity = MainTabsActivity()
        val rootFrameLayout = FrameLayout(this)
        rootFrameLayout.clipChildren = false
        rootFrameLayout.clipToPadding = false
        actionBarLayout = ActionBarLayout(this)
        drawerLayoutContainer = DrawerLayoutContainer(this)
        drawerLayoutContainer.clipChildren = false
        drawerLayoutContainer.clipToPadding = false
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
        lifecycleScope.launch {
            (applicationContext as NativeDoApp).appContainer.sessionCoordinator.start()
        }
        observeProfileTabAvatar()
        registerBackCallbacks()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidUtilities.init(this)
        PhotoViewer.getInstance().onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
        PhotoViewer.getInstance().onResume()
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
        PhotoViewer.getInstance().destroyPhotoViewer()
        LaunchActivity.instance = null
        super.onDestroy()
    }

    /**
     * Mirror the signed-in account's avatar onto the bottom profile tab. Shows the
     * avatar whenever a session snapshot carries a non-blank avatar template
     * (authenticated, restoring, or expired-with-cache), and falls back to the
     * static profile icon otherwise.
     */
    private fun observeProfileTabAvatar() {
        val container = (applicationContext as NativeDoApp).appContainer
        lifecycleScope.launch {
            container.sessionCoordinator.sessionState.collect { state ->
                val snapshot: SessionSnapshot? = when (state) {
                    is AppSessionState.Authenticated -> state.snapshot
                    is AppSessionState.Restoring -> state.snapshot
                    is AppSessionState.Expired -> state.snapshot
                    else -> null
                }
                val template = snapshot?.avatarTemplate.orEmpty()
                if (snapshot != null && template.isNotBlank()) {
                    val url = resolveAvatarUrl(container.baseUrl, template, size = AVATAR_TAB_SIZE)
                    val name = snapshot.displayName.ifBlank { snapshot.username }
                    mainTabsActivity.setProfileAvatar(url, avatarImageFilter("", AVATAR_TAB_SIZE), name)
                } else {
                    mainTabsActivity.clearProfileAvatar()
                }
            }
        }
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
