package io.github.baba983.nativedo.core.theme

enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    fun next(): ThemeMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }

    fun resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        System -> systemInDarkTheme
        Light -> false
        Dark -> true
    }
}
