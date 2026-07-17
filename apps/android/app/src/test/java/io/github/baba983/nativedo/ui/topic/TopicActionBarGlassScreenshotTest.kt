package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor

internal class TopicActionBarGlassScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun topicActionBarUsesOnlyOpaqueCircleAndPillSurfaces() {
        paparazziRule.snapshot { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(104f),
                )
                setBackgroundColor(Color.rgb(103, 157, 213))
                addView(
                    createActionBar(context, withIdentity = false),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP,
                    ),
                )
            }
        }
    }

    @Test
    fun topicActionBarIdentityL2SitsInsideMiddleGlassPill() {
        paparazziRule.snapshot { context ->
            // Occupy status bar like production TopicDetail so the identity strip
            // must self-offset by statusBarHeight into the action-bar content band.
            AndroidUtilities.statusBarHeight = AndroidUtilities.dp(24f)
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(16f),
                )
                setBackgroundColor(Color.rgb(103, 157, 213))
                addView(
                    createActionBar(context, withIdentity = true),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP,
                    ),
                )
            }
        }
    }

    private fun createActionBar(context: Context, withIdentity: Boolean): ActionBar = ActionBar(context).apply {
        setBackButtonDrawable(BackDrawable(false))
        setTitle(if (withIdentity) "" else "Topic title")
        setOccupyStatusBar(withIdentity)
        // Menu pill: single ⋯ (search lives under overflow in production).
        createMenu().addItem(0, R.drawable.ic_ab_other, null)
        val source = BlurredBackgroundSourceColor().apply {
            setColor(Color.rgb(103, 157, 213))
        }
        setupGlass(
            BlurredBackgroundDrawableViewFactory(source),
            object : BlurredBackgroundColorProvider {
                override fun getShadowColor(): Int = 0

                override fun getBackgroundColor(): Int = Theme.getColor(Theme.key_actionBarDefault)

                override fun getStrokeColorTop(): Int = 0

                override fun getStrokeColorBottom(): Int = 0
            },
        )
        if (withIdentity) {
            titleTextView.visibility = View.GONE
            val identity = TopicIdentityContainer(context).apply {
                setOccupyStatusBar(true)
                bind(
                    title = "Topic identity title that can ellipsize",
                    subtitle = "开发 · 12 replies · 340 views",
                    avatarUrl = "",
                    displayName = "Baba",
                )
                setIdentityVisible(true)
            }
            // ChatAvatarContainer: WRAP_CONTENT + left 53 / right 52 (⋯ only).
            addView(
                identity,
                0,
                LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.MATCH_PARENT.toFloat(),
                    Gravity.TOP or Gravity.LEFT,
                    53f,
                    0f,
                    52f,
                    0f,
                ),
            )
            backButtonImageView.bringToFront()
            createMenu().bringToFront()
        }
    }
}
