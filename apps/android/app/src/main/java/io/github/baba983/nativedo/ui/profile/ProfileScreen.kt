package io.github.baba983.nativedo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.topic.rememberTopicThemeColors

@Composable
fun ProfileScreen() {
    val colors = rememberTopicThemeColors()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS.dp +
        8.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.rowBackground)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.title,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "账号、通知、阅读偏好和后续登录入口会放在这里。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.body,
                    )
                }
            }
        }
    }
}
