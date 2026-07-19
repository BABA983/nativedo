package org.telegram.ui.ActionBar

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

/**
 * Contract: ThemeDescription can recolor a simple FLAG_TEXTCOLOR/BACKGROUND target
 * and Theme.applyThemeDescriptions drives a list after setColor updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeDescriptionContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
        AndroidUtilities.init(context)
        Theme.init(context)
    }

    @Test
    fun textColorFlagRecolorsTextView() {
        val text = TextView(context)
        text.setTextColor(Color.BLACK)
        val description = ThemeDescription(
            text,
            ThemeDescription.FLAG_TEXTCOLOR,
            null,
            null as android.graphics.Paint?,
            null,
            null,
            Theme.key_windowBackgroundWhiteBlackText,
        )
        val expected = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        description.setColor(expected, false, false)
        assertThat(text.currentTextColor).isEqualTo(expected)
    }

    @Test
    fun backgroundFlagRecolorsView() {
        val view = View(context)
        val description = ThemeDescription(
            view,
            ThemeDescription.FLAG_BACKGROUND,
            null,
            null as android.graphics.Paint?,
            null,
            null,
            Theme.key_windowBackgroundWhite,
        )
        val expected = Theme.getColor(Theme.key_windowBackgroundWhite)
        description.setColor(expected, false, false)
        // ColorDrawable or solid background color
        assertThat(view.background).isNotNull()
    }

    @Test
    fun applyThemeDescriptionsUsesCurrentThemeColor() {
        val root = FrameLayout(context)
        val text = TextView(context)
        root.addView(text)
        val descriptions = arrayListOf(
            ThemeDescription(
                text,
                ThemeDescription.FLAG_TEXTCOLOR,
                null,
                null as android.graphics.Paint?,
                null,
                null,
                Theme.key_windowBackgroundWhiteBlackText,
            ),
        )
        Theme.applyThemeDescriptions(descriptions)
        assertThat(text.currentTextColor)
            .isEqualTo(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
    }

    @Test
    fun setColorWithSaveWritesCurrentColors() {
        val key = Theme.key_windowBackgroundWhiteBlackText
        val original = Theme.getNonAnimatedColor(key)
        val override = 0xff112233.toInt()
        val description = ThemeDescription(
            View(context),
            ThemeDescription.FLAG_TEXTCOLOR,
            null,
            null as android.graphics.Paint?,
            null,
            null,
            key,
        )
        description.setColor(override, false, true)
        assertThat(Theme.getNonAnimatedColor(key)).isEqualTo(override)
        // restore
        Theme.setColor(key, original, false)
    }

    @Test
    fun baseFragmentDefaultThemeDescriptionsEmpty() {
        val fragment = object : BaseFragment() {
            override fun createView(context: Context): View = View(context)
        }
        assertThat(fragment.themeDescriptions).isEmpty()
    }
}
