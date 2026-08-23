package com.releasewatch.app.data.backup

import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.db.HiddenRepoDao
import com.releasewatch.app.data.db.HiddenRepoEntity
import com.releasewatch.app.data.db.ReleaseStateDao
import com.releasewatch.app.data.db.ReleaseStateEntity
import com.releasewatch.app.data.db.RepoPackageDao
import com.releasewatch.app.data.db.RepoPackageEntity
import com.squareup.moshi.Moshi

class BackupManager(
    private val tokenStore: TokenStore,
    private val hiddenRepoDao: HiddenRepoDao,
    private val releaseStateDao: ReleaseStateDao,
    private val repoPackageDao: RepoPackageDao
) {
    private val adapter = Moshi.Builder().build().adapter(BackupPayload::class.java).indent("  ")

    suspend fun createBackup(): String {
        val payload = BackupPayload(
            token = tokenStore.getToken(),
            username = tokenStore.getUsername(),
            scopeWarning = tokenStore.getScopeWarning(),
            hiddenRepos = hiddenRepoDao.getAllFullNames(),
            releaseStates = releaseStateDao.getAll().map {
                ReleaseStateBackup(it.repoFullName, it.lastSeenReleaseId, it.lastSeenTag)
            },
            repoPackages = repoPackageDao.getAll().map {
                RepoPackageBackup(it.repoFullName, it.packageName)
            }
        )
        return adapter.toJson(payload)
    }

    suspend fun restoreBackup(text: String) {
        val payload = requireNotNull(adapter.fromJson(text)) { "백업 파일 형식이 올바르지 않습니다." }

        payload.token?.let { tokenStore.saveToken(it) }
        payload.username?.let { tokenStore.saveUsername(it) }
        tokenStore.saveScopeWarning(payload.scopeWarning)

        hiddenRepoDao.clearAll()
        payload.hiddenRepos.forEach { hiddenRepoDao.hide(HiddenRepoEntity(it)) }

        releaseStateDao.clearAll()
        payload.releaseStates.forEach {
            releaseStateDao.upsert(ReleaseStateEntity(it.repoFullName, it.lastSeenReleaseId, it.lastSeenTag))
        }

        repoPackageDao.clearAll()
        payload.repoPackages.forEach {
            repoPackageDao.upsert(RepoPackageEntity(it.repoFullName, it.packageName))
        }
    }
}
