package com.releasewatch.app.ui.repos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.releasewatch.app.data.backup.BackupManager
import com.releasewatch.app.data.repository.GitHubRepository
import com.releasewatch.app.data.repository.RepoRelease
import kotlinx.coroutines.launch

data class RepoListUiState(
    val isLoading: Boolean = false,
    val repos: List<RepoRelease> = emptyList(),
    val username: String? = null,
    val errorMessage: String? = null,
    val scopeWarning: String? = null,
    val hiddenRepos: List<String> = emptyList()
)

class RepoListViewModel(
    private val repository: GitHubRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    var uiState by mutableStateOf(
        RepoListUiState(
            username = repository.cachedUsername(),
            scopeWarning = repository.scopeWarning()
        )
    )
        private set

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            repository.refreshRepos()
                .onSuccess { list ->
                    uiState = uiState.copy(
                        isLoading = false,
                        repos = list,
                        username = repository.cachedUsername()
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "새로고침에 실패했습니다. 네트워크 연결을 확인해주세요."
                    )
                }
        }
    }

    fun removeRepo(repoRelease: RepoRelease) {
        viewModelScope.launch {
            repository.hideRepo(repoRelease.repo.fullName)
            uiState = uiState.copy(
                repos = uiState.repos.filterNot { it.repo.fullName == repoRelease.repo.fullName }
            )
        }
    }

    fun loadHiddenRepos() {
        viewModelScope.launch {
            uiState = uiState.copy(hiddenRepos = repository.getHiddenRepos())
        }
    }

    fun unhideRepo(fullName: String) {
        viewModelScope.launch {
            repository.unhideRepo(fullName)
            uiState = uiState.copy(hiddenRepos = uiState.hiddenRepos - fullName)
            refresh()
        }
    }

    fun dismissScopeWarning() {
        uiState = uiState.copy(scopeWarning = null)
    }

    suspend fun createBackupText(): String = backupManager.createBackup()

    suspend fun restoreBackup(text: String): Result<Unit> =
        runCatching { backupManager.restoreBackup(text) }.onSuccess { refresh() }

    fun authToken(): String? = repository.authToken()

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }
}
