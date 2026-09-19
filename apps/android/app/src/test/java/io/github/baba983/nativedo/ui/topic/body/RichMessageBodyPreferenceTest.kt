package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RichMessageBodyPreferenceTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        MessagesController.getGlobalMainSettings()
            .edit()
            .remove("nativedo_rich_message_body_enabled")
            .commit()
        RichMessageBody.enabled = false
    }

    @Test
    fun theSwitchDefaultsOff() {
        RichMessageBody.loadPreference()

        assertThat(RichMessageBody.enabled).isFalse()
    }

    @Test
    fun theDebugToggleSurvivesARestart() {
        RichMessageBody.setEnabled(true)

        // A fresh process: the in-memory flag is gone, the preference is not.
        RichMessageBody.enabled = false
        RichMessageBody.loadPreference()

        assertThat(RichMessageBody.enabled).isTrue()
    }

    @Test
    fun turningItBackOffIsPersistedToo() {
        RichMessageBody.setEnabled(true)
        RichMessageBody.setEnabled(false)

        RichMessageBody.enabled = true
        RichMessageBody.loadPreference()

        assertThat(RichMessageBody.enabled).isFalse()
    }
}
