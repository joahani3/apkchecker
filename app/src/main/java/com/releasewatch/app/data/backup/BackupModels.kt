package com.releasewatch.app.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val version: Int = 1,
    val token: String?,
    val username: String?,
    val scopeWarning: String?,
    val hiddenRepos: List<String>,
    val releaseStates: List<ReleaseStateBackup>,
    val repoPackages: List<RepoPackageBackup>
)

@JsonClass(generateAdapter = true)
data class ReleaseStateBackup(
    val repoFullName: String,
    val lastSeenReleaseId: Long?,
    val lastSeenTag: String?
)

@JsonClass(generateAdapter = true)
data class RepoPackageBackup(
    val repoFullName: String,
    val packageName: String
)
