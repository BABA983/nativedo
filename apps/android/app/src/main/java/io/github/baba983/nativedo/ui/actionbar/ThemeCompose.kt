package io.github.baba983.nativedo.ui.actionbar

import androidx.compose.ui.graphics.Color
import org.telegram.ui.ActionBar.Theme

fun themedColor(key: Int): Color = Color(Theme.getColor(key))
