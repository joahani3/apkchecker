package com.releasewatch.app.data.network

import com.releasewatch.app.data.network.model.AppEditResponse
import com.releasewatch.app.data.network.model.GoogleOAuthTokenResponse
import com.releasewatch.app.data.network.model.TrackResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface GoogleOAuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer",
        @Field("assertion") assertion: String
    ): Response<GoogleOAuthTokenResponse>
}

// Android Publisher edits are transactional: a track's live version can only be read through an
// edit session (insert -> read -> discard), even though nothing is actually being changed here.
interface AndroidPublisherApi {
    @POST("androidpublisher/v3/applications/{packageName}/edits")
    suspend fun insertEdit(
        @Path("packageName") packageName: String,
        @retrofit2.http.Body body: RequestBody
    ): Response<AppEditResponse>

    @GET("androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks/{track}")
    suspend fun getTrack(
        @Path("packageName") packageName: String,
        @Path("editId") editId: String,
        @Path("track") track: String
    ): Response<TrackResponse>

    @DELETE("androidpublisher/v3/applications/{packageName}/edits/{editId}")
    suspend fun deleteEdit(
        @Path("packageName") packageName: String,
        @Path("editId") editId: String
    ): Response<Unit>
}
