package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun BottomUtilityShelf(
    onReplyClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val shelfShape = RoundedCornerShape(24.dp)
    val actionShape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shelfShape,
        color = colors.glassBackgroundStrong,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(actionShape)
                    .background(colors.accentMuted.copy(alpha = 0.82f))
                    .clickable(onClick = onReplyClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.topic_reader_action_reply),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }

            Row(
                modifier = Modifier
                    .clip(actionShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                    .clickable(onClick = onMoreClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.topic_reader_action_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
