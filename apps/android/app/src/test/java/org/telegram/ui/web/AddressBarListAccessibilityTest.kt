package org.telegram.ui.web

import android.view.View
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
class AddressBarListAccessibilityTest {
    private lateinit var list: AddressBarList

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        list = AddressBarList(RuntimeEnvironment.getApplication())
    }

    @Test
    fun collapsedListHidesDescendantsFromAccessibility() {
        assertThat(list.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }

    @Test
    fun openingAndClosingListUpdatesAccessibilityVisibility() {
        list.setOpenProgress(1f)
        assertThat(list.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)

        list.setOpenProgress(0f)
        assertThat(list.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }
}
