package org.telegram.ui.Components.chat.layouts

import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ChatActivitySideControlsButtonsLayoutTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun pageDownButtonKeepsPinnedMeasurementAndVisibilityEndpoints() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
        val source = BlurredBackgroundSourceColor().apply {
            setColor(Theme.getColor(Theme.key_chat_messagePanelBackground))
        }
        val controls = ChatActivitySideControlsButtonsLayout(
            context,
            null,
            BlurredBackgroundColorProviderThemed(null, Theme.key_chat_messagePanelBackground),
            BlurredBackgroundDrawableViewFactory(source),
        )
        controls.setButtonCount(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, 3, false)
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, true, false)
        controls.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(57f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300f), View.MeasureSpec.EXACTLY),
        )
        controls.layout(0, 0, controls.measuredWidth, controls.measuredHeight)
        val button = controls.getChildAt(0)

        assertThat(button.measuredWidth).isEqualTo(AndroidUtilities.dp(56f))
        assertThat(button.measuredHeight).isEqualTo(AndroidUtilities.dp(64f))
        assertThat(button.pivotX).isEqualTo(AndroidUtilities.dp(28f).toFloat())
        assertThat(button.pivotY).isEqualTo(AndroidUtilities.dp(36f).toFloat())

        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, true, false)
        assertThat(button.visibility).isEqualTo(View.VISIBLE)
        assertThat(button.alpha).isEqualTo(1f)
        assertThat(button.scaleX).isEqualTo(1f)
        assertThat(button.translationY).isEqualTo(0f)

        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, false, false)
        assertThat(button.visibility).isEqualTo(View.GONE)
        assertThat(button.alpha).isEqualTo(0f)
        assertThat(button.scaleX).isEqualTo(0.7f)
        assertThat(button.translationY).isEqualTo(AndroidUtilities.dp(80f).toFloat())
    }
}
