package io.github.baba983.nativedo.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onHomeHeaderSearchVisibilityChanged: (Boolean) -> Unit,
) {
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
                            onHeaderSearchVisibilityChanged = onHomeHeaderSearchVisibilityChanged,
                        )
                    }

                    RootDestination.Profile -> {
                        ProfileRoute(
                            viewer = ProfileViewer.Self,
                            onLoginClick = {
                                navController.navigate("login")
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
            TopicRoute(topicId = topicId)
        }
        composable("user/{username}") { entry ->
            ProfileRoute(
                viewer = ProfileViewer.Other(
                    entry.arguments!!.getString("username")!!,
                ),
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
