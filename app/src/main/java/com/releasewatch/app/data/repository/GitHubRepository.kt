package com.releasewatch.app.data.repository

import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.db.ReleaseStateDao
import com.releasewatch.app.data.db.ReleaseStateEntity
import com.releasewatch.app.data.network.GitHubApi
import com.releasewatch.app.data.network.model.GithubRelease
import com.releasewatch.app.data.network.model.GithubRepo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GitHubRepository(
    private val api: GitHubApi,
    private val tokenStore: TokenStore,
    private val dao: ReleaseStateDao
) {

    fun isLoggedIn(): Boolean = tokenStore.getToken() != null

    fun cachedUsername(): String? = tokenStore.getUsername()

    fun authToken(): String? = tokenStore.getToken()

    suspend fun login(token: String): Result<String> = runCatching {
        tokenStore.saveToken(token.trim())
        try {
            val user = api.getAuthenticatedUser()
            tokenStore.saveUsername(user.login)
            user.login
        } catch (e: Exception) {
            tokenStore.clear()
            throw e
        }
    }

    suspend fun logout() {
        tokenStore.clear()
        dao.clearAll()
    }

    suspend fun markSeen(repoRelease: RepoRelease) {
        val release = repoRelease.release ?: return
        dao.upsert(ReleaseStateEntity(repoRelease.repo.fullName, release.id, release.tagName))
    }

    suspend fun refreshRepos(): Result<List<RepoRelease>> = runCatching {
        coroutineScope {
            val ownDeferred = async { safeFetch { api.getOwnRepos() } }
            val starredDeferred = async { safeFetch { api.getStarredRepos() } }
            val watchedDeferred = async { safeFetch { api.getWatchedRepos() } }

            val merged = linkedMapOf<String, Pair<GithubRepo, MutableSet<RepoSource>>>()
            fun merge(list: List<GithubRepo>, source: RepoSource) {
                for (repo in list) {
                    val entry = merged.getOrPut(repo.fullName) { repo to mutableSetOf() }
                    entry.second.add(source)
                }
            }
            merge(ownDeferred.await(), RepoSource.OWNED)
            merge(starredDeferred.await(), RepoSource.STARRED)
            merge(watchedDeferred.await(), RepoSource.WATCHED)

            merged.values.map { (repo, sources) ->
                async { buildRepoRelease(repo, sources) }
            }.awaitAll()
                .sortedWith(
                    compareByDescending<RepoRelease> { it.isNew }
                        .thenBy { it.repo.fullName.lowercase() }
                )
        }
    }

    private suspend fun buildRepoRelease(repo: GithubRepo, sources: Set<RepoSource>): RepoRelease {
        val release = fetchLatestReleaseOrNull(repo.owner.login, repo.name)
        val state = dao.getByRepo(repo.fullName)

        val isNew = when {
            release == null -> false
            state == null -> {
                dao.upsert(ReleaseStateEntity(repo.fullName, release.id, release.tagName))
                false
            }
            else -> state.lastSeenReleaseId != release.id
        }

        return RepoRelease(repo, release, sources, isNew)
    }

    private suspend fun fetchLatestReleaseOrNull(owner: String, repoName: String): GithubRelease? {
        return try {
            val response = api.getLatestRelease(owner, repoName)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun safeFetch(block: suspend () -> List<GithubRepo>): List<GithubRepo> {
        return try {
            block()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
