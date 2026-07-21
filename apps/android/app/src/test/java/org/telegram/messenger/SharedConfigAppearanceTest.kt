package org.telegram.messenger

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SharedConfigAppearanceTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        MessagesController.getGlobalMainSettings().edit().clear().commit()
        resetLoadedState()
    }

    @After
    fun tearDown() {
        MessagesController.getGlobalMainSettings().edit().clear().commit()
        SharedConfig.fontSize = 16
        SharedConfig.fontSizeIsDefault = true
        SharedConfig.reduceAnimations = false
        resetLoadedState()
    }

    @Test
    fun loadsLegacyFontSizeAndPersistsCanonicalValue() {
        MessagesController.getGlobalMainSettings().edit().putInt("fons_size", 21).commit()

        SharedConfig.loadBrowserConfig()
        val changed = SharedConfig.setFontSize(24)

        assertThat(changed).isTrue()
        assertThat(SharedConfig.fontSize).isEqualTo(24)
        assertThat(MessagesController.getGlobalMainSettings().getInt("font_size", 0)).isEqualTo(24)
        assertThat(MessagesController.getGlobalMainSettings().contains("fons_size")).isFalse()
    }

    @Test
    fun reduceAnimationsControlsAnimationCapability() {
        SharedConfig.loadBrowserConfig()

        SharedConfig.setReduceAnimations(true)

        assertThat(SharedConfig.animationsEnabled()).isFalse()
        assertThat(MessagesController.getGlobalMainSettings().getBoolean("reduce_animations", false)).isTrue()
    }

    private fun resetLoadedState() {
        SharedConfig::class.java.getDeclaredField("browserConfigLoaded")
            .apply { isAccessible = true }
            .setBoolean(null, false)
    }
}
