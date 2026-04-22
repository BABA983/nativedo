package io.github.baba983.nativedo.core.event

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLifecycleState(
    val isForeground: Boolean,
    val isNetworkAvailable: Boolean,
)

class AppLifecycleMonitor(
    context: Context,
) : Application.ActivityLifecycleCallbacks {
    private val application = context.applicationContext as Application
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var startedActivityCount: Int = 0
    private var activityLifecycleCallbacksRegistered: Boolean = false
    private var connectivityReceiverRegistered: Boolean = false

    private val _state = MutableStateFlow(
        AppLifecycleState(
            isForeground = false,
            isNetworkAvailable = resolveCurrentNetworkAvailability(),
        ),
    )
    val state: StateFlow<AppLifecycleState> = _state.asStateFlow()

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNetworkAvailability(resolveCurrentNetworkAvailability())
        }
    }

    init {
        application.registerActivityLifecycleCallbacks(this)
        activityLifecycleCallbacksRegistered = true
        application.registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
        )
        connectivityReceiverRegistered = true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        if (startedActivityCount == 1) {
            updateForegroundState(true)
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0) {
            updateForegroundState(false)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    internal fun updateNetworkAvailabilityForTesting(isNetworkAvailable: Boolean) {
        updateNetworkAvailability(isNetworkAvailable)
    }

    fun dispose() {
        if (activityLifecycleCallbacksRegistered) {
            application.unregisterActivityLifecycleCallbacks(this)
            activityLifecycleCallbacksRegistered = false
        }
        if (connectivityReceiverRegistered) {
            application.unregisterReceiver(connectivityReceiver)
            connectivityReceiverRegistered = false
        }
    }

    internal fun disposeForTesting() {
        dispose()
    }

    private fun updateForegroundState(isForeground: Boolean) {
        val previousState = _state.value
        if (previousState.isForeground == isForeground) {
            return
        }
        _state.value = previousState.copy(isForeground = isForeground)
        NotificationCenter.postNotificationName(
            NotificationCenter.appForegroundStateChanged,
            isForeground,
        )
    }

    private fun updateNetworkAvailability(isNetworkAvailable: Boolean) {
        val previousState = _state.value
        if (previousState.isNetworkAvailable == isNetworkAvailable) {
            return
        }
        _state.value = previousState.copy(isNetworkAvailable = isNetworkAvailable)
        NotificationCenter.postNotificationName(
            NotificationCenter.networkStateChanged,
            isNetworkAvailable,
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveCurrentNetworkAvailability(): Boolean {
        return connectivityManager?.activeNetworkInfo?.isConnected == true
    }
}
