package io.github.baba983.nativedo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import io.github.baba983.nativedo.core.navigation.TelegramStyleScaffold

/**
 * Example: How to wrap your existing routes with Telegram-style navigation
 *
 * This shows how to add predictive back support to any composable route.
 */

/**
 * Wrapper for Topic route with Telegram-style back gesture
 */
@Composable
fun TopicRouteWithTelegramNav(
    topicId: Long,
    navController: NavHostController,
    onHeaderTitleChanged: (String?) -> Unit,
    onSessionRecoveryRequired: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TelegramStyleScaffold(
        canNavigateBack = navController.previousBackStackEntry != null,
        onNavigateBack = { navController.popBackStack() },
        enableSwipeBack = true,
        modifier = modifier,
    ) {
        // Your existing TopicRoute composable
        io.github.baba983.nativedo.feature.topic.ui.TopicRoute(
            topicId = topicId,
            onHeaderTitleChanged = onHeaderTitleChanged,
            onSessionRecoveryRequired = onSessionRecoveryRequired,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
        )
    }
}

/**
 * Wrapper for User Profile route with Telegram-style back gesture
 */
@Composable
fun ProfileRouteWithTelegramNav(
    username: String,
    navController: NavHostController,
    onSessionRecoveryRequired: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TelegramStyleScaffold(
        canNavigateBack = navController.previousBackStackEntry != null,
        onNavigateBack = { navController.popBackStack() },
        enableSwipeBack = true,
        modifier = modifier,
    ) {
        io.github.baba983.nativedo.feature.profile.ui.ProfileRoute(
            viewer = io.github.baba983.nativedo.feature.profile.ui.ProfileViewer.Other(username),
            onSessionRecoveryRequired = onSessionRecoveryRequired,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
        )
    }
}

/**
 * Example: How to integrate into NativeDoNavHost.kt
 *
 * Replace your existing composable calls with the wrapped versions:
 *
 * Before:
 * ```
 * composable("topic/{topicId}") { entry ->
 *     val topicId = entry.arguments?.getString("topicId")!!.toLong()
 *     NativeDoNavScenePadding(contentPadding = contentPadding) {
 *         TopicRoute(
 *             topicId = topicId,
 *             onHeaderTitleChanged = onTopicHeaderTitleChanged,
 *             // ... other params
 *         )
 *     }
 * }
 * ```
 *
 * After:
 * ```
 * composable("topic/{topicId}") { entry ->
 *     val topicId = entry.arguments?.getString("topicId")!!.toLong()
 *     NativeDoNavScenePadding(contentPadding = contentPadding) {
 *         TopicRouteWithTelegramNav(
 *             topicId = topicId,
 *             navController = navController,
 *             onHeaderTitleChanged = onTopicHeaderTitleChanged,
 *             // ... other params
 *         )
 *     }
 * }
 * ```
 */
