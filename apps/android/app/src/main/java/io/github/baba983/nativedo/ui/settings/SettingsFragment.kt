/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of org/telegram/ui/SettingsActivity.java, bound to NativeDo
 * account, appearance, browser, and developer settings.
 */
package io.github.baba983.nativedo.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.baba983.nativedo.BuildConfig
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.web.normalizeHttpUrl
import io.github.baba983.nativedo.core.web.parseSameOriginTopicId
import io.github.baba983.nativedo.debug.DebugTopicFixtureSheet
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.settings.IconSettingCell.Factory as IconSettingFactory
import io.github.baba983.nativedo.ui.theme.ThemeSettingsFragment
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import io.github.baba983.nativedo.ui.topic.body.RichMessageBody
import java.util.ArrayList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.web.WebBrowserSettings

private const val SETTINGS_ACTION_ACCOUNT = 1
private const val SETTINGS_ACTION_APPEARANCE = 2
private const val SETTINGS_ACTION_BROWSER = 3
private const val SETTINGS_ACTION_LOGOUT = 4
private const val SETTINGS_ACTION_DEV_URL = 20
private const val SETTINGS_ACTION_TOPIC_FIXTURES = 21
private const val SETTINGS_ACTION_TOPIC_URL = 22
private const val SETTINGS_ACTION_RICH_MESSAGE_BODY = 23

class SettingsFragment : SessionSettingsFragment(), MainTabsActivity.TabFragmentDelegate {
    private var topicFixtureSheet: DebugTopicFixtureSheet? = null
    private var sessionUsername: String? = null

    override fun createView(context: Context): View {
        val view = super.createView(context)
        actionBar.setBackButtonDrawable(null)
        listView.setPadding(
            0,
            0,
            0,
            AndroidUtilities.dp(MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS.toFloat()),
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            listView.setPadding(
                0,
                0,
                0,
                navigationBarInset + AndroidUtilities.dp(MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS.toFloat()),
            )
            WindowInsetsCompat.CONSUMED
        }
        viewCollectors += fragmentScope.launch {
            appContainer.sessionCoordinator.sessionState.collectLatest { state ->
                sessionUsername = (state as? AppSessionState.Authenticated)?.username
                listView.adapter?.update(false)
            }
        }
        return view
    }

    override fun getTitle(): CharSequence = string(R.string.profile_settings_title)

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun drawEdgeNavigationBar(): Boolean = false

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val username = sessionUsername
        if (username != null) {
            items.add(
                IconSettingFactory.of(
                    SETTINGS_ACTION_ACCOUNT,
                    IconSettingCell.Colors.BLUE_TOP,
                    IconSettingCell.Colors.BLUE_BOTTOM,
                    R.drawable.settings_account,
                    string(R.string.profile_settings_account_title),
                    string(R.string.profile_settings_account_subtitle),
                    "@$username",
                ),
            )
            items.add(UItem.asShadow(null))
        }

        items.add(UItem.asHeader(string(R.string.profile_app_section_title)))
        items.add(
            IconSettingFactory.of(
                SETTINGS_ACTION_APPEARANCE,
                IconSettingCell.Colors.ORANGE_TOP,
                IconSettingCell.Colors.ORANGE_BOTTOM,
                R.drawable.settings_chat,
                string(R.string.profile_appearance_title),
                appearanceThemeSummary(),
                if (Theme.isCurrentThemeDark()) {
                    string(R.string.profile_appearance_value_night)
                } else {
                    string(R.string.profile_appearance_value_day)
                },
            ),
        )
        items.add(
            IconSettingFactory.of(
                SETTINGS_ACTION_BROWSER,
                IconSettingCell.Colors.BLUE_TOP,
                IconSettingCell.Colors.BLUE_BOTTOM,
                R.drawable.settings_link,
                string(R.string.profile_browser_settings_title),
                string(R.string.profile_browser_settings_subtitle),
            ),
        )
        items.add(UItem.asShadow(null))

        addDevToolsItems(items)

