package io.github.baba983.nativedo.feature.search.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.SearchUser
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.RecentSearchStore
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.feature.home.ui.TopicListItem
import io.github.baba983.nativedo.feature.search.data.SearchRepository
import io.github.baba983.nativedo.feature.search.domain.SearchTopicsUseCase
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun SearchRoute(
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCloseRequest: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val recentSearchStore = remember { RecentSearchStore() }
    val viewModel = rememberSearchViewModel(
        appContainer = appContainer,
        recentSearchStore = recentSearchStore,
        key = "search-route",
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        cookieStore = appContainer.cookieStore,
        baseUrl = appContainer.baseUrl,
        onQueryChanged = viewModel::onQueryChanged,
        onQuerySubmitted = viewModel::onQuerySubmitted,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        modifier = modifier,
        onCloseRequest = onCloseRequest,
        autoFocus = autoFocus,
    )
}

@Composable
internal fun rememberSearchViewModel(
    appContainer: AppContainer,
    recentSearchStore: RecentSearchStore,
    key: String,
): SearchViewModel {
    return viewModel(
        key = key,
        factory = remember(appContainer, recentSearchStore) {
            SearchViewModelFactory(
                searchTopics = SearchTopicsUseCase(
                    repository = SearchRepository(appContainer.discourseApiClient),
                ),
                recentSearchStore = recentSearchStore,
            )
        },
    )
}

@Composable
internal fun SearchScreen(
    uiState: SearchUiState,
    cookieStore: CookieStore,
    baseUrl: String,
    onQueryChanged: (String) -> Unit,
    onQuerySubmitted: (String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCloseRequest: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val colors = NativeDoThemeExtras.colors
    val errorPresentation = uiState.errorMessage?.let {
        requestErrorPresentation(
            rawMessage = it,
            genericTitle = "Couldn't search Linux.do",
            genericDescription = "Please try again in a moment.",
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val stickyBackgroundColor = lerp(
        start = colors.backgroundBase,
        stop = colors.backgroundElevated,
        fraction = 0.34f,
    ).copy(alpha = 0.97f)
    val activeFieldColor = lerp(
        start = colors.backgroundElevated,
        stop = colors.glassBackgroundStrong,
        fraction = 0.28f,
    ).copy(alpha = 0.98f)

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(stickyBackgroundColor),
        ) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                shape = NativeDoChromeShape,
                color = activeFieldColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.glassStrokeStrong.copy(alpha = 0.72f),
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (uiState.query.isBlank()) {
                            Text(
                                text = "Search linux.do",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = onQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onQuerySubmitted(uiState.query) },
                            ),
                        )
                    }
                    if (onCloseRequest != null) {
                        IconButton(
                            onClick = onCloseRequest,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.textSecondary,
                            )
                        }
                    } else {
                        Text(
                            text = "Go",
                            modifier = Modifier.clickable { onQuerySubmitted(uiState.query) },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.query.isBlank()) {
                                colors.textSecondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }

        if (uiState.isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        uiState.errorMessage?.let {
            RequestErrorCard(
                presentation = requireNotNull(errorPresentation),
                onRetry = {
                    if (uiState.query.isNotBlank()) {
                        onQuerySubmitted(uiState.query)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                compact = true,
            )
        }

        if (uiState.recentQueries.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Text(
                    text = uiState.recentQueries.joinToString(separator = " · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            itemsIndexed(
                items = uiState.topics,
                key = { _, topic -> "topic-${topic.id}" },
            ) { index, topic ->
                TopicListItem(
                    topic = topic,
                    baseUrl = baseUrl,
                    onClick = { onTopicClick(topic.id) },
                    modifier = Modifier.fillMaxWidth(),
                    showDivider = index != uiState.topics.lastIndex || uiState.users.isNotEmpty(),
                )
            }
            itemsIndexed(
                items = uiState.users,
                key = { _, user -> "user-${user.username}" },
            ) { index, user ->
                SearchUserListItem(
                    user = user,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onClick = { onUserClick(user.username) },
                    modifier = Modifier.fillMaxWidth(),
                    showDivider = index != uiState.users.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun SearchUserListItem(
    user: SearchUser,
    cookieStore: CookieStore,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = NativeDoThemeExtras.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                label = user.displayName.ifBlank { user.username },
                avatarTemplate = user.avatarTemplate,
                baseUrl = baseUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (user.displayName.isNotBlank()) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
            )
        }
    }
}

private class SearchViewModelFactory(
    private val searchTopics: SearchTopicsUseCase,
    private val recentSearchStore: RecentSearchStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(
            searchTopics = searchTopics,
            recentSearchStore = recentSearchStore,
        ) as T
    }
}
