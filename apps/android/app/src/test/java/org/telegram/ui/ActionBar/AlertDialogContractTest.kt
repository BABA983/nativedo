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
    fun builderRetainsTelegramButtonSurfaceStateBeforeWindowCreation() {
        val dialog = AlertDialog.Builder(context)
            .setPositiveButton("Done", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setDismissDialogByButtons(false)

        val policy = AlertDialog::class.java.getDeclaredField("dismissDialogByButtons")
        policy.isAccessible = true
        val positive = AlertDialog::class.java.getDeclaredField("positiveLabel").apply { isAccessible = true }
        val negative = AlertDialog::class.java.getDeclaredField("negativeLabel").apply { isAccessible = true }

        assertThat(policy.getBoolean(dialog)).isFalse()
        assertThat(positive.get(dialog)).isEqualTo("Done")
        assertThat(negative.get(dialog)).isEqualTo("Cancel")
        assertThat(AlertDialog::class.java.getDeclaredMethod("createMessageContent")).isNotNull()
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

    @Test
    fun builderRetainsTelegramTopAnimationContract() {
        val dialog = AlertDialog.Builder(context)
            .setTopAnimation(123, 72, false, 0xFF112233.toInt())
            .create()

        val animationId = AlertDialog::class.java.getDeclaredField("topAnimationId").apply { isAccessible = true }
        val animationSize = AlertDialog::class.java.getDeclaredField("topAnimationSize").apply { isAccessible = true }
        val autoRepeat = AlertDialog::class.java.getDeclaredField("topAnimationAutoRepeat").apply { isAccessible = true }
        val background = AlertDialog::class.java.getDeclaredField("topBackgroundColor").apply { isAccessible = true }

        assertThat(animationId.getInt(dialog)).isEqualTo(123)
        assertThat(animationSize.getInt(dialog)).isEqualTo(72)
        assertThat(autoRepeat.getBoolean(dialog)).isFalse()
        assertThat(background.getInt(dialog)).isEqualTo(0xFF112233.toInt())
    }
}
