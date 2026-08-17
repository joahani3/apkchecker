package com.releasewatch.app.data.repository

import com.releasewatch.app.data.network.model.GithubRelease
import com.releasewatch.app.data.network.model.GithubRepo

enum class RepoSource {
    OWNED, STARRED, WATCHED
}

data class RepoRelease(
    val repo: GithubRepo,
    val release: GithubRelease?,
    val sources: Set<RepoSource>,
    val isNew: Boolean
)
