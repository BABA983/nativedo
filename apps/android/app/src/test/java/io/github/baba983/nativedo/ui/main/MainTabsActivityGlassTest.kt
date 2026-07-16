package io.github.baba983.nativedo.ui.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode

@RunWith(RobolectricTestRunner::class)
class MainTabsActivityGlassTest {

    @Test
    @Config(sdk = [28], manifest = Config.NONE)
    fun preAndroid12_usesColorGlassFallback() {
        val activity = MainTabsActivity()

        assertThat(activity.privateField<BlurredBackgroundSourceRenderNode?>("blurSourceTabs")).isNull()
        assertThat(activity.privateField<BlurredBackgroundSourceColor>("blurSourceColor")).isNotNull()
    }

    @Test
    @Config(sdk = [31], manifest = Config.NONE)
    fun android12_usesRenderNodeWithTheSameColorFallback() {
        val activity = MainTabsActivity()
        val colorSource = activity.privateField<BlurredBackgroundSourceColor>("blurSourceColor")
        val renderNodeSource = activity.privateField<BlurredBackgroundSourceRenderNode>("blurSourceTabs")

        assertThat(renderNodeSource).isNotNull()
        assertThat(renderNodeSource.fallbackSource).isSameInstanceAs(colorSource)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> MainTabsActivity.privateField(name: String): T {
        return MainTabsActivity::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(this) as T
    }
}
