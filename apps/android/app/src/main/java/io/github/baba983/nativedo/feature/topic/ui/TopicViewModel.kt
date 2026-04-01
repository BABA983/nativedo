package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TopicViewModel(
    private val loadTopic: LoadTopicUseCase,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
            )
            loadTopic(topicId).fold(
                onSuccess = { detail ->
                    _uiState.value = TopicUiState(
                        title = detail.title,
                        posts = detail.posts,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = TopicUiState(
                        isLoading = false,
                        errorMessage = error.message,
                    )
                },
            )
        }
    }
}
