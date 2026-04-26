package io.github.baba983.nativedo.ui.actionbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Composable
fun rememberThemeRevision(): Int {
    var revision by remember { mutableStateOf(Theme.getThemeGeneration()) }
    DisposableEffect(Unit) {
        val delegate = Theme.ThemeDelegate {
            revision = Theme.getThemeGeneration()
        }
        Theme.addThemeDelegate(delegate)
        onDispose {
            Theme.removeThemeDelegate(delegate)
        }
    }
    return revision
}

fun themedColor(key: Int): Color = Color(Theme.getColor(key))
