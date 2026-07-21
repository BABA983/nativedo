package org.telegram.ui.Components

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DocumentObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemePreviewDrawableContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun imageLoaderPreviewIsConcreteAndKeepsThemeDocument() {
        val document = DocumentObject.ThemeDocument()
        val drawable = ThemePreviewDrawable(null, document)

        assertThat(drawable.bitmap).isNotNull()
        assertThat(drawable.bitmap.width).isEqualTo(560)
        assertThat(drawable.bitmap.height).isEqualTo(678)
        assertThat(drawable.themeDocument).isSameInstanceAs(document)
    }
}
