package io.github.baba983.nativedo.feature.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class AppBrowserViewModel(
    initialUrl: String,
    private val baseUrl: String = "https://linux.do",
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialAppBrowserUiState(initialUrl))
    val uiState: StateFlow<AppBrowserUiState> = _uiState.asStateFlow()
    var lastSubmittedAddress: String? = null
        private set

    fun startAddressEditing(
        currentUrl: String,
        recentEntries: List<AppBrowserHistoryRecord>,
    ) {
        _uiState.update {
            it.copy(
                address = AppBrowserAddressUiState(
                    isEditing = true,
                    draft = "",
                    recentEntries = recentEntries,
                ),
                search = AppBrowserSearchUiState(),
                isHistoryPopupVisible = false,
            )
        }
    }

    fun onAction(action: AppBrowserAction) {
        when (action) {
            AppBrowserAction.NavigateBack,
            AppBrowserAction.CloseBrowser -> Unit

            AppBrowserAction.StartAddressEditing,
            AppBrowserAction.OpenAddressSheet -> Unit
            AppBrowserAction.OpenHistory -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        sheet = AppBrowserSheet.History,
                        isHistoryPopupVisible = false,
                    )
                }
            }

            AppBrowserAction.OpenSettings -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        sheet = AppBrowserSheet.Settings,
                        isHistoryPopupVisible = false,
                    )
                }
            }

            AppBrowserAction.CancelAddressEditing,
            AppBrowserAction.DismissSheet -> {
                _uiState.update {
                    it.copy(address = it.address.copy(isEditing = false))
                }
            }

            AppBrowserAction.CloseSheet -> {
                _uiState.update { it.copy(sheet = AppBrowserSheet.None) }
            }

            AppBrowserAction.StartSearch -> {
                _uiState.update {
                    it.copy(
                        address = it.address.copy(isEditing = false),
                        search = it.search.copy(isActive = true),
                        isHistoryPopupVisible = false,
                    )
                }
            }

            AppBrowserAction.CloseSearch -> {
                _uiState.update {
                    it.copy(
                        search = AppBrowserSearchUiState(),
                        isHistoryPopupVisible = false,
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
                        search = it.search.copy(query = action.query),
                    )
                }
            }

            is AppBrowserAction.UpdateSearchMatches -> {
                _uiState.update {
                    it.copy(
                        search = it.search.copy(
                            activeMatchOrdinal = action.activeMatchOrdinal,
                            numberOfMatches = action.numberOfMatches,
                        ),
                    )
                }
            }

            is AppBrowserAction.SetHistoryPopupVisible -> {
                _uiState.update {
                    it.copy(isHistoryPopupVisible = action.visible)
                }
            }

            is AppBrowserAction.OpenHistoryEntry,
            AppBrowserAction.GoForward,
            AppBrowserAction.Refresh,
            AppBrowserAction.Share,
            AppBrowserAction.OpenInBrowser,
            AppBrowserAction.CopyLink -> Unit
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
        val trimmedValue = value.trim()
        if (trimmedValue.isBlank()) {
            return null
        }

        trimmedValue.toHttpUrlOrNull()?.let { return it.toString() }

        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
        if (trimmedValue.startsWith("/")) {
            return baseHttpUrl?.resolve(trimmedValue)?.toString() ?: trimmedValue
        }

        if (!trimmedValue.contains(' ')) {
            val guessedUrl = "https://$trimmedValue".toHttpUrlOrNull()
            if (guessedUrl != null) {
                return guessedUrl.toString()
            }
        }

        return baseHttpUrl
            ?.newBuilder()
            ?.encodedPath("/search")
            ?.query(null)
            ?.addQueryParameter("q", trimmedValue)
            ?.build()
            ?.toString()
            ?: trimmedValue
    }
}
