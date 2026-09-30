package com.releasewatch.app.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleOAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Long
)

@JsonClass(generateAdapter = true)
data class AppEditResponse(
    val id: String
)

@JsonClass(generateAdapter = true)
data class TrackResponse(
    val track: String,
    val releases: List<TrackReleaseDto>?
)

@JsonClass(generateAdapter = true)
data class TrackReleaseDto(
    val name: String?,
    val status: String?,
    val versionCodes: List<String>?
)

@JsonClass(generateAdapter = true)
data class ServiceAccountJson(
    @Json(name = "client_email") val clientEmail: String,
    @Json(name = "private_key") val privateKey: String,
    @Json(name = "token_uri") val tokenUri: String?
)
