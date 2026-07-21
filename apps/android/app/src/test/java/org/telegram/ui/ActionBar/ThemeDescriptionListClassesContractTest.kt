package org.telegram.ui.ActionBar

import android.content.Context
import android.view.View
import android.widget.FrameLayout
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
 * listClasses (flags=0) keeps the upstream child invalidation/delegate contract.
 * Colorable refresh is owned by ActionBarLayout's visible-tree animation pass,
 * not by a ThemeDescription host extension.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeDescriptionListClassesContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
        AndroidUtilities.init(context)
        Theme.getActiveTheme()
    }

    private class ColorableCell(context: Context) : View(context), Theme.Colorable {
        var updateCount = 0
            private set

        override fun updateColors() {
            updateCount++
        }
    }

    @Test
    fun listClassesDescriptionInvalidatesChildrenWithoutColorableExtension() {
        val host = FrameLayout(context)
        val a = ColorableCell(context)
        val b = ColorableCell(context)
        host.addView(a)
        host.addView(b)

        val descriptions = arrayListOf(
            ThemeDescription(
                host,
                0,
                arrayOf(ColorableCell::class.java),
                null as android.graphics.Paint?,
                null,
                null,
                Theme.key_windowBackgroundWhite,
            ),
        )
        descriptions.forEach { it.setColor(Theme.getColor(it.currentKey), false, false) }
        assertThat(a.updateCount).isEqualTo(0)
        assertThat(b.updateCount).isEqualTo(0)

        // setColor always re-applies (no generation cache).
        descriptions.forEach { it.setColor(Theme.getColor(it.currentKey), false, false) }
        assertThat(a.updateCount).isEqualTo(0)
    }

    @Test
    fun chromeDelegateStillRunsWithListClasses() {
        var chrome = 0
        val host = FrameLayout(context)
        val cell = ColorableCell(context)
        host.addView(cell)
        val descriptions = arrayListOf(
            ThemeDescription(
                host,
                0,
                arrayOf(ColorableCell::class.java),
                null as android.graphics.Paint?,
                null,
                ThemeDescription.ThemeDescriptionDelegate { chrome++ },
                Theme.key_windowBackgroundWhite,
            ),
        )
        descriptions.forEach { it.setColor(Theme.getColor(it.currentKey), false, false) }
        assertThat(chrome).isEqualTo(1)
        assertThat(cell.updateCount).isEqualTo(0)
    }
}
