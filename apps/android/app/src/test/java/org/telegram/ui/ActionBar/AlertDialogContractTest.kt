package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AlertDialogContractTest {
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun builderInstallsCustomButtonPolicyBeforeWindowCreation() {
        val dialog = AlertDialog.Builder(context)
            .setPositiveButton("Done", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setDismissDialogByButtons(false)

        val policy = AlertDialog::class.java.getDeclaredField("dismissDialogByButtons")
        policy.isAccessible = true
        val configurator = AlertDialog::class.java.getDeclaredField("delegateOnShowConfigurator")
        configurator.isAccessible = true

        assertThat(policy.getBoolean(dialog)).isFalse()
        assertThat(configurator.get(dialog)).isNotNull()
    }

    @Test
    fun dynamicSettersRetainStateForTheDeferredDelegate() {
        val dialog = AlertDialog.Builder(context).create()
        dialog.setTitle("Title")
        dialog.setMessage("Message")
        dialog.setCanCancel(false)

        val title = AlertDialog::class.java.getDeclaredField("currentTitle")
        title.isAccessible = true
        val message = AlertDialog::class.java.getDeclaredField("currentMessage")
        message.isAccessible = true
        val canCancel = AlertDialog::class.java.getDeclaredField("canCancel")
        canCancel.isAccessible = true

        assertThat(title.get(dialog)).isEqualTo("Title")
        assertThat(message.get(dialog)).isEqualTo("Message")
        assertThat(canCancel.getBoolean(dialog)).isFalse()
    }
}
