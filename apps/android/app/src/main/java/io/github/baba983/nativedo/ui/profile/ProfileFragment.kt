package io.github.baba983.nativedo.ui.profile

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.ColorUtils
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.browser.BrowserSheet

class ProfileFragment : BaseFragment() {

    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.tab_profile))
        actionBar.setBackButtonDrawable(null)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    ProfileScreen(
                        onDevToolClick = { showDevUrlDialog(context) },
                    )
                }
            }
        }
    }

    private fun showDevUrlDialog(context: Context) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val input = EditText(context).apply {
            hint = context.getString(R.string.profile_dev_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setHintTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 180))
            textSize = 16f
            background = roundedDrawable(
                color = Theme.getColor(Theme.key_windowBackgroundGray),
                radiusDp = 12f,
                strokeColor = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 210),
                strokeDp = 1f,
            )
            setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
        }

        fun submit() {
            val url = normalizeHttpUrl(input.text.toString())
            if (url == null) {
                input.error = context.getString(R.string.profile_dev_url_invalid)
            } else {
                dialog.dismiss()
                openBrowser(url)
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AndroidUtilities.dp(22f),
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(22f),
                AndroidUtilities.dp(8f),
            )
            background = roundedDrawable(
                color = Theme.getColor(Theme.key_windowBackgroundWhite),
                radiusDp = 22f,
            )
            elevation = AndroidUtilities.dpf2(14f)
        }

        content.addView(
            TextView(context).apply {
                setText(R.string.profile_dev_url_title)
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(
            TextView(context).apply {
                setText(R.string.profile_dev_url_message)
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(AndroidUtilities.dpf2(2f), 1f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = AndroidUtilities.dp(8f)
            },
        )

        content.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(50f),
            ).apply {
                topMargin = AndroidUtilities.dp(18f)
            },
        )

        content.addView(
            LinearLayout(context).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(
                    createDialogButton(context, context.getString(android.R.string.cancel), false) {
                        dialog.dismiss()
                    },
                )
                addView(
                    createDialogButton(context, context.getString(R.string.profile_dev_url_open), true) {
                        submit()
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(56f),
            ).apply {
                topMargin = AndroidUtilities.dp(8f)
            },
        )

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }

        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.42f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setLayout(
                minOf(context.resources.displayMetrics.widthPixels - AndroidUtilities.dp(48f), AndroidUtilities.dp(336f)),
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        input.requestFocus()
    }

    private fun normalizeHttpUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
            return uri.toString()
        }
        return null
    }

    private fun openBrowser(url: String) {
        val sheet = BrowserSheet(this, url)
        addSheet(sheet)
        sheet.show()
    }

    private fun createDialogButton(
        context: Context,
        text: String,
        primary: Boolean,
        onClick: () -> Unit,
    ): TextView {
        val primaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
        return TextView(context).apply {
            this.text = text.uppercase()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (primary) {
                    primaryColor
                } else {
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
                },
            )
            minWidth = AndroidUtilities.dp(64f)
            minHeight = AndroidUtilities.dp(40f)
            setPadding(AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f), 0)
            background = Theme.createSelectorDrawable(
                ColorUtils.setAlphaComponent(primaryColor, if (primary) 28 else 18),
                Theme.RIPPLE_MASK_ALL,
            )
            setOnClickListener { onClick() }
        }
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Float = 0f,
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = AndroidUtilities.dpf2(radiusDp)
            if (strokeDp > 0f) {
                setStroke(AndroidUtilities.dp(strokeDp), strokeColor)
            }
        }
    }
}
