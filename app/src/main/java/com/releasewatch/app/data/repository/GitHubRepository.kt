package com.releasewatch.app.data.repository

import android.content.Context
import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.db.HiddenRepoDao
import com.releasewatch.app.data.db.HiddenRepoEntity
import com.releasewatch.app.data.db.ReleaseStateDao
import com.releasewatch.app.data.db.ReleaseStateEntity
import com.releasewatch.app.data.db.RepoPackageDao
import com.releasewatch.app.data.db.RepoPackageEntity
import com.releasewatch.app.data.install.ApkPackageInspector
import com.releasewatch.app.data.install.InstalledAppChecker
import com.releasewatch.app.data.install.VersionTextComparator
import com.releasewatch.app.data.network.GitHubApi
import com.releasewatch.app.data.network.model.GithubAsset
import com.releasewatch.app.data.network.model.GithubRelease
import com.releasewatch.app.data.network.model.GithubRepo
import com.releasewatch.app.data.network.model.apkAsset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GitHubRepository(
    private val context: Context,
    private val api: GitHubApi,
    private val tokenStore: TokenStore,
    private val dao: ReleaseStateDao,
    private val hiddenRepoDao: HiddenRepoDao,
    private val repoPackageDao: RepoPackageDao
) {

    fun isLoggedIn(): Boolean = tokenStore.getToken() != null

    fun cachedUsername(): String? = tokenStore.getUsername()

    fun authToken(): String? = tokenStore.getToken()

    fun scopeWarning(): String? = tokenStore.getScopeWarning()

    suspend fun login(token: String): Result<String> = runCatching {
        tokenStore.saveToken(token.trim())
        try {
            val response = api.getAuthenticatedUser()
            val user = if (response.isSuccessful) response.body() else null
            requireNotNull(user) { "GitHub 인증에 실패했습니다 (HTTP ${response.code()})" }

            tokenStore.saveUsername(user.login)
            tokenStore.saveScopeWarning(scopeWarningFrom(response.headers()["X-OAuth-Scopes"]))
            user.login
        } catch (e: Exception) {
            tokenStore.clear()
            throw e
        }
    }

    private fun scopeWarningFrom(scopesHeader: String?): String? {
        // Fine-grained tokens don't send this header at all, so only classic tokens are checked here.
        val scopes = scopesHeader?.split(",")?.map { it.trim() } ?: return null
        return if ("repo" !in scopes) {
            "이 토큰에는 'repo' 권한이 없어 private 저장소의 릴리즈가 보이지 않을 수 있습니다. " +
                "설정에서 'repo' 권한을 포함한 토큰을 새로 발급해주세요."
        } else {
            null
        }
    }

    suspend fun logout() {
        tokenStore.clear()
        dao.clearAll()
        hiddenRepoDao.clearAll()
    }

    suspend fun markSeen(repoRelease: RepoRelease) {
        val release = repoRelease.release ?: return
        dao.upsert(ReleaseStateEntity(repoRelease.repo.fullName, release.id, release.tagName))
    }

    suspend fun hideRepo(fullName: String) {
        hiddenRepoDao.hide(HiddenRepoEntity(fullName))
    }

    suspend fun refreshRepos(): Result<List<RepoRelease>> = runCatching {
        coroutineScope {
            val ownDeferred = async { safeFetch { api.getOwnRepos() } }
            val starredDeferred = async { safeFetch { api.getStarredRepos() } }
            val watchedDeferred = async { safeFetch { api.getWatchedRepos() } }
            val hiddenDeferred = async { hiddenRepoDao.getAllFullNames().toSet() }

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

            val hidden = hiddenDeferred.await()
            merged.values
                .filter { (repo, _) -> repo.fullName !in hidden }
                .map { (repo, sources) ->
                    async { buildRepoRelease(repo, sources) }
                }.awaitAll()
                .sortedWith(
                    compareBy<RepoRelease> { installPriority(it.installStatus) }
                        .thenByDescending { it.release?.publishedAt ?: "" }
                        .thenByDescending { it.isNew }
                        .thenBy { it.repo.fullName.lowercase() }
                )
        }
    }

    private fun installPriority(status: InstallStatus): Int = when (status) {
        InstallStatus.NOT_INSTALLED -> 0
        InstallStatus.UPDATE_AVAILABLE -> 1
        InstallStatus.UP_TO_DATE, InstallStatus.UNKNOWN -> 2
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

        val (installStatus, installedVersionName) = resolveInstallStatus(repo, release)

        return RepoRelease(repo, release, sources, isNew, installStatus, installedVersionName)
    }

    private suspend fun resolveInstallStatus(
        repo: GithubRepo,
        release: GithubRelease?
    ): Pair<InstallStatus, String?> {
        val apkAsset = release?.apkAsset ?: return InstallStatus.UNKNOWN to null
        val packageName = resolvePackageName(repo.fullName, apkAsset) ?: return InstallStatus.UNKNOWN to null

        val installed = InstalledAppChecker.getInstalledPackageInfo(context, packageName)
            ?: return InstallStatus.NOT_INSTALLED to null

        val status = when (VersionTextComparator.isOlder(installed.versionName, release.tagName)) {
            true -> InstallStatus.UPDATE_AVAILABLE
            false -> InstallStatus.UP_TO_DATE
            null -> InstallStatus.UNKNOWN
        }
        return status to installed.versionName
    }

    private suspend fun resolvePackageName(repoFullName: String, asset: GithubAsset): String? {
        repoPackageDao.getPackageName(repoFullName)?.let { return it }

        val resolved = ApkPackageInspector.resolvePackageName(context, asset, tokenStore.getToken()) ?: return null
        repoPackageDao.upsert(RepoPackageEntity(repoFullName, resolved))
        return resolved
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
