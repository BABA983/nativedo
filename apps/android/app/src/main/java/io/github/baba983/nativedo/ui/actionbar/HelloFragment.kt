package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HelloFragment(
    private val depth: Int = 1,
) : BaseFragment() {
    override fun createView(context: Context): View {
        return ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 20f), dp(context, 24f), dp(context, 20f), dp(context, 24f))
                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#0F172A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                            text = if (depth == 1) {
                                "Rewrite Root Ready"
                            } else {
                                "Rewrite Page $depth"
                            }
                        },
                    )
                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#334155"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            text = if (depth == 1) {
                                "现在这已经不是 placeholder 了，而是 rewrite 根栈里的第一个原生页面。"
                            } else {
                                "这是用 ActionBarLayout push 进来的第 $depth 个页面。"
                            }
                        },
                    )
                    addView(
                        Button(context).apply {
                            text = "Push Next Page"
                            setOnClickListener {
                                parentLayout?.presentFragment(
                                    HelloFragment(depth = depth + 1),
                                    animated = true,
                                )
                            }
                        },
                    )
                    addView(
                        TextView(context).apply {
                            gravity = Gravity.START
                            setTextColor(Color.parseColor("#475569"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            text = "Back 键或顶部返回按钮会 pop 当前页面。"
                        },
                    )
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
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

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        ).toInt()
    }
}
