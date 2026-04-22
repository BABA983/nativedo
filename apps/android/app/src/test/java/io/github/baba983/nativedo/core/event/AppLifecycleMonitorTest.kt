package io.github.baba983.nativedo.core.event

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLifecycleMonitorTest {
    private var monitor: AppLifecycleMonitor? = null

    @After
    fun tearDown() {
        monitor?.disposeForTesting()
        monitor = null
        NotificationCenter.resetForTesting()
    }

    @Test
    fun activityLifecycle_updatesForegroundStateAndPostsNotification() {
        val observer = RecordingObserver()
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        NotificationCenter.addObserver(observer, NotificationCenter.appForegroundStateChanged)
        monitor = AppLifecycleMonitor(RuntimeEnvironment.getApplication())

        monitor?.onActivityStarted(activity)
        monitor?.onActivityStopped(activity)

        assertThat(monitor?.state?.value).isEqualTo(
            AppLifecycleState(
                isForeground = false,
                isNetworkAvailable = monitor?.state?.value?.isNetworkAvailable ?: false,
            ),
        )
        assertThat(observer.notifications).containsExactly(
            ReceivedNotification(
                id = NotificationCenter.appForegroundStateChanged,
                args = listOf(true),
            ),
            ReceivedNotification(
                id = NotificationCenter.appForegroundStateChanged,
                args = listOf(false),
            ),
        ).inOrder()
    }

    @Test
    fun networkUpdates_changeStateAndPostNotification() {
        val observer = RecordingObserver()

        NotificationCenter.addObserver(observer, NotificationCenter.networkStateChanged)
        monitor = AppLifecycleMonitor(RuntimeEnvironment.getApplication())
        val initialNetworkAvailability = monitor?.state?.value?.isNetworkAvailable ?: false

        monitor?.updateNetworkAvailabilityForTesting(!initialNetworkAvailability)
        monitor?.updateNetworkAvailabilityForTesting(initialNetworkAvailability)

        assertThat(monitor?.state?.value?.isNetworkAvailable).isEqualTo(initialNetworkAvailability)
        assertThat(observer.notifications).containsExactly(
            ReceivedNotification(
                id = NotificationCenter.networkStateChanged,
                args = listOf(!initialNetworkAvailability),
            ),
            ReceivedNotification(
                id = NotificationCenter.networkStateChanged,
                args = listOf(initialNetworkAvailability),
            ),
        ).inOrder()
    }

    private class RecordingObserver : NotificationCenterDelegate {
        val notifications = mutableListOf<ReceivedNotification>()

        override fun didReceivedNotification(id: Int, vararg args: Any?) {
            notifications += ReceivedNotification(
                id = id,
                args = args.toList(),
            )
        }
    }

    private data class ReceivedNotification(
        val id: Int,
        val args: List<Any?>,
    )
}
