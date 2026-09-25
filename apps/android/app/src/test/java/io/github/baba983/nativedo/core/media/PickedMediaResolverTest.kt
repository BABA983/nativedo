package io.github.baba983.nativedo.core.media

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader

/**
 * The picker's content URI as the upload needs it: our own copy in the cache with a name, so the
 * uploader has a path and the markdown has an alt. The system picker itself is a device concern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PickedMediaResolverTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun anUnreadableUriResolvesToNull() {
        val context = RuntimeEnvironment.getApplication()

        val picked = PickedMediaResolver.resolve(context, Uri.parse("content://nope/missing.png"))

        assertThat(picked).isNull()
    }

    @Test
    fun nothingIsLeftBehindWhenTheCopyFails() {
        val context = RuntimeEnvironment.getApplication()
        val cacheDir = File(context.cacheDir, "reply-media")
        val before = cacheDir.listFiles()?.size ?: 0

        PickedMediaResolver.resolve(context, Uri.parse("content://nope/missing.png"))

        val after = cacheDir.listFiles()?.size ?: 0
        assertThat(after).isEqualTo(before)
    }
}
