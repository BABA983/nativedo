package io.github.baba983.nativedo.core.theme

import android.content.Context

object NdUiConfig {
    private const val preferencesName = "nd_ui_config"
    private const val keyAnimationsEnabled = "animations_enabled"
    private const val keyDrawActionBarShadow = "draw_action_bar_shadow"
    private const val keySectionShadows = "section_shadows"
    private const val keyHapticsEnabled = "haptics_enabled"

    @Volatile
    private var preferences: android.content.SharedPreferences? = null

    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    var animationsEnabled: Boolean
        get() = preferences?.getBoolean(keyAnimationsEnabled, true) ?: true
        set(value) {
            preferences?.edit()?.putBoolean(keyAnimationsEnabled, value)?.apply()
        }

    var drawActionBarShadow: Boolean
        get() = preferences?.getBoolean(keyDrawActionBarShadow, true) ?: true
        set(value) {
            preferences?.edit()?.putBoolean(keyDrawActionBarShadow, value)?.apply()
        }

    var sectionShadows: Boolean
        get() = preferences?.getBoolean(keySectionShadows, true) ?: true
        set(value) {
            preferences?.edit()?.putBoolean(keySectionShadows, value)?.apply()
        }

    var hapticsEnabled: Boolean
        get() = preferences?.getBoolean(keyHapticsEnabled, true) ?: true
        set(value) {
            preferences?.edit()?.putBoolean(keyHapticsEnabled, value)?.apply()
        }
}