        if (appContainer.sessionCoordinator.sessionState.value is AppSessionState.Authenticated) {
            items.add(UItem.asHeader(string(R.string.profile_account_title)))
            items.add(
                IconSettingFactory.of(
                    SETTINGS_ACTION_LOGOUT,
                    IconSettingCell.Colors.RED_TOP,
                    IconSettingCell.Colors.RED_BOTTOM,
                    R.drawable.settings_logout,
                    string(R.string.profile_logout_action),
                    string(R.string.profile_logout_subtitle),
                ),
            )
            items.add(UItem.asShadow(null))
        }

        if (profileVersionFooterReplacesTerminalShadow(items.lastOrNull()?.viewType)) {
            items.removeAt(items.lastIndex)
        }
        items.add(UItem.asCenterShadow("NativeDo v${BuildConfig.VERSION_NAME}"))
    }

    private fun addDevToolsItems(items: ArrayList<UItem>) {
        if (!BuildConfig.DEBUG) return
        items.add(UItem.asHeader(string(R.string.profile_debug_title)))
        items.add(
            IconSettingFactory.of(
                SETTINGS_ACTION_DEV_URL,
                IconSettingCell.Colors.BLUE_TOP,
                IconSettingCell.Colors.BLUE_BOTTOM,
                R.drawable.settings_link,
                string(R.string.profile_dev_url_title),
                string(R.string.profile_dev_tools_subtitle),
            ),
        )
        items.add(
            IconSettingFactory.of(
                SETTINGS_ACTION_TOPIC_FIXTURES,
                IconSettingCell.Colors.ORANGE_TOP,
                IconSettingCell.Colors.ORANGE_BOTTOM,
                R.drawable.settings_topic,
                string(R.string.profile_topic_fixture_tools_title),
                string(R.string.profile_topic_fixture_tools_subtitle),
            ),
        )
        items.add(
            IconSettingFactory.of(
                SETTINGS_ACTION_TOPIC_URL,
                IconSettingCell.Colors.GREEN_TOP,
                IconSettingCell.Colors.GREEN_BOTTOM,
                R.drawable.settings_topic,
                string(R.string.profile_topic_dev_url_title),
                string(R.string.profile_topic_dev_tools_subtitle),
            ),
        )
        items.add(
            UItem.asCheck(
                SETTINGS_ACTION_RICH_MESSAGE_BODY,
                string(R.string.profile_dev_rich_message_body_title),
            ).setChecked(RichMessageBody.enabled),
        )
        items.add(UItem.asShadow(string(R.string.profile_dev_rich_message_body_subtitle)))
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            SETTINGS_ACTION_ACCOUNT -> presentFragment(AccountDetailsFragment())
            SETTINGS_ACTION_APPEARANCE -> presentFragment(ThemeSettingsFragment())
            SETTINGS_ACTION_BROWSER -> openBrowserSettings()
            SETTINGS_ACTION_RICH_MESSAGE_BODY -> {
                RichMessageBody.setEnabled(!RichMessageBody.enabled)
                listView?.adapter?.update(true)
            }
            SETTINGS_ACTION_LOGOUT -> showLogoutConfirm()
            SETTINGS_ACTION_DEV_URL -> showUrlInputDialog(
                title = string(R.string.profile_dev_url_title),
                message = string(R.string.profile_dev_url_message),
                hint = string(R.string.profile_dev_url_hint),
                actionText = string(R.string.profile_dev_url_open),
                invalidText = string(R.string.profile_dev_url_invalid),
                parser = ::normalizeHttpUrl,
                onSubmit = ::openBrowser,
            )
            SETTINGS_ACTION_TOPIC_FIXTURES -> showTopicRenderFixturesSheet()
            SETTINGS_ACTION_TOPIC_URL -> showUrlInputDialog(
                title = string(R.string.profile_topic_dev_url_title),
                message = string(R.string.profile_topic_dev_url_message),
                hint = string(R.string.profile_topic_dev_url_hint),
                initialText = "https://linux.do/t/topic/",
                actionText = string(R.string.profile_topic_dev_url_open),
                invalidText = string(R.string.profile_topic_dev_url_invalid),
                parser = { parseSameOriginTopicId(it, appContainer.baseUrl)?.toString() },
                onSubmit = { topicId -> presentFragment(TopicDetailFragment(topicId.toLong())) },
            )
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean = false

    override fun onParentScrollToTop() {
        listView.smoothScrollToPosition(0)
    }

    override fun canParentTabsSlide(event: MotionEvent, forward: Boolean): Boolean = true

    override fun onResume() {
        super.onResume()
        if (isSessionContainerReady()) {
            listView?.adapter?.update(false)
        }
    }

    override fun onFragmentDestroy() {
        topicFixtureSheet?.dismiss()
        topicFixtureSheet = null
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }

    private fun appearanceThemeSummary(): String {
        val dayName = Theme.getCurrentTheme()?.getName().orEmpty()
        val nightName = Theme.getCurrentNightTheme()?.getName().orEmpty()
        return context?.getString(R.string.profile_appearance_subtitle, dayName, nightName).orEmpty()
    }

    private fun showLogoutConfirm() {
        val activity = parentActivity ?: return
        val dialog = AlertDialog.Builder(activity, resourceProvider)
            .setTitle(activity.getString(R.string.profile_logout_confirm_title))
            .setMessage(activity.getString(R.string.profile_logout_confirm_message))
            .setPositiveButton(activity.getString(R.string.profile_logout_action)) { _, _ ->
                fragmentScope.launch {
                    appContainer.sessionCoordinator.onLogout()
                }
            }
            .setNegativeButton(activity.getString(R.string.action_cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .create()
        showDialog(dialog)
    }

    private fun showTopicRenderFixturesSheet() {
        val currentContext = context ?: return
        topicFixtureSheet?.dismiss()
        topicFixtureSheet = DebugTopicFixtureSheet(
            context = currentContext,
            fragment = this,
            onDismissed = { topicFixtureSheet = null },
        ).also { it.show() }
    }

    private fun showUrlInputDialog(
        title: String,
        message: String,
        hint: String,
        initialText: String = "",
        actionText: String,
        invalidText: String,
        parser: (String) -> String?,
        onSubmit: (String) -> Unit,
    ) {
        val currentContext = context ?: return
        val input = EditTextBoldCursor(currentContext).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintTextColor(
                ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_dialogIcon, resourceProvider),
                    180,
                ),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            background = inputBackground()
            setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
        }
        val inputContainer = FrameLayout(currentContext).apply {
            setPadding(AndroidUtilities.dp(24f), 0, AndroidUtilities.dp(24f), AndroidUtilities.dp(4f))
            addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP))
        }
        fun submit(dialog: android.content.DialogInterface) {
            val parsed = parser(input.text?.toString().orEmpty())
            if (parsed == null) {
                input.error = invalidText
            } else {
                dialog.dismiss()
                onSubmit(parsed)
            }
        }
        val dialog = AlertDialog.Builder(currentContext, resourceProvider)
            .setTitle(title)
            .setMessage(message)
            .setView(inputContainer)
            .setNegativeButton(currentContext.getString(android.R.string.cancel)) { currentDialog, _ ->
                currentDialog.dismiss()
            }
            .setPositiveButton(actionText) { currentDialog, _ -> submit(currentDialog) }
            .create()
        dialog.setDismissDialogByButtons(false)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit(dialog)
                true
            } else {
                false
            }
        }
        showDialog(dialog)
        input.post {
            input.requestFocus()
            currentContext.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun openBrowserSettings() {
        val params = BaseFragment.BottomSheetParams().apply { transitionFromLeft = true }
        showAsSheet(
            WebBrowserSettings { entry ->
                Browser.openUrl(parentActivity ?: return@WebBrowserSettings, entry.url)
            },
            params,
        )
    }

    private fun openBrowser(url: String) {
        Browser.openUrl(context ?: return, url)
    }

    private fun inputBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Theme.getColor(Theme.key_dialogBackgroundGray, resourceProvider))
        cornerRadius = AndroidUtilities.dpf2(10f)
        setStroke(
            AndroidUtilities.dp(1f),
            ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_dialogIcon, resourceProvider),
                96,
            ),
        )
    }
}
