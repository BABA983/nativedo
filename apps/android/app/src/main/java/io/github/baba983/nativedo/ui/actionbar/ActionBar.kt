package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class ActionBar(
    context: Context,
) : FrameLayout(context) {
    private val backButtonView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#0F172A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        text = "Back"
        visibility = GONE
    }
    private val titleView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.parseColor("#0F172A"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }

    init {
        setBackgroundColor(Color.parseColor("#F8FAFC"))
        minimumHeight = dp(56f)
        val horizontalPadding = dp(16f)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)

        addView(
            backButtonView,
            LayoutParams(dp(64f), LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
        )
        addView(
            titleView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(72f)
            },
        )
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setBackButtonVisible(visible: Boolean) {
        backButtonView.visibility = if (visible) VISIBLE else GONE
    }

    fun setOnBackClickListener(listener: (() -> Unit)?) {
        backButtonView.setOnClickListener(
            if (listener == null) {
                null
            } else {
                OnClickListener { listener() }
            },
        )
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        ).toInt()
    }
}
