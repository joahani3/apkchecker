package com.releasewatch.app.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.releasewatch.app.data.repository.GitHubRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: GitHubRepository) : ViewModel() {

    var tokenInput by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onTokenChange(value: String) {
        tokenInput = value
        errorMessage = null
    }

    fun login(onSuccess: () -> Unit) {
        if (tokenInput.isBlank()) {
            errorMessage = "토큰을 입력해주세요"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            repository.login(tokenInput)
                .onSuccess {
                    isLoading = false
                    onSuccess()
                }
                .onFailure {
                    isLoading = false
                    errorMessage = "로그인에 실패했습니다. 토큰을 확인해주세요."
                }
        }
    }
}
