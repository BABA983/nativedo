package org.telegram.ui.web

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
class WebBrowserSettingsWebsiteViewTest {
    private lateinit var view: WebBrowserSettings.WebsiteView

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        view = WebBrowserSettings.WebsiteView(RuntimeEnvironment.getApplication())
        WebMetadataCache.getInstance().clear()
    }

    @Test
    fun cachedBitmapFaviconIsUsedWhenExceptionHasNoDocumentId() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val metadata = WebMetadataCache.WebMetadata().apply {
            domain = "cached.example"
            favicon = bitmap
        }
        WebMetadataCache.getInstance().save(metadata)

        view.set("Cached title", "cached.example", 0L, false)

        val drawable = view.imageView.drawable
        assertThat(drawable).isInstanceOf(BitmapDrawable::class.java)
        assertThat((drawable as BitmapDrawable).bitmap).isSameInstanceAs(bitmap)
    }
}
