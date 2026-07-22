package org.telegram.ui.ActionBar

import android.content.Context
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.R

internal class ActionBarMenuThemeContractTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule()

    @Test
    fun customPopupBackgroundUpdatesLayoutThemeState() {
        paparazziRule.verify { context ->
            val item = actionBar(context).createMenu().addItem(1, R.drawable.ic_ab_other)
            item.addSubItem(2, R.drawable.msg_search, "Search")

            val popupColor = 0xf9222222.toInt()
            item.redrawPopup(popupColor)

            assertThat(popupLayout(item).backgroundColor).isEqualTo(popupColor)
        }
    }

    @Test
    fun applyThemeRefreshesSubmenuTextAndBackground() {
        paparazziRule.verify { context ->
            val actionBar = actionBar(context)
            val item = actionBar.createMenu().addItem(1, R.drawable.ic_ab_other)
            val iconItem = item.addSubItem(2, R.drawable.msg_search, "Search")
            val textItem = item.addSubItem(3, "Theme")
            iconItem.setTextColor(Color.MAGENTA)
            textItem.setTextColor(Color.MAGENTA)
            item.redrawPopup(Color.MAGENTA)

            actionBar.applyTheme()

            val expectedText = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem)
            assertThat(iconItem.textView.currentTextColor).isEqualTo(expectedText)
            assertThat(textItem.currentTextColor).isEqualTo(expectedText)
            assertThat(popupLayout(item).backgroundColor)
                .isEqualTo(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
        }
    }

    @Test
    fun resourcesProviderReachesActionBarPopupSubitemAndSearch() {
        paparazziRule.verify { context ->
            val titleColor = 0xff102030.toInt()
            val submenuTextColor = 0xfff1e2d3.toInt()
            val submenuBackgroundColor = 0xff203040.toInt()
            val searchColor = 0xffa1b2c3.toInt()
            val provider = object : Theme.ResourcesProvider {
                override fun getColor(key: Int): Int = when (key) {
                    Theme.key_actionBarDefaultTitle -> titleColor
                    Theme.key_actionBarDefaultSubmenuItem -> submenuTextColor
                    Theme.key_actionBarDefaultSubmenuItemIcon -> submenuTextColor
                    Theme.key_actionBarDefaultSubmenuBackground -> submenuBackgroundColor
                    Theme.key_actionBarDefaultSearch -> searchColor
                    else -> Theme.getColor(key)
                }
            }
            val actionBar = actionBar(context, provider)
            val menu = actionBar.createMenu()
            val overflow = menu.addItem(1, R.drawable.ic_ab_other, provider)
            val subItem = overflow.addSubItem(2, R.drawable.msg_search, "Search")
            val searchItem = menu.addItem(3, R.drawable.msg_search, provider).setIsSearchField(true)

            assertThat(actionBar.titleTextView.textColor).isEqualTo(titleColor)
            assertThat(subItem.textView.currentTextColor).isEqualTo(submenuTextColor)
            assertThat(popupLayout(overflow).backgroundColor).isEqualTo(submenuBackgroundColor)
            assertThat(searchItem.searchField.currentTextColor).isEqualTo(searchColor)
        }
    }

    private fun actionBar(context: Context, resourcesProvider: Theme.ResourcesProvider? = null): ActionBar =
        ActionBar(context, resourcesProvider)

    private fun popupLayout(item: ActionBarMenuItem): ActionBarPopupWindow.ActionBarPopupWindowLayout {
        val field = ActionBarMenuItem::class.java.getDeclaredField("popupLayout")
        field.isAccessible = true
        return field.get(item) as ActionBarPopupWindow.ActionBarPopupWindowLayout
    }
}
