package io.github.baba983.nativedo.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import io.github.baba983.nativedo.R

sealed class RootDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    data object Home : RootDestination(
        route = "home",
        labelRes = R.string.tab_home,
        icon = Icons.Outlined.Home,
    )

    data object Search : RootDestination(
        route = "search",
        labelRes = R.string.tab_search,
        icon = Icons.Outlined.Search,
    )

    data object Profile : RootDestination(
        route = "profile",
        labelRes = R.string.tab_profile,
        icon = Icons.Outlined.PersonOutline,
    )

    companion object {
        const val rootContainerRoute: String = "root"
        val start: RootDestination = Home
        val bottomTabs: List<RootDestination> = listOf(Home, Profile)
    }
}
