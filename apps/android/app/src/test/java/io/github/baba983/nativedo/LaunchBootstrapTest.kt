package io.github.baba983.nativedo

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.actionbar.HelloFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LaunchBootstrapTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun attach_mountsHelloFragmentIntoEmptyRootLayout() {
        val layout = ActionBarLayout(context)
        val bootstrap = LaunchBootstrap()

        val attached = bootstrap.attach(layout, Intent(Intent.ACTION_MAIN))

        assertThat(attached).isTrue()
        assertThat(layout.getFragmentStack()).hasSize(1)
        assertThat(layout.getLastFragment()).isInstanceOf(HelloFragment::class.java)
    }

    @Test
    fun attach_doesNothingWhenRootAlreadyHasStack() {
        val layout = ActionBarLayout(context)
        val bootstrap = LaunchBootstrap()

        bootstrap.attach(layout, null)
        val attachedAgain = bootstrap.attach(layout, Intent(Intent.ACTION_VIEW))

        assertThat(attachedAgain).isFalse()
        assertThat(layout.getFragmentStack()).hasSize(1)
    }
}
