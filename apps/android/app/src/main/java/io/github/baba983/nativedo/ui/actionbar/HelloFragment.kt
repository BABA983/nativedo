package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.event.NotificationCenterDelegate
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.core.theme.ThemeMode
import io.github.baba983.nativedo.core.util.AndroidUtilities

class HelloFragment(
    private val depth: Int = 1,
) : BaseFragment() {
    companion object {
        const val NavigationKey: String = "hello"
        private const val DepthArgumentKey: String = "depth"

        fun restore(arguments: Bundle?): HelloFragment {
            return HelloFragment(
                depth = arguments?.getInt(DepthArgumentKey, 1) ?: 1,
            )
        }
    }

    override fun createView(context: Context): View {
        return HelloPageView(
            context = context,
            depth = depth,
            onPushNext = {
                parentLayout?.presentFragment(
                    HelloFragment(depth = depth + 1),
                    animated = true,
                )
            },
            onToggleTheme = {
                NdTheme.setThemeMode(NdTheme.getThemeMode().next())
            },
        )
    }

    override fun onCreateActionBar(context: Context): ActionBar {
        return ActionBar(context).apply {
            setTitle(
                if (depth == 1) {
                    "NativeDo Rewrite"
                } else {
                    "Rewrite Page $depth"
                },
            )
            setBackButtonVisible(depth > 1)
        }
    }

    override fun getNavigationKey(): String = NavigationKey

    override fun saveArguments(): Bundle {
        return Bundle().apply {
            putInt(DepthArgumentKey, depth)
        }
    }

    private class HelloPageView(
        context: Context,
        private val depth: Int,
        private val onPushNext: () -> Unit,
        private val onToggleTheme: () -> Unit,
    ) : ScrollView(context), NotificationCenterDelegate {
        private val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(24f))
        }
        private val titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            text = if (depth == 1) {
                "Rewrite Root Ready"
            } else {
                "Rewrite Page $depth"
            }
        }
        private val bodyView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = if (depth == 1) {
                "rewrite 根栈已接入 NdTheme。切换主题时，ActionBar、背景、文本会一起渐变。"
            } else {
                "这是用 ActionBarLayout push 进来的第 $depth 个页面。主题切换会立刻广播到当前页。"
            }
        }
        private val pushButton = buildButton("Push Next Page") {
            onPushNext()
        }
        private val themeButton = buildButton("") {
            onToggleTheme()
        }
        private val footnoteView = TextView(context).apply {
            gravity = Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text = "返回键或顶部返回按钮会 pop 当前页面。"
        }

        init {
            background = NdTheme.getThemedDrawable(NdTheme.key_windowBackgroundGray)
            addView(
                contentLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            contentLayout.addView(titleView)
            contentLayout.addView(bodyView, linearParams(topMarginDp = 12f))
            contentLayout.addView(pushButton, linearParams(topMarginDp = 20f))
            contentLayout.addView(themeButton, linearParams(topMarginDp = 12f))
            contentLayout.addView(footnoteView, linearParams(topMarginDp = 16f))
            refreshTheme()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            NotificationCenter.addObserver(this, NotificationCenter.themeDidChange)
            refreshTheme()
        }

        override fun onDetachedFromWindow() {
            NotificationCenter.removeObserver(this, NotificationCenter.themeDidChange)
            super.onDetachedFromWindow()
        }

        override fun didReceivedNotification(id: Int, vararg args: Any?) {
            if (id == NotificationCenter.themeDidChange) {
                refreshTheme()
            }
        }

        private fun refreshTheme() {
            titleView.setTextColor(NdTheme.getColor(NdTheme.key_topic_title))
            bodyView.setTextColor(NdTheme.getColor(NdTheme.key_topic_excerpt))
            footnoteView.setTextColor(NdTheme.getColor(NdTheme.key_topic_metadata))
            pushButton.setTextColor(resolveAccentTextColor())
            themeButton.setTextColor(NdTheme.getColor(NdTheme.key_windowBackgroundWhiteBlackText))
            themeButton.text = "Theme Mode: ${themeModeLabel(NdTheme.getThemeMode())}"
            invalidate()
            contentLayout.invalidate()
            pushButton.invalidate()
            themeButton.invalidate()
        }

        private fun buildButton(
            label: String,
            onClick: () -> Unit,
        ): TextView {
            return TextView(context).apply {
                gravity = Gravity.CENTER
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dp(44f)
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        private fun linearParams(topMarginDp: Float): LinearLayout.LayoutParams {
            return LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(topMarginDp)
            }
        }

        private fun resolveAccentTextColor(): Int {
            return if (NdTheme.isDarkTheme()) {
                NdTheme.getColor(NdTheme.key_windowBackgroundGray)
            } else {
                NdTheme.getColor(NdTheme.key_windowBackgroundWhite)
            }
        }

        private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
            ThemeMode.System -> "System"
            ThemeMode.Light -> "Light"
            ThemeMode.Dark -> "Dark"
        }

        private fun dp(value: Float): Int = AndroidUtilities.dp(value)

        init {
            pushButton.background = NdTheme.createRoundRectSelectorDrawable(
                colorKey = NdTheme.key_topic_categoryBadge,
                pressedColorKey = NdTheme.key_actionBarDefaultSelector,
                radiusDp = 22f,
            )
            themeButton.background = NdTheme.createRoundRectSelectorDrawable(
                colorKey = NdTheme.key_dialogBackground,
                pressedColorKey = NdTheme.key_actionBarDefaultSelector,
                radiusDp = 22f,
            )
        }
    }
}
