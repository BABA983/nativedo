package io.github.baba983.nativedo.settings

import android.content.Context
import io.github.baba983.nativedo.hooks.NativeDoHooks

/**
 * The reader's "在应用内打开链接" choice: whether an ordinary link opens in Telegram's in-app
 * browser or in the system's custom tab.
 *
 * <p>Upstream keeps the equivalent choice in its server-delivered
 * `MessagesController.webBrowserSettings`, which never arrives here. This is the local store for
 * that choice, and it is also what applies it: [NativeDoHooks.setWebBrowserOpenInAppEnabled] is
 * what `MessagesController.isWebBrowserOpenInApp` reads, so the browser asks the hook and the hook
 * answers with this preference.</p>
 */
object LinkOpenSettings {

    private const val PREFERENCES = "nativedo_settings"
    private const val KEY_OPEN_IN_APP = "open_links_in_app"

    /** Without a stored choice, links open in the in-app browser, the behavior this client ships. */
    const val DEFAULT_IN_APP = true

    /**
     * Reads the stored choice, hands it to the browser hook, and registers the write-back the
     * hosted chat-settings screen's InappBrowser row flips: upstream's row asks
     * [NativeDoHooks.toggleWebBrowserInAppEnabled], which reaches this handler and persists here.
     * The app calls this once at startup, before any link can be pressed.
     */
    fun load(context: Context) {
        NativeDoHooks.setWebBrowserOpenInAppEnabled(isInApp(context))
        val appContext = context.applicationContext
        NativeDoHooks.setWebBrowserToggleHandler { setInApp(appContext, !isInApp(appContext)) }
    }

    fun isInApp(context: Context?): Boolean =
        context?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_OPEN_IN_APP, DEFAULT_IN_APP)
            ?: DEFAULT_IN_APP

    /** Stores the choice and applies it now, so the next pressed link uses it. */
    fun setInApp(context: Context, inApp: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OPEN_IN_APP, inApp).apply()
        NativeDoHooks.setWebBrowserOpenInAppEnabled(inApp)
    }
}
