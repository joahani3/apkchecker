package com.releasewatch.app.data.repository

import com.releasewatch.app.data.network.model.GithubRelease
import com.releasewatch.app.data.network.model.GithubRepo
import com.releasewatch.app.data.playconsole.PlayTrackVersions

enum class RepoSource {
    OWNED, STARRED, WATCHED
}

enum class InstallStatus {
    NOT_INSTALLED, UPDATE_AVAILABLE, UP_TO_DATE, UNKNOWN
}

data class RepoRelease(
    val repo: GithubRepo,
    val release: GithubRelease?,
    val sources: Set<RepoSource>,
    val installStatus: InstallStatus = InstallStatus.UNKNOWN,
    val installedVersionName: String? = null,
    val playVersions: PlayTrackVersions? = null,
    val pushedToday: Boolean = false,
    val releaseToday: Boolean = false,
    // 오늘 push된 적이 있고, 그 push를 반영한 APK가 아직 빌드되지 않았거나(= 소스가 release보다 최신)
    // 빌드됐어도 아직 설치되지 않은 경우. 즉 "오늘 소스가 바뀐 것에 대해 아직 할 일이 남은" 저장소.
    val needsWork: Boolean = false
)
