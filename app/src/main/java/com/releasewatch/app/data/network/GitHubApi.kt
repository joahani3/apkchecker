package com.releasewatch.app.data.network

import com.releasewatch.app.data.network.model.GithubRelease
import com.releasewatch.app.data.network.model.GithubRepo
import com.releasewatch.app.data.network.model.GithubUser
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("user")
    suspend fun getAuthenticatedUser(): GithubUser

    @GET("user/repos")
    suspend fun getOwnRepos(
        @Query("affiliation") affiliation: String = "owner",
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): List<GithubRepo>

    @GET("user/starred")
    suspend fun getStarredRepos(
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepo>

    @GET("user/subscriptions")
    suspend fun getWatchedRepos(
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepo>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GithubRelease>
}
