package org.telegram.ui.ActionBar

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
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
class BottomSheetTabsOverlaySheetContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun modalAccessibilityHidesAndRestoresSiblingContent() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val sibling = View(context)
        val overlay = BottomSheetTabsOverlay(context)
        parent.addView(sibling)
        parent.addView(overlay)
        val method = BottomSheetTabsOverlay::class.java
            .getDeclaredMethod("setModalAccessibility", java.lang.Boolean.TYPE)
            .apply { isAccessible = true }

        method.invoke(overlay, true)

        assertThat(overlay.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(sibling.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        assertThat(ViewCompat.hasAccessibilityDelegate(overlay)).isTrue()

        method.invoke(overlay, false)

        assertThat(overlay.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(sibling.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
    }
}
