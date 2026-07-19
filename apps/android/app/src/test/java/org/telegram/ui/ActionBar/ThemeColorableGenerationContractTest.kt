package org.telegram.ui.ActionBar

import android.content.Context
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

/**
 * Contract: [Theme.applyThemeColorsIfNeeded] only re-tints Colorable views when
 * themeGeneration advanced — covers RecyclerView cache/pool rebinds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeColorableGenerationContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
        AndroidUtilities.init(context)
        context.getSharedPreferences("themeconfig", 0).edit().clear().commit()
        Theme.init(context)
    }

    private class CountingColorableView(context: Context) : View(context), Theme.Colorable {
        var updateCount = 0
            private set

        override fun updateColors() {
            updateCount++
        }
    }

    @Test
    fun applyThemeColorsIfNeededSkipsWhenGenerationUnchanged() {
        val view = CountingColorableView(context)
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(1)
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(1)
    }

    @Test
    fun applyThemeColorsIfNeededRunsAgainAfterThemeToggle() {
        val view = CountingColorableView(context)
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(1)

        Theme.toggleTheme()
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(2)

        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(2)
    }

    @Test
    fun markThemeColorsAppliedSkipsNextBind() {
        val view = CountingColorableView(context)
        Theme.markThemeColorsApplied(view)
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(0)

        Theme.toggleTheme()
        Theme.applyThemeColorsIfNeeded(view)
        assertThat(view.updateCount).isEqualTo(1)
    }

    @Test
    fun nonColorableViewIsNoOp() {
        val plain = View(context)
        // Must not throw.
        Theme.applyThemeColorsIfNeeded(plain)
        Theme.markThemeColorsApplied(plain)
    }
}
