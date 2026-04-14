package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NativeDoGlassBar(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    sectionSpacing: Dp = 0.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(sectionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        title()
        trailing?.invoke(this)
    }
}
