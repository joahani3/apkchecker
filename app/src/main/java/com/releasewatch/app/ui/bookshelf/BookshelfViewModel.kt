package com.releasewatch.app.ui.bookshelf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.releasewatch.app.data.repository.GitHubRepository
import kotlinx.coroutines.launch

data class BookshelfUiState(
    val isLoading: Boolean = true,
    val html: String? = null,
    val errorMessage: String? = null
)

class BookshelfViewModel(private val repository: GitHubRepository) : ViewModel() {

    var uiState by mutableStateOf(BookshelfUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            repository.fetchBookshelfHtml()
                .onSuccess { html -> uiState = BookshelfUiState(isLoading = false, html = html) }
                .onFailure {
                    uiState = BookshelfUiState(
                        isLoading = false,
                        errorMessage = "책장을 불러오지 못했습니다. 네트워크 연결을 확인해주세요."
                    )
                }
        }
    }
}
