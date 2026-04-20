package io.github.baba983.nativedo.feature.browser

import androidx.lifecycle.ViewModel
import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class BrowserViewModel(
    initialUrl: String,
    private val baseUrl: String = "https://linux.do",
    initialSearchEngine: AppBrowserSearchEngine = AppBrowserSearchEngine.LinuxDo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialBrowserUiState(initialUrl))
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
    private var searchEngine: AppBrowserSearchEngine = initialSearchEngine
    var lastSubmittedAddress: String? = null
        private set

    fun startAddressEditing(
        currentUrl: String,
        recentEntries: List<BrowserHistoryRecord>,
        recentSearches: List<AppBrowserRecentSearchRecord> = emptyList(),
        suggestionSections: List<BrowserAddressSuggestionSection> = emptyList(),
    ) {
        _uiState.update {
            it.copy(
                address = BrowserAddressUiState(
                    isEditing = true,
                    draft = "",
                    recentEntries = recentEntries,
                    recentSearches = recentSearches,
                    suggestionSections = suggestionSections,
                ),
                search = BrowserSearchUiState(),
                panel = BrowserPanel.None,
                isHistoryPopupVisible = false,
                isOverflowMenuVisible = false,
            )
        }
    }

    fun onAction(action: BrowserAction) {
        when (action) {
            BrowserAction.NavigateBack,
            BrowserAction.MinimizeBrowser,
            BrowserAction.CloseBrowser -> Unit

            BrowserAction.StartAddressEditing,
            BrowserAction.OpenAddressSheet -> Unit
            BrowserAction.OpenHistory -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        panel = BrowserPanel.History,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.OpenBookmarks -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        panel = BrowserPanel.Bookmarks,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.OpenSettings -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        panel = BrowserPanel.Settings,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.CancelAddressEditing,
            BrowserAction.DismissPanel -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.ClosePanel -> {
                _uiState.update {
                    it.copy(
                        panel = BrowserPanel.None,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.StartSearch -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        search = it.search.copy(isActive = true),
                        panel = BrowserPanel.None,
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            BrowserAction.CloseSearch -> {
                _uiState.update {
                    it.copy(
                        search = BrowserSearchUiState(),
                        isHistoryPopupVisible = false,
                        isOverflowMenuVisible = false,
                    )
                }
            }

            is BrowserAction.UpdateAddressDraft -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(draft = action.value),
                    )
                }
            }

            is BrowserAction.SubmitAddress -> {
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

            is BrowserAction.UpdateSearchQuery -> {
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

            is BrowserAction.UpdateSearchMatches -> {
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

            is BrowserAction.SetHistoryPopupVisible -> {
                _uiState.update {
                    val canOpenHistoryPopup = !it.search.isActive &&
                        !it.address.isEditing &&
                        it.panel == BrowserPanel.None
                    val historyPopupVisible = action.visible && canOpenHistoryPopup
                    it.copy(
                        isHistoryPopupVisible = historyPopupVisible,
                        isOverflowMenuVisible = if (historyPopupVisible) false else it.isOverflowMenuVisible,
                    )
                }
            }

            is BrowserAction.SetOverflowMenuVisible -> {
                _uiState.update {
                    val canOpenOverflowMenu = !it.search.isActive &&
                        !it.address.isEditing &&
                        it.panel == BrowserPanel.None
                    val overflowMenuVisible = action.visible && canOpenOverflowMenu
                    it.copy(
                        isHistoryPopupVisible = if (overflowMenuVisible) false else it.isHistoryPopupVisible,
                        isOverflowMenuVisible = overflowMenuVisible,
                    )
                }
            }

            is BrowserAction.OpenHistoryEntry,
            is BrowserAction.ApplyAddressSuggestion,
            BrowserAction.GoForward,
            BrowserAction.Refresh,
            BrowserAction.Share,
            BrowserAction.OpenInstantView,
            BrowserAction.OpenInBrowser,
            BrowserAction.CopyLink,
            BrowserAction.ToggleBookmark -> Unit
        }
    }

    fun updateSearchEngine(searchEngine: AppBrowserSearchEngine) {
        this.searchEngine = searchEngine
    }

    fun updateAddressSuggestions(
        recentSearches: List<AppBrowserRecentSearchRecord>,
        suggestionSections: List<BrowserAddressSuggestionSection>,
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

    fun setChrome(chrome: BrowserChromeModel) {
        _uiState.update { state ->
            state.copy(chrome = chrome)
        }
    }

    fun updateChrome(
        transform: (BrowserChromeModel) -> BrowserChromeModel,
    ) {
        setChrome(transform(_uiState.value.chrome))
    }

    private fun resolveAddressInput(value: String): String? {
        return resolveBrowserAddressInput(
            value = value,
            baseUrl = baseUrl,
            searchEngine = searchEngine,
        )
    }
}
