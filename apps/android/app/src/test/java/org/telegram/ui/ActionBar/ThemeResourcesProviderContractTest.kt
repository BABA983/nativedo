package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Stories.DarkThemeResourceProvider
import org.telegram.ui.WrappedResourceProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeResourcesProviderContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun wrappedProviderOverridesThemeColor() {
        val provider = WrappedResourceProvider(null)
        provider.sparseIntArray.put(Theme.key_windowBackgroundWhite, 0xff123456.toInt())

        assertThat(Theme.getColor(Theme.key_windowBackgroundWhite, provider))
            .isEqualTo(0xff123456.toInt())
    }

    @Test
    fun darkProviderSuppliesReadableShareSheetColors() {
        val provider = DarkThemeResourceProvider()

        assertThat(provider.isDark()).isTrue()
        assertThat(Theme.getColor(Theme.key_windowBackgroundWhite, provider))
            .isEqualTo(0xff1c1c1e.toInt())
        assertThat(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider))
            .isEqualTo(0xffffffff.toInt())
    }
}
