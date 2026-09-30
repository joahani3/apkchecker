package com.releasewatch.app.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubUser(
    val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class GithubOwner(
    val login: String
)

@JsonClass(generateAdapter = true)
data class GithubRepo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val owner: GithubOwner,
    @Json(name = "html_url") val htmlUrl: String,
    val private: Boolean,
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    // Updated on any push to any branch, including a brand-new branch's first push, so it
    // doubles as "branch created or source pushed today" without extra API calls.
    @Json(name = "pushed_at") val pushedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GithubRelease(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "published_at") val publishedAt: String?,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GithubAsset(
    val id: Long,
    val name: String,
    // The API asset endpoint, not browser_download_url: that one needs a browser session
    // cookie and returns 404 for a private repo's assets even with a valid Authorization header.
    @Json(name = "url") val apiUrl: String,
    @Json(name = "content_type") val contentType: String? = null
)

val GithubRelease.apkAsset: GithubAsset?
    get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
