package io.github.baba983.nativedo

import android.content.Intent
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout
import io.github.baba983.nativedo.ui.actionbar.HelloFragment
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = NativeDoApplication::class,
    manifest = "src/main/AndroidManifest.xml",
)
class MainActivityRootSwitchTest {
    @After
    fun tearDown() {
        MainRootHostSwitch.setOverrideModeForTesting(null)
    }

    @Test
    fun defaultMode_resolvesLegacyCompose() {
        assertThat(resolveMainRootHostMode()).isEqualTo(MainRootHostMode.LegacyCompose)
    }

    @Test
    fun intentExtra_enablesRewriteMode() {
        val intent = Intent().putExtra(RewriteRootIntentExtra, true)

        assertThat(resolveMainRootHostMode(intent)).isEqualTo(MainRootHostMode.Rewrite)
    }

    @Test
    fun rewriteOverride_mountsActionBarLayoutRoot() {
        MainRootHostSwitch.setOverrideModeForTesting(MainRootHostMode.Rewrite)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

        assertThat(resolveMainRootHostMode()).isEqualTo(MainRootHostMode.Rewrite)
        assertThat(rootView).isInstanceOf(ActionBarLayout::class.java)
        val actionBarLayout = rootView as ActionBarLayout
        assertThat(actionBarLayout.getFragmentStack()).hasSize(1)
        assertThat(actionBarLayout.getFragmentStack().single()).isInstanceOf(HelloFragment::class.java)
    }
}
