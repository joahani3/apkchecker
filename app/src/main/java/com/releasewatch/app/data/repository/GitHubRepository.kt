package com.releasewatch.app.data.repository

import android.content.Context
import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.db.HiddenRepoDao
import com.releasewatch.app.data.db.HiddenRepoEntity
import com.releasewatch.app.data.db.ReleaseStateDao
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

    suspend fun hideRepo(fullName: String) {
        hiddenRepoDao.hide(HiddenRepoEntity(fullName))
    }

    suspend fun getHiddenRepos(): List<String> = hiddenRepoDao.getAllFullNames()

    suspend fun unhideRepo(fullName: String) {
        hiddenRepoDao.unhide(fullName)
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
                .filter { (repo, _) -> repo.fullName !in hidden && !isDistributionMirror(repo) }
                .map { (repo, sources) ->
                    async { buildRepoRelease(repo, sources) }
                }.awaitAll()
                .filterNotNull()
                .sortedWith(
                    compareBy<RepoRelease> { installPriority(it.installStatus) }
                        .thenByDescending { it.release?.publishedAt ?: "" }
                        .thenBy { it.repo.fullName.lowercase() }
                )
        }
    }

    // "<name>-app" repos are public release mirrors of private source repos, which are already
    // listed on their own; showing both would put the same app on the list twice.
    private fun isDistributionMirror(repo: GithubRepo): Boolean =
        repo.name.endsWith("-app", ignoreCase = true)

    private fun installPriority(status: InstallStatus): Int = when (status) {
        InstallStatus.NOT_INSTALLED -> 0
        InstallStatus.UPDATE_AVAILABLE -> 1
        InstallStatus.UP_TO_DATE, InstallStatus.UNKNOWN -> 2
    }

    // Private repos are always listed; public ones only when their latest release ships an APK,
    // so the app's own (public) repo shows up without pulling in docs-only public repos.
    private suspend fun buildRepoRelease(repo: GithubRepo, sources: Set<RepoSource>): RepoRelease? {
        val release = fetchLatestReleaseOrNull(repo.owner.login, repo.name)
        if (!repo.private && release?.apkAsset == null) return null
        val (installStatus, installedVersionName) = resolveInstallStatus(repo, release)

        return RepoRelease(repo, release, sources, installStatus, installedVersionName)
    }

    private suspend fun resolveInstallStatus(
        repo: GithubRepo,
        release: GithubRelease?
    ): Pair<InstallStatus, String?> {
        val apkAsset = release?.apkAsset ?: return InstallStatus.UNKNOWN to null
        val packageName = resolvePackageName(repo.fullName, apkAsset) ?: return InstallStatus.UNKNOWN to null

        val installed = InstalledAppChecker.getInstalledPackageInfo(context, packageName)
            ?: return InstallStatus.NOT_INSTALLED to null

        // Prefer the release's display name over its tag: tags are often build counters
        // (e.g. "apk-8") unrelated to the app's own version number, while maintainers usually
        // put the actual version in the release title (e.g. "apk v3.2").
        val releaseVersionLabel = release.name?.takeIf { it.isNotBlank() } ?: release.tagName

        val status = when (VersionTextComparator.isOlder(installed.versionName, releaseVersionLabel)) {
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

    // Synced by ~/.claude/scripts/shelf.py on every commit across the user's repos; kept in a
    // private repo (rather than public GitHub Pages) since the page lists private repo names.
    suspend fun fetchBookshelfHtml(): Result<String> = runCatching {
        val owner = tokenStore.getUsername() ?: error("로그인이 필요합니다")
        val response = api.getRawFileContent(owner, BOOKSHELF_REPO, BOOKSHELF_PATH)
        if (!response.isSuccessful) {
            error("책장을 불러오지 못했습니다 (HTTP ${response.code()})")
        }
        response.body()?.string() ?: error("책장 내용이 비어 있습니다")
    }

    private companion object {
        const val BOOKSHELF_REPO = "coding-bookshelf"
        const val BOOKSHELF_PATH = "index.html"
    }
}
