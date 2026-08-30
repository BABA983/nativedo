package io.github.baba983.nativedo.ui.main

import android.content.Context
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities

internal class MainTabsLayoutTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule()

    @Test
    fun threeTabsKeepTelegramMainPillWidth() {
        paparazziRule.verify { context ->
            val layout = MainTabsLayout(context).apply {
                setPadding(
                    AndroidUtilities.dp(12f),
                    AndroidUtilities.dp(12f),
                    AndroidUtilities.dp(12f),
                    AndroidUtilities.dp(12f),
                )
                setMaxWidth(AndroidUtilities.dp(344f))
                addView(FixedTextTab(context, 36f))
                addView(FixedTextTab(context, 52f))
                addView(FixedTextTab(context, 40f))
            }

            layout.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72f), View.MeasureSpec.EXACTLY),
            )

            assertThat(layout.measuredWidth).isEqualTo(AndroidUtilities.dp(344f))
            assertThat(layout.measuredHeight).isEqualTo(AndroidUtilities.dp(72f))
            assertThat((0 until layout.childCount).sumOf { layout.getChildAt(it).measuredWidth })
                .isEqualTo(layout.measuredWidth - layout.paddingLeft - layout.paddingRight)
        }
    }

    @Test
    fun profileTabUsesStaticIconUntilAvatarIsBound() {
        paparazziRule.verify { context ->
            val tab = GlassTabView.createMainTab(
                context,
                GlassTabView.TabIcon.PROFILE,
                io.github.baba983.nativedo.R.string.tab_profile,
            )
            val width = AndroidUtilities.dp(96f)
            val height = AndroidUtilities.dp(72f)

            fun layoutTab() {
                tab.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                tab.layout(0, 0, width, height)
            }

            layoutTab()
            assertThat(tab.getChildAt(0).visibility).isEqualTo(View.VISIBLE)
            assertThat(tab.getChildAt(1).visibility).isEqualTo(View.GONE)

            tab.setAvatar("", "", "Baba")
            layoutTab()
            assertThat(tab.getChildAt(0).visibility).isEqualTo(View.GONE)
            assertThat(tab.getChildAt(1).visibility).isEqualTo(View.VISIBLE)
            assertThat(tab.getChildAt(1).measuredWidth).isEqualTo(AndroidUtilities.dp(22f))
            assertThat(tab.getChildAt(1).measuredHeight).isEqualTo(AndroidUtilities.dp(22f))
            assertThat(tab.getChildAt(1).top).isEqualTo(AndroidUtilities.dp(5f))

            tab.clearAvatar()
            assertThat(tab.getChildAt(0).visibility).isEqualTo(View.VISIBLE)
            assertThat(tab.getChildAt(1).visibility).isEqualTo(View.GONE)
        }
    }

    private class FixedTextTab(
        context: Context,
        private val textWidthDp: Float,
    ) : View(context), MainTabsLayout.Tab {
        override fun measureTextWidth(): Float = AndroidUtilities.dpf2(textWidthDp)
    }
}
