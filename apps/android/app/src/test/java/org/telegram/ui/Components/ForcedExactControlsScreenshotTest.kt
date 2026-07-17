package org.telegram.ui.Components

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

/**
 * Visual golden for small `forced_exact` Telegram controls used across NativeDo.
 *
 * Covers:
 * - [Switch] (drawing, animation)
 * - [CheckBox2] / [CheckBoxBase] (drawing, animation)
 * - [CircularProgressDrawable] (drawing, animation)
 *
 * Intent: catch host-wiring regressions that change track/thumb geometry or
 * default checked appearance without requiring a full sheet/page snapshot.
 */
internal class ForcedExactControlsScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun switchUncheckedAndCheckedShareTelegramTrackGeometry() {
        paparazziRule.snapshot { context ->
            controlRow(context) {
                addView(
                    Switch(context).apply {
                        setColors(
                            Theme.key_switchTrack,
                            Theme.key_switchTrackChecked,
                            Theme.key_windowBackgroundWhite,
                            Theme.key_windowBackgroundWhite,
                        )
                        setChecked(false, false)
                    },
                    LinearLayout.LayoutParams(
                        AndroidUtilities.dp(31f),
                        AndroidUtilities.dp(20f),
                    ).apply { rightMargin = AndroidUtilities.dp(16f) },
                )
                addView(
                    Switch(context).apply {
                        setColors(
                            Theme.key_switchTrack,
                            Theme.key_switchTrackChecked,
                            Theme.key_windowBackgroundWhite,
                            Theme.key_windowBackgroundWhite,
                        )
                        setChecked(true, false)
                    },
                    LinearLayout.LayoutParams(
                        AndroidUtilities.dp(31f),
                        AndroidUtilities.dp(20f),
                    ),
                )
            }
        }
    }

    @Test
    fun checkBoxUncheckedCheckedAndNumberedKeepBaseGeometry() {
        paparazziRule.snapshot { context ->
            controlRow(context) {
                val size = AndroidUtilities.dp(24f)
                addView(
                    CheckBox2(context, 21).apply {
                        setColor(
                            Theme.key_checkbox,
                            Theme.key_checkboxDisabled,
                            Theme.key_checkboxCheck,
                        )
                        setDrawUnchecked(true)
                        setChecked(false, false)
                    },
                    LinearLayout.LayoutParams(size, size).apply {
                        rightMargin = AndroidUtilities.dp(16f)
                    },
                )
                addView(
                    CheckBox2(context, 21).apply {
                        setColor(
                            Theme.key_checkbox,
                            Theme.key_checkboxDisabled,
                            Theme.key_checkboxCheck,
                        )
                        setDrawUnchecked(true)
                        setChecked(true, false)
                    },
                    LinearLayout.LayoutParams(size, size).apply {
                        rightMargin = AndroidUtilities.dp(16f)
                    },
                )
                addView(
                    CheckBox2(context, 21).apply {
                        setColor(
                            Theme.key_checkbox,
                            Theme.key_checkboxDisabled,
                            Theme.key_checkboxCheck,
                        )
                        setDrawUnchecked(true)
                        setChecked(3, true, false)
                    },
                    LinearLayout.LayoutParams(size, size),
                )
            }
        }
    }

    @Test
    fun circularProgressDefaultStrokeGeometry() {
        paparazziRule.snapshot { context ->
            val size = AndroidUtilities.dp(40f)
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    size + AndroidUtilities.dp(24f),
                )
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                setPadding(
                    AndroidUtilities.dp(16f),
                    AndroidUtilities.dp(12f),
                    AndroidUtilities.dp(16f),
                    AndroidUtilities.dp(12f),
                )
                val progress = ImageView(context).apply {
                    setImageDrawable(
                        CircularProgressDrawable(
                            AndroidUtilities.dp(18f).toFloat(),
                            AndroidUtilities.dp(2.25f).toFloat(),
                            Theme.getColor(Theme.key_featuredStickers_addButton),
                        ).also { it.reset() },
                    )
                }
                addView(
                    progress,
                    FrameLayout.LayoutParams(size, size, Gravity.CENTER),
                )
            }
        }
    }

    private fun controlRow(
        context: Context,
        build: LinearLayout.() -> Unit,
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            setPadding(
                AndroidUtilities.dp(16f),
                AndroidUtilities.dp(16f),
                AndroidUtilities.dp(16f),
                AndroidUtilities.dp(16f),
            )
            // Stable backdrop so theme glass/wallpaper noise cannot flake goldens.
            background = ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite))
            build()
        }
    }
}
