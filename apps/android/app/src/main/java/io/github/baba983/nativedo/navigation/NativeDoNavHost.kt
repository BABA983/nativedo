package io.github.baba983.nativedo.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.feature.auth.ui.LoginWebViewRoute
import io.github.baba983.nativedo.feature.search.ui.SearchRoute
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.baba983.nativedo.core.navigation.TelegramStyleScaffold

@Composable
internal fun NativeDoNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    onOpenLogin: () -> Unit,
    rootContainer: @Composable BoxScope.() -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.events.collect { event ->
            if (event is SessionEvent.SessionExpired) {
                onOpenLogin()
            }
        }
    }

    // Telegram-style transitions: 48dp slide + fade, 150ms with decelerate easing
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 48.dp.roundToPx() }
    val transitionDurationMs = 150
    val transitionEasing = NativeDoEasing.Standard

    NavHost(
        navController = navController,
        startDestination = RootDestination.rootContainerRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            // New page slides in from right (+48dp) with fade in
            slideInHorizontally(
                initialOffsetX = { slideOffsetPx },
                animationSpec = tween(transitionDurationMs, easing = transitionEasing),
            ) + fadeIn(
                animationSpec = tween(transitionDurationMs, easing = transitionEasing)
            )
        },
        exitTransition = {
            // Current page stays in place but fades out slightly
            fadeOut(
                animationSpec = tween(transitionDurationMs, easing = transitionEasing),
                targetAlpha = 0.7f, // Keep slightly visible like Telegram
            )
        },
        popEnterTransition = {
            // Previous page fades back in (was at alpha 0.7)
            fadeIn(
                initialAlpha = 0.7f,
                animationSpec = tween(transitionDurationMs, easing = transitionEasing)
            )
        },
        popExitTransition = {
            // Current page slides out to right (+48dp) with fade out
            slideOutHorizontally(
                targetOffsetX = { slideOffsetPx },
                animationSpec = tween(transitionDurationMs, easing = transitionEasing),
            ) + fadeOut(
                animationSpec = tween(transitionDurationMs, easing = transitionEasing)
            )
        },
    ) {
        composable(
            RootDestination.rootContainerRoute,
            // Root container uses the default enter/exit transitions defined
            // on NavHost so the Home tab actually fades out (to alpha 0.7) when
            // a Topic/User page slides in from the right. Without this the
            // background page stays static and the forward-nav animation is
            // barely visible.
        ) {
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                rootContainer()
            }
        }
        composable(RootDestination.Search.route) {
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                SearchRoute(
                    onTopicClick = { topicId ->
                        onOpenTopic(topicId)
                    },
                    onUserClick = { username ->
                        onOpenUser(username)
                    },
                    onCloseRequest = { navController.popBackStack() },
                    autoFocus = true,
                )
            }
        }
        composable("login") {
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                TelegramStyleScaffold(
                    canNavigateBack = navController.previousBackStackEntry != null,
                    onNavigateBack = { navController.popBackStack() },
                    enableSwipeBack = true,
                ) {
                    LoginWebViewRoute(
                        onLoginFinished = {
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeDoNavScenePadding(
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        content = content,
    )
}
