package io.github.baba983.nativedo.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.feature.auth.ui.LoginWebViewRoute
import io.github.baba983.nativedo.feature.home.ui.HomeRoute
import io.github.baba983.nativedo.feature.profile.ui.ProfileRoute
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewer
import io.github.baba983.nativedo.feature.search.ui.SearchRoute
import io.github.baba983.nativedo.feature.topic.ui.TopicRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NativeDoNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    rootPagerState: PagerState,
    homeSearchRequestToken: Int,
    homeRefreshRequestToken: Int,
    onHomeHeaderSearchVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val openLogin: () -> Unit = {
        if (navController.currentDestination?.route != "login") {
            navController.navigate("login")
        }
    }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.events.collect { event ->
            if (event is SessionEvent.SessionExpired) {
                openLogin()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = RootDestination.rootContainerRoute,
        modifier = Modifier.padding(contentPadding),
    ) {
        composable(RootDestination.rootContainerRoute) {
            HorizontalPager(
                state = rootPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> RootDestination.bottomTabs[page].route },
            ) { page ->
                when (RootDestination.bottomTabs[page]) {
                    RootDestination.Home -> {
                        HomeRoute(
                            onTopicClick = { topicId ->
                                navController.navigate("topic/$topicId")
                            },
                            onUserClick = { username ->
                                navController.navigate("user/$username")
                            },
                            headerSearchRequestToken = homeSearchRequestToken,
                            refreshRequestToken = homeRefreshRequestToken,
                            onHeaderSearchVisibilityChanged = onHomeHeaderSearchVisibilityChanged,
                        )
                    }

                    RootDestination.Profile -> {
                        ProfileRoute(
                            viewer = ProfileViewer.Self,
                            onLoginClick = openLogin,
                            onSessionRecoveryRequired = openLogin,
                            onTopicClick = { topicId ->
                                navController.navigate("topic/$topicId")
                            },
                            onUserClick = { username ->
                                navController.navigate("user/$username")
                            },
                        )
                    }

                    RootDestination.Search -> Unit
                }
            }
        }
        composable(RootDestination.Search.route) {
            SearchRoute(
                onTopicClick = { topicId ->
                    navController.navigate("topic/$topicId")
                },
                onUserClick = { username ->
                    navController.navigate("user/$username")
                },
            )
        }
        composable("topic/{topicId}") { entry ->
            val topicId = entry.arguments?.getString("topicId")!!.toLong()
            TopicRoute(
                topicId = topicId,
                onSessionRecoveryRequired = openLogin,
                onTopicClick = { linkedTopicId ->
                    navController.navigate("topic/$linkedTopicId")
                },
                onUserClick = { username ->
                    navController.navigate("user/$username")
                },
            )
        }
        composable("user/{username}") { entry ->
            ProfileRoute(
                viewer = ProfileViewer.Other(
                    entry.arguments!!.getString("username")!!,
                ),
                onSessionRecoveryRequired = openLogin,
                onTopicClick = { topicId ->
                    navController.navigate("topic/$topicId")
                },
                onUserClick = { username ->
                    navController.navigate("user/$username")
                },
            )
        }
        composable("login") {
            LoginWebViewRoute(
                onLoginFinished = {
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun DestinationPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
