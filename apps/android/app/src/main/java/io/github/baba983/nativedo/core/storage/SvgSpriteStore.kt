package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SvgSpriteStore(
    private val persistence: SvgSpriteStorePersistence,
) {
    constructor() : this(persistence = NoOpSvgSpriteStorePersistence())

    private val _sprite = MutableStateFlow(persistence.load().orEmpty())
    val sprite: StateFlow<String> = _sprite.asStateFlow()

    fun save(markup: String) {
        val normalizedMarkup = markup.trim()
        if (normalizedMarkup.isBlank() || normalizedMarkup == _sprite.value) {
            return
        }
        _sprite.value = normalizedMarkup
        persistence.save(normalizedMarkup)
    }
}

interface SvgSpriteStorePersistence {
    fun load(): String?

    fun save(markup: String)
}

class SharedPreferencesSvgSpriteStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : SvgSpriteStorePersistence {
    override fun load(): String? = sharedPreferences.getString(KEY_SPRITE, null)

    override fun save(markup: String) {
        sharedPreferences.edit()
            .putString(KEY_SPRITE, markup)
            .apply()
    }

    private companion object {
        const val KEY_SPRITE: String = "svg_sprite_markup"
    }
}

private class NoOpSvgSpriteStorePersistence : SvgSpriteStorePersistence {
    override fun load(): String? = null

    override fun save(markup: String) = Unit
}
