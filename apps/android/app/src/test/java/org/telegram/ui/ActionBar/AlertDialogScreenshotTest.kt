package org.telegram.ui.ActionBar

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper

internal class AlertDialogScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun messageDialogUsesTelegramInputAndButtonChrome() {
        paparazziRule.snapshot { context ->
            val input = EditTextBoldCursor(context).apply {
                hint = "https://linux.do/t/topic/"
                setSingleLine(true)
                setTextSize(16f)
                setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                setHintTextColor(Theme.getColor(Theme.key_dialogIcon))
                setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
                background = GradientDrawable().apply {
                    setColor(Theme.getColor(Theme.key_dialogBackgroundGray))
                    cornerRadius = AndroidUtilities.dpf2(10f)
                }
            }
            val inputContainer = FrameLayout(context).apply {
                setPadding(AndroidUtilities.dp(24f), 0, AndroidUtilities.dp(24f), AndroidUtilities.dp(4f))
                addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP))
            }
            val dialog = AlertDialog.Builder(context)
                .setTitle("Open topic URL")
                .setMessage("Enter a linux.do topic URL to inspect its native rendering.")
                .setView(inputContainer)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open", null)
                .create()
            val createContent = AlertDialog::class.java.getDeclaredMethod("createMessageContent")
                .apply { isAccessible = true }
            createContent.invoke(dialog) as View
        }
    }
}
