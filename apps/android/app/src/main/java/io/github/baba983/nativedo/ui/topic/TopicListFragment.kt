package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.AndroidUtilities
import io.github.baba983.nativedo.ui.actionbar.ActionBar
import io.github.baba983.nativedo.ui.actionbar.BaseFragment
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.main.MainTabsActivity

private const val MENU_SWITCH_THEME = 1

class TopicListFragment : BaseFragment() {

    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.screen_topic_list))
        actionBar.setBackButtonDrawable(null)
        applyCurrentTheme(context)
        fun themeModeText(): String {
            val id = if (Theme.isCurrentThemeDark()) R.string.theme_mode_day else R.string.theme_mode_night
            return context.getString(id)
        }
        actionBar.createMenu()
            .addItem(0, MoreDrawable())
            .apply {
                contentDescription = context.getString(R.string.action_more_options)
                addSubItem(MENU_SWITCH_THEME, themeModeText())
            }
        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == MENU_SWITCH_THEME) {
                        Theme.toggleTheme()
                        applyCurrentTheme(context)
                        actionBar.menu?.getItem(0)?.setSubItemText(MENU_SWITCH_THEME, themeModeText())
                    }
                }
            },
        )

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    TopicListScreen(
                        topics = MockTopics.all,
                        mainTabsContentPadding = MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS,
                        onTopicClick = { topic ->
                            presentFragment(TopicDetailFragment(topic.id))
                        },
                    )
                }
            }
        }
    }

    private fun applyCurrentTheme(context: Context) {
        actionBar.applyTheme()
        (parentLayout as? View)?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        (context as? Activity)?.let {
            AndroidUtilities.setLightStatusBar(it.window, !Theme.isCurrentThemeDark())
        }
    }
}

private class MoreDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val spacing = AndroidUtilities.dpf2(5f)
        val radius = AndroidUtilities.dpf2(1.6f)
        canvas.drawCircle(centerX, centerY - spacing, radius, paint)
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawCircle(centerX, centerY + spacing, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(24f)

    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(24f)
}
