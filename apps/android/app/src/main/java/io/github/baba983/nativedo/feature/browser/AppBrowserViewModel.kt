package io.github.baba983.nativedo.feature.browser

import androidx.lifecycle.ViewModel
import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class AppBrowserViewModel(
    initialUrl: String,
    private val baseUrl: String = "https://linux.do",
    initialSearchEngine: AppBrowserSearchEngine = AppBrowserSearchEngine.LinuxDo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialAppBrowserUiState(initialUrl))
    val uiState: StateFlow<AppBrowserUiState> = _uiState.asStateFlow()
    private var searchEngine: AppBrowserSearchEngine = initialSearchEngine
    var lastSubmittedAddress: String? = null
        private set

    fun startAddressEditing(
        currentUrl: String,
        recentEntries: List<AppBrowserHistoryRecord>,
        recentSearches: List<AppBrowserRecentSearchRecord> = emptyList(),
        suggestionSections: List<AppBrowserAddressSuggestionSection> = emptyList(),
    ) {
        _uiState.update {
            it.copy(
                address = AppBrowserAddressUiState(
                    isEditing = true,
                    draft = "",
                    recentEntries = recentEntries,
                    recentSearches = recentSearches,
                    suggestionSections = suggestionSections,
                ),
                search = AppBrowserSearchUiState(),
                sheet = AppBrowserSheet.None,
                isHistoryPopupVisible = false,
                isOverflowMenuVisible = false,
            )
        }
    }

    fun onAction(action: AppBrowserAction) {
        when (action) {
            AppBrowserAction.NavigateBack,
            AppBrowserAction.MinimizeBrowser,
            AppBrowserAction.CloseBrowser -> Unit

            AppBrowserAction.StartAddressEditing,
            AppBrowserAction.OpenAddressSheet -> Unit
            AppBrowserAction.OpenHistory -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        sheet = AppBrowserSheet.History,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.OpenBookmarks -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        sheet = AppBrowserSheet.Bookmarks,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.OpenSettings -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        sheet = AppBrowserSheet.Settings,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.CancelAddressEditing,
            AppBrowserAction.DismissSheet -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.CloseSheet -> {
                _uiState.update {
                    it.copy(
                        sheet = AppBrowserSheet.None,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.StartSearch -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        search = it.search.copy(isActive = true),
                        sheet = AppBrowserSheet.None,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            AppBrowserAction.CloseSearch -> {
                _uiState.update {
                    it.copy(
                        search = AppBrowserSearchUiState(),
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            is AppBrowserAction.UpdateAddressDraft -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(draft = action.value),
                    )
                }
            }

            is AppBrowserAction.SubmitAddress -> {
                val submittedAddress = resolveAddressInput(action.value)
                lastSubmittedAddress = submittedAddress
                _uiState.update {
                    it.copy(
                        address = it.address.copy(
                            isEditing = false,
                            draft = action.value,
                        ),
                    )
                }
            }

            is AppBrowserAction.UpdateSearchQuery -> {
                _uiState.update {
                    it.copy(
                        search = it.search.copy(
                            query = action.query,
                            activeMatchOrdinal = 0,
                            numberOfMatches = 0,
                            isCounting = action.query.isNotBlank(),
                        ),
                    )
                }
            }

            is AppBrowserAction.UpdateSearchMatches -> {
                _uiState.update {
                    it.copy(
                        search = it.search.copy(
                            activeMatchOrdinal = action.activeMatchOrdinal,
                            numberOfMatches = action.numberOfMatches,
                            isCounting = false,
                        ),
                    )
                }
            }

            is AppBrowserAction.SetHistoryPopupVisible -> {
                _uiState.update {
                    val canOpenHistoryPopup = !it.search.isActive &&
                        !it.address.isEditing &&
                        it.sheet == AppBrowserSheet.None
                    val historyPopupVisible = action.visible && canOpenHistoryPopup
                    it.copy(
                        isHistoryPopupVisible = historyPopupVisible,
                        isOverflowMenuVisible = if (historyPopupVisible) false else it.isOverflowMenuVisible,
                    )
                }
            }

            is AppBrowserAction.SetOverflowMenuVisible -> {
                _uiState.update {
                    val canOpenOverflowMenu = !it.search.isActive &&
                        !it.address.isEditing &&
                        it.sheet == AppBrowserSheet.None
                    val overflowMenuVisible = action.visible && canOpenOverflowMenu
                    it.copy(
                        isHistoryPopupVisible = if (overflowMenuVisible) false else it.isHistoryPopupVisible,
                        isOverflowMenuVisible = overflowMenuVisible,
                    )
                }
            }

            is AppBrowserAction.OpenHistoryEntry,
            is AppBrowserAction.ApplyAddressSuggestion,
            AppBrowserAction.GoForward,
            AppBrowserAction.Refresh,
            AppBrowserAction.Share,
            AppBrowserAction.OpenInstantView,
            AppBrowserAction.OpenInBrowser,
            AppBrowserAction.CopyLink,
            AppBrowserAction.ToggleBookmark -> Unit
        }
    }

    fun updateSearchEngine(searchEngine: AppBrowserSearchEngine) {
        this.searchEngine = searchEngine
    }

    fun updateAddressSuggestions(
        recentSearches: List<AppBrowserRecentSearchRecord>,
        suggestionSections: List<AppBrowserAddressSuggestionSection>,
    ) {
        _uiState.update { state ->
            state.copy(
                address = state.address.copy(
                    recentSearches = recentSearches,
                    suggestionSections = suggestionSections,
                ),
            )
        }
    }

    fun setChrome(chrome: AppBrowserChromeModel) {
        _uiState.update { state ->
            state.copy(chrome = chrome)
        }
    }

    fun updateChrome(
        transform: (AppBrowserChromeModel) -> AppBrowserChromeModel,
    ) {
        setChrome(transform(_uiState.value.chrome))
    }

    private fun resolveAddressInput(value: String): String? {
        return resolveAppBrowserAddressInput(
            value = value,
            baseUrl = baseUrl,
            searchEngine = searchEngine,
        )
    }
}
